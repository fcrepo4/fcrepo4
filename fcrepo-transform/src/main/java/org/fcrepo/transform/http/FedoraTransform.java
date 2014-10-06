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
package org.fcrepo.transform.http;

import static javax.jcr.nodetype.NodeType.NT_BASE;
import static javax.jcr.nodetype.NodeType.NT_FILE;
import static javax.jcr.nodetype.NodeType.NT_FOLDER;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.apache.jena.riot.WebContent.contentTypeN3;
import static org.apache.jena.riot.WebContent.contentTypeNTriples;
import static org.apache.jena.riot.WebContent.contentTypeRDFXML;
import static org.apache.jena.riot.WebContent.contentTypeResultsBIO;
import static org.apache.jena.riot.WebContent.contentTypeResultsJSON;
import static org.apache.jena.riot.WebContent.contentTypeResultsXML;
import static org.apache.jena.riot.WebContent.contentTypeSPARQLQuery;
import static org.apache.jena.riot.WebContent.contentTypeSSE;
import static org.apache.jena.riot.WebContent.contentTypeTextCSV;
import static org.apache.jena.riot.WebContent.contentTypeTextPlain;
import static org.apache.jena.riot.WebContent.contentTypeTextTSV;
import static org.apache.jena.riot.WebContent.contentTypeTurtle;
import static org.fcrepo.transform.transformations.LDPathTransform.APPLICATION_RDF_LDPATH;
import static org.fcrepo.transform.transformations.LDPathTransform.CONFIGURATION_FOLDER;
import static org.fcrepo.transform.transformations.LDPathTransform.getNodeTypeTransform;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.fcrepo.http.api.FedoraBaseResource;
import org.fcrepo.kernel.FedoraResource;
import org.fcrepo.transform.TransformationFactory;
import org.jvnet.hk2.annotations.Optional;
import org.slf4j.Logger;
import org.springframework.context.annotation.Scope;

import com.codahale.metrics.annotation.Timed;
import com.hp.hpl.jena.query.Dataset;

/**
 * Endpoint for transforming object properties using stored
 * or POSTed transformations.
 *
 * @author cbeer
 */
@Scope("prototype")
@Path("/{path: .*}/fcr:transform")
public class FedoraTransform extends FedoraBaseResource {

    @Inject
    protected Session session;

    private static final Logger LOGGER = getLogger(FedoraTransform.class);

    @Inject
    @Optional
    private TransformationFactory transformationFactory;

    /**
     * Register the LDPath configuration tree in JCR
     *
     * @throws RepositoryException
     * @throws java.io.IOException
     * @throws SecurityException
     */
    @PostConstruct
    public void setUpRepositoryConfiguration() throws RepositoryException, IOException {

        final Session internalSession = sessions.getInternalSession();
        try {
            // register our CND
            jcrTools.registerNodeTypes(internalSession, "ldpath.cnd");

            // create the configuration base path
            jcrTools.findOrCreateNode(internalSession, "/fedora:system/fedora:transform", "fedora:configuration",
                    "fedora:node_type_configuration");
            final Node node =
                jcrTools.findOrCreateNode(internalSession, CONFIGURATION_FOLDER + "default", NT_FOLDER, NT_FOLDER);
            LOGGER.debug("Transforming node: {}", node.getPath());

            // register an initial default program
            if (!node.hasNode(NT_BASE)) {
                final Node baseConfig = node.addNode(NT_BASE, NT_FILE);
                jcrTools.uploadFile(internalSession, baseConfig.getPath(), getClass().getResourceAsStream(
                        "/ldpath/default/nt_base_ldpath_program.txt"));
            }
            internalSession.save();
        } finally {
            internalSession.logout();
        }
    }

    /**
     * Execute an LDpath program transform
     *
     * @param externalPath
     * @return Binary blob
     * @throws RepositoryException
     */
    @GET
    @Path("{program}")
    @Produces({APPLICATION_JSON})
    @Timed
    public Object evaluateLdpathProgram(@PathParam("path")
        final String externalPath, @PathParam("program")
        final String program) throws RepositoryException {

        try {
            final FedoraResource object = getResourceFromPath(externalPath);

            final Dataset propertiesDataset =
                object.getPropertiesDataset(translator());

            return getNodeTypeTransform(object.getNode(), program).apply(propertiesDataset);

        } finally {
            session.logout();
        }
    }

    /**
     * Get the LDPath output as a JSON stream appropriate for e.g. Solr
     *
     * @param externalPath
     * @param requestBodyStream
     * @return LDPath as a JSON stream
     * @throws RepositoryException
     */
    @POST
    @Consumes({APPLICATION_RDF_LDPATH, contentTypeSPARQLQuery})
    @Produces({APPLICATION_JSON, contentTypeTextTSV, contentTypeTextCSV,
            contentTypeSSE, contentTypeTextPlain, contentTypeResultsJSON,
            contentTypeResultsXML, contentTypeResultsBIO, contentTypeTurtle,
            contentTypeN3, contentTypeNTriples, contentTypeRDFXML})
    @Timed
    public Object evaluateTransform(@PathParam("path")
        final String externalPath, @HeaderParam("Content-Type")
        final MediaType contentType, final InputStream requestBodyStream)
        throws RepositoryException {

        if (transformationFactory == null) {
            transformationFactory = new TransformationFactory();
        }

        try {
            final FedoraResource object = getResourceFromPath(externalPath);

            final Dataset propertiesDataset =
                object.getPropertiesDataset(translator());

            return transformationFactory.getTransform(contentType, requestBodyStream).apply(propertiesDataset);

        } finally {
            session.logout();
        }
    }

    @Override
    protected Session session() {
        return session;
    }
}
