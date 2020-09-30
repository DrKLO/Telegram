// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/rand_util.h"

#include <nacl/nacl_random.h>
#include <stddef.h>
#include <stdint.h>

#include "base/logging.h"

namespace base {

void RandBytes(void* output, size_t output_length) {
  char* output_ptr = static_cast<char*>(output);
  while (output_length > 0) {
    size_t nread;
    const int error = nacl_secure_random(output_ptr, output_length, &nread);
    CHECK_EQ(error, 0);
    CHECK_LE(nread, output_length);
    output_ptr += nread;
    output_length -= nread;
  }
}

}  // namespace base
