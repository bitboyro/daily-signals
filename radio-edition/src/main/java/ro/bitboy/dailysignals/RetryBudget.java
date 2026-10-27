package ro.bitboy.dailysignals;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Shared fixed-window attempt budget that prevents concurrent retry storms. */
public final class RetryBudget {
    private final int maximumAttempts;
    private final Duration window;
    private final Clock clock;
    private Instant windowStarted;
    private int used;

    public RetryBudget(int maximumAttempts, Duration window, Clock clock) {
        if (maximumAttempts < 1 || window == null || window.isNegative() || window.isZero() || clock == null)
            throw new IllegalArgumentException("positive retry budget required");
        this.maximumAttempts = maximumAttempts;
        this.window = window;
        this.clock = clock;
        this.windowStarted = clock.instant();
    }

    public synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (!now.isBefore(windowStarted.plus(window))) {
            windowStarted = now;
            used = 0;
        }
        if (used >= maximumAttempts) return false;
        used++;
        return true;
    }
}
