/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_header_extensions.h"

#include <string.h>

#include <cmath>
#include <cstdint>
#include <limits>

#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/source/byte_io.h"
// TODO(bug:9855) Move kNoSpatialIdx from vp9_globals.h to common_constants
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "modules/video_coding/codecs/vp9/include/vp9_globals.h"
#include "rtc_base/checks.h"

namespace webrtc {
// Absolute send time in RTP streams.
//
// The absolute send time is signaled to the receiver in-band using the
// general mechanism for RTP header extensions [RFC8285]. The payload
// of this extension (the transmitted value) is a 24-bit unsigned integer
// containing the sender's current time in seconds as a fixed point number
// with 18 bits fractional part.
//
// The form of the absolute send time extension block:
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=2 |              absolute send time               |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType AbsoluteSendTime::kId;
constexpr uint8_t AbsoluteSendTime::kValueSizeBytes;

bool AbsoluteSendTime::Parse(rtc::ArrayView<const uint8_t> data,
                             uint32_t* time_24bits) {
  if (data.size() != 3)
    return false;
  *time_24bits = ByteReader<uint32_t, 3>::ReadBigEndian(data.data());
  return true;
}

bool AbsoluteSendTime::Write(rtc::ArrayView<uint8_t> data,
                             uint32_t time_24bits) {
  RTC_DCHECK_EQ(data.size(), 3);
  RTC_DCHECK_LE(time_24bits, 0x00FFFFFF);
  ByteWriter<uint32_t, 3>::WriteBigEndian(data.data(), time_24bits);
  return true;
}

// Absolute Capture Time
//
// The Absolute Capture Time extension is used to stamp RTP packets with a NTP
// timestamp showing when the first audio or video frame in a packet was
// originally captured. The intent of this extension is to provide a way to
// accomplish audio-to-video synchronization when RTCP-terminating intermediate
// systems (e.g. mixers) are involved.
//
// Data layout of the shortened version of abs-capture-time:
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=7 |     absolute capture timestamp (bit 0-23)     |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |             absolute capture timestamp (bit 24-55)            |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ... (56-63)  |
//   +-+-+-+-+-+-+-+-+
//
// Data layout of the extended version of abs-capture-time:
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=15|     absolute capture timestamp (bit 0-23)     |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |             absolute capture timestamp (bit 24-55)            |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ... (56-63)  |   estimated capture clock offset (bit 0-23)   |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |           estimated capture clock offset (bit 24-55)          |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ... (56-63)  |
//   +-+-+-+-+-+-+-+-+
constexpr RTPExtensionType AbsoluteCaptureTimeExtension::kId;
constexpr uint8_t AbsoluteCaptureTimeExtension::kValueSizeBytes;
constexpr uint8_t AbsoluteCaptureTimeExtension::
    kValueSizeBytesWithoutEstimatedCaptureClockOffset;

bool AbsoluteCaptureTimeExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                         AbsoluteCaptureTime* extension) {
  if (data.size() != kValueSizeBytes &&
      data.size() != kValueSizeBytesWithoutEstimatedCaptureClockOffset) {
    return false;
  }

  extension->absolute_capture_timestamp =
      ByteReader<uint64_t>::ReadBigEndian(data.data());

  if (data.size() != kValueSizeBytesWithoutEstimatedCaptureClockOffset) {
    extension->estimated_capture_clock_offset =
        ByteReader<int64_t>::ReadBigEndian(data.data() + 8);
  }

  return true;
}

size_t AbsoluteCaptureTimeExtension::ValueSize(
    const AbsoluteCaptureTime& extension) {
  if (extension.estimated_capture_clock_offset != absl::nullopt) {
    return kValueSizeBytes;
  } else {
    return kValueSizeBytesWithoutEstimatedCaptureClockOffset;
  }
}

bool AbsoluteCaptureTimeExtension::Write(rtc::ArrayView<uint8_t> data,
                                         const AbsoluteCaptureTime& extension) {
  RTC_DCHECK_EQ(data.size(), ValueSize(extension));

  ByteWriter<uint64_t>::WriteBigEndian(data.data(),
                                       extension.absolute_capture_timestamp);

  if (data.size() != kValueSizeBytesWithoutEstimatedCaptureClockOffset) {
    ByteWriter<int64_t>::WriteBigEndian(
        data.data() + 8, extension.estimated_capture_clock_offset.value());
  }

  return true;
}

// An RTP Header Extension for Client-to-Mixer Audio Level Indication
//
// https://tools.ietf.org/html/rfc6464
//
// The form of the audio level extension block:
//
//  0                   1
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |  ID   | len=0 |V| level       |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the One-Byte Header Format
//
//  0                   1                   2
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |      ID       |     len=1     |V|    level    |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the Two-Byte Header Format

constexpr RTPExtensionType AudioLevel::kId;
constexpr uint8_t AudioLevel::kValueSizeBytes;

bool AudioLevel::Parse(rtc::ArrayView<const uint8_t> data,
                       bool* voice_activity,
                       uint8_t* audio_level) {
  // One-byte and two-byte format share the same data definition.
  if (data.size() != 1)
    return false;
  *voice_activity = (data[0] & 0x80) != 0;
  *audio_level = data[0] & 0x7F;
  return true;
}

bool AudioLevel::Write(rtc::ArrayView<uint8_t> data,
                       bool voice_activity,
                       uint8_t audio_level) {
  // One-byte and two-byte format share the same data definition.
  RTC_DCHECK_EQ(data.size(), 1);
  RTC_CHECK_LE(audio_level, 0x7f);
  data[0] = (voice_activity ? 0x80 : 0x00) | audio_level;
  return true;
}

// An RTP Header Extension for Mixer-to-Client Audio Level Indication
//
// https://tools.ietf.org/html/rfc6465
//
// The form of the audio level extension block:
//
//  0                   1                   2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |  ID   | len=2 |0|   level 1   |0|   level 2   |0|   level 3   |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the One-Byte Header Format
//
//  0                   1                   2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |      ID       |     len=3     |0|   level 1   |0|   level 2   |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |0|   level 3   |    0 (pad)    |               ...             |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the Two-Byte Header Format
constexpr RTPExtensionType CsrcAudioLevel::kId;
constexpr uint8_t CsrcAudioLevel::kMaxValueSizeBytes;

bool CsrcAudioLevel::Parse(rtc::ArrayView<const uint8_t> data,
                           std::vector<uint8_t>* csrc_audio_levels) {
  if (data.size() > kRtpCsrcSize) {
    return false;
  }
  csrc_audio_levels->resize(data.size());
  for (size_t i = 0; i < data.size(); i++) {
    (*csrc_audio_levels)[i] = data[i] & 0x7F;
  }
  return true;
}

size_t CsrcAudioLevel::ValueSize(
    rtc::ArrayView<const uint8_t> csrc_audio_levels) {
  return csrc_audio_levels.size();
}

bool CsrcAudioLevel::Write(rtc::ArrayView<uint8_t> data,
                           rtc::ArrayView<const uint8_t> csrc_audio_levels) {
  RTC_CHECK_LE(csrc_audio_levels.size(), kRtpCsrcSize);
  if (csrc_audio_levels.size() != data.size()) {
    return false;
  }
  for (size_t i = 0; i < csrc_audio_levels.size(); i++) {
    data[i] = csrc_audio_levels[i] & 0x7F;
  }
  return true;
}

// From RFC 5450: Transmission Time Offsets in RTP Streams.
//
// The transmission time is signaled to the receiver in-band using the
// general mechanism for RTP header extensions [RFC8285]. The payload
// of this extension (the transmitted value) is a 24-bit signed integer.
// When added to the RTP timestamp of the packet, it represents the
// "effective" RTP transmission time of the packet, on the RTP
// timescale.
//
// The form of the transmission offset extension block:
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=2 |              transmission offset              |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType TransmissionOffset::kId;
constexpr uint8_t TransmissionOffset::kValueSizeBytes;

bool TransmissionOffset::Parse(rtc::ArrayView<const uint8_t> data,
                               int32_t* rtp_time) {
  if (data.size() != 3)
    return false;
  *rtp_time = ByteReader<int32_t, 3>::ReadBigEndian(data.data());
  return true;
}

bool TransmissionOffset::Write(rtc::ArrayView<uint8_t> data, int32_t rtp_time) {
  RTC_DCHECK_EQ(data.size(), 3);
  RTC_DCHECK_LE(rtp_time, 0x00ffffff);
  ByteWriter<int32_t, 3>::WriteBigEndian(data.data(), rtp_time);
  return true;
}

// TransportSequenceNumber
//
//   0                   1                   2
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  ID   | L=1   |transport-wide sequence number |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType TransportSequenceNumber::kId;
constexpr uint8_t TransportSequenceNumber::kValueSizeBytes;

bool TransportSequenceNumber::Parse(rtc::ArrayView<const uint8_t> data,
                                    uint16_t* transport_sequence_number) {
  if (data.size() != kValueSizeBytes)
    return false;
  *transport_sequence_number = ByteReader<uint16_t>::ReadBigEndian(data.data());
  return true;
}

bool TransportSequenceNumber::Write(rtc::ArrayView<uint8_t> data,
                                    uint16_t transport_sequence_number) {
  RTC_DCHECK_EQ(data.size(), ValueSize(transport_sequence_number));
  ByteWriter<uint16_t>::WriteBigEndian(data.data(), transport_sequence_number);
  return true;
}

// TransportSequenceNumberV2
//
// In addition to the format used for TransportSequencNumber, V2 also supports
// the following packet format where two extra bytes are used to specify that
// the sender requests immediate feedback.
//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  ID   | L=3   |transport-wide sequence number |T|  seq count  |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |seq count cont.|
//  +-+-+-+-+-+-+-+-+
//
// The bit `T` determines whether the feedback should include timing information
// or not and `seq_count` determines how many packets the feedback packet should
// cover including the current packet. If `seq_count` is zero no feedback is
// requested.
constexpr RTPExtensionType TransportSequenceNumberV2::kId;
constexpr uint8_t TransportSequenceNumberV2::kValueSizeBytes;
constexpr uint8_t
    TransportSequenceNumberV2::kValueSizeBytesWithoutFeedbackRequest;
constexpr uint16_t TransportSequenceNumberV2::kIncludeTimestampsBit;

bool TransportSequenceNumberV2::Parse(
    rtc::ArrayView<const uint8_t> data,
    uint16_t* transport_sequence_number,
    absl::optional<FeedbackRequest>* feedback_request) {
  if (data.size() != kValueSizeBytes &&
      data.size() != kValueSizeBytesWithoutFeedbackRequest)
    return false;

  *transport_sequence_number = ByteReader<uint16_t>::ReadBigEndian(data.data());

  *feedback_request = absl::nullopt;
  if (data.size() == kValueSizeBytes) {
    uint16_t feedback_request_raw =
        ByteReader<uint16_t>::ReadBigEndian(data.data() + 2);
    bool include_timestamps =
        (feedback_request_raw & kIncludeTimestampsBit) != 0;
    uint16_t sequence_count = feedback_request_raw & ~kIncludeTimestampsBit;

    // If `sequence_count` is zero no feedback is requested.
    if (sequence_count != 0) {
      *feedback_request = {include_timestamps, sequence_count};
    }
  }
  return true;
}

bool TransportSequenceNumberV2::Write(
    rtc::ArrayView<uint8_t> data,
    uint16_t transport_sequence_number,
    const absl::optional<FeedbackRequest>& feedback_request) {
  RTC_DCHECK_EQ(data.size(),
                ValueSize(transport_sequence_number, feedback_request));

  ByteWriter<uint16_t>::WriteBigEndian(data.data(), transport_sequence_number);

  if (feedback_request) {
    RTC_DCHECK_GE(feedback_request->sequence_count, 0);
    RTC_DCHECK_LT(feedback_request->sequence_count, kIncludeTimestampsBit);
    uint16_t feedback_request_raw =
        feedback_request->sequence_count |
        (feedback_request->include_timestamps ? kIncludeTimestampsBit : 0);
    ByteWriter<uint16_t>::WriteBigEndian(data.data() + 2, feedback_request_raw);
  }
  return true;
}

// Coordination of Video Orientation in RTP streams.
//
// Coordination of Video Orientation consists in signaling of the current
// orientation of the image captured on the sender side to the receiver for
// appropriate rendering and displaying.
//
//    0                   1
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=0 |0 0 0 0 C F R R|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType VideoOrientation::kId;
constexpr uint8_t VideoOrientation::kValueSizeBytes;

bool VideoOrientation::Parse(rtc::ArrayView<const uint8_t> data,
                             VideoRotation* rotation) {
  if (data.size() != 1)
    return false;
  *rotation = ConvertCVOByteToVideoRotation(data[0]);
  return true;
}

bool VideoOrientation::Write(rtc::ArrayView<uint8_t> data,
                             VideoRotation rotation) {
  RTC_DCHECK_EQ(data.size(), 1);
  data[0] = ConvertVideoRotationToCVOByte(rotation);
  return true;
}

bool VideoOrientation::Parse(rtc::ArrayView<const uint8_t> data,
                             uint8_t* value) {
  if (data.size() != 1)
    return false;
  *value = data[0];
  return true;
}

bool VideoOrientation::Write(rtc::ArrayView<uint8_t> data, uint8_t value) {
  RTC_DCHECK_EQ(data.size(), 1);
  data[0] = value;
  return true;
}

//   0                   1                   2                   3
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  ID   | len=2 |   MIN delay           |   MAX delay           |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType PlayoutDelayLimits::kId;
constexpr uint8_t PlayoutDelayLimits::kValueSizeBytes;

bool PlayoutDelayLimits::Parse(rtc::ArrayView<const uint8_t> data,
                               VideoPlayoutDelay* playout_delay) {
  RTC_DCHECK(playout_delay);
  if (data.size() != 3)
    return false;
  uint32_t raw = ByteReader<uint32_t, 3>::ReadBigEndian(data.data());
  uint16_t min_raw = (raw >> 12);
  uint16_t max_raw = (raw & 0xfff);
  if (min_raw > max_raw)
    return false;
  playout_delay->min_ms = min_raw * kGranularityMs;
  playout_delay->max_ms = max_raw * kGranularityMs;
  return true;
}

bool PlayoutDelayLimits::Write(rtc::ArrayView<uint8_t> data,
                               const VideoPlayoutDelay& playout_delay) {
  RTC_DCHECK_EQ(data.size(), 3);
  RTC_DCHECK_LE(0, playout_delay.min_ms);
  RTC_DCHECK_LE(playout_delay.min_ms, playout_delay.max_ms);
  RTC_DCHECK_LE(playout_delay.max_ms, kMaxMs);
  // Convert MS to value to be sent on extension header.
  uint32_t min_delay = playout_delay.min_ms / kGranularityMs;
  uint32_t max_delay = playout_delay.max_ms / kGranularityMs;
  ByteWriter<uint32_t, 3>::WriteBigEndian(data.data(),
                                          (min_delay << 12) | max_delay);
  return true;
}

// Video Content Type.
//
// E.g. default video or screenshare.
//
//    0                   1
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=0 | Content type  |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
constexpr RTPExtensionType VideoContentTypeExtension::kId;
constexpr uint8_t VideoContentTypeExtension::kValueSizeBytes;

bool VideoContentTypeExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                      VideoContentType* content_type) {
  if (data.size() == 1 &&
      videocontenttypehelpers::IsValidContentType(data[0])) {
    *content_type = static_cast<VideoContentType>(data[0]);
    return true;
  }
  return false;
}

bool VideoContentTypeExtension::Write(rtc::ArrayView<uint8_t> data,
                                      VideoContentType content_type) {
  RTC_DCHECK_EQ(data.size(), 1);
  data[0] = static_cast<uint8_t>(content_type);
  return true;
}

// Video Timing.
// 6 timestamps in milliseconds counted from capture time stored in rtp header:
// encode start/finish, packetization complete, pacer exit and reserved for
// modification by the network modification. `flags` is a bitmask and has the
// following allowed values:
// 0 = Valid data, but no flags available (backwards compatibility)
// 1 = Frame marked as timing frame due to cyclic timer.
// 2 = Frame marked as timing frame due to size being outside limit.
// 255 = Invalid. The whole timing frame extension should be ignored.
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | len=12|     flags     |     encode start ms delta     |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |    encode finish ms delta     |  packetizer finish ms delta   |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |     pacer exit ms delta       |  network timestamp ms delta   |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  network2 timestamp ms delta  |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

constexpr RTPExtensionType VideoTimingExtension::kId;
constexpr uint8_t VideoTimingExtension::kValueSizeBytes;
constexpr uint8_t VideoTimingExtension::kFlagsOffset;
constexpr uint8_t VideoTimingExtension::kEncodeStartDeltaOffset;
constexpr uint8_t VideoTimingExtension::kEncodeFinishDeltaOffset;
constexpr uint8_t VideoTimingExtension::kPacketizationFinishDeltaOffset;
constexpr uint8_t VideoTimingExtension::kPacerExitDeltaOffset;
constexpr uint8_t VideoTimingExtension::kNetworkTimestampDeltaOffset;
constexpr uint8_t VideoTimingExtension::kNetwork2TimestampDeltaOffset;

bool VideoTimingExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                 VideoSendTiming* timing) {
  RTC_DCHECK(timing);
  // TODO(sprang): Deprecate support for old wire format.
  ptrdiff_t off = 0;
  switch (data.size()) {
    case kValueSizeBytes - 1:
      timing->flags = 0;
      off = 1;  // Old wire format without the flags field.
      break;
    case kValueSizeBytes:
      timing->flags = ByteReader<uint8_t>::ReadBigEndian(data.data());
      break;
    default:
      return false;
  }

  timing->encode_start_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kEncodeStartDeltaOffset - off);
  timing->encode_finish_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kEncodeFinishDeltaOffset - off);
  timing->packetization_finish_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kPacketizationFinishDeltaOffset - off);
  timing->pacer_exit_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kPacerExitDeltaOffset - off);
  timing->network_timestamp_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kNetworkTimestampDeltaOffset - off);
  timing->network2_timestamp_delta_ms = ByteReader<uint16_t>::ReadBigEndian(
      data.data() + kNetwork2TimestampDeltaOffset - off);
  return true;
}

bool VideoTimingExtension::Write(rtc::ArrayView<uint8_t> data,
                                 const VideoSendTiming& timing) {
  RTC_DCHECK_EQ(data.size(), 1 + 2 * 6);
  ByteWriter<uint8_t>::WriteBigEndian(data.data() + kFlagsOffset, timing.flags);
  ByteWriter<uint16_t>::WriteBigEndian(data.data() + kEncodeStartDeltaOffset,
                                       timing.encode_start_delta_ms);
  ByteWriter<uint16_t>::WriteBigEndian(data.data() + kEncodeFinishDeltaOffset,
                                       timing.encode_finish_delta_ms);
  ByteWriter<uint16_t>::WriteBigEndian(
      data.data() + kPacketizationFinishDeltaOffset,
      timing.packetization_finish_delta_ms);
  ByteWriter<uint16_t>::WriteBigEndian(data.data() + kPacerExitDeltaOffset,
                                       timing.pacer_exit_delta_ms);
  ByteWriter<uint16_t>::WriteBigEndian(
      data.data() + kNetworkTimestampDeltaOffset,
      timing.network_timestamp_delta_ms);
  ByteWriter<uint16_t>::WriteBigEndian(
      data.data() + kNetwork2TimestampDeltaOffset,
      timing.network2_timestamp_delta_ms);
  return true;
}

bool VideoTimingExtension::Write(rtc::ArrayView<uint8_t> data,
                                 uint16_t time_delta_ms,
                                 uint8_t offset) {
  RTC_DCHECK_GE(data.size(), offset + 2);
  RTC_DCHECK_LE(offset, kValueSizeBytes - sizeof(uint16_t));
  ByteWriter<uint16_t>::WriteBigEndian(data.data() + offset, time_delta_ms);
  return true;
}

// Color space including HDR metadata as an optional field.
//
// RTP header extension to carry color space information and optionally HDR
// metadata. The float values in the HDR metadata struct are upscaled by a
// static factor and transmitted as unsigned integers.
//
// Data layout of color space with HDR metadata (two-byte RTP header extension)
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |      ID       |   length=28   |   primaries   |   transfer    |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |    matrix     |range+chr.sit. |         luminance_max         |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |         luminance_min         |            mastering_metadata.|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |primary_r.x and .y             |            mastering_metadata.|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |primary_g.x and .y             |            mastering_metadata.|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |primary_b.x and .y             |            mastering_metadata.|
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |white.x and .y                 |    max_content_light_level    |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   | max_frame_average_light_level |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
// Data layout of color space w/o HDR metadata (one-byte RTP header extension)
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |  ID   | L = 3 |   primaries   |   transfer    |    matrix     |
//   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//   |range+chr.sit. |
//   +-+-+-+-+-+-+-+-+

constexpr RTPExtensionType ColorSpaceExtension::kId;
constexpr uint8_t ColorSpaceExtension::kValueSizeBytes;

bool ColorSpaceExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                ColorSpace* color_space) {
  RTC_DCHECK(color_space);
  if (data.size() != kValueSizeBytes &&
      data.size() != kValueSizeBytesWithoutHdrMetadata)
    return false;

  size_t offset = 0;
  // Read color space information.
  if (!color_space->set_primaries_from_uint8(data[offset++]))
    return false;
  if (!color_space->set_transfer_from_uint8(data[offset++]))
    return false;
  if (!color_space->set_matrix_from_uint8(data[offset++]))
    return false;

  uint8_t range_and_chroma_siting = data[offset++];
  if (!color_space->set_range_from_uint8((range_and_chroma_siting >> 4) & 0x03))
    return false;
  if (!color_space->set_chroma_siting_horizontal_from_uint8(
          (range_and_chroma_siting >> 2) & 0x03))
    return false;
  if (!color_space->set_chroma_siting_vertical_from_uint8(
          range_and_chroma_siting & 0x03))
    return false;

  // Read HDR metadata if it exists, otherwise clear it.
  if (data.size() == kValueSizeBytesWithoutHdrMetadata) {
    color_space->set_hdr_metadata(nullptr);
  } else {
    HdrMetadata hdr_metadata;
    offset += ParseHdrMetadata(data.subview(offset), &hdr_metadata);
    if (!hdr_metadata.Validate())
      return false;
    color_space->set_hdr_metadata(&hdr_metadata);
  }
  RTC_DCHECK_EQ(ValueSize(*color_space), offset);
  return true;
}

bool ColorSpaceExtension::Write(rtc::ArrayView<uint8_t> data,
                                const ColorSpace& color_space) {
  RTC_DCHECK_EQ(data.size(), ValueSize(color_space));
  size_t offset = 0;
  // Write color space information.
  data[offset++] = static_cast<uint8_t>(color_space.primaries());
  data[offset++] = static_cast<uint8_t>(color_space.transfer());
  data[offset++] = static_cast<uint8_t>(color_space.matrix());
  data[offset++] = CombineRangeAndChromaSiting(
      color_space.range(), color_space.chroma_siting_horizontal(),
      color_space.chroma_siting_vertical());

  // Write HDR metadata if it exists.
  if (color_space.hdr_metadata()) {
    offset +=
        WriteHdrMetadata(data.subview(offset), *color_space.hdr_metadata());
  }
  RTC_DCHECK_EQ(ValueSize(color_space), offset);
  return true;
}

// Combines range and chroma siting into one byte with the following bit layout:
// bits 0-1 Chroma siting vertical.
//      2-3 Chroma siting horizontal.
//      4-5 Range.
//      6-7 Unused.
uint8_t ColorSpaceExtension::CombineRangeAndChromaSiting(
    ColorSpace::RangeID range,
    ColorSpace::ChromaSiting chroma_siting_horizontal,
    ColorSpace::ChromaSiting chroma_siting_vertical) {
  RTC_DCHECK_LE(static_cast<uint8_t>(range), 3);
  RTC_DCHECK_LE(static_cast<uint8_t>(chroma_siting_horizontal), 3);
  RTC_DCHECK_LE(static_cast<uint8_t>(chroma_siting_vertical), 3);
  return (static_cast<uint8_t>(range) << 4) |
         (static_cast<uint8_t>(chroma_siting_horizontal) << 2) |
         static_cast<uint8_t>(chroma_siting_vertical);
}

size_t ColorSpaceExtension::ParseHdrMetadata(rtc::ArrayView<const uint8_t> data,
                                             HdrMetadata* hdr_metadata) {
  RTC_DCHECK_EQ(data.size(),
                kValueSizeBytes - kValueSizeBytesWithoutHdrMetadata);
  size_t offset = 0;
  offset += ParseLuminance(data.data() + offset,
                           &hdr_metadata->mastering_metadata.luminance_max,
                           kLuminanceMaxDenominator);
  offset += ParseLuminance(data.data() + offset,
                           &hdr_metadata->mastering_metadata.luminance_min,
                           kLuminanceMinDenominator);
  offset += ParseChromaticity(data.data() + offset,
                              &hdr_metadata->mastering_metadata.primary_r);
  offset += ParseChromaticity(data.data() + offset,
                              &hdr_metadata->mastering_metadata.primary_g);
  offset += ParseChromaticity(data.data() + offset,
                              &hdr_metadata->mastering_metadata.primary_b);
  offset += ParseChromaticity(data.data() + offset,
                              &hdr_metadata->mastering_metadata.white_point);
  hdr_metadata->max_content_light_level =
      ByteReader<uint16_t>::ReadBigEndian(data.data() + offset);
  offset += 2;
  hdr_metadata->max_frame_average_light_level =
      ByteReader<uint16_t>::ReadBigEndian(data.data() + offset);
  offset += 2;
  return offset;
}

size_t ColorSpaceExtension::ParseChromaticity(
    const uint8_t* data,
    HdrMasteringMetadata::Chromaticity* p) {
  uint16_t chromaticity_x_scaled = ByteReader<uint16_t>::ReadBigEndian(data);
  uint16_t chromaticity_y_scaled =
      ByteReader<uint16_t>::ReadBigEndian(data + 2);
  p->x = static_cast<float>(chromaticity_x_scaled) / kChromaticityDenominator;
  p->y = static_cast<float>(chromaticity_y_scaled) / kChromaticityDenominator;
  return 4;  // Return number of bytes read.
}

size_t ColorSpaceExtension::ParseLuminance(const uint8_t* data,
                                           float* f,
                                           int denominator) {
  uint16_t luminance_scaled = ByteReader<uint16_t>::ReadBigEndian(data);
  *f = static_cast<float>(luminance_scaled) / denominator;
  return 2;  // Return number of bytes read.
}

size_t ColorSpaceExtension::WriteHdrMetadata(rtc::ArrayView<uint8_t> data,
                                             const HdrMetadata& hdr_metadata) {
  RTC_DCHECK_EQ(data.size(),
                kValueSizeBytes - kValueSizeBytesWithoutHdrMetadata);
  RTC_DCHECK(hdr_metadata.Validate());
  size_t offset = 0;
  offset += WriteLuminance(data.data() + offset,
                           hdr_metadata.mastering_metadata.luminance_max,
                           kLuminanceMaxDenominator);
  offset += WriteLuminance(data.data() + offset,
                           hdr_metadata.mastering_metadata.luminance_min,
                           kLuminanceMinDenominator);
  offset += WriteChromaticity(data.data() + offset,
                              hdr_metadata.mastering_metadata.primary_r);
  offset += WriteChromaticity(data.data() + offset,
                              hdr_metadata.mastering_metadata.primary_g);
  offset += WriteChromaticity(data.data() + offset,
                              hdr_metadata.mastering_metadata.primary_b);
  offset += WriteChromaticity(data.data() + offset,
                              hdr_metadata.mastering_metadata.white_point);

  ByteWriter<uint16_t>::WriteBigEndian(data.data() + offset,
                                       hdr_metadata.max_content_light_level);
  offset += 2;
  ByteWriter<uint16_t>::WriteBigEndian(
      data.data() + offset, hdr_metadata.max_frame_average_light_level);
  offset += 2;
  return offset;
}

size_t ColorSpaceExtension::WriteChromaticity(
    uint8_t* data,
    const HdrMasteringMetadata::Chromaticity& p) {
  RTC_DCHECK_GE(p.x, 0.0f);
  RTC_DCHECK_LE(p.x, 1.0f);
  RTC_DCHECK_GE(p.y, 0.0f);
  RTC_DCHECK_LE(p.y, 1.0f);
  ByteWriter<uint16_t>::WriteBigEndian(
      data, std::round(p.x * kChromaticityDenominator));
  ByteWriter<uint16_t>::WriteBigEndian(
      data + 2, std::round(p.y * kChromaticityDenominator));
  return 4;  // Return number of bytes written.
}

size_t ColorSpaceExtension::WriteLuminance(uint8_t* data,
                                           float f,
                                           int denominator) {
  RTC_DCHECK_GE(f, 0.0f);
  float upscaled_value = f * denominator;
  RTC_DCHECK_LE(upscaled_value, std::numeric_limits<uint16_t>::max());
  ByteWriter<uint16_t>::WriteBigEndian(data, std::round(upscaled_value));
  return 2;  // Return number of bytes written.
}

bool BaseRtpStringExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                   std::string* str) {
  if (data.empty() || data[0] == 0)  // Valid string extension can't be empty.
    return false;
  const char* cstr = reinterpret_cast<const char*>(data.data());
  // If there is a \0 character in the middle of the `data`, treat it as end
  // of the string. Well-formed string extensions shouldn't contain it.
  str->assign(cstr, strnlen(cstr, data.size()));
  RTC_DCHECK(!str->empty());
  return true;
}

bool BaseRtpStringExtension::Write(rtc::ArrayView<uint8_t> data,
                                   const std::string& str) {
  if (str.size() > kMaxValueSizeBytes) {
    return false;
  }
  RTC_DCHECK_EQ(data.size(), str.size());
  RTC_DCHECK_GE(str.size(), 1);
  memcpy(data.data(), str.data(), str.size());
  return true;
}

// Constant declarations for RTP header extension types.
constexpr RTPExtensionType RtpStreamId::kId;
constexpr RTPExtensionType RepairedRtpStreamId::kId;
constexpr RTPExtensionType RtpMid::kId;

// An RTP Header Extension for Inband Comfort Noise
//
// The form of the audio level extension block:
//
//  0                   1
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |  ID   | len=0 |N| level       |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the One-Byte Header Format
//
//  0                   1                   2
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |      ID       |     len=1     |N|    level    |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// Sample Audio Level Encoding Using the Two-Byte Header Format

constexpr RTPExtensionType InbandComfortNoiseExtension::kId;
constexpr uint8_t InbandComfortNoiseExtension::kValueSizeBytes;
constexpr const char InbandComfortNoiseExtension::kUri[];

bool InbandComfortNoiseExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                        absl::optional<uint8_t>* level) {
  if (data.size() != kValueSizeBytes)
    return false;
  *level = (data[0] & 0b1000'0000) != 0
               ? absl::nullopt
               : absl::make_optional(data[0] & 0b0111'1111);
  return true;
}

bool InbandComfortNoiseExtension::Write(rtc::ArrayView<uint8_t> data,
                                        absl::optional<uint8_t> level) {
  RTC_DCHECK_EQ(data.size(), kValueSizeBytes);
  data[0] = 0b0000'0000;
  if (level) {
    if (*level > 127) {
      return false;
    }
    data[0] = 0b1000'0000 | *level;
  }
  return true;
}

// VideoFrameTrackingIdExtension
//
//   0                   1                   2
//   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  |  ID   | L=1   |    video-frame-tracking-id    |
//  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

constexpr RTPExtensionType VideoFrameTrackingIdExtension::kId;
constexpr uint8_t VideoFrameTrackingIdExtension::kValueSizeBytes;

bool VideoFrameTrackingIdExtension::Parse(rtc::ArrayView<const uint8_t> data,
                                          uint16_t* video_frame_tracking_id) {
  if (data.size() != kValueSizeBytes) {
    return false;
  }
  *video_frame_tracking_id = ByteReader<uint16_t>::ReadBigEndian(data.data());
  return true;
}

bool VideoFrameTrackingIdExtension::Write(rtc::ArrayView<uint8_t> data,
                                          uint16_t video_frame_tracking_id) {
  RTC_DCHECK_EQ(data.size(), kValueSizeBytes);
  ByteWriter<uint16_t>::WriteBigEndian(data.data(), video_frame_tracking_id);
  return true;
}

}  // namespace webrtc
