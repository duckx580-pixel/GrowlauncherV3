package com.rtsoft.growtopia;

/**
 * Native entry points for the ImGui mod-menu overlay, implemented in
 * libzennkuy.so (zennkuy.cpp).
 *
 * These are called directly from {@link AppRenderer#onDrawFrame} right after
 * the game's own {@code nativeRender()}, on the same GL thread and inside the
 * same current EGL context — not through any EGL/GOT hook. GLSurfaceView
 * (the Android framework, not libgrowtopia.so) owns eglSwapBuffers and calls
 * it only after onDrawFrame() returns, so there was never a PLT entry inside
 * the game library for a GOT hook to patch.
 */
final class ZennKuyRenderer {
    private ZennKuyRenderer() {}

    static native void nativeDrawFrame();
    static native void nativeSurfaceChanged(int width, int height);
    static native void nativeOnTouch(int action, float x, float y);
    static native boolean isCapturingInput();
}
