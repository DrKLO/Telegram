/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_SDP_FMTP_UTILS_H_
#define MEDIA_BASE_SDP_FMTP_UTILS_H_

#include "absl/types/optional.h"
#include "api/video_codecs/sdp_video_format.h"

namespace webrtc {

// Parse max frame rate from SDP FMTP line. absl::nullopt is returned if the
// field is missing or not a number.
absl::optional<int> ParseSdpForVPxMaxFrameRate(
    const SdpVideoFormat::Parameters& params);

// Parse max frame size from SDP FMTP line. absl::nullopt is returned if the
// field is missing or not a number. Please note that the value is stored in sub
// blocks but the returned value is in total number of pixels.
absl::optional<int> ParseSdpForVPxMaxFrameSize(
    const SdpVideoFormat::Parameters& params);

}  // namespace webrtc

#endif  // MEDIA_BASE_SDP_FMTP_UTILS_H__
