package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Google Sign-In — Chrome OAuth path (same as Real Growlauncher v5.57).
 *
 * <p><b>Flow:</b><br>
 * {@link #SignIn()} → {@link ZennKuyBridge#startResolving()} loads the Growtopia
 * dashboard URL in the in-app WebView → login page JS calls
 * {@code NativeApp.openAsResult(googleOAuthUrl)} → Chrome opens at
 * {@code accounts.google.com} ("to continue to growtopiagame.com") → user picks
 * account → Chrome redirects to {@code grow://} → {@link Main#onNewIntent} →
 * {@link Main#handleIntent} extracts info+token → {@link com.rtsoft.growtopia.NativeAppInterface#OnDeepLinkProcess}
 * delivers to engine.
 *
 * <p><b>Why no Error 10?</b><br>
 * The Android Google Sign-In SDK's {@code getSignInIntent()} always performs
 * an Android OAuth client SHA-1 fingerprint check, regardless of the
 * {@code requestIdToken()} client ID type. Debug-signed APKs have a different
 * SHA-1 than the release key registered for {@code com.gentz.launcher} →
 * {@code DEVELOPER_ERROR} (code 10) every time.
 * Chrome's OAuth flow uses the web-type client ID
 * ({@code 389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br}) which has no
 * SHA-1 requirement — any signing key works.
 *
 * <p><b>Real Growlauncher comparison:</b><br>
 * Real Growlauncher's {@code SignIn()} also calls {@code startActivityForResult}
 * with the SDK intent but drops the result in {@code onActivityResult}
 * (only calls {@code super}). Their actual token delivery is the same
 * Chrome → {@code grow://} redirect path described above.
 * V3 skips the SDK picker entirely and goes straight to that path.
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    /**
     * Growtopia's web-type OAuth client ID.
     * Used by Real Growlauncher v5.57 and Genta Hax.
     * Web clients have no SHA-1 requirement — safe on any signing key.
     */
    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    /** Kept for reference; RC 9001 is not launched by this class. */
    static final int RC_GOOGLE_SIGNIN = 9001;

    Activity mainActivity;
    private final LoginSpoof spoof;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    /** Native method in libzennkuy.so — delivers sign-in result to the game engine. */
    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {}

    // ── Entry point called by the native engine ───────────────────────────────────────────
    /**
     * Starts the Chrome OAuth flow — identical to Real Growlauncher v5.57's working path.
     *
     * <p>Calls {@link ZennKuyBridge#startResolving()} which loads the Growtopia
     * dashboard URL in the in-app WebView. The login page JS then calls
     * {@code NativeApp.openAsResult(googleOAuthUrl)}, which fires
     * {@code startActivityForResult(ACTION_VIEW, googleOAuthUrl, 1)} opening Chrome.
     * Chrome handles the full OAuth flow and redirects to {@code grow://} on completion.
     *
     * <p>The Android SDK account picker ({@code GoogleSignIn.getClient().getSignInIntent()})
     * is intentionally NOT used here — it triggers a SHA-1 fingerprint check that
     * always fails on debug-signed APKs, producing Error 10.
     */
    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }
        mainActivity.runOnUiThread(() -> {
            Log.d(TAG, "SignIn: starting Chrome OAuth path via dashboard");
            spoof.setGoogleLogs("SignIn: loading dashboard → Chrome OAuth (Real Growlauncher path)");
            ZennKuyBridge.startResolving();
        });
    }

    // ── onActivityResult stub (RC 9001 is never launched) ────────────────────────────────
    /**
     * No-op stub kept for compatibility with {@link Main#onActivityResult}.
     * RC 9001 is never fired because {@link #SignIn()} does not launch the SDK picker.
     * The Chrome OAuth result arrives via {@code onNewIntent} → {@code handleIntent},
     * not through {@code onActivityResult}.
     */
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_GOOGLE_SIGNIN) return;
        // SDK picker not launched — this is never reached in normal operation.
        Log.w(TAG, "handleSignInResult: unexpected RC_GOOGLE_SIGNIN result — ignored");
    }

    // ── Token delivery on the GL thread ─────────────────────────────────────────────────
    private static final int MAX_DELIVER_RETRIES = 40; // ~4s at 100ms
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

    /**
     * Delivers a sign-in result to the engine on the GL thread.
     * Called externally if a non-SDK path needs to report a result via OnSignIn.
     */
    public void deliverResult(int code, String token) {
        if (code == 0 && token != null && !token.isEmpty()) {
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
