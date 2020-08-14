// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MEMORY_DISCARDABLE_MEMORY_INTERNAL_H_
#define BASE_MEMORY_DISCARDABLE_MEMORY_INTERNAL_H_

#include "base/base_export.h"
#include "base/feature_list.h"
#include "base/metrics/field_trial_params.h"
#include "build/build_config.h"

#if defined(OS_ANDROID) || defined(OS_LINUX)

namespace base {

// Enumeration of the possible experiment groups in the discardable memory
// backing trial. Note that |kAshmem| and |kEmulatedSharedMemory| both map to
// discardable shared memory, except the former allows for the use of ashmem for
// unpinning memory. Ensure that the order of the enum values matches those in
// |kDiscardableMemoryBackingParamOptions|.
enum DiscardableMemoryTrialGroup : int {
  kEmulatedSharedMemory = 0,
  kMadvFree,
  // Only Android devices will be assigned to the ashmem group.
  kAshmem,
};

namespace features {
// Feature flag enabling the discardable memory backing trial.
BASE_EXPORT extern const base::Feature kDiscardableMemoryBackingTrial;

BASE_EXPORT extern const base::FeatureParam<DiscardableMemoryTrialGroup>::Option
    kDiscardableMemoryBackingParamOptions[];

BASE_EXPORT extern const base::FeatureParam<DiscardableMemoryTrialGroup>
    kDiscardableMemoryBackingParam;
}  // namespace features

// Whether we should do the discardable memory backing trial for this session.
BASE_EXPORT bool DiscardableMemoryBackingFieldTrialIsEnabled();

// If we should do the discardable memory backing trial, then get the trial
// group this session belongs in.
BASE_EXPORT DiscardableMemoryTrialGroup
GetDiscardableMemoryBackingFieldTrialGroup();

}  // namespace base

#endif  // defined(OS_LINUX) || defined(OS_ANDROID)

#endif  //  BASE_MEMORY_DISCARDABLE_MEMORY_INTERNAL_H_
