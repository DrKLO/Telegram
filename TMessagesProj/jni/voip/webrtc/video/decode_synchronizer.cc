/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/decode_synchronizer.h"

#include <iterator>
#include <memory>
#include <utility>
#include <vector>

#include "api/sequence_checker.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "video/frame_decode_scheduler.h"
#include "video/frame_decode_timing.h"

namespace webrtc {

DecodeSynchronizer::ScheduledFrame::ScheduledFrame(
    uint32_t rtp_timestamp,
    FrameDecodeTiming::FrameSchedule schedule,
    FrameDecodeScheduler::FrameReleaseCallback callback)
    : rtp_timestamp_(rtp_timestamp),
      schedule_(std::move(schedule)),
      callback_(std::move(callback)) {}

void DecodeSynchronizer::ScheduledFrame::RunFrameReleaseCallback() && {
  // Inspiration from Chromium base::OnceCallback. Move `*this` to a local
  // before execution to ensure internal state is cleared after callback
  // execution.
  auto sf = std::move(*this);
  std::move(sf.callback_)(sf.rtp_timestamp_, sf.schedule_.render_time);
}

Timestamp DecodeSynchronizer::ScheduledFrame::LatestDecodeTime() const {
  return schedule_.latest_decode_time;
}

DecodeSynchronizer::SynchronizedFrameDecodeScheduler::
    SynchronizedFrameDecodeScheduler(DecodeSynchronizer* sync)
    : sync_(sync) {
  RTC_DCHECK(sync_);
}

DecodeSynchronizer::SynchronizedFrameDecodeScheduler::
    ~SynchronizedFrameDecodeScheduler() {
  RTC_DCHECK(!next_frame_);
  RTC_DCHECK(stopped_);
}

absl::optional<uint32_t>
DecodeSynchronizer::SynchronizedFrameDecodeScheduler::ScheduledRtpTimestamp() {
  return next_frame_.has_value()
             ? absl::make_optional(next_frame_->rtp_timestamp())
             : absl::nullopt;
}

DecodeSynchronizer::ScheduledFrame
DecodeSynchronizer::SynchronizedFrameDecodeScheduler::ReleaseNextFrame() {
  RTC_DCHECK(next_frame_);
  auto res = std::move(*next_frame_);
  next_frame_.reset();
  return res;
}

Timestamp
DecodeSynchronizer::SynchronizedFrameDecodeScheduler::LatestDecodeTime() {
  RTC_DCHECK(next_frame_);
  return next_frame_->LatestDecodeTime();
}

void DecodeSynchronizer::SynchronizedFrameDecodeScheduler::ScheduleFrame(
    uint32_t rtp,
    FrameDecodeTiming::FrameSchedule schedule,
    FrameReleaseCallback cb) {
  RTC_DCHECK(!next_frame_) << "Can not schedule two frames at once.";
  next_frame_ = ScheduledFrame(rtp, std::move(schedule), std::move(cb));
  sync_->OnFrameScheduled(this);
}

void DecodeSynchronizer::SynchronizedFrameDecodeScheduler::CancelOutstanding() {
  next_frame_.reset();
}

void DecodeSynchronizer::SynchronizedFrameDecodeScheduler::Stop() {
  CancelOutstanding();
  stopped_ = true;
  sync_->RemoveFrameScheduler(this);
}

DecodeSynchronizer::DecodeSynchronizer(Clock* clock,
                                       Metronome* metronome,
                                       TaskQueueBase* worker_queue)
    : clock_(clock), worker_queue_(worker_queue), metronome_(metronome) {
  RTC_DCHECK(metronome_);
  RTC_DCHECK(worker_queue_);
}

DecodeSynchronizer::~DecodeSynchronizer() {
  RTC_DCHECK(schedulers_.empty());
}

std::unique_ptr<FrameDecodeScheduler>
DecodeSynchronizer::CreateSynchronizedFrameScheduler() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  auto scheduler = std::make_unique<SynchronizedFrameDecodeScheduler>(this);
  auto [it, inserted] = schedulers_.emplace(scheduler.get());
  // If this is the first `scheduler` added, start listening to the metronome.
  if (inserted && schedulers_.size() == 1) {
    RTC_DLOG(LS_VERBOSE) << "Listening to metronome";
    metronome_->AddListener(this);
  }

  return std::move(scheduler);
}

void DecodeSynchronizer::OnFrameScheduled(
    SynchronizedFrameDecodeScheduler* scheduler) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  RTC_DCHECK(scheduler->ScheduledRtpTimestamp());

  Timestamp now = clock_->CurrentTime();
  Timestamp next_tick = expected_next_tick_;
  // If no tick has registered yet assume it will occur in the tick period.
  if (next_tick.IsInfinite()) {
    next_tick = now + metronome_->TickPeriod();
  }

  // Release the frame right away if the decode time is too soon. Otherwise
  // the stream may fall behind too much.
  bool decode_before_next_tick =
      scheduler->LatestDecodeTime() <
      (next_tick - FrameDecodeTiming::kMaxAllowedFrameDelay);
  // Decode immediately if the decode time is in the past.
  bool decode_time_in_past = scheduler->LatestDecodeTime() < now;

  if (decode_before_next_tick || decode_time_in_past) {
    ScheduledFrame scheduled_frame = scheduler->ReleaseNextFrame();
    std::move(scheduled_frame).RunFrameReleaseCallback();
  }
}

void DecodeSynchronizer::RemoveFrameScheduler(
    SynchronizedFrameDecodeScheduler* scheduler) {
  RTC_DCHECK_RUN_ON(worker_queue_);
  RTC_DCHECK(scheduler);
  auto it = schedulers_.find(scheduler);
  if (it == schedulers_.end()) {
    return;
  }
  schedulers_.erase(it);
  // If there are no more schedulers active, stop listening for metronome ticks.
  if (schedulers_.empty()) {
    RTC_DLOG(LS_VERBOSE) << "Not listening to metronome";
    metronome_->RemoveListener(this);
    expected_next_tick_ = Timestamp::PlusInfinity();
  }
}

void DecodeSynchronizer::OnTick() {
  RTC_DCHECK_RUN_ON(worker_queue_);
  expected_next_tick_ = clock_->CurrentTime() + metronome_->TickPeriod();

  for (auto* scheduler : schedulers_) {
    if (scheduler->ScheduledRtpTimestamp() &&
        scheduler->LatestDecodeTime() < expected_next_tick_) {
      auto scheduled_frame = scheduler->ReleaseNextFrame();
      std::move(scheduled_frame).RunFrameReleaseCallback();
    }
  }
}

TaskQueueBase* DecodeSynchronizer::OnTickTaskQueue() {
  return worker_queue_;
}

}  // namespace webrtc
