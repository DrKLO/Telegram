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

.macro LOAD_ALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, src*, src_stride
    ld1 {\arg0\().d}[0], [\arg4], \arg5
    ld1 {\arg1\().d}[0], [\arg4], \arg5
    ld1 {\arg2\().d}[0], [\arg4], \arg5
    ld1 {\arg3\().d}[0], [\arg4], \arg5
//  }
.endm

.macro STORE_ALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, dst*, dst_stride
    st1 {\arg0\().d}[0], [\arg4], \arg5
    st1 {\arg1\().d}[0], [\arg4], \arg5
    st1 {\arg2\().d}[0], [\arg4], \arg5
    st1 {\arg3\().d}[0], [\arg4], \arg5
//  }
.endm

.macro LOAD_UNALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, src*, src_stride
    ld1 {\arg0\().8b}, [\arg4], \arg5
    ld1 {\arg1\().8b}, [\arg4], \arg5
    ld1 {\arg2\().8b}, [\arg4], \arg5
    ld1 {\arg3\().8b}, [\arg4], \arg5
//  }
.endm

.macro STORE_UNALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, dst*, dst_stride
    st1 {\arg0\().8b}, [\arg4], \arg5
    st1 {\arg1\().8b}, [\arg4], \arg5
    st1 {\arg2\().8b}, [\arg4], \arg5
    st1 {\arg3\().8b}, [\arg4], \arg5
//  }
.endm

.macro LOAD16_ALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, src*, src_stride
    ld1 {\arg0\().2d}, [\arg4], \arg5
    ld1 {\arg1\().2d}, [\arg4], \arg5
    ld1 {\arg2\().2d}, [\arg4], \arg5
    ld1 {\arg3\().2d}, [\arg4], \arg5
//  }
.endm

.macro STORE16_ALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, dst*, dst_stride
    st1 {\arg0\().2d}, [\arg4], \arg5
    st1 {\arg1\().2d}, [\arg4], \arg5
    st1 {\arg2\().2d}, [\arg4], \arg5
    st1 {\arg3\().2d}, [\arg4], \arg5
//  }
.endm

.macro LOAD16_UNALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, src*, src_stride
    ld1 {\arg0\().16b}, [\arg4], \arg5
    ld1 {\arg1\().16b}, [\arg4], \arg5
    ld1 {\arg2\().16b}, [\arg4], \arg5
    ld1 {\arg3\().16b}, [\arg4], \arg5
//  }
.endm

.macro STORE16_UNALIGNED_DATA_WITH_STRIDE arg0, arg1, arg2, arg3, arg4, arg5
//  {   //  input: $0~$3, dst*, dst_stride
    st1 {\arg0\().16b}, [\arg4], \arg5
    st1 {\arg1\().16b}, [\arg4], \arg5
    st1 {\arg2\().16b}, [\arg4], \arg5
    st1 {\arg3\().16b}, [\arg4], \arg5
//  }
.endm

//void WelsCopy8x8_AArch64_neon (uint8_t* pDst, int32_t iStrideD, uint8_t* pSrc, int32_t iStrideS);
WELS_ASM_AARCH64_FUNC_BEGIN WelsCopy8x8_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    LOAD_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD_UNALIGNED_DATA_WITH_STRIDE v4, v5, v6, v7, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v4, v5, v6, v7, x0, x1

WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsCopy16x16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    LOAD16_ALIGNED_DATA_WITH_STRIDE   v0, v1, v2, v3, x2, x3

    STORE16_ALIGNED_DATA_WITH_STRIDE  v0, v1, v2, v3, x0, x1

    LOAD16_ALIGNED_DATA_WITH_STRIDE   v16, v17, v18, v19, x2, x3

    STORE16_ALIGNED_DATA_WITH_STRIDE  v16, v17, v18, v19, x0, x1

    LOAD16_ALIGNED_DATA_WITH_STRIDE   v0, v1, v2, v3, x2, x3

    STORE16_ALIGNED_DATA_WITH_STRIDE  v0, v1, v2, v3, x0, x1

    LOAD16_ALIGNED_DATA_WITH_STRIDE   v16, v17, v18, v19, x2, x3

    STORE16_ALIGNED_DATA_WITH_STRIDE  v16, v17, v18, v19, x0, x1

WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsCopy16x16NotAligned_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    LOAD16_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD16_UNALIGNED_DATA_WITH_STRIDE v16, v17, v18, v19, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v16, v17, v18, v19, x0, x1

    LOAD16_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD16_UNALIGNED_DATA_WITH_STRIDE v16, v17, v18, v19, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v16, v17, v18, v19, x0, x1

WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsCopy16x8NotAligned_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    LOAD16_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD16_UNALIGNED_DATA_WITH_STRIDE v16, v17, v18, v19, x2, x3

    STORE16_UNALIGNED_DATA_WITH_STRIDE    v16, v17, v18, v19, x0, x1

WELS_ASM_AARCH64_FUNC_END


WELS_ASM_AARCH64_FUNC_BEGIN WelsCopy8x16_AArch64_neon
    SIGN_EXTENSION x1,w1
    SIGN_EXTENSION x3,w3
    LOAD_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD_UNALIGNED_DATA_WITH_STRIDE v4, v5, v6, v7, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v4, v5, v6, v7, x0, x1

    LOAD_UNALIGNED_DATA_WITH_STRIDE v0, v1, v2, v3, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v0, v1, v2, v3, x0, x1

    LOAD_UNALIGNED_DATA_WITH_STRIDE v4, v5, v6, v7, x2, x3

    STORE_UNALIGNED_DATA_WITH_STRIDE    v4, v5, v6, v7, x0, x1

WELS_ASM_AARCH64_FUNC_END

#endif
