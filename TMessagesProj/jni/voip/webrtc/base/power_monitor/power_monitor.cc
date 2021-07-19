// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/power_monitor/power_monitor.h"

#include <utility>

#include "base/power_monitor/power_monitor_source.h"
#include "base/trace_event/trace_event.h"

namespace base {

void PowerMonitor::Initialize(std::unique_ptr<PowerMonitorSource> source) {
  DCHECK(!IsInitialized());
  GetInstance()->source_ = std::move(source);
}

bool PowerMonitor::IsInitialized() {
  return GetInstance()->source_.get() != nullptr;
}

bool PowerMonitor::AddObserver(PowerObserver* obs) {
  PowerMonitor* power_monitor = GetInstance();
  if (!IsInitialized())
    return false;
  power_monitor->observers_->AddObserver(obs);
  return true;
}

void PowerMonitor::RemoveObserver(PowerObserver* obs) {
  GetInstance()->observers_->RemoveObserver(obs);
}

PowerMonitorSource* PowerMonitor::Source() {
  return GetInstance()->source_.get();
}

bool PowerMonitor::IsOnBatteryPower() {
  DCHECK(IsInitialized());
  return GetInstance()->source_->IsOnBatteryPower();
}

void PowerMonitor::ShutdownForTesting() {
  PowerMonitor::GetInstance()->observers_->AssertEmpty();
  GetInstance()->source_ = nullptr;
}

void PowerMonitor::NotifyPowerStateChange(bool battery_in_use) {
  DCHECK(IsInitialized());
  DVLOG(1) << "PowerStateChange: " << (battery_in_use ? "On" : "Off")
           << " battery";
  GetInstance()->observers_->Notify(
      FROM_HERE, &PowerObserver::OnPowerStateChange, battery_in_use);
}

void PowerMonitor::NotifySuspend() {
  DCHECK(IsInitialized());
  TRACE_EVENT_INSTANT0("base", "PowerMonitor::NotifySuspend",
                       TRACE_EVENT_SCOPE_GLOBAL);
  DVLOG(1) << "Power Suspending";
  GetInstance()->observers_->Notify(FROM_HERE, &PowerObserver::OnSuspend);
}

void PowerMonitor::NotifyResume() {
  DCHECK(IsInitialized());
  TRACE_EVENT_INSTANT0("base", "PowerMonitor::NotifyResume",
                       TRACE_EVENT_SCOPE_GLOBAL);
  DVLOG(1) << "Power Resuming";
  GetInstance()->observers_->Notify(FROM_HERE, &PowerObserver::OnResume);
}

PowerMonitor* PowerMonitor::GetInstance() {
  static base::NoDestructor<PowerMonitor> power_monitor;
  return power_monitor.get();
}

PowerMonitor::PowerMonitor()
    : observers_(
          base::MakeRefCounted<ObserverListThreadSafe<PowerObserver>>()) {}

PowerMonitor::~PowerMonitor() = default;

}  // namespace base
