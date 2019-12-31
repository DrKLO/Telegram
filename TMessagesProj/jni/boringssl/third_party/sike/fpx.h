#ifndef FPX_H_
#define FPX_H_

#include "utils.h"

#if defined(__cplusplus)
extern "C" {
#endif

// Modular addition, c = a+b mod p.
void sike_fpadd(const felm_t a, const felm_t b, felm_t c);
// Modular subtraction, c = a-b mod p.
void sike_fpsub(const felm_t a, const felm_t b, felm_t c);
// Modular division by two, c = a/2 mod p.
void sike_fpdiv2(const felm_t a, felm_t c);
// Modular correction to reduce field element a in [0, 2*p-1] to [0, p-1].
void sike_fpcorrection(felm_t a);
// Multiprecision multiply, c = a*b, where lng(a) = lng(b) = nwords.
void sike_mpmul(const felm_t a, const felm_t b, dfelm_t c);
// 443-bit Montgomery reduction, c = a mod p. Buffer 'a' is modified after
// call returns.
void sike_fprdc(dfelm_t a, felm_t c);
// Double 2x443-bit multiprecision subtraction, c = c-a-b
void sike_mpdblsubx2_asm(const felm_t a, const felm_t b, felm_t c);
// Multiprecision subtraction, c = a-b
crypto_word_t sike_mpsubx2_asm(const dfelm_t a, const dfelm_t b, dfelm_t c);
// 443-bit multiprecision addition, c = a+b
void sike_mpadd_asm(const felm_t a, const felm_t b, felm_t c);
// Modular negation, a = -a mod p.
void sike_fpneg(felm_t a);
// Copy of a field element, c = a
void sike_fpcopy(const felm_t a, felm_t c);
// Copy a field element, c = a.
void sike_fpzero(felm_t a);
// If option = 0xFF...FF x=y; y=x, otherwise swap doesn't happen. Constant time.
void sike_cswap_asm(point_proj_t x, point_proj_t y, const crypto_word_t option);
// Conversion from Montgomery representation to standard representation,
// c = ma*R^(-1) mod p = a mod p, where ma in [0, p-1].
void sike_from_mont(const felm_t ma, felm_t c);
// Field multiplication using Montgomery arithmetic, c = a*b*R^-1 mod p443, where R=2^768
void sike_fpmul_mont(const felm_t ma, const felm_t mb, felm_t mc);
// GF(p443^2) multiplication using Montgomery arithmetic, c = a*b in GF(p443^2)
void sike_fp2mul_mont(const f2elm_t a, const f2elm_t b, f2elm_t c);
// GF(p443^2) inversion using Montgomery arithmetic, a = (a0-i*a1)/(a0^2+a1^2)
void sike_fp2inv_mont(f2elm_t a);
// GF(p^2) squaring using Montgomery arithmetic, c = a^2 in GF(p^2).
void sike_fp2sqr_mont(const f2elm_t a, f2elm_t c);
// Modular correction, a = a in GF(p^2).
void sike_fp2correction(f2elm_t a);

#if defined(__cplusplus)
}  // extern C
#endif

// GF(p^2) addition, c = a+b in GF(p^2).
#define sike_fp2add(a, b, c)             \
do {                                     \
    sike_fpadd(a->c0, b->c0, c->c0);     \
    sike_fpadd(a->c1, b->c1, c->c1);     \
} while(0)

// GF(p^2) subtraction, c = a-b in GF(p^2).
#define sike_fp2sub(a,b,c)               \
do {                                     \
    sike_fpsub(a->c0, b->c0, c->c0);     \
    sike_fpsub(a->c1, b->c1, c->c1);     \
} while(0)

// Copy a GF(p^2) element, c = a.
#define sike_fp2copy(a, c)               \
do {                                     \
    sike_fpcopy(a->c0, c->c0);           \
    sike_fpcopy(a->c1, c->c1);           \
} while(0)

// GF(p^2) negation, a = -a in GF(p^2).
#define sike_fp2neg(a)                   \
do {                                     \
    sike_fpneg(a->c0);                   \
    sike_fpneg(a->c1);                   \
} while(0)

// GF(p^2) division by two, c = a/2  in GF(p^2).
#define sike_fp2div2(a, c)               \
do {                                     \
    sike_fpdiv2(a->c0, c->c0);           \
    sike_fpdiv2(a->c1, c->c1);           \
} while(0)

// Modular correction, a = a in GF(p^2).
#define sike_fp2correction(a)            \
do {                                     \
    sike_fpcorrection(a->c0);            \
    sike_fpcorrection(a->c1);            \
} while(0)

// Conversion of a GF(p^2) element to Montgomery representation,
// mc_i = a_i*R^2*R^(-1) = a_i*R in GF(p^2).
#define sike_to_fp2mont(a, mc)                           \
  do {                                                   \
    sike_fpmul_mont(a->c0, sike_params.mont_R2, mc->c0); \
    sike_fpmul_mont(a->c1, sike_params.mont_R2, mc->c1); \
  } while (0)

// Conversion of a GF(p^2) element from Montgomery representation to standard representation,
// c_i = ma_i*R^(-1) = a_i in GF(p^2).
#define sike_from_fp2mont(ma, c)         \
do {                                     \
    sike_from_mont(ma->c0, c->c0);       \
    sike_from_mont(ma->c1, c->c1);       \
} while(0)

#endif // FPX_H_
