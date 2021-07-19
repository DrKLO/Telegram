/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_format_vp9.h"

#include <string.h>

#include "api/video/video_codec_constants.h"
#include "modules/rtp_rtcp/source/rtp_packet_to_send.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp9.h"
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
// Length of VP9 payload descriptors' fixed part.
const size_t kFixedPayloadDescriptorBytes = 1;

const uint32_t kReservedBitValue0 = 0;

uint8_t TemporalIdxField(const RTPVideoHeaderVP9& hdr, uint8_t def) {
  return (hdr.temporal_idx == kNoTemporalIdx) ? def : hdr.temporal_idx;
}

uint8_t SpatialIdxField(const RTPVideoHeaderVP9& hdr, uint8_t def) {
  return (hdr.spatial_idx == kNoSpatialIdx) ? def : hdr.spatial_idx;
}

int16_t Tl0PicIdxField(const RTPVideoHeaderVP9& hdr, uint8_t def) {
  return (hdr.tl0_pic_idx == kNoTl0PicIdx) ? def : hdr.tl0_pic_idx;
}

// Picture ID:
//
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  |   M:0 => picture id is 7 bits.
//      +-+-+-+-+-+-+-+-+   M:1 => picture id is 15 bits.
// M:   | EXTENDED PID  |
//      +-+-+-+-+-+-+-+-+
//
size_t PictureIdLength(const RTPVideoHeaderVP9& hdr) {
  if (hdr.picture_id == kNoPictureId)
    return 0;
  return (hdr.max_picture_id == kMaxOneBytePictureId) ? 1 : 2;
}

bool PictureIdPresent(const RTPVideoHeaderVP9& hdr) {
  return PictureIdLength(hdr) > 0;
}

// Layer indices:
//
// Flexible mode (F=1):     Non-flexible mode (F=0):
//
//      +-+-+-+-+-+-+-+-+   +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+   +-+-+-+-+-+-+-+-+
//                          |   TL0PICIDX   |
//                          +-+-+-+-+-+-+-+-+
//
size_t LayerInfoLength(const RTPVideoHeaderVP9& hdr) {
  if (hdr.temporal_idx == kNoTemporalIdx && hdr.spatial_idx == kNoSpatialIdx) {
    return 0;
  }
  return hdr.flexible_mode ? 1 : 2;
}

bool LayerInfoPresent(const RTPVideoHeaderVP9& hdr) {
  return LayerInfoLength(hdr) > 0;
}

// Reference indices:
//
//      +-+-+-+-+-+-+-+-+                P=1,F=1: At least one reference index
// P,F: | P_DIFF      |N|  up to 3 times          has to be specified.
//      +-+-+-+-+-+-+-+-+                    N=1: An additional P_DIFF follows
//                                                current P_DIFF.
//
size_t RefIndicesLength(const RTPVideoHeaderVP9& hdr) {
  if (!hdr.inter_pic_predicted || !hdr.flexible_mode)
    return 0;

  RTC_DCHECK_GT(hdr.num_ref_pics, 0U);
  RTC_DCHECK_LE(hdr.num_ref_pics, kMaxVp9RefPics);
  return hdr.num_ref_pics;
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
size_t SsDataLength(const RTPVideoHeaderVP9& hdr) {
  if (!hdr.ss_data_available)
    return 0;

  RTC_DCHECK_GT(hdr.num_spatial_layers, 0U);
  RTC_DCHECK_LE(hdr.num_spatial_layers, kMaxVp9NumberOfSpatialLayers);
  RTC_DCHECK_LE(hdr.gof.num_frames_in_gof, kMaxVp9FramesInGof);
  size_t length = 1;  // V
  if (hdr.spatial_layer_resolution_present) {
    length += 4 * hdr.num_spatial_layers;  // Y
  }
  if (hdr.gof.num_frames_in_gof > 0) {
    ++length;  // G
  }
  // N_G
  length += hdr.gof.num_frames_in_gof;  // T, U, R
  for (size_t i = 0; i < hdr.gof.num_frames_in_gof; ++i) {
    RTC_DCHECK_LE(hdr.gof.num_ref_pics[i], kMaxVp9RefPics);
    length += hdr.gof.num_ref_pics[i];  // R times
  }
  return length;
}

size_t PayloadDescriptorLengthMinusSsData(const RTPVideoHeaderVP9& hdr) {
  return kFixedPayloadDescriptorBytes + PictureIdLength(hdr) +
         LayerInfoLength(hdr) + RefIndicesLength(hdr);
}

// Picture ID:
//
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  |   M:0 => picture id is 7 bits.
//      +-+-+-+-+-+-+-+-+   M:1 => picture id is 15 bits.
// M:   | EXTENDED PID  |
//      +-+-+-+-+-+-+-+-+
//
bool WritePictureId(const RTPVideoHeaderVP9& vp9,
                    rtc::BitBufferWriter* writer) {
  bool m_bit = (PictureIdLength(vp9) == 2);
  RETURN_FALSE_ON_ERROR(writer->WriteBits(m_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.picture_id, m_bit ? 15 : 7));
  return true;
}

// Layer indices:
//
// Flexible mode (F=1):
//
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+
//
bool WriteLayerInfoCommon(const RTPVideoHeaderVP9& vp9,
                          rtc::BitBufferWriter* writer) {
  RETURN_FALSE_ON_ERROR(writer->WriteBits(TemporalIdxField(vp9, 0), 3));
  RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.temporal_up_switch ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer->WriteBits(SpatialIdxField(vp9, 0), 3));
  RETURN_FALSE_ON_ERROR(
      writer->WriteBits(vp9.inter_layer_predicted ? 1 : 0, 1));
  return true;
}

// Non-flexible mode (F=0):
//
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D|
//      +-+-+-+-+-+-+-+-+
//      |   TL0PICIDX   |
//      +-+-+-+-+-+-+-+-+
//
bool WriteLayerInfoNonFlexibleMode(const RTPVideoHeaderVP9& vp9,
                                   rtc::BitBufferWriter* writer) {
  RETURN_FALSE_ON_ERROR(writer->WriteUInt8(Tl0PicIdxField(vp9, 0)));
  return true;
}

bool WriteLayerInfo(const RTPVideoHeaderVP9& vp9,
                    rtc::BitBufferWriter* writer) {
  if (!WriteLayerInfoCommon(vp9, writer))
    return false;

  if (vp9.flexible_mode)
    return true;

  return WriteLayerInfoNonFlexibleMode(vp9, writer);
}

// Reference indices:
//
//      +-+-+-+-+-+-+-+-+                P=1,F=1: At least one reference index
// P,F: | P_DIFF      |N|  up to 3 times          has to be specified.
//      +-+-+-+-+-+-+-+-+                    N=1: An additional P_DIFF follows
//                                                current P_DIFF.
//
bool WriteRefIndices(const RTPVideoHeaderVP9& vp9,
                     rtc::BitBufferWriter* writer) {
  if (!PictureIdPresent(vp9) || vp9.num_ref_pics == 0 ||
      vp9.num_ref_pics > kMaxVp9RefPics) {
    return false;
  }
  for (uint8_t i = 0; i < vp9.num_ref_pics; ++i) {
    bool n_bit = !(i == vp9.num_ref_pics - 1);
    RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.pid_diff[i], 7));
    RETURN_FALSE_ON_ERROR(writer->WriteBits(n_bit ? 1 : 0, 1));
  }
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
bool WriteSsData(const RTPVideoHeaderVP9& vp9, rtc::BitBufferWriter* writer) {
  RTC_DCHECK_GT(vp9.num_spatial_layers, 0U);
  RTC_DCHECK_LE(vp9.num_spatial_layers, kMaxVp9NumberOfSpatialLayers);
  RTC_DCHECK_LE(vp9.gof.num_frames_in_gof, kMaxVp9FramesInGof);
  bool g_bit = vp9.gof.num_frames_in_gof > 0;

  RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.num_spatial_layers - 1, 3));
  RETURN_FALSE_ON_ERROR(
      writer->WriteBits(vp9.spatial_layer_resolution_present ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer->WriteBits(g_bit ? 1 : 0, 1));  // G
  RETURN_FALSE_ON_ERROR(writer->WriteBits(kReservedBitValue0, 3));

  if (vp9.spatial_layer_resolution_present) {
    for (size_t i = 0; i < vp9.num_spatial_layers; ++i) {
      RETURN_FALSE_ON_ERROR(writer->WriteUInt16(vp9.width[i]));
      RETURN_FALSE_ON_ERROR(writer->WriteUInt16(vp9.height[i]));
    }
  }
  if (g_bit) {
    RETURN_FALSE_ON_ERROR(writer->WriteUInt8(vp9.gof.num_frames_in_gof));
  }
  for (size_t i = 0; i < vp9.gof.num_frames_in_gof; ++i) {
    RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.gof.temporal_idx[i], 3));
    RETURN_FALSE_ON_ERROR(
        writer->WriteBits(vp9.gof.temporal_up_switch[i] ? 1 : 0, 1));
    RETURN_FALSE_ON_ERROR(writer->WriteBits(vp9.gof.num_ref_pics[i], 2));
    RETURN_FALSE_ON_ERROR(writer->WriteBits(kReservedBitValue0, 2));
    for (uint8_t r = 0; r < vp9.gof.num_ref_pics[i]; ++r) {
      RETURN_FALSE_ON_ERROR(writer->WriteUInt8(vp9.gof.pid_diff[i][r]));
    }
  }
  return true;
}

// TODO(https://bugs.webrtc.org/11319):
// Workaround for switching off spatial layers on the fly.
// Sent layers must start from SL0 on RTP layer, but can start from any
// spatial layer because WebRTC-SVC api isn't implemented yet and
// current API to invoke SVC is not flexible enough.
RTPVideoHeaderVP9 RemoveInactiveSpatialLayers(
    const RTPVideoHeaderVP9& original_header) {
  RTPVideoHeaderVP9 hdr(original_header);
  if (original_header.first_active_layer == 0)
    return hdr;
  for (size_t i = hdr.first_active_layer; i < hdr.num_spatial_layers; ++i) {
    hdr.width[i - hdr.first_active_layer] = hdr.width[i];
    hdr.height[i - hdr.first_active_layer] = hdr.height[i];
  }
  for (size_t i = hdr.num_spatial_layers - hdr.first_active_layer;
       i < hdr.num_spatial_layers; ++i) {
    hdr.width[i] = 0;
    hdr.height[i] = 0;
  }
  hdr.num_spatial_layers -= hdr.first_active_layer;
  hdr.spatial_idx -= hdr.first_active_layer;
  hdr.first_active_layer = 0;
  return hdr;
}
}  // namespace

RtpPacketizerVp9::RtpPacketizerVp9(rtc::ArrayView<const uint8_t> payload,
                                   PayloadSizeLimits limits,
                                   const RTPVideoHeaderVP9& hdr)
    : hdr_(RemoveInactiveSpatialLayers(hdr)),
      header_size_(PayloadDescriptorLengthMinusSsData(hdr_)),
      first_packet_extra_header_size_(SsDataLength(hdr_)),
      remaining_payload_(payload) {
  RTC_DCHECK_EQ(hdr_.first_active_layer, 0);

  limits.max_payload_len -= header_size_;
  limits.first_packet_reduction_len += first_packet_extra_header_size_;
  limits.single_packet_reduction_len += first_packet_extra_header_size_;

  payload_sizes_ = SplitAboutEqually(payload.size(), limits);
  current_packet_ = payload_sizes_.begin();
}

RtpPacketizerVp9::~RtpPacketizerVp9() = default;

size_t RtpPacketizerVp9::NumPackets() const {
  return payload_sizes_.end() - current_packet_;
}

bool RtpPacketizerVp9::NextPacket(RtpPacketToSend* packet) {
  RTC_DCHECK(packet);
  if (current_packet_ == payload_sizes_.end()) {
    return false;
  }

  bool layer_begin = current_packet_ == payload_sizes_.begin();
  int packet_payload_len = *current_packet_;
  ++current_packet_;
  bool layer_end = current_packet_ == payload_sizes_.end();

  int header_size = header_size_;
  if (layer_begin)
    header_size += first_packet_extra_header_size_;

  uint8_t* buffer = packet->AllocatePayload(header_size + packet_payload_len);
  RTC_CHECK(buffer);

  if (!WriteHeader(layer_begin, layer_end,
                   rtc::MakeArrayView(buffer, header_size)))
    return false;

  memcpy(buffer + header_size, remaining_payload_.data(), packet_payload_len);
  remaining_payload_ = remaining_payload_.subview(packet_payload_len);

  // Ensure end_of_picture is always set on top spatial layer when it is not
  // dropped.
  RTC_DCHECK(hdr_.spatial_idx < hdr_.num_spatial_layers - 1 ||
             hdr_.end_of_picture);

  packet->SetMarker(layer_end && hdr_.end_of_picture);
  return true;
}

// VP9 format:
//
// Payload descriptor for F = 1 (flexible mode)
//       0 1 2 3 4 5 6 7
//      +-+-+-+-+-+-+-+-+
//      |I|P|L|F|B|E|V|Z| (REQUIRED)
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  | (RECOMMENDED)
//      +-+-+-+-+-+-+-+-+
// M:   | EXTENDED PID  | (RECOMMENDED)
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
//      +-+-+-+-+-+-+-+-+                             -|
// P,F: | P_DIFF      |N| (CONDITIONALLY RECOMMENDED)  . up to 3 times
//      +-+-+-+-+-+-+-+-+                             -|
// V:   | SS            |
//      | ..            |
//      +-+-+-+-+-+-+-+-+
//
// Payload descriptor for F = 0 (non-flexible mode)
//       0 1 2 3 4 5 6 7
//      +-+-+-+-+-+-+-+-+
//      |I|P|L|F|B|E|V|Z| (REQUIRED)
//      +-+-+-+-+-+-+-+-+
// I:   |M| PICTURE ID  | (RECOMMENDED)
//      +-+-+-+-+-+-+-+-+
// M:   | EXTENDED PID  | (RECOMMENDED)
//      +-+-+-+-+-+-+-+-+
// L:   |  T  |U|  S  |D| (CONDITIONALLY RECOMMENDED)
//      +-+-+-+-+-+-+-+-+
//      |   TL0PICIDX   | (CONDITIONALLY REQUIRED)
//      +-+-+-+-+-+-+-+-+
// V:   | SS            |
//      | ..            |
//      +-+-+-+-+-+-+-+-+
bool RtpPacketizerVp9::WriteHeader(bool layer_begin,
                                   bool layer_end,
                                   rtc::ArrayView<uint8_t> buffer) const {
  // Required payload descriptor byte.
  bool i_bit = PictureIdPresent(hdr_);
  bool p_bit = hdr_.inter_pic_predicted;
  bool l_bit = LayerInfoPresent(hdr_);
  bool f_bit = hdr_.flexible_mode;
  bool b_bit = layer_begin;
  bool e_bit = layer_end;
  bool v_bit = hdr_.ss_data_available && b_bit;
  bool z_bit = hdr_.non_ref_for_inter_layer_pred;

  rtc::BitBufferWriter writer(buffer.data(), buffer.size());
  RETURN_FALSE_ON_ERROR(writer.WriteBits(i_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(p_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(l_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(f_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(b_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(e_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(v_bit ? 1 : 0, 1));
  RETURN_FALSE_ON_ERROR(writer.WriteBits(z_bit ? 1 : 0, 1));

  // Add fields that are present.
  if (i_bit && !WritePictureId(hdr_, &writer)) {
    RTC_LOG(LS_ERROR) << "Failed writing VP9 picture id.";
    return false;
  }
  if (l_bit && !WriteLayerInfo(hdr_, &writer)) {
    RTC_LOG(LS_ERROR) << "Failed writing VP9 layer info.";
    return false;
  }
  if (p_bit && f_bit && !WriteRefIndices(hdr_, &writer)) {
    RTC_LOG(LS_ERROR) << "Failed writing VP9 ref indices.";
    return false;
  }
  if (v_bit && !WriteSsData(hdr_, &writer)) {
    RTC_LOG(LS_ERROR) << "Failed writing VP9 SS data.";
    return false;
  }

  size_t offset_bytes = 0;
  size_t offset_bits = 0;
  writer.GetCurrentOffset(&offset_bytes, &offset_bits);
  RTC_DCHECK_EQ(offset_bits, 0);
  RTC_DCHECK_EQ(offset_bytes, buffer.size());
  return true;
}

}  // namespace webrtc
