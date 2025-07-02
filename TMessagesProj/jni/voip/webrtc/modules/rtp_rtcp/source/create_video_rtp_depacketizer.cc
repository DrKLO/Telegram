/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/create_video_rtp_depacketizer.h"

#include <memory>

#include "api/video/video_codec_type.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_av1.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_generic.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_h264.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp8.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.h"
#ifdef RTC_ENABLE_H265
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_h265.h"
#endif

namespace webrtc {

std::unique_ptr<VideoRtpDepacketizer> CreateVideoRtpDepacketizer(
    VideoCodecType codec) {
  switch (codec) {
    case kVideoCodecH264:
      return std::make_unique<VideoRtpDepacketizerH264>();
    case kVideoCodecVP8:
      return std::make_unique<VideoRtpDepacketizerVp8>();
    case kVideoCodecVP9:
      return std::make_unique<VideoRtpDepacketizerVp9>();
    case kVideoCodecAV1:
      return std::make_unique<VideoRtpDepacketizerAv1>();
    case kVideoCodecH265:
#ifdef RTC_ENABLE_H265
      return std::make_unique<VideoRtpDepacketizerH265>();
#else
      return nullptr;
#endif
    case kVideoCodecGeneric:
    case kVideoCodecMultiplex:
      return std::make_unique<VideoRtpDepacketizerGeneric>();
  }
  RTC_CHECK_NOTREACHED();
}

}  // namespace webrtc
