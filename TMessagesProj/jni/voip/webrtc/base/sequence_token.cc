// Copyright 2016 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/sequence_token.h"

#include "base/atomic_sequence_num.h"
#include "base/logging.h"
#include "base/no_destructor.h"
#include "base/threading/thread_local.h"

namespace base {

namespace {

base::AtomicSequenceNumber g_sequence_token_generator;

base::AtomicSequenceNumber g_task_token_generator;

ThreadLocalPointer<const SequenceToken>& GetTlsCurrentSequenceToken() {
  static base::NoDestructor<ThreadLocalPointer<const SequenceToken>> instance;
  return *instance;
}

ThreadLocalPointer<const TaskToken>& GetTlsCurrentTaskToken() {
  static base::NoDestructor<ThreadLocalPointer<const TaskToken>> instance;
  return *instance;
}

}  // namespace

bool SequenceToken::operator==(const SequenceToken& other) const {
  return token_ == other.token_ && IsValid();
}

bool SequenceToken::operator!=(const SequenceToken& other) const {
  return !(*this == other);
}

bool SequenceToken::IsValid() const {
  return token_ != kInvalidSequenceToken;
}

int SequenceToken::ToInternalValue() const {
  return token_;
}

SequenceToken SequenceToken::Create() {
  return SequenceToken(g_sequence_token_generator.GetNext());
}

SequenceToken SequenceToken::GetForCurrentThread() {
  const SequenceToken* current_sequence_token =
      GetTlsCurrentSequenceToken().Get();
  return current_sequence_token ? *current_sequence_token : SequenceToken();
}

bool TaskToken::operator==(const TaskToken& other) const {
  return token_ == other.token_ && IsValid();
}

bool TaskToken::operator!=(const TaskToken& other) const {
  return !(*this == other);
}

bool TaskToken::IsValid() const {
  return token_ != kInvalidTaskToken;
}

TaskToken TaskToken::Create() {
  return TaskToken(g_task_token_generator.GetNext());
}

TaskToken TaskToken::GetForCurrentThread() {
  const TaskToken* current_task_token = GetTlsCurrentTaskToken().Get();
  return current_task_token ? *current_task_token : TaskToken();
}

ScopedSetSequenceTokenForCurrentThread::ScopedSetSequenceTokenForCurrentThread(
    const SequenceToken& sequence_token)
    : sequence_token_(sequence_token), task_token_(TaskToken::Create()) {
  DCHECK(!GetTlsCurrentSequenceToken().Get());
  DCHECK(!GetTlsCurrentTaskToken().Get());
  GetTlsCurrentSequenceToken().Set(&sequence_token_);
  GetTlsCurrentTaskToken().Set(&task_token_);
}

ScopedSetSequenceTokenForCurrentThread::
    ~ScopedSetSequenceTokenForCurrentThread() {
  DCHECK_EQ(GetTlsCurrentSequenceToken().Get(), &sequence_token_);
  DCHECK_EQ(GetTlsCurrentTaskToken().Get(), &task_token_);
  GetTlsCurrentSequenceToken().Set(nullptr);
  GetTlsCurrentTaskToken().Set(nullptr);
}

}  // namespace base
