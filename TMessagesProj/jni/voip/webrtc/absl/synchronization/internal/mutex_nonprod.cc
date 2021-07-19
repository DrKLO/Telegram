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

// Implementation of a small subset of Mutex and CondVar functionality
// for platforms where the production implementation hasn't been fully
// ported yet.

#include "absl/synchronization/mutex.h"

#if defined(_WIN32)
#include <chrono>  // NOLINT(build/c++11)
#else
#include <sys/time.h>
#include <time.h>
#endif

#include <algorithm>

#include "absl/base/internal/raw_logging.h"
#include "absl/time/time.h"

namespace absl {
ABSL_NAMESPACE_BEGIN
namespace synchronization_internal {

namespace {

// Return the current time plus the timeout.
absl::Time DeadlineFromTimeout(absl::Duration timeout) {
  return absl::Now() + timeout;
}

// Limit the deadline to a positive, 32-bit time_t value to accommodate
// implementation restrictions.  This also deals with InfinitePast and
// InfiniteFuture.
absl::Time LimitedDeadline(absl::Time deadline) {
  deadline = std::max(absl::FromTimeT(0), deadline);
  deadline = std::min(deadline, absl::FromTimeT(0x7fffffff));
  return deadline;
}

}  // namespace

#if defined(_WIN32)

MutexImpl::MutexImpl() {}

MutexImpl::~MutexImpl() {
  if (locked_) {
    std_mutex_.unlock();
  }
}

void MutexImpl::Lock() {
  std_mutex_.lock();
  locked_ = true;
}

bool MutexImpl::TryLock() {
  bool locked = std_mutex_.try_lock();
  if (locked) locked_ = true;
  return locked;
}

void MutexImpl::Unlock() {
  locked_ = false;
  released_.SignalAll();
  std_mutex_.unlock();
}

CondVarImpl::CondVarImpl() {}

CondVarImpl::~CondVarImpl() {}

void CondVarImpl::Signal() { std_cv_.notify_one(); }

void CondVarImpl::SignalAll() { std_cv_.notify_all(); }

void CondVarImpl::Wait(MutexImpl* mu) {
  mu->released_.SignalAll();
  std_cv_.wait(mu->std_mutex_);
}

bool CondVarImpl::WaitWithDeadline(MutexImpl* mu, absl::Time deadline) {
  mu->released_.SignalAll();
  time_t when = ToTimeT(deadline);
  int64_t nanos = ToInt64Nanoseconds(deadline - absl::FromTimeT(when));
  std::chrono::system_clock::time_point deadline_tp =
      std::chrono::system_clock::from_time_t(when) +
      std::chrono::duration_cast<std::chrono::system_clock::duration>(
          std::chrono::nanoseconds(nanos));
  auto deadline_since_epoch =
      std::chrono::duration_cast<std::chrono::duration<double>>(
          deadline_tp - std::chrono::system_clock::from_time_t(0));
  return std_cv_.wait_until(mu->std_mutex_, deadline_tp) ==
         std::cv_status::timeout;
}

#else  // ! _WIN32

MutexImpl::MutexImpl() {
  ABSL_RAW_CHECK(pthread_mutex_init(&pthread_mutex_, nullptr) == 0,
                 "pthread error");
}

MutexImpl::~MutexImpl() {
  if (locked_) {
    ABSL_RAW_CHECK(pthread_mutex_unlock(&pthread_mutex_) == 0, "pthread error");
  }
  ABSL_RAW_CHECK(pthread_mutex_destroy(&pthread_mutex_) == 0, "pthread error");
}

void MutexImpl::Lock() {
  ABSL_RAW_CHECK(pthread_mutex_lock(&pthread_mutex_) == 0, "pthread error");
  locked_ = true;
}

bool MutexImpl::TryLock() {
  bool locked = (0 == pthread_mutex_trylock(&pthread_mutex_));
  if (locked) locked_ = true;
  return locked;
}

void MutexImpl::Unlock() {
  locked_ = false;
  released_.SignalAll();
  ABSL_RAW_CHECK(pthread_mutex_unlock(&pthread_mutex_) == 0, "pthread error");
}

CondVarImpl::CondVarImpl() {
  ABSL_RAW_CHECK(pthread_cond_init(&pthread_cv_, nullptr) == 0,
                 "pthread error");
}

CondVarImpl::~CondVarImpl() {
  ABSL_RAW_CHECK(pthread_cond_destroy(&pthread_cv_) == 0, "pthread error");
}

void CondVarImpl::Signal() {
  ABSL_RAW_CHECK(pthread_cond_signal(&pthread_cv_) == 0, "pthread error");
}

void CondVarImpl::SignalAll() {
  ABSL_RAW_CHECK(pthread_cond_broadcast(&pthread_cv_) == 0, "pthread error");
}

void CondVarImpl::Wait(MutexImpl* mu) {
  mu->released_.SignalAll();
  ABSL_RAW_CHECK(pthread_cond_wait(&pthread_cv_, &mu->pthread_mutex_) == 0,
                 "pthread error");
}

bool CondVarImpl::WaitWithDeadline(MutexImpl* mu, absl::Time deadline) {
  mu->released_.SignalAll();
  struct timespec ts = ToTimespec(deadline);
  int rc = pthread_cond_timedwait(&pthread_cv_, &mu->pthread_mutex_, &ts);
  if (rc == ETIMEDOUT) return true;
  ABSL_RAW_CHECK(rc == 0, "pthread error");
  return false;
}

#endif  // ! _WIN32

void MutexImpl::Await(const Condition& cond) {
  if (cond.Eval()) return;
  released_.SignalAll();
  do {
    released_.Wait(this);
  } while (!cond.Eval());
}

bool MutexImpl::AwaitWithDeadline(const Condition& cond, absl::Time deadline) {
  if (cond.Eval()) return true;
  released_.SignalAll();
  while (true) {
    if (released_.WaitWithDeadline(this, deadline)) return false;
    if (cond.Eval()) return true;
  }
}

}  // namespace synchronization_internal

Mutex::Mutex() {}

Mutex::~Mutex() {}

void Mutex::Lock() { impl()->Lock(); }

void Mutex::Unlock() { impl()->Unlock(); }

bool Mutex::TryLock() { return impl()->TryLock(); }

void Mutex::ReaderLock() { Lock(); }

void Mutex::ReaderUnlock() { Unlock(); }

void Mutex::Await(const Condition& cond) { impl()->Await(cond); }

void Mutex::LockWhen(const Condition& cond) {
  Lock();
  Await(cond);
}

bool Mutex::AwaitWithDeadline(const Condition& cond, absl::Time deadline) {
  return impl()->AwaitWithDeadline(
      cond, synchronization_internal::LimitedDeadline(deadline));
}

bool Mutex::AwaitWithTimeout(const Condition& cond, absl::Duration timeout) {
  return AwaitWithDeadline(
      cond, synchronization_internal::DeadlineFromTimeout(timeout));
}

bool Mutex::LockWhenWithDeadline(const Condition& cond, absl::Time deadline) {
  Lock();
  return AwaitWithDeadline(cond, deadline);
}

bool Mutex::LockWhenWithTimeout(const Condition& cond, absl::Duration timeout) {
  return LockWhenWithDeadline(
      cond, synchronization_internal::DeadlineFromTimeout(timeout));
}

void Mutex::ReaderLockWhen(const Condition& cond) {
  ReaderLock();
  Await(cond);
}

bool Mutex::ReaderLockWhenWithTimeout(const Condition& cond,
                                      absl::Duration timeout) {
  return LockWhenWithTimeout(cond, timeout);
}
bool Mutex::ReaderLockWhenWithDeadline(const Condition& cond,
                                       absl::Time deadline) {
  return LockWhenWithDeadline(cond, deadline);
}

void Mutex::EnableDebugLog(const char*) {}
void Mutex::EnableInvariantDebugging(void (*)(void*), void*) {}
void Mutex::ForgetDeadlockInfo() {}
void Mutex::AssertHeld() const {}
void Mutex::AssertReaderHeld() const {}
void Mutex::AssertNotHeld() const {}

CondVar::CondVar() {}

CondVar::~CondVar() {}

void CondVar::Signal() { impl()->Signal(); }

void CondVar::SignalAll() { impl()->SignalAll(); }

void CondVar::Wait(Mutex* mu) { return impl()->Wait(mu->impl()); }

bool CondVar::WaitWithDeadline(Mutex* mu, absl::Time deadline) {
  return impl()->WaitWithDeadline(
      mu->impl(), synchronization_internal::LimitedDeadline(deadline));
}

bool CondVar::WaitWithTimeout(Mutex* mu, absl::Duration timeout) {
  return WaitWithDeadline(mu, absl::Now() + timeout);
}

void CondVar::EnableDebugLog(const char*) {}

#ifdef THREAD_SANITIZER
extern "C" void __tsan_read1(void *addr);
#else
#define __tsan_read1(addr)  // do nothing if TSan not enabled
#endif

// A function that just returns its argument, dereferenced
static bool Dereference(void *arg) {
  // ThreadSanitizer does not instrument this file for memory accesses.
  // This function dereferences a user variable that can participate
  // in a data race, so we need to manually tell TSan about this memory access.
  __tsan_read1(arg);
  return *(static_cast<bool *>(arg));
}

Condition::Condition() {}   // null constructor, used for kTrue only
const Condition Condition::kTrue;

Condition::Condition(bool (*func)(void *), void *arg)
    : eval_(&CallVoidPtrFunction),
      function_(func),
      method_(nullptr),
      arg_(arg) {}

bool Condition::CallVoidPtrFunction(const Condition *c) {
  return (*c->function_)(c->arg_);
}

Condition::Condition(const bool *cond)
    : eval_(CallVoidPtrFunction),
      function_(Dereference),
      method_(nullptr),
      // const_cast is safe since Dereference does not modify arg
      arg_(const_cast<bool *>(cond)) {}

bool Condition::Eval() const {
  // eval_ == null for kTrue
  return (this->eval_ == nullptr) || (*this->eval_)(this);
}

void RegisterSymbolizer(bool (*)(const void*, char*, int)) {}

ABSL_NAMESPACE_END
}  // namespace absl
