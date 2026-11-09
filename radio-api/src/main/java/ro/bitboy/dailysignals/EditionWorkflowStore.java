package ro.bitboy.dailysignals;

/** Offline edition workflow used for development and contract tests. */
final class EditionWorkflowStore implements EditionWorkflowBackend {
    private String status = "DRAFT";
    private int revision;

    @Override public synchronized String toJson() { return json(status, revision); }

    @Override public synchronized String transitionJson(String action, String actor) {
        String requested = action == null ? "" : action.strip();
        if ("APPROVE".equals(requested) && "DRAFT".equals(status)) {
            if (actor == null || actor.isBlank()) throw new IllegalArgumentException("approval actor is required");
            status = "READY";
        }
        else if ("PUBLISH".equals(requested) && "READY".equals(status)) status = "PUBLISHED";
        else throw new IllegalArgumentException("invalid edition transition");
        revision++;
        return toJson();
    }

    static String json(String status, int revision) {
        return "{\"schemaVersion\":1,\"editionId\":\"producer-today\",\"status\":\"" + status
                + "\",\"revision\":" + revision + "}";
    }
}
