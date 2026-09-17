#include <jni.h>
#include <android/log.h>
#include <string.h>

#define LOG_TAG "ZennKuy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jobject JNICALL
Java_launcher_powerkuy_growlauncher_api_JNICall_00024Companion_notifyValueChanged(
    JNIEnv* env, jobject /*thiz*/, jint type, jstring key, jobject /*value*/) {
    const char* k = key ? env->GetStringUTFChars(key, nullptr) : "";
    LOGI("JNICall type=%d key=%s", (int)type, k ? k : "");
    bool google = k && (strstr(k, "google_login") || strstr(k, "google_redirect"));
    if (key) env->ReleaseStringUTFChars(key, k);
    if (google) {
        jclass cls = env->FindClass("com/rtsoft/growtopia/ZennKuyBridge");
        if (cls) {
            jmethodID mid = env->GetStaticMethodID(cls, "openGoogleAccountPicker", "()V");
            if (mid) env->CallStaticVoidMethod(cls, mid);
            if (env->ExceptionCheck()) env->ExceptionClear();
            env->DeleteLocalRef(cls);
        }
    }
    return nullptr;
}
