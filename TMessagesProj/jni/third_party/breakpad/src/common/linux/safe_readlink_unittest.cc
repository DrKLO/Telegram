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

// safe_readlink_unittest.cc: Unit tests for google_breakpad::SafeReadLink.

#include "breakpad_googletest_includes.h"
#include "common/linux/safe_readlink.h"

using google_breakpad::SafeReadLink;

TEST(SafeReadLinkTest, ZeroBufferSize) {
  char buffer[1];
  EXPECT_FALSE(SafeReadLink("/proc/self/exe", buffer, 0));
}

TEST(SafeReadLinkTest, BufferSizeTooSmall) {
  char buffer[1];
  EXPECT_FALSE(SafeReadLink("/proc/self/exe", buffer, 1));
}

TEST(SafeReadLinkTest, BoundaryBufferSize) {
  char buffer[PATH_MAX];
  EXPECT_TRUE(SafeReadLink("/proc/self/exe", buffer, sizeof(buffer)));
  size_t path_length = strlen(buffer);
  EXPECT_LT(0U, path_length);
  EXPECT_GT(sizeof(buffer), path_length);

  // Buffer size equals to the expected path length plus 1 for the NULL byte.
  char buffer2[PATH_MAX];
  EXPECT_TRUE(SafeReadLink("/proc/self/exe", buffer2, path_length + 1));
  EXPECT_EQ(path_length, strlen(buffer2));
  EXPECT_EQ(0, strncmp(buffer, buffer2, PATH_MAX));

  // Buffer size equals to the expected path length.
  EXPECT_FALSE(SafeReadLink("/proc/self/exe", buffer, path_length));
}

TEST(SafeReadLinkTest, NonexistentPath) {
  char buffer[PATH_MAX];
  EXPECT_FALSE(SafeReadLink("nonexistent_path", buffer, sizeof(buffer)));
}

TEST(SafeReadLinkTest, NonSymbolicLinkPath) {
  char actual_path[PATH_MAX];
  EXPECT_TRUE(SafeReadLink("/proc/self/exe", actual_path, sizeof(actual_path)));

  char buffer[PATH_MAX];
  EXPECT_FALSE(SafeReadLink(actual_path, buffer, sizeof(buffer)));
}

TEST(SafeReadLinkTest, DeduceBufferSizeFromCharArray) {
  char buffer[PATH_MAX];
  char* buffer_pointer = buffer;
  EXPECT_TRUE(SafeReadLink("/proc/self/exe", buffer_pointer, sizeof(buffer)));
  size_t path_length = strlen(buffer);

  // Use the template version of SafeReadLink to deduce the buffer size
  // from the char array.
  char buffer2[PATH_MAX];
  EXPECT_TRUE(SafeReadLink("/proc/self/exe", buffer2));
  EXPECT_EQ(path_length, strlen(buffer2));
  EXPECT_EQ(0, strncmp(buffer, buffer2, PATH_MAX));
}
