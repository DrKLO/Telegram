/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_parameters_conversion.h"

#include <cstdint>
#include <set>
#include <string>
#include <type_traits>
#include <utility>

#include "api/array_view.h"
#include "api/media_types.h"
#include "api/rtc_error.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/base/rtp_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace webrtc {

RTCErrorOr<cricket::FeedbackParam> ToCricketFeedbackParam(
    const RtcpFeedback& feedback) {
  switch (feedback.type) {
    case RtcpFeedbackType::CCM:
      if (!feedback.message_type) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Missing message type in CCM RtcpFeedback.");
      } else if (*feedback.message_type != RtcpFeedbackMessageType::FIR) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Invalid message type in CCM RtcpFeedback.");
      }
      return cricket::FeedbackParam(cricket::kRtcpFbParamCcm,
                                    cricket::kRtcpFbCcmParamFir);
    case RtcpFeedbackType::LNTF:
      if (feedback.message_type) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Didn't expect message type in LNTF RtcpFeedback.");
      }
      return cricket::FeedbackParam(cricket::kRtcpFbParamLntf);
    case RtcpFeedbackType::NACK:
      if (!feedback.message_type) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Missing message type in NACK RtcpFeedback.");
      }
      switch (*feedback.message_type) {
        case RtcpFeedbackMessageType::GENERIC_NACK:
          return cricket::FeedbackParam(cricket::kRtcpFbParamNack);
        case RtcpFeedbackMessageType::PLI:
          return cricket::FeedbackParam(cricket::kRtcpFbParamNack,
                                        cricket::kRtcpFbNackParamPli);
        default:
          LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                               "Invalid message type in NACK RtcpFeedback.");
      }
    case RtcpFeedbackType::REMB:
      if (feedback.message_type) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Didn't expect message type in REMB RtcpFeedback.");
      }
      return cricket::FeedbackParam(cricket::kRtcpFbParamRemb);
    case RtcpFeedbackType::TRANSPORT_CC:
      if (feedback.message_type) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Didn't expect message type in transport-cc RtcpFeedback.");
      }
      return cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc);
  }
  RTC_CHECK_NOTREACHED();
}

RTCErrorOr<cricket::Codec> ToCricketCodec(const RtpCodecParameters& codec) {
  switch (codec.kind) {
    case cricket::MEDIA_TYPE_AUDIO:
      if (codec.kind != cricket::MEDIA_TYPE_AUDIO) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Can't use video codec with audio sender or receiver.");
      }
      if (!codec.num_channels) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Missing number of channels for audio codec.");
      }
      if (*codec.num_channels <= 0) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                             "Number of channels must be positive.");
      }
      if (!codec.clock_rate) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Missing codec clock rate.");
      }
      if (*codec.clock_rate <= 0) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                             "Clock rate must be positive.");
      }
      break;
    case cricket::MEDIA_TYPE_VIDEO:
      if (codec.kind != cricket::MEDIA_TYPE_VIDEO) {
        LOG_AND_RETURN_ERROR(
            RTCErrorType::INVALID_PARAMETER,
            "Can't use audio codec with video sender or receiver.");
      }
      if (codec.num_channels) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Video codec shouldn't have num_channels.");
      }
      if (!codec.clock_rate) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Missing codec clock rate.");
      }
      if (*codec.clock_rate != cricket::kVideoCodecClockrate) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                             "Video clock rate must be 90000.");
      }
      break;
    default:
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "Unknown codec type");
  }

  if (!cricket::IsValidRtpPayloadType(codec.payload_type)) {
    char buf[40];
    rtc::SimpleStringBuilder sb(buf);
    sb << "Invalid payload type: " << codec.payload_type;
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE, sb.str());
  }

  cricket::Codec cricket_codec = [&]() {
    if (codec.kind == cricket::MEDIA_TYPE_AUDIO) {
      return cricket::CreateAudioCodec(codec.payload_type, codec.name,
                                       *codec.clock_rate, *codec.num_channels);
    }
    RTC_DCHECK(codec.kind == cricket::MEDIA_TYPE_VIDEO);
    return cricket::CreateVideoCodec(codec.payload_type, codec.name);
  }();

  for (const RtcpFeedback& feedback : codec.rtcp_feedback) {
    auto result = ToCricketFeedbackParam(feedback);
    if (!result.ok()) {
      return result.MoveError();
    }
    cricket_codec.AddFeedbackParam(result.MoveValue());
  }
  cricket_codec.params = codec.parameters;
  return std::move(cricket_codec);
}

RTCErrorOr<std::vector<cricket::Codec>> ToCricketCodecs(
    const std::vector<RtpCodecParameters>& codecs) {
  std::vector<cricket::Codec> cricket_codecs;
  std::set<int> seen_payload_types;
  for (const RtpCodecParameters& codec : codecs) {
    auto result = ToCricketCodec(codec);
    if (!result.ok()) {
      return result.MoveError();
    }
    if (!seen_payload_types.insert(codec.payload_type).second) {
      char buf[40];
      rtc::SimpleStringBuilder sb(buf);
      sb << "Duplicate payload type: " << codec.payload_type;
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, sb.str());
    }
    cricket_codecs.push_back(result.MoveValue());
  }
  return std::move(cricket_codecs);
}

RTCErrorOr<cricket::StreamParamsVec> ToCricketStreamParamsVec(
    const std::vector<RtpEncodingParameters>& encodings) {
  if (encodings.size() > 1u) {
    LOG_AND_RETURN_ERROR(RTCErrorType::UNSUPPORTED_PARAMETER,
                         "ORTC API implementation doesn't currently "
                         "support simulcast or layered encodings.");
  } else if (encodings.empty()) {
    return cricket::StreamParamsVec();
  }
  cricket::StreamParamsVec cricket_streams;
  const RtpEncodingParameters& encoding = encodings[0];
  if (encoding.ssrc) {
    cricket::StreamParams stream_params;
    stream_params.add_ssrc(*encoding.ssrc);
    cricket_streams.push_back(std::move(stream_params));
  }
  return std::move(cricket_streams);
}

absl::optional<RtcpFeedback> ToRtcpFeedback(
    const cricket::FeedbackParam& cricket_feedback) {
  if (cricket_feedback.id() == cricket::kRtcpFbParamCcm) {
    if (cricket_feedback.param() == cricket::kRtcpFbCcmParamFir) {
      return RtcpFeedback(RtcpFeedbackType::CCM, RtcpFeedbackMessageType::FIR);
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported parameter for CCM RTCP feedback: "
                          << cricket_feedback.param();
      return absl::nullopt;
    }
  } else if (cricket_feedback.id() == cricket::kRtcpFbParamLntf) {
    if (cricket_feedback.param().empty()) {
      return RtcpFeedback(RtcpFeedbackType::LNTF);
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported parameter for LNTF RTCP feedback: "
                          << cricket_feedback.param();
      return absl::nullopt;
    }
  } else if (cricket_feedback.id() == cricket::kRtcpFbParamNack) {
    if (cricket_feedback.param().empty()) {
      return RtcpFeedback(RtcpFeedbackType::NACK,
                          RtcpFeedbackMessageType::GENERIC_NACK);
    } else if (cricket_feedback.param() == cricket::kRtcpFbNackParamPli) {
      return RtcpFeedback(RtcpFeedbackType::NACK, RtcpFeedbackMessageType::PLI);
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported parameter for NACK RTCP feedback: "
                          << cricket_feedback.param();
      return absl::nullopt;
    }
  } else if (cricket_feedback.id() == cricket::kRtcpFbParamRemb) {
    if (!cricket_feedback.param().empty()) {
      RTC_LOG(LS_WARNING) << "Unsupported parameter for REMB RTCP feedback: "
                          << cricket_feedback.param();
      return absl::nullopt;
    } else {
      return RtcpFeedback(RtcpFeedbackType::REMB);
    }
  } else if (cricket_feedback.id() == cricket::kRtcpFbParamTransportCc) {
    if (!cricket_feedback.param().empty()) {
      RTC_LOG(LS_WARNING)
          << "Unsupported parameter for transport-cc RTCP feedback: "
          << cricket_feedback.param();
      return absl::nullopt;
    } else {
      return RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC);
    }
  }
  RTC_LOG(LS_WARNING) << "Unsupported RTCP feedback type: "
                      << cricket_feedback.id();
  return absl::nullopt;
}

std::vector<RtpEncodingParameters> ToRtpEncodings(
    const cricket::StreamParamsVec& stream_params) {
  std::vector<RtpEncodingParameters> rtp_encodings;
  for (const cricket::StreamParams& stream_param : stream_params) {
    RtpEncodingParameters rtp_encoding;
    rtp_encoding.ssrc.emplace(stream_param.first_ssrc());
    rtp_encodings.push_back(std::move(rtp_encoding));
  }
  return rtp_encodings;
}

RtpCodecCapability ToRtpCodecCapability(const cricket::Codec& cricket_codec) {
  RtpCodecCapability codec;
  codec.name = cricket_codec.name;
  codec.kind = cricket_codec.type == cricket::Codec::Type::kAudio
                   ? cricket::MEDIA_TYPE_AUDIO
                   : cricket::MEDIA_TYPE_VIDEO;
  codec.clock_rate.emplace(cricket_codec.clockrate);
  codec.preferred_payload_type.emplace(cricket_codec.id);
  for (const cricket::FeedbackParam& cricket_feedback :
       cricket_codec.feedback_params.params()) {
    absl::optional<RtcpFeedback> feedback = ToRtcpFeedback(cricket_feedback);
    if (feedback) {
      codec.rtcp_feedback.push_back(feedback.value());
    }
  }
  switch (cricket_codec.type) {
    case cricket::Codec::Type::kAudio:
      codec.num_channels = static_cast<int>(cricket_codec.channels);
      break;
    case cricket::Codec::Type::kVideo:
      codec.scalability_modes = cricket_codec.scalability_modes;
      break;
  }
  codec.parameters.insert(cricket_codec.params.begin(),
                          cricket_codec.params.end());
  return codec;
}

RtpCodecParameters ToRtpCodecParameters(const cricket::Codec& cricket_codec) {
  RtpCodecParameters codec_param;
  codec_param.name = cricket_codec.name;
  codec_param.kind = cricket_codec.type == cricket::Codec::Type::kAudio
                         ? cricket::MEDIA_TYPE_AUDIO
                         : cricket::MEDIA_TYPE_VIDEO;
  codec_param.clock_rate.emplace(cricket_codec.clockrate);
  codec_param.payload_type = cricket_codec.id;
  for (const cricket::FeedbackParam& cricket_feedback :
       cricket_codec.feedback_params.params()) {
    absl::optional<RtcpFeedback> feedback = ToRtcpFeedback(cricket_feedback);
    if (feedback) {
      codec_param.rtcp_feedback.push_back(feedback.value());
    }
  }
  switch (cricket_codec.type) {
    case cricket::Codec::Type::kAudio:
      codec_param.num_channels = static_cast<int>(cricket_codec.channels);
      break;
    case cricket::Codec::Type::kVideo:
      // Nothing to do.
      break;
  }
  codec_param.parameters = cricket_codec.params;
  return codec_param;
}

RtpCapabilities ToRtpCapabilities(
    const std::vector<cricket::Codec>& cricket_codecs,
    const cricket::RtpHeaderExtensions& cricket_extensions) {
  RtpCapabilities capabilities;
  bool have_red = false;
  bool have_ulpfec = false;
  bool have_flexfec = false;
  bool have_rtx = false;
  for (const cricket::Codec& cricket_codec : cricket_codecs) {
    if (cricket_codec.name == cricket::kRedCodecName) {
      have_red = true;
    } else if (cricket_codec.name == cricket::kUlpfecCodecName) {
      have_ulpfec = true;
    } else if (cricket_codec.name == cricket::kFlexfecCodecName) {
      have_flexfec = true;
    } else if (cricket_codec.name == cricket::kRtxCodecName) {
      if (have_rtx) {
        // There should only be one RTX codec entry
        continue;
      }
      have_rtx = true;
    }
    auto codec_capability = ToRtpCodecCapability(cricket_codec);
    if (cricket_codec.name == cricket::kRtxCodecName) {
      // RTX codec should not have any parameter
      codec_capability.parameters.clear();
    }
    capabilities.codecs.push_back(codec_capability);
  }
  for (const RtpExtension& cricket_extension : cricket_extensions) {
    capabilities.header_extensions.emplace_back(cricket_extension.uri,
                                                cricket_extension.id);
  }
  if (have_red) {
    capabilities.fec.push_back(FecMechanism::RED);
  }
  if (have_red && have_ulpfec) {
    capabilities.fec.push_back(FecMechanism::RED_AND_ULPFEC);
  }
  if (have_flexfec) {
    capabilities.fec.push_back(FecMechanism::FLEXFEC);
  }
  return capabilities;
}

RtpParameters ToRtpParameters(
    const std::vector<cricket::Codec>& cricket_codecs,
    const cricket::RtpHeaderExtensions& cricket_extensions,
    const cricket::StreamParamsVec& stream_params) {
  RtpParameters rtp_parameters;
  for (const cricket::Codec& cricket_codec : cricket_codecs) {
    rtp_parameters.codecs.push_back(ToRtpCodecParameters(cricket_codec));
  }
  for (const RtpExtension& cricket_extension : cricket_extensions) {
    rtp_parameters.header_extensions.emplace_back(cricket_extension.uri,
                                                  cricket_extension.id);
  }
  rtp_parameters.encodings = ToRtpEncodings(stream_params);
  return rtp_parameters;
}

}  // namespace webrtc
