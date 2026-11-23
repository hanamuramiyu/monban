package hanamuramiyu.monban.sync;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RateLimitedLoggerTest {
    private final AtomicLong nanoClock = new AtomicLong();
    private final List<String> output = new ArrayList<>();
    private final RateLimitedLogger logger = new RateLimitedLogger(
            output::add,
            nanoClock::get,
            Duration.ofSeconds(60)
    );

    @Test
    void emitsFirstMessageImmediately() {
        logger.warn("first");

        assertEquals(List.of("first"), output);
    }

    @Test
    void suppressesMessagesInsideWindowAndReportsThemLater() {
        logger.warn("first");
        logger.warn("second");
        logger.warn("third");
        assertEquals(List.of("first"), output);

        nanoClock.addAndGet(Duration.ofSeconds(60).toNanos());
        logger.warn("fourth");

        assertEquals(List.of("first", "fourth (+2 similar warnings suppressed)"), output);
    }

    @Test
    void windowResetsAfterEachEmission() {
        logger.warn("first");
        nanoClock.addAndGet(Duration.ofSeconds(59).toNanos());
        logger.warn("suppressed");
        nanoClock.addAndGet(Duration.ofSeconds(1).toNanos());
        logger.warn("second");
        nanoClock.addAndGet(Duration.ofSeconds(59).toNanos());
        logger.warn("suppressed again");
        nanoClock.addAndGet(Duration.ofSeconds(1).toNanos());
        logger.warn("third");

        assertEquals(
                List.of("first", "second (+1 similar warnings suppressed)", "third (+1 similar warnings suppressed)"),
                output
        );
    }

    @Test
    void defaultIntervalSuppressesForSixtySeconds() {
        RateLimitedLogger defaultLogger = new RateLimitedLogger(output::add, nanoClock::get);

        defaultLogger.warn("first");
        nanoClock.addAndGet(Duration.ofSeconds(59).toNanos());
        defaultLogger.warn("suppressed");
        nanoClock.addAndGet(Duration.ofSeconds(1).toNanos());
        defaultLogger.warn("second");

        assertEquals(List.of("first", "second (+1 similar warnings suppressed)"), output);
    }

    @Test
    void validatesArguments() {
        assertThrows(NullPointerException.class, () -> new RateLimitedLogger(null, nanoClock::get));
        assertThrows(NullPointerException.class, () -> new RateLimitedLogger(output::add, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitedLogger(output::add, nanoClock::get, Duration.ZERO)
        );
    }

    @Test
    void rejectsNegativeInterval() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new RateLimitedLogger(output::add, nanoClock::get, Duration.ofSeconds(-1))
        );
    }

    @Test
    void rejectsNullMessage() {
        assertThrows(NullPointerException.class, () -> logger.warn(null));
    }
}
