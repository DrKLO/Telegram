/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_H265_VPS_SPS_PPS_TRACKER_H_
#define MODULES_VIDEO_CODING_H265_VPS_SPS_PPS_TRACKER_H_

#include <cstddef>
#include <cstdint>
#include <map>
#include <memory>
#include <vector>

#include "api/array_view.h"
#include "modules/rtp_rtcp/source/rtp_video_header.h"
#include "rtc_base/copy_on_write_buffer.h"

namespace webrtc {
namespace video_coding {

class H265VpsSpsPpsTracker {
 public:
  enum PacketAction { kInsert, kDrop, kRequestKeyframe };
  struct FixedBitstream {
    PacketAction action;
    rtc::CopyOnWriteBuffer bitstream;
  };

  FixedBitstream CopyAndFixBitstream(rtc::ArrayView<const uint8_t> bitstream,
                                   RTPVideoHeader* video_header);

  void InsertVpsSpsPpsNalus(const std::vector<uint8_t>& vps,
                            const std::vector<uint8_t>& sps,
                            const std::vector<uint8_t>& pps);

 private:
  struct VpsInfo {
    size_t size = 0;
    std::unique_ptr<uint8_t[]> data;
  };

  struct PpsInfo {
    int sps_id = -1;
    size_t size = 0;
    std::unique_ptr<uint8_t[]> data;
  };

  struct SpsInfo {
    int vps_id = -1;
    size_t size = 0;
    int width = -1;
    int height = -1;
    std::unique_ptr<uint8_t[]> data;
  };

  std::map<uint32_t, VpsInfo> vps_data_;
  std::map<uint32_t, PpsInfo> pps_data_;
  std::map<uint32_t, SpsInfo> sps_data_;
};

}  // namespace video_coding
}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_H265_SPS_PPS_TRACKER_H_
