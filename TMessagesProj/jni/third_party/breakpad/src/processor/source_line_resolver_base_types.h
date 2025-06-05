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

// source_line_resolver_base_types.h: definition of nested classes/structs in
// SourceLineResolverBase.  It moves the definitions out of
// source_line_resolver_base.cc, so that other classes may have access
// to these private nested types without including source_line_resolver_base.cc
// In addition, Module is defined as a pure abstract class to be implemented by
// each concrete source line resolver class.
//
// See source_line_resolver_base.h for more documentation.
//
// Author: Siyang Xie (lambxsy@google.com)

#include <stdio.h>

#include <map>
#include <string>

#include "google_breakpad/common/breakpad_types.h"
#include "google_breakpad/processor/source_line_resolver_base.h"
#include "google_breakpad/processor/stack_frame.h"
#include "processor/cfi_frame_info.h"
#include "processor/windows_frame_info.h"

#ifndef PROCESSOR_SOURCE_LINE_RESOLVER_BASE_TYPES_H__
#define PROCESSOR_SOURCE_LINE_RESOLVER_BASE_TYPES_H__

namespace google_breakpad {

class SourceLineResolverBase::AutoFileCloser {
 public:
  explicit AutoFileCloser(FILE *file) : file_(file) {}
  ~AutoFileCloser() {
    if (file_)
      fclose(file_);
  }

 private:
  FILE *file_;
};

struct SourceLineResolverBase::Line {
  Line() { }
  Line(MemAddr addr, MemAddr code_size, int file_id, int source_line)
      : address(addr)
      , size(code_size)
      , source_file_id(file_id)
      , line(source_line) { }

  MemAddr address;
  MemAddr size;
  int32_t source_file_id;
  int32_t line;
};

struct SourceLineResolverBase::Function {
  Function() { }
  Function(const string &function_name,
           MemAddr function_address,
           MemAddr code_size,
           int set_parameter_size)
      : name(function_name), address(function_address), size(code_size),
        parameter_size(set_parameter_size) { }

  string name;
  MemAddr address;
  MemAddr size;

  // The size of parameters passed to this function on the stack.
  int32_t parameter_size;
};

struct SourceLineResolverBase::PublicSymbol {
  PublicSymbol() { }
  PublicSymbol(const string& set_name,
               MemAddr set_address,
               int set_parameter_size)
      : name(set_name),
        address(set_address),
        parameter_size(set_parameter_size) {}

  string name;
  MemAddr address;

  // If the public symbol is used as a function entry point, parameter_size
  // is set to the size of the parameters passed to the funciton on the
  // stack, if known.
  int32_t parameter_size;
};

class SourceLineResolverBase::Module {
 public:
  virtual ~Module() { };
  // Loads a map from the given buffer in char* type.
  // Does NOT take ownership of memory_buffer (the caller, source line resolver,
  // is the owner of memory_buffer).
  // The passed in |memory buffer| is of size |memory_buffer_size|.  If it is
  // not null terminated, LoadMapFromMemory will null terminate it by modifying
  // the passed in buffer.
  virtual bool LoadMapFromMemory(char *memory_buffer,
                                 size_t memory_buffer_size) = 0;

  // Tells whether the loaded symbol data is corrupt.  Return value is
  // undefined, if the symbol data hasn't been loaded yet.
  virtual bool IsCorrupt() const = 0;

  // Looks up the given relative address, and fills the StackFrame struct
  // with the result.
  virtual void LookupAddress(StackFrame *frame) const = 0;

  // If Windows stack walking information is available covering ADDRESS,
  // return a WindowsFrameInfo structure describing it. If the information
  // is not available, returns NULL. A NULL return value does not indicate
  // an error. The caller takes ownership of any returned WindowsFrameInfo
  // object.
  virtual WindowsFrameInfo *
  FindWindowsFrameInfo(const StackFrame *frame) const = 0;

  // If CFI stack walking information is available covering ADDRESS,
  // return a CFIFrameInfo structure describing it. If the information
  // is not available, return NULL. The caller takes ownership of any
  // returned CFIFrameInfo object.
  virtual CFIFrameInfo *FindCFIFrameInfo(const StackFrame *frame) const = 0;
 protected:
  virtual bool ParseCFIRuleSet(const string &rule_set,
                               CFIFrameInfo *frame_info) const;
};

}  // namespace google_breakpad

#endif  // PROCESSOR_SOURCE_LINE_RESOLVER_BASE_TYPES_H__
