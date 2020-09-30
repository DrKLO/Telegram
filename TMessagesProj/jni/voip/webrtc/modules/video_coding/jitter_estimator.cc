/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/jitter_estimator.h"

#include <assert.h>
#include <math.h>
#include <string.h>

#include <algorithm>
#include <cstdint>

#include "absl/types/optional.h"
#include "modules/video_coding/internal_defines.h"
#include "modules/video_coding/rtt_filter.h"
#include "rtc_base/experiments/jitter_upper_bound_experiment.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace {
static constexpr uint32_t kStartupDelaySamples = 30;
static constexpr int64_t kFsAccuStartupSamples = 5;
static constexpr double kMaxFramerateEstimate = 200.0;
static constexpr int64_t kNackCountTimeoutMs = 60000;
static constexpr double kDefaultMaxTimestampDeviationInSigmas = 3.5;
}  // namespace

VCMJitterEstimator::VCMJitterEstimator(Clock* clock)
    : _phi(0.97),
      _psi(0.9999),
      _alphaCountMax(400),
      _thetaLow(0.000001),
      _nackLimit(3),
      _numStdDevDelayOutlier(15),
      _numStdDevFrameSizeOutlier(3),
      _noiseStdDevs(2.33),       // ~Less than 1% chance
                                 // (look up in normal distribution table)...
      _noiseStdDevOffset(30.0),  // ...of getting 30 ms freezes
      _rttFilter(),
      fps_counter_(30),  // TODO(sprang): Use an estimator with limit based on
                         // time, rather than number of samples.
      time_deviation_upper_bound_(
          JitterUpperBoundExperiment::GetUpperBoundSigmas().value_or(
              kDefaultMaxTimestampDeviationInSigmas)),
      enable_reduced_delay_(
          !field_trial::IsEnabled("WebRTC-ReducedJitterDelayKillSwitch")),
      clock_(clock) {
  Reset();
}

VCMJitterEstimator::~VCMJitterEstimator() {}

VCMJitterEstimator& VCMJitterEstimator::operator=(
    const VCMJitterEstimator& rhs) {
  if (this != &rhs) {
    memcpy(_thetaCov, rhs._thetaCov, sizeof(_thetaCov));
    memcpy(_Qcov, rhs._Qcov, sizeof(_Qcov));

    _avgFrameSize = rhs._avgFrameSize;
    _varFrameSize = rhs._varFrameSize;
    _maxFrameSize = rhs._maxFrameSize;
    _fsSum = rhs._fsSum;
    _fsCount = rhs._fsCount;
    _lastUpdateT = rhs._lastUpdateT;
    _prevEstimate = rhs._prevEstimate;
    _prevFrameSize = rhs._prevFrameSize;
    _avgNoise = rhs._avgNoise;
    _alphaCount = rhs._alphaCount;
    _filterJitterEstimate = rhs._filterJitterEstimate;
    _startupCount = rhs._startupCount;
    _latestNackTimestamp = rhs._latestNackTimestamp;
    _nackCount = rhs._nackCount;
    _rttFilter = rhs._rttFilter;
    clock_ = rhs.clock_;
  }
  return *this;
}

// Resets the JitterEstimate.
void VCMJitterEstimator::Reset() {
  _theta[0] = 1 / (512e3 / 8);
  _theta[1] = 0;
  _varNoise = 4.0;

  _thetaCov[0][0] = 1e-4;
  _thetaCov[1][1] = 1e2;
  _thetaCov[0][1] = _thetaCov[1][0] = 0;
  _Qcov[0][0] = 2.5e-10;
  _Qcov[1][1] = 1e-10;
  _Qcov[0][1] = _Qcov[1][0] = 0;
  _avgFrameSize = 500;
  _maxFrameSize = 500;
  _varFrameSize = 100;
  _lastUpdateT = -1;
  _prevEstimate = -1.0;
  _prevFrameSize = 0;
  _avgNoise = 0.0;
  _alphaCount = 1;
  _filterJitterEstimate = 0.0;
  _latestNackTimestamp = 0;
  _nackCount = 0;
  _latestNackTimestamp = 0;
  _fsSum = 0;
  _fsCount = 0;
  _startupCount = 0;
  _rttFilter.Reset();
  fps_counter_.Reset();
}

// Updates the estimates with the new measurements.
void VCMJitterEstimator::UpdateEstimate(int64_t frameDelayMS,
                                        uint32_t frameSizeBytes,
                                        bool incompleteFrame /* = false */) {
  if (frameSizeBytes == 0) {
    return;
  }
  int deltaFS = frameSizeBytes - _prevFrameSize;
  if (_fsCount < kFsAccuStartupSamples) {
    _fsSum += frameSizeBytes;
    _fsCount++;
  } else if (_fsCount == kFsAccuStartupSamples) {
    // Give the frame size filter.
    _avgFrameSize = static_cast<double>(_fsSum) / static_cast<double>(_fsCount);
    _fsCount++;
  }
  if (!incompleteFrame || frameSizeBytes > _avgFrameSize) {
    double avgFrameSize = _phi * _avgFrameSize + (1 - _phi) * frameSizeBytes;
    if (frameSizeBytes < _avgFrameSize + 2 * sqrt(_varFrameSize)) {
      // Only update the average frame size if this sample wasn't a key frame.
      _avgFrameSize = avgFrameSize;
    }
    // Update the variance anyway since we want to capture cases where we only
    // get key frames.
    _varFrameSize = VCM_MAX(
        _phi * _varFrameSize + (1 - _phi) * (frameSizeBytes - avgFrameSize) *
                                   (frameSizeBytes - avgFrameSize),
        1.0);
  }

  // Update max frameSize estimate.
  _maxFrameSize =
      VCM_MAX(_psi * _maxFrameSize, static_cast<double>(frameSizeBytes));

  if (_prevFrameSize == 0) {
    _prevFrameSize = frameSizeBytes;
    return;
  }
  _prevFrameSize = frameSizeBytes;

  // Cap frameDelayMS based on the current time deviation noise.
  int64_t max_time_deviation_ms =
      static_cast<int64_t>(time_deviation_upper_bound_ * sqrt(_varNoise) + 0.5);
  frameDelayMS = std::max(std::min(frameDelayMS, max_time_deviation_ms),
                          -max_time_deviation_ms);

  // Only update the Kalman filter if the sample is not considered an extreme
  // outlier. Even if it is an extreme outlier from a delay point of view, if
  // the frame size also is large the deviation is probably due to an incorrect
  // line slope.
  double deviation = DeviationFromExpectedDelay(frameDelayMS, deltaFS);

  if (fabs(deviation) < _numStdDevDelayOutlier * sqrt(_varNoise) ||
      frameSizeBytes >
          _avgFrameSize + _numStdDevFrameSizeOutlier * sqrt(_varFrameSize)) {
    // Update the variance of the deviation from the line given by the Kalman
    // filter.
    EstimateRandomJitter(deviation, incompleteFrame);
    // Prevent updating with frames which have been congested by a large frame,
    // and therefore arrives almost at the same time as that frame.
    // This can occur when we receive a large frame (key frame) which has been
    // delayed. The next frame is of normal size (delta frame), and thus deltaFS
    // will be << 0. This removes all frame samples which arrives after a key
    // frame.
    if ((!incompleteFrame || deviation >= 0.0) &&
        static_cast<double>(deltaFS) > -0.25 * _maxFrameSize) {
      // Update the Kalman filter with the new data
      KalmanEstimateChannel(frameDelayMS, deltaFS);
    }
  } else {
    int nStdDev =
        (deviation >= 0) ? _numStdDevDelayOutlier : -_numStdDevDelayOutlier;
    EstimateRandomJitter(nStdDev * sqrt(_varNoise), incompleteFrame);
  }
  // Post process the total estimated jitter
  if (_startupCount >= kStartupDelaySamples) {
    PostProcessEstimate();
  } else {
    _startupCount++;
  }
}

// Updates the nack/packet ratio.
void VCMJitterEstimator::FrameNacked() {
  if (_nackCount < _nackLimit) {
    _nackCount++;
  }
  _latestNackTimestamp = clock_->TimeInMicroseconds();
}

// Updates Kalman estimate of the channel.
// The caller is expected to sanity check the inputs.
void VCMJitterEstimator::KalmanEstimateChannel(int64_t frameDelayMS,
                                               int32_t deltaFSBytes) {
  double Mh[2];
  double hMh_sigma;
  double kalmanGain[2];
  double measureRes;
  double t00, t01;

  // Kalman filtering

  // Prediction
  // M = M + Q
  _thetaCov[0][0] += _Qcov[0][0];
  _thetaCov[0][1] += _Qcov[0][1];
  _thetaCov[1][0] += _Qcov[1][0];
  _thetaCov[1][1] += _Qcov[1][1];

  // Kalman gain
  // K = M*h'/(sigma2n + h*M*h') = M*h'/(1 + h*M*h')
  // h = [dFS 1]
  // Mh = M*h'
  // hMh_sigma = h*M*h' + R
  Mh[0] = _thetaCov[0][0] * deltaFSBytes + _thetaCov[0][1];
  Mh[1] = _thetaCov[1][0] * deltaFSBytes + _thetaCov[1][1];
  // sigma weights measurements with a small deltaFS as noisy and
  // measurements with large deltaFS as good
  if (_maxFrameSize < 1.0) {
    return;
  }
  double sigma = (300.0 * exp(-fabs(static_cast<double>(deltaFSBytes)) /
                              (1e0 * _maxFrameSize)) +
                  1) *
                 sqrt(_varNoise);
  if (sigma < 1.0) {
    sigma = 1.0;
  }
  hMh_sigma = deltaFSBytes * Mh[0] + Mh[1] + sigma;
  if ((hMh_sigma < 1e-9 && hMh_sigma >= 0) ||
      (hMh_sigma > -1e-9 && hMh_sigma <= 0)) {
    assert(false);
    return;
  }
  kalmanGain[0] = Mh[0] / hMh_sigma;
  kalmanGain[1] = Mh[1] / hMh_sigma;

  // Correction
  // theta = theta + K*(dT - h*theta)
  measureRes = frameDelayMS - (deltaFSBytes * _theta[0] + _theta[1]);
  _theta[0] += kalmanGain[0] * measureRes;
  _theta[1] += kalmanGain[1] * measureRes;

  if (_theta[0] < _thetaLow) {
    _theta[0] = _thetaLow;
  }

  // M = (I - K*h)*M
  t00 = _thetaCov[0][0];
  t01 = _thetaCov[0][1];
  _thetaCov[0][0] = (1 - kalmanGain[0] * deltaFSBytes) * t00 -
                    kalmanGain[0] * _thetaCov[1][0];
  _thetaCov[0][1] = (1 - kalmanGain[0] * deltaFSBytes) * t01 -
                    kalmanGain[0] * _thetaCov[1][1];
  _thetaCov[1][0] = _thetaCov[1][0] * (1 - kalmanGain[1]) -
                    kalmanGain[1] * deltaFSBytes * t00;
  _thetaCov[1][1] = _thetaCov[1][1] * (1 - kalmanGain[1]) -
                    kalmanGain[1] * deltaFSBytes * t01;

  // Covariance matrix, must be positive semi-definite.
  assert(_thetaCov[0][0] + _thetaCov[1][1] >= 0 &&
         _thetaCov[0][0] * _thetaCov[1][1] -
                 _thetaCov[0][1] * _thetaCov[1][0] >=
             0 &&
         _thetaCov[0][0] >= 0);
}

// Calculate difference in delay between a sample and the expected delay
// estimated by the Kalman filter
double VCMJitterEstimator::DeviationFromExpectedDelay(
    int64_t frameDelayMS,
    int32_t deltaFSBytes) const {
  return frameDelayMS - (_theta[0] * deltaFSBytes + _theta[1]);
}

// Estimates the random jitter by calculating the variance of the sample
// distance from the line given by theta.
void VCMJitterEstimator::EstimateRandomJitter(double d_dT,
                                              bool incompleteFrame) {
  uint64_t now = clock_->TimeInMicroseconds();
  if (_lastUpdateT != -1) {
    fps_counter_.AddSample(now - _lastUpdateT);
  }
  _lastUpdateT = now;

  if (_alphaCount == 0) {
    assert(false);
    return;
  }
  double alpha =
      static_cast<double>(_alphaCount - 1) / static_cast<double>(_alphaCount);
  _alphaCount++;
  if (_alphaCount > _alphaCountMax)
    _alphaCount = _alphaCountMax;

  // In order to avoid a low frame rate stream to react slower to changes,
  // scale the alpha weight relative a 30 fps stream.
  double fps = GetFrameRate();
  if (fps > 0.0) {
    double rate_scale = 30.0 / fps;
    // At startup, there can be a lot of noise in the fps estimate.
    // Interpolate rate_scale linearly, from 1.0 at sample #1, to 30.0 / fps
    // at sample #kStartupDelaySamples.
    if (_alphaCount < kStartupDelaySamples) {
      rate_scale =
          (_alphaCount * rate_scale + (kStartupDelaySamples - _alphaCount)) /
          kStartupDelaySamples;
    }
    alpha = pow(alpha, rate_scale);
  }

  double avgNoise = alpha * _avgNoise + (1 - alpha) * d_dT;
  double varNoise =
      alpha * _varNoise + (1 - alpha) * (d_dT - _avgNoise) * (d_dT - _avgNoise);
  if (!incompleteFrame || varNoise > _varNoise) {
    _avgNoise = avgNoise;
    _varNoise = varNoise;
  }
  if (_varNoise < 1.0) {
    // The variance should never be zero, since we might get stuck and consider
    // all samples as outliers.
    _varNoise = 1.0;
  }
}

double VCMJitterEstimator::NoiseThreshold() const {
  double noiseThreshold = _noiseStdDevs * sqrt(_varNoise) - _noiseStdDevOffset;
  if (noiseThreshold < 1.0) {
    noiseThreshold = 1.0;
  }
  return noiseThreshold;
}

// Calculates the current jitter estimate from the filtered estimates.
double VCMJitterEstimator::CalculateEstimate() {
  double ret = _theta[0] * (_maxFrameSize - _avgFrameSize) + NoiseThreshold();

  // A very low estimate (or negative) is neglected.
  if (ret < 1.0) {
    if (_prevEstimate <= 0.01) {
      ret = 1.0;
    } else {
      ret = _prevEstimate;
    }
  }
  if (ret > 10000.0) {  // Sanity
    ret = 10000.0;
  }
  _prevEstimate = ret;
  return ret;
}

void VCMJitterEstimator::PostProcessEstimate() {
  _filterJitterEstimate = CalculateEstimate();
}

void VCMJitterEstimator::UpdateRtt(int64_t rttMs) {
  _rttFilter.Update(rttMs);
}

// Returns the current filtered estimate if available,
// otherwise tries to calculate an estimate.
int VCMJitterEstimator::GetJitterEstimate(
    double rttMultiplier,
    absl::optional<double> rttMultAddCapMs) {
  double jitterMS = CalculateEstimate() + OPERATING_SYSTEM_JITTER;
  uint64_t now = clock_->TimeInMicroseconds();

  if (now - _latestNackTimestamp > kNackCountTimeoutMs * 1000)
    _nackCount = 0;

  if (_filterJitterEstimate > jitterMS)
    jitterMS = _filterJitterEstimate;
  if (_nackCount >= _nackLimit) {
    if (rttMultAddCapMs.has_value()) {
      jitterMS +=
          std::min(_rttFilter.RttMs() * rttMultiplier, rttMultAddCapMs.value());
    } else {
      jitterMS += _rttFilter.RttMs() * rttMultiplier;
    }
  }

  if (enable_reduced_delay_) {
    static const double kJitterScaleLowThreshold = 5.0;
    static const double kJitterScaleHighThreshold = 10.0;
    double fps = GetFrameRate();
    // Ignore jitter for very low fps streams.
    if (fps < kJitterScaleLowThreshold) {
      if (fps == 0.0) {
        return rtc::checked_cast<int>(std::max(0.0, jitterMS) + 0.5);
      }
      return 0;
    }

    // Semi-low frame rate; scale by factor linearly interpolated from 0.0 at
    // kJitterScaleLowThreshold to 1.0 at kJitterScaleHighThreshold.
    if (fps < kJitterScaleHighThreshold) {
      jitterMS =
          (1.0 / (kJitterScaleHighThreshold - kJitterScaleLowThreshold)) *
          (fps - kJitterScaleLowThreshold) * jitterMS;
    }
  }

  return rtc::checked_cast<int>(std::max(0.0, jitterMS) + 0.5);
}

double VCMJitterEstimator::GetFrameRate() const {
  if (fps_counter_.ComputeMean() <= 0.0)
    return 0;

  double fps = 1000000.0 / fps_counter_.ComputeMean();
  // Sanity check.
  assert(fps >= 0.0);
  if (fps > kMaxFramerateEstimate) {
    fps = kMaxFramerateEstimate;
  }
  return fps;
}
}  // namespace webrtc
