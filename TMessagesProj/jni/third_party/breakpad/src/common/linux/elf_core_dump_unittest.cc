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

// elf_core_dump_unittest.cc: Unit tests for google_breakpad::ElfCoreDump.

#include <sys/procfs.h>

#include <set>
#include <string>

#include "breakpad_googletest_includes.h"
#include "common/linux/elf_core_dump.h"
#include "common/linux/memory_mapped_file.h"
#include "common/tests/file_utils.h"
#include "common/linux/tests/crash_generator.h"
#include "common/using_std_string.h"

using google_breakpad::AutoTempDir;
using google_breakpad::CrashGenerator;
using google_breakpad::ElfCoreDump;
using google_breakpad::MemoryMappedFile;
using google_breakpad::MemoryRange;
using google_breakpad::WriteFile;
using std::set;

TEST(ElfCoreDumpTest, DefaultConstructor) {
  ElfCoreDump core;
  EXPECT_FALSE(core.IsValid());
  EXPECT_EQ(NULL, core.GetHeader());
  EXPECT_EQ(0U, core.GetProgramHeaderCount());
  EXPECT_EQ(NULL, core.GetProgramHeader(0));
  EXPECT_EQ(NULL, core.GetFirstProgramHeaderOfType(PT_LOAD));
  EXPECT_FALSE(core.GetFirstNote().IsValid());
}

TEST(ElfCoreDumpTest, TestElfHeader) {
  ElfCoreDump::Ehdr header;
  memset(&header, 0, sizeof(header));

  AutoTempDir temp_dir;
  string core_path = temp_dir.path() + "/core";
  const char* core_file = core_path.c_str();
  MemoryMappedFile mapped_core_file;
  ElfCoreDump core;

  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header) - 1));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());
  EXPECT_EQ(NULL, core.GetHeader());
  EXPECT_EQ(0U, core.GetProgramHeaderCount());
  EXPECT_EQ(NULL, core.GetProgramHeader(0));
  EXPECT_EQ(NULL, core.GetFirstProgramHeaderOfType(PT_LOAD));
  EXPECT_FALSE(core.GetFirstNote().IsValid());

  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_ident[0] = ELFMAG0;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_ident[1] = ELFMAG1;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_ident[2] = ELFMAG2;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_ident[3] = ELFMAG3;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_ident[4] = ElfCoreDump::kClass;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_version = EV_CURRENT;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_FALSE(core.IsValid());

  header.e_type = ET_CORE;
  ASSERT_TRUE(WriteFile(core_file, &header, sizeof(header)));
  ASSERT_TRUE(mapped_core_file.Map(core_file, 0));
  core.SetContent(mapped_core_file.content());
  EXPECT_TRUE(core.IsValid());
}

TEST(ElfCoreDumpTest, ValidCoreFile) {
  CrashGenerator crash_generator;
  if (!crash_generator.HasDefaultCorePattern()) {
    fprintf(stderr, "ElfCoreDumpTest.ValidCoreFile test is skipped "
            "due to non-default core pattern");
    return;
  }

  const unsigned kNumOfThreads = 3;
  const unsigned kCrashThread = 1;
  const int kCrashSignal = SIGABRT;
  ASSERT_TRUE(crash_generator.CreateChildCrash(kNumOfThreads, kCrashThread,
                                               kCrashSignal, NULL));
  pid_t expected_crash_thread_id = crash_generator.GetThreadId(kCrashThread);
  set<pid_t> expected_thread_ids;
  for (unsigned i = 0; i < kNumOfThreads; ++i) {
    expected_thread_ids.insert(crash_generator.GetThreadId(i));
  }

#if defined(__ANDROID__)
  struct stat st;
  if (stat(crash_generator.GetCoreFilePath().c_str(), &st) != 0) {
    fprintf(stderr, "ElfCoreDumpTest.ValidCoreFile test is skipped "
            "due to no core file being generated");
    return;
  }
#endif

  MemoryMappedFile mapped_core_file;
  ASSERT_TRUE(
      mapped_core_file.Map(crash_generator.GetCoreFilePath().c_str(), 0));

  ElfCoreDump core;
  core.SetContent(mapped_core_file.content());
  EXPECT_TRUE(core.IsValid());

  // Based on write_note_info() in linux/kernel/fs/binfmt_elf.c, notes are
  // ordered as follows (NT_PRXFPREG and NT_386_TLS are i386 specific):
  //   Thread           Name          Type
  //   -------------------------------------------------------------------
  //   1st thread       CORE          NT_PRSTATUS
  //   process-wide     CORE          NT_PRPSINFO
  //   process-wide     CORE          NT_AUXV
  //   1st thread       CORE          NT_FPREGSET
  //   1st thread       LINUX         NT_PRXFPREG
  //   1st thread       LINUX         NT_386_TLS
  //
  //   2nd thread       CORE          NT_PRSTATUS
  //   2nd thread       CORE          NT_FPREGSET
  //   2nd thread       LINUX         NT_PRXFPREG
  //   2nd thread       LINUX         NT_386_TLS
  //
  //   3rd thread       CORE          NT_PRSTATUS
  //   3rd thread       CORE          NT_FPREGSET
  //   3rd thread       LINUX         NT_PRXFPREG
  //   3rd thread       LINUX         NT_386_TLS

  size_t num_nt_prpsinfo = 0;
  size_t num_nt_prstatus = 0;
  size_t num_pr_fpvalid = 0;
#if defined(__i386__) || defined(__x86_64__)
  size_t num_nt_fpregset = 0;
#endif
#if defined(__i386__)
  size_t num_nt_prxfpreg = 0;
#endif
  set<pid_t> actual_thread_ids;
  ElfCoreDump::Note note = core.GetFirstNote();
  while (note.IsValid()) {
    MemoryRange name = note.GetName();
    MemoryRange description = note.GetDescription();
    EXPECT_FALSE(name.IsEmpty());
    EXPECT_FALSE(description.IsEmpty());

    switch (note.GetType()) {
      case NT_PRPSINFO: {
        EXPECT_TRUE(description.data() != NULL);
        EXPECT_EQ(sizeof(elf_prpsinfo), description.length());
        ++num_nt_prpsinfo;
        break;
      }
      case NT_PRSTATUS: {
        EXPECT_TRUE(description.data() != NULL);
        EXPECT_EQ(sizeof(elf_prstatus), description.length());
        const elf_prstatus* status = description.GetData<elf_prstatus>(0);
        actual_thread_ids.insert(status->pr_pid);
        if (num_nt_prstatus == 0) {
          EXPECT_EQ(expected_crash_thread_id, status->pr_pid);
          EXPECT_EQ(kCrashSignal, status->pr_info.si_signo);
        }
        ++num_nt_prstatus;
        if (status->pr_fpvalid)
          ++num_pr_fpvalid;
        break;
      }
#if defined(__i386__) || defined(__x86_64__)
      case NT_FPREGSET: {
        EXPECT_TRUE(description.data() != NULL);
        EXPECT_EQ(sizeof(user_fpregs_struct), description.length());
        ++num_nt_fpregset;
        break;
      }
#endif
#if defined(__i386__)
      case NT_PRXFPREG: {
        EXPECT_TRUE(description.data() != NULL);
        EXPECT_EQ(sizeof(user_fpxregs_struct), description.length());
        ++num_nt_prxfpreg;
        break;
      }
#endif
      default:
        break;
    }
    note = note.GetNextNote();
  }

  EXPECT_TRUE(expected_thread_ids == actual_thread_ids);
  EXPECT_EQ(1U, num_nt_prpsinfo);
  EXPECT_EQ(kNumOfThreads, num_nt_prstatus);
#if defined(__i386__) || defined(__x86_64__)
  EXPECT_EQ(num_pr_fpvalid, num_nt_fpregset);
#endif
#if defined(__i386__)
  EXPECT_EQ(num_pr_fpvalid, num_nt_prxfpreg);
#endif
}
