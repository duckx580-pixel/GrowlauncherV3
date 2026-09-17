package com.gentz.launcher.api;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.rtsoft.growtopia.LoginSpoof;
import com.rtsoft.growtopia.Main;

/**
 * Gentz launcher Google path.
 * Same jobs PowerKuy does in libPowerKuy.so, implemented in this package.
 * Does not load or ship PowerKuy code.
 */
public final class GentzGoogle {
    private static final String TAG = "GentzGoogle";

    public static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    public static final String CALLBACK =
        "https://login.growtopiagame.com/google/callback";

    public static final String OAUTH =
        "https://accounts.google.com/o/oauth2/v2/auth"
        + "?client_id=" + CLIENT_ID
        + "&redirect_uri=" + Uri.encode(CALLBACK)
        + "&response_type=code"
        + "&scope=" + Uri.encode("openid profile email")
        + "&prompt=select_account";

    private GentzGoogle() {}

    public static boolean isLtokenSpoofActive() {
        try {
            if (Main.mainApp == null) return false;
            LoginSpoof s = new LoginSpoof(Main.mainApp);
            return s.isEnabled() && !s.getLtoken().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getSafeGameVersion() {
        return "4.70";
    }

    public static void notifyValueChanged(int type, String key, Object value) {
        Log.d(TAG, "notify type=" + type + " key=" + key);
        if (key == null) return;
        if (key.contains("google_login")) {
            openChrome(OAUTH);
            return;
        }
        if (key.contains("google_redirect") && value != null) {
            String payload = String.valueOf(value);
            String token = payload;
            int i = payload.indexOf("token=");
            if (i >= 0) token = payload.substring(i + 6);
            if (Main.mainApp != null && Main.mainApp.googleSignInHelper != null
                    && token != null && !token.isEmpty()) {
                try {
                    Main.mainApp.googleSignInHelper.OnSignIn(0, token);
                } catch (Throwable t) {
                    Log.e(TAG, "OnSignIn: " + t.getMessage());
                }
            }
        }
    }

    public static void openChrome(String url) {
        Activity act = Main.mainApp;
        if (act == null || url == null) return;
        act.runOnUiThread(() -> {
            try {
                act.startActivityForResult(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), 1);
                Log.d(TAG, "Chrome " + url);
            } catch (Exception e) {
                Log.e(TAG, "Chrome fail " + e.getMessage());
            }
        });
    }

    public static void openAsResult(Activity act, String url) {
        if (act == null || url == null) return;
        act.runOnUiThread(() -> {
            act.startActivityForResult(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), 1);
        });
    }
}
