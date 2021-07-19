// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/discardable_memory.h"
#include "base/feature_list.h"
#include "base/memory/discardable_memory_internal.h"
#include "base/memory/madv_free_discardable_memory_posix.h"
#include "base/metrics/field_trial_params.h"
#include "build/build_config.h"

#if defined(OS_ANDROID)
#include <third_party/ashmem/ashmem.h>
#endif  // defined(OS_ANDROID)

namespace base {

namespace features {
#if defined(OS_POSIX)
// Feature flag allowing the use of MADV_FREE discardable memory when there are
// multiple supported discardable memory backings.
const base::Feature kMadvFreeDiscardableMemory{
    "MadvFreeDiscardableMemory", base::FEATURE_DISABLED_BY_DEFAULT};
#endif  // defined(OS_POSIX)

#if defined(OS_ANDROID) || defined(OS_LINUX)
const base::Feature kDiscardableMemoryBackingTrial{
    "DiscardableMemoryBackingTrial", base::FEATURE_DISABLED_BY_DEFAULT};

// Association of trial group names to trial group enum. Array order must match
// order of DiscardableMemoryTrialGroup enum.
const base::FeatureParam<DiscardableMemoryTrialGroup>::Option
    kDiscardableMemoryBackingParamOptions[] = {
        {DiscardableMemoryTrialGroup::kEmulatedSharedMemory, "shmem"},
        {DiscardableMemoryTrialGroup::kMadvFree, "madvfree"},
        {DiscardableMemoryTrialGroup::kAshmem, "ashmem"}};

const base::FeatureParam<DiscardableMemoryTrialGroup>
    kDiscardableMemoryBackingParam{
        &kDiscardableMemoryBackingTrial, "DiscardableMemoryBacking",
        DiscardableMemoryTrialGroup::kEmulatedSharedMemory,
        &kDiscardableMemoryBackingParamOptions};

#endif  // defined(OS_ANDROID) || defined(OS_LINUX)

}  // namespace features

namespace {

#if defined(OS_ANDROID) || defined(OS_LINUX)

DiscardableMemoryBacking GetBackingForFieldTrial() {
  DiscardableMemoryTrialGroup trial_group =
      GetDiscardableMemoryBackingFieldTrialGroup();
  switch (trial_group) {
    case DiscardableMemoryTrialGroup::kEmulatedSharedMemory:
    case DiscardableMemoryTrialGroup::kAshmem:
      return DiscardableMemoryBacking::kSharedMemory;
    case DiscardableMemoryTrialGroup::kMadvFree:
      return DiscardableMemoryBacking::kMadvFree;
  }
  NOTREACHED();
}
#endif  // defined(OS_ANDROID) || defined(OS_LINUX)

}  // namespace

#if defined(OS_ANDROID) || defined(OS_LINUX)

// Probe capabilities of this device to determine whether we should participate
// in the discardable memory backing trial.
bool DiscardableMemoryBackingFieldTrialIsEnabled() {
#if defined(OS_ANDROID)
  if (!ashmem_device_is_supported())
    return false;
#endif  // defined(OS_ANDROID)
  if (base::GetMadvFreeSupport() != base::MadvFreeSupport::kSupported)
    return false;

  // IMPORTANT: Only query the feature after we determine the device has the
  // capabilities required, which will have the side-effect of assigning a
  // trial-group.
  return base::FeatureList::IsEnabled(features::kDiscardableMemoryBackingTrial);
}

DiscardableMemoryTrialGroup GetDiscardableMemoryBackingFieldTrialGroup() {
  DCHECK(DiscardableMemoryBackingFieldTrialIsEnabled());
  return features::kDiscardableMemoryBackingParam.Get();
}
#endif  // defined(OS_ANDROID) || defined(OS_LINUX)

DiscardableMemory::DiscardableMemory() = default;

DiscardableMemory::~DiscardableMemory() = default;

DiscardableMemoryBacking GetDiscardableMemoryBacking() {
#if defined(OS_ANDROID) || defined(OS_LINUX)
  if (DiscardableMemoryBackingFieldTrialIsEnabled()) {
    return GetBackingForFieldTrial();
  }
#endif  // defined(OS_ANDROID) || defined(OS_LINUX)

#if defined(OS_ANDROID)
  if (ashmem_device_is_supported())
    return DiscardableMemoryBacking::kSharedMemory;
#endif  // defined(OS_ANDROID)

#if defined(OS_POSIX)
  if (base::FeatureList::IsEnabled(
          base::features::kMadvFreeDiscardableMemory) &&
      base::GetMadvFreeSupport() == base::MadvFreeSupport::kSupported) {
    return DiscardableMemoryBacking::kMadvFree;
  }
#endif  // defined(OS_POSIX)

  return DiscardableMemoryBacking::kSharedMemory;
}

}  // namespace base
