// Copyright (c) 2010 Google Inc.
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

// exploitability_engine.cc: Generic exploitability engine.
//
// See exploitable_engine.h for documentation.
//
// Author: Cris Neckar


#include <cassert>

#include "common/scoped_ptr.h"
#include "google_breakpad/processor/exploitability.h"
#include "google_breakpad/processor/minidump.h"
#include "google_breakpad/processor/process_state.h"
#include "processor/exploitability_linux.h"
#include "processor/exploitability_win.h"
#include "processor/logging.h"

namespace google_breakpad {

Exploitability::Exploitability(Minidump *dump,
                               ProcessState *process_state)
    : dump_(dump),
      process_state_(process_state) {}

ExploitabilityRating Exploitability::CheckExploitability() {
  return CheckPlatformExploitability();
}

Exploitability *Exploitability::ExploitabilityForPlatform(
    Minidump *dump,
    ProcessState *process_state) {
  Exploitability *platform_exploitability = NULL;
  MinidumpSystemInfo *minidump_system_info = dump->GetSystemInfo();
  if (!minidump_system_info)
    return NULL;

  const MDRawSystemInfo *raw_system_info =
    minidump_system_info->system_info();
  if (!raw_system_info)
    return NULL;

  switch (raw_system_info->platform_id) {
    case MD_OS_WIN32_NT:
    case MD_OS_WIN32_WINDOWS: {
      platform_exploitability = new ExploitabilityWin(dump, process_state);
      break;
    }
    case MD_OS_LINUX: {
      platform_exploitability = new ExploitabilityLinux(dump, process_state);
      break;
    }
    case MD_OS_MAC_OS_X:
    case MD_OS_IOS:
    case MD_OS_UNIX:
    case MD_OS_SOLARIS:
    case MD_OS_ANDROID:
    case MD_OS_PS3:
    default: {
      platform_exploitability = NULL;
      break;
    }
  }

  BPLOG_IF(ERROR, !platform_exploitability) <<
    "No Exploitability module for platform: " <<
    process_state->system_info()->os;
  return platform_exploitability;
}

bool Exploitability::AddressIsAscii(uint64_t address) {
  for (int i = 0; i < 8; i++) {
    uint8_t byte = (address >> (8*i)) & 0xff;
    if ((byte >= ' ' && byte <= '~') || byte == 0)
      continue;
    return false;
  }
  return true;
}

}  // namespace google_breakpad

