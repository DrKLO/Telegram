/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/webrtc_media_engine.h"

#include <algorithm>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "api/transport/field_trial_based_config.h"
#include "media/base/media_constants.h"
#include "media/engine/webrtc_voice_engine.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

#ifdef HAVE_WEBRTC_VIDEO
#include "media/engine/webrtc_video_engine.h"
#else
#include "media/engine/null_webrtc_video_engine.h"
#endif

namespace cricket {

std::unique_ptr<MediaEngineInterface> CreateMediaEngine(
    MediaEngineDependencies dependencies) {
  // TODO(sprang): Make populating `dependencies.trials` mandatory and remove
  // these fallbacks.
  std::unique_ptr<webrtc::FieldTrialsView> fallback_trials(
      dependencies.trials ? nullptr : new webrtc::FieldTrialBasedConfig());
  const webrtc::FieldTrialsView& trials =
      dependencies.trials ? *dependencies.trials : *fallback_trials;
  auto audio_engine = std::make_unique<WebRtcVoiceEngine>(
      dependencies.task_queue_factory, dependencies.adm.get(),
      std::move(dependencies.audio_encoder_factory),
      std::move(dependencies.audio_decoder_factory),
      std::move(dependencies.audio_mixer),
      std::move(dependencies.audio_processing),
      dependencies.audio_frame_processor, trials);
#ifdef HAVE_WEBRTC_VIDEO
  auto video_engine = std::make_unique<WebRtcVideoEngine>(
      std::move(dependencies.video_encoder_factory),
      std::move(dependencies.video_decoder_factory), trials);
#else
  auto video_engine = std::make_unique<NullWebRtcVideoEngine>();
#endif
  return std::make_unique<CompositeMediaEngine>(std::move(fallback_trials),
                                                std::move(audio_engine),
                                                std::move(video_engine));
}

namespace {
// Remove mutually exclusive extensions with lower priority.
void DiscardRedundantExtensions(
    std::vector<webrtc::RtpExtension>* extensions,
    rtc::ArrayView<const char* const> extensions_decreasing_prio) {
  RTC_DCHECK(extensions);
  bool found = false;
  for (const char* uri : extensions_decreasing_prio) {
    auto it = absl::c_find_if(
        *extensions,
        [uri](const webrtc::RtpExtension& rhs) { return rhs.uri == uri; });
    if (it != extensions->end()) {
      if (found) {
        extensions->erase(it);
      }
      found = true;
    }
  }
}
}  // namespace

bool ValidateRtpExtensions(
    rtc::ArrayView<const webrtc::RtpExtension> extensions,
    rtc::ArrayView<const webrtc::RtpExtension> old_extensions) {
  bool id_used[1 + webrtc::RtpExtension::kMaxId] = {false};
  for (const auto& extension : extensions) {
    if (extension.id < webrtc::RtpExtension::kMinId ||
        extension.id > webrtc::RtpExtension::kMaxId) {
      RTC_LOG(LS_ERROR) << "Bad RTP extension ID: " << extension.ToString();
      return false;
    }
    if (id_used[extension.id]) {
      RTC_LOG(LS_ERROR) << "Duplicate RTP extension ID: "
                        << extension.ToString();
      return false;
    }
    id_used[extension.id] = true;
  }
  // Validate the extension list against the already negotiated extensions.
  // Re-registering is OK, re-mapping (either same URL at new ID or same
  // ID used with new URL) is an illegal remap.

  // This is required in order to avoid a crash when registering an
  // extension. A better structure would use the registered extensions
  // in the RTPSender. This requires spinning through:
  //
  // WebRtcVoiceMediaChannel::::WebRtcAudioSendStream::stream_ (pointer)
  // AudioSendStream::rtp_rtcp_module_ (pointer)
  // ModuleRtpRtcpImpl2::rtp_sender_ (pointer)
  // RtpSenderContext::packet_generator (struct member)
  // RTPSender::rtp_header_extension_map_ (class member)
  //
  // Getting at this seems like a hard slog.
  if (!old_extensions.empty()) {
    absl::string_view urimap[1 + webrtc::RtpExtension::kMaxId];
    std::map<absl::string_view, int> idmap;
    for (const auto& old_extension : old_extensions) {
      urimap[old_extension.id] = old_extension.uri;
      idmap[old_extension.uri] = old_extension.id;
    }
    for (const auto& extension : extensions) {
      if (!urimap[extension.id].empty() &&
          urimap[extension.id] != extension.uri) {
        RTC_LOG(LS_ERROR) << "Extension negotiation failure: " << extension.id
                          << " was mapped to " << urimap[extension.id]
                          << " but is proposed changed to " << extension.uri;
        return false;
      }
      const auto& it = idmap.find(extension.uri);
      if (it != idmap.end() && it->second != extension.id) {
        RTC_LOG(LS_ERROR) << "Extension negotation failure: " << extension.uri
                          << " was identified by " << it->second
                          << " but is proposed changed to " << extension.id;
        return false;
      }
    }
  }
  return true;
}

std::vector<webrtc::RtpExtension> FilterRtpExtensions(
    const std::vector<webrtc::RtpExtension>& extensions,
    bool (*supported)(absl::string_view),
    bool filter_redundant_extensions,
    const webrtc::FieldTrialsView& trials) {
  // Don't check against old parameters; this should have been done earlier.
  RTC_DCHECK(ValidateRtpExtensions(extensions, {}));
  RTC_DCHECK(supported);
  std::vector<webrtc::RtpExtension> result;

  // Ignore any extensions that we don't recognize.
  for (const auto& extension : extensions) {
    if (supported(extension.uri)) {
      result.push_back(extension);
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported RTP extension: "
                          << extension.ToString();
    }
  }

  // Sort by name, ascending (prioritise encryption), so that we don't reset
  // extensions if they were specified in a different order (also allows us
  // to use std::unique below).
  absl::c_sort(result, [](const webrtc::RtpExtension& rhs,
                          const webrtc::RtpExtension& lhs) {
    return rhs.encrypt == lhs.encrypt ? rhs.uri < lhs.uri
                                      : rhs.encrypt > lhs.encrypt;
  });

  // Remove unnecessary extensions (used on send side).
  if (filter_redundant_extensions) {
    auto it = std::unique(
        result.begin(), result.end(),
        [](const webrtc::RtpExtension& rhs, const webrtc::RtpExtension& lhs) {
          return rhs.uri == lhs.uri && rhs.encrypt == lhs.encrypt;
        });
    result.erase(it, result.end());

    // Keep just the highest priority extension of any in the following lists.
    if (absl::StartsWith(trials.Lookup("WebRTC-FilterAbsSendTimeExtension"),
                         "Enabled")) {
      static const char* const kBweExtensionPriorities[] = {
          webrtc::RtpExtension::kTransportSequenceNumberUri,
          webrtc::RtpExtension::kAbsSendTimeUri,
          webrtc::RtpExtension::kTimestampOffsetUri};
      DiscardRedundantExtensions(&result, kBweExtensionPriorities);
    } else {
      static const char* const kBweExtensionPriorities[] = {
          webrtc::RtpExtension::kAbsSendTimeUri,
          webrtc::RtpExtension::kTimestampOffsetUri};
      DiscardRedundantExtensions(&result, kBweExtensionPriorities);
    }
  }
  return result;
}

webrtc::BitrateConstraints GetBitrateConfigForCodec(const Codec& codec) {
  webrtc::BitrateConstraints config;
  int bitrate_kbps = 0;
  if (codec.GetParam(kCodecParamMinBitrate, &bitrate_kbps) &&
      bitrate_kbps > 0) {
    config.min_bitrate_bps = bitrate_kbps * 1000;
  } else {
    config.min_bitrate_bps = 0;
  }
  if (codec.GetParam(kCodecParamStartBitrate, &bitrate_kbps) &&
      bitrate_kbps > 0) {
    config.start_bitrate_bps = bitrate_kbps * 1000;
  } else {
    // Do not reconfigure start bitrate unless it's specified and positive.
    config.start_bitrate_bps = -1;
  }
  if (codec.GetParam(kCodecParamMaxBitrate, &bitrate_kbps) &&
      bitrate_kbps > 0) {
    config.max_bitrate_bps = bitrate_kbps * 1000;
  } else {
    config.max_bitrate_bps = -1;
  }
  return config;
}
}  // namespace cricket
