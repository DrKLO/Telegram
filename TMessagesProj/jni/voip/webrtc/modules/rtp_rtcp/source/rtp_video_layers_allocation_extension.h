/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_RTP_VIDEO_LAYERS_ALLOCATION_EXTENSION_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_VIDEO_LAYERS_ALLOCATION_EXTENSION_H_

#include "api/video/video_layers_allocation.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"

namespace webrtc {

// TODO(bugs.webrtc.org/12000): Note that this extensions is being developed and
// the wire format will likely change.
class RtpVideoLayersAllocationExtension {
 public:
  using value_type = VideoLayersAllocation;
  static constexpr RTPExtensionType kId = kRtpExtensionVideoLayersAllocation;
  static constexpr const char kUri[] =
      "http://www.webrtc.org/experiments/rtp-hdrext/video-layers-allocation00";
  static bool Parse(rtc::ArrayView<const uint8_t> data,
                    VideoLayersAllocation* allocation);
  static size_t ValueSize(const VideoLayersAllocation& allocation);
  static bool Write(rtc::ArrayView<uint8_t> data,
                    const VideoLayersAllocation& allocation);
};

}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_RTP_VIDEO_LAYERS_ALLOCATION_EXTENSION_H_
