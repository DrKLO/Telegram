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

#include <stdio.h>
#include <stdlib.h>

#include "gf256.h"

const uint8_t gf256_ctx::GF256_GEN_POLY[GF256_GEN_POLY_COUNT] = {
        0x8e, 0x95, 0x96, 0xa6, 0xaf, 0xb1, 0xb2, 0xb4,
        0xb8, 0xc3, 0xc6, 0xd4, 0xe1, 0xe7, 0xf3, 0xfa,
    };

gf256_ctx::gf256_ctx() :
    initialized(false)
{
    gf256_init_();
}

gf256_ctx::~gf256_ctx()
{
}

// Select which polynomial to use
void gf256_ctx::gf255_poly_init(int polynomialIndex)
{
    if (polynomialIndex < 0 || polynomialIndex >= GF256_GEN_POLY_COUNT)
    {
        polynomialIndex = 0;
    }

    Polynomial = (GF256_GEN_POLY[polynomialIndex] << 1) | 1;
}


//-----------------------------------------------------------------------------
// Exponential and Log Tables

// Construct EXP and LOG tables from polynomial
void gf256_ctx::gf256_explog_init()
{
    unsigned poly = Polynomial;
    uint8_t* exptab = GF256_EXP_TABLE;
    uint16_t* logtab = GF256_LOG_TABLE;

    logtab[0] = 512;
    exptab[0] = 1;
    for (unsigned jj = 1; jj < 255; ++jj)
    {
        unsigned next = (unsigned)exptab[jj - 1] * 2;
        if (next >= 256) next ^= poly;

        exptab[jj] = static_cast<uint8_t>( next );
        logtab[exptab[jj]] = static_cast<uint16_t>( jj );
    }

    exptab[255] = exptab[0];
    logtab[exptab[255]] = 255;

    for (unsigned jj = 256; jj < 2 * 255; ++jj)
    {
        exptab[jj] = exptab[jj % 255];
    }

    exptab[2 * 255] = 1;

    for (unsigned jj = 2 * 255 + 1; jj < 4 * 255; ++jj)
    {
        exptab[jj] = 0;
    }
}


//-----------------------------------------------------------------------------
// Multiply and Divide Tables

// Initialize MUL and DIV tables using LOG and EXP tables
void gf256_ctx::gf256_muldiv_init()
{
    // Allocate table memory 65KB x 2
    uint8_t* m = GF256_MUL_TABLE;
    uint8_t* d = GF256_DIV_TABLE;

    // Unroll y = 0 subtable
    for (int x = 0; x < 256; ++x)
    {
        m[x] = d[x] = 0;
    }

    // For each other y value,
    for (int y = 1; y < 256; ++y)
    {
        // Calculate log(y) for mult and 255 - log(y) for div
        const uint8_t log_y = static_cast<uint8_t>(GF256_LOG_TABLE[y]);
        const uint8_t log_yn = 255 - log_y;

        // Next subtable
        m += 256;
        d += 256;

        // Unroll x = 0
        m[0] = 0;
        d[0] = 0;

        // Calculate x * y, x / y
        for (int x = 1; x < 256; ++x)
        {
            uint16_t log_x = GF256_LOG_TABLE[x];

            m[x] = GF256_EXP_TABLE[log_x + log_y];
            d[x] = GF256_EXP_TABLE[log_x + log_yn];
        }
    }
}


//-----------------------------------------------------------------------------
// Inverse Table

// Initialize INV table using DIV table
void gf256_ctx::gf256_inv_init()
{
    for (int x = 0; x < 256; ++x)
    {
        GF256_INV_TABLE[x] = gf256_div(1, static_cast<uint8_t>(x));
    }
}


//-----------------------------------------------------------------------------
// Multiply and Add Memory Tables

/*
    Fast algorithm to compute m[1..8] = a[1..8] * b in GF(256)
    using SSE3 SIMD instruction set:

    Consider z = x * y in GF(256).
    This operation can be performed bit-by-bit.  Usefully, the partial product
    of each bit is combined linearly with the rest.  This means that the 8-bit
    number x can be split into its high and low 4 bits, and partial products
    can be formed from each half.  Then the halves can be linearly combined:

        z = x[0..3] * y + x[4..7] * y

    The multiplication of each half can be done efficiently via table lookups,
    and the addition in GF(256) is XOR.  There must be two tables that map 16
    input elements for the low or high 4 bits of x to the two partial products.
    Each value for y has a different set of two tables:

        z = TABLE_LO_y(x[0..3]) xor TABLE_HI_y(x[4..7])

    This means that we need 16 * 2 * 256 = 8192 bytes for precomputed tables.

    Computing z[] = x[] * y can be performed 16 bytes at a time by using the
    128-bit register operations supported by modern processors.

    This is efficiently realized in SSE3 using the _mm_shuffle_epi8() function
    provided by Visual Studio 2010 or newer in <tmmintrin.h>.  This function
    uses the low bits to do a table lookup on each byte.  Unfortunately the
    high bit of each mask byte has the special feature that it clears the
    output byte when it is set, so we need to make sure it's cleared by masking
    off the high bit of each byte before using it:

        clr_mask = _mm_set1_epi8(0x0f) = 0x0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f

    For the low half of the partial product, clear the high bit of each byte
    and perform the table lookup:

        p_lo = _mm_and_si128(x, clr_mask)
        p_lo = _mm_shuffle_epi8(p_lo, TABLE_LO_y)

    For the high half of the partial product, shift the high 4 bits of each
    byte into the low 4 bits and clear the high bit of each byte, and then
    perform the table lookup:

        p_hi = _mm_srli_epi64(x, 4)
        p_hi = _mm_and_si128(p_hi, clr_mask)
        p_hi = _mm_shuffle_epi8(p_hi, TABLE_HI_y)

    Finally add the two partial products to form the product, recalling that
    addition is XOR in a Galois field:

        result = _mm_xor_si128(p_lo, p_hi)

    This crunches 16 bytes of x at a time, and the result can be stored in z.
*/

/*
    Intrinsic reference:

    SSE3, VS2010+, tmmintrin.h:

    GF256_M128 _mm_shuffle_epi8(GF256_M128 a, GF256_M128 mask);
        Emits the Supplemental Streaming SIMD Extensions 3 (SSSE3) instruction pshufb. This instruction shuffles 16-byte parameters from a 128-bit parameter.

        Pseudo-code for PSHUFB (with 128 bit operands):

            for i = 0 to 15 {
                 if (SRC[(i * 8)+7] = 1 ) then
                      DEST[(i*8)+7..(i*8)+0] <- 0;
                  else
                      index[3..0] <- SRC[(i*8)+3 .. (i*8)+0];
                      DEST[(i*8)+7..(i*8)+0] <- DEST[(index*8+7)..(index*8+0)];
                 endif
            }

    SSE2, VS2008+, emmintrin.h:

    GF256_M128 _mm_slli_epi64 (GF256_M128 a, int count);
        Shifts the 2 signed or unsigned 64-bit integers in a left by count bits while shifting in zeros.
    GF256_M128 _mm_srli_epi64 (GF256_M128 a, int count);
        Shifts the 2 signed or unsigned 64-bit integers in a right by count bits while shifting in zeros.
    GF256_M128 _mm_set1_epi8 (char b);
        Sets the 16 signed 8-bit integer values to b.
    GF256_M128 _mm_and_si128 (GF256_M128 a, GF256_M128 b);
        Computes the bitwise AND of the 128-bit value in a and the 128-bit value in b.
    GF256_M128 _mm_xor_si128 ( GF256_M128 a, GF256_M128 b);
        Computes the bitwise XOR of the 128-bit value in a and the 128-bit value in b.
*/

// Initialize the MM256 tables using gf256_mul()
void gf256_ctx::gf256_muladd_mem_init()
{
    for (int y = 0; y < 256; ++y)
    {
        uint8_t lo[16], hi[16];

        // TABLE_LO_Y maps 0..15 to 8-bit partial product based on y.
        for (unsigned char x = 0; x < 16; ++x)
        {
            lo[x] = gf256_mul(x, static_cast<uint8_t>( y ));
            hi[x] = gf256_mul(x << 4, static_cast<uint8_t>( y ));
        }

        const GF256_M128 table_lo = _mm_set_epi8(
            lo[15], lo[14], lo[13], lo[12], lo[11], lo[10], lo[9], lo[8],
            lo[7], lo[6], lo[5], lo[4], lo[3], lo[2], lo[1], lo[0]);
        const GF256_M128 table_hi = _mm_set_epi8(
            hi[15], hi[14], hi[13], hi[12], hi[11], hi[10], hi[9], hi[8],
            hi[7], hi[6], hi[5], hi[4], hi[3], hi[2], hi[1], hi[0]);
        _mm_store_si128(MM256_TABLE_LO_Y + y, table_lo);
        _mm_store_si128(MM256_TABLE_HI_Y + y, table_hi);
    }
}

//-----------------------------------------------------------------------------
// Initialization
//
// Initialize a context, filling in the tables.
//
// Thread-safety / Usage Notes:
//
// It is perfectly safe and encouraged to use a gf256_ctx object from multiple
// threads.  The gf256_init() is relatively expensive and should only be done
// once, though it will take less than a millisecond.
//
// The gf256_ctx object must be aligned to 16 byte boundary.
// Simply tag the object with GF256_ALIGNED to achieve this.
//
// Example:
//    static GF256_ALIGNED gf256_ctx TheGF256Context;
//    gf256_init(&TheGF256Context, 0);
//
// Returns 0 on success and other values on failure.

int gf256_ctx::gf256_init_()
{
    // Avoid multiple initialization
    if (initialized)
    {
        return 0;
    }

    if (!IsLittleEndian())
    {
        fprintf(stderr, "gf256_ctx::gf256_init_: Little Endian architecture expected (code won't work without mods)\n");
        return -2;
    }

    gf255_poly_init(DefaultPolynomialIndex);
    gf256_explog_init();
    gf256_muldiv_init();
    gf256_inv_init();
    gf256_muladd_mem_init();

    initialized = true;
    fprintf(stderr, "gf256_ctx::gf256_init_: initialized\n");
    return 0;
}

//-----------------------------------------------------------------------------
// Operations with context

void gf256_ctx::gf256_mul_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, uint8_t y, int bytes)
{
    // Use a single if-statement to handle special cases
    if (y <= 1)
    {
        if (y == 0)
        {
            memset(vz, 0, bytes);
        }
        return;
    }

    // Partial product tables; see above
    const GF256_M128 table_lo_y = _mm_load_si128(MM256_TABLE_LO_Y + y);
    const GF256_M128 table_hi_y = _mm_load_si128(MM256_TABLE_HI_Y + y);

    // clr_mask = 0x0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f
    const GF256_M128 clr_mask = _mm_set1_epi8(0x0f);

    GF256_M128 * GF256_RESTRICT z16 = reinterpret_cast<GF256_M128*>(vz);
    const GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<const GF256_M128*>(vx);

    // Handle multiples of 16 bytes
    while (bytes >= 16)
    {
        // See above comments for details
        GF256_M128 x0 = _mm_loadu_si128(x16);
        GF256_M128 l0 = _mm_and_si128(x0, clr_mask);
        x0 = _mm_srli_epi64(x0, 4);
        GF256_M128 h0 = _mm_and_si128(x0, clr_mask);
        l0 = _mm_shuffle_epi8(table_lo_y, l0);
        h0 = _mm_shuffle_epi8(table_hi_y, h0);
        _mm_storeu_si128(z16, _mm_xor_si128(l0, h0));

        x16++;
        z16++;
        bytes -= 16;
    }

    uint8_t * GF256_RESTRICT z8 = reinterpret_cast<uint8_t*>(z16);
    const uint8_t * GF256_RESTRICT x8 = reinterpret_cast<const uint8_t*>(x16);
    const uint8_t * GF256_RESTRICT table = GF256_MUL_TABLE + ((unsigned)y << 8);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t word = table[x8[0]];
        word |= (uint64_t)table[x8[1]] << 8;
        word |= (uint64_t)table[x8[2]] << 16;
        word |= (uint64_t)table[x8[3]] << 24;
        word |= (uint64_t)table[x8[4]] << 32;
        word |= (uint64_t)table[x8[5]] << 40;
        word |= (uint64_t)table[x8[6]] << 48;
        word |= (uint64_t)table[x8[7]] << 56;
        *(uint64_t*)z8 = word;

        x8 += 8;
        z8 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t word = table[x8[0]];
        word |= (uint32_t)table[x8[1]] << 8;
        word |= (uint32_t)table[x8[2]] << 16;
        word |= (uint32_t)table[x8[3]] << 24;
        *(uint32_t*)z8 = word;

        x8 += 4;
        z8 += 4;
        bytes -= 4;
    }

    // Handle single bytes
    for (int i = bytes; i > 0; i--) {
        z8[i-1] = table[x8[i-1]];
    }
}

void gf256_ctx::gf256_muladd_mem(void * GF256_RESTRICT vz, uint8_t y, const void * GF256_RESTRICT vx, int bytes)
{
    // Use a single if-statement to handle special cases
    if (y <= 1)
    {
        if (y == 1)
        {
            gf256_add_mem(vz, vx, bytes);
        }
        return;
    }

    // Partial product tables; see above
    const GF256_M128 table_lo_y = _mm_load_si128(MM256_TABLE_LO_Y + y);
    const GF256_M128 table_hi_y = _mm_load_si128(MM256_TABLE_HI_Y + y);

    // clr_mask = 0x0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f0f
    const GF256_M128 clr_mask = _mm_set1_epi8(0x0f);

    GF256_M128 * GF256_RESTRICT z16 = reinterpret_cast<GF256_M128*>(vz);
    const GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<const GF256_M128*>(vx);

    // Handle multiples of 16 bytes
    while (bytes >= 16)
    {
        // See above comments for details
        GF256_M128 x0 = _mm_loadu_si128(x16);
        GF256_M128 l0 = _mm_and_si128(x0, clr_mask);
        x0 = _mm_srli_epi64(x0, 4);
        GF256_M128 h0 = _mm_and_si128(x0, clr_mask);
        l0 = _mm_shuffle_epi8(table_lo_y, l0);
        h0 = _mm_shuffle_epi8(table_hi_y, h0);
        const GF256_M128 p0 = _mm_xor_si128(l0, h0);
        const GF256_M128 z0 = _mm_loadu_si128(z16);
        _mm_storeu_si128(z16, _mm_xor_si128(p0, z0));

        x16++;
        z16++;
        bytes -= 16;
    }

    uint8_t * GF256_RESTRICT z8 = reinterpret_cast<uint8_t*>(z16);
    const uint8_t * GF256_RESTRICT x8 = reinterpret_cast<const uint8_t*>(x16);
    const uint8_t * GF256_RESTRICT table = GF256_MUL_TABLE + ((unsigned)y << 8);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t word = table[x8[0]];
        word |= (uint64_t)table[x8[1]] << 8;
        word |= (uint64_t)table[x8[2]] << 16;
        word |= (uint64_t)table[x8[3]] << 24;
        word |= (uint64_t)table[x8[4]] << 32;
        word |= (uint64_t)table[x8[5]] << 40;
        word |= (uint64_t)table[x8[6]] << 48;
        word |= (uint64_t)table[x8[7]] << 56;
        *(uint64_t*)z8 ^= word;

        x8 += 8;
        z8 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t word = table[x8[0]];
        word |= (uint32_t)table[x8[1]] << 8;
        word |= (uint32_t)table[x8[2]] << 16;
        word |= (uint32_t)table[x8[3]] << 24;
        *(uint32_t*)z8 ^= word;

        x8 += 4;
        z8 += 4;
        bytes -= 4;
    }

    // Handle single bytes
    for (int i = bytes; i > 0; i--) {
        z8[i-1] ^= table[x8[i-1]];
    }
}

//-----------------------------------------------------------------------------
// Static operations

void gf256_ctx::gf256_add_mem(void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes)
{
    GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<GF256_M128*>(vx);
    const GF256_M128 * GF256_RESTRICT y16 = reinterpret_cast<const GF256_M128*>(vy);

    // Handle multiples of 64 bytes
    while (bytes >= 64)
    {
        GF256_M128 x0 = _mm_loadu_si128(x16);
        GF256_M128 x1 = _mm_loadu_si128(x16 + 1);
        GF256_M128 x2 = _mm_loadu_si128(x16 + 2);
        GF256_M128 x3 = _mm_loadu_si128(x16 + 3);
        GF256_M128 y0 = _mm_loadu_si128(y16);
        GF256_M128 y1 = _mm_loadu_si128(y16 + 1);
        GF256_M128 y2 = _mm_loadu_si128(y16 + 2);
        GF256_M128 y3 = _mm_loadu_si128(y16 + 3);

        _mm_storeu_si128(x16,
            _mm_xor_si128(x0, y0));
        _mm_storeu_si128(x16 + 1,
            _mm_xor_si128(x1, y1));
        _mm_storeu_si128(x16 + 2,
            _mm_xor_si128(x2, y2));
        _mm_storeu_si128(x16 + 3,
            _mm_xor_si128(x3, y3));

        x16 += 4;
        y16 += 4;
        bytes -= 64;
    }

    // Handle multiples of 16 bytes
    while (bytes >= 16)
    {
        // x[i] = x[i] xor y[i]
        _mm_storeu_si128(x16,
            _mm_xor_si128(
                _mm_loadu_si128(x16),
                _mm_loadu_si128(y16)));

        x16++;
        y16++;
        bytes -= 16;
    }

    uint8_t * GF256_RESTRICT x1 = reinterpret_cast<uint8_t *>(x16);
    const uint8_t * GF256_RESTRICT y1 = reinterpret_cast<const uint8_t *>(y16);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t * GF256_RESTRICT x8 = reinterpret_cast<uint64_t *>(x1);
        const uint64_t * GF256_RESTRICT y8 = reinterpret_cast<const uint64_t *>(y1);
        *x8 ^= *y8;

        x1 += 8;
        y1 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t * GF256_RESTRICT x4 = reinterpret_cast<uint32_t *>(x1);
        const uint32_t * GF256_RESTRICT y4 = reinterpret_cast<const uint32_t *>(y1);
        *x4 ^= *y4;

        x1 += 4;
        y1 += 4;
        bytes -= 4;
    }

    // Handle final bytes
    for (int i = bytes; i > 0; i--) {
        x1[i-1] ^= y1[i-1];
    }
}

void gf256_ctx::gf256_add2_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes)
{
    GF256_M128 * GF256_RESTRICT z16 = reinterpret_cast<GF256_M128*>(vz);
    const GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<const GF256_M128*>(vx);
    const GF256_M128 * GF256_RESTRICT y16 = reinterpret_cast<const GF256_M128*>(vy);

    // Handle multiples of 16 bytes
    while (bytes >= 16)
    {
        // z[i] = x[i] xor y[i]
        _mm_storeu_si128(z16,
            _mm_xor_si128(
            _mm_loadu_si128(z16),
            _mm_xor_si128(
            _mm_loadu_si128(x16),
            _mm_loadu_si128(y16))));

        x16++;
        y16++;
        z16++;
        bytes -= 16;
    }

    uint8_t * GF256_RESTRICT z1 = reinterpret_cast<uint8_t *>(z16);
    const uint8_t * GF256_RESTRICT x1 = reinterpret_cast<const uint8_t *>(x16);
    const uint8_t * GF256_RESTRICT y1 = reinterpret_cast<const uint8_t *>(y16);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t * GF256_RESTRICT z8 = reinterpret_cast<uint64_t *>(z1);
        const uint64_t * GF256_RESTRICT x8 = reinterpret_cast<const uint64_t *>(x1);
        const uint64_t * GF256_RESTRICT y8 = reinterpret_cast<const uint64_t *>(y1);
        *z8 ^= *x8 ^ *y8;

        x1 += 8;
        y1 += 8;
        z1 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t * GF256_RESTRICT z4 = reinterpret_cast<uint32_t *>(z1);
        const uint32_t * GF256_RESTRICT x4 = reinterpret_cast<const uint32_t *>(x1);
        const uint32_t * GF256_RESTRICT y4 = reinterpret_cast<const uint32_t *>(y1);
        *z4 ^= *x4 ^ *y4;

        x1 += 4;
        y1 += 4;
        z1 += 4;
        bytes -= 4;
    }

    // Handle final bytes
    for (int i = bytes; i > 0; i--) {
        z1[i-1] ^= x1[i-1] ^ y1[i-1];
    }
}

void gf256_ctx::gf256_addset_mem(void * GF256_RESTRICT vz, const void * GF256_RESTRICT vx, const void * GF256_RESTRICT vy, int bytes)
{
    GF256_M128 * GF256_RESTRICT z16 = reinterpret_cast<GF256_M128*>(vz);
    const GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<const GF256_M128*>(vx);
    const GF256_M128 * GF256_RESTRICT y16 = reinterpret_cast<const GF256_M128*>(vy);

    // Handle multiples of 64 bytes
    while (bytes >= 64)
    {
        GF256_M128 x0 = _mm_loadu_si128(x16);
        GF256_M128 x1 = _mm_loadu_si128(x16 + 1);
        GF256_M128 x2 = _mm_loadu_si128(x16 + 2);
        GF256_M128 x3 = _mm_loadu_si128(x16 + 3);
        GF256_M128 y0 = _mm_loadu_si128(y16);
        GF256_M128 y1 = _mm_loadu_si128(y16 + 1);
        GF256_M128 y2 = _mm_loadu_si128(y16 + 2);
        GF256_M128 y3 = _mm_loadu_si128(y16 + 3);

        _mm_storeu_si128(z16, _mm_xor_si128(x0, y0));
        _mm_storeu_si128(z16 + 1, _mm_xor_si128(x1, y1));
        _mm_storeu_si128(z16 + 2, _mm_xor_si128(x2, y2));
        _mm_storeu_si128(z16 + 3, _mm_xor_si128(x3, y3));

        x16 += 4;
        y16 += 4;
        z16 += 4;
        bytes -= 64;
    }

    // Handle multiples of 16 bytes
    while (bytes >= 16)
    {
        // z[i] = x[i] xor y[i]
        _mm_storeu_si128(z16,
            _mm_xor_si128(
                _mm_loadu_si128(x16),
                _mm_loadu_si128(y16)));

        x16++;
        y16++;
        z16++;
        bytes -= 16;
    }

    uint8_t * GF256_RESTRICT z1 = reinterpret_cast<uint8_t *>(z16);
    const uint8_t * GF256_RESTRICT x1 = reinterpret_cast<const uint8_t *>(x16);
    const uint8_t * GF256_RESTRICT y1 = reinterpret_cast<const uint8_t *>(y16);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t * GF256_RESTRICT z8 = reinterpret_cast<uint64_t *>(z1);
        const uint64_t * GF256_RESTRICT x8 = reinterpret_cast<const uint64_t *>(x1);
        const uint64_t * GF256_RESTRICT y8 = reinterpret_cast<const uint64_t *>(y1);
        *z8 = *x8 ^ *y8;

        x1 += 8;
        y1 += 8;
        z1 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t * GF256_RESTRICT z4 = reinterpret_cast<uint32_t *>(z1);
        const uint32_t * GF256_RESTRICT x4 = reinterpret_cast<const uint32_t *>(x1);
        const uint32_t * GF256_RESTRICT y4 = reinterpret_cast<const uint32_t *>(y1);
        *z4 = *x4 ^ *y4;

        x1 += 4;
        y1 += 4;
        z1 += 4;
        bytes -= 4;
    }

    // Handle final bytes
    for (int i = bytes; i > 0; i--) {
        z1[i-1] = x1[i-1] ^ y1[i-1];
    }
}

void gf256_memswap(void * GF256_RESTRICT vx, void * GF256_RESTRICT vy, int bytes)
{
    GF256_M128 * GF256_RESTRICT x16 = reinterpret_cast<GF256_M128*>(vx);
    GF256_M128 * GF256_RESTRICT y16 = reinterpret_cast<GF256_M128*>(vy);

    // Handle blocks of 16 bytes
    while (bytes >= 16)
    {
        GF256_M128 x0 = _mm_loadu_si128(x16);
        GF256_M128 y0 = _mm_loadu_si128(y16);
        _mm_storeu_si128(x16, y0);
        _mm_storeu_si128(y16, x0);

        bytes -= 16;
        ++x16;
        ++y16;
    }

    uint8_t * GF256_RESTRICT x1 = reinterpret_cast<uint8_t *>(x16);
    uint8_t * GF256_RESTRICT y1 = reinterpret_cast<uint8_t *>(y16);

    // Handle a block of 8 bytes
    if (bytes >= 8)
    {
        uint64_t * GF256_RESTRICT x8 = reinterpret_cast<uint64_t *>(x1);
        uint64_t * GF256_RESTRICT y8 = reinterpret_cast<uint64_t *>(y1);

        uint64_t temp = *x8;
        *x8 = *y8;
        *y8 = temp;

        x1 += 8;
        y1 += 8;
        bytes -= 8;
    }

    // Handle a block of 4 bytes
    if (bytes >= 4)
    {
        uint32_t * GF256_RESTRICT x4 = reinterpret_cast<uint32_t *>(x1);
        uint32_t * GF256_RESTRICT y4 = reinterpret_cast<uint32_t *>(y1);

        uint32_t temp = *x4;
        *x4 = *y4;
        *y4 = temp;

        x1 += 4;
        y1 += 4;
        bytes -= 4;
    }

    // Handle final bytes
    uint8_t temp;

    for (int i = bytes; i > 0; i--) {
        temp = x1[i-1]; x1[i-1] = y1[i-1]; y1[i-1] = temp;
    }
}
