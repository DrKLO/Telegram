// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/memory_dump_manager.h"

#include <inttypes.h>
#include <stdio.h>

#include <algorithm>
#include <memory>
#include <utility>

#include "base/allocator/buildflags.h"
#include "base/base_switches.h"
#include "base/command_line.h"
#include "base/debug/alias.h"
#include "base/debug/stack_trace.h"
#include "base/memory/ptr_util.h"
#include "base/sequenced_task_runner.h"
#include "base/strings/string_util.h"
#include "base/third_party/dynamic_annotations/dynamic_annotations.h"
#include "base/threading/thread.h"
#include "base/threading/thread_task_runner_handle.h"
#include "base/trace_event/heap_profiler.h"
#include "base/trace_event/heap_profiler_allocation_context_tracker.h"
#include "base/trace_event/heap_profiler_event_filter.h"
#include "base/trace_event/malloc_dump_provider.h"
#include "base/trace_event/memory_dump_provider.h"
#include "base/trace_event/memory_dump_scheduler.h"
#include "base/trace_event/memory_infra_background_allowlist.h"
#include "base/trace_event/process_memory_dump.h"
#include "base/trace_event/trace_event.h"
#include "base/trace_event/traced_value.h"
#include "build/build_config.h"

#if defined(OS_ANDROID)
#include "base/trace_event/java_heap_dump_provider_android.h"

#if BUILDFLAG(CAN_UNWIND_WITH_CFI_TABLE)
#include "base/trace_event/cfi_backtrace_android.h"
#endif

#endif  // defined(OS_ANDROID)

namespace base {
namespace trace_event {

namespace {

MemoryDumpManager* g_memory_dump_manager_for_testing = nullptr;

// Temporary (until scheduler is moved outside of here)
// trampoline function to match the |request_dump_function| passed to Initialize
// to the callback expected by MemoryDumpScheduler.
// TODO(primiano): remove this.
void DoGlobalDumpWithoutCallback(
    MemoryDumpManager::RequestGlobalDumpFunction global_dump_fn,
    MemoryDumpType dump_type,
    MemoryDumpLevelOfDetail level_of_detail) {
  global_dump_fn.Run(dump_type, level_of_detail);
}

}  // namespace

// static
constexpr const char* MemoryDumpManager::kTraceCategory;

// static
const int MemoryDumpManager::kMaxConsecutiveFailuresCount = 3;

// static
const uint64_t MemoryDumpManager::kInvalidTracingProcessId = 0;

// static
const char* const MemoryDumpManager::kSystemAllocatorPoolName =
#if defined(MALLOC_MEMORY_TRACING_SUPPORTED)
    MallocDumpProvider::kAllocatedObjects;
#else
    nullptr;
#endif

// static
MemoryDumpManager* MemoryDumpManager::GetInstance() {
  if (g_memory_dump_manager_for_testing)
    return g_memory_dump_manager_for_testing;

  return Singleton<MemoryDumpManager,
                   LeakySingletonTraits<MemoryDumpManager>>::get();
}

// static
std::unique_ptr<MemoryDumpManager>
MemoryDumpManager::CreateInstanceForTesting() {
  DCHECK(!g_memory_dump_manager_for_testing);
  std::unique_ptr<MemoryDumpManager> instance(new MemoryDumpManager());
  g_memory_dump_manager_for_testing = instance.get();
  return instance;
}

MemoryDumpManager::MemoryDumpManager()
    : is_coordinator_(false),
      tracing_process_id_(kInvalidTracingProcessId),
      dumper_registrations_ignored_for_testing_(false) {}

MemoryDumpManager::~MemoryDumpManager() {
  Thread* dump_thread = nullptr;
  {
    AutoLock lock(lock_);
    if (dump_thread_) {
      dump_thread = dump_thread_.get();
    }
  }
  if (dump_thread) {
    dump_thread->Stop();
  }
  AutoLock lock(lock_);
  dump_thread_.reset();
  g_memory_dump_manager_for_testing = nullptr;
}

void MemoryDumpManager::Initialize(
    RequestGlobalDumpFunction request_dump_function,
    bool is_coordinator) {
  {
    AutoLock lock(lock_);
    DCHECK(!request_dump_function.is_null());
    DCHECK(!can_request_global_dumps());
    request_dump_function_ = request_dump_function;
    is_coordinator_ = is_coordinator;
  }

// Enable the core dump providers.
#if defined(MALLOC_MEMORY_TRACING_SUPPORTED)
  RegisterDumpProvider(MallocDumpProvider::GetInstance(), "Malloc", nullptr);
#endif

#if defined(OS_ANDROID)
  RegisterDumpProvider(JavaHeapDumpProvider::GetInstance(), "JavaHeap",
                       nullptr);
#endif
}

void MemoryDumpManager::RegisterDumpProvider(
    MemoryDumpProvider* mdp,
    const char* name,
    scoped_refptr<SingleThreadTaskRunner> task_runner,
    MemoryDumpProvider::Options options) {
  options.dumps_on_single_thread_task_runner = true;
  RegisterDumpProviderInternal(mdp, name, std::move(task_runner), options);
}

void MemoryDumpManager::RegisterDumpProvider(
    MemoryDumpProvider* mdp,
    const char* name,
    scoped_refptr<SingleThreadTaskRunner> task_runner) {
  // Set |dumps_on_single_thread_task_runner| to true because all providers
  // without task runner are run on dump thread.
  MemoryDumpProvider::Options options;
  options.dumps_on_single_thread_task_runner = true;
  RegisterDumpProviderInternal(mdp, name, std::move(task_runner), options);
}

void MemoryDumpManager::RegisterDumpProviderWithSequencedTaskRunner(
    MemoryDumpProvider* mdp,
    const char* name,
    scoped_refptr<SequencedTaskRunner> task_runner,
    MemoryDumpProvider::Options options) {
  DCHECK(task_runner);
  options.dumps_on_single_thread_task_runner = false;
  RegisterDumpProviderInternal(mdp, name, std::move(task_runner), options);
}

void MemoryDumpManager::RegisterDumpProviderInternal(
    MemoryDumpProvider* mdp,
    const char* name,
    scoped_refptr<SequencedTaskRunner> task_runner,
    const MemoryDumpProvider::Options& options) {
  if (dumper_registrations_ignored_for_testing_)
    return;

  // Only a handful of MDPs are required to compute the memory metrics. These
  // have small enough performance overhead that it is reasonable to run them
  // in the background while the user is doing other things. Those MDPs are
  // 'allowed in background mode'.
  bool allowed_in_background_mode = IsMemoryDumpProviderInAllowlist(name);

  scoped_refptr<MemoryDumpProviderInfo> mdpinfo = new MemoryDumpProviderInfo(
      mdp, name, std::move(task_runner), options, allowed_in_background_mode);

  {
    AutoLock lock(lock_);
    bool already_registered = !dump_providers_.insert(mdpinfo).second;
    // This actually happens in some tests which don't have a clean tear-down
    // path for RenderThreadImpl::Init().
    if (already_registered)
      return;
  }
}

void MemoryDumpManager::UnregisterDumpProvider(MemoryDumpProvider* mdp) {
  UnregisterDumpProviderInternal(mdp, false /* delete_async */);
}

void MemoryDumpManager::UnregisterAndDeleteDumpProviderSoon(
    std::unique_ptr<MemoryDumpProvider> mdp) {
  UnregisterDumpProviderInternal(mdp.release(), true /* delete_async */);
}

void MemoryDumpManager::UnregisterDumpProviderInternal(
    MemoryDumpProvider* mdp,
    bool take_mdp_ownership_and_delete_async) {
  std::unique_ptr<MemoryDumpProvider> owned_mdp;
  if (take_mdp_ownership_and_delete_async)
    owned_mdp.reset(mdp);

  AutoLock lock(lock_);

  auto mdp_iter = dump_providers_.begin();
  for (; mdp_iter != dump_providers_.end(); ++mdp_iter) {
    if ((*mdp_iter)->dump_provider == mdp)
      break;
  }

  if (mdp_iter == dump_providers_.end())
    return;  // Not registered / already unregistered.

  if (take_mdp_ownership_and_delete_async) {
    // The MDP will be deleted whenever the MDPInfo struct will, that is either:
    // - At the end of this function, if no dump is in progress.
    // - In ContinueAsyncProcessDump() when MDPInfo is removed from
    //   |pending_dump_providers|.
    DCHECK(!(*mdp_iter)->owned_dump_provider);
    (*mdp_iter)->owned_dump_provider = std::move(owned_mdp);
  } else {
    // If you hit this DCHECK, your dump provider has a bug.
    // Unregistration of a MemoryDumpProvider is safe only if:
    // - The MDP has specified a sequenced task runner affinity AND the
    //   unregistration happens on the same task runner. So that the MDP cannot
    //   unregister and be in the middle of a OnMemoryDump() at the same time.
    // - The MDP has NOT specified a task runner affinity and its ownership is
    //   transferred via UnregisterAndDeleteDumpProviderSoon().
    // In all the other cases, it is not possible to guarantee that the
    // unregistration will not race with OnMemoryDump() calls.
    DCHECK((*mdp_iter)->task_runner &&
           (*mdp_iter)->task_runner->RunsTasksInCurrentSequence())
        << "MemoryDumpProvider \"" << (*mdp_iter)->name << "\" attempted to "
        << "unregister itself in a racy way. Please file a crbug.";
  }

  // The MDPInfo instance can still be referenced by the
  // |ProcessMemoryDumpAsyncState.pending_dump_providers|. For this reason
  // the MDPInfo is flagged as disabled. It will cause InvokeOnMemoryDump()
  // to just skip it, without actually invoking the |mdp|, which might be
  // destroyed by the caller soon after this method returns.
  (*mdp_iter)->disabled = true;
  dump_providers_.erase(mdp_iter);
}

bool MemoryDumpManager::IsDumpProviderRegisteredForTesting(
    MemoryDumpProvider* provider) {
  AutoLock lock(lock_);

  for (const auto& info : dump_providers_) {
    if (info->dump_provider == provider)
      return true;
  }
  return false;
}

scoped_refptr<SequencedTaskRunner>
MemoryDumpManager::GetDumpThreadTaskRunner() {
  base::AutoLock lock(lock_);
  return GetOrCreateBgTaskRunnerLocked();
}

scoped_refptr<base::SequencedTaskRunner>
MemoryDumpManager::GetOrCreateBgTaskRunnerLocked() {
  lock_.AssertAcquired();

  if (dump_thread_)
    return dump_thread_->task_runner();

  dump_thread_ = std::make_unique<Thread>("MemoryInfra");
  bool started = dump_thread_->Start();
  CHECK(started);

  return dump_thread_->task_runner();
}

void MemoryDumpManager::CreateProcessDump(const MemoryDumpRequestArgs& args,
                                          ProcessMemoryDumpCallback callback) {
  char guid_str[20];
  sprintf(guid_str, "0x%" PRIx64, args.dump_guid);
  TRACE_EVENT_NESTABLE_ASYNC_BEGIN1(kTraceCategory, "ProcessMemoryDump",
                                    TRACE_ID_LOCAL(args.dump_guid), "dump_guid",
                                    TRACE_STR_COPY(guid_str));

  // If argument filter is enabled then only background mode dumps should be
  // allowed. In case the trace config passed for background tracing session
  // missed the allowed modes argument, it crashes here instead of creating
  // unexpected dumps.
  if (TraceLog::GetInstance()
          ->GetCurrentTraceConfig()
          .IsArgumentFilterEnabled()) {
    CHECK_EQ(MemoryDumpLevelOfDetail::BACKGROUND, args.level_of_detail);
  }

  std::unique_ptr<ProcessMemoryDumpAsyncState> pmd_async_state;
  {
    AutoLock lock(lock_);

    pmd_async_state.reset(new ProcessMemoryDumpAsyncState(
        args, dump_providers_, std::move(callback),
        GetOrCreateBgTaskRunnerLocked()));
  }

  // Start the process dump. This involves task runner hops as specified by the
  // MemoryDumpProvider(s) in RegisterDumpProvider()).
  ContinueAsyncProcessDump(pmd_async_state.release());
}

// Invokes OnMemoryDump() on all MDPs that are next in the pending list and run
// on the current sequenced task runner. If the next MDP does not run in current
// sequenced task runner, then switches to that task runner and continues. All
// OnMemoryDump() invocations are linearized. |lock_| is used in these functions
// purely to ensure consistency w.r.t. (un)registrations of |dump_providers_|.
void MemoryDumpManager::ContinueAsyncProcessDump(
    ProcessMemoryDumpAsyncState* owned_pmd_async_state) {
  HEAP_PROFILER_SCOPED_IGNORE;
  // Initalizes the ThreadLocalEventBuffer to guarantee that the TRACE_EVENTs
  // in the PostTask below don't end up registering their own dump providers
  // (for discounting trace memory overhead) while holding the |lock_|.
  TraceLog::GetInstance()->InitializeThreadLocalEventBufferIfSupported();

  // In theory |owned_pmd_async_state| should be a unique_ptr. The only reason
  // why it isn't is because of the corner case logic of |did_post_task|
  // above, which needs to take back the ownership of the |pmd_async_state| when
  // the PostTask() fails.
  // Unfortunately, PostTask() destroys the unique_ptr arguments upon failure
  // to prevent accidental leaks. Using a unique_ptr would prevent us to to
  // skip the hop and move on. Hence the manual naked -> unique ptr juggling.
  auto pmd_async_state = WrapUnique(owned_pmd_async_state);
  owned_pmd_async_state = nullptr;

  while (!pmd_async_state->pending_dump_providers.empty()) {
    // Read MemoryDumpProviderInfo thread safety considerations in
    // memory_dump_manager.h when accessing |mdpinfo| fields.
    MemoryDumpProviderInfo* mdpinfo =
        pmd_async_state->pending_dump_providers.back().get();

    // If we are in background mode, we should invoke only the whitelisted
    // providers. Ignore other providers and continue.
    if (pmd_async_state->req_args.level_of_detail ==
            MemoryDumpLevelOfDetail::BACKGROUND &&
        !mdpinfo->allowed_in_background_mode) {
      pmd_async_state->pending_dump_providers.pop_back();
      continue;
    }

    // If the dump provider did not specify a task runner affinity, dump on
    // |dump_thread_|.
    scoped_refptr<SequencedTaskRunner> task_runner = mdpinfo->task_runner;
    if (!task_runner) {
      DCHECK(mdpinfo->options.dumps_on_single_thread_task_runner);
      task_runner = pmd_async_state->dump_thread_task_runner;
      DCHECK(task_runner);
    }

    // If |RunsTasksInCurrentSequence()| is true then no PostTask is
    // required since we are on the right SequencedTaskRunner.
    if (task_runner->RunsTasksInCurrentSequence()) {
      InvokeOnMemoryDump(mdpinfo, pmd_async_state->process_memory_dump.get());
      pmd_async_state->pending_dump_providers.pop_back();
      continue;
    }

    bool did_post_task = task_runner->PostTask(
        FROM_HERE,
        BindOnce(&MemoryDumpManager::ContinueAsyncProcessDump, Unretained(this),
                 Unretained(pmd_async_state.get())));

    if (did_post_task) {
      // Ownership is tranferred to the posted task.
      ignore_result(pmd_async_state.release());
      return;
    }

    // PostTask usually fails only if the process or thread is shut down. So,
    // the dump provider is disabled here. But, don't disable unbound dump
    // providers, since the |dump_thread_| is controlled by MDM.
    if (mdpinfo->task_runner) {
      // A locked access is required to R/W |disabled| (for the
      // UnregisterAndDeleteDumpProviderSoon() case).
      AutoLock lock(lock_);
      mdpinfo->disabled = true;
    }

    // PostTask failed. Ignore the dump provider and continue.
    pmd_async_state->pending_dump_providers.pop_back();
  }

  FinishAsyncProcessDump(std::move(pmd_async_state));
}

// This function is called on the right task runner for current MDP. It is
// either the task runner specified by MDP or |dump_thread_task_runner| if the
// MDP did not specify task runner. Invokes the dump provider's OnMemoryDump()
// (unless disabled).
void MemoryDumpManager::InvokeOnMemoryDump(MemoryDumpProviderInfo* mdpinfo,
                                           ProcessMemoryDump* pmd) {
  HEAP_PROFILER_SCOPED_IGNORE;
  DCHECK(!mdpinfo->task_runner ||
         mdpinfo->task_runner->RunsTasksInCurrentSequence());

  TRACE_EVENT1(kTraceCategory, "MemoryDumpManager::InvokeOnMemoryDump",
               "dump_provider.name", mdpinfo->name);

  // Do not add any other TRACE_EVENT macro (or function that might have them)
  // below this point. Under some rare circunstances, they can re-initialize
  // and invalide the current ThreadLocalEventBuffer MDP, making the
  // |should_dump| check below susceptible to TOCTTOU bugs
  // (https://crbug.com/763365).

  bool is_thread_bound;
  {
    // A locked access is required to R/W |disabled| (for the
    // UnregisterAndDeleteDumpProviderSoon() case).
    AutoLock lock(lock_);

    // Unregister the dump provider if it failed too many times consecutively.
    if (!mdpinfo->disabled &&
        mdpinfo->consecutive_failures >= kMaxConsecutiveFailuresCount) {
      mdpinfo->disabled = true;
      DLOG(ERROR) << "Disabling MemoryDumpProvider \"" << mdpinfo->name
                  << "\". Dump failed multiple times consecutively.";
    }
    if (mdpinfo->disabled)
      return;

    is_thread_bound = mdpinfo->task_runner != nullptr;
  }  // AutoLock lock(lock_);

  // Invoke the dump provider.

  // A stack allocated string with dump provider name is useful to debug
  // crashes while invoking dump after a |dump_provider| is not unregistered
  // in safe way.
  char provider_name_for_debugging[16];
  strncpy(provider_name_for_debugging, mdpinfo->name,
          sizeof(provider_name_for_debugging) - 1);
  provider_name_for_debugging[sizeof(provider_name_for_debugging) - 1] = '\0';
  base::debug::Alias(provider_name_for_debugging);

  ANNOTATE_BENIGN_RACE(&mdpinfo->disabled, "best-effort race detection");
  CHECK(!is_thread_bound ||
        !*(static_cast<volatile bool*>(&mdpinfo->disabled)));
  bool dump_successful =
      mdpinfo->dump_provider->OnMemoryDump(pmd->dump_args(), pmd);
  mdpinfo->consecutive_failures =
      dump_successful ? 0 : mdpinfo->consecutive_failures + 1;
}

void MemoryDumpManager::FinishAsyncProcessDump(
    std::unique_ptr<ProcessMemoryDumpAsyncState> pmd_async_state) {
  HEAP_PROFILER_SCOPED_IGNORE;
  DCHECK(pmd_async_state->pending_dump_providers.empty());
  const uint64_t dump_guid = pmd_async_state->req_args.dump_guid;
  if (!pmd_async_state->callback_task_runner->BelongsToCurrentThread()) {
    scoped_refptr<SingleThreadTaskRunner> callback_task_runner =
        pmd_async_state->callback_task_runner;
    callback_task_runner->PostTask(
        FROM_HERE, BindOnce(&MemoryDumpManager::FinishAsyncProcessDump,
                            Unretained(this), std::move(pmd_async_state)));
    return;
  }

  TRACE_EVENT0(kTraceCategory, "MemoryDumpManager::FinishAsyncProcessDump");

  if (!pmd_async_state->callback.is_null()) {
    std::move(pmd_async_state->callback)
        .Run(true /* success */, dump_guid,
             std::move(pmd_async_state->process_memory_dump));
  }

  TRACE_EVENT_NESTABLE_ASYNC_END0(kTraceCategory, "ProcessMemoryDump",
                                  TRACE_ID_LOCAL(dump_guid));
}

void MemoryDumpManager::SetupForTracing(
    const TraceConfig::MemoryDumpConfig& memory_dump_config) {
  AutoLock lock(lock_);

  // At this point we must have the ability to request global dumps.
  DCHECK(can_request_global_dumps());

  MemoryDumpScheduler::Config periodic_config;
  for (const auto& trigger : memory_dump_config.triggers) {
    if (trigger.trigger_type == MemoryDumpType::PERIODIC_INTERVAL) {
      if (periodic_config.triggers.empty()) {
        periodic_config.callback =
            BindRepeating(&DoGlobalDumpWithoutCallback, request_dump_function_,
                          MemoryDumpType::PERIODIC_INTERVAL);
      }
      periodic_config.triggers.push_back(
          {trigger.level_of_detail, trigger.min_time_between_dumps_ms});
    }
  }

  // Only coordinator process triggers periodic memory dumps.
  if (is_coordinator_ && !periodic_config.triggers.empty()) {
    MemoryDumpScheduler::GetInstance()->Start(periodic_config,
                                              GetOrCreateBgTaskRunnerLocked());
  }
}

void MemoryDumpManager::TeardownForTracing() {
  // There might be a memory dump in progress while this happens. Therefore,
  // ensure that the MDM state which depends on the tracing enabled / disabled
  // state is always accessed by the dumping methods holding the |lock_|.
  AutoLock lock(lock_);

  MemoryDumpScheduler::GetInstance()->Stop();
}

MemoryDumpManager::ProcessMemoryDumpAsyncState::ProcessMemoryDumpAsyncState(
    MemoryDumpRequestArgs req_args,
    const MemoryDumpProviderInfo::OrderedSet& dump_providers,
    ProcessMemoryDumpCallback callback,
    scoped_refptr<SequencedTaskRunner> dump_thread_task_runner)
    : req_args(req_args),
      callback(std::move(callback)),
      callback_task_runner(ThreadTaskRunnerHandle::Get()),
      dump_thread_task_runner(std::move(dump_thread_task_runner)) {
  pending_dump_providers.reserve(dump_providers.size());
  pending_dump_providers.assign(dump_providers.rbegin(), dump_providers.rend());
  MemoryDumpArgs args = {req_args.level_of_detail, req_args.determinism,
                         req_args.dump_guid};
  process_memory_dump = std::make_unique<ProcessMemoryDump>(args);
}

MemoryDumpManager::ProcessMemoryDumpAsyncState::~ProcessMemoryDumpAsyncState() =
    default;

}  // namespace trace_event
}  // namespace base
