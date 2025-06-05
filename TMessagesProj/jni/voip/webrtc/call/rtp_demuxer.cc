/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtp_demuxer.h"

#include "absl/strings/string_view.h"
#include "call/rtp_packet_sink_interface.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {
namespace {

template <typename Container, typename Value>
size_t RemoveFromMultimapByValue(Container* multimap, const Value& value) {
  size_t count = 0;
  for (auto it = multimap->begin(); it != multimap->end();) {
    if (it->second == value) {
      it = multimap->erase(it);
      ++count;
    } else {
      ++it;
    }
  }
  return count;
}

template <typename Map, typename Value>
size_t RemoveFromMapByValue(Map* map, const Value& value) {
  return EraseIf(*map, [&](const auto& elem) { return elem.second == value; });
}

// Temp fix: MID in SDP is allowed to be slightly longer than what's allowed
// in the RTP demuxer. Truncate if needed; this won't match, but it only
// makes sense in places that wouldn't use this for matching anyway.
// TODO(bugs.webrtc.org/12517): remove when length 16 is policed by parser.
std::string CheckMidLength(absl::string_view mid) {
  std::string new_mid(mid);
  if (new_mid.length() > BaseRtpStringExtension::kMaxValueSizeBytes) {
    RTC_LOG(LS_WARNING) << "`mid` attribute too long. Truncating.";
    new_mid.resize(BaseRtpStringExtension::kMaxValueSizeBytes);
  }
  return new_mid;
}

}  // namespace

RtpDemuxerCriteria::RtpDemuxerCriteria(
    absl::string_view mid,
    absl::string_view rsid /*= absl::string_view()*/)
    : mid_(CheckMidLength(mid)), rsid_(rsid) {}

RtpDemuxerCriteria::RtpDemuxerCriteria() = default;
RtpDemuxerCriteria::~RtpDemuxerCriteria() = default;

bool RtpDemuxerCriteria::operator==(const RtpDemuxerCriteria& other) const {
  return mid_ == other.mid_ && rsid_ == other.rsid_ && ssrcs_ == other.ssrcs_ &&
         payload_types_ == other.payload_types_;
}

bool RtpDemuxerCriteria::operator!=(const RtpDemuxerCriteria& other) const {
  return !(*this == other);
}

std::string RtpDemuxerCriteria::ToString() const {
  rtc::StringBuilder sb;
  sb << "{mid: " << (mid_.empty() ? "<empty>" : mid_)
     << ", rsid: " << (rsid_.empty() ? "<empty>" : rsid_) << ", ssrcs: [";

  for (auto ssrc : ssrcs_) {
    sb << ssrc << ", ";
  }

  sb << "], payload_types = [";

  for (auto pt : payload_types_) {
    sb << pt << ", ";
  }

  sb << "]}";
  return sb.Release();
}

// static
std::string RtpDemuxer::DescribePacket(const RtpPacketReceived& packet) {
  rtc::StringBuilder sb;
  sb << "PT=" << packet.PayloadType() << " SSRC=" << packet.Ssrc();
  std::string mid;
  if (packet.GetExtension<RtpMid>(&mid)) {
    sb << " MID=" << mid;
  }
  std::string rsid;
  if (packet.GetExtension<RtpStreamId>(&rsid)) {
    sb << " RSID=" << rsid;
  }
  std::string rrsid;
  if (packet.GetExtension<RepairedRtpStreamId>(&rrsid)) {
    sb << " RRSID=" << rrsid;
  }
  return sb.Release();
}

RtpDemuxer::RtpDemuxer(bool use_mid /* = true*/) : use_mid_(use_mid) {}

RtpDemuxer::~RtpDemuxer() {
  RTC_DCHECK(sink_by_mid_.empty());
  RTC_DCHECK(sink_by_ssrc_.empty());
  RTC_DCHECK(sinks_by_pt_.empty());
  RTC_DCHECK(sink_by_mid_and_rsid_.empty());
  RTC_DCHECK(sink_by_rsid_.empty());
}

bool RtpDemuxer::AddSink(const RtpDemuxerCriteria& criteria,
                         RtpPacketSinkInterface* sink) {
  RTC_DCHECK(!criteria.payload_types().empty() || !criteria.ssrcs().empty() ||
             !criteria.mid().empty() || !criteria.rsid().empty());
  RTC_DCHECK(criteria.mid().empty() || IsLegalMidName(criteria.mid()));
  RTC_DCHECK(criteria.rsid().empty() || IsLegalRsidName(criteria.rsid()));
  RTC_DCHECK(sink);

  // We return false instead of DCHECKing for logical conflicts with the new
  // criteria because new sinks are created according to user-specified SDP and
  // we do not want to crash due to a data validation error.
  if (CriteriaWouldConflict(criteria)) {
    RTC_LOG(LS_ERROR) << "Unable to add sink=" << sink
                      << " due to conflicting criteria " << criteria.ToString();
    return false;
  }

  if (!criteria.mid().empty()) {
    if (criteria.rsid().empty()) {
      sink_by_mid_.emplace(criteria.mid(), sink);
    } else {
      sink_by_mid_and_rsid_.emplace(
          std::make_pair(criteria.mid(), criteria.rsid()), sink);
    }
  } else {
    if (!criteria.rsid().empty()) {
      sink_by_rsid_.emplace(criteria.rsid(), sink);
    }
  }

  for (uint32_t ssrc : criteria.ssrcs()) {
    sink_by_ssrc_.emplace(ssrc, sink);
  }

  for (uint8_t payload_type : criteria.payload_types()) {
    sinks_by_pt_.emplace(payload_type, sink);
  }

  RefreshKnownMids();

  RTC_DLOG(LS_INFO) << "Added sink = " << sink << " for criteria "
                    << criteria.ToString();

  return true;
}

bool RtpDemuxer::CriteriaWouldConflict(
    const RtpDemuxerCriteria& criteria) const {
  if (!criteria.mid().empty()) {
    if (criteria.rsid().empty()) {
      // If the MID is in the known_mids_ set, then there is already a sink
      // added for this MID directly, or there is a sink already added with a
      // MID, RSID pair for our MID and some RSID.
      // Adding this criteria would cause one of these rules to be shadowed, so
      // reject this new criteria.
      if (known_mids_.find(criteria.mid()) != known_mids_.end()) {
        RTC_LOG(LS_INFO) << criteria.ToString()
                         << " would conflict with known mid";
        return true;
      }
    } else {
      // If the exact rule already exists, then reject this duplicate.
      const auto sink_by_mid_and_rsid = sink_by_mid_and_rsid_.find(
          std::make_pair(criteria.mid(), criteria.rsid()));
      if (sink_by_mid_and_rsid != sink_by_mid_and_rsid_.end()) {
        RTC_LOG(LS_INFO) << criteria.ToString()
                         << " would conflict with existing sink = "
                         << sink_by_mid_and_rsid->second
                         << " by mid+rsid binding";
        return true;
      }
      // If there is already a sink registered for the bare MID, then this
      // criteria will never receive any packets because they will just be
      // directed to that MID sink, so reject this new criteria.
      const auto sink_by_mid = sink_by_mid_.find(criteria.mid());
      if (sink_by_mid != sink_by_mid_.end()) {
        RTC_LOG(LS_INFO) << criteria.ToString()
                         << " would conflict with existing sink = "
                         << sink_by_mid->second << " by mid binding";
        return true;
      }
    }
  }

  for (uint32_t ssrc : criteria.ssrcs()) {
    const auto sink_by_ssrc = sink_by_ssrc_.find(ssrc);
    if (sink_by_ssrc != sink_by_ssrc_.end()) {
      RTC_LOG(LS_INFO) << criteria.ToString()
                       << " would conflict with existing sink = "
                       << sink_by_ssrc->second << " binding by SSRC=" << ssrc;
      return true;
    }
  }

  // TODO(steveanton): May also sanity check payload types.

  return false;
}

void RtpDemuxer::RefreshKnownMids() {
  known_mids_.clear();

  for (auto const& item : sink_by_mid_) {
    const std::string& mid = item.first;
    known_mids_.insert(mid);
  }

  for (auto const& item : sink_by_mid_and_rsid_) {
    const std::string& mid = item.first.first;
    known_mids_.insert(mid);
  }
}

bool RtpDemuxer::AddSink(uint32_t ssrc, RtpPacketSinkInterface* sink) {
  RtpDemuxerCriteria criteria;
  criteria.ssrcs().insert(ssrc);
  return AddSink(criteria, sink);
}

void RtpDemuxer::AddSink(absl::string_view rsid, RtpPacketSinkInterface* sink) {
  RtpDemuxerCriteria criteria(absl::string_view() /* mid */, rsid);
  AddSink(criteria, sink);
}

bool RtpDemuxer::RemoveSink(const RtpPacketSinkInterface* sink) {
  RTC_DCHECK(sink);
  size_t num_removed = RemoveFromMapByValue(&sink_by_mid_, sink) +
                       RemoveFromMapByValue(&sink_by_ssrc_, sink) +
                       RemoveFromMultimapByValue(&sinks_by_pt_, sink) +
                       RemoveFromMapByValue(&sink_by_mid_and_rsid_, sink) +
                       RemoveFromMapByValue(&sink_by_rsid_, sink);
  RefreshKnownMids();
  return num_removed > 0;
}

flat_set<uint32_t> RtpDemuxer::GetSsrcsForSink(
    const RtpPacketSinkInterface* sink) const {
  flat_set<uint32_t> ssrcs;
  if (sink) {
    for (const auto& it : sink_by_ssrc_) {
      if (it.second == sink) {
        ssrcs.insert(it.first);
      }
    }
  }
  return ssrcs;
}

bool RtpDemuxer::OnRtpPacket(const RtpPacketReceived& packet) {
  RtpPacketSinkInterface* sink = ResolveSink(packet);
  if (sink != nullptr) {
    sink->OnRtpPacket(packet);
    return true;
  }
  return false;
}

RtpPacketSinkInterface* RtpDemuxer::ResolveSink(
    const RtpPacketReceived& packet) {
  // See the BUNDLE spec for high level reference to this algorithm:
  // https://tools.ietf.org/html/draft-ietf-mmusic-sdp-bundle-negotiation-38#section-10.2

  // RSID and RRID are routed to the same sinks. If an RSID is specified on a
  // repair packet, it should be ignored and the RRID should be used.
  std::string packet_mid, packet_rsid;
  bool has_mid = use_mid_ && packet.GetExtension<RtpMid>(&packet_mid);
  bool has_rsid = packet.GetExtension<RepairedRtpStreamId>(&packet_rsid);
  if (!has_rsid) {
    has_rsid = packet.GetExtension<RtpStreamId>(&packet_rsid);
  }
  uint32_t ssrc = packet.Ssrc();

  // The BUNDLE spec says to drop any packets with unknown MIDs, even if the
  // SSRC is known/latched.
  if (has_mid && known_mids_.find(packet_mid) == known_mids_.end()) {
    return nullptr;
  }

  // Cache information we learn about SSRCs and IDs. We need to do this even if
  // there isn't a rule/sink yet because we might add an MID/RSID rule after
  // learning an MID/RSID<->SSRC association.

  std::string* mid = nullptr;
  if (has_mid) {
    mid_by_ssrc_[ssrc] = packet_mid;
    mid = &packet_mid;
  } else {
    // If the packet does not include a MID header extension, check if there is
    // a latched MID for the SSRC.
    const auto it = mid_by_ssrc_.find(ssrc);
    if (it != mid_by_ssrc_.end()) {
      mid = &it->second;
    }
  }

  std::string* rsid = nullptr;
  if (has_rsid) {
    rsid_by_ssrc_[ssrc] = packet_rsid;
    rsid = &packet_rsid;
  } else {
    // If the packet does not include an RRID/RSID header extension, check if
    // there is a latched RSID for the SSRC.
    const auto it = rsid_by_ssrc_.find(ssrc);
    if (it != rsid_by_ssrc_.end()) {
      rsid = &it->second;
    }
  }

  // If MID and/or RSID is specified, prioritize that for demuxing the packet.
  // The motivation behind the BUNDLE algorithm is that we trust these are used
  // deliberately by senders and are more likely to be correct than SSRC/payload
  // type which are included with every packet.
  // TODO(steveanton): According to the BUNDLE spec, new SSRC mappings are only
  //                   accepted if the packet's extended sequence number is
  //                   greater than that of the last SSRC mapping update.
  //                   https://tools.ietf.org/html/rfc7941#section-4.2.6
  if (mid != nullptr) {
    RtpPacketSinkInterface* sink_by_mid = ResolveSinkByMid(*mid, ssrc);
    if (sink_by_mid != nullptr) {
      return sink_by_mid;
    }

    // RSID is scoped to a given MID if both are included.
    if (rsid != nullptr) {
      RtpPacketSinkInterface* sink_by_mid_rsid =
          ResolveSinkByMidRsid(*mid, *rsid, ssrc);
      if (sink_by_mid_rsid != nullptr) {
        return sink_by_mid_rsid;
      }
    }

    // At this point, there is at least one sink added for this MID and an RSID
    // but either the packet does not have an RSID or it is for a different
    // RSID. This falls outside the BUNDLE spec so drop the packet.
    return nullptr;
  }

  // RSID can be used without MID as long as they are unique.
  if (rsid != nullptr) {
    RtpPacketSinkInterface* sink_by_rsid = ResolveSinkByRsid(*rsid, ssrc);
    if (sink_by_rsid != nullptr) {
      return sink_by_rsid;
    }
  }

  // We trust signaled SSRC more than payload type which is likely to conflict
  // between streams.
  const auto ssrc_sink_it = sink_by_ssrc_.find(ssrc);
  if (ssrc_sink_it != sink_by_ssrc_.end()) {
    return ssrc_sink_it->second;
  }

  // Legacy senders will only signal payload type, support that as last resort.
  return ResolveSinkByPayloadType(packet.PayloadType(), ssrc);
}

RtpPacketSinkInterface* RtpDemuxer::ResolveSinkByMid(absl::string_view mid,
                                                     uint32_t ssrc) {
  const auto it = sink_by_mid_.find(mid);
  if (it != sink_by_mid_.end()) {
    RtpPacketSinkInterface* sink = it->second;
    AddSsrcSinkBinding(ssrc, sink);
    return sink;
  }
  return nullptr;
}

RtpPacketSinkInterface* RtpDemuxer::ResolveSinkByMidRsid(absl::string_view mid,
                                                         absl::string_view rsid,
                                                         uint32_t ssrc) {
  const auto it = sink_by_mid_and_rsid_.find(
      std::make_pair(std::string(mid), std::string(rsid)));
  if (it != sink_by_mid_and_rsid_.end()) {
    RtpPacketSinkInterface* sink = it->second;
    AddSsrcSinkBinding(ssrc, sink);
    return sink;
  }
  return nullptr;
}

RtpPacketSinkInterface* RtpDemuxer::ResolveSinkByRsid(absl::string_view rsid,
                                                      uint32_t ssrc) {
  const auto it = sink_by_rsid_.find(rsid);
  if (it != sink_by_rsid_.end()) {
    RtpPacketSinkInterface* sink = it->second;
    AddSsrcSinkBinding(ssrc, sink);
    return sink;
  }
  return nullptr;
}

RtpPacketSinkInterface* RtpDemuxer::ResolveSinkByPayloadType(
    uint8_t payload_type,
    uint32_t ssrc) {
  const auto range = sinks_by_pt_.equal_range(payload_type);
  if (range.first != range.second) {
    auto it = range.first;
    const auto end = range.second;
    if (std::next(it) == end) {
      RtpPacketSinkInterface* sink = it->second;
      AddSsrcSinkBinding(ssrc, sink);
      return sink;
    }
  }
  return nullptr;
}

void RtpDemuxer::AddSsrcSinkBinding(uint32_t ssrc,
                                    RtpPacketSinkInterface* sink) {
  if (sink_by_ssrc_.size() >= kMaxSsrcBindings) {
    RTC_LOG(LS_WARNING) << "New SSRC=" << ssrc
                        << " sink binding ignored; limit of" << kMaxSsrcBindings
                        << " bindings has been reached.";
    return;
  }

  auto result = sink_by_ssrc_.emplace(ssrc, sink);
  auto it = result.first;
  bool inserted = result.second;
  if (inserted) {
    RTC_DLOG(LS_INFO) << "Added sink = " << sink
                      << " binding with SSRC=" << ssrc;
  } else if (it->second != sink) {
    RTC_DLOG(LS_INFO) << "Updated sink = " << sink
                      << " binding with SSRC=" << ssrc;
    it->second = sink;
  }
}

}  // namespace webrtc
