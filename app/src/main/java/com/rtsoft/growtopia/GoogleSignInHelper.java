package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Google Sign-In — Chrome-direct flow, matching real GrowLauncher v5.57.
 *
 * <p><b>Why not the Android Google Sign-In SDK?</b><br>
 * The Android SDK ({@code GoogleSignIn.getClient(...).getSignInIntent()}) always fails with
 * {@code DEVELOPER_ERROR} (status code 10) on debug-signed APKs because the SHA-1 fingerprint
 * is not registered in the Google Cloud project. Real GrowLauncher v5.57 (libPowerKuy.so) never
 * uses that SDK — its native layer opens Chrome directly to the Growtopia OAuth URL the game
 * engine already stored in {@link WebViewManager#last_url} when it called {@code LoadURLPost}.
 *
 * <p><b>Flow (Chrome path):</b><br>
 * {@code SignIn()} → open Chrome to {@code last_url} ({@code accounts.google.com/v3/signin/...})
 * → user authenticates → Google redirects to {@code login.growtopiagame.com/google/callback}
 * → Growtopia server redirects to {@code grow://growtopia?info=...&token=...}
 * → Android delivers to {@link Main#onNewIntent} → {@link Main#handleIntent}
 * → {@link NativeAppInterface#OnDeepLinkProcess} on the GL thread.
 *
 * <p><b>Fallback (WebView path):</b><br>
 * If {@code last_url} is not yet populated (engine hasn't called {@code LoadURLPost}),
 * {@link ZennKuyBridge#startResolving()} is called, which shows the in-app WebView login flow.
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    // Kept for onActivityResult dispatch wiring in Main (harmless if never triggered).
    static final int RC_GOOGLE_SIGNIN = 9001;

    Activity mainActivity;

    // Records the captured token/status for the Login Spoof menu.
    private final LoginSpoof spoof;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {}

    // ── Entry point called by the native engine ────────────────────────────
    /**
     * Opens Chrome directly to the Growtopia Google OAuth URL — no Android SDK, no Error 10.
     *
     * <p>The URL ({@code accounts.google.com/v3/signin/accountchooser?...}) was stored by the
     * game engine in {@link WebViewManager#last_url} when it called {@code LoadURLPost}. We just
     * pass it straight to Chrome via {@code ACTION_VIEW}, exactly as libPowerKuy.so does in v5.57.
     */
    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }

        // Grab the OAuth URL the engine already stored.
        String oauthUrl = null;
        WebViewManager wvm = Main.mainApp.webViewManager;
        if (wvm != null && wvm.last_url != null && !wvm.last_url.isEmpty()) {
            oauthUrl = wvm.last_url;
        }

        final String urlToOpen = oauthUrl;

        mainActivity.runOnUiThread(() -> {
            if (urlToOpen != null) {
                // ── Chrome path: open the Google OAuth page in the external browser ──
                Log.d(TAG, "SignIn: opening OAuth URL in Chrome (bypassing Android SDK)");
                spoof.setGoogleLogs("Opening Chrome OAuth: " + urlToOpen.substring(0, Math.min(80, urlToOpen.length())) + "…");
                try {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlToOpen));
                    browserIntent.addCategory(Intent.CATEGORY_BROWSABLE);
                    mainActivity.startActivity(browserIntent);
                } catch (Exception e) {
                    Log.e(TAG, "SignIn: could not open Chrome — falling back to WebView: " + e);
                    spoof.setGoogleLogs("Chrome open failed, using WebView: " + e.getMessage());
                    ZennKuyBridge.startResolving();
                }
            } else {
                // ── Fallback: last_url not yet set — use in-app WebView login ──
                Log.d(TAG, "SignIn: last_url not available — falling back to WebView login");
                spoof.setGoogleLogs("last_url not set, using WebView login");
                ZennKuyBridge.startResolving();
            }
        });
    }

    // ── onActivityResult dispatcher (called from Main.onActivityResult) ────
    // The Android SDK is no longer used, so this will never be triggered in
    // normal operation. Kept so Main.onActivityResult wiring compiles.
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        // No-op: we no longer use startActivityForResult / Android SDK.
        Log.d(TAG, "handleSignInResult called (SDK no longer used) — ignoring");
    }

    // ── Token delivery on the GL thread ───────────────────────────────────
    // Used only if some external path calls OnSignIn directly (e.g. ltoken spoof).
    private static final int MAX_DELIVER_RETRIES = 40; // ~4s at 100ms
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

    public void deliverResult(int code, String token) {
        if (code == 0 && token != null && !token.isEmpty()) {
            spoof.setGoogleToken(token);
            spoof.setGoogleLogs("Google sign-in OK (token length=" + token.length() + ")");
        } else {
            spoof.setGoogleLogs("Google sign-in failed (code=" + code + ")");
        }
        deliverToGl(code, token, 0);
    }

    private void deliverToGl(int code, String token, int attempt) {
        GLSurfaceView glView = SharedActivity.mGLView;
        if (glView == null) {
            if (attempt >= MAX_DELIVER_RETRIES) {
                Log.w(TAG, "GL view never became available; dropping OnSignIn(code=" + code + ")");
                return;
            }
            deliverHandler.postDelayed(() -> deliverToGl(code, token, attempt + 1), 100);
            return;
        }
        glView.queueEvent(() -> {
            try {
                OnSignIn(code, token);
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "OnSignIn native unavailable: " + e.getMessage());
            }
        });
    }
}
