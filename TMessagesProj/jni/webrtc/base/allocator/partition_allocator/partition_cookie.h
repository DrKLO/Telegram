// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_COOKIE_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_COOKIE_H_

#include "base/compiler_specific.h"
#include "base/logging.h"

namespace base {
namespace internal {

#if DCHECK_IS_ON()
// Handles alignment up to XMM instructions on Intel.
static constexpr size_t kCookieSize = 16;

static constexpr unsigned char kCookieValue[kCookieSize] = {
    0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xD0, 0x0D,
    0x13, 0x37, 0xF0, 0x05, 0xBA, 0x11, 0xAB, 0x1E};
#endif

ALWAYS_INLINE void PartitionCookieCheckValue(void* ptr) {
#if DCHECK_IS_ON()
  unsigned char* cookie_ptr = reinterpret_cast<unsigned char*>(ptr);
  for (size_t i = 0; i < kCookieSize; ++i, ++cookie_ptr)
    DCHECK(*cookie_ptr == kCookieValue[i]);
#endif
}

ALWAYS_INLINE size_t PartitionCookieSizeAdjustAdd(size_t size) {
#if DCHECK_IS_ON()
  // Add space for cookies, checking for integer overflow. TODO(palmer):
  // Investigate the performance and code size implications of using
  // CheckedNumeric throughout PA.
  DCHECK(size + (2 * kCookieSize) > size);
  size += 2 * kCookieSize;
#endif
  return size;
}

ALWAYS_INLINE void* PartitionCookieFreePointerAdjust(void* ptr) {
#if DCHECK_IS_ON()
  // The value given to the application is actually just after the cookie.
  ptr = static_cast<char*>(ptr) - kCookieSize;
#endif
  return ptr;
}

ALWAYS_INLINE size_t PartitionCookieSizeAdjustSubtract(size_t size) {
#if DCHECK_IS_ON()
  // Remove space for cookies.
  DCHECK(size >= 2 * kCookieSize);
  size -= 2 * kCookieSize;
#endif
  return size;
}

ALWAYS_INLINE void PartitionCookieWriteValue(void* ptr) {
#if DCHECK_IS_ON()
  unsigned char* cookie_ptr = reinterpret_cast<unsigned char*>(ptr);
  for (size_t i = 0; i < kCookieSize; ++i, ++cookie_ptr)
    *cookie_ptr = kCookieValue[i];
#endif
}

}  // namespace internal
}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_PARTITION_COOKIE_H_
