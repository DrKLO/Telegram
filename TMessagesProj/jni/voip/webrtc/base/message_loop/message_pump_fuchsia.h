// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MESSAGE_LOOP_MESSAGE_PUMP_FUCHSIA_H_
#define BASE_MESSAGE_LOOP_MESSAGE_PUMP_FUCHSIA_H_

#include <lib/async/wait.h>

#include "base/base_export.h"
#include "base/location.h"
#include "base/macros.h"
#include "base/memory/weak_ptr.h"
#include "base/message_loop/message_pump.h"
#include "base/message_loop/watchable_io_message_pump_posix.h"

typedef struct fdio fdio_t;

namespace async {
class Loop;
}  // namespace async

namespace base {

class BASE_EXPORT MessagePumpFuchsia : public MessagePump,
                                       public WatchableIOMessagePumpPosix {
 public:
  // Implemented by callers to receive notifications of handle & fd events.
  class ZxHandleWatcher {
   public:
    virtual void OnZxHandleSignalled(zx_handle_t handle,
                                     zx_signals_t signals) = 0;

   protected:
    virtual ~ZxHandleWatcher() {}
  };

  // Manages an active watch on an zx_handle_t.
  class ZxHandleWatchController : public async_wait_t {
   public:
    explicit ZxHandleWatchController(const Location& from_here);
    // Deleting the Controller implicitly calls StopWatchingZxHandle.
    virtual ~ZxHandleWatchController();

    // Stop watching the handle, always safe to call.  No-op if there's nothing
    // to do.
    bool StopWatchingZxHandle();

    const Location& created_from_location() { return created_from_location_; }

   protected:
    friend class MessagePumpFuchsia;

    virtual bool WaitBegin();

    static void HandleSignal(async_dispatcher_t* async,
                             async_wait_t* wait,
                             zx_status_t status,
                             const zx_packet_signal_t* signal);

    const Location created_from_location_;

    // This bool is used by the pump when invoking the ZxHandleWatcher callback,
    // and by the FdHandleWatchController when invoking read & write callbacks,
    // to cope with the possibility of the caller deleting the *Watcher within
    // the callback. The pump sets |was_stopped_| to a location on the stack,
    // and the Watcher writes to it, if set, when deleted, allowing the pump
    // to check the value on the stack to short-cut any post-callback work.
    bool* was_stopped_ = nullptr;

    // Set directly from the inputs to WatchFileDescriptor.
    ZxHandleWatcher* watcher_ = nullptr;

    // Used to safely access resources owned by the associated message pump.
    WeakPtr<MessagePumpFuchsia> weak_pump_;

    // A watch may be marked as persistent, which means it remains active even
    // after triggering.
    bool persistent_ = false;

    DISALLOW_COPY_AND_ASSIGN(ZxHandleWatchController);
  };

  class FdWatchController : public FdWatchControllerInterface,
                            public ZxHandleWatchController,
                            public ZxHandleWatcher {
   public:
    explicit FdWatchController(const Location& from_here);
    ~FdWatchController() override;

    // FdWatchControllerInterface:
    bool StopWatchingFileDescriptor() override;

   private:
    friend class MessagePumpFuchsia;

    // Determines the desires signals, and begins waiting on the handle.
    bool WaitBegin() override;

    // ZxHandleWatcher interface.
    void OnZxHandleSignalled(zx_handle_t handle, zx_signals_t signals) override;

    // Set directly from the inputs to WatchFileDescriptor.
    FdWatcher* watcher_ = nullptr;
    int fd_ = -1;
    uint32_t desired_events_ = 0;

    // Set by WatchFileDescriptor to hold a reference to the descriptor's mxio.
    fdio_t* io_ = nullptr;

    DISALLOW_COPY_AND_ASSIGN(FdWatchController);
  };

  enum Mode {
    WATCH_READ = 1 << 0,
    WATCH_WRITE = 1 << 1,
    WATCH_READ_WRITE = WATCH_READ | WATCH_WRITE
  };

  MessagePumpFuchsia();
  ~MessagePumpFuchsia() override;

  bool WatchZxHandle(zx_handle_t handle,
                     bool persistent,
                     zx_signals_t signals,
                     ZxHandleWatchController* controller,
                     ZxHandleWatcher* delegate);
  bool WatchFileDescriptor(int fd,
                           bool persistent,
                           int mode,
                           FdWatchController* controller,
                           FdWatcher* delegate);

  // MessagePump implementation:
  void Run(Delegate* delegate) override;
  void Quit() override;
  void ScheduleWork() override;
  void ScheduleDelayedWork(const TimeTicks& delayed_work_time) override;

 private:
  // Handles IO events by running |async_dispatcher_| until |deadline|. Returns
  // true if any events were received or if ScheduleWork() was called.
  bool HandleIoEventsUntil(zx_time_t deadline);

  // This flag is set to false when Run should return.
  bool keep_running_ = true;

  std::unique_ptr<async::Loop> async_loop_;

  base::WeakPtrFactory<MessagePumpFuchsia> weak_factory_;

  DISALLOW_COPY_AND_ASSIGN(MessagePumpFuchsia);
};

}  // namespace base

#endif  // BASE_MESSAGE_LOOP_MESSAGE_PUMP_FUCHSIA_H_
