/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.http.api;

import static java.nio.file.StandardOpenOption.APPEND;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.fcrepo.kernel.api.exception.ExternalMessageBodyException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author bbpennel
 */
public class ExternalContentPathValidatorTest {

    private ExternalContentPathValidator validator;

    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();

    private File allowListFile;

    @Before
    public void init() throws Exception {
        allowListFile = tmpDir.newFile();

        validator = new ExternalContentPathValidator();
        validator.setConfigPath(allowListFile.getAbsolutePath());
    }

    @After
    public void after() {
        validator.shutdown();
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testValidateWithNoAllowList() throws Exception {
        validator.setConfigPath(null);
        validator.init();

        final String extPath = "file:///this/path/file.txt";
        validator.validate(extPath);
    }

    @Test
    public void testValidFileUri() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        final String extPath = goodPath + "file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test
    public void testValidHttpUri() throws Exception {
        final String goodPath = "http://example.com/";
        final String extPath = goodPath + "file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test
    public void testExactFileUri() throws Exception {
        final String goodPath = "file:///this/path/is/good/file.txt";

        addAllowedPath(goodPath);

        validator.validate(goodPath);
    }

    @Test
    public void testMultipleMatches() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        final String extPath = goodPath + "file.txt";
        final String anotherPath = "file:///this/path/";

        addAllowedPath(anotherPath);
        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test
    public void testMultipleSchemes() throws Exception {
        final String httpPath = "http://example.com/";
        final String filePath = "file:///this/path/is/good/";
        final String extPath = filePath + "file.txt";

        addAllowedPath(httpPath);
        addAllowedPath(filePath);

        validator.validate(extPath);
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testInvalidFileUri() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        final String extPath = "file:///a/different/path/file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testInvalidHttpUri() throws Exception {
        final String goodPath = "http://good.example.com/";
        final String extPath = "http://bad.example.com/file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testRelativeModifier() throws Exception {
        final String goodPath = "file:///this/path/";
        final String extPath = goodPath + "../sneaky/file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testNoScheme() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        final String extPath = "/this/path/is/good/file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testEmptyPath() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        addAllowedPath(goodPath);

        validator.validate("");
    }

    @Test(expected = ExternalMessageBodyException.class)
    public void testNullPath() throws Exception {
        final String goodPath = "file:///this/path/is/good/";
        addAllowedPath(goodPath);

        validator.validate(null);
    }

    @Test(expected = IOException.class)
    public void testListFileDoesNotExist() throws Exception {
        allowListFile.delete();

        validator.init();
    }

    @Test
    public void testAllowAny() throws Exception {
        addAllowedPath("file:///");
        addAllowedPath("http://");
        addAllowedPath("https://");

        final String path1 = "file:///this/path/is/good/file";
        validator.validate(path1);
        final String path2 = "http://example.com/file";
        validator.validate(path2);
        final String path3 = "https://example.com/file";
        validator.validate(path3);
    }

    @Test
    public void testCaseInsensitive() throws Exception {
        final String goodPath = "FILE:///this/path/";
        final String extPath = "file:///this/path/file.txt";

        addAllowedPath(goodPath);

        validator.validate(extPath);
    }

    @Test
    public void testFileSlashes() throws Exception {
        addAllowedPath("file:/one/slash/");
        addAllowedPath("file://two/slash/");
        addAllowedPath("file:///three/slash/");
        addAllowedPath("file:////toomany/slash/");

        validator.validate("file:/one/slash/file");
        validator.validate("file://one/slash/file");
        validator.validate("file:///one/slash/file");

        validator.validate("file:/two/slash/file");
        validator.validate("file://two/slash/file");
        validator.validate("file:///two/slash/file");

        validator.validate("file:/three/slash/file");
        validator.validate("file://three/slash/file");
        validator.validate("file:///three/slash/file");

        validator.validate("file:////toomany/slash/file");
    }

    /*
     * Test ignored because it takes around 10+ seconds to poll for events on MacOS:
     * https://bugs.openjdk.java.net/browse/JDK-7133447 Can be enabled for one off testing
     */
    @Ignore("Test is ignored due to file event timing")
    @Test
    public void testDetectModification() throws Exception {
        validator.setMonitorForChanges(true);

        addAllowedPath("file:///different/path/");

        final String path = "file:///this/path/will/be/good/file";
        try {
            validator.validate(path);
            fail();
        } catch (final ExternalMessageBodyException e) {
            // Expected
        }

        // Wait to ensure that the watch service is watching...
        Thread.sleep(5000);

        // Add a new allowed path
        try (BufferedWriter writer = Files.newBufferedWriter(allowListFile.toPath(), APPEND)) {
            writer.write("file:///this/path/" + System.lineSeparator());
        }

        // Check that the new allowed path was detected
        boolean pass = false;
        // Polling to see if change occurred for 20 seconds
        final long endTimes = System.nanoTime() + 20000000000l;
        while (System.nanoTime() < endTimes) {
            Thread.sleep(50);
            try {
                validator.validate(path);
                pass = true;
                break;
            } catch (final ExternalMessageBodyException e) {
                // Still not passing, retry
            }
        }

        assertTrue("Validator did not update with new path", pass);
    }

    private void addAllowedPath(final String allowed) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(allowListFile.toPath(), APPEND)) {
            writer.write(allowed + System.lineSeparator());
        }
        validator.init();
    }
}
