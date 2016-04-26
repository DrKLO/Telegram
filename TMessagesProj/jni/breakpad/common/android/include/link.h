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

#ifndef GOOGLE_BREAKPAD_ANDROID_INCLUDE_LINK_H
#define GOOGLE_BREAKPAD_ANDROID_INCLUDE_LINK_H

/* Android doesn't provide all the data-structures required in its <link.h>.
   Provide custom version here. */
#include_next <link.h>

// TODO(rmcilroy): Remove this file once the ndk is updated for other
// architectures - crbug.com/358831
#if !defined(__aarch64__) && !defined(__x86_64__) && \
    !(defined(__mips__) && _MIPS_SIM == _ABI64)

#ifdef __cplusplus
extern "C" {
#endif  // __cplusplus

struct r_debug {
  int              r_version;
  struct link_map* r_map;
  ElfW(Addr)       r_brk;
  enum {
    RT_CONSISTENT,
    RT_ADD,
    RT_DELETE }    r_state;
  ElfW(Addr)       r_ldbase;
};

struct link_map {
  ElfW(Addr)       l_addr;
  char*            l_name;
  ElfW(Dyn)*       l_ld;
  struct link_map* l_next;
  struct link_map* l_prev;
};

#ifdef __cplusplus
}  // extern "C"
#endif  // __cplusplus

#endif  // !defined(__aarch64__) && !defined(__x86_64__)

#endif /* GOOGLE_BREAKPAD_ANDROID_INCLUDE_LINK_H */
