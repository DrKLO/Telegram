// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/synchronization/waitable_event_watcher.h"

#include "base/compiler_specific.h"
#include "base/synchronization/waitable_event.h"
#include "base/win/object_watcher.h"

#include <windows.h>

namespace base {

WaitableEventWatcher::WaitableEventWatcher() = default;

WaitableEventWatcher::~WaitableEventWatcher() {}

bool WaitableEventWatcher::StartWatching(
    WaitableEvent* event,
    EventCallback callback,
    scoped_refptr<SequencedTaskRunner> task_runner) {
  DCHECK(event);
  callback_ = std::move(callback);
  event_ = event;

  // Duplicate and hold the event handle until a callback is returned or
  // waiting is stopped.
  HANDLE handle = nullptr;
  if (!::DuplicateHandle(::GetCurrentProcess(),  // hSourceProcessHandle
                         event->handle(),
                         ::GetCurrentProcess(),  // hTargetProcessHandle
                         &handle,
                         0,      // dwDesiredAccess ignored due to SAME_ACCESS
                         FALSE,  // !bInheritHandle
                         DUPLICATE_SAME_ACCESS)) {
    return false;
  }
  duplicated_event_handle_.Set(handle);
  return watcher_.StartWatchingOnce(handle, this);
}

void WaitableEventWatcher::StopWatching() {
  callback_.Reset();
  event_ = NULL;
  watcher_.StopWatching();
  duplicated_event_handle_.Close();
}

void WaitableEventWatcher::OnObjectSignaled(HANDLE h) {
  DCHECK_EQ(duplicated_event_handle_.Get(), h);
  WaitableEvent* event = event_;
  EventCallback callback = std::move(callback_);
  event_ = NULL;
  duplicated_event_handle_.Close();
  DCHECK(event);

  std::move(callback).Run(event);
}

}  // namespace base
