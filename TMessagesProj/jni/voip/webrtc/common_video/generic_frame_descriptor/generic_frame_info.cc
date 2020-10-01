/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "common_video/generic_frame_descriptor/generic_frame_info.h"

#include <utility>

#include "rtc_base/checks.h"

namespace webrtc {

GenericFrameInfo::GenericFrameInfo() = default;
GenericFrameInfo::GenericFrameInfo(const GenericFrameInfo&) = default;
GenericFrameInfo::~GenericFrameInfo() = default;

GenericFrameInfo::Builder::Builder() = default;
GenericFrameInfo::Builder::~Builder() = default;

GenericFrameInfo GenericFrameInfo::Builder::Build() const {
  return info_;
}

GenericFrameInfo::Builder& GenericFrameInfo::Builder::T(int temporal_id) {
  info_.temporal_id = temporal_id;
  return *this;
}

GenericFrameInfo::Builder& GenericFrameInfo::Builder::S(int spatial_id) {
  info_.spatial_id = spatial_id;
  return *this;
}

GenericFrameInfo::Builder& GenericFrameInfo::Builder::Dtis(
    absl::string_view indication_symbols) {
  info_.decode_target_indications =
      webrtc_impl::StringToDecodeTargetIndications(indication_symbols);
  return *this;
}

}  // namespace webrtc
