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
#include "api/function_view.h"
#include "api/task_queue/queued_task.h"
#include "api/task_queue/task_queue_base.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/deprecated/recursive_critical_section.h"
#include "rtc_base/location.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/platform_thread_types.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_message.h"

#if defined(WEBRTC_WIN)
#include "rtc_base/win32.h"
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

  void Run() override { functor_(); }

 private:
  ~MessageWithFunctor() override {}

  typename std::remove_reference<FunctorT>::type functor_;

  RTC_DISALLOW_COPY_AND_ASSIGN(MessageWithFunctor);
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

  bool IsMainThread();

#if RTC_DCHECK_IS_ON
  // Registers that a Send operation is to be performed between |source| and
  // |target|, while checking that this does not cause a send cycle that could
  // potentially cause a deadlock.
  void RegisterSendAndCheckForCycles(Thread* source, Thread* target);
#endif

 private:
  ThreadManager();
  ~ThreadManager();

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

  // The thread to potentially autowrap.
  const PlatformThreadRef main_thread_ref_;

  RTC_DISALLOW_COPY_AND_ASSIGN(ThreadManager);
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
  // pass false for |do_init|, so that DoInit is called only on the fully
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
  // |time_sensitive| is deprecated and should always be false.
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

  // When this signal is sent out, any references to this queue should
  // no longer be used.
  sigslot::signal0<> SignalQueueDestroyed;

  bool IsCurrent() const;

  // Sleeps the calling thread for the specified number of milliseconds, during
  // which time no processing is performed. Returns false if sleeping was
  // interrupted by a signal (POSIX only).
  static bool SleepMs(int millis);

  // Sets the thread's name, for debugging. Must be called before Start().
  // If |obj| is non-null, its value is appended to |name|.
  const std::string& name() const { return name_; }
  bool SetName(const std::string& name, const void* obj);

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
  // provide the |ReturnT| template argument, which cannot (easily) be deduced.
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

  // Allows invoke to specified |thread|. Thread never will be dereferenced and
  // will be used only for reference-based comparison, so instance can be safely
  // deleted. If NDEBUG is defined and DCHECK_ALWAYS_ON is undefined do nothing.
  void AllowInvokesToThread(Thread* thread);

  // If NDEBUG is defined and DCHECK_ALWAYS_ON is undefined do nothing.
  void DisallowAllInvokes();
  // Returns true if |target| was allowed by AllowInvokesToThread() or if no
  // calls were made to AllowInvokesToThread and DisallowAllInvokes. Otherwise
  // returns false.
  // If NDEBUG is defined and DCHECK_ALWAYS_ON is undefined always returns true.
  bool IsInvokeToThreadAllowed(rtc::Thread* target);

  // Posts a task to invoke the functor on |this| thread asynchronously, i.e.
  // without blocking the thread that invoked PostTask(). Ownership of |functor|
  // is passed and (usually, see below) destroyed on |this| thread after it is
  // invoked.
  // Requirements of FunctorT:
  // - FunctorT is movable.
  // - FunctorT implements "T operator()()" or "T operator()() const" for some T
  //   (if T is not void, the return value is discarded on |this| thread).
  // - FunctorT has a public destructor that can be invoked from |this| thread
  //   after operation() has been invoked.
  // - The functor must not cause the thread to quit before PostTask() is done.
  //
  // Destruction of the functor/task mimics what TaskQueue::PostTask does: If
  // the task is run, it will be destroyed on |this| thread. However, if there
  // are pending tasks by the time the Thread is destroyed, or a task is posted
  // to a thread that is quitting, the task is destroyed immediately, on the
  // calling thread. Destroying the Thread only blocks for any currently running
  // task to complete. Note that TQ abstraction is even vaguer on how
  // destruction happens in these cases, allowing destruction to happen
  // asynchronously at a later time and on some arbitrary thread. So to ease
  // migration, don't depend on Thread::PostTask destroying un-run tasks
  // immediately.
  //
  // Example - Calling a class method:
  // class Foo {
  //  public:
  //   void DoTheThing();
  // };
  // Foo foo;
  // thread->PostTask(RTC_FROM_HERE, Bind(&Foo::DoTheThing, &foo));
  //
  // Example - Calling a lambda function:
  // thread->PostTask(RTC_FROM_HERE,
  //                  [&x, &y] { x.TrackComputations(y.Compute()); });
  template <class FunctorT>
  void PostTask(const Location& posted_from, FunctorT&& functor) {
    Post(posted_from, GetPostTaskMessageHandler(), /*id=*/0,
         new rtc_thread_internal::MessageWithFunctor<FunctorT>(
             std::forward<FunctorT>(functor)));
  }
  template <class FunctorT>
  void PostDelayedTask(const Location& posted_from,
                       FunctorT&& functor,
                       uint32_t milliseconds) {
    PostDelayed(posted_from, milliseconds, GetPostTaskMessageHandler(),
                /*id=*/0,
                new rtc_thread_internal::MessageWithFunctor<FunctorT>(
                    std::forward<FunctorT>(functor)));
  }

  // From TaskQueueBase
  void PostTask(std::unique_ptr<webrtc::QueuedTask> task) override;
  void PostDelayedTask(std::unique_ptr<webrtc::QueuedTask> task,
                       uint32_t milliseconds) override;
  void Delete() override;

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
  // |need_synchronize_access| is true.
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
#if (!defined(NDEBUG) || defined(DCHECK_ALWAYS_ON))
  std::vector<Thread*> allowed_threads_ RTC_GUARDED_BY(this);
  bool invoke_policy_enabled_ RTC_GUARDED_BY(this) = false;
#endif
  RecursiveCriticalSection crit_;
  bool fInitialized_;
  bool fDestroyed_;

  volatile int stop_;

  // The SocketServer might not be owned by Thread.
  SocketServer* const ss_;
  // Used if SocketServer ownership lies with |this|.
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

  RTC_DISALLOW_COPY_AND_ASSIGN(Thread);
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

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(AutoThread);
};

// AutoSocketServerThread automatically installs itself at
// construction and uninstalls at destruction. If a Thread object is
// already associated with the current OS thread, it is temporarily
// disassociated and restored by the destructor.

class AutoSocketServerThread : public Thread {
 public:
  explicit AutoSocketServerThread(SocketServer* ss);
  ~AutoSocketServerThread() override;

 private:
  rtc::Thread* old_thread_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AutoSocketServerThread);
};
}  // namespace rtc

#endif  // RTC_BASE_THREAD_H_
