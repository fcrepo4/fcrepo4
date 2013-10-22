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

package org.fcrepo.kernel.utils;

import static com.google.common.collect.Iterables.any;
import static com.hp.hpl.jena.graph.Triple.create;
import static com.hp.hpl.jena.rdf.model.ModelFactory.createDefaultModel;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static javax.jcr.PropertyType.REFERENCE;
import static javax.jcr.PropertyType.STRING;
import static javax.jcr.PropertyType.UNDEFINED;
import static javax.jcr.PropertyType.URI;
import static javax.jcr.PropertyType.WEAKREFERENCE;
import static org.fcrepo.kernel.RdfLexicon.HAS_MEMBER_OF_RESULT;
import static org.fcrepo.kernel.RdfLexicon.REPOSITORY_NAMESPACE;
import static org.fcrepo.kernel.utils.FedoraTypesUtils.getValueFactory;
import static org.fcrepo.kernel.utils.NamespaceTools.getNamespaceRegistry;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Iterator;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.nodetype.NodeType;

import org.fcrepo.kernel.RdfLexicon;
import org.fcrepo.kernel.rdf.GraphSubjects;
import org.fcrepo.kernel.rdf.impl.DefaultGraphSubjects;
import org.fcrepo.kernel.rdf.impl.FixityRdfContext;
import org.fcrepo.kernel.rdf.impl.HierarchyRdfContext;
import org.fcrepo.kernel.rdf.impl.NamespaceContext;
import org.fcrepo.kernel.rdf.impl.PropertiesRdfContext;
import org.fcrepo.kernel.rdf.impl.VersionsRdfContext;
import org.fcrepo.kernel.services.LowLevelStorageService;
import org.fcrepo.kernel.services.functions.GetClusterConfiguration;
import org.fcrepo.kernel.utils.iterators.RdfStream;
import org.modeshape.jcr.api.NamespaceRegistry;
import org.slf4j.Logger;

import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.hp.hpl.jena.datatypes.RDFDatatype;
import com.hp.hpl.jena.datatypes.xsd.XSDDateTime;
import com.hp.hpl.jena.rdf.model.Literal;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.RDFNode;
import com.hp.hpl.jena.rdf.model.Resource;

/**
 * A set of helpful tools for converting JCR properties to RDF
 *
 * @author Chris Beer
 * @date May 10, 2013
 */
public class JcrRdfTools {

    private static final Logger LOGGER = getLogger(JcrRdfTools.class);

    /**
     * A map of JCR namespaces to Fedora's RDF namespaces
     */
    public static BiMap<String, String> jcrNamespacesToRDFNamespaces =
        ImmutableBiMap.of("http://www.jcp.org/jcr/1.0",
                RdfLexicon.REPOSITORY_NAMESPACE);

    /**
     * A map of Fedora's RDF namespaces to the JCR equivalent
     */
    public static BiMap<String, String> rdfNamespacesToJcrNamespaces =
        jcrNamespacesToRDFNamespaces.inverse();

    private static GetClusterConfiguration getClusterConfiguration =
        new GetClusterConfiguration();

    private LowLevelStorageService llstore;

    private final GraphSubjects graphSubjects;

    private Session session;

    /**
     * Factory method to create a new JcrRdfTools utility with a graph subjects
     * converter
     *
     * @param graphSubjects
     */
    public JcrRdfTools(final GraphSubjects graphSubjects) {
        this(graphSubjects, null, null);
    }

    /**
     * Factory method to create a new JcrRdfTools utility with a graph subjects
     * converter
     *
     * @param graphSubjects
     * @param session
     */
    public JcrRdfTools(final GraphSubjects graphSubjects, final Session session) {
        this(graphSubjects, session, null);
    }

    /**
     * Contructor with even more context.
     *
     * @param graphSubjects
     * @param session
     * @param lls
     */
    public JcrRdfTools(final GraphSubjects graphSubjects,
            final Session session, final LowLevelStorageService lls) {
        this.graphSubjects = graphSubjects;
        this.session = session;
        this.llstore = lls;
    }

    /**
     * Factory method to create a new JcrRdfTools instance
     *
     * @param graphSubjects
     * @return
     */
    public static JcrRdfTools withContext(final GraphSubjects graphSubjects) {
        return new JcrRdfTools(graphSubjects);
    }

    /**
     * Factory method to create a new JcrRdfTools instance
     *
     * @param graphSubjects
     * @param session
     * @return
     */
    public static JcrRdfTools withContext(final GraphSubjects graphSubjects,
        final Session session) {
        if (graphSubjects == null) {
            return new JcrRdfTools(new DefaultGraphSubjects(session), session);
        } else {
            return new JcrRdfTools(graphSubjects, session);
        }
    }

    /**
     * Factory method to create a new JcrRdfTools instance with full context.
     *
     * @param graphSubjects
     * @param session
     * @param lls
     * @return
     */
    public static JcrRdfTools withContext(final GraphSubjects graphSubjects,
            final Session session, final LowLevelStorageService lls) {
        return new JcrRdfTools(graphSubjects, session, lls);
    }

    /**
     * Convert a Fedora RDF Namespace into its JCR equivalent
     *
     * @param rdfNamespaceUri a namespace from an RDF document
     * @return the JCR namespace, or the RDF namespace if no matching JCR
     *         namespace is found
     */
    public static String getJcrNamespaceForRDFNamespace(
            final String rdfNamespaceUri) {
        if (rdfNamespacesToJcrNamespaces.containsKey(rdfNamespaceUri)) {
            return rdfNamespacesToJcrNamespaces.get(rdfNamespaceUri);
        } else {
            return rdfNamespaceUri;
        }
    }

    /**
     * Convert a JCR namespace into an RDF namespace fit for downstream
     * consumption.
     *
     * @param jcrNamespaceUri a namespace from the JCR NamespaceRegistry
     * @return an RDF namespace for downstream consumption.
     */
    public static String getRDFNamespaceForJcrNamespace(
            final String jcrNamespaceUri) {
        if (jcrNamespacesToRDFNamespaces.containsKey(jcrNamespaceUri)) {
            return jcrNamespacesToRDFNamespaces.get(jcrNamespaceUri);
        } else {
            return jcrNamespaceUri;
        }
    }

    /**
     * Get a model in which to collect statements of RDF extraction problems
     *
     * @return
     */
    public static Model getProblemsModel() {
        return createDefaultModel();
    }

    /**
     * Using the same graph subjects, create a new JcrRdfTools with the given
     * session
     *
     * @param session
     * @return
     */
    public JcrRdfTools withSession(final Session session) {
        return new JcrRdfTools(graphSubjects, session);
    }

    /**
     * Create a default Jena Model populated with the registered JCR namespaces
     *
     * @return
     * @throws RepositoryException
     */
    public Model getJcrPropertiesModel() throws RepositoryException {
        return new NamespaceContext(session).asModel();
    }

    /**
     * Get an RDF model for the given JCR NodeIterator
     *
     * @param nodeIterator
     * @param iteratorSubject
     * @return
     * @throws RepositoryException
     */
    public Model getJcrPropertiesModel(final Iterator<Node> nodeIterator,
            final Resource iteratorSubject) throws RepositoryException {

        final RdfStream results = new RdfStream();
        while (nodeIterator.hasNext()) {
            final Node node = nodeIterator.next();
            results.concat(new PropertiesRdfContext(node, graphSubjects,
                    llstore));
            if (iteratorSubject != null) {
                results.concat(singleton(create(iteratorSubject.asNode(),
                        HAS_MEMBER_OF_RESULT.asNode(), graphSubjects
                                .getGraphSubject(node).asNode())));
            }
        }
        return results.asModel();
    }

    /**
     * Get an {@link Model} for a node that includes all its own JCR properties,
     * as well as the properties of its immediate children. TODO add triples for
     * root node, ala addRepositoryMetricsToModel()
     *
     * @param node
     * @return
     * @throws RepositoryException
     */
    public Model getJcrPropertiesModel(final Node node) throws RepositoryException {
        final RdfStream namespaceContext = new NamespaceContext(session);
        return namespaceContext.concat(
                new PropertiesRdfContext(node, graphSubjects, llstore))
                .asModel();
    }

    /**
     * Get a Jena RDF model for the JCR version history information for a node
     *
     * @param node
     * @return
     * @throws RepositoryException
     */
    public Model getJcrVersionPropertiesModel(final Node node)
        throws RepositoryException {
        return new VersionsRdfContext(node, graphSubjects, llstore).asModel();
    }

    /**
     * Serialize the JCR fixity information in a Jena Model
     *
     * @param node
     * @param blobs
     * @return
     * @throws RepositoryException
     */
    public Model getJcrPropertiesModel(final Node node,
            final Iterable<FixityResult> blobs) throws RepositoryException {
        return new NamespaceContext(session).concat(
                new FixityRdfContext(node, graphSubjects, llstore, blobs))
                .concat(new PropertiesRdfContext(node, graphSubjects, llstore))
                .asModel();
    }

    /**
     * Get an RDF model of the registered JCR namespaces
     *
     * @return
     * @throws RepositoryException
     */
    public Model getJcrNamespaceModel() throws RepositoryException {
        return new NamespaceContext(session).asModel();
    }

    /**
     * Add the properties of a Node's parent and immediate children (as well as
     * the jcr:content of children) to the given RDF model
     *
     * @param node
     * @param offset
     * @param limit @throws RepositoryException
     */
    public Model getJcrTreeModel(final Node node, final long offset,
            final int limit) throws RepositoryException {
        return new HierarchyRdfContext(node, graphSubjects, llstore).asModel();
    }

    /**
     * Decides whether the RDF represetnation of this {@link Node} will receive LDP Container status.
     *
     * @param node
     * @return
     * @throws RepositoryException
     */
    public static boolean isContainer(final Node node) throws RepositoryException {
        return HAS_CHILD_NODE_DEFINITIONS.apply(node.getPrimaryNodeType())
                || any(ImmutableList.copyOf(node.getMixinNodeTypes()),
                        HAS_CHILD_NODE_DEFINITIONS);
    }

    static Predicate<NodeType> HAS_CHILD_NODE_DEFINITIONS =
        new Predicate<NodeType>() {

            @Override
            public boolean apply(final NodeType input) {
                return input.getChildNodeDefinitions().length > 0;
            }
        };

    /**
     * Determine if a predicate is an internal property of a node (and should
     * not be modified from external sources)
     *
     * @param subjectNode
     * @param predicate
     * @return
     */
    public boolean isInternalProperty(final Node subjectNode,
            final Resource predicate) {
        switch (predicate.getNameSpace()) {
            case REPOSITORY_NAMESPACE:
            case "http://www.jcp.org/jcr/1.0":
            case "http://www.w3.org/ns/ldp#":
                return true;
            default:
                return false;
        }
    }

    /**
     * Create a JCR value from an RDFNode, either by using the given JCR
     * PropertyType or by looking at the RDFNode Datatype
     *
     * @param data an RDF Node (possibly with a DataType)
     * @param type a JCR PropertyType value
     * @return a JCR Value
     * @throws javax.jcr.RepositoryException
     */
    Value createValue(final Node node, final RDFNode data, final int type)
        throws RepositoryException {
        final ValueFactory valueFactory = getValueFactory.apply(node);
        assert (valueFactory != null);

        if (data.isURIResource()
                && (type == REFERENCE || type == WEAKREFERENCE)) {
            // reference to another node (by path)
            final Node nodeFromGraphSubject =
                graphSubjects.getNodeFromGraphSubject(data.asResource());
            return valueFactory.createValue(nodeFromGraphSubject,
                    type == WEAKREFERENCE);
        } else if (data.isURIResource() || type == URI) {
            // some random opaque URI
            return valueFactory.createValue(data.toString(), PropertyType.URI);
        } else if (data.isResource()) {
            // a non-URI resource (e.g. a blank node)
            return valueFactory.createValue(data.toString(), UNDEFINED);
        } else if (data.isLiteral() && type == UNDEFINED) {
            // the JCR schema doesn't know what this should be; so introspect
            // the RDF and try to figure it out
            final Literal literal = data.asLiteral();
            final RDFDatatype dataType = literal.getDatatype();
            final Object rdfValue = literal.getValue();

            if (rdfValue instanceof Boolean) {
                return valueFactory.createValue((Boolean) rdfValue);
            } else if (rdfValue instanceof Byte
                    || (dataType != null && dataType.getJavaClass() == Byte.class)) {
                return valueFactory.createValue(literal.getByte());
            } else if (rdfValue instanceof Double) {
                return valueFactory.createValue((Double) rdfValue);
            } else if (rdfValue instanceof Float) {
                return valueFactory.createValue((Float) rdfValue);
            } else if (rdfValue instanceof Long
                    || (dataType != null && dataType.getJavaClass() == Long.class)) {
                return valueFactory.createValue(literal.getLong());
            } else if (rdfValue instanceof Short
                    || (dataType != null && dataType.getJavaClass() == Short.class)) {
                return valueFactory.createValue(literal.getShort());
            } else if (rdfValue instanceof Integer) {
                return valueFactory.createValue((Integer) rdfValue);
            } else if (rdfValue instanceof XSDDateTime) {
                return valueFactory.createValue(((XSDDateTime) rdfValue)
                        .asCalendar());
            } else {
                return valueFactory.createValue(literal.getString(), STRING);
            }

        } else {
            return valueFactory.createValue(data.asLiteral().getString(), type);
        }
    }

    /**
     * Given an RDF predicate value (namespace URI + local name), figure out
     * what JCR property to use
     *
     * @param node the JCR node we want a property for
     * @param predicate the predicate to map to a property name
     * @return
     * @throws RepositoryException
     */
    String getPropertyNameFromPredicate(final Node node,
        final com.hp.hpl.jena.rdf.model.Property predicate)
        throws RepositoryException {
        final Map<String, String> s = emptyMap();
        return getPropertyNameFromPredicate(node, predicate, s);

    }

    /**
     * Given an RDF predicate value (namespace URI + local name), figure out
     * what JCR property to use
     *
     * @param node the JCR node we want a property for
     * @param predicate the predicate to map to a property name
     * @param namespaceMapping prefix => uri namespace mapping
     * @return the JCR property name
     * @throws RepositoryException
     */
    String getPropertyNameFromPredicate(final Node node, final com.hp.hpl.jena.rdf.model.Property predicate,
        final Map<String, String> namespaceMapping) throws RepositoryException {

        final String prefix;

        final String namespace =
            getJcrNamespaceForRDFNamespace(predicate.getNameSpace());

        final NamespaceRegistry namespaceRegistry = getNamespaceRegistry(node);

        assert (namespaceRegistry != null);

        if (namespaceRegistry.isRegisteredUri(namespace)) {
            prefix = namespaceRegistry.getPrefix(namespace);
        } else {
            final ImmutableBiMap<String, String> nsMap =
                ImmutableBiMap.copyOf(namespaceMapping);
            if (nsMap.containsValue(namespace)
                    && !namespaceRegistry.isRegisteredPrefix(nsMap.inverse()
                            .get(namespace))) {
                prefix = nsMap.inverse().get(namespace);
                namespaceRegistry.registerNamespace(prefix, namespace);
            } else {
                prefix = namespaceRegistry.registerNamespace(namespace);
            }
        }

        final String localName = predicate.getLocalName();

        final String propertyName = prefix + ":" + localName;

        LOGGER.trace("Took RDF predicate {} and translated it to "
                + "JCR property {}", predicate, propertyName);

        return propertyName;

    }

    /**
     * Set the function used to get the cluster configuration for Infinispan
     */
    public static void setGetClusterConfiguration(
            final GetClusterConfiguration newClusterConfiguration) {
        getClusterConfiguration = newClusterConfiguration;
    }

    /**
     * Set the Low-level storage server implementation
     */
    public void setLlstore(final LowLevelStorageService lowLevelStorageService) {
        llstore = lowLevelStorageService;
    }

}
