package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Google Sign-In — Chrome OAuth path (same as Real Growlauncher v5.57).
 */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";

    static final int RC_GOOGLE_SIGNIN = 9001;

    Activity mainActivity;
    private final LoginSpoof spoof;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    public native void OnSignIn(int code, String token);
    public void Init() {}
    public void SignOut() {}

    public void SignIn() {
        if (Main.mainApp == null) {
            Log.e(TAG, "SignIn: mainApp is null");
            return;
        }
        mainActivity.runOnUiThread(() -> {
            Log.d(TAG, "SignIn: opening Chrome account picker");
            spoof.setGoogleLogs("SignIn: Chrome account picker");
            ZennKuyBridge.openChrome(mainActivity, ZennKuyBridge.DASHBOARD_URL);
        });
    }

    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_GOOGLE_SIGNIN) return;
        Log.w(TAG, "handleSignInResult: unexpected RC_GOOGLE_SIGNIN result — ignored");
    }

    private static final int MAX_DELIVER_RETRIES = 40;
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
