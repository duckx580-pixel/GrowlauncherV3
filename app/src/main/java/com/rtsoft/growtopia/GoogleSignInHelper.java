package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

/**
 * Google Sign-In — Android SDK path (same as Real Growlauncher v5.57 and Genta Hax).
 *
 * <p><b>Why no Error 10?</b><br>
 * Error 10 ({@code DEVELOPER_ERROR}) only fires when using an <em>Android-type</em>
 * OAuth client that has a SHA-1 fingerprint registered. The client ID
 * {@code 389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br} is Growtopia’s
 * <em>web-type</em> OAuth client — it has no SHA-1 requirement. Real Growlauncher
 * uses it with package name {@code com.gentz.launcher} (not {@code com.rtsoft.growtopia})
 * and it works, which confirms no fingerprint check. Any signing key works.
 *
 * <p><b>Flow:</b><br>
 * {@link #SignIn()} → Android SDK account picker → user picks account →
 * {@link Main#onActivityResult} → {@link #handleSignInResult} extracts Google
 * ID token → {@link #OnSignIn(int, String)} delivers to engine via GL thread.
 *
 * <p>The secondary path ({@link ZennKuyBridge#startResolving()}) is kept as a
 * fallback if the SDK throws unexpectedly. The primary WebView path
 * ({@code LoadURLPost} → {@code openAsResult} → Chrome → {@code grow://} redirect
 * → {@link Main#handleIntent}) also continues to work independently.
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    /**
     * Growtopia’s web-type OAuth client ID.
     * Web clients do not require SHA-1 fingerprint registration — no Error 10.
     * Same client ID used by Real Growlauncher v5.57 and Genta Hax.
     */
    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    /** Request code passed to startActivityForResult for the SDK account picker. */
    static final int RC_GOOGLE_SIGNIN = 9001;

    Activity mainActivity;
    private final LoginSpoof spoof;
    private GoogleSignInClient googleSignInClient;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    /** Native method in libzennkuy.so — delivers sign-in result to the game engine. */
    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {}

    // ── Entry point called by the native engine ──────────────────────────────────────────────
    /**
     * Starts the Google Sign-In SDK flow — same approach as Real Growlauncher v5.57.
     *
     * <p>Builds a {@link GoogleSignInOptions} with {@code requestIdToken} using
     * Growtopia’s web client ID, then launches the native Google account picker
     * via {@code startActivityForResult}. The result arrives at
     * {@link Main#onActivityResult} and is dispatched to
     * {@link #handleSignInResult(int, int, Intent)}.
     *
     * <p>If the SDK throws (device has no Google Play Services, or another
     * unexpected error), falls back to {@link ZennKuyBridge#startResolving()}
     * which loads the dashboard URL in the in-app WebView.
     */
    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }
        mainActivity.runOnUiThread(() -> {
            try {
                GoogleSignInOptions options = new GoogleSignInOptions
                        .Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(CLIENT_ID)
                        .requestEmail()
                        .build();
                googleSignInClient = GoogleSignIn.getClient(mainActivity, options);
                Intent signInIntent = googleSignInClient.getSignInIntent();
                Log.d(TAG, "SignIn: launching Google account picker via SDK");
                spoof.setGoogleLogs("SDK sign-in started — account picker shown");
                mainActivity.startActivityForResult(signInIntent, RC_GOOGLE_SIGNIN);
            } catch (Exception e) {
                Log.e(TAG, "SignIn: SDK error: " + e.getMessage());
                spoof.setGoogleLogs("SDK failed (" + e.getMessage() + "), fallback to dashboard");
                // Fallback: load the Growtopia dashboard URL in the in-app WebView.
                // The dashboard page calls NativeApp.nativeSignIn(token) from JS
                // after Google auth, same mechanism as Genta Hax “Start Resolving”.
                ZennKuyBridge.startResolving();
            }
        });
    }

    // ── onActivityResult dispatcher (called from Main.onActivityResult) ───────────────────
    /**
     * Processes the Google Sign-In SDK result.
     *
     * <p>Real Growlauncher calls {@code super.onActivityResult()} and never
     * dispatches to this method — the SDK result is silently dropped on their side.
     * V3 improves on this by actually extracting the Google ID token and delivering
     * it to the engine via {@link #OnSignIn(int, String)}.
     *
     * <p>Only handles {@link #RC_GOOGLE_SIGNIN} (9001). Request code 1 is used by
     * {@code openAsResult} (Chrome browser OAuth) and is handled separately via
     * {@code onNewIntent} → {@code handleIntent}.
     */
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_GOOGLE_SIGNIN) return;
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            Log.d(TAG, "handleSignInResult: success — idToken len=" + (idToken != null ? idToken.length() : 0));
            if (idToken != null && !idToken.isEmpty()) {
                spoof.setGoogleToken(idToken);
                spoof.setGoogleLogs("SDK sign-in OK (idToken len=" + idToken.length() + ")");
                deliverResult(0, idToken);
            } else {
                Log.w(TAG, "handleSignInResult: idToken is null or empty");
                spoof.setGoogleLogs("SDK sign-in: idToken was null");
                deliverResult(-1, "");
            }
        } catch (ApiException e) {
            Log.w(TAG, "handleSignInResult: sign-in failed, status=" + e.getStatusCode());
            spoof.setGoogleLogs("SDK sign-in failed (status=" + e.getStatusCode() + ")");
            // status 10 = DEVELOPER_ERROR; status 12501 = user cancelled
            deliverResult(e.getStatusCode(), "");
        } catch (Exception e) {
            Log.e(TAG, "handleSignInResult: unexpected error: " + e.getMessage());
            deliverResult(-1, "");
        }
    }

    // ── Token delivery on the GL thread ──────────────────────────────────────────────────────
    private static final int MAX_DELIVER_RETRIES = 40; // ~4s at 100ms
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

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
