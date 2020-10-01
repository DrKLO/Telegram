/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_TEMPLATE_H_
#define API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_TEMPLATE_H_

#include <memory>
#include <vector>

#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/scoped_refptr.h"
#include "rtc_base/ref_counted_object.h"

namespace webrtc {

namespace audio_encoder_factory_template_impl {

template <typename... Ts>
struct Helper;

// Base case: 0 template parameters.
template <>
struct Helper<> {
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs) {}
  static absl::optional<AudioCodecInfo> QueryAudioEncoder(
      const SdpAudioFormat& format) {
    return absl::nullopt;
  }
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      int payload_type,
      const SdpAudioFormat& format,
      absl::optional<AudioCodecPairId> codec_pair_id) {
    return nullptr;
  }
};

// Inductive case: Called with n + 1 template parameters; calls subroutines
// with n template parameters.
template <typename T, typename... Ts>
struct Helper<T, Ts...> {
  static void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs) {
    T::AppendSupportedEncoders(specs);
    Helper<Ts...>::AppendSupportedEncoders(specs);
  }
  static absl::optional<AudioCodecInfo> QueryAudioEncoder(
      const SdpAudioFormat& format) {
    auto opt_config = T::SdpToConfig(format);
    static_assert(std::is_same<decltype(opt_config),
                               absl::optional<typename T::Config>>::value,
                  "T::SdpToConfig() must return a value of type "
                  "absl::optional<T::Config>");
    return opt_config ? absl::optional<AudioCodecInfo>(
                            T::QueryAudioEncoder(*opt_config))
                      : Helper<Ts...>::QueryAudioEncoder(format);
  }
  static std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      int payload_type,
      const SdpAudioFormat& format,
      absl::optional<AudioCodecPairId> codec_pair_id) {
    auto opt_config = T::SdpToConfig(format);
    if (opt_config) {
      return T::MakeAudioEncoder(*opt_config, payload_type, codec_pair_id);
    } else {
      return Helper<Ts...>::MakeAudioEncoder(payload_type, format,
                                             codec_pair_id);
    }
  }
};

template <typename... Ts>
class AudioEncoderFactoryT : public AudioEncoderFactory {
 public:
  std::vector<AudioCodecSpec> GetSupportedEncoders() override {
    std::vector<AudioCodecSpec> specs;
    Helper<Ts...>::AppendSupportedEncoders(&specs);
    return specs;
  }

  absl::optional<AudioCodecInfo> QueryAudioEncoder(
      const SdpAudioFormat& format) override {
    return Helper<Ts...>::QueryAudioEncoder(format);
  }

  std::unique_ptr<AudioEncoder> MakeAudioEncoder(
      int payload_type,
      const SdpAudioFormat& format,
      absl::optional<AudioCodecPairId> codec_pair_id) override {
    return Helper<Ts...>::MakeAudioEncoder(payload_type, format, codec_pair_id);
  }
};

}  // namespace audio_encoder_factory_template_impl

// Make an AudioEncoderFactory that can create instances of the given encoders.
//
// Each encoder type is given as a template argument to the function; it should
// be a struct with the following static member functions:
//
//   // Converts |audio_format| to a ConfigType instance. Returns an empty
//   // optional if |audio_format| doesn't correctly specify an encoder of our
//   // type.
//   absl::optional<ConfigType> SdpToConfig(const SdpAudioFormat& audio_format);
//
//   // Appends zero or more AudioCodecSpecs to the list that will be returned
//   // by AudioEncoderFactory::GetSupportedEncoders().
//   void AppendSupportedEncoders(std::vector<AudioCodecSpec>* specs);
//
//   // Returns information about how this format would be encoded. Used to
//   // implement AudioEncoderFactory::QueryAudioEncoder().
//   AudioCodecInfo QueryAudioEncoder(const ConfigType& config);
//
//   // Creates an AudioEncoder for the specified format. Used to implement
//   // AudioEncoderFactory::MakeAudioEncoder().
//   std::unique_ptr<AudioDecoder> MakeAudioEncoder(
//       const ConfigType& config,
//       int payload_type,
//       absl::optional<AudioCodecPairId> codec_pair_id);
//
// ConfigType should be a type that encapsulates all the settings needed to
// create an AudioEncoder. T::Config (where T is the encoder struct) should
// either be the config type, or an alias for it.
//
// Whenever it tries to do something, the new factory will try each of the
// encoders in the order they were specified in the template argument list,
// stopping at the first one that claims to be able to do the job.
//
// TODO(kwiberg): Point at CreateBuiltinAudioEncoderFactory() for an example of
// how it is used.
template <typename... Ts>
rtc::scoped_refptr<AudioEncoderFactory> CreateAudioEncoderFactory() {
  // There's no technical reason we couldn't allow zero template parameters,
  // but such a factory couldn't create any encoders, and callers can do this
  // by mistake by simply forgetting the <> altogether. So we forbid it in
  // order to prevent caller foot-shooting.
  static_assert(sizeof...(Ts) >= 1,
                "Caller must give at least one template parameter");

  return rtc::scoped_refptr<AudioEncoderFactory>(
      new rtc::RefCountedObject<
          audio_encoder_factory_template_impl::AudioEncoderFactoryT<Ts...>>());
}

}  // namespace webrtc

#endif  // API_AUDIO_CODECS_AUDIO_ENCODER_FACTORY_TEMPLATE_H_
