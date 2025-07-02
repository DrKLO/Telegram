/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/video/video_frame_metadata.h"

#include "api/video/video_frame.h"
#include "modules/video_coding/codecs/h264/include/h264_globals.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "test/gtest.h"
#include "video/video_receive_stream2.h"

namespace webrtc {
namespace {

RTPVideoHeaderH264 ExampleHeaderH264() {
  NaluInfo nalu_info;
  nalu_info.type = 1;
  nalu_info.sps_id = 2;
  nalu_info.pps_id = 3;

  RTPVideoHeaderH264 header;
  header.nalu_type = 4;
  header.packetization_type = H264PacketizationTypes::kH264StapA;
  header.nalus[0] = nalu_info;
  header.nalus_length = 1;
  header.packetization_mode = H264PacketizationMode::SingleNalUnit;
  return header;
}

RTPVideoHeaderVP9 ExampleHeaderVP9() {
  RTPVideoHeaderVP9 header;
  header.InitRTPVideoHeaderVP9();
  header.inter_pic_predicted = true;
  header.flexible_mode = true;
  header.beginning_of_frame = true;
  header.end_of_frame = true;
  header.ss_data_available = true;
  header.non_ref_for_inter_layer_pred = true;
  header.picture_id = 1;
  header.max_picture_id = 2;
  header.tl0_pic_idx = 3;
  header.temporal_idx = 4;
  header.spatial_idx = 5;
  header.temporal_up_switch = true;
  header.inter_layer_predicted = true;
  header.gof_idx = 6;
  header.num_ref_pics = 1;
  header.pid_diff[0] = 8;
  header.ref_picture_id[0] = 9;
  header.num_spatial_layers = 1;
  header.first_active_layer = 0;
  header.spatial_layer_resolution_present = true;
  header.width[0] = 12;
  header.height[0] = 13;
  header.end_of_picture = true;
  header.gof.SetGofInfoVP9(TemporalStructureMode::kTemporalStructureMode1);
  header.gof.pid_start = 14;
  return header;
}

TEST(VideoFrameMetadataTest, H264MetadataEquality) {
  RTPVideoHeaderH264 header = ExampleHeaderH264();

  VideoFrameMetadata metadata_lhs;
  metadata_lhs.SetRTPVideoHeaderCodecSpecifics(header);

  VideoFrameMetadata metadata_rhs;
  metadata_rhs.SetRTPVideoHeaderCodecSpecifics(header);

  EXPECT_TRUE(metadata_lhs == metadata_rhs);
  EXPECT_FALSE(metadata_lhs != metadata_rhs);
}

TEST(VideoFrameMetadataTest, H264MetadataInequality) {
  RTPVideoHeaderH264 header = ExampleHeaderH264();

  VideoFrameMetadata metadata_lhs;
  metadata_lhs.SetRTPVideoHeaderCodecSpecifics(header);

  VideoFrameMetadata metadata_rhs;
  header.nalus[0].type = 17;
  metadata_rhs.SetRTPVideoHeaderCodecSpecifics(header);

  EXPECT_FALSE(metadata_lhs == metadata_rhs);
  EXPECT_TRUE(metadata_lhs != metadata_rhs);
}

TEST(VideoFrameMetadataTest, VP9MetadataEquality) {
  RTPVideoHeaderVP9 header = ExampleHeaderVP9();

  VideoFrameMetadata metadata_lhs;
  metadata_lhs.SetRTPVideoHeaderCodecSpecifics(header);

  VideoFrameMetadata metadata_rhs;
  metadata_rhs.SetRTPVideoHeaderCodecSpecifics(header);

  EXPECT_TRUE(metadata_lhs == metadata_rhs);
  EXPECT_FALSE(metadata_lhs != metadata_rhs);
}

TEST(VideoFrameMetadataTest, VP9MetadataInequality) {
  RTPVideoHeaderVP9 header = ExampleHeaderVP9();

  VideoFrameMetadata metadata_lhs;
  metadata_lhs.SetRTPVideoHeaderCodecSpecifics(header);

  VideoFrameMetadata metadata_rhs;
  header.gof.pid_diff[0][0] = 42;
  metadata_rhs.SetRTPVideoHeaderCodecSpecifics(header);

  EXPECT_FALSE(metadata_lhs == metadata_rhs);
  EXPECT_TRUE(metadata_lhs != metadata_rhs);
}

}  // namespace
}  // namespace webrtc
