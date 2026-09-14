package com.rtsoft.growtopia;

import android.util.Log;

public final class ZennKuyBridge {
    private static final String TAG = "ZennKuyBridge";

    private ZennKuyBridge() {}

    private static LoginSpoof spoof() {
        if (Main.mainApp == null) return null;
        return new LoginSpoof(Main.mainApp);
    }

    public static String generateMac() {
        try {
            LoginSpoof s = spoof();
            return s != null ? s.generateMac() : "02:00:00:00:00:00";
        } catch (Exception e) {
            Log.e(TAG, "generateMac: " + e.getMessage());
            return "02:00:00:00:00:00";
        }
    }

    public static String generateRid() {
        try {
            LoginSpoof s = spoof();
            return s != null ? s.generateRid() : "";
        } catch (Exception e) {
            Log.e(TAG, "generateRid: " + e.getMessage());
            return "";
        }
    }

    public static String generateWk() {
        try {
            LoginSpoof s = spoof();
            return s != null ? s.generateWk() : "";
        } catch (Exception e) {
            Log.e(TAG, "generateWk: " + e.getMessage());
            return "";
        }
    }

    /**
     * Initiates login from the ImGui mod menu.
     *
     * <p>When the ltoken spoof is enabled the spoof credential is injected directly into the
     * game engine via {@link WebViewManager#nativeOnScriptCall}, bypassing Google OAuth
     * entirely.  When no spoof credential is available the WebView web-OAuth flow is used
     * (replaying the Growtopia login URL that the engine already stored in
     * {@link WebViewManager#last_url} / {@link WebViewManager#last_packet}).  This opens the
     * real Google accounts website in the in-app WebView — identical to the real GrowLauncher
     * v5.57 behaviour — and never touches the Android Google Sign-In SDK, which would fail
     * with Error 10 on debug-signed APKs.
     *
     * <ul>
     *   <li>Spoof enabled + ltoken present &rarr; inject ltoken immediately, no UI shown.</li>
     *   <li>Spoof enabled + only refresh token &rarr; exchange for ltoken first, then inject.</li>
     *   <li>No spoof / no tokens &rarr; replay stored Growtopia login URL via WebView.</li>
     * </ul>
     */
    public static void startResolving() {
        try {
            if (Main.mainApp == null) return;

            // Check ltoken spoof before touching any OAuth flow.
            LoginSpoof spoof = spoofIfEnabled();
            if (spoof != null) {
                String ltoken = spoof.getLtoken();
                if (!ltoken.isEmpty()) {
                    // Ready immediately — no OAuth dialog at all.
                    injectLtoken(ltoken);
                    return;
                }
                String refreshToken = spoof.getRefreshToken();
                if (!refreshToken.isEmpty()) {
                    // Exchange refresh → ltoken in the background, then inject.
                    Log.d(TAG, "startResolving: exchanging refresh token");
                    spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                        @Override public void onSuccess(String lt) {
                            Log.d(TAG, "startResolving: refresh→ltoken OK");
                            injectLtoken(lt);
                        }
                        @Override public void onFailure(String msg, String raw) {
                            Log.w(TAG, "startResolving: refresh→ltoken failed (" + msg + "), falling back to WebView login");
                            triggerWebViewLogin();
                        }
                    });
                    return;
                }
                Log.w(TAG, "startResolving: spoof enabled but no tokens; using WebView login");
            }

            triggerWebViewLogin();
        } catch (Exception e) {
            Log.e(TAG, "startResolving: " + e.getMessage());
        }
    }

    /** Returns the active {@link LoginSpoof} if the feature is enabled, or {@code null}. */
    private static LoginSpoof spoofIfEnabled() {
        try {
            if (Main.mainApp == null) return null;
            LoginSpoof s = new LoginSpoof(Main.mainApp);
            return s.isEnabled() ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Calls {@code nativeOnScriptCall("nativeSignIn", ltoken)} on the main thread. */
    private static void injectLtoken(String ltoken) {
        try {
            Main.mainApp.runOnUiThread(() -> {
                WebViewManager wvm = Main.mainApp.webViewManager;
                if (wvm == null) {
                    Log.e(TAG, "injectLtoken: webViewManager is null");
                    return;
                }
                Log.d(TAG, "injectLtoken: injecting ltoken (len=" + ltoken.length() + ")");
                wvm.nativeOnScriptCall("nativeSignIn", ltoken);
            });
        } catch (Exception e) {
            Log.e(TAG, "injectLtoken: " + e.getMessage());
        }
    }

    /**
     * Opens Google sign-in via the Growtopia WebView login flow.
     *
     * <p>Replays the login URL that the game engine previously stored in
     * {@link WebViewManager#last_url} / {@link WebViewManager#last_packet}.  The URL redirects
     * through {@code accounts.google.com} web OAuth inside the in-app WebView — exactly like
     * real GrowLauncher v5.57 — without using the Android Google Sign-In SDK (which requires a
     * matching SHA-1 cert and fails with Error 10 on debug builds).
     */
    private static void triggerWebViewLogin() {
        Main.mainApp.runOnUiThread(() -> {
            try {
                WebViewManager wvm = Main.mainApp.webViewManager;
                if (wvm == null) {
                    Log.e(TAG, "triggerWebViewLogin: webViewManager is null");
                    return;
                }
                String url = wvm.last_url;
                String packet = wvm.last_packet;
                if (url != null && !url.isEmpty() && packet != null && !packet.isEmpty()) {
                    Log.d(TAG, "triggerWebViewLogin: replaying stored login URL via WebView");
                    wvm.LoadURLPost(url, packet.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), false);
                } else {
                    // Engine hasn't called LoadURLPost yet — show the WebView so the game
                    // can trigger it on its own when the user taps the Google button.
                    Log.w(TAG, "triggerWebViewLogin: no stored URL yet, showing WebView directly");
                    wvm.ShowWebView();
                }
            } catch (Exception e) {
                Log.e(TAG, "triggerWebViewLogin: " + e.getMessage());
            }
        });
    }
}
