/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains codec dependent definitions that are needed in
// order to compile the WebRTC codebase, even if this codec is not used.

#ifndef MODULES_VIDEO_CODING_CODECS_H265_INCLUDE_H265_GLOBALS_H_
#define MODULES_VIDEO_CODING_CODECS_H265_INCLUDE_H265_GLOBALS_H_

#ifndef DISABLE_H265

#include "modules/video_coding/codecs/h264/include/h264_globals.h"

namespace webrtc {

// The packetization types that we support: single, aggregated, and fragmented.
enum H265PacketizationTypes {
  kH265SingleNalu,  // This packet contains a single NAL unit.
  kH265AP,          // This packet contains aggregation Packet.
                    // If this packet has an associated NAL unit type,
                    // it'll be for the first such aggregated packet.
  kH265FU,          // This packet contains a FU (fragmentation
                    // unit) packet, meaning it is a part of a frame
                    // that was too large to fit into a single packet.
};

struct H265NaluInfo {
  uint8_t type;
  int vps_id;
  int sps_id;
  int pps_id;
};

enum class H265PacketizationMode {
  NonInterleaved = 0,  // Mode 1 - STAP-A, FU-A is allowed
  SingleNalUnit        // Mode 0 - only single NALU allowed
};

struct RTPVideoHeaderH265 {
  // The NAL unit type. If this is a header for a fragmented packet, it's the
  // NAL unit type of the original data. If this is the header for an aggregated
  // packet, it's the NAL unit type of the first NAL unit in the packet.
  uint8_t nalu_type;
  H265PacketizationTypes packetization_type;
  H265NaluInfo nalus[kMaxNalusPerPacket];
  size_t nalus_length;
  // The packetization type of this buffer - single, aggregated or fragmented.
  H265PacketizationMode packetization_mode;
};

}  // namespace webrtc

#endif

#endif  // MODULES_VIDEO_CODING_CODECS_H265_INCLUDE_H265_GLOBALS_H_
