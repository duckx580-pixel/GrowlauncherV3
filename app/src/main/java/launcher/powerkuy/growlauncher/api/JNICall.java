package launcher.powerkuy.growlauncher.api;

/**
 * Java-side stub whose package name must match the JNI symbol exported by
 * libPowerKuy.so:
 *   Java_launcher_powerkuy_growlauncher_api_JNICall_00024Companion_notifyValueChanged
 *
 * libPowerKuy.so handles the full Google OAuth browser invocation once
 * notifyValueChanged(0, "google_login_btn", Boolean.TRUE) is fired.
 * The redirect callback arrives as notifyValueChanged(5, "google_redirect_callback", ...).
 */
public class JNICall {
    public static class Companion {
        public static native Object notifyValueChanged(int type, String key, Object value);
    }
    public static final Companion Companion = new Companion();
}
