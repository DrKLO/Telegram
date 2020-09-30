// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/process_iterator.h"

namespace base {

ProcessIterator::ProcessIterator(const ProcessFilter* filter) {
  // TODO(fuchsia): There's no Fuchsia API to iterate processes currently.
  NOTREACHED();
}

ProcessIterator::~ProcessIterator() {}

bool ProcessIterator::CheckForNextProcess() {
  // TODO(fuchsia): There's no Fuchsia API to iterate processes currently.
  return false;
}

bool NamedProcessIterator::IncludeEntry() {
  // TODO(fuchsia): There's no Fuchsia API to iterate processes currently.
  return false;
}

}  // namespace base
