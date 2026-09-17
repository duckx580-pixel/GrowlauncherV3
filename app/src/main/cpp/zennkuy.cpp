#include <jni.h>
#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/input.h>
#include <android/native_window.h>
#include <pthread.h>
#include <unistd.h>
#include <string>
#include <atomic>
#include <dlfcn.h>

#include "got_hook.h"

#define IMGUI_IMPL_OPENGL_ES2
#include "imgui/imgui.h"
#include "imgui/backends/imgui_impl_opengl3.h"
#include "imgui/backends/imgui_impl_android.h"

#define LOG_TAG "ZennKuy"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* g_jvm = nullptr;
static jobject g_bridge_class = nullptr;
static std::atomic<bool> g_imgui_ready{false};
static std::atomic<bool> g_menu_open{false};

typedef EGLBoolean (*eglSwapBuffers_t)(EGLDisplay, EGLSurface);
static eglSwapBuffers_t g_orig_eglSwapBuffers = nullptr;

static JNIEnv* get_env() {
    JNIEnv* env = nullptr;
    if (g_jvm) g_jvm->AttachCurrentThread(&env, nullptr);
    return env;
}

static void call_bridge(const char* method) {
    JNIEnv* env = get_env();
    if (!env || !g_bridge_class) return;
    jmethodID mid = env->GetStaticMethodID((jclass)g_bridge_class, method, "()V");
    if (mid) env->CallStaticVoidMethod((jclass)g_bridge_class, mid);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

static void imgui_init() {
    if (g_imgui_ready) return;
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.DisplaySize = ImVec2(1080.0f, 2400.0f);
    ImGui::StyleColorsDark();
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding = 8.0f;
    style.FrameRounding  = 4.0f;
    style.Alpha          = 0.92f;
    style.ScaleAllSizes(2.5f);
    io.FontGlobalScale = 2.0f;
    ImGui_ImplOpenGL3_Init("#version 100");
    g_imgui_ready = true;
    LOGI("ImGui initialised");
}

static int g_tab = 0;

static void render_menu() {
    ImGuiIO& io = ImGui::GetIO();
    float btn_sz = 60.0f;
    ImGui::SetNextWindowPos(ImVec2(io.DisplaySize.x - btn_sz - 8, 8), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(btn_sz, btn_sz), ImGuiCond_Always);
    ImGui::SetNextWindowBgAlpha(0.7f);
    ImGui::Begin("##toggle", nullptr,
        ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoMove |
        ImGuiWindowFlags_NoNav | ImGuiWindowFlags_NoSavedSettings);
    if (ImGui::Button("ZK", ImVec2(btn_sz - 16, btn_sz - 16)))
        g_menu_open = !g_menu_open.load();
    ImGui::End();

    if (!g_menu_open) return;

    ImGui::SetNextWindowPos(ImVec2(20, 80), ImGuiCond_Once);
    ImGui::SetNextWindowSize(ImVec2(io.DisplaySize.x - 40, 360), ImGuiCond_Once);
    ImGui::Begin("ZennKuy", nullptr,
        ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoCollapse);

    const char* tabs[] = { "Google Login", "Info" };
    ImGui::BeginTabBar("##tabs");
    for (int i = 0; i < 2; i++) {
        if (ImGui::BeginTabItem(tabs[i])) { g_tab = i; ImGui::EndTabItem(); }
    }
    ImGui::EndTabBar();
    ImGui::Separator();

    if (g_tab == 0) {
        ImGui::TextWrapped("Error 10 fix: open Google in Chrome. MAC / RID / GID live in Settings.");
        ImGui::Spacing();
        ImGui::TextColored(ImVec4(0.4f, 1.0f, 0.4f, 1.0f),
            "Tap Start Resolving to pick a Google account.");
        ImGui::Spacing();
        if (ImGui::Button("Start Resolving", ImVec2(-1, 70))) {
            call_bridge("startResolving");
        }
    } else {
        ImGui::Text("ZennKuy");
        ImGui::TextWrapped("Spoof fields were removed from this menu. Use launcher Settings.");
    }
    ImGui::End();
}

static EGLBoolean my_eglSwapBuffers(EGLDisplay display, EGLSurface surface) {
    if (!g_imgui_ready) imgui_init();
    if (g_imgui_ready) {
        EGLint w = 0, h = 0;
        eglQuerySurface(display, surface, EGL_WIDTH,  &w);
        eglQuerySurface(display, surface, EGL_HEIGHT, &h);
        ImGuiIO& io = ImGui::GetIO();
        if (w > 0 && h > 0) io.DisplaySize = ImVec2((float)w, (float)h);
        ImGui_ImplOpenGL3_NewFrame();
        ImGui_ImplAndroid_NewFrame();
        ImGui::NewFrame();
        render_menu();
        ImGui::Render();
        ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
    }
    return g_orig_eglSwapBuffers(display, surface);
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_rtsoft_growtopia_Main_nativeOnTouch(JNIEnv*, jclass,
    jint action, jfloat x, jfloat y)
{
    if (!g_imgui_ready) return;
    ImGuiIO& io = ImGui::GetIO();
    switch (action & AMOTION_EVENT_ACTION_MASK) {
        case AMOTION_EVENT_ACTION_DOWN:
        case AMOTION_EVENT_ACTION_POINTER_DOWN:
            io.AddMousePosEvent(x, y);
            io.AddMouseButtonEvent(0, true);
            break;
        case AMOTION_EVENT_ACTION_UP:
        case AMOTION_EVENT_ACTION_POINTER_UP:
        case AMOTION_EVENT_ACTION_CANCEL:
            io.AddMouseButtonEvent(0, false);
            break;
        case AMOTION_EVENT_ACTION_MOVE:
            io.AddMousePosEvent(x, y);
            break;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_rtsoft_growtopia_Main_isImGuiCapturingInput(JNIEnv*, jclass)
{
    if (!g_imgui_ready) return JNI_FALSE;
    return ImGui::GetIO().WantCaptureMouse ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_jvm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) return -1;
    jclass cls = env->FindClass("com/rtsoft/growtopia/ZennKuyBridge");
    if (cls) {
        g_bridge_class = env->NewGlobalRef(cls);
        env->DeleteLocalRef(cls);
        LOGI("ZennKuyBridge class cached");
    } else {
        LOGE("ZennKuyBridge class not found");
        env->ExceptionClear();
    }
    void* old = nullptr;
    for (int attempt = 0; attempt < 5 && !old; attempt++) {
        if (attempt > 0) usleep(50000);
        old = got_hook("libgrowtopia.so", "eglSwapBuffers", (void*)my_eglSwapBuffers);
    }
    if (old) {
        g_orig_eglSwapBuffers = (eglSwapBuffers_t)old;
        LOGI("eglSwapBuffers hooked in libgrowtopia.so");
    } else {
        old = got_hook("libEGL.so", "eglSwapBuffers", (void*)my_eglSwapBuffers);
        if (old) {
            g_orig_eglSwapBuffers = (eglSwapBuffers_t)old;
            LOGI("eglSwapBuffers hooked in libEGL.so (fallback)");
        } else {
            g_orig_eglSwapBuffers = (eglSwapBuffers_t)dlsym(RTLD_DEFAULT, "eglSwapBuffers");
            LOGE("GOT hook failed; ImGui overlay disabled");
        }
    }
    return JNI_VERSION_1_6;
}

} // extern "C"
