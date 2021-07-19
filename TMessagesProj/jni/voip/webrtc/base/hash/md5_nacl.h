// Copyright (c) 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_HASH_MD5_NACL_H_
#define BASE_HASH_MD5_NACL_H_

#include <stdint.h>

namespace base {

// The output of an MD5 operation.
struct MD5Digest {
  uint8_t a[16];
};

// Used for storing intermediate data during an MD5 computation. Callers
// should not access the data.
typedef char MD5Context[88];

}  // namespace base

#endif  // BASE_HASH_MD5_NACL_H_
