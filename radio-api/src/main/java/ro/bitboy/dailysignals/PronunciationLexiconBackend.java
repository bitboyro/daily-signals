package ro.bitboy.dailysignals;

/** Storage-neutral producer pronunciation lexicon. */
interface PronunciationLexiconBackend {
    String toJson();
    String saveJson(String text);
}
