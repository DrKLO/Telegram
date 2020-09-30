// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/random.h"

#include "base/allocator/partition_allocator/spin_lock.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/rand_util.h"
#include "base/synchronization/lock.h"

namespace base {

namespace {

Lock& GetLock() {
  static NoDestructor<Lock> lock;
  return *lock;
}

}  // namespace

// This is the same PRNG as used by tcmalloc for mapping address randomness;
// see http://burtleburtle.net/bob/rand/smallprng.html.
struct RandomContext {
  bool initialized;
  uint32_t a;
  uint32_t b;
  uint32_t c;
  uint32_t d;
};

static RandomContext g_context GUARDED_BY(GetLock());

namespace {

RandomContext& GetRandomContext() EXCLUSIVE_LOCKS_REQUIRED(GetLock()) {
  if (UNLIKELY(!g_context.initialized)) {
    const uint64_t r1 = RandUint64();
    const uint64_t r2 = RandUint64();
    g_context.a = static_cast<uint32_t>(r1);
    g_context.b = static_cast<uint32_t>(r1 >> 32);
    g_context.c = static_cast<uint32_t>(r2);
    g_context.d = static_cast<uint32_t>(r2 >> 32);
    g_context.initialized = true;
  }
  return g_context;
}

}  // namespace

uint32_t RandomValue() {
  AutoLock guard(GetLock());
  RandomContext& x = GetRandomContext();
#define rot(x, k) (((x) << (k)) | ((x) >> (32 - (k))))
  uint32_t e = x.a - rot(x.b, 27);
  x.a = x.b ^ rot(x.c, 17);
  x.b = x.c + x.d;
  x.c = x.d + e;
  x.d = e + x.a;
  return x.d;
#undef rot
}

void SetMmapSeedForTesting(uint64_t seed) {
  AutoLock guard(GetLock());
  RandomContext& x = GetRandomContext();
  x.a = x.b = static_cast<uint32_t>(seed);
  x.c = x.d = static_cast<uint32_t>(seed >> 32);
  x.initialized = true;
}

}  // namespace base
