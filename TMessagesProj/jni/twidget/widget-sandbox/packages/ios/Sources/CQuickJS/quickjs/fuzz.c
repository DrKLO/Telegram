// clang -g -O1 -fsanitize=fuzzer -o fuzz fuzz.c
#include "quickjs.h"
#include "quickjs.c"
#include "cutils.h"
#include "libregexp.c"
#include "libunicode.c"
#include "dtoa.c"

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// note: LLVM output does not contain checksum, needs to be added
// manually (4 byte field at position 1) when adding to the corpus
//
// fill in UINT32_MAX to disable checksumming
int LLVMFuzzerTestOneInput(const uint8_t *buf, size_t len)
{
    if (!len)
        return 0;
    JSRuntime *rt = JS_NewRuntime();
    if (!rt)
        exit(1);
    JSContext *ctx = JS_NewContext(rt);
    if (!ctx)
        exit(1);
    size_t newlen = len + 4;
    uint8_t *newbuf = malloc(newlen);
    if (!newbuf)
        exit(1);
    uint32_t csum = bc_csum(&buf[1], len-1);    // skip version field
    newbuf[0] = buf[0];                         // copy version field
    put_u32(&newbuf[1], csum);                  // insert checksum
    memcpy(&newbuf[5], &buf[1], len-1);         // copy rest of payload
    JSValue val = JS_ReadObject(ctx, newbuf, newlen, /*flags*/0);
    free(newbuf);
    if (JS_IsException(val)) {
        JSValue exc = JS_GetException(ctx);
        const char *str = JS_ToCString(ctx, exc);
        JS_FreeValue(ctx, exc);
        if (!str)
            exit(1);
        if (strstr(str, "checksum error"))
            exit(1);
        JS_FreeCString(ctx, str);
    }
    JS_FreeValue(ctx, val);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return 0;
}
