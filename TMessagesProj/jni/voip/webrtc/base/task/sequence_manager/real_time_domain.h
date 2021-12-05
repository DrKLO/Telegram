// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_SEQUENCE_MANAGER_REAL_TIME_DOMAIN_H_
#define BASE_TASK_SEQUENCE_MANAGER_REAL_TIME_DOMAIN_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/task/sequence_manager/time_domain.h"

namespace base {
namespace sequence_manager {
namespace internal {

class BASE_EXPORT RealTimeDomain : public TimeDomain {
 public:
  RealTimeDomain();
  ~RealTimeDomain() override;

  // TimeDomain implementation:
  LazyNow CreateLazyNow() const override;
  TimeTicks Now() const override;
  Optional<TimeDelta> DelayTillNextTask(LazyNow* lazy_now) override;
  bool MaybeFastForwardToNextTask(bool quit_when_idle_requested) override;

 protected:
  void OnRegisterWithSequenceManager(
      SequenceManagerImpl* sequence_manager) override;
  const char* GetName() const override;

 private:
  const TickClock* tick_clock_ = nullptr;

  DISALLOW_COPY_AND_ASSIGN(RealTimeDomain);
};

}  // namespace internal
}  // namespace sequence_manager
}  // namespace base

#endif  // BASE_TASK_SEQUENCE_MANAGER_REAL_TIME_DOMAIN_H_
