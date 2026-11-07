package ro.bitboy.dailysignals;

/** Storage-neutral review queue used by the HTTP composition root. */
interface ReviewQueueBackend {
    boolean contains(String storyId);
    String decideJson(String storyId, String decision, String reviewer);
    String toJson();
    String auditToJson();
}
