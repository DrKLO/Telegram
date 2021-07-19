/********************************************************************************************
* SIDH: an efficient supersingular isogeny cryptography library
*
* Abstract: portable modular arithmetic for P503
*********************************************************************************************/

#include <openssl/base.h>

#if defined(OPENSSL_NO_ASM) || \
    (!defined(OPENSSL_X86_64) && !defined(OPENSSL_AARCH64))

#include "../utils.h"
#include "../fpx.h"

// Global constants
extern const struct params_t sike_params;

static void digit_x_digit(const crypto_word_t a, const crypto_word_t b, crypto_word_t* c)
{ // Digit multiplication, digit * digit -> 2-digit result
    crypto_word_t al, ah, bl, bh, temp;
    crypto_word_t albl, albh, ahbl, ahbh, res1, res2, res3, carry;
    crypto_word_t mask_low = (crypto_word_t)(-1) >> (sizeof(crypto_word_t)*4);
    crypto_word_t mask_high = (crypto_word_t)(-1) << (sizeof(crypto_word_t)*4);

    al = a & mask_low;                              // Low part
    ah = a >> (sizeof(crypto_word_t) * 4);          // High part
    bl = b & mask_low;
    bh = b >> (sizeof(crypto_word_t) * 4);

    albl = al*bl;
    albh = al*bh;
    ahbl = ah*bl;
    ahbh = ah*bh;
    c[0] = albl & mask_low;                         // C00

    res1 = albl >> (sizeof(crypto_word_t) * 4);
    res2 = ahbl & mask_low;
    res3 = albh & mask_low;
    temp = res1 + res2 + res3;
    carry = temp >> (sizeof(crypto_word_t) * 4);
    c[0] ^= temp << (sizeof(crypto_word_t) * 4);    // C01

    res1 = ahbl >> (sizeof(crypto_word_t) * 4);
    res2 = albh >> (sizeof(crypto_word_t) * 4);
    res3 = ahbh & mask_low;
    temp = res1 + res2 + res3 + carry;
    c[1] = temp & mask_low;                         // C10
    carry = temp & mask_high;
    c[1] ^= (ahbh & mask_high) + carry;             // C11
}

void sike_fpadd(const felm_t a, const felm_t b, felm_t c)
{ // Modular addition, c = a+b mod p434.
  // Inputs: a, b in [0, 2*p434-1]
  // Output: c in [0, 2*p434-1]
    unsigned int i, carry = 0;
    crypto_word_t mask;

    for (i = 0; i < NWORDS_FIELD; i++) {
        ADDC(carry, a[i], b[i], carry, c[i]);
    }

    carry = 0;
    for (i = 0; i < NWORDS_FIELD; i++) {
        SUBC(carry, c[i], sike_params.prime_x2[i], carry, c[i]);
    }
    mask = 0 - (crypto_word_t)carry;

    carry = 0;
    for (i = 0; i < NWORDS_FIELD; i++) {
        ADDC(carry, c[i], sike_params.prime_x2[i] & mask, carry, c[i]);
    }
}

void sike_fpsub(const felm_t a, const felm_t b, felm_t c)
{ // Modular subtraction, c = a-b mod p434.
  // Inputs: a, b in [0, 2*p434-1]
  // Output: c in [0, 2*p434-1]
    unsigned int i, borrow = 0;
    crypto_word_t mask;

    for (i = 0; i < NWORDS_FIELD; i++) {
        SUBC(borrow, a[i], b[i], borrow, c[i]);
    }
    mask = 0 - (crypto_word_t)borrow;

    borrow = 0;
    for (i = 0; i < NWORDS_FIELD; i++) {
        ADDC(borrow, c[i], sike_params.prime_x2[i] & mask, borrow, c[i]);
    }
}

void sike_mpmul(const felm_t a, const felm_t b, dfelm_t c)
{ // Multiprecision comba multiply, c = a*b, where lng(a) = lng(b) = NWORDS_FIELD.
    unsigned int i, j;
    crypto_word_t t = 0, u = 0, v = 0, UV[2];
    unsigned int carry = 0;

    for (i = 0; i < NWORDS_FIELD; i++) {
        for (j = 0; j <= i; j++) {
            MUL(a[j], b[i-j], UV+1, UV[0]);
            ADDC(0, UV[0], v, carry, v);
            ADDC(carry, UV[1], u, carry, u);
            t += carry;
        }
        c[i] = v;
        v = u;
        u = t;
        t = 0;
    }

    for (i = NWORDS_FIELD; i < 2*NWORDS_FIELD-1; i++) {
        for (j = i-NWORDS_FIELD+1; j < NWORDS_FIELD; j++) {
            MUL(a[j], b[i-j], UV+1, UV[0]);
            ADDC(0, UV[0], v, carry, v);
            ADDC(carry, UV[1], u, carry, u);
            t += carry;
        }
        c[i] = v;
        v = u;
        u = t;
        t = 0;
    }
    c[2*NWORDS_FIELD-1] = v;
}

void sike_fprdc(felm_t ma, felm_t mc)
{ // Efficient Montgomery reduction using comba and exploiting the special form of the prime p434.
  // mc = ma*R^-1 mod p434x2, where R = 2^448.
  // If ma < 2^448*p434, the output mc is in the range [0, 2*p434-1].
  // ma is assumed to be in Montgomery representation.
    unsigned int i, j, carry, count = ZERO_WORDS;
    crypto_word_t UV[2], t = 0, u = 0, v = 0;

    for (i = 0; i < NWORDS_FIELD; i++) {
        mc[i] = 0;
    }

    for (i = 0; i < NWORDS_FIELD; i++) {
        for (j = 0; j < i; j++) {
            if (j < (i-ZERO_WORDS+1)) {
                MUL(mc[j], sike_params.prime_p1[i-j], UV+1, UV[0]);
                ADDC(0, UV[0], v, carry, v);
                ADDC(carry, UV[1], u, carry, u);
                t += carry;
            }
        }
        ADDC(0, v, ma[i], carry, v);
        ADDC(carry, u, 0, carry, u);
        t += carry;
        mc[i] = v;
        v = u;
        u = t;
        t = 0;
    }

    for (i = NWORDS_FIELD; i < 2*NWORDS_FIELD-1; i++) {
        if (count > 0) {
            count -= 1;
        }
        for (j = i-NWORDS_FIELD+1; j < NWORDS_FIELD; j++) {
            if (j < (NWORDS_FIELD-count)) {
                MUL(mc[j], sike_params.prime_p1[i-j], UV+1, UV[0]);
                ADDC(0, UV[0], v, carry, v);
                ADDC(carry, UV[1], u, carry, u);
                t += carry;
            }
        }
        ADDC(0, v, ma[i], carry, v);
        ADDC(carry, u, 0, carry, u);
        t += carry;
        mc[i-NWORDS_FIELD] = v;
        v = u;
        u = t;
        t = 0;
    }
    ADDC(0, v, ma[2*NWORDS_FIELD-1], carry, v);
    mc[NWORDS_FIELD-1] = v;
}

#endif  // NO_ASM || (!X86_64 && !AARCH64)
