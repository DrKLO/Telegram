#ifdef NDEBUG
#undef NDEBUG
#endif
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "quickjs.h"
#include "quickjs-libc.h"
#include "cutils.h"

static JSRuntime *new_runtime(void)
{
    JSRuntime *rt = JS_NewRuntime();

    if (rt)
        JS_SetDumpFlags(rt, JS_ABORT_ON_LEAKS);
    return rt;
}

static JSValue eval(JSContext *ctx, const char *code)
{
    return JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_GLOBAL);
}

static JSValue cfunc_callback(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    return JS_ThrowTypeError(ctx, "from cfunc");
}

static JSValue cfuncdata_callback(JSContext *ctx, JSValueConst this_val,
                                  int argc, JSValueConst *argv,
                                  int magic, JSValueConst *func_data)
{
    return JS_ThrowTypeError(ctx, "from cfuncdata");
}

static JSValue cclosure_callback(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv,
                                 int magic, void *func_data)
{
  return JS_ThrowTypeError(ctx, "from cclosure");
}

static bool closure_finalized = false;

static void cclosure_opaque_finalize(void *opaque)
{
    if ((intptr_t)opaque == 12)
        closure_finalized = true;
}

static void cfunctions(void)
{
    uint32_t length;
    const char *s;
    JSValue ret, stack;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue cfunc = JS_NewCFunction(ctx, cfunc_callback, "cfunc", 42);
    JSValue cfuncdata =
        JS_NewCFunctionData2(ctx, cfuncdata_callback, "cfuncdata",
                             /*length*/1337, /*magic*/0, /*data_len*/0, NULL);
    JSValue cclosure =
        JS_NewCClosure(ctx, cclosure_callback, "cclosure", cclosure_opaque_finalize,
                       /*length*/0xC0DE, /*magic*/11, /*opaque*/(void*)12);
    JSValue global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "cfunc", cfunc);
    JS_SetPropertyStr(ctx, global, "cfuncdata", cfuncdata);
    JS_SetPropertyStr(ctx, global, "cclosure", cclosure);
    JS_FreeValue(ctx, global);

    ret = eval(ctx, "cfunc.name");
    assert(!JS_IsException(ret));
    assert(JS_IsString(ret));
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "cfunc"));
    JS_FreeCString(ctx, s);
    ret = eval(ctx, "cfunc.length");
    assert(!JS_IsException(ret));
    assert(JS_IsNumber(ret));
    assert(0 == JS_ToUint32(ctx, &length, ret));
    assert(length == 42);

    ret = eval(ctx, "cfuncdata.name");
    assert(!JS_IsException(ret));
    assert(JS_IsString(ret));
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "cfuncdata"));
    JS_FreeCString(ctx, s);
    ret = eval(ctx, "cfuncdata.length");
    assert(!JS_IsException(ret));
    assert(JS_IsNumber(ret));
    assert(0 == JS_ToUint32(ctx, &length, ret));
    assert(length == 1337);

    ret = eval(ctx, "cclosure.name");
    assert(!JS_IsException(ret));
    assert(JS_IsString(ret));
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "cclosure"));
    JS_FreeCString(ctx, s);
    ret = eval(ctx, "cclosure.length");
    assert(!JS_IsException(ret));
    assert(JS_IsNumber(ret));
    assert(0 == JS_ToUint32(ctx, &length, ret));
    assert(length == 0xC0DE);

    ret = eval(ctx, "cfunc()");
    assert(JS_IsException(ret));
    ret = JS_GetException(ctx);
    assert(JS_IsError(ret));
    stack = JS_GetPropertyStr(ctx, ret, "stack");
    assert(JS_IsString(stack));
    s = JS_ToCString(ctx, stack);
    JS_FreeValue(ctx, stack);
    assert(s);
    assert(!strcmp(s, "    at cfunc (native)\n    at <eval> (<input>:1:1)\n"));
    JS_FreeCString(ctx, s);
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "TypeError: from cfunc"));
    JS_FreeCString(ctx, s);

    ret = eval(ctx, "cfuncdata()");
    assert(JS_IsException(ret));
    ret = JS_GetException(ctx);
    assert(JS_IsError(ret));
    stack = JS_GetPropertyStr(ctx, ret, "stack");
    assert(JS_IsString(stack));
    s = JS_ToCString(ctx, stack);
    JS_FreeValue(ctx, stack);
    assert(s);
    assert(!strcmp(s, "    at cfuncdata (native)\n    at <eval> (<input>:1:1)\n"));
    JS_FreeCString(ctx, s);
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "TypeError: from cfuncdata"));
    JS_FreeCString(ctx, s);

    ret = eval(ctx, "cclosure()");
    assert(JS_IsException(ret));
    ret = JS_GetException(ctx);
    assert(JS_IsError(ret));
    stack = JS_GetPropertyStr(ctx, ret, "stack");
    assert(JS_IsString(stack));
    s = JS_ToCString(ctx, stack);
    JS_FreeValue(ctx, stack);
    assert(s);
    assert(!strcmp(s, "    at cclosure (native)\n    at <eval> (<input>:1:1)\n"));
    JS_FreeCString(ctx, s);
    s = JS_ToCString(ctx, ret);
    JS_FreeValue(ctx, ret);
    assert(s);
    assert(!strcmp(s, "TypeError: from cclosure"));
    JS_FreeCString(ctx, s);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);

    assert(closure_finalized);
}

#define MAX_TIME 10

static int timeout_interrupt_handler(JSContext *ctx, void *opaque)
{
    int *time = (int *)opaque;
    return (*time)++ > MAX_TIME;
}

static void sync_call(void)
{
    static const char code[] =
"(function() { \
    try { \
        while (true) {} \
    } catch (e) {} \
})();";

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    int time = 0;
    JS_SetInterruptHandler(rt, timeout_interrupt_handler, &time);
    JSValue ret = eval(ctx, code);
    assert(time > MAX_TIME);
    assert(JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    assert(JS_HasException(ctx));
    JSValue e = JS_GetException(ctx);
    assert(JS_IsUncatchableError(e));
    JS_FreeValue(ctx, e);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void async_call(void)
{
    static const char code[] =
"(async function() { \
    const loop = async () => { \
        await Promise.resolve(); \
        while (true) {} \
    }; \
    await loop().catch(() => {}); \
})();";

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    int time = 0;
    JS_SetInterruptHandler(rt, timeout_interrupt_handler, &time);
    JSValue ret = eval(ctx, code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    assert(JS_IsJobPending(rt));
    int r = 0;
    while (JS_IsJobPending(rt)) {
        r = JS_ExecutePendingJob(rt, &ctx);
    }
    assert(time > MAX_TIME);
    assert(r == -1);
    assert(JS_HasException(ctx));
    JSValue e = JS_GetException(ctx);
    assert(JS_IsUncatchableError(e));
    JS_FreeValue(ctx, e);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void std_eval_interrupt_handler(void)
{
    static const char code[] =
        "import * as std from 'std'; std.evalScript('for(;;){}')";
    JSRuntime *rt = new_runtime();
    js_std_init_handlers(rt);
    JSContext *ctx = JS_NewContext(rt);
    js_init_module_std(ctx, "std");
    int time = 0;
    JS_SetInterruptHandler(rt, timeout_interrupt_handler, &time);
    JSValue ret =
        JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
    ret = js_std_await(ctx, ret);
    assert(time > MAX_TIME);
    assert(JS_IsException(ret));
    ret = JS_GetException(ctx);
    assert(JS_IsError(ret));
    // uncatchable "interrupted" exception is turned into a regular exception
    assert(!JS_IsUncatchableError(ret));
    const char *str = JS_ToCString(ctx, ret);
    assert(str != NULL);
    assert(strstr(str, "InternalError: interrupted"));
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, ret);
    uintptr_t interrupt_handler = js_std_cmd(/*GetInterruptHandler*/5, rt);
    uintptr_t interrupt_opaque = js_std_cmd(/*GetInterruptOpaque*/6, rt);
    assert(interrupt_handler == (uintptr_t)timeout_interrupt_handler);
    assert(interrupt_opaque == (uintptr_t)&time);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static JSValue save_value(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    assert(argc == 1);
    JSValue *p = (JSValue *)JS_GetContextOpaque(ctx);
    *p = JS_DupValue(ctx, argv[0]);
    return JS_UNDEFINED;
}

static void async_call_stack_overflow(void)
{
    static const char code[] =
"(async function() { \
    const f = () => f(); \
    try { \
        await Promise.resolve(); \
        f(); \
    } catch (e) { \
        save_value(e); \
    } \
})();";

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue value = JS_UNDEFINED;
    JS_SetContextOpaque(ctx, &value);
    JSValue global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "save_value", JS_NewCFunction(ctx, save_value, "save_value", 1));
    JS_FreeValue(ctx, global);
    JSValue ret = eval(ctx, code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    assert(JS_IsJobPending(rt));
    int r = 0;
    while (JS_IsJobPending(rt)) {
        r = JS_ExecutePendingJob(rt, &ctx);
    }
    assert(r == 1);
    assert(!JS_HasException(ctx));
    assert(JS_IsError(value)); // stack overflow should be caught
    JS_FreeValue(ctx, value);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

// https://github.com/quickjs-ng/quickjs/issues/914
static void raw_context_global_var(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContextRaw(rt);
    JS_AddIntrinsicEval(ctx);
    {
        JSValue ret = eval(ctx, "globalThis");
        assert(JS_IsException(ret));
        JS_FreeValue(ctx, ret);
    }
    {
        JSValue ret = eval(ctx, "var x = 42");
        assert(JS_IsUndefined(ret));
        JS_FreeValue(ctx, ret);
    }
    {
        JSValue ret = eval(ctx, "function f() {}");
        assert(JS_IsUndefined(ret));
        JS_FreeValue(ctx, ret);
    }
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void is_array(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    {
        JSValue ret = eval(ctx, "[]");
        assert(!JS_IsException(ret));
        assert(JS_IsArray(ret));
        JS_FreeValue(ctx, ret);
    }
    {
        JSValue ret = eval(ctx, "new Proxy([], {})");
        assert(!JS_IsException(ret));
        assert(!JS_IsArray(ret));
        assert(JS_IsProxy(ret));
        JSValue handler = JS_GetProxyHandler(ctx, ret);
        JSValue target = JS_GetProxyTarget(ctx, ret);
        assert(!JS_IsException(handler));
        assert(!JS_IsException(target));
        assert(!JS_IsProxy(handler));
        assert(!JS_IsProxy(target));
        assert(JS_IsObject(handler));
        assert(JS_IsArray(target));
        JS_FreeValue(ctx, handler);
        JS_FreeValue(ctx, target);
        JS_FreeValue(ctx, ret);
    }
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static int loader_calls;

static JSModuleDef *loader(JSContext *ctx, const char *name, void *opaque)
{
    loader_calls++;
    assert(!strcmp(name, "b"));
    static const char code[] = "export function f(x){}";
    JSValue ret = JS_Eval(ctx, code, strlen(code), "b",
                          JS_EVAL_TYPE_MODULE|JS_EVAL_FLAG_COMPILE_ONLY);
    assert(!JS_IsException(ret));
    JSModuleDef *m = JS_VALUE_GET_PTR(ret);
    assert(m);
    JS_FreeValue(ctx, ret);
    return m;
}

static void module_serde(void)
{
    JSRuntime *rt = new_runtime();
    //JS_SetDumpFlags(rt, JS_DUMP_MODULE_RESOLVE);
    JS_SetModuleLoaderFunc(rt, NULL, loader, NULL);
    JSContext *ctx = JS_NewContext(rt);
    static const char code[] = "import {f} from 'b'; f()";
    assert(loader_calls == 0);
    JSValue mod = JS_Eval(ctx, code, strlen(code), "a",
                          JS_EVAL_TYPE_MODULE|JS_EVAL_FLAG_COMPILE_ONLY);
    assert(loader_calls == 1);
    assert(!JS_IsException(mod));
    assert(JS_IsModule(mod));
    size_t len = 0;
    uint8_t *buf = JS_WriteObject(ctx, &len, mod,
                                  JS_WRITE_OBJ_BYTECODE|JS_WRITE_OBJ_REFERENCE);
    assert(buf);
    assert(len > 0);
    JS_FreeValue(ctx, mod);
    assert(loader_calls == 1);
    mod = JS_ReadObject(ctx, buf, len, JS_READ_OBJ_BYTECODE);
    js_free(ctx, buf);
    assert(loader_calls == 1); // 'b' is returned from cache
    assert(!JS_IsException(mod));
    JSValue ret = JS_EvalFunction(ctx, mod);
    assert(!JS_IsException(ret));
    assert(JS_IsPromise(ret));
    JSValue result = JS_PromiseResult(ctx, ret);
    assert(!JS_IsException(result));
    assert(JS_IsUndefined(result));
    JS_FreeValue(ctx, result);
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, mod);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

struct rejection_counts {
    int reject_count;
    int handle_count;
};

static void rejection_counter(JSContext *ctx, JSValueConst promise,
                              JSValueConst reason, bool is_handled, void *opaque)
{
    struct rejection_counts *c = opaque;
    if (is_handled)
        c->handle_count++;
    else
        c->reject_count++;
}

// A synchronous module that throws at top level must surface exactly one unhandled rejection
static void module_unhandled_rejection(void)
{
    struct rejection_counts c = {0, 0};
    JSRuntime *rt = new_runtime();
    JS_SetHostPromiseRejectionTracker(rt, rejection_counter, &c);
    JSContext *ctx = JS_NewContext(rt);

    static const char code[] = "throw new Error('Nuke the entire site from orbit. It\\'s the only way to be sure.')";
    JSValue v = JS_Eval(ctx, code, strlen(code), "<m>", JS_EVAL_TYPE_MODULE);
    JS_FreeValue(ctx, v);

    JSContext *c1;
    while (JS_ExecutePendingJob(rt, &c1) > 0)
        ;

    // net unhandled rejections == (2 rejects - 1 handled)
    assert(c.reject_count == 2);
    assert(c.handle_count == 1);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void promise_mark_as_handled(void)
{
    struct rejection_counts c = {0, 0};
    JSRuntime *rt = new_runtime();
    JS_SetHostPromiseRejectionTracker(rt, rejection_counter, &c);
    JSContext *ctx = JS_NewContext(rt);
    JSContext *c1;

    // marking an already-rejected promise notifies the tracker exactly once
    static const char code[] = "Promise.reject('kaboom')";
    JSValue promise = JS_Eval(ctx, code, strlen(code), "<t>", JS_EVAL_TYPE_GLOBAL);
    assert(JS_IsPromise(promise));
    while (JS_ExecutePendingJob(rt, &c1) > 0)
        ;
    assert(c.reject_count == 1);
    assert(c.handle_count == 0);
    JS_PromiseMarkAsHandled(ctx, promise);
    assert(c.handle_count == 1);
    JS_PromiseMarkAsHandled(ctx, promise);
    assert(c.handle_count == 1);
    JS_FreeValue(ctx, promise);

    // marking a pending promise suppresses the report when it later rejects
    JSValue resolving_funcs[2];
    JSValue promise2 = JS_NewPromiseCapability(ctx, resolving_funcs);
    JS_PromiseMarkAsHandled(ctx, promise2);
    JSValue reason = JS_NewString(ctx, "unseen");
    JSValue ret = JS_Call(ctx, resolving_funcs[1], JS_UNDEFINED, 1,
                          (JSValueConst *)&reason);
    while (JS_ExecutePendingJob(rt, &c1) > 0)
        ;
    assert(c.reject_count == 1);
    assert(c.handle_count == 1);
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, reason);
    JS_FreeValue(ctx, resolving_funcs[0]);
    JS_FreeValue(ctx, resolving_funcs[1]);
    JS_FreeValue(ctx, promise2);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void promise_then(void)
{
    const char *s;
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSContext *c1;
    JSValue got = JS_UNDEFINED;
    JS_SetContextOpaque(ctx, &got);

    // the result promise is intrinsic: neither an overridden then() nor
    // Symbol.species is consulted
    static const char code[] =
        "class P extends Promise {"
        "  static get [Symbol.species]() { throw new Error('species'); }"
        "  then() { throw new Error('then'); }"
        "}"
        "P.resolve('ok')";
    JSValue promise = eval(ctx, code);
    assert(JS_IsPromise(promise));
    JSValue on_fulfilled = JS_NewCFunction(ctx, save_value, "onFulfilled", 1);
    JSValue result = JS_PromiseThen(ctx, promise, on_fulfilled, JS_UNDEFINED);
    assert(JS_IsPromise(result));
    while (JS_ExecutePendingJob(rt, &c1) > 0)
        ;
    s = JS_ToCString(ctx, got);
    assert(s);
    assert(!strcmp(s, "ok"));
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, got);
    assert(JS_PromiseState(ctx, result) == JS_PROMISE_FULFILLED);
    JS_FreeValue(ctx, result);
    JS_FreeValue(ctx, on_fulfilled);
    JS_FreeValue(ctx, promise);

    // rejection handler receives the reason when the promise rejects later
    JSValue resolving_funcs[2];
    JSValue promise2 = JS_NewPromiseCapability(ctx, resolving_funcs);
    JSValue on_rejected = JS_NewCFunction(ctx, save_value, "onRejected", 1);
    JSValue result2 = JS_PromiseThen(ctx, promise2, JS_UNDEFINED, on_rejected);
    assert(JS_IsPromise(result2));
    got = JS_UNDEFINED;
    JSValue reason = JS_NewString(ctx, "boom");
    JSValue ret = JS_Call(ctx, resolving_funcs[1], JS_UNDEFINED, 1,
                          (JSValueConst *)&reason);
    while (JS_ExecutePendingJob(rt, &c1) > 0)
        ;
    s = JS_ToCString(ctx, got);
    assert(s);
    assert(!strcmp(s, "boom"));
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, got);
    assert(JS_PromiseState(ctx, result2) == JS_PROMISE_FULFILLED);
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, reason);
    JS_FreeValue(ctx, result2);
    JS_FreeValue(ctx, on_rejected);
    JS_FreeValue(ctx, resolving_funcs[0]);
    JS_FreeValue(ctx, resolving_funcs[1]);
    JS_FreeValue(ctx, promise2);

    // a non-promise argument is a TypeError
    JSValue bad = JS_PromiseThen(ctx, JS_UNDEFINED, JS_UNDEFINED, JS_UNDEFINED);
    assert(JS_IsException(bad));
    JSValue exc = JS_GetException(ctx);
    JS_FreeValue(ctx, exc);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void runtime_cstring_free(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    // string -> cstring + JS_FreeCStringRT
    {
        JSValue ret = eval(ctx, "\"testStringPleaseIgnore\"");
        assert(JS_IsString(ret));
        const char *s = JS_ToCString(ctx, ret);
        assert(s);
        assert(strcmp(s, "testStringPleaseIgnore") == 0);
        JS_FreeCStringRT(rt, s);
        JS_FreeValue(ctx, ret);
    }
    // string -> cstring + JS_FreeCStringRT, destroying the source value first
    {
        JSValue ret = eval(ctx, "\"testStringPleaseIgnore\"");
        assert(JS_IsString(ret));
        const char *s = JS_ToCString(ctx, ret);
        assert(s);
        JS_FreeValue(ctx, ret);
        assert(strcmp(s, "testStringPleaseIgnore") == 0);
        JS_FreeCStringRT(rt, s);
    }
    // number -> cstring + JS_FreeCStringRT
    {
        JSValue ret = eval(ctx, "123987");
        assert(JS_IsNumber(ret));
        const char *s = JS_ToCString(ctx, ret);
        assert(s);
        assert(strcmp(s, "123987") == 0);
        JS_FreeCStringRT(rt, s);
        JS_FreeValue(ctx, ret);
    }
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void utf16_string(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    {
        JSValue v = JS_NewStringUTF16(ctx, NULL, 0);
        assert(!JS_IsException(v));
        const char *s = JS_ToCString(ctx, v);
        assert(s);
        assert(!strcmp(s, ""));
        JS_FreeCString(ctx, s);
        JS_FreeValue(ctx, v);
    }
    {
        JSValue v = JS_NewStringUTF16(ctx, (uint16_t[]){'o','k'}, 2);
        assert(!JS_IsException(v));
        const char *s = JS_ToCString(ctx, v);
        assert(s);
        assert(!strcmp(s, "ok"));
        JS_FreeCString(ctx, s);
        size_t n;
        const uint16_t *u = JS_ToCStringLenUTF16(ctx, &n, v);
        assert(u);
        assert(n == 2);
        assert(u[0] == 'o');
        assert(u[1] == 'k');
        JS_FreeCStringUTF16(ctx, u);
        JS_FreeValue(ctx, v);
    }
    {
        JSValue v = JS_NewStringUTF16(ctx, (uint16_t[]){0xD800}, 1);
        assert(!JS_IsException(v));
        const char *s = JS_ToCString(ctx, v);
        assert(s);
        // questionable but surrogates don't map to UTF-8 without WTF-8
        assert(!strcmp(s, "\xED\xA0\x80"));
        JS_FreeCString(ctx, s);
        size_t n;
        const uint16_t *u = JS_ToCStringLenUTF16(ctx, &n, v);
        assert(u);
        assert(n == 1);
        assert(u[0] == 0xD800);
        JS_FreeCStringUTF16(ctx, u);
        JS_FreeValue(ctx, v);
    }
    {
        JSValue v = JS_NewStringLen(ctx, "ok", 2); // ascii -> ucs
        assert(!JS_IsException(v));
        size_t n;
        const uint16_t *u = JS_ToCStringLenUTF16(ctx, &n, v);
        assert(u);
        assert(n == 2);
        assert(u[0] == 'o');
        assert(u[1] == 'k');
        JS_FreeCStringUTF16(ctx, u);
        JS_FreeValue(ctx, v);
    }
    {
        JSValue v = JS_NewStringUTF16(ctx, NULL, (size_t)INT_MAX + 1);
        assert(JS_IsException(v));
        JSValue e = JS_GetException(ctx);
        assert(JS_IsError(e));
        const char *s = JS_ToCString(ctx, e);
        assert(s);
        assert(strstr(s, "invalid string length") != NULL);
        JS_FreeCString(ctx, s);
        JS_FreeValue(ctx, e);
    }
    {
        // create wide char slice string, see JS_STRING_SLICE_LEN_MAX
        JSValue v = eval(ctx, "`\\uD800\\uDC00`.repeat(8192).slice(0, -1)");
        assert(!JS_IsException(v));
        size_t n;
        const uint16_t *u = JS_ToCStringLenUTF16(ctx, &n, v);
        assert(u);
        assert(n == 2*8192-1);
        assert(u[0] == 0xD800);
        assert(u[1] == 0xDC00);
        JS_FreeCStringUTF16(ctx, u);
        JS_FreeValue(ctx, v);
    }
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void weak_map_gc_check(void)
{
    static const char init_code[] =
"const map = new WeakMap(); \
function addItem() { \
    const k = { \
        text: 'a', \
    }; \
    map.set(k, {k}); \
}";
    static const char test_code[] = "addItem()";

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    JSValue ret = eval(ctx, init_code);
    assert(!JS_IsException(ret));

    JSValue ret_test = eval(ctx, test_code);
    assert(!JS_IsException(ret_test));
    JS_RunGC(rt);
    JSMemoryUsage memory_usage;
    JS_ComputeMemoryUsage(rt, &memory_usage);

    for (int i = 0; i < 3; i++) {
        JSValue ret_test2 = eval(ctx, test_code);
        assert(!JS_IsException(ret_test2));
        JS_RunGC(rt);
        JSMemoryUsage memory_usage2;
        JS_ComputeMemoryUsage(rt, &memory_usage2);

        assert(memory_usage.memory_used_count == memory_usage2.memory_used_count);
        assert(memory_usage.memory_used_size == memory_usage2.memory_used_size);
        JS_FreeValue(ctx, ret_test2);
    }

    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, ret_test);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

struct {
    int hook_type_call_count[4];
} promise_hook_state;

static void promise_hook_cb(JSContext *ctx, JSPromiseHookType type,
                            JSValueConst promise, JSValueConst parent_promise,
                            void *opaque)
{
    assert(type == JS_PROMISE_HOOK_INIT ||
           type == JS_PROMISE_HOOK_BEFORE ||
           type == JS_PROMISE_HOOK_AFTER ||
           type == JS_PROMISE_HOOK_RESOLVE);
    promise_hook_state.hook_type_call_count[type]++;
    assert(opaque == (void *)&promise_hook_state);
    if (!JS_IsUndefined(parent_promise)) {
        JSValue global_object = JS_GetGlobalObject(ctx);
        JS_SetPropertyStr(ctx, global_object, "actual",
                          JS_DupValue(ctx, parent_promise));
        JS_FreeValue(ctx, global_object);
    }
}

static void promise_hook(void)
{
    int *cc = promise_hook_state.hook_type_call_count;
    JSContext *unused;
    JSRuntime *rt = new_runtime();
    //JS_SetDumpFlags(rt, JS_DUMP_PROMISE);
    JS_SetPromiseHook(rt, promise_hook_cb, &promise_hook_state);
    JSContext *ctx = JS_NewContext(rt);
    JSValue global_object = JS_GetGlobalObject(ctx);
    {
        // empty module; creates an outer and inner module promise;
        // JS_Eval returns the outer promise
        JSValue ret = JS_Eval(ctx, "", 0, "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret));
        JS_FreeValue(ctx, ret);
        assert(2 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(2 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(!JS_IsJobPending(rt));
    }
    memset(&promise_hook_state, 0, sizeof(promise_hook_state));
    {
        // module with unresolved promise; the outer and inner module promises
        // are resolved but not the user's promise
        static const char code[] = "new Promise(() => {})";
        JSValue ret = JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret)); // outer module promise
        JS_FreeValue(ctx, ret);
        assert(3 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(2 == cc[JS_PROMISE_HOOK_RESOLVE]); // outer and inner module promise
        assert(!JS_IsJobPending(rt));
    }
    memset(&promise_hook_state, 0, sizeof(promise_hook_state));
    {
        // module with resolved promise
        static const char code[] = "new Promise((resolve,reject) => resolve())";
        JSValue ret = JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret)); // outer module promise
        JS_FreeValue(ctx, ret);
        assert(3 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(3 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(!JS_IsJobPending(rt));
    }
    memset(&promise_hook_state, 0, sizeof(promise_hook_state));
    {
        // module with rejected promise
        static const char code[] = "new Promise((resolve,reject) => reject())";
        JSValue ret = JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret)); // outer module promise
        JS_FreeValue(ctx, ret);
        assert(3 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(2 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(!JS_IsJobPending(rt));
    }
    memset(&promise_hook_state, 0, sizeof(promise_hook_state));
    {
        // module with promise chain
        static const char code[] =
            "globalThis.count = 0;"
            "globalThis.actual = undefined;" // set by promise_hook_cb
            "globalThis.expected = new Promise(resolve => resolve());"
            "expected.then(_ => count++)";
        JSValue ret = JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret)); // outer module promise
        JS_FreeValue(ctx, ret);
        assert(4 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(3 == cc[JS_PROMISE_HOOK_RESOLVE]);
        JSValue v = JS_GetPropertyStr(ctx, global_object, "count");
        assert(!JS_IsException(v));
        int32_t count;
        assert(0 == JS_ToInt32(ctx, &count, v));
        assert(0 == count);
        JS_FreeValue(ctx, v);
        assert(JS_IsJobPending(rt));
        assert(1 == JS_ExecutePendingJob(rt, &unused));
        assert(!JS_HasException(ctx));
        assert(4 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(4 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(!JS_IsJobPending(rt));
        v = JS_GetPropertyStr(ctx, global_object, "count");
        assert(!JS_IsException(v));
        assert(0 == JS_ToInt32(ctx, &count, v));
        assert(1 == count);
        JS_FreeValue(ctx, v);
        JSValue actual = JS_GetPropertyStr(ctx, global_object, "actual");
        JSValue expected = JS_GetPropertyStr(ctx, global_object, "expected");
        assert(!JS_IsException(actual));
        assert(!JS_IsException(expected));
        assert(JS_IsSameValue(ctx, actual, expected));
        JS_FreeValue(ctx, actual);
        JS_FreeValue(ctx, expected);
    }
    memset(&promise_hook_state, 0, sizeof(promise_hook_state));
    {
        // module with thenable; fires before and after hooks
        static const char code[] =
            "new Promise(resolve => resolve({then(resolve){ resolve() }}))";
        JSValue ret = JS_Eval(ctx, code, strlen(code), "<input>", JS_EVAL_TYPE_MODULE);
        assert(!JS_IsException(ret));
        assert(JS_IsPromise(ret));
        assert(JS_PROMISE_FULFILLED == JS_PromiseState(ctx, ret)); // outer module promise
        JS_FreeValue(ctx, ret);
        assert(3 == cc[JS_PROMISE_HOOK_INIT]);
        assert(0 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(0 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(2 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(JS_IsJobPending(rt));
        assert(1 == JS_ExecutePendingJob(rt, &unused));
        assert(!JS_HasException(ctx));
        assert(3 == cc[JS_PROMISE_HOOK_INIT]);
        assert(1 == cc[JS_PROMISE_HOOK_BEFORE]);
        assert(1 == cc[JS_PROMISE_HOOK_AFTER]);
        assert(3 == cc[JS_PROMISE_HOOK_RESOLVE]);
        assert(!JS_IsJobPending(rt));
    }
    JS_FreeValue(ctx, global_object);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void dump_memory_usage(void)
{
    JSMemoryUsage stats;

    JSRuntime *rt = NULL;
    JSContext *ctx = NULL;

    rt = new_runtime();
    ctx = JS_NewContext(rt);

    //JS_SetDumpFlags(rt, JS_DUMP_PROMISE);

    static const char code[] =
    "globalThis.count = 0;"
    "globalThis.actual = undefined;" // set by promise_hook_cb
    "globalThis.expected = new Promise(resolve => resolve());"
    "expected.then(_ => count++)";

    JSValue evalVal = JS_Eval(ctx, code, strlen(code), "<input>", 0);
    JS_FreeValue(ctx, evalVal);

    FILE *temp = tmpfile();
    assert(temp != NULL);
    JS_ComputeMemoryUsage(rt, &stats);
    JS_DumpMemoryUsage(temp, &stats, rt);
    // JS_DumpMemoryUsage(stdout, &stats, rt);
    fclose(temp);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void new_errors(void)
{
    typedef struct {
        const char name[16];
        JSValue (*func)(JSContext *, const char *, ...);
    } Entry;
    static const Entry entries[] = {
        {"Error",           JS_NewPlainError},
        {"InternalError",   JS_NewInternalError},
        {"RangeError",      JS_NewRangeError},
        {"ReferenceError",  JS_NewReferenceError},
        {"SyntaxError",     JS_NewSyntaxError},
        {"TypeError",       JS_NewTypeError},
    };
    const Entry *e;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    for (e = entries; e < endof(entries); e++) {
        JSValue obj = (*e->func)(ctx, "the %s", "needle");
        assert(!JS_IsException(obj));
        assert(JS_IsObject(obj));
        assert(JS_IsError(obj));
        const char *haystack = JS_ToCString(ctx, obj);
        char needle[256];
        snprintf(needle, sizeof(needle), "%s: the needle", e->name);
        assert(strstr(haystack, needle));
        JS_FreeCString(ctx, haystack);
        JSValue stack = JS_GetPropertyStr(ctx, obj, "stack");
        assert(!JS_IsException(stack));
        assert(JS_IsString(stack));
        JS_FreeValue(ctx, stack);
        JS_FreeValue(ctx, obj);
    }
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

// JS_NewContext() already installs DOMException by way of
// JS_AddIntrinsicAToB(), so a host that also calls JS_AddIntrinsicDOMException()
// explicitly installs it twice. The second install must release the prototype
// the first one left in ctx->class_proto[] instead of overwriting the slot;
// new_runtime() aborts at JS_FreeRuntime() if it does not.
static void dom_exception_added_twice(void)
{
    JSValue ret;
    const char *s;
    int i;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    // the implicit install left a working DOMException behind
    ret = eval(ctx, "DOMException.prototype === "
                    "Object.getPrototypeOf(new DOMException)");
    assert(JS_ToBool(ctx, ret) == true);
    JS_FreeValue(ctx, ret);

    // installing it again, repeatedly, must not leak the previous prototype
    for (i = 0; i < 3; i++)
        assert(JS_AddIntrinsicDOMException(ctx) == 0);

    // and the class prototype still matches the global constructor, i.e. the
    // slot tracks the newest install rather than a freed or stale object
    ret = eval(ctx, "DOMException.prototype === "
                    "Object.getPrototypeOf(new DOMException)");
    assert(JS_ToBool(ctx, ret) == true);
    JS_FreeValue(ctx, ret);

    ret = eval(ctx, "new DOMException('m', 'InvalidCharacterError').name");
    assert(!JS_IsException(ret));
    s = JS_ToCString(ctx, ret);
    assert(s);
    assert(!strcmp(s, "InvalidCharacterError"));
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, ret);

    // the internally thrown DOMExceptions pick up the current prototype too
    ret = eval(ctx, "try { atob('a') } catch (e) { "
                    "  e instanceof DOMException && e.name } ");
    assert(!JS_IsException(ret));
    s = JS_ToCString(ctx, ret);
    assert(s);
    assert(!strcmp(s, "InvalidCharacterError"));
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, ret);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void backtrace_oom_callsite_array(void)
{
    static const char setup_code[] =
        "Error.prepareStackTrace = (e, frames) => frames;\n"
        "globalThis.f = () => new Error();\n";
    JSValue global_object, func, ret;
    JSMemoryUsage stats;
    uint32_t headroom;
    JSRuntime *rt;
    JSContext *ctx;

    rt = new_runtime();
    ctx = JS_NewContext(rt);
    global_object = JS_GetGlobalObject(ctx);

    ret = eval(ctx, setup_code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);

    func = JS_GetPropertyStr(ctx, global_object, "f");
    assert(JS_IsFunction(ctx, func));

    for (headroom = 0; headroom < 2048; headroom++) {
        JS_ComputeMemoryUsage(rt, &stats);
        JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + headroom);
        ret = JS_Call(ctx, func, JS_UNDEFINED, 0, NULL);
        JS_SetMemoryLimit(rt, 0);
        JS_FreeValue(ctx, ret);
        JS_FreeValue(ctx, JS_GetException(ctx));
    }

    JS_FreeValue(ctx, func);
    JS_FreeValue(ctx, global_object);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void large_allocation_accounting(void)
{
    static const size_t block_size = 1024 * 1024;
    JSMemoryUsage before, after;
    JSValue ret, exception;
    JSRuntime *rt;
    JSContext *ctx;
    const char *str;

    rt = new_runtime();
    ctx = JS_NewContext(rt);

    /* the shapes JS_NewContext leaves behind are freed by the first GC inside
       eval(); collect them first so that the delta is the block alone */
    JS_RunGC(rt);
    JS_ComputeMemoryUsage(rt, &before);
    ret = eval(ctx, "globalThis.a = new Uint8Array(1024 * 1024)");
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    JS_ComputeMemoryUsage(rt, &after);
    assert(after.malloc_size - before.malloc_size >= (int64_t)block_size);

    JS_ComputeMemoryUsage(rt, &before);
    JS_SetMemoryLimit(rt, (size_t)before.malloc_size + 4 * block_size);
    ret = eval(ctx, "globalThis.a = [];\n"
                    "for (let i = 0; i < 64; i++) {\n"
                    "    // OOM exception should be uncatchable\n"
                    "    try {\n"
                    "        a.push(new Uint8Array(1024 * 1024));"
                    "    } catch (e) {\n"
                    "        throw 'unexpected exception';\n"
                    "    }\n"
                    "}");
    assert(JS_IsException(ret));
    JS_SetMemoryLimit(rt, 0);
    JS_ComputeMemoryUsage(rt, &after);
    assert(after.malloc_size - before.malloc_size < (int64_t)(8 * block_size));

    exception = JS_GetException(ctx);
    assert(JS_IsError(exception));
    str = JS_ToCString(ctx, exception);
    assert(str);
    assert(!strcmp(str, "InternalError: out of memory"));
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exception);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void backtrace_oom_current_exception(void)
{
    static const char setup_code[] =
        "globalThis.f = function() { missing; };\n"
        "Object.defineProperty(f, 'name', { value: 'x'.repeat(2 * 1024 * 1024) });";
    JSMemoryUsage stats;
    JSValue ret, exception;
    JSRuntime *rt;
    JSContext *ctx;

    rt = new_runtime();
    ctx = JS_NewContext(rt);

    ret = eval(ctx, setup_code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);

    JS_ComputeMemoryUsage(rt, &stats);
    JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + 128 * 1024);

    ret = eval(ctx, "f()");
    assert(JS_IsException(ret));
    assert(JS_HasException(ctx));
    exception = JS_GetException(ctx);
    JS_FreeValue(ctx, exception);
    JS_SetMemoryLimit(rt, 0);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void proxy_own_keys_huge_length(void)
{
    static const char setup_code[] =
        "globalThis.p = new Proxy({}, {\n"
        "    ownKeys() { return { length: 0x20000000 }; },\n"
        "});";
    JSMemoryUsage stats;
    JSValue ret, exception;
    JSRuntime *rt;
    JSContext *ctx;
    const char *str;

    rt = new_runtime();
    ctx = JS_NewContext(rt);

    ret = eval(ctx, setup_code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);

    JS_ComputeMemoryUsage(rt, &stats);
    JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + 128 * 1024);

    ret = eval(ctx, "Object.keys(p)");
    assert(JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    exception = JS_GetException(ctx);
    str = JS_ToCString(ctx, exception);
    assert(str);
    /* the trap result has no index 0, so the first key must be rejected */
    assert(!strcmp(str, "TypeError: proxy: properties must be strings or symbols"));
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, exception);
    JS_SetMemoryLimit(rt, 0);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static int gop_get_own_property(JSContext *ctx, JSPropertyDescriptor *desc,
                                JSValueConst obj, JSAtom prop)
{
    const char *name;
    int found;

    found = 0;
    name = JS_AtomToCString(ctx, prop);
    if (!name)
        return -1;
    if (!strcmp(name, "answer")) {
        found = 1;
        *desc = (JSPropertyDescriptor){
            .value = JS_NewInt32(ctx, 42),
            .flags = JS_PROP_C_W_E | JS_PROP_HAS_VALUE,
        };
    }
    JS_FreeCString(ctx, name);
    return found;
}

static void global_object_prototype(void)
{
    static const char code[] = "answer";
    JSValue global_object, proto, ret;
    JSClassID class_id;
    JSRuntime *rt;
    JSContext *ctx;
    int32_t answer;
    int res;

    {
        rt = new_runtime();
        ctx = JS_NewContext(rt);
        proto = JS_NewObject(ctx);
        assert(JS_IsObject(proto));
        JSCFunctionListEntry prop = JS_PROP_INT32_DEF("answer", 42, JS_PROP_C_W_E);
        JS_SetPropertyFunctionList(ctx, proto, &prop, 1);
        global_object = JS_GetGlobalObject(ctx);
        res = JS_SetPrototype(ctx, global_object, proto);
        assert(res == true);
        JS_FreeValue(ctx, global_object);
        JS_FreeValue(ctx, proto);
        ret = eval(ctx, code);
        assert(!JS_IsException(ret));
        assert(JS_IsNumber(ret));
        res = JS_ToInt32(ctx, &answer, ret);
        assert(res == 0);
        assert(answer == 42);
        JS_FreeValue(ctx, ret);
        JS_FreeContext(ctx);
        JS_FreeRuntime(rt);
    }
    {
        JSClassExoticMethods exotic = (JSClassExoticMethods){
            .get_own_property = gop_get_own_property,
        };
        JSClassDef def = (JSClassDef){
            .class_name = "Global Object",
            .exotic = &exotic,
        };
        rt = new_runtime();
        class_id = 0;
        JS_NewClassID(rt, &class_id);
        res = JS_NewClass(rt, class_id, &def);
        assert(res == 0);
        ctx = JS_NewContext(rt);
        proto = JS_NewObjectClass(ctx, class_id);
        assert(JS_IsObject(proto));
        global_object = JS_GetGlobalObject(ctx);
        res = JS_SetPrototype(ctx, global_object, proto);
        assert(res == true);
        JS_FreeValue(ctx, global_object);
        JS_FreeValue(ctx, proto);
        ret = eval(ctx, code);
        assert(!JS_IsException(ret));
        assert(JS_IsNumber(ret));
        res = JS_ToInt32(ctx, &answer, ret);
        assert(res == 0);
        assert(answer == 42);
        JS_FreeValue(ctx, ret);
        JS_FreeContext(ctx);
        JS_FreeRuntime(rt);
    }
}

// https://github.com/quickjs-ng/quickjs/issues/1178
static void slice_string_tocstring(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue ret = eval(ctx, "'.'.repeat(16384).slice(1, -1)");
    assert(!JS_IsException(ret));
    assert(JS_IsString(ret));
    const char *str = JS_ToCString(ctx, ret);
    assert(strlen(str) == 16382);
    JS_FreeCString(ctx, str);
    JS_FreeValue(ctx, ret);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void immutable_array_buffer(void)
{
    JSValue obj, ret;
    bool immutable;
    char buf[96];
    int i, v;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    for (i = 0; i < 2; i++) {
        obj = JS_NewObject(ctx);
        immutable = (i == 0);
        assert(-1 == JS_IsImmutableArrayBuffer(JS_NULL));
        assert(-1 == JS_IsImmutableArrayBuffer(JS_UNDEFINED));
        assert(-1 == JS_IsImmutableArrayBuffer(obj));
        assert(-1 == JS_SetImmutableArrayBuffer(JS_NULL, immutable));
        assert(-1 == JS_SetImmutableArrayBuffer(JS_UNDEFINED, immutable));
        assert(-1 == JS_SetImmutableArrayBuffer(obj, immutable));
        JS_FreeValue(ctx, obj);
    }
    obj = eval(ctx, "globalThis.ab = new ArrayBuffer(1)");
    assert(!JS_IsException(obj));
    assert(JS_IsArrayBuffer(obj));
    assert(!JS_IsImmutableArrayBuffer(obj));
    for (i = 1; i <= 3; i++) {
        immutable = (i == 2);
        if (i > 1)
            JS_SetImmutableArrayBuffer(obj, immutable);
        assert(immutable == JS_IsImmutableArrayBuffer(obj));
        snprintf(buf, sizeof(buf),
                 "var ta = new Uint8Array(ab); ta[0] = %d; ta[0]", i);
        ret = eval(ctx, buf);
        assert(!JS_IsException(ret));
        assert(JS_IsNumber(ret));
        assert(0 == JS_ToInt32(ctx, &v, ret));
        JS_FreeValue(ctx, ret);
        if (immutable) {
            assert(v != i);
        } else {
            assert(v == i);
        }
    }
    JS_FreeValue(ctx, obj);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void *sab_test_alloc(void *opaque, size_t size)
{
    return malloc(size);
}

static void sab_test_free(void *opaque, void *ptr)
{
    free(ptr);
}

static void shared_array_buffer_growth(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue ret, exception;

    ret = eval(ctx, "new SharedArrayBuffer(16)");
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);

    ret = eval(ctx,
               "const sab = new SharedArrayBuffer(16, { maxByteLength: 16 });"
               "sab.grow(16);"
               "sab.byteLength === 16 && sab.maxByteLength === 16");
    assert(!JS_IsException(ret));
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    ret = eval(ctx, "new SharedArrayBuffer(16, { maxByteLength: 16384 })");
    assert(JS_IsException(ret));
    assert(JS_HasException(ctx));
    exception = JS_GetException(ctx);
    assert(JS_IsError(exception));
    JS_FreeValue(ctx, exception);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);

    JSSharedArrayBufferFunctions funcs = {
        .sab_alloc = sab_test_alloc,
        .sab_free = sab_test_free,
        .sab_dup = NULL,
        .sab_opaque = NULL,
    };

    rt = new_runtime();
    JS_SetSharedArrayBufferFunctions(rt, &funcs);
    ctx = JS_NewContext(rt);
    ret = eval(ctx,
               "const sab = new SharedArrayBuffer(16, { maxByteLength: 16384 });"
               "const u8 = new Uint8Array(sab);"
               "sab.grow(16384);"
               "u8[1024] === 0 && u8.byteLength === 16384");
    assert(!JS_IsException(ret));
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void get_uint8array(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue val;
    uint8_t *p;
    size_t size;
    uint8_t buf[3] = { 1, 2, 3 };

    val = eval(ctx, "new Uint8Array(0)");
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p != NULL);
    assert(size == 0);
    JS_FreeValue(ctx, val);

    val = JS_NewUint8Array(ctx, NULL, 0, NULL, NULL, false);
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p != NULL);
    assert(size == 0);
    JS_FreeValue(ctx, val);

    val = JS_NewUint8ArrayCopy(ctx, NULL, 0);
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p != NULL);
    assert(size == 0);
    JS_FreeValue(ctx, val);

    val = JS_NewUint8ArrayCopy(ctx, buf, sizeof(buf));
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p != NULL);
    assert(size == 3);
    assert(p[0] == 1 && p[1] == 2 && p[2] == 3);
    JS_FreeValue(ctx, val);

    val = eval(ctx, "new Uint8Array([4, 5, 6])");
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p != NULL);
    assert(size == 3);
    assert(p[0] == 4 && p[1] == 5 && p[2] == 6);
    JS_FreeValue(ctx, val);

    val = eval(ctx, "new Int32Array(4)");
    assert(!JS_IsException(val));
    p = JS_GetUint8Array(ctx, &size, val);
    assert(p == NULL);
    assert(size == 0);
    JS_FreeValue(ctx, val);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void new_symbol(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue global = JS_GetGlobalObject(ctx);
    JSValue sym, ret;

    /* Local symbol with NULL description -> Symbol() */
    sym = JS_NewSymbol(ctx, NULL, false);
    assert(!JS_IsException(sym));
    assert(JS_IsSymbol(sym));
    JS_SetPropertyStr(ctx, global, "sym_local_null", sym);

    ret = eval(ctx, "typeof sym_local_null === 'symbol' && sym_local_null.description === undefined && Symbol.keyFor(sym_local_null) === undefined");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    /* Global symbol with NULL description -> Symbol.for() -> Symbol.for('undefined') */
    sym = JS_NewSymbol(ctx, NULL, true);
    assert(!JS_IsException(sym));
    assert(JS_IsSymbol(sym));
    JS_SetPropertyStr(ctx, global, "sym_global_null", sym);

    ret = eval(ctx, "typeof sym_global_null === 'symbol' && sym_global_null.description === 'undefined' && Symbol.keyFor(sym_global_null) === 'undefined'");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    /* Local symbol with description -> Symbol('test_local') */
    sym = JS_NewSymbol(ctx, "test_local", false);
    assert(!JS_IsException(sym));
    assert(JS_IsSymbol(sym));
    JS_SetPropertyStr(ctx, global, "sym_local_str", sym);

    ret = eval(ctx, "sym_local_str.description === 'test_local' && Symbol.keyFor(sym_local_str) === undefined");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    /* Global symbol with description -> Symbol.for('test_global') */
    sym = JS_NewSymbol(ctx, "test_global", true);
    assert(!JS_IsException(sym));
    assert(JS_IsSymbol(sym));
    JS_SetPropertyStr(ctx, global, "sym_global_str", sym);

    ret = eval(ctx, "sym_global_str.description === 'test_global' && Symbol.keyFor(sym_global_str) === 'test_global'");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    JS_FreeValue(ctx, global);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void bulk_free_macros(void) {
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    JSValue val0 = JS_NewObject(ctx);
    JSValue val1 = JS_NewArray(ctx);
    JSValue val2 = JS_NewDate(ctx, 0.0);
    
    JSMemoryUsage mem_usage;
    JS_ComputeMemoryUsage(rt, &mem_usage);
    int obj_count = mem_usage.obj_count;

    JS_FreeValues(ctx, val0, val1);
    JS_FreeValuesRT(rt, val2);

    // silly atoms to ensure qjs doesn't find built-ins that match
    JSAtom atom0 = JS_NewAtom(ctx, "ALL!!");
    JSAtom atom1 = JS_NewAtom(ctx, "YOUR!!");
    JSAtom atom2 = JS_NewAtom(ctx, "ATOMS!!");
    JSAtom atom3 = JS_NewAtom(ctx, "ARE!!");
    JSAtom atom4 = JS_NewAtom(ctx, "BELONG!!");
    JSAtom atom5 = JS_NewAtom(ctx, "TO US!!");

    JS_ComputeMemoryUsage(rt, &mem_usage);
    assert((obj_count - 3) == mem_usage.obj_count);
    int atom_count = mem_usage.atom_count;

    JS_FreeAtoms(ctx, atom1, atom3, atom4);
    JS_FreeAtomsRT(rt, atom0, atom2, atom5);

    JS_ComputeMemoryUsage(rt, &mem_usage);
    assert((atom_count - 6) == mem_usage.atom_count);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static int detach_free_count;

static void *detach_realloc_func(JSRuntime *rt, void *opaque, void *ptr,
                                 size_t size)
{
    if (size == 0) {
        detach_free_count++;
        free(ptr);
        return NULL;
    }
    return realloc(ptr, size);
}

static void detach_array_buffer_free_once(void)
{
    JSValue obj;
    uint8_t *buf;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    detach_free_count = 0;
    buf = malloc(8);
    obj = JS_NewArrayBuffer(ctx, buf, 8, /*max_len*/0, detach_realloc_func,
                            NULL, false);
    assert(JS_IsArrayBuffer(obj));

    /* detaching releases the backing store exactly once */
    JS_DetachArrayBuffer(ctx, obj);
    assert(detach_free_count == 1);

    /* finalizing the detached buffer must not release it a second time */
    JS_FreeValue(ctx, obj);
    assert(detach_free_count == 1);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void *realloc_array_buffer_data(JSRuntime *rt, void *opaque, void *ptr,
                                       size_t size)
{
    if (size == 0) {
        free(ptr);
        return NULL;
    }
    return realloc(ptr, size);
}

static void typed_array_sort_index_overflow(void)
{
    const size_t len = 0x40000010; /* 2**30 + 16 */
    JSMemoryUsage stats;
    JSValue global, buffer, ret;
    JSRuntime *rt;
    JSContext *ctx;
    uint8_t *buf;

    /* the data is never read, but keep it real memory so a regression
       corrupts the heap in a way ASan can see rather than segfaulting */
    buf = calloc(1, len);
    rt = new_runtime();
    ctx = JS_NewContext(rt);

    buffer = JS_NewArrayBuffer(ctx, buf, len, /*max_len*/0,
                               realloc_array_buffer_data, NULL, false);
    assert(JS_IsArrayBuffer(buffer));
    global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "buf", buffer);
    JS_FreeValue(ctx, global);

    /* Cap memory so that the correctly computed 4 GB index array also
       fails to allocate on 64 bit platforms; the undersized allocation a
       32 bit platform computes fits well within the cap. */
    JS_ComputeMemoryUsage(rt, &stats);
    JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + 1024 * 1024);

    ret = eval(ctx, "new Uint8Array(buf).sort((a, b) => a - b)");
    assert(JS_IsException(ret));
    assert(JS_HasException(ctx));
    JS_FreeValue(ctx, ret);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static int transfer_free_count;
static int transfer_realloc_count;

static void *transfer_realloc_func(JSRuntime *rt, void *opaque, void *ptr,
                                   size_t size)
{
    if (size == 0) {
        transfer_free_count++;
        free(ptr);
        return NULL;
    }
    transfer_realloc_count++;
    return realloc(ptr, size);
}

/* a realloc function that can only free, never resize */
static void *failing_realloc_func(JSRuntime *rt, void *opaque, void *ptr,
                                  size_t size)
{
    if (size == 0) {
        transfer_free_count++;
        free(ptr);
    }
    return NULL;
}

static JSValue make_external_ab(JSContext *ctx, uint8_t **pbuf, size_t len,
                                size_t max_len,
                                JSReallocArrayBufferDataFunc *realloc_func)
{
    uint8_t *buf = malloc(max_len > len ? max_len : len);
    memset(buf, 0xAB, len);
    JSValue obj = JS_NewArrayBuffer(ctx, buf, len, max_len, realloc_func, NULL,
                                    /*is_shared*/false);
    assert(JS_IsArrayBuffer(obj));
    JSValue global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "ab", JS_DupValue(ctx, obj));
    JS_FreeValue(ctx, global);
    *pbuf = buf;
    return obj;
}

static void transfer_external_array_buffer(void)
{
    JSValue obj, ret, exc, det;
    const char *s;
    uint8_t *buf, *p;
    size_t i, size;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    // same-length transfer succeeds and reuses the backing store; each variant
    // detaches the source, so use a fresh buffer per iteration
    {
        static const char *const exprs[] = {
            "ab.transfer()", "ab.transfer(8)", "ab.transferToFixedLength()",
            "ab.transferToFixedLength(8)", "ab.transferToImmutable()",
        };
        for (i = 0; i < countof(exprs); i++) {
            transfer_free_count = 0;
            transfer_realloc_count = 0;
            obj = make_external_ab(ctx, &buf, 8, /*max_len*/0,
                                   transfer_realloc_func);

            ret = eval(ctx, exprs[i]);
            assert(!JS_IsException(ret));
            assert(JS_IsArrayBuffer(ret));
            assert(transfer_realloc_count == 0);
            p = JS_GetArrayBuffer(ctx, &size, ret);
            assert(p == buf);
            assert(size == 8);
            assert(p[0] == 0xAB && p[7] == 0xAB);
            det = eval(ctx, "ab.detached");
            assert(JS_IsBool(det));
            assert(JS_VALUE_GET_BOOL(det));
            JS_FreeValue(ctx, det);
            assert(transfer_free_count == 0);
            JS_FreeValue(ctx, ret);
            assert(transfer_free_count == 1);

            ret = eval(ctx, "globalThis.ab = undefined");
            JS_FreeValue(ctx, ret);
            JS_FreeValue(ctx, obj);
            assert(transfer_free_count == 1);
        }
    }

    // a length-changing transfer resizes the external store through the
    // realloc function and hands it over to the new ArrayBuffer
    {
        static const struct {
            const char *code;
            size_t len;
        } cases[] = {
            { "ab.transfer(4)", 4 },
            { "ab.transfer(16)", 16 },
            { "ab.transferToFixedLength(4)", 4 },
            { "ab.transferToImmutable(16)", 16 },
        };
        for (i = 0; i < countof(cases); i++) {
            transfer_free_count = 0;
            transfer_realloc_count = 0;
            obj = make_external_ab(ctx, &buf, 8, /*max_len*/0,
                                   transfer_realloc_func);

            ret = eval(ctx, cases[i].code);
            assert(!JS_IsException(ret));
            assert(JS_IsArrayBuffer(ret));
            assert(transfer_realloc_count == 1);
            assert(transfer_free_count == 0);
            p = JS_GetArrayBuffer(ctx, &size, ret);
            assert(size == cases[i].len);
            assert(p[0] == 0xAB && p[3] == 0xAB);
            if (cases[i].len > 8)
                assert(p[8] == 0 && p[15] == 0); // the grown tail is zeroed
            det = eval(ctx, "ab.detached");
            assert(JS_IsBool(det));
            assert(JS_VALUE_GET_BOOL(det));
            JS_FreeValue(ctx, det);

            // the source handed the store over; releasing the new AB frees it
            JS_FreeValue(ctx, ret);
            assert(transfer_free_count == 1);

            ret = eval(ctx, "globalThis.ab = undefined");
            JS_FreeValue(ctx, ret);
            JS_FreeValue(ctx, obj);
            assert(transfer_free_count == 1);
        }
    }

    // a transfer that fails to realloc throws and leaves the source alone
    {
        transfer_free_count = 0;
        obj = make_external_ab(ctx, &buf, 8, /*max_len*/0,
                               failing_realloc_func);

        ret = eval(ctx, "ab.transfer(16)");
        assert(JS_IsException(ret));
        JS_FreeValue(ctx, ret);
        exc = JS_GetException(ctx);
        assert(JS_IsError(exc));
        s = JS_ToCString(ctx, exc);
        assert(s);
        assert(strstr(s, "out of memory"));
        JS_FreeCString(ctx, s);
        JS_FreeValue(ctx, exc);

        det = eval(ctx, "ab.detached");
        assert(JS_IsBool(det));
        assert(!JS_VALUE_GET_BOOL(det));
        JS_FreeValue(ctx, det);
        p = JS_GetArrayBuffer(ctx, &size, obj);
        assert(p == buf && size == 8 && p[0] == 0xAB);
        assert(transfer_free_count == 0);

        ret = eval(ctx, "globalThis.ab = undefined");
        JS_FreeValue(ctx, ret);
        JS_FreeValue(ctx, obj);
        assert(transfer_free_count == 1);
    }

    // transfer(0) changes the length but doesn't realloc: it detaches the
    // source (freeing the external store) and yields a fresh empty AB
    {
        transfer_free_count = 0;
        transfer_realloc_count = 0;
        obj = make_external_ab(ctx, &buf, 8, /*max_len*/0,
                               transfer_realloc_func);

        ret = eval(ctx, "ab.transfer(0)");
        assert(!JS_IsException(ret));
        assert(JS_IsArrayBuffer(ret));
        p = JS_GetArrayBuffer(ctx, &size, ret);
        assert(size == 0);
        assert(transfer_free_count == 1);
        JS_FreeValue(ctx, ret);

        det = eval(ctx, "ab.detached");
        assert(JS_IsBool(det));
        assert(JS_VALUE_GET_BOOL(det));
        JS_FreeValue(ctx, det);

        ret = eval(ctx, "globalThis.ab = undefined");
        JS_FreeValue(ctx, ret);
        JS_FreeValue(ctx, obj);
        assert(transfer_free_count == 1);
    }

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void resize_external_array_buffer(void)
{
    JSValue obj, global, ret, exc, det;
    const char *s;
    uint8_t *buf, *p;
    size_t size;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    transfer_free_count = 0;
    transfer_realloc_count = 0;
    obj = make_external_ab(ctx, &buf, 8, /*max_len*/16, transfer_realloc_func);

    ret = eval(ctx, "ab.resizable && ab.maxByteLength === 16");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);

    // growing reallocs through the callback and zeroes the new tail
    ret = eval(ctx, "ab.resize(16), ab.byteLength");
    assert(!JS_IsException(ret));
    assert(JS_VALUE_GET_INT(ret) == 16);
    JS_FreeValue(ctx, ret);
    assert(transfer_realloc_count == 1);
    p = JS_GetArrayBuffer(ctx, &size, obj);
    assert(size == 16);
    assert(p[0] == 0xAB && p[7] == 0xAB);
    assert(p[8] == 0 && p[15] == 0);

    // and so does shrinking
    ret = eval(ctx, "ab.resize(4), ab.byteLength");
    assert(!JS_IsException(ret));
    assert(JS_VALUE_GET_INT(ret) == 4);
    JS_FreeValue(ctx, ret);
    assert(transfer_realloc_count == 2);
    p = JS_GetArrayBuffer(ctx, &size, obj);
    assert(size == 4);
    assert(p[0] == 0xAB && p[3] == 0xAB);

    // resizing past maxByteLength still throws, without calling the callback
    ret = eval(ctx, "ab.resize(17)");
    assert(JS_IsException(ret));
    JS_FreeValue(ctx, ret);
    exc = JS_GetException(ctx);
    s = JS_ToCString(ctx, exc);
    assert(s);
    assert(strstr(s, "invalid array buffer length"));
    JS_FreeCString(ctx, s);
    JS_FreeValue(ctx, exc);
    assert(transfer_realloc_count == 2);

    // a resizable external AB carries its maxByteLength across a transfer
    ret = eval(ctx, "ab.transfer(8)");
    assert(!JS_IsException(ret));
    assert(transfer_realloc_count == 3);
    det = eval(ctx, "ab.detached");
    assert(JS_IsBool(det));
    assert(JS_VALUE_GET_BOOL(det));
    JS_FreeValue(ctx, det);
    global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "ab2", ret);
    JS_FreeValue(ctx, global);
    ret = eval(ctx, "ab2.resizable && ab2.maxByteLength === 16");
    assert(JS_IsBool(ret));
    assert(JS_VALUE_GET_BOOL(ret));
    JS_FreeValue(ctx, ret);
    ret = eval(ctx, "ab2.resize(16), ab2.byteLength");
    assert(!JS_IsException(ret));
    assert(JS_VALUE_GET_INT(ret) == 16);
    JS_FreeValue(ctx, ret);
    assert(transfer_realloc_count == 4);

    assert(transfer_free_count == 0);
    ret = eval(ctx, "globalThis.ab = globalThis.ab2 = undefined");
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, obj);
    assert(transfer_free_count == 1);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

/* hands the buffer over to the runtime allocator, the way quickjs manages the
   memory of ArrayBuffers it allocates itself */
static void *runtime_realloc_func(JSRuntime *rt, void *opaque, void *ptr,
                                  size_t size)
{
    return js_realloc_rt(rt, ptr, size);
}

static void transfer_default_managed_array_buffer(void)
{
    JSValue obj, global, ret, det;
    uint8_t *buf, *p;
    size_t size;

    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);

    buf = js_malloc(ctx, 8);
    memset(buf, 0xAB, 8);
    obj = JS_NewArrayBuffer(ctx, buf, 8, /*max_len*/0,
                            runtime_realloc_func, NULL, false);
    assert(JS_IsArrayBuffer(obj));
    global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "ab", JS_DupValue(ctx, obj));
    JS_FreeValue(ctx, global);

    ret = eval(ctx, "ab.transfer(16)");
    assert(!JS_IsException(ret));
    assert(JS_IsArrayBuffer(ret));
    p = JS_GetArrayBuffer(ctx, &size, ret);
    assert(size == 16);
    assert(p[0] == 0xAB && p[7] == 0xAB);
    assert(p[8] == 0 && p[15] == 0);
    det = eval(ctx, "ab.detached");
    assert(JS_IsBool(det));
    assert(JS_VALUE_GET_BOOL(det));
    JS_FreeValue(ctx, det);
    JS_FreeValue(ctx, ret);
    ret = eval(ctx, "globalThis.ab = undefined");
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, obj);

    buf = js_malloc(ctx, 8);
    memset(buf, 0xCD, 8);
    obj = JS_NewArrayBuffer(ctx, buf, 8, /*max_len*/0,
                            runtime_realloc_func, NULL, false);
    global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "ab", JS_DupValue(ctx, obj));
    JS_FreeValue(ctx, global);

    ret = eval(ctx, "ab.transferToFixedLength(4)");
    assert(!JS_IsException(ret));
    p = JS_GetArrayBuffer(ctx, &size, ret);
    assert(size == 4);
    assert(p[0] == 0xCD && p[3] == 0xCD);
    JS_FreeValue(ctx, ret);
    ret = eval(ctx, "globalThis.ab = undefined");
    JS_FreeValue(ctx, ret);
    JS_FreeValue(ctx, obj);

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static void get_class_name(void)
{
    static const struct {
        const char *code;
        const char *name;
    } builtins[] = {
        { "({})",               "Object" },
        { "[]",                 "Array" },
        { "new Error()",        "Error" },
        { "new Date()",         "Date" },
        { "/re/",               "RegExp" },
        { "new Map()",          "Map" },
        { "new ArrayBuffer(0)", "ArrayBuffer" },
        { "new Uint8Array(0)",  "Uint8Array" },
        { "(function(){})",     "Function" },
    };
    JSClassDef def = (JSClassDef){ .class_name = "MyClass" };
    JSClassID class_id, unregistered_class_id;
    JSAtom atom, expected;
    JSRuntime *rt;
    JSContext *ctx;
    const char *s;
    JSValue obj;
    size_t i;

    rt = new_runtime();
    class_id = 0;
    JS_NewClassID(rt, &class_id);
    assert(0 == JS_NewClass(rt, class_id, &def));

    /* handed out by JS_NewClassID() but never passed to JS_NewClass() */
    unregistered_class_id = 0;
    JS_NewClassID(rt, &unregistered_class_id);

    ctx = JS_NewContext(rt);

    /* the name of a class registered from C, not its class id */
    expected = JS_NewAtom(ctx, "MyClass");
    atom = JS_GetClassName(rt, class_id);
    assert(atom == expected);
    s = JS_AtomToCString(ctx, atom);
    assert(s);
    assert(!strcmp(s, "MyClass"));
    JS_FreeCString(ctx, s);
    JS_FreeAtom(ctx, atom);
    JS_FreeAtom(ctx, expected);

    /* the names of the built-in classes */
    for (i = 0; i < countof(builtins); i++) {
        obj = eval(ctx, builtins[i].code);
        assert(!JS_IsException(obj));
        atom = JS_GetClassName(rt, JS_GetClassID(obj));
        assert(atom != JS_ATOM_NULL);
        s = JS_AtomToCString(ctx, atom);
        assert(s);
        assert(!strcmp(s, builtins[i].name));
        JS_FreeCString(ctx, s);
        JS_FreeAtom(ctx, atom);
        JS_FreeValue(ctx, obj);
    }

    /* class ids without a registered class have no name */
    assert(JS_ATOM_NULL == JS_GetClassName(rt, JS_INVALID_CLASS_ID));
    assert(JS_ATOM_NULL == JS_GetClassName(rt, unregistered_class_id));
    assert(JS_ATOM_NULL == JS_GetClassName(rt, class_id + 4096));

    /* the caller owns the returned atom; the class keeps its own reference,
       so releasing it repeatedly doesn't free the name out from under it */
    for (i = 0; i < 64; i++)
        JS_FreeAtom(ctx, JS_GetClassName(rt, class_id));
    atom = JS_GetClassName(rt, class_id);
    s = JS_AtomToCString(ctx, atom);
    assert(s);
    assert(!strcmp(s, "MyClass"));
    JS_FreeCString(ctx, s);
    JS_FreeAtom(ctx, atom);

    /* every registered class has a name, and it is a name rather than a
       number: returning the class id instead produced a valid-looking atom,
       either an unrelated predefined one for a small id or the id spelled out
       in decimal for a large one, so check the whole table rather than the
       handful of classes spelled out above */
    {
        JSClassID id;
        int registered = 0;

        for (id = 1; id < class_id + 16; id++) {
            char decimal[32];
            if (!JS_IsRegisteredClass(rt, id))
                continue;
            registered++;
            atom = JS_GetClassName(rt, id);
            assert(atom != JS_ATOM_NULL);
            s = JS_AtomToCString(ctx, atom);
            assert(s);
            // a few internal classes are deliberately unnamed, but none is
            // named after a number
            snprintf(decimal, sizeof(decimal), "%u", (unsigned)id);
            assert(strcmp(s, decimal));
            assert(s[0] < '0' || s[0] > '9');
            JS_FreeCString(ctx, s);
            JS_FreeAtom(ctx, atom);
        }
        /* the built-ins alone are far more than this */
        assert(registered > 20);
    }

    /* two classes sharing a name share the interned atom, and the name does
       not depend on which context asks */
    {
        JSClassDef def2 = (JSClassDef){ .class_name = "MyClass" };
        JSClassID class_id2 = 0;
        JSContext *ctx2;
        JSAtom a1, a2;

        JS_NewClassID(rt, &class_id2);
        assert(0 == JS_NewClass(rt, class_id2, &def2));
        assert(class_id2 != class_id);

        a1 = JS_GetClassName(rt, class_id);
        a2 = JS_GetClassName(rt, class_id2);
        assert(a1 == a2);
        JS_FreeAtom(ctx, a1);
        JS_FreeAtom(ctx, a2);

        ctx2 = JS_NewContext(rt);
        a1 = JS_GetClassName(rt, class_id);
        a2 = JS_GetClassName(rt, class_id);
        assert(a1 == a2);
        JS_FreeAtom(ctx2, a1);
        JS_FreeAtom(ctx2, a2);
        JS_FreeContext(ctx2);
    }

    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

void object_from(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue t0 = JS_NewObject(ctx);
    assert(!JS_IsException(t0));
    assert(JS_IsObject(t0));
    JSAtom prop = JS_NewAtomLen(ctx, "prop", 4);
    assert(prop != JS_ATOM_NULL);
    JSValue val = JS_NULL;
    JSValue t1 = JS_NewObjectFrom(ctx, 1, &prop, &val);
    assert(!JS_IsException(t1));
    assert(JS_IsObject(t1));
    uint32_t old_shape_hash_count = js_std_cmd(/*GetShapeHashCount*/4, rt);
    JSValue t2 = JS_NewObjectFrom(ctx, 1, &prop, &val);
    assert(!JS_IsException(t2));
    assert(JS_IsObject(t2));
    uint32_t new_shape_hash_count = js_std_cmd(/*GetShapeHashCount*/4, rt);
    assert(old_shape_hash_count == new_shape_hash_count);
    JS_FreeAtom(ctx, prop);
    JS_FreeValue(ctx, t2);
    JS_FreeValue(ctx, t1);
    JS_FreeValue(ctx, t0);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

void add_intrinsic_bigint(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContextRaw(rt);
    JS_AddIntrinsicBaseObjects(ctx);
    JS_AddIntrinsicBigInt(ctx);
    assert(!JS_HasException(ctx));
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

void new_typed_array(void)
{
    JSValueConst argv[4];
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    uint8_t *buf = js_malloc(ctx, 8);
    JSValue ab = JS_NewArrayBuffer(ctx, buf, 8, /*max_len*/0, NULL, NULL, false);
    assert(JS_IsArrayBuffer(ab));
    for (int argc = 0; argc < (int)countof(argv); argc++) {
        JSValue ret = JS_NewTypedArray(ctx, argc, argv, JS_TYPED_ARRAY_UINT8);
        assert(!JS_IsException(ret));
        assert(JS_IsObject(ret));
        JS_FreeValue(ctx, ret);
        argv[argc] = JS_UNDEFINED;
        argv[0] = ab;
    }
    JS_FreeValue(ctx, ab);
    js_free(ctx, buf);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

void private_symbols(void)
{
    JSRuntime *rt = new_runtime();
    JSContext *ctx = JS_NewContext(rt);
    JSValue pub = JS_NewSymbol(ctx, "public", /*is_global*/false);
    JSValue priv = JS_NewPrivateSymbol(ctx, "private");
    assert(JS_IsSymbol(pub));
    assert(JS_IsSymbol(priv));
    JSValue obj = JS_NewObject(ctx);
    assert(JS_IsObject(obj));
    assert(true == JS_SetPropertyValue(ctx, obj, pub, JS_TRUE, JS_PROP_C_W_E));
    assert(true == JS_SetPropertyValue(ctx, obj, priv, JS_FALSE, JS_PROP_C_W_E));
    JSValue global_object = JS_GetGlobalObject(ctx);
    assert(true == JS_SetPropertyStr(ctx, global_object, "o", JS_DupValue(ctx, obj)));
    JS_FreeValue(ctx, global_object);
    JSValue result = eval(ctx, "Object.getOwnPropertySymbols(o)");
    assert(JS_IsArray(result));
    int64_t length = -1;
    assert(0 == JS_GetLength(ctx, result, &length));
    assert(length == 1);
    JSValue item = JS_GetPropertyUint32(ctx, result, 0);
    assert(JS_IsSymbol(item));
    assert(JS_IsSameValue(ctx, item, pub));
    JS_FreeValue(ctx, item);
    JS_FreeValue(ctx, result);
    result = JS_GetPropertyValue(ctx, obj, JS_DupValue(ctx, pub));
    assert(JS_IsBool(result));
    assert(JS_IsSameValue(ctx, result, JS_TRUE));
    result = JS_GetPropertyValue(ctx, obj, JS_DupValue(ctx, priv));
    assert(JS_IsBool(result));
    assert(JS_IsSameValue(ctx, result, JS_FALSE));
    JS_FreeValue(ctx, obj);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

// https://github.com/quickjs-ng/quickjs/issues/1181
// JS_NewContext() must fail cleanly, not crash or leak, whichever of its
// allocations runs out of memory.
static void new_context_low_memory(void)
{
    JSMemoryUsage stats;
    size_t base, headroom;
    JSRuntime *rt;
    JSContext *ctx;
    int all, ok;

    all = ok = 0;
    rt = new_runtime();
    JS_ComputeMemoryUsage(rt, &stats);
    base = (size_t)stats.malloc_size;
    // Small steps first, so that each of the first allocations gets its turn
    // to fail, then bigger ones until a few contexts fit.
    for (headroom = 0; ok < 4 && headroom < 4 * 1024 * 1024;
         headroom += headroom < 8192 ? 8 : 1024) {
        JS_SetMemoryLimit(rt, base + headroom);
        ctx = JS_NewContext(rt); // expected to fail, not to crash
        if (ctx) {
            JS_FreeContext(ctx);
            ok++;
        }
        all++;
    }
    JS_SetMemoryLimit(rt, 0);
    JS_FreeRuntime(rt);
    assert(ok > 0);     // expect some successes...
    assert(ok < all);   // ...but not all
}

// running out of memory in JSON.stringify must not leak the array element
// that was being converted
static void json_stringify_oom(void)
{
    static const char setup_code[] =
        "globalThis.f = () => JSON.stringify([{}]);\n"
        "f();\n"; // JSON.stringify is set up on first access, do it now
    JSValue global_object, func, ret;
    JSMemoryUsage stats;
    uint32_t headroom;
    JSRuntime *rt;
    JSContext *ctx;

    rt = new_runtime();
    ctx = JS_NewContext(rt);
    global_object = JS_GetGlobalObject(ctx);

    ret = eval(ctx, setup_code);
    assert(!JS_IsException(ret));
    JS_FreeValue(ctx, ret);

    func = JS_GetPropertyStr(ctx, global_object, "f");
    assert(JS_IsFunction(ctx, func));

    for (headroom = 0; headroom < 2048; headroom++) {
        JS_ComputeMemoryUsage(rt, &stats);
        JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + headroom);
        ret = JS_Call(ctx, func, JS_UNDEFINED, 0, NULL);
        JS_SetMemoryLimit(rt, 0);
        JS_FreeValue(ctx, ret);
        JS_FreeValue(ctx, JS_GetException(ctx));
    }

    JS_FreeValue(ctx, func);
    JS_FreeValue(ctx, global_object);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

/* The CPU budget of an embedder ran out: from now on the handler says stop
   at every poll. */
static int stop_interrupt_handler(JSContext *ctx, void *opaque)
{
    int *polls = (int *)opaque;
    return ++*polls > 50;
}

/* The uncatchable exception raised by the interrupt handler must reach the
   embedder through every construct that turns an exception into a value on
   the way out: a Promise executor, Promise.try, an async generator, the
   Promise.all/race iterator, a thenable getter, using and DisposableStack
   disposers, an iterator return() during a throw, Error.prepareStackTrace
   and Error.stackTraceLimit. Otherwise the script goes on and eval() returns
   the trailing 1. Each construct runs at the top level and inside a job. */
static void uncatchable_interrupt_sites(void)
{
    static const char *const sites[] = {
        "try { for (;;) {} } catch (e) {}",
        "try { for (;;) {} } finally {}",
        "new Promise(() => { for (;;) {} })",
        "Promise.try(() => { for (;;) {} })",
        "(async function* () { for (;;) {} })().next()",
        "Promise.all({ [Symbol.iterator]() { for (;;) {} } })",
        "Promise.race({ [Symbol.iterator]() { for (;;) {} } })",
        "Promise.resolve({ get then() { for (;;) {} } })",
        "try { { using x = { [Symbol.dispose]() { for (;;) {} } };"
        " throw new Error('body'); } } catch (e) {}",
        "try { const s = new DisposableStack();"
        " s.defer(() => { throw new Error('a'); });"
        " s.defer(() => { for (;;) {} }); s.dispose(); } catch (e) {}",
        "try { for (const x of { [Symbol.iterator]() { return {"
        " next() { return { value: 1, done: false }; },"
        " return() { for (;;) {} } }; } }) throw new Error('body'); }"
        " catch (e) {}",
        "try { Array.from({ [Symbol.iterator]() { return {"
        " next() { return { value: 1, done: false }; },"
        " return() { for (;;) {} } }; } }, () => { throw new Error('body'); });"
        " } catch (e) {}",
        "Error.prepareStackTrace = () => { for (;;) {} };"
        " try { null.x; } catch (e) {}",
        "Error.stackTraceLimit = { valueOf() { for (;;) {} } };"
        " try { null.x; } catch (e) {}",
    };
    static const char *const modes[] = {
        "%s; 1",
        "Promise.resolve().then(() => { %s; }); 1",
    };
    char code[1024];
    size_t i, j;

    for (i = 0; i < sizeof(sites) / sizeof(sites[0]); i++) {
        for (j = 0; j < sizeof(modes) / sizeof(modes[0]); j++) {
            JSRuntime *rt = new_runtime();
            JSContext *ctx = JS_NewContext(rt);
            JSValue ret, e;
            const char *str;
            int polls = 0, r;

            snprintf(code, sizeof(code), modes[j], sites[i]);
            JS_SetInterruptHandler(rt, stop_interrupt_handler, &polls);
            ret = eval(ctx, code);
            if (j == 1) {
                /* the construct runs in a job */
                assert(!JS_IsException(ret));
                JS_FreeValue(ctx, ret);
                r = 1;
                while (r > 0)
                    r = JS_ExecutePendingJob(rt, &ctx);
                assert(r == -1);
            } else {
                assert(JS_IsException(ret));
            }
            assert(polls > 50);
            assert(JS_HasException(ctx));
            e = JS_GetException(ctx);
            assert(JS_IsError(e));
            assert(JS_IsUncatchableError(e));
            str = JS_ToCString(ctx, e);
            assert(str != NULL);
            assert(strstr(str, "InternalError: interrupted"));
            JS_FreeCString(ctx, str);
            JS_FreeValue(ctx, e);
            /* the embedder took the exception: the runtime is usable again */
            JS_SetInterruptHandler(rt, NULL, NULL);
            ret = eval(ctx, "1 + 1");
            assert(JS_VALUE_GET_TAG(ret) == JS_TAG_INT);
            assert(JS_VALUE_GET_INT(ret) == 2);
            JS_FreeValue(ctx, ret);
            JS_FreeContext(ctx);
            JS_FreeRuntime(rt);
        }
    }
}

/* At the memory limit the error object of the interrupt could not be
   allocated and JS_NULL was thrown instead, which a plain catch block
   catches. The embedder must get an uncatchable InternalError. */
static void uncatchable_interrupt_at_memory_limit(void)
{
    static const char setup_code[] =
        "globalThis.keep = [];"
        " for (let i = 0; i < 64; i++) keep.push(new Uint8Array(16384));"
        " globalThis.f = function() { try { for (;;) {} } catch (e) {} return 1; };";
    JSValue global_object, func, ret, e;
    JSMemoryUsage stats;
    uint32_t headroom;
    const char *str;

    for (headroom = 0; headroom < 2048; headroom += 128) {
        JSRuntime *rt = new_runtime();
        JSContext *ctx = JS_NewContext(rt);
        int polls = 0;

        ret = eval(ctx, setup_code);
        assert(!JS_IsException(ret));
        JS_FreeValue(ctx, ret);
        global_object = JS_GetGlobalObject(ctx);
        func = JS_GetPropertyStr(ctx, global_object, "f");
        assert(JS_IsFunction(ctx, func));
        /* the function is compiled; only the call runs at the limit */
        JS_ComputeMemoryUsage(rt, &stats);
        JS_SetMemoryLimit(rt, (size_t)stats.malloc_size + headroom);
        JS_SetInterruptHandler(rt, stop_interrupt_handler, &polls);
        ret = JS_Call(ctx, func, JS_UNDEFINED, 0, NULL);
        assert(JS_IsException(ret));
        assert(polls > 50);
        e = JS_GetException(ctx);
        assert(JS_IsError(e));
        assert(JS_IsUncatchableError(e));
        JS_SetMemoryLimit(rt, 0);
        str = JS_ToCString(ctx, e);
        assert(str != NULL);
        assert(strstr(str, "InternalError: interrupted"));
        JS_FreeCString(ctx, str);
        JS_FreeValue(ctx, e);
        JS_FreeValue(ctx, func);
        JS_FreeValue(ctx, global_object);
        JS_FreeContext(ctx);
        JS_FreeRuntime(rt);
    }
}

/* Running out of memory is uncatchable too: no catch block runs, and the
   embedder gets an InternalError object even when the error object itself
   was the allocation that failed. The limits sweep the point at which the
   allocation fails so that both the object and its property array hit it. */
static void uncatchable_out_of_memory(void)
{
    static const char *const codes[] = {
        "var caught = 0; try { const a = [];"
        " for (;;) a.push({ a: 1, b: 2, c: 3 }); } catch (e) { caught = 1; }"
        " caught",
        "var caught = 0; try { const a = [];"
        " for (;;) a.push(new Array(1000).fill(1)); } catch (e) { caught = 1; }"
        " caught",
    };
    size_t i, limit;

    for (i = 0; i < sizeof(codes) / sizeof(codes[0]); i++) {
        for (limit = 4000000; limit < 4000000 + 40 * 997; limit += 997) {
            JSRuntime *rt = new_runtime();
            JSContext *ctx;
            JSValue ret, e;
            const char *str;

            JS_SetMemoryLimit(rt, limit);
            ctx = JS_NewContext(rt);
            ret = eval(ctx, codes[i]);
            assert(JS_IsException(ret));
            e = JS_GetException(ctx);
            assert(JS_IsError(e));
            assert(JS_IsUncatchableError(e));
            JS_SetMemoryLimit(rt, 0);
            str = JS_ToCString(ctx, e);
            assert(str != NULL);
            assert(strstr(str, "InternalError: out of memory"));
            JS_FreeCString(ctx, str);
            JS_FreeValue(ctx, e);
            JS_FreeContext(ctx);
            JS_FreeRuntime(rt);
        }
    }
}

int main(void)
{
    cfunctions();
    sync_call();
    async_call();
    async_call_stack_overflow();
    raw_context_global_var();
    is_array();
    module_serde();
    module_unhandled_rejection();
    promise_mark_as_handled();
    promise_then();
    runtime_cstring_free();
    utf16_string();
    weak_map_gc_check();
    promise_hook();
    dump_memory_usage();
    new_errors();
    dom_exception_added_twice();
    large_allocation_accounting();
    backtrace_oom_current_exception();
    backtrace_oom_callsite_array();
    proxy_own_keys_huge_length();
    global_object_prototype();
    slice_string_tocstring();
    immutable_array_buffer();
    shared_array_buffer_growth();
    get_uint8array();
    new_symbol();
    bulk_free_macros();
    detach_array_buffer_free_once();
    typed_array_sort_index_overflow();
    transfer_external_array_buffer();
    resize_external_array_buffer();
    transfer_default_managed_array_buffer();
    get_class_name();
    object_from();
    add_intrinsic_bigint();
    new_typed_array();
    std_eval_interrupt_handler();
    uncatchable_interrupt_sites();
    uncatchable_interrupt_at_memory_limit();
    uncatchable_out_of_memory();
    private_symbols();
    new_context_low_memory();
    json_stringify_oom();
    return 0;
}
