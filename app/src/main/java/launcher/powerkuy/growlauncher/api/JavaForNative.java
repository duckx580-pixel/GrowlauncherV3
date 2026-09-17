package launcher.powerkuy.growlauncher.api;

/**
 * Java-side stub for the native methods exported by libPowerKuy.so
 * under the JavaForNative symbol group. These are called to initialise
 * and query the PowerKuy layer before the login WebView is shown.
 */
public class JavaForNative {
    public static native String getSafeGameVersion();
    public static native boolean isLtokenSpoofActive();
    public static native String getSupportedGameVersion();
    public static native void shutdown();
    public static native void initialize();

    public static class Configuration {
        public static native String getJsonConfiguration();
        public static native void setJsonConfiguration(String config);
    }
}
