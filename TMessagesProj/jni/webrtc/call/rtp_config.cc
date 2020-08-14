/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtp_config.h"

#include <cstdint>

#include "absl/algorithm/container.h"
#include "api/array_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

namespace {

uint32_t FindAssociatedSsrc(uint32_t ssrc,
                            const std::vector<uint32_t>& ssrcs,
                            const std::vector<uint32_t>& associated_ssrcs) {
  RTC_DCHECK_EQ(ssrcs.size(), associated_ssrcs.size());
  for (size_t i = 0; i < ssrcs.size(); ++i) {
    if (ssrcs[i] == ssrc)
      return associated_ssrcs[i];
  }
  RTC_NOTREACHED();
  return 0;
}

}  // namespace

std::string LntfConfig::ToString() const {
  return enabled ? "{enabled: true}" : "{enabled: false}";
}

std::string NackConfig::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{rtp_history_ms: " << rtp_history_ms;
  ss << '}';
  return ss.str();
}

std::string UlpfecConfig::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{ulpfec_payload_type: " << ulpfec_payload_type;
  ss << ", red_payload_type: " << red_payload_type;
  ss << ", red_rtx_payload_type: " << red_rtx_payload_type;
  ss << '}';
  return ss.str();
}

bool UlpfecConfig::operator==(const UlpfecConfig& other) const {
  return ulpfec_payload_type == other.ulpfec_payload_type &&
         red_payload_type == other.red_payload_type &&
         red_rtx_payload_type == other.red_rtx_payload_type;
}

RtpConfig::RtpConfig() = default;
RtpConfig::RtpConfig(const RtpConfig&) = default;
RtpConfig::~RtpConfig() = default;

RtpConfig::Flexfec::Flexfec() = default;
RtpConfig::Flexfec::Flexfec(const Flexfec&) = default;
RtpConfig::Flexfec::~Flexfec() = default;

std::string RtpConfig::ToString() const {
  char buf[2 * 1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{ssrcs: [";
  for (size_t i = 0; i < ssrcs.size(); ++i) {
    ss << ssrcs[i];
    if (i != ssrcs.size() - 1)
      ss << ", ";
  }
  ss << "], rids: [";
  for (size_t i = 0; i < rids.size(); ++i) {
    ss << rids[i];
    if (i != rids.size() - 1)
      ss << ", ";
  }
  ss << "], mid: '" << mid << "'";
  ss << ", rtcp_mode: "
     << (rtcp_mode == RtcpMode::kCompound ? "RtcpMode::kCompound"
                                          : "RtcpMode::kReducedSize");
  ss << ", max_packet_size: " << max_packet_size;
  ss << ", extmap-allow-mixed: " << (extmap_allow_mixed ? "true" : "false");
  ss << ", extensions: [";
  for (size_t i = 0; i < extensions.size(); ++i) {
    ss << extensions[i].ToString();
    if (i != extensions.size() - 1)
      ss << ", ";
  }
  ss << ']';

  ss << ", lntf: " << lntf.ToString();
  ss << ", nack: {rtp_history_ms: " << nack.rtp_history_ms << '}';
  ss << ", ulpfec: " << ulpfec.ToString();
  ss << ", payload_name: " << payload_name;
  ss << ", payload_type: " << payload_type;
  ss << ", raw_payload: " << (raw_payload ? "true" : "false");

  ss << ", flexfec: {payload_type: " << flexfec.payload_type;
  ss << ", ssrc: " << flexfec.ssrc;
  ss << ", protected_media_ssrcs: [";
  for (size_t i = 0; i < flexfec.protected_media_ssrcs.size(); ++i) {
    ss << flexfec.protected_media_ssrcs[i];
    if (i != flexfec.protected_media_ssrcs.size() - 1)
      ss << ", ";
  }
  ss << "]}";

  ss << ", rtx: " << rtx.ToString();
  ss << ", c_name: " << c_name;
  ss << '}';
  return ss.str();
}

RtpConfig::Rtx::Rtx() = default;
RtpConfig::Rtx::Rtx(const Rtx&) = default;
RtpConfig::Rtx::~Rtx() = default;

std::string RtpConfig::Rtx::ToString() const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "{ssrcs: [";
  for (size_t i = 0; i < ssrcs.size(); ++i) {
    ss << ssrcs[i];
    if (i != ssrcs.size() - 1)
      ss << ", ";
  }
  ss << ']';

  ss << ", payload_type: " << payload_type;
  ss << '}';
  return ss.str();
}

bool RtpConfig::IsMediaSsrc(uint32_t ssrc) const {
  return absl::c_linear_search(ssrcs, ssrc);
}

bool RtpConfig::IsRtxSsrc(uint32_t ssrc) const {
  return absl::c_linear_search(rtx.ssrcs, ssrc);
}

bool RtpConfig::IsFlexfecSsrc(uint32_t ssrc) const {
  return flexfec.payload_type != -1 && ssrc == flexfec.ssrc;
}

absl::optional<uint32_t> RtpConfig::GetRtxSsrcAssociatedWithMediaSsrc(
    uint32_t media_ssrc) const {
  RTC_DCHECK(IsMediaSsrc(media_ssrc));
  // If we don't use RTX there is no association.
  if (rtx.ssrcs.empty())
    return absl::nullopt;
  // If we use RTX there MUST be an association ssrcs[i] <-> rtx.ssrcs[i].
  RTC_DCHECK_EQ(ssrcs.size(), rtx.ssrcs.size());
  return FindAssociatedSsrc(media_ssrc, ssrcs, rtx.ssrcs);
}

uint32_t RtpConfig::GetMediaSsrcAssociatedWithRtxSsrc(uint32_t rtx_ssrc) const {
  RTC_DCHECK(IsRtxSsrc(rtx_ssrc));
  // If we use RTX there MUST be an association ssrcs[i] <-> rtx.ssrcs[i].
  RTC_DCHECK_EQ(ssrcs.size(), rtx.ssrcs.size());
  return FindAssociatedSsrc(rtx_ssrc, rtx.ssrcs, ssrcs);
}

uint32_t RtpConfig::GetMediaSsrcAssociatedWithFlexfecSsrc(
    uint32_t flexfec_ssrc) const {
  RTC_DCHECK(IsFlexfecSsrc(flexfec_ssrc));
  // If we use FlexFEC there MUST be an associated media ssrc.
  //
  // TODO(brandtr/hbos): The current implementation only supports an association
  // with a single media ssrc. If multiple ssrcs are to be supported in the
  // future, in order not to break GetStats()'s packet and byte counters, we
  // must be able to tell how many packets and bytes have contributed to which
  // SSRC.
  RTC_DCHECK_EQ(1u, flexfec.protected_media_ssrcs.size());
  uint32_t media_ssrc = flexfec.protected_media_ssrcs[0];
  RTC_DCHECK(IsMediaSsrc(media_ssrc));
  return media_ssrc;
}

absl::optional<std::string> RtpConfig::GetRidForSsrc(uint32_t ssrc) const {
  auto it = std::find(ssrcs.begin(), ssrcs.end(), ssrc);
  if (it != ssrcs.end()) {
    size_t ssrc_index = std::distance(ssrcs.begin(), it);
    if (ssrc_index < rids.size()) {
      return rids[ssrc_index];
    }
  }
  return absl::nullopt;
}

}  // namespace webrtc
