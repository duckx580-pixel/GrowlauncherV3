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

    // Opens the WebView-based Google sign-in (same flow as GoogleSignInHelper.signIn()).
    public static void startResolving() {
        try {
            if (Main.mainApp == null) return;
            Main.mainApp.runOnUiThread(() -> {
                try {
                    GoogleSignInHelper helper = Main.mainApp.googleSignInHelper;
                    if (helper != null) {
                        helper.SignIn();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "startResolving inner: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "startResolving: " + e.getMessage());
        }
    }
}
