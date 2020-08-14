/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/media_engine.h"

#include <stddef.h>

#include <cstdint>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "api/video/video_bitrate_allocation.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_encode.h"

namespace cricket {

RtpCapabilities::RtpCapabilities() = default;
RtpCapabilities::~RtpCapabilities() = default;

webrtc::RtpParameters CreateRtpParametersWithOneEncoding() {
  webrtc::RtpParameters parameters;
  webrtc::RtpEncodingParameters encoding;
  parameters.encodings.push_back(encoding);
  return parameters;
}

webrtc::RtpParameters CreateRtpParametersWithEncodings(StreamParams sp) {
  std::vector<uint32_t> primary_ssrcs;
  sp.GetPrimarySsrcs(&primary_ssrcs);
  size_t encoding_count = primary_ssrcs.size();

  std::vector<webrtc::RtpEncodingParameters> encodings(encoding_count);
  for (size_t i = 0; i < encodings.size(); ++i) {
    encodings[i].ssrc = primary_ssrcs[i];
  }

  const std::vector<RidDescription>& rids = sp.rids();
  RTC_DCHECK(rids.size() == 0 || rids.size() == encoding_count);
  for (size_t i = 0; i < rids.size(); ++i) {
    encodings[i].rid = rids[i].rid;
  }

  webrtc::RtpParameters parameters;
  parameters.encodings = encodings;
  parameters.rtcp.cname = sp.cname;
  return parameters;
}

std::vector<webrtc::RtpExtension> GetDefaultEnabledRtpHeaderExtensions(
    const RtpHeaderExtensionQueryInterface& query_interface) {
  std::vector<webrtc::RtpExtension> extensions;
  for (const auto& entry : query_interface.GetRtpHeaderExtensions()) {
    if (entry.direction != webrtc::RtpTransceiverDirection::kStopped)
      extensions.emplace_back(entry.uri, *entry.preferred_id);
  }
  return extensions;
}

webrtc::RTCError CheckRtpParametersValues(
    const webrtc::RtpParameters& rtp_parameters) {
  using webrtc::RTCErrorType;

  for (size_t i = 0; i < rtp_parameters.encodings.size(); ++i) {
    if (rtp_parameters.encodings[i].bitrate_priority <= 0) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                           "Attempted to set RtpParameters bitrate_priority to "
                           "an invalid number. bitrate_priority must be > 0.");
    }
    if (rtp_parameters.encodings[i].scale_resolution_down_by &&
        *rtp_parameters.encodings[i].scale_resolution_down_by < 1.0) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_RANGE,
          "Attempted to set RtpParameters scale_resolution_down_by to an "
          "invalid value. scale_resolution_down_by must be >= 1.0");
    }
    if (rtp_parameters.encodings[i].max_framerate &&
        *rtp_parameters.encodings[i].max_framerate < 0.0) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                           "Attempted to set RtpParameters max_framerate to an "
                           "invalid value. max_framerate must be >= 0.0");
    }
    if (rtp_parameters.encodings[i].min_bitrate_bps &&
        rtp_parameters.encodings[i].max_bitrate_bps) {
      if (*rtp_parameters.encodings[i].max_bitrate_bps <
          *rtp_parameters.encodings[i].min_bitrate_bps) {
        LOG_AND_RETURN_ERROR(webrtc::RTCErrorType::INVALID_RANGE,
                             "Attempted to set RtpParameters min bitrate "
                             "larger than max bitrate.");
      }
    }
    if (rtp_parameters.encodings[i].num_temporal_layers) {
      if (*rtp_parameters.encodings[i].num_temporal_layers < 1 ||
          *rtp_parameters.encodings[i].num_temporal_layers >
              webrtc::kMaxTemporalStreams) {
        LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_RANGE,
                             "Attempted to set RtpParameters "
                             "num_temporal_layers to an invalid number.");
      }
    }
    if (i > 0 && (rtp_parameters.encodings[i].num_temporal_layers !=
                  rtp_parameters.encodings[i - 1].num_temporal_layers)) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_MODIFICATION,
          "Attempted to set RtpParameters num_temporal_layers "
          "at encoding layer i: " +
              rtc::ToString(i) +
              " to a different value than other encoding layers.");
    }
  }

  return webrtc::RTCError::OK();
}

webrtc::RTCError CheckRtpParametersInvalidModificationAndValues(
    const webrtc::RtpParameters& old_rtp_parameters,
    const webrtc::RtpParameters& rtp_parameters) {
  using webrtc::RTCErrorType;
  if (rtp_parameters.encodings.size() != old_rtp_parameters.encodings.size()) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_MODIFICATION,
        "Attempted to set RtpParameters with different encoding count");
  }
  if (rtp_parameters.rtcp != old_rtp_parameters.rtcp) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_MODIFICATION,
        "Attempted to set RtpParameters with modified RTCP parameters");
  }
  if (rtp_parameters.header_extensions !=
      old_rtp_parameters.header_extensions) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_MODIFICATION,
        "Attempted to set RtpParameters with modified header extensions");
  }
  if (!absl::c_equal(old_rtp_parameters.encodings, rtp_parameters.encodings,
                     [](const webrtc::RtpEncodingParameters& encoding1,
                        const webrtc::RtpEncodingParameters& encoding2) {
                       return encoding1.rid == encoding2.rid;
                     })) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_MODIFICATION,
                         "Attempted to change RID values in the encodings.");
  }
  if (!absl::c_equal(old_rtp_parameters.encodings, rtp_parameters.encodings,
                     [](const webrtc::RtpEncodingParameters& encoding1,
                        const webrtc::RtpEncodingParameters& encoding2) {
                       return encoding1.ssrc == encoding2.ssrc;
                     })) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_MODIFICATION,
                         "Attempted to set RtpParameters with modified SSRC");
  }

  return CheckRtpParametersValues(rtp_parameters);
}

CompositeMediaEngine::CompositeMediaEngine(
    std::unique_ptr<VoiceEngineInterface> voice_engine,
    std::unique_ptr<VideoEngineInterface> video_engine)
    : voice_engine_(std::move(voice_engine)),
      video_engine_(std::move(video_engine)) {}

CompositeMediaEngine::~CompositeMediaEngine() = default;

bool CompositeMediaEngine::Init() {
  voice().Init();
  return true;
}

VoiceEngineInterface& CompositeMediaEngine::voice() {
  return *voice_engine_.get();
}

VideoEngineInterface& CompositeMediaEngine::video() {
  return *video_engine_.get();
}

const VoiceEngineInterface& CompositeMediaEngine::voice() const {
  return *voice_engine_.get();
}

const VideoEngineInterface& CompositeMediaEngine::video() const {
  return *video_engine_.get();
}

}  // namespace cricket
