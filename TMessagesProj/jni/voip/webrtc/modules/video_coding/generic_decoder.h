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

#include <cstdint>
#include <deque>
#include <string>
#include <utility>

#include "api/field_trials_view.h"
#include "api/sequence_checker.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/timing/timing.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class VCMReceiveCallback;

struct FrameInfo {
  FrameInfo() = default;
  FrameInfo(const FrameInfo&) = delete;
  FrameInfo& operator=(const FrameInfo&) = delete;
  FrameInfo(FrameInfo&&) = default;
  FrameInfo& operator=(FrameInfo&&) = default;

  uint32_t rtp_timestamp;
  // This is likely not optional, but some inputs seem to sometimes be negative.
  // TODO(bugs.webrtc.org/13756): See if this can be replaced with Timestamp
  // once all inputs to this field use Timestamp instead of an integer.
  absl::optional<Timestamp> render_time;
  absl::optional<Timestamp> decode_start;
  VideoRotation rotation;
  VideoContentType content_type;
  EncodedImage::Timing timing;
  int64_t ntp_time_ms;
  RtpPacketInfos packet_infos;
  // ColorSpace is not stored here, as it might be modified by decoders.
};

class VCMDecodedFrameCallback : public DecodedImageCallback {
 public:
  VCMDecodedFrameCallback(VCMTiming* timing,
                          Clock* clock,
                          const FieldTrialsView& field_trials);
  ~VCMDecodedFrameCallback() override;
  void SetUserReceiveCallback(VCMReceiveCallback* receiveCallback);
  VCMReceiveCallback* UserReceiveCallback();

  int32_t Decoded(VideoFrame& decodedImage) override;
  int32_t Decoded(VideoFrame& decodedImage, int64_t decode_time_ms) override;
  void Decoded(VideoFrame& decodedImage,
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp) override;

  void OnDecoderInfoChanged(const VideoDecoder::DecoderInfo& decoder_info);

  void Map(FrameInfo frameInfo);
  void ClearTimestampMap();

 private:
  std::pair<absl::optional<FrameInfo>, size_t> FindFrameInfo(
      uint32_t rtp_timestamp) RTC_EXCLUSIVE_LOCKS_REQUIRED(lock_);

  SequenceChecker construction_thread_;
  Clock* const _clock;
  // This callback must be set before the decoder thread starts running
  // and must only be unset when external threads (e.g decoder thread)
  // have been stopped. Due to that, the variable should regarded as const
  // while there are more than one threads involved, it must be set
  // from the same thread, and therfore a lock is not required to access it.
  VCMReceiveCallback* _receiveCallback = nullptr;
  VCMTiming* _timing;
  Mutex lock_;
  std::deque<FrameInfo> frame_infos_ RTC_GUARDED_BY(lock_);
  int64_t ntp_offset_;
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
