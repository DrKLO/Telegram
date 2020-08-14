// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/observer_list_types.h"

namespace base {

CheckedObserver::CheckedObserver() {}
CheckedObserver::~CheckedObserver() = default;

bool CheckedObserver::IsInObserverList() const {
  return factory_.HasWeakPtrs();
}

}  // namespace base
