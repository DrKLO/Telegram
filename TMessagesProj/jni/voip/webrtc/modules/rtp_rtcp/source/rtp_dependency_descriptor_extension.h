/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_RTP_RTCP_SOURCE_RTP_DEPENDENCY_DESCRIPTOR_EXTENSION_H_
#define MODULES_RTP_RTCP_SOURCE_RTP_DEPENDENCY_DESCRIPTOR_EXTENSION_H_

#include <bitset>
#include <cstdint>

#include "absl/strings/string_view.h"
#include "api/array_view.h"
#include "api/rtp_parameters.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"

namespace webrtc {
// Trait to read/write the dependency descriptor extension as described in
// https://aomediacodec.github.io/av1-rtp-spec/#dependency-descriptor-rtp-header-extension
// While the format is still in design, the code might change without backward
// compatibility.
class RtpDependencyDescriptorExtension {
 public:
  static constexpr RTPExtensionType kId = kRtpExtensionGenericFrameDescriptor02;
  static constexpr absl::string_view Uri() {
    return RtpExtension::kDependencyDescriptorUri;
  }

  static bool Parse(rtc::ArrayView<const uint8_t> data,
                    const FrameDependencyStructure* structure,
                    DependencyDescriptor* descriptor);

  static size_t ValueSize(const FrameDependencyStructure& structure,
                          const DependencyDescriptor& descriptor) {
    return ValueSize(structure, kAllChainsAreActive, descriptor);
  }
  static size_t ValueSize(const FrameDependencyStructure& structure,
                          std::bitset<32> active_chains,
                          const DependencyDescriptor& descriptor);
  static bool Write(rtc::ArrayView<uint8_t> data,
                    const FrameDependencyStructure& structure,
                    const DependencyDescriptor& descriptor) {
    return Write(data, structure, kAllChainsAreActive, descriptor);
  }
  static bool Write(rtc::ArrayView<uint8_t> data,
                    const FrameDependencyStructure& structure,
                    std::bitset<32> active_chains,
                    const DependencyDescriptor& descriptor);

 private:
  static constexpr std::bitset<32> kAllChainsAreActive = ~uint32_t{0};
};

}  // namespace webrtc

#endif  // MODULES_RTP_RTCP_SOURCE_RTP_DEPENDENCY_DESCRIPTOR_EXTENSION_H_
