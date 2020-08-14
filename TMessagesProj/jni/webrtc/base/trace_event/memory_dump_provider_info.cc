// Copyright 2017 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/trace_event/memory_dump_provider_info.h"

#include <tuple>

#include "base/sequenced_task_runner.h"

namespace base {
namespace trace_event {

MemoryDumpProviderInfo::MemoryDumpProviderInfo(
    MemoryDumpProvider* dump_provider,
    const char* name,
    scoped_refptr<SequencedTaskRunner> task_runner,
    const MemoryDumpProvider::Options& options,
    bool allowed_in_background_mode)
    : dump_provider(dump_provider),
      options(options),
      name(name),
      task_runner(std::move(task_runner)),
      allowed_in_background_mode(allowed_in_background_mode),
      consecutive_failures(0),
      disabled(false) {}

MemoryDumpProviderInfo::~MemoryDumpProviderInfo() = default;

bool MemoryDumpProviderInfo::Comparator::operator()(
    const scoped_refptr<MemoryDumpProviderInfo>& a,
    const scoped_refptr<MemoryDumpProviderInfo>& b) const {
  if (!a || !b)
    return a.get() < b.get();
  // Ensure that unbound providers (task_runner == nullptr) always run last.
  // Rationale: some unbound dump providers are known to be slow, keep them last
  // to avoid skewing timings of the other dump providers.
  return std::tie(a->task_runner, a->dump_provider) >
         std::tie(b->task_runner, b->dump_provider);
}

}  // namespace trace_event
}  // namespace base
