// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/test/sequence_manager_for_test.h"

#include "base/task/sequence_manager/thread_controller_impl.h"

namespace base {
namespace sequence_manager {

namespace {

class ThreadControllerForTest : public internal::ThreadControllerImpl {
 public:
  ThreadControllerForTest(
      internal::SequenceManagerImpl* funneled_sequence_manager,
      scoped_refptr<SingleThreadTaskRunner> task_runner,
      const TickClock* time_source)
      : ThreadControllerImpl(funneled_sequence_manager,
                             std::move(task_runner),
                             time_source) {}

  void AddNestingObserver(RunLoop::NestingObserver* observer) override {
    if (!funneled_sequence_manager_)
      return;
    ThreadControllerImpl::AddNestingObserver(observer);
  }

  void RemoveNestingObserver(RunLoop::NestingObserver* observer) override {
    if (!funneled_sequence_manager_)
      return;
    ThreadControllerImpl::RemoveNestingObserver(observer);
  }

  ~ThreadControllerForTest() override = default;
};

}  // namespace

SequenceManagerForTest::SequenceManagerForTest(
    std::unique_ptr<internal::ThreadController> thread_controller,
    SequenceManager::Settings settings)
    : SequenceManagerImpl(std::move(thread_controller), std::move(settings)) {}

// static
std::unique_ptr<SequenceManagerForTest> SequenceManagerForTest::Create(
    SequenceManagerImpl* funneled_sequence_manager,
    scoped_refptr<SingleThreadTaskRunner> task_runner,
    const TickClock* clock,
    SequenceManager::Settings settings) {
  std::unique_ptr<SequenceManagerForTest> manager(new SequenceManagerForTest(
      std::make_unique<ThreadControllerForTest>(funneled_sequence_manager,
                                                std::move(task_runner), clock),
      std::move(settings)));
  manager->BindToCurrentThread();
  return manager;
}

// static
std::unique_ptr<SequenceManagerForTest> SequenceManagerForTest::Create(
    std::unique_ptr<internal::ThreadController> thread_controller,
    SequenceManager::Settings settings) {
  std::unique_ptr<SequenceManagerForTest> manager(new SequenceManagerForTest(
      std::move(thread_controller), std::move(settings)));
  manager->BindToCurrentThread();
  return manager;
}

// static
std::unique_ptr<SequenceManagerForTest>
SequenceManagerForTest::CreateOnCurrentThread(
    SequenceManager::Settings settings) {
  return Create(CreateThreadControllerImplForCurrentThread(settings.clock),
                std::move(settings));
}

size_t SequenceManagerForTest::ActiveQueuesCount() const {
  return main_thread_only().active_queues.size();
}

bool SequenceManagerForTest::HasImmediateWork() const {
  return main_thread_only().selector.GetHighestPendingPriority().has_value();
}

size_t SequenceManagerForTest::PendingTasksCount() const {
  size_t task_count = 0;
  for (auto* const queue : main_thread_only().active_queues)
    task_count += queue->GetNumberOfPendingTasks();
  return task_count;
}

size_t SequenceManagerForTest::QueuesToDeleteCount() const {
  return main_thread_only().queues_to_delete.size();
}

size_t SequenceManagerForTest::QueuesToShutdownCount() {
  return main_thread_only().queues_to_gracefully_shutdown.size();
}

}  // namespace sequence_manager
}  // namespace base
