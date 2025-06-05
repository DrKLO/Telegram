/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/screen_drawer_lock_posix.h"

#include <fcntl.h>
#include <sys/stat.h>

#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

// A uuid as the name of semaphore.
static constexpr char kSemaphoreName[] = "GSDL54fe5552804711e6a7253f429a";

}  // namespace

ScreenDrawerLockPosix::ScreenDrawerLockPosix()
    : ScreenDrawerLockPosix(kSemaphoreName) {}

ScreenDrawerLockPosix::ScreenDrawerLockPosix(const char* name) {
  semaphore_ = sem_open(name, O_CREAT, S_IRWXU | S_IRWXG | S_IRWXO, 1);
  if (semaphore_ == SEM_FAILED) {
    RTC_LOG_ERRNO(LS_ERROR) << "Failed to create named semaphore with " << name;
    RTC_DCHECK_NOTREACHED();
  }

  sem_wait(semaphore_);
}

ScreenDrawerLockPosix::~ScreenDrawerLockPosix() {
  if (semaphore_ == SEM_FAILED) {
    return;
  }

  sem_post(semaphore_);
  sem_close(semaphore_);
  // sem_unlink a named semaphore won't wait until other clients to release the
  // sem_t. So if a new process starts, it will sem_open a different kernel
  // object with the same name and eventually breaks the cross-process lock.
}

// static
void ScreenDrawerLockPosix::Unlink(absl::string_view name) {
  sem_unlink(std::string(name).c_str());
}

}  // namespace webrtc
