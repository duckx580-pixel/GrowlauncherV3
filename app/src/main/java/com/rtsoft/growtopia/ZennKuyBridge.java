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

    /** Same OAuth as Real Chrome. state/dsh come from the dashboard page via openAsResult. */
    public static final String GOOGLE_OAUTH_URL =
        "https://accounts.google.com/o/oauth2/v2/auth"
        + "?client_id=389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com"
        + "&redirect_uri=" + Uri.encode("https://login.growtopiagame.com/google/callback")
        + "&response_type=code"
        + "&scope=" + Uri.encode("openid profile email")
        + "&prompt=select_account"
        + "&service=lso"
        + "&flowName=GeneralOAuthFlow";

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

    public static void startResolving() { openGoogleAccountPicker(); }

    public static void openGoogleAccountPicker() {
        Activity act = Main.mainApp;
        if (act == null) return;
        openChrome(act, GOOGLE_OAUTH_URL);
    }

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
