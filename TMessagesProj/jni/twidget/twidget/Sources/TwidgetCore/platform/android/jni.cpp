// JNI surface of the litehtml renderer (org.telegram.widgetsandbox.litehtml.LitehtmlNative)
// and the Backend that calls back into LitehtmlBackend.kt for fonts, text
// metrics, image decoding and canvas bitmaps (android.graphics — there is no
// NDK text API). One Renderer per widget behind an opaque handle; every
// call happens on the Android main thread (the Kotlin side guarantees it), so
// the document needs no lock and the JNIEnv of the current entry serves the
// callbacks. Exceptions are caught and logged, never thrown into the JVM.

#include "filters.h"
#include "renderer.h"

#include <android/bitmap.h>
#include <android/log.h>
#include <jni.h>

#include <algorithm>
#include <cmath>
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

// JNI's *StringUTF* functions speak modified UTF-8, which writes characters outside the BMP (emoji) as
// surrogate pairs and NUL as two bytes; the core speaks standard UTF-8. Strings cross as UTF-16 instead.
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

/// Fonts, text metrics and images through LitehtmlBackend.kt.
class JniBackend : public twlite::Backend {
public:
    JniBackend(JNIEnv* env, jobject backend) {
        backend_ = env->NewGlobalRef(backend);
        jclass cls = env->GetObjectClass(backend);
        create_font_ = env->GetMethodID(cls, "createFont", "(Ljava/lang/String;FIZLjava/lang/String;)[F");
        delete_font_ = env->GetMethodID(cls, "deleteFont", "(I)V");
        text_width_ = env->GetMethodID(cls, "textWidth", "(ILjava/lang/String;)F");
        load_image_ = env->GetMethodID(cls, "loadImage", "(ILjava/lang/String;)[F");
        // Canvas is optional: a backend without these methods leaves canvases transparent.
        canvas_run_ = optional_method(env, cls, "canvasRun", "(IIILjava/lang/String;)V");
        canvas_read_ = optional_method(env, cls, "canvasRead", "(IIIII)[B");
        canvas_release_ = optional_method(env, cls, "canvasRelease", "(I)V");
        env->DeleteLocalRef(cls);
    }
    ~JniBackend() override {
        if (g_env) g_env->DeleteGlobalRef(backend_);
    }

    int create_font(const std::string& families, float size, int weight, bool italic, const std::string& features, twlite::FontMetrics& out) override {
        JNIEnv* env = g_env;
        if (!env) return 0;
        jstring fam = new_string(env, families);
        // Paint.setFontFeatureSettings takes CSS syntax: `tnum=1,zero=1` becomes `'tnum' 1, 'zero' 1`.
        std::string css;
        for (size_t start = 0; start < features.size();) {
            size_t end = std::min(features.find(',', start), features.size());
            if (end - start > 5 && features[start + 4] == '=') {
                if (!css.empty()) css += ", ";
                css += "'" + features.substr(start, 4) + "' " + features.substr(start + 5, end - start - 5);
            }
            start = end + 1;
        }
        jstring feat = new_string(env, css);
        auto arr = (jfloatArray)env->CallObjectMethod(backend_, create_font_, fam, (jfloat)size, (jint)weight, (jboolean)italic, feat);
        env->DeleteLocalRef(fam);
        env->DeleteLocalRef(feat);
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
    void canvas_run(int id, int w, int h, const std::vector<twlite::CanvasOp>& ops) override {
        JNIEnv* env = g_env;
        if (!env || !canvas_run_) return;
        jstring json = new_string(env, twlite::to_json(ops));
        env->CallVoidMethod(backend_, canvas_run_, (jint)id, (jint)w, (jint)h, json);
        env->DeleteLocalRef(json);
        clear_exception(env);
    }
    bool canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) override {
        JNIEnv* env = g_env;
        if (!env || !canvas_read_) return false;
        auto arr = (jbyteArray)env->CallObjectMethod(backend_, canvas_read_, (jint)id, (jint)x, (jint)y, (jint)w, (jint)h);
        if (clear_exception(env) || !arr) return false;
        jsize n = env->GetArrayLength(arr);
        bool ok = size_t(n) == size_t(w) * h * 4;
        if (ok) {
            rgba.resize(size_t(n));
            env->GetByteArrayRegion(arr, 0, n, reinterpret_cast<jbyte*>(rgba.data()));
        }
        env->DeleteLocalRef(arr);
        return ok;
    }
    void canvas_release(int id) override {
        if (!g_env || !canvas_release_) return;
        g_env->CallVoidMethod(backend_, canvas_release_, (jint)id);
        clear_exception(g_env);
    }

private:
    static jmethodID optional_method(JNIEnv* env, jclass cls, const char* name, const char* signature) {
        jmethodID id = env->GetMethodID(cls, name, signature);
        if (env->ExceptionCheck()) env->ExceptionClear();
        return id;
    }
    static bool clear_exception(JNIEnv* env) {
        if (!env->ExceptionCheck()) return false;
        env->ExceptionDescribe();
        env->ExceptionClear();
        return true;
    }

    jobject backend_;
    jmethodID create_font_, delete_font_, text_width_, load_image_;
    jmethodID canvas_run_ = nullptr, canvas_read_ = nullptr, canvas_release_ = nullptr;
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

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_setMaxHeight(JNIEnv* env, jclass, jlong h, jdouble height) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "setMaxHeight", [&](Handle& hd) {
        std::vector<Json> out;
        hd.renderer->set_max_height(height, out);
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

/// A mouse or touch pointer with modifiers (bit0 alt, bit1 ctrl, bit2 meta, bit3 shift); `mouse` sends no touch events.
/// `button` 2 is a mouse's secondary button.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_pointer(JNIEnv* env, jclass, jlong h, jint action, jdouble x, jdouble y, jint pointer, jboolean primary, jboolean mouse, jint modifiers, jint button) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "pointer", [&](Handle& hd) { return messages(hd.renderer->touch(action, x, y, pointer, primary, mouse, modifiers, button)); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_hover(JNIEnv* env, jclass, jlong h, jint action, jdouble x, jdouble y, jint modifiers) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "hover", [&](Handle& hd) { return messages(hd.renderer->hover(action, x, y, modifiers)); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_wheel(JNIEnv* env, jclass, jlong h, jdouble x, jdouble y, jdouble dx, jdouble dy, jint mode, jint modifiers) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "wheel", [&](Handle& hd) { return messages(hd.renderer->wheel(x, y, dx, dy, mode, modifiers)); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_key(JNIEnv* env, jclass, jlong h, jstring type, jstring key, jstring code, jboolean repeat, jint modifiers) {
    EnvScope scope(env);
    std::string t = get_string(env, type), k = get_string(env, key), c = get_string(env, code);
    return new_string(env, guarded<std::string>(h, "[]", "key", [&](Handle& hd) { return messages(hd.renderer->key(t, k, c, repeat, modifiers)); }));
}

/// The text control being edited (Renderer::EditorSpec as JSON), or "null": the view covers it with an
/// EditText of its own, which brings the keyboard and the input method.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_editor(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "null", "editor", [&](Handle& hd) { return hd.renderer->editor_json(); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_edit(JNIEnv* env, jclass, jlong h, jlong generation, jstring text, jstring inputType) {
    EnvScope scope(env);
    std::string t = get_string(env, text), i = get_string(env, inputType);
    return new_string(env, guarded<std::string>(h, "[]", "edit", [&](Handle& hd) { return messages(hd.renderer->edit(uint64_t(generation), t, i.empty() ? "insertText" : i)); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_endEditing(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "end_editing", [&](Handle& hd) { return messages(hd.renderer->end_editing()); }));
}

/// One display frame (Choreographer): animations advance. Call it every frame while wantsTick holds,
/// otherwise after nextTickIn seconds.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_tick(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "tick", [&](Handle& hd) { return messages(hd.renderer->tick()); }));
}

JNIEXPORT jboolean JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_wantsTick(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<bool>(h, false, "wantsTick", [&](Handle& hd) { return hd.renderer->wants_tick(); });
}

JNIEXPORT jdouble JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_nextTickIn(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<double>(h, INFINITY, "nextTickIn", [&](Handle& hd) { return hd.renderer->next_tick_in(); });
}

JNIEXPORT void JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_setCompositing(JNIEnv* env, jclass, jlong h, jboolean enabled) {
    EnvScope scope(env);
    guarded<bool>(h, false, "setCompositing", [&](Handle& hd) {
        hd.renderer->set_compositing(enabled);
        return true;
    });
}

/// Where the last displayList differs from the one before (Renderer::damage_json): null means all.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_damage(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "null", "damage", [&](Handle& hd) { return hd.renderer->damage_json(); }));
}

/// Drops caches that are made again on demand: onTrimMemory, or a widget scrolled away.
JNIEXPORT void JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_trim(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    guarded<int>(h, 0, "trim", [&](Handle& hd) {
        hd.renderer->trim();
        return 0;
    });
}

/// The layers above the last displayList (src/compositor.h, to_json(Composition)).
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_composition(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "{\"v\":0,\"l\":[]}", "composition", [&](Handle& hd) { return twlite::to_json(hd.renderer->composition()); }));
}

/// The renderer clock, in seconds: what the layers' track starts are on.
JNIEXPORT jdouble JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_clock(JNIEnv*, jclass) {
    return twlite::Renderer::clock();
}

/// Layer `index` of the last composition as a display list; null past the end.
JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_layerContent(JNIEnv* env, jclass, jlong h, jint index) {
    EnvScope scope(env);
    std::string json = guarded<std::string>(h, "", "layerContent", [&](Handle& hd) {
        const auto& layers = hd.renderer->composition().layers;
        return index >= 0 && size_t(index) < layers.size() ? twlite::to_json(layers[size_t(index)].content) : std::string();
    });
    return json.empty() ? nullptr : new_string(env, json);
}

/// (width << 32) | height, CSS px.
JNIEXPORT jlong JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_size(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<jlong>(h, 0, "size", [&](Handle& hd) {
        return (jlong(uint32_t(hd.renderer->width())) << 32) | jlong(uint32_t(hd.renderer->viewport_height()));
    });
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_selectedText(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "", "selected_text", [&](Handle& hd) { return hd.renderer->selected_text(); }));
}

JNIEXPORT jboolean JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_clearSelection(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return guarded<bool>(h, false, "clear_selection", [&](Handle& hd) { return hd.renderer->clear_selection(); });
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_selectableText(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "selectable_text", [&](Handle& hd) { return hd.renderer->selectable_text_json(); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_cursor(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "auto", "cursor", [&](Handle& hd) { return hd.renderer->cursor(); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_masterCss(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "", "master_css", [&](Handle& hd) { return hd.renderer->master_css(); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_themeVariables(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "{}", "theme_variables", [&](Handle& hd) { return hd.renderer->theme_variables_json(); }));
}

JNIEXPORT void JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_setResizeTracking(JNIEnv* env, jclass, jlong h, jboolean enabled, jboolean prefersBottom) {
    EnvScope scope(env);
    guarded<bool>(h, false, "set_resize_tracking", [&](Handle& hd) {
        hd.renderer->set_resize_tracking(enabled, prefersBottom);
        return true;
    });
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_imageReady(JNIEnv* env, jclass, jlong h, jint image, jfloat width, jfloat height) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "[]", "image_ready", [&](Handle& hd) { return messages(hd.renderer->image_ready(image, width, height)); }));
}

JNIEXPORT jstring JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_html(JNIEnv* env, jclass, jlong h) {
    EnvScope scope(env);
    return new_string(env, guarded<std::string>(h, "", "html", [&](Handle& hd) { return hd.renderer->last_html(); }));
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

/// Runs a FilterPush's filters (its "fl", display_list.h's Filter as [kind, amount, x, y, argb]) over an
/// ARGB_8888 bitmap's premultiplied pixels; `scale` is its px per CSS px.
JNIEXPORT void JNICALL Java_org_telegram_widgetsandbox_litehtml_LitehtmlNative_filterPixels(JNIEnv* env, jclass, jobject bitmap, jstring filters, jfloat scale) {
    auto parsed = Json::parse(get_string(env, filters));
    if (!parsed) return;
    twlite::Filters steps;
    for (const Json& f : parsed->arr()) {
        twlite::Filter step;
        int kind = f[size_t(0)].asInt(-1);
        if (kind < 0 || kind > int(twlite::Filter::DropShadow)) continue;
        step.kind = twlite::Filter::Kind(kind);
        step.amount = float(f[size_t(1)].asNumber());
        step.x = float(f[size_t(2)].asNumber());
        step.y = float(f[size_t(3)].asNumber());
        uint32_t argb = uint32_t(f[size_t(4)].asNumber());
        step.color = twlite::Color{uint8_t(argb >> 16), uint8_t(argb >> 8), uint8_t(argb), uint8_t(argb >> 24)};
        steps.push_back(step);
    }
    AndroidBitmapInfo info;
    void* pixels = nullptr;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) return;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return;
    twlite::filter_pixels(static_cast<uint8_t*>(pixels), int(info.width), int(info.height), info.stride, false, steps, scale);
    AndroidBitmap_unlockPixels(env, bitmap);
}

} // extern "C"
