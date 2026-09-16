package dev.tommy.daylightpilot;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;

/** Settings shown inside the recording panel. */
final class SettingsPanel extends ScrollView {
    SettingsPanel(Activity activity, Runnable back, Runnable close) {
        super(activity);
        setFillViewport(false);
        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24), dp(20), dp(24), dp(20));
        body.setFocusableInTouchMode(true);
        addView(body);
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(icon(activity, R.drawable.ic_back, "Back", back), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label(activity, "Settings", 23); title.setGravity(Gravity.CENTER);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        header.addView(icon(activity, R.drawable.ic_close, "Close Poppy", close), new LinearLayout.LayoutParams(dp(48), dp(48)));
        body.addView(header);
        addModelSettings(activity, body);
        TextView keyLabel = label(activity, "OpenAI API key", 18);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(28); labelParams.bottomMargin = dp(12); body.addView(keyLabel, labelParams);
        EditText input = new EditText(activity);
        input.setTextSize(17); input.setTextColor(0xff25251f); input.setHintTextColor(0xff74736a);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true); input.setSaveEnabled(false);
        input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        updateKeyHint(activity, input);
        input.setOnFocusChangeListener((view, focused) -> updateKeyHint(activity, input));
        input.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable field = new GradientDrawable(); field.setColor(0xffeeede6);
        field.setCornerRadius(dp(18)); field.setStroke(dp(1), 0xffd5d4cb); input.setBackground(field);
        body.addView(input, new LinearLayout.LayoutParams(-1, dp(60)));
        TextView info = label(activity, "Your key is encrypted on this device. Questions and included screenshots go directly to OpenAI, with usage charged to your API account.", 15);
        info.setTextColor(0xff66655c); info.setLineSpacing(dp(3), 1);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(-1, -2);
        infoParams.topMargin = dp(14); infoParams.bottomMargin = dp(24); body.addView(info, infoParams);
        LinearLayout actions = new LinearLayout(activity);
        Button save = pill(activity, "Save key"); Button remove = pill(activity, "Remove key");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(60), 1);
        saveParams.rightMargin = dp(12); actions.addView(save, saveParams);
        actions.addView(remove, new LinearLayout.LayoutParams(0, dp(60), 1)); body.addView(actions);
        TextView status = label(activity, "", 15); status.setVisibility(GONE);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(14); body.addView(status, statusParams);
        save.setOnClickListener(v -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty() && ApiKeyStore.hasKey(activity)) {
                hideKeyboard(activity, input); body.requestFocus();
                feedback(status, "Saved API key kept."); return;
            }
            if (value.isEmpty() || value.matches(".*\\s.*")) { feedback(status, "Enter an API key without spaces."); return; }
            try {
                ApiKeyStore.save(activity, value); input.setText("");
                hideKeyboard(activity, input); body.requestFocus(); updateKeyHint(activity, input);
                feedback(status, "API key saved.");
            } catch (Exception error) { feedback(status, "Could not save your key. Please try again."); }
        });
        remove.setOnClickListener(v -> {
            try {
                ApiKeyStore.delete(activity); input.setText("");
                hideKeyboard(activity, input); body.requestFocus(); updateKeyHint(activity, input);
                feedback(status, "API key removed.");
            } catch (Exception error) { feedback(status, "Could not remove your key. Please try again."); }
        });
        body.requestFocus();
    }
    private void updateKeyHint(Activity activity, EditText input) {
        boolean saved = ApiKeyStore.hasKey(activity);
        // A placeholder indicates presence without loading the credential into the UI.
        input.setHint(saved ? (input.hasFocus() ? "Enter a replacement key" : "••••••••  Saved") : "Enter your API key");
        input.setContentDescription(saved ? "OpenAI API key, saved. Enter a replacement to change it." : "OpenAI API key, not saved.");
    }
    private void addModelSettings(Activity activity, LinearLayout body) {
        ModelOptions saved = ModelSettings.read(activity);
        Spinner model = dropdown(activity, body, "Model");
        Spinner thinking = dropdown(activity, body, "Thinking");
        model.setAdapter(adapter(activity, ModelOptions.Model.values()));
        model.setSelection(saved.model.ordinal());
        setEfforts(activity, thinking, saved.model, saved.effort);
        model.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ModelOptions.Model selected = (ModelOptions.Model) model.getSelectedItem();
                ModelOptions current = ModelSettings.read(activity);
                if (selected == current.model) return;
                ModelOptions.Effort effort = selected.normalize(current.effort);
                ModelSettings.save(activity, selected, effort);
                setEfforts(activity, thinking, selected, effort);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        thinking.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ModelOptions.Model selected = (ModelOptions.Model) model.getSelectedItem();
                ModelOptions.Effort effort = (ModelOptions.Effort) thinking.getSelectedItem();
                if (selected != null && effort != null) ModelSettings.save(activity, selected, effort);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
    }
    private void setEfforts(Activity activity, Spinner spinner, ModelOptions.Model model, ModelOptions.Effort selected) {
        ModelOptions.Effort[] efforts = model.efforts();
        spinner.setAdapter(adapter(activity, efforts));
        for (int i = 0; i < efforts.length; i++) if (efforts[i] == selected) spinner.setSelection(i);
    }
    private <T> ArrayAdapter<T> adapter(Activity activity, T[] values) {
        ArrayAdapter<T> adapter = new ArrayAdapter<>(activity, R.layout.selector_value, values);
        adapter.setDropDownViewResource(R.layout.selector_option);
        return adapter;
    }
    private Spinner dropdown(Activity activity, LinearLayout body, String title) {
        TextView label = label(activity, title, 18);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = dp(20); labelParams.bottomMargin = dp(6); body.addView(label, labelParams);
        Spinner spinner = new Spinner(activity, Spinner.MODE_DROPDOWN);
        spinner.setId(View.generateViewId()); label.setLabelFor(spinner.getId());
        spinner.setContentDescription(title); spinner.setMinimumHeight(dp(48));
        spinner.setBackgroundTintList(null);
        spinner.setBackgroundResource(R.drawable.selector_background);
        spinner.setPadding(0, 0, 0, 0);
        spinner.setPopupBackgroundResource(R.drawable.selector_popup);
        // Cover the closed field completely, including its chevron, when the menu opens.
        spinner.setDropDownVerticalOffset(-dp(60));
        spinner.setDropDownHorizontalOffset(0);
        spinner.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
            spinner.setDropDownWidth(right - left));
        body.addView(spinner, new LinearLayout.LayoutParams(-1, dp(60)));
        return spinner;
    }
    static void hideKeyboard(Activity activity, View view) {
        activity.getSystemService(android.view.inputmethod.InputMethodManager.class)
            .hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    private void feedback(TextView status, String message) { status.setText(message); status.setVisibility(VISIBLE); }
    private TextView label(Activity activity, String text, int size) {
        TextView view = new TextView(activity); view.setText(text); view.setTextSize(size);
        view.setTextColor(0xff25251f); view.setIncludeFontPadding(false); return view;
    }
    private RippleDrawable background(int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(0xffe4e3dc); shape.setCornerRadius(dp(radius));
        return new RippleDrawable(ColorStateList.valueOf(0x2233332a), shape, null);
    }
    private ImageButton icon(Activity activity, int resource, String description, Runnable action) {
        ImageButton button = new ImageButton(activity); button.setImageResource(resource);
        button.setContentDescription(description); button.setScaleType(ImageView.ScaleType.FIT_CENTER);
        button.setPadding(dp(14), dp(14), dp(14), dp(14)); button.setBackground(background(24));
        button.setOnClickListener(v -> action.run()); return button;
    }
    private Button pill(Activity activity, String text) {
        Button button = new Button(activity); button.setText(text); button.setAllCaps(false);
        button.setTextSize(20); button.setTextColor(0xff25251f); button.setIncludeFontPadding(false);
        button.setPadding(dp(16), 0, dp(16), 0); button.setGravity(Gravity.CENTER);
        button.setBackgroundTintList(null); button.setBackground(background(30)); button.setStateListAnimator(null);
        return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
