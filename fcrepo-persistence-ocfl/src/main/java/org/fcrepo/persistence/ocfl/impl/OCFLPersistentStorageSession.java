/*
 * Licensed to DuraSpace under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership.
 *
 * DuraSpace licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fcrepo.persistence.ocfl.impl;

import org.apache.commons.io.FileUtils;
import org.fcrepo.kernel.api.RdfStream;
import org.fcrepo.kernel.api.models.ResourceHeaders;
import org.fcrepo.kernel.api.operations.ResourceOperation;
import org.fcrepo.persistence.api.PersistentStorageSession;
import org.fcrepo.persistence.api.exceptions.PersistentItemNotFoundException;
import org.fcrepo.persistence.api.exceptions.PersistentSessionClosedException;
import org.fcrepo.persistence.api.exceptions.PersistentStorageException;
import org.fcrepo.persistence.ocfl.api.FedoraOCFLMappingNotFoundException;
import org.fcrepo.persistence.ocfl.api.FedoraToOcflObjectIndex;
import org.fcrepo.persistence.ocfl.api.OCFLObjectSession;
import org.fcrepo.persistence.ocfl.api.OCFLObjectSessionFactory;
import org.fcrepo.persistence.ocfl.api.Persister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeoutException;

import static java.lang.String.format;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.fcrepo.kernel.api.RdfLexicon.NON_RDF_SOURCE;
import static org.fcrepo.persistence.api.CommitOption.NEW_VERSION;
import static org.fcrepo.persistence.common.ResourceHeaderSerializationUtils.deserializeHeaders;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.getBinaryStream;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.getRdfStream;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.getSidecarSubpath;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.resolveExtensions;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.resolveVersionId;
import static org.fcrepo.persistence.ocfl.impl.OCFLPersistentStorageUtils.resovleOCFLSubpathFromResourceId;



/**
 * OCFL Persistent Storage class.
 *
 * @author whikloj
 * @since 2019-09-20
 */
public class OCFLPersistentStorageSession implements PersistentStorageSession {

    private static final Logger LOGGER = LoggerFactory.getLogger(OCFLPersistentStorageSession.class);

    private static final long AWAIT_TIMEOUT = 30000L;

    /**
     * Externally generated id for the session.
     */
    private final String sessionId;

    private final FedoraToOcflObjectIndex fedoraOcflIndex;

    private final Map<String, OCFLObjectSession> sessionMap;

    private Map<String, OCFLObjectSession> sessionsToRollback;

    private final Phaser phaser = new Phaser();

    private final List<Persister> persisterList = new ArrayList<>();

    private State state = State.COMMIT_NOT_STARTED;

    private final OCFLObjectSessionFactory objectSessionFactory;

    private final Path sessionStagingDir;

    private enum State {
        COMMIT_NOT_STARTED,
        COMMIT_STARTED,
        PREPARE_FAILED,
        COMMITTED,
        COMMIT_FAILED,
        ROLLING_BACK,
        ROLLED_BACK,
        ROLLBACK_FAILED;
    }

    /**
     * Constructor
     *
     * @param sessionId            session id.
     * @param fedoraOcflIndex      the index
     * @param objectSessionFactory the session factory
     */
    protected OCFLPersistentStorageSession(final String sessionId, final FedoraToOcflObjectIndex fedoraOcflIndex,
                                           final Path sessionStagingDir,
                                           final OCFLObjectSessionFactory objectSessionFactory) {
        this.sessionId = sessionId;
        this.fedoraOcflIndex = fedoraOcflIndex;
        this.objectSessionFactory = objectSessionFactory;
        this.sessionMap = new ConcurrentHashMap<>();
        this.sessionsToRollback = new HashMap<>();
        this.sessionStagingDir = sessionStagingDir;

        //load the persister list if empty
        persisterList.add(new CreateRDFSourcePersister(this.fedoraOcflIndex));
        persisterList.add(new UpdateRDFSourcePersister(this.fedoraOcflIndex));
        persisterList.add(new CreateNonRdfSourcePersister(this.fedoraOcflIndex));
        persisterList.add(new UpdateNonRdfSourcePersister(this.fedoraOcflIndex));
        persisterList.add(new DeleteResourcePersister(this.fedoraOcflIndex));
        persisterList.add(new CreateVersionPersister(this.fedoraOcflIndex));
        persisterList.add(new PurgeResourcePersister(this.fedoraOcflIndex));

    }

    /**
     * Constructor
     *
     * @param fedoraOcflIndex      the index
     * @param objectSessionFactory the session factory
     */
    protected OCFLPersistentStorageSession(final FedoraToOcflObjectIndex fedoraOcflIndex,
                                           final Path sessionStagingDir,
                                           final OCFLObjectSessionFactory objectSessionFactory) {
        this(null, fedoraOcflIndex, sessionStagingDir, objectSessionFactory);
    }

    @Override
    public String getId() {
        return this.sessionId;
    }

    @Override
    public void persist(final ResourceOperation operation) throws PersistentStorageException {
        actionNeedsWrite();
        ensureCommitNotStarted();

        try {
            phaser.register();

            //resolve the persister based on the operation
            final var persister = persisterList.stream().filter(p -> p.handle(operation)).findFirst().orElse(null);

            if (persister == null) {
                throw new UnsupportedOperationException(format("The %s is not yet supported", operation.getClass()));
            }

            //perform the operation
            persister.persist(this, operation);

        } finally {
            phaser.arriveAndDeregister();
        }

    }

    private void ensureCommitNotStarted() throws PersistentSessionClosedException {
        if (!state.equals(State.COMMIT_NOT_STARTED)) {
            throw new PersistentSessionClosedException("The session cannot be committed in the  " + state + " state");
        }
    }


    OCFLObjectSession findOrCreateSession(final String ocflId) {
        return this.sessionMap.computeIfAbsent(ocflId,
                key -> this.objectSessionFactory.create(key, sessionStagingDir));
    }

    @Override
    public ResourceHeaders getHeaders(final String identifier, final Instant version)
            throws PersistentStorageException {

        ensureCommitNotStarted();

        final FedoraOCFLMapping mapping = getFedoraOCFLMapping(identifier);
        final OCFLObjectSession objSession = findOrCreateSession(mapping.getOcflObjectId());
        final var rootIdentifier = mapping.getRootObjectIdentifier();
        final var ocflSubpath = resovleOCFLSubpathFromResourceId(rootIdentifier, identifier);
        final var sidecarSubpath = getSidecarSubpath(ocflSubpath);

        final InputStream headerStream;
        if (version != null) {
            final var versionId = resolveVersionId(objSession, version);
            headerStream = objSession.read(sidecarSubpath, versionId);
        } else {
            headerStream = objSession.read(sidecarSubpath);
        }

        return deserializeHeaders(headerStream);
    }

    private FedoraOCFLMapping getFedoraOCFLMapping(final String identifier) throws PersistentStorageException {
        try {
            return fedoraOcflIndex.getMapping(identifier);
        } catch (final FedoraOCFLMappingNotFoundException e) {
            throw new PersistentItemNotFoundException(e.getMessage());
        }
    }

    @Override
    public RdfStream getTriples(final String identifier, final Instant version)
            throws PersistentStorageException {
        ensureCommitNotStarted();

        final var mapping = getFedoraOCFLMapping(identifier);
        final var rootIdentifier = mapping.getRootObjectIdentifier();
        final var objSession = findOrCreateSession(mapping.getOcflObjectId());
        final var ocflSubpath = resovleOCFLSubpathFromResourceId(rootIdentifier, identifier);
        final var filePath = resolveExtensions(ocflSubpath, true);
        return getRdfStream(identifier, objSession, filePath, version);
    }

    @Override
    public List<Instant> listVersions(final String fedoraIdentifier) throws PersistentStorageException {
        final var mapping = getFedoraOCFLMapping(fedoraIdentifier);
        final var objSession = findOrCreateSession(mapping.getOcflObjectId());

        String subpath = null;

        // Find the subpath if it's a child of an AG
        if (!mapping.getRootObjectIdentifier().equals(fedoraIdentifier)) {

            final var headers = getHeaders(fedoraIdentifier, null);
            subpath = resolveExtensions(
                    resovleOCFLSubpathFromResourceId(mapping.getRootObjectIdentifier(), fedoraIdentifier),
                    !NON_RDF_SOURCE.getURI().equals(headers.getInteractionModel())
            );
        }

        return OCFLPersistentStorageUtils.listVersions(objSession, subpath);
    }

    @Override
    public InputStream getBinaryContent(final String identifier, final Instant version)
            throws PersistentStorageException {
        ensureCommitNotStarted();

        final var mapping = getFedoraOCFLMapping(identifier);
        final var rootIdentifier = mapping.getRootObjectIdentifier();
        final var objSession = findOrCreateSession(mapping.getOcflObjectId());
        final var ocflSubpath = resovleOCFLSubpathFromResourceId(rootIdentifier, identifier);

        return getBinaryStream(objSession, ocflSubpath, version);
    }

    @Override
    public synchronized void commit() throws PersistentStorageException {
        ensureCommitNotStarted();
        if (isReadOnly()) {
            // No changes to commit.
            return;
        }

        this.state = State.COMMIT_STARTED;
        LOGGER.debug("Starting storage session {} commit", sessionId);

        synchronized (this.phaser) {
            if (this.phaser.getRegisteredParties() > 0) {
                this.phaser.awaitAdvance(0);
            }
        }

        LOGGER.debug("All persisters are complete in session {}", sessionId);

        // order map for testing
        final var sessions = new TreeMap<>(sessionMap);

        prepareObjectSessions(sessions);
        commitObjectSessions(sessions);

        cleanupStagingDir();

        LOGGER.debug("Committed storage session {}", sessionId);
    }

    private void prepareObjectSessions(final Map<String, OCFLObjectSession> sessions)
            throws PersistentStorageException {
        LOGGER.debug("Preparing commit session {}", sessionId);

        for (final var entry : sessions.entrySet()) {
            try {
                entry.getValue().prepare();
            } catch (Exception e) {
                this.state = State.PREPARE_FAILED;
                throw new PersistentStorageException(
                        String.format("Storage session <%s> failed to prepare object <%s> for commit.",
                                sessionId, entry.getKey()), e);
            }
        }
    }

    private void commitObjectSessions(final Map<String, OCFLObjectSession> sessions)
            throws PersistentStorageException {
        LOGGER.debug("Committing session {}", sessionId);

        this.sessionsToRollback = new HashMap<>(sessionMap.size());

        for (final var entry : sessions.entrySet()) {
            final var id = entry.getKey();
            final var session = entry.getValue();
            try {
                session.commit();
                sessionsToRollback.put(id, session);
                session.close();
            } catch (Exception e) {
                this.state = State.COMMIT_FAILED;
                throw new PersistentStorageException(String.format("Failed to commit object <%s> in session <%s>",
                        id, sessionId), e);
            }
        }

        state = State.COMMITTED;
    }

    @Override
    public void rollback() throws PersistentStorageException {
        if (isReadOnly()) {
            // No changes to rollback
            return;
        }

        if (!(state.equals(State.COMMIT_FAILED) || state.equals(State.PREPARE_FAILED) ||
                state.equals(State.COMMIT_NOT_STARTED))) {
            throw new PersistentStorageException("This session cannot be rolled back in this state: " + state);
        }

        final boolean commitWasStarted = this.state != State.COMMIT_NOT_STARTED;

        this.state = State.ROLLING_BACK;
        LOGGER.info("Rolling back storage session {}", sessionId);

        if (!commitWasStarted) {
            //if the commit had not been started at the time this method was invoked
            //we must ensure that all persist operations are complete before we close any
            //ocfl object sessions. If the commit had been started then this synchronization step
            //will have already occurred and is thus unnecessary.
            synchronized (this.phaser) {
                if (this.phaser.getRegisteredParties() > 0) {
                    try {
                        this.phaser.awaitAdvanceInterruptibly(0, AWAIT_TIMEOUT, MILLISECONDS);
                    } catch (final InterruptedException | TimeoutException e) {
                        throw new PersistentStorageException(
                                "Waiting for operations to complete took too long, rollback failed");
                    }
                }
            }
        }

        closeUncommittedSessions();

        if (commitWasStarted) {
            rollbackCommittedSessions();
        }

        cleanupStagingDir();

        this.state = State.ROLLED_BACK;
        LOGGER.debug("Successfully rolled back storage session {}", sessionId);
    }

    private void closeUncommittedSessions() {
        this.sessionMap.entrySet().stream()
                .filter(entry -> !sessionsToRollback.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .forEach(OCFLObjectSession::close);
    }

    private void rollbackCommittedSessions() throws PersistentStorageException {
        final List<String> rollbackFailures = new ArrayList<>(this.sessionsToRollback.size());

        for (final var entry : this.sessionsToRollback.entrySet()) {
            final var id = entry.getKey();
            final var session = entry.getValue();

            if (session.getCommitOption() == NEW_VERSION) {
                //TODO rollback to previous OCFL version this is supported in ocfl-java 0.0.4-SNAPSHOT
                rollbackFailures.add(
                        String.format("Cannot rollback object <%s>." +
                                " Rollback to previous version not yet implemented.", id));
            } else {
                rollbackFailures.add(String.format("Cannot rollback object <%s>." +
                        " It was already committed to the mutable head", id));
            }

            session.close();
        }
        //throw an exception if any sessions could not be rolled back.
        if (rollbackFailures.size() > 0) {
            state = State.ROLLBACK_FAILED;
            final StringBuilder builder = new StringBuilder()
                    .append("Unable to rollback storage session ")
                    .append(sessionId)
                    .append(" completely due to the following errors: \n");

            for (final String failures : rollbackFailures) {
                builder.append("\t").append(failures).append("\n");
            }

            throw new PersistentStorageException(builder.toString());
        }
    }

    /**
     * Check if we are in a read-only session.
     *
     * @return whether we are read-only (ie. no transaction).
     */
    private boolean isReadOnly() {
        return this.sessionId == null;
    }

    /**
     * Utility to throw exception if trying to perform write operation on read-only session.
     */
    private void actionNeedsWrite() throws PersistentStorageException {
        if (isReadOnly()) {
            throw new PersistentStorageException("Session is read-only");
        }
    }

    private void cleanupStagingDir() {
        if (!FileUtils.deleteQuietly(sessionStagingDir.toFile())) {
            LOGGER.warn("Failed to cleanup session staging directory at {}", sessionStagingDir);
        }
    }

    @Override
    public String toString() {
        return "OCFLPersistentStorageSession{" +
                "sessionId='" + sessionId + '\'' +
                ", state=" + state +
                '}';
    }

}
