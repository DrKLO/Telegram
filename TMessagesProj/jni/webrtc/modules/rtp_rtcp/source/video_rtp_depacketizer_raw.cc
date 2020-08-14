/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_raw.h"

#include <utility>

#include "absl/types/optional.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer.h"
#include "rtc_base/copy_on_write_buffer.h"

namespace webrtc {

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerRaw::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  absl::optional<ParsedRtpPayload> parsed(absl::in_place);
  parsed->video_payload = std::move(rtp_payload);
  return parsed;
}

}  // namespace webrtc
