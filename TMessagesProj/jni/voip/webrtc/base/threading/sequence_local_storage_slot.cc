// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/sequence_local_storage_slot.h"

#include <limits>

#include "base/atomic_sequence_num.h"
#include "base/logging.h"

namespace base {
namespace internal {

namespace {
AtomicSequenceNumber g_sequence_local_storage_slot_generator;
}  // namespace

int GetNextSequenceLocalStorageSlotNumber() {
  int slot_id = g_sequence_local_storage_slot_generator.GetNext();
  DCHECK_LT(slot_id, std::numeric_limits<int>::max());
  return slot_id;
}

}  // namespace internal

}  // namespace base
