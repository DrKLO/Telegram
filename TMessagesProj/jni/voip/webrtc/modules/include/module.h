/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_INCLUDE_MODULE_H_
#define MODULES_INCLUDE_MODULE_H_

#include <stdint.h>

namespace webrtc {

class ProcessThread;

class Module {
 public:
  // Returns the number of milliseconds until the module wants a worker
  // thread to call Process.
  // This method is called on the same worker thread as Process will
  // be called on.
  // TODO(tommi): Almost all implementations of this function, need to know
  // the current tick count.  Consider passing it as an argument.  It could
  // also improve the accuracy of when the next callback occurs since the
  // thread that calls Process() will also have it's tick count reference
  // which might not match with what the implementations use.
  virtual int64_t TimeUntilNextProcess() = 0;

  // Process any pending tasks such as timeouts.
  // Called on a worker thread.
  virtual void Process() = 0;

  // This method is called when the module is attached to a *running* process
  // thread or detached from one.  In the case of detaching, `process_thread`
  // will be nullptr.
  //
  // This method will be called in the following cases:
  //
  // * Non-null process_thread:
  //   * ProcessThread::RegisterModule() is called while the thread is running.
  //   * ProcessThread::Start() is called and RegisterModule has previously
  //     been called.  The thread will be started immediately after notifying
  //     all modules.
  //
  // * Null process_thread:
  //   * ProcessThread::DeRegisterModule() is called while the thread is
  //     running.
  //   * ProcessThread::Stop() was called and the thread has been stopped.
  //
  // NOTE: This method is not called from the worker thread itself, but from
  //       the thread that registers/deregisters the module or calls Start/Stop.
  virtual void ProcessThreadAttached(ProcessThread* process_thread) {}

 protected:
  virtual ~Module() {}
};
}  // namespace webrtc

#endif  // MODULES_INCLUDE_MODULE_H_
