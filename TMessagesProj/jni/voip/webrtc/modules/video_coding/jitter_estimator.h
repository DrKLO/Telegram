/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_JITTER_ESTIMATOR_H_
#define MODULES_VIDEO_CODING_JITTER_ESTIMATOR_H_

#include "modules/video_coding/rtt_filter.h"
#include "rtc_base/rolling_accumulator.h"

namespace webrtc {

class Clock;

class VCMJitterEstimator {
 public:
  explicit VCMJitterEstimator(Clock* clock);
  virtual ~VCMJitterEstimator();
  VCMJitterEstimator& operator=(const VCMJitterEstimator& rhs);

  // Resets the estimate to the initial state.
  void Reset();

  // Updates the jitter estimate with the new data.
  //
  // Input:
  //          - frameDelay      : Delay-delta calculated by UTILDelayEstimate in
  //                              milliseconds.
  //          - frameSize       : Frame size of the current frame.
  //          - incompleteFrame : Flags if the frame is used to update the
  //                              estimate before it was complete.
  //                              Default is false.
  void UpdateEstimate(int64_t frameDelayMS,
                      uint32_t frameSizeBytes,
                      bool incompleteFrame = false);

  // Returns the current jitter estimate in milliseconds and adds an RTT
  // dependent term in cases of retransmission.
  //  Input:
  //          - rttMultiplier  : RTT param multiplier (when applicable).
  //
  // Return value              : Jitter estimate in milliseconds.
  virtual int GetJitterEstimate(double rttMultiplier,
                                absl::optional<double> rttMultAddCapMs);

  // Updates the nack counter.
  void FrameNacked();

  // Updates the RTT filter.
  //
  // Input:
  //          - rttMs          : RTT in ms.
  void UpdateRtt(int64_t rttMs);

  // A constant describing the delay from the jitter buffer to the delay on the
  // receiving side which is not accounted for by the jitter buffer nor the
  // decoding delay estimate.
  static const uint32_t OPERATING_SYSTEM_JITTER = 10;

 protected:
  // These are protected for better testing possibilities.
  double _theta[2];  // Estimated line parameters (slope, offset)
  double _varNoise;  // Variance of the time-deviation from the line

 private:
  // Updates the Kalman filter for the line describing the frame size dependent
  // jitter.
  //
  // Input:
  //          - frameDelayMS    : Delay-delta calculated by UTILDelayEstimate in
  //                              milliseconds.
  //          - deltaFSBytes    : Frame size delta, i.e. frame size at time T
  //                            : minus frame size at time T-1.
  void KalmanEstimateChannel(int64_t frameDelayMS, int32_t deltaFSBytes);

  // Updates the random jitter estimate, i.e. the variance of the time
  // deviations from the line given by the Kalman filter.
  //
  // Input:
  //          - d_dT              : The deviation from the kalman estimate.
  //          - incompleteFrame   : True if the frame used to update the
  //                                estimate with was incomplete.
  void EstimateRandomJitter(double d_dT, bool incompleteFrame);

  double NoiseThreshold() const;

  // Calculates the current jitter estimate.
  //
  // Return value                 : The current jitter estimate in milliseconds.
  double CalculateEstimate();

  // Post process the calculated estimate.
  void PostProcessEstimate();

  // Calculates the difference in delay between a sample and the expected delay
  // estimated by the Kalman filter.
  //
  // Input:
  //          - frameDelayMS    : Delay-delta calculated by UTILDelayEstimate in
  //                              milliseconds.
  //          - deltaFS         : Frame size delta, i.e. frame size at time
  //                              T minus frame size at time T-1.
  //
  // Return value               : The difference in milliseconds.
  double DeviationFromExpectedDelay(int64_t frameDelayMS,
                                    int32_t deltaFSBytes) const;

  double GetFrameRate() const;

  // Constants, filter parameters.
  const double _phi;
  const double _psi;
  const uint32_t _alphaCountMax;
  const double _thetaLow;
  const uint32_t _nackLimit;
  const int32_t _numStdDevDelayOutlier;
  const int32_t _numStdDevFrameSizeOutlier;
  const double _noiseStdDevs;
  const double _noiseStdDevOffset;

  double _thetaCov[2][2];  // Estimate covariance
  double _Qcov[2][2];      // Process noise covariance
  double _avgFrameSize;    // Average frame size
  double _varFrameSize;    // Frame size variance
  double _maxFrameSize;    // Largest frame size received (descending
                           // with a factor _psi)
  uint32_t _fsSum;
  uint32_t _fsCount;

  int64_t _lastUpdateT;
  double _prevEstimate;     // The previously returned jitter estimate
  uint32_t _prevFrameSize;  // Frame size of the previous frame
  double _avgNoise;         // Average of the random jitter
  uint32_t _alphaCount;
  double _filterJitterEstimate;  // The filtered sum of jitter estimates

  uint32_t _startupCount;

  int64_t
      _latestNackTimestamp;  // Timestamp in ms when the latest nack was seen
  uint32_t _nackCount;       // Keeps track of the number of nacks received,
                             // but never goes above _nackLimit
  VCMRttFilter _rttFilter;

  rtc::RollingAccumulator<uint64_t> fps_counter_;
  const double time_deviation_upper_bound_;
  const bool enable_reduced_delay_;
  Clock* clock_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_JITTER_ESTIMATOR_H_
