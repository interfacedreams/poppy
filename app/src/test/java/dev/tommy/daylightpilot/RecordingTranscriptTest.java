package dev.tommy.daylightpilot;

/** Standalone regression checks: run with Java assertions enabled. */
public final class RecordingTranscriptTest {
    public static void main(String[] args) {
        RecordingTranscript t = new RecordingTranscript("");
        t.begin(); t.partial("what"); t.partial("what is this");
        t.finish("what is this flower");
        assert t.text().equals("what is this flower") : "Final must replace provisional revisions";
        t.begin(); t.partial("and where does it grow");
        t.finish(null);
        assert t.text().equals("what is this flower and where does it grow") : "Error/timeout must keep partial words";
        t.begin(); t.finish("");
        assert t.text().equals("what is this flower and where does it grow") : "Silence must keep the previous question";
        t.begin(); t.finish(null);
        assert t.text().equals("what is this flower and where does it grow") : "Missing words must preserve the captured question";
        t.begin(); t.partial("why"); t.partial(""); assert t.snapshot().endsWith(" why");
        t.finish(""); assert t.text().endsWith(" why") : "Empty finals must keep provisional words";
        t.finish(null); assert !t.text().endsWith("why why") : "Recovery cannot append a segment twice";
        RecordingTranscript restored = new RecordingTranscript(t.snapshot());
        assert restored.text().equals(t.text());
        t = new RecordingTranscript("");
        t.begin(); t.partial("x".repeat(5000)); t.finish(null);
        assert t.text().length() == 5000 : "Long questions must not be truncated";
        System.out.println("Recording transcript regression checks passed");
    }
}
