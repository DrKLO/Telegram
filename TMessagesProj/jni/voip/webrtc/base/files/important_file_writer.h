// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_FILES_IMPORTANT_FILE_WRITER_H_
#define BASE_FILES_IMPORTANT_FILE_WRITER_H_

#include <string>

#include "base/base_export.h"
#include "base/callback.h"
#include "base/files/file_path.h"
#include "base/macros.h"
#include "base/memory/ref_counted.h"
#include "base/sequence_checker.h"
#include "base/strings/string_piece.h"
#include "base/time/time.h"
#include "base/timer/timer.h"

namespace base {

class SequencedTaskRunner;

// Helper for atomically writing a file to ensure that it won't be corrupted by
// *application* crash during write (implemented as create, flush, rename).
//
// As an added benefit, ImportantFileWriter makes it less likely that the file
// is corrupted by *system* crash, though even if the ImportantFileWriter call
// has already returned at the time of the crash it is not specified which
// version of the file (old or new) is preserved. And depending on system
// configuration (hardware and software) a significant likelihood of file
// corruption may remain, thus using ImportantFileWriter is not a valid
// substitute for file integrity checks and recovery codepaths for malformed
// files.
//
// Also note that ImportantFileWriter can be *really* slow (cf. File::Flush()
// for details) and thus please don't block shutdown on ImportantFileWriter.
class BASE_EXPORT ImportantFileWriter {
 public:
  // Used by ScheduleSave to lazily provide the data to be saved. Allows us
  // to also batch data serializations.
  class BASE_EXPORT DataSerializer {
   public:
    // Should put serialized string in |data| and return true on successful
    // serialization. Will be called on the same thread on which
    // ImportantFileWriter has been created.
    virtual bool SerializeData(std::string* data) = 0;

   protected:
    virtual ~DataSerializer() = default;
  };

  // Save |data| to |path| in an atomic manner. Blocks and writes data on the
  // current thread. Does not guarantee file integrity across system crash (see
  // the class comment above).
  static bool WriteFileAtomically(const FilePath& path,
                                  StringPiece data,
                                  StringPiece histogram_suffix = StringPiece());

  // Initialize the writer.
  // |path| is the name of file to write.
  // |task_runner| is the SequencedTaskRunner instance where on which we will
  // execute file I/O operations.
  // All non-const methods, ctor and dtor must be called on the same thread.
  ImportantFileWriter(const FilePath& path,
                      scoped_refptr<SequencedTaskRunner> task_runner,
                      const char* histogram_suffix = nullptr);

  // Same as above, but with a custom commit interval.
  ImportantFileWriter(const FilePath& path,
                      scoped_refptr<SequencedTaskRunner> task_runner,
                      TimeDelta interval,
                      const char* histogram_suffix = nullptr);

  // You have to ensure that there are no pending writes at the moment
  // of destruction.
  ~ImportantFileWriter();

  const FilePath& path() const { return path_; }

  // Returns true if there is a scheduled write pending which has not yet
  // been started.
  bool HasPendingWrite() const;

  // Save |data| to target filename. Does not block. If there is a pending write
  // scheduled by ScheduleWrite(), it is cancelled.
  void WriteNow(std::unique_ptr<std::string> data);

  // Schedule a save to target filename. Data will be serialized and saved
  // to disk after the commit interval. If another ScheduleWrite is issued
  // before that, only one serialization and write to disk will happen, and
  // the most recent |serializer| will be used. This operation does not block.
  // |serializer| should remain valid through the lifetime of
  // ImportantFileWriter.
  void ScheduleWrite(DataSerializer* serializer);

  // Serialize data pending to be saved and execute write on backend thread.
  void DoScheduledWrite();

  // Registers |before_next_write_callback| and |after_next_write_callback| to
  // be synchronously invoked from WriteFileAtomically() before its next write
  // and after its next write, respectively. The boolean passed to
  // |after_next_write_callback| indicates whether the write was successful.
  // Both callbacks must be thread safe as they will be called on |task_runner_|
  // and may be called during Chrome shutdown.
  // If called more than once before a write is scheduled on |task_runner|, the
  // latest callbacks clobber the others.
  void RegisterOnNextWriteCallbacks(
      OnceClosure before_next_write_callback,
      OnceCallback<void(bool success)> after_next_write_callback);

  TimeDelta commit_interval() const {
    return commit_interval_;
  }

  // Overrides the timer to use for scheduling writes with |timer_override|.
  void SetTimerForTesting(OneShotTimer* timer_override);

 private:
  const OneShotTimer& timer() const {
    return timer_override_ ? *timer_override_ : timer_;
  }
  OneShotTimer& timer() { return timer_override_ ? *timer_override_ : timer_; }

  void ClearPendingWrite();

  // Invoked synchronously on the next write event.
  OnceClosure before_next_write_callback_;
  OnceCallback<void(bool success)> after_next_write_callback_;

  // Path being written to.
  const FilePath path_;

  // TaskRunner for the thread on which file I/O can be done.
  const scoped_refptr<SequencedTaskRunner> task_runner_;

  // Timer used to schedule commit after ScheduleWrite.
  OneShotTimer timer_;

  // An override for |timer_| used for testing.
  OneShotTimer* timer_override_ = nullptr;

  // Serializer which will provide the data to be saved.
  DataSerializer* serializer_;

  // Time delta after which scheduled data will be written to disk.
  const TimeDelta commit_interval_;

  // Custom histogram suffix.
  const std::string histogram_suffix_;

  SEQUENCE_CHECKER(sequence_checker_);

  WeakPtrFactory<ImportantFileWriter> weak_factory_{this};

  DISALLOW_COPY_AND_ASSIGN(ImportantFileWriter);
};

}  // namespace base

#endif  // BASE_FILES_IMPORTANT_FILE_WRITER_H_
