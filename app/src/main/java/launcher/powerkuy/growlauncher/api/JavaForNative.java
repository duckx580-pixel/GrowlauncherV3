package launcher.powerkuy.growlauncher.api;

import com.gentz.launcher.api.GentzGoogle;

public class JavaForNative {
    public static String getSafeGameVersion() {
        return GentzGoogle.getSafeGameVersion();
    }
    public static boolean isLtokenSpoofActive() {
        return GentzGoogle.isLtokenSpoofActive();
    }
    public static String getSupportedGameVersion() {
        return GentzGoogle.getSafeGameVersion();
    }
    public static void shutdown() {}
    public static void initialize() {}

    public static class Configuration {
        public static String getJsonConfiguration() { return "{}"; }
        public static void setJsonConfiguration(String config) {}
    }
}
