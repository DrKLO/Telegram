/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_UTILITY_INCLUDE_PROCESS_THREAD_H_
#define MODULES_UTILITY_INCLUDE_PROCESS_THREAD_H_

#include <memory>

#include "api/task_queue/queued_task.h"
#include "api/task_queue/task_queue_base.h"

namespace rtc {
class Location;
}

namespace webrtc {
class Module;

// TODO(tommi): ProcessThread probably doesn't need to be a virtual
// interface.  There exists one override besides ProcessThreadImpl,
// MockProcessThread, but when looking at how it is used, it seems
// a nullptr might suffice (or simply an actual ProcessThread instance).
class ProcessThread : public TaskQueueBase {
 public:
  ~ProcessThread() override;

  static std::unique_ptr<ProcessThread> Create(const char* thread_name);

  // Starts the worker thread.  Must be called from the construction thread.
  virtual void Start() = 0;

  // Stops the worker thread.  Must be called from the construction thread.
  virtual void Stop() = 0;

  // Wakes the thread up to give a module a chance to do processing right
  // away.  This causes the worker thread to wake up and requery the specified
  // module for when it should be called back. (Typically the module should
  // return 0 from TimeUntilNextProcess on the worker thread at that point).
  // Can be called on any thread.
  virtual void WakeUp(Module* module) = 0;

  // Adds a module that will start to receive callbacks on the worker thread.
  // Can be called from any thread.
  virtual void RegisterModule(Module* module, const rtc::Location& from) = 0;

  // Removes a previously registered module.
  // Can be called from any thread.
  virtual void DeRegisterModule(Module* module) = 0;
};

}  // namespace webrtc

#endif  // MODULES_UTILITY_INCLUDE_PROCESS_THREAD_H_
