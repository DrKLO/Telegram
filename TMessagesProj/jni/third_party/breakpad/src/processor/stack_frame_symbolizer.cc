// Copyright (c) 2012 Google Inc.
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

// Implementation of StackFrameSymbolizer, which encapsulates the logic of how
// SourceLineResolverInterface interacts with SymbolSupplier to fill source
// line information in a stack frame, and also looks up WindowsFrameInfo or
// CFIFrameInfo for a stack frame.

#include "google_breakpad/processor/stack_frame_symbolizer.h"

#include <assert.h>

#include "common/scoped_ptr.h"
#include "google_breakpad/processor/code_module.h"
#include "google_breakpad/processor/code_modules.h"
#include "google_breakpad/processor/source_line_resolver_interface.h"
#include "google_breakpad/processor/stack_frame.h"
#include "google_breakpad/processor/symbol_supplier.h"
#include "google_breakpad/processor/system_info.h"
#include "processor/linked_ptr.h"
#include "processor/logging.h"

namespace google_breakpad {

StackFrameSymbolizer::StackFrameSymbolizer(
    SymbolSupplier* supplier,
    SourceLineResolverInterface* resolver) : supplier_(supplier),
                                             resolver_(resolver) { }

StackFrameSymbolizer::SymbolizerResult StackFrameSymbolizer::FillSourceLineInfo(
    const CodeModules* modules,
    const SystemInfo* system_info,
    StackFrame* frame) {
  assert(frame);

  if (!modules) return kError;
  const CodeModule* module = modules->GetModuleForAddress(frame->instruction);
  if (!module) return kError;
  frame->module = module;

  if (!resolver_) return kError;  // no resolver.
  // If module is known to have missing symbol file, return.
  if (no_symbol_modules_.find(module->code_file()) !=
      no_symbol_modules_.end()) {
    return kError;
  }

  // If module is already loaded, go ahead to fill source line info and return.
  if (resolver_->HasModule(frame->module)) {
    resolver_->FillSourceLineInfo(frame);
    return resolver_->IsModuleCorrupt(frame->module) ?
        kWarningCorruptSymbols : kNoError;
  }

  // Module needs to fetch symbol file. First check to see if supplier exists.
  if (!supplier_) {
    return kError;
  }

  // Start fetching symbol from supplier.
  string symbol_file;
  char* symbol_data = NULL;
  size_t symbol_data_size;
  SymbolSupplier::SymbolResult symbol_result = supplier_->GetCStringSymbolData(
      module, system_info, &symbol_file, &symbol_data, &symbol_data_size);

  switch (symbol_result) {
    case SymbolSupplier::FOUND: {
      bool load_success = resolver_->LoadModuleUsingMemoryBuffer(
          frame->module,
          symbol_data,
          symbol_data_size);
      if (resolver_->ShouldDeleteMemoryBufferAfterLoadModule()) {
        supplier_->FreeSymbolData(module);
      }

      if (load_success) {
        resolver_->FillSourceLineInfo(frame);
        return resolver_->IsModuleCorrupt(frame->module) ?
            kWarningCorruptSymbols : kNoError;
      } else {
        BPLOG(ERROR) << "Failed to load symbol file in resolver.";
        no_symbol_modules_.insert(module->code_file());
        return kError;
      }
    }

    case SymbolSupplier::NOT_FOUND:
      no_symbol_modules_.insert(module->code_file());
      return kError;

    case SymbolSupplier::INTERRUPT:
      return kInterrupt;

    default:
      BPLOG(ERROR) << "Unknown SymbolResult enum: " << symbol_result;
      return kError;
  }
  return kError;
}

WindowsFrameInfo* StackFrameSymbolizer::FindWindowsFrameInfo(
    const StackFrame* frame) {
  return resolver_ ? resolver_->FindWindowsFrameInfo(frame) : NULL;
}

CFIFrameInfo* StackFrameSymbolizer::FindCFIFrameInfo(
    const StackFrame* frame) {
  return resolver_ ? resolver_->FindCFIFrameInfo(frame) : NULL;
}

}  // namespace google_breakpad
