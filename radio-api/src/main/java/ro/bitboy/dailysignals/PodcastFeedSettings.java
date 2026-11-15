package ro.bitboy.dailysignals;

import java.util.Map;
import java.util.Locale;

/** Runtime publication switch for operators that do not want to expose a podcast feed. */
public record PodcastFeedSettings(boolean enabled, Locale locale) {
    public static final String ENVIRONMENT_KEY = "DAILY_SIGNALS_PODCAST_FEED_ENABLED";
    public static final String LOCALE_ENVIRONMENT_KEY = "DAILY_SIGNALS_PODCAST_FEED_LOCALE";

    public PodcastFeedSettings(boolean enabled) {
        this(enabled, Locale.UK);
    }

    public PodcastFeedSettings {
        if (locale == null || !(locale.getLanguage().equals("en") || locale.getLanguage().equals("ro"))) {
            throw new IllegalArgumentException("podcast feed locale must be en or ro");
        }
    }

    public static PodcastFeedSettings fromEnvironment(Map<String, String> environment) {
        String configured = environment.get(ENVIRONMENT_KEY);
        boolean enabled;
        if (configured == null || configured.isBlank()) enabled = false;
        else if ("true".equalsIgnoreCase(configured)) enabled = true;
        else if ("false".equalsIgnoreCase(configured)) enabled = false;
        else throw new IllegalArgumentException(ENVIRONMENT_KEY + " must be true or false");
        String locale = environment.getOrDefault(LOCALE_ENVIRONMENT_KEY, "en").trim().toLowerCase(Locale.ROOT);
        return switch (locale) {
            case "en", "en-gb" -> new PodcastFeedSettings(enabled, Locale.UK);
            case "ro", "ro-ro" -> new PodcastFeedSettings(enabled, Locale.forLanguageTag("ro-RO"));
            default -> throw new IllegalArgumentException(LOCALE_ENVIRONMENT_KEY + " must be en or ro");
        };
    }
}
