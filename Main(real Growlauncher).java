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
import androidx.lifecycle.w;
import c5.g;
import com.rtsoft.growtopia.HeightProvider;
import com.ubisoft.bridge.a;
import eh.m;
import gi.e;
import i.o;
import java.net.URLEncoder;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;
import l3.c;
import launcher.powerkuy.growlauncher.api.JNICall;
import launcher.powerkuy.growlauncher.api.JavaForNative;
import launcher.powerkuy.growlauncher.luamanager.LuaManager;
import w1.y0;

/* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
/* JADX INFO: loaded from: classes.dex */
public class Main extends SharedActivity {
    public static boolean OriginalKeyboard = false;
    public static boolean block_pause;
    public static HelpShiftManager helpshiftManager;
    public static Main mainApp;
    public static GLSurfaceView mygl;
    private g controller;
    private o delegate;
    private HeightProvider heightProvider;
    private w registry;
    public NativeAppInterface nativeAppInterface = new NativeAppInterface();
    public AppsFlyerManager appsflyerManager = new AppsFlyerManager(this);
    public IronSourceManager ironSourceManager = new IronSourceManager(this);
    public WebViewManager webViewManager = new WebViewManager(this);
    public AppReviewManager appReviewManager = new AppReviewManager(this);
    public FirebaseCrashlyticsManager firebaseCrashlyticsManager = new FirebaseCrashlyticsManager();
    public FirebaseCloudMessageManager firebaseCloudMessageManager = new FirebaseCloudMessageManager();
    public GoogleSignInHelper googleSignInHelper = new GoogleSignInHelper(this);
    public MAFManager mafManager = new MAFManager(this);
    public UsercentricsManager usercentricsManager = null;

    static {
        System.loadLibrary("PowerKuy");
    }

    public static AppReviewManager GetAppReviewManager() {
        return mainApp.appReviewManager;
    }

    public static AppsFlyerManager GetAppsflyerManager() {
        return mainApp.appsflyerManager;
    }

    public static FirebaseCloudMessageManager GetFirebaseCloudMessageManager() {
        return mainApp.firebaseCloudMessageManager;
    }

    public static FirebaseCrashlyticsManager GetFirebaseCrashlyticsManager() {
        return new FirebaseCrashlyticsManager();
    }

    public static GoogleSignInHelper GetGoogleSignInHelper() {
        return mainApp.googleSignInHelper;
    }

    public static Object GetHelpShiftManager() {
        return helpshiftManager;
    }

    public static Object GetIronSourceManager() {
        return mainApp.ironSourceManager;
    }

    public static MAFManager GetMAFManager() {
        return mainApp.mafManager;
    }

    public static UsercentricsManager GetUsercentricsManager() {
        return mainApp.usercentricsManager;
    }

    public static WebViewManager GetWebViewManager() {
        return mainApp.webViewManager;
    }

    public static boolean HandleDeeplink(Intent intent) {
        final Uri data = intent.getData();
        if (data == null) {
            return false;
        }
        Log.d("URL host", data.getHost());
        Log.d("URL data", data.toString());
        Log.d("URL Path", data.getPath());
        Log.d("URL Scheme", data.getScheme());
        Log.d("URL Fragment", data.getSchemeSpecificPart());
        SharedActivity.mGLView.post(new Runnable() { // from class: com.rtsoft.growtopia.Main.1
            @Override // java.lang.Runnable
            public void run() {
                NativeAppInterface nativeAppInterface = Main.mainApp.nativeAppInterface;
                NativeAppInterface.OnDeepLinkProcess(data.getSchemeSpecificPart());
            }
        });
        return true;
    }

    private void applyImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT < 30) {
            getWindow().getDecorView().setSystemUiVisibility(5894);
            return;
        }
        WindowInsetsController insetsController = getWindow().getInsetsController();
        if (insetsController != null) {
            insetsController.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            insetsController.setSystemBarsBehavior(2);
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            Uri data = intent.getData();
            if (!"android.intent.action.VIEW".equals(action) || data == null) {
                return;
            }
            JNICall.Companion.notifyValueChanged(5, "google_redirect_callback", "info=" + URLEncoder.encode(data.getQueryParameter("info")) + "&token=" + URLEncoder.encode(data.getQueryParameter("token")));
        }
    }

    public static native void nativeOnKey(int i10, int i11, int i12);

    public static native boolean nativeOnTouch(float f8, float f10, int i10);

    @Override // com.rtsoft.growtopia.SharedActivity
    public String GetAppsflyerUID() {
        return "";
    }

    public void OnKeyboardHeightChanged(int i10) {
        if (OriginalKeyboard) {
            if (this.webViewManager.IsVisible()) {
                this.webViewManager.MoveView(i10);
                return;
            }
            SharedActivity.m_KeyBoardHeight = i10;
            boolean z10 = i10 > getBottomCutoutHeight();
            Log.d("NIRMAN", "Keyboard height = " + SharedActivity.m_KeyBoardHeight);
            if (z10 && !SharedActivity.m_editText.isFocused()) {
                Log.d("NIRMAN", "KeyboardX opening...");
                UpdateEditBoxInView(true, false);
            } else if (!z10 && SharedActivity.m_editText.isFocused()) {
                OriginalKeyboard = false;
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
    }

    public int getBottomCutoutHeight() {
        WindowInsets rootWindowInsets = getWindow().getDecorView().getRootWindowInsets();
        if (rootWindowInsets == null || Build.VERSION.SDK_INT < 30) {
            return 0;
        }
        return c.c(rootWindowInsets.getInsets(135)).f10006d;
    }

    public void hideKeyboard(Activity activity) {
        View viewFindViewById = activity.findViewById(android.R.id.content);
        if (viewFindViewById != null) {
            ((InputMethodManager) activity.getSystemService("input_method")).hideSoftInputFromWindow(viewFindViewById.getWindowToken(), 0);
            Log.e("ModMenu", "Keyboard hidden");
        }
    }

    public void initialize(Bundle bundle) {
        this.mViewGroup.addView(new e(this, null));
    }

    @Override // androidx.activity.n, android.app.Activity
    public void onActivityResult(int i10, int i11, Intent intent) {
        super.onActivityResult(i10, i11, intent);
    }

    @Override // androidx.activity.n, android.app.Activity, android.content.ComponentCallbacks
    public void onConfigurationChanged(Configuration configuration) {
        int i10 = configuration.screenHeightDp;
        int i11 = configuration.screenWidthDp;
        if (i10 > i11) {
            configuration.screenHeightDp = i11;
            configuration.screenWidthDp = i10;
        }
        super.onConfigurationChanged(configuration);
        getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
    }

    @Override // com.rtsoft.growtopia.SharedActivity, androidx.activity.n, i3.m, android.app.Activity
    public void onCreate(Bundle bundle) {
        mainApp = this;
        helpshiftManager = new HelpShiftManager(this);
        SharedActivity.dllname = "growtopia";
        this.BASE64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArv12FD/xxuAJ3/B8Jgx78985UN/FitcQD5C21eIS5D+98yr7dy9sw8R2fSTFZKExBZVAfatgDH7s6fb9vfHi43szfpdXs3ZL2hsa7DeCWRyVSTD6o/i14vgwInv1S/dgLAwQth3PDXWF+zYXOlL+umOt9K9eqQo5CZhkwl9JAmMHlazvbhSGAldV5QsdY3pK5wmg/w2873abgYsGdI3B9wL75kgZW9tV2O6efiIbXlevktGOMup3Ql2H4Rcpa3ZeDtGl+YTQbEUQTYiYBDtFGCyqksXeM6+kCnaF97Ss5wA0w5ID9WJLkziXI4iGBMRd0a7s+vVniwpx771oGcJxewIDAQAB";
        SharedActivity.securityEnabled = false;
        SharedActivity.IAPEnabled = true;
        SharedActivity.HookedEnabled = false;
        SharedActivity.PackageName = BuildConfig.APPLICATION_ID;
        System.loadLibrary("growtopia");
        super.onCreate(bundle);
        Configuration configuration = getResources().getConfiguration();
        int i10 = configuration.screenHeightDp;
        int i11 = configuration.screenWidthDp;
        if (i10 > i11) {
            configuration.screenHeightDp = i11;
            configuration.screenWidthDp = i10;
            getResources().updateConfiguration(configuration, getResources().getDisplayMetrics());
        }
        a.a(this);
        this.heightProvider = new HeightProvider(this).setHeightListener(new HeightProvider.HeightListener() { // from class: com.rtsoft.growtopia.Main.2
            @Override // com.rtsoft.growtopia.HeightProvider.HeightListener
            public void onHeightChanged(int i12) {
                Main.this.OnKeyboardHeightChanged(i12);
            }
        });
        initialize(bundle);
        this.usercentricsManager = new UsercentricsManager(this);
        getWindow().addFlags(128);
        if (m.m(this).getFullscreen()) {
            applyImmersiveFullscreen();
        }
    }

    @Override // com.rtsoft.growtopia.SharedActivity, android.app.Activity
    public void onDestroy() {
        stopService(new Intent(this, (Class<?>) LuaManager.class));
        super.onDestroy();
    }

    @Override // androidx.activity.n, android.app.Activity
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override // com.rtsoft.growtopia.SharedActivity, android.app.Activity
    public void onPause() {
        super.onPause();
        LuaManager luaManager = LuaManager.f10099v;
        if (luaManager != null) {
            luaManager.a();
        }
        this.heightProvider.OnPause();
    }

    @Override // com.rtsoft.growtopia.SharedActivity, android.app.Activity
    public void onResume() {
        y0 y0Var;
        super.onResume();
        LuaManager luaManager = LuaManager.f10099v;
        if (luaManager != null && (y0Var = luaManager.f10103t) != null) {
            try {
                y0Var.setVisibility(0);
            } catch (Exception e10) {
                e10.printStackTrace();
            }
        }
        this.heightProvider.OnResume();
    }

    @Override // com.rtsoft.growtopia.SharedActivity, android.app.Activity
    public void onStart() {
        super.onStart();
    }

    @Override // com.rtsoft.growtopia.SharedActivity, android.app.Activity
    public void onStop() {
        LuaManager luaManager = LuaManager.f10099v;
        if (luaManager != null) {
            luaManager.a();
        }
        JavaForNative.shutdown();
        super.onStop();
    }

    /* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
    public static class PowerKuyRootRenderer implements GLSurfaceView.Renderer {
        public static native void nativeDrawFrame();

        public static native int nativeGetMessagePowerKuy();

        public static native void nativeSurfaceChanged(int i10, int i11);

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onDrawFrame(GL10 gl10) {
            nativeDrawFrame();
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceChanged(GL10 gl10, int i10, int i11) {
            try {
                nativeSurfaceChanged(i10, i11);
            } catch (UnsatisfiedLinkError unused) {
            }
        }

        @Override // android.opengl.GLSurfaceView.Renderer
        public void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
        }
    }
}
