/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "modules/video_coding/svc/scalability_structure_test_helpers.h"

#include <stdint.h>

#include <utility>
#include <vector>

#include "api/array_view.h"
#include "api/transport/rtp/dependency_descriptor.h"
#include "api/video/video_bitrate_allocation.h"
#include "modules/video_coding/chain_diff_calculator.h"
#include "modules/video_coding/frame_dependencies_calculator.h"
#include "modules/video_coding/svc/scalable_video_controller.h"
#include "test/gtest.h"

namespace webrtc {

VideoBitrateAllocation EnableTemporalLayers(int s0, int s1, int s2) {
  VideoBitrateAllocation bitrate;
  for (int tid = 0; tid < s0; ++tid) {
    bitrate.SetBitrate(0, tid, 1'000'000);
  }
  for (int tid = 0; tid < s1; ++tid) {
    bitrate.SetBitrate(1, tid, 1'000'000);
  }
  for (int tid = 0; tid < s2; ++tid) {
    bitrate.SetBitrate(2, tid, 1'000'000);
  }
  return bitrate;
}

void ScalabilityStructureWrapper::GenerateFrames(
    int num_temporal_units,
    std::vector<GenericFrameInfo>& frames) {
  for (int i = 0; i < num_temporal_units; ++i) {
    for (auto& layer_frame :
         structure_controller_.NextFrameConfig(/*restart=*/false)) {
      int64_t frame_id = ++frame_id_;
      bool is_keyframe = layer_frame.IsKeyframe();

      GenericFrameInfo frame_info =
          structure_controller_.OnEncodeDone(layer_frame);
      if (is_keyframe) {
        chain_diff_calculator_.Reset(frame_info.part_of_chain);
      }
      frame_info.chain_diffs =
          chain_diff_calculator_.From(frame_id, frame_info.part_of_chain);
      for (int64_t base_frame_id : frame_deps_calculator_.FromBuffersUsage(
               frame_id, frame_info.encoder_buffers)) {
        frame_info.frame_diffs.push_back(frame_id - base_frame_id);
      }

      frames.push_back(std::move(frame_info));
    }
  }
}

bool ScalabilityStructureWrapper::FrameReferencesAreValid(
    rtc::ArrayView<const GenericFrameInfo> frames) const {
  bool valid = true;
  // VP9 and AV1 supports up to 8 buffers. Expect no more buffers are not used.
  std::bitset<8> buffer_contains_frame;
  for (size_t i = 0; i < frames.size(); ++i) {
    const GenericFrameInfo& frame = frames[i];
    for (const CodecBufferUsage& buffer_usage : frame.encoder_buffers) {
      if (buffer_usage.id < 0 || buffer_usage.id >= 8) {
        ADD_FAILURE() << "Invalid buffer id " << buffer_usage.id
                      << " for frame#" << i
                      << ". Up to 8 buffers are supported.";
        valid = false;
        continue;
      }
      if (buffer_usage.referenced && !buffer_contains_frame[buffer_usage.id]) {
        ADD_FAILURE() << "buffer " << buffer_usage.id << " for frame#" << i
                      << " was reference before updated.";
        valid = false;
      }
      if (buffer_usage.updated) {
        buffer_contains_frame.set(buffer_usage.id);
      }
    }
    for (int fdiff : frame.frame_diffs) {
      if (fdiff <= 0 || static_cast<size_t>(fdiff) > i) {
        ADD_FAILURE() << "Invalid frame diff " << fdiff << " for frame#" << i;
        valid = false;
      }
    }
  }
  return valid;
}

}  // namespace webrtc
