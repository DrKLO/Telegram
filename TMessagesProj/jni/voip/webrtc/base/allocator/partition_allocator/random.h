// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ALLOCATOR_PARTITION_ALLOCATOR_RANDOM_H_
#define BASE_ALLOCATOR_PARTITION_ALLOCATOR_RANDOM_H_

#include <stdint.h>

#include "base/base_export.h"

namespace base {

// Returns a random value. The generator's internal state is initialized with
// `base::RandUint64` which is very unpredictable, but which is expensive due to
// the need to call into the kernel. Therefore this generator uses a fast,
// entirely user-space function after initialization.
BASE_EXPORT uint32_t RandomValue();

// Sets the seed for the random number generator to a known value, to cause the
// RNG to generate a predictable sequence of outputs. May be called multiple
// times.
BASE_EXPORT void SetMmapSeedForTesting(uint64_t seed);

}  // namespace base

#endif  // BASE_ALLOCATOR_PARTITION_ALLOCATOR_RANDOM_H_
