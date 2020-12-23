/*
 *  Copyright (c) 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/time/timestamp_extrapolator.h"

#include <algorithm>

namespace webrtc {

TimestampExtrapolator::TimestampExtrapolator(int64_t start_ms)
    : _startMs(0),
      _firstTimestamp(0),
      _wrapArounds(0),
      _prevUnwrappedTimestamp(-1),
      _prevWrapTimestamp(-1),
      _lambda(1),
      _firstAfterReset(true),
      _packetCount(0),
      _startUpFilterDelayInPackets(2),
      _detectorAccumulatorPos(0),
      _detectorAccumulatorNeg(0),
      _alarmThreshold(60e3),
      _accDrift(6600),  // in timestamp ticks, i.e. 15 ms
      _accMaxError(7000),
      _pP11(1e10) {
  Reset(start_ms);
}

void TimestampExtrapolator::Reset(int64_t start_ms) {
  _startMs = start_ms;
  _prevMs = _startMs;
  _firstTimestamp = 0;
  _w[0] = 90.0;
  _w[1] = 0;
  _pP[0][0] = 1;
  _pP[1][1] = _pP11;
  _pP[0][1] = _pP[1][0] = 0;
  _firstAfterReset = true;
  _prevUnwrappedTimestamp = -1;
  _prevWrapTimestamp = -1;
  _wrapArounds = 0;
  _packetCount = 0;
  _detectorAccumulatorPos = 0;
  _detectorAccumulatorNeg = 0;
}

void TimestampExtrapolator::Update(int64_t tMs, uint32_t ts90khz) {
  if (tMs - _prevMs > 10e3) {
    // Ten seconds without a complete frame.
    // Reset the extrapolator
    Reset(tMs);
  } else {
    _prevMs = tMs;
  }

  // Remove offset to prevent badly scaled matrices
  tMs -= _startMs;

  CheckForWrapArounds(ts90khz);

  int64_t unwrapped_ts90khz =
      static_cast<int64_t>(ts90khz) +
      _wrapArounds * ((static_cast<int64_t>(1) << 32) - 1);

  if (_firstAfterReset) {
    // Make an initial guess of the offset,
    // should be almost correct since tMs - _startMs
    // should about zero at this time.
    _w[1] = -_w[0] * tMs;
    _firstTimestamp = unwrapped_ts90khz;
    _firstAfterReset = false;
  }

  double residual = (static_cast<double>(unwrapped_ts90khz) - _firstTimestamp) -
                    static_cast<double>(tMs) * _w[0] - _w[1];
  if (DelayChangeDetection(residual) &&
      _packetCount >= _startUpFilterDelayInPackets) {
    // A sudden change of average network delay has been detected.
    // Force the filter to adjust its offset parameter by changing
    // the offset uncertainty. Don't do this during startup.
    _pP[1][1] = _pP11;
  }

  if (_prevUnwrappedTimestamp >= 0 &&
      unwrapped_ts90khz < _prevUnwrappedTimestamp) {
    // Drop reordered frames.
    return;
  }

  // T = [t(k) 1]';
  // that = T'*w;
  // K = P*T/(lambda + T'*P*T);
  double K[2];
  K[0] = _pP[0][0] * tMs + _pP[0][1];
  K[1] = _pP[1][0] * tMs + _pP[1][1];
  double TPT = _lambda + tMs * K[0] + K[1];
  K[0] /= TPT;
  K[1] /= TPT;
  // w = w + K*(ts(k) - that);
  _w[0] = _w[0] + K[0] * residual;
  _w[1] = _w[1] + K[1] * residual;
  // P = 1/lambda*(P - K*T'*P);
  double p00 =
      1 / _lambda * (_pP[0][0] - (K[0] * tMs * _pP[0][0] + K[0] * _pP[1][0]));
  double p01 =
      1 / _lambda * (_pP[0][1] - (K[0] * tMs * _pP[0][1] + K[0] * _pP[1][1]));
  _pP[1][0] =
      1 / _lambda * (_pP[1][0] - (K[1] * tMs * _pP[0][0] + K[1] * _pP[1][0]));
  _pP[1][1] =
      1 / _lambda * (_pP[1][1] - (K[1] * tMs * _pP[0][1] + K[1] * _pP[1][1]));
  _pP[0][0] = p00;
  _pP[0][1] = p01;
  _prevUnwrappedTimestamp = unwrapped_ts90khz;
  if (_packetCount < _startUpFilterDelayInPackets) {
    _packetCount++;
  }
}

int64_t TimestampExtrapolator::ExtrapolateLocalTime(uint32_t timestamp90khz) {
  int64_t localTimeMs = 0;
  CheckForWrapArounds(timestamp90khz);
  double unwrapped_ts90khz =
      static_cast<double>(timestamp90khz) +
      _wrapArounds * ((static_cast<int64_t>(1) << 32) - 1);
  if (_packetCount == 0) {
    localTimeMs = -1;
  } else if (_packetCount < _startUpFilterDelayInPackets) {
    localTimeMs =
        _prevMs +
        static_cast<int64_t>(
            static_cast<double>(unwrapped_ts90khz - _prevUnwrappedTimestamp) /
                90.0 +
            0.5);
  } else {
    if (_w[0] < 1e-3) {
      localTimeMs = _startMs;
    } else {
      double timestampDiff =
          unwrapped_ts90khz - static_cast<double>(_firstTimestamp);
      localTimeMs = static_cast<int64_t>(static_cast<double>(_startMs) +
                                         (timestampDiff - _w[1]) / _w[0] + 0.5);
    }
  }
  return localTimeMs;
}

// Investigates if the timestamp clock has overflowed since the last timestamp
// and keeps track of the number of wrap arounds since reset.
void TimestampExtrapolator::CheckForWrapArounds(uint32_t ts90khz) {
  if (_prevWrapTimestamp == -1) {
    _prevWrapTimestamp = ts90khz;
    return;
  }
  if (ts90khz < _prevWrapTimestamp) {
    // This difference will probably be less than -2^31 if we have had a wrap
    // around (e.g. timestamp = 1, _previousTimestamp = 2^32 - 1). Since it is
    // casted to a Word32, it should be positive.
    if (static_cast<int32_t>(ts90khz - _prevWrapTimestamp) > 0) {
      // Forward wrap around
      _wrapArounds++;
    }
  } else {
    // This difference will probably be less than -2^31 if we have had a
    // backward wrap around. Since it is casted to a Word32, it should be
    // positive.
    if (static_cast<int32_t>(_prevWrapTimestamp - ts90khz) > 0) {
      // Backward wrap around
      _wrapArounds--;
    }
  }
  _prevWrapTimestamp = ts90khz;
}

bool TimestampExtrapolator::DelayChangeDetection(double error) {
  // CUSUM detection of sudden delay changes
  error = (error > 0) ? std::min(error, _accMaxError)
                      : std::max(error, -_accMaxError);
  _detectorAccumulatorPos =
      std::max(_detectorAccumulatorPos + error - _accDrift, double{0});
  _detectorAccumulatorNeg =
      std::min(_detectorAccumulatorNeg + error + _accDrift, double{0});
  if (_detectorAccumulatorPos > _alarmThreshold ||
      _detectorAccumulatorNeg < -_alarmThreshold) {
    // Alarm
    _detectorAccumulatorPos = _detectorAccumulatorNeg = 0;
    return true;
  }
  return false;
}

}  // namespace webrtc
