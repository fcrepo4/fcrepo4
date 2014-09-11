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
package org.fcrepo.kernel.utils;

import static javax.jcr.observation.Event.NODE_ADDED;
import static org.fcrepo.kernel.utils.EventType.valueOf;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * <p>EventTypeTest class.</p>
 *
 * @author ajs6f
 */
public class EventTypeTest {

    @Test
    public void testGetEventName() {
        assertEquals("node added", valueOf(NODE_ADDED).getName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadEvent() {
        valueOf(9999999);
    }

    @Test()
    public void testValueOf() {
        assertEquals(EventType.PERSIST, EventType.valueOf("PERSIST"));
    }


}
