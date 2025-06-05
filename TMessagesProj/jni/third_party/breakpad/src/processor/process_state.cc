// Copyright (c) 2006, Google Inc.
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

// process_state.cc: A snapshot of a process, in a fully-digested state.
//
// See process_state.h for documentation.
//
// Author: Mark Mentovai

#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/code_modules.h"

namespace google_breakpad {

ProcessState::~ProcessState() {
  Clear();
}

void ProcessState::Clear() {
  time_date_stamp_ = 0;
  process_create_time_ = 0;
  crashed_ = false;
  crash_reason_.clear();
  crash_address_ = 0;
  assertion_.clear();
  requesting_thread_ = -1;
  for (vector<CallStack *>::const_iterator iterator = threads_.begin();
       iterator != threads_.end();
       ++iterator) {
    delete *iterator;
  }
  threads_.clear();
  system_info_.Clear();
  // modules_without_symbols_ and modules_with_corrupt_symbols_ DO NOT own
  // the underlying CodeModule pointers.  Just clear the vectors.
  modules_without_symbols_.clear();
  modules_with_corrupt_symbols_.clear();
  delete modules_;
  modules_ = NULL;
}

}  // namespace google_breakpad
