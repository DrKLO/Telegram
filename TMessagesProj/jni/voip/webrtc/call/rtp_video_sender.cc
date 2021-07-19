/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/rtp_video_sender.h"

#include <algorithm>
#include <memory>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "api/array_view.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video_codecs/video_codec.h"
#include "call/rtp_transport_controller_send_interface.h"
#include "modules/pacing/packet_router.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/rtp_rtcp/source/rtp_rtcp_impl2.h"
#include "modules/rtp_rtcp/source/rtp_sender.h"
#include "modules/utility/include/process_thread.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_queue.h"

namespace webrtc {

namespace webrtc_internal_rtp_video_sender {

RtpStreamSender::RtpStreamSender(
    std::unique_ptr<ModuleRtpRtcpImpl2> rtp_rtcp,
    std::unique_ptr<RTPSenderVideo> sender_video,
    std::unique_ptr<VideoFecGenerator> fec_generator)
    : rtp_rtcp(std::move(rtp_rtcp)),
      sender_video(std::move(sender_video)),
      fec_generator(std::move(fec_generator)) {}

RtpStreamSender::~RtpStreamSender() = default;

}  // namespace webrtc_internal_rtp_video_sender

namespace {
static const int kMinSendSidePacketHistorySize = 600;
// We don't do MTU discovery, so assume that we have the standard ethernet MTU.
static const size_t kPathMTU = 1500;

using webrtc_internal_rtp_video_sender::RtpStreamSender;

bool PayloadTypeSupportsSkippingFecPackets(const std::string& payload_name,
                                           const WebRtcKeyValueConfig& trials) {
  const VideoCodecType codecType = PayloadStringToCodecType(payload_name);
  if (codecType == kVideoCodecVP8 || codecType == kVideoCodecVP9) {
    return true;
  }
  if (codecType == kVideoCodecGeneric &&
      absl::StartsWith(trials.Lookup("WebRTC-GenericPictureId"), "Enabled")) {
    return true;
  }
  return false;
}

bool ShouldDisableRedAndUlpfec(bool flexfec_enabled,
                               const RtpConfig& rtp_config,
                               const WebRtcKeyValueConfig& trials) {
  // Consistency of NACK and RED+ULPFEC parameters is checked in this function.
  const bool nack_enabled = rtp_config.nack.rtp_history_ms > 0;

  // Shorthands.
  auto IsRedEnabled = [&]() { return rtp_config.ulpfec.red_payload_type >= 0; };
  auto IsUlpfecEnabled = [&]() {
    return rtp_config.ulpfec.ulpfec_payload_type >= 0;
  };

  bool should_disable_red_and_ulpfec = false;

  if (absl::StartsWith(trials.Lookup("WebRTC-DisableUlpFecExperiment"),
                       "Enabled")) {
    RTC_LOG(LS_INFO) << "Experiment to disable sending ULPFEC is enabled.";
    should_disable_red_and_ulpfec = true;
  }

  // If enabled, FlexFEC takes priority over RED+ULPFEC.
  if (flexfec_enabled) {
    if (IsUlpfecEnabled()) {
      RTC_LOG(LS_INFO)
          << "Both FlexFEC and ULPFEC are configured. Disabling ULPFEC.";
    }
    should_disable_red_and_ulpfec = true;
  }

  // Payload types without picture ID cannot determine that a stream is complete
  // without retransmitting FEC, so using ULPFEC + NACK for H.264 (for instance)
  // is a waste of bandwidth since FEC packets still have to be transmitted.
  // Note that this is not the case with FlexFEC.
  if (nack_enabled && IsUlpfecEnabled() &&
      !PayloadTypeSupportsSkippingFecPackets(rtp_config.payload_name, trials)) {
    RTC_LOG(LS_WARNING)
        << "Transmitting payload type without picture ID using "
           "NACK+ULPFEC is a waste of bandwidth since ULPFEC packets "
           "also have to be retransmitted. Disabling ULPFEC.";
    should_disable_red_and_ulpfec = true;
  }

  // Verify payload types.
  if (IsUlpfecEnabled() ^ IsRedEnabled()) {
    RTC_LOG(LS_WARNING)
        << "Only RED or only ULPFEC enabled, but not both. Disabling both.";
    should_disable_red_and_ulpfec = true;
  }

  return should_disable_red_and_ulpfec;
}

// TODO(brandtr): Update this function when we support multistream protection.
std::unique_ptr<VideoFecGenerator> MaybeCreateFecGenerator(
    Clock* clock,
    const RtpConfig& rtp,
    const std::map<uint32_t, RtpState>& suspended_ssrcs,
    int simulcast_index,
    const WebRtcKeyValueConfig& trials) {
  // If flexfec is configured that takes priority.
  if (rtp.flexfec.payload_type >= 0) {
    RTC_DCHECK_GE(rtp.flexfec.payload_type, 0);
    RTC_DCHECK_LE(rtp.flexfec.payload_type, 127);
    if (rtp.flexfec.ssrc == 0) {
      RTC_LOG(LS_WARNING) << "FlexFEC is enabled, but no FlexFEC SSRC given. "
                             "Therefore disabling FlexFEC.";
      return nullptr;
    }
    if (rtp.flexfec.protected_media_ssrcs.empty()) {
      RTC_LOG(LS_WARNING)
          << "FlexFEC is enabled, but no protected media SSRC given. "
             "Therefore disabling FlexFEC.";
      return nullptr;
    }

    if (rtp.flexfec.protected_media_ssrcs.size() > 1) {
      RTC_LOG(LS_WARNING)
          << "The supplied FlexfecConfig contained multiple protected "
             "media streams, but our implementation currently only "
             "supports protecting a single media stream. "
             "To avoid confusion, disabling FlexFEC completely.";
      return nullptr;
    }

    if (absl::c_find(rtp.flexfec.protected_media_ssrcs,
                     rtp.ssrcs[simulcast_index]) ==
        rtp.flexfec.protected_media_ssrcs.end()) {
      // Media SSRC not among flexfec protected SSRCs.
      return nullptr;
    }

    const RtpState* rtp_state = nullptr;
    auto it = suspended_ssrcs.find(rtp.flexfec.ssrc);
    if (it != suspended_ssrcs.end()) {
      rtp_state = &it->second;
    }

    RTC_DCHECK_EQ(1U, rtp.flexfec.protected_media_ssrcs.size());
    return std::make_unique<FlexfecSender>(
        rtp.flexfec.payload_type, rtp.flexfec.ssrc,
        rtp.flexfec.protected_media_ssrcs[0], rtp.mid, rtp.extensions,
        RTPSender::FecExtensionSizes(), rtp_state, clock);
  } else if (rtp.ulpfec.red_payload_type >= 0 &&
             rtp.ulpfec.ulpfec_payload_type >= 0 &&
             !ShouldDisableRedAndUlpfec(/*flexfec_enabled=*/false, rtp,
                                        trials)) {
    // Flexfec not configured, but ulpfec is and is not disabled.
    return std::make_unique<UlpfecGenerator>(
        rtp.ulpfec.red_payload_type, rtp.ulpfec.ulpfec_payload_type, clock);
  }

  // Not a single FEC is given.
  return nullptr;
}

std::vector<RtpStreamSender> CreateRtpStreamSenders(
    Clock* clock,
    const RtpConfig& rtp_config,
    const RtpSenderObservers& observers,
    int rtcp_report_interval_ms,
    Transport* send_transport,
    RtcpBandwidthObserver* bandwidth_callback,
    RtpTransportControllerSendInterface* transport,
    const std::map<uint32_t, RtpState>& suspended_ssrcs,
    RtcEventLog* event_log,
    RateLimiter* retransmission_rate_limiter,
    FrameEncryptorInterface* frame_encryptor,
    const CryptoOptions& crypto_options,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
    const WebRtcKeyValueConfig& trials) {
  RTC_DCHECK_GT(rtp_config.ssrcs.size(), 0);

  RtpRtcpInterface::Configuration configuration;
  configuration.clock = clock;
  configuration.audio = false;
  configuration.receiver_only = false;
  configuration.outgoing_transport = send_transport;
  configuration.intra_frame_callback = observers.intra_frame_callback;
  configuration.rtcp_loss_notification_observer =
      observers.rtcp_loss_notification_observer;
  configuration.bandwidth_callback = bandwidth_callback;
  configuration.network_state_estimate_observer =
      transport->network_state_estimate_observer();
  configuration.transport_feedback_callback =
      transport->transport_feedback_observer();
  configuration.rtt_stats = observers.rtcp_rtt_stats;
  configuration.rtcp_packet_type_counter_observer =
      observers.rtcp_type_observer;
  configuration.report_block_data_observer =
      observers.report_block_data_observer;
  configuration.paced_sender = transport->packet_sender();
  configuration.send_bitrate_observer = observers.bitrate_observer;
  configuration.send_side_delay_observer = observers.send_delay_observer;
  configuration.send_packet_observer = observers.send_packet_observer;
  configuration.event_log = event_log;
  configuration.retransmission_rate_limiter = retransmission_rate_limiter;
  configuration.rtp_stats_callback = observers.rtp_stats;
  configuration.frame_encryptor = frame_encryptor;
  configuration.require_frame_encryption =
      crypto_options.sframe.require_frame_encryption;
  configuration.extmap_allow_mixed = rtp_config.extmap_allow_mixed;
  configuration.rtcp_report_interval_ms = rtcp_report_interval_ms;
  configuration.field_trials = &trials;

  std::vector<RtpStreamSender> rtp_streams;

  RTC_DCHECK(rtp_config.rtx.ssrcs.empty() ||
             rtp_config.rtx.ssrcs.size() == rtp_config.ssrcs.size());
  for (size_t i = 0; i < rtp_config.ssrcs.size(); ++i) {
    RTPSenderVideo::Config video_config;
    configuration.local_media_ssrc = rtp_config.ssrcs[i];

    std::unique_ptr<VideoFecGenerator> fec_generator =
        MaybeCreateFecGenerator(clock, rtp_config, suspended_ssrcs, i, trials);
    configuration.fec_generator = fec_generator.get();

    configuration.rtx_send_ssrc =
        rtp_config.GetRtxSsrcAssociatedWithMediaSsrc(rtp_config.ssrcs[i]);
    RTC_DCHECK_EQ(configuration.rtx_send_ssrc.has_value(),
                  !rtp_config.rtx.ssrcs.empty());

    configuration.need_rtp_packet_infos = rtp_config.lntf.enabled;

    std::unique_ptr<ModuleRtpRtcpImpl2> rtp_rtcp(
        ModuleRtpRtcpImpl2::Create(configuration));
    rtp_rtcp->SetSendingStatus(false);
    rtp_rtcp->SetSendingMediaStatus(false);
    rtp_rtcp->SetRTCPStatus(RtcpMode::kCompound);
    // Set NACK.
    rtp_rtcp->SetStorePacketsStatus(true, kMinSendSidePacketHistorySize);

    video_config.clock = configuration.clock;
    video_config.rtp_sender = rtp_rtcp->RtpSender();
    video_config.frame_encryptor = frame_encryptor;
    video_config.require_frame_encryption =
        crypto_options.sframe.require_frame_encryption;
    video_config.enable_retransmit_all_layers = false;
    video_config.field_trials = &trials;

    const bool using_flexfec =
        fec_generator &&
        fec_generator->GetFecType() == VideoFecGenerator::FecType::kFlexFec;
    const bool should_disable_red_and_ulpfec =
        ShouldDisableRedAndUlpfec(using_flexfec, rtp_config, trials);
    if (!should_disable_red_and_ulpfec &&
        rtp_config.ulpfec.red_payload_type != -1) {
      video_config.red_payload_type = rtp_config.ulpfec.red_payload_type;
    }
    if (fec_generator) {
      video_config.fec_type = fec_generator->GetFecType();
      video_config.fec_overhead_bytes = fec_generator->MaxPacketOverhead();
    }
    video_config.frame_transformer = frame_transformer;
    video_config.send_transport_queue = transport->GetWorkerQueue()->Get();
    auto sender_video = std::make_unique<RTPSenderVideo>(video_config);
    rtp_streams.emplace_back(std::move(rtp_rtcp), std::move(sender_video),
                             std::move(fec_generator));
  }
  return rtp_streams;
}

absl::optional<VideoCodecType> GetVideoCodecType(const RtpConfig& config) {
  if (config.raw_payload) {
    return absl::nullopt;
  }
  return PayloadStringToCodecType(config.payload_name);
}
bool TransportSeqNumExtensionConfigured(const RtpConfig& config) {
  return absl::c_any_of(config.extensions, [](const RtpExtension& ext) {
    return ext.uri == RtpExtension::kTransportSequenceNumberUri;
  });
}

// Returns true when some coded video sequence can be decoded starting with
// this frame without requiring any previous frames.
// e.g. it is the same as a key frame when spatial scalability is not used.
// When spatial scalability is used, then it is true for layer frames of
// a key frame without inter-layer dependencies.
bool IsFirstFrameOfACodedVideoSequence(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  if (encoded_image._frameType != VideoFrameType::kVideoFrameKey) {
    return false;
  }

  if (codec_specific_info != nullptr) {
    if (codec_specific_info->generic_frame_info.has_value()) {
      // This function is used before
      // `codec_specific_info->generic_frame_info->frame_diffs` are calculated,
      // so need to use a more complicated way to check for presence of the
      // dependencies.
      return absl::c_none_of(
          codec_specific_info->generic_frame_info->encoder_buffers,
          [](const CodecBufferUsage& buffer) { return buffer.referenced; });
    }

    if (codec_specific_info->codecType == VideoCodecType::kVideoCodecVP8 ||
        codec_specific_info->codecType == VideoCodecType::kVideoCodecH264 ||
        codec_specific_info->codecType == VideoCodecType::kVideoCodecGeneric) {
      // These codecs do not support intra picture dependencies, so a frame
      // marked as a key frame should be a key frame.
      return true;
    }
  }

  // Without depenedencies described in generic format do an educated guess.
  // It might be wrong for VP9 with spatial layer 0 skipped or higher spatial
  // layer not depending on the spatial layer 0. This corner case is unimportant
  // for current usage of this helper function.

  // Use <= to accept both 0 (i.e. the first) and nullopt (i.e. the only).
  return encoded_image.SpatialIndex() <= 0;
}

}  // namespace

RtpVideoSender::RtpVideoSender(
    Clock* clock,
    std::map<uint32_t, RtpState> suspended_ssrcs,
    const std::map<uint32_t, RtpPayloadState>& states,
    const RtpConfig& rtp_config,
    int rtcp_report_interval_ms,
    Transport* send_transport,
    const RtpSenderObservers& observers,
    RtpTransportControllerSendInterface* transport,
    RtcEventLog* event_log,
    RateLimiter* retransmission_limiter,
    std::unique_ptr<FecController> fec_controller,
    FrameEncryptorInterface* frame_encryptor,
    const CryptoOptions& crypto_options,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer)
    : send_side_bwe_with_overhead_(!absl::StartsWith(
          field_trials_.Lookup("WebRTC-SendSideBwe-WithOverhead"),
          "Disabled")),
      use_frame_rate_for_overhead_(absl::StartsWith(
          field_trials_.Lookup("WebRTC-Video-UseFrameRateForOverhead"),
          "Enabled")),
      has_packet_feedback_(TransportSeqNumExtensionConfigured(rtp_config)),
      simulate_vp9_structure_(absl::StartsWith(
          field_trials_.Lookup("WebRTC-Vp9DependencyDescriptor"),
          "Enabled")),
      active_(false),
      module_process_thread_(nullptr),
      suspended_ssrcs_(std::move(suspended_ssrcs)),
      fec_controller_(std::move(fec_controller)),
      fec_allowed_(true),
      rtp_streams_(CreateRtpStreamSenders(clock,
                                          rtp_config,
                                          observers,
                                          rtcp_report_interval_ms,
                                          send_transport,
                                          transport->GetBandwidthObserver(),
                                          transport,
                                          suspended_ssrcs_,
                                          event_log,
                                          retransmission_limiter,
                                          frame_encryptor,
                                          crypto_options,
                                          std::move(frame_transformer),
                                          field_trials_)),
      rtp_config_(rtp_config),
      codec_type_(GetVideoCodecType(rtp_config)),
      transport_(transport),
      transport_overhead_bytes_per_packet_(0),
      encoder_target_rate_bps_(0),
      frame_counts_(rtp_config.ssrcs.size()),
      frame_count_observer_(observers.frame_count_observer) {
  RTC_DCHECK_EQ(rtp_config_.ssrcs.size(), rtp_streams_.size());
  if (send_side_bwe_with_overhead_ && has_packet_feedback_)
    transport_->IncludeOverheadInPacedSender();
  module_process_thread_checker_.Detach();
  // SSRCs are assumed to be sorted in the same order as |rtp_modules|.
  for (uint32_t ssrc : rtp_config_.ssrcs) {
    // Restore state if it previously existed.
    const RtpPayloadState* state = nullptr;
    auto it = states.find(ssrc);
    if (it != states.end()) {
      state = &it->second;
      shared_frame_id_ = std::max(shared_frame_id_, state->shared_frame_id);
    }
    params_.push_back(RtpPayloadParams(ssrc, state, field_trials_));
  }

  // RTP/RTCP initialization.

  for (size_t i = 0; i < rtp_config_.extensions.size(); ++i) {
    const std::string& extension = rtp_config_.extensions[i].uri;
    int id = rtp_config_.extensions[i].id;
    RTC_DCHECK(RtpExtension::IsSupportedForVideo(extension));
    for (const RtpStreamSender& stream : rtp_streams_) {
      stream.rtp_rtcp->RegisterRtpHeaderExtension(extension, id);
    }
  }

  ConfigureSsrcs();
  ConfigureRids();

  if (!rtp_config_.mid.empty()) {
    for (const RtpStreamSender& stream : rtp_streams_) {
      stream.rtp_rtcp->SetMid(rtp_config_.mid);
    }
  }

  bool fec_enabled = false;
  for (const RtpStreamSender& stream : rtp_streams_) {
    // Simulcast has one module for each layer. Set the CNAME on all modules.
    stream.rtp_rtcp->SetCNAME(rtp_config_.c_name.c_str());
    stream.rtp_rtcp->SetMaxRtpPacketSize(rtp_config_.max_packet_size);
    stream.rtp_rtcp->RegisterSendPayloadFrequency(rtp_config_.payload_type,
                                                  kVideoPayloadTypeFrequency);
    if (stream.fec_generator != nullptr) {
      fec_enabled = true;
    }
  }
  // Currently, both ULPFEC and FlexFEC use the same FEC rate calculation logic,
  // so enable that logic if either of those FEC schemes are enabled.
  fec_controller_->SetProtectionMethod(fec_enabled, NackEnabled());

  fec_controller_->SetProtectionCallback(this);
  // Signal congestion controller this object is ready for OnPacket* callbacks.
  transport_->GetStreamFeedbackProvider()->RegisterStreamFeedbackObserver(
      rtp_config_.ssrcs, this);
}

RtpVideoSender::~RtpVideoSender() {
  SetActiveModulesLocked(
      std::vector<bool>(rtp_streams_.size(), /*active=*/false));
  transport_->GetStreamFeedbackProvider()->DeRegisterStreamFeedbackObserver(
      this);
}

void RtpVideoSender::RegisterProcessThread(
    ProcessThread* module_process_thread) {
  RTC_DCHECK_RUN_ON(&module_process_thread_checker_);
  RTC_DCHECK(!module_process_thread_);
  module_process_thread_ = module_process_thread;

  for (const RtpStreamSender& stream : rtp_streams_) {
    module_process_thread_->RegisterModule(stream.rtp_rtcp.get(),
                                           RTC_FROM_HERE);
  }
}

void RtpVideoSender::DeRegisterProcessThread() {
  RTC_DCHECK_RUN_ON(&module_process_thread_checker_);
  for (const RtpStreamSender& stream : rtp_streams_)
    module_process_thread_->DeRegisterModule(stream.rtp_rtcp.get());
}

void RtpVideoSender::SetActive(bool active) {
  MutexLock lock(&mutex_);
  if (active_ == active)
    return;
  const std::vector<bool> active_modules(rtp_streams_.size(), active);
  SetActiveModulesLocked(active_modules);
}

void RtpVideoSender::SetActiveModules(const std::vector<bool> active_modules) {
  MutexLock lock(&mutex_);
  return SetActiveModulesLocked(active_modules);
}

void RtpVideoSender::SetActiveModulesLocked(
    const std::vector<bool> active_modules) {
  RTC_DCHECK_EQ(rtp_streams_.size(), active_modules.size());
  active_ = false;
  for (size_t i = 0; i < active_modules.size(); ++i) {
    if (active_modules[i]) {
      active_ = true;
    }

    RtpRtcpInterface& rtp_module = *rtp_streams_[i].rtp_rtcp;
    const bool was_active = rtp_module.SendingMedia();
    const bool should_be_active = active_modules[i];

    // Sends a kRtcpByeCode when going from true to false.
    rtp_module.SetSendingStatus(active_modules[i]);

    if (was_active && !should_be_active) {
      // Disabling media, remove from packet router map to reduce size and
      // prevent any stray packets in the pacer from asynchronously arriving
      // to a disabled module.
      transport_->packet_router()->RemoveSendRtpModule(&rtp_module);
    }

    // If set to false this module won't send media.
    rtp_module.SetSendingMediaStatus(active_modules[i]);

    if (!was_active && should_be_active) {
      // Turning on media, register with packet router.
      transport_->packet_router()->AddSendRtpModule(&rtp_module,
                                                    /*remb_candidate=*/true);
    }
  }
}

bool RtpVideoSender::IsActive() {
  MutexLock lock(&mutex_);
  return IsActiveLocked();
}

bool RtpVideoSender::IsActiveLocked() {
  return active_ && !rtp_streams_.empty();
}

EncodedImageCallback::Result RtpVideoSender::OnEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  fec_controller_->UpdateWithEncodedData(encoded_image.size(),
                                         encoded_image._frameType);
  MutexLock lock(&mutex_);
  RTC_DCHECK(!rtp_streams_.empty());
  if (!active_)
    return Result(Result::ERROR_SEND_FAILED);

  shared_frame_id_++;
  size_t stream_index = 0;
  if (codec_specific_info &&
      (codec_specific_info->codecType == kVideoCodecVP8 ||
       codec_specific_info->codecType == kVideoCodecH264 ||
       codec_specific_info->codecType == kVideoCodecGeneric)) {
    // Map spatial index to simulcast.
    stream_index = encoded_image.SpatialIndex().value_or(0);
  }
  RTC_DCHECK_LT(stream_index, rtp_streams_.size());

  uint32_t rtp_timestamp =
      encoded_image.Timestamp() +
      rtp_streams_[stream_index].rtp_rtcp->StartTimestamp();

  // RTCPSender has it's own copy of the timestamp offset, added in
  // RTCPSender::BuildSR, hence we must not add the in the offset for this call.
  // TODO(nisse): Delete RTCPSender:timestamp_offset_, and see if we can confine
  // knowledge of the offset to a single place.
  if (!rtp_streams_[stream_index].rtp_rtcp->OnSendingRtpFrame(
          encoded_image.Timestamp(), encoded_image.capture_time_ms_,
          rtp_config_.payload_type,
          encoded_image._frameType == VideoFrameType::kVideoFrameKey)) {
    // The payload router could be active but this module isn't sending.
    return Result(Result::ERROR_SEND_FAILED);
  }

  absl::optional<int64_t> expected_retransmission_time_ms;
  if (encoded_image.RetransmissionAllowed()) {
    expected_retransmission_time_ms =
        rtp_streams_[stream_index].rtp_rtcp->ExpectedRetransmissionTimeMs();
  }

  if (IsFirstFrameOfACodedVideoSequence(encoded_image, codec_specific_info)) {
    // If encoder adapter produce FrameDependencyStructure, pass it so that
    // dependency descriptor rtp header extension can be used.
    // If not supported, disable using dependency descriptor by passing nullptr.
    RTPSenderVideo& sender_video = *rtp_streams_[stream_index].sender_video;
    if (codec_specific_info && codec_specific_info->template_structure) {
      sender_video.SetVideoStructure(&*codec_specific_info->template_structure);
    } else if (simulate_vp9_structure_ && codec_specific_info &&
               codec_specific_info->codecType == kVideoCodecVP9) {
      FrameDependencyStructure structure =
          RtpPayloadParams::MinimalisticVp9Structure(
              codec_specific_info->codecSpecific.VP9);
      sender_video.SetVideoStructure(&structure);
    } else {
      sender_video.SetVideoStructure(nullptr);
    }
  }

  bool send_result = rtp_streams_[stream_index].sender_video->SendEncodedImage(
      rtp_config_.payload_type, codec_type_, rtp_timestamp, encoded_image,
      params_[stream_index].GetRtpVideoHeader(
          encoded_image, codec_specific_info, shared_frame_id_),
      expected_retransmission_time_ms);
  if (frame_count_observer_) {
    FrameCounts& counts = frame_counts_[stream_index];
    if (encoded_image._frameType == VideoFrameType::kVideoFrameKey) {
      ++counts.key_frames;
    } else if (encoded_image._frameType == VideoFrameType::kVideoFrameDelta) {
      ++counts.delta_frames;
    } else {
      RTC_DCHECK(encoded_image._frameType == VideoFrameType::kEmptyFrame);
    }
    frame_count_observer_->FrameCountUpdated(counts,
                                             rtp_config_.ssrcs[stream_index]);
  }
  if (!send_result)
    return Result(Result::ERROR_SEND_FAILED);

  return Result(Result::OK, rtp_timestamp);
}

void RtpVideoSender::OnBitrateAllocationUpdated(
    const VideoBitrateAllocation& bitrate) {
  MutexLock lock(&mutex_);
  if (IsActiveLocked()) {
    if (rtp_streams_.size() == 1) {
      // If spatial scalability is enabled, it is covered by a single stream.
      rtp_streams_[0].rtp_rtcp->SetVideoBitrateAllocation(bitrate);
    } else {
      std::vector<absl::optional<VideoBitrateAllocation>> layer_bitrates =
          bitrate.GetSimulcastAllocations();
      // Simulcast is in use, split the VideoBitrateAllocation into one struct
      // per rtp stream, moving over the temporal layer allocation.
      for (size_t i = 0; i < rtp_streams_.size(); ++i) {
        // The next spatial layer could be used if the current one is
        // inactive.
        if (layer_bitrates[i]) {
          rtp_streams_[i].rtp_rtcp->SetVideoBitrateAllocation(
              *layer_bitrates[i]);
        } else {
          // Signal a 0 bitrate on a simulcast stream.
          rtp_streams_[i].rtp_rtcp->SetVideoBitrateAllocation(
              VideoBitrateAllocation());
        }
      }
    }
  }
}
void RtpVideoSender::OnVideoLayersAllocationUpdated(
    const VideoLayersAllocation& allocation) {
  MutexLock lock(&mutex_);
  if (IsActiveLocked()) {
    for (size_t i = 0; i < rtp_streams_.size(); ++i) {
      VideoLayersAllocation stream_allocation = allocation;
      stream_allocation.rtp_stream_index = i;
      rtp_streams_[i].sender_video->SetVideoLayersAllocation(
          std::move(stream_allocation));
    }
  }
}

bool RtpVideoSender::NackEnabled() const {
  const bool nack_enabled = rtp_config_.nack.rtp_history_ms > 0;
  return nack_enabled;
}

uint32_t RtpVideoSender::GetPacketizationOverheadRate() const {
  uint32_t packetization_overhead_bps = 0;
  for (size_t i = 0; i < rtp_streams_.size(); ++i) {
    if (rtp_streams_[i].rtp_rtcp->SendingMedia()) {
      packetization_overhead_bps +=
          rtp_streams_[i].sender_video->PacketizationOverheadBps();
    }
  }
  return packetization_overhead_bps;
}

void RtpVideoSender::DeliverRtcp(const uint8_t* packet, size_t length) {
  // Runs on a network thread.
  for (const RtpStreamSender& stream : rtp_streams_)
    stream.rtp_rtcp->IncomingRtcpPacket(packet, length);
}

void RtpVideoSender::ConfigureSsrcs() {
  // Configure regular SSRCs.
  RTC_CHECK(ssrc_to_rtp_module_.empty());
  for (size_t i = 0; i < rtp_config_.ssrcs.size(); ++i) {
    uint32_t ssrc = rtp_config_.ssrcs[i];
    RtpRtcpInterface* const rtp_rtcp = rtp_streams_[i].rtp_rtcp.get();

    // Restore RTP state if previous existed.
    auto it = suspended_ssrcs_.find(ssrc);
    if (it != suspended_ssrcs_.end())
      rtp_rtcp->SetRtpState(it->second);

    ssrc_to_rtp_module_[ssrc] = rtp_rtcp;
  }

  // Set up RTX if available.
  if (rtp_config_.rtx.ssrcs.empty())
    return;

  RTC_DCHECK_EQ(rtp_config_.rtx.ssrcs.size(), rtp_config_.ssrcs.size());
  for (size_t i = 0; i < rtp_config_.rtx.ssrcs.size(); ++i) {
    uint32_t ssrc = rtp_config_.rtx.ssrcs[i];
    RtpRtcpInterface* const rtp_rtcp = rtp_streams_[i].rtp_rtcp.get();
    auto it = suspended_ssrcs_.find(ssrc);
    if (it != suspended_ssrcs_.end())
      rtp_rtcp->SetRtxState(it->second);
  }

  // Configure RTX payload types.
  RTC_DCHECK_GE(rtp_config_.rtx.payload_type, 0);
  for (const RtpStreamSender& stream : rtp_streams_) {
    stream.rtp_rtcp->SetRtxSendPayloadType(rtp_config_.rtx.payload_type,
                                           rtp_config_.payload_type);
    stream.rtp_rtcp->SetRtxSendStatus(kRtxRetransmitted |
                                      kRtxRedundantPayloads);
  }
  if (rtp_config_.ulpfec.red_payload_type != -1 &&
      rtp_config_.ulpfec.red_rtx_payload_type != -1) {
    for (const RtpStreamSender& stream : rtp_streams_) {
      stream.rtp_rtcp->SetRtxSendPayloadType(
          rtp_config_.ulpfec.red_rtx_payload_type,
          rtp_config_.ulpfec.red_payload_type);
    }
  }
}

void RtpVideoSender::ConfigureRids() {
  if (rtp_config_.rids.empty())
    return;

  // Some streams could have been disabled, but the rids are still there.
  // This will occur when simulcast has been disabled for a codec (e.g. VP9)
  RTC_DCHECK(rtp_config_.rids.size() >= rtp_streams_.size());
  for (size_t i = 0; i < rtp_streams_.size(); ++i) {
    rtp_streams_[i].rtp_rtcp->SetRid(rtp_config_.rids[i]);
  }
}

void RtpVideoSender::OnNetworkAvailability(bool network_available) {
  for (const RtpStreamSender& stream : rtp_streams_) {
    stream.rtp_rtcp->SetRTCPStatus(network_available ? rtp_config_.rtcp_mode
                                                     : RtcpMode::kOff);
  }
}

std::map<uint32_t, RtpState> RtpVideoSender::GetRtpStates() const {
  std::map<uint32_t, RtpState> rtp_states;

  for (size_t i = 0; i < rtp_config_.ssrcs.size(); ++i) {
    uint32_t ssrc = rtp_config_.ssrcs[i];
    RTC_DCHECK_EQ(ssrc, rtp_streams_[i].rtp_rtcp->SSRC());
    rtp_states[ssrc] = rtp_streams_[i].rtp_rtcp->GetRtpState();

    // Only happens during shutdown, when RTP module is already inactive,
    // so OK to call fec generator here.
    if (rtp_streams_[i].fec_generator) {
      absl::optional<RtpState> fec_state =
          rtp_streams_[i].fec_generator->GetRtpState();
      if (fec_state) {
        uint32_t ssrc = rtp_config_.flexfec.ssrc;
        rtp_states[ssrc] = *fec_state;
      }
    }
  }

  for (size_t i = 0; i < rtp_config_.rtx.ssrcs.size(); ++i) {
    uint32_t ssrc = rtp_config_.rtx.ssrcs[i];
    rtp_states[ssrc] = rtp_streams_[i].rtp_rtcp->GetRtxState();
  }

  return rtp_states;
}

std::map<uint32_t, RtpPayloadState> RtpVideoSender::GetRtpPayloadStates()
    const {
  MutexLock lock(&mutex_);
  std::map<uint32_t, RtpPayloadState> payload_states;
  for (const auto& param : params_) {
    payload_states[param.ssrc()] = param.state();
    payload_states[param.ssrc()].shared_frame_id = shared_frame_id_;
  }
  return payload_states;
}

void RtpVideoSender::OnTransportOverheadChanged(
    size_t transport_overhead_bytes_per_packet) {
  MutexLock lock(&mutex_);
  transport_overhead_bytes_per_packet_ = transport_overhead_bytes_per_packet;

  size_t max_rtp_packet_size =
      std::min(rtp_config_.max_packet_size,
               kPathMTU - transport_overhead_bytes_per_packet_);
  for (const RtpStreamSender& stream : rtp_streams_) {
    stream.rtp_rtcp->SetMaxRtpPacketSize(max_rtp_packet_size);
  }
}

void RtpVideoSender::OnBitrateUpdated(BitrateAllocationUpdate update,
                                      int framerate) {
  // Substract overhead from bitrate.
  MutexLock lock(&mutex_);
  size_t num_active_streams = 0;
  size_t overhead_bytes_per_packet = 0;
  for (const auto& stream : rtp_streams_) {
    if (stream.rtp_rtcp->SendingMedia()) {
      overhead_bytes_per_packet += stream.rtp_rtcp->ExpectedPerPacketOverhead();
      ++num_active_streams;
    }
  }
  if (num_active_streams > 1) {
    overhead_bytes_per_packet /= num_active_streams;
  }

  DataSize packet_overhead = DataSize::Bytes(
      overhead_bytes_per_packet + transport_overhead_bytes_per_packet_);
  DataSize max_total_packet_size = DataSize::Bytes(
      rtp_config_.max_packet_size + transport_overhead_bytes_per_packet_);
  uint32_t payload_bitrate_bps = update.target_bitrate.bps();
  if (send_side_bwe_with_overhead_ && has_packet_feedback_) {
    DataRate overhead_rate =
        CalculateOverheadRate(update.target_bitrate, max_total_packet_size,
                              packet_overhead, Frequency::Hertz(framerate));
    // TODO(srte): We probably should not accept 0 payload bitrate here.
    payload_bitrate_bps = rtc::saturated_cast<uint32_t>(payload_bitrate_bps -
                                                        overhead_rate.bps());
  }

  // Get the encoder target rate. It is the estimated network rate -
  // protection overhead.
  // TODO(srte): We should multiply with 255 here.
  encoder_target_rate_bps_ = fec_controller_->UpdateFecRates(
      payload_bitrate_bps, framerate,
      rtc::saturated_cast<uint8_t>(update.packet_loss_ratio * 256),
      loss_mask_vector_, update.round_trip_time.ms());
  if (!fec_allowed_) {
    encoder_target_rate_bps_ = payload_bitrate_bps;
    // fec_controller_->UpdateFecRates() was still called so as to allow
    // |fec_controller_| to update whatever internal state it might have,
    // since |fec_allowed_| may be toggled back on at any moment.
  }

    // Subtract packetization overhead from the encoder target. If target rate
    // is really low, cap the overhead at 50%. This also avoids the case where
    // |encoder_target_rate_bps_| is 0 due to encoder pause event while the
    // packetization rate is positive since packets are still flowing.
  uint32_t packetization_rate_bps =
      std::min(GetPacketizationOverheadRate(), encoder_target_rate_bps_ / 2);
  encoder_target_rate_bps_ -= packetization_rate_bps;

  loss_mask_vector_.clear();

  uint32_t encoder_overhead_rate_bps = 0;
  if (send_side_bwe_with_overhead_ && has_packet_feedback_) {
    // TODO(srte): The packet size should probably be the same as in the
    // CalculateOverheadRate call above (just max_total_packet_size), it doesn't
    // make sense to use different packet rates for different overhead
    // calculations.
    DataRate encoder_overhead_rate = CalculateOverheadRate(
        DataRate::BitsPerSec(encoder_target_rate_bps_),
        max_total_packet_size - DataSize::Bytes(overhead_bytes_per_packet),
        packet_overhead, Frequency::Hertz(framerate));
    encoder_overhead_rate_bps = std::min(
        encoder_overhead_rate.bps<uint32_t>(),
        update.target_bitrate.bps<uint32_t>() - encoder_target_rate_bps_);
  }
  // When the field trial "WebRTC-SendSideBwe-WithOverhead" is enabled
  // protection_bitrate includes overhead.
  const uint32_t media_rate = encoder_target_rate_bps_ +
                              encoder_overhead_rate_bps +
                              packetization_rate_bps;
  RTC_DCHECK_GE(update.target_bitrate, DataRate::BitsPerSec(media_rate));
  protection_bitrate_bps_ = update.target_bitrate.bps() - media_rate;
}

uint32_t RtpVideoSender::GetPayloadBitrateBps() const {
  return encoder_target_rate_bps_;
}

uint32_t RtpVideoSender::GetProtectionBitrateBps() const {
  return protection_bitrate_bps_;
}

std::vector<RtpSequenceNumberMap::Info> RtpVideoSender::GetSentRtpPacketInfos(
    uint32_t ssrc,
    rtc::ArrayView<const uint16_t> sequence_numbers) const {
  for (const auto& rtp_stream : rtp_streams_) {
    if (ssrc == rtp_stream.rtp_rtcp->SSRC()) {
      return rtp_stream.rtp_rtcp->GetSentRtpPacketInfos(sequence_numbers);
    }
  }
  return std::vector<RtpSequenceNumberMap::Info>();
}

int RtpVideoSender::ProtectionRequest(const FecProtectionParams* delta_params,
                                      const FecProtectionParams* key_params,
                                      uint32_t* sent_video_rate_bps,
                                      uint32_t* sent_nack_rate_bps,
                                      uint32_t* sent_fec_rate_bps) {
  *sent_video_rate_bps = 0;
  *sent_nack_rate_bps = 0;
  *sent_fec_rate_bps = 0;
  for (const RtpStreamSender& stream : rtp_streams_) {
      stream.rtp_rtcp->SetFecProtectionParams(*delta_params, *key_params);

      auto send_bitrate = stream.rtp_rtcp->GetSendRates();
      *sent_video_rate_bps += send_bitrate[RtpPacketMediaType::kVideo].bps();
      *sent_fec_rate_bps +=
          send_bitrate[RtpPacketMediaType::kForwardErrorCorrection].bps();
      *sent_nack_rate_bps +=
          send_bitrate[RtpPacketMediaType::kRetransmission].bps();
  }
  return 0;
}

void RtpVideoSender::SetFecAllowed(bool fec_allowed) {
  MutexLock lock(&mutex_);
  fec_allowed_ = fec_allowed;
}

void RtpVideoSender::OnPacketFeedbackVector(
    std::vector<StreamPacketInfo> packet_feedback_vector) {
  if (fec_controller_->UseLossVectorMask()) {
    MutexLock lock(&mutex_);
    for (const StreamPacketInfo& packet : packet_feedback_vector) {
      loss_mask_vector_.push_back(!packet.received);
    }
  }

  // Map from SSRC to all acked packets for that RTP module.
  std::map<uint32_t, std::vector<uint16_t>> acked_packets_per_ssrc;
  for (const StreamPacketInfo& packet : packet_feedback_vector) {
    if (packet.received) {
      acked_packets_per_ssrc[packet.ssrc].push_back(packet.rtp_sequence_number);
    }
  }

    // Map from SSRC to vector of RTP sequence numbers that are indicated as
    // lost by feedback, without being trailed by any received packets.
    std::map<uint32_t, std::vector<uint16_t>> early_loss_detected_per_ssrc;

    for (const StreamPacketInfo& packet : packet_feedback_vector) {
      if (!packet.received) {
        // Last known lost packet, might not be detectable as lost by remote
        // jitter buffer.
        early_loss_detected_per_ssrc[packet.ssrc].push_back(
            packet.rtp_sequence_number);
      } else {
        // Packet received, so any loss prior to this is already detectable.
        early_loss_detected_per_ssrc.erase(packet.ssrc);
      }
    }

    for (const auto& kv : early_loss_detected_per_ssrc) {
      const uint32_t ssrc = kv.first;
      auto it = ssrc_to_rtp_module_.find(ssrc);
      RTC_DCHECK(it != ssrc_to_rtp_module_.end());
      RTPSender* rtp_sender = it->second->RtpSender();
      for (uint16_t sequence_number : kv.second) {
        rtp_sender->ReSendPacket(sequence_number);
      }
    }

  for (const auto& kv : acked_packets_per_ssrc) {
    const uint32_t ssrc = kv.first;
    auto it = ssrc_to_rtp_module_.find(ssrc);
    if (it == ssrc_to_rtp_module_.end()) {
      // Packets not for a media SSRC, so likely RTX or FEC. If so, ignore
      // since there's no RTP history to clean up anyway.
      continue;
    }
    rtc::ArrayView<const uint16_t> rtp_sequence_numbers(kv.second);
    it->second->OnPacketsAcknowledged(rtp_sequence_numbers);
  }
}

void RtpVideoSender::SetEncodingData(size_t width,
                                     size_t height,
                                     size_t num_temporal_layers) {
  fec_controller_->SetEncodingData(width, height, num_temporal_layers,
                                   rtp_config_.max_packet_size);
}

DataRate RtpVideoSender::CalculateOverheadRate(DataRate data_rate,
                                               DataSize packet_size,
                                               DataSize overhead_per_packet,
                                               Frequency framerate) const {
  Frequency packet_rate = data_rate / packet_size;
  if (use_frame_rate_for_overhead_) {
    framerate = std::max(framerate, Frequency::Hertz(1));
    DataSize frame_size = data_rate / framerate;
    int packets_per_frame = ceil(frame_size / packet_size);
    packet_rate = packets_per_frame * framerate;
  }
  return packet_rate.RoundUpTo(Frequency::Hertz(1)) * overhead_per_packet;
}

}  // namespace webrtc
