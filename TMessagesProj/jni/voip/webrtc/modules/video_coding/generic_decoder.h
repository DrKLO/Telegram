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

#include <string>

#include "api/sequence_checker.h"
#include "api/units/time_delta.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/timestamp_map.h"
#include "modules/video_coding/timing.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class VCMReceiveCallback;

enum { kDecoderFrameMemoryLength = 10 };

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

  void Map(uint32_t timestamp, const VCMFrameInformation& frameInfo);
  void ClearTimestampMap();

 private:
  SequenceChecker construction_thread_;
  // Protect `_timestampMap`.
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

  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter `enabled`
  // determines if the low-latency renderer algorithm should be used for the
  // case min playout delay=0 and max playout delay>0.
  FieldTrialParameter<bool> low_latency_renderer_enabled_;
  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter
  // `include_predecode_buffer` determines if the predecode buffer should be
  // taken into account when calculating maximum number of frames in composition
  // queue.
  FieldTrialParameter<bool> low_latency_renderer_include_predecode_buffer_;
};

class VCMGenericDecoder {
 public:
  explicit VCMGenericDecoder(VideoDecoder* decoder);
  ~VCMGenericDecoder();

  /**
   * Initialize the decoder with the information from the `settings`
   */
  bool Configure(const VideoDecoder::Settings& settings);

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

  bool IsSameDecoder(VideoDecoder* decoder) const {
    return decoder_ == decoder;
  }

 private:
  VCMDecodedFrameCallback* _callback = nullptr;
  VideoDecoder* const decoder_;
  VideoContentType _last_keyframe_content_type;
  VideoDecoder::DecoderInfo decoder_info_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_GENERIC_DECODER_H_
