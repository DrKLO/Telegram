// note: file is not actually compiled, only checked for C syntax errors
#include "quickjs.h"

int main(void)
{
    JSRuntime *rt = JS_NewRuntime();
    JSContext *ctx = JS_NewContext(rt);
    JS_FreeValue(ctx, JS_NAN);
    JS_FreeValue(ctx, JS_UNDEFINED);
    JS_FreeValue(ctx, JS_NewFloat64(ctx, 42));
    // not a legal way of using JS_MKPTR but this is here
    // to have the compiler syntax-check its definition
    JS_FreeValue(ctx, JS_MKPTR(JS_TAG_UNINITIALIZED, 0));
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return 0;
}
