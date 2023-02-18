// Copyright (c) 2014, Google Inc.
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
//    * Neither the name of Google Inc. nor the names of its
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

// microdump_processor.cc: A microdump processor.
//
// See microdump_processor.h for documentation.

#include "google_breakpad/processor/microdump_processor.h"

#include <assert.h>

#include <string>

#include "common/using_std_string.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/microdump.h"
#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/stackwalker.h"
#include "google_breakpad/processor/stack_frame_symbolizer.h"
#include "processor/logging.h"

namespace google_breakpad {

MicrodumpProcessor::MicrodumpProcessor(StackFrameSymbolizer* frame_symbolizer)
    : frame_symbolizer_(frame_symbolizer) {
  assert(frame_symbolizer);
}

MicrodumpProcessor::~MicrodumpProcessor() {}

ProcessResult MicrodumpProcessor::Process(const string &microdump_contents,
                                          ProcessState* process_state) {
  assert(process_state);

  process_state->Clear();

  if (microdump_contents.empty()) {
    BPLOG(ERROR) << "Microdump is empty.";
    return PROCESS_ERROR_MINIDUMP_NOT_FOUND;
  }

  Microdump microdump(microdump_contents);
  process_state->modules_ = microdump.GetModules()->Copy();
  scoped_ptr<Stackwalker> stackwalker(
      Stackwalker::StackwalkerForCPU(
                            &process_state->system_info_,
                            microdump.GetContext(),
                            microdump.GetMemory(),
                            process_state->modules_,
                            frame_symbolizer_));

  scoped_ptr<CallStack> stack(new CallStack());
  if (stackwalker.get()) {
    if (!stackwalker->Walk(stack.get(),
                           &process_state->modules_without_symbols_,
                           &process_state->modules_with_corrupt_symbols_)) {
      BPLOG(INFO) << "Processing was interrupted.";
      return PROCESS_SYMBOL_SUPPLIER_INTERRUPTED;
    }
  } else {
    BPLOG(ERROR) << "No stackwalker found for microdump.";
    return PROCESS_ERROR_NO_THREAD_LIST;
  }

  process_state->threads_.push_back(stack.release());
  process_state->thread_memory_regions_.push_back(microdump.GetMemory());
  process_state->crashed_ = true;
  process_state->requesting_thread_ = 0;
  process_state->system_info_ = *microdump.GetSystemInfo();

  return PROCESS_OK;
}

}  // namespace google_breakpad
