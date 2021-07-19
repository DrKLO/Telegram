/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VIDEO_ENCODED_IMAGE_H_
#define API_VIDEO_ENCODED_IMAGE_H_

#include <stdint.h>

#include <map>
#include <utility>

#include "absl/types/optional.h"
#include "api/rtp_packet_infos.h"
#include "api/scoped_refptr.h"
#include "api/video/color_space.h"
#include "api/video/video_codec_constants.h"
#include "api/video/video_content_type.h"
#include "api/video/video_frame_type.h"
#include "api/video/video_rotation.h"
#include "api/video/video_timing.h"
#include "rtc_base/checks.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// Abstract interface for buffer storage. Intended to support buffers owned by
// external encoders with special release requirements, e.g, java encoders with
// releaseOutputBuffer.
class EncodedImageBufferInterface : public rtc::RefCountInterface {
 public:
  virtual const uint8_t* data() const = 0;
  // TODO(bugs.webrtc.org/9378): Make interface essentially read-only, delete
  // this non-const data method.
  virtual uint8_t* data() = 0;
  virtual size_t size() const = 0;
};

// Basic implementation of EncodedImageBufferInterface.
class RTC_EXPORT EncodedImageBuffer : public EncodedImageBufferInterface {
 public:
  static rtc::scoped_refptr<EncodedImageBuffer> Create() { return Create(0); }
  static rtc::scoped_refptr<EncodedImageBuffer> Create(size_t size);
  static rtc::scoped_refptr<EncodedImageBuffer> Create(const uint8_t* data,
                                                       size_t size);

  const uint8_t* data() const override;
  uint8_t* data() override;
  size_t size() const override;
  void Realloc(size_t t);

 protected:
  explicit EncodedImageBuffer(size_t size);
  EncodedImageBuffer(const uint8_t* data, size_t size);
  ~EncodedImageBuffer();

  size_t size_;
  uint8_t* buffer_;
};

// TODO(bug.webrtc.org/9378): This is a legacy api class, which is slowly being
// cleaned up. Direct use of its members is strongly discouraged.
class RTC_EXPORT EncodedImage {
 public:
  EncodedImage();
  EncodedImage(EncodedImage&&);
  EncodedImage(const EncodedImage&);

  ~EncodedImage();

  EncodedImage& operator=(EncodedImage&&);
  EncodedImage& operator=(const EncodedImage&);

  // TODO(nisse): Change style to timestamp(), set_timestamp(), for consistency
  // with the VideoFrame class.
  // Set frame timestamp (90kHz).
  void SetTimestamp(uint32_t timestamp) { timestamp_rtp_ = timestamp; }

  // Get frame timestamp (90kHz).
  uint32_t Timestamp() const { return timestamp_rtp_; }

  void SetEncodeTime(int64_t encode_start_ms, int64_t encode_finish_ms);

  int64_t NtpTimeMs() const { return ntp_time_ms_; }

  absl::optional<int> SpatialIndex() const { return spatial_index_; }
  void SetSpatialIndex(absl::optional<int> spatial_index) {
    RTC_DCHECK_GE(spatial_index.value_or(0), 0);
    RTC_DCHECK_LT(spatial_index.value_or(0), kMaxSpatialLayers);
    spatial_index_ = spatial_index;
  }

  // These methods can be used to set/get size of subframe with spatial index
  // |spatial_index| on encoded frames that consist of multiple spatial layers.
  absl::optional<size_t> SpatialLayerFrameSize(int spatial_index) const;
  void SetSpatialLayerFrameSize(int spatial_index, size_t size_bytes);

  const webrtc::ColorSpace* ColorSpace() const {
    return color_space_ ? &*color_space_ : nullptr;
  }
  void SetColorSpace(const absl::optional<webrtc::ColorSpace>& color_space) {
    color_space_ = color_space;
  }

  // These methods along with the private member video_frame_tracking_id_ are
  // meant for media quality testing purpose only.
  absl::optional<uint16_t> VideoFrameTrackingId() const {
    return video_frame_tracking_id_;
  }
  void SetVideoFrameTrackingId(absl::optional<uint16_t> tracking_id) {
    video_frame_tracking_id_ = tracking_id;
  }

  const RtpPacketInfos& PacketInfos() const { return packet_infos_; }
  void SetPacketInfos(RtpPacketInfos packet_infos) {
    packet_infos_ = std::move(packet_infos);
  }

  bool RetransmissionAllowed() const { return retransmission_allowed_; }
  void SetRetransmissionAllowed(bool retransmission_allowed) {
    retransmission_allowed_ = retransmission_allowed;
  }

  size_t size() const { return size_; }
  void set_size(size_t new_size) {
    // Allow set_size(0) even if we have no buffer.
    RTC_DCHECK_LE(new_size, new_size == 0 ? 0 : capacity());
    size_ = new_size;
  }

  void SetEncodedData(
      rtc::scoped_refptr<EncodedImageBufferInterface> encoded_data) {
    encoded_data_ = encoded_data;
    size_ = encoded_data->size();
  }

  void ClearEncodedData() {
    encoded_data_ = nullptr;
    size_ = 0;
  }

  rtc::scoped_refptr<EncodedImageBufferInterface> GetEncodedData() const {
    return encoded_data_;
  }

  const uint8_t* data() const {
    return encoded_data_ ? encoded_data_->data() : nullptr;
  }

  uint32_t _encodedWidth = 0;
  uint32_t _encodedHeight = 0;
  // NTP time of the capture time in local timebase in milliseconds.
  // TODO(minyue): make this member private.
  int64_t ntp_time_ms_ = 0;
  int64_t capture_time_ms_ = 0;
  VideoFrameType _frameType = VideoFrameType::kVideoFrameDelta;
  VideoRotation rotation_ = kVideoRotation_0;
  VideoContentType content_type_ = VideoContentType::UNSPECIFIED;
  int qp_ = -1;  // Quantizer value.

  // When an application indicates non-zero values here, it is taken as an
  // indication that all future frames will be constrained with those limits
  // until the application indicates a change again.
  VideoPlayoutDelay playout_delay_;

  struct Timing {
    uint8_t flags = VideoSendTiming::kInvalid;
    int64_t encode_start_ms = 0;
    int64_t encode_finish_ms = 0;
    int64_t packetization_finish_ms = 0;
    int64_t pacer_exit_ms = 0;
    int64_t network_timestamp_ms = 0;
    int64_t network2_timestamp_ms = 0;
    int64_t receive_start_ms = 0;
    int64_t receive_finish_ms = 0;
  } timing_;

 private:
  size_t capacity() const { return encoded_data_ ? encoded_data_->size() : 0; }

  rtc::scoped_refptr<EncodedImageBufferInterface> encoded_data_;
  size_t size_ = 0;  // Size of encoded frame data.
  uint32_t timestamp_rtp_ = 0;
  absl::optional<int> spatial_index_;
  std::map<int, size_t> spatial_layer_frame_size_bytes_;
  absl::optional<webrtc::ColorSpace> color_space_;
  // This field is meant for media quality testing purpose only. When enabled it
  // carries the webrtc::VideoFrame id field from the sender to the receiver.
  absl::optional<uint16_t> video_frame_tracking_id_;
  // Information about packets used to assemble this video frame. This is needed
  // by |SourceTracker| when the frame is delivered to the RTCRtpReceiver's
  // MediaStreamTrack, in order to implement getContributingSources(). See:
  // https://w3c.github.io/webrtc-pc/#dom-rtcrtpreceiver-getcontributingsources
  RtpPacketInfos packet_infos_;
  bool retransmission_allowed_ = true;
};

}  // namespace webrtc

#endif  // API_VIDEO_ENCODED_IMAGE_H_
