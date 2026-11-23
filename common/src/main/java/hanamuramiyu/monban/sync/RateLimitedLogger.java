package hanamuramiyu.monban.sync;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Rate-limited warning log: at most one message per interval. Suppressed messages
 * are aggregated and reported with the next emitted message.
 */
public final class RateLimitedLogger {
    private static final Duration DEFAULT_INTERVAL = Duration.ofSeconds(60);

    private final Consumer<String> sink;
    private final LongSupplier nanoClock;
    private final long intervalNanos;
    private boolean emitted;
    private long lastEmitNanos;
    private long suppressed;

    public RateLimitedLogger(Consumer<String> sink, LongSupplier nanoClock) {
        this(sink, nanoClock, DEFAULT_INTERVAL);
    }

    public RateLimitedLogger(Consumer<String> sink, LongSupplier nanoClock, Duration interval) {
        this.sink = Objects.requireNonNull(sink, "sink");
        this.nanoClock = Objects.requireNonNull(nanoClock, "nanoClock");
        Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Suppression interval must be positive.");
        }
        this.intervalNanos = interval.toNanos();
    }

    public synchronized void warn(String message) {
        Objects.requireNonNull(message, "message");
        long now = nanoClock.getAsLong();
        if (emitted && now - lastEmitNanos < intervalNanos) {
            suppressed++;
            return;
        }
        if (suppressed > 0) {
            sink.accept(message + " (+" + suppressed + " similar warnings suppressed)");
            suppressed = 0;
        } else {
            sink.accept(message);
        }
        emitted = true;
        lastEmitNanos = now;
    }
}
