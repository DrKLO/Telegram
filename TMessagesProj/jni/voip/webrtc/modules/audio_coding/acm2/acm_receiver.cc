/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/acm2/acm_receiver.h"

#include <stdlib.h>
#include <string.h>

#include <cstdint>
#include <vector>

#include "absl/strings/match.h"
#include "api/audio/audio_frame.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/neteq/neteq.h"
#include "modules/audio_coding/acm2/acm_resampler.h"
#include "modules/audio_coding/acm2/call_statistics.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/strings/audio_format_to_string.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

namespace acm2 {

namespace {

std::unique_ptr<NetEq> CreateNetEq(
    NetEqFactory* neteq_factory,
    const NetEq::Config& config,
    Clock* clock,
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory) {
  if (neteq_factory) {
    return neteq_factory->CreateNetEq(config, decoder_factory, clock);
  }
  return DefaultNetEqFactory().CreateNetEq(config, decoder_factory, clock);
}

}  // namespace

AcmReceiver::AcmReceiver(const AudioCodingModule::Config& config)
    : last_audio_buffer_(new int16_t[AudioFrame::kMaxDataSizeSamples]),
      neteq_(CreateNetEq(config.neteq_factory,
                         config.neteq_config,
                         config.clock,
                         config.decoder_factory)),
      clock_(config.clock),
      resampled_last_output_frame_(true) {
  RTC_DCHECK(clock_);
  memset(last_audio_buffer_.get(), 0,
         sizeof(int16_t) * AudioFrame::kMaxDataSizeSamples);
}

AcmReceiver::~AcmReceiver() = default;

int AcmReceiver::SetMinimumDelay(int delay_ms) {
  if (neteq_->SetMinimumDelay(delay_ms))
    return 0;
  RTC_LOG(LERROR) << "AcmReceiver::SetExtraDelay " << delay_ms;
  return -1;
}

int AcmReceiver::SetMaximumDelay(int delay_ms) {
  if (neteq_->SetMaximumDelay(delay_ms))
    return 0;
  RTC_LOG(LERROR) << "AcmReceiver::SetExtraDelay " << delay_ms;
  return -1;
}

bool AcmReceiver::SetBaseMinimumDelayMs(int delay_ms) {
  return neteq_->SetBaseMinimumDelayMs(delay_ms);
}

int AcmReceiver::GetBaseMinimumDelayMs() const {
  return neteq_->GetBaseMinimumDelayMs();
}

absl::optional<int> AcmReceiver::last_packet_sample_rate_hz() const {
  MutexLock lock(&mutex_);
  if (!last_decoder_) {
    return absl::nullopt;
  }
  return last_decoder_->sample_rate_hz;
}

int AcmReceiver::last_output_sample_rate_hz() const {
  return neteq_->last_output_sample_rate_hz();
}

int AcmReceiver::InsertPacket(const RTPHeader& rtp_header,
                              rtc::ArrayView<const uint8_t> incoming_payload) {
  if (incoming_payload.empty()) {
    neteq_->InsertEmptyPacket(rtp_header);
    return 0;
  }

  int payload_type = rtp_header.payloadType;
  auto format = neteq_->GetDecoderFormat(payload_type);
  if (format && absl::EqualsIgnoreCase(format->sdp_format.name, "red")) {
    // This is a RED packet. Get the format of the audio codec.
    payload_type = incoming_payload[0] & 0x7f;
    format = neteq_->GetDecoderFormat(payload_type);
  }
  if (!format) {
    RTC_LOG_F(LS_ERROR) << "Payload-type " << payload_type
                        << " is not registered.";
    return -1;
  }

  {
    MutexLock lock(&mutex_);
    if (absl::EqualsIgnoreCase(format->sdp_format.name, "cn")) {
      if (last_decoder_ && last_decoder_->num_channels > 1) {
        // This is a CNG and the audio codec is not mono, so skip pushing in
        // packets into NetEq.
        return 0;
      }
    } else {
      last_decoder_ = DecoderInfo{/*payload_type=*/payload_type,
                                  /*sample_rate_hz=*/format->sample_rate_hz,
                                  /*num_channels=*/format->num_channels,
                                  /*sdp_format=*/std::move(format->sdp_format)};
    }
  }  // |mutex_| is released.

  if (neteq_->InsertPacket(rtp_header, incoming_payload) < 0) {
    RTC_LOG(LERROR) << "AcmReceiver::InsertPacket "
                    << static_cast<int>(rtp_header.payloadType)
                    << " Failed to insert packet";
    return -1;
  }
  return 0;
}

int AcmReceiver::GetAudio(int desired_freq_hz,
                          AudioFrame* audio_frame,
                          bool* muted) {
  RTC_DCHECK(muted);
  // Accessing members, take the lock.
  MutexLock lock(&mutex_);

  if (neteq_->GetAudio(audio_frame, muted) != NetEq::kOK) {
    RTC_LOG(LERROR) << "AcmReceiver::GetAudio - NetEq Failed.";
    return -1;
  }

  const int current_sample_rate_hz = neteq_->last_output_sample_rate_hz();

  // Update if resampling is required.
  const bool need_resampling =
      (desired_freq_hz != -1) && (current_sample_rate_hz != desired_freq_hz);

  if (need_resampling && !resampled_last_output_frame_) {
    // Prime the resampler with the last frame.
    int16_t temp_output[AudioFrame::kMaxDataSizeSamples];
    int samples_per_channel_int = resampler_.Resample10Msec(
        last_audio_buffer_.get(), current_sample_rate_hz, desired_freq_hz,
        audio_frame->num_channels_, AudioFrame::kMaxDataSizeSamples,
        temp_output);
    if (samples_per_channel_int < 0) {
      RTC_LOG(LERROR) << "AcmReceiver::GetAudio - "
                         "Resampling last_audio_buffer_ failed.";
      return -1;
    }
  }

  // TODO(henrik.lundin) Glitches in the output may appear if the output rate
  // from NetEq changes. See WebRTC issue 3923.
  if (need_resampling) {
    // TODO(yujo): handle this more efficiently for muted frames.
    int samples_per_channel_int = resampler_.Resample10Msec(
        audio_frame->data(), current_sample_rate_hz, desired_freq_hz,
        audio_frame->num_channels_, AudioFrame::kMaxDataSizeSamples,
        audio_frame->mutable_data());
    if (samples_per_channel_int < 0) {
      RTC_LOG(LERROR)
          << "AcmReceiver::GetAudio - Resampling audio_buffer_ failed.";
      return -1;
    }
    audio_frame->samples_per_channel_ =
        static_cast<size_t>(samples_per_channel_int);
    audio_frame->sample_rate_hz_ = desired_freq_hz;
    RTC_DCHECK_EQ(
        audio_frame->sample_rate_hz_,
        rtc::dchecked_cast<int>(audio_frame->samples_per_channel_ * 100));
    resampled_last_output_frame_ = true;
  } else {
    resampled_last_output_frame_ = false;
    // We might end up here ONLY if codec is changed.
  }

  // Store current audio in |last_audio_buffer_| for next time.
  memcpy(last_audio_buffer_.get(), audio_frame->data(),
         sizeof(int16_t) * audio_frame->samples_per_channel_ *
             audio_frame->num_channels_);

  call_stats_.DecodedByNetEq(audio_frame->speech_type_, *muted);
  return 0;
}

void AcmReceiver::SetCodecs(const std::map<int, SdpAudioFormat>& codecs) {
  neteq_->SetCodecs(codecs);
}

void AcmReceiver::FlushBuffers() {
  neteq_->FlushBuffers();
}

void AcmReceiver::RemoveAllCodecs() {
  MutexLock lock(&mutex_);
  neteq_->RemoveAllPayloadTypes();
  last_decoder_ = absl::nullopt;
}

absl::optional<uint32_t> AcmReceiver::GetPlayoutTimestamp() {
  return neteq_->GetPlayoutTimestamp();
}

int AcmReceiver::FilteredCurrentDelayMs() const {
  return neteq_->FilteredCurrentDelayMs();
}

int AcmReceiver::TargetDelayMs() const {
  return neteq_->TargetDelayMs();
}

absl::optional<std::pair<int, SdpAudioFormat>> AcmReceiver::LastDecoder()
    const {
  MutexLock lock(&mutex_);
  if (!last_decoder_) {
    return absl::nullopt;
  }
  RTC_DCHECK_NE(-1, last_decoder_->payload_type);
  return std::make_pair(last_decoder_->payload_type, last_decoder_->sdp_format);
}

void AcmReceiver::GetNetworkStatistics(
    NetworkStatistics* acm_stat,
    bool get_and_clear_legacy_stats /* = true */) const {
  NetEqNetworkStatistics neteq_stat;
  if (get_and_clear_legacy_stats) {
    // NetEq function always returns zero, so we don't check the return value.
    neteq_->NetworkStatistics(&neteq_stat);

    acm_stat->currentExpandRate = neteq_stat.expand_rate;
    acm_stat->currentSpeechExpandRate = neteq_stat.speech_expand_rate;
    acm_stat->currentPreemptiveRate = neteq_stat.preemptive_rate;
    acm_stat->currentAccelerateRate = neteq_stat.accelerate_rate;
    acm_stat->currentSecondaryDecodedRate = neteq_stat.secondary_decoded_rate;
    acm_stat->currentSecondaryDiscardedRate =
        neteq_stat.secondary_discarded_rate;
    acm_stat->meanWaitingTimeMs = neteq_stat.mean_waiting_time_ms;
    acm_stat->maxWaitingTimeMs = neteq_stat.max_waiting_time_ms;
  } else {
    neteq_stat = neteq_->CurrentNetworkStatistics();
    acm_stat->currentExpandRate = 0;
    acm_stat->currentSpeechExpandRate = 0;
    acm_stat->currentPreemptiveRate = 0;
    acm_stat->currentAccelerateRate = 0;
    acm_stat->currentSecondaryDecodedRate = 0;
    acm_stat->currentSecondaryDiscardedRate = 0;
    acm_stat->meanWaitingTimeMs = -1;
    acm_stat->maxWaitingTimeMs = 1;
  }
  acm_stat->currentBufferSize = neteq_stat.current_buffer_size_ms;
  acm_stat->preferredBufferSize = neteq_stat.preferred_buffer_size_ms;
  acm_stat->jitterPeaksFound = neteq_stat.jitter_peaks_found ? true : false;

  NetEqLifetimeStatistics neteq_lifetime_stat = neteq_->GetLifetimeStatistics();
  acm_stat->totalSamplesReceived = neteq_lifetime_stat.total_samples_received;
  acm_stat->concealedSamples = neteq_lifetime_stat.concealed_samples;
  acm_stat->silentConcealedSamples =
      neteq_lifetime_stat.silent_concealed_samples;
  acm_stat->concealmentEvents = neteq_lifetime_stat.concealment_events;
  acm_stat->jitterBufferDelayMs = neteq_lifetime_stat.jitter_buffer_delay_ms;
  acm_stat->jitterBufferTargetDelayMs =
      neteq_lifetime_stat.jitter_buffer_target_delay_ms;
  acm_stat->jitterBufferEmittedCount =
      neteq_lifetime_stat.jitter_buffer_emitted_count;
  acm_stat->delayedPacketOutageSamples =
      neteq_lifetime_stat.delayed_packet_outage_samples;
  acm_stat->relativePacketArrivalDelayMs =
      neteq_lifetime_stat.relative_packet_arrival_delay_ms;
  acm_stat->interruptionCount = neteq_lifetime_stat.interruption_count;
  acm_stat->totalInterruptionDurationMs =
      neteq_lifetime_stat.total_interruption_duration_ms;
  acm_stat->insertedSamplesForDeceleration =
      neteq_lifetime_stat.inserted_samples_for_deceleration;
  acm_stat->removedSamplesForAcceleration =
      neteq_lifetime_stat.removed_samples_for_acceleration;
  acm_stat->fecPacketsReceived = neteq_lifetime_stat.fec_packets_received;
  acm_stat->fecPacketsDiscarded = neteq_lifetime_stat.fec_packets_discarded;

  NetEqOperationsAndState neteq_operations_and_state =
      neteq_->GetOperationsAndState();
  acm_stat->packetBufferFlushes =
      neteq_operations_and_state.packet_buffer_flushes;
}

int AcmReceiver::EnableNack(size_t max_nack_list_size) {
  neteq_->EnableNack(max_nack_list_size);
  return 0;
}

void AcmReceiver::DisableNack() {
  neteq_->DisableNack();
}

std::vector<uint16_t> AcmReceiver::GetNackList(
    int64_t round_trip_time_ms) const {
  return neteq_->GetNackList(round_trip_time_ms);
}

void AcmReceiver::ResetInitialDelay() {
  neteq_->SetMinimumDelay(0);
  // TODO(turajs): Should NetEq Buffer be flushed?
}

uint32_t AcmReceiver::NowInTimestamp(int decoder_sampling_rate) const {
  // Down-cast the time to (32-6)-bit since we only care about
  // the least significant bits. (32-6) bits cover 2^(32-6) = 67108864 ms.
  // We masked 6 most significant bits of 32-bit so there is no overflow in
  // the conversion from milliseconds to timestamp.
  const uint32_t now_in_ms =
      static_cast<uint32_t>(clock_->TimeInMilliseconds() & 0x03ffffff);
  return static_cast<uint32_t>((decoder_sampling_rate / 1000) * now_in_ms);
}

void AcmReceiver::GetDecodingCallStatistics(
    AudioDecodingCallStats* stats) const {
  MutexLock lock(&mutex_);
  *stats = call_stats_.GetDecodingStatistics();
}

}  // namespace acm2

}  // namespace webrtc
