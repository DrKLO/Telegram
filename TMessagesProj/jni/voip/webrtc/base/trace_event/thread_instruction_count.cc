// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/thread_instruction_count.h"

#include "base/base_switches.h"
#include "base/command_line.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/threading/thread_local_storage.h"
#include "build/build_config.h"

#if defined(OS_LINUX)
#include <linux/perf_event.h>
#include <sys/syscall.h>
#include <unistd.h>
#endif  // defined(OS_LINUX)

namespace base {
namespace trace_event {

namespace {

#if defined(OS_LINUX)

// Special constants used for counter FD states.
constexpr int kPerfFdDisabled = -2;
constexpr int kPerfFdOpenFailed = -1;
constexpr int kPerfFdUninitialized = 0;

ThreadLocalStorage::Slot& InstructionCounterFdSlot() {
  static NoDestructor<ThreadLocalStorage::Slot> fd_slot([](void* fd_ptr) {
    int fd = reinterpret_cast<intptr_t>(fd_ptr);
    if (fd > kPerfFdUninitialized)
      close(fd);
  });
  return *fd_slot;
}

// Opens a new file descriptor that emits the value of
// PERF_COUNT_HW_INSTRUCTIONS in userspace (excluding kernel and hypervisor
// instructions) for the given |thread_id|, or 0 for the calling thread.
//
// Returns kPerfFdOpenFailed if opening the file descriptor failed, or
// kPerfFdDisabled if performance counters are disabled in the calling process.
int OpenInstructionCounterFdForThread(int thread_id) {
  // This switch is only propagated for processes that are unaffected by the
  // BPF sandbox, such as the browser process or renderers with --no-sandbox.
  const base::CommandLine& command_line =
      *base::CommandLine::ForCurrentProcess();
  if (!command_line.HasSwitch(switches::kEnableThreadInstructionCount))
    return kPerfFdDisabled;

  struct perf_event_attr pe = {0};
  pe.type = PERF_TYPE_HARDWARE;
  pe.size = sizeof(struct perf_event_attr);
  pe.config = PERF_COUNT_HW_INSTRUCTIONS;
  pe.exclude_kernel = 1;
  pe.exclude_hv = 1;

  int fd = syscall(__NR_perf_event_open, &pe, thread_id, /* cpu */ -1,
                   /* group_fd */ -1, /* flags */ 0);
  if (fd < 0) {
    LOG(ERROR) << "perf_event_open failed, omitting instruction counters";
    return kPerfFdOpenFailed;
  }
  return fd;
}

// Retrieves the active perf counter FD for the current thread, performing
// lazy-initialization if necessary.
int InstructionCounterFdForCurrentThread() {
  auto& slot = InstructionCounterFdSlot();
  int fd = reinterpret_cast<intptr_t>(slot.Get());
  if (fd == kPerfFdUninitialized) {
    fd = OpenInstructionCounterFdForThread(0);
    slot.Set(reinterpret_cast<void*>(fd));
  }
  return fd;
}

#endif  // defined(OS_LINUX)

}  // namespace

bool ThreadInstructionCount::IsSupported() {
#if defined(OS_LINUX)
  // If we can't initialize the counter FD, mark as disabled.
  int counter_fd = InstructionCounterFdForCurrentThread();
  if (counter_fd <= 0)
    return false;

  return true;
#endif  // defined(OS_LINUX)
  return false;
}

ThreadInstructionCount ThreadInstructionCount::Now() {
  DCHECK(IsSupported());
#if defined(OS_LINUX)
  int fd = InstructionCounterFdForCurrentThread();
  if (fd <= 0)
    return ThreadInstructionCount();

  uint64_t instructions = 0;
  ssize_t bytes_read = read(fd, &instructions, sizeof(instructions));
  CHECK_EQ(bytes_read, static_cast<ssize_t>(sizeof(instructions)))
      << "Short reads of small size from kernel memory is not expected. If "
         "this fails, use HANDLE_EINTR.";
  return ThreadInstructionCount(instructions);
#endif  // defined(OS_LINUX)
  return ThreadInstructionCount();
}

}  // namespace trace_event
}  // namespace base
