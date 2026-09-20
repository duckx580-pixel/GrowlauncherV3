package com.rtsoft.growtopia;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.usercentrics.sdk.Usercentrics;
import com.usercentrics.sdk.UsercentricsOptions;
import com.usercentrics.sdk.UsercentricsServiceConsent;
import com.usercentrics.sdk.models.settings.UsercentricsReadyStatus;

import java.util.List;

public class UsercentricsManager {
    private static final String TAG = "UsercentricsManager";

    private Activity baseContext;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public UsercentricsManager(Activity activity) {
        this.baseContext = activity;
    }

    public native void InitFinish(boolean success);
    public native void OnConsentFetchedFail(int code, String message);
    public native void OnConsentFetchedSuccess(List<UsercentricsServiceConsent> list);

    private void initUsercentrics(UsercentricsOptions options) {
        Log.d(TAG, "Initializing Usercentrics SDK");
        try {
            Usercentrics.initialize(baseContext, options, status -> {
                Log.d(TAG, "Usercentrics initialized with status: " + status);
                if (status == UsercentricsReadyStatus.READY) {
                    checkReadyState();
                } else {
                    Log.w(TAG, "Usercentrics not ready after initialization");
                    reportInitSuccess();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Usercentrics: " + e.getMessage());
            reportInitSuccess();
        }
    }

    private void checkReadyState() {
        baseContext.runOnUiThread(() -> {
            try {
                Usercentrics.isReady(status -> {
                    Log.d(TAG, "Usercentrics ready check returned: " + status);
                    if (status == UsercentricsReadyStatus.READY) {
                        List<UsercentricsServiceConsent> consents = Usercentrics.getConsents();
                        if (consents != null) {
                            OnConsentFetchedSuccess(consents);
                        } else {
                            Log.w(TAG, "No consents available from SDK");
                            reportInitSuccess();
                        }
                    } else {
                        reportInitSuccess();
                    }
                });
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "isReady unavailable: " + e.getMessage());
                reportInitSuccess();
            } catch (Exception e) {
                Log.e(TAG, "Error checking ready state: " + e.getMessage());
                reportInitSuccess();
            }
        });
    }

    private void reportInitSuccess() {
        try {
            InitFinish(true);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "InitFinish unavailable: " + e.getMessage());
        }
    }

    public void InitWithRuleSet(String ruleSetId) {
        Log.d(TAG, "InitWithRuleSet ruleSetId=" + ruleSetId);
        UsercentricsOptions options = new UsercentricsOptions();
        options.setRuleSetId(ruleSetId);
        initUsercentrics(options);
    }

    public void InitWithSettings(String settingsId) {
        Log.d(TAG, "InitWithSettings settingsId=" + settingsId);
        UsercentricsOptions options = new UsercentricsOptions();
        options.setSettingsId(settingsId);
        initUsercentrics(options);
    }

    public void CheckConsentState() {
        Log.d(TAG, "CheckConsentState");
        baseContext.runOnUiThread(() -> {
            try {
                Usercentrics.isReady(status -> {
                    Log.d(TAG, "CheckConsentState - isReady returned: " + status);
                    if (status == UsercentricsReadyStatus.READY) {
                        List<UsercentricsServiceConsent> consents = Usercentrics.getConsents();
                        if (consents != null) {
                            OnConsentFetchedSuccess(consents);
                        } else {
                            Log.w(TAG, "No consents in CheckConsentState");
                            reportInitSuccess();
                        }
                    }
                });
            } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "isReady unavailable in CheckConsentState: " + e.getMessage());
            } catch (Exception e) {
                Log.e(TAG, "Error in CheckConsentState: " + e.getMessage());
            }
        });
    }

    public void FetchUserConsent(List<UsercentricsServiceConsent> list) {
        Log.d(TAG, "FetchUserConsent called with " + (list != null ? list.size() : 0) + " consents");
        try {
            OnConsentFetchedSuccess(list);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "OnConsentFetchedSuccess unavailable: " + e.getMessage());
        }
    }

    public void RequestConsentSettings() {
        Log.d(TAG, "RequestConsentSettings");
        CheckConsentState();
    }

    public void ShowConsentSettings() {
        Log.d(TAG, "ShowConsentSettings");
        CheckConsentState();
    }
}
