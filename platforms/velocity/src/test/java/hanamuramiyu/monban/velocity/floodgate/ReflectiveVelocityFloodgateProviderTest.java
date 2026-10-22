package hanamuramiyu.monban.velocity.floodgate;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectiveVelocityFloodgateProviderTest {
    private static final UUID FLOODGATE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID JAVA_UUID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void resolvesFloodgateApiWithoutCompileTimeFloodgateTypes() {
        ReflectiveVelocityFloodgateProvider provider = new ReflectiveVelocityFloodgateProvider(
                new FakeApi()
        );

        FloodgatePlayer player = provider.find(FLOODGATE_UUID).orElseThrow();

        assertEquals(".Bedrock_Name", player.name());
        assertEquals("1234567890", player.xuid());
        assertTrue(provider.available());
        assertFalse(provider.failed());
    }

    @Test
    void nonFloodgateConnectionRemainsEmptyAndHealthy() {
        ReflectiveVelocityFloodgateProvider provider = new ReflectiveVelocityFloodgateProvider(
                new FakeApi()
        );

        assertTrue(provider.find(JAVA_UUID).isEmpty());
        assertTrue(provider.available());
        assertFalse(provider.failed());
    }

    @Test
    void malformedFloodgateResponseMarksProviderFailed() {
        ReflectiveVelocityFloodgateProvider provider = new ReflectiveVelocityFloodgateProvider(
                new MalformedApi()
        );

        assertTrue(provider.find(FLOODGATE_UUID).isEmpty());
        assertFalse(provider.available());
        assertTrue(provider.failed());
    }

    public static final class FakeApi {
        public boolean isFloodgatePlayer(UUID uuid) {
            return FLOODGATE_UUID.equals(uuid);
        }

        public FakePlayer getPlayer(UUID uuid) {
            return FLOODGATE_UUID.equals(uuid) ? new FakePlayer() : null;
        }
    }

    public static final class FakePlayer {
        public String getJavaUsername() {
            return ".Bedrock_Name";
        }

        public String getUsername() {
            return "Bedrock Name";
        }

        public String getXuid() {
            return "1234567890";
        }
    }

    public static final class MalformedApi {
        public boolean isFloodgatePlayer(UUID uuid) {
            return true;
        }

        public Object getPlayer(UUID uuid) {
            return new Object();
        }
    }
}
