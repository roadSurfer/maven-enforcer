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
package org.apache.maven.enforcer.rules.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.maven.enforcer.rule.api.EnforcerRuleError;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rules.AbstractStandardEnforcerRule;

/**
 * Contains the common code to compare an array of files against a requirement.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
abstract class AbstractRequireFiles extends AbstractStandardEnforcerRule {

    /** List of files to check. */
    private List<File> files = Collections.emptyList();

    /** if null file handles should be allowed. If they are allowed, it means treat it as a success. */
    private boolean allowNulls = false;

    /** Allow that a single one of the files can make the rule to pass. */
    private boolean satisfyAny;

    /** If case-insensitive matching should be used. */
    private boolean caseSenstive = true;

    /** If the filesystem is detected as being case-sensitive */
    private boolean filesystemCaseSensitive = true;

    // check the file for the specific condition
    /**
     * Check one file.
     *
     * @param file the file
     * @return <code>true</code> if successful
     */
    abstract boolean checkFile(File file);

    // return standard error message
    /**
     * Gets the error msg.
     *
     * @return the error msg
     */
    abstract String getErrorMsg();

    @Override
    public void execute() throws EnforcerRuleException {

        if (!allowNulls && files.isEmpty()) {
            throw new EnforcerRuleError("The file list is empty and Null files are disabled.");
        }

        List<File> failures = new ArrayList<>();
        for (File file : files) {
            if (!allowNulls && file == null) {
                failures.add(file);
            } else if (!checkFile(file)) {
                failures.add(file);
            }
        }

        if (satisfyAny) {
            int passed = files.size() - failures.size();
            if (passed == 0) {
                fail(failures);
            }
        }
        // if anything was found, log it with the optional message.
        else if (!failures.isEmpty()) {
            fail(failures);
        }
    }

    /**
     * This is, frankly, terrible. There is no guarantee that the temp filesystem has the same case-sensitivity rules and any
     * file-system containing files that may be checked for.
     *
     * @return If the probed file-system is deemed case-sensitive
     */
    private boolean probeFilesystemCaseSensitive() {
        File lower = null;
        try {
            lower = File.createTempFile("enforcer_probe", "");
            File upper = new File(lower.getParentFile(), lower.getName().toUpperCase());
            return !upper.exists();
        } catch (Exception e) {
            getLog().warn("Failed to determine filesystem case sensitivity");
            return true;
        } finally {
            if (lower != null && lower.exists()) {
                lower.delete();
            }
        }
    }

    private void fail(List<File> failures) throws EnforcerRuleException {
        String message = getMessage();

        StringBuilder buf = new StringBuilder();
        if (message != null) {
            buf.append(message).append(System.lineSeparator());
        }
        buf.append(getErrorMsg());

        for (File file : failures) {
            if (file != null) {
                buf.append(file.getAbsolutePath()).append(System.lineSeparator());
            } else {
                buf.append("(an empty filename was given and allowNulls is false)")
                        .append(System.lineSeparator());
            }
        }

        throw new EnforcerRuleException(buf.toString());
    }

    @Override
    public String getCacheId() {
        return Integer.toString(files.hashCode());
    }

    void setFilesList(List<File> files) {
        this.files = files;
    }

    // method using for testing purpose ...

    List<File> getFiles() {
        return files;
    }

    void setAllowNulls(boolean allowNulls) {
        this.allowNulls = allowNulls;
    }

    void setSatisfyAny(boolean satisfyAny) {
        this.satisfyAny = satisfyAny;
    }

    void setCaseSenstive(boolean caseSenstive) {
        this.caseSenstive = caseSenstive;
    }

    boolean isCaseSenstiveCheck() {
        return caseSenstive;
    }

    void setFilesystemCaseSenstive(boolean filesystemCaseSensitive) {
        this.filesystemCaseSensitive = filesystemCaseSensitive;
    }

    boolean isFilesystemCaseSensitive() {
        return filesystemCaseSensitive;
    }

    @Override
    public String toString() {
        return String.format(
                "%s[message=%s, files=%s, allowNulls=%b, satisfyAny=%b, caseSenstive=%b, filesystemCaseSensitive=%b]",
                getClass().getSimpleName(),
                getMessage(),
                files,
                allowNulls,
                satisfyAny,
                caseSenstive,
                filesystemCaseSensitive);
    }

    /**
     * Attempt to check for the file on a case-sensitive file system (i.e. default behaviour). If this is an insensitive
     * check, then we can only really check on the name and not the entire path
     *
     * @param file  The file to check for
     * @return If the file exists
     */
    boolean caseSensitiveFileSystemCheck(File file) {
        if (!isCaseSenstiveCheck()) {
            getLog().warn("Case-insensitive checks on a case-sensitive filesystem are restricted to the name only");
            File parent = file.getParentFile();

            if (parent.exists()) {
                for (String child : Objects.requireNonNull(parent.list())) {
                    if (child.equalsIgnoreCase(file.getName())) {
                        return true;
                    }
                }
            } else {
                getLog().warn(String.format("Cannot find parent folder for '%s', path casing?", file.getPath()));
            }

            return false;
        }

        // A simple test will do
        return file.exists();
    }

    /**
     * Attempt to check for the file on a case-sensitive file system (i.e. default behaviour). If this is an insensitive
     * check, then we can only really check on the name and not the entire path
     *
     * @param file  The file to check for
     * @return If the file exists
     */
    boolean caseInsensitiveFileSystemCheck(File file) throws IOException {
        if (isCaseSenstiveCheck()) {
            if (Files.isSymbolicLink(file.toPath())) {
                // We cannot rely on getCanonicalFile() as this will resolve the symbolic link and lead to an
                // erroneous result
                getLog().warn(
                                "Case-sensitive checks on a case-insensitive filesystem of a symbolic links are restricted to the name only");
                File parent = file.getParentFile();

                for (String child : Objects.requireNonNull(parent.list())) {
                    if (child.equals(file.getName())) {
                        return true;
                    }
                }

                return false;
            }

            return file.toURI()
                    .toString()
                    .equals(file.getCanonicalFile().toURI().toString());
        } else {
            // A simple test will do once again
            return file.exists();
        }
    }
}
