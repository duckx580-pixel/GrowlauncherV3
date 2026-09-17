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

    public static final String GOOGLE_OAUTH_URL =
        "https://accounts.google.com/o/oauth2/v2/auth"
        + "?client_id=389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com"
        + "&redirect_uri=" + Uri.encode("https://login.growtopiagame.com/google/callback")
        + "&response_type=code"
        + "&scope=" + Uri.encode("openid profile email")
        + "&prompt=select_account";

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

    /** ZK menu button. Opens Chrome accountchooser. Does not use GMS. */
    public static void startResolving() {
        Activity act = Main.mainApp;
        if (act == null) {
            Log.e(TAG, "startResolving: Main.mainApp is null");
            return;
        }
        act.runOnUiThread(() -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_OAUTH_URL));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                act.startActivity(intent);
                Log.d(TAG, "startResolving: Chrome OAuth");
            } catch (Exception e) {
                Log.e(TAG, "startResolving: " + e.getMessage());
            }
        });
    }
}
