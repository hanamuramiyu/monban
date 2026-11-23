package hanamuramiyu.monban.bukkit.command;

import hanamuramiyu.monban.access.admin.AccessGrantAdministrationService;
import hanamuramiyu.monban.access.grant.memory.InMemoryAccessGrantRepository;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@code /monban} root command: the help output must render the version injected by the
 * platform and must not delegate to the whitelist command unless the subcommand is requested.
 */
class BukkitMonbanCommandTest {
    private static final String VERSION = "9.9.9-qe";
    private static final String UNKNOWN_COMMAND = "Unknown command. Type \"/help\" for help.";

    @Test
    void rootHelpShowsInjectedVersionAndUnknownCommandFallback() {
        RecordingSender sender = new RecordingSender();
        BukkitMonbanCommand command = new BukkitMonbanCommand(VERSION, whitelistCommand());

        assertTrue(command.onCommand(sender.sender(), null, "monban", new String[0]));

        assertEquals("monban › " + VERSION, sender.messages.get(0));
        assertEquals(3, sender.messages.size());
        assertEquals(UNKNOWN_COMMAND, sender.messages.get(2));
    }

    @Test
    void rootHelpListsWhitelistEntryOnlyForPermittedSender() {
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);
        BukkitMonbanCommand command = new BukkitMonbanCommand(VERSION, whitelistCommand());

        assertTrue(command.onCommand(sender.sender(), null, "monban", new String[0]));

        assertEquals(3, sender.messages.size());
        assertEquals("/monban whitelist  Manage network whitelist", sender.messages.get(2));
        assertFalse(sender.messages.contains(UNKNOWN_COMMAND));
    }

    @Test
    void backendManagedRootCommandPointsOperatorsToVelocity() {
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);
        BukkitMonbanCommand command = new BukkitMonbanCommand(VERSION, true);

        assertTrue(command.onCommand(sender.sender(), null, "monban", new String[0]));

        assertEquals(
                List.of("Whitelist administration is controlled by monban on Velocity."),
                sender.messages
        );
    }

    @Test
    void whitelistSubcommandIsDelegatedInsteadOfPrintingHelp() {
        RecordingSender sender = new RecordingSender(BukkitWhitelistCommand.PERMISSION);
        BukkitMonbanCommand command = new BukkitMonbanCommand(VERSION, whitelistCommand());

        assertTrue(command.onCommand(sender.sender(), null, "monban", new String[]{"whitelist", "list"}));

        assertFalse(
                sender.messages.stream().anyMatch(message -> message.startsWith("monban › ")),
                "the root help must not be printed for a whitelist subcommand: " + sender.messages
        );
    }

    private static BukkitWhitelistCommand whitelistCommand() {
        Logger logger = Logger.getLogger(BukkitMonbanCommandTest.class.getName());
        logger.setUseParentHandlers(false);
        return new BukkitWhitelistCommand(
                new AccessGrantAdministrationService(new InMemoryAccessGrantRepository(), scope -> {
                }),
                Runnable::run,
                ignored -> Runnable::run,
                (sender, component) -> {
                },
                logger
        );
    }

    private static final class RecordingSender {
        private final Set<String> permissions;
        private final List<String> messages = new ArrayList<>();

        private RecordingSender(String... permissions) {
            this.permissions = Set.of(permissions);
        }

        private CommandSender sender() {
            return (CommandSender) Proxy.newProxyInstance(
                    CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "hasPermission" -> permissions.contains((String) args[0]);
                        case "sendMessage" -> {
                            recordStringMessage(args);
                            yield null;
                        }
                        case "spigot" -> new CommandSender.Spigot() {
                            @Override
                            public void sendMessage(BaseComponent component) {
                                messages.add(component.toPlainText());
                            }

                            @Override
                            public void sendMessage(BaseComponent... components) {
                                messages.add(BaseComponent.toPlainText(components));
                            }

                            @Override
                            public void sendMessage(UUID sender, BaseComponent component) {
                                messages.add(component.toPlainText());
                            }

                            @Override
                            public void sendMessage(UUID sender, BaseComponent... components) {
                                messages.add(BaseComponent.toPlainText(components));
                            }
                        };
                        case "getName" -> "RecordingSender";
                        case "toString" -> "RecordingSender";
                        default -> defaultValue(method.getReturnType());
                    }
            );
        }

        private void recordStringMessage(Object[] args) {
            if (args == null) {
                return;
            }
            for (Object argument : args) {
                if (argument instanceof String message) {
                    messages.add(message);
                    return;
                }
                if (argument instanceof String[] batch && batch.length > 0) {
                    messages.addAll(List.of(batch));
                    return;
                }
            }
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