#include <jni.h>

#include <exception>
#include <limits>
#include <new>
#include <stdexcept>

#include "PrismaEngine.h"

namespace {

void throwJava(JNIEnv* env, const char* type, const char* message) {
    if (env->ExceptionCheck()) return;
    jclass exception = env->FindClass(type);
    if (exception != nullptr) {
        env->ThrowNew(exception, message);
        env->DeleteLocalRef(exception);
    }
}

void translateException(JNIEnv* env) {
    try {
        throw;
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "Native allocation failed");
    } catch (const std::invalid_argument& error) {
        throwJava(env, "java/lang/IllegalArgumentException", error.what());
    } catch (const std::exception& error) {
        throwJava(env, "java/lang/IllegalStateException", error.what());
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "Unknown libprisma error");
    }
}

class JavaString {
public:
    JavaString(JNIEnv* env, jstring string) : env_(env), string_(string) {
        static_assert(sizeof(jchar) == sizeof(char16_t));
        if (string_ == nullptr) {
            throwJava(env_, "java/lang/NullPointerException", "String is null");
            return;
        }
        length_ = env_->GetStringLength(string_);
        chars_ = env_->GetStringChars(string_, nullptr);
    }

    ~JavaString() {
        if (chars_ != nullptr) env_->ReleaseStringChars(string_, chars_);
    }

    JavaString(const JavaString&) = delete;
    JavaString& operator=(const JavaString&) = delete;

    bool valid() const { return chars_ != nullptr; }
    std::u16string value() const {
        std::u16string result;
        result.reserve(static_cast<size_t>(length_));
        for (jsize i = 0; i < length_; ++i) result.push_back(chars_[i]);
        return result;
    }

private:
    JNIEnv* env_;
    jstring string_;
    const jchar* chars_ = nullptr;
    jsize length_ = 0;
};

prisma_android::Engine* engine(JNIEnv* env, jlong handle) {
    if (handle == 0) {
        throwJava(env, "java/lang/IllegalStateException", "Highlighter is closed");
        return nullptr;
    }
    return reinterpret_cast<prisma_android::Engine*>(static_cast<uintptr_t>(handle));
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL Java_org_telegram_utils_code_highlight_PrismaHighlighter_nativeCreate(
    JNIEnv* env, jclass, jbyteArray grammars) {
    if (grammars == nullptr) {
        throwJava(env, "java/lang/NullPointerException", "Grammars are null");
        return 0;
    }
    try {
        const jsize length = env->GetArrayLength(grammars);
        std::string bytes(static_cast<size_t>(length), '\0');
        if (length > 0) {
            env->GetByteArrayRegion(grammars, 0, length, reinterpret_cast<jbyte*>(bytes.data()));
            if (env->ExceptionCheck()) return 0;
        }
        auto instance = std::make_unique<prisma_android::Engine>(bytes);
        return static_cast<jlong>(reinterpret_cast<uintptr_t>(instance.release()));
    } catch (...) {
        translateException(env);
        return 0;
    }
}

extern "C" JNIEXPORT jintArray JNICALL
Java_org_telegram_utils_code_highlight_PrismaHighlighter_nativeHighlight(JNIEnv* env, jclass, jlong handle,
                                                              jstring text, jstring language) {
    auto* instance = engine(env, handle);
    if (instance == nullptr) return nullptr;
    try {
        JavaString source(env, text);
        if (!source.valid()) return nullptr;
        JavaString languageName(env, language);
        if (!languageName.valid()) return nullptr;
        const auto ranges = instance->highlight(source.value(), languageName.value());
        const jsize length = static_cast<jsize>(ranges.size());
        jintArray result = env->NewIntArray(length);
        if (result != nullptr && length > 0) {
            static_assert(sizeof(jint) == sizeof(int32_t));
            env->SetIntArrayRegion(result, 0, length, reinterpret_cast<const jint*>(ranges.data()));
        }
        return result;
    } catch (...) {
        translateException(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_telegram_utils_code_highlight_PrismaHighlighter_nativeGetLanguages(JNIEnv* env, jclass,
                                                                 jlong handle) {
    auto* instance = engine(env, handle);
    if (instance == nullptr) return nullptr;
    try {
        const auto languages = instance->languages();
        jclass stringClass = env->FindClass("java/lang/String");
        if (stringClass == nullptr) return nullptr;
        jobjectArray result =
            env->NewObjectArray(static_cast<jsize>(languages.size()), stringClass, nullptr);
        env->DeleteLocalRef(stringClass);
        if (result == nullptr) return nullptr;
        for (size_t i = 0; i < languages.size(); ++i) {
            // Language identifiers in grammars.dat are ASCII, not display names.
            jstring name = env->NewStringUTF(languages[i].c_str());
            if (name == nullptr) return nullptr;
            env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
            env->DeleteLocalRef(name);
            if (env->ExceptionCheck()) return nullptr;
        }
        return result;
    } catch (...) {
        translateException(env);
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_telegram_utils_code_highlight_PrismaHighlighter_nativeDestroy(JNIEnv*, jclass, jlong handle) {
    delete reinterpret_cast<prisma_android::Engine*>(static_cast<uintptr_t>(handle));
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_org_telegram_utils_code_highlight_PrismaHighlighter_nativeGetTokenNames(JNIEnv* env, jclass,
                                                                  jlong handle) {
    auto* instance = engine(env, handle);
    if (instance == nullptr) return nullptr;
    try {
        const auto& names = instance->tokenNames();
        jclass stringClass = env->FindClass("java/lang/String");
        if (stringClass == nullptr) return nullptr;
        jobjectArray result =
            env->NewObjectArray(static_cast<jsize>(names.size()), stringClass, nullptr);
        env->DeleteLocalRef(stringClass);
        if (result == nullptr) return nullptr;
        for (size_t i = 0; i < names.size(); ++i) {
            jstring name = env->NewStringUTF(names[i].c_str());
            if (name == nullptr) return nullptr;
            env->SetObjectArrayElement(result, static_cast<jsize>(i), name);
            env->DeleteLocalRef(name);
            if (env->ExceptionCheck()) return nullptr;
        }
        return result;
    } catch (...) {
        translateException(env);
        return nullptr;
    }
}
