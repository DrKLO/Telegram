/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_NULL_WEBRTC_VIDEO_ENGINE_H_
#define MEDIA_ENGINE_NULL_WEBRTC_VIDEO_ENGINE_H_

#include <vector>

#include "media/base/media_channel.h"
#include "media/base/media_engine.h"

namespace webrtc {

class Call;

}  // namespace webrtc

namespace cricket {

// Video engine implementation that does nothing and can be used in
// CompositeMediaEngine.
class NullWebRtcVideoEngine : public VideoEngineInterface {
 public:
  std::vector<VideoCodec> send_codecs(bool) const override {
    return std::vector<VideoCodec>();
  }

  std::vector<VideoCodec> recv_codecs(bool) const override {
    return std::vector<VideoCodec>();
  }
  std::vector<VideoCodec> send_codecs() const override {
    return std::vector<VideoCodec>();
  }

  std::vector<VideoCodec> recv_codecs() const override {
    return std::vector<VideoCodec>();
  }

  std::vector<webrtc::RtpHeaderExtensionCapability> GetRtpHeaderExtensions()
      const override {
    return {};
  }
};

}  // namespace cricket

#endif  // MEDIA_ENGINE_NULL_WEBRTC_VIDEO_ENGINE_H_
