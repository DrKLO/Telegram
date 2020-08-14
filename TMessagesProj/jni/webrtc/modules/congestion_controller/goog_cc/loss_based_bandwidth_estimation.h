/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_CONGESTION_CONTROLLER_GOOG_CC_LOSS_BASED_BANDWIDTH_ESTIMATION_H_
#define MODULES_CONGESTION_CONTROLLER_GOOG_CC_LOSS_BASED_BANDWIDTH_ESTIMATION_H_

#include <vector>

#include "api/transport/network_types.h"
#include "api/units/data_rate.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

struct LossBasedControlConfig {
  LossBasedControlConfig();
  LossBasedControlConfig(const LossBasedControlConfig&);
  LossBasedControlConfig& operator=(const LossBasedControlConfig&) = default;
  ~LossBasedControlConfig();
  bool enabled;
  FieldTrialParameter<double> min_increase_factor;
  FieldTrialParameter<double> max_increase_factor;
  FieldTrialParameter<TimeDelta> increase_low_rtt;
  FieldTrialParameter<TimeDelta> increase_high_rtt;
  FieldTrialParameter<double> decrease_factor;
  FieldTrialParameter<TimeDelta> loss_window;
  FieldTrialParameter<TimeDelta> loss_max_window;
  FieldTrialParameter<TimeDelta> acknowledged_rate_max_window;
  FieldTrialParameter<DataRate> increase_offset;
  FieldTrialParameter<DataRate> loss_bandwidth_balance_increase;
  FieldTrialParameter<DataRate> loss_bandwidth_balance_decrease;
  FieldTrialParameter<double> loss_bandwidth_balance_exponent;
  FieldTrialParameter<bool> allow_resets;
  FieldTrialParameter<TimeDelta> decrease_interval;
  FieldTrialParameter<TimeDelta> loss_report_timeout;
};

class LossBasedBandwidthEstimation {
 public:
  LossBasedBandwidthEstimation();
  void Update(Timestamp at_time,
              DataRate min_bitrate,
              TimeDelta last_round_trip_time);
  void UpdateAcknowledgedBitrate(DataRate acknowledged_bitrate,
                                 Timestamp at_time);
  void MaybeReset(DataRate bitrate);
  void SetInitialBitrate(DataRate bitrate);
  bool Enabled() const { return config_.enabled; }
  void UpdateLossStatistics(const std::vector<PacketResult>& packet_results,
                            Timestamp at_time);
  DataRate GetEstimate() const { return loss_based_bitrate_; }

 private:
  friend class GoogCcStatePrinter;
  void Reset(DataRate bitrate);
  double loss_increase_threshold() const;
  double loss_decrease_threshold() const;
  DataRate decreased_bitrate() const;

  LossBasedControlConfig config_;
  double average_loss_;
  double average_loss_max_;
  DataRate loss_based_bitrate_;
  DataRate acknowledged_bitrate_max_;
  Timestamp acknowledged_bitrate_last_update_;
  Timestamp time_last_decrease_;
  bool has_decreased_since_last_loss_report_;
  Timestamp last_loss_packet_report_;
  double last_loss_ratio_;
};

}  // namespace webrtc

#endif  // MODULES_CONGESTION_CONTROLLER_GOOG_CC_LOSS_BASED_BANDWIDTH_ESTIMATION_H_
