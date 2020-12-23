/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_DEPRECATED_SIGNAL_THREAD_H_
#define RTC_BASE_DEPRECATED_SIGNAL_THREAD_H_

#include <string>

#include "rtc_base/checks.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/deprecation.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// NOTE: this class has been deprecated. Do not use for new code. New code
// should use factilities exposed by api/task_queue/ instead.
//
// SignalThread - Base class for worker threads.  The main thread should call
//  Start() to begin work, and then follow one of these models:
//   Normal: Wait for SignalWorkDone, and then call Release to destroy.
//   Cancellation: Call Release(true), to abort the worker thread.
//   Fire-and-forget: Call Release(false), which allows the thread to run to
//    completion, and then self-destruct without further notification.
//   Periodic tasks: Wait for SignalWorkDone, then eventually call Start()
//    again to repeat the task. When the instance isn't needed anymore,
//    call Release. DoWork, OnWorkStart and OnWorkStop are called again,
//    on a new thread.
//  The subclass should override DoWork() to perform the background task.  By
//   periodically calling ContinueWork(), it can check for cancellation.
//   OnWorkStart and OnWorkDone can be overridden to do pre- or post-work
//   tasks in the context of the main thread.
///////////////////////////////////////////////////////////////////////////////

class DEPRECATED_SignalThread : public sigslot::has_slots<>,
                                protected MessageHandlerAutoCleanup {
 public:
  DEPRECATED_SignalThread();

  // Context: Main Thread.  Call before Start to change the worker's name.
  bool SetName(const std::string& name, const void* obj);

  // Context: Main Thread.  Call to begin the worker thread.
  void Start();

  // Context: Main Thread.  If the worker thread is not running, deletes the
  // object immediately.  Otherwise, asks the worker thread to abort processing,
  // and schedules the object to be deleted once the worker exits.
  // SignalWorkDone will not be signalled.  If wait is true, does not return
  // until the thread is deleted.
  void Destroy(bool wait);

  // Context: Main Thread.  If the worker thread is complete, deletes the
  // object immediately.  Otherwise, schedules the object to be deleted once
  // the worker thread completes.  SignalWorkDone will be signalled.
  void Release();

  // Context: Main Thread.  Signalled when work is complete.
  sigslot::signal1<DEPRECATED_SignalThread*> SignalWorkDone;

  enum { ST_MSG_WORKER_DONE, ST_MSG_FIRST_AVAILABLE };

 protected:
  ~DEPRECATED_SignalThread() override;

  Thread* worker() { return &worker_; }

  // Context: Main Thread.  Subclass should override to do pre-work setup.
  virtual void OnWorkStart() {}

  // Context: Worker Thread.  Subclass should override to do work.
  virtual void DoWork() = 0;

  // Context: Worker Thread.  Subclass should call periodically to
  // dispatch messages and determine if the thread should terminate.
  bool ContinueWork();

  // Context: Worker Thread.  Subclass should override when extra work is
  // needed to abort the worker thread.
  virtual void OnWorkStop() {}

  // Context: Main Thread.  Subclass should override to do post-work cleanup.
  virtual void OnWorkDone() {}

  // Context: Any Thread.  If subclass overrides, be sure to call the base
  // implementation.  Do not use (message_id < ST_MSG_FIRST_AVAILABLE)
  void OnMessage(Message* msg) override;

 private:
  enum State {
    kInit,       // Initialized, but not started
    kRunning,    // Started and doing work
    kReleasing,  // Same as running, but to be deleted when work is done
    kComplete,   // Work is done
    kStopping,   // Work is being interrupted
  };

  class Worker : public Thread {
   public:
    explicit Worker(DEPRECATED_SignalThread* parent);

    Worker() = delete;
    Worker(const Worker&) = delete;
    Worker& operator=(const Worker&) = delete;

    ~Worker() override;
    void Run() override;
    bool IsProcessingMessagesForTesting() override;

   private:
    DEPRECATED_SignalThread* parent_;
  };

  class RTC_SCOPED_LOCKABLE EnterExit {
   public:
    explicit EnterExit(DEPRECATED_SignalThread* t)
        RTC_EXCLUSIVE_LOCK_FUNCTION(t->cs_)
        : t_(t) {
      t_->cs_.Enter();
      // If refcount_ is zero then the object has already been deleted and we
      // will be double-deleting it in ~EnterExit()! (shouldn't happen)
      RTC_DCHECK_NE(0, t_->refcount_);
      ++t_->refcount_;
    }

    EnterExit() = delete;
    EnterExit(const EnterExit&) = delete;
    EnterExit& operator=(const EnterExit&) = delete;

    ~EnterExit() RTC_UNLOCK_FUNCTION() {
      bool d = (0 == --t_->refcount_);
      t_->cs_.Leave();
      if (d)
        delete t_;
    }

   private:
    DEPRECATED_SignalThread* t_;
  };

  void Run();
  void OnMainThreadDestroyed();

  Thread* main_;
  Worker worker_;
  RecursiveCriticalSection cs_;
  State state_ RTC_GUARDED_BY(cs_);
  int refcount_ RTC_GUARDED_BY(cs_);
  bool destroy_called_ RTC_GUARDED_BY(cs_) = false;

  RTC_DISALLOW_COPY_AND_ASSIGN(DEPRECATED_SignalThread);
};

typedef RTC_DEPRECATED DEPRECATED_SignalThread SignalThread;

///////////////////////////////////////////////////////////////////////////////

}  // namespace rtc

#endif  // RTC_BASE_DEPRECATED_SIGNAL_THREAD_H_
