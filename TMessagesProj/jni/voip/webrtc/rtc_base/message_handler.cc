/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/message_handler.h"

#include "rtc_base/thread.h"

namespace rtc {

MessageHandlerAutoCleanup::MessageHandlerAutoCleanup() {}

MessageHandlerAutoCleanup::~MessageHandlerAutoCleanup() {
  // Note that even though this clears currently pending messages for the
  // message handler, it's still racy since it doesn't prevent threads that
  // might be in the process of posting new messages with would-be dangling
  // pointers.
  // This is related to the design of Message having a raw pointer.
  // We could consider whether it would be safer to require message handlers
  // to be reference counted (as some are).
  ThreadManager::Clear(this);
}

}  // namespace rtc
