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
// source_line_resolver_base.cc: Implementation of SourceLineResolverBase.
//
// See source_line_resolver_base.h and source_line_resolver_base_types.h for
// more documentation.
//
// Author: Siyang Xie (lambxsy@google.com)

#include <stdio.h>
#include <string.h>
#include <sys/stat.h>

#include <map>
#include <utility>

#include "google_breakpad/processor/source_line_resolver_base.h"
#include "processor/source_line_resolver_base_types.h"
#include "processor/module_factory.h"

using std::map;
using std::make_pair;

namespace google_breakpad {

SourceLineResolverBase::SourceLineResolverBase(
    ModuleFactory *module_factory)
  : modules_(new ModuleMap),
    corrupt_modules_(new ModuleSet),
    memory_buffers_(new MemoryMap),
    module_factory_(module_factory) {
}

SourceLineResolverBase::~SourceLineResolverBase() {
  ModuleMap::iterator it;
  // Iterate through ModuleMap and delete all loaded modules.
  for (it = modules_->begin(); it != modules_->end(); ++it) {
    // Delete individual module.
    delete it->second;
  }
  // Delete the map of modules.
  delete modules_;
  modules_ = NULL;

  // Delete the set of corrupt modules.
  delete corrupt_modules_;
  corrupt_modules_ = NULL;

  MemoryMap::iterator iter = memory_buffers_->begin();
  for (; iter != memory_buffers_->end(); ++iter) {
    delete [] iter->second;
  }
  // Delete the map of memory buffers.
  delete memory_buffers_;
  memory_buffers_ = NULL;

  delete module_factory_;
  module_factory_ = NULL;
}

bool SourceLineResolverBase::ReadSymbolFile(const string &map_file,
                                            char **symbol_data,
                                            size_t *symbol_data_size) {
  if (symbol_data == NULL || symbol_data_size == NULL) {
    BPLOG(ERROR) << "Could not Read file into Null memory pointer";
    return false;
  }

  struct stat buf;
  int error_code = stat(map_file.c_str(), &buf);
  if (error_code == -1) {
    string error_string;
    error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "Could not open " << map_file <<
        ", error " << error_code << ": " << error_string;
    return false;
  }

  off_t file_size = buf.st_size;

  // Allocate memory for file contents, plus a null terminator
  // since we may use strtok() on the contents.
  *symbol_data_size = file_size + 1;
  *symbol_data = new char[file_size + 1];

  if (*symbol_data == NULL) {
    BPLOG(ERROR) << "Could not allocate memory for " << map_file;
    return false;
  }

  BPLOG(INFO) << "Opening " << map_file;

  FILE *f = fopen(map_file.c_str(), "rt");
  if (!f) {
    string error_string;
    error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "Could not open " << map_file <<
        ", error " << error_code << ": " << error_string;
    delete [] (*symbol_data);
    *symbol_data = NULL;
    return false;
  }

  AutoFileCloser closer(f);

  int items_read = 0;

  items_read = fread(*symbol_data, 1, file_size, f);

  if (items_read != file_size) {
    string error_string;
    error_code = ErrnoString(&error_string);
    BPLOG(ERROR) << "Could not slurp " << map_file <<
        ", error " << error_code << ": " << error_string;
    delete [] (*symbol_data);
    *symbol_data = NULL;
    return false;
  }

  (*symbol_data)[file_size] = '\0';
  return true;
}

bool SourceLineResolverBase::LoadModule(const CodeModule *module,
                                        const string &map_file) {
  if (module == NULL)
    return false;

  // Make sure we don't already have a module with the given name.
  if (modules_->find(module->code_file()) != modules_->end()) {
    BPLOG(INFO) << "Symbols for module " << module->code_file()
                << " already loaded";
    return false;
  }

  BPLOG(INFO) << "Loading symbols for module " << module->code_file()
              << " from " << map_file;

  char *memory_buffer;
  size_t memory_buffer_size;
  if (!ReadSymbolFile(map_file, &memory_buffer, &memory_buffer_size))
    return false;

  BPLOG(INFO) << "Read symbol file " << map_file << " succeeded";

  bool load_result = LoadModuleUsingMemoryBuffer(module, memory_buffer,
                                                 memory_buffer_size);

  if (load_result && !ShouldDeleteMemoryBufferAfterLoadModule()) {
    // memory_buffer has to stay alive as long as the module.
    memory_buffers_->insert(make_pair(module->code_file(), memory_buffer));
  } else {
    delete [] memory_buffer;
  }

  return load_result;
}

bool SourceLineResolverBase::LoadModuleUsingMapBuffer(
    const CodeModule *module, const string &map_buffer) {
  if (module == NULL)
    return false;

  // Make sure we don't already have a module with the given name.
  if (modules_->find(module->code_file()) != modules_->end()) {
    BPLOG(INFO) << "Symbols for module " << module->code_file()
                << " already loaded";
    return false;
  }

  size_t memory_buffer_size = map_buffer.size() + 1;
  char *memory_buffer = new char[memory_buffer_size];
  if (memory_buffer == NULL) {
    BPLOG(ERROR) << "Could not allocate memory for " << module->code_file();
    return false;
  }

  // Can't use strcpy, as the data may contain '\0's before the end.
  memcpy(memory_buffer, map_buffer.c_str(), map_buffer.size());
  memory_buffer[map_buffer.size()] = '\0';

  bool load_result = LoadModuleUsingMemoryBuffer(module, memory_buffer,
                                                 memory_buffer_size);

  if (load_result && !ShouldDeleteMemoryBufferAfterLoadModule()) {
    // memory_buffer has to stay alive as long as the module.
    memory_buffers_->insert(make_pair(module->code_file(), memory_buffer));
  } else {
    delete [] memory_buffer;
  }

  return load_result;
}

bool SourceLineResolverBase::LoadModuleUsingMemoryBuffer(
    const CodeModule *module,
    char *memory_buffer,
    size_t memory_buffer_size) {
  if (!module)
    return false;

  // Make sure we don't already have a module with the given name.
  if (modules_->find(module->code_file()) != modules_->end()) {
    BPLOG(INFO) << "Symbols for module " << module->code_file()
                << " already loaded";
    return false;
  }

  BPLOG(INFO) << "Loading symbols for module " << module->code_file()
             << " from memory buffer";

  Module *basic_module = module_factory_->CreateModule(module->code_file());

  // Ownership of memory is NOT transfered to Module::LoadMapFromMemory().
  if (!basic_module->LoadMapFromMemory(memory_buffer, memory_buffer_size)) {
    BPLOG(ERROR) << "Too many error while parsing symbol data for module "
                 << module->code_file();
    // Returning false from here would be an indication that the symbols for
    // this module are missing which would be wrong.  Intentionally fall through
    // and add the module to both the modules_ and the corrupt_modules_ lists.
    assert(basic_module->IsCorrupt());
  }

  modules_->insert(make_pair(module->code_file(), basic_module));
  if (basic_module->IsCorrupt()) {
    corrupt_modules_->insert(module->code_file());
  }
  return true;
}

bool SourceLineResolverBase::ShouldDeleteMemoryBufferAfterLoadModule() {
  return true;
}

void SourceLineResolverBase::UnloadModule(const CodeModule *code_module) {
  if (!code_module)
    return;

  ModuleMap::iterator mod_iter = modules_->find(code_module->code_file());
  if (mod_iter != modules_->end()) {
    Module *symbol_module = mod_iter->second;
    delete symbol_module;
    corrupt_modules_->erase(mod_iter->first);
    modules_->erase(mod_iter);
  }

  if (ShouldDeleteMemoryBufferAfterLoadModule()) {
    // No-op.  Because we never store any memory buffers.
  } else {
    // There may be a buffer stored locally, we need to find and delete it.
    MemoryMap::iterator iter = memory_buffers_->find(code_module->code_file());
    if (iter != memory_buffers_->end()) {
      delete [] iter->second;
      memory_buffers_->erase(iter);
    }
  }
}

bool SourceLineResolverBase::HasModule(const CodeModule *module) {
  if (!module)
    return false;
  return modules_->find(module->code_file()) != modules_->end();
}

bool SourceLineResolverBase::IsModuleCorrupt(const CodeModule *module) {
  if (!module)
    return false;
  return corrupt_modules_->find(module->code_file()) != corrupt_modules_->end();
}

void SourceLineResolverBase::FillSourceLineInfo(StackFrame *frame) {
  if (frame->module) {
    ModuleMap::const_iterator it = modules_->find(frame->module->code_file());
    if (it != modules_->end()) {
      it->second->LookupAddress(frame);
    }
  }
}

WindowsFrameInfo *SourceLineResolverBase::FindWindowsFrameInfo(
    const StackFrame *frame) {
  if (frame->module) {
    ModuleMap::const_iterator it = modules_->find(frame->module->code_file());
    if (it != modules_->end()) {
      return it->second->FindWindowsFrameInfo(frame);
    }
  }
  return NULL;
}

CFIFrameInfo *SourceLineResolverBase::FindCFIFrameInfo(
    const StackFrame *frame) {
  if (frame->module) {
    ModuleMap::const_iterator it = modules_->find(frame->module->code_file());
    if (it != modules_->end()) {
      return it->second->FindCFIFrameInfo(frame);
    }
  }
  return NULL;
}

bool SourceLineResolverBase::CompareString::operator()(
    const string &s1, const string &s2) const {
  return strcmp(s1.c_str(), s2.c_str()) < 0;
}

bool SourceLineResolverBase::Module::ParseCFIRuleSet(
    const string &rule_set, CFIFrameInfo *frame_info) const {
  CFIFrameInfoParseHandler handler(frame_info);
  CFIRuleParser parser(&handler);
  return parser.Parse(rule_set);
}

}  // namespace google_breakpad
