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
constexpr char RtpExtension::kCsrcAudioLevelsUri[];

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
  return
#if defined(ENABLE_EXTERNAL_AUTH)
      // TODO(jbauch): Figure out a way to always allow "kAbsSendTimeUri"
      // here and filter out later if external auth is really used in
      // srtpfilter. External auth is used by Chromium and replaces the
      // extension header value of "kAbsSendTimeUri", so it must not be
      // encrypted (which can't be done by Chromium).
      uri != webrtc::RtpExtension::kAbsSendTimeUri &&
#endif
      uri != webrtc::RtpExtension::kEncryptHeaderExtensionsUri;
}

// Returns whether a header extension with the given URI exists.
// Note: This does not differentiate between encrypted and non-encrypted
// extensions, so use with care!
static bool HeaderExtensionWithUriExists(
    const std::vector<RtpExtension>& extensions,
    absl::string_view uri) {
  for (const auto& extension : extensions) {
    if (extension.uri == uri) {
      return true;
    }
  }
  return false;
}

const RtpExtension* RtpExtension::FindHeaderExtensionByUri(
    const std::vector<RtpExtension>& extensions,
    absl::string_view uri,
    Filter filter) {
  const webrtc::RtpExtension* fallback_extension = nullptr;
  for (const auto& extension : extensions) {
    if (extension.uri != uri) {
      continue;
    }

    switch (filter) {
      case kDiscardEncryptedExtension:
        // We only accept an unencrypted extension.
        if (!extension.encrypt) {
          return &extension;
        }
        break;

      case kPreferEncryptedExtension:
        // We prefer an encrypted extension but we can fall back to an
        // unencrypted extension.
        if (extension.encrypt) {
          return &extension;
        } else {
          fallback_extension = &extension;
        }
        break;

      case kRequireEncryptedExtension:
        // We only accept an encrypted extension.
        if (extension.encrypt) {
          return &extension;
        }
        break;
    }
  }

  // Returning fallback extension (if any)
  return fallback_extension;
}

const RtpExtension* RtpExtension::FindHeaderExtensionByUriAndEncryption(
    const std::vector<RtpExtension>& extensions,
    absl::string_view uri,
    bool encrypt) {
  for (const auto& extension : extensions) {
    if (extension.uri == uri && extension.encrypt == encrypt) {
      return &extension;
    }
  }
  return nullptr;
}

const std::vector<RtpExtension> RtpExtension::DeduplicateHeaderExtensions(
    const std::vector<RtpExtension>& extensions,
    Filter filter) {
  std::vector<RtpExtension> filtered;

  // If we do not discard encrypted extensions, add them first
  if (filter != kDiscardEncryptedExtension) {
    for (const auto& extension : extensions) {
      if (!extension.encrypt) {
        continue;
      }
      if (!HeaderExtensionWithUriExists(filtered, extension.uri)) {
        filtered.push_back(extension);
      }
    }
  }

  // If we do not require encrypted extensions, add missing, non-encrypted
  // extensions.
  if (filter != kRequireEncryptedExtension) {
    for (const auto& extension : extensions) {
      if (extension.encrypt) {
        continue;
      }
      if (!HeaderExtensionWithUriExists(filtered, extension.uri)) {
        filtered.push_back(extension);
      }
    }
  }

  return filtered;
}
}  // namespace webrtc
