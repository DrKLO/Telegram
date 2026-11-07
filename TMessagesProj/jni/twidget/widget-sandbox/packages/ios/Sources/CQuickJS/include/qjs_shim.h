// Small C layer over quickjs-ng for Swift: hides macros, static inline helpers
// and JSValue's representation. Every function here is a thin pass-through.
#ifndef QJS_SHIM_H
#define QJS_SHIM_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "../quickjs/quickjs.h"

#ifdef __cplusplus
extern "C" {
#endif

JSValue qjs_undefined(void);
JSValue qjs_null(void);
bool qjs_is_exception(JSValue v);
bool qjs_is_undefined(JSValue v);
bool qjs_is_string(JSValue v);
bool qjs_is_number(JSValue v);
bool qjs_is_function(JSContext *ctx, JSValue v);
void qjs_free_value(JSContext *ctx, JSValue v);
JSValue qjs_dup_value(JSContext *ctx, JSValue v);

JSValue qjs_new_string(JSContext *ctx, const char *s);
JSValue qjs_new_string_len(JSContext *ctx, const char *s, size_t len);
JSValue qjs_new_float64(JSContext *ctx, double d);
JSValue qjs_new_array_buffer_copy(JSContext *ctx, const uint8_t *buf, size_t len);
JSValue qjs_throw_error(JSContext *ctx, const char *message);

/* Registers a C function with a `magic` index, so one trampoline can serve many names. */
JSValue qjs_new_cfunction_magic(JSContext *ctx, JSCFunctionMagic *fn, const char *name, int length, int magic);
/* The bytes a typed array views (valid until the next JS call); returns its JSTypedArrayEnum or -1. */
int qjs_typed_array_bytes(JSContext *ctx, JSValue v, uint8_t **ptr, size_t *len);

const char *qjs_to_cstring(JSContext *ctx, JSValue v);
void qjs_free_cstring(JSContext *ctx, const char *s);
double qjs_to_float64(JSContext *ctx, JSValue v);

/* `code` must have a NUL at code[len] (JS_Eval reads it); the same holds for qjs_compile_bytecode. */
JSValue qjs_eval(JSContext *ctx, const char *code, size_t len, const char *filename, bool as_module);

/* Precompiled bundles (dev-time `tools/qjsc`, loaded by every native port).
   The blob is a small header — magic "TWQB", format version, source length,
   FNV-1a 64 of the source — followed by quickjs-ng's own bytecode of the
   script compiled as global code. qjs_eval_bytecode checks the header against
   the source it would otherwise evaluate (a stale .qbc next to a newer .js is
   refused, not run) and quickjs-ng checks its own bytecode version; on any
   mismatch the result is an exception and the caller falls back to the source.
   Bytecode is only ever read from the host's own bundle: JS_ReadObject has no
   verifier, so this path never sees widget code. */
uint8_t *qjs_compile_bytecode(JSContext *ctx, const char *code, size_t len, const char *filename, size_t *out_len);
void qjs_free_bytecode(JSContext *ctx, uint8_t *blob);
JSValue qjs_eval_bytecode(JSContext *ctx, const uint8_t *blob, size_t blob_len, const char *source, size_t source_len);
JSValue qjs_call(JSContext *ctx, JSValue fn, int argc, JSValue *argv);
int qjs_set_global_prop(JSContext *ctx, const char *name, JSValue v);
JSValue qjs_get_global_prop(JSContext *ctx, const char *name);

/* "name: message\nstack" of the pending exception; free with free(). */
char *qjs_take_exception_description(JSContext *ctx);

bool qjs_is_job_pending(JSRuntime *rt);
/* >0 job executed, 0 none, <0 error (exception pending in *pctx). */
int qjs_execute_pending_job(JSRuntime *rt, JSContext **pctx);
size_t qjs_memory_used(JSRuntime *rt);

#ifdef __cplusplus
}
#endif
#endif
