/**
 * Copyright 2015 DuraSpace, Inc.
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
package org.fcrepo.kernel.impl.services.functions;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

import org.modeshape.jcr.JcrRepository;
import org.modeshape.jcr.RepositoryConfiguration;
import org.modeshape.jcr.value.binary.BinaryStore;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * GetBinaryStore class.
 *
 * @author acoburn
 */
@RunWith(MockitoJUnitRunner.class)
public class GetBinaryStoreTest {

    @Mock
    private JcrRepository mockRepo;

    @Mock
    private RepositoryConfiguration mockConfig;

    @Mock
    private BinaryStore mockBinaryStore;

    @Mock
    private RepositoryConfiguration.BinaryStorage mockStorage;

    @Before
    public void setUp() throws Exception {
        when(mockConfig.getBinaryStorage()).thenReturn(mockStorage);
        when(mockStorage.getBinaryStore()).thenReturn(mockBinaryStore);
        when(mockRepo.getConfiguration()).thenReturn(mockConfig);
    }

    @Test
    public void testApply() throws Exception {

        final GetBinaryStore testObj = new GetBinaryStore();

        final BinaryStore binaryStore = testObj.apply(mockRepo);

        assertNotNull(binaryStore);
    }
}
