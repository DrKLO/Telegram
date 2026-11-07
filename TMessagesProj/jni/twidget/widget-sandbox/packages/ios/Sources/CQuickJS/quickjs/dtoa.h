/*
 * Tiny float64 printing and parsing library
 *
 * Copyright (c) 2024 Fabrice Bellard
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
#ifndef DTOA_H
#define DTOA_H

//#define JS_DTOA_DUMP_STATS

/* maximum number of digits for fixed and frac formats */
#define JS_DTOA_MAX_DIGITS 101

/* radix != 10 is only supported with flags = JS_DTOA_FORMAT_FREE */
/* use as many digits as necessary */
#define JS_DTOA_FORMAT_FREE  (0 << 0)
/* use n_digits significant digits (1 <= n_digits <= JS_DTOA_MAX_DIGITS) */
#define JS_DTOA_FORMAT_FIXED (1 << 0)
/* force fractional format: [-]dd.dd with n_digits fractional digits.
   0 <= n_digits <= JS_DTOA_MAX_DIGITS */
#define JS_DTOA_FORMAT_FRAC  (2 << 0)
#define JS_DTOA_FORMAT_MASK  (3 << 0)

/* select exponential notation either in fixed or free format */
#define JS_DTOA_EXP_AUTO     (0 << 2)
#define JS_DTOA_EXP_ENABLED  (1 << 2)
#define JS_DTOA_EXP_DISABLED (2 << 2)
#define JS_DTOA_EXP_MASK     (3 << 2)

#define JS_DTOA_MINUS_ZERO   (1 << 4) /* show the minus sign for -0 */

/* only accepts integers (no dot, no exponent) */
#define JS_ATOD_INT_ONLY       (1 << 0)
/* accept Oo and Ob prefixes in addition to 0x prefix if radix = 0 */
#define JS_ATOD_ACCEPT_BIN_OCT (1 << 1)
/* accept O prefix as octal if radix == 0 and properly formed (Annex B) */
#define JS_ATOD_ACCEPT_LEGACY_OCTAL  (1 << 2)
/* accept _ between digits as a digit separator */
#define JS_ATOD_ACCEPT_UNDERSCORES  (1 << 3)

typedef struct {
    uint64_t mem[37];
} JSDTOATempMem;

typedef struct {
    uint64_t mem[27];
} JSATODTempMem;

/* return a maximum bound of the string length */
int js_dtoa_max_len(double d, int radix, int n_digits, int flags);
/* return the string length */
int js_dtoa(char *buf, double d, int radix, int n_digits, int flags,
            JSDTOATempMem *tmp_mem);
double js_atod(const char *str, const char **pnext, int radix, int flags,
               JSATODTempMem *tmp_mem);

#ifdef JS_DTOA_DUMP_STATS
void js_dtoa_dump_stats(void);
#endif

/* additional exported functions */
size_t u32toa(char *buf, uint32_t n);
size_t i32toa(char *buf, int32_t n);
size_t u64toa(char *buf, uint64_t n);
size_t i64toa(char *buf, int64_t n);
size_t u64toa_radix(char *buf, uint64_t n, unsigned int radix);
size_t i64toa_radix(char *buf, int64_t n, unsigned int radix);

#endif /* DTOA_H */
