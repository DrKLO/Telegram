/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_RETRANSMISSION_TIMEOUT_H_
#define NET_DCSCTP_TX_RETRANSMISSION_TIMEOUT_H_

#include <cstdint>
#include <functional>

#include "net/dcsctp/public/dcsctp_options.h"

namespace dcsctp {

// Manages updating of the Retransmission Timeout (RTO) SCTP variable, which is
// used directly as the base timeout for T3-RTX and for other timers, such as
// delayed ack.
//
// When a round-trip-time (RTT) is calculated (outside this class), `Observe`
// is called, which calculates the retransmission timeout (RTO) value. The RTO
// value will become larger if the RTT is high and/or the RTT values are varying
// a lot, which is an indicator of a bad connection.
class RetransmissionTimeout {
 public:
  static constexpr int kRttShift = 3;
  static constexpr int kRttVarShift = 2;
  explicit RetransmissionTimeout(const DcSctpOptions& options);

  // To be called when a RTT has been measured, to update the RTO value.
  void ObserveRTT(DurationMs measured_rtt);

  // Returns the Retransmission Timeout (RTO) value, in milliseconds.
  DurationMs rto() const { return DurationMs(rto_); }

  // Returns the smoothed RTT value, in milliseconds.
  DurationMs srtt() const { return DurationMs(scaled_srtt_ >> kRttShift); }

 private:
  const int32_t min_rto_;
  const int32_t max_rto_;
  const int32_t max_rtt_;
  const int32_t min_rtt_variance_;
  // If this is the first measurement
  bool first_measurement_ = true;
  // Smoothed Round-Trip Time, shifted by kRttShift
  int32_t scaled_srtt_;
  // Round-Trip Time Variation, shifted by kRttVarShift
  int32_t scaled_rtt_var_ = 0;
  // Retransmission Timeout
  int32_t rto_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_TX_RETRANSMISSION_TIMEOUT_H_
