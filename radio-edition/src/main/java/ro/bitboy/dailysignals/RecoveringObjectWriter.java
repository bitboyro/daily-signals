package ro.bitboy.dailysignals;

import java.io.IOException;
import java.time.Duration;

/** Bounded retry wrapper for transient object-store transfer failures. */
public final class RecoveringObjectWriter implements PublishOutbox.ObjectWriter {
    @FunctionalInterface public interface Sleeper { void sleep(Duration delay) throws InterruptedException; }

    private final PublishOutbox.ObjectWriter delegate;
    private final int maximumAttempts;
    private final Duration retryDelay;
    private final Sleeper sleeper;
    private final RetryBudget budget;

    public RecoveringObjectWriter(PublishOutbox.ObjectWriter delegate, int maximumAttempts,
            Duration retryDelay, Sleeper sleeper) {
        this(delegate, maximumAttempts, retryDelay, sleeper, null);
    }

    public RecoveringObjectWriter(PublishOutbox.ObjectWriter delegate, int maximumAttempts,
            Duration retryDelay, Sleeper sleeper, RetryBudget budget) {
        if (delegate == null || maximumAttempts < 1 || retryDelay == null || retryDelay.isNegative()
                || sleeper == null) throw new IllegalArgumentException("valid retry configuration required");
        this.delegate = delegate;
        this.maximumAttempts = maximumAttempts;
        this.retryDelay = retryDelay;
        this.sleeper = sleeper;
        this.budget = budget;
    }

    @Override public void put(String key, byte[] bytes) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= maximumAttempts; attempt++) {
            if (budget != null && !budget.tryAcquire()) throw new IOException("retry budget exhausted");
            try {
                delegate.put(key, bytes);
                return;
            } catch (IOException transientFailure) {
                last = transientFailure;
                if (attempt < maximumAttempts) sleeper.sleep(retryDelay);
            }
        }
        throw last;
    }
}
