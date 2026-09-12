package com.rtsoft.growtopia;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;

import java.net.URLEncoder;
import java.util.UUID;

/**
 * Google Sign-In without needing com.gentz.launcher registered in Ubisoft's
 * Google Cloud project.
 *
 * Primary path  – Android AccountManager with "audience:server:client_id:"
 *   scope.  Returns a real OpenID Connect ID token via Google Play Services.
 *   No SHA-1 / package registration needed.
 *
 * Fallback path – WebView using response_type=id_token (implicit grant).
 *   The ID token lands in the redirect URL fragment (#id_token=...) and is
 *   extracted before the Growtopia server page ever loads.  This avoids the
 *   "wrong token type" freeze caused by the previous code/flow approach which
 *   got a Growtopia session token instead of a Google ID token.
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";
    private static final String REDIRECT_URI =
        "https://login.growtopiagame.com/google/callback";

    // Request codes – must stay in sync with Main.onActivityResult
    static final int RC_ACCOUNT_PICKER = 9001;
    static final int RC_AUTH_CONSENT   = 9002;
    static final int RC_GOOGLE_WEB     = 9003;

    Activity mainActivity;

    // Saved from RC_ACCOUNT_PICKER so RC_AUTH_CONSENT can retry fetchIdToken
    // even when the consent activity doesn't return KEY_ACCOUNT_NAME.
    private String pendingAccountName = null;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
    }

    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {}

    // ── Entry point called by the native engine ────────────────────────────
    public void SignIn() {
        // Skip AccountManager: the 'audience:server:client_id' token it returns
        // uses the old iss="accounts.google.com" format that Growtopia's server
        // rejects with Error: 10.  The WebView implicit flow (response_type=id_token)
        // produces a modern JWT (iss="https://accounts.google.com") that the server
        // accepts, same as what the real Google Sign-In SDK returns.
        startWebSignIn();
    }

    // ── AccountManager token fetch ─────────────────────────────────────────
    private void fetchIdToken(String accountName) {
        pendingAccountName = accountName;
        AccountManager am = AccountManager.get(mainActivity);
        Account account = new Account(accountName, "com.google");

        am.getAuthToken(
            account,
            "audience:server:client_id:" + CLIENT_ID,
            Bundle.EMPTY,
            mainActivity,
            future -> {
                try {
                    Bundle result = future.getResult();
                    String token = result.getString(AccountManager.KEY_AUTHTOKEN);
                    if (token != null && !token.isEmpty()) {
                        Log.d(TAG, "AccountManager returned Google ID token");
                        deliverResult(0, token);
                    } else {
                        Intent authIntent = result.getParcelable(AccountManager.KEY_INTENT);
                        if (authIntent != null) {
                            mainActivity.runOnUiThread(() ->
                                mainActivity.startActivityForResult(authIntent, RC_AUTH_CONSENT));
                        } else {
                            Log.w(TAG, "No token and no consent intent – falling back to WebView");
                            startWebSignIn();
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "getAuthToken failed (" + e.getMessage() + ") – falling back to WebView");
                    startWebSignIn();
                }
            },
            null
        );
    }

    // ── WebView fallback – uses response_type=id_token ────────────────────
    // The ID token is returned in the URL fragment (#id_token=...) so we
    // never need to process the Growtopia server's response body.  The engine
    // needs a real Google ID token, not a Growtopia session token.
    private void startWebSignIn() {
        try {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            String state = UUID.randomUUID().toString().replace("-", "");
            String url = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + CLIENT_ID
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "utf-8")
                + "&response_type=id_token"
                + "&scope=" + URLEncoder.encode("openid profile email", "utf-8")
                + "&nonce=" + nonce
                + "&state=" + state;
            Intent intent = new Intent(mainActivity, GoogleWebSignInActivity.class);
            intent.putExtra("url", url);
            mainActivity.startActivityForResult(intent, RC_GOOGLE_WEB);
        } catch (Exception e) {
            Log.e(TAG, "WebView sign-in start failed: " + e.getMessage());
            deliverResult(-1, "");
        }
    }

    // ── onActivityResult dispatcher (called from Main.onActivityResult) ────
    public void handleSignInResult(int requestCode, int resultCode, Intent data) {

        if (requestCode == RC_ACCOUNT_PICKER) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                if (name != null && !name.isEmpty()) {
                    Log.d(TAG, "Account picked: " + name);
                    fetchIdToken(name);
                    return;
                }
            }
            Log.d(TAG, "Account picker cancelled – trying WebView");
            startWebSignIn();
            return;
        }

        if (requestCode == RC_AUTH_CONSENT) {
            if (resultCode == Activity.RESULT_OK) {
                // Prefer the account name we saved from RC_ACCOUNT_PICKER – the
                // consent activity often does NOT return KEY_ACCOUNT_NAME, so
                // relying on it caused a silent fall-through to WebView.
                String name = null;
                if (data != null) {
                    name = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                }
                if ((name == null || name.isEmpty()) && pendingAccountName != null) {
                    name = pendingAccountName;
                }
                if (name != null && !name.isEmpty()) {
                    Log.d(TAG, "Consent granted – retrying fetchIdToken for: " + name);
                    fetchIdToken(name);
                    return;
                }
            }
            Log.d(TAG, "Auth consent denied – trying WebView");
            startWebSignIn();
            return;
        }

        if (requestCode == RC_GOOGLE_WEB) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String token = data.getStringExtra(GoogleWebSignInActivity.EXTRA_TOKEN);
                Log.d(TAG, "WebView sign-in token length=" + (token != null ? token.length() : 0));
                deliverResult(0, token != null ? token : "");
            } else {
                Log.d(TAG, "WebView sign-in cancelled or failed");
                deliverResult(-1, "");
            }
        }
    }

    // ── Token delivery on the GL thread ───────────────────────────────────
    private void deliverResult(int code, String token) {
        GLSurfaceView glView = SharedActivity.mGLView;
        if (glView != null) {
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
}
