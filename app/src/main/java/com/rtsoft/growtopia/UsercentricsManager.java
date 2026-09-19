package com.rtsoft.growtopia;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.usercentrics.sdk.UsercentricsConsentHistoryEntry;
import com.usercentrics.sdk.UsercentricsServiceConsent;
import com.usercentrics.sdk.models.settings.UsercentricsConsentType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class UsercentricsManager {
    private static final String TAG = "UsercentricsManager";

    private Activity baseContext;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private volatile boolean consentDelivered = false;

    public UsercentricsManager(Activity activity) {
        this.baseContext = activity;
    }

    public native void InitFinish(boolean success);
    public native void OnConsentFetchedFail(int code, String message);
    public native void OnConsentFetchedSuccess(List<UsercentricsServiceConsent> list);

    private List<UsercentricsServiceConsent> buildAcceptedConsentList() {
        UsercentricsConsentHistoryEntry historyEntry = new UsercentricsConsentHistoryEntry(
                true, UsercentricsConsentType.EXPLICIT, System.currentTimeMillis());
        List<UsercentricsConsentHistoryEntry> history = new ArrayList<>();
        history.add(historyEntry);

        UsercentricsServiceConsent consent = new UsercentricsServiceConsent(
                "growtopia",
                true,
                history,
                UsercentricsConsentType.EXPLICIT,
                "Ubisoft",
                "1.0",
                true,
                "Essential"
        );
        return Collections.singletonList(consent);
    }

    private void deliverAccepted() {
        List<UsercentricsServiceConsent> consents = buildAcceptedConsentList();
        try {
            InitFinish(true);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "InitFinish unavailable: " + e.getMessage());
        }
        try {
            OnConsentFetchedSuccess(consents);
        } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "OnConsentFetchedSuccess unavailable: " + e.getMessage());
        }
    }

    private void scheduleDeliveries() {
        long[] delays = new long[] { 0L, 200L, 800L, 2000L };
        for (long d : delays) {
            uiHandler.postDelayed(() -> {
                Log.d(TAG, "deliverAccepted delayMs=" + d);
                deliverAccepted();
            }, d);
        }
    }

    public void InitWithRuleSet(String str) {
        Log.d(TAG, "InitWithRuleSet ruleSetId=" + str);
        consentDelivered = false;
        scheduleDeliveries();
    }

    public void InitWithSettings(String str) {
        Log.d(TAG, "InitWithSettings settingsId=" + str);
        consentDelivered = false;
        scheduleDeliveries();
    }

    public void CheckConsentState() {
        Log.d(TAG, "CheckConsentState");
        consentDelivered = true;
        uiHandler.post(this::deliverAccepted);
    }

    public void FetchUserConsent(List<UsercentricsServiceConsent> list) {
        uiHandler.post(this::deliverAccepted);
    }

    public void RequestConsentSettings() {
        CheckConsentState();
    }

    public void ShowConsentSettings() {
        CheckConsentState();
    }
}
