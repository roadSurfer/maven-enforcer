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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

import org.apache.maven.enforcer.rule.api.EnforcerLogger;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Test the "require files exist" rule.
 *
 * @author <a href="brett@apache.org">Brett Porter</a>
 */
class TestRequireFilesExist {
    @TempDir
    public File temporaryFolder;

    private final RequireFilesExist rule = new RequireFilesExist();

    @BeforeEach
    void setup() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    void testFileExist() throws Exception {
        File f = File.createTempFile("junit", null, temporaryFolder);

        rule.setFilesList(Collections.singletonList(f.getCanonicalFile()));

        rule.execute();
    }

    @Test
    void testFileExistCaseSensitive() throws Exception {
        File f = File.createTempFile("case_test", null, temporaryFolder);

        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found, but we should still fail as file name does not match
        rule.setFilesList(Collections.singletonList(
                new File(f.getCanonicalFile().toString().toUpperCase())));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        rule.setCaseSenstive(true);
        e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        // Filesystem is either: Containing folder will be found, but should still fail to match on file
        rule.setFilesList(Collections.singletonList(
                new File(String.format("%s/%s", f.getParentFile(), f.getName().toUpperCase()))));
        e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());
    }

    @Test
    void testFileExistCaseInsensitive() throws Exception {
        File f = File.createTempFile("case_test", null, temporaryFolder);

        rule.setFilesList(Collections.singletonList(
                new File(f.getCanonicalFile().toString().toUpperCase())));
        rule.setCaseSenstive(false);

        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found, but we should still fail as file name does not match
        if (rule.isFilesystemCaseSensitive()) {
            EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
            assertNotNull(e.getMessage());
        } else {
            rule.execute();
        }

        // Filesystem is either: Containing folder will be found, and the file should be found
        rule.setFilesList(Collections.singletonList(
                new File(String.format("%s/%s", f.getParentFile(), f.getName().toUpperCase()))));
        // No matter the file-system case-sensitivity, the containing folder will now be found
        rule.execute();
    }

    @Test
    void testFileSymbolicLinkExistCaseSensitive() throws Exception {
        File canonicalFile = File.createTempFile("canonical_", null, temporaryFolder);
        Path canonicalPath = Paths.get(canonicalFile.getAbsolutePath());
        Path linkPath =
                Files.createSymbolicLink(Paths.get(temporaryFolder.getAbsolutePath(), "symbolic.link"), canonicalPath);
        File linkFile = new File(linkPath.toString());

        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found, but we should still fail as file name does not match
        rule.setFilesList(
                Collections.singletonList(new File(linkFile.getAbsolutePath().toUpperCase())));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        rule.setCaseSenstive(true);
        e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        // Filesystem is either: Containing folder will be found, but should still fail to match on file
        rule.setFilesList(Collections.singletonList(new File(String.format(
                "%s/%s", linkFile.getParentFile(), linkFile.getName().toUpperCase()))));
        e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());
    }

    @Test
    void testFileSymbolicLinkExistCaseInsensitive() throws Exception {
        File canonicalFile = File.createTempFile("canonical_", null, temporaryFolder);
        Path canonicalPath = Paths.get(canonicalFile.getAbsolutePath());
        Path linkPath =
                Files.createSymbolicLink(Paths.get(temporaryFolder.getAbsolutePath(), "symbolic.link"), canonicalPath);
        File linkFile = new File(linkPath.toString());

        rule.setFilesList(Collections.singletonList(
                new File(linkFile.getCanonicalFile().toString().toUpperCase())));
        rule.setCaseSenstive(false);

        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found as will the file
        if (rule.isFilesystemCaseSensitive()) {
            EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
            assertNotNull(e.getMessage());
        } else {
            rule.execute();
        }

        // Filesystem is either: Containing folder will be found, as should the file
        rule.setFilesList(Collections.singletonList(new File(String.format(
                "%s/%s", linkFile.getParentFile(), linkFile.getName().toUpperCase()))));
        rule.execute();
    }

    @Test
    void testEmptyFile() {
        rule.setFilesList(Collections.singletonList(null));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, () -> rule.execute());

        assertNotNull(e.getMessage());
    }

    @Test
    void testEmptyFileAllowNull() throws Exception {
        rule.setFilesList(Collections.singletonList(null));
        rule.setAllowNulls(true);
        rule.execute();
    }

    @Test
    void testEmptyFileList() {
        rule.setFilesList(Collections.emptyList());
        assertTrue(rule.getFiles().isEmpty());

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, () -> rule.execute());

        assertNotNull(e.getMessage());
    }

    @Test
    void testEmptyFileListAllowNull() throws Exception {
        rule.setFilesList(Collections.emptyList());
        assertTrue(rule.getFiles().isEmpty());
        rule.setAllowNulls(true);
        rule.execute();
    }

    @Test
    void testFileDoesNotExist() throws Exception {
        File f = File.createTempFile("junit", null, temporaryFolder);
        f.delete();

        assertFalse(f.exists());
        rule.setFilesList(Collections.singletonList(f));

        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, () -> rule.execute());

        assertNotNull(e.getMessage());
    }

    @Test
    void testFileExistSatisfyAny() throws EnforcerRuleException, IOException {
        File f = File.createTempFile("junit", null, temporaryFolder);
        f.delete();

        assertFalse(f.exists());

        File g = File.createTempFile("junit", null, temporaryFolder);

        assertTrue(g.exists());

        rule.setFilesList(Arrays.asList(f, g.getCanonicalFile()));
        rule.setSatisfyAny(true);

        rule.execute();
    }

    /**
     * Test id.
     */
    @Test
    void testId() {
        assertNotNull(rule.getCacheId());
    }
}
