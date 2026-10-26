package ro.bitboy.dailysignals;

import java.util.List;

/** Authorization decision at the recording publication boundary. */
public final class ProducerPermissions {
    private ProducerPermissions() {}

    public static boolean mayPublish(String role) {
        return "PRODUCER".equals(role);
    }

    /** Story approval is editorial work; it never grants publication. */
    public static boolean mayReview(String role) {
        return "PRODUCER".equals(role) || "EDITOR".equals(role);
    }
    public static List<String> knownRoles() { return List.of("PRODUCER", "EDITOR", "LISTENER"); }
}
