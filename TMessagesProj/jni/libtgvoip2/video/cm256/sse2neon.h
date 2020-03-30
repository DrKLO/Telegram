#ifndef SSE2NEON_H_
#define SSE2NEON_H_

#ifndef SSE2NEON_H
#define SSE2NEON_H

// This header file provides a simple API translation layer
// between SSE intrinsics to their corresponding ARM NEON versions
//
// This header file does not (yet) translate *all* of the SSE intrinsics.
// Since this is in support of a specific porting effort, I have only
// included the intrinsics I needed to get my port to work.
//
// Questions/Comments/Feedback send to: jratcliffscarab@gmail.com
//
// If you want to improve or add to this project, send me an
// email and I will probably approve your access to the depot.
//
// Project is located here:
//
//  https://github.com/jratcliff63367/sse2neon
//
// Show your appreciation for open source by sending me a bitcoin tip to the following
// address.
//
// TipJar: 1PzgWDSyq4pmdAXRH8SPUtta4SWGrt4B1p :
// https://blockchain.info/address/1PzgWDSyq4pmdAXRH8SPUtta4SWGrt4B1p
//
//
// Contributors to this project are:
//
// John W. Ratcliff : jratcliffscarab@gmail.com
// Brandon Rowlett : browlett@nvidia.com
// Ken Fast : kfast@gdeb.com
//
//
/*
** The MIT license:
**
** Permission is hereby granted, MEMALLOC_FREE of charge, to any person obtaining a copy
** of this software and associated documentation files (the "Software"), to deal
** in the Software without restriction, including without limitation the rights
** to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
** copies of the Software, and to permit persons to whom the Software is furnished
** to do so, subject to the following conditions:
**
** The above copyright notice and this permission notice shall be included in all
** copies or substantial portions of the Software.

** THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
** IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
** FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
** AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
** WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
** CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

#define GCC 1
#define ENABLE_CPP_VERSION 0

#if GCC
#define FORCE_INLINE                    inline __attribute__((always_inline))
#else
#define FORCE_INLINE                    inline
#endif

#include "arm_neon.h"

/*******************************************************/
/* MACRO for shuffle parameter for _mm_shuffle_ps().   */
/* Argument fp3 is a digit[0123] that represents the fp*/
/* from argument "b" of mm_shuffle_ps that will be     */
/* placed in fp3 of result. fp2 is the same for fp2 in */
/* result. fp1 is a digit[0123] that represents the fp */
/* from argument "a" of mm_shuffle_ps that will be     */
/* places in fp1 of result. fp0 is the same for fp0 of */
/* result                                              */
/*******************************************************/
#define _MM_SHUFFLE(fp3,fp2,fp1,fp0) (((fp3) << 6) | ((fp2) << 4) | \
    ((fp1) << 2) | ((fp0)))

typedef float32x4_t __m128;
typedef int32x4_t __m128i;

// ******************************************
// Set/get methods
// ******************************************

// Sets the 128-bit value to zero https://msdn.microsoft.com/en-us/library/vstudio/ys7dw0kh(v=vs.100).aspx
FORCE_INLINE __m128i _mm_setzero_si128()
{
    return vdupq_n_s32(0);
}

// Clears the four single-precision, floating-point values. https://msdn.microsoft.com/en-us/library/vstudio/tk1t2tbz(v=vs.100).aspx
FORCE_INLINE __m128 _mm_setzero_ps(void)
{
    return vdupq_n_f32(0);
}

// Sets the four single-precision, floating-point values to w. https://msdn.microsoft.com/en-us/library/vstudio/2x1se8ha(v=vs.100).aspx
FORCE_INLINE __m128 _mm_set1_ps(float _w)
{
    return vdupq_n_f32(_w);
}

// Sets the four single-precision, floating-point values to w. https://msdn.microsoft.com/en-us/library/vstudio/2x1se8ha(v=vs.100).aspx
FORCE_INLINE __m128 _mm_set_ps1(float _w)
{
    return vdupq_n_f32(_w);
}

// Sets the four single-precision, floating-point values to the four inputs. https://msdn.microsoft.com/en-us/library/vstudio/afh0zf75(v=vs.100).aspx
FORCE_INLINE __m128 _mm_set_ps(float w, float z, float y, float x)
{
    float __attribute__((aligned(16))) data[4] = { x, y, z, w };
    return vld1q_f32(data);
}

// Sets the four single-precision, floating-point values to the four inputs in reverse order. https://msdn.microsoft.com/en-us/library/vstudio/d2172ct3(v=vs.100).aspx
FORCE_INLINE __m128 _mm_setr_ps(float w, float z , float y , float x )
{
    float __attribute__ ((aligned (16))) data[4] = { w, z, y, x };
    return vld1q_f32(data);
}

// Sets the 4 signed 32-bit integer values to i. https://msdn.microsoft.com/en-us/library/vstudio/h4xscxat(v=vs.100).aspx
FORCE_INLINE __m128i _mm_set1_epi32(int _i)
{
    return vdupq_n_s32(_i);
}

// Sets the 4 signed 32-bit integer values. https://msdn.microsoft.com/en-us/library/vstudio/019beekt(v=vs.100).aspx
FORCE_INLINE __m128i _mm_set_epi32(int i3, int i2, int i1, int i0)
{
    int32_t __attribute__((aligned(16))) data[4] = { i0, i1, i2, i3 };
    return vld1q_s32(data);
}

// Stores four single-precision, floating-point values. https://msdn.microsoft.com/en-us/library/vstudio/s3h4ay6y(v=vs.100).aspx
FORCE_INLINE void _mm_store_ps(float *p, __m128 a)
{
    vst1q_f32(p, a);
}

// Stores four single-precision, floating-point values. https://msdn.microsoft.com/en-us/library/44e30x22(v=vs.100).aspx
FORCE_INLINE void _mm_storeu_ps(float *p, __m128 a)
{
    vst1q_f32(p, a);
}

// Stores four 32-bit integer values as (as a __m128i value) at the address p. https://msdn.microsoft.com/en-us/library/vstudio/edk11s13(v=vs.100).aspx
FORCE_INLINE void _mm_store_si128(__m128i *p, __m128i a )
{
    vst1q_s32((int32_t*) p,a);
}

// Stores the lower single - precision, floating - point value. https://msdn.microsoft.com/en-us/library/tzz10fbx(v=vs.100).aspx
FORCE_INLINE void _mm_store_ss(float *p, __m128 a)
{
    vst1q_lane_f32(p, a, 0);
}

// Reads the lower 64 bits of b and stores them into the lower 64 bits of a.  https://msdn.microsoft.com/en-us/library/hhwf428f%28v=vs.90%29.aspx
FORCE_INLINE void _mm_storel_epi64(__m128i* a, __m128i b)
{
    *a = (__m128i)vsetq_lane_s64((int64_t)vget_low_s32(b), *(int64x2_t*)a, 0);
}

// Loads a single single-precision, floating-point value, copying it into all four words https://msdn.microsoft.com/en-us/library/vstudio/5cdkf716(v=vs.100).aspx
FORCE_INLINE __m128 _mm_load1_ps(const float * p)
{
    return vld1q_dup_f32(p);
}

// Loads four single-precision, floating-point values. https://msdn.microsoft.com/en-us/library/vstudio/zzd50xxt(v=vs.100).aspx
FORCE_INLINE __m128 _mm_load_ps(const float * p)
{
    return vld1q_f32(p);
}

// Loads four single-precision, floating-point values.  https://msdn.microsoft.com/en-us/library/x1b16s7z%28v=vs.90%29.aspx
FORCE_INLINE __m128 _mm_loadu_ps(const float * p)
{
    // for neon, alignment doesn't matter, so _mm_load_ps and _mm_loadu_ps are equivalent for neon
    return vld1q_f32(p);
}

// Loads an single - precision, floating - point value into the low word and clears the upper three words.  https://msdn.microsoft.com/en-us/library/548bb9h4%28v=vs.90%29.aspx
FORCE_INLINE __m128 _mm_load_ss(const float * p)
{
    __m128 result = vdupq_n_f32(0);
    return vsetq_lane_f32(*p, result, 0);
}


// ******************************************
// Logic/Binary operations
// ******************************************

// Compares for inequality.  https://msdn.microsoft.com/en-us/library/sf44thbx(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmpneq_ps(__m128 a, __m128 b)
{
    return (__m128)vmvnq_s32((__m128i)vceqq_f32(a, b));
}

// Computes the bitwise AND-NOT of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/68h7wd02(v=vs.100).aspx
FORCE_INLINE __m128 _mm_andnot_ps(__m128 a, __m128 b)
{
    return (__m128)vbicq_s32((__m128i)b, (__m128i)a); // *NOTE* argument swap
}

// Computes the bitwise AND of the 128-bit value in b and the bitwise NOT of the 128-bit value in a. https://msdn.microsoft.com/en-us/library/vstudio/1beaceh8(v=vs.100).aspx
FORCE_INLINE __m128i _mm_andnot_si128(__m128i a, __m128i b)
{
    return (__m128i)vbicq_s32(b, a); // *NOTE* argument swap
}

// Computes the bitwise AND of the 128-bit value in a and the 128-bit value in b. https://msdn.microsoft.com/en-us/library/vstudio/6d1txsa8(v=vs.100).aspx
FORCE_INLINE __m128i _mm_and_si128(__m128i a, __m128i b)
{
    return (__m128i)vandq_s32(a, b);
}

// Computes the bitwise AND of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/73ck1xc5(v=vs.100).aspx
FORCE_INLINE __m128 _mm_and_ps(__m128 a, __m128 b)
{
    return (__m128)vandq_s32((__m128i)a, (__m128i)b);
}

// Computes the bitwise OR of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/7ctdsyy0(v=vs.100).aspx
FORCE_INLINE __m128 _mm_or_ps(__m128 a, __m128 b)
{
    return (__m128)vorrq_s32((__m128i)a, (__m128i)b);
}

// Computes bitwise EXOR (exclusive-or) of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/ss6k3wk8(v=vs.100).aspx
FORCE_INLINE __m128 _mm_xor_ps(__m128 a, __m128 b)
{
    return (__m128)veorq_s32((__m128i)a, (__m128i)b);
}

// Computes the bitwise OR of the 128-bit value in a and the 128-bit value in b. https://msdn.microsoft.com/en-us/library/vstudio/ew8ty0db(v=vs.100).aspx
FORCE_INLINE __m128i _mm_or_si128(__m128i a, __m128i b)
{
    return (__m128i)vorrq_s32(a, b);
}

// Computes the bitwise XOR of the 128-bit value in a and the 128-bit value in b.  https://msdn.microsoft.com/en-us/library/fzt08www(v=vs.100).aspx
FORCE_INLINE __m128i _mm_xor_si128(__m128i a, __m128i b)
{
    return veorq_s32(a, b);
}

// NEON does not provide this method
// Creates a 4-bit mask from the most significant bits of the four single-precision, floating-point values. https://msdn.microsoft.com/en-us/library/vstudio/4490ys29(v=vs.100).aspx
FORCE_INLINE int _mm_movemask_ps(__m128 a)
{
#if ENABLE_CPP_VERSION // I am not yet convinced that the NEON version is faster than the C version of this
    uint32x4_t &ia = *(uint32x4_t *)&a;
    return (ia[0] >> 31) | ((ia[1] >> 30) & 2) | ((ia[2] >> 29) & 4) | ((ia[3] >> 28) & 8);
#else
    static const uint32x4_t movemask = { 1, 2, 4, 8 };
    static const uint32x4_t highbit = { 0x80000000, 0x80000000, 0x80000000, 0x80000000 };
    uint32x4_t t0 = vreinterpretq_u32_f32(a);
    uint32x4_t t1 = vtstq_u32(t0, highbit);
    uint32x4_t t2 = vandq_u32(t1, movemask);
    uint32x2_t t3 = vorr_u32(vget_low_u32(t2), vget_high_u32(t2));
    return vget_lane_u32(t3, 0) | vget_lane_u32(t3, 1);
#endif
}

// Takes the upper 64 bits of a and places it in the low end of the result
// Takes the lower 64 bits of b and places it into the high end of the result.
FORCE_INLINE __m128 _mm_shuffle_ps_1032(__m128 a, __m128 b)
{
    return vcombine_f32(vget_high_f32(a), vget_low_f32(b));
}

// takes the lower two 32-bit values from a and swaps them and places in high end of result
// takes the higher two 32 bit values from b and swaps them and places in low end of result.
FORCE_INLINE __m128 _mm_shuffle_ps_2301(__m128 a, __m128 b)
{
    return vcombine_f32(vrev64_f32(vget_low_f32(a)), vrev64_f32(vget_high_f32(b)));
}

// keeps the low 64 bits of b in the low and puts the high 64 bits of a in the high
FORCE_INLINE __m128 _mm_shuffle_ps_3210(__m128 a, __m128 b)
{
    return vcombine_f32(vget_low_f32(a), vget_high_f32(b));
}

FORCE_INLINE __m128 _mm_shuffle_ps_0011(__m128 a, __m128 b)
{
    return vcombine_f32(vdup_n_f32(vgetq_lane_f32(a, 1)), vdup_n_f32(vgetq_lane_f32(b, 0)));
}

FORCE_INLINE __m128 _mm_shuffle_ps_0022(__m128 a, __m128 b)
{
    return vcombine_f32(vdup_n_f32(vgetq_lane_f32(a, 2)), vdup_n_f32(vgetq_lane_f32(b, 0)));
}

FORCE_INLINE __m128 _mm_shuffle_ps_2200(__m128 a, __m128 b)
{
    return vcombine_f32(vdup_n_f32(vgetq_lane_f32(a, 0)), vdup_n_f32(vgetq_lane_f32(b, 2)));
}

FORCE_INLINE __m128 _mm_shuffle_ps_3202(__m128 a, __m128 b)
{
    float32_t a0 = vgetq_lane_f32(a, 0);
    float32_t a2 = vgetq_lane_f32(a, 2);
    float32x2_t aVal = vdup_n_f32(a2);
    aVal = vset_lane_f32(a0, aVal, 1);
    return vcombine_f32(aVal, vget_high_f32(b));
}

FORCE_INLINE __m128 _mm_shuffle_ps_1133(__m128 a, __m128 b)
{
    return vcombine_f32(vdup_n_f32(vgetq_lane_f32(a, 3)), vdup_n_f32(vgetq_lane_f32(b, 1)));
}

FORCE_INLINE __m128 _mm_shuffle_ps_2010(__m128 a, __m128 b)
{
    float32_t b0 = vgetq_lane_f32(b, 0);
    float32_t b2 = vgetq_lane_f32(b, 2);
    float32x2_t bVal = vdup_n_f32(b0);
    bVal = vset_lane_f32(b2, bVal, 1);
    return vcombine_f32(vget_low_f32(a), bVal);
}

FORCE_INLINE __m128 _mm_shuffle_ps_2001(__m128 a, __m128 b)
{
    float32_t b0 = vgetq_lane_f32(b, 0);
    float32_t b2 = vgetq_lane_f32(b, 2);
    float32x2_t bVal = vdup_n_f32(b0);
    bVal = vset_lane_f32(b2, bVal, 1);
    return vcombine_f32(vrev64_f32(vget_low_f32(a)), bVal);
}

FORCE_INLINE __m128 _mm_shuffle_ps_2032(__m128 a, __m128 b)
{
    float32_t b0 = vgetq_lane_f32(b, 0);
    float32_t b2 = vgetq_lane_f32(b, 2);
    float32x2_t bVal = vdup_n_f32(b0);
    bVal = vset_lane_f32(b2, bVal, 1);
    return vcombine_f32(vget_high_f32(a), bVal);
}


// NEON does not support a general purpose permute intrinsic
// Currently I am not sure whether the C implementation is faster or slower than the NEON version.
// Note, this has to be expanded as a template because the shuffle value must be an immediate value.
// The same is true on SSE as well.
// Selects four specific single-precision, floating-point values from a and b, based on the mask i. https://msdn.microsoft.com/en-us/library/vstudio/5f0858x0(v=vs.100).aspx
template <int i>
FORCE_INLINE __m128 _mm_shuffle_ps_default(__m128 a, __m128 b)
{
#if ENABLE_CPP_VERSION // I am not convinced that the NEON version is faster than the C version yet.
    __m128 ret;
    ret[0] = a[i & 0x3];
    ret[1] = a[(i >> 2) & 0x3];
    ret[2] = b[(i >> 4) & 0x03];
    ret[3] = b[(i >> 6) & 0x03];
    return ret;
#else
    __m128 ret = vmovq_n_f32(vgetq_lane_f32(a, i & 0x3));
    ret = vsetq_lane_f32(vgetq_lane_f32(a, (i >> 2) & 0x3), ret, 1);
    ret = vsetq_lane_f32(vgetq_lane_f32(b, (i >> 4) & 0x3), ret, 2);
    ret = vsetq_lane_f32(vgetq_lane_f32(b, (i >> 6) & 0x3), ret, 3);
    return ret;
#endif
}

template <int i >
FORCE_INLINE __m128 _mm_shuffle_ps_function(__m128 a, __m128 b)
{
    switch (i)
    {
        case _MM_SHUFFLE(1, 0, 3, 2): return _mm_shuffle_ps_1032(a, b); break;
        case _MM_SHUFFLE(2, 3, 0, 1): return _mm_shuffle_ps_2301(a, b); break;
        case _MM_SHUFFLE(3, 2, 1, 0): return _mm_shuffle_ps_3210(a, b); break;
        case _MM_SHUFFLE(0, 0, 1, 1): return _mm_shuffle_ps_0011(a, b); break;
        case _MM_SHUFFLE(0, 0, 2, 2): return _mm_shuffle_ps_0022(a, b); break;
        case _MM_SHUFFLE(2, 2, 0, 0): return _mm_shuffle_ps_2200(a, b); break;
        case _MM_SHUFFLE(3, 2, 0, 2): return _mm_shuffle_ps_3202(a, b); break;
        case _MM_SHUFFLE(1, 1, 3, 3): return _mm_shuffle_ps_1133(a, b); break;
        case _MM_SHUFFLE(2, 0, 1, 0): return _mm_shuffle_ps_2010(a, b); break;
        case _MM_SHUFFLE(2, 0, 0, 1): return _mm_shuffle_ps_2001(a, b); break;
        case _MM_SHUFFLE(2, 0, 3, 2): return _mm_shuffle_ps_2032(a, b); break;
        default: _mm_shuffle_ps_default<i>(a, b);
    }
}

#define _mm_shuffle_ps(a,b,i) _mm_shuffle_ps_function<i>(a,b)

// Takes the upper 64 bits of a and places it in the low end of the result
// Takes the lower 64 bits of b and places it into the high end of the result.
FORCE_INLINE __m128i _mm_shuffle_epi_1032(__m128i a, __m128i b)
{
    return vcombine_s32(vget_high_s32(a), vget_low_s32(b));
}

// takes the lower two 32-bit values from a and swaps them and places in low end of result
// takes the higher two 32 bit values from b and swaps them and places in high end of result.
FORCE_INLINE __m128i _mm_shuffle_epi_2301(__m128i a, __m128i b)
{
    return vcombine_s32(vrev64_s32(vget_low_s32(a)), vrev64_s32(vget_high_s32(b)));
}

// shift a right by 32 bits, and put the lower 32 bits of a into the upper 32 bits of b
// when a and b are the same, rotates the least significant 32 bits into the most signficant 32 bits, and shifts the rest down
FORCE_INLINE __m128i _mm_shuffle_epi_0321(__m128i a, __m128i b)
{
    return vextq_s32(a, b, 1);
}

// shift a left by 32 bits, and put the upper 32 bits of b into the lower 32 bits of a
// when a and b are the same, rotates the most significant 32 bits into the least signficant 32 bits, and shifts the rest up
FORCE_INLINE __m128i _mm_shuffle_epi_2103(__m128i a, __m128i b)
{
    return vextq_s32(a, b, 3);
}

// gets the lower 64 bits of a, and places it in the upper 64 bits
// gets the lower 64 bits of b and places it in the lower 64 bits
FORCE_INLINE __m128i _mm_shuffle_epi_1010(__m128i a, __m128i b)
{
    return vcombine_s32(vget_low_s32(a), vget_low_s32(b));
}

// gets the lower 64 bits of a, and places it in the upper 64 bits
// gets the lower 64 bits of b, swaps the 0 and 1 elements, and places it in the lower 64 bits
FORCE_INLINE __m128i _mm_shuffle_epi_1001(__m128i a, __m128i b)
{
    return vcombine_s32(vrev64_s32(vget_low_s32(a)), vget_low_s32(b));
}

// gets the lower 64 bits of a, swaps the 0 and 1 elements and places it in the upper 64 bits
// gets the lower 64 bits of b, swaps the 0 and 1 elements, and places it in the lower 64 bits
FORCE_INLINE __m128i _mm_shuffle_epi_0101(__m128i a, __m128i b)
{
    return vcombine_s32(vrev64_s32(vget_low_s32(a)), vrev64_s32(vget_low_s32(b)));
}

FORCE_INLINE __m128i _mm_shuffle_epi_2211(__m128i a, __m128i b)
{
    return vcombine_s32(vdup_n_s32(vgetq_lane_s32(a, 1)), vdup_n_s32(vgetq_lane_s32(b, 2)));
}

FORCE_INLINE __m128i _mm_shuffle_epi_0122(__m128i a, __m128i b)
{
    return vcombine_s32(vdup_n_s32(vgetq_lane_s32(a, 2)), vrev64_s32(vget_low_s32(b)));
}

FORCE_INLINE __m128i _mm_shuffle_epi_3332(__m128i a, __m128i b)
{
    return vcombine_s32(vget_high_s32(a), vdup_n_s32(vgetq_lane_s32(b, 3)));
}

template <int i >
FORCE_INLINE __m128i _mm_shuffle_epi32_default(__m128i a, __m128i b)
{
#if ENABLE_CPP_VERSION
    __m128i ret;
    ret[0] = a[i & 0x3];
    ret[1] = a[(i >> 2) & 0x3];
    ret[2] = b[(i >> 4) & 0x03];
    ret[3] = b[(i >> 6) & 0x03];
    return ret;
#else
    __m128i ret = vmovq_n_s32(vgetq_lane_s32(a, i & 0x3));
    ret = vsetq_lane_s32(vgetq_lane_s32(a, (i >> 2) & 0x3), ret, 1);
    ret = vsetq_lane_s32(vgetq_lane_s32(b, (i >> 4) & 0x3), ret, 2);
    ret = vsetq_lane_s32(vgetq_lane_s32(b, (i >> 6) & 0x3), ret, 3);
    return ret;
#endif
}

template <int i >
FORCE_INLINE __m128i _mm_shuffle_epi32_function(__m128i a, __m128i b)
{
    switch (i)
    {
        case _MM_SHUFFLE(1, 0, 3, 2): return _mm_shuffle_epi_1032(a, b); break;
        case _MM_SHUFFLE(2, 3, 0, 1): return _mm_shuffle_epi_2301(a, b); break;
        case _MM_SHUFFLE(0, 3, 2, 1): return _mm_shuffle_epi_0321(a, b); break;
        case _MM_SHUFFLE(2, 1, 0, 3): return _mm_shuffle_epi_2103(a, b); break;
        case _MM_SHUFFLE(1, 0, 1, 0): return _mm_shuffle_epi_1010(a, b); break;
        case _MM_SHUFFLE(1, 0, 0, 1): return _mm_shuffle_epi_1001(a, b); break;
        case _MM_SHUFFLE(0, 1, 0, 1): return _mm_shuffle_epi_0101(a, b); break;
        case _MM_SHUFFLE(2, 2, 1, 1): return _mm_shuffle_epi_2211(a, b); break;
        case _MM_SHUFFLE(0, 1, 2, 2): return _mm_shuffle_epi_0122(a, b); break;
        case _MM_SHUFFLE(3, 3, 3, 2): return _mm_shuffle_epi_3332(a, b); break;
        default: return _mm_shuffle_epi32_default<i>(a, b);
    }
}

template <int i >
FORCE_INLINE __m128i _mm_shuffle_epi32_splat(__m128i a)
{
    return vdupq_n_s32(vgetq_lane_s32(a, i));
}

template <int i>
FORCE_INLINE __m128i _mm_shuffle_epi32_single(__m128i a)
{
    switch (i)
    {
        case _MM_SHUFFLE(0, 0, 0, 0): return _mm_shuffle_epi32_splat<0>(a); break;
        case _MM_SHUFFLE(1, 1, 1, 1): return _mm_shuffle_epi32_splat<1>(a); break;
        case _MM_SHUFFLE(2, 2, 2, 2): return _mm_shuffle_epi32_splat<2>(a); break;
        case _MM_SHUFFLE(3, 3, 3, 3): return _mm_shuffle_epi32_splat<3>(a); break;
        default: return _mm_shuffle_epi32_function<i>(a, a);
    }
}

// Shuffles the 4 signed or unsigned 32-bit integers in a as specified by imm.  https://msdn.microsoft.com/en-us/library/56f67xbk%28v=vs.90%29.aspx
#define _mm_shuffle_epi32(a,i) _mm_shuffle_epi32_single<i>(a)

template <int i>
FORCE_INLINE __m128i _mm_shufflehi_epi16_function(__m128i a)
{
    int16x8_t ret = (int16x8_t)a;
    int16x4_t highBits = vget_high_s16(ret);
    ret = vsetq_lane_s16(vget_lane_s16(highBits, i & 0x3), ret, 4);
    ret = vsetq_lane_s16(vget_lane_s16(highBits, (i >> 2) & 0x3), ret, 5);
    ret = vsetq_lane_s16(vget_lane_s16(highBits, (i >> 4) & 0x3), ret, 6);
    ret = vsetq_lane_s16(vget_lane_s16(highBits, (i >> 6) & 0x3), ret, 7);
    return (__m128i)ret;
}

// Shuffles the upper 4 signed or unsigned 16 - bit integers in a as specified by imm.  https://msdn.microsoft.com/en-us/library/13ywktbs(v=vs.100).aspx
#define _mm_shufflehi_epi16(a,i) _mm_shufflehi_epi16_function<i>(a)

// Shifts the 4 signed or unsigned 32-bit integers in a left by count bits while shifting in zeros. : https://msdn.microsoft.com/en-us/library/z2k3bbtb%28v=vs.90%29.aspx
#define _mm_slli_epi32(a, imm) (__m128i)vshlq_n_s32(a,imm)

//Shifts the 4 signed or unsigned 32-bit integers in a right by count bits while shifting in zeros.  https://msdn.microsoft.com/en-us/library/w486zcfa(v=vs.100).aspx
#define _mm_srli_epi32( a, imm ) (__m128i)vshrq_n_u32((uint32x4_t)a, imm)

// Shifts the 4 signed 32 - bit integers in a right by count bits while shifting in the sign bit.  https://msdn.microsoft.com/en-us/library/z1939387(v=vs.100).aspx
#define _mm_srai_epi32( a, imm ) vshrq_n_s32(a, imm)

// Shifts the 128 - bit value in a right by imm bytes while shifting in zeros.imm must be an immediate. https://msdn.microsoft.com/en-us/library/305w28yz(v=vs.100).aspx
//#define _mm_srli_si128( a, imm ) (__m128i)vmaxq_s8((int8x16_t)a, vextq_s8((int8x16_t)a, vdupq_n_s8(0), imm))
#define _mm_srli_si128( a, imm ) (__m128i)vextq_s8((int8x16_t)a, vdupq_n_s8(0), (imm))

// Shifts the 128-bit value in a left by imm bytes while shifting in zeros. imm must be an immediate.  https://msdn.microsoft.com/en-us/library/34d3k2kt(v=vs.100).aspx
#define _mm_slli_si128( a, imm ) (__m128i)vextq_s8(vdupq_n_s8(0), (int8x16_t)a, 16 - (imm))

// NEON does not provide a version of this function, here is an article about some ways to repro the results.
// http://stackoverflow.com/questions/11870910/sse-mm-movemask-epi8-equivalent-method-for-arm-neon
// Creates a 16-bit mask from the most significant bits of the 16 signed or unsigned 8-bit integers in a and zero extends the upper bits. https://msdn.microsoft.com/en-us/library/vstudio/s090c8fk(v=vs.100).aspx
FORCE_INLINE int _mm_movemask_epi8(__m128i _a)
{
    uint8x16_t input = (uint8x16_t)_a;
    const int8_t __attribute__((aligned(16))) xr[8] = { -7, -6, -5, -4, -3, -2, -1, 0 };
    uint8x8_t mask_and = vdup_n_u8(0x80);
    int8x8_t mask_shift = vld1_s8(xr);

    uint8x8_t lo = vget_low_u8(input);
    uint8x8_t hi = vget_high_u8(input);

    lo = vand_u8(lo, mask_and);
    lo = vshl_u8(lo, mask_shift);

    hi = vand_u8(hi, mask_and);
    hi = vshl_u8(hi, mask_shift);

    lo = vpadd_u8(lo, lo);
    lo = vpadd_u8(lo, lo);
    lo = vpadd_u8(lo, lo);

    hi = vpadd_u8(hi, hi);
    hi = vpadd_u8(hi, hi);
    hi = vpadd_u8(hi, hi);

    return ((hi[0] << 8) | (lo[0] & 0xFF));
}


// ******************************************
// Math operations
// ******************************************

// Subtracts the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/1zad2k61(v=vs.100).aspx
FORCE_INLINE __m128 _mm_sub_ps(__m128 a, __m128 b)
{
    return vsubq_f32(a, b);
}

// Subtracts the 4 signed or unsigned 32-bit integers of b from the 4 signed or unsigned 32-bit integers of a. https://msdn.microsoft.com/en-us/library/vstudio/fhh866h0(v=vs.100).aspx
FORCE_INLINE __m128i _mm_sub_epi32(__m128i a, __m128i b)
{
    return vsubq_s32(a, b);
}

// Adds the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/c9848chc(v=vs.100).aspx
FORCE_INLINE __m128 _mm_add_ps(__m128 a, __m128 b)
{
    return vaddq_f32(a, b);
}

// Adds the 4 signed or unsigned 32-bit integers in a to the 4 signed or unsigned 32-bit integers in b. https://msdn.microsoft.com/en-us/library/vstudio/09xs4fkk(v=vs.100).aspx
FORCE_INLINE __m128i _mm_add_epi32(__m128i a, __m128i b)
{
    return vaddq_s32(a, b);
}

// Adds the 8 signed or unsigned 16-bit integers in a to the 8 signed or unsigned 16-bit integers in b. https://msdn.microsoft.com/en-us/library/fceha5k4(v=vs.100).aspx
FORCE_INLINE __m128i _mm_add_epi16(__m128i a, __m128i b)
{
    return (__m128i)vaddq_s16((int16x8_t)a, (int16x8_t)b);
}

// Multiplies the 8 signed or unsigned 16-bit integers from a by the 8 signed or unsigned 16-bit integers from b. https://msdn.microsoft.com/en-us/library/vstudio/9ks1472s(v=vs.100).aspx
FORCE_INLINE __m128i _mm_mullo_epi16(__m128i a, __m128i b)
{
    return (__m128i)vmulq_s16((int16x8_t)a, (int16x8_t)b);
}

// Multiplies the 4 signed or unsigned 32-bit integers from a by the 4 signed or unsigned 32-bit integers from b. https://msdn.microsoft.com/en-us/library/vstudio/bb531409(v=vs.100).aspx
FORCE_INLINE __m128i _mm_mullo_epi32 (__m128i a, __m128i b)
{
    return (__m128i)vmulq_s32((int32x4_t)a,(int32x4_t)b);
}

// Multiplies the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/22kbk6t9(v=vs.100).aspx
FORCE_INLINE __m128 _mm_mul_ps(__m128 a, __m128 b)
{
    return vmulq_f32(a, b);
}

// This version does additional iterations to improve accuracy.  Between 1 and 4 recommended.
// Computes the approximations of reciprocals of the four single-precision, floating-point values of a. https://msdn.microsoft.com/en-us/library/vstudio/796k1tty(v=vs.100).aspx
FORCE_INLINE __m128 recipq_newton(__m128 in, int n)
{
    __m128 recip = vrecpeq_f32(in);
    for (int i = 0; i<n; ++i)
    {
        recip = vmulq_f32(recip, vrecpsq_f32(recip, in));
    }
    return recip;
}

// Computes the approximations of reciprocals of the four single-precision, floating-point values of a. https://msdn.microsoft.com/en-us/library/vstudio/796k1tty(v=vs.100).aspx
FORCE_INLINE __m128 _mm_rcp_ps(__m128 in)
{
    __m128 recip = vrecpeq_f32(in);
    recip = vmulq_f32(recip, vrecpsq_f32(recip, in));
    return recip;
}


// Computes the approximations of square roots of the four single-precision, floating-point values of a. First computes reciprocal square roots and then reciprocals of the four values. https://msdn.microsoft.com/en-us/library/vstudio/8z67bwwk(v=vs.100).aspx
FORCE_INLINE __m128 _mm_sqrt_ps(__m128 in)
{
    __m128 recipsq = vrsqrteq_f32(in);
    __m128 sq = vrecpeq_f32(recipsq);
    // ??? use step versions of both sqrt and recip for better accuracy?
    return sq;
}


// Computes the maximums of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/ff5d607a(v=vs.100).aspx
FORCE_INLINE __m128 _mm_max_ps(__m128 a, __m128 b)
{
    return vmaxq_f32(a, b);
}

// Computes the minima of the four single-precision, floating-point values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/wh13kadz(v=vs.100).aspx
FORCE_INLINE __m128 _mm_min_ps(__m128 a, __m128 b)
{
    return vminq_f32(a, b);
}

// Computes the pairwise minima of the 8 signed 16-bit integers from a and the 8 signed 16-bit integers from b. https://msdn.microsoft.com/en-us/library/vstudio/6te997ew(v=vs.100).aspx
FORCE_INLINE __m128i _mm_min_epi16(__m128i a, __m128i b)
{
    return (__m128i)vminq_s16((int16x8_t)a, (int16x8_t)b);
}

// epi versions of min/max
// Computes the pariwise maximums of the four signed 32-bit integer values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/bb514055(v=vs.100).aspx
FORCE_INLINE __m128i _mm_max_epi32(__m128i a, __m128i b )
{
    return vmaxq_s32(a,b);
}

// Computes the pariwise minima of the four signed 32-bit integer values of a and b. https://msdn.microsoft.com/en-us/library/vstudio/bb531476(v=vs.100).aspx
FORCE_INLINE __m128i _mm_min_epi32(__m128i a, __m128i b )
{
    return vminq_s32(a,b);
}

// Multiplies the 8 signed 16-bit integers from a by the 8 signed 16-bit integers from b. https://msdn.microsoft.com/en-us/library/vstudio/59hddw1d(v=vs.100).aspx
FORCE_INLINE __m128i _mm_mulhi_epi16(__m128i a, __m128i b)
{
    int16x8_t ret = vqdmulhq_s16((int16x8_t)a, (int16x8_t)b);
    ret = vshrq_n_s16(ret, 1);
    return (__m128i)ret;
}

// Computes pairwise add of each argument as single-precision, floating-point values a and b.
//https://msdn.microsoft.com/en-us/library/yd9wecaa.aspx
FORCE_INLINE __m128 _mm_hadd_ps(__m128 a, __m128 b )
{
// This does not work, no vpaddq...
//  return (__m128) vpaddq_f32(a,b);
        //
        // get two f32x2_t values from a
        // do vpadd
        // put result in low half of f32x4 result
        //
        // get two f32x2_t values from b
        // do vpadd
        // put result in high half of f32x4 result
        //
        // combine
        return vcombine_f32( vpadd_f32( vget_low_f32(a), vget_high_f32(a) ), vpadd_f32( vget_low_f32(b), vget_high_f32(b) ) );
}

// ******************************************
// Compare operations
// ******************************************

// Compares for less than https://msdn.microsoft.com/en-us/library/vstudio/f330yhc8(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmplt_ps(__m128 a, __m128 b)
{
    return (__m128)vcltq_f32(a, b);
}

// Compares for greater than. https://msdn.microsoft.com/en-us/library/vstudio/11dy102s(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmpgt_ps(__m128 a, __m128 b)
{
    return (__m128)vcgtq_f32(a, b);
}

// Compares for greater than or equal. https://msdn.microsoft.com/en-us/library/vstudio/fs813y2t(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmpge_ps(__m128 a, __m128 b)
{
    return (__m128)vcgeq_f32(a, b);
}

// Compares for less than or equal. https://msdn.microsoft.com/en-us/library/vstudio/1s75w83z(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmple_ps(__m128 a, __m128 b)
{
    return (__m128)vcleq_f32(a, b);
}

// Compares for equality. https://msdn.microsoft.com/en-us/library/vstudio/36aectz5(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cmpeq_ps(__m128 a, __m128 b)
{
    return (__m128)vceqq_f32(a, b);
}

// Compares the 4 signed 32-bit integers in a and the 4 signed 32-bit integers in b for less than. https://msdn.microsoft.com/en-us/library/vstudio/4ak0bf5d(v=vs.100).aspx
FORCE_INLINE __m128i _mm_cmplt_epi32(__m128i a, __m128i b)
{
    return (__m128i)vcltq_s32(a, b);
}

// Compares the 4 signed 32-bit integers in a and the 4 signed 32-bit integers in b for greater than. https://msdn.microsoft.com/en-us/library/vstudio/1s9f2z0y(v=vs.100).aspx
FORCE_INLINE __m128i _mm_cmpgt_epi32(__m128i a, __m128i b)
{
    return (__m128i)vcgtq_s32(a, b);
}

// Compares the four 32-bit floats in a and b to check if any values are NaN. Ordered compare between each value returns true for "orderable" and false for "not orderable" (NaN). https://msdn.microsoft.com/en-us/library/vstudio/0h9w00fx(v=vs.100).aspx
// see also:
// http://stackoverflow.com/questions/8627331/what-does-ordered-unordered-comparison-mean
// http://stackoverflow.com/questions/29349621/neon-isnanval-intrinsics
FORCE_INLINE __m128 _mm_cmpord_ps(__m128 a, __m128 b )
{
        // Note: NEON does not have ordered compare builtin
        // Need to compare a eq a and b eq b to check for NaN
        // Do AND of results to get final
    return (__m128) vreinterpretq_f32_u32( vandq_u32( vceqq_f32(a,a), vceqq_f32(b,b) ) );
}

// ******************************************
// Conversions
// ******************************************

// Converts the four single-precision, floating-point values of a to signed 32-bit integer values using truncate. https://msdn.microsoft.com/en-us/library/vstudio/1h005y6x(v=vs.100).aspx
FORCE_INLINE __m128i _mm_cvttps_epi32(__m128 a)
{
    return vcvtq_s32_f32(a);
}

// Converts the four signed 32-bit integer values of a to single-precision, floating-point values https://msdn.microsoft.com/en-us/library/vstudio/36bwxcx5(v=vs.100).aspx
FORCE_INLINE __m128 _mm_cvtepi32_ps(__m128i a)
{
    return vcvtq_f32_s32(a);
}

// Converts the four single-precision, floating-point values of a to signed 32-bit integer values. https://msdn.microsoft.com/en-us/library/vstudio/xdc42k5e(v=vs.100).aspx
FORCE_INLINE __m128i _mm_cvtps_epi32(__m128 a)
{
#if __aarch64__
    return vcvtaq_s32_f32(a);
#else
    __m128 half = vdupq_n_f32(0.5f);
    const __m128 sign = vcvtq_f32_u32((vshrq_n_u32(vreinterpretq_u32_f32(a), 31)));
    const __m128 aPlusHalf = vaddq_f32(a, half);
    const __m128 aRound = vsubq_f32(aPlusHalf, sign);
    return vcvtq_s32_f32(aRound);
#endif
}

// Moves the least significant 32 bits of a to a 32-bit integer. https://msdn.microsoft.com/en-us/library/5z7a9642%28v=vs.90%29.aspx
FORCE_INLINE int _mm_cvtsi128_si32(__m128i a)
{
    return vgetq_lane_s32(a, 0);
}

// Moves 32-bit integer a to the least significant 32 bits of an __m128 object, zero extending the upper bits. https://msdn.microsoft.com/en-us/library/ct3539ha%28v=vs.90%29.aspx
FORCE_INLINE __m128i _mm_cvtsi32_si128(int a)
{
    __m128i result = vdupq_n_s32(0);
    return vsetq_lane_s32(a, result, 0);
}


// Applies a type cast to reinterpret four 32-bit floating point values passed in as a 128-bit parameter as packed 32-bit integers. https://msdn.microsoft.com/en-us/library/bb514099.aspx
FORCE_INLINE __m128i _mm_castps_si128(__m128 a)
{
    return vcvtq_s32_f32(a);
    //return *((const __m128i *) &a);
}

// Applies a type cast to reinterpret four 32-bit integers passed in as a 128-bit parameter as packed 32-bit floating point values. https://msdn.microsoft.com/en-us/library/bb514029.aspx
FORCE_INLINE __m128 _mm_castsi128_ps(__m128i a)
{
    return vcvtq_f32_s32(a);
    //return *((const __m128 *) &a);
}

// Loads 128-bit value. : https://msdn.microsoft.com/en-us/library/atzzad1h(v=vs.80).aspx
FORCE_INLINE __m128i _mm_load_si128(const __m128i *p)
{
    return vld1q_s32((int32_t *)p);
}

// ******************************************
// Miscellaneous Operations
// ******************************************

// Packs the 16 signed 16-bit integers from a and b into 8-bit integers and saturates. https://msdn.microsoft.com/en-us/library/k4y4f7w5%28v=vs.90%29.aspx
FORCE_INLINE __m128i _mm_packs_epi16(__m128i a, __m128i b)
{
    return (__m128i)vcombine_s8(vqmovn_s16((int16x8_t)a), vqmovn_s16((int16x8_t)b));
}

// Packs the 16 signed 16 - bit integers from a and b into 8 - bit unsigned integers and saturates. https://msdn.microsoft.com/en-us/library/07ad1wx4(v=vs.100).aspx
FORCE_INLINE __m128i _mm_packus_epi16(const __m128i a, const __m128i b)
{
    return (__m128i)vcombine_u8(vqmovun_s16((int16x8_t)a), vqmovun_s16((int16x8_t)b));
}

// Packs the 8 signed 32-bit integers from a and b into signed 16-bit integers and saturates. https://msdn.microsoft.com/en-us/library/393t56f9%28v=vs.90%29.aspx
FORCE_INLINE __m128i _mm_packs_epi32(__m128i a, __m128i b)
{
    return (__m128i)vcombine_s16(vqmovn_s32(a), vqmovn_s32(b));
}

// Interleaves the lower 8 signed or unsigned 8-bit integers in a with the lower 8 signed or unsigned 8-bit integers in b.  https://msdn.microsoft.com/en-us/library/xf7k860c%28v=vs.90%29.aspx
FORCE_INLINE __m128i _mm_unpacklo_epi8(__m128i a, __m128i b)
{
    int8x8_t a1 = (int8x8_t)vget_low_s16((int16x8_t)a);
    int8x8_t b1 = (int8x8_t)vget_low_s16((int16x8_t)b);

    int8x8x2_t result = vzip_s8(a1, b1);

    return (__m128i)vcombine_s8(result.val[0], result.val[1]);
}

// Interleaves the lower 4 signed or unsigned 16-bit integers in a with the lower 4 signed or unsigned 16-bit integers in b.  https://msdn.microsoft.com/en-us/library/btxb17bw%28v=vs.90%29.aspx
FORCE_INLINE __m128i _mm_unpacklo_epi16(__m128i a, __m128i b)
{
    int16x4_t a1 = vget_low_s16((int16x8_t)a);
    int16x4_t b1 = vget_low_s16((int16x8_t)b);

    int16x4x2_t result = vzip_s16(a1, b1);

    return (__m128i)vcombine_s16(result.val[0], result.val[1]);
}

// Interleaves the lower 2 signed or unsigned 32 - bit integers in a with the lower 2 signed or unsigned 32 - bit integers in b.  https://msdn.microsoft.com/en-us/library/x8atst9d(v=vs.100).aspx
FORCE_INLINE __m128i _mm_unpacklo_epi32(__m128i a, __m128i b)
{
    int32x2_t a1 = vget_low_s32(a);
    int32x2_t b1 = vget_low_s32(b);

    int32x2x2_t result = vzip_s32(a1, b1);

    return vcombine_s32(result.val[0], result.val[1]);
}

// Selects and interleaves the lower two single-precision, floating-point values from a and b. https://msdn.microsoft.com/en-us/library/25st103b%28v=vs.90%29.aspx
FORCE_INLINE __m128 _mm_unpacklo_ps(__m128 a, __m128 b)
{
    float32x2x2_t result = vzip_f32(vget_low_f32(a), vget_low_f32(b));
    return vcombine_f32(result.val[0], result.val[1]);
}

// Selects and interleaves the upper two single-precision, floating-point values from a and b. https://msdn.microsoft.com/en-us/library/skccxx7d%28v=vs.90%29.aspx
FORCE_INLINE __m128 _mm_unpackhi_ps(__m128 a, __m128 b)
{
    float32x2x2_t result = vzip_f32(vget_high_f32(a), vget_high_f32(b));
    return vcombine_f32(result.val[0], result.val[1]);
}

// Interleaves the upper 2 signed or unsigned 32-bit integers in a with the upper 2 signed or unsigned 32-bit integers in b.  https://msdn.microsoft.com/en-us/library/65sa7cbs(v=vs.100).aspx
FORCE_INLINE __m128i _mm_unpackhi_epi32(__m128i a, __m128i b)
{
    int32x2_t a1 = vget_high_s32(a);
    int32x2_t b1 = vget_high_s32(b);

    int32x2x2_t result = vzip_s32(a1, b1);

    return vcombine_s32(result.val[0], result.val[1]);
}

// Extracts the selected signed or unsigned 16-bit integer from a and zero extends.  https://msdn.microsoft.com/en-us/library/6dceta0c(v=vs.100).aspx
#define _mm_extract_epi16( a, imm ) vgetq_lane_s16((int16x8_t)a, imm)

// ******************************************
// Streaming Extensions
// ******************************************

// Guarantees that every preceding store is globally visible before any subsequent store.  https://msdn.microsoft.com/en-us/library/5h2w73d1%28v=vs.90%29.aspx
FORCE_INLINE void _mm_sfence(void)
{
    __sync_synchronize();
}

// Stores the data in a to the address p without polluting the caches.  If the cache line containing address p is already in the cache, the cache will be updated.Address p must be 16 - byte aligned.  https://msdn.microsoft.com/en-us/library/ba08y07y%28v=vs.90%29.aspx
FORCE_INLINE void _mm_stream_si128(__m128i *p, __m128i a)
{
    *p = a;
}

// Cache line containing p is flushed and invalidated from all caches in the coherency domain.
FORCE_INLINE void _mm_clflush(void const*p __attribute__((unused))) {
    // no corollary for Neon?
}

// ******************************************
// GF256 usage specific methods
// ******************************************

FORCE_INLINE __m128i _mm_set_epi8(uint8_t i15, uint8_t i14, uint8_t i13, uint8_t i12, uint8_t i11, uint8_t i10, uint8_t i9, uint8_t i8,
        uint8_t i7, uint8_t i6, uint8_t i5, uint8_t i4, uint8_t i3, uint8_t i2, uint8_t i1, uint8_t i0)
{
    int32_t a0 =  i0 |  (i1<<8) |  (i2<<16) |  (i3<<24);
    int32_t a1 =  i4 |  (i5<<8) |  (i6<<16) |  (i7<<24);
    int32_t a2 =  i8 |  (i9<<8) | (i10<<16) | (i11<<24);
    int32_t a3 = i12 | (i13<<8) | (i14<<16) | (i15<<24);

    return _mm_set_epi32(a3, a2, a1, a0);
}

FORCE_INLINE __m128i _mm_set1_epi8(uint8_t i)
{
    int32_t a = i | (i<<8) | (i<<16) | (i<<24);
    return _mm_set1_epi32(a);
}

FORCE_INLINE __m128i _mm_loadu_si128(const __m128i *p )
{
    return vld1q_s32((int32_t*) p);
}

FORCE_INLINE void _mm_storeu_si128(__m128i *p, __m128i a )
{
    _mm_store_si128(p, a);
//    vst1q_s32((int32_t*) p,a);
}

FORCE_INLINE __m128i _mm_shuffle_epi8(__m128i ia, __m128i ib)
{
    uint8_t *a = (uint8_t *) &ia; // input a
    uint8_t *b = (uint8_t *) &ib; // input b
    int32_t r[4];

    r[0] = ((b[3] & 0x80) ? 0 : a[b[3] % 16])<<24;
    r[0] |= ((b[2] & 0x80) ? 0 : a[b[2] % 16])<<16;
    r[0] |= ((b[1] & 0x80) ? 0 : a[b[1] % 16])<<8;
    r[0] |= ((b[0] & 0x80) ? 0 : a[b[0] % 16]);

    r[1] = ((b[7] & 0x80) ? 0 : a[b[7] % 16])<<24;
    r[1] |= ((b[6] & 0x80) ? 0 : a[b[6] % 16])<<16;
    r[1] |= ((b[5] & 0x80) ? 0 : a[b[5] % 16])<<8;
    r[1] |= ((b[4] & 0x80) ? 0 : a[b[4] % 16]);

    r[2] = ((b[11] & 0x80) ? 0 : a[b[11] % 16])<<24;
    r[2] |= ((b[10] & 0x80) ? 0 : a[b[10] % 16])<<16;
    r[2] |= ((b[9] & 0x80) ? 0 : a[b[9] % 16])<<8;
    r[2] |= ((b[8] & 0x80) ? 0 : a[b[8] % 16]);

    r[3] = ((b[15] & 0x80) ? 0 : a[b[15] % 16])<<24;
    r[3] |= ((b[14] & 0x80) ? 0 : a[b[14] % 16])<<16;
    r[3] |= ((b[13] & 0x80) ? 0 : a[b[13] % 16])<<8;
    r[3] |= ((b[12] & 0x80) ? 0 : a[b[12] % 16]);

    return vld1q_s32(r);
}

#define _mm_srli_epi64( a, imm ) (__m128i)vshrq_n_u64((uint64x2_t)a, imm)

#endif

#endif /* SSE2NEON_H_ */
