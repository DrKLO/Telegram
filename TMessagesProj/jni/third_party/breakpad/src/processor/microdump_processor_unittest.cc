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

// Unit test for MicrodumpProcessor.

#include <fstream>
#include <iostream>
#include <string>
#include <vector>

#include "breakpad_googletest_includes.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/call_stack.h"
#include "google_breakpad/processor/microdump_processor.h"
#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/stack_frame.h"
#include "google_breakpad/processor/stack_frame_symbolizer.h"
#include "processor/simple_symbol_supplier.h"
#include "processor/stackwalker_unittest_utils.h"

namespace {

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::MicrodumpProcessor;
using google_breakpad::ProcessState;
using google_breakpad::SimpleSymbolSupplier;
using google_breakpad::StackFrameSymbolizer;

class MicrodumpProcessorTest : public ::testing::Test {
 public:
  MicrodumpProcessorTest()
    : files_path_(string(getenv("srcdir") ? getenv("srcdir") : ".") +
                  "/src/processor/testdata/") {
  }

  void ReadFile(const string& file_name, string* file_contents) {
    assert(file_contents);
    std::ifstream file_stream(file_name.c_str(), std::ios::in);
    ASSERT_TRUE(file_stream.good());
    std::vector<char> bytes;
    file_stream.seekg(0, std::ios_base::end);
    ASSERT_TRUE(file_stream.good());
    bytes.resize(file_stream.tellg());
    file_stream.seekg(0, std::ios_base::beg);
    ASSERT_TRUE(file_stream.good());
    file_stream.read(&bytes[0], bytes.size());
    ASSERT_TRUE(file_stream.good());
    *file_contents = string(&bytes[0], bytes.size());
  }

  google_breakpad::ProcessResult ProcessMicrodump(
      const string& symbols_file,
      const string& microdump_contents,
      ProcessState* state) {
    SimpleSymbolSupplier supplier(symbols_file);
    BasicSourceLineResolver resolver;
    StackFrameSymbolizer frame_symbolizer(&supplier, &resolver);
    MicrodumpProcessor processor(&frame_symbolizer);

    return processor.Process(microdump_contents, state);
  }

  void AnalyzeDump(const string& microdump_file_name, ProcessState* state,
                   bool omit_symbols) {
    string symbols_file = omit_symbols ? "" : files_path_ + "symbols/microdump";
    string microdump_file_path = files_path_ + microdump_file_name;
    string microdump_contents;
    ReadFile(microdump_file_path, &microdump_contents);

    google_breakpad::ProcessResult result =
        ProcessMicrodump(symbols_file, microdump_contents, state);

    ASSERT_EQ(google_breakpad::PROCESS_OK, result);
    ASSERT_TRUE(state->crashed());
    ASSERT_EQ(0, state->requesting_thread());
    ASSERT_EQ(1U, state->threads()->size());

    ASSERT_EQ(2, state->system_info()->cpu_count);
    ASSERT_EQ("android", state->system_info()->os_short);
    ASSERT_EQ("Android", state->system_info()->os);
  }

  string files_path_;
};

TEST_F(MicrodumpProcessorTest, TestProcess_Empty) {
  ProcessState state;
  google_breakpad::ProcessResult result =
      ProcessMicrodump("", "", &state);
  ASSERT_EQ(google_breakpad::PROCESS_ERROR_MINIDUMP_NOT_FOUND, result);
}

TEST_F(MicrodumpProcessorTest, TestProcess_Invalid) {
  ProcessState state;
  google_breakpad::ProcessResult result =
      ProcessMicrodump("", "This is not a valid microdump", &state);
  ASSERT_EQ(google_breakpad::PROCESS_ERROR_NO_THREAD_LIST, result);
}

TEST_F(MicrodumpProcessorTest, TestProcess_MissingSymbols) {
  ProcessState state;
  AnalyzeDump("microdump-arm64.dmp", &state, true /* omit_symbols */);

  ASSERT_EQ(8U, state.modules()->module_count());
  ASSERT_EQ("aarch64", state.system_info()->cpu);
  ASSERT_EQ("OS 64 VERSION INFO", state.system_info()->os_version);
  ASSERT_EQ(1U, state.threads()->size());
  ASSERT_EQ(12U, state.threads()->at(0)->frames()->size());

  ASSERT_EQ("",
            state.threads()->at(0)->frames()->at(0)->function_name);
  ASSERT_EQ("",
            state.threads()->at(0)->frames()->at(3)->function_name);
}

TEST_F(MicrodumpProcessorTest, TestProcess_UnsupportedArch) {
  string microdump_contents =
      "W/google-breakpad(26491): -----BEGIN BREAKPAD MICRODUMP-----\n"
      "W/google-breakpad(26491): O A \"unsupported-arch\"\n"
      "W/google-breakpad(26491): S 0 A48BD840 A48BD000 00002000\n";

  ProcessState state;

  google_breakpad::ProcessResult result =
      ProcessMicrodump("", microdump_contents, &state);

  ASSERT_EQ(google_breakpad::PROCESS_ERROR_NO_THREAD_LIST, result);
}

TEST_F(MicrodumpProcessorTest, TestProcessArm) {
  ProcessState state;
  AnalyzeDump("microdump-arm.dmp", &state, false /* omit_symbols */);

  ASSERT_EQ(6U, state.modules()->module_count());
  ASSERT_EQ("armv7l", state.system_info()->cpu);
  ASSERT_EQ("OS VERSION INFO", state.system_info()->os_version);
  ASSERT_EQ(8U, state.threads()->at(0)->frames()->size());
  ASSERT_EQ("MicrodumpWriterTest_Setup_Test::TestBody",
            state.threads()->at(0)->frames()->at(0)->function_name);
  ASSERT_EQ("testing::Test::Run",
            state.threads()->at(0)->frames()->at(1)->function_name);
  ASSERT_EQ("main",
            state.threads()->at(0)->frames()->at(6)->function_name);
  ASSERT_EQ("breakpad_unittests",
            state.threads()->at(0)->frames()->at(6)->module->code_file());
}

TEST_F(MicrodumpProcessorTest, TestProcessArm64) {
  ProcessState state;
  AnalyzeDump("microdump-arm64.dmp", &state, false /* omit_symbols */);

  ASSERT_EQ(8U, state.modules()->module_count());
  ASSERT_EQ("aarch64", state.system_info()->cpu);
  ASSERT_EQ("OS 64 VERSION INFO", state.system_info()->os_version);
  ASSERT_EQ(9U, state.threads()->at(0)->frames()->size());
  ASSERT_EQ("MicrodumpWriterTest_Setup_Test::TestBody",
            state.threads()->at(0)->frames()->at(0)->function_name);
  ASSERT_EQ("testing::Test::Run",
            state.threads()->at(0)->frames()->at(2)->function_name);
  ASSERT_EQ("main",
            state.threads()->at(0)->frames()->at(7)->function_name);
  ASSERT_EQ("breakpad_unittests",
            state.threads()->at(0)->frames()->at(7)->module->code_file());
}

}  // namespace

int main(int argc, char* argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
