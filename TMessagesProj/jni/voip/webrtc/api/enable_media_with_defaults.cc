/*
 *  Copyright 2023 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/enable_media_with_defaults.h"

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/enable_media.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/video_codecs/builtin_video_decoder_factory.h"
#include "api/video_codecs/builtin_video_encoder_factory.h"
#include "modules/audio_processing/include/audio_processing.h"

namespace webrtc {

void EnableMediaWithDefaults(PeerConnectionFactoryDependencies& deps) {
  if (deps.task_queue_factory == nullptr) {
    deps.task_queue_factory = CreateDefaultTaskQueueFactory();
  }
  if (deps.audio_encoder_factory == nullptr) {
    deps.audio_encoder_factory = CreateBuiltinAudioEncoderFactory();
  }
  if (deps.audio_decoder_factory == nullptr) {
    deps.audio_decoder_factory = CreateBuiltinAudioDecoderFactory();
  }
  if (deps.audio_processing == nullptr) {
    deps.audio_processing = AudioProcessingBuilder().Create();
  }

  if (deps.video_encoder_factory == nullptr) {
    deps.video_encoder_factory = CreateBuiltinVideoEncoderFactory();
  }
  if (deps.video_decoder_factory == nullptr) {
    deps.video_decoder_factory = CreateBuiltinVideoDecoderFactory();
  }
  EnableMedia(deps);
}

}  // namespace webrtc
