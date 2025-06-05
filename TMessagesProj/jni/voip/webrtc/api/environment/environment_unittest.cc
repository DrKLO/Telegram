/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/environment/environment.h"

#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/functional/any_invocable.h"
#include "absl/types/optional.h"
#include "api/environment/environment_factory.h"
#include "api/field_trials_view.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/units/timestamp.h"
#include "system_wrappers/include/clock.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {
namespace {

using ::testing::ElementsAre;
using ::testing::IsEmpty;
using ::testing::Not;
using ::testing::NotNull;
using ::testing::Ref;

class FakeEvent : public RtcEvent {
 public:
  Type GetType() const override { return RtcEvent::Type::FakeEvent; }
  bool IsConfigEvent() const override { return false; }
};

class FakeFieldTrials : public FieldTrialsView {
 public:
  explicit FakeFieldTrials(absl::AnyInvocable<void() &&> on_destroyed = nullptr)
      : on_destroyed_(std::move(on_destroyed)) {}
  ~FakeFieldTrials() override {
    if (on_destroyed_ != nullptr) {
      std::move(on_destroyed_)();
    }
  }

  std::string Lookup(absl::string_view key) const override { return "fake"; }

 private:
  absl::AnyInvocable<void() &&> on_destroyed_;
};

class FakeTaskQueueFactory : public TaskQueueFactory {
 public:
  explicit FakeTaskQueueFactory(
      absl::AnyInvocable<void() &&> on_destroyed = nullptr)
      : on_destroyed_(std::move(on_destroyed)) {}
  ~FakeTaskQueueFactory() override {
    if (on_destroyed_ != nullptr) {
      std::move(on_destroyed_)();
    }
  }

  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> CreateTaskQueue(
      absl::string_view name,
      Priority priority) const override {
    return nullptr;
  }

 private:
  absl::AnyInvocable<void() &&> on_destroyed_;
};

TEST(EnvironmentTest, DefaultEnvironmentHasAllUtilities) {
  Environment env = EnvironmentFactory().Create();

  // Try to use each utility, expect no crashes.
  env.clock().CurrentTime();
  EXPECT_THAT(env.task_queue_factory().CreateTaskQueue(
                  "test", TaskQueueFactory::Priority::NORMAL),
              NotNull());
  env.event_log().Log(std::make_unique<FakeEvent>());
  env.field_trials().Lookup("WebRTC-Debugging-RtpDump");
}

TEST(EnvironmentTest, UsesProvidedUtilitiesWithOwnership) {
  auto owned_field_trials = std::make_unique<FakeFieldTrials>();
  auto owned_task_queue_factory = std::make_unique<FakeTaskQueueFactory>();
  auto owned_clock = std::make_unique<SimulatedClock>(Timestamp::Zero());
  auto owned_event_log = std::make_unique<RtcEventLogNull>();

  FieldTrialsView& field_trials = *owned_field_trials;
  TaskQueueFactory& task_queue_factory = *owned_task_queue_factory;
  Clock& clock = *owned_clock;
  RtcEventLog& event_log = *owned_event_log;

  Environment env = CreateEnvironment(
      std::move(owned_field_trials), std::move(owned_clock),
      std::move(owned_task_queue_factory), std::move(owned_event_log));

  EXPECT_THAT(env.field_trials(), Ref(field_trials));
  EXPECT_THAT(env.task_queue_factory(), Ref(task_queue_factory));
  EXPECT_THAT(env.clock(), Ref(clock));
  EXPECT_THAT(env.event_log(), Ref(event_log));
}

TEST(EnvironmentTest, UsesProvidedUtilitiesWithoutOwnership) {
  FakeFieldTrials field_trials;
  FakeTaskQueueFactory task_queue_factory;
  SimulatedClock clock(Timestamp::Zero());
  RtcEventLogNull event_log;

  Environment env =
      CreateEnvironment(&field_trials, &clock, &task_queue_factory, &event_log);

  EXPECT_THAT(env.field_trials(), Ref(field_trials));
  EXPECT_THAT(env.task_queue_factory(), Ref(task_queue_factory));
  EXPECT_THAT(env.clock(), Ref(clock));
  EXPECT_THAT(env.event_log(), Ref(event_log));
}

TEST(EnvironmentTest, UsesLastProvidedUtility) {
  auto owned_field_trials1 = std::make_unique<FakeFieldTrials>();
  auto owned_field_trials2 = std::make_unique<FakeFieldTrials>();
  FieldTrialsView& field_trials2 = *owned_field_trials2;

  Environment env = CreateEnvironment(std::move(owned_field_trials1),
                                      std::move(owned_field_trials2));

  EXPECT_THAT(env.field_trials(), Ref(field_trials2));
}

// Utilities can be provided from different sources, and when some source
// choose not to provide an utility, it is usually expressed with nullptr.
// When utility is not provided, it is natural to use previously set one.
// E.g. Both PeerConnectionFactoryDependencies and PeerConnectionDependencies
// provide field trials. When PeerConnectionDependencies::trials == nullptr,
// then trials from the PeerConnectionFactoryDependencies should be used.
// With nullptr accepted and ignored this can be expressed by
// `Environemt env = CreateEnvironment(pcf_deps.trials, pc_deps.trials);`
// That would use pc_deps.trials when not nullptr, pcf_deps.trials when
// pc_deps.trials is nullptr, but pcf_deps.trials is not, and default field
// trials when both are nullptr.
TEST(EnvironmentTest, IgnoresProvidedNullptrUtility) {
  auto owned_field_trials = std::make_unique<FakeFieldTrials>();
  std::unique_ptr<FieldTrialsView> null_field_trials = nullptr;
  FieldTrialsView& field_trials = *owned_field_trials;

  Environment env = CreateEnvironment(std::move(owned_field_trials),
                                      std::move(null_field_trials));

  EXPECT_THAT(env.field_trials(), Ref(field_trials));
}

TEST(EnvironmentTest, KeepsUtilityAliveWhileEnvironmentIsAlive) {
  bool utility_destroyed = false;
  auto field_trials = std::make_unique<FakeFieldTrials>(
      /*on_destroyed=*/[&] { utility_destroyed = true; });

  // Wrap Environment into optional to have explicit control when it is deleted.
  absl::optional<Environment> env = CreateEnvironment(std::move(field_trials));

  EXPECT_FALSE(utility_destroyed);
  env = absl::nullopt;
  EXPECT_TRUE(utility_destroyed);
}

TEST(EnvironmentTest, KeepsUtilityAliveWhileCopyOfEnvironmentIsAlive) {
  bool utility_destroyed = false;
  auto field_trials = std::make_unique<FakeFieldTrials>(
      /*on_destroyed=*/[&] { utility_destroyed = true; });

  absl::optional<Environment> env1 = CreateEnvironment(std::move(field_trials));
  absl::optional<Environment> env2 = env1;

  EXPECT_FALSE(utility_destroyed);
  env1 = absl::nullopt;
  EXPECT_FALSE(utility_destroyed);
  env2 = absl::nullopt;
  EXPECT_TRUE(utility_destroyed);
}

TEST(EnvironmentTest, FactoryCanBeReusedToCreateDifferentEnvironments) {
  auto owned_task_queue_factory = std::make_unique<FakeTaskQueueFactory>();
  auto owned_field_trials1 = std::make_unique<FakeFieldTrials>();
  auto owned_field_trials2 = std::make_unique<FakeFieldTrials>();
  TaskQueueFactory& task_queue_factory = *owned_task_queue_factory;
  FieldTrialsView& field_trials1 = *owned_field_trials1;
  FieldTrialsView& field_trials2 = *owned_field_trials2;

  EnvironmentFactory factory;
  factory.Set(std::move(owned_task_queue_factory));
  factory.Set(std::move(owned_field_trials1));
  Environment env1 = factory.Create();
  factory.Set(std::move(owned_field_trials2));
  Environment env2 = factory.Create();

  // Environments share the same custom task queue factory.
  EXPECT_THAT(env1.task_queue_factory(), Ref(task_queue_factory));
  EXPECT_THAT(env2.task_queue_factory(), Ref(task_queue_factory));

  // Environments have different field trials.
  EXPECT_THAT(env1.field_trials(), Ref(field_trials1));
  EXPECT_THAT(env2.field_trials(), Ref(field_trials2));
}

TEST(EnvironmentTest, FactoryCanCreateNewEnvironmentFromExistingOne) {
  Environment env1 =
      CreateEnvironment(std::make_unique<FakeTaskQueueFactory>());
  EnvironmentFactory factory(env1);
  factory.Set(std::make_unique<FakeFieldTrials>());
  Environment env2 = factory.Create();

  // Environments share the same default clock.
  EXPECT_THAT(env2.clock(), Ref(env1.clock()));

  // Environments share the same custom task queue factory.
  EXPECT_THAT(env2.task_queue_factory(), Ref(env1.task_queue_factory()));

  // Environments have different field trials.
  EXPECT_THAT(env2.field_trials(), Not(Ref(env1.field_trials())));
}

TEST(EnvironmentTest, KeepsOwnershipsWhenCreateNewEnvironmentFromExistingOne) {
  bool utility1_destroyed = false;
  bool utility2_destroyed = false;
  absl::optional<Environment> env1 =
      CreateEnvironment(std::make_unique<FakeTaskQueueFactory>(
          /*on_destroyed=*/[&] { utility1_destroyed = true; }));

  absl::optional<EnvironmentFactory> factory = EnvironmentFactory(*env1);

  // Destroy env1, check utility1 it was using is still alive.
  env1 = absl::nullopt;
  EXPECT_FALSE(utility1_destroyed);

  factory->Set(std::make_unique<FakeFieldTrials>(
      /*on_destroyed=*/[&] { utility2_destroyed = true; }));
  absl::optional<Environment> env2 = factory->Create();

  // Destroy the factory, check all utilities used by env2 are alive.
  factory = absl::nullopt;
  EXPECT_FALSE(utility1_destroyed);
  EXPECT_FALSE(utility2_destroyed);

  // Once last Environment object is deleted, utilties should be deleted too.
  env2 = absl::nullopt;
  EXPECT_TRUE(utility1_destroyed);
  EXPECT_TRUE(utility2_destroyed);
}

TEST(EnvironmentTest, DestroysUtilitiesInReverseProvidedOrder) {
  std::vector<std::string> destroyed;
  auto field_trials = std::make_unique<FakeFieldTrials>(
      /*on_destroyed=*/[&] { destroyed.push_back("field_trials"); });
  auto task_queue_factory = std::make_unique<FakeTaskQueueFactory>(
      /*on_destroyed=*/[&] { destroyed.push_back("task_queue_factory"); });

  absl::optional<Environment> env =
      CreateEnvironment(std::move(field_trials), std::move(task_queue_factory));

  ASSERT_THAT(destroyed, IsEmpty());
  env = absl::nullopt;
  EXPECT_THAT(destroyed, ElementsAre("task_queue_factory", "field_trials"));
}

}  // namespace
}  // namespace webrtc
