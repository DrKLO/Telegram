/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/send_delay_stats.h"

#include <utility>

#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
// Packet with a larger delay are removed and excluded from the delay stats.
// Set to larger than max histogram delay which is 10 seconds.
constexpr TimeDelta kMaxSentPacketDelay = TimeDelta::Seconds(11);
constexpr size_t kMaxPacketMapSize = 2000;

// Limit for the maximum number of streams to calculate stats for.
constexpr size_t kMaxSsrcMapSize = 50;
constexpr int kMinRequiredPeriodicSamples = 5;
}  // namespace

SendDelayStats::SendDelayStats(Clock* clock)
    : clock_(clock), num_old_packets_(0), num_skipped_packets_(0) {}

SendDelayStats::~SendDelayStats() {
  if (num_old_packets_ > 0 || num_skipped_packets_ > 0) {
    RTC_LOG(LS_WARNING) << "Delay stats: number of old packets "
                        << num_old_packets_ << ", skipped packets "
                        << num_skipped_packets_ << ". Number of streams "
                        << send_delay_counters_.size();
  }
  UpdateHistograms();
}

void SendDelayStats::UpdateHistograms() {
  MutexLock lock(&mutex_);
  for (auto& [unused, counter] : send_delay_counters_) {
    AggregatedStats stats = counter.GetStats();
    if (stats.num_samples >= kMinRequiredPeriodicSamples) {
      RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.SendDelayInMs", stats.average);
      RTC_LOG(LS_INFO) << "WebRTC.Video.SendDelayInMs, " << stats.ToString();
    }
  }
}

void SendDelayStats::AddSsrcs(const VideoSendStream::Config& config) {
  MutexLock lock(&mutex_);
  if (send_delay_counters_.size() + config.rtp.ssrcs.size() > kMaxSsrcMapSize)
    return;
  for (uint32_t ssrc : config.rtp.ssrcs) {
    send_delay_counters_.try_emplace(ssrc, clock_, nullptr, false);
  }
}

void SendDelayStats::OnSendPacket(uint16_t packet_id,
                                  Timestamp capture_time,
                                  uint32_t ssrc) {
  // Packet sent to transport.
  MutexLock lock(&mutex_);
  auto it = send_delay_counters_.find(ssrc);
  if (it == send_delay_counters_.end())
    return;

  Timestamp now = clock_->CurrentTime();
  RemoveOld(now);

  if (packets_.size() > kMaxPacketMapSize) {
    ++num_skipped_packets_;
    return;
  }
  // `send_delay_counters_` is an std::map - adding new entries doesn't
  // invalidate existent iterators, and it has pointer stability for values.
  // Entries are never remove from the `send_delay_counters_`.
  // Thus memorizing pointer to the AvgCounter is safe.
  packets_.emplace(packet_id, Packet{.send_delay = &it->second,
                                     .capture_time = capture_time,
                                     .send_time = now});
}

bool SendDelayStats::OnSentPacket(int packet_id, Timestamp time) {
  // Packet leaving socket.
  if (packet_id == -1)
    return false;

  MutexLock lock(&mutex_);
  auto it = packets_.find(packet_id);
  if (it == packets_.end())
    return false;

  // TODO(asapersson): Remove SendSideDelayUpdated(), use capture -> sent.
  // Elapsed time from send (to transport) -> sent (leaving socket).
  TimeDelta diff = time - it->second.send_time;
  it->second.send_delay->Add(diff.ms());
  packets_.erase(it);
  return true;
}

void SendDelayStats::RemoveOld(Timestamp now) {
  while (!packets_.empty()) {
    auto it = packets_.begin();
    if (now - it->second.capture_time < kMaxSentPacketDelay)
      break;

    packets_.erase(it);
    ++num_old_packets_;
  }
}

}  // namespace webrtc
