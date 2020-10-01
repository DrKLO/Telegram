// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/hash/legacy_hash.h"

#include "base/third_party/cityhash_v103/src/city_v103.h"

namespace base {
namespace legacy {

uint64_t CityHash64(base::span<const uint8_t> data) {
  return internal::cityhash_v103::CityHash64(
      reinterpret_cast<const char*>(data.data()), data.size());
}

uint64_t CityHash64WithSeed(base::span<const uint8_t> data, uint64_t seed) {
  return internal::cityhash_v103::CityHash64WithSeed(
      reinterpret_cast<const char*>(data.data()), data.size(), seed);
}

}  // namespace legacy
}  // namespace base
