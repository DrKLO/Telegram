/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/tx/retransmission_timeout.h"

#include <algorithm>
#include <cstdint>

#include "api/units/time_delta.h"
#include "net/dcsctp/public/dcsctp_options.h"

namespace dcsctp {

RetransmissionTimeout::RetransmissionTimeout(const DcSctpOptions& options)
    : min_rto_(options.rto_min.ToTimeDelta()),
      max_rto_(options.rto_max.ToTimeDelta()),
      max_rtt_(options.rtt_max.ToTimeDelta()),
      min_rtt_variance_(*options.min_rtt_variance),
      scaled_srtt_(*options.rto_initial << kRttShift),
      rto_(*options.rto_initial) {}

void RetransmissionTimeout::ObserveRTT(webrtc::TimeDelta measured_rtt) {
  // Unrealistic values will be skipped. If a wrongly measured (or otherwise
  // corrupt) value was processed, it could change the state in a way that would
  // take a very long time to recover.
  if (measured_rtt < webrtc::TimeDelta::Zero() || measured_rtt > max_rtt_) {
    return;
  }

  const int64_t rtt = measured_rtt.ms();

  // From https://tools.ietf.org/html/rfc4960#section-6.3.1, but avoiding
  // floating point math by implementing algorithm from "V. Jacobson: Congestion
  // avoidance and control", but adapted for SCTP.
  if (first_measurement_) {
    scaled_srtt_ = rtt << kRttShift;
    scaled_rtt_var_ = (rtt / 2) << kRttVarShift;
    first_measurement_ = false;
  } else {
    int64_t rtt_diff = rtt - (scaled_srtt_ >> kRttShift);
    scaled_srtt_ += rtt_diff;
    if (rtt_diff < 0) {
      rtt_diff = -rtt_diff;
    }
    rtt_diff -= (scaled_rtt_var_ >> kRttVarShift);
    scaled_rtt_var_ += rtt_diff;
  }

  if (scaled_rtt_var_ < min_rtt_variance_) {
    scaled_rtt_var_ = min_rtt_variance_;
  }

  rto_ = (scaled_srtt_ >> kRttShift) + scaled_rtt_var_;

  // Clamp RTO between min and max.
  rto_ = std::min(std::max(rto_, min_rto_.ms()), max_rto_.ms());
}
}  // namespace dcsctp
