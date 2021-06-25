/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "api/rtp_parameters.h"

#include <algorithm>
#include <string>
#include <utility>

#include "api/array_view.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

const char* DegradationPreferenceToString(
    DegradationPreference degradation_preference) {
  switch (degradation_preference) {
    case DegradationPreference::DISABLED:
      return "disabled";
    case DegradationPreference::MAINTAIN_FRAMERATE:
      return "maintain-framerate";
    case DegradationPreference::MAINTAIN_RESOLUTION:
      return "maintain-resolution";
    case DegradationPreference::BALANCED:
      return "balanced";
  }
  RTC_CHECK_NOTREACHED();
}

const double kDefaultBitratePriority = 1.0;

RtcpFeedback::RtcpFeedback() = default;
RtcpFeedback::RtcpFeedback(RtcpFeedbackType type) : type(type) {}
RtcpFeedback::RtcpFeedback(RtcpFeedbackType type,
                           RtcpFeedbackMessageType message_type)
    : type(type), message_type(message_type) {}
RtcpFeedback::RtcpFeedback(const RtcpFeedback& rhs) = default;
RtcpFeedback::~RtcpFeedback() = default;

RtpCodecCapability::RtpCodecCapability() = default;
RtpCodecCapability::~RtpCodecCapability() = default;

RtpHeaderExtensionCapability::RtpHeaderExtensionCapability() = default;
RtpHeaderExtensionCapability::RtpHeaderExtensionCapability(
    absl::string_view uri)
    : uri(uri) {}
RtpHeaderExtensionCapability::RtpHeaderExtensionCapability(
    absl::string_view uri,
    int preferred_id)
    : uri(uri), preferred_id(preferred_id) {}
RtpHeaderExtensionCapability::RtpHeaderExtensionCapability(
    absl::string_view uri,
    int preferred_id,
    RtpTransceiverDirection direction)
    : uri(uri), preferred_id(preferred_id), direction(direction) {}
RtpHeaderExtensionCapability::~RtpHeaderExtensionCapability() = default;

RtpExtension::RtpExtension() = default;
RtpExtension::RtpExtension(absl::string_view uri, int id) : uri(uri), id(id) {}
RtpExtension::RtpExtension(absl::string_view uri, int id, bool encrypt)
    : uri(uri), id(id), encrypt(encrypt) {}
RtpExtension::~RtpExtension() = default;

RtpFecParameters::RtpFecParameters() = default;
RtpFecParameters::RtpFecParameters(FecMechanism mechanism)
    : mechanism(mechanism) {}
RtpFecParameters::RtpFecParameters(FecMechanism mechanism, uint32_t ssrc)
    : ssrc(ssrc), mechanism(mechanism) {}
RtpFecParameters::RtpFecParameters(const RtpFecParameters& rhs) = default;
RtpFecParameters::~RtpFecParameters() = default;

RtpRtxParameters::RtpRtxParameters() = default;
RtpRtxParameters::RtpRtxParameters(uint32_t ssrc) : ssrc(ssrc) {}
RtpRtxParameters::RtpRtxParameters(const RtpRtxParameters& rhs) = default;
RtpRtxParameters::~RtpRtxParameters() = default;

RtpEncodingParameters::RtpEncodingParameters() = default;
RtpEncodingParameters::RtpEncodingParameters(const RtpEncodingParameters& rhs) =
    default;
RtpEncodingParameters::~RtpEncodingParameters() = default;

RtpCodecParameters::RtpCodecParameters() = default;
RtpCodecParameters::RtpCodecParameters(const RtpCodecParameters& rhs) = default;
RtpCodecParameters::~RtpCodecParameters() = default;

RtpCapabilities::RtpCapabilities() = default;
RtpCapabilities::~RtpCapabilities() = default;

RtcpParameters::RtcpParameters() = default;
RtcpParameters::RtcpParameters(const RtcpParameters& rhs) = default;
RtcpParameters::~RtcpParameters() = default;

RtpParameters::RtpParameters() = default;
RtpParameters::RtpParameters(const RtpParameters& rhs) = default;
RtpParameters::~RtpParameters() = default;

std::string RtpExtension::ToString() const {
  char buf[256];
  rtc::SimpleStringBuilder sb(buf);
  sb << "{uri: " << uri;
  sb << ", id: " << id;
  if (encrypt) {
    sb << ", encrypt";
  }
  sb << '}';
  return sb.str();
}

constexpr char RtpExtension::kEncryptHeaderExtensionsUri[];
constexpr char RtpExtension::kAudioLevelUri[];
constexpr char RtpExtension::kTimestampOffsetUri[];
constexpr char RtpExtension::kAbsSendTimeUri[];
constexpr char RtpExtension::kAbsoluteCaptureTimeUri[];
constexpr char RtpExtension::kVideoRotationUri[];
constexpr char RtpExtension::kVideoContentTypeUri[];
constexpr char RtpExtension::kVideoTimingUri[];
constexpr char RtpExtension::kGenericFrameDescriptorUri00[];
constexpr char RtpExtension::kDependencyDescriptorUri[];
constexpr char RtpExtension::kVideoLayersAllocationUri[];
constexpr char RtpExtension::kTransportSequenceNumberUri[];
constexpr char RtpExtension::kTransportSequenceNumberV2Uri[];
constexpr char RtpExtension::kPlayoutDelayUri[];
constexpr char RtpExtension::kColorSpaceUri[];
constexpr char RtpExtension::kMidUri[];
constexpr char RtpExtension::kRidUri[];
constexpr char RtpExtension::kRepairedRidUri[];
constexpr char RtpExtension::kVideoFrameTrackingIdUri[];

constexpr int RtpExtension::kMinId;
constexpr int RtpExtension::kMaxId;
constexpr int RtpExtension::kMaxValueSize;
constexpr int RtpExtension::kOneByteHeaderExtensionMaxId;
constexpr int RtpExtension::kOneByteHeaderExtensionMaxValueSize;

bool RtpExtension::IsSupportedForAudio(absl::string_view uri) {
  return uri == webrtc::RtpExtension::kAudioLevelUri ||
         uri == webrtc::RtpExtension::kAbsSendTimeUri ||
         uri == webrtc::RtpExtension::kAbsoluteCaptureTimeUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberV2Uri ||
         uri == webrtc::RtpExtension::kMidUri ||
         uri == webrtc::RtpExtension::kRidUri ||
         uri == webrtc::RtpExtension::kRepairedRidUri;
}

bool RtpExtension::IsSupportedForVideo(absl::string_view uri) {
  return uri == webrtc::RtpExtension::kTimestampOffsetUri ||
         uri == webrtc::RtpExtension::kAbsSendTimeUri ||
         uri == webrtc::RtpExtension::kAbsoluteCaptureTimeUri ||
         uri == webrtc::RtpExtension::kVideoRotationUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberV2Uri ||
         uri == webrtc::RtpExtension::kPlayoutDelayUri ||
         uri == webrtc::RtpExtension::kVideoContentTypeUri ||
         uri == webrtc::RtpExtension::kVideoTimingUri ||
         uri == webrtc::RtpExtension::kMidUri ||
         uri == webrtc::RtpExtension::kGenericFrameDescriptorUri00 ||
         uri == webrtc::RtpExtension::kDependencyDescriptorUri ||
         uri == webrtc::RtpExtension::kColorSpaceUri ||
         uri == webrtc::RtpExtension::kRidUri ||
         uri == webrtc::RtpExtension::kRepairedRidUri ||
         uri == webrtc::RtpExtension::kVideoLayersAllocationUri ||
         uri == webrtc::RtpExtension::kVideoFrameTrackingIdUri;
}

bool RtpExtension::IsEncryptionSupported(absl::string_view uri) {
  return uri == webrtc::RtpExtension::kAudioLevelUri ||
         uri == webrtc::RtpExtension::kTimestampOffsetUri ||
#if !defined(ENABLE_EXTERNAL_AUTH)
         // TODO(jbauch): Figure out a way to always allow "kAbsSendTimeUri"
         // here and filter out later if external auth is really used in
         // srtpfilter. External auth is used by Chromium and replaces the
         // extension header value of "kAbsSendTimeUri", so it must not be
         // encrypted (which can't be done by Chromium).
         uri == webrtc::RtpExtension::kAbsSendTimeUri ||
#endif
         uri == webrtc::RtpExtension::kAbsoluteCaptureTimeUri ||
         uri == webrtc::RtpExtension::kVideoRotationUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberUri ||
         uri == webrtc::RtpExtension::kTransportSequenceNumberV2Uri ||
         uri == webrtc::RtpExtension::kPlayoutDelayUri ||
         uri == webrtc::RtpExtension::kVideoContentTypeUri ||
         uri == webrtc::RtpExtension::kMidUri ||
         uri == webrtc::RtpExtension::kRidUri ||
         uri == webrtc::RtpExtension::kRepairedRidUri ||
         uri == webrtc::RtpExtension::kVideoLayersAllocationUri;
}

const RtpExtension* RtpExtension::FindHeaderExtensionByUri(
    const std::vector<RtpExtension>& extensions,
    absl::string_view uri) {
  for (const auto& extension : extensions) {
    if (extension.uri == uri) {
      return &extension;
    }
  }
  return nullptr;
}

std::vector<RtpExtension> RtpExtension::FilterDuplicateNonEncrypted(
    const std::vector<RtpExtension>& extensions) {
  std::vector<RtpExtension> filtered;
  for (auto extension = extensions.begin(); extension != extensions.end();
       ++extension) {
    if (extension->encrypt) {
      filtered.push_back(*extension);
      continue;
    }

    // Only add non-encrypted extension if no encrypted with the same URI
    // is also present...
    if (std::any_of(extension + 1, extensions.end(),
                    [&](const RtpExtension& check) {
                      return extension->uri == check.uri;
                    })) {
      continue;
    }

    // ...and has not been added before.
    if (!FindHeaderExtensionByUri(filtered, extension->uri)) {
      filtered.push_back(*extension);
    }
  }
  return filtered;
}
}  // namespace webrtc
