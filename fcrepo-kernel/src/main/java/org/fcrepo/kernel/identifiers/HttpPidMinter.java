/**
 * Copyright 2013 DuraSpace, Inc.
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
package org.fcrepo.kernel.identifiers;

import static org.slf4j.LoggerFactory.getLogger;
import org.slf4j.Logger;
import com.codahale.metrics.annotation.Timed;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;



/**
 * PidMinter that uses an external HTTP API to mint PIDs.
 *
 * @author escowles
 * @date 04/28/2014
 */
public class HttpPidMinter extends BasePidMinter {

    private static final Logger log = getLogger(HttpPidMinter.class);
    private static HttpClient client = HttpClients.createSystem();
    private final String minterURL;

    /**
     * Default constructor, which uses the <code>fcrepo.httpPidMinter.url</code>
     * System property to determine the URL to POST to for minting PIDs.
    **/
    public HttpPidMinter() {
        minterURL = System.getProperty("fcrepo.httpPidMinter.url");
    }

    /**
     * Mint a unique identifier using an external HTTP API.
     * @return
     */
    @Timed
    @Override
    public String mintPid() {
        try {
            final HttpResponse resp = client.execute(new HttpPost(minterURL));
            return EntityUtils.toString(resp.getEntity());
        } catch ( IOException ex ) {
            log.error("Error minting pid from {}: {}", minterURL, ex);
        }
        return null;
    }
}
