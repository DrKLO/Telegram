#include "qjs_shim.h"
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

JSValue qjs_undefined(void) { return JS_UNDEFINED; }
JSValue qjs_null(void) { return JS_NULL; }
bool qjs_is_exception(JSValue v) { return JS_IsException(v); }
bool qjs_is_undefined(JSValue v) { return JS_IsUndefined(v); }
bool qjs_is_string(JSValue v) { return JS_IsString(v); }
bool qjs_is_number(JSValue v) { return JS_IsNumber(v); }
bool qjs_is_function(JSContext *ctx, JSValue v) { return JS_IsFunction(ctx, v); }
void qjs_free_value(JSContext *ctx, JSValue v) { JS_FreeValue(ctx, v); }
JSValue qjs_dup_value(JSContext *ctx, JSValue v) { return JS_DupValue(ctx, v); }

JSValue qjs_new_string(JSContext *ctx, const char *s) { return JS_NewString(ctx, s); }
JSValue qjs_new_string_len(JSContext *ctx, const char *s, size_t len) { return JS_NewStringLen(ctx, s, len); }
JSValue qjs_new_float64(JSContext *ctx, double d) { return JS_NewFloat64(ctx, d); }
JSValue qjs_new_array_buffer_copy(JSContext *ctx, const uint8_t *buf, size_t len) { return JS_NewArrayBufferCopy(ctx, buf, len); }
JSValue qjs_throw_error(JSContext *ctx, const char *message) { return JS_ThrowTypeError(ctx, "%s", message); }

JSValue qjs_new_cfunction_magic(JSContext *ctx, JSCFunctionMagic *fn, const char *name, int length, int magic) {
    return JS_NewCFunction2(ctx, (JSCFunction *)fn, name, length, JS_CFUNC_generic_magic, magic);
}

const char *qjs_to_cstring(JSContext *ctx, JSValue v) { return JS_ToCString(ctx, v); }
void qjs_free_cstring(JSContext *ctx, const char *s) { JS_FreeCString(ctx, s); }
double qjs_to_float64(JSContext *ctx, JSValue v) {
    double d = NAN;
    if (JS_ToFloat64(ctx, &d, v) < 0) return NAN;
    return d;
}

JSValue qjs_eval(JSContext *ctx, const char *code, size_t len, const char *filename, bool as_module) {
    return JS_Eval(ctx, code, len, filename, as_module ? JS_EVAL_TYPE_MODULE : JS_EVAL_TYPE_GLOBAL);
}

/* --- precompiled bundles --- */

#define QJS_BC_MAGIC "TWQB"
#define QJS_BC_FORMAT 1u
#define QJS_BC_HEADER 24

static uint64_t fnv1a64(const uint8_t *p, size_t n) {
    uint64_t h = 1469598103934665603ULL;
    for (size_t i = 0; i < n; i++) { h ^= p[i]; h *= 1099511628211ULL; }
    return h;
}
static void put_u32(uint8_t *p, uint32_t v) { for (int i = 0; i < 4; i++) p[i] = (uint8_t)(v >> (8 * i)); }
static void put_u64(uint8_t *p, uint64_t v) { for (int i = 0; i < 8; i++) p[i] = (uint8_t)(v >> (8 * i)); }
static uint32_t get_u32(const uint8_t *p) { uint32_t v = 0; for (int i = 0; i < 4; i++) v |= (uint32_t)p[i] << (8 * i); return v; }
static uint64_t get_u64(const uint8_t *p) { uint64_t v = 0; for (int i = 0; i < 8; i++) v |= (uint64_t)p[i] << (8 * i); return v; }

uint8_t *qjs_compile_bytecode(JSContext *ctx, const char *code, size_t len, const char *filename, size_t *out_len) {
    JSValue fn = JS_Eval(ctx, code, len, filename, JS_EVAL_TYPE_GLOBAL | JS_EVAL_FLAG_COMPILE_ONLY);
    if (JS_IsException(fn)) return NULL;
    size_t bc_len = 0;
    /* Source text is dropped (Function.prototype.toString of our own code is nobody's business);
       debug info stays so stack traces from the shim keep their line numbers. */
    uint8_t *bc = JS_WriteObject(ctx, &bc_len, fn, JS_WRITE_OBJ_BYTECODE | JS_WRITE_OBJ_STRIP_SOURCE);
    JS_FreeValue(ctx, fn);
    if (!bc) return NULL;
    uint8_t *blob = js_malloc(ctx, QJS_BC_HEADER + bc_len);
    if (!blob) { js_free(ctx, bc); return NULL; }
    memcpy(blob, QJS_BC_MAGIC, 4);
    put_u32(blob + 4, QJS_BC_FORMAT);
    put_u64(blob + 8, (uint64_t)len);
    put_u64(blob + 16, fnv1a64((const uint8_t *)code, len));
    memcpy(blob + QJS_BC_HEADER, bc, bc_len);
    js_free(ctx, bc);
    *out_len = QJS_BC_HEADER + bc_len;
    return blob;
}

void qjs_free_bytecode(JSContext *ctx, uint8_t *blob) { js_free(ctx, blob); }

JSValue qjs_eval_bytecode(JSContext *ctx, const uint8_t *blob, size_t blob_len, const char *source, size_t source_len) {
    if (blob_len < QJS_BC_HEADER || memcmp(blob, QJS_BC_MAGIC, 4) != 0)
        return JS_ThrowTypeError(ctx, "bytecode: not a TWQB blob");
    if (get_u32(blob + 4) != QJS_BC_FORMAT)
        return JS_ThrowTypeError(ctx, "bytecode: container format %u, expected %u", get_u32(blob + 4), QJS_BC_FORMAT);
    if (get_u64(blob + 8) != (uint64_t)source_len || get_u64(blob + 16) != fnv1a64((const uint8_t *)source, source_len))
        return JS_ThrowTypeError(ctx, "bytecode: compiled from a different source (stale .qbc?)");
    JSValue fn = JS_ReadObject(ctx, blob + QJS_BC_HEADER, blob_len - QJS_BC_HEADER, JS_READ_OBJ_BYTECODE);
    if (JS_IsException(fn)) return fn;
    return JS_EvalFunction(ctx, fn); /* takes ownership of fn */
}

JSValue qjs_call(JSContext *ctx, JSValue fn, int argc, JSValue *argv) { return JS_Call(ctx, fn, JS_UNDEFINED, argc, argv); }
int qjs_set_global_prop(JSContext *ctx, const char *name, JSValue v) {
    JSValue g = JS_GetGlobalObject(ctx);
    int r = JS_SetPropertyStr(ctx, g, name, v);
    JS_FreeValue(ctx, g);
    return r;
}
JSValue qjs_get_global_prop(JSContext *ctx, const char *name) {
    JSValue g = JS_GetGlobalObject(ctx);
    JSValue v = JS_GetPropertyStr(ctx, g, name);
    JS_FreeValue(ctx, g);
    return v;
}

static const char *prop_cstr(JSContext *ctx, JSValue obj, const char *name) {
    JSValue v = JS_GetPropertyStr(ctx, obj, name);
    const char *s = (JS_IsUndefined(v) || JS_IsNull(v)) ? NULL : JS_ToCString(ctx, v);
    JS_FreeValue(ctx, v);
    return s;
}

char *qjs_take_exception_description(JSContext *ctx) {
    JSValue e = JS_GetException(ctx);
    char *out = NULL;
    if (JS_IsObject(e)) {
        const char *name = prop_cstr(ctx, e, "name");
        const char *msg = prop_cstr(ctx, e, "message");
        const char *stack = prop_cstr(ctx, e, "stack");
        size_t n = (name ? strlen(name) : 5) + (msg ? strlen(msg) : 0) + (stack ? strlen(stack) : 0) + 8;
        out = malloc(n);
        if (out) snprintf(out, n, "%s: %s%s%s", name ? name : "Error", msg ? msg : "", stack ? "\n" : "", stack ? stack : "");
        if (name) JS_FreeCString(ctx, name);
        if (msg) JS_FreeCString(ctx, msg);
        if (stack) JS_FreeCString(ctx, stack);
    } else {
        const char *s = JS_ToCString(ctx, e);
        out = strdup(s ? s : "unknown error");
        if (s) JS_FreeCString(ctx, s);
    }
    JS_FreeValue(ctx, e);
    return out;
}

bool qjs_is_job_pending(JSRuntime *rt) { return JS_IsJobPending(rt); }
int qjs_execute_pending_job(JSRuntime *rt, JSContext **pctx) { return JS_ExecutePendingJob(rt, pctx); }
size_t qjs_memory_used(JSRuntime *rt) {
    JSMemoryUsage u;
    JS_ComputeMemoryUsage(rt, &u);
    return (size_t)u.memory_used_size;
}

int qjs_typed_array_bytes(JSContext *ctx, JSValue v, uint8_t **ptr, size_t *len)
{
    int type = JS_GetTypedArrayType(v);
    if (type < 0)
        return -1;
    size_t offset = 0, length = 0, element = 0;
    JSValue buffer = JS_GetTypedArrayBuffer(ctx, v, &offset, &length, &element);
    if (JS_IsException(buffer))
        return -1;
    size_t size = 0;
    uint8_t *base = JS_GetArrayBuffer(ctx, &size, buffer);
    JS_FreeValue(ctx, buffer);
    if (!base || offset > size || length > size - offset)
        return -1;
    *ptr = base + offset;
    *len = length;
    return type;
}
