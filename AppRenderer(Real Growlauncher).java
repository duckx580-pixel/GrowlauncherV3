package com.rtsoft.growtopia;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Surface;
import android.view.inputmethod.InputMethodManager;
import com.rtsoft.growtopia.Main;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
/* JADX INFO: loaded from: classes.dex */
public class AppRenderer implements GLSurfaceView.Renderer {
    static final int MESSAGE_ALLOW_SCREEN_DIMMING = 21;
    static final int MESSAGE_APPSFLYER_EVENT = 1004;
    static final int MESSAGE_APPSFLYER_LOG_PURCHASE = 40;
    static final int MESSAGE_CHARTBOOST_CACHE_INTERSTITIAL = 23;
    static final int MESSAGE_CHARTBOOST_CACHE_MORE_APPS = 25;
    static final int MESSAGE_CHARTBOOST_NOTIFY_INSTALL = 28;
    static final int MESSAGE_CHARTBOOST_RESERVED1 = 29;
    static final int MESSAGE_CHARTBOOST_RESERVED2 = 30;
    static final int MESSAGE_CHARTBOOST_SETUP = 27;
    static final int MESSAGE_CHARTBOOST_SHOW_INTERSTITIAL = 24;
    static final int MESSAGE_CHARTBOOST_SHOW_MORE_APPS = 26;
    static final int MESSAGE_CHECK_CONNECTION = 3;
    static final int MESSAGE_CLOSE_TEXT_BOX = 2;
    static final int MESSAGE_FINISH_APP = 6;
    static final int MESSAGE_FLURRY_LOG_EVENT = 33;
    static final int MESSAGE_FLURRY_ON_PAGE_VIEW = 32;
    static final int MESSAGE_FLURRY_SETUP = 31;
    static final int MESSAGE_FLURRY_START_TIMED_EVENT = 1001;
    static final int MESSAGE_FLURRY_STOP_TIMED_EVENT = 1002;
    static final int MESSAGE_GETSOCIAL_ADD_FRIEND = 1008;
    static final int MESSAGE_GETSOCIAL_EVENT = 1005;
    static final int MESSAGE_GETSOCIAL_LOGIN = 1006;
    static final int MESSAGE_GETSOCIAL_LOGOUT = 1009;
    static final int MESSAGE_GETSOCIAL_OPEN_UI = 1007;
    static final int MESSAGE_HOOKED_SHOW_RATE_DIALOG = 20;
    static final int MESSAGE_IAP_CONSUME_ITEM = 38;
    static final int MESSAGE_IAP_GET_PURCHASED_LIST = 15;
    static final int MESSAGE_IAP_ITEM_DETAILS = 39;
    static final int MESSAGE_IAP_PURCHASE = 14;
    static final int MESSAGE_NONE = 0;
    static final int MESSAGE_OPEN_TEXTBOX_SECRET = 41;
    static final int MESSAGE_OPEN_TEXT_BOX = 1;
    static final int MESSAGE_REQUEST_AD_SIZE = 22;
    static final int MESSAGE_SET_ACCELEROMETER_UPDATE_HZ = 5;
    static final int MESSAGE_SET_FPS_LIMIT = 4;
    static final int MESSAGE_SET_IAP_FLAG = 1011;
    static final int MESSAGE_SET_VIDEO_MODE = 7;
    static final int MESSAGE_SUSPEND_TO_HOME_SCREEN = 34;
    static final int MESSAGE_TAPJOY_AWARD_TAP_POINTS = 18;
    static final int MESSAGE_TAPJOY_GET_AD = 9;
    static final int MESSAGE_TAPJOY_GET_FEATURED_APP = 8;
    static final int MESSAGE_TAPJOY_GET_MOVIE = 10;
    static final int MESSAGE_TAPJOY_GET_TAP_POINTS = 16;
    static final int MESSAGE_TAPJOY_INIT_MAIN = 35;
    static final int MESSAGE_TAPJOY_INIT_PAID_APP_WITH_ACTIONID = 36;
    static final int MESSAGE_TAPJOY_LOGOUT = 1010;
    static final int MESSAGE_TAPJOY_SET_USERID = 37;
    static final int MESSAGE_TAPJOY_SHOW_AD = 12;
    static final int MESSAGE_TAPJOY_SHOW_FEATURED_APP = 11;
    static final int MESSAGE_TAPJOY_SHOW_MOVIE_AD = 13;
    static final int MESSAGE_TAPJOY_SHOW_OFFERS = 19;
    static final int MESSAGE_TAPJOY_SPEND_TAP_POINTS = 17;
    static long m_gameTimer;
    static int m_timerLoopMS;
    public SharedActivity app;
    int height;
    int width;

    public AppRenderer(SharedActivity sharedActivity) {
        this.app = sharedActivity;
    }

    private static native void nativeDone();

    private static native void nativeEmergencyMessageClear();

    private static native int nativeGetLastOSMessageParm1();

    private static native String nativeGetLastOSMessageString();

    private static native String nativeGetLastOSMessageString2();

    private static native String nativeGetLastOSMessageString3();

    private static native float nativeGetLastOSMessageX();

    private static native float nativeGetLastOSMessageY();

    private static native void nativeInit();

    private static native int nativeOSMessageGet();

    public static native void nativeRender();

    public static native void nativeResize(int i10, int i11);

    public static native void nativeSetWindow(Surface surface);

    public static native void nativeUpdate();

    @Override // android.opengl.GLSurfaceView.Renderer
    public synchronized void onDrawFrame(GL10 gl10) {
        int iNativeOSMessageGet;
        try {
            if (this.app == null) {
                return;
            }
            GLES20.glClear(16384);
            if (m_timerLoopMS != 0) {
                while (true) {
                    if (m_gameTimer <= SystemClock.uptimeMillis() && m_gameTimer <= SystemClock.uptimeMillis() + ((long) m_timerLoopMS) + 1) {
                        break;
                    }
                    SystemClock.sleep(1L);
                }
                m_gameTimer = SystemClock.uptimeMillis() + ((long) m_timerLoopMS);
            }
            if (!SharedActivity.bIsShuttingDown && Looper.myLooper() != Looper.getMainLooper()) {
                nativeUpdate();
                nativeRender();
                Main.PowerKuyRootRenderer.nativeDrawFrame();
            }
            int iNativeGetMessagePowerKuy = Main.PowerKuyRootRenderer.nativeGetMessagePowerKuy();
            if (iNativeGetMessagePowerKuy != 0) {
                if (iNativeGetMessagePowerKuy == 1) {
                    ((InputMethodManager) SharedActivity.app.getSystemService("input_method")).toggleSoftInput(2, 0);
                } else if (iNativeGetMessagePowerKuy == 2) {
                    ((InputMethodManager) SharedActivity.app.getSystemService("input_method")).hideSoftInputFromWindow(SharedActivity.mGLView.getWindowToken(), 0);
                }
            }
            while (this.app != null && !SharedActivity.bIsShuttingDown && (iNativeOSMessageGet = nativeOSMessageGet()) != 0) {
                SharedActivity sharedActivity = this.app;
                if (sharedActivity.mMainThreadHandler == null) {
                }
                if (iNativeOSMessageGet != 1) {
                    if (iNativeOSMessageGet == 2) {
                        Log.d(SharedActivity.PackageName, "keyboard MESSAGE_CLOSE_TEXT_BOX");
                        this.app.toggle_keyboard(false);
                        SharedActivity sharedActivity2 = this.app;
                        sharedActivity2.mMainThreadHandler.post(sharedActivity2.mUpdateMainThread);
                    } else if (iNativeOSMessageGet != 4) {
                        if (iNativeOSMessageGet != 5) {
                            if (iNativeOSMessageGet != 6) {
                                if (iNativeOSMessageGet != 8) {
                                    if (iNativeOSMessageGet != 9) {
                                        if (iNativeOSMessageGet != 11 && iNativeOSMessageGet != 12) {
                                            if (iNativeOSMessageGet != 27) {
                                                if (iNativeOSMessageGet != 31) {
                                                    if (iNativeOSMessageGet != MESSAGE_APPSFLYER_EVENT && iNativeOSMessageGet != 34 && iNativeOSMessageGet != 35 && iNativeOSMessageGet != MESSAGE_TAPJOY_LOGOUT && iNativeOSMessageGet != MESSAGE_SET_IAP_FLAG) {
                                                        switch (iNativeOSMessageGet) {
                                                            case 14:
                                                            case 15:
                                                            case 16:
                                                            case 17:
                                                            case 18:
                                                            case 19:
                                                            case 20:
                                                            case 22:
                                                                break;
                                                            case 21:
                                                                if (nativeGetLastOSMessageX() != 0.0f) {
                                                                    Log.v(SharedActivity.PackageName, "Allowing screen dimming.");
                                                                    SharedActivity.set_allow_dimming_asap = true;
                                                                    SharedActivity sharedActivity3 = this.app;
                                                                    sharedActivity3.mMainThreadHandler.post(sharedActivity3.mUpdateMainThread);
                                                                    continue;
                                                                } else {
                                                                    SharedActivity.set_disallow_dimming_asap = true;
                                                                    SharedActivity sharedActivity4 = this.app;
                                                                    sharedActivity4.mMainThreadHandler.post(sharedActivity4.mUpdateMainThread);
                                                                }
                                                                break;
                                                            default:
                                                                switch (iNativeOSMessageGet) {
                                                                    case 37:
                                                                    case 38:
                                                                    case 39:
                                                                    case 40:
                                                                        break;
                                                                    case 41:
                                                                        break;
                                                                    default:
                                                                        Log.v("Unhandled", "Unhandled OS message " + iNativeOSMessageGet);
                                                                        continue;
                                                                }
                                                                break;
                                                        }
                                                    }
                                                } else {
                                                    Log.v(SharedActivity.PackageName, "ERROR: RT_FLURRY_SUPPORT isn't defined in Main.java, you can't use it!");
                                                }
                                            } else {
                                                Log.v(SharedActivity.PackageName, "ERROR: RT_CHARTBOOST_SUPPORT isn't defined in Main.java, you can't use it!");
                                            }
                                        }
                                    } else {
                                        Log.v(SharedActivity.PackageName, "banner ads no longer supported in TJ 10");
                                    }
                                } else {
                                    Log.v(SharedActivity.PackageName, "Asking tj for fullscreen ad");
                                    String strNativeGetLastOSMessageString = nativeGetLastOSMessageString();
                                    Log.v(SharedActivity.PackageName, "MESSAGE_TAPJOY_GET_FEATURED_APP: " + strNativeGetLastOSMessageString);
                                    if (nativeGetLastOSMessageString().length() <= 0 || this.app.tapjoyAdPlacementForSub01 == null || !strNativeGetLastOSMessageString.equals("Sub_01")) {
                                        if (nativeGetLastOSMessageString().length() <= 0 || this.app.tapjoyAdPlacementForTV == null || !strNativeGetLastOSMessageString.equals("GROW_GGP_V4VC_TV")) {
                                            Log.e(SharedActivity.PackageName, "Tapjoy Plancement name not passed");
                                        }
                                    }
                                }
                            } else {
                                Log.v(SharedActivity.PackageName, "Finishing app from java side");
                                SharedActivity.bIsShuttingDown = true;
                                Log.v(SharedActivity.PackageName, "Native shutdown");
                                SharedActivity sharedActivity5 = this.app;
                                sharedActivity5.mMainThreadHandler.post(sharedActivity5.mUpdateMainThread);
                            }
                        } else {
                            sharedActivity.setup_accel(nativeGetLastOSMessageX());
                        }
                    } else if (nativeGetLastOSMessageX() == 0.0f) {
                        m_timerLoopMS = 0;
                    } else {
                        m_timerLoopMS = (int) (1000.0f / nativeGetLastOSMessageX());
                    }
                }
                SharedActivity.passwordField = false;
                if (iNativeOSMessageGet == 41) {
                    SharedActivity.passwordField = true;
                }
                SharedActivity.m_text_max_length = nativeGetLastOSMessageParm1();
                SharedActivity.m_text_default = nativeGetLastOSMessageString();
                SharedActivity.m_before = nativeGetLastOSMessageString();
                SharedActivity.updateText = true;
                this.app.clearIngameInputBox();
                this.app.ChangeEditBoxProperty();
                SharedActivity.updateText = false;
                this.app.toggle_keyboard(true);
                Log.d(SharedActivity.PackageName, "keyboard MESSAGE_OPEN_TEXT_BOX");
                SharedActivity sharedActivity6 = this.app;
                sharedActivity6.mMainThreadHandler.post(sharedActivity6.mUpdateMainThread);
            }
        } catch (Throwable th2) {
            throw th2;
        }
    }

    @Override // android.opengl.GLSurfaceView.Renderer
    public void onSurfaceChanged(GL10 gl10, int i10, int i11) {
        GLES20.glViewport(0, 0, i10, i11);
        nativeResize(i10, i11);
        this.width = i10;
        this.height = i11;
        nativeSetWindow(SharedActivity.mGLView.getHolder().getSurface());
        try {
            Main.PowerKuyRootRenderer.nativeSurfaceChanged(i10, i11);
        } catch (UnsatisfiedLinkError unused) {
        }
    }

    @Override // android.opengl.GLSurfaceView.Renderer
    public void onSurfaceCreated(GL10 gl10, EGLConfig eGLConfig) {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        nativeSetWindow(SharedActivity.mGLView.getHolder().getSurface());
    }
}
