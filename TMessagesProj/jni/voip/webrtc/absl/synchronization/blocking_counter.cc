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

#include "absl/synchronization/blocking_counter.h"

#include "absl/base/internal/raw_logging.h"

namespace absl {
ABSL_NAMESPACE_BEGIN

// Return whether int *arg is zero.
static bool IsZero(void *arg) {
  return 0 == *reinterpret_cast<int *>(arg);
}

bool BlockingCounter::DecrementCount() {
  MutexLock l(&lock_);
  count_--;
  if (count_ < 0) {
    ABSL_RAW_LOG(
        FATAL,
        "BlockingCounter::DecrementCount() called too many times.  count=%d",
        count_);
  }
  return count_ == 0;
}

void BlockingCounter::Wait() {
  MutexLock l(&this->lock_);
  ABSL_RAW_CHECK(count_ >= 0, "BlockingCounter underflow");

  // only one thread may call Wait(). To support more than one thread,
  // implement a counter num_to_exit, like in the Barrier class.
  ABSL_RAW_CHECK(num_waiting_ == 0, "multiple threads called Wait()");
  num_waiting_++;

  this->lock_.Await(Condition(IsZero, &this->count_));

  // At this point, We know that all threads executing DecrementCount have
  // released the lock, and so will not touch this object again.
  // Therefore, the thread calling this method is free to delete the object
  // after we return from this method.
}

ABSL_NAMESPACE_END
}  // namespace absl
