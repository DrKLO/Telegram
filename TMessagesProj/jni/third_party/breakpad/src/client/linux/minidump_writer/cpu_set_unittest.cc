// Copyright (c) 2013, Google Inc.
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

#include <stdlib.h>
#include <unistd.h>
#include <sys/types.h>
#include <stdio.h>
#include <errno.h>
#include <string.h>

#include "breakpad_googletest_includes.h"
#include "client/linux/minidump_writer/cpu_set.h"
#include "common/linux/tests/auto_testfile.h"

using namespace google_breakpad;

namespace {

typedef testing::Test CpuSetTest;

// Helper class to write test text file to a temporary file and return
// its file descriptor.
class ScopedTestFile : public AutoTestFile {
public:
  explicit ScopedTestFile(const char* text)
    : AutoTestFile("cpu_set", text) {
  }
};

}

TEST(CpuSetTest, EmptyCount) {
  CpuSet set;
  ASSERT_EQ(0, set.GetCount());
}

TEST(CpuSetTest, OneCpu) {
  ScopedTestFile file("10");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(1, set.GetCount());
}

TEST(CpuSetTest, OneCpuTerminated) {
  ScopedTestFile file("10\n");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(1, set.GetCount());
}

TEST(CpuSetTest, TwoCpusWithComma) {
  ScopedTestFile file("1,10");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(2, set.GetCount());
}

TEST(CpuSetTest, TwoCpusWithRange) {
  ScopedTestFile file("1-2");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(2, set.GetCount());
}

TEST(CpuSetTest, TenCpusWithRange) {
  ScopedTestFile file("9-18");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(10, set.GetCount());
}

TEST(CpuSetTest, MultiItems) {
  ScopedTestFile file("0, 2-4, 128");
  ASSERT_TRUE(file.IsOk());

  CpuSet set;
  ASSERT_TRUE(set.ParseSysFile(file.GetFd()));
  ASSERT_EQ(5, set.GetCount());
}

TEST(CpuSetTest, IntersectWith) {
  ScopedTestFile file1("9-19");
  ASSERT_TRUE(file1.IsOk());
  CpuSet set1;
  ASSERT_TRUE(set1.ParseSysFile(file1.GetFd()));
  ASSERT_EQ(11, set1.GetCount());

  ScopedTestFile file2("16-24");
  ASSERT_TRUE(file2.IsOk());
  CpuSet set2;
  ASSERT_TRUE(set2.ParseSysFile(file2.GetFd()));
  ASSERT_EQ(9, set2.GetCount());

  set1.IntersectWith(set2);
  ASSERT_EQ(4, set1.GetCount());
  ASSERT_EQ(9, set2.GetCount());
}

TEST(CpuSetTest, SelfIntersection) {
  ScopedTestFile file1("9-19");
  ASSERT_TRUE(file1.IsOk());
  CpuSet set1;
  ASSERT_TRUE(set1.ParseSysFile(file1.GetFd()));
  ASSERT_EQ(11, set1.GetCount());

  set1.IntersectWith(set1);
  ASSERT_EQ(11, set1.GetCount());
}

TEST(CpuSetTest, EmptyIntersection) {
  ScopedTestFile file1("0-19");
  ASSERT_TRUE(file1.IsOk());
  CpuSet set1;
  ASSERT_TRUE(set1.ParseSysFile(file1.GetFd()));
  ASSERT_EQ(20, set1.GetCount());

  ScopedTestFile file2("20-39");
  ASSERT_TRUE(file2.IsOk());
  CpuSet set2;
  ASSERT_TRUE(set2.ParseSysFile(file2.GetFd()));
  ASSERT_EQ(20, set2.GetCount());

  set1.IntersectWith(set2);
  ASSERT_EQ(0, set1.GetCount());

  ASSERT_EQ(20, set2.GetCount());
}

