/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_PLAYOUT_H_
#define LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_PLAYOUT_H_

#include <stdint.h>

#include <memory>

#include "api/rtc_event_log/rtc_event.h"

namespace webrtc {

class RtcEventAudioPlayout final : public RtcEvent {
 public:
  explicit RtcEventAudioPlayout(uint32_t ssrc);
  ~RtcEventAudioPlayout() override = default;

  Type GetType() const override;

  bool IsConfigEvent() const override;

  std::unique_ptr<RtcEventAudioPlayout> Copy() const;

  uint32_t ssrc() const { return ssrc_; }

 private:
  RtcEventAudioPlayout(const RtcEventAudioPlayout& other);

  const uint32_t ssrc_;
};

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_EVENTS_RTC_EVENT_AUDIO_PLAYOUT_H_
