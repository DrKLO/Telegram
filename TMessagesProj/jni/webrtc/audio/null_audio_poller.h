/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_NULL_AUDIO_POLLER_H_
#define AUDIO_NULL_AUDIO_POLLER_H_

#include <stdint.h>

#include "modules/audio_device/include/audio_device_defines.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {
namespace internal {

class NullAudioPoller final : public rtc::MessageHandler {
 public:
  explicit NullAudioPoller(AudioTransport* audio_transport);
  ~NullAudioPoller() override;

 protected:
  void OnMessage(rtc::Message* msg) override;

 private:
  rtc::ThreadChecker thread_checker_;
  AudioTransport* const audio_transport_;
  int64_t reschedule_at_;
};

}  // namespace internal
}  // namespace webrtc

#endif  // AUDIO_NULL_AUDIO_POLLER_H_
