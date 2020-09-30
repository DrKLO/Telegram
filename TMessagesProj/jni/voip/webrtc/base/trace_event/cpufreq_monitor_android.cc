// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/cpufreq_monitor_android.h"

#include <fcntl.h>

#include "base/atomicops.h"
#include "base/bind.h"
#include "base/files/file_util.h"
#include "base/files/scoped_file.h"
#include "base/memory/scoped_refptr.h"
#include "base/no_destructor.h"
#include "base/strings/string_number_conversions.h"
#include "base/strings/string_split.h"
#include "base/strings/stringprintf.h"
#include "base/task/post_task.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool.h"
#include "base/trace_event/trace_event.h"

namespace base {

namespace trace_event {

namespace {

const size_t kNumBytesToReadForSampling = 32;
constexpr const char kTraceCategory[] = TRACE_DISABLED_BY_DEFAULT("power");
const char kEventTitle[] = "CPU Frequency";

}  // namespace

CPUFreqMonitorDelegate::CPUFreqMonitorDelegate() {}

std::string CPUFreqMonitorDelegate::GetScalingCurFreqPathString(
    unsigned int cpu_id) const {
  return base::StringPrintf(
      "/sys/devices/system/cpu/cpu%d/cpufreq/scaling_cur_freq", cpu_id);
}

bool CPUFreqMonitorDelegate::IsTraceCategoryEnabled() const {
  bool enabled;
  TRACE_EVENT_CATEGORY_GROUP_ENABLED(kTraceCategory, &enabled);
  return enabled;
}

unsigned int CPUFreqMonitorDelegate::GetKernelMaxCPUs() const {
  std::string str;
  if (!base::ReadFileToString(
          base::FilePath("/sys/devices/system/cpu/kernel_max"), &str)) {
    // If we fail to read the kernel_max file, we just assume that CPU0 exists.
    return 0;
  }

  unsigned int kernel_max_cpu = 0;
  base::StringToUint(str, &kernel_max_cpu);
  return kernel_max_cpu;
}

std::string CPUFreqMonitorDelegate::GetRelatedCPUsPathString(
    unsigned int cpu_id) const {
  return base::StringPrintf(
      "/sys/devices/system/cpu/cpu%d/cpufreq/related_cpus", cpu_id);
}

void CPUFreqMonitorDelegate::GetCPUIds(std::vector<unsigned int>* ids) const {
  ids->clear();
  unsigned int kernel_max_cpu = GetKernelMaxCPUs();
  // CPUs related to one that's already marked for monitoring get set to "false"
  // so we don't needlessly monitor CPUs with redundant frequency information.
  char cpus_to_monitor[kernel_max_cpu + 1];
  std::memset(cpus_to_monitor, 1, kernel_max_cpu + 1);

  // Rule out the related CPUs for each one so we only end up with the CPUs
  // that are representative of the cluster.
  for (unsigned int i = 0; i <= kernel_max_cpu; i++) {
    if (!cpus_to_monitor[i])
      continue;

    std::string filename = GetRelatedCPUsPathString(i);
    std::string line;
    if (!base::ReadFileToString(base::FilePath(filename), &line))
      continue;
    // When reading the related_cpus file, we expected the format to be
    // something like "0 1 2 3" for CPU0-3 if they're all in one cluster.
    for (auto& str_piece :
         base::SplitString(line, " ", base::WhitespaceHandling::TRIM_WHITESPACE,
                           base::SplitResult::SPLIT_WANT_NONEMPTY)) {
      unsigned int cpu_id;
      if (base::StringToUint(str_piece, &cpu_id)) {
        if (cpu_id != i && cpu_id >= 0 && cpu_id <= kernel_max_cpu)
          cpus_to_monitor[cpu_id] = 0;
      }
    }
    ids->push_back(i);
  }

  // If none of the files were readable, we assume CPU0 exists and fall back to
  // using that.
  if (ids->size() == 0)
    ids->push_back(0);
}

void CPUFreqMonitorDelegate::RecordFrequency(unsigned int cpu_id,
                                             unsigned int freq) {
  TRACE_COUNTER_ID1(kTraceCategory, kEventTitle, cpu_id, freq);
}

scoped_refptr<SingleThreadTaskRunner>
CPUFreqMonitorDelegate::CreateTaskRunner() {
  return base::ThreadPool::CreateSingleThreadTaskRunner(
      {base::MayBlock(), base::TaskShutdownBehavior::SKIP_ON_SHUTDOWN,
       base::TaskPriority::BEST_EFFORT},
      base::SingleThreadTaskRunnerThreadMode::SHARED);
}

CPUFreqMonitor::CPUFreqMonitor()
    : CPUFreqMonitor(std::make_unique<CPUFreqMonitorDelegate>()) {}

CPUFreqMonitor::CPUFreqMonitor(std::unique_ptr<CPUFreqMonitorDelegate> delegate)
    : delegate_(std::move(delegate)) {}

CPUFreqMonitor::~CPUFreqMonitor() {
  Stop();
}

// static
CPUFreqMonitor* CPUFreqMonitor::GetInstance() {
  static base::NoDestructor<CPUFreqMonitor> instance;
  return instance.get();
}

void CPUFreqMonitor::OnTraceLogEnabled() {
  GetOrCreateTaskRunner()->PostTask(
      FROM_HERE,
      base::BindOnce(&CPUFreqMonitor::Start, weak_ptr_factory_.GetWeakPtr()));
}

void CPUFreqMonitor::OnTraceLogDisabled() {
  Stop();
}

void CPUFreqMonitor::Start() {
  // It's the responsibility of the caller to ensure that Start/Stop are
  // synchronized. If Start/Stop are called asynchronously where this value
  // may be incorrect, we have bigger problems.
  if (base::subtle::NoBarrier_Load(&is_enabled_) == 1 ||
      !delegate_->IsTraceCategoryEnabled()) {
    return;
  }

  std::vector<unsigned int> cpu_ids;
  delegate_->GetCPUIds(&cpu_ids);

  std::vector<std::pair<unsigned int, base::ScopedFD>> fds;
  for (unsigned int id : cpu_ids) {
    std::string fstr = delegate_->GetScalingCurFreqPathString(id);
    int fd = open(fstr.c_str(), O_RDONLY);
    if (fd == -1)
      continue;

    fds.emplace_back(std::make_pair(id, base::ScopedFD(fd)));
  }
  // We failed to read any scaling_cur_freq files, no point sampling nothing.
  if (fds.size() == 0)
    return;

  base::subtle::Release_Store(&is_enabled_, 1);

  GetOrCreateTaskRunner()->PostTask(
      FROM_HERE,
      base::BindOnce(&CPUFreqMonitor::Sample, weak_ptr_factory_.GetWeakPtr(),
                     std::move(fds)));
}

void CPUFreqMonitor::Stop() {
  base::subtle::Release_Store(&is_enabled_, 0);
}

void CPUFreqMonitor::Sample(
    std::vector<std::pair<unsigned int, base::ScopedFD>> fds) {
  // For the same reason as above we use NoBarrier_Load, because if this value
  // is in transition and we use Acquire_Load then we'll never shut down our
  // original Sample tasks until the next Stop, so it's still the responsibility
  // of callers to sync Start/Stop.
  if (base::subtle::NoBarrier_Load(&is_enabled_) == 0)
    return;

  for (auto& id_fd : fds) {
    int fd = id_fd.second.get();
    unsigned int freq = 0;
    // If we have trouble reading data from the file for any reason we'll end up
    // reporting the frequency as nothing.
    lseek(fd, 0L, SEEK_SET);
    char data[kNumBytesToReadForSampling];

    size_t bytes_read = read(fd, data, kNumBytesToReadForSampling);
    if (bytes_read > 0) {
      if (bytes_read < kNumBytesToReadForSampling)
        data[bytes_read] = '\0';
      int ret = sscanf(data, "%d", &freq);
      if (ret == 0 || ret == std::char_traits<char>::eof())
        freq = 0;
    }

    delegate_->RecordFrequency(id_fd.first, freq);
  }

  GetOrCreateTaskRunner()->PostDelayedTask(
      FROM_HERE,
      base::BindOnce(&CPUFreqMonitor::Sample, weak_ptr_factory_.GetWeakPtr(),
                     std::move(fds)),
      base::TimeDelta::FromMilliseconds(kDefaultCPUFreqSampleIntervalMs));
}

bool CPUFreqMonitor::IsEnabledForTesting() {
  return base::subtle::Acquire_Load(&is_enabled_) == 1;
}

const scoped_refptr<SingleThreadTaskRunner>&
CPUFreqMonitor::GetOrCreateTaskRunner() {
  if (!task_runner_)
    task_runner_ = delegate_->CreateTaskRunner();
  return task_runner_;
}

}  // namespace trace_event
}  // namespace base
