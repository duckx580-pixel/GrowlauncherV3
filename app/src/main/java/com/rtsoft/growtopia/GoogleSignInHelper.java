package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";

    Activity mainActivity;
    private final LoginSpoof spoof;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
        this.spoof = new LoginSpoof(activity);
    }

    public native void OnSignIn(int code, String token);

    /** Implemented in libzennkuy.so — opens Chrome AccountChooser. Never GMS. */
    public native void SignIn();

    public void Init() {}
    public void SignOut() {}

    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        /* Real Growlauncher ignores RC 1 (GMS / Chrome). Token arrives via grow:// */
    }

    private static final int MAX_DELIVER_RETRIES = 40;
    private final Handler deliverHandler = new Handler(Looper.getMainLooper());

    public void deliverResult(int code, String token) {
        Log.d(TAG, "deliverResult code=" + code + " len=" + (token == null ? 0 : token.length()));
        if (spoof != null) {
            if (code == 0 && token != null && !token.isEmpty())
                spoof.setGoogleLogs("Google OK token=" + token.length());
            else
                spoof.setGoogleLogs("Google fail code=" + code);
        }
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
            try { OnSignIn(code, token); } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "OnSignIn missing in growtopia so: " + e.getMessage());
            }
        });
    }
}
