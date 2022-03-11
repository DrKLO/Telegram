/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/source_tracker.h"

#include <algorithm>
#include <utility>

namespace webrtc {

constexpr int64_t SourceTracker::kTimeoutMs;

SourceTracker::SourceTracker(Clock* clock) : clock_(clock) {}

void SourceTracker::OnFrameDelivered(const RtpPacketInfos& packet_infos) {
  if (packet_infos.empty()) {
    return;
  }

  int64_t now_ms = clock_->TimeInMilliseconds();
  MutexLock lock_scope(&lock_);

  for (const auto& packet_info : packet_infos) {
    for (uint32_t csrc : packet_info.csrcs()) {
      SourceKey key(RtpSourceType::CSRC, csrc);
      SourceEntry& entry = UpdateEntry(key);

      entry.timestamp_ms = now_ms;
      entry.audio_level = packet_info.audio_level();
      entry.absolute_capture_time = packet_info.absolute_capture_time();
      entry.rtp_timestamp = packet_info.rtp_timestamp();
    }

    SourceKey key(RtpSourceType::SSRC, packet_info.ssrc());
    SourceEntry& entry = UpdateEntry(key);

    entry.timestamp_ms = now_ms;
    entry.audio_level = packet_info.audio_level();
    entry.absolute_capture_time = packet_info.absolute_capture_time();
    entry.rtp_timestamp = packet_info.rtp_timestamp();
  }

  PruneEntries(now_ms);
}

std::vector<RtpSource> SourceTracker::GetSources() const {
  std::vector<RtpSource> sources;

  int64_t now_ms = clock_->TimeInMilliseconds();
  MutexLock lock_scope(&lock_);

  PruneEntries(now_ms);

  for (const auto& pair : list_) {
    const SourceKey& key = pair.first;
    const SourceEntry& entry = pair.second;

    sources.emplace_back(
        entry.timestamp_ms, key.source, key.source_type, entry.rtp_timestamp,
        RtpSource::Extensions{entry.audio_level, entry.absolute_capture_time});
  }

  return sources;
}

SourceTracker::SourceEntry& SourceTracker::UpdateEntry(const SourceKey& key) {
  // We intentionally do |find() + emplace()|, instead of checking the return
  // value of `emplace()`, for performance reasons. It's much more likely for
  // the key to already exist than for it not to.
  auto map_it = map_.find(key);
  if (map_it == map_.end()) {
    // Insert a new entry at the front of the list.
    list_.emplace_front(key, SourceEntry());
    map_.emplace(key, list_.begin());
  } else if (map_it->second != list_.begin()) {
    // Move the old entry to the front of the list.
    list_.splice(list_.begin(), list_, map_it->second);
  }

  return list_.front().second;
}

void SourceTracker::PruneEntries(int64_t now_ms) const {
  int64_t prune_ms = now_ms - kTimeoutMs;

  while (!list_.empty() && list_.back().second.timestamp_ms < prune_ms) {
    map_.erase(list_.back().first);
    list_.pop_back();
  }
}

}  // namespace webrtc
