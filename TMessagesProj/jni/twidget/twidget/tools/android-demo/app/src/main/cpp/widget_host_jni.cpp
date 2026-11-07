// JNI surface of org.telegram.widgets.WidgetHostNative: widget-sandbox's C++ WidgetHost (QuickJS, the trusted
// validator, the sandbox engine) for one widget, over a renderer that lives in Java. JavaRenderer is the
// twidget::Renderer the host sees: it forwards each call to the Java bridge on the host's thread, attached to the
// VM for the thread's lifetime, and the bridge posts it to the main thread, where TwidgetCore runs. The renderer's
// messages come back through rendererMessage, from the main thread.

#include "twidget/json.h"
#include "twidget/renderer.h"
#include "twidget/resources.h"
#include "twidget/widget_host.h"

#include <android/log.h>
#include <jni.h>

#include <exception>
#include <memory>
#include <mutex>
#include <string>

#define TAG "twidget-host"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using twidget::Json;

namespace {

JavaVM* g_vm = nullptr;

/// The NDK's jni.h takes a JNIEnv** here, the JDK's (which type-checks this off device) a void**.
template <class VM>
jint attach(VM* vm, JNIEnv** env) {
    if constexpr (requires { vm->AttachCurrentThread(env, nullptr); }) return vm->AttachCurrentThread(env, nullptr);
    else return vm->AttachCurrentThread(reinterpret_cast<void**>(env), nullptr);
}

/// The calling thread's JNIEnv, attaching a native thread (the host's) until it ends.
JNIEnv* env_here() {
    struct Attachment {
        bool attached = false;
        ~Attachment() {
            if (attached && g_vm) g_vm->DetachCurrentThread();
        }
    };
    thread_local Attachment attachment;
    JNIEnv* env = nullptr;
    if (!g_vm) return nullptr;
    if (g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_OK) return env;
    if (attach(g_vm, &env) != JNI_OK) return nullptr;
    attachment.attached = true;
    return env;
}

bool clear_exception(JNIEnv* env) {
    if (!env->ExceptionCheck()) return false;
    env->ExceptionDescribe();
    env->ExceptionClear();
    return true;
}

// JNI's *StringUTF* functions speak modified UTF-8, which writes characters outside the BMP as surrogate pairs;
// widget-sandbox speaks standard UTF-8. Strings cross as UTF-16 instead, as in TwidgetCore's jni.cpp.
std::string get_string(JNIEnv* env, jstring s) {
    if (!s) return {};
    const jchar* chars = env->GetStringChars(s, nullptr);
    jsize n = env->GetStringLength(s);
    std::string out;
    out.reserve(size_t(n));
    for (jsize i = 0; chars && i < n; i++) {
        uint32_t c = chars[i];
        if (c >= 0xD800 && c < 0xDC00 && i + 1 < n && chars[i + 1] >= 0xDC00 && chars[i + 1] < 0xE000) {
            c = 0x10000 + ((c - 0xD800) << 10) + (chars[i + 1] - 0xDC00);
            i++;
        } else if (c >= 0xD800 && c < 0xE000) {
            c = 0xFFFD;
        }
        if (c < 0x80) {
            out.push_back(char(c));
        } else if (c < 0x800) {
            out.push_back(char(0xC0 | (c >> 6)));
            out.push_back(char(0x80 | (c & 0x3F)));
        } else if (c < 0x10000) {
            out.push_back(char(0xE0 | (c >> 12)));
            out.push_back(char(0x80 | ((c >> 6) & 0x3F)));
            out.push_back(char(0x80 | (c & 0x3F)));
        } else {
            out.push_back(char(0xF0 | (c >> 18)));
            out.push_back(char(0x80 | ((c >> 12) & 0x3F)));
            out.push_back(char(0x80 | ((c >> 6) & 0x3F)));
            out.push_back(char(0x80 | (c & 0x3F)));
        }
    }
    if (chars) env->ReleaseStringChars(s, chars);
    return out;
}

jstring new_string(JNIEnv* env, const std::string& s) {
    std::u16string units;
    units.reserve(s.size());
    const auto* p = reinterpret_cast<const unsigned char*>(s.data());
    size_t n = s.size(), i = 0;
    while (i < n) {
        uint32_t c = p[i];
        size_t len = c < 0x80 ? 1 : (c >> 5) == 0x6 ? 2 : (c >> 4) == 0xE ? 3 : (c >> 3) == 0x1E ? 4 : 0;
        if (len == 0 || i + len > n) {
            units.push_back(char16_t(0xFFFD));
            i++;
            continue;
        }
        if (len > 1) c &= 0xFFu >> (len + 1);
        for (size_t k = 1; k < len; k++) c = (c << 6) | (p[i + k] & 0x3F);
        i += len;
        if (c >= 0x10000) {
            c -= 0x10000;
            units.push_back(char16_t(0xD800 + (c >> 10)));
            units.push_back(char16_t(0xDC00 + (c & 0x3FF)));
        } else {
            units.push_back(char16_t(c));
        }
    }
    return env->NewString(reinterpret_cast<const jchar*>(units.data()), jsize(units.size()));
}

/// The renderer the host sees: every call goes to WidgetHostNative.RendererBridge, which runs it on the main thread.
class JavaRenderer final : public twidget::Renderer {
public:
    JavaRenderer(JNIEnv* env, jobject bridge) : bridge_(env->NewGlobalRef(bridge)) {
        jclass cls = env->GetObjectClass(bridge);
        load_ = env->GetMethodID(cls, "load", "()V");
        send_ = env->GetMethodID(cls, "send", "(Ljava/lang/String;)V");
        set_theme_ = env->GetMethodID(cls, "setTheme", "(Ljava/lang/String;)V");
        set_width_ = env->GetMethodID(cls, "setWidth", "(D)V");
        destroy_ = env->GetMethodID(cls, "destroy", "()V");
        env->DeleteLocalRef(cls);
    }
    ~JavaRenderer() override {
        if (JNIEnv* env = env_here()) env->DeleteGlobalRef(bridge_);
    }

    void load() override { call(load_); }
    void send(const std::string& json) override { call(send_, json); }
    void setTheme(const std::string& theme) override { call(set_theme_, theme); }
    void setWidth(double width) override {
        JNIEnv* env = env_here();
        if (!env) return;
        env->CallVoidMethod(bridge_, set_width_, jdouble(width));
        clear_exception(env);
    }
    void destroy() override { call(destroy_); }

private:
    void call(jmethodID method) {
        JNIEnv* env = env_here();
        if (!env) return;
        env->CallVoidMethod(bridge_, method);
        clear_exception(env);
    }
    void call(jmethodID method, const std::string& text) {
        JNIEnv* env = env_here();
        if (!env) return;
        jstring s = new_string(env, text);
        env->CallVoidMethod(bridge_, method, s);
        env->DeleteLocalRef(s);
        clear_exception(env);
    }

    jobject bridge_;
    jmethodID load_, send_, set_theme_, set_width_, destroy_;
};

struct Host {
    std::unique_ptr<twidget::WidgetHost> host;
    JavaRenderer* renderer = nullptr;  // owned by host
    jobject listener = nullptr;
    jmethodID on_diagnostics = nullptr;
};

/// The WidgetJS bundles, loaded once for every widget.
std::shared_ptr<twidget::SandboxResources> resources_for(const std::string& dir) {
    static std::mutex lock;
    static std::string loaded_dir;
    static std::shared_ptr<twidget::SandboxResources> resources;
    std::lock_guard l(lock);
    if (!resources || loaded_dir != dir) {
        resources = twidget::SandboxResources::fromDirectory(dir);
        loaded_dir = dir;
    }
    return resources;
}

std::string diagnostics_json(const twidget::WidgetDiagnostics& d) {
    Json o = Json::object();
    o.set("height", d.height).set("loaded", d.loaded).set("rendered", d.rendered).set("nodes", d.nodes);
    o.set("ops", d.stats.ops).set("jsMs", d.stats.jsMs).set("memoryBytes", double(d.stats.memoryBytes));
    if (d.loadedMs) o.set("loadedMs", *d.loadedMs);
    if (d.firstContentMs) o.set("firstContentMs", *d.firstContentMs);
    if (d.dead) o.set("dead", *d.dead);
    if (d.lastError) o.set("error", *d.lastError);
    return o.dump();
}

Host* host_of(jlong h) {
    return reinterpret_cast<Host*>(h);
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_org_telegram_widgets_WidgetHostNative_create(JNIEnv* env, jclass, jstring html, jstring theme, jdouble width, jdouble dpr,
                                                                         jstring resources_dir, jobject renderer, jobject listener) {
    if (!g_vm) env->GetJavaVM(&g_vm);
    try {
        auto h = std::make_unique<Host>();
        h->listener = env->NewGlobalRef(listener);
        jclass cls = env->GetObjectClass(listener);
        h->on_diagnostics = env->GetMethodID(cls, "onDiagnostics", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(cls);

        twidget::WidgetHostOptions options;
        options.devicePixelRatio = dpr;
        options.features = {"webAnimations"};
        JavaRenderer* created = nullptr;
        jobject bridge = renderer;
        auto factory = [&created, env, bridge](std::shared_ptr<twidget::SandboxResources>, const std::string&, double) -> std::unique_ptr<twidget::Renderer> {
            auto r = std::make_unique<JavaRenderer>(env, bridge);
            created = r.get();
            return r;
        };
        h->host = std::make_unique<twidget::WidgetHost>(get_string(env, html), get_string(env, theme), width, options,
                                                        resources_for(get_string(env, resources_dir)), factory);
        h->renderer = created;
        Host* raw = h.get();
        h->host->onChange = [raw](const twidget::WidgetDiagnostics& d) {
            JNIEnv* e = env_here();
            if (!e || !raw->on_diagnostics) return;
            jstring s = new_string(e, diagnostics_json(d));
            e->CallVoidMethod(raw->listener, raw->on_diagnostics, s);
            e->DeleteLocalRef(s);
            clear_exception(e);
        };
        return reinterpret_cast<jlong>(h.release());
    } catch (const std::exception& e) {
        LOGE("create: %s", e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_start(JNIEnv*, jclass, jlong h) {
    if (Host* host = host_of(h)) host->host->start();
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_setTheme(JNIEnv* env, jclass, jlong h, jstring theme) {
    if (Host* host = host_of(h)) host->host->setTheme(get_string(env, theme));
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_setVisible(JNIEnv*, jclass, jlong h, jboolean visible) {
    if (Host* host = host_of(h)) host->host->setVisible(visible);
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_destroy(JNIEnv* env, jclass, jlong h) {
    Host* host = host_of(h);
    if (!host) return;
    // Joins the host and engine threads: nothing calls the listener or the renderer after it.
    host->host->destroy();
    host->host.reset();
    env->DeleteGlobalRef(host->listener);
    delete host;
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_rendererMessage(JNIEnv* env, jclass, jlong h, jstring json) {
    Host* host = host_of(h);
    if (!host || !host->renderer || !host->renderer->onMessage) return;
    host->renderer->onMessage(get_string(env, json));
}

JNIEXPORT void JNICALL Java_org_telegram_widgets_WidgetHostNative_rendererDiag(JNIEnv* env, jclass, jlong h, jstring kind, jstring detail) {
    Host* host = host_of(h);
    if (!host || !host->renderer || !host->renderer->onDiag) return;
    host->renderer->onDiag(get_string(env, kind), get_string(env, detail));
}

} // extern "C"
