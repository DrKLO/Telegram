// Copyright (c) 2011, Google Inc.
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

// memory_mapped_file_unittest.cc:
// Unit tests for google_breakpad::MemoryMappedFile.

#include <fcntl.h>
#include <string.h>
#include <unistd.h>

#include <string>

#include "breakpad_googletest_includes.h"
#include "common/linux/memory_mapped_file.h"
#include "common/tests/auto_tempdir.h"
#include "common/tests/file_utils.h"
#include "common/using_std_string.h"

using google_breakpad::AutoTempDir;
using google_breakpad::MemoryMappedFile;
using google_breakpad::WriteFile;

namespace {

class MemoryMappedFileTest : public testing::Test {
 protected:
  void ExpectNoMappedData(const MemoryMappedFile& mapped_file) {
    EXPECT_TRUE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() == NULL);
    EXPECT_EQ(0U, mapped_file.size());
  }
};

}  // namespace

TEST_F(MemoryMappedFileTest, DefaultConstructor) {
  MemoryMappedFile mapped_file;
  ExpectNoMappedData(mapped_file);
}

TEST_F(MemoryMappedFileTest, UnmapWithoutMap) {
  MemoryMappedFile mapped_file;
  mapped_file.Unmap();
}

TEST_F(MemoryMappedFileTest, MapNonexistentFile) {
  {
    MemoryMappedFile mapped_file("nonexistent-file", 0);
    ExpectNoMappedData(mapped_file);
  }
  {
    MemoryMappedFile mapped_file;
    EXPECT_FALSE(mapped_file.Map("nonexistent-file", 0));
    ExpectNoMappedData(mapped_file);
  }
}

TEST_F(MemoryMappedFileTest, MapEmptyFile) {
  AutoTempDir temp_dir;
  string test_file = temp_dir.path() + "/empty_file";
  ASSERT_TRUE(WriteFile(test_file.c_str(), NULL, 0));

  {
    MemoryMappedFile mapped_file(test_file.c_str(), 0);
    ExpectNoMappedData(mapped_file);
  }
  {
    MemoryMappedFile mapped_file;
    EXPECT_TRUE(mapped_file.Map(test_file.c_str(), 0));
    ExpectNoMappedData(mapped_file);
  }
}

TEST_F(MemoryMappedFileTest, MapNonEmptyFile) {
  char data[256];
  size_t data_size = sizeof(data);
  for (size_t i = 0; i < data_size; ++i) {
    data[i] = i;
  }

  AutoTempDir temp_dir;
  string test_file = temp_dir.path() + "/test_file";
  ASSERT_TRUE(WriteFile(test_file.c_str(), data, data_size));

  {
    MemoryMappedFile mapped_file(test_file.c_str(), 0);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data, mapped_file.data(), data_size));
  }
  {
    MemoryMappedFile mapped_file;
    EXPECT_TRUE(mapped_file.Map(test_file.c_str(), 0));
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data, mapped_file.data(), data_size));
  }
}

TEST_F(MemoryMappedFileTest, RemapAfterMap) {
  char data1[256];
  size_t data1_size = sizeof(data1);
  for (size_t i = 0; i < data1_size; ++i) {
    data1[i] = i;
  }

  char data2[50];
  size_t data2_size = sizeof(data2);
  for (size_t i = 0; i < data2_size; ++i) {
    data2[i] = 255 - i;
  }

  AutoTempDir temp_dir;
  string test_file1 = temp_dir.path() + "/test_file1";
  string test_file2 = temp_dir.path() + "/test_file2";
  ASSERT_TRUE(WriteFile(test_file1.c_str(), data1, data1_size));
  ASSERT_TRUE(WriteFile(test_file2.c_str(), data2, data2_size));

  {
    MemoryMappedFile mapped_file(test_file1.c_str(), 0);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data1_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data1, mapped_file.data(), data1_size));

    mapped_file.Map(test_file2.c_str(), 0);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data2_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data2, mapped_file.data(), data2_size));
  }
  {
    MemoryMappedFile mapped_file;
    EXPECT_TRUE(mapped_file.Map(test_file1.c_str(), 0));
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data1_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data1, mapped_file.data(), data1_size));

    mapped_file.Map(test_file2.c_str(), 0);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data2_size, mapped_file.size());
    EXPECT_EQ(0, memcmp(data2, mapped_file.data(), data2_size));
  }
}

TEST_F(MemoryMappedFileTest, MapWithOffset) {
  // Put more data in the test file this time. Offsets can only be
  // done on page boundaries, so we need a two page file to test this.
  const int page_size = 4096;
  char data1[2 * page_size];
  size_t data1_size = sizeof(data1);
  for (size_t i = 0; i < data1_size; ++i) {
    data1[i] = i & 0x7f;
  }

  AutoTempDir temp_dir;
  string test_file1 = temp_dir.path() + "/test_file1";
  ASSERT_TRUE(WriteFile(test_file1.c_str(), data1, data1_size));
  {
    MemoryMappedFile mapped_file(test_file1.c_str(), page_size);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data1_size - page_size, mapped_file.size());
    EXPECT_EQ(
        0,
        memcmp(data1 + page_size, mapped_file.data(), data1_size - page_size));
  }
  {
    MemoryMappedFile mapped_file;
    mapped_file.Map(test_file1.c_str(), page_size);
    EXPECT_FALSE(mapped_file.content().IsEmpty());
    EXPECT_TRUE(mapped_file.data() != NULL);
    EXPECT_EQ(data1_size - page_size, mapped_file.size());
    EXPECT_EQ(
        0,
        memcmp(data1 + page_size, mapped_file.data(), data1_size - page_size));
  }
}
