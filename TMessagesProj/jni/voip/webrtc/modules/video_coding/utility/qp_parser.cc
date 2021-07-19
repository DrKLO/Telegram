/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/utility/qp_parser.h"

#include "modules/video_coding/utility/vp8_header_parser.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"

namespace webrtc {

absl::optional<uint32_t> QpParser::Parse(VideoCodecType codec_type,
                                         size_t spatial_idx,
                                         const uint8_t* frame_data,
                                         size_t frame_size) {
  if (frame_data == nullptr || frame_size == 0 ||
      spatial_idx >= kMaxSimulcastStreams) {
    return absl::nullopt;
  }

  if (codec_type == kVideoCodecVP8) {
    int qp = -1;
    if (vp8::GetQp(frame_data, frame_size, &qp)) {
      return qp;
    }
  } else if (codec_type == kVideoCodecVP9) {
    int qp = -1;
    if (vp9::GetQp(frame_data, frame_size, &qp)) {
      return qp;
    }
  } else if (codec_type == kVideoCodecH264) {
    return h264_parsers_[spatial_idx].Parse(frame_data, frame_size);
  }

  return absl::nullopt;
}

absl::optional<uint32_t> QpParser::H264QpParser::Parse(
    const uint8_t* frame_data,
    size_t frame_size) {
  MutexLock lock(&mutex_);
  bitstream_parser_.ParseBitstream(
      rtc::ArrayView<const uint8_t>(frame_data, frame_size));
  return bitstream_parser_.GetLastSliceQp();
}

}  // namespace webrtc
