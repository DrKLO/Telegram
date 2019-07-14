/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_
#define MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_

#include "modules/audio_processing/agc/agc.h"

#include "test/gmock.h"

namespace webrtc {

class MockAgc : public Agc {
 public:
  virtual ~MockAgc() {}
  MOCK_METHOD2(AnalyzePreproc, float(const int16_t* audio, size_t length));
  MOCK_METHOD3(Process,
               void(const int16_t* audio, size_t length, int sample_rate_hz));
  MOCK_METHOD1(GetRmsErrorDb, bool(int* error));
  MOCK_METHOD0(Reset, void());
  MOCK_METHOD1(set_target_level_dbfs, int(int level));
  MOCK_CONST_METHOD0(target_level_dbfs, int());
  MOCK_METHOD1(EnableStandaloneVad, void(bool enable));
  MOCK_CONST_METHOD0(standalone_vad_enabled, bool());
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_AGC_MOCK_AGC_H_
