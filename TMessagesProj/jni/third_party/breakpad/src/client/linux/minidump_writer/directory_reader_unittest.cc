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

#include <set>
#include <string>

#include <dirent.h>
#include <fcntl.h>
#include <sys/types.h>

#include "client/linux/minidump_writer/directory_reader.h"
#include "common/using_std_string.h"
#include "breakpad_googletest_includes.h"

using namespace google_breakpad;

namespace {
typedef testing::Test DirectoryReaderTest;
}

TEST(DirectoryReaderTest, CompareResults) {
  std::set<string> dent_set;

  DIR *const dir = opendir("/proc/self");
  ASSERT_TRUE(dir != NULL);

  struct dirent* dent;
  while ((dent = readdir(dir)))
    dent_set.insert(dent->d_name);

  closedir(dir);

  const int fd = open("/proc/self", O_DIRECTORY | O_RDONLY);
  ASSERT_GE(fd, 0);

  DirectoryReader dir_reader(fd);
  unsigned seen = 0;

  const char* name;
  while (dir_reader.GetNextEntry(&name)) {
    ASSERT_TRUE(dent_set.find(name) != dent_set.end());
    seen++;
    dir_reader.PopEntry();
  }

  ASSERT_TRUE(dent_set.find("status") != dent_set.end());
  ASSERT_TRUE(dent_set.find("stat") != dent_set.end());
  ASSERT_TRUE(dent_set.find("cmdline") != dent_set.end());

  ASSERT_EQ(dent_set.size(), seen);
  close(fd);
}
