/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef COMMON_VIDEO_H264_PREFIX_PARSER_H_
#define COMMON_VIDEO_H264_PREFIX_PARSER_H_

#include "absl/types/optional.h"

namespace rtc {
class BitBuffer;
}

namespace webrtc {

// A class for parsing out SVC extension data from an H264 prefix NALU
class PrefixParser {
 public:
  // The parsed state of the SVC extension. Only some select values are stored.
  // Add more as they are actually needed.
  struct PrefixState {
    PrefixState();
    PrefixState(const PrefixState&);
    ~PrefixState();

    uint32_t idr_flag = 0;
    uint32_t priority_id = 0;
    uint32_t no_inter_layer_pred_flag = 1;
    uint32_t dependency_id = 0;
    uint32_t quality_id = 0;
    uint32_t temporal_id = 0;
    uint32_t use_ref_base_pic_flag = 0;
    uint32_t discardable_flag = 1;
    uint32_t output_flag = 1;
  };

  // Unpack RBSP and parse prefix state from the supplied buffer.
  static absl::optional<PrefixState> ParsePrefix(const uint8_t* data, size_t length);

 protected:
  // Parse the prefix NAL, up till the SVC extension part, for a bit buffer where RBSP
  // decoding has already been performed.
  static absl::optional<PrefixState> ParsePrefixUpToSvcExtension(rtc::BitBuffer* buffer);
};

}  // namespace webrtc
#endif  // COMMON_VIDEO_H264_PREFIX_PARSER_H_
