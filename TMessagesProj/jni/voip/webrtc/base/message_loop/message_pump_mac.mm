// Copyright (c) 2012 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#import "base/message_loop/message_pump_mac.h"

#import <Foundation/Foundation.h>

#include <limits>
#include <memory>

#include "base/auto_reset.h"
#include "base/feature_list.h"
#include "base/logging.h"
#include "base/mac/call_with_eh_frame.h"
#include "base/mac/scoped_cftyperef.h"
#include "base/message_loop/timer_slack.h"
#include "base/run_loop.h"
#include "base/stl_util.h"
#include "base/time/time.h"
#include "build/build_config.h"

#if !defined(OS_IOS)
#import <AppKit/AppKit.h>
#endif  // !defined(OS_IOS)

namespace base {

const CFStringRef kMessageLoopExclusiveRunLoopMode =
    CFSTR("kMessageLoopExclusiveRunLoopMode");

namespace {

// Mask that determines which modes to use.
enum { kCommonModeMask = 0x1, kAllModesMask = 0xf };

// Modes to use for MessagePumpNSApplication that are considered "safe".
// Currently just common and exclusive modes. Ideally, messages would be pumped
// in all modes, but that interacts badly with app modal dialogs (e.g. NSAlert).
enum { kNSApplicationModalSafeModeMask = 0x3 };

void NoOp(void* info) {
}

constexpr CFTimeInterval kCFTimeIntervalMax =
    std::numeric_limits<CFTimeInterval>::max();

#if !defined(OS_IOS)
// Set to true if MessagePumpMac::Create() is called before NSApp is
// initialized.  Only accessed from the main thread.
bool g_not_using_cr_app = false;

// The MessagePump controlling [NSApp run].
MessagePumpNSApplication* g_app_pump;

Feature kMessagePumpTimerInvalidation{"MessagePumpMacTimerInvalidation",
                                      FEATURE_ENABLED_BY_DEFAULT};

// Various CoreFoundation definitions.
typedef struct __CFRuntimeBase {
  uintptr_t _cfisa;
  uint8_t _cfinfo[4];
  uint32_t _rc;
} CFRuntimeBase;

#if defined(__BIG_ENDIAN__)
#define __CF_BIG_ENDIAN__ 1
#define __CF_LITTLE_ENDIAN__ 0
#endif

#if defined(__LITTLE_ENDIAN__)
#define __CF_LITTLE_ENDIAN__ 1
#define __CF_BIG_ENDIAN__ 0
#endif

#define CF_INFO_BITS (!!(__CF_BIG_ENDIAN__)*3)

#define __CFBitfieldMask(N1, N2) \
  ((((UInt32)~0UL) << (31UL - (N1) + (N2))) >> (31UL - N1))
#define __CFBitfieldSetValue(V, N1, N2, X)   \
  ((V) = ((V) & ~__CFBitfieldMask(N1, N2)) | \
         (((X) << (N2)) & __CFBitfieldMask(N1, N2)))

// Marking timers as invalid at the right time by flipping their valid bit helps
// significantly reduce power use (see the explanation in
// RunDelayedWorkTimer()), however there is no public API for doing so.
// CFRuntime.h states that CFRuntimeBase can change from release to release
// and should not be accessed directly. The last known change of this struct
// occurred in 2008 in CF-476 / 10.5; unfortunately the source for 10.11 and
// 10.12 is not available for inspection at this time.
// CanInvalidateCFRunLoopTimers() will at least prevent us from invalidating
// timers if this function starts flipping the wrong bit on a future OS release.
void __ChromeCFRunLoopTimerSetValid(CFRunLoopTimerRef timer, bool valid) {
  __CFBitfieldSetValue(((CFRuntimeBase*)timer)->_cfinfo[CF_INFO_BITS], 3, 3,
                       valid);
}
#endif  // !defined(OS_IOS)

}  // namespace

// A scoper for autorelease pools created from message pump run loops.
// Avoids dirtying up the ScopedNSAutoreleasePool interface for the rare
// case where an autorelease pool needs to be passed in.
class MessagePumpScopedAutoreleasePool {
 public:
  explicit MessagePumpScopedAutoreleasePool(MessagePumpCFRunLoopBase* pump) :
      pool_(pump->CreateAutoreleasePool()) {
  }
   ~MessagePumpScopedAutoreleasePool() {
    [pool_ drain];
  }

 private:
  NSAutoreleasePool* pool_;
  DISALLOW_COPY_AND_ASSIGN(MessagePumpScopedAutoreleasePool);
};

class MessagePumpCFRunLoopBase::ScopedModeEnabler {
 public:
  ScopedModeEnabler(MessagePumpCFRunLoopBase* owner, int mode_index)
      : owner_(owner), mode_index_(mode_index) {
    CFRunLoopRef loop = owner_->run_loop_;
    CFRunLoopAddTimer(loop, owner_->delayed_work_timer_, mode());
    CFRunLoopAddSource(loop, owner_->work_source_, mode());
    CFRunLoopAddSource(loop, owner_->idle_work_source_, mode());
    CFRunLoopAddSource(loop, owner_->nesting_deferred_work_source_, mode());
    CFRunLoopAddObserver(loop, owner_->pre_wait_observer_, mode());
    CFRunLoopAddObserver(loop, owner_->pre_source_observer_, mode());
    CFRunLoopAddObserver(loop, owner_->enter_exit_observer_, mode());
  }

  ~ScopedModeEnabler() {
    CFRunLoopRef loop = owner_->run_loop_;
    CFRunLoopRemoveObserver(loop, owner_->enter_exit_observer_, mode());
    CFRunLoopRemoveObserver(loop, owner_->pre_source_observer_, mode());
    CFRunLoopRemoveObserver(loop, owner_->pre_wait_observer_, mode());
    CFRunLoopRemoveSource(loop, owner_->nesting_deferred_work_source_, mode());
    CFRunLoopRemoveSource(loop, owner_->idle_work_source_, mode());
    CFRunLoopRemoveSource(loop, owner_->work_source_, mode());
    CFRunLoopRemoveTimer(loop, owner_->delayed_work_timer_, mode());
  }

  // This function knows about the AppKit RunLoop modes observed to potentially
  // run tasks posted to Chrome's main thread task runner. Some are internal to
  // AppKit but must be observed to keep Chrome's UI responsive. Others that may
  // be interesting, but are not watched:
  //  - com.apple.hitoolbox.windows.transitionmode
  //  - com.apple.hitoolbox.windows.flushmode
  const CFStringRef& mode() const {
    static const CFStringRef modes[] = {
        // The standard Core Foundation "common modes" constant. Must always be
        // first in this list to match the value of kCommonModeMask.
        kCFRunLoopCommonModes,

        // Mode that only sees Chrome work sources.
        kMessageLoopExclusiveRunLoopMode,

        // Process work when NSMenus are fading out.
        CFSTR("com.apple.hitoolbox.windows.windowfadingmode"),

        // Process work when AppKit is highlighting an item on the main menubar.
        CFSTR("NSUnhighlightMenuRunLoopMode"),
    };
    static_assert(base::size(modes) == kNumModes, "mode size mismatch");
    static_assert((1 << kNumModes) - 1 == kAllModesMask,
                  "kAllModesMask not large enough");

    return modes[mode_index_];
  }

 private:
  MessagePumpCFRunLoopBase* const owner_;  // Weak. Owns this.
  const int mode_index_;

  DISALLOW_COPY_AND_ASSIGN(ScopedModeEnabler);
};

// Must be called on the run loop thread.
void MessagePumpCFRunLoopBase::Run(Delegate* delegate) {
  AutoReset<bool> auto_reset_keep_running(&keep_running_, true);
  // nesting_level_ will be incremented in EnterExitRunLoop, so set
  // run_nesting_level_ accordingly.
  int last_run_nesting_level = run_nesting_level_;
  run_nesting_level_ = nesting_level_ + 1;

  Delegate* last_delegate = delegate_;
  SetDelegate(delegate);

  ScheduleWork();
  DoRun(delegate);

  // Restore the previous state of the object.
  SetDelegate(last_delegate);
  run_nesting_level_ = last_run_nesting_level;
}

void MessagePumpCFRunLoopBase::Quit() {
  if (DoQuit())
    OnDidQuit();
}

void MessagePumpCFRunLoopBase::OnDidQuit() {
  keep_running_ = false;
}

// May be called on any thread.
void MessagePumpCFRunLoopBase::ScheduleWork() {
  CFRunLoopSourceSignal(work_source_);
  CFRunLoopWakeUp(run_loop_);
}

// Must be called on the run loop thread.
void MessagePumpCFRunLoopBase::ScheduleDelayedWork(
    const TimeTicks& delayed_work_time) {
  ScheduleDelayedWorkImpl(delayed_work_time - TimeTicks::Now());
}

void MessagePumpCFRunLoopBase::ScheduleDelayedWorkImpl(TimeDelta delta) {
  // Flip the timer's validation bit just before setting the new fire time. Do
  // this now because CFRunLoopTimerSetNextFireDate() likely checks the validity
  // of a timer before proceeding to set its fire date. Making the timer valid
  // now won't have any side effects (such as a premature firing of the timer)
  // because we're only flipping a bit.
  //
  // Please see the comment in RunDelayedWorkTimer() for more info on the whys
  // of invalidation.
  SetDelayedWorkTimerValid(true);

  // The tolerance needs to be set before the fire date or it may be ignored.
  if (timer_slack_ == TIMER_SLACK_MAXIMUM) {
    CFRunLoopTimerSetTolerance(delayed_work_timer_, delta.InSecondsF() * 0.5);
  } else {
    CFRunLoopTimerSetTolerance(delayed_work_timer_, 0);
  }
  CFRunLoopTimerSetNextFireDate(
      delayed_work_timer_, CFAbsoluteTimeGetCurrent() + delta.InSecondsF());
}

void MessagePumpCFRunLoopBase::SetTimerSlack(TimerSlack timer_slack) {
  timer_slack_ = timer_slack;
}

#if defined(OS_IOS)
void MessagePumpCFRunLoopBase::Attach(Delegate* delegate) {}

void MessagePumpCFRunLoopBase::Detach() {}
#endif  // OS_IOS

// Must be called on the run loop thread.
MessagePumpCFRunLoopBase::MessagePumpCFRunLoopBase(int initial_mode_mask)
    : delegate_(NULL),
      timer_slack_(base::TIMER_SLACK_NONE),
      nesting_level_(0),
      run_nesting_level_(0),
      deepest_nesting_level_(0),
      keep_running_(true),
      delegateless_work_(false),
      delegateless_idle_work_(false),
      allow_timer_invalidation_(true) {
  run_loop_ = CFRunLoopGetCurrent();
  CFRetain(run_loop_);

  // Set a repeating timer with a preposterous firing time and interval.  The
  // timer will effectively never fire as-is.  The firing time will be adjusted
  // as needed when ScheduleDelayedWork is called.
  CFRunLoopTimerContext timer_context = CFRunLoopTimerContext();
  timer_context.info = this;
  delayed_work_timer_ = CFRunLoopTimerCreate(NULL,                // allocator
                                             kCFTimeIntervalMax,  // fire time
                                             kCFTimeIntervalMax,  // interval
                                             0,                   // flags
                                             0,                   // priority
                                             RunDelayedWorkTimer,
                                             &timer_context);

  CFRunLoopSourceContext source_context = CFRunLoopSourceContext();
  source_context.info = this;
  source_context.perform = RunWorkSource;
  work_source_ = CFRunLoopSourceCreate(NULL,  // allocator
                                       1,     // priority
                                       &source_context);
  source_context.perform = RunIdleWorkSource;
  idle_work_source_ = CFRunLoopSourceCreate(NULL,  // allocator
                                            2,     // priority
                                            &source_context);
  source_context.perform = RunNestingDeferredWorkSource;
  nesting_deferred_work_source_ = CFRunLoopSourceCreate(NULL,  // allocator
                                                        0,     // priority
                                                        &source_context);

  CFRunLoopObserverContext observer_context = CFRunLoopObserverContext();
  observer_context.info = this;
  pre_wait_observer_ = CFRunLoopObserverCreate(NULL,  // allocator
                                               kCFRunLoopBeforeWaiting,
                                               true,  // repeat
                                               0,     // priority
                                               PreWaitObserver,
                                               &observer_context);
  pre_source_observer_ = CFRunLoopObserverCreate(NULL,  // allocator
                                                 kCFRunLoopBeforeSources,
                                                 true,  // repeat
                                                 0,     // priority
                                                 PreSourceObserver,
                                                 &observer_context);
  enter_exit_observer_ = CFRunLoopObserverCreate(NULL,  // allocator
                                                 kCFRunLoopEntry |
                                                     kCFRunLoopExit,
                                                 true,  // repeat
                                                 0,     // priority
                                                 EnterExitObserver,
                                                 &observer_context);
  SetModeMask(initial_mode_mask);
}

// Ideally called on the run loop thread.  If other run loops were running
// lower on the run loop thread's stack when this object was created, the
// same number of run loops must be running when this object is destroyed.
MessagePumpCFRunLoopBase::~MessagePumpCFRunLoopBase() {
  SetModeMask(0);
  CFRelease(enter_exit_observer_);
  CFRelease(pre_source_observer_);
  CFRelease(pre_wait_observer_);
  CFRelease(nesting_deferred_work_source_);
  CFRelease(idle_work_source_);
  CFRelease(work_source_);
  CFRelease(delayed_work_timer_);
  CFRelease(run_loop_);
}

void MessagePumpCFRunLoopBase::SetDelegate(Delegate* delegate) {
  delegate_ = delegate;

  if (delegate) {
    // If any work showed up but could not be dispatched for want of a
    // delegate, set it up for dispatch again now that a delegate is
    // available.
    if (delegateless_work_) {
      CFRunLoopSourceSignal(work_source_);
      delegateless_work_ = false;
    }
    if (delegateless_idle_work_) {
      CFRunLoopSourceSignal(idle_work_source_);
      delegateless_idle_work_ = false;
    }
  }
}

// Base version returns a standard NSAutoreleasePool.
AutoreleasePoolType* MessagePumpCFRunLoopBase::CreateAutoreleasePool() {
  return [[NSAutoreleasePool alloc] init];
}

void MessagePumpCFRunLoopBase::SetModeMask(int mode_mask) {
  for (size_t i = 0; i < kNumModes; ++i) {
    bool enable = mode_mask & (0x1 << i);
    if (enable == !enabled_modes_[i]) {
      enabled_modes_[i] =
          enable ? std::make_unique<ScopedModeEnabler>(this, i) : nullptr;
    }
  }
}

int MessagePumpCFRunLoopBase::GetModeMask() const {
  int mask = 0;
  for (size_t i = 0; i < kNumModes; ++i)
    mask |= enabled_modes_[i] ? (0x1 << i) : 0;
  return mask;
}

#if !defined(OS_IOS)
// This function uses private API to modify a test timer's valid state and
// uses public API to confirm that the private API changed the correct bit.
// static
bool MessagePumpCFRunLoopBase::CanInvalidateCFRunLoopTimers() {
  if (!FeatureList::IsEnabled(kMessagePumpTimerInvalidation)) {
    return false;
  }

  CFRunLoopTimerContext timer_context = CFRunLoopTimerContext();
  timer_context.info = nullptr;
  ScopedCFTypeRef<CFRunLoopTimerRef> test_timer(
      CFRunLoopTimerCreate(NULL,                // allocator
                           kCFTimeIntervalMax,  // fire time
                           kCFTimeIntervalMax,  // interval
                           0,                   // flags
                           0,                   // priority
                           nullptr, &timer_context));
  // Should be valid from the start.
  if (!CFRunLoopTimerIsValid(test_timer)) {
    return false;
  }
  // Confirm that the private API can mark the timer invalid.
  __ChromeCFRunLoopTimerSetValid(test_timer, false);
  if (CFRunLoopTimerIsValid(test_timer)) {
    return false;
  }
  // Confirm that the private API can mark the timer valid.
  __ChromeCFRunLoopTimerSetValid(test_timer, true);
  return CFRunLoopTimerIsValid(test_timer);
}
#endif  // !defined(OS_IOS)

// static
void MessagePumpCFRunLoopBase::ChromeCFRunLoopTimerSetValid(
    CFRunLoopTimerRef timer,
    bool valid) {
#if !defined(OS_IOS)
  static bool can_invalidate_timers = CanInvalidateCFRunLoopTimers();
  if (can_invalidate_timers) {
    __ChromeCFRunLoopTimerSetValid(timer, valid);
  }
#endif  // !defined(OS_IOS)
}

void MessagePumpCFRunLoopBase::SetDelayedWorkTimerValid(bool valid) {
  if (allow_timer_invalidation_) {
    ChromeCFRunLoopTimerSetValid(delayed_work_timer_, valid);
  } else {
    pending_timer_validity_ = valid;
  }
}

void MessagePumpCFRunLoopBase::SetTimerInvalidationAllowed(bool allowed) {
  if (!allowed)
    ChromeCFRunLoopTimerSetValid(delayed_work_timer_, true);
  allow_timer_invalidation_ = allowed;
  if (allowed && pending_timer_validity_.has_value()) {
    SetDelayedWorkTimerValid(*pending_timer_validity_);
    pending_timer_validity_ = nullopt;
  }
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::RunDelayedWorkTimer(CFRunLoopTimerRef timer,
                                                   void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);

  // The message pump's timer needs to fire at changing and unpredictable
  // intervals. Creating a new timer for each firing time is very expensive, so
  // the message pump instead uses a repeating timer with a very large repeat
  // rate. After each firing of the timer, the run loop sets the timer's next
  // firing time to the distant future, essentially pausing the timer until the
  // pump sets the next firing time. This is the solution recommended by Apple.
  //
  // It turns out, however, that scheduling timers is also quite expensive, and
  // that every one of the message pump's timer firings incurs two
  // reschedulings. The first rescheduling occurs in ScheduleDelayedWork(),
  // which sets the desired next firing time. The second comes after exiting
  // this method (the timer's callback method), when the run loop sets the
  // timer's next firing time to far in the future.
  //
  // The code in __CFRunLoopDoTimer() inside CFRunLoop.c calls the timer's
  // callback, confirms that the timer is valid, and then sets its future
  // firing time based on its repeat frequency. Flipping the valid bit here
  // causes the __CFRunLoopDoTimer() to skip setting the future firing time.
  // Note that there's public API to invalidate a timer but it goes beyond
  // flipping the valid bit, making the timer unusable in the future.
  //
  // ScheduleDelayedWork() flips the valid bit back just before setting the
  // timer's new firing time.
  self->SetDelayedWorkTimerValid(false);

  // The timer fired, assume we have work and let RunWork() figure out what to
  // do and what to schedule after.
  base::mac::CallWithEHFrame(^{
    self->RunWork();
  });
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::RunWorkSource(void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);
  base::mac::CallWithEHFrame(^{
    self->RunWork();
  });
}

// Called by MessagePumpCFRunLoopBase::RunWorkSource and RunDelayedWorkTimer.
bool MessagePumpCFRunLoopBase::RunWork() {
  if (!delegate_) {
    // This point can be reached with a nullptr |delegate_| if Run is not on the
    // stack but foreign code is spinning the CFRunLoop.  Arrange to come back
    // here when a delegate is available.
    delegateless_work_ = true;
    return false;
  }
  if (!keep_running())
    return false;

  // The NSApplication-based run loop only drains the autorelease pool at each
  // UI event (NSEvent).  The autorelease pool is not drained for each
  // CFRunLoopSource target that's run.  Use a local pool for any autoreleased
  // objects if the app is not currently handling a UI event to ensure they're
  // released promptly even in the absence of UI events.
  MessagePumpScopedAutoreleasePool autorelease_pool(this);

  Delegate::NextWorkInfo next_work_info = delegate_->DoSomeWork();

  if (next_work_info.is_immediate()) {
    CFRunLoopSourceSignal(work_source_);
    return true;
  }

  if (!next_work_info.delayed_run_time.is_max())
    ScheduleDelayedWorkImpl(next_work_info.remaining_delay());
  return false;
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::RunIdleWorkSource(void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);
  base::mac::CallWithEHFrame(^{
    self->RunIdleWork();
  });
}

// Called by MessagePumpCFRunLoopBase::RunIdleWorkSource.
void MessagePumpCFRunLoopBase::RunIdleWork() {
  if (!delegate_) {
    // This point can be reached with a nullptr delegate_ if Run is not on the
    // stack but foreign code is spinning the CFRunLoop.  Arrange to come back
    // here when a delegate is available.
    delegateless_idle_work_ = true;
    return;
  }
  if (!keep_running())
    return;
  // The NSApplication-based run loop only drains the autorelease pool at each
  // UI event (NSEvent).  The autorelease pool is not drained for each
  // CFRunLoopSource target that's run.  Use a local pool for any autoreleased
  // objects if the app is not currently handling a UI event to ensure they're
  // released promptly even in the absence of UI events.
  MessagePumpScopedAutoreleasePool autorelease_pool(this);
  // Call DoIdleWork once, and if something was done, arrange to come back here
  // again as long as the loop is still running.
  bool did_work = delegate_->DoIdleWork();
  if (did_work)
    CFRunLoopSourceSignal(idle_work_source_);
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::RunNestingDeferredWorkSource(void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);
  base::mac::CallWithEHFrame(^{
    self->RunNestingDeferredWork();
  });
}

// Called by MessagePumpCFRunLoopBase::RunNestingDeferredWorkSource.
void MessagePumpCFRunLoopBase::RunNestingDeferredWork() {
  if (!delegate_) {
    // This point can be reached with a nullptr |delegate_| if Run is not on the
    // stack but foreign code is spinning the CFRunLoop.  There's no sense in
    // attempting to do any work or signalling the work sources because
    // without a delegate, work is not possible.
    return;
  }

  if (RunWork()) {
    // Work was done.  Arrange for the loop to try non-nestable idle work on
    // a subsequent pass.
    CFRunLoopSourceSignal(idle_work_source_);
  } else {
    RunIdleWork();
  }
}

// Called before the run loop goes to sleep or exits, or processes sources.
void MessagePumpCFRunLoopBase::MaybeScheduleNestingDeferredWork() {
  // deepest_nesting_level_ is set as run loops are entered.  If the deepest
  // level encountered is deeper than the current level, a nested loop
  // (relative to the current level) ran since the last time nesting-deferred
  // work was scheduled.  When that situation is encountered, schedule
  // nesting-deferred work in case any work was deferred because nested work
  // was disallowed.
  if (deepest_nesting_level_ > nesting_level_) {
    deepest_nesting_level_ = nesting_level_;
    CFRunLoopSourceSignal(nesting_deferred_work_source_);
  }
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::PreWaitObserver(CFRunLoopObserverRef observer,
                                               CFRunLoopActivity activity,
                                               void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);
  base::mac::CallWithEHFrame(^{
    // Attempt to do some idle work before going to sleep.
    self->RunIdleWork();

    // The run loop is about to go to sleep.  If any of the work done since it
    // started or woke up resulted in a nested run loop running,
    // nesting-deferred work may have accumulated.  Schedule it for processing
    // if appropriate.
    self->MaybeScheduleNestingDeferredWork();
  });
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::PreSourceObserver(CFRunLoopObserverRef observer,
                                                 CFRunLoopActivity activity,
                                                 void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);

  // The run loop has reached the top of the loop and is about to begin
  // processing sources.  If the last iteration of the loop at this nesting
  // level did not sleep or exit, nesting-deferred work may have accumulated
  // if a nested loop ran.  Schedule nesting-deferred work for processing if
  // appropriate.
  base::mac::CallWithEHFrame(^{
    self->MaybeScheduleNestingDeferredWork();
  });
}

// Called from the run loop.
// static
void MessagePumpCFRunLoopBase::EnterExitObserver(CFRunLoopObserverRef observer,
                                                 CFRunLoopActivity activity,
                                                 void* info) {
  MessagePumpCFRunLoopBase* self = static_cast<MessagePumpCFRunLoopBase*>(info);

  switch (activity) {
    case kCFRunLoopEntry:
      ++self->nesting_level_;
      if (self->nesting_level_ > self->deepest_nesting_level_) {
        self->deepest_nesting_level_ = self->nesting_level_;
      }
      break;

    case kCFRunLoopExit:
      // Not all run loops go to sleep.  If a run loop is stopped before it
      // goes to sleep due to a CFRunLoopStop call, or if the timeout passed
      // to CFRunLoopRunInMode expires, the run loop may proceed directly from
      // handling sources to exiting without any sleep.  This most commonly
      // occurs when CFRunLoopRunInMode is passed a timeout of 0, causing it
      // to make a single pass through the loop and exit without sleep.  Some
      // native loops use CFRunLoop in this way.  Because PreWaitObserver will
      // not be called in these case, MaybeScheduleNestingDeferredWork needs
      // to be called here, as the run loop exits.
      //
      // MaybeScheduleNestingDeferredWork consults self->nesting_level_
      // to determine whether to schedule nesting-deferred work.  It expects
      // the nesting level to be set to the depth of the loop that is going
      // to sleep or exiting.  It must be called before decrementing the
      // value so that the value still corresponds to the level of the exiting
      // loop.
      base::mac::CallWithEHFrame(^{
        self->MaybeScheduleNestingDeferredWork();
      });
      --self->nesting_level_;
      break;

    default:
      break;
  }

  base::mac::CallWithEHFrame(^{
    self->EnterExitRunLoop(activity);
  });
}

// Called by MessagePumpCFRunLoopBase::EnterExitRunLoop.  The default
// implementation is a no-op.
void MessagePumpCFRunLoopBase::EnterExitRunLoop(CFRunLoopActivity activity) {
}

MessagePumpCFRunLoop::MessagePumpCFRunLoop()
    : MessagePumpCFRunLoopBase(kCommonModeMask), quit_pending_(false) {}

MessagePumpCFRunLoop::~MessagePumpCFRunLoop() {}

// Called by MessagePumpCFRunLoopBase::DoRun.  If other CFRunLoopRun loops were
// running lower on the run loop thread's stack when this object was created,
// the same number of CFRunLoopRun loops must be running for the outermost call
// to Run.  Run/DoRun are reentrant after that point.
void MessagePumpCFRunLoop::DoRun(Delegate* delegate) {
  // This is completely identical to calling CFRunLoopRun(), except autorelease
  // pool management is introduced.
  int result;
  do {
    MessagePumpScopedAutoreleasePool autorelease_pool(this);
    result = CFRunLoopRunInMode(kCFRunLoopDefaultMode,
                                kCFTimeIntervalMax,
                                false);
  } while (result != kCFRunLoopRunStopped && result != kCFRunLoopRunFinished);
}

// Must be called on the run loop thread.
bool MessagePumpCFRunLoop::DoQuit() {
  // Stop the innermost run loop managed by this MessagePumpCFRunLoop object.
  if (nesting_level() == run_nesting_level()) {
    // This object is running the innermost loop, just stop it.
    CFRunLoopStop(run_loop());
    return true;
  } else {
    // There's another loop running inside the loop managed by this object.
    // In other words, someone else called CFRunLoopRunInMode on the same
    // thread, deeper on the stack than the deepest Run call.  Don't preempt
    // other run loops, just mark this object to quit the innermost Run as
    // soon as the other inner loops not managed by Run are done.
    quit_pending_ = true;
    return false;
  }
}

// Called by MessagePumpCFRunLoopBase::EnterExitObserver.
void MessagePumpCFRunLoop::EnterExitRunLoop(CFRunLoopActivity activity) {
  if (activity == kCFRunLoopExit &&
      nesting_level() == run_nesting_level() &&
      quit_pending_) {
    // Quit was called while loops other than those managed by this object
    // were running further inside a run loop managed by this object.  Now
    // that all unmanaged inner run loops are gone, stop the loop running
    // just inside Run.
    CFRunLoopStop(run_loop());
    quit_pending_ = false;
    OnDidQuit();
  }
}

MessagePumpNSRunLoop::MessagePumpNSRunLoop()
    : MessagePumpCFRunLoopBase(kCommonModeMask) {
  CFRunLoopSourceContext source_context = CFRunLoopSourceContext();
  source_context.perform = NoOp;
  quit_source_ = CFRunLoopSourceCreate(NULL,  // allocator
                                       0,     // priority
                                       &source_context);
  CFRunLoopAddSource(run_loop(), quit_source_, kCFRunLoopCommonModes);
}

MessagePumpNSRunLoop::~MessagePumpNSRunLoop() {
  CFRunLoopRemoveSource(run_loop(), quit_source_, kCFRunLoopCommonModes);
  CFRelease(quit_source_);
}

void MessagePumpNSRunLoop::DoRun(Delegate* delegate) {
  while (keep_running()) {
    // NSRunLoop manages autorelease pools itself.
    [[NSRunLoop currentRunLoop] runMode:NSDefaultRunLoopMode
                             beforeDate:[NSDate distantFuture]];
  }
}

bool MessagePumpNSRunLoop::DoQuit() {
  CFRunLoopSourceSignal(quit_source_);
  CFRunLoopWakeUp(run_loop());
  return true;
}

#if defined(OS_IOS)
MessagePumpUIApplication::MessagePumpUIApplication()
    : MessagePumpCFRunLoopBase(kCommonModeMask), run_loop_(NULL) {}

MessagePumpUIApplication::~MessagePumpUIApplication() {}

void MessagePumpUIApplication::DoRun(Delegate* delegate) {
  NOTREACHED();
}

bool MessagePumpUIApplication::DoQuit() {
  NOTREACHED();
  return false;
}

void MessagePumpUIApplication::Attach(Delegate* delegate) {
  DCHECK(!run_loop_);
  run_loop_ = new RunLoop();
  CHECK(run_loop_->BeforeRun());
  SetDelegate(delegate);
}

void MessagePumpUIApplication::Detach() {
  DCHECK(run_loop_);
  run_loop_->AfterRun();
  SetDelegate(nullptr);
  run_loop_ = nullptr;
}

#else

ScopedPumpMessagesInPrivateModes::ScopedPumpMessagesInPrivateModes() {
  DCHECK(g_app_pump);
  DCHECK_EQ(kNSApplicationModalSafeModeMask, g_app_pump->GetModeMask());
  // Pumping events in private runloop modes is known to interact badly with
  // app modal windows like NSAlert.
  if ([NSApp modalWindow])
    return;
  g_app_pump->SetModeMask(kAllModesMask);
  // Disable timer invalidation to avoid hangs. See crbug.com/912273.
  g_app_pump->SetTimerInvalidationAllowed(false);
}

ScopedPumpMessagesInPrivateModes::~ScopedPumpMessagesInPrivateModes() {
  DCHECK(g_app_pump);
  g_app_pump->SetModeMask(kNSApplicationModalSafeModeMask);
  g_app_pump->SetTimerInvalidationAllowed(true);
}

int ScopedPumpMessagesInPrivateModes::GetModeMaskForTest() {
  return g_app_pump ? g_app_pump->GetModeMask() : -1;
}

MessagePumpNSApplication::MessagePumpNSApplication()
    : MessagePumpCFRunLoopBase(kNSApplicationModalSafeModeMask),
      running_own_loop_(false),
      quit_pending_(false) {
  DCHECK_EQ(nullptr, g_app_pump);
  g_app_pump = this;
}

MessagePumpNSApplication::~MessagePumpNSApplication() {
  DCHECK_EQ(this, g_app_pump);
  g_app_pump = nullptr;
}

void MessagePumpNSApplication::DoRun(Delegate* delegate) {
  bool last_running_own_loop_ = running_own_loop_;

  // NSApp must be initialized by calling:
  // [{some class which implements CrAppProtocol} sharedApplication]
  // Most likely candidates are CrApplication or BrowserCrApplication.
  // These can be initialized from C++ code by calling
  // RegisterCrApp() or RegisterBrowserCrApp().
  CHECK(NSApp);

  if (![NSApp isRunning]) {
    running_own_loop_ = false;
    // NSApplication manages autorelease pools itself when run this way.
    [NSApp run];
  } else {
    running_own_loop_ = true;
    NSDate* distant_future = [NSDate distantFuture];
    while (keep_running()) {
      MessagePumpScopedAutoreleasePool autorelease_pool(this);
      NSEvent* event = [NSApp nextEventMatchingMask:NSAnyEventMask
                                          untilDate:distant_future
                                             inMode:NSDefaultRunLoopMode
                                            dequeue:YES];
      if (event) {
        [NSApp sendEvent:event];
      }
    }
  }

  running_own_loop_ = last_running_own_loop_;
}

bool MessagePumpNSApplication::DoQuit() {
  // If the app is displaying a modal window in a native run loop, we can only
  // quit our run loop after the window is closed. Otherwise the [NSApplication
  // stop] below will apply to the modal window run loop instead. To work around
  // this, the quit is applied when we re-enter our own run loop after the
  // window is gone (see MessagePumpNSApplication::EnterExitRunLoop).
  if (nesting_level() > run_nesting_level() &&
      [[NSApplication sharedApplication] modalWindow] != nil) {
    quit_pending_ = true;
    return false;
  }

  if (!running_own_loop_) {
    [[NSApplication sharedApplication] stop:nil];
  }

  // Send a fake event to wake the loop up.
  [NSApp postEvent:[NSEvent otherEventWithType:NSApplicationDefined
                                      location:NSZeroPoint
                                 modifierFlags:0
                                     timestamp:0
                                  windowNumber:0
                                       context:NULL
                                       subtype:0
                                         data1:0
                                         data2:0]
           atStart:NO];
  return true;
}

void MessagePumpNSApplication::EnterExitRunLoop(CFRunLoopActivity activity) {
  // If we previously tried quitting while a modal window was active, check if
  // the window is gone now and we're no longer nested in a system run loop.
  if (activity == kCFRunLoopEntry && quit_pending_ &&
      nesting_level() <= run_nesting_level() &&
      [[NSApplication sharedApplication] modalWindow] == nil) {
    quit_pending_ = false;
    if (DoQuit())
      OnDidQuit();
  }
}

MessagePumpCrApplication::MessagePumpCrApplication() {
}

MessagePumpCrApplication::~MessagePumpCrApplication() {
}

// Prevents an autorelease pool from being created if the app is in the midst of
// handling a UI event because various parts of AppKit depend on objects that
// are created while handling a UI event to be autoreleased in the event loop.
// An example of this is NSWindowController. When a window with a window
// controller is closed it goes through a stack like this:
// (Several stack frames elided for clarity)
//
// #0 [NSWindowController autorelease]
// #1 DoAClose
// #2 MessagePumpCFRunLoopBase::DoWork()
// #3 [NSRunLoop run]
// #4 [NSButton performClick:]
// #5 [NSWindow sendEvent:]
// #6 [NSApp sendEvent:]
// #7 [NSApp run]
//
// -performClick: spins a nested run loop. If the pool created in DoWork was a
// standard NSAutoreleasePool, it would release the objects that were
// autoreleased into it once DoWork released it. This would cause the window
// controller, which autoreleased itself in frame #0, to release itself, and
// possibly free itself. Unfortunately this window controller controls the
// window in frame #5. When the stack is unwound to frame #5, the window would
// no longer exists and crashes may occur. Apple gets around this by never
// releasing the pool it creates in frame #4, and letting frame #7 clean it up
// when it cleans up the pool that wraps frame #7. When an autorelease pool is
// released it releases all other pools that were created after it on the
// autorelease pool stack.
//
// CrApplication is responsible for setting handlingSendEvent to true just
// before it sends the event through the event handling mechanism, and
// returning it to its previous value once the event has been sent.
AutoreleasePoolType* MessagePumpCrApplication::CreateAutoreleasePool() {
  if (MessagePumpMac::IsHandlingSendEvent())
    return nil;
  return MessagePumpNSApplication::CreateAutoreleasePool();
}

// static
bool MessagePumpMac::UsingCrApp() {
  DCHECK([NSThread isMainThread]);

  // If NSApp is still not initialized, then the subclass used cannot
  // be determined.
  DCHECK(NSApp);

  // The pump was created using MessagePumpNSApplication.
  if (g_not_using_cr_app)
    return false;

  return [NSApp conformsToProtocol:@protocol(CrAppProtocol)];
}

// static
bool MessagePumpMac::IsHandlingSendEvent() {
  DCHECK([NSApp conformsToProtocol:@protocol(CrAppProtocol)]);
  NSObject<CrAppProtocol>* app = static_cast<NSObject<CrAppProtocol>*>(NSApp);
  return [app isHandlingSendEvent];
}
#endif  // !defined(OS_IOS)

// static
std::unique_ptr<MessagePump> MessagePumpMac::Create() {
  if ([NSThread isMainThread]) {
#if defined(OS_IOS)
    return std::make_unique<MessagePumpUIApplication>();
#else
    if ([NSApp conformsToProtocol:@protocol(CrAppProtocol)])
      return std::make_unique<MessagePumpCrApplication>();

    // The main-thread MessagePump implementations REQUIRE an NSApp.
    // Executables which have specific requirements for their
    // NSApplication subclass should initialize appropriately before
    // creating an event loop.
    [NSApplication sharedApplication];
    g_not_using_cr_app = true;
    return std::make_unique<MessagePumpNSApplication>();
#endif
  }

  return std::make_unique<MessagePumpNSRunLoop>();
}

}  // namespace base
