/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_CREATE_CALL_H_
#define CALL_CREATE_CALL_H_

#include <memory>

#include "call/call.h"
#include "call/call_config.h"

namespace webrtc {

std::unique_ptr<Call> CreateCall(const CallConfig& config);

}  // namespace webrtc

#endif  // CALL_CREATE_CALL_H_
