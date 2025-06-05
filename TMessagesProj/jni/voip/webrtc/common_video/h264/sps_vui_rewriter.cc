/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#include "common_video/h264/sps_vui_rewriter.h"

#include <string.h>

#include <algorithm>
#include <cstdint>
#include <vector>

#include "api/video/color_space.h"
#include "common_video/h264/h264_common.h"
#include "common_video/h264/sps_parser.h"
#include "rtc_base/bit_buffer.h"
#include "rtc_base/bitstream_reader.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

// The maximum expected growth from adding a VUI to the SPS. It's actually
// closer to 24 or so, but better safe than sorry.
const size_t kMaxVuiSpsIncrease = 64;

const char* kSpsValidHistogramName = "WebRTC.Video.H264.SpsValid";
enum SpsValidEvent {
  kReceivedSpsVuiOk = 1,
  kReceivedSpsRewritten = 2,
  kReceivedSpsParseFailure = 3,
  kSentSpsPocOk = 4,
  kSentSpsVuiOk = 5,
  kSentSpsRewritten = 6,
  kSentSpsParseFailure = 7,
  kSpsRewrittenMax = 8
};

#define RETURN_FALSE_ON_FAIL(x)                                        \
  do {                                                                 \
    if (!(x)) {                                                        \
      RTC_LOG_F(LS_ERROR) << " (line:" << __LINE__ << ") FAILED: " #x; \
      return false;                                                    \
    }                                                                  \
  } while (0)

uint8_t CopyUInt8(BitstreamReader& source, rtc::BitBufferWriter& destination) {
  uint8_t tmp = source.Read<uint8_t>();
  if (!destination.WriteUInt8(tmp)) {
    source.Invalidate();
  }
  return tmp;
}

uint32_t CopyExpGolomb(BitstreamReader& source,
                       rtc::BitBufferWriter& destination) {
  uint32_t tmp = source.ReadExponentialGolomb();
  if (!destination.WriteExponentialGolomb(tmp)) {
    source.Invalidate();
  }
  return tmp;
}

uint32_t CopyBits(int bits,
                  BitstreamReader& source,
                  rtc::BitBufferWriter& destination) {
  RTC_DCHECK_GT(bits, 0);
  RTC_DCHECK_LE(bits, 32);
  uint64_t tmp = source.ReadBits(bits);
  if (!destination.WriteBits(tmp, bits)) {
    source.Invalidate();
  }
  return tmp;
}

bool CopyAndRewriteVui(const SpsParser::SpsState& sps,
                       BitstreamReader& source,
                       rtc::BitBufferWriter& destination,
                       const webrtc::ColorSpace* color_space,
                       SpsVuiRewriter::ParseResult& out_vui_rewritten);

void CopyHrdParameters(BitstreamReader& source,
                       rtc::BitBufferWriter& destination);
bool AddBitstreamRestriction(rtc::BitBufferWriter* destination,
                             uint32_t max_num_ref_frames);
bool IsDefaultColorSpace(const ColorSpace& color_space);
bool AddVideoSignalTypeInfo(rtc::BitBufferWriter& destination,
                            const ColorSpace& color_space);
bool CopyOrRewriteVideoSignalTypeInfo(
    BitstreamReader& source,
    rtc::BitBufferWriter& destination,
    const ColorSpace* color_space,
    SpsVuiRewriter::ParseResult& out_vui_rewritten);
bool CopyRemainingBits(BitstreamReader& source,
                       rtc::BitBufferWriter& destination);
}  // namespace

void SpsVuiRewriter::UpdateStats(ParseResult result, Direction direction) {
  switch (result) {
    case SpsVuiRewriter::ParseResult::kVuiRewritten:
      RTC_HISTOGRAM_ENUMERATION(
          kSpsValidHistogramName,
          direction == SpsVuiRewriter::Direction::kIncoming
              ? SpsValidEvent::kReceivedSpsRewritten
              : SpsValidEvent::kSentSpsRewritten,
          SpsValidEvent::kSpsRewrittenMax);
      break;
    case SpsVuiRewriter::ParseResult::kVuiOk:
      RTC_HISTOGRAM_ENUMERATION(
          kSpsValidHistogramName,
          direction == SpsVuiRewriter::Direction::kIncoming
              ? SpsValidEvent::kReceivedSpsVuiOk
              : SpsValidEvent::kSentSpsVuiOk,
          SpsValidEvent::kSpsRewrittenMax);
      break;
    case SpsVuiRewriter::ParseResult::kFailure:
      RTC_HISTOGRAM_ENUMERATION(
          kSpsValidHistogramName,
          direction == SpsVuiRewriter::Direction::kIncoming
              ? SpsValidEvent::kReceivedSpsParseFailure
              : SpsValidEvent::kSentSpsParseFailure,
          SpsValidEvent::kSpsRewrittenMax);
      break;
  }
}

SpsVuiRewriter::ParseResult SpsVuiRewriter::ParseAndRewriteSps(
    const uint8_t* buffer,
    size_t length,
    absl::optional<SpsParser::SpsState>* sps,
    const webrtc::ColorSpace* color_space,
    rtc::Buffer* destination) {
  // Create temporary RBSP decoded buffer of the payload (exlcuding the
  // leading nalu type header byte (the SpsParser uses only the payload).
  std::vector<uint8_t> rbsp_buffer = H264::ParseRbsp(buffer, length);
  BitstreamReader source_buffer(rbsp_buffer);
  absl::optional<SpsParser::SpsState> sps_state =
      SpsParser::ParseSpsUpToVui(source_buffer);
  if (!sps_state)
    return ParseResult::kFailure;

  *sps = sps_state;

  // We're going to completely muck up alignment, so we need a BitBufferWriter
  // to write with.
  rtc::Buffer out_buffer(length + kMaxVuiSpsIncrease);
  rtc::BitBufferWriter sps_writer(out_buffer.data(), out_buffer.size());

  // Check how far the SpsParser has read, and copy that data in bulk.
  RTC_DCHECK(source_buffer.Ok());
  size_t total_bit_offset =
      rbsp_buffer.size() * 8 - source_buffer.RemainingBitCount();
  size_t byte_offset = total_bit_offset / 8;
  size_t bit_offset = total_bit_offset % 8;
  memcpy(out_buffer.data(), rbsp_buffer.data(),
         byte_offset + (bit_offset > 0 ? 1 : 0));  // OK to copy the last bits.

  // SpsParser will have read the vui_params_present flag, which we want to
  // modify, so back off a bit;
  if (bit_offset == 0) {
    --byte_offset;
    bit_offset = 7;
  } else {
    --bit_offset;
  }
  sps_writer.Seek(byte_offset, bit_offset);

  ParseResult vui_updated;
  if (!CopyAndRewriteVui(*sps_state, source_buffer, sps_writer, color_space,
                         vui_updated)) {
    RTC_LOG(LS_ERROR) << "Failed to parse/copy SPS VUI.";
    return ParseResult::kFailure;
  }

  if (vui_updated == ParseResult::kVuiOk) {
    // No update necessary after all, just return.
    return vui_updated;
  }

  if (!CopyRemainingBits(source_buffer, sps_writer)) {
    RTC_LOG(LS_ERROR) << "Failed to parse/copy SPS VUI.";
    return ParseResult::kFailure;
  }

  // Pad up to next byte with zero bits.
  sps_writer.GetCurrentOffset(&byte_offset, &bit_offset);
  if (bit_offset > 0) {
    sps_writer.WriteBits(0, 8 - bit_offset);
    ++byte_offset;
    bit_offset = 0;
  }

  RTC_DCHECK(byte_offset <= length + kMaxVuiSpsIncrease);
  RTC_CHECK(destination != nullptr);

  out_buffer.SetSize(byte_offset);

  // Write updates SPS to destination with added RBSP
  H264::WriteRbsp(out_buffer.data(), out_buffer.size(), destination);

  return ParseResult::kVuiRewritten;
}

SpsVuiRewriter::ParseResult SpsVuiRewriter::ParseAndRewriteSps(
    const uint8_t* buffer,
    size_t length,
    absl::optional<SpsParser::SpsState>* sps,
    const webrtc::ColorSpace* color_space,
    rtc::Buffer* destination,
    Direction direction) {
  ParseResult result =
      ParseAndRewriteSps(buffer, length, sps, color_space, destination);
  UpdateStats(result, direction);
  return result;
}

rtc::Buffer SpsVuiRewriter::ParseOutgoingBitstreamAndRewrite(
    rtc::ArrayView<const uint8_t> buffer,
    const webrtc::ColorSpace* color_space) {
  std::vector<H264::NaluIndex> nalus =
      H264::FindNaluIndices(buffer.data(), buffer.size());

  // Allocate some extra space for potentially adding a missing VUI.
  rtc::Buffer output_buffer(/*size=*/0, /*capacity=*/buffer.size() +
                                            nalus.size() * kMaxVuiSpsIncrease);

  for (const H264::NaluIndex& nalu : nalus) {
    // Copy NAL unit start code.
    const uint8_t* start_code_ptr = buffer.data() + nalu.start_offset;
    const size_t start_code_length =
        nalu.payload_start_offset - nalu.start_offset;
    const uint8_t* nalu_ptr = buffer.data() + nalu.payload_start_offset;
    const size_t nalu_length = nalu.payload_size;

    if (H264::ParseNaluType(nalu_ptr[0]) == H264::NaluType::kSps) {
      // Check if stream uses picture order count type 0, and if so rewrite it
      // to enable faster decoding. Streams in that format incur additional
      // delay because it allows decode order to differ from render order.
      // The mechanism used is to rewrite (edit or add) the SPS's VUI to contain
      // restrictions on the maximum number of reordered pictures. This reduces
      // latency significantly, though it still adds about a frame of latency to
      // decoding.
      // Note that we do this rewriting both here (send side, in order to
      // protect legacy receive clients) in RtpDepacketizerH264::ParseSingleNalu
      // (receive side, in orderer to protect us from unknown or legacy send
      // clients).
      absl::optional<SpsParser::SpsState> sps;
      rtc::Buffer output_nalu;

      // Add the type header to the output buffer first, so that the rewriter
      // can append modified payload on top of that.
      output_nalu.AppendData(nalu_ptr[0]);

      ParseResult result = ParseAndRewriteSps(
          nalu_ptr + H264::kNaluTypeSize, nalu_length - H264::kNaluTypeSize,
          &sps, color_space, &output_nalu, Direction::kOutgoing);
      if (result == ParseResult::kVuiRewritten) {
        output_buffer.AppendData(start_code_ptr, start_code_length);
        output_buffer.AppendData(output_nalu.data(), output_nalu.size());
        continue;
      }
    } else if (H264::ParseNaluType(nalu_ptr[0]) == H264::NaluType::kAud) {
      // Skip the access unit delimiter copy.
      continue;
    }

    // vui wasn't rewritten and it is not aud, copy the nal unit as is.
    output_buffer.AppendData(start_code_ptr, start_code_length);
    output_buffer.AppendData(nalu_ptr, nalu_length);
  }
  return output_buffer;
}

namespace {
bool CopyAndRewriteVui(const SpsParser::SpsState& sps,
                       BitstreamReader& source,
                       rtc::BitBufferWriter& destination,
                       const webrtc::ColorSpace* color_space,
                       SpsVuiRewriter::ParseResult& out_vui_rewritten) {
  out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiOk;

  //
  // vui_parameters_present_flag: u(1)
  //
  RETURN_FALSE_ON_FAIL(destination.WriteBits(1, 1));

  // ********* IMPORTANT! **********
  // Now we're at the VUI, so we want to (1) add it if it isn't present, and
  // (2) rewrite frame reordering values so no reordering is allowed.
  if (!sps.vui_params_present) {
    // Write a simple VUI with the parameters we want and 0 for all other flags.

    // aspect_ratio_info_present_flag, overscan_info_present_flag. Both u(1).
    RETURN_FALSE_ON_FAIL(destination.WriteBits(0, 2));

    uint32_t video_signal_type_present_flag =
        (color_space && !IsDefaultColorSpace(*color_space)) ? 1 : 0;
    RETURN_FALSE_ON_FAIL(
        destination.WriteBits(video_signal_type_present_flag, 1));
    if (video_signal_type_present_flag) {
      RETURN_FALSE_ON_FAIL(AddVideoSignalTypeInfo(destination, *color_space));
    }
    // chroma_loc_info_present_flag, timing_info_present_flag,
    // nal_hrd_parameters_present_flag, vcl_hrd_parameters_present_flag,
    // pic_struct_present_flag, All u(1)
    RETURN_FALSE_ON_FAIL(destination.WriteBits(0, 5));
    // bitstream_restriction_flag: u(1)
    RETURN_FALSE_ON_FAIL(destination.WriteBits(1, 1));
    RETURN_FALSE_ON_FAIL(
        AddBitstreamRestriction(&destination, sps.max_num_ref_frames));

    out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiRewritten;
  } else {
    // Parse out the full VUI.
    // aspect_ratio_info_present_flag: u(1)
    uint32_t aspect_ratio_info_present_flag = CopyBits(1, source, destination);
    if (aspect_ratio_info_present_flag) {
      // aspect_ratio_idc: u(8)
      uint8_t aspect_ratio_idc = CopyUInt8(source, destination);
      if (aspect_ratio_idc == 255u) {  // Extended_SAR
        // sar_width/sar_height: u(16) each.
        CopyBits(32, source, destination);
      }
    }
    // overscan_info_present_flag: u(1)
    uint32_t overscan_info_present_flag = CopyBits(1, source, destination);
    if (overscan_info_present_flag) {
      // overscan_appropriate_flag: u(1)
      CopyBits(1, source, destination);
    }

    CopyOrRewriteVideoSignalTypeInfo(source, destination, color_space,
                                     out_vui_rewritten);

    // chroma_loc_info_present_flag: u(1)
    uint32_t chroma_loc_info_present_flag = CopyBits(1, source, destination);
    if (chroma_loc_info_present_flag == 1) {
      // chroma_sample_loc_type_(top|bottom)_field: ue(v) each.
      CopyExpGolomb(source, destination);
      CopyExpGolomb(source, destination);
    }
    // timing_info_present_flag: u(1)
    uint32_t timing_info_present_flag = CopyBits(1, source, destination);
    if (timing_info_present_flag == 1) {
      // num_units_in_tick, time_scale: u(32) each
      CopyBits(32, source, destination);
      CopyBits(32, source, destination);
      // fixed_frame_rate_flag: u(1)
      CopyBits(1, source, destination);
    }
    // nal_hrd_parameters_present_flag: u(1)
    uint32_t nal_hrd_parameters_present_flag = CopyBits(1, source, destination);
    if (nal_hrd_parameters_present_flag == 1) {
      CopyHrdParameters(source, destination);
    }
    // vcl_hrd_parameters_present_flag: u(1)
    uint32_t vcl_hrd_parameters_present_flag = CopyBits(1, source, destination);
    if (vcl_hrd_parameters_present_flag == 1) {
      CopyHrdParameters(source, destination);
    }
    if (nal_hrd_parameters_present_flag == 1 ||
        vcl_hrd_parameters_present_flag == 1) {
      // low_delay_hrd_flag: u(1)
      CopyBits(1, source, destination);
    }
    // pic_struct_present_flag: u(1)
    CopyBits(1, source, destination);

    // bitstream_restriction_flag: u(1)
    uint32_t bitstream_restriction_flag = source.ReadBit();
    RETURN_FALSE_ON_FAIL(destination.WriteBits(1, 1));
    if (bitstream_restriction_flag == 0) {
      // We're adding one from scratch.
      RETURN_FALSE_ON_FAIL(
          AddBitstreamRestriction(&destination, sps.max_num_ref_frames));
      out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiRewritten;
    } else {
      // We're replacing.
      // motion_vectors_over_pic_boundaries_flag: u(1)
      CopyBits(1, source, destination);
      // max_bytes_per_pic_denom: ue(v)
      CopyExpGolomb(source, destination);
      // max_bits_per_mb_denom: ue(v)
      CopyExpGolomb(source, destination);
      // log2_max_mv_length_horizontal: ue(v)
      CopyExpGolomb(source, destination);
      // log2_max_mv_length_vertical: ue(v)
      CopyExpGolomb(source, destination);
      // ********* IMPORTANT! **********
      // The next two are the ones we need to set to low numbers:
      // max_num_reorder_frames: ue(v)
      // max_dec_frame_buffering: ue(v)
      // However, if they are already set to no greater than the numbers we
      // want, then we don't need to be rewriting.
      uint32_t max_num_reorder_frames = source.ReadExponentialGolomb();
      uint32_t max_dec_frame_buffering = source.ReadExponentialGolomb();
      RETURN_FALSE_ON_FAIL(destination.WriteExponentialGolomb(0));
      RETURN_FALSE_ON_FAIL(
          destination.WriteExponentialGolomb(sps.max_num_ref_frames));
      if (max_num_reorder_frames != 0 ||
          max_dec_frame_buffering > sps.max_num_ref_frames) {
        out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiRewritten;
      }
    }
  }
  return source.Ok();
}

// Copies a VUI HRD parameters segment.
void CopyHrdParameters(BitstreamReader& source,
                       rtc::BitBufferWriter& destination) {
  // cbp_cnt_minus1: ue(v)
  uint32_t cbp_cnt_minus1 = CopyExpGolomb(source, destination);
  // bit_rate_scale and cbp_size_scale: u(4) each
  CopyBits(8, source, destination);
  for (size_t i = 0; source.Ok() && i <= cbp_cnt_minus1; ++i) {
    // bit_rate_value_minus1 and cbp_size_value_minus1: ue(v) each
    CopyExpGolomb(source, destination);
    CopyExpGolomb(source, destination);
    // cbr_flag: u(1)
    CopyBits(1, source, destination);
  }
  // initial_cbp_removal_delay_length_minus1: u(5)
  // cbp_removal_delay_length_minus1: u(5)
  // dbp_output_delay_length_minus1: u(5)
  // time_offset_length: u(5)
  CopyBits(5 * 4, source, destination);
}

// These functions are similar to webrtc::H264SpsParser::Parse, and based on the
// same version of the H.264 standard. You can find it here:
// http://www.itu.int/rec/T-REC-H.264

// Adds a bitstream restriction VUI segment.
bool AddBitstreamRestriction(rtc::BitBufferWriter* destination,
                             uint32_t max_num_ref_frames) {
  // motion_vectors_over_pic_boundaries_flag: u(1)
  // Default is 1 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteBits(1, 1));
  // max_bytes_per_pic_denom: ue(v)
  // Default is 2 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(2));
  // max_bits_per_mb_denom: ue(v)
  // Default is 1 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(1));
  // log2_max_mv_length_horizontal: ue(v)
  // log2_max_mv_length_vertical: ue(v)
  // Both default to 16 when not present.
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(16));
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(16));

  // ********* IMPORTANT! **********
  // max_num_reorder_frames: ue(v)
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(0));
  // max_dec_frame_buffering: ue(v)
  RETURN_FALSE_ON_FAIL(destination->WriteExponentialGolomb(max_num_ref_frames));
  return true;
}

bool IsDefaultColorSpace(const ColorSpace& color_space) {
  return color_space.range() != ColorSpace::RangeID::kFull &&
         color_space.primaries() == ColorSpace::PrimaryID::kUnspecified &&
         color_space.transfer() == ColorSpace::TransferID::kUnspecified &&
         color_space.matrix() == ColorSpace::MatrixID::kUnspecified;
}

bool AddVideoSignalTypeInfo(rtc::BitBufferWriter& destination,
                            const ColorSpace& color_space) {
  // video_format: u(3).
  RETURN_FALSE_ON_FAIL(destination.WriteBits(5, 3));  // 5 = Unspecified
  // video_full_range_flag: u(1)
  RETURN_FALSE_ON_FAIL(destination.WriteBits(
      color_space.range() == ColorSpace::RangeID::kFull ? 1 : 0, 1));
  // colour_description_present_flag: u(1)
  RETURN_FALSE_ON_FAIL(destination.WriteBits(1, 1));
  // colour_primaries: u(8)
  RETURN_FALSE_ON_FAIL(
      destination.WriteUInt8(static_cast<uint8_t>(color_space.primaries())));
  // transfer_characteristics: u(8)
  RETURN_FALSE_ON_FAIL(
      destination.WriteUInt8(static_cast<uint8_t>(color_space.transfer())));
  // matrix_coefficients: u(8)
  RETURN_FALSE_ON_FAIL(
      destination.WriteUInt8(static_cast<uint8_t>(color_space.matrix())));
  return true;
}

bool CopyOrRewriteVideoSignalTypeInfo(
    BitstreamReader& source,
    rtc::BitBufferWriter& destination,
    const ColorSpace* color_space,
    SpsVuiRewriter::ParseResult& out_vui_rewritten) {
  // Read.
  uint32_t video_format = 5;           // H264 default: unspecified
  uint32_t video_full_range_flag = 0;  // H264 default: limited
  uint32_t colour_description_present_flag = 0;
  uint8_t colour_primaries = 3;          // H264 default: unspecified
  uint8_t transfer_characteristics = 3;  // H264 default: unspecified
  uint8_t matrix_coefficients = 3;       // H264 default: unspecified
  uint32_t video_signal_type_present_flag = source.ReadBit();
  if (video_signal_type_present_flag) {
    video_format = source.ReadBits(3);
    video_full_range_flag = source.ReadBit();
    colour_description_present_flag = source.ReadBit();
    if (colour_description_present_flag) {
      colour_primaries = source.Read<uint8_t>();
      transfer_characteristics = source.Read<uint8_t>();
      matrix_coefficients = source.Read<uint8_t>();
    }
  }
  RETURN_FALSE_ON_FAIL(source.Ok());

  // Update.
  uint32_t video_signal_type_present_flag_override =
      video_signal_type_present_flag;
  uint32_t video_format_override = video_format;
  uint32_t video_full_range_flag_override = video_full_range_flag;
  uint32_t colour_description_present_flag_override =
      colour_description_present_flag;
  uint8_t colour_primaries_override = colour_primaries;
  uint8_t transfer_characteristics_override = transfer_characteristics;
  uint8_t matrix_coefficients_override = matrix_coefficients;
  if (color_space) {
    if (IsDefaultColorSpace(*color_space)) {
      video_signal_type_present_flag_override = 0;
    } else {
      video_signal_type_present_flag_override = 1;
      video_format_override = 5;  // unspecified

      if (color_space->range() == ColorSpace::RangeID::kFull) {
        video_full_range_flag_override = 1;
      } else {
        // ColorSpace::RangeID::kInvalid and kDerived are treated as limited.
        video_full_range_flag_override = 0;
      }

      colour_description_present_flag_override =
          color_space->primaries() != ColorSpace::PrimaryID::kUnspecified ||
          color_space->transfer() != ColorSpace::TransferID::kUnspecified ||
          color_space->matrix() != ColorSpace::MatrixID::kUnspecified;
      colour_primaries_override =
          static_cast<uint8_t>(color_space->primaries());
      transfer_characteristics_override =
          static_cast<uint8_t>(color_space->transfer());
      matrix_coefficients_override =
          static_cast<uint8_t>(color_space->matrix());
    }
  }

  // Write.
  RETURN_FALSE_ON_FAIL(
      destination.WriteBits(video_signal_type_present_flag_override, 1));
  if (video_signal_type_present_flag_override) {
    RETURN_FALSE_ON_FAIL(destination.WriteBits(video_format_override, 3));
    RETURN_FALSE_ON_FAIL(
        destination.WriteBits(video_full_range_flag_override, 1));
    RETURN_FALSE_ON_FAIL(
        destination.WriteBits(colour_description_present_flag_override, 1));
    if (colour_description_present_flag_override) {
      RETURN_FALSE_ON_FAIL(destination.WriteUInt8(colour_primaries_override));
      RETURN_FALSE_ON_FAIL(
          destination.WriteUInt8(transfer_characteristics_override));
      RETURN_FALSE_ON_FAIL(
          destination.WriteUInt8(matrix_coefficients_override));
    }
  }

  if (video_signal_type_present_flag_override !=
          video_signal_type_present_flag ||
      video_format_override != video_format ||
      video_full_range_flag_override != video_full_range_flag ||
      colour_description_present_flag_override !=
          colour_description_present_flag ||
      colour_primaries_override != colour_primaries ||
      transfer_characteristics_override != transfer_characteristics ||
      matrix_coefficients_override != matrix_coefficients) {
    out_vui_rewritten = SpsVuiRewriter::ParseResult::kVuiRewritten;
  }

  return true;
}

bool CopyRemainingBits(BitstreamReader& source,
                       rtc::BitBufferWriter& destination) {
  // Try to get at least the destination aligned.
  if (source.RemainingBitCount() > 0 && source.RemainingBitCount() % 8 != 0) {
    size_t misaligned_bits = source.RemainingBitCount() % 8;
    CopyBits(misaligned_bits, source, destination);
  }
  while (source.RemainingBitCount() > 0) {
    int count = std::min(32, source.RemainingBitCount());
    CopyBits(count, source, destination);
  }
  // TODO(noahric): The last byte could be all zeroes now, which we should just
  // strip.
  return source.Ok();
}

}  // namespace

}  // namespace webrtc
