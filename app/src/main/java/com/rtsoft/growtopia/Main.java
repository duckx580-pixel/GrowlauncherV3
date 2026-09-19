package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import com.ubisoft.bridge.JavaInterface;

import java.io.File;
import java.net.URLEncoder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import android.view.MotionEvent;

public class Main extends SharedActivity {
    public static boolean OriginalKeyboard = false;
    public static boolean block_pause;
    public static HelpShiftManager helpshiftManager;
    public static Main mainApp;
    public static GLSurfaceView mygl;
    private HeightProvider heightProvider;

    // Touch event processing on background thread to prevent GL thread blocking
    private static class TouchEvent {
        final int action;
        final float x;
        final float y;

        TouchEvent(int action, float x, float y) {
            this.action = action;
            this.x = x;
            this.y = y;
        }
    }

    private BlockingQueue<TouchEvent> touchEventQueue = new LinkedBlockingQueue<>();
    private Thread touchProcessorThread;

    public NativeAppInterface nativeAppInterface = new NativeAppInterface();
    public AppsFlyerManager appsflyerManager = new AppsFlyerManager(this);
    public IronSourceManager ironSourceManager = new IronSourceManager(this);
    public WebViewManager webViewManager = new WebViewManager(this);
    public AppReviewManager appReviewManager = new AppReviewManager(this);
    public FirebaseCrashlyticsManager firebaseCrashlyticsManager;
    public FirebaseCloudMessageManager firebaseCloudMessageManager = new FirebaseCloudMessageManager();
    public GoogleSignInHelper googleSignInHelper;
    public ZennKuyOverlay zennKuyOverlay;
    public MAFManager mafManager = new MAFManager(this);
    public UsercentricsManager usercentricsManager = null;

    public static AppReviewManager GetAppReviewManager() { return mainApp.appReviewManager; }
    public static AppsFlyerManager GetAppsflyerManager() { return mainApp.appsflyerManager; }
    public static FirebaseCloudMessageManager GetFirebaseCloudMessageManager() { return mainApp.firebaseCloudMessageManager; }
    public static FirebaseCrashlyticsManager GetFirebaseCrashlyticsManager() { return mainApp.firebaseCrashlyticsManager; }
    public static GoogleSignInHelper GetGoogleSignInHelper() { return mainApp.googleSignInHelper; }
    public static Object GetHelpShiftManager() { return helpshiftManager; }
    public static Object GetIronSourceManager() { return mainApp.ironSourceManager; }
    public static MAFManager GetMAFManager() { return mainApp.mafManager; }
    public static UsercentricsManager GetUsercentricsManager() {
        if (mainApp == null) return null;
        if (mainApp.usercentricsManager == null) {
            mainApp.usercentricsManager = new UsercentricsManager(mainApp);
        }
        return mainApp.usercentricsManager;
    }
    public static WebViewManager GetWebViewManager() { return mainApp.webViewManager; }

    private void handleIntent(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return;
        Uri data = intent.getData();
        if (data == null) return;
        if (!"grow".equals(data.getScheme())) return;

        String info = data.getQueryParameter("info");
        String token = data.getQueryParameter("token");
        Toast.makeText(this, "Logging in with google... wait a moment...", Toast.LENGTH_LONG).show();

        try {
            String payload = "info=" + URLEncoder.encode(info == null ? "" : info, "UTF-8")
                + "&token=" + URLEncoder.encode(token == null ? "" : token, "UTF-8");
            launcher.powerkuy.growlauncher.api.JNICall.Companion.notifyValueChanged(
                5, "google_redirect_callback", payload);
        } catch (Throwable ignored) {}

        try {
            NativeAppInterface.OnDeepLinkProcess(data.getSchemeSpecificPart());
        } catch (Throwable t) {
            Log.e("Main", "OnDeepLinkProcess", t);
        }

        if (token != null && !token.isEmpty()) {
            try {
                LoginSpoof s = new LoginSpoof(this);
                s.setGoogleToken(token);
                s.setLtoken(token);
                s.setEnabled(true);
            } catch (Throwable ignored) {}
            if (webViewManager != null) webViewManager.nativeOnScriptCall("nativeSignIn", token);
        }
    }

    private void logSavePath() {
        try {
            File dir = getExternalFilesDir(null);
            File save = dir == null ? null : new File(dir, "save.dat");
            File cache = dir == null ? null : new File(dir, "cache");
            Log.i("ZennKuyPath", "pkg=" + getPackageName()
                + " assetPkg=" + SharedActivity.PackageName
                + " files=" + (dir == null ? "null" : dir.getAbsolutePath())
                + " save.exists=" + (save != null && save.isFile())
                + " save.size=" + (save != null && save.isFile() ? save.length() : 0)
                + " cache.dir=" + (cache != null && cache.isDirectory()));
        } catch (Throwable t) {
            Log.e("ZennKuyPath", "logSavePath", t);
        }
    }

    public static native void nativeOnTouch(int action, float x, float y);
    public static native boolean isImGuiCapturingInput();

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            // Queue the touch event to be processed on background thread
            // This prevents blocking the GL rendering thread with socket operations
            touchEventQueue.offer(new TouchEvent(ev.getAction(), ev.getX(), ev.getY()));
            if (isImGuiCapturingInput()) return true;
        } catch (Exception ignored) {}
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public String GetAppsflyerUID() { return ""; }

    public int getBottomCutoutHeight() {
        android.view.WindowInsets rootWindowInsets = getWindow().getDecorView().getRootWindowInsets();
        if (rootWindowInsets == null || Build.VERSION.SDK_INT < 30) return 0;
        return rootWindowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).bottom;
    }

    public void OnKeyboardHeightChanged(int height) {
        if (this.webViewManager.IsVisible()) {
            this.webViewManager.MoveView(height);
            return;
        }
        SharedActivity.m_KeyBoardHeight = height;
        boolean keyboardOpen = height > getBottomCutoutHeight();
        if (keyboardOpen && !SharedActivity.m_editText.isFocused()) {
            UpdateEditBoxInView(true, false);
        } else if (!keyboardOpen && SharedActivity.m_editText.isFocused()) {
            SharedActivity.nativeOnInputText(SharedActivity.m_editText.getText().toString());
            if (!SharedActivity.passwordField) SharedActivity.nativeOnKey(1, 500000, 0);
            SharedActivity.nativeCancelBtnPressed();
            UpdateEditBoxInView(false, false);
        }
        if (SharedActivity.m_editText.isFocused()) UpdateEditBoxRootViewPosition();
    }

    public void hideKeyboard(Activity activity) {
        View view = activity.findViewById(android.R.id.content);
        if (view != null) {
            ((InputMethodManager) activity.getSystemService("input_method")).hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 1 && googleSignInHelper != null) {
            googleSignInHelper.handleSignInResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        int h = config.screenHeightDp, w = config.screenWidthDp;
        if (h > w) { config.screenHeightDp = w; config.screenWidthDp = h; }
        super.onConfigurationChanged(config);
        getResources().updateConfiguration(config, getResources().getDisplayMetrics());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mainApp = this;
        googleSignInHelper = new GoogleSignInHelper(this);
        helpshiftManager = new HelpShiftManager(this);
        SharedActivity.dllname = "growtopia";
        this.BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArv12FD/xxuAJ3/B8Jgx78985UN/FitcQD5C21eIS5D+98yr7dy9sw8R2fSTFZKExBZVAfatgDH7s6fb9vfHi43szfpdXs3ZL2hsa7DeCWRyVSTD6o/i14vgwInv1S/dgLAwQth3PDXWF+zYXOlL+umOt9K9eqQo5CZhkwl9JAmMHlazvbhSGAldV5QsdY3pK5wmg/w2873abgYsGdI3B9wL75kgZW9tV2O6efiIbXlevktGOMup3Ql2H4Rcpa3ZeDtGl+YTQbEUQTYiYBDtFGCyqksXeM6+kCnaF97Ss5wA0w5ID9WJLkziXI4iGBMRd0a7s+vVniwpx771oGcJxewIDAQAB";
        SharedActivity.securityEnabled = false;
        SharedActivity.IAPEnabled = true;
        SharedActivity.HookedEnabled = false;
        SharedActivity.PackageName = SharedActivity.GROWTOPIA_PACKAGE;
        com.gentz.launcher.CrashLogger.markLaunchStarted();
        NativeLibraries.loadGame();
        this.usercentricsManager = new UsercentricsManager(this);
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        JavaInterface.injectActivityJava(this);
        com.ubisoft.bridge.a.a(this);
        this.zennKuyOverlay = new ZennKuyOverlay(this);
        this.zennKuyOverlay.attachTo(mViewGroup);
        this.heightProvider = new HeightProvider(this).setHeightListener(this::OnKeyboardHeightChanged);
        // Start background thread for touch event processing to prevent GL thread blocking
        startTouchProcessorThread();
        this.firebaseCrashlyticsManager = new FirebaseCrashlyticsManager(this);
        this.ironSourceManager.OnCreate();
        this.appReviewManager.OnCreate();
        getWindow().addFlags(128);
        logSavePath();
        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (this.heightProvider != null) this.heightProvider.OnPause();
        this.ironSourceManager.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (this.heightProvider != null) this.heightProvider.OnResume();
        this.ironSourceManager.onResume();
        logSavePath();
    }

    @Override public void onStart() { super.onStart(); }

    @Override
    public void onStop() {
        com.gentz.launcher.CrashLogger.markLaunchFinished();
        stopTouchProcessorThread();
        super.onStop();
    }

    private void startTouchProcessorThread() {
        if (touchProcessorThread == null || !touchProcessorThread.isAlive()) {
            touchProcessorThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        TouchEvent event = touchEventQueue.take(); // Blocks until event available
                        try {
                            nativeOnTouch(event.action, event.x, event.y);
                        } catch (UnsatisfiedLinkError ignored) {}
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "TouchEventProcessor");
            touchProcessorThread.start();
        }
    }

    private void stopTouchProcessorThread() {
        if (touchProcessorThread != null) {
            touchProcessorThread.interrupt();
            try {
                touchProcessorThread.join(1000); // Wait up to 1 second for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ZennKuy Renderer - Static inner class for native rendering and message handling
    public static class ZennKuyRenderer {
        public static native void nativeDrawFrame();
        public static native int nativeGetMessageZennKuy();
        public static native void nativeSurfaceChanged(int width, int height);
        public static native void nativeForcedOnlineMode(boolean enabled);
        public static native void nativeBypassLogin(String token);
    }
}