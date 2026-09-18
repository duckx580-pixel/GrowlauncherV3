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
            return "02:00:00:00:00:00";
        }
    }

    public static String generateRid() {
        try {
            LoginSpoof s = spoof();
            return s != null ? s.generateRid() : "";
        } catch (Exception e) {
            return "";
        }
    }

    public static String generateWk() {
        try {
            LoginSpoof s = spoof();
            return s != null ? s.generateWk() : "";
        } catch (Exception e) {
            return "";
        }
    }

    /** Same door as Real/Genta: dashboard WebView, then host-jump Chrome. */
    public static void startResolving() {
        Activity act = Main.mainApp;
        if (act == null) {
            Log.e(TAG, "startResolving: Main.mainApp is null");
            return;
        }
        if (sTokenDelivered) {
            Log.d(TAG, "startResolving: token already delivered");
            return;
        }
        act.runOnUiThread(() -> {
            try {
                WebViewManager wvm = Main.mainApp.webViewManager;
                if (wvm == null) {
                    Log.e(TAG, "startResolving: webViewManager is null");
                    return;
                }
                if (wvm.IsVisible()) {
                    Log.d(TAG, "startResolving: WebView already visible");
                    return;
                }
                String storedUrl = wvm.last_url;
                String storedPacket = wvm.last_packet;
                if (storedUrl != null && !storedUrl.isEmpty()
                        && storedPacket != null && !storedPacket.isEmpty()) {
                    byte[] post = storedPacket.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                    wvm.LoadURLPost(storedUrl, post, true);
                    Log.d(TAG, "startResolving: replay LoadURLPost " + storedUrl);
                } else {
                    wvm.LoadURL(DASHBOARD_URL, true);
                    Log.d(TAG, "startResolving: dashboard GET fallback");
                }
            } catch (Exception e) {
                Log.e(TAG, "startResolving: " + e.getMessage());
            }
        });
    }
}
