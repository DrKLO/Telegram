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
//
// fast_source_line_resolver.cc: FastSourceLineResolver is a concrete class that
// implements SourceLineResolverInterface.  Both FastSourceLineResolver and
// BasicSourceLineResolver inherit from SourceLineResolverBase class to reduce
// code redundancy.
//
// See fast_source_line_resolver.h and fast_source_line_resolver_types.h
// for more documentation.
//
// Author: Siyang Xie (lambxsy@google.com)

#include "google_breakpad/processor/fast_source_line_resolver.h"
#include "processor/fast_source_line_resolver_types.h"

#include <map>
#include <string>
#include <utility>

#include "common/scoped_ptr.h"
#include "common/using_std_string.h"
#include "processor/module_factory.h"
#include "processor/simple_serializer-inl.h"

using std::map;
using std::make_pair;

namespace google_breakpad {

FastSourceLineResolver::FastSourceLineResolver()
  : SourceLineResolverBase(new FastModuleFactory) { }

bool FastSourceLineResolver::ShouldDeleteMemoryBufferAfterLoadModule() {
  return false;
}

void FastSourceLineResolver::Module::LookupAddress(StackFrame *frame) const {
  MemAddr address = frame->instruction - frame->module->base_address();

  // First, look for a FUNC record that covers address. Use
  // RetrieveNearestRange instead of RetrieveRange so that, if there
  // is no such function, we can use the next function to bound the
  // extent of the PUBLIC symbol we find, below. This does mean we
  // need to check that address indeed falls within the function we
  // find; do the range comparison in an overflow-friendly way.
  scoped_ptr<Function> func(new Function);
  const Function* func_ptr = 0;
  scoped_ptr<PublicSymbol> public_symbol(new PublicSymbol);
  const PublicSymbol* public_symbol_ptr = 0;
  MemAddr function_base;
  MemAddr function_size;
  MemAddr public_address;

  if (functions_.RetrieveNearestRange(address, func_ptr,
                                      &function_base, &function_size) &&
      address >= function_base && address - function_base < function_size) {
    func.get()->CopyFrom(func_ptr);
    frame->function_name = func->name;
    frame->function_base = frame->module->base_address() + function_base;

    scoped_ptr<Line> line(new Line);
    const Line* line_ptr = 0;
    MemAddr line_base;
    if (func->lines.RetrieveRange(address, line_ptr, &line_base, NULL)) {
      line.get()->CopyFrom(line_ptr);
      FileMap::iterator it = files_.find(line->source_file_id);
      if (it != files_.end()) {
        frame->source_file_name =
            files_.find(line->source_file_id).GetValuePtr();
      }
      frame->source_line = line->line;
      frame->source_line_base = frame->module->base_address() + line_base;
    }
  } else if (public_symbols_.Retrieve(address,
                                      public_symbol_ptr, &public_address) &&
             (!func_ptr || public_address > function_base)) {
    public_symbol.get()->CopyFrom(public_symbol_ptr);
    frame->function_name = public_symbol->name;
    frame->function_base = frame->module->base_address() + public_address;
  }
}

// WFI: WindowsFrameInfo.
// Returns a WFI object reading from a raw memory chunk of data
WindowsFrameInfo FastSourceLineResolver::CopyWFI(const char *raw) {
  const WindowsFrameInfo::StackInfoTypes type =
     static_cast<const WindowsFrameInfo::StackInfoTypes>(
         *reinterpret_cast<const int32_t*>(raw));

  // The first 8 bytes of int data are unused.
  // They correspond to "StackInfoTypes type_;" and "int valid;"
  // data member of WFI.
  const uint32_t *para_uint32 = reinterpret_cast<const uint32_t*>(
      raw + 2 * sizeof(int32_t));

  uint32_t prolog_size = para_uint32[0];;
  uint32_t epilog_size = para_uint32[1];
  uint32_t parameter_size = para_uint32[2];
  uint32_t saved_register_size = para_uint32[3];
  uint32_t local_size = para_uint32[4];
  uint32_t max_stack_size = para_uint32[5];
  const char *boolean = reinterpret_cast<const char*>(para_uint32 + 6);
  bool allocates_base_pointer = (*boolean != 0);
  string program_string = boolean + 1;

  return WindowsFrameInfo(type,
                          prolog_size,
                          epilog_size,
                          parameter_size,
                          saved_register_size,
                          local_size,
                          max_stack_size,
                          allocates_base_pointer,
                          program_string);
}

// Loads a map from the given buffer in char* type.
// Does NOT take ownership of mem_buffer.
// In addition, treat mem_buffer as const char*.
bool FastSourceLineResolver::Module::LoadMapFromMemory(
    char *memory_buffer,
    size_t memory_buffer_size) {
  if (!memory_buffer) return false;

  // Read the "is_corrupt" flag.
  const char *mem_buffer = memory_buffer;
  mem_buffer = SimpleSerializer<bool>::Read(mem_buffer, &is_corrupt_);

  const uint32_t *map_sizes = reinterpret_cast<const uint32_t*>(mem_buffer);

  unsigned int header_size = kNumberMaps_ * sizeof(unsigned int);

  // offsets[]: an array of offset addresses (with respect to mem_buffer),
  // for each "Static***Map" component of Module.
  // "Static***Map": static version of std::map or map wrapper, i.e., StaticMap,
  // StaticAddressMap, StaticContainedRangeMap, and StaticRangeMap.
  unsigned int offsets[kNumberMaps_];
  offsets[0] = header_size;
  for (int i = 1; i < kNumberMaps_; ++i) {
    offsets[i] = offsets[i - 1] + map_sizes[i - 1];
  }

  // Use pointers to construct Static*Map data members in Module:
  int map_id = 0;
  files_ = StaticMap<int, char>(mem_buffer + offsets[map_id++]);
  functions_ =
      StaticRangeMap<MemAddr, Function>(mem_buffer + offsets[map_id++]);
  public_symbols_ =
      StaticAddressMap<MemAddr, PublicSymbol>(mem_buffer + offsets[map_id++]);
  for (int i = 0; i < WindowsFrameInfo::STACK_INFO_LAST; ++i)
    windows_frame_info_[i] =
        StaticContainedRangeMap<MemAddr, char>(mem_buffer + offsets[map_id++]);

  cfi_initial_rules_ =
      StaticRangeMap<MemAddr, char>(mem_buffer + offsets[map_id++]);
  cfi_delta_rules_ = StaticMap<MemAddr, char>(mem_buffer + offsets[map_id++]);

  return true;
}

WindowsFrameInfo *FastSourceLineResolver::Module::FindWindowsFrameInfo(
    const StackFrame *frame) const {
  MemAddr address = frame->instruction - frame->module->base_address();
  scoped_ptr<WindowsFrameInfo> result(new WindowsFrameInfo());

  // We only know about WindowsFrameInfo::STACK_INFO_FRAME_DATA and
  // WindowsFrameInfo::STACK_INFO_FPO. Prefer them in this order.
  // WindowsFrameInfo::STACK_INFO_FRAME_DATA is the newer type that
  // includes its own program string.
  // WindowsFrameInfo::STACK_INFO_FPO is the older type
  // corresponding to the FPO_DATA struct. See stackwalker_x86.cc.
  const char* frame_info_ptr;
  if ((windows_frame_info_[WindowsFrameInfo::STACK_INFO_FRAME_DATA]
       .RetrieveRange(address, frame_info_ptr))
      || (windows_frame_info_[WindowsFrameInfo::STACK_INFO_FPO]
          .RetrieveRange(address, frame_info_ptr))) {
    result->CopyFrom(CopyWFI(frame_info_ptr));
    return result.release();
  }

  // Even without a relevant STACK line, many functions contain
  // information about how much space their parameters consume on the
  // stack. Use RetrieveNearestRange instead of RetrieveRange, so that
  // we can use the function to bound the extent of the PUBLIC symbol,
  // below. However, this does mean we need to check that ADDRESS
  // falls within the retrieved function's range; do the range
  // comparison in an overflow-friendly way.
  scoped_ptr<Function> function(new Function);
  const Function* function_ptr = 0;
  MemAddr function_base, function_size;
  if (functions_.RetrieveNearestRange(address, function_ptr,
                                      &function_base, &function_size) &&
      address >= function_base && address - function_base < function_size) {
    function.get()->CopyFrom(function_ptr);
    result->parameter_size = function->parameter_size;
    result->valid |= WindowsFrameInfo::VALID_PARAMETER_SIZE;
    return result.release();
  }

  // PUBLIC symbols might have a parameter size. Use the function we
  // found above to limit the range the public symbol covers.
  scoped_ptr<PublicSymbol> public_symbol(new PublicSymbol);
  const PublicSymbol* public_symbol_ptr = 0;
  MemAddr public_address;
  if (public_symbols_.Retrieve(address, public_symbol_ptr, &public_address) &&
      (!function_ptr || public_address > function_base)) {
    public_symbol.get()->CopyFrom(public_symbol_ptr);
    result->parameter_size = public_symbol->parameter_size;
  }

  return NULL;
}

CFIFrameInfo *FastSourceLineResolver::Module::FindCFIFrameInfo(
    const StackFrame *frame) const {
  MemAddr address = frame->instruction - frame->module->base_address();
  MemAddr initial_base, initial_size;
  const char* initial_rules = NULL;

  // Find the initial rule whose range covers this address. That
  // provides an initial set of register recovery rules. Then, walk
  // forward from the initial rule's starting address to frame's
  // instruction address, applying delta rules.
  if (!cfi_initial_rules_.RetrieveRange(address, initial_rules,
                                        &initial_base, &initial_size)) {
    return NULL;
  }

  // Create a frame info structure, and populate it with the rules from
  // the STACK CFI INIT record.
  scoped_ptr<CFIFrameInfo> rules(new CFIFrameInfo());
  if (!ParseCFIRuleSet(initial_rules, rules.get()))
    return NULL;

  // Find the first delta rule that falls within the initial rule's range.
  StaticMap<MemAddr, char>::iterator delta =
    cfi_delta_rules_.lower_bound(initial_base);

  // Apply delta rules up to and including the frame's address.
  while (delta != cfi_delta_rules_.end() && delta.GetKey() <= address) {
    ParseCFIRuleSet(delta.GetValuePtr(), rules.get());
    delta++;
  }

  return rules.release();
}

}  // namespace google_breakpad
