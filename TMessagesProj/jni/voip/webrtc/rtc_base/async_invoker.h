/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_INVOKER_H_
#define RTC_BASE_ASYNC_INVOKER_H_

#include <atomic>
#include <memory>
#include <utility>

#include "absl/base/attributes.h"
#include "api/scoped_refptr.h"
#include "rtc_base/async_invoker_inl.h"
#include "rtc_base/event.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"

namespace rtc {

// DEPRECATED - do not use.
//
// Invokes function objects (aka functors) asynchronously on a Thread, and
// owns the lifetime of calls (ie, when this object is destroyed, calls in
// flight are cancelled). AsyncInvoker can optionally execute a user-specified
// function when the asynchronous call is complete, or operates in
// fire-and-forget mode otherwise.
//
// AsyncInvoker does not own the thread it calls functors on.
//
// A note about async calls and object lifetimes: users should
// be mindful of object lifetimes when calling functions asynchronously and
// ensure objects used by the function _cannot_ be deleted between the
// invocation and execution of the functor. AsyncInvoker is designed to
// help: any calls in flight will be cancelled when the AsyncInvoker used to
// make the call is destructed, and any calls executing will be allowed to
// complete before AsyncInvoker destructs.
//
// The easiest way to ensure lifetimes are handled correctly is to create a
// class that owns the Thread and AsyncInvoker objects, and then call its
// methods asynchronously as needed.
//
// Example:
//   class MyClass {
//    public:
//     void FireAsyncTaskWithResult(Thread* thread, int x) {
//       // Specify a callback to get the result upon completion.
//       invoker_.AsyncInvoke<int>(RTC_FROM_HERE,
//           thread, Bind(&MyClass::AsyncTaskWithResult, this, x),
//           &MyClass::OnTaskComplete, this);
//     }
//     void FireAnotherAsyncTask(Thread* thread) {
//       // No callback specified means fire-and-forget.
//       invoker_.AsyncInvoke<void>(RTC_FROM_HERE,
//           thread, Bind(&MyClass::AnotherAsyncTask, this));
//
//    private:
//     int AsyncTaskWithResult(int x) {
//       // Some long running process...
//       return x * x;
//     }
//     void AnotherAsyncTask() {
//       // Some other long running process...
//     }
//     void OnTaskComplete(int result) { result_ = result; }
//
//     AsyncInvoker invoker_;
//     int result_;
//   };
//
// More details about threading:
// - It's safe to construct/destruct AsyncInvoker on different threads.
// - It's safe to call AsyncInvoke from different threads.
// - It's safe to call AsyncInvoke recursively from *within* a functor that's
//   being AsyncInvoked.
// - However, it's *not* safe to call AsyncInvoke from *outside* a functor
//   that's being AsyncInvoked while the AsyncInvoker is being destroyed on
//   another thread. This is just inherently unsafe and there's no way to
//   prevent that. So, the user of this class should ensure that the start of
//   each "chain" of invocations is synchronized somehow with the AsyncInvoker's
//   destruction. This can be done by starting each chain of invocations on the
//   same thread on which it will be destroyed, or by using some other
//   synchronization method.
class DEPRECATED_AsyncInvoker : public MessageHandlerAutoCleanup {
 public:
  DEPRECATED_AsyncInvoker();
  ~DEPRECATED_AsyncInvoker() override;

  DEPRECATED_AsyncInvoker(const DEPRECATED_AsyncInvoker&) = delete;
  DEPRECATED_AsyncInvoker& operator=(const DEPRECATED_AsyncInvoker&) = delete;

  // Call `functor` asynchronously on `thread`, with no callback upon
  // completion. Returns immediately.
  template <class ReturnT, class FunctorT>
  void AsyncInvoke(const Location& posted_from,
                   Thread* thread,
                   FunctorT&& functor,
                   uint32_t id = 0) {
    std::unique_ptr<AsyncClosure> closure(
        new FireAndForgetAsyncClosure<FunctorT>(
            this, std::forward<FunctorT>(functor)));
    DoInvoke(posted_from, thread, std::move(closure), id);
  }

  // Call `functor` asynchronously on `thread` with `delay_ms`, with no callback
  // upon completion. Returns immediately.
  template <class ReturnT, class FunctorT>
  void AsyncInvokeDelayed(const Location& posted_from,
                          Thread* thread,
                          FunctorT&& functor,
                          uint32_t delay_ms,
                          uint32_t id = 0) {
    std::unique_ptr<AsyncClosure> closure(
        new FireAndForgetAsyncClosure<FunctorT>(
            this, std::forward<FunctorT>(functor)));
    DoInvokeDelayed(posted_from, thread, std::move(closure), delay_ms, id);
  }

  // Cancels any outstanding calls we own that are pending on any thread, and
  // which have not yet started to execute. This does not wait for any calls
  // that have already started executing to complete.
  void Clear();

 private:
  void OnMessage(Message* msg) override;
  void DoInvoke(const Location& posted_from,
                Thread* thread,
                std::unique_ptr<AsyncClosure> closure,
                uint32_t id);
  void DoInvokeDelayed(const Location& posted_from,
                       Thread* thread,
                       std::unique_ptr<AsyncClosure> closure,
                       uint32_t delay_ms,
                       uint32_t id);

  // Used to keep track of how many invocations (AsyncClosures) are still
  // alive, so that the destructor can wait for them to finish, as described in
  // the class documentation.
  //
  // TODO(deadbeef): Using a raw std::atomic like this is prone to error and
  // difficult to maintain. We should try to wrap this functionality in a
  // separate class to reduce the chance of errors being introduced in the
  // future.
  std::atomic<int> pending_invocations_;

  // Reference counted so that if the destructor finishes before an
  // AsyncClosure's destructor that's about to call
  // "invocation_complete_->Set()", it's not dereferenced after being destroyed.
  rtc::Ref<Event>::Ptr invocation_complete_;

  // This flag is used to ensure that if an application AsyncInvokes tasks that
  // recursively AsyncInvoke other tasks ad infinitum, the cycle eventually
  // terminates.
  std::atomic<bool> destroying_;

  friend class AsyncClosure;
};

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_INVOKER_H_
