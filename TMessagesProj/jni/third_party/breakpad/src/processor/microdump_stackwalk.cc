// Copyright (c) 2014 Google Inc.
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

// microdump_stackwalk.cc: Process a microdump with MicrodumpProcessor, printing
// the results, including stack traces.

#include <stdio.h>
#include <string.h>

#include <fstream>
#include <string>
#include <vector>

#include "common/scoped_ptr.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/microdump_processor.h"
#include "google_breakpad/processor/process_state.h"
#include "google_breakpad/processor/stack_frame_symbolizer.h"
#include "processor/logging.h"
#include "processor/simple_symbol_supplier.h"
#include "processor/stackwalk_common.h"


namespace {

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::MicrodumpProcessor;
using google_breakpad::ProcessResult;
using google_breakpad::ProcessState;
using google_breakpad::scoped_ptr;
using google_breakpad::SimpleSymbolSupplier;
using google_breakpad::StackFrameSymbolizer;

// Processes |microdump_file| using MicrodumpProcessor. |symbol_path|, if
// non-empty, is the base directory of a symbol storage area, laid out in
// the format required by SimpleSymbolSupplier.  If such a storage area
// is specified, it is made available for use by the MicrodumpProcessor.
//
// Returns the value of MicrodumpProcessor::Process. If processing succeeds,
// prints identifying OS and CPU information from the microdump, crash
// information and call stacks for the crashing thread.
// All information is printed to stdout.
int PrintMicrodumpProcess(const char* microdump_file,
                          const std::vector<string>& symbol_paths,
                          bool machine_readable) {
  std::ifstream file_stream(microdump_file);
  std::vector<char> bytes;
  file_stream.seekg(0, std::ios_base::end);
  bytes.resize(file_stream.tellg());
  file_stream.seekg(0, std::ios_base::beg);
  file_stream.read(&bytes[0], bytes.size());
  string microdump_content(&bytes[0], bytes.size());

  scoped_ptr<SimpleSymbolSupplier> symbol_supplier;
  if (!symbol_paths.empty()) {
    symbol_supplier.reset(new SimpleSymbolSupplier(symbol_paths));
  }

  BasicSourceLineResolver resolver;
  StackFrameSymbolizer frame_symbolizer(symbol_supplier.get(), &resolver);
  ProcessState process_state;
  MicrodumpProcessor microdump_processor(&frame_symbolizer);
  ProcessResult res = microdump_processor.Process(microdump_content,
                                                  &process_state);

  if (res == google_breakpad::PROCESS_OK) {
    if (machine_readable) {
      PrintProcessStateMachineReadable(process_state);
    } else {
      PrintProcessState(process_state, false, &resolver);
    }
    return 0;
  }

  BPLOG(ERROR) << "MicrodumpProcessor::Process failed (code = " << res << ")";
  return 1;
}

void usage(const char *program_name) {
  fprintf(stderr, "usage: %s [-m] <microdump-file> [symbol-path ...]\n"
          "    -m : Output in machine-readable format\n",
          program_name);
}

}  // namespace

int main(int argc, char** argv) {
  BPLOG_INIT(&argc, &argv);

  if (argc < 2) {
    usage(argv[0]);
    return 1;
  }

  const char* microdump_file;
  bool machine_readable;
  int symbol_path_arg;

  if (strcmp(argv[1], "-m") == 0) {
    if (argc < 3) {
      usage(argv[0]);
      return 1;
    }

    machine_readable = true;
    microdump_file = argv[2];
    symbol_path_arg = 3;
  } else {
    machine_readable = false;
    microdump_file = argv[1];
    symbol_path_arg = 2;
  }

  // extra arguments are symbol paths
  std::vector<string> symbol_paths;
  if (argc > symbol_path_arg) {
    for (int argi = symbol_path_arg; argi < argc; ++argi)
      symbol_paths.push_back(argv[argi]);
  }

  return PrintMicrodumpProcess(microdump_file,
                               symbol_paths,
                               machine_readable);
}
