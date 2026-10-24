package ro.bitboy.dailysignals;

import java.util.Map;
import java.util.Optional;

/** Pre-UI listener URLs that stay reachable while older links, players and scripts are retired. */
public final class LegacyRoutes {
    private static final Map<String, String> PERMANENT = Map.of(
            "/editions/demo/audio", "/editions/demo-2026-01-15/audio");

    private LegacyRoutes() {}

    public static Optional<String> redirectFor(String path) {
        return Optional.ofNullable(PERMANENT.get(path));
    }

    public static Map<String, String> all() { return PERMANENT; }
}
