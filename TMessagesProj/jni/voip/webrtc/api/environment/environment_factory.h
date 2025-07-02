/*
 *  Copyright 2023 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_ENVIRONMENT_ENVIRONMENT_FACTORY_H_
#define API_ENVIRONMENT_ENVIRONMENT_FACTORY_H_

#include <memory>
#include <utility>

#include "absl/base/nullability.h"
#include "api/environment/environment.h"
#include "api/ref_counted_base.h"
#include "api/scoped_refptr.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// These classes are forward declared to reduce amount of headers exposed
// through api header.
class Clock;
class TaskQueueFactory;
class FieldTrialsView;
class RtcEventLog;

// Constructs `Environment`.
// Individual utilities are provided using one of the `Set` functions.
// `Set` functions do nothing when nullptr value is passed.
// Creates default implementations for utilities that are not provided.
//
// Examples:
//    Environment default_env = EnvironmentFactory().Create();
//
//    EnvironmentFactory factory;
//    factory.Set(std::make_unique<CustomTaskQueueFactory>());
//    factory.Set(std::make_unique<CustomFieldTrials>());
//    Environment custom_env = factory.Create();
//
class RTC_EXPORT EnvironmentFactory final {
 public:
  EnvironmentFactory() = default;
  explicit EnvironmentFactory(const Environment& env);

  EnvironmentFactory(const EnvironmentFactory&) = default;
  EnvironmentFactory(EnvironmentFactory&&) = default;
  EnvironmentFactory& operator=(const EnvironmentFactory&) = default;
  EnvironmentFactory& operator=(EnvironmentFactory&&) = default;

  ~EnvironmentFactory() = default;

  void Set(absl::Nullable<std::unique_ptr<const FieldTrialsView>> utility);
  void Set(absl::Nullable<std::unique_ptr<Clock>> utility);
  void Set(absl::Nullable<std::unique_ptr<TaskQueueFactory>> utility);
  void Set(absl::Nullable<std::unique_ptr<RtcEventLog>> utility);

  void Set(absl::Nullable<const FieldTrialsView*> utility);
  void Set(absl::Nullable<Clock*> utility);
  void Set(absl::Nullable<TaskQueueFactory*> utility);
  void Set(absl::Nullable<RtcEventLog*> utility);

  Environment Create() const;

 private:
  Environment CreateWithDefaults() &&;

  scoped_refptr<const rtc::RefCountedBase> leaf_;

  absl::Nullable<const FieldTrialsView*> field_trials_ = nullptr;
  absl::Nullable<Clock*> clock_ = nullptr;
  absl::Nullable<TaskQueueFactory*> task_queue_factory_ = nullptr;
  absl::Nullable<RtcEventLog*> event_log_ = nullptr;
};

// Helper for concise way to create an environment.
// `Environment env = CreateEnvironment(utility1, utility2)` is a shortcut to
// `EnvironmentFactory factory;
// factory.Set(utility1);
// factory.Set(utility2);
// Environment env = factory.Create();`
//
// Examples:
//    Environment default_env = CreateEnvironment();
//    Environment custom_env =
//        CreateEnvironment(std::make_unique<CustomTaskQueueFactory>(),
//                          std::make_unique<CustomFieldTrials>());
template <typename... Utilities>
Environment CreateEnvironment(Utilities&&... utilities);

//------------------------------------------------------------------------------
// Implementation details follow
//------------------------------------------------------------------------------

inline void EnvironmentFactory::Set(
    absl::Nullable<const FieldTrialsView*> utility) {
  if (utility != nullptr) {
    field_trials_ = utility;
  }
}

inline void EnvironmentFactory::Set(absl::Nullable<Clock*> utility) {
  if (utility != nullptr) {
    clock_ = utility;
  }
}

inline void EnvironmentFactory::Set(absl::Nullable<TaskQueueFactory*> utility) {
  if (utility != nullptr) {
    task_queue_factory_ = utility;
  }
}

inline void EnvironmentFactory::Set(absl::Nullable<RtcEventLog*> utility) {
  if (utility != nullptr) {
    event_log_ = utility;
  }
}

namespace webrtc_create_environment_internal {

inline void Set(EnvironmentFactory& factory) {}

template <typename FirstUtility, typename... Utilities>
void Set(EnvironmentFactory& factory,
         FirstUtility&& first,
         Utilities&&... utilities) {
  factory.Set(std::forward<FirstUtility>(first));
  Set(factory, std::forward<Utilities>(utilities)...);
}

}  // namespace webrtc_create_environment_internal

template <typename... Utilities>
Environment CreateEnvironment(Utilities&&... utilities) {
  EnvironmentFactory factory;
  webrtc_create_environment_internal::Set(
      factory, std::forward<Utilities>(utilities)...);
  return factory.Create();
}

}  // namespace webrtc

#endif  // API_ENVIRONMENT_ENVIRONMENT_FACTORY_H_
