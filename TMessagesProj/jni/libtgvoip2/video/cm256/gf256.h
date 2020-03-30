/*
    Copyright (c) 2015 Christopher A. Taylor.  All rights reserved.

    Redistribution and use in source and binary forms, with or without
    modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice,
      this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright notice,
      this list of conditions and the following disclaimer in the documentation
      and/or other materials provided with the distribution.
    * Neither the name of CM256 nor the names of its contributors may be
      used to endorse or promote products derived from this software without
      specific prior written permission.

    THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
    AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
    IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
    ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
    LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
    CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
    SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
    INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
    CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
    ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
    POSSIBILITY OF SUCH DAMAGE.
*/

#ifndef GF256_H
#define GF256_H

#include <stdint.h> // uint32_t etc
#include <string.h> // memcpy, memset
#include "export.h"

// TBD: Fix the polynomial at one value and use precomputed tables here to
// simplify the API for GF256.h version 2.  Avoids user data alignment issues.


//-----------------------------------------------------------------------------
// Platform-Specific Definitions
//
// Edit these to port to your architecture

#if defined(USE_SSSE3)

#ifdef _MSC_VER

    // Compiler-specific 128-bit SIMD register keyword
    #define GF256_M128 __m128i

    // Compiler-specific C++11 restrict keyword
    #define GF256_RESTRICT_KW __restrict

    // Compiler-specific force inline keyword
    #define GF256_FORCE_INLINE __forceinline

    // Compiler-specific alignment keyword
    #define GF256_ALIGNED __declspec(align(16))

    // Compiler-specific SSE headers
    #include <tmmintrin.h> // SSE3: _mm_shuffle_epi8
    #include <emmintrin.h> // SSE2

#else

    // Compiler-specific 128-bit SIMD register keyword
    #define GF256_M128 __m128i

    // Compiler-specific C++11 restrict keyword
    #define GF256_RESTRICT_KW __restrict__

    // Compiler-specific force inline keyword
    #define GF256_FORCE_INLINE __attribute__((always_inline)) inline

    // Compiler-specific alignment keyword
    #define GF256_ALIGNED __attribute__((aligned(16)))

    // Compiler-specific SSE headers
    #include <x86intrin.h>

#endif

#elif defined(USE_NEON)

    #include "sse2neon.h"

    // Compiler-specific 128-bit SIMD register keyword
    #define GF256_M128 __m128i

    // Compiler-specific C++11 restrict keyword
    #define GF256_RESTRICT_KW __restrict__

    // Compiler-specific force inline keyword
    #define GF256_FORCE_INLINE __attribute__((always_inline)) inline

    // Compiler-specific alignment keyword
    #define GF256_ALIGNED __attribute__((aligned(16)))

#endif

#if defined(NO_RESTRICT)
    #define GF256_RESTRICT
#else
    #define GF256_RESTRICT GF256_RESTRICT_KW
#endif

#ifndef nullptr
    #define nullptr NULL
#endif

//-----------------------------------------------------------------------------
// GF(256) Context
//
// The context object stores tables required to perform library calculations.
//
// Usage Notes:
// This struct should be aligned in memory, meaning that a pointer to it should
// have the low 4 bits cleared.  To achieve this simply tag the gf256_ctx object
// with the GF256_ALIGNED macro provided above.

#ifdef _MSC_VER
    #pragma warning(push)
    #pragma warning(disable: 4324) // warning C4324: 'gf256_ctx' : structure was padded due to __declspec(align())
#endif

class CM256CC_API gf256_ctx // 141,072 bytes
{
public:
    gf256_ctx();
    ~gf256_ctx();

    bool isInitialized() const { return initialized; }

    /** Performs "x[] += y[]" bulk memory XOR operation */
    static void gf256_add_mem(void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes);
    /** Performs "z[] += x[] + y[]" bulk memory operation */
    static void gf256_add2_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes);
    /** Performs "z[] = x[] + y[]" bulk memory operation */
    static void gf256_addset_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes);
    /** Swap two memory buffers in-place */
    static void gf256_memswap(void * GF256_RESTRICT vx, void * GF256_RESTRICT vy, int bytes);

    // return x + y
    static GF256_FORCE_INLINE uint8_t gf256_add(const uint8_t x, const uint8_t y)
    {
        return x ^ y;
    }

    // return x * y
    // For repeated multiplication by a constant, it is faster to put the constant in y.
    GF256_FORCE_INLINE uint8_t gf256_mul(uint8_t x, uint8_t y)
    {
        return GF256_MUL_TABLE[((unsigned)y << 8) + x];
    }

    // return x / y
    // Memory-access optimized for constant divisors in y.
    GF256_FORCE_INLINE uint8_t gf256_div(uint8_t x, uint8_t y)
    {
        return GF256_DIV_TABLE[((unsigned)y << 8) + x];
    }

    // return 1 / x
    GF256_FORCE_INLINE uint8_t gf256_inv(uint8_t x)
    {
        return GF256_INV_TABLE[x];
    }

    // This function generates each matrix element based on x_i, x_0, y_j
    // Note that for x_i == x_0, this will return 1, so it is better to unroll out the first row.
    GF256_FORCE_INLINE unsigned char getMatrixElement(const unsigned char x_i, const unsigned char x_0, const unsigned char y_j)
    {
        return gf256_div(gf256_add(y_j, x_0), gf256_add(x_i, y_j));
    }

    /** Performs "z[] = x[] * y" bulk memory operation */
    void gf256_mul_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, uint8_t y, int bytes);
    /** Performs "z[] += x[] * y" bulk memory operation */
    void gf256_muladd_mem(void * GF256_RESTRICT vz, uint8_t y, const void * GF256_RESTRICT vx, int bytes);

    /** Performs "x[] /= y" bulk memory operation */
    GF256_FORCE_INLINE void gf256_div_mem(void * GF256_RESTRICT vz,
                                                 const void * GF256_RESTRICT vx, uint8_t y, int bytes)
    {
        gf256_mul_mem(vz, vx, GF256_INV_TABLE[y], bytes); // Multiply by inverse
    }

    // Polynomial used
    unsigned Polynomial;

    // Log/Exp tables
    uint16_t GF256_LOG_TABLE[256];
    uint8_t GF256_EXP_TABLE[512 * 2 + 1];

    // Mul/Div/Inv tables
    uint8_t GF256_MUL_TABLE[256 * 256];
    uint8_t GF256_DIV_TABLE[256 * 256];
    uint8_t GF256_INV_TABLE[256];

    // Muladd_mem tables
    // We require memory to be aligned since the SIMD instructions benefit from
    // aligned accesses to the MM256_* table data.
    GF256_ALIGNED GF256_M128 MM256_TABLE_LO_Y[256];
    GF256_ALIGNED GF256_M128 MM256_TABLE_HI_Y[256];

private:
    int gf256_init_();

    void gf255_poly_init(int polynomialIndex); //!< Select which polynomial to use
    void gf256_explog_init();                  //!< Construct EXP and LOG tables from polynomial
    void gf256_muldiv_init();                  //!< Initialize MUL and DIV tables using LOG and EXP tables
    void gf256_inv_init();                     //!< Initialize INV table using DIV table
    void gf256_muladd_mem_init();              //!< Initialize the MM256 tables using gf256_mul()

    static bool IsLittleEndian()
    {
        int x = 1;
        char *y = (char *) &x;

        return *y != 0;
    }

    //-----------------------------------------------------------------------------
    // Generator Polynomial

    // There are only 16 irreducible polynomials for GF(256)
    static const int GF256_GEN_POLY_COUNT = 16;
    static const uint8_t GF256_GEN_POLY[GF256_GEN_POLY_COUNT];
    static const int DefaultPolynomialIndex = 3;

    bool initialized;
};

#ifdef _MSC_VER
    #pragma warning(pop)
#endif


#endif // GF256_H
