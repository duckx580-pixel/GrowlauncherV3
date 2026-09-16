package com.rtsoft.growtopia;

import android.util.Log;
// AppLogger is in the same package — no import needed

public final class ZennKuyBridge {
    private static final String TAG = "ZennKuyBridge";

    private ZennKuyBridge() {}

    /**
     * Set to {@code true} the moment the JS interface (or interceptUrl) delivers a
     * non-empty token to the engine via {@code nativeOnScriptCall("nativeSignIn", token)}.
     *
     * <p>Once set, {@link #startResolving()} short-circuits and does NOT load the
     * dashboard URL.  Without this guard the engine calls {@code SignIn()} as a retry
     * after the WebView is already hidden (OAuth completed), {@code IsVisible()} returns
     * false, and {@code triggerWebViewLogin()} loads the dashboard — firing a second
     * {@code nativeSignIn} that disrupts the running auth attempt and causes
     * "Please try login again."
     *
     * <p>Reset to {@code false} at the start of each new auth session
     * ({@code LoadURLPost} no-spoof path).
     */
    public static volatile boolean sTokenDelivered = false;

    /**
     * Growtopia dashboard login URL.
     *
     * Public so WebViewManager.nativeSignIn("") can open it directly in Chrome
     * when the login page triggers Google sign-in without an SDK token.
     */
    public static final String DASHBOARD_URL =
        "https://login.growtopiagame.com/player/login/dashboard?valKey=40db4045f2d8c572efe8c4a060605726";

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
     * Initiates login from the ImGui mod menu or from GoogleSignInHelper.SignIn().
     *
     * <p>Priority order:
     * <ol>
     *   <li>Token already delivered this session → return immediately (no dashboard reload).</li>
     *   <li>ltoken spoof enabled + ltoken present → inject immediately, no UI.</li>
     *   <li>ltoken spoof enabled + refresh token → exchange first, then inject.</li>
     *   <li>WebView already visible (OAuth flow started by LoadURLPost) → let it
     *       continue; do not load a second URL on top of the running flow.</li>
     *   <li>No WebView / no spoof → load the Growtopia dashboard URL which triggers
     *       Google auth via JS callback (NativeApp.nativeSignIn).</li>
     * </ol>
     */
    public static void startResolving() {
        try {
            if (Main.mainApp == null) return;

            // 0. If a token was already delivered to the engine this session, bail out.
            //    The engine sometimes calls SignIn() as a retry after the WebView is hidden
            //    (OAuth just completed).  Without this guard, IsVisible() returns false
            //    (WebView was hidden in nativeSignIn handler), triggerWebViewLogin() fires,
            //    the dashboard page calls nativeSignIn a second time, and the double-token
            //    delivery causes "Please try login again."
            if (sTokenDelivered) {
                Log.d(TAG, "startResolving: token already delivered — skipping dashboard redirect");
                AppLogger.log(TAG, "startResolving: token already delivered, skipping");
                return;
            }

            // 1. ltoken spoof check.
            LoginSpoof spoof = spoofIfEnabled();
            if (spoof != null) {
                String ltoken = spoof.getLtoken();
                if (!ltoken.isEmpty()) {
                    injectLtoken(ltoken);
                    return;
                }
                String refreshToken = spoof.getRefreshToken();
                if (!refreshToken.isEmpty()) {
                    Log.d(TAG, "startResolving: exchanging refresh token");
                    spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                        @Override public void onSuccess(String lt) {
                            Log.d(TAG, "startResolving: refresh->ltoken OK");
                            injectLtoken(lt);
                        }
                        @Override public void onFailure(String msg, String raw) {
                            Log.w(TAG, "startResolving: refresh->ltoken failed (" + msg + "), falling back to WebView login");
                            triggerWebViewLogin();
                        }
                    });
                    return;
                }
                Log.w(TAG, "startResolving: spoof enabled but no tokens; using WebView login");
            }

            // 2. If the WebView is already visible, LoadURLPost has already started the
            //    OAuth flow. Let it proceed — loading the dashboard URL on top would
            //    cancel the in-progress auth and cause a second redirect chain.
            WebViewManager wvm = Main.mainApp.webViewManager;
            if (wvm != null && wvm.IsVisible()) {
                Log.d(TAG, "startResolving: WebView already showing OAuth flow — skipping dashboard redirect");
                AppLogger.log(TAG, "startResolving: WebView visible, OAuth flow already running");
                return;
            }

            // 3. WebView not visible — load the dashboard URL as a fallback.
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
     * Loads the Growtopia login dashboard as a fallback when startResolving() determines
     * no WebView or spoof path is active.
     *
     * <p>Uses {@link WebViewManager#LoadURLPost} with the POST data the game engine
     * originally sent (stored in {@code wvm.last_url} / {@code wvm.last_packet}) so the
     * server receives the same device fingerprint it expects.  Falls back to a plain GET
     * via {@link WebViewManager#LoadURL} only if no POST data is available — for example
     * when startResolving() is called from the mod menu before the engine has ever issued
     * a LoadURLPost for the login flow.
     */
    private static void triggerWebViewLogin() {
        Main.mainApp.runOnUiThread(() -> {
            try {
                WebViewManager wvm = Main.mainApp.webViewManager;
                if (wvm == null) {
                    Log.e(TAG, "triggerWebViewLogin: webViewManager is null");
                    return;
                }

                String storedUrl    = wvm.last_url;
                String storedPacket = wvm.last_packet;

                if (storedUrl != null && !storedUrl.isEmpty()
                        && storedPacket != null && !storedPacket.isEmpty()) {
                    // Re-issue the original LoadURLPost so the page receives the same
                    // POST body (device fingerprint, valKey, etc.) the engine sent.
                    // This is the path Real Growlauncher v5.57 always takes.
                    AppLogger.log(TAG, "triggerWebViewLogin: replaying stored LoadURLPost — url=" + storedUrl);
                    Log.d(TAG, "triggerWebViewLogin: LoadURLPost (stored data) — url=" + storedUrl);
                    byte[] postData = storedPacket.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                    wvm.LoadURLPost(storedUrl, postData, false);
                } else {
                    // No stored POST data — fall back to GET.  The dashboard page still
                    // renders and the NativeApp.nativeSignIn JS bridge still fires, but
                    // the server may not personalise the session without the POST body.
                    AppLogger.log(TAG, "triggerWebViewLogin: no stored POST data — GET fallback — url=" + DASHBOARD_URL);
                    Log.d(TAG, "triggerWebViewLogin: LoadURL GET fallback — url=" + DASHBOARD_URL);
                    wvm.LoadURL(DASHBOARD_URL, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "triggerWebViewLogin: " + e.getMessage());
            }
        });
    }
}
