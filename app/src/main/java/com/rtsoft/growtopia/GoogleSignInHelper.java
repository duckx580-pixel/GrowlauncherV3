package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;

/** Same shape as Real Growlauncher v5.57 GoogleSignInHelper. */
public class GoogleSignInHelper {
    private static final String TAG = "GoogleSignInHelper";
    static final String CLIENT_ID =
        "389994132396-4s6ol46f60831v5blfpci7lnmsdnh8br.apps.googleusercontent.com";
    static final int RC_SIGN_IN = 1; // Real uses 1, same as openAsResult

    Activity mainActivity;

    public GoogleSignInHelper(Activity activity) {
        this.mainActivity = activity;
    }

    public native void OnSignIn(int code, String token);

    public void Init() {}

    public void SignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(CLIENT_ID)
            .requestEmail()
            .build();
        GoogleSignInClient client = GoogleSignIn.getClient(mainActivity, gso);
        mainActivity.startActivityForResult(client.getSignInIntent(), RC_SIGN_IN);
        Log.d(TAG, "SignIn: GMS intent RC=1 (Real path)");
    }

    public void handleSignInResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != RC_SIGN_IN) return;
        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
        handleSignInResult(task);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> task) {
        try {
            GoogleSignInAccount acct = task.getResult(ApiException.class);
            String token = acct.getIdToken();
            Log.d(TAG, "Token = " + token);
            OnSignIn(0, token != null ? token : "");
        } catch (ApiException e) {
            int code = e.getStatusCode();
            if (code == 12501) {
                Log.e(TAG, "signInResult: canceled by user");
                OnSignIn(-1, "");
            } else {
                Log.e(TAG, "signInResult: failed by reason: " + e);
                OnSignIn(code, "");
            }
        }
    }
}
