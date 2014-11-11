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
package org.fcrepo.services;

import org.fcrepo.metrics.RegistryService;
import org.junit.Test;

/**
 * <p>MetricsServiceTest class.</p>
 *
 * @author eddies
 */
public class MetricsServiceTest {

    @Test
    public void testSingletonBranch() {
        //Create instance
        RegistryService.getInstance();
        //Fetches the created instance. Success indicated by branch coverage.
        RegistryService.getInstance();
    }
}
