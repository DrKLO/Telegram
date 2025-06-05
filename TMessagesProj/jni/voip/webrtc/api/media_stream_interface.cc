/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/media_stream_interface.h"

#include "api/media_types.h"

namespace webrtc {

const char* const MediaStreamTrackInterface::kVideoKind =
    cricket::kMediaTypeVideo;
const char* const MediaStreamTrackInterface::kAudioKind =
    cricket::kMediaTypeAudio;

VideoTrackInterface::ContentHint VideoTrackInterface::content_hint() const {
  return ContentHint::kNone;
}

bool AudioTrackInterface::GetSignalLevel(int* level) {
  return false;
}

rtc::scoped_refptr<AudioProcessorInterface>
AudioTrackInterface::GetAudioProcessor() {
  return nullptr;
}

const cricket::AudioOptions AudioSourceInterface::options() const {
  return {};
}

}  // namespace webrtc
