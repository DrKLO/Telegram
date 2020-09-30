// Copyright 2019 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/task/single_thread_task_executor.h"

#include "base/message_loop/message_pump.h"
#include "base/message_loop/message_pump_type.h"
#include "base/task/sequence_manager/sequence_manager.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "build/build_config.h"

namespace base {

SingleThreadTaskExecutor::SingleThreadTaskExecutor(MessagePumpType type)
    : SingleThreadTaskExecutor(type, MessagePump::Create(type)) {
  DCHECK_NE(type, MessagePumpType::CUSTOM);
}

SingleThreadTaskExecutor::SingleThreadTaskExecutor(
    std::unique_ptr<MessagePump> pump)
    : SingleThreadTaskExecutor(MessagePumpType::CUSTOM, std::move(pump)) {}

SingleThreadTaskExecutor::SingleThreadTaskExecutor(
    MessagePumpType type,
    std::unique_ptr<MessagePump> pump)
    : sequence_manager_(sequence_manager::CreateUnboundSequenceManager(
          sequence_manager::SequenceManager::Settings::Builder()
              .SetMessagePumpType(type)
              .Build())),
      default_task_queue_(sequence_manager_->CreateTaskQueue(
          sequence_manager::TaskQueue::Spec("default_tq"))),
      type_(type),
      simple_task_executor_(task_runner()) {
  sequence_manager_->SetDefaultTaskRunner(default_task_queue_->task_runner());
  sequence_manager_->BindToMessagePump(std::move(pump));
}

SingleThreadTaskExecutor::~SingleThreadTaskExecutor() = default;

scoped_refptr<SingleThreadTaskRunner> SingleThreadTaskExecutor::task_runner()
    const {
  return default_task_queue_->task_runner();
}

void SingleThreadTaskExecutor::SetWorkBatchSize(size_t work_batch_size) {
  sequence_manager_->SetWorkBatchSize(work_batch_size);
}

}  // namespace base
