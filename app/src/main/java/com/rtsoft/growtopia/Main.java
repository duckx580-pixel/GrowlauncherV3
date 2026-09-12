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
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;

import com.rtsoft.growtopia.HeightProvider;
import com.ubisoft.bridge.JavaInterface;

import java.net.URLEncoder;

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
    public GoogleSignInHelper googleSignInHelper = new GoogleSignInHelper(this);
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

    // Input is handled by AppGLSurfaceView and SharedActivity's JNI bridge.

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
                // UbiConnect/CSTS OAuth redirect: grow://growtopia?info=...&token=...
                // Pass the OAuth callback to the game engine.
                final String payload = "info=" + URLEncoder.encode(info != null ? info : "")
                        + "&token=" + URLEncoder.encode(token != null ? token : "");
                Log.d("Main", "CSTS redirect received, payload=" + payload);
                if (mGLView != null) {
                    mGLView.post(() -> NativeAppInterface.OnDeepLinkProcess(payload));
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        googleSignInHelper.handleSignInResult(requestCode, resultCode, data);
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
        helpshiftManager = new HelpShiftManager(this);
        SharedActivity.dllname = "growtopia";
        this.BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArv12FD/xxuAJ3/B8Jgx78985UN/FitcQD5C21eIS5D+98yr7dy9sw8R2fSTFZKExBZVAfatgDH7s6fb9vfHi43szfpdXs3ZL2hsa7DeCWRyVSTD6o/i14vgwInv1S/dgLAwQth3PDXWF+zYXOlL+umOt9K9eqQo5CZhkwl9JAmMHlazvbhSGAldV5QsdY3pK5wmg/w2873abgYsGdI3B9wL75kgZW9tV2O6efiIbXlevktGOMup3Ql2H4Rcpa3ZeDtGl+YTQbEUQTYiYBDtFGCyqksXeM6+kCnaF97Ss5wA0w5ID9WJLkziXI4iGBMRd0a7s+vVniwpx771oGcJxewIDAQAB";
        SharedActivity.securityEnabled = false;
        SharedActivity.IAPEnabled = true;
        SharedActivity.HookedEnabled = false;
        // Deliberately NOT getPackageName(): the real Growlauncher sets this to the
        // Growtopia application id, and it decides which APK the engine mounts for
        // its GameData. Overwriting it with the launcher id made the engine look in
        // the wrong package, and made sendVersionDetails() report the launcher
        // version to the server instead of the game version.
        SharedActivity.PackageName = SharedActivity.GROWTOPIA_PACKAGE;
        com.gentz.launcher.CrashLogger.markLaunchStarted();
        NativeLibraries.loadGame();

        // The engine can ask for the consent manager from its first frame, so it has to
        // exist before the GL surface is created in SharedActivity.onCreate().
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

        JavaInterface.injectActivityJava(this); // Ubisoft bridge init

        this.heightProvider = new HeightProvider(this).setHeightListener(height -> {
            OnKeyboardHeightChanged(height);
        });

        this.firebaseCrashlyticsManager = new FirebaseCrashlyticsManager(this);
        this.ironSourceManager.OnCreate();
        this.appReviewManager.OnCreate();
        getWindow().addFlags(128); // FLAG_KEEP_SCREEN_ON

        // Handle grow:// redirect if the activity was cold-started by the OAuth callback.
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
        // Clears the launch breadcrumb; without it the launcher reports a phantom
        // native crash on every subsequent start.
        com.gentz.launcher.CrashLogger.markLaunchFinished();
        super.onStop();
    }

}
