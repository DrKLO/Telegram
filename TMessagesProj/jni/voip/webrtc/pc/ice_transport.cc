/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/ice_transport.h"

#include "api/sequence_checker.h"

namespace webrtc {

IceTransportWithPointer::~IceTransportWithPointer() {
  // We depend on the networking thread to call Clear() before dropping
  // its last reference to this object; if the destructor is called
  // on the networking thread, it's OK to not have called Clear().
  if (internal_) {
    RTC_DCHECK_RUN_ON(creator_thread_);
  }
}

cricket::IceTransportInternal* IceTransportWithPointer::internal() {
  RTC_DCHECK_RUN_ON(creator_thread_);
  return internal_;
}

void IceTransportWithPointer::Clear() {
  RTC_DCHECK_RUN_ON(creator_thread_);
  internal_ = nullptr;
}

}  // namespace webrtc
