/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef MODULES_VIDEO_CODING_CODECS_AV1_SCALABLE_VIDEO_CONTROLLER_NO_LAYERING_H_
#define MODULES_VIDEO_CODING_CODECS_AV1_SCALABLE_VIDEO_CONTROLLER_NO_LAYERING_H_

#include <vector>

#include "api/transport/rtp/dependency_descriptor.h"
#include "common_video/generic_frame_descriptor/generic_frame_info.h"
#include "modules/video_coding/codecs/av1/scalable_video_controller.h"

namespace webrtc {

class ScalableVideoControllerNoLayering : public ScalableVideoController {
 public:
  ~ScalableVideoControllerNoLayering() override;

  StreamLayersConfig StreamConfig() const override;
  FrameDependencyStructure DependencyStructure() const override;

  std::vector<LayerFrameConfig> NextFrameConfig(bool restart) override;
  absl::optional<GenericFrameInfo> OnEncodeDone(
      LayerFrameConfig config) override;

 private:
  bool start_ = true;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_CODECS_AV1_SCALABLE_VIDEO_CONTROLLER_NO_LAYERING_H_
