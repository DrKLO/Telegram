/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VOIP_VOIP_STATISTICS_H_
#define API_VOIP_VOIP_STATISTICS_H_

#include "api/neteq/neteq.h"
#include "api/voip/voip_base.h"

namespace webrtc {

struct IngressStatistics {
  // Stats included from api/neteq/neteq.h.
  NetEqLifetimeStatistics neteq_stats;

  // Represents the total duration in seconds of all samples that have been
  // received.
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-totalsamplesduration
  double total_duration = 0.0;
};

// VoipStatistics interface provides the interfaces for querying metrics around
// the jitter buffer (NetEq) performance.
class VoipStatistics {
 public:
  // Gets the audio ingress statistics. Returns absl::nullopt when channel_id is
  // invalid.
  virtual absl::optional<IngressStatistics> GetIngressStatistics(
      ChannelId channel_id) = 0;

 protected:
  virtual ~VoipStatistics() = default;
};

}  // namespace webrtc

#endif  // API_VOIP_VOIP_STATISTICS_H_
