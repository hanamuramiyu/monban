package hanamuramiyu.monban.update;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MonbanVersionTest {
    @Test
    void parsesPlainThreeComponentVersion() {
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("3.4.0"));
    }

    @Test
    void parsesVersionWithLeadingV() {
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("v3.4.0"));
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("V3.4.0"));
    }

    @Test
    void parsesVersionWithSurroundingWhitespace() {
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("  3.4.0  "));
    }

    @Test
    void ignoresSuffixAfterDash() {
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("3.4.0-beta.1"));
    }

    @Test
    void ignoresBuildMetadataAfterPlus() {
        assertEquals(Optional.of(new MonbanVersion(3, 4, 0)), MonbanVersion.parse("3.4.0+build.5"));
    }

    @Test
    void rejectsIncompleteVersion() {
        assertTrue(MonbanVersion.parse("3.4").isEmpty());
    }

    @Test
    void rejectsExtraComponents() {
        assertTrue(MonbanVersion.parse("3.4.0.1").isEmpty());
    }

    @Test
    void rejectsNonNumericVersion() {
        assertTrue(MonbanVersion.parse("latest").isEmpty());
    }

    @Test
    void rejectsEmptyVersion() {
        assertTrue(MonbanVersion.parse("").isEmpty());
    }

    @Test
    void rejectsNullVersion() {
        assertTrue(MonbanVersion.parse(null).isEmpty());
    }

    @Test
    void comparesByMajorThenMinorThenPatch() {
        MonbanVersion base = new MonbanVersion(3, 4, 0);

        assertTrue(base.compareTo(new MonbanVersion(2, 9, 9)) > 0);
        assertTrue(base.compareTo(new MonbanVersion(3, 3, 9)) > 0);
        assertTrue(base.compareTo(new MonbanVersion(3, 4, 0)) == 0);
        assertTrue(base.compareTo(new MonbanVersion(3, 4, 1)) < 0);
        assertTrue(base.compareTo(new MonbanVersion(4, 0, 0)) < 0);
    }

    @Test
    void toStringUsesNormalizedForm() {
        assertEquals("3.4.0", new MonbanVersion(3, 4, 0).toString());
    }
}
