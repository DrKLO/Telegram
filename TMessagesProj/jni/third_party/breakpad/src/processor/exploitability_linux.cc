// Copyright (c) 2013 Google Inc.
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

// exploitability_linux.cc: Linux specific exploitability engine.
//
// Provides a guess at the exploitability of the crash for the Linux
// platform given a minidump and process_state.
//
// Author: Matthew Riley

#include "processor/exploitability_linux.h"

#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/stack_frame.h"

namespace {

// This function in libc is called if the program was compiled with
// -fstack-protector and a function's stack canary changes.
const char kStackCheckFailureFunction[] = "__stack_chk_fail";

// This function in libc is called if the program was compiled with
// -D_FORTIFY_SOURCE=2, a function like strcpy() is called, and the runtime
// can determine that the call would overflow the target buffer.
const char kBoundsCheckFailureFunction[] = "__chk_fail";

}  // namespace

namespace google_breakpad {

ExploitabilityLinux::ExploitabilityLinux(Minidump *dump,
                                         ProcessState *process_state)
    : Exploitability(dump, process_state) { }

ExploitabilityRating ExploitabilityLinux::CheckPlatformExploitability() {
  // Check the crashing thread for functions suggesting a buffer overflow or
  // stack smash.
  if (process_state_->requesting_thread() != -1) {
    CallStack* crashing_thread =
        process_state_->threads()->at(process_state_->requesting_thread());
    const vector<StackFrame*>& crashing_thread_frames =
        *crashing_thread->frames();
    for (size_t i = 0; i < crashing_thread_frames.size(); ++i) {
      if (crashing_thread_frames[i]->function_name ==
          kStackCheckFailureFunction) {
        return EXPLOITABILITY_HIGH;
      }

      if (crashing_thread_frames[i]->function_name ==
          kBoundsCheckFailureFunction) {
        return EXPLOITABILITY_HIGH;
      }
    }
  }

  return EXPLOITABILITY_NONE;
}

}  // namespace google_breakpad
