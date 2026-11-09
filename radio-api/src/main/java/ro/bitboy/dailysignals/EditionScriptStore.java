package ro.bitboy.dailysignals;

/** Current producer draft; persistence is introduced separately from this API contract. */
final class EditionScriptStore {
    private String text;
    private int revision;

    EditionScriptStore(String initialText) {
        text = validate(initialText);
    }

    synchronized String toJson() {
        return "{\"schemaVersion\":1,\"text\":" + quote(text) + ",\"revision\":" + revision + "}";
    }

    synchronized String saveJson(String updated) {
        text = validate(updated);
        revision++;
        return toJson();
    }

    synchronized ScriptAssembler.Script synthesisScript() {
        return new ScriptAssembler.Script(text, java.util.List.of("producer-workspace-draft"));
    }

    private static String validate(String value) {
        if (value == null || value.isBlank() || value.length() > 20_000
                || value.chars().anyMatch(c -> Character.isISOControl(c) && c != '\n' && c != '\r' && c != '\t')) {
            throw new IllegalArgumentException("script must contain 1 to 20000 printable characters");
        }
        return value.strip();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
}
