/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "video/video_send_stream_impl.h"

#include <stdio.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "api/crypto/crypto_options.h"
#include "api/rtp_parameters.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/video_codecs/video_codec.h"
#include "call/rtp_transport_controller_send_interface.h"
#include "call/video_send_stream.h"
#include "modules/pacing/paced_sender.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/alr_experiment.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/min_video_bitrate_experiment.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {
namespace internal {
namespace {

// Max positive size difference to treat allocations as "similar".
static constexpr int kMaxVbaSizeDifferencePercent = 10;
// Max time we will throttle similar video bitrate allocations.
static constexpr int64_t kMaxVbaThrottleTimeMs = 500;

constexpr TimeDelta kEncoderTimeOut = TimeDelta::Seconds(2);

// When send-side BWE is used a stricter 1.1x pacing factor is used, rather than
// the 2.5x which is used with receive-side BWE. Provides a more careful
// bandwidth rampup with less risk of overshoots causing adverse effects like
// packet loss. Not used for receive side BWE, since there we lack the probing
// feature and so may result in too slow initial rampup.
static constexpr double kStrictPacingMultiplier = 1.1;

bool TransportSeqNumExtensionConfigured(const VideoSendStream::Config& config) {
  const std::vector<RtpExtension>& extensions = config.rtp.extensions;
  return absl::c_any_of(extensions, [](const RtpExtension& ext) {
    return ext.uri == RtpExtension::kTransportSequenceNumberUri;
  });
}

// Calculate max padding bitrate for a multi layer codec.
int CalculateMaxPadBitrateBps(const std::vector<VideoStream>& streams,
                              bool is_svc,
                              VideoEncoderConfig::ContentType content_type,
                              int min_transmit_bitrate_bps,
                              bool pad_to_min_bitrate,
                              bool alr_probing) {
  int pad_up_to_bitrate_bps = 0;

  RTC_DCHECK(!is_svc || streams.size() <= 1) << "Only one stream is allowed in "
                                                "SVC mode.";

  // Filter out only the active streams;
  std::vector<VideoStream> active_streams;
  for (const VideoStream& stream : streams) {
    if (stream.active)
      active_streams.emplace_back(stream);
  }

  if (active_streams.size() > 1 || (!active_streams.empty() && is_svc)) {
    // Simulcast or SVC is used.
    // if SVC is used, stream bitrates should already encode svc bitrates:
    // min_bitrate = min bitrate of a lowest svc layer.
    // target_bitrate = sum of target bitrates of lower layers + min bitrate
    // of the last one (as used in the calculations below).
    // max_bitrate = sum of all active layers' max_bitrate.
    if (alr_probing) {
      // With alr probing, just pad to the min bitrate of the lowest stream,
      // probing will handle the rest of the rampup.
      pad_up_to_bitrate_bps = active_streams[0].min_bitrate_bps;
    } else {
      // Without alr probing, pad up to start bitrate of the
      // highest active stream.
      const double hysteresis_factor =
          RateControlSettings::ParseFromFieldTrials()
              .GetSimulcastHysteresisFactor(content_type);
      if (is_svc) {
        // For SVC, since there is only one "stream", the padding bitrate
        // needed to enable the top spatial layer is stored in the
        // `target_bitrate_bps` field.
        // TODO(sprang): This behavior needs to die.
        pad_up_to_bitrate_bps = static_cast<int>(
            hysteresis_factor * active_streams[0].target_bitrate_bps + 0.5);
      } else {
        const size_t top_active_stream_idx = active_streams.size() - 1;
        pad_up_to_bitrate_bps = std::min(
            static_cast<int>(
                hysteresis_factor *
                    active_streams[top_active_stream_idx].min_bitrate_bps +
                0.5),
            active_streams[top_active_stream_idx].target_bitrate_bps);

        // Add target_bitrate_bps of the lower active streams.
        for (size_t i = 0; i < top_active_stream_idx; ++i) {
          pad_up_to_bitrate_bps += active_streams[i].target_bitrate_bps;
        }
      }
    }
  } else if (!active_streams.empty() && pad_to_min_bitrate) {
    pad_up_to_bitrate_bps = active_streams[0].min_bitrate_bps;
  }

  pad_up_to_bitrate_bps =
      std::max(pad_up_to_bitrate_bps, min_transmit_bitrate_bps);

  return pad_up_to_bitrate_bps;
}

absl::optional<AlrExperimentSettings> GetAlrSettings(
    VideoEncoderConfig::ContentType content_type) {
  if (content_type == VideoEncoderConfig::ContentType::kScreen) {
    return AlrExperimentSettings::CreateFromFieldTrial(
        AlrExperimentSettings::kScreenshareProbingBweExperimentName);
  }
  return AlrExperimentSettings::CreateFromFieldTrial(
      AlrExperimentSettings::kStrictPacingAndProbingExperimentName);
}

bool SameStreamsEnabled(const VideoBitrateAllocation& lhs,
                        const VideoBitrateAllocation& rhs) {
  for (size_t si = 0; si < kMaxSpatialLayers; ++si) {
    for (size_t ti = 0; ti < kMaxTemporalStreams; ++ti) {
      if (lhs.HasBitrate(si, ti) != rhs.HasBitrate(si, ti)) {
        return false;
      }
    }
  }
  return true;
}

// Returns an optional that has value iff TransportSeqNumExtensionConfigured
// is `true` for the given video send stream config.
absl::optional<float> GetConfiguredPacingFactor(
    const VideoSendStream::Config& config,
    VideoEncoderConfig::ContentType content_type,
    const PacingConfig& default_pacing_config) {
  if (!TransportSeqNumExtensionConfigured(config))
    return absl::nullopt;

  absl::optional<AlrExperimentSettings> alr_settings =
      GetAlrSettings(content_type);
  if (alr_settings)
    return alr_settings->pacing_factor;

  RateControlSettings rate_control_settings =
      RateControlSettings::ParseFromFieldTrials();
  return rate_control_settings.GetPacingFactor().value_or(
      default_pacing_config.pacing_factor);
}

uint32_t GetInitialEncoderMaxBitrate(int initial_encoder_max_bitrate) {
  if (initial_encoder_max_bitrate > 0)
    return rtc::dchecked_cast<uint32_t>(initial_encoder_max_bitrate);

  // TODO(srte): Make sure max bitrate is not set to negative values. We don't
  // have any way to handle unset values in downstream code, such as the
  // bitrate allocator. Previously -1 was implicitly casted to UINT32_MAX, a
  // behaviour that is not safe. Converting to 10 Mbps should be safe for
  // reasonable use cases as it allows adding the max of multiple streams
  // without wrappping around.
  const int kFallbackMaxBitrateBps = 10000000;
  RTC_DLOG(LS_ERROR) << "ERROR: Initial encoder max bitrate = "
                     << initial_encoder_max_bitrate << " which is <= 0!";
  RTC_DLOG(LS_INFO) << "Using default encoder max bitrate = 10 Mbps";
  return kFallbackMaxBitrateBps;
}

}  // namespace

PacingConfig::PacingConfig()
    : pacing_factor("factor", kStrictPacingMultiplier),
      max_pacing_delay("max_delay",
                       TimeDelta::Millis(PacedSender::kMaxQueueLengthMs)) {
  ParseFieldTrial({&pacing_factor, &max_pacing_delay},
                  field_trial::FindFullName("WebRTC-Video-Pacing"));
}
PacingConfig::PacingConfig(const PacingConfig&) = default;
PacingConfig::~PacingConfig() = default;

VideoSendStreamImpl::VideoSendStreamImpl(
    Clock* clock,
    SendStatisticsProxy* stats_proxy,
    rtc::TaskQueue* rtp_transport_queue,
    RtpTransportControllerSendInterface* transport,
    BitrateAllocatorInterface* bitrate_allocator,
    VideoStreamEncoderInterface* video_stream_encoder,
    const VideoSendStream::Config* config,
    int initial_encoder_max_bitrate,
    double initial_encoder_bitrate_priority,
    VideoEncoderConfig::ContentType content_type,
    RtpVideoSenderInterface* rtp_video_sender)
    : clock_(clock),
      has_alr_probing_(config->periodic_alr_bandwidth_probing ||
                       GetAlrSettings(content_type)),
      pacing_config_(PacingConfig()),
      stats_proxy_(stats_proxy),
      config_(config),
      rtp_transport_queue_(rtp_transport_queue),
      timed_out_(false),
      transport_(transport),
      bitrate_allocator_(bitrate_allocator),
      disable_padding_(true),
      max_padding_bitrate_(0),
      encoder_min_bitrate_bps_(0),
      encoder_max_bitrate_bps_(
          GetInitialEncoderMaxBitrate(initial_encoder_max_bitrate)),
      encoder_target_rate_bps_(0),
      encoder_bitrate_priority_(initial_encoder_bitrate_priority),
      video_stream_encoder_(video_stream_encoder),
      bandwidth_observer_(transport->GetBandwidthObserver()),
      rtp_video_sender_(rtp_video_sender),
      configured_pacing_factor_(
          GetConfiguredPacingFactor(*config_, content_type, pacing_config_)) {
  RTC_DCHECK_GE(config_->rtp.payload_type, 0);
  RTC_DCHECK_LE(config_->rtp.payload_type, 127);
  RTC_DCHECK(!config_->rtp.ssrcs.empty());
  RTC_DCHECK(transport_);
  RTC_DCHECK_NE(initial_encoder_max_bitrate, 0);
  RTC_LOG(LS_INFO) << "VideoSendStreamImpl: " << config_->ToString();

  RTC_CHECK(AlrExperimentSettings::MaxOneFieldTrialEnabled());

  // Only request rotation at the source when we positively know that the remote
  // side doesn't support the rotation extension. This allows us to prepare the
  // encoder in the expectation that rotation is supported - which is the common
  // case.
  bool rotation_applied = absl::c_none_of(
      config_->rtp.extensions, [](const RtpExtension& extension) {
        return extension.uri == RtpExtension::kVideoRotationUri;
      });

  video_stream_encoder_->SetSink(this, rotation_applied);

  absl::optional<bool> enable_alr_bw_probing;

  // If send-side BWE is enabled, check if we should apply updated probing and
  // pacing settings.
  if (configured_pacing_factor_) {
    absl::optional<AlrExperimentSettings> alr_settings =
        GetAlrSettings(content_type);
    int queue_time_limit_ms;
    if (alr_settings) {
      enable_alr_bw_probing = true;
      queue_time_limit_ms = alr_settings->max_paced_queue_time;
    } else {
      RateControlSettings rate_control_settings =
          RateControlSettings::ParseFromFieldTrials();
      enable_alr_bw_probing = rate_control_settings.UseAlrProbing();
      queue_time_limit_ms = pacing_config_.max_pacing_delay.Get().ms();
    }

    transport->SetQueueTimeLimit(queue_time_limit_ms);
  }

  if (config_->periodic_alr_bandwidth_probing) {
    enable_alr_bw_probing = config_->periodic_alr_bandwidth_probing;
  }

  if (enable_alr_bw_probing) {
    transport->EnablePeriodicAlrProbing(*enable_alr_bw_probing);
  }

  rtp_transport_queue_->PostTask(ToQueuedTask(transport_queue_safety_, [this] {
    if (configured_pacing_factor_)
      transport_->SetPacingFactor(*configured_pacing_factor_);

    video_stream_encoder_->SetStartBitrate(
        bitrate_allocator_->GetStartBitrate(this));
  }));
}

VideoSendStreamImpl::~VideoSendStreamImpl() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_LOG(LS_INFO) << "~VideoSendStreamImpl: " << config_->ToString();
}

void VideoSendStreamImpl::DeliverRtcp(const uint8_t* packet, size_t length) {
  // Runs on a network thread.
  RTC_DCHECK(!rtp_transport_queue_->IsCurrent());
  rtp_video_sender_->DeliverRtcp(packet, length);
}

void VideoSendStreamImpl::UpdateActiveSimulcastLayers(
    const std::vector<bool> active_layers) {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  bool previously_active = rtp_video_sender_->IsActive();
  rtp_video_sender_->SetActiveModules(active_layers);
  if (!rtp_video_sender_->IsActive() && previously_active) {
    // Payload router switched from active to inactive.
    StopVideoSendStream();
  } else if (rtp_video_sender_->IsActive() && !previously_active) {
    // Payload router switched from inactive to active.
    StartupVideoSendStream();
  }
}

void VideoSendStreamImpl::Start() {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  RTC_LOG(LS_INFO) << "VideoSendStream::Start";
  if (rtp_video_sender_->IsActive())
    return;

  TRACE_EVENT_INSTANT0("webrtc", "VideoSendStream::Start");
  rtp_video_sender_->SetActive(true);
  StartupVideoSendStream();
}

void VideoSendStreamImpl::StartupVideoSendStream() {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);

  transport_queue_safety_->SetAlive();

  bitrate_allocator_->AddObserver(this, GetAllocationConfig());
  // Start monitoring encoder activity.
  {
    RTC_DCHECK(!check_encoder_activity_task_.Running());

    activity_ = false;
    timed_out_ = false;
    check_encoder_activity_task_ = RepeatingTaskHandle::DelayedStart(
        rtp_transport_queue_->Get(), kEncoderTimeOut, [this] {
          RTC_DCHECK_RUN_ON(rtp_transport_queue_);
          if (!activity_) {
            if (!timed_out_) {
              SignalEncoderTimedOut();
            }
            timed_out_ = true;
            disable_padding_ = true;
          } else if (timed_out_) {
            SignalEncoderActive();
            timed_out_ = false;
          }
          activity_ = false;
          return kEncoderTimeOut;
        });
  }

  video_stream_encoder_->SendKeyFrame();
}

void VideoSendStreamImpl::Stop() {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  RTC_LOG(LS_INFO) << "VideoSendStreamImpl::Stop";
  if (!rtp_video_sender_->IsActive())
    return;

  RTC_DCHECK(transport_queue_safety_->alive());
  TRACE_EVENT_INSTANT0("webrtc", "VideoSendStream::Stop");
  rtp_video_sender_->SetActive(false);
  StopVideoSendStream();
}

// RTC_RUN_ON(rtp_transport_queue_)
void VideoSendStreamImpl::StopVideoSendStream() {
  bitrate_allocator_->RemoveObserver(this);
  check_encoder_activity_task_.Stop();
  video_stream_encoder_->OnBitrateUpdated(DataRate::Zero(), DataRate::Zero(),
                                          DataRate::Zero(), 0, 0, 0);
  stats_proxy_->OnSetEncoderTargetRate(0);
  transport_queue_safety_->SetNotAlive();
}

void VideoSendStreamImpl::SignalEncoderTimedOut() {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  // If the encoder has not produced anything the last kEncoderTimeOut and it
  // is supposed to, deregister as BitrateAllocatorObserver. This can happen
  // if a camera stops producing frames.
  if (encoder_target_rate_bps_ > 0) {
    RTC_LOG(LS_INFO) << "SignalEncoderTimedOut, Encoder timed out.";
    bitrate_allocator_->RemoveObserver(this);
  }
}

void VideoSendStreamImpl::OnBitrateAllocationUpdated(
    const VideoBitrateAllocation& allocation) {
  if (!rtp_transport_queue_->IsCurrent()) {
    rtp_transport_queue_->PostTask(ToQueuedTask(transport_queue_safety_, [=] {
      OnBitrateAllocationUpdated(allocation);
    }));
    return;
  }

  RTC_DCHECK_RUN_ON(rtp_transport_queue_);

  int64_t now_ms = clock_->TimeInMilliseconds();
  if (encoder_target_rate_bps_ != 0) {
    if (video_bitrate_allocation_context_) {
      // If new allocation is within kMaxVbaSizeDifferencePercent larger than
      // the previously sent allocation and the same streams are still enabled,
      // it is considered "similar". We do not want send similar allocations
      // more once per kMaxVbaThrottleTimeMs.
      const VideoBitrateAllocation& last =
          video_bitrate_allocation_context_->last_sent_allocation;
      const bool is_similar =
          allocation.get_sum_bps() >= last.get_sum_bps() &&
          allocation.get_sum_bps() <
              (last.get_sum_bps() * (100 + kMaxVbaSizeDifferencePercent)) /
                  100 &&
          SameStreamsEnabled(allocation, last);
      if (is_similar &&
          (now_ms - video_bitrate_allocation_context_->last_send_time_ms) <
              kMaxVbaThrottleTimeMs) {
        // This allocation is too similar, cache it and return.
        video_bitrate_allocation_context_->throttled_allocation = allocation;
        return;
      }
    } else {
      video_bitrate_allocation_context_.emplace();
    }

    video_bitrate_allocation_context_->last_sent_allocation = allocation;
    video_bitrate_allocation_context_->throttled_allocation.reset();
    video_bitrate_allocation_context_->last_send_time_ms = now_ms;

    // Send bitrate allocation metadata only if encoder is not paused.
    rtp_video_sender_->OnBitrateAllocationUpdated(allocation);
  }
}

void VideoSendStreamImpl::OnVideoLayersAllocationUpdated(
    VideoLayersAllocation allocation) {
  // OnVideoLayersAllocationUpdated is handled on the encoder task queue in
  // order to not race with OnEncodedImage callbacks.
  rtp_video_sender_->OnVideoLayersAllocationUpdated(allocation);
}

void VideoSendStreamImpl::SignalEncoderActive() {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  if (rtp_video_sender_->IsActive()) {
    RTC_LOG(LS_INFO) << "SignalEncoderActive, Encoder is active.";
    bitrate_allocator_->AddObserver(this, GetAllocationConfig());
  }
}

MediaStreamAllocationConfig VideoSendStreamImpl::GetAllocationConfig() const {
  return MediaStreamAllocationConfig{
      static_cast<uint32_t>(encoder_min_bitrate_bps_),
      encoder_max_bitrate_bps_,
      static_cast<uint32_t>(disable_padding_ ? 0 : max_padding_bitrate_),
      /* priority_bitrate */ 0,
      !config_->suspend_below_min_bitrate,
      encoder_bitrate_priority_};
}

void VideoSendStreamImpl::OnEncoderConfigurationChanged(
    std::vector<VideoStream> streams,
    bool is_svc,
    VideoEncoderConfig::ContentType content_type,
    int min_transmit_bitrate_bps) {
  if (!rtp_transport_queue_->IsCurrent()) {
    rtp_transport_queue_->PostTask(ToQueuedTask(
        transport_queue_safety_,
        [this, streams = std::move(streams), is_svc, content_type,
         min_transmit_bitrate_bps]() mutable {
          OnEncoderConfigurationChanged(std::move(streams), is_svc,
                                        content_type, min_transmit_bitrate_bps);
        }));
    return;
  }

  RTC_DCHECK_GE(config_->rtp.ssrcs.size(), streams.size());
  TRACE_EVENT0("webrtc", "VideoSendStream::OnEncoderConfigurationChanged");
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);

  const VideoCodecType codec_type =
      PayloadStringToCodecType(config_->rtp.payload_name);

  const absl::optional<DataRate> experimental_min_bitrate =
      GetExperimentalMinVideoBitrate(codec_type);
  encoder_min_bitrate_bps_ =
      experimental_min_bitrate
          ? experimental_min_bitrate->bps()
          : std::max(streams[0].min_bitrate_bps, kDefaultMinVideoBitrateBps);

  encoder_max_bitrate_bps_ = 0;
  double stream_bitrate_priority_sum = 0;
  for (const auto& stream : streams) {
    // We don't want to allocate more bitrate than needed to inactive streams.
    encoder_max_bitrate_bps_ += stream.active ? stream.max_bitrate_bps : 0;
    if (stream.bitrate_priority) {
      RTC_DCHECK_GT(*stream.bitrate_priority, 0);
      stream_bitrate_priority_sum += *stream.bitrate_priority;
    }
  }
  RTC_DCHECK_GT(stream_bitrate_priority_sum, 0);
  encoder_bitrate_priority_ = stream_bitrate_priority_sum;
  encoder_max_bitrate_bps_ =
      std::max(static_cast<uint32_t>(encoder_min_bitrate_bps_),
               encoder_max_bitrate_bps_);

  // TODO(bugs.webrtc.org/10266): Query the VideoBitrateAllocator instead.
  max_padding_bitrate_ = CalculateMaxPadBitrateBps(
      streams, is_svc, content_type, min_transmit_bitrate_bps,
      config_->suspend_below_min_bitrate, has_alr_probing_);

  // Clear stats for disabled layers.
  for (size_t i = streams.size(); i < config_->rtp.ssrcs.size(); ++i) {
    stats_proxy_->OnInactiveSsrc(config_->rtp.ssrcs[i]);
  }

  const size_t num_temporal_layers =
      streams.back().num_temporal_layers.value_or(1);

  rtp_video_sender_->SetEncodingData(streams[0].width, streams[0].height,
                                     num_temporal_layers);

  if (rtp_video_sender_->IsActive()) {
    // The send stream is started already. Update the allocator with new bitrate
    // limits.
    bitrate_allocator_->AddObserver(this, GetAllocationConfig());
  }
}

EncodedImageCallback::Result VideoSendStreamImpl::OnEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  // Encoded is called on whatever thread the real encoder implementation run
  // on. In the case of hardware encoders, there might be several encoders
  // running in parallel on different threads.

  // Indicate that there still is activity going on.
  activity_ = true;

  auto enable_padding_task = [this]() {
    if (disable_padding_) {
      RTC_DCHECK_RUN_ON(rtp_transport_queue_);
      disable_padding_ = false;
      // To ensure that padding bitrate is propagated to the bitrate allocator.
      SignalEncoderActive();
    }
  };
  if (!rtp_transport_queue_->IsCurrent()) {
    rtp_transport_queue_->PostTask(
        ToQueuedTask(transport_queue_safety_, std::move(enable_padding_task)));
  } else {
    enable_padding_task();
  }

  EncodedImageCallback::Result result(EncodedImageCallback::Result::OK);
  result =
      rtp_video_sender_->OnEncodedImage(encoded_image, codec_specific_info);
  // Check if there's a throttled VideoBitrateAllocation that we should try
  // sending.
  auto update_task = [this]() {
    RTC_DCHECK_RUN_ON(rtp_transport_queue_);
    auto& context = video_bitrate_allocation_context_;
    if (context && context->throttled_allocation) {
      OnBitrateAllocationUpdated(*context->throttled_allocation);
    }
  };
  if (!rtp_transport_queue_->IsCurrent()) {
    rtp_transport_queue_->PostTask(
        ToQueuedTask(transport_queue_safety_, std::move(update_task)));
  } else {
    update_task();
  }

  return result;
}

void VideoSendStreamImpl::OnDroppedFrame(
    EncodedImageCallback::DropReason reason) {
  activity_ = true;
}

std::map<uint32_t, RtpState> VideoSendStreamImpl::GetRtpStates() const {
  return rtp_video_sender_->GetRtpStates();
}

std::map<uint32_t, RtpPayloadState> VideoSendStreamImpl::GetRtpPayloadStates()
    const {
  return rtp_video_sender_->GetRtpPayloadStates();
}

uint32_t VideoSendStreamImpl::OnBitrateUpdated(BitrateAllocationUpdate update) {
  RTC_DCHECK_RUN_ON(rtp_transport_queue_);
  RTC_DCHECK(rtp_video_sender_->IsActive())
      << "VideoSendStream::Start has not been called.";

  // When the BWE algorithm doesn't pass a stable estimate, we'll use the
  // unstable one instead.
  if (update.stable_target_bitrate.IsZero()) {
    update.stable_target_bitrate = update.target_bitrate;
  }

  rtp_video_sender_->OnBitrateUpdated(update, stats_proxy_->GetSendFrameRate());
  encoder_target_rate_bps_ = rtp_video_sender_->GetPayloadBitrateBps();
  const uint32_t protection_bitrate_bps =
      rtp_video_sender_->GetProtectionBitrateBps();
  DataRate link_allocation = DataRate::Zero();
  if (encoder_target_rate_bps_ > protection_bitrate_bps) {
    link_allocation =
        DataRate::BitsPerSec(encoder_target_rate_bps_ - protection_bitrate_bps);
  }
  DataRate overhead =
      update.target_bitrate - DataRate::BitsPerSec(encoder_target_rate_bps_);
  DataRate encoder_stable_target_rate = update.stable_target_bitrate;
  if (encoder_stable_target_rate > overhead) {
    encoder_stable_target_rate = encoder_stable_target_rate - overhead;
  } else {
    encoder_stable_target_rate = DataRate::BitsPerSec(encoder_target_rate_bps_);
  }

  encoder_target_rate_bps_ =
      std::min(encoder_max_bitrate_bps_, encoder_target_rate_bps_);

  encoder_stable_target_rate =
      std::min(DataRate::BitsPerSec(encoder_max_bitrate_bps_),
               encoder_stable_target_rate);

  DataRate encoder_target_rate = DataRate::BitsPerSec(encoder_target_rate_bps_);
  link_allocation = std::max(encoder_target_rate, link_allocation);
  video_stream_encoder_->OnBitrateUpdated(
      encoder_target_rate, encoder_stable_target_rate, link_allocation,
      rtc::dchecked_cast<uint8_t>(update.packet_loss_ratio * 256),
      update.round_trip_time.ms(), update.cwnd_reduce_ratio);
  stats_proxy_->OnSetEncoderTargetRate(encoder_target_rate_bps_);
  return protection_bitrate_bps;
}

}  // namespace internal
}  // namespace webrtc
