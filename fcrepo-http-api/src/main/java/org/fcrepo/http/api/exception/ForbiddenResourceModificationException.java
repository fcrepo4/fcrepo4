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
package org.fcrepo.http.api.exception;
import javax.ws.rs.ForbiddenException;

/**
 * An extension of {@link javax.ws.rs.ForbiddenException} that may be thrown when attempting a
 * forbidden or unsupported operation on a {@link org.fcrepo.kernel.models.FedoraResource}.
 *
 * @author jrgriffiniii
 */
public class ForbiddenResourceModificationException extends ForbiddenException {

    private static final long serialVersionUID = 1L;

    /**
     * Default constructor.
     * @param message the message
     */
    public ForbiddenResourceModificationException(final String message) {
        super(message);
    }
}
