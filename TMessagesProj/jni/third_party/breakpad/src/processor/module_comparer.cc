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
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
// module_comparer.cc: ModuleComparer implementation.
// See module_comparer.h for documentation.
//
// Author: lambxsy@google.com (Siyang Xie)

#include "processor/module_comparer.h"

#include <map>
#include <string>

#include "common/scoped_ptr.h"
#include "processor/basic_code_module.h"
#include "processor/logging.h"

#define ASSERT_TRUE(condition) \
  if (!(condition)) { \
    BPLOG(ERROR) << "FAIL: " << #condition << " @ " \
                 << __FILE__ << ":" << __LINE__; \
    return false; \
  }

#define ASSERT_FALSE(condition) ASSERT_TRUE(!(condition))

namespace google_breakpad {

bool ModuleComparer::Compare(const string &symbol_data) {
  scoped_ptr<BasicModule> basic_module(new BasicModule("test_module"));
  scoped_ptr<FastModule> fast_module(new FastModule("test_module"));

  // Load symbol data into basic_module
  scoped_array<char> buffer(new char[symbol_data.size() + 1]);
  memcpy(buffer.get(), symbol_data.c_str(), symbol_data.size());
  buffer.get()[symbol_data.size()] = '\0';
  ASSERT_TRUE(basic_module->LoadMapFromMemory(buffer.get(),
                                              symbol_data.size() + 1));
  buffer.reset();

  // Serialize BasicSourceLineResolver::Module.
  unsigned int serialized_size = 0;
  scoped_array<char> serialized_data(
      serializer_.Serialize(*(basic_module.get()), &serialized_size));
  ASSERT_TRUE(serialized_data.get());
  BPLOG(INFO) << "Serialized size = " << serialized_size << " Bytes";

  // Load FastSourceLineResolver::Module using serialized data.
  ASSERT_TRUE(fast_module->LoadMapFromMemory(serialized_data.get(),
                                             serialized_size));
  ASSERT_TRUE(fast_module->IsCorrupt() == basic_module->IsCorrupt());

  // Compare FastSourceLineResolver::Module with
  // BasicSourceLineResolver::Module.
  ASSERT_TRUE(CompareModule(basic_module.get(), fast_module.get()));

  return true;
}

// Traversal the content of module and do comparison
bool ModuleComparer::CompareModule(const BasicModule *basic_module,
                                  const FastModule *fast_module) const {
  // Compare name_.
  ASSERT_TRUE(basic_module->name_ == fast_module->name_);

  // Compare files_:
  {
    BasicModule::FileMap::const_iterator iter1 = basic_module->files_.begin();
    FastModule::FileMap::iterator iter2 = fast_module->files_.begin();
    while (iter1 != basic_module->files_.end()
        && iter2 != fast_module->files_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      string tmp(iter2.GetValuePtr());
      ASSERT_TRUE(iter1->second == tmp);
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_module->files_.end());
    ASSERT_TRUE(iter2 == fast_module->files_.end());
  }

  // Compare functions_:
  {
    RangeMap<MemAddr, linked_ptr<BasicFunc> >::MapConstIterator iter1;
    StaticRangeMap<MemAddr, FastFunc>::MapConstIterator iter2;
    iter1 = basic_module->functions_.map_.begin();
    iter2 = fast_module->functions_.map_.begin();
    while (iter1 != basic_module->functions_.map_.end()
        && iter2 != fast_module->functions_.map_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      ASSERT_TRUE(iter1->second.base() == iter2.GetValuePtr()->base());
      ASSERT_TRUE(CompareFunction(
          iter1->second.entry().get(), iter2.GetValuePtr()->entryptr()));
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_module->functions_.map_.end());
    ASSERT_TRUE(iter2 == fast_module->functions_.map_.end());
  }

  // Compare public_symbols_:
  {
    AddressMap<MemAddr, linked_ptr<BasicPubSymbol> >::MapConstIterator iter1;
    StaticAddressMap<MemAddr, FastPubSymbol>::MapConstIterator iter2;
    iter1 = basic_module->public_symbols_.map_.begin();
    iter2 = fast_module->public_symbols_.map_.begin();
    while (iter1 != basic_module->public_symbols_.map_.end()
          && iter2 != fast_module->public_symbols_.map_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      ASSERT_TRUE(ComparePubSymbol(
          iter1->second.get(), iter2.GetValuePtr()));
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_module->public_symbols_.map_.end());
    ASSERT_TRUE(iter2 == fast_module->public_symbols_.map_.end());
  }

  // Compare windows_frame_info_[]:
  for (int i = 0; i < WindowsFrameInfo::STACK_INFO_LAST; ++i) {
    ASSERT_TRUE(CompareCRM(&(basic_module->windows_frame_info_[i]),
                           &(fast_module->windows_frame_info_[i])));
  }

  // Compare cfi_initial_rules_:
  {
    RangeMap<MemAddr, string>::MapConstIterator iter1;
    StaticRangeMap<MemAddr, char>::MapConstIterator iter2;
    iter1 = basic_module->cfi_initial_rules_.map_.begin();
    iter2 = fast_module->cfi_initial_rules_.map_.begin();
    while (iter1 != basic_module->cfi_initial_rules_.map_.end()
        && iter2 != fast_module->cfi_initial_rules_.map_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      ASSERT_TRUE(iter1->second.base() == iter2.GetValuePtr()->base());
      string tmp(iter2.GetValuePtr()->entryptr());
      ASSERT_TRUE(iter1->second.entry() == tmp);
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_module->cfi_initial_rules_.map_.end());
    ASSERT_TRUE(iter2 == fast_module->cfi_initial_rules_.map_.end());
  }

  // Compare cfi_delta_rules_:
  {
    map<MemAddr, string>::const_iterator iter1;
    StaticMap<MemAddr, char>::iterator iter2;
    iter1 = basic_module->cfi_delta_rules_.begin();
    iter2 = fast_module->cfi_delta_rules_.begin();
    while (iter1 != basic_module->cfi_delta_rules_.end()
        && iter2 != fast_module->cfi_delta_rules_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      string tmp(iter2.GetValuePtr());
      ASSERT_TRUE(iter1->second == tmp);
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_module->cfi_delta_rules_.end());
    ASSERT_TRUE(iter2 == fast_module->cfi_delta_rules_.end());
  }

  return true;
}

bool ModuleComparer::CompareFunction(const BasicFunc *basic_func,
                                    const FastFunc *fast_func_raw) const {
  FastFunc* fast_func = new FastFunc();
  fast_func->CopyFrom(fast_func_raw);
  ASSERT_TRUE(basic_func->name == fast_func->name);
  ASSERT_TRUE(basic_func->address == fast_func->address);
  ASSERT_TRUE(basic_func->size == fast_func->size);

  // compare range map of lines:
  RangeMap<MemAddr, linked_ptr<BasicLine> >::MapConstIterator iter1;
  StaticRangeMap<MemAddr, FastLine>::MapConstIterator iter2;
  iter1 = basic_func->lines.map_.begin();
  iter2 = fast_func->lines.map_.begin();
  while (iter1 != basic_func->lines.map_.end()
      && iter2 != fast_func->lines.map_.end()) {
    ASSERT_TRUE(iter1->first == iter2.GetKey());
    ASSERT_TRUE(iter1->second.base() == iter2.GetValuePtr()->base());
    ASSERT_TRUE(CompareLine(iter1->second.entry().get(),
                            iter2.GetValuePtr()->entryptr()));
    ++iter1;
    ++iter2;
  }
  ASSERT_TRUE(iter1 == basic_func->lines.map_.end());
  ASSERT_TRUE(iter2 == fast_func->lines.map_.end());

  delete fast_func;
  return true;
}

bool ModuleComparer::CompareLine(const BasicLine *basic_line,
                                const FastLine *fast_line_raw) const {
  FastLine *fast_line = new FastLine;
  fast_line->CopyFrom(fast_line_raw);

  ASSERT_TRUE(basic_line->address == fast_line->address);
  ASSERT_TRUE(basic_line->size == fast_line->size);
  ASSERT_TRUE(basic_line->source_file_id == fast_line->source_file_id);
  ASSERT_TRUE(basic_line->line == fast_line->line);

  delete fast_line;
  return true;
}

bool ModuleComparer::ComparePubSymbol(const BasicPubSymbol* basic_ps,
                                     const FastPubSymbol* fastps_raw) const {
  FastPubSymbol *fast_ps = new FastPubSymbol;
  fast_ps->CopyFrom(fastps_raw);
  ASSERT_TRUE(basic_ps->name == fast_ps->name);
  ASSERT_TRUE(basic_ps->address == fast_ps->address);
  ASSERT_TRUE(basic_ps->parameter_size == fast_ps->parameter_size);
  delete fast_ps;
  return true;
}

bool ModuleComparer::CompareWFI(const WindowsFrameInfo& wfi1,
                               const WindowsFrameInfo& wfi2) const {
  ASSERT_TRUE(wfi1.type_ == wfi2.type_);
  ASSERT_TRUE(wfi1.valid == wfi2.valid);
  ASSERT_TRUE(wfi1.prolog_size == wfi2.prolog_size);
  ASSERT_TRUE(wfi1.epilog_size == wfi2.epilog_size);
  ASSERT_TRUE(wfi1.parameter_size == wfi2.parameter_size);
  ASSERT_TRUE(wfi1.saved_register_size == wfi2.saved_register_size);
  ASSERT_TRUE(wfi1.local_size == wfi2.local_size);
  ASSERT_TRUE(wfi1.max_stack_size == wfi2.max_stack_size);
  ASSERT_TRUE(wfi1.allocates_base_pointer == wfi2.allocates_base_pointer);
  ASSERT_TRUE(wfi1.program_string == wfi2.program_string);
  return true;
}

// Compare ContainedRangeMap
bool ModuleComparer::CompareCRM(
    const ContainedRangeMap<MemAddr, linked_ptr<WFI> >* basic_crm,
    const StaticContainedRangeMap<MemAddr, char>* fast_crm) const {
  ASSERT_TRUE(basic_crm->base_ == fast_crm->base_);

  if (!basic_crm->entry_.get() || !fast_crm->entry_ptr_) {
    // empty entry:
    ASSERT_TRUE(!basic_crm->entry_.get() && !fast_crm->entry_ptr_);
  } else {
    WFI newwfi;
    newwfi.CopyFrom(fast_resolver_->CopyWFI(fast_crm->entry_ptr_));
    ASSERT_TRUE(CompareWFI(*(basic_crm->entry_.get()), newwfi));
  }

  if ((!basic_crm->map_ || basic_crm->map_->empty())
      || fast_crm->map_.empty()) {
    ASSERT_TRUE((!basic_crm->map_ || basic_crm->map_->empty())
               && fast_crm->map_.empty());
  } else {
    ContainedRangeMap<MemAddr, linked_ptr<WFI> >::MapConstIterator iter1;
    StaticContainedRangeMap<MemAddr, char>::MapConstIterator iter2;
    iter1 = basic_crm->map_->begin();
    iter2 = fast_crm->map_.begin();
    while (iter1 != basic_crm->map_->end()
        && iter2 != fast_crm->map_.end()) {
      ASSERT_TRUE(iter1->first == iter2.GetKey());
      StaticContainedRangeMap<MemAddr, char> *child =
          new StaticContainedRangeMap<MemAddr, char>(
              reinterpret_cast<const char*>(iter2.GetValuePtr()));
      ASSERT_TRUE(CompareCRM(iter1->second, child));
      delete child;
      ++iter1;
      ++iter2;
    }
    ASSERT_TRUE(iter1 == basic_crm->map_->end());
    ASSERT_TRUE(iter2 == fast_crm->map_.end());
  }

  return true;
}

}  // namespace google_breakpad
