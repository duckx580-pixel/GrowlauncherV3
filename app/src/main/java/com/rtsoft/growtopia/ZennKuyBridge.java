package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public final class ZennKuyBridge {
    private static final String TAG = "ZennKuyBridge";

    private ZennKuyBridge() {}

    public static volatile boolean sTokenDelivered = false;

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

    public static void startResolving() {
        try {
            if (Main.mainApp == null) return;
            if (sTokenDelivered) {
                Log.d(TAG, "startResolving: token already delivered — skipping dashboard redirect");
                return;
            }
            LoginSpoof spoof = spoofIfEnabled();
            if (spoof != null) {
                String ltoken = spoof.getLtoken();
                if (!ltoken.isEmpty()) {
                    injectLtoken(ltoken);
                    return;
                }
                String refreshToken = spoof.getRefreshToken();
                if (!refreshToken.isEmpty()) {
                    spoof.exchangeStoredRefreshToken(new LoginSpoof.ExchangeCallback() {
                        @Override public void onSuccess(String lt) { injectLtoken(lt); }
                        @Override public void onFailure(String msg, String raw) { triggerWebViewLogin(); }
                    });
                    return;
                }
            }
            WebViewManager wvm = Main.mainApp.webViewManager;
            if (wvm != null && wvm.IsVisible()) {
                return;
            }
            openChrome(Main.mainApp, DASHBOARD_URL);
        } catch (Exception e) {
            Log.e(TAG, "startResolving: " + e.getMessage());
        }
    }

    private static LoginSpoof spoofIfEnabled() {
        try {
            if (Main.mainApp == null) return null;
            LoginSpoof s = new LoginSpoof(Main.mainApp);
            return s.isEnabled() ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void injectLtoken(String ltoken) {
        try {
            Main.mainApp.runOnUiThread(() -> {
                WebViewManager wvm = Main.mainApp.webViewManager;
                if (wvm == null) return;
                wvm.nativeOnScriptCall("nativeSignIn", ltoken);
            });
        } catch (Exception e) {
            Log.e(TAG, "injectLtoken: " + e.getMessage());
        }
    }

    public static void openChrome(Activity activity, String url) {
        if (activity == null || url == null || url.isEmpty()) return;
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setPackage("com.android.chrome");
                try {
                    activity.startActivityForResult(intent, 1);
                    Log.d(TAG, "openChrome: chrome " + url);
                } catch (Exception chromeMissing) {
                    intent.setPackage(null);
                    activity.startActivityForResult(intent, 1);
                    Log.d(TAG, "openChrome: default browser " + url);
                }
            } catch (Exception e) {
                Log.e(TAG, "openChrome failed: " + e.getMessage());
            }
        });
    }

    private static void triggerWebViewLogin() {
        openChrome(Main.mainApp, DASHBOARD_URL);
    }
}
