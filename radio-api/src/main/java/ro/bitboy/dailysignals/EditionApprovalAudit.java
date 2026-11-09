package ro.bitboy.dailysignals;

import java.time.Instant;
import java.util.List;

/** Versioned JSON representation of the append-only producer approval trail. */
final class EditionApprovalAudit {
    record Entry(int editionRevision, String actor, Instant approvedAt) {
        Entry {
            if (editionRevision < 1 || actor == null || actor.isBlank() || approvedAt == null) {
                throw new IllegalArgumentException("positive revision, actor and approval time are required");
            }
        }
    }

    private EditionApprovalAudit() {}

    static String json(String editionId, List<Entry> approvals) {
        if (editionId == null || editionId.isBlank()) throw new IllegalArgumentException("edition is required");
        return "{\"schemaVersion\":1,\"editionId\":\"" + escape(editionId) + "\",\"approvals\":["
                + approvals.stream().map(EditionApprovalAudit::entryJson)
                .reduce((left, right) -> left + "," + right).orElse("") + "]}";
    }

    private static String entryJson(Entry entry) {
        return "{\"editionRevision\":" + entry.editionRevision() + ",\"actor\":\"" + escape(entry.actor())
                + "\",\"approvedAt\":\"" + entry.approvedAt() + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
