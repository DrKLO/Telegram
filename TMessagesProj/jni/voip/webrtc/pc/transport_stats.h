/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TRANSPORT_STATS_H_
#define PC_TRANSPORT_STATS_H_

#include <string>
#include <vector>

#include "api/dtls_transport_interface.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/port.h"
#include "rtc_base/ssl_stream_adapter.h"

namespace cricket {

struct TransportChannelStats {
  TransportChannelStats();
  TransportChannelStats(const TransportChannelStats&);
  ~TransportChannelStats();

  int component = 0;
  int ssl_version_bytes = 0;
  int srtp_crypto_suite = rtc::kSrtpInvalidCryptoSuite;
  int ssl_cipher_suite = rtc::kTlsNullWithNullNull;
  webrtc::DtlsTransportState dtls_state = webrtc::DtlsTransportState::kNew;
  IceTransportStats ice_transport_stats;
};

// Information about all the channels of a transport.
// TODO(hta): Consider if a simple vector is as good as a map.
typedef std::vector<TransportChannelStats> TransportChannelStatsList;

// Information about the stats of a transport.
struct TransportStats {
  std::string transport_name;
  TransportChannelStatsList channel_stats;
};

}  // namespace cricket

#endif  // PC_TRANSPORT_STATS_H_
