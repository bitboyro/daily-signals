package ro.bitboy.dailysignals;

import java.time.Clock;
import java.util.ArrayList;

/** Offline edition workflow used for development and contract tests. */
final class EditionWorkflowStore implements EditionWorkflowBackend {
    private static final String ID = "producer-today";
    private final Clock clock;
    private final ArrayList<EditionApprovalAudit.Entry> approvals = new ArrayList<>();
    private String status = "DRAFT";
    private int revision;

    EditionWorkflowStore() { this(Clock.systemUTC()); }

    EditionWorkflowStore(Clock clock) { this.clock = clock; }

    @Override public synchronized String toJson() { return json(status, revision); }

    @Override public synchronized String auditJson(String editionId) {
        if (!ID.equals(editionId)) throw new IllegalArgumentException("unknown edition");
        return EditionApprovalAudit.json(editionId, approvals);
    }

    @Override public synchronized String transitionJson(String action, String actor) {
        String requested = action == null ? "" : action.strip();
        boolean approving = "APPROVE".equals(requested) && "DRAFT".equals(status);
        if (approving) {
            if (actor == null || actor.isBlank()) throw new IllegalArgumentException("approval actor is required");
            status = "READY";
        }
        else if ("PUBLISH".equals(requested) && "READY".equals(status)) status = "PUBLISHED";
        else throw new IllegalArgumentException("invalid edition transition");
        revision++;
        if (approving) approvals.add(new EditionApprovalAudit.Entry(revision, actor.strip(), clock.instant()));
        return toJson();
    }

    static String json(String status, int revision) {
        return "{\"schemaVersion\":1,\"editionId\":\"producer-today\",\"status\":\"" + status
                + "\",\"revision\":" + revision + "}";
    }
}
