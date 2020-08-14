/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_CLUSTER_CREATED_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_CLUSTER_CREATED_H_

#include <stdint.h>

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventProbeClusterCreated final : public RtcEvent {
 public:
  RtcEventProbeClusterCreated(int32_t id,
                              int32_t bitrate_bps,
                              uint32_t min_probes,
                              uint32_t min_bytes);
  ~RtcEventProbeClusterCreated() override = default;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventProbeClusterCreated> Copy() const;

  int32_t id() const { return id_; }
  int32_t bitrate_bps() const { return bitrate_bps_; }
  uint32_t min_probes() const { return min_probes_; }
  uint32_t min_bytes() const { return min_bytes_; }

 private:
  RtcEventProbeClusterCreated(const RtcEventProbeClusterCreated& other);

  const int32_t id_;
  const int32_t bitrate_bps_;
  const uint32_t min_probes_;
  const uint32_t min_bytes_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_PROBE_CLUSTER_CREATED_H_
