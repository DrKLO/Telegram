/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_TEST_RTC_STATS_OBTAINER_H_
#define PC_TEST_RTC_STATS_OBTAINER_H_

#include "api/make_ref_counted.h"
#include "api/sequence_checker.h"
#include "api/stats/rtc_stats_collector_callback.h"
#include "api/stats/rtc_stats_report.h"
#include "rtc_base/gunit.h"

namespace webrtc {

class RTCStatsObtainer : public RTCStatsCollectorCallback {
 public:
  static rtc::scoped_refptr<RTCStatsObtainer> Create(
      rtc::scoped_refptr<const RTCStatsReport>* report_ptr = nullptr) {
    return rtc::make_ref_counted<RTCStatsObtainer>(report_ptr);
  }

  void OnStatsDelivered(
      const rtc::scoped_refptr<const RTCStatsReport>& report) override {
    EXPECT_TRUE(thread_checker_.IsCurrent());
    report_ = report;
    if (report_ptr_)
      *report_ptr_ = report_;
  }

  rtc::scoped_refptr<const RTCStatsReport> report() const {
    EXPECT_TRUE(thread_checker_.IsCurrent());
    return report_;
  }

 protected:
  explicit RTCStatsObtainer(
      rtc::scoped_refptr<const RTCStatsReport>* report_ptr)
      : report_ptr_(report_ptr) {}

 private:
  SequenceChecker thread_checker_;
  rtc::scoped_refptr<const RTCStatsReport> report_;
  rtc::scoped_refptr<const RTCStatsReport>* report_ptr_;
};

}  // namespace webrtc

#endif  // PC_TEST_RTC_STATS_OBTAINER_H_
