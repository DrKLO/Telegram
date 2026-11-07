// JNI surface of the litehtml renderer (org.telegram.widgetsandbox.litehtml.LitehtmlNative)
// and the Backend that calls back into LitehtmlBackend.kt for fonts, text
// metrics and image decoding (android.graphics.Paint / BitmapFactory — there
// is no NDK text API). One Renderer per widget behind an opaque handle; every
// call happens on the Android main thread (the Kotlin side guarantees it), so
// the document needs no lock and the JNIEnv of the current entry serves the
// callbacks. Exceptions are caught and logged, never thrown into the JVM.

#include "../renderer.h"

#include <android/log.h>
#include <jni.h>

#include <exception>
#include <memory>
#include <string>
#include <vector>

#define TAG "twidget-litehtml"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

using twidget::Json;

namespace {

thread_local JNIEnv* g_env = nullptr;

struct EnvScope {
    JNIEnv* prev;
    explicit EnvScope(JNIEnv* env) : prev(g_env) { g_env = env; }
    ~EnvScope() { g_env = prev; }
};

std::string get_string(JNIEnv* env, jstring s) {
    if (!s) return {};
    const char* chars = env->GetStringUTFChars(s, nullptr);
    std::string out = chars ? chars : "";
    if (chars) env->ReleaseStringUTFChars(s, chars);
    return out;
}

jstring new_string(JNIEnv* env, const std::string& s) {
    return env->NewStringUTF(s.c_str());
}

/// Fonts, text metrics and images through LitehtmlBackend.kt.
class JniBackend : public twlite::Backend {
public:
    JniBackend(JNIEnv* env, jobject backend) {
        backend_ = env->NewGlobalRef(backend);
        jclass cls = env->GetObjectClass(backend);
        create_font_ = env->GetMethodID(cls, "createFont", "(Ljava/lang/String;FIZ)[F");
        delete_font_ = env->GetMethodID(cls, "deleteFont", "(I)V");
        text_width_ = env->GetMethodID(cls, "textWidth", "(ILjava/lang/String;)F");
        load_image_ = env->GetMethodID(cls, "loadImage", "(ILjava/lang/String;)[F");
        env->DeleteLocalRef(cls);
    }
    ~JniBackend() override {
        if (g_env) g_env->DeleteGlobalRef(backend_);
    }

    int create_font(const std::string& families, float size, int weight, bool italic, twlite::FontMetrics& out) override {
        JNIEnv* env = g_env;
        if (!env) return 0;
        jstring fam = new_string(env, families);
        auto arr = (jfloatArray)env->CallObjectMethod(backend_, create_font_, fam, (jfloat)size, (jint)weight, (jboolean)italic);
        env->DeleteLocalRef(fam);
        if (clear_exception(env) || !arr) return 0;
        jfloat v[6] = {0, 0, 0, 0, 0, 0};
        jsize n = env->GetArrayLength(arr);
        env->GetFloatArrayRegion(arr, 0, n < 6 ? n : 6, v);
        env->DeleteLocalRef(arr);
        out.ascent = v[1];
        out.descent = v[2];
        out.height = v[3];
        out.x_height = v[4];
        out.zero_width = v[5];
        return int(v[0]);
    }
    void delete_font(int font) override {
        if (!g_env) return;
        g_env->CallVoidMethod(backend_, delete_font_, (jint)font);
        clear_exception(g_env);
    }
    float text_width(int font, const std::string& text) override {
        JNIEnv* env = g_env;
        if (!env) return 0;
        jstring s = new_string(env, text);
        jfloat w = env->CallFloatMethod(backend_, text_width_, (jint)font, s);
        env->DeleteLocalRef(s);
        return clear_exception(env) ? 0.0f : float(w);
    }
    bool load_image(int id, const std::string& url, float& w, float& h) override {
        JNIEnv* env = g_env;
        if (!env) return false;
        jstring s = new_string(env, url);
        auto arr = (jfloatArray)env->CallObjectMethod(backend_, load_image_, (jint)id, s);
        env->DeleteLocalRef(s);
        if (clear_exception(env) || !arr) return false;
        jfloat v[2] = {0, 0};
        env->GetFloatArrayRegion(arr, 0, 2, v);
        env->DeleteLocalRef(arr);
        w = v[0];
        h = v[1];
        return w > 0 && h > 0;
    }

private:
    static bool clear_exception(JNIEnv* env) {
        if (!env->ExceptionCheck()) return false;
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }

    jobject backend_;
    jmethodID create_font_, delete_font_, text_width_, load_image_;
};

struct Handle {
    std::unique_ptr<JniBackend> backend;
    std::unique_ptr<twlite::Renderer> renderer;
    size_t last_display_ops = 0;
};

Handle* handle_of(jlong h) {
    return reinterpret_cast<Handle*>(h);
}

std::string messages(const std::vector<Json>& out) {
    Json arr = Json::array();
    for (const Json& m : out) arr.push(m);
    return arr.dump();
}

template <typename T, typename F>
T guarded(jlong h, T def, const char* what, F f) {
    Handle* handle = handle_of(h);
    if (!handle || !handle->renderer) return def;
    try {
        return f(*handle);
    } catch (const std::exception& e) {
        LOGE("%s: %s", what, e.what());
    } catch (...) {
        LOGE("%s: unknown exception", what);
    }
    return def;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_create(JNIEnv* env, jclass, jdouble width, jdouble dpr, jstring theme, jobject backend) {
    EnvScope scope(env);
    try {
        auto h = std::make_unique<Handle>();
        h->backend = std::make_unique<JniBackend>(env, backend);
        h->renderer = std::make_unique<twlite::Renderer>(width, dpr, get_string(env, theme) == "dark", *h->backend);
        return reinterpret_cast<jlong>(h.release());
    } catch (const std::exception& e) {
        LOGE("create: %s", e.what());
        return 0;
    }
}

JNIEXPORT void JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_destroy(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    delete handle_of(h);
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_receive(JNIEnv* env, jclass, jlong h, jstring json) {
    EnvScope scope(env);
    std::string text = get_string(env, json);
    return new_string(env, guarded<std::string>(h, "[]", "receive", [&](Handle& hd) {
        auto msg = Json::parse(text);
        if (!msg) return std::string("[{\"type\":\"violation\",\"detail\":\"bad message\"}]");
        return messages(hd.renderer->receive(*msg));
    }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_setWidth(JNIEnv* env, jclass, jlong h, jdouble width) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "setWidth", [&](Handle& hd) {
        std::vector<Json> out;
        hd.renderer->set_width(width, out);
        return messages(out);
    }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_setTheme(JNIEnv* env, jclass, jlong h, jstring theme) {
    EnvScope scope(env);
    bool dark = get_string(env, theme) == "dark";
    return new_string(env, guarded<std::string>(h, "[]", "setTheme", [&](Handle& hd) {
        std::vector<Json> out;
        hd.renderer->set_theme(dark, out);
        return messages(out);
    }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_touch(JNIEnv* env, jclass, jlong h, jint action, jdouble x, jdouble y, jint pointer, jboolean primary) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "touch", [&](Handle& hd) { return messages(hd.renderer->touch(action, x, y, pointer, primary)); }));
}

/// (width << 32) | height, CSS px.
JNIEXPORT jlong JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_size(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<jlong>(h, 0, "size", [&](Handle& hd) {
        return (jlong(uint32_t(hd.renderer->width())) << 32) | jlong(uint32_t(hd.renderer->viewport_height()));
    });
}

JNIEXPORT jboolean JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_dirty(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<bool>(h, false, "dirty", [&](Handle& hd) { return hd.renderer->dirty(); });
}

/// The display list of the current document as JSON (display_list.h), CSS px.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_displayList(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "displayList", [&](Handle& hd) {
        twlite::DisplayList list = hd.renderer->paint();
        hd.last_display_ops = list.size();
        return twlite::to_json(list);
    }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_stats(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "{}", "stats", [&](Handle& hd) {
        const auto& s = hd.renderer->stats();
        Json o = Json::object();
        o.set("serializeMs", s.serialize_ms).set("parseLayoutMs", s.parse_ms).set("passes", s.passes);
        o.set("htmlBytes", double(s.html_bytes)).set("builds", s.builds).set("displayOps", double(hd.last_display_ops));
        o.set("viewportHeight", hd.renderer->viewport_height());
        return o.dump();
    }));
}

} // extern "C"
