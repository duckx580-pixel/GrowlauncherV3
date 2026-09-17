package launcher.powerkuy.growlauncher.api;

/**
 * Java-side stub whose package name must match the JNI symbol exported by
 * libPowerKuy.so:
 *   Java_launcher_powerkuy_growlauncher_api_JNICall_00024Companion_notifyValueChanged
 *
 * IMPORTANT: notifyValueChanged must NOT be declared static.
 * libPowerKuy.so was compiled from Kotlin without @JvmStatic, so the native
 * symbol expects a jobject receiver (instance method on Companion), not a
 * jclass. Declaring it static causes a silent UnsatisfiedLinkError at
 * runtime and the call silently no-ops on the JS thread.
 */
public class JNICall {
    public static class Companion {
        public native Object notifyValueChanged(int type, String key, Object value);
    }

    public static final Companion Companion = new Companion();
}
