/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_PROCESSING_GAIN_CONTROL_FOR_EXPERIMENTAL_AGC_H_
#define MODULES_AUDIO_PROCESSING_GAIN_CONTROL_FOR_EXPERIMENTAL_AGC_H_

#include "modules/audio_processing/agc/agc_manager_direct.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {

class ApmDataDumper;

// This class has two main purposes:
//
// 1) It is returned instead of the real GainControl after the new AGC has been
//    enabled in order to prevent an outside user from overriding compression
//    settings. It doesn't do anything in its implementation, except for
//    delegating the const methods and Enable calls to the real GainControl, so
//    AGC can still be disabled.
//
// 2) It is injected into AgcManagerDirect and implements volume callbacks for
//    getting and setting the volume level. It just caches this value to be used
//    in VoiceEngine later.
class GainControlForExperimentalAgc : public GainControl,
                                      public VolumeCallbacks {
 public:
  GainControlForExperimentalAgc(GainControl* gain_control,
                                rtc::CriticalSection* crit_capture);
  ~GainControlForExperimentalAgc() override;

  // GainControl implementation.
  int Enable(bool enable) override;
  bool is_enabled() const override;
  int set_stream_analog_level(int level) override;
  int stream_analog_level() override;
  int set_mode(Mode mode) override;
  Mode mode() const override;
  int set_target_level_dbfs(int level) override;
  int target_level_dbfs() const override;
  int set_compression_gain_db(int gain) override;
  int compression_gain_db() const override;
  int enable_limiter(bool enable) override;
  bool is_limiter_enabled() const override;
  int set_analog_level_limits(int minimum, int maximum) override;
  int analog_level_minimum() const override;
  int analog_level_maximum() const override;
  bool stream_is_saturated() const override;

  // VolumeCallbacks implementation.
  void SetMicVolume(int volume) override;
  int GetMicVolume() override;

  void Initialize();

 private:
  std::unique_ptr<ApmDataDumper> data_dumper_;
  GainControl* real_gain_control_;
  int volume_;
  rtc::CriticalSection* crit_capture_;
  bool do_log_level_ = true;
  static int instance_counter_;
  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(GainControlForExperimentalAgc);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_PROCESSING_GAIN_CONTROL_FOR_EXPERIMENTAL_AGC_H_
