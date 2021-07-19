// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_POWER_MONITOR_POWER_MONITOR_DEVICE_SOURCE_H_
#define BASE_POWER_MONITOR_POWER_MONITOR_DEVICE_SOURCE_H_

#include "base/base_export.h"
#include "base/macros.h"
#include "base/power_monitor/power_monitor_source.h"
#include "base/power_monitor/power_observer.h"
#include "build/build_config.h"

#if defined(OS_WIN)
#include <windows.h>
#endif  // !OS_WIN

#if defined(OS_MACOSX) && !defined(OS_IOS)
#include <IOKit/IOTypes.h>

#include "base/mac/scoped_cftyperef.h"
#include "base/mac/scoped_ionotificationportref.h"
#endif

#if defined(OS_IOS)
#include <objc/runtime.h>
#endif  // OS_IOS

namespace base {

// A class used to monitor the power state change and notify the observers about
// the change event.
class BASE_EXPORT PowerMonitorDeviceSource : public PowerMonitorSource {
 public:
  PowerMonitorDeviceSource();
  ~PowerMonitorDeviceSource() override;

#if defined(OS_CHROMEOS)
  // On Chrome OS, Chrome receives power-related events from powerd, the system
  // power daemon, via D-Bus signals received on the UI thread. base can't
  // directly depend on that code, so this class instead exposes static methods
  // so that events can be passed in.
  static void SetPowerSource(bool on_battery);
  static void HandleSystemSuspending();
  static void HandleSystemResumed();
#endif

 private:
#if defined(OS_WIN)
  // Represents a message-only window for power message handling on Windows.
  // Only allow PowerMonitor to create it.
  class PowerMessageWindow {
   public:
    PowerMessageWindow();
    ~PowerMessageWindow();

   private:
    static LRESULT CALLBACK WndProcThunk(HWND hwnd,
                                         UINT message,
                                         WPARAM wparam,
                                         LPARAM lparam);
    // Instance of the module containing the window procedure.
    HMODULE instance_;
    // A hidden message-only window.
    HWND message_hwnd_;
  };
#endif  // OS_WIN

#if defined(OS_MACOSX)
  void PlatformInit();
  void PlatformDestroy();

#if !defined(OS_IOS)
  // Callback from IORegisterForSystemPower(). |refcon| is the |this| pointer.
  static void SystemPowerEventCallback(void* refcon,
                                       io_service_t service,
                                       natural_t message_type,
                                       void* message_argument);
#endif  // !OS_IOS
#endif  // OS_MACOSX

  // Platform-specific method to check whether the system is currently
  // running on battery power.  Returns true if running on batteries,
  // false otherwise.
  bool IsOnBatteryPowerImpl() override;

#if defined(OS_MACOSX) && !defined(OS_IOS)
  // Reference to the system IOPMrootDomain port.
  io_connect_t power_manager_port_ = IO_OBJECT_NULL;

  // Notification port that delivers power (sleep/wake) notifications.
  mac::ScopedIONotificationPortRef notification_port_;

  // Notifier reference for the |notification_port_|.
  io_object_t notifier_ = IO_OBJECT_NULL;

  // Run loop source to observe power-source-change events.
  ScopedCFTypeRef<CFRunLoopSourceRef> power_source_run_loop_source_;
#endif

#if defined(OS_IOS)
  // Holds pointers to system event notification observers.
  std::vector<id> notification_observers_;
#endif

#if defined(OS_WIN)
  PowerMessageWindow power_message_window_;
#endif

  DISALLOW_COPY_AND_ASSIGN(PowerMonitorDeviceSource);
};

}  // namespace base

#endif  // BASE_POWER_MONITOR_POWER_MONITOR_DEVICE_SOURCE_H_
