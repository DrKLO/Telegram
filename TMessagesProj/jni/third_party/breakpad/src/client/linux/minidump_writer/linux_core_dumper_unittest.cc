// Copyright (c) 2012, Google Inc.
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

// linux_core_dumper_unittest.cc:
// Unit tests for google_breakpad::LinuxCoreDumoer.

#include <string>

#include "breakpad_googletest_includes.h"
#include "client/linux/minidump_writer/linux_core_dumper.h"
#include "common/linux/tests/crash_generator.h"
#include "common/using_std_string.h"

using namespace google_breakpad;

TEST(LinuxCoreDumperTest, BuildProcPath) {
  const pid_t pid = getpid();
  const char procfs_path[] = "/procfs_copy";
  LinuxCoreDumper dumper(getpid(), "core_file", procfs_path);

  char maps_path[NAME_MAX] = "";
  char maps_path_expected[NAME_MAX];
  snprintf(maps_path_expected, sizeof(maps_path_expected),
           "%s/maps", procfs_path);
  EXPECT_TRUE(dumper.BuildProcPath(maps_path, pid, "maps"));
  EXPECT_STREQ(maps_path_expected, maps_path);

  EXPECT_FALSE(dumper.BuildProcPath(NULL, pid, "maps"));
  EXPECT_FALSE(dumper.BuildProcPath(maps_path, pid, ""));
  EXPECT_FALSE(dumper.BuildProcPath(maps_path, pid, NULL));

  char long_node[NAME_MAX];
  size_t long_node_len = NAME_MAX - strlen(procfs_path) - 1;
  memset(long_node, 'a', long_node_len);
  long_node[long_node_len] = '\0';
  EXPECT_FALSE(dumper.BuildProcPath(maps_path, pid, long_node));
}

TEST(LinuxCoreDumperTest, VerifyDumpWithMultipleThreads) {
  CrashGenerator crash_generator;
  if (!crash_generator.HasDefaultCorePattern()) {
    fprintf(stderr, "LinuxCoreDumperTest.VerifyDumpWithMultipleThreads test "
            "is skipped due to non-default core pattern\n");
    return;
  }

  const unsigned kNumOfThreads = 3;
  const unsigned kCrashThread = 1;
  const int kCrashSignal = SIGABRT;
  pid_t child_pid;
  ASSERT_TRUE(crash_generator.CreateChildCrash(kNumOfThreads, kCrashThread,
                                               kCrashSignal, &child_pid));

  const string core_file = crash_generator.GetCoreFilePath();
  const string procfs_path = crash_generator.GetDirectoryOfProcFilesCopy();

#if defined(__ANDROID__)
  struct stat st;
  if (stat(core_file.c_str(), &st) != 0) {
    fprintf(stderr, "LinuxCoreDumperTest.VerifyDumpWithMultipleThreads test is "
            "skipped due to no core file being generated");
    return;
  }
#endif

  LinuxCoreDumper dumper(child_pid, core_file.c_str(), procfs_path.c_str());

  EXPECT_TRUE(dumper.Init());

  EXPECT_TRUE(dumper.IsPostMortem());

  // These are no-ops and should always return true.
  EXPECT_TRUE(dumper.ThreadsSuspend());
  EXPECT_TRUE(dumper.ThreadsResume());

  // LinuxCoreDumper cannot determine the crash address and thus it always
  // sets the crash address to 0.
  EXPECT_EQ(0U, dumper.crash_address());
  EXPECT_EQ(kCrashSignal, dumper.crash_signal());
  EXPECT_EQ(crash_generator.GetThreadId(kCrashThread),
            dumper.crash_thread());

  EXPECT_EQ(kNumOfThreads, dumper.threads().size());
  for (unsigned i = 0; i < kNumOfThreads; ++i) {
    ThreadInfo info;
    EXPECT_TRUE(dumper.GetThreadInfoByIndex(i, &info));
    const void* stack;
    size_t stack_len;
    EXPECT_TRUE(dumper.GetStackInfo(&stack, &stack_len, info.stack_pointer));
    EXPECT_EQ(getpid(), info.ppid);
  }
}
