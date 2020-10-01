// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/threading/platform_thread.h"

#include <memory>

#include "base/feature_list.h"

namespace base {

namespace {

// Whether thread priorities should be used. When disabled,
// PlatformThread::SetCurrentThreadPriority() no-ops.
const Feature kThreadPrioritiesFeature{"ThreadPriorities",
                                       FEATURE_ENABLED_BY_DEFAULT};

// Whether thread priorities should be used.
//
// PlatformThread::SetCurrentThreadPriority() doesn't query the state of the
// feature directly because FeatureList initialization is not always
// synchronized with PlatformThread::SetCurrentThreadPriority().
std::atomic<bool> g_use_thread_priorities(true);

}  // namespace

// static
void PlatformThread::SetCurrentThreadPriority(ThreadPriority priority) {
  if (g_use_thread_priorities.load())
    SetCurrentThreadPriorityImpl(priority);
}

namespace internal {

void InitializeThreadPrioritiesFeature() {
  // A DCHECK is triggered on FeatureList initialization if the state of a
  // feature has been checked before. To avoid triggering this DCHECK in unit
  // tests that call this before initializing the FeatureList, only check the
  // state of the feature if the FeatureList is initialized.
  if (FeatureList::GetInstance() &&
      !FeatureList::IsEnabled(kThreadPrioritiesFeature)) {
    g_use_thread_priorities.store(false);
  }
}

}  // namespace internal

}  // namespace base
