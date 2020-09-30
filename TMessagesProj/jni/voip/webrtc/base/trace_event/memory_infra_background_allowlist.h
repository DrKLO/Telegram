// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TRACE_EVENT_MEMORY_INFRA_BACKGROUND_ALLOWLIST_H_
#define BASE_TRACE_EVENT_MEMORY_INFRA_BACKGROUND_ALLOWLIST_H_

// This file contains the allowlists (aka whitelists) for background mode to
// limit the tracing overhead and remove sensitive information from traces.

#include <string>

#include "base/base_export.h"

namespace base {
namespace trace_event {

// Checks if the given |mdp_name| is in the allow list.
bool BASE_EXPORT IsMemoryDumpProviderInAllowlist(const char* mdp_name);

// Checks if the given |name| matches any of the allowed patterns.
bool BASE_EXPORT IsMemoryAllocatorDumpNameInAllowlist(const std::string& name);

// The allow list is replaced with the given list for tests. The last element
// of the list must be nullptr.
void BASE_EXPORT SetDumpProviderAllowlistForTesting(const char* const* list);
void BASE_EXPORT
SetAllocatorDumpNameAllowlistForTesting(const char* const* list);

}  // namespace trace_event
}  // namespace base

#endif  // BASE_TRACE_EVENT_MEMORY_INFRA_BACKGROUND_ALLOWLIST_H_
