/*
 * C utilities
 *
 * Copyright (c) 2017 Fabrice Bellard
 * Copyright (c) 2018 Charlie Gordon
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
#ifndef CUTILS_H
#define CUTILS_H

#include <assert.h>
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#if !defined(_MSC_VER)
#include <sys/time.h>
#endif
#if defined(__APPLE__)
#include <mach-o/dyld.h>
#endif
#include <stdbool.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <math.h>

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_MSC_VER)
#include <malloc.h>
#define alloca _alloca
#define ssize_t ptrdiff_t
#endif
#if defined(__APPLE__)
#include <malloc/malloc.h>
#elif defined(__linux__) || defined(__ANDROID__) || defined(__CYGWIN__) || defined(__GLIBC__) || defined(__wasi__)
#include <malloc.h>
#elif defined(__FreeBSD__)
#include <malloc_np.h>
#elif defined(_WIN32)
#include <winsock2.h>
#include <windows.h>
#include <process.h> // _beginthread
#endif
#if !defined(_WIN32) && !defined(EMSCRIPTEN) && !defined(__wasi__) && !defined(__DJGPP)
#include <errno.h>
#include <pthread.h>
#endif
#if !defined(_WIN32)
#include <limits.h>
#include <unistd.h>
#endif

#if defined(__sun)
#undef __maybe_unused
#endif

#if defined(_MSC_VER) && !defined(__clang__)
#  define likely(x)       (x)
#  define unlikely(x)     (x)
#  define no_inline __declspec(noinline)
#  define __maybe_unused
#  define __attribute__(x)
#  define __attribute(x)
#else
#  define likely(x)       __builtin_expect(!!(x), 1)
#  define unlikely(x)     __builtin_expect(!!(x), 0)
#  define no_inline __attribute__((noinline))
#  define __maybe_unused __attribute__((unused))
#endif

#ifndef offsetof
#define offsetof(type, field) ((size_t) &((type *)0)->field)
#endif
#ifndef countof
#define countof(x) (sizeof(x) / sizeof((x)[0]))
#ifndef endof
#define endof(x) ((x) + countof(x))
#endif
#endif
#ifndef container_of
/* return the pointer of type 'type *' containing 'ptr' as field 'member' */
#define container_of(ptr, type, member) ((type *)((uint8_t *)(ptr) - offsetof(type, member)))
#endif

#if defined(_MSC_VER) || defined(__cplusplus)
#define minimum_length(n) n
#else
#define minimum_length(n) static n
#endif

/* Borrowed from Folly */
#ifndef JS_PRINTF_FORMAT
/* Clang on Windows doesn't seem to support _Printf_format_string_ */
#if defined(_MSC_VER) && !defined(__clang__)
#include <sal.h>
#define JS_PRINTF_FORMAT _Printf_format_string_
#define JS_PRINTF_FORMAT_ATTR(format_param, dots_param)
#else
#define JS_PRINTF_FORMAT
#if !defined(__clang__) && defined(__GNUC__)
#define JS_PRINTF_FORMAT_ATTR(format_param, dots_param) \
  __attribute__((format(gnu_printf, format_param, dots_param)))
#else
#define JS_PRINTF_FORMAT_ATTR(format_param, dots_param) \
  __attribute__((format(printf, format_param, dots_param)))
#endif
#endif
#endif

#if defined(PATH_MAX)
# define JS__PATH_MAX PATH_MAX
#elif defined(_WIN32)
# define JS__PATH_MAX 32767
#else
# define JS__PATH_MAX 8192
#endif

static inline void js__pstrcpy(char *buf, int buf_size, const char *str);
static inline char *js__pstrcat(char *buf, int buf_size, const char *s);
static inline int js__strstart(const char *str, const char *val, const char **ptr);
static inline int js__has_suffix(const char *str, const char *suffix);

static inline uint8_t is_be(void) {
    union {
        uint16_t a;
        uint8_t  b;
    } u = { 0x100 };
    return u.b;
}

static inline int max_int(int a, int b)
{
    if (a > b)
        return a;
    else
        return b;
}

static inline int min_int(int a, int b)
{
    if (a < b)
        return a;
    else
        return b;
}

static inline uint32_t max_uint32(uint32_t a, uint32_t b)
{
    if (a > b)
        return a;
    else
        return b;
}

static inline uint32_t min_uint32(uint32_t a, uint32_t b)
{
    if (a < b)
        return a;
    else
        return b;
}

static inline int64_t max_int64(int64_t a, int64_t b)
{
    if (a > b)
        return a;
    else
        return b;
}

static inline int64_t min_int64(int64_t a, int64_t b)
{
    if (a < b)
        return a;
    else
        return b;
}

static inline uint32_t hash32(uint32_t a)
{
    // use the negative of the golden ratio, it spreads out the bits nicely
    // and is what the linux kernel does
    //
    // the golden ratio phi is defined as (1+sqrt(5))/2 or 1 + (sqrt(5)-1)/2
    // (approx. 1.618033988), and negated is round(2**32/phi**2) = 0x61c88647
    return a * 0x61c88647;
}

static inline uint64_t hash64(uint64_t a)
{
    return a * 0x61c8864680b583ebULL;
}

static inline uintptr_t hash_uintptr(uintptr_t a)
{
    if (sizeof(uintptr_t) == sizeof(uint32_t)) {
        return hash32(a);
    } else {
        return hash64(a);
    }
}

/* WARNING: undefined if a = 0 */
static inline int clz32(unsigned int a)
{
#if defined(_MSC_VER) && !defined(__clang__)
    unsigned long index;
    _BitScanReverse(&index, a);
    return 31 - index;
#else
    return __builtin_clz(a);
#endif
}

/* WARNING: undefined if a = 0 */
static inline int clz64(uint64_t a)
{
#if defined(_MSC_VER) && !defined(__clang__)
#if INTPTR_MAX == INT64_MAX
    unsigned long index;
    _BitScanReverse64(&index, a);
    return 63 - index;
#else
    if (a >> 32)
        return clz32((unsigned)(a >> 32));
    else
        return clz32((unsigned)a) + 32;
#endif
#else
    return __builtin_clzll(a);
#endif
}

static inline uintptr_t clz_uintptr(uintptr_t a)
{
    if (sizeof(uintptr_t) == sizeof(uint32_t)) {
        return clz32(a);
    } else {
        return clz64(a);
    }
}

/* WARNING: undefined if a = 0 */
static inline int ctz32(unsigned int a)
{
#if defined(_MSC_VER) && !defined(__clang__)
    unsigned long index;
    _BitScanForward(&index, a);
    return index;
#else
    return __builtin_ctz(a);
#endif
}

/* WARNING: undefined if a = 0 */
static inline int ctz64(uint64_t a)
{
#if defined(_MSC_VER) && !defined(__clang__)
    unsigned long index;
    _BitScanForward64(&index, a);
    return index;
#else
    return __builtin_ctzll(a);
#endif
}

static inline uint64_t get_u64(const uint8_t *tab)
{
    uint64_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline int64_t get_i64(const uint8_t *tab)
{
    int64_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline void put_u64(uint8_t *tab, uint64_t val)
{
    memcpy(tab, &val, sizeof(val));
}

static inline uint32_t get_u32(const uint8_t *tab)
{
    uint32_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline int32_t get_i32(const uint8_t *tab)
{
    int32_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline void put_u32(uint8_t *tab, uint32_t val)
{
    memcpy(tab, &val, sizeof(val));
}

static inline uint32_t get_u16(const uint8_t *tab)
{
    uint16_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline int32_t get_i16(const uint8_t *tab)
{
    int16_t v;
    memcpy(&v, tab, sizeof(v));
    return v;
}

static inline void put_u16(uint8_t *tab, uint16_t val)
{
    memcpy(tab, &val, sizeof(val));
}

static inline uint32_t get_u8(const uint8_t *tab)
{
    return *tab;
}

static inline int32_t get_i8(const uint8_t *tab)
{
    return (int8_t)*tab;
}

static inline void put_u8(uint8_t *tab, uint8_t val)
{
    *tab = val;
}

#ifndef bswap16
static inline uint16_t bswap16(uint16_t x)
{
    return (x >> 8) | (x << 8);
}
#endif

#ifndef bswap32
static inline uint32_t bswap32(uint32_t v)
{
    return ((v & 0xff000000) >> 24) | ((v & 0x00ff0000) >>  8) |
        ((v & 0x0000ff00) <<  8) | ((v & 0x000000ff) << 24);
}
#endif

#ifndef bswap64
static inline uint64_t bswap64(uint64_t v)
{
    return ((v & ((uint64_t)0xff << (7 * 8))) >> (7 * 8)) |
        ((v & ((uint64_t)0xff << (6 * 8))) >> (5 * 8)) |
        ((v & ((uint64_t)0xff << (5 * 8))) >> (3 * 8)) |
        ((v & ((uint64_t)0xff << (4 * 8))) >> (1 * 8)) |
        ((v & ((uint64_t)0xff << (3 * 8))) << (1 * 8)) |
        ((v & ((uint64_t)0xff << (2 * 8))) << (3 * 8)) |
        ((v & ((uint64_t)0xff << (1 * 8))) << (5 * 8)) |
        ((v & ((uint64_t)0xff << (0 * 8))) << (7 * 8));
}
#endif

static inline double fromfp16(uint16_t v) {
    double d, s;
    int e;
    if ((v & 0x7C00) == 0x7C00) {
        d = (v & 0x3FF) ? NAN : INFINITY;
    } else {
        d = (v & 0x3FF) / 1024.;
        e = (v & 0x7C00) >> 10;
        if (e == 0) {
            e = -14;
        } else {
            d += 1;
            e -= 15;
        }
        d = scalbn(d, e);
    }
    s = (v & 0x8000) ? -1.0 : 1.0;
    return d * s;
}

static inline uint16_t tofp16(double d) {
    uint16_t f, s;
    double t;
    int e;
    s = 0;
    if (copysign(1, d) < 0) { // preserve sign when |d| is negative zero
        d = -d;
        s = 0x8000;
    }
    if (isinf(d))
        return s | 0x7C00;
    if (isnan(d))
        return s | 0x7C01;
    if (d == 0)
        return s | 0;
    d = 2 * frexp(d, &e);
    e--;
    if (e > 15)
        return s | 0x7C00; // out of range, return +/-infinity
    if (e < -25) {
        d = 0;
        e = 0;
    } else if (e < -14) {
        d = scalbn(d, e + 14);
        e = 0;
    } else {
        d -= 1;
        e += 15;
    }
    d *= 1024.;
    f = (uint16_t)d;
    t = d - f;
    if (t < 0.5)
        goto done;
    if (t == 0.5)
        if ((f & 1) == 0)
            goto done;
    // adjust for rounding
    if (++f == 1024) {
        f = 0;
        if (++e == 31)
            return s | 0x7C00; // out of range, return +/-infinity
    }
done:
    return s | (e << 10) | f;
}

static inline int isfp16nan(uint16_t v) {
    return (v & 0x7FFF) > 0x7C00;
}

static inline int isfp16zero(uint16_t v) {
    return (v & 0x7FFF) == 0;
}

/* XXX: should take an extra argument to pass slack information to the caller */
typedef void *DynBufReallocFunc(void *opaque, void *ptr, size_t size);

typedef struct DynBuf {
    uint8_t *buf;
    size_t size;
    size_t allocated_size;
    bool error; /* true if a memory allocation error occurred */
    DynBufReallocFunc *realloc_func;
    void *opaque; /* for realloc_func */
} DynBuf;

static inline void dbuf_init(DynBuf *s);
static inline void dbuf_init2(DynBuf *s, void *opaque, DynBufReallocFunc *realloc_func);
static inline int dbuf_claim(DynBuf *s, size_t len);
static inline int dbuf_put(DynBuf *s, const void *data, size_t len);
static inline int dbuf_put_self(DynBuf *s, size_t offset, size_t len);
static inline int __dbuf_putc(DynBuf *s, uint8_t c);
static inline int __dbuf_put_u16(DynBuf *s, uint16_t val);
static inline int __dbuf_put_u32(DynBuf *s, uint32_t val);
static inline int __dbuf_put_u64(DynBuf *s, uint64_t val);
static inline int dbuf_putstr(DynBuf *s, const char *str);
static inline int dbuf_putc(DynBuf *s, uint8_t val)
{
    if (unlikely((s->allocated_size - s->size) < 1))
        return __dbuf_putc(s, val);
    s->buf[s->size++] = val;
    return 0;
}
static inline int dbuf_put_u16(DynBuf *s, uint16_t val)
{
    if (unlikely((s->allocated_size - s->size) < 2))
        return __dbuf_put_u16(s, val);
    put_u16(s->buf + s->size, val);
    s->size += 2;
    return 0;
}
static inline int dbuf_put_u32(DynBuf *s, uint32_t val)
{
    if (unlikely((s->allocated_size - s->size) < 4))
        return __dbuf_put_u32(s, val);
    put_u32(s->buf + s->size, val);
    s->size += 4;
    return 0;
}
static inline int dbuf_put_u64(DynBuf *s, uint64_t val)
{
    if (unlikely((s->allocated_size - s->size) < 8))
        return __dbuf_put_u64(s, val);
    put_u64(s->buf + s->size, val);
    s->size += 8;
    return 0;
}
static inline int JS_PRINTF_FORMAT_ATTR(2, 3) dbuf_printf(DynBuf *s, JS_PRINTF_FORMAT const char *fmt, ...);
static inline void dbuf_free(DynBuf *s);
static inline bool dbuf_error(DynBuf *s) {
    return s->error;
}
static inline void dbuf_set_error(DynBuf *s)
{
    s->error = true;
}

/*---- UTF-8 and UTF-16 handling ----*/

#define UTF8_CHAR_LEN_MAX 4

enum {
    UTF8_PLAIN_ASCII  = 0,  // 7-bit ASCII plain text
    UTF8_NON_ASCII    = 1,  // has non ASCII code points (8-bit or more)
    UTF8_HAS_16BIT    = 2,  // has 16-bit code points
    UTF8_HAS_NON_BMP1 = 4,  // has non-BMP1 code points, needs UTF-16 surrogate pairs
    UTF8_HAS_ERRORS   = 8,  // has encoding errors
};
static inline int utf8_scan(const char *buf, size_t len, size_t *plen);
static inline size_t utf8_encode_len(uint32_t c);
static inline size_t utf8_encode(uint8_t buf[minimum_length(UTF8_CHAR_LEN_MAX)], uint32_t c);
static inline uint32_t utf8_decode_len(const uint8_t *p, size_t max_len, const uint8_t **pp);
static inline uint32_t utf8_decode(const uint8_t *p, const uint8_t **pp);
static inline size_t utf8_decode_buf8(uint8_t *dest, size_t dest_len, const char *src, size_t src_len);
static inline size_t utf8_decode_buf16(uint16_t *dest, size_t dest_len, const char *src, size_t src_len);
static inline size_t utf8_encode_buf8(char *dest, size_t dest_len, const uint8_t *src, size_t src_len);
static inline size_t utf8_encode_buf16(char *dest, size_t dest_len, const uint16_t *src, size_t src_len);

static inline bool is_surrogate(uint32_t c)
{
    return (c >> 11) == (0xD800 >> 11); // 0xD800-0xDFFF
}

static inline bool is_hi_surrogate(uint32_t c)
{
    return (c >> 10) == (0xD800 >> 10); // 0xD800-0xDBFF
}

static inline bool is_lo_surrogate(uint32_t c)
{
    return (c >> 10) == (0xDC00 >> 10); // 0xDC00-0xDFFF
}

static inline uint32_t get_hi_surrogate(uint32_t c)
{
    return (c >> 10) - (0x10000 >> 10) + 0xD800;
}

static inline uint32_t get_lo_surrogate(uint32_t c)
{
    return (c & 0x3FF) | 0xDC00;
}

static inline uint32_t from_surrogate(uint32_t hi, uint32_t lo)
{
    return 65536 + 1024 * (hi & 1023) + (lo & 1023);
}

static inline int from_hex(int c)
{
    if (c >= '0' && c <= '9')
        return c - '0';
    else if (c >= 'A' && c <= 'F')
        return c - 'A' + 10;
    else if (c >= 'a' && c <= 'f')
        return c - 'a' + 10;
    else
        return -1;
}

static inline uint8_t is_upper_ascii(uint8_t c) {
    return c >= 'A' && c <= 'Z';
}

static inline uint8_t to_upper_ascii(uint8_t c) {
    return c >= 'a' && c <= 'z' ? c - 'a' + 'A' : c;
}

static inline void rqsort(void *base, size_t nmemb, size_t size,
            int (*cmp)(const void *, const void *, void *),
            void *arg);

static inline uint64_t float64_as_uint64(double d)
{
    union {
        double d;
        uint64_t u64;
    } u;
    u.d = d;
    return u.u64;
}

static inline double uint64_as_float64(uint64_t u64)
{
    union {
        double d;
        uint64_t u64;
    } u;
    u.u64 = u64;
    return u.d;
}

static inline int64_t js__gettimeofday_us(void);
static inline uint64_t js__hrtime_ns(void);

static inline size_t js__malloc_usable_size(const void *ptr)
{
#if defined(__APPLE__)
    return malloc_size(ptr);
#elif defined(_WIN32)
    return _msize((void *)ptr);
#elif defined(__linux__) || defined(__ANDROID__) || defined(__CYGWIN__) || defined(__FreeBSD__) || defined(__GLIBC__) || defined(__wasi__)
    return malloc_usable_size((void *)ptr);
#else
    return 0;
#endif
}

static inline int js_exepath(char* buffer, size_t* size);

/* Cross-platform threading APIs. */

#if defined(EMSCRIPTEN) || defined(__wasi__) || defined(__DJGPP)

#define JS_HAVE_THREADS 0

#else

#define JS_HAVE_THREADS 1

#if defined(_WIN32)
#define JS_ONCE_INIT INIT_ONCE_STATIC_INIT
typedef INIT_ONCE js_once_t;
typedef CRITICAL_SECTION js_mutex_t;
typedef CONDITION_VARIABLE js_cond_t;
typedef HANDLE js_thread_t;
#else
#define JS_ONCE_INIT PTHREAD_ONCE_INIT
typedef pthread_once_t js_once_t;
typedef pthread_mutex_t js_mutex_t;
typedef pthread_cond_t js_cond_t;
typedef pthread_t js_thread_t;
#endif

static inline void js_once(js_once_t *guard, void (*callback)(void));

static inline void js_mutex_init(js_mutex_t *mutex);
static inline void js_mutex_destroy(js_mutex_t *mutex);
static inline void js_mutex_lock(js_mutex_t *mutex);
static inline void js_mutex_unlock(js_mutex_t *mutex);

static inline void js_cond_init(js_cond_t *cond);
static inline void js_cond_destroy(js_cond_t *cond);
static inline void js_cond_signal(js_cond_t *cond);
static inline void js_cond_broadcast(js_cond_t *cond);
static inline void js_cond_wait(js_cond_t *cond, js_mutex_t *mutex);
static inline int js_cond_timedwait(js_cond_t *cond, js_mutex_t *mutex, uint64_t timeout);

enum {
    JS_THREAD_CREATE_DETACHED = 1,
};

// creates threads with 2 MB stacks (glibc default)
static inline int js_thread_create(js_thread_t *thrd, void (*start)(void *), void *arg,
                     int flags);
static inline int js_thread_join(js_thread_t thrd);

#endif /* !defined(EMSCRIPTEN) && !defined(__wasi__) */

// JS requires strict rounding behavior. Turn on 64-bits double precision
// and disable x87 80-bits extended precision for intermediate floating-point
// results. 0x300 is extended  precision, 0x200 is double precision.
// Note that `*&cw` in the asm constraints looks redundant but isn't.
#if defined(__i386__) && !defined(_MSC_VER)
#define JS_X87_FPCW_SAVE_AND_ADJUST(cw)                                     \
    (void)0;                                                                \
    unsigned short cw;                                                      \
    __asm__ __volatile__("fnstcw %0" : "=m"(*&cw));                         \
    do {                                                                    \
        unsigned short t = 0x200 | (cw & ~0x300);                           \
        __asm__ __volatile__("fldcw %0" : /*empty*/ : "m"(*&t));            \
    } while (0)
#define JS_X87_FPCW_RESTORE(cw)                                             \
    __asm__ __volatile__("fldcw %0" : /*empty*/ : "m"(*&cw))
#else
#define JS_X87_FPCW_SAVE_AND_ADJUST(cw)
#define JS_X87_FPCW_RESTORE(cw)
#endif

#undef NANOSEC
#define NANOSEC ((uint64_t) 1e9)

static inline void js__pstrcpy(char *buf, int buf_size, const char *str)
{
    int c;
    char *q = buf;

    if (buf_size <= 0)
        return;

    for(;;) {
        c = *str++;
        if (c == 0 || q >= buf + buf_size - 1)
            break;
        *q++ = c;
    }
    *q = '\0';
}

/* strcat and truncate. */
static inline char *js__pstrcat(char *buf, int buf_size, const char *s)
{
    int len;
    len = strlen(buf);
    if (len < buf_size)
        js__pstrcpy(buf + len, buf_size - len, s);
    return buf;
}

static inline int js__strstart(const char *str, const char *val, const char **ptr)
{
    const char *p, *q;
    p = str;
    q = val;
    while (*q != '\0') {
        if (*p != *q)
            return 0;
        p++;
        q++;
    }
    if (ptr)
        *ptr = p;
    return 1;
}

static inline int js__has_suffix(const char *str, const char *suffix)
{
    size_t len = strlen(str);
    size_t slen = strlen(suffix);
    return (len >= slen && !memcmp(str + len - slen, suffix, slen));
}

/* Dynamic buffer package */

static void *dbuf_default_realloc(void *opaque, void *ptr, size_t size)
{
    if (unlikely(size == 0)) {
        free(ptr);
        return NULL;
    }
    return realloc(ptr, size);
}

static inline void dbuf_init2(DynBuf *s, void *opaque, DynBufReallocFunc *realloc_func)
{
    memset(s, 0, sizeof(*s));
    if (!realloc_func)
        realloc_func = dbuf_default_realloc;
    s->opaque = opaque;
    s->realloc_func = realloc_func;
}

static inline void dbuf_init(DynBuf *s)
{
    dbuf_init2(s, NULL, NULL);
}

/* Try to allocate 'len' more bytes. return < 0 if error */
static inline int dbuf_claim(DynBuf *s, size_t len)
{
    size_t new_size, size, new_allocated_size;
    uint8_t *new_buf;
    new_size = s->size + len;
    if (new_size < len)
        return -1; /* overflow */
    if (new_size > s->allocated_size) {
        if (s->error)
            return -1;
        size = s->allocated_size + (s->allocated_size / 2);
        if (size < new_size || size < s->allocated_size) /* overflow test */
            new_allocated_size = new_size;
        else
            new_allocated_size = size;
        new_buf = s->realloc_func(s->opaque, s->buf, new_allocated_size);
        if (!new_buf) {
            s->error = true;
            return -1;
        }
        s->buf = new_buf;
        s->allocated_size = new_allocated_size;
    }
    return 0;
}

static inline int dbuf_put(DynBuf *s, const void *data, size_t len)
{
    if (unlikely((s->size + len) > s->allocated_size)) {
        if (dbuf_claim(s, len))
            return -1;
    }
    if (len > 0) {
        memcpy(s->buf + s->size, data, len);
        s->size += len;
    }
    return 0;
}

static inline int dbuf_put_self(DynBuf *s, size_t offset, size_t len)
{
    if (unlikely((s->size + len) > s->allocated_size)) {
        if (dbuf_claim(s, len))
            return -1;
    }
    if (len > 0) {
        memcpy(s->buf + s->size, s->buf + offset, len);
        s->size += len;
    }
    return 0;
}

static inline int __dbuf_putc(DynBuf *s, uint8_t c)
{
    return dbuf_put(s, &c, 1);
}

static inline int __dbuf_put_u16(DynBuf *s, uint16_t val)
{
    return dbuf_put(s, (uint8_t *)&val, 2);
}

static inline int __dbuf_put_u32(DynBuf *s, uint32_t val)
{
    return dbuf_put(s, (uint8_t *)&val, 4);
}

static inline int __dbuf_put_u64(DynBuf *s, uint64_t val)
{
    return dbuf_put(s, (uint8_t *)&val, 8);
}

static inline int dbuf_putstr(DynBuf *s, const char *str)
{
    return dbuf_put(s, (const uint8_t *)str, strlen(str));
}

static inline int JS_PRINTF_FORMAT_ATTR(2, 3) dbuf_printf(DynBuf *s, JS_PRINTF_FORMAT const char *fmt, ...)
{
    va_list ap;
    char buf[128];
    int len;

    va_start(ap, fmt);
    len = vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    if (len < (int)sizeof(buf)) {
        /* fast case */
        return dbuf_put(s, (uint8_t *)buf, len);
    } else {
        if (dbuf_claim(s, len + 1))
            return -1;
        va_start(ap, fmt);
        vsnprintf((char *)(s->buf + s->size), s->allocated_size - s->size,
                  fmt, ap);
        va_end(ap);
        s->size += len;
    }
    return 0;
}

static inline void dbuf_free(DynBuf *s)
{
    /* we test s->buf as a fail safe to avoid crashing if dbuf_free()
       is called twice */
    if (s->buf) {
        s->realloc_func(s->opaque, s->buf, 0);
    }
    memset(s, 0, sizeof(*s));
}

/*--- UTF-8 utility functions --*/

/* Note: only encode valid codepoints (0x0000..0x10FFFF).
   At most UTF8_CHAR_LEN_MAX bytes are output. */

/* Compute the number of bytes of the UTF-8 encoding for a codepoint
   `c` is a code-point.
   Returns the number of bytes. If a codepoint is beyond 0x10FFFF the
   return value is 3 as the codepoint would be encoded as 0xFFFD.
 */
static inline size_t utf8_encode_len(uint32_t c)
{
    if (c < 0x80)
        return 1;
    if (c < 0x800)
        return 2;
    if (c < 0x10000)
        return 3;
    if (c < 0x110000)
        return 4;
    return 3;
}

/* Encode a codepoint in UTF-8
   `buf` points to an array of at least `UTF8_CHAR_LEN_MAX` bytes
   `c` is a code-point.
   Returns the number of bytes. If a codepoint is beyond 0x10FFFF the
   return value is 3 and the codepoint is encoded as 0xFFFD.
   No null byte is stored after the encoded bytes.
   Return value is in range 1..4
 */
static inline size_t utf8_encode(uint8_t buf[minimum_length(UTF8_CHAR_LEN_MAX)], uint32_t c)
{
    if (c < 0x80) {
        buf[0] = c;
        return 1;
    }
    if (c < 0x800) {
        buf[0] = (c >> 6) | 0xC0;
        buf[1] = (c & 0x3F) | 0x80;
        return 2;
    }
    if (c < 0x10000) {
        buf[0] = (c >> 12) | 0xE0;
        buf[1] = ((c >> 6) & 0x3F) | 0x80;
        buf[2] = (c & 0x3F) | 0x80;
        return 3;
    }
    if (c < 0x110000) {
        buf[0] = (c >> 18) | 0xF0;
        buf[1] = ((c >> 12) & 0x3F) | 0x80;
        buf[2] = ((c >> 6) & 0x3F) | 0x80;
        buf[3] = (c & 0x3F) | 0x80;
        return 4;
    }
    buf[0] = (0xFFFD >> 12) | 0xE0;
    buf[1] = ((0xFFFD >> 6) & 0x3F) | 0x80;
    buf[2] = (0xFFFD & 0x3F) | 0x80;
    return 3;
}

/* Decode a single code point from a UTF-8 encoded array of bytes
   `p` is a valid pointer to an array of bytes
   `pp` is a valid pointer to a `const uint8_t *` to store a pointer
   to the byte following the current sequence.
   Return the code point at `p`, in the range `0..0x10FFFF`
   Return 0xFFFD on error. Only a single byte is consumed in this case
   The maximum length for a UTF-8 byte sequence is 4 bytes.
   This implements the algorithm specified in whatwg.org, except it accepts
   UTF-8 encoded surrogates as JavaScript allows them in strings.
   The source string is assumed to have at least UTF8_CHAR_LEN_MAX bytes
   or be null terminated.
   If `p[0]` is '\0', the return value is `0` and the byte is consumed.
   cf: https://encoding.spec.whatwg.org/#utf-8-encoder
 */
static inline uint32_t utf8_decode(const uint8_t *p, const uint8_t **pp)
{
    uint32_t c;
    uint8_t lower, upper;

    c = *p++;
    if (c < 0x80) {
        *pp = p;
        return c;
    }
    switch(c) {
    case 0xC2: case 0xC3:
    case 0xC4: case 0xC5: case 0xC6: case 0xC7:
    case 0xC8: case 0xC9: case 0xCA: case 0xCB:
    case 0xCC: case 0xCD: case 0xCE: case 0xCF:
    case 0xD0: case 0xD1: case 0xD2: case 0xD3:
    case 0xD4: case 0xD5: case 0xD6: case 0xD7:
    case 0xD8: case 0xD9: case 0xDA: case 0xDB:
    case 0xDC: case 0xDD: case 0xDE: case 0xDF:
        if (*p >= 0x80 && *p <= 0xBF) {
            *pp = p + 1;
            return ((c - 0xC0) << 6) + (*p - 0x80);
        }
        // otherwise encoding error
        break;
    case 0xE0:
        lower = 0xA0;   /* reject invalid encoding */
        goto need2;
    case 0xE1: case 0xE2: case 0xE3:
    case 0xE4: case 0xE5: case 0xE6: case 0xE7:
    case 0xE8: case 0xE9: case 0xEA: case 0xEB:
    case 0xEC: case 0xED: case 0xEE: case 0xEF:
        lower = 0x80;
    need2:
        if (*p >= lower && *p <= 0xBF && p[1] >= 0x80 && p[1] <= 0xBF) {
            *pp = p + 2;
            return ((c - 0xE0) << 12) + ((*p - 0x80) << 6) + (p[1] - 0x80);
        }
        // otherwise encoding error
        break;
    case 0xF0:
        lower = 0x90;   /* reject invalid encoding */
        upper = 0xBF;
        goto need3;
    case 0xF4:
        lower = 0x80;
        upper = 0x8F;   /* reject values above 0x10FFFF */
        goto need3;
    case 0xF1: case 0xF2: case 0xF3:
        lower = 0x80;
        upper = 0xBF;
    need3:
        if (*p >= lower && *p <= upper && p[1] >= 0x80 && p[1] <= 0xBF
        &&  p[2] >= 0x80 && p[2] <= 0xBF) {
            *pp = p + 3;
            return ((c - 0xF0) << 18) + ((*p - 0x80) << 12) +
                ((p[1] - 0x80) << 6) + (p[2] - 0x80);
        }
        // otherwise encoding error
        break;
    default:
        // invalid lead byte
        break;
    }
    *pp = p;
    return 0xFFFD;
}

static inline uint32_t utf8_decode_len(const uint8_t *p, size_t max_len, const uint8_t **pp) {
    switch (max_len) {
    case 0:
        *pp = p;
        return 0xFFFD;
    case 1:
        if (*p < 0x80)
            goto good;
        break;
    case 2:
        if (*p < 0xE0)
            goto good;
        break;
    case 3:
        if (*p < 0xF0)
            goto good;
        break;
    default:
    good:
        return utf8_decode(p, pp);
    }
    *pp = p + 1;
    return 0xFFFD;
}

/* Scan a UTF-8 encoded buffer for content type
   `buf` is a valid pointer to a UTF-8 encoded string
   `len` is the number of bytes to scan
   `plen` points to a `size_t` variable to receive the number of units
   Return value is a mask of bits.
   - `UTF8_PLAIN_ASCII`: return value for 7-bit ASCII plain text
   - `UTF8_NON_ASCII`: bit for non ASCII code points (8-bit or more)
   - `UTF8_HAS_16BIT`: bit for 16-bit code points
   - `UTF8_HAS_NON_BMP1`: bit for non-BMP1 code points, needs UTF-16 surrogate pairs
   - `UTF8_HAS_ERRORS`: bit for encoding errors
 */
static inline int utf8_scan(const char *buf, size_t buf_len, size_t *plen)
{
    const uint8_t *p, *p_end, *p_next;
    size_t i, len;
    int kind;
    uint8_t cbits;

    kind = UTF8_PLAIN_ASCII;
    cbits = 0;
    len = buf_len;
    // TODO: handle more than 1 byte at a time
    for (i = 0; i < buf_len; i++)
        cbits |= buf[i];
    if (cbits >= 0x80) {
        p = (const uint8_t *)buf;
        p_end = p + buf_len;
        kind = UTF8_NON_ASCII;
        len = 0;
        while (p < p_end) {
            len++;
            if (*p++ >= 0x80) {
                /* parse UTF-8 sequence, check for encoding error */
                uint32_t c = utf8_decode_len(p - 1, p_end - (p - 1), &p_next);
                if (p_next == p)
                    kind |= UTF8_HAS_ERRORS;
                p = p_next;
                if (c > 0xFF) {
                    kind |= UTF8_HAS_16BIT;
                    if (c > 0xFFFF) {
                        len++;
                        kind |= UTF8_HAS_NON_BMP1;
                    }
                }
            }
        }
    }
    *plen = len;
    return kind;
}

/* Decode a string encoded in UTF-8 into an array of bytes
   `src` points to the source string. It is assumed to be correctly encoded
   and only contains code points below 0x800
   `src_len` is the length of the source string
   `dest` points to the destination array, it can be null if `dest_len` is `0`
   `dest_len` is the length of the destination array. A null
   terminator is stored at the end of the array unless `dest_len` is `0`.
 */
static inline size_t utf8_decode_buf8(uint8_t *dest, size_t dest_len, const char *src, size_t src_len)
{
    const uint8_t *p, *p_end;
    size_t i;

    p = (const uint8_t *)src;
    p_end = p + src_len;
    for (i = 0; p < p_end; i++) {
        uint32_t c = *p++;
        if (c >= 0xC0)
            c = (c << 6) + *p++ - ((0xC0 << 6) + 0x80);
        if (i < dest_len)
            dest[i] = c;
    }
    if (i < dest_len)
        dest[i] = '\0';
    else if (dest_len > 0)
        dest[dest_len - 1] = '\0';
    return i;
}

/* Decode a string encoded in UTF-8 into an array of 16-bit words
   `src` points to the source string. It is assumed to be correctly encoded.
   `src_len` is the length of the source string
   `dest` points to the destination array, it can be null if `dest_len` is `0`
   `dest_len` is the length of the destination array. No null terminator is
   stored at the end of the array.
 */
static inline size_t utf8_decode_buf16(uint16_t *dest, size_t dest_len, const char *src, size_t src_len)
{
    const uint8_t *p, *p_end;
    size_t i;

    p = (const uint8_t *)src;
    p_end = p + src_len;
    for (i = 0; p < p_end; i++) {
        uint32_t c = *p++;
        if (c >= 0x80) {
            /* parse utf-8 sequence */
            c = utf8_decode_len(p - 1, p_end - (p - 1), &p);
            /* encoding errors are converted as 0xFFFD and use a single byte */
            if (c > 0xFFFF) {
                if (i < dest_len)
                    dest[i] = get_hi_surrogate(c);
                i++;
                c = get_lo_surrogate(c);
            }
        }
        if (i < dest_len)
            dest[i] = c;
    }
    return i;
}

/* Encode a buffer of 8-bit bytes as a UTF-8 encoded string
   `src` points to the source buffer.
   `src_len` is the length of the source buffer
   `dest` points to the destination array, it can be null if `dest_len` is `0`
   `dest_len` is the length in bytes of the destination array. A null
   terminator is stored at the end of the array unless `dest_len` is `0`.
 */
static inline size_t utf8_encode_buf8(char *dest, size_t dest_len, const uint8_t *src, size_t src_len)
{
    size_t i, j;
    uint32_t c;

    for (i = j = 0; i < src_len; i++) {
        c = src[i];
        if (c < 0x80) {
            if (j + 1 >= dest_len)
                goto overflow;
            dest[j++] = c;
        } else {
            if (j + 2 >= dest_len)
                goto overflow;
            dest[j++] = (c >> 6) | 0xC0;
            dest[j++] = (c & 0x3F) | 0x80;
        }
    }
    if (j < dest_len)
        dest[j] = '\0';
    return j;

overflow:
    if (j < dest_len)
        dest[j] = '\0';
    while (i < src_len)
        j += 1 + (src[i++] >= 0x80);
    return j;
}

/* Encode a buffer of 16-bit code points as a UTF-8 encoded string
   `src` points to the source buffer.
   `src_len` is the length of the source buffer
   `dest` points to the destination array, it can be null if `dest_len` is `0`
   `dest_len` is the length in bytes of the destination array. A null
   terminator is stored at the end of the array unless `dest_len` is `0`.
 */
static inline size_t utf8_encode_buf16(char *dest, size_t dest_len, const uint16_t *src, size_t src_len)
{
    size_t i, j;
    uint32_t c;

    for (i = j = 0; i < src_len;) {
        c = src[i++];
        if (c < 0x80) {
            if (j + 1 >= dest_len)
                goto overflow;
            dest[j++] = c;
        } else {
            if (is_hi_surrogate(c) && i < src_len && is_lo_surrogate(src[i]))
                c = from_surrogate(c, src[i++]);
            if (j + utf8_encode_len(c) >= dest_len)
                goto overflow;
            j += utf8_encode((uint8_t *)dest + j, c);
        }
    }
    if (j < dest_len)
        dest[j] = '\0';
    return j;

overflow:
    i -= 1 + (c > 0xFFFF);
    if (j < dest_len)
        dest[j] = '\0';
    while (i < src_len) {
        c = src[i++];
        if (c < 0x80) {
            j++;
        } else {
            if (is_hi_surrogate(c) && i < src_len && is_lo_surrogate(src[i]))
                c = from_surrogate(c, src[i++]);
            j += utf8_encode_len(c);
        }
    }
    return j;
}

/*---- sorting with opaque argument ----*/

typedef void (*exchange_f)(void *a, void *b, size_t size);
typedef int (*cmp_f)(const void *, const void *, void *opaque);

static void exchange_bytes(void *a, void *b, size_t size) {
    uint8_t *ap = (uint8_t *)a;
    uint8_t *bp = (uint8_t *)b;

    while (size-- != 0) {
        uint8_t t = *ap;
        *ap++ = *bp;
        *bp++ = t;
    }
}

static void exchange_one_byte(void *a, void *b, size_t size) {
    uint8_t *ap = (uint8_t *)a;
    uint8_t *bp = (uint8_t *)b;
    uint8_t t = *ap;
    *ap = *bp;
    *bp = t;
}

static void exchange_int16s(void *a, void *b, size_t size) {
    uint16_t *ap = (uint16_t *)a;
    uint16_t *bp = (uint16_t *)b;

    for (size /= sizeof(uint16_t); size-- != 0;) {
        uint16_t t = *ap;
        *ap++ = *bp;
        *bp++ = t;
    }
}

static void exchange_one_int16(void *a, void *b, size_t size) {
    uint16_t *ap = (uint16_t *)a;
    uint16_t *bp = (uint16_t *)b;
    uint16_t t = *ap;
    *ap = *bp;
    *bp = t;
}

static void exchange_int32s(void *a, void *b, size_t size) {
    uint32_t *ap = (uint32_t *)a;
    uint32_t *bp = (uint32_t *)b;

    for (size /= sizeof(uint32_t); size-- != 0;) {
        uint32_t t = *ap;
        *ap++ = *bp;
        *bp++ = t;
    }
}

static void exchange_one_int32(void *a, void *b, size_t size) {
    uint32_t *ap = (uint32_t *)a;
    uint32_t *bp = (uint32_t *)b;
    uint32_t t = *ap;
    *ap = *bp;
    *bp = t;
}

static void exchange_int64s(void *a, void *b, size_t size) {
    uint64_t *ap = (uint64_t *)a;
    uint64_t *bp = (uint64_t *)b;

    for (size /= sizeof(uint64_t); size-- != 0;) {
        uint64_t t = *ap;
        *ap++ = *bp;
        *bp++ = t;
    }
}

static void exchange_one_int64(void *a, void *b, size_t size) {
    uint64_t *ap = (uint64_t *)a;
    uint64_t *bp = (uint64_t *)b;
    uint64_t t = *ap;
    *ap = *bp;
    *bp = t;
}

static void exchange_int128s(void *a, void *b, size_t size) {
    uint64_t *ap = (uint64_t *)a;
    uint64_t *bp = (uint64_t *)b;

    for (size /= sizeof(uint64_t) * 2; size-- != 0; ap += 2, bp += 2) {
        uint64_t t = ap[0];
        uint64_t u = ap[1];
        ap[0] = bp[0];
        ap[1] = bp[1];
        bp[0] = t;
        bp[1] = u;
    }
}

static void exchange_one_int128(void *a, void *b, size_t size) {
    uint64_t *ap = (uint64_t *)a;
    uint64_t *bp = (uint64_t *)b;
    uint64_t t = ap[0];
    uint64_t u = ap[1];
    ap[0] = bp[0];
    ap[1] = bp[1];
    bp[0] = t;
    bp[1] = u;
}

static inline exchange_f exchange_func(const void *base, size_t size) {
    switch (((uintptr_t)base | (uintptr_t)size) & 15) {
    case 0:
        if (size == sizeof(uint64_t) * 2)
            return exchange_one_int128;
        else
            return exchange_int128s;
    case 8:
        if (size == sizeof(uint64_t))
            return exchange_one_int64;
        else
            return exchange_int64s;
    case 4:
    case 12:
        if (size == sizeof(uint32_t))
            return exchange_one_int32;
        else
            return exchange_int32s;
    case 2:
    case 6:
    case 10:
    case 14:
        if (size == sizeof(uint16_t))
            return exchange_one_int16;
        else
            return exchange_int16s;
    default:
        if (size == 1)
            return exchange_one_byte;
        else
            return exchange_bytes;
    }
}

static void heapsortx(void *base, size_t nmemb, size_t size, cmp_f cmp, void *opaque)
{
    uint8_t *basep = (uint8_t *)base;
    size_t i, n, c, r;
    exchange_f swap = exchange_func(base, size);

    if (nmemb > 1) {
        i = (nmemb / 2) * size;
        n = nmemb * size;

        while (i > 0) {
            i -= size;
            for (r = i; (c = r * 2 + size) < n; r = c) {
                if (c < n - size && cmp(basep + c, basep + c + size, opaque) <= 0)
                    c += size;
                if (cmp(basep + r, basep + c, opaque) > 0)
                    break;
                swap(basep + r, basep + c, size);
            }
        }
        for (i = n - size; i > 0; i -= size) {
            swap(basep, basep + i, size);

            for (r = 0; (c = r * 2 + size) < i; r = c) {
                if (c < i - size && cmp(basep + c, basep + c + size, opaque) <= 0)
                    c += size;
                if (cmp(basep + r, basep + c, opaque) > 0)
                    break;
                swap(basep + r, basep + c, size);
            }
        }
    }
}

static inline void *med3(void *a, void *b, void *c, cmp_f cmp, void *opaque)
{
    return cmp(a, b, opaque) < 0 ?
        (cmp(b, c, opaque) < 0 ? b : (cmp(a, c, opaque) < 0 ? c : a )) :
        (cmp(b, c, opaque) > 0 ? b : (cmp(a, c, opaque) < 0 ? a : c ));
}

/* pointer based version with local stack and insertion sort threshhold */
static inline void rqsort(void *base, size_t nmemb, size_t size, cmp_f cmp, void *opaque)
{
    struct { uint8_t *base; size_t count; int depth; } stack[50], *sp = stack;
    uint8_t *ptr, *pi, *pj, *plt, *pgt, *top, *m;
    size_t m4, i, lt, gt, span, span2;
    int c, depth;
    exchange_f swap = exchange_func(base, size);
    exchange_f swap_block = exchange_func(base, size | 128);

    if (nmemb < 2 || size <= 0)
        return;

    sp->base = (uint8_t *)base;
    sp->count = nmemb;
    sp->depth = 0;
    sp++;

    while (sp > stack) {
        sp--;
        ptr = sp->base;
        nmemb = sp->count;
        depth = sp->depth;

        while (nmemb > 6) {
            if (++depth > 50) {
                /* depth check to ensure worst case logarithmic time */
                heapsortx(ptr, nmemb, size, cmp, opaque);
                nmemb = 0;
                break;
            }
            /* select median of 3 from 1/4, 1/2, 3/4 positions */
            /* should use median of 5 or 9? */
            m4 = (nmemb >> 2) * size;
            m = med3(ptr + m4, ptr + 2 * m4, ptr + 3 * m4, cmp, opaque);
            swap(ptr, m, size);  /* move the pivot to the start or the array */
            i = lt = 1;
            pi = plt = ptr + size;
            gt = nmemb;
            pj = pgt = top = ptr + nmemb * size;
            for (;;) {
                while (pi < pj && (c = cmp(ptr, pi, opaque)) >= 0) {
                    if (c == 0) {
                        swap(plt, pi, size);
                        lt++;
                        plt += size;
                    }
                    i++;
                    pi += size;
                }
                while (pi < (pj -= size) && (c = cmp(ptr, pj, opaque)) <= 0) {
                    if (c == 0) {
                        gt--;
                        pgt -= size;
                        swap(pgt, pj, size);
                    }
                }
                if (pi >= pj)
                    break;
                swap(pi, pj, size);
                i++;
                pi += size;
            }
            /* array has 4 parts:
             * from 0 to lt excluded: elements identical to pivot
             * from lt to pi excluded: elements smaller than pivot
             * from pi to gt excluded: elements greater than pivot
             * from gt to n excluded: elements identical to pivot
             */
            /* move elements identical to pivot in the middle of the array: */
            /* swap values in ranges [0..lt[ and [i-lt..i[
               swapping the smallest span between lt and i-lt is sufficient
             */
            span = plt - ptr;
            span2 = pi - plt;
            lt = i - lt;
            if (span > span2)
                span = span2;
            swap_block(ptr, pi - span, span);
            /* swap values in ranges [gt..top[ and [i..top-(top-gt)[
               swapping the smallest span between top-gt and gt-i is sufficient
             */
            span = top - pgt;
            span2 = pgt - pi;
            pgt = top - span2;
            gt = nmemb - (gt - i);
            if (span > span2)
                span = span2;
            swap_block(pi, top - span, span);

            /* now array has 3 parts:
             * from 0 to lt excluded: elements smaller than pivot
             * from lt to gt excluded: elements identical to pivot
             * from gt to n excluded: elements greater than pivot
             */
            /* stack the larger segment and keep processing the smaller one
               to minimize stack use for pathological distributions */
            if (lt > nmemb - gt) {
                sp->base = ptr;
                sp->count = lt;
                sp->depth = depth;
                sp++;
                ptr = pgt;
                nmemb -= gt;
            } else {
                sp->base = pgt;
                sp->count = nmemb - gt;
                sp->depth = depth;
                sp++;
                nmemb = lt;
            }
        }
        /* Use insertion sort for small fragments */
        for (pi = ptr + size, top = ptr + nmemb * size; pi < top; pi += size) {
            for (pj = pi; pj > ptr && cmp(pj - size, pj, opaque) > 0; pj -= size)
                swap(pj, pj - size, size);
        }
    }
}

/*---- Portable time functions ----*/

#ifdef _WIN32
 // From: https://stackoverflow.com/a/26085827
static int gettimeofday_msvc(struct timeval *tp)
{
  static const uint64_t EPOCH = ((uint64_t)116444736000000000ULL);

  SYSTEMTIME  system_time;
  FILETIME    file_time;
  uint64_t    time;

  GetSystemTime(&system_time);
  SystemTimeToFileTime(&system_time, &file_time);
  time = ((uint64_t)file_time.dwLowDateTime);
  time += ((uint64_t)file_time.dwHighDateTime) << 32;

  tp->tv_sec = (long)((time - EPOCH) / 10000000L);
  tp->tv_usec = (long)(system_time.wMilliseconds * 1000);

  return 0;
}

static inline uint64_t js__hrtime_ns(void) {
    LARGE_INTEGER counter, frequency;
    double scaled_freq;
    double result;

    if (!QueryPerformanceFrequency(&frequency))
        abort();
    assert(frequency.QuadPart != 0);

    if (!QueryPerformanceCounter(&counter))
        abort();
    assert(counter.QuadPart != 0);

  /* Because we have no guarantee about the order of magnitude of the
   * performance counter interval, integer math could cause this computation
   * to overflow. Therefore we resort to floating point math.
   */
  scaled_freq = (double) frequency.QuadPart / NANOSEC;
  result = (double) counter.QuadPart / scaled_freq;
  return (uint64_t) result;
}
#else
static inline uint64_t js__hrtime_ns(void) {
#ifdef __DJGPP
  struct timeval tv;
  if (gettimeofday(&tv, NULL))
    abort();
  return tv.tv_sec * NANOSEC + tv.tv_usec * 1000;
#else
  struct timespec t;

  if (clock_gettime(CLOCK_MONOTONIC, &t))
    abort();

  return t.tv_sec * NANOSEC + t.tv_nsec;
#endif
}
#endif

static inline int64_t js__gettimeofday_us(void) {
    struct timeval tv;
#ifdef _WIN32
    gettimeofday_msvc(&tv);
#else
    gettimeofday(&tv, NULL);
#endif
    return ((int64_t)tv.tv_sec * 1000000) + tv.tv_usec;
}

#if defined(_WIN32)
static inline int js_exepath(char *buffer, size_t *size_ptr) {
    int utf8_len, utf16_buffer_len, utf16_len;
    WCHAR* utf16_buffer;

    if (buffer == NULL || size_ptr == NULL || *size_ptr == 0)
      return -1;

    if (*size_ptr > 32768) {
      /* Windows paths can never be longer than this. */
      utf16_buffer_len = 32768;
    } else {
      utf16_buffer_len = (int)*size_ptr;
    }

    utf16_buffer = malloc(sizeof(WCHAR) * utf16_buffer_len);
    if (!utf16_buffer)
        return -1;

    /* Get the path as UTF-16. */
    utf16_len = GetModuleFileNameW(NULL, utf16_buffer, utf16_buffer_len);
    if (utf16_len <= 0)
      goto error;

    /* Convert to UTF-8 */
    utf8_len = WideCharToMultiByte(CP_UTF8,
                                   0,
                                   utf16_buffer,
                                   -1,
                                   buffer,
                                   (int)*size_ptr,
                                   NULL,
                                   NULL);
    if (utf8_len == 0)
      goto error;

    free(utf16_buffer);

    /* utf8_len *does* include the terminating null at this point, but the
     * returned size shouldn't. */
    *size_ptr = utf8_len - 1;
    return 0;

error:
    free(utf16_buffer);
    return -1;
}
#elif defined(__APPLE__)
static inline int js_exepath(char *buffer, size_t *size) {
    /* realpath(exepath) may be > PATH_MAX so double it to be on the safe side. */
    char abspath[PATH_MAX * 2 + 1];
    char exepath[PATH_MAX + 1];
    uint32_t exepath_size;
    size_t abspath_size;

    if (buffer == NULL || size == NULL || *size == 0)
        return -1;

    exepath_size = sizeof(exepath);
    if (_NSGetExecutablePath(exepath, &exepath_size))
        return -1;

    if (realpath(exepath, abspath) != abspath)
        return -1;

    abspath_size = strlen(abspath);
    if (abspath_size == 0)
        return -1;

    *size -= 1;
    if (*size > abspath_size)
        *size = abspath_size;

    memcpy(buffer, abspath, *size);
    buffer[*size] = '\0';

    return 0;
}
#elif defined(__linux__) || defined(__GNU__)
static inline int js_exepath(char *buffer, size_t *size) {
    ssize_t n;

    if (buffer == NULL || size == NULL || *size == 0)
        return -1;

    n = *size - 1;
    if (n > 0)
        n = readlink("/proc/self/exe", buffer, n);

    if (n == -1)
        return n;

    buffer[n] = '\0';
    *size = n;

    return 0;
}
#else
static inline int js_exepath(char* buffer, size_t* size_ptr) {
    return -1;
}
#endif

/*--- Cross-platform threading APIs. ----*/

#if JS_HAVE_THREADS
#if defined(_WIN32)
typedef void (*js__once_cb)(void);

typedef struct {
    js__once_cb callback;
} js__once_data_t;

static int WINAPI js__once_inner(INIT_ONCE *once, void *param, void **context) {
    js__once_data_t *data = param;

    data->callback();

    return 1;
}

static inline void js_once(js_once_t *guard, js__once_cb callback) {
    js__once_data_t data = { .callback = callback };
    InitOnceExecuteOnce(guard, js__once_inner, (void*) &data, NULL);
}

static inline void js_mutex_init(js_mutex_t *mutex) {
    InitializeCriticalSection(mutex);
}

static inline void js_mutex_destroy(js_mutex_t *mutex) {
    DeleteCriticalSection(mutex);
}

static inline void js_mutex_lock(js_mutex_t *mutex) {
    EnterCriticalSection(mutex);
}

static inline void js_mutex_unlock(js_mutex_t *mutex) {
    LeaveCriticalSection(mutex);
}

static inline void js_cond_init(js_cond_t *cond) {
    InitializeConditionVariable(cond);
}

static inline void js_cond_destroy(js_cond_t *cond) {
  /* nothing to do */
  (void) cond;
}

static inline void js_cond_signal(js_cond_t *cond) {
    WakeConditionVariable(cond);
}

static inline void js_cond_broadcast(js_cond_t *cond) {
    WakeAllConditionVariable(cond);
}

static inline void js_cond_wait(js_cond_t *cond, js_mutex_t *mutex) {
    if (!SleepConditionVariableCS(cond, mutex, INFINITE))
        abort();
}

static inline int js_cond_timedwait(js_cond_t *cond, js_mutex_t *mutex, uint64_t timeout) {
    if (SleepConditionVariableCS(cond, mutex, (DWORD)(timeout / 1e6)))
        return 0;
    if (GetLastError() != ERROR_TIMEOUT)
        abort();
    return -1;
}

static inline int js_thread_create(js_thread_t *thrd, void (*start)(void *), void *arg,
                     int flags)
{
    HANDLE h, cp;

    *thrd = INVALID_HANDLE_VALUE;
    if (flags & ~JS_THREAD_CREATE_DETACHED)
        return -1;
    h = (HANDLE)_beginthread(start, /*stacksize*/2<<20, arg);
    if (!h)
        return -1;
    if (flags & JS_THREAD_CREATE_DETACHED)
        return 0;
    // _endthread() automatically closes the handle but we want to wait on
    // it so make a copy. Race-y for very short-lived threads. Can be solved
    // by switching to _beginthreadex(CREATE_SUSPENDED) but means changing
    // |start| from __cdecl to __stdcall.
    cp = GetCurrentProcess();
    if (DuplicateHandle(cp, h, cp, thrd, 0, FALSE, DUPLICATE_SAME_ACCESS))
        return 0;
    return -1;
}

static inline int js_thread_join(js_thread_t thrd)
{
    if (WaitForSingleObject(thrd, INFINITE))
        return -1;
    CloseHandle(thrd);
    return 0;
}

#else /* !defined(_WIN32) */

static inline void js_once(js_once_t *guard, void (*callback)(void)) {
    if (pthread_once(guard, callback))
        abort();
}

static inline void js_mutex_init(js_mutex_t *mutex) {
    if (pthread_mutex_init(mutex, NULL))
        abort();
}

static inline void js_mutex_destroy(js_mutex_t *mutex) {
    if (pthread_mutex_destroy(mutex))
        abort();
}

static inline void js_mutex_lock(js_mutex_t *mutex) {
    if (pthread_mutex_lock(mutex))
        abort();
}

static inline void js_mutex_unlock(js_mutex_t *mutex) {
    if (pthread_mutex_unlock(mutex))
        abort();
}

static inline void js_cond_init(js_cond_t *cond) {
#if defined(__APPLE__) && defined(__MACH__)
    if (pthread_cond_init(cond, NULL))
        abort();
#else
    pthread_condattr_t attr;

    if (pthread_condattr_init(&attr))
        abort();

    if (pthread_condattr_setclock(&attr, CLOCK_MONOTONIC))
        abort();

    if (pthread_cond_init(cond, &attr))
        abort();

    if (pthread_condattr_destroy(&attr))
        abort();
#endif
}

static inline void js_cond_destroy(js_cond_t *cond) {
#if defined(__APPLE__) && defined(__MACH__)
    /* It has been reported that destroying condition variables that have been
     * signalled but not waited on can sometimes result in application crashes.
     * See https://codereview.chromium.org/1323293005.
     */
    pthread_mutex_t mutex;
    struct timespec ts;
    int err;

    if (pthread_mutex_init(&mutex, NULL))
        abort();

    if (pthread_mutex_lock(&mutex))
        abort();

    ts.tv_sec = 0;
    ts.tv_nsec = 1;

    err = pthread_cond_timedwait_relative_np(cond, &mutex, &ts);
    if (err != 0 && err != ETIMEDOUT)
        abort();

    if (pthread_mutex_unlock(&mutex))
        abort();

    if (pthread_mutex_destroy(&mutex))
        abort();
#endif /* defined(__APPLE__) && defined(__MACH__) */

    if (pthread_cond_destroy(cond))
        abort();
}

static inline void js_cond_signal(js_cond_t *cond) {
    if (pthread_cond_signal(cond))
        abort();
}

static inline void js_cond_broadcast(js_cond_t *cond) {
    if (pthread_cond_broadcast(cond))
        abort();
}

static inline void js_cond_wait(js_cond_t *cond, js_mutex_t *mutex) {
#if defined(__APPLE__) && defined(__MACH__)
    int r;

    errno = 0;
    r = pthread_cond_wait(cond, mutex);

    /* Workaround for a bug in OS X at least up to 13.6
     * See https://github.com/libuv/libuv/issues/4165
     */
    if (r == EINVAL && errno == EBUSY)
        return;
    if (r)
        abort();
#else
    if (pthread_cond_wait(cond, mutex))
        abort();
#endif
}

static inline int js_cond_timedwait(js_cond_t *cond, js_mutex_t *mutex, uint64_t timeout) {
    int r;
    struct timespec ts;

#if !defined(__APPLE__)
    timeout += js__hrtime_ns();
#endif

    ts.tv_sec = timeout / NANOSEC;
    ts.tv_nsec = timeout % NANOSEC;
#if defined(__APPLE__) && defined(__MACH__)
    r = pthread_cond_timedwait_relative_np(cond, mutex, &ts);
#else
    r = pthread_cond_timedwait(cond, mutex, &ts);
#endif

    if (r == 0)
        return 0;

    if (r == ETIMEDOUT)
        return -1;

    abort();

    /* Pacify some compilers. */
    return -1;
}

static inline int js_thread_create(js_thread_t *thrd, void (*start)(void *), void *arg,
                     int flags)
{
    union {
        void (*x)(void *);
        void *(*f)(void *);
    } u = {start};
    pthread_attr_t attr;
    int ret;

    if (flags & ~JS_THREAD_CREATE_DETACHED)
        return -1;
    if (pthread_attr_init(&attr))
        return -1;
    ret = -1;
    if (pthread_attr_setstacksize(&attr, 2<<20))
        goto fail;
    if (flags & JS_THREAD_CREATE_DETACHED)
        if (pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_DETACHED))
            goto fail;
    if (pthread_create(thrd, &attr, u.f, arg))
        goto fail;
    ret = 0;
fail:
    pthread_attr_destroy(&attr);
    return ret;
}

static inline int js_thread_join(js_thread_t thrd)
{
    if (pthread_join(thrd, NULL))
        return -1;
    return 0;
}

#endif /* !defined(_WIN32) */
#endif /* JS_HAVE_THREADS */

#ifdef __cplusplus
} /* extern "C" { */
#endif

#endif  /* CUTILS_H */
