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
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <inttypes.h>
#include <string.h>
#include <assert.h>
#include <ctype.h>
// #include <sys/time.h>
#include <math.h>
// #include <setjmp.h>

#include "cutils.h"
#include "dtoa.h"

/* 
   TODO:
   - simplify subnormal handling
   - reduce max memory usage
   - free format: could add shortcut if exact result
   - use 64 bit limb_t when possible
   - use another algorithm for free format dtoa in base 10 (ryu ?)
*/

#define USE_POW5_TABLE
/* use fast path to print small integers in free format */
#define USE_FAST_INT

#define LIMB_LOG2_BITS 5

#define LIMB_BITS (1 << LIMB_LOG2_BITS)

typedef int32_t slimb_t;
typedef uint32_t limb_t;
typedef uint64_t dlimb_t;

#define LIMB_DIGITS 9

#define JS_RADIX_MAX 36

#define DBIGNUM_LEN_MAX 52 /* ~ 2^(1072+53)*36^100 (dtoa) */
#define MANT_LEN_MAX 18 /* < 36^100 */

typedef intptr_t mp_size_t;

/* the represented number is sum(i, tab[i]*2^(LIMB_BITS * i)) */
typedef struct {
    int len; /* >= 1 */
    limb_t tab[];
} mpb_t;

static limb_t mp_add_ui(limb_t *tab, limb_t b, size_t n)
{
    size_t i;
    limb_t k, a;

    k=b;
    for(i=0;i<n;i++) {
        if (k == 0)
            break;
        a = tab[i] + k;
        k = (a < k);
        tab[i] = a;
    }
    return k;
}

/* tabr[] = taba[] * b + l. Return the high carry */
static limb_t mp_mul1(limb_t *tabr, const limb_t *taba, limb_t n, 
                      limb_t b, limb_t l)
{
    limb_t i;
    dlimb_t t;

    for(i = 0; i < n; i++) {
        t = (dlimb_t)taba[i] * (dlimb_t)b + l;
        tabr[i] = t;
        l = t >> LIMB_BITS;
    }
    return l;
}

/* WARNING: d must be >= 2^(LIMB_BITS-1) */
static inline limb_t udiv1norm_init(limb_t d)
{
    limb_t a0, a1;
    a1 = -d - 1;
    a0 = -1;
    return (((dlimb_t)a1 << LIMB_BITS) | a0) / d;
}

/* return the quotient and the remainder in '*pr'of 'a1*2^LIMB_BITS+a0
   / d' with 0 <= a1 < d. */
static inline limb_t udiv1norm(limb_t *pr, limb_t a1, limb_t a0,
                                limb_t d, limb_t d_inv)
{
    limb_t n1m, n_adj, q, r, ah;
    dlimb_t a;
    n1m = ((slimb_t)a0 >> (LIMB_BITS - 1));
    n_adj = a0 + (n1m & d);
    a = (dlimb_t)d_inv * (a1 - n1m) + n_adj;
    q = (a >> LIMB_BITS) + a1;
    /* compute a - q * r and update q so that the remainder is between
       0 and d - 1 */
    a = ((dlimb_t)a1 << LIMB_BITS) | a0;
    a = a - (dlimb_t)q * d - d;
    ah = a >> LIMB_BITS;
    q += 1 + ah;
    r = (limb_t)a + (ah & d);
    *pr = r;
    return q;
}

static limb_t mp_div1(limb_t *tabr, const limb_t *taba, limb_t n,
                      limb_t b, limb_t r)
{
    slimb_t i;
    dlimb_t a1;
    for(i = n - 1; i >= 0; i--) {
        a1 = ((dlimb_t)r << LIMB_BITS) | taba[i];
        tabr[i] = a1 / b;
        r = a1 % b;
    }
    return r;
}

/* r = (a + high*B^n) >> shift. Return the remainder r (0 <= r < 2^shift). 
   1 <= shift <= LIMB_BITS - 1 */
static limb_t mp_shr(limb_t *tab_r, const limb_t *tab, mp_size_t n, 
                     int shift, limb_t high)
{
    mp_size_t i;
    limb_t l, a;

    assert(shift >= 1 && shift < LIMB_BITS);
    l = high;
    for(i = n - 1; i >= 0; i--) {
        a = tab[i];
        tab_r[i] = (a >> shift) | (l << (LIMB_BITS - shift));
        l = a;
    }
    return l & (((limb_t)1 << shift) - 1);
}

/* r = (a << shift) + low. 1 <= shift <= LIMB_BITS - 1, 0 <= low <
   2^shift. */
static limb_t mp_shl(limb_t *tab_r, const limb_t *tab, mp_size_t n, 
              int shift, limb_t low)
{
    mp_size_t i;
    limb_t l, a;

    assert(shift >= 1 && shift < LIMB_BITS);
    l = low;
    for(i = 0; i < n; i++) {
        a = tab[i];
        tab_r[i] = (a << shift) | l;
        l = (a >> (LIMB_BITS - shift)); 
    }
    return l;
}

static no_inline limb_t mp_div1norm(limb_t *tabr, const limb_t *taba, limb_t n,
                                    limb_t b, limb_t r, limb_t b_inv, int shift)
{
    slimb_t i;

    if (shift != 0) {
        r = (r << shift) | mp_shl(tabr, taba, n, shift, 0);
    }
    for(i = n - 1; i >= 0; i--) {
        tabr[i] = udiv1norm(&r, r, taba[i], b, b_inv);
    }
    r >>= shift;
    return r;
}

static __maybe_unused void mpb_dump(const char *str, const mpb_t *a)
{
    int i;
    
    printf("%s= 0x", str);
    for(i = a->len - 1; i >= 0; i--) {
        printf("%08x", a->tab[i]);
        if (i != 0)
            printf("_");
    }
    printf("\n");
}

static void mpb_renorm(mpb_t *r)
{
    while (r->len > 1 && r->tab[r->len - 1] == 0)
        r->len--;
}

#ifdef USE_POW5_TABLE
static const uint32_t pow5_table[17] = {
    0x00000005, 0x00000019, 0x0000007d, 0x00000271, 
    0x00000c35, 0x00003d09, 0x0001312d, 0x0005f5e1, 
    0x001dcd65, 0x009502f9, 0x02e90edd, 0x0e8d4a51, 
    0x48c27395, 0x6bcc41e9, 0x1afd498d, 0x86f26fc1, 
    0xa2bc2ec5, 
};

static const uint8_t pow5h_table[4] = {
    0x00000001, 0x00000007, 0x00000023, 0x000000b1, 
};

static const uint32_t pow5_inv_table[13] = {
    0x99999999, 0x47ae147a, 0x0624dd2f, 0xa36e2eb1,
    0x4f8b588e, 0x0c6f7a0b, 0xad7f29ab, 0x5798ee23,
    0x12e0be82, 0xb7cdfd9d, 0x5fd7fe17, 0x19799812,
    0xc25c2684,
};
#endif

/* return a^b */
static uint64_t pow_ui(uint32_t a, uint32_t b)
{
    int i, n_bits;
    uint64_t r;
    if (b == 0)
        return 1;
    if (b == 1)
        return a;
#ifdef USE_POW5_TABLE
    if ((a == 5 || a == 10) && b <= 17) {
        r = pow5_table[b - 1];
        if (b >= 14) {
            r |= (uint64_t)pow5h_table[b - 14] << 32;
        }
        if (a == 10)
            r <<= b;
        return r;
    }
#endif
    r = a;
    n_bits = 32 - clz32(b);
    for(i = n_bits - 2; i >= 0; i--) {
        r *= r;
        if ((b >> i) & 1)
            r *= a;
    }
    return r;
}

static uint32_t pow_ui_inv(uint32_t *pr_inv, int *pshift, uint32_t a, uint32_t b)
{
    uint32_t r_inv, r;
    int shift;
#ifdef USE_POW5_TABLE
    if (a == 5 && b >= 1 && b <= 13) {
        r = pow5_table[b - 1];
        shift = clz32(r);
        r <<= shift;
        r_inv = pow5_inv_table[b - 1];
    } else
#endif
    {
        r = pow_ui(a, b);
        shift = clz32(r);
        r <<= shift;
        r_inv = udiv1norm_init(r);
    }
    *pshift = shift;
    *pr_inv = r_inv;
    return r;
}

enum {
    JS_RNDN, /* round to nearest, ties to even */
    JS_RNDNA, /* round to nearest, ties away from zero */
    JS_RNDZ,
};

static int mpb_get_bit(const mpb_t *r, int k)
{
    int l;
    
    l = (unsigned)k / LIMB_BITS;
    k = k & (LIMB_BITS - 1);
    if (l >= r->len)
        return 0;
    else
        return (r->tab[l] >> k) & 1;
}

/* compute round(r / 2^shift). 'shift' can be negative */
static void mpb_shr_round(mpb_t *r, int shift, int rnd_mode)
{
    int l, i;

    if (shift == 0)
        return;
    if (shift < 0) {
        shift = -shift;
        l = (unsigned)shift / LIMB_BITS;
        shift = shift & (LIMB_BITS - 1);
        if (shift != 0) {
            r->tab[r->len] = mp_shl(r->tab, r->tab, r->len, shift, 0);
            r->len++;
            mpb_renorm(r);
        }
        if (l > 0) {
            for(i = r->len - 1; i >= 0; i--)
                r->tab[i + l] = r->tab[i];
            for(i = 0; i < l; i++)
                r->tab[i] = 0;
            r->len += l;
        }
    } else {
        limb_t bit1, bit2;
        int k, add_one;
        
        switch(rnd_mode) {
        default:
        case JS_RNDZ:
            add_one = 0;
            break;
        case JS_RNDN:
        case JS_RNDNA:
            bit1 = mpb_get_bit(r, shift - 1);
            if (bit1) {
                if (rnd_mode == JS_RNDNA) {
                    bit2 = 1;
                } else {
                    /* bit2 = oring of all the bits after bit1 */
                    bit2 = 0;
                    if (shift >= 2) {
                        k = shift - 1;
                        l = (unsigned)k / LIMB_BITS;
                        k = k & (LIMB_BITS - 1);
                        for(i = 0; i < min_int(l, r->len); i++)
                            bit2 |= r->tab[i];
                        if (l < r->len)
                            bit2 |= r->tab[l] & (((limb_t)1 << k) - 1);
                    }
                }
                if (bit2) {
                    add_one = 1;
                } else {
                    /* round to even */
                    add_one = mpb_get_bit(r, shift);
                }
            } else {
                add_one = 0;
            }
            break;
        }

        l = (unsigned)shift / LIMB_BITS;
        shift = shift & (LIMB_BITS - 1);
        if (l >= r->len) {
            r->len = 1;
            r->tab[0] = add_one;
        } else {
            if (l > 0) {
                r->len -= l;
                for(i = 0; i < r->len; i++)
                    r->tab[i] = r->tab[i + l];
            }
            if (shift != 0) {
                mp_shr(r->tab, r->tab, r->len, shift, 0);
                mpb_renorm(r);
            }
            if (add_one) {
                limb_t a;
                a = mp_add_ui(r->tab, 1, r->len);
                if (a)
                    r->tab[r->len++] = a;
            }
        }
    }
}

/* return -1, 0 or 1 */
static int mpb_cmp(const mpb_t *a, const mpb_t *b)
{
    mp_size_t i;
    if (a->len < b->len)
        return -1;
    else if (a->len > b->len)
        return 1;
    for(i = a->len - 1; i >= 0; i--) {
        if (a->tab[i] != b->tab[i]) {
            if (a->tab[i] < b->tab[i])
                return -1;
            else
                return 1;
        }
    }
    return 0;
}

static void mpb_set_u64(mpb_t *r, uint64_t m)
{
#if LIMB_BITS == 64
    r->tab[0] = m;
    r->len = 1;
#else
    r->tab[0] = m;
    r->tab[1] = m >> LIMB_BITS;
    if (r->tab[1] == 0)
        r->len = 1;
    else
        r->len = 2;
#endif
}

static uint64_t mpb_get_u64(mpb_t *r)
{
#if LIMB_BITS == 64
    return r->tab[0];
#else
    if (r->len == 1) {
        return r->tab[0];
    } else {
        return r->tab[0] | ((uint64_t)r->tab[1] << LIMB_BITS);
    }
#endif
}

/* floor_log2() = position of the first non zero bit or -1 if zero. */
static int mpb_floor_log2(mpb_t *a)
{
    limb_t v;
    v = a->tab[a->len - 1];
    if (v == 0)
        return -1;
    else
        return a->len * LIMB_BITS - 1 - clz32(v);
}

#define MUL_LOG2_RADIX_BASE_LOG2 24

/* round((1 << MUL_LOG2_RADIX_BASE_LOG2)/log2(i + 2)) */
static const uint32_t mul_log2_radix_table[JS_RADIX_MAX - 1] = {
    0x000000, 0xa1849d, 0x000000, 0x6e40d2, 
    0x6308c9, 0x5b3065, 0x000000, 0x50c24e, 
    0x4d104d, 0x4a0027, 0x4768ce, 0x452e54, 
    0x433d00, 0x418677, 0x000000, 0x3ea16b, 
    0x3d645a, 0x3c43c2, 0x3b3b9a, 0x3a4899, 
    0x39680b, 0x3897b3, 0x37d5af, 0x372069, 
    0x367686, 0x35d6df, 0x354072, 0x34b261, 
    0x342bea, 0x33ac62, 0x000000, 0x32bfd9, 
    0x3251dd, 0x31e8d6, 0x318465,
};

/* return floor(a / log2(radix)) for -2048 <= a <= 2047 */
static int mul_log2_radix(int a, int radix)
{
    int radix_bits, mult;

    if ((radix & (radix - 1)) == 0) {
        /* if the radix is a power of two better to do it exactly */
        radix_bits = 31 - clz32(radix);
        if (a < 0)
            a -= radix_bits - 1;
        return a / radix_bits;
    } else {
        mult = mul_log2_radix_table[radix - 2];
        return ((int64_t)a * mult) >> MUL_LOG2_RADIX_BASE_LOG2;
    }
}

#if 0
static void build_mul_log2_radix_table(void)
{
    int base, radix, mult, col, base_log2;

    base_log2 = 24;
    base = 1 << base_log2;
    col = 0;
    for(radix = 2; radix <= 36; radix++) {
        if ((radix & (radix - 1)) == 0)
            mult = 0;
        else
            mult = lrint((double)base / log2(radix));
        printf("0x%06x, ", mult);
        if (++col == 4) {
            printf("\n");
            col = 0;
        }
    }
    printf("\n");
}

static void mul_log2_radix_test(void)
{
    int radix, i, ref, r;
    
    for(radix = 2; radix <= 36; radix++) {
        for(i = -2048; i <= 2047; i++) {
            ref = (int)floor((double)i / log2(radix));
            r = mul_log2_radix(i, radix);
            if (ref != r) {
                printf("ERROR: radix=%d i=%d r=%d ref=%d\n",
                       radix, i, r, ref);
                exit(1);
            }
        }
    }
    if (0)
        build_mul_log2_radix_table();
}
#endif

static void u32toa_len(char *buf, uint32_t n, size_t len)
{
    int digit, i;
    for(i = len - 1; i >= 0; i--) {
        digit = n % 10;
        n = n / 10;
        buf[i] = digit + '0';
    }
}

/* for power of 2 radixes. len >= 1 */
static void u64toa_bin_len(char *buf, uint64_t n, unsigned int radix_bits, int len)
{
    int digit, i;
    unsigned int mask;

    mask = (1 << radix_bits) - 1;
    for(i = len - 1; i >= 0; i--) {
        digit = n & mask;
        n >>= radix_bits;
        if (digit < 10)
            digit += '0';
        else
            digit += 'a' - 10;
        buf[i] = digit;
    }
}

/* len >= 1. 2 <= radix <= 36 */
static void limb_to_a(char *buf, limb_t n, unsigned int radix, int len)
{
    int digit, i;

    if (radix == 10) {
        /* specific case with constant divisor */
#if LIMB_BITS == 32
        u32toa_len(buf, n, len);
#else
        /* XXX: optimize */
        for(i = len - 1; i >= 0; i--) {
            digit = (limb_t)n % 10;
            n = (limb_t)n / 10;
            buf[i] = digit + '0';
        }
#endif
    } else {
        for(i = len - 1; i >= 0; i--) {
            digit = (limb_t)n % radix;
            n = (limb_t)n / radix;
            if (digit < 10)
                digit += '0';
            else
                digit += 'a' - 10;
            buf[i] = digit;
        }
    }
}

size_t u32toa(char *buf, uint32_t n)
{
    char buf1[10], *q;
    size_t len;
    
    q = buf1 + sizeof(buf1);
    do {
        *--q = n % 10 + '0';
        n /= 10;
    } while (n != 0);
    len = buf1 + sizeof(buf1) - q;
    memcpy(buf, q, len);
    return len;
}

size_t i32toa(char *buf, int32_t n)
{
    if (n >= 0) {
        return u32toa(buf, n);
    } else {
        buf[0] = '-';
        return u32toa(buf + 1, -(uint32_t)n) + 1;
    }
}

#ifdef USE_FAST_INT
size_t u64toa(char *buf, uint64_t n)
{
    if (n < 0x100000000) {
        return u32toa(buf, n);
    } else {
        uint64_t n1;
        char *q = buf;
        uint32_t n2;
        
        n1 = n / 1000000000;
        n %= 1000000000;
        if (n1 >= 0x100000000) {
            n2 = n1 / 1000000000;
            n1 = n1 % 1000000000;
            /* at most two digits */
            if (n2 >= 10) {
                *q++ = n2 / 10 + '0';
                n2 %= 10;
            }
            *q++ = n2 + '0';
            u32toa_len(q, n1, 9);
            q += 9;
        } else {
            q += u32toa(q, n1);
        }
        u32toa_len(q, n, 9);
        q += 9;
        return q - buf;
    }
}

size_t i64toa(char *buf, int64_t n)
{
    if (n >= 0) {
        return u64toa(buf, n);
    } else {
        buf[0] = '-';
        return u64toa(buf + 1, -(uint64_t)n) + 1;
    }
}

/* XXX: only tested for 1 <= n < 2^53 */
size_t u64toa_radix(char *buf, uint64_t n, unsigned int radix)
{
    int radix_bits, l;
    if (likely(radix == 10))
        return u64toa(buf, n);
    if ((radix & (radix - 1)) == 0) {
        radix_bits = 31 - clz32(radix);
        if (n == 0)
            l = 1;
        else
            l = (64 - clz64(n) + radix_bits - 1) / radix_bits;
        u64toa_bin_len(buf, n, radix_bits, l);
        return l;
    } else {
        char buf1[41], *q; /* maximum length for radix = 3 */
        size_t len;
        int digit;
        q = buf1 + sizeof(buf1);
        do {
            digit = n % radix;
            n /= radix;
            if (digit < 10)
                digit += '0';
            else
                digit += 'a' - 10;
            *--q = digit;
        } while (n != 0);
        len = buf1 + sizeof(buf1) - q;
        memcpy(buf, q, len);
        return len;
    }
}

size_t i64toa_radix(char *buf, int64_t n, unsigned int radix)
{
    if (n >= 0) {
        return u64toa_radix(buf, n, radix);
    } else {
        buf[0] = '-';
        return u64toa_radix(buf + 1, -(uint64_t)n, radix) + 1;
    }
}
#endif /* USE_FAST_INT */

static const uint8_t digits_per_limb_table[JS_RADIX_MAX - 1] = {
#if LIMB_BITS == 32
32,20,16,13,12,11,10,10, 9, 9, 8, 8, 8, 8, 8, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
#else
64,40,32,27,24,22,21,20,19,18,17,17,16,16,16,15,15,15,14,14,14,14,13,13,13,13,13,13,13,12,12,12,12,12,12,
#endif
};

static const uint32_t radix_base_table[JS_RADIX_MAX - 1] = {
 0x00000000, 0xcfd41b91, 0x00000000, 0x48c27395,
 0x81bf1000, 0x75db9c97, 0x40000000, 0xcfd41b91,
 0x3b9aca00, 0x8c8b6d2b, 0x19a10000, 0x309f1021,
 0x57f6c100, 0x98c29b81, 0x00000000, 0x18754571,
 0x247dbc80, 0x3547667b, 0x4c4b4000, 0x6b5a6e1d,
 0x94ace180, 0xcaf18367, 0x0b640000, 0x0e8d4a51,
 0x1269ae40, 0x17179149, 0x1cb91000, 0x23744899,
 0x2b73a840, 0x34e63b41, 0x40000000, 0x4cfa3cc1,
 0x5c13d840, 0x6d91b519, 0x81bf1000,
};

/* XXX: remove the table ? */
static uint8_t dtoa_max_digits_table[JS_RADIX_MAX - 1] = {
    54, 35, 28, 24, 22, 20, 19, 18, 17, 17, 16, 16, 15, 15, 15, 14, 14, 14, 14, 14, 13, 13, 13, 13, 13, 13, 13, 12, 12, 12, 12, 12, 12, 12, 12,
};

/* we limit the maximum number of significant digits for atod to about
   128 bits of precision for non power of two bases. The only
   requirement for Javascript is at least 20 digits in base 10. For
   power of two bases, we do an exact rounding in all the cases. */
static uint8_t atod_max_digits_table[JS_RADIX_MAX - 1] = {
     64, 80, 32, 55, 49, 45, 21, 40, 38, 37, 35, 34, 33, 32, 16, 31, 30, 30, 29, 29, 28, 28, 27, 27, 27, 26, 26, 26, 26, 25, 12, 25, 25, 24, 24,
};

/* if abs(d) >= B^max_exponent, it is an overflow */
static const int16_t max_exponent[JS_RADIX_MAX - 1] = {
 1024,   647,   512,   442,   397,   365,   342,   324, 
  309,   297,   286,   277,   269,   263,   256,   251, 
  246,   242,   237,   234,   230,   227,   224,   221, 
  218,   216,   214,   211,   209,   207,   205,   203, 
  202,   200,   199, 
};

/* if abs(d) <= B^min_exponent, it is an underflow */
static const int16_t min_exponent[JS_RADIX_MAX - 1] = {
-1075,  -679,  -538,  -463,  -416,  -383,  -359,  -340, 
 -324,  -311,  -300,  -291,  -283,  -276,  -269,  -263, 
 -258,  -254,  -249,  -245,  -242,  -238,  -235,  -232, 
 -229,  -227,  -224,  -222,  -220,  -217,  -215,  -214, 
 -212,  -210,  -208, 
};

#if 0
void build_tables(void)
{
    int r, j, radix, n, col, i;
    
    /* radix_base_table */
    for(radix = 2; radix <= 36; radix++) {
        r = 1;
        for(j = 0; j < digits_per_limb_table[radix - 2]; j++) {
            r *= radix;
        }
        printf(" 0x%08x,", r);
        if ((radix % 4) == 1)
            printf("\n");
    }
    printf("\n");

    /* dtoa_max_digits_table */
    for(radix = 2; radix <= 36; radix++) {
        /* Note: over estimated when the radix is a power of two */
        printf(" %d,", 1 + (int)ceil(53.0 / log2(radix)));
    }
    printf("\n");

    /* atod_max_digits_table */
    for(radix = 2; radix <= 36; radix++) {
        if ((radix & (radix - 1)) == 0) {
            /* 64 bits is more than enough */
            n = (int)floor(64.0 / log2(radix));
        } else {
            n = (int)floor(128.0 / log2(radix));
        }
        printf(" %d,", n);
    }
    printf("\n");

    printf("static const int16_t max_exponent[JS_RADIX_MAX - 1] = {\n");
    col = 0;
    for(radix = 2; radix <= 36; radix++) {
        printf("%5d, ", (int)ceil(1024 / log2(radix)));
        if (++col == 8) {
            col = 0;
            printf("\n");
        }
    }
    printf("\n};\n\n");

    printf("static const int16_t min_exponent[JS_RADIX_MAX - 1] = {\n");
    col = 0; 
    for(radix = 2; radix <= 36; radix++) {
        printf("%5d, ", (int)floor(-1075 / log2(radix)));
        if (++col == 8) {
            col = 0;
            printf("\n");
        }
    }
    printf("\n};\n\n");

    printf("static const uint32_t pow5_table[16] = {\n");
    col = 0; 
    for(i = 2; i <= 17; i++) {
        r = 1;
        for(j = 0; j < i; j++) {
            r *= 5;
        }
        printf("0x%08x, ", r);
        if (++col == 4) {
            col = 0;
            printf("\n");
        }
    }
    printf("\n};\n\n");

    /* high part */
    printf("static const uint8_t pow5h_table[4] = {\n");
    col = 0; 
    for(i = 14; i <= 17; i++) {
        uint64_t r1;
        r1 = 1;
        for(j = 0; j < i; j++) {
            r1 *= 5;
        }
        printf("0x%08x, ", (uint32_t)(r1 >> 32));
        if (++col == 4) {
            col = 0;
            printf("\n");
        }
    }
    printf("\n};\n\n");
}
#endif

/* n_digits >= 1. 0 <= dot_pos <= n_digits. If dot_pos == n_digits,
   the dot is not displayed. 'a' is modified. */
static int output_digits(char *buf,
                         mpb_t *a, int radix, int n_digits1,
                         int dot_pos)
{
    int n_digits, digits_per_limb, radix_bits, n, len;

    n_digits = n_digits1;
    if ((radix & (radix - 1)) == 0) {
        /* radix = 2^radix_bits */
        radix_bits = 31 - clz32(radix);
    } else {
        radix_bits = 0;
    }
    digits_per_limb = digits_per_limb_table[radix - 2];
    if (radix_bits != 0) {
        for(;;) {
            n = min_int(n_digits, digits_per_limb);
            n_digits -= n;
            u64toa_bin_len(buf + n_digits, a->tab[0], radix_bits, n);
            if (n_digits == 0)
                break;
            mpb_shr_round(a, digits_per_limb * radix_bits, JS_RNDZ);
        }
    } else {
        limb_t r;
        while (n_digits != 0) {
            n = min_int(n_digits, digits_per_limb);
            n_digits -= n;
            r = mp_div1(a->tab, a->tab, a->len, radix_base_table[radix - 2], 0);
            mpb_renorm(a);
            limb_to_a(buf + n_digits, r, radix, n);
        }
    }

    /* add the dot */
    len = n_digits1;
    if (dot_pos != n_digits1) {
        memmove(buf + dot_pos + 1, buf + dot_pos, n_digits1 - dot_pos);
        buf[dot_pos] = '.';
        len++;
    }
    return len;
}

/* return (a, e_offset) such that a = a * (radix1*2^radix_shift)^f *
   2^-e_offset. 'f' can be negative. */
static int mul_pow(mpb_t *a, int radix1, int radix_shift, int f, bool is_int, int e)
{
    int e_offset, d, n, n0;

    e_offset = -f * radix_shift;
    if (radix1 != 1) {
        d = digits_per_limb_table[radix1 - 2];
        if (f >= 0) {
            limb_t h, b;
            
            b = 0;
            n0 = 0;
            while (f != 0) {
                n = min_int(f, d);
                if (n != n0) {
                    b = pow_ui(radix1, n);
                    n0 = n;
                }
                h = mp_mul1(a->tab, a->tab, a->len, b, 0);
                if (h != 0) {
                    a->tab[a->len++] = h;
                }
                f -= n;
            }
        } else {
            int extra_bits, l, shift;
            limb_t r, rem, b, b_inv;
            
            f = -f;
            l = (f + d - 1) / d; /* high bound for the number of limbs (XXX: make it better) */
            e_offset += l * LIMB_BITS;
            if (!is_int) {
                /* at least 'e' bits are needed in the final result for rounding */
                extra_bits = max_int(e - mpb_floor_log2(a), 0);
            } else {
                /* at least two extra bits are needed in the final result
                   for rounding */
                extra_bits = max_int(2 + e - e_offset, 0);
            }
            e_offset += extra_bits;
            mpb_shr_round(a, -(l * LIMB_BITS + extra_bits), JS_RNDZ);
            
            b = 0;
            b_inv = 0;
            shift = 0;
            n0 = 0;
            rem = 0;
            while (f != 0) {
                n = min_int(f, d);
                if (n != n0) {
                    b = pow_ui_inv(&b_inv, &shift, radix1, n);
                    n0 = n;
                }
                r = mp_div1norm(a->tab, a->tab, a->len, b, 0, b_inv, shift);
                rem |= r;
                mpb_renorm(a);
                f -= n;
            }
            /* if the remainder is non zero, use it for rounding */
            a->tab[0] |= (rem != 0);
        }
    }
    return e_offset;
}

/* tmp1 = round(m*2^e*radix^f). 'tmp0' is a temporary storage */
static void mul_pow_round(mpb_t *tmp1, uint64_t m, int e, int radix1, int radix_shift, int f,
                          int rnd_mode)
{
    int e_offset;

    mpb_set_u64(tmp1, m);
    e_offset = mul_pow(tmp1, radix1, radix_shift, f, true, e);
    mpb_shr_round(tmp1, -e + e_offset, rnd_mode);
}

/* return round(a*2^e_offset) rounded as a float64. 'a' is modified */
static uint64_t round_to_d(int *pe, mpb_t *a, int e_offset, int rnd_mode)
{
    int e;
    uint64_t m;

    if (a->tab[0] == 0 && a->len == 1) {
        /* zero result */
        m = 0;
        e = 0; /* don't care */
    } else {
        int prec, prec1, e_min;
        e = mpb_floor_log2(a) + 1 - e_offset;
        prec1 = 53;
        e_min = -1021;
        if (e < e_min) {
            /* subnormal result or zero */
            prec = prec1 - (e_min - e);
        } else {
            prec = prec1;
        }
        mpb_shr_round(a, e + e_offset - prec, rnd_mode);
        m = mpb_get_u64(a);
        m <<= (53 - prec);
        /* mantissa overflow due to rounding */
        if (m >= (uint64_t)1 << 53) {
            m >>= 1;
            e++;
        }
    }
    *pe = e;
    return m;
}

/* return (m, e) such that m*2^(e-53) = round(a * radix^f) with 2^52
   <= m < 2^53 or m = 0.
   'a' is modified. */
static uint64_t mul_pow_round_to_d(int *pe, mpb_t *a,
                                   int radix1, int radix_shift, int f, int rnd_mode)
{
    int e_offset;

    e_offset = mul_pow(a, radix1, radix_shift, f, false, 55);
    return round_to_d(pe, a, e_offset, rnd_mode);
}

#ifdef JS_DTOA_DUMP_STATS
static int out_len_count[17];

void js_dtoa_dump_stats(void)
{
    int i, sum;
    sum = 0;
    for(i = 0; i < 17; i++)
        sum += out_len_count[i];
    for(i = 0; i < 17; i++) {
        printf("%2d %8d %5.2f%%\n",
               i + 1, out_len_count[i], (double)out_len_count[i] / sum * 100);
    }
}
#endif

/* return a maximum bound of the string length. The bound depends on
   'd' only if format = JS_DTOA_FORMAT_FRAC or if JS_DTOA_EXP_DISABLED
   is enabled. */
int js_dtoa_max_len(double d, int radix, int n_digits, int flags)
{
    int fmt = flags & JS_DTOA_FORMAT_MASK;
    int n, e;
    uint64_t a;

    if (fmt != JS_DTOA_FORMAT_FRAC) {
        if (fmt == JS_DTOA_FORMAT_FREE) {
            n = dtoa_max_digits_table[radix - 2];
        } else {
            n = n_digits;
        }
        if ((flags & JS_DTOA_EXP_MASK) == JS_DTOA_EXP_DISABLED) {
            /* no exponential */
            a = float64_as_uint64(d);
            e = (a >> 52) & 0x7ff;
            if (e == 0x7ff) {
                /* NaN, Infinity */
                n = 0;
            } else {
                e -= 1023;
                /* XXX: adjust */
                n += 10 + abs(mul_log2_radix(e - 1, radix));
            }
        } else {
            /* extra: sign, 1 dot and exponent "e-1000" */
            n += 1 + 1 + 6;
        }
    } else {
        a = float64_as_uint64(d);
        e = (a >> 52) & 0x7ff;
        if (e == 0x7ff) {
            /* NaN, Infinity */
            n = 0;
        } else {
            /* high bound for the integer part */
            e -= 1023;
            /* x < 2^(e + 1) */
            if (e < 0) {
                n = 1;
            } else {
                n = 2 + mul_log2_radix(e - 1, radix);
            }
            /* sign, extra digit, 1 dot */
            n += 1 + 1 + 1 + n_digits;
        }
    }
    return max_int(n, 9); /* also include NaN and [-]Infinity */
}

#if defined(__SANITIZE_ADDRESS__) && 0
static void *dtoa_malloc(uint64_t **pptr, size_t size)
{
    return malloc(size);
}
static void dtoa_free(void *ptr)
{
    free(ptr);
}
#else
static void *dtoa_malloc(uint64_t **pptr, size_t size)
{
    void *ret;
    ret = *pptr;
    *pptr += (size + 7) / 8;
    return ret;
}

static void dtoa_free(void *ptr)
{
}
#endif

/* return the length */
/* Grisu3 (Florian Loitsch, "Printing Floating-Point Numbers Quickly and
   Accurately with Integers", 2010) for the shortest base 10 output. It
   either produces the shortest digit string that is the closest to the
   value or reports failure (about 0.5% of the inputs), in which case the
   exact multi-precision path below is used. */

typedef struct {
    uint64_t f;
    int e;
} dtoa_diyfp;

typedef struct {
    uint64_t f;
    int16_t e;
    int16_t k;
} dtoa_cached_pow10;

/* 10^k for k = -348, -340, ..., 340: 64 bit significand rounded to
   nearest and binary exponent */
static const dtoa_cached_pow10 dtoa_cached_pow10_table[] = {
    { 0xfa8fd5a0081c0288ULL, -1220, -348 },
    { 0xbaaee17fa23ebf76ULL, -1193, -340 },
    { 0x8b16fb203055ac76ULL, -1166, -332 },
    { 0xcf42894a5dce35eaULL, -1140, -324 },
    { 0x9a6bb0aa55653b2dULL, -1113, -316 },
    { 0xe61acf033d1a45dfULL, -1087, -308 },
    { 0xab70fe17c79ac6caULL, -1060, -300 },
    { 0xff77b1fcbebcdc4fULL, -1034, -292 },
    { 0xbe5691ef416bd60cULL, -1007, -284 },
    { 0x8dd01fad907ffc3cULL, -980, -276 },
    { 0xd3515c2831559a83ULL, -954, -268 },
    { 0x9d71ac8fada6c9b5ULL, -927, -260 },
    { 0xea9c227723ee8bcbULL, -901, -252 },
    { 0xaecc49914078536dULL, -874, -244 },
    { 0x823c12795db6ce57ULL, -847, -236 },
    { 0xc21094364dfb5637ULL, -821, -228 },
    { 0x9096ea6f3848984fULL, -794, -220 },
    { 0xd77485cb25823ac7ULL, -768, -212 },
    { 0xa086cfcd97bf97f4ULL, -741, -204 },
    { 0xef340a98172aace5ULL, -715, -196 },
    { 0xb23867fb2a35b28eULL, -688, -188 },
    { 0x84c8d4dfd2c63f3bULL, -661, -180 },
    { 0xc5dd44271ad3cdbaULL, -635, -172 },
    { 0x936b9fcebb25c996ULL, -608, -164 },
    { 0xdbac6c247d62a584ULL, -582, -156 },
    { 0xa3ab66580d5fdaf6ULL, -555, -148 },
    { 0xf3e2f893dec3f126ULL, -529, -140 },
    { 0xb5b5ada8aaff80b8ULL, -502, -132 },
    { 0x87625f056c7c4a8bULL, -475, -124 },
    { 0xc9bcff6034c13053ULL, -449, -116 },
    { 0x964e858c91ba2655ULL, -422, -108 },
    { 0xdff9772470297ebdULL, -396, -100 },
    { 0xa6dfbd9fb8e5b88fULL, -369, -92 },
    { 0xf8a95fcf88747d94ULL, -343, -84 },
    { 0xb94470938fa89bcfULL, -316, -76 },
    { 0x8a08f0f8bf0f156bULL, -289, -68 },
    { 0xcdb02555653131b6ULL, -263, -60 },
    { 0x993fe2c6d07b7facULL, -236, -52 },
    { 0xe45c10c42a2b3b06ULL, -210, -44 },
    { 0xaa242499697392d3ULL, -183, -36 },
    { 0xfd87b5f28300ca0eULL, -157, -28 },
    { 0xbce5086492111aebULL, -130, -20 },
    { 0x8cbccc096f5088ccULL, -103, -12 },
    { 0xd1b71758e219652cULL, -77, -4 },
    { 0x9c40000000000000ULL, -50, 4 },
    { 0xe8d4a51000000000ULL, -24, 12 },
    { 0xad78ebc5ac620000ULL, 3, 20 },
    { 0x813f3978f8940984ULL, 30, 28 },
    { 0xc097ce7bc90715b3ULL, 56, 36 },
    { 0x8f7e32ce7bea5c70ULL, 83, 44 },
    { 0xd5d238a4abe98068ULL, 109, 52 },
    { 0x9f4f2726179a2245ULL, 136, 60 },
    { 0xed63a231d4c4fb27ULL, 162, 68 },
    { 0xb0de65388cc8ada8ULL, 189, 76 },
    { 0x83c7088e1aab65dbULL, 216, 84 },
    { 0xc45d1df942711d9aULL, 242, 92 },
    { 0x924d692ca61be758ULL, 269, 100 },
    { 0xda01ee641a708deaULL, 295, 108 },
    { 0xa26da3999aef774aULL, 322, 116 },
    { 0xf209787bb47d6b85ULL, 348, 124 },
    { 0xb454e4a179dd1877ULL, 375, 132 },
    { 0x865b86925b9bc5c2ULL, 402, 140 },
    { 0xc83553c5c8965d3dULL, 428, 148 },
    { 0x952ab45cfa97a0b3ULL, 455, 156 },
    { 0xde469fbd99a05fe3ULL, 481, 164 },
    { 0xa59bc234db398c25ULL, 508, 172 },
    { 0xf6c69a72a3989f5cULL, 534, 180 },
    { 0xb7dcbf5354e9beceULL, 561, 188 },
    { 0x88fcf317f22241e2ULL, 588, 196 },
    { 0xcc20ce9bd35c78a5ULL, 614, 204 },
    { 0x98165af37b2153dfULL, 641, 212 },
    { 0xe2a0b5dc971f303aULL, 667, 220 },
    { 0xa8d9d1535ce3b396ULL, 694, 228 },
    { 0xfb9b7cd9a4a7443cULL, 720, 236 },
    { 0xbb764c4ca7a44410ULL, 747, 244 },
    { 0x8bab8eefb6409c1aULL, 774, 252 },
    { 0xd01fef10a657842cULL, 800, 260 },
    { 0x9b10a4e5e9913129ULL, 827, 268 },
    { 0xe7109bfba19c0c9dULL, 853, 276 },
    { 0xac2820d9623bf429ULL, 880, 284 },
    { 0x80444b5e7aa7cf85ULL, 907, 292 },
    { 0xbf21e44003acdd2dULL, 933, 300 },
    { 0x8e679c2f5e44ff8fULL, 960, 308 },
    { 0xd433179d9c8cb841ULL, 986, 316 },
    { 0x9e19db92b4e31ba9ULL, 1013, 324 },
    { 0xeb96bf6ebadf77d9ULL, 1039, 332 },
    { 0xaf87023b9bf0ee6bULL, 1066, 340 }
};

#define DTOA_GRISU_MIN_TARGET_EXP (-60)
#define DTOA_GRISU_MAX_TARGET_EXP (-32)

static dtoa_diyfp dtoa_diyfp_mul(dtoa_diyfp x, dtoa_diyfp y)
{
    const uint64_t M32 = 0xffffffffu;
    uint64_t a = x.f >> 32, b = x.f & M32, c = y.f >> 32, d = y.f & M32;
    uint64_t ac = a * c, bc = b * c, ad = a * d, bd = b * d;
    uint64_t tmp = (bd >> 32) + (ad & M32) + (bc & M32);
    dtoa_diyfp r;
    tmp += (uint64_t)1 << 31;
    r.f = ac + (ad >> 32) + (bc >> 32) + (tmp >> 32);
    r.e = x.e + y.e + 64;
    return r;
}

static dtoa_diyfp dtoa_diyfp_normalize(dtoa_diyfp x)
{
    int s = clz64(x.f);
    x.f <<= s;
    x.e -= s;
    return x;
}

static bool dtoa_grisu_round_weed(char *buf, int len, uint64_t distance_too_high_w,
                                  uint64_t unsafe_interval, uint64_t rest,
                                  uint64_t ten_kappa, uint64_t unit)
{
    uint64_t small_distance = distance_too_high_w - unit;
    uint64_t big_distance = distance_too_high_w + unit;
    while (rest < small_distance &&
           unsafe_interval - rest >= ten_kappa &&
           (rest + ten_kappa < small_distance ||
            small_distance - rest >= rest + ten_kappa - small_distance)) {
        buf[len - 1]--;
        rest += ten_kappa;
    }
    if (rest < big_distance &&
        unsafe_interval - rest >= ten_kappa &&
        (rest + ten_kappa < big_distance ||
         big_distance - rest > rest + ten_kappa - big_distance)) {
        return false;
    }
    return 2 * unit <= rest && rest <= unsafe_interval - 4 * unit;
}

/* 'd' is finite, non zero and positive. On success, return the number of
   digits written to 'buf' (at most 17) and set '*pk' so that d = buf * 10^k */
static int dtoa_grisu3(double d, char *buf, int *pk)
{
    static const uint32_t small_pow10[] = {
        0, 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000,
        1000000000,
    };
    uint64_t a = float64_as_uint64(d);
    int biased_e = (a >> 52) & 0x7ff;
    uint64_t sig = a & (((uint64_t)1 << 52) - 1);
    dtoa_diyfp v, w, m_plus, m_minus, c, low, high, too_low, too_high, one;
    uint64_t unsafe_interval, fractionals, rest, unit;
    uint32_t integrals, divisor;
    int min_e, idx, kappa, len, mk;

    if (biased_e == 0) {
        v.f = sig;
        v.e = 1 - 1075;
    } else {
        v.f = sig | ((uint64_t)1 << 52);
        v.e = biased_e - 1075;
    }
    w = dtoa_diyfp_normalize(v);
    m_plus.f = (v.f << 1) + 1;
    m_plus.e = v.e - 1;
    m_plus = dtoa_diyfp_normalize(m_plus);
    if (sig == 0 && biased_e > 1) {
        m_minus.f = (v.f << 2) - 1;
        m_minus.e = v.e - 2;
    } else {
        m_minus.f = (v.f << 1) - 1;
        m_minus.e = v.e - 1;
    }
    m_minus.f <<= m_minus.e - m_plus.e;
    m_minus.e = m_plus.e;

    /* cached power c = 10^-mk such that the exponent of w * c is in
       [DTOA_GRISU_MIN_TARGET_EXP, DTOA_GRISU_MAX_TARGET_EXP] */
    min_e = DTOA_GRISU_MIN_TARGET_EXP - (w.e + 64);
    idx = (int)((((int64_t)(min_e + 63) * 78913) >> 18) + 348 + 7) / 8;
    if (idx < 0)
        idx = 0;
    if (idx > (int)countof(dtoa_cached_pow10_table) - 1)
        idx = countof(dtoa_cached_pow10_table) - 1;
    while (idx > 0 && dtoa_cached_pow10_table[idx].e > DTOA_GRISU_MAX_TARGET_EXP - (w.e + 64))
        idx--;
    while (idx < (int)countof(dtoa_cached_pow10_table) - 1 && dtoa_cached_pow10_table[idx].e < min_e)
        idx++;
    if (dtoa_cached_pow10_table[idx].e < min_e ||
        dtoa_cached_pow10_table[idx].e > DTOA_GRISU_MAX_TARGET_EXP - (w.e + 64))
        return 0;
    c.f = dtoa_cached_pow10_table[idx].f;
    c.e = dtoa_cached_pow10_table[idx].e;
    mk = -dtoa_cached_pow10_table[idx].k;

    w = dtoa_diyfp_mul(w, c);
    low = dtoa_diyfp_mul(m_minus, c);
    high = dtoa_diyfp_mul(m_plus, c);

    /* digit generation */
    unit = 1;
    too_low.f = low.f - unit;
    too_low.e = low.e;
    too_high.f = high.f + unit;
    too_high.e = high.e;
    unsafe_interval = too_high.f - too_low.f;
    one.e = w.e;
    one.f = (uint64_t)1 << -one.e;
    integrals = (uint32_t)(too_high.f >> -one.e);
    fractionals = too_high.f & (one.f - 1);
    kappa = 10;
    while (kappa > 0 && integrals < small_pow10[kappa])
        kappa--;
    divisor = small_pow10[kappa];
    len = 0;
    while (kappa > 0) {
        buf[len++] = '0' + integrals / divisor;
        integrals %= divisor;
        kappa--;
        rest = ((uint64_t)integrals << -one.e) + fractionals;
        if (rest < unsafe_interval) {
            if (!dtoa_grisu_round_weed(buf, len, too_high.f - w.f, unsafe_interval,
                                       rest, (uint64_t)divisor << -one.e, unit))
                return 0;
            *pk = mk + kappa;
            return len;
        }
        divisor /= 10;
    }
    for(;;) {
        fractionals *= 10;
        unit *= 10;
        unsafe_interval *= 10;
        buf[len++] = '0' + (int)(fractionals >> -one.e);
        fractionals &= one.f - 1;
        kappa--;
        if (fractionals < unsafe_interval) {
            if (!dtoa_grisu_round_weed(buf, len, (too_high.f - w.f) * unit,
                                       unsafe_interval, fractionals, one.f, unit))
                return 0;
            *pk = mk + kappa;
            return len;
        }
        if (len >= 17)
            return 0;
    }
}

int js_dtoa(char *buf, double d, int radix, int n_digits, int flags,
            JSDTOATempMem *tmp_mem)
{
    uint64_t a, m, *mptr = tmp_mem->mem;
    int e, sgn, l, E, P, i, E_max, radix1, radix_shift;
    char *q;
    mpb_t *tmp1, *mant_max;
    int fmt = flags & JS_DTOA_FORMAT_MASK;

    tmp1 = dtoa_malloc(&mptr, sizeof(mpb_t) + sizeof(limb_t) * DBIGNUM_LEN_MAX);
    mant_max = dtoa_malloc(&mptr, sizeof(mpb_t) + sizeof(limb_t) * MANT_LEN_MAX);
    assert((mptr - tmp_mem->mem) <= sizeof(JSDTOATempMem) / sizeof(mptr[0]));

    radix_shift = ctz32(radix);
    radix1 = radix >> radix_shift;
    a = float64_as_uint64(d);
    sgn = a >> 63;
    e = (a >> 52) & 0x7ff;
    m = a & (((uint64_t)1 << 52) - 1);
    q = buf;
    if (e == 0x7ff) {
        if (m == 0) {
            if (sgn)
                *q++ = '-';
            memcpy(q, "Infinity", 8);
            q += 8;
        } else {
            memcpy(q, "NaN", 3);
            q += 3;
        }
        goto done;
    } else if (e == 0) {
        if (m == 0) {
            tmp1->len = 1;
            tmp1->tab[0] = 0;
            E = 1;
            if (fmt == JS_DTOA_FORMAT_FREE)
                P = 1;
            else if (fmt == JS_DTOA_FORMAT_FRAC)
                P = n_digits + 1;
            else
                P = n_digits;
            /* "-0" is displayed as "0" if JS_DTOA_MINUS_ZERO is not present */
            if (sgn && (flags & JS_DTOA_MINUS_ZERO))
                *q++ = '-';
            goto output;
        }
        /* denormal number: convert to a normal number */
        l = clz64(m) - 11;
        e -= l - 1;
        m <<= l;
    } else {
        m |= (uint64_t)1 << 52;
    }
    if (sgn)
        *q++ = '-';
    /* remove the bias */
    e -= 1022;
    /* d = 2^(e-53)*m */
    //    printf("m=0x%016" PRIx64 " e=%d\n", m, e);
#ifdef USE_FAST_INT
    if (fmt == JS_DTOA_FORMAT_FREE &&
        e >= 1 && e <= 53 &&
        (m & (((uint64_t)1 << (53 - e)) - 1)) == 0 &&
        (flags & JS_DTOA_EXP_MASK) != JS_DTOA_EXP_ENABLED) {
        m >>= 53 - e;
        /* 'm' is never zero */
        q += u64toa_radix(q, m, radix);
        goto done;
    }
#endif
    
    /* this choice of E implies F=round(x*B^(P-E) is such as: 
       B^(P-1) <= F < 2.B^P. */
    E = 1 + mul_log2_radix(e - 1, radix);
    
    if (fmt == JS_DTOA_FORMAT_FREE && radix == 10) {
        char digits[20];
        int k, n = dtoa_grisu3(fabs(d), digits, &k);
        if (n > 0) {
            while (digits[n - 1] == '0')
                n--;
            E = n + k;
            if ((flags & JS_DTOA_EXP_MASK) == JS_DTOA_EXP_ENABLED ||
                ((flags & JS_DTOA_EXP_MASK) == JS_DTOA_EXP_AUTO &&
                 (E <= -6 || E > dtoa_max_digits_table[10 - 2] + 4))) {
                *q++ = digits[0];
                if (n > 1) {
                    *q++ = '.';
                    memcpy(q, digits + 1, n - 1);
                    q += n - 1;
                }
                *q++ = 'e';
                E--;
                if (E < 0) {
                    *q++ = '-';
                    E = -E;
                } else {
                    *q++ = '+';
                }
                q += u32toa(q, E);
            } else if (E <= 0) {
                *q++ = '0';
                *q++ = '.';
                for(i = 0; i < -E; i++)
                    *q++ = '0';
                memcpy(q, digits, n);
                q += n;
            } else if (E >= n) {
                memcpy(q, digits, n);
                q += n;
                for(i = 0; i < E - n; i++)
                    *q++ = '0';
            } else {
                memcpy(q, digits, E);
                q += E;
                *q++ = '.';
                memcpy(q, digits + E, n - E);
                q += n - E;
            }
            goto done;
        }
    }
    if (fmt == JS_DTOA_FORMAT_FREE) {
        int P_max, E0, e1, E_found, P_found;
        uint64_t m1, mant_found, mant, mant_max1;
        /* P_max is guarranteed to work by construction */
        P_max = dtoa_max_digits_table[radix - 2];
        E0 = E;
        E_found = 0;
        P_found = 0;
        mant_found = 0;
        /* find the minimum number of digits by successive tries */
        P = P_max; /* P_max is guarateed to work */
        for(;;) {
            /* mant_max always fits on 64 bits */
            mant_max1 = pow_ui(radix, P);
            /* compute the mantissa in base B */
            E = E0;
            for(;;) {
                /* XXX: add inexact flag */
                mul_pow_round(tmp1, m, e - 53, radix1, radix_shift, P - E, JS_RNDN);
                mant = mpb_get_u64(tmp1);
                if (mant < mant_max1)
                    break;
                E++; /* at most one iteration is possible */
            }
            /* remove useless trailing zero digits */
            while ((mant % radix) == 0) {
                mant /= radix;
                P--;
            }
            /* garanteed to work for P = P_max */
            if (P_found == 0)
                goto prec_found;
            /* convert back to base 2 */
            mpb_set_u64(tmp1, mant);
            m1 = mul_pow_round_to_d(&e1, tmp1, radix1, radix_shift, E - P, JS_RNDN);
            //            printf("P=%2d: m=0x%016" PRIx64 " e=%d m1=0x%016" PRIx64 " e1=%d\n", P, m, e, m1, e1);
            /* Note: (m, e) is never zero here, so the exponent for m1
               = 0 does not matter */
            if (m1 == m && e1 == e) {
            prec_found:
                P_found = P;
                E_found = E;
                mant_found = mant;
                if (P == 1)
                    break;
                P--; /* try lower exponent */
            } else {
                break;
            }
        }
        P = P_found;
        E = E_found;
        mpb_set_u64(tmp1, mant_found);
#ifdef JS_DTOA_DUMP_STATS
        if (radix == 10) {
            out_len_count[P - 1]++;
        }
#endif        
    } else if (fmt == JS_DTOA_FORMAT_FRAC) {
        int len;

        assert(n_digits >= 0 && n_digits <= JS_DTOA_MAX_DIGITS);
        /* P = max_int(E, 1) + n_digits; */
        /* frac is rounded using RNDNA */
        mul_pow_round(tmp1, m, e - 53, radix1, radix_shift, n_digits, JS_RNDNA);

        /* we add one extra digit on the left and remove it if needed
           to avoid testing if the result is < radix^P */
        len = output_digits(q, tmp1, radix, max_int(E + 1, 1) + n_digits,
                            max_int(E + 1, 1));
        if (q[0] == '0' && len >= 2 && q[1] != '.') {
            len--;
            memmove(q, q + 1, len);
        }
        q += len;
        goto done;
    } else {
        int pow_shift;
        assert(n_digits >= 1 && n_digits <= JS_DTOA_MAX_DIGITS);
        P = n_digits;
        /* mant_max = radix^P */
        mant_max->len = 1;
        mant_max->tab[0] = 1;
        pow_shift = mul_pow(mant_max, radix1, radix_shift, P, false, 0);
        mpb_shr_round(mant_max, pow_shift, JS_RNDZ);
        
        for(;;) {
            /* fixed and frac are rounded using RNDNA */
            mul_pow_round(tmp1, m, e - 53, radix1, radix_shift, P - E, JS_RNDNA);
            if (mpb_cmp(tmp1, mant_max) < 0)
                break;
            E++; /* at most one iteration is possible */
        }
    }
 output:
    if (fmt == JS_DTOA_FORMAT_FIXED)
        E_max = n_digits;
    else
        E_max = dtoa_max_digits_table[radix - 2] + 4;
    if ((flags & JS_DTOA_EXP_MASK) == JS_DTOA_EXP_ENABLED ||
        ((flags & JS_DTOA_EXP_MASK) == JS_DTOA_EXP_AUTO && (E <= -6 || E > E_max))) {
        q += output_digits(q, tmp1, radix, P, 1);
        E--;
        if (radix == 10) {
            *q++ = 'e';
        } else if (radix1 == 1 && radix_shift <= 4) {
            E *= radix_shift;
            *q++ = 'p';
        } else {
            *q++ = '@';
        }
        if (E < 0) {
            *q++ = '-';
            E = -E;
        } else {
            *q++ = '+';
        }
        q += u32toa(q, E);
    } else if (E <= 0) {
        *q++ = '0';
        *q++ = '.';
        for(i = 0; i < -E; i++)
            *q++ = '0';
        q += output_digits(q, tmp1, radix, P, P);
    } else {
        q += output_digits(q, tmp1, radix, P, min_int(P, E));
        for(i = 0; i < E - P; i++)
            *q++ = '0';
    }
 done:
    *q = '\0';
    dtoa_free(mant_max);
    dtoa_free(tmp1);
    return q - buf;
}

static inline int to_digit(int c)
{
    if (c >= '0' && c <= '9')
        return c - '0';
    else if (c >= 'A' && c <= 'Z')
        return c - 'A' + 10;
    else if (c >= 'a' && c <= 'z')
        return c - 'a' + 10;
    else
        return 36;
}

/* r = r * radix_base + a. radix_base = 0 means radix_base = 2^32 */
static void mpb_mul1_base(mpb_t *r, limb_t radix_base, limb_t a)
{
    int i;
    if (r->tab[0] == 0 && r->len == 1) {
        r->tab[0] = a;
    } else {
        if (radix_base == 0) {
            for(i = r->len; i >= 0; i--) {
                r->tab[i + 1] = r->tab[i];
            }
            r->tab[0] = a;
        } else {
            r->tab[r->len] = mp_mul1(r->tab, r->tab, r->len,
                                     radix_base, a);
        }
        r->len++;
        mpb_renorm(r);
    }
}

/* XXX: add fast path for small integers */
double js_atod(const char *str, const char **pnext, int radix, int flags,
               JSATODTempMem *tmp_mem)
{
    uint64_t *mptr = tmp_mem->mem;
    const char *p, *p_start;
    limb_t cur_limb, radix_base, extra_digits;
    int is_neg, digit_count, limb_digit_count, digits_per_limb, sep, radix1, radix_shift;
    int radix_bits, expn, e, max_digits, expn_offset, dot_pos, sig_pos, pos;
    mpb_t *tmp0;
    double dval;
    bool is_bin_exp, is_zero, expn_overflow;
    uint64_t m, a;

    tmp0 = dtoa_malloc(&mptr, sizeof(mpb_t) + sizeof(limb_t) * DBIGNUM_LEN_MAX);
    assert((mptr - tmp_mem->mem) <= sizeof(JSATODTempMem) / sizeof(mptr[0]));
    /* optional separator between digits */
    sep = (flags & JS_ATOD_ACCEPT_UNDERSCORES) ? '_' : 256;

    p = str;
    is_neg = 0;
    if (p[0] == '+') {
        p++;
        p_start = p;
    } else if (p[0] == '-') {
        is_neg = 1;
        p++;
        p_start = p;
    } else {
        p_start = p;
    }
    
    if (p[0] == '0') {
        if ((p[1] == 'x' || p[1] == 'X') &&
            (radix == 0 || radix == 16)) {
            p += 2;
            radix = 16;
        } else if ((p[1] == 'o' || p[1] == 'O') &&
                   radix == 0 && (flags & JS_ATOD_ACCEPT_BIN_OCT)) {
            p += 2;
            radix = 8;
        } else if ((p[1] == 'b' || p[1] == 'B') &&
                   radix == 0 && (flags & JS_ATOD_ACCEPT_BIN_OCT)) {
            p += 2;
            radix = 2;
        } else if ((p[1] >= '0' && p[1] <= '9') &&
                   radix == 0 && (flags & JS_ATOD_ACCEPT_LEGACY_OCTAL)) {
            int i;
            sep = 256;
            for (i = 1; (p[i] >= '0' && p[i] <= '7'); i++)
                continue;
            if (p[i] == '8' || p[i] == '9')
                goto no_prefix;
            p += 1;
            radix = 8;
        } else {
            goto no_prefix;
        }
        /* there must be a digit after the prefix */
        if (to_digit((uint8_t)*p) >= radix)
            goto fail;
    no_prefix: ;
    } else {
        if (!(flags & JS_ATOD_INT_ONLY) && js__strstart(p, "Infinity", &p))
            goto overflow;
    }
    if (radix == 0)
        radix = 10;

    cur_limb = 0;
    expn_offset = 0;
    digit_count = 0;
    limb_digit_count = 0;
    max_digits = atod_max_digits_table[radix - 2];
    digits_per_limb = digits_per_limb_table[radix - 2];
    radix_base = radix_base_table[radix - 2];
    radix_shift = ctz32(radix);
    radix1 = radix >> radix_shift;
    if (radix1 == 1) {
        /* radix = 2^radix_bits */
        radix_bits = radix_shift;
    } else {
        radix_bits = 0;
    }
    tmp0->len = 1;
    tmp0->tab[0] = 0;
    extra_digits = 0;
    pos = 0;
    dot_pos = -1;
    /* skip leading zeros */
    for(;;) {
        if (*p == '.' && (p > p_start || to_digit(p[1]) < radix) &&
            !(flags & JS_ATOD_INT_ONLY)) {
            if (*p == sep)
                goto fail;
            if (dot_pos >= 0)
                break;
            dot_pos = pos;
            p++;
        }
        if (*p == sep && p > p_start && p[1] == '0')
            p++;
        if (*p != '0')
            break;
        p++;
        pos++;
    }
    
    sig_pos = pos;
    for(;;) {
        limb_t c;
        if (*p == '.' && (p > p_start || to_digit(p[1]) < radix) &&
            !(flags & JS_ATOD_INT_ONLY)) {
            if (*p == sep)
                goto fail;
            if (dot_pos >= 0)
                break;
            dot_pos = pos;
            p++;
        }
        if (*p == sep && p > p_start && to_digit(p[1]) < radix)
            p++;
        c = to_digit(*p);
        if (c >= radix)
            break;
        p++;
        pos++;
        if (digit_count < max_digits) {
            /* XXX: could be faster when radix_bits != 0 */
            cur_limb = cur_limb * radix + c;
            limb_digit_count++;
            if (limb_digit_count == digits_per_limb) {
                mpb_mul1_base(tmp0, radix_base, cur_limb);
                cur_limb = 0;
                limb_digit_count = 0;
            }
            digit_count++;
        } else {
            extra_digits |= c;
        }
    }
    if (limb_digit_count != 0) {
        mpb_mul1_base(tmp0, pow_ui(radix, limb_digit_count), cur_limb);
    }
    if (digit_count == 0) {
        is_zero = true;
        expn_offset = 0;
    } else {
        is_zero = false;
        if (dot_pos < 0)
            dot_pos = pos;
        expn_offset = sig_pos + digit_count - dot_pos;
    }
    
    /* Use the extra digits for rounding if the base is a power of
       two. Otherwise they are just truncated. */
    if (radix_bits != 0 && extra_digits != 0) {
        tmp0->tab[0] |= 1;
    }
    
    /* parse the exponent, if any */
    expn = 0;
    expn_overflow = false;
    is_bin_exp = false;
    if (!(flags & JS_ATOD_INT_ONLY) &&
        ((radix == 10 && (*p == 'e' || *p == 'E')) ||
         (radix != 10 && (*p == '@' ||
                          (radix_bits >= 1 && radix_bits <= 4 && (*p == 'p' || *p == 'P'))))) &&
        p > p_start) {
        bool exp_is_neg;
        int c;
        is_bin_exp = (*p == 'p' || *p == 'P');
        p++;
        exp_is_neg = false;
        if (*p == '+') {
            p++;
        } else if (*p == '-') {
            exp_is_neg = true;
            p++;
        }
        c = to_digit(*p);
        if (c >= 10)
            goto fail; /* XXX: could stop before the exponent part */
        expn = c;
        p++;
        for(;;) {
            if (*p == sep && to_digit(p[1]) < 10)
                p++;
            c = to_digit(*p);
            if (c >= 10)
                break;
            if (!expn_overflow) {
                if (unlikely(expn > ((INT32_MAX - 2 - 9) / 10))) {
                    expn_overflow = true;
                } else {
                    expn = expn * 10 + c;
                }
            }
            p++;
        }
        if (exp_is_neg)
            expn = -expn;
        /* if zero result, the exponent can be arbitrarily large */
        if (!is_zero && expn_overflow) {
            if (exp_is_neg)
                a = 0;
            else
                a = (uint64_t)0x7ff << 52; /* infinity */
            goto done;
        }
    }

    if (p == p_start)
        goto fail;

    if (is_zero) {
        a = 0;
    } else {
        int expn1;
        if (radix_bits != 0) {
            if (!is_bin_exp)
                expn *= radix_bits;
            expn -= expn_offset * radix_bits;
            expn1 = expn + digit_count * radix_bits;
            if (expn1 >= 1024 + radix_bits)
                goto overflow;
            else if (expn1 <= -1075)
                goto underflow;
            m = round_to_d(&e, tmp0, -expn, JS_RNDN);
        } else {
            expn -= expn_offset;
            expn1 = expn + digit_count;
            if (expn1 >= max_exponent[radix - 2] + 1)
                goto overflow;
            else if (expn1 <= min_exponent[radix - 2])
                goto underflow;
            m = mul_pow_round_to_d(&e, tmp0, radix1, radix_shift, expn, JS_RNDN);
        }
        if (m == 0) {
        underflow:
            a = 0;
        } else if (e > 1024) {
        overflow:
            /* overflow */
            a = (uint64_t)0x7ff << 52;
        } else if (e < -1073) {
            /* underflow */
            /* XXX: check rounding */
            a = 0;
        } else if (e < -1021) {
            /* subnormal */
            a = m >> (-e - 1021);
        } else {
            a = ((uint64_t)(e + 1022) << 52) | (m & (((uint64_t)1 << 52) - 1));
        }
    }
 done:
    a |= (uint64_t)is_neg << 63;
    dval = uint64_as_float64(a);
 done1:
    if (pnext)
        *pnext = p;
    dtoa_free(tmp0);
    return dval;
 fail:
    dval = NAN;
    goto done1;
}
