// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/test/mock_time_domain.h"

namespace base {
namespace sequence_manager {

MockTimeDomain::MockTimeDomain(TimeTicks initial_now_ticks)
    : now_ticks_(initial_now_ticks) {}

MockTimeDomain::~MockTimeDomain() = default;

LazyNow MockTimeDomain::CreateLazyNow() const {
  return LazyNow(now_ticks_);
}

TimeTicks MockTimeDomain::Now() const {
  return now_ticks_;
}

void MockTimeDomain::SetNowTicks(TimeTicks now_ticks) {
  now_ticks_ = now_ticks;
}

Optional<TimeDelta> MockTimeDomain::DelayTillNextTask(LazyNow* lazy_now) {
  return nullopt;
}

bool MockTimeDomain::MaybeFastForwardToNextTask(bool quit_when_idle_requested) {
  return false;
}

void MockTimeDomain::SetNextDelayedDoWork(LazyNow* lazy_now,
                                          TimeTicks run_time) {}

const char* MockTimeDomain::GetName() const {
  return "MockTimeDomain";
}

}  // namespace sequence_manager
}  // namespace base
