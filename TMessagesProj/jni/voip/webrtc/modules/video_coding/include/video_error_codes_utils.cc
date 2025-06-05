/*
 *  Copyright (c) 2024 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/include/video_error_codes_utils.h"

#include "modules/video_coding/include/video_error_codes.h"

namespace webrtc {

const char* WebRtcVideoCodecErrorToString(int32_t error_code) {
  switch (error_code) {
    case WEBRTC_VIDEO_CODEC_TARGET_BITRATE_OVERSHOOT:
      return "WEBRTC_VIDEO_CODEC_TARGET_BITRATE_OVERSHOOT";
    case WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME:
      return "WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME";
    case WEBRTC_VIDEO_CODEC_NO_OUTPUT:
      return "WEBRTC_VIDEO_CODEC_NO_OUTPUT";
    case WEBRTC_VIDEO_CODEC_ERROR:
      return "WEBRTC_VIDEO_CODEC_ERROR";
    case WEBRTC_VIDEO_CODEC_MEMORY:
      return "WEBRTC_VIDEO_CODEC_MEMORY";
    case WEBRTC_VIDEO_CODEC_ERR_PARAMETER:
      return "WEBRTC_VIDEO_CODEC_ERR_PARAMETER";
    case WEBRTC_VIDEO_CODEC_TIMEOUT:
      return "WEBRTC_VIDEO_CODEC_TIMEOUT";
    case WEBRTC_VIDEO_CODEC_UNINITIALIZED:
      return "WEBRTC_VIDEO_CODEC_UNINITIALIZED";
    case WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE:
      return "WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE";
    case WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED:
      return "WEBRTC_VIDEO_CODEC_ERR_SIMULCAST_PARAMETERS_NOT_SUPPORTED";
    case WEBRTC_VIDEO_CODEC_ENCODER_FAILURE:
      return "WEBRTC_VIDEO_CODEC_ENCODER_FAILURE";
    default:
      return "WEBRTC_VIDEO_CODEC_UNKNOWN";
  }
}

}  // namespace webrtc
