/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/audio_receive_stream.h"

namespace webrtc {

AudioReceiveStreamInterface::Stats::Stats() = default;
AudioReceiveStreamInterface::Stats::~Stats() = default;

AudioReceiveStreamInterface::Config::Config() = default;
AudioReceiveStreamInterface::Config::~Config() = default;

AudioReceiveStreamInterface::Config::Rtp::Rtp() = default;
AudioReceiveStreamInterface::Config::Rtp::~Rtp() = default;

}  // namespace webrtc
