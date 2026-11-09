package ro.bitboy.dailysignals;

/** Storage-neutral producer lifecycle for the edition currently being prepared. */
interface EditionWorkflowBackend {
    String toJson();
    String transitionJson(String action, String actor);

    default String transitionJson(String action) {
        return transitionJson(action, "system");
    }
}
