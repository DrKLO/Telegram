// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/system/sys_info.h"

#include <algorithm>

#include "base/base_switches.h"
#include "base/bind.h"
#include "base/callback.h"
#include "base/command_line.h"
#include "base/location.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/system/sys_info_internal.h"
#include "base/task/post_task.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool.h"
#include "base/task_runner_util.h"
#include "base/time/time.h"
#include "build/build_config.h"

namespace base {
namespace {
static const int kLowMemoryDeviceThresholdMB = 512;
}  // namespace

// static
int64_t SysInfo::AmountOfPhysicalMemory() {
  if (base::CommandLine::ForCurrentProcess()->HasSwitch(
          switches::kEnableLowEndDeviceMode)) {
    return kLowMemoryDeviceThresholdMB * 1024 * 1024;
  }

  return AmountOfPhysicalMemoryImpl();
}

// static
int64_t SysInfo::AmountOfAvailablePhysicalMemory() {
  if (base::CommandLine::ForCurrentProcess()->HasSwitch(
          switches::kEnableLowEndDeviceMode)) {
    // Estimate the available memory by subtracting our memory used estimate
    // from the fake |kLowMemoryDeviceThresholdMB| limit.
    size_t memory_used =
        AmountOfPhysicalMemoryImpl() - AmountOfAvailablePhysicalMemoryImpl();
    size_t memory_limit = kLowMemoryDeviceThresholdMB * 1024 * 1024;
    // std::min ensures no underflow, as |memory_used| can be > |memory_limit|.
    return memory_limit - std::min(memory_used, memory_limit);
  }

  return AmountOfAvailablePhysicalMemoryImpl();
}

bool SysInfo::IsLowEndDevice() {
  if (base::CommandLine::ForCurrentProcess()->HasSwitch(
          switches::kEnableLowEndDeviceMode)) {
    return true;
  }

  return IsLowEndDeviceImpl();
}

#if !defined(OS_ANDROID)

bool DetectLowEndDevice() {
  CommandLine* command_line = CommandLine::ForCurrentProcess();
  if (command_line->HasSwitch(switches::kEnableLowEndDeviceMode))
    return true;
  if (command_line->HasSwitch(switches::kDisableLowEndDeviceMode))
    return false;

  int ram_size_mb = SysInfo::AmountOfPhysicalMemoryMB();
  return (ram_size_mb > 0 && ram_size_mb <= kLowMemoryDeviceThresholdMB);
}

// static
bool SysInfo::IsLowEndDeviceImpl() {
  static base::NoDestructor<
      internal::LazySysInfoValue<bool, DetectLowEndDevice>>
      instance;
  return instance->value();
}
#endif

#if !defined(OS_MACOSX) && !defined(OS_ANDROID)
std::string SysInfo::HardwareModelName() {
  return std::string();
}
#endif

void SysInfo::GetHardwareInfo(base::OnceCallback<void(HardwareInfo)> callback) {
#if defined(OS_WIN)
  // On Windows the calls to GetHardwareInfoSync can take a really long time to
  // complete as they depend on WMI, using the CONTINUE_ON_SHUTDOWN traits will
  // prevent this task from blocking shutdown.
  base::PostTaskAndReplyWithResult(
      base::ThreadPool::CreateCOMSTATaskRunner(
          {TaskShutdownBehavior::CONTINUE_ON_SHUTDOWN})
          .get(),
      FROM_HERE, base::BindOnce(&GetHardwareInfoSync), std::move(callback));
#elif defined(OS_ANDROID) || defined(OS_MACOSX)
  base::ThreadPool::PostTaskAndReplyWithResult(
      FROM_HERE, {}, base::BindOnce(&GetHardwareInfoSync), std::move(callback));
#elif defined(OS_LINUX)
  base::ThreadPool::PostTaskAndReplyWithResult(
      FROM_HERE, {base::MayBlock()}, base::BindOnce(&GetHardwareInfoSync),
      std::move(callback));
#else
  NOTIMPLEMENTED();
  base::ThreadPool::PostTask(
      FROM_HERE, {}, base::BindOnce(std::move(callback), HardwareInfo()));
#endif
}

// static
base::TimeDelta SysInfo::Uptime() {
  // This code relies on an implementation detail of TimeTicks::Now() - that
  // its return value happens to coincide with the system uptime value in
  // microseconds, on Win/Mac/iOS/Linux/ChromeOS and Android.
  int64_t uptime_in_microseconds = TimeTicks::Now().ToInternalValue();
  return base::TimeDelta::FromMicroseconds(uptime_in_microseconds);
}

}  // namespace base
