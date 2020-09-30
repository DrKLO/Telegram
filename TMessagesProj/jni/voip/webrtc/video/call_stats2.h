/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_CALL_STATS2_H_
#define VIDEO_CALL_STATS2_H_

#include <list>
#include <memory>

#include "api/units/timestamp.h"
#include "modules/include/module_common_types.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace internal {

class CallStats {
 public:
  // Time interval for updating the observers.
  static constexpr TimeDelta kUpdateInterval = TimeDelta::Millis(1000);

  CallStats(Clock* clock, TaskQueueBase* task_queue);
  ~CallStats();

  // Expose an RtcpRttStats implementation without inheriting from RtcpRttStats.
  // That allows us to separate the threading model of how RtcpRttStats is
  // used (mostly on a process thread) and how CallStats is used (mostly on
  // the TQ/worker thread). Since for both cases, there is a LastProcessedRtt()
  // method, this separation allows us to not need a lock for either.
  RtcpRttStats* AsRtcpRttStats() { return &rtcp_rtt_stats_impl_; }

  // Registers/deregisters a new observer to receive statistics updates.
  // Must be called from the construction thread.
  void RegisterStatsObserver(CallStatsObserver* observer);
  void DeregisterStatsObserver(CallStatsObserver* observer);

  // Expose |LastProcessedRtt()| from RtcpRttStats to the public interface, as
  // it is the part of the API that is needed by direct users of CallStats.
  // TODO(tommi): Threading or lifetime guarantees are not explicit in how
  // CallStats is used as RtcpRttStats or how pointers are cached in a
  // few different places (distributed via Call). It would be good to clarify
  // from what thread/TQ calls to OnRttUpdate and LastProcessedRtt need to be
  // allowed.
  int64_t LastProcessedRtt() const;

  // Exposed for tests to test histogram support.
  void UpdateHistogramsForTest() { UpdateHistograms(); }

  // Helper struct keeping track of the time a rtt value is reported.
  struct RttTime {
    RttTime(int64_t new_rtt, int64_t rtt_time) : rtt(new_rtt), time(rtt_time) {}
    const int64_t rtt;
    const int64_t time;
  };

 private:
  // Part of the RtcpRttStats implementation. Called by RtcpRttStatsImpl.
  void OnRttUpdate(int64_t rtt);

  void UpdateAndReport();

  // This method must only be called when the process thread is not
  // running, and from the construction thread.
  void UpdateHistograms();

  class RtcpRttStatsImpl : public RtcpRttStats {
   public:
    explicit RtcpRttStatsImpl(CallStats* owner) : owner_(owner) {}
    ~RtcpRttStatsImpl() override = default;

   private:
    void OnRttUpdate(int64_t rtt) override {
      // For video send streams (video/video_send_stream.cc), the RtpRtcp module
      // is currently created on a transport worker TaskQueue and not the worker
      // thread - which is what happens in other cases. We should probably fix
      // that so that the call consistently comes in on the right thread.
      owner_->OnRttUpdate(rtt);
    }

    int64_t LastProcessedRtt() const override {
      // This call path shouldn't be used anymore. This impl is only for
      // propagating the rtt from the RtpRtcp module, which does not call
      // LastProcessedRtt(). Down the line we should consider removing
      // LastProcessedRtt() and use the interface for event notifications only.
      RTC_NOTREACHED() << "Legacy call path";
      return 0;
    }

    CallStats* const owner_;
  } rtcp_rtt_stats_impl_{this};

  Clock* const clock_;

  // Used to regularly call UpdateAndReport().
  RepeatingTaskHandle repeating_task_
      RTC_GUARDED_BY(construction_thread_checker_);

  // The last RTT in the statistics update (zero if there is no valid estimate).
  int64_t max_rtt_ms_ RTC_GUARDED_BY(construction_thread_checker_);

  // Last reported average RTT value.
  int64_t avg_rtt_ms_ RTC_GUARDED_BY(construction_thread_checker_);

  // |sum_avg_rtt_ms_|, |num_avg_rtt_| and |time_of_first_rtt_ms_| are only used
  // on the ProcessThread when running. When the Process Thread is not running,
  // (and only then) they can be used in UpdateHistograms(), usually called from
  // the dtor.
  int64_t sum_avg_rtt_ms_ RTC_GUARDED_BY(construction_thread_checker_);
  int64_t num_avg_rtt_ RTC_GUARDED_BY(construction_thread_checker_);
  int64_t time_of_first_rtt_ms_ RTC_GUARDED_BY(construction_thread_checker_);

  // All Rtt reports within valid time interval, oldest first.
  std::list<RttTime> reports_ RTC_GUARDED_BY(construction_thread_checker_);

  // Observers getting stats reports.
  // When attached to ProcessThread, this is read-only. In order to allow
  // modification, we detach from the process thread while the observer
  // list is updated, to avoid races. This allows us to not require a lock
  // for the observers_ list, which makes the most common case lock free.
  std::list<CallStatsObserver*> observers_;

  SequenceChecker construction_thread_checker_;
  SequenceChecker process_thread_checker_;
  TaskQueueBase* const task_queue_;

  // Used to signal destruction to potentially pending tasks.
  ScopedTaskSafety task_safety_;

  RTC_DISALLOW_COPY_AND_ASSIGN(CallStats);
};

}  // namespace internal
}  // namespace webrtc

#endif  // VIDEO_CALL_STATS2_H_
