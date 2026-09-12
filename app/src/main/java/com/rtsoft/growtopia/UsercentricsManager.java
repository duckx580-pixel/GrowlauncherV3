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

    // Native callbacks — called from the UI thread (matches real 5.55 implementation)
    public native void InitFinish(boolean success);
    public native void OnConsentFetchedFail(int code, String message);
    public native void OnConsentFetchedSuccess(List<UsercentricsServiceConsent> list);

    private List<UsercentricsServiceConsent> buildAcceptedConsentList() {
        UsercentricsConsentHistoryEntry historyEntry = new UsercentricsConsentHistoryEntry(
                true, UsercentricsConsentType.EXPLICIT, System.currentTimeMillis());
        List<UsercentricsConsentHistoryEntry> history = new ArrayList<>();
        history.add(historyEntry);

        UsercentricsServiceConsent consent = new UsercentricsServiceConsent(
                "growtopia",   // templateId
                true,          // status = accepted
                history,
                UsercentricsConsentType.EXPLICIT,
                "Ubisoft",     // dataProcessor
                "1.0",         // version
                true,          // isEssential
                "Essential"    // category
        );
        return Collections.singletonList(consent);
    }

    public void InitWithRuleSet(String str) {
        Log.d(TAG, "InitWithRuleSet called, ruleSetId=" + str);
        uiHandler.post(() -> {
            Log.d(TAG, "InitWithRuleSet -> calling InitFinish(true)");
            try { InitFinish(true); } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "InitFinish unavailable: " + e.getMessage());
            }
        });
    }

    public void InitWithSettings(String str) {
        Log.d(TAG, "InitWithSettings called, settingsId=" + str);
        uiHandler.post(() -> {
            Log.d(TAG, "InitWithSettings -> calling InitFinish(true)");
            try { InitFinish(true); } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "InitFinish unavailable: " + e.getMessage());
            }
        });
    }

    public void CheckConsentState() {
        Log.d(TAG, "CheckConsentState called");
        if (consentDelivered) {
            Log.d(TAG, "CheckConsentState: consent already delivered, skipping duplicate call");
            return;
        }
        consentDelivered = true;
        List<UsercentricsServiceConsent> consents = buildAcceptedConsentList();
        uiHandler.post(() -> {
            Log.d(TAG, "CheckConsentState -> calling OnConsentFetchedSuccess");
            try { OnConsentFetchedSuccess(consents); } catch (UnsatisfiedLinkError e) {
                Log.w(TAG, "OnConsentFetchedSuccess unavailable: " + e.getMessage());
            }
        });
    }

    public void FetchUserConsent(List<UsercentricsServiceConsent> list) {
        Log.d(TAG, "FetchUserConsent called, list=" + (list == null ? "null" : "size=" + list.size()));
        List<UsercentricsServiceConsent> consents =
                (list != null && !list.isEmpty()) ? list : buildAcceptedConsentList();
        Log.d(TAG, "FetchUserConsent -> calling OnConsentFetchedSuccess with " + consents.size() + " entries");
        try { OnConsentFetchedSuccess(consents); } catch (UnsatisfiedLinkError e) {
            Log.w(TAG, "OnConsentFetchedSuccess unavailable: " + e.getMessage());
        }
    }

    public void RequestConsentSettings() {
        Log.d(TAG, "RequestConsentSettings called -> delegating to CheckConsentState");
        CheckConsentState();
    }

    public void ShowConsentSettings() {
        Log.d(TAG, "ShowConsentSettings called -> delegating to CheckConsentState");
        CheckConsentState();
    }
}
