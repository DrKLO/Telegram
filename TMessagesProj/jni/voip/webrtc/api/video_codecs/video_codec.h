/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_CODECS_VIDEO_CODEC_H_
#define API_VIDEO_CODECS_VIDEO_CODEC_H_

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video/video_codec_type.h"
#include "api/video_codecs/spatial_layer.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// The VideoCodec class represents an old defacto-apis, which we're migrating
// away from slowly.

// Video codec
enum class VideoCodecComplexity {
  kComplexityNormal = 0,
  kComplexityHigh = 1,
  kComplexityHigher = 2,
  kComplexityMax = 3
};

// VP8 specific
struct VideoCodecVP8 {
  bool operator==(const VideoCodecVP8& other) const;
  bool operator!=(const VideoCodecVP8& other) const {
    return !(*this == other);
  }
  VideoCodecComplexity complexity;
  unsigned char numberOfTemporalLayers;
  bool denoisingOn;
  bool automaticResizeOn;
  bool frameDroppingOn;
  int keyFrameInterval;
};

enum class InterLayerPredMode : int {
  kOff = 0,      // Inter-layer prediction is disabled.
  kOn = 1,       // Inter-layer prediction is enabled.
  kOnKeyPic = 2  // Inter-layer prediction is enabled but limited to key frames.
};

// VP9 specific.
struct VideoCodecVP9 {
  bool operator==(const VideoCodecVP9& other) const;
  bool operator!=(const VideoCodecVP9& other) const {
    return !(*this == other);
  }
  VideoCodecComplexity complexity;
  unsigned char numberOfTemporalLayers;
  bool denoisingOn;
  bool frameDroppingOn;
  int keyFrameInterval;
  bool adaptiveQpMode;
  bool automaticResizeOn;
  unsigned char numberOfSpatialLayers;
  bool flexibleMode;
  InterLayerPredMode interLayerPred;
};

// H264 specific.
struct VideoCodecH264 {
  bool operator==(const VideoCodecH264& other) const;
  bool operator!=(const VideoCodecH264& other) const {
    return !(*this == other);
  }
  bool frameDroppingOn;
  int keyFrameInterval;
  uint8_t numberOfTemporalLayers;
};

#ifndef DISABLE_H265
struct VideoCodecH265 {
  bool operator==(const VideoCodecH265& other) const;
  bool operator!=(const VideoCodecH265& other) const {
    return !(*this == other);
  }
  bool frameDroppingOn;
  int keyFrameInterval;
  const uint8_t* vpsData;
  size_t vpsLen;
  const uint8_t* spsData;
  size_t spsLen;
  const uint8_t* ppsData;
  size_t ppsLen;
};
#endif

// Translates from name of codec to codec type and vice versa.
RTC_EXPORT const char* CodecTypeToPayloadString(VideoCodecType type);
RTC_EXPORT VideoCodecType PayloadStringToCodecType(const std::string& name);

union VideoCodecUnion {
  VideoCodecVP8 VP8;
  VideoCodecVP9 VP9;
  VideoCodecH264 H264;
#ifndef DISABLE_H265
  VideoCodecH265 H265;
#endif
};

enum class VideoCodecMode { kRealtimeVideo, kScreensharing };

// Common video codec properties
class RTC_EXPORT VideoCodec {
 public:
  VideoCodec();

  // Scalability mode as described in
  // https://www.w3.org/TR/webrtc-svc/#scalabilitymodes*
  // or value 'NONE' to indicate no scalability.
  absl::string_view ScalabilityMode() const { return scalability_mode_; }
  void SetScalabilityMode(absl::string_view scalability_mode) {
    scalability_mode_ = std::string(scalability_mode);
  }

  // Public variables. TODO(hta): Make them private with accessors.
  VideoCodecType codecType;

  // TODO(nisse): Change to int, for consistency.
  uint16_t width;
  uint16_t height;

  unsigned int startBitrate;  // kilobits/sec.
  unsigned int maxBitrate;    // kilobits/sec.
  unsigned int minBitrate;    // kilobits/sec.

  uint32_t maxFramerate;

  // This enables/disables encoding and sending when there aren't multiple
  // simulcast streams,by allocating 0 bitrate if inactive.
  bool active;

  unsigned int qpMax;
  unsigned char numberOfSimulcastStreams;
  SpatialLayer simulcastStream[kMaxSimulcastStreams];
  SpatialLayer spatialLayers[kMaxSpatialLayers];

  VideoCodecMode mode;
  bool expect_encode_from_texture;

  // The size of pool which is used to store video frame buffers inside decoder.
  // If value isn't present some codec-default value will be used.
  // If value is present and decoder doesn't have buffer pool the
  // value will be ignored.
  absl::optional<int> buffer_pool_size;

  // Timing frames configuration. There is delay of delay_ms between two
  // consequent timing frames, excluding outliers. Frame is always made a
  // timing frame if it's at least outlier_ratio in percent of "ideal" average
  // frame given bitrate and framerate, i.e. if it's bigger than
  // |outlier_ratio / 100.0 * bitrate_bps / fps| in bits. This way, timing
  // frames will not be sent too often usually. Yet large frames will always
  // have timing information for debug purposes because they are more likely to
  // cause extra delays.
  struct TimingFrameTriggerThresholds {
    int64_t delay_ms;
    uint16_t outlier_ratio_percent;
  } timing_frame_thresholds;

  // Legacy Google conference mode flag for simulcast screenshare
  bool legacy_conference_mode;

  bool operator==(const VideoCodec& other) const = delete;
  bool operator!=(const VideoCodec& other) const = delete;

  // Accessors for codec specific information.
  // There is a const version of each that returns a reference,
  // and a non-const version that returns a pointer, in order
  // to allow modification of the parameters.
  VideoCodecVP8* VP8();
  const VideoCodecVP8& VP8() const;
  VideoCodecVP9* VP9();
  const VideoCodecVP9& VP9() const;
  VideoCodecH264* H264();
  const VideoCodecH264& H264() const;
#ifndef DISABLE_H265
  VideoCodecH265* H265();
  const VideoCodecH265& H265() const;
#endif

 private:
  // TODO(hta): Consider replacing the union with a pointer type.
  // This will allow removing the VideoCodec* types from this file.
  VideoCodecUnion codec_specific_;
  std::string scalability_mode_;
};

}  // namespace webrtc
#endif  // API_VIDEO_CODECS_VIDEO_CODEC_H_
