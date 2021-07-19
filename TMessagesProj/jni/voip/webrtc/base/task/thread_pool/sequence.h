// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_TASK_THREAD_POOL_SEQUENCE_H_
#define BASE_TASK_THREAD_POOL_SEQUENCE_H_

#include <stddef.h>

#include "base/base_export.h"
#include "base/compiler_specific.h"
#include "base/containers/queue.h"
#include "base/macros.h"
#include "base/optional.h"
#include "base/sequence_token.h"
#include "base/task/task_traits.h"
#include "base/task/thread_pool/pooled_parallel_task_runner.h"
#include "base/task/thread_pool/sequence_sort_key.h"
#include "base/task/thread_pool/task.h"
#include "base/task/thread_pool/task_source.h"
#include "base/threading/sequence_local_storage_map.h"

namespace base {
namespace internal {

// A Sequence holds slots each containing up to a single Task that must be
// executed in posting order.
//
// In comments below, an "empty Sequence" is a Sequence with no slot.
//
// Note: there is a known refcounted-ownership cycle in the Scheduler
// architecture: Sequence -> Task -> TaskRunner -> Sequence -> ...
// This is okay so long as the other owners of Sequence (PriorityQueue and
// WorkerThread in alternation and
// ThreadGroupImpl::WorkerThreadDelegateImpl::GetWork()
// temporarily) keep running it (and taking Tasks from it as a result). A
// dangling reference cycle would only occur should they release their reference
// to it while it's not empty. In other words, it is only correct for them to
// release it after PopTask() returns false to indicate it was made empty by
// that call (in which case the next PushTask() will return true to indicate to
// the caller that the Sequence should be re-enqueued for execution).
//
// This class is thread-safe.
class BASE_EXPORT Sequence : public TaskSource {
 public:
  // A Transaction can perform multiple operations atomically on a
  // Sequence. While a Transaction is alive, it is guaranteed that nothing
  // else will access the Sequence; the Sequence's lock is held for the
  // lifetime of the Transaction.
  class BASE_EXPORT Transaction : public TaskSource::Transaction {
   public:
    Transaction(Transaction&& other);
    ~Transaction();

    // Returns true if the sequence would need to be queued after receiving a
    // new Task.
    bool WillPushTask() const WARN_UNUSED_RESULT;

    // Adds |task| in a new slot at the end of the Sequence. This must only be
    // called after invoking WillPushTask().
    void PushTask(Task task);

    Sequence* sequence() const { return static_cast<Sequence*>(task_source()); }

   private:
    friend class Sequence;

    explicit Transaction(Sequence* sequence);

    DISALLOW_COPY_AND_ASSIGN(Transaction);
  };

  // |traits| is metadata that applies to all Tasks in the Sequence.
  // |task_runner| is a reference to the TaskRunner feeding this TaskSource.
  // |task_runner| can be nullptr only for tasks with no TaskRunner, in which
  // case |execution_mode| must be kParallel. Otherwise, |execution_mode| is the
  // execution mode of |task_runner|.
  Sequence(const TaskTraits& traits,
           TaskRunner* task_runner,
           TaskSourceExecutionMode execution_mode);

  // Begins a Transaction. This method cannot be called on a thread which has an
  // active Sequence::Transaction.
  Transaction BeginTransaction() WARN_UNUSED_RESULT;

  // TaskSource:
  ExecutionEnvironment GetExecutionEnvironment() override;
  size_t GetRemainingConcurrency() const override;

  // Returns a token that uniquely identifies this Sequence.
  const SequenceToken& token() const { return token_; }

  SequenceLocalStorageMap* sequence_local_storage() {
    return &sequence_local_storage_;
  }

 private:
  ~Sequence() override;

  // TaskSource:
  RunStatus WillRunTask() override;
  Task TakeTask(TaskSource::Transaction* transaction) override;
  Task Clear(TaskSource::Transaction* transaction) override;
  bool DidProcessTask(TaskSource::Transaction* transaction) override;
  SequenceSortKey GetSortKey() const override;

  // Releases reference to TaskRunner.
  void ReleaseTaskRunner();

  const SequenceToken token_ = SequenceToken::Create();

  // Queue of tasks to execute.
  base::queue<Task> queue_;

  // True if a worker is currently associated with a Task from this Sequence.
  bool has_worker_ = false;

  // Holds data stored through the SequenceLocalStorageSlot API.
  SequenceLocalStorageMap sequence_local_storage_;

  DISALLOW_COPY_AND_ASSIGN(Sequence);
};

}  // namespace internal
}  // namespace base

#endif  // BASE_TASK_THREAD_POOL_SEQUENCE_H_
