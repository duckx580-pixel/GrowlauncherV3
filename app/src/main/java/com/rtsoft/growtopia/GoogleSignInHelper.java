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
 * Google Sign-In via the standard Play Services SDK.
 *
 * Requests an ID token directly ({@code requestIdToken(CLIENT_ID)}) using the
 * Google account picker Play Services provides natively. This is the same
 * client id and the same API both the official Growtopia app and the
 * reference mod build use — no WebView involved, so there is no GPU
 * contention with the game's GLSurfaceView (the previous WebView-based
 * fallback crashed ~2s into the sign-in page load on some devices/emulators
 * because the game's GL context and the WebView's Chromium renderer both
 * fought for the same GPU).
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    static final int RC_GOOGLE_SIGNIN = 9001;

    Activity mainActivity;
    private GoogleSignInClient client;

    // Records the captured Google token and status for the Login Spoof menu
    // (google_token / google_logs) and stores device-identity / ltoken values.
    private final LoginSpoof spoof;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {
        if (client != null) client.signOut();
    }

    // ── Entry point called by the native engine ────────────────────────────
    public void SignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(CLIENT_ID)
                .requestEmail()
                .build();
        client = GoogleSignIn.getClient(mainActivity, gso);

        // Force the account chooser so a stale/single cached session can't
        // silently return a token for the wrong account.
        if (GoogleSignIn.getLastSignedInAccount(mainActivity) != null) {
            client.signOut();
        }
        mainActivity.startActivityForResult(client.getSignInIntent(), RC_GOOGLE_SIGNIN);
    }

    // ── onActivityResult dispatcher (called from Main.onActivityResult) ────
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_GOOGLE_SIGNIN) return;
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            Log.d(TAG, "Google sign-in OK, idToken length=" + (idToken != null ? idToken.length() : 0));
            deliverResult(0, idToken != null ? idToken : "");
        } catch (ApiException e) {
            if (e.getStatusCode() == 12501) {
                Log.d(TAG, "Google sign-in cancelled by user");
                deliverResult(-1, "");
            } else {
                Log.e(TAG, "Google sign-in failed: " + e);
                // Error 10 = DEVELOPER_ERROR (SHA-1 mismatch on debug builds).
                // libzennkuy.so does NOT have libpowerkuy's built-in WebView fallback,
                // so Java must trigger it. Mirror what v5.57's libpowerkuy does on
                // Error 10: automatically replay the stored Growtopia login URL via
                // WebView instead of leaving the user stuck on "Getting server address…"
                if (e.getStatusCode() == 10) {
                    Log.d(TAG, "Error 10 detected — auto-triggering WebView login fallback");
                    if (Main.mainApp != null) {
                        Main.mainApp.runOnUiThread(ZennKuyBridge::startResolving);
                    }
                }
                deliverResult(e.getStatusCode(), "");
            }
        }
    }

    // ── Token delivery on the GL thread ───────────────────────────────────
    private void deliverResult(int code, String token) {
        // Record what happened for the Login Spoof "Google" menu section.
        if (code == 0 && token != null && !token.isEmpty()) {
            spoof.setGoogleToken(token);
            spoof.setGoogleLogs("Google sign-in OK (token length=" + token.length() + ")");
        } else {
            spoof.setGoogleLogs("Google sign-in failed (code=" + code + ")");
        }
        deliverToGl(code, token, 0);
    }

    // The GL view may not exist yet when the sign-in activity returns (the
    // engine tears it down while another activity is on top). Retry briefly so
    // native always receives OnSignIn instead of the login screen hanging on
    // "Getting server address..." forever.
    private static final int MAX_DELIVER_RETRIES = 40; // ~4s at 100ms
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

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
        final int c = code;
        final String t = token;
        glView.queueEvent(() -> {
            try {
                OnSignIn(c, t);
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "OnSignIn native unavailable: " + e.getMessage());
            }
        });
    }
}
