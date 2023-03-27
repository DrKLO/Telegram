// Copyright (c) 2010 Google Inc.
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

#include <stdint.h>
#include <unistd.h>
#include <signal.h>
#include <sys/mman.h>
#include <sys/poll.h>
#include <sys/socket.h>
#include <sys/uio.h>
#include <sys/wait.h>
#if defined(__mips__)
#include <sys/cachectl.h>
#endif

#include <string>

#include "breakpad_googletest_includes.h"
#include "client/linux/handler/exception_handler.h"
#include "client/linux/minidump_writer/minidump_writer.h"
#include "common/linux/eintr_wrapper.h"
#include "common/linux/file_id.h"
#include "common/linux/ignore_ret.h"
#include "common/linux/linux_libc_support.h"
#include "common/tests/auto_tempdir.h"
#include "common/using_std_string.h"
#include "third_party/lss/linux_syscall_support.h"
#include "google_breakpad/processor/minidump.h"

using namespace google_breakpad;

namespace {

// Flush the instruction cache for a given memory range.
// Only required on ARM and mips.
void FlushInstructionCache(const char* memory, uint32_t memory_size) {
#if defined(__arm__)
  long begin = reinterpret_cast<long>(memory);
  long end = begin + static_cast<long>(memory_size);
# if defined(__ANDROID__)
  // Provided by Android's <unistd.h>
  cacheflush(begin, end, 0);
# elif defined(__linux__)
  // GLibc/ARM doesn't provide a wrapper for it, do a direct syscall.
#  ifndef __ARM_NR_cacheflush
#  define __ARM_NR_cacheflush 0xf0002
#  endif
  syscall(__ARM_NR_cacheflush, begin, end, 0);
# else
#   error "Your operating system is not supported yet"
# endif
#elif defined(__mips__)
# if defined(__ANDROID__)
  // Provided by Android's <unistd.h>
  long begin = reinterpret_cast<long>(memory);
  long end = begin + static_cast<long>(memory_size);
#if _MIPS_SIM == _ABIO32
  cacheflush(begin, end, 0);
#else
  syscall(__NR_cacheflush, begin, end, ICACHE);
#endif
# elif defined(__linux__)
  // See http://www.linux-mips.org/wiki/Cacheflush_Syscall.
  cacheflush(const_cast<char*>(memory), memory_size, ICACHE);
# else
#   error "Your operating system is not supported yet"
# endif
#endif
}

// Length of a formatted GUID string =
// sizeof(MDGUID) * 2 + 4 (for dashes) + 1 (null terminator)
const int kGUIDStringSize = 37;

void sigchld_handler(int signo) { }

int CreateTMPFile(const string& dir, string* path) {
  string file = dir + "/exception-handler-unittest.XXXXXX";
  const char* c_file = file.c_str();
  // Copy that string, mkstemp needs a C string it can modify.
  char* c_path = strdup(c_file);
  const int fd = mkstemp(c_path);
  if (fd >= 0)
    *path = c_path;
  free(c_path);
  return fd;
}

class ExceptionHandlerTest : public ::testing::Test {
 protected:
  void SetUp() {
    // We need to be able to wait for children, so SIGCHLD cannot be SIG_IGN.
    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sa.sa_handler = sigchld_handler;
    ASSERT_NE(sigaction(SIGCHLD, &sa, &old_action), -1);
  }

  void TearDown() {
    sigaction(SIGCHLD, &old_action, NULL);
  }

  struct sigaction old_action;
};


void WaitForProcessToTerminate(pid_t process_id, int expected_status) {
  int status;
  ASSERT_NE(HANDLE_EINTR(waitpid(process_id, &status, 0)), -1);
  ASSERT_TRUE(WIFSIGNALED(status));
  ASSERT_EQ(expected_status, WTERMSIG(status));
}

// Reads the minidump path sent over the pipe |fd| and sets it in |path|.
void ReadMinidumpPathFromPipe(int fd, string* path) {
  struct pollfd pfd;
  memset(&pfd, 0, sizeof(pfd));
  pfd.fd = fd;
  pfd.events = POLLIN | POLLERR;

  const int r = HANDLE_EINTR(poll(&pfd, 1, 0));
  ASSERT_EQ(1, r);
  ASSERT_TRUE(pfd.revents & POLLIN);

  int32_t len;
  ASSERT_EQ(static_cast<ssize_t>(sizeof(len)), read(fd, &len, sizeof(len)));
  ASSERT_LT(len, 2048);
  char* filename = static_cast<char*>(malloc(len + 1));
  ASSERT_EQ(len, read(fd, filename, len));
  filename[len] = 0;
  close(fd);
  *path = filename;
  free(filename);
}

}  // namespace

TEST(ExceptionHandlerTest, SimpleWithPath) {
  AutoTempDir temp_dir;
  ExceptionHandler handler(
      MinidumpDescriptor(temp_dir.path()), NULL, NULL, NULL, true, -1);
  EXPECT_EQ(temp_dir.path(), handler.minidump_descriptor().directory());
  string temp_subdir = temp_dir.path() + "/subdir";
  handler.set_minidump_descriptor(MinidumpDescriptor(temp_subdir));
  EXPECT_EQ(temp_subdir, handler.minidump_descriptor().directory());
}

TEST(ExceptionHandlerTest, SimpleWithFD) {
  AutoTempDir temp_dir;
  string path;
  const int fd = CreateTMPFile(temp_dir.path(), &path);
  ExceptionHandler handler(MinidumpDescriptor(fd), NULL, NULL, NULL, true, -1);
  close(fd);
}

static bool DoneCallback(const MinidumpDescriptor& descriptor,
                         void* context,
                         bool succeeded) {
  if (!succeeded)
    return false;

  if (!descriptor.IsFD()) {
    int fd = reinterpret_cast<intptr_t>(context);
    uint32_t len = 0;
    len = my_strlen(descriptor.path());
    IGNORE_RET(HANDLE_EINTR(sys_write(fd, &len, sizeof(len))));
    IGNORE_RET(HANDLE_EINTR(sys_write(fd, descriptor.path(), len)));
  }
  return true;
}

#ifndef ADDRESS_SANITIZER

// This is a replacement for "*reinterpret_cast<volatile int*>(NULL) = 0;"
// It is needed because GCC is allowed to assume that the program will
// not execute any undefined behavior (UB) operation. Further, when GCC
// observes that UB statement is reached, it can assume that all statements
// leading to the UB one are never executed either, and can completely
// optimize them out. In the case of ExceptionHandlerTest::ExternalDumper,
// GCC-4.9 optimized out the entire set up of ExceptionHandler, causing
// test failure.
volatile int *p_null;  // external linkage, so GCC can't tell that it
                       // remains NULL. Volatile just for a good measure.
static void DoNullPointerDereference() {
  *p_null = 1;
}

void ChildCrash(bool use_fd) {
  AutoTempDir temp_dir;
  int fds[2] = {0};
  int minidump_fd = -1;
  string minidump_path;
  if (use_fd) {
    minidump_fd = CreateTMPFile(temp_dir.path(), &minidump_path);
  } else {
    ASSERT_NE(pipe(fds), -1);
  }

  const pid_t child = fork();
  if (child == 0) {
    {
      google_breakpad::scoped_ptr<ExceptionHandler> handler;
      if (use_fd) {
        handler.reset(new ExceptionHandler(MinidumpDescriptor(minidump_fd),
                                           NULL, NULL, NULL, true, -1));
      } else {
        close(fds[0]);  // Close the reading end.
        void* fd_param = reinterpret_cast<void*>(fds[1]);
        handler.reset(new ExceptionHandler(MinidumpDescriptor(temp_dir.path()),
                                           NULL, DoneCallback, fd_param,
                                           true, -1));
      }
      // Crash with the exception handler in scope.
      DoNullPointerDereference();
    }
  }
  if (!use_fd)
    close(fds[1]);  // Close the writting end.

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));

  if (!use_fd)
    ASSERT_NO_FATAL_FAILURE(ReadMinidumpPathFromPipe(fds[0], &minidump_path));

  struct stat st;
  ASSERT_EQ(0, stat(minidump_path.c_str(), &st));
  ASSERT_GT(st.st_size, 0);
  unlink(minidump_path.c_str());
}

TEST(ExceptionHandlerTest, ChildCrashWithPath) {
  ASSERT_NO_FATAL_FAILURE(ChildCrash(false));
}

TEST(ExceptionHandlerTest, ChildCrashWithFD) {
  ASSERT_NO_FATAL_FAILURE(ChildCrash(true));
}

#endif  // !ADDRESS_SANITIZER

static bool DoneCallbackReturnFalse(const MinidumpDescriptor& descriptor,
                                    void* context,
                                    bool succeeded) {
  return false;
}

static bool DoneCallbackReturnTrue(const MinidumpDescriptor& descriptor,
                                   void* context,
                                   bool succeeded) {
  return true;
}

static bool DoneCallbackRaiseSIGKILL(const MinidumpDescriptor& descriptor,
                                     void* context,
                                     bool succeeded) {
  raise(SIGKILL);
  return true;
}

static bool FilterCallbackReturnFalse(void* context) {
  return false;
}

static bool FilterCallbackReturnTrue(void* context) {
  return true;
}

// SIGKILL cannot be blocked and a handler cannot be installed for it. In the
// following tests, if the child dies with signal SIGKILL, then the signal was
// redelivered to this handler. If the child dies with SIGSEGV then it wasn't.
static void RaiseSIGKILL(int sig) {
  raise(SIGKILL);
}

static bool InstallRaiseSIGKILL() {
  struct sigaction sa;
  memset(&sa, 0, sizeof(sa));
  sa.sa_handler = RaiseSIGKILL;
  return sigaction(SIGSEGV, &sa, NULL) != -1;
}

#ifndef ADDRESS_SANITIZER

static void CrashWithCallbacks(ExceptionHandler::FilterCallback filter,
                               ExceptionHandler::MinidumpCallback done,
                               string path) {
  ExceptionHandler handler(
      MinidumpDescriptor(path), filter, done, NULL, true, -1);
  // Crash with the exception handler in scope.
  DoNullPointerDereference();
}

TEST(ExceptionHandlerTest, RedeliveryOnFilterCallbackFalse) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ASSERT_TRUE(InstallRaiseSIGKILL());
    CrashWithCallbacks(FilterCallbackReturnFalse, NULL, temp_dir.path());
  }

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

TEST(ExceptionHandlerTest, RedeliveryOnDoneCallbackFalse) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ASSERT_TRUE(InstallRaiseSIGKILL());
    CrashWithCallbacks(NULL, DoneCallbackReturnFalse, temp_dir.path());
  }

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

TEST(ExceptionHandlerTest, NoRedeliveryOnDoneCallbackTrue) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ASSERT_TRUE(InstallRaiseSIGKILL());
    CrashWithCallbacks(NULL, DoneCallbackReturnTrue, temp_dir.path());
  }

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));
}

TEST(ExceptionHandlerTest, NoRedeliveryOnFilterCallbackTrue) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ASSERT_TRUE(InstallRaiseSIGKILL());
    CrashWithCallbacks(FilterCallbackReturnTrue, NULL, temp_dir.path());
  }

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));
}

TEST(ExceptionHandlerTest, RedeliveryToDefaultHandler) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    CrashWithCallbacks(FilterCallbackReturnFalse, NULL, temp_dir.path());
  }

  // As RaiseSIGKILL wasn't installed, the redelivery should just kill the child
  // with SIGSEGV.
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));
}

// Check that saving and restoring the signal handler with 'signal'
// instead of 'sigaction' doesn't make the Breakpad signal handler
// crash. See comments in ExceptionHandler::SignalHandler for full
// details.
TEST(ExceptionHandlerTest, RedeliveryOnBadSignalHandlerFlag) {
  AutoTempDir temp_dir;
  const pid_t child = fork();
  if (child == 0) {
    // Install the RaiseSIGKILL handler for SIGSEGV.
    ASSERT_TRUE(InstallRaiseSIGKILL());

    // Create a new exception handler, this installs a new SIGSEGV
    // handler, after saving the old one.
    ExceptionHandler handler(
        MinidumpDescriptor(temp_dir.path()), NULL,
        DoneCallbackReturnFalse, NULL, true, -1);

    // Install the default SIGSEGV handler, saving the current one.
    // Then re-install the current one with 'signal', this loses the
    // SA_SIGINFO flag associated with the Breakpad handler.
    sighandler_t old_handler = signal(SIGSEGV, SIG_DFL);
    ASSERT_NE(reinterpret_cast<void*>(old_handler),
              reinterpret_cast<void*>(SIG_ERR));
    ASSERT_NE(reinterpret_cast<void*>(signal(SIGSEGV, old_handler)),
              reinterpret_cast<void*>(SIG_ERR));

    // Crash with the exception handler in scope.
    DoNullPointerDereference();
  }
  // SIGKILL means Breakpad's signal handler didn't crash.
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

TEST(ExceptionHandlerTest, StackedHandlersDeliveredToTop) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ExceptionHandler bottom(MinidumpDescriptor(temp_dir.path()),
                            NULL,
                            NULL,
                            NULL,
                            true,
                            -1);
    CrashWithCallbacks(NULL, DoneCallbackRaiseSIGKILL, temp_dir.path());
  }
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

TEST(ExceptionHandlerTest, StackedHandlersNotDeliveredToBottom) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ExceptionHandler bottom(MinidumpDescriptor(temp_dir.path()),
                            NULL,
                            DoneCallbackRaiseSIGKILL,
                            NULL,
                            true,
                            -1);
    CrashWithCallbacks(NULL, NULL, temp_dir.path());
  }
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));
}

TEST(ExceptionHandlerTest, StackedHandlersFilteredToBottom) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ExceptionHandler bottom(MinidumpDescriptor(temp_dir.path()),
                            NULL,
                            DoneCallbackRaiseSIGKILL,
                            NULL,
                            true,
                            -1);
    CrashWithCallbacks(FilterCallbackReturnFalse, NULL, temp_dir.path());
  }
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

TEST(ExceptionHandlerTest, StackedHandlersUnhandledToBottom) {
  AutoTempDir temp_dir;

  const pid_t child = fork();
  if (child == 0) {
    ExceptionHandler bottom(MinidumpDescriptor(temp_dir.path()),
                            NULL,
                            DoneCallbackRaiseSIGKILL,
                            NULL,
                            true,
                            -1);
    CrashWithCallbacks(NULL, DoneCallbackReturnFalse, temp_dir.path());
  }
  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGKILL));
}

#endif  // !ADDRESS_SANITIZER

const unsigned char kIllegalInstruction[] = {
#if defined(__mips__)
  // mfc2 zero,Impl - usually illegal in userspace.
  0x48, 0x00, 0x00, 0x48
#else
  // This crashes with SIGILL on x86/x86-64/arm.
  0xff, 0xff, 0xff, 0xff
#endif
};

// Test that memory around the instruction pointer is written
// to the dump as a MinidumpMemoryRegion.
TEST(ExceptionHandlerTest, InstructionPointerMemory) {
  AutoTempDir temp_dir;
  int fds[2];
  ASSERT_NE(pipe(fds), -1);

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const uint32_t kMemorySize = 256;  // bytes
  const int kOffset = kMemorySize / 2;

  const pid_t child = fork();
  if (child == 0) {
    close(fds[0]);
    ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL,
                             DoneCallback, reinterpret_cast<void*>(fds[1]),
                             true, -1);
    // Get some executable memory.
    char* memory =
      reinterpret_cast<char*>(mmap(NULL,
                                   kMemorySize,
                                   PROT_READ | PROT_WRITE | PROT_EXEC,
                                   MAP_PRIVATE | MAP_ANON,
                                   -1,
                                   0));
    if (!memory)
      exit(0);

    // Write some instructions that will crash. Put them in the middle
    // of the block of memory, because the minidump should contain 128
    // bytes on either side of the instruction pointer.
    memcpy(memory + kOffset, kIllegalInstruction, sizeof(kIllegalInstruction));
    FlushInstructionCache(memory, kMemorySize);

    // Now execute the instructions, which should crash.
    typedef void (*void_function)(void);
    void_function memory_function =
        reinterpret_cast<void_function>(memory + kOffset);
    memory_function();
  }
  close(fds[1]);

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGILL));

  string minidump_path;
  ASSERT_NO_FATAL_FAILURE(ReadMinidumpPathFromPipe(fds[0], &minidump_path));

  struct stat st;
  ASSERT_EQ(0, stat(minidump_path.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  // Read the minidump. Locate the exception record and the
  // memory list, and then ensure that there is a memory region
  // in the memory list that covers the instruction pointer from
  // the exception record.
  Minidump minidump(minidump_path);
  ASSERT_TRUE(minidump.Read());

  MinidumpException* exception = minidump.GetException();
  MinidumpMemoryList* memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(exception);
  ASSERT_TRUE(memory_list);
  ASSERT_LT(0U, memory_list->region_count());

  MinidumpContext* context = exception->GetContext();
  ASSERT_TRUE(context);

  uint64_t instruction_pointer;
  ASSERT_TRUE(context->GetInstructionPointer(&instruction_pointer));

  MinidumpMemoryRegion* region =
      memory_list->GetMemoryRegionForAddress(instruction_pointer);
  ASSERT_TRUE(region);

  EXPECT_EQ(kMemorySize, region->GetSize());
  const uint8_t* bytes = region->GetMemory();
  ASSERT_TRUE(bytes);

  uint8_t prefix_bytes[kOffset];
  uint8_t suffix_bytes[kMemorySize - kOffset - sizeof(kIllegalInstruction)];
  memset(prefix_bytes, 0, sizeof(prefix_bytes));
  memset(suffix_bytes, 0, sizeof(suffix_bytes));
  EXPECT_TRUE(memcmp(bytes, prefix_bytes, sizeof(prefix_bytes)) == 0);
  EXPECT_TRUE(memcmp(bytes + kOffset, kIllegalInstruction, 
                     sizeof(kIllegalInstruction)) == 0);
  EXPECT_TRUE(memcmp(bytes + kOffset + sizeof(kIllegalInstruction),
                     suffix_bytes, sizeof(suffix_bytes)) == 0);

  unlink(minidump_path.c_str());
}

// Test that the memory region around the instruction pointer is
// bounded correctly on the low end.
TEST(ExceptionHandlerTest, InstructionPointerMemoryMinBound) {
  AutoTempDir temp_dir;
  int fds[2];
  ASSERT_NE(pipe(fds), -1);

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const uint32_t kMemorySize = 256;  // bytes
  const int kOffset = 0;

  const pid_t child = fork();
  if (child == 0) {
    close(fds[0]);
    ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL,
                             DoneCallback, reinterpret_cast<void*>(fds[1]),
                             true, -1);
    // Get some executable memory.
    char* memory =
        reinterpret_cast<char*>(mmap(NULL,
                                     kMemorySize,
                                     PROT_READ | PROT_WRITE | PROT_EXEC,
                                     MAP_PRIVATE | MAP_ANON,
                                     -1,
                                     0));
    if (!memory)
      exit(0);

    // Write some instructions that will crash. Put them in the middle
    // of the block of memory, because the minidump should contain 128
    // bytes on either side of the instruction pointer.
    memcpy(memory + kOffset, kIllegalInstruction, sizeof(kIllegalInstruction));
    FlushInstructionCache(memory, kMemorySize);

    // Now execute the instructions, which should crash.
    typedef void (*void_function)(void);
    void_function memory_function =
        reinterpret_cast<void_function>(memory + kOffset);
    memory_function();
  }
  close(fds[1]);

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGILL));

  string minidump_path;
  ASSERT_NO_FATAL_FAILURE(ReadMinidumpPathFromPipe(fds[0], &minidump_path));

  struct stat st;
  ASSERT_EQ(0, stat(minidump_path.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  // Read the minidump. Locate the exception record and the
  // memory list, and then ensure that there is a memory region
  // in the memory list that covers the instruction pointer from
  // the exception record.
  Minidump minidump(minidump_path);
  ASSERT_TRUE(minidump.Read());

  MinidumpException* exception = minidump.GetException();
  MinidumpMemoryList* memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(exception);
  ASSERT_TRUE(memory_list);
  ASSERT_LT(0U, memory_list->region_count());

  MinidumpContext* context = exception->GetContext();
  ASSERT_TRUE(context);

  uint64_t instruction_pointer;
  ASSERT_TRUE(context->GetInstructionPointer(&instruction_pointer));

  MinidumpMemoryRegion* region =
      memory_list->GetMemoryRegionForAddress(instruction_pointer);
  ASSERT_TRUE(region);

  EXPECT_EQ(kMemorySize / 2, region->GetSize());
  const uint8_t* bytes = region->GetMemory();
  ASSERT_TRUE(bytes);

  uint8_t suffix_bytes[kMemorySize / 2 - sizeof(kIllegalInstruction)];
  memset(suffix_bytes, 0, sizeof(suffix_bytes));
  EXPECT_TRUE(memcmp(bytes + kOffset, kIllegalInstruction, 
                     sizeof(kIllegalInstruction)) == 0);
  EXPECT_TRUE(memcmp(bytes + kOffset + sizeof(kIllegalInstruction),
                     suffix_bytes, sizeof(suffix_bytes)) == 0);
  unlink(minidump_path.c_str());
}

// Test that the memory region around the instruction pointer is
// bounded correctly on the high end.
TEST(ExceptionHandlerTest, InstructionPointerMemoryMaxBound) {
  AutoTempDir temp_dir;
  int fds[2];
  ASSERT_NE(pipe(fds), -1);

  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  // Use 4k here because the OS will hand out a single page even
  // if a smaller size is requested, and this test wants to
  // test the upper bound of the memory range.
  const uint32_t kMemorySize = 4096;  // bytes
  const int kOffset = kMemorySize - sizeof(kIllegalInstruction);

  const pid_t child = fork();
  if (child == 0) {
    close(fds[0]);
    ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL,
                             DoneCallback, reinterpret_cast<void*>(fds[1]),
                             true, -1);
    // Get some executable memory.
    char* memory =
        reinterpret_cast<char*>(mmap(NULL,
                                     kMemorySize,
                                     PROT_READ | PROT_WRITE | PROT_EXEC,
                                     MAP_PRIVATE | MAP_ANON,
                                     -1,
                                     0));
    if (!memory)
      exit(0);

    // Write some instructions that will crash. Put them in the middle
    // of the block of memory, because the minidump should contain 128
    // bytes on either side of the instruction pointer.
    memcpy(memory + kOffset, kIllegalInstruction, sizeof(kIllegalInstruction));
    FlushInstructionCache(memory, kMemorySize);

    // Now execute the instructions, which should crash.
    typedef void (*void_function)(void);
    void_function memory_function =
        reinterpret_cast<void_function>(memory + kOffset);
    memory_function();
  }
  close(fds[1]);

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGILL));

  string minidump_path;
  ASSERT_NO_FATAL_FAILURE(ReadMinidumpPathFromPipe(fds[0], &minidump_path));

  struct stat st;
  ASSERT_EQ(0, stat(minidump_path.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  // Read the minidump. Locate the exception record and the memory list, and
  // then ensure that there is a memory region in the memory list that covers
  // the instruction pointer from the exception record.
  Minidump minidump(minidump_path);
  ASSERT_TRUE(minidump.Read());

  MinidumpException* exception = minidump.GetException();
  MinidumpMemoryList* memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(exception);
  ASSERT_TRUE(memory_list);
  ASSERT_LT(0U, memory_list->region_count());

  MinidumpContext* context = exception->GetContext();
  ASSERT_TRUE(context);

  uint64_t instruction_pointer;
  ASSERT_TRUE(context->GetInstructionPointer(&instruction_pointer));

  MinidumpMemoryRegion* region =
      memory_list->GetMemoryRegionForAddress(instruction_pointer);
  ASSERT_TRUE(region);

  const size_t kPrefixSize = 128;  // bytes
  EXPECT_EQ(kPrefixSize + sizeof(kIllegalInstruction), region->GetSize());
  const uint8_t* bytes = region->GetMemory();
  ASSERT_TRUE(bytes);

  uint8_t prefix_bytes[kPrefixSize];
  memset(prefix_bytes, 0, sizeof(prefix_bytes));
  EXPECT_TRUE(memcmp(bytes, prefix_bytes, sizeof(prefix_bytes)) == 0);
  EXPECT_TRUE(memcmp(bytes + kPrefixSize,
                     kIllegalInstruction, sizeof(kIllegalInstruction)) == 0);

  unlink(minidump_path.c_str());
}

#ifndef ADDRESS_SANITIZER

// Ensure that an extra memory block doesn't get added when the instruction
// pointer is not in mapped memory.
TEST(ExceptionHandlerTest, InstructionPointerMemoryNullPointer) {
  AutoTempDir temp_dir;
  int fds[2];
  ASSERT_NE(pipe(fds), -1);

  const pid_t child = fork();
  if (child == 0) {
    close(fds[0]);
    ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL,
                             DoneCallback, reinterpret_cast<void*>(fds[1]),
                             true, -1);
    // Try calling a NULL pointer.
    typedef void (*void_function)(void);
    void_function memory_function = reinterpret_cast<void_function>(NULL);
    memory_function();
  }
  close(fds[1]);

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));

  string minidump_path;
  ASSERT_NO_FATAL_FAILURE(ReadMinidumpPathFromPipe(fds[0], &minidump_path));

  struct stat st;
  ASSERT_EQ(0, stat(minidump_path.c_str(), &st));
  ASSERT_GT(st.st_size, 0);

  // Read the minidump. Locate the exception record and the
  // memory list, and then ensure that there is a memory region
  // in the memory list that covers the instruction pointer from
  // the exception record.
  Minidump minidump(minidump_path);
  ASSERT_TRUE(minidump.Read());

  MinidumpException* exception = minidump.GetException();
  MinidumpMemoryList* memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(exception);
  ASSERT_TRUE(memory_list);
  ASSERT_EQ(static_cast<unsigned int>(1), memory_list->region_count());

  unlink(minidump_path.c_str());
}

#endif  // !ADDRESS_SANITIZER

// Test that anonymous memory maps can be annotated with names and IDs.
TEST(ExceptionHandlerTest, ModuleInfo) {
  // These are defined here so the parent can use them to check the
  // data from the minidump afterwards.
  const uint32_t kMemorySize = sysconf(_SC_PAGESIZE);
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
                                   kMemorySize,
                                   PROT_READ | PROT_WRITE,
                                   MAP_PRIVATE | MAP_ANON,
                                   -1,
                                   0));
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);

  AutoTempDir temp_dir;
  ExceptionHandler handler(
      MinidumpDescriptor(temp_dir.path()), NULL, NULL, NULL, true, -1);

  // Add info about the anonymous memory mapping.
  handler.AddMappingInfo(kMemoryName,
                         kModuleGUID,
                         kMemoryAddress,
                         kMemorySize,
                         0);
  ASSERT_TRUE(handler.WriteMinidump());

  const MinidumpDescriptor& minidump_desc = handler.minidump_descriptor();
  // Read the minidump. Load the module list, and ensure that the mmap'ed
  // |memory| is listed with the given module name and debug ID.
  Minidump minidump(minidump_desc.path());
  ASSERT_TRUE(minidump.Read());

  MinidumpModuleList* module_list = minidump.GetModuleList();
  ASSERT_TRUE(module_list);
  const MinidumpModule* module =
      module_list->GetModuleForAddress(kMemoryAddress);
  ASSERT_TRUE(module);

  EXPECT_EQ(kMemoryAddress, module->base_address());
  EXPECT_EQ(kMemorySize, module->size());
  EXPECT_EQ(kMemoryName, module->code_file());
  EXPECT_EQ(module_identifier, module->debug_identifier());

  unlink(minidump_desc.path());
}

static const unsigned kControlMsgSize =
    CMSG_SPACE(sizeof(int)) + CMSG_SPACE(sizeof(struct ucred));

static bool
CrashHandler(const void* crash_context, size_t crash_context_size,
             void* context) {
  const int fd = (intptr_t) context;
  int fds[2];
  if (pipe(fds) == -1) {
    // There doesn't seem to be any way to reliably handle
    // this failure without the parent process hanging
    // At least make sure that this process doesn't access
    // unexpected file descriptors
    fds[0] = -1;
    fds[1] = -1;
  }
  struct kernel_msghdr msg = {0};
  struct kernel_iovec iov;
  iov.iov_base = const_cast<void*>(crash_context);
  iov.iov_len = crash_context_size;
  msg.msg_iov = &iov;
  msg.msg_iovlen = 1;
  char cmsg[kControlMsgSize];
  memset(cmsg, 0, kControlMsgSize);
  msg.msg_control = cmsg;
  msg.msg_controllen = sizeof(cmsg);

  struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg);
  hdr->cmsg_level = SOL_SOCKET;
  hdr->cmsg_type = SCM_RIGHTS;
  hdr->cmsg_len = CMSG_LEN(sizeof(int));
  *((int*) CMSG_DATA(hdr)) = fds[1];
  hdr = CMSG_NXTHDR((struct msghdr*) &msg, hdr);
  hdr->cmsg_level = SOL_SOCKET;
  hdr->cmsg_type = SCM_CREDENTIALS;
  hdr->cmsg_len = CMSG_LEN(sizeof(struct ucred));
  struct ucred *cred = reinterpret_cast<struct ucred*>(CMSG_DATA(hdr));
  cred->uid = getuid();
  cred->gid = getgid();
  cred->pid = getpid();

  ssize_t ret = HANDLE_EINTR(sys_sendmsg(fd, &msg, 0));
  sys_close(fds[1]);
  if (ret <= 0)
    return false;

  char b;
  IGNORE_RET(HANDLE_EINTR(sys_read(fds[0], &b, 1)));

  return true;
}

#ifndef ADDRESS_SANITIZER

TEST(ExceptionHandlerTest, ExternalDumper) {
  int fds[2];
  ASSERT_NE(socketpair(AF_UNIX, SOCK_DGRAM, 0, fds), -1);
  static const int on = 1;
  setsockopt(fds[0], SOL_SOCKET, SO_PASSCRED, &on, sizeof(on));
  setsockopt(fds[1], SOL_SOCKET, SO_PASSCRED, &on, sizeof(on));

  const pid_t child = fork();
  if (child == 0) {
    close(fds[0]);
    ExceptionHandler handler(MinidumpDescriptor("/tmp1"), NULL, NULL,
                             reinterpret_cast<void*>(fds[1]), true, -1);
    handler.set_crash_handler(CrashHandler);
    DoNullPointerDereference();
  }
  close(fds[1]);
  struct msghdr msg = {0};
  struct iovec iov;
  static const unsigned kCrashContextSize =
      sizeof(ExceptionHandler::CrashContext);
  char context[kCrashContextSize];
  char control[kControlMsgSize];
  iov.iov_base = context;
  iov.iov_len = kCrashContextSize;
  msg.msg_iov = &iov;
  msg.msg_iovlen = 1;
  msg.msg_control = control;
  msg.msg_controllen = kControlMsgSize;

  const ssize_t n = HANDLE_EINTR(recvmsg(fds[0], &msg, 0));
  ASSERT_EQ(static_cast<ssize_t>(kCrashContextSize), n);
  ASSERT_EQ(kControlMsgSize, msg.msg_controllen);
  ASSERT_EQ(static_cast<__typeof__(msg.msg_flags)>(0), msg.msg_flags);
  ASSERT_EQ(0, close(fds[0]));

  pid_t crashing_pid = -1;
  int signal_fd = -1;
  for (struct cmsghdr *hdr = CMSG_FIRSTHDR(&msg); hdr;
       hdr = CMSG_NXTHDR(&msg, hdr)) {
    if (hdr->cmsg_level != SOL_SOCKET)
      continue;
    if (hdr->cmsg_type == SCM_RIGHTS) {
      const unsigned len = hdr->cmsg_len -
          (((uint8_t*)CMSG_DATA(hdr)) - (uint8_t*)hdr);
      ASSERT_EQ(sizeof(int), len);
      signal_fd = *(reinterpret_cast<int*>(CMSG_DATA(hdr)));
    } else if (hdr->cmsg_type == SCM_CREDENTIALS) {
      const struct ucred *cred =
          reinterpret_cast<struct ucred*>(CMSG_DATA(hdr));
      crashing_pid = cred->pid;
    }
  }

  ASSERT_NE(crashing_pid, -1);
  ASSERT_NE(signal_fd, -1);

  AutoTempDir temp_dir;
  string templ = temp_dir.path() + "/exception-handler-unittest";
  ASSERT_TRUE(WriteMinidump(templ.c_str(), crashing_pid, context,
                            kCrashContextSize));
  static const char b = 0;
  ASSERT_EQ(1, (HANDLE_EINTR(write(signal_fd, &b, 1))));
  ASSERT_EQ(0, close(signal_fd));

  ASSERT_NO_FATAL_FAILURE(WaitForProcessToTerminate(child, SIGSEGV));

  struct stat st;
  ASSERT_EQ(0, stat(templ.c_str(), &st));
  ASSERT_GT(st.st_size, 0);
  unlink(templ.c_str());
}

#endif  // !ADDRESS_SANITIZER

TEST(ExceptionHandlerTest, WriteMinidumpExceptionStream) {
  AutoTempDir temp_dir;
  ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL, NULL,
                           NULL, false, -1);
  ASSERT_TRUE(handler.WriteMinidump());

  string minidump_path = handler.minidump_descriptor().path();

  // Read the minidump and check the exception stream.
  Minidump minidump(minidump_path);
  ASSERT_TRUE(minidump.Read());
  MinidumpException* exception = minidump.GetException();
  ASSERT_TRUE(exception);
  const MDRawExceptionStream* raw = exception->exception();
  ASSERT_TRUE(raw);
  EXPECT_EQ(MD_EXCEPTION_CODE_LIN_DUMP_REQUESTED,
            raw->exception_record.exception_code);
}

TEST(ExceptionHandlerTest, GenerateMultipleDumpsWithFD) {
  AutoTempDir temp_dir;
  string path;
  const int fd = CreateTMPFile(temp_dir.path(), &path);
  ExceptionHandler handler(MinidumpDescriptor(fd), NULL, NULL, NULL, false, -1);
  ASSERT_TRUE(handler.WriteMinidump());
  // Check by the size of the data written to the FD that a minidump was
  // generated.
  off_t size = lseek(fd, 0, SEEK_CUR);
  ASSERT_GT(size, 0);

  // Generate another minidump.
  ASSERT_TRUE(handler.WriteMinidump());
  size = lseek(fd, 0, SEEK_CUR);
  ASSERT_GT(size, 0);
}

TEST(ExceptionHandlerTest, GenerateMultipleDumpsWithPath) {
  AutoTempDir temp_dir;
  ExceptionHandler handler(MinidumpDescriptor(temp_dir.path()), NULL, NULL,
                           NULL, false, -1);
  ASSERT_TRUE(handler.WriteMinidump());

  const MinidumpDescriptor& minidump_1 = handler.minidump_descriptor();
  struct stat st;
  ASSERT_EQ(0, stat(minidump_1.path(), &st));
  ASSERT_GT(st.st_size, 0);
  string minidump_1_path(minidump_1.path());
  // Check it is a valid minidump.
  Minidump minidump1(minidump_1_path);
  ASSERT_TRUE(minidump1.Read());
  unlink(minidump_1.path());

  // Generate another minidump, it should go to a different file.
  ASSERT_TRUE(handler.WriteMinidump());
  const MinidumpDescriptor& minidump_2 = handler.minidump_descriptor();
  ASSERT_EQ(0, stat(minidump_2.path(), &st));
  ASSERT_GT(st.st_size, 0);
  string minidump_2_path(minidump_2.path());
  // Check it is a valid minidump.
  Minidump minidump2(minidump_2_path);
  ASSERT_TRUE(minidump2.Read());
  unlink(minidump_2.path());

  // 2 distinct files should be produced.
  ASSERT_STRNE(minidump_1_path.c_str(), minidump_2_path.c_str());
}

// Test that an additional memory region can be added to the minidump.
TEST(ExceptionHandlerTest, AdditionalMemory) {
  const uint32_t kMemorySize = sysconf(_SC_PAGESIZE);

  // Get some heap memory.
  uint8_t* memory = new uint8_t[kMemorySize];
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);

  // Stick some data into the memory so the contents can be verified.
  for (uint32_t i = 0; i < kMemorySize; ++i) {
    memory[i] = i % 255;
  }

  AutoTempDir temp_dir;
  ExceptionHandler handler(
      MinidumpDescriptor(temp_dir.path()), NULL, NULL, NULL, true, -1);

  // Add the memory region to the list of memory to be included.
  handler.RegisterAppMemory(memory, kMemorySize);
  handler.WriteMinidump();

  const MinidumpDescriptor& minidump_desc = handler.minidump_descriptor();

  // Read the minidump. Ensure that the memory region is present
  Minidump minidump(minidump_desc.path());
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
}

// Test that a memory region that was previously registered
// can be unregistered.
TEST(ExceptionHandlerTest, AdditionalMemoryRemove) {
  const uint32_t kMemorySize = sysconf(_SC_PAGESIZE);

  // Get some heap memory.
  uint8_t* memory = new uint8_t[kMemorySize];
  const uintptr_t kMemoryAddress = reinterpret_cast<uintptr_t>(memory);
  ASSERT_TRUE(memory);

  AutoTempDir temp_dir;
  ExceptionHandler handler(
      MinidumpDescriptor(temp_dir.path()), NULL, NULL, NULL, true, -1);

  // Add the memory region to the list of memory to be included.
  handler.RegisterAppMemory(memory, kMemorySize);

  // ...and then remove it
  handler.UnregisterAppMemory(memory);
  handler.WriteMinidump();

  const MinidumpDescriptor& minidump_desc = handler.minidump_descriptor();

  // Read the minidump. Ensure that the memory region is not present.
  Minidump minidump(minidump_desc.path());
  ASSERT_TRUE(minidump.Read());

  MinidumpMemoryList* dump_memory_list = minidump.GetMemoryList();
  ASSERT_TRUE(dump_memory_list);
  const MinidumpMemoryRegion* region =
    dump_memory_list->GetMemoryRegionForAddress(kMemoryAddress);
  EXPECT_FALSE(region);

  delete[] memory;
}

static bool SimpleCallback(const MinidumpDescriptor& descriptor,
                           void* context,
                           bool succeeded) {
  string* filename = reinterpret_cast<string*>(context);
  *filename = descriptor.path();
  return true;
}

TEST(ExceptionHandlerTest, WriteMinidumpForChild) {
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

  AutoTempDir temp_dir;
  string minidump_filename;
  ASSERT_TRUE(
    ExceptionHandler::WriteMinidumpForChild(child, child,
                                            temp_dir.path(), SimpleCallback,
                                            (void*)&minidump_filename));

  Minidump minidump(minidump_filename);
  ASSERT_TRUE(minidump.Read());
  // Check that the crashing thread is the main thread of |child|
  MinidumpException* exception = minidump.GetException();
  ASSERT_TRUE(exception);
  uint32_t thread_id;
  ASSERT_TRUE(exception->GetThreadID(&thread_id));
  EXPECT_EQ(child, static_cast<int32_t>(thread_id));

  const MDRawExceptionStream* raw = exception->exception();
  ASSERT_TRUE(raw);
  EXPECT_EQ(MD_EXCEPTION_CODE_LIN_DUMP_REQUESTED,
            raw->exception_record.exception_code);

  close(fds[1]);
  unlink(minidump_filename.c_str());
}
