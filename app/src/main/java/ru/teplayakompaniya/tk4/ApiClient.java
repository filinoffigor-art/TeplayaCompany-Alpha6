
package ru.teplayakompaniya.tk4;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ApiClient {
    // Replaced with real /exec URL before the final APK build.
    public static final String API_URL = "https://script.google.com/macros/s/AKfycbwoAeJf7fPZDGvteBsjrver2RhPGfooZdFZn-FhrZv_rnvxw-5FpvCcr6kfKFFeOdmv/exec";

    public interface Callback {
        void onSuccess(JSONObject json);
        void onError(String error);
    }

    private final SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> pending = new HashSet<>();

    public ApiClient(Context context, SharedPreferences prefs) {
        this.prefs = prefs;
        if (prefs.getString("deviceId", "").isEmpty()) {
            prefs.edit().putString("deviceId", UUID.randomUUID().toString()).apply();
        }
    }

    public static boolean isConfigured() {
        return API_URL.startsWith("https://script.google.com/") && API_URL.endsWith("/exec");
    }

    public boolean hasToken() {
        return !prefs.getString("apiToken", "").isEmpty()
                && !prefs.getString("apiUserId", "").isEmpty();
    }

    public String getUserId() { return prefs.getString("apiUserId", ""); }
    public String getUserName() { return prefs.getString("apiUserName", ""); }
    public String getRole() { return prefs.getString("apiRole", ""); }
    public String getDeviceId() { return prefs.getString("deviceId", ""); }
    public String getToken() { return prefs.getString("apiToken", ""); }

    public void clearAuth() {
        prefs.edit().remove("apiToken").remove("apiUserId").remove("apiUserName").remove("apiRole").apply();
    }

    public void pair(String userId, String pairingCode, Callback cb) {
        try {
            JSONObject b = new JSONObject();
            b.put("action", "pair");
            b.put("requestId", requestId());
            b.put("userId", userId);
            b.put("pairingCode", pairingCode);
            b.put("deviceId", getDeviceId());
            postRaw(b, new Callback() {
                @Override public void onSuccess(JSONObject json) {
                    if (!json.optBoolean("ok")) { cb.onError(json.optString("error","PAIRING_FAILED")); return; }
                    prefs.edit()
                            .putString("apiUserId", json.optString("userId"))
                            .putString("apiUserName", json.optString("name"))
                            .putString("apiRole", json.optString("role"))
                            .putString("apiToken", json.optString("token"))
                            .apply();
                    cb.onSuccess(json);
                }
                @Override public void onError(String error) { cb.onError(error); }
            });
        } catch (Exception e) { cb.onError(e.toString()); }
    }

    public void bootstrap(String period, Callback cb) {
        try {
            JSONObject b = authBody("bootstrap");
            b.put("period", period);
            postRaw(b, cb);
        } catch (Exception e) { cb.onError(e.toString()); }
    }

    public void mutate(String action, JSONObject fields, Callback cb) {
        try {
            JSONObject b = authBody(action);
            if (fields != null) {
                java.util.Iterator<String> it = fields.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    b.put(k, fields.get(k));
                }
            }
            postRaw(b, cb);
        } catch (Exception e) { cb.onError(e.toString()); }
    }

    private JSONObject authBody(String action) throws Exception {
        JSONObject b = new JSONObject();
        b.put("action", action);
        b.put("requestId", requestId());
        b.put("userId", getUserId());
        b.put("deviceId", getDeviceId());
        b.put("token", getToken());
        return b;
    }

    private static String requestId() {
        return "APP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0,8);
    }

    private void postRaw(JSONObject body, Callback cb) {
        // Exclude generated request IDs, but include account/device and actual payload.
        final String pendingKey;
        try {
            JSONObject fingerprint = new JSONObject(body.toString());
            fingerprint.remove("requestId");
            pendingKey = fingerprint.toString();
        } catch (Exception ignored) { postError(cb, "Некорректный запрос"); return; }
        synchronized (pending) {
            if (!pending.add(pendingKey)) { postError(cb, "Это действие уже выполняется"); return; }
        }
        executor.execute(() -> {
            HttpURLConnection c = null;
            try {
                URL u = new URL(API_URL);
                c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(20000);
                c.setReadTimeout(35000);
                c.setRequestMethod("POST");
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                // Buffered mode allows the platform to follow the Apps Script redirect.
                try(OutputStream os = c.getOutputStream()) { os.write(bytes); }

                int code = c.getResponseCode();
                InputStream is = code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream();
                String text = readAll(is);
                JSONObject json = new JSONObject(text.isEmpty() ? "{}" : text);
                if (!json.optBoolean("ok", false)) {
                    postError(cb, json.optString("error", "HTTP_" + code));
                } else {
                    postSuccess(cb, json);
                }
            } catch (Exception e) {
                postError(cb, "Нет связи с сервером. Проверьте интернет и повторите запрос.");
            } finally {
                if (c != null) c.disconnect();
                synchronized (pending) { pending.remove(pendingKey); }
            }
        });
    }

    private void postSuccess(Callback cb, JSONObject json) {
        main.post(() -> cb.onSuccess(json));
    }

    private void postError(Callback cb, String error) {
        main.post(() -> cb.onError(error));
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try(BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
