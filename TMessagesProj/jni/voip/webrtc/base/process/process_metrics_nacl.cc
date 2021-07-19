// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_metrics.h"

#include <stddef.h>
#include <unistd.h>

namespace base {

size_t GetPageSize() {
  return getpagesize();
}

}  // namespace base
