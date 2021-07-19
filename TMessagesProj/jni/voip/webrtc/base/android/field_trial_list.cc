// Copyright 2014 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <jni.h>

#include <map>
#include <string>

#include "base/android/jni_string.h"
#include "base/base_jni_headers/FieldTrialList_jni.h"
#include "base/lazy_instance.h"
#include "base/macros.h"
#include "base/metrics/field_trial.h"
#include "base/metrics/field_trial_params.h"

using base::android::ConvertJavaStringToUTF8;
using base::android::ConvertUTF8ToJavaString;
using base::android::JavaParamRef;
using base::android::ScopedJavaLocalRef;

namespace {

// Log trials and their groups on activation, for debugging purposes.
class TrialLogger : public base::FieldTrialList::Observer {
 public:
  TrialLogger() {}

  void OnFieldTrialGroupFinalized(const std::string& trial_name,
                                  const std::string& group_name) override {
    Log(trial_name, group_name);
  }

  static void Log(const std::string& trial_name,
                  const std::string& group_name) {
    LOG(INFO) << "Active field trial \"" << trial_name
              << "\" in group \"" << group_name<< '"';
  }

 protected:
  ~TrialLogger() override {}

 private:
  DISALLOW_COPY_AND_ASSIGN(TrialLogger);
};

base::LazyInstance<TrialLogger>::Leaky g_trial_logger =
    LAZY_INSTANCE_INITIALIZER;

}  // namespace

static ScopedJavaLocalRef<jstring> JNI_FieldTrialList_FindFullName(
    JNIEnv* env,
    const JavaParamRef<jstring>& jtrial_name) {
  std::string trial_name(ConvertJavaStringToUTF8(env, jtrial_name));
  return ConvertUTF8ToJavaString(
      env, base::FieldTrialList::FindFullName(trial_name));
}

static jboolean JNI_FieldTrialList_TrialExists(
    JNIEnv* env,
    const JavaParamRef<jstring>& jtrial_name) {
  std::string trial_name(ConvertJavaStringToUTF8(env, jtrial_name));
  return base::FieldTrialList::TrialExists(trial_name);
}

static ScopedJavaLocalRef<jstring> JNI_FieldTrialList_GetVariationParameter(
    JNIEnv* env,
    const JavaParamRef<jstring>& jtrial_name,
    const JavaParamRef<jstring>& jparameter_key) {
  std::map<std::string, std::string> parameters;
  base::GetFieldTrialParams(ConvertJavaStringToUTF8(env, jtrial_name),
                            &parameters);
  return ConvertUTF8ToJavaString(
      env, parameters[ConvertJavaStringToUTF8(env, jparameter_key)]);
}

static void JNI_FieldTrialList_LogActiveTrials(JNIEnv* env) {
  DCHECK(!g_trial_logger.IsCreated()); // This need only be called once.

  LOG(INFO) << "Logging active field trials...";
  base::FieldTrialList::AddObserver(&g_trial_logger.Get());

  // Log any trials that were already active before adding the observer.
  std::vector<base::FieldTrial::ActiveGroup> active_groups;
  base::FieldTrialList::GetActiveFieldTrialGroups(&active_groups);
  for (const base::FieldTrial::ActiveGroup& group : active_groups) {
    TrialLogger::Log(group.trial_name, group.group_name);
  }
}
