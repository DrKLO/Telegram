/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_GENERIC_DECODER_H_
#define MODULES_VIDEO_CODING_GENERIC_DECODER_H_

#include <memory>
#include <string>

#include "api/units/time_delta.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/timestamp_map.h"
#include "modules/video_coding/timing.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {

class VCMReceiveCallback;

enum { kDecoderFrameMemoryLength = 10 };

struct VCMFrameInformation {
  int64_t renderTimeMs;
  absl::optional<Timestamp> decodeStart;
  void* userData;
  VideoRotation rotation;
  VideoContentType content_type;
  PlayoutDelay playout_delay;
  EncodedImage::Timing timing;
  int64_t ntp_time_ms;
  RtpPacketInfos packet_infos;
  // ColorSpace is not stored here, as it might be modified by decoders.
};

class VCMDecodedFrameCallback : public DecodedImageCallback {
 public:
  VCMDecodedFrameCallback(VCMTiming* timing, Clock* clock);
  ~VCMDecodedFrameCallback() override;
  void SetUserReceiveCallback(VCMReceiveCallback* receiveCallback);
  VCMReceiveCallback* UserReceiveCallback();

  int32_t Decoded(VideoFrame& decodedImage) override;
  int32_t Decoded(VideoFrame& decodedImage, int64_t decode_time_ms) override;
  void Decoded(VideoFrame& decodedImage,
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp) override;

  void OnDecoderImplementationName(const char* implementation_name);

  void Map(uint32_t timestamp, VCMFrameInformation* frameInfo);
  int32_t Pop(uint32_t timestamp);

 private:
  rtc::ThreadChecker construction_thread_;
  // Protect |_timestampMap|.
  Clock* const _clock;
  // This callback must be set before the decoder thread starts running
  // and must only be unset when external threads (e.g decoder thread)
  // have been stopped. Due to that, the variable should regarded as const
  // while there are more than one threads involved, it must be set
  // from the same thread, and therfore a lock is not required to access it.
  VCMReceiveCallback* _receiveCallback = nullptr;
  VCMTiming* _timing;
  Mutex lock_;
  VCMTimestampMap _timestampMap RTC_GUARDED_BY(lock_);
  int64_t ntp_offset_;
  // Set by the field trial WebRTC-SlowDownDecoder to simulate a slow decoder.
  FieldTrialOptional<TimeDelta> _extra_decode_time;

  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter |enabled|
  // determines if the low-latency renderer algorithm should be used for the
  // case min playout delay=0 and max playout delay>0.
  FieldTrialParameter<bool> low_latency_renderer_enabled_;
  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter
  // |include_predecode_buffer| determines if the predecode buffer should be
  // taken into account when calculating maximum number of frames in composition
  // queue.
  FieldTrialParameter<bool> low_latency_renderer_include_predecode_buffer_;
};

class VCMGenericDecoder {
 public:
  explicit VCMGenericDecoder(std::unique_ptr<VideoDecoder> decoder);
  explicit VCMGenericDecoder(VideoDecoder* decoder, bool isExternal = false);
  ~VCMGenericDecoder();

  /**
   * Initialize the decoder with the information from the VideoCodec
   */
  int32_t InitDecode(const VideoCodec* settings, int32_t numberOfCores);

  /**
   * Decode to a raw I420 frame,
   *
   * inputVideoBuffer reference to encoded video frame
   */
  int32_t Decode(const VCMEncodedFrame& inputFrame, Timestamp now);

  /**
   * Set decode callback. Deregistering while decoding is illegal.
   */
  int32_t RegisterDecodeCompleteCallback(VCMDecodedFrameCallback* callback);

  bool PrefersLateDecoding() const;
  bool IsSameDecoder(VideoDecoder* decoder) const {
    return decoder_.get() == decoder;
  }

 private:
  VCMDecodedFrameCallback* _callback;
  VCMFrameInformation _frameInfos[kDecoderFrameMemoryLength];
  uint32_t _nextFrameInfoIdx;
  std::unique_ptr<VideoDecoder> decoder_;
  VideoCodecType _codecType;
  const bool _isExternal;
  VideoContentType _last_keyframe_content_type;
  std::string implementation_name_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_GENERIC_DECODER_H_
