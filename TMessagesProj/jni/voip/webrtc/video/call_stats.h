/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_CALL_STATS_H_
#define VIDEO_CALL_STATS_H_

#include <list>
#include <memory>

#include "api/sequence_checker.h"
#include "modules/include/module.h"
#include "modules/include/module_common_types.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/synchronization/mutex.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

// CallStats keeps track of statistics for a call.
// TODO(webrtc:11489): Make call_stats_ not depend on ProcessThread and
// make callbacks on the worker thread (TQ).
class CallStats : public Module, public RtcpRttStats {
 public:
  // Time interval for updating the observers.
  static constexpr int64_t kUpdateIntervalMs = 1000;

  CallStats(Clock* clock, ProcessThread* process_thread);
  ~CallStats() override;

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
  int64_t LastProcessedRtt() const override;

  // Exposed for tests to test histogram support.
  void UpdateHistogramsForTest() { UpdateHistograms(); }

  // Helper struct keeping track of the time a rtt value is reported.
  struct RttTime {
    RttTime(int64_t new_rtt, int64_t rtt_time) : rtt(new_rtt), time(rtt_time) {}
    const int64_t rtt;
    const int64_t time;
  };

 private:
  // RtcpRttStats implementation.
  void OnRttUpdate(int64_t rtt) override;

  // Implements Module, to use the process thread.
  int64_t TimeUntilNextProcess() override;
  void Process() override;

  // TODO(tommi): Use this to know when we're attached to the process thread?
  // Alternatively, inject that pointer via the ctor since the call_stats
  // test code, isn't using a processthread atm.
  void ProcessThreadAttached(ProcessThread* process_thread) override;

  // This method must only be called when the process thread is not
  // running, and from the construction thread.
  void UpdateHistograms();

  Clock* const clock_;

  // The last time 'Process' resulted in statistic update.
  int64_t last_process_time_ RTC_GUARDED_BY(process_thread_checker_);
  // The last RTT in the statistics update (zero if there is no valid estimate).
  int64_t max_rtt_ms_ RTC_GUARDED_BY(process_thread_checker_);

  // Accessed from random threads (seemingly). Consider atomic.
  // |avg_rtt_ms_| is allowed to be read on the process thread without a lock.
  // |avg_rtt_ms_lock_| must be held elsewhere for reading.
  // |avg_rtt_ms_lock_| must be held on the process thread for writing.
  int64_t avg_rtt_ms_;

  // Protects |avg_rtt_ms_|.
  mutable Mutex avg_rtt_ms_lock_;

  // |sum_avg_rtt_ms_|, |num_avg_rtt_| and |time_of_first_rtt_ms_| are only used
  // on the ProcessThread when running. When the Process Thread is not running,
  // (and only then) they can be used in UpdateHistograms(), usually called from
  // the dtor.
  int64_t sum_avg_rtt_ms_ RTC_GUARDED_BY(process_thread_checker_);
  int64_t num_avg_rtt_ RTC_GUARDED_BY(process_thread_checker_);
  int64_t time_of_first_rtt_ms_ RTC_GUARDED_BY(process_thread_checker_);

  // All Rtt reports within valid time interval, oldest first.
  std::list<RttTime> reports_ RTC_GUARDED_BY(process_thread_checker_);

  // Observers getting stats reports.
  // When attached to ProcessThread, this is read-only. In order to allow
  // modification, we detach from the process thread while the observer
  // list is updated, to avoid races. This allows us to not require a lock
  // for the observers_ list, which makes the most common case lock free.
  std::list<CallStatsObserver*> observers_;

  SequenceChecker construction_thread_checker_;
  SequenceChecker process_thread_checker_;
  ProcessThread* const process_thread_;
  bool process_thread_running_ RTC_GUARDED_BY(construction_thread_checker_);

  RTC_DISALLOW_COPY_AND_ASSIGN(CallStats);
};

}  // namespace webrtc

#endif  // VIDEO_CALL_STATS_H_
