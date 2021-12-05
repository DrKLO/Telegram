/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_RTP_HEADERS_H_
#define API_RTP_HEADERS_H_

#include <stddef.h>
#include <stdint.h>

#include <string>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/units/timestamp.h"
#include "api/video/color_space.h"
#include "api/video/video_content_type.h"
#include "api/video/video_rotation.h"
#include "api/video/video_timing.h"

namespace webrtc {

struct FeedbackRequest {
  // Determines whether the recv delta as specified in
  // https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions-01
  // should be included.
  bool include_timestamps;
  // Include feedback of received packets in the range [sequence_number -
  // sequence_count + 1, sequence_number]. That is, no feedback will be sent if
  // sequence_count is zero.
  int sequence_count;
};

// The Absolute Capture Time extension is used to stamp RTP packets with a NTP
// timestamp showing when the first audio or video frame in a packet was
// originally captured. The intent of this extension is to provide a way to
// accomplish audio-to-video synchronization when RTCP-terminating intermediate
// systems (e.g. mixers) are involved. See:
// http://www.webrtc.org/experiments/rtp-hdrext/abs-capture-time
struct AbsoluteCaptureTime {
  // Absolute capture timestamp is the NTP timestamp of when the first frame in
  // a packet was originally captured. This timestamp MUST be based on the same
  // clock as the clock used to generate NTP timestamps for RTCP sender reports
  // on the capture system.
  //
  // It’s not always possible to do an NTP clock readout at the exact moment of
  // when a media frame is captured. A capture system MAY postpone the readout
  // until a more convenient time. A capture system SHOULD have known delays
  // (e.g. from hardware buffers) subtracted from the readout to make the final
  // timestamp as close to the actual capture time as possible.
  //
  // This field is encoded as a 64-bit unsigned fixed-point number with the high
  // 32 bits for the timestamp in seconds and low 32 bits for the fractional
  // part. This is also known as the UQ32.32 format and is what the RTP
  // specification defines as the canonical format to represent NTP timestamps.
  uint64_t absolute_capture_timestamp;

  // Estimated capture clock offset is the sender’s estimate of the offset
  // between its own NTP clock and the capture system’s NTP clock. The sender is
  // here defined as the system that owns the NTP clock used to generate the NTP
  // timestamps for the RTCP sender reports on this stream. The sender system is
  // typically either the capture system or a mixer.
  //
  // This field is encoded as a 64-bit two’s complement signed fixed-point
  // number with the high 32 bits for the seconds and low 32 bits for the
  // fractional part. It’s intended to make it easy for a receiver, that knows
  // how to estimate the sender system’s NTP clock, to also estimate the capture
  // system’s NTP clock:
  //
  //   Capture NTP Clock = Sender NTP Clock + Capture Clock Offset
  absl::optional<int64_t> estimated_capture_clock_offset;
};

inline bool operator==(const AbsoluteCaptureTime& lhs,
                       const AbsoluteCaptureTime& rhs) {
  return (lhs.absolute_capture_timestamp == rhs.absolute_capture_timestamp) &&
         (lhs.estimated_capture_clock_offset ==
          rhs.estimated_capture_clock_offset);
}

inline bool operator!=(const AbsoluteCaptureTime& lhs,
                       const AbsoluteCaptureTime& rhs) {
  return !(lhs == rhs);
}

struct RTPHeaderExtension {
  RTPHeaderExtension();
  RTPHeaderExtension(const RTPHeaderExtension& other);
  RTPHeaderExtension& operator=(const RTPHeaderExtension& other);

  static constexpr int kAbsSendTimeFraction = 18;

  Timestamp GetAbsoluteSendTimestamp() const {
    RTC_DCHECK(hasAbsoluteSendTime);
    RTC_DCHECK(absoluteSendTime < (1ul << 24));
    return Timestamp::Micros((absoluteSendTime * 1000000ll) /
                             (1 << kAbsSendTimeFraction));
  }

  TimeDelta GetAbsoluteSendTimeDelta(uint32_t previous_sendtime) const {
    RTC_DCHECK(hasAbsoluteSendTime);
    RTC_DCHECK(absoluteSendTime < (1ul << 24));
    RTC_DCHECK(previous_sendtime < (1ul << 24));
    int32_t delta =
        static_cast<int32_t>((absoluteSendTime - previous_sendtime) << 8) >> 8;
    return TimeDelta::Micros((delta * 1000000ll) / (1 << kAbsSendTimeFraction));
  }

  bool hasTransmissionTimeOffset;
  int32_t transmissionTimeOffset;
  bool hasAbsoluteSendTime;
  uint32_t absoluteSendTime;
  absl::optional<AbsoluteCaptureTime> absolute_capture_time;
  bool hasTransportSequenceNumber;
  uint16_t transportSequenceNumber;
  absl::optional<FeedbackRequest> feedback_request;

  // Audio Level includes both level in dBov and voiced/unvoiced bit. See:
  // https://tools.ietf.org/html/rfc6464#section-3
  bool hasAudioLevel;
  bool voiceActivity;
  uint8_t audioLevel;

  // For Coordination of Video Orientation. See
  // http://www.etsi.org/deliver/etsi_ts/126100_126199/126114/12.07.00_60/
  // ts_126114v120700p.pdf
  bool hasVideoRotation;
  VideoRotation videoRotation;

  // TODO(ilnik): Refactor this and one above to be absl::optional() and remove
  // a corresponding bool flag.
  bool hasVideoContentType;
  VideoContentType videoContentType;

  bool has_video_timing;
  VideoSendTiming video_timing;

  VideoPlayoutDelay playout_delay;

  // For identification of a stream when ssrc is not signaled. See
  // https://tools.ietf.org/html/rfc8852
  std::string stream_id;
  std::string repaired_stream_id;

  // For identifying the media section used to interpret this RTP packet. See
  // https://tools.ietf.org/html/rfc8843
  std::string mid;

  absl::optional<ColorSpace> color_space;
};

enum { kRtpCsrcSize = 15 };  // RFC 3550 page 13

struct RTPHeader {
  RTPHeader();
  RTPHeader(const RTPHeader& other);
  RTPHeader& operator=(const RTPHeader& other);

  bool markerBit;
  uint8_t payloadType;
  uint16_t sequenceNumber;
  uint32_t timestamp;
  uint32_t ssrc;
  uint8_t numCSRCs;
  uint32_t arrOfCSRCs[kRtpCsrcSize];
  size_t paddingLength;
  size_t headerLength;
  int payload_type_frequency;
  RTPHeaderExtension extension;
};

// RTCP mode to use. Compound mode is described by RFC 4585 and reduced-size
// RTCP mode is described by RFC 5506.
enum class RtcpMode { kOff, kCompound, kReducedSize };

enum NetworkState {
  kNetworkUp,
  kNetworkDown,
};

}  // namespace webrtc

#endif  // API_RTP_HEADERS_H_
