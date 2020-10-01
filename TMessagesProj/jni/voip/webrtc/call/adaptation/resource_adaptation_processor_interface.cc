/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/adaptation/resource_adaptation_processor_interface.h"

namespace webrtc {

ResourceAdaptationProcessorInterface::~ResourceAdaptationProcessorInterface() =
    default;

ResourceLimitationsListener::~ResourceLimitationsListener() = default;

}  // namespace webrtc
