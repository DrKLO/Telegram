/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_receive_stream2.h"

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
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/timing.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/thread_registry.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"
#include "video/call_stats2.h"
#include "video/frame_dumping_decoder.h"
#include "video/receive_statistics_proxy2.h"

namespace webrtc {

namespace internal {
constexpr int VideoReceiveStream2::kMaxWaitForKeyFrameMs;

namespace {

constexpr int kMinBaseMinimumDelayMs = 0;
constexpr int kMaxBaseMinimumDelayMs = 10000;

constexpr int kMaxWaitForFrameMs = 3000;

// Create a decoder for the preferred codec before the stream starts and any
// other decoder lazily on demand.
constexpr int kDefaultMaximumPreStreamDecoders = 1;

// Concrete instance of RecordableEncodedFrame wrapping needed content
// from EncodedFrame.
class WebRtcRecordableEncodedFrame : public RecordableEncodedFrame {
 public:
  explicit WebRtcRecordableEncodedFrame(
      const EncodedFrame& frame,
      RecordableEncodedFrame::EncodedResolution resolution)
      : buffer_(frame.GetEncodedData()),
        render_time_ms_(frame.RenderTime()),
        codec_(frame.CodecSpecific()->codecType),
        is_key_frame_(frame.FrameType() == VideoFrameType::kVideoFrameKey),
        resolution_(resolution) {
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

RenderResolution InitialDecoderResolution() {
  FieldTrialOptional<int> width("w");
  FieldTrialOptional<int> height("h");
  ParseFieldTrial(
      {&width, &height},
      field_trial::FindFullName("WebRTC-Video-InitialDecoderResolution"));
  if (width && height) {
    return RenderResolution(width.Value(), height.Value());
  }

  return RenderResolution(320, 180);
}

// Video decoder class to be used for unknown codecs. Doesn't support decoding
// but logs messages to LS_ERROR.
class NullVideoDecoder : public webrtc::VideoDecoder {
 public:
  bool Configure(const Settings& settings) override {
    RTC_LOG(LS_ERROR) << "Can't initialize NullVideoDecoder.";
    return true;
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

  const char* ImplementationName() const override { return "NullVideoDecoder"; }
};

bool IsKeyFrameAndUnspecifiedResolution(const EncodedFrame& frame) {
  return frame.FrameType() == VideoFrameType::kVideoFrameKey &&
         frame.EncodedImage()._encodedWidth == 0 &&
         frame.EncodedImage()._encodedHeight == 0;
}

// TODO(https://bugs.webrtc.org/9974): Consider removing this workaround.
// Maximum time between frames before resetting the FrameBuffer to avoid RTP
// timestamps wraparound to affect FrameBuffer.
constexpr int kInactiveStreamThresholdMs = 600000;  //  10 minutes.

}  // namespace

int DetermineMaxWaitForFrame(const VideoReceiveStream::Config& config,
                             bool is_keyframe) {
  // A (arbitrary) conversion factor between the remotely signalled NACK buffer
  // time (if not present defaults to 1000ms) and the maximum time we wait for a
  // remote frame. Chosen to not change existing defaults when using not
  // rtx-time.
  const int conversion_factor = 3;

  if (config.rtp.nack.rtp_history_ms > 0 &&
      conversion_factor * config.rtp.nack.rtp_history_ms < kMaxWaitForFrameMs) {
    return is_keyframe ? config.rtp.nack.rtp_history_ms
                       : conversion_factor * config.rtp.nack.rtp_history_ms;
  }
  return is_keyframe ? VideoReceiveStream2::kMaxWaitForKeyFrameMs
                     : kMaxWaitForFrameMs;
}

VideoReceiveStream2::VideoReceiveStream2(
    TaskQueueFactory* task_queue_factory,
    Call* call,
    int num_cpu_cores,
    PacketRouter* packet_router,
    VideoReceiveStream::Config config,
    CallStats* call_stats,
    Clock* clock,
    VCMTiming* timing,
    NackPeriodicProcessor* nack_periodic_processor)
    : task_queue_factory_(task_queue_factory),
      transport_adapter_(config.rtcp_send_transport),
      config_(std::move(config)),
      num_cpu_cores_(num_cpu_cores),
      call_(call),
      clock_(clock),
      call_stats_(call_stats),
      source_tracker_(clock_),
      stats_proxy_(config_.rtp.remote_ssrc, clock_, call->worker_thread()),
      rtp_receive_statistics_(ReceiveStatistics::Create(clock_)),
      timing_(timing),
      video_receiver_(clock_, timing_.get()),
      rtp_video_stream_receiver_(call->worker_thread(),
                                 clock_,
                                 &transport_adapter_,
                                 call_stats->AsRtcpRttStats(),
                                 packet_router,
                                 &config_,
                                 rtp_receive_statistics_.get(),
                                 &stats_proxy_,
                                 &stats_proxy_,
                                 nack_periodic_processor,
                                 this,     // NackSender
                                 nullptr,  // Use default KeyFrameRequestSender
                                 this,     // OnCompleteFrameCallback
                                 std::move(config_.frame_decryptor),
                                 std::move(config_.frame_transformer)),
      rtp_stream_sync_(call->worker_thread(), this),
      max_wait_for_keyframe_ms_(DetermineMaxWaitForFrame(config, true)),
      max_wait_for_frame_ms_(DetermineMaxWaitForFrame(config, false)),
      low_latency_renderer_enabled_("enabled", true),
      low_latency_renderer_include_predecode_buffer_("include_predecode_buffer",
                                                     true),
      maximum_pre_stream_decoders_("max", kDefaultMaximumPreStreamDecoders),
      decode_queue_(task_queue_factory_->CreateTaskQueue(
          "DecodingQueue",
          TaskQueueFactory::Priority::HIGH)) {
  RTC_LOG(LS_INFO) << "VideoReceiveStream2: " << config_.ToString();

  RTC_DCHECK(call_->worker_thread());
  RTC_DCHECK(config_.renderer);
  RTC_DCHECK(call_stats_);
  packet_sequence_checker_.Detach();

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

  if (config_.rtp.rtx_ssrc) {
    rtx_receive_stream_ = std::make_unique<RtxReceiveStream>(
        &rtp_video_stream_receiver_, config.rtp.rtx_associated_payload_types,
        config_.rtp.remote_ssrc, rtp_receive_statistics_.get());
  } else {
    rtp_receive_statistics_->EnableRetransmitDetection(config.rtp.remote_ssrc,
                                                       true);
  }

  ParseFieldTrial({&low_latency_renderer_enabled_,
                   &low_latency_renderer_include_predecode_buffer_},
                  field_trial::FindFullName("WebRTC-LowLatencyRenderer"));
  ParseFieldTrial(
      {
          &maximum_pre_stream_decoders_,
      },
      field_trial::FindFullName("WebRTC-PreStreamDecoders"));
}

VideoReceiveStream2::~VideoReceiveStream2() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  RTC_LOG(LS_INFO) << "~VideoReceiveStream2: " << config_.ToString();
  RTC_DCHECK(!media_receiver_);
  RTC_DCHECK(!rtx_receiver_);
  Stop();
}

void VideoReceiveStream2::RegisterWithTransport(
    RtpStreamReceiverControllerInterface* receiver_controller) {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  RTC_DCHECK(!media_receiver_);
  RTC_DCHECK(!rtx_receiver_);

  // Register with RtpStreamReceiverController.
  media_receiver_ = receiver_controller->CreateReceiver(
      config_.rtp.remote_ssrc, &rtp_video_stream_receiver_);
  if (config_.rtp.rtx_ssrc) {
    RTC_DCHECK(rtx_receive_stream_);
    rtx_receiver_ = receiver_controller->CreateReceiver(
        config_.rtp.rtx_ssrc, rtx_receive_stream_.get());
  }
}

void VideoReceiveStream2::UnregisterFromTransport() {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  media_receiver_.reset();
  rtx_receiver_.reset();
}

const VideoReceiveStream2::Config::Rtp& VideoReceiveStream2::rtp() const {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  return config_.rtp;
}

const std::string& VideoReceiveStream2::sync_group() const {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  return config_.sync_group;
}

void VideoReceiveStream2::SignalNetworkState(NetworkState state) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtp_video_stream_receiver_.SignalNetworkState(state);
}

bool VideoReceiveStream2::DeliverRtcp(const uint8_t* packet, size_t length) {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  return rtp_video_stream_receiver_.DeliverRtcp(packet, length);
}

void VideoReceiveStream2::SetSync(Syncable* audio_syncable) {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  rtp_stream_sync_.ConfigureSync(audio_syncable);
}

void VideoReceiveStream2::Start() {
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

  int decoders_count = 0;
  for (const Decoder& decoder : config_.decoders) {
    // Create up to maximum_pre_stream_decoders_ up front, wait the the other
    // decoders until they are requested (i.e., we receive the corresponding
    // payload).
    if (decoders_count < maximum_pre_stream_decoders_) {
      CreateAndRegisterExternalDecoder(decoder);
      ++decoders_count;
    }

    VideoDecoder::Settings settings;
    settings.set_codec_type(
        PayloadStringToCodecType(decoder.video_format.name));
    settings.set_max_render_resolution(InitialDecoderResolution());
    settings.set_number_of_cores(num_cpu_cores_);

    const bool raw_payload =
        config_.rtp.raw_payload_types.count(decoder.payload_type) > 0;
    {
      // TODO(bugs.webrtc.org/11993): Make this call on the network thread.
      RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
      rtp_video_stream_receiver_.AddReceiveCodec(
          decoder.payload_type, settings.codec_type(),
          decoder.video_format.parameters, raw_payload);
    }
    video_receiver_.RegisterReceiveCodec(decoder.payload_type, settings);
  }

  RTC_DCHECK(renderer != nullptr);
  video_stream_decoder_.reset(
      new VideoStreamDecoder(&video_receiver_, &stats_proxy_, renderer));

  // Make sure we register as a stats observer *after* we've prepared the
  // `video_stream_decoder_`.
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

  {
    // TODO(bugs.webrtc.org/11993): Make this call on the network thread.
    RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
    rtp_video_stream_receiver_.StartReceive();
  }
}

void VideoReceiveStream2::Stop() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  {
    // TODO(bugs.webrtc.org/11993): Make this call on the network thread.
    // Also call `GetUniqueFramesSeen()` at the same time (since it's a counter
    // that's updated on the network thread).
    RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
    rtp_video_stream_receiver_.StopReceive();
  }

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

void VideoReceiveStream2::SetRtpExtensions(
    std::vector<RtpExtension> extensions) {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  rtp_video_stream_receiver_.SetRtpExtensions(extensions);
  // TODO(tommi): We don't use the `c.rtp.extensions` member in the
  // VideoReceiveStream2 class, so this const_cast<> is a temporary hack to keep
  // things consistent between VideoReceiveStream2 and RtpVideoStreamReceiver2
  // for debugging purposes. The `packet_sequence_checker_` gives us assurances
  // that from a threading perspective, this is still safe. The accessors that
  // give read access to this state, run behind the same check.
  // The alternative to the const_cast<> would be to make `config_` non-const
  // and guarded by `packet_sequence_checker_`. However the scope of that state
  // is huge (the whole Config struct), and would require all methods that touch
  // the struct to abide the needs of the `extensions` member.
  VideoReceiveStream::Config& c =
      const_cast<VideoReceiveStream::Config&>(config_);
  c.rtp.extensions = std::move(extensions);
}

void VideoReceiveStream2::CreateAndRegisterExternalDecoder(
    const Decoder& decoder) {
  TRACE_EVENT0("webrtc",
               "VideoReceiveStream2::CreateAndRegisterExternalDecoder");
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
        << config_.rtp.remote_ssrc << "-" << rtc::TimeMicros() << ".ivf";
    video_decoder = CreateFrameDumpingDecoderWrapper(
        std::move(video_decoder), FileWrapper::OpenWriteOnly(ssb.str()));
  }

  video_decoders_.push_back(std::move(video_decoder));
  video_receiver_.RegisterExternalDecoder(video_decoders_.back().get(),
                                          decoder.payload_type);
}

VideoReceiveStream::Stats VideoReceiveStream2::GetStats() const {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  VideoReceiveStream2::Stats stats = stats_proxy_.GetStats();
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

void VideoReceiveStream2::UpdateHistograms() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
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

bool VideoReceiveStream2::SetBaseMinimumPlayoutDelayMs(int delay_ms) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  if (delay_ms < kMinBaseMinimumDelayMs || delay_ms > kMaxBaseMinimumDelayMs) {
    return false;
  }

  base_minimum_playout_delay_ms_ = delay_ms;
  UpdatePlayoutDelays();
  return true;
}

int VideoReceiveStream2::GetBaseMinimumPlayoutDelayMs() const {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  return base_minimum_playout_delay_ms_;
}

void VideoReceiveStream2::OnFrame(const VideoFrame& video_frame) {
  VideoFrameMetaData frame_meta(video_frame, clock_->CurrentTime());

  // TODO(bugs.webrtc.org/10739): we should set local capture clock offset for
  // `video_frame.packet_infos`. But VideoFrame is const qualified here.

  call_->worker_thread()->PostTask(
      ToQueuedTask(task_safety_, [frame_meta, this]() {
        RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
        int64_t video_playout_ntp_ms;
        int64_t sync_offset_ms;
        double estimated_freq_khz;
        if (rtp_stream_sync_.GetStreamSyncOffsetInMs(
                frame_meta.rtp_timestamp, frame_meta.render_time_ms(),
                &video_playout_ntp_ms, &sync_offset_ms, &estimated_freq_khz)) {
          stats_proxy_.OnSyncOffsetUpdated(video_playout_ntp_ms, sync_offset_ms,
                                           estimated_freq_khz);
        }
        stats_proxy_.OnRenderedFrame(frame_meta);
      }));

  source_tracker_.OnFrameDelivered(video_frame.packet_infos());
  config_.renderer->OnFrame(video_frame);
  webrtc::MutexLock lock(&pending_resolution_mutex_);
  if (pending_resolution_.has_value()) {
    if (!pending_resolution_->empty() &&
        (video_frame.width() != static_cast<int>(pending_resolution_->width) ||
         video_frame.height() !=
             static_cast<int>(pending_resolution_->height))) {
      RTC_LOG(LS_WARNING)
          << "Recordable encoded frame stream resolution was reported as "
          << pending_resolution_->width << "x" << pending_resolution_->height
          << " but the stream is now " << video_frame.width()
          << video_frame.height();
    }
    pending_resolution_ = RecordableEncodedFrame::EncodedResolution{
        static_cast<unsigned>(video_frame.width()),
        static_cast<unsigned>(video_frame.height())};
  }
}

void VideoReceiveStream2::SetFrameDecryptor(
    rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor) {
  rtp_video_stream_receiver_.SetFrameDecryptor(std::move(frame_decryptor));
}

void VideoReceiveStream2::SetDepacketizerToDecoderFrameTransformer(
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {
  rtp_video_stream_receiver_.SetDepacketizerToDecoderFrameTransformer(
      std::move(frame_transformer));
}

void VideoReceiveStream2::SendNack(
    const std::vector<uint16_t>& sequence_numbers,
    bool buffering_allowed) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  RTC_DCHECK(buffering_allowed);
  rtp_video_stream_receiver_.RequestPacketRetransmit(sequence_numbers);
}

void VideoReceiveStream2::RequestKeyFrame(int64_t timestamp_ms) {
  // Running on worker_sequence_checker_.
  // Called from RtpVideoStreamReceiver (rtp_video_stream_receiver_ is
  // ultimately responsible).
  rtp_video_stream_receiver_.RequestKeyFrame();
  decode_queue_.PostTask([this, timestamp_ms]() {
    RTC_DCHECK_RUN_ON(&decode_queue_);
    last_keyframe_request_ms_ = timestamp_ms;
  });
}

void VideoReceiveStream2::OnCompleteFrame(std::unique_ptr<EncodedFrame> frame) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);

  // TODO(https://bugs.webrtc.org/9974): Consider removing this workaround.
  int64_t time_now_ms = clock_->TimeInMilliseconds();
  if (last_complete_frame_time_ms_ > 0 &&
      time_now_ms - last_complete_frame_time_ms_ > kInactiveStreamThresholdMs) {
    frame_buffer_->Clear();
  }
  last_complete_frame_time_ms_ = time_now_ms;

  const VideoPlayoutDelay& playout_delay = frame->EncodedImage().playout_delay_;
  if (playout_delay.min_ms >= 0) {
    frame_minimum_playout_delay_ms_ = playout_delay.min_ms;
    UpdatePlayoutDelays();
  }

  if (playout_delay.max_ms >= 0) {
    frame_maximum_playout_delay_ms_ = playout_delay.max_ms;
    UpdatePlayoutDelays();
  }

  int64_t last_continuous_pid = frame_buffer_->InsertFrame(std::move(frame));
  if (last_continuous_pid != -1) {
    {
      // TODO(bugs.webrtc.org/11993): Call on the network thread.
      RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
      rtp_video_stream_receiver_.FrameContinuous(last_continuous_pid);
    }
  }
}

void VideoReceiveStream2::OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  frame_buffer_->UpdateRtt(max_rtt_ms);
  rtp_video_stream_receiver_.UpdateRtt(max_rtt_ms);
  stats_proxy_.OnRttUpdate(avg_rtt_ms);
}

uint32_t VideoReceiveStream2::id() const {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  return config_.rtp.remote_ssrc;
}

absl::optional<Syncable::Info> VideoReceiveStream2::GetInfo() const {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  absl::optional<Syncable::Info> info =
      rtp_video_stream_receiver_.GetSyncInfo();

  if (!info)
    return absl::nullopt;

  info->current_delay_ms = timing_->TargetVideoDelay();
  return info;
}

bool VideoReceiveStream2::GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                                                 int64_t* time_ms) const {
  RTC_DCHECK_NOTREACHED();
  return 0;
}

void VideoReceiveStream2::SetEstimatedPlayoutNtpTimestampMs(
    int64_t ntp_timestamp_ms,
    int64_t time_ms) {
  RTC_DCHECK_NOTREACHED();
}

bool VideoReceiveStream2::SetMinimumPlayoutDelay(int delay_ms) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  syncable_minimum_playout_delay_ms_ = delay_ms;
  UpdatePlayoutDelays();
  return true;
}

int64_t VideoReceiveStream2::GetMaxWaitMs() const {
  return keyframe_required_ ? max_wait_for_keyframe_ms_
                            : max_wait_for_frame_ms_;
}

void VideoReceiveStream2::StartNextDecode() {
  // Running on the decode thread.
  TRACE_EVENT0("webrtc", "VideoReceiveStream2::StartNextDecode");
  frame_buffer_->NextFrame(
      GetMaxWaitMs(), keyframe_required_, &decode_queue_,
      /* encoded frame handler */
      [this](std::unique_ptr<EncodedFrame> frame) {
        RTC_DCHECK_RUN_ON(&decode_queue_);
        if (decoder_stopped_)
          return;
        if (frame) {
          HandleEncodedFrame(std::move(frame));
        } else {
          int64_t now_ms = clock_->TimeInMilliseconds();
          // TODO(bugs.webrtc.org/11993): PostTask to the network thread.
          call_->worker_thread()->PostTask(ToQueuedTask(
              task_safety_, [this, now_ms, wait_ms = GetMaxWaitMs()]() {
                RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
                HandleFrameBufferTimeout(now_ms, wait_ms);
              }));
        }
        StartNextDecode();
      });
}

void VideoReceiveStream2::HandleEncodedFrame(
    std::unique_ptr<EncodedFrame> frame) {
  // Running on `decode_queue_`.
  int64_t now_ms = clock_->TimeInMilliseconds();

  // Current OnPreDecode only cares about QP for VP8.
  int qp = -1;
  if (frame->CodecSpecific()->codecType == kVideoCodecVP8) {
    if (!vp8::GetQp(frame->data(), frame->size(), &qp)) {
      RTC_LOG(LS_WARNING) << "Failed to extract QP from VP8 video frame";
    }
  }
  stats_proxy_.OnPreDecode(frame->CodecSpecific()->codecType, qp);

  bool force_request_key_frame = false;
  int64_t decoded_frame_picture_id = -1;

  const bool keyframe_request_is_due =
      now_ms >= (last_keyframe_request_ms_ + max_wait_for_keyframe_ms_);

  if (!video_receiver_.IsExternalDecoderRegistered(frame->PayloadType())) {
    // Look for the decoder with this payload type.
    for (const Decoder& decoder : config_.decoders) {
      if (decoder.payload_type == frame->PayloadType()) {
        CreateAndRegisterExternalDecoder(decoder);
        break;
      }
    }
  }

  int64_t frame_id = frame->Id();
  bool received_frame_is_keyframe =
      frame->FrameType() == VideoFrameType::kVideoFrameKey;
  int decode_result = DecodeAndMaybeDispatchEncodedFrame(std::move(frame));
  if (decode_result == WEBRTC_VIDEO_CODEC_OK ||
      decode_result == WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME) {
    keyframe_required_ = false;
    frame_decoded_ = true;

    decoded_frame_picture_id = frame_id;

    if (decode_result == WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME)
      force_request_key_frame = true;
  } else if (!frame_decoded_ || !keyframe_required_ ||
             keyframe_request_is_due) {
    keyframe_required_ = true;
    // TODO(philipel): Remove this keyframe request when downstream project
    //                 has been fixed.
    force_request_key_frame = true;
  }

  {
    // TODO(bugs.webrtc.org/11993): Make this PostTask to the network thread.
    call_->worker_thread()->PostTask(ToQueuedTask(
        task_safety_,
        [this, now_ms, received_frame_is_keyframe, force_request_key_frame,
         decoded_frame_picture_id, keyframe_request_is_due]() {
          RTC_DCHECK_RUN_ON(&packet_sequence_checker_);

          if (decoded_frame_picture_id != -1)
            rtp_video_stream_receiver_.FrameDecoded(decoded_frame_picture_id);

          HandleKeyFrameGeneration(received_frame_is_keyframe, now_ms,
                                   force_request_key_frame,
                                   keyframe_request_is_due);
        }));
  }
}

int VideoReceiveStream2::DecodeAndMaybeDispatchEncodedFrame(
    std::unique_ptr<EncodedFrame> frame) {
  // Running on decode_queue_.

  // If `buffered_encoded_frames_` grows out of control (=60 queued frames),
  // maybe due to a stuck decoder, we just halt the process here and log the
  // error.
  const bool encoded_frame_output_enabled =
      encoded_frame_buffer_function_ != nullptr &&
      buffered_encoded_frames_.size() < kBufferedEncodedFramesMaxSize;
  EncodedFrame* frame_ptr = frame.get();
  if (encoded_frame_output_enabled) {
    // If we receive a key frame with unset resolution, hold on dispatching the
    // frame and following ones until we know a resolution of the stream.
    // NOTE: The code below has a race where it can report the wrong
    // resolution for keyframes after an initial keyframe of other resolution.
    // However, the only known consumer of this information is the W3C
    // MediaRecorder and it will only use the resolution in the first encoded
    // keyframe from WebRTC, so misreporting is fine.
    buffered_encoded_frames_.push_back(std::move(frame));
    if (buffered_encoded_frames_.size() == kBufferedEncodedFramesMaxSize)
      RTC_LOG(LS_ERROR) << "About to halt recordable encoded frame output due "
                           "to too many buffered frames.";

    webrtc::MutexLock lock(&pending_resolution_mutex_);
    if (IsKeyFrameAndUnspecifiedResolution(*frame_ptr) &&
        !pending_resolution_.has_value())
      pending_resolution_.emplace();
  }

  int decode_result = video_receiver_.Decode(frame_ptr);
  if (encoded_frame_output_enabled) {
    absl::optional<RecordableEncodedFrame::EncodedResolution>
        pending_resolution;
    {
      // Fish out `pending_resolution_` to avoid taking the mutex on every lap
      // or dispatching under the mutex in the flush loop.
      webrtc::MutexLock lock(&pending_resolution_mutex_);
      if (pending_resolution_.has_value())
        pending_resolution = *pending_resolution_;
    }
    if (!pending_resolution.has_value() || !pending_resolution->empty()) {
      // Flush the buffered frames.
      for (const auto& frame : buffered_encoded_frames_) {
        RecordableEncodedFrame::EncodedResolution resolution{
            frame->EncodedImage()._encodedWidth,
            frame->EncodedImage()._encodedHeight};
        if (IsKeyFrameAndUnspecifiedResolution(*frame)) {
          RTC_DCHECK(!pending_resolution->empty());
          resolution = *pending_resolution;
        }
        encoded_frame_buffer_function_(
            WebRtcRecordableEncodedFrame(*frame, resolution));
      }
      buffered_encoded_frames_.clear();
    }
  }
  return decode_result;
}

// RTC_RUN_ON(packet_sequence_checker_)
void VideoReceiveStream2::HandleKeyFrameGeneration(
    bool received_frame_is_keyframe,
    int64_t now_ms,
    bool always_request_key_frame,
    bool keyframe_request_is_due) {
  bool request_key_frame = always_request_key_frame;

  // Repeat sending keyframe requests if we've requested a keyframe.
  if (keyframe_generation_requested_) {
    if (received_frame_is_keyframe) {
      keyframe_generation_requested_ = false;
    } else if (keyframe_request_is_due) {
      if (!IsReceivingKeyFrame(now_ms)) {
        request_key_frame = true;
      }
    } else {
      // It hasn't been long enough since the last keyframe request, do nothing.
    }
  }

  if (request_key_frame) {
    // HandleKeyFrameGeneration is initated from the decode thread -
    // RequestKeyFrame() triggers a call back to the decode thread.
    // Perhaps there's a way to avoid that.
    RequestKeyFrame(now_ms);
  }
}

// RTC_RUN_ON(packet_sequence_checker_)
void VideoReceiveStream2::HandleFrameBufferTimeout(int64_t now_ms,
                                                   int64_t wait_ms) {
  absl::optional<int64_t> last_packet_ms =
      rtp_video_stream_receiver_.LastReceivedPacketMs();

  // To avoid spamming keyframe requests for a stream that is not active we
  // check if we have received a packet within the last 5 seconds.
  const bool stream_is_active =
      last_packet_ms && now_ms - *last_packet_ms < 5000;
  if (!stream_is_active)
    stats_proxy_.OnStreamInactive();

  if (stream_is_active && !IsReceivingKeyFrame(now_ms) &&
      (!config_.crypto_options.sframe.require_frame_encryption ||
       rtp_video_stream_receiver_.IsDecryptable())) {
    RTC_LOG(LS_WARNING) << "No decodable frame in " << wait_ms
                        << " ms, requesting keyframe.";
    RequestKeyFrame(now_ms);
  }
}

// RTC_RUN_ON(packet_sequence_checker_)
bool VideoReceiveStream2::IsReceivingKeyFrame(int64_t timestamp_ms) const {
  absl::optional<int64_t> last_keyframe_packet_ms =
      rtp_video_stream_receiver_.LastReceivedKeyframePacketMs();

  // If we recently have been receiving packets belonging to a keyframe then
  // we assume a keyframe is currently being received.
  bool receiving_keyframe =
      last_keyframe_packet_ms &&
      timestamp_ms - *last_keyframe_packet_ms < max_wait_for_keyframe_ms_;
  return receiving_keyframe;
}

void VideoReceiveStream2::UpdatePlayoutDelays() const {
  // Running on worker_sequence_checker_.
  const int minimum_delay_ms =
      std::max({frame_minimum_playout_delay_ms_, base_minimum_playout_delay_ms_,
                syncable_minimum_playout_delay_ms_});
  if (minimum_delay_ms >= 0) {
    timing_->set_min_playout_delay(minimum_delay_ms);
    if (frame_minimum_playout_delay_ms_ == 0 &&
        frame_maximum_playout_delay_ms_ > 0 && low_latency_renderer_enabled_) {
      // TODO(kron): Estimate frame rate from video stream.
      constexpr double kFrameRate = 60.0;
      // Convert playout delay in ms to number of frames.
      int max_composition_delay_in_frames = std::lrint(
          static_cast<double>(frame_maximum_playout_delay_ms_ * kFrameRate) /
          rtc::kNumMillisecsPerSec);
      if (low_latency_renderer_include_predecode_buffer_) {
        // Subtract frames in buffer.
        max_composition_delay_in_frames = std::max<int16_t>(
            max_composition_delay_in_frames - frame_buffer_->Size(), 0);
      }
      timing_->SetMaxCompositionDelayInFrames(
          absl::make_optional(max_composition_delay_in_frames));
    }
  }

  const int maximum_delay_ms = frame_maximum_playout_delay_ms_;
  if (maximum_delay_ms >= 0) {
    timing_->set_max_playout_delay(maximum_delay_ms);
  }
}

std::vector<webrtc::RtpSource> VideoReceiveStream2::GetSources() const {
  return source_tracker_.GetSources();
}

VideoReceiveStream2::RecordingState
VideoReceiveStream2::SetAndGetRecordingState(RecordingState state,
                                             bool generate_key_frame) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  rtc::Event event;

  // Save old state, set the new state.
  RecordingState old_state;

  decode_queue_.PostTask(
      [this, &event, &old_state, callback = std::move(state.callback),
       generate_key_frame,
       last_keyframe_request = state.last_keyframe_request_ms.value_or(0)] {
        RTC_DCHECK_RUN_ON(&decode_queue_);
        old_state.callback = std::move(encoded_frame_buffer_function_);
        encoded_frame_buffer_function_ = std::move(callback);

        old_state.last_keyframe_request_ms = last_keyframe_request_ms_;
        last_keyframe_request_ms_ = generate_key_frame
                                        ? clock_->TimeInMilliseconds()
                                        : last_keyframe_request;

        event.Set();
      });

  if (generate_key_frame) {
    rtp_video_stream_receiver_.RequestKeyFrame();
    {
      // TODO(bugs.webrtc.org/11993): Post this to the network thread.
      RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
      keyframe_generation_requested_ = true;
    }
  }

  event.Wait(rtc::Event::kForever);
  return old_state;
}

void VideoReceiveStream2::GenerateKeyFrame() {
  RTC_DCHECK_RUN_ON(&packet_sequence_checker_);
  RequestKeyFrame(clock_->TimeInMilliseconds());
  keyframe_generation_requested_ = true;
}

}  // namespace internal
}  // namespace webrtc
