// Copyright (c) 2011 Google Inc.
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

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// module.cc: Implement google_breakpad::Module.  See module.h.

#include "common/module.h"

#include <assert.h>
#include <errno.h>
#include <stdio.h>
#include <string.h>

#include <iostream>
#include <utility>

namespace google_breakpad {

using std::dec;
using std::endl;
using std::hex;


Module::Module(const string &name, const string &os,
               const string &architecture, const string &id) :
    name_(name),
    os_(os),
    architecture_(architecture),
    id_(id),
    load_address_(0) { }

Module::~Module() {
  for (FileByNameMap::iterator it = files_.begin(); it != files_.end(); ++it)
    delete it->second;
  for (FunctionSet::iterator it = functions_.begin();
       it != functions_.end(); ++it) {
    delete *it;
  }
  for (vector<StackFrameEntry *>::iterator it = stack_frame_entries_.begin();
       it != stack_frame_entries_.end(); ++it) {
    delete *it;
  }
  for (ExternSet::iterator it = externs_.begin(); it != externs_.end(); ++it)
    delete *it;
}

void Module::SetLoadAddress(Address address) {
  load_address_ = address;
}

void Module::AddFunction(Function *function) {
  // FUNC lines must not hold an empty name, so catch the problem early if
  // callers try to add one.
  assert(!function->name.empty());

  // FUNCs are better than PUBLICs as they come with sizes, so remove an extern
  // with the same address if present.
  Extern ext(function->address);
  ExternSet::iterator it_ext = externs_.find(&ext);
  if (it_ext == externs_.end() &&
      architecture_ == "arm" &&
      (function->address & 0x1) == 0) {
    // ARM THUMB functions have bit 0 set. ARM64 does not have THUMB.
    Extern arm_thumb_ext(function->address | 0x1);
    it_ext = externs_.find(&arm_thumb_ext);
  }
  if (it_ext != externs_.end()) {
    delete *it_ext;
    externs_.erase(it_ext);
  }
#if _DEBUG
  {
    // There should be no other PUBLIC symbols that overlap with the function.
    Extern debug_ext(function->address);
    ExternSet::iterator it_debug = externs_.lower_bound(&ext);
    assert(it_debug == externs_.end() ||
           (*it_debug)->address >= function->address + function->size);
  }
#endif

  std::pair<FunctionSet::iterator,bool> ret = functions_.insert(function);
  if (!ret.second && (*ret.first != function)) {
    // Free the duplicate that was not inserted because this Module
    // now owns it.
    delete function;
  }
}

void Module::AddFunctions(vector<Function *>::iterator begin,
                          vector<Function *>::iterator end) {
  for (vector<Function *>::iterator it = begin; it != end; ++it)
    AddFunction(*it);
}

void Module::AddStackFrameEntry(StackFrameEntry *stack_frame_entry) {
  stack_frame_entries_.push_back(stack_frame_entry);
}

void Module::AddExtern(Extern *ext) {
  std::pair<ExternSet::iterator,bool> ret = externs_.insert(ext);
  if (!ret.second) {
    // Free the duplicate that was not inserted because this Module
    // now owns it.
    delete ext;
  }
}

void Module::GetFunctions(vector<Function *> *vec,
                          vector<Function *>::iterator i) {
  vec->insert(i, functions_.begin(), functions_.end());
}

void Module::GetExterns(vector<Extern *> *vec,
                        vector<Extern *>::iterator i) {
  vec->insert(i, externs_.begin(), externs_.end());
}

Module::File *Module::FindFile(const string &name) {
  // A tricky bit here.  The key of each map entry needs to be a
  // pointer to the entry's File's name string.  This means that we
  // can't do the initial lookup with any operation that would create
  // an empty entry for us if the name isn't found (like, say,
  // operator[] or insert do), because such a created entry's key will
  // be a pointer the string passed as our argument.  Since the key of
  // a map's value type is const, we can't fix it up once we've
  // created our file.  lower_bound does the lookup without doing an
  // insertion, and returns a good hint iterator to pass to insert.
  // Our "destiny" is where we belong, whether we're there or not now.
  FileByNameMap::iterator destiny = files_.lower_bound(&name);
  if (destiny == files_.end()
      || *destiny->first != name) {  // Repeated string comparison, boo hoo.
    File *file = new File(name);
    file->source_id = -1;
    destiny = files_.insert(destiny,
                            FileByNameMap::value_type(&file->name, file));
  }
  return destiny->second;
}

Module::File *Module::FindFile(const char *name) {
  string name_string = name;
  return FindFile(name_string);
}

Module::File *Module::FindExistingFile(const string &name) {
  FileByNameMap::iterator it = files_.find(&name);
  return (it == files_.end()) ? NULL : it->second;
}

void Module::GetFiles(vector<File *> *vec) {
  vec->clear();
  for (FileByNameMap::iterator it = files_.begin(); it != files_.end(); ++it)
    vec->push_back(it->second);
}

void Module::GetStackFrameEntries(vector<StackFrameEntry *> *vec) const {
  *vec = stack_frame_entries_;
}

void Module::AssignSourceIds() {
  // First, give every source file an id of -1.
  for (FileByNameMap::iterator file_it = files_.begin();
       file_it != files_.end(); ++file_it) {
    file_it->second->source_id = -1;
  }

  // Next, mark all files actually cited by our functions' line number
  // info, by setting each one's source id to zero.
  for (FunctionSet::const_iterator func_it = functions_.begin();
       func_it != functions_.end(); ++func_it) {
    Function *func = *func_it;
    for (vector<Line>::iterator line_it = func->lines.begin();
         line_it != func->lines.end(); ++line_it)
      line_it->file->source_id = 0;
  }

  // Finally, assign source ids to those files that have been marked.
  // We could have just assigned source id numbers while traversing
  // the line numbers, but doing it this way numbers the files in
  // lexicographical order by name, which is neat.
  int next_source_id = 0;
  for (FileByNameMap::iterator file_it = files_.begin();
       file_it != files_.end(); ++file_it) {
    if (!file_it->second->source_id)
      file_it->second->source_id = next_source_id++;
  }
}

bool Module::ReportError() {
  fprintf(stderr, "error writing symbol file: %s\n",
          strerror(errno));
  return false;
}

bool Module::WriteRuleMap(const RuleMap &rule_map, std::ostream &stream) {
  for (RuleMap::const_iterator it = rule_map.begin();
       it != rule_map.end(); ++it) {
    if (it != rule_map.begin())
      stream << ' ';
    stream << it->first << ": " << it->second;
  }
  return stream.good();
}

bool Module::Write(std::ostream &stream, SymbolData symbol_data) {
  stream << "MODULE " << os_ << " " << architecture_ << " "
         << id_ << " " << name_ << endl;
  if (!stream.good())
    return ReportError();

  if (symbol_data != ONLY_CFI) {
    AssignSourceIds();

    // Write out files.
    for (FileByNameMap::iterator file_it = files_.begin();
         file_it != files_.end(); ++file_it) {
      File *file = file_it->second;
      if (file->source_id >= 0) {
        stream << "FILE " << file->source_id << " " <<  file->name << endl;
        if (!stream.good())
          return ReportError();
      }
    }

    // Write out functions and their lines.
    for (FunctionSet::const_iterator func_it = functions_.begin();
         func_it != functions_.end(); ++func_it) {
      Function *func = *func_it;
      stream << "FUNC " << hex
             << (func->address - load_address_) << " "
             << func->size << " "
             << func->parameter_size << " "
             << func->name << dec << endl;
      if (!stream.good())
        return ReportError();

      for (vector<Line>::iterator line_it = func->lines.begin();
           line_it != func->lines.end(); ++line_it) {
        stream << hex
               << (line_it->address - load_address_) << " "
               << line_it->size << " "
               << dec
               << line_it->number << " "
               << line_it->file->source_id << endl;
        if (!stream.good())
          return ReportError();
      }
    }

    // Write out 'PUBLIC' records.
    for (ExternSet::const_iterator extern_it = externs_.begin();
         extern_it != externs_.end(); ++extern_it) {
      Extern *ext = *extern_it;
      stream << "PUBLIC " << hex
             << (ext->address - load_address_) << " 0 "
             << ext->name << dec << endl;
    }
  }

  if (symbol_data != NO_CFI) {
    // Write out 'STACK CFI INIT' and 'STACK CFI' records.
    vector<StackFrameEntry *>::const_iterator frame_it;
    for (frame_it = stack_frame_entries_.begin();
         frame_it != stack_frame_entries_.end(); ++frame_it) {
      StackFrameEntry *entry = *frame_it;
      stream << "STACK CFI INIT " << hex
             << (entry->address - load_address_) << " "
             << entry->size << " " << dec;
      if (!stream.good()
          || !WriteRuleMap(entry->initial_rules, stream))
        return ReportError();

      stream << endl;

      // Write out this entry's delta rules as 'STACK CFI' records.
      for (RuleChangeMap::const_iterator delta_it = entry->rule_changes.begin();
           delta_it != entry->rule_changes.end(); ++delta_it) {
        stream << "STACK CFI " << hex
               << (delta_it->first - load_address_) << " " << dec;
        if (!stream.good()
            || !WriteRuleMap(delta_it->second, stream))
          return ReportError();

        stream << endl;
      }
    }
  }

  return true;
}

}  // namespace google_breakpad
