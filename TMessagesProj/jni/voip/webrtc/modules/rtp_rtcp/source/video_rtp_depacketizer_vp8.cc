/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_vp8.h"

#include <stddef.h>
#include <stdint.h>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

// VP8 payload descriptor
// https://datatracker.ietf.org/doc/html/rfc7741#section-4.2
//
//       0 1 2 3 4 5 6 7
//      +-+-+-+-+-+-+-+-+
//      |X|R|N|S|R| PID | (REQUIRED)
//      +-+-+-+-+-+-+-+-+
// X:   |I|L|T|K| RSV   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
// I:   |M| PictureID   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
//      |   PictureID   |
//      +-+-+-+-+-+-+-+-+
// L:   |   TL0PICIDX   | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
// T/K: |TID|Y| KEYIDX  | (OPTIONAL)
//      +-+-+-+-+-+-+-+-+
//
// VP8 payload header. Considered part of the actual payload, sent to decoder.
// https://datatracker.ietf.org/doc/html/rfc7741#section-4.3
//
//       0 1 2 3 4 5 6 7
//      +-+-+-+-+-+-+-+-+
//      |Size0|H| VER |P|
//      +-+-+-+-+-+-+-+-+
//      :      ...      :
//      +-+-+-+-+-+-+-+-+

namespace webrtc {
namespace {

constexpr int kFailedToParse = 0;

int ParseVP8Descriptor(RTPVideoHeaderVP8* vp8,
                       const uint8_t* data,
                       size_t data_length) {
  RTC_DCHECK_GT(data_length, 0);
  int parsed_bytes = 0;
  // Parse mandatory first byte of payload descriptor.
  bool extension = (*data & 0x80) ? true : false;             // X bit
  vp8->nonReference = (*data & 0x20) ? true : false;          // N bit
  vp8->beginningOfPartition = (*data & 0x10) ? true : false;  // S bit
  vp8->partitionId = (*data & 0x07);                          // PID field

  data++;
  parsed_bytes++;
  data_length--;

  if (!extension)
    return parsed_bytes;

  if (data_length == 0)
    return kFailedToParse;
  // Optional X field is present.
  bool has_picture_id = (*data & 0x80) ? true : false;   // I bit
  bool has_tl0_pic_idx = (*data & 0x40) ? true : false;  // L bit
  bool has_tid = (*data & 0x20) ? true : false;          // T bit
  bool has_key_idx = (*data & 0x10) ? true : false;      // K bit

  // Advance data and decrease remaining payload size.
  data++;
  parsed_bytes++;
  data_length--;

  if (has_picture_id) {
    if (data_length == 0)
      return kFailedToParse;

    vp8->pictureId = (*data & 0x7F);
    if (*data & 0x80) {
      data++;
      parsed_bytes++;
      if (--data_length == 0)
        return kFailedToParse;
      // PictureId is 15 bits
      vp8->pictureId = (vp8->pictureId << 8) + *data;
    }
    data++;
    parsed_bytes++;
    data_length--;
  }

  if (has_tl0_pic_idx) {
    if (data_length == 0)
      return kFailedToParse;

    vp8->tl0PicIdx = *data;
    data++;
    parsed_bytes++;
    data_length--;
  }

  if (has_tid || has_key_idx) {
    if (data_length == 0)
      return kFailedToParse;

    if (has_tid) {
      vp8->temporalIdx = ((*data >> 6) & 0x03);
      vp8->layerSync = (*data & 0x20) ? true : false;  // Y bit
    }
    if (has_key_idx) {
      vp8->keyIdx = *data & 0x1F;
    }
    data++;
    parsed_bytes++;
    data_length--;
  }
  return parsed_bytes;
}

}  // namespace

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerVp8::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
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

int VideoRtpDepacketizerVp8::ParseRtpPayload(
    rtc::ArrayView<const uint8_t> rtp_payload,
    RTPVideoHeader* video_header) {
  RTC_DCHECK(video_header);
  if (rtp_payload.empty()) {
    RTC_LOG(LS_ERROR) << "Empty rtp payload.";
    return kFailedToParse;
  }

  video_header->simulcastIdx = 0;
  video_header->codec = kVideoCodecVP8;
  auto& vp8_header =
      video_header->video_type_header.emplace<RTPVideoHeaderVP8>();
  vp8_header.InitRTPVideoHeaderVP8();

  const int descriptor_size =
      ParseVP8Descriptor(&vp8_header, rtp_payload.data(), rtp_payload.size());
  if (descriptor_size == kFailedToParse)
    return kFailedToParse;

  RTC_DCHECK_LT(vp8_header.partitionId, 8);

  video_header->is_first_packet_in_frame =
      vp8_header.beginningOfPartition && vp8_header.partitionId == 0;

  int vp8_payload_size = rtp_payload.size() - descriptor_size;
  if (vp8_payload_size == 0) {
    RTC_LOG(LS_WARNING) << "Empty vp8 payload.";
    return kFailedToParse;
  }
  const uint8_t* vp8_payload = rtp_payload.data() + descriptor_size;

  // Read P bit from payload header (only at beginning of first partition).
  if (video_header->is_first_packet_in_frame && (*vp8_payload & 0x01) == 0) {
    video_header->frame_type = VideoFrameType::kVideoFrameKey;

    if (vp8_payload_size < 10) {
      // For an I-frame we should always have the uncompressed VP8 header
      // in the beginning of the partition.
      return kFailedToParse;
    }
    video_header->width = ((vp8_payload[7] << 8) + vp8_payload[6]) & 0x3FFF;
    video_header->height = ((vp8_payload[9] << 8) + vp8_payload[8]) & 0x3FFF;
  } else {
    video_header->frame_type = VideoFrameType::kVideoFrameDelta;

    video_header->width = 0;
    video_header->height = 0;
  }

  return descriptor_size;
}

}  // namespace webrtc
