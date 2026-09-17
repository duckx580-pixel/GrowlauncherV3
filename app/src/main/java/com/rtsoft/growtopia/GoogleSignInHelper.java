package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

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
        Log.d(TAG, "SignIn: forcing Chrome Google AccountChooser");
        if (spoof != null) spoof.setGoogleLogs("SignIn: Chrome AccountChooser");
        ZennKuyBridge.openGoogleAccountPicker();
    }

    public void handleSignInResult(int requestCode, int resultCode, Intent data) {}

    private static final int MAX_DELIVER_RETRIES = 40;
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

    public void deliverResult(int code, String token) {
        deliverToGl(code, token, 0);
    }

    private void deliverToGl(int code, String token, int attempt) {
        GLSurfaceView glView = SharedActivity.mGLView;
        if (glView == null) {
            if (attempt >= MAX_DELIVER_RETRIES) return;
            deliverHandler.postDelayed(() -> deliverToGl(code, token, attempt + 1), 100);
            return;
        }
        glView.queueEvent(() -> {
            try { OnSignIn(code, token); } catch (UnsatisfiedLinkError ignored) {}
        });
    }
}
