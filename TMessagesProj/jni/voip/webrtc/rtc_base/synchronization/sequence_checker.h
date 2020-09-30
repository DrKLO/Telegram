/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_SYNCHRONIZATION_SEQUENCE_CHECKER_H_
#define RTC_BASE_SYNCHRONIZATION_SEQUENCE_CHECKER_H_

#include <type_traits>

#include "api/task_queue/task_queue_base.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {
// Real implementation of SequenceChecker, for use in debug mode, or
// for temporary use in release mode (e.g. to RTC_CHECK on a threading issue
// seen only in the wild).
//
// Note: You should almost always use the SequenceChecker class to get the
// right version for your build configuration.
class RTC_EXPORT SequenceCheckerImpl {
 public:
  SequenceCheckerImpl();
  ~SequenceCheckerImpl();

  bool IsCurrent() const;
  // Changes the task queue or thread that is checked for in IsCurrent. This can
  // be useful when an object may be created on one task queue / thread and then
  // used exclusively on another thread.
  void Detach();

  // Returns a string that is formatted to match with the error string printed
  // by RTC_CHECK() when a condition is not met.
  // This is used in conjunction with the RTC_DCHECK_RUN_ON() macro.
  std::string ExpectationToString() const;

 private:
  mutable Mutex lock_;
  // These are mutable so that IsCurrent can set them.
  mutable bool attached_ RTC_GUARDED_BY(lock_);
  mutable rtc::PlatformThreadRef valid_thread_ RTC_GUARDED_BY(lock_);
  mutable const TaskQueueBase* valid_queue_ RTC_GUARDED_BY(lock_);
  mutable const void* valid_system_queue_ RTC_GUARDED_BY(lock_);
};

// Do nothing implementation, for use in release mode.
//
// Note: You should almost always use the SequenceChecker class to get the
// right version for your build configuration.
class SequenceCheckerDoNothing {
 public:
  bool IsCurrent() const { return true; }
  void Detach() {}
};

// SequenceChecker is a helper class used to help verify that some methods
// of a class are called on the same task queue or thread. A
// SequenceChecker is bound to a a task queue if the object is
// created on a task queue, or a thread otherwise.
//
//
// Example:
// class MyClass {
//  public:
//   void Foo() {
//     RTC_DCHECK_RUN_ON(sequence_checker_);
//     ... (do stuff) ...
//   }
//
//  private:
//   SequenceChecker sequence_checker_;
// }
//
// In Release mode, IsCurrent will always return true.
#if RTC_DCHECK_IS_ON
class RTC_LOCKABLE SequenceChecker : public SequenceCheckerImpl {};
#else
class RTC_LOCKABLE SequenceChecker : public SequenceCheckerDoNothing {};
#endif  // RTC_ENABLE_THREAD_CHECKER

namespace webrtc_seq_check_impl {
// Helper class used by RTC_DCHECK_RUN_ON (see example usage below).
class RTC_SCOPED_LOCKABLE SequenceCheckerScope {
 public:
  template <typename ThreadLikeObject>
  explicit SequenceCheckerScope(const ThreadLikeObject* thread_like_object)
      RTC_EXCLUSIVE_LOCK_FUNCTION(thread_like_object) {}
  SequenceCheckerScope(const SequenceCheckerScope&) = delete;
  SequenceCheckerScope& operator=(const SequenceCheckerScope&) = delete;
  ~SequenceCheckerScope() RTC_UNLOCK_FUNCTION() {}

  template <typename ThreadLikeObject>
  static bool IsCurrent(const ThreadLikeObject* thread_like_object) {
    return thread_like_object->IsCurrent();
  }
};
}  // namespace webrtc_seq_check_impl
}  // namespace webrtc

// RTC_RUN_ON/RTC_GUARDED_BY/RTC_DCHECK_RUN_ON macros allows to annotate
// variables are accessed from same thread/task queue.
// Using tools designed to check mutexes, it checks at compile time everywhere
// variable is access, there is a run-time dcheck thread/task queue is correct.
//
// class ThreadExample {
//  public:
//   void NeedVar1() {
//     RTC_DCHECK_RUN_ON(network_thread_);
//     transport_->Send();
//   }
//
//  private:
//   rtc::Thread* network_thread_;
//   int transport_ RTC_GUARDED_BY(network_thread_);
// };
//
// class SequenceCheckerExample {
//  public:
//   int CalledFromPacer() RTC_RUN_ON(pacer_sequence_checker_) {
//     return var2_;
//   }
//
//   void CallMeFromPacer() {
//     RTC_DCHECK_RUN_ON(&pacer_sequence_checker_)
//        << "Should be called from pacer";
//     CalledFromPacer();
//   }
//
//  private:
//   int pacer_var_ RTC_GUARDED_BY(pacer_sequence_checker_);
//   SequenceChecker pacer_sequence_checker_;
// };
//
// class TaskQueueExample {
//  public:
//   class Encoder {
//    public:
//     rtc::TaskQueue* Queue() { return encoder_queue_; }
//     void Encode() {
//       RTC_DCHECK_RUN_ON(encoder_queue_);
//       DoSomething(var_);
//     }
//
//    private:
//     rtc::TaskQueue* const encoder_queue_;
//     Frame var_ RTC_GUARDED_BY(encoder_queue_);
//   };
//
//   void Encode() {
//     // Will fail at runtime when DCHECK is enabled:
//     // encoder_->Encode();
//     // Will work:
//     rtc::scoped_refptr<Encoder> encoder = encoder_;
//     encoder_->Queue()->PostTask([encoder] { encoder->Encode(); });
//   }
//
//  private:
//   rtc::scoped_refptr<Encoder> encoder_;
// }

// Document if a function expected to be called from same thread/task queue.
#define RTC_RUN_ON(x) \
  RTC_THREAD_ANNOTATION_ATTRIBUTE__(exclusive_locks_required(x))

namespace webrtc {
std::string ExpectationToString(const webrtc::SequenceChecker* checker);

// Catch-all implementation for types other than explicitly supported above.
template <typename ThreadLikeObject>
std::string ExpectationToString(const ThreadLikeObject*) {
  return std::string();
}

}  // namespace webrtc

#define RTC_DCHECK_RUN_ON(x)                                              \
  webrtc::webrtc_seq_check_impl::SequenceCheckerScope seq_check_scope(x); \
  RTC_DCHECK((x)->IsCurrent()) << webrtc::ExpectationToString(x)

#endif  // RTC_BASE_SYNCHRONIZATION_SEQUENCE_CHECKER_H_
