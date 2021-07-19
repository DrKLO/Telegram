/*!
 * \copy
 *     Copyright (c)  2013, Cisco Systems
 *     All rights reserved.
 *
 *     Redistribution and use in source and binary forms, with or without
 *     modification, are permitted provided that the following conditions
 *     are met:
 *
 *        * Redistributions of source code must retain the above copyright
 *          notice, this list of conditions and the following disclaimer.
 *
 *        * Redistributions in binary form must reproduce the above copyright
 *          notice, this list of conditions and the following disclaimer in
 *          the documentation and/or other materials provided with the
 *          distribution.
 *
 *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 *     "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 *     LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 *     FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 *     COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *     INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 *     BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *     LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 *     CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 *     LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 *     ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *     POSSIBILITY OF SUCH DAMAGE.
 *
 */

#ifdef HAVE_NEON_AARCH64
#include "arm_arch64_common_macro.S"
WELS_ASM_AARCH64_FUNC_BEGIN SampleVariance16x16_AArch64_neon
    SIGN_EXTENSION x1, w1
    SIGN_EXTENSION x3, w3
    ld1   {v1.16b}, [x0], x1 //save the ref data (16bytes)
    ld1   {v0.16b}, [x2], x3 //save the src data (16bytes)
    uabd  v2.16b, v0.16b, v1.16b
    umull  v3.8h, v2.8b, v2.8b
    umull2 v4.8h, v2.16b, v2.16b
    uaddlp v4.4s, v4.8h
    uadalp v4.4s, v3.8h //sqr
    uaddlp v2.8h, v2.16b //sum

    uaddlp v1.8h, v0.16b //sum_cur

    umull  v3.8h, v0.8b, v0.8b
    umull2 v5.8h, v0.16b, v0.16b
    uaddlp v3.4s, v3.8h
    uadalp v3.4s, v5.8h //sqr_cur
.rept 15
    ld1   {v5.16b}, [x0], x1 //save the ref data (16bytes)
    ld1   {v0.16b}, [x2], x3 //save the src data (16bytes)

    uabd  v6.16b, v0.16b, v5.16b

    //v1 save sum_cur
    uadalp v1.8h, v0.16b

    //v4 save sqr
    umull  v5.8h, v6.8b, v6.8b
    umull2 v7.8h, v6.16b, v6.16b
    uadalp v4.4s, v5.8h //sqr
    uadalp v4.4s, v7.8h //sqr

    //v2 save sum
    uadalp v2.8h, v6.16b

    //v3 save sqr_cur
    umull  v5.8h, v0.8b, v0.8b
    umull2 v7.8h, v0.16b, v0.16b
    uadalp v3.4s, v5.8h //sqr_cur
    uadalp v3.4s, v7.8h //sqr_cur
.endr
    uaddlv s2, v2.8h //sum
    uaddlv s1, v1.8h //sum_cur
    ins    v2.s[1], v1.s[0] // sum, sum_cur
    shrn   v2.4h, v2.4s, #8 // sum, sum_cur>>8
    mul    v2.4h, v2.4h, v2.4h//// sum*sum, sum_cur*sum_cur
    uaddlv d4, v4.4s //sqr
    uaddlv d3, v3.4s //sqr_cur
    ins    v4.s[1], v3.s[0] // sqr, sqr_cur
    shrn   v4.4h, v4.4s, #8 // sqr, sqr_cur>>8
    sub    v4.4h, v4.4h, v2.4h
    st1  {v4.s}[0], [x4]
WELS_ASM_AARCH64_FUNC_END
#endif
