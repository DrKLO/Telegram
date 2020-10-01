/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/video_rtp_depacketizer_h265.h"

#include <cstddef>
#include <cstdint>
#include <utility>
#include <vector>

#include "absl/base/macros.h"
#include "absl/types/optional.h"
#include "absl/types/variant.h"
#include "common_video/h264/h264_common.h"
#include "common_video/h265/h265_common.h"
#include "common_video/h265/h265_pps_parser.h"
#include "common_video/h265/h265_sps_parser.h"
#include "common_video/h265/h265_vps_parser.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/video_rtp_depacketizer.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

enum NaluType {
  kTrailN = 0,
  kTrailR = 1,
  kTsaN = 2,
  kTsaR = 3,
  kStsaN = 4,
  kStsaR = 5,
  kRadlN = 6,
  kRadlR = 7,
  kBlaWLp = 16,
  kBlaWRadl = 17,
  kBlaNLp = 18,
  kIdrWRadl = 19,
  kIdrNLp = 20,
  kCra = 21,
  kVps = 32,
  kHevcSps = 33,
  kHevcPps = 34,
  kHevcAud = 35,
  kPrefixSei = 39,
  kSuffixSei = 40,
  kHevcAp = 48,
  kHevcFu = 49
};

/*
   0                   1                   2                   3
   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
  |    PayloadHdr (Type=49)       |   FU header   | DONL (cond)   |
  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-|
*/
// Unlike H.264, HEVC NAL header is 2-bytes.
static const size_t kHevcNalHeaderSize = 2;
// H.265's FU is constructed of 2-byte payload header, and 1-byte FU header
static const size_t kHevcFuHeaderSize = 1;
static const size_t kHevcLengthFieldSize = 2;
static const size_t kHevcApHeaderSize =
    kHevcNalHeaderSize + kHevcLengthFieldSize;

enum HevcNalHdrMasks {
  kHevcFBit = 0x80,
  kHevcTypeMask = 0x7E,
  kHevcLayerIDHMask = 0x1,
  kHevcLayerIDLMask = 0xF8,
  kHevcTIDMask = 0x7,
  kHevcTypeMaskN = 0x81,
  kHevcTypeMaskInFuHeader = 0x3F
};

// Bit masks for FU headers.
enum HevcFuDefs { kHevcSBit = 0x80, kHevcEBit = 0x40, kHevcFuTypeBit = 0x3F };

// TODO(pbos): Avoid parsing this here as well as inside the jitter buffer.
bool ParseApStartOffsets(const uint8_t* nalu_ptr,
                         size_t length_remaining,
                         std::vector<size_t>* offsets) {
  size_t offset = 0;
  while (length_remaining > 0) {
    // Buffer doesn't contain room for additional nalu length.
    if (length_remaining < sizeof(uint16_t))
      return false;
    uint16_t nalu_size = ByteReader<uint16_t>::ReadBigEndian(nalu_ptr);
    nalu_ptr += sizeof(uint16_t);
    length_remaining -= sizeof(uint16_t);
    if (nalu_size > length_remaining)
      return false;
    nalu_ptr += nalu_size;
    length_remaining -= nalu_size;

    offsets->push_back(offset + kHevcApHeaderSize);
    offset += kHevcLengthFieldSize + nalu_size;
  }
  return true;
}

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> ProcessApOrSingleNalu(
    rtc::CopyOnWriteBuffer rtp_payload) {
  const uint8_t* const payload_data = rtp_payload.cdata();
  absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> parsed_payload(
      absl::in_place);
  parsed_payload->video_payload = rtp_payload;
  parsed_payload->video_header.width = 0;
  parsed_payload->video_header.height = 0;
  parsed_payload->video_header.codec = kVideoCodecH265;
  parsed_payload->video_header.is_first_packet_in_frame = true;
  auto& h265_header = parsed_payload->video_header.video_type_header
                          .emplace<RTPVideoHeaderH265>();

  const uint8_t* nalu_start = payload_data + kHevcNalHeaderSize;
  const size_t nalu_length = rtp_payload.size() - kHevcNalHeaderSize;
  uint8_t nal_type = (payload_data[0] & kHevcTypeMask) >> 1;
  std::vector<size_t> nalu_start_offsets;
  if (nal_type == H265::NaluType::kAP) {
    // Skip the StapA header (StapA NAL type + length).
    if (rtp_payload.size() <= kHevcApHeaderSize) {
      RTC_LOG(LS_ERROR) << "AP header truncated.";
      return absl::nullopt;
    }

    if (!ParseApStartOffsets(nalu_start, nalu_length, &nalu_start_offsets)) {
      RTC_LOG(LS_ERROR) << "AP packet with incorrect NALU packet lengths.";
      return absl::nullopt;
    }

    h265_header.packetization_type = kH265AP;
    // nal_type = (payload_data[kHevcApHeaderSize] & kHevcTypeMask) >> 1;
  } else {
    h265_header.packetization_type = kH265SingleNalu;
    nalu_start_offsets.push_back(0);
  }
  h265_header.nalu_type = nal_type;
  parsed_payload->video_header.frame_type = VideoFrameType::kVideoFrameDelta;

  nalu_start_offsets.push_back(rtp_payload.size() + kHevcLengthFieldSize);  // End offset.
  for (size_t i = 0; i < nalu_start_offsets.size() - 1; ++i) {
    size_t start_offset = nalu_start_offsets[i];
    // End offset is actually start offset for next unit, excluding length field
    // so remove that from this units length.
    size_t end_offset = nalu_start_offsets[i + 1] - kHevcLengthFieldSize;
    if (end_offset - start_offset < kHevcNalHeaderSize) {  // Same as H.264.
      RTC_LOG(LS_ERROR) << "AP packet too short";
      return absl::nullopt;
    }

    H265NaluInfo nalu;
    nalu.type = (payload_data[start_offset] & kHevcTypeMask) >> 1;
    nalu.vps_id = -1;
    nalu.sps_id = -1;
    nalu.pps_id = -1;
    start_offset += kHevcNalHeaderSize;
    switch (nalu.type) {
      case H265::NaluType::kVps: {
        absl::optional<H265VpsParser::VpsState> vps = H265VpsParser::ParseVps(
            &payload_data[start_offset], end_offset - start_offset);
        if (vps) {
          nalu.vps_id = vps->id;
        } else {
          RTC_LOG(LS_WARNING) << "Failed to parse VPS id from VPS slice.";
        }
        break;
      }
      case H265::NaluType::kSps: {
        // TODO: Check if VUI is present in SPS and if it needs to be modified to
        // avoid excessive decoder latency.

        // Copy any previous data first (likely just the first header).
        std::unique_ptr<rtc::Buffer> output_buffer(new rtc::Buffer());
        if (start_offset)
          output_buffer->AppendData(payload_data, start_offset);

        absl::optional<H265SpsParser::SpsState> sps = H265SpsParser::ParseSps(
            &payload_data[start_offset], end_offset - start_offset);

        if (sps) {
          parsed_payload->video_header.width = sps->width;
          parsed_payload->video_header.height = sps->height;
          nalu.sps_id = sps->id;
          nalu.vps_id = sps->vps_id;
        } else {
          RTC_LOG(LS_WARNING)
              << "Failed to parse SPS and VPS id from SPS slice.";
        }
        parsed_payload->video_header.frame_type = VideoFrameType::kVideoFrameKey;
        break;
      }
      case H265::NaluType::kPps: {
        uint32_t pps_id;
        uint32_t sps_id;
        if (H265PpsParser::ParsePpsIds(&payload_data[start_offset],
                                       end_offset - start_offset, &pps_id,
                                       &sps_id)) {
          nalu.pps_id = pps_id;
          nalu.sps_id = sps_id;
        } else {
          RTC_LOG(LS_WARNING)
              << "Failed to parse PPS id and SPS id from PPS slice.";
        }
        break;
      }
      case H265::NaluType::kIdrWRadl:
      case H265::NaluType::kIdrNLp:
      case H265::NaluType::kCra:
        parsed_payload->video_header.frame_type =
		    VideoFrameType::kVideoFrameKey;
        ABSL_FALLTHROUGH_INTENDED;
      case H265::NaluType::kTrailN:
      case H265::NaluType::kTrailR: {
        absl::optional<uint32_t> pps_id =
            H265PpsParser::ParsePpsIdFromSliceSegmentLayerRbsp(
                &payload_data[start_offset], end_offset - start_offset,
                nalu.type);
        if (pps_id) {
          nalu.pps_id = *pps_id;
        } else {
          RTC_LOG(LS_WARNING) << "Failed to parse PPS id from slice of type: "
                              << static_cast<int>(nalu.type);
        }
        break;
      }
      // Slices below don't contain SPS or PPS ids.
      case H265::NaluType::kAud:
      case H265::NaluType::kTsaN:
      case H265::NaluType::kTsaR:
      case H265::NaluType::kStsaN:
      case H265::NaluType::kStsaR:
      case H265::NaluType::kRadlN:
      case H265::NaluType::kRadlR:
      case H265::NaluType::kBlaWLp:
      case H265::NaluType::kBlaWRadl:
      case H265::NaluType::kPrefixSei:
      case H265::NaluType::kSuffixSei:
        break;
      case H265::NaluType::kAP:
      case H265::NaluType::kFU:
        RTC_LOG(LS_WARNING) << "Unexpected AP or FU received.";
        return absl::nullopt;
    }

    if (h265_header.nalus_length == kMaxNalusPerPacket) {
      RTC_LOG(LS_WARNING)
          << "Received packet containing more than " << kMaxNalusPerPacket
          << " NAL units. Will not keep track sps and pps ids for all of them.";
    } else {
      h265_header.nalus[h265_header.nalus_length++] = nalu;
    }
  }
  return parsed_payload;
}

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> ParseFuNalu(
    rtc::CopyOnWriteBuffer rtp_payload) {
  if (rtp_payload.size() < kHevcFuHeaderSize + kHevcNalHeaderSize) {
    RTC_LOG(LS_ERROR) << "FU-A NAL units truncated.";
    return absl::nullopt;
  }
  absl::optional<VideoRtpDepacketizer::ParsedRtpPayload> parsed_payload(
      absl::in_place);
	  
  uint8_t f = rtp_payload.cdata()[0] & kHevcFBit;
  uint8_t layer_id_h = rtp_payload.cdata()[0] & kHevcLayerIDHMask;
  uint8_t layer_id_l_unshifted = rtp_payload.cdata()[1] & kHevcLayerIDLMask;
  uint8_t tid = rtp_payload.cdata()[1] & kHevcTIDMask;

  uint8_t original_nal_type = rtp_payload.cdata()[2] & kHevcTypeMaskInFuHeader;
  bool first_fragment = rtp_payload.cdata()[2] & kHevcSBit;
  H265NaluInfo nalu;
  nalu.type = original_nal_type;
  nalu.vps_id = -1;
  nalu.sps_id = -1;
  nalu.pps_id = -1;
  if (first_fragment) {
    absl::optional<uint32_t> pps_id =
        H265PpsParser::ParsePpsIdFromSliceSegmentLayerRbsp(
            rtp_payload.cdata() + kHevcNalHeaderSize + kHevcFuHeaderSize,
            rtp_payload.size() - kHevcFuHeaderSize, nalu.type);
    if (pps_id) {
      nalu.pps_id = *pps_id;
    } else {
      RTC_LOG(LS_WARNING)
          << "Failed to parse PPS from first fragment of FU NAL "
             "unit with original type: "
          << static_cast<int>(nalu.type);
    }
	rtp_payload =
	    rtp_payload.Slice(1, rtp_payload.size() - 1);
    rtp_payload[0] = f | original_nal_type << 1 | layer_id_h;
    rtp_payload[1] = layer_id_l_unshifted | tid;
	parsed_payload->video_payload = std::move(rtp_payload);
  } else {
	 parsed_payload->video_payload =
        rtp_payload.Slice(kHevcNalHeaderSize + kHevcFuHeaderSize,
		rtp_payload.size() - kHevcNalHeaderSize - kHevcFuHeaderSize);
  }

  if (original_nal_type == H265::NaluType::kIdrWRadl
      || original_nal_type == H265::NaluType::kIdrNLp
      || original_nal_type == H265::NaluType::kCra) {
    parsed_payload->video_header.frame_type = VideoFrameType::kVideoFrameKey;
  } else {
    parsed_payload->video_header.frame_type = VideoFrameType::kVideoFrameDelta;
  }
  parsed_payload->video_header.width = 0;
  parsed_payload->video_header.height = 0;
  parsed_payload->video_header.codec = kVideoCodecH265;
  parsed_payload->video_header.is_first_packet_in_frame = first_fragment;
  auto& h265_header = parsed_payload->video_header.video_type_header
                          .emplace<RTPVideoHeaderH265>();
  h265_header.packetization_type = kH265FU;
  h265_header.nalu_type = original_nal_type;
  if (first_fragment) {
    h265_header.nalus[h265_header.nalus_length] = nalu;
    h265_header.nalus_length = 1;
  }
  return parsed_payload;
}

} // namespace

absl::optional<VideoRtpDepacketizer::ParsedRtpPayload>
VideoRtpDepacketizerH265::Parse(rtc::CopyOnWriteBuffer rtp_payload) {
  if (rtp_payload.size() == 0) {
    RTC_LOG(LS_ERROR) << "Empty payload.";
    return absl::nullopt;
  }

  uint8_t nal_type = (rtp_payload.cdata()[0] & kHevcTypeMask) >> 1;

  if (nal_type == H265::NaluType::kFU) {
    // Fragmented NAL units (FU-A).
    return ParseFuNalu(std::move(rtp_payload));
  } else {
    // We handle STAP-A and single NALU's the same way here. The jitter buffer
    // will depacketize the STAP-A into NAL units later.
    // TODO(sprang): Parse STAP-A offsets here and store in fragmentation vec.
    return ProcessApOrSingleNalu(std::move(rtp_payload));
  }
}

}  // namespace webrtc
