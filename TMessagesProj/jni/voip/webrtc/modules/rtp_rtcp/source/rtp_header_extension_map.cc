/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"

#include "modules/rtp_rtcp/source/rtp_dependency_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_generic_frame_descriptor_extension.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_video_layers_allocation_extension.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace {

struct ExtensionInfo {
  RTPExtensionType type;
  const char* uri;
};

template <typename Extension>
constexpr ExtensionInfo CreateExtensionInfo() {
  return {Extension::kId, Extension::kUri};
}

constexpr ExtensionInfo kExtensions[] = {
    CreateExtensionInfo<TransmissionOffset>(),
    CreateExtensionInfo<AudioLevel>(),
    CreateExtensionInfo<AbsoluteSendTime>(),
    CreateExtensionInfo<AbsoluteCaptureTimeExtension>(),
    CreateExtensionInfo<VideoOrientation>(),
    CreateExtensionInfo<TransportSequenceNumber>(),
    CreateExtensionInfo<TransportSequenceNumberV2>(),
    CreateExtensionInfo<PlayoutDelayLimits>(),
    CreateExtensionInfo<VideoContentTypeExtension>(),
    CreateExtensionInfo<RtpVideoLayersAllocationExtension>(),
    CreateExtensionInfo<VideoTimingExtension>(),
    CreateExtensionInfo<RtpStreamId>(),
    CreateExtensionInfo<RepairedRtpStreamId>(),
    CreateExtensionInfo<RtpMid>(),
    CreateExtensionInfo<RtpGenericFrameDescriptorExtension00>(),
    CreateExtensionInfo<RtpDependencyDescriptorExtension>(),
    CreateExtensionInfo<ColorSpaceExtension>(),
    CreateExtensionInfo<InbandComfortNoiseExtension>(),
};

// Because of kRtpExtensionNone, NumberOfExtension is 1 bigger than the actual
// number of known extensions.
static_assert(arraysize(kExtensions) ==
                  static_cast<int>(kRtpExtensionNumberOfExtensions) - 1,
              "kExtensions expect to list all known extensions");

}  // namespace

constexpr RTPExtensionType RtpHeaderExtensionMap::kInvalidType;
constexpr int RtpHeaderExtensionMap::kInvalidId;

RtpHeaderExtensionMap::RtpHeaderExtensionMap() : RtpHeaderExtensionMap(false) {}

RtpHeaderExtensionMap::RtpHeaderExtensionMap(bool extmap_allow_mixed)
    : extmap_allow_mixed_(extmap_allow_mixed) {
  for (auto& id : ids_)
    id = kInvalidId;
}

RtpHeaderExtensionMap::RtpHeaderExtensionMap(
    rtc::ArrayView<const RtpExtension> extensions)
    : RtpHeaderExtensionMap(false) {
  for (const RtpExtension& extension : extensions)
    RegisterByUri(extension.id, extension.uri);
}

bool RtpHeaderExtensionMap::RegisterByType(int id, RTPExtensionType type) {
  for (const ExtensionInfo& extension : kExtensions)
    if (type == extension.type)
      return Register(id, extension.type, extension.uri);
  RTC_NOTREACHED();
  return false;
}

bool RtpHeaderExtensionMap::RegisterByUri(int id, absl::string_view uri) {
  for (const ExtensionInfo& extension : kExtensions)
    if (uri == extension.uri)
      return Register(id, extension.type, extension.uri);
  RTC_LOG(LS_WARNING) << "Unknown extension uri:'" << uri << "', id: " << id
                      << '.';
  return false;
}

RTPExtensionType RtpHeaderExtensionMap::GetType(int id) const {
  RTC_DCHECK_GE(id, RtpExtension::kMinId);
  RTC_DCHECK_LE(id, RtpExtension::kMaxId);
  for (int type = kRtpExtensionNone + 1; type < kRtpExtensionNumberOfExtensions;
       ++type) {
    if (ids_[type] == id) {
      return static_cast<RTPExtensionType>(type);
    }
  }
  return kInvalidType;
}

int32_t RtpHeaderExtensionMap::Deregister(RTPExtensionType type) {
  if (IsRegistered(type)) {
    ids_[type] = kInvalidId;
  }
  return 0;
}

void RtpHeaderExtensionMap::Deregister(absl::string_view uri) {
  for (const ExtensionInfo& extension : kExtensions) {
    if (extension.uri == uri) {
      ids_[extension.type] = kInvalidId;
      break;
    }
  }
}

bool RtpHeaderExtensionMap::Register(int id,
                                     RTPExtensionType type,
                                     const char* uri) {
  RTC_DCHECK_GT(type, kRtpExtensionNone);
  RTC_DCHECK_LT(type, kRtpExtensionNumberOfExtensions);

  if (id < RtpExtension::kMinId || id > RtpExtension::kMaxId) {
    RTC_LOG(LS_WARNING) << "Failed to register extension uri:'" << uri
                        << "' with invalid id:" << id << ".";
    return false;
  }

  RTPExtensionType registered_type = GetType(id);
  if (registered_type == type) {  // Same type/id pair already registered.
    RTC_LOG(LS_VERBOSE) << "Reregistering extension uri:'" << uri
                        << "', id:" << id;
    return true;
  }

  if (registered_type !=
      kInvalidType) {  // |id| used by another extension type.
    RTC_LOG(LS_WARNING) << "Failed to register extension uri:'" << uri
                        << "', id:" << id
                        << ". Id already in use by extension type "
                        << static_cast<int>(registered_type);
    return false;
  }
  RTC_DCHECK(!IsRegistered(type));

  // There is a run-time check above id fits into uint8_t.
  ids_[type] = static_cast<uint8_t>(id);
  return true;
}

}  // namespace webrtc
