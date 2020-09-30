// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_LAZY_NOW_H_
#define BASE_TASK_SEQUENCE_MANAGER_LAZY_NOW_H_

#include "base/base_export.h"
#include "base/optional.h"
#include "base/time/time.h"

namespace base {

class TickClock;

namespace sequence_manager {

// Now() is somewhat expensive so it makes sense not to call Now() unless we
// really need to and to avoid subsequent calls if already called once.
// LazyNow objects are expected to be short-living to represent accurate time.
class BASE_EXPORT LazyNow {
 public:
  explicit LazyNow(TimeTicks now);
  explicit LazyNow(const TickClock* tick_clock);

  LazyNow(LazyNow&& move_from) noexcept;

  // Result will not be updated on any subsesequent calls.
  TimeTicks Now();

  bool has_value() const { return !!now_; }

 private:
  const TickClock* tick_clock_;  // Not owned.
  Optional<TimeTicks> now_;

  DISALLOW_COPY_AND_ASSIGN(LazyNow);
};

}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_LAZY_NOW_H_
