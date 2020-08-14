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
#include "rtc_base/bit_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

#define RETURN_FALSE_ON_ERROR(x) \
  if (!(x)) {                    \
    return false;                \
  }

namespace webrtc {
namespace {

constexpr int kFailedToParse = 0;

// Picture ID:
//
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  |   M:0 => picture id is 7 bits.
//      +-+-+-+-+-+-+-+-+   M:1 => picture id is 15 bits.
// M:   | EXTENDED PID  |
//      +-+-+-+-+-+-+-+-+
//
bool ParsePictureId(rtc::BitBuffer* parser, RTPVideoHeaderVP9* vp9) {
  uint32_t picture_id;
  uint32_t m_bit;
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&m_bit, 1));
  if (m_bit) {
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&picture_id, 15));
    vp9->max_picture_id = kMaxTwoBytePictureId;
  } else {
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&picture_id, 7));
    vp9->max_picture_id = kMaxOneBytePictureId;
  }
  vp9->picture_id = picture_id;
  return true;
}

// Layer indices (flexible mode):
//
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+
//
bool ParseLayerInfoCommon(rtc::BitBuffer* parser, RTPVideoHeaderVP9* vp9) {
  uint32_t t, u_bit, s, d_bit;
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&t, 3));
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&u_bit, 1));
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&s, 3));
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&d_bit, 1));
  vp9->temporal_idx = t;
  vp9->temporal_up_switch = u_bit ? true : false;
  if (s >= kMaxSpatialLayers)
    return false;
  vp9->spatial_idx = s;
  vp9->inter_layer_predicted = d_bit ? true : false;
  return true;
}

// Layer indices (non-flexible mode):
//
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+
//      |   TL0PICIDX   |
//      +-+-+-+-+-+-+-+-+
//
bool ParseLayerInfoNonFlexibleMode(rtc::BitBuffer* parser,
                                   RTPVideoHeaderVP9* vp9) {
  uint8_t tl0picidx;
  RETURN_FALSE_ON_ERROR(parser->ReadUInt8(&tl0picidx));
  vp9->tl0_pic_idx = tl0picidx;
  return true;
}

bool ParseLayerInfo(rtc::BitBuffer* parser, RTPVideoHeaderVP9* vp9) {
  if (!ParseLayerInfoCommon(parser, vp9))
    return false;

  if (vp9->flexible_mode)
    return true;

  return ParseLayerInfoNonFlexibleMode(parser, vp9);
}

// Reference indices:
//
//      +-+-+-+-+-+-+-+-+                P=1,F=1: At least one reference index
// P,F: | P_DIFF      |N|  up to 3 times          has to be specified.
//      +-+-+-+-+-+-+-+-+                    N=1: An additional P_DIFF follows
//                                                current P_DIFF.
//
bool ParseRefIndices(rtc::BitBuffer* parser, RTPVideoHeaderVP9* vp9) {
  if (vp9->picture_id == kNoPictureId)
    return false;

  vp9->num_ref_pics = 0;
  uint32_t n_bit;
  do {
    if (vp9->num_ref_pics == kMaxVp9RefPics)
      return false;

    uint32_t p_diff;
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&p_diff, 7));
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&n_bit, 1));

    vp9->pid_diff[vp9->num_ref_pics] = p_diff;
    uint32_t scaled_pid = vp9->picture_id;
    if (p_diff > scaled_pid) {
      // TODO(asapersson): Max should correspond to the picture id of last wrap.
      scaled_pid += vp9->max_picture_id + 1;
    }
    vp9->ref_picture_id[vp9->num_ref_pics++] = scaled_pid - p_diff;
  } while (n_bit);

  return true;
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
bool ParseSsData(rtc::BitBuffer* parser, RTPVideoHeaderVP9* vp9) {
  uint32_t n_s, y_bit, g_bit;
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&n_s, 3));
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&y_bit, 1));
  RETURN_FALSE_ON_ERROR(parser->ReadBits(&g_bit, 1));
  RETURN_FALSE_ON_ERROR(parser->ConsumeBits(3));
  vp9->num_spatial_layers = n_s + 1;
  vp9->spatial_layer_resolution_present = y_bit ? true : false;
  vp9->gof.num_frames_in_gof = 0;

  if (y_bit) {
    for (size_t i = 0; i < vp9->num_spatial_layers; ++i) {
      RETURN_FALSE_ON_ERROR(parser->ReadUInt16(&vp9->width[i]));
      RETURN_FALSE_ON_ERROR(parser->ReadUInt16(&vp9->height[i]));
    }
  }
  if (g_bit) {
    uint8_t n_g;
    RETURN_FALSE_ON_ERROR(parser->ReadUInt8(&n_g));
    vp9->gof.num_frames_in_gof = n_g;
  }
  for (size_t i = 0; i < vp9->gof.num_frames_in_gof; ++i) {
    uint32_t t, u_bit, r;
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&t, 3));
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&u_bit, 1));
    RETURN_FALSE_ON_ERROR(parser->ReadBits(&r, 2));
    RETURN_FALSE_ON_ERROR(parser->ConsumeBits(2));
    vp9->gof.temporal_idx[i] = t;
    vp9->gof.temporal_up_switch[i] = u_bit ? true : false;
    vp9->gof.num_ref_pics[i] = r;

    for (uint8_t p = 0; p < vp9->gof.num_ref_pics[i]; ++p) {
      uint8_t p_diff;
      RETURN_FALSE_ON_ERROR(parser->ReadUInt8(&p_diff));
      vp9->gof.pid_diff[i][p] = p_diff;
    }
  }
  return true;
}
}  // namespace

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerVp9::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  rtc::ArrayView<const uint8_t> payload(rtp_payload.cdata(),
                                        rtp_payload.size());
  absl::optional<ParsedRtpPayload> result(absl::in_place);
  int offset = ParseRtpPayload(payload, &result->video_header);
  if (offset == kFailedToParse)
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
  rtc::BitBuffer parser(rtp_payload.data(), rtp_payload.size());
  uint8_t first_byte;
  if (!parser.ReadUInt8(&first_byte)) {
    RTC_LOG(LS_ERROR) << "Payload length is zero.";
    return kFailedToParse;
  }
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
  if (i_bit && !ParsePictureId(&parser, &vp9_header)) {
    RTC_LOG(LS_ERROR) << "Failed parsing VP9 picture id.";
    return kFailedToParse;
  }
  if (l_bit && !ParseLayerInfo(&parser, &vp9_header)) {
    RTC_LOG(LS_ERROR) << "Failed parsing VP9 layer info.";
    return kFailedToParse;
  }
  if (p_bit && f_bit && !ParseRefIndices(&parser, &vp9_header)) {
    RTC_LOG(LS_ERROR) << "Failed parsing VP9 ref indices.";
    return kFailedToParse;
  }
  if (v_bit) {
    if (!ParseSsData(&parser, &vp9_header)) {
      RTC_LOG(LS_ERROR) << "Failed parsing VP9 SS data.";
      return kFailedToParse;
    }
    if (vp9_header.spatial_layer_resolution_present) {
      // TODO(asapersson): Add support for spatial layers.
      video_header->width = vp9_header.width[0];
      video_header->height = vp9_header.height[0];
    }
  }
  video_header->is_first_packet_in_frame =
      b_bit && (!l_bit || !vp9_header.inter_layer_predicted);

  size_t byte_offset;
  size_t bit_offset;
  parser.GetCurrentOffset(&byte_offset, &bit_offset);
  RTC_DCHECK_EQ(bit_offset, 0);
  if (byte_offset == rtp_payload.size()) {
    // Empty vp9 payload data.
    return kFailedToParse;
  }

  return byte_offset;
}
}  // namespace webrtc
