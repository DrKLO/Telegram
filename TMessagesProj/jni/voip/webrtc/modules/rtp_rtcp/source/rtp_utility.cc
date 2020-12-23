/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtp_utility.h"

#include <assert.h>
#include <stddef.h>

#include <string>

#include "api/array_view.h"
#include "api/video/video_content_type.h"
#include "api/video/video_rotation.h"
#include "api/video/video_timing.h"
#include "modules/rtp_rtcp/include/rtp_cvo.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

namespace RtpUtility {

enum {
  kRtcpExpectedVersion = 2,
  kRtcpMinHeaderLength = 4,
  kRtcpMinParseLength = 8,

  kRtpExpectedVersion = 2,
  kRtpMinParseLength = 12
};

/*
 * Misc utility routines
 */

size_t Word32Align(size_t size) {
  uint32_t remainder = size % 4;
  if (remainder != 0)
    return size + 4 - remainder;
  return size;
}

RtpHeaderParser::RtpHeaderParser(const uint8_t* rtpData,
                                 const size_t rtpDataLength)
    : _ptrRTPDataBegin(rtpData),
      _ptrRTPDataEnd(rtpData ? (rtpData + rtpDataLength) : NULL) {}

RtpHeaderParser::~RtpHeaderParser() {}

bool RtpHeaderParser::RTCP() const {
  // 72 to 76 is reserved for RTP
  // 77 to 79 is not reserver but  they are not assigned we will block them
  // for RTCP 200 SR  == marker bit + 72
  // for RTCP 204 APP == marker bit + 76
  /*
   *       RTCP
   *
   * FIR      full INTRA-frame request             192     [RFC2032]   supported
   * NACK     negative acknowledgement             193     [RFC2032]
   * IJ       Extended inter-arrival jitter report 195 [RFC-ietf-avt-rtp-toff
   * set-07.txt] http://tools.ietf.org/html/draft-ietf-avt-rtp-toffset-07
   * SR       sender report                        200     [RFC3551]   supported
   * RR       receiver report                      201     [RFC3551]   supported
   * SDES     source description                   202     [RFC3551]   supported
   * BYE      goodbye                              203     [RFC3551]   supported
   * APP      application-defined                  204     [RFC3551]   ignored
   * RTPFB    Transport layer FB message           205     [RFC4585]   supported
   * PSFB     Payload-specific FB message          206     [RFC4585]   supported
   * XR       extended report                      207     [RFC3611]   supported
   */

  /* 205       RFC 5104
   * FMT 1      NACK       supported
   * FMT 2      reserved
   * FMT 3      TMMBR      supported
   * FMT 4      TMMBN      supported
   */

  /* 206      RFC 5104
   * FMT 1:     Picture Loss Indication (PLI)                      supported
   * FMT 2:     Slice Lost Indication (SLI)
   * FMT 3:     Reference Picture Selection Indication (RPSI)
   * FMT 4:     Full Intra Request (FIR) Command                   supported
   * FMT 5:     Temporal-Spatial Trade-off Request (TSTR)
   * FMT 6:     Temporal-Spatial Trade-off Notification (TSTN)
   * FMT 7:     Video Back Channel Message (VBCM)
   * FMT 15:    Application layer FB message
   */

  const ptrdiff_t length = _ptrRTPDataEnd - _ptrRTPDataBegin;
  if (length < kRtcpMinHeaderLength) {
    return false;
  }

  const uint8_t V = _ptrRTPDataBegin[0] >> 6;
  if (V != kRtcpExpectedVersion) {
    return false;
  }

  const uint8_t payloadType = _ptrRTPDataBegin[1];
  switch (payloadType) {
    case 192:
      return true;
    case 193:
      // not supported
      // pass through and check for a potential RTP packet
      return false;
    case 195:
    case 200:
    case 201:
    case 202:
    case 203:
    case 204:
    case 205:
    case 206:
    case 207:
      return true;
    default:
      return false;
  }
}

bool RtpHeaderParser::ParseRtcp(RTPHeader* header) const {
  assert(header != NULL);

  const ptrdiff_t length = _ptrRTPDataEnd - _ptrRTPDataBegin;
  if (length < kRtcpMinParseLength) {
    return false;
  }

  const uint8_t V = _ptrRTPDataBegin[0] >> 6;
  if (V != kRtcpExpectedVersion) {
    return false;
  }

  const uint8_t PT = _ptrRTPDataBegin[1];
  const size_t len = (_ptrRTPDataBegin[2] << 8) + _ptrRTPDataBegin[3];
  const uint8_t* ptr = &_ptrRTPDataBegin[4];

  uint32_t SSRC = ByteReader<uint32_t>::ReadBigEndian(ptr);
  ptr += 4;

  header->payloadType = PT;
  header->ssrc = SSRC;
  header->headerLength = 4 + (len << 2);

  return true;
}

bool RtpHeaderParser::Parse(RTPHeader* header,
                            const RtpHeaderExtensionMap* ptrExtensionMap,
                            bool header_only) const {
  const ptrdiff_t length = _ptrRTPDataEnd - _ptrRTPDataBegin;
  if (length < kRtpMinParseLength) {
    return false;
  }

  // Version
  const uint8_t V = _ptrRTPDataBegin[0] >> 6;
  // Padding
  const bool P = ((_ptrRTPDataBegin[0] & 0x20) == 0) ? false : true;
  // eXtension
  const bool X = ((_ptrRTPDataBegin[0] & 0x10) == 0) ? false : true;
  const uint8_t CC = _ptrRTPDataBegin[0] & 0x0f;
  const bool M = ((_ptrRTPDataBegin[1] & 0x80) == 0) ? false : true;

  const uint8_t PT = _ptrRTPDataBegin[1] & 0x7f;

  const uint16_t sequenceNumber =
      (_ptrRTPDataBegin[2] << 8) + _ptrRTPDataBegin[3];

  const uint8_t* ptr = &_ptrRTPDataBegin[4];

  uint32_t RTPTimestamp = ByteReader<uint32_t>::ReadBigEndian(ptr);
  ptr += 4;

  uint32_t SSRC = ByteReader<uint32_t>::ReadBigEndian(ptr);
  ptr += 4;

  if (V != kRtpExpectedVersion) {
    return false;
  }

  const size_t CSRCocts = CC * 4;

  if ((ptr + CSRCocts) > _ptrRTPDataEnd) {
    return false;
  }

  header->markerBit = M;
  header->payloadType = PT;
  header->sequenceNumber = sequenceNumber;
  header->timestamp = RTPTimestamp;
  header->ssrc = SSRC;
  header->numCSRCs = CC;
  if (!P || header_only) {
    header->paddingLength = 0;
  }

  for (uint8_t i = 0; i < CC; ++i) {
    uint32_t CSRC = ByteReader<uint32_t>::ReadBigEndian(ptr);
    ptr += 4;
    header->arrOfCSRCs[i] = CSRC;
  }

  header->headerLength = 12 + CSRCocts;

  // If in effect, MAY be omitted for those packets for which the offset
  // is zero.
  header->extension.hasTransmissionTimeOffset = false;
  header->extension.transmissionTimeOffset = 0;

  // May not be present in packet.
  header->extension.hasAbsoluteSendTime = false;
  header->extension.absoluteSendTime = 0;

  // May not be present in packet.
  header->extension.hasAudioLevel = false;
  header->extension.voiceActivity = false;
  header->extension.audioLevel = 0;

  // May not be present in packet.
  header->extension.hasVideoRotation = false;
  header->extension.videoRotation = kVideoRotation_0;

  // May not be present in packet.
  header->extension.playout_delay.min_ms = -1;
  header->extension.playout_delay.max_ms = -1;

  // May not be present in packet.
  header->extension.hasVideoContentType = false;
  header->extension.videoContentType = VideoContentType::UNSPECIFIED;

  header->extension.has_video_timing = false;
  header->extension.video_timing = {0u, 0u, 0u, 0u, 0u, 0u, false};

  if (X) {
    /* RTP header extension, RFC 3550.
     0                   1                   2                   3
     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |      defined by profile       |           length              |
    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
    |                        header extension                       |
    |                             ....                              |
    */
    const ptrdiff_t remain = _ptrRTPDataEnd - ptr;
    if (remain < 4) {
      return false;
    }

    header->headerLength += 4;

    uint16_t definedByProfile = ByteReader<uint16_t>::ReadBigEndian(ptr);
    ptr += 2;

    // in 32 bit words
    size_t XLen = ByteReader<uint16_t>::ReadBigEndian(ptr);
    ptr += 2;
    XLen *= 4;  // in bytes

    if (static_cast<size_t>(remain) < (4 + XLen)) {
      return false;
    }
    static constexpr uint16_t kRtpOneByteHeaderExtensionId = 0xBEDE;
    if (definedByProfile == kRtpOneByteHeaderExtensionId) {
      const uint8_t* ptrRTPDataExtensionEnd = ptr + XLen;
      ParseOneByteExtensionHeader(header, ptrExtensionMap,
                                  ptrRTPDataExtensionEnd, ptr);
    }
    header->headerLength += XLen;
  }
  if (header->headerLength > static_cast<size_t>(length))
    return false;

  if (P && !header_only) {
    // Packet has padding.
    if (header->headerLength != static_cast<size_t>(length)) {
      // Packet is not header only. We can parse padding length now.
      header->paddingLength = *(_ptrRTPDataEnd - 1);
    } else {
      RTC_LOG(LS_WARNING) << "Cannot parse padding length.";
      // Packet is header only. We have no clue of the padding length.
      return false;
    }
  }

  if (header->headerLength + header->paddingLength >
      static_cast<size_t>(length))
    return false;
  return true;
}

void RtpHeaderParser::ParseOneByteExtensionHeader(
    RTPHeader* header,
    const RtpHeaderExtensionMap* ptrExtensionMap,
    const uint8_t* ptrRTPDataExtensionEnd,
    const uint8_t* ptr) const {
  if (!ptrExtensionMap) {
    return;
  }

  while (ptrRTPDataExtensionEnd - ptr > 0) {
    //  0
    //  0 1 2 3 4 5 6 7
    // +-+-+-+-+-+-+-+-+
    // |  ID   |  len  |
    // +-+-+-+-+-+-+-+-+

    // Note that 'len' is the header extension element length, which is the
    // number of bytes - 1.
    const int id = (*ptr & 0xf0) >> 4;
    const int len = (*ptr & 0x0f);
    ptr++;

    if (id == 0) {
      // Padding byte, skip ignoring len.
      continue;
    }

    if (id == 15) {
      RTC_LOG(LS_VERBOSE)
          << "RTP extension header 15 encountered. Terminate parsing.";
      return;
    }

    if (ptrRTPDataExtensionEnd - ptr < (len + 1)) {
      RTC_LOG(LS_WARNING) << "Incorrect one-byte extension len: " << (len + 1)
                          << ", bytes left in buffer: "
                          << (ptrRTPDataExtensionEnd - ptr);
      return;
    }

    RTPExtensionType type = ptrExtensionMap->GetType(id);
    if (type == RtpHeaderExtensionMap::kInvalidType) {
      // If we encounter an unknown extension, just skip over it.
      RTC_LOG(LS_WARNING) << "Failed to find extension id: " << id;
    } else {
      switch (type) {
        case kRtpExtensionTransmissionTimeOffset: {
          if (len != 2) {
            RTC_LOG(LS_WARNING)
                << "Incorrect transmission time offset len: " << len;
            return;
          }
          //  0                   1                   2                   3
          //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          // |  ID   | len=2 |              transmission offset              |
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

          header->extension.transmissionTimeOffset =
              ByteReader<int32_t, 3>::ReadBigEndian(ptr);
          header->extension.hasTransmissionTimeOffset = true;
          break;
        }
        case kRtpExtensionAudioLevel: {
          if (len != 0) {
            RTC_LOG(LS_WARNING) << "Incorrect audio level len: " << len;
            return;
          }
          //  0                   1
          //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          // |  ID   | len=0 |V|   level     |
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          //
          header->extension.audioLevel = ptr[0] & 0x7f;
          header->extension.voiceActivity = (ptr[0] & 0x80) != 0;
          header->extension.hasAudioLevel = true;
          break;
        }
        case kRtpExtensionAbsoluteSendTime: {
          if (len != 2) {
            RTC_LOG(LS_WARNING) << "Incorrect absolute send time len: " << len;
            return;
          }
          //  0                   1                   2                   3
          //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          // |  ID   | len=2 |              absolute send time               |
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

          header->extension.absoluteSendTime =
              ByteReader<uint32_t, 3>::ReadBigEndian(ptr);
          header->extension.hasAbsoluteSendTime = true;
          break;
        }
        case kRtpExtensionAbsoluteCaptureTime: {
          AbsoluteCaptureTime extension;
          if (!AbsoluteCaptureTimeExtension::Parse(
                  rtc::MakeArrayView(ptr, len + 1), &extension)) {
            RTC_LOG(LS_WARNING)
                << "Incorrect absolute capture time len: " << len;
            return;
          }
          header->extension.absolute_capture_time = extension;
          break;
        }
        case kRtpExtensionVideoRotation: {
          if (len != 0) {
            RTC_LOG(LS_WARNING)
                << "Incorrect coordination of video coordination len: " << len;
            return;
          }
          //  0                   1
          //  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          // |  ID   | len=0 |0 0 0 0 C F R R|
          // +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          header->extension.hasVideoRotation = true;
          header->extension.videoRotation =
              ConvertCVOByteToVideoRotation(ptr[0]);
          break;
        }
        case kRtpExtensionTransportSequenceNumber: {
          if (len != 1) {
            RTC_LOG(LS_WARNING)
                << "Incorrect transport sequence number len: " << len;
            return;
          }
          //   0                   1                   2
          //   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3
          //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          //  |  ID   | L=1   |transport wide sequence number |
          //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

          uint16_t sequence_number = ptr[0] << 8;
          sequence_number += ptr[1];
          header->extension.transportSequenceNumber = sequence_number;
          header->extension.hasTransportSequenceNumber = true;
          break;
        }
        case kRtpExtensionTransportSequenceNumber02:
          RTC_LOG(WARNING) << "TransportSequenceNumberV2 unsupported by rtp "
                              "header parser.";
          break;
        case kRtpExtensionPlayoutDelay: {
          if (len != 2) {
            RTC_LOG(LS_WARNING) << "Incorrect playout delay len: " << len;
            return;
          }
          //   0                   1                   2                   3
          //   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
          //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          //  |  ID   | len=2 |   MIN delay           |   MAX delay           |
          //  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

          int min_playout_delay = (ptr[0] << 4) | ((ptr[1] >> 4) & 0xf);
          int max_playout_delay = ((ptr[1] & 0xf) << 8) | ptr[2];
          header->extension.playout_delay.min_ms =
              min_playout_delay * PlayoutDelayLimits::kGranularityMs;
          header->extension.playout_delay.max_ms =
              max_playout_delay * PlayoutDelayLimits::kGranularityMs;
          break;
        }
        case kRtpExtensionVideoContentType: {
          if (len != 0) {
            RTC_LOG(LS_WARNING) << "Incorrect video content type len: " << len;
            return;
          }
          //    0                   1
          //    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5
          //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
          //   |  ID   | len=0 | Content type  |
          //   +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

          if (videocontenttypehelpers::IsValidContentType(ptr[0])) {
            header->extension.hasVideoContentType = true;
            header->extension.videoContentType =
                static_cast<VideoContentType>(ptr[0]);
          }
          break;
        }
        case kRtpExtensionVideoTiming: {
          if (len != VideoTimingExtension::kValueSizeBytes - 1) {
            RTC_LOG(LS_WARNING) << "Incorrect video timing len: " << len;
            return;
          }
          header->extension.has_video_timing = true;
          VideoTimingExtension::Parse(rtc::MakeArrayView(ptr, len + 1),
                                      &header->extension.video_timing);
          break;
        }
        case kRtpExtensionVideoLayersAllocation:
          RTC_LOG(WARNING) << "VideoLayersAllocation extension unsupported by "
                              "rtp header parser.";
          break;
        case kRtpExtensionRtpStreamId: {
          std::string name(reinterpret_cast<const char*>(ptr), len + 1);
          if (IsLegalRsidName(name)) {
            header->extension.stream_id = name;
          } else {
            RTC_LOG(LS_WARNING) << "Incorrect RtpStreamId";
          }
          break;
        }
        case kRtpExtensionRepairedRtpStreamId: {
          std::string name(reinterpret_cast<const char*>(ptr), len + 1);
          if (IsLegalRsidName(name)) {
            header->extension.repaired_stream_id = name;
          } else {
            RTC_LOG(LS_WARNING) << "Incorrect RepairedRtpStreamId";
          }
          break;
        }
        case kRtpExtensionMid: {
          std::string name(reinterpret_cast<const char*>(ptr), len + 1);
          if (IsLegalMidName(name)) {
            header->extension.mid = name;
          } else {
            RTC_LOG(LS_WARNING) << "Incorrect Mid";
          }
          break;
        }
        case kRtpExtensionGenericFrameDescriptor00:
        case kRtpExtensionGenericFrameDescriptor02:
          RTC_LOG(WARNING)
              << "RtpGenericFrameDescriptor unsupported by rtp header parser.";
          break;
        case kRtpExtensionColorSpace:
          RTC_LOG(WARNING)
              << "RtpExtensionColorSpace unsupported by rtp header parser.";
          break;
        case kRtpExtensionInbandComfortNoise:
          RTC_LOG(WARNING) << "Inband comfort noise extension unsupported by "
                              "rtp header parser.";
          break;
        case kRtpExtensionNone:
        case kRtpExtensionNumberOfExtensions: {
          RTC_NOTREACHED() << "Invalid extension type: " << type;
          return;
        }
      }
    }
    ptr += (len + 1);
  }
}

}  // namespace RtpUtility
}  // namespace webrtc
