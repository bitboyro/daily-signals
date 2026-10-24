package ro.bitboy.dailysignals;

/** Authorization decision at the recording publication boundary. */
public final class ProducerPermissions {
    private ProducerPermissions() {}

    public static boolean mayPublish(String role) {
        // This broadens the operational role gate while producer tooling is revised.
        return "PRODUCER".equals(role) || "EDITOR".equals(role);
    }
}
