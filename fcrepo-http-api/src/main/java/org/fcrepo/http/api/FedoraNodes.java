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

package org.fcrepo.http.api;

import static com.hp.hpl.jena.graph.NodeFactory.createURI;
import static com.hp.hpl.jena.graph.Triple.create;
import static com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel;
import static com.hp.hpl.jena.rdf.model.ResourceFactory.createResource;
import static com.sun.jersey.api.Responses.clientError;
import static com.sun.jersey.api.Responses.notAcceptable;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XHTML_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static javax.ws.rs.core.MediaType.TEXT_PLAIN;
import static javax.ws.rs.core.Response.created;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.OK;
import static org.apache.commons.lang.ArrayUtils.contains;
import static org.apache.http.HttpStatus.SC_BAD_GATEWAY;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;
import static org.apache.http.HttpStatus.SC_PRECONDITION_FAILED;
import static org.apache.jena.riot.WebContent.contentTypeSPARQLUpdate;
import static org.apache.jena.riot.WebContent.contentTypeToLang;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3_ALT1;
import static org.fcrepo.http.commons.domain.RDFMediaType.N3_ALT2;
import static org.fcrepo.http.commons.domain.RDFMediaType.NTRIPLES;
import static org.fcrepo.http.commons.domain.RDFMediaType.RDF_XML;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE;
import static org.fcrepo.http.commons.domain.RDFMediaType.TURTLE_X;
import static org.fcrepo.jcr.FedoraJcrTypes.FEDORA_DATASTREAM;
import static org.fcrepo.jcr.FedoraJcrTypes.FEDORA_OBJECT;
import static org.fcrepo.kernel.RdfLexicon.FIRST_PAGE;
import static org.fcrepo.kernel.RdfLexicon.LDP_NAMESPACE;
import static org.fcrepo.kernel.RdfLexicon.NEXT_PAGE;
import static org.fcrepo.kernel.rdf.GraphProperties.PROBLEMS_MODEL_NAME;
import static org.modeshape.jcr.api.JcrConstants.JCR_CONTENT;
import static org.slf4j.LoggerFactory.getLogger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.jcr.ItemExistsException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.CacheControl;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.EntityTag;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.UriInfo;

import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.sun.jersey.core.header.ContentDisposition;
import com.sun.jersey.multipart.FormDataParam;

import org.apache.commons.io.IOUtils;
import org.apache.jena.riot.Lang;
import org.fcrepo.http.commons.AbstractResource;
import org.fcrepo.http.commons.api.rdf.HttpIdentifierTranslator;
import org.fcrepo.http.commons.domain.MOVE;
import org.fcrepo.http.commons.domain.PATCH;
import org.fcrepo.http.commons.domain.COPY;
import org.fcrepo.http.commons.domain.Prefer;
import org.fcrepo.http.commons.domain.PreferTag;
import org.fcrepo.http.commons.session.InjectedSession;
import org.fcrepo.kernel.Datastream;
import org.fcrepo.kernel.FedoraResource;
import org.fcrepo.kernel.exception.InvalidChecksumException;
import org.fcrepo.kernel.rdf.IdentifierTranslator;
import org.fcrepo.kernel.rdf.HierarchyRdfContextOptions;
import org.fcrepo.kernel.utils.iterators.RdfStream;
import org.openrdf.util.iterators.Iterators;
import org.slf4j.Logger;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.codahale.metrics.annotation.Timed;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.rdf.model.Model;

/**
 * CRUD operations on Fedora Nodes
 */
@Component
@Scope("prototype")
@Path("/{path: .*}")
public class FedoraNodes extends AbstractResource {

    @InjectedSession
    protected Session session;

    private static final Logger LOGGER = getLogger(FedoraNodes.class);

    /**
     * Retrieve the node profile
     *
     * @param pathList
     * @param offset with limit, control the pagination window of details for
     *        child nodes
     * @param limit with offset, control the pagination window of details for
     *        child nodes
     * @param request
     * @param uriInfo
     * @return
     * @throws RepositoryException
     */
    @GET
    @Produces({TURTLE, N3, N3_ALT2, RDF_XML, NTRIPLES, APPLICATION_XML, TEXT_PLAIN, TURTLE_X,
            TEXT_HTML, APPLICATION_XHTML_XML})
    public RdfStream describe(@PathParam("path") final List<PathSegment> pathList,
            @QueryParam("offset") @DefaultValue("0") final int offset,
            @QueryParam("limit")  @DefaultValue("-1") final int limit,
            @HeaderParam("Prefer") final Prefer prefer,
            @Context final Request request,
            @Context final HttpServletResponse servletResponse,
            @Context final UriInfo uriInfo) throws RepositoryException {
        final String path = toPath(pathList);
        LOGGER.trace("Getting profile for: {}", path);

        final FedoraResource resource = nodeService.getObject(session, path);

        final EntityTag etag = new EntityTag(resource.getEtagValue());
        final Date date = resource.getLastModifiedDate();
        final Date roundedDate = new Date();
        if (date != null) {
            roundedDate.setTime(date.getTime() - date.getTime() % 1000);
        }
        final ResponseBuilder builder =
            request.evaluatePreconditions(roundedDate, etag);
        if (builder != null) {
            final CacheControl cc = new CacheControl();
            cc.setMaxAge(0);
            cc.setMustRevalidate(true);
            // here we are implicitly emitting a 304
            // the exception is not an error, it's genuinely
            // an exceptional condition
            throw new WebApplicationException(builder.cacheControl(cc)
                    .lastModified(date).tag(etag).build());
        }
        final HttpIdentifierTranslator subjects =
            new HttpIdentifierTranslator(session, this.getClass(), uriInfo);

        final RdfStream rdfStream =
            resource.getTriples(subjects).session(session)
                    .topic(subjects.getSubject(resource.getNode().getPath())
                            .asNode());

        final PreferTag returnPreference;

        if (prefer != null && prefer.hasReturn()) {
            returnPreference = prefer.getReturn();
        } else {
            returnPreference = new PreferTag("");
        }

        if (!returnPreference.getValue().equals("minimal")) {
            String include = returnPreference.getParams().get("include");
            if (include == null) {
                include = "";
            }

            String omit = returnPreference.getParams().get("omit");
            if (omit == null) {
                omit = "";
            }

            final String[] includes = include.split(" ");
            final String[] omits = omit.split(" ");

            if (limit >= 0) {
                final Node firstPage =
                    createURI(uriInfo.getRequestUriBuilder().replaceQueryParam("offset", 0)
                                  .replaceQueryParam("limit", limit).build()
                                  .toString().replace("&", "&amp;"));
                rdfStream.concat(create(subjects.getContext().asNode(), FIRST_PAGE.asNode(), firstPage));
                servletResponse.addHeader("Link", firstPage + ";rel=\"first\"");

                if ( resource.getNode().getNodes().getSize() > (offset + limit) ) {
                    final Node nextPage =
                        createURI(uriInfo.getRequestUriBuilder().replaceQueryParam("offset", offset + limit)
                                  .replaceQueryParam("limit", limit).build()
                                  .toString().replace("&", "&amp;"));
                    rdfStream.concat(create(subjects.getContext().asNode(), NEXT_PAGE.asNode(), nextPage));
                    servletResponse.addHeader("Link", nextPage + ";rel=\"next\"");
                }
            }

            List<String> appliedIncludes = new ArrayList<>();

            final HierarchyRdfContextOptions hierarchyRdfContextOptions = new HierarchyRdfContextOptions(limit, offset);
            hierarchyRdfContextOptions.containment =
                (!contains(includes, LDP_NAMESPACE + "PreferEmptyContainer") ||
                     contains(includes, LDP_NAMESPACE + "PreferContainment"))
                    && !contains(omits, LDP_NAMESPACE + "PreferContainment");

            hierarchyRdfContextOptions.membership =
                (!contains(includes, LDP_NAMESPACE + "PreferEmptyContainer") ||
                     contains(includes, LDP_NAMESPACE + "PreferMembership"))
                    && !contains(omits, LDP_NAMESPACE + "PreferMembership");

            if (hierarchyRdfContextOptions.membershipEnabled()) {
                appliedIncludes.add(LDP_NAMESPACE + "PreferMembership");
            }

            if (hierarchyRdfContextOptions.containmentEnabled()) {
                appliedIncludes.add(LDP_NAMESPACE + "PreferContainment");
            }

            rdfStream.concat(resource.getHierarchyTriples(subjects, hierarchyRdfContextOptions));

            final String preferences = "return=representation; include=\""
                                           + Iterators.toString(appliedIncludes.iterator(), " ") + "\"";
            servletResponse.addHeader("Preference-Applied", preferences);

        } else {
            servletResponse.addHeader("Preference-Applied", "return=minimal");
        }
        servletResponse.addHeader("Vary", "Prefer");



        if (!etag.getValue().isEmpty()) {
            servletResponse.addHeader("ETag", etag.toString());
        }

        if (resource.getLastModifiedDate() != null) {
            servletResponse.addDateHeader("Last-Modified", resource
                    .getLastModifiedDate().getTime());
        }

        if (resource.hasContent()) {
            servletResponse.addHeader("Link", subjects.getSubject(
                resource.getNode().getNode(JCR_CONTENT).getPath()) + ";rel=\"describes\"");
        }

        servletResponse.addHeader("Accept-Patch", contentTypeSPARQLUpdate);
        servletResponse.addHeader("Link", LDP_NAMESPACE + "Resource;rel=\"type\"");
        servletResponse.addHeader("Link", LDP_NAMESPACE + "DirectContainer;rel=\"type\"");

        addResponseInformationToStream(resource, rdfStream, uriInfo,
                subjects);

        return rdfStream;


    }

    /**
     * Update an object using SPARQL-UPDATE
     *
     * @param pathList
     * @return 201
     * @throws RepositoryException
     * @throws org.fcrepo.kernel.exception.InvalidChecksumException
     * @throws IOException
     */
    @PATCH
    @Consumes({contentTypeSPARQLUpdate})
    @Timed
    public Response updateSparql(@PathParam("path")
            final List<PathSegment> pathList,
            @Context
            final UriInfo uriInfo,
            final InputStream requestBodyStream,
            @Context final Request request)
        throws RepositoryException, IOException {

        final String path = toPath(pathList);
        LOGGER.debug("Attempting to update path: {}", path);

        try {

            if (requestBodyStream != null) {

                final FedoraResource resource =
                        nodeService.getObject(session, path);


                final EntityTag etag = new EntityTag(resource.getEtagValue());
                final Date date = resource.getLastModifiedDate();
                final Date roundedDate = new Date();

                if (date != null) {
                    roundedDate.setTime(date.getTime() - date.getTime() % 1000);
                }

                final ResponseBuilder builder =
                    request.evaluatePreconditions(roundedDate, etag);

                if (builder != null) {
                    throw new WebApplicationException(builder.build());
                }

                final Dataset properties = resource.updatePropertiesDataset(new HttpIdentifierTranslator(
                        session, FedoraNodes.class, uriInfo), IOUtils
                        .toString(requestBodyStream));


                final Model problems = properties.getNamedModel(PROBLEMS_MODEL_NAME);
                if (!problems.isEmpty()) {
                    LOGGER.info(
                                   "Found these problems updating the properties for {}: {}",
                                   path, problems);
                    return status(FORBIDDEN).entity(problems.toString())
                            .build();

                }

                session.save();
                versionService.nodeUpdated(resource.getNode());

                return status(SC_NO_CONTENT).build();
            }
            return status(SC_BAD_REQUEST).entity(
                    "SPARQL-UPDATE requests must have content!").build();

        } finally {
            session.logout();
        }
    }

    /**
     * Replace triples with triples from a new model
     * @param pathList
     * @param uriInfo
     * @param requestContentType
     * @param requestBodyStream
     * @return
     * @throws Exception
     */
    @PUT
    @Consumes({TURTLE, N3, N3_ALT1, N3_ALT2, RDF_XML, NTRIPLES})
    @Timed
    public Response createOrReplaceObjectRdf(
            @PathParam("path") final List<PathSegment> pathList,
            @Context final UriInfo uriInfo,
            @HeaderParam("Content-Type")
            final MediaType requestContentType,
            final InputStream requestBodyStream,
            @Context
            final Request request) throws RepositoryException, ParseException,
            IOException, InvalidChecksumException, URISyntaxException {
        final String path = toPath(pathList);
        LOGGER.debug("Attempting to replace path: {}", path);
        try {

            if (!nodeService.exists(session, path)) {
                return createObject(pathList, null, null, null,
                        requestContentType, null, uriInfo, requestBodyStream);
            }

            final FedoraResource resource =
                nodeService.getObject(session, path);

            final Date date = resource.getLastModifiedDate();
            final Date roundedDate = new Date();


            final EntityTag etag = new EntityTag(resource.getEtagValue());

            if (date != null) {
                roundedDate.setTime(date.getTime() - date.getTime() % 1000);
            }

            final ResponseBuilder builder =
                request.evaluatePreconditions(roundedDate, etag);

            if (builder != null) {
                throw new WebApplicationException(builder.build());
            }

            final HttpIdentifierTranslator graphSubjects =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);

            if (requestContentType != null && requestBodyStream != null)  {
                final String contentType = requestContentType.toString();

                final String format = contentTypeToLang(contentType).getName()
                                          .toUpperCase();

                final Model inputModel =
                    createDefaultModel().read(requestBodyStream,
                            graphSubjects.getSubject(resource.getNode().getPath()).toString(), format);

                resource.replaceProperties(graphSubjects, inputModel);
            }

            session.save();
            versionService.nodeUpdated(resource.getNode());

            return status(SC_NO_CONTENT).lastModified(resource.getLastModifiedDate()).build();
        } finally {
            session.logout();
        }
    }

    /**
     * Creates a new object.
     *
     * @param pathList
     * @return 201
     * @throws Exception
     */
    @POST
    @Timed
    public Response createObject(@PathParam("path")
            final List<PathSegment> pathList,
            @QueryParam("mixin")
            final String mixin,
            @QueryParam("checksum")
            final String checksum,
            @HeaderParam("Content-Disposition") final String contentDisposition,
            @HeaderParam("Content-Type")
            final MediaType requestContentType,
            @HeaderParam("Slug")
            final String slug,
            @Context
            final UriInfo uriInfo, final InputStream requestBodyStream)
        throws RepositoryException, ParseException, IOException,
                   InvalidChecksumException, URISyntaxException {

        final String newObjectPath;
        final String path = toPath(pathList);

        final HttpIdentifierTranslator idTranslator =
            new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);

        if (nodeService.exists(session, path)) {
            String pid;

            if (slug != null) {
                pid = slug;
            } else {
                pid = pidMinter.mintPid();
            }
            // reverse translate the proffered or created identifier
            LOGGER.trace("Using external identifier {} to create new resource.", pid);
            LOGGER.trace("Using prefixed external identifier {} to create new resource.", uriInfo.getBaseUri() + "/"
                    + pid);
            pid = idTranslator.getPathFromSubject(createResource(uriInfo.getBaseUri() + "/" + pid));
            // remove leading slash left over from translation
            pid = pid.substring(1, pid.length());
            LOGGER.trace("Using internal identifier {} to create new resource.", pid);
            newObjectPath = path + "/" + pid;
        } else {
            newObjectPath = path;
        }

        LOGGER.debug("Attempting to ingest with path: {}", newObjectPath);

        try {
            if (nodeService.exists(session, newObjectPath)) {
                return status(SC_CONFLICT).entity(
                        path + " is an existing resource!").build();
            }

            final URI checksumURI;

            if (checksum != null && !checksum.equals("")) {
                checksumURI = new URI(checksum);
            } else {
                checksumURI = null;
            }

            final String objectType;

            if (mixin != null) {
                objectType = mixin;
            } else {
                if (requestContentType != null) {
                    final String s = requestContentType.toString();
                    if (s.equals(contentTypeSPARQLUpdate) || contentTypeToLang(s) != null) {
                        objectType = FEDORA_OBJECT;
                    } else {
                        objectType = FEDORA_DATASTREAM;
                    }
                } else {
                    objectType = FEDORA_OBJECT;
                }
            }

            final FedoraResource result;

            switch (objectType) {
                case FEDORA_OBJECT:
                    result = objectService.createObject(session, newObjectPath);
                    break;
                case FEDORA_DATASTREAM:
                    result = datastreamService.createDatastream(session, newObjectPath);
                    break;
                default:
                    throw new WebApplicationException(clientError().entity(
                            "Unknown object type " + objectType).build());
            }

            if (requestBodyStream != null) {

                final MediaType contentType =
                    requestContentType != null ? requestContentType
                        : APPLICATION_OCTET_STREAM_TYPE;

                final String contentTypeString = contentType.toString();

                if (contentTypeString.equals(contentTypeSPARQLUpdate)) {
                    result.updatePropertiesDataset(idTranslator, IOUtils.toString(requestBodyStream));
                } else if (contentTypeToLang(contentTypeString) != null) {

                    final Lang lang = contentTypeToLang(contentTypeString);

                    if (lang == null) {
                        throw new WebApplicationException(notAcceptable().entity(
                                "Invalid Content type " + contentType).build());
                    }

                    final String format = lang.getName()
                                              .toUpperCase();

                    final Model inputModel =
                        createDefaultModel().read(requestBodyStream,
                                idTranslator.getSubject(result.getNode().getPath()).toString(), format);

                    result.replaceProperties(idTranslator, inputModel);
                } else if (result instanceof Datastream) {

                    final String originalFileName;

                    if (contentDisposition != null) {
                        final ContentDisposition disposition = new ContentDisposition(contentDisposition);
                        originalFileName = disposition.getFileName();
                    } else {
                        originalFileName = null;
                    }

                    datastreamService.createDatastreamNode(session,
                            newObjectPath, contentTypeString, originalFileName, requestBodyStream, checksumURI);

                }
            }

            session.save();
            versionService.nodeUpdated(result.getNode());

            LOGGER.debug("Finished creating {} with path: {}", mixin, newObjectPath);

            final URI location;
            if (result.hasContent()) {
                location = new URI(idTranslator.getSubject(result.getNode().getNode(JCR_CONTENT).getPath()).getURI());
            } else {
                location = new URI(idTranslator.getSubject(result.getNode().getPath()).getURI());
            }

            return created(location).lastModified(result.getLastModifiedDate())
                    .entity(location.toString()).build();

        } finally {
            session.logout();
        }
    }

    /**
     * Create a new object from a multipart/form-data POST request
     * @param pathList
     * @param mixin
     * @param slug
     * @param uriInfo
     * @param file
     * @return
     * @throws Exception
     */
    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Timed
    public Response createObjectFromFormPost(
                                                @PathParam("path") final List<PathSegment> pathList,
                                                @FormDataParam("mixin") final String mixin,
                                                @FormDataParam("slug") final String slug,
                                                @Context final UriInfo uriInfo,
                                                @FormDataParam("file") final InputStream file
    ) throws RepositoryException, URISyntaxException, InvalidChecksumException, ParseException, IOException {

        return createObject(pathList, mixin, null, null, null, slug, uriInfo, file);

    }

    /**
     * Deletes an object.
     *
     * @param pathList
     * @return
     * @throws RepositoryException
     */
    @DELETE
    @Timed
    public Response deleteObject(@PathParam("path")
            final List<PathSegment> pathList,
            @Context final Request request) throws RepositoryException {

        try {

            final String path = toPath(pathList);

            final FedoraResource resource =
                nodeService.getObject(session, path);


            final EntityTag etag = new EntityTag(resource.getEtagValue());
            final Date date = resource.getLastModifiedDate();
            final Date roundedDate = new Date();

            if (date != null) {
                roundedDate.setTime(date.getTime() - date.getTime() % 1000);
            }

            final ResponseBuilder builder =
                request.evaluatePreconditions(roundedDate, etag);

            if (builder != null) {
                throw new WebApplicationException(builder.build());
            }

            nodeService.deleteObject(session, path);
            session.save();
            return noContent().build();
        } finally {
            session.logout();
        }
    }

    /**
     * Copies an object from one path to another
     */
    @COPY
    @Timed
    public Response copyObject(@PathParam("path") final List<PathSegment> path,
                               @HeaderParam("Destination") final String destinationUri)
        throws RepositoryException, URISyntaxException {

        try {

            final IdentifierTranslator subjects =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);

            if (!nodeService.exists(session, toPath(path))) {
                return status(SC_CONFLICT).entity("The source path does not exist").build();
            }

            final String destination =
                subjects.getPathFromSubject(ResourceFactory.createResource(destinationUri));

            if (destination == null) {
                return status(SC_BAD_GATEWAY).entity("Destination was not a valid resource path").build();
            }

            nodeService.copyObject(session, toPath(path), destination);
            session.save();
            versionService.nodeUpdated(session, destination);
            return created(new URI(destinationUri)).build();
        } catch (final ItemExistsException e) {
            throw new WebApplicationException(e,
                status(SC_PRECONDITION_FAILED).entity("Destination resource already exists").build());
        } catch (final PathNotFoundException e) {
            throw new WebApplicationException(e, status(SC_CONFLICT).entity(
                    "There is no node that will serve as the parent of the moved item")
                    .build());
        } finally {
            session.logout();
        }

    }

    /**
     * Copies an object from one path to another
     */
    @MOVE
    @Timed
    public Response moveObject(@PathParam("path") final List<PathSegment> pathList,
                               @HeaderParam("Destination") final String destinationUri,
                               @Context final Request request)
        throws RepositoryException, URISyntaxException {

        try {

            final String path = toPath(pathList);

            if (!nodeService.exists(session, path)) {
                return status(SC_CONFLICT).entity("The source path does not exist").build();
            }


            final FedoraResource resource =
                nodeService.getObject(session, path);


            final EntityTag etag = new EntityTag(resource.getEtagValue());
            final Date date = resource.getLastModifiedDate();
            final Date roundedDate = new Date();

            if (date != null) {
                roundedDate.setTime(date.getTime() - date.getTime() % 1000);
            }

            final ResponseBuilder builder =
                request.evaluatePreconditions(roundedDate, etag);

            if (builder != null) {
                throw new WebApplicationException(builder.build());
            }

            final IdentifierTranslator subjects =
                new HttpIdentifierTranslator(session, FedoraNodes.class, uriInfo);

            final String destination =
                subjects.getPathFromSubject(ResourceFactory.createResource(destinationUri));

            if (destination == null) {
                return status(SC_BAD_GATEWAY).entity("Destination was not a valid resource path").build();
            }

            nodeService.moveObject(session, path, destination);
            session.save();
            versionService.nodeUpdated(session, destination);
            return created(new URI(destinationUri)).build();
        } catch (final ItemExistsException e) {
            throw new WebApplicationException(e,
                status(SC_PRECONDITION_FAILED).entity("Destination resource already exists").build());
        } catch (final PathNotFoundException e) {
            throw new WebApplicationException(e, status(SC_CONFLICT).entity(
                    "There is no node that will serve as the parent of the moved item")
                    .build());
        } finally {
            session.logout();
        }

    }

    /**
     * Outputs information about the supported HTTP methods, etc.
     */
    @OPTIONS
    @Timed
    public Response options(@PathParam("path") final List<PathSegment> pathList,
                            @Context final HttpServletResponse servletResponse)
        throws RepositoryException {
        servletResponse.addHeader("Allow", "MOVE,COPY,DELETE,POST,HEAD,GET,PUT,PATCH,OPTIONS");
        final String rdfTypes = TURTLE + "," + N3 + "," + N3_ALT1 + ","
                + N3_ALT2 + "," + RDF_XML + "," + NTRIPLES;
        servletResponse.addHeader("Accept-Patch", contentTypeSPARQLUpdate);
        servletResponse.addHeader("Accept-Post", rdfTypes + "," + MediaType.MULTIPART_FORM_DATA
                + "," + contentTypeSPARQLUpdate);
        return status(OK).build();
    }

}
