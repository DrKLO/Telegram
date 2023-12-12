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

#include <fcntl.h>
#include <sys/poll.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <ucontext.h>
#include <unistd.h>

#include <string>

#include "breakpad_googletest_includes.h"
#include "client/linux/handler/exception_handler.h"
#include "client/linux/minidump_writer/linux_dumper.h"
#include "client/linux/minidump_writer/minidump_writer.h"
#include "client/linux/minidump_writer/minidump_writer_unittest_utils.h"
#include "common/linux/eintr_wrapper.h"
#include "common/linux/file_id.h"
#include "common/linux/ignore_ret.h"
#include "common/linux/safe_readlink.h"
#include "common/scoped_ptr.h"
#include "common/tests/auto_tempdir.h"
#include "common/tests/file_utils.h"
#include "common/using_std_string.h"
#include "google_breakpad/processor/minidump.h"

using namespace google_breakpad;

// Length of a formatted GUID string =
// sizeof(MDGUID) * 2 + 4 (for dashes) + 1 (null terminator)
const int kGUIDStringSize = 37;

namespace {

typedef testing::Test MinidumpWriterTest;

const char kMDWriterUnitTestFileName[] = "/minidump-writer-unittest";

TEST(MinidumpWriterTest, SetupWithPath) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    IGNORE_RET(HANDLE_EINTR(read(fds[0], &b, sizeof(b))));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;
  memset(&context, 0, sizeof(context));

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + kMDWriterUnitTestFileName;
  // Set a non-zero tid to avoid tripping asserts.
  context.tid = child;
  ASSERT_TRUE(WriteMinidump(templ.c_str(), child, &context, sizeof(context)));
  struct stat st;
  ASSERT_EQ(0, stat(templ.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  close(fds[1]);
}

TEST(MinidumpWriterTest, SetupWithFD) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    HANDLE_EINTR(read(fds[0], &b, sizeof(b)));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;
  memset(&context, 0, sizeof(context));

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + kMDWriterUnitTestFileName;
  int fd = open(templ.c_str(), O_CREAT | O_WRONLY, S_IRWXU);
  // Set a non-zero tid to avoid tripping asserts.
  context.tid = child;
  ASSERT_TRUE(WriteMinidump(fd, child, &context, sizeof(context)));
  struct stat st;
  ASSERT_EQ(0, stat(templ.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  close(fds[1]);
}

// Test that mapping info can be specified when writing a minidump,
// and that it ends up in the module list of the minidump.
TEST(MinidumpWriterTest, MappingInfo) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const uint32_t memory_size = sysconf(_SC_PAGESIZE);
  const char* kMemoryName = "a fake module";
  const uint8_t kModuleGUID[sizeof(MDGUID)] = {
    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
    0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF
  };
  char module_identifier_buffer[kGUIDStringSize];
  FileID::ConvertIdentifierToString(kModuleGUID,
                                    module_identifier_buffer,
                                    sizeof(module_identifier_buffer));
  string module_identifier(module_identifier_buffer);
  // Strip out dashes
  size_t pos;
  while ((pos = module_identifier.find('-')) != string::npos) {
    module_identifier.erase(pos, 1);
  }
  // And append a zero, because module IDs include an "age" field
  // which is always zero on Linux.
  module_identifier += "0";

  // Get some memory.
  char* memory =
    reinterpret_cast<char*>(mmap(NULL,
                                 memory_size,
                                 PROT_READ | PROT_WRITE,
                                 MAP_PRIVATE | MAP_ANON,
                                 -1,
                                 0));
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    IGNORE_RET(HANDLE_EINTR(read(fds[0], &b, sizeof(b))));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;
  memset(&context, 0, sizeof(context));
  ASSERT_EQ(0, getcontext(&context.context));
  context.tid = child;

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + kMDWriterUnitTestFileName;

  // Add information about the mapped memory.
  MappingInfo info;
  info.start_addr = kMemoryAddress;
  info.size = memory_size;
  info.offset = 0;
  strcpy(info.name, kMemoryName);

  MappingList mappings;
  AppMemoryList memory_list;
  MappingEntry mapping;
  mapping.first = info;
  memcpy(mapping.second, kModuleGUID, sizeof(MDGUID));
  mappings.push_back(mapping);
  ASSERT_TRUE(WriteMinidump(templ.c_str(), child, &context, sizeof(context),
                            mappings, memory_list));

  // Read the minidump. Load the module list, and ensure that
  // the mmap'ed |memory| is listed with the given module name
  // and debug ID.
  Minidump minidump(templ);
  ASSERT_TRUE(minidump.Read());

  MinidumpModuleList* module_list = minidump.GetModuleList();
  ASSERT_TRUE(module_list);
  const MinidumpModule* module =
    module_list->GetModuleForAddress(kMemoryAddress);
  ASSERT_TRUE(module);

  EXPECT_EQ(kMemoryAddress, module->base_address());
  EXPECT_EQ(memory_size, module->size());
  EXPECT_EQ(kMemoryName, module->code_file());
  EXPECT_EQ(module_identifier, module->debug_identifier());

  uint32_t len;
  // These streams are expected to be there
  EXPECT_TRUE(minidump.SeekToStreamType(MD_THREAD_LIST_STREAM, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_MEMORY_LIST_STREAM, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_EXCEPTION_STREAM, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_SYSTEM_INFO_STREAM, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_CPU_INFO, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_PROC_STATUS, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_CMD_LINE, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_ENVIRON, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_AUXV, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_MAPS, &len));
  EXPECT_TRUE(minidump.SeekToStreamType(MD_LINUX_DSO_DEBUG, &len));

  close(fds[1]);
}

// Test that mapping info can be specified, and that it overrides
// existing mappings that are wholly contained within the specified
// range.
TEST(MinidumpWriterTest, MappingInfoContained) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const int32_t memory_size = sysconf(_SC_PAGESIZE);
  const char* kMemoryName = "a fake module";
  const uint8_t kModuleGUID[sizeof(MDGUID)] = {
    0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77,
    0x88, 0x99, 0xAA, 0xBB, 0xCC, 0xDD, 0xEE, 0xFF
  };
  char module_identifier_buffer[kGUIDStringSize];
  FileID::ConvertIdentifierToString(kModuleGUID,
                                    module_identifier_buffer,
                                    sizeof(module_identifier_buffer));
  string module_identifier(module_identifier_buffer);
  // Strip out dashes
  size_t pos;
  while ((pos = module_identifier.find('-')) != string::npos) {
    module_identifier.erase(pos, 1);
  }
  // And append a zero, because module IDs include an "age" field
  // which is always zero on Linux.
  module_identifier += "0";

  // mmap a file
  AutoTempDir temp_dir;
  string tempfile = temp_dir.path() + "/minidump-writer-unittest-temp";
  int fd = open(tempfile.c_str(), O_RDWR | O_CREAT, 0);
  ASSERT_NE(-1, fd);
  unlink(tempfile.c_str());
  // fill with zeros
  google_breakpad::scoped_array<char> buffer(new char[memory_size]);
  memset(buffer.get(), 0, memory_size);
  ASSERT_EQ(memory_size, write(fd, buffer.get(), memory_size));
  lseek(fd, 0, SEEK_SET);

  char* memory =
    reinterpret_cast<char*>(mmap(NULL,
                                 memory_size,
                                 PROT_READ | PROT_WRITE,
                                 MAP_PRIVATE,
                                 fd,
                                 0));
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);
  close(fd);

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    IGNORE_RET(HANDLE_EINTR(read(fds[0], &b, sizeof(b))));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;
  memset(&context, 0, sizeof(context));
  context.tid = 1;

  string dumpfile = temp_dir.path() + kMDWriterUnitTestFileName;

  // Add information about the mapped memory. Report it as being larger than
  // it actually is.
  MappingInfo info;
  info.start_addr = kMemoryAddress - memory_size;
  info.size = memory_size * 3;
  info.offset = 0;
  strcpy(info.name, kMemoryName);

  MappingList mappings;
  AppMemoryList memory_list;
  MappingEntry mapping;
  mapping.first = info;
  memcpy(mapping.second, kModuleGUID, sizeof(MDGUID));
  mappings.push_back(mapping);
  ASSERT_TRUE(WriteMinidump(dumpfile.c_str(), child, &context, sizeof(context),
                            mappings, memory_list));

  // Read the minidump. Load the module list, and ensure that
  // the mmap'ed |memory| is listed with the given module name
  // and debug ID.
  Minidump minidump(dumpfile);
  ASSERT_TRUE(minidump.Read());

  MinidumpModuleList* module_list = minidump.GetModuleList();
  ASSERT_TRUE(module_list);
  const MinidumpModule* module =
    module_list->GetModuleForAddress(kMemoryAddress);
  ASSERT_TRUE(module);

  EXPECT_EQ(info.start_addr, module->base_address());
  EXPECT_EQ(info.size, module->size());
  EXPECT_EQ(kMemoryName, module->code_file());
  EXPECT_EQ(module_identifier, module->debug_identifier());

  close(fds[1]);
}

TEST(MinidumpWriterTest, DeletedBinary) {
  const string kNumberOfThreadsArgument = "1";
  const string helper_path(GetHelperBinary());
  if (helper_path.empty()) {
    FAIL() << "Couldn't find helper binary";
    exit(1);
  }

  // Copy binary to a temp file.
  AutoTempDir temp_dir;
  string binpath = temp_dir.path() + "/linux-dumper-unittest-helper";
  ASSERT_TRUE(CopyFile(helper_path.c_str(), binpath.c_str()))
      << "Failed to copy " << helper_path << " to " << binpath;
  ASSERT_EQ(0, chmod(binpath.c_str(), 0755));

  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  pid_t child_pid = fork();
  if (child_pid == 0) {
    // In child process.
    close(fds[0]);

    // Pass the pipe fd and the number of threads as arguments.
    char pipe_fd_string[8];
    sprintf(pipe_fd_string, "%d", fds[1]);
    execl(binpath.c_str(),
          binpath.c_str(),
          pipe_fd_string,
          kNumberOfThreadsArgument.c_str(),
          NULL);
  }
  close(fds[1]);
  // Wait for the child process to signal that it's ready.
  struct pollfd pfd;
  memset(&pfd, 0, sizeof(pfd));
  pfd.fd = fds[0];
  pfd.events = POLLIN | POLLERR;

  const int r = HANDLE_EINTR(poll(&pfd, 1, 1000));
  ASSERT_EQ(1, r);
  ASSERT_TRUE(pfd.revents & POLLIN);
  uint8_t junk;
  const int nr = HANDLE_EINTR(read(fds[0], &junk, sizeof(junk)));
  ASSERT_EQ(static_cast<ssize_t>(sizeof(junk)), nr);
  close(fds[0]);

  // Child is ready now.
  // Unlink the test binary.
  unlink(binpath.c_str());

  ExceptionHandler::CrashContext context;
  memset(&context, 0, sizeof(context));

  string templ = temp_dir.path() + kMDWriterUnitTestFileName;
  // Set a non-zero tid to avoid tripping asserts.
  context.tid = child_pid;
  ASSERT_TRUE(WriteMinidump(templ.c_str(), child_pid, &context,
                            sizeof(context)));
  kill(child_pid, SIGKILL);

  struct stat st;
  ASSERT_EQ(0, stat(templ.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  Minidump minidump(templ);
  ASSERT_TRUE(minidump.Read());

  // Check that the main module filename is correct.
  MinidumpModuleList* module_list = minidump.GetModuleList();
  ASSERT_TRUE(module_list);
  const MinidumpModule* module = module_list->GetMainModule();
  EXPECT_STREQ(binpath.c_str(), module->code_file().c_str());
  // Check that the file ID is correct.
  FileID fileid(helper_path.c_str());
  uint8_t identifier[sizeof(MDGUID)];
  EXPECT_TRUE(fileid.ElfFileIdentifier(identifier));
  char identifier_string[kGUIDStringSize];
  FileID::ConvertIdentifierToString(identifier,
                                    identifier_string,
                                    kGUIDStringSize);
  string module_identifier(identifier_string);
  // Strip out dashes
  size_t pos;
  while ((pos = module_identifier.find('-')) != string::npos) {
    module_identifier.erase(pos, 1);
  }
  // And append a zero, because module IDs include an "age" field
  // which is always zero on Linux.
  module_identifier += "0";
  EXPECT_EQ(module_identifier, module->debug_identifier());
}

// Test that an additional memory region can be added to the minidump.
TEST(MinidumpWriterTest, AdditionalMemory) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const uint32_t kMemorySize = sysconf(_SC_PAGESIZE);

  // Get some heap memory.
  uint8_t* memory = new uint8_t[kMemorySize];
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);

  // Stick some data into the memory so the contents can be verified.
  for (uint32_t i = 0; i < kMemorySize; ++i) {
    memory[i] = i % 255;
  }

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    HANDLE_EINTR(read(fds[0], &b, sizeof(b)));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;

  // This needs a valid context for minidump writing to work, but getting
  // a useful one from the child is too much work, so just use one from
  // the parent since the child is just a forked copy anyway.
  ASSERT_EQ(0, getcontext(&context.context));
  context.tid = child;

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + kMDWriterUnitTestFileName;
  unlink(templ.c_str());

  MappingList mappings;
  AppMemoryList memory_list;

  // Add the memory region to the list of memory to be included.
  AppMemory app_memory;
  app_memory.ptr = memory;
  app_memory.length = kMemorySize;
  memory_list.push_back(app_memory);
  ASSERT_TRUE(WriteMinidump(templ.c_str(), child, &context, sizeof(context),
                            mappings, memory_list));

  // Read the minidump. Ensure that the memory region is present
  Minidump minidump(templ);
  ASSERT_TRUE(minidump.Read());

  MinidumpMemoryList* dump_memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(dump_memory_list);
  const MinidumpMemoryRegion* region =
    dump_memory_list->GetMemoryRegionForAddress(kMemoryAddress);
  ASSERT_TRUE(region);

  EXPECT_EQ(kMemoryAddress, region->GetBase());
  EXPECT_EQ(kMemorySize, region->GetSize());

  // Verify memory contents.
  EXPECT_EQ(0, memcmp(region->GetMemory(), memory, kMemorySize));

  delete[] memory;
  close(fds[1]);
}

// Test that an invalid thread stack pointer still results in a minidump.
TEST(MinidumpWriterTest, InvalidStackPointer) {
  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  const pid_t child = fork();
  if (child == 0) {
    close(fds[1]);
    char b;
    HANDLE_EINTR(read(fds[0], &b, sizeof(b)));
    close(fds[0]);
    syscall(__NR_exit);
  }
  close(fds[0]);

  ExceptionHandler::CrashContext context;

  // This needs a valid context for minidump writing to work, but getting
  // a useful one from the child is too much work, so just use one from
  // the parent since the child is just a forked copy anyway.
  ASSERT_EQ(0, getcontext(&context.context));
  context.tid = child;

  // Fake the child's stack pointer for its crashing thread.  NOTE: This must
  // be an invalid memory address for the child process (stack or otherwise).
  // Try 1MB below the current stack.
  uintptr_t invalid_stack_pointer =
      reinterpret_cast<uintptr_t>(&context) - 1024*1024;
#if defined(__i386)
  context.context.uc_mcontext.gregs[REG_ESP] = invalid_stack_pointer;
#elif defined(__x86_64)
  context.context.uc_mcontext.gregs[REG_RSP] = invalid_stack_pointer;
#elif defined(__ARM_EABI__)
  context.context.uc_mcontext.arm_sp = invalid_stack_pointer;
#elif defined(__aarch64__)
  context.context.uc_mcontext.sp = invalid_stack_pointer;
#elif defined(__mips__)
  context.context.uc_mcontext.gregs[MD_CONTEXT_MIPS_REG_SP] =
      invalid_stack_pointer;
#else
# error "This code has not been ported to your platform yet."
#endif

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + kMDWriterUnitTestFileName;
  // NOTE: In previous versions of Breakpad, WriteMinidump() would fail if
  // presented with an invalid stack pointer.
  ASSERT_TRUE(WriteMinidump(templ.c_str(), child, &context, sizeof(context)));

  // Read the minidump. Ensure that the memory region is present
  Minidump minidump(templ);
  ASSERT_TRUE(minidump.Read());

  // TODO(ted.mielczarek,mkrebs): Enable this part of the test once
  // https://breakpad.appspot.com/413002/ is committed.
#if 0
  // Make sure there's a thread without a stack.  NOTE: It's okay if
  // GetThreadList() shows the error: "ERROR: MinidumpThread has a memory
  // region problem".
  MinidumpThreadList* dump_thread_list = minidump.GetThreadList();
  ASSERT_TRUE(dump_thread_list);
  bool found_empty_stack = false;
  for (int i = 0; i < dump_thread_list->thread_count(); i++) {
    MinidumpThread* thread = dump_thread_list->GetThreadAtIndex(i);
    ASSERT_TRUE(thread->thread() != NULL);
    // When the stack size is zero bytes, GetMemory() returns NULL.
    if (thread->GetMemory() == NULL) {
      found_empty_stack = true;
      break;
    }
  }
  // NOTE: If you fail this, first make sure that "invalid_stack_pointer"
  // above is indeed set to an invalid address.
  ASSERT_TRUE(found_empty_stack);
#endif

  close(fds[1]);
}

// Test that limiting the size of the minidump works.
TEST(MinidumpWriterTest, MinidumpSizeLimit) {
  static const int kNumberOfThreadsInHelperProgram = 40;

  char number_of_threads_arg[3];
  sprintf(number_of_threads_arg, "%d", kNumberOfThreadsInHelperProgram);

  string helper_path(GetHelperBinary());
  if (helper_path.empty()) {
    FAIL() << "Couldn't find helper binary";
    exit(1);
  }

  int fds[2];
  ASSERT_NE(-1, pipe(fds));

  pid_t child_pid = fork();
  if (child_pid == 0) {
    // In child process.
    close(fds[0]);

    // Pass the pipe fd and the number of threads as arguments.
    char pipe_fd_string[8];
    sprintf(pipe_fd_string, "%d", fds[1]);
    execl(helper_path.c_str(),
          helper_path.c_str(),
          pipe_fd_string,
          number_of_threads_arg,
          NULL);
  }
  close(fds[1]);

  // Wait for all child threads to indicate that they have started
  for (int threads = 0; threads < kNumberOfThreadsInHelperProgram; threads++) {
    struct pollfd pfd;
    memset(&pfd, 0, sizeof(pfd));
    pfd.fd = fds[0];
    pfd.events = POLLIN | POLLERR;

    const int r = HANDLE_EINTR(poll(&pfd, 1, 1000));
    ASSERT_EQ(1, r);
    ASSERT_TRUE(pfd.revents & POLLIN);
    uint8_t junk;
    ASSERT_EQ(read(fds[0], &junk, sizeof(junk)),
              static_cast<ssize_t>(sizeof(junk)));
  }
  close(fds[0]);

  // There is a race here because we may stop a child thread before
  // it is actually running the busy loop. Empirically this sleep
  // is sufficient to avoid the race.
  usleep(100000);

  // Child and its threads are ready now.


  off_t normal_file_size;
  int total_normal_stack_size = 0;
  AutoTempDir temp_dir;

  // First, write a minidump with no size limit.
  {
    string normal_dump = temp_dir.path() +
        "/minidump-writer-unittest.dmp";
    ASSERT_TRUE(WriteMinidump(normal_dump.c_str(), -1,
                              child_pid, NULL, 0,
                              MappingList(), AppMemoryList()));
    struct stat st;
    ASSERT_EQ(0, stat(normal_dump.c_str(), &st));
    ASSERT_GT(st.st_size, 0);
    normal_file_size = st.st_size;

    Minidump minidump(normal_dump);
    ASSERT_TRUE(minidump.Read());
    MinidumpThreadList* dump_thread_list = minidump.GetThreadList();
    ASSERT_TRUE(dump_thread_list);
    for (unsigned int i = 0; i < dump_thread_list->thread_count(); i++) {
      MinidumpThread* thread = dump_thread_list->GetThreadAtIndex(i);
      ASSERT_TRUE(thread->thread() != NULL);
      // When the stack size is zero bytes, GetMemory() returns NULL.
      MinidumpMemoryRegion* memory = thread->GetMemory();
      ASSERT_TRUE(memory != NULL);
      total_normal_stack_size += memory->GetSize();
    }
  }

  // Second, write a minidump with a size limit big enough to not trigger
  // anything.
  {
    // Set size limit arbitrarily 1MB larger than the normal file size -- such
    // that the limiting code will not kick in.
    const off_t minidump_size_limit = normal_file_size + 1024*1024;

    string same_dump = temp_dir.path() +
        "/minidump-writer-unittest-same.dmp";
    ASSERT_TRUE(WriteMinidump(same_dump.c_str(), minidump_size_limit,
                              child_pid, NULL, 0,
                              MappingList(), AppMemoryList()));
    struct stat st;
    ASSERT_EQ(0, stat(same_dump.c_str(), &st));
    // Make sure limiting wasn't actually triggered.  NOTE: If you fail this,
    // first make sure that "minidump_size_limit" above is indeed set to a
    // large enough value -- the limit-checking code in minidump_writer.cc
    // does just a rough estimate.
    ASSERT_EQ(normal_file_size, st.st_size);
  }

  // Third, write a minidump with a size limit small enough to be triggered.
  {
    // Set size limit to some arbitrary amount, such that the limiting code
    // will kick in.  The equation used to set this value was determined by
    // simply reversing the size-limit logic a little bit in order to pick a
    // size we know will trigger it.  The definition of
    // kLimitAverageThreadStackLength here was copied from class
    // MinidumpWriter in minidump_writer.cc.
    static const unsigned kLimitAverageThreadStackLength = 8 * 1024;
    off_t minidump_size_limit = kNumberOfThreadsInHelperProgram *
        kLimitAverageThreadStackLength;
    // If, in reality, each of the threads' stack is *smaller* than
    // kLimitAverageThreadStackLength, the normal file size could very well be
    // smaller than the arbitrary limit that was just set.  In that case,
    // either of these numbers should trigger the size-limiting code, but we
    // might as well pick the smallest.
    if (normal_file_size < minidump_size_limit)
      minidump_size_limit = normal_file_size;

    string limit_dump = temp_dir.path() +
        "/minidump-writer-unittest-limit.dmp";
    ASSERT_TRUE(WriteMinidump(limit_dump.c_str(), minidump_size_limit,
                              child_pid, NULL, 0,
                              MappingList(), AppMemoryList()));
    struct stat st;
    ASSERT_EQ(0, stat(limit_dump.c_str(), &st));
    ASSERT_GT(st.st_size, 0);
    // Make sure the file size is at least smaller than the original.  If this
    // fails because it's the same size, then the size-limit logic didn't kick
    // in like it was supposed to.
    EXPECT_LT(st.st_size, normal_file_size);

    Minidump minidump(limit_dump);
    ASSERT_TRUE(minidump.Read());
    MinidumpThreadList* dump_thread_list = minidump.GetThreadList();
    ASSERT_TRUE(dump_thread_list);
    int total_limit_stack_size = 0;
    for (unsigned int i = 0; i < dump_thread_list->thread_count(); i++) {
      MinidumpThread* thread = dump_thread_list->GetThreadAtIndex(i);
      ASSERT_TRUE(thread->thread() != NULL);
      // When the stack size is zero bytes, GetMemory() returns NULL.
      MinidumpMemoryRegion* memory = thread->GetMemory();
      ASSERT_TRUE(memory != NULL);
      total_limit_stack_size += memory->GetSize();
    }

    // Make sure stack size shrunk by at least 1KB per extra thread.  The
    // definition of kLimitBaseThreadCount here was copied from class
    // MinidumpWriter in minidump_writer.cc.
    // Note: The 1KB is arbitrary, and assumes that the thread stacks are big
    // enough to shrink by that much.  For example, if each thread stack was
    // originally only 2KB, the current size-limit logic wouldn't actually
    // shrink them because that's the size to which it tries to shrink.  If
    // you fail this part of the test due to something like that, the test
    // logic should probably be improved to account for your situation.
    const unsigned kLimitBaseThreadCount = 20;
    const unsigned kMinPerExtraThreadStackReduction = 1024;
    const int min_expected_reduction = (kNumberOfThreadsInHelperProgram -
        kLimitBaseThreadCount) * kMinPerExtraThreadStackReduction;
    EXPECT_LT(total_limit_stack_size,
              total_normal_stack_size - min_expected_reduction);
  }

  // Kill the helper program.
  kill(child_pid, SIGKILL);
}

}  // namespace
