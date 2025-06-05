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

// minidump_writer_unittest_utils.cc:
// Shared routines used by unittests under client/linux/minidump_writer.

#include <limits.h>
#include <stdlib.h>

#include "client/linux/minidump_writer/minidump_writer_unittest_utils.h"
#include "common/linux/safe_readlink.h"
#include "common/using_std_string.h"

namespace google_breakpad {

string GetHelperBinary() {
  string helper_path;
  char *bindir = getenv("bindir");
  if (bindir) {
    helper_path = string(bindir) + "/";
  } else {
    // Locate helper binary next to the current binary.
    char self_path[PATH_MAX];
    if (!SafeReadLink("/proc/self/exe", self_path)) {
      return "";
    }
    helper_path = string(self_path);
    size_t pos = helper_path.rfind('/');
    if (pos == string::npos) {
      return "";
    }
    helper_path.erase(pos + 1);
  }

  helper_path += "linux_dumper_unittest_helper";

  return helper_path;
}

}  // namespace google_breakpad
