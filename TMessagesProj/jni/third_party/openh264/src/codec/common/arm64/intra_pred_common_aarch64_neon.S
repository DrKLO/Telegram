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

//for Luma 16x16
//void WelsI16x16LumaPredV_AArch64_neon (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride);
WELS_ASM_AARCH64_FUNC_BEGIN WelsI16x16LumaPredV_AArch64_neon
    SIGN_EXTENSION x2,w2
    sub     x3, x1, x2
    ld1     {v0.16b}, [x3]
.rept 16
    st1     {v0.16b}, [x0], 16
.endr
WELS_ASM_AARCH64_FUNC_END

//void WelsI16x16LumaPredH_AArch64_neon (uint8_t* pPred, uint8_t* pRef, const int32_t kiStride);
WELS_ASM_AARCH64_FUNC_BEGIN WelsI16x16LumaPredH_AArch64_neon
    SIGN_EXTENSION x2,w2
    sub     x3, x1, #1
.rept 16
    ld1r    {v0.16b}, [x3], x2
    st1     {v0.16b}, [x0], 16
.endr
WELS_ASM_AARCH64_FUNC_END

#endif

