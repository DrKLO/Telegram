/********************************************************************************************
* SIDH: an efficient supersingular isogeny cryptography library
*
* Abstract: internal header file for P434
*********************************************************************************************/

#ifndef UTILS_H_
#define UTILS_H_

#include <openssl/base.h>

#include "../crypto/internal.h"
#include "sike.h"

// Conversion macro from number of bits to number of bytes
#define BITS_TO_BYTES(nbits)      (((nbits)+7)/8)

// Bit size of the field
#define BITS_FIELD              434
// Byte size of the field
#define FIELD_BYTESZ            BITS_TO_BYTES(BITS_FIELD)
// Number of 64-bit words of a 224-bit element
#define NBITS_ORDER             224
#define NWORDS64_ORDER          ((NBITS_ORDER+63)/64)
// Number of elements in Alice's strategy
#define A_max                   108
// Number of elements in Bob's strategy
#define B_max                   137
// Word size size
#define RADIX                   sizeof(crypto_word_t)*8
// Byte size of a limb
#define LSZ                     sizeof(crypto_word_t)

#if defined(OPENSSL_64_BIT)
    // Number of words of a 434-bit field element
    #define NWORDS_FIELD    7
    // Number of "0" digits in the least significant part of p434 + 1
    #define ZERO_WORDS 3
    // U64_TO_WORDS expands |x| for a |crypto_word_t| array literal.
    #define U64_TO_WORDS(x) UINT64_C(x)
#else
    // Number of words of a 434-bit field element
    #define NWORDS_FIELD    14
    // Number of "0" digits in the least significant part of p434 + 1
    #define ZERO_WORDS 6
    // U64_TO_WORDS expands |x| for a |crypto_word_t| array literal.
    #define U64_TO_WORDS(x) \
        (uint32_t)(UINT64_C(x) & 0xffffffff), (uint32_t)(UINT64_C(x) >> 32)
#endif

// Extended datatype support
#if !defined(BORINGSSL_HAS_UINT128)
    typedef uint64_t uint128_t[2];
#endif

// The following functions return 1 (TRUE) if condition is true, 0 (FALSE) otherwise
// Digit multiplication
#define MUL(multiplier, multiplicand, hi, lo) digit_x_digit((multiplier), (multiplicand), &(lo));

// If mask |x|==0xff.ff set |x| to 1, otherwise 0
#define M2B(x) ((x)>>(RADIX-1))

// Digit addition with carry
#define ADDC(carryIn, addend1, addend2, carryOut, sumOut)                   \
do {                                                                        \
  crypto_word_t tempReg = (addend1) + (crypto_word_t)(carryIn);             \
  (sumOut) = (addend2) + tempReg;                                           \
  (carryOut) = M2B(constant_time_lt_w(tempReg, (crypto_word_t)(carryIn)) |  \
                   constant_time_lt_w((sumOut), tempReg));                  \
} while(0)

// Digit subtraction with borrow
#define SUBC(borrowIn, minuend, subtrahend, borrowOut, differenceOut)           \
do {                                                                            \
    crypto_word_t tempReg = (minuend) - (subtrahend);                           \
    crypto_word_t borrowReg = M2B(constant_time_lt_w((minuend), (subtrahend))); \
    borrowReg |= ((borrowIn) & constant_time_is_zero_w(tempReg));               \
    (differenceOut) = tempReg - (crypto_word_t)(borrowIn);                      \
    (borrowOut) = borrowReg;                                                    \
} while(0)

/* Old GCC 4.9 (jessie) doesn't implement {0} initialization properly,
   which violates C11 as described in 6.7.9, 21 (similarily C99, 6.7.8).
   Defines below are used to work around the bug, and provide a way
   to initialize f2elem_t and point_proj_t structs.
   Bug has been fixed in GCC6 (debian stretch).
*/
#define F2ELM_INIT {{ {0}, {0} }}
#define POINT_PROJ_INIT {{ F2ELM_INIT, F2ELM_INIT }}

// Datatype for representing 434-bit field elements (448-bit max.)
// Elements over GF(p434) are encoded in 63 octets in little endian format
// (i.e., the least significant octet is located in the lowest memory address).
typedef crypto_word_t felm_t[NWORDS_FIELD];

// An element in F_{p^2}, is composed of two coefficients from F_p, * i.e.
// Fp2 element = c0 + c1*i in F_{p^2}
// Datatype for representing double-precision 2x434-bit field elements (448-bit max.)
// Elements (a+b*i) over GF(p434^2), where a and b are defined over GF(p434), are
// encoded as {a, b}, with a in the lowest memory portion.
typedef struct {
    felm_t c0;
    felm_t c1;
} fp2;

// Our F_{p^2} element type is a pointer to the struct.
typedef fp2 f2elm_t[1];

// Datatype for representing double-precision 2x434-bit
// field elements in contiguous memory.
typedef crypto_word_t dfelm_t[2*NWORDS_FIELD];

// Constants used during SIKE computation.
struct params_t {
    // Stores a prime
    const crypto_word_t prime[NWORDS_FIELD];
    // Stores prime + 1
    const crypto_word_t prime_p1[NWORDS_FIELD];
    // Stores prime * 2
    const crypto_word_t prime_x2[NWORDS_FIELD];
    // Alice's generator values {XPA0 + XPA1*i, XQA0 + XQA1*i, XRA0 + XRA1*i}
    // in GF(prime^2), expressed in Montgomery representation
    const crypto_word_t A_gen[6*NWORDS_FIELD];
    // Bob's generator values {XPB0 + XPB1*i, XQB0 + XQB1*i, XRB0 + XRB1*i}
    // in GF(prime^2), expressed in Montgomery representation
    const crypto_word_t B_gen[6*NWORDS_FIELD];
    // Montgomery constant mont_R2 = (2^448)^2 mod prime
    const crypto_word_t mont_R2[NWORDS_FIELD];
    // Value 'one' in Montgomery representation
    const crypto_word_t mont_one[NWORDS_FIELD];
    // Value '6' in Montgomery representation
    const crypto_word_t mont_six[NWORDS_FIELD];
    // Fixed parameters for isogeny tree computation
    const unsigned int A_strat[A_max-1];
    const unsigned int B_strat[B_max-1];
};

// Point representation in projective XZ Montgomery coordinates.
typedef struct {
    f2elm_t X;
    f2elm_t Z;
} point_proj;
typedef point_proj point_proj_t[1];

#endif // UTILS_H_
