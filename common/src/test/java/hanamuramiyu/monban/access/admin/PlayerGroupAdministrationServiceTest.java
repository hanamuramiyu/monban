package hanamuramiyu.monban.access.admin;

import hanamuramiyu.monban.access.group.PlayerGroupAddResult;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.group.PlayerGroupRemoveResult;
import hanamuramiyu.monban.access.group.PlayerGroupRepository;
import hanamuramiyu.monban.access.group.PlayerGroupUpdateResult;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupAssignmentRepository;
import hanamuramiyu.monban.access.group.memory.InMemoryPlayerGroupRepository;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PermissionGrantAddResult;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.permission.memory.InMemoryPlayerPermissionGrantRepository;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerGroupAdministrationServiceTest {
    @Test
    void managesGroupsAssignmentsAndDirectPermissions() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        InMemoryPlayerPermissionGrantRepository permissions = new InMemoryPlayerPermissionGrantRepository();
        PlayerGroupAdministrationService service = new PlayerGroupAdministrationService(
                groups,
                assignments,
                permissions,
                scope -> {
                }
        );
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        PermissionGrant directPermission = PermissionGrant.server("survival", "coreprotect.inspect");

        assertEquals(PlayerGroupAddResult.ADDED, service.createGroup("moderator"));
        assertEquals(PlayerGroupEntryResult.ADDED, service.grantAccess("moderator", AccessScope.network()));
        assertEquals(
                PlayerGroupEntryResult.ADDED,
                service.grantPermission("moderator", PermissionGrant.network("proxy.command"))
        );
        assertEquals(
                PlayerGroupEntryResult.ADDED,
                service.addAssignment(identity, "moderator")
        );
        assertEquals(
                PermissionGrantAddResult.ADDED,
                service.addDirectPermission(identity, directPermission)
        );

        PlayerGroupDefinition group = groups.find("moderator").orElseThrow();
        assertEquals(List.of(AccessScope.network()), group.accessGrants());
        assertEquals(List.of(PermissionGrant.network("proxy.command")), group.permissions());
        assertEquals(List.of(new PlayerGroupAssignment(identity, "moderator")), assignments.findAll());
        assertEquals(List.of(new PlayerPermissionGrant(identity, directPermission)), permissions.findAll());

        assertEquals(PlayerGroupRemoveResult.REMOVED, service.removeGroup("moderator"));
        assertTrue(groups.find("moderator").isEmpty());
        assertTrue(assignments.findAll().isEmpty());
    }

    @Test
    void removeGroupRemovesOnlyAssignmentsOfThatGroup() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerGroupAdministrationService service = service(groups, assignments);
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        groups.add(new PlayerGroupDefinition("moderator", List.of(), List.of()));
        groups.add(new PlayerGroupDefinition("builder", List.of(), List.of()));
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        assignments.add(new PlayerGroupAssignment(identity, "builder"));

        assertEquals(PlayerGroupRemoveResult.REMOVED, service.removeGroup("moderator"));

        assertTrue(groups.find("moderator").isEmpty());
        assertEquals(List.of(new PlayerGroupAssignment(identity, "builder")), assignments.findAll());
    }

    @Test
    void removeGroupLeavesGroupWhenAssignmentRemovalFails() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        groups.add(new PlayerGroupDefinition("moderator", List.of(), List.of()));
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        PlayerGroupAdministrationService service = new PlayerGroupAdministrationService(
                groups,
                new FailingRemoveAssignmentRepository(assignments),
                new InMemoryPlayerPermissionGrantRepository(),
                scope -> {
                }
        );

        assertThrows(IllegalStateException.class, () -> service.removeGroup("moderator"));

        assertTrue(groups.find("moderator").isPresent());
        assertEquals(List.of(new PlayerGroupAssignment(identity, "moderator")), assignments.findAll());
    }

    @Test
    void removeGroupIsIdempotent() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerGroupAdministrationService service = service(groups, assignments);
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        groups.add(new PlayerGroupDefinition("moderator", List.of(), List.of()));
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));

        assertEquals(PlayerGroupRemoveResult.REMOVED, service.removeGroup("moderator"));
        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, service.removeGroup("moderator"));

        assertTrue(groups.find("moderator").isEmpty());
        assertTrue(assignments.findAll().isEmpty());
    }

    @Test
    void removeGroupDoesNotScanAssignmentsWhenGroupIsMissing() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        PlayerGroupAdministrationService service = new PlayerGroupAdministrationService(
                groups,
                new FailingFindAssignmentRepository(),
                new InMemoryPlayerPermissionGrantRepository(),
                scope -> {
                }
        );

        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, service.removeGroup("missing"));
    }

    @Test
    void removeGroupReturnsNotFoundWhenGroupDisappearsAfterAssignmentsAreRemoved() {
        InMemoryPlayerGroupRepository groups = new InMemoryPlayerGroupRepository();
        InMemoryPlayerGroupAssignmentRepository assignments = new InMemoryPlayerGroupAssignmentRepository();
        PlayerIdentity identity = PlayerIdentity.offline("Miyu");
        groups.add(new PlayerGroupDefinition("moderator", List.of(), List.of()));
        assignments.add(new PlayerGroupAssignment(identity, "moderator"));
        PlayerGroupAdministrationService service = new PlayerGroupAdministrationService(
                new VanishingRemovePlayerGroupRepository(groups),
                assignments,
                new InMemoryPlayerPermissionGrantRepository(),
                scope -> {
                }
        );

        assertEquals(PlayerGroupRemoveResult.NOT_FOUND, service.removeGroup("moderator"));

        assertTrue(assignments.findAll().isEmpty());
    }

    private static PlayerGroupAdministrationService service(
            PlayerGroupRepository groups,
            PlayerGroupAssignmentRepository assignments
    ) {
        return new PlayerGroupAdministrationService(
                groups,
                assignments,
                new InMemoryPlayerPermissionGrantRepository(),
                scope -> {
                }
        );
    }

    private static final class FailingRemoveAssignmentRepository implements PlayerGroupAssignmentRepository {
        private final PlayerGroupAssignmentRepository delegate;

        private FailingRemoveAssignmentRepository(PlayerGroupAssignmentRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<PlayerGroupAssignment> findAll() {
            return delegate.findAll();
        }

        @Override
        public PlayerGroupAddResult add(PlayerGroupAssignment assignment) {
            return delegate.add(assignment);
        }

        @Override
        public PlayerGroupRemoveResult remove(PlayerGroupAssignment assignment) {
            throw new IllegalStateException("simulated assignment removal failure");
        }
    }

    private static final class FailingFindAssignmentRepository implements PlayerGroupAssignmentRepository {
        @Override
        public List<PlayerGroupAssignment> findAll() {
            throw new IllegalStateException("assignments must not be scanned when the group is missing");
        }

        @Override
        public PlayerGroupAddResult add(PlayerGroupAssignment assignment) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerGroupRemoveResult remove(PlayerGroupAssignment assignment) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class VanishingRemovePlayerGroupRepository implements PlayerGroupRepository {
        private final PlayerGroupRepository delegate;

        private VanishingRemovePlayerGroupRepository(PlayerGroupRepository delegate) {
            this.delegate = delegate;
        }

        @Override
        public Optional<PlayerGroupDefinition> find(String id) {
            return delegate.find(id);
        }

        @Override
        public List<PlayerGroupDefinition> findAll() {
            return delegate.findAll();
        }

        @Override
        public PlayerGroupAddResult add(PlayerGroupDefinition group) {
            return delegate.add(group);
        }

        @Override
        public PlayerGroupUpdateResult update(PlayerGroupDefinition group) {
            return delegate.update(group);
        }

        @Override
        public PlayerGroupRemoveResult remove(String id) {
            return PlayerGroupRemoveResult.NOT_FOUND;
        }
    }
}
