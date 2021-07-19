// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/message_loop/watchable_io_message_pump_posix.h"

namespace base {

WatchableIOMessagePumpPosix::FdWatchControllerInterface::
    FdWatchControllerInterface(const Location& from_here)
    : created_from_location_(from_here) {}

WatchableIOMessagePumpPosix::FdWatchControllerInterface::
    ~FdWatchControllerInterface() = default;

}  // namespace base
