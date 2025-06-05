/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/network_route.h"

namespace rtc {

bool RouteEndpoint::operator==(const RouteEndpoint& other) const {
  return adapter_type_ == other.adapter_type_ &&
         adapter_id_ == other.adapter_id_ && network_id_ == other.network_id_ &&
         uses_turn_ == other.uses_turn_;
}

bool NetworkRoute::operator==(const NetworkRoute& other) const {
  return connected == other.connected && local == other.local &&
         remote == other.remote && packet_overhead == other.packet_overhead &&
         last_sent_packet_id == other.last_sent_packet_id;
}

}  // namespace rtc
