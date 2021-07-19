// Copyright 2018 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_ANDROID_TASK_SCHEDULER_TASK_RUNNER_ANDROID_H_
#define BASE_ANDROID_TASK_SCHEDULER_TASK_RUNNER_ANDROID_H_

#include "base/android/jni_weak_ref.h"
#include "base/single_thread_task_runner.h"

namespace base {

// GENERATED_JAVA_ENUM_PACKAGE: org.chromium.base.task
enum class TaskRunnerType { BASE, SEQUENCED, SINGLE_THREAD };

// Native implementation backing TaskRunnerImpl.java which posts java tasks onto
// a C++ TaskRunner.
class TaskRunnerAndroid {
 public:
  explicit TaskRunnerAndroid(scoped_refptr<TaskRunner> task_runner,
                             TaskRunnerType type);
  ~TaskRunnerAndroid();

  void Destroy(JNIEnv* env);

  void PostDelayedTask(JNIEnv* env,
                       const base::android::JavaRef<jobject>& task,
                       jlong delay);

  bool BelongsToCurrentThread(JNIEnv* env);

 private:
  const scoped_refptr<TaskRunner> task_runner_;
  const TaskRunnerType type_;

  DISALLOW_COPY_AND_ASSIGN(TaskRunnerAndroid);
};

}  // namespace base

#endif  // BASE_ANDROID_TASK_SCHEDULER_TASK_RUNNER_ANDROID_H_
