// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/reached_code_profiler.h"

#include <signal.h>
#include <sys/time.h>
#include <ucontext.h>
#include <unistd.h>

#include <atomic>

#include "base/android/library_loader/anchor_functions.h"
#include "base/android/orderfile/orderfile_buildflags.h"
#include "base/android/reached_addresses_bitset.h"
#include "base/base_switches.h"
#include "base/bind.h"
#include "base/command_line.h"
#include "base/feature_list.h"
#include "base/files/file_path.h"
#include "base/files/file_util.h"
#include "base/files/important_file_writer.h"
#include "base/linux_util.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/optional.h"
#include "base/path_service.h"
#include "base/scoped_generic.h"
#include "base/single_thread_task_runner.h"
#include "base/strings/string_piece.h"
#include "base/strings/stringprintf.h"
#include "base/synchronization/lock.h"
#include "base/threading/thread.h"
#include "base/timer/timer.h"

#if !BUILDFLAG(SUPPORTS_CODE_ORDERING)
#error Code ordering support is required for the reached code profiler.
#endif

namespace base {
namespace android {

namespace {

#if !defined(NDEBUG) || defined(COMPONENT_BUILD)
// Always disabled for debug builds to avoid hitting a limit of signal
// interrupts that can get delivered into a single HANDLE_EINTR. Also
// debugging experience would be bad if there are a lot of signals flying
// around.
// Always disabled for component builds because in this case the code is not
// organized in one contiguous region which is required for the reached code
// profiler.
constexpr const bool kConfigurationSupported = false;
#else
constexpr const bool kConfigurationSupported = true;
#endif

constexpr const char kDumpToFileFlag[] = "reached-code-profiler-dump-to-file";

constexpr uint64_t kIterationsBeforeSkipping = 50;
constexpr uint64_t kIterationsBetweenUpdates = 100;
constexpr int kProfilerSignal = SIGURG;

constexpr base::TimeDelta kSamplingInterval =
    base::TimeDelta::FromMilliseconds(10);
constexpr base::TimeDelta kDumpInterval = base::TimeDelta::FromSeconds(30);

void HandleSignal(int signal, siginfo_t* info, void* context) {
  if (signal != kProfilerSignal)
    return;

  ucontext_t* ucontext = reinterpret_cast<ucontext_t*>(context);
  uint32_t address = ucontext->uc_mcontext.arm_pc;
  ReachedAddressesBitset::GetTextBitset()->RecordAddress(address);
}

struct ScopedTimerCloseTraits {
  static base::Optional<timer_t> InvalidValue() { return base::nullopt; }

  static void Free(base::Optional<timer_t> x) { timer_delete(*x); }
};

// RAII object holding an interval timer.
using ScopedTimer =
    base::ScopedGeneric<base::Optional<timer_t>, ScopedTimerCloseTraits>;

void DumpToFile(const base::FilePath& path,
                scoped_refptr<base::SingleThreadTaskRunner> task_runner) {
  DCHECK(task_runner->BelongsToCurrentThread());

  auto dir_path = path.DirName();
  if (!base::DirectoryExists(dir_path) && !base::CreateDirectory(dir_path)) {
    PLOG(ERROR) << "Could not create " << dir_path;
    return;
  }

  std::vector<uint32_t> reached_offsets =
      ReachedAddressesBitset::GetTextBitset()->GetReachedOffsets();
  base::StringPiece contents(
      reinterpret_cast<const char*>(reached_offsets.data()),
      reached_offsets.size());
  if (!base::ImportantFileWriter::WriteFileAtomically(path, contents,
                                                      "ReachedDump")) {
    LOG(ERROR) << "Could not write reached dump into " << path;
  }

  task_runner->PostDelayedTask(
      FROM_HERE, base::BindOnce(&DumpToFile, path, task_runner), kDumpInterval);
}

class ReachedCodeProfiler {
 public:
  static ReachedCodeProfiler* GetInstance() {
    static base::NoDestructor<ReachedCodeProfiler> instance;
    return instance.get();
  }

  // Starts to periodically send |kProfilerSignal| to all threads.
  void Start(LibraryProcessType library_process_type) {
    if (is_enabled_)
      return;

    // Set |kProfilerSignal| signal handler.
    // TODO(crbug.com/916263): consider restoring |old_handler| after the
    // profiler gets stopped.
    struct sigaction old_handler;
    struct sigaction sa;
    sigemptyset(&sa.sa_mask);
    sa.sa_sigaction = &HandleSignal;
    sa.sa_flags = SA_RESTART | SA_SIGINFO;
    int ret = sigaction(kProfilerSignal, &sa, &old_handler);
    if (ret) {
      PLOG(ERROR) << "Error setting signal handler. The reached code profiler "
                     "is disabled";
      return;
    }

    // Create a new interval timer.
    struct sigevent sevp;
    memset(&sevp, 0, sizeof(sevp));
    sevp.sigev_notify = SIGEV_THREAD;
    sevp.sigev_notify_function = &OnTimerNotify;
    timer_t timerid;
    ret = timer_create(CLOCK_PROCESS_CPUTIME_ID, &sevp, &timerid);
    if (ret) {
      PLOG(ERROR)
          << "timer_create() failed. The reached code profiler is disabled";
      return;
    }

    timer_.reset(timerid);

    // Start the interval timer.
    struct itimerspec its;
    memset(&its, 0, sizeof(its));
    its.it_interval.tv_nsec = kSamplingInterval.InNanoseconds();
    its.it_value = its.it_interval;
    ret = timer_settime(timerid, 0, &its, nullptr);
    if (ret) {
      PLOG(ERROR)
          << "timer_settime() failed. The reached code profiler is disabled";
      return;
    }

    if (library_process_type == PROCESS_BROWSER)
      StartDumpingReachedCode();

    is_enabled_ = true;
  }

  // Stops profiling.
  void Stop() {
    timer_.reset();
    dumping_thread_.reset();
    is_enabled_ = false;
  }

  // Returns whether the profiler is currently enabled.
  bool IsEnabled() { return is_enabled_; }

 private:
  ReachedCodeProfiler()
      : current_pid_(getpid()), iteration_number_(0), is_enabled_(false) {}

  static void OnTimerNotify(sigval_t ignored) {
    ReachedCodeProfiler::GetInstance()->SendSignalToAllThreads();
  }

  void SendSignalToAllThreads() {
    // This code should be thread-safe.
    base::AutoLock scoped_lock(lock_);
    ++iteration_number_;

    if (iteration_number_ <= kIterationsBeforeSkipping ||
        iteration_number_ % kIterationsBetweenUpdates == 0) {
      tids_.clear();
      if (!base::GetThreadsForProcess(current_pid_, &tids_)) {
        LOG(WARNING) << "Failed to get a list of threads for process "
                     << current_pid_;
        return;
      }
    }

    pid_t current_tid = gettid();
    for (pid_t tid : tids_) {
      if (tid != current_tid)
        tgkill(current_pid_, tid, kProfilerSignal);
    }
  }

  void StartDumpingReachedCode() {
    const base::CommandLine* cmdline = base::CommandLine::ForCurrentProcess();
    if (!cmdline->HasSwitch(kDumpToFileFlag))
      return;

    base::FilePath dir_path(cmdline->GetSwitchValueASCII(kDumpToFileFlag));
    if (dir_path.empty()) {
      if (!base::PathService::Get(base::DIR_CACHE, &dir_path)) {
        LOG(WARNING) << "Failed to get cache dir path.";
        return;
      }
    }

    auto file_path =
        dir_path.Append(base::StringPrintf("reached-code-%d.txt", getpid()));

    dumping_thread_ =
        std::make_unique<base::Thread>("ReachedCodeProfilerDumpingThread");
    base::Thread::Options options;
    options.priority = base::ThreadPriority::BACKGROUND;
    dumping_thread_->StartWithOptions(options);
    dumping_thread_->task_runner()->PostDelayedTask(
        FROM_HERE,
        base::BindOnce(&DumpToFile, file_path, dumping_thread_->task_runner()),
        kDumpInterval);
  }

  base::Lock lock_;
  std::vector<pid_t> tids_;
  const pid_t current_pid_;
  uint64_t iteration_number_;
  ScopedTimer timer_;
  std::unique_ptr<base::Thread> dumping_thread_;

  bool is_enabled_;

  friend class NoDestructor<ReachedCodeProfiler>;

  DISALLOW_COPY_AND_ASSIGN(ReachedCodeProfiler);
};

bool ShouldEnableReachedCodeProfiler() {
  if (!kConfigurationSupported)
    return false;

  const base::CommandLine* cmdline = base::CommandLine::ForCurrentProcess();
  return cmdline->HasSwitch(switches::kEnableReachedCodeProfiler);
}

}  // namespace

void InitReachedCodeProfilerAtStartup(LibraryProcessType library_process_type) {
  // The profiler shouldn't be run as part of webview.
  CHECK(library_process_type == PROCESS_BROWSER ||
        library_process_type == PROCESS_CHILD);

  if (!ShouldEnableReachedCodeProfiler())
    return;

  ReachedCodeProfiler::GetInstance()->Start(library_process_type);
}

bool IsReachedCodeProfilerEnabled() {
  return ReachedCodeProfiler::GetInstance()->IsEnabled();
}

bool IsReachedCodeProfilerSupported() {
  return kConfigurationSupported;
}

}  // namespace android
}  // namespace base
