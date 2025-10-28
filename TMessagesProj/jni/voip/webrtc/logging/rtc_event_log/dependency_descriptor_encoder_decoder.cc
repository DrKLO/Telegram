/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "logging/rtc_event_log/dependency_descriptor_encoder_decoder.h"

#include <string>
#include <vector>

#include "logging/rtc_event_log/encoder/delta_encoding.h"
#include "logging/rtc_event_log/encoder/optional_blob_encoding.h"
#include "logging/rtc_event_log/events/rtc_event_log_parse_status.h"
#include "logging/rtc_event_log/rtc_event_log2_proto_include.h"
#include "rtc_base/logging.h"

namespace webrtc {

// static
absl::optional<rtclog2::DependencyDescriptorsWireInfo>
RtcEventLogDependencyDescriptorEncoderDecoder::Encode(
    const std::vector<rtc::ArrayView<const uint8_t>>& raw_dd_data) {
  if (raw_dd_data.empty()) {
    return {};
  }

  for (const auto& dd : raw_dd_data) {
    if (!dd.empty() && dd.size() < 3) {
      RTC_LOG(LS_WARNING) << "DependencyDescriptor size not valid.";
      return {};
    }
  }

  rtclog2::DependencyDescriptorsWireInfo res;
  const rtc::ArrayView<const uint8_t>& base_dd = raw_dd_data[0];
  auto delta_dds =
      rtc::MakeArrayView(raw_dd_data.data(), raw_dd_data.size()).subview(1);

  // Start and end bit.
  {
    absl::optional<uint32_t> start_end_bit;
    if (!base_dd.empty()) {
      start_end_bit = (base_dd[0] >> 6);
      res.set_start_end_bit(*start_end_bit);
    }
    if (!delta_dds.empty()) {
      std::vector<absl::optional<uint64_t>> values(delta_dds.size());
      for (size_t i = 0; i < delta_dds.size(); ++i) {
        if (!delta_dds[i].empty()) {
          values[i] = delta_dds[i][0] >> 6;
        }
      }
      std::string encoded_deltas = EncodeDeltas(start_end_bit, values);
      if (!encoded_deltas.empty()) {
        res.set_start_end_bit_deltas(encoded_deltas);
      }
    }
  }

  // Template IDs.
  {
    absl::optional<uint32_t> template_id;
    if (!base_dd.empty()) {
      template_id = (base_dd[0] & 0b0011'1111);
      res.set_template_id(*template_id);
    }

    if (!delta_dds.empty()) {
      std::vector<absl::optional<uint64_t>> values(delta_dds.size());
      for (size_t i = 0; i < delta_dds.size(); ++i) {
        if (!delta_dds[i].empty()) {
          values[i] = delta_dds[i][0] & 0b0011'1111;
        }
      }
      std::string encoded_deltas = EncodeDeltas(template_id, values);
      if (!encoded_deltas.empty()) {
        res.set_template_id_deltas(encoded_deltas);
      }
    }
  }

  // Frame IDs.
  {
    absl::optional<uint32_t> frame_id;
    if (!base_dd.empty()) {
      frame_id = (uint16_t{base_dd[1]} << 8) + base_dd[2];
      res.set_frame_id(*frame_id);
    }

    if (!delta_dds.empty()) {
      std::vector<absl::optional<uint64_t>> values(delta_dds.size());
      for (size_t i = 0; i < delta_dds.size(); ++i) {
        if (!delta_dds[i].empty()) {
          values[i] = (uint16_t{delta_dds[i][1]} << 8) + delta_dds[i][2];
        }
      }
      std::string encoded_deltas = EncodeDeltas(frame_id, values);
      if (!encoded_deltas.empty()) {
        res.set_frame_id_deltas(encoded_deltas);
      }
    }
  }

  // Extended info
  {
    std::vector<absl::optional<std::string>> values(raw_dd_data.size());
    for (size_t i = 0; i < raw_dd_data.size(); ++i) {
      if (raw_dd_data[i].size() > 3) {
        auto extended_info = raw_dd_data[i].subview(3);
        values[i] = {reinterpret_cast<const char*>(extended_info.data()),
                     extended_info.size()};
      }
    }

    std::string encoded_blobs = EncodeOptionalBlobs(values);
    if (!encoded_blobs.empty()) {
      res.set_extended_infos(encoded_blobs);
    }
  }

  return res;
}

// static
RtcEventLogParseStatusOr<std::vector<std::vector<uint8_t>>>
RtcEventLogDependencyDescriptorEncoderDecoder::Decode(
    const rtclog2::DependencyDescriptorsWireInfo& dd_wire_info,
    size_t num_packets) {
  if (num_packets == 0) {
    return {std::vector<std::vector<uint8_t>>()};
  }

  std::vector<std::vector<uint8_t>> res(num_packets);

  absl::optional<uint64_t> start_end_bit_base;
  if (dd_wire_info.has_start_end_bit()) {
    start_end_bit_base = dd_wire_info.start_end_bit();
  }
  absl::optional<uint64_t> template_id_base;
  if (dd_wire_info.has_template_id()) {
    template_id_base = dd_wire_info.template_id();
  }
  absl::optional<uint64_t> frame_id_base;
  if (dd_wire_info.has_frame_id()) {
    frame_id_base = dd_wire_info.frame_id();
  }

  std::vector<absl::optional<uint64_t>> start_end_bit_deltas;
  if (dd_wire_info.has_start_end_bit_deltas()) {
    start_end_bit_deltas = DecodeDeltas(dd_wire_info.start_end_bit_deltas(),
                                        start_end_bit_base, num_packets - 1);
    RTC_DCHECK(start_end_bit_deltas.empty() ||
               start_end_bit_deltas.size() == (num_packets - 1));
  }
  std::vector<absl::optional<uint64_t>> template_id_deltas;
  if (dd_wire_info.has_template_id_deltas()) {
    template_id_deltas = DecodeDeltas(dd_wire_info.template_id_deltas(),
                                      template_id_base, num_packets - 1);
    RTC_DCHECK(template_id_deltas.empty() ||
               template_id_deltas.size() == (num_packets - 1));
  }
  std::vector<absl::optional<uint64_t>> frame_id_deltas;
  if (dd_wire_info.has_frame_id_deltas()) {
    frame_id_deltas = DecodeDeltas(dd_wire_info.frame_id_deltas(),
                                   frame_id_base, num_packets - 1);
    RTC_DCHECK(frame_id_deltas.empty() ||
               frame_id_deltas.size() == (num_packets - 1));
  }
  std::vector<absl::optional<std::string>> extended_infos;
  if (dd_wire_info.has_extended_infos()) {
    extended_infos =
        DecodeOptionalBlobs(dd_wire_info.extended_infos(), num_packets);
  }

  auto recreate_raw_dd = [&](int i, const absl::optional<uint64_t>& be,
                             const absl::optional<uint64_t>& tid,
                             const absl::optional<uint64_t>& fid) {
    absl::string_view ext;
    if (!extended_infos.empty() && extended_infos[i].has_value()) {
      ext = *extended_infos[i];
    }
    if (be.has_value() && tid.has_value() && fid.has_value()) {
      res[i].reserve(3 + ext.size());
      res[i].push_back((*be << 6) | *tid);
      res[i].push_back(*fid >> 8);
      res[i].push_back(*fid);
      if (!ext.empty()) {
        res[i].insert(res[i].end(), ext.begin(), ext.end());
      }
    } else if (be.has_value() || tid.has_value() || fid.has_value()) {
      RTC_PARSE_RETURN_ERROR("Not all required fields present.");
    } else if (!ext.empty()) {
      RTC_PARSE_RETURN_ERROR(
          "Extended info present without required fields present.");
    }

    return RtcEventLogParseStatus::Success();
  };

  RTC_RETURN_IF_ERROR(
      recreate_raw_dd(0, start_end_bit_base, template_id_base, frame_id_base));

  for (size_t i = 1; i < num_packets; ++i) {
    RTC_RETURN_IF_ERROR(recreate_raw_dd(
        i,
        start_end_bit_deltas.empty() ? start_end_bit_base
                                     : start_end_bit_deltas[i - 1],
        template_id_deltas.empty() ? template_id_base
                                   : template_id_deltas[i - 1],
        frame_id_deltas.empty() ? frame_id_base : frame_id_deltas[i - 1]));
  }

  return res;
}

}  // namespace webrtc
