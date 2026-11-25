package com.agy.agents;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONObject;
import java.util.UUID;

/**
 * Human-in-the-loop handoff between Antigravity and the user's subscribed
 * ChatGPT/Gemini web sessions. It never reads browser pages or credentials.
 */
public final class AgentSessionController {
    private static final Object LOCK = new Object();
    private static String requestId = "";
    private static String provider = "";
    private static String prompt = "";
    private static String response = "";
    private static String status = "idle";
    private static String error = "";
    private static long updatedAt;

    private AgentSessionController() {}

    public static JSONObject stagePrompt(String requestedProvider, String requestedPrompt) {
        JSONObject out = new JSONObject();
        String p = normalizeProvider(requestedProvider);
        if (p == null) {
            try { out.put("ok", false); out.put("error", "provider must be chatgpt or gemini"); }
            catch (Exception ignored) {}
            return out;
        }
        if (requestedPrompt == null || requestedPrompt.trim().isEmpty()) {
            try { out.put("ok", false); out.put("error", "prompt is empty"); }
            catch (Exception ignored) {}
            return out;
        }
        if (requestedPrompt.length() > 30000) {
            try { out.put("ok", false); out.put("error", "prompt exceeds the 30,000 character limit"); }
            catch (Exception ignored) {}
            return out;
        }
        synchronized (LOCK) {
            if (!requestId.isEmpty() && !"response_imported".equals(status)
                    && !"cleared".equals(status) && !"error".equals(status)) {
                try {
                    out.put("ok", false);
                    out.put("error", "There is already a staged request. Complete or clear it before staging another.");
                    out.put("request_id", requestId);
                } catch (Exception ignored) {}
                return out;
            }
            requestId = UUID.randomUUID().toString();
            provider = p;
            prompt = requestedPrompt;
            response = "";
            status = "awaiting_user_handoff";
            error = "";
            updatedAt = System.currentTimeMillis();
            try {
                out.put("ok", true);
                out.put("request_id", requestId);
                out.put("provider", provider);
                out.put("status", status);
                out.put("message", "Question staged. Open Agent sessions, copy it to the provider, then import the reply.");
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject openProvider(Context context, String requestedProvider) {
        JSONObject out = new JSONObject();
        String p = normalizeProvider(requestedProvider);
        if (p == null) {
            try { out.put("ok", false); out.put("error", "provider must be chatgpt or gemini"); }
            catch (Exception ignored) {}
            return out;
        }
        String url = "gemini".equals(p)
                ? "https://gemini.google.com/app" : "https://chatgpt.com/";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.setPackage("com.android.chrome");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) == null) {
                intent.setPackage(null);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
            synchronized (LOCK) {
                provider = p;
                status = requestId.isEmpty() ? "provider_opened" : status;
                updatedAt = System.currentTimeMillis();
            }
            out.put("ok", true);
            out.put("provider", p);
            out.put("url", url);
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("error", e.getMessage() == null ? "Could not open provider." : e.getMessage());
            } catch (Exception ignored) {}
        }
        return out;
    }

    /** Called only from an explicit button tap after the user copied a reply. */
    public static JSONObject importResponse(String copiedText) {
        JSONObject out = new JSONObject();
        if (copiedText == null || copiedText.trim().isEmpty()) {
            try { out.put("ok", false); out.put("error", "Clipboard is empty. Copy the provider reply first."); }
            catch (Exception ignored) {}
            return out;
        }
        if (copiedText.length() > 100000) {
            try { out.put("ok", false); out.put("error", "Copied reply exceeds the 100,000 character limit."); }
            catch (Exception ignored) {}
            return out;
        }
        synchronized (LOCK) {
            if (!prompt.isEmpty() && prompt.equals(copiedText.trim())) {
                try { out.put("ok", false); out.put("error", "Clipboard still contains the staged prompt. Copy the provider's reply first."); }
                catch (Exception ignored) {}
                return out;
            }
            if (requestId.isEmpty()) {
                try { out.put("ok", false); out.put("error", "No staged request exists. Stage a question first."); }
                catch (Exception ignored) {}
                return out;
            }
            response = copiedText;
            status = "response_imported";
            error = "";
            updatedAt = System.currentTimeMillis();
            try {
                out.put("ok", true);
                out.put("request_id", requestId);
                out.put("provider", provider);
                out.put("status", status);
                out.put("characters", response.length());
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject statusJson() {
        JSONObject out = new JSONObject();
        synchronized (LOCK) {
            try {
                out.put("ok", true);
                out.put("request_id", requestId);
                out.put("provider", provider);
                out.put("prompt", prompt);
                out.put("response", response);
                out.put("status", status);
                out.put("error", error);
                out.put("updated_at", updatedAt);
            } catch (Exception ignored) {}
        }
        return out;
    }

    public static JSONObject readResponse(String expectedRequestId) {
        JSONObject out = statusJson();
        synchronized (LOCK) {
            if (expectedRequestId != null && !expectedRequestId.isEmpty()
                    && !expectedRequestId.equals(requestId)) {
                try {
                    out.put("ok", false);
                    out.put("error", "The current imported response belongs to a different request.");
                } catch (Exception ignored) {}
            } else if (!"response_imported".equals(status)) {
                try {
                    out.put("ok", false);
                    out.put("error", "The provider reply has not been imported yet.");
                } catch (Exception ignored) {}
            }
        }
        return out;
    }

    public static JSONObject clear() {
        synchronized (LOCK) {
            requestId = "";
            provider = "";
            prompt = "";
            response = "";
            status = "cleared";
            error = "";
            updatedAt = System.currentTimeMillis();
        }
        return statusJson();
    }

    private static String normalizeProvider(String value) {
        if (value == null) return null;
        String p = value.trim().toLowerCase(java.util.Locale.ROOT);
        return ("chatgpt".equals(p) || "gemini".equals(p)) ? p : null;
    }
}
