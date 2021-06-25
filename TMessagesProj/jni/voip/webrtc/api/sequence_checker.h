/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_SEQUENCE_CHECKER_H_
#define API_SEQUENCE_CHECKER_H_

#include "rtc_base/checks.h"
#include "rtc_base/synchronization/sequence_checker_internal.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

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
//     RTC_DCHECK_RUN_ON(&sequence_checker_);
//     ... (do stuff) ...
//   }
//
//  private:
//   SequenceChecker sequence_checker_;
// }
//
// In Release mode, IsCurrent will always return true.
class RTC_LOCKABLE SequenceChecker
#if RTC_DCHECK_IS_ON
    : public webrtc_sequence_checker_internal::SequenceCheckerImpl {
  using Impl = webrtc_sequence_checker_internal::SequenceCheckerImpl;
#else
    : public webrtc_sequence_checker_internal::SequenceCheckerDoNothing {
  using Impl = webrtc_sequence_checker_internal::SequenceCheckerDoNothing;
#endif
 public:
  // Returns true if sequence checker is attached to the current sequence.
  bool IsCurrent() const { return Impl::IsCurrent(); }
  // Detaches checker from sequence to which it is attached. Next attempt
  // to do a check with this checker will result in attaching this checker
  // to the sequence on which check was performed.
  void Detach() { Impl::Detach(); }
};

}  // namespace webrtc

// RTC_RUN_ON/RTC_GUARDED_BY/RTC_DCHECK_RUN_ON macros allows to annotate
// variables are accessed from same thread/task queue.
// Using tools designed to check mutexes, it checks at compile time everywhere
// variable is access, there is a run-time dcheck thread/task queue is correct.
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
//     rtc::TaskQueueBase& Queue() { return encoder_queue_; }
//     void Encode() {
//       RTC_DCHECK_RUN_ON(&encoder_queue_);
//       DoSomething(var_);
//     }
//
//    private:
//     rtc::TaskQueueBase& encoder_queue_;
//     Frame var_ RTC_GUARDED_BY(encoder_queue_);
//   };
//
//   void Encode() {
//     // Will fail at runtime when DCHECK is enabled:
//     // encoder_->Encode();
//     // Will work:
//     rtc::scoped_refptr<Encoder> encoder = encoder_;
//     encoder_->Queue().PostTask([encoder] { encoder->Encode(); });
//   }
//
//  private:
//   rtc::scoped_refptr<Encoder> encoder_;
// }

// Document if a function expected to be called from same thread/task queue.
#define RTC_RUN_ON(x) \
  RTC_THREAD_ANNOTATION_ATTRIBUTE__(exclusive_locks_required(x))

#define RTC_DCHECK_RUN_ON(x)                                     \
  webrtc::webrtc_sequence_checker_internal::SequenceCheckerScope \
      seq_check_scope(x);                                        \
  RTC_DCHECK((x)->IsCurrent())                                   \
      << webrtc::webrtc_sequence_checker_internal::ExpectationToString(x)

#endif  // API_SEQUENCE_CHECKER_H_
