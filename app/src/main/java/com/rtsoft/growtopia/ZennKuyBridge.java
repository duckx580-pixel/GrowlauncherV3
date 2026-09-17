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

    public static String generateMac() {
        try { return new LoginSpoof(Main.mainApp).generateMac(); }
        catch (Exception e) { return "02:00:00:00:00:00"; }
    }
    public static String generateRid() {
        try { return new LoginSpoof(Main.mainApp).generateRid(); }
        catch (Exception e) { return ""; }
    }
    public static String generateWk() {
        try { return new LoginSpoof(Main.mainApp).generateWk(); }
        catch (Exception e) { return ""; }
    }

    public static void startResolving() {
        openGoogleAccountPicker();
    }

    public static void openGoogleAccountPicker() {
        Activity act = Main.mainApp;
        if (act == null) return;
        openChrome(act, DASHBOARD_URL);
    }

    /** Real WebViewManager.openAsResult / openInBrowser. */
    public static void openChrome(Activity activity, String url) {
        if (activity == null || url == null || url.isEmpty()) return;
        activity.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                activity.startActivityForResult(intent, 1);
                Log.d(TAG, "openAsResult VIEW rc=1 " + url);
            } catch (Exception e) {
                Log.e(TAG, "openChrome: " + e.getMessage());
            }
        });
    }
}
