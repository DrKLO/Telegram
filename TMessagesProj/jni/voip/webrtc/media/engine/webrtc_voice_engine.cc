/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/webrtc_voice_engine.h"

#include <algorithm>
#include <atomic>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "api/audio/audio_frame_processor.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/call/audio_sink.h"
#include "api/field_trials_view.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "media/base/audio_source.h"
#include "media/base/media_constants.h"
#include "media/base/stream_params.h"
#include "media/engine/adm_helpers.h"
#include "media/engine/payload_type_mapper.h"
#include "media/engine/webrtc_media_engine.h"
#include "modules/async_audio_processing/async_audio_processing.h"
#include "modules/audio_device/audio_device_impl.h"
#include "modules/audio_mixer/audio_mixer_impl.h"
#include "modules/audio_processing/aec_dump/aec_dump_factory.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/field_trial_units.h"
#include "rtc_base/experiments/struct_parameters_parser.h"
#include "rtc_base/helpers.h"
#include "rtc_base/ignore_wundef.h"
#include "rtc_base/logging.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/strings/audio_format_to_string.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/strings/string_format.h"
#include "rtc_base/third_party/base64/base64.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/metrics.h"

#if WEBRTC_ENABLE_PROTOBUF
RTC_PUSH_IGNORING_WUNDEF()
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_coding/audio_network_adaptor/config.pb.h"
#else
#include "modules/audio_coding/audio_network_adaptor/config.pb.h"
#endif
RTC_POP_IGNORING_WUNDEF()
#endif

namespace cricket {
namespace {

using ::webrtc::ParseRtpSsrc;

constexpr size_t kMaxUnsignaledRecvStreams = 4;

constexpr int kNackRtpHistoryMs = 5000;

const int kMinTelephoneEventCode = 0;  // RFC4733 (Section 2.3.1)
const int kMaxTelephoneEventCode = 255;

const int kMinPayloadType = 0;
const int kMaxPayloadType = 127;

class ProxySink : public webrtc::AudioSinkInterface {
 public:
  explicit ProxySink(AudioSinkInterface* sink) : sink_(sink) {
    RTC_DCHECK(sink);
  }

  void OnData(const Data& audio) override { sink_->OnData(audio); }

 private:
  webrtc::AudioSinkInterface* sink_;
};

bool ValidateStreamParams(const StreamParams& sp) {
  if (sp.ssrcs.empty()) {
    RTC_DLOG(LS_ERROR) << "No SSRCs in stream parameters: " << sp.ToString();
    return false;
  }
  if (sp.ssrcs.size() > 1) {
    RTC_DLOG(LS_ERROR) << "Multiple SSRCs in stream parameters: "
                       << sp.ToString();
    return false;
  }
  return true;
}

// Dumps an AudioCodec in RFC 2327-ish format.
std::string ToString(const AudioCodec& codec) {
  rtc::StringBuilder ss;
  ss << codec.name << "/" << codec.clockrate << "/" << codec.channels;
  if (!codec.params.empty()) {
    ss << " {";
    for (const auto& param : codec.params) {
      ss << " " << param.first << "=" << param.second;
    }
    ss << " }";
  }
  ss << " (" << codec.id << ")";
  return ss.Release();
}

bool IsCodec(const AudioCodec& codec, const char* ref_name) {
  return absl::EqualsIgnoreCase(codec.name, ref_name);
}

bool FindCodec(const std::vector<AudioCodec>& codecs,
               const AudioCodec& codec,
               AudioCodec* found_codec,
               const webrtc::FieldTrialsView* field_trials) {
  for (const AudioCodec& c : codecs) {
    if (c.Matches(codec, field_trials)) {
      if (found_codec != NULL) {
        *found_codec = c;
      }
      return true;
    }
  }
  return false;
}

bool VerifyUniquePayloadTypes(const std::vector<AudioCodec>& codecs) {
  if (codecs.empty()) {
    return true;
  }
  std::vector<int> payload_types;
  absl::c_transform(codecs, std::back_inserter(payload_types),
                    [](const AudioCodec& codec) { return codec.id; });
  absl::c_sort(payload_types);
  return absl::c_adjacent_find(payload_types) == payload_types.end();
}

absl::optional<std::string> GetAudioNetworkAdaptorConfig(
    const AudioOptions& options) {
  if (options.audio_network_adaptor && *options.audio_network_adaptor &&
      options.audio_network_adaptor_config) {
    // Turn on audio network adaptor only when `options_.audio_network_adaptor`
    // equals true and `options_.audio_network_adaptor_config` has a value.
    return options.audio_network_adaptor_config;
  }
  return absl::nullopt;
}

// Returns its smallest positive argument. If neither argument is positive,
// returns an arbitrary nonpositive value.
int MinPositive(int a, int b) {
  if (a <= 0) {
    return b;
  }
  if (b <= 0) {
    return a;
  }
  return std::min(a, b);
}

// `max_send_bitrate_bps` is the bitrate from "b=" in SDP.
// `rtp_max_bitrate_bps` is the bitrate from RtpSender::SetParameters.
absl::optional<int> ComputeSendBitrate(int max_send_bitrate_bps,
                                       absl::optional<int> rtp_max_bitrate_bps,
                                       const webrtc::AudioCodecSpec& spec) {
  // If application-configured bitrate is set, take minimum of that and SDP
  // bitrate.
  const int bps = rtp_max_bitrate_bps
                      ? MinPositive(max_send_bitrate_bps, *rtp_max_bitrate_bps)
                      : max_send_bitrate_bps;
  if (bps <= 0) {
    return spec.info.default_bitrate_bps;
  }

  if (bps < spec.info.min_bitrate_bps) {
    // If codec is not multi-rate and `bps` is less than the fixed bitrate then
    // fail. If codec is not multi-rate and `bps` exceeds or equal the fixed
    // bitrate then ignore.
    RTC_LOG(LS_ERROR) << "Failed to set codec " << spec.format.name
                      << " to bitrate " << bps
                      << " bps"
                         ", requires at least "
                      << spec.info.min_bitrate_bps << " bps.";
    return absl::nullopt;
  }

  if (spec.info.HasFixedBitrate()) {
    return spec.info.default_bitrate_bps;
  } else {
    // If codec is multi-rate then just set the bitrate.
    return std::min(bps, spec.info.max_bitrate_bps);
  }
}

bool IsEnabled(const webrtc::FieldTrialsView& config, absl::string_view trial) {
  return absl::StartsWith(config.Lookup(trial), "Enabled");
}

struct AdaptivePtimeConfig {
  bool enabled = false;
  webrtc::DataRate min_payload_bitrate = webrtc::DataRate::KilobitsPerSec(16);
  // Value is chosen to ensure FEC can be encoded, see LBRR_WB_MIN_RATE_BPS in
  // libopus.
  webrtc::DataRate min_encoder_bitrate = webrtc::DataRate::KilobitsPerSec(16);
  bool use_slow_adaptation = true;

  absl::optional<std::string> audio_network_adaptor_config;

  std::unique_ptr<webrtc::StructParametersParser> Parser() {
    return webrtc::StructParametersParser::Create(    //
        "enabled", &enabled,                          //
        "min_payload_bitrate", &min_payload_bitrate,  //
        "min_encoder_bitrate", &min_encoder_bitrate,  //
        "use_slow_adaptation", &use_slow_adaptation);
  }

  explicit AdaptivePtimeConfig(const webrtc::FieldTrialsView& trials) {
    Parser()->Parse(trials.Lookup("WebRTC-Audio-AdaptivePtime"));
#if WEBRTC_ENABLE_PROTOBUF
    webrtc::audio_network_adaptor::config::ControllerManager config;
    auto* frame_length_controller =
        config.add_controllers()->mutable_frame_length_controller_v2();
    frame_length_controller->set_min_payload_bitrate_bps(
        min_payload_bitrate.bps());
    frame_length_controller->set_use_slow_adaptation(use_slow_adaptation);
    config.add_controllers()->mutable_bitrate_controller();
    audio_network_adaptor_config = config.SerializeAsString();
#endif
  }
};

// TODO(tommi): Constructing a receive stream could be made simpler.
// Move some of this boiler plate code into the config structs themselves.
webrtc::AudioReceiveStreamInterface::Config BuildReceiveStreamConfig(
    uint32_t remote_ssrc,
    uint32_t local_ssrc,
    bool use_transport_cc,
    bool use_nack,
    bool enable_non_sender_rtt,
    const std::vector<std::string>& stream_ids,
    const std::vector<webrtc::RtpExtension>& extensions,
    webrtc::Transport* rtcp_send_transport,
    const rtc::scoped_refptr<webrtc::AudioDecoderFactory>& decoder_factory,
    const std::map<int, webrtc::SdpAudioFormat>& decoder_map,
    absl::optional<webrtc::AudioCodecPairId> codec_pair_id,
    size_t jitter_buffer_max_packets,
    bool jitter_buffer_fast_accelerate,
    int jitter_buffer_min_delay_ms,
    rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor,
    const webrtc::CryptoOptions& crypto_options,
    rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
  webrtc::AudioReceiveStreamInterface::Config config;
  config.rtp.remote_ssrc = remote_ssrc;
  config.rtp.local_ssrc = local_ssrc;
  config.rtp.transport_cc = use_transport_cc;
  config.rtp.nack.rtp_history_ms = use_nack ? kNackRtpHistoryMs : 0;
  if (!stream_ids.empty()) {
    config.sync_group = stream_ids[0];
  }
  config.rtp.extensions = extensions;
  config.rtcp_send_transport = rtcp_send_transport;
  config.enable_non_sender_rtt = enable_non_sender_rtt;
  config.decoder_factory = decoder_factory;
  config.decoder_map = decoder_map;
  config.codec_pair_id = codec_pair_id;
  config.jitter_buffer_max_packets = jitter_buffer_max_packets;
  config.jitter_buffer_fast_accelerate = jitter_buffer_fast_accelerate;
  config.jitter_buffer_min_delay_ms = jitter_buffer_min_delay_ms;
  config.frame_decryptor = std::move(frame_decryptor);
  config.crypto_options = crypto_options;
  config.frame_transformer = std::move(frame_transformer);
  return config;
}

}  // namespace

WebRtcVoiceEngine::WebRtcVoiceEngine(
    webrtc::TaskQueueFactory* task_queue_factory,
    webrtc::AudioDeviceModule* adm,
    const rtc::scoped_refptr<webrtc::AudioEncoderFactory>& encoder_factory,
    const rtc::scoped_refptr<webrtc::AudioDecoderFactory>& decoder_factory,
    rtc::scoped_refptr<webrtc::AudioMixer> audio_mixer,
    rtc::scoped_refptr<webrtc::AudioProcessing> audio_processing,
    webrtc::AudioFrameProcessor* audio_frame_processor,
    const webrtc::FieldTrialsView& trials)
    : task_queue_factory_(task_queue_factory),
      adm_(adm),
      encoder_factory_(encoder_factory),
      decoder_factory_(decoder_factory),
      audio_mixer_(audio_mixer),
      apm_(audio_processing),
      audio_frame_processor_(audio_frame_processor),
      minimized_remsampling_on_mobile_trial_enabled_(
          IsEnabled(trials, "WebRTC-Audio-MinimizeResamplingOnMobile")) {
  // This may be called from any thread, so detach thread checkers.
  worker_thread_checker_.Detach();
  signal_thread_checker_.Detach();
  RTC_LOG(LS_INFO) << "WebRtcVoiceEngine::WebRtcVoiceEngine";
  RTC_DCHECK(decoder_factory);
  RTC_DCHECK(encoder_factory);
  // The rest of our initialization will happen in Init.
}

WebRtcVoiceEngine::~WebRtcVoiceEngine() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceEngine::~WebRtcVoiceEngine";
  if (initialized_) {
    StopAecDump();

    // Stop AudioDevice.
    adm()->StopPlayout();
    adm()->StopRecording();
    adm()->RegisterAudioCallback(nullptr);
    adm()->Terminate();
  }
}

void WebRtcVoiceEngine::Init() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceEngine::Init";

  // TaskQueue expects to be created/destroyed on the same thread.
  RTC_DCHECK(!low_priority_worker_queue_);
  low_priority_worker_queue_.reset(
      new rtc::TaskQueue(task_queue_factory_->CreateTaskQueue(
          "rtc-low-prio", webrtc::TaskQueueFactory::Priority::LOW)));

  // Load our audio codec lists.
  RTC_LOG(LS_VERBOSE) << "Supported send codecs in order of preference:";
  send_codecs_ = CollectCodecs(encoder_factory_->GetSupportedEncoders());
  for (const AudioCodec& codec : send_codecs_) {
    RTC_LOG(LS_VERBOSE) << ToString(codec);
  }

  RTC_LOG(LS_VERBOSE) << "Supported recv codecs in order of preference:";
  recv_codecs_ = CollectCodecs(decoder_factory_->GetSupportedDecoders());
  for (const AudioCodec& codec : recv_codecs_) {
    RTC_LOG(LS_VERBOSE) << ToString(codec);
  }

#if defined(WEBRTC_INCLUDE_INTERNAL_AUDIO_DEVICE)
  // No ADM supplied? Create a default one.
  if (!adm_) {
    adm_ = webrtc::AudioDeviceModule::Create(
        webrtc::AudioDeviceModule::kPlatformDefaultAudio, task_queue_factory_);
  }
#endif  // WEBRTC_INCLUDE_INTERNAL_AUDIO_DEVICE
  RTC_CHECK(adm());
  webrtc::adm_helpers::Init(adm());

  // Set up AudioState.
  {
    webrtc::AudioState::Config config;
    if (audio_mixer_) {
      config.audio_mixer = audio_mixer_;
    } else {
      config.audio_mixer = webrtc::AudioMixerImpl::Create();
    }
    config.audio_processing = apm_;
    config.audio_device_module = adm_;
    if (audio_frame_processor_)
      config.async_audio_processing_factory =
          rtc::make_ref_counted<webrtc::AsyncAudioProcessing::Factory>(
              *audio_frame_processor_, *task_queue_factory_);
    audio_state_ = webrtc::AudioState::Create(config);
  }

  // Connect the ADM to our audio path.
  adm()->RegisterAudioCallback(audio_state()->audio_transport());

  // Set default engine options.
  {
    AudioOptions options;
    options.echo_cancellation = true;
    options.auto_gain_control = true;
#if defined(WEBRTC_IOS)
    // On iOS, VPIO provides built-in NS.
    options.noise_suppression = false;
#else
    options.noise_suppression = true;
#endif
    options.highpass_filter = true;
    options.stereo_swapping = false;
    options.audio_jitter_buffer_max_packets = 200;
    options.audio_jitter_buffer_fast_accelerate = false;
    options.audio_jitter_buffer_min_delay_ms = 0;
    ApplyOptions(options);
  }
  initialized_ = true;
}

rtc::scoped_refptr<webrtc::AudioState> WebRtcVoiceEngine::GetAudioState()
    const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return audio_state_;
}

VoiceMediaChannel* WebRtcVoiceEngine::CreateMediaChannel(
    webrtc::Call* call,
    const MediaConfig& config,
    const AudioOptions& options,
    const webrtc::CryptoOptions& crypto_options) {
  RTC_DCHECK_RUN_ON(call->worker_thread());
  return new WebRtcVoiceMediaChannel(this, config, options, crypto_options,
                                     call);
}

void WebRtcVoiceEngine::ApplyOptions(const AudioOptions& options_in) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceEngine::ApplyOptions: "
                   << options_in.ToString();
  AudioOptions options = options_in;  // The options are modified below.

  // Set and adjust echo canceller options.
  // Use desktop AEC by default, when not using hardware AEC.
  bool use_mobile_software_aec = false;

#if defined(WEBRTC_IOS)
  if (options.ios_force_software_aec_HACK &&
      *options.ios_force_software_aec_HACK) {
    // EC may be forced on for a device known to have non-functioning platform
    // AEC.
    options.echo_cancellation = true;
    RTC_LOG(LS_WARNING)
        << "Force software AEC on iOS. May conflict with platform AEC.";
  } else {
    // On iOS, VPIO provides built-in EC.
    options.echo_cancellation = false;
    RTC_LOG(LS_INFO) << "Always disable AEC on iOS. Use built-in instead.";
  }
#elif defined(WEBRTC_ANDROID)
  use_mobile_software_aec = true;
#endif

// Set and adjust gain control options.
#if defined(WEBRTC_IOS)
  // On iOS, VPIO provides built-in AGC.
  options.auto_gain_control = false;
  RTC_LOG(LS_INFO) << "Always disable AGC on iOS. Use built-in instead.";
#endif

#if defined(WEBRTC_IOS) || defined(WEBRTC_ANDROID)
  // Turn off the gain control if specified by the field trial.
  // The purpose of the field trial is to reduce the amount of resampling
  // performed inside the audio processing module on mobile platforms by
  // whenever possible turning off the fixed AGC mode and the high-pass filter.
  // (https://bugs.chromium.org/p/webrtc/issues/detail?id=6181).
  if (minimized_remsampling_on_mobile_trial_enabled_) {
    options.auto_gain_control = false;
    RTC_LOG(LS_INFO) << "Disable AGC according to field trial.";
    if (!(options.noise_suppression.value_or(false) ||
          options.echo_cancellation.value_or(false))) {
      // If possible, turn off the high-pass filter.
      RTC_LOG(LS_INFO)
          << "Disable high-pass filter in response to field trial.";
      options.highpass_filter = false;
    }
  }
#endif

  if (options.echo_cancellation) {
    // Check if platform supports built-in EC. Currently only supported on
    // Android and in combination with Java based audio layer.
    // TODO(henrika): investigate possibility to support built-in EC also
    // in combination with Open SL ES audio.
    const bool built_in_aec = adm()->BuiltInAECIsAvailable();
    if (built_in_aec) {
      // Built-in EC exists on this device. Enable/Disable it according to the
      // echo_cancellation audio option.
      const bool enable_built_in_aec = *options.echo_cancellation;
      if (adm()->EnableBuiltInAEC(enable_built_in_aec) == 0 &&
          enable_built_in_aec) {
        // Disable internal software EC if built-in EC is enabled,
        // i.e., replace the software EC with the built-in EC.
        options.echo_cancellation = false;
        RTC_LOG(LS_INFO)
            << "Disabling EC since built-in EC will be used instead";
      }
    }
  }

  if (options.auto_gain_control) {
    bool built_in_agc_avaliable = adm()->BuiltInAGCIsAvailable();
    if (built_in_agc_avaliable) {
      if (adm()->EnableBuiltInAGC(*options.auto_gain_control) == 0 &&
          *options.auto_gain_control) {
        // Disable internal software AGC if built-in AGC is enabled,
        // i.e., replace the software AGC with the built-in AGC.
        options.auto_gain_control = false;
        RTC_LOG(LS_INFO)
            << "Disabling AGC since built-in AGC will be used instead";
      }
    }
  }

  if (options.noise_suppression) {
    if (adm()->BuiltInNSIsAvailable()) {
      bool builtin_ns = *options.noise_suppression;
      if (adm()->EnableBuiltInNS(builtin_ns) == 0 && builtin_ns) {
        // Disable internal software NS if built-in NS is enabled,
        // i.e., replace the software NS with the built-in NS.
        options.noise_suppression = false;
        RTC_LOG(LS_INFO)
            << "Disabling NS since built-in NS will be used instead";
      }
    }
  }

  if (options.stereo_swapping) {
    audio_state()->SetStereoChannelSwapping(*options.stereo_swapping);
  }

  if (options.audio_jitter_buffer_max_packets) {
    audio_jitter_buffer_max_packets_ =
        std::max(20, *options.audio_jitter_buffer_max_packets);
  }
  if (options.audio_jitter_buffer_fast_accelerate) {
    audio_jitter_buffer_fast_accelerate_ =
        *options.audio_jitter_buffer_fast_accelerate;
  }
  if (options.audio_jitter_buffer_min_delay_ms) {
    audio_jitter_buffer_min_delay_ms_ =
        *options.audio_jitter_buffer_min_delay_ms;
  }

  webrtc::AudioProcessing* ap = apm();
  if (!ap) {
    return;
  }

  webrtc::AudioProcessing::Config apm_config = ap->GetConfig();

  if (options.echo_cancellation) {
    apm_config.echo_canceller.enabled = *options.echo_cancellation;
    apm_config.echo_canceller.mobile_mode = use_mobile_software_aec;
  }

  if (options.auto_gain_control) {
    const bool enabled = *options.auto_gain_control;
    apm_config.gain_controller1.enabled = enabled;
#if defined(WEBRTC_IOS) || defined(WEBRTC_ANDROID)
    apm_config.gain_controller1.mode =
        apm_config.gain_controller1.kFixedDigital;
#else
    apm_config.gain_controller1.mode =
        apm_config.gain_controller1.kAdaptiveAnalog;
#endif
  }

  if (options.highpass_filter) {
    apm_config.high_pass_filter.enabled = *options.highpass_filter;
  }

  if (options.noise_suppression) {
    const bool enabled = *options.noise_suppression;
    apm_config.noise_suppression.enabled = enabled;
    apm_config.noise_suppression.level =
        webrtc::AudioProcessing::Config::NoiseSuppression::Level::kHigh;
  }

  ap->ApplyConfig(apm_config);
}

const std::vector<AudioCodec>& WebRtcVoiceEngine::send_codecs() const {
  RTC_DCHECK(signal_thread_checker_.IsCurrent());
  return send_codecs_;
}

const std::vector<AudioCodec>& WebRtcVoiceEngine::recv_codecs() const {
  RTC_DCHECK(signal_thread_checker_.IsCurrent());
  return recv_codecs_;
}

std::vector<webrtc::RtpHeaderExtensionCapability>
WebRtcVoiceEngine::GetRtpHeaderExtensions() const {
  RTC_DCHECK(signal_thread_checker_.IsCurrent());
  std::vector<webrtc::RtpHeaderExtensionCapability> result;
  int id = 1;
  for (const auto& uri : {webrtc::RtpExtension::kAudioLevelUri,
                          webrtc::RtpExtension::kAbsSendTimeUri,
                          webrtc::RtpExtension::kTransportSequenceNumberUri,
                          webrtc::RtpExtension::kMidUri}) {
    result.emplace_back(uri, id++, webrtc::RtpTransceiverDirection::kSendRecv);
  }
  return result;
}

bool WebRtcVoiceEngine::StartAecDump(webrtc::FileWrapper file,
                                     int64_t max_size_bytes) {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);

  webrtc::AudioProcessing* ap = apm();
  if (!ap) {
    RTC_LOG(LS_WARNING)
        << "Attempting to start aecdump when no audio processing module is "
           "present, hence no aecdump is started.";
    return false;
  }

  return ap->CreateAndAttachAecDump(file.Release(), max_size_bytes,
                                    low_priority_worker_queue_.get());
}

void WebRtcVoiceEngine::StopAecDump() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  webrtc::AudioProcessing* ap = apm();
  if (ap) {
    ap->DetachAecDump();
  } else {
    RTC_LOG(LS_WARNING) << "Attempting to stop aecdump when no audio "
                           "processing module is present";
  }
}

webrtc::AudioDeviceModule* WebRtcVoiceEngine::adm() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_DCHECK(adm_);
  return adm_.get();
}

webrtc::AudioProcessing* WebRtcVoiceEngine::apm() const {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  return apm_.get();
}

webrtc::AudioState* WebRtcVoiceEngine::audio_state() {
  RTC_DCHECK_RUN_ON(&worker_thread_checker_);
  RTC_DCHECK(audio_state_);
  return audio_state_.get();
}

std::vector<AudioCodec> WebRtcVoiceEngine::CollectCodecs(
    const std::vector<webrtc::AudioCodecSpec>& specs) const {
  PayloadTypeMapper mapper;
  std::vector<AudioCodec> out;

  // Only generate CN payload types for these clockrates:
  std::map<int, bool, std::greater<int>> generate_cn = {
      {8000, false}, {16000, false}, {32000, false}};
  // Only generate telephone-event payload types for these clockrates:
  std::map<int, bool, std::greater<int>> generate_dtmf = {
      {8000, false}, {16000, false}, {32000, false}, {48000, false}};

  auto map_format = [&mapper](const webrtc::SdpAudioFormat& format,
                              std::vector<AudioCodec>* out) {
    absl::optional<AudioCodec> opt_codec = mapper.ToAudioCodec(format);
    if (opt_codec) {
      if (out) {
        out->push_back(*opt_codec);
      }
    } else {
      RTC_LOG(LS_ERROR) << "Unable to assign payload type to format: "
                        << rtc::ToString(format);
    }

    return opt_codec;
  };

  for (const auto& spec : specs) {
    // We need to do some extra stuff before adding the main codecs to out.
    absl::optional<AudioCodec> opt_codec = map_format(spec.format, nullptr);
    if (opt_codec) {
      AudioCodec& codec = *opt_codec;
      if (spec.info.supports_network_adaption) {
        codec.AddFeedbackParam(
            FeedbackParam(kRtcpFbParamTransportCc, kParamValueEmpty));
      }

      if (spec.info.allow_comfort_noise) {
        // Generate a CN entry if the decoder allows it and we support the
        // clockrate.
        auto cn = generate_cn.find(spec.format.clockrate_hz);
        if (cn != generate_cn.end()) {
          cn->second = true;
        }
      }

      // Generate a telephone-event entry if we support the clockrate.
      auto dtmf = generate_dtmf.find(spec.format.clockrate_hz);
      if (dtmf != generate_dtmf.end()) {
        dtmf->second = true;
      }

      out.push_back(codec);

      if (codec.name == kOpusCodecName) {
        std::string redFmtp =
            rtc::ToString(codec.id) + "/" + rtc::ToString(codec.id);
        map_format({kRedCodecName, 48000, 2, {{"", redFmtp}}}, &out);
      }
    }
  }

  // Add CN codecs after "proper" audio codecs.
  for (const auto& cn : generate_cn) {
    if (cn.second) {
      map_format({kCnCodecName, cn.first, 1}, &out);
    }
  }

  // Add telephone-event codecs last.
  for (const auto& dtmf : generate_dtmf) {
    if (dtmf.second) {
      map_format({kDtmfCodecName, dtmf.first, 1}, &out);
    }
  }

  return out;
}

class WebRtcVoiceMediaChannel::WebRtcAudioSendStream
    : public AudioSource::Sink {
 public:
  WebRtcAudioSendStream(
      uint32_t ssrc,
      const std::string& mid,
      const std::string& c_name,
      const std::string track_id,
      const absl::optional<webrtc::AudioSendStream::Config::SendCodecSpec>&
          send_codec_spec,
      bool extmap_allow_mixed,
      const std::vector<webrtc::RtpExtension>& extensions,
      int max_send_bitrate_bps,
      int rtcp_report_interval_ms,
      const absl::optional<std::string>& audio_network_adaptor_config,
      webrtc::Call* call,
      webrtc::Transport* send_transport,
      const rtc::scoped_refptr<webrtc::AudioEncoderFactory>& encoder_factory,
      const absl::optional<webrtc::AudioCodecPairId> codec_pair_id,
      rtc::scoped_refptr<webrtc::FrameEncryptorInterface> frame_encryptor,
      const webrtc::CryptoOptions& crypto_options)
      : adaptive_ptime_config_(call->trials()),
        call_(call),
        config_(send_transport),
        max_send_bitrate_bps_(max_send_bitrate_bps),
        rtp_parameters_(CreateRtpParametersWithOneEncoding()) {
    RTC_DCHECK(call);
    RTC_DCHECK(encoder_factory);
    config_.rtp.ssrc = ssrc;
    config_.rtp.mid = mid;
    config_.rtp.c_name = c_name;
    config_.rtp.extmap_allow_mixed = extmap_allow_mixed;
    config_.rtp.extensions = extensions;
    config_.has_dscp =
        rtp_parameters_.encodings[0].network_priority != webrtc::Priority::kLow;
    config_.encoder_factory = encoder_factory;
    config_.codec_pair_id = codec_pair_id;
    config_.track_id = track_id;
    config_.frame_encryptor = frame_encryptor;
    config_.crypto_options = crypto_options;
    config_.rtcp_report_interval_ms = rtcp_report_interval_ms;
    rtp_parameters_.encodings[0].ssrc = ssrc;
    rtp_parameters_.rtcp.cname = c_name;
    rtp_parameters_.header_extensions = extensions;

    audio_network_adaptor_config_from_options_ = audio_network_adaptor_config;
    UpdateAudioNetworkAdaptorConfig();

    if (send_codec_spec) {
      UpdateSendCodecSpec(*send_codec_spec);
    }

    stream_ = call_->CreateAudioSendStream(config_);
  }

  WebRtcAudioSendStream() = delete;
  WebRtcAudioSendStream(const WebRtcAudioSendStream&) = delete;
  WebRtcAudioSendStream& operator=(const WebRtcAudioSendStream&) = delete;

  ~WebRtcAudioSendStream() override {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    ClearSource();
    call_->DestroyAudioSendStream(stream_);
  }

  void SetSendCodecSpec(
      const webrtc::AudioSendStream::Config::SendCodecSpec& send_codec_spec) {
    UpdateSendCodecSpec(send_codec_spec);
    ReconfigureAudioSendStream();
  }

  void SetRtpExtensions(const std::vector<webrtc::RtpExtension>& extensions) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    config_.rtp.extensions = extensions;
    rtp_parameters_.header_extensions = extensions;
    ReconfigureAudioSendStream();
  }

  void SetExtmapAllowMixed(bool extmap_allow_mixed) {
    config_.rtp.extmap_allow_mixed = extmap_allow_mixed;
    ReconfigureAudioSendStream();
  }

  void SetMid(const std::string& mid) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (config_.rtp.mid == mid) {
      return;
    }
    config_.rtp.mid = mid;
    ReconfigureAudioSendStream();
  }

  void SetFrameEncryptor(
      rtc::scoped_refptr<webrtc::FrameEncryptorInterface> frame_encryptor) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    config_.frame_encryptor = frame_encryptor;
    ReconfigureAudioSendStream();
  }

  void SetAudioNetworkAdaptorConfig(
      const absl::optional<std::string>& audio_network_adaptor_config) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (audio_network_adaptor_config_from_options_ ==
        audio_network_adaptor_config) {
      return;
    }
    audio_network_adaptor_config_from_options_ = audio_network_adaptor_config;
    UpdateAudioNetworkAdaptorConfig();
    UpdateAllowedBitrateRange();
    ReconfigureAudioSendStream();
  }

  bool SetMaxSendBitrate(int bps) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(config_.send_codec_spec);
    RTC_DCHECK(audio_codec_spec_);
    auto send_rate = ComputeSendBitrate(
        bps, rtp_parameters_.encodings[0].max_bitrate_bps, *audio_codec_spec_);

    if (!send_rate) {
      return false;
    }

    max_send_bitrate_bps_ = bps;

    if (send_rate != config_.send_codec_spec->target_bitrate_bps) {
      config_.send_codec_spec->target_bitrate_bps = send_rate;
      ReconfigureAudioSendStream();
    }
    return true;
  }

  bool SendTelephoneEvent(int payload_type,
                          int payload_freq,
                          int event,
                          int duration_ms) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(stream_);
    return stream_->SendTelephoneEvent(payload_type, payload_freq, event,
                                       duration_ms);
  }

  void SetSend(bool send) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    send_ = send;
    UpdateSendState();
  }

  void SetMuted(bool muted) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(stream_);
    stream_->SetMuted(muted);
    muted_ = muted;
  }

  bool muted() const {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    return muted_;
  }

  webrtc::AudioSendStream::Stats GetStats(bool has_remote_tracks) const {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(stream_);
    return stream_->GetStats(has_remote_tracks);
  }

  // Starts the sending by setting ourselves as a sink to the AudioSource to
  // get data callbacks.
  // This method is called on the libjingle worker thread.
  // TODO(xians): Make sure Start() is called only once.
  void SetSource(AudioSource* source) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(source);
    if (source_) {
      RTC_DCHECK(source_ == source);
      return;
    }
    source->SetSink(this);
    source_ = source;
    UpdateSendState();
  }

  // Stops sending by setting the sink of the AudioSource to nullptr. No data
  // callback will be received after this method.
  // This method is called on the libjingle worker thread.
  void ClearSource() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (source_) {
      source_->SetSink(nullptr);
      source_ = nullptr;
    }
    UpdateSendState();
  }

  // AudioSource::Sink implementation.
  // This method is called on the audio thread.
  void OnData(const void* audio_data,
              int bits_per_sample,
              int sample_rate,
              size_t number_of_channels,
              size_t number_of_frames,
              absl::optional<int64_t> absolute_capture_timestamp_ms) override {
    TRACE_EVENT_BEGIN2("webrtc", "WebRtcAudioSendStream::OnData", "sample_rate",
                       sample_rate, "number_of_frames", number_of_frames);
    RTC_DCHECK_EQ(16, bits_per_sample);
    RTC_CHECK_RUNS_SERIALIZED(&audio_capture_race_checker_);
    RTC_DCHECK(stream_);
    std::unique_ptr<webrtc::AudioFrame> audio_frame(new webrtc::AudioFrame());
    audio_frame->UpdateFrame(
        audio_frame->timestamp_, static_cast<const int16_t*>(audio_data),
        number_of_frames, sample_rate, audio_frame->speech_type_,
        audio_frame->vad_activity_, number_of_channels);
    // TODO(bugs.webrtc.org/10739): add dcheck that
    // `absolute_capture_timestamp_ms` always receives a value.
    if (absolute_capture_timestamp_ms) {
      audio_frame->set_absolute_capture_timestamp_ms(
          *absolute_capture_timestamp_ms);
    }
    stream_->SendAudioData(std::move(audio_frame));
    TRACE_EVENT_END1("webrtc", "WebRtcAudioSendStream::OnData",
                     "number_of_channels", number_of_channels);
  }

  // Callback from the `source_` when it is going away. In case Start() has
  // never been called, this callback won't be triggered.
  void OnClose() override {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    // Set `source_` to nullptr to make sure no more callback will get into
    // the source.
    source_ = nullptr;
    UpdateSendState();
  }

  const webrtc::RtpParameters& rtp_parameters() const {
    return rtp_parameters_;
  }

  webrtc::RTCError SetRtpParameters(const webrtc::RtpParameters& parameters) {
    webrtc::RTCError error = CheckRtpParametersInvalidModificationAndValues(
        rtp_parameters_, parameters);
    if (!error.ok()) {
      return error;
    }

    absl::optional<int> send_rate;
    if (audio_codec_spec_) {
      send_rate = ComputeSendBitrate(max_send_bitrate_bps_,
                                     parameters.encodings[0].max_bitrate_bps,
                                     *audio_codec_spec_);
      if (!send_rate) {
        return webrtc::RTCError(webrtc::RTCErrorType::INTERNAL_ERROR);
      }
    }

    const absl::optional<int> old_rtp_max_bitrate =
        rtp_parameters_.encodings[0].max_bitrate_bps;
    double old_priority = rtp_parameters_.encodings[0].bitrate_priority;
    webrtc::Priority old_dscp = rtp_parameters_.encodings[0].network_priority;
    bool old_adaptive_ptime = rtp_parameters_.encodings[0].adaptive_ptime;
    rtp_parameters_ = parameters;
    config_.bitrate_priority = rtp_parameters_.encodings[0].bitrate_priority;
    config_.has_dscp = (rtp_parameters_.encodings[0].network_priority !=
                        webrtc::Priority::kLow);

    bool reconfigure_send_stream =
        (rtp_parameters_.encodings[0].max_bitrate_bps != old_rtp_max_bitrate) ||
        (rtp_parameters_.encodings[0].bitrate_priority != old_priority) ||
        (rtp_parameters_.encodings[0].network_priority != old_dscp) ||
        (rtp_parameters_.encodings[0].adaptive_ptime != old_adaptive_ptime);
    if (rtp_parameters_.encodings[0].max_bitrate_bps != old_rtp_max_bitrate) {
      // Update the bitrate range.
      if (send_rate) {
        config_.send_codec_spec->target_bitrate_bps = send_rate;
      }
    }
    if (reconfigure_send_stream) {
      // Changing adaptive_ptime may update the audio network adaptor config
      // used.
      UpdateAudioNetworkAdaptorConfig();
      UpdateAllowedBitrateRange();
      ReconfigureAudioSendStream();
    }

    rtp_parameters_.rtcp.cname = config_.rtp.c_name;
    rtp_parameters_.rtcp.reduced_size = false;

    // parameters.encodings[0].active could have changed.
    UpdateSendState();
    return webrtc::RTCError::OK();
  }

  void SetEncoderToPacketizerFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    config_.frame_transformer = std::move(frame_transformer);
    ReconfigureAudioSendStream();
  }

 private:
  void UpdateSendState() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(stream_);
    RTC_DCHECK_EQ(1UL, rtp_parameters_.encodings.size());
    if (send_ && source_ != nullptr && rtp_parameters_.encodings[0].active) {
      stream_->Start();
    } else {  // !send || source_ = nullptr
      stream_->Stop();
    }
  }

  void UpdateAllowedBitrateRange() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    // The order of precedence, from lowest to highest is:
    // - a reasonable default of 32kbps min/max
    // - fixed target bitrate from codec spec
    // - lower min bitrate if adaptive ptime is enabled
    // - bitrate configured in the rtp_parameter encodings settings
    const int kDefaultBitrateBps = 32000;
    config_.min_bitrate_bps = kDefaultBitrateBps;
    config_.max_bitrate_bps = kDefaultBitrateBps;

    if (config_.send_codec_spec &&
        config_.send_codec_spec->target_bitrate_bps) {
      config_.min_bitrate_bps = *config_.send_codec_spec->target_bitrate_bps;
      config_.max_bitrate_bps = *config_.send_codec_spec->target_bitrate_bps;
    }

    if (rtp_parameters_.encodings[0].adaptive_ptime) {
      config_.min_bitrate_bps = std::min(
          config_.min_bitrate_bps,
          static_cast<int>(adaptive_ptime_config_.min_encoder_bitrate.bps()));
    }

    if (rtp_parameters_.encodings[0].min_bitrate_bps) {
      config_.min_bitrate_bps = *rtp_parameters_.encodings[0].min_bitrate_bps;
    }
    if (rtp_parameters_.encodings[0].max_bitrate_bps) {
      config_.max_bitrate_bps = *rtp_parameters_.encodings[0].max_bitrate_bps;
    }
  }

  void UpdateSendCodecSpec(
      const webrtc::AudioSendStream::Config::SendCodecSpec& send_codec_spec) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    config_.send_codec_spec = send_codec_spec;
    auto info =
        config_.encoder_factory->QueryAudioEncoder(send_codec_spec.format);
    RTC_DCHECK(info);
    // If a specific target bitrate has been set for the stream, use that as
    // the new default bitrate when computing send bitrate.
    if (send_codec_spec.target_bitrate_bps) {
      info->default_bitrate_bps = std::max(
          info->min_bitrate_bps,
          std::min(info->max_bitrate_bps, *send_codec_spec.target_bitrate_bps));
    }

    audio_codec_spec_.emplace(
        webrtc::AudioCodecSpec{send_codec_spec.format, *info});

    config_.send_codec_spec->target_bitrate_bps = ComputeSendBitrate(
        max_send_bitrate_bps_, rtp_parameters_.encodings[0].max_bitrate_bps,
        *audio_codec_spec_);

    UpdateAllowedBitrateRange();

    // Encoder will only use two channels if the stereo parameter is set.
    const auto& it = send_codec_spec.format.parameters.find("stereo");
    if (it != send_codec_spec.format.parameters.end() && it->second == "1") {
      num_encoded_channels_ = 2;
    } else {
      num_encoded_channels_ = 1;
    }
  }

  void UpdateAudioNetworkAdaptorConfig() {
    if (adaptive_ptime_config_.enabled ||
        rtp_parameters_.encodings[0].adaptive_ptime) {
      config_.audio_network_adaptor_config =
          adaptive_ptime_config_.audio_network_adaptor_config;
      return;
    }
    config_.audio_network_adaptor_config =
        audio_network_adaptor_config_from_options_;
  }

  void ReconfigureAudioSendStream() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    RTC_DCHECK(stream_);
    stream_->Reconfigure(config_);
  }

  int NumPreferredChannels() const override { return num_encoded_channels_; }

  const AdaptivePtimeConfig adaptive_ptime_config_;
  webrtc::SequenceChecker worker_thread_checker_;
  rtc::RaceChecker audio_capture_race_checker_;
  webrtc::Call* call_ = nullptr;
  webrtc::AudioSendStream::Config config_;
  // The stream is owned by WebRtcAudioSendStream and may be reallocated if
  // configuration changes.
  webrtc::AudioSendStream* stream_ = nullptr;

  // Raw pointer to AudioSource owned by LocalAudioTrackHandler.
  // PeerConnection will make sure invalidating the pointer before the object
  // goes away.
  AudioSource* source_ = nullptr;
  bool send_ = false;
  bool muted_ = false;
  int max_send_bitrate_bps_;
  webrtc::RtpParameters rtp_parameters_;
  absl::optional<webrtc::AudioCodecSpec> audio_codec_spec_;
  // TODO(webrtc:11717): Remove this once audio_network_adaptor in AudioOptions
  // has been removed.
  absl::optional<std::string> audio_network_adaptor_config_from_options_;
  std::atomic<int> num_encoded_channels_{-1};
};

class WebRtcVoiceMediaChannel::WebRtcAudioReceiveStream {
 public:
  WebRtcAudioReceiveStream(webrtc::AudioReceiveStreamInterface::Config config,
                           webrtc::Call* call)
      : call_(call), stream_(call_->CreateAudioReceiveStream(config)) {
    RTC_DCHECK(call);
    RTC_DCHECK(stream_);
  }

  WebRtcAudioReceiveStream() = delete;
  WebRtcAudioReceiveStream(const WebRtcAudioReceiveStream&) = delete;
  WebRtcAudioReceiveStream& operator=(const WebRtcAudioReceiveStream&) = delete;

  ~WebRtcAudioReceiveStream() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    call_->DestroyAudioReceiveStream(stream_);
  }

  webrtc::AudioReceiveStreamInterface& stream() {
    RTC_DCHECK(stream_);
    return *stream_;
  }

  void SetFrameDecryptor(
      rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetFrameDecryptor(std::move(frame_decryptor));
  }

  void SetUseTransportCc(bool use_transport_cc, bool use_nack) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetTransportCc(use_transport_cc);
    stream_->SetNackHistory(use_nack ? kNackRtpHistoryMs : 0);
  }

  void SetNonSenderRttMeasurement(bool enabled) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetNonSenderRttMeasurement(enabled);
  }

  void SetRtpExtensions(const std::vector<webrtc::RtpExtension>& extensions) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetRtpExtensions(extensions);
  }

  // Set a new payload type -> decoder map.
  void SetDecoderMap(const std::map<int, webrtc::SdpAudioFormat>& decoder_map) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetDecoderMap(decoder_map);
  }

  webrtc::AudioReceiveStreamInterface::Stats GetStats(
      bool get_and_clear_legacy_stats) const {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    return stream_->GetStats(get_and_clear_legacy_stats);
  }

  void SetRawAudioSink(std::unique_ptr<webrtc::AudioSinkInterface> sink) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    // Need to update the stream's sink first; once raw_audio_sink_ is
    // reassigned, whatever was in there before is destroyed.
    stream_->SetSink(sink.get());
    raw_audio_sink_ = std::move(sink);
  }

  void SetOutputVolume(double volume) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetGain(volume);
  }

  void SetPlayout(bool playout) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (playout) {
      stream_->Start();
    } else {
      stream_->Stop();
    }
  }

  bool SetBaseMinimumPlayoutDelayMs(int delay_ms) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    if (stream_->SetBaseMinimumPlayoutDelayMs(delay_ms))
      return true;

    RTC_LOG(LS_ERROR) << "Failed to SetBaseMinimumPlayoutDelayMs"
                         " on AudioReceiveStreamInterface on SSRC="
                      << stream_->remote_ssrc()
                      << " with delay_ms=" << delay_ms;
    return false;
  }

  int GetBaseMinimumPlayoutDelayMs() const {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    return stream_->GetBaseMinimumPlayoutDelayMs();
  }

  std::vector<webrtc::RtpSource> GetSources() {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    return stream_->GetSources();
  }

  webrtc::RtpParameters GetRtpParameters() const {
    webrtc::RtpParameters rtp_parameters;
    rtp_parameters.encodings.emplace_back();
    rtp_parameters.encodings[0].ssrc = stream_->remote_ssrc();
    rtp_parameters.header_extensions = stream_->GetRtpExtensions();
    return rtp_parameters;
  }

  void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
    RTC_DCHECK_RUN_ON(&worker_thread_checker_);
    stream_->SetDepacketizerToDecoderFrameTransformer(frame_transformer);
  }

 private:
  webrtc::SequenceChecker worker_thread_checker_;
  webrtc::Call* call_ = nullptr;
  webrtc::AudioReceiveStreamInterface* const stream_ = nullptr;
  std::unique_ptr<webrtc::AudioSinkInterface> raw_audio_sink_
      RTC_GUARDED_BY(worker_thread_checker_);
};

WebRtcVoiceMediaChannel::WebRtcVoiceMediaChannel(
    WebRtcVoiceEngine* engine,
    const MediaConfig& config,
    const AudioOptions& options,
    const webrtc::CryptoOptions& crypto_options,
    webrtc::Call* call)
    : VoiceMediaChannel(call->network_thread(), config.enable_dscp),
      worker_thread_(call->worker_thread()),
      engine_(engine),
      call_(call),
      audio_config_(config.audio),
      crypto_options_(crypto_options) {
  network_thread_checker_.Detach();
  RTC_LOG(LS_VERBOSE) << "WebRtcVoiceMediaChannel::WebRtcVoiceMediaChannel";
  RTC_DCHECK(call);
  SetOptions(options);
}

WebRtcVoiceMediaChannel::~WebRtcVoiceMediaChannel() {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_VERBOSE) << "WebRtcVoiceMediaChannel::~WebRtcVoiceMediaChannel";
  // TODO(solenberg): Should be able to delete the streams directly, without
  //                  going through RemoveNnStream(), once stream objects handle
  //                  all (de)configuration.
  while (!send_streams_.empty()) {
    RemoveSendStream(send_streams_.begin()->first);
  }
  while (!recv_streams_.empty()) {
    RemoveRecvStream(recv_streams_.begin()->first);
  }
}

bool WebRtcVoiceMediaChannel::SetSendParameters(
    const AudioSendParameters& params) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::SetSendParameters");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceMediaChannel::SetSendParameters: "
                   << params.ToString();
  // TODO(pthatcher): Refactor this to be more clean now that we have
  // all the information at once.

  if (!SetSendCodecs(params.codecs)) {
    return false;
  }

  if (!ValidateRtpExtensions(params.extensions, send_rtp_extensions_)) {
    return false;
  }

  if (ExtmapAllowMixed() != params.extmap_allow_mixed) {
    SetExtmapAllowMixed(params.extmap_allow_mixed);
    for (auto& it : send_streams_) {
      it.second->SetExtmapAllowMixed(params.extmap_allow_mixed);
    }
  }

  std::vector<webrtc::RtpExtension> filtered_extensions = FilterRtpExtensions(
      params.extensions, webrtc::RtpExtension::IsSupportedForAudio, true,
      call_->trials());
  if (send_rtp_extensions_ != filtered_extensions) {
    send_rtp_extensions_.swap(filtered_extensions);
    for (auto& it : send_streams_) {
      it.second->SetRtpExtensions(send_rtp_extensions_);
    }
  }
  if (!params.mid.empty()) {
    mid_ = params.mid;
    for (auto& it : send_streams_) {
      it.second->SetMid(params.mid);
    }
  }

  if (!SetMaxSendBitrate(params.max_bandwidth_bps)) {
    return false;
  }
  return SetOptions(params.options);
}

bool WebRtcVoiceMediaChannel::SetRecvParameters(
    const AudioRecvParameters& params) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::SetRecvParameters");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceMediaChannel::SetRecvParameters: "
                   << params.ToString();
  // TODO(pthatcher): Refactor this to be more clean now that we have
  // all the information at once.

  if (!SetRecvCodecs(params.codecs)) {
    return false;
  }

  if (!ValidateRtpExtensions(params.extensions, recv_rtp_extensions_)) {
    return false;
  }
  std::vector<webrtc::RtpExtension> filtered_extensions = FilterRtpExtensions(
      params.extensions, webrtc::RtpExtension::IsSupportedForAudio, false,
      call_->trials());
  if (recv_rtp_extensions_ != filtered_extensions) {
    recv_rtp_extensions_.swap(filtered_extensions);
    for (auto& it : recv_streams_) {
      it.second->SetRtpExtensions(recv_rtp_extensions_);
    }
  }
  return true;
}

webrtc::RtpParameters WebRtcVoiceMediaChannel::GetRtpSendParameters(
    uint32_t ssrc) const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto it = send_streams_.find(ssrc);
  if (it == send_streams_.end()) {
    RTC_LOG(LS_WARNING) << "Attempting to get RTP send parameters for stream "
                           "with ssrc "
                        << ssrc << " which doesn't exist.";
    return webrtc::RtpParameters();
  }

  webrtc::RtpParameters rtp_params = it->second->rtp_parameters();
  // Need to add the common list of codecs to the send stream-specific
  // RTP parameters.
  for (const AudioCodec& codec : send_codecs_) {
    rtp_params.codecs.push_back(codec.ToCodecParameters());
  }
  return rtp_params;
}

webrtc::RTCError WebRtcVoiceMediaChannel::SetRtpSendParameters(
    uint32_t ssrc,
    const webrtc::RtpParameters& parameters) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto it = send_streams_.find(ssrc);
  if (it == send_streams_.end()) {
    RTC_LOG(LS_WARNING) << "Attempting to set RTP send parameters for stream "
                           "with ssrc "
                        << ssrc << " which doesn't exist.";
    return webrtc::RTCError(webrtc::RTCErrorType::INTERNAL_ERROR);
  }

  // TODO(deadbeef): Handle setting parameters with a list of codecs in a
  // different order (which should change the send codec).
  webrtc::RtpParameters current_parameters = GetRtpSendParameters(ssrc);
  if (current_parameters.codecs != parameters.codecs) {
    RTC_DLOG(LS_ERROR) << "Using SetParameters to change the set of codecs "
                          "is not currently supported.";
    return webrtc::RTCError(webrtc::RTCErrorType::UNSUPPORTED_PARAMETER);
  }

  if (!parameters.encodings.empty()) {
    // Note that these values come from:
    // https://tools.ietf.org/html/draft-ietf-tsvwg-rtcweb-qos-16#section-5
    rtc::DiffServCodePoint new_dscp = rtc::DSCP_DEFAULT;
    switch (parameters.encodings[0].network_priority) {
      case webrtc::Priority::kVeryLow:
        new_dscp = rtc::DSCP_CS1;
        break;
      case webrtc::Priority::kLow:
        new_dscp = rtc::DSCP_DEFAULT;
        break;
      case webrtc::Priority::kMedium:
        new_dscp = rtc::DSCP_EF;
        break;
      case webrtc::Priority::kHigh:
        new_dscp = rtc::DSCP_EF;
        break;
    }
    SetPreferredDscp(new_dscp);
  }

  // TODO(minyue): The following legacy actions go into
  // `WebRtcAudioSendStream::SetRtpParameters()` which is called at the end,
  // though there are two difference:
  // 1. `WebRtcVoiceMediaChannel::SetChannelSendParameters()` only calls
  // `SetSendCodec` while `WebRtcAudioSendStream::SetRtpParameters()` calls
  // `SetSendCodecs`. The outcome should be the same.
  // 2. AudioSendStream can be recreated.

  // Codecs are handled at the WebRtcVoiceMediaChannel level.
  webrtc::RtpParameters reduced_params = parameters;
  reduced_params.codecs.clear();
  return it->second->SetRtpParameters(reduced_params);
}

webrtc::RtpParameters WebRtcVoiceMediaChannel::GetRtpReceiveParameters(
    uint32_t ssrc) const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  webrtc::RtpParameters rtp_params;
  auto it = recv_streams_.find(ssrc);
  if (it == recv_streams_.end()) {
    RTC_LOG(LS_WARNING)
        << "Attempting to get RTP receive parameters for stream "
           "with ssrc "
        << ssrc << " which doesn't exist.";
    return webrtc::RtpParameters();
  }
  rtp_params = it->second->GetRtpParameters();

  for (const AudioCodec& codec : recv_codecs_) {
    rtp_params.codecs.push_back(codec.ToCodecParameters());
  }
  return rtp_params;
}

webrtc::RtpParameters WebRtcVoiceMediaChannel::GetDefaultRtpReceiveParameters()
    const {
  RTC_DCHECK_RUN_ON(worker_thread_);
  webrtc::RtpParameters rtp_params;
  if (!default_sink_) {
    // Getting parameters on a default, unsignaled audio receive stream but
    // because we've not configured to receive such a stream, `encodings` is
    // empty.
    return rtp_params;
  }
  rtp_params.encodings.emplace_back();

  for (const AudioCodec& codec : recv_codecs_) {
    rtp_params.codecs.push_back(codec.ToCodecParameters());
  }
  return rtp_params;
}

bool WebRtcVoiceMediaChannel::SetOptions(const AudioOptions& options) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "Setting voice channel options: " << options.ToString();

  // We retain all of the existing options, and apply the given ones
  // on top.  This means there is no way to "clear" options such that
  // they go back to the engine default.
  options_.SetAll(options);
  engine()->ApplyOptions(options_);

  absl::optional<std::string> audio_network_adaptor_config =
      GetAudioNetworkAdaptorConfig(options_);
  for (auto& it : send_streams_) {
    it.second->SetAudioNetworkAdaptorConfig(audio_network_adaptor_config);
  }

  RTC_LOG(LS_INFO) << "Set voice channel options. Current options: "
                   << options_.ToString();
  return true;
}

bool WebRtcVoiceMediaChannel::SetRecvCodecs(
    const std::vector<AudioCodec>& codecs) {
  RTC_DCHECK_RUN_ON(worker_thread_);

  // Set the payload types to be used for incoming media.
  RTC_LOG(LS_INFO) << "Setting receive voice codecs.";

  if (!VerifyUniquePayloadTypes(codecs)) {
    RTC_LOG(LS_ERROR) << "Codec payload types overlap.";
    return false;
  }

  // Create a payload type -> SdpAudioFormat map with all the decoders. Fail
  // unless the factory claims to support all decoders.
  std::map<int, webrtc::SdpAudioFormat> decoder_map;
  for (const AudioCodec& codec : codecs) {
    // Log a warning if a codec's payload type is changing. This used to be
    // treated as an error. It's abnormal, but not really illegal.
    AudioCodec old_codec;
    if (FindCodec(recv_codecs_, codec, &old_codec, &call_->trials()) &&
        old_codec.id != codec.id) {
      RTC_LOG(LS_WARNING) << codec.name << " mapped to a second payload type ("
                          << codec.id << ", was already mapped to "
                          << old_codec.id << ")";
    }
    auto format = AudioCodecToSdpAudioFormat(codec);
    if (!IsCodec(codec, kCnCodecName) && !IsCodec(codec, kDtmfCodecName) &&
        !IsCodec(codec, kRedCodecName) &&
        !engine()->decoder_factory_->IsSupportedDecoder(format)) {
      RTC_LOG(LS_ERROR) << "Unsupported codec: " << rtc::ToString(format);
      return false;
    }
    // We allow adding new codecs but don't allow changing the payload type of
    // codecs that are already configured since we might already be receiving
    // packets with that payload type. See RFC3264, Section 8.3.2.
    // TODO(deadbeef): Also need to check for clashes with previously mapped
    // payload types, and not just currently mapped ones. For example, this
    // should be illegal:
    // 1. {100: opus/48000/2, 101: ISAC/16000}
    // 2. {100: opus/48000/2}
    // 3. {100: opus/48000/2, 101: ISAC/32000}
    // Though this check really should happen at a higher level, since this
    // conflict could happen between audio and video codecs.
    auto existing = decoder_map_.find(codec.id);
    if (existing != decoder_map_.end() && !existing->second.Matches(format)) {
      RTC_LOG(LS_ERROR) << "Attempting to use payload type " << codec.id
                        << " for " << codec.name
                        << ", but it is already used for "
                        << existing->second.name;
      return false;
    }
    decoder_map.insert({codec.id, std::move(format)});
  }

  if (decoder_map == decoder_map_) {
    // There's nothing new to configure.
    return true;
  }

  bool playout_enabled = playout_;
  // Receive codecs can not be changed while playing. So we temporarily
  // pause playout.
  SetPlayout(false);
  RTC_DCHECK(!playout_);

  decoder_map_ = std::move(decoder_map);
  for (auto& kv : recv_streams_) {
    kv.second->SetDecoderMap(decoder_map_);
  }

  recv_codecs_ = codecs;

  SetPlayout(playout_enabled);
  RTC_DCHECK_EQ(playout_, playout_enabled);

  return true;
}

// Utility function to check if RED codec and its parameters match a codec spec.
bool CheckRedParameters(
    const AudioCodec& red_codec,
    const webrtc::AudioSendStream::Config::SendCodecSpec& send_codec_spec) {
  if (red_codec.clockrate != send_codec_spec.format.clockrate_hz ||
      red_codec.channels != send_codec_spec.format.num_channels) {
    return false;
  }

  // Check the FMTP line for the empty parameter which should match
  // <primary codec>/<primary codec>[/...]
  auto red_parameters = red_codec.params.find("");
  if (red_parameters == red_codec.params.end()) {
    RTC_LOG(LS_WARNING) << "audio/RED missing fmtp parameters.";
    return false;
  }
  std::vector<absl::string_view> redundant_payloads =
      rtc::split(red_parameters->second, '/');
  // 32 is chosen as a maximum upper bound for consistency with the
  // red payload splitter.
  if (redundant_payloads.size() < 2 || redundant_payloads.size() > 32) {
    return false;
  }
  for (auto pt : redundant_payloads) {
    if (pt != rtc::ToString(send_codec_spec.payload_type)) {
      return false;
    }
  }
  return true;
}

// Utility function called from SetSendParameters() to extract current send
// codec settings from the given list of codecs (originally from SDP). Both send
// and receive streams may be reconfigured based on the new settings.
bool WebRtcVoiceMediaChannel::SetSendCodecs(
    const std::vector<AudioCodec>& codecs) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  dtmf_payload_type_ = absl::nullopt;
  dtmf_payload_freq_ = -1;

  // Validate supplied codecs list.
  for (const AudioCodec& codec : codecs) {
    // TODO(solenberg): Validate more aspects of input - that payload types
    //                  don't overlap, remove redundant/unsupported codecs etc -
    //                  the same way it is done for RtpHeaderExtensions.
    if (codec.id < kMinPayloadType || codec.id > kMaxPayloadType) {
      RTC_LOG(LS_WARNING) << "Codec payload type out of range: "
                          << ToString(codec);
      return false;
    }
  }

  // Find PT of telephone-event codec with lowest clockrate, as a fallback, in
  // case we don't have a DTMF codec with a rate matching the send codec's, or
  // if this function returns early.
  std::vector<AudioCodec> dtmf_codecs;
  for (const AudioCodec& codec : codecs) {
    if (IsCodec(codec, kDtmfCodecName)) {
      dtmf_codecs.push_back(codec);
      if (!dtmf_payload_type_ || codec.clockrate < dtmf_payload_freq_) {
        dtmf_payload_type_ = codec.id;
        dtmf_payload_freq_ = codec.clockrate;
      }
    }
  }

  // Scan through the list to figure out the codec to use for sending.
  absl::optional<webrtc::AudioSendStream::Config::SendCodecSpec>
      send_codec_spec;
  webrtc::BitrateConstraints bitrate_config;
  absl::optional<webrtc::AudioCodecInfo> voice_codec_info;
  size_t send_codec_position = 0;
  for (const AudioCodec& voice_codec : codecs) {
    if (!(IsCodec(voice_codec, kCnCodecName) ||
          IsCodec(voice_codec, kDtmfCodecName) ||
          IsCodec(voice_codec, kRedCodecName))) {
      webrtc::SdpAudioFormat format(voice_codec.name, voice_codec.clockrate,
                                    voice_codec.channels, voice_codec.params);

      voice_codec_info = engine()->encoder_factory_->QueryAudioEncoder(format);
      if (!voice_codec_info) {
        RTC_LOG(LS_WARNING) << "Unknown codec " << ToString(voice_codec);
        continue;
      }

      send_codec_spec = webrtc::AudioSendStream::Config::SendCodecSpec(
          voice_codec.id, format);
      if (voice_codec.bitrate > 0) {
        send_codec_spec->target_bitrate_bps = voice_codec.bitrate;
      }
      send_codec_spec->transport_cc_enabled = HasTransportCc(voice_codec);
      send_codec_spec->nack_enabled = HasNack(voice_codec);
      send_codec_spec->enable_non_sender_rtt = HasRrtr(voice_codec);
      bitrate_config = GetBitrateConfigForCodec(voice_codec);
      break;
    }
    send_codec_position++;
  }

  if (!send_codec_spec) {
    return false;
  }

  RTC_DCHECK(voice_codec_info);
  if (voice_codec_info->allow_comfort_noise) {
    // Loop through the codecs list again to find the CN codec.
    // TODO(solenberg): Break out into a separate function?
    for (const AudioCodec& cn_codec : codecs) {
      if (IsCodec(cn_codec, kCnCodecName) &&
          cn_codec.clockrate == send_codec_spec->format.clockrate_hz &&
          cn_codec.channels == voice_codec_info->num_channels) {
        if (cn_codec.channels != 1) {
          RTC_LOG(LS_WARNING)
              << "CN #channels " << cn_codec.channels << " not supported.";
        } else if (cn_codec.clockrate != 8000 && cn_codec.clockrate != 16000 &&
                   cn_codec.clockrate != 32000) {
          RTC_LOG(LS_WARNING)
              << "CN frequency " << cn_codec.clockrate << " not supported.";
        } else {
          send_codec_spec->cng_payload_type = cn_codec.id;
        }
        break;
      }
    }

    // Find the telephone-event PT exactly matching the preferred send codec.
    for (const AudioCodec& dtmf_codec : dtmf_codecs) {
      if (dtmf_codec.clockrate == send_codec_spec->format.clockrate_hz) {
        dtmf_payload_type_ = dtmf_codec.id;
        dtmf_payload_freq_ = dtmf_codec.clockrate;
        break;
      }
    }
  }

  // Loop through the codecs to find the RED codec that matches opus
  // with respect to clockrate and number of channels.
  size_t red_codec_position = 0;
  for (const AudioCodec& red_codec : codecs) {
    if (red_codec_position < send_codec_position &&
        IsCodec(red_codec, kRedCodecName) &&
        CheckRedParameters(red_codec, *send_codec_spec)) {
      send_codec_spec->red_payload_type = red_codec.id;
      break;
    }
    red_codec_position++;
  }

  if (send_codec_spec_ != send_codec_spec) {
    send_codec_spec_ = std::move(send_codec_spec);
    // Apply new settings to all streams.
    for (const auto& kv : send_streams_) {
      kv.second->SetSendCodecSpec(*send_codec_spec_);
    }
  } else {
    // If the codec isn't changing, set the start bitrate to -1 which means
    // "unchanged" so that BWE isn't affected.
    bitrate_config.start_bitrate_bps = -1;
  }
  call_->GetTransportControllerSend()->SetSdpBitrateParameters(bitrate_config);

  // Check if the transport cc feedback or NACK status has changed on the
  // preferred send codec, and in that case reconfigure all receive streams.
  if (recv_transport_cc_enabled_ != send_codec_spec_->transport_cc_enabled ||
      recv_nack_enabled_ != send_codec_spec_->nack_enabled) {
    RTC_LOG(LS_INFO) << "Changing transport cc and NACK status on receive "
                        "streams.";
    recv_transport_cc_enabled_ = send_codec_spec_->transport_cc_enabled;
    recv_nack_enabled_ = send_codec_spec_->nack_enabled;
    for (auto& kv : recv_streams_) {
      kv.second->SetUseTransportCc(recv_transport_cc_enabled_,
                                   recv_nack_enabled_);
    }
  }

  // Check if the receive-side RTT status has changed on the preferred send
  // codec, in that case reconfigure all receive streams.
  if (enable_non_sender_rtt_ != send_codec_spec_->enable_non_sender_rtt) {
    RTC_LOG(LS_INFO) << "Changing receive-side RTT status on receive streams.";
    enable_non_sender_rtt_ = send_codec_spec_->enable_non_sender_rtt;
    for (auto& kv : recv_streams_) {
      kv.second->SetNonSenderRttMeasurement(enable_non_sender_rtt_);
    }
  }

  send_codecs_ = codecs;
  return true;
}

void WebRtcVoiceMediaChannel::SetPlayout(bool playout) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::SetPlayout");
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (playout_ == playout) {
    return;
  }

  for (const auto& kv : recv_streams_) {
    kv.second->SetPlayout(playout);
  }
  playout_ = playout;
}

void WebRtcVoiceMediaChannel::SetSend(bool send) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::SetSend");
  if (send_ == send) {
    return;
  }

  // Apply channel specific options.
  if (send) {
    engine()->ApplyOptions(options_);

    // Initialize the ADM for recording (this may take time on some platforms,
    // e.g. Android).
    if (options_.init_recording_on_send.value_or(true) &&
        // InitRecording() may return an error if the ADM is already recording.
        !engine()->adm()->RecordingIsInitialized() &&
        !engine()->adm()->Recording()) {
      if (engine()->adm()->InitRecording() != 0) {
        RTC_LOG(LS_WARNING) << "Failed to initialize recording";
      }
    }
  }

  // Change the settings on each send channel.
  for (auto& kv : send_streams_) {
    kv.second->SetSend(send);
  }

  send_ = send;
}

bool WebRtcVoiceMediaChannel::SetAudioSend(uint32_t ssrc,
                                           bool enable,
                                           const AudioOptions* options,
                                           AudioSource* source) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  // TODO(solenberg): The state change should be fully rolled back if any one of
  //                  these calls fail.
  if (!SetLocalSource(ssrc, source)) {
    return false;
  }
  if (!MuteStream(ssrc, !enable)) {
    return false;
  }
  if (enable && options) {
    return SetOptions(*options);
  }
  return true;
}

bool WebRtcVoiceMediaChannel::AddSendStream(const StreamParams& sp) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::AddSendStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "AddSendStream: " << sp.ToString();

  uint32_t ssrc = sp.first_ssrc();
  RTC_DCHECK(0 != ssrc);

  if (send_streams_.find(ssrc) != send_streams_.end()) {
    RTC_LOG(LS_ERROR) << "Stream already exists with ssrc " << ssrc;
    return false;
  }

  absl::optional<std::string> audio_network_adaptor_config =
      GetAudioNetworkAdaptorConfig(options_);
  WebRtcAudioSendStream* stream = new WebRtcAudioSendStream(
      ssrc, mid_, sp.cname, sp.id, send_codec_spec_, ExtmapAllowMixed(),
      send_rtp_extensions_, max_send_bitrate_bps_,
      audio_config_.rtcp_report_interval_ms, audio_network_adaptor_config,
      call_, this, engine()->encoder_factory_, codec_pair_id_, nullptr,
      crypto_options_);
  send_streams_.insert(std::make_pair(ssrc, stream));

  // At this point the stream's local SSRC has been updated. If it is the first
  // send stream, make sure that all the receive streams are updated with the
  // same SSRC in order to send receiver reports.
  if (send_streams_.size() == 1) {
    receiver_reports_ssrc_ = ssrc;
    for (auto& kv : recv_streams_) {
      call_->OnLocalSsrcUpdated(kv.second->stream(), ssrc);
    }
  }

  send_streams_[ssrc]->SetSend(send_);
  return true;
}

bool WebRtcVoiceMediaChannel::RemoveSendStream(uint32_t ssrc) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::RemoveSendStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "RemoveSendStream: " << ssrc;

  auto it = send_streams_.find(ssrc);
  if (it == send_streams_.end()) {
    RTC_LOG(LS_WARNING) << "Try to remove stream with ssrc " << ssrc
                        << " which doesn't exist.";
    return false;
  }

  it->second->SetSend(false);

  // TODO(solenberg): If we're removing the receiver_reports_ssrc_ stream, find
  // the first active send stream and use that instead, reassociating receive
  // streams.

  delete it->second;
  send_streams_.erase(it);
  if (send_streams_.empty()) {
    SetSend(false);
  }
  return true;
}

bool WebRtcVoiceMediaChannel::AddRecvStream(const StreamParams& sp) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::AddRecvStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "AddRecvStream: " << sp.ToString();

  if (!sp.has_ssrcs()) {
    // This is a StreamParam with unsignaled SSRCs. Store it, so it can be used
    // later when we know the SSRCs on the first packet arrival.
    unsignaled_stream_params_ = sp;
    return true;
  }

  if (!ValidateStreamParams(sp)) {
    return false;
  }

  const uint32_t ssrc = sp.first_ssrc();

  // If this stream was previously received unsignaled, we promote it, possibly
  // updating the sync group if stream ids have changed.
  if (MaybeDeregisterUnsignaledRecvStream(ssrc)) {
    auto stream_ids = sp.stream_ids();
    std::string sync_group = stream_ids.empty() ? std::string() : stream_ids[0];
    call_->OnUpdateSyncGroup(recv_streams_[ssrc]->stream(),
                             std::move(sync_group));
    return true;
  }

  if (recv_streams_.find(ssrc) != recv_streams_.end()) {
    RTC_LOG(LS_ERROR) << "Stream already exists with ssrc " << ssrc;
    return false;
  }

  // Create a new channel for receiving audio data.
  auto config = BuildReceiveStreamConfig(
      ssrc, receiver_reports_ssrc_, recv_transport_cc_enabled_,
      recv_nack_enabled_, enable_non_sender_rtt_, sp.stream_ids(),
      recv_rtp_extensions_, this, engine()->decoder_factory_, decoder_map_,
      codec_pair_id_, engine()->audio_jitter_buffer_max_packets_,
      engine()->audio_jitter_buffer_fast_accelerate_,
      engine()->audio_jitter_buffer_min_delay_ms_, unsignaled_frame_decryptor_,
      crypto_options_, unsignaled_frame_transformer_);

  recv_streams_.insert(std::make_pair(
      ssrc, new WebRtcAudioReceiveStream(std::move(config), call_)));
  recv_streams_[ssrc]->SetPlayout(playout_);

  return true;
}

bool WebRtcVoiceMediaChannel::RemoveRecvStream(uint32_t ssrc) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::RemoveRecvStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "RemoveRecvStream: " << ssrc;

  const auto it = recv_streams_.find(ssrc);
  if (it == recv_streams_.end()) {
    RTC_LOG(LS_WARNING) << "Try to remove stream with ssrc " << ssrc
                        << " which doesn't exist.";
    return false;
  }

  MaybeDeregisterUnsignaledRecvStream(ssrc);

  it->second->SetRawAudioSink(nullptr);
  delete it->second;
  recv_streams_.erase(it);
  return true;
}

void WebRtcVoiceMediaChannel::ResetUnsignaledRecvStream() {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "ResetUnsignaledRecvStream.";
  unsignaled_stream_params_ = StreamParams();
  // Create a copy since RemoveRecvStream will modify `unsignaled_recv_ssrcs_`.
  std::vector<uint32_t> to_remove = unsignaled_recv_ssrcs_;
  for (uint32_t ssrc : to_remove) {
    RemoveRecvStream(ssrc);
  }
}

// Not implemented.
// TODO(https://crbug.com/webrtc/12676): Implement a fix for the unsignalled
// SSRC race that can happen when an m= section goes from receiving to not
// receiving.
void WebRtcVoiceMediaChannel::OnDemuxerCriteriaUpdatePending() {}
void WebRtcVoiceMediaChannel::OnDemuxerCriteriaUpdateComplete() {}

bool WebRtcVoiceMediaChannel::SetLocalSource(uint32_t ssrc,
                                             AudioSource* source) {
  auto it = send_streams_.find(ssrc);
  if (it == send_streams_.end()) {
    if (source) {
      // Return an error if trying to set a valid source with an invalid ssrc.
      RTC_LOG(LS_ERROR) << "SetLocalSource failed with ssrc " << ssrc;
      return false;
    }

    // The channel likely has gone away, do nothing.
    return true;
  }

  if (source) {
    it->second->SetSource(source);
  } else {
    it->second->ClearSource();
  }

  return true;
}

bool WebRtcVoiceMediaChannel::SetOutputVolume(uint32_t ssrc, double volume) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << rtc::StringFormat("WRVMC::%s({ssrc=%u}, {volume=%.2f})",
                                        __func__, ssrc, volume);
  const auto it = recv_streams_.find(ssrc);
  if (it == recv_streams_.end()) {
    RTC_LOG(LS_WARNING) << rtc::StringFormat(
        "WRVMC::%s => (WARNING: no receive stream for SSRC %u)", __func__,
        ssrc);
    return false;
  }
  it->second->SetOutputVolume(volume);
  RTC_LOG(LS_INFO) << rtc::StringFormat(
      "WRVMC::%s => (stream with SSRC %u now uses volume %.2f)", __func__, ssrc,
      volume);
  return true;
}

bool WebRtcVoiceMediaChannel::SetDefaultOutputVolume(double volume) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  default_recv_volume_ = volume;
  for (uint32_t ssrc : unsignaled_recv_ssrcs_) {
    const auto it = recv_streams_.find(ssrc);
    if (it == recv_streams_.end()) {
      RTC_LOG(LS_WARNING) << "SetDefaultOutputVolume: no recv stream " << ssrc;
      return false;
    }
    it->second->SetOutputVolume(volume);
    RTC_LOG(LS_INFO) << "SetDefaultOutputVolume() to " << volume
                     << " for recv stream with ssrc " << ssrc;
  }
  return true;
}

bool WebRtcVoiceMediaChannel::SetBaseMinimumPlayoutDelayMs(uint32_t ssrc,
                                                           int delay_ms) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  std::vector<uint32_t> ssrcs(1, ssrc);
  // SSRC of 0 represents the default receive stream.
  if (ssrc == 0) {
    default_recv_base_minimum_delay_ms_ = delay_ms;
    ssrcs = unsignaled_recv_ssrcs_;
  }
  for (uint32_t ssrc : ssrcs) {
    const auto it = recv_streams_.find(ssrc);
    if (it == recv_streams_.end()) {
      RTC_LOG(LS_WARNING) << "SetBaseMinimumPlayoutDelayMs: no recv stream "
                          << ssrc;
      return false;
    }
    it->second->SetBaseMinimumPlayoutDelayMs(delay_ms);
    RTC_LOG(LS_INFO) << "SetBaseMinimumPlayoutDelayMs() to " << delay_ms
                     << " for recv stream with ssrc " << ssrc;
  }
  return true;
}

absl::optional<int> WebRtcVoiceMediaChannel::GetBaseMinimumPlayoutDelayMs(
    uint32_t ssrc) const {
  // SSRC of 0 represents the default receive stream.
  if (ssrc == 0) {
    return default_recv_base_minimum_delay_ms_;
  }

  const auto it = recv_streams_.find(ssrc);

  if (it != recv_streams_.end()) {
    return it->second->GetBaseMinimumPlayoutDelayMs();
  }
  return absl::nullopt;
}

bool WebRtcVoiceMediaChannel::CanInsertDtmf() {
  return dtmf_payload_type_.has_value() && send_;
}

void WebRtcVoiceMediaChannel::SetFrameDecryptor(
    uint32_t ssrc,
    rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto matching_stream = recv_streams_.find(ssrc);
  if (matching_stream != recv_streams_.end()) {
    matching_stream->second->SetFrameDecryptor(frame_decryptor);
  }
  // Handle unsignaled frame decryptors.
  if (ssrc == 0) {
    unsignaled_frame_decryptor_ = frame_decryptor;
  }
}

void WebRtcVoiceMediaChannel::SetFrameEncryptor(
    uint32_t ssrc,
    rtc::scoped_refptr<webrtc::FrameEncryptorInterface> frame_encryptor) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto matching_stream = send_streams_.find(ssrc);
  if (matching_stream != send_streams_.end()) {
    matching_stream->second->SetFrameEncryptor(frame_encryptor);
  }
}

bool WebRtcVoiceMediaChannel::InsertDtmf(uint32_t ssrc,
                                         int event,
                                         int duration) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_INFO) << "WebRtcVoiceMediaChannel::InsertDtmf";
  if (!CanInsertDtmf()) {
    return false;
  }

  // Figure out which WebRtcAudioSendStream to send the event on.
  auto it = ssrc != 0 ? send_streams_.find(ssrc) : send_streams_.begin();
  if (it == send_streams_.end()) {
    RTC_LOG(LS_WARNING) << "The specified ssrc " << ssrc << " is not in use.";
    return false;
  }
  if (event < kMinTelephoneEventCode || event > kMaxTelephoneEventCode) {
    RTC_LOG(LS_WARNING) << "DTMF event code " << event << " out of range.";
    return false;
  }
  RTC_DCHECK_NE(-1, dtmf_payload_freq_);
  return it->second->SendTelephoneEvent(*dtmf_payload_type_, dtmf_payload_freq_,
                                        event, duration);
}

void WebRtcVoiceMediaChannel::OnPacketReceived(rtc::CopyOnWriteBuffer packet,
                                               int64_t packet_time_us) {
  RTC_DCHECK_RUN_ON(&network_thread_checker_);
  // TODO(bugs.webrtc.org/11993): This code is very similar to what
  // WebRtcVideoChannel::OnPacketReceived does. For maintainability and
  // consistency it would be good to move the interaction with call_->Receiver()
  // to a common implementation and provide a callback on the worker thread
  // for the exception case (DELIVERY_UNKNOWN_SSRC) and how retry is attempted.
  worker_thread_->PostTask(SafeTask(task_safety_.flag(), [this, packet,
                                                          packet_time_us] {
    RTC_DCHECK_RUN_ON(worker_thread_);

    webrtc::PacketReceiver::DeliveryStatus delivery_result =
        call_->Receiver()->DeliverPacket(webrtc::MediaType::AUDIO, packet,
                                         packet_time_us);

    if (delivery_result != webrtc::PacketReceiver::DELIVERY_UNKNOWN_SSRC) {
      return;
    }

    // Create an unsignaled receive stream for this previously not received
    // ssrc. If there already is N unsignaled receive streams, delete the
    // oldest. See: https://bugs.chromium.org/p/webrtc/issues/detail?id=5208
    uint32_t ssrc = ParseRtpSsrc(packet);
    RTC_DCHECK(!absl::c_linear_search(unsignaled_recv_ssrcs_, ssrc));

    // Add new stream.
    StreamParams sp = unsignaled_stream_params_;
    sp.ssrcs.push_back(ssrc);
    RTC_LOG(LS_INFO) << "Creating unsignaled receive stream for SSRC=" << ssrc;
    if (!AddRecvStream(sp)) {
      RTC_LOG(LS_WARNING) << "Could not create unsignaled receive stream.";
      return;
    }
    unsignaled_recv_ssrcs_.push_back(ssrc);
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.Audio.NumOfUnsignaledStreams",
                                unsignaled_recv_ssrcs_.size(), 1, 100, 101);

    // Remove oldest unsignaled stream, if we have too many.
    if (unsignaled_recv_ssrcs_.size() > kMaxUnsignaledRecvStreams) {
      uint32_t remove_ssrc = unsignaled_recv_ssrcs_.front();
      RTC_DLOG(LS_INFO) << "Removing unsignaled receive stream with SSRC="
                        << remove_ssrc;
      RemoveRecvStream(remove_ssrc);
    }
    RTC_DCHECK_GE(kMaxUnsignaledRecvStreams, unsignaled_recv_ssrcs_.size());

    SetOutputVolume(ssrc, default_recv_volume_);
    SetBaseMinimumPlayoutDelayMs(ssrc, default_recv_base_minimum_delay_ms_);

    // The default sink can only be attached to one stream at a time, so we hook
    // it up to the *latest* unsignaled stream we've seen, in order to support
    // the case where the SSRC of one unsignaled stream changes.
    if (default_sink_) {
      for (uint32_t drop_ssrc : unsignaled_recv_ssrcs_) {
        auto it = recv_streams_.find(drop_ssrc);
        it->second->SetRawAudioSink(nullptr);
      }
      std::unique_ptr<webrtc::AudioSinkInterface> proxy_sink(
          new ProxySink(default_sink_.get()));
      SetRawAudioSink(ssrc, std::move(proxy_sink));
    }

    delivery_result = call_->Receiver()->DeliverPacket(webrtc::MediaType::AUDIO,
                                                       packet, packet_time_us);
    RTC_DCHECK_NE(webrtc::PacketReceiver::DELIVERY_UNKNOWN_SSRC,
                  delivery_result);
  }));
}

void WebRtcVoiceMediaChannel::OnPacketSent(const rtc::SentPacket& sent_packet) {
  RTC_DCHECK_RUN_ON(&network_thread_checker_);
  // TODO(tommi): We shouldn't need to go through call_ to deliver this
  // notification. We should already have direct access to
  // video_send_delay_stats_ and transport_send_ptr_ via `stream_`.
  // So we should be able to remove OnSentPacket from Call and handle this per
  // channel instead. At the moment Call::OnSentPacket calls OnSentPacket for
  // the video stats, which we should be able to skip.
  call_->OnSentPacket(sent_packet);
}

void WebRtcVoiceMediaChannel::OnNetworkRouteChanged(
    absl::string_view transport_name,
    const rtc::NetworkRoute& network_route) {
  RTC_DCHECK_RUN_ON(&network_thread_checker_);

  call_->OnAudioTransportOverheadChanged(network_route.packet_overhead);

  worker_thread_->PostTask(SafeTask(
      task_safety_.flag(),
      [this, name = std::string(transport_name), route = network_route] {
        RTC_DCHECK_RUN_ON(worker_thread_);
        call_->GetTransportControllerSend()->OnNetworkRouteChanged(name, route);
      }));
}

bool WebRtcVoiceMediaChannel::MuteStream(uint32_t ssrc, bool muted) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  const auto it = send_streams_.find(ssrc);
  if (it == send_streams_.end()) {
    RTC_LOG(LS_WARNING) << "The specified ssrc " << ssrc << " is not in use.";
    return false;
  }
  it->second->SetMuted(muted);

  // TODO(solenberg):
  // We set the AGC to mute state only when all the channels are muted.
  // This implementation is not ideal, instead we should signal the AGC when
  // the mic channel is muted/unmuted. We can't do it today because there
  // is no good way to know which stream is mapping to the mic channel.
  bool all_muted = muted;
  for (const auto& kv : send_streams_) {
    all_muted = all_muted && kv.second->muted();
  }
  webrtc::AudioProcessing* ap = engine()->apm();
  if (ap) {
    ap->set_output_will_be_muted(all_muted);
  }

  return true;
}

bool WebRtcVoiceMediaChannel::SetMaxSendBitrate(int bps) {
  RTC_LOG(LS_INFO) << "WebRtcVoiceMediaChannel::SetMaxSendBitrate.";
  max_send_bitrate_bps_ = bps;
  bool success = true;
  for (const auto& kv : send_streams_) {
    if (!kv.second->SetMaxSendBitrate(max_send_bitrate_bps_)) {
      success = false;
    }
  }
  return success;
}

void WebRtcVoiceMediaChannel::OnReadyToSend(bool ready) {
  RTC_DCHECK_RUN_ON(&network_thread_checker_);
  RTC_LOG(LS_VERBOSE) << "OnReadyToSend: " << (ready ? "Ready." : "Not ready.");
  call_->SignalChannelNetworkState(
      webrtc::MediaType::AUDIO,
      ready ? webrtc::kNetworkUp : webrtc::kNetworkDown);
}

bool WebRtcVoiceMediaChannel::GetStats(VoiceMediaInfo* info,
                                       bool get_and_clear_legacy_stats) {
  TRACE_EVENT0("webrtc", "WebRtcVoiceMediaChannel::GetStats");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(info);

  // Get SSRC and stats for each sender.
  RTC_DCHECK_EQ(info->senders.size(), 0U);
  for (const auto& stream : send_streams_) {
    webrtc::AudioSendStream::Stats stats =
        stream.second->GetStats(recv_streams_.size() > 0);
    VoiceSenderInfo sinfo;
    sinfo.add_ssrc(stats.local_ssrc);
    sinfo.payload_bytes_sent = stats.payload_bytes_sent;
    sinfo.header_and_padding_bytes_sent = stats.header_and_padding_bytes_sent;
    sinfo.retransmitted_bytes_sent = stats.retransmitted_bytes_sent;
    sinfo.packets_sent = stats.packets_sent;
    sinfo.total_packet_send_delay = stats.total_packet_send_delay;
    sinfo.retransmitted_packets_sent = stats.retransmitted_packets_sent;
    sinfo.packets_lost = stats.packets_lost;
    sinfo.fraction_lost = stats.fraction_lost;
    sinfo.nacks_rcvd = stats.nacks_rcvd;
    sinfo.target_bitrate = stats.target_bitrate_bps;
    sinfo.codec_name = stats.codec_name;
    sinfo.codec_payload_type = stats.codec_payload_type;
    sinfo.jitter_ms = stats.jitter_ms;
    sinfo.rtt_ms = stats.rtt_ms;
    sinfo.audio_level = stats.audio_level;
    sinfo.total_input_energy = stats.total_input_energy;
    sinfo.total_input_duration = stats.total_input_duration;
    sinfo.ana_statistics = stats.ana_statistics;
    sinfo.apm_statistics = stats.apm_statistics;
    sinfo.report_block_datas = std::move(stats.report_block_datas);

    auto encodings = stream.second->rtp_parameters().encodings;
    if (!encodings.empty()) {
      sinfo.active = encodings[0].active;
    }

    info->senders.push_back(sinfo);
  }

  // Get SSRC and stats for each receiver.
  RTC_DCHECK_EQ(info->receivers.size(), 0U);
  for (const auto& stream : recv_streams_) {
    uint32_t ssrc = stream.first;
    // When SSRCs are unsignaled, there's only one audio MediaStreamTrack, but
    // multiple RTP streams can be received over time (if the SSRC changes for
    // whatever reason). We only want the RTCMediaStreamTrackStats to represent
    // the stats for the most recent stream (the one whose audio is actually
    // routed to the MediaStreamTrack), so here we ignore any unsignaled SSRCs
    // except for the most recent one (last in the vector). This is somewhat of
    // a hack, and means you don't get *any* stats for these inactive streams,
    // but it's slightly better than the previous behavior, which was "highest
    // SSRC wins".
    // See: https://bugs.chromium.org/p/webrtc/issues/detail?id=8158
    if (!unsignaled_recv_ssrcs_.empty()) {
      auto end_it = --unsignaled_recv_ssrcs_.end();
      if (absl::linear_search(unsignaled_recv_ssrcs_.begin(), end_it, ssrc)) {
        continue;
      }
    }
    webrtc::AudioReceiveStreamInterface::Stats stats =
        stream.second->GetStats(get_and_clear_legacy_stats);
    VoiceReceiverInfo rinfo;
    rinfo.add_ssrc(stats.remote_ssrc);
    rinfo.payload_bytes_rcvd = stats.payload_bytes_rcvd;
    rinfo.header_and_padding_bytes_rcvd = stats.header_and_padding_bytes_rcvd;
    rinfo.packets_rcvd = stats.packets_rcvd;
    rinfo.fec_packets_received = stats.fec_packets_received;
    rinfo.fec_packets_discarded = stats.fec_packets_discarded;
    rinfo.packets_lost = stats.packets_lost;
    rinfo.packets_discarded = stats.packets_discarded;
    rinfo.codec_name = stats.codec_name;
    rinfo.codec_payload_type = stats.codec_payload_type;
    rinfo.jitter_ms = stats.jitter_ms;
    rinfo.jitter_buffer_ms = stats.jitter_buffer_ms;
    rinfo.jitter_buffer_preferred_ms = stats.jitter_buffer_preferred_ms;
    rinfo.delay_estimate_ms = stats.delay_estimate_ms;
    rinfo.audio_level = stats.audio_level;
    rinfo.total_output_energy = stats.total_output_energy;
    rinfo.total_samples_received = stats.total_samples_received;
    rinfo.total_output_duration = stats.total_output_duration;
    rinfo.concealed_samples = stats.concealed_samples;
    rinfo.silent_concealed_samples = stats.silent_concealed_samples;
    rinfo.concealment_events = stats.concealment_events;
    rinfo.jitter_buffer_delay_seconds = stats.jitter_buffer_delay_seconds;
    rinfo.jitter_buffer_emitted_count = stats.jitter_buffer_emitted_count;
    rinfo.jitter_buffer_target_delay_seconds =
        stats.jitter_buffer_target_delay_seconds;
    rinfo.jitter_buffer_minimum_delay_seconds =
        stats.jitter_buffer_minimum_delay_seconds;
    rinfo.inserted_samples_for_deceleration =
        stats.inserted_samples_for_deceleration;
    rinfo.removed_samples_for_acceleration =
        stats.removed_samples_for_acceleration;
    rinfo.expand_rate = stats.expand_rate;
    rinfo.speech_expand_rate = stats.speech_expand_rate;
    rinfo.secondary_decoded_rate = stats.secondary_decoded_rate;
    rinfo.secondary_discarded_rate = stats.secondary_discarded_rate;
    rinfo.accelerate_rate = stats.accelerate_rate;
    rinfo.preemptive_expand_rate = stats.preemptive_expand_rate;
    rinfo.delayed_packet_outage_samples = stats.delayed_packet_outage_samples;
    rinfo.decoding_calls_to_silence_generator =
        stats.decoding_calls_to_silence_generator;
    rinfo.decoding_calls_to_neteq = stats.decoding_calls_to_neteq;
    rinfo.decoding_normal = stats.decoding_normal;
    rinfo.decoding_plc = stats.decoding_plc;
    rinfo.decoding_codec_plc = stats.decoding_codec_plc;
    rinfo.decoding_cng = stats.decoding_cng;
    rinfo.decoding_plc_cng = stats.decoding_plc_cng;
    rinfo.decoding_muted_output = stats.decoding_muted_output;
    rinfo.capture_start_ntp_time_ms = stats.capture_start_ntp_time_ms;
    rinfo.last_packet_received_timestamp_ms =
        stats.last_packet_received_timestamp_ms;
    rinfo.estimated_playout_ntp_timestamp_ms =
        stats.estimated_playout_ntp_timestamp_ms;
    rinfo.jitter_buffer_flushes = stats.jitter_buffer_flushes;
    rinfo.relative_packet_arrival_delay_seconds =
        stats.relative_packet_arrival_delay_seconds;
    rinfo.interruption_count = stats.interruption_count;
    rinfo.total_interruption_duration_ms = stats.total_interruption_duration_ms;
    rinfo.last_sender_report_timestamp_ms =
        stats.last_sender_report_timestamp_ms;
    rinfo.last_sender_report_remote_timestamp_ms =
        stats.last_sender_report_remote_timestamp_ms;
    rinfo.sender_reports_packets_sent = stats.sender_reports_packets_sent;
    rinfo.sender_reports_bytes_sent = stats.sender_reports_bytes_sent;
    rinfo.sender_reports_reports_count = stats.sender_reports_reports_count;
    rinfo.round_trip_time = stats.round_trip_time;
    rinfo.round_trip_time_measurements = stats.round_trip_time_measurements;
    rinfo.total_round_trip_time = stats.total_round_trip_time;

    if (recv_nack_enabled_) {
      rinfo.nacks_sent = stats.nacks_sent;
    }

    info->receivers.push_back(rinfo);
  }

  // Get codec info
  for (const AudioCodec& codec : send_codecs_) {
    webrtc::RtpCodecParameters codec_params = codec.ToCodecParameters();
    info->send_codecs.insert(
        std::make_pair(codec_params.payload_type, std::move(codec_params)));
  }
  for (const AudioCodec& codec : recv_codecs_) {
    webrtc::RtpCodecParameters codec_params = codec.ToCodecParameters();
    info->receive_codecs.insert(
        std::make_pair(codec_params.payload_type, std::move(codec_params)));
  }
  info->device_underrun_count = engine_->adm()->GetPlayoutUnderrunCount();

  return true;
}

void WebRtcVoiceMediaChannel::SetRawAudioSink(
    uint32_t ssrc,
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_VERBOSE) << "WebRtcVoiceMediaChannel::SetRawAudioSink: ssrc:"
                      << ssrc << " " << (sink ? "(ptr)" : "NULL");
  const auto it = recv_streams_.find(ssrc);
  if (it == recv_streams_.end()) {
    RTC_LOG(LS_WARNING) << "SetRawAudioSink: no recv stream " << ssrc;
    return;
  }
  it->second->SetRawAudioSink(std::move(sink));
}

void WebRtcVoiceMediaChannel::SetDefaultRawAudioSink(
    std::unique_ptr<webrtc::AudioSinkInterface> sink) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_LOG(LS_VERBOSE) << "WebRtcVoiceMediaChannel::SetDefaultRawAudioSink:";
  if (!unsignaled_recv_ssrcs_.empty()) {
    std::unique_ptr<webrtc::AudioSinkInterface> proxy_sink(
        sink ? new ProxySink(sink.get()) : nullptr);
    SetRawAudioSink(unsignaled_recv_ssrcs_.back(), std::move(proxy_sink));
  }
  default_sink_ = std::move(sink);
}

std::vector<webrtc::RtpSource> WebRtcVoiceMediaChannel::GetSources(
    uint32_t ssrc) const {
  auto it = recv_streams_.find(ssrc);
  if (it == recv_streams_.end()) {
    RTC_LOG(LS_ERROR) << "Attempting to get contributing sources for SSRC:"
                      << ssrc << " which doesn't exist.";
    return std::vector<webrtc::RtpSource>();
  }
  return it->second->GetSources();
}

void WebRtcVoiceMediaChannel::SetEncoderToPacketizerFrameTransformer(
    uint32_t ssrc,
    rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto matching_stream = send_streams_.find(ssrc);
  if (matching_stream == send_streams_.end()) {
    RTC_LOG(LS_INFO) << "Attempting to set frame transformer for SSRC:" << ssrc
                     << " which doesn't exist.";
    return;
  }
  matching_stream->second->SetEncoderToPacketizerFrameTransformer(
      std::move(frame_transformer));
}

void WebRtcVoiceMediaChannel::SetDepacketizerToDecoderFrameTransformer(
    uint32_t ssrc,
    rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (ssrc == 0) {
    // If the receiver is unsignaled, save the frame transformer and set it when
    // the stream is associated with an ssrc.
    unsignaled_frame_transformer_ = std::move(frame_transformer);
    return;
  }

  auto matching_stream = recv_streams_.find(ssrc);
  if (matching_stream == recv_streams_.end()) {
    RTC_LOG(LS_INFO) << "Attempting to set frame transformer for SSRC:" << ssrc
                     << " which doesn't exist.";
    return;
  }
  matching_stream->second->SetDepacketizerToDecoderFrameTransformer(
      std::move(frame_transformer));
}

bool WebRtcVoiceMediaChannel::SendRtp(const uint8_t* data,
                                      size_t len,
                                      const webrtc::PacketOptions& options) {
  MediaChannel::SendRtp(data, len, options);
  return true;
}

bool WebRtcVoiceMediaChannel::SendRtcp(const uint8_t* data, size_t len) {
  MediaChannel::SendRtcp(data, len);
  return true;
}

bool WebRtcVoiceMediaChannel::MaybeDeregisterUnsignaledRecvStream(
    uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  auto it = absl::c_find(unsignaled_recv_ssrcs_, ssrc);
  if (it != unsignaled_recv_ssrcs_.end()) {
    unsignaled_recv_ssrcs_.erase(it);
    return true;
  }
  return false;
}
}  // namespace cricket
