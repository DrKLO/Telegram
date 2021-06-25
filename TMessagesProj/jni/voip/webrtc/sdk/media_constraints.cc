/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/media_constraints.h"

#include "absl/types/optional.h"
#include "api/peer_connection_interface.h"

namespace webrtc {
namespace {

// Find the highest-priority instance of the T-valued constraint named by
// |key| and return its value as |value|. |constraints| can be null.
// If |mandatory_constraints| is non-null, it is incremented if the key appears
// among the mandatory constraints.
// Returns true if the key was found and has a valid value for type T.
// If the key appears multiple times as an optional constraint, appearances
// after the first are ignored.
// Note: Because this uses FindFirst, repeated optional constraints whose
// first instance has an unrecognized value are not handled precisely in
// accordance with the specification.
template <typename T>
bool FindConstraint(const MediaConstraints* constraints,
                    const std::string& key,
                    T* value,
                    size_t* mandatory_constraints) {
  std::string string_value;
  if (!FindConstraint(constraints, key, &string_value, mandatory_constraints)) {
    return false;
  }
  return rtc::FromString(string_value, value);
}

// Specialization for std::string, since a string doesn't need conversion.
template <>
bool FindConstraint(const MediaConstraints* constraints,
                    const std::string& key,
                    std::string* value,
                    size_t* mandatory_constraints) {
  if (!constraints) {
    return false;
  }
  if (constraints->GetMandatory().FindFirst(key, value)) {
    if (mandatory_constraints) {
      ++*mandatory_constraints;
    }
    return true;
  }
  if (constraints->GetOptional().FindFirst(key, value)) {
    return true;
  }
  return false;
}

bool FindConstraint(const MediaConstraints* constraints,
                    const std::string& key,
                    bool* value,
                    size_t* mandatory_constraints) {
  return FindConstraint<bool>(constraints, key, value, mandatory_constraints);
}

bool FindConstraint(const MediaConstraints* constraints,
                    const std::string& key,
                    int* value,
                    size_t* mandatory_constraints) {
  return FindConstraint<int>(constraints, key, value, mandatory_constraints);
}

// Converts a constraint (mandatory takes precedence over optional) to an
// absl::optional.
template <typename T>
void ConstraintToOptional(const MediaConstraints* constraints,
                          const std::string& key,
                          absl::optional<T>* value_out) {
  T value;
  bool present = FindConstraint<T>(constraints, key, &value, nullptr);
  if (present) {
    *value_out = value;
  }
}
}  // namespace

const char MediaConstraints::kValueTrue[] = "true";
const char MediaConstraints::kValueFalse[] = "false";

// Constraints declared as static members in mediastreaminterface.h

// Audio constraints.
const char MediaConstraints::kGoogEchoCancellation[] = "googEchoCancellation";
const char MediaConstraints::kAutoGainControl[] = "googAutoGainControl";
const char MediaConstraints::kExperimentalAutoGainControl[] =
    "googAutoGainControl2";
const char MediaConstraints::kNoiseSuppression[] = "googNoiseSuppression";
const char MediaConstraints::kExperimentalNoiseSuppression[] =
    "googNoiseSuppression2";
const char MediaConstraints::kHighpassFilter[] = "googHighpassFilter";
const char MediaConstraints::kTypingNoiseDetection[] =
    "googTypingNoiseDetection";
const char MediaConstraints::kAudioMirroring[] = "googAudioMirroring";
const char MediaConstraints::kAudioNetworkAdaptorConfig[] =
    "googAudioNetworkAdaptorConfig";

// Constraint keys for CreateOffer / CreateAnswer defined in W3C specification.
const char MediaConstraints::kOfferToReceiveAudio[] = "OfferToReceiveAudio";
const char MediaConstraints::kOfferToReceiveVideo[] = "OfferToReceiveVideo";
const char MediaConstraints::kVoiceActivityDetection[] =
    "VoiceActivityDetection";
const char MediaConstraints::kIceRestart[] = "IceRestart";
// Google specific constraint for BUNDLE enable/disable.
const char MediaConstraints::kUseRtpMux[] = "googUseRtpMUX";

// Below constraints should be used during PeerConnection construction.
const char MediaConstraints::kEnableDtlsSrtp[] = "DtlsSrtpKeyAgreement";
// Google-specific constraint keys.
const char MediaConstraints::kEnableDscp[] = "googDscp";
const char MediaConstraints::kEnableIPv6[] = "googIPv6";
const char MediaConstraints::kEnableVideoSuspendBelowMinBitrate[] =
    "googSuspendBelowMinBitrate";
const char MediaConstraints::kCombinedAudioVideoBwe[] =
    "googCombinedAudioVideoBwe";
const char MediaConstraints::kScreencastMinBitrate[] =
    "googScreencastMinBitrate";
// TODO(ronghuawu): Remove once cpu overuse detection is stable.
const char MediaConstraints::kCpuOveruseDetection[] = "googCpuOveruseDetection";

const char MediaConstraints::kRawPacketizationForVideoEnabled[] =
    "googRawPacketizationForVideoEnabled";

const char MediaConstraints::kNumSimulcastLayers[] = "googNumSimulcastLayers";

// Set |value| to the value associated with the first appearance of |key|, or
// return false if |key| is not found.
bool MediaConstraints::Constraints::FindFirst(const std::string& key,
                                              std::string* value) const {
  for (Constraints::const_iterator iter = begin(); iter != end(); ++iter) {
    if (iter->key == key) {
      *value = iter->value;
      return true;
    }
  }
  return false;
}

void CopyConstraintsIntoRtcConfiguration(
    const MediaConstraints* constraints,
    PeerConnectionInterface::RTCConfiguration* configuration) {
  // Copy info from constraints into configuration, if present.
  if (!constraints) {
    return;
  }

  bool enable_ipv6;
  if (FindConstraint(constraints, MediaConstraints::kEnableIPv6, &enable_ipv6,
                     nullptr)) {
    configuration->disable_ipv6 = !enable_ipv6;
  }
  FindConstraint(constraints, MediaConstraints::kEnableDscp,
                 &configuration->media_config.enable_dscp, nullptr);
  FindConstraint(constraints, MediaConstraints::kCpuOveruseDetection,
                 &configuration->media_config.video.enable_cpu_adaptation,
                 nullptr);
  // Find Suspend Below Min Bitrate constraint.
  FindConstraint(
      constraints, MediaConstraints::kEnableVideoSuspendBelowMinBitrate,
      &configuration->media_config.video.suspend_below_min_bitrate, nullptr);
  ConstraintToOptional<int>(constraints,
                            MediaConstraints::kScreencastMinBitrate,
                            &configuration->screencast_min_bitrate);
  ConstraintToOptional<bool>(constraints,
                             MediaConstraints::kCombinedAudioVideoBwe,
                             &configuration->combined_audio_video_bwe);
  ConstraintToOptional<bool>(constraints, MediaConstraints::kEnableDtlsSrtp,
                             &configuration->enable_dtls_srtp);
}

void CopyConstraintsIntoAudioOptions(const MediaConstraints* constraints,
                                     cricket::AudioOptions* options) {
  if (!constraints) {
    return;
  }

  ConstraintToOptional<bool>(constraints,
                             MediaConstraints::kGoogEchoCancellation,
                             &options->echo_cancellation);
  ConstraintToOptional<bool>(constraints, MediaConstraints::kAutoGainControl,
                             &options->auto_gain_control);
  ConstraintToOptional<bool>(constraints,
                             MediaConstraints::kExperimentalAutoGainControl,
                             &options->experimental_agc);
  ConstraintToOptional<bool>(constraints, MediaConstraints::kNoiseSuppression,
                             &options->noise_suppression);
  ConstraintToOptional<bool>(constraints,
                             MediaConstraints::kExperimentalNoiseSuppression,
                             &options->experimental_ns);
  ConstraintToOptional<bool>(constraints, MediaConstraints::kHighpassFilter,
                             &options->highpass_filter);
  ConstraintToOptional<bool>(constraints,
                             MediaConstraints::kTypingNoiseDetection,
                             &options->typing_detection);
  ConstraintToOptional<bool>(constraints, MediaConstraints::kAudioMirroring,
                             &options->stereo_swapping);
  ConstraintToOptional<std::string>(
      constraints, MediaConstraints::kAudioNetworkAdaptorConfig,
      &options->audio_network_adaptor_config);
  // When |kAudioNetworkAdaptorConfig| is defined, it both means that audio
  // network adaptor is desired, and provides the config string.
  if (options->audio_network_adaptor_config) {
    options->audio_network_adaptor = true;
  }
}

bool CopyConstraintsIntoOfferAnswerOptions(
    const MediaConstraints* constraints,
    PeerConnectionInterface::RTCOfferAnswerOptions* offer_answer_options) {
  if (!constraints) {
    return true;
  }

  bool value = false;
  size_t mandatory_constraints_satisfied = 0;

  if (FindConstraint(constraints, MediaConstraints::kOfferToReceiveAudio,
                     &value, &mandatory_constraints_satisfied)) {
    offer_answer_options->offer_to_receive_audio =
        value ? PeerConnectionInterface::RTCOfferAnswerOptions::
                    kOfferToReceiveMediaTrue
              : 0;
  }

  if (FindConstraint(constraints, MediaConstraints::kOfferToReceiveVideo,
                     &value, &mandatory_constraints_satisfied)) {
    offer_answer_options->offer_to_receive_video =
        value ? PeerConnectionInterface::RTCOfferAnswerOptions::
                    kOfferToReceiveMediaTrue
              : 0;
  }
  if (FindConstraint(constraints, MediaConstraints::kVoiceActivityDetection,
                     &value, &mandatory_constraints_satisfied)) {
    offer_answer_options->voice_activity_detection = value;
  }
  if (FindConstraint(constraints, MediaConstraints::kUseRtpMux, &value,
                     &mandatory_constraints_satisfied)) {
    offer_answer_options->use_rtp_mux = value;
  }
  if (FindConstraint(constraints, MediaConstraints::kIceRestart, &value,
                     &mandatory_constraints_satisfied)) {
    offer_answer_options->ice_restart = value;
  }

  if (FindConstraint(constraints,
                     MediaConstraints::kRawPacketizationForVideoEnabled, &value,
                     &mandatory_constraints_satisfied)) {
    offer_answer_options->raw_packetization_for_video = value;
  }

  int layers;
  if (FindConstraint(constraints, MediaConstraints::kNumSimulcastLayers,
                     &layers, &mandatory_constraints_satisfied)) {
    offer_answer_options->num_simulcast_layers = layers;
  }

  return mandatory_constraints_satisfied == constraints->GetMandatory().size();
}

}  // namespace webrtc
