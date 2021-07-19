// Copyright 2013 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/android/java_handler_thread.h"

#include <jni.h>

#include "base/android/jni_android.h"
#include "base/android/jni_string.h"
#include "base/base_jni_headers/JavaHandlerThread_jni.h"
#include "base/bind.h"
#include "base/message_loop/message_pump.h"
#include "base/message_loop/message_pump_type.h"
#include "base/run_loop.h"
#include "base/synchronization/waitable_event.h"
#include "base/task/sequence_manager/sequence_manager_impl.h"
#include "base/threading/platform_thread_internal_posix.h"
#include "base/threading/thread_id_name_manager.h"
#include "base/threading/thread_restrictions.h"

using base::android::AttachCurrentThread;

namespace base {

namespace android {

JavaHandlerThread::JavaHandlerThread(const char* name,
                                     base::ThreadPriority priority)
    : JavaHandlerThread(
          name,
          Java_JavaHandlerThread_create(
              AttachCurrentThread(),
              ConvertUTF8ToJavaString(AttachCurrentThread(), name),
              base::internal::ThreadPriorityToNiceValue(priority))) {}

JavaHandlerThread::JavaHandlerThread(
    const char* name,
    const base::android::ScopedJavaLocalRef<jobject>& obj)
    : name_(name), java_thread_(obj) {}

JavaHandlerThread::~JavaHandlerThread() {
  JNIEnv* env = base::android::AttachCurrentThread();
  DCHECK(!Java_JavaHandlerThread_isAlive(env, java_thread_));
  DCHECK(!state_ || state_->pump->IsAborted());
  // TODO(mthiesse): We shouldn't leak the MessageLoop as this could affect
  // future tests.
  if (state_ && state_->pump->IsAborted()) {
    // When the Pump has been aborted due to a crash, we intentionally leak the
    // SequenceManager because the SequenceManager hasn't been shut down
    // properly and would trigger DCHECKS. This should only happen in tests,
    // where we handle the exception instead of letting it take down the
    // process.
    state_.release();
  }
}

void JavaHandlerThread::Start() {
  // Check the thread has not already been started.
  DCHECK(!state_);

  JNIEnv* env = base::android::AttachCurrentThread();
  base::WaitableEvent initialize_event(
      WaitableEvent::ResetPolicy::AUTOMATIC,
      WaitableEvent::InitialState::NOT_SIGNALED);
  Java_JavaHandlerThread_startAndInitialize(
      env, java_thread_, reinterpret_cast<intptr_t>(this),
      reinterpret_cast<intptr_t>(&initialize_event));
  // Wait for thread to be initialized so it is ready to be used when Start
  // returns.
  base::ScopedAllowBaseSyncPrimitivesOutsideBlockingScope wait_allowed;
  initialize_event.Wait();
}

void JavaHandlerThread::Stop() {
  DCHECK(!task_runner()->BelongsToCurrentThread());
  task_runner()->PostTask(
      FROM_HERE,
      base::BindOnce(&JavaHandlerThread::StopOnThread, base::Unretained(this)));
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_JavaHandlerThread_joinThread(env, java_thread_);
}

void JavaHandlerThread::InitializeThread(JNIEnv* env,
                                         jlong event) {
  base::ThreadIdNameManager::GetInstance()->RegisterThread(
      base::PlatformThread::CurrentHandle().platform_handle(),
      base::PlatformThread::CurrentId());

  if (name_)
    PlatformThread::SetName(name_);

  state_ = std::make_unique<State>();
  Init();
  reinterpret_cast<base::WaitableEvent*>(event)->Signal();
}

void JavaHandlerThread::OnLooperStopped(JNIEnv* env) {
  DCHECK(task_runner()->BelongsToCurrentThread());
  state_.reset();

  CleanUp();

  base::ThreadIdNameManager::GetInstance()->RemoveName(
      base::PlatformThread::CurrentHandle().platform_handle(),
      base::PlatformThread::CurrentId());
}

void JavaHandlerThread::StopSequenceManagerForTesting() {
  DCHECK(task_runner()->BelongsToCurrentThread());
  StopOnThread();
}

void JavaHandlerThread::JoinForTesting() {
  DCHECK(!task_runner()->BelongsToCurrentThread());
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_JavaHandlerThread_joinThread(env, java_thread_);
}

void JavaHandlerThread::ListenForUncaughtExceptionsForTesting() {
  DCHECK(!task_runner()->BelongsToCurrentThread());
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_JavaHandlerThread_listenForUncaughtExceptionsForTesting(env,
                                                               java_thread_);
}

ScopedJavaLocalRef<jthrowable> JavaHandlerThread::GetUncaughtExceptionIfAny() {
  DCHECK(!task_runner()->BelongsToCurrentThread());
  JNIEnv* env = base::android::AttachCurrentThread();
  return Java_JavaHandlerThread_getUncaughtExceptionIfAny(env, java_thread_);
}

void JavaHandlerThread::StopOnThread() {
  DCHECK(task_runner()->BelongsToCurrentThread());
  DCHECK(state_);
  state_->pump->QuitWhenIdle(base::BindOnce(
      &JavaHandlerThread::QuitThreadSafely, base::Unretained(this)));
}

void JavaHandlerThread::QuitThreadSafely() {
  DCHECK(task_runner()->BelongsToCurrentThread());
  JNIEnv* env = base::android::AttachCurrentThread();
  Java_JavaHandlerThread_quitThreadSafely(env, java_thread_,
                                          reinterpret_cast<intptr_t>(this));
}

JavaHandlerThread::State::State()
    : sequence_manager(sequence_manager::CreateUnboundSequenceManager(
          sequence_manager::SequenceManager::Settings::Builder()
              .SetMessagePumpType(base::MessagePumpType::JAVA)
              .Build())),
      default_task_queue(sequence_manager->CreateTaskQueue(
          sequence_manager::TaskQueue::Spec("default_tq"))) {
  // TYPE_JAVA to get the Android java style message loop.
  std::unique_ptr<MessagePump> message_pump =
      MessagePump::Create(base::MessagePumpType::JAVA);
  pump = static_cast<MessagePumpForUI*>(message_pump.get());

  // We must set SetTaskRunner before binding because the Android UI pump
  // creates a RunLoop which samples ThreadTaskRunnerHandle::Get.
  static_cast<sequence_manager::internal::SequenceManagerImpl*>(
      sequence_manager.get())
      ->SetTaskRunner(default_task_queue->task_runner());
  sequence_manager->BindToMessagePump(std::move(message_pump));
}

JavaHandlerThread::State::~State() = default;

} // namespace android
} // namespace base
