package hanamuramiyu.monban.sync;

import hanamuramiyu.monban.access.grant.AccessGrant;
import hanamuramiyu.monban.access.group.PlayerGroupAssignment;
import hanamuramiyu.monban.access.group.PlayerGroupDefinition;
import hanamuramiyu.monban.access.permission.PermissionGrant;
import hanamuramiyu.monban.access.permission.PlayerPermissionGrant;
import hanamuramiyu.monban.access.scope.AccessScope;
import hanamuramiyu.monban.identity.PlayerIdentity;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerAccessStateCodecTest {
    private static final PlayerIdentity ONLINE = PlayerIdentity.online(
            "Miyu",
            UUID.fromString("00000000-0000-0000-0000-000000000001")
    );
    private static final UUID EPOCH = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final SyncSecret SECRET = SyncSecret.fromBase64(
            Base64.getEncoder().encodeToString("monban-sync-secret".getBytes(StandardCharsets.UTF_8))
    );

    /**
     * Golden payload of protocol version 3 (fixed snapshot: revision 1, empty state,
     * network whitelist enabled) signed with {@link #SECRET}. Rejected by version 4
     * decoders; guards the mutual incompatibility of mixed installations.
     */
    private static final String VERSION_THREE_GOLDEN_HEX =
            "4d4253540000000300000000000000010100000000000000000000000000000000"
                    + "3135210ae8cf1db63c09a361269a391ca208b7737aab1646efb8b21a499bf49b";

    /**
     * Golden payload of protocol version 4 for {@link #goldenSnapshot()} signed with
     * {@link #SECRET}. Guards the wire format against drift (including enum codes).
     */
    private static final String GOLDEN_HEX =
            "4d42535400000004550e8400e29b41d4a716446655440000000000000000002a0100000001000000044d697975010000"
                    + "0000000000000000000000000001000000000100096d6f64657261746f72000000010100067075626c69630000000102"
                    + "0008737572766976616c0013636f726570726f746563742e696e737065637400000001020007426564726f636b000100"
                    + "0c30303132333435363738393000096d6f64657261746f72000000010100076f66666c696e65000000000d70726f7879"
                    + "2e636f6d6d616e64672246de8d0af15b6cbf71a3410640ac3296b7703247c97bf4f2ec1af02fd1db";

    @Test
    void roundTripsAllStateTypes() {
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                EPOCH,
                7,
                true,
                List.of(
                        new AccessGrant(AccessScope.network(), ONLINE),
                        new AccessGrant(AccessScope.server("survival"), PlayerIdentity.offline("offline")),
                        new AccessGrant(AccessScope.server("bedrock"), PlayerIdentity.floodgate("Bedrock")),
                        new AccessGrant(AccessScope.server("bedrock-xuid"), PlayerIdentity.floodgate("BedrockWithXuid", "001234567890"))
                ),
                List.of(new PlayerGroupDefinition(
                        "moderator",
                        List.of(AccessScope.serverGroup("public")),
                        List.of(PermissionGrant.server("survival", "coreprotect.inspect"))
                )),
                List.of(new PlayerGroupAssignment(ONLINE, "moderator")),
                List.of(new PlayerPermissionGrant(ONLINE, PermissionGrant.network("proxy.command")))
        );

        PlayerAccessStateSnapshot decoded = new PlayerAccessStateCodec().decode(
                new PlayerAccessStateCodec().encode(snapshot, SECRET),
                SECRET
        );

        assertEquals(snapshot, decoded);
        assertEquals(EPOCH, decoded.epoch());
    }

    @Test
    void encodesGoldenBytesForVersionFour() {
        byte[] payload = new PlayerAccessStateCodec().encode(goldenSnapshot(), SECRET);

        assertEquals(GOLDEN_HEX, HexFormat.of().formatHex(payload));
        assertEquals(goldenSnapshot(), new PlayerAccessStateCodec().decode(payload, SECRET));
    }

    @Test
    void rejectsVersionThreeGoldenBytes() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(HexFormat.of().parseHex(VERSION_THREE_GOLDEN_HEX), SECRET)
        );

        assertEquals("Unsupported state snapshot protocol.", exception.getMessage());
    }

    @Test
    void rejectsCorruptedEpochBytes() {
        byte[] payload = new PlayerAccessStateCodec().encode(goldenSnapshot(), SECRET);

        for (int index = 8; index < 24; index++) {
            byte[] corrupted = payload.clone();
            corrupted[index] ^= 1;
            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerAccessStateCodec().decode(corrupted, SECRET)
            );
            assertEquals("Invalid state snapshot signature.", exception.getMessage());
        }
    }

    @Test
    void rejectsWrongSecretAndTampering() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                1,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        byte[] payload = codec.encode(snapshot, SECRET);
        payload[0] ^= 1;

        assertThrows(IllegalArgumentException.class, () -> codec.decode(payload, SECRET));
        assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode(codec.encode(snapshot, SECRET), SyncSecret.fromBase64(
                        Base64.getEncoder().encodeToString("another-sync-secret".getBytes(StandardCharsets.UTF_8))
                ))
        );
    }

    @Test
    void rejectsTruncatedPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(new byte[32], SECRET)
        );
    }

    @Test
    void rejectsOversizedPayloadOnDecode() {
        byte[] oversized = new byte[1024 * 1024 + 1];

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(oversized, SECRET)
        );
        assertEquals("Invalid state snapshot payload size.", exception.getMessage());
    }

    @Test
    void rejectsOversizedSnapshotOnEncode() {
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                1,
                List.of(),
                List.of(),
                Collections.nCopies(46_000, new PlayerGroupAssignment(ONLINE, "moderator")),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().encode(snapshot, SECRET)
        );
        assertEquals("State snapshot exceeds the maximum payload size.", exception.getMessage());
    }

    @Test
    void rejectsTooManyEntriesOnEncode() {
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                1,
                Collections.nCopies(100_001, new AccessGrant(AccessScope.network(), ONLINE)),
                List.of(),
                List.of(),
                List.of()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().encode(snapshot, SECRET)
        );
        assertEquals("Snapshot entry count exceeds the limit.", exception.getMessage());
    }

    @Test
    void rejectsTrailingData() {
        byte[] payload = new PlayerAccessStateCodec().encode(goldenSnapshot(), SECRET);
        byte[] content = java.util.Arrays.copyOf(payload, payload.length - 32 + 1);
        byte[] withTrailingData = new byte[content.length + 32];
        System.arraycopy(content, 0, withTrailingData, 0, content.length);
        System.arraycopy(sign(content), 0, withTrailingData, content.length, 32);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(withTrailingData, SECRET)
        );
        assertEquals("Trailing data in state snapshot.", exception.getMessage());
    }

    @Test
    void acceptsExactlyMaximumEntryCount() {
        List<AccessGrant> grants = Collections.nCopies(
                100_000,
                new AccessGrant(AccessScope.network(), PlayerIdentity.offline("a"))
        );
        PlayerAccessStateSnapshot snapshot = new PlayerAccessStateSnapshot(
                EPOCH,
                1,
                true,
                grants,
                List.of(),
                List.of(),
                List.of()
        );

        byte[] payload = new PlayerAccessStateCodec().encode(snapshot, SECRET);
        PlayerAccessStateSnapshot decoded = new PlayerAccessStateCodec().decode(payload, SECRET);

        assertTrue(payload.length < 1024 * 1024);
        assertEquals(100_000, decoded.accessGrants().size());
        assertEquals(grants, decoded.accessGrants());
    }

    @Test
    void rejectsEntryCountAboveMaximumOnDecode() {
        for (int count : new int[]{100_001, -1}) {
            byte[] payload = signedContent(countedHeader(count));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerAccessStateCodec().decode(payload, SECRET)
            );

            assertEquals("Invalid snapshot entry count.", exception.getMessage());
        }
    }

    @Test
    void rejectsUnknownAccessScopeTypeOnDecode() {
        byte[] payload = signedContent(concatenateBytes(countedHeader(1), 99));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(payload, SECRET)
        );

        assertEquals("Invalid access scope type.", exception.getMessage());
    }

    @Test
    void rejectsUnknownIdentityTypeOnDecode() {
        byte[] payload = signedContent(concatenateBytes(countedHeader(1), 0, 99));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(payload, SECRET)
        );

        assertEquals("Invalid identity type.", exception.getMessage());
    }

    @Test
    void rejectsTruncatedContentWithValidSignature() {
        for (byte[] content : List.of(new byte[0], stateHeader())) {
            byte[] payload = signedContent(content);

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> new PlayerAccessStateCodec().decode(payload, SECRET)
            );

            assertEquals("Truncated state snapshot.", exception.getMessage());
        }
    }

    @Test
    void rejectsUnsupportedProtocolVersionWithValidSignature() {
        byte[] payload = signedContent(protocolContent(5));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerAccessStateCodec().decode(payload, SECRET)
        );

        assertEquals("Unsupported state snapshot protocol.", exception.getMessage());
    }

    @Test
    void fingerprintIsIndependentOfEpochAndRevision() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
        PlayerAccessStateSnapshot base = goldenSnapshot();
        PlayerAccessStateSnapshot otherMetadata = new PlayerAccessStateSnapshot(
                UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"),
                999,
                base.networkWhitelistEnabled(),
                base.accessGrants(),
                base.groups(),
                base.assignments(),
                base.permissions()
        );

        assertEquals(32, codec.fingerprint(base).length);
        assertArrayEquals(codec.fingerprint(base), codec.fingerprint(otherMetadata));
    }

    @Test
    void fingerprintIsOrderStableForUnchangedState() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();

        assertArrayEquals(codec.fingerprint(goldenSnapshot()), codec.fingerprint(goldenSnapshot()));
    }

    @Test
    void fingerprintChangesForEveryField() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
        byte[] base = codec.fingerprint(emptySnapshot(true));

        List<PlayerAccessStateSnapshot> variants = List.of(
                emptySnapshot(false),
                snapshot(List.of(new AccessGrant(AccessScope.network(), ONLINE)), List.of(), List.of(), List.of()),
                snapshot(List.of(), List.of(new PlayerGroupDefinition(
                        "moderator",
                        List.of(AccessScope.serverGroup("public")),
                        List.of()
                )), List.of(), List.of()),
                snapshot(List.of(), List.of(), List.of(new PlayerGroupAssignment(ONLINE, "moderator")), List.of()),
                snapshot(List.of(), List.of(), List.of(), List.of(
                        new PlayerPermissionGrant(ONLINE, PermissionGrant.network("proxy.command"))))
        );

        for (PlayerAccessStateSnapshot variant : variants) {
            assertFalse(
                    java.util.Arrays.equals(base, codec.fingerprint(variant)),
                    "fingerprint must change for: " + variant
            );
        }
    }

    @Test
    void fingerprintDistinguishesIdentityFieldsIgnoredByDomainEquality() {
        PlayerAccessStateCodec codec = new PlayerAccessStateCodec();
        UUID uuidOne = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID uuidTwo = UUID.fromString("00000000-0000-0000-0000-000000000002");

        PlayerIdentity offlineOne = PlayerIdentity.offline("offline", uuidOne);
        PlayerIdentity offlineTwo = PlayerIdentity.offline("offline", uuidTwo);
        assertEquals(offlineOne, offlineTwo, "domain equality ignores OFFLINE technicalUuid");
        assertFalse(java.util.Arrays.equals(
                codec.fingerprint(snapshot(List.of(new AccessGrant(AccessScope.network(), offlineOne)),
                        List.of(), List.of(), List.of())),
                codec.fingerprint(snapshot(List.of(new AccessGrant(AccessScope.network(), offlineTwo)),
                        List.of(), List.of(), List.of()))
        ), "fingerprint must include OFFLINE technicalUuid");

        PlayerIdentity floodgateOne = PlayerIdentity.floodgate("Bedrock", "001234567890");
        PlayerIdentity floodgateTwo = PlayerIdentity.floodgate("Bedrock", "009876543210");
        assertEquals(floodgateOne, floodgateTwo, "domain equality ignores FLOODGATE xuid");
        assertFalse(java.util.Arrays.equals(
                codec.fingerprint(snapshot(List.of(), List.of(), List.of(), List.of(
                        new PlayerPermissionGrant(floodgateOne, PermissionGrant.network("proxy.command"))))),
                codec.fingerprint(snapshot(List.of(), List.of(), List.of(), List.of(
                        new PlayerPermissionGrant(floodgateTwo, PermissionGrant.network("proxy.command")))))
        ), "fingerprint must include FLOODGATE xuid");
    }

    private static PlayerAccessStateSnapshot emptySnapshot(boolean whitelistEnabled) {
        return new PlayerAccessStateSnapshot(
                EPOCH, 1, whitelistEnabled, List.of(), List.of(), List.of(), List.of());
    }

    private static PlayerAccessStateSnapshot snapshot(
            List<AccessGrant> grants,
            List<PlayerGroupDefinition> groups,
            List<PlayerGroupAssignment> assignments,
            List<PlayerPermissionGrant> permissions
    ) {
        return new PlayerAccessStateSnapshot(EPOCH, 1, true, grants, groups, assignments, permissions);
    }

    private static byte[] protocolContent(int version) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeInt(0x4D425354);
            output.writeInt(version);
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] stateHeader() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(protocolContent(4));
            output.writeLong(EPOCH.getMostSignificantBits());
            output.writeLong(EPOCH.getLeastSignificantBits());
            output.writeLong(42);
            output.writeBoolean(true);
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] countedHeader(int count) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.write(stateHeader());
            output.writeInt(count);
            return bytes.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static byte[] concatenateBytes(byte[] prefix, int... suffix) {
        byte[] result = java.util.Arrays.copyOf(prefix, prefix.length + suffix.length);
        for (int index = 0; index < suffix.length; index++) {
            result[prefix.length + index] = (byte) suffix[index];
        }
        return result;
    }

    private static byte[] signedContent(byte[] content) {
        byte[] payload = java.util.Arrays.copyOf(content, content.length + 32);
        System.arraycopy(sign(content), 0, payload, content.length, 32);
        return payload;
    }

    private static PlayerAccessStateSnapshot goldenSnapshot() {
        return new PlayerAccessStateSnapshot(
                EPOCH,
                42,
                true,
                List.of(new AccessGrant(AccessScope.network(), ONLINE)),
                List.of(new PlayerGroupDefinition(
                        "moderator",
                        List.of(AccessScope.serverGroup("public")),
                        List.of(PermissionGrant.server("survival", "coreprotect.inspect"))
                )),
                List.of(new PlayerGroupAssignment(PlayerIdentity.floodgate("Bedrock", "001234567890"), "moderator")),
                List.of(new PlayerPermissionGrant(PlayerIdentity.offline("offline"), PermissionGrant.network("proxy.command")))
        );
    }

    private static byte[] sign(byte[] content) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.bytes(), "HmacSHA256"));
            return mac.doFinal(content);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
