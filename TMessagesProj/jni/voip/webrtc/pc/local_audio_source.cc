/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/local_audio_source.h"

using webrtc::MediaSourceInterface;

namespace webrtc {

rtc::scoped_refptr<LocalAudioSource> LocalAudioSource::Create(
    const cricket::AudioOptions* audio_options) {
  auto source = rtc::make_ref_counted<LocalAudioSource>();
  source->Initialize(audio_options);
  return source;
}

void LocalAudioSource::Initialize(const cricket::AudioOptions* audio_options) {
  if (!audio_options)
    return;

  options_ = *audio_options;
}

}  // namespace webrtc
