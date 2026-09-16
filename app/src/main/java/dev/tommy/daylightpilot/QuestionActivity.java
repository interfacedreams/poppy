package dev.tommy.daylightpilot;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.lang.ref.WeakReference;

public class QuestionActivity extends Activity implements RecognitionListener {
    private static WeakReference<QuestionActivity> active = new WeakReference<>(null);
    public static void closeForSession(int session) {
        QuestionActivity activity = active.get();
        if (activity != null && activity.session == session && !activity.isFinishing()) activity.finish();
    }
    private SpeechRecognizer recognizer;
    private TextView status;
    private RecordingTranscript transcript = new RecordingTranscript("");
    private boolean speechReady, speechEnded;
    private int recoveryAttempts;
    private WaveBars bars;
    private LinearLayout recordingBody;
    private boolean showingSettings;
    private PoppyLoadingView loading;
    private boolean sendRequested, foreground;
    private Button ask;
    private android.animation.ValueAnimator panelResize;
    private android.widget.CheckBox includeScreenshot;
    private boolean listening, recordingFailed, sessionExpired;
    private boolean screenshotAvailable;
    private boolean submitted;
    private boolean permissionPending;
    private int session, generation;
    private boolean segmentHeard;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        active = new WeakReference<>(this);
        setFinishOnTouchOutside(false);
        session = getIntent().getIntExtra("session", -1);
        screenshotAvailable = getIntent().getBooleanExtra("screenshot_available", true);
        LinearLayout body = new LinearLayout(this); recordingBody = body; body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(20));
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        android.widget.ImageButton settings = new android.widget.ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings); settings.setContentDescription("Settings");
        settings.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        settings.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable settingsMask = new android.graphics.drawable.GradientDrawable();
        settingsMask.setColor(android.graphics.Color.WHITE); settingsMask.setCornerRadius(dp(24));
        settings.setBackgroundTintList(null);
        settings.setBackground(new android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x2233332a), null, settingsMask));
        settings.setStateListAnimator(null);
        settings.setOnClickListener(v -> showSettings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(48), dp(48)));
        status = new TextView(this); status.setText("Poppy"); status.setTextSize(23);
        status.setTextColor(0xff25251f); status.setGravity(Gravity.CENTER); status.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.leftMargin = dp(12); titleParams.rightMargin = dp(12); header.addView(status, titleParams);
        android.widget.ImageButton close = new android.widget.ImageButton(this);
        close.setImageResource(R.drawable.ic_close); close.setContentDescription("Close Poppy");
        close.setPadding(dp(14), dp(14), dp(14), dp(14));
        close.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        close.setBackground(roundedControl(24));
        close.setOnClickListener(v -> finish()); header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48))); body.addView(header);
        if (saved != null) transcript = new RecordingTranscript(saved.getString("question", ""));
        bars = new WaveBars(); bars.setContentDescription("Microphone level.");
        LinearLayout.LayoutParams barsParams = new LinearLayout.LayoutParams(-1, dp(150));
        barsParams.topMargin = dp(12); barsParams.bottomMargin = dp(20); android.widget.FrameLayout recordingArea = new android.widget.FrameLayout(this);
        recordingArea.addView(bars, new android.widget.FrameLayout.LayoutParams(-1, -1));
        loading = new PoppyLoadingView(this); loading.setVisibility(android.view.View.GONE);
        recordingArea.addView(loading, new android.widget.FrameLayout.LayoutParams(-1, -1));
        body.addView(recordingArea, barsParams);
        android.content.SharedPreferences preferences = getSharedPreferences("question_preferences", MODE_PRIVATE);
        includeScreenshot = new android.widget.CheckBox(this);
        includeScreenshot.setText("Include screenshot"); includeScreenshot.setTextSize(16);
        includeScreenshot.setTextColor(0xff25251f);
        includeScreenshot.setButtonTintList(android.content.res.ColorStateList.valueOf(0xff33332a));
        includeScreenshot.setChecked(screenshotAvailable && preferences.getBoolean("include_screenshot", true));
        includeScreenshot.setEnabled(screenshotAvailable);
        if (!screenshotAvailable) includeScreenshot.setText("Screenshot unavailable — voice only");
        includeScreenshot.setOnCheckedChangeListener((button, checked) ->
            preferences.edit().putBoolean("include_screenshot", checked).apply());
        LinearLayout.LayoutParams screenshotParams = new LinearLayout.LayoutParams(-2, dp(48));
        screenshotParams.gravity = Gravity.CENTER_HORIZONTAL; screenshotParams.bottomMargin = dp(8);
        body.addView(includeScreenshot, screenshotParams);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        body.addView(actions, new LinearLayout.LayoutParams(-1, dp(68)));
        ask = addButton(actions, "Ask Poppy");
        ask.setLayoutParams(new LinearLayout.LayoutParams(0, dp(68), 1)); ask.setTextSize(22); ask.getLayoutParams().height = dp(68); ask.setOnClickListener(v -> {
            if (sessionExpired) {
                PilotService service = PilotService.current();
                finish();
                if (service != null) service.capture();
                else startActivity(new Intent(this, MainActivity.class));
            } else if (recordingFailed) { recoveryAttempts = 0; startSpeech(); }
            else requestSend();
        });
        ask.setBackgroundTintList(null); ask.setBackground(roundedControl(34));
        ask.setPadding(dp(24), 0, dp(24), 0); ask.setGravity(Gravity.CENTER); ask.setIncludeFontPadding(false);
        ask.setStateListAnimator(null); ask.setTextColor(0xff25251f);
        setContentView(body);
        android.graphics.drawable.GradientDrawable panel = new android.graphics.drawable.GradientDrawable();
        panel.setColor(0xfff6f5ef); panel.setCornerRadius(dp(28));
        getWindow().setBackgroundDrawable(panel);
        getWindow().getDecorView().setClipToOutline(true);
        int panelWidth = Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(540));
        int previousHeight = getIntent().getIntExtra("previous_panel_height", 0);
        body.measure(android.view.View.MeasureSpec.makeMeasureSpec(panelWidth, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));
        int recordingHeight = body.getMeasuredHeight();
        boolean resize = previousHeight > 0 && android.animation.ValueAnimator.areAnimatorsEnabled();
        getWindow().setWindowAnimations(0);
        getWindow().setLayout(panelWidth, resize ? previousHeight : WindowManager.LayoutParams.WRAP_CONTENT);
        if (resize) body.post(() -> {
            if (isFinishing() || isDestroyed() || showingSettings) return;
            panelResize = PanelResize.start(previousHeight, recordingHeight,
                height -> getWindow().setLayout(panelWidth, height),
                () -> getWindow().setLayout(panelWidth, WindowManager.LayoutParams.WRAP_CONTENT));
        });
        getWindow().setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        WindowManager.LayoutParams params = getWindow().getAttributes(); params.y = dp(40); getWindow().setAttributes(params);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (saved != null && saved.getBoolean("settings", false)) showSettings();
    }
    private void showSettings() {
        if (sendRequested || submitted || showingSettings) return;
        showingSettings = true;
        handler.removeCallbacksAndMessages(null); transcript.finish(null); resetSpeech();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        switchPanel(new SettingsPanel(this, this::backToQuestion, this::finish));
    }
    private void backToQuestion() {
        if (!showingSettings) return;
        SettingsPanel.hideKeyboard(this, getWindow().getDecorView());
        showingSettings = false;
        switchPanel(recordingBody);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        PilotService service = PilotService.current();
        if (service != null && service.hasQuestion(session)) handler.postDelayed(this::startSpeech, 250);
    }
    private void switchPanel(android.view.View content) {
        if (panelResize != null) { panelResize.cancel(); panelResize = null; }
        setContentView(content);
        getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(32), dp(540)),
            WindowManager.LayoutParams.WRAP_CONTENT);
    }
    @Override public void onBackPressed() {
        if (showingSettings) backToQuestion(); else super.onBackPressed();
    }
    @Override protected void onResume() {
        super.onResume(); foreground = true;
        PilotService service = PilotService.current();
        if (service == null || !service.hasQuestion(session)) { restartSession(); return; }
        if (!listening && !permissionPending) handler.postDelayed(this::startSpeech, 250);
    }
    private void startSpeech() {
        if (!foreground || showingSettings || sessionExpired || isFinishing() || submitted || sendRequested || listening) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionPending = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1); return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { recordingError(); return; }
        recordingFailed = false; ask.setText("Ask Poppy");
        if (recognizer == null) recognizer = SpeechRecognizer.createSpeechRecognizer(this);
        transcript.begin(); speechReady = false; speechEnded = false;
        int current = ++generation; segmentHeard = false;
        recognizer.setRecognitionListener(new RecognitionListener() {
            private boolean valid() { return current == generation && listening; }
            public void onReadyForSpeech(Bundle b) { if (valid()) QuestionActivity.this.onReadyForSpeech(b); }
            public void onBeginningOfSpeech() { if (valid()) QuestionActivity.this.onBeginningOfSpeech(); }
            public void onRmsChanged(float r) { if (valid()) QuestionActivity.this.onRmsChanged(r); }
            public void onBufferReceived(byte[] b) {}
            public void onEndOfSpeech() { if (valid()) QuestionActivity.this.onEndOfSpeech(); }
            public void onError(int e) { if (valid()) QuestionActivity.this.onError(e); }
            public void onResults(Bundle b) { if (valid()) QuestionActivity.this.onResults(b); }
            public void onPartialResults(Bundle b) { if (valid()) QuestionActivity.this.onPartialResults(b); }
            public void onEvent(int e, Bundle b) {}
        });
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        listening = true; status.setText("Poppy");
        try { recognizer.startListening(intent); }
        catch (RuntimeException error) { resetSpeech(); recoverRecording(); }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants); permissionPending = false;
        if (request == 1 && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) startSpeech();
        else status.setText("Allow microphone access in Poppy’s app permissions.");
    }
    private void requestSend() {
        if (submitted || sendRequested) return;
        loading.begin(0);
        sendRequested = true; bars.setVisibility(android.view.View.GONE); loading.setVisibility(android.view.View.VISIBLE); ask.setEnabled(false); includeScreenshot.setEnabled(false); handler.removeCallbacksAndMessages(null);
        if (listening) {
            status.setText("Poppy");
            // A stop before ready can race the provider connection. After end-of-speech,
            // the provider is already finalizing; a second stop is unnecessary.
            if (speechReady && !speechEnded) stopForSend();
            handler.postDelayed(() -> {
                if (sendRequested && !submitted) recoverSend("timeout");
            }, 8000);
        } else submit();
    }
    private void stopForSend() {
        if (recognizer == null || !listening || speechEnded) return;
        speechEnded = true;
        try { recognizer.stopListening(); }
        catch (RuntimeException error) { recoverSend("stop_failed"); }
    }
    private void recoverSend(String reason) {
        handler.removeCallbacksAndMessages(null);
        transcript.finish(null);
        android.util.Log.i("PoppySpeech", "send_recovery=" + reason + " hasWords=" + !transcript.text().isBlank());
        resetSpeech();
        submit();
    }
    private void recoverRecording() {
        if (recoveryAttempts++ < 2) {
            status.setText("Poppy"); handler.postDelayed(this::startSpeech, 800);
        } else recordingError();
    }
    private void recordingError() {
        recordingFailed = true;
        retry("Couldn’t record. Try again.");
        ask.setText("Retry recording");
    }
    private void restartSession() {
        handler.removeCallbacksAndMessages(null);
        resetSpeech();
        sessionExpired = true;
        retry("Start a new question.");
        ask.setText("Restart Poppy");
    }
    private void retry(String message) {
        sendRequested = false; bars.setVisibility(android.view.View.VISIBLE); loading.setVisibility(android.view.View.GONE); ask.setEnabled(true); includeScreenshot.setEnabled(screenshotAvailable);
        status.setText(message);
    }
    private void submit() {
        if (!sendRequested || submitted || !foreground || isFinishing()) return;
        String value = transcript.text();
        if (value.isEmpty()) { retry("Poppy"); recoveryAttempts = 0; startSpeech(); return; }
        PilotService service = PilotService.current();
        if (service == null || !service.hasQuestion(session)) { restartSession(); return; }
        submitted = true; resetSpeech(); service.ask(session, value, includeScreenshot.isChecked(), getWindow().getDecorView().getHeight(), loading.elapsedSeconds()); finish();
    }
    private void resetSpeech() {
        generation++; listening = false; bars.clear();
        if (recognizer != null) { recognizer.cancel(); recognizer.destroy(); recognizer = null; }
    }
    @Override protected void onStop() {
        foreground = false; handler.removeCallbacksAndMessages(null);
        if (!permissionPending) { transcript.finish(null); resetSpeech(); sendRequested = false; bars.setVisibility(android.view.View.VISIBLE); loading.setVisibility(android.view.View.GONE); ask.setEnabled(true); includeScreenshot.setEnabled(screenshotAvailable); }
        super.onStop();
    }
    @Override protected void onDestroy() {
        if (panelResize != null) panelResize.cancel();
        handler.removeCallbacksAndMessages(null); resetSpeech();
        if (isFinishing() && !submitted) { PilotService service = PilotService.current(); if (service != null) service.cancelQuestion(session); }
        if (active.get() == this) active.clear();
        super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("question", transcript.snapshot()); state.putBoolean("settings", showingSettings); super.onSaveInstanceState(state); }
    private android.graphics.drawable.Drawable roundedControl(int radius) {
        android.graphics.drawable.GradientDrawable shape = new android.graphics.drawable.GradientDrawable();
        shape.setColor(0xffe4e3dc); shape.setCornerRadius(dp(radius));
        return new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2233332a), shape, null);
    }
    private Button addButton(LinearLayout parent, String title) { Button b = new Button(this); b.setText(title); b.setAllCaps(false); parent.addView(b, new LinearLayout.LayoutParams(-1, dp(52))); return b; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    @Override public void onReadyForSpeech(Bundle params) {
        speechReady = true; status.setText("Poppy");
        if (sendRequested) stopForSend();
    }
    @Override public void onBeginningOfSpeech() { segmentHeard = true; status.setText("Poppy"); }
    @Override public void onEndOfSpeech() { speechEnded = true; bars.clear(); status.setText("Poppy"); }
    private String recognizedWords(Bundle result) {
        ArrayList<String> words = result == null ? null : result.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return words == null || words.isEmpty() ? "" : words.get(0);
    }
    @Override public void onResults(Bundle result) {
        if (submitted || isFinishing() || !listening) return;
        handler.removeCallbacksAndMessages(null);
        transcript.finish(recognizedWords(result));
        // A terminal callback releases this segment. Reuse the connected recognizer.
        generation++; listening = false; bars.clear(); recoveryAttempts = 0;
        if (sendRequested) submit();
        else { status.setText("Poppy"); handler.postDelayed(this::startSpeech, 300); }
    }
    @Override public void onPartialResults(Bundle result) {
        if (listening && !submitted) transcript.partial(recognizedWords(result));
    }
    @Override public void onError(int error) {
        if (!listening || submitted) return;
        android.util.Log.w("PoppySpeech", "recognition_error=" + error + " sending=" + sendRequested + " ready=" + speechReady);
        if (sendRequested) { recoverSend("error_" + error); return; }
        handler.removeCallbacksAndMessages(null);
        transcript.finish(null);
        boolean silence = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
        if (silence) { generation++; listening = false; bars.clear(); }
        else resetSpeech();
        boolean transientError = error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
            || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
            || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT || error == SpeechRecognizer.ERROR_NETWORK
            || error == SpeechRecognizer.ERROR_SERVER || error == SpeechRecognizer.ERROR_AUDIO;
        if (silence || (transientError && recoveryAttempts++ < 2)) {
            status.setText("Poppy"); handler.postDelayed(this::startSpeech, silence ? 500 : 800);
        } else if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            status.setText("Allow microphone access in Poppy’s app permissions.");
        } else recordingError();
    }
    private float rmsMin = Float.POSITIVE_INFINITY, rmsMax = Float.NEGATIVE_INFINITY;
    private int rmsSamples;
    @Override public void onRmsChanged(float rms) {
        if (!listening || !Float.isFinite(rms)) return;
        rmsMin = Math.min(rmsMin, rms); rmsMax = Math.max(rmsMax, rms);
        if (++rmsSamples == 1 || rmsSamples % 30 == 0) {
            android.util.Log.i("PoppyMeter", "samples=" + rmsSamples + " min=" + rmsMin + " max=" + rmsMax + " speech=" + segmentHeard);
            rmsMin = Float.POSITIVE_INFINITY; rmsMax = Float.NEGATIVE_INFINITY;
        }
        bars.microphone(rms);
    }
    private class WaveBars extends android.view.View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final float[] levels = new float[11];
        private float target, envelope, phase;
        private float noiseFloor = Float.NaN;
        private final float[] recent = new float[60];
        private int samples;
        private boolean gateOpen, animating;
        private long lastFrame;
        WaveBars() { super(QuestionActivity.this); paint.setColor(0xff33332a); }
        void clear() { gateOpen = false; target = 0; animateBars(); }
        void microphone(float rms) {
            if (!Float.isFinite(rms)) return;
            recent[samples++ % recent.length] = rms;
            int count = Math.min(samples, recent.length);
            float[] sorted = java.util.Arrays.copyOf(recent, count); java.util.Arrays.sort(sorted);
            float quiet = sorted[(count - 1) / 5];
            if (Float.isNaN(noiseFloor)) noiseFloor = rms;
            // Follow quieter levels quickly; don't learn speech as background noise.
            noiseFloor += (quiet - noiseFloor) * (quiet < noiseFloor ? 0.3f : 0.015f);
            float aboveNoise = Math.max(0, rms - noiseFloor);
            gateOpen = aboveNoise >= (gateOpen ? 0.3f : 0.65f);
            float voice = gateOpen ? Math.min(1, Math.max(0, (aboveNoise - 0.2f) / 3f)) : 0;
            target = (float)Math.sqrt(voice);
            animateBars();
        }
        private void animateBars() {
            if (animating || !isAttachedToWindow()) return;
            animating = true; lastFrame = android.os.SystemClock.uptimeMillis(); post(frame);
        }
        private final Runnable frame = new Runnable() {
            @Override public void run() {
                long now = android.os.SystemClock.uptimeMillis();
                float dt = Math.max(0.016f, Math.min(0.12f, (now - lastFrame) / 1000f)); lastFrame = now;
                float smoothing = 1 - (float)Math.exp(-dt / (target > envelope ? 0.10f : 0.26f));
                envelope += (target - envelope) * smoothing;
                phase += dt * (1.5f + 3 * envelope);
                boolean moving = target > 0 || envelope > 0.002f;
                for (int i = 0; i < levels.length; i++) {
                    // Decorative voice-driven variation, not a frequency spectrum.
                    // Every bar stays at its own fixed horizontal position.
                    float variation = 0.28f + 0.72f * (0.5f + 0.5f * (float)Math.sin(phase * (0.8f + (i % 5) * 0.23f) + i * 2.399f));
                    float desired = envelope * variation;
                    levels[i] += (desired - levels[i]) * (1 - (float)Math.exp(-dt / (0.06f + (i % 4) * 0.025f)));
                    moving |= levels[i] > 0.002f;
                }
                if (!moving) { envelope = 0; java.util.Arrays.fill(levels, 0); }
                invalidate();
                if (moving) postDelayed(this, 66); else animating = false;
            }
        };
        @Override protected void onDetachedFromWindow() {
            removeCallbacks(frame); animating = false; super.onDetachedFromWindow();
        }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            float step = Math.min(dp(20), getWidth() / 15.6f), start = (getWidth() - step * (levels.length - 1)) / 2f;
            for (int i = 0; i < levels.length; i++) {
                float height = 0.8f * (dp(6) + levels[i] * dp(110)), x = start + i * step;
                canvas.drawRoundRect(x - dp(3), (getHeight() - height) / 2, x + dp(3), (getHeight() + height) / 2, dp(3), dp(3), paint);
            }
        }
    }
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEvent(int type, Bundle params) {}
}
