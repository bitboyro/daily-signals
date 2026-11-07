package ro.bitboy.dailysignals;

/** Central role matrix for producer-only HTTP operations. Authentication remains a separate boundary. */
final class ProducerEndpointPolicy {
    enum Operation { REVIEW_STORY, READ_AUDIT, EDIT_SCRIPT }

    private ProducerEndpointPolicy() {}

    static boolean permits(Operation operation, String role) {
        return switch (operation) {
            case REVIEW_STORY, READ_AUDIT, EDIT_SCRIPT -> ProducerPermissions.mayReview(role);
        };
    }
}
