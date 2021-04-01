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
package org.fcrepo.integration.persistence.ocfl.impl;

import static org.fcrepo.kernel.api.RdfLexicon.BASIC_CONTAINER;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.fcrepo.config.ServerManagedPropsMode;
import org.fcrepo.kernel.api.identifiers.FedoraId;
import org.fcrepo.kernel.api.operations.DeleteResourceOperationFactory;
import org.fcrepo.kernel.api.operations.RdfSourceOperationFactory;
import org.fcrepo.persistence.api.PersistentStorageSession;
import org.fcrepo.persistence.ocfl.impl.OcflPersistentSessionManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import edu.wisc.library.ocfl.api.MutableOcflRepository;
import edu.wisc.library.ocfl.api.model.ObjectVersionId;

/**
 * Test delete resource persister for stamping versions of deleted resources in manually versioned repository.
 * @author whikloj
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("/spring-test/manual-versioning-config.xml")
public class DeleteResourcePersisterIT {

    @Autowired
    private OcflPersistentSessionManager sessionManager;

    @Autowired
    private RdfSourceOperationFactory rdfSourceOpFactory;

    @Autowired
    private DeleteResourceOperationFactory deleteResourceOpFactory;

    @Autowired
    private MutableOcflRepository ocflRepository;

    private FedoraId rescId;

    @Before
    public void setup() {
        rescId = FedoraId.create(UUID.randomUUID().toString());
    }

    @Test
    public void testDeleteAgResource() {
        final PersistentStorageSession storageSession1 = startWriteSession();
        // Create an AG resource.
        final var agOp = rdfSourceOpFactory
                .createBuilder(rescId, BASIC_CONTAINER.getURI(), ServerManagedPropsMode.STRICT)
                .archivalGroup(true)
                .parentId(FedoraId.getRepositoryRootId())
                .build();
        storageSession1.persist(agOp);
        storageSession1.prepare();
        storageSession1.commit();

        // Assert it exists
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert it has a mutable head.
        assertTrue(ocflRepository.hasStagedChanges(rescId.getResourceId()));

        // In a new session delete it
        final PersistentStorageSession storageSession2 = startWriteSession();
        final var deleteOp = deleteResourceOpFactory
                .deleteBuilder(rescId)
                .build();
        storageSession2.persist(deleteOp);
        storageSession2.prepare();
        storageSession2.commit();

        // Assert it still exists.
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert it is committed.
        assertFalse(ocflRepository.hasStagedChanges(rescId.getResourceId()));
    }

    @Test
    public void testDeleteResourceInAg() {
        final String childResourceId = UUID.randomUUID().toString();
        final FedoraId childId = rescId.resolve(childResourceId);
        final PersistentStorageSession storageSession1 = startWriteSession();
        // Create an AG resource.
        final var agOp = rdfSourceOpFactory
                .createBuilder(rescId, BASIC_CONTAINER.getURI(), ServerManagedPropsMode.STRICT)
                .archivalGroup(true)
                .parentId(FedoraId.getRepositoryRootId())
                .build();
        storageSession1.persist(agOp);
        storageSession1.prepare();
        storageSession1.commit();

        // Assert it exists
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert it has a mutable head.
        assertTrue(ocflRepository.hasStagedChanges(rescId.getResourceId()));

        final PersistentStorageSession storageSession2 = startWriteSession();

        // Create a resource in the AG
        final var agChild = rdfSourceOpFactory
                .createBuilder(childId, BASIC_CONTAINER.getURI(), ServerManagedPropsMode.STRICT)
                .parentId(rescId)
                .build();
        storageSession2.persist(agChild);
        storageSession2.prepare();
        storageSession2.commit();

        // Assert the child exists
        final var child = ocflRepository.getObject(ObjectVersionId.head(rescId.getResourceId()));
        assertTrue(child.containsFile(childResourceId + "/fcr-container.nt"));
        // Assert the resource still has a mutable head.
        assertTrue(ocflRepository.hasStagedChanges(rescId.getResourceId()));

        // In a new session delete the child resource.
        final PersistentStorageSession storageSession3 = startWriteSession();
        final var deleteOp = deleteResourceOpFactory
                .deleteBuilder(childId)
                .build();
        storageSession3.persist(deleteOp);
        storageSession3.prepare();
        storageSession3.commit();

        // Assert the AG resource still exists.
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert the child file is now gone.
        final var child2 = ocflRepository.getObject(ObjectVersionId.head(rescId.getResourceId()));
        assertFalse(child2.containsFile(childResourceId + "/fcr-container.nt"));
        // Assert the AG resource still has a mutable head.
        assertTrue(ocflRepository.hasStagedChanges(rescId.getResourceId()));
    }

    @Test
    public void testDeleteAtomicResource() {
        final PersistentStorageSession storageSession1 = startWriteSession();
        // Create an atomic resource.
        final var op = rdfSourceOpFactory
                .createBuilder(rescId, BASIC_CONTAINER.getURI(), ServerManagedPropsMode.RELAXED)
                .parentId(FedoraId.getRepositoryRootId())
                .build();
        storageSession1.persist(op);
        storageSession1.prepare();
        storageSession1.commit();

        // Assert it exists.
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert it has a mutable head.
        assertTrue(ocflRepository.hasStagedChanges(rescId.getResourceId()));

        // In a new session delete it
        final PersistentStorageSession storageSession2 = startWriteSession();
        final var deleteOp = deleteResourceOpFactory
                .deleteBuilder(rescId)
                .build();
        storageSession2.persist(deleteOp);
        storageSession2.prepare();
        storageSession2.commit();

        // Assert it still exist.
        assertTrue(ocflRepository.containsObject(rescId.getResourceId()));
        // Assert it is committed.
        assertFalse(ocflRepository.hasStagedChanges(rescId.getResourceId()));
    }

    private PersistentStorageSession startWriteSession() {
        return sessionManager.getSession(UUID.randomUUID().toString());
    }
}
