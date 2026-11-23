package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAccessStateSnapshotTest {
    private static final AccessGrant GRANT = new AccessGrant(
            AccessScope.network(),
            PlayerIdentity.online("Miyu", UUID.fromString("00000000-0000-0000-0000-000000000001"))
    );
    private static final PlayerGroupDefinition GROUP = new PlayerGroupDefinition("moderator", List.of(), List.of());
    private static final PlayerGroupAssignment ASSIGNMENT = new PlayerGroupAssignment(GRANT.identity(), GROUP.id());
    private static final PlayerPermissionGrant PERMISSION = new PlayerPermissionGrant(
            GRANT.identity(),
            PermissionGrant.network("proxy.command")
    );

    @Test
    void convenienceConstructorsKeepPreviousSignatures() {
        PlayerAccessStateSnapshot withFlag = new PlayerAccessStateSnapshot(3, true, List.of(), List.of(), List.of(), List.of());
        PlayerAccessStateSnapshot withoutFlag = new PlayerAccessStateSnapshot(3, List.of(), List.of(), List.of(), List.of());

        assertEquals(PlayerAccessStateSnapshot.UNSPECIFIED_EPOCH, withFlag.epoch());
        assertEquals(PlayerAccessStateSnapshot.UNSPECIFIED_EPOCH, withoutFlag.epoch());
        assertEquals(3, withFlag.revision());
        assertTrue(withoutFlag.networkWhitelistEnabled());
    }

    @Test
    void convenienceConstructorCopiesLists() {
        List<AccessGrant> grants = new ArrayList<>(List.of(GRANT));

        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(1, grants, List.of(), List.of(), List.of());
        grants.clear();

        assertEquals(List.of(GRANT), snapshot.accessGrants());
    }

    @Test
    void canonicalConstructorRejectsMissingEpoch() {
        assertThrows(
                NullPointerException.class,
                () -> new PlayerAccessStateSnapshot(null, 1, true, List.of(), List.of(), List.of(), List.of())
        );
    }

    @Test
    void canonicalConstructorKeepsEpoch() {
        UUID epoch = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                epoch,
                1,
                true,
                List.of(GRANT),
                List.of(GROUP),
                List.of(ASSIGNMENT),
                List.of(PERMISSION)
        );

        assertEquals(epoch, snapshot.epoch());
    }

    @Test
    void rejectsNonPositiveRevision() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateSnapshot(0, List.of(), List.of(), List.of(), List.of())
        );
    }
}
