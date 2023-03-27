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

// crash_generator.cc: Implement google_breakpad::CrashGenerator.
// See crash_generator.h for details.

#include "common/linux/tests/crash_generator.h"

#include <pthread.h>
#include <signal.h>
#include <stdio.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#include <string>

#if defined(__ANDROID__)
#include "common/android/testing/pthread_fixes.h"
#endif
#include "common/linux/eintr_wrapper.h"
#include "common/tests/auto_tempdir.h"
#include "common/tests/file_utils.h"
#include "common/using_std_string.h"

namespace {

struct ThreadData {
  pthread_t thread;
  pthread_barrier_t* barrier;
  pid_t* thread_id_ptr;
};

const char* const kProcFilesToCopy[] = {
  "auxv", "cmdline", "environ", "maps", "status"
};
const size_t kNumProcFilesToCopy =
    sizeof(kProcFilesToCopy) / sizeof(kProcFilesToCopy[0]);

int gettid() {
  // Glibc does not provide a wrapper for this.
  return syscall(__NR_gettid);
}

int tkill(pid_t tid, int sig) {
  // Glibc does not provide a wrapper for this.
  return syscall(__NR_tkill, tid, sig);
}

// Core file size limit set to 1 MB, which is big enough for test purposes.
const rlim_t kCoreSizeLimit = 1024 * 1024;

void *thread_function(void *data) {
  ThreadData* thread_data = reinterpret_cast<ThreadData*>(data);
  volatile pid_t thread_id = gettid();
  *(thread_data->thread_id_ptr) = thread_id;
  int result = pthread_barrier_wait(thread_data->barrier);
  if (result != 0 && result != PTHREAD_BARRIER_SERIAL_THREAD) {
    perror("Failed to wait for sync barrier");
    exit(1);
  }
  while (true) {
    pthread_yield();
  }
}

}  // namespace

namespace google_breakpad {

CrashGenerator::CrashGenerator()
    : shared_memory_(NULL),
      shared_memory_size_(0) {
}

CrashGenerator::~CrashGenerator() {
  UnmapSharedMemory();
}

bool CrashGenerator::HasDefaultCorePattern() const {
  char buffer[8];
  ssize_t buffer_size = sizeof(buffer);
  return ReadFile("/proc/sys/kernel/core_pattern", buffer, &buffer_size) &&
         buffer_size == 5 && memcmp(buffer, "core", 4) == 0;
}

string CrashGenerator::GetCoreFilePath() const {
  return temp_dir_.path() + "/core";
}

string CrashGenerator::GetDirectoryOfProcFilesCopy() const {
  return temp_dir_.path() + "/proc";
}

pid_t CrashGenerator::GetThreadId(unsigned index) const {
  return reinterpret_cast<pid_t*>(shared_memory_)[index];
}

pid_t* CrashGenerator::GetThreadIdPointer(unsigned index) {
  return reinterpret_cast<pid_t*>(shared_memory_) + index;
}

bool CrashGenerator::MapSharedMemory(size_t memory_size) {
  if (!UnmapSharedMemory())
    return false;

  void* mapped_memory = mmap(0, memory_size, PROT_READ | PROT_WRITE,
                             MAP_SHARED | MAP_ANONYMOUS, -1, 0);
  if (mapped_memory == MAP_FAILED) {
    perror("CrashGenerator: Failed to map shared memory");
    return false;
  }

  memset(mapped_memory, 0, memory_size);
  shared_memory_ = mapped_memory;
  shared_memory_size_ = memory_size;
  return true;
}

bool CrashGenerator::UnmapSharedMemory() {
  if (!shared_memory_)
    return true;

  if (munmap(shared_memory_, shared_memory_size_) == 0) {
    shared_memory_ = NULL;
    shared_memory_size_ = 0;
    return true;
  }

  perror("CrashGenerator: Failed to unmap shared memory");
  return false;
}

bool CrashGenerator::SetCoreFileSizeLimit(rlim_t limit) const {
  struct rlimit limits = { limit, limit };
  if (setrlimit(RLIMIT_CORE, &limits) == -1) {
    perror("CrashGenerator: Failed to set core file size limit");
    return false;
  }
  return true;
}

bool CrashGenerator::CreateChildCrash(
    unsigned num_threads, unsigned crash_thread, int crash_signal,
    pid_t* child_pid) {
  if (num_threads == 0 || crash_thread >= num_threads) {
    fprintf(stderr, "CrashGenerator: Invalid thread counts; num_threads=%u"
                    " crash_thread=%u\n", num_threads, crash_thread);
    return false;
  }

  if (!MapSharedMemory(num_threads * sizeof(pid_t))) {
    perror("CrashGenerator: Unable to map shared memory");
    return false;
  }

  pid_t pid = fork();
  if (pid == 0) {
    if (chdir(temp_dir_.path().c_str()) == -1) {
      perror("CrashGenerator: Failed to change directory");
      exit(1);
    }
    if (SetCoreFileSizeLimit(kCoreSizeLimit)) {
      CreateThreadsInChildProcess(num_threads);
      string proc_dir = GetDirectoryOfProcFilesCopy();
      if (mkdir(proc_dir.c_str(), 0755) == -1) {
        perror("CrashGenerator: Failed to create proc directory");
        exit(1);
      }
      if (!CopyProcFiles(getpid(), proc_dir.c_str())) {
        fprintf(stderr, "CrashGenerator: Failed to copy proc files\n");
        exit(1);
      }
      // On Android the signal sometimes doesn't seem to get sent even though
      // tkill returns '0'.  Retry a couple of times if the signal doesn't get
      // through on the first go:
      // https://code.google.com/p/google-breakpad/issues/detail?id=579
#if defined(__ANDROID__)
      const int kRetries = 60;
      const unsigned int kSleepTimeInSeconds = 1;
#else
      const int kRetries = 1;
      const unsigned int kSleepTimeInSeconds = 600;
#endif
      for (int i = 0; i < kRetries; i++) {
        if (tkill(*GetThreadIdPointer(crash_thread), crash_signal) == -1) {
          perror("CrashGenerator: Failed to kill thread by signal");
        } else {
          // At this point, we've queued the signal for delivery, but there's no
          // guarantee when it'll be delivered.  We don't want the main thread to
          // race and exit before the thread we signaled is processed.  So sleep
          // long enough that we won't flake even under fairly high load.
          // TODO: See if we can't be a bit more deterministic.  There doesn't
          // seem to be an API to check on signal delivery status, so we can't
          // really poll and wait for the kernel to declare the signal has been
          // delivered.  If it has, and things worked, we'd be killed, so the
          // sleep length doesn't really matter.
          sleep(kSleepTimeInSeconds);
        }
      }
    } else {
      perror("CrashGenerator: Failed to set core limit");
    }
    exit(1);
  } else if (pid == -1) {
    perror("CrashGenerator: Failed to create child process");
    return false;
  }

  int status;
  if (HANDLE_EINTR(waitpid(pid, &status, 0)) == -1) {
    perror("CrashGenerator: Failed to wait for child process");
    return false;
  }
  if (!WIFSIGNALED(status) || WTERMSIG(status) != crash_signal) {
    fprintf(stderr, "CrashGenerator: Child process not killed by the expected signal\n"
                    "  exit status=0x%x pid=%u signaled=%s sig=%d expected=%d\n",
                    status, pid, WIFSIGNALED(status) ? "true" : "false",
                    WTERMSIG(status), crash_signal);
    return false;
  }

  if (child_pid)
    *child_pid = pid;
  return true;
}

bool CrashGenerator::CopyProcFiles(pid_t pid, const char* path) const {
  char from_path[PATH_MAX], to_path[PATH_MAX];
  for (size_t i = 0; i < kNumProcFilesToCopy; ++i) {
    int num_chars = snprintf(from_path, PATH_MAX, "/proc/%d/%s",
                             pid, kProcFilesToCopy[i]);
    if (num_chars < 0 || num_chars >= PATH_MAX)
      return false;

    num_chars = snprintf(to_path, PATH_MAX, "%s/%s",
                         path, kProcFilesToCopy[i]);
    if (num_chars < 0 || num_chars >= PATH_MAX)
      return false;

    if (!CopyFile(from_path, to_path))
      return false;
  }
  return true;
}

void CrashGenerator::CreateThreadsInChildProcess(unsigned num_threads) {
  *GetThreadIdPointer(0) = getpid();

  if (num_threads <= 1)
    return;

  // This method does not clean up any pthread resource, as the process
  // is expected to be killed anyway.
  ThreadData* thread_data = new ThreadData[num_threads];

  // Create detached threads so that we do not worry about pthread_join()
  // later being called or not.
  pthread_attr_t thread_attributes;
  if (pthread_attr_init(&thread_attributes) != 0 ||
      pthread_attr_setdetachstate(&thread_attributes,
                                  PTHREAD_CREATE_DETACHED) != 0) {
    fprintf(stderr, "CrashGenerator: Failed to initialize thread attribute\n");
    exit(1);
  }

  pthread_barrier_t thread_barrier;
  if (pthread_barrier_init(&thread_barrier, NULL, num_threads) != 0) {
    fprintf(stderr, "CrashGenerator: Failed to initialize thread barrier\n");
    exit(1);
  }

  for (unsigned i = 1; i < num_threads; ++i) {
    thread_data[i].barrier = &thread_barrier;
    thread_data[i].thread_id_ptr = GetThreadIdPointer(i);
    if (pthread_create(&thread_data[i].thread, &thread_attributes,
                       thread_function, &thread_data[i]) != 0) {
      fprintf(stderr, "CrashGenerator: Failed to create thread %d\n", i);
      exit(1);
    }
  }

  int result = pthread_barrier_wait(&thread_barrier);
  if (result != 0 && result != PTHREAD_BARRIER_SERIAL_THREAD) {
    fprintf(stderr, "CrashGenerator: Failed to wait for thread barrier\n");
    exit(1);
  }

  pthread_barrier_destroy(&thread_barrier);
  pthread_attr_destroy(&thread_attributes);
  delete[] thread_data;
}

}  // namespace google_breakpad
