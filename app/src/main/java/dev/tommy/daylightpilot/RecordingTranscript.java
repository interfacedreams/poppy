package dev.tommy.daylightpilot;

/** Keeps provisional revisions separate so final results never duplicate words. */
final class RecordingTranscript {
    private String committed = "", partial = "";
    RecordingTranscript(String restored) { committed = clean(restored); }
    void begin() { partial = ""; }
    void partial(String value) {
        if (value != null && !value.isBlank()) { partial = value.trim(); }
    }
    void finish(String value) {
        String segment = value == null || value.isBlank() ? partial : value.trim();
        if (!segment.isBlank()) committed = clean(committed + " " + segment);
        partial = "";
    }
    String text() { return committed; }
    String snapshot() { return clean(committed + " " + partial); }
    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
