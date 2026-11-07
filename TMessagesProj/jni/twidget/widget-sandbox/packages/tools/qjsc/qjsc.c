// Dev-time compiler for the bundles the native ports ship: JS → quickjs-ng
// bytecode in the TWQB container (see qjs_shim.h). Built by
// scripts/build-native-bundles.ts from the vendored ios/Sources/CQuickJS
// sources, so the bytecode format always matches the engine the apps link.
//
//   qjsc <in.js> <out.qbc> [filename-in-stack-traces]
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "qjs_shim.h"

static char *read_all(const char *path, size_t *len) {
    FILE *f = fopen(path, "rb");
    if (!f) return NULL;
    fseek(f, 0, SEEK_END);
    long n = ftell(f);
    fseek(f, 0, SEEK_SET);
    char *buf = malloc((size_t)n + 1);
    if (!buf || fread(buf, 1, (size_t)n, f) != (size_t)n) { fclose(f); free(buf); return NULL; }
    buf[n] = 0;
    fclose(f);
    *len = (size_t)n;
    return buf;
}

int main(int argc, char **argv) {
    if (argc < 3) { fprintf(stderr, "usage: qjsc <in.js> <out.qbc> [filename]\n"); return 2; }
    const char *filename = argc > 3 ? argv[3] : argv[1];
    size_t len = 0;
    char *code = read_all(argv[1], &len);
    if (!code) { fprintf(stderr, "qjsc: cannot read %s\n", argv[1]); return 1; }
    JSRuntime *rt = JS_NewRuntime();
    JSContext *ctx = JS_NewContext(rt);
    size_t out_len = 0;
    uint8_t *blob = qjs_compile_bytecode(ctx, code, len, filename, &out_len);
    if (!blob) {
        char *d = qjs_take_exception_description(ctx);
        fprintf(stderr, "qjsc: %s: %s\n", argv[1], d ? d : "compile failed");
        free(d);
        return 1;
    }
    FILE *out = fopen(argv[2], "wb");
    if (!out || fwrite(blob, 1, out_len, out) != out_len) { fprintf(stderr, "qjsc: cannot write %s\n", argv[2]); return 1; }
    fclose(out);
    printf("%s: %zu bytes of source → %zu bytes of bytecode\n", filename, len, out_len);
    qjs_free_bytecode(ctx, blob);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    free(code);
    return 0;
}
