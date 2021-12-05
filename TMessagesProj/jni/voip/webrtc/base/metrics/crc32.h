// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_CRC32_H_
#define BASE_METRICS_CRC32_H_

#include <stddef.h>
#include <stdint.h>

#include "base/base_export.h"

namespace base {

BASE_EXPORT extern const uint32_t kCrcTable[256];

// This provides a simple, fast CRC-32 calculation that can be used for checking
// the integrity of data.  It is not a "secure" calculation!  |sum| can start
// with any seed or be used to continue an operation began with previous data.
BASE_EXPORT uint32_t Crc32(uint32_t sum, const void* data, size_t size);

}  // namespace base

#endif  // BASE_METRICS_CRC32_H_
