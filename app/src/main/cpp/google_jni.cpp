#include <jni.h>
#include <android/log.h>
#include <string.h>

#define LOG_TAG "ZennKuy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static void open_picker(JNIEnv* env) {
    jclass cls = env->FindClass("com/rtsoft/growtopia/ZennKuyBridge");
    if (!cls) {
        LOGE("ZennKuyBridge missing");
        if (env->ExceptionCheck()) env->ExceptionClear();
        return;
    }
    jmethodID mid = env->GetStaticMethodID(cls, "openGoogleAccountPicker", "()V");
    if (mid) env->CallStaticVoidMethod(cls, mid);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(cls);
}

static void deliver_token(JNIEnv* env, const char* token) {
    if (!token || !token[0]) return;
    jclass mainCls = env->FindClass("com/rtsoft/growtopia/Main");
    if (!mainCls) return;
    jfieldID appField = env->GetStaticFieldID(mainCls, "mainApp", "Lcom/rtsoft/growtopia/Main;");
    jobject app = appField ? env->GetStaticObjectField(mainCls, appField) : nullptr;
    env->DeleteLocalRef(mainCls);
    if (!app) return;
    jclass appCls = env->GetObjectClass(app);
    jfieldID helperField = env->GetFieldID(appCls, "googleSignInHelper",
        "Lcom/rtsoft/growtopia/GoogleSignInHelper;");
    jobject helper = helperField ? env->GetObjectField(app, helperField) : nullptr;
    env->DeleteLocalRef(appCls);
    env->DeleteLocalRef(app);
    if (!helper) return;
    jclass hCls = env->GetObjectClass(helper);
    jmethodID mid = env->GetMethodID(hCls, "deliverResult", "(ILjava/lang/String;)V");
    if (mid) {
        jstring jtok = env->NewStringUTF(token);
        env->CallVoidMethod(helper, mid, 0, jtok);
        env->DeleteLocalRef(jtok);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(hCls);
    env->DeleteLocalRef(helper);
}

/* Engine calls GetGoogleSignInHelper().SignIn() — this is the GMS path that
 * returns Error 10. Replacing the Java body with this native keeps GMS closed. */
extern "C" JNIEXPORT void JNICALL
Java_com_rtsoft_growtopia_GoogleSignInHelper_SignIn(JNIEnv* env, jobject /*thiz*/) {
    LOGI("GoogleSignInHelper.SignIn native — Chrome AccountChooser (no GMS)");
    open_picker(env);
}

/* Same JNI symbol libPowerKuy.so exports. libzennkuy provides it so the Java
 * stub JNICall.Companion.notifyValueChanged actually runs. */
extern "C" JNIEXPORT jobject JNICALL
Java_launcher_powerkuy_growlauncher_api_JNICall_00024Companion_notifyValueChanged(
    JNIEnv* env, jobject /*thiz*/, jint type, jstring key, jobject value) {
    const char* k = key ? env->GetStringUTFChars(key, nullptr) : "";
    LOGI("JNICall type=%d key=%s", (int)type, k ? k : "");

    if (k && (strstr(k, "google_login") || strstr(k, "google_last_url"))) {
        /* login button / dashboard URL — open Chrome picker, never GMS */
        if (strstr(k, "google_login")) open_picker(env);
    }

    if (k && strstr(k, "google_redirect_callback") && value) {
        /* grow://?info=&token= landed in Main.handleIntent */
        if (env->IsInstanceOf(value, env->FindClass("java/lang/String"))) {
            const char* payload = env->GetStringUTFChars((jstring)value, nullptr);
            LOGI("grow:// callback %s", payload ? payload : "");
            const char* tok = payload ? strstr(payload, "token=") : nullptr;
            if (tok) {
                tok += 6;
                deliver_token(env, tok);
            }
            if (payload) env->ReleaseStringUTFChars((jstring)value, payload);
        }
    }

    if (key) env->ReleaseStringUTFChars(key, k);
    return nullptr;
}
