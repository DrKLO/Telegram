/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "video/video_send_stream.h"

#include <utility>

#include "api/array_view.h"
#include "api/task_queue/task_queue_base.h"
#include "api/video/video_stream_encoder_settings.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtp_header_extension_size.h"
#include "modules/rtp_rtcp/source/rtp_sender.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/clock.h"
#include "video/adaptation/overuse_frame_detector.h"
#include "video/frame_cadence_adapter.h"
#include "video/video_stream_encoder.h"

namespace webrtc {

namespace {

size_t CalculateMaxHeaderSize(const RtpConfig& config) {
  size_t header_size = kRtpHeaderSize;
  size_t extensions_size = 0;
  size_t fec_extensions_size = 0;
  if (!config.extensions.empty()) {
    RtpHeaderExtensionMap extensions_map(config.extensions);
    extensions_size = RtpHeaderExtensionSize(RTPSender::VideoExtensionSizes(),
                                             extensions_map);
    fec_extensions_size =
        RtpHeaderExtensionSize(RTPSender::FecExtensionSizes(), extensions_map);
  }
  header_size += extensions_size;
  if (config.flexfec.payload_type >= 0) {
    // All FEC extensions again plus maximum FlexFec overhead.
    header_size += fec_extensions_size + 32;
  } else {
    if (config.ulpfec.ulpfec_payload_type >= 0) {
      // Header with all the FEC extensions will be repeated plus maximum
      // UlpFec overhead.
      header_size += fec_extensions_size + 18;
    }
    if (config.ulpfec.red_payload_type >= 0) {
      header_size += 1;  // RED header.
    }
  }
  // Additional room for Rtx.
  if (config.rtx.payload_type >= 0)
    header_size += kRtxHeaderSize;
  return header_size;
}

VideoStreamEncoder::BitrateAllocationCallbackType
GetBitrateAllocationCallbackType(const VideoSendStream::Config& config,
                                 const FieldTrialsView& field_trials) {
  if (webrtc::RtpExtension::FindHeaderExtensionByUri(
          config.rtp.extensions,
          webrtc::RtpExtension::kVideoLayersAllocationUri,
          config.crypto_options.srtp.enable_encrypted_rtp_header_extensions
              ? RtpExtension::Filter::kPreferEncryptedExtension
              : RtpExtension::Filter::kDiscardEncryptedExtension)) {
    return VideoStreamEncoder::BitrateAllocationCallbackType::
        kVideoLayersAllocation;
  }
  if (field_trials.IsEnabled("WebRTC-Target-Bitrate-Rtcp")) {
    return VideoStreamEncoder::BitrateAllocationCallbackType::
        kVideoBitrateAllocation;
  }
  return VideoStreamEncoder::BitrateAllocationCallbackType::
      kVideoBitrateAllocationWhenScreenSharing;
}

RtpSenderFrameEncryptionConfig CreateFrameEncryptionConfig(
    const VideoSendStream::Config* config) {
  RtpSenderFrameEncryptionConfig frame_encryption_config;
  frame_encryption_config.frame_encryptor = config->frame_encryptor.get();
  frame_encryption_config.crypto_options = config->crypto_options;
  return frame_encryption_config;
}

RtpSenderObservers CreateObservers(RtcpRttStats* call_stats,
                                   EncoderRtcpFeedback* encoder_feedback,
                                   SendStatisticsProxy* stats_proxy,
                                   SendDelayStats* send_delay_stats) {
  RtpSenderObservers observers;
  observers.rtcp_rtt_stats = call_stats;
  observers.intra_frame_callback = encoder_feedback;
  observers.rtcp_loss_notification_observer = encoder_feedback;
  observers.report_block_data_observer = stats_proxy;
  observers.rtp_stats = stats_proxy;
  observers.bitrate_observer = stats_proxy;
  observers.frame_count_observer = stats_proxy;
  observers.rtcp_type_observer = stats_proxy;
  observers.send_delay_observer = stats_proxy;
  observers.send_packet_observer = send_delay_stats;
  return observers;
}

std::unique_ptr<VideoStreamEncoder> CreateVideoStreamEncoder(
    Clock* clock,
    int num_cpu_cores,
    TaskQueueFactory* task_queue_factory,
    SendStatisticsProxy* stats_proxy,
    const VideoStreamEncoderSettings& encoder_settings,
    VideoStreamEncoder::BitrateAllocationCallbackType
        bitrate_allocation_callback_type,
    const FieldTrialsView& field_trials,
    webrtc::VideoEncoderFactory::EncoderSelectorInterface* encoder_selector) {
  std::unique_ptr<TaskQueueBase, TaskQueueDeleter> encoder_queue =
      task_queue_factory->CreateTaskQueue("EncoderQueue",
                                          TaskQueueFactory::Priority::NORMAL);
  TaskQueueBase* encoder_queue_ptr = encoder_queue.get();
  return std::make_unique<VideoStreamEncoder>(
      clock, num_cpu_cores, stats_proxy, encoder_settings,
      std::make_unique<OveruseFrameDetector>(stats_proxy, field_trials),
      FrameCadenceAdapterInterface::Create(clock, encoder_queue_ptr,
                                           field_trials),
      std::move(encoder_queue), bitrate_allocation_callback_type, field_trials,
      encoder_selector);
}

}  // namespace

namespace internal {

VideoSendStream::VideoSendStream(
    Clock* clock,
    int num_cpu_cores,
    TaskQueueFactory* task_queue_factory,
    TaskQueueBase* network_queue,
    RtcpRttStats* call_stats,
    RtpTransportControllerSendInterface* transport,
    BitrateAllocatorInterface* bitrate_allocator,
    SendDelayStats* send_delay_stats,
    RtcEventLog* event_log,
    VideoSendStream::Config config,
    VideoEncoderConfig encoder_config,
    const std::map<uint32_t, RtpState>& suspended_ssrcs,
    const std::map<uint32_t, RtpPayloadState>& suspended_payload_states,
    std::unique_ptr<FecController> fec_controller,
    const FieldTrialsView& field_trials)
    : rtp_transport_queue_(transport->GetWorkerQueue()),
      transport_(transport),
      stats_proxy_(clock, config, encoder_config.content_type, field_trials),
      config_(std::move(config)),
      content_type_(encoder_config.content_type),
      video_stream_encoder_(CreateVideoStreamEncoder(
          clock,
          num_cpu_cores,
          task_queue_factory,
          &stats_proxy_,
          config_.encoder_settings,
          GetBitrateAllocationCallbackType(config_, field_trials),
          field_trials,
          config_.encoder_selector)),
      encoder_feedback_(
          clock,
          config_.rtp.ssrcs,
          video_stream_encoder_.get(),
          [this](uint32_t ssrc, const std::vector<uint16_t>& seq_nums) {
            return rtp_video_sender_->GetSentRtpPacketInfos(ssrc, seq_nums);
          }),
      rtp_video_sender_(
          transport->CreateRtpVideoSender(suspended_ssrcs,
                                          suspended_payload_states,
                                          config_.rtp,
                                          config_.rtcp_report_interval_ms,
                                          config_.send_transport,
                                          CreateObservers(call_stats,
                                                          &encoder_feedback_,
                                                          &stats_proxy_,
                                                          send_delay_stats),
                                          event_log,
                                          std::move(fec_controller),
                                          CreateFrameEncryptionConfig(&config_),
                                          config_.frame_transformer)),
      send_stream_(clock,
                   &stats_proxy_,
                   transport,
                   bitrate_allocator,
                   video_stream_encoder_.get(),
                   &config_,
                   encoder_config.max_bitrate_bps,
                   encoder_config.bitrate_priority,
                   encoder_config.content_type,
                   rtp_video_sender_,
                   field_trials) {
  RTC_DCHECK(config_.encoder_settings.encoder_factory);
  RTC_DCHECK(config_.encoder_settings.bitrate_allocator_factory);

  video_stream_encoder_->SetFecControllerOverride(rtp_video_sender_);

  ReconfigureVideoEncoder(std::move(encoder_config));
}

VideoSendStream::~VideoSendStream() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(!running_);
  transport_->DestroyRtpVideoSender(rtp_video_sender_);
}

void VideoSendStream::UpdateActiveSimulcastLayers(
    const std::vector<bool> active_layers) {
  RTC_DCHECK_RUN_ON(&thread_checker_);

  // Keep our `running_` flag expected state in sync with active layers since
  // the `send_stream_` will be implicitly stopped/started depending on the
  // state of the layers.
  bool running = false;

  rtc::StringBuilder active_layers_string;
  active_layers_string << "{";
  for (size_t i = 0; i < active_layers.size(); ++i) {
    if (active_layers[i]) {
      running = true;
      active_layers_string << "1";
    } else {
      active_layers_string << "0";
    }
    if (i < active_layers.size() - 1) {
      active_layers_string << ", ";
    }
  }
  active_layers_string << "}";
  RTC_LOG(LS_INFO) << "UpdateActiveSimulcastLayers: "
                   << active_layers_string.str();

  rtp_transport_queue_->RunOrPost(
      SafeTask(transport_queue_safety_, [this, active_layers] {
        send_stream_.UpdateActiveSimulcastLayers(active_layers);
      }));

  running_ = running;
}

void VideoSendStream::Start() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DLOG(LS_INFO) << "VideoSendStream::Start";
  if (running_)
    return;

  running_ = true;

  // It is expected that after VideoSendStream::Start has been called, incoming
  // frames are not dropped in VideoStreamEncoder. To ensure this, Start has to
  // be synchronized.
  // TODO(tommi): ^^^ Validate if this still holds.
  rtp_transport_queue_->RunSynchronous([this] {
    transport_queue_safety_->SetAlive();
    send_stream_.Start();
  });
}

void VideoSendStream::Stop() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  if (!running_)
    return;
  RTC_DLOG(LS_INFO) << "VideoSendStream::Stop";
  running_ = false;
  rtp_transport_queue_->RunOrPost(SafeTask(transport_queue_safety_, [this] {
    // As the stream can get re-used and implicitly restarted via changing
    // the state of the active layers, we do not mark the
    // `transport_queue_safety_` flag with `SetNotAlive()` here. That's only
    // done when we stop permanently via `StopPermanentlyAndGetRtpStates()`.
    send_stream_.Stop();
  }));
}

bool VideoSendStream::started() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return running_;
}

void VideoSendStream::AddAdaptationResource(
    rtc::scoped_refptr<Resource> resource) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  video_stream_encoder_->AddAdaptationResource(resource);
}

std::vector<rtc::scoped_refptr<Resource>>
VideoSendStream::GetAdaptationResources() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return video_stream_encoder_->GetAdaptationResources();
}

void VideoSendStream::SetSource(
    rtc::VideoSourceInterface<webrtc::VideoFrame>* source,
    const DegradationPreference& degradation_preference) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  video_stream_encoder_->SetSource(source, degradation_preference);
}

void VideoSendStream::ReconfigureVideoEncoder(VideoEncoderConfig config) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK_EQ(content_type_, config.content_type);
  video_stream_encoder_->ConfigureEncoder(
      std::move(config),
      config_.rtp.max_packet_size - CalculateMaxHeaderSize(config_.rtp));
}

VideoSendStream::Stats VideoSendStream::GetStats() {
  // TODO(perkj, solenberg): Some test cases in EndToEndTest call GetStats from
  // a network thread. See comment in Call::GetStats().
  // RTC_DCHECK_RUN_ON(&thread_checker_);
  return stats_proxy_.GetStats();
}

absl::optional<float> VideoSendStream::GetPacingFactorOverride() const {
  return send_stream_.configured_pacing_factor();
}

void VideoSendStream::StopPermanentlyAndGetRtpStates(
    VideoSendStream::RtpStateMap* rtp_state_map,
    VideoSendStream::RtpPayloadStateMap* payload_state_map) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  video_stream_encoder_->Stop();

  running_ = false;
  // Always run these cleanup steps regardless of whether running_ was set
  // or not. This will unregister callbacks before destruction.
  // See `VideoSendStreamImpl::StopVideoSendStream` for more.
  rtp_transport_queue_->RunSynchronous(
      [this, rtp_state_map, payload_state_map]() {
        transport_queue_safety_->SetNotAlive();
        send_stream_.Stop();
        *rtp_state_map = send_stream_.GetRtpStates();
        *payload_state_map = send_stream_.GetRtpPayloadStates();
      });
}

void VideoSendStream::DeliverRtcp(const uint8_t* packet, size_t length) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  send_stream_.DeliverRtcp(packet, length);
}

void VideoSendStream::GenerateKeyFrame() {
  if (video_stream_encoder_) {
    video_stream_encoder_->SendKeyFrame();
  }
}

}  // namespace internal
}  // namespace webrtc
