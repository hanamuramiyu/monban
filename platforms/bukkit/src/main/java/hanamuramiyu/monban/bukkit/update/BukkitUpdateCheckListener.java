package hanamuramiyu.monban.bukkit.update;

import hanamuramiyu.monban.update.JdkHttpFetcher;
import hanamuramiyu.monban.update.UpdateCheckService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public final class BukkitUpdateCheckListener implements Listener {
    private final JavaPlugin plugin;
    private final String currentVersion;
    private final Logger logger;

    public BukkitUpdateCheckListener(JavaPlugin plugin, String currentVersion, Logger logger) {
        this.plugin = plugin;
        this.currentVersion = currentVersion;
        this.logger = logger;
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        if (event.getType() != ServerLoadEvent.LoadType.STARTUP) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, this::check);
    }

    private void check() {
        try {
            UpdateCheckService service = new UpdateCheckService(
                    new JdkHttpFetcher(currentVersion),
                    currentVersion
            );
            service.check().ifPresentOrElse(
                    result -> logger.info(result.message()),
                    () -> logger.log(Level.FINE, "No newer monban version was found.")
            );
        } catch (RuntimeException exception) {
            logger.log(Level.FINE, "monban update check failed; continuing normally.", exception);
        }
    }
}
