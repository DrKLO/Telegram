// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_MESSAGE_LOOP_MESSAGE_PUMP_ANDROID_H_
#define BASE_MESSAGE_LOOP_MESSAGE_PUMP_ANDROID_H_

#include <jni.h>
#include <memory>

#include "base/android/scoped_java_ref.h"
#include "base/base_export.h"
#include "base/callback.h"
#include "base/compiler_specific.h"
#include "base/macros.h"
#include "base/message_loop/message_pump.h"
#include "base/optional.h"
#include "base/time/time.h"

struct ALooper;

namespace base {

class RunLoop;

// This class implements a MessagePump needed for TYPE_UI MessageLoops on
// OS_ANDROID platform.
class BASE_EXPORT MessagePumpForUI : public MessagePump {
 public:
  MessagePumpForUI();
  ~MessagePumpForUI() override;

  void Run(Delegate* delegate) override;
  void Quit() override;
  void ScheduleWork() override;
  void ScheduleDelayedWork(const TimeTicks& delayed_work_time) override;

  // Attaches |delegate| to this native MessagePump. |delegate| will from then
  // on be invoked by the native loop to process application tasks.
  virtual void Attach(Delegate* delegate);

  // We call Abort when there is a pending JNI exception, meaning that the
  // current thread will crash when we return to Java.
  // We can't call any JNI-methods before returning to Java as we would then
  // cause a native crash (instead of the original Java crash).
  void Abort() { should_abort_ = true; }
  bool IsAborted() { return should_abort_; }
  bool ShouldQuit() const { return should_abort_ || quit_; }

  // Tells the RunLoop to quit when idle, calling the callback when it's safe
  // for the Thread to stop.
  void QuitWhenIdle(base::OnceClosure callback);

  // These functions are only public so that the looper callbacks can call them,
  // and should not be called from outside this class.
  void OnDelayedLooperCallback();
  void OnNonDelayedLooperCallback();

 protected:
  void SetDelegate(Delegate* delegate) { delegate_ = delegate; }
  virtual bool IsTestImplementation() const;

 private:
  void DoIdleWork();

  // Unlike other platforms, we don't control the message loop as it's
  // controlled by the Android Looper, so we can't run a RunLoop to keep the
  // Thread this pump belongs to alive. However, threads are expected to have an
  // active run loop, so we manage a RunLoop internally here, starting/stopping
  // it as necessary.
  std::unique_ptr<RunLoop> run_loop_;

  // See Abort().
  bool should_abort_ = false;

  // Whether this message pump is quitting, or has quit.
  bool quit_ = false;

  // The MessageLoop::Delegate for this pump.
  Delegate* delegate_ = nullptr;

  // The time at which we are currently scheduled to wake up and perform a
  // delayed task. This avoids redundantly scheduling |delayed_fd_| with the
  // same timeout when subsequent work phases all go idle on the same pending
  // delayed task; nullopt if no wakeup is currently scheduled.
  Optional<TimeTicks> delayed_scheduled_time_;

  // If set, a callback to fire when the message pump is quit.
  base::OnceClosure on_quit_callback_;

  // The file descriptor used to signal that non-delayed work is available.
  int non_delayed_fd_;

  // The file descriptor used to signal that delayed work is available.
  int delayed_fd_;

  // The Android Looper for this thread.
  ALooper* looper_ = nullptr;

  // The JNIEnv* for this thread, used to check for pending exceptions.
  JNIEnv* env_;

  DISALLOW_COPY_AND_ASSIGN(MessagePumpForUI);
};

}  // namespace base

#endif  // BASE_MESSAGE_LOOP_MESSAGE_PUMP_ANDROID_H_
