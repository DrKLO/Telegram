/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/ulpfec_generator.h"

#include <string.h>

#include <cstdint>
#include <memory>
#include <utility>

#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/forward_error_correction.h"
#include "modules/rtp_rtcp/source/forward_error_correction_internal.h"
#include "modules/rtp_rtcp/source/rtp_utility.h"
#include "rtc_base/checks.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

namespace {

constexpr size_t kRedForFecHeaderLength = 1;

// This controls the maximum amount of excess overhead (actual - target)
// allowed in order to trigger EncodeFec(), before |params_.max_fec_frames|
// is reached. Overhead here is defined as relative to number of media packets.
constexpr int kMaxExcessOverhead = 50;  // Q8.

// This is the minimum number of media packets required (above some protection
// level) in order to trigger EncodeFec(), before |params_.max_fec_frames| is
// reached.
constexpr size_t kMinMediaPackets = 4;

// Threshold on the received FEC protection level, above which we enforce at
// least |kMinMediaPackets| packets for the FEC code. Below this
// threshold |kMinMediaPackets| is set to default value of 1.
//
// The range is between 0 and 255, where 255 corresponds to 100% overhead
// (relative to the number of protected media packets).
constexpr uint8_t kHighProtectionThreshold = 80;

// This threshold is used to adapt the |kMinMediaPackets| threshold, based
// on the average number of packets per frame seen so far. When there are few
// packets per frame (as given by this threshold), at least
// |kMinMediaPackets| + 1 packets are sent to the FEC code.
constexpr float kMinMediaPacketsAdaptationThreshold = 2.0f;

// At construction time, we don't know the SSRC that is used for the generated
// FEC packets, but we still need to give it to the ForwardErrorCorrection ctor
// to be used in the decoding.
// TODO(brandtr): Get rid of this awkwardness by splitting
// ForwardErrorCorrection in two objects -- one encoder and one decoder.
constexpr uint32_t kUnknownSsrc = 0;

}  // namespace

UlpfecGenerator::Params::Params() = default;
UlpfecGenerator::Params::Params(FecProtectionParams delta_params,
                                FecProtectionParams keyframe_params)
    : delta_params(delta_params), keyframe_params(keyframe_params) {}

UlpfecGenerator::UlpfecGenerator(int red_payload_type,
                                 int ulpfec_payload_type,
                                 Clock* clock)
    : red_payload_type_(red_payload_type),
      ulpfec_payload_type_(ulpfec_payload_type),
      clock_(clock),
      fec_(ForwardErrorCorrection::CreateUlpfec(kUnknownSsrc)),
      num_protected_frames_(0),
      min_num_media_packets_(1),
      media_contains_keyframe_(false),
      fec_bitrate_(/*max_window_size_ms=*/1000, RateStatistics::kBpsScale) {}

// Used by FlexFecSender, payload types are unused.
UlpfecGenerator::UlpfecGenerator(std::unique_ptr<ForwardErrorCorrection> fec,
                                 Clock* clock)
    : red_payload_type_(0),
      ulpfec_payload_type_(0),
      clock_(clock),
      fec_(std::move(fec)),
      num_protected_frames_(0),
      min_num_media_packets_(1),
      media_contains_keyframe_(false),
      fec_bitrate_(/*max_window_size_ms=*/1000, RateStatistics::kBpsScale) {}

UlpfecGenerator::~UlpfecGenerator() = default;

void UlpfecGenerator::SetProtectionParameters(
    const FecProtectionParams& delta_params,
    const FecProtectionParams& key_params) {
  RTC_DCHECK_GE(delta_params.fec_rate, 0);
  RTC_DCHECK_LE(delta_params.fec_rate, 255);
  RTC_DCHECK_GE(key_params.fec_rate, 0);
  RTC_DCHECK_LE(key_params.fec_rate, 255);
  // Store the new params and apply them for the next set of FEC packets being
  // produced.
  MutexLock lock(&mutex_);
  pending_params_.emplace(delta_params, key_params);
}

void UlpfecGenerator::AddPacketAndGenerateFec(const RtpPacketToSend& packet) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  RTC_DCHECK(generated_fec_packets_.empty());

  {
    MutexLock lock(&mutex_);
    if (pending_params_) {
      current_params_ = *pending_params_;
      pending_params_.reset();

      if (CurrentParams().fec_rate > kHighProtectionThreshold) {
        min_num_media_packets_ = kMinMediaPackets;
      } else {
        min_num_media_packets_ = 1;
      }
    }
  }

  if (packet.is_key_frame()) {
    media_contains_keyframe_ = true;
  }
  const bool complete_frame = packet.Marker();
  if (media_packets_.size() < kUlpfecMaxMediaPackets) {
    // Our packet masks can only protect up to |kUlpfecMaxMediaPackets| packets.
    auto fec_packet = std::make_unique<ForwardErrorCorrection::Packet>();
    fec_packet->data = packet.Buffer();
    media_packets_.push_back(std::move(fec_packet));

    // Keep a copy of the last RTP packet, so we can copy the RTP header
    // from it when creating newly generated ULPFEC+RED packets.
    RTC_DCHECK_GE(packet.headers_size(), kRtpHeaderSize);
    last_media_packet_ = packet;
  }

  if (complete_frame) {
    ++num_protected_frames_;
  }

  auto params = CurrentParams();

  // Produce FEC over at most |params_.max_fec_frames| frames, or as soon as:
  // (1) the excess overhead (actual overhead - requested/target overhead) is
  // less than |kMaxExcessOverhead|, and
  // (2) at least |min_num_media_packets_| media packets is reached.
  if (complete_frame &&
      (num_protected_frames_ >= params.max_fec_frames ||
       (ExcessOverheadBelowMax() && MinimumMediaPacketsReached()))) {
    // We are not using Unequal Protection feature of the parity erasure code.
    constexpr int kNumImportantPackets = 0;
    constexpr bool kUseUnequalProtection = false;
    fec_->EncodeFec(media_packets_, params.fec_rate, kNumImportantPackets,
                    kUseUnequalProtection, params.fec_mask_type,
                    &generated_fec_packets_);
    if (generated_fec_packets_.empty()) {
      ResetState();
    }
  }
}

bool UlpfecGenerator::ExcessOverheadBelowMax() const {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);

  return ((Overhead() - CurrentParams().fec_rate) < kMaxExcessOverhead);
}

bool UlpfecGenerator::MinimumMediaPacketsReached() const {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  float average_num_packets_per_frame =
      static_cast<float>(media_packets_.size()) / num_protected_frames_;
  int num_media_packets = static_cast<int>(media_packets_.size());
  if (average_num_packets_per_frame < kMinMediaPacketsAdaptationThreshold) {
    return num_media_packets >= min_num_media_packets_;
  } else {
    // For larger rates (more packets/frame), increase the threshold.
    // TODO(brandtr): Investigate what impact this adaptation has.
    return num_media_packets >= min_num_media_packets_ + 1;
  }
}

const FecProtectionParams& UlpfecGenerator::CurrentParams() const {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  return media_contains_keyframe_ ? current_params_.keyframe_params
                                  : current_params_.delta_params;
}

size_t UlpfecGenerator::MaxPacketOverhead() const {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  return fec_->MaxPacketOverhead();
}

std::vector<std::unique_ptr<RtpPacketToSend>> UlpfecGenerator::GetFecPackets() {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  if (generated_fec_packets_.empty()) {
    return std::vector<std::unique_ptr<RtpPacketToSend>>();
  }

  // Wrap FEC packet (including FEC headers) in a RED packet. Since the
  // FEC packets in |generated_fec_packets_| don't have RTP headers, we
  // reuse the header from the last media packet.
  RTC_CHECK(last_media_packet_.has_value());
  last_media_packet_->SetPayloadSize(0);

  std::vector<std::unique_ptr<RtpPacketToSend>> fec_packets;
  fec_packets.reserve(generated_fec_packets_.size());

  size_t total_fec_size_bytes = 0;
  for (const auto* fec_packet : generated_fec_packets_) {
    std::unique_ptr<RtpPacketToSend> red_packet =
        std::make_unique<RtpPacketToSend>(*last_media_packet_);
    red_packet->SetPayloadType(red_payload_type_);
    red_packet->SetMarker(false);
    uint8_t* payload_buffer = red_packet->SetPayloadSize(
        kRedForFecHeaderLength + fec_packet->data.size());
    // Primary RED header with F bit unset.
    // See https://tools.ietf.org/html/rfc2198#section-3
    payload_buffer[0] = ulpfec_payload_type_;  // RED header.
    memcpy(&payload_buffer[1], fec_packet->data.data(),
           fec_packet->data.size());
    total_fec_size_bytes += red_packet->size();
    red_packet->set_packet_type(RtpPacketMediaType::kForwardErrorCorrection);
    red_packet->set_allow_retransmission(false);
    red_packet->set_is_red(true);
    red_packet->set_fec_protect_packet(false);
    fec_packets.push_back(std::move(red_packet));
  }

  ResetState();

  MutexLock lock(&mutex_);
  fec_bitrate_.Update(total_fec_size_bytes, clock_->TimeInMilliseconds());

  return fec_packets;
}

DataRate UlpfecGenerator::CurrentFecRate() const {
  MutexLock lock(&mutex_);
  return DataRate::BitsPerSec(
      fec_bitrate_.Rate(clock_->TimeInMilliseconds()).value_or(0));
}

int UlpfecGenerator::Overhead() const {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  RTC_DCHECK(!media_packets_.empty());
  int num_fec_packets =
      fec_->NumFecPackets(media_packets_.size(), CurrentParams().fec_rate);

  // Return the overhead in Q8.
  return (num_fec_packets << 8) / media_packets_.size();
}

void UlpfecGenerator::ResetState() {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  media_packets_.clear();
  last_media_packet_.reset();
  generated_fec_packets_.clear();
  num_protected_frames_ = 0;
  media_contains_keyframe_ = false;
}

}  // namespace webrtc
