/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/timer/timer.h"

#include <algorithm>
#include <cstdint>
#include <limits>
#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "net/dcsctp/public/timeout.h"
#include "rtc_base/checks.h"

namespace dcsctp {
namespace {
TimeoutID MakeTimeoutId(TimerID timer_id, TimerGeneration generation) {
  return TimeoutID(static_cast<uint64_t>(*timer_id) << 32 | *generation);
}

DurationMs GetBackoffDuration(const TimerOptions& options,
                              DurationMs base_duration,
                              int expiration_count) {
  switch (options.backoff_algorithm) {
    case TimerBackoffAlgorithm::kFixed:
      return base_duration;
    case TimerBackoffAlgorithm::kExponential: {
      int32_t duration_ms = *base_duration;

      while (expiration_count > 0 && duration_ms < *Timer::kMaxTimerDuration) {
        duration_ms *= 2;
        --expiration_count;

        if (options.max_backoff_duration.has_value() &&
            duration_ms > **options.max_backoff_duration) {
          return *options.max_backoff_duration;
        }
      }

      return DurationMs(std::min(duration_ms, *Timer::kMaxTimerDuration));
    }
  }
}
}  // namespace

constexpr DurationMs Timer::kMaxTimerDuration;

Timer::Timer(TimerID id,
             absl::string_view name,
             OnExpired on_expired,
             UnregisterHandler unregister_handler,
             std::unique_ptr<Timeout> timeout,
             const TimerOptions& options)
    : id_(id),
      name_(name),
      options_(options),
      on_expired_(std::move(on_expired)),
      unregister_handler_(std::move(unregister_handler)),
      timeout_(std::move(timeout)),
      duration_(options.duration) {}

Timer::~Timer() {
  Stop();
  unregister_handler_();
}

void Timer::Start() {
  expiration_count_ = 0;
  if (!is_running()) {
    is_running_ = true;
    generation_ = TimerGeneration(*generation_ + 1);
    timeout_->Start(duration_, MakeTimeoutId(id_, generation_));
  } else {
    // Timer was running - stop and restart it, to make it expire in `duration_`
    // from now.
    generation_ = TimerGeneration(*generation_ + 1);
    timeout_->Restart(duration_, MakeTimeoutId(id_, generation_));
  }
}

void Timer::Stop() {
  if (is_running()) {
    timeout_->Stop();
    expiration_count_ = 0;
    is_running_ = false;
  }
}

void Timer::Trigger(TimerGeneration generation) {
  if (is_running_ && generation == generation_) {
    ++expiration_count_;
    is_running_ = false;
    if (!options_.max_restarts.has_value() ||
        expiration_count_ <= *options_.max_restarts) {
      // The timer should still be running after this triggers. Start a new
      // timer. Note that it might be very quickly restarted again, if the
      // `on_expired_` callback returns a new duration.
      is_running_ = true;
      DurationMs duration =
          GetBackoffDuration(options_, duration_, expiration_count_);
      generation_ = TimerGeneration(*generation_ + 1);
      timeout_->Start(duration, MakeTimeoutId(id_, generation_));
    }

    absl::optional<DurationMs> new_duration = on_expired_();
    if (new_duration.has_value() && new_duration != duration_) {
      duration_ = new_duration.value();
      if (is_running_) {
        // Restart it with new duration.
        timeout_->Stop();

        DurationMs duration =
            GetBackoffDuration(options_, duration_, expiration_count_);
        generation_ = TimerGeneration(*generation_ + 1);
        timeout_->Start(duration, MakeTimeoutId(id_, generation_));
      }
    }
  }
}

void TimerManager::HandleTimeout(TimeoutID timeout_id) {
  TimerID timer_id(*timeout_id >> 32);
  TimerGeneration generation(*timeout_id);
  auto it = timers_.find(timer_id);
  if (it != timers_.end()) {
    it->second->Trigger(generation);
  }
}

std::unique_ptr<Timer> TimerManager::CreateTimer(absl::string_view name,
                                                 Timer::OnExpired on_expired,
                                                 const TimerOptions& options) {
  next_id_ = TimerID(*next_id_ + 1);
  TimerID id = next_id_;
  // This would overflow after 4 billion timers created, which in SCTP would be
  // after 800 million reconnections on a single socket. Ensure this will never
  // happen.
  RTC_CHECK_NE(*id, std::numeric_limits<uint32_t>::max());
  std::unique_ptr<Timeout> timeout = create_timeout_();
  RTC_CHECK(timeout != nullptr);
  auto timer = absl::WrapUnique(new Timer(
      id, name, std::move(on_expired), [this, id]() { timers_.erase(id); },
      std::move(timeout), options));
  timers_[id] = timer.get();
  return timer;
}

}  // namespace dcsctp
