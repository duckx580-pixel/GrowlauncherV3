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

    /** Chrome account picker, then continue into Growtopia web login. */
    public static final String GOOGLE_ACCOUNT_CHOOSER_URL =
        "https://accounts.google.com/AccountChooser?continue=" +
        "https%3A%2F%2Flogin.growtopiagame.com%2Fplayer%2Flogin%2Fdashboard%3FvalKey%3D40db4045f2d8c572efe8c4a060605726";

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

    /** Called from Java SignIn and from libzennkuy JNICall hook. */
    public static void startResolving() {
        openGoogleAccountPicker();
    }

    public static void openGoogleAccountPicker() {
        Activity act = Main.mainApp;
        if (act == null) {
            Log.e(TAG, "openGoogleAccountPicker: mainApp null");
            return;
        }
        openChrome(act, GOOGLE_ACCOUNT_CHOOSER_URL);
    }

    public static void openChrome(Activity activity, String url) {
        if (activity == null || url == null || url.isEmpty()) return;
        Runnable launch = () -> {
            try {
                Uri uri = Uri.parse(url);
                Intent chrome = new Intent(Intent.ACTION_VIEW, uri);
                chrome.addCategory(Intent.CATEGORY_BROWSABLE);
                chrome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                chrome.setPackage("com.android.chrome");
                try {
                    activity.startActivity(chrome);
                    Log.d(TAG, "openChrome: chrome " + url);
                    return;
                } catch (Exception ignored) {}
                Intent any = new Intent(Intent.ACTION_VIEW, uri);
                any.addCategory(Intent.CATEGORY_BROWSABLE);
                any.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                activity.startActivity(any);
                Log.d(TAG, "openChrome: default browser " + url);
            } catch (Exception e) {
                Log.e(TAG, "openChrome failed: " + e.getMessage());
            }
        };
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            launch.run();
        } else {
            activity.runOnUiThread(launch);
        }
    }
}
