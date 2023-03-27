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

#include "client/linux/minidump_writer/proc_cpuinfo_reader.h"
#include "breakpad_googletest_includes.h"
#include "common/linux/tests/auto_testfile.h"

using namespace google_breakpad;

#if !defined(__ANDROID__)
#define TEMPDIR "/tmp"
#else
#define TEMPDIR "/data/local/tmp"
#endif


namespace {

typedef testing::Test ProcCpuInfoReaderTest;

class ScopedTestFile : public AutoTestFile {
public:
  explicit ScopedTestFile(const char* text)
    : AutoTestFile("proc_cpuinfo_reader", text) {
  }
};

}

TEST(ProcCpuInfoReaderTest, EmptyFile) {
  ScopedTestFile file("");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char *field;
  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, OneLineTerminated) {
  ScopedTestFile file("foo : bar\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char *field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, OneLine) {
  ScopedTestFile file("foo : bar");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char *field;
  size_t value_len;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValueAndLen(&value_len));
  ASSERT_EQ(3U, value_len);

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, TwoLinesTerminated) {
  ScopedTestFile file("foo : bar\nzoo : tut\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("zoo", field);
  ASSERT_STREQ("tut", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, SkipMalformedLine) {
  ScopedTestFile file("this line should have a column\nfoo : bar\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, SkipOneEmptyLine) {
  ScopedTestFile file("\n\nfoo : bar\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, SkipEmptyField) {
  ScopedTestFile file(" : bar\nzoo : tut\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("zoo", field);
  ASSERT_STREQ("tut", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, SkipTwoEmptyLines) {
  ScopedTestFile file("foo : bar\n\n\nfoo : bar\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  ASSERT_STREQ("bar", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, FieldWithSpaces) {
  ScopedTestFile file("foo bar    : zoo\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo bar", field);
  ASSERT_STREQ("zoo", reader.GetValue());

  ASSERT_FALSE(reader.GetNextField(&field));
}

TEST(ProcCpuInfoReaderTest, EmptyValue) {
  ScopedTestFile file("foo :\n");
  ASSERT_TRUE(file.IsOk());
  ProcCpuInfoReader reader(file.GetFd());

  const char* field;
  ASSERT_TRUE(reader.GetNextField(&field));
  ASSERT_STREQ("foo", field);
  size_t value_len;
  ASSERT_STREQ("", reader.GetValueAndLen(&value_len));
  ASSERT_EQ(0U, value_len);

  ASSERT_FALSE(reader.GetNextField(&field));
}
