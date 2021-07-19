// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/metrics/field_trial_param_associator.h"

#include "base/metrics/field_trial.h"

namespace base {

FieldTrialParamAssociator::FieldTrialParamAssociator() = default;
FieldTrialParamAssociator::~FieldTrialParamAssociator() = default;

// static
FieldTrialParamAssociator* FieldTrialParamAssociator::GetInstance() {
  return Singleton<FieldTrialParamAssociator,
                   LeakySingletonTraits<FieldTrialParamAssociator>>::get();
}

bool FieldTrialParamAssociator::AssociateFieldTrialParams(
    const std::string& trial_name,
    const std::string& group_name,
    const FieldTrialParams& params) {
  if (FieldTrialList::IsTrialActive(trial_name))
    return false;

  AutoLock scoped_lock(lock_);
  const FieldTrialKey key(trial_name, group_name);
  if (Contains(field_trial_params_, key))
    return false;

  field_trial_params_[key] = params;
  return true;
}

bool FieldTrialParamAssociator::GetFieldTrialParams(
    const std::string& trial_name,
    FieldTrialParams* params) {
  FieldTrial* field_trial = FieldTrialList::Find(trial_name);
  if (!field_trial)
    return false;

  // First try the local map, falling back to getting it from shared memory.
  if (GetFieldTrialParamsWithoutFallback(trial_name, field_trial->group_name(),
                                         params)) {
    return true;
  }

  // TODO(lawrencewu): add the params to field_trial_params_ for next time.
  return FieldTrialList::GetParamsFromSharedMemory(field_trial, params);
}

bool FieldTrialParamAssociator::GetFieldTrialParamsWithoutFallback(
    const std::string& trial_name,
    const std::string& group_name,
    FieldTrialParams* params) {
  AutoLock scoped_lock(lock_);

  const FieldTrialKey key(trial_name, group_name);
  if (!Contains(field_trial_params_, key))
    return false;

  *params = field_trial_params_[key];
  return true;
}

void FieldTrialParamAssociator::ClearAllParamsForTesting() {
  {
    AutoLock scoped_lock(lock_);
    field_trial_params_.clear();
  }
  FieldTrialList::ClearParamsFromSharedMemoryForTesting();
}

void FieldTrialParamAssociator::ClearParamsForTesting(
    const std::string& trial_name,
    const std::string& group_name) {
  AutoLock scoped_lock(lock_);
  const FieldTrialKey key(trial_name, group_name);
  field_trial_params_.erase(key);
}

void FieldTrialParamAssociator::ClearAllCachedParamsForTesting() {
  AutoLock scoped_lock(lock_);
  field_trial_params_.clear();
}

}  // namespace base
