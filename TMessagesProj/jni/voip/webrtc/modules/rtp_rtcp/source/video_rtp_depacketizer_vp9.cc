/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.h"

#include <string.h>

#include "api/video/video_codec_constants.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

// Picture ID:
//
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  |   M:0 => picture id is 7 bits.
//      +-+-+-+-+-+-+-+-+   M:1 => picture id is 15 bits.
// M:   | EXTENDED PID  |
//      +-+-+-+-+-+-+-+-+
//
void ParsePictureId(BitstreamReader& parser, RTPVideoHeaderVP9* vp9) {
  if (parser.ReadBit()) {  // m_bit
    vp9->picture_id = parser.ReadBits(15);
    vp9->max_picture_id = kMaxTwoBytePictureId;
  } else {
    vp9->picture_id = parser.ReadBits(7);
    vp9->max_picture_id = kMaxOneBytePictureId;
  }
}

// Layer indices :
//
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+
//      |   TL0PICIDX   |  (non-flexible mode only)
//      +-+-+-+-+-+-+-+-+
//
void ParseLayerInfo(BitstreamReader& parser, RTPVideoHeaderVP9* vp9) {
  vp9->temporal_idx = parser.ReadBits(3);
  vp9->temporal_up_switch = parser.Read<bool>();
  vp9->spatial_idx = parser.ReadBits(3);
  vp9->inter_layer_predicted = parser.Read<bool>();
  if (vp9->spatial_idx >= kMaxSpatialLayers) {
    parser.Invalidate();
    return;
  }

  if (!vp9->flexible_mode) {
    vp9->tl0_pic_idx = parser.Read<uint8_t>();
  }
}

// Reference indices:
//
//      +-+-+-+-+-+-+-+-+                P=1,F=1: At least one reference index
// P,F: | P_DIFF      |N|  up to 3 times          has to be specified.
//      +-+-+-+-+-+-+-+-+                    N=1: An additional P_DIFF follows
//                                                current P_DIFF.
//
void ParseRefIndices(BitstreamReader& parser, RTPVideoHeaderVP9* vp9) {
  if (vp9->picture_id == kNoPictureId) {
    parser.Invalidate();
    return;
  }

  vp9->num_ref_pics = 0;
  bool n_bit;
  do {
    if (vp9->num_ref_pics == kMaxVp9RefPics) {
      parser.Invalidate();
      return;
    }

    uint8_t p_diff = parser.ReadBits(7);
    n_bit = parser.Read<bool>();

    vp9->pid_diff[vp9->num_ref_pics] = p_diff;
    uint32_t scaled_pid = vp9->picture_id;
    if (p_diff > scaled_pid) {
      // TODO(asapersson): Max should correspond to the picture id of last wrap.
      scaled_pid += vp9->max_picture_id + 1;
    }
    vp9->ref_picture_id[vp9->num_ref_pics++] = scaled_pid - p_diff;
  } while (n_bit);
}

// Scalability structure (SS).
//
//      +-+-+-+-+-+-+-+-+
// V:   | N_S |Y|G|-|-|-|
//      +-+-+-+-+-+-+-+-+              -|
// Y:   |     WIDTH     | (OPTIONAL)    .
//      +               +               .
//      |               | (OPTIONAL)    .
//      +-+-+-+-+-+-+-+-+               . N_S + 1 times
//      |     HEIGHT    | (OPTIONAL)    .
//      +               +               .
//      |               | (OPTIONAL)    .
//      +-+-+-+-+-+-+-+-+              -|
// G:   |      N_G      | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+                           -|
// N_G: |  T  |U| R |-|-| (OPTIONAL)                 .
//      +-+-+-+-+-+-+-+-+              -|            . N_G times
//      |    P_DIFF     | (OPTIONAL)    . R times    .
//      +-+-+-+-+-+-+-+-+              -|           -|
//
void ParseSsData(BitstreamReader& parser, RTPVideoHeaderVP9* vp9) {
  vp9->num_spatial_layers = parser.ReadBits(3) + 1;
  vp9->spatial_layer_resolution_present = parser.Read<bool>();
  bool g_bit = parser.Read<bool>();
  parser.ConsumeBits(3);
  vp9->gof.num_frames_in_gof = 0;

  if (vp9->spatial_layer_resolution_present) {
    for (size_t i = 0; i < vp9->num_spatial_layers; ++i) {
      vp9->width[i] = parser.Read<uint16_t>();
      vp9->height[i] = parser.Read<uint16_t>();
    }
  }
  if (g_bit) {
    vp9->gof.num_frames_in_gof = parser.Read<uint8_t>();
  }
  for (size_t i = 0; i < vp9->gof.num_frames_in_gof; ++i) {
    vp9->gof.temporal_idx[i] = parser.ReadBits(3);
    vp9->gof.temporal_up_switch[i] = parser.Read<bool>();
    vp9->gof.num_ref_pics[i] = parser.ReadBits(2);
    parser.ConsumeBits(2);

    for (uint8_t p = 0; p < vp9->gof.num_ref_pics[i]; ++p) {
      vp9->gof.pid_diff[i][p] = parser.Read<uint8_t>();
    }
  }
}
}  // namespace

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerVp9::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  absl::optional<ParsedRtpPayload> result(absl::in_place);
  int offset = ParseRtpPayload(rtp_payload, &result->video_header);
  if (offset == 0)
    return absl::nullopt;
  RTC_DCHECK_LT(offset, rtp_payload.size());
  result->video_payload =
      rtp_payload.Slice(offset, rtp_payload.size() - offset);
  return result;
}

int VideoRtpDepacketizerVp9::ParseRtpPayload(
    rtc::ArrayView<const uint8_t> rtp_payload,
    RTPVideoHeader* video_header) {
  RTC_DCHECK(video_header);
  // Parse mandatory first byte of payload descriptor.
  BitstreamReader parser(rtp_payload);
  uint8_t first_byte = parser.Read<uint8_t>();
  bool i_bit = first_byte & 0b1000'0000;  // PictureId present .
  bool p_bit = first_byte & 0b0100'0000;  // Inter-picture predicted.
  bool l_bit = first_byte & 0b0010'0000;  // Layer indices present.
  bool f_bit = first_byte & 0b0001'0000;  // Flexible mode.
  bool b_bit = first_byte & 0b0000'1000;  // Begins frame flag.
  bool e_bit = first_byte & 0b0000'0100;  // Ends frame flag.
  bool v_bit = first_byte & 0b0000'0010;  // Scalability structure present.
  bool z_bit = first_byte & 0b0000'0001;  // Not used for inter-layer prediction

  // Parsed payload.
  video_header->width = 0;
  video_header->height = 0;
  video_header->simulcastIdx = 0;
  video_header->codec = kVideoCodecVP9;

  video_header->frame_type =
      p_bit ? VideoFrameType::kVideoFrameDelta : VideoFrameType::kVideoFrameKey;

  auto& vp9_header =
      video_header->video_type_header.emplace<RTPVideoHeaderVP9>();
  vp9_header.InitRTPVideoHeaderVP9();
  vp9_header.inter_pic_predicted = p_bit;
  vp9_header.flexible_mode = f_bit;
  vp9_header.beginning_of_frame = b_bit;
  vp9_header.end_of_frame = e_bit;
  vp9_header.ss_data_available = v_bit;
  vp9_header.non_ref_for_inter_layer_pred = z_bit;

  // Parse fields that are present.
  if (i_bit) {
    ParsePictureId(parser, &vp9_header);
  }
  if (l_bit) {
    ParseLayerInfo(parser, &vp9_header);
  }
  if (p_bit && f_bit) {
    ParseRefIndices(parser, &vp9_header);
  }
  if (v_bit) {
    ParseSsData(parser, &vp9_header);
    if (vp9_header.spatial_layer_resolution_present) {
      // TODO(asapersson): Add support for spatial layers.
      video_header->width = vp9_header.width[0];
      video_header->height = vp9_header.height[0];
    }
  }
  video_header->is_first_packet_in_frame = b_bit;
  video_header->is_last_packet_in_frame = e_bit;

  int num_remaining_bits = parser.RemainingBitCount();
  if (num_remaining_bits <= 0) {
    // Failed to parse or empty vp9 payload data.
    return 0;
  }
  // vp9 descriptor is byte aligned.
  RTC_DCHECK_EQ(num_remaining_bits % 8, 0);
  return rtp_payload.size() - num_remaining_bits / 8;
}
}  // namespace webrtc
