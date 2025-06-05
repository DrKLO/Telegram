/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/task_queue/pending_task_safety_flag.h"

#include <memory>

#include "rtc_base/event.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_queue_for_test.h"
#include "test/gmock.h"
#include "test/gtest.h"

namespace webrtc {

TEST(PendingTaskSafetyFlagTest, Basic) {
  rtc::scoped_refptr<PendingTaskSafetyFlag> safety_flag;
  {
    // Scope for the `owner` instance.
    class Owner {
     public:
      Owner() = default;
      ~Owner() { flag_->SetNotAlive(); }

      rtc::scoped_refptr<PendingTaskSafetyFlag> flag_ =
          PendingTaskSafetyFlag::Create();
    } owner;
    EXPECT_TRUE(owner.flag_->alive());
    safety_flag = owner.flag_;
    EXPECT_TRUE(safety_flag->alive());
  }
  // `owner` now out of scope.
  EXPECT_FALSE(safety_flag->alive());
}

TEST(PendingTaskSafetyFlagTest, BasicScoped) {
  rtc::scoped_refptr<PendingTaskSafetyFlag> safety_flag;
  {
    struct Owner {
      ScopedTaskSafety safety;
    } owner;
    safety_flag = owner.safety.flag();
    EXPECT_TRUE(safety_flag->alive());
  }
  // `owner` now out of scope.
  EXPECT_FALSE(safety_flag->alive());
}

TEST(PendingTaskSafetyFlagTest, PendingTaskSuccess) {
  TaskQueueForTest tq1("OwnerHere");
  TaskQueueForTest tq2("OwnerNotHere");

  class Owner {
   public:
    Owner() : tq_main_(TaskQueueBase::Current()) { RTC_DCHECK(tq_main_); }
    ~Owner() {
      RTC_DCHECK(tq_main_->IsCurrent());
      flag_->SetNotAlive();
    }

    void DoStuff() {
      RTC_DCHECK(!tq_main_->IsCurrent());
      rtc::scoped_refptr<PendingTaskSafetyFlag> safe = flag_;
      tq_main_->PostTask([safe = std::move(safe), this]() {
        if (!safe->alive())
          return;
        stuff_done_ = true;
      });
    }

    bool stuff_done() const { return stuff_done_; }

   private:
    TaskQueueBase* const tq_main_;
    bool stuff_done_ = false;
    rtc::scoped_refptr<PendingTaskSafetyFlag> flag_ =
        PendingTaskSafetyFlag::Create();
  };

  std::unique_ptr<Owner> owner;
  tq1.SendTask([&owner]() {
    owner = std::make_unique<Owner>();
    EXPECT_FALSE(owner->stuff_done());
  });
  ASSERT_TRUE(owner);
  tq2.SendTask([&owner]() { owner->DoStuff(); });
  tq1.SendTask([&owner]() {
    EXPECT_TRUE(owner->stuff_done());
    owner.reset();
  });
  ASSERT_FALSE(owner);
}

TEST(PendingTaskSafetyFlagTest, PendingTaskDropped) {
  TaskQueueForTest tq1("OwnerHere");
  TaskQueueForTest tq2("OwnerNotHere");

  class Owner {
   public:
    explicit Owner(bool* stuff_done)
        : tq_main_(TaskQueueBase::Current()), stuff_done_(stuff_done) {
      RTC_DCHECK(tq_main_);
      *stuff_done_ = false;
    }
    ~Owner() { RTC_DCHECK(tq_main_->IsCurrent()); }

    void DoStuff() {
      RTC_DCHECK(!tq_main_->IsCurrent());
      tq_main_->PostTask(
          SafeTask(safety_.flag(), [this]() { *stuff_done_ = true; }));
    }

   private:
    TaskQueueBase* const tq_main_;
    bool* const stuff_done_;
    ScopedTaskSafety safety_;
  };

  std::unique_ptr<Owner> owner;
  bool stuff_done = false;
  tq1.SendTask([&owner, &stuff_done]() {
    owner = std::make_unique<Owner>(&stuff_done);
  });
  ASSERT_TRUE(owner);
  // Queue up a task on tq1 that will execute before the 'DoStuff' task
  // can, and delete the `owner` before the 'stuff' task can execute.
  rtc::Event blocker;
  tq1.PostTask([&blocker, &owner]() {
    blocker.Wait(rtc::Event::kForever);
    owner.reset();
  });

  // Queue up a DoStuff...
  tq2.SendTask([&owner]() { owner->DoStuff(); });

  ASSERT_TRUE(owner);
  blocker.Set();

  // Run an empty task on tq1 to flush all the queued tasks.
  tq1.WaitForPreviouslyPostedTasks();
  ASSERT_FALSE(owner);
  EXPECT_FALSE(stuff_done);
}

TEST(PendingTaskSafetyFlagTest, PendingTaskNotAliveInitialized) {
  TaskQueueForTest tq("PendingTaskNotAliveInitialized");

  // Create a new flag that initially not `alive`.
  auto flag = PendingTaskSafetyFlag::CreateDetachedInactive();
  tq.SendTask([&flag]() { EXPECT_FALSE(flag->alive()); });

  bool task_1_ran = false;
  bool task_2_ran = false;
  tq.PostTask(SafeTask(flag, [&task_1_ran]() { task_1_ran = true; }));
  tq.PostTask([&flag]() { flag->SetAlive(); });
  tq.PostTask(SafeTask(flag, [&task_2_ran]() { task_2_ran = true; }));

  tq.WaitForPreviouslyPostedTasks();
  EXPECT_FALSE(task_1_ran);
  EXPECT_TRUE(task_2_ran);
}

TEST(PendingTaskSafetyFlagTest, PendingTaskInitializedForTaskQueue) {
  TaskQueueForTest tq("PendingTaskAliveInitializedForTaskQueue");

  // Create a new flag that initially `alive`, attached to a specific TQ.
  auto flag = PendingTaskSafetyFlag::CreateAttachedToTaskQueue(true, tq.Get());
  tq.SendTask([&flag]() { EXPECT_TRUE(flag->alive()); });
  // Repeat the same steps but initialize as inactive.
  flag = PendingTaskSafetyFlag::CreateAttachedToTaskQueue(false, tq.Get());
  tq.SendTask([&flag]() { EXPECT_FALSE(flag->alive()); });
}

TEST(PendingTaskSafetyFlagTest, SafeTask) {
  rtc::scoped_refptr<PendingTaskSafetyFlag> flag =
      PendingTaskSafetyFlag::Create();

  int count = 0;
  // Create two identical tasks that increment the `count`.
  auto task1 = SafeTask(flag, [&count] { ++count; });
  auto task2 = SafeTask(flag, [&count] { ++count; });

  EXPECT_EQ(count, 0);
  std::move(task1)();
  EXPECT_EQ(count, 1);
  flag->SetNotAlive();
  // Now task2 should actually not run.
  std::move(task2)();
  EXPECT_EQ(count, 1);
}

}  // namespace webrtc
