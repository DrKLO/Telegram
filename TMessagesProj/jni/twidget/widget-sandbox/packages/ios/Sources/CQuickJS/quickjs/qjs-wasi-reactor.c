/*
 * QuickJS WASI Reactor Mode
 *
 * In reactor mode, QuickJS exports functions that can be called repeatedly
 * by the host instead of running main() once and blocking in the event loop.
 *
 * Copyright (c) 2017-2021 Fabrice Bellard
 * Copyright (c) 2017-2021 Charlie Gordon
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/* Include qjs.c to get access to static functions like eval_buf, eval_file,
 * parse_limit, and JS_NewCustomContext */
#include "qjs.c"

static JSRuntime *reactor_rt = NULL;
static JSContext *reactor_ctx = NULL;

int qjs_init_argv(int argc, char **argv);

__attribute__((export_name("qjs_init")))
int qjs_init(void)
{
    static char *empty_argv[] = { "qjs", NULL };
    return qjs_init_argv(1, empty_argv);
}

__attribute__((export_name("qjs_init_argv")))
int qjs_init_argv(int argc, char **argv)
{
    int optind = 1;
    char *expr = NULL;
    int module = -1;
    int load_std = 0;
    char *include_list[32];
    int i, include_count = 0;
    int64_t memory_limit = -1;
    int64_t stack_size = -1;

    if (reactor_rt)
        return -1; /* already initialized */

    /* Parse options (subset of main()) */
    while (optind < argc && *argv[optind] == '-') {
        char *arg = argv[optind] + 1;
        const char *longopt = "";
        char *optarg = NULL;
        if (!*arg)
            break;
        optind++;
        if (*arg == '-') {
            longopt = arg + 1;
            optarg = strchr(longopt, '=');
            if (optarg)
                *optarg++ = '\0';
            arg += strlen(arg);
            if (!*longopt)
                break;
        }
        for (; *arg || *longopt; longopt = "") {
            char opt = *arg;
            if (opt) {
                arg++;
                if (!optarg && *arg)
                    optarg = arg;
            }
            if (opt == 'e' || !strcmp(longopt, "eval")) {
                if (!optarg) {
                    if (optind >= argc)
                        return -1;
                    optarg = argv[optind++];
                }
                expr = optarg;
                break;
            }
            if (opt == 'I' || !strcmp(longopt, "include")) {
                if (optind >= argc || include_count >= countof(include_list))
                    return -1;
                include_list[include_count++] = argv[optind++];
                continue;
            }
            if (opt == 'm' || !strcmp(longopt, "module")) {
                module = 1;
                continue;
            }
            if (!strcmp(longopt, "std")) {
                load_std = 1;
                continue;
            }
            if (!strcmp(longopt, "memory-limit")) {
                if (!optarg) {
                    if (optind >= argc)
                        return -1;
                    optarg = argv[optind++];
                }
                memory_limit = parse_limit(optarg);
                break;
            }
            if (!strcmp(longopt, "stack-size")) {
                if (!optarg) {
                    if (optind >= argc)
                        return -1;
                    optarg = argv[optind++];
                }
                stack_size = parse_limit(optarg);
                break;
            }
            break; /* ignore unknown options */
        }
    }

    reactor_rt = JS_NewRuntime();
    if (!reactor_rt)
        return -1;
    if (memory_limit >= 0)
        JS_SetMemoryLimit(reactor_rt, (size_t)memory_limit);
    if (stack_size >= 0)
        JS_SetMaxStackSize(reactor_rt, (size_t)stack_size);

    js_std_set_worker_new_context_func(JS_NewCustomContext);
    js_std_init_handlers(reactor_rt);

    reactor_ctx = JS_NewCustomContext(reactor_rt);
    if (!reactor_ctx) {
        js_std_free_handlers(reactor_rt);
        JS_FreeRuntime(reactor_rt);
        reactor_rt = NULL;
        return -1;
    }

    JS_SetModuleLoaderFunc2(reactor_rt, NULL, js_module_loader,
                            js_module_check_attributes, NULL);
    JS_SetHostPromiseRejectionTracker(reactor_rt, js_std_promise_rejection_tracker, NULL);
    js_std_add_helpers(reactor_ctx, argc - optind, argv + optind);

    if (load_std) {
        const char *str =
            "import * as bjson from 'qjs:bjson';\n"
            "import * as std from 'qjs:std';\n"
            "import * as os from 'qjs:os';\n"
            "globalThis.bjson = bjson;\n"
            "globalThis.std = std;\n"
            "globalThis.os = os;\n";
        if (eval_buf(reactor_ctx, str, strlen(str), "<input>", JS_EVAL_TYPE_MODULE))
            goto fail;
    }

    for (i = 0; i < include_count; i++) {
        if (eval_file(reactor_ctx, include_list[i], 0))
            goto fail;
    }

    if (expr) {
        if (eval_buf(reactor_ctx, expr, strlen(expr), "<cmdline>",
                     module == 1 ? JS_EVAL_TYPE_MODULE : 0))
            goto fail;
    } else if (optind < argc) {
        if (eval_file(reactor_ctx, argv[optind], module))
            goto fail;
    }

    return 0;

fail:
    js_std_free_handlers(reactor_rt);
    JS_FreeContext(reactor_ctx);
    JS_FreeRuntime(reactor_rt);
    reactor_rt = NULL;
    reactor_ctx = NULL;
    return -1;
}

__attribute__((export_name("qjs_get_context")))
JSContext *qjs_get_context(void)
{
    return reactor_ctx;
}

__attribute__((export_name("qjs_destroy")))
void qjs_destroy(void)
{
    if (reactor_ctx) {
        js_std_free_handlers(reactor_rt);
        JS_FreeContext(reactor_ctx);
        reactor_ctx = NULL;
    }
    if (reactor_rt) {
        JS_FreeRuntime(reactor_rt);
        reactor_rt = NULL;
    }
}
