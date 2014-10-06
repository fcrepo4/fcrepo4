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
package org.fcrepo.http.api;

import com.google.common.annotations.VisibleForTesting;
import org.fcrepo.http.commons.responses.HtmlTemplate;
import org.fcrepo.kernel.FedoraResource;
import org.fcrepo.kernel.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.exception.RepositoryVersionRuntimeException;
import org.fcrepo.kernel.impl.rdf.impl.VersionsRdfContext;
import org.fcrepo.kernel.utils.iterators.RdfStream;
import org.slf4j.Logger;
import org.springframework.context.annotation.Scope;

import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.net.URISyntaxException;
import java.util.Collection;

import static java.util.Collections.singleton;
import static javax.ws.rs.core.MediaType.APPLICATION_XHTML_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static org.fcrepo.http.commons.domain.RDFMediaType.JSON_LD;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3_ALT2;
import static org.fcrepo.http.commons.domain.RDFMediaType.NTRIPLES;
import static org.fcrepo.http.commons.domain.RDFMediaType.RDF_XML;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE_X;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author cabeer
 * @since 9/25/14
 */
@Scope("request")
@Path("/{path: .*}/fcr:versions")
public class FedoraVersioning extends FedoraBaseResource {

    @Inject
    protected Session session;

    private static final Logger LOGGER = getLogger(FedoraVersioning.class);


    @Context protected Request request;
    @Context protected HttpServletResponse servletResponse;
    @Context protected UriInfo uriInfo;

    @PathParam("path") protected String externalPath;

    protected FedoraResource resource;


    /**
     * Default JAX-RS entry point
     */
    public FedoraVersioning() {
        super();
    }

    /**
     * Create a new FedoraNodes instance for a given path
     * @param externalPath
     */
    @VisibleForTesting
    public FedoraVersioning(final String externalPath) {
        this.externalPath = externalPath;
    }


    /**
     * Enable versioning
     * @return
     * @throws java.net.URISyntaxException
     */
    @PUT
    public Response enableVersioning() throws URISyntaxException {
        try {

            resource().enableVersioning();

            try {
                session.save();
            } catch (final RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }
            return created(uriInfo.getRequestUri()).build();
        } finally {
            session.logout();
        }
    }

    /**
     * Disable versioning
     * @return
     */
    @DELETE
    public Response disableVersioning() {
        try {
            resource().disableVersioning();

            try {
                session.save();
            } catch (final RepositoryException e) {
                throw new RepositoryRuntimeException(e);
            }

            return noContent().build();
        } finally {
            session.logout();
        }
    }

    /**
     * Create a new version checkpoint and tag it with the given label.  If
     * that label already describes another version it will silently be
     * reassigned to describe this version.
     *
     * @return response
     * @throws RepositoryException
     */
    @POST
    public Response addVersion(@HeaderParam("Slug") final String slug) throws RepositoryException {
        try {
            final String path = toPath(translator(), externalPath);
            final Collection<String> versions = versionService.createVersion(session.getWorkspace(),
                    singleton(path));

            if (slug != null) {
                resource().addVersionLabel(slug);
            }

            final String version = (slug != null) ? slug : versions.iterator().next();

            return noContent().header("Location", uriInfo.getRequestUri() + "/" + version).build();
        } finally {
            session.logout();
        }
    }


    /**
     * Get the list of versions for the object
     *
     * @return List of versions for the object as RDF
     * @throws RepositoryException
     */
    @GET
    @HtmlTemplate(value = "fcr:versions")
    @Produces({TURTLE, N3, N3_ALT2, RDF_XML, NTRIPLES, APPLICATION_XML, TEXT_PLAIN, TURTLE_X,
            TEXT_HTML, APPLICATION_XHTML_XML, JSON_LD})
    public RdfStream getVersionList() {
        if (!resource().isVersioned()) {
            throw new RepositoryVersionRuntimeException("This operation requires that the node be versionable");
        }

        return resource().getTriples(translator(), VersionsRdfContext.class)
                .session(session)
                .topic(translator().reverse().convert(resource().getNode()).asNode());
    }

    protected FedoraResource resource() {
        if (resource == null) {
            resource = getResourceFromPath(externalPath);
        }

        return resource;
    }

    @Override
    protected Session session() {
        return session;
    }
}
