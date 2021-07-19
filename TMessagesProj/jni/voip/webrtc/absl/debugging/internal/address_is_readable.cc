// Copyright 2017 The Abseil Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// base::AddressIsReadable() probes an address to see whether it is readable,
// without faulting.

#include "absl/debugging/internal/address_is_readable.h"

#if !defined(__linux__) || defined(__ANDROID__)

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace debugging_internal {

// On platforms other than Linux, just return true.
bool AddressIsReadable(const void* /* addr */) { return true; }

}  // namespace debugging_internal
ABSL_NAMESPACE_END
}  // namespace absl

#else

#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <atomic>
#include <cerrno>
#include <cstdint>

#include "absl/base/internal/errno_saver.h"
#include "absl/base/internal/raw_logging.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace debugging_internal {

// Pack a pid and two file descriptors into a 64-bit word,
// using 16, 24, and 24 bits for each respectively.
static uint64_t Pack(uint64_t pid, uint64_t read_fd, uint64_t write_fd) {
  ABSL_RAW_CHECK((read_fd >> 24) == 0 && (write_fd >> 24) == 0,
                 "fd out of range");
  return (pid << 48) | ((read_fd & 0xffffff) << 24) | (write_fd & 0xffffff);
}

// Unpack x into a pid and two file descriptors, where x was created with
// Pack().
static void Unpack(uint64_t x, int *pid, int *read_fd, int *write_fd) {
  *pid = x >> 48;
  *read_fd = (x >> 24) & 0xffffff;
  *write_fd = x & 0xffffff;
}

// Return whether the byte at *addr is readable, without faulting.
// Save and restores errno.   Returns true on systems where
// unimplemented.
// This is a namespace-scoped variable for correct zero-initialization.
static std::atomic<uint64_t> pid_and_fds;  // initially 0, an invalid pid.
bool AddressIsReadable(const void *addr) {
  absl::base_internal::ErrnoSaver errno_saver;
  // We test whether a byte is readable by using write().  Normally, this would
  // be done via a cached file descriptor to /dev/null, but linux fails to
  // check whether the byte is readable when the destination is /dev/null, so
  // we use a cached pipe.  We store the pid of the process that created the
  // pipe to handle the case where a process forks, and the child closes all
  // the file descriptors and then calls this routine.  This is not perfect:
  // the child could use the routine, then close all file descriptors and then
  // use this routine again.  But the likely use of this routine is when
  // crashing, to test the validity of pages when dumping the stack.  Beware
  // that we may leak file descriptors, but we're unlikely to leak many.
  int bytes_written;
  int current_pid = getpid() & 0xffff;   // we use only the low order 16 bits
  do {  // until we do not get EBADF trying to use file descriptors
    int pid;
    int read_fd;
    int write_fd;
    uint64_t local_pid_and_fds = pid_and_fds.load(std::memory_order_relaxed);
    Unpack(local_pid_and_fds, &pid, &read_fd, &write_fd);
    while (current_pid != pid) {
      int p[2];
      // new pipe
      if (pipe(p) != 0) {
        ABSL_RAW_LOG(FATAL, "Failed to create pipe, errno=%d", errno);
      }
      fcntl(p[0], F_SETFD, FD_CLOEXEC);
      fcntl(p[1], F_SETFD, FD_CLOEXEC);
      uint64_t new_pid_and_fds = Pack(current_pid, p[0], p[1]);
      if (pid_and_fds.compare_exchange_strong(
              local_pid_and_fds, new_pid_and_fds, std::memory_order_relaxed,
              std::memory_order_relaxed)) {
        local_pid_and_fds = new_pid_and_fds;  // fds exposed to other threads
      } else {  // fds not exposed to other threads; we can close them.
        close(p[0]);
        close(p[1]);
        local_pid_and_fds = pid_and_fds.load(std::memory_order_relaxed);
      }
      Unpack(local_pid_and_fds, &pid, &read_fd, &write_fd);
    }
    errno = 0;
    // Use syscall(SYS_write, ...) instead of write() to prevent ASAN
    // and other checkers from complaining about accesses to arbitrary
    // memory.
    do {
      bytes_written = syscall(SYS_write, write_fd, addr, 1);
    } while (bytes_written == -1 && errno == EINTR);
    if (bytes_written == 1) {   // remove the byte from the pipe
      char c;
      while (read(read_fd, &c, 1) == -1 && errno == EINTR) {
      }
    }
    if (errno == EBADF) {  // Descriptors invalid.
      // If pid_and_fds contains the problematic file descriptors we just used,
      // this call will forget them, and the loop will try again.
      pid_and_fds.compare_exchange_strong(local_pid_and_fds, 0,
                                          std::memory_order_relaxed,
                                          std::memory_order_relaxed);
    }
  } while (errno == EBADF);
  return bytes_written == 1;
}

}  // namespace debugging_internal
ABSL_NAMESPACE_END
}  // namespace absl

#endif
