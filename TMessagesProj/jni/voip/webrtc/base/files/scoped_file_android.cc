// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/files/scoped_file.h"

#include <stdint.h>

// Copied from <android/fdsan.h>.
// This can go away once this header is included in our copy of the NDK.
extern "C" {
void android_fdsan_exchange_owner_tag(int fd,
                                      uint64_t expected_tag,
                                      uint64_t new_tag)
    __attribute__((__weak__));
}

namespace base {
namespace internal {

static uint64_t ScopedFDToTag(const ScopedFD& owner) {
  return reinterpret_cast<uint64_t>(&owner);
}

// static
void ScopedFDCloseTraits::Acquire(const ScopedFD& owner, int fd) {
  if (android_fdsan_exchange_owner_tag) {
    android_fdsan_exchange_owner_tag(fd, 0, ScopedFDToTag(owner));
  }
}

// static
void ScopedFDCloseTraits::Release(const ScopedFD& owner, int fd) {
  if (android_fdsan_exchange_owner_tag) {
    android_fdsan_exchange_owner_tag(fd, ScopedFDToTag(owner), 0);
  }
}

}  // namespace internal
}  // namespace base
