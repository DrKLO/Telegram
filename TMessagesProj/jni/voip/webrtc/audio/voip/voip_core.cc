/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "audio/voip/voip_core.h"

#include <algorithm>
#include <memory>
#include <utility>

#include "api/audio_codecs/audio_format.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace {

// For Windows, use specific enum type to initialize default audio device as
// defined in AudioDeviceModule::WindowsDeviceType.
#if defined(WEBRTC_WIN)
constexpr AudioDeviceModule::WindowsDeviceType kAudioDeviceId =
    AudioDeviceModule::WindowsDeviceType::kDefaultCommunicationDevice;
#else
constexpr uint16_t kAudioDeviceId = 0;
#endif  // defined(WEBRTC_WIN)

// Maximum value range limit on ChannelId. This can be increased without any
// side effect and only set at this moderate value for better readability for
// logging.
static constexpr int kMaxChannelId = 100000;

}  // namespace

VoipCore::VoipCore(rtc::scoped_refptr<AudioEncoderFactory> encoder_factory,
                   rtc::scoped_refptr<AudioDecoderFactory> decoder_factory,
                   std::unique_ptr<TaskQueueFactory> task_queue_factory,
                   rtc::scoped_refptr<AudioDeviceModule> audio_device_module,
                   rtc::scoped_refptr<AudioProcessing> audio_processing,
                   std::unique_ptr<ProcessThread> process_thread) {
  encoder_factory_ = std::move(encoder_factory);
  decoder_factory_ = std::move(decoder_factory);
  task_queue_factory_ = std::move(task_queue_factory);
  audio_device_module_ = std::move(audio_device_module);
  audio_processing_ = std::move(audio_processing);
  process_thread_ = std::move(process_thread);

  if (!process_thread_) {
    process_thread_ = ProcessThread::Create("ModuleProcessThread");
  }
  audio_mixer_ = AudioMixerImpl::Create();

  // AudioTransportImpl depends on audio mixer and audio processing instances.
  audio_transport_ = std::make_unique<AudioTransportImpl>(
      audio_mixer_.get(), audio_processing_.get(), nullptr);
}

bool VoipCore::InitializeIfNeeded() {
  // |audio_device_module_| internally owns a lock and the whole logic here
  // needs to be executed atomically once using another lock in VoipCore.
  // Further changes in this method will need to make sure that no deadlock is
  // introduced in the future.
  MutexLock lock(&lock_);

  if (initialized_) {
    return true;
  }

  // Initialize ADM.
  if (audio_device_module_->Init() != 0) {
    RTC_LOG(LS_ERROR) << "Failed to initialize the ADM.";
    return false;
  }

  // Note that failures on initializing default recording/speaker devices are
  // not considered to be fatal here. In certain case, caller may not care about
  // recording device functioning (e.g webinar where only speaker is available).
  // It's also possible that there are other audio devices available that may
  // work.

  // Initialize default speaker device.
  if (audio_device_module_->SetPlayoutDevice(kAudioDeviceId) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to set playout device.";
  }
  if (audio_device_module_->InitSpeaker() != 0) {
    RTC_LOG(LS_WARNING) << "Unable to access speaker.";
  }

  // Initialize default recording device.
  if (audio_device_module_->SetRecordingDevice(kAudioDeviceId) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to set recording device.";
  }
  if (audio_device_module_->InitMicrophone() != 0) {
    RTC_LOG(LS_WARNING) << "Unable to access microphone.";
  }

  // Set number of channels on speaker device.
  bool available = false;
  if (audio_device_module_->StereoPlayoutIsAvailable(&available) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to query stereo playout.";
  }
  if (audio_device_module_->SetStereoPlayout(available) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to set mono/stereo playout mode.";
  }

  // Set number of channels on recording device.
  available = false;
  if (audio_device_module_->StereoRecordingIsAvailable(&available) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to query stereo recording.";
  }
  if (audio_device_module_->SetStereoRecording(available) != 0) {
    RTC_LOG(LS_WARNING) << "Unable to set stereo recording mode.";
  }

  if (audio_device_module_->RegisterAudioCallback(audio_transport_.get()) !=
      0) {
    RTC_LOG(LS_WARNING) << "Unable to register audio callback.";
  }

  initialized_ = true;

  return true;
}

ChannelId VoipCore::CreateChannel(Transport* transport,
                                  absl::optional<uint32_t> local_ssrc) {
  ChannelId channel_id;

  // Set local ssrc to random if not set by caller.
  if (!local_ssrc) {
    Random random(rtc::TimeMicros());
    local_ssrc = random.Rand<uint32_t>();
  }

  rtc::scoped_refptr<AudioChannel> channel =
      rtc::make_ref_counted<AudioChannel>(
          transport, local_ssrc.value(), task_queue_factory_.get(),
          process_thread_.get(), audio_mixer_.get(), decoder_factory_);

  // Check if we need to start the process thread.
  bool start_process_thread = false;

  {
    MutexLock lock(&lock_);

    // Start process thread if the channel is the first one.
    start_process_thread = channels_.empty();

    channel_id = static_cast<ChannelId>(next_channel_id_);
    channels_[channel_id] = channel;
    next_channel_id_++;
    if (next_channel_id_ >= kMaxChannelId) {
      next_channel_id_ = 0;
    }
  }

  // Set ChannelId in audio channel for logging/debugging purpose.
  channel->SetId(channel_id);

  if (start_process_thread) {
    process_thread_->Start();
  }

  return channel_id;
}

VoipResult VoipCore::ReleaseChannel(ChannelId channel_id) {
  // Destroy channel outside of the lock.
  rtc::scoped_refptr<AudioChannel> channel;

  bool no_channels_after_release = false;

  {
    MutexLock lock(&lock_);

    auto iter = channels_.find(channel_id);
    if (iter != channels_.end()) {
      channel = std::move(iter->second);
      channels_.erase(iter);
    }

    no_channels_after_release = channels_.empty();
  }

  VoipResult status_code = VoipResult::kOk;
  if (!channel) {
    RTC_LOG(LS_WARNING) << "Channel " << channel_id << " not found";
    status_code = VoipResult::kInvalidArgument;
  }

  if (no_channels_after_release) {
    // Release audio channel first to have it DeRegisterModule first.
    channel = nullptr;
    process_thread_->Stop();

    // Make sure to stop playout on ADM if it is playing.
    if (audio_device_module_->Playing()) {
      if (audio_device_module_->StopPlayout() != 0) {
        RTC_LOG(LS_WARNING) << "StopPlayout failed";
        status_code = VoipResult::kInternal;
      }
    }
  }

  return status_code;
}

rtc::scoped_refptr<AudioChannel> VoipCore::GetChannel(ChannelId channel_id) {
  rtc::scoped_refptr<AudioChannel> channel;
  {
    MutexLock lock(&lock_);
    auto iter = channels_.find(channel_id);
    if (iter != channels_.end()) {
      channel = iter->second;
    }
  }
  if (!channel) {
    RTC_LOG(LS_ERROR) << "Channel " << channel_id << " not found";
  }
  return channel;
}

bool VoipCore::UpdateAudioTransportWithSenders() {
  std::vector<AudioSender*> audio_senders;

  // Gather a list of audio channel that are currently sending along with
  // highest sampling rate and channel numbers to configure into audio
  // transport.
  int max_sampling_rate = 8000;
  size_t max_num_channels = 1;
  {
    MutexLock lock(&lock_);
    // Reserve to prevent run time vector re-allocation.
    audio_senders.reserve(channels_.size());
    for (auto kv : channels_) {
      rtc::scoped_refptr<AudioChannel>& channel = kv.second;
      if (channel->IsSendingMedia()) {
        auto encoder_format = channel->GetEncoderFormat();
        if (!encoder_format) {
          RTC_LOG(LS_ERROR)
              << "channel " << channel->GetId() << " encoder is not set";
          continue;
        }
        audio_senders.push_back(channel->GetAudioSender());
        max_sampling_rate =
            std::max(max_sampling_rate, encoder_format->clockrate_hz);
        max_num_channels =
            std::max(max_num_channels, encoder_format->num_channels);
      }
    }
  }

  audio_transport_->UpdateAudioSenders(audio_senders, max_sampling_rate,
                                       max_num_channels);

  // Depending on availability of senders, turn on or off ADM recording.
  if (!audio_senders.empty()) {
    // Initialize audio device module and default device if needed.
    if (!InitializeIfNeeded()) {
      return false;
    }

    if (!audio_device_module_->Recording()) {
      if (audio_device_module_->InitRecording() != 0) {
        RTC_LOG(LS_ERROR) << "InitRecording failed";
        return false;
      }
      if (audio_device_module_->StartRecording() != 0) {
        RTC_LOG(LS_ERROR) << "StartRecording failed";
        return false;
      }
    }
  } else {
    if (audio_device_module_->Recording() &&
        audio_device_module_->StopRecording() != 0) {
      RTC_LOG(LS_ERROR) << "StopRecording failed";
      return false;
    }
  }
  return true;
}

VoipResult VoipCore::StartSend(ChannelId channel_id) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  if (!channel->StartSend()) {
    return VoipResult::kFailedPrecondition;
  }

  return UpdateAudioTransportWithSenders() ? VoipResult::kOk
                                           : VoipResult::kInternal;
}

VoipResult VoipCore::StopSend(ChannelId channel_id) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->StopSend();

  return UpdateAudioTransportWithSenders() ? VoipResult::kOk
                                           : VoipResult::kInternal;
}

VoipResult VoipCore::StartPlayout(ChannelId channel_id) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  if (channel->IsPlaying()) {
    return VoipResult::kOk;
  }

  if (!channel->StartPlay()) {
    return VoipResult::kFailedPrecondition;
  }

  // Initialize audio device module and default device if needed.
  if (!InitializeIfNeeded()) {
    return VoipResult::kInternal;
  }

  if (!audio_device_module_->Playing()) {
    if (audio_device_module_->InitPlayout() != 0) {
      RTC_LOG(LS_ERROR) << "InitPlayout failed";
      return VoipResult::kInternal;
    }
    if (audio_device_module_->StartPlayout() != 0) {
      RTC_LOG(LS_ERROR) << "StartPlayout failed";
      return VoipResult::kInternal;
    }
  }

  return VoipResult::kOk;
}

VoipResult VoipCore::StopPlayout(ChannelId channel_id) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->StopPlay();

  return VoipResult::kOk;
}

VoipResult VoipCore::ReceivedRTPPacket(
    ChannelId channel_id,
    rtc::ArrayView<const uint8_t> rtp_packet) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->ReceivedRTPPacket(rtp_packet);

  return VoipResult::kOk;
}

VoipResult VoipCore::ReceivedRTCPPacket(
    ChannelId channel_id,
    rtc::ArrayView<const uint8_t> rtcp_packet) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->ReceivedRTCPPacket(rtcp_packet);

  return VoipResult::kOk;
}

VoipResult VoipCore::SetSendCodec(ChannelId channel_id,
                                  int payload_type,
                                  const SdpAudioFormat& encoder_format) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  auto encoder = encoder_factory_->MakeAudioEncoder(
      payload_type, encoder_format, absl::nullopt);
  channel->SetEncoder(payload_type, encoder_format, std::move(encoder));

  return VoipResult::kOk;
}

VoipResult VoipCore::SetReceiveCodecs(
    ChannelId channel_id,
    const std::map<int, SdpAudioFormat>& decoder_specs) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->SetReceiveCodecs(decoder_specs);

  return VoipResult::kOk;
}

VoipResult VoipCore::RegisterTelephoneEventType(ChannelId channel_id,
                                                int rtp_payload_type,
                                                int sample_rate_hz) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->RegisterTelephoneEventType(rtp_payload_type, sample_rate_hz);

  return VoipResult::kOk;
}

VoipResult VoipCore::SendDtmfEvent(ChannelId channel_id,
                                   DtmfEvent dtmf_event,
                                   int duration_ms) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  return (channel->SendTelephoneEvent(static_cast<int>(dtmf_event), duration_ms)
              ? VoipResult::kOk
              : VoipResult::kFailedPrecondition);
}

VoipResult VoipCore::GetIngressStatistics(ChannelId channel_id,
                                          IngressStatistics& ingress_stats) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  ingress_stats = channel->GetIngressStatistics();

  return VoipResult::kOk;
}

VoipResult VoipCore::GetChannelStatistics(ChannelId channel_id,
                                          ChannelStatistics& channel_stats) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel_stats = channel->GetChannelStatistics();

  return VoipResult::kOk;
}

VoipResult VoipCore::SetInputMuted(ChannelId channel_id, bool enable) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  channel->SetMute(enable);

  return VoipResult::kOk;
}

VoipResult VoipCore::GetInputVolumeInfo(ChannelId channel_id,
                                        VolumeInfo& input_volume) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  input_volume.audio_level = channel->GetInputAudioLevel();
  input_volume.total_energy = channel->GetInputTotalEnergy();
  input_volume.total_duration = channel->GetInputTotalDuration();

  return VoipResult::kOk;
}

VoipResult VoipCore::GetOutputVolumeInfo(ChannelId channel_id,
                                         VolumeInfo& output_volume) {
  rtc::scoped_refptr<AudioChannel> channel = GetChannel(channel_id);

  if (!channel) {
    return VoipResult::kInvalidArgument;
  }

  output_volume.audio_level = channel->GetOutputAudioLevel();
  output_volume.total_energy = channel->GetOutputTotalEnergy();
  output_volume.total_duration = channel->GetOutputTotalDuration();

  return VoipResult::kOk;
}

}  // namespace webrtc
