/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/connection_info.h"

namespace cricket {

ConnectionInfo::ConnectionInfo()
    : best_connection(false),
      writable(false),
      receiving(false),
      timeout(false),
      new_connection(false),
      rtt(0),
      sent_discarded_bytes(0),
      sent_total_bytes(0),
      sent_bytes_second(0),
      sent_discarded_packets(0),
      sent_total_packets(0),
      sent_ping_requests_total(0),
      sent_ping_requests_before_first_response(0),
      sent_ping_responses(0),
      recv_total_bytes(0),
      recv_bytes_second(0),
      packets_received(0),
      recv_ping_requests(0),
      recv_ping_responses(0),
      key(nullptr),
      state(IceCandidatePairState::WAITING),
      priority(0),
      nominated(false),
      total_round_trip_time_ms(0) {}

ConnectionInfo::ConnectionInfo(const ConnectionInfo&) = default;

ConnectionInfo::~ConnectionInfo() = default;

}  // namespace cricket
