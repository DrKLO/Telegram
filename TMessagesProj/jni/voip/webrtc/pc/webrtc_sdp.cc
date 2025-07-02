/*
 *  Copyright 2011 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/webrtc_sdp.h"

#include <ctype.h>
#include <limits.h>

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <type_traits>
#include <unordered_map>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/ascii.h"
#include "absl/strings/match.h"
#include "api/candidate.h"
#include "api/jsep_ice_candidate.h"
#include "api/jsep_session_description.h"
#include "api/media_types.h"
// for RtpExtension
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/rtc_error.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_direction.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/base/rid_description.h"
#include "media/base/rtp_utils.h"
#include "media/base/stream_params.h"
#include "media/sctp/sctp_transport_internal.h"
#include "p2p/base/candidate_pair_interface.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "p2p/base/port_interface.h"
#include "p2p/base/transport_description.h"
#include "p2p/base/transport_info.h"
#include "pc/media_protocol_names.h"
#include "pc/media_session.h"
#include "pc/session_description.h"
#include "pc/simulcast_description.h"
#include "pc/simulcast_sdp_serializer.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/net_helper.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/strings/string_builder.h"

using cricket::AudioContentDescription;
using cricket::Candidate;
using cricket::Candidates;
using cricket::ContentInfo;
using cricket::ICE_CANDIDATE_COMPONENT_RTCP;
using cricket::ICE_CANDIDATE_COMPONENT_RTP;
using cricket::kApplicationSpecificBandwidth;
using cricket::kCodecParamMaxPTime;
using cricket::kCodecParamMinPTime;
using cricket::kCodecParamPTime;
using cricket::kTransportSpecificBandwidth;
using cricket::MediaContentDescription;
using cricket::MediaProtocolType;
using cricket::MediaType;
using cricket::RidDescription;
using cricket::RtpHeaderExtensions;
using cricket::SctpDataContentDescription;
using cricket::SimulcastDescription;
using cricket::SimulcastLayer;
using cricket::SimulcastLayerList;
using cricket::SsrcGroup;
using cricket::StreamParams;
using cricket::StreamParamsVec;
using cricket::TransportDescription;
using cricket::TransportInfo;
using cricket::UnsupportedContentDescription;
using cricket::VideoContentDescription;
using rtc::SocketAddress;

// TODO(deadbeef): Switch to using anonymous namespace rather than declaring
// everything "static".
namespace webrtc {

// Line type
// RFC 4566
// An SDP session description consists of a number of lines of text of
// the form:
// <type>=<value>
// where <type> MUST be exactly one case-significant character.

// Check if passed character is a "token-char" from RFC 4566.
// https://datatracker.ietf.org/doc/html/rfc4566#section-9
//    token-char =          %x21 / %x23-27 / %x2A-2B / %x2D-2E / %x30-39
//                         / %x41-5A / %x5E-7E
bool IsTokenChar(char ch) {
  return ch == 0x21 || (ch >= 0x23 && ch <= 0x27) || ch == 0x2a || ch == 0x2b ||
         ch == 0x2d || ch == 0x2e || (ch >= 0x30 && ch <= 0x39) ||
         (ch >= 0x41 && ch <= 0x5a) || (ch >= 0x5e && ch <= 0x7e);
}
static const int kLinePrefixLength = 2;  // Length of <type>=
static const char kLineTypeVersion = 'v';
static const char kLineTypeOrigin = 'o';
static const char kLineTypeSessionName = 's';
static const char kLineTypeSessionInfo = 'i';
static const char kLineTypeSessionUri = 'u';
static const char kLineTypeSessionEmail = 'e';
static const char kLineTypeSessionPhone = 'p';
static const char kLineTypeSessionBandwidth = 'b';
static const char kLineTypeTiming = 't';
static const char kLineTypeRepeatTimes = 'r';
static const char kLineTypeTimeZone = 'z';
static const char kLineTypeEncryptionKey = 'k';
static const char kLineTypeMedia = 'm';
static const char kLineTypeConnection = 'c';
static const char kLineTypeAttributes = 'a';

// Attributes
static const char kAttributeGroup[] = "group";
static const char kAttributeMid[] = "mid";
static const char kAttributeMsid[] = "msid";
static const char kAttributeBundleOnly[] = "bundle-only";
static const char kAttributeRtcpMux[] = "rtcp-mux";
static const char kAttributeRtcpReducedSize[] = "rtcp-rsize";
static const char kAttributeSsrc[] = "ssrc";
static const char kSsrcAttributeCname[] = "cname";
static const char kAttributeExtmapAllowMixed[] = "extmap-allow-mixed";
static const char kAttributeExtmap[] = "extmap";
// draft-alvestrand-mmusic-msid-01
// a=msid-semantic: WMS
// This is a legacy field supported only for Plan B semantics.
static const char kAttributeMsidSemantics[] = "msid-semantic";
static const char kMediaStreamSemantic[] = "WMS";
static const char kSsrcAttributeMsid[] = "msid";
static const char kDefaultMsid[] = "default";
static const char kNoStreamMsid[] = "-";
static const char kAttributeSsrcGroup[] = "ssrc-group";
static const char kAttributeCandidate[] = "candidate";
static const char kAttributeCandidateTyp[] = "typ";
static const char kAttributeCandidateRaddr[] = "raddr";
static const char kAttributeCandidateRport[] = "rport";
static const char kAttributeCandidateUfrag[] = "ufrag";
static const char kAttributeCandidatePwd[] = "pwd";
static const char kAttributeCandidateGeneration[] = "generation";
static const char kAttributeCandidateNetworkId[] = "network-id";
static const char kAttributeCandidateNetworkCost[] = "network-cost";
static const char kAttributeFingerprint[] = "fingerprint";
static const char kAttributeSetup[] = "setup";
static const char kAttributeFmtp[] = "fmtp";
static const char kAttributeRtpmap[] = "rtpmap";
static const char kAttributeSctpmap[] = "sctpmap";
static const char kAttributeRtcp[] = "rtcp";
static const char kAttributeIceUfrag[] = "ice-ufrag";
static const char kAttributeIcePwd[] = "ice-pwd";
static const char kAttributeIceLite[] = "ice-lite";
static const char kAttributeIceOption[] = "ice-options";
static const char kAttributeSendOnly[] = "sendonly";
static const char kAttributeRecvOnly[] = "recvonly";
static const char kAttributeRtcpFb[] = "rtcp-fb";
static const char kAttributeSendRecv[] = "sendrecv";
static const char kAttributeInactive[] = "inactive";
// draft-ietf-mmusic-sctp-sdp-26
// a=sctp-port, a=max-message-size
static const char kAttributeSctpPort[] = "sctp-port";
static const char kAttributeMaxMessageSize[] = "max-message-size";
static const int kDefaultSctpMaxMessageSize = 65536;
// draft-ietf-mmusic-sdp-simulcast-13
// a=simulcast
static const char kAttributeSimulcast[] = "simulcast";
// draft-ietf-mmusic-rid-15
// a=rid
static const char kAttributeRid[] = "rid";
static const char kAttributePacketization[] = "packetization";

// Experimental flags
static const char kAttributeXGoogleFlag[] = "x-google-flag";
static const char kValueConference[] = "conference";

static const char kAttributeRtcpRemoteEstimate[] = "remote-net-estimate";

// Candidate
static const char kCandidateHost[] = "host";
static const char kCandidateSrflx[] = "srflx";
static const char kCandidatePrflx[] = "prflx";
static const char kCandidateRelay[] = "relay";
static const char kTcpCandidateType[] = "tcptype";

// rtc::StringBuilder doesn't have a << overload for chars, while rtc::split and
// rtc::tokenize_first both take a char delimiter. To handle both cases these
// constants come in pairs of a chars and length-one strings.
static const char kSdpDelimiterEqual[] = "=";
static const char kSdpDelimiterEqualChar = '=';
static const char kSdpDelimiterSpace[] = " ";
static const char kSdpDelimiterSpaceChar = ' ';
static const char kSdpDelimiterColon[] = ":";
static const char kSdpDelimiterColonChar = ':';
static const char kSdpDelimiterSemicolon[] = ";";
static const char kSdpDelimiterSemicolonChar = ';';
static const char kSdpDelimiterSlashChar = '/';
static const char kNewLineChar = '\n';
static const char kReturnChar = '\r';
static const char kLineBreak[] = "\r\n";

// TODO(deadbeef): Generate the Session and Time description
// instead of hardcoding.
static const char kSessionVersion[] = "v=0";
// RFC 4566
static const char kSessionOriginUsername[] = "-";
static const char kSessionOriginSessionId[] = "0";
static const char kSessionOriginSessionVersion[] = "0";
static const char kSessionOriginNettype[] = "IN";
static const char kSessionOriginAddrtype[] = "IP4";
static const char kSessionOriginAddress[] = "127.0.0.1";
static const char kSessionName[] = "s=-";
static const char kTimeDescription[] = "t=0 0";
static const char kAttrGroup[] = "a=group:BUNDLE";
static const char kConnectionNettype[] = "IN";
static const char kConnectionIpv4Addrtype[] = "IP4";
static const char kConnectionIpv6Addrtype[] = "IP6";
static const char kMediaTypeVideo[] = "video";
static const char kMediaTypeAudio[] = "audio";
static const char kMediaTypeData[] = "application";
static const char kMediaPortRejected[] = "0";
// draft-ietf-mmusic-trickle-ice-01
// When no candidates have been gathered, set the connection
// address to IP6 ::.
// TODO(perkj): FF can not parse IP6 ::. See http://crbug/430333
// Use IPV4 per default.
static const char kDummyAddress[] = "0.0.0.0";
static const char kDummyPort[] = "9";

static const char kDefaultSctpmapProtocol[] = "webrtc-datachannel";

// RTP payload type is in the 0-127 range. Use -1 to indicate "all" payload
// types.
const int kWildcardPayloadType = -1;

// Maximum number of channels allowed.
static const size_t kMaxNumberOfChannels = 24;

struct SsrcInfo {
  uint32_t ssrc_id;
  std::string cname;
  std::string stream_id;
  std::string track_id;
};
using SsrcInfoVec = std::vector<SsrcInfo>;
using SsrcGroupVec = std::vector<SsrcGroup>;

static void BuildMediaDescription(const ContentInfo* content_info,
                                  const TransportInfo* transport_info,
                                  const cricket::MediaType media_type,
                                  const std::vector<Candidate>& candidates,
                                  int msid_signaling,
                                  std::string* message);
static void BuildMediaLine(const cricket::MediaType media_type,
                           const ContentInfo* content_info,
                           const MediaContentDescription* media_desc,
                           std::string* message);
static void BuildRtpContentAttributes(const MediaContentDescription* media_desc,
                                      const cricket::MediaType media_type,
                                      int msid_signaling,
                                      std::string* message);
static void BuildRtpHeaderExtensions(const RtpHeaderExtensions& extensions,
                                     std::string* message);
static void BuildRtpmap(const MediaContentDescription* media_desc,
                        const cricket::MediaType media_type,
                        std::string* message);
static void BuildCandidate(const std::vector<Candidate>& candidates,
                           bool include_ufrag,
                           std::string* message);
static void BuildIceUfragPwd(const TransportInfo* transport_info,
                             std::string* message);
static void BuildDtlsFingerprintSetup(const TransportInfo* transport_info,
                                      std::string* message);
static void BuildIceOptions(const std::vector<std::string>& transport_options,
                            std::string* message);
static bool ParseSessionDescription(absl::string_view message,
                                    size_t* pos,
                                    std::string* session_id,
                                    std::string* session_version,
                                    TransportDescription* session_td,
                                    RtpHeaderExtensions* session_extmaps,
                                    rtc::SocketAddress* connection_addr,
                                    cricket::SessionDescription* desc,
                                    SdpParseError* error);
static bool ParseMediaDescription(
    absl::string_view message,
    const TransportDescription& session_td,
    const RtpHeaderExtensions& session_extmaps,
    size_t* pos,
    const rtc::SocketAddress& session_connection_addr,
    cricket::SessionDescription* desc,
    std::vector<std::unique_ptr<JsepIceCandidate>>* candidates,
    SdpParseError* error);
static bool ParseContent(
    absl::string_view message,
    const cricket::MediaType media_type,
    int mline_index,
    absl::string_view protocol,
    const std::vector<int>& payload_types,
    size_t* pos,
    std::string* content_name,
    bool* bundle_only,
    int* msid_signaling,
    MediaContentDescription* media_desc,
    TransportDescription* transport,
    std::vector<std::unique_ptr<JsepIceCandidate>>* candidates,
    SdpParseError* error);
static bool ParseGroupAttribute(absl::string_view line,
                                cricket::SessionDescription* desc,
                                SdpParseError* error);
static bool ParseSsrcAttribute(absl::string_view line,
                               SsrcInfoVec* ssrc_infos,
                               int* msid_signaling,
                               SdpParseError* error);
static bool ParseSsrcGroupAttribute(absl::string_view line,
                                    SsrcGroupVec* ssrc_groups,
                                    SdpParseError* error);
static bool ParseRtpmapAttribute(absl::string_view line,
                                 const cricket::MediaType media_type,
                                 const std::vector<int>& payload_types,
                                 MediaContentDescription* media_desc,
                                 SdpParseError* error);
static bool ParseFmtpAttributes(absl::string_view line,
                                const cricket::MediaType media_type,
                                MediaContentDescription* media_desc,
                                SdpParseError* error);
static bool ParseFmtpParam(absl::string_view line,
                           std::string* parameter,
                           std::string* value,
                           SdpParseError* error);
static bool ParsePacketizationAttribute(absl::string_view line,
                                        const cricket::MediaType media_type,
                                        MediaContentDescription* media_desc,
                                        SdpParseError* error);
static bool ParseRtcpFbAttribute(absl::string_view line,
                                 const cricket::MediaType media_type,
                                 MediaContentDescription* media_desc,
                                 SdpParseError* error);
static bool ParseIceOptions(absl::string_view line,
                            std::vector<std::string>* transport_options,
                            SdpParseError* error);
static bool ParseExtmap(absl::string_view line,
                        RtpExtension* extmap,
                        SdpParseError* error);
static bool ParseFingerprintAttribute(
    absl::string_view line,
    std::unique_ptr<rtc::SSLFingerprint>* fingerprint,
    SdpParseError* error);
static bool ParseDtlsSetup(absl::string_view line,
                           cricket::ConnectionRole* role,
                           SdpParseError* error);
static bool ParseMsidAttribute(absl::string_view line,
                               std::vector<std::string>* stream_ids,
                               std::string* track_id,
                               SdpParseError* error);

static void RemoveInvalidRidDescriptions(const std::vector<int>& payload_types,
                                         std::vector<RidDescription>* rids);

static SimulcastLayerList RemoveRidsFromSimulcastLayerList(
    const std::set<std::string>& to_remove,
    const SimulcastLayerList& layers);

static void RemoveInvalidRidsFromSimulcast(
    const std::vector<RidDescription>& rids,
    SimulcastDescription* simulcast);

// Helper functions

// Below ParseFailed*** functions output the line that caused the parsing
// failure and the detailed reason (`description`) of the failure to `error`.
// The functions always return false so that they can be used directly in the
// following way when error happens:
// "return ParseFailed***(...);"

// The line starting at `line_start` of `message` is the failing line.
// The reason for the failure should be provided in the `description`.
// An example of a description could be "unknown character".
static bool ParseFailed(absl::string_view message,
                        size_t line_start,
                        std::string description,
                        SdpParseError* error) {
  // Get the first line of `message` from `line_start`.
  absl::string_view first_line;
  size_t line_end = message.find(kNewLineChar, line_start);
  if (line_end != std::string::npos) {
    if (line_end > 0 && (message.at(line_end - 1) == kReturnChar)) {
      --line_end;
    }
    first_line = message.substr(line_start, (line_end - line_start));
  } else {
    first_line = message.substr(line_start);
  }

  RTC_LOG(LS_ERROR) << "Failed to parse: \"" << first_line
                    << "\". Reason: " << description;
  if (error) {
    // TODO(bugs.webrtc.org/13220): In C++17, we can use plain assignment, with
    // a string_view on the right hand side.
    error->line.assign(first_line.data(), first_line.size());
    error->description = std::move(description);
  }
  return false;
}

// `line` is the failing line. The reason for the failure should be
// provided in the `description`.
static bool ParseFailed(absl::string_view line,
                        std::string description,
                        SdpParseError* error) {
  return ParseFailed(line, 0, std::move(description), error);
}

// Parses failure where the failing SDP line isn't know or there are multiple
// failing lines.
static bool ParseFailed(std::string description, SdpParseError* error) {
  return ParseFailed("", std::move(description), error);
}

// `line` is the failing line. The failure is due to the fact that `line`
// doesn't have `expected_fields` fields.
static bool ParseFailedExpectFieldNum(absl::string_view line,
                                      int expected_fields,
                                      SdpParseError* error) {
  rtc::StringBuilder description;
  description << "Expects " << expected_fields << " fields.";
  return ParseFailed(line, description.Release(), error);
}

// `line` is the failing line. The failure is due to the fact that `line` has
// less than `expected_min_fields` fields.
static bool ParseFailedExpectMinFieldNum(absl::string_view line,
                                         int expected_min_fields,
                                         SdpParseError* error) {
  rtc::StringBuilder description;
  description << "Expects at least " << expected_min_fields << " fields.";
  return ParseFailed(line, description.Release(), error);
}

// `line` is the failing line. The failure is due to the fact that it failed to
// get the value of `attribute`.
static bool ParseFailedGetValue(absl::string_view line,
                                absl::string_view attribute,
                                SdpParseError* error) {
  rtc::StringBuilder description;
  description << "Failed to get the value of attribute: " << attribute;
  return ParseFailed(line, description.Release(), error);
}

// The line starting at `line_start` of `message` is the failing line. The
// failure is due to the line type (e.g. the "m" part of the "m-line")
// not matching what is expected. The expected line type should be
// provided as `line_type`.
static bool ParseFailedExpectLine(absl::string_view message,
                                  size_t line_start,
                                  const char line_type,
                                  absl::string_view line_value,
                                  SdpParseError* error) {
  rtc::StringBuilder description;
  description << "Expect line: " << std::string(1, line_type) << "="
              << line_value;
  return ParseFailed(message, line_start, description.Release(), error);
}

static bool AddLine(absl::string_view line, std::string* message) {
  if (!message)
    return false;

  message->append(line.data(), line.size());
  message->append(kLineBreak);
  return true;
}

// Trim return character, if any.
static absl::string_view TrimReturnChar(absl::string_view line) {
  if (!line.empty() && line.back() == kReturnChar) {
    line.remove_suffix(1);
  }
  return line;
}

// Gets line of `message` starting at `pos`, and checks overall SDP syntax. On
// success, advances `pos` to the next line.
static absl::optional<absl::string_view> GetLine(absl::string_view message,
                                                 size_t* pos) {
  size_t line_end = message.find(kNewLineChar, *pos);
  if (line_end == absl::string_view::npos) {
    return absl::nullopt;
  }
  absl::string_view line =
      TrimReturnChar(message.substr(*pos, line_end - *pos));

  // RFC 4566
  // An SDP session description consists of a number of lines of text of
  // the form:
  // <type>=<value>
  // where <type> MUST be exactly one case-significant character and
  // <value> is structured text whose format depends on <type>.
  // Whitespace MUST NOT be used on either side of the "=" sign.
  //
  // However, an exception to the whitespace rule is made for "s=", since
  // RFC4566 also says:
  //
  //   If a session has no meaningful name, the value "s= " SHOULD be used
  //   (i.e., a single space as the session name).
  if (line.length() < 3 || !islower(static_cast<unsigned char>(line[0])) ||
      line[1] != kSdpDelimiterEqualChar ||
      (line[0] != kLineTypeSessionName && line[2] == kSdpDelimiterSpaceChar)) {
    return absl::nullopt;
  }
  *pos = line_end + 1;
  return line;
}

// Init `os` to "`type`=`value`".
static void InitLine(const char type,
                     absl::string_view value,
                     rtc::StringBuilder* os) {
  os->Clear();
  *os << std::string(1, type) << kSdpDelimiterEqual << value;
}

// Init `os` to "a=`attribute`".
static void InitAttrLine(absl::string_view attribute, rtc::StringBuilder* os) {
  InitLine(kLineTypeAttributes, attribute, os);
}

// Writes a SDP attribute line based on `attribute` and `value` to `message`.
static void AddAttributeLine(absl::string_view attribute,
                             int value,
                             std::string* message) {
  rtc::StringBuilder os;
  InitAttrLine(attribute, &os);
  os << kSdpDelimiterColon << value;
  AddLine(os.str(), message);
}

static bool IsLineType(absl::string_view message,
                       const char type,
                       size_t line_start) {
  if (message.size() < line_start + kLinePrefixLength) {
    return false;
  }
  return (message[line_start] == type &&
          message[line_start + 1] == kSdpDelimiterEqualChar);
}

static bool IsLineType(absl::string_view line, const char type) {
  return IsLineType(line, type, 0);
}

static absl::optional<absl::string_view>
GetLineWithType(absl::string_view message, size_t* pos, const char type) {
  if (IsLineType(message, type, *pos)) {
    return GetLine(message, pos);
  }
  return absl::nullopt;
}

static bool HasAttribute(absl::string_view line, absl::string_view attribute) {
  if (line.compare(kLinePrefixLength, attribute.size(), attribute) == 0) {
    // Make sure that the match is not only a partial match. If length of
    // strings doesn't match, the next character of the line must be ':' or ' '.
    // This function is also used for media descriptions (e.g., "m=audio 9..."),
    // hence the need to also allow space in the end.
    RTC_CHECK_LE(kLinePrefixLength + attribute.size(), line.size());
    if ((kLinePrefixLength + attribute.size()) == line.size() ||
        line[kLinePrefixLength + attribute.size()] == kSdpDelimiterColonChar ||
        line[kLinePrefixLength + attribute.size()] == kSdpDelimiterSpaceChar) {
      return true;
    }
  }
  return false;
}

static bool AddSsrcLine(uint32_t ssrc_id,
                        absl::string_view attribute,
                        absl::string_view value,
                        std::string* message) {
  // RFC 5576
  // a=ssrc:<ssrc-id> <attribute>:<value>
  rtc::StringBuilder os;
  InitAttrLine(kAttributeSsrc, &os);
  os << kSdpDelimiterColon << ssrc_id << kSdpDelimiterSpace << attribute
     << kSdpDelimiterColon << value;
  return AddLine(os.str(), message);
}

// Get value only from <attribute>:<value>.
static bool GetValue(absl::string_view message,
                     absl::string_view attribute,
                     std::string* value,
                     SdpParseError* error) {
  std::string leftpart;
  if (!rtc::tokenize_first(message, kSdpDelimiterColonChar, &leftpart, value)) {
    return ParseFailedGetValue(message, attribute, error);
  }
  // The left part should end with the expected attribute.
  if (leftpart.length() < attribute.length() ||
      absl::string_view(leftpart).compare(
          leftpart.length() - attribute.length(), attribute.length(),
          attribute) != 0) {
    return ParseFailedGetValue(message, attribute, error);
  }
  return true;
}

// Get a single [token] from <attribute>:<token>
static bool GetSingleTokenValue(absl::string_view message,
                                absl::string_view attribute,
                                std::string* value,
                                SdpParseError* error) {
  if (!GetValue(message, attribute, value, error)) {
    return false;
  }
  if (!absl::c_all_of(absl::string_view(*value), IsTokenChar)) {
    rtc::StringBuilder description;
    description << "Illegal character found in the value of " << attribute;
    return ParseFailed(message, description.Release(), error);
  }
  return true;
}

static bool CaseInsensitiveFind(std::string str1, std::string str2) {
  absl::c_transform(str1, str1.begin(), ::tolower);
  absl::c_transform(str2, str2.begin(), ::tolower);
  return str1.find(str2) != std::string::npos;
}

template <class T>
static bool GetValueFromString(absl::string_view line,
                               absl::string_view s,
                               T* t,
                               SdpParseError* error) {
  if (!rtc::FromString(s, t)) {
    rtc::StringBuilder description;
    description << "Invalid value: " << s << ".";
    return ParseFailed(line, description.Release(), error);
  }
  return true;
}

static bool GetPayloadTypeFromString(absl::string_view line,
                                     absl::string_view s,
                                     int* payload_type,
                                     SdpParseError* error) {
  return GetValueFromString(line, s, payload_type, error) &&
         cricket::IsValidRtpPayloadType(*payload_type);
}

// Creates a StreamParams track in the case when no SSRC lines are signaled.
// This is a track that does not contain SSRCs and only contains
// stream_ids/track_id if it's signaled with a=msid lines.
void CreateTrackWithNoSsrcs(const std::vector<std::string>& msid_stream_ids,
                            absl::string_view msid_track_id,
                            const std::vector<RidDescription>& rids,
                            StreamParamsVec* tracks) {
  StreamParams track;
  if (msid_track_id.empty() && rids.empty()) {
    // We only create an unsignaled track if a=msid lines were signaled.
    RTC_LOG(LS_INFO) << "MSID not signaled, skipping creation of StreamParams";
    return;
  }
  track.set_stream_ids(msid_stream_ids);
  track.id = std::string(msid_track_id);
  track.set_rids(rids);
  tracks->push_back(track);
}

// Creates the StreamParams tracks, for the case when SSRC lines are signaled.
// `msid_stream_ids` and `msid_track_id` represent the stream/track ID from the
// "a=msid" attribute, if it exists. They are empty if the attribute does not
// exist. We prioritize getting stream_ids/track_ids signaled in a=msid lines.
void CreateTracksFromSsrcInfos(const SsrcInfoVec& ssrc_infos,
                               const std::vector<std::string>& msid_stream_ids,
                               absl::string_view msid_track_id,
                               StreamParamsVec* tracks,
                               int msid_signaling) {
  RTC_DCHECK(tracks);
  for (const SsrcInfo& ssrc_info : ssrc_infos) {
    // According to https://tools.ietf.org/html/rfc5576#section-6.1, the CNAME
    // attribute is mandatory, but we relax that restriction.
    if (ssrc_info.cname.empty()) {
      RTC_LOG(LS_WARNING) << "CNAME attribute missing for SSRC "
                          << ssrc_info.ssrc_id;
    }
    std::vector<std::string> stream_ids;
    std::string track_id;
    if (msid_signaling & cricket::kMsidSignalingMediaSection) {
      // This is the case with Unified Plan SDP msid signaling.
      stream_ids = msid_stream_ids;
      track_id = std::string(msid_track_id);
    } else if (msid_signaling & cricket::kMsidSignalingSsrcAttribute) {
      // This is the case with Plan B SDP msid signaling.
      stream_ids.push_back(ssrc_info.stream_id);
      track_id = ssrc_info.track_id;
    } else if (msid_signaling == cricket::kMsidSignalingNotUsed) {
      // Since no media streams isn't supported with older SDP signaling, we
      // use a default stream id.
      stream_ids.push_back(kDefaultMsid);
    }

    auto track_it = absl::c_find_if(
        *tracks,
        [track_id](const StreamParams& track) { return track.id == track_id; });
    if (track_it == tracks->end()) {
      // If we don't find an existing track, create a new one.
      tracks->push_back(StreamParams());
      track_it = tracks->end() - 1;
    }
    StreamParams& track = *track_it;
    track.add_ssrc(ssrc_info.ssrc_id);
    track.cname = ssrc_info.cname;
    track.set_stream_ids(stream_ids);
    track.id = track_id;
  }
  for (StreamParams& stream : *tracks) {
    // If a track ID wasn't populated from the SSRC attributes OR the
    // msid attribute, use default/random values. This happens after
    // deduplication.
    if (stream.id.empty()) {
      stream.id = rtc::CreateRandomString(8);
    }
  }
}

void GetMediaStreamIds(const ContentInfo* content,
                       std::set<std::string>* labels) {
  for (const StreamParams& stream_params :
       content->media_description()->streams()) {
    for (const std::string& stream_id : stream_params.stream_ids()) {
      labels->insert(stream_id);
    }
  }
}

// Get ip and port of the default destination from the `candidates` with the
// given value of `component_id`. The default candidate should be the one most
// likely to work, typically IPv4 relay.
// RFC 5245
// The value of `component_id` currently supported are 1 (RTP) and 2 (RTCP).
// TODO(deadbeef): Decide the default destination in webrtcsession and
// pass it down via SessionDescription.
static void GetDefaultDestination(const std::vector<Candidate>& candidates,
                                  int component_id,
                                  std::string* port,
                                  std::string* ip,
                                  std::string* addr_type) {
  *addr_type = kConnectionIpv4Addrtype;
  *port = kDummyPort;
  *ip = kDummyAddress;
  int current_preference = 0;  // Start with lowest preference
  int current_family = AF_UNSPEC;
  for (const Candidate& candidate : candidates) {
    if (candidate.component() != component_id) {
      continue;
    }
    // Default destination should be UDP only.
    if (candidate.protocol() != cricket::UDP_PROTOCOL_NAME) {
      continue;
    }
    const int preference = candidate.type_preference();
    const int family = candidate.address().ipaddr().family();
    // See if this candidate is more preferable then the current one if it's the
    // same family. Or if the current family is IPv4 already so we could safely
    // ignore all IPv6 ones. WebRTC bug 4269.
    // http://code.google.com/p/webrtc/issues/detail?id=4269
    if ((preference <= current_preference && current_family == family) ||
        (current_family == AF_INET && family == AF_INET6)) {
      continue;
    }
    if (family == AF_INET) {
      addr_type->assign(kConnectionIpv4Addrtype);
    } else if (family == AF_INET6) {
      addr_type->assign(kConnectionIpv6Addrtype);
    }
    current_preference = preference;
    current_family = family;
    *port = candidate.address().PortAsString();
    *ip = candidate.address().ipaddr().ToString();
  }
}

// Gets "a=rtcp" line if found default RTCP candidate from `candidates`.
static std::string GetRtcpLine(const std::vector<Candidate>& candidates) {
  std::string rtcp_line, rtcp_port, rtcp_ip, addr_type;
  GetDefaultDestination(candidates, ICE_CANDIDATE_COMPONENT_RTCP, &rtcp_port,
                        &rtcp_ip, &addr_type);
  // Found default RTCP candidate.
  // RFC 5245
  // If the agent is utilizing RTCP, it MUST encode the RTCP candidate
  // using the a=rtcp attribute as defined in RFC 3605.

  // RFC 3605
  // rtcp-attribute =  "a=rtcp:" port  [nettype space addrtype space
  // connection-address] CRLF
  rtc::StringBuilder os;
  InitAttrLine(kAttributeRtcp, &os);
  os << kSdpDelimiterColon << rtcp_port << " " << kConnectionNettype << " "
     << addr_type << " " << rtcp_ip;
  rtcp_line = os.str();
  return rtcp_line;
}

// Get candidates according to the mline index from SessionDescriptionInterface.
static void GetCandidatesByMindex(const SessionDescriptionInterface& desci,
                                  int mline_index,
                                  std::vector<Candidate>* candidates) {
  if (!candidates) {
    return;
  }
  const IceCandidateCollection* cc = desci.candidates(mline_index);
  for (size_t i = 0; i < cc->count(); ++i) {
    const IceCandidateInterface* candidate = cc->at(i);
    candidates->push_back(candidate->candidate());
  }
}

static bool IsValidPort(int port) {
  return port >= 0 && port <= 65535;
}

std::string SdpSerialize(const JsepSessionDescription& jdesc) {
  const cricket::SessionDescription* desc = jdesc.description();
  if (!desc) {
    return "";
  }

  std::string message;

  // Session Description.
  AddLine(kSessionVersion, &message);
  // Session Origin
  // RFC 4566
  // o=<username> <sess-id> <sess-version> <nettype> <addrtype>
  // <unicast-address>
  rtc::StringBuilder os;
  InitLine(kLineTypeOrigin, kSessionOriginUsername, &os);
  const std::string& session_id =
      jdesc.session_id().empty() ? kSessionOriginSessionId : jdesc.session_id();
  const std::string& session_version = jdesc.session_version().empty()
                                           ? kSessionOriginSessionVersion
                                           : jdesc.session_version();
  os << " " << session_id << " " << session_version << " "
     << kSessionOriginNettype << " " << kSessionOriginAddrtype << " "
     << kSessionOriginAddress;
  AddLine(os.str(), &message);
  AddLine(kSessionName, &message);

  // Time Description.
  AddLine(kTimeDescription, &message);

  // BUNDLE Groups
  std::vector<const cricket::ContentGroup*> groups =
      desc->GetGroupsByName(cricket::GROUP_TYPE_BUNDLE);
  for (const cricket::ContentGroup* group : groups) {
    std::string group_line = kAttrGroup;
    RTC_DCHECK(group != NULL);
    for (const std::string& content_name : group->content_names()) {
      group_line.append(" ");
      group_line.append(content_name);
    }
    AddLine(group_line, &message);
  }

  // Mixed one- and two-byte header extension.
  if (desc->extmap_allow_mixed()) {
    InitAttrLine(kAttributeExtmapAllowMixed, &os);
    AddLine(os.str(), &message);
  }

  // MediaStream semantics.
  // TODO(bugs.webrtc.org/10421): Change to & cricket::kMsidSignalingSemantic
  // when we think it's safe to do so, so that we gradually fade out this old
  // line that was removed from the specification.
  if (desc->msid_signaling() != cricket::kMsidSignalingNotUsed) {
    InitAttrLine(kAttributeMsidSemantics, &os);
    os << kSdpDelimiterColon << " " << kMediaStreamSemantic;

    // TODO(bugs.webrtc.org/10421): this code only looks at the first
    // audio/video content. Fixing that might result in much larger SDP and the
    // msid-semantic line should eventually go away so this is not worth fixing.
    std::set<std::string> media_stream_ids;
    const ContentInfo* audio_content = GetFirstAudioContent(desc);
    if (audio_content)
      GetMediaStreamIds(audio_content, &media_stream_ids);

    const ContentInfo* video_content = GetFirstVideoContent(desc);
    if (video_content)
      GetMediaStreamIds(video_content, &media_stream_ids);

    for (const std::string& id : media_stream_ids) {
      os << " " << id;
    }
    AddLine(os.str(), &message);
  }

  // a=ice-lite
  //
  // TODO(deadbeef): It's weird that we need to iterate TransportInfos for
  // this, when it's a session-level attribute. It really should be moved to a
  // session-level structure like SessionDescription.
  for (const cricket::TransportInfo& transport : desc->transport_infos()) {
    if (transport.description.ice_mode == cricket::ICEMODE_LITE) {
      InitAttrLine(kAttributeIceLite, &os);
      AddLine(os.str(), &message);
      break;
    }
  }

  // Preserve the order of the media contents.
  int mline_index = -1;
  for (const ContentInfo& content : desc->contents()) {
    std::vector<Candidate> candidates;
    GetCandidatesByMindex(jdesc, ++mline_index, &candidates);
    BuildMediaDescription(&content, desc->GetTransportInfoByName(content.name),
                          content.media_description()->type(), candidates,
                          desc->msid_signaling(), &message);
  }
  return message;
}

// Serializes the passed in IceCandidateInterface to a SDP string.
// candidate - The candidate to be serialized.
std::string SdpSerializeCandidate(const IceCandidateInterface& candidate) {
  return SdpSerializeCandidate(candidate.candidate());
}

// Serializes a cricket Candidate.
std::string SdpSerializeCandidate(const cricket::Candidate& candidate) {
  std::string message;
  std::vector<cricket::Candidate> candidates(1, candidate);
  BuildCandidate(candidates, true, &message);
  // From WebRTC draft section 4.8.1.1 candidate-attribute will be
  // just candidate:<candidate> not a=candidate:<blah>CRLF
  RTC_DCHECK(message.find("a=") == 0);
  message.erase(0, 2);
  RTC_DCHECK(message.find(kLineBreak) == message.size() - 2);
  message.resize(message.size() - 2);
  return message;
}

bool SdpDeserialize(absl::string_view message,
                    JsepSessionDescription* jdesc,
                    SdpParseError* error) {
  std::string session_id;
  std::string session_version;
  TransportDescription session_td("", "");
  RtpHeaderExtensions session_extmaps;
  rtc::SocketAddress session_connection_addr;
  auto desc = std::make_unique<cricket::SessionDescription>();
  size_t current_pos = 0;

  // Session Description
  if (!ParseSessionDescription(message, &current_pos, &session_id,
                               &session_version, &session_td, &session_extmaps,
                               &session_connection_addr, desc.get(), error)) {
    return false;
  }

  // Media Description
  std::vector<std::unique_ptr<JsepIceCandidate>> candidates;
  if (!ParseMediaDescription(message, session_td, session_extmaps, &current_pos,
                             session_connection_addr, desc.get(), &candidates,
                             error)) {
    return false;
  }

  jdesc->Initialize(std::move(desc), session_id, session_version);

  for (const auto& candidate : candidates) {
    jdesc->AddCandidate(candidate.get());
  }
  return true;
}

bool SdpDeserializeCandidate(absl::string_view message,
                             JsepIceCandidate* jcandidate,
                             SdpParseError* error) {
  RTC_DCHECK(jcandidate != NULL);
  Candidate candidate;
  if (!ParseCandidate(message, &candidate, error, true)) {
    return false;
  }
  jcandidate->SetCandidate(candidate);
  return true;
}

bool SdpDeserializeCandidate(absl::string_view transport_name,
                             absl::string_view message,
                             cricket::Candidate* candidate,
                             SdpParseError* error) {
  RTC_DCHECK(candidate != nullptr);
  if (!ParseCandidate(message, candidate, error, true)) {
    return false;
  }
  candidate->set_transport_name(transport_name);
  return true;
}

bool ParseCandidate(absl::string_view message,
                    Candidate* candidate,
                    SdpParseError* error,
                    bool is_raw) {
  RTC_DCHECK(candidate != NULL);

  // Makes sure `message` contains only one line.
  absl::string_view first_line;

  size_t line_end = message.find(kNewLineChar);
  if (line_end == absl::string_view::npos) {
    first_line = message;
  } else if (line_end + 1 == message.size()) {
    first_line = message.substr(0, line_end);
  } else {
    return ParseFailed(message, 0, "Expect one line only", error);
  }

  // Trim return char, if any.
  first_line = TrimReturnChar(first_line);

  // From WebRTC draft section 4.8.1.1 candidate-attribute should be
  // candidate:<candidate> when trickled, but we still support
  // a=candidate:<blah>CRLF for backward compatibility and for parsing a line
  // from the SDP.
  if (IsLineType(first_line, kLineTypeAttributes)) {
    first_line = first_line.substr(kLinePrefixLength);
  }

  std::string attribute_candidate;
  std::string candidate_value;

  // `first_line` must be in the form of "candidate:<value>".
  if (!rtc::tokenize_first(first_line, kSdpDelimiterColonChar,
                           &attribute_candidate, &candidate_value) ||
      attribute_candidate != kAttributeCandidate) {
    if (is_raw) {
      rtc::StringBuilder description;
      description << "Expect line: " << kAttributeCandidate
                  << ":"
                     "<candidate-str>";
      return ParseFailed(first_line, 0, description.Release(), error);
    } else {
      return ParseFailedExpectLine(first_line, 0, kLineTypeAttributes,
                                   kAttributeCandidate, error);
    }
  }

  std::vector<absl::string_view> fields =
      rtc::split(candidate_value, kSdpDelimiterSpaceChar);

  // RFC 5245
  // a=candidate:<foundation> <component-id> <transport> <priority>
  // <connection-address> <port> typ <candidate-types>
  // [raddr <connection-address>] [rport <port>]
  // *(SP extension-att-name SP extension-att-value)
  const size_t expected_min_fields = 8;
  if (fields.size() < expected_min_fields ||
      (fields[6] != kAttributeCandidateTyp)) {
    return ParseFailedExpectMinFieldNum(first_line, expected_min_fields, error);
  }
  const absl::string_view foundation = fields[0];

  int component_id = 0;
  if (!GetValueFromString(first_line, fields[1], &component_id, error)) {
    return false;
  }
  const absl::string_view transport = fields[2];
  uint32_t priority = 0;
  if (!GetValueFromString(first_line, fields[3], &priority, error)) {
    return false;
  }
  const absl::string_view connection_address = fields[4];
  int port = 0;
  if (!GetValueFromString(first_line, fields[5], &port, error)) {
    return false;
  }
  if (!IsValidPort(port)) {
    return ParseFailed(first_line, "Invalid port number.", error);
  }
  SocketAddress address(connection_address, port);

  absl::optional<cricket::ProtocolType> protocol =
      cricket::StringToProto(transport);
  if (!protocol) {
    return ParseFailed(first_line, "Unsupported transport type.", error);
  }
  bool tcp_protocol = false;
  switch (*protocol) {
    // Supported protocols.
    case cricket::PROTO_UDP:
      break;
    case cricket::PROTO_TCP:
    case cricket::PROTO_SSLTCP:
      tcp_protocol = true;
      break;
    default:
      return ParseFailed(first_line, "Unsupported transport type.", error);
  }

  absl::string_view candidate_type;
  const absl::string_view type = fields[7];
  if (type == kCandidateHost) {
    candidate_type = cricket::LOCAL_PORT_TYPE;
  } else if (type == kCandidateSrflx) {
    candidate_type = cricket::STUN_PORT_TYPE;
  } else if (type == kCandidateRelay) {
    candidate_type = cricket::RELAY_PORT_TYPE;
  } else if (type == kCandidatePrflx) {
    candidate_type = cricket::PRFLX_PORT_TYPE;
  } else {
    return ParseFailed(first_line, "Unsupported candidate type.", error);
  }

  size_t current_position = expected_min_fields;
  SocketAddress related_address;
  // The 2 optional fields for related address
  // [raddr <connection-address>] [rport <port>]
  if (fields.size() >= (current_position + 2) &&
      fields[current_position] == kAttributeCandidateRaddr) {
    related_address.SetIP(fields[++current_position]);
    ++current_position;
  }
  if (fields.size() >= (current_position + 2) &&
      fields[current_position] == kAttributeCandidateRport) {
    int port = 0;
    if (!GetValueFromString(first_line, fields[++current_position], &port,
                            error)) {
      return false;
    }
    if (!IsValidPort(port)) {
      return ParseFailed(first_line, "Invalid port number.", error);
    }
    related_address.SetPort(port);
    ++current_position;
  }

  // If this is a TCP candidate, it has additional extension as defined in
  // RFC 6544.
  absl::string_view tcptype;
  if (fields.size() >= (current_position + 2) &&
      fields[current_position] == kTcpCandidateType) {
    tcptype = fields[++current_position];
    ++current_position;

    if (tcptype != cricket::TCPTYPE_ACTIVE_STR &&
        tcptype != cricket::TCPTYPE_PASSIVE_STR &&
        tcptype != cricket::TCPTYPE_SIMOPEN_STR) {
      return ParseFailed(first_line, "Invalid TCP candidate type.", error);
    }

    if (!tcp_protocol) {
      return ParseFailed(first_line, "Invalid non-TCP candidate", error);
    }
  } else if (tcp_protocol) {
    // We allow the tcptype to be missing, for backwards compatibility,
    // treating it as a passive candidate.
    // TODO(bugs.webrtc.org/11466): Treat a missing tcptype as an error?
    tcptype = cricket::TCPTYPE_PASSIVE_STR;
  }

  // Extension
  // Though non-standard, we support the ICE ufrag and pwd being signaled on
  // the candidate to avoid issues with confusing which generation a candidate
  // belongs to when trickling multiple generations at the same time.
  absl::string_view username;
  absl::string_view password;
  uint32_t generation = 0;
  uint16_t network_id = 0;
  uint16_t network_cost = 0;
  for (size_t i = current_position; i + 1 < fields.size(); ++i) {
    // RFC 5245
    // *(SP extension-att-name SP extension-att-value)
    if (fields[i] == kAttributeCandidateGeneration) {
      if (!GetValueFromString(first_line, fields[++i], &generation, error)) {
        return false;
      }
    } else if (fields[i] == kAttributeCandidateUfrag) {
      username = fields[++i];
    } else if (fields[i] == kAttributeCandidatePwd) {
      password = fields[++i];
    } else if (fields[i] == kAttributeCandidateNetworkId) {
      if (!GetValueFromString(first_line, fields[++i], &network_id, error)) {
        return false;
      }
    } else if (fields[i] == kAttributeCandidateNetworkCost) {
      if (!GetValueFromString(first_line, fields[++i], &network_cost, error)) {
        return false;
      }
      network_cost = std::min(network_cost, rtc::kNetworkCostMax);
    } else {
      // Skip the unknown extension.
      ++i;
    }
  }

  *candidate = Candidate(component_id, cricket::ProtoToString(*protocol),
                         address, priority, username, password, candidate_type,
                         generation, foundation, network_id, network_cost);
  candidate->set_related_address(related_address);
  candidate->set_tcptype(tcptype);
  return true;
}

bool ParseIceOptions(absl::string_view line,
                     std::vector<std::string>* transport_options,
                     SdpParseError* error) {
  std::string ice_options;
  if (!GetValue(line, kAttributeIceOption, &ice_options, error)) {
    return false;
  }
  std::vector<absl::string_view> fields =
      rtc::split(ice_options, kSdpDelimiterSpaceChar);
  for (size_t i = 0; i < fields.size(); ++i) {
    transport_options->emplace_back(fields[i]);
  }
  return true;
}

bool ParseSctpPort(absl::string_view line,
                   int* sctp_port,
                   SdpParseError* error) {
  // draft-ietf-mmusic-sctp-sdp-26
  // a=sctp-port
  const size_t expected_min_fields = 2;
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterColonChar);
  if (fields.size() < expected_min_fields) {
    fields = rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  }
  if (fields.size() < expected_min_fields) {
    return ParseFailedExpectMinFieldNum(line, expected_min_fields, error);
  }
  if (!rtc::FromString(fields[1], sctp_port)) {
    return ParseFailed(line, "Invalid sctp port value.", error);
  }
  return true;
}

bool ParseSctpMaxMessageSize(absl::string_view line,
                             int* max_message_size,
                             SdpParseError* error) {
  // draft-ietf-mmusic-sctp-sdp-26
  // a=max-message-size:199999
  const size_t expected_min_fields = 2;
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterColonChar);
  if (fields.size() < expected_min_fields) {
    return ParseFailedExpectMinFieldNum(line, expected_min_fields, error);
  }
  if (!rtc::FromString(fields[1], max_message_size)) {
    return ParseFailed(line, "Invalid SCTP max message size.", error);
  }
  return true;
}

bool ParseExtmap(absl::string_view line,
                 RtpExtension* extmap,
                 SdpParseError* error) {
  // RFC 5285
  // a=extmap:<value>["/"<direction>] <URI> <extensionattributes>
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  const size_t expected_min_fields = 2;
  if (fields.size() < expected_min_fields) {
    return ParseFailedExpectMinFieldNum(line, expected_min_fields, error);
  }
  absl::string_view uri = fields[1];

  std::string value_direction;
  if (!GetValue(fields[0], kAttributeExtmap, &value_direction, error)) {
    return false;
  }
  std::vector<absl::string_view> sub_fields =
      rtc::split(value_direction, kSdpDelimiterSlashChar);
  int value = 0;
  if (!GetValueFromString(line, sub_fields[0], &value, error)) {
    return false;
  }

  bool encrypted = false;
  if (uri == RtpExtension::kEncryptHeaderExtensionsUri) {
    // RFC 6904
    // a=extmap:<value["/"<direction>] urn:ietf:params:rtp-hdrext:encrypt <URI>
    //     <extensionattributes>
    const size_t expected_min_fields_encrypted = expected_min_fields + 1;
    if (fields.size() < expected_min_fields_encrypted) {
      return ParseFailedExpectMinFieldNum(line, expected_min_fields_encrypted,
                                          error);
    }

    encrypted = true;
    uri = fields[2];
    if (uri == RtpExtension::kEncryptHeaderExtensionsUri) {
      return ParseFailed(line, "Recursive encrypted header.", error);
    }
  }

  *extmap = RtpExtension(uri, value, encrypted);
  return true;
}

static void BuildSctpContentAttributes(
    std::string* message,
    const cricket::SctpDataContentDescription* data_desc) {
  rtc::StringBuilder os;
  if (data_desc->use_sctpmap()) {
    // draft-ietf-mmusic-sctp-sdp-04
    // a=sctpmap:sctpmap-number  protocol  [streams]
    rtc::StringBuilder os;
    InitAttrLine(kAttributeSctpmap, &os);
    os << kSdpDelimiterColon << data_desc->port() << kSdpDelimiterSpace
       << kDefaultSctpmapProtocol << kSdpDelimiterSpace
       << cricket::kMaxSctpStreams;
    AddLine(os.str(), message);
  } else {
    // draft-ietf-mmusic-sctp-sdp-23
    // a=sctp-port:<port>
    InitAttrLine(kAttributeSctpPort, &os);
    os << kSdpDelimiterColon << data_desc->port();
    AddLine(os.str(), message);
    if (data_desc->max_message_size() != kDefaultSctpMaxMessageSize) {
      InitAttrLine(kAttributeMaxMessageSize, &os);
      os << kSdpDelimiterColon << data_desc->max_message_size();
      AddLine(os.str(), message);
    }
  }
}

void BuildIceUfragPwd(const TransportInfo* transport_info,
                      std::string* message) {
  RTC_DCHECK(transport_info);

  rtc::StringBuilder os;
  // RFC 5245
  // ice-pwd-att           = "ice-pwd" ":" password
  // ice-ufrag-att         = "ice-ufrag" ":" ufrag
  // ice-ufrag
  if (!transport_info->description.ice_ufrag.empty()) {
    InitAttrLine(kAttributeIceUfrag, &os);
    os << kSdpDelimiterColon << transport_info->description.ice_ufrag;
    AddLine(os.str(), message);
  }
  // ice-pwd
  if (!transport_info->description.ice_pwd.empty()) {
    InitAttrLine(kAttributeIcePwd, &os);
    os << kSdpDelimiterColon << transport_info->description.ice_pwd;
    AddLine(os.str(), message);
  }
}

void BuildDtlsFingerprintSetup(const TransportInfo* transport_info,
                               std::string* message) {
  RTC_DCHECK(transport_info);

  rtc::StringBuilder os;
  // RFC 4572
  // fingerprint-attribute  =
  //   "fingerprint" ":" hash-func SP fingerprint
  // When using max-bundle this is already included at session level.
  // Insert the fingerprint attribute.
  auto fingerprint = transport_info->description.identity_fingerprint.get();
  if (!fingerprint) {
    return;
  }
  InitAttrLine(kAttributeFingerprint, &os);
  os << kSdpDelimiterColon << fingerprint->algorithm << kSdpDelimiterSpace
     << fingerprint->GetRfc4572Fingerprint();
  AddLine(os.str(), message);

  // Inserting setup attribute.
  if (transport_info->description.connection_role !=
      cricket::CONNECTIONROLE_NONE) {
    // Making sure we are not using "passive" mode.
    cricket::ConnectionRole role = transport_info->description.connection_role;
    std::string dtls_role_str;
    const bool success = cricket::ConnectionRoleToString(role, &dtls_role_str);
    RTC_DCHECK(success);
    InitAttrLine(kAttributeSetup, &os);
    os << kSdpDelimiterColon << dtls_role_str;
    AddLine(os.str(), message);
  }
}

void BuildMediaLine(const cricket::MediaType media_type,
                    const ContentInfo* content_info,
                    const MediaContentDescription* media_desc,
                    std::string* message) {
  rtc::StringBuilder os;

  // RFC 4566
  // m=<media> <port> <proto> <fmt>
  // fmt is a list of payload type numbers that MAY be used in the session.
  std::string type;
  std::string fmt;
  if (media_type == cricket::MEDIA_TYPE_AUDIO ||
      media_type == cricket::MEDIA_TYPE_VIDEO) {
    type = media_type == cricket::MEDIA_TYPE_AUDIO ? kMediaTypeAudio
                                                   : kMediaTypeVideo;
    for (const cricket::Codec& codec : media_desc->codecs()) {
      fmt.append(" ");
      fmt.append(rtc::ToString(codec.id));
    }
  } else if (media_type == cricket::MEDIA_TYPE_DATA) {
    type = kMediaTypeData;
    const cricket::SctpDataContentDescription* sctp_data_desc =
        media_desc->as_sctp();
    if (sctp_data_desc) {
      fmt.append(" ");

      if (sctp_data_desc->use_sctpmap()) {
        fmt.append(rtc::ToString(sctp_data_desc->port()));
      } else {
        fmt.append(kDefaultSctpmapProtocol);
      }
    } else {
      RTC_DCHECK_NOTREACHED() << "Data description without SCTP";
    }
  } else if (media_type == cricket::MEDIA_TYPE_UNSUPPORTED) {
    const UnsupportedContentDescription* unsupported_desc =
        media_desc->as_unsupported();
    type = unsupported_desc->media_type();
  } else {
    RTC_DCHECK_NOTREACHED();
  }
  // The fmt must never be empty. If no codecs are found, set the fmt attribute
  // to 0.
  if (fmt.empty()) {
    fmt = " 0";
  }

  // The port number in the m line will be updated later when associated with
  // the candidates.
  //
  // A port value of 0 indicates that the m= section is rejected.
  // RFC 3264
  // To reject an offered stream, the port number in the corresponding stream in
  // the answer MUST be set to zero.
  //
  // However, the BUNDLE draft adds a new meaning to port zero, when used along
  // with a=bundle-only.
  std::string port = kDummyPort;
  if (content_info->rejected || content_info->bundle_only) {
    port = kMediaPortRejected;
  } else if (!media_desc->connection_address().IsNil()) {
    port = rtc::ToString(media_desc->connection_address().port());
  }

  // Add the m and c lines.
  InitLine(kLineTypeMedia, type, &os);
  os << " " << port << " " << media_desc->protocol() << fmt;
  AddLine(os.str(), message);
}

void BuildMediaDescription(const ContentInfo* content_info,
                           const TransportInfo* transport_info,
                           const cricket::MediaType media_type,
                           const std::vector<Candidate>& candidates,
                           int msid_signaling,
                           std::string* message) {
  RTC_DCHECK(message);
  if (!content_info) {
    return;
  }
  rtc::StringBuilder os;
  const MediaContentDescription* media_desc = content_info->media_description();
  RTC_DCHECK(media_desc);

  // Add the m line.
  BuildMediaLine(media_type, content_info, media_desc, message);
  // Add the c line.
  InitLine(kLineTypeConnection, kConnectionNettype, &os);
  if (media_desc->connection_address().IsNil()) {
    os << " " << kConnectionIpv4Addrtype << " " << kDummyAddress;
  } else if (media_desc->connection_address().family() == AF_INET) {
    os << " " << kConnectionIpv4Addrtype << " "
       << media_desc->connection_address().ipaddr().ToString();
  } else if (media_desc->connection_address().family() == AF_INET6) {
    os << " " << kConnectionIpv6Addrtype << " "
       << media_desc->connection_address().ipaddr().ToString();
  } else {
    os << " " << kConnectionIpv4Addrtype << " " << kDummyAddress;
  }
  AddLine(os.str(), message);

  // RFC 4566
  // b=AS:<bandwidth> or
  // b=TIAS:<bandwidth>
  int bandwidth = media_desc->bandwidth();
  std::string bandwidth_type = media_desc->bandwidth_type();
  if (bandwidth_type == kApplicationSpecificBandwidth && bandwidth >= 1000) {
    InitLine(kLineTypeSessionBandwidth, bandwidth_type, &os);
    bandwidth /= 1000;
    os << kSdpDelimiterColon << bandwidth;
    AddLine(os.str(), message);
  } else if (bandwidth_type == kTransportSpecificBandwidth && bandwidth > 0) {
    InitLine(kLineTypeSessionBandwidth, bandwidth_type, &os);
    os << kSdpDelimiterColon << bandwidth;
    AddLine(os.str(), message);
  }

  // Add the a=bundle-only line.
  if (content_info->bundle_only) {
    InitAttrLine(kAttributeBundleOnly, &os);
    AddLine(os.str(), message);
  }

  // Add the a=rtcp line.
  if (cricket::IsRtpProtocol(media_desc->protocol())) {
    std::string rtcp_line = GetRtcpLine(candidates);
    if (!rtcp_line.empty()) {
      AddLine(rtcp_line, message);
    }
  }

  // Build the a=candidate lines. We don't include ufrag and pwd in the
  // candidates in the SDP to avoid redundancy.
  BuildCandidate(candidates, false, message);

  // Use the transport_info to build the media level ice-ufrag, ice-pwd
  // and DTLS fingerprint and setup attributes.
  if (transport_info) {
    BuildIceUfragPwd(transport_info, message);

    // draft-petithuguenin-mmusic-ice-attributes-level-03
    BuildIceOptions(transport_info->description.transport_options, message);

    // Also include the DTLS fingerprint and setup attribute if available.
    BuildDtlsFingerprintSetup(transport_info, message);
  }

  // RFC 3388
  // mid-attribute      = "a=mid:" identification-tag
  // identification-tag = token
  // Use the content name as the mid identification-tag.
  InitAttrLine(kAttributeMid, &os);
  os << kSdpDelimiterColon << content_info->name;
  AddLine(os.str(), message);

  if (cricket::IsDtlsSctp(media_desc->protocol())) {
    const cricket::SctpDataContentDescription* data_desc =
        media_desc->as_sctp();
    BuildSctpContentAttributes(message, data_desc);
  } else if (cricket::IsRtpProtocol(media_desc->protocol())) {
    BuildRtpContentAttributes(media_desc, media_type, msid_signaling, message);
  }
}

void BuildRtpContentAttributes(const MediaContentDescription* media_desc,
                               const cricket::MediaType media_type,
                               int msid_signaling,
                               std::string* message) {
  SimulcastSdpSerializer serializer;
  rtc::StringBuilder os;
  // RFC 8285
  // a=extmap-allow-mixed
  // The attribute MUST be either on session level or media level. We support
  // responding on both levels, however, we don't respond on media level if it's
  // set on session level.
  if (media_desc->extmap_allow_mixed_enum() ==
      MediaContentDescription::kMedia) {
    InitAttrLine(kAttributeExtmapAllowMixed, &os);
    AddLine(os.str(), message);
  }
  BuildRtpHeaderExtensions(media_desc->rtp_header_extensions(), message);

  // RFC 3264
  // a=sendrecv || a=sendonly || a=sendrecv || a=inactive
  switch (media_desc->direction()) {
    // Special case that for sdp purposes should be treated same as inactive.
    case RtpTransceiverDirection::kStopped:
    case RtpTransceiverDirection::kInactive:
      InitAttrLine(kAttributeInactive, &os);
      break;
    case RtpTransceiverDirection::kSendOnly:
      InitAttrLine(kAttributeSendOnly, &os);
      break;
    case RtpTransceiverDirection::kRecvOnly:
      InitAttrLine(kAttributeRecvOnly, &os);
      break;
    case RtpTransceiverDirection::kSendRecv:
      InitAttrLine(kAttributeSendRecv, &os);
      break;
    default:
      RTC_DCHECK_NOTREACHED();
      InitAttrLine(kAttributeSendRecv, &os);
      break;
  }
  AddLine(os.str(), message);

  // Specified in https://datatracker.ietf.org/doc/draft-ietf-mmusic-msid/16/
  // a=msid:<msid-id> <msid-appdata>
  // The msid-id is a 1*64 token char representing the media stream id, and the
  // msid-appdata is a 1*64 token char representing the track id. There is a
  // line for every media stream, with a special msid-id value of "-"
  // representing no streams. The value of "msid-appdata" MUST be identical for
  // all lines.
  if (msid_signaling & cricket::kMsidSignalingMediaSection) {
    const StreamParamsVec& streams = media_desc->streams();
    if (streams.size() == 1u) {
      const StreamParams& track = streams[0];
      std::vector<std::string> stream_ids = track.stream_ids();
      if (stream_ids.empty()) {
        stream_ids.push_back(kNoStreamMsid);
      }
      for (const std::string& stream_id : stream_ids) {
        InitAttrLine(kAttributeMsid, &os);
        os << kSdpDelimiterColon << stream_id << kSdpDelimiterSpace << track.id;
        AddLine(os.str(), message);
      }
    } else if (streams.size() > 1u) {
      RTC_LOG(LS_WARNING)
          << "Trying to serialize Unified Plan SDP with more than "
             "one track in a media section. Omitting 'a=msid'.";
    }
  }

  // RFC 5761
  // a=rtcp-mux
  if (media_desc->rtcp_mux()) {
    InitAttrLine(kAttributeRtcpMux, &os);
    AddLine(os.str(), message);
  }

  // RFC 5506
  // a=rtcp-rsize
  if (media_desc->rtcp_reduced_size()) {
    InitAttrLine(kAttributeRtcpReducedSize, &os);
    AddLine(os.str(), message);
  }

  if (media_desc->conference_mode()) {
    InitAttrLine(kAttributeXGoogleFlag, &os);
    os << kSdpDelimiterColon << kValueConference;
    AddLine(os.str(), message);
  }

  if (media_desc->remote_estimate()) {
    InitAttrLine(kAttributeRtcpRemoteEstimate, &os);
    AddLine(os.str(), message);
  }

  // RFC 4566
  // a=rtpmap:<payload type> <encoding name>/<clock rate>
  // [/<encodingparameters>]
  BuildRtpmap(media_desc, media_type, message);

  for (const StreamParams& track : media_desc->streams()) {
    // Build the ssrc-group lines.
    for (const SsrcGroup& ssrc_group : track.ssrc_groups) {
      // RFC 5576
      // a=ssrc-group:<semantics> <ssrc-id> ...
      if (ssrc_group.ssrcs.empty()) {
        continue;
      }
      InitAttrLine(kAttributeSsrcGroup, &os);
      os << kSdpDelimiterColon << ssrc_group.semantics;
      for (uint32_t ssrc : ssrc_group.ssrcs) {
        os << kSdpDelimiterSpace << rtc::ToString(ssrc);
      }
      AddLine(os.str(), message);
    }
    // Build the ssrc lines for each ssrc.
    for (uint32_t ssrc : track.ssrcs) {
      // RFC 5576
      // a=ssrc:<ssrc-id> cname:<value>
      AddSsrcLine(ssrc, kSsrcAttributeCname, track.cname, message);

      if (msid_signaling & cricket::kMsidSignalingSsrcAttribute) {
        // draft-alvestrand-mmusic-msid-00
        // a=ssrc:<ssrc-id> msid:identifier [appdata]
        // The appdata consists of the "id" attribute of a MediaStreamTrack,
        // which corresponds to the "id" attribute of StreamParams.
        // Since a=ssrc msid signaling is used in Plan B SDP semantics, and
        // multiple stream ids are not supported for Plan B, we are only adding
        // a line for the first media stream id here.
        const std::string& track_stream_id = track.first_stream_id();
        // We use a special msid-id value of "-" to represent no streams,
        // for Unified Plan compatibility. Plan B will always have a
        // track_stream_id.
        const std::string& stream_id =
            track_stream_id.empty() ? kNoStreamMsid : track_stream_id;
        InitAttrLine(kAttributeSsrc, &os);
        os << kSdpDelimiterColon << ssrc << kSdpDelimiterSpace
           << kSsrcAttributeMsid << kSdpDelimiterColon << stream_id
           << kSdpDelimiterSpace << track.id;
        AddLine(os.str(), message);
      }
    }

    // Build the rid lines for each layer of the track
    for (const RidDescription& rid_description : track.rids()) {
      InitAttrLine(kAttributeRid, &os);
      os << kSdpDelimiterColon
         << serializer.SerializeRidDescription(rid_description);
      AddLine(os.str(), message);
    }
  }

  for (const RidDescription& rid_description : media_desc->receive_rids()) {
    InitAttrLine(kAttributeRid, &os);
    os << kSdpDelimiterColon
       << serializer.SerializeRidDescription(rid_description);
    AddLine(os.str(), message);
  }

  // Simulcast (a=simulcast)
  // https://tools.ietf.org/html/draft-ietf-mmusic-sdp-simulcast-13#section-5.1
  if (media_desc->HasSimulcast()) {
    const auto& simulcast = media_desc->simulcast_description();
    InitAttrLine(kAttributeSimulcast, &os);
    os << kSdpDelimiterColon
       << serializer.SerializeSimulcastDescription(simulcast);
    AddLine(os.str(), message);
  }
}

void BuildRtpHeaderExtensions(const RtpHeaderExtensions& extensions,
                              std::string* message) {
  rtc::StringBuilder os;

  // RFC 8285
  // a=extmap:<value>["/"<direction>] <URI> <extensionattributes>
  // The definitions MUST be either all session level or all media level. This
  // implementation uses all media level.
  for (const RtpExtension& extension : extensions) {
    InitAttrLine(kAttributeExtmap, &os);
    os << kSdpDelimiterColon << extension.id;
    if (extension.encrypt) {
      os << kSdpDelimiterSpace << RtpExtension::kEncryptHeaderExtensionsUri;
    }
    os << kSdpDelimiterSpace << extension.uri;
    AddLine(os.str(), message);
  }
}

void WriteFmtpHeader(int payload_type, rtc::StringBuilder* os) {
  // fmtp header: a=fmtp:`payload_type` <parameters>
  // Add a=fmtp
  InitAttrLine(kAttributeFmtp, os);
  // Add :`payload_type`
  *os << kSdpDelimiterColon << payload_type;
}

void WritePacketizationHeader(int payload_type, rtc::StringBuilder* os) {
  // packetization header: a=packetization:`payload_type` <packetization_format>
  // Add a=packetization
  InitAttrLine(kAttributePacketization, os);
  // Add :`payload_type`
  *os << kSdpDelimiterColon << payload_type;
}

void WriteRtcpFbHeader(int payload_type, rtc::StringBuilder* os) {
  // rtcp-fb header: a=rtcp-fb:`payload_type`
  // <parameters>/<ccm <ccm_parameters>>
  // Add a=rtcp-fb
  InitAttrLine(kAttributeRtcpFb, os);
  // Add :
  *os << kSdpDelimiterColon;
  if (payload_type == kWildcardPayloadType) {
    *os << "*";
  } else {
    *os << payload_type;
  }
}

void WriteFmtpParameter(absl::string_view parameter_name,
                        absl::string_view parameter_value,
                        rtc::StringBuilder* os) {
  if (parameter_name.empty()) {
    // RFC 2198 and RFC 4733 don't use key-value pairs.
    *os << parameter_value;
  } else {
    // fmtp parameters: `parameter_name`=`parameter_value`
    *os << parameter_name << kSdpDelimiterEqual << parameter_value;
  }
}

bool IsFmtpParam(absl::string_view name) {
  // RFC 4855, section 3 specifies the mapping of media format parameters to SDP
  // parameters. Only ptime, maxptime, channels and rate are placed outside of
  // the fmtp line. In WebRTC, channels and rate are already handled separately
  // and thus not included in the CodecParameterMap.
  return name != kCodecParamPTime && name != kCodecParamMaxPTime;
}

bool WriteFmtpParameters(const webrtc::CodecParameterMap& parameters,
                         rtc::StringBuilder* os) {
  bool empty = true;
  const char* delimiter = "";  // No delimiter before first parameter.
  for (const auto& entry : parameters) {
    const std::string& key = entry.first;
    const std::string& value = entry.second;

    if (IsFmtpParam(key)) {
      *os << delimiter;
      // A semicolon before each subsequent parameter.
      delimiter = kSdpDelimiterSemicolon;
      WriteFmtpParameter(key, value, os);
      empty = false;
    }
  }

  return !empty;
}

void AddFmtpLine(const cricket::Codec& codec, std::string* message) {
  rtc::StringBuilder os;
  WriteFmtpHeader(codec.id, &os);
  os << kSdpDelimiterSpace;
  // Create FMTP line and check that it's nonempty.
  if (WriteFmtpParameters(codec.params, &os)) {
    AddLine(os.str(), message);
  }
  return;
}

void AddPacketizationLine(const cricket::Codec& codec, std::string* message) {
  if (!codec.packetization) {
    return;
  }
  rtc::StringBuilder os;
  WritePacketizationHeader(codec.id, &os);
  os << " " << *codec.packetization;
  AddLine(os.str(), message);
}

void AddRtcpFbLines(const cricket::Codec& codec, std::string* message) {
  for (const cricket::FeedbackParam& param : codec.feedback_params.params()) {
    rtc::StringBuilder os;
    WriteRtcpFbHeader(codec.id, &os);
    os << " " << param.id();
    if (!param.param().empty()) {
      os << " " << param.param();
    }
    AddLine(os.str(), message);
  }
}

bool GetMinValue(const std::vector<int>& values, int* value) {
  if (values.empty()) {
    return false;
  }
  auto it = absl::c_min_element(values);
  *value = *it;
  return true;
}

bool GetParameter(const std::string& name,
                  const webrtc::CodecParameterMap& params,
                  int* value) {
  std::map<std::string, std::string>::const_iterator found = params.find(name);
  if (found == params.end()) {
    return false;
  }
  if (!rtc::FromString(found->second, value)) {
    return false;
  }
  return true;
}

void BuildRtpmap(const MediaContentDescription* media_desc,
                 const cricket::MediaType media_type,
                 std::string* message) {
  RTC_DCHECK(message != NULL);
  RTC_DCHECK(media_desc != NULL);
  rtc::StringBuilder os;
  if (media_type == cricket::MEDIA_TYPE_VIDEO) {
    for (const cricket::Codec& codec : media_desc->codecs()) {
      // RFC 4566
      // a=rtpmap:<payload type> <encoding name>/<clock rate>
      // [/<encodingparameters>]
      if (codec.id != kWildcardPayloadType) {
        InitAttrLine(kAttributeRtpmap, &os);
        os << kSdpDelimiterColon << codec.id << " " << codec.name << "/"
           << cricket::kVideoCodecClockrate;
        AddLine(os.str(), message);
      }
      AddPacketizationLine(codec, message);
      AddRtcpFbLines(codec, message);
      AddFmtpLine(codec, message);
    }
  } else if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    std::vector<int> ptimes;
    std::vector<int> maxptimes;
    int max_minptime = 0;
    for (const cricket::Codec& codec : media_desc->codecs()) {
      RTC_DCHECK(!codec.name.empty());
      // RFC 4566
      // a=rtpmap:<payload type> <encoding name>/<clock rate>
      // [/<encodingparameters>]
      InitAttrLine(kAttributeRtpmap, &os);
      os << kSdpDelimiterColon << codec.id << " ";
      os << codec.name << "/" << codec.clockrate;
      if (codec.channels != 1) {
        os << "/" << codec.channels;
      }
      AddLine(os.str(), message);
      AddRtcpFbLines(codec, message);
      AddFmtpLine(codec, message);
      int minptime = 0;
      if (GetParameter(kCodecParamMinPTime, codec.params, &minptime)) {
        max_minptime = std::max(minptime, max_minptime);
      }
      int ptime;
      if (GetParameter(kCodecParamPTime, codec.params, &ptime)) {
        ptimes.push_back(ptime);
      }
      int maxptime;
      if (GetParameter(kCodecParamMaxPTime, codec.params, &maxptime)) {
        maxptimes.push_back(maxptime);
      }
    }
    // Populate the maxptime attribute with the smallest maxptime of all codecs
    // under the same m-line.
    int min_maxptime = INT_MAX;
    if (GetMinValue(maxptimes, &min_maxptime)) {
      AddAttributeLine(kCodecParamMaxPTime, min_maxptime, message);
    }
    RTC_DCHECK_GE(min_maxptime, max_minptime);
    // Populate the ptime attribute with the smallest ptime or the largest
    // minptime, whichever is the largest, for all codecs under the same m-line.
    int ptime = INT_MAX;
    if (GetMinValue(ptimes, &ptime)) {
      ptime = std::min(ptime, min_maxptime);
      ptime = std::max(ptime, max_minptime);
      AddAttributeLine(kCodecParamPTime, ptime, message);
    }
  }
}

void BuildCandidate(const std::vector<Candidate>& candidates,
                    bool include_ufrag,
                    std::string* message) {
  rtc::StringBuilder os;

  for (const Candidate& candidate : candidates) {
    // RFC 5245
    // a=candidate:<foundation> <component-id> <transport> <priority>
    // <connection-address> <port> typ <candidate-types>
    // [raddr <connection-address>] [rport <port>]
    // *(SP extension-att-name SP extension-att-value)
    std::string type;
    // Map the cricket candidate type to "host" / "srflx" / "prflx" / "relay"
    if (candidate.is_local()) {
      type = kCandidateHost;
    } else if (candidate.is_stun()) {
      type = kCandidateSrflx;
    } else if (candidate.is_relay()) {
      type = kCandidateRelay;
    } else if (candidate.is_prflx()) {
      type = kCandidatePrflx;
      // Peer reflexive candidate may be signaled for being removed.
    } else {
      RTC_DCHECK_NOTREACHED();
      // Never write out candidates if we don't know the type.
      continue;
    }

    InitAttrLine(kAttributeCandidate, &os);
    os << kSdpDelimiterColon << candidate.foundation() << " "
       << candidate.component() << " " << candidate.protocol() << " "
       << candidate.priority() << " "
       << (candidate.address().ipaddr().IsNil()
               ? candidate.address().hostname()
               : candidate.address().ipaddr().ToString())
       << " " << candidate.address().PortAsString() << " "
       << kAttributeCandidateTyp << " " << type << " ";

    // Related address
    if (!candidate.related_address().IsNil()) {
      os << kAttributeCandidateRaddr << " "
         << candidate.related_address().ipaddr().ToString() << " "
         << kAttributeCandidateRport << " "
         << candidate.related_address().PortAsString() << " ";
    }

    // Note that we allow the tcptype to be missing, for backwards
    // compatibility; the implementation treats this as a passive candidate.
    // TODO(bugs.webrtc.org/11466): Treat a missing tcptype as an error?
    if (candidate.protocol() == cricket::TCP_PROTOCOL_NAME &&
        !candidate.tcptype().empty()) {
      os << kTcpCandidateType << " " << candidate.tcptype() << " ";
    }

    // Extensions
    os << kAttributeCandidateGeneration << " " << candidate.generation();
    if (include_ufrag && !candidate.username().empty()) {
      os << " " << kAttributeCandidateUfrag << " " << candidate.username();
    }
    if (candidate.network_id() > 0) {
      os << " " << kAttributeCandidateNetworkId << " "
         << candidate.network_id();
    }
    if (candidate.network_cost() > 0) {
      os << " " << kAttributeCandidateNetworkCost << " "
         << candidate.network_cost();
    }

    AddLine(os.str(), message);
  }
}

void BuildIceOptions(const std::vector<std::string>& transport_options,
                     std::string* message) {
  if (!transport_options.empty()) {
    rtc::StringBuilder os;
    InitAttrLine(kAttributeIceOption, &os);
    os << kSdpDelimiterColon << transport_options[0];
    for (size_t i = 1; i < transport_options.size(); ++i) {
      os << kSdpDelimiterSpace << transport_options[i];
    }
    AddLine(os.str(), message);
  }
}

bool ParseConnectionData(absl::string_view line,
                         rtc::SocketAddress* addr,
                         SdpParseError* error) {
  // Parse the line from left to right.
  std::string token;
  std::string rightpart;
  // RFC 4566
  // c=<nettype> <addrtype> <connection-address>
  // Skip the "c="
  if (!rtc::tokenize_first(line, kSdpDelimiterEqualChar, &token, &rightpart)) {
    return ParseFailed(line, "Failed to parse the network type.", error);
  }

  // Extract and verify the <nettype>
  if (!rtc::tokenize_first(rightpart, kSdpDelimiterSpaceChar, &token,
                           &rightpart) ||
      token != kConnectionNettype) {
    return ParseFailed(line,
                       "Failed to parse the connection data. The network type "
                       "is not currently supported.",
                       error);
  }

  // Extract the "<addrtype>" and "<connection-address>".
  if (!rtc::tokenize_first(rightpart, kSdpDelimiterSpaceChar, &token,
                           &rightpart)) {
    return ParseFailed(line, "Failed to parse the address type.", error);
  }

  // The rightpart part should be the IP address without the slash which is used
  // for multicast.
  if (rightpart.find('/') != std::string::npos) {
    return ParseFailed(line,
                       "Failed to parse the connection data. Multicast is not "
                       "currently supported.",
                       error);
  }
  addr->SetIP(rightpart);

  // Verify that the addrtype matches the type of the parsed address.
  if ((addr->family() == AF_INET && token != "IP4") ||
      (addr->family() == AF_INET6 && token != "IP6")) {
    addr->Clear();
    return ParseFailed(
        line,
        "Failed to parse the connection data. The address type is mismatching.",
        error);
  }
  return true;
}

bool ParseSessionDescription(absl::string_view message,
                             size_t* pos,
                             std::string* session_id,
                             std::string* session_version,
                             TransportDescription* session_td,
                             RtpHeaderExtensions* session_extmaps,
                             rtc::SocketAddress* connection_addr,
                             cricket::SessionDescription* desc,
                             SdpParseError* error) {
  absl::optional<absl::string_view> line;

  desc->set_msid_signaling(cricket::kMsidSignalingNotUsed);
  desc->set_extmap_allow_mixed(false);
  // RFC 4566
  // v=  (protocol version)
  line = GetLineWithType(message, pos, kLineTypeVersion);
  if (!line) {
    return ParseFailedExpectLine(message, *pos, kLineTypeVersion, std::string(),
                                 error);
  }
  // RFC 4566
  // o=<username> <sess-id> <sess-version> <nettype> <addrtype>
  // <unicast-address>
  line = GetLineWithType(message, pos, kLineTypeOrigin);
  if (!line) {
    return ParseFailedExpectLine(message, *pos, kLineTypeOrigin, std::string(),
                                 error);
  }
  std::vector<absl::string_view> fields =
      rtc::split(line->substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  const size_t expected_fields = 6;
  if (fields.size() != expected_fields) {
    return ParseFailedExpectFieldNum(*line, expected_fields, error);
  }
  *session_id = std::string(fields[1]);
  *session_version = std::string(fields[2]);

  // RFC 4566
  // s=  (session name)
  line = GetLineWithType(message, pos, kLineTypeSessionName);
  if (!line) {
    return ParseFailedExpectLine(message, *pos, kLineTypeSessionName,
                                 std::string(), error);
  }

  // optional lines
  // Those are the optional lines, so shouldn't return false if not present.
  // RFC 4566
  // i=* (session information)
  GetLineWithType(message, pos, kLineTypeSessionInfo);

  // RFC 4566
  // u=* (URI of description)
  GetLineWithType(message, pos, kLineTypeSessionUri);

  // RFC 4566
  // e=* (email address)
  GetLineWithType(message, pos, kLineTypeSessionEmail);

  // RFC 4566
  // p=* (phone number)
  GetLineWithType(message, pos, kLineTypeSessionPhone);

  // RFC 4566
  // c=* (connection information -- not required if included in
  //      all media)
  if (absl::optional<absl::string_view> cline =
          GetLineWithType(message, pos, kLineTypeConnection);
      cline.has_value()) {
    if (!ParseConnectionData(*cline, connection_addr, error)) {
      return false;
    }
  }

  // RFC 4566
  // b=* (zero or more bandwidth information lines)
  while (GetLineWithType(message, pos, kLineTypeSessionBandwidth).has_value()) {
    // By pass zero or more b lines.
  }

  // RFC 4566
  // One or more time descriptions ("t=" and "r=" lines; see below)
  // t=  (time the session is active)
  // r=* (zero or more repeat times)
  // Ensure there's at least one time description
  if (!GetLineWithType(message, pos, kLineTypeTiming).has_value()) {
    return ParseFailedExpectLine(message, *pos, kLineTypeTiming, std::string(),
                                 error);
  }

  while (GetLineWithType(message, pos, kLineTypeRepeatTimes).has_value()) {
    // By pass zero or more r lines.
  }

  // Go through the rest of the time descriptions
  while (GetLineWithType(message, pos, kLineTypeTiming).has_value()) {
    while (GetLineWithType(message, pos, kLineTypeRepeatTimes).has_value()) {
      // By pass zero or more r lines.
    }
  }

  // RFC 4566
  // z=* (time zone adjustments)
  GetLineWithType(message, pos, kLineTypeTimeZone);

  // RFC 4566
  // k=* (encryption key)
  GetLineWithType(message, pos, kLineTypeEncryptionKey);

  // RFC 4566
  // a=* (zero or more session attribute lines)
  while (absl::optional<absl::string_view> aline =
             GetLineWithType(message, pos, kLineTypeAttributes)) {
    if (HasAttribute(*aline, kAttributeGroup)) {
      if (!ParseGroupAttribute(*aline, desc, error)) {
        return false;
      }
    } else if (HasAttribute(*aline, kAttributeIceUfrag)) {
      if (!GetValue(*aline, kAttributeIceUfrag, &(session_td->ice_ufrag),
                    error)) {
        return false;
      }
    } else if (HasAttribute(*aline, kAttributeIcePwd)) {
      if (!GetValue(*aline, kAttributeIcePwd, &(session_td->ice_pwd), error)) {
        return false;
      }
    } else if (HasAttribute(*aline, kAttributeIceLite)) {
      session_td->ice_mode = cricket::ICEMODE_LITE;
    } else if (HasAttribute(*aline, kAttributeIceOption)) {
      if (!ParseIceOptions(*aline, &(session_td->transport_options), error)) {
        return false;
      }
    } else if (HasAttribute(*aline, kAttributeFingerprint)) {
      if (session_td->identity_fingerprint.get()) {
        return ParseFailed(
            *aline,
            "Can't have multiple fingerprint attributes at the same level.",
            error);
      }
      std::unique_ptr<rtc::SSLFingerprint> fingerprint;
      if (!ParseFingerprintAttribute(*aline, &fingerprint, error)) {
        return false;
      }
      session_td->identity_fingerprint = std::move(fingerprint);
    } else if (HasAttribute(*aline, kAttributeSetup)) {
      if (!ParseDtlsSetup(*aline, &(session_td->connection_role), error)) {
        return false;
      }
    } else if (HasAttribute(*aline, kAttributeMsidSemantics)) {
      std::string semantics;
      if (!GetValue(*aline, kAttributeMsidSemantics, &semantics, error)) {
        return false;
      }
      if (CaseInsensitiveFind(semantics, kMediaStreamSemantic)) {
        desc->set_msid_signaling(cricket::kMsidSignalingSemantic);
      }
    } else if (HasAttribute(*aline, kAttributeExtmapAllowMixed)) {
      desc->set_extmap_allow_mixed(true);
    } else if (HasAttribute(*aline, kAttributeExtmap)) {
      RtpExtension extmap;
      if (!ParseExtmap(*aline, &extmap, error)) {
        return false;
      }
      session_extmaps->push_back(extmap);
    }
  }
  return true;
}

bool ParseGroupAttribute(absl::string_view line,
                         cricket::SessionDescription* desc,
                         SdpParseError* error) {
  RTC_DCHECK(desc != NULL);

  // RFC 5888 and draft-holmberg-mmusic-sdp-bundle-negotiation-00
  // a=group:BUNDLE video voice
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  std::string semantics;
  if (!GetValue(fields[0], kAttributeGroup, &semantics, error)) {
    return false;
  }
  cricket::ContentGroup group(semantics);
  for (size_t i = 1; i < fields.size(); ++i) {
    group.AddContentName(fields[i]);
  }
  desc->AddGroup(group);
  return true;
}

static bool ParseFingerprintAttribute(
    absl::string_view line,
    std::unique_ptr<rtc::SSLFingerprint>* fingerprint,
    SdpParseError* error) {
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  const size_t expected_fields = 2;
  if (fields.size() != expected_fields) {
    return ParseFailedExpectFieldNum(line, expected_fields, error);
  }

  // The first field here is "fingerprint:<hash>.
  std::string algorithm;
  if (!GetValue(fields[0], kAttributeFingerprint, &algorithm, error)) {
    return false;
  }

  // Downcase the algorithm. Note that we don't need to downcase the
  // fingerprint because hex_decode can handle upper-case.
  absl::c_transform(algorithm, algorithm.begin(), ::tolower);

  // The second field is the digest value. De-hexify it.
  *fingerprint =
      rtc::SSLFingerprint::CreateUniqueFromRfc4572(algorithm, fields[1]);
  if (!*fingerprint) {
    return ParseFailed(line, "Failed to create fingerprint from the digest.",
                       error);
  }

  return true;
}

static bool ParseDtlsSetup(absl::string_view line,
                           cricket::ConnectionRole* role_ptr,
                           SdpParseError* error) {
  // setup-attr           =  "a=setup:" role
  // role                 =  "active" / "passive" / "actpass" / "holdconn"
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterColonChar);
  const size_t expected_fields = 2;
  if (fields.size() != expected_fields) {
    return ParseFailedExpectFieldNum(line, expected_fields, error);
  }
  if (absl::optional<cricket::ConnectionRole> role =
          cricket::StringToConnectionRole(fields[1]);
      role.has_value()) {
    *role_ptr = *role;
    return true;
  }
  return ParseFailed(line, "Invalid attribute value.", error);
}

static bool ParseMsidAttribute(absl::string_view line,
                               std::vector<std::string>* stream_ids,
                               std::string* track_id,
                               SdpParseError* error) {
  // https://datatracker.ietf.org/doc/rfc8830/
  // a=msid:<msid-value>
  // msid-value = msid-id [ SP msid-appdata ]
  // msid-id = 1*64token-char ; see RFC 4566
  // msid-appdata = 1*64token-char  ; see RFC 4566
  // Note that JSEP stipulates not sending msid-appdata so
  // a=msid:<stream id> <track id>
  // is supported for backward compability reasons only.
  // RFC 8830 section 2 states that duplicate a=msid:stream track is illegal.
  std::vector<std::string> fields;
  size_t num_fields = rtc::tokenize(line.substr(kLinePrefixLength),
                                    kSdpDelimiterSpaceChar, &fields);
  if (num_fields < 1 || num_fields > 2) {
    return ParseFailed(line, "Expected a stream ID and optionally a track ID",
                       error);
  }
  if (num_fields == 1) {
    if (line.back() == kSdpDelimiterSpaceChar) {
      return ParseFailed(line, "Missing track ID in msid attribute.", error);
    }
    if (!track_id->empty()) {
      fields.push_back(*track_id);
    } else {
      // Ending with an empty string track will cause a random track id
      // to be generated later in the process.
      fields.push_back("");
    }
  }
  RTC_DCHECK_EQ(fields.size(), 2);

  // All track ids should be the same within an m section in a Unified Plan SDP.
  if (!track_id->empty() && track_id->compare(fields[1]) != 0) {
    return ParseFailed(
        line, "Two different track IDs in msid attribute in one m= section",
        error);
  }
  *track_id = fields[1];

  // msid:<msid-id>
  std::string new_stream_id;
  if (!GetValue(fields[0], kAttributeMsid, &new_stream_id, error)) {
    return false;
  }
  if (new_stream_id.empty()) {
    return ParseFailed(line, "Missing stream ID in msid attribute.", error);
  }
  // The special value "-" indicates "no MediaStream".
  if (new_stream_id.compare(kNoStreamMsid) != 0 &&
      !absl::c_any_of(*stream_ids,
                      [&new_stream_id](const std::string& existing_stream_id) {
                        return new_stream_id == existing_stream_id;
                      })) {
    stream_ids->push_back(new_stream_id);
  }
  return true;
}

static void RemoveInvalidRidDescriptions(const std::vector<int>& payload_types,
                                         std::vector<RidDescription>* rids) {
  RTC_DCHECK(rids);
  std::set<std::string> to_remove;
  std::set<std::string> unique_rids;

  // Check the rids to see which ones should be removed.
  for (RidDescription& rid : *rids) {
    // In the case of a duplicate, the entire "a=rid" line, and all "a=rid"
    // lines with rid-ids that duplicate this line, are discarded and MUST NOT
    // be included in the SDP Answer.
    auto pair = unique_rids.insert(rid.rid);
    // Insert will "fail" if element already exists.
    if (!pair.second) {
      to_remove.insert(rid.rid);
      continue;
    }

    // If the "a=rid" line contains a "pt=", the list of payload types
    // is verified against the list of valid payload types for the media
    // section (that is, those listed on the "m=" line).  Any PT missing
    // from the "m=" line is discarded from the set of values in the
    // "pt=".  If no values are left in the "pt=" parameter after this
    // processing, then the "a=rid" line is discarded.
    if (rid.payload_types.empty()) {
      // If formats were not specified, rid should not be removed.
      continue;
    }

    // Note: Spec does not mention how to handle duplicate formats.
    // Media section does not handle duplicates either.
    std::set<int> removed_formats;
    for (int payload_type : rid.payload_types) {
      if (!absl::c_linear_search(payload_types, payload_type)) {
        removed_formats.insert(payload_type);
      }
    }

    rid.payload_types.erase(
        std::remove_if(rid.payload_types.begin(), rid.payload_types.end(),
                       [&removed_formats](int format) {
                         return removed_formats.count(format) > 0;
                       }),
        rid.payload_types.end());

    // If all formats were removed then remove the rid alogether.
    if (rid.payload_types.empty()) {
      to_remove.insert(rid.rid);
    }
  }

  // Remove every rid description that appears in the to_remove list.
  if (!to_remove.empty()) {
    rids->erase(std::remove_if(rids->begin(), rids->end(),
                               [&to_remove](const RidDescription& rid) {
                                 return to_remove.count(rid.rid) > 0;
                               }),
                rids->end());
  }
}

// Create a new list (because SimulcastLayerList is immutable) without any
// layers that have a rid in the to_remove list.
// If a group of alternatives is empty after removing layers, the group should
// be removed altogether.
static SimulcastLayerList RemoveRidsFromSimulcastLayerList(
    const std::set<std::string>& to_remove,
    const SimulcastLayerList& layers) {
  SimulcastLayerList result;
  for (const std::vector<SimulcastLayer>& vector : layers) {
    std::vector<SimulcastLayer> new_layers;
    for (const SimulcastLayer& layer : vector) {
      if (to_remove.find(layer.rid) == to_remove.end()) {
        new_layers.push_back(layer);
      }
    }
    // If all layers were removed, do not add an entry.
    if (!new_layers.empty()) {
      result.AddLayerWithAlternatives(new_layers);
    }
  }

  return result;
}

// Will remove Simulcast Layers if:
// 1. They appear in both send and receive directions.
// 2. They do not appear in the list of `valid_rids`.
static void RemoveInvalidRidsFromSimulcast(
    const std::vector<RidDescription>& valid_rids,
    SimulcastDescription* simulcast) {
  RTC_DCHECK(simulcast);
  std::set<std::string> to_remove;
  std::vector<SimulcastLayer> all_send_layers =
      simulcast->send_layers().GetAllLayers();
  std::vector<SimulcastLayer> all_receive_layers =
      simulcast->receive_layers().GetAllLayers();

  // If a rid appears in both send and receive directions, remove it from both.
  // This algorithm runs in O(n^2) time, but for small n (as is the case with
  // simulcast layers) it should still perform well.
  for (const SimulcastLayer& send_layer : all_send_layers) {
    if (absl::c_any_of(all_receive_layers,
                       [&send_layer](const SimulcastLayer& layer) {
                         return layer.rid == send_layer.rid;
                       })) {
      to_remove.insert(send_layer.rid);
    }
  }

  // Add any rid that is not in the valid list to the remove set.
  for (const SimulcastLayer& send_layer : all_send_layers) {
    if (absl::c_none_of(valid_rids, [&send_layer](const RidDescription& rid) {
          return send_layer.rid == rid.rid &&
                 rid.direction == cricket::RidDirection::kSend;
        })) {
      to_remove.insert(send_layer.rid);
    }
  }

  // Add any rid that is not in the valid list to the remove set.
  for (const SimulcastLayer& receive_layer : all_receive_layers) {
    if (absl::c_none_of(
            valid_rids, [&receive_layer](const RidDescription& rid) {
              return receive_layer.rid == rid.rid &&
                     rid.direction == cricket::RidDirection::kReceive;
            })) {
      to_remove.insert(receive_layer.rid);
    }
  }

  simulcast->send_layers() =
      RemoveRidsFromSimulcastLayerList(to_remove, simulcast->send_layers());
  simulcast->receive_layers() =
      RemoveRidsFromSimulcastLayerList(to_remove, simulcast->receive_layers());
}

// RFC 3551
//  PT   encoding    media type  clock rate   channels
//                      name                    (Hz)
//  0    PCMU        A            8,000       1
//  1    reserved    A
//  2    reserved    A
//  3    GSM         A            8,000       1
//  4    G723        A            8,000       1
//  5    DVI4        A            8,000       1
//  6    DVI4        A           16,000       1
//  7    LPC         A            8,000       1
//  8    PCMA        A            8,000       1
//  9    G722        A            8,000       1
//  10   L16         A           44,100       2
//  11   L16         A           44,100       1
//  12   QCELP       A            8,000       1
//  13   CN          A            8,000       1
//  14   MPA         A           90,000       (see text)
//  15   G728        A            8,000       1
//  16   DVI4        A           11,025       1
//  17   DVI4        A           22,050       1
//  18   G729        A            8,000       1
struct StaticPayloadAudioCodec {
  const char* name;
  int clockrate;
  size_t channels;
};
static const StaticPayloadAudioCodec kStaticPayloadAudioCodecs[] = {
    {"PCMU", 8000, 1},  {"reserved", 0, 0}, {"reserved", 0, 0},
    {"GSM", 8000, 1},   {"G723", 8000, 1},  {"DVI4", 8000, 1},
    {"DVI4", 16000, 1}, {"LPC", 8000, 1},   {"PCMA", 8000, 1},
    {"G722", 8000, 1},  {"L16", 44100, 2},  {"L16", 44100, 1},
    {"QCELP", 8000, 1}, {"CN", 8000, 1},    {"MPA", 90000, 1},
    {"G728", 8000, 1},  {"DVI4", 11025, 1}, {"DVI4", 22050, 1},
    {"G729", 8000, 1},
};

void MaybeCreateStaticPayloadAudioCodecs(const std::vector<int>& fmts,
                                         MediaContentDescription* media_desc) {
  if (!media_desc) {
    return;
  }
  RTC_DCHECK(media_desc->codecs().empty());
  for (int payload_type : fmts) {
    if (!media_desc->HasCodec(payload_type) && payload_type >= 0 &&
        static_cast<uint32_t>(payload_type) <
            arraysize(kStaticPayloadAudioCodecs)) {
      std::string encoding_name = kStaticPayloadAudioCodecs[payload_type].name;
      int clock_rate = kStaticPayloadAudioCodecs[payload_type].clockrate;
      size_t channels = kStaticPayloadAudioCodecs[payload_type].channels;
      media_desc->AddCodec(cricket::CreateAudioCodec(
          payload_type, encoding_name, clock_rate, channels));
    }
  }
}

static void BackfillCodecParameters(std::vector<cricket::Codec>& codecs) {
  for (auto& codec : codecs) {
    std::string unused_value;
    if (absl::EqualsIgnoreCase(cricket::kVp9CodecName, codec.name)) {
      // https://datatracker.ietf.org/doc/html/draft-ietf-payload-vp9#section-6
      // profile-id defaults to "0"
      if (!codec.GetParam(cricket::kVP9ProfileId, &unused_value)) {
        codec.SetParam(cricket::kVP9ProfileId, "0");
      }
    } else if (absl::EqualsIgnoreCase(cricket::kH264CodecName, codec.name)) {
      // https://www.rfc-editor.org/rfc/rfc6184#section-6.2
      // packetization-mode defaults to "0"
      if (!codec.GetParam(cricket::kH264FmtpPacketizationMode, &unused_value)) {
        codec.SetParam(cricket::kH264FmtpPacketizationMode, "0");
      }
    } else if (absl::EqualsIgnoreCase(cricket::kAv1CodecName, codec.name)) {
      // https://aomediacodec.github.io/av1-rtp-spec/#72-sdp-parameters
      if (!codec.GetParam(cricket::kAv1FmtpProfile, &unused_value)) {
        codec.SetParam(cricket::kAv1FmtpProfile, "0");
      }
      if (!codec.GetParam(cricket::kAv1FmtpLevelIdx, &unused_value)) {
        codec.SetParam(cricket::kAv1FmtpLevelIdx, "5");
      }
      if (!codec.GetParam(cricket::kAv1FmtpTier, &unused_value)) {
        codec.SetParam(cricket::kAv1FmtpTier, "0");
      }
    } else if (absl::EqualsIgnoreCase(cricket::kH265CodecName, codec.name)) {
      // https://datatracker.ietf.org/doc/html/draft-aboba-avtcore-hevc-webrtc
      if (!codec.GetParam(cricket::kH265FmtpLevelId, &unused_value)) {
        codec.SetParam(cricket::kH265FmtpLevelId, "93");
      }
      if (!codec.GetParam(cricket::kH265FmtpTxMode, &unused_value)) {
        codec.SetParam(cricket::kH265FmtpTxMode, "SRST");
      }
    }
  }
}

static std::unique_ptr<MediaContentDescription> ParseContentDescription(
    absl::string_view message,
    const cricket::MediaType media_type,
    int mline_index,
    absl::string_view protocol,
    const std::vector<int>& payload_types,
    size_t* pos,
    std::string* content_name,
    bool* bundle_only,
    int* msid_signaling,
    TransportDescription* transport,
    std::vector<std::unique_ptr<JsepIceCandidate>>* candidates,
    SdpParseError* error) {
  std::unique_ptr<MediaContentDescription> media_desc;
  if (media_type == cricket::MediaType::MEDIA_TYPE_AUDIO) {
    media_desc = std::make_unique<AudioContentDescription>();
  } else if (media_type == cricket::MediaType::MEDIA_TYPE_VIDEO) {
    media_desc = std::make_unique<VideoContentDescription>();
  } else {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }

  media_desc->set_extmap_allow_mixed_enum(MediaContentDescription::kNo);
  if (!ParseContent(message, media_type, mline_index, protocol, payload_types,
                    pos, content_name, bundle_only, msid_signaling,
                    media_desc.get(), transport, candidates, error)) {
    return nullptr;
  }
  // Sort the codecs according to the m-line fmt list.
  std::unordered_map<int, int> payload_type_preferences;
  // "size + 1" so that the lowest preference payload type has a preference of
  // 1, which is greater than the default (0) for payload types not in the fmt
  // list.
  int preference = static_cast<int>(payload_types.size() + 1);
  for (int pt : payload_types) {
    payload_type_preferences[pt] = preference--;
  }
  std::vector<cricket::Codec> codecs = media_desc->codecs();
  absl::c_sort(codecs, [&payload_type_preferences](const cricket::Codec& a,
                                                   const cricket::Codec& b) {
    return payload_type_preferences[a.id] > payload_type_preferences[b.id];
  });
  // Backfill any default parameters.
  BackfillCodecParameters(codecs);

  media_desc->set_codecs(codecs);
  return media_desc;
}

bool HasDuplicateMsidLines(cricket::SessionDescription* desc) {
  std::set<std::pair<std::string, std::string>> seen_msids;
  for (const cricket::ContentInfo& content : desc->contents()) {
    for (const cricket::StreamParams& stream :
         content.media_description()->streams()) {
      auto msid = std::pair(stream.first_stream_id(), stream.id);
      if (seen_msids.find(msid) != seen_msids.end()) {
        return true;
      }
      seen_msids.insert(std::move(msid));
    }
  }
  return false;
}

bool ParseMediaDescription(
    absl::string_view message,
    const TransportDescription& session_td,
    const RtpHeaderExtensions& session_extmaps,
    size_t* pos,
    const rtc::SocketAddress& session_connection_addr,
    cricket::SessionDescription* desc,
    std::vector<std::unique_ptr<JsepIceCandidate>>* candidates,
    SdpParseError* error) {
  RTC_DCHECK(desc != NULL);
  int mline_index = -1;
  int msid_signaling = desc->msid_signaling();

  // Zero or more media descriptions
  // RFC 4566
  // m=<media> <port> <proto> <fmt>
  while (absl::optional<absl::string_view> mline =
             GetLineWithType(message, pos, kLineTypeMedia)) {
    ++mline_index;

    std::vector<absl::string_view> fields =
        rtc::split(mline->substr(kLinePrefixLength), kSdpDelimiterSpaceChar);

    const size_t expected_min_fields = 4;
    if (fields.size() < expected_min_fields) {
      return ParseFailedExpectMinFieldNum(*mline, expected_min_fields, error);
    }
    bool port_rejected = false;
    // RFC 3264
    // To reject an offered stream, the port number in the corresponding stream
    // in the answer MUST be set to zero.
    if (fields[1] == kMediaPortRejected) {
      port_rejected = true;
    }

    int port = 0;
    if (!rtc::FromString<int>(fields[1], &port) || !IsValidPort(port)) {
      return ParseFailed(*mline, "The port number is invalid", error);
    }
    absl::string_view protocol = fields[2];

    // <fmt>
    std::vector<int> payload_types;
    if (cricket::IsRtpProtocol(protocol)) {
      for (size_t j = 3; j < fields.size(); ++j) {
        int pl = 0;
        if (!GetPayloadTypeFromString(*mline, fields[j], &pl, error)) {
          return false;
        }
        payload_types.push_back(pl);
      }
    }

    // Make a temporary TransportDescription based on `session_td`.
    // Some of this gets overwritten by ParseContent.
    TransportDescription transport(
        session_td.transport_options, session_td.ice_ufrag, session_td.ice_pwd,
        session_td.ice_mode, session_td.connection_role,
        session_td.identity_fingerprint.get());

    std::unique_ptr<MediaContentDescription> content;
    std::string content_name;
    bool bundle_only = false;
    int section_msid_signaling = cricket::kMsidSignalingNotUsed;
    absl::string_view media_type = fields[0];
    if ((media_type == kMediaTypeVideo || media_type == kMediaTypeAudio) &&
        !cricket::IsRtpProtocol(protocol)) {
      return ParseFailed(*mline, "Unsupported protocol for media type", error);
    }
    if (media_type == kMediaTypeVideo) {
      content = ParseContentDescription(
          message, cricket::MEDIA_TYPE_VIDEO, mline_index, protocol,
          payload_types, pos, &content_name, &bundle_only,
          &section_msid_signaling, &transport, candidates, error);
    } else if (media_type == kMediaTypeAudio) {
      content = ParseContentDescription(
          message, cricket::MEDIA_TYPE_AUDIO, mline_index, protocol,
          payload_types, pos, &content_name, &bundle_only,
          &section_msid_signaling, &transport, candidates, error);
    } else if (media_type == kMediaTypeData && cricket::IsDtlsSctp(protocol)) {
      // The draft-03 format is:
      // m=application <port> DTLS/SCTP <sctp-port>...
      // use_sctpmap should be false.
      // The draft-26 format is:
      // m=application <port> UDP/DTLS/SCTP webrtc-datachannel
      // use_sctpmap should be false.
      auto data_desc = std::make_unique<SctpDataContentDescription>();
      // Default max message size is 64K
      // according to draft-ietf-mmusic-sctp-sdp-26
      data_desc->set_max_message_size(kDefaultSctpMaxMessageSize);
      int p;
      if (rtc::FromString(fields[3], &p)) {
        data_desc->set_port(p);
      } else if (fields[3] == kDefaultSctpmapProtocol) {
        data_desc->set_use_sctpmap(false);
      }
      if (!ParseContent(message, cricket::MEDIA_TYPE_DATA, mline_index,
                        protocol, payload_types, pos, &content_name,
                        &bundle_only, &section_msid_signaling, data_desc.get(),
                        &transport, candidates, error)) {
        return false;
      }
      data_desc->set_protocol(protocol);
      content = std::move(data_desc);
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported media type: " << *mline;
      auto unsupported_desc =
          std::make_unique<UnsupportedContentDescription>(media_type);
      if (!ParseContent(message, cricket::MEDIA_TYPE_UNSUPPORTED, mline_index,
                        protocol, payload_types, pos, &content_name,
                        &bundle_only, &section_msid_signaling,
                        unsupported_desc.get(), &transport, candidates,
                        error)) {
        return false;
      }
      unsupported_desc->set_protocol(protocol);
      content = std::move(unsupported_desc);
    }
    if (!content.get()) {
      // ParseContentDescription returns NULL if failed.
      return false;
    }

    msid_signaling |= section_msid_signaling;

    bool content_rejected = false;
    // A port of 0 is not interpreted as a rejected m= section when it's
    // used along with a=bundle-only.
    if (bundle_only) {
      if (!port_rejected) {
        // Usage of bundle-only with a nonzero port is unspecified. So just
        // ignore bundle-only if we see this.
        bundle_only = false;
        RTC_LOG(LS_WARNING)
            << "a=bundle-only attribute observed with a nonzero "
               "port; this usage is unspecified so the attribute is being "
               "ignored.";
      }
    } else {
      // If not using bundle-only, interpret port 0 in the normal way; the m=
      // section is being rejected.
      content_rejected = port_rejected;
    }

    if (content->as_unsupported()) {
      content_rejected = true;
    } else if (cricket::IsRtpProtocol(protocol) && !content->as_sctp()) {
      content->set_protocol(std::string(protocol));
      // Set the extmap.
      if (!session_extmaps.empty() &&
          !content->rtp_header_extensions().empty()) {
        return ParseFailed("",
                           "The a=extmap MUST be either all session level or "
                           "all media level.",
                           error);
      }
      for (size_t i = 0; i < session_extmaps.size(); ++i) {
        content->AddRtpHeaderExtension(session_extmaps[i]);
      }
    } else if (content->as_sctp()) {
      // Do nothing, it's OK
    } else {
      RTC_LOG(LS_WARNING) << "Parse failed with unknown protocol " << protocol;
      return false;
    }

    // Use the session level connection address if the media level addresses are
    // not specified.
    rtc::SocketAddress address;
    address = content->connection_address().IsNil()
                  ? session_connection_addr
                  : content->connection_address();
    address.SetPort(port);
    content->set_connection_address(address);

    desc->AddContent(content_name,
                     cricket::IsDtlsSctp(protocol) ? MediaProtocolType::kSctp
                                                   : MediaProtocolType::kRtp,
                     content_rejected, bundle_only, std::move(content));
    // Create TransportInfo with the media level "ice-pwd" and "ice-ufrag".
    desc->AddTransportInfo(TransportInfo(content_name, transport));
  }
  // Apply whole-description sanity checks
  if (HasDuplicateMsidLines(desc)) {
    ParseFailed(message, *pos, "Duplicate a=msid lines detected", error);
    return false;
  }

  desc->set_msid_signaling(msid_signaling);

  size_t end_of_message = message.size();
  if (mline_index == -1 && *pos != end_of_message) {
    ParseFailed(message, *pos, "Expects m line.", error);
    return false;
  }
  return true;
}

void AddParameters(const webrtc::CodecParameterMap& parameters,
                   cricket::Codec* codec) {
  for (const auto& entry : parameters) {
    const std::string& key = entry.first;
    const std::string& value = entry.second;
    codec->SetParam(key, value);
  }
}

void AddFeedbackParameter(const cricket::FeedbackParam& feedback_param,
                          cricket::Codec* codec) {
  codec->AddFeedbackParam(feedback_param);
}

void AddFeedbackParameters(const cricket::FeedbackParams& feedback_params,
                           cricket::Codec* codec) {
  for (const cricket::FeedbackParam& param : feedback_params.params()) {
    codec->AddFeedbackParam(param);
  }
}

// Gets the current codec setting associated with `payload_type`. If there
// is no Codec associated with that payload type it returns an empty codec
// with that payload type.
cricket::Codec GetCodecWithPayloadType(
    cricket::MediaType type,
    const std::vector<cricket::Codec>& codecs,
    int payload_type) {
  const cricket::Codec* codec = FindCodecById(codecs, payload_type);
  if (codec)
    return *codec;
  // Return empty codec with `payload_type`.
  if (type == cricket::MEDIA_TYPE_AUDIO) {
    return cricket::CreateAudioCodec(payload_type, "", 0, 0);
  } else {
    return cricket::CreateVideoCodec(payload_type, "");
  }
}

// Updates or creates a new codec entry in the media description.
void AddOrReplaceCodec(MediaContentDescription* content_desc,
                       const cricket::Codec& codec) {
  std::vector<cricket::Codec> codecs = content_desc->codecs();
  bool found = false;
  for (cricket::Codec& existing_codec : codecs) {
    if (codec.id == existing_codec.id) {
      // Overwrite existing codec with the new codec.
      existing_codec = codec;
      found = true;
      break;
    }
  }
  if (!found) {
    content_desc->AddCodec(codec);
    return;
  }
  content_desc->set_codecs(codecs);
}

// Adds or updates existing codec corresponding to `payload_type` according
// to `parameters`.
void UpdateCodec(MediaContentDescription* content_desc,
                 int payload_type,
                 const webrtc::CodecParameterMap& parameters) {
  // Codec might already have been populated (from rtpmap).
  cricket::Codec new_codec = GetCodecWithPayloadType(
      content_desc->type(), content_desc->codecs(), payload_type);
  AddParameters(parameters, &new_codec);
  AddOrReplaceCodec(content_desc, new_codec);
}

// Adds or updates existing codec corresponding to `payload_type` according
// to `feedback_param`.
void UpdateCodec(MediaContentDescription* content_desc,
                 int payload_type,
                 const cricket::FeedbackParam& feedback_param) {
  // Codec might already have been populated (from rtpmap).
  cricket::Codec new_codec = GetCodecWithPayloadType(
      content_desc->type(), content_desc->codecs(), payload_type);
  AddFeedbackParameter(feedback_param, &new_codec);
  AddOrReplaceCodec(content_desc, new_codec);
}

// Adds or updates existing video codec corresponding to `payload_type`
// according to `packetization`.
void UpdateVideoCodecPacketization(MediaContentDescription* desc,
                                   int payload_type,
                                   absl::string_view packetization) {
  if (packetization != cricket::kPacketizationParamRaw) {
    // Ignore unsupported packetization attribute.
    return;
  }

  // Codec might already have been populated (from rtpmap).
  cricket::Codec codec =
      GetCodecWithPayloadType(desc->type(), desc->codecs(), payload_type);
  codec.packetization = std::string(packetization);
  AddOrReplaceCodec(desc, codec);
}

absl::optional<cricket::Codec> PopWildcardCodec(
    std::vector<cricket::Codec>* codecs) {
  RTC_DCHECK(codecs);
  for (auto iter = codecs->begin(); iter != codecs->end(); ++iter) {
    if (iter->id == kWildcardPayloadType) {
      cricket::Codec wildcard_codec = *iter;
      codecs->erase(iter);
      return wildcard_codec;
    }
  }
  return absl::nullopt;
}

void UpdateFromWildcardCodecs(cricket::MediaContentDescription* desc) {
  RTC_DCHECK(desc);
  auto codecs = desc->codecs();
  absl::optional<cricket::Codec> wildcard_codec = PopWildcardCodec(&codecs);
  if (!wildcard_codec) {
    return;
  }
  for (auto& codec : codecs) {
    AddFeedbackParameters(wildcard_codec->feedback_params, &codec);
  }
  desc->set_codecs(codecs);
}

void AddAudioAttribute(const std::string& name,
                       absl::string_view value,
                       MediaContentDescription* desc) {
  RTC_DCHECK(desc);
  if (value.empty()) {
    return;
  }
  std::vector<cricket::Codec> codecs = desc->codecs();
  for (cricket::Codec& codec : codecs) {
    codec.params[name] = std::string(value);
  }
  desc->set_codecs(codecs);
}

bool ParseContent(absl::string_view message,
                  const cricket::MediaType media_type,
                  int mline_index,
                  absl::string_view protocol,
                  const std::vector<int>& payload_types,
                  size_t* pos,
                  std::string* content_name,
                  bool* bundle_only,
                  int* msid_signaling,
                  MediaContentDescription* media_desc,
                  TransportDescription* transport,
                  std::vector<std::unique_ptr<JsepIceCandidate>>* candidates,
                  SdpParseError* error) {
  RTC_DCHECK(media_desc != NULL);
  RTC_DCHECK(content_name != NULL);
  RTC_DCHECK(transport != NULL);

  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    MaybeCreateStaticPayloadAudioCodecs(payload_types, media_desc);
  }

  // The media level "ice-ufrag" and "ice-pwd".
  // The candidates before update the media level "ice-pwd" and "ice-ufrag".
  Candidates candidates_orig;
  std::string mline_id;
  // Tracks created out of the ssrc attributes.
  StreamParamsVec tracks;
  SsrcInfoVec ssrc_infos;
  SsrcGroupVec ssrc_groups;
  std::string maxptime_as_string;
  std::string ptime_as_string;
  std::vector<std::string> stream_ids;
  std::string track_id;
  SimulcastSdpSerializer deserializer;
  std::vector<RidDescription> rids;
  SimulcastDescription simulcast;

  // Loop until the next m line
  while (!IsLineType(message, kLineTypeMedia, *pos)) {
    absl::optional<absl::string_view> line = GetLine(message, pos);
    if (!line.has_value()) {
      if (*pos >= message.size()) {
        break;  // Done parsing
      } else {
        return ParseFailed(message, *pos, "Invalid SDP line.", error);
      }
    }

    // RFC 4566
    // b=* (zero or more bandwidth information lines)
    if (IsLineType(*line, kLineTypeSessionBandwidth)) {
      std::string bandwidth;
      std::string bandwidth_type;
      if (!rtc::tokenize_first(line->substr(kLinePrefixLength),
                               kSdpDelimiterColonChar, &bandwidth_type,
                               &bandwidth)) {
        return ParseFailed(
            *line,
            "b= syntax error, does not match b=<modifier>:<bandwidth-value>.",
            error);
      }
      if (!(bandwidth_type == kApplicationSpecificBandwidth ||
            bandwidth_type == kTransportSpecificBandwidth)) {
        // Ignore unknown bandwidth types.
        continue;
      }
      int b = 0;
      if (!GetValueFromString(*line, bandwidth, &b, error)) {
        return false;
      }
      // TODO(deadbeef): Historically, applications may be setting a value
      // of -1 to mean "unset any previously set bandwidth limit", even
      // though ommitting the "b=AS" entirely will do just that. Once we've
      // transitioned applications to doing the right thing, it would be
      // better to treat this as a hard error instead of just ignoring it.
      if (bandwidth_type == kApplicationSpecificBandwidth && b == -1) {
        RTC_LOG(LS_WARNING) << "Ignoring \"b=AS:-1\"; will be treated as \"no "
                               "bandwidth limit\".";
        continue;
      }
      if (b < 0) {
        return ParseFailed(
            *line, "b=" + bandwidth_type + " value can't be negative.", error);
      }
      // Convert values. Prevent integer overflow.
      if (bandwidth_type == kApplicationSpecificBandwidth) {
        b = std::min(b, INT_MAX / 1000) * 1000;
      } else {
        b = std::min(b, INT_MAX);
      }
      media_desc->set_bandwidth(b);
      media_desc->set_bandwidth_type(bandwidth_type);
      continue;
    }

    // Parse the media level connection data.
    if (IsLineType(*line, kLineTypeConnection)) {
      rtc::SocketAddress addr;
      if (!ParseConnectionData(*line, &addr, error)) {
        return false;
      }
      media_desc->set_connection_address(addr);
      continue;
    }

    if (!IsLineType(*line, kLineTypeAttributes)) {
      // TODO(deadbeef): Handle other lines if needed.
      RTC_LOG(LS_VERBOSE) << "Ignored line: " << *line;
      continue;
    }

    // Handle attributes common to SCTP and RTP.
    if (HasAttribute(*line, kAttributeMid)) {
      // RFC 3388
      // mid-attribute      = "a=mid:" identification-tag
      // identification-tag = token
      // Use the mid identification-tag as the content name.
      if (!GetSingleTokenValue(*line, kAttributeMid, &mline_id, error)) {
        return false;
      }
      *content_name = mline_id;
    } else if (HasAttribute(*line, kAttributeBundleOnly)) {
      *bundle_only = true;
    } else if (HasAttribute(*line, kAttributeCandidate)) {
      Candidate candidate;
      if (!ParseCandidate(*line, &candidate, error, false)) {
        return false;
      }
      // ParseCandidate will parse non-standard ufrag and password attributes,
      // since it's used for candidate trickling, but we only want to process
      // the "a=ice-ufrag"/"a=ice-pwd" values in a session description, so
      // strip them off at this point.
      candidate.set_username(std::string());
      candidate.set_password(std::string());
      candidates_orig.push_back(candidate);
    } else if (HasAttribute(*line, kAttributeIceUfrag)) {
      if (!GetValue(*line, kAttributeIceUfrag, &transport->ice_ufrag, error)) {
        return false;
      }
    } else if (HasAttribute(*line, kAttributeIcePwd)) {
      if (!GetValue(*line, kAttributeIcePwd, &transport->ice_pwd, error)) {
        return false;
      }
    } else if (HasAttribute(*line, kAttributeIceOption)) {
      if (!ParseIceOptions(*line, &transport->transport_options, error)) {
        return false;
      }
    } else if (HasAttribute(*line, kAttributeFmtp)) {
      if (!ParseFmtpAttributes(*line, media_type, media_desc, error)) {
        return false;
      }
    } else if (HasAttribute(*line, kAttributeFingerprint)) {
      std::unique_ptr<rtc::SSLFingerprint> fingerprint;
      if (!ParseFingerprintAttribute(*line, &fingerprint, error)) {
        return false;
      }
      transport->identity_fingerprint = std::move(fingerprint);
    } else if (HasAttribute(*line, kAttributeSetup)) {
      if (!ParseDtlsSetup(*line, &(transport->connection_role), error)) {
        return false;
      }
    } else if (cricket::IsDtlsSctp(protocol) &&
               media_type == cricket::MEDIA_TYPE_DATA) {
      //
      // SCTP specific attributes
      //
      if (HasAttribute(*line, kAttributeSctpPort)) {
        if (media_desc->as_sctp()->use_sctpmap()) {
          return ParseFailed(
              *line, "sctp-port attribute can't be used with sctpmap.", error);
        }
        int sctp_port;
        if (!ParseSctpPort(*line, &sctp_port, error)) {
          return false;
        }
        media_desc->as_sctp()->set_port(sctp_port);
      } else if (HasAttribute(*line, kAttributeMaxMessageSize)) {
        int max_message_size;
        if (!ParseSctpMaxMessageSize(*line, &max_message_size, error)) {
          return false;
        }
        media_desc->as_sctp()->set_max_message_size(max_message_size);
      } else if (HasAttribute(*line, kAttributeSctpmap)) {
        // Ignore a=sctpmap: from early versions of draft-ietf-mmusic-sctp-sdp
        continue;
      }
    } else if (cricket::IsRtpProtocol(protocol)) {
      //
      // RTP specific attributes
      //
      if (HasAttribute(*line, kAttributeRtcpMux)) {
        media_desc->set_rtcp_mux(true);
      } else if (HasAttribute(*line, kAttributeRtcpReducedSize)) {
        media_desc->set_rtcp_reduced_size(true);
      } else if (HasAttribute(*line, kAttributeRtcpRemoteEstimate)) {
        media_desc->set_remote_estimate(true);
      } else if (HasAttribute(*line, kAttributeSsrcGroup)) {
        if (!ParseSsrcGroupAttribute(*line, &ssrc_groups, error)) {
          return false;
        }
      } else if (HasAttribute(*line, kAttributeSsrc)) {
        if (!ParseSsrcAttribute(*line, &ssrc_infos, msid_signaling, error)) {
          return false;
        }
      } else if (HasAttribute(*line, kAttributeRtpmap)) {
        if (!ParseRtpmapAttribute(*line, media_type, payload_types, media_desc,
                                  error)) {
          return false;
        }
      } else if (HasAttribute(*line, kCodecParamMaxPTime)) {
        if (!GetValue(*line, kCodecParamMaxPTime, &maxptime_as_string, error)) {
          return false;
        }
      } else if (HasAttribute(*line, kAttributePacketization)) {
        if (!ParsePacketizationAttribute(*line, media_type, media_desc,
                                         error)) {
          return false;
        }
      } else if (HasAttribute(*line, kAttributeRtcpFb)) {
        if (!ParseRtcpFbAttribute(*line, media_type, media_desc, error)) {
          return false;
        }
      } else if (HasAttribute(*line, kCodecParamPTime)) {
        if (!GetValue(*line, kCodecParamPTime, &ptime_as_string, error)) {
          return false;
        }
      } else if (HasAttribute(*line, kAttributeSendOnly)) {
        media_desc->set_direction(RtpTransceiverDirection::kSendOnly);
      } else if (HasAttribute(*line, kAttributeRecvOnly)) {
        media_desc->set_direction(RtpTransceiverDirection::kRecvOnly);
      } else if (HasAttribute(*line, kAttributeInactive)) {
        media_desc->set_direction(RtpTransceiverDirection::kInactive);
      } else if (HasAttribute(*line, kAttributeSendRecv)) {
        media_desc->set_direction(RtpTransceiverDirection::kSendRecv);
      } else if (HasAttribute(*line, kAttributeExtmapAllowMixed)) {
        media_desc->set_extmap_allow_mixed_enum(
            MediaContentDescription::kMedia);
      } else if (HasAttribute(*line, kAttributeExtmap)) {
        RtpExtension extmap;
        if (!ParseExtmap(*line, &extmap, error)) {
          return false;
        }
        media_desc->AddRtpHeaderExtension(extmap);
      } else if (HasAttribute(*line, kAttributeXGoogleFlag)) {
        // Experimental attribute.  Conference mode activates more aggressive
        // AEC and NS settings.
        // TODO(deadbeef): expose API to set these directly.
        std::string flag_value;
        if (!GetValue(*line, kAttributeXGoogleFlag, &flag_value, error)) {
          return false;
        }
        if (flag_value.compare(kValueConference) == 0)
          media_desc->set_conference_mode(true);
      } else if (HasAttribute(*line, kAttributeMsid)) {
        if (!ParseMsidAttribute(*line, &stream_ids, &track_id, error)) {
          return false;
        }
        *msid_signaling |= cricket::kMsidSignalingMediaSection;
      } else if (HasAttribute(*line, kAttributeRid)) {
        const size_t kRidPrefixLength =
            kLinePrefixLength + arraysize(kAttributeRid);
        if (line->size() <= kRidPrefixLength) {
          RTC_LOG(LS_INFO) << "Ignoring empty RID attribute: " << *line;
          continue;
        }
        RTCErrorOr<RidDescription> error_or_rid_description =
            deserializer.DeserializeRidDescription(
                line->substr(kRidPrefixLength));

        // Malformed a=rid lines are discarded.
        if (!error_or_rid_description.ok()) {
          RTC_LOG(LS_INFO) << "Ignoring malformed RID line: '" << *line
                           << "'. Error: "
                           << error_or_rid_description.error().message();
          continue;
        }

        rids.push_back(error_or_rid_description.MoveValue());
      } else if (HasAttribute(*line, kAttributeSimulcast)) {
        const size_t kSimulcastPrefixLength =
            kLinePrefixLength + arraysize(kAttributeSimulcast);
        if (line->size() <= kSimulcastPrefixLength) {
          return ParseFailed(*line, "Simulcast attribute is empty.", error);
        }

        if (!simulcast.empty()) {
          return ParseFailed(*line, "Multiple Simulcast attributes specified.",
                             error);
        }

        RTCErrorOr<SimulcastDescription> error_or_simulcast =
            deserializer.DeserializeSimulcastDescription(
                line->substr(kSimulcastPrefixLength));
        if (!error_or_simulcast.ok()) {
          return ParseFailed(*line,
                             std::string("Malformed simulcast line: ") +
                                 error_or_simulcast.error().message(),
                             error);
        }

        simulcast = error_or_simulcast.value();
      } else if (HasAttribute(*line, kAttributeRtcp)) {
        // Ignore and do not log a=rtcp line.
        // JSEP  section 5.8.2 (media section parsing) says to ignore it.
        continue;
      } else {
        // Unrecognized attribute in RTP protocol.
        RTC_LOG(LS_VERBOSE) << "Ignored line: " << *line;
        continue;
      }
    } else {
      // Only parse lines that we are interested of.
      RTC_LOG(LS_VERBOSE) << "Ignored line: " << *line;
      continue;
    }
  }

  // Remove duplicate or inconsistent rids.
  RemoveInvalidRidDescriptions(payload_types, &rids);

  // If simulcast is specifed, split the rids into send and receive.
  // Rids that do not appear in simulcast attribute will be removed.
  std::vector<RidDescription> send_rids;
  std::vector<RidDescription> receive_rids;
  if (!simulcast.empty()) {
    // Verify that the rids in simulcast match rids in sdp.
    RemoveInvalidRidsFromSimulcast(rids, &simulcast);

    // Use simulcast description to figure out Send / Receive RIDs.
    std::map<std::string, RidDescription> rid_map;
    for (const RidDescription& rid : rids) {
      rid_map[rid.rid] = rid;
    }

    for (const auto& layer : simulcast.send_layers().GetAllLayers()) {
      auto iter = rid_map.find(layer.rid);
      RTC_DCHECK(iter != rid_map.end());
      send_rids.push_back(iter->second);
    }

    for (const auto& layer : simulcast.receive_layers().GetAllLayers()) {
      auto iter = rid_map.find(layer.rid);
      RTC_DCHECK(iter != rid_map.end());
      receive_rids.push_back(iter->second);
    }

    media_desc->set_simulcast_description(simulcast);
  } else {
    // RID is specified in RFC 8851, which identifies a lot of usages.
    // We only support RFC 8853 usage of RID, not anything else.
    // Ignore all RID parameters when a=simulcast is missing.
    // In particular do NOT do send_rids = rids;
    RTC_LOG(LS_VERBOSE) << "Ignoring send_rids without simulcast";
  }

  media_desc->set_receive_rids(receive_rids);

  // Create tracks from the `ssrc_infos`.
  // If the stream_id/track_id for all SSRCS are identical, one StreamParams
  // will be created in CreateTracksFromSsrcInfos, containing all the SSRCs from
  // the m= section.
  if (!ssrc_infos.empty()) {
    CreateTracksFromSsrcInfos(ssrc_infos, stream_ids, track_id, &tracks,
                              *msid_signaling);
  } else if (media_type != cricket::MEDIA_TYPE_DATA &&
             (*msid_signaling & cricket::kMsidSignalingMediaSection)) {
    // If the stream_ids/track_id was signaled but SSRCs were unsignaled we
    // still create a track. This isn't done for data media types because
    // StreamParams aren't used for SCTP streams, and RTP data channels don't
    // support unsignaled SSRCs.
    // If track id was not specified, create a random one.
    if (track_id.empty()) {
      track_id = rtc::CreateRandomString(8);
    }
    CreateTrackWithNoSsrcs(stream_ids, track_id, send_rids, &tracks);
  }

  // Add the ssrc group to the track.
  for (const SsrcGroup& ssrc_group : ssrc_groups) {
    if (ssrc_group.ssrcs.empty()) {
      continue;
    }
    uint32_t ssrc = ssrc_group.ssrcs.front();
    for (StreamParams& track : tracks) {
      if (track.has_ssrc(ssrc)) {
        track.ssrc_groups.push_back(ssrc_group);
      }
    }
  }

  // Add the new tracks to the `media_desc`.
  for (StreamParams& track : tracks) {
    media_desc->AddStream(track);
  }

  UpdateFromWildcardCodecs(media_desc);
  // Codec has not been populated correctly unless the name has been set. This
  // can happen if an SDP has an fmtp or rtcp-fb with a payload type but doesn't
  // have a corresponding "rtpmap" line. This should lead to a parse error.
  if (!absl::c_all_of(media_desc->codecs(), [](const cricket::Codec codec) {
        return !codec.name.empty();
      })) {
    return ParseFailed("Failed to parse codecs correctly.", error);
  }
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    AddAudioAttribute(kCodecParamMaxPTime, maxptime_as_string, media_desc);
    AddAudioAttribute(kCodecParamPTime, ptime_as_string, media_desc);
  }

  // RFC 5245
  // Update the candidates with the media level "ice-pwd" and "ice-ufrag".
  for (Candidate& candidate : candidates_orig) {
    RTC_DCHECK(candidate.username().empty() ||
               candidate.username() == transport->ice_ufrag);
    candidate.set_username(transport->ice_ufrag);
    RTC_DCHECK(candidate.password().empty());
    candidate.set_password(transport->ice_pwd);
    candidates->push_back(
        std::make_unique<JsepIceCandidate>(mline_id, mline_index, candidate));
  }

  return true;
}

bool ParseSsrcAttribute(absl::string_view line,
                        SsrcInfoVec* ssrc_infos,
                        int* msid_signaling,
                        SdpParseError* error) {
  RTC_DCHECK(ssrc_infos != NULL);
  // RFC 5576
  // a=ssrc:<ssrc-id> <attribute>
  // a=ssrc:<ssrc-id> <attribute>:<value>
  std::string field1, field2;
  if (!rtc::tokenize_first(line.substr(kLinePrefixLength),
                           kSdpDelimiterSpaceChar, &field1, &field2)) {
    const size_t expected_fields = 2;
    return ParseFailedExpectFieldNum(line, expected_fields, error);
  }

  // ssrc:<ssrc-id>
  std::string ssrc_id_s;
  if (!GetValue(field1, kAttributeSsrc, &ssrc_id_s, error)) {
    return false;
  }
  uint32_t ssrc_id = 0;
  if (!GetValueFromString(line, ssrc_id_s, &ssrc_id, error)) {
    return false;
  }

  std::string attribute;
  std::string value;
  if (!rtc::tokenize_first(field2, kSdpDelimiterColonChar, &attribute,
                           &value)) {
    rtc::StringBuilder description;
    description << "Failed to get the ssrc attribute value from " << field2
                << ". Expected format <attribute>:<value>.";
    return ParseFailed(line, description.Release(), error);
  }

  // Check if there's already an item for this `ssrc_id`. Create a new one if
  // there isn't.
  auto ssrc_info_it =
      absl::c_find_if(*ssrc_infos, [ssrc_id](const SsrcInfo& ssrc_info) {
        return ssrc_info.ssrc_id == ssrc_id;
      });
  if (ssrc_info_it == ssrc_infos->end()) {
    SsrcInfo info;
    info.ssrc_id = ssrc_id;
    ssrc_infos->push_back(info);
    ssrc_info_it = ssrc_infos->end() - 1;
  }
  SsrcInfo& ssrc_info = *ssrc_info_it;

  // Store the info to the `ssrc_info`.
  if (attribute == kSsrcAttributeCname) {
    // RFC 5576
    // cname:<value>
    ssrc_info.cname = value;
  } else if (attribute == kSsrcAttributeMsid) {
    // draft-alvestrand-mmusic-msid-00
    // msid:identifier [appdata]
    std::vector<absl::string_view> fields =
        rtc::split(value, kSdpDelimiterSpaceChar);
    if (fields.size() < 1 || fields.size() > 2) {
      return ParseFailed(
          line, "Expected format \"msid:<identifier>[ <appdata>]\".", error);
    }
    ssrc_info.stream_id = std::string(fields[0]);
    if (fields.size() == 2) {
      ssrc_info.track_id = std::string(fields[1]);
    }
    *msid_signaling |= cricket::kMsidSignalingSsrcAttribute;
  } else {
    RTC_LOG(LS_INFO) << "Ignored unknown ssrc-specific attribute: " << line;
  }
  return true;
}

bool ParseSsrcGroupAttribute(absl::string_view line,
                             SsrcGroupVec* ssrc_groups,
                             SdpParseError* error) {
  RTC_DCHECK(ssrc_groups != NULL);
  // RFC 5576
  // a=ssrc-group:<semantics> <ssrc-id> ...
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  const size_t expected_min_fields = 2;
  if (fields.size() < expected_min_fields) {
    return ParseFailedExpectMinFieldNum(line, expected_min_fields, error);
  }
  std::string semantics;
  if (!GetValue(fields[0], kAttributeSsrcGroup, &semantics, error)) {
    return false;
  }
  std::vector<uint32_t> ssrcs;
  for (size_t i = 1; i < fields.size(); ++i) {
    uint32_t ssrc = 0;
    if (!GetValueFromString(line, fields[i], &ssrc, error)) {
      return false;
    }
    // Reject duplicates. While not forbidden by RFC 5576,
    // they don't make sense.
    if (absl::c_linear_search(ssrcs, ssrc)) {
      return ParseFailed(line, "Duplicate SSRC in ssrc-group", error);
    }
    ssrcs.push_back(ssrc);
  }
  ssrc_groups->push_back(SsrcGroup(semantics, ssrcs));
  return true;
}

// Updates or creates a new codec entry in the audio description with according
// to `name`, `clockrate`, `bitrate`, and `channels`.
void UpdateCodec(int payload_type,
                 absl::string_view name,
                 int clockrate,
                 int bitrate,
                 size_t channels,
                 MediaContentDescription* desc) {
  // Codec may already be populated with (only) optional parameters
  // (from an fmtp).
  cricket::Codec codec =
      GetCodecWithPayloadType(desc->type(), desc->codecs(), payload_type);
  codec.name = std::string(name);
  codec.clockrate = clockrate;
  codec.bitrate = bitrate;
  codec.channels = channels;
  AddOrReplaceCodec(desc, codec);
}

// Updates or creates a new codec entry in the video description according to
// `name`, `width`, `height`, and `framerate`.
void UpdateCodec(int payload_type,
                 absl::string_view name,
                 MediaContentDescription* desc) {
  // Codec may already be populated with (only) optional parameters
  // (from an fmtp).
  cricket::Codec codec =
      GetCodecWithPayloadType(desc->type(), desc->codecs(), payload_type);
  codec.name = std::string(name);
  AddOrReplaceCodec(desc, codec);
}

bool ParseRtpmapAttribute(absl::string_view line,
                          const cricket::MediaType media_type,
                          const std::vector<int>& payload_types,
                          MediaContentDescription* media_desc,
                          SdpParseError* error) {
  static const int kFirstDynamicPayloadTypeLowerRange = 35;
  std::vector<absl::string_view> fields =
      rtc::split(line.substr(kLinePrefixLength), kSdpDelimiterSpaceChar);
  // RFC 4566
  // a=rtpmap:<payload type> <encoding name>/<clock rate>[/<encodingparameters>]
  const size_t expected_min_fields = 2;
  if (fields.size() < expected_min_fields) {
    return ParseFailedExpectMinFieldNum(line, expected_min_fields, error);
  }
  std::string payload_type_value;
  if (!GetValue(fields[0], kAttributeRtpmap, &payload_type_value, error)) {
    return false;
  }
  int payload_type = 0;
  if (!GetPayloadTypeFromString(line, payload_type_value, &payload_type,
                                error)) {
    return false;
  }

  if (!absl::c_linear_search(payload_types, payload_type)) {
    RTC_LOG(LS_WARNING) << "Ignore rtpmap line that did not appear in the "
                           "<fmt> of the m-line: "
                        << line;
    return true;
  }
  std::vector<absl::string_view> codec_params = rtc::split(fields[1], '/');
  // <encoding name>/<clock rate>[/<encodingparameters>]
  // 2 mandatory fields
  if (codec_params.size() < 2 || codec_params.size() > 3) {
    return ParseFailed(line,
                       "Expected format \"<encoding name>/<clock rate>"
                       "[/<encodingparameters>]\".",
                       error);
  }
  const absl::string_view encoding_name = codec_params[0];
  int clock_rate = 0;
  if (!GetValueFromString(line, codec_params[1], &clock_rate, error)) {
    return false;
  }

  if (media_type == cricket::MEDIA_TYPE_VIDEO) {
    for (const cricket::VideoCodec& existing_codec : media_desc->codecs()) {
      if (!existing_codec.name.empty() && payload_type == existing_codec.id &&
          (!absl::EqualsIgnoreCase(encoding_name, existing_codec.name) ||
           clock_rate != existing_codec.clockrate)) {
        rtc::StringBuilder description;
        description
            << "Duplicate "
            << (payload_type < kFirstDynamicPayloadTypeLowerRange
                    ? "statically assigned"
                    : "")
            << " payload type with conflicting codec name or clock rate.";
        return ParseFailed(line, description.Release(), error);
      }
    }
    UpdateCodec(payload_type, encoding_name, media_desc);
  } else if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    // RFC 4566
    // For audio streams, <encoding parameters> indicates the number
    // of audio channels.  This parameter is OPTIONAL and may be
    // omitted if the number of channels is one, provided that no
    // additional parameters are needed.
    size_t channels = 1;
    if (codec_params.size() == 3) {
      if (!GetValueFromString(line, codec_params[2], &channels, error)) {
        return false;
      }
    }
    if (channels > kMaxNumberOfChannels) {
      return ParseFailed(line, "At most 24 channels are supported.", error);
    }

    for (const cricket::AudioCodec& existing_codec : media_desc->codecs()) {
      // TODO(crbug.com/1338902) re-add checks for clockrate and number of
      // channels.
      if (!existing_codec.name.empty() && payload_type == existing_codec.id &&
          (!absl::EqualsIgnoreCase(encoding_name, existing_codec.name))) {
        rtc::StringBuilder description;
        description
            << "Duplicate "
            << (payload_type < kFirstDynamicPayloadTypeLowerRange
                    ? "statically assigned"
                    : "")
            << " payload type with conflicting codec name or clock rate.";
        return ParseFailed(line, description.Release(), error);
      }
    }
    UpdateCodec(payload_type, encoding_name, clock_rate, 0, channels,
                media_desc);
  }
  return true;
}

bool ParseFmtpParam(absl::string_view line,
                    std::string* parameter,
                    std::string* value,
                    SdpParseError* error) {
  if (!rtc::tokenize_first(line, kSdpDelimiterEqualChar, parameter, value)) {
    // Support for non-key-value lines like RFC 2198 or RFC 4733.
    *parameter = "";
    *value = std::string(line);
    return true;
  }
  // a=fmtp:<payload_type> <param1>=<value1>; <param2>=<value2>; ...
  return true;
}

bool ParseFmtpAttributes(absl::string_view line,
                         const cricket::MediaType media_type,
                         MediaContentDescription* media_desc,
                         SdpParseError* error) {
  if (media_type != cricket::MEDIA_TYPE_AUDIO &&
      media_type != cricket::MEDIA_TYPE_VIDEO) {
    return true;
  }

  std::string line_payload;
  std::string line_params;

  // https://tools.ietf.org/html/rfc4566#section-6
  // a=fmtp:<format> <format specific parameters>
  // At least two fields, whereas the second one is any of the optional
  // parameters.
  if (!rtc::tokenize_first(line.substr(kLinePrefixLength),
                           kSdpDelimiterSpaceChar, &line_payload,
                           &line_params)) {
    ParseFailedExpectMinFieldNum(line, 2, error);
    return false;
  }

  // Parse out the payload information.
  std::string payload_type_str;
  if (!GetValue(line_payload, kAttributeFmtp, &payload_type_str, error)) {
    return false;
  }

  int payload_type = 0;
  if (!GetPayloadTypeFromString(line_payload, payload_type_str, &payload_type,
                                error)) {
    return false;
  }

  // Parse out format specific parameters.
  webrtc::CodecParameterMap codec_params;
  for (absl::string_view param :
       rtc::split(line_params, kSdpDelimiterSemicolonChar)) {
    std::string name;
    std::string value;
    if (!ParseFmtpParam(absl::StripAsciiWhitespace(param), &name, &value,
                        error)) {
      return false;
    }
    if (codec_params.find(name) != codec_params.end()) {
      RTC_LOG(LS_INFO) << "Overwriting duplicate fmtp parameter with key \""
                       << name << "\".";
    }
    codec_params[name] = value;
  }

  if (media_type == cricket::MEDIA_TYPE_AUDIO ||
      media_type == cricket::MEDIA_TYPE_VIDEO) {
    UpdateCodec(media_desc, payload_type, codec_params);
  }
  return true;
}

bool ParsePacketizationAttribute(absl::string_view line,
                                 const cricket::MediaType media_type,
                                 MediaContentDescription* media_desc,
                                 SdpParseError* error) {
  if (media_type != cricket::MEDIA_TYPE_VIDEO) {
    return true;
  }
  std::vector<absl::string_view> packetization_fields =
      rtc::split(line, kSdpDelimiterSpaceChar);
  if (packetization_fields.size() < 2) {
    return ParseFailedGetValue(line, kAttributePacketization, error);
  }
  std::string payload_type_string;
  if (!GetValue(packetization_fields[0], kAttributePacketization,
                &payload_type_string, error)) {
    return false;
  }
  int payload_type;
  if (!GetPayloadTypeFromString(line, payload_type_string, &payload_type,
                                error)) {
    return false;
  }
  absl::string_view packetization = packetization_fields[1];
  UpdateVideoCodecPacketization(media_desc, payload_type, packetization);
  return true;
}

bool ParseRtcpFbAttribute(absl::string_view line,
                          const cricket::MediaType media_type,
                          MediaContentDescription* media_desc,
                          SdpParseError* error) {
  if (media_type != cricket::MEDIA_TYPE_AUDIO &&
      media_type != cricket::MEDIA_TYPE_VIDEO) {
    return true;
  }
  std::vector<absl::string_view> rtcp_fb_fields =
      rtc::split(line, kSdpDelimiterSpaceChar);
  if (rtcp_fb_fields.size() < 2) {
    return ParseFailedGetValue(line, kAttributeRtcpFb, error);
  }
  std::string payload_type_string;
  if (!GetValue(rtcp_fb_fields[0], kAttributeRtcpFb, &payload_type_string,
                error)) {
    return false;
  }
  int payload_type = kWildcardPayloadType;
  if (payload_type_string != "*") {
    if (!GetPayloadTypeFromString(line, payload_type_string, &payload_type,
                                  error)) {
      return false;
    }
  }
  absl::string_view id = rtcp_fb_fields[1];
  std::string param = "";
  for (auto iter = rtcp_fb_fields.begin() + 2; iter != rtcp_fb_fields.end();
       ++iter) {
    param.append(iter->data(), iter->length());
  }
  const cricket::FeedbackParam feedback_param(id, param);

  if (media_type == cricket::MEDIA_TYPE_AUDIO ||
      media_type == cricket::MEDIA_TYPE_VIDEO) {
    UpdateCodec(media_desc, payload_type, feedback_param);
  }
  return true;
}

}  // namespace webrtc
