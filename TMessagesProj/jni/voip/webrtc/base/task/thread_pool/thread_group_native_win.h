// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_THREAD_GROUP_NATIVE_WIN_H_
#define BASE_TASK_THREAD_POOL_THREAD_GROUP_NATIVE_WIN_H_

#include <windows.h>

#include "base/base_export.h"
#include "base/task/thread_pool/thread_group_native.h"

namespace base {
namespace internal {

// A ThreadGroup implementation backed by the Windows Thread Pool API.
//
// Windows Thread Pool API official documentation:
// https://msdn.microsoft.com/en-us/library/windows/desktop/ms686766(v=vs.85).aspx
//
// Blog posts on the Windows Thread Pool API:
// https://msdn.microsoft.com/magazine/hh335066.aspx
// https://msdn.microsoft.com/magazine/hh394144.aspx
// https://msdn.microsoft.com/magazine/hh456398.aspx
// https://msdn.microsoft.com/magazine/hh547107.aspx
// https://msdn.microsoft.com/magazine/hh580731.aspx
class BASE_EXPORT ThreadGroupNativeWin : public ThreadGroupNative {
 public:
  ThreadGroupNativeWin(TrackedRef<TaskTracker> task_tracker,
                       TrackedRef<Delegate> delegate,
                       ThreadGroup* predecessor_thread_group = nullptr);

  ~ThreadGroupNativeWin() override;

 private:
  class ScopedCallbackMayRunLongObserver;

  // Callback that gets run by |pool_|.
  static void CALLBACK
  RunNextTaskSource(PTP_CALLBACK_INSTANCE callback_instance,
                    void* thread_group_windows_impl,
                    PTP_WORK);

  // ThreadGroupNative:
  void JoinImpl() override;
  void StartImpl() override;
  void SubmitWork() override;

  // Thread pool object that |work_| gets executed on.
  PTP_POOL pool_ = nullptr;

  // Callback environment. |pool_| is associated with |environment_| so that
  // work objects using this environment run on |pool_|.
  TP_CALLBACK_ENVIRON environment_ = {};

  // Work object that executes RunNextTaskSource. It has a pointer to the
  // current |ThreadGroupNativeWin| and a pointer to |environment_| bound
  // to it.
  PTP_WORK work_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(ThreadGroupNativeWin);
};

using ThreadGroupNativeImpl = ThreadGroupNativeWin;

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_THREAD_GROUP_NATIVE_WIN_H_
