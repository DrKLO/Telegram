/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_TIME_TIMESTAMP_EXTRAPOLATOR_H_
#define RTC_BASE_TIME_TIMESTAMP_EXTRAPOLATOR_H_

#include <stdint.h>

namespace webrtc {

// Not thread safe.
class TimestampExtrapolator {
 public:
  explicit TimestampExtrapolator(int64_t start_ms);
  void Update(int64_t tMs, uint32_t ts90khz);
  int64_t ExtrapolateLocalTime(uint32_t timestamp90khz);
  void Reset(int64_t start_ms);

 private:
  void CheckForWrapArounds(uint32_t ts90khz);
  bool DelayChangeDetection(double error);
  double _w[2];
  double _pP[2][2];
  int64_t _startMs;
  int64_t _prevMs;
  uint32_t _firstTimestamp;
  int32_t _wrapArounds;
  int64_t _prevUnwrappedTimestamp;
  int64_t _prevWrapTimestamp;
  const double _lambda;
  bool _firstAfterReset;
  uint32_t _packetCount;
  const uint32_t _startUpFilterDelayInPackets;

  double _detectorAccumulatorPos;
  double _detectorAccumulatorNeg;
  const double _alarmThreshold;
  const double _accDrift;
  const double _accMaxError;
  const double _pP11;
};

}  // namespace webrtc

#endif  // RTC_BASE_TIME_TIMESTAMP_EXTRAPOLATOR_H_
