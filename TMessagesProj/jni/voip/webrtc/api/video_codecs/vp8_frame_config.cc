/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video_codecs/vp8_frame_config.h"

#include "modules/video_coding/codecs/interface/common_constants.h"
#include "rtc_base/checks.h"

namespace webrtc {

Vp8FrameConfig::Vp8FrameConfig() : Vp8FrameConfig(kNone, kNone, kNone, false) {}

Vp8FrameConfig::Vp8FrameConfig(BufferFlags last,
                               BufferFlags golden,
                               BufferFlags arf)
    : Vp8FrameConfig(last, golden, arf, false) {}

Vp8FrameConfig::Vp8FrameConfig(BufferFlags last,
                               BufferFlags golden,
                               BufferFlags arf,
                               FreezeEntropy)
    : Vp8FrameConfig(last, golden, arf, true) {}

Vp8FrameConfig::Vp8FrameConfig(BufferFlags last,
                               BufferFlags golden,
                               BufferFlags arf,
                               bool freeze_entropy)
    : drop_frame(last == BufferFlags::kNone && golden == BufferFlags::kNone &&
                 arf == BufferFlags::kNone),
      last_buffer_flags(last),
      golden_buffer_flags(golden),
      arf_buffer_flags(arf),
      encoder_layer_id(0),
      packetizer_temporal_idx(kNoTemporalIdx),
      layer_sync(false),
      freeze_entropy(freeze_entropy),
      first_reference(Vp8BufferReference::kNone),
      second_reference(Vp8BufferReference::kNone),
      retransmission_allowed(true) {}

bool Vp8FrameConfig::References(Buffer buffer) const {
  switch (buffer) {
    case Buffer::kLast:
      return (last_buffer_flags & kReference) != 0;
    case Buffer::kGolden:
      return (golden_buffer_flags & kReference) != 0;
    case Buffer::kArf:
      return (arf_buffer_flags & kReference) != 0;
    case Buffer::kCount:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return false;
}

bool Vp8FrameConfig::Updates(Buffer buffer) const {
  switch (buffer) {
    case Buffer::kLast:
      return (last_buffer_flags & kUpdate) != 0;
    case Buffer::kGolden:
      return (golden_buffer_flags & kUpdate) != 0;
    case Buffer::kArf:
      return (arf_buffer_flags & kUpdate) != 0;
    case Buffer::kCount:
      break;
  }
  RTC_DCHECK_NOTREACHED();
  return false;
}

}  // namespace webrtc
