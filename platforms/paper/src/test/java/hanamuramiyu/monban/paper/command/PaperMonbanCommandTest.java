package hanamuramiyu.monban.paper.command;

import com.mojang.brigadier.CommandDispatcher;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code /monban} root command on Paper: the help output must render the version injected by
 * the platform, and the whitelist subtree must not print the root help.
 */
class PaperMonbanCommandTest {
    private static final String VERSION = "9.9.9-qe";
    private static final String UNKNOWN_COMMAND = "Unknown command. Type \"/help\" for help.";

    @Test
    void rootHelpShowsInjectedVersionAndUnknownCommandFallback() throws Exception {
        RecordingSender sender = new RecordingSender();

        assertEquals(1, dispatcher().execute("monban", sender.source()));

        assertEquals("monban › " + VERSION, sender.messages.get(0));
        assertEquals(3, sender.messages.size());
        assertEquals(UNKNOWN_COMMAND, sender.messages.get(2));
    }

    @Test
    void rootHelpListsWhitelistEntryOnlyForPermittedSender() throws Exception {
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        assertEquals(1, dispatcher().execute("monban", sender.source()));

        assertEquals(3, sender.messages.size());
        assertEquals("/monban whitelist  Manage network whitelist", sender.messages.get(2));
        assertFalse(sender.messages.contains(UNKNOWN_COMMAND));
    }

    @Test
    void whitelistSubcommandIsDelegatedInsteadOfPrintingHelp() throws Exception {
        RecordingSender sender = new RecordingSender(PaperWhitelistCommand.PERMISSION);

        dispatcher().execute("monban whitelist list", sender.source());

        assertFalse(
                sender.messages.stream().anyMatch(message -> message.startsWith("monban › ")),
                "the root help must not be printed for a whitelist subcommand: " + sender.messages
        );
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher() {
        Logger logger = Logger.getLogger(PaperMonbanCommandTest.class.getName());
        logger.setUseParentHandlers(false);
        PaperWhitelistCommand whitelist = new PaperWhitelistCommand(
                new AccessGrantAdministrationService(new InMemoryAccessGrantRepository(), scope -> {
                }),
                Runnable::run,
                ignored -> Runnable::run,
                (sender, component) -> {
                },
                logger
        );
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(PaperMonbanCommand.create(VERSION, whitelist));
        return dispatcher;
    }

    private static final class RecordingSender {
        private final Set<String> permissions;
        private final List<String> messages = new ArrayList<>();

        private RecordingSender(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        private CommandSourceStack source() {
            CommandSender sender = sender();
            return (CommandSourceStack) Proxy.newProxyInstance(
                    CommandSourceStack.class.getClassLoader(),
                    new Class<?>[]{CommandSourceStack.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getSender" -> sender;
                        case "toString" -> "CommandSourceStackStub";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private CommandSender sender() {
            return (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permissions.contains((String) args[0]);
                        case "sendMessage" -> {
                            record(args);
                            yield null;
                        }
                        case "getName" -> "RecordingSender";
                        case "toString" -> "RecordingSender";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private void record(Object[] args) {
            if (args == null) {
                return;
            }
            for (Object argument : args) {
                if (argument instanceof Component component) {
                    messages.add(visibleText(component));
                    return;
                }
                if (argument instanceof String message) {
                    messages.add(message);
                    return;
                }
            }
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

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0F;
            }
            if (type == double.class) {
                return 0D;
            }
            if (type == char.class) {
                return '\0';
            }
            return null;
        }
    }
}