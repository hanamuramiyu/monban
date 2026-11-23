package hanamuramiyu.monban.presentation;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HelpPresentationTest {
    private static final String VERSION = "9.9.9-qe";

    private static final String WHITELIST_LINE = "/monban whitelist  Manage network whitelist";
    private static final String LOOKUP_LINE = "/monban lookup <player>  Inspect player access";
    private static final String ACCESS_LINE = "/monban access  Manage scoped access";
    private static final String STATUS_LINE = "/monban status  View access status";
    private static final String GROUP_LINE = "/monban group  Manage player groups and permissions";

    private final HelpPresentation presentation = new HelpPresentation(VERSION);

    @Test
    void titleRendersSuppliedVersion() {
        List<String> lines = visibleLines(presentation.lines(false, false, false, false));

        assertEquals("monban › " + VERSION, lines.get(0));
    }

    @Test
    void blankOrNullVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new HelpPresentation(""));
        assertThrows(IllegalArgumentException.class, () -> new HelpPresentation("   "));
        assertThrows(NullPointerException.class, () -> new HelpPresentation(null));
    }

    @Test
    void eachPermissionFlagControlsItsHelpLine() {
        assertHelpLines(false, false, false, false, false, List.of());
        assertHelpLines(true, false, false, false, false, List.of(WHITELIST_LINE));
        assertHelpLines(false, true, false, false, false, List.of(LOOKUP_LINE));
        assertHelpLines(false, false, true, false, false, List.of(ACCESS_LINE));
        assertHelpLines(false, false, false, true, false, List.of(STATUS_LINE));
        assertHelpLines(false, false, false, false, true, List.of(GROUP_LINE));
        assertHelpLines(
                true,
                true,
                true,
                true,
                true,
                List.of(WHITELIST_LINE, LOOKUP_LINE, ACCESS_LINE, STATUS_LINE, GROUP_LINE)
        );
    }

    @Test
    void noPermissionsFallsBackToUnknownCommandNotice() {
        List<String> lines = visibleLines(presentation.lines(false, false, false, false, false));

        assertFalse(lines.isEmpty());
        assertEquals(3, lines.size());
        assertEquals("Unknown command. Type \"/help\" for help.", lines.get(2));
    }

    @Test
    void helpLinesAreNotDuplicated() {
        List<String> lines = visibleLines(presentation.lines(true, true, true, true, true));

        assertEquals(lines.size(), new HashSet<>(lines).size());
    }

    @Test
    void fourArgumentOverloadOmitsGroupLine() {
        List<String> lines = visibleLines(presentation.lines(true, true, true, true));

        assertFalse(lines.contains(GROUP_LINE));
        assertEquals(visibleLines(presentation.lines(true, true, true, true, false)), lines);
    }

    private void assertHelpLines(
            boolean whitelist,
            boolean lookup,
            boolean access,
            boolean status,
            boolean group,
            List<String> expectedCommands
    ) {
        List<String> lines = visibleLines(presentation.lines(whitelist, lookup, access, status, group));

        int expectedSize = expectedCommands.isEmpty() ? 3 : 2 + expectedCommands.size();
        assertEquals(expectedSize, lines.size());
        if (!expectedCommands.isEmpty()) {
            assertEquals(expectedCommands, lines.subList(2, lines.size()));
        }
    }

    private static List<String> visibleLines(List<Component> components) {
        List<String> lines = new ArrayList<>();
        for (Component component : components) {
            lines.add(visibleText(component));
        }
        return lines;
    }

    private static String visibleText(Component component) {
        StringBuilder builder = new StringBuilder();
        appendVisibleText(component, builder);
        return builder.toString();
    }

    private static void appendVisibleText(Component component, StringBuilder builder) {
        if (component instanceof TextComponent text) {
            builder.append(text.content());
        }
        component.children().forEach(child -> appendVisibleText(child, builder));
    }
}