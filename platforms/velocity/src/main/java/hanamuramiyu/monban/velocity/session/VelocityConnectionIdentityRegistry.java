package hanamuramiyu.monban.velocity.session;

import com.velocitypowered.api.proxy.Player;
import hanamuramiyu.monban.identity.PlayerIdentity;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VelocityConnectionIdentityRegistry {
    private final Map<Player, PlayerIdentity> pending = new IdentityHashMap<>();
    private final Map<Player, PlayerIdentity> active = new IdentityHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();

    public void stage(Player player, PlayerIdentity identity) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(identity, "identity");

        writeLock.lock();
        try {
            active.remove(player);
            pending.put(player, identity);
        } finally {
            writeLock.unlock();
        }
    }

    public Optional<PlayerIdentity> activate(Player player) {
        Objects.requireNonNull(player, "player");

        writeLock.lock();
        try {
            PlayerIdentity identity = pending.remove(player);
            if (identity == null) {
                return Optional.empty();
            }

            active.put(player, identity);
            return Optional.of(identity);
        } finally {
            writeLock.unlock();
        }
    }

    public Optional<PlayerIdentity> findActive(Player player) {
        Objects.requireNonNull(player, "player");

        readLock.lock();
        try {
            return Optional.ofNullable(active.get(player));
        } finally {
            readLock.unlock();
        }
    }

    public void remove(Player player) {
        Objects.requireNonNull(player, "player");

        writeLock.lock();
        try {
            pending.remove(player);
            active.remove(player);
        } finally {
            writeLock.unlock();
        }
    }
}
