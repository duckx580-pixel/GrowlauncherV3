package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Google Sign-In — dashboard API WebView flow, fully contained inside the process.
 *
 * <p><b>Why not the Android Google Sign-In SDK?</b><br>
 * The Android SDK ({@code GoogleSignIn.getClient(...).getSignInIntent()}) always fails with
 * {@code DEVELOPER_ERROR} (status code 10) on debug-signed APKs because the SHA-1 fingerprint
 * is not registered in the Google Cloud project.
 *
 * <p><b>Why not the standard OAuth URL (LoadURLPost)?</b><br>
 * The standard OAuth flow ends with a {@code grow://growtopia?token=...} redirect.
 * If the official Growtopia app is installed and also handles the {@code grow://} scheme,
 * Android routes the token to it instead of V3 — silently swallowing the login and causing
 * the "loops back to Google" symptom. Even inside a WebView, the redirect can fail silently
 * on some devices.
 *
 * <p><b>Actual flow (dashboard API, same as Genta Hax v5.53 "Start Resolving"):</b><br>
 * {@link ZennKuyBridge#startResolving()} loads the Growtopia dashboard URL
 * ({@code https://login.growtopiagame.com/player/login/dashboard?valKey=...}) in the in-app
 * WebView. After the user authenticates with Google, the dashboard page calls
 * {@code NativeApp.nativeSignIn(ltoken)} from its own JavaScript.
 * {@link WebViewManager.WebViewJavascriptInterface#nativeSignIn} picks this up and delivers
 * the token to the engine via {@code nativeOnScriptCall("nativeSignIn", token)} — no
 * {@code grow://} redirect involved at all.
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

    // ── Entry point called by the native engine ──────────────────────────────────────────────
    /**
     * Delegates immediately to {@link ZennKuyBridge#startResolving()} which handles
     * ltoken spoof injection or dashboard-API Google OAuth as appropriate.
     *
     * <p>The previous "WebView already visible, do nothing" path was insufficient: the
     * engine's {@link WebViewManager#LoadURLPost} opens the WebView with the standard
     * Growtopia OAuth URL, which terminates with a {@code grow://} redirect that either
     * goes to the official app or fails silently. We now always redirect to the dashboard
     * URL instead.
     */
    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }

        mainActivity.runOnUiThread(() -> {
            // Always delegate to ZennKuyBridge.
            //
            // The normal path (engine calls LoadURLPost before SignIn) opened the WebView with
            // the standard Growtopia OAuth URL, which ends with a grow:// redirect.  That
            // redirect either gets intercepted by the official Growtopia app (if installed) or
            // fails silently — both cause the "loops back to Google" symptom.
            //
            // ZennKuyBridge.startResolving() checks ltoken spoof first (instant login), then
            // falls through to triggerWebViewLogin() which loads the Growtopia DASHBOARD URL
            // instead.  The dashboard page calls NativeApp.nativeSignIn(token) from its own
            // JavaScript after Google auth completes — no grow:// redirect needed at all.
            // This is the same mechanism Genta Hax v5.53 "Start Resolving" uses.
            Log.d(TAG, "SignIn: delegating to ZennKuyBridge (dashboard API path)");
            spoof.setGoogleLogs("Triggering dashboard API Google OAuth via ZennKuyBridge");
            ZennKuyBridge.startResolving();
        });
    }

    // ── onActivityResult dispatcher (called from Main.onActivityResult) ────
    // The Android SDK is no longer used, so this will never be triggered in
    // normal operation. Kept so Main.onActivityResult wiring compiles.
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        // No-op: we no longer use startActivityForResult / Android SDK.
        Log.d(TAG, "handleSignInResult called (SDK no longer used) — ignoring");
    }

    // ── Token delivery on the GL thread ──────────────────────────────────────────────
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
