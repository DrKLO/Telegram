// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/sequence_manager/lazy_now.h"

#include "base/time/tick_clock.h"

namespace base {
namespace sequence_manager {

LazyNow::LazyNow(TimeTicks now) : tick_clock_(nullptr), now_(now) {}

LazyNow::LazyNow(const TickClock* tick_clock)
    : tick_clock_(tick_clock), now_() {
  DCHECK(tick_clock);
}

LazyNow::LazyNow(LazyNow&& move_from) noexcept
    : tick_clock_(move_from.tick_clock_), now_(move_from.now_) {
  move_from.tick_clock_ = nullptr;
  move_from.now_ = nullopt;
}

TimeTicks LazyNow::Now() {
  // It looks tempting to avoid using Optional and to rely on is_null() instead,
  // but in some test environments clock intentionally starts from zero.
  if (!now_) {
    DCHECK(tick_clock_);  // It can fire only on use after std::move.
    now_ = tick_clock_->NowTicks();
  }
  return *now_;
}

}  // namespace sequence_manager
}  // namespace base
