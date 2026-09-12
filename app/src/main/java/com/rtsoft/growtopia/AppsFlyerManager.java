package com.rtsoft.growtopia;

import android.content.Context;
import java.util.Map;

public class AppsFlyerManager {
    private Context baseContext;
    private volatile boolean isStoped = false;
    private volatile boolean isStarted = false;

    native void nativeOnStarted(int i);

    public AppsFlyerManager(Context context) {
        this.baseContext = context;
    }

    public String GetAppsFlyerId() { return ""; }
    public void Init(String key) {}
    public void Start(boolean consent, boolean shouldStart) {
        try { nativeOnStarted(0); } catch (UnsatisfiedLinkError e) {}
    }
    public void LogEvent(String event, String value) {}
    public void LogEvent(String event, Map<String, Object> map) {}
    public void LogPurchase(String a, String b, String c) {}
}
