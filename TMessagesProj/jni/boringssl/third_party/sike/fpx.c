/********************************************************************************************
* SIDH: an efficient supersingular isogeny cryptography library
*
* Abstract: core functions over GF(p) and GF(p^2)
*********************************************************************************************/
#include <openssl/base.h>

#include "utils.h"
#include "fpx.h"

extern const struct params_t sike_params;

// Multiprecision squaring, c = a^2 mod p.
static void fpsqr_mont(const felm_t ma, felm_t mc)
{
    dfelm_t temp = {0};
    sike_mpmul(ma, ma, temp);
    sike_fprdc(temp, mc);
}

// Chain to compute a^(p-3)/4 using Montgomery arithmetic.
static void fpinv_chain_mont(felm_t a)
{
    unsigned int i, j;
    felm_t t[31], tt;

    // Precomputed table
    fpsqr_mont(a, tt);
    sike_fpmul_mont(a, tt, t[0]);
    for (i = 0; i <= 29; i++) sike_fpmul_mont(t[i], tt, t[i+1]);

    sike_fpcopy(a, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[5], tt, tt);
    for (i = 0; i < 10; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[14], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[3], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[23], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[13], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[24], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[7], tt, tt);
    for (i = 0; i < 8; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[12], tt, tt);
    for (i = 0; i < 8; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[30], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[1], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[30], tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[21], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[2], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[19], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[1], tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[24], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[26], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[16], tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[10], tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[6], tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[0], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[20], tt, tt);
    for (i = 0; i < 8; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[9], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[25], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[30], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[26], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(a, tt, tt);
    for (i = 0; i < 7; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[28], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[6], tt, tt);
    for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[10], tt, tt);
    for (i = 0; i < 9; i++) fpsqr_mont(tt, tt);
    sike_fpmul_mont(t[22], tt, tt);
    for (j = 0; j < 35; j++) {
        for (i = 0; i < 6; i++) fpsqr_mont(tt, tt);
        sike_fpmul_mont(t[30], tt, tt);
    }
    sike_fpcopy(tt, a);
}

// Field inversion using Montgomery arithmetic, a = a^(-1)*R mod p.
static void fpinv_mont(felm_t a)
{
    felm_t tt = {0};
    sike_fpcopy(a, tt);
    fpinv_chain_mont(tt);
    fpsqr_mont(tt, tt);
    fpsqr_mont(tt, tt);
    sike_fpmul_mont(a, tt, a);
}

// Multiprecision addition, c = a+b, where lng(a) = lng(b) = nwords. Returns the carry bit.
#if defined(OPENSSL_NO_ASM) || (!defined(OPENSSL_X86_64) && !defined(OPENSSL_AARCH64))
inline static unsigned int mp_add(const felm_t a, const felm_t b, felm_t c, const unsigned int nwords) {
    uint8_t carry = 0;
    for (size_t i = 0; i < nwords; i++) {
        ADDC(carry, a[i], b[i], carry, c[i]);
    }
    return carry;
}

// Multiprecision subtraction, c = a-b, where lng(a) = lng(b) = nwords. Returns the borrow bit.
inline static unsigned int mp_sub(const felm_t a, const felm_t b, felm_t c, const unsigned int nwords) {
    uint32_t borrow = 0;
    for (size_t i = 0; i < nwords; i++) {
        SUBC(borrow, a[i], b[i], borrow, c[i]);
    }
    return borrow;
}
#endif

// Multiprecision addition, c = a+b.
inline static void mp_addfast(const felm_t a, const felm_t b, felm_t c)
{
#if defined(OPENSSL_NO_ASM) || (!defined(OPENSSL_X86_64) && !defined(OPENSSL_AARCH64))
    mp_add(a, b, c, NWORDS_FIELD);
#else
    sike_mpadd_asm(a, b, c);
#endif
}

// Multiprecision subtraction, c = a-b, where lng(a) = lng(b) = 2*NWORDS_FIELD.
// If c < 0 then returns mask = 0xFF..F, else mask = 0x00..0
inline static crypto_word_t mp_subfast(const dfelm_t a, const dfelm_t b, dfelm_t c) {
#if defined(OPENSSL_NO_ASM) || (!defined(OPENSSL_X86_64) && !defined(OPENSSL_AARCH64))
    return (0 - (crypto_word_t)mp_sub(a, b, c, 2*NWORDS_FIELD));
#else
    return sike_mpsubx2_asm(a, b, c);
#endif
}

// Multiprecision subtraction, c = c-a-b, where lng(a) = lng(b) = 2*NWORDS_FIELD.
// Inputs should be s.t. c > a and c > b
inline static void mp_dblsubfast(const dfelm_t a, const dfelm_t b, dfelm_t c) {
#if defined(OPENSSL_NO_ASM) || (!defined(OPENSSL_X86_64) && !defined(OPENSSL_AARCH64))
    mp_sub(c, a, c, 2*NWORDS_FIELD);
    mp_sub(c, b, c, 2*NWORDS_FIELD);
#else
    sike_mpdblsubx2_asm(a, b, c);
#endif
}

// Copy a field element, c = a.
void sike_fpcopy(const felm_t a, felm_t c) {
    for (size_t i = 0; i < NWORDS_FIELD; i++) {
        c[i] = a[i];
    }
}

// Field multiplication using Montgomery arithmetic, c = a*b*R^-1 mod prime, where R=2^768
void sike_fpmul_mont(const felm_t ma, const felm_t mb, felm_t mc)
{
    dfelm_t temp = {0};
    sike_mpmul(ma, mb, temp);
    sike_fprdc(temp, mc);
}

// Conversion from Montgomery representation to standard representation,
// c = ma*R^(-1) mod p = a mod p, where ma in [0, p-1].
void sike_from_mont(const felm_t ma, felm_t c)
{
    felm_t one = {0};
    one[0] = 1;

    sike_fpmul_mont(ma, one, c);
    sike_fpcorrection(c);
}

// GF(p^2) squaring using Montgomery arithmetic, c = a^2 in GF(p^2).
// Inputs: a = a0+a1*i, where a0, a1 are in [0, 2*p-1]
// Output: c = c0+c1*i, where c0, c1 are in [0, 2*p-1]
void sike_fp2sqr_mont(const f2elm_t a, f2elm_t c) {
    felm_t t1, t2, t3;

    mp_addfast(a->c0, a->c1, t1);                      // t1 = a0+a1
    sike_fpsub(a->c0, a->c1, t2);                      // t2 = a0-a1
    mp_addfast(a->c0, a->c0, t3);                      // t3 = 2a0
    sike_fpmul_mont(t1, t2, c->c0);                    // c0 = (a0+a1)(a0-a1)
    sike_fpmul_mont(t3, a->c1, c->c1);                 // c1 = 2a0*a1
}

// Modular negation, a = -a mod p503.
// Input/output: a in [0, 2*p503-1]
void sike_fpneg(felm_t a) {
  uint32_t borrow = 0;
  for (size_t i = 0; i < NWORDS_FIELD; i++) {
    SUBC(borrow, sike_params.prime_x2[i], a[i], borrow, a[i]);
  }
}

// Modular division by two, c = a/2 mod p503.
// Input : a in [0, 2*p503-1]
// Output: c in [0, 2*p503-1]
void sike_fpdiv2(const felm_t a, felm_t c) {
  uint32_t carry = 0;
  crypto_word_t mask;

  mask = 0 - (crypto_word_t)(a[0] & 1);    // If a is odd compute a+p503
  for (size_t i = 0; i < NWORDS_FIELD; i++) {
    ADDC(carry, a[i], sike_params.prime[i] & mask, carry, c[i]);
  }

  // Multiprecision right shift by one.
  for (size_t i = 0; i < NWORDS_FIELD-1; i++) {
    c[i] = (c[i] >> 1) ^ (c[i+1] << (RADIX - 1));
  }
  c[NWORDS_FIELD-1] >>= 1;
}

// Modular correction to reduce field element a in [0, 2*p503-1] to [0, p503-1].
void sike_fpcorrection(felm_t a) {
  uint32_t borrow = 0;
  crypto_word_t mask;

  for (size_t i = 0; i < NWORDS_FIELD; i++) {
    SUBC(borrow, a[i], sike_params.prime[i], borrow, a[i]);
  }
  mask = 0 - (crypto_word_t)borrow;

  borrow = 0;
  for (size_t i = 0; i < NWORDS_FIELD; i++) {
    ADDC(borrow, a[i], sike_params.prime[i] & mask, borrow, a[i]);
  }
}

// GF(p^2) multiplication using Montgomery arithmetic, c = a*b in GF(p^2).
// Inputs: a = a0+a1*i and b = b0+b1*i, where a0, a1, b0, b1 are in [0, 2*p-1]
// Output: c = c0+c1*i, where c0, c1 are in [0, 2*p-1]
void sike_fp2mul_mont(const f2elm_t a, const f2elm_t b, f2elm_t c) {
    felm_t t1, t2;
    dfelm_t tt1, tt2, tt3;
    crypto_word_t mask;

    mp_addfast(a->c0, a->c1, t1);                      // t1 = a0+a1
    mp_addfast(b->c0, b->c1, t2);                      // t2 = b0+b1
    sike_mpmul(a->c0, b->c0, tt1);                     // tt1 = a0*b0
    sike_mpmul(a->c1, b->c1, tt2);                     // tt2 = a1*b1
    sike_mpmul(t1, t2, tt3);                           // tt3 = (a0+a1)*(b0+b1)
    mp_dblsubfast(tt1, tt2, tt3);                      // tt3 = (a0+a1)*(b0+b1) - a0*b0 - a1*b1
    mask = mp_subfast(tt1, tt2, tt1);                  // tt1 = a0*b0 - a1*b1. If tt1 < 0 then mask = 0xFF..F, else if tt1 >= 0 then mask = 0x00..0

    for (size_t i = 0; i < NWORDS_FIELD; i++) {
        t1[i] = sike_params.prime[i] & mask;
    }

    sike_fprdc(tt3, c->c1);                             // c[1] = (a0+a1)*(b0+b1) - a0*b0 - a1*b1
    mp_addfast(&tt1[NWORDS_FIELD], t1, &tt1[NWORDS_FIELD]);
    sike_fprdc(tt1, c->c0);                             // c[0] = a0*b0 - a1*b1
}

// GF(p^2) inversion using Montgomery arithmetic, a = (a0-i*a1)/(a0^2+a1^2).
void sike_fp2inv_mont(f2elm_t a) {
    f2elm_t t1;

    fpsqr_mont(a->c0, t1->c0);                         // t10 = a0^2
    fpsqr_mont(a->c1, t1->c1);                         // t11 = a1^2
    sike_fpadd(t1->c0, t1->c1, t1->c0);                // t10 = a0^2+a1^2
    fpinv_mont(t1->c0);                                // t10 = (a0^2+a1^2)^-1
    sike_fpneg(a->c1);                                 // a = a0-i*a1
    sike_fpmul_mont(a->c0, t1->c0, a->c0);
    sike_fpmul_mont(a->c1, t1->c0, a->c1);             // a = (a0-i*a1)*(a0^2+a1^2)^-1
}
