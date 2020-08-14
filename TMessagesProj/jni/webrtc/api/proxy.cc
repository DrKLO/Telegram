/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/proxy.h"

namespace webrtc {
namespace internal {

SynchronousMethodCall::SynchronousMethodCall(rtc::MessageHandler* proxy)
    : proxy_(proxy) {}

SynchronousMethodCall::~SynchronousMethodCall() = default;

void SynchronousMethodCall::Invoke(const rtc::Location& posted_from,
                                   rtc::Thread* t) {
  if (t->IsCurrent()) {
    proxy_->OnMessage(nullptr);
  } else {
    t->Post(posted_from, this, 0);
    e_.Wait(rtc::Event::kForever);
  }
}

void SynchronousMethodCall::OnMessage(rtc::Message*) {
  proxy_->OnMessage(nullptr);
  e_.Set();
}

}  // namespace internal
}  // namespace webrtc
