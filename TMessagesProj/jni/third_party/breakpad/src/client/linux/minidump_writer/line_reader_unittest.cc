// Copyright (c) 2009, Google Inc.
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

#include "client/linux/minidump_writer/line_reader.h"
#include "breakpad_googletest_includes.h"
#include "common/linux/tests/auto_testfile.h"

using namespace google_breakpad;

namespace {

typedef testing::Test LineReaderTest;

class ScopedTestFile : public AutoTestFile {
public:
  explicit ScopedTestFile(const char* text)
    : AutoTestFile("line_reader", text) {
  }

  ScopedTestFile(const char* text, size_t text_len)
    : AutoTestFile("line_reader", text, text_len) {
  }
};

}

TEST(LineReaderTest, EmptyFile) {
  ScopedTestFile file("");
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}

TEST(LineReaderTest, OneLineTerminated) {
  ScopedTestFile file("a\n");
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned int len;
  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned int)1, len);
  ASSERT_EQ('a', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}

TEST(LineReaderTest, OneLine) {
  ScopedTestFile file("a");
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned)1, len);
  ASSERT_EQ('a', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}

TEST(LineReaderTest, TwoLinesTerminated) {
  ScopedTestFile file("a\nb\n");
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned)1, len);
  ASSERT_EQ('a', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned)1, len);
  ASSERT_EQ('b', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}

TEST(LineReaderTest, TwoLines) {
  ScopedTestFile file("a\nb");
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned)1, len);
  ASSERT_EQ('a', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ((unsigned)1, len);
  ASSERT_EQ('b', line[0]);
  ASSERT_EQ('\0', line[1]);
  reader.PopLine(len);

  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}

TEST(LineReaderTest, MaxLength) {
  char l[LineReader::kMaxLineLen-1];
  memset(l, 'a', sizeof(l));
  ScopedTestFile file(l, sizeof(l));
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_TRUE(reader.GetNextLine(&line, &len));
  ASSERT_EQ(sizeof(l), len);
  ASSERT_TRUE(memcmp(l, line, sizeof(l)) == 0);
  ASSERT_EQ('\0', line[len]);
}

TEST(LineReaderTest, TooLong) {
  // Note: this writes kMaxLineLen 'a' chars in the test file.
  char l[LineReader::kMaxLineLen];
  memset(l, 'a', sizeof(l));
  ScopedTestFile file(l, sizeof(l));
  ASSERT_TRUE(file.IsOk());
  LineReader reader(file.GetFd());

  const char *line;
  unsigned len;
  ASSERT_FALSE(reader.GetNextLine(&line, &len));
}
