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

// minidump_stackwalk.cc: Process a minidump with MinidumpProcessor, printing
// the results, including stack traces.
//
// Author: Mark Mentovai

#include <stdio.h>
#include <string.h>

#include <string>
#include <vector>

#include "common/scoped_ptr.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/basic_source_line_resolver.h"
#include "google_breakpad/processor/minidump.h"
#include "google_breakpad/processor/minidump_processor.h"
#include "google_breakpad/processor/process_state.h"
#include "processor/logging.h"
#include "processor/simple_symbol_supplier.h"
#include "processor/stackwalk_common.h"


namespace {

using google_breakpad::BasicSourceLineResolver;
using google_breakpad::Minidump;
using google_breakpad::MinidumpProcessor;
using google_breakpad::ProcessState;
using google_breakpad::SimpleSymbolSupplier;
using google_breakpad::scoped_ptr;

// Processes |minidump_file| using MinidumpProcessor.  |symbol_path|, if
// non-empty, is the base directory of a symbol storage area, laid out in
// the format required by SimpleSymbolSupplier.  If such a storage area
// is specified, it is made available for use by the MinidumpProcessor.
//
// Returns the value of MinidumpProcessor::Process.  If processing succeeds,
// prints identifying OS and CPU information from the minidump, crash
// information if the minidump was produced as a result of a crash, and
// call stacks for each thread contained in the minidump.  All information
// is printed to stdout.
bool PrintMinidumpProcess(const string &minidump_file,
                          const std::vector<string> &symbol_paths,
                          bool machine_readable,
                          bool output_stack_contents) {
  scoped_ptr<SimpleSymbolSupplier> symbol_supplier;
  if (!symbol_paths.empty()) {
    // TODO(mmentovai): check existence of symbol_path if specified?
    symbol_supplier.reset(new SimpleSymbolSupplier(symbol_paths));
  }

  BasicSourceLineResolver resolver;
  MinidumpProcessor minidump_processor(symbol_supplier.get(), &resolver);

  // Process the minidump.
  Minidump dump(minidump_file);
  if (!dump.Read()) {
     BPLOG(ERROR) << "Minidump " << dump.path() << " could not be read";
     return false;
  }
  ProcessState process_state;
  if (minidump_processor.Process(&dump, &process_state) !=
      google_breakpad::PROCESS_OK) {
    BPLOG(ERROR) << "MinidumpProcessor::Process failed";
    return false;
  }

  if (machine_readable) {
    PrintProcessStateMachineReadable(process_state);
  } else {
    PrintProcessState(process_state, output_stack_contents, &resolver);
  }

  return true;
}

void usage(const char *program_name) {
  fprintf(stderr, "usage: %s [-m|-s] <minidump-file> [symbol-path ...]\n"
          "    -m : Output in machine-readable format\n"
          "    -s : Output stack contents\n",
          program_name);
}

}  // namespace

int main(int argc, char **argv) {
  BPLOG_INIT(&argc, &argv);

  if (argc < 2) {
    usage(argv[0]);
    return 1;
  }

  const char *minidump_file;
  bool machine_readable = false;
  bool output_stack_contents = false;
  int symbol_path_arg;

  if (strcmp(argv[1], "-m") == 0) {
    if (argc < 3) {
      usage(argv[0]);
      return 1;
    }

    machine_readable = true;
    minidump_file = argv[2];
    symbol_path_arg = 3;
  } else if (strcmp(argv[1], "-s") == 0) {
    if (argc < 3) {
      usage(argv[0]);
      return 1;
    }

    output_stack_contents = true;
    minidump_file = argv[2];
    symbol_path_arg = 3;
  } else {
    minidump_file = argv[1];
    symbol_path_arg = 2;
  }

  // extra arguments are symbol paths
  std::vector<string> symbol_paths;
  if (argc > symbol_path_arg) {
    for (int argi = symbol_path_arg; argi < argc; ++argi)
      symbol_paths.push_back(argv[argi]);
  }

  return PrintMinidumpProcess(minidump_file,
                              symbol_paths,
                              machine_readable,
                              output_stack_contents) ? 0 : 1;
}
