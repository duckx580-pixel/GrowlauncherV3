package com.rtsoft.growtopia;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Login-spoof acquisition &amp; persistence layer.
 *
 * <p>This is the Java-side half of the "Login Spoof" feature declared in
 * {@code assets_v557_slim/menu.json}: it turns a Growtopia <b>refresh token</b>
 * into an <b>ltoken</b> using the same server endpoint the native engine uses
 * ({@code player/growid/checktoken}), and it stores the ltoken plus the device
 * identity (MAC / RID / WK / platform) so a login flow can reuse them.
 *
 * <p><b>Scope &amp; limits.</b> This class only <i>acquires and persists</i> the
 * credential. It does <i>not</i> inject the ltoken into the login packet — the
 * prebuilt {@code libgrowtopia.so} in this project is a stock engine that
 * builds the packet in native code and exposes no JNI setter for the ltoken /
 * MAC / RID / WK. Injecting the stored values requires a native hook or a
 * mod-menu engine build that consumes them; when such a consumer exists it can
 * read everything it needs from this class (see {@link #getLtoken()} etc.).
 *
 * <p>All values are kept in the shared {@code "launcher_data"} preferences so
 * they survive restarts, mirroring {@code com.gentz.launcher.App}.
 */
public final class LoginSpoof {

    private static final String TAG = "LoginSpoof";

    // Shared with com.gentz.launcher.App so there is a single prefs file.
    private static final String PREFS = "launcher_data";

    // Preference keys (aliases mirror menu.json for traceability).
    private static final String K_LTOKEN        = "spoof_ltoken";          // ltoken_spoof
    private static final String K_ENABLE        = "spoof_ltoken_enable";   // ltoken_spoof_enable
    private static final String K_MAC           = "spoof_login_mac";       // login_mac
    private static final String K_RID           = "spoof_login_rid";       // login_rid
    private static final String K_WK            = "spoof_login_wk";        // login_wk
    private static final String K_PLATFORM      = "spoof_login_platform";  // login_platform (0=Android,1=Windows)
    private static final String K_REFRESH_TOKEN = "spoof_refresh_token";   // ltoken_refresh_token
    private static final String K_GOOGLE_TOKEN  = "spoof_google_token";    // google_token
    private static final String K_GOOGLE_LOGS   = "spoof_google_logs";     // google_logs

    /** Endpoint the stock engine hits to validate a refresh token (see libgrowtopia.so strings). */
    private static final String CHECKTOKEN_URL =
        "https://login.growtopiagame.com/player/growid/checktoken";

    public static final int PLATFORM_ANDROID = 0;
    public static final int PLATFORM_WINDOWS = 1;

    private static final ExecutorService NET = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final SecureRandom RNG = new SecureRandom();

    private final SharedPreferences prefs;

    public LoginSpoof(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Result of a refresh-token &rarr; ltoken exchange. */
    public interface ExchangeCallback {
        /** @param ltoken the extracted ltoken (already persisted). */
        void onSuccess(String ltoken);
        /** @param message human-readable reason; @param rawResponse the untouched server body (may be empty). */
        void onFailure(String message, String rawResponse);
    }

    // ── Stored getters ─────────────────────────────────────────────────────

    public String getLtoken()       { return prefs.getString(K_LTOKEN, ""); }
    public boolean isEnabled()      { return prefs.getBoolean(K_ENABLE, false); }
    public String getMac()          { return prefs.getString(K_MAC, ""); }
    public String getRid()          { return prefs.getString(K_RID, ""); }
    public String getWk()           { return prefs.getString(K_WK, ""); }
    public int getPlatform()        { return prefs.getInt(K_PLATFORM, PLATFORM_WINDOWS); }
    public String getRefreshToken() { return prefs.getString(K_REFRESH_TOKEN, ""); }
    public String getGoogleToken()  { return prefs.getString(K_GOOGLE_TOKEN, ""); }
    public String getGoogleLogs()   { return prefs.getString(K_GOOGLE_LOGS, "Not started."); }

    // ── Stored setters ─────────────────────────────────────────────────────

    public void setLtoken(String v)       { prefs.edit().putString(K_LTOKEN, safe(v)).apply(); }
    public void setEnabled(boolean v)     { prefs.edit().putBoolean(K_ENABLE, v).apply(); }
    public void setMac(String v)          { prefs.edit().putString(K_MAC, safe(v)).apply(); }
    public void setRid(String v)          { prefs.edit().putString(K_RID, safe(v)).apply(); }
    public void setWk(String v)           { prefs.edit().putString(K_WK, safe(v)).apply(); }
    public void setPlatform(int v)        { prefs.edit().putInt(K_PLATFORM, v).apply(); }
    public void setRefreshToken(String v) { prefs.edit().putString(K_REFRESH_TOKEN, safe(v)).apply(); }
    public void setGoogleToken(String v)  { prefs.edit().putString(K_GOOGLE_TOKEN, safe(v)).apply(); }
    public void setGoogleLogs(String v)   { prefs.edit().putString(K_GOOGLE_LOGS, safe(v)).apply(); }

    public void clearLtoken()      { prefs.edit().remove(K_LTOKEN).putBoolean(K_ENABLE, false).apply(); }
    public void clearGoogleToken() { prefs.edit().remove(K_GOOGLE_TOKEN).apply(); }

    // ── Device-identity generators (menu: login_generate_*) ─────────────────

    /** Random locally-administered unicast MAC, e.g. {@code BE:9C:F8:99:E0:D7}. Persisted. */
    public String generateMac() {
        byte[] b = new byte[6];
        RNG.nextBytes(b);
        b[0] = (byte) ((b[0] & 0xFC) | 0x02); // clear multicast bit, set locally-administered bit
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", b[i] & 0xFF));
        }
        String mac = sb.toString();
        setMac(mac);
        return mac;
    }

    /** Random 32-char uppercase-hex RID (16 bytes). Persisted. */
    public String generateRid() {
        String rid = randomHex(16);
        setRid(rid);
        return rid;
    }

    /** Random 32-char uppercase-hex WK (16 bytes). Persisted. */
    public String generateWk() {
        String wk = randomHex(16);
        setWk(wk);
        return wk;
    }

    private static String randomHex(int bytes) {
        byte[] b = new byte[bytes];
        RNG.nextBytes(b);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte value : b) sb.append(String.format("%02X", value & 0xFF));
        return sb.toString();
    }

    // ── Refresh token → ltoken exchange ─────────────────────────────────────

    /**
     * Exchanges the currently stored refresh token for an ltoken. Runs off the
     * main thread; the callback is delivered on the main thread. On success the
     * ltoken is persisted (see {@link #getLtoken()}) and spoof login is enabled.
     */
    public void exchangeStoredRefreshToken(ExchangeCallback cb) {
        exchangeRefreshToken(getRefreshToken(), cb);
    }

    /**
     * Exchanges {@code refreshToken} for an ltoken via {@link #CHECKTOKEN_URL}.
     *
     * <p>The request body is {@code refreshToken=<rt>&clientData=<cd>}, the same
     * shape the native engine uses. {@code clientData} is assembled from the
     * stored device identity in Growtopia's {@code key|value} line format; if a
     * server rejects it, the untouched response body is handed to
     * {@link ExchangeCallback#onFailure} so the exact reason is visible.
     */
    public void exchangeRefreshToken(final String refreshToken, final ExchangeCallback cb) {
        final ExchangeCallback callback = cb != null ? cb : NOOP;
        if (TextUtils.isEmpty(refreshToken)) {
            MAIN.post(() -> callback.onFailure("Refresh token is empty", ""));
            return;
        }
        setRefreshToken(refreshToken);

        NET.execute(() -> {
            HttpURLConnection conn = null;
            String raw = "";
            try {
                String body = "refreshToken=" + enc(refreshToken)
                        + "&clientData=" + enc(buildClientData());

                conn = (HttpURLConnection) new URL(CHECKTOKEN_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("User-Agent", "UbiServices_SDK_2022.Release.9_PC64_ansi_static");

                byte[] payload = body.getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(payload.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(payload);
                }

                int status = conn.getResponseCode();
                raw = readBody(status >= 200 && status < 400
                        ? conn.getInputStream() : conn.getErrorStream());

                String ltoken = parseLtoken(raw);
                if (!TextUtils.isEmpty(ltoken)) {
                    // Persist and enable — this is the credential the packet needs.
                    prefs.edit()
                            .putString(K_LTOKEN, ltoken)
                            .putBoolean(K_ENABLE, true)
                            .apply();
                    final String out = ltoken;
                    MAIN.post(() -> callback.onSuccess(out));
                } else {
                    final String reason = describeFailure(status, raw);
                    final String rawFinal = raw;
                    MAIN.post(() -> callback.onFailure(reason, rawFinal));
                }
            } catch (Exception e) {
                Log.e(TAG, "refresh token exchange failed: " + e.getMessage());
                final String reason = "Network error: " + e.getMessage();
                final String rawFinal = raw;
                MAIN.post(() -> callback.onFailure(reason, rawFinal));
            } finally {
                if (conn != null) conn.disconnect();
            }
        });
    }

    /**
     * Growtopia {@code clientData} blob built from the stored device identity.
     * Uses the {@code key|value} line format the engine's login packet uses
     * (see {@code libgrowtopia.so}: {@code platformID|}, {@code mac|}, ...).
     * MAC / RID / WK are auto-generated on first use if empty.
     */
    private String buildClientData() {
        String mac = getMac(); if (TextUtils.isEmpty(mac)) mac = generateMac();
        String rid = getRid(); if (TextUtils.isEmpty(rid)) rid = generateRid();
        String wk  = getWk();  if (TextUtils.isEmpty(wk))  wk  = generateWk();
        int platformId = getPlatform() == PLATFORM_WINDOWS ? 0 : 4; // GT: 0=Windows, 4=Android

        StringBuilder sb = new StringBuilder();
        sb.append("platformID|").append(platformId).append('\n');
        sb.append("deviceVersion|0\n");
        sb.append("mac|").append(mac).append('\n');
        sb.append("rid|").append(rid).append('\n');
        sb.append("wk|").append(wk).append('\n');
        sb.append("lmode|1\n");
        return sb.toString();
    }

    /** Extracts the ltoken from a checktoken JSON response, tolerating field-name variants. */
    private static String parseLtoken(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        try {
            JSONObject json = new JSONObject(raw.trim());
            String status = json.optString("status", "");
            if (!TextUtils.isEmpty(status) && !"success".equalsIgnoreCase(status)) {
                return "";
            }
            // The engine reads the ltoken from the "token" field; accept known aliases too.
            for (String key : new String[] {"token", "ltoken", "loginToken"}) {
                String v = json.optString(key, "");
                if (!TextUtils.isEmpty(v)) return v;
            }
        } catch (Exception e) {
            Log.w(TAG, "checktoken response was not JSON: " + e.getMessage());
        }
        return "";
    }

    private static String describeFailure(int status, String raw) {
        String msg = "";
        if (!TextUtils.isEmpty(raw)) {
            try {
                msg = new JSONObject(raw.trim()).optString("message", "");
            } catch (Exception ignored) { /* raw is not JSON */ }
        }
        if (TextUtils.isEmpty(msg)) {
            msg = "No ltoken in response (HTTP " + status + ")";
        }
        return msg;
    }

    // ── small helpers ────────────────────────────────────────────────────────

    private static String readBody(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private static String enc(String v) {
        try {
            return URLEncoder.encode(v, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String safe(String v) {
        return v == null ? "" : v;
    }

    private static final ExchangeCallback NOOP = new ExchangeCallback() {
        @Override public void onSuccess(String ltoken) {}
        @Override public void onFailure(String message, String rawResponse) {}
    };
}
