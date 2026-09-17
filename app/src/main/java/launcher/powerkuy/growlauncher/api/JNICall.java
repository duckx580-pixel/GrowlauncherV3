package launcher.powerkuy.growlauncher.api;

import com.gentz.launcher.api.GentzGoogle;

public class JNICall {
    public static class Companion {
        public Object notifyValueChanged(int type, String key, Object value) {
            GentzGoogle.notifyValueChanged(type, key, value);
            return null;
        }
    }
    public static final Companion Companion = new Companion();
}
