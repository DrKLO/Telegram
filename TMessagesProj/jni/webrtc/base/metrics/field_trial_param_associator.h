// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_METRICS_FIELD_TRIAL_PARAM_ASSOCIATOR_H_
#define BASE_METRICS_FIELD_TRIAL_PARAM_ASSOCIATOR_H_

#include <map>
#include <string>
#include <utility>

#include "base/base_export.h"
#include "base/memory/singleton.h"
#include "base/metrics/field_trial.h"
#include "base/metrics/field_trial_params.h"
#include "base/synchronization/lock.h"

namespace base {

// Keeps track of the parameters of all field trials and ensures access to them
// is thread-safe.
class BASE_EXPORT FieldTrialParamAssociator {
 public:
  FieldTrialParamAssociator();
  ~FieldTrialParamAssociator();

  // Retrieve the singleton.
  static FieldTrialParamAssociator* GetInstance();

  // Sets parameters for the given field trial name and group.
  bool AssociateFieldTrialParams(const std::string& trial_name,
                                 const std::string& group_name,
                                 const FieldTrialParams& params);

  // Gets the parameters for a field trial and its chosen group. If not found in
  // field_trial_params_, then tries to looks it up in shared memory.
  bool GetFieldTrialParams(const std::string& trial_name,
                           FieldTrialParams* params);

  // Gets the parameters for a field trial and its chosen group. Does not
  // fallback to looking it up in shared memory. This should only be used if you
  // know for sure the params are in the mapping, like if you're in the browser
  // process, and even then you should probably just use GetFieldTrialParams().
  bool GetFieldTrialParamsWithoutFallback(const std::string& trial_name,
                                          const std::string& group_name,
                                          FieldTrialParams* params);

  // Clears the internal field_trial_params_ mapping, plus removes all params in
  // shared memory.
  void ClearAllParamsForTesting();

  // Clears a single field trial param.
  // Note: this does NOT remove the param in shared memory.
  void ClearParamsForTesting(const std::string& trial_name,
                             const std::string& group_name);

  // Clears the internal field_trial_params_ mapping.
  void ClearAllCachedParamsForTesting();

 private:
  friend struct DefaultSingletonTraits<FieldTrialParamAssociator>;

  // (field_trial_name, field_trial_group)
  typedef std::pair<std::string, std::string> FieldTrialKey;

  Lock lock_;
  std::map<FieldTrialKey, FieldTrialParams> field_trial_params_;

  DISALLOW_COPY_AND_ASSIGN(FieldTrialParamAssociator);
};

}  // namespace base

#endif  // BASE_METRICS_FIELD_TRIAL_PARAM_ASSOCIATOR_H_
