// Copyright (c) 2010, Google Inc.
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
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE

#include <stdlib.h>
#include <unistd.h>

#include <string>

#include "breakpad_googletest_includes.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/minidump_processor.h"
#include "google_breakpad/processor/process_state.h"
#include "processor/simple_symbol_supplier.h"

namespace {

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::MinidumpProcessor;
using google_breakpad::ProcessState;
using google_breakpad::SimpleSymbolSupplier;

string TestDataDir() {
  return string(getenv("srcdir") ? getenv("srcdir") : ".") +
      "/src/processor/testdata";
}

// Find the given dump file in <srcdir>/src/processor/testdata, process it,
// and get the exploitability rating. Returns EXPLOITABILITY_ERR_PROCESSING
// if the crash dump can't be processed.
google_breakpad::ExploitabilityRating
ExploitabilityFor(const string& filename) {
  SimpleSymbolSupplier supplier(TestDataDir() + "/symbols");
  BasicSourceLineResolver resolver;
  MinidumpProcessor processor(&supplier, &resolver, true);
  ProcessState state;

  string minidump_file = TestDataDir() + "/" + filename;

  if (processor.Process(minidump_file, &state) !=
      google_breakpad::PROCESS_OK) {
    return google_breakpad::EXPLOITABILITY_ERR_PROCESSING;
  }

  return state.exploitability();
}

TEST(ExploitabilityTest, TestWindowsEngine) {
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av_block_write.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av_clobber_write.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av_conditional.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av_then_jmp.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_read_av_xchg_write.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_write_av.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("ascii_write_av_arg_to_call.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_NONE,
            ExploitabilityFor("null_read_av.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_NONE,
            ExploitabilityFor("null_write_av.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_NONE,
            ExploitabilityFor("stack_exhaustion.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("exec_av_on_stack.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_MEDIUM,
            ExploitabilityFor("write_av_non_null.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_LOW,
            ExploitabilityFor("read_av_non_null.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_LOW,
            ExploitabilityFor("read_av_clobber_write.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_LOW,
            ExploitabilityFor("read_av_conditional.dmp"));
}

TEST(ExploitabilityTest, TestLinuxEngine) {
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_NONE,
            ExploitabilityFor("linux_null_read_av.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("linux_overflow.dmp"));
  ASSERT_EQ(google_breakpad::EXPLOITABILITY_HIGH,
            ExploitabilityFor("linux_stacksmash.dmp"));
}
}
