/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TIMER_FAKE_TIMEOUT_H_
#define NET_DCSCTP_TIMER_FAKE_TIMEOUT_H_

#include <cstdint>
#include <functional>
#include <limits>
#include <memory>
#include <unordered_set>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "net/dcsctp/public/timeout.h"
#include "rtc_base/checks.h"

namespace dcsctp {

// A timeout used in tests.
class FakeTimeout : public Timeout {
 public:
  explicit FakeTimeout(std::function<TimeMs()> get_time,
                       std::function<void(FakeTimeout*)> on_delete)
      : get_time_(std::move(get_time)), on_delete_(std::move(on_delete)) {}

  ~FakeTimeout() override { on_delete_(this); }

  void Start(DurationMs duration_ms, TimeoutID timeout_id) override {
    RTC_DCHECK(expiry_ == TimeMs::InfiniteFuture());
    timeout_id_ = timeout_id;
    expiry_ = get_time_() + duration_ms;
  }
  void Stop() override {
    RTC_DCHECK(expiry_ != TimeMs::InfiniteFuture());
    expiry_ = TimeMs::InfiniteFuture();
  }

  bool EvaluateHasExpired(TimeMs now) {
    if (now >= expiry_) {
      expiry_ = TimeMs::InfiniteFuture();
      return true;
    }
    return false;
  }

  TimeoutID timeout_id() const { return timeout_id_; }

 private:
  const std::function<TimeMs()> get_time_;
  const std::function<void(FakeTimeout*)> on_delete_;

  TimeoutID timeout_id_ = TimeoutID(0);
  TimeMs expiry_ = TimeMs::InfiniteFuture();
};

class FakeTimeoutManager {
 public:
  // The `get_time` function must return the current time, relative to any
  // epoch.
  explicit FakeTimeoutManager(std::function<TimeMs()> get_time)
      : get_time_(std::move(get_time)) {}

  std::unique_ptr<Timeout> CreateTimeout() {
    auto timer = std::make_unique<FakeTimeout>(
        get_time_, [this](FakeTimeout* timer) { timers_.erase(timer); });
    timers_.insert(timer.get());
    return timer;
  }

  // NOTE: This can't return a vector, as calling EvaluateHasExpired requires
  // calling socket->HandleTimeout directly afterwards, as the owning Timer
  // still believes it's running, and it needs to be updated to set
  // Timer::is_running_ to false before you operate on the Timer or Timeout
  // again.
  absl::optional<TimeoutID> GetNextExpiredTimeout() {
    TimeMs now = get_time_();
    std::vector<TimeoutID> expired_timers;
    for (auto& timer : timers_) {
      if (timer->EvaluateHasExpired(now)) {
        return timer->timeout_id();
      }
    }
    return absl::nullopt;
  }

 private:
  const std::function<TimeMs()> get_time_;
  std::unordered_set<FakeTimeout*> timers_;
};

}  // namespace dcsctp

#endif  // NET_DCSCTP_TIMER_FAKE_TIMEOUT_H_
