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
     * entirely (which would otherwise fail with Error 10 if the APK signing certificate does
     * not match the OAuth client ID registration).
     *
     * <ul>
     *   <li>Spoof enabled + ltoken present → inject ltoken immediately, no UI dialog shown.</li>
     *   <li>Spoof enabled + only refresh token → exchange for ltoken first, then inject.</li>
     *   <li>Spoof enabled but no tokens, or spoof disabled → fall through to Google OAuth.</li>
     * </ul>
     */
    public static void startResolving() {
        try {
            if (Main.mainApp == null) return;

            // Check ltoken spoof before touching Google OAuth.
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
                            Log.w(TAG, "startResolving: refresh→ltoken failed (" + msg + "), falling back to Google OAuth");
                            triggerGoogleSignIn();
                        }
                    });
                    return;
                }
                // Spoof enabled but no tokens stored yet — fall through to Google OAuth
                // so the user can get a Google token to seed the spoof menu with.
                Log.w(TAG, "startResolving: spoof enabled but no tokens; using Google OAuth");
            }

            triggerGoogleSignIn();
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

    /** Opens the Google account-picker activity — the pre-existing OAuth flow. */
    private static void triggerGoogleSignIn() {
        Main.mainApp.runOnUiThread(() -> {
            try {
                GoogleSignInHelper helper = Main.mainApp.googleSignInHelper;
                if (helper != null) {
                    helper.SignIn();
                }
            } catch (Exception e) {
                Log.e(TAG, "triggerGoogleSignIn: " + e.getMessage());
            }
        });
    }
}
