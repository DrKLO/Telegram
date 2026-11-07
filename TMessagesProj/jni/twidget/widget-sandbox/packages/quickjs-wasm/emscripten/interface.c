/**
 * interface.c
 *
 * We primarily use JSValue* (pointer to JSValue) when communicating with the
 * host javascript environment, because pointers are trivial to use for calls
 * into emscripten because they're just a number!
 *
 * As with the quickjs.h API, a JSValueConst* value is "borrowed" and should
 * not be freed. A JSValue* is "owned" and should be freed by the owner.
 *
 * Functions starting with "QTS_" are exported by generate.ts to:
 * - interface.h for native C code.
 * - ffi.ts for emscripten.
 *
 * We support building the following build outputs:
 *
 * ## 1. Native machine code
 * For internal development testing purposes.
 *
 * ## 2. WASM via Emscripten
 * For general production use.
 *
 * ## 3. Experimental: Asyncified WASM via Emscripten with -s ASYNCIFY=1.
 * This variant supports treating async host Javascript calls as synchronous
 * from the perspective of the WASM c code.
 *
 * The way this works is described here:
 * https://emscripten.org/docs/porting/asyncify.html
 *
 * In this variant, any call into our C code could return a promise if it ended
 * up suspended. We mark the methods we suspect might suspend due to users' code
 * as returning MaybeAsync(T). This information is ignored for the regular
 * build.
 */

#ifdef __EMSCRIPTEN__
#include <emscripten.h>
#endif

#include <math.h>  // For NAN
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#ifdef QTS_SANITIZE_LEAK
#include <sanitizer/lsan_interface.h>
#endif

#ifdef QTS_USE_QUICKJS_NG
// quickjs-ng amalgam only provides quickjs.h and quickjs-libc.h
#include "../vendor/quickjs-ng/quickjs-libc.h"
#include "../vendor/quickjs-ng/quickjs.h"
// Compatibility: quickjs-ng uses C99 bool instead of custom BOOL type
#ifndef BOOL
#define BOOL bool
#define TRUE true
#define FALSE false
#endif
// Compatibility: quickjs-ng JS_IsBigInt takes only 1 argument
#define QTS_JS_IsBigInt(ctx, v) JS_IsBigInt(v)
// Forward declaration for js_std_dump_error (defined in amalgam with QJS_BUILD_LIBC)
void js_std_dump_error(JSContext *ctx);
#else
#include "../vendor/quickjs/cutils.h"
#include "../vendor/quickjs/quickjs-libc.h"
#include "../vendor/quickjs/quickjs.h"
// bellard/quickjs JS_IsBigInt takes 2 arguments
#define QTS_JS_IsBigInt(ctx, v) JS_IsBigInt(ctx, v)
#endif

#define PKG "quickjs-emscripten: "
#define LOG_LEN 500

#ifdef QTS_DEBUG_MODE
#define IF_DEBUG if (qts_get_context_rt_data(ctx)->debug_log)
#define IF_DEBUG_RT if (qts_get_runtime_data(rt)->debug_log)

#define QTS_DEBUG(msg) IF_DEBUG qts_log(msg);
#define QTS_DEBUG_RT(msg) IF_DEBUG_RT qts_log(msg);
#define QTS_DUMP(value) IF_DEBUG qts_dump(ctx, value);

#else
#define IF_DEBUG if (0)
#define IF_DEBUG_RT if (0)
#define QTS_DEBUG(msg) ;
#define QTS_DEBUG_RT(msg) ;
#define QTS_DUMP(value) ;
#endif

/**
 * Signal to our FFI code generator that this string argument should be passed as a pointer
 * allocated by the caller on the heap, not a JS string on the stack.
 * https://github.com/emscripten-core/emscripten/issues/6860#issuecomment-405818401
 */
#define BorrowedHeapChar const char
#define OwnedHeapChar char
#define JSBorrowedChar const char
#define HostRefId int32_t

/**
 * Signal to our FFI code generator that this function should be called
 * asynchronously when compiled with ASYNCIFY.
 */
#define MaybeAsync(T) T

/**
 * Signal to our FFI code generator that this function is only available in
 * ASYNCIFY builds.
 */
#define AsyncifyOnly(T) T

#define JSVoid void

#define EvalFlags int
#define IntrinsicsFlags enum QTS_Intrinsic
#define EvalDetectModule int

// Forward declarations for EM_JS callback functions
// These are implemented via EM_JS macro but need forward declarations for C99 compliance
#ifdef __EMSCRIPTEN__
JSValue *qts_host_call_function(JSContext *ctx, JSValueConst *this_ptr, int argc, JSValueConst *argv, int32_t host_ref_id);
int qts_host_interrupt_handler(JSRuntime *rt);
char *qts_host_load_module_source(JSRuntime *rt, JSContext *ctx, const char *module_name);
char *qts_host_normalize_module(JSRuntime *rt, JSContext *ctx, const char *module_base_name, const char *module_name);
#endif

typedef struct qts_RuntimeData {
  bool debug_log;
} qts_RuntimeData;

qts_RuntimeData *qts_get_runtime_data(JSRuntime *rt) {
  qts_RuntimeData *data = JS_GetRuntimeOpaque(rt);
  if (data == NULL) {
    data = malloc(sizeof(qts_RuntimeData));
    data->debug_log = false;
    JS_SetRuntimeOpaque(rt, data);
  }
  return data;
}

qts_RuntimeData *qts_get_context_rt_data(JSContext *ctx) {
  return qts_get_runtime_data(JS_GetRuntime(ctx));
}

void qts_log(char *msg) {
  fputs(PKG, stderr);
  fputs(msg, stderr);
  fputs("\n", stderr);
}

void qts_dump(JSContext *ctx, JSValueConst value) {
  const char *str = JS_ToCString(ctx, value);
  if (!str) {
    QTS_DEBUG("QTS_DUMP: can't dump");
    return;
  }
  fputs(str, stderr);
  JS_FreeCString(ctx, str);
  putchar('\n');
}

void copy_prop_if_needed(JSContext *ctx, JSValueConst dest, JSValueConst src, const char *prop_name) {
  JSAtom prop_atom = JS_NewAtom(ctx, prop_name);
  JSValue dest_prop = JS_GetProperty(ctx, dest, prop_atom);
  if (JS_IsUndefined(dest_prop)) {
    JSValue src_prop = JS_GetProperty(ctx, src, prop_atom);
    if (!JS_IsUndefined(src_prop) && !JS_IsException(src_prop)) {
      JS_SetProperty(ctx, dest, prop_atom, src_prop);
    }
  } else {
    JS_FreeValue(ctx, dest_prop);
  }
  JS_FreeAtom(ctx, prop_atom);
}

JSValue *jsvalue_to_heap(JSValueConst value) {
  JSValue *result = malloc(sizeof(JSValue));
  if (result) {
    // Could be better optimized, but at -0z / -ftlo, it
    // appears to produce the same binary code as a memcpy.
    *result = value;
  }
  return result;
}

JSValue *QTS_Throw(JSContext *ctx, JSValueConst *error) {
  JSValue copy = JS_DupValue(ctx, *error);
  return jsvalue_to_heap(JS_Throw(ctx, copy));
}

JSValue *QTS_NewError(JSContext *ctx) {
  return jsvalue_to_heap(JS_NewError(ctx));
}

/**
 * Limits.
 */

/**
 * Memory limit. Set to -1 to disable.
 */
void QTS_RuntimeSetMemoryLimit(JSRuntime *rt, size_t limit) {
  JS_SetMemoryLimit(rt, limit);
}

/**
 * Memory diagnostics
 */

JSValue *QTS_RuntimeComputeMemoryUsage(JSRuntime *rt, JSContext *ctx) {
  JSMemoryUsage s;
  JS_ComputeMemoryUsage(rt, &s);

  // Note that we're going to allocate more memory just to report the memory usage.
  // A more sound approach would be to bind JSMemoryUsage struct directly - but that's
  // a lot of work. This should be okay in the mean time.
  JSValue result = JS_NewObject(ctx);

  // Manually generated via editor-fu from JSMemoryUsage struct definition in quickjs.h
  JS_SetPropertyStr(ctx, result, "malloc_limit", JS_NewInt64(ctx, s.malloc_limit));
  JS_SetPropertyStr(ctx, result, "memory_used_size", JS_NewInt64(ctx, s.memory_used_size));
  JS_SetPropertyStr(ctx, result, "malloc_count", JS_NewInt64(ctx, s.malloc_count));
  JS_SetPropertyStr(ctx, result, "memory_used_count", JS_NewInt64(ctx, s.memory_used_count));
  JS_SetPropertyStr(ctx, result, "atom_count", JS_NewInt64(ctx, s.atom_count));
  JS_SetPropertyStr(ctx, result, "atom_size", JS_NewInt64(ctx, s.atom_size));
  JS_SetPropertyStr(ctx, result, "str_count", JS_NewInt64(ctx, s.str_count));
  JS_SetPropertyStr(ctx, result, "str_size", JS_NewInt64(ctx, s.str_size));
  JS_SetPropertyStr(ctx, result, "obj_count", JS_NewInt64(ctx, s.obj_count));
  JS_SetPropertyStr(ctx, result, "obj_size", JS_NewInt64(ctx, s.obj_size));
  JS_SetPropertyStr(ctx, result, "prop_count", JS_NewInt64(ctx, s.prop_count));
  JS_SetPropertyStr(ctx, result, "prop_size", JS_NewInt64(ctx, s.prop_size));
  JS_SetPropertyStr(ctx, result, "shape_count", JS_NewInt64(ctx, s.shape_count));
  JS_SetPropertyStr(ctx, result, "shape_size", JS_NewInt64(ctx, s.shape_size));
  JS_SetPropertyStr(ctx, result, "js_func_count", JS_NewInt64(ctx, s.js_func_count));
  JS_SetPropertyStr(ctx, result, "js_func_size", JS_NewInt64(ctx, s.js_func_size));
  JS_SetPropertyStr(ctx, result, "js_func_code_size", JS_NewInt64(ctx, s.js_func_code_size));
  JS_SetPropertyStr(ctx, result, "js_func_pc2line_count", JS_NewInt64(ctx, s.js_func_pc2line_count));
  JS_SetPropertyStr(ctx, result, "js_func_pc2line_size", JS_NewInt64(ctx, s.js_func_pc2line_size));
  JS_SetPropertyStr(ctx, result, "c_func_count", JS_NewInt64(ctx, s.c_func_count));
  JS_SetPropertyStr(ctx, result, "array_count", JS_NewInt64(ctx, s.array_count));
  JS_SetPropertyStr(ctx, result, "fast_array_count", JS_NewInt64(ctx, s.fast_array_count));
  JS_SetPropertyStr(ctx, result, "fast_array_elements", JS_NewInt64(ctx, s.fast_array_elements));
  JS_SetPropertyStr(ctx, result, "binary_object_count", JS_NewInt64(ctx, s.binary_object_count));
  JS_SetPropertyStr(ctx, result, "binary_object_size", JS_NewInt64(ctx, s.binary_object_size));

  return jsvalue_to_heap(result);
}

OwnedHeapChar *QTS_RuntimeDumpMemoryUsage(JSRuntime *rt) {
  char *result = malloc(sizeof(char) * 1024);
  FILE *memfile = fmemopen(result, 1024, "w");
  JSMemoryUsage s;
  JS_ComputeMemoryUsage(rt, &s);
  JS_DumpMemoryUsage(memfile, &s, rt);
  fclose(memfile);
  return result;
}

int QTS_RecoverableLeakCheck() {
#ifdef QTS_SANITIZE_LEAK
  return __lsan_do_recoverable_leak_check();
#else
  return 0;
#endif
}

int QTS_BuildIsSanitizeLeak() {
#ifdef QTS_SANITIZE_LEAK
  return 1;
#else
  return 0;
#endif
}

#ifdef QTS_ASYNCIFY
EM_JS(void, set_asyncify_stack_size, (size_t size, size_t default_size), {
  Asyncify.StackSize = size || default_size;
});
#endif

/**
 * Set the stack size limit, in bytes. Set to 0 to disable.
 */

void QTS_RuntimeSetMaxStackSize(JSRuntime *rt, size_t stack_size) {
#ifdef QTS_ASYNCIFY
  set_asyncify_stack_size(stack_size, QTS_ASYNCIFY_DEFAULT_STACK_SIZE);
#endif
  JS_SetMaxStackSize(rt, stack_size);
}

/**
 * Constant pointers. Because we always use JSValue* from the host Javascript environment,
 * we need helper fuctions to return pointers to these constants.
 */

JSValueConst QTS_Undefined = JS_UNDEFINED;
JSValueConst *QTS_GetUndefined() {
  return &QTS_Undefined;
}

JSValueConst QTS_Null = JS_NULL;
JSValueConst *QTS_GetNull() {
  return &QTS_Null;
}

JSValueConst QTS_False = JS_FALSE;
JSValueConst *QTS_GetFalse() {
  return &QTS_False;
}

JSValueConst QTS_True = JS_TRUE;
JSValueConst *QTS_GetTrue() {
  return &QTS_True;
}

// ----------------------------------------------------------------------------
// HostRef - De-reference host value based on JSValue GC.
// Used for functions

static JSClassID host_ref_class_id;
// todo: variant with mark callback

typedef struct HostRef {
  int32_t id;
} HostRef;

#ifdef __EMSCRIPTEN__
EM_JS(void, qts_host_ref_free, (JSRuntime *rt, int32_t id), {
  // For now we don't allow for async freeHostRef.
  const asyncify = undefined;
  Module['callbacks']['freeHostRef'](asyncify, rt, id);
});
#endif

static void host_ref_finalizer(JSRuntime *rt, JSValue val) {
  HostRef *hv = JS_GetOpaque(val, host_ref_class_id);
  if (hv) {
#ifdef __EMSCRIPTEN__
    qts_host_ref_free(rt, hv->id);
#endif
    js_free_rt(rt, hv);
  }
}

static JSClassDef host_ref_class = {
  .class_name = "HostRef",
  .finalizer = host_ref_finalizer,
};

static int host_ref_class_init(JSRuntime *rt) {
  // Only allocate class ID once globally
  if (host_ref_class_id == 0) {
#ifdef QTS_USE_QUICKJS_NG
    JS_NewClassID(rt, &host_ref_class_id);
#else
    JS_NewClassID(&host_ref_class_id);
#endif
  }
  // Register class with this runtime if not already registered
  // JS_NewClass returns 0 on success, -1 if already registered (which is fine)
  if (!JS_IsRegisteredClass(rt, host_ref_class_id)) {
    return JS_NewClass(rt, host_ref_class_id, &host_ref_class);
  }
  return 0;
}

static JSValue new_host_ref(JSContext *ctx, HostRefId id) {
  HostRef *hv;
  JSValue obj;
  obj = JS_NewObjectClass(ctx, host_ref_class_id);
  if (JS_IsException(obj)) {
    return obj;
  }

  hv = js_mallocz(ctx, sizeof(*hv));
  if (!hv) {
    // js_mallocz returns NULL on failure and sets rt.exception to OutOfMemory
    JS_FreeValue(ctx, obj);
    return JS_EXCEPTION;
  }

  hv->id = id;
  JS_SetOpaque(obj, hv);
  return obj;
}

JSValue *QTS_NewHostRef(JSContext *ctx, HostRefId id) {
  return jsvalue_to_heap(new_host_ref(ctx, id));
}

HostRefId QTS_GetHostRefId(JSValueConst *value) {
  HostRef *hv = JS_GetOpaque(*value, host_ref_class_id);
  if (hv) {
    return hv->id;
  }
  return 0;
}

// ----------------------------------------------------------------------------
// Context & Runtime

/**
 * Standard FFI functions
 */

JSRuntime *QTS_NewRuntime() {
  return JS_NewRuntime();
}

void QTS_FreeRuntime(JSRuntime *rt) {
  void *data = JS_GetRuntimeOpaque(rt);
  if (data) {
    free(data);
  }
  JS_FreeRuntime(rt);
}

enum QTS_Intrinsic {
  QTS_Intrinsic_BaseObjects = 1 << 0,
  QTS_Intrinsic_Date = 1 << 1,
  QTS_Intrinsic_Eval = 1 << 2,
  QTS_Intrinsic_StringNormalize = 1 << 3,
  QTS_Intrinsic_RegExp = 1 << 4,
  QTS_Intrinsic_RegExpCompiler = 1 << 5,
  QTS_Intrinsic_JSON = 1 << 6,
  QTS_Intrinsic_Proxy = 1 << 7,
  QTS_Intrinsic_MapSet = 1 << 8,
  QTS_Intrinsic_TypedArrays = 1 << 9,
  QTS_Intrinsic_Promise = 1 << 10,
  QTS_Intrinsic_BigInt = 1 << 11,
};

JSContext *QTS_NewContext(JSRuntime *rt, IntrinsicsFlags intrinsics) {
  JSContext *ctx;

  if (intrinsics == 0) {
    ctx = JS_NewContext(rt);
    if (ctx == NULL) {
      return NULL;
    }
  } else {
    ctx = JS_NewContextRaw(rt);
    if (ctx == NULL) {
      return NULL;
    }

    if (intrinsics & QTS_Intrinsic_BaseObjects) {
      JS_AddIntrinsicBaseObjects(ctx);
    }
    if (intrinsics & QTS_Intrinsic_Date) {
      JS_AddIntrinsicDate(ctx);
    }
    if (intrinsics & QTS_Intrinsic_Eval) {
      JS_AddIntrinsicEval(ctx);
    }
  #ifndef QTS_USE_QUICKJS_NG
    if (intrinsics & QTS_Intrinsic_StringNormalize) {
      JS_AddIntrinsicStringNormalize(ctx);
    }
  #endif
    if (intrinsics & QTS_Intrinsic_RegExp) {
      JS_AddIntrinsicRegExp(ctx);
    }
    if (intrinsics & QTS_Intrinsic_RegExpCompiler) {
      JS_AddIntrinsicRegExpCompiler(ctx);
    }
    if (intrinsics & QTS_Intrinsic_JSON) {
      JS_AddIntrinsicJSON(ctx);
    }
    if (intrinsics & QTS_Intrinsic_Proxy) {
      JS_AddIntrinsicProxy(ctx);
    }
    if (intrinsics & QTS_Intrinsic_MapSet) {
      JS_AddIntrinsicMapSet(ctx);
    }
    if (intrinsics & QTS_Intrinsic_TypedArrays) {
      JS_AddIntrinsicTypedArrays(ctx);
    }
    if (intrinsics & QTS_Intrinsic_Promise) {
      JS_AddIntrinsicPromise(ctx);
    }

    // Note: BigInt is now always part of QuickJS core, no need for separate intrinsic
  }

  if (host_ref_class_init(JS_GetRuntime(ctx)) != 0) {
    JS_FreeContext(ctx);
    return NULL;
  }

  return ctx;
}

void QTS_FreeContext(JSContext *ctx) {
  JS_FreeContext(ctx);
}

void QTS_FreeValuePointer(JSContext *ctx, JSValue *value) {
  JS_FreeValue(ctx, *value);
  free(value);
}

void QTS_FreeValuePointerRuntime(JSRuntime *rt, JSValue *value) {
  JS_FreeValueRT(rt, *value);
  free(value);
}

void QTS_FreeVoidPointer(JSContext *ctx, JSVoid *ptr) {
  js_free(ctx, ptr);
}

void QTS_FreeCString(JSContext *ctx, JSBorrowedChar *str) {
  JS_FreeCString(ctx, str);
}

JSValue *QTS_DupValuePointer(JSContext *ctx, JSValueConst *val) {
  return jsvalue_to_heap(JS_DupValue(ctx, *val));
}

JSValue *QTS_NewObject(JSContext *ctx) {
  return jsvalue_to_heap(JS_NewObject(ctx));
}

JSValue *QTS_NewObjectProto(JSContext *ctx, JSValueConst *proto) {
  return jsvalue_to_heap(JS_NewObjectProto(ctx, *proto));
}

JSValue *QTS_NewArray(JSContext *ctx) {
  return jsvalue_to_heap(JS_NewArray(ctx));
}

#if defined(QJS_VERSION_MAJOR) && (QJS_VERSION_MAJOR > 0 || QJS_VERSION_MINOR >= 17)
/* quickjs-ng 0.17: external ArrayBuffer memory is managed through a realloc function (size 0 frees). */
void *qts_realloc_buffer(JSRuntime *unused_rt, void *unused_opaque, void *ptr, size_t size) {
  if (size == 0) {
    free(ptr);
    return NULL;
  }
  return realloc(ptr, size);
}

JSValue *QTS_NewArrayBuffer(JSContext *ctx, JSVoid *buffer, size_t length) {
  return jsvalue_to_heap(
      JS_NewArrayBuffer(ctx, (uint8_t *)buffer, length, 0, qts_realloc_buffer, NULL, false));
}
#else
void qts_free_buffer(JSRuntime *unused_rt, void *unused_opaque, void *ptr) { free(ptr); }

JSValue *QTS_NewArrayBuffer(JSContext *ctx, JSVoid *buffer, size_t length) {
  return jsvalue_to_heap(
      JS_NewArrayBuffer(ctx, (uint8_t *)buffer, length, qts_free_buffer, NULL, false));
}
#endif

JSValue *QTS_NewFloat64(JSContext *ctx, double num) {
  return jsvalue_to_heap(JS_NewFloat64(ctx, num));
}

double QTS_GetFloat64(JSContext *ctx, JSValueConst *value) {
  double result = NAN;
  JS_ToFloat64(ctx, &result, *value);
  return result;
}

JSValue *QTS_NewString(JSContext *ctx, BorrowedHeapChar *string) {
  return jsvalue_to_heap(JS_NewString(ctx, string));
}

JSBorrowedChar *QTS_GetString(JSContext *ctx, JSValueConst *value) {
  return JS_ToCString(ctx, *value);
}

JSVoid *QTS_GetArrayBuffer(JSContext *ctx, JSValueConst *data) {
  size_t length;
  uint8_t *buffer = JS_GetArrayBuffer(ctx, &length, *data);
  if (!buffer)
    return 0;
  uint8_t *result = malloc(length);
  if (!result)
    return result;
  memcpy(result, buffer, length);
  return result;
}

// I don't know how to return two values in C, maybe allocate memory in stack?
size_t QTS_GetArrayBufferLength(JSContext *ctx, JSValueConst *data) {
  size_t length;
  uint8_t *buffer = JS_GetArrayBuffer(ctx, &length, *data);
  return length;
}

JSValue qts_get_symbol_key(JSContext *ctx, JSValueConst *value) {
  JSValue global = JS_GetGlobalObject(ctx);
  JSValue Symbol = JS_GetPropertyStr(ctx, global, "Symbol");
  JS_FreeValue(ctx, global);

  JSValue Symbol_keyFor = JS_GetPropertyStr(ctx, Symbol, "keyFor");
  JSValue key = JS_Call(ctx, Symbol_keyFor, Symbol, 1, value);
  JS_FreeValue(ctx, Symbol_keyFor);
  JS_FreeValue(ctx, Symbol);
  return key;
}

JSValue *QTS_NewSymbol(JSContext *ctx, BorrowedHeapChar *description, int isGlobal) {
  JSValue global = JS_GetGlobalObject(ctx);
  JSValue Symbol = JS_GetPropertyStr(ctx, global, "Symbol");
  JS_FreeValue(ctx, global);
  JSValue descriptionValue = JS_NewString(ctx, description);
  JSValue symbol;

  if (isGlobal != 0) {
    JSValue Symbol_for = JS_GetPropertyStr(ctx, Symbol, "for");
    symbol = JS_Call(ctx, Symbol_for, Symbol, 1, &descriptionValue);
    JS_FreeValue(ctx, descriptionValue);
    JS_FreeValue(ctx, Symbol_for);
    JS_FreeValue(ctx, Symbol);
    return jsvalue_to_heap(symbol);
  }

  symbol = JS_Call(ctx, Symbol, JS_UNDEFINED, 1, &descriptionValue);
  JS_FreeValue(ctx, descriptionValue);
  JS_FreeValue(ctx, Symbol);

  return jsvalue_to_heap(symbol);
}

MaybeAsync(JSBorrowedChar *) QTS_GetSymbolDescriptionOrKey(JSContext *ctx, JSValueConst *value) {
  JSBorrowedChar *result;

  JSValue key = qts_get_symbol_key(ctx, value);
  if (!JS_IsUndefined(key)) {
    result = JS_ToCString(ctx, key);
    JS_FreeValue(ctx, key);
    return result;
  }

  JSValue description = JS_GetPropertyStr(ctx, *value, "description");
  result = JS_ToCString(ctx, description);
  JS_FreeValue(ctx, description);
  return result;
}

int QTS_IsGlobalSymbol(JSContext *ctx, JSValueConst *value) {
  JSValue key = qts_get_symbol_key(ctx, value);
  int undefined = JS_IsUndefined(key);
  JS_FreeValue(ctx, key);

  if (undefined) {
    return 0;
  } else {
    return 1;
  }
}

int QTS_IsJobPending(JSRuntime *rt) {
  return JS_IsJobPending(rt);
}

/*
  runs pending jobs (Promises/async functions) until it encounters
  an exception or it executed the passed maxJobsToExecute jobs.

  Passing a negative value will run the loop until there are no more
  pending jobs or an exception happened

  Returns the executed number of jobs or the exception encountered
*/
MaybeAsync(JSValue *) QTS_ExecutePendingJob(JSRuntime *rt, int maxJobsToExecute, JSContext **lastJobContext) {
  JSContext *pctx;
  int status = 1;
  int executed = 0;
  while (executed != maxJobsToExecute && status == 1) {
    status = JS_ExecutePendingJob(rt, &pctx);
    if (status == -1) {
      *lastJobContext = pctx;
      return jsvalue_to_heap(JS_GetException(pctx));
    } else if (status == 1) {
      *lastJobContext = pctx;
      executed++;
    }
  }
  IF_DEBUG_RT {
    char msg[LOG_LEN];
    snprintf(msg, LOG_LEN, "QTS_ExecutePendingJob(executed: %d, pctx: %p, lastJobExecuted: %p)", executed, pctx, *lastJobContext);
    qts_log(msg);
  }
  return jsvalue_to_heap(JS_NewFloat64(pctx, executed));
}

MaybeAsync(JSValue *) QTS_GetProp(JSContext *ctx, JSValueConst *this_val, JSValueConst *prop_name) {
  JSAtom prop_atom = JS_ValueToAtom(ctx, *prop_name);
  JSValue prop_val = JS_GetProperty(ctx, *this_val, prop_atom);
  JS_FreeAtom(ctx, prop_atom);
  return jsvalue_to_heap(prop_val);
}

MaybeAsync(JSValue *) QTS_GetPropNumber(JSContext *ctx, JSValueConst *this_val, int prop_name) {
  JSValue prop_val = JS_GetPropertyUint32(ctx, *this_val, (uint32_t)prop_name);
  return jsvalue_to_heap(prop_val);
}

MaybeAsync(void) QTS_SetProp(JSContext *ctx, JSValueConst *this_val, JSValueConst *prop_name, JSValueConst *prop_value) {
  JSAtom prop_atom = JS_ValueToAtom(ctx, *prop_name);
  JSValue extra_prop_value = JS_DupValue(ctx, *prop_value);
  // TODO: should we use DefineProperty internally if this object doesn't have the property yet?
  JS_SetProperty(ctx, *this_val, prop_atom, extra_prop_value);  // consumes extra_prop_value
  JS_FreeAtom(ctx, prop_atom);
}

void QTS_DefineProp(JSContext *ctx, JSValueConst *this_val, JSValueConst *prop_name, JSValueConst *prop_value, JSValueConst *get, JSValueConst *set, bool configurable, bool enumerable, bool has_value) {
  JSAtom prop_atom = JS_ValueToAtom(ctx, *prop_name);

  int flags = 0;
  if (configurable) {
    flags = flags | JS_PROP_CONFIGURABLE;
    if (has_value) {
      flags = flags | JS_PROP_HAS_CONFIGURABLE;
    }
  }
  if (enumerable) {
    flags = flags | JS_PROP_ENUMERABLE;
    if (has_value) {
      flags = flags | JS_PROP_HAS_ENUMERABLE;
    }
  }
  if (!JS_IsUndefined(*get)) {
    flags = flags | JS_PROP_HAS_GET;
  }
  if (!JS_IsUndefined(*set)) {
    flags = flags | JS_PROP_HAS_SET;
  }
  if (has_value) {
    flags = flags | JS_PROP_HAS_VALUE;
  }

  JS_DefineProperty(ctx, *this_val, prop_atom, *prop_value, *get, *set, flags);
  JS_FreeAtom(ctx, prop_atom);
}

#define JS_ATOM_TAG_INT (1U << 31)
static inline BOOL __JS_AtomIsTaggedInt(JSAtom v) {
  return (v & JS_ATOM_TAG_INT) != 0;
}

static inline uint32_t __JS_AtomToUInt32(JSAtom atom) {
  return atom & ~JS_ATOM_TAG_INT;
}

/* include numbers. when only this is set, we filter out strings */
#define QTS_GPN_NUMBER_MASK (1 << 6)
/* Treat numbers as strings */
#define QTS_STANDARD_COMPLIANT_NUMBER (1 << 7)

MaybeAsync(JSValue *) QTS_GetOwnPropertyNames(JSContext *ctx, JSValue ***out_ptrs, uint32_t *out_len, JSValueConst *obj, int flags) {
  JSPropertyEnum *tab = NULL;
  uint32_t total_props = 0;
  uint32_t out_props = 0;

  BOOL qts_standard_compliant_number = (flags & QTS_STANDARD_COMPLIANT_NUMBER) != 0;
  BOOL qts_include_string = (flags & JS_GPN_STRING_MASK) != 0;
  BOOL qts_include_number = qts_standard_compliant_number ? 0 : (flags & QTS_GPN_NUMBER_MASK) != 0;
  if (qts_include_number) {
    // if we want numbers, we must include strings in call down
    flags = flags | JS_GPN_STRING_MASK;
  }

  int status = 0;
  status = JS_GetOwnPropertyNames(ctx, &tab, &total_props, *obj, flags);
  if (status < 0) {
    if (tab != NULL) {
      js_free(ctx, tab);
    }
    return jsvalue_to_heap(JS_GetException(ctx));
  }
  *out_ptrs = malloc(sizeof(JSValue) * total_props);
  for (int i = 0; i < total_props; i++) {
    JSAtom atom = tab[i].atom;

    if (__JS_AtomIsTaggedInt(atom)) {
      if (qts_include_number) {
        uint32_t v = __JS_AtomToUInt32(atom);
        (*out_ptrs)[out_props++] = jsvalue_to_heap(JS_NewUint32(ctx, v));
      } else if (qts_include_string && qts_standard_compliant_number) {
        (*out_ptrs)[out_props++] = jsvalue_to_heap(JS_AtomToValue(ctx, tab[i].atom));
      }
      JS_FreeAtom(ctx, atom);
      continue;
    }

    JSValue atom_value = JS_AtomToValue(ctx, atom);
    JS_FreeAtom(ctx, atom);

    if (JS_IsString(atom_value)) {
      if (qts_include_string) {
        (*out_ptrs)[out_props++] = jsvalue_to_heap(atom_value);
      } else {
        JS_FreeValue(ctx, atom_value);
      }
    } else {  // Symbol
      // We must have set the includeSymbol or includePrivate flags
      // if it's present
      (*out_ptrs)[out_props++] = jsvalue_to_heap(atom_value);
    }
  }
  js_free(ctx, tab);
  *out_len = out_props;
  return NULL;
}

MaybeAsync(JSValue *) QTS_Call(JSContext *ctx, JSValueConst *func_obj, JSValueConst *this_obj, int argc, JSValueConst **argv_ptrs) {
  // convert array of pointers to array of values
  JSValueConst argv[argc];
  int i;
  for (i = 0; i < argc; i++) {
    argv[i] = *(argv_ptrs[i]);
  }

  return jsvalue_to_heap(JS_Call(ctx, *func_obj, *this_obj, argc, argv));
}

/**
 * If maybe_exception is an exception, get the error.
 * Otherwise, return NULL.
 */
JSValue *QTS_ResolveException(JSContext *ctx, JSValue *maybe_exception) {
  if (JS_IsException(*maybe_exception)) {
    return jsvalue_to_heap(JS_GetException(ctx));
  }

  return NULL;
}

MaybeAsync(JSBorrowedChar *) QTS_Dump(JSContext *ctx, JSValueConst *obj) {
  JSValue obj_json_value = JS_JSONStringify(ctx, *obj, JS_UNDEFINED, JS_UNDEFINED);
  if (!JS_IsException(obj_json_value)) {
    const char *obj_json_chars = JS_ToCString(ctx, obj_json_value);
    JS_FreeValue(ctx, obj_json_value);
    if (obj_json_chars != NULL) {
      JSValue enumerable_props = JS_ParseJSON(ctx, obj_json_chars, strlen(obj_json_chars), "<dump>");
      JS_FreeCString(ctx, obj_json_chars);
      if (!JS_IsException(enumerable_props)) {
        // Copy common non-enumerable props for different object types.
        // Errors:
        copy_prop_if_needed(ctx, enumerable_props, *obj, "name");
        copy_prop_if_needed(ctx, enumerable_props, *obj, "message");
        copy_prop_if_needed(ctx, enumerable_props, *obj, "stack");
        copy_prop_if_needed(ctx, enumerable_props, *obj, "fileName");
        copy_prop_if_needed(ctx, enumerable_props, *obj, "lineNumber");

        // Serialize again.
        JSValue enumerable_json = JS_JSONStringify(ctx, enumerable_props, JS_UNDEFINED, JS_UNDEFINED);
        JS_FreeValue(ctx, enumerable_props);

        JSBorrowedChar *result = QTS_GetString(ctx, &enumerable_json);
        JS_FreeValue(ctx, enumerable_json);
        return result;
      }
    }
  }

  IF_DEBUG {
    qts_log("Error dumping JSON:");
    js_std_dump_error(ctx);
  }

  // Fallback: convert to string
  return QTS_GetString(ctx, obj);
}

JSValue qts_resolve_func_data(
    JSContext *ctx,
    JSValueConst this_val,
    int argc,
    JSValueConst *argv,
    int magic,
    JSValue *func_data) {
  return JS_DupValue(ctx, func_data[0]);
}

// QTS_DetectModule - bellard/quickjs style module detection heuristic.
// For quickjs-ng, this is patched into the amalgam (see vendor/quickjs-ng-patches/).
// It uses bellard's approach: skip whitespace/comments, check for 'import' or 'export'.
#ifdef QTS_USE_QUICKJS_NG
extern bool QTS_DetectModule(const char *input, size_t input_len);
#endif

MaybeAsync(JSValue *) QTS_Eval(JSContext *ctx, BorrowedHeapChar *js_code, size_t js_code_length, const char *filename, EvalDetectModule detectModule, EvalFlags evalFlags) {
  char msg[LOG_LEN];
#ifdef QTS_USE_QUICKJS_NG
  // quickjs-ng's JS_DetectModule has different behavior - it parses the code
  // as a module and returns true if parsing succeeds. Since any valid JS
  // expression is valid module syntax, this incorrectly detects non-modules.
  // Use bellard/quickjs heuristic: check for import/export keywords at start.
  if (detectModule) {
    if (QTS_DetectModule((const char *)js_code, js_code_length)) {
      QTS_DEBUG("QTS_Eval: quickjs-ng heuristic detected module = true");
      evalFlags |= JS_EVAL_TYPE_MODULE;
    } else {
      QTS_DEBUG("QTS_Eval: quickjs-ng heuristic detected module = false");
    }
  } else {
    QTS_DEBUG("QTS_Eval: do not detect module");
  }
#else
  if (detectModule) {
    if (JS_DetectModule((const char *)js_code, js_code_length)) {
      QTS_DEBUG("QTS_Eval: Detected module = true");
      evalFlags |= JS_EVAL_TYPE_MODULE;
    } else {
      QTS_DEBUG("QTS_Eval: Detected module = false");
    }
  } else {
    QTS_DEBUG("QTS_Eval: do not detect module");
  }
#endif

  JSModuleDef *module;
  JSValue eval_result;
  if (
      (evalFlags & JS_EVAL_TYPE_MODULE) != 0 &&
      (evalFlags & JS_EVAL_FLAG_COMPILE_ONLY) == 0) {
    QTS_DEBUG("QTS_Eval: JS_EVAL_TYPE_MODULE");
    JSValue func_obj = JS_Eval(ctx, js_code, js_code_length, filename, evalFlags | JS_EVAL_FLAG_COMPILE_ONLY);
    if (JS_IsException(func_obj)) {
      QTS_DEBUG("QTS_Eval: JS_EVAL_FLAG_COMPILE_ONLY exception")
      return jsvalue_to_heap(func_obj);
    }

    if (JS_VALUE_GET_TAG(func_obj) != JS_TAG_MODULE) {
      QTS_DEBUG("QTS_Eval: JS_EVAL_FLAG_COMPILE_ONLY result != JS_TAG_MODULE")
      JS_FreeValue(ctx, func_obj);
      return jsvalue_to_heap(JS_ThrowInternalError(ctx, "Module code compiled to non-module object"));
    }

    module = JS_VALUE_GET_PTR(func_obj);
    if (module == NULL) {
      QTS_DEBUG("QTS_Eval: JSModuleDef == NULL")
      JS_FreeValue(ctx, func_obj);
      return jsvalue_to_heap(JS_ThrowInternalError(ctx, "Module compiled to null"));
    }

    eval_result = JS_EvalFunction(ctx, func_obj);
    QTS_DEBUG("QTS_Eval: JS_EVAL_TYPE_MODULE eval_result")
    // It seems like this is a double-free.
    // JS_FreeValue(ctx, func_obj);
  } else {
    eval_result = JS_Eval(ctx, js_code, js_code_length, filename, evalFlags);
    QTS_DEBUG("QTS_Eval: JS_EVAL_TYPE_GLOBAL eval_result")
  }

  IF_DEBUG {
    snprintf(msg, LOG_LEN, "QTS_Eval: eval_result = %d", JS_VALUE_GET_TAG(eval_result));
    qts_log(msg);
  }

  if (
      // Error - nothing more to do.
      JS_IsException(eval_result)
      // Non-module eval - return the result
      || (evalFlags & JS_EVAL_TYPE_MODULE) == 0) {
    QTS_DEBUG("QTS_Eval: non-module or exception")
    return jsvalue_to_heap(eval_result);
  }

  // We eval'd a module.
  // Make our return type `ModuleExports | Promise<ModuleExports>>`
  JSPromiseStateEnum state = JS_PromiseState(ctx, eval_result);
  IF_DEBUG {
    snprintf(msg, LOG_LEN, "QTS_Eval: eval_result JS_PromiseState = %i", state);
    qts_log(msg);
  }
  if (
      // quickjs@2024-01-14 evaluating module
      // produced a promise
      (state == JS_PROMISE_FULFILLED)
      // quickjs in compile mode
      // quickjs-ng before rebasing on quickjs@2024-01-14
      // not a promise.
      || (state == -1)) {
    QTS_DEBUG("QTS_Eval: result: JS_PROMISE_FULFILLED or not a promise")
    JS_FreeValue(ctx, eval_result);
    return jsvalue_to_heap(JS_GetModuleNamespace(ctx, module));
  } else if (state == JS_PROMISE_REJECTED) {
    // Throw the rejection response, to match evaluation of non-module code,
    // or of code with a syntax error.
    QTS_DEBUG("QTS_Eval: result: JS_PROMISE_REJECTED - throw rejection reason")
    JS_Throw(ctx, JS_PromiseResult(ctx, eval_result));
    JS_FreeValue(ctx, eval_result);
    return jsvalue_to_heap(JS_EXCEPTION);
  } else if (state == JS_PROMISE_PENDING) {
    QTS_DEBUG("QTS_Eval: result: JS_PROMISE_PENDING")
    // return moduleDone.then(() => module_namespace)
    JSValue module_namespace = JS_GetModuleNamespace(ctx, module);
    if (JS_IsException(module_namespace)) {
      QTS_DEBUG("QTS_Eval: module_namespace exception")
      JS_FreeValue(ctx, eval_result);
      return jsvalue_to_heap(module_namespace);
    }

    JSValue then_resolve_module_namespace = JS_NewCFunctionData(ctx, &qts_resolve_func_data, 0, 0, 1, &module_namespace);
    JS_FreeValue(ctx, module_namespace);
    if (JS_IsException(then_resolve_module_namespace)) {
      QTS_DEBUG("QTS_Eval: then_resolve_module_namespace exception")
      JS_FreeValue(ctx, eval_result);
      return jsvalue_to_heap(then_resolve_module_namespace);
    }

    JSAtom then_atom = JS_NewAtom(ctx, "then");
    JSValue new_promise = JS_Invoke(ctx, eval_result, then_atom, 1, &then_resolve_module_namespace);
    QTS_DEBUG("QTS_Eval: new_promise = promise.then(() => resolveModuleNamespace())")
    JS_FreeAtom(ctx, then_atom);
    JS_FreeValue(ctx, then_resolve_module_namespace);
    JS_FreeValue(ctx, eval_result);

    return jsvalue_to_heap(new_promise);
  } else {
    // Unknown case
    QTS_DEBUG("QTS_Eval: unknown JSPromiseStateEnum")
    return jsvalue_to_heap(eval_result);
  }
}

JSValue *QTS_GetModuleNamespace(JSContext *ctx, JSValueConst *module_func_obj) {
  if (JS_VALUE_GET_TAG(*module_func_obj) != JS_TAG_MODULE) {
    return jsvalue_to_heap(JS_ThrowInternalError(ctx, "Not a module"));
  }

  JSModuleDef *module = JS_VALUE_GET_PTR(*module_func_obj);
  return jsvalue_to_heap(JS_GetModuleNamespace(ctx, module));
}

OwnedHeapChar *QTS_Typeof(JSContext *ctx, JSValueConst *value) {
  const char *result = "unknown";

  if (JS_IsNumber(*value)) {
    result = "number";
  } else if (QTS_JS_IsBigInt(ctx, *value)) {
    result = "bigint";
  } else if (JS_IsFunction(ctx, *value)) {
    result = "function";
  } else if (JS_IsBool(*value)) {
    result = "boolean";
  } else if (JS_IsNull(*value)) {
    result = "object";
  } else if (JS_IsUndefined(*value)) {
    result = "undefined";
  } else if (JS_IsUninitialized(*value)) {
    result = "undefined";
  } else if (JS_IsString(*value)) {
    result = "string";
  } else if (JS_IsSymbol(*value)) {
    result = "symbol";
  } else if (JS_IsObject(*value)) {
    result = "object";
  }

  char *out = strdup(result);
  return out;
}

JSAtom QTS_AtomLength = 0;
int QTS_GetLength(JSContext *ctx, uint32_t *out_len, JSValueConst *value) {
  JSValue len_val;
  int result;

  if (!JS_IsObject(*value)) {
    return -1;
  }

  if (QTS_AtomLength == 0) {
    // This should result in a constant static atom we don't actually need to
    // free, since it's interned within quickjs.c
    QTS_AtomLength = JS_NewAtom(ctx, "length");
  }

  len_val = JS_GetProperty(ctx, *value, QTS_AtomLength);
  if (JS_IsException(len_val)) {
    return -1;
  }

  result = JS_ToUint32(ctx, out_len, len_val);
  JS_FreeValue(ctx, len_val);
  return result;
}

typedef enum IsEqualOp {
  QTS_EqualOp_StrictEq = 0,
  QTS_EqualOp_SameValue = 1,
  QTS_EqualOp_SameValueZero = 2,
} IsEqualOp;

int QTS_IsEqual(JSContext *ctx, JSValueConst *a, JSValueConst *b, IsEqualOp op) {
#ifdef QTS_USE_QUICKJS_NG
  return -1;
#else
  switch (op) {
    case QTS_EqualOp_SameValue:
      return JS_SameValue(ctx, *a, *b);
    case QTS_EqualOp_SameValueZero:
      return JS_SameValueZero(ctx, *a, *b);
    default:
    case QTS_EqualOp_StrictEq:
      return JS_StrictEq(ctx, *a, *b);
  }
#endif
}

JSValue *QTS_GetGlobalObject(JSContext *ctx) {
  return jsvalue_to_heap(JS_GetGlobalObject(ctx));
}

JSValue *QTS_NewPromiseCapability(JSContext *ctx, JSValue **resolve_funcs_out) {
  JSValue resolve_funcs[2];
  JSValue promise = JS_NewPromiseCapability(ctx, resolve_funcs);
  resolve_funcs_out[0] = jsvalue_to_heap(resolve_funcs[0]);
  resolve_funcs_out[1] = jsvalue_to_heap(resolve_funcs[1]);
  return jsvalue_to_heap(promise);
}

JSPromiseStateEnum QTS_PromiseState(JSContext *ctx, JSValueConst *promise) {
  return JS_PromiseState(ctx, *promise);
}

JSValue *QTS_PromiseResult(JSContext *ctx, JSValueConst *promise) {
  return jsvalue_to_heap(JS_PromiseResult(ctx, *promise));
}

void QTS_TestStringArg(const char *string) {
  // pass
}

int QTS_GetDebugLogEnabled(JSRuntime *rt) {
  IF_DEBUG_RT {
    return 1;
  }
  return 0;
}

void QTS_SetDebugLogEnabled(JSRuntime *rt, int is_enabled) {
#ifdef QTS_DEBUG_MODE
  qts_get_runtime_data(rt)->debug_log = (bool)is_enabled;
#endif
}

int QTS_BuildIsDebug() {
#ifdef QTS_DEBUG_MODE
  return 1;
#else
  return 0;
#endif
}

int QTS_BuildIsAsyncify() {
#ifdef QTS_ASYNCIFY
  return 1;
#else
  return 0;
#endif
}

// ----------------------------------------------------------------------------
// C -> Host Callbacks
// Note: inside EM_JS, we need to use ['...'] subscript syntax for accessing JS
// objects, because in optimized builds, Closure compiler will mangle all the
// names.

// -------------------
// function: C -> Host
#ifdef __EMSCRIPTEN__
EM_JS(MaybeAsync(JSValue *), qts_host_call_function, (JSContext * ctx, JSValueConst *this_ptr, int argc, JSValueConst *argv, HostRefId host_ref_id), {
#ifdef QTS_ASYNCIFY
  const asyncify = {['handleSleep'] : Asyncify.handleSleep};
#else
  const asyncify = undefined;
#endif
  return Module['callbacks']['callFunction'](asyncify, ctx, this_ptr, argc, argv, host_ref_id);
});
#endif

// Function: QuickJS -> C
JSValue qts_call_function(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv, int magic, JSValue *func_data) {
  int32_t host_ref_id = 0;
  HostRef *host_ref = JS_GetOpaque(*func_data, host_ref_class_id);
  if (host_ref) {
    host_ref_id = host_ref->id;
  }

  JSValue *result_ptr = qts_host_call_function(ctx, &this_val, argc, argv, host_ref_id);
  if (result_ptr == NULL) {
    return JS_UNDEFINED;
  }
  JSValue result = *result_ptr;
  free(result_ptr);
  return result;
}

// Function: Host -> QuickJS
JSValue *QTS_NewFunction(JSContext *ctx, const char *name, int arg_length, bool is_constructor, HostRefId host_ref_id) {
  IF_DEBUG {
    char msg[LOG_LEN];
    snprintf(msg, LOG_LEN, "new_function(name: %s, length: %d, is_constructor: %d, host_ref_id: %d)", name, arg_length, is_constructor, host_ref_id);
    qts_log(msg);
  }

  JSValue host_ref = new_host_ref(ctx, host_ref_id);
  if (JS_IsException(host_ref)) {
    return jsvalue_to_heap(host_ref);
  }

  JSValue func_obj = JS_NewCFunctionData(
      /* context */ ctx,
      /* JSCFunctionData* */ &qts_call_function,
      /* fn.length */ 0,
      /* magic */ 0,
      /* data length */ 1,
      /* data */ &host_ref
  );
  JS_FreeValue(ctx, host_ref);

  if (JS_IsException(func_obj)) {
    return jsvalue_to_heap(func_obj);
  }

  if (name && strlen(name) > 0) {
    JSValue name_val = JS_NewString(ctx, name);
    if (JS_IsException(name_val)) {
      JS_FreeValue(ctx, func_obj);
      return jsvalue_to_heap(name_val);
    }
    // JS_DefinePropertyValueStr takes ownership of name_val and frees it
    JS_DefinePropertyValueStr(ctx, func_obj, "name", name_val, JS_PROP_CONFIGURABLE);
  }

  if (is_constructor) {
    JS_SetConstructorBit(ctx, func_obj, is_constructor);
  }

  return jsvalue_to_heap(func_obj);
}

JSValueConst *QTS_ArgvGetJSValueConstPointer(JSValueConst *argv, int index) {
  return &argv[index];
}

// --------------------
// interrupt: C -> Host
#ifdef __EMSCRIPTEN__
EM_JS(int, qts_host_interrupt_handler, (JSRuntime * rt), {
  // Async not supported here.
  // #ifdef QTS_ASYNCIFY
  //   const asyncify = Asyncify;
  // #else
  const asyncify = undefined;
  // #endif
  return Module['callbacks']['shouldInterrupt'](asyncify, rt);
});
#endif

// interrupt: QuickJS -> C
#if defined(QJS_VERSION_MAJOR) && (QJS_VERSION_MAJOR > 0 || QJS_VERSION_MINOR >= 17)
/* quickjs-ng 0.17: the interrupt handler receives the context; the host still keys on the runtime. */
int qts_interrupt_handler(JSContext *ctx, void *_unused) {
  return qts_host_interrupt_handler(JS_GetRuntime(ctx));
}
#else
int qts_interrupt_handler(JSRuntime *rt, void *_unused) {
  return qts_host_interrupt_handler(rt);
}
#endif

// interrupt: Host -> QuickJS
void QTS_RuntimeEnableInterruptHandler(JSRuntime *rt) {
  JS_SetInterruptHandler(rt, &qts_interrupt_handler, NULL);
}

void QTS_RuntimeDisableInterruptHandler(JSRuntime *rt) {
  JS_SetInterruptHandler(rt, NULL, NULL);
}

// ----------------------------------------------------------------------------
// Module loading helpers
// --------------------
// load module: C -> Host
// TODO: a future version can support host returning JSModuleDef* directly;
// for now we only support loading module source code.

/*
The module loading model under ASYNCIFY is convoluted. We need to make sure we
never have an async request running concurrently for loading modules.

The first implemenation looked like this:

C                                  HOST                      SUSPENDED
qts_host_load_module(name) ------>                            false
                                   call rt.loadModule(name)   false
                                   Start async load module    false
                                   Suspend C                  true
                                   Async load complete        true
            < ---------------      QTS_CompileModule(source)  true
QTS_Eval(source, COMPILE_ONLY)                                true
Loaded module has import                                      true
qts_host_load_module(dep) ------->                            true
                                  call rt.loadModule(dep)     true
                                  Start async load module     true
                                  ALREADY SUSPENDED, CRASH

We can solve this in two different ways:

1. Return to C as soon as we async load the module source.
   That way, we unsuspend before calling QTS_CompileModule.
2. Once we load the module, use a new API to detect and async
   load the module's downstream dependencies. This way
   they're loaded synchronously so we don't need to suspend "again".

Probably we could optimize (2) to make it more performant, eg with parallel
loading, but (1) seems much easier to implement in the sort run.
*/

JSModuleDef *qts_compile_module(JSContext *ctx, const char *module_name, BorrowedHeapChar *module_body) {
  IF_DEBUG {
    char msg[LOG_LEN];
    sprintf(msg, "QTS_CompileModule(ctx: %p, name: %s, bodyLength: %lu)", ctx, module_name, strlen(module_body));
    qts_log(msg);
  }
  JSValue func_val = JS_Eval(ctx, module_body, strlen(module_body), module_name, JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
  if (JS_IsException(func_val)) {
    return NULL;
  }
  // TODO: Is exception ok?
  // TODO: set import.meta?
  JSModuleDef *module = JS_VALUE_GET_PTR(func_val);
  JS_FreeValue(ctx, func_val);
  return module;
}

#ifdef __EMSCRIPTEN__
EM_JS(MaybeAsync(char *), qts_host_load_module_source, (JSRuntime * rt, JSContext *ctx, const char *module_name), {
#ifdef QTS_ASYNCIFY
  const asyncify = {['handleSleep'] : Asyncify.handleSleep};
#else
  const asyncify = undefined;
#endif
  // https://emscripten.org/docs/api_reference/preamble.js.html#UTF8ToString
  const moduleNameString = UTF8ToString(module_name);
  return Module['callbacks']['loadModuleSource'](asyncify, rt, ctx, moduleNameString);
});

EM_JS(MaybeAsync(char *), qts_host_normalize_module, (JSRuntime * rt, JSContext *ctx, const char *module_base_name, const char *module_name), {
#ifdef QTS_ASYNCIFY
  const asyncify = {['handleSleep'] : Asyncify.handleSleep};
#else
  const asyncify = undefined;
#endif
  // https://emscripten.org/docs/api_reference/preamble.js.html#UTF8ToString
  const moduleBaseNameString = UTF8ToString(module_base_name);
  const moduleNameString = UTF8ToString(module_name);
  return Module['callbacks']['normalizeModule'](asyncify, rt, ctx, moduleBaseNameString, moduleNameString);
});
#endif

// load module: QuickJS -> C
// See js_module_loader in quickjs/quickjs-libc.c:567
JSModuleDef *qts_load_module(JSContext *ctx, const char *module_name, void *_unused) {
  JSRuntime *rt = JS_GetRuntime(ctx);
  IF_DEBUG_RT {
    char msg[LOG_LEN];
    sprintf(msg, "qts_load_module(rt: %p, ctx: %p, name: %s)", rt, ctx, module_name);
    qts_log(msg);
  }
  char *module_source = qts_host_load_module_source(rt, ctx, module_name);
  if (module_source == NULL) {
    return NULL;
  }

  JSModuleDef *module = qts_compile_module(ctx, module_name, module_source);
  free(module_source);
  return module;
}

char *qts_normalize_module(JSContext *ctx, const char *module_base_name, const char *module_name, void *_unused) {
  JSRuntime *rt = JS_GetRuntime(ctx);
  IF_DEBUG_RT {
    char msg[LOG_LEN];
    sprintf(msg, "qts_normalize_module(rt: %p, ctx: %p, base_name: %s, name: %s)", rt, ctx, module_base_name, module_name);
    qts_log(msg);
  }
  char *em_module_name = qts_host_normalize_module(rt, ctx, module_base_name, module_name);
  char *js_module_name = js_strdup(ctx, em_module_name);
  free(em_module_name);
  return js_module_name;
}

// Load module: Host -> QuickJS
void QTS_RuntimeEnableModuleLoader(JSRuntime *rt, int use_custom_normalize) {
  JSModuleNormalizeFunc *module_normalize = NULL; /* use default name normalizer */
  if (use_custom_normalize) {
    module_normalize = &qts_normalize_module;
  }
  JS_SetModuleLoaderFunc(rt, module_normalize, &qts_load_module, NULL);
}

void QTS_RuntimeDisableModuleLoader(JSRuntime *rt) {
  JS_SetModuleLoaderFunc(rt, NULL, NULL, NULL);
}

JSValue *QTS_bjson_encode(JSContext *ctx, JSValueConst *val) {
  size_t length;
  uint8_t *buffer = JS_WriteObject(ctx, &length, *val, JS_WRITE_OBJ_REFERENCE);
  if (!buffer)
    return jsvalue_to_heap(JS_EXCEPTION);

  JSValue array = JS_NewArrayBufferCopy(ctx, buffer, length);
  js_free(ctx, buffer);
  return jsvalue_to_heap(array);
}

JSValue *QTS_bjson_decode(JSContext *ctx, JSValueConst *data) {
  size_t length;
  uint8_t *buffer = JS_GetArrayBuffer(ctx, &length, *data);
  if (!buffer)
    return jsvalue_to_heap(JS_EXCEPTION);

  JSValue value = JS_ReadObject(ctx, buffer, length, 0);
  return jsvalue_to_heap(value);
}