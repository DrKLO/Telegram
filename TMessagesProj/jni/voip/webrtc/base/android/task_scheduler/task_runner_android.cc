// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/task_scheduler/task_runner_android.h"

#include "base/android/task_scheduler/post_task_android.h"
#include "base/base_jni_headers/TaskRunnerImpl_jni.h"
#include "base/bind.h"
#include "base/run_loop.h"
#include "base/task/post_task.h"
#include "base/time/time.h"

namespace base {

jlong JNI_TaskRunnerImpl_Init(
    JNIEnv* env,
    jint task_runner_type,
    jint priority,
    jboolean may_block,
    jboolean thread_pool,
    jbyte extension_id,
    const base::android::JavaParamRef<jbyteArray>& extension_data) {
  TaskTraits task_traits = PostTaskAndroid::CreateTaskTraits(
      env, priority, may_block, thread_pool, extension_id, extension_data);
  scoped_refptr<TaskRunner> task_runner;
  switch (static_cast<TaskRunnerType>(task_runner_type)) {
    case TaskRunnerType::BASE:
      task_runner = CreateTaskRunner(task_traits);
      break;
    case TaskRunnerType::SEQUENCED:
      task_runner = CreateSequencedTaskRunner(task_traits);
      break;
    case TaskRunnerType::SINGLE_THREAD:
      task_runner = CreateSingleThreadTaskRunner(task_traits);
      break;
  }
  return reinterpret_cast<intptr_t>(new TaskRunnerAndroid(
      task_runner, static_cast<TaskRunnerType>(task_runner_type)));
}

TaskRunnerAndroid::TaskRunnerAndroid(scoped_refptr<TaskRunner> task_runner,
                                     TaskRunnerType type)
    : task_runner_(std::move(task_runner)), type_(type) {}

TaskRunnerAndroid::~TaskRunnerAndroid() = default;

void TaskRunnerAndroid::Destroy(JNIEnv* env) {
  // This could happen on any thread.
  delete this;
}

void TaskRunnerAndroid::PostDelayedTask(
    JNIEnv* env,
    const base::android::JavaRef<jobject>& task,
    jlong delay) {
  task_runner_->PostDelayedTask(
      FROM_HERE,
      base::BindOnce(&PostTaskAndroid::RunJavaTask,
                     base::android::ScopedJavaGlobalRef<jobject>(task)),
      TimeDelta::FromMilliseconds(delay));
}

bool TaskRunnerAndroid::BelongsToCurrentThread(JNIEnv* env) {
  // TODO(crbug.com/1026641): Move BelongsToCurrentThread from TaskRunnerImpl to
  // SequencedTaskRunnerImpl on the Java side too.
  if (type_ == TaskRunnerType::BASE)
    return false;
  return static_cast<SequencedTaskRunner*>(task_runner_.get())
      ->RunsTasksInCurrentSequence();
}

}  // namespace base
