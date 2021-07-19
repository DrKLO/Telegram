// Copyright (c) 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/allocator/partition_allocator/oom_callback.h"

#include "base/logging.h"

namespace base {

namespace {
PartitionAllocOomCallback g_oom_callback;
}  // namespace

void SetPartitionAllocOomCallback(PartitionAllocOomCallback callback) {
  DCHECK(!g_oom_callback);
  g_oom_callback = callback;
}

namespace internal {
void RunPartitionAllocOomCallback() {
  if (g_oom_callback)
    g_oom_callback();
}
}  // namespace internal

}  // namespace base
