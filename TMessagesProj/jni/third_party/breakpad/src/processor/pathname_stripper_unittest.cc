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

#include <stdio.h>

#include "processor/pathname_stripper.h"
#include "processor/logging.h"

#define ASSERT_TRUE(condition) \
  if (!(condition)) { \
    fprintf(stderr, "FAIL: %s @ %s:%d\n", #condition, __FILE__, __LINE__); \
    return false; \
  }

#define ASSERT_EQ(e1, e2) ASSERT_TRUE((e1) == (e2))

namespace {

using google_breakpad::PathnameStripper;

static bool RunTests() {
  ASSERT_EQ(PathnameStripper::File("/dir/file"), "file");
  ASSERT_EQ(PathnameStripper::File("\\dir\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("/dir\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("\\dir/file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir/file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir/\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir\\/file"), "file");
  ASSERT_EQ(PathnameStripper::File("file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir/"), "");
  ASSERT_EQ(PathnameStripper::File("dir\\"), "");
  ASSERT_EQ(PathnameStripper::File("dir/dir/"), "");
  ASSERT_EQ(PathnameStripper::File("dir\\dir\\"), "");
  ASSERT_EQ(PathnameStripper::File("dir1/dir2/file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir1\\dir2\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir1/dir2\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir1\\dir2/file"), "file");
  ASSERT_EQ(PathnameStripper::File(""), "");
  ASSERT_EQ(PathnameStripper::File("1"), "1");
  ASSERT_EQ(PathnameStripper::File("1/2"), "2");
  ASSERT_EQ(PathnameStripper::File("1\\2"), "2");
  ASSERT_EQ(PathnameStripper::File("/1/2"), "2");
  ASSERT_EQ(PathnameStripper::File("\\1\\2"), "2");
  ASSERT_EQ(PathnameStripper::File("dir//file"), "file");
  ASSERT_EQ(PathnameStripper::File("dir\\\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("/dir//file"), "file");
  ASSERT_EQ(PathnameStripper::File("\\dir\\\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("c:\\dir\\file"), "file");
  ASSERT_EQ(PathnameStripper::File("c:\\dir\\file.ext"), "file.ext");

  return true;
}

}  // namespace

int main(int argc, char **argv) {
  BPLOG_INIT(&argc, &argv);

  return RunTests() ? 0 : 1;
}
