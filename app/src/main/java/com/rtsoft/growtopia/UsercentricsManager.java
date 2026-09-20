package com.rtsoft.growtopia;

import android.app.Activity;
import com.usercentrics.sdk.Usercentrics;
import com.usercentrics.sdk.UsercentricsOptions;
import com.usercentrics.sdk.UsercentricsServiceConsent;
import java.util.List;
import kotlin.Unit;

public class UsercentricsManager {
    private final Activity baseContext;

    public UsercentricsManager(Activity activity) {
        this.baseContext = activity;
    }

    private void initUsercentrics(UsercentricsOptions usercentricsOptions) {
        Usercentrics.initialize(baseContext, usercentricsOptions);
        baseContext.runOnUiThread(() -> Usercentrics.isReady(
            status -> {
                InitFinish(true);
                return Unit.INSTANCE;
            },
            throwable -> {
                InitFinish(false);
                return Unit.INSTANCE;
            }
        ));
    }

    public void CheckConsentState() {
        baseContext.runOnUiThread(() -> Usercentrics.isReady(
            status -> {
                FetchUserConsent(status.getConsents());
                return Unit.INSTANCE;
            },
            throwable -> {
                OnConsentFetchedFail(-1, throwable.getLocalizedMessage());
                return Unit.INSTANCE;
            }
        ));
    }

    public void FetchUserConsent(List<UsercentricsServiceConsent> list) {
        OnConsentFetchedSuccess(list);
    }

    public native void InitFinish(boolean z3);

    public void InitWithRuleSet(String str) {
        UsercentricsOptions usercentricsOptions = new UsercentricsOptions();
        usercentricsOptions.setRuleSetId(str);
        initUsercentrics(usercentricsOptions);
    }

    public void InitWithSettings(String str) {
        UsercentricsOptions usercentricsOptions = new UsercentricsOptions();
        usercentricsOptions.setSettingsId(str);
        initUsercentrics(usercentricsOptions);
    }

    public native void OnConsentFetchedFail(int i10, String str);

    public native void OnConsentFetchedSuccess(List<UsercentricsServiceConsent> list);

    public void RequestConsentSettings() {
        CheckConsentState();
    }

    public void ShowConsentSettings() {
        CheckConsentState();
    }
}
