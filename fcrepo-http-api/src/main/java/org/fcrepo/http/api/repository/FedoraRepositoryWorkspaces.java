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
package org.fcrepo.http.api.repository;

import static javax.ws.rs.core.MediaType.APPLICATION_XHTML_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static org.fcrepo.http.commons.domain.RDFMediaType.JSON_LD;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3_ALT2;
import static org.fcrepo.http.commons.domain.RDFMediaType.NTRIPLES;
import static org.fcrepo.http.commons.domain.RDFMediaType.RDF_XML;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE_X;

import java.net.URI;
import java.net.URISyntaxException;
import javax.inject.Inject;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Workspace;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import com.google.common.collect.ImmutableSet;
import org.fcrepo.http.api.FedoraBaseResource;
import org.fcrepo.http.commons.responses.HtmlTemplate;
import org.fcrepo.kernel.exception.RepositoryRuntimeException;
import org.fcrepo.kernel.impl.rdf.impl.WorkspaceRdfContext;
import org.fcrepo.kernel.utils.iterators.RdfStream;
import org.springframework.context.annotation.Scope;

/**
 * This class exposes the JCR workspace functionality. It may be
 * too JCR-y in the long run, but this lets us exercise the functionality.
 *
 * @author awoods
 * @author cbeer
 * @author ajs6f
 */
@Scope("prototype")
@Path("/fcr:workspaces")
public class FedoraRepositoryWorkspaces extends FedoraBaseResource {

    @Inject
    protected Session session;

    /**
     * Get the list of accessible workspaces in this repository.
     *
     * @return list of accessible workspaces
     * @throws RepositoryException
     */
    @GET
    @Produces({TURTLE, N3, N3_ALT2, RDF_XML, NTRIPLES, APPLICATION_XML, TEXT_PLAIN, TURTLE_X,
                      TEXT_HTML, APPLICATION_XHTML_XML, JSON_LD})
    @HtmlTemplate("jcr:workspaces")
    public RdfStream getWorkspaces() {
        try {
            return new WorkspaceRdfContext(session, translator()).session(session);
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Create a new workspace in the repository
     *
     * @param path
     * @param uriInfo
     * @return response
     * @throws RepositoryException
     */
    @POST
    @Path("{path}")
    public Response createWorkspace(@PathParam("path") final String path,
            @Context final UriInfo uriInfo)
        throws URISyntaxException {

        try {
            final Workspace workspace = session.getWorkspace();

            if (!workspace.getName().equals("default")) {
                throw new ClientErrorException("Unable to create workspace from non-default workspace", BAD_REQUEST);
            }

            final String[] workspaceNames = workspace.getAccessibleWorkspaceNames();
            if ( workspaceNames != null && ImmutableSet.copyOf(workspaceNames).contains(path)) {
                throw new WebApplicationException(
                    status(CONFLICT).entity("Workspace already exists").build());
            }

            workspace.createWorkspace(path);

            return created(new URI(translator().toDomain("/workspace:" + path + "/").toString())).build();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    /**
     * Delete a workspace from the repository
     * @param path
     * @return response
     * @throws RepositoryException
     */
    @DELETE
    @Path("{path}")
    public Response deleteWorkspace(@PathParam("path") final String path) {
        try {
            final Workspace workspace = session.getWorkspace();

            if (!ImmutableSet.copyOf(workspace.getAccessibleWorkspaceNames()).contains(path)) {
                throw new NotFoundException();
            }

            workspace.deleteWorkspace(path);

            return noContent().build();
        } catch (final RepositoryException e) {
            throw new RepositoryRuntimeException(e);
        }
    }

    @Override
    protected Session session() {
        return session;
    }

}
