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

// basic_code_modules.cc: Contains all of the CodeModule objects that
// were loaded into a single process.
//
// See basic_code_modules.h for documentation.
//
// Author: Mark Mentovai

#include "processor/basic_code_modules.h"

#include <assert.h>

#include "google_breakpad/processor/code_module.h"
#include "processor/linked_ptr.h"
#include "processor/logging.h"
#include "processor/range_map-inl.h"

namespace google_breakpad {

BasicCodeModules::BasicCodeModules(const CodeModules *that)
    : main_address_(0),
      map_(new RangeMap<uint64_t, linked_ptr<const CodeModule> >()) {
  BPLOG_IF(ERROR, !that) << "BasicCodeModules::BasicCodeModules requires "
                            "|that|";
  assert(that);

  const CodeModule *main_module = that->GetMainModule();
  if (main_module)
    main_address_ = main_module->base_address();

  unsigned int count = that->module_count();
  for (unsigned int module_sequence = 0;
       module_sequence < count;
       ++module_sequence) {
    // Make a copy of the module and insert it into the map.  Use
    // GetModuleAtIndex because ordering is unimportant when slurping the
    // entire list, and GetModuleAtIndex may be faster than
    // GetModuleAtSequence.
    linked_ptr<const CodeModule> module(
        that->GetModuleAtIndex(module_sequence)->Copy());
    if (!map_->StoreRange(module->base_address(), module->size(), module)) {
      BPLOG(ERROR) << "Module " << module->code_file() <<
                      " could not be stored";
    }
  }
}

BasicCodeModules::BasicCodeModules()
  : main_address_(0),
    map_(new RangeMap<uint64_t, linked_ptr<const CodeModule> >()) {
}

BasicCodeModules::~BasicCodeModules() {
  delete map_;
}

unsigned int BasicCodeModules::module_count() const {
  return map_->GetCount();
}

const CodeModule* BasicCodeModules::GetModuleForAddress(
    uint64_t address) const {
  linked_ptr<const CodeModule> module;
  if (!map_->RetrieveRange(address, &module, NULL, NULL)) {
    BPLOG(INFO) << "No module at " << HexString(address);
    return NULL;
  }

  return module.get();
}

const CodeModule* BasicCodeModules::GetMainModule() const {
  return GetModuleForAddress(main_address_);
}

const CodeModule* BasicCodeModules::GetModuleAtSequence(
    unsigned int sequence) const {
  linked_ptr<const CodeModule> module;
  if (!map_->RetrieveRangeAtIndex(sequence, &module, NULL, NULL)) {
    BPLOG(ERROR) << "RetrieveRangeAtIndex failed for sequence " << sequence;
    return NULL;
  }

  return module.get();
}

const CodeModule* BasicCodeModules::GetModuleAtIndex(
    unsigned int index) const {
  // This class stores everything in a RangeMap, without any more-efficient
  // way to walk the list of CodeModule objects.  Implement GetModuleAtIndex
  // using GetModuleAtSequence, which meets all of the requirements, and
  // in addition, guarantees ordering.
  return GetModuleAtSequence(index);
}

const CodeModules* BasicCodeModules::Copy() const {
  return new BasicCodeModules(this);
}

}  // namespace google_breakpad
