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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * Test the "require files don't exist" rule.
 *
 * @author <a href="brianf@apache.org">Brian Fox</a>
 */
class TestRequireFilesDontExist {
    @TempDir
    public File temporaryFolder;

    private final RequireFilesDontExist rule = new RequireFilesDontExist();

    @BeforeEach
    void setup() {
        rule.setLog(mock(EnforcerLogger.class));
    }

    @Test
    void testFileExists() throws IOException {
        File f = File.createTempFile("junit", null, temporaryFolder);

        rule.setFilesList(Collections.singletonList(f));

        try {
            rule.execute();
            fail("Expected an Exception.");
        } catch (EnforcerRuleException e) {
            assertNotNull(e.getMessage());
        }
        f.delete();
    }

    @Test
    void testEmptyFile() {
        rule.setFilesList(Collections.singletonList(null));
        try {
            rule.execute();
            fail("Should get exception");
        } catch (EnforcerRuleException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testEmptyFileAllowNull() {
        rule.setFilesList(Collections.singletonList(null));
        rule.setAllowNulls(true);
        try {
            rule.execute();
        } catch (EnforcerRuleException e) {
            fail("Unexpected Exception:" + e.getLocalizedMessage());
        }
    }

    @Test
    void testEmptyFileList() {
        rule.setFilesList(Collections.emptyList());
        assertTrue(rule.getFiles().isEmpty());
        try {
            rule.execute();
            fail("Should get exception");
        } catch (EnforcerRuleException e) {
            assertNotNull(e.getMessage());
        }
    }

    @Test
    void testEmptyFileListAllowNull() {
        rule.setFilesList(Collections.emptyList());
        assertTrue(rule.getFiles().isEmpty());
        rule.setAllowNulls(true);
        try {
            rule.execute();
        } catch (EnforcerRuleException e) {
            fail("Unexpected Exception:" + e.getLocalizedMessage());
        }
    }

    @Test
    void testFileDoesNotExist() throws EnforcerRuleException, IOException {
        File f = File.createTempFile("junit", null, temporaryFolder);
        f.delete();

        assertFalse(f.exists());

        rule.setFilesList(Collections.singletonList(f));

        rule.execute();
    }

    @Test
    void testFileDoesNotExistCaseSensitive() throws Exception {
        File f = File.createTempFile("case_test", null, temporaryFolder);

        rule.setFilesList(Collections.singletonList(
                new File(f.getCanonicalFile().toString().toUpperCase())));
        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found, but the file name casing does not match
        rule.execute();

        rule.setCaseSenstive(true);
        rule.execute();

        // Filesystem is either: Containing folder will be found, but the file name casing does not match
        rule.setFilesList(Collections.singletonList(
                new File(String.format("%s/%s", f.getParentFile(), f.getName().toUpperCase()))));
        rule.execute();

        rule.setFilesList(Collections.singletonList(f));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        f.delete();
        rule.execute();
    }

    @Test
    void testFileDoesNotExistCaseInsensitive() throws Exception {
        File f = File.createTempFile("case_test", null, temporaryFolder);

        rule.setFilesList(Collections.singletonList(
                new File(f.getCanonicalFile().toString().toUpperCase())));
        rule.setCaseSenstive(false);

        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found as will the file
        if (rule.isFilesystemCaseSensitive()) {
            rule.execute();
        } else {
            EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
            assertNotNull(e.getMessage());
        }

        // Filesystem is either: Containing folder will be found, as should the file (failure)
        rule.setFilesList(Collections.singletonList(
                new File(String.format("%s/%s", f.getParentFile(), f.getName().toUpperCase()))));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        f.delete();
        rule.execute();
    }

    @Test
    void testFileSymbolicLinkDoesNotExistCaseSensitive() throws Exception {
        File canonicalFile = File.createTempFile("canonical_", null, temporaryFolder);
        Path canonicalPath = Paths.get(canonicalFile.getAbsolutePath());
        Path linkPath =
                Files.createSymbolicLink(Paths.get(temporaryFolder.getAbsolutePath(), "symbolic.link"), canonicalPath);
        File linkFile = new File(linkPath.toString());

        rule.setFilesList(Collections.singletonList(
                new File(linkFile.getCanonicalFile().toString().toUpperCase())));
        // Filesystem is:
        //   Case-sensitive: Can't find the containing folder due to incorrect casing in the path
        //   Case-insensitive: Containing folder will be found, but the file name casing does not match
        rule.execute();

        rule.setCaseSenstive(true);
        rule.execute();

        // Filesystem is either: Containing folder will be found, but the file name casing does not match
        rule.setFilesList(Collections.singletonList(new File(String.format(
                "%s/%s", linkFile.getParentFile(), linkFile.getName().toUpperCase()))));
        rule.execute();

        rule.setFilesList(Collections.singletonList(linkFile));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        linkFile.delete();
        rule.execute();
    }

    @Test
    void testFileSymbolicLinkDoesNotExistCaseInsensitive() throws Exception {
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
            rule.execute();
        } else {
            EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
            assertNotNull(e.getMessage());
        }

        // Filesystem is either: Containing folder will be found, as should the file (failure)
        rule.setFilesList(Collections.singletonList(new File(String.format(
                "%s/%s", linkFile.getParentFile(), linkFile.getName().toUpperCase()))));
        EnforcerRuleException e = assertThrows(EnforcerRuleException.class, rule::execute);
        assertNotNull(e.getMessage());

        linkFile.delete();
        rule.execute();
    }

    @Test
    void testFileDoesNotExistSatisfyAny() throws EnforcerRuleException, IOException {
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
