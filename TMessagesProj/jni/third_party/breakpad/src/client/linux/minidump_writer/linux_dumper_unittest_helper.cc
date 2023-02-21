// Copyright (c) 2010, Google Inc.
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
//
// Helper program for the linux_dumper class, which creates a bunch of
// threads. The first word of each thread's stack is set to the thread
// id.

#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/syscall.h>
#include <unistd.h>

#include "common/scoped_ptr.h"
#include "third_party/lss/linux_syscall_support.h"

#if defined(__ARM_EABI__)
#define TID_PTR_REGISTER "r3"
#elif defined(__aarch64__)
#define TID_PTR_REGISTER "x3"
#elif defined(__i386)
#define TID_PTR_REGISTER "ecx"
#elif defined(__x86_64)
#define TID_PTR_REGISTER "rcx"
#elif defined(__mips__)
#define TID_PTR_REGISTER "$1"
#else
#error This test has not been ported to this platform.
#endif

void *thread_function(void *data) {
  int pipefd = *static_cast<int *>(data);
  volatile pid_t thread_id = syscall(__NR_gettid);
  // Signal parent that a thread has started.
  uint8_t byte = 1;
  if (write(pipefd, &byte, sizeof(byte)) != sizeof(byte)) {
    perror("ERROR: parent notification failed");
    return NULL;
  }
  register volatile pid_t *thread_id_ptr asm(TID_PTR_REGISTER) = &thread_id;
  while (true)
    asm volatile ("" : : "r" (thread_id_ptr));
  return NULL;
}

int main(int argc, char *argv[]) {
  if (argc < 3) {
    fprintf(stderr,
            "usage: linux_dumper_unittest_helper <pipe fd> <# of threads>\n");
    return 1;
  }
  int pipefd = atoi(argv[1]);
  int num_threads = atoi(argv[2]);
  if (num_threads < 1) {
    fprintf(stderr, "ERROR: number of threads is 0");
    return 1;
  }
  google_breakpad::scoped_array<pthread_t> threads(new pthread_t[num_threads]);
  pthread_attr_t thread_attributes;
  pthread_attr_init(&thread_attributes);
  pthread_attr_setdetachstate(&thread_attributes, PTHREAD_CREATE_DETACHED);
  for (int i = 1; i < num_threads; i++) {
    pthread_create(&threads[i], &thread_attributes, &thread_function, &pipefd);
  }
  thread_function(&pipefd);
  return 0;
}
