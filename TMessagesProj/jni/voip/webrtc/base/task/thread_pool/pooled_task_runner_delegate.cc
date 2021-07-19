// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/thread_pool/pooled_task_runner_delegate.h"

namespace base {
namespace internal {

namespace {

// Indicates whether a PooledTaskRunnerDelegate instance exists in the
// process. Used to tell when a task is posted from the main thread after the
// task environment was brought down in unit tests so that TaskRunners can
// return false on PostTask, letting callers know they should complete
// necessary work synchronously. A PooledTaskRunnerDelegate is usually
// instantiated before worker threads are started and deleted after worker
// threads have been joined. This makes the variable const while worker threads
// are up and as such it doesn't need to be atomic.
bool g_exists = false;

}  // namespace

PooledTaskRunnerDelegate::PooledTaskRunnerDelegate() {
  DCHECK(!g_exists);
  g_exists = true;
}

PooledTaskRunnerDelegate::~PooledTaskRunnerDelegate() {
  DCHECK(g_exists);
  g_exists = false;
}

// static
bool PooledTaskRunnerDelegate::Exists() {
  return g_exists;
}

}  // namespace internal
}  // namespace base
