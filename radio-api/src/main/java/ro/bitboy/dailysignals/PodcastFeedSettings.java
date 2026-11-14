package ro.bitboy.dailysignals;

import java.util.Map;

/** Runtime publication switch for operators that do not want to expose a podcast feed. */
public record PodcastFeedSettings(boolean enabled) {
    public static final String ENVIRONMENT_KEY = "DAILY_SIGNALS_PODCAST_FEED_ENABLED";

    public static PodcastFeedSettings fromEnvironment(Map<String, String> environment) {
        String configured = environment.get(ENVIRONMENT_KEY);
        if (configured == null || configured.isBlank()) return new PodcastFeedSettings(false);
        if ("true".equalsIgnoreCase(configured)) return new PodcastFeedSettings(true);
        if ("false".equalsIgnoreCase(configured)) return new PodcastFeedSettings(false);
        throw new IllegalArgumentException(ENVIRONMENT_KEY + " must be true or false");
    }
}
