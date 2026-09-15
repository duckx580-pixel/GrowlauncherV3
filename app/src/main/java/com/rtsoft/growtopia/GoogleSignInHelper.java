package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Google Sign-In — in-app WebView flow, fully contained inside the process.
 *
 * <p><b>Why not the Android Google Sign-In SDK?</b><br>
 * The Android SDK ({@code GoogleSignIn.getClient(...).getSignInIntent()}) always fails with
 * {@code DEVELOPER_ERROR} (status code 10) on debug-signed APKs because the SHA-1 fingerprint
 * is not registered in the Google Cloud project.
 *
 * <p><b>Why not Chrome?</b><br>
 * Opening Chrome means the final {@code grow://growtopia?token=...} redirect becomes an Android
 * {@code ACTION_VIEW} Intent. If the official Growtopia app is installed and also handles the
 * {@code grow://} scheme, Android routes the token to it — silently swallowing the login and
 * causing the "loops back to Google" symptom.
 *
 * <p><b>Actual flow:</b><br>
 * {@code libgrowtopia.so} calls {@link WebViewManager#LoadURLPost} with the Google OAuth URL
 * <em>before</em> calling {@code SignIn()}.  {@code LoadURLPost} opens the WebView and POSTs
 * to {@code accounts.google.com} — the OAuth flow is already running by the time
 * {@code SignIn()} arrives.  When Google authentication completes, Growtopia's server redirects
 * to {@code grow://growtopia?token=...}.  {@link WebViewManager.WebViewClientImpl#interceptUrl}
 * catches this redirect <em>inside the WebView</em> and calls
 * {@code nativeOnScriptCall("nativeSignIn", token)} directly — without dispatching any Android
 * Intent, and without any risk of the official app intercepting the token.
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

    // ── Entry point called by the native engine ────────────────────────────────────────
    /**
     * Ensures the in-app WebView Google OAuth flow is running — no Chrome, no Android SDK.
     *
     * <p><b>Normal path (engine already opened the WebView):</b><br>
     * {@code libgrowtopia.so} always calls {@link WebViewManager#LoadURLPost} with the Google
     * OAuth URL <em>before</em> it calls {@code SignIn()} on the Java side. {@code LoadURLPost}
     * shows the WebView and begins POSTing to {@code accounts.google.com}. When {@code SignIn()}
     * arrives, the WebView is already visible and the OAuth flow is in progress — we simply
     * confirm this and let it continue. {@link WebViewManager.WebViewClientImpl#interceptUrl}
     * catches the trailing {@code grow://growtopia?token=…} redirect internally, so the token
     * is delivered to the engine without ever becoming an Android Intent (which the official
     * Growtopia app, if installed, would otherwise intercept).
     *
     * <p><b>Fallback path (WebView not yet visible):</b><br>
     * If, for any reason, the WebView is not showing when {@code SignIn()} fires (e.g. ltoken
     * spoof consumed the call, or a timing edge case), {@link ZennKuyBridge#startResolving()}
     * replays the stored OAuth URL through the WebView — or injects a cached ltoken directly
     * if one is available.
     *
     * <p><b>Why not Chrome?</b><br>
     * Opening Chrome via {@code ACTION_VIEW} means the final {@code grow://} redirect leaves
     * the app and is dispatched as an Android Intent. If the official Growtopia app is installed
     * and also registered for the {@code grow://} scheme, Android routes the token to it instead
     * of V3 — silently swallowing the login. The in-app WebView keeps the entire OAuth chain
     * inside our process.
     */
    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }

        mainActivity.runOnUiThread(() -> {
            WebViewManager wvm = Main.mainApp.webViewManager;

            if (wvm != null && wvm.IsVisible()) {
                // Normal path: LoadURLPost already opened the WebView with the Google OAuth page.
                // The grow:// callback is intercepted in WebViewClientImpl — nothing to do here.
                Log.d(TAG, "SignIn: WebView already visible (LoadURLPost ran first) — OAuth in progress");
                spoof.setGoogleLogs("WebView Google OAuth in progress");
                return;
            }

            // Fallback: WebView not visible — either ltoken spoof fired, or the engine called
            // SignIn() before LoadURLPost (unusual).  ZennKuyBridge handles both cases:
            // if an ltoken is stored it injects it immediately; otherwise it replays the
            // stored OAuth URL through the WebView.
            Log.d(TAG, "SignIn: WebView not visible — delegating to ZennKuyBridge");
            spoof.setGoogleLogs("Triggering WebView Google OAuth via ZennKuyBridge");
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

    // ── Token delivery on the GL thread ──────────────────────────────────────
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
