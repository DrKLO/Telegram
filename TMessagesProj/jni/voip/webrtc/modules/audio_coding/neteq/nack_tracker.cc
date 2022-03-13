/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/nack_tracker.h"

#include <cstdint>
#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {

const int kDefaultSampleRateKhz = 48;
const int kMaxPacketSizeMs = 120;
constexpr char kNackTrackerConfigFieldTrial[] =
    "WebRTC-Audio-NetEqNackTrackerConfig";

}  // namespace

NackTracker::Config::Config() {
  auto parser = StructParametersParser::Create(
      "packet_loss_forget_factor", &packet_loss_forget_factor,
      "ms_per_loss_percent", &ms_per_loss_percent, "never_nack_multiple_times",
      &never_nack_multiple_times, "require_valid_rtt", &require_valid_rtt,
      "max_loss_rate", &max_loss_rate);
  parser->Parse(
      webrtc::field_trial::FindFullName(kNackTrackerConfigFieldTrial));
  RTC_LOG(LS_INFO) << "Nack tracker config:"
                      " packet_loss_forget_factor="
                   << packet_loss_forget_factor
                   << " ms_per_loss_percent=" << ms_per_loss_percent
                   << " never_nack_multiple_times=" << never_nack_multiple_times
                   << " require_valid_rtt=" << require_valid_rtt
                   << " max_loss_rate=" << max_loss_rate;
}

NackTracker::NackTracker()
    : sequence_num_last_received_rtp_(0),
      timestamp_last_received_rtp_(0),
      any_rtp_received_(false),
      sequence_num_last_decoded_rtp_(0),
      timestamp_last_decoded_rtp_(0),
      any_rtp_decoded_(false),
      sample_rate_khz_(kDefaultSampleRateKhz),
      max_nack_list_size_(kNackListSizeLimit) {}

NackTracker::~NackTracker() = default;

void NackTracker::UpdateSampleRate(int sample_rate_hz) {
  RTC_DCHECK_GT(sample_rate_hz, 0);
  sample_rate_khz_ = sample_rate_hz / 1000;
}

void NackTracker::UpdateLastReceivedPacket(uint16_t sequence_number,
                                           uint32_t timestamp) {
  // Just record the value of sequence number and timestamp if this is the
  // first packet.
  if (!any_rtp_received_) {
    sequence_num_last_received_rtp_ = sequence_number;
    timestamp_last_received_rtp_ = timestamp;
    any_rtp_received_ = true;
    // If no packet is decoded, to have a reasonable estimate of time-to-play
    // use the given values.
    if (!any_rtp_decoded_) {
      sequence_num_last_decoded_rtp_ = sequence_number;
      timestamp_last_decoded_rtp_ = timestamp;
    }
    return;
  }

  if (sequence_number == sequence_num_last_received_rtp_)
    return;

  // Received RTP should not be in the list.
  nack_list_.erase(sequence_number);

  // If this is an old sequence number, no more action is required, return.
  if (IsNewerSequenceNumber(sequence_num_last_received_rtp_, sequence_number))
    return;

  UpdatePacketLossRate(sequence_number - sequence_num_last_received_rtp_ - 1);

  UpdateList(sequence_number, timestamp);

  sequence_num_last_received_rtp_ = sequence_number;
  timestamp_last_received_rtp_ = timestamp;
  LimitNackListSize();
}

absl::optional<int> NackTracker::GetSamplesPerPacket(
    uint16_t sequence_number_current_received_rtp,
    uint32_t timestamp_current_received_rtp) const {
  uint32_t timestamp_increase =
      timestamp_current_received_rtp - timestamp_last_received_rtp_;
  uint16_t sequence_num_increase =
      sequence_number_current_received_rtp - sequence_num_last_received_rtp_;

  int samples_per_packet = timestamp_increase / sequence_num_increase;
  if (samples_per_packet == 0 ||
      samples_per_packet > kMaxPacketSizeMs * sample_rate_khz_) {
    // Not a valid samples per packet.
    return absl::nullopt;
  }
  return samples_per_packet;
}

void NackTracker::UpdateList(uint16_t sequence_number_current_received_rtp,
                             uint32_t timestamp_current_received_rtp) {
  if (!IsNewerSequenceNumber(sequence_number_current_received_rtp,
                             sequence_num_last_received_rtp_ + 1)) {
    return;
  }
  RTC_DCHECK(!any_rtp_decoded_ ||
             IsNewerSequenceNumber(sequence_number_current_received_rtp,
                                   sequence_num_last_decoded_rtp_));

  absl::optional<int> samples_per_packet = GetSamplesPerPacket(
      sequence_number_current_received_rtp, timestamp_current_received_rtp);
  if (!samples_per_packet) {
    return;
  }

  for (uint16_t n = sequence_num_last_received_rtp_ + 1;
       IsNewerSequenceNumber(sequence_number_current_received_rtp, n); ++n) {
    uint32_t timestamp = EstimateTimestamp(n, *samples_per_packet);
    NackElement nack_element(TimeToPlay(timestamp), timestamp);
    nack_list_.insert(nack_list_.end(), std::make_pair(n, nack_element));
  }
}

uint32_t NackTracker::EstimateTimestamp(uint16_t sequence_num,
                                        int samples_per_packet) {
  uint16_t sequence_num_diff = sequence_num - sequence_num_last_received_rtp_;
  return sequence_num_diff * samples_per_packet + timestamp_last_received_rtp_;
}

void NackTracker::UpdateEstimatedPlayoutTimeBy10ms() {
  while (!nack_list_.empty() &&
         nack_list_.begin()->second.time_to_play_ms <= 10)
    nack_list_.erase(nack_list_.begin());

  for (NackList::iterator it = nack_list_.begin(); it != nack_list_.end(); ++it)
    it->second.time_to_play_ms -= 10;
}

void NackTracker::UpdateLastDecodedPacket(uint16_t sequence_number,
                                          uint32_t timestamp) {
  if (IsNewerSequenceNumber(sequence_number, sequence_num_last_decoded_rtp_) ||
      !any_rtp_decoded_) {
    sequence_num_last_decoded_rtp_ = sequence_number;
    timestamp_last_decoded_rtp_ = timestamp;
    // Packets in the list with sequence numbers less than the
    // sequence number of the decoded RTP should be removed from the lists.
    // They will be discarded by the jitter buffer if they arrive.
    nack_list_.erase(nack_list_.begin(),
                     nack_list_.upper_bound(sequence_num_last_decoded_rtp_));

    // Update estimated time-to-play.
    for (NackList::iterator it = nack_list_.begin(); it != nack_list_.end();
         ++it)
      it->second.time_to_play_ms = TimeToPlay(it->second.estimated_timestamp);
  } else {
    RTC_DCHECK_EQ(sequence_number, sequence_num_last_decoded_rtp_);

    // Same sequence number as before. 10 ms is elapsed, update estimations for
    // time-to-play.
    UpdateEstimatedPlayoutTimeBy10ms();

    // Update timestamp for better estimate of time-to-play, for packets which
    // are added to NACK list later on.
    timestamp_last_decoded_rtp_ += sample_rate_khz_ * 10;
  }
  any_rtp_decoded_ = true;
}

NackTracker::NackList NackTracker::GetNackList() const {
  return nack_list_;
}

void NackTracker::Reset() {
  nack_list_.clear();

  sequence_num_last_received_rtp_ = 0;
  timestamp_last_received_rtp_ = 0;
  any_rtp_received_ = false;
  sequence_num_last_decoded_rtp_ = 0;
  timestamp_last_decoded_rtp_ = 0;
  any_rtp_decoded_ = false;
  sample_rate_khz_ = kDefaultSampleRateKhz;
}

void NackTracker::SetMaxNackListSize(size_t max_nack_list_size) {
  RTC_CHECK_GT(max_nack_list_size, 0);
  // Ugly hack to get around the problem of passing static consts by reference.
  const size_t kNackListSizeLimitLocal = NackTracker::kNackListSizeLimit;
  RTC_CHECK_LE(max_nack_list_size, kNackListSizeLimitLocal);

  max_nack_list_size_ = max_nack_list_size;
  LimitNackListSize();
}

void NackTracker::LimitNackListSize() {
  uint16_t limit = sequence_num_last_received_rtp_ -
                   static_cast<uint16_t>(max_nack_list_size_) - 1;
  nack_list_.erase(nack_list_.begin(), nack_list_.upper_bound(limit));
}

int64_t NackTracker::TimeToPlay(uint32_t timestamp) const {
  uint32_t timestamp_increase = timestamp - timestamp_last_decoded_rtp_;
  return timestamp_increase / sample_rate_khz_;
}

// We don't erase elements with time-to-play shorter than round-trip-time.
std::vector<uint16_t> NackTracker::GetNackList(int64_t round_trip_time_ms) {
  RTC_DCHECK_GE(round_trip_time_ms, 0);
  std::vector<uint16_t> sequence_numbers;
  if (config_.require_valid_rtt && round_trip_time_ms == 0) {
    return sequence_numbers;
  }
  if (packet_loss_rate_ >
      static_cast<uint32_t>(config_.max_loss_rate * (1 << 30))) {
    return sequence_numbers;
  }
  // The estimated packet loss is between 0 and 1, so we need to multiply by 100
  // here.
  int max_wait_ms =
      100.0 * config_.ms_per_loss_percent * packet_loss_rate_ / (1 << 30);
  for (NackList::const_iterator it = nack_list_.begin(); it != nack_list_.end();
       ++it) {
    int64_t time_since_packet_ms =
        (timestamp_last_received_rtp_ - it->second.estimated_timestamp) /
        sample_rate_khz_;
    if (it->second.time_to_play_ms > round_trip_time_ms ||
        time_since_packet_ms + round_trip_time_ms < max_wait_ms)
      sequence_numbers.push_back(it->first);
  }
  if (config_.never_nack_multiple_times) {
    nack_list_.clear();
  }
  return sequence_numbers;
}

void NackTracker::UpdatePacketLossRate(int packets_lost) {
  const uint64_t alpha_q30 = (1 << 30) * config_.packet_loss_forget_factor;
  // Exponential filter.
  packet_loss_rate_ = (alpha_q30 * packet_loss_rate_) >> 30;
  for (int i = 0; i < packets_lost; ++i) {
    packet_loss_rate_ =
        ((alpha_q30 * packet_loss_rate_) >> 30) + ((1 << 30) - alpha_q30);
  }
}
}  // namespace webrtc
