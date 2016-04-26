// Copyright (c) 2012, Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

#ifndef GOOGLE_BREAKPAD_COMMON_ANDROID_INCLUDE_SYS_USER_H
#define GOOGLE_BREAKPAD_COMMON_ANDROID_INCLUDE_SYS_USER_H

// The purpose of this file is to glue the mismatching headers (Android NDK vs
// glibc) and therefore avoid doing otherwise awkward #ifdefs in the code.
// The following quirks are currently handled by this file:
// - i386: Use the Android NDK but alias user_fxsr_struct > user_fpxregs_struct.
// - aarch64: Add missing user_regs_struct and user_fpsimd_struct structs.
// - Other platforms: Just use the Android NDK unchanged.

// TODO(primiano): remove these changes after Chromium has stably rolled to
// an NDK with the appropriate fixes.

#include_next <sys/user.h>

#ifdef __i386__
#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus
typedef struct user_fxsr_struct user_fpxregs_struct;
#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus
#endif  // __i386__

#ifdef __aarch64__
#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus
struct user_regs_struct {
 __u64 regs[31];
 __u64 sp;
 __u64 pc;
 __u64 pstate;
};
struct user_fpsimd_struct {
 __uint128_t vregs[32];
 __u32 fpsr;
 __u32 fpcr;
};
#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus
#endif  // __aarch64__

#endif  // GOOGLE_BREAKPAD_COMMON_ANDROID_INCLUDE_SYS_USER_H
