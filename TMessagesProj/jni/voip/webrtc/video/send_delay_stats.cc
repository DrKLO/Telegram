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
// Set to larger than max histogram delay which is 10000.
const int64_t kMaxSentPacketDelayMs = 11000;
const size_t kMaxPacketMapSize = 2000;

// Limit for the maximum number of streams to calculate stats for.
const size_t kMaxSsrcMapSize = 50;
const int kMinRequiredPeriodicSamples = 5;
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
  for (const auto& it : send_delay_counters_) {
    AggregatedStats stats = it.second->GetStats();
    if (stats.num_samples >= kMinRequiredPeriodicSamples) {
      RTC_HISTOGRAM_COUNTS_10000("WebRTC.Video.SendDelayInMs", stats.average);
      RTC_LOG(LS_INFO) << "WebRTC.Video.SendDelayInMs, " << stats.ToString();
    }
  }
}

void SendDelayStats::AddSsrcs(const VideoSendStream::Config& config) {
  MutexLock lock(&mutex_);
  if (ssrcs_.size() > kMaxSsrcMapSize)
    return;
  for (const auto& ssrc : config.rtp.ssrcs)
    ssrcs_.insert(ssrc);
}

AvgCounter* SendDelayStats::GetSendDelayCounter(uint32_t ssrc) {
  const auto& it = send_delay_counters_.find(ssrc);
  if (it != send_delay_counters_.end())
    return it->second.get();

  AvgCounter* counter = new AvgCounter(clock_, nullptr, false);
  send_delay_counters_[ssrc].reset(counter);
  return counter;
}

void SendDelayStats::OnSendPacket(uint16_t packet_id,
                                  int64_t capture_time_ms,
                                  uint32_t ssrc) {
  // Packet sent to transport.
  MutexLock lock(&mutex_);
  if (ssrcs_.find(ssrc) == ssrcs_.end())
    return;

  int64_t now = clock_->TimeInMilliseconds();
  RemoveOld(now, &packets_);

  if (packets_.size() > kMaxPacketMapSize) {
    ++num_skipped_packets_;
    return;
  }
  packets_.insert(
      std::make_pair(packet_id, Packet(ssrc, capture_time_ms, now)));
}

bool SendDelayStats::OnSentPacket(int packet_id, int64_t time_ms) {
  // Packet leaving socket.
  if (packet_id == -1)
    return false;

  MutexLock lock(&mutex_);
  auto it = packets_.find(packet_id);
  if (it == packets_.end())
    return false;

  // TODO(asapersson): Remove SendSideDelayUpdated(), use capture -> sent.
  // Elapsed time from send (to transport) -> sent (leaving socket).
  int diff_ms = time_ms - it->second.send_time_ms;
  GetSendDelayCounter(it->second.ssrc)->Add(diff_ms);
  packets_.erase(it);
  return true;
}

void SendDelayStats::RemoveOld(int64_t now, PacketMap* packets) {
  while (!packets->empty()) {
    auto it = packets->begin();
    if (now - it->second.capture_time_ms < kMaxSentPacketDelayMs)
      break;

    packets->erase(it);
    ++num_old_packets_;
  }
}

}  // namespace webrtc
