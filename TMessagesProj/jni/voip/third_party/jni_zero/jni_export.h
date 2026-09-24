// Copyright 2023 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef JNI_ZERO_JNI_EXPORT_H_
#define JNI_ZERO_JNI_EXPORT_H_

#if defined(__i386__)
// Dalvik JIT generated code doesn't guarantee 16-byte stack alignment on
// x86 - use force_align_arg_pointer to realign the stack at the JNI
// boundary. crbug.com/655248
#define JNI_BOUNDARY_EXPORT \
  extern "C" __attribute__((visibility("default"), force_align_arg_pointer))
#else
#define JNI_BOUNDARY_EXPORT extern "C" __attribute__((visibility("default")))
#endif

#if defined(COMPONENT_BUILD)
#define JNI_ZERO_COMPONENT_BUILD_EXPORT __attribute__((visibility("default")))
#else
#define JNI_ZERO_COMPONENT_BUILD_EXPORT
#endif

#endif  // JNI_ZERO_JNI_EXPORT_H_
