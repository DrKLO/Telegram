/*
 *  Copyright 2023 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/enable_media.h"

#include <memory>
#include <utility>

#include "api/environment/environment.h"
#include "api/peer_connection_interface.h"
#include "call/create_call.h"
#include "media/engine/webrtc_media_engine.h"
#include "media/engine/webrtc_video_engine.h"
#include "media/engine/webrtc_voice_engine.h"
#include "pc/media_factory.h"

namespace webrtc {
namespace {

using ::cricket::CompositeMediaEngine;
using ::cricket::MediaEngineInterface;
using ::cricket::WebRtcVideoEngine;
using ::cricket::WebRtcVoiceEngine;

class MediaFactoryImpl : public MediaFactory {
 public:
  MediaFactoryImpl() = default;
  MediaFactoryImpl(const MediaFactoryImpl&) = delete;
  MediaFactoryImpl& operator=(const MediaFactoryImpl&) = delete;
  ~MediaFactoryImpl() override = default;

  std::unique_ptr<Call> CreateCall(const CallConfig& config) override {
    return webrtc::CreateCall(config);
  }

  std::unique_ptr<MediaEngineInterface> CreateMediaEngine(
      const Environment& env,
      PeerConnectionFactoryDependencies& deps) override {
    auto audio_engine = std::make_unique<WebRtcVoiceEngine>(
        &env.task_queue_factory(), deps.adm.get(),
        std::move(deps.audio_encoder_factory),
        std::move(deps.audio_decoder_factory), std::move(deps.audio_mixer),
        std::move(deps.audio_processing), std::move(deps.audio_frame_processor),
        env.field_trials());
    auto video_engine = std::make_unique<WebRtcVideoEngine>(
        std::move(deps.video_encoder_factory),
        std::move(deps.video_decoder_factory), env.field_trials());
    return std::make_unique<CompositeMediaEngine>(std::move(audio_engine),
                                                  std::move(video_engine));
  }
};

}  // namespace

void EnableMedia(PeerConnectionFactoryDependencies& deps) {
  if (deps.media_factory != nullptr) {
    // Do nothing if media is already enabled. Overwriting media_factory can be
    // harmful when a different (e.g. test-only) implementation is used.
    return;
  }
  deps.media_factory = std::make_unique<MediaFactoryImpl>();
}

}  // namespace webrtc
