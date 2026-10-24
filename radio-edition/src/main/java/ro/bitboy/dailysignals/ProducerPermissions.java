package ro.bitboy.dailysignals;

/** Authorization decision at the recording publication boundary. */
public final class ProducerPermissions {
    private ProducerPermissions() {}

    public static boolean mayPublish(String role) {
        return "PRODUCER".equals(role);
    }
}
