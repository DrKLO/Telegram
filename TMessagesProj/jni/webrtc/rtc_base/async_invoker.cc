/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_invoker.h"

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace rtc {

AsyncInvoker::AsyncInvoker()
    : pending_invocations_(0),
      invocation_complete_(new RefCountedObject<Event>()),
      destroying_(false) {}

AsyncInvoker::~AsyncInvoker() {
  destroying_.store(true, std::memory_order_relaxed);
  // Messages for this need to be cleared *before* our destructor is complete.
  ThreadManager::Clear(this);
  // And we need to wait for any invocations that are still in progress on
  // other threads. Using memory_order_acquire for synchronization with
  // AsyncClosure destructors.
  while (pending_invocations_.load(std::memory_order_acquire) > 0) {
    // If the destructor was called while AsyncInvoke was being called by
    // another thread, WITHIN an AsyncInvoked functor, it may do another
    // Thread::Post even after we called ThreadManager::Clear(this). So
    // we need to keep calling Clear to discard these posts.
    Thread::Current()->Clear(this);
    invocation_complete_->Wait(Event::kForever);
  }
}

void AsyncInvoker::OnMessage(Message* msg) {
  // Get the AsyncClosure shared ptr from this message's data.
  ScopedMessageData<AsyncClosure>* data =
      static_cast<ScopedMessageData<AsyncClosure>*>(msg->pdata);
  // Execute the closure and trigger the return message if needed.
  data->inner_data().Execute();
  delete data;
}

void AsyncInvoker::Flush(Thread* thread, uint32_t id /*= MQID_ANY*/) {
  // If the destructor is waiting for invocations to finish, don't start
  // running even more tasks.
  if (destroying_.load(std::memory_order_relaxed))
    return;

  // Run this on |thread| to reduce the number of context switches.
  if (Thread::Current() != thread) {
    thread->Invoke<void>(RTC_FROM_HERE,
                         Bind(&AsyncInvoker::Flush, this, thread, id));
    return;
  }

  MessageList removed;
  thread->Clear(this, id, &removed);
  for (MessageList::iterator it = removed.begin(); it != removed.end(); ++it) {
    // This message was pending on this thread, so run it now.
    thread->Send(it->posted_from, it->phandler, it->message_id, it->pdata);
  }
}

void AsyncInvoker::Clear() {
  ThreadManager::Clear(this);
}

void AsyncInvoker::DoInvoke(const Location& posted_from,
                            Thread* thread,
                            std::unique_ptr<AsyncClosure> closure,
                            uint32_t id) {
  if (destroying_.load(std::memory_order_relaxed)) {
    // Note that this may be expected, if the application is AsyncInvoking
    // tasks that AsyncInvoke other tasks. But otherwise it indicates a race
    // between a thread destroying the AsyncInvoker and a thread still trying
    // to use it.
    RTC_LOG(LS_WARNING) << "Tried to invoke while destroying the invoker.";
    return;
  }
  thread->Post(posted_from, this, id,
               new ScopedMessageData<AsyncClosure>(std::move(closure)));
}

void AsyncInvoker::DoInvokeDelayed(const Location& posted_from,
                                   Thread* thread,
                                   std::unique_ptr<AsyncClosure> closure,
                                   uint32_t delay_ms,
                                   uint32_t id) {
  if (destroying_.load(std::memory_order_relaxed)) {
    // See above comment.
    RTC_LOG(LS_WARNING) << "Tried to invoke while destroying the invoker.";
    return;
  }
  thread->PostDelayed(posted_from, delay_ms, this, id,
                      new ScopedMessageData<AsyncClosure>(std::move(closure)));
}

AsyncClosure::AsyncClosure(AsyncInvoker* invoker)
    : invoker_(invoker), invocation_complete_(invoker_->invocation_complete_) {
  invoker_->pending_invocations_.fetch_add(1, std::memory_order_relaxed);
}

AsyncClosure::~AsyncClosure() {
  // Using memory_order_release for synchronization with the AsyncInvoker
  // destructor.
  invoker_->pending_invocations_.fetch_sub(1, std::memory_order_release);

  // After |pending_invocations_| is decremented, we may need to signal
  // |invocation_complete_| in case the AsyncInvoker is being destroyed and
  // waiting for pending tasks to complete.
  //
  // It's also possible that the destructor finishes before "Set()" is called,
  // which is safe because the event is reference counted (and in a thread-safe
  // way).
  invocation_complete_->Set();
}

}  // namespace rtc
