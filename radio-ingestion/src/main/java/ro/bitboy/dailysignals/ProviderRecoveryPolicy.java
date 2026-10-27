package ro.bitboy.dailysignals;

import java.time.Duration;

/** Bounded retry policy for transient source-provider outages. */
public record ProviderRecoveryPolicy(int maximumAttempts, Duration initialDelay, Duration maximumDelay) {
    public ProviderRecoveryPolicy {
        if (maximumAttempts < 1 || initialDelay == null || initialDelay.isNegative() || initialDelay.isZero()
                || maximumDelay == null || maximumDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("positive attempts and ordered retry delays required");
        }
    }

    public Decision afterFailure(int failedAttempts) {
        if (failedAttempts < 1) throw new IllegalArgumentException("failed attempts must be positive");
        if (failedAttempts >= maximumAttempts) return new Decision(false, Duration.ZERO);
        long multiplier = 1L << Math.min(failedAttempts - 1, 30);
        Duration delay;
        try {
            delay = initialDelay.multipliedBy(multiplier);
        } catch (ArithmeticException overflow) {
            delay = maximumDelay;
        }
        return new Decision(true, delay.compareTo(maximumDelay) > 0 ? maximumDelay : delay);
    }

    public record Decision(boolean retry, Duration delay) {}
}
