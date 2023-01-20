/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.enforcer.rules;

import javax.inject.Inject;
import javax.inject.Named;

import java.text.ChoiceFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.utils.ArtifactMatcher;
import org.apache.maven.enforcer.rules.utils.ArtifactUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.DependencyVisitor;
import org.eclipse.aether.util.graph.selector.AndDependencySelector;
import org.eclipse.aether.util.graph.selector.OptionalDependencySelector;
import org.eclipse.aether.util.graph.selector.ScopeDependencySelector;
import org.eclipse.aether.util.graph.visitor.TreeDependencyVisitor;
import org.eclipse.aether.version.VersionConstraint;

/**
 * This rule bans dependencies having a version which requires resolution (i.e. dynamic versions which might change with
 * each build). Dynamic versions are either
 * <ul>
 * <li>version ranges,</li>
 * <li>the special placeholders {@code LATEST} or {@code RELEASE} or</li>
 * <li>versions ending with {@code -SNAPSHOT}.
 * </ul>
 *
 * @since 3.2.0
 */
@Named("banDynamicVersions")
public final class BanDynamicVersions extends AbstractStandardEnforcerRule {

    private static final String RELEASE = "RELEASE";

    private static final String LATEST = "LATEST";

    private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

    /**
     * {@code true} if versions ending with {@code -SNAPSHOT} should be allowed
     */
    private boolean allowSnapshots;

    /**
     * {@code true} if versions using {@code LATEST} should be allowed
     */
    private boolean allowLatest;

    /**
     * {@code true} if versions using {@code RELEASE} should be allowed
     */
    private boolean allowRelease;

    /**
     * {@code true} if version ranges should be allowed
     */
    private boolean allowRanges;

    /**
     * {@code true} if ranges having the same upper and lower bound like {@code [1.0]} should be allowed.
     * Only applicable if {@link #allowRanges} is not set to {@code true}.
     */
    private boolean allowRangesWithIdenticalBounds;

    /**
     * {@code true} if optional dependencies should not be checked
     */
    private boolean excludeOptionals;

    /**
     * the scopes of dependencies which should be excluded from this rule
     */
    private String[] excludedScopes;

    /**
     * Specify the ignored dependencies. This can be a list of artifacts in the format
     * <code>groupId[:artifactId[:version[:type[:scope:[classifier]]]]]</code>.
     * Any of the sections can be a wildcard by using '*' (e.g. {@code group:*:1.0}).
     * <br>
     * Any of the ignored dependencies may have dynamic versions.
     */
    private List<String> ignores = null;

    private final MavenProject project;

    private final RepositorySystem repoSystem;

    private final MavenSession mavenSession;

    @Inject
    public BanDynamicVersions(MavenProject project, RepositorySystem repoSystem, MavenSession mavenSession) {
        this.project = Objects.requireNonNull(project);
        this.repoSystem = Objects.requireNonNull(repoSystem);
        this.mavenSession = Objects.requireNonNull(mavenSession);
    }

    private final class BannedDynamicVersionCollector implements DependencyVisitor {

        private final Deque<DependencyNode> nodeStack; // all intermediate nodes (without the root node)

        private boolean isRoot = true;

        private int numViolations;

        private final Predicate<DependencyNode> predicate;

        public int getNumViolations() {
            return numViolations;
        }

        BannedDynamicVersionCollector(Predicate<DependencyNode> predicate) {
            nodeStack = new ArrayDeque<>();
            this.predicate = predicate;
            this.isRoot = true;
            numViolations = 0;
        }

        private boolean isBannedDynamicVersion(VersionConstraint versionConstraint) {
            if (versionConstraint.getVersion() != null) {
                if (versionConstraint.getVersion().toString().equals(LATEST)) {
                    return !allowLatest;
                } else if (versionConstraint.getVersion().toString().equals(RELEASE)) {
                    return !allowRelease;
                } else if (versionConstraint.getVersion().toString().endsWith(SNAPSHOT_SUFFIX)) {
                    return !allowSnapshots;
                }
            } else if (versionConstraint.getRange() != null) {
                if (allowRangesWithIdenticalBounds
                        && Objects.equals(
                                versionConstraint.getRange().getLowerBound(),
                                versionConstraint.getRange().getUpperBound())) {
                    return false;
                }
                return !allowRanges;
            } else {
                getLog().warn("Unexpected version constraint found: " + versionConstraint);
            }
            return false;
        }

        @Override
        public boolean visitEnter(DependencyNode node) {
            if (isRoot) {
                isRoot = false;
            } else {
                getLog().debug("Found node " + node + " with version constraint " + node.getVersionConstraint());
                if (predicate.test(node) && isBannedDynamicVersion(node.getVersionConstraint())) {
                    getLog().warnOrError(() -> new StringBuilder()
                            .append("Dependency ")
                            .append(node.getDependency())
                            .append(dumpIntermediatePath(nodeStack))
                            .append(" is referenced with a banned dynamic version ")
                            .append(node.getVersionConstraint()));
                    numViolations++;
                    return false;
                }
                nodeStack.addLast(node);
            }
            return true;
        }

        @Override
        public boolean visitLeave(DependencyNode node) {
            if (!nodeStack.isEmpty()) {
                nodeStack.removeLast();
            }
            return true;
        }
    }

    @Override
    public void execute() throws EnforcerRuleException {

        // get a new session to be able to tweak the dependency selector
        DefaultRepositorySystemSession newRepoSession =
                new DefaultRepositorySystemSession(mavenSession.getRepositorySession());

        Collection<DependencySelector> depSelectors = new ArrayList<>();
        depSelectors.add(new ScopeDependencySelector(excludedScopes));
        if (excludeOptionals) {
            depSelectors.add(new OptionalDependencySelector());
        }
        newRepoSession.setDependencySelector(new AndDependencySelector(depSelectors));

        Dependency rootDependency = RepositoryUtils.toDependency(project.getArtifact(), null);
        try {
            // use root dependency with unresolved direct dependencies
            int numViolations = emitDependenciesWithBannedDynamicVersions(rootDependency, newRepoSession);
            if (numViolations > 0) {
                ChoiceFormat dependenciesFormat = new ChoiceFormat("1#dependency|1<dependencies");
                throw new EnforcerRuleException("Found " + numViolations + " "
                        + dependenciesFormat.format(numViolations)
                        + " with dynamic versions. Look at the warnings emitted above for the details.");
            }
        } catch (DependencyCollectionException e) {
            throw new EnforcerRuleException("Could not retrieve dependency metadata for project", e);
        }
    }

    private static String dumpIntermediatePath(Collection<DependencyNode> path) {
        if (path.isEmpty()) {
            return "";
        }
        return " via " + path.stream().map(n -> n.getArtifact().toString()).collect(Collectors.joining(" -> "));
    }

    private static final class ExcludeArtifactPatternsPredicate implements Predicate<DependencyNode> {

        private final ArtifactMatcher artifactMatcher;

        ExcludeArtifactPatternsPredicate(List<String> excludes) {
            this.artifactMatcher = new ArtifactMatcher(excludes, Collections.emptyList());
        }

        @Override
        public boolean test(DependencyNode depNode) {
            return artifactMatcher.match(ArtifactUtils.toArtifact(depNode));
        }
    }

    private int emitDependenciesWithBannedDynamicVersions(
            Dependency rootDependency, RepositorySystemSession repoSession) throws DependencyCollectionException {
        CollectRequest collectRequest = new CollectRequest(rootDependency, project.getRemoteProjectRepositories());
        CollectResult collectResult = repoSystem.collectDependencies(repoSession, collectRequest);
        Predicate<DependencyNode> predicate;
        if (ignores != null && !ignores.isEmpty()) {
            predicate = new ExcludeArtifactPatternsPredicate(ignores);
        } else {
            predicate = d -> true;
        }
        BannedDynamicVersionCollector bannedDynamicVersionCollector = new BannedDynamicVersionCollector(predicate);
        DependencyVisitor depVisitor = new TreeDependencyVisitor(bannedDynamicVersionCollector);
        collectResult.getRoot().accept(depVisitor);
        return bannedDynamicVersionCollector.getNumViolations();
    }

    @Override
    public String toString() {
        return String.format(
                "BanDynamicVersions[allowSnapshots=%b, allowLatest=%b, allowRelease=%b, allowRanges=%b, allowRangesWithIdenticalBounds=%b, excludeOptionals=%b, excludedScopes=%s, ignores=%s]",
                allowSnapshots,
                allowLatest,
                allowRelease,
                allowRanges,
                allowRangesWithIdenticalBounds,
                excludeOptionals,
                excludedScopes,
                ignores);
    }
}
