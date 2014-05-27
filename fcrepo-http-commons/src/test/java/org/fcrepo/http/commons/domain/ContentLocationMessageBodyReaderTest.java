/**
 * Copyright 2014 DuraSpace, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.http.commons.domain;

import com.sun.jersey.core.header.InBoundHeaders;
import org.fcrepo.kernel.services.ExternalContentService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author cabeer
 */
public class ContentLocationMessageBodyReaderTest {


    private ContentLocationMessageBodyReader testObj;

    @Mock
    private InputStream mockInputStream;

    @Mock
    private ExternalContentService mockContentService;

    @Before
    public void setUp() throws URISyntaxException, IOException {
        initMocks(this);
        testObj = new ContentLocationMessageBodyReader();
        testObj.setContentService(mockContentService);
    }

    @Test
    public void testReadFromURI() throws Exception {
        final InBoundHeaders headers = new InBoundHeaders();
        headers.putSingle("Content-Location", "http://localhost:8080/xyz");
        when(mockContentService.retrieveExternalContent(new URI("http://localhost:8080/xyz")))
            .thenReturn(mockInputStream);
        final InputStream actual = testObj.readFrom(InputStream.class, null, null, null, headers, null);
        assertEquals(mockInputStream, actual);
    }

    @Test
    public void testReadFromRequestBody() throws Exception {

        final InBoundHeaders headers = new InBoundHeaders();
        final InputStream actual = testObj.readFrom(InputStream.class, null, null, null, headers, mockInputStream);
        assertEquals(mockInputStream, actual);

    }
}