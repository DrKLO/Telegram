// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/malloc_zone_functions_mac.h"

#include <atomic>

#include "base/synchronization/lock.h"

namespace base {
namespace allocator {

MallocZoneFunctions g_malloc_zones[kMaxZoneCount];
static_assert(std::is_pod<MallocZoneFunctions>::value,
              "MallocZoneFunctions must be POD");

void StoreZoneFunctions(const ChromeMallocZone* zone,
                        MallocZoneFunctions* functions) {
  memset(functions, 0, sizeof(MallocZoneFunctions));
  functions->malloc = zone->malloc;
  functions->calloc = zone->calloc;
  functions->valloc = zone->valloc;
  functions->free = zone->free;
  functions->realloc = zone->realloc;
  functions->size = zone->size;
  CHECK(functions->malloc && functions->calloc && functions->valloc &&
        functions->free && functions->realloc && functions->size);

  // These functions might be nullptr.
  functions->batch_malloc = zone->batch_malloc;
  functions->batch_free = zone->batch_free;

  if (zone->version >= 5) {
    // Not all custom malloc zones have a memalign.
    functions->memalign = zone->memalign;
  }
  if (zone->version >= 6) {
    // This may be nullptr.
    functions->free_definite_size = zone->free_definite_size;
  }

  functions->context = zone;
}

namespace {

// All modifications to g_malloc_zones are gated behind this lock.
// Dispatch to a malloc zone does not need to acquire this lock.
base::Lock& GetLock() {
  static base::Lock* g_lock = new base::Lock;
  return *g_lock;
}

void EnsureMallocZonesInitializedLocked() {
  GetLock().AssertAcquired();
}

int g_zone_count = 0;

bool IsMallocZoneAlreadyStoredLocked(ChromeMallocZone* zone) {
  EnsureMallocZonesInitializedLocked();
  GetLock().AssertAcquired();
  for (int i = 0; i < g_zone_count; ++i) {
    if (g_malloc_zones[i].context == reinterpret_cast<void*>(zone))
      return true;
  }
  return false;
}

}  // namespace

bool StoreMallocZone(ChromeMallocZone* zone) {
  base::AutoLock l(GetLock());
  EnsureMallocZonesInitializedLocked();
  if (IsMallocZoneAlreadyStoredLocked(zone))
    return false;

  if (g_zone_count == kMaxZoneCount)
    return false;

  StoreZoneFunctions(zone, &g_malloc_zones[g_zone_count]);
  ++g_zone_count;

  // No other thread can possibly see these stores at this point. The code that
  // reads these values is triggered after this function returns. so we want to
  // guarantee that they are committed at this stage"
  std::atomic_thread_fence(std::memory_order_seq_cst);
  return true;
}

bool IsMallocZoneAlreadyStored(ChromeMallocZone* zone) {
  base::AutoLock l(GetLock());
  return IsMallocZoneAlreadyStoredLocked(zone);
}

bool DoesMallocZoneNeedReplacing(ChromeMallocZone* zone,
                                 const MallocZoneFunctions* functions) {
  return IsMallocZoneAlreadyStored(zone) && zone->malloc != functions->malloc;
}

int GetMallocZoneCountForTesting() {
  base::AutoLock l(GetLock());
  return g_zone_count;
}

void ClearAllMallocZonesForTesting() {
  base::AutoLock l(GetLock());
  EnsureMallocZonesInitializedLocked();
  memset(g_malloc_zones, 0, kMaxZoneCount * sizeof(MallocZoneFunctions));
  g_zone_count = 0;
}

}  // namespace allocator
}  // namespace base
