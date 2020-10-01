/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TASK_UTILS_PENDING_TASK_SAFETY_FLAG_H_
#define RTC_BASE_TASK_UTILS_PENDING_TASK_SAFETY_FLAG_H_

#include "api/scoped_refptr.h"
#include "rtc_base/checks.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/synchronization/sequence_checker.h"

namespace webrtc {

// Use this flag to drop pending tasks that have been posted to the "main"
// thread/TQ and end up running after the owning instance has been
// deleted. The owning instance signals deletion by calling SetNotAlive() from
// its destructor.
//
// When posting a task, post a copy (capture by-value in a lambda) of the flag
// instance and before performing the work, check the |alive()| state. Abort if
// alive() returns |false|:
//
//    // Running outside of the main thread.
//    my_task_queue_->PostTask(ToQueuedTask(
//        [safety = pending_task_safety_flag_, this]() {
//          // Now running on the main thread.
//          if (!safety->alive())
//            return;
//          MyMethod();
//        }));
//
// Or implicitly by letting ToQueuedTask do the checking:
//
//    // Running outside of the main thread.
//    my_task_queue_->PostTask(ToQueuedTask(pending_task_safety_flag_,
//        [this]() { MyMethod(); }));
//
// Note that checking the state only works on the construction/destruction
// thread of the ReceiveStatisticsProxy instance.
class PendingTaskSafetyFlag : public rtc::RefCountInterface {
 public:
  static rtc::scoped_refptr<PendingTaskSafetyFlag> Create();

  ~PendingTaskSafetyFlag() = default;

  void SetNotAlive();
  bool alive() const;

 protected:
  PendingTaskSafetyFlag() = default;

 private:
  bool alive_ = true;
  SequenceChecker main_sequence_;
};

// Makes using PendingTaskSafetyFlag very simple. Automatic PTSF creation
// and signalling of destruction when the ScopedTaskSafety instance goes out
// of scope.
// Should be used by the class that wants tasks dropped after destruction.
// Requirements are that the instance be constructed and destructed on
// the same thread as the potentially dropped tasks would be running on.
class ScopedTaskSafety {
 public:
  ScopedTaskSafety() = default;
  ~ScopedTaskSafety() { flag_->SetNotAlive(); }

  // Returns a new reference to the safety flag.
  rtc::scoped_refptr<PendingTaskSafetyFlag> flag() const { return flag_; }

 private:
  rtc::scoped_refptr<PendingTaskSafetyFlag> flag_ =
      PendingTaskSafetyFlag::Create();
};

}  // namespace webrtc

#endif  // RTC_BASE_TASK_UTILS_PENDING_TASK_SAFETY_FLAG_H_
