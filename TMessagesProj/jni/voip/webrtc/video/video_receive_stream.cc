/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_receive_stream.h"

#include <stdlib.h>
#include <string.h>

#include <algorithm>
#include <memory>
#include <set>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/video/encoded_image.h"
#include "api/video_codecs/h264_profile_level_id.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder.h"
#include "call/rtp_stream_receiver_controller_interface.h"
#include "call/rtx_receive_stream.h"
#include "common_video/include/incoming_video_stream.h"
#include "modules/utility/include/process_thread.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/timing.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/thread_registry.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"
#include "video/call_stats.h"
#include "video/frame_dumping_decoder.h"
#include "video/receive_statistics_proxy.h"

namespace webrtc {

namespace internal {
constexpr int VideoReceiveStream::kMaxWaitForKeyFrameMs;
}  // namespace internal

namespace {

using ReturnReason = video_coding::FrameBuffer::ReturnReason;

constexpr int kMinBaseMinimumDelayMs = 0;
constexpr int kMaxBaseMinimumDelayMs = 10000;

constexpr int kMaxWaitForFrameMs = 3000;

// Concrete instance of RecordableEncodedFrame wrapping needed content
// from EncodedFrame.
class WebRtcRecordableEncodedFrame : public RecordableEncodedFrame {
 public:
  explicit WebRtcRecordableEncodedFrame(const EncodedFrame& frame)
      : buffer_(frame.GetEncodedData()),
        render_time_ms_(frame.RenderTime()),
        codec_(frame.CodecSpecific()->codecType),
        is_key_frame_(frame.FrameType() == VideoFrameType::kVideoFrameKey),
        resolution_{frame.EncodedImage()._encodedWidth,
                    frame.EncodedImage()._encodedHeight} {
    if (frame.ColorSpace()) {
      color_space_ = *frame.ColorSpace();
    }
  }

  // VideoEncodedSinkInterface::FrameBuffer
  rtc::scoped_refptr<const EncodedImageBufferInterface> encoded_buffer()
      const override {
    return buffer_;
  }

  absl::optional<webrtc::ColorSpace> color_space() const override {
    return color_space_;
  }

  VideoCodecType codec() const override { return codec_; }

  bool is_key_frame() const override { return is_key_frame_; }

  EncodedResolution resolution() const override { return resolution_; }

  Timestamp render_time() const override {
    return Timestamp::Millis(render_time_ms_);
  }

 private:
  rtc::scoped_refptr<EncodedImageBufferInterface> buffer_;
  int64_t render_time_ms_;
  VideoCodecType codec_;
  bool is_key_frame_;
  EncodedResolution resolution_;
  absl::optional<webrtc::ColorSpace> color_space_;
};

VideoCodec CreateDecoderVideoCodec(const VideoReceiveStream::Decoder& decoder) {
  VideoCodec codec;
  codec.codecType = PayloadStringToCodecType(decoder.video_format.name);

  if (codec.codecType == kVideoCodecVP8) {
    *(codec.VP8()) = VideoEncoder::GetDefaultVp8Settings();
  } else if (codec.codecType == kVideoCodecVP9) {
    *(codec.VP9()) = VideoEncoder::GetDefaultVp9Settings();
  } else if (codec.codecType == kVideoCodecH264) {
    *(codec.H264()) = VideoEncoder::GetDefaultH264Settings();
  } else if (codec.codecType == kVideoCodecMultiplex) {
    VideoReceiveStream::Decoder associated_decoder = decoder;
    associated_decoder.video_format =
        SdpVideoFormat(CodecTypeToPayloadString(kVideoCodecVP9));
    VideoCodec associated_codec = CreateDecoderVideoCodec(associated_decoder);
    associated_codec.codecType = kVideoCodecMultiplex;
    return associated_codec;
  }
#ifndef DISABLE_H265
  else if (codec.codecType == kVideoCodecH265) {
    *(codec.H265()) = VideoEncoder::GetDefaultH265Settings();
  }
#endif

  codec.width = 320;
  codec.height = 180;
  const int kDefaultStartBitrate = 300;
  codec.startBitrate = codec.minBitrate = codec.maxBitrate =
      kDefaultStartBitrate;

  return codec;
}

// Video decoder class to be used for unknown codecs. Doesn't support decoding
// but logs messages to LS_ERROR.
class NullVideoDecoder : public webrtc::VideoDecoder {
 public:
  int32_t InitDecode(const webrtc::VideoCodec* codec_settings,
                     int32_t number_of_cores) override {
    RTC_LOG(LS_ERROR) << "Can't initialize NullVideoDecoder.";
    return WEBRTC_VIDEO_CODEC_OK;
  }

  int32_t Decode(const webrtc::EncodedImage& input_image,
                 bool missing_frames,
                 int64_t render_time_ms) override {
    RTC_LOG(LS_ERROR) << "The NullVideoDecoder doesn't support decoding.";
    return WEBRTC_VIDEO_CODEC_OK;
  }

  int32_t RegisterDecodeCompleteCallback(
      webrtc::DecodedImageCallback* callback) override {
    RTC_LOG(LS_ERROR)
        << "Can't register decode complete callback on NullVideoDecoder.";
    return WEBRTC_VIDEO_CODEC_OK;
  }

  int32_t Release() override { return WEBRTC_VIDEO_CODEC_OK; }

  DecoderInfo GetDecoderInfo() const override {
    DecoderInfo info;
    info.implementation_name = "NullVideoDecoder";
    return info;
  }
  const char* ImplementationName() const override { return "NullVideoDecoder"; }
};

// TODO(https://bugs.webrtc.org/9974): Consider removing this workaround.
// Maximum time between frames before resetting the FrameBuffer to avoid RTP
// timestamps wraparound to affect FrameBuffer.
constexpr int kInactiveStreamThresholdMs = 600000;  //  10 minutes.

}  // namespace

namespace internal {

VideoReceiveStream::VideoReceiveStream(
    TaskQueueFactory* task_queue_factory,
    RtpStreamReceiverControllerInterface* receiver_controller,
    int num_cpu_cores,
    PacketRouter* packet_router,
    VideoReceiveStream::Config config,
    ProcessThread* process_thread,
    CallStats* call_stats,
    Clock* clock,
    VCMTiming* timing)
    : task_queue_factory_(task_queue_factory),
      transport_adapter_(config.rtcp_send_transport),
      config_(std::move(config)),
      num_cpu_cores_(num_cpu_cores),
      process_thread_(process_thread),
      clock_(clock),
      call_stats_(call_stats),
      source_tracker_(clock_),
      stats_proxy_(&config_, clock_),
      rtp_receive_statistics_(ReceiveStatistics::Create(clock_)),
      timing_(timing),
      video_receiver_(clock_, timing_.get()),
      rtp_video_stream_receiver_(clock_,
                                 &transport_adapter_,
                                 call_stats,
                                 packet_router,
                                 &config_,
                                 rtp_receive_statistics_.get(),
                                 &stats_proxy_,
                                 &stats_proxy_,
                                 process_thread_,
                                 this,     // NackSender
                                 nullptr,  // Use default KeyFrameRequestSender
                                 this,     // OnCompleteFrameCallback
                                 config_.frame_decryptor,
                                 config_.frame_transformer),
      rtp_stream_sync_(this),
      max_wait_for_keyframe_ms_(kMaxWaitForKeyFrameMs),
      max_wait_for_frame_ms_(kMaxWaitForFrameMs),
      decode_queue_(task_queue_factory_->CreateTaskQueue(
          "DecodingQueue",
          TaskQueueFactory::Priority::HIGH)) {
  RTC_LOG(LS_INFO) << "VideoReceiveStream: " << config_.ToString();

  RTC_DCHECK(config_.renderer);
  RTC_DCHECK(process_thread_);
  RTC_DCHECK(call_stats_);

  module_process_sequence_checker_.Detach();
  network_sequence_checker_.Detach();

  RTC_DCHECK(!config_.decoders.empty());
  RTC_CHECK(config_.decoder_factory);
  std::set<int> decoder_payload_types;
  for (const Decoder& decoder : config_.decoders) {
    RTC_CHECK(decoder_payload_types.find(decoder.payload_type) ==
              decoder_payload_types.end())
        << "Duplicate payload type (" << decoder.payload_type
        << ") for different decoders.";
    decoder_payload_types.insert(decoder.payload_type);
  }

  timing_->set_render_delay(config_.render_delay_ms);

  frame_buffer_.reset(
      new video_coding::FrameBuffer(clock_, timing_.get(), &stats_proxy_));

  process_thread_->RegisterModule(&rtp_stream_sync_, RTC_FROM_HERE);
  // Register with RtpStreamReceiverController.
  media_receiver_ = receiver_controller->CreateReceiver(
      config_.rtp.remote_ssrc, &rtp_video_stream_receiver_);
  if (config_.rtp.rtx_ssrc) {
    rtx_receive_stream_ = std::make_unique<RtxReceiveStream>(
        &rtp_video_stream_receiver_, config.rtp.rtx_associated_payload_types,
        config_.rtp.remote_ssrc, rtp_receive_statistics_.get());
    rtx_receiver_ = receiver_controller->CreateReceiver(
        config_.rtp.rtx_ssrc, rtx_receive_stream_.get());
  } else {
    rtp_receive_statistics_->EnableRetransmitDetection(config.rtp.remote_ssrc,
                                                       true);
  }
}

VideoReceiveStream::VideoReceiveStream(
    TaskQueueFactory* task_queue_factory,
    RtpStreamReceiverControllerInterface* receiver_controller,
    int num_cpu_cores,
    PacketRouter* packet_router,
    VideoReceiveStream::Config config,
    ProcessThread* process_thread,
    CallStats* call_stats,
    Clock* clock)
    : VideoReceiveStream(task_queue_factory,
                         receiver_controller,
                         num_cpu_cores,
                         packet_router,
                         std::move(config),
                         process_thread,
                         call_stats,
                         clock,
                         new VCMTiming(clock)) {}

VideoReceiveStream::~VideoReceiveStream() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  RTC_LOG(LS_INFO) << "~VideoReceiveStream: " << config_.ToString();
  Stop();
  process_thread_->DeRegisterModule(&rtp_stream_sync_);
}

void VideoReceiveStream::SignalNetworkState(NetworkState state) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtp_video_stream_receiver_.SignalNetworkState(state);
}

bool VideoReceiveStream::DeliverRtcp(const uint8_t* packet, size_t length) {
  return rtp_video_stream_receiver_.DeliverRtcp(packet, length);
}

void VideoReceiveStream::SetSync(Syncable* audio_syncable) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtp_stream_sync_.ConfigureSync(audio_syncable);
}

void VideoReceiveStream::Start() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);

  if (decoder_running_) {
    return;
  }

  const bool protected_by_fec = config_.rtp.protected_by_flexfec ||
                                rtp_video_stream_receiver_.IsUlpfecEnabled();

  if (rtp_video_stream_receiver_.IsRetransmissionsEnabled() &&
      protected_by_fec) {
    frame_buffer_->SetProtectionMode(kProtectionNackFEC);
  }

  transport_adapter_.Enable();
  rtc::VideoSinkInterface<VideoFrame>* renderer = nullptr;
  if (config_.enable_prerenderer_smoothing) {
    incoming_video_stream_.reset(new IncomingVideoStream(
        task_queue_factory_, config_.render_delay_ms, this));
    renderer = incoming_video_stream_.get();
  } else {
    renderer = this;
  }

  for (const Decoder& decoder : config_.decoders) {
    std::unique_ptr<VideoDecoder> video_decoder =
        config_.decoder_factory->CreateVideoDecoder(decoder.video_format);
    // If we still have no valid decoder, we have to create a "Null" decoder
    // that ignores all calls. The reason we can get into this state is that the
    // old decoder factory interface doesn't have a way to query supported
    // codecs.
    if (!video_decoder) {
      video_decoder = std::make_unique<NullVideoDecoder>();
    }

    std::string decoded_output_file =
        field_trial::FindFullName("WebRTC-DecoderDataDumpDirectory");
    // Because '/' can't be used inside a field trial parameter, we use ';'
    // instead.
    // This is only relevant to WebRTC-DecoderDataDumpDirectory
    // field trial. ';' is chosen arbitrary. Even though it's a legal character
    // in some file systems, we can sacrifice ability to use it in the path to
    // dumped video, since it's developers-only feature for debugging.
    absl::c_replace(decoded_output_file, ';', '/');
    if (!decoded_output_file.empty()) {
      char filename_buffer[256];
      rtc::SimpleStringBuilder ssb(filename_buffer);
      ssb << decoded_output_file << "/webrtc_receive_stream_"
          << this->config_.rtp.remote_ssrc << "-" << rtc::TimeMicros()
          << ".ivf";
      video_decoder = CreateFrameDumpingDecoderWrapper(
          std::move(video_decoder), FileWrapper::OpenWriteOnly(ssb.str()));
    }

    video_decoders_.push_back(std::move(video_decoder));

    video_receiver_.RegisterExternalDecoder(video_decoders_.back().get(),
                                            decoder.payload_type);
    VideoCodec codec = CreateDecoderVideoCodec(decoder);

    const bool raw_payload =
        config_.rtp.raw_payload_types.count(decoder.payload_type) > 0;
    rtp_video_stream_receiver_.AddReceiveCodec(decoder.payload_type, codec,
                                               decoder.video_format.parameters,
                                               raw_payload);
    RTC_CHECK_EQ(VCM_OK, video_receiver_.RegisterReceiveCodec(
                             decoder.payload_type, &codec, num_cpu_cores_));
  }

  RTC_DCHECK(renderer != nullptr);
  video_stream_decoder_.reset(
      new VideoStreamDecoder(&video_receiver_, &stats_proxy_, renderer));

  // Make sure we register as a stats observer *after* we've prepared the
  // |video_stream_decoder_|.
  call_stats_->RegisterStatsObserver(this);

  // Start decoding on task queue.
  video_receiver_.DecoderThreadStarting();
  stats_proxy_.DecoderThreadStarting();
  decode_queue_.PostTask([this] {
    RTC_DCHECK_RUN_ON(&decode_queue_);
    decoder_stopped_ = false;
    StartNextDecode();
  });
  decoder_running_ = true;
  rtp_video_stream_receiver_.StartReceive();
}

void VideoReceiveStream::Stop() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtp_video_stream_receiver_.StopReceive();

  stats_proxy_.OnUniqueFramesCounted(
      rtp_video_stream_receiver_.GetUniqueFramesSeen());

  decode_queue_.PostTask([this] { frame_buffer_->Stop(); });

  call_stats_->DeregisterStatsObserver(this);

  if (decoder_running_) {
    rtc::Event done;
    decode_queue_.PostTask([this, &done] {
      RTC_DCHECK_RUN_ON(&decode_queue_);
      decoder_stopped_ = true;
      done.Set();
    });
    done.Wait(rtc::Event::kForever);

    decoder_running_ = false;
    video_receiver_.DecoderThreadStopped();
    stats_proxy_.DecoderThreadStopped();
    // Deregister external decoders so they are no longer running during
    // destruction. This effectively stops the VCM since the decoder thread is
    // stopped, the VCM is deregistered and no asynchronous decoder threads are
    // running.
    for (const Decoder& decoder : config_.decoders)
      video_receiver_.RegisterExternalDecoder(nullptr, decoder.payload_type);

    UpdateHistograms();
  }

  video_stream_decoder_.reset();
  incoming_video_stream_.reset();
  transport_adapter_.Disable();
}

VideoReceiveStream::Stats VideoReceiveStream::GetStats() const {
  VideoReceiveStream::Stats stats = stats_proxy_.GetStats();
  stats.total_bitrate_bps = 0;
  StreamStatistician* statistician =
      rtp_receive_statistics_->GetStatistician(stats.ssrc);
  if (statistician) {
    stats.rtp_stats = statistician->GetStats();
    stats.total_bitrate_bps = statistician->BitrateReceived();
  }
  if (config_.rtp.rtx_ssrc) {
    StreamStatistician* rtx_statistician =
        rtp_receive_statistics_->GetStatistician(config_.rtp.rtx_ssrc);
    if (rtx_statistician)
      stats.total_bitrate_bps += rtx_statistician->BitrateReceived();
  }
  return stats;
}

void VideoReceiveStream::UpdateHistograms() {
  absl::optional<int> fraction_lost;
  StreamDataCounters rtp_stats;
  StreamStatistician* statistician =
      rtp_receive_statistics_->GetStatistician(config_.rtp.remote_ssrc);
  if (statistician) {
    fraction_lost = statistician->GetFractionLostInPercent();
    rtp_stats = statistician->GetReceiveStreamDataCounters();
  }
  if (config_.rtp.rtx_ssrc) {
    StreamStatistician* rtx_statistician =
        rtp_receive_statistics_->GetStatistician(config_.rtp.rtx_ssrc);
    if (rtx_statistician) {
      StreamDataCounters rtx_stats =
          rtx_statistician->GetReceiveStreamDataCounters();
      stats_proxy_.UpdateHistograms(fraction_lost, rtp_stats, &rtx_stats);
      return;
    }
  }
  stats_proxy_.UpdateHistograms(fraction_lost, rtp_stats, nullptr);
}

void VideoReceiveStream::AddSecondarySink(RtpPacketSinkInterface* sink) {
  rtp_video_stream_receiver_.AddSecondarySink(sink);
}

void VideoReceiveStream::RemoveSecondarySink(
    const RtpPacketSinkInterface* sink) {
  rtp_video_stream_receiver_.RemoveSecondarySink(sink);
}

bool VideoReceiveStream::SetBaseMinimumPlayoutDelayMs(int delay_ms) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  if (delay_ms < kMinBaseMinimumDelayMs || delay_ms > kMaxBaseMinimumDelayMs) {
    return false;
  }

  MutexLock lock(&playout_delay_lock_);
  base_minimum_playout_delay_ms_ = delay_ms;
  UpdatePlayoutDelays();
  return true;
}

int VideoReceiveStream::GetBaseMinimumPlayoutDelayMs() const {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);

  MutexLock lock(&playout_delay_lock_);
  return base_minimum_playout_delay_ms_;
}

// TODO(tommi): This method grabs a lock 6 times.
void VideoReceiveStream::OnFrame(const VideoFrame& video_frame) {
  int64_t video_playout_ntp_ms;
  int64_t sync_offset_ms;
  double estimated_freq_khz;

  // TODO(bugs.webrtc.org/10739): we should set local capture clock offset for
  // |video_frame.packet_infos|. But VideoFrame is const qualified here.

  // TODO(tommi): GetStreamSyncOffsetInMs grabs three locks.  One inside the
  // function itself, another in GetChannel() and a third in
  // GetPlayoutTimestamp.  Seems excessive.  Anyhow, I'm assuming the function
  // succeeds most of the time, which leads to grabbing a fourth lock.
  if (rtp_stream_sync_.GetStreamSyncOffsetInMs(
          video_frame.timestamp(), video_frame.render_time_ms(),
          &video_playout_ntp_ms, &sync_offset_ms, &estimated_freq_khz)) {
    // TODO(tommi): OnSyncOffsetUpdated grabs a lock.
    stats_proxy_.OnSyncOffsetUpdated(video_playout_ntp_ms, sync_offset_ms,
                                     estimated_freq_khz);
  }
  source_tracker_.OnFrameDelivered(video_frame.packet_infos());

  config_.renderer->OnFrame(video_frame);

  // TODO(tommi): OnRenderFrame grabs a lock too.
  stats_proxy_.OnRenderedFrame(video_frame);
}

void VideoReceiveStream::SetFrameDecryptor(
    rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor) {
  rtp_video_stream_receiver_.SetFrameDecryptor(std::move(frame_decryptor));
}

void VideoReceiveStream::SetDepacketizerToDecoderFrameTransformer(
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {
  rtp_video_stream_receiver_.SetDepacketizerToDecoderFrameTransformer(
      std::move(frame_transformer));
}

void VideoReceiveStream::SendNack(const std::vector<uint16_t>& sequence_numbers,
                                  bool buffering_allowed) {
  RTC_DCHECK(buffering_allowed);
  rtp_video_stream_receiver_.RequestPacketRetransmit(sequence_numbers);
}

void VideoReceiveStream::RequestKeyFrame(int64_t timestamp_ms) {
  rtp_video_stream_receiver_.RequestKeyFrame();
  last_keyframe_request_ms_ = timestamp_ms;
}

void VideoReceiveStream::OnCompleteFrame(std::unique_ptr<EncodedFrame> frame) {
  RTC_DCHECK_RUN_ON(&network_sequence_checker_);
  // TODO(https://bugs.webrtc.org/9974): Consider removing this workaround.
  int64_t time_now_ms = clock_->TimeInMilliseconds();
  if (last_complete_frame_time_ms_ > 0 &&
      time_now_ms - last_complete_frame_time_ms_ > kInactiveStreamThresholdMs) {
    frame_buffer_->Clear();
  }
  last_complete_frame_time_ms_ = time_now_ms;

  const VideoPlayoutDelay& playout_delay = frame->EncodedImage().playout_delay_;
  if (playout_delay.min_ms >= 0) {
    MutexLock lock(&playout_delay_lock_);
    frame_minimum_playout_delay_ms_ = playout_delay.min_ms;
    UpdatePlayoutDelays();
  }

  if (playout_delay.max_ms >= 0) {
    MutexLock lock(&playout_delay_lock_);
    frame_maximum_playout_delay_ms_ = playout_delay.max_ms;
    UpdatePlayoutDelays();
  }

  int64_t last_continuous_pid = frame_buffer_->InsertFrame(std::move(frame));
  if (last_continuous_pid != -1)
    rtp_video_stream_receiver_.FrameContinuous(last_continuous_pid);
}

void VideoReceiveStream::OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) {
  RTC_DCHECK_RUN_ON(&module_process_sequence_checker_);
  frame_buffer_->UpdateRtt(max_rtt_ms);
  rtp_video_stream_receiver_.UpdateRtt(max_rtt_ms);
}

uint32_t VideoReceiveStream::id() const {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  return config_.rtp.remote_ssrc;
}

absl::optional<Syncable::Info> VideoReceiveStream::GetInfo() const {
  RTC_DCHECK_RUN_ON(&module_process_sequence_checker_);
  absl::optional<Syncable::Info> info =
      rtp_video_stream_receiver_.GetSyncInfo();

  if (!info)
    return absl::nullopt;

  info->current_delay_ms = timing_->TargetVideoDelay();
  return info;
}

bool VideoReceiveStream::GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                                                int64_t* time_ms) const {
  RTC_NOTREACHED();
  return 0;
}

void VideoReceiveStream::SetEstimatedPlayoutNtpTimestampMs(
    int64_t ntp_timestamp_ms,
    int64_t time_ms) {
  RTC_NOTREACHED();
}

bool VideoReceiveStream::SetMinimumPlayoutDelay(int delay_ms) {
  RTC_DCHECK_RUN_ON(&module_process_sequence_checker_);
  MutexLock lock(&playout_delay_lock_);
  syncable_minimum_playout_delay_ms_ = delay_ms;
  UpdatePlayoutDelays();
  return true;
}

int64_t VideoReceiveStream::GetWaitMs() const {
  return keyframe_required_ ? max_wait_for_keyframe_ms_
                            : max_wait_for_frame_ms_;
}

void VideoReceiveStream::StartNextDecode() {
  TRACE_EVENT0("webrtc", "VideoReceiveStream::StartNextDecode");
  frame_buffer_->NextFrame(
      GetWaitMs(), keyframe_required_, &decode_queue_,
      /* encoded frame handler */
      [this](std::unique_ptr<EncodedFrame> frame, ReturnReason res) {
        RTC_DCHECK_EQ(frame == nullptr, res == ReturnReason::kTimeout);
        RTC_DCHECK_EQ(frame != nullptr, res == ReturnReason::kFrameFound);
        RTC_DCHECK_RUN_ON(&decode_queue_);
        if (decoder_stopped_)
          return;
        if (frame) {
          HandleEncodedFrame(std::move(frame));
        } else {
          HandleFrameBufferTimeout();
        }
        StartNextDecode();
      });
}

void VideoReceiveStream::HandleEncodedFrame(
    std::unique_ptr<EncodedFrame> frame) {
  int64_t now_ms = clock_->TimeInMilliseconds();

  // Current OnPreDecode only cares about QP for VP8.
  int qp = -1;
  if (frame->CodecSpecific()->codecType == kVideoCodecVP8) {
    if (!vp8::GetQp(frame->data(), frame->size(), &qp)) {
      RTC_LOG(LS_WARNING) << "Failed to extract QP from VP8 video frame";
    }
  }
  stats_proxy_.OnPreDecode(frame->CodecSpecific()->codecType, qp);
  HandleKeyFrameGeneration(frame->FrameType() == VideoFrameType::kVideoFrameKey,
                           now_ms);
  int decode_result = video_receiver_.Decode(frame.get());
  if (decode_result == WEBRTC_VIDEO_CODEC_OK ||
      decode_result == WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME) {
    keyframe_required_ = false;
    frame_decoded_ = true;
    rtp_video_stream_receiver_.FrameDecoded(frame->Id());

    if (decode_result == WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME)
      RequestKeyFrame(now_ms);
  } else if (!frame_decoded_ || !keyframe_required_ ||
             (last_keyframe_request_ms_ + max_wait_for_keyframe_ms_ < now_ms)) {
    keyframe_required_ = true;
    // TODO(philipel): Remove this keyframe request when downstream project
    //                 has been fixed.
    RequestKeyFrame(now_ms);
  }

  if (encoded_frame_buffer_function_) {
    encoded_frame_buffer_function_(WebRtcRecordableEncodedFrame(*frame));
  }
}

void VideoReceiveStream::HandleKeyFrameGeneration(
    bool received_frame_is_keyframe,
    int64_t now_ms) {
  // Repeat sending keyframe requests if we've requested a keyframe.
  if (!keyframe_generation_requested_) {
    return;
  }
  if (received_frame_is_keyframe) {
    keyframe_generation_requested_ = false;
  } else if (last_keyframe_request_ms_ + max_wait_for_keyframe_ms_ <= now_ms) {
    if (!IsReceivingKeyFrame(now_ms)) {
      RequestKeyFrame(now_ms);
    }
  } else {
    // It hasn't been long enough since the last keyframe request, do nothing.
  }
}

void VideoReceiveStream::HandleFrameBufferTimeout() {
  int64_t now_ms = clock_->TimeInMilliseconds();
  absl::optional<int64_t> last_packet_ms =
      rtp_video_stream_receiver_.LastReceivedPacketMs();

  // To avoid spamming keyframe requests for a stream that is not active we
  // check if we have received a packet within the last 5 seconds.
  bool stream_is_active = last_packet_ms && now_ms - *last_packet_ms < 5000;
  if (!stream_is_active)
    stats_proxy_.OnStreamInactive();

  if (stream_is_active && !IsReceivingKeyFrame(now_ms) &&
      (!config_.crypto_options.sframe.require_frame_encryption ||
       rtp_video_stream_receiver_.IsDecryptable())) {
    RTC_LOG(LS_WARNING) << "No decodable frame in " << GetWaitMs()
                        << " ms, requesting keyframe.";
    RequestKeyFrame(now_ms);
  }
}

bool VideoReceiveStream::IsReceivingKeyFrame(int64_t timestamp_ms) const {
  absl::optional<int64_t> last_keyframe_packet_ms =
      rtp_video_stream_receiver_.LastReceivedKeyframePacketMs();

  // If we recently have been receiving packets belonging to a keyframe then
  // we assume a keyframe is currently being received.
  bool receiving_keyframe =
      last_keyframe_packet_ms &&
      timestamp_ms - *last_keyframe_packet_ms < max_wait_for_keyframe_ms_;
  return receiving_keyframe;
}

void VideoReceiveStream::UpdatePlayoutDelays() const {
  const int minimum_delay_ms =
      std::max({frame_minimum_playout_delay_ms_, base_minimum_playout_delay_ms_,
                syncable_minimum_playout_delay_ms_});
  if (minimum_delay_ms >= 0) {
    timing_->set_min_playout_delay(minimum_delay_ms);
  }

  const int maximum_delay_ms = frame_maximum_playout_delay_ms_;
  if (maximum_delay_ms >= 0) {
    timing_->set_max_playout_delay(maximum_delay_ms);
  }
}

std::vector<webrtc::RtpSource> VideoReceiveStream::GetSources() const {
  return source_tracker_.GetSources();
}

VideoReceiveStream::RecordingState VideoReceiveStream::SetAndGetRecordingState(
    RecordingState state,
    bool generate_key_frame) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtc::Event event;
  RecordingState old_state;
  decode_queue_.PostTask([this, &event, &old_state, generate_key_frame,
                          state = std::move(state)] {
    RTC_DCHECK_RUN_ON(&decode_queue_);
    // Save old state.
    old_state.callback = std::move(encoded_frame_buffer_function_);
    old_state.keyframe_needed = keyframe_generation_requested_;
    old_state.last_keyframe_request_ms = last_keyframe_request_ms_;

    // Set new state.
    encoded_frame_buffer_function_ = std::move(state.callback);
    if (generate_key_frame) {
      RequestKeyFrame(clock_->TimeInMilliseconds());
      keyframe_generation_requested_ = true;
    } else {
      keyframe_generation_requested_ = state.keyframe_needed;
      last_keyframe_request_ms_ = state.last_keyframe_request_ms.value_or(0);
    }
    event.Set();
  });
  event.Wait(rtc::Event::kForever);
  return old_state;
}

void VideoReceiveStream::GenerateKeyFrame() {
  decode_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&decode_queue_);
    RequestKeyFrame(clock_->TimeInMilliseconds());
    keyframe_generation_requested_ = true;
  });
}

}  // namespace internal
}  // namespace webrtc
