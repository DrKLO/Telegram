/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/deprecated/signal_thread.h"

#include <memory>

#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/null_socket_server.h"
#include "rtc_base/socket_server.h"

namespace rtc {

///////////////////////////////////////////////////////////////////////////////
// SignalThread
///////////////////////////////////////////////////////////////////////////////

DEPRECATED_SignalThread::DEPRECATED_SignalThread()
    : main_(Thread::Current()), worker_(this), state_(kInit), refcount_(1) {
  main_->SignalQueueDestroyed.connect(
      this, &DEPRECATED_SignalThread::OnMainThreadDestroyed);
  worker_.SetName("SignalThread", this);
}

DEPRECATED_SignalThread::~DEPRECATED_SignalThread() {
  rtc::CritScope lock(&cs_);
  RTC_DCHECK(refcount_ == 0);
}

bool DEPRECATED_SignalThread::SetName(const std::string& name,
                                      const void* obj) {
  EnterExit ee(this);
  RTC_DCHECK(!destroy_called_);
  RTC_DCHECK(main_->IsCurrent());
  RTC_DCHECK(kInit == state_);
  return worker_.SetName(name, obj);
}

void DEPRECATED_SignalThread::Start() {
  EnterExit ee(this);
  RTC_DCHECK(!destroy_called_);
  RTC_DCHECK(main_->IsCurrent());
  if (kInit == state_ || kComplete == state_) {
    state_ = kRunning;
    OnWorkStart();
    worker_.Start();
  } else {
    RTC_NOTREACHED();
  }
}

void DEPRECATED_SignalThread::Destroy(bool wait) {
  EnterExit ee(this);
  // Sometimes the caller can't guarantee which thread will call Destroy, only
  // that it will be the last thing it does.
  // RTC_DCHECK(main_->IsCurrent());
  RTC_DCHECK(!destroy_called_);
  destroy_called_ = true;
  if ((kInit == state_) || (kComplete == state_)) {
    refcount_--;
  } else if (kRunning == state_ || kReleasing == state_) {
    state_ = kStopping;
    // OnWorkStop() must follow Quit(), so that when the thread wakes up due to
    // OWS(), ContinueWork() will return false.
    worker_.Quit();
    OnWorkStop();
    if (wait) {
      // Release the thread's lock so that it can return from ::Run.
      cs_.Leave();
      worker_.Stop();
      cs_.Enter();
      refcount_--;
    }
  } else {
    RTC_NOTREACHED();
  }
}

void DEPRECATED_SignalThread::Release() {
  EnterExit ee(this);
  RTC_DCHECK(!destroy_called_);
  RTC_DCHECK(main_->IsCurrent());
  if (kComplete == state_) {
    refcount_--;
  } else if (kRunning == state_) {
    state_ = kReleasing;
  } else {
    // if (kInit == state_) use Destroy()
    RTC_NOTREACHED();
  }
}

bool DEPRECATED_SignalThread::ContinueWork() {
  EnterExit ee(this);
  RTC_DCHECK(!destroy_called_);
  RTC_DCHECK(worker_.IsCurrent());
  return worker_.ProcessMessages(0);
}

void DEPRECATED_SignalThread::OnMessage(Message* msg) {
  EnterExit ee(this);
  if (ST_MSG_WORKER_DONE == msg->message_id) {
    RTC_DCHECK(main_->IsCurrent());
    OnWorkDone();
    bool do_delete = false;
    if (kRunning == state_) {
      state_ = kComplete;
    } else {
      do_delete = true;
    }
    if (kStopping != state_) {
      // Before signaling that the work is done, make sure that the worker
      // thread actually is done. We got here because DoWork() finished and
      // Run() posted the ST_MSG_WORKER_DONE message. This means the worker
      // thread is about to go away anyway, but sometimes it doesn't actually
      // finish before SignalWorkDone is processed, and for a reusable
      // SignalThread this makes an assert in thread.cc fire.
      //
      // Calling Stop() on the worker ensures that the OS thread that underlies
      // the worker will finish, and will be set to null, enabling us to call
      // Start() again.
      worker_.Stop();
      SignalWorkDone(this);
    }
    if (do_delete) {
      refcount_--;
    }
  }
}

DEPRECATED_SignalThread::Worker::Worker(DEPRECATED_SignalThread* parent)
    : Thread(std::make_unique<NullSocketServer>(), /*do_init=*/false),
      parent_(parent) {
  DoInit();
}

DEPRECATED_SignalThread::Worker::~Worker() {
  Stop();
}

void DEPRECATED_SignalThread::Worker::Run() {
  parent_->Run();
}

void DEPRECATED_SignalThread::Run() {
  DoWork();
  {
    EnterExit ee(this);
    if (main_) {
      main_->Post(RTC_FROM_HERE, this, ST_MSG_WORKER_DONE);
    }
  }
}

void DEPRECATED_SignalThread::OnMainThreadDestroyed() {
  EnterExit ee(this);
  main_ = nullptr;
}

bool DEPRECATED_SignalThread::Worker::IsProcessingMessagesForTesting() {
  return false;
}

}  // namespace rtc
