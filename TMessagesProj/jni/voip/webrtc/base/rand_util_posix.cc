// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/rand_util.h"

#include <errno.h>
#include <fcntl.h>
#include <stddef.h>
#include <stdint.h>
#include <unistd.h>

#include "base/files/file_util.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/posix/eintr_wrapper.h"

namespace {

// We keep the file descriptor for /dev/urandom around so we don't need to
// reopen it (which is expensive), and since we may not even be able to reopen
// it if we are later put in a sandbox. This class wraps the file descriptor so
// we can use a static-local variable to handle opening it on the first access.
class URandomFd {
 public:
#if defined(OS_AIX)
  // AIX has no 64-bit support for open falgs such as -
  //  O_CLOEXEC, O_NOFOLLOW and O_TTY_INIT
  URandomFd() : fd_(HANDLE_EINTR(open("/dev/urandom", O_RDONLY))) {
    DPCHECK(fd_ >= 0) << "Cannot open /dev/urandom";
  }
#else
  URandomFd() : fd_(HANDLE_EINTR(open("/dev/urandom", O_RDONLY | O_CLOEXEC))) {
    DPCHECK(fd_ >= 0) << "Cannot open /dev/urandom";
  }
#endif

  ~URandomFd() { close(fd_); }

  int fd() const { return fd_; }

 private:
  const int fd_;
};

}  // namespace

namespace base {

void RandBytes(void* output, size_t output_length) {
  const int urandom_fd = GetUrandomFD();
  const bool success =
      ReadFromFD(urandom_fd, static_cast<char*>(output), output_length);
  CHECK(success);
}

int GetUrandomFD() {
  static NoDestructor<URandomFd> urandom_fd;
  return urandom_fd->fd();
}

}  // namespace base
