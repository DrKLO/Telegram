/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/h265_vps_sps_pps_tracker.h"

#include <memory>
#include <string>
#include <utility>

#include "absl/types/variant.h"
#include "common_video/h264/h264_common.h"
#include "common_video/h265/h265_common.h"
#include "common_video/h265/h265_pps_parser.h"
#include "common_video/h265/h265_sps_parser.h"
#include "common_video/h265/h265_vps_parser.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#include "modules/video_coding/codecs/h265/include/h265_globals.h"
#include "modules/video_coding/frame_object.h"
#include "modules/video_coding/packet_buffer.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace video_coding {

namespace {
const uint8_t start_code_h265[] = {0, 0, 0, 1};
}  // namespace

H265VpsSpsPpsTracker::FixedBitstream H265VpsSpsPpsTracker::CopyAndFixBitstream(
    rtc::ArrayView<const uint8_t> bitstream,
    RTPVideoHeader* video_header) {
  RTC_DCHECK(video_header);
  RTC_DCHECK(video_header->codec == kVideoCodecH265);

  auto& h265_header =
      absl::get<RTPVideoHeaderH265>(video_header->video_type_header);

  bool append_vps_sps_pps = false;
  auto vps = vps_data_.end();
  auto sps = sps_data_.end();
  auto pps = pps_data_.end();

  for (size_t i = 0; i < h265_header.nalus_length; ++i) {
    const H265NaluInfo& nalu = h265_header.nalus[i];
    switch (nalu.type) {
      case H265::NaluType::kVps: {
        vps_data_[nalu.vps_id].size = 0;
        break;
      }
      case H265::NaluType::kSps: {
        sps_data_[nalu.sps_id].vps_id = nalu.vps_id;
        sps_data_[nalu.sps_id].width = video_header->width;
        sps_data_[nalu.sps_id].height = video_header->height;
        break;
      }
      case H265::NaluType::kPps: {
        pps_data_[nalu.pps_id].sps_id = nalu.sps_id;
        break;
      }
      case H265::NaluType::kIdrWRadl:
      case H265::NaluType::kIdrNLp:
      case H265::NaluType::kCra: {
        // If this is the first packet of an IDR, make sure we have the required
        // SPS/PPS and also calculate how much extra space we need in the buffer
        // to prepend the SPS/PPS to the bitstream with start codes.
        if (video_header->is_first_packet_in_frame) {
          if (nalu.pps_id == -1) {
            RTC_LOG(LS_WARNING) << "No PPS id in IDR nalu.";
            return {kRequestKeyframe};
          }

          pps = pps_data_.find(nalu.pps_id);
          if (pps == pps_data_.end()) {
            RTC_LOG(LS_WARNING)
                << "No PPS with id " << nalu.pps_id << " received";
            return {kRequestKeyframe};
          }

          sps = sps_data_.find(pps->second.sps_id);
          if (sps == sps_data_.end()) {
            RTC_LOG(LS_WARNING)
                << "No SPS with id << " << pps->second.sps_id << " received";
            return {kRequestKeyframe};
          }

          vps = vps_data_.find(sps->second.vps_id);
          if (vps == vps_data_.end()) {
            RTC_LOG(LS_WARNING)
                << "No VPS with id " << sps->second.vps_id << " received";
            return {kRequestKeyframe};
          }

          // Since the first packet of every keyframe should have its width and
          // height set we set it here in the case of it being supplied out of
          // band.
          video_header->width = sps->second.width;
          video_header->height = sps->second.height;

          // If the VPS/SPS/PPS was supplied out of band then we will have saved
          // the actual bitstream in |data|.
          // This branch is not verified.
          if (vps->second.data && sps->second.data && pps->second.data) {
            RTC_DCHECK_GT(vps->second.size, 0);
            RTC_DCHECK_GT(sps->second.size, 0);
            RTC_DCHECK_GT(pps->second.size, 0);
            append_vps_sps_pps = true;
          }
        }
        break;
      }
      default:
        break;
    }
  }

  RTC_CHECK(!append_vps_sps_pps ||
            (sps != sps_data_.end() && pps != pps_data_.end()));

  // Calculate how much space we need for the rest of the bitstream.
  size_t required_size = 0;

  if (append_vps_sps_pps) {
    required_size += vps->second.size + sizeof(start_code_h265);
    required_size += sps->second.size + sizeof(start_code_h265);
    required_size += pps->second.size + sizeof(start_code_h265);
  }

  if (h265_header.packetization_type == kH265AP) {
    const uint8_t* nalu_ptr = bitstream.data() + 1;
    while (nalu_ptr < bitstream.data() + bitstream.size()) {
      RTC_DCHECK(video_header->is_first_packet_in_frame);
      required_size += sizeof(start_code_h265);

      // The first two bytes describe the length of a segment.
      uint16_t segment_length = nalu_ptr[0] << 8 | nalu_ptr[1];
      nalu_ptr += 2;

      required_size += segment_length;
      nalu_ptr += segment_length;
    }
  } else {
	// TODO: in h.264 this is "h264_header.nalus_length > 0"
    if (video_header->is_first_packet_in_frame)
      required_size += sizeof(start_code_h265);
    required_size += bitstream.size();
  }

  // Then we copy to the new buffer.
  H265VpsSpsPpsTracker::FixedBitstream fixed;
  fixed.bitstream.EnsureCapacity(required_size);

  if (append_vps_sps_pps) {
    // Insert VPS.
	fixed.bitstream.AppendData(start_code_h265);
	fixed.bitstream.AppendData(vps->second.data.get(), vps->second.size);

    // Insert SPS.
	fixed.bitstream.AppendData(start_code_h265);
	fixed.bitstream.AppendData(sps->second.data.get(), sps->second.size);

    // Insert PPS.
	fixed.bitstream.AppendData(start_code_h265);
	fixed.bitstream.AppendData(pps->second.data.get(), pps->second.size);

    // Update codec header to reflect the newly added SPS and PPS.
    H265NaluInfo vps_info;
    vps_info.type = H265::NaluType::kVps;
    vps_info.vps_id = vps->first;
    vps_info.sps_id = -1;
    vps_info.pps_id = -1;
    H265NaluInfo sps_info;
    sps_info.type = H265::NaluType::kSps;
    sps_info.vps_id = vps->first;
    sps_info.sps_id = sps->first;
    sps_info.pps_id = -1;
    H265NaluInfo pps_info;
    pps_info.type = H265::NaluType::kPps;
    pps_info.vps_id = vps->first;
    pps_info.sps_id = sps->first;
    pps_info.pps_id = pps->first;
    if (h265_header.nalus_length + 2 <= kMaxNalusPerPacket) {
      h265_header.nalus[h265_header.nalus_length++] = vps_info;
      h265_header.nalus[h265_header.nalus_length++] = sps_info;
      h265_header.nalus[h265_header.nalus_length++] = pps_info;
    } else {
      RTC_LOG(LS_WARNING) << "Not enough space in H.265 codec header to insert "
                             "SPS/PPS provided out-of-band.";
    }
  }

  // Copy the rest of the bitstream and insert start codes.
  if (h265_header.packetization_type == kH265AP) {
    const uint8_t* nalu_ptr = bitstream.data() + 1;
    while (nalu_ptr < bitstream.data() + bitstream.size()) {
      fixed.bitstream.AppendData(start_code_h265);

      // The first two bytes describe the length of a segment.
      uint16_t segment_length = nalu_ptr[0] << 8 | nalu_ptr[1];
      nalu_ptr += 2;

      size_t copy_end = nalu_ptr - bitstream.data() + segment_length;
      if (copy_end > bitstream.size()) {
        return {kDrop};
      }

      fixed.bitstream.AppendData(nalu_ptr, segment_length);
      nalu_ptr += segment_length;
    }
  } else {
	// For h.264 it is "h264_header.nalus_length > 0"
    if (video_header->is_first_packet_in_frame) {
      fixed.bitstream.AppendData(start_code_h265);
    }
    fixed.bitstream.AppendData(bitstream.data(), bitstream.size());
  }

  fixed.action = kInsert;
  return fixed;
}

void H265VpsSpsPpsTracker::InsertVpsSpsPpsNalus(
    const std::vector<uint8_t>& vps,
    const std::vector<uint8_t>& sps,
    const std::vector<uint8_t>& pps) {
  constexpr size_t kNaluHeaderOffset = 1;
  if (vps.size() < kNaluHeaderOffset) {
    RTC_LOG(LS_WARNING) << "VPS size  " << vps.size() << " is smaller than "
                        << kNaluHeaderOffset;
    return;
  }
  if ((vps[0] & 0x7e) >> 1 != H265::NaluType::kSps) {
    RTC_LOG(LS_WARNING) << "SPS Nalu header missing";
    return;
  }
  if (sps.size() < kNaluHeaderOffset) {
    RTC_LOG(LS_WARNING) << "SPS size  " << sps.size() << " is smaller than "
                        << kNaluHeaderOffset;
    return;
  }
  if ((sps[0] & 0x7e) >> 1 != H265::NaluType::kSps) {
    RTC_LOG(LS_WARNING) << "SPS Nalu header missing";
    return;
  }
  if (pps.size() < kNaluHeaderOffset) {
    RTC_LOG(LS_WARNING) << "PPS size  " << pps.size() << " is smaller than "
                        << kNaluHeaderOffset;
    return;
  }
  if ((pps[0] & 0x7e) >> 1 != H265::NaluType::kPps) {
    RTC_LOG(LS_WARNING) << "SPS Nalu header missing";
    return;
  }
  absl::optional<H265VpsParser::VpsState> parsed_vps = H265VpsParser::ParseVps(
      vps.data() + kNaluHeaderOffset, vps.size() - kNaluHeaderOffset);
  absl::optional<H265SpsParser::SpsState> parsed_sps = H265SpsParser::ParseSps(
      sps.data() + kNaluHeaderOffset, sps.size() - kNaluHeaderOffset);
  absl::optional<H265PpsParser::PpsState> parsed_pps = H265PpsParser::ParsePps(
      pps.data() + kNaluHeaderOffset, pps.size() - kNaluHeaderOffset);

  if (!parsed_vps) {
    RTC_LOG(LS_WARNING) << "Failed to parse VPS.";
  }

  if (!parsed_sps) {
    RTC_LOG(LS_WARNING) << "Failed to parse SPS.";
  }

  if (!parsed_pps) {
    RTC_LOG(LS_WARNING) << "Failed to parse PPS.";
  }

  if (!parsed_vps || !parsed_pps || !parsed_sps) {
    return;
  }

  VpsInfo vps_info;
  vps_info.size = vps.size();
  uint8_t* vps_data = new uint8_t[vps_info.size];
  memcpy(vps_data, vps.data(), vps_info.size);
  vps_info.data.reset(vps_data);
  vps_data_[parsed_vps->id] = std::move(vps_info);

  SpsInfo sps_info;
  sps_info.size = sps.size();
  sps_info.width = parsed_sps->width;
  sps_info.height = parsed_sps->height;
  sps_info.vps_id = parsed_sps->vps_id;
  uint8_t* sps_data = new uint8_t[sps_info.size];
  memcpy(sps_data, sps.data(), sps_info.size);
  sps_info.data.reset(sps_data);
  sps_data_[parsed_sps->id] = std::move(sps_info);

  PpsInfo pps_info;
  pps_info.size = pps.size();
  pps_info.sps_id = parsed_pps->sps_id;
  uint8_t* pps_data = new uint8_t[pps_info.size];
  memcpy(pps_data, pps.data(), pps_info.size);
  pps_info.data.reset(pps_data);
  pps_data_[parsed_pps->id] = std::move(pps_info);

  RTC_LOG(LS_INFO) << "Inserted SPS id " << parsed_sps->id << " and PPS id "
                   << parsed_pps->id << " (referencing SPS "
                   << parsed_pps->sps_id << ")";
}

}  // namespace video_coding
}  // namespace webrtc
