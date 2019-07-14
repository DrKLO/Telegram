/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Borrowed from Chromium's src/base/threading/thread_checker.h.

#ifndef RTC_BASE_THREAD_CHECKER_H_
#define RTC_BASE_THREAD_CHECKER_H_

// Apart from debug builds, we also enable the thread checker in
// builds with RTC_DCHECK_IS_ON so that trybots and waterfall bots
// with this define will get the same level of thread checking as
// debug bots.
#define RTC_ENABLE_THREAD_CHECKER RTC_DCHECK_IS_ON

#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_checker_impl.h"

namespace rtc {

// Do nothing implementation, for use in release mode.
//
// Note: You should almost always use the ThreadChecker class to get the
// right version for your build configuration.
class ThreadCheckerDoNothing {
 public:
  bool CalledOnValidThread() const { return true; }

  void DetachFromThread() {}
};

// ThreadChecker is a helper class used to help verify that some methods of a
// class are called from the same thread. It provides identical functionality to
// base::NonThreadSafe, but it is meant to be held as a member variable, rather
// than inherited from base::NonThreadSafe.
//
// While inheriting from base::NonThreadSafe may give a clear indication about
// the thread-safety of a class, it may also lead to violations of the style
// guide with regard to multiple inheritance. The choice between having a
// ThreadChecker member and inheriting from base::NonThreadSafe should be based
// on whether:
//  - Derived classes need to know the thread they belong to, as opposed to
//    having that functionality fully encapsulated in the base class.
//  - Derived classes should be able to reassign the base class to another
//    thread, via DetachFromThread.
//
// If neither of these are true, then having a ThreadChecker member and calling
// CalledOnValidThread is the preferable solution.
//
// Example:
// class MyClass {
//  public:
//   void Foo() {
//     RTC_DCHECK(thread_checker_.CalledOnValidThread());
//     ... (do stuff) ...
//   }
//
//  private:
//   ThreadChecker thread_checker_;
// }
//
// In Release mode, CalledOnValidThread will always return true.
#if RTC_ENABLE_THREAD_CHECKER
class RTC_LOCKABLE ThreadChecker : public ThreadCheckerImpl {};
#else
class RTC_LOCKABLE ThreadChecker : public ThreadCheckerDoNothing {};
#endif  // RTC_ENABLE_THREAD_CHECKER

#undef RTC_ENABLE_THREAD_CHECKER

namespace internal {
class RTC_SCOPED_LOCKABLE AnnounceOnThread {
 public:
  template <typename ThreadLikeObject>
  explicit AnnounceOnThread(const ThreadLikeObject* thread_like_object)
      RTC_EXCLUSIVE_LOCK_FUNCTION(thread_like_object) {}
  ~AnnounceOnThread() RTC_UNLOCK_FUNCTION() {}

  template <typename ThreadLikeObject>
  static bool IsCurrent(const ThreadLikeObject* thread_like_object) {
    return thread_like_object->IsCurrent();
  }
  static bool IsCurrent(const rtc::ThreadChecker* checker) {
    return checker->CalledOnValidThread();
  }

 private:
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(AnnounceOnThread);
};

}  // namespace internal
}  // namespace rtc

// RTC_RUN_ON/RTC_GUARDED_BY/RTC_DCHECK_RUN_ON macros allows to annotate
// variables are accessed from same thread/task queue.
// Using tools designed to check mutexes, it checks at compile time everywhere
// variable is access, there is a run-time dcheck thread/task queue is correct.
//
// class ExampleThread {
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
// class ExampleThreadChecker {
//  public:
//   int CalledFromPacer() RTC_RUN_ON(pacer_thread_checker_) {
//     return var2_;
//   }
//
//   void CallMeFromPacer() {
//     RTC_DCHECK_RUN_ON(&pacer_thread_checker_)
//        << "Should be called from pacer";
//     CalledFromPacer();
//   }
//
//  private:
//   int pacer_var_ RTC_GUARDED_BY(pacer_thread_checker_);
//   rtc::ThreadChecker pacer_thread_checker_;
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
//     rtc::scoped_ref_ptr<Encoder> encoder = encoder_;
//     encoder_->Queue()->PostTask([encoder] { encoder->Encode(); });
//   }
//
//  private:
//   rtc::scoped_ref_ptr<Encoder> encoder_;
// }

// Document if a function expected to be called from same thread/task queue.
#define RTC_RUN_ON(x) \
  RTC_THREAD_ANNOTATION_ATTRIBUTE__(exclusive_locks_required(x))

#define RTC_DCHECK_RUN_ON(thread_like_object)                           \
  rtc::internal::AnnounceOnThread thread_announcer(thread_like_object); \
  RTC_DCHECK(rtc::internal::AnnounceOnThread::IsCurrent(thread_like_object))

#endif  // RTC_BASE_THREAD_CHECKER_H_
