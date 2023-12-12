/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.h"


#include <cstdint>
#include <utility>

#include "absl/types/optional.h"
#include "modules/remote_bitrate_estimator/aimd_rate_control.h"
#include "modules/remote_bitrate_estimator/include/bwe_defines.h"
#include "modules/remote_bitrate_estimator/inter_arrival.h"
#include "modules/remote_bitrate_estimator/overuse_detector.h"
#include "modules/remote_bitrate_estimator/overuse_estimator.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {
namespace {
absl::optional<DataRate> OptionalRateFromOptionalBps(
    absl::optional<int> bitrate_bps) {
  if (bitrate_bps) {
    return DataRate::BitsPerSec(*bitrate_bps);
  } else {
    return absl::nullopt;
  }
}
}  // namespace

enum { kTimestampGroupLengthMs = 5 };
static const double kTimestampToMs = 1.0 / 90.0;

struct RemoteBitrateEstimatorSingleStream::Detector {
  explicit Detector(int64_t last_packet_time_ms,
                    const OverUseDetectorOptions& options,
                    bool enable_burst_grouping,
                    const FieldTrialsView* key_value_config)
      : last_packet_time_ms(last_packet_time_ms),
        inter_arrival(90 * kTimestampGroupLengthMs,
                      kTimestampToMs,
                      enable_burst_grouping),
        estimator(options),
        detector(key_value_config) {}
  int64_t last_packet_time_ms;
  InterArrival inter_arrival;
  OveruseEstimator estimator;
  OveruseDetector detector;
};

RemoteBitrateEstimatorSingleStream::RemoteBitrateEstimatorSingleStream(
    RemoteBitrateObserver* observer,
    Clock* clock)
    : clock_(clock),
      incoming_bitrate_(kBitrateWindowMs, 8000),
      last_valid_incoming_bitrate_(0),
      remote_rate_(new AimdRateControl(&field_trials_)),
      observer_(observer),
      last_process_time_(-1),
      process_interval_ms_(kProcessIntervalMs),
      uma_recorded_(false) {
  RTC_LOG(LS_INFO) << "RemoteBitrateEstimatorSingleStream: Instantiating.";
}

RemoteBitrateEstimatorSingleStream::~RemoteBitrateEstimatorSingleStream() {
  while (!overuse_detectors_.empty()) {
    SsrcOveruseEstimatorMap::iterator it = overuse_detectors_.begin();
    delete it->second;
    overuse_detectors_.erase(it);
  }
}

void RemoteBitrateEstimatorSingleStream::IncomingPacket(
    int64_t arrival_time_ms,
    size_t payload_size,
    const RTPHeader& header) {
  if (!uma_recorded_) {
    BweNames type = BweNames::kReceiverTOffset;
    if (!header.extension.hasTransmissionTimeOffset)
      type = BweNames::kReceiverNoExtension;
    RTC_HISTOGRAM_ENUMERATION(kBweTypeHistogram, type, BweNames::kBweNamesMax);
    uma_recorded_ = true;
  }
  uint32_t ssrc = header.ssrc;
  uint32_t rtp_timestamp =
      header.timestamp + header.extension.transmissionTimeOffset;
  int64_t now_ms = clock_->TimeInMilliseconds();
  MutexLock lock(&mutex_);
  SsrcOveruseEstimatorMap::iterator it = overuse_detectors_.find(ssrc);
  if (it == overuse_detectors_.end()) {
    // This is a new SSRC. Adding to map.
    // TODO(holmer): If the channel changes SSRC the old SSRC will still be
    // around in this map until the channel is deleted. This is OK since the
    // callback will no longer be called for the old SSRC. This will be
    // automatically cleaned up when we have one RemoteBitrateEstimator per REMB
    // group.
    std::pair<SsrcOveruseEstimatorMap::iterator, bool> insert_result =
        overuse_detectors_.insert(
            std::make_pair(ssrc, new Detector(now_ms, OverUseDetectorOptions(),
                                              true, &field_trials_)));
    it = insert_result.first;
  }
  Detector* estimator = it->second;
  estimator->last_packet_time_ms = now_ms;

  // Check if incoming bitrate estimate is valid, and if it needs to be reset.
  absl::optional<uint32_t> incoming_bitrate = incoming_bitrate_.Rate(now_ms);
  if (incoming_bitrate) {
    last_valid_incoming_bitrate_ = *incoming_bitrate;
  } else if (last_valid_incoming_bitrate_ > 0) {
    // Incoming bitrate had a previous valid value, but now not enough data
    // point are left within the current window. Reset incoming bitrate
    // estimator so that the window size will only contain new data points.
    incoming_bitrate_.Reset();
    last_valid_incoming_bitrate_ = 0;
  }
  incoming_bitrate_.Update(payload_size, now_ms);

  const BandwidthUsage prior_state = estimator->detector.State();
  uint32_t timestamp_delta = 0;
  int64_t time_delta = 0;
  int size_delta = 0;
  if (estimator->inter_arrival.ComputeDeltas(
          rtp_timestamp, arrival_time_ms, now_ms, payload_size,
          &timestamp_delta, &time_delta, &size_delta)) {
    double timestamp_delta_ms = timestamp_delta * kTimestampToMs;
    estimator->estimator.Update(time_delta, timestamp_delta_ms, size_delta,
                                estimator->detector.State(), now_ms);
    estimator->detector.Detect(estimator->estimator.offset(),
                               timestamp_delta_ms,
                               estimator->estimator.num_of_deltas(), now_ms);
  }
  if (estimator->detector.State() == BandwidthUsage::kBwOverusing) {
    absl::optional<uint32_t> incoming_bitrate_bps =
        incoming_bitrate_.Rate(now_ms);
    if (incoming_bitrate_bps &&
        (prior_state != BandwidthUsage::kBwOverusing ||
         GetRemoteRate()->TimeToReduceFurther(
             Timestamp::Millis(now_ms),
             DataRate::BitsPerSec(*incoming_bitrate_bps)))) {
      // The first overuse should immediately trigger a new estimate.
      // We also have to update the estimate immediately if we are overusing
      // and the target bitrate is too high compared to what we are receiving.
      UpdateEstimate(now_ms);
    }
  }
}

TimeDelta RemoteBitrateEstimatorSingleStream::Process() {
  MutexLock lock(&mutex_);
  int64_t now_ms = clock_->TimeInMilliseconds();
  int64_t next_process_time_ms = last_process_time_ + process_interval_ms_;
  if (last_process_time_ == -1 || now_ms >= next_process_time_ms) {
    UpdateEstimate(now_ms);
    last_process_time_ = now_ms;
    return TimeDelta::Millis(process_interval_ms_);
  }

  return TimeDelta::Millis(next_process_time_ms - now_ms);
}

void RemoteBitrateEstimatorSingleStream::UpdateEstimate(int64_t now_ms) {
  BandwidthUsage bw_state = BandwidthUsage::kBwNormal;
  SsrcOveruseEstimatorMap::iterator it = overuse_detectors_.begin();
  while (it != overuse_detectors_.end()) {
    const int64_t time_of_last_received_packet =
        it->second->last_packet_time_ms;
    if (time_of_last_received_packet >= 0 &&
        now_ms - time_of_last_received_packet > kStreamTimeOutMs) {
      // This over-use detector hasn't received packets for `kStreamTimeOutMs`
      // milliseconds and is considered stale.
      delete it->second;
      overuse_detectors_.erase(it++);
    } else {
      // Make sure that we trigger an over-use if any of the over-use detectors
      // is detecting over-use.
      if (it->second->detector.State() > bw_state) {
        bw_state = it->second->detector.State();
      }
      ++it;
    }
  }
  // We can't update the estimate if we don't have any active streams.
  if (overuse_detectors_.empty()) {
    return;
  }
  AimdRateControl* remote_rate = GetRemoteRate();

  const RateControlInput input(
      bw_state, OptionalRateFromOptionalBps(incoming_bitrate_.Rate(now_ms)));
  uint32_t target_bitrate =
      remote_rate->Update(&input, Timestamp::Millis(now_ms)).bps<uint32_t>();
  if (remote_rate->ValidEstimate()) {
    process_interval_ms_ = remote_rate->GetFeedbackInterval().ms();
    RTC_DCHECK_GT(process_interval_ms_, 0);
    std::vector<uint32_t> ssrcs;
    GetSsrcs(&ssrcs);
    if (observer_)
      observer_->OnReceiveBitrateChanged(ssrcs, target_bitrate);
  }
}

void RemoteBitrateEstimatorSingleStream::OnRttUpdate(int64_t avg_rtt_ms,
                                                     int64_t max_rtt_ms) {
  MutexLock lock(&mutex_);
  GetRemoteRate()->SetRtt(TimeDelta::Millis(avg_rtt_ms));
}

void RemoteBitrateEstimatorSingleStream::RemoveStream(unsigned int ssrc) {
  MutexLock lock(&mutex_);
  SsrcOveruseEstimatorMap::iterator it = overuse_detectors_.find(ssrc);
  if (it != overuse_detectors_.end()) {
    delete it->second;
    overuse_detectors_.erase(it);
  }
}

DataRate RemoteBitrateEstimatorSingleStream::LatestEstimate() const {
  MutexLock lock(&mutex_);
  if (!remote_rate_->ValidEstimate() || overuse_detectors_.empty()) {
    return DataRate::Zero();
  }
  return remote_rate_->LatestEstimate();
}

void RemoteBitrateEstimatorSingleStream::GetSsrcs(
    std::vector<uint32_t>* ssrcs) const {
  RTC_DCHECK(ssrcs);
  ssrcs->resize(overuse_detectors_.size());
  int i = 0;
  for (SsrcOveruseEstimatorMap::const_iterator it = overuse_detectors_.begin();
       it != overuse_detectors_.end(); ++it, ++i) {
    (*ssrcs)[i] = it->first;
  }
}

AimdRateControl* RemoteBitrateEstimatorSingleStream::GetRemoteRate() {
  if (!remote_rate_)
    remote_rate_.reset(new AimdRateControl(&field_trials_));
  return remote_rate_.get();
}

}  // namespace webrtc
