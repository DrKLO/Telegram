// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_SEQUENCE_TOKEN_H_
#define BASE_SEQUENCE_TOKEN_H_

#include "base/base_export.h"
#include "base/macros.h"

namespace base {

// A token that identifies a series of sequenced tasks (i.e. tasks that run one
// at a time in posting order).
class BASE_EXPORT SequenceToken {
 public:
  // Instantiates an invalid SequenceToken.
  SequenceToken() = default;

  // Explicitly allow copy.
  SequenceToken(const SequenceToken& other) = default;
  SequenceToken& operator=(const SequenceToken& other) = default;

  // An invalid SequenceToken is not equal to any other SequenceToken, including
  // other invalid SequenceTokens.
  bool operator==(const SequenceToken& other) const;
  bool operator!=(const SequenceToken& other) const;

  // Returns true if this is a valid SequenceToken.
  bool IsValid() const;

  // Returns the integer uniquely representing this SequenceToken. This method
  // should only be used for tracing and debugging.
  int ToInternalValue() const;

  // Returns a valid SequenceToken which isn't equal to any previously returned
  // SequenceToken.
  static SequenceToken Create();

  // Returns the SequenceToken associated with the task running on the current
  // thread, as determined by the active ScopedSetSequenceTokenForCurrentThread
  // if any.
  static SequenceToken GetForCurrentThread();

 private:
  explicit SequenceToken(int token) : token_(token) {}

  static constexpr int kInvalidSequenceToken = -1;
  int token_ = kInvalidSequenceToken;
};

// A token that identifies a task.
//
// This is used by ThreadCheckerImpl to determine whether calls to
// CalledOnValidThread() come from the same task and hence are deterministically
// single-threaded (vs. calls coming from different sequenced or parallel tasks,
// which may or may not run on the same thread).
class BASE_EXPORT TaskToken {
 public:
  // Instantiates an invalid TaskToken.
  TaskToken() = default;

  // Explicitly allow copy.
  TaskToken(const TaskToken& other) = default;
  TaskToken& operator=(const TaskToken& other) = default;

  // An invalid TaskToken is not equal to any other TaskToken, including
  // other invalid TaskTokens.
  bool operator==(const TaskToken& other) const;
  bool operator!=(const TaskToken& other) const;

  // Returns true if this is a valid TaskToken.
  bool IsValid() const;

  // In the scope of a ScopedSetSequenceTokenForCurrentThread, returns a valid
  // TaskToken which isn't equal to any TaskToken returned in the scope of a
  // different ScopedSetSequenceTokenForCurrentThread. Otherwise, returns an
  // invalid TaskToken.
  static TaskToken GetForCurrentThread();

 private:
  friend class ScopedSetSequenceTokenForCurrentThread;

  explicit TaskToken(int token) : token_(token) {}

  // Returns a valid TaskToken which isn't equal to any previously returned
  // TaskToken. This is private as it only meant to be instantiated by
  // ScopedSetSequenceTokenForCurrentThread.
  static TaskToken Create();

  static constexpr int kInvalidTaskToken = -1;
  int token_ = kInvalidTaskToken;
};

// Instantiate this in the scope where a single task runs.
class BASE_EXPORT ScopedSetSequenceTokenForCurrentThread {
 public:
  // Throughout the lifetime of the constructed object,
  // SequenceToken::GetForCurrentThread() will return |sequence_token| and
  // TaskToken::GetForCurrentThread() will return a TaskToken which is not equal
  // to any TaskToken returned in the scope of another
  // ScopedSetSequenceTokenForCurrentThread.
  ScopedSetSequenceTokenForCurrentThread(const SequenceToken& sequence_token);
  ~ScopedSetSequenceTokenForCurrentThread();

 private:
  const SequenceToken sequence_token_;
  const TaskToken task_token_;

  DISALLOW_COPY_AND_ASSIGN(ScopedSetSequenceTokenForCurrentThread);
};

}  // namespace base

#endif  // BASE_SEQUENCE_TOKEN_H_
