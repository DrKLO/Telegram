// Copyright (c) 2013, Google Inc.
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

// Utility class for creating a temporary file for unit tests
// that is deleted in the destructor.

#ifndef GOOGLE_BREAKPAD_COMMON_LINUX_TESTS_AUTO_TESTFILE
#define GOOGLE_BREAKPAD_COMMON_LINUX_TESTS_AUTO_TESTFILE

#include <unistd.h>
#include <sys/types.h>

#include <string>

#include "breakpad_googletest_includes.h"
#include "common/linux/eintr_wrapper.h"
#include "common/tests/auto_tempdir.h"

namespace google_breakpad {

class AutoTestFile {
 public:
  // Create a new empty test file.
  // test_prefix: (input) test-specific prefix, can't be NULL.
  explicit AutoTestFile(const char* test_prefix) {
    Init(test_prefix);
  }

  // Create a new test file, and fill it with initial data from a C string.
  // The terminating zero is not written.
  // test_prefix: (input) test-specific prefix, can't be NULL.
  // text: (input) initial content.
  AutoTestFile(const char* test_prefix, const char* text) {
    Init(test_prefix);
    if (fd_ >= 0)
      WriteText(text, static_cast<size_t>(strlen(text)));
  }

  AutoTestFile(const char* test_prefix, const char* text, size_t text_len) {
    Init(test_prefix);
    if (fd_ >= 0)
      WriteText(text, text_len);
  }

  // Destroy test file on scope exit.
  ~AutoTestFile() {
    if (fd_ >= 0) {
      close(fd_);
      fd_ = -1;
    }
  }

  // Returns true iff the test file could be created properly.
  // Useful in tests inside EXPECT_TRUE(file.IsOk());
  bool IsOk() {
    return fd_ >= 0;
  }

  // Returns the Posix file descriptor for the test file, or -1
  // If IsOk() returns false. Note: on Windows, this always returns -1.
  int GetFd() {
    return fd_;
  }

 private:
  void Init(const char* test_prefix) {
    fd_ = -1;
    char path_templ[PATH_MAX];
    int ret = snprintf(path_templ, sizeof(path_templ),
                       TEMPDIR "/%s-unittest.XXXXXX",
                       test_prefix);
    if (ret >= static_cast<int>(sizeof(path_templ)))
      return;

    fd_ = mkstemp(path_templ);
    if (fd_ < 0)
      return;

    unlink(path_templ);
  }

  void WriteText(const char* text, size_t text_len) {
    ssize_t r = HANDLE_EINTR(write(fd_, text, text_len));
    if (r != static_cast<ssize_t>(text_len)) {
      close(fd_);
      fd_ = -1;
      return;
    }

    lseek(fd_, 0, SEEK_SET);
  }

  int fd_;
};

}  // namespace google_breakpad

#endif  // GOOGLE_BREAKPAD_COMMON_LINUX_TESTS_AUTO_TESTFILE
