/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/create_peerconnection_factory.h"

#include <memory>
#include <utility>

#include "api/enable_media.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/scoped_refptr.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/transport/field_trial_based_config.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/thread.h"

namespace webrtc {

rtc::scoped_refptr<PeerConnectionFactoryInterface> CreatePeerConnectionFactory(
    rtc::Thread* network_thread,
    rtc::Thread* worker_thread,
    rtc::Thread* signaling_thread,
    rtc::scoped_refptr<AudioDeviceModule> default_adm,
    rtc::scoped_refptr<AudioEncoderFactory> audio_encoder_factory,
    rtc::scoped_refptr<AudioDecoderFactory> audio_decoder_factory,
    std::unique_ptr<VideoEncoderFactory> video_encoder_factory,
    std::unique_ptr<VideoDecoderFactory> video_decoder_factory,
    rtc::scoped_refptr<AudioMixer> audio_mixer,
    rtc::scoped_refptr<AudioProcessing> audio_processing,
    std::unique_ptr<AudioFrameProcessor> audio_frame_processor,
    std::unique_ptr<FieldTrialsView> field_trials) {
  if (!field_trials) {
    field_trials = std::make_unique<webrtc::FieldTrialBasedConfig>();
  }

  PeerConnectionFactoryDependencies dependencies;
  dependencies.network_thread = network_thread;
  dependencies.worker_thread = worker_thread;
  dependencies.signaling_thread = signaling_thread;
  dependencies.task_queue_factory =
      CreateDefaultTaskQueueFactory(field_trials.get());
  dependencies.event_log_factory = std::make_unique<RtcEventLogFactory>();
  dependencies.trials = std::move(field_trials);

  if (network_thread) {
    // TODO(bugs.webrtc.org/13145): Add an rtc::SocketFactory* argument.
    dependencies.socket_factory = network_thread->socketserver();
  }
  dependencies.adm = std::move(default_adm);
  dependencies.audio_encoder_factory = std::move(audio_encoder_factory);
  dependencies.audio_decoder_factory = std::move(audio_decoder_factory);
  dependencies.audio_frame_processor = std::move(audio_frame_processor);
  if (audio_processing) {
    dependencies.audio_processing = std::move(audio_processing);
  } else {
    dependencies.audio_processing = AudioProcessingBuilder().Create();
  }
  dependencies.audio_mixer = std::move(audio_mixer);
  dependencies.video_encoder_factory = std::move(video_encoder_factory);
  dependencies.video_decoder_factory = std::move(video_decoder_factory);
  EnableMedia(dependencies);

  return CreateModularPeerConnectionFactory(std::move(dependencies));
}

}  // namespace webrtc
