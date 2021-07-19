// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_POWER_MONITOR_POWER_MONITOR_H_
#define BASE_POWER_MONITOR_POWER_MONITOR_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/no_destructor.h"
#include "base/observer_list_threadsafe.h"
#include "base/power_monitor/power_observer.h"

namespace base {

class PowerMonitorSource;

// A class used to monitor the power state change and notify the observers about
// the change event. The threading model of this class is as follows:
// Once initialized, it is threadsafe. However, the client must ensure that
// initialization happens before any other methods are invoked, including
// IsInitialized(). IsInitialized() exists only as a convenience for detection
// of test contexts where the PowerMonitor global is never created.
class BASE_EXPORT PowerMonitor {
 public:
  // Initializes global PowerMonitor state. Takes ownership of |source|, which
  // will be leaked on process teardown. May only be called once. Not threadsafe
  // - no other PowerMonitor methods may be called on any thread while calling
  // Initialize(). |source| must not be nullptr.
  static void Initialize(std::unique_ptr<PowerMonitorSource> source);

  // Returns true if Initialize() has been called. Safe to call on any thread,
  // but must not be called while Initialize() or ShutdownForTesting() is being
  // invoked.
  static bool IsInitialized();

  // Add and remove an observer.
  // Can be called from any thread. |observer| is notified on the sequence
  // from which it was registered.
  // Must not be called from within a notification callback.
  //
  // AddObserver() fails and returns false if PowerMonitor::Initialize() has not
  // been invoked. Failure should only happen in unit tests, where the
  // PowerMonitor is generally not initialized. It is safe to call
  // RemoveObserver with a PowerObserver that was not successfully added as an
  // observer.
  static bool AddObserver(PowerObserver* observer);
  static void RemoveObserver(PowerObserver* observer);

  // Is the computer currently on battery power. May only be called if the
  // PowerMonitor has been initialized.
  static bool IsOnBatteryPower();

  // Uninitializes the PowerMonitor. Should be called at the end of any unit
  // test that mocks out the PowerMonitor, to avoid affecting subsequent tests.
  // There must be no live PowerObservers when invoked. Safe to call even if the
  // PowerMonitor hasn't been initialized.
  static void ShutdownForTesting();

 private:
  friend class PowerMonitorSource;
  friend class base::NoDestructor<PowerMonitor>;

  PowerMonitor();
  ~PowerMonitor();

  static PowerMonitorSource* Source();

  static void NotifyPowerStateChange(bool battery_in_use);
  static void NotifySuspend();
  static void NotifyResume();

  static PowerMonitor* GetInstance();

  scoped_refptr<ObserverListThreadSafe<PowerObserver>> observers_;
  std::unique_ptr<PowerMonitorSource> source_;

  DISALLOW_COPY_AND_ASSIGN(PowerMonitor);
};

}  // namespace base

#endif  // BASE_POWER_MONITOR_POWER_MONITOR_H_
