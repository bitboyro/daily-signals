package ro.bitboy.dailysignals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderRecoveryPolicyTest {
    private final ProviderRecoveryPolicy policy =
            new ProviderRecoveryPolicy(4, Duration.ofSeconds(2), Duration.ofSeconds(5));

    @Test void backsOffAndRecoversFromATransientOutage() {
        assertEquals(Duration.ofSeconds(2), policy.afterFailure(1).delay());
        assertEquals(Duration.ofSeconds(4), policy.afterFailure(2).delay());
        assertEquals(Duration.ofSeconds(5), policy.afterFailure(3).delay());
        assertTrue(policy.afterFailure(3).retry());
    }

    @Test void stopsAfterTheConfiguredAttemptBudget() {
        assertFalse(policy.afterFailure(4).retry());
        assertEquals(Duration.ZERO, policy.afterFailure(4).delay());
    }

    @Test void rejectsUnsafeRecoveryConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProviderRecoveryPolicy(0, Duration.ofSeconds(1), Duration.ofSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> policy.afterFailure(0));
    }
}
