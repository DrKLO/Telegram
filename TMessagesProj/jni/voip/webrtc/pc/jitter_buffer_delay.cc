/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/jitter_buffer_delay.h"

#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_checker.h"

namespace {
constexpr int kDefaultDelay = 0;
constexpr int kMaximumDelayMs = 10000;
}  // namespace

namespace webrtc {

JitterBufferDelay::JitterBufferDelay(rtc::Thread* worker_thread)
    : signaling_thread_(rtc::Thread::Current()), worker_thread_(worker_thread) {
  RTC_DCHECK(worker_thread_);
}

void JitterBufferDelay::OnStart(cricket::Delayable* media_channel,
                                uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(signaling_thread_);

  media_channel_ = media_channel;
  ssrc_ = ssrc;

  // Trying to apply cached delay for the audio stream.
  if (cached_delay_seconds_) {
    Set(cached_delay_seconds_.value());
  }
}

void JitterBufferDelay::OnStop() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  // Assume that audio stream is no longer present.
  media_channel_ = nullptr;
  ssrc_ = absl::nullopt;
}

void JitterBufferDelay::Set(absl::optional<double> delay_seconds) {
  RTC_DCHECK_RUN_ON(worker_thread_);

  // TODO(kuddai) propagate absl::optional deeper down as default preference.
  int delay_ms =
      rtc::saturated_cast<int>(delay_seconds.value_or(kDefaultDelay) * 1000);
  delay_ms = rtc::SafeClamp(delay_ms, 0, kMaximumDelayMs);

  cached_delay_seconds_ = delay_seconds;
  if (media_channel_ && ssrc_) {
    media_channel_->SetBaseMinimumPlayoutDelayMs(ssrc_.value(), delay_ms);
  }
}

}  // namespace webrtc
