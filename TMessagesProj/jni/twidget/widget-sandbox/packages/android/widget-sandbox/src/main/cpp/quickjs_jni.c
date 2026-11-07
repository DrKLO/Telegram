// JNI layer over quickjs-ng (through qjs_shim.h) for QuickJSContext.kt.
//
// Every string crosses the boundary as a byte[] of real UTF-8: JNI's own
// jstring helpers speak *modified* UTF-8, which corrupts supplementary
// characters (emoji are common in widgets). The Kotlin side decodes.
//
// A context is only ever used from one thread (JSThread). The JNIEnv of that
// thread is recorded on every entry (eval / call / jobs) so host functions
// called back from JS can reach the Kotlin object.

#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <android/log.h>
#include "qjs_shim.h"

#define TAG "twquickjs"

typedef struct {
    JSRuntime *rt;
    JSContext *ctx;
    JNIEnv *env;          // valid only while a native entry point is executing
    jobject self;         // global ref to the owning QuickJSContext
    int64_t deadline_ns;  // CLOCK_BOOTTIME nanoseconds (= SystemClock.elapsedRealtimeNanos)
    int64_t cpu_deadline_ns;  // CLOCK_THREAD_CPUTIME_ID nanoseconds of the thread running JS
    char *captured_name;      // a global function kept privately (the shim's dispatcher) after the global is deleted
    JSValue captured;
    int interrupted;
} Ctx;

static jclass g_bytearray_class;    // [B
static jclass g_double_class;       // java/lang/Double
static jmethodID g_double_init;     // Double(double)
static jmethodID g_double_value;    // double doubleValue()
static jclass g_arraybuffer_class;  // HostReturn$ArrayBuffer
static jfieldID g_arraybuffer_bytes;
static jclass g_null_class;         // HostReturn$Null
static jmethodID g_dispatch;        // Object dispatch(int, byte[][])
static jmethodID g_raise;           // void raise(byte[])

static int64_t now_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_BOOTTIME, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

static int64_t thread_cpu_ns(void) {
    struct timespec ts;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts);
    return (int64_t)ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

static int interrupt_handler(JSContext *ctx, void *opaque) {
    (void)ctx;
    Ctx *c = (Ctx *)opaque;
    if (now_ns() > c->deadline_ns || (c->cpu_deadline_ns != INT64_MAX && thread_cpu_ns() > c->cpu_deadline_ns)) {
        c->interrupted = 1;
        return 1;
    }
    return 0;
}

static Ctx *ctx_of(jlong handle) { return (Ctx *)(intptr_t)handle; }

static void enter(Ctx *c, JNIEnv *env) {
    c->env = env;
    // QuickJS measures recursion from the stack pointer it saw at creation;
    // re-read it on every entry (the thread is pinned, this is the belt).
    JS_UpdateStackTop(c->rt);
}

static jbyteArray bytes_from(JNIEnv *env, const char *s, size_t len) {
    jbyteArray a = (*env)->NewByteArray(env, (jsize)len);
    if (a && len) (*env)->SetByteArrayRegion(env, a, 0, (jsize)len, (const jbyte *)s);
    return a;
}

/* Raises QuickJSException in Java with the given UTF-8 description. */
static void raise_text(Ctx *c, const char *text) {
    JNIEnv *env = c->env;
    jbyteArray a = bytes_from(env, text, strlen(text));
    (*env)->CallVoidMethod(env, c->self, g_raise, a);  // throws; the exception stays pending
    (*env)->DeleteLocalRef(env, a);
}

/* Raises QuickJSException for the exception pending in the JS context. */
static void raise_pending(Ctx *c) {
    char *d = qjs_take_exception_description(c->ctx);
    raise_text(c, d ? d : "unknown error");
    free(d);
}

/* JSValue (consumed) → byte[] for strings, Double for numbers, null for everything else. */
static jobject result_to_java(Ctx *c, JSValue v) {
    JNIEnv *env = c->env;
    jobject out = NULL;
    if (qjs_is_string(v)) {
        size_t len = 0;
        const char *s = JS_ToCStringLen(c->ctx, &len, v);
        if (s) {
            out = bytes_from(env, s, len);
            qjs_free_cstring(c->ctx, s);
        }
    } else if (qjs_is_number(v)) {
        out = (*env)->NewObject(env, g_double_class, g_double_init, qjs_to_float64(c->ctx, v));
    }
    qjs_free_value(c->ctx, v);
    return out;
}

/* Single trampoline for every registered host function; `magic` selects the Kotlin closure. */
static JSValue host_trampoline(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv, int magic) {
    (void)this_val;
    Ctx *c = (Ctx *)JS_GetContextOpaque(ctx);
    JNIEnv *env = c ? c->env : NULL;
    if (!env) return JS_ThrowInternalError(ctx, "host function called outside a JNI entry");
    if ((*env)->PushLocalFrame(env, 8 + argc) < 0) return JS_ThrowInternalError(ctx, "JNI local frame");

    jobjectArray arr = (*env)->NewObjectArray(env, argc, g_bytearray_class, NULL);
    for (int i = 0; i < argc; i++) {
        jbyteArray a;
        if (qjs_is_string(argv[i]) || qjs_is_number(argv[i])) {
            size_t len = 0;
            const char *s = JS_ToCStringLen(ctx, &len, argv[i]);
            a = bytes_from(env, s ? s : "", s ? len : 0);
            if (s) qjs_free_cstring(ctx, s);
        } else {
            a = bytes_from(env, "", 0);
        }
        (*env)->SetObjectArrayElement(env, arr, i, a);
        (*env)->DeleteLocalRef(env, a);
    }

    jobject res = (*env)->CallObjectMethod(env, c->self, g_dispatch, (jint)magic, arr);
    JSValue out;
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionDescribe(env);
        (*env)->ExceptionClear(env);
        out = JS_ThrowInternalError(ctx, "host function failed");
    } else if (res == NULL) {
        out = JS_UNDEFINED;
    } else if ((*env)->IsInstanceOf(env, res, g_bytearray_class)) {
        jsize len = (*env)->GetArrayLength(env, (jbyteArray)res);
        jbyte *b = (*env)->GetByteArrayElements(env, (jbyteArray)res, NULL);
        out = qjs_new_string_len(ctx, (const char *)b, (size_t)len);
        (*env)->ReleaseByteArrayElements(env, (jbyteArray)res, b, JNI_ABORT);
    } else if ((*env)->IsInstanceOf(env, res, g_double_class)) {
        out = qjs_new_float64(ctx, (*env)->CallDoubleMethod(env, res, g_double_value));
    } else if ((*env)->IsInstanceOf(env, res, g_arraybuffer_class)) {
        jbyteArray bytes = (jbyteArray)(*env)->GetObjectField(env, res, g_arraybuffer_bytes);
        jsize len = bytes ? (*env)->GetArrayLength(env, bytes) : 0;
        jbyte *b = bytes ? (*env)->GetByteArrayElements(env, bytes, NULL) : NULL;
        out = qjs_new_array_buffer_copy(ctx, (const uint8_t *)b, (size_t)len);
        if (b) (*env)->ReleaseByteArrayElements(env, bytes, b, JNI_ABORT);
    } else if ((*env)->IsInstanceOf(env, res, g_null_class)) {
        out = JS_NULL;
    } else {
        out = JS_UNDEFINED;
    }
    (*env)->PopLocalFrame(env, NULL);
    return out;
}

// ───────────────────────── native methods ─────────────────────────

static jlong n_create(JNIEnv *env, jobject thiz, jlong memory_limit, jlong max_stack) {
    Ctx *c = (Ctx *)calloc(1, sizeof(Ctx));
    if (!c) return 0;
    c->rt = JS_NewRuntime();
    if (!c->rt) { free(c); return 0; }
    JS_SetMemoryLimit(c->rt, (size_t)memory_limit);
    JS_SetMaxStackSize(c->rt, (size_t)max_stack);
    c->ctx = JS_NewContext(c->rt);
    if (!c->ctx) { JS_FreeRuntime(c->rt); free(c); return 0; }
    JS_SetContextOpaque(c->ctx, c);
    c->self = (*env)->NewGlobalRef(env, thiz);
    c->deadline_ns = INT64_MAX;
    c->cpu_deadline_ns = INT64_MAX;
    // No module loader is installed: `import()` of anything fails inside the sandbox.
    JS_SetInterruptHandler(c->rt, interrupt_handler, c);
    return (jlong)(intptr_t)c;
}

static void n_destroy(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    if (!c) return;
    if (c->captured_name) {
        qjs_free_value(c->ctx, c->captured);
        free(c->captured_name);
    }
    JS_FreeContext(c->ctx);
    JS_FreeRuntime(c->rt);
    (*env)->DeleteGlobalRef(env, c->self);
    free(c);
}

static void n_register(JNIEnv *env, jobject thiz, jlong handle, jstring name, jint magic) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue fn = qjs_new_cfunction_magic(c->ctx, host_trampoline, n, 2, magic);
    qjs_set_global_prop(c->ctx, n, fn);  // takes ownership of fn
    (*env)->ReleaseStringUTFChars(env, name, n);
    c->env = NULL;
}

static jobject n_eval(JNIEnv *env, jobject thiz, jlong handle, jbyteArray code, jstring filename, jboolean as_module) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    jsize len = (*env)->GetArrayLength(env, code);
    char *buf = (char *)malloc((size_t)len + 1);
    if (!buf) { raise_text(c, "out of memory (eval buffer)"); c->env = NULL; return NULL; }
    (*env)->GetByteArrayRegion(env, code, 0, len, (jbyte *)buf);
    buf[len] = 0;
    const char *fn = (*env)->GetStringUTFChars(env, filename, NULL);
    JSValue v = qjs_eval(c->ctx, buf, (size_t)len, fn, as_module);
    (*env)->ReleaseStringUTFChars(env, filename, fn);
    free(buf);
    jobject out = NULL;
    if (qjs_is_exception(v)) raise_pending(c);
    else out = result_to_java(c, v);
    c->env = NULL;
    return out;
}

static jboolean n_capture_global_function(JNIEnv *env, jobject thiz, jlong handle, jstring name) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue fn = qjs_get_global_prop(c->ctx, n);
    jboolean ok = JNI_FALSE;
    if (qjs_is_function(c->ctx, fn)) {
        if (c->captured_name) {
            qjs_free_value(c->ctx, c->captured);
            free(c->captured_name);
        }
        c->captured_name = strdup(n);
        c->captured = fn;
        ok = JNI_TRUE;
    } else {
        qjs_free_value(c->ctx, fn);
    }
    (*env)->ReleaseStringUTFChars(env, name, n);
    return ok;
}

static jobject n_call(JNIEnv *env, jobject thiz, jlong handle, jstring name, jobjectArray args) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    const char *n = (*env)->GetStringUTFChars(env, name, NULL);
    JSValue fn = c->captured_name && strcmp(c->captured_name, n) == 0 ? qjs_dup_value(c->ctx, c->captured) : qjs_get_global_prop(c->ctx, n);
    jobject out = NULL;
    if (!qjs_is_function(c->ctx, fn)) {
        char msg[160];
        snprintf(msg, sizeof msg, "%s is not a function", n);
        qjs_free_value(c->ctx, fn);
        (*env)->ReleaseStringUTFChars(env, name, n);
        raise_text(c, msg);
        c->env = NULL;
        return NULL;
    }
    (*env)->ReleaseStringUTFChars(env, name, n);
    int argc = args ? (*env)->GetArrayLength(env, args) : 0;
    JSValue *argv = argc ? (JSValue *)malloc(sizeof(JSValue) * (size_t)argc) : NULL;
    for (int i = 0; i < argc; i++) {
        jbyteArray a = (jbyteArray)(*env)->GetObjectArrayElement(env, args, i);
        jsize len = (*env)->GetArrayLength(env, a);
        jbyte *b = (*env)->GetByteArrayElements(env, a, NULL);
        argv[i] = qjs_new_string_len(c->ctx, (const char *)b, (size_t)len);
        (*env)->ReleaseByteArrayElements(env, a, b, JNI_ABORT);
        (*env)->DeleteLocalRef(env, a);
    }
    JSValue r = qjs_call(c->ctx, fn, argc, argv);
    for (int i = 0; i < argc; i++) qjs_free_value(c->ctx, argv[i]);
    free(argv);
    qjs_free_value(c->ctx, fn);
    if (qjs_is_exception(r)) raise_pending(c);
    else out = result_to_java(c, r);
    c->env = NULL;
    return out;
}

static jboolean n_is_job_pending(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    return qjs_is_job_pending(ctx_of(handle)->rt) ? JNI_TRUE : JNI_FALSE;
}

/* >0 a job ran, 0 none pending; throws QuickJSException when the job failed. */
static jint n_execute_pending_job(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    JSContext *pc = NULL;
    int r = qjs_execute_pending_job(c->rt, &pc);
    if (r < 0) raise_pending(c);
    c->env = NULL;
    return r;
}

static jlong n_memory_used(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    return (jlong)qjs_memory_used(ctx_of(handle)->rt);
}

static void n_set_deadline(JNIEnv *env, jobject thiz, jlong handle, jlong ns) {
    (void)env; (void)thiz;
    ctx_of(handle)->deadline_ns = ns;
}
static void n_set_cpu_deadline(JNIEnv *env, jobject thiz, jlong handle, jlong ns) {
    (void)env; (void)thiz;
    ctx_of(handle)->cpu_deadline_ns = ns;
}
static jlong n_thread_cpu_nanos(JNIEnv *env, jobject thiz) {
    (void)env; (void)thiz;
    return thread_cpu_ns();
}
static jlong n_get_deadline(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    return ctx_of(handle)->deadline_ns;
}
static jboolean n_interrupted(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    return ctx_of(handle)->interrupted ? JNI_TRUE : JNI_FALSE;
}
static void n_reset_interrupted(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    ctx_of(handle)->interrupted = 0;
}
static jlong n_now(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return now_ns();
}

static jobject n_eval_bytecode(JNIEnv *env, jobject thiz, jlong handle, jbyteArray blob, jbyteArray source) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    jsize blob_len = (*env)->GetArrayLength(env, blob), src_len = (*env)->GetArrayLength(env, source);
    jbyte *b = (*env)->GetByteArrayElements(env, blob, NULL);
    jbyte *s = (*env)->GetByteArrayElements(env, source, NULL);
    jobject out = NULL;
    if (!b || !s) {
        raise_text(c, "out of memory (bytecode buffer)");
    } else {
        JSValue v = qjs_eval_bytecode(c->ctx, (const uint8_t *)b, (size_t)blob_len, (const char *)s, (size_t)src_len);
        if (qjs_is_exception(v)) raise_pending(c);
        else out = result_to_java(c, v);
    }
    if (b) (*env)->ReleaseByteArrayElements(env, blob, b, JNI_ABORT);
    if (s) (*env)->ReleaseByteArrayElements(env, source, s, JNI_ABORT);
    c->env = NULL;
    return out;
}

static jbyteArray n_compile_bytecode(JNIEnv *env, jobject thiz, jlong handle, jbyteArray code, jstring filename) {
    (void)thiz;
    Ctx *c = ctx_of(handle);
    enter(c, env);
    jsize len = (*env)->GetArrayLength(env, code);
    /* JS_Eval reads input[len], which must be NUL: a Java array has no terminator, so copy it (as n_eval does). */
    char *src = (char *)malloc((size_t)len + 1);
    if (!src) { raise_text(c, "out of memory (compile buffer)"); c->env = NULL; return NULL; }
    (*env)->GetByteArrayRegion(env, code, 0, len, (jbyte *)src);
    src[len] = 0;
    const char *fn = (*env)->GetStringUTFChars(env, filename, NULL);
    size_t out_len = 0;
    uint8_t *blob = qjs_compile_bytecode(c->ctx, src, (size_t)len, fn, &out_len);
    (*env)->ReleaseStringUTFChars(env, filename, fn);
    free(src);
    jbyteArray out = NULL;
    if (!blob) {
        raise_pending(c);
    } else {
        out = (*env)->NewByteArray(env, (jsize)out_len);
        if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize)out_len, (const jbyte *)blob);
        qjs_free_bytecode(c->ctx, blob);
    }
    c->env = NULL;
    return out;
}

static const JNINativeMethod METHODS[] = {
    {"nativeCreate", "(JJ)J", (void *)n_create},
    {"nativeDestroy", "(J)V", (void *)n_destroy},
    {"nativeRegisterFunction", "(JLjava/lang/String;I)V", (void *)n_register},
    {"nativeEval", "(J[BLjava/lang/String;Z)Ljava/lang/Object;", (void *)n_eval},
    {"nativeEvalBytecode", "(J[B[B)Ljava/lang/Object;", (void *)n_eval_bytecode},
    {"nativeCompileBytecode", "(J[BLjava/lang/String;)[B", (void *)n_compile_bytecode},
    {"nativeCall", "(JLjava/lang/String;[[B)Ljava/lang/Object;", (void *)n_call},
    {"nativeIsJobPending", "(J)Z", (void *)n_is_job_pending},
    {"nativeExecutePendingJob", "(J)I", (void *)n_execute_pending_job},
    {"nativeMemoryUsed", "(J)J", (void *)n_memory_used},
    {"nativeSetDeadline", "(JJ)V", (void *)n_set_deadline},
    {"nativeGetDeadline", "(J)J", (void *)n_get_deadline},
    {"nativeSetCpuDeadline", "(JJ)V", (void *)n_set_cpu_deadline},
    {"nativeCaptureGlobalFunction", "(JLjava/lang/String;)Z", (void *)n_capture_global_function},
    {"nativeThreadCpuNanos", "()J", (void *)n_thread_cpu_nanos},
    {"nativeInterrupted", "(J)Z", (void *)n_interrupted},
    {"nativeResetInterrupted", "(J)V", (void *)n_reset_interrupted},
    {"nativeNow", "()J", (void *)n_now},
};

static jclass global_class(JNIEnv *env, const char *name) {
    jclass local = (*env)->FindClass(env, name);
    if (!local) {
        __android_log_print(ANDROID_LOG_ERROR, TAG, "class not found: %s", name);
        return NULL;
    }
    jclass g = (jclass)(*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return g;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)reserved;
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **)&env, JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass ctx_class = (*env)->FindClass(env, "org/telegram/widgetsandbox/QuickJSContext");
    if (!ctx_class) return JNI_ERR;
    if ((*env)->RegisterNatives(env, ctx_class, METHODS, sizeof METHODS / sizeof METHODS[0]) != JNI_OK) return JNI_ERR;
    g_dispatch = (*env)->GetMethodID(env, ctx_class, "dispatch", "(I[[B)Ljava/lang/Object;");
    g_raise = (*env)->GetMethodID(env, ctx_class, "raise", "([B)V");
    (*env)->DeleteLocalRef(env, ctx_class);
    g_bytearray_class = global_class(env, "[B");
    g_double_class = global_class(env, "java/lang/Double");
    g_arraybuffer_class = global_class(env, "org/telegram/widgetsandbox/HostReturn$ArrayBuffer");
    g_null_class = global_class(env, "org/telegram/widgetsandbox/HostReturn$Null");
    if (!g_dispatch || !g_raise || !g_bytearray_class || !g_double_class || !g_arraybuffer_class || !g_null_class) return JNI_ERR;
    g_double_init = (*env)->GetMethodID(env, g_double_class, "<init>", "(D)V");
    g_double_value = (*env)->GetMethodID(env, g_double_class, "doubleValue", "()D");
    g_arraybuffer_bytes = (*env)->GetFieldID(env, g_arraybuffer_class, "bytes", "[B");
    if (!g_double_init || !g_double_value || !g_arraybuffer_bytes) return JNI_ERR;
    return JNI_VERSION_1_6;
}
