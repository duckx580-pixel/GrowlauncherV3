package com.rtsoft.growtopia;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.view.MotionEvent;

/* JADX INFO: compiled from: r8-map-id-d54062505e0b897b6ce43519f4a85b629f053b64b0d6ed2b86131a7f1732bd67 */
/* JADX INFO: loaded from: classes.dex */
public class AppGLSurfaceView extends GLSurfaceView {
    private static boolean mMultiTouchClassAvailable;
    public static AppRenderer mRenderer;
    public SharedActivity app;

    public AppGLSurfaceView(Context context, SharedActivity sharedActivity) {
        super(context);
        setEGLContextClientVersion(2);
        setSystemUiVisibility(260);
        this.app = sharedActivity;
        if (SharedActivity.m_editText != null) {
            Log.d(SharedActivity.PackageName, "Setting focus options...");
            setFocusable(true);
            setFocusableInTouchMode(true);
            requestFocus();
        }
        setEGLConfigChooser(8, 8, 8, 8, 24, 8);
        setPreserveEGLContextOnPause(true);
        AppRenderer appRenderer = new AppRenderer(this.app);
        try {
            setRenderer(appRenderer);
            mRenderer = appRenderer;
            setRenderMode(1);
        } catch (Exception e10) {
            Log.e(SharedActivity.PackageName, "setRenderer failed: " + e10.getMessage());
        }
        try {
            WrapSharedMultiTouchInput.checkAvailable(this.app);
            mMultiTouchClassAvailable = true;
        } catch (Throwable unused) {
            mMultiTouchClassAvailable = false;
        }
    }

    public static native void nativeOnTouch(int i10, float f8, float f10, int i11);

    public static native void nativePause();

    public static native void nativeResume();

    @Override // android.opengl.GLSurfaceView
    public void onPause() {
        super.onPause();
        if (SharedActivity.bIsShuttingDown) {
            return;
        }
        nativePause();
    }

    @Override // android.opengl.GLSurfaceView
    public void onResume() {
        super.onResume();
        if (SharedActivity.bIsShuttingDown) {
            return;
        }
        try {
            setSystemUiVisibility(260);
        } catch (Exception unused) {
        }
        nativeResume();
    }

    @Override // android.view.View
    public boolean onTouchEvent(MotionEvent motionEvent) {
        super.onTouchEvent(motionEvent);
        if (mMultiTouchClassAvailable) {
            return WrapSharedMultiTouchInput.OnInput(motionEvent);
        }
        if (!Main.nativeOnTouch(motionEvent.getX(), motionEvent.getY(), motionEvent.getAction())) {
            nativeOnTouch(motionEvent.getAction(), motionEvent.getX(), motionEvent.getY(), 0);
        }
        return false;
    }
}
