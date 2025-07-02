/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/codecs/opus/audio_encoder_opus.h"

#include <algorithm>
#include <iterator>
#include <memory>
#include <string>
#include <utility>

#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "modules/audio_coding/audio_network_adaptor/audio_network_adaptor_impl.h"
#include "modules/audio_coding/audio_network_adaptor/controller_manager.h"
#include "modules/audio_coding/codecs/opus/audio_coder_opus_common.h"
#include "modules/audio_coding/codecs/opus/opus_interface.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/exp_filter.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/string_to_number.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

namespace {

// Codec parameters for Opus.
// draft-spittka-payload-rtp-opus-03

// Recommended bitrates:
// 8-12 kb/s for NB speech,
// 16-20 kb/s for WB speech,
// 28-40 kb/s for FB speech,
// 48-64 kb/s for FB mono music, and
// 64-128 kb/s for FB stereo music.
// The current implementation applies the following values to mono signals,
// and multiplies them by 2 for stereo.
constexpr int kOpusBitrateNbBps = 12000;
constexpr int kOpusBitrateWbBps = 20000;
constexpr int kOpusBitrateFbBps = 32000;

constexpr int kRtpTimestampRateHz = 48000;
constexpr int kDefaultMaxPlaybackRate = 48000;

// These two lists must be sorted from low to high
#if WEBRTC_OPUS_SUPPORT_120MS_PTIME
constexpr int kANASupportedFrameLengths[] = {20, 40, 60, 120};
constexpr int kOpusSupportedFrameLengths[] = {10, 20, 40, 60, 120};
#else
constexpr int kANASupportedFrameLengths[] = {20, 40, 60};
constexpr int kOpusSupportedFrameLengths[] = {10, 20, 40, 60};
#endif

// PacketLossFractionSmoother uses an exponential filter with a time constant
// of -1.0 / ln(0.9999) = 10000 ms.
constexpr float kAlphaForPacketLossFractionSmoother = 0.9999f;
constexpr float kMaxPacketLossFraction = 0.2f;

int CalculateDefaultBitrate(int max_playback_rate, size_t num_channels) {
  const int bitrate = [&] {
    if (max_playback_rate <= 8000) {
      return kOpusBitrateNbBps * rtc::dchecked_cast<int>(num_channels);
    } else if (max_playback_rate <= 16000) {
      return kOpusBitrateWbBps * rtc::dchecked_cast<int>(num_channels);
    } else {
      return kOpusBitrateFbBps * rtc::dchecked_cast<int>(num_channels);
    }
  }();
  RTC_DCHECK_GE(bitrate, AudioEncoderOpusConfig::kMinBitrateBps);
  RTC_DCHECK_LE(bitrate, AudioEncoderOpusConfig::kMaxBitrateBps);
  return bitrate;
}

// Get the maxaveragebitrate parameter in string-form, so we can properly figure
// out how invalid it is and accurately log invalid values.
int CalculateBitrate(int max_playback_rate_hz,
                     size_t num_channels,
                     absl::optional<std::string> bitrate_param) {
  const int default_bitrate =
      CalculateDefaultBitrate(max_playback_rate_hz, num_channels);

  if (bitrate_param) {
    const auto bitrate = rtc::StringToNumber<int>(*bitrate_param);
    if (bitrate) {
      const int chosen_bitrate =
          std::max(AudioEncoderOpusConfig::kMinBitrateBps,
                   std::min(*bitrate, AudioEncoderOpusConfig::kMaxBitrateBps));
      if (bitrate != chosen_bitrate) {
        RTC_LOG(LS_WARNING) << "Invalid maxaveragebitrate " << *bitrate
                            << " clamped to " << chosen_bitrate;
      }
      return chosen_bitrate;
    }
    RTC_LOG(LS_WARNING) << "Invalid maxaveragebitrate \"" << *bitrate_param
                        << "\" replaced by default bitrate " << default_bitrate;
  }

  return default_bitrate;
}

int GetChannelCount(const SdpAudioFormat& format) {
  const auto param = GetFormatParameter(format, "stereo");
  if (param == "1") {
    return 2;
  } else {
    return 1;
  }
}

int GetMaxPlaybackRate(const SdpAudioFormat& format) {
  const auto param = GetFormatParameter<int>(format, "maxplaybackrate");
  if (param && *param >= 8000) {
    return std::min(*param, kDefaultMaxPlaybackRate);
  }
  return kDefaultMaxPlaybackRate;
}

int GetFrameSizeMs(const SdpAudioFormat& format) {
  const auto ptime = GetFormatParameter<int>(format, "ptime");
  if (ptime) {
    // Pick the next highest supported frame length from
    // kOpusSupportedFrameLengths.
    for (const int supported_frame_length : kOpusSupportedFrameLengths) {
      if (supported_frame_length >= *ptime) {
        return supported_frame_length;
      }
    }
    // If none was found, return the largest supported frame length.
    return *(std::end(kOpusSupportedFrameLengths) - 1);
  }

  return AudioEncoderOpusConfig::kDefaultFrameSizeMs;
}

void FindSupportedFrameLengths(int min_frame_length_ms,
                               int max_frame_length_ms,
                               std::vector<int>* out) {
  out->clear();
  std::copy_if(std::begin(kANASupportedFrameLengths),
               std::end(kANASupportedFrameLengths), std::back_inserter(*out),
               [&](int frame_length_ms) {
                 return frame_length_ms >= min_frame_length_ms &&
                        frame_length_ms <= max_frame_length_ms;
               });
  RTC_DCHECK(std::is_sorted(out->begin(), out->end()));
}

int GetBitrateBps(const AudioEncoderOpusConfig& config) {
  RTC_DCHECK(config.IsOk());
  return *config.bitrate_bps;
}

std::vector<float> GetBitrateMultipliers() {
  constexpr char kBitrateMultipliersName[] =
      "WebRTC-Audio-OpusBitrateMultipliers";
  const bool use_bitrate_multipliers =
      webrtc::field_trial::IsEnabled(kBitrateMultipliersName);
  if (use_bitrate_multipliers) {
    const std::string field_trial_string =
        webrtc::field_trial::FindFullName(kBitrateMultipliersName);
    std::vector<std::string> pieces;
    rtc::tokenize(field_trial_string, '-', &pieces);
    if (pieces.size() < 2 || pieces[0] != "Enabled") {
      RTC_LOG(LS_WARNING) << "Invalid parameters for "
                          << kBitrateMultipliersName
                          << ", not using custom values.";
      return std::vector<float>();
    }
    std::vector<float> multipliers(pieces.size() - 1);
    for (size_t i = 1; i < pieces.size(); i++) {
      if (!rtc::FromString(pieces[i], &multipliers[i - 1])) {
        RTC_LOG(LS_WARNING)
            << "Invalid parameters for " << kBitrateMultipliersName
            << ", not using custom values.";
        return std::vector<float>();
      }
    }
    RTC_LOG(LS_INFO) << "Using custom bitrate multipliers: "
                     << field_trial_string;
    return multipliers;
  }
  return std::vector<float>();
}

int GetMultipliedBitrate(int bitrate, const std::vector<float>& multipliers) {
  // The multipliers are valid from 5 kbps.
  const size_t bitrate_kbps = static_cast<size_t>(bitrate / 1000);
  if (bitrate_kbps < 5 || bitrate_kbps >= multipliers.size() + 5) {
    return bitrate;
  }
  return static_cast<int>(multipliers[bitrate_kbps - 5] * bitrate);
}
}  // namespace

void AudioEncoderOpusImpl::AppendSupportedEncoders(
    std::vector<AudioCodecSpec>* specs) {
  const SdpAudioFormat fmt = {"opus",
                              kRtpTimestampRateHz,
                              2,
                              {{"minptime", "10"}, {"useinbandfec", "1"}}};
  const AudioCodecInfo info = QueryAudioEncoder(*SdpToConfig(fmt));
  specs->push_back({fmt, info});
}

AudioCodecInfo AudioEncoderOpusImpl::QueryAudioEncoder(
    const AudioEncoderOpusConfig& config) {
  RTC_DCHECK(config.IsOk());
  AudioCodecInfo info(config.sample_rate_hz, config.num_channels,
                      *config.bitrate_bps,
                      AudioEncoderOpusConfig::kMinBitrateBps,
                      AudioEncoderOpusConfig::kMaxBitrateBps);
  info.allow_comfort_noise = false;
  info.supports_network_adaption = true;
  return info;
}

std::unique_ptr<AudioEncoder> AudioEncoderOpusImpl::MakeAudioEncoder(
    const AudioEncoderOpusConfig& config,
    int payload_type) {
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return nullptr;
  }
  return std::make_unique<AudioEncoderOpusImpl>(config, payload_type);
}

absl::optional<AudioEncoderOpusConfig> AudioEncoderOpusImpl::SdpToConfig(
    const SdpAudioFormat& format) {
  if (!absl::EqualsIgnoreCase(format.name, "opus") ||
      format.clockrate_hz != kRtpTimestampRateHz || format.num_channels != 2) {
    return absl::nullopt;
  }

  AudioEncoderOpusConfig config;
  config.num_channels = GetChannelCount(format);
  config.frame_size_ms = GetFrameSizeMs(format);
  config.max_playback_rate_hz = GetMaxPlaybackRate(format);
  config.fec_enabled = (GetFormatParameter(format, "useinbandfec") == "1");
  config.dtx_enabled = (GetFormatParameter(format, "usedtx") == "1");
  config.cbr_enabled = (GetFormatParameter(format, "cbr") == "1");
  config.bitrate_bps =
      CalculateBitrate(config.max_playback_rate_hz, config.num_channels,
                       GetFormatParameter(format, "maxaveragebitrate"));
  config.application = config.num_channels == 1
                           ? AudioEncoderOpusConfig::ApplicationMode::kVoip
                           : AudioEncoderOpusConfig::ApplicationMode::kAudio;

  constexpr int kMinANAFrameLength = kANASupportedFrameLengths[0];
  constexpr int kMaxANAFrameLength =
      kANASupportedFrameLengths[arraysize(kANASupportedFrameLengths) - 1];

  // For now, minptime and maxptime are only used with ANA. If ptime is outside
  // of this range, it will get adjusted once ANA takes hold. Ideally, we'd know
  // if ANA was to be used when setting up the config, and adjust accordingly.
  const int min_frame_length_ms =
      GetFormatParameter<int>(format, "minptime").value_or(kMinANAFrameLength);
  const int max_frame_length_ms =
      GetFormatParameter<int>(format, "maxptime").value_or(kMaxANAFrameLength);

  FindSupportedFrameLengths(min_frame_length_ms, max_frame_length_ms,
                            &config.supported_frame_lengths_ms);
  if (!config.IsOk()) {
    RTC_DCHECK_NOTREACHED();
    return absl::nullopt;
  }
  return config;
}

absl::optional<int> AudioEncoderOpusImpl::GetNewComplexity(
    const AudioEncoderOpusConfig& config) {
  RTC_DCHECK(config.IsOk());
  const int bitrate_bps = GetBitrateBps(config);
  if (bitrate_bps >= config.complexity_threshold_bps -
                         config.complexity_threshold_window_bps &&
      bitrate_bps <= config.complexity_threshold_bps +
                         config.complexity_threshold_window_bps) {
    // Within the hysteresis window; make no change.
    return absl::nullopt;
  } else {
    return bitrate_bps <= config.complexity_threshold_bps
               ? config.low_rate_complexity
               : config.complexity;
  }
}

absl::optional<int> AudioEncoderOpusImpl::GetNewBandwidth(
    const AudioEncoderOpusConfig& config,
    OpusEncInst* inst) {
  constexpr int kMinWidebandBitrate = 8000;
  constexpr int kMaxNarrowbandBitrate = 9000;
  constexpr int kAutomaticThreshold = 11000;
  RTC_DCHECK(config.IsOk());
  const int bitrate = GetBitrateBps(config);
  if (bitrate > kAutomaticThreshold) {
    return absl::optional<int>(OPUS_AUTO);
  }
  const int bandwidth = WebRtcOpus_GetBandwidth(inst);
  RTC_DCHECK_GE(bandwidth, 0);
  if (bitrate > kMaxNarrowbandBitrate && bandwidth < OPUS_BANDWIDTH_WIDEBAND) {
    return absl::optional<int>(OPUS_BANDWIDTH_WIDEBAND);
  } else if (bitrate < kMinWidebandBitrate &&
             bandwidth > OPUS_BANDWIDTH_NARROWBAND) {
    return absl::optional<int>(OPUS_BANDWIDTH_NARROWBAND);
  }
  return absl::optional<int>();
}

class AudioEncoderOpusImpl::PacketLossFractionSmoother {
 public:
  explicit PacketLossFractionSmoother()
      : last_sample_time_ms_(rtc::TimeMillis()),
        smoother_(kAlphaForPacketLossFractionSmoother) {}

  // Gets the smoothed packet loss fraction.
  float GetAverage() const {
    float value = smoother_.filtered();
    return (value == rtc::ExpFilter::kValueUndefined) ? 0.0f : value;
  }

  // Add new observation to the packet loss fraction smoother.
  void AddSample(float packet_loss_fraction) {
    int64_t now_ms = rtc::TimeMillis();
    smoother_.Apply(static_cast<float>(now_ms - last_sample_time_ms_),
                    packet_loss_fraction);
    last_sample_time_ms_ = now_ms;
  }

 private:
  int64_t last_sample_time_ms_;

  // An exponential filter is used to smooth the packet loss fraction.
  rtc::ExpFilter smoother_;
};

AudioEncoderOpusImpl::AudioEncoderOpusImpl(const AudioEncoderOpusConfig& config,
                                           int payload_type)
    : AudioEncoderOpusImpl(
          config,
          payload_type,
          [this](absl::string_view config_string, RtcEventLog* event_log) {
            return DefaultAudioNetworkAdaptorCreator(config_string, event_log);
          },
          // We choose 5sec as initial time constant due to empirical data.
          std::make_unique<SmoothingFilterImpl>(5000)) {}

AudioEncoderOpusImpl::AudioEncoderOpusImpl(
    const AudioEncoderOpusConfig& config,
    int payload_type,
    const AudioNetworkAdaptorCreator& audio_network_adaptor_creator,
    std::unique_ptr<SmoothingFilter> bitrate_smoother)
    : payload_type_(payload_type),
      use_stable_target_for_adaptation_(!webrtc::field_trial::IsDisabled(
          "WebRTC-Audio-StableTargetAdaptation")),
      adjust_bandwidth_(
          webrtc::field_trial::IsEnabled("WebRTC-AdjustOpusBandwidth")),
      bitrate_changed_(true),
      bitrate_multipliers_(GetBitrateMultipliers()),
      packet_loss_rate_(0.0),
      inst_(nullptr),
      packet_loss_fraction_smoother_(new PacketLossFractionSmoother()),
      audio_network_adaptor_creator_(audio_network_adaptor_creator),
      bitrate_smoother_(std::move(bitrate_smoother)),
      consecutive_dtx_frames_(0) {
  RTC_DCHECK(0 <= payload_type && payload_type <= 127);

  // Sanity check of the redundant payload type field that we want to get rid
  // of. See https://bugs.chromium.org/p/webrtc/issues/detail?id=7847
  RTC_CHECK(config.payload_type == -1 || config.payload_type == payload_type);

  RTC_CHECK(RecreateEncoderInstance(config));
  SetProjectedPacketLossRate(packet_loss_rate_);
}

AudioEncoderOpusImpl::AudioEncoderOpusImpl(int payload_type,
                                           const SdpAudioFormat& format)
    : AudioEncoderOpusImpl(*SdpToConfig(format), payload_type) {}

AudioEncoderOpusImpl::~AudioEncoderOpusImpl() {
  RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
}

int AudioEncoderOpusImpl::SampleRateHz() const {
  return config_.sample_rate_hz;
}

size_t AudioEncoderOpusImpl::NumChannels() const {
  return config_.num_channels;
}

int AudioEncoderOpusImpl::RtpTimestampRateHz() const {
  return kRtpTimestampRateHz;
}

size_t AudioEncoderOpusImpl::Num10MsFramesInNextPacket() const {
  return Num10msFramesPerPacket();
}

size_t AudioEncoderOpusImpl::Max10MsFramesInAPacket() const {
  return Num10msFramesPerPacket();
}

int AudioEncoderOpusImpl::GetTargetBitrate() const {
  return GetBitrateBps(config_);
}

void AudioEncoderOpusImpl::Reset() {
  RTC_CHECK(RecreateEncoderInstance(config_));
}

bool AudioEncoderOpusImpl::SetFec(bool enable) {
  if (enable) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableFec(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableFec(inst_));
  }
  config_.fec_enabled = enable;
  return true;
}

bool AudioEncoderOpusImpl::SetDtx(bool enable) {
  if (enable) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableDtx(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableDtx(inst_));
  }
  config_.dtx_enabled = enable;
  return true;
}

bool AudioEncoderOpusImpl::GetDtx() const {
  return config_.dtx_enabled;
}

bool AudioEncoderOpusImpl::SetApplication(Application application) {
  auto conf = config_;
  switch (application) {
    case Application::kSpeech:
      conf.application = AudioEncoderOpusConfig::ApplicationMode::kVoip;
      break;
    case Application::kAudio:
      conf.application = AudioEncoderOpusConfig::ApplicationMode::kAudio;
      break;
  }
  return RecreateEncoderInstance(conf);
}

void AudioEncoderOpusImpl::SetMaxPlaybackRate(int frequency_hz) {
  auto conf = config_;
  conf.max_playback_rate_hz = frequency_hz;
  RTC_CHECK(RecreateEncoderInstance(conf));
}

bool AudioEncoderOpusImpl::EnableAudioNetworkAdaptor(
    const std::string& config_string,
    RtcEventLog* event_log) {
  audio_network_adaptor_ =
      audio_network_adaptor_creator_(config_string, event_log);
  return audio_network_adaptor_.get() != nullptr;
}

void AudioEncoderOpusImpl::DisableAudioNetworkAdaptor() {
  audio_network_adaptor_.reset(nullptr);
}

void AudioEncoderOpusImpl::OnReceivedUplinkPacketLossFraction(
    float uplink_packet_loss_fraction) {
  if (audio_network_adaptor_) {
    audio_network_adaptor_->SetUplinkPacketLossFraction(
        uplink_packet_loss_fraction);
    ApplyAudioNetworkAdaptor();
  }
  packet_loss_fraction_smoother_->AddSample(uplink_packet_loss_fraction);
  float average_fraction_loss = packet_loss_fraction_smoother_->GetAverage();
  SetProjectedPacketLossRate(average_fraction_loss);
}

void AudioEncoderOpusImpl::OnReceivedTargetAudioBitrate(
    int target_audio_bitrate_bps) {
  SetTargetBitrate(target_audio_bitrate_bps);
}

void AudioEncoderOpusImpl::OnReceivedUplinkBandwidth(
    int target_audio_bitrate_bps,
    absl::optional<int64_t> bwe_period_ms,
    absl::optional<int64_t> stable_target_bitrate_bps) {
  if (audio_network_adaptor_) {
    audio_network_adaptor_->SetTargetAudioBitrate(target_audio_bitrate_bps);
    if (use_stable_target_for_adaptation_) {
      if (stable_target_bitrate_bps)
        audio_network_adaptor_->SetUplinkBandwidth(*stable_target_bitrate_bps);
    } else {
      // We give smoothed bitrate allocation to audio network adaptor as
      // the uplink bandwidth.
      // The BWE spikes should not affect the bitrate smoother more than 25%.
      // To simplify the calculations we use a step response as input signal.
      // The step response of an exponential filter is
      // u(t) = 1 - e^(-t / time_constant).
      // In order to limit the affect of a BWE spike within 25% of its value
      // before
      // the next BWE update, we would choose a time constant that fulfills
      // 1 - e^(-bwe_period_ms / time_constant) < 0.25
      // Then 4 * bwe_period_ms is a good choice.
      if (bwe_period_ms)
        bitrate_smoother_->SetTimeConstantMs(*bwe_period_ms * 4);
      bitrate_smoother_->AddSample(target_audio_bitrate_bps);
    }

    ApplyAudioNetworkAdaptor();
  } else {
    if (!overhead_bytes_per_packet_) {
      RTC_LOG(LS_INFO)
          << "AudioEncoderOpusImpl: Overhead unknown, target audio bitrate "
          << target_audio_bitrate_bps << " bps is ignored.";
      return;
    }
    const int overhead_bps = static_cast<int>(
        *overhead_bytes_per_packet_ * 8 * 100 / Num10MsFramesInNextPacket());
    SetTargetBitrate(
        std::min(AudioEncoderOpusConfig::kMaxBitrateBps,
                 std::max(AudioEncoderOpusConfig::kMinBitrateBps,
                          target_audio_bitrate_bps - overhead_bps)));
  }
}
void AudioEncoderOpusImpl::OnReceivedUplinkBandwidth(
    int target_audio_bitrate_bps,
    absl::optional<int64_t> bwe_period_ms) {
  OnReceivedUplinkBandwidth(target_audio_bitrate_bps, bwe_period_ms,
                            absl::nullopt);
}

void AudioEncoderOpusImpl::OnReceivedUplinkAllocation(
    BitrateAllocationUpdate update) {
  OnReceivedUplinkBandwidth(update.target_bitrate.bps(), update.bwe_period.ms(),
                            update.stable_target_bitrate.bps());
}

void AudioEncoderOpusImpl::OnReceivedRtt(int rtt_ms) {
  if (!audio_network_adaptor_)
    return;
  audio_network_adaptor_->SetRtt(rtt_ms);
  ApplyAudioNetworkAdaptor();
}

void AudioEncoderOpusImpl::OnReceivedOverhead(
    size_t overhead_bytes_per_packet) {
  if (audio_network_adaptor_) {
    audio_network_adaptor_->SetOverhead(overhead_bytes_per_packet);
    ApplyAudioNetworkAdaptor();
  } else {
    overhead_bytes_per_packet_ = overhead_bytes_per_packet;
  }
}

void AudioEncoderOpusImpl::SetReceiverFrameLengthRange(
    int min_frame_length_ms,
    int max_frame_length_ms) {
  // Ensure that `SetReceiverFrameLengthRange` is called before
  // `EnableAudioNetworkAdaptor`, otherwise we need to recreate
  // `audio_network_adaptor_`, which is not a needed use case.
  RTC_DCHECK(!audio_network_adaptor_);
  FindSupportedFrameLengths(min_frame_length_ms, max_frame_length_ms,
                            &config_.supported_frame_lengths_ms);
}

AudioEncoder::EncodedInfo AudioEncoderOpusImpl::EncodeImpl(
    uint32_t rtp_timestamp,
    rtc::ArrayView<const int16_t> audio,
    rtc::Buffer* encoded) {
  MaybeUpdateUplinkBandwidth();

  if (input_buffer_.empty())
    first_timestamp_in_buffer_ = rtp_timestamp;

  input_buffer_.insert(input_buffer_.end(), audio.cbegin(), audio.cend());
  if (input_buffer_.size() <
      (Num10msFramesPerPacket() * SamplesPer10msFrame())) {
    return EncodedInfo();
  }
  RTC_CHECK_EQ(input_buffer_.size(),
               Num10msFramesPerPacket() * SamplesPer10msFrame());

  const size_t max_encoded_bytes = SufficientOutputBufferSize();
  EncodedInfo info;
  info.encoded_bytes = encoded->AppendData(
      max_encoded_bytes, [&](rtc::ArrayView<uint8_t> encoded) {
        int status = WebRtcOpus_Encode(
            inst_, &input_buffer_[0],
            rtc::CheckedDivExact(input_buffer_.size(), config_.num_channels),
            rtc::saturated_cast<int16_t>(max_encoded_bytes), encoded.data());

        RTC_CHECK_GE(status, 0);  // Fails only if fed invalid data.

        return static_cast<size_t>(status);
      });
  input_buffer_.clear();

  bool dtx_frame = (info.encoded_bytes <= 2);

  // Will use new packet size for next encoding.
  config_.frame_size_ms = next_frame_length_ms_;

  if (adjust_bandwidth_ && bitrate_changed_) {
    const auto bandwidth = GetNewBandwidth(config_, inst_);
    if (bandwidth) {
      RTC_CHECK_EQ(0, WebRtcOpus_SetBandwidth(inst_, *bandwidth));
    }
    bitrate_changed_ = false;
  }

  info.encoded_timestamp = first_timestamp_in_buffer_;
  info.payload_type = payload_type_;
  info.send_even_if_empty = true;  // Allows Opus to send empty packets.
  // After 20 DTX frames (MAX_CONSECUTIVE_DTX) Opus will send a frame
  // coding the background noise. Avoid flagging this frame as speech
  // (even though there is a probability of the frame being speech).
  info.speech = !dtx_frame && (consecutive_dtx_frames_ != 20);
  info.encoder_type = CodecType::kOpus;

  // Increase or reset DTX counter.
  consecutive_dtx_frames_ = (dtx_frame) ? (consecutive_dtx_frames_ + 1) : (0);

  return info;
}

size_t AudioEncoderOpusImpl::Num10msFramesPerPacket() const {
  return static_cast<size_t>(rtc::CheckedDivExact(config_.frame_size_ms, 10));
}

size_t AudioEncoderOpusImpl::SamplesPer10msFrame() const {
  return rtc::CheckedDivExact(config_.sample_rate_hz, 100) *
         config_.num_channels;
}

size_t AudioEncoderOpusImpl::SufficientOutputBufferSize() const {
  // Calculate the number of bytes we expect the encoder to produce,
  // then multiply by two to give a wide margin for error.
  const size_t bytes_per_millisecond =
      static_cast<size_t>(GetBitrateBps(config_) / (1000 * 8) + 1);
  const size_t approx_encoded_bytes =
      Num10msFramesPerPacket() * 10 * bytes_per_millisecond;
  return 2 * approx_encoded_bytes;
}

// If the given config is OK, recreate the Opus encoder instance with those
// settings, save the config, and return true. Otherwise, do nothing and return
// false.
bool AudioEncoderOpusImpl::RecreateEncoderInstance(
    const AudioEncoderOpusConfig& config) {
  if (!config.IsOk())
    return false;
  config_ = config;
  if (inst_)
    RTC_CHECK_EQ(0, WebRtcOpus_EncoderFree(inst_));
  input_buffer_.clear();
  input_buffer_.reserve(Num10msFramesPerPacket() * SamplesPer10msFrame());
  RTC_CHECK_EQ(0, WebRtcOpus_EncoderCreate(
                      &inst_, config.num_channels,
                      config.application ==
                              AudioEncoderOpusConfig::ApplicationMode::kVoip
                          ? 0
                          : 1,
                      config.sample_rate_hz));
  const int bitrate = GetBitrateBps(config);
  RTC_CHECK_EQ(0, WebRtcOpus_SetBitRate(inst_, bitrate));
  RTC_LOG(LS_VERBOSE) << "Set Opus bitrate to " << bitrate << " bps.";
  if (config.fec_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableFec(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableFec(inst_));
  }
  RTC_CHECK_EQ(
      0, WebRtcOpus_SetMaxPlaybackRate(inst_, config.max_playback_rate_hz));
  // Use the default complexity if the start bitrate is within the hysteresis
  // window.
  complexity_ = GetNewComplexity(config).value_or(config.complexity);
  RTC_CHECK_EQ(0, WebRtcOpus_SetComplexity(inst_, complexity_));
  bitrate_changed_ = true;
  if (config.dtx_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableDtx(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableDtx(inst_));
  }
  RTC_CHECK_EQ(0,
               WebRtcOpus_SetPacketLossRate(
                   inst_, static_cast<int32_t>(packet_loss_rate_ * 100 + .5)));
  if (config.cbr_enabled) {
    RTC_CHECK_EQ(0, WebRtcOpus_EnableCbr(inst_));
  } else {
    RTC_CHECK_EQ(0, WebRtcOpus_DisableCbr(inst_));
  }
  num_channels_to_encode_ = NumChannels();
  next_frame_length_ms_ = config_.frame_size_ms;
  return true;
}

void AudioEncoderOpusImpl::SetFrameLength(int frame_length_ms) {
  if (next_frame_length_ms_ != frame_length_ms) {
    RTC_LOG(LS_VERBOSE) << "Update Opus frame length "
                        << "from " << next_frame_length_ms_ << " ms "
                        << "to " << frame_length_ms << " ms.";
  }
  next_frame_length_ms_ = frame_length_ms;
}

void AudioEncoderOpusImpl::SetNumChannelsToEncode(
    size_t num_channels_to_encode) {
  RTC_DCHECK_GT(num_channels_to_encode, 0);
  RTC_DCHECK_LE(num_channels_to_encode, config_.num_channels);

  if (num_channels_to_encode_ == num_channels_to_encode)
    return;

  RTC_CHECK_EQ(0, WebRtcOpus_SetForceChannels(inst_, num_channels_to_encode));
  num_channels_to_encode_ = num_channels_to_encode;
}

void AudioEncoderOpusImpl::SetProjectedPacketLossRate(float fraction) {
  fraction = std::min(std::max(fraction, 0.0f), kMaxPacketLossFraction);
  if (packet_loss_rate_ != fraction) {
    packet_loss_rate_ = fraction;
    RTC_CHECK_EQ(
        0, WebRtcOpus_SetPacketLossRate(
               inst_, static_cast<int32_t>(packet_loss_rate_ * 100 + .5)));
  }
}

void AudioEncoderOpusImpl::SetTargetBitrate(int bits_per_second) {
  const int new_bitrate = rtc::SafeClamp<int>(
      bits_per_second, AudioEncoderOpusConfig::kMinBitrateBps,
      AudioEncoderOpusConfig::kMaxBitrateBps);
  if (config_.bitrate_bps && *config_.bitrate_bps != new_bitrate) {
    config_.bitrate_bps = new_bitrate;
    RTC_DCHECK(config_.IsOk());
    const int bitrate = GetBitrateBps(config_);
    RTC_CHECK_EQ(
        0, WebRtcOpus_SetBitRate(
               inst_, GetMultipliedBitrate(bitrate, bitrate_multipliers_)));
    RTC_LOG(LS_VERBOSE) << "Set Opus bitrate to " << bitrate << " bps.";
    bitrate_changed_ = true;
  }

  const auto new_complexity = GetNewComplexity(config_);
  if (new_complexity && complexity_ != *new_complexity) {
    complexity_ = *new_complexity;
    RTC_CHECK_EQ(0, WebRtcOpus_SetComplexity(inst_, complexity_));
  }
}

void AudioEncoderOpusImpl::ApplyAudioNetworkAdaptor() {
  auto config = audio_network_adaptor_->GetEncoderRuntimeConfig();

  if (config.bitrate_bps)
    SetTargetBitrate(*config.bitrate_bps);
  if (config.frame_length_ms)
    SetFrameLength(*config.frame_length_ms);
  if (config.enable_dtx)
    SetDtx(*config.enable_dtx);
  if (config.num_channels)
    SetNumChannelsToEncode(*config.num_channels);
}

std::unique_ptr<AudioNetworkAdaptor>
AudioEncoderOpusImpl::DefaultAudioNetworkAdaptorCreator(
    absl::string_view config_string,
    RtcEventLog* event_log) const {
  AudioNetworkAdaptorImpl::Config config;
  config.event_log = event_log;
  return std::unique_ptr<AudioNetworkAdaptor>(new AudioNetworkAdaptorImpl(
      config, ControllerManagerImpl::Create(
                  config_string, NumChannels(), supported_frame_lengths_ms(),
                  AudioEncoderOpusConfig::kMinBitrateBps,
                  num_channels_to_encode_, next_frame_length_ms_,
                  GetTargetBitrate(), config_.fec_enabled, GetDtx())));
}

void AudioEncoderOpusImpl::MaybeUpdateUplinkBandwidth() {
  if (audio_network_adaptor_ && !use_stable_target_for_adaptation_) {
    int64_t now_ms = rtc::TimeMillis();
    if (!bitrate_smoother_last_update_time_ ||
        now_ms - *bitrate_smoother_last_update_time_ >=
            config_.uplink_bandwidth_update_interval_ms) {
      absl::optional<float> smoothed_bitrate = bitrate_smoother_->GetAverage();
      if (smoothed_bitrate)
        audio_network_adaptor_->SetUplinkBandwidth(*smoothed_bitrate);
      bitrate_smoother_last_update_time_ = now_ms;
    }
  }
}

ANAStats AudioEncoderOpusImpl::GetANAStats() const {
  if (audio_network_adaptor_) {
    return audio_network_adaptor_->GetStats();
  }
  return ANAStats();
}

absl::optional<std::pair<TimeDelta, TimeDelta> >
AudioEncoderOpusImpl::GetFrameLengthRange() const {
  if (audio_network_adaptor_) {
    if (config_.supported_frame_lengths_ms.empty()) {
      return absl::nullopt;
    }
    return {{TimeDelta::Millis(config_.supported_frame_lengths_ms.front()),
             TimeDelta::Millis(config_.supported_frame_lengths_ms.back())}};
  } else {
    return {{TimeDelta::Millis(config_.frame_size_ms),
             TimeDelta::Millis(config_.frame_size_ms)}};
  }
}

}  // namespace webrtc
