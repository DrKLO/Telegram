/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_POST_DECODE_VAD_H_
#define MODULES_AUDIO_CODING_NETEQ_POST_DECODE_VAD_H_

#include <stddef.h>
#include <stdint.h>

#include "api/audio_codecs/audio_decoder.h"
#include "common_audio/vad/include/webrtc_vad.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

class PostDecodeVad {
 public:
  PostDecodeVad()
      : enabled_(false),
        running_(false),
        active_speech_(true),
        sid_interval_counter_(0),
        vad_instance_(NULL) {}

  virtual ~PostDecodeVad();

  // Enables post-decode VAD.
  void Enable();

  // Disables post-decode VAD.
  void Disable();

  // Initializes post-decode VAD.
  void Init();

  // Updates post-decode VAD with the audio data in |signal| having |length|
  // samples. The data is of type |speech_type|, at the sample rate |fs_hz|.
  void Update(int16_t* signal,
              size_t length,
              AudioDecoder::SpeechType speech_type,
              bool sid_frame,
              int fs_hz);

  // Accessors.
  bool enabled() const { return enabled_; }
  bool running() const { return running_; }
  bool active_speech() const { return active_speech_; }

 private:
  static const int kVadMode = 0;  // Sets aggressiveness to "Normal".
  // Number of Update() calls without CNG/SID before re-enabling VAD.
  static const int kVadAutoEnable = 3000;

  bool enabled_;
  bool running_;
  bool active_speech_;
  int sid_interval_counter_;
  ::VadInst* vad_instance_;

  RTC_DISALLOW_COPY_AND_ASSIGN(PostDecodeVad);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_POST_DECODE_VAD_H_
