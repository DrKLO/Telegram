/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_JITTER_BUFFER_DELAY_H_
#define PC_JITTER_BUFFER_DELAY_H_

#include <stdint.h>

#include "absl/types/optional.h"
#include "media/base/delayable.h"
#include "pc/jitter_buffer_delay_interface.h"
#include "rtc_base/thread.h"

namespace webrtc {

// JitterBufferDelay converts delay from seconds to milliseconds for the
// underlying media channel. It also handles cases when user sets delay before
// the start of media_channel by caching its request. Note, this class is not
// thread safe. Its thread safe version is defined in
// pc/jitter_buffer_delay_proxy.h
class JitterBufferDelay : public JitterBufferDelayInterface {
 public:
  // Must be called on signaling thread.
  explicit JitterBufferDelay(rtc::Thread* worker_thread);

  void OnStart(cricket::Delayable* media_channel, uint32_t ssrc) override;

  void OnStop() override;

  void Set(absl::optional<double> delay_seconds) override;

 private:
  // Throughout webrtc source, sometimes it is also called as |main_thread_|.
  rtc::Thread* const signaling_thread_;
  rtc::Thread* const worker_thread_;
  // Media channel and ssrc together uniqely identify audio stream.
  cricket::Delayable* media_channel_ = nullptr;
  absl::optional<uint32_t> ssrc_;
  absl::optional<double> cached_delay_seconds_;
};

}  // namespace webrtc

#endif  // PC_JITTER_BUFFER_DELAY_H_
