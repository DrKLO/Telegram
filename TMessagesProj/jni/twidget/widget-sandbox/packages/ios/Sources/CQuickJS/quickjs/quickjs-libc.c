/*
 * QuickJS C library
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
#include "quickjs.h"
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <inttypes.h>
#include <string.h>
#include <assert.h>
#include <errno.h>
#include <fcntl.h>
#if !defined(_MSC_VER)
#include <sys/time.h>
#include <unistd.h>
#endif
#include <time.h>
#include <signal.h>
#include <limits.h>
#include <sys/stat.h>
#if !defined(_MSC_VER)
#include <dirent.h>
#endif
#if defined(_WIN32)
#include <windows.h>
#include <direct.h>
#include <io.h>
#include <conio.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/utime.h>
#define popen _popen
#define pclose _pclose
#define rmdir _rmdir
#define getcwd _getcwd
#define chdir _chdir
#else
#include <sys/ioctl.h>
#include <poll.h>
#if !defined(__wasi__)
#include <dlfcn.h>
#include <termios.h>
#include <sys/resource.h>
#include <sys/wait.h>
#include <grp.h>
#endif

#if defined(__APPLE__)
typedef sig_t sighandler_t;
#include <crt_externs.h>
#include <TargetConditionals.h>
#define environ (*_NSGetEnviron())
#endif

#ifdef __sun
typedef void (*sighandler_t)(int);
extern char **environ;
#endif

#if defined(__OpenBSD__) || defined(__FreeBSD__) || defined(__NetBSD__)
typedef sig_t sighandler_t;
extern char **environ;
#endif

#endif /* _WIN32 */

#include "cutils.h"
#include "list.h"
#include "quickjs-libc.h"

#if JS_HAVE_THREADS
#include "quickjs-c-atomics.h"
#define USE_WORKER // enable os.Worker
#endif

#ifndef S_IFBLK
#define S_IFBLK 0
#endif

#ifndef S_IFIFO
#define S_IFIFO 0
#endif

#ifndef MAX_SAFE_INTEGER // already defined in amalgamation builds
#define MAX_SAFE_INTEGER (((int64_t) 1 << 53) - 1)
#endif

#ifndef QJS_NATIVE_MODULE_SUFFIX
#ifdef _WIN32
#define QJS_NATIVE_MODULE_SUFFIX ".dll"
#else
#define QJS_NATIVE_MODULE_SUFFIX ".so"
#endif
#endif

/* TODO:
   - add socket calls
*/

typedef struct {
    struct list_head link;
    int fd;
    JSValue rw_func[2];
} JSOSRWHandler;

typedef struct {
    struct list_head link;
    int sig_num;
    JSValue func;
} JSOSSignalHandler;

typedef struct {
    struct list_head link;
    int64_t timer_id;
    uint8_t repeats:1;
    int64_t timeout;
    int64_t delay;
    JSValue func;
} JSOSTimer;

typedef struct {
    struct list_head link;
    JSValue promise;
    JSValue reason;
} JSRejectedPromiseEntry;

#ifdef USE_WORKER

typedef struct {
    struct list_head link;
    uint8_t *data;
    size_t data_len;
    /* list of SharedArrayBuffers, necessary to free the message */
    uint8_t **sab_tab;
    size_t sab_tab_len;
} JSWorkerMessage;

typedef struct JSWaker {
#ifdef _WIN32
    HANDLE handle;
#else
    int read_fd;
    int write_fd;
#endif
} JSWaker;

typedef struct {
    int ref_count;
    js_mutex_t mutex;
    struct list_head msg_queue; /* list of JSWorkerMessage.link */
    JSWaker waker;
} JSWorkerMessagePipe;

typedef struct {
    struct list_head link;
    JSWorkerMessagePipe *recv_pipe;
    JSValue on_message_func;
} JSWorkerMessageHandler;

#endif // USE_WORKER

typedef struct JSThreadState {
    struct list_head os_rw_handlers; /* list of JSOSRWHandler.link */
    struct list_head os_signal_handlers; /* list JSOSSignalHandler.link */
    struct list_head os_timers; /* list of JSOSTimer.link */
    struct list_head port_list; /* list of JSWorkerMessageHandler.link */
    struct list_head rejected_promise_list; /* list of JSRejectedPromiseEntry.link */
    int eval_script_recurse; /* only used in the main thread */
    int64_t next_timer_id; /* for setTimeout / setInterval */
    bool can_js_os_poll;
    /* not used in the main thread */
#ifdef USE_WORKER
    JSWorkerMessagePipe *recv_pipe, *send_pipe;
#else
    void *recv_pipe;
#endif // USE_WORKER
    JSClassID std_file_class_id;
    JSClassID worker_class_id;
    JSInterruptHandler *prev_interrupt_handler;
    void *prev_interrupt_opaque;
} JSThreadState;

static uint64_t os_pending_signals;

static void *js_std_dbuf_realloc(void *opaque, void *ptr, size_t size)
{
    JSRuntime *rt = opaque;
    return js_realloc_rt(rt, ptr, size);
}

static void js_std_dbuf_init(JSContext *ctx, DynBuf *s)
{
    dbuf_init2(s, JS_GetRuntime(ctx), js_std_dbuf_realloc);
}

static bool my_isdigit(int c)
{
    return (c >= '0' && c <= '9');
}

static JSThreadState *js_get_thread_state(JSRuntime *rt)
{
    return (JSThreadState *)js_std_cmd(/*GetOpaque*/0, rt);
}

static void js_set_thread_state(JSRuntime *rt, JSThreadState *ts)
{
    js_std_cmd(/*SetOpaque*/1, rt, ts);
}

// Non-CL Clang on Windows does not define __GNUC__
#if defined(__GNUC__) || defined(__clang__)
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wformat-nonliteral"
#endif // __GNUC__ || __clang__
static JSValue js_printf_internal(JSContext *ctx,
                                  int argc, JSValueConst *argv, FILE *fp)
{
    char fmtbuf[32];
    uint8_t cbuf[UTF8_CHAR_LEN_MAX+1];
    JSValue res;
    DynBuf dbuf;
    const char *fmt_str = NULL;
    const uint8_t *fmt, *fmt_end;
    const uint8_t *p;
    char *q;
    int i, c, len, mod;
    size_t fmt_len;
    int32_t int32_arg;
    int64_t int64_arg;
    double double_arg;
    const char *string_arg;

    js_std_dbuf_init(ctx, &dbuf);

    if (argc > 0) {
        fmt_str = JS_ToCStringLen(ctx, &fmt_len, argv[0]);
        if (!fmt_str)
            goto fail;

        i = 1;
        fmt = (const uint8_t *)fmt_str;
        fmt_end = fmt + fmt_len;
        while (fmt < fmt_end) {
            for (p = fmt; fmt < fmt_end && *fmt != '%'; fmt++)
                continue;
            dbuf_put(&dbuf, p, fmt - p);
            if (fmt >= fmt_end)
                break;
            q = fmtbuf;
            *q++ = *fmt++;  /* copy '%' */

            /* flags */
            for(;;) {
                c = *fmt;
                if (c == '0' || c == '#' || c == '+' || c == '-' || c == ' ' ||
                    c == '\'') {
                    if (q >= fmtbuf + sizeof(fmtbuf) - 1)
                        goto invalid;
                    *q++ = c;
                    fmt++;
                } else {
                    break;
                }
            }
            /* width */
            if (*fmt == '*') {
                if (i >= argc)
                    goto missing;
                if (JS_ToInt32(ctx, &int32_arg, argv[i++]))
                    goto fail;
                q += snprintf(q, fmtbuf + sizeof(fmtbuf) - q, "%d", int32_arg);
                fmt++;
            } else {
                while (my_isdigit(*fmt)) {
                    if (q >= fmtbuf + sizeof(fmtbuf) - 1)
                        goto invalid;
                    *q++ = *fmt++;
                }
            }
            if (*fmt == '.') {
                if (q >= fmtbuf + sizeof(fmtbuf) - 1)
                    goto invalid;
                *q++ = *fmt++;
                if (*fmt == '*') {
                    if (i >= argc)
                        goto missing;
                    if (JS_ToInt32(ctx, &int32_arg, argv[i++]))
                        goto fail;
                    q += snprintf(q, fmtbuf + sizeof(fmtbuf) - q, "%d", int32_arg);
                    fmt++;
                } else {
                    while (my_isdigit(*fmt)) {
                        if (q >= fmtbuf + sizeof(fmtbuf) - 1)
                            goto invalid;
                        *q++ = *fmt++;
                    }
                }
            }

            /* we only support the "l" modifier for 64 bit numbers */
            mod = ' ';
            if (*fmt == 'l') {
                mod = *fmt++;
            }

            /* type */
            c = *fmt++;
            if (q >= fmtbuf + sizeof(fmtbuf) - 1)
                goto invalid;
            *q++ = c;
            *q = '\0';

            switch (c) {
            case 'c':
                if (i >= argc)
                    goto missing;
                if (JS_IsString(argv[i])) {
                    // TODO(chqrlie) need an API to wrap charCodeAt and codePointAt */
                    string_arg = JS_ToCString(ctx, argv[i++]);
                    if (!string_arg)
                        goto fail;
                    int32_arg = utf8_decode((const uint8_t *)string_arg, &p);
                    JS_FreeCString(ctx, string_arg);
                } else {
                    if (JS_ToInt32(ctx, &int32_arg, argv[i++]))
                        goto fail;
                }
                // XXX: throw an exception?
                if ((unsigned)int32_arg > 0x10FFFF)
                    int32_arg = 0xFFFD;
                /* ignore conversion flags, width and precision */
                len = utf8_encode(cbuf, int32_arg);
                dbuf_put(&dbuf, cbuf, len);
                break;

            case 'd':
            case 'i':
            case 'o':
            case 'u':
            case 'x':
            case 'X':
                if (i >= argc)
                    goto missing;
                if (JS_ToInt64Ext(ctx, &int64_arg, argv[i++]))
                    goto fail;
                if (mod == 'l') {
                    /* 64 bit number */
#if defined(_WIN32)
                    if (q >= fmtbuf + sizeof(fmtbuf) - 3)
                        goto invalid;
                    q[2] = q[-1];
                    q[-1] = 'I';
                    q[0] = '6';
                    q[1] = '4';
                    q[3] = '\0';
                    dbuf_printf(&dbuf, fmtbuf, (int64_t)int64_arg);
#else
                    if (q >= fmtbuf + sizeof(fmtbuf) - 2)
                        goto invalid;
                    q[1] = q[-1];
                    q[-1] = q[0] = 'l';
                    q[2] = '\0';
                    dbuf_printf(&dbuf, fmtbuf, (long long)int64_arg);
#endif
                } else {
                    dbuf_printf(&dbuf, fmtbuf, (int)int64_arg);
                }
                break;

            case 's':
                if (i >= argc)
                    goto missing;
                /* XXX: handle strings containing null characters */
                string_arg = JS_ToCString(ctx, argv[i++]);
                if (!string_arg)
                    goto fail;
                dbuf_printf(&dbuf, fmtbuf, string_arg);
                JS_FreeCString(ctx, string_arg);
                break;

            case 'e':
            case 'f':
            case 'g':
            case 'a':
            case 'E':
            case 'F':
            case 'G':
            case 'A':
                if (i >= argc)
                    goto missing;
                if (JS_ToFloat64(ctx, &double_arg, argv[i++]))
                    goto fail;
                dbuf_printf(&dbuf, fmtbuf, double_arg);
                break;

            case '%':
                dbuf_putc(&dbuf, '%');
                break;

            default:
                /* XXX: should support an extension mechanism */
            invalid:
                JS_ThrowTypeError(ctx, "invalid conversion specifier in format string");
                goto fail;
            missing:
                JS_ThrowReferenceError(ctx, "missing argument for conversion specifier");
                goto fail;
            }
        }
        JS_FreeCString(ctx, fmt_str);
    }
    if (dbuf.error) {
        res = JS_ThrowOutOfMemory(ctx);
    } else {
        if (fp) {
            len = fwrite(dbuf.buf, 1, dbuf.size, fp);
            res = JS_NewInt32(ctx, len);
        } else {
            res = JS_NewStringLen(ctx, (char *)dbuf.buf, dbuf.size);
        }
    }
    dbuf_free(&dbuf);
    return res;

fail:
    JS_FreeCString(ctx, fmt_str);
    dbuf_free(&dbuf);
    return JS_EXCEPTION;
}
#if defined(__GNUC__) || defined(__clang__)
#pragma GCC diagnostic pop // ignored "-Wformat-nonliteral"
#endif // __GNUC__ || __clang__

uint8_t *js_load_file(JSContext *ctx, size_t *pbuf_len, const char *filename)
{
    FILE *f;
    size_t n, len;
    uint8_t *p, *buf, tmp[8192];

    f = fopen(filename, "rb");
    if (!f)
        return NULL;
    buf = NULL;
    len = 0;
    do {
        n = fread(tmp, 1, sizeof(tmp), f);
        if (ctx) {
            p = js_realloc(ctx, buf, len + n + 1);
        } else {
            p = realloc(buf, len + n + 1);
        }
        if (!p) {
            if (ctx) {
                js_free(ctx, buf);
            } else {
                free(buf);
            }
            fclose(f);
            return NULL;
        }
        memcpy(&p[len], tmp, n);
        buf = p;
        len += n;
        buf[len] = '\0';
    } while (n == sizeof(tmp));
    fclose(f);
    *pbuf_len = len;
    return buf;
}

/* load and evaluate a file */
static JSValue js_loadScript(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    uint8_t *buf;
    const char *filename;
    JSValue ret;
    size_t buf_len;

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        return JS_EXCEPTION;
    buf = js_load_file(ctx, &buf_len, filename);
    if (!buf) {
        JS_ThrowReferenceError(ctx, "could not load '%s'", filename);
        JS_FreeCString(ctx, filename);
        return JS_EXCEPTION;
    }
    ret = JS_Eval(ctx, (char *)buf, buf_len, filename,
                  JS_EVAL_TYPE_GLOBAL);
    js_free(ctx, buf);
    JS_FreeCString(ctx, filename);
    return ret;
}

static int get_bool_option(JSContext *ctx, bool *pbool,
                           JSValueConst obj,
                           const char *option)
{
    JSValue val;
    val = JS_GetPropertyStr(ctx, obj, option);
    if (JS_IsException(val))
        return -1;
    if (!JS_IsUndefined(val)) {
        *pbool = JS_ToBool(ctx, val);
    }
    JS_FreeValue(ctx, val);
    return 0;
}

// js_realloc_array_buffer to avoid a name conflict with
// js_array_buffer_realloc from quickjs.c in the amalgamation build
static void *js_realloc_array_buffer(JSRuntime *rt, void *opaque, void *ptr,
                                     size_t size)
{
    return js_realloc_rt(rt, ptr, size);
}

/* load a file as a UTF-8 encoded string or Uint8Array */
static JSValue js_std_loadFile(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    uint8_t *buf;
    const char *filename;
    JSValueConst options_obj;
    JSValue ret;
    size_t buf_len;
    bool binary = false;

    if (argc >= 2) {
        options_obj = argv[1];
        if (get_bool_option(ctx, &binary, options_obj,
                            "binary"))
            return JS_EXCEPTION;
    }

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        return JS_EXCEPTION;
    buf = js_load_file(ctx, &buf_len, filename);
    JS_FreeCString(ctx, filename);
    if (!buf)
        return JS_NULL;
    if (binary) {
        ret = JS_NewUint8Array(ctx, buf, buf_len, js_realloc_array_buffer,
                               NULL, false);
    } else {
        ret = JS_NewStringLen(ctx, (char *)buf, buf_len);
        js_free(ctx, buf);
    }

    return ret;
}

static JSValue js_std_writeFile(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv)
{
    const char *filename;
    const char *mode;
    const void *buf;
    size_t len, n;
    JSValueConst data;
    JSValue val, ret, unref;
    bool release;
    FILE *fp;

    ret = JS_EXCEPTION;
    len = 0;
    buf = "";
    mode = "w";
    data = argv[1];
    unref = JS_UNDEFINED;
    release = false;
    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        return JS_EXCEPTION;
    if (JS_IsObject(data)) {
        val = JS_GetPropertyStr(ctx, data, "buffer");
        if (JS_IsException(val))
            goto exception;
        if (JS_IsArrayBuffer(val)) {
            data = unref = val;
        } else {
            JS_FreeValue(ctx, val);
        }
    }
    if (JS_IsArrayBuffer(data)) {
        buf = JS_GetArrayBuffer(ctx, &len, data);
        mode = "wb";
    } else if (!JS_IsUndefined(data)) {
        buf = JS_ToCStringLen(ctx, &len, data);
        release = true;
    }
    if (!buf)
        goto exception;
    fp = fopen(filename, mode);
    if (!fp) {
        JS_ThrowPlainError(ctx, "error opening %s for writing", filename);
        goto exception;
    }
    n = fwrite(buf, len, 1, fp);
    fclose(fp);
    if (n != 1) {
        JS_ThrowPlainError(ctx, "error writing to %s", filename);
        goto exception;
    }
    ret = JS_UNDEFINED;
exception:
    JS_FreeCString(ctx, filename);
    if (release)
        JS_FreeCString(ctx, buf);
    JS_FreeValue(ctx, unref);
    return ret;
}

typedef JSModuleDef *(JSInitModuleFunc)(JSContext *ctx,
                                        const char *module_name);


#if defined(_WIN32)
static JSModuleDef *js_module_loader_so(JSContext *ctx,
                                        const char *module_name)
{
    JSModuleDef *m;
    HINSTANCE hd;
    JSInitModuleFunc *init;
    char *filename = NULL;
    size_t len = strlen(module_name);
    bool is_absolute = len > 2 && ((module_name[0] >= 'A' && module_name[0] <= 'Z') ||
            (module_name[0] >= 'a' && module_name[0] <= 'z')) && module_name[1] == ':';
    bool is_relative = len > 2 && module_name[0] == '.' && (module_name[1] == '/' || module_name[1] == '\\');
    if (is_absolute || is_relative) {
        filename = (char *)module_name;
    } else {
        filename = js_malloc(ctx, len + 2 + 1);
        if (!filename)
            return NULL;
        strcpy(filename, "./");
        strcpy(filename + 2, module_name);
    }
    hd = LoadLibraryA(filename);
    if (filename != module_name)
        js_free(ctx, filename);
    if (hd == NULL) {
        JS_ThrowReferenceError(ctx, "js_load_module '%s' error: %lu",
                               module_name, GetLastError());
        goto fail;
    }
    init = (JSInitModuleFunc *)(uintptr_t)GetProcAddress(hd, "js_init_module");
    if (!init) {
        JS_ThrowReferenceError(ctx, "js_init_module '%s' not found: %lu",
                               module_name, GetLastError());
        goto fail;
    }
    m = init(ctx, module_name);
    if (!m) {
        JS_ThrowReferenceError(ctx, "js_call_module '%s' initialization error",
                               module_name);
    fail:
        if (hd != NULL)
            FreeLibrary(hd);
        return NULL;
    }
    return m;
}
#elif defined(__wasi__)
static JSModuleDef *js_module_loader_so(JSContext *ctx,
                                        const char *module_name)
{
    JS_ThrowReferenceError(ctx, "shared library modules are not supported yet");
    return NULL;
}
#else
static JSModuleDef *js_module_loader_so(JSContext *ctx,
                                        const char *module_name)
{
    JSModuleDef *m;
    void *hd;
    JSInitModuleFunc *init;
    char *filename;

    if (!strchr(module_name, '/')) {
        /* must add a '/' so that the DLL is not searched in the
           system library paths */
        filename = js_malloc(ctx, strlen(module_name) + 2 + 1);
        if (!filename)
            return NULL;
        strcpy(filename, "./");
        strcpy(filename + 2, module_name);
    } else {
        filename = (char *)module_name;
    }

    /* C module */
    hd = dlopen(filename, RTLD_NOW | RTLD_LOCAL);
    if (filename != module_name)
        js_free(ctx, filename);
    if (!hd) {
        JS_ThrowReferenceError(ctx, "could not load module filename '%s' as shared library: %s",
                               module_name, dlerror());
        goto fail;
    }

    *(void **) (&init) = dlsym(hd, "js_init_module");
    if (!init) {
        JS_ThrowReferenceError(ctx, "could not load module filename '%s': js_init_module not found",
                               module_name);
        goto fail;
    }

    m = init(ctx, module_name);
    if (!m) {
        JS_ThrowReferenceError(ctx, "could not load module filename '%s': initialization error",
                               module_name);
    fail:
        if (hd)
            dlclose(hd);
        return NULL;
    }
    return m;
}
#endif /* !_WIN32 */

int js_module_set_import_meta(JSContext *ctx, JSValueConst func_val,
                              bool use_realpath, bool is_main)
{
    JSModuleDef *m;
    char buf[JS__PATH_MAX + 16];
    JSValue meta_obj;
    JSAtom module_name_atom;
    const char *module_name;

    assert(JS_VALUE_GET_TAG(func_val) == JS_TAG_MODULE);
    m = JS_VALUE_GET_PTR(func_val);

    module_name_atom = JS_GetModuleName(ctx, m);
    module_name = JS_AtomToCString(ctx, module_name_atom);
    JS_FreeAtom(ctx, module_name_atom);
    if (!module_name)
        return -1;
    if (!strchr(module_name, ':')) {
        strcpy(buf, "file://");
#if !defined(_WIN32) && !defined(__wasi__)
        /* realpath() cannot be used with modules compiled with qjsc
           because the corresponding module source code is not
           necessarily present */
        if (use_realpath) {
            char *res = realpath(module_name, buf + strlen(buf));
            if (!res) {
                JS_ThrowTypeError(ctx, "realpath failure");
                JS_FreeCString(ctx, module_name);
                return -1;
            }
        } else
#endif
        {
            js__pstrcat(buf, sizeof(buf), module_name);
        }
    } else {
        js__pstrcpy(buf, sizeof(buf), module_name);
    }
    JS_FreeCString(ctx, module_name);

    meta_obj = JS_GetImportMeta(ctx, m);
    if (JS_IsException(meta_obj))
        return -1;
    JS_DefinePropertyValueStr(ctx, meta_obj, "url",
                              JS_NewString(ctx, buf),
                              JS_PROP_C_W_E);
    JS_DefinePropertyValueStr(ctx, meta_obj, "main",
                              JS_NewBool(ctx, is_main),
                              JS_PROP_C_W_E);
    JS_FreeValue(ctx, meta_obj);
    return 0;
}

static int default_module_init(JSContext *ctx, JSModuleDef *m)
{
    JSValue val;
    val = JS_GetModulePrivateValue(ctx, m);
    JS_SetModuleExport(ctx, m, "default", val);
    return 0;
}

/* in order to conform with the specification, only the keys should be
   tested and not the associated values. */
int js_module_check_attributes(JSContext *ctx, void *opaque,
                               JSValueConst attributes)
{
    JSPropertyEnum *tab;
    uint32_t i, len;
    int ret;
    const char *cstr;
    size_t cstr_len;

    if (JS_GetOwnPropertyNames(ctx, &tab, &len, attributes, JS_GPN_ENUM_ONLY | JS_GPN_STRING_MASK))
        return -1;
    ret = 0;
    for(i = 0; i < len; i++) {
        cstr = JS_AtomToCStringLen(ctx, &cstr_len, tab[i].atom);
        if (!cstr) {
            ret = -1;
            break;
        }
        if (!(cstr_len == 4 && !memcmp(cstr, "type", cstr_len))) {
            JS_ThrowTypeError(ctx, "import attribute '%s' is not supported", cstr);
            ret = -1;
        }
        JS_FreeCString(ctx, cstr);
        if (ret)
            break;
    }
    JS_FreePropertyEnum(ctx, tab, len);
    return ret;
}

enum {
    JS_IMPORT_TYPE_JS,
    JS_IMPORT_TYPE_JSON,
    JS_IMPORT_TYPE_TEXT,
    JS_IMPORT_TYPE_BYTES,
};

/* return > 0 if the attributes indicate a JSON module, 0 otherwise, -1 on error */
static int js_module_import_type(JSContext *ctx, JSValueConst attributes)
{
    JSValue str;
    const char *cstr;
    size_t len;
    int res;

    if (JS_IsUndefined(attributes))
        return JS_IMPORT_TYPE_JS;
    str = JS_GetPropertyStr(ctx, attributes, "type");
    if (JS_IsException(str))
        return -1;
    if (!JS_IsString(str)) {
        JS_FreeValue(ctx, str);
        return JS_IMPORT_TYPE_JS;
    }
    cstr = JS_ToCStringLen(ctx, &len, str);
    JS_FreeValue(ctx, str);
    if (!cstr)
        return -1;
    if (len == 4 && !memcmp(cstr, "json", len)) {
        res = JS_IMPORT_TYPE_JSON;
    } else if (len == 4 && !memcmp(cstr, "text", len)) {
        res = JS_IMPORT_TYPE_TEXT;
    } else if (len == 5 && !memcmp(cstr, "bytes", len)) {
        res = JS_IMPORT_TYPE_BYTES;
    } else {
        /* unknown type - throw error */
        JS_ThrowTypeError(ctx, "unsupported module type: '%s'", cstr);
        res = -1;
    }
    JS_FreeCString(ctx, cstr);
    return res;
}

JSModuleDef *js_module_load(JSContext *ctx, const char *module_name,
                            void *opaque, JSValueConst attributes,
                            JSLoadFileFunc *load_file)
{
    JSModuleDef *m;
    JSValue val;
    size_t buf_len;
    char *buf;
    int type;

    type = js_module_import_type(ctx, attributes);
    if (type < 0)
        return NULL;
    /* the .json suffix only selects the JSON type when no type attribute
       was given, so that e.g. 'with { type: "text" }' is honored */
    if (type == JS_IMPORT_TYPE_JS)
        if (js__has_suffix(module_name, ".json"))
            type = JS_IMPORT_TYPE_JSON;
    buf = (char *)load_file(ctx, &buf_len, module_name);
    if (!buf) {
        JS_ThrowReferenceError(ctx, "could not load module filename '%s'",
                               module_name);
        return NULL;
    }
    switch (type) {
    case JS_IMPORT_TYPE_JS:
        val = JS_Eval(ctx, buf, buf_len, module_name,
                      JS_EVAL_TYPE_MODULE | JS_EVAL_FLAG_COMPILE_ONLY);
        break;
    case JS_IMPORT_TYPE_JSON:
        val = JS_ParseJSON(ctx, buf, buf_len, module_name);
        break;
    case JS_IMPORT_TYPE_TEXT:
        val = JS_NewStringLen(ctx, buf, buf_len);
        break;
    case JS_IMPORT_TYPE_BYTES:
        val = JS_NewUint8Array(ctx, (uint8_t *)buf, buf_len,
                               js_realloc_array_buffer, NULL,
                               /*is_shared*/false);
        if (!JS_IsException(val)) {
            JSValue abuf = JS_GetTypedArrayBuffer(ctx, val, NULL, NULL, NULL);
            JS_SetImmutableArrayBuffer(abuf, /*immutable*/true);
            JS_FreeValue(ctx, abuf);
            buf = NULL;
        }
        break;
    default:
        val = JS_ThrowInternalError(ctx, "unhandled import type");
        break;
    }
    js_free(ctx, buf);
    if (JS_IsException(val))
        return NULL;
    if (type == JS_IMPORT_TYPE_JS) {
        if (js_module_set_import_meta(ctx, val, true, false) < 0) {
            JS_FreeValue(ctx, val);
            return NULL;
        }
        // the module is already referenced, so we must free it
        m = JS_VALUE_GET_PTR(val);
        JS_FreeValue(ctx, val);
    } else {
        m = JS_NewCModule(ctx, module_name, default_module_init);
        if (!m) {
            JS_FreeValue(ctx, val);
            return NULL;
        }
        // only export the "default" symbol which will contain the string
        // or JSON object
        JS_AddModuleExport(ctx, m, "default");
        JS_SetModulePrivateValue(ctx, m, val);
    }
    return m;
}

JSModuleDef *js_module_loader(JSContext *ctx, const char *module_name,
                              void *opaque, JSValueConst attributes)
{
    if (js__has_suffix(module_name, QJS_NATIVE_MODULE_SUFFIX))
        return js_module_loader_so(ctx, module_name);
    return js_module_load(ctx, module_name, opaque, attributes, js_load_file);
}

static JSValue js_std_exit(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv)
{
    int status;
    if (JS_ToInt32(ctx, &status, argv[0]))
        status = -1;
    exit(status);
    return JS_UNDEFINED;
}

static JSValue js_std_getenv(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    const char *name, *str;
    name = JS_ToCString(ctx, argv[0]);
    if (!name)
        return JS_EXCEPTION;
    str = getenv(name);
    JS_FreeCString(ctx, name);
    if (!str)
        return JS_UNDEFINED;
    else
        return JS_NewString(ctx, str);
}

#if defined(_WIN32)
static void setenv(const char *name, const char *value, int overwrite)
{
    char *str;
    size_t name_len, value_len;
    name_len = strlen(name);
    value_len = strlen(value);
    str = malloc(name_len + 1 + value_len + 1);
    memcpy(str, name, name_len);
    str[name_len] = '=';
    memcpy(str + name_len + 1, value, value_len);
    str[name_len + 1 + value_len] = '\0';
    _putenv(str);
    free(str);
}

static void unsetenv(const char *name)
{
    setenv(name, "", true);
}
#endif /* _WIN32 */

static JSValue js_std_setenv(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    const char *name, *value;
    name = JS_ToCString(ctx, argv[0]);
    if (!name)
        return JS_EXCEPTION;
    value = JS_ToCString(ctx, argv[1]);
    if (!value) {
        JS_FreeCString(ctx, name);
        return JS_EXCEPTION;
    }
    setenv(name, value, true);
    JS_FreeCString(ctx, name);
    JS_FreeCString(ctx, value);
    return JS_UNDEFINED;
}

static JSValue js_std_unsetenv(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    const char *name;
    name = JS_ToCString(ctx, argv[0]);
    if (!name)
        return JS_EXCEPTION;
    unsetenv(name);
    JS_FreeCString(ctx, name);
    return JS_UNDEFINED;
}

/* return an object containing the list of the available environment
   variables. */
static JSValue js_std_getenviron(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    char **envp;
    const char *name, *p, *value;
    JSValue obj;
    uint32_t idx;
    size_t name_len;
    JSAtom atom;
    int ret;

    obj = JS_NewObject(ctx);
    if (JS_IsException(obj))
        return JS_EXCEPTION;
    envp = environ;
    for(idx = 0; envp[idx] != NULL; idx++) {
        name = envp[idx];
        p = strchr(name, '=');
        name_len = p - name;
        if (!p)
            continue;
        value = p + 1;
        atom = JS_NewAtomLen(ctx, name, name_len);
        if (atom == JS_ATOM_NULL)
            goto fail;
        ret = JS_DefinePropertyValue(ctx, obj, atom, JS_NewString(ctx, value),
                                     JS_PROP_C_W_E);
        JS_FreeAtom(ctx, atom);
        if (ret < 0)
            goto fail;
    }
    return obj;
 fail:
    JS_FreeValue(ctx, obj);
    return JS_EXCEPTION;
}

static JSValue js_std_gc(JSContext *ctx, JSValueConst this_val,
                         int argc, JSValueConst *argv)
{
    JS_RunGC(JS_GetRuntime(ctx));
    return JS_UNDEFINED;
}

static int interrupt_handler(JSContext *ctx, void *opaque)
{
    JSThreadState *ts = opaque;

    if (1 & (os_pending_signals >> SIGINT))
        return 1;
    if (ts->prev_interrupt_handler)
        return ts->prev_interrupt_handler(ctx, ts->prev_interrupt_opaque);
    return 0;
}

static JSValue js_evalScript(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    const char *str = NULL;
    size_t len;
    JSValue ret, obj;
    JSValueConst options_obj, arg;
    bool backtrace_barrier = false;
    bool eval_function = false;
    bool eval_module = false;
    bool compile_only = false;
    bool compile_module = false;
    bool is_async = false;
    int flags;

    if (argc >= 2) {
        options_obj = argv[1];
        if (get_bool_option(ctx, &backtrace_barrier, options_obj,
                            "backtrace_barrier"))
            return JS_EXCEPTION;
        if (get_bool_option(ctx, &eval_function, options_obj,
                            "eval_function"))
            return JS_EXCEPTION;
        if (get_bool_option(ctx, &eval_module, options_obj,
                            "eval_module"))
            return JS_EXCEPTION;
        if (get_bool_option(ctx, &compile_only, options_obj,
                            "compile_only"))
            return JS_EXCEPTION;
        if (get_bool_option(ctx, &compile_module, options_obj,
                            "compile_module"))
            return JS_EXCEPTION;
        if (get_bool_option(ctx, &is_async, options_obj,
                            "async"))
            return JS_EXCEPTION;
    }

    if (eval_module) {
        arg = argv[0];
        if (JS_VALUE_GET_TAG(arg) != JS_TAG_MODULE)
            return JS_ThrowTypeError(ctx, "not a module");

        if (JS_ResolveModule(ctx, arg) < 0)
            return JS_EXCEPTION;

        if (js_module_set_import_meta(ctx, arg, false, false) < 0)
            return JS_EXCEPTION;

        return JS_EvalFunction(ctx, JS_DupValue(ctx, arg));
    }

    if (!eval_function) {
        str = JS_ToCStringLen(ctx, &len, argv[0]);
        if (!str)
            return JS_EXCEPTION;
    }
    if (!ts->recv_pipe && ++ts->eval_script_recurse == 1) {
        /* install the interrupt handler */
        ts->prev_interrupt_handler =
            (JSInterruptHandler *)js_std_cmd(/*GetInterruptHandler*/5, rt);
        ts->prev_interrupt_opaque =
            (void *)js_std_cmd(/*GetInterruptOpaque*/6, rt);
        // FIXME(bnoordhuis) Questionable hack to make the REPL interruptible.
        // Ideally qjs installs a signal handler + interrupt handler but then
        // scripts can intercept it with os.signal(SIGINT).
        JS_SetInterruptHandler(JS_GetRuntime(ctx), interrupt_handler, ts);
    }
    flags = compile_module ? JS_EVAL_TYPE_MODULE : JS_EVAL_TYPE_GLOBAL;
    if (backtrace_barrier)
        flags |= JS_EVAL_FLAG_BACKTRACE_BARRIER;
    if (compile_only)
        flags |= JS_EVAL_FLAG_COMPILE_ONLY;
    if (is_async)
        flags |= JS_EVAL_FLAG_ASYNC;
    if (eval_function) {
        obj = JS_DupValue(ctx, argv[0]);
        ret = JS_EvalFunction(ctx, obj); // takes ownership of |obj|
    } else {
        ret = JS_Eval(ctx, str, len, "<evalScript>", flags);
    }
    JS_FreeCString(ctx, str);
    if (!ts->recv_pipe && --ts->eval_script_recurse == 0) {
        /* remove the interrupt handler */
        JS_SetInterruptHandler(JS_GetRuntime(ctx),
                               ts->prev_interrupt_handler,
                               ts->prev_interrupt_opaque);
        ts->prev_interrupt_handler = NULL;
        ts->prev_interrupt_opaque = NULL;
        os_pending_signals &= ~((uint64_t)1 << SIGINT);
        /* convert the uncatchable "interrupted" error into a normal error
           so that it can be caught by the REPL */
        if (JS_IsException(ret))
            JS_ResetUncatchableError(ctx);
    }
    return ret;
}

typedef struct {
    FILE *f;
    bool is_popen;
} JSSTDFile;

static bool is_stdio(FILE *f)
{
    return f == stdin || f == stdout || f == stderr;
}

static void safe_close(FILE *f, bool is_popen)
{
    if (!f)
        return;
    if (is_stdio(f))
        return;
    if (is_popen) {
#if !defined(__wasi__)
        pclose(f);
#endif
    } else {
        fclose(f);
    }
}

static void js_std_file_finalizer(JSRuntime *rt, JSValueConst val)
{
    JSThreadState *ts = js_get_thread_state(rt);
    JSSTDFile *s = JS_GetOpaque(val, ts->std_file_class_id);
    if (s) {
        safe_close(s->f, s->is_popen);
        js_free_rt(rt, s);
    }
}

static ssize_t js_get_errno(ssize_t ret)
{
    if (ret == -1)
        ret = -errno;
    return ret;
}

static JSValue js_std_strerror(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    int err;
    if (JS_ToInt32(ctx, &err, argv[0]))
        return JS_EXCEPTION;
    return JS_NewString(ctx, strerror(err));
}

static JSValue js_new_std_file(JSContext *ctx, FILE *f, bool is_popen)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSSTDFile *s;
    JSValue obj;
    obj = JS_NewObjectClass(ctx, ts->std_file_class_id);
    if (JS_IsException(obj))
        goto exception;
    s = js_mallocz(ctx, sizeof(*s));
    if (!s)
        goto exception;
    s->is_popen = is_popen;
    s->f = f;
    JS_SetOpaque(obj, s);
    return obj;
exception:
    safe_close(f, is_popen);
    JS_FreeValue(ctx, obj);
    return JS_EXCEPTION;
}

static void js_set_error_object(JSContext *ctx, JSValueConst obj, int err)
{
    if (!JS_IsUndefined(obj)) {
        JS_SetPropertyStr(ctx, obj, "errno", JS_NewInt32(ctx, err));
    }
}

static JSValue js_std_open(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv)
{
    const char *filename, *mode = NULL;
    FILE *f;
    int err;

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        goto fail;
    mode = JS_ToCString(ctx, argv[1]);
    if (!mode)
        goto fail;
    if (mode[strspn(mode, "rwa+bx")] != '\0') {
        JS_ThrowTypeError(ctx, "invalid file mode");
        goto fail;
    }

    f = fopen(filename, mode);
    if (!f)
        err = errno;
    else
        err = 0;
    if (argc >= 3)
        js_set_error_object(ctx, argv[2], err);
    JS_FreeCString(ctx, filename);
    JS_FreeCString(ctx, mode);
    if (!f)
        return JS_NULL;
    return js_new_std_file(ctx, f, false);
 fail:
    JS_FreeCString(ctx, filename);
    JS_FreeCString(ctx, mode);
    return JS_EXCEPTION;
}

#if !defined(__wasi__)
static JSValue js_std_popen(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    const char *filename, *mode = NULL;
    FILE *f;
    int err;

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        goto fail;
    mode = JS_ToCString(ctx, argv[1]);
    if (!mode)
        goto fail;
    if (strcmp(mode, "r") && strcmp(mode, "w")) {
        JS_ThrowTypeError(ctx, "invalid file mode");
        goto fail;
    }

    f = popen(filename, mode);
    if (!f)
        err = errno;
    else
        err = 0;
    if (argc >= 3)
        js_set_error_object(ctx, argv[2], err);
    JS_FreeCString(ctx, filename);
    JS_FreeCString(ctx, mode);
    if (!f)
        return JS_NULL;
    return js_new_std_file(ctx, f, true);
 fail:
    JS_FreeCString(ctx, filename);
    JS_FreeCString(ctx, mode);
    return JS_EXCEPTION;
}
#endif // !defined(__wasi__)

static JSValue js_std_fdopen(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    const char *mode;
    FILE *f;
    int fd, err;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    mode = JS_ToCString(ctx, argv[1]);
    if (!mode)
        goto fail;
    if (mode[strspn(mode, "rwa+")] != '\0') {
        JS_ThrowTypeError(ctx, "invalid file mode");
        goto fail;
    }

    f = fdopen(fd, mode);
    if (!f)
        err = errno;
    else
        err = 0;
    if (argc >= 3)
        js_set_error_object(ctx, argv[2], err);
    JS_FreeCString(ctx, mode);
    if (!f)
        return JS_NULL;
    return js_new_std_file(ctx, f, false);
 fail:
    JS_FreeCString(ctx, mode);
    return JS_EXCEPTION;
}

#if !defined(__wasi__)
static JSValue js_std_tmpfile(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    FILE *f;
    f = tmpfile();
    if (argc >= 1)
        js_set_error_object(ctx, argv[0], f ? 0 : errno);
    if (!f)
        return JS_NULL;
    return js_new_std_file(ctx, f, false);
}
#endif

static JSValue js_std_sprintf(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    return js_printf_internal(ctx, argc, argv, NULL);
}

static JSValue js_std_printf(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    return js_printf_internal(ctx, argc, argv, stdout);
}

static FILE *js_std_file_get(JSContext *ctx, JSValueConst obj)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSSTDFile *s = JS_GetOpaque2(ctx, obj, ts->std_file_class_id);
    if (!s)
        return NULL;
    if (!s->f) {
        JS_ThrowTypeError(ctx, "invalid file handle");
        return NULL;
    }
    return s->f;
}

static JSValue js_std_file_puts(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv, int magic)
{
    FILE *f;
    int i;
    const char *str;
    size_t len;

    if (magic == 0) {
        f = stdout;
    } else {
        f = js_std_file_get(ctx, this_val);
        if (!f)
            return JS_EXCEPTION;
    }

    for(i = 0; i < argc; i++) {
        str = JS_ToCStringLen(ctx, &len, argv[i]);
        if (!str)
            return JS_EXCEPTION;
        fwrite(str, 1, len, f);
        JS_FreeCString(ctx, str);
    }
    return JS_UNDEFINED;
}

static JSValue js_std_file_close(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSSTDFile *s = JS_GetOpaque2(ctx, this_val, ts->std_file_class_id);
    int err;
    if (!s)
        return JS_EXCEPTION;
    if (!s->f)
        return JS_ThrowTypeError(ctx, "invalid file handle");
    if (is_stdio(s->f))
        return JS_ThrowTypeError(ctx, "cannot close stdio");
#if !defined(__wasi__)
    if (s->is_popen)
        err = js_get_errno(pclose(s->f));
    else
#endif
        err = js_get_errno(fclose(s->f));
    s->f = NULL;
    return JS_NewInt32(ctx, err);
}

static JSValue js_std_file_printf(JSContext *ctx, JSValueConst this_val,
                                  int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    return js_printf_internal(ctx, argc, argv, f);
}

static JSValue js_std_file_flush(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    fflush(f);
    return JS_UNDEFINED;
}

static JSValue js_std_file_tell(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv, int is_bigint)
{
    FILE *f = js_std_file_get(ctx, this_val);
    int64_t pos;
    if (!f)
        return JS_EXCEPTION;
#if defined(__linux__) || defined(__GLIBC__)
    pos = ftello(f);
#else
    pos = ftell(f);
#endif
    if (is_bigint)
        return JS_NewBigInt64(ctx, pos);
    else
        return JS_NewInt64(ctx, pos);
}

static JSValue js_std_file_seek(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    int64_t pos;
    int whence, ret;
    if (!f)
        return JS_EXCEPTION;
    if (JS_ToInt64Ext(ctx, &pos, argv[0]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &whence, argv[1]))
        return JS_EXCEPTION;
#if defined(__linux__) || defined(__GLIBC__)
    ret = fseeko(f, pos, whence);
#else
    ret = fseek(f, pos, whence);
#endif
    if (ret < 0)
        ret = -errno;
    return JS_NewInt32(ctx, ret);
}

static JSValue js_std_file_eof(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    return JS_NewBool(ctx, feof(f));
}

static JSValue js_std_file_error(JSContext *ctx, JSValueConst this_val,
                                 int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    return JS_NewBool(ctx, ferror(f));
}

static JSValue js_std_file_clearerr(JSContext *ctx, JSValueConst this_val,
                                    int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    clearerr(f);
    return JS_UNDEFINED;
}

static JSValue js_std_file_fileno(JSContext *ctx, JSValueConst this_val,
                                  int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    return JS_NewInt32(ctx, fileno(f));
}

static JSValue js_std_file_read_write(JSContext *ctx, JSValueConst this_val,
                                      int argc, JSValueConst *argv, int magic)
{
    FILE *f = js_std_file_get(ctx, this_val);
    bool is_write = (magic != 0);
    uint64_t pos, len;
    size_t size, ret;
    const char *str;
    uint8_t *buf;

    if (!f)
        return JS_EXCEPTION;
    pos = 0;
    if (argc > 1 && JS_ToIndex(ctx, &pos, argv[1]))
        return JS_EXCEPTION;
    len = 0;
    if (argc > 2 && JS_ToIndex(ctx, &len, argv[2]))
        return JS_EXCEPTION;
    if (is_write && JS_IsString(argv[0])) {
        str = JS_ToCStringLen(ctx, &size, argv[0]);
        buf = (void *)str;
    } else {
        str = NULL;
        buf = JS_GetArrayBuffer(ctx, &size, argv[0]);
    }
    if (!buf)
        return JS_EXCEPTION;
    if (pos > size)
        pos = size;
    if (argc < 3)
        len = size - pos;
    if (pos + len > size)
        len = size - pos;
    if (is_write) {
        ret = fwrite(buf + pos, 1, len, f);
    } else {
        ret = fread(buf + pos, 1, len, f);
    }
    JS_FreeCString(ctx, str);
    return JS_NewInt64(ctx, ret);
}

/* XXX: could use less memory and go faster */
static JSValue js_std_file_getline(JSContext *ctx, JSValueConst this_val,
                                   int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    int c;
    DynBuf dbuf;
    JSValue obj;

    if (!f)
        return JS_EXCEPTION;

    js_std_dbuf_init(ctx, &dbuf);
    for(;;) {
        c = fgetc(f);
        if (c == EOF) {
            if (dbuf.size == 0) {
                /* EOF */
                dbuf_free(&dbuf);
                return JS_NULL;
            } else {
                break;
            }
        }
        if (c == '\n')
            break;
        if (dbuf_putc(&dbuf, c)) {
            dbuf_free(&dbuf);
            return JS_ThrowOutOfMemory(ctx);
        }
    }
    obj = JS_NewStringLen(ctx, (const char *)dbuf.buf, dbuf.size);
    dbuf_free(&dbuf);
    return obj;
}

/* XXX: could use less memory and go faster */
static JSValue js_std_file_readAs(JSContext *ctx, JSValueConst this_val,
                                  int argc, JSValueConst *argv, int magic)
{
    FILE *f = js_std_file_get(ctx, this_val);
    int c;
    DynBuf dbuf;
    JSValue obj;
    uint64_t max_size64;
    size_t max_size;
    JSValueConst max_size_val;

    if (!f)
        return JS_EXCEPTION;

    if (argc >= 1)
        max_size_val = argv[0];
    else
        max_size_val = JS_UNDEFINED;
    max_size = (size_t)-1;
    if (!JS_IsUndefined(max_size_val)) {
        if (JS_ToIndex(ctx, &max_size64, max_size_val))
            return JS_EXCEPTION;
        if (max_size64 < max_size)
            max_size = max_size64;
    }

    js_std_dbuf_init(ctx, &dbuf);
    while (max_size != 0) {
        c = fgetc(f);
        if (c == EOF)
            break;
        if (dbuf_putc(&dbuf, c)) {
            dbuf_free(&dbuf);
            return JS_EXCEPTION;
        }
        max_size--;
    }
    if (magic) {
        obj = JS_NewStringLen(ctx, (const char *)dbuf.buf, dbuf.size);
    } else {
        obj = JS_NewArrayBufferCopy(ctx, dbuf.buf, dbuf.size);
    }
    dbuf_free(&dbuf);
    return obj;
}

static JSValue js_std_file_getByte(JSContext *ctx, JSValueConst this_val,
                                   int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    if (!f)
        return JS_EXCEPTION;
    return JS_NewInt32(ctx, fgetc(f));
}

static JSValue js_std_file_putByte(JSContext *ctx, JSValueConst this_val,
                                   int argc, JSValueConst *argv)
{
    FILE *f = js_std_file_get(ctx, this_val);
    int c;
    if (!f)
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &c, argv[0]))
        return JS_EXCEPTION;
    c = fputc(c, f);
    return JS_NewInt32(ctx, c);
}

/* urlGet */
#if !defined(__wasi__)

#define URL_GET_PROGRAM "curl -s -i --"
#define URL_GET_BUF_SIZE 4096

static int http_get_header_line(FILE *f, char *buf, size_t buf_size,
                                DynBuf *dbuf)
{
    int c;
    char *p;

    p = buf;
    for(;;) {
        c = fgetc(f);
        if (c < 0)
            return -1;
        if ((p - buf) < buf_size - 1)
            *p++ = c;
        if (dbuf)
            dbuf_putc(dbuf, c);
        if (c == '\n')
            break;
    }
    *p = '\0';
    return 0;
}

static int http_get_status(const char *buf)
{
    const char *p = buf;
    while (*p != ' ' && *p != '\0')
        p++;
    if (*p != ' ')
        return 0;
    while (*p == ' ')
        p++;
    return atoi(p);
}

static JSValue js_std_urlGet(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    const char *url;
    DynBuf cmd_buf;
    DynBuf data_buf_s, *data_buf = &data_buf_s;
    DynBuf header_buf_s, *header_buf = &header_buf_s;
    char *buf;
    size_t i, len;
    int status;
    JSValue response = JS_UNDEFINED, ret_obj;
    JSValueConst options_obj;
    FILE *f;
    bool binary_flag, full_flag;

    url = JS_ToCString(ctx, argv[0]);
    if (!url)
        return JS_EXCEPTION;

    binary_flag = false;
    full_flag = false;

    if (argc >= 2) {
        options_obj = argv[1];

        if (get_bool_option(ctx, &binary_flag, options_obj, "binary"))
            goto fail_obj;

        if (get_bool_option(ctx, &full_flag, options_obj, "full")) {
        fail_obj:
            JS_FreeCString(ctx, url);
            return JS_EXCEPTION;
        }
    }

    js_std_dbuf_init(ctx, &cmd_buf);
    dbuf_printf(&cmd_buf, "%s '", URL_GET_PROGRAM);
    for(i = 0; url[i] != '\0'; i++) {
        unsigned char c = url[i];
        switch (c) {
        case '\'':
            /* shell single quoted string does not support \' */
            dbuf_putstr(&cmd_buf, "'\\''");
            break;
        case '[': case ']': case '{': case '}': case '\\':
            /* prevent interpretation by curl as range or set specification */
            dbuf_putc(&cmd_buf, '\\');
            /* FALLTHROUGH */
        default:
            dbuf_putc(&cmd_buf, c);
            break;
        }
    }
    JS_FreeCString(ctx, url);
    dbuf_putstr(&cmd_buf, "'");
    dbuf_putc(&cmd_buf, '\0');
    if (dbuf_error(&cmd_buf)) {
        dbuf_free(&cmd_buf);
        return JS_EXCEPTION;
    }
    //    printf("%s\n", (char *)cmd_buf.buf);
    f = popen((char *)cmd_buf.buf, "r");
    dbuf_free(&cmd_buf);
    if (!f) {
        return JS_ThrowTypeError(ctx, "could not start curl");
    }

    js_std_dbuf_init(ctx, data_buf);
    js_std_dbuf_init(ctx, header_buf);

    buf = js_malloc(ctx, URL_GET_BUF_SIZE);
    if (!buf)
        goto fail;

    /* get the HTTP status */
    if (http_get_header_line(f, buf, URL_GET_BUF_SIZE, NULL) < 0) {
        status = 0;
        goto bad_header;
    }
    status = http_get_status(buf);
    if (!full_flag && !(status >= 200 && status <= 299)) {
        goto bad_header;
    }

    /* wait until there is an empty line */
    for(;;) {
        if (http_get_header_line(f, buf, URL_GET_BUF_SIZE, header_buf) < 0) {
        bad_header:
            response = JS_NULL;
            goto done;
        }
        if (!strcmp(buf, "\r\n"))
            break;
    }
    if (dbuf_error(header_buf))
        goto fail;
    header_buf->size -= 2; /* remove the trailing CRLF */

    /* download the data */
    for(;;) {
        len = fread(buf, 1, URL_GET_BUF_SIZE, f);
        if (len == 0)
            break;
        dbuf_put(data_buf, (uint8_t *)buf, len);
    }
    if (dbuf_error(data_buf))
        goto fail;
    if (binary_flag) {
        response = JS_NewArrayBufferCopy(ctx,
                                         data_buf->buf, data_buf->size);
    } else {
        response = JS_NewStringLen(ctx, (char *)data_buf->buf, data_buf->size);
    }
    if (JS_IsException(response))
        goto fail;
 done:
    js_free(ctx, buf);
    buf = NULL;
    pclose(f);
    f = NULL;
    dbuf_free(data_buf);
    data_buf = NULL;

    if (full_flag) {
        ret_obj = JS_NewObject(ctx);
        if (JS_IsException(ret_obj))
            goto fail;
        JS_DefinePropertyValueStr(ctx, ret_obj, "response",
                                  response,
                                  JS_PROP_C_W_E);
        if (!JS_IsNull(response)) {
            JS_DefinePropertyValueStr(ctx, ret_obj, "responseHeaders",
                                      JS_NewStringLen(ctx, (char *)header_buf->buf,
                                                      header_buf->size),
                                      JS_PROP_C_W_E);
            JS_DefinePropertyValueStr(ctx, ret_obj, "status",
                                      JS_NewInt32(ctx, status),
                                      JS_PROP_C_W_E);
        }
    } else {
        ret_obj = response;
    }
    dbuf_free(header_buf);
    return ret_obj;
 fail:
    if (f)
        pclose(f);
    js_free(ctx, buf);
    if (data_buf)
        dbuf_free(data_buf);
    if (header_buf)
        dbuf_free(header_buf);
    JS_FreeValue(ctx, response);
    return JS_EXCEPTION;
}
#endif // !defined(__wasi__)

static JSClassDef js_std_file_class = {
    "FILE",
    .finalizer = js_std_file_finalizer,
};

static const JSCFunctionListEntry js_std_error_props[] = {
    /* various errno values */
#define DEF(x) JS_PROP_INT32_DEF(#x, x, JS_PROP_CONFIGURABLE )
    DEF(EINVAL),
    DEF(EIO),
    DEF(EACCES),
    DEF(EEXIST),
    DEF(ENOSPC),
    DEF(ENOSYS),
    DEF(EBUSY),
    DEF(ENOENT),
    DEF(EPERM),
    DEF(EPIPE),
    DEF(EBADF),
#undef DEF
};

static const JSCFunctionListEntry js_std_funcs[] = {
    JS_CFUNC_DEF("exit", 1, js_std_exit ),
    JS_CFUNC_DEF("gc", 0, js_std_gc ),
    JS_CFUNC_DEF("evalScript", 1, js_evalScript ),
    JS_CFUNC_DEF("loadScript", 1, js_loadScript ),
    JS_CFUNC_DEF("getenv", 1, js_std_getenv ),
    JS_CFUNC_DEF("setenv", 2, js_std_setenv ),
    JS_CFUNC_DEF("unsetenv", 1, js_std_unsetenv ),
    JS_CFUNC_DEF("getenviron", 1, js_std_getenviron ),
#if !defined(__wasi__)
    JS_CFUNC_DEF("urlGet", 1, js_std_urlGet ),
#endif
    JS_CFUNC_DEF("loadFile", 1, js_std_loadFile ),
    JS_CFUNC_DEF("writeFile", 2, js_std_writeFile ),
    JS_CFUNC_DEF("strerror", 1, js_std_strerror ),

    /* FILE I/O */
    JS_CFUNC_DEF("open", 2, js_std_open ),
#if !defined(__wasi__)
    JS_CFUNC_DEF("popen", 2, js_std_popen ),
    JS_CFUNC_DEF("tmpfile", 0, js_std_tmpfile ),
#endif
    JS_CFUNC_DEF("fdopen", 2, js_std_fdopen ),
    JS_CFUNC_MAGIC_DEF("puts", 1, js_std_file_puts, 0 ),
    JS_CFUNC_DEF("printf", 1, js_std_printf ),
    JS_CFUNC_DEF("sprintf", 1, js_std_sprintf ),
    JS_PROP_INT32_DEF("SEEK_SET", SEEK_SET, JS_PROP_CONFIGURABLE ),
    JS_PROP_INT32_DEF("SEEK_CUR", SEEK_CUR, JS_PROP_CONFIGURABLE ),
    JS_PROP_INT32_DEF("SEEK_END", SEEK_END, JS_PROP_CONFIGURABLE ),
    JS_OBJECT_DEF("Error", js_std_error_props, countof(js_std_error_props), JS_PROP_CONFIGURABLE),
};

static const JSCFunctionListEntry js_std_file_proto_funcs[] = {
    JS_CFUNC_DEF("close", 0, js_std_file_close ),
    JS_CFUNC_MAGIC_DEF("puts", 1, js_std_file_puts, 1 ),
    JS_CFUNC_DEF("printf", 1, js_std_file_printf ),
    JS_CFUNC_DEF("flush", 0, js_std_file_flush ),
    JS_CFUNC_MAGIC_DEF("tell", 0, js_std_file_tell, 0 ),
    JS_CFUNC_MAGIC_DEF("tello", 0, js_std_file_tell, 1 ),
    JS_CFUNC_DEF("seek", 2, js_std_file_seek ),
    JS_CFUNC_DEF("eof", 0, js_std_file_eof ),
    JS_CFUNC_DEF("fileno", 0, js_std_file_fileno ),
    JS_CFUNC_DEF("error", 0, js_std_file_error ),
    JS_CFUNC_DEF("clearerr", 0, js_std_file_clearerr ),
    JS_CFUNC_MAGIC_DEF("read", 1, js_std_file_read_write, 0 ),
    JS_CFUNC_MAGIC_DEF("write", 1, js_std_file_read_write, 1 ),
    JS_CFUNC_DEF("getline", 0, js_std_file_getline ),
    JS_CFUNC_MAGIC_DEF("readAsArrayBuffer", 0, js_std_file_readAs, 0 ),
    JS_CFUNC_MAGIC_DEF("readAsString", 0, js_std_file_readAs, 1 ),
    JS_CFUNC_DEF("getByte", 0, js_std_file_getByte ),
    JS_CFUNC_DEF("putByte", 1, js_std_file_putByte ),
    /* setvbuf, ...  */
};

static int js_std_init(JSContext *ctx, JSModuleDef *m)
{
    JSValue proto;
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);

    /* FILE class */
    /* the class ID is created once */
    JS_NewClassID(rt, &ts->std_file_class_id);
    /* the class is created once per runtime */
    JS_NewClass(rt, ts->std_file_class_id, &js_std_file_class);
    proto = JS_NewObject(ctx);
    JS_SetPropertyFunctionList(ctx, proto, js_std_file_proto_funcs,
                               countof(js_std_file_proto_funcs));
    JS_SetClassProto(ctx, ts->std_file_class_id, proto);

    JS_SetModuleExportList(ctx, m, js_std_funcs,
                           countof(js_std_funcs));
    JS_SetModuleExport(ctx, m, "in", js_new_std_file(ctx, stdin, false));
    JS_SetModuleExport(ctx, m, "out", js_new_std_file(ctx, stdout, false));
    JS_SetModuleExport(ctx, m, "err", js_new_std_file(ctx, stderr, false));
    return 0;
}

JSModuleDef *js_init_module_std(JSContext *ctx, const char *module_name)
{
    JSModuleDef *m;
    m = JS_NewCModule(ctx, module_name, js_std_init);
    if (!m)
        return NULL;
    JS_AddModuleExportList(ctx, m, js_std_funcs, countof(js_std_funcs));
    JS_AddModuleExport(ctx, m, "in");
    JS_AddModuleExport(ctx, m, "out");
    JS_AddModuleExport(ctx, m, "err");
    return m;
}

/**********************************************************/
/* 'os' object */

static JSValue js_os_open(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    const char *filename;
    int flags, mode, ret;

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &flags, argv[1]))
        goto fail;
    if (argc >= 3 && !JS_IsUndefined(argv[2])) {
        if (JS_ToInt32(ctx, &mode, argv[2])) {
        fail:
            JS_FreeCString(ctx, filename);
            return JS_EXCEPTION;
        }
    } else {
        mode = 0666;
    }
#if defined(_WIN32)
    /* force binary mode by default */
    if (!(flags & O_TEXT))
        flags |= O_BINARY;
#endif
    ret = js_get_errno(open(filename, flags, mode));
    JS_FreeCString(ctx, filename);
    return JS_NewInt32(ctx, ret);
}

static JSValue js_os_close(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv)
{
    int fd, ret;
    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    ret = js_get_errno(close(fd));
    return JS_NewInt32(ctx, ret);
}

static JSValue js_os_seek(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    int fd, whence;
    int64_t pos, ret;
    bool is_bigint;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    is_bigint = JS_IsBigInt(argv[1]);
    if (JS_ToInt64Ext(ctx, &pos, argv[1]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &whence, argv[2]))
        return JS_EXCEPTION;
    ret = lseek(fd, pos, whence);
    if (ret == -1)
        ret = -errno;
    if (is_bigint)
        return JS_NewBigInt64(ctx, ret);
    else
        return JS_NewInt64(ctx, ret);
}

static JSValue js_os_read_write(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv, int magic)
{
    int fd;
    uint64_t pos, len;
    size_t size;
    ssize_t ret;
    uint8_t *buf;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    if (JS_ToIndex(ctx, &pos, argv[2]))
        return JS_EXCEPTION;
    if (JS_ToIndex(ctx, &len, argv[3]))
        return JS_EXCEPTION;
    buf = JS_GetArrayBuffer(ctx, &size, argv[1]);
    if (!buf)
        return JS_EXCEPTION;
    if (pos + len > size)
        return JS_ThrowRangeError(ctx, "read/write array buffer overflow");
    if (magic)
        ret = js_get_errno(write(fd, buf + pos, len));
    else
        ret = js_get_errno(read(fd, buf + pos, len));
    return JS_NewInt64(ctx, ret);
}

static JSValue js_os_isatty(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    int fd;
    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    return JS_NewBool(ctx, (isatty(fd) != 0));
}

#if defined(_WIN32)
static JSValue js_os_ttyGetWinSize(JSContext *ctx, JSValueConst this_val,
                                   int argc, JSValueConst *argv)
{
    int fd;
    HANDLE handle;
    CONSOLE_SCREEN_BUFFER_INFO info;
    JSValue obj;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    handle = (HANDLE)_get_osfhandle(fd);

    if (!GetConsoleScreenBufferInfo(handle, &info))
        return JS_NULL;
    obj = JS_NewArray(ctx);
    if (JS_IsException(obj))
        return obj;
    JS_DefinePropertyValueUint32(ctx, obj, 0, JS_NewInt32(ctx, info.dwSize.X), JS_PROP_C_W_E);
    JS_DefinePropertyValueUint32(ctx, obj, 1, JS_NewInt32(ctx, info.dwSize.Y), JS_PROP_C_W_E);
    return obj;
}

/* Windows 10 built-in VT100 emulation */
#define __ENABLE_VIRTUAL_TERMINAL_PROCESSING 0x0004
#define __ENABLE_VIRTUAL_TERMINAL_INPUT 0x0200

static JSValue js_os_ttySetRaw(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    int fd;
    HANDLE handle;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    handle = (HANDLE)_get_osfhandle(fd);
    SetConsoleMode(handle, ENABLE_WINDOW_INPUT | __ENABLE_VIRTUAL_TERMINAL_INPUT);
    _setmode(fd, _O_BINARY);
    if (fd == 0) {
        handle = (HANDLE)_get_osfhandle(1); /* corresponding output */
        SetConsoleMode(handle, ENABLE_PROCESSED_OUTPUT | ENABLE_WRAP_AT_EOL_OUTPUT | __ENABLE_VIRTUAL_TERMINAL_PROCESSING);
    }
    return JS_UNDEFINED;
}
#elif !defined(__wasi__)
static JSValue js_os_ttyGetWinSize(JSContext *ctx, JSValueConst this_val,
                                   int argc, JSValueConst *argv)
{
    int fd;
    struct winsize ws;
    JSValue obj;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    if (ioctl(fd, TIOCGWINSZ, &ws) == 0 &&
        ws.ws_col >= 4 && ws.ws_row >= 4) {
        obj = JS_NewArray(ctx);
        if (JS_IsException(obj))
            return obj;
        JS_DefinePropertyValueUint32(ctx, obj, 0, JS_NewInt32(ctx, ws.ws_col), JS_PROP_C_W_E);
        JS_DefinePropertyValueUint32(ctx, obj, 1, JS_NewInt32(ctx, ws.ws_row), JS_PROP_C_W_E);
        return obj;
    } else {
        return JS_NULL;
    }
}

static struct termios oldtty;

static void term_exit(void)
{
    tcsetattr(0, TCSANOW, &oldtty);
}

/* XXX: should add a way to go back to normal mode */
static JSValue js_os_ttySetRaw(JSContext *ctx, JSValueConst this_val,
                               int argc, JSValueConst *argv)
{
    struct termios tty;
    int fd;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;

    memset(&tty, 0, sizeof(tty));
    tcgetattr(fd, &tty);
    oldtty = tty;

    tty.c_iflag &= ~(IGNBRK|BRKINT|PARMRK|ISTRIP
                          |INLCR|IGNCR|ICRNL|IXON);
    tty.c_oflag |= OPOST;
    tty.c_lflag &= ~(ECHO|ECHONL|ICANON|IEXTEN);
    tty.c_cflag &= ~(CSIZE|PARENB);
    tty.c_cflag |= CS8;
    tty.c_cc[VMIN] = 1;
    tty.c_cc[VTIME] = 0;

    tcsetattr(fd, TCSANOW, &tty);

    atexit(term_exit);
    return JS_UNDEFINED;
}

#endif /* !_WIN32 && !__wasi__ */

static JSValue js_os_remove(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    const char *filename;
    int ret;

    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        return JS_EXCEPTION;
#if defined(_WIN32)
    {
        struct stat st;
        if (stat(filename, &st) == 0 && (st.st_mode & _S_IFDIR)) {
            ret = rmdir(filename);
        } else {
            ret = unlink(filename);
        }
    }
#else
    ret = remove(filename);
#endif
    ret = js_get_errno(ret);
    JS_FreeCString(ctx, filename);
    return JS_NewInt32(ctx, ret);
}

static JSValue js_os_rename(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    const char *oldpath, *newpath;
    int ret;

    oldpath = JS_ToCString(ctx, argv[0]);
    if (!oldpath)
        return JS_EXCEPTION;
    newpath = JS_ToCString(ctx, argv[1]);
    if (!newpath) {
        JS_FreeCString(ctx, oldpath);
        return JS_EXCEPTION;
    }
    ret = js_get_errno(rename(oldpath, newpath));
    JS_FreeCString(ctx, oldpath);
    JS_FreeCString(ctx, newpath);
    return JS_NewInt32(ctx, ret);
}

static bool is_main_thread(JSRuntime *rt)
{
    JSThreadState *ts = js_get_thread_state(rt);
    return !ts->recv_pipe;
}

static JSOSRWHandler *find_rh(JSThreadState *ts, int fd)
{
    JSOSRWHandler *rh;
    struct list_head *el;

    list_for_each(el, &ts->os_rw_handlers) {
        rh = list_entry(el, JSOSRWHandler, link);
        if (rh->fd == fd)
            return rh;
    }
    return NULL;
}

static void free_rw_handler(JSRuntime *rt, JSOSRWHandler *rh)
{
    int i;
    list_del(&rh->link);
    for(i = 0; i < 2; i++) {
        JS_FreeValueRT(rt, rh->rw_func[i]);
    }
    js_free_rt(rt, rh);
}

static JSValue js_os_setReadHandler(JSContext *ctx, JSValueConst this_val,
                                    int argc, JSValueConst *argv, int magic)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSOSRWHandler *rh;
    int fd;
    JSValueConst func;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    func = argv[1];
    if (JS_IsNull(func)) {
        rh = find_rh(ts, fd);
        if (rh) {
            JS_FreeValue(ctx, rh->rw_func[magic]);
            rh->rw_func[magic] = JS_NULL;
            if (JS_IsNull(rh->rw_func[0]) &&
                JS_IsNull(rh->rw_func[1])) {
                /* remove the entry */
                free_rw_handler(JS_GetRuntime(ctx), rh);
            }
        }
    } else {
        if (!JS_IsFunction(ctx, func))
            return JS_ThrowTypeError(ctx, "not a function");
        rh = find_rh(ts, fd);
        if (!rh) {
            rh = js_mallocz(ctx, sizeof(*rh));
            if (!rh)
                return JS_EXCEPTION;
            rh->fd = fd;
            rh->rw_func[0] = JS_NULL;
            rh->rw_func[1] = JS_NULL;
            list_add_tail(&rh->link, &ts->os_rw_handlers);
        }
        JS_FreeValue(ctx, rh->rw_func[magic]);
        rh->rw_func[magic] = JS_DupValue(ctx, func);
    }
    return JS_UNDEFINED;
}

static JSOSSignalHandler *find_sh(JSThreadState *ts, int sig_num)
{
    JSOSSignalHandler *sh;
    struct list_head *el;
    list_for_each(el, &ts->os_signal_handlers) {
        sh = list_entry(el, JSOSSignalHandler, link);
        if (sh->sig_num == sig_num)
            return sh;
    }
    return NULL;
}

static void free_sh(JSRuntime *rt, JSOSSignalHandler *sh)
{
    list_del(&sh->link);
    JS_FreeValueRT(rt, sh->func);
    js_free_rt(rt, sh);
}

static void os_signal_handler(int sig_num)
{
    os_pending_signals |= ((uint64_t)1 << sig_num);
}

#if defined(_WIN32)
typedef void (*sighandler_t)(int sig_num);
#endif

static JSValue js_os_signal(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSOSSignalHandler *sh;
    uint32_t sig_num;
    JSValueConst func;
    sighandler_t handler;

    if (!is_main_thread(rt))
        return JS_ThrowTypeError(ctx, "signal handler can only be set in the main thread");

    if (JS_ToUint32(ctx, &sig_num, argv[0]))
        return JS_EXCEPTION;
    if (sig_num >= 64)
        return JS_ThrowRangeError(ctx, "invalid signal number");
    func = argv[1];
    /* func = null: SIG_DFL, func = undefined, SIG_IGN */
    if (JS_IsNull(func) || JS_IsUndefined(func)) {
        sh = find_sh(ts, sig_num);
        if (sh) {
            free_sh(JS_GetRuntime(ctx), sh);
        }
        if (JS_IsNull(func))
            handler = SIG_DFL;
        else
            handler = SIG_IGN;
        signal(sig_num, handler);
    } else {
        if (!JS_IsFunction(ctx, func))
            return JS_ThrowTypeError(ctx, "not a function");
        sh = find_sh(ts, sig_num);
        if (!sh) {
            sh = js_mallocz(ctx, sizeof(*sh));
            if (!sh)
                return JS_EXCEPTION;
            sh->sig_num = sig_num;
            list_add_tail(&sh->link, &ts->os_signal_handlers);
        }
        JS_FreeValue(ctx, sh->func);
        sh->func = JS_DupValue(ctx, func);
        signal(sig_num, os_signal_handler);
    }
    return JS_UNDEFINED;
}

#if !defined(_WIN32) && !defined(__wasi__)
static JSValue js_os_cputime(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    struct rusage ru;
    int64_t cputime;

    cputime = 0;
    if (!getrusage(RUSAGE_SELF, &ru))
        cputime = (int64_t)ru.ru_utime.tv_sec * 1000000 + ru.ru_utime.tv_usec;

    return JS_NewInt64(ctx, cputime);
}
#endif

static JSValue js_os_exepath(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    char buf[JS__PATH_MAX];
    size_t len = sizeof(buf);
    if (js_exepath(buf, &len))
        return JS_UNDEFINED;
    return JS_NewStringLen(ctx, buf, len);
}

static JSValue js_os_now(JSContext *ctx, JSValueConst this_val,
                         int argc, JSValueConst *argv)
{
    return JS_NewInt64(ctx, js__hrtime_ns() / 1000);
}

static uint64_t js__hrtime_ms(void)
{
    return js__hrtime_ns() / (1000 * 1000);
}

static void free_timer(JSRuntime *rt, JSOSTimer *th)
{
    list_del(&th->link);
    JS_FreeValueRT(rt, th->func);
    js_free_rt(rt, th);
}

// TODO(bnoordhuis) accept string as first arg and eval at timer expiry
// TODO(bnoordhuis) retain argv[2..] as args for callback if argc > 2
static JSValue js_os_setTimeout(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv, int magic)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    int64_t delay;
    JSValueConst func;
    JSOSTimer *th;

    func = argv[0];
    if (!JS_IsFunction(ctx, func))
        return JS_ThrowTypeError(ctx, "not a function");
    if (JS_ToInt64(ctx, &delay, argv[1]))
        return JS_EXCEPTION;
    if (delay < 1)
        delay = 1;
    th = js_mallocz(ctx, sizeof(*th));
    if (!th)
        return JS_EXCEPTION;
    th->timer_id = ts->next_timer_id++;
    if (ts->next_timer_id > MAX_SAFE_INTEGER)
        ts->next_timer_id = 1;
    th->repeats = (magic > 0);
    th->timeout = js__hrtime_ms() + delay;
    th->delay = delay;
    th->func = JS_DupValue(ctx, func);
    list_add_tail(&th->link, &ts->os_timers);
    return JS_NewInt64(ctx, th->timer_id);
}

static JSOSTimer *find_timer_by_id(JSThreadState *ts, int timer_id)
{
    struct list_head *el;
    if (timer_id <= 0)
        return NULL;
    list_for_each(el, &ts->os_timers) {
        JSOSTimer *th = list_entry(el, JSOSTimer, link);
        if (th->timer_id == timer_id)
            return th;
    }
    return NULL;
}

static JSValue js_os_clearTimeout(JSContext *ctx, JSValueConst this_val,
                                  int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSOSTimer *th;
    int64_t timer_id;

    if (JS_ToInt64(ctx, &timer_id, argv[0]))
        return JS_EXCEPTION;
    th = find_timer_by_id(ts, timer_id);
    if (!th)
        return JS_UNDEFINED;
    free_timer(rt, th);
    return JS_UNDEFINED;
}

/* return a promise */
static JSValue js_os_sleepAsync(JSContext *ctx, JSValueConst this_val,
                                int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    int64_t delay;
    JSOSTimer *th;
    JSValue promise, resolving_funcs[2];

    if (JS_ToInt64(ctx, &delay, argv[0]))
        return JS_EXCEPTION;
    promise = JS_NewPromiseCapability(ctx, resolving_funcs);
    if (JS_IsException(promise))
        return JS_EXCEPTION;

    th = js_mallocz(ctx, sizeof(*th));
    if (!th) {
        JS_FreeValue(ctx, promise);
        JS_FreeValue(ctx, resolving_funcs[0]);
        JS_FreeValue(ctx, resolving_funcs[1]);
        return JS_EXCEPTION;
    }
    th->timer_id = -1;
    th->timeout = js__hrtime_ms() + delay;
    th->func = JS_DupValue(ctx, resolving_funcs[0]);
    list_add_tail(&th->link, &ts->os_timers);
    JS_FreeValue(ctx, resolving_funcs[0]);
    JS_FreeValue(ctx, resolving_funcs[1]);
    return promise;
}

static int call_handler(JSContext *ctx, JSValue func)
{
    int r;
    JSValue ret, func1;
    /* 'func' might be destroyed when calling itself (if it frees the
       handler), so must take extra care */
    func1 = JS_DupValue(ctx, func);
    ret = JS_Call(ctx, func1, JS_UNDEFINED, 0, NULL);
    JS_FreeValue(ctx, func1);
    r = 0;
    if (JS_IsException(ret))
        r = -1;
    JS_FreeValue(ctx, ret);
    return r;
}

static int js_os_run_timers(JSRuntime *rt, JSContext *ctx, JSThreadState *ts, int *min_delay)
{
    JSValue func;
    JSOSTimer *th;
    int64_t cur_time, delay;
    struct list_head *el;
    int r;

    if (list_empty(&ts->os_timers)) {
        *min_delay = -1;
        return 0;
    }

    cur_time = js__hrtime_ms();
    *min_delay = INT32_MAX;

    list_for_each(el, &ts->os_timers) {
        th = list_entry(el, JSOSTimer, link);
        delay = th->timeout - cur_time;
        if (delay > 0) {
            *min_delay = min_int(*min_delay, delay);
        } else {
            *min_delay = 0;
            func = JS_DupValueRT(rt, th->func);
            if (th->repeats)
                th->timeout = cur_time + th->delay;
            else
                free_timer(rt, th);
            r = call_handler(ctx, func);
            JS_FreeValueRT(rt, func);
            return r;
        }
    }

    return 0;
}

#ifdef USE_WORKER

#ifdef _WIN32

static int js_waker_init(JSWaker *w)
{
    w->handle = CreateEvent(NULL, TRUE, FALSE, NULL);
    return w->handle ? 0 : -1;
}

static void js_waker_signal(JSWaker *w)
{
    SetEvent(w->handle);
}

static void js_waker_clear(JSWaker *w)
{
    ResetEvent(w->handle);
}

static void js_waker_close(JSWaker *w)
{
    CloseHandle(w->handle);
    w->handle = INVALID_HANDLE_VALUE;
}

#else // !_WIN32

static int js_waker_init(JSWaker *w)
{
    int fds[2];

    if (pipe(fds) < 0)
        return -1;
    w->read_fd = fds[0];
    w->write_fd = fds[1];
    return 0;
}

static void js_waker_signal(JSWaker *w)
{
    int ret;

    for(;;) {
        ret = write(w->write_fd, "", 1);
        if (ret == 1)
            break;
        if (ret < 0 && errno != EAGAIN && errno != EINTR)
            break;
    }
}

static void js_waker_clear(JSWaker *w)
{
    uint8_t buf[16];
    int ret;

    for(;;) {
        ret = read(w->read_fd, buf, sizeof(buf));
        if (ret >= 0)
            break;
        if (errno != EAGAIN && errno != EINTR)
            break;
    }
}

static void js_waker_close(JSWaker *w)
{
    close(w->read_fd);
    close(w->write_fd);
    w->read_fd = -1;
    w->write_fd = -1;
}

#endif // _WIN32

static void js_free_message(JSWorkerMessage *msg);

/* return 1 if a message was handled, 0 if no message */
static int handle_posted_message(JSRuntime *rt, JSContext *ctx,
                                 JSWorkerMessageHandler *port)
{
    JSWorkerMessagePipe *ps = port->recv_pipe;
    int ret;
    struct list_head *el;
    JSWorkerMessage *msg;
    JSValue obj, data_obj, func, retval;

    js_mutex_lock(&ps->mutex);
    if (!list_empty(&ps->msg_queue)) {
        el = ps->msg_queue.next;
        msg = list_entry(el, JSWorkerMessage, link);

        /* remove the message from the queue */
        list_del(&msg->link);

        // drain read end of pipe
        if (list_empty(&ps->msg_queue))
            js_waker_clear(&ps->waker);

        js_mutex_unlock(&ps->mutex);

        data_obj = JS_ReadObject(ctx, msg->data, msg->data_len,
                                 JS_READ_OBJ_SAB | JS_READ_OBJ_REFERENCE);

        js_free_message(msg);

        if (JS_IsException(data_obj))
            goto fail;
        obj = JS_NewObject(ctx);
        if (JS_IsException(obj)) {
            JS_FreeValue(ctx, data_obj);
            goto fail;
        }
        JS_DefinePropertyValueStr(ctx, obj, "data", data_obj, JS_PROP_C_W_E);

        /* 'func' might be destroyed when calling itself (if it frees the
           handler), so must take extra care */
        func = JS_DupValue(ctx, port->on_message_func);
        retval = JS_Call(ctx, func, JS_UNDEFINED, 1, (JSValueConst *)&obj);
        JS_FreeValue(ctx, obj);
        JS_FreeValue(ctx, func);
        if (JS_IsException(retval)) {
        fail:
            js_std_dump_error(ctx);
        } else {
            JS_FreeValue(ctx, retval);
        }
        ret = 1;
    } else {
        js_mutex_unlock(&ps->mutex);
        ret = 0;
    }
    return ret;
}

#endif // USE_WORKER

/* flags for js_os_poll_internal */
#define JS_OS_POLL_RUN_TIMERS  (1 << 0)
#define JS_OS_POLL_WORKERS     (1 << 1)
#define JS_OS_POLL_SIGNALS     (1 << 2)

#if defined(_WIN32)
static int js_os_poll_internal(JSContext *ctx, int timeout_ms, int flags)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    int min_delay, count;
    JSOSRWHandler *rh;
    struct list_head *el;
    HANDLE handles[MAXIMUM_WAIT_OBJECTS]; // 64

    /* XXX: handle signals if useful */

    min_delay = timeout_ms;

    if (flags & JS_OS_POLL_RUN_TIMERS) {
        if (js_os_run_timers(rt, ctx, ts, &min_delay))
            return -1;
        if (min_delay == 0)
            return 0; // expired timer
        if (min_delay < 0)
            if (list_empty(&ts->os_rw_handlers) && list_empty(&ts->port_list))
                return -1; /* no more events */
    }

    count = 0;
    list_for_each(el, &ts->os_rw_handlers) {
        rh = list_entry(el, JSOSRWHandler, link);
        if (rh->fd == 0 && !JS_IsNull(rh->rw_func[0]))
            handles[count++] = (HANDLE)_get_osfhandle(rh->fd); // stdin
        if (count == (int)countof(handles))
            break;
    }

    if (flags & JS_OS_POLL_WORKERS) {
        list_for_each(el, &ts->port_list) {
            JSWorkerMessageHandler *port = list_entry(el, JSWorkerMessageHandler, link);
            if (JS_IsNull(port->on_message_func))
                continue;
            handles[count++] = port->recv_pipe->waker.handle;
            if (count == (int)countof(handles))
                break;
        }
    }

    if (count > 0) {
        DWORD ret, timeout = INFINITE;
        if (min_delay != -1)
            timeout = min_delay;
        ret = WaitForMultipleObjects(count, handles, FALSE, timeout);
        if (ret < (DWORD)count) {
            list_for_each(el, &ts->os_rw_handlers) {
                rh = list_entry(el, JSOSRWHandler, link);
                if (rh->fd == 0 && !JS_IsNull(rh->rw_func[0])) {
                    return call_handler(ctx, rh->rw_func[0]);
                    /* must stop because the list may have been modified */
                }
            }

            if (flags & JS_OS_POLL_WORKERS) {
                list_for_each(el, &ts->port_list) {
                    JSWorkerMessageHandler *port = list_entry(el, JSWorkerMessageHandler, link);
                    if (!JS_IsNull(port->on_message_func)) {
                        JSWorkerMessagePipe *ps = port->recv_pipe;
                        if (ps->waker.handle == handles[ret]) {
                            if (handle_posted_message(rt, ctx, port))
                                goto done;
                        }
                    }
                }
            }
        }
    } else if (min_delay > 0) {
        Sleep(min_delay);
    }
done:
    return 0;
}

static int js_os_poll(JSContext *ctx)
{
    return js_os_poll_internal(ctx, -1,
        JS_OS_POLL_RUN_TIMERS | JS_OS_POLL_WORKERS | JS_OS_POLL_SIGNALS);
}

int js_std_poll_io(JSContext *ctx, int timeout_ms)
{
    int ret = js_os_poll_internal(ctx, timeout_ms, 0);
    /* map return codes: -1 on error stays -1, negative from handler becomes -2 */
    if (ret < -1)
        return -2;
    return ret;
}
#else // !defined(_WIN32)
static int js_os_poll_internal(JSContext *ctx, int timeout_ms, int flags)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    int r, w, ret, nfds, min_delay, poll_nfds;
    JSOSRWHandler *rh;
    struct list_head *el;
    struct pollfd *pfd, *pfds, pfds_local[64];

    min_delay = timeout_ms;

    /* only check signals in the main thread */
    if ((flags & JS_OS_POLL_SIGNALS) &&
        !ts->recv_pipe &&
        unlikely(os_pending_signals != 0)) {
        JSOSSignalHandler *sh;
        uint64_t mask;

        list_for_each(el, &ts->os_signal_handlers) {
            sh = list_entry(el, JSOSSignalHandler, link);
            mask = (uint64_t)1 << sh->sig_num;
            if (os_pending_signals & mask) {
                os_pending_signals &= ~mask;
                return call_handler(ctx, sh->func);
            }
        }
    }

    if (flags & JS_OS_POLL_RUN_TIMERS) {
        if (js_os_run_timers(rt, ctx, ts, &min_delay))
            return -1;
        if (min_delay == 0)
            return 0; // expired timer
        if (min_delay < 0)
            if (list_empty(&ts->os_rw_handlers) && list_empty(&ts->port_list))
                return -1; /* no more events */
    }

    nfds = 0;
    list_for_each(el, &ts->os_rw_handlers) {
        rh = list_entry(el, JSOSRWHandler, link);
        nfds += (!JS_IsNull(rh->rw_func[0]) || !JS_IsNull(rh->rw_func[1]));
    }

#ifdef USE_WORKER
    if (flags & JS_OS_POLL_WORKERS) {
        list_for_each(el, &ts->port_list) {
            JSWorkerMessageHandler *port = list_entry(el, JSWorkerMessageHandler, link);
            nfds += !JS_IsNull(port->on_message_func);
        }
    }
#endif // USE_WORKER

    if (nfds == 0) {
        if (min_delay > 0) {
            struct timespec ts_sleep = {
                .tv_sec = min_delay / 1000,
                .tv_nsec = (min_delay % 1000) * 1000000L
            };
            uint64_t mask = os_pending_signals;
            while (nanosleep(&ts_sleep, &ts_sleep)
                && errno == EINTR
                && mask == os_pending_signals);
        }
        return 0;
    }

    pfd = pfds = pfds_local;
    if (nfds > (int)countof(pfds_local)) {
        pfd = pfds = js_malloc(ctx, nfds * sizeof(*pfd));
        if (!pfd)
            return -1;
    }

    list_for_each(el, &ts->os_rw_handlers) {
        rh = list_entry(el, JSOSRWHandler, link);
        r = POLLIN * !JS_IsNull(rh->rw_func[0]);
        w = POLLOUT * !JS_IsNull(rh->rw_func[1]);
        if (r || w)
            *pfd++ = (struct pollfd){rh->fd, r|w, 0};
    }

#ifdef USE_WORKER
    if (flags & JS_OS_POLL_WORKERS) {
        list_for_each(el, &ts->port_list) {
            JSWorkerMessageHandler *port = list_entry(el, JSWorkerMessageHandler, link);
            if (!JS_IsNull(port->on_message_func)) {
                JSWorkerMessagePipe *ps = port->recv_pipe;
                *pfd++ = (struct pollfd){ps->waker.read_fd, POLLIN, 0};
            }
        }
    }
#endif // USE_WORKER

    // FIXME(bnoordhuis) the loop below is quadratic in theory but
    // linear-ish in practice because we bail out on the first hit,
    // i.e., it's probably good enough for now
    ret = 0;
    /* poll() returns the number of ready descriptors, not the array length. */
    poll_nfds = nfds;
    nfds = poll(pfds, nfds, min_delay);
    if (nfds < 0) {
        ret = -1;
        goto done;
    }
    for (pfd = pfds; poll_nfds-- > 0; pfd++) {
        rh = find_rh(ts, pfd->fd);
        if (rh) {
            r = (POLLERR|POLLHUP|POLLNVAL|POLLIN) * !JS_IsNull(rh->rw_func[0]);
            w = (POLLERR|POLLHUP|POLLNVAL|POLLOUT) * !JS_IsNull(rh->rw_func[1]);
            if (r & pfd->revents) {
                ret = call_handler(ctx, rh->rw_func[0]);
                goto done;
                /* must stop because the list may have been modified */
            }
            if (w & pfd->revents) {
                ret = call_handler(ctx, rh->rw_func[1]);
                goto done;
                /* must stop because the list may have been modified */
            }
        }
#ifdef USE_WORKER
        else if (flags & JS_OS_POLL_WORKERS) {
            list_for_each(el, &ts->port_list) {
                JSWorkerMessageHandler *port = list_entry(el, JSWorkerMessageHandler, link);
                if (!JS_IsNull(port->on_message_func)) {
                    JSWorkerMessagePipe *ps = port->recv_pipe;
                    if (pfd->fd == ps->waker.read_fd) {
                        if (handle_posted_message(rt, ctx, port))
                            goto done;
                    }
                }
            }
        }
#endif // USE_WORKER
    }
done:
    if (pfds != pfds_local)
        js_free(ctx, pfds);
    return ret;
}

static int js_os_poll(JSContext *ctx)
{
    return js_os_poll_internal(ctx, -1,
        JS_OS_POLL_RUN_TIMERS | JS_OS_POLL_WORKERS | JS_OS_POLL_SIGNALS);
}

int js_std_poll_io(JSContext *ctx, int timeout_ms)
{
    int ret = js_os_poll_internal(ctx, timeout_ms, 0);
    /* map return codes: -1 on error stays -1, negative from handler becomes -2 */
    if (ret < -1)
        return -2;
    return ret;
}
#endif // defined(_WIN32)

static JSValue make_obj_error(JSContext *ctx,
                              JSValue obj,
                              int err)
{
    JSValue vals[2];

    if (JS_IsException(obj))
        return obj;
    vals[0] = obj;
    vals[1] = JS_NewInt32(ctx, err);
    return JS_NewArrayFrom(ctx, countof(vals), vals);
}

static JSValue make_string_error(JSContext *ctx,
                                 const char *buf,
                                 int err)
{
    return make_obj_error(ctx, JS_NewString(ctx, buf), err);
}

/* return [cwd, errorcode] */
static JSValue js_os_getcwd(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    char buf[JS__PATH_MAX];
    int err;

    if (!getcwd(buf, sizeof(buf))) {
        buf[0] = '\0';
        err = errno;
    } else {
        err = 0;
    }
    return make_string_error(ctx, buf, err);
}

static JSValue js_os_chdir(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv)
{
    const char *target;
    int err;

    target = JS_ToCString(ctx, argv[0]);
    if (!target)
        return JS_EXCEPTION;
    err = js_get_errno(chdir(target));
    JS_FreeCString(ctx, target);
    return JS_NewInt32(ctx, err);
}

static JSValue js_os_mkdir(JSContext *ctx, JSValueConst this_val,
                           int argc, JSValueConst *argv)
{
    int mode, ret;
    const char *path;

    if (argc >= 2) {
        if (JS_ToInt32(ctx, &mode, argv[1]))
            return JS_EXCEPTION;
    } else {
        mode = 0777;
    }
    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
#if defined(_WIN32)
    (void)mode;
    ret = js_get_errno(mkdir(path));
#else
    ret = js_get_errno(mkdir(path, mode));
#endif
    JS_FreeCString(ctx, path);
    return JS_NewInt32(ctx, ret);
}

/* return [array, errorcode] */
static JSValue js_os_readdir(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
#ifdef _WIN32
    const char *path;
    JSValue obj;
    int err;
    uint32_t len;
    HANDLE h;
    WIN32_FIND_DATAA d;
    char s[1024];

    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
    obj = JS_NewArray(ctx);
    if (JS_IsException(obj)) {
        JS_FreeCString(ctx, path);
        return JS_EXCEPTION;
    }
    snprintf(s, sizeof(s), "%s/*", path);
    JS_FreeCString(ctx, path);
    err = 0;
    h = FindFirstFileA(s, &d);
    if (h == INVALID_HANDLE_VALUE)
        err = GetLastError();
    if (err)
        goto done;
    JS_DefinePropertyValueUint32(ctx, obj, 0, JS_NewString(ctx, "."),
                                 JS_PROP_C_W_E);
    for (len = 1; FindNextFileA(h, &d); len++) {
        JS_DefinePropertyValueUint32(ctx, obj, len,
                                     JS_NewString(ctx, d.cFileName),
                                     JS_PROP_C_W_E);
    }
    FindClose(h);
done:
    return make_obj_error(ctx, obj, err);
#else
    const char *path;
    DIR *f;
    struct dirent *d;
    JSValue obj;
    int err;
    uint32_t len;

    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
    obj = JS_NewArray(ctx);
    if (JS_IsException(obj)) {
        JS_FreeCString(ctx, path);
        return JS_EXCEPTION;
    }
    f = opendir(path);
    if (!f)
        err = errno;
    else
        err = 0;
    JS_FreeCString(ctx, path);
    if (!f)
        goto done;
    len = 0;
    for(;;) {
        errno = 0;
        d = readdir(f);
        if (!d) {
            err = errno;
            break;
        }
        JS_DefinePropertyValueUint32(ctx, obj, len++,
                                     JS_NewString(ctx, d->d_name),
                                     JS_PROP_C_W_E);
    }
    closedir(f);
 done:
    return make_obj_error(ctx, obj, err);
#endif
}

#if !defined(_WIN32) && !defined(__wasi__)
#define PAT "XXXXXX"
#define PSZ (sizeof(PAT)-1)
static JSValue js_os_mkdstemp(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv, int magic)
{
    char pat_s[32] = "tmp"PAT, *pat = pat_s;
    const char *s;
    size_t k, n;
    JSValue val;
    int err;

    if (argc > 0) {
        s = JS_ToCStringLen(ctx, &n, argv[0]);
        if (!s)
            return JS_EXCEPTION;
        k = n;
        if (n < PSZ || memcmp(&s[n-PSZ], PAT, PSZ))
            k += PSZ;
        if (k >= sizeof(pat_s))
            pat = js_malloc(ctx, k+1);
        if (pat) {
            memcpy(pat, s, n);
            if (n < k)
                memcpy(&pat[n], PAT, PSZ);
            pat[k] = '\0';
        }
        JS_FreeCString(ctx, s);
        if (!pat)
            return JS_EXCEPTION;
    }
    if (magic == 'd') {
        err = 0;
        if (!mkdtemp(pat))
            err = -errno;
    } else {
        err = js_get_errno(mkstemp(pat));
    }
    val = JS_NewString(ctx, pat);
    if (pat != pat_s)
        js_free(ctx, pat);
    return make_obj_error(ctx, val, err);
}
#undef PSZ
#undef PAT
#endif // !defined(_WIN32) && !defined(__wasi__)

#if !defined(_WIN32)
static int64_t timespec_to_ms(const struct timespec *tv)
{
    return (int64_t)tv->tv_sec * 1000 + (tv->tv_nsec / 1000000);
}
#endif

/* return [obj, errcode] */
static JSValue js_os_stat(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv, int is_lstat)
{
    const char *path;
    int err, res;
    struct stat st;
    JSValue obj;

    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
#if defined(_WIN32)
    res = stat(path, &st);
#else
    if (is_lstat)
        res = lstat(path, &st);
    else
        res = stat(path, &st);
#endif
    err = (res < 0) ? errno : 0;
    JS_FreeCString(ctx, path);
    if (res < 0) {
        obj = JS_NULL;
    } else {
        obj = JS_NewObject(ctx);
        if (JS_IsException(obj))
            return JS_EXCEPTION;
        JS_DefinePropertyValueStr(ctx, obj, "dev",
                                  JS_NewInt64(ctx, st.st_dev),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "ino",
                                  JS_NewInt64(ctx, st.st_ino),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "mode",
                                  JS_NewInt32(ctx, st.st_mode),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "nlink",
                                  JS_NewInt64(ctx, st.st_nlink),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "uid",
                                  JS_NewInt64(ctx, st.st_uid),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "gid",
                                  JS_NewInt64(ctx, st.st_gid),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "rdev",
                                  JS_NewInt64(ctx, st.st_rdev),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "size",
                                  JS_NewInt64(ctx, st.st_size),
                                  JS_PROP_C_W_E);
#if !defined(_WIN32)
        JS_DefinePropertyValueStr(ctx, obj, "blocks",
                                  JS_NewInt64(ctx, st.st_blocks),
                                  JS_PROP_C_W_E);
#endif
#if defined(_WIN32)
        JS_DefinePropertyValueStr(ctx, obj, "atime",
                                  JS_NewInt64(ctx, (int64_t)st.st_atime * 1000),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "mtime",
                                  JS_NewInt64(ctx, (int64_t)st.st_mtime * 1000),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "ctime",
                                  JS_NewInt64(ctx, (int64_t)st.st_ctime * 1000),
                                  JS_PROP_C_W_E);
#elif defined(__APPLE__)
        JS_DefinePropertyValueStr(ctx, obj, "atime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_atimespec)),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "mtime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_mtimespec)),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "ctime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_ctimespec)),
                                  JS_PROP_C_W_E);
#else
        JS_DefinePropertyValueStr(ctx, obj, "atime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_atim)),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "mtime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_mtim)),
                                  JS_PROP_C_W_E);
        JS_DefinePropertyValueStr(ctx, obj, "ctime",
                                  JS_NewInt64(ctx, timespec_to_ms(&st.st_ctim)),
                                  JS_PROP_C_W_E);
#endif
    }
    return make_obj_error(ctx, obj, err);
}

#if !defined(_WIN32)
static void ms_to_timeval(struct timeval *tv, uint64_t v)
{
    tv->tv_sec = v / 1000;
    tv->tv_usec = (v % 1000) * 1000;
}
#endif

static JSValue js_os_utimes(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    const char *path;
    int64_t atime, mtime;
    int ret;

    if (JS_ToInt64(ctx, &atime, argv[1]))
        return JS_EXCEPTION;
    if (JS_ToInt64(ctx, &mtime, argv[2]))
        return JS_EXCEPTION;
    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
#if defined(_WIN32)
    {
        struct _utimbuf times;
        times.actime = atime / 1000;
        times.modtime = mtime / 1000;
        ret = js_get_errno(_utime(path, &times));
    }
#else
    {
        struct timeval times[2];
        ms_to_timeval(&times[0], atime);
        ms_to_timeval(&times[1], mtime);
        ret = js_get_errno(utimes(path, times));
    }
#endif
    JS_FreeCString(ctx, path);
    return JS_NewInt32(ctx, ret);
}

/* sleep(delay_ms) */
static JSValue js_os_sleep(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    int64_t delay;
    int ret;

    if (JS_ToInt64(ctx, &delay, argv[0]))
        return JS_EXCEPTION;
    if (delay < 0)
        delay = 0;
#if defined(_WIN32)
    {
        if (delay > INT32_MAX)
            delay = INT32_MAX;
        Sleep(delay);
        ret = 0;
    }
#else
    {
        struct timespec ts;

        ts.tv_sec = delay / 1000;
        ts.tv_nsec = (delay % 1000) * 1000000;
        ret = js_get_errno(nanosleep(&ts, NULL));
    }
#endif
    return JS_NewInt32(ctx, ret);
}

#if defined(_WIN32)
static char *realpath(const char *path, char *buf)
{
    if (!_fullpath(buf, path, JS__PATH_MAX)) {
        errno = ENOENT;
        return NULL;
    } else {
        return buf;
    }
}
#endif

#if !defined(__wasi__)
/* return [path, errorcode] */
static JSValue js_os_realpath(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    const char *path;
    char buf[JS__PATH_MAX], *res;
    int err;

    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
    res = realpath(path, buf);
    JS_FreeCString(ctx, path);
    if (!res) {
        buf[0] = '\0';
        err = errno;
    } else {
        err = 0;
    }
    return make_string_error(ctx, buf, err);
}
#endif

#if !defined(_WIN32) && !defined(__wasi__) && !(defined(__APPLE__) && (TARGET_OS_TV || TARGET_OS_WATCH))
static JSValue js_os_symlink(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    const char *target, *linkpath;
    int err;

    target = JS_ToCString(ctx, argv[0]);
    if (!target)
        return JS_EXCEPTION;
    linkpath = JS_ToCString(ctx, argv[1]);
    if (!linkpath) {
        JS_FreeCString(ctx, target);
        return JS_EXCEPTION;
    }
    err = js_get_errno(symlink(target, linkpath));
    JS_FreeCString(ctx, target);
    JS_FreeCString(ctx, linkpath);
    return JS_NewInt32(ctx, err);
}

/* return [path, errorcode] */
static JSValue js_os_readlink(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    const char *path;
    char buf[JS__PATH_MAX];
    int err;
    ssize_t res;

    path = JS_ToCString(ctx, argv[0]);
    if (!path)
        return JS_EXCEPTION;
    res = readlink(path, buf, sizeof(buf) - 1);
    if (res < 0) {
        buf[0] = '\0';
        err = errno;
    } else {
        buf[res] = '\0';
        err = 0;
    }
    JS_FreeCString(ctx, path);
    return make_string_error(ctx, buf, err);
}

static char **build_envp(JSContext *ctx, JSValue obj)
{
    uint32_t len, i;
    JSPropertyEnum *tab;
    char **envp, *pair;
    const char *key, *str;
    JSValue val;
    size_t key_len, str_len;

    if (JS_GetOwnPropertyNames(ctx, &tab, &len, obj,
                               JS_GPN_STRING_MASK | JS_GPN_ENUM_ONLY) < 0)
        return NULL;
    envp = js_mallocz(ctx, sizeof(envp[0]) * ((size_t)len + 1));
    if (!envp)
        goto fail;
    for(i = 0; i < len; i++) {
        val = JS_GetProperty(ctx, obj, tab[i].atom);
        if (JS_IsException(val))
            goto fail;
        str = JS_ToCString(ctx, val);
        JS_FreeValue(ctx, val);
        if (!str)
            goto fail;
        key = JS_AtomToCString(ctx, tab[i].atom);
        if (!key) {
            JS_FreeCString(ctx, str);
            goto fail;
        }
        key_len = strlen(key);
        str_len = strlen(str);
        pair = js_malloc(ctx, key_len + str_len + 2);
        if (!pair) {
            JS_FreeCString(ctx, key);
            JS_FreeCString(ctx, str);
            goto fail;
        }
        memcpy(pair, key, key_len);
        pair[key_len] = '=';
        memcpy(pair + key_len + 1, str, str_len);
        pair[key_len + 1 + str_len] = '\0';
        envp[i] = pair;
        JS_FreeCString(ctx, key);
        JS_FreeCString(ctx, str);
    }
 done:
    for(i = 0; i < len; i++)
        JS_FreeAtom(ctx, tab[i].atom);
    js_free(ctx, tab);
    return envp;
 fail:
    if (envp) {
        for(i = 0; i < len; i++)
            js_free(ctx, envp[i]);
        js_free(ctx, envp);
        envp = NULL;
    }
    goto done;
}

/* execvpe is not available on non GNU systems */
static int my_execvpe(const char *filename, char **argv, char **envp)
{
    char *path, *p, *p_next, *p1;
    char buf[JS__PATH_MAX];
    size_t filename_len, path_len;
    bool eacces_error;

    filename_len = strlen(filename);
    if (filename_len == 0) {
        errno = ENOENT;
        return -1;
    }
    if (strchr(filename, '/'))
        return execve(filename, argv, envp);

    path = getenv("PATH");
    if (!path)
        path = (char *)"/bin:/usr/bin";
    eacces_error = false;
    p = path;
    for(p = path; p != NULL; p = p_next) {
        p1 = strchr(p, ':');
        if (!p1) {
            p_next = NULL;
            path_len = strlen(p);
        } else {
            p_next = p1 + 1;
            path_len = p1 - p;
        }
        /* path too long */
        if ((path_len + 1 + filename_len + 1) > JS__PATH_MAX)
            continue;
        memcpy(buf, p, path_len);
        buf[path_len] = '/';
        memcpy(buf + path_len + 1, filename, filename_len);
        buf[path_len + 1 + filename_len] = '\0';

        execve(buf, argv, envp);

        switch(errno) {
        case EACCES:
            eacces_error = true;
            break;
        case ENOENT:
        case ENOTDIR:
            break;
        default:
            return -1;
        }
    }
    if (eacces_error)
        errno = EACCES;
    return -1;
}

static void (*js_os_exec_closefrom)(int);

#if !defined(EMSCRIPTEN) && !defined(__wasi__)

static js_once_t js_os_exec_once = JS_ONCE_INIT;

static void js_os_exec_once_init(void)
{
     *(void **) (&js_os_exec_closefrom) = dlsym(RTLD_DEFAULT, "closefrom");
}

#endif

/* exec(args[, options]) -> exitcode */
static JSValue js_os_exec(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    JSValueConst options, args = argv[0];
    JSValue val, ret_val;
    const char **exec_argv, *file = NULL, *str, *cwd = NULL;
    char **envp = environ;
    uint32_t exec_argc, i;
    int ret, pid, status;
    bool block_flag = true, use_path = true;
    static const char *std_name[3] = { "stdin", "stdout", "stderr" };
    int std_fds[3];
    uint32_t uid = -1, gid = -1;
    int ngroups = -1;
    gid_t groups[64];

    val = JS_GetPropertyStr(ctx, args, "length");
    if (JS_IsException(val))
        return JS_EXCEPTION;
    ret = JS_ToUint32(ctx, &exec_argc, val);
    JS_FreeValue(ctx, val);
    if (ret)
        return JS_EXCEPTION;
    /* arbitrary limit to avoid overflow */
    if (exec_argc < 1 || exec_argc > 65535) {
        return JS_ThrowTypeError(ctx, "invalid number of arguments");
    }
    exec_argv = js_mallocz(ctx, sizeof(exec_argv[0]) * (exec_argc + 1));
    if (!exec_argv)
        return JS_EXCEPTION;
    for(i = 0; i < exec_argc; i++) {
        val = JS_GetPropertyUint32(ctx, args, i);
        if (JS_IsException(val))
            goto exception;
        str = JS_ToCString(ctx, val);
        JS_FreeValue(ctx, val);
        if (!str)
            goto exception;
        exec_argv[i] = str;
    }
    exec_argv[exec_argc] = NULL;

    for(i = 0; i < 3; i++)
        std_fds[i] = i;

    /* get the options, if any */
    if (argc >= 2) {
        options = argv[1];

        if (get_bool_option(ctx, &block_flag, options, "block"))
            goto exception;
        if (get_bool_option(ctx, &use_path, options, "usePath"))
            goto exception;

        val = JS_GetPropertyStr(ctx, options, "file");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            file = JS_ToCString(ctx, val);
            JS_FreeValue(ctx, val);
            if (!file)
                goto exception;
        }

        val = JS_GetPropertyStr(ctx, options, "cwd");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            cwd = JS_ToCString(ctx, val);
            JS_FreeValue(ctx, val);
            if (!cwd)
                goto exception;
        }

        /* stdin/stdout/stderr handles */
        for(i = 0; i < 3; i++) {
            val = JS_GetPropertyStr(ctx, options, std_name[i]);
            if (JS_IsException(val))
                goto exception;
            if (!JS_IsUndefined(val)) {
                int fd;
                ret = JS_ToInt32(ctx, &fd, val);
                JS_FreeValue(ctx, val);
                if (ret)
                    goto exception;
                std_fds[i] = fd;
            }
        }

        val = JS_GetPropertyStr(ctx, options, "env");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            envp = build_envp(ctx, val);
            JS_FreeValue(ctx, val);
            if (!envp)
                goto exception;
        }

        val = JS_GetPropertyStr(ctx, options, "uid");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            ret = JS_ToUint32(ctx, &uid, val);
            JS_FreeValue(ctx, val);
            if (ret)
                goto exception;
        }

        val = JS_GetPropertyStr(ctx, options, "gid");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            ret = JS_ToUint32(ctx, &gid, val);
            JS_FreeValue(ctx, val);
            if (ret)
                goto exception;
        }

        val = JS_GetPropertyStr(ctx, options, "groups");
        if (JS_IsException(val))
            goto exception;
        if (!JS_IsUndefined(val)) {
            int64_t idx, len;
            JSValue prop;
            uint32_t id;
            ngroups = 0;
            if (JS_GetLength(ctx, val, &len)) {
                JS_FreeValue(ctx, val);
                goto exception;
            }
            for (idx = 0; idx < len; idx++) {
                prop = JS_GetPropertyInt64(ctx, val, idx);
                if (JS_IsException(prop))
                    break;
                if (JS_IsUndefined(prop))
                    continue;
                ret = JS_ToUint32(ctx, &id, prop);
                JS_FreeValue(ctx, prop);
                if (ret)
                    break;
                if (ngroups == countof(groups)) {
                    JS_ThrowRangeError(ctx, "too many groups");
                    break;
                }
                groups[ngroups++] = id;
            }
            JS_FreeValue(ctx, val);
            if (idx < len)
                goto exception;
        }

    }

#if !defined(EMSCRIPTEN) && !defined(__wasi__)
    // should happen pre-fork because it calls dlsym()
    // and that's not an async-signal-safe function
    js_once(&js_os_exec_once, js_os_exec_once_init);
#endif

    pid = fork();
    if (pid < 0) {
        JS_ThrowTypeError(ctx, "fork error");
        goto exception;
    }
    if (pid == 0) {
        /* child */
        /* remap the stdin/stdout/stderr handles if necessary */
        for(i = 0; i < 3; i++) {
            if (std_fds[i] != i) {
                if (dup2(std_fds[i], i) < 0)
                    _exit(127);
            }
        }

        if (js_os_exec_closefrom) {
            js_os_exec_closefrom(3);
        } else {
            int fd_max = sysconf(_SC_OPEN_MAX);
            for(i = 3; i < fd_max; i++)
                close(i);
        }

        if (cwd) {
            if (chdir(cwd) < 0)
                _exit(127);
        }
        if (ngroups != -1) {
            if (setgroups(ngroups, groups) < 0)
                _exit(127);
        }
        if (uid != -1) {
            if (setuid(uid) < 0)
                _exit(127);
        }
        if (gid != -1) {
            if (setgid(gid) < 0)
                _exit(127);
        }

        if (!file)
            file = exec_argv[0];
        if (use_path)
            ret = my_execvpe(file, (char **)exec_argv, envp);
        else
            ret = execve(file, (char **)exec_argv, envp);
        _exit(127);
    }
    /* parent */
    if (block_flag) {
        for(;;) {
            ret = waitpid(pid, &status, 0);
            if (ret == pid) {
                if (WIFEXITED(status)) {
                    ret = WEXITSTATUS(status);
                    break;
                } else if (WIFSIGNALED(status)) {
                    ret = -WTERMSIG(status);
                    break;
                }
            }
        }
    } else {
        ret = pid;
    }
    ret_val = JS_NewInt32(ctx, ret);
 done:
    JS_FreeCString(ctx, file);
    JS_FreeCString(ctx, cwd);
    for(i = 0; i < exec_argc; i++)
        JS_FreeCString(ctx, exec_argv[i]);
    js_free(ctx, exec_argv);
    if (envp != environ) {
        char **p;
        p = envp;
        while (*p != NULL) {
            js_free(ctx, *p);
            p++;
        }
        js_free(ctx, envp);
    }
    return ret_val;
 exception:
    ret_val = JS_EXCEPTION;
    goto done;
}

/* getpid() -> pid */
static JSValue js_os_getpid(JSContext *ctx, JSValueConst this_val,
                            int argc, JSValueConst *argv)
{
    return JS_NewInt32(ctx, getpid());
}

/* waitpid(pid, block) -> [pid, status] */
static JSValue js_os_waitpid(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    int pid, status, options, ret;
    JSValue obj;

    if (JS_ToInt32(ctx, &pid, argv[0]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &options, argv[1]))
        return JS_EXCEPTION;

    ret = waitpid(pid, &status, options);
    if (ret < 0) {
        ret = -errno;
        status = 0;
    }

    obj = JS_NewArray(ctx);
    if (JS_IsException(obj))
        return obj;
    JS_DefinePropertyValueUint32(ctx, obj, 0, JS_NewInt32(ctx, ret),
                                 JS_PROP_C_W_E);
    JS_DefinePropertyValueUint32(ctx, obj, 1, JS_NewInt32(ctx, status),
                                 JS_PROP_C_W_E);
    return obj;
}

/* pipe() -> [read_fd, write_fd] or null if error */
static JSValue js_os_pipe(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    int pipe_fds[2], ret;
    JSValue obj;

    ret = pipe(pipe_fds);
    if (ret < 0)
        return JS_NULL;
    obj = JS_NewArray(ctx);
    if (JS_IsException(obj))
        return obj;
    JS_DefinePropertyValueUint32(ctx, obj, 0, JS_NewInt32(ctx, pipe_fds[0]),
                                 JS_PROP_C_W_E);
    JS_DefinePropertyValueUint32(ctx, obj, 1, JS_NewInt32(ctx, pipe_fds[1]),
                                 JS_PROP_C_W_E);
    return obj;
}

/* kill(pid, sig) */
static JSValue js_os_kill(JSContext *ctx, JSValueConst this_val,
                          int argc, JSValueConst *argv)
{
    int pid, sig, ret;

    if (JS_ToInt32(ctx, &pid, argv[0]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &sig, argv[1]))
        return JS_EXCEPTION;
    ret = js_get_errno(kill(pid, sig));
    return JS_NewInt32(ctx, ret);
}

/* dup(fd) */
static JSValue js_os_dup(JSContext *ctx, JSValueConst this_val,
                         int argc, JSValueConst *argv)
{
    int fd, ret;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    ret = js_get_errno(dup(fd));
    return JS_NewInt32(ctx, ret);
}

/* dup2(fd) */
static JSValue js_os_dup2(JSContext *ctx, JSValueConst this_val,
                         int argc, JSValueConst *argv)
{
    int fd, fd2, ret;

    if (JS_ToInt32(ctx, &fd, argv[0]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &fd2, argv[1]))
        return JS_EXCEPTION;
    ret = js_get_errno(dup2(fd, fd2));
    return JS_NewInt32(ctx, ret);
}

#endif /* !_WIN32 */

#ifdef USE_WORKER

/* Worker */

typedef struct {
    JSWorkerMessagePipe *recv_pipe;
    JSWorkerMessagePipe *send_pipe;
    JSWorkerMessageHandler *msg_handler;
} JSWorkerData;

typedef struct {
    char *filename; /* module filename */
    char *basename; /* module base name */
    JSWorkerMessagePipe *recv_pipe, *send_pipe;
} WorkerFuncArgs;

typedef struct {
    int ref_count;
    uint64_t buf[];
} JSSABHeader;

static JSRuntime *(*js_worker_new_runtime_func)(void);
static JSContext *(*js_worker_new_context_func)(JSRuntime *rt);

static int atomic_add_int(int *ptr, int v)
{
    return atomic_fetch_add((_Atomic uint32_t*)ptr, v) + v;
}

/* shared array buffer allocator */
static void *js_sab_alloc(void *opaque, size_t size)
{
    JSSABHeader *sab;
    sab = malloc(sizeof(JSSABHeader) + size);
    if (!sab)
        return NULL;
    sab->ref_count = 1;
    return sab->buf;
}

static void js_sab_free(void *opaque, void *ptr)
{
    JSSABHeader *sab;
    int ref_count;
    sab = (JSSABHeader *)((uint8_t *)ptr - sizeof(JSSABHeader));
    ref_count = atomic_add_int(&sab->ref_count, -1);
    assert(ref_count >= 0);
    if (ref_count == 0) {
        free(sab);
    }
}

static void js_sab_dup(void *opaque, void *ptr)
{
    JSSABHeader *sab;
    sab = (JSSABHeader *)((uint8_t *)ptr - sizeof(JSSABHeader));
    atomic_add_int(&sab->ref_count, 1);
}

static JSWorkerMessagePipe *js_new_message_pipe(void)
{
    JSWorkerMessagePipe *ps;

    ps = malloc(sizeof(*ps));
    if (!ps)
        return NULL;
    if (js_waker_init(&ps->waker)) {
        free(ps);
        return NULL;
    }
    ps->ref_count = 1;
    init_list_head(&ps->msg_queue);
    js_mutex_init(&ps->mutex);
    return ps;
}

static JSWorkerMessagePipe *js_dup_message_pipe(JSWorkerMessagePipe *ps)
{
    atomic_add_int(&ps->ref_count, 1);
    return ps;
}

static void js_free_message(JSWorkerMessage *msg)
{
    size_t i;
    /* free the SAB */
    for(i = 0; i < msg->sab_tab_len; i++) {
        js_sab_free(NULL, msg->sab_tab[i]);
    }
    free(msg->sab_tab);
    free(msg->data);
    free(msg);
}

static void js_free_message_pipe(JSWorkerMessagePipe *ps)
{
    struct list_head *el, *el1;
    JSWorkerMessage *msg;
    int ref_count;

    if (!ps)
        return;

    ref_count = atomic_add_int(&ps->ref_count, -1);
    assert(ref_count >= 0);
    if (ref_count == 0) {
        list_for_each_safe(el, el1, &ps->msg_queue) {
            msg = list_entry(el, JSWorkerMessage, link);
            js_free_message(msg);
        }
        js_mutex_destroy(&ps->mutex);
        js_waker_close(&ps->waker);
        free(ps);
    }
}

static void js_free_port(JSRuntime *rt, JSWorkerMessageHandler *port)
{
    if (port) {
        js_free_message_pipe(port->recv_pipe);
        JS_FreeValueRT(rt, port->on_message_func);
        list_del(&port->link);
        js_free_rt(rt, port);
    }
}

static void js_worker_finalizer(JSRuntime *rt, JSValueConst val)
{
    JSThreadState *ts = js_get_thread_state(rt);
    JSWorkerData *worker = JS_GetOpaque(val, ts->worker_class_id);
    if (worker) {
        js_free_message_pipe(worker->recv_pipe);
        js_free_message_pipe(worker->send_pipe);
        js_free_port(rt, worker->msg_handler);
        js_free_rt(rt, worker);
    }
}

static JSClassDef js_worker_class = {
    "Worker",
    .finalizer = js_worker_finalizer,
};

static void worker_func(void *opaque)
{
    JSRuntime *(*new_runtime_func)(void);
    JSContext *(*new_context_func)(JSRuntime *);
    WorkerFuncArgs *args = opaque;
    JSRuntime *rt;
    JSThreadState *ts;
    JSContext *ctx;
    JSValue val;

    new_runtime_func = js_worker_new_runtime_func;
    if (!new_runtime_func)
        new_runtime_func = JS_NewRuntime;
    rt = new_runtime_func();
    if (rt == NULL) {
        fprintf(stderr, "JS_NewRuntime failure");
        exit(1);
    }
    js_std_init_handlers(rt);

    JS_SetModuleLoaderFunc2(rt, NULL, js_module_loader, js_module_check_attributes, NULL);

    /* set the pipe to communicate with the parent */
    ts = js_get_thread_state(rt);
    ts->recv_pipe = args->recv_pipe;
    ts->send_pipe = args->send_pipe;

    /* function pointer to avoid linking the whole JS_NewContext() if
       not needed */
    new_context_func = js_worker_new_context_func;
    if (!new_context_func)
        new_context_func = JS_NewContext;
    ctx = new_context_func(rt);
    if (ctx == NULL) {
        fprintf(stderr, "JS_NewContext failure");
        exit(1);
    }

    JS_SetCanBlock(rt, true);

    js_std_add_helpers(ctx, -1, NULL);

    val = JS_LoadModule(ctx, args->basename, args->filename);
    free(args->filename);
    free(args->basename);
    free(args);
    val = js_std_await(ctx, val);
    if (JS_IsException(val))
        js_std_dump_error(ctx);
    JS_FreeValue(ctx, val);

    js_std_loop(ctx);

    js_std_free_handlers(rt);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
}

static JSValue js_worker_ctor_internal(JSContext *ctx, JSValueConst new_target,
                                       JSWorkerMessagePipe *recv_pipe,
                                       JSWorkerMessagePipe *send_pipe)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSValue obj = JS_UNDEFINED, proto;
    JSWorkerData *s;

    /* create the object */
    if (JS_IsUndefined(new_target)) {
        proto = JS_GetClassProto(ctx, ts->worker_class_id);
    } else {
        proto = JS_GetPropertyStr(ctx, new_target, "prototype");
        if (JS_IsException(proto))
            goto fail;
    }
    obj = JS_NewObjectProtoClass(ctx, proto, ts->worker_class_id);
    JS_FreeValue(ctx, proto);
    if (JS_IsException(obj))
        goto fail;
    s = js_mallocz(ctx, sizeof(*s));
    if (!s)
        goto fail;
    s->recv_pipe = js_dup_message_pipe(recv_pipe);
    s->send_pipe = js_dup_message_pipe(send_pipe);

    JS_SetOpaque(obj, s);
    return obj;
 fail:
    JS_FreeValue(ctx, obj);
    return JS_EXCEPTION;
}

static JSValue js_worker_ctor(JSContext *ctx, JSValueConst new_target,
                              int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    WorkerFuncArgs *args = NULL;
    js_thread_t thr;
    JSValue obj = JS_UNDEFINED;
    int ret;
    const char *filename = NULL, *basename;
    JSAtom basename_atom;

    /* XXX: in order to avoid problems with resource liberation, we
       don't support creating workers inside workers */
    if (!is_main_thread(rt))
        return JS_ThrowTypeError(ctx, "cannot create a worker inside a worker");

    /* base name, assuming the calling function is a normal JS
       function */
    basename_atom = JS_GetScriptOrModuleName(ctx, 1);
    if (basename_atom == JS_ATOM_NULL) {
        return JS_ThrowTypeError(ctx, "could not determine calling script or module name");
    }
    basename = JS_AtomToCString(ctx, basename_atom);
    JS_FreeAtom(ctx, basename_atom);
    if (!basename)
        goto fail;

    /* module name */
    filename = JS_ToCString(ctx, argv[0]);
    if (!filename)
        goto fail;

    args = malloc(sizeof(*args));
    if (!args)
        goto oom_fail;
    memset(args, 0, sizeof(*args));
    args->filename = strdup(filename);
    args->basename = strdup(basename);

    /* ports */
    args->recv_pipe = js_new_message_pipe();
    if (!args->recv_pipe)
        goto oom_fail;
    args->send_pipe = js_new_message_pipe();
    if (!args->send_pipe)
        goto oom_fail;

    obj = js_worker_ctor_internal(ctx, new_target,
                                  args->send_pipe, args->recv_pipe);
    if (JS_IsException(obj))
        goto fail;

    ret = js_thread_create(&thr, worker_func, args, JS_THREAD_CREATE_DETACHED);
    if (ret != 0) {
        JS_ThrowTypeError(ctx, "could not create worker");
        goto fail;
    }
    JS_FreeCString(ctx, basename);
    JS_FreeCString(ctx, filename);
    return obj;
 oom_fail:
    JS_ThrowOutOfMemory(ctx);
 fail:
    JS_FreeCString(ctx, basename);
    JS_FreeCString(ctx, filename);
    if (args) {
        free(args->filename);
        free(args->basename);
        js_free_message_pipe(args->recv_pipe);
        js_free_message_pipe(args->send_pipe);
        free(args);
    }
    JS_FreeValue(ctx, obj);
    return JS_EXCEPTION;
}

static JSValue js_worker_postMessage(JSContext *ctx, JSValueConst this_val,
                                     int argc, JSValueConst *argv)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSWorkerData *worker = JS_GetOpaque2(ctx, this_val, ts->worker_class_id);
    JSWorkerMessagePipe *ps;
    size_t data_len, i;
    uint8_t *data;
    JSWorkerMessage *msg;
    JSSABTab sab_tab;

    if (!worker)
        return JS_EXCEPTION;

    data = JS_WriteObject2(ctx, &data_len, argv[0],
                           JS_WRITE_OBJ_SAB | JS_WRITE_OBJ_REFERENCE,
                           &sab_tab);
    if (!data)
        return JS_EXCEPTION;

    msg = malloc(sizeof(*msg));
    if (!msg)
        goto fail;
    msg->data = NULL;
    msg->sab_tab = NULL;

    /* must reallocate because the allocator may be different */
    msg->data = malloc(data_len);
    if (!msg->data)
        goto fail;
    memcpy(msg->data, data, data_len);
    msg->data_len = data_len;

    if (sab_tab.len > 0) {
        msg->sab_tab = malloc(sizeof(msg->sab_tab[0]) * sab_tab.len);
        if (!msg->sab_tab)
            goto fail;
        memcpy(msg->sab_tab, sab_tab.tab, sizeof(msg->sab_tab[0]) * sab_tab.len);
    }
    msg->sab_tab_len = sab_tab.len;

    js_free(ctx, data);
    js_free(ctx, sab_tab.tab);

    /* increment the SAB reference counts */
    for(i = 0; i < msg->sab_tab_len; i++) {
        js_sab_dup(NULL, msg->sab_tab[i]);
    }

    ps = worker->send_pipe;
    js_mutex_lock(&ps->mutex);
    /* indicate that data is present */
    if (list_empty(&ps->msg_queue))
        js_waker_signal(&ps->waker);
    list_add_tail(&msg->link, &ps->msg_queue);
    js_mutex_unlock(&ps->mutex);
    return JS_UNDEFINED;
 fail:
    if (msg) {
        free(msg->data);
        free(msg->sab_tab);
        free(msg);
    }
    js_free(ctx, data);
    js_free(ctx, sab_tab.tab);
    return JS_EXCEPTION;

}

static JSValue js_worker_set_onmessage(JSContext *ctx, JSValueConst this_val,
                                       JSValueConst func)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSWorkerData *worker = JS_GetOpaque2(ctx, this_val, ts->worker_class_id);
    JSWorkerMessageHandler *port;

    if (!worker)
        return JS_EXCEPTION;

    port = worker->msg_handler;
    if (JS_IsNull(func)) {
        if (port) {
            js_free_port(rt, port);
            worker->msg_handler = NULL;
        }
    } else {
        if (!JS_IsFunction(ctx, func))
            return JS_ThrowTypeError(ctx, "not a function");
        if (!port) {
            port = js_mallocz(ctx, sizeof(*port));
            if (!port)
                return JS_EXCEPTION;
            port->recv_pipe = js_dup_message_pipe(worker->recv_pipe);
            port->on_message_func = JS_NULL;
            list_add_tail(&port->link, &ts->port_list);
            worker->msg_handler = port;
        }
        JS_FreeValue(ctx, port->on_message_func);
        port->on_message_func = JS_DupValue(ctx, func);
    }
    return JS_UNDEFINED;
}

static JSValue js_worker_get_onmessage(JSContext *ctx, JSValueConst this_val)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSWorkerData *worker = JS_GetOpaque2(ctx, this_val, ts->worker_class_id);
    JSWorkerMessageHandler *port;
    if (!worker)
        return JS_EXCEPTION;
    port = worker->msg_handler;
    if (port) {
        return JS_DupValue(ctx, port->on_message_func);
    } else {
        return JS_NULL;
    }
}

static const JSCFunctionListEntry js_worker_proto_funcs[] = {
    JS_CFUNC_DEF("postMessage", 1, js_worker_postMessage ),
    JS_CGETSET_DEF("onmessage", js_worker_get_onmessage, js_worker_set_onmessage ),
};

#endif /* USE_WORKER */

void js_std_set_worker_new_runtime_func(JSRuntime *(*func)(void))
{
#ifdef USE_WORKER
    js_worker_new_runtime_func = func;
#endif
}

void js_std_set_worker_new_context_func(JSContext *(*func)(JSRuntime *rt))
{
#ifdef USE_WORKER
    js_worker_new_context_func = func;
#endif
}

#if defined(_WIN32)
#define OS_PLATFORM "win32"
#elif defined(__APPLE__)
#define OS_PLATFORM "darwin"
#elif defined(EMSCRIPTEN)
#define OS_PLATFORM "js"
#elif defined(__CYGWIN__)
#define OS_PLATFORM "cygwin"
#elif defined(__linux__)
#define OS_PLATFORM "linux"
#elif defined(__OpenBSD__)
#define OS_PLATFORM "openbsd"
#elif defined(__NetBSD__)
#define OS_PLATFORM "netbsd"
#elif defined(__FreeBSD__)
#define OS_PLATFORM "freebsd"
#elif defined(__wasi__)
#define OS_PLATFORM "wasi"
#elif defined(__GNU__)
#define OS_PLATFORM "hurd"
#else
#define OS_PLATFORM "unknown"
#endif

#define OS_FLAG(x) JS_PROP_INT32_DEF(#x, x, JS_PROP_CONFIGURABLE )

static const JSCFunctionListEntry js_os_funcs[] = {
    JS_CFUNC_DEF("open", 2, js_os_open ),
    OS_FLAG(O_RDONLY),
    OS_FLAG(O_WRONLY),
    OS_FLAG(O_RDWR),
    OS_FLAG(O_APPEND),
    OS_FLAG(O_CREAT),
    OS_FLAG(O_EXCL),
    OS_FLAG(O_TRUNC),
#if defined(_WIN32)
    OS_FLAG(O_BINARY),
    OS_FLAG(O_TEXT),
#endif
    JS_CFUNC_DEF("close", 1, js_os_close ),
    JS_CFUNC_DEF("seek", 3, js_os_seek ),
    JS_CFUNC_MAGIC_DEF("read", 4, js_os_read_write, 0 ),
    JS_CFUNC_MAGIC_DEF("write", 4, js_os_read_write, 1 ),
    JS_CFUNC_DEF("isatty", 1, js_os_isatty ),
#if !defined(__wasi__)
    JS_CFUNC_DEF("ttyGetWinSize", 1, js_os_ttyGetWinSize ),
    JS_CFUNC_DEF("ttySetRaw", 1, js_os_ttySetRaw ),
#endif
    JS_CFUNC_DEF("remove", 1, js_os_remove ),
    JS_CFUNC_DEF("rename", 2, js_os_rename ),
    JS_CFUNC_MAGIC_DEF("setReadHandler", 2, js_os_setReadHandler, 0 ),
    JS_CFUNC_MAGIC_DEF("setWriteHandler", 2, js_os_setReadHandler, 1 ),
    JS_CFUNC_DEF("signal", 2, js_os_signal ),
    OS_FLAG(SIGINT),
    OS_FLAG(SIGABRT),
    OS_FLAG(SIGFPE),
    OS_FLAG(SIGILL),
    OS_FLAG(SIGSEGV),
    OS_FLAG(SIGTERM),
#if !defined(_WIN32) && !defined(__wasi__)
    OS_FLAG(SIGQUIT),
    OS_FLAG(SIGPIPE),
    OS_FLAG(SIGALRM),
    OS_FLAG(SIGUSR1),
    OS_FLAG(SIGUSR2),
    OS_FLAG(SIGCHLD),
    OS_FLAG(SIGCONT),
    OS_FLAG(SIGSTOP),
    OS_FLAG(SIGTSTP),
    OS_FLAG(SIGTTIN),
    OS_FLAG(SIGTTOU),
    JS_CFUNC_DEF("cputime", 0, js_os_cputime ),
#endif
    JS_CFUNC_DEF("exePath", 0, js_os_exepath ),
    JS_CFUNC_DEF("now", 0, js_os_now ),
    JS_CFUNC_MAGIC_DEF("setTimeout", 2, js_os_setTimeout, 0 ),
    JS_CFUNC_MAGIC_DEF("setInterval", 2, js_os_setTimeout, 1 ),
    // per spec: both functions can cancel timeouts and intervals
    JS_CFUNC_DEF("clearTimeout", 1, js_os_clearTimeout ),
    JS_CFUNC_DEF("clearInterval", 1, js_os_clearTimeout ),
    JS_CFUNC_DEF("sleepAsync", 1, js_os_sleepAsync ),
    JS_PROP_STRING_DEF("platform", OS_PLATFORM, 0 ),
    JS_CFUNC_DEF("getcwd", 0, js_os_getcwd ),
    JS_CFUNC_DEF("chdir", 1, js_os_chdir ),
    JS_CFUNC_DEF("mkdir", 1, js_os_mkdir ),
    JS_CFUNC_DEF("readdir", 1, js_os_readdir ),
#if !defined(_WIN32) && !defined(__wasi__)
    JS_CFUNC_MAGIC_DEF("mkdtemp", 0, js_os_mkdstemp, 'd' ),
    JS_CFUNC_MAGIC_DEF("mkstemp", 0, js_os_mkdstemp, 's' ),
#endif
    /* st_mode constants */
    OS_FLAG(S_IFMT),
    OS_FLAG(S_IFIFO),
    OS_FLAG(S_IFCHR),
    OS_FLAG(S_IFDIR),
    OS_FLAG(S_IFBLK),
    OS_FLAG(S_IFREG),
#if !defined(_WIN32)
    OS_FLAG(S_IFSOCK),
    OS_FLAG(S_IFLNK),
    OS_FLAG(S_ISGID),
    OS_FLAG(S_ISUID),
#endif
    JS_CFUNC_MAGIC_DEF("stat", 1, js_os_stat, 0 ),
    JS_CFUNC_DEF("utimes", 3, js_os_utimes ),
    JS_CFUNC_DEF("sleep", 1, js_os_sleep ),
#if !defined(__wasi__)
    JS_CFUNC_DEF("realpath", 1, js_os_realpath ),
#endif
#if !defined(_WIN32) && !defined(__wasi__) && !(defined(__APPLE__) && (TARGET_OS_TV || TARGET_OS_WATCH))
    JS_CFUNC_MAGIC_DEF("lstat", 1, js_os_stat, 1 ),
    JS_CFUNC_DEF("symlink", 2, js_os_symlink ),
    JS_CFUNC_DEF("readlink", 1, js_os_readlink ),
    JS_CFUNC_DEF("exec", 1, js_os_exec ),
    JS_CFUNC_DEF("getpid", 0, js_os_getpid ),
    JS_CFUNC_DEF("waitpid", 2, js_os_waitpid ),
    OS_FLAG(WNOHANG),
    JS_CFUNC_DEF("pipe", 0, js_os_pipe ),
    JS_CFUNC_DEF("kill", 2, js_os_kill ),
    JS_CFUNC_DEF("dup", 1, js_os_dup ),
    JS_CFUNC_DEF("dup2", 2, js_os_dup2 ),
#endif
};

static int js_os_init(JSContext *ctx, JSModuleDef *m)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);

    ts->can_js_os_poll = true;

#ifdef USE_WORKER
    {
        JSValue proto, obj;
        /* Worker class */
        JS_NewClassID(rt, &ts->worker_class_id);
        JS_NewClass(rt, ts->worker_class_id, &js_worker_class);
        proto = JS_NewObject(ctx);
        JS_SetPropertyFunctionList(ctx, proto, js_worker_proto_funcs, countof(js_worker_proto_funcs));

        obj = JS_NewCFunction2(ctx, js_worker_ctor, "Worker", 1,
                               JS_CFUNC_constructor, 0);
        JS_SetConstructor(ctx, obj, proto);

        JS_SetClassProto(ctx, ts->worker_class_id, proto);

        /* set 'Worker.parent' if necessary */
        if (ts->recv_pipe && ts->send_pipe) {
            JS_DefinePropertyValueStr(ctx, obj, "parent",
                                      js_worker_ctor_internal(ctx, JS_UNDEFINED, ts->recv_pipe, ts->send_pipe),
                                      JS_PROP_C_W_E);
        }

        JS_SetModuleExport(ctx, m, "Worker", obj);
    }
#endif /* USE_WORKER */

    return JS_SetModuleExportList(ctx, m, js_os_funcs, countof(js_os_funcs));
}

JSModuleDef *js_init_module_os(JSContext *ctx, const char *module_name)
{
    JSModuleDef *m;
    m = JS_NewCModule(ctx, module_name, js_os_init);
    if (!m)
        return NULL;
    JS_AddModuleExportList(ctx, m, js_os_funcs, countof(js_os_funcs));
#ifdef USE_WORKER
    JS_AddModuleExport(ctx, m, "Worker");
#endif
    return m;
}

/**********************************************************/

static JSValue js_print(JSContext *ctx, JSValueConst this_val,
                        int argc, JSValueConst *argv)
{
#ifdef _WIN32
    HANDLE handle;
    DWORD mode;
#endif
    const char *s;
    JSValueConst v;
    DynBuf b;
    int i;

    dbuf_init(&b);
    for(i = 0; i < argc; i++) {
        v = argv[i];
        s = JS_ToCString(ctx, v);
        if (!s && JS_IsObject(v)) {
            JS_FreeValue(ctx, JS_GetException(ctx));
            JSValue t = JS_ToObjectString(ctx, v);
            s = JS_ToCString(ctx, t);
            JS_FreeValue(ctx, t);
        }
        if (s) {
            dbuf_printf(&b, "%s%s", &" "[!i], s);
            JS_FreeCString(ctx, s);
        } else {
            dbuf_printf(&b, "%s<exception>", &" "[!i]);
            JS_FreeValue(ctx, JS_GetException(ctx));
        }
    }
    dbuf_putc(&b, '\n');
#ifdef _WIN32
    // use WriteConsoleA with CP_UTF8 for better Unicode handling vis-a-vis
    // the mangling that happens when going through msvcrt's stdio layer,
    // *except* when stdout is redirected to something that is not a console
    handle = (HANDLE)_get_osfhandle(/*STDOUT_FILENO*/1); // don't CloseHandle
    if (GetFileType(handle) != FILE_TYPE_CHAR)
        goto fallback;
    if (!GetConsoleMode(handle, &mode))
        goto fallback;
    handle = GetStdHandle(STD_OUTPUT_HANDLE);
    if (handle == INVALID_HANDLE_VALUE)
        goto fallback;
    mode = GetConsoleOutputCP();
    SetConsoleOutputCP(CP_UTF8);
    WriteConsoleA(handle, b.buf, b.size, NULL, NULL);
    SetConsoleOutputCP(mode);
    FlushFileBuffers(handle);
    goto done;
fallback:
#endif
    fwrite(b.buf, 1, b.size, stdout);
    fflush(stdout);
    goto done; // avoid unused label warning
done:
    dbuf_free(&b);
    return JS_UNDEFINED;
}

void js_std_add_helpers(JSContext *ctx, int argc, char **argv)
{
    JSValue global_obj, console, args;
    int i;

    /* XXX: should these global definitions be enumerable? */
    global_obj = JS_GetGlobalObject(ctx);

    console = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, console, "log",
                      JS_NewCFunction(ctx, js_print, "log", 1));
    JS_SetPropertyStr(ctx, global_obj, "console", console);

    /* same methods as the mozilla JS shell */
    if (argc >= 0) {
        args = JS_NewArray(ctx);
        for(i = 0; i < argc; i++) {
            JS_SetPropertyUint32(ctx, args, i, JS_NewString(ctx, argv[i]));
        }
        JS_SetPropertyStr(ctx, global_obj, "scriptArgs", args);
    }

    JS_SetPropertyStr(ctx, global_obj, "print",
                      JS_NewCFunction(ctx, js_print, "print", 1));

    JS_FreeValue(ctx, global_obj);
}

static void js_std_finalize(JSRuntime *rt, void *arg)
{
    JSThreadState *ts = arg;
    js_set_thread_state(rt, NULL);
    js_free_rt(rt, ts);
}

void js_std_init_handlers(JSRuntime *rt)
{
    JSThreadState *ts;

    ts = js_mallocz_rt(rt, sizeof(*ts));
    if (!ts) {
        fprintf(stderr, "Could not allocate memory for the worker");
        exit(1);
    }
    init_list_head(&ts->os_rw_handlers);
    init_list_head(&ts->os_signal_handlers);
    init_list_head(&ts->os_timers);
    init_list_head(&ts->port_list);
    init_list_head(&ts->rejected_promise_list);

    ts->next_timer_id = 1;

    js_set_thread_state(rt, ts);
    JS_AddRuntimeFinalizer(rt, js_std_finalize, ts);

#ifdef USE_WORKER
    /* set the SharedArrayBuffer memory handlers */
    {
        JSSharedArrayBufferFunctions sf;
        memset(&sf, 0, sizeof(sf));
        sf.sab_alloc = js_sab_alloc;
        sf.sab_free = js_sab_free;
        sf.sab_dup = js_sab_dup;
        JS_SetSharedArrayBufferFunctions(rt, &sf);
    }
#endif
}

static void free_rp(JSRuntime *rt, JSRejectedPromiseEntry *rp)
{
    list_del(&rp->link);
    JS_FreeValueRT(rt, rp->promise);
    JS_FreeValueRT(rt, rp->reason);
    js_free_rt(rt, rp);
}

void js_std_free_handlers(JSRuntime *rt)
{
    JSThreadState *ts = js_get_thread_state(rt);
    struct list_head *el, *el1;

    list_for_each_safe(el, el1, &ts->os_rw_handlers) {
        JSOSRWHandler *rh = list_entry(el, JSOSRWHandler, link);
        free_rw_handler(rt, rh);
    }

    list_for_each_safe(el, el1, &ts->os_signal_handlers) {
        JSOSSignalHandler *sh = list_entry(el, JSOSSignalHandler, link);
        free_sh(rt, sh);
    }

    list_for_each_safe(el, el1, &ts->os_timers) {
        JSOSTimer *th = list_entry(el, JSOSTimer, link);
        free_timer(rt, th);
    }

    list_for_each_safe(el, el1, &ts->rejected_promise_list) {
        JSRejectedPromiseEntry *rp = list_entry(el, JSRejectedPromiseEntry, link);
        free_rp(rt, rp);
    }

#ifdef USE_WORKER
    /* XXX: free port_list ? */
    js_free_message_pipe(ts->recv_pipe);
    js_free_message_pipe(ts->send_pipe);
#endif
}

static void js_dump_obj(JSContext *ctx, FILE *f, JSValueConst val)
{
    const char *str;

    str = JS_ToCString(ctx, val);
    if (str) {
        fprintf(f, "%s\n", str);
        JS_FreeCString(ctx, str);
    } else {
        fprintf(f, "[exception]\n");
    }
}

#define JS_DUMP_ERROR_MAX_CAUSE_DEPTH 10

static void js_std_dump_error1(JSContext *ctx, JSValueConst exception_val)
{
    JSValue val, current;
    bool is_error;
    int depth;

    current = JS_DupValue(ctx, exception_val);
    for (depth = 0; ; depth++) {
        is_error = JS_IsError(current);
        js_dump_obj(ctx, stderr, current);
        if (is_error) {
            val = JS_GetPropertyStr(ctx, current, "stack");
        } else if (depth == 0) {
            js_std_cmd(/*ErrorBackTrace*/2, ctx, &val);
        } else {
            val = JS_UNDEFINED;
        }
        if (!JS_IsUndefined(val)) {
            js_dump_obj(ctx, stderr, val);
            JS_FreeValue(ctx, val);
        }
        if (!is_error || depth >= JS_DUMP_ERROR_MAX_CAUSE_DEPTH)
            break;
        val = JS_GetPropertyStr(ctx, current, "cause");
        if (JS_IsUndefined(val)) {
            JS_FreeValue(ctx, val);
            break;
        }
        fputs("Caused by: ", stderr);
        JS_FreeValue(ctx, current);
        current = val;
    }
    JS_FreeValue(ctx, current);
}

void js_std_dump_error(JSContext *ctx)
{
    JSValue exception_val;

    exception_val = JS_GetException(ctx);
    js_std_dump_error1(ctx, exception_val);
    JS_FreeValue(ctx, exception_val);
}

static JSRejectedPromiseEntry *find_rejected_promise(JSContext *ctx, JSThreadState *ts,
                                                     JSValueConst promise)
{
    struct list_head *el;

    list_for_each(el, &ts->rejected_promise_list) {
        JSRejectedPromiseEntry *rp = list_entry(el, JSRejectedPromiseEntry, link);
        if (JS_IsSameValue(ctx, rp->promise, promise))
            return rp;
    }
    return NULL;
}

void js_std_promise_rejection_tracker(JSContext *ctx, JSValueConst promise,
                                      JSValueConst reason,
                                      bool is_handled, void *opaque)
{

    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSRejectedPromiseEntry *rp = find_rejected_promise(ctx, ts, promise);

    if (is_handled) {
        /* the rejection is handled, so the entry can be removed if present */
        if (rp)
            free_rp(rt, rp);
    } else {
        /* add a new entry if needed */
        if (!rp) {
            rp = js_malloc_rt(rt, sizeof(*rp));
            if (rp) {
                rp->promise = JS_DupValue(ctx, promise);
                rp->reason = JS_DupValue(ctx, reason);
                list_add_tail(&rp->link, &ts->rejected_promise_list);
            }
        }
    }
}

/* check if there are pending promise rejections. It must be done
   asynchrously in case a rejected promise is handled later. Currently
   we do it once the application is about to sleep. It could be done
   more often if needed. */
static void js_std_promise_rejection_check(JSContext *ctx)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    struct list_head *el;

    if (unlikely(!list_empty(&ts->rejected_promise_list))) {
        list_for_each(el, &ts->rejected_promise_list) {
            JSRejectedPromiseEntry *rp = list_entry(el, JSRejectedPromiseEntry, link);
            fprintf(stderr, "Possibly unhandled promise rejection: ");
            js_std_dump_error1(ctx, rp->reason);
            fflush(stderr);
        }
        exit(1);
    }
}

/* main loop which calls the user JS callbacks */
int js_std_loop(JSContext *ctx)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSContext *ctx1;
    int err;

    for(;;) {
        /* execute the pending jobs */
        for(;;) {
            err = JS_ExecutePendingJob(JS_GetRuntime(ctx), &ctx1);
            if (err <= 0) {
                if (err < 0)
                    goto done;
                break;
            }
        }

        js_std_promise_rejection_check(ctx);

        if (!ts->can_js_os_poll || js_os_poll(ctx))
            break;
    }
done:
    return JS_HasException(ctx);
}

int js_std_loop_once(JSContext *ctx)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSContext *ctx1;
    int err, min_delay;

    /* execute all pending jobs */
    for(;;) {
        err = JS_ExecutePendingJob(rt, &ctx1);
        if (err < 0)
            return -2; /* error */
        if (err == 0)
            break;
    }

    /* run at most one expired timer */
    if (js_os_run_timers(rt, ctx, ts, &min_delay) < 0)
        return -2; /* error in timer callback */

    /* check if more work is pending */
    if (JS_IsJobPending(rt))
        return 0; /* more microtasks pending */

    if (min_delay == 0)
        return 0; /* timer ready to fire immediately */

    if (min_delay > 0)
        return min_delay; /* next timer delay in ms */

    return -1; /* idle, no pending work */
}

/* Wait for a promise and execute pending jobs while waiting for
   it. Return the promise result or JS_EXCEPTION in case of promise
   rejection. */
JSValue js_std_await(JSContext *ctx, JSValue obj)
{
    JSRuntime *rt = JS_GetRuntime(ctx);
    JSThreadState *ts = js_get_thread_state(rt);
    JSValue ret;
    int state;

    for(;;) {
        state = JS_PromiseState(ctx, obj);
        if (state == JS_PROMISE_FULFILLED) {
            ret = JS_PromiseResult(ctx, obj);
            JS_FreeValue(ctx, obj);
            break;
        } else if (state == JS_PROMISE_REJECTED) {
            ret = JS_Throw(ctx, JS_PromiseResult(ctx, obj));
            JS_FreeValue(ctx, obj);
            break;
        } else if (state == JS_PROMISE_PENDING) {
            JSContext *ctx1;
            int err;
            err = JS_ExecutePendingJob(JS_GetRuntime(ctx), &ctx1);
            if (err < 0) {
                js_std_dump_error(ctx1);
            }
            if (err == 0)
                js_std_promise_rejection_check(ctx);
            if (ts->can_js_os_poll)
                js_os_poll(ctx);
        } else {
            /* not a promise */
            ret = obj;
            break;
        }
    }
    return ret;
}

void js_std_eval_binary(JSContext *ctx, const uint8_t *buf, size_t buf_len,
                        int load_only)
{
    JSValue obj, val;
    obj = JS_ReadObject(ctx, buf, buf_len, JS_READ_OBJ_BYTECODE);
    if (JS_IsException(obj))
        goto exception;
    if (load_only) {
        if (JS_VALUE_GET_TAG(obj) == JS_TAG_MODULE) {
            if (js_module_set_import_meta(ctx, obj, false, false) < 0)
                goto exception;
        }
        JS_FreeValue(ctx, obj);
    } else {
        if (JS_VALUE_GET_TAG(obj) == JS_TAG_MODULE) {
            if (JS_ResolveModule(ctx, obj) < 0) {
                JS_FreeValue(ctx, obj);
                goto exception;
            }
            if (js_module_set_import_meta(ctx, obj, false, true) < 0)
                goto exception;
            val = JS_EvalFunction(ctx, obj);
            val = js_std_await(ctx, val);
        } else {
            val = JS_EvalFunction(ctx, obj);
        }
        if (JS_IsException(val)) {
        exception:
            js_std_dump_error(ctx);
            exit(1);
        }
        JS_FreeValue(ctx, val);
    }
}

static JSValue js_bjson_read(JSContext *ctx, JSValueConst this_val,
                             int argc, JSValueConst *argv)
{
    uint8_t *buf;
    uint64_t pos, len;
    JSValue obj;
    size_t size;
    int flags;

    if (JS_ToIndex(ctx, &pos, argv[1]))
        return JS_EXCEPTION;
    if (JS_ToIndex(ctx, &len, argv[2]))
        return JS_EXCEPTION;
    if (JS_ToInt32(ctx, &flags, argv[3]))
        return JS_EXCEPTION;
    flags &= ~JS_READ_OBJ_SAB;
    buf = JS_GetArrayBuffer(ctx, &size, argv[0]);
    if (!buf)
        return JS_EXCEPTION;
    if (pos + len > size)
        return JS_ThrowRangeError(ctx, "array buffer overflow");
    obj = JS_ReadObject(ctx, buf + pos, len, flags);
    return obj;
}

static JSValue js_bjson_write(JSContext *ctx, JSValueConst this_val,
                              int argc, JSValueConst *argv)
{
    size_t len;
    uint8_t *buf;
    JSValue array;
    int flags;

    if (JS_ToInt32(ctx, &flags, argv[1]))
        return JS_EXCEPTION;
    flags &= ~JS_WRITE_OBJ_SAB;
    buf = JS_WriteObject(ctx, &len, argv[0], flags);
    if (!buf)
        return JS_EXCEPTION;
    array = JS_NewArrayBufferCopy(ctx, buf, len);
    js_free(ctx, buf);
    return array;
}


static const JSCFunctionListEntry js_bjson_funcs[] = {
    JS_CFUNC_DEF("read", 4, js_bjson_read ),
    JS_CFUNC_DEF("write", 2, js_bjson_write ),
#define DEF(x) JS_PROP_INT32_DEF(#x, JS_##x, JS_PROP_CONFIGURABLE)
    DEF(READ_OBJ_BYTECODE),
    DEF(READ_OBJ_REFERENCE),
    DEF(WRITE_OBJ_BYTECODE),
    DEF(WRITE_OBJ_REFERENCE),
    DEF(WRITE_OBJ_STRIP_DEBUG),
    DEF(WRITE_OBJ_STRIP_SOURCE),
#undef DEF
};

static int js_bjson_init(JSContext *ctx, JSModuleDef *m)
{
    return JS_SetModuleExportList(ctx, m, js_bjson_funcs,
                                  countof(js_bjson_funcs));
}

JSModuleDef *js_init_module_bjson(JSContext *ctx, const char *module_name)
{
    JSModuleDef *m;
    m = JS_NewCModule(ctx, module_name, js_bjson_init);
    if (!m)
        return NULL;
    JS_AddModuleExportList(ctx, m, js_bjson_funcs, countof(js_bjson_funcs));
    return m;
}
