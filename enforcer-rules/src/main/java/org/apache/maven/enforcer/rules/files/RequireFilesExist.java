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

import javax.inject.Named;

import java.io.File;
import java.io.IOException;

/**
 * The Class RequireFilesExist.
 */
@Named("requireFilesExist")
public final class RequireFilesExist extends AbstractRequireFiles {
    @Override
    boolean checkFile(File file) {
        if (file == null) {
            // if we get here the handle was null, treat it as a success
            return true;
        }

        try {
            if (isFilesystemCaseSensitive()) {
                return caseSensitiveFileSystemCheck(file);
            } else {
                return caseInsensitiveFileSystemCheck(file);
            }
        } catch (IOException ioe) {
            getLog().warnOrError(String.format(
                    "Failed to fully check for file '%s' due to exception '%s'", file.getPath(), ioe.getMessage()));
            return false;
        }
    }

    @Override
    String getErrorMsg() {
        return "Some required files are missing:" + System.lineSeparator();
    }
}
