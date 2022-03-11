/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_THREAD_H_
#define RTC_BASE_THREAD_H_

#include <stdint.h>

#include <list>
#include <map>
#include <memory>
#include <queue>
#include <set>
#include <string>
#include <type_traits>
#include <vector>

#if defined(WEBRTC_POSIX)
#include <pthread.h>
#endif
#include "absl/base/attributes.h"
#include "api/function_view.h"
#include "api/task_queue/queued_task.h"
#include "api/task_queue/task_queue_base.h"
#include "rtc_base/checks.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/location.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_message.h"

#if defined(WEBRTC_WIN)
#include "rtc_base/win32.h"
#endif

#if RTC_DCHECK_IS_ON
// Counts how many blocking Thread::Invoke or Thread::Send calls are made from
// within a scope and logs the number of blocking calls at the end of the scope.
#define RTC_LOG_THREAD_BLOCK_COUNT()                                        \
  rtc::Thread::ScopedCountBlockingCalls blocked_call_count_printer(         \
      [func = __func__](uint32_t actual_block, uint32_t could_block) {      \
        auto total = actual_block + could_block;                            \
        if (total) {                                                        \
          RTC_LOG(LS_WARNING) << "Blocking " << func << ": total=" << total \
                              << " (actual=" << actual_block                \
                              << ", could=" << could_block << ")";          \
        }                                                                   \
      })

// Adds an RTC_DCHECK_LE that checks that the number of blocking calls are
// less than or equal to a specific value. Use to avoid regressing in the
// number of blocking thread calls.
// Note: Use of this macro, requires RTC_LOG_THREAD_BLOCK_COUNT() to be called
// first.
#define RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(x)                               \
  do {                                                                       \
    blocked_call_count_printer.set_minimum_call_count_for_callback(x + 1);   \
    RTC_DCHECK_LE(blocked_call_count_printer.GetTotalBlockedCallCount(), x); \
  } while (0)
#else
#define RTC_LOG_THREAD_BLOCK_COUNT()
#define RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(x)
#endif

namespace rtc {

class Thread;

namespace rtc_thread_internal {

class MessageLikeTask : public MessageData {
 public:
  virtual void Run() = 0;
};

template <class FunctorT>
class MessageWithFunctor final : public MessageLikeTask {
 public:
  explicit MessageWithFunctor(FunctorT&& functor)
      : functor_(std::forward<FunctorT>(functor)) {}

  MessageWithFunctor(const MessageWithFunctor&) = delete;
  MessageWithFunctor& operator=(const MessageWithFunctor&) = delete;

  void Run() override { functor_(); }

 private:
  ~MessageWithFunctor() override {}

  typename std::remove_reference<FunctorT>::type functor_;
};

}  // namespace rtc_thread_internal

class RTC_EXPORT ThreadManager {
 public:
  static const int kForever = -1;

  // Singleton, constructor and destructor are private.
  static ThreadManager* Instance();

  static void Add(Thread* message_queue);
  static void Remove(Thread* message_queue);
  static void Clear(MessageHandler* handler);

  // For testing purposes, for use with a simulated clock.
  // Ensures that all message queues have processed delayed messages
  // up until the current point in time.
  static void ProcessAllMessageQueuesForTesting();

  Thread* CurrentThread();
  void SetCurrentThread(Thread* thread);
  // Allows changing the current thread, this is intended for tests where we
  // want to simulate multiple threads running on a single physical thread.
  void ChangeCurrentThreadForTest(Thread* thread);

  // Returns a thread object with its thread_ ivar set
  // to whatever the OS uses to represent the thread.
  // If there already *is* a Thread object corresponding to this thread,
  // this method will return that.  Otherwise it creates a new Thread
  // object whose wrapped() method will return true, and whose
  // handle will, on Win32, be opened with only synchronization privileges -
  // if you need more privilegs, rather than changing this method, please
  // write additional code to adjust the privileges, or call a different
  // factory method of your own devising, because this one gets used in
  // unexpected contexts (like inside browser plugins) and it would be a
  // shame to break it.  It is also conceivable on Win32 that we won't even
  // be able to get synchronization privileges, in which case the result
  // will have a null handle.
  Thread* WrapCurrentThread();
  void UnwrapCurrentThread();

#if RTC_DCHECK_IS_ON
  // Registers that a Send operation is to be performed between `source` and
  // `target`, while checking that this does not cause a send cycle that could
  // potentially cause a deadlock.
  void RegisterSendAndCheckForCycles(Thread* source, Thread* target);
#endif

 private:
  ThreadManager();
  ~ThreadManager();

  ThreadManager(const ThreadManager&) = delete;
  ThreadManager& operator=(const ThreadManager&) = delete;

  void SetCurrentThreadInternal(Thread* thread);
  void AddInternal(Thread* message_queue);
  void RemoveInternal(Thread* message_queue);
  void ClearInternal(MessageHandler* handler);
  void ProcessAllMessageQueuesInternal();
#if RTC_DCHECK_IS_ON
  void RemoveFromSendGraph(Thread* thread) RTC_EXCLUSIVE_LOCKS_REQUIRED(crit_);
#endif

  // This list contains all live Threads.
  std::vector<Thread*> message_queues_ RTC_GUARDED_BY(crit_);

  // Methods that don't modify the list of message queues may be called in a
  // re-entrant fashion. "processing_" keeps track of the depth of re-entrant
  // calls.
  RecursiveCriticalSection crit_;
  size_t processing_ RTC_GUARDED_BY(crit_) = 0;
#if RTC_DCHECK_IS_ON
  // Represents all thread seand actions by storing all send targets per thread.
  // This is used by RegisterSendAndCheckForCycles. This graph has no cycles
  // since we will trigger a CHECK failure if a cycle is introduced.
  std::map<Thread*, std::set<Thread*>> send_graph_ RTC_GUARDED_BY(crit_);
#endif

#if defined(WEBRTC_POSIX)
  pthread_key_t key_;
#endif

#if defined(WEBRTC_WIN)
  const DWORD key_;
#endif
};

// WARNING! SUBCLASSES MUST CALL Stop() IN THEIR DESTRUCTORS!  See ~Thread().

class RTC_LOCKABLE RTC_EXPORT Thread : public webrtc::TaskQueueBase {
 public:
  static const int kForever = -1;

  // Create a new Thread and optionally assign it to the passed
  // SocketServer. Subclasses that override Clear should pass false for
  // init_queue and call DoInit() from their constructor to prevent races
  // with the ThreadManager using the object while the vtable is still
  // being created.
  explicit Thread(SocketServer* ss);
  explicit Thread(std::unique_ptr<SocketServer> ss);

  // Constructors meant for subclasses; they should call DoInit themselves and
  // pass false for `do_init`, so that DoInit is called only on the fully
  // instantiated class, which avoids a vptr data race.
  Thread(SocketServer* ss, bool do_init);
  Thread(std::unique_ptr<SocketServer> ss, bool do_init);

  // NOTE: ALL SUBCLASSES OF Thread MUST CALL Stop() IN THEIR DESTRUCTORS (or
  // guarantee Stop() is explicitly called before the subclass is destroyed).
  // This is required to avoid a data race between the destructor modifying the
  // vtable, and the Thread::PreRun calling the virtual method Run().

  // NOTE: SUBCLASSES OF Thread THAT OVERRIDE Clear MUST CALL
  // DoDestroy() IN THEIR DESTRUCTORS! This is required to avoid a data race
  // between the destructor modifying the vtable, and the ThreadManager
  // calling Clear on the object from a different thread.
  ~Thread() override;

  Thread(const Thread&) = delete;
  Thread& operator=(const Thread&) = delete;

  static std::unique_ptr<Thread> CreateWithSocketServer();
  static std::unique_ptr<Thread> Create();
  static Thread* Current();

  // Used to catch performance regressions. Use this to disallow blocking calls
  // (Invoke) for a given scope.  If a synchronous call is made while this is in
  // effect, an assert will be triggered.
  // Note that this is a single threaded class.
  class ScopedDisallowBlockingCalls {
   public:
    ScopedDisallowBlockingCalls();
    ScopedDisallowBlockingCalls(const ScopedDisallowBlockingCalls&) = delete;
    ScopedDisallowBlockingCalls& operator=(const ScopedDisallowBlockingCalls&) =
        delete;
    ~ScopedDisallowBlockingCalls();

   private:
    Thread* const thread_;
    const bool previous_state_;
  };

#if RTC_DCHECK_IS_ON
  class ScopedCountBlockingCalls {
   public:
    ScopedCountBlockingCalls(std::function<void(uint32_t, uint32_t)> callback);
    ScopedCountBlockingCalls(const ScopedDisallowBlockingCalls&) = delete;
    ScopedCountBlockingCalls& operator=(const ScopedDisallowBlockingCalls&) =
        delete;
    ~ScopedCountBlockingCalls();

    uint32_t GetBlockingCallCount() const;
    uint32_t GetCouldBeBlockingCallCount() const;
    uint32_t GetTotalBlockedCallCount() const;

    void set_minimum_call_count_for_callback(uint32_t minimum) {
      min_blocking_calls_for_callback_ = minimum;
    }

   private:
    Thread* const thread_;
    const uint32_t base_blocking_call_count_;
    const uint32_t base_could_be_blocking_call_count_;
    // The minimum number of blocking calls required in order to issue the
    // result_callback_. This is used by RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN to
    // tame log spam.
    // By default we always issue the callback, regardless of callback count.
    uint32_t min_blocking_calls_for_callback_ = 0;
    std::function<void(uint32_t, uint32_t)> result_callback_;
  };

  uint32_t GetBlockingCallCount() const;
  uint32_t GetCouldBeBlockingCallCount() const;
#endif

  SocketServer* socketserver();

  // Note: The behavior of Thread has changed.  When a thread is stopped,
  // futher Posts and Sends will fail.  However, any pending Sends and *ready*
  // Posts (as opposed to unexpired delayed Posts) will be delivered before
  // Get (or Peek) returns false.  By guaranteeing delivery of those messages,
  // we eliminate the race condition when an MessageHandler and Thread
  // may be destroyed independently of each other.
  virtual void Quit();
  virtual bool IsQuitting();
  virtual void Restart();
  // Not all message queues actually process messages (such as SignalThread).
  // In those cases, it's important to know, before posting, that it won't be
  // Processed.  Normally, this would be true until IsQuitting() is true.
  virtual bool IsProcessingMessagesForTesting();

  // Get() will process I/O until:
  //  1) A message is available (returns true)
  //  2) cmsWait seconds have elapsed (returns false)
  //  3) Stop() is called (returns false)
  virtual bool Get(Message* pmsg,
                   int cmsWait = kForever,
                   bool process_io = true);
  virtual bool Peek(Message* pmsg, int cmsWait = 0);
  // `time_sensitive` is deprecated and should always be false.
  virtual void Post(const Location& posted_from,
                    MessageHandler* phandler,
                    uint32_t id = 0,
                    MessageData* pdata = nullptr,
                    bool time_sensitive = false);
  virtual void PostDelayed(const Location& posted_from,
                           int delay_ms,
                           MessageHandler* phandler,
                           uint32_t id = 0,
                           MessageData* pdata = nullptr);
  virtual void PostAt(const Location& posted_from,
                      int64_t run_at_ms,
                      MessageHandler* phandler,
                      uint32_t id = 0,
                      MessageData* pdata = nullptr);
  virtual void Clear(MessageHandler* phandler,
                     uint32_t id = MQID_ANY,
                     MessageList* removed = nullptr);
  virtual void Dispatch(Message* pmsg);

  // Amount of time until the next message can be retrieved
  virtual int GetDelay();

  bool empty() const { return size() == 0u; }
  size_t size() const {
    CritScope cs(&crit_);
    return messages_.size() + delayed_messages_.size() + (fPeekKeep_ ? 1u : 0u);
  }

  // Internally posts a message which causes the doomed object to be deleted
  template <class T>
  void Dispose(T* doomed) {
    if (doomed) {
      Post(RTC_FROM_HERE, nullptr, MQID_DISPOSE, new DisposeData<T>(doomed));
    }
  }

  bool IsCurrent() const;

  // Sleeps the calling thread for the specified number of milliseconds, during
  // which time no processing is performed. Returns false if sleeping was
  // interrupted by a signal (POSIX only).
  static bool SleepMs(int millis);

  // Sets the thread's name, for debugging. Must be called before Start().
  // If `obj` is non-null, its value is appended to `name`.
  const std::string& name() const { return name_; }
  bool SetName(const std::string& name, const void* obj);

  // Sets the expected processing time in ms. The thread will write
  // log messages when Invoke() takes more time than this.
  // Default is 50 ms.
  void SetDispatchWarningMs(int deadline);

  // Starts the execution of the thread.
  bool Start();

  // Tells the thread to stop and waits until it is joined.
  // Never call Stop on the current thread.  Instead use the inherited Quit
  // function which will exit the base Thread without terminating the
  // underlying OS thread.
  virtual void Stop();

  // By default, Thread::Run() calls ProcessMessages(kForever).  To do other
  // work, override Run().  To receive and dispatch messages, call
  // ProcessMessages occasionally.
  virtual void Run();

  virtual void Send(const Location& posted_from,
                    MessageHandler* phandler,
                    uint32_t id = 0,
                    MessageData* pdata = nullptr);

  // Convenience method to invoke a functor on another thread.  Caller must
  // provide the `ReturnT` template argument, which cannot (easily) be deduced.
  // Uses Send() internally, which blocks the current thread until execution
  // is complete.
  // Ex: bool result = thread.Invoke<bool>(RTC_FROM_HERE,
  // &MyFunctionReturningBool);
  // NOTE: This function can only be called when synchronous calls are allowed.
  // See ScopedDisallowBlockingCalls for details.
  // NOTE: Blocking invokes are DISCOURAGED, consider if what you're doing can
  // be achieved with PostTask() and callbacks instead.
  template <
      class ReturnT,
      typename = typename std::enable_if<!std::is_void<ReturnT>::value>::type>
  ReturnT Invoke(const Location& posted_from, FunctionView<ReturnT()> functor) {
    ReturnT result;
    InvokeInternal(posted_from, [functor, &result] { result = functor(); });
    return result;
  }

  template <
      class ReturnT,
      typename = typename std::enable_if<std::is_void<ReturnT>::value>::type>
  void Invoke(const Location& posted_from, FunctionView<void()> functor) {
    InvokeInternal(posted_from, functor);
  }

  // Allows invoke to specified `thread`. Thread never will be dereferenced and
  // will be used only for reference-based comparison, so instance can be safely
  // deleted. If NDEBUG is defined and RTC_DCHECK_IS_ON is undefined do
  // nothing.
  void AllowInvokesToThread(Thread* thread);

  // If NDEBUG is defined and RTC_DCHECK_IS_ON is undefined do nothing.
  void DisallowAllInvokes();
  // Returns true if `target` was allowed by AllowInvokesToThread() or if no
  // calls were made to AllowInvokesToThread and DisallowAllInvokes. Otherwise
  // returns false.
  // If NDEBUG is defined and RTC_DCHECK_IS_ON is undefined always returns
  // true.
  bool IsInvokeToThreadAllowed(rtc::Thread* target);

  // From TaskQueueBase
  void PostTask(std::unique_ptr<webrtc::QueuedTask> task) override;
  void PostDelayedTask(std::unique_ptr<webrtc::QueuedTask> task,
                       uint32_t milliseconds) override;
  void PostDelayedHighPrecisionTask(std::unique_ptr<webrtc::QueuedTask> task,
                                    uint32_t milliseconds) override;
  void Delete() override;

  // Helper methods to avoid having to do ToQueuedTask() at the calling places.
  template <class Closure,
            typename std::enable_if<!std::is_convertible<
                Closure,
                std::unique_ptr<webrtc::QueuedTask>>::value>::type* = nullptr>
  void PostTask(Closure&& closure) {
    PostTask(webrtc::ToQueuedTask(std::forward<Closure>(closure)));
  }
  template <class Closure,
            typename std::enable_if<!std::is_convertible<
                Closure,
                std::unique_ptr<webrtc::QueuedTask>>::value>::type* = nullptr>
  void PostDelayedTask(Closure&& closure, uint32_t milliseconds) {
    PostDelayedTask(webrtc::ToQueuedTask(std::forward<Closure>(closure)),
                    milliseconds);
  }
  template <class Closure,
            typename std::enable_if<!std::is_convertible<
                Closure,
                std::unique_ptr<webrtc::QueuedTask>>::value>::type* = nullptr>
  void PostDelayedHighPrecisionTask(Closure&& closure, uint32_t milliseconds) {
    PostDelayedHighPrecisionTask(
        webrtc::ToQueuedTask(std::forward<Closure>(closure)), milliseconds);
  }

  // ProcessMessages will process I/O and dispatch messages until:
  //  1) cms milliseconds have elapsed (returns true)
  //  2) Stop() is called (returns false)
  bool ProcessMessages(int cms);

  // Returns true if this is a thread that we created using the standard
  // constructor, false if it was created by a call to
  // ThreadManager::WrapCurrentThread().  The main thread of an application
  // is generally not owned, since the OS representation of the thread
  // obviously exists before we can get to it.
  // You cannot call Start on non-owned threads.
  bool IsOwned();

  // Expose private method IsRunning() for tests.
  //
  // DANGER: this is a terrible public API.  Most callers that might want to
  // call this likely do not have enough control/knowledge of the Thread in
  // question to guarantee that the returned value remains true for the duration
  // of whatever code is conditionally executing because of the return value!
  bool RunningForTest() { return IsRunning(); }

  // These functions are public to avoid injecting test hooks. Don't call them
  // outside of tests.
  // This method should be called when thread is created using non standard
  // method, like derived implementation of rtc::Thread and it can not be
  // started by calling Start(). This will set started flag to true and
  // owned to false. This must be called from the current thread.
  bool WrapCurrent();
  void UnwrapCurrent();

  // Sets the per-thread allow-blocking-calls flag to false; this is
  // irrevocable. Must be called on this thread.
  void DisallowBlockingCalls() { SetAllowBlockingCalls(false); }

 protected:
  class CurrentThreadSetter : CurrentTaskQueueSetter {
   public:
    explicit CurrentThreadSetter(Thread* thread)
        : CurrentTaskQueueSetter(thread),
          manager_(rtc::ThreadManager::Instance()),
          previous_(manager_->CurrentThread()) {
      manager_->ChangeCurrentThreadForTest(thread);
    }
    ~CurrentThreadSetter() { manager_->ChangeCurrentThreadForTest(previous_); }

   private:
    rtc::ThreadManager* const manager_;
    rtc::Thread* const previous_;
  };

  // DelayedMessage goes into a priority queue, sorted by trigger time. Messages
  // with the same trigger time are processed in num_ (FIFO) order.
  class DelayedMessage {
   public:
    DelayedMessage(int64_t delay,
                   int64_t run_time_ms,
                   uint32_t num,
                   const Message& msg)
        : delay_ms_(delay),
          run_time_ms_(run_time_ms),
          message_number_(num),
          msg_(msg) {}

    bool operator<(const DelayedMessage& dmsg) const {
      return (dmsg.run_time_ms_ < run_time_ms_) ||
             ((dmsg.run_time_ms_ == run_time_ms_) &&
              (dmsg.message_number_ < message_number_));
    }

    int64_t delay_ms_;  // for debugging
    int64_t run_time_ms_;
    // Monotonicaly incrementing number used for ordering of messages
    // targeted to execute at the same time.
    uint32_t message_number_;
    Message msg_;
  };

  class PriorityQueue : public std::priority_queue<DelayedMessage> {
   public:
    container_type& container() { return c; }
    void reheap() { make_heap(c.begin(), c.end(), comp); }
  };

  void DoDelayPost(const Location& posted_from,
                   int64_t cmsDelay,
                   int64_t tstamp,
                   MessageHandler* phandler,
                   uint32_t id,
                   MessageData* pdata);

  // Perform initialization, subclasses must call this from their constructor
  // if false was passed as init_queue to the Thread constructor.
  void DoInit();

  // Does not take any lock. Must be called either while holding crit_, or by
  // the destructor (by definition, the latter has exclusive access).
  void ClearInternal(MessageHandler* phandler,
                     uint32_t id,
                     MessageList* removed) RTC_EXCLUSIVE_LOCKS_REQUIRED(&crit_);

  // Perform cleanup; subclasses must call this from the destructor,
  // and are not expected to actually hold the lock.
  void DoDestroy() RTC_EXCLUSIVE_LOCKS_REQUIRED(&crit_);

  void WakeUpSocketServer();

  // Same as WrapCurrent except that it never fails as it does not try to
  // acquire the synchronization access of the thread. The caller should never
  // call Stop() or Join() on this thread.
  void SafeWrapCurrent();

  // Blocks the calling thread until this thread has terminated.
  void Join();

  static void AssertBlockingIsAllowedOnCurrentThread();

  friend class ScopedDisallowBlockingCalls;

  RecursiveCriticalSection* CritForTest() { return &crit_; }

 private:
  static const int kSlowDispatchLoggingThreshold = 50;  // 50 ms

  class QueuedTaskHandler final : public MessageHandler {
   public:
    QueuedTaskHandler() {}
    void OnMessage(Message* msg) override;
  };

  // Sets the per-thread allow-blocking-calls flag and returns the previous
  // value. Must be called on this thread.
  bool SetAllowBlockingCalls(bool allow);

#if defined(WEBRTC_WIN)
  static DWORD WINAPI PreRun(LPVOID context);
#else
  static void* PreRun(void* pv);
#endif

  // ThreadManager calls this instead WrapCurrent() because
  // ThreadManager::Instance() cannot be used while ThreadManager is
  // being created.
  // The method tries to get synchronization rights of the thread on Windows if
  // `need_synchronize_access` is true.
  bool WrapCurrentWithThreadManager(ThreadManager* thread_manager,
                                    bool need_synchronize_access);

  // Return true if the thread is currently running.
  bool IsRunning();

  void InvokeInternal(const Location& posted_from,
                      rtc::FunctionView<void()> functor);

  // Called by the ThreadManager when being set as the current thread.
  void EnsureIsCurrentTaskQueue();

  // Called by the ThreadManager when being unset as the current thread.
  void ClearCurrentTaskQueue();

  // Returns a static-lifetime MessageHandler which runs message with
  // MessageLikeTask payload data.
  static MessageHandler* GetPostTaskMessageHandler();

  bool fPeekKeep_;
  Message msgPeek_;
  MessageList messages_ RTC_GUARDED_BY(crit_);
  PriorityQueue delayed_messages_ RTC_GUARDED_BY(crit_);
  uint32_t delayed_next_num_ RTC_GUARDED_BY(crit_);
#if RTC_DCHECK_IS_ON
  uint32_t blocking_call_count_ RTC_GUARDED_BY(this) = 0;
  uint32_t could_be_blocking_call_count_ RTC_GUARDED_BY(this) = 0;
  std::vector<Thread*> allowed_threads_ RTC_GUARDED_BY(this);
  bool invoke_policy_enabled_ RTC_GUARDED_BY(this) = false;
#endif
  RecursiveCriticalSection crit_;
  bool fInitialized_;
  bool fDestroyed_;

  volatile int stop_;

  // The SocketServer might not be owned by Thread.
  SocketServer* const ss_;
  // Used if SocketServer ownership lies with `this`.
  std::unique_ptr<SocketServer> own_ss_;

  std::string name_;

  // TODO(tommi): Add thread checks for proper use of control methods.
  // Ideally we should be able to just use PlatformThread.

#if defined(WEBRTC_POSIX)
  pthread_t thread_ = 0;
#endif

#if defined(WEBRTC_WIN)
  HANDLE thread_ = nullptr;
  DWORD thread_id_ = 0;
#endif

  // Indicates whether or not ownership of the worker thread lies with
  // this instance or not. (i.e. owned_ == !wrapped).
  // Must only be modified when the worker thread is not running.
  bool owned_ = true;

  // Only touched from the worker thread itself.
  bool blocking_calls_allowed_ = true;

  // Runs webrtc::QueuedTask posted to the Thread.
  QueuedTaskHandler queued_task_handler_;
  std::unique_ptr<TaskQueueBase::CurrentTaskQueueSetter>
      task_queue_registration_;

  friend class ThreadManager;

  int dispatch_warning_ms_ RTC_GUARDED_BY(this) = kSlowDispatchLoggingThreshold;
};

// AutoThread automatically installs itself at construction
// uninstalls at destruction, if a Thread object is
// _not already_ associated with the current OS thread.
//
// NOTE: *** This class should only be used by tests ***
//
class AutoThread : public Thread {
 public:
  AutoThread();
  ~AutoThread() override;

  AutoThread(const AutoThread&) = delete;
  AutoThread& operator=(const AutoThread&) = delete;
};

// AutoSocketServerThread automatically installs itself at
// construction and uninstalls at destruction. If a Thread object is
// already associated with the current OS thread, it is temporarily
// disassociated and restored by the destructor.

class AutoSocketServerThread : public Thread {
 public:
  explicit AutoSocketServerThread(SocketServer* ss);
  ~AutoSocketServerThread() override;

  AutoSocketServerThread(const AutoSocketServerThread&) = delete;
  AutoSocketServerThread& operator=(const AutoSocketServerThread&) = delete;

 private:
  rtc::Thread* old_thread_;
};
}  // namespace rtc

#endif  // RTC_BASE_THREAD_H_
