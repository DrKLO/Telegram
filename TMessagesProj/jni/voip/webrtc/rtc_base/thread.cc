/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/thread.h"

#if defined(WEBRTC_WIN)
#include <comdef.h>
#elif defined(WEBRTC_POSIX)
#include <time.h>
#else
#error "Either WEBRTC_WIN or WEBRTC_POSIX needs to be defined."
#endif

#if defined(WEBRTC_WIN)
// Disable warning that we don't care about:
// warning C4722: destructor never returns, potential memory leak
#pragma warning(disable : 4722)
#endif

#include <stdio.h>

#include <utility>

#include "absl/algorithm/container.h"
#include "api/sequence_checker.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/event.h"
#include "rtc_base/internal/default_socket_server.h"
#include "rtc_base/logging.h"
#include "rtc_base/null_socket_server.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"

#if defined(WEBRTC_MAC)
#include "rtc_base/system/cocoa_threading.h"

/*
 * These are forward-declarations for methods that are part of the
 * ObjC runtime. They are declared in the private header objc-internal.h.
 * These calls are what clang inserts when using @autoreleasepool in ObjC,
 * but here they are used directly in order to keep this file C++.
 * https://clang.llvm.org/docs/AutomaticReferenceCounting.html#runtime-support
 */
extern "C" {
void* objc_autoreleasePoolPush(void);
void objc_autoreleasePoolPop(void* pool);
}

namespace {
class ScopedAutoReleasePool {
 public:
  ScopedAutoReleasePool() : pool_(objc_autoreleasePoolPush()) {}
  ~ScopedAutoReleasePool() { objc_autoreleasePoolPop(pool_); }

 private:
  void* const pool_;
};
}  // namespace
#endif

namespace rtc {
namespace {

class MessageHandlerWithTask final : public MessageHandler {
 public:
  MessageHandlerWithTask() {}

  void OnMessage(Message* msg) override {
    static_cast<rtc_thread_internal::MessageLikeTask*>(msg->pdata)->Run();
    delete msg->pdata;
  }

 private:
  ~MessageHandlerWithTask() override {}

  RTC_DISALLOW_COPY_AND_ASSIGN(MessageHandlerWithTask);
};

class RTC_SCOPED_LOCKABLE MarkProcessingCritScope {
 public:
  MarkProcessingCritScope(const RecursiveCriticalSection* cs,
                          size_t* processing) RTC_EXCLUSIVE_LOCK_FUNCTION(cs)
      : cs_(cs), processing_(processing) {
    cs_->Enter();
    *processing_ += 1;
  }

  ~MarkProcessingCritScope() RTC_UNLOCK_FUNCTION() {
    *processing_ -= 1;
    cs_->Leave();
  }

 private:
  const RecursiveCriticalSection* const cs_;
  size_t* processing_;

  RTC_DISALLOW_COPY_AND_ASSIGN(MarkProcessingCritScope);
};

}  // namespace

ThreadManager* ThreadManager::Instance() {
  static ThreadManager* const thread_manager = new ThreadManager();
  return thread_manager;
}

ThreadManager::~ThreadManager() {
  // By above RTC_DEFINE_STATIC_LOCAL.
  RTC_NOTREACHED() << "ThreadManager should never be destructed.";
}

// static
void ThreadManager::Add(Thread* message_queue) {
  return Instance()->AddInternal(message_queue);
}
void ThreadManager::AddInternal(Thread* message_queue) {
  CritScope cs(&crit_);
  // Prevent changes while the list of message queues is processed.
  RTC_DCHECK_EQ(processing_, 0);
  message_queues_.push_back(message_queue);
}

// static
void ThreadManager::Remove(Thread* message_queue) {
  return Instance()->RemoveInternal(message_queue);
}
void ThreadManager::RemoveInternal(Thread* message_queue) {
  {
    CritScope cs(&crit_);
    // Prevent changes while the list of message queues is processed.
    RTC_DCHECK_EQ(processing_, 0);
    std::vector<Thread*>::iterator iter;
    iter = absl::c_find(message_queues_, message_queue);
    if (iter != message_queues_.end()) {
      message_queues_.erase(iter);
    }
#if RTC_DCHECK_IS_ON
    RemoveFromSendGraph(message_queue);
#endif
  }
}

#if RTC_DCHECK_IS_ON
void ThreadManager::RemoveFromSendGraph(Thread* thread) {
  for (auto it = send_graph_.begin(); it != send_graph_.end();) {
    if (it->first == thread) {
      it = send_graph_.erase(it);
    } else {
      it->second.erase(thread);
      ++it;
    }
  }
}

void ThreadManager::RegisterSendAndCheckForCycles(Thread* source,
                                                  Thread* target) {
  RTC_DCHECK(source);
  RTC_DCHECK(target);

  CritScope cs(&crit_);
  std::deque<Thread*> all_targets({target});
  // We check the pre-existing who-sends-to-who graph for any path from target
  // to source. This loop is guaranteed to terminate because per the send graph
  // invariant, there are no cycles in the graph.
  for (size_t i = 0; i < all_targets.size(); i++) {
    const auto& targets = send_graph_[all_targets[i]];
    all_targets.insert(all_targets.end(), targets.begin(), targets.end());
  }
  RTC_CHECK_EQ(absl::c_count(all_targets, source), 0)
      << " send loop between " << source->name() << " and " << target->name();

  // We may now insert source -> target without creating a cycle, since there
  // was no path from target to source per the prior CHECK.
  send_graph_[source].insert(target);
}
#endif

// static
void ThreadManager::Clear(MessageHandler* handler) {
  return Instance()->ClearInternal(handler);
}
void ThreadManager::ClearInternal(MessageHandler* handler) {
  // Deleted objects may cause re-entrant calls to ClearInternal. This is
  // allowed as the list of message queues does not change while queues are
  // cleared.
  MarkProcessingCritScope cs(&crit_, &processing_);
  for (Thread* queue : message_queues_) {
    queue->Clear(handler);
  }
}

// static
void ThreadManager::ProcessAllMessageQueuesForTesting() {
  return Instance()->ProcessAllMessageQueuesInternal();
}

void ThreadManager::ProcessAllMessageQueuesInternal() {
  // This works by posting a delayed message at the current time and waiting
  // for it to be dispatched on all queues, which will ensure that all messages
  // that came before it were also dispatched.
  volatile int queues_not_done = 0;

  // This class is used so that whether the posted message is processed, or the
  // message queue is simply cleared, queues_not_done gets decremented.
  class ScopedIncrement : public MessageData {
   public:
    ScopedIncrement(volatile int* value) : value_(value) {
      AtomicOps::Increment(value_);
    }
    ~ScopedIncrement() override { AtomicOps::Decrement(value_); }

   private:
    volatile int* value_;
  };

  {
    MarkProcessingCritScope cs(&crit_, &processing_);
    for (Thread* queue : message_queues_) {
      if (!queue->IsProcessingMessagesForTesting()) {
        // If the queue is not processing messages, it can
        // be ignored. If we tried to post a message to it, it would be dropped
        // or ignored.
        continue;
      }
      queue->PostDelayed(RTC_FROM_HERE, 0, nullptr, MQID_DISPOSE,
                         new ScopedIncrement(&queues_not_done));
    }
  }

  rtc::Thread* current = rtc::Thread::Current();
  // Note: One of the message queues may have been on this thread, which is
  // why we can't synchronously wait for queues_not_done to go to 0; we need
  // to process messages as well.
  while (AtomicOps::AcquireLoad(&queues_not_done) > 0) {
    if (current) {
      current->ProcessMessages(0);
    }
  }
}

// static
Thread* Thread::Current() {
  ThreadManager* manager = ThreadManager::Instance();
  Thread* thread = manager->CurrentThread();

#ifndef NO_MAIN_THREAD_WRAPPING
  // Only autowrap the thread which instantiated the ThreadManager.
  if (!thread && manager->IsMainThread()) {
    thread = new Thread(CreateDefaultSocketServer());
    thread->WrapCurrentWithThreadManager(manager, true);
  }
#endif

  return thread;
}

#if defined(WEBRTC_POSIX)
ThreadManager::ThreadManager() : main_thread_ref_(CurrentThreadRef()) {
#if defined(WEBRTC_MAC)
  InitCocoaMultiThreading();
#endif
  pthread_key_create(&key_, nullptr);
}

Thread* ThreadManager::CurrentThread() {
  return static_cast<Thread*>(pthread_getspecific(key_));
}

void ThreadManager::SetCurrentThreadInternal(Thread* thread) {
  pthread_setspecific(key_, thread);
}
#endif

#if defined(WEBRTC_WIN)
ThreadManager::ThreadManager()
    : key_(TlsAlloc()), main_thread_ref_(CurrentThreadRef()) {}

Thread* ThreadManager::CurrentThread() {
  return static_cast<Thread*>(TlsGetValue(key_));
}

void ThreadManager::SetCurrentThreadInternal(Thread* thread) {
  TlsSetValue(key_, thread);
}
#endif

void ThreadManager::SetCurrentThread(Thread* thread) {
#if RTC_DLOG_IS_ON
  if (CurrentThread() && thread) {
    RTC_DLOG(LS_ERROR) << "SetCurrentThread: Overwriting an existing value?";
  }
#endif  // RTC_DLOG_IS_ON

  if (thread) {
    thread->EnsureIsCurrentTaskQueue();
  } else {
    Thread* current = CurrentThread();
    if (current) {
      // The current thread is being cleared, e.g. as a result of
      // UnwrapCurrent() being called or when a thread is being stopped
      // (see PreRun()). This signals that the Thread instance is being detached
      // from the thread, which also means that TaskQueue::Current() must not
      // return a pointer to the Thread instance.
      current->ClearCurrentTaskQueue();
    }
  }

  SetCurrentThreadInternal(thread);
}

void rtc::ThreadManager::ChangeCurrentThreadForTest(rtc::Thread* thread) {
  SetCurrentThreadInternal(thread);
}

Thread* ThreadManager::WrapCurrentThread() {
  Thread* result = CurrentThread();
  if (nullptr == result) {
    result = new Thread(CreateDefaultSocketServer());
    result->WrapCurrentWithThreadManager(this, true);
  }
  return result;
}

void ThreadManager::UnwrapCurrentThread() {
  Thread* t = CurrentThread();
  if (t && !(t->IsOwned())) {
    t->UnwrapCurrent();
    delete t;
  }
}

bool ThreadManager::IsMainThread() {
  return IsThreadRefEqual(CurrentThreadRef(), main_thread_ref_);
}

Thread::ScopedDisallowBlockingCalls::ScopedDisallowBlockingCalls()
    : thread_(Thread::Current()),
      previous_state_(thread_->SetAllowBlockingCalls(false)) {}

Thread::ScopedDisallowBlockingCalls::~ScopedDisallowBlockingCalls() {
  RTC_DCHECK(thread_->IsCurrent());
  thread_->SetAllowBlockingCalls(previous_state_);
}

#if RTC_DCHECK_IS_ON
Thread::ScopedCountBlockingCalls::ScopedCountBlockingCalls(
    std::function<void(uint32_t, uint32_t)> callback)
    : thread_(Thread::Current()),
      base_blocking_call_count_(thread_->GetBlockingCallCount()),
      base_could_be_blocking_call_count_(
          thread_->GetCouldBeBlockingCallCount()),
      result_callback_(std::move(callback)) {}

Thread::ScopedCountBlockingCalls::~ScopedCountBlockingCalls() {
  if (GetTotalBlockedCallCount() >= min_blocking_calls_for_callback_) {
    result_callback_(GetBlockingCallCount(), GetCouldBeBlockingCallCount());
  }
}

uint32_t Thread::ScopedCountBlockingCalls::GetBlockingCallCount() const {
  return thread_->GetBlockingCallCount() - base_blocking_call_count_;
}

uint32_t Thread::ScopedCountBlockingCalls::GetCouldBeBlockingCallCount() const {
  return thread_->GetCouldBeBlockingCallCount() -
         base_could_be_blocking_call_count_;
}

uint32_t Thread::ScopedCountBlockingCalls::GetTotalBlockedCallCount() const {
  return GetBlockingCallCount() + GetCouldBeBlockingCallCount();
}
#endif

Thread::Thread(SocketServer* ss) : Thread(ss, /*do_init=*/true) {}

Thread::Thread(std::unique_ptr<SocketServer> ss)
    : Thread(std::move(ss), /*do_init=*/true) {}

Thread::Thread(SocketServer* ss, bool do_init)
    : fPeekKeep_(false),
      delayed_next_num_(0),
      fInitialized_(false),
      fDestroyed_(false),
      stop_(0),
      ss_(ss) {
  RTC_DCHECK(ss);
  ss_->SetMessageQueue(this);
  SetName("Thread", this);  // default name
  if (do_init) {
    DoInit();
  }
}

Thread::Thread(std::unique_ptr<SocketServer> ss, bool do_init)
    : Thread(ss.get(), do_init) {
  own_ss_ = std::move(ss);
}

Thread::~Thread() {
  Stop();
  DoDestroy();
}

void Thread::DoInit() {
  if (fInitialized_) {
    return;
  }

  fInitialized_ = true;
  ThreadManager::Add(this);
}

void Thread::DoDestroy() {
  if (fDestroyed_) {
    return;
  }

  fDestroyed_ = true;
  // The signal is done from here to ensure
  // that it always gets called when the queue
  // is going away.
  if (ss_) {
    ss_->SetMessageQueue(nullptr);
  }
  ThreadManager::Remove(this);
  ClearInternal(nullptr, MQID_ANY, nullptr);
}

SocketServer* Thread::socketserver() {
  return ss_;
}

void Thread::WakeUpSocketServer() {
  ss_->WakeUp();
}

void Thread::Quit() {
  AtomicOps::ReleaseStore(&stop_, 1);
  WakeUpSocketServer();
}

bool Thread::IsQuitting() {
  return AtomicOps::AcquireLoad(&stop_) != 0;
}

void Thread::Restart() {
  AtomicOps::ReleaseStore(&stop_, 0);
}

bool Thread::Peek(Message* pmsg, int cmsWait) {
  if (fPeekKeep_) {
    *pmsg = msgPeek_;
    return true;
  }
  if (!Get(pmsg, cmsWait))
    return false;
  msgPeek_ = *pmsg;
  fPeekKeep_ = true;
  return true;
}

bool Thread::Get(Message* pmsg, int cmsWait, bool process_io) {
  // Return and clear peek if present
  // Always return the peek if it exists so there is Peek/Get symmetry

  if (fPeekKeep_) {
    *pmsg = msgPeek_;
    fPeekKeep_ = false;
    return true;
  }

  // Get w/wait + timer scan / dispatch + socket / event multiplexer dispatch

  int64_t cmsTotal = cmsWait;
  int64_t cmsElapsed = 0;
  int64_t msStart = TimeMillis();
  int64_t msCurrent = msStart;
  while (true) {
    // Check for posted events
    int64_t cmsDelayNext = kForever;
    bool first_pass = true;
    while (true) {
      // All queue operations need to be locked, but nothing else in this loop
      // (specifically handling disposed message) can happen inside the crit.
      // Otherwise, disposed MessageHandlers will cause deadlocks.
      {
        CritScope cs(&crit_);
        // On the first pass, check for delayed messages that have been
        // triggered and calculate the next trigger time.
        if (first_pass) {
          first_pass = false;
          while (!delayed_messages_.empty()) {
            if (msCurrent < delayed_messages_.top().run_time_ms_) {
              cmsDelayNext =
                  TimeDiff(delayed_messages_.top().run_time_ms_, msCurrent);
              break;
            }
            messages_.push_back(delayed_messages_.top().msg_);
            delayed_messages_.pop();
          }
        }
        // Pull a message off the message queue, if available.
        if (messages_.empty()) {
          break;
        } else {
          *pmsg = messages_.front();
          messages_.pop_front();
        }
      }  // crit_ is released here.

      // If this was a dispose message, delete it and skip it.
      if (MQID_DISPOSE == pmsg->message_id) {
        RTC_DCHECK(nullptr == pmsg->phandler);
        delete pmsg->pdata;
        *pmsg = Message();
        continue;
      }
      return true;
    }

    if (IsQuitting())
      break;

    // Which is shorter, the delay wait or the asked wait?

    int64_t cmsNext;
    if (cmsWait == kForever) {
      cmsNext = cmsDelayNext;
    } else {
      cmsNext = std::max<int64_t>(0, cmsTotal - cmsElapsed);
      if ((cmsDelayNext != kForever) && (cmsDelayNext < cmsNext))
        cmsNext = cmsDelayNext;
    }

    {
      // Wait and multiplex in the meantime
      if (!ss_->Wait(static_cast<int>(cmsNext), process_io))
        return false;
    }

    // If the specified timeout expired, return

    msCurrent = TimeMillis();
    cmsElapsed = TimeDiff(msCurrent, msStart);
    if (cmsWait != kForever) {
      if (cmsElapsed >= cmsWait)
        return false;
    }
  }
  return false;
}

void Thread::Post(const Location& posted_from,
                  MessageHandler* phandler,
                  uint32_t id,
                  MessageData* pdata,
                  bool time_sensitive) {
  RTC_DCHECK(!time_sensitive);
  if (IsQuitting()) {
    delete pdata;
    return;
  }

  // Keep thread safe
  // Add the message to the end of the queue
  // Signal for the multiplexer to return

  {
    CritScope cs(&crit_);
    Message msg;
    msg.posted_from = posted_from;
    msg.phandler = phandler;
    msg.message_id = id;
    msg.pdata = pdata;
    messages_.push_back(msg);
  }
  WakeUpSocketServer();
}

void Thread::PostDelayed(const Location& posted_from,
                         int delay_ms,
                         MessageHandler* phandler,
                         uint32_t id,
                         MessageData* pdata) {
  return DoDelayPost(posted_from, delay_ms, TimeAfter(delay_ms), phandler, id,
                     pdata);
}

void Thread::PostAt(const Location& posted_from,
                    int64_t run_at_ms,
                    MessageHandler* phandler,
                    uint32_t id,
                    MessageData* pdata) {
  return DoDelayPost(posted_from, TimeUntil(run_at_ms), run_at_ms, phandler, id,
                     pdata);
}

void Thread::DoDelayPost(const Location& posted_from,
                         int64_t delay_ms,
                         int64_t run_at_ms,
                         MessageHandler* phandler,
                         uint32_t id,
                         MessageData* pdata) {
  if (IsQuitting()) {
    delete pdata;
    return;
  }

  // Keep thread safe
  // Add to the priority queue. Gets sorted soonest first.
  // Signal for the multiplexer to return.

  {
    CritScope cs(&crit_);
    Message msg;
    msg.posted_from = posted_from;
    msg.phandler = phandler;
    msg.message_id = id;
    msg.pdata = pdata;
    DelayedMessage delayed(delay_ms, run_at_ms, delayed_next_num_, msg);
    delayed_messages_.push(delayed);
    // If this message queue processes 1 message every millisecond for 50 days,
    // we will wrap this number.  Even then, only messages with identical times
    // will be misordered, and then only briefly.  This is probably ok.
    ++delayed_next_num_;
    RTC_DCHECK_NE(0, delayed_next_num_);
  }
  WakeUpSocketServer();
}

int Thread::GetDelay() {
  CritScope cs(&crit_);

  if (!messages_.empty())
    return 0;

  if (!delayed_messages_.empty()) {
    int delay = TimeUntil(delayed_messages_.top().run_time_ms_);
    if (delay < 0)
      delay = 0;
    return delay;
  }

  return kForever;
}

void Thread::ClearInternal(MessageHandler* phandler,
                           uint32_t id,
                           MessageList* removed) {
  // Remove messages with phandler

  if (fPeekKeep_ && msgPeek_.Match(phandler, id)) {
    if (removed) {
      removed->push_back(msgPeek_);
    } else {
      delete msgPeek_.pdata;
    }
    fPeekKeep_ = false;
  }

  // Remove from ordered message queue

  for (auto it = messages_.begin(); it != messages_.end();) {
    if (it->Match(phandler, id)) {
      if (removed) {
        removed->push_back(*it);
      } else {
        delete it->pdata;
      }
      it = messages_.erase(it);
    } else {
      ++it;
    }
  }

  // Remove from priority queue. Not directly iterable, so use this approach

  auto new_end = delayed_messages_.container().begin();
  for (auto it = new_end; it != delayed_messages_.container().end(); ++it) {
    if (it->msg_.Match(phandler, id)) {
      if (removed) {
        removed->push_back(it->msg_);
      } else {
        delete it->msg_.pdata;
      }
    } else {
      *new_end++ = *it;
    }
  }
  delayed_messages_.container().erase(new_end,
                                      delayed_messages_.container().end());
  delayed_messages_.reheap();
}

void Thread::Dispatch(Message* pmsg) {
  TRACE_EVENT2("webrtc", "Thread::Dispatch", "src_file",
               pmsg->posted_from.file_name(), "src_func",
               pmsg->posted_from.function_name());
  RTC_DCHECK_RUN_ON(this);
  int64_t start_time = TimeMillis();
  pmsg->phandler->OnMessage(pmsg);
  int64_t end_time = TimeMillis();
  int64_t diff = TimeDiff(end_time, start_time);
  if (diff >= dispatch_warning_ms_) {
    RTC_LOG(LS_INFO) << "Message to " << name() << " took " << diff
                     << "ms to dispatch. Posted from: "
                     << pmsg->posted_from.ToString();
    // To avoid log spew, move the warning limit to only give warning
    // for delays that are larger than the one observed.
    dispatch_warning_ms_ = diff + 1;
  }
}

bool Thread::IsCurrent() const {
  return ThreadManager::Instance()->CurrentThread() == this;
}

std::unique_ptr<Thread> Thread::CreateWithSocketServer() {
  return std::unique_ptr<Thread>(new Thread(CreateDefaultSocketServer()));
}

std::unique_ptr<Thread> Thread::Create() {
  return std::unique_ptr<Thread>(
      new Thread(std::unique_ptr<SocketServer>(new NullSocketServer())));
}

bool Thread::SleepMs(int milliseconds) {
  AssertBlockingIsAllowedOnCurrentThread();

#if defined(WEBRTC_WIN)
  ::Sleep(milliseconds);
  return true;
#else
  // POSIX has both a usleep() and a nanosleep(), but the former is deprecated,
  // so we use nanosleep() even though it has greater precision than necessary.
  struct timespec ts;
  ts.tv_sec = milliseconds / 1000;
  ts.tv_nsec = (milliseconds % 1000) * 1000000;
  int ret = nanosleep(&ts, nullptr);
  if (ret != 0) {
    RTC_LOG_ERR(LS_WARNING) << "nanosleep() returning early";
    return false;
  }
  return true;
#endif
}

bool Thread::SetName(const std::string& name, const void* obj) {
  RTC_DCHECK(!IsRunning());

  name_ = name;
  if (obj) {
    // The %p specifier typically produce at most 16 hex digits, possibly with a
    // 0x prefix. But format is implementation defined, so add some margin.
    char buf[30];
    snprintf(buf, sizeof(buf), " 0x%p", obj);
    name_ += buf;
  }
  return true;
}

void Thread::SetDispatchWarningMs(int deadline) {
  if (!IsCurrent()) {
    PostTask(webrtc::ToQueuedTask(
        [this, deadline]() { SetDispatchWarningMs(deadline); }));
    return;
  }
  RTC_DCHECK_RUN_ON(this);
  dispatch_warning_ms_ = deadline;
}

bool Thread::Start() {
  RTC_DCHECK(!IsRunning());

  if (IsRunning())
    return false;

  Restart();  // reset IsQuitting() if the thread is being restarted

  // Make sure that ThreadManager is created on the main thread before
  // we start a new thread.
  ThreadManager::Instance();

  owned_ = true;

#if defined(WEBRTC_WIN)
  thread_ = CreateThread(nullptr, 0, PreRun, this, 0, &thread_id_);
  if (!thread_) {
    return false;
  }
#elif defined(WEBRTC_POSIX)
  pthread_attr_t attr;
  pthread_attr_init(&attr);

  int error_code = pthread_create(&thread_, &attr, PreRun, this);
  if (0 != error_code) {
    RTC_LOG(LS_ERROR) << "Unable to create pthread, error " << error_code;
    thread_ = 0;
    return false;
  }
  RTC_DCHECK(thread_);
#endif
  return true;
}

bool Thread::WrapCurrent() {
  return WrapCurrentWithThreadManager(ThreadManager::Instance(), true);
}

void Thread::UnwrapCurrent() {
  // Clears the platform-specific thread-specific storage.
  ThreadManager::Instance()->SetCurrentThread(nullptr);
#if defined(WEBRTC_WIN)
  if (thread_ != nullptr) {
    if (!CloseHandle(thread_)) {
      RTC_LOG_GLE(LS_ERROR)
          << "When unwrapping thread, failed to close handle.";
    }
    thread_ = nullptr;
    thread_id_ = 0;
  }
#elif defined(WEBRTC_POSIX)
  thread_ = 0;
#endif
}

void Thread::SafeWrapCurrent() {
  WrapCurrentWithThreadManager(ThreadManager::Instance(), false);
}

void Thread::Join() {
  if (!IsRunning())
    return;

  RTC_DCHECK(!IsCurrent());
  if (Current() && !Current()->blocking_calls_allowed_) {
    RTC_LOG(LS_WARNING) << "Waiting for the thread to join, "
                           "but blocking calls have been disallowed";
  }

#if defined(WEBRTC_WIN)
  RTC_DCHECK(thread_ != nullptr);
  WaitForSingleObject(thread_, INFINITE);
  CloseHandle(thread_);
  thread_ = nullptr;
  thread_id_ = 0;
#elif defined(WEBRTC_POSIX)
  pthread_join(thread_, nullptr);
  thread_ = 0;
#endif
}

bool Thread::SetAllowBlockingCalls(bool allow) {
  RTC_DCHECK(IsCurrent());
  bool previous = blocking_calls_allowed_;
  blocking_calls_allowed_ = allow;
  return previous;
}

// static
void Thread::AssertBlockingIsAllowedOnCurrentThread() {
#if !defined(NDEBUG)
  Thread* current = Thread::Current();
  RTC_DCHECK(!current || current->blocking_calls_allowed_);
#endif
}

// static
#if defined(WEBRTC_WIN)
DWORD WINAPI Thread::PreRun(LPVOID pv) {
#else
void* Thread::PreRun(void* pv) {
#endif
  Thread* thread = static_cast<Thread*>(pv);
  ThreadManager::Instance()->SetCurrentThread(thread);
  rtc::SetCurrentThreadName(thread->name_.c_str());
#if defined(WEBRTC_MAC)
  ScopedAutoReleasePool pool;
#endif
  thread->Run();

  ThreadManager::Instance()->SetCurrentThread(nullptr);
#ifdef WEBRTC_WIN
  return 0;
#else
  return nullptr;
#endif
}  // namespace rtc

void Thread::Run() {
  ProcessMessages(kForever);
}

bool Thread::IsOwned() {
  RTC_DCHECK(IsRunning());
  return owned_;
}

void Thread::Stop() {
  Thread::Quit();
  Join();
}

void Thread::Send(const Location& posted_from,
                  MessageHandler* phandler,
                  uint32_t id,
                  MessageData* pdata) {
  RTC_DCHECK(!IsQuitting());
  if (IsQuitting())
    return;

  // Sent messages are sent to the MessageHandler directly, in the context
  // of "thread", like Win32 SendMessage. If in the right context,
  // call the handler directly.
  Message msg;
  msg.posted_from = posted_from;
  msg.phandler = phandler;
  msg.message_id = id;
  msg.pdata = pdata;
  if (IsCurrent()) {
#if RTC_DCHECK_IS_ON
    RTC_DCHECK_RUN_ON(this);
    could_be_blocking_call_count_++;
#endif
    msg.phandler->OnMessage(&msg);
    return;
  }

  AssertBlockingIsAllowedOnCurrentThread();

  Thread* current_thread = Thread::Current();

#if RTC_DCHECK_IS_ON
  if (current_thread) {
    RTC_DCHECK_RUN_ON(current_thread);
    current_thread->blocking_call_count_++;
    RTC_DCHECK(current_thread->IsInvokeToThreadAllowed(this));
    ThreadManager::Instance()->RegisterSendAndCheckForCycles(current_thread,
                                                             this);
  }
#endif

  // Perhaps down the line we can get rid of this workaround and always require
  // current_thread to be valid when Send() is called.
  std::unique_ptr<rtc::Event> done_event;
  if (!current_thread)
    done_event.reset(new rtc::Event());

  bool ready = false;
  PostTask(webrtc::ToQueuedTask(
      [&msg]() mutable { msg.phandler->OnMessage(&msg); },
      [this, &ready, current_thread, done = done_event.get()] {
        if (current_thread) {
          CritScope cs(&crit_);
          ready = true;
          current_thread->socketserver()->WakeUp();
        } else {
          done->Set();
        }
      }));

  if (current_thread) {
    bool waited = false;
    crit_.Enter();
    while (!ready) {
      crit_.Leave();
      current_thread->socketserver()->Wait(kForever, false);
      waited = true;
      crit_.Enter();
    }
    crit_.Leave();

    // Our Wait loop above may have consumed some WakeUp events for this
    // Thread, that weren't relevant to this Send.  Losing these WakeUps can
    // cause problems for some SocketServers.
    //
    // Concrete example:
    // Win32SocketServer on thread A calls Send on thread B.  While processing
    // the message, thread B Posts a message to A.  We consume the wakeup for
    // that Post while waiting for the Send to complete, which means that when
    // we exit this loop, we need to issue another WakeUp, or else the Posted
    // message won't be processed in a timely manner.

    if (waited) {
      current_thread->socketserver()->WakeUp();
    }
  } else {
    done_event->Wait(rtc::Event::kForever);
  }
}

void Thread::InvokeInternal(const Location& posted_from,
                            rtc::FunctionView<void()> functor) {
  TRACE_EVENT2("webrtc", "Thread::Invoke", "src_file", posted_from.file_name(),
               "src_func", posted_from.function_name());

  class FunctorMessageHandler : public MessageHandler {
   public:
    explicit FunctorMessageHandler(rtc::FunctionView<void()> functor)
        : functor_(functor) {}
    void OnMessage(Message* msg) override { functor_(); }

   private:
    rtc::FunctionView<void()> functor_;
  } handler(functor);

  Send(posted_from, &handler);
}

// Called by the ThreadManager when being set as the current thread.
void Thread::EnsureIsCurrentTaskQueue() {
  task_queue_registration_ =
      std::make_unique<TaskQueueBase::CurrentTaskQueueSetter>(this);
}

// Called by the ThreadManager when being set as the current thread.
void Thread::ClearCurrentTaskQueue() {
  task_queue_registration_.reset();
}

void Thread::QueuedTaskHandler::OnMessage(Message* msg) {
  RTC_DCHECK(msg);
  auto* data = static_cast<ScopedMessageData<webrtc::QueuedTask>*>(msg->pdata);
  std::unique_ptr<webrtc::QueuedTask> task = std::move(data->data());
  // Thread expects handler to own Message::pdata when OnMessage is called
  // Since MessageData is no longer needed, delete it.
  delete data;

  // QueuedTask interface uses Run return value to communicate who owns the
  // task. false means QueuedTask took the ownership.
  if (!task->Run())
    task.release();
}

void Thread::AllowInvokesToThread(Thread* thread) {
#if (!defined(NDEBUG) || defined(DCHECK_ALWAYS_ON))
  if (!IsCurrent()) {
    PostTask(webrtc::ToQueuedTask(
        [thread, this]() { AllowInvokesToThread(thread); }));
    return;
  }
  RTC_DCHECK_RUN_ON(this);
  allowed_threads_.push_back(thread);
  invoke_policy_enabled_ = true;
#endif
}

void Thread::DisallowAllInvokes() {
#if (!defined(NDEBUG) || defined(DCHECK_ALWAYS_ON))
  if (!IsCurrent()) {
    PostTask(webrtc::ToQueuedTask([this]() { DisallowAllInvokes(); }));
    return;
  }
  RTC_DCHECK_RUN_ON(this);
  allowed_threads_.clear();
  invoke_policy_enabled_ = true;
#endif
}

#if RTC_DCHECK_IS_ON
uint32_t Thread::GetBlockingCallCount() const {
  RTC_DCHECK_RUN_ON(this);
  return blocking_call_count_;
}
uint32_t Thread::GetCouldBeBlockingCallCount() const {
  RTC_DCHECK_RUN_ON(this);
  return could_be_blocking_call_count_;
}
#endif

// Returns true if no policies added or if there is at least one policy
// that permits invocation to |target| thread.
bool Thread::IsInvokeToThreadAllowed(rtc::Thread* target) {
#if (!defined(NDEBUG) || defined(DCHECK_ALWAYS_ON))
  RTC_DCHECK_RUN_ON(this);
  if (!invoke_policy_enabled_) {
    return true;
  }
  for (const auto* thread : allowed_threads_) {
    if (thread == target) {
      return true;
    }
  }
  return false;
#else
  return true;
#endif
}

void Thread::PostTask(std::unique_ptr<webrtc::QueuedTask> task) {
  // Though Post takes MessageData by raw pointer (last parameter), it still
  // takes it with ownership.
  Post(RTC_FROM_HERE, &queued_task_handler_,
       /*id=*/0, new ScopedMessageData<webrtc::QueuedTask>(std::move(task)));
}

void Thread::PostDelayedTask(std::unique_ptr<webrtc::QueuedTask> task,
                             uint32_t milliseconds) {
  // Though PostDelayed takes MessageData by raw pointer (last parameter),
  // it still takes it with ownership.
  PostDelayed(RTC_FROM_HERE, milliseconds, &queued_task_handler_,
              /*id=*/0,
              new ScopedMessageData<webrtc::QueuedTask>(std::move(task)));
}

void Thread::Delete() {
  Stop();
  delete this;
}

bool Thread::IsProcessingMessagesForTesting() {
  return (owned_ || IsCurrent()) && !IsQuitting();
}

void Thread::Clear(MessageHandler* phandler,
                   uint32_t id,
                   MessageList* removed) {
  CritScope cs(&crit_);
  ClearInternal(phandler, id, removed);
}

bool Thread::ProcessMessages(int cmsLoop) {
  // Using ProcessMessages with a custom clock for testing and a time greater
  // than 0 doesn't work, since it's not guaranteed to advance the custom
  // clock's time, and may get stuck in an infinite loop.
  RTC_DCHECK(GetClockForTesting() == nullptr || cmsLoop == 0 ||
             cmsLoop == kForever);
  int64_t msEnd = (kForever == cmsLoop) ? 0 : TimeAfter(cmsLoop);
  int cmsNext = cmsLoop;

  while (true) {
#if defined(WEBRTC_MAC)
    ScopedAutoReleasePool pool;
#endif
    Message msg;
    if (!Get(&msg, cmsNext))
      return !IsQuitting();
    Dispatch(&msg);

    if (cmsLoop != kForever) {
      cmsNext = static_cast<int>(TimeUntil(msEnd));
      if (cmsNext < 0)
        return true;
    }
  }
}

bool Thread::WrapCurrentWithThreadManager(ThreadManager* thread_manager,
                                          bool need_synchronize_access) {
  RTC_DCHECK(!IsRunning());

#if defined(WEBRTC_WIN)
  if (need_synchronize_access) {
    // We explicitly ask for no rights other than synchronization.
    // This gives us the best chance of succeeding.
    thread_ = OpenThread(SYNCHRONIZE, FALSE, GetCurrentThreadId());
    if (!thread_) {
      RTC_LOG_GLE(LS_ERROR) << "Unable to get handle to thread.";
      return false;
    }
    thread_id_ = GetCurrentThreadId();
  }
#elif defined(WEBRTC_POSIX)
  thread_ = pthread_self();
#endif
  owned_ = false;
  thread_manager->SetCurrentThread(this);
  return true;
}

bool Thread::IsRunning() {
#if defined(WEBRTC_WIN)
  return thread_ != nullptr;
#elif defined(WEBRTC_POSIX)
  return thread_ != 0;
#endif
}

// static
MessageHandler* Thread::GetPostTaskMessageHandler() {
  // Allocate at first call, never deallocate.
  static MessageHandler* handler = new MessageHandlerWithTask;
  return handler;
}

AutoThread::AutoThread()
    : Thread(CreateDefaultSocketServer(), /*do_init=*/false) {
  if (!ThreadManager::Instance()->CurrentThread()) {
    // DoInit registers with ThreadManager. Do that only if we intend to
    // be rtc::Thread::Current(), otherwise ProcessAllMessageQueuesInternal will
    // post a message to a queue that no running thread is serving.
    DoInit();
    ThreadManager::Instance()->SetCurrentThread(this);
  }
}

AutoThread::~AutoThread() {
  Stop();
  DoDestroy();
  if (ThreadManager::Instance()->CurrentThread() == this) {
    ThreadManager::Instance()->SetCurrentThread(nullptr);
  }
}

AutoSocketServerThread::AutoSocketServerThread(SocketServer* ss)
    : Thread(ss, /*do_init=*/false) {
  DoInit();
  old_thread_ = ThreadManager::Instance()->CurrentThread();
  // Temporarily set the current thread to nullptr so that we can keep checks
  // around that catch unintentional pointer overwrites.
  rtc::ThreadManager::Instance()->SetCurrentThread(nullptr);
  rtc::ThreadManager::Instance()->SetCurrentThread(this);
  if (old_thread_) {
    ThreadManager::Remove(old_thread_);
  }
}

AutoSocketServerThread::~AutoSocketServerThread() {
  RTC_DCHECK(ThreadManager::Instance()->CurrentThread() == this);
  // Some tests post destroy messages to this thread. To avoid memory
  // leaks, we have to process those messages. In particular
  // P2PTransportChannelPingTest, relying on the message posted in
  // cricket::Connection::Destroy.
  ProcessMessages(0);
  // Stop and destroy the thread before clearing it as the current thread.
  // Sometimes there are messages left in the Thread that will be
  // destroyed by DoDestroy, and sometimes the destructors of the message and/or
  // its contents rely on this thread still being set as the current thread.
  Stop();
  DoDestroy();
  rtc::ThreadManager::Instance()->SetCurrentThread(nullptr);
  rtc::ThreadManager::Instance()->SetCurrentThread(old_thread_);
  if (old_thread_) {
    ThreadManager::Add(old_thread_);
  }
}

}  // namespace rtc
