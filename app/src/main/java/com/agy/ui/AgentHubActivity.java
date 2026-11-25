package com.agy.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.agy.agents.AgentSessionController;
import org.json.JSONObject;

/** Native, user-controlled handoff screen for existing ChatGPT/Gemini subscriptions. */
public final class AgentHubActivity extends Activity {
    private String selectedProvider = "chatgpt";
    private TextView stateView;
    private TextView stagedView;
    private TextView responseView;
    private EditText promptInput;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        selectedProvider = getPreferences(MODE_PRIVATE).getString("provider", "chatgpt");
        buildUi();
        refreshState();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(20), dp(20), dp(28));
        page.setBackgroundColor(0xff121419);
        scroll.addView(page);
        setContentView(scroll);

        page.addView(text("Agent sessions", 27, Color.WHITE, true), matchWrap());
        TextView intro = text(
                "Use your existing ChatGPT and Gemini subscriptions in Chrome. No API keys or API billing.",
                14, 0xffb9c0cc, false);
        intro.setPadding(0, dp(8), 0, dp(16));
        page.addView(intro, matchWrap());

        stateView = text("No request staged.", 14, Color.WHITE, true);
        stateView.setPadding(dp(14), dp(12), dp(14), dp(12));
        stateView.setBackground(round(0xff292e38, dp(14)));
        page.addView(stateView, matchWrap());

        TextView explanation = text(
                "Manual handoff is intentional. ChatGPT's current Terms prohibit automatic or programmatic extraction of its output. Antigravity stages the question; you send it in Chrome, copy the reply, then import it here. The app does not read browser cookies, passwords, or page content.",
                12, 0xffaeb6c4, false);
        explanation.setPadding(0, dp(12), 0, dp(14));
        page.addView(explanation, matchWrap());

        LinearLayout providers = new LinearLayout(this);
        providers.setOrientation(LinearLayout.HORIZONTAL);
        Button chatgpt = button("ChatGPT");
        Button gemini = button("Gemini");
        LinearLayout.LayoutParams first = new LinearLayout.LayoutParams(0, dp(48), 1);
        first.rightMargin = dp(5);
        providers.addView(chatgpt, first);
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0, dp(48), 1);
        second.leftMargin = dp(5);
        providers.addView(gemini, second);
        chatgpt.setOnClickListener(v -> selectProvider("chatgpt"));
        gemini.setOnClickListener(v -> selectProvider("gemini"));
        page.addView(providers, matchWrap());

        promptInput = new EditText(this);
        promptInput.setTextColor(Color.WHITE);
        promptInput.setHintTextColor(0xff929baa);
        promptInput.setHint("Stage a question for the selected agent…");
        promptInput.setTextSize(15);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setMinLines(4);
        promptInput.setMaxLines(9);
        promptInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        promptInput.setBackground(round(0xff292e38, dp(12)));
        LinearLayout.LayoutParams inputParams = matchWrap();
        inputParams.topMargin = dp(14);
        page.addView(promptInput, inputParams);

        Button stage = button("Stage question for Antigravity");
        stage.setOnClickListener(v -> {
            JSONObject result = AgentSessionController.stagePrompt(
                    selectedProvider, promptInput.getText().toString());
            toast(result.optString("message", result.optString("error", "Could not stage question.")));
            ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(promptInput.getWindowToken(), 0);
            refreshState();
        });
        page.addView(stage, matchWrap());

        Button copyOpen = button("Copy staged question and open provider");
        copyOpen.setOnClickListener(v -> copyPromptAndOpen());
        page.addView(copyOpen, matchWrap());

        stagedView = text("No staged question.", 13, 0xffd7dce5, false);
        stagedView.setTextIsSelectable(true);
        stagedView.setPadding(dp(14), dp(14), dp(14), dp(14));
        stagedView.setBackground(round(0xff22262e, dp(12)));
        LinearLayout.LayoutParams stagedParams = matchWrap();
        stagedParams.topMargin = dp(12);
        page.addView(stagedView, stagedParams);

        Button importReply = button("Import copied provider reply");
        importReply.setOnClickListener(v -> importClipboard());
        page.addView(importReply, matchWrap());

        responseView = text("Imported reply will appear here.", 13, 0xffd7dce5, false);
        responseView.setTextIsSelectable(true);
        responseView.setPadding(dp(14), dp(14), dp(14), dp(14));
        responseView.setBackground(round(0xff22262e, dp(12)));
        LinearLayout.LayoutParams responseParams = matchWrap();
        responseParams.topMargin = dp(12);
        page.addView(responseView, responseParams);

        Button copyReply = button("Copy imported reply");
        copyReply.setOnClickListener(v -> {
            JSONObject s = AgentSessionController.statusJson();
            String value = s.optString("response", "");
            if (value.isEmpty()) { toast("There is no imported reply."); return; }
            setClipboard("Agent reply", value);
            toast("Imported reply copied.");
        });
        page.addView(copyReply, matchWrap());

        Button clear = button("Clear handoff");
        clear.setOnClickListener(v -> {
            AgentSessionController.clear();
            promptInput.setText("");
            refreshState();
        });
        page.addView(clear, matchWrap());

        TextView instructions = text(
                "Workflow: stage a question here or from Antigravity's Python bridge → copy/open provider → paste and send in Chrome → copy the answer → return here and tap Import → Antigravity can read the imported answer.",
                12, 0xff929baa, false);
        instructions.setPadding(0, dp(16), 0, 0);
        page.addView(instructions, matchWrap());
    }

    private void copyPromptAndOpen() {
        JSONObject state = AgentSessionController.statusJson();
        String p = state.optString("prompt", "");
        String provider = state.optString("provider", selectedProvider);
        if (p.isEmpty()) {
            toast("Stage a question first.");
            return;
        }
        setClipboard("Antigravity agent prompt", p);
        JSONObject opened = AgentSessionController.openProvider(this, provider);
        toast(opened.optBoolean("ok", false)
                ? "Question copied. Paste it into the provider chat and send manually."
                : opened.optString("error", "Question copied, but the provider could not be opened."));
    }

    private void importClipboard() {
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null || !manager.hasPrimaryClip()) {
                toast("Clipboard is empty. Copy the provider reply first.");
                return;
            }
            ClipData clip = manager.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) {
                toast("Clipboard is empty. Copy the provider reply first.");
                return;
            }
            CharSequence text = clip.getItemAt(0).coerceToText(this);
            JSONObject result = AgentSessionController.importResponse(text == null ? "" : text.toString());
            toast(result.optBoolean("ok", false)
                    ? "Provider reply imported for Antigravity."
                    : result.optString("error", "Could not import reply."));
            refreshState();
        } catch (Exception e) {
            toast("Could not read clipboard: " + e.getMessage());
        }
    }

    private void setClipboard(String label, String value) {
        ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager != null) manager.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private void selectProvider(String provider) {
        selectedProvider = provider;
        getPreferences(MODE_PRIVATE).edit().putString("provider", provider).apply();
    }

    private void refreshState() {
        JSONObject state = AgentSessionController.statusJson();
        selectedProvider = state.optString("provider", selectedProvider);
        String request = state.optString("request_id", "");
        String status = state.optString("status", "idle");
        stateView.setText("Provider: " + (selectedProvider.isEmpty() ? "none" : selectedProvider)
                + "\nStatus: " + status
                + (request.isEmpty() ? "" : "\nRequest: " + request));
        String question = state.optString("prompt", "");
        stagedView.setText(question.isEmpty() ? "No staged question." : question);
        String response = state.optString("response", "");
        responseView.setText(response.isEmpty() ? "No imported reply." : response);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(0xff394252, dp(14)));
        b.setPadding(dp(8), 0, dp(8), 0);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(8);
        b.setLayoutParams(params);
        return b;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
