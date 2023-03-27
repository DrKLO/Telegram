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

// Original author: Jim Blandy <jimb@mozilla.com> <jimb@red-bean.com>

// language.cc: Subclasses and singletons for google_breakpad::Language.
// See language.h for details.

#include "common/language.h"

namespace google_breakpad {

// C++ language-specific operations.
class CPPLanguage: public Language {
 public:
  CPPLanguage() {}
  string MakeQualifiedName(const string &parent_name,
                           const string &name) const {
    if (parent_name.empty())
      return name;
    else
      return parent_name + "::" + name;
  }
};

CPPLanguage CPPLanguageSingleton;

// Java language-specific operations.
class JavaLanguage: public Language {
 public:
  string MakeQualifiedName(const string &parent_name,
                           const string &name) const {
    if (parent_name.empty())
      return name;
    else
      return parent_name + "." + name;
  }
};

JavaLanguage JavaLanguageSingleton;

// Assembler language-specific operations.
class AssemblerLanguage: public Language {
  bool HasFunctions() const { return false; }
  string MakeQualifiedName(const string &parent_name,
                           const string &name) const {
    return name;
  }
};

AssemblerLanguage AssemblerLanguageSingleton;

const Language * const Language::CPlusPlus = &CPPLanguageSingleton;
const Language * const Language::Java = &JavaLanguageSingleton;
const Language * const Language::Assembler = &AssemblerLanguageSingleton;

} // namespace google_breakpad
