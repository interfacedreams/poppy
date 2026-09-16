package dev.tommy.daylightpilot;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;
import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;

public class PilotService extends AccessibilityService {
    // The DC-1 orange button reports F12 (verified on the tablet).
    private static final int DEFAULT_BUTTON_KEY_CODE = KeyEvent.KEYCODE_F12;
    private static WeakReference<PilotService> reference = new WeakReference<>(null);
    public static PilotService current() { return reference.get(); }
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long learningUntil;
    private int keyCode = -1;
    private int captureGeneration;
    private boolean capturing;
    private WindowManager manager;
    private View panel;
    private Bitmap preview;
    private byte[] questionBytes;
    private int questionSession;
    private boolean questionOpen, sessionActive;
    private boolean sending;
    private final OpenAIClient openAI = new OpenAIClient();
    private OpenAIClient.Call call;
    private TextView answerText;
    private PoppyLoadingView loading;
    private ScrollView answerScroll;
    private TextView answerHeading;
    private Button newQuestion;
    private android.animation.ValueAnimator panelResize;
    private boolean receiverRegistered;
    private final BroadcastReceiver screenOff = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { cancelLearning(); dismiss(); }
    };

    @Override protected void onServiceConnected() {
        reference = new WeakReference<>(this);
        manager = getSystemService(WindowManager.class);
        keyCode = getSharedPreferences("pilot", MODE_PRIVATE).getInt("keyCode", DEFAULT_BUTTON_KEY_CODE);
        registerReceiver(screenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        receiverRegistered = true;
        Log.i("DaylightPilot", "Service connected; selected key=" + keyCode);
    }
    public void beginLearning() { learningUntil = SystemClock.uptimeMillis() + 30000; }
    public boolean isLearning() { return SystemClock.uptimeMillis() < learningUntil; }
    public void cancelLearning() { learningUntil = 0; }
    private boolean unlocked() {
        return getSystemService(PowerManager.class).isInteractive()
            && !getSystemService(KeyguardManager.class).isKeyguardLocked();
    }
    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (!unlocked()) return false;
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_POWER || code == KeyEvent.KEYCODE_HOME
                || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_UNKNOWN) return false;
        if (isLearning() && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            keyCode = code;
            learningUntil = 0;
            getSharedPreferences("pilot", MODE_PRIVATE).edit().putInt("keyCode", code).apply();
            Log.i("DaylightPilot", "Button learned: " + KeyEvent.keyCodeToString(code) + " scan=" + event.getScanCode());
            return true;
        }
        if (code != keyCode || keyCode < 0) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            if (panel != null || capturing || questionOpen || sending) dismiss(); else capture();
        }
        return true;
    }

    public void capture() {
        if (capturing || !unlocked()) return;
        dismiss();
        capturing = true;
        int generation = ++captureGeneration;
        handler.postDelayed(() -> {
            if (capturing && generation == captureGeneration) {
                capturing = false;
                captureGeneration++;
                beginQuestion(null);
            }
        }, 5000);
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult result) {
                    try (HardwareBuffer buffer = result.getHardwareBuffer()) {
                        if (generation != captureGeneration || current() != PilotService.this) return;
                        capturing = false;
                        if (!unlocked()) return;
                        Bitmap hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware == null) { beginQuestion(null); return; }
                        Bitmap copy;
                        try { copy = hardware.copy(Bitmap.Config.ARGB_8888, false); }
                        finally { hardware.recycle(); }
                        if (copy == null) { beginQuestion(null); return; }
                        Log.i("DaylightPilot", "Screenshot success " + copy.getWidth() + "x" + copy.getHeight());
                        beginQuestion(copy);
                    } catch (RuntimeException error) {
                        capturing = false;
                        Log.e("DaylightPilot", "Screenshot conversion failed", error);
                        if (generation == captureGeneration && current() == PilotService.this)
                            beginQuestion(null);
                    }
                }
                @Override public void onFailure(int errorCode) {
                    if (generation != captureGeneration || current() != PilotService.this) return;
                    capturing = false;
                    Log.w("DaylightPilot", "Screenshot error=" + errorCode);
                    beginQuestion(null);
                }
            });
        } catch (RuntimeException error) {
            capturing = false;
            Log.e("DaylightPilot", "Capture failed", error);
            beginQuestion(null);
        }
    }

    private void beginQuestion(Bitmap image) {
        sessionActive = true;
        if (image == null) { questionBytes = null; openQuestion(); return; }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try { image.compress(Bitmap.CompressFormat.JPEG, 90, output); }
        finally { image.recycle(); }
        questionBytes = output.toByteArray();
        openQuestion();
    }

    private void openQuestion() { openQuestion(0); }

    private void openQuestion(int previousHeight) {
        questionSession = captureGeneration;
        questionOpen = true;
        Intent intent = new Intent(this, QuestionActivity.class);
        intent.putExtra("session", questionSession);
        intent.putExtra("screenshot_available", questionBytes != null);
        intent.putExtra("previous_panel_height", previousHeight);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
        try { startActivity(intent); }
        catch (RuntimeException error) {
            questionOpen = false; sessionActive = false; questionBytes = null;
            Log.e("DaylightPilot", "Question window failed", error);
            show(null, "Could not open your question", "Open Poppy and try again.");
        }
    }

    public boolean hasQuestion(int session) {
        return questionOpen && sessionActive && questionSession == session && !sending && unlocked();
    }
    public void cancelQuestion(int session) { if (session == questionSession && !sending) dismiss(); }
    public void ask(int session, String question, boolean includeScreenshot, int recordingHeight, float loadingElapsedSeconds) {
        if (!hasQuestion(session)) return;
        questionOpen = false; sending = true;
        final int generation = captureGeneration;
        byte[] image = includeScreenshot ? questionBytes : null;
        show(null, "Poppy", "Thinking…", recordingHeight);
        if (loading != null) loading.begin(loadingElapsedSeconds);
        final StringBuilder answer = new StringBuilder();
        call = openAI.ask(this, question, image, new OpenAIClient.Listener() {
            @Override public void delta(String value) { handler.post(() -> {
                if (generation != captureGeneration || answerText == null) return;
                if (value.isEmpty()) return;
                revealAnswer(); answer.append(value); answerText.setText(answer.toString());
            }); }
            @Override public void done() { handler.post(() -> {
                if (generation != captureGeneration || answerText == null) return;
                revealAnswer(); sending = false; call = null;
                if (newQuestion != null) newQuestion.setEnabled(true);
                if (answer.length() == 0) { answerHeading.setText("Could not finish"); answerText.setText("No answer returned. Tap New question to try again."); }
                Log.i("DaylightPilot", "Answer complete; characters=" + answer.length());
            }); }
            @Override public void error(String value) { handler.post(() -> {
                if (generation != captureGeneration || answerText == null) return;
                revealAnswer(); sending = false; call = null;
                if (newQuestion != null) newQuestion.setEnabled(true);
                answerHeading.setText("Could not finish");
                answerHeading.setVisibility(View.VISIBLE);
                answerText.setText(answer.length() > 0 ? answer + "\n\n" + value : value);
                Log.w("DaylightPilot", "Answer request failed");
            }); }
        });
    }

    private void startNewQuestion() {
        if (sending || questionOpen || !sessionActive || !unlocked()) return;
        // Keep the original screenshot, but invalidate callbacks from the last answer.
        captureGeneration++;
        int previousHeight = panel == null ? 0 : panel.getHeight();
        removePanel();
        openQuestion(previousHeight);
    }

    private void show(Bitmap image, String title, String message) {
        show(image, title, message, 0);
    }

    private void show(Bitmap image, String title, String message, int previousHeight) {
        if (current() != this || !unlocked()) { if (image != null) image.recycle(); return; }
        removePanel();
        preview = image;
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(20), dp(24), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xfff6f5ef); background.setCornerRadius(dp(28));
        content.setBackground(background);
        content.setElevation(dp(10));
        content.setClipToOutline(true);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView poppy = new ImageView(this);
        poppy.setImageResource(R.drawable.ic_poppy); poppy.setContentDescription("Poppy");
        poppy.setScaleType(ImageView.ScaleType.FIT_CENTER);
        poppy.setPadding(dp(4), dp(4), dp(4), dp(4));
        header.addView(poppy, new LinearLayout.LayoutParams(dp(48), dp(48)));
        answerHeading = label(title, 23);
        answerHeading.setGravity(Gravity.CENTER);
        answerHeading.setPadding(0, 0, 0, 0); answerHeading.setIncludeFontPadding(false);
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(0, -2, 1);
        headingParams.leftMargin = dp(12); headingParams.rightMargin = dp(12); header.addView(answerHeading, headingParams);
        android.widget.ImageButton close = new android.widget.ImageButton(this);
        close.setImageResource(R.drawable.ic_close); close.setContentDescription("Close Poppy");
        close.setPadding(dp(14), dp(14), dp(14), dp(14)); close.setScaleType(ImageView.ScaleType.FIT_CENTER);
        GradientDrawable circle = new GradientDrawable(); circle.setColor(0xffe4e3dc); circle.setCornerRadius(dp(24));
        close.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x2233332a), circle, null));
        close.setOnClickListener(v -> dismiss()); header.addView(close, new LinearLayout.LayoutParams(dp(48), dp(48)));
        content.addView(header, new LinearLayout.LayoutParams(-1, dp(48)));
        if (image != null) {
            ImageView view = new ImageView(this); view.setImageBitmap(image);
            view.setContentDescription("Screenshot of the app you were viewing");
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int available = manager.getCurrentWindowMetrics().getBounds().height();
            content.addView(view, new LinearLayout.LayoutParams(-1, Math.min(dp(260), available / 3)));
        }
        answerText = label(message, 19);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(answerText);
        int maxBodyHeight = Math.min(dp(420), manager.getCurrentWindowMetrics().getBounds().height() / 2);
        answerScroll = scroll;
        android.widget.FrameLayout answerArea = new android.widget.FrameLayout(this);
        answerArea.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, -1));
        if (sending) {
            loading = new PoppyLoadingView(this);
            answerArea.addView(loading, new android.widget.FrameLayout.LayoutParams(-1, -1));
            scroll.setVisibility(View.GONE);
        }
        content.addView(answerArea, new LinearLayout.LayoutParams(-1, maxBodyHeight));

        if (sessionActive) {
            newQuestion = new Button(new android.view.ContextThemeWrapper(this, R.style.QuestionTheme));
            newQuestion.setText("New question"); newQuestion.setAllCaps(false); newQuestion.setTextSize(22);
            newQuestion.setTextColor(0xff25251f); newQuestion.setGravity(Gravity.CENTER);
            newQuestion.setIncludeFontPadding(false); newQuestion.setPadding(dp(24), 0, dp(24), 0);
            newQuestion.setBackgroundTintList(null); newQuestion.setStateListAnimator(null);
            GradientDrawable buttonShape = new GradientDrawable();
            buttonShape.setColor(0xffe4e3dc); buttonShape.setCornerRadius(dp(34));
            newQuestion.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x2233332a), buttonShape, null));
            newQuestion.setEnabled(!sending);
            newQuestion.setOnClickListener(v -> startNewQuestion());
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, dp(68));
            buttonParams.topMargin = dp(12); content.addView(newQuestion, buttonParams);
        }

        int screenWidth = manager.getCurrentWindowMetrics().getBounds().width();
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            Math.min(screenWidth - dp(32), dp(540)), -2,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.y = dp(40);
        // Measure the normal answer size, then let the text area absorb the resize.
        content.measure(View.MeasureSpec.makeMeasureSpec(params.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int answerHeight = content.getMeasuredHeight();
        boolean resize = previousHeight > 0 && android.animation.ValueAnimator.areAnimatorsEnabled();
        if (resize) {
            answerArea.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
            params.height = previousHeight;
        }
        try {
            manager.addView(content, params); panel = content;
            if (resize) content.post(() -> {
                if (panel != content) return;
                panelResize = PanelResize.start(previousHeight, answerHeight, height -> {
                    params.height = height;
                    manager.updateViewLayout(content, params);
                }, () -> {
                    answerArea.setLayoutParams(new LinearLayout.LayoutParams(-1, maxBodyHeight));
                    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                    manager.updateViewLayout(content, params);
                });
            });
            Log.i("DaylightPilot", "Overlay shown");
        } catch (RuntimeException error) {
            Log.e("DaylightPilot", "Overlay failed", error);
            if (preview != null) { preview.recycle(); preview = null; }
        }
    }
    private void revealAnswer() {
        if (loading != null) loading.setVisibility(View.GONE);
        if (answerScroll != null) answerScroll.setVisibility(View.VISIBLE);
    }
    private TextView label(String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(0xff25251f); view.setPadding(0, dp(6), 0, dp(10)); return view;
    }
    public void dismiss() {
        captureGeneration++; capturing = false; questionOpen = false; sending = false; sessionActive = false; questionBytes = null;
        if (call != null) { call.cancel(); call = null; }
        QuestionActivity.closeForSession(questionSession);
        removePanel();
    }
    private void removePanel() {
        if (panelResize != null) { panelResize.cancel(); panelResize = null; }
        if (panel != null) {
            manager.removeViewImmediate(panel); panel = null;
            Log.i("DaylightPilot", "Overlay closed");
        }
        if (preview != null) { preview.recycle(); preview = null; }
        answerText = null; answerHeading = null; newQuestion = null; loading = null; answerScroll = null;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!unlocked() && (panel != null || capturing || questionOpen || sending)) dismiss();
    }
    @Override public void onInterrupt() { cancelLearning(); dismiss(); }
    @Override public boolean onUnbind(Intent intent) { cleanup(); return super.onUnbind(intent); }
    @Override public void onDestroy() { cleanup(); openAI.shutdown(); super.onDestroy(); }
    private void cleanup() {
        cancelLearning(); dismiss(); handler.removeCallbacksAndMessages(null);
        if (receiverRegistered) { unregisterReceiver(screenOff); receiverRegistered = false; }
        if (current() == this) reference.clear();
    }
}
