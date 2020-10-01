// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_FILES_FILE_DESCRIPTOR_WATCHER_POSIX_H_
#define BASE_FILES_FILE_DESCRIPTOR_WATCHER_POSIX_H_

#include <memory>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/logging.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/memory/weak_ptr.h"
#include "base/message_loop/message_pump_for_io.h"
#include "base/sequence_checker.h"
#include "base/single_thread_task_runner.h"

namespace base {

class SingleThreadTaskRunner;

// The FileDescriptorWatcher API allows callbacks to be invoked when file
// descriptors are readable or writable without blocking.
//
// To enable this API in unit tests, use a TaskEnvironment with
// MainThreadType::IO.
//
// Note: Prefer FileDescriptorWatcher to MessageLoopForIO::WatchFileDescriptor()
// for non-critical IO. FileDescriptorWatcher works on threads/sequences without
// MessagePumps but involves going through the task queue after being notified
// by the OS (a desirablable property for non-critical IO that shouldn't preempt
// the main queue).
class BASE_EXPORT FileDescriptorWatcher {
 public:
  // Instantiated and returned by WatchReadable() or WatchWritable(). The
  // constructor registers a callback to be invoked when a file descriptor is
  // readable or writable without blocking and the destructor unregisters it.
  class Controller {
   public:
    // Unregisters the callback registered by the constructor.
    ~Controller();

   private:
    friend class FileDescriptorWatcher;
    class Watcher;

    // Registers |callback| to be invoked when |fd| is readable or writable
    // without blocking (depending on |mode|).
    Controller(MessagePumpForIO::Mode mode,
               int fd,
               const RepeatingClosure& callback);

    // Starts watching the file descriptor.
    void StartWatching();

    // Runs |callback_|.
    void RunCallback();

    // The callback to run when the watched file descriptor is readable or
    // writable without blocking.
    RepeatingClosure callback_;

    // TaskRunner associated with the MessageLoopForIO that watches the file
    // descriptor.
    const scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner_;

    // Notified by the MessageLoopForIO associated with
    // |io_thread_task_runner_| when the watched file descriptor is
    // readable or writable without blocking. Posts a task to run RunCallback()
    // on the sequence on which the Controller was instantiated. When the
    // Controller is deleted, ownership of |watcher_| is transfered to a delete
    // task posted to the MessageLoopForIO. This ensures that |watcher_| isn't
    // deleted while it is being used by the MessageLoopForIO.
    std::unique_ptr<Watcher> watcher_;

    // Validates that the Controller is used on the sequence on which it was
    // instantiated.
    SequenceChecker sequence_checker_;

    WeakPtrFactory<Controller> weak_factory_{this};

    DISALLOW_COPY_AND_ASSIGN(Controller);
  };

  // Registers |io_thread_task_runner| to watch file descriptors for which
  // callbacks are registered from the current thread via WatchReadable() or
  // WatchWritable(). |io_thread_task_runner| must post tasks to a thread which
  // runs a MessagePumpForIO. If it is not the current thread, it must be highly
  // responsive (i.e. not used to run other expensive tasks such as potentially
  // blocking I/O) since ~Controller waits for a task posted to it.
  explicit FileDescriptorWatcher(
      scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner);
  ~FileDescriptorWatcher();

  // Registers |callback| to be posted on the current sequence when |fd| is
  // readable or writable without blocking. |callback| is unregistered when the
  // returned Controller is deleted (deletion must happen on the current
  // sequence).
  // Usage note: To call these methods, a FileDescriptorWatcher must have been
  // instantiated on the current thread and SequencedTaskRunnerHandle::IsSet()
  // must return true (these conditions are met at least on all ThreadPool
  // threads as well as on threads backed by a MessageLoopForIO). |fd| must
  // outlive the returned Controller.
  // Shutdown note: notifications aren't guaranteed to be emitted once the bound
  // (current) SequencedTaskRunner enters its shutdown phase (i.e.
  // ThreadPool::Shutdown() or Thread::Stop()) regardless of the
  // SequencedTaskRunner's TaskShutdownBehavior.
  static std::unique_ptr<Controller> WatchReadable(
      int fd,
      const RepeatingClosure& callback);
  static std::unique_ptr<Controller> WatchWritable(
      int fd,
      const RepeatingClosure& callback);

  // Asserts that usage of this API is allowed on this thread.
  static void AssertAllowed()
#if DCHECK_IS_ON()
      ;
#else
  {
  }
#endif

 private:
  scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner() const {
    return io_thread_task_runner_;
  }

  const scoped_refptr<SingleThreadTaskRunner> io_thread_task_runner_;

  DISALLOW_COPY_AND_ASSIGN(FileDescriptorWatcher);
};

}  // namespace base

#endif  // BASE_FILES_FILE_DESCRIPTOR_WATCHER_POSIX_H_
