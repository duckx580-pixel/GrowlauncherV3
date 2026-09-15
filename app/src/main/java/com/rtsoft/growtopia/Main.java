package com.rtsoft.growtopia;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;

import com.rtsoft.growtopia.HeightProvider;
import com.ubisoft.bridge.JavaInterface;

public class Main extends SharedActivity {
    public static boolean OriginalKeyboard = false;
    public static boolean block_pause;
    public static HelpShiftManager helpshiftManager;
    public static Main mainApp;
    public static GLSurfaceView mygl;
    private HeightProvider heightProvider;

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

    // === Static getters the native engine calls via JNI ===
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

    public static boolean HandleDeeplink(Intent intent) {
        final Uri data = intent.getData();
        if (data == null) return false;
        Log.d("URL host", "" + data.getHost());
        Log.d("URL data", data.toString());
        SharedActivity.mGLView.post(() -> {
            NativeAppInterface.OnDeepLinkProcess(data.getSchemeSpecificPart());
        });
        return true;
    }

    // Native methods in libzennkuy.so
    public static native void nativeOnTouch(int action, float x, float y);
    public static native boolean isImGuiCapturingInput();

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        try {
            nativeOnTouch(ev.getAction(), ev.getX(), ev.getY());
            if (isImGuiCapturingInput()) return true;
        } catch (UnsatisfiedLinkError ignored) {
            // libzennkuy not loaded (debug builds without the lib)
        }
        return super.dispatchTouchEvent(ev);
    }

    private void applyImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (!"android.intent.action.VIEW".equals(intent.getAction())) return;
        try {
            Uri data = intent.getData();
            if (data == null) return;
            String info = data.getQueryParameter("info");
            String token = data.getQueryParameter("token");
            if (info != null || token != null) {
                // Google OAuth redirect: grow://growtopia?info=...&token=...
                // Chrome followed the grow:// redirect after Google authentication.
                final String safeInfo  = info  != null ? info  : "";
                final String safeToken = token != null ? token : "";
                Log.d("Main", "Google OAuth redirect received, token length=" + safeToken.length()
                        + " info length=" + safeInfo.length());

                // Mark token as delivered so ZennKuyBridge.startResolving() does not
                // reload the dashboard URL if the engine retries SignIn() after this.
                // Check both safeToken and safeInfo — Growtopia sometimes delivers the
                // ltoken in the "info" param instead of "token".
                if (!safeToken.isEmpty() || !safeInfo.isEmpty()) {
                    ZennKuyBridge.sTokenDelivered = true;
                }

                // Dismiss the WebView overlay.
                // When nativeSignIn("") launches Chrome externally, the grow:// redirect
                // arrives here via onNewIntent — it bypasses WebViewManager.handleGrowUrl()
                // which normally calls HideWebView(). Without this call the WebView stays
                // on top of the game after token delivery.
                webViewManager.HideWebView();

                // Deliver ltoken to the engine via nativeOnScriptCall("nativeSignIn", token).
                // This is the V3 equivalent of Real Growlauncher's:
                //   JNICall.notifyValueChanged(5, "google_redirect_callback", payload)
                // "token" param first, "info" as fallback — Growtopia sometimes delivers
                // the ltoken in the "info" param instead of "token".
                // This is the same delivery path used by handleGrowUrl() when the grow://
                // redirect is intercepted inside the WebView.
                final String actualToken = !safeToken.isEmpty() ? safeToken : safeInfo;
                if (!actualToken.isEmpty()) {
                    webViewManager.nativeOnScriptCall("nativeSignIn", actualToken);
                } else {
                    Log.w("Main", "handleIntent: grow:// redirect had no usable token — login may fail");
                }
            } else {
                HandleDeeplink(intent);
            }
        } catch (Exception e) {
            Log.e("Main", "handleIntent error: " + e.getMessage());
        }
    }

    @Override
    public String GetAppsflyerUID() { return ""; }

    public int getBottomCutoutHeight() {
        android.view.WindowInsets rootWindowInsets = getWindow().getDecorView().getRootWindowInsets();
        if (rootWindowInsets == null || Build.VERSION.SDK_INT < 30) {
            return 0;
        }
        return rootWindowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()).bottom;
    }

    public void OnKeyboardHeightChanged(int height) {
        if (this.webViewManager.IsVisible()) {
            this.webViewManager.MoveView(height);
            return;
        }
        SharedActivity.m_KeyBoardHeight = height;
        boolean keyboardOpen = height > getBottomCutoutHeight();
        Log.d("NIRMAN", "Keyboard height = " + SharedActivity.m_KeyBoardHeight);
        if (keyboardOpen && !SharedActivity.m_editText.isFocused()) {
            Log.d("NIRMAN", "KeyboardX opening...");
            UpdateEditBoxInView(true, false);
        } else if (!keyboardOpen && SharedActivity.m_editText.isFocused()) {
            Log.d("NIRMAN", "KeyboardX closing...");
            SharedActivity.nativeOnInputText(SharedActivity.m_editText.getText().toString());
            if (!SharedActivity.passwordField) {
                SharedActivity.nativeOnKey(1, 500000, 0);
            }
            SharedActivity.nativeCancelBtnPressed();
            UpdateEditBoxInView(false, false);
            if (Looper.myLooper() != Looper.getMainLooper()) {
                SharedActivity.nativeUpdateConsoleLogPos(SharedActivity.m_KeyBoardHeight);
            }
        }
        if (SharedActivity.m_editText.isFocused()) {
            UpdateEditBoxRootViewPosition();
        }
    }

    public void hideKeyboard(Activity activity) {
        View view = activity.findViewById(android.R.id.content);
        if (view != null) {
            ((InputMethodManager) activity.getSystemService("input_method")).hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    /**
     * Dispatches the Google Sign-In SDK result to GoogleSignInHelper.
     *
     * Request code 1 is reserved for Chrome/browser OAuth launches
     * (nativeSignIn("") and openAsResult()). Chrome opens as a separate
     * task and immediately returns RESULT_CANCELED — forwarding that to the
     * SDK would trigger a failed-sign-in callback and show a second Cancel
     * button in-game (the Cancel #2 bug). RC 1 is therefore excluded.
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Do NOT forward RC 1 to the SDK — that is the Chrome browser launch
        // request code. Chrome returns RESULT_CANCELED immediately (it opens
        // as a separate task), which the SDK misinterprets as a failed sign-in
        // and triggers a second Cancel dialog in-game.
        if (requestCode != 1 && googleSignInHelper != null) {
            googleSignInHelper.handleSignInResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        int h = config.screenHeightDp;
        int w = config.screenWidthDp;
        if (h > w) {
            config.screenHeightDp = w;
            config.screenWidthDp = h;
        }
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
        if (isFinishing()) {
            return;
        }

        Configuration config = getResources().getConfiguration();
        int h = config.screenHeightDp;
        int w = config.screenWidthDp;
        if (h > w) {
            config.screenHeightDp = w;
            config.screenWidthDp = h;
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
        }

        JavaInterface.injectActivityJava(this);

        this.zennKuyOverlay = new ZennKuyOverlay(this);
        this.zennKuyOverlay.attachTo(mViewGroup);

        this.heightProvider = new HeightProvider(this).setHeightListener(height -> {
            OnKeyboardHeightChanged(height);
        });

        this.firebaseCrashlyticsManager = new FirebaseCrashlyticsManager(this);
        this.ironSourceManager.OnCreate();
        this.appReviewManager.OnCreate();
        getWindow().addFlags(128);

        handleIntent(getIntent());
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
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
    }

    @Override
    public void onStart() { super.onStart(); }

    @Override
    public void onStop() {
        com.gentz.launcher.CrashLogger.markLaunchFinished();
        super.onStop();
    }

}
