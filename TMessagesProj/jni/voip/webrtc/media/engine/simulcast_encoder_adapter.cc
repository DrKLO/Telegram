/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/simulcast_encoder_adapter.h"

#include <stdio.h>
#include <string.h>

#include <algorithm>
#include <cstdint>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "api/scoped_refptr.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_codec_constants.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "api/video_codecs/video_encoder_software_fallback_wrapper.h"
#include "media/base/video_common.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/utility/simulcast_rate_allocator.h"
#include "rtc_base/atomic_ops.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/rate_control_settings.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace {

const unsigned int kDefaultMinQp = 2;
const unsigned int kDefaultMaxQp = 56;
// Max qp for lowest spatial resolution when doing simulcast.
const unsigned int kLowestResMaxQp = 45;

absl::optional<unsigned int> GetScreenshareBoostedQpValue() {
  std::string experiment_group =
      webrtc::field_trial::FindFullName("WebRTC-BoostedScreenshareQp");
  unsigned int qp;
  if (sscanf(experiment_group.c_str(), "%u", &qp) != 1)
    return absl::nullopt;
  qp = std::min(qp, 63u);
  qp = std::max(qp, 1u);
  return qp;
}

uint32_t SumStreamMaxBitrate(int streams, const webrtc::VideoCodec& codec) {
  uint32_t bitrate_sum = 0;
  for (int i = 0; i < streams; ++i) {
    bitrate_sum += codec.simulcastStream[i].maxBitrate;
  }
  return bitrate_sum;
}

int CountAllStreams(const webrtc::VideoCodec& codec) {
  int total_streams_count =
      codec.numberOfSimulcastStreams < 1 ? 1 : codec.numberOfSimulcastStreams;
  uint32_t simulcast_max_bitrate =
      SumStreamMaxBitrate(total_streams_count, codec);
  if (simulcast_max_bitrate == 0) {
    total_streams_count = 1;
  }
  return total_streams_count;
}

int CountActiveStreams(const webrtc::VideoCodec& codec) {
  if (codec.numberOfSimulcastStreams < 1) {
    return 1;
  }
  int total_streams_count = CountAllStreams(codec);
  int active_streams_count = 0;
  for (int i = 0; i < total_streams_count; ++i) {
    if (codec.simulcastStream[i].active) {
      ++active_streams_count;
    }
  }
  return active_streams_count;
}

int VerifyCodec(const webrtc::VideoCodec* inst) {
  if (inst == nullptr) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->maxFramerate < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  // allow zero to represent an unspecified maxBitRate
  if (inst->maxBitrate > 0 && inst->startBitrate > inst->maxBitrate) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->width <= 1 || inst->height <= 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  if (inst->codecType == webrtc::kVideoCodecVP8 &&
      inst->VP8().automaticResizeOn && CountActiveStreams(*inst) > 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

bool StreamQualityCompare(const webrtc::SpatialLayer& a,
                          const webrtc::SpatialLayer& b) {
  return std::tie(a.height, a.width, a.maxBitrate, a.maxFramerate) <
         std::tie(b.height, b.width, b.maxBitrate, b.maxFramerate);
}

void GetLowestAndHighestQualityStreamIndixes(
    rtc::ArrayView<webrtc::SpatialLayer> streams,
    int* lowest_quality_stream_idx,
    int* highest_quality_stream_idx) {
  const auto lowest_highest_quality_streams =
      absl::c_minmax_element(streams, StreamQualityCompare);
  *lowest_quality_stream_idx =
      std::distance(streams.begin(), lowest_highest_quality_streams.first);
  *highest_quality_stream_idx =
      std::distance(streams.begin(), lowest_highest_quality_streams.second);
}

std::vector<uint32_t> GetStreamStartBitratesKbps(
    const webrtc::VideoCodec& codec) {
  std::vector<uint32_t> start_bitrates;
  std::unique_ptr<webrtc::VideoBitrateAllocator> rate_allocator =
      std::make_unique<webrtc::SimulcastRateAllocator>(codec);
  webrtc::VideoBitrateAllocation allocation =
      rate_allocator->Allocate(webrtc::VideoBitrateAllocationParameters(
          codec.startBitrate * 1000, codec.maxFramerate));

  int total_streams_count = CountAllStreams(codec);
  for (int i = 0; i < total_streams_count; ++i) {
    uint32_t stream_bitrate = allocation.GetSpatialLayerSum(i) / 1000;
    start_bitrates.push_back(stream_bitrate);
  }
  return start_bitrates;
}

}  // namespace

namespace webrtc {

SimulcastEncoderAdapter::EncoderContext::EncoderContext(
    std::unique_ptr<VideoEncoder> encoder,
    bool prefer_temporal_support)
    : encoder_(std::move(encoder)),
      prefer_temporal_support_(prefer_temporal_support) {}

void SimulcastEncoderAdapter::EncoderContext::Release() {
  if (encoder_) {
    encoder_->RegisterEncodeCompleteCallback(nullptr);
    encoder_->Release();
  }
}

SimulcastEncoderAdapter::StreamContext::StreamContext(
    SimulcastEncoderAdapter* parent,
    std::unique_ptr<EncoderContext> encoder_context,
    std::unique_ptr<FramerateController> framerate_controller,
    int stream_idx,
    uint16_t width,
    uint16_t height,
    bool is_paused)
    : parent_(parent),
      encoder_context_(std::move(encoder_context)),
      framerate_controller_(std::move(framerate_controller)),
      stream_idx_(stream_idx),
      width_(width),
      height_(height),
      is_keyframe_needed_(false),
      is_paused_(is_paused) {
  if (parent_) {
    encoder_context_->encoder().RegisterEncodeCompleteCallback(this);
  }
}

SimulcastEncoderAdapter::StreamContext::StreamContext(StreamContext&& rhs)
    : parent_(rhs.parent_),
      encoder_context_(std::move(rhs.encoder_context_)),
      framerate_controller_(std::move(rhs.framerate_controller_)),
      stream_idx_(rhs.stream_idx_),
      width_(rhs.width_),
      height_(rhs.height_),
      is_keyframe_needed_(rhs.is_keyframe_needed_),
      is_paused_(rhs.is_paused_) {
  if (parent_) {
    encoder_context_->encoder().RegisterEncodeCompleteCallback(this);
  }
}

SimulcastEncoderAdapter::StreamContext::~StreamContext() {
  if (encoder_context_) {
    encoder_context_->Release();
  }
}

std::unique_ptr<SimulcastEncoderAdapter::EncoderContext>
SimulcastEncoderAdapter::StreamContext::ReleaseEncoderContext() && {
  encoder_context_->Release();
  return std::move(encoder_context_);
}

void SimulcastEncoderAdapter::StreamContext::OnKeyframe(Timestamp timestamp) {
  is_keyframe_needed_ = false;
  if (framerate_controller_) {
    framerate_controller_->AddFrame(timestamp.ms());
  }
}

bool SimulcastEncoderAdapter::StreamContext::ShouldDropFrame(
    Timestamp timestamp) {
  if (!framerate_controller_) {
    return false;
  }

  if (framerate_controller_->DropFrame(timestamp.ms())) {
    return true;
  }
  framerate_controller_->AddFrame(timestamp.ms());
  return false;
}

EncodedImageCallback::Result
SimulcastEncoderAdapter::StreamContext::OnEncodedImage(
    const EncodedImage& encoded_image,
    const CodecSpecificInfo* codec_specific_info) {
  RTC_CHECK(parent_);  // If null, this method should never be called.
  return parent_->OnEncodedImage(stream_idx_, encoded_image,
                                 codec_specific_info);
}

void SimulcastEncoderAdapter::StreamContext::OnDroppedFrame(
    DropReason /*reason*/) {
  RTC_CHECK(parent_);  // If null, this method should never be called.
  parent_->OnDroppedFrame(stream_idx_);
}

SimulcastEncoderAdapter::SimulcastEncoderAdapter(VideoEncoderFactory* factory,
                                                 const SdpVideoFormat& format)
    : SimulcastEncoderAdapter(factory, nullptr, format) {}

SimulcastEncoderAdapter::SimulcastEncoderAdapter(
    VideoEncoderFactory* primary_factory,
    VideoEncoderFactory* fallback_factory,
    const SdpVideoFormat& format)
    : inited_(0),
      primary_encoder_factory_(primary_factory),
      fallback_encoder_factory_(fallback_factory),
      video_format_(format),
      total_streams_count_(0),
      bypass_mode_(false),
      encoded_complete_callback_(nullptr),
      experimental_boosted_screenshare_qp_(GetScreenshareBoostedQpValue()),
      boost_base_layer_quality_(RateControlSettings::ParseFromFieldTrials()
                                    .Vp8BoostBaseLayerQuality()),
      prefer_temporal_support_on_base_layer_(field_trial::IsEnabled(
          "WebRTC-Video-PreferTemporalSupportOnBaseLayer")) {
  RTC_DCHECK(primary_factory);

  // The adapter is typically created on the worker thread, but operated on
  // the encoder task queue.
  encoder_queue_.Detach();
}

SimulcastEncoderAdapter::~SimulcastEncoderAdapter() {
  RTC_DCHECK(!Initialized());
  DestroyStoredEncoders();
}

void SimulcastEncoderAdapter::SetFecControllerOverride(
    FecControllerOverride* /*fec_controller_override*/) {
  // Ignored.
}

int SimulcastEncoderAdapter::Release() {
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  while (!stream_contexts_.empty()) {
    // Move the encoder instances and put it on the |cached_encoder_contexts_|
    // where it may possibly be reused from (ordering does not matter).
    cached_encoder_contexts_.push_front(
        std::move(stream_contexts_.back()).ReleaseEncoderContext());
    stream_contexts_.pop_back();
  }

  bypass_mode_ = false;

  // It's legal to move the encoder to another queue now.
  encoder_queue_.Detach();

  rtc::AtomicOps::ReleaseStore(&inited_, 0);

  return WEBRTC_VIDEO_CODEC_OK;
}

int SimulcastEncoderAdapter::InitEncode(
    const VideoCodec* inst,
    const VideoEncoder::Settings& settings) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  if (settings.number_of_cores < 1) {
    return WEBRTC_VIDEO_CODEC_ERR_PARAMETER;
  }

  int ret = VerifyCodec(inst);
  if (ret < 0) {
    return ret;
  }

  Release();

  codec_ = *inst;
  total_streams_count_ = CountAllStreams(*inst);

  // TODO(ronghuawu): Remove once this is handled in LibvpxVp8Encoder.
  if (codec_.qpMax < kDefaultMinQp) {
    codec_.qpMax = kDefaultMaxQp;
  }

  bool is_legacy_singlecast = codec_.numberOfSimulcastStreams == 0;
  int lowest_quality_stream_idx = 0;
  int highest_quality_stream_idx = 0;
  if (!is_legacy_singlecast) {
    GetLowestAndHighestQualityStreamIndixes(
        rtc::ArrayView<SpatialLayer>(codec_.simulcastStream,
                                     total_streams_count_),
        &lowest_quality_stream_idx, &highest_quality_stream_idx);
  }

  std::unique_ptr<EncoderContext> encoder_context = FetchOrCreateEncoderContext(
      /*is_lowest_quality_stream=*/(
          is_legacy_singlecast ||
          codec_.simulcastStream[lowest_quality_stream_idx].active));
  if (encoder_context == nullptr) {
    return WEBRTC_VIDEO_CODEC_MEMORY;
  }

  // Two distinct scenarios:
  // * Singlecast (total_streams_count == 1) or simulcast with simulcast-capable
  //   underlaying encoder implementation. SEA operates in bypass mode: original
  //   settings are passed to the underlaying encoder, frame encode complete
  //   callback is not intercepted.
  // * Multi-encoder simulcast or singlecast if layers are deactivated
  //   (total_streams_count > 1 and active_streams_count >= 1). SEA creates
  //   N=active_streams_count encoders and configures each to produce a single
  //   stream.

  // Singlecast or simulcast with simulcast-capable underlaying encoder.
  if (total_streams_count_ == 1 ||
      encoder_context->encoder().GetEncoderInfo().supports_simulcast) {
    int ret = encoder_context->encoder().InitEncode(&codec_, settings);
    if (ret >= 0) {
      int active_streams_count = CountActiveStreams(*inst);
      stream_contexts_.emplace_back(
          /*parent=*/nullptr, std::move(encoder_context),
          /*framerate_controller=*/nullptr, /*stream_idx=*/0, codec_.width,
          codec_.height, /*is_paused=*/active_streams_count == 0);
      bypass_mode_ = true;

      DestroyStoredEncoders();
      rtc::AtomicOps::ReleaseStore(&inited_, 1);
      return WEBRTC_VIDEO_CODEC_OK;
    }

    encoder_context->Release();
    if (total_streams_count_ == 1) {
      // Failed to initialize singlecast encoder.
      return ret;
    }
  }

  // Multi-encoder simulcast or singlecast (deactivated layers).
  std::vector<uint32_t> stream_start_bitrate_kbps =
      GetStreamStartBitratesKbps(codec_);

  for (int stream_idx = 0; stream_idx < total_streams_count_; ++stream_idx) {
    if (!is_legacy_singlecast && !codec_.simulcastStream[stream_idx].active) {
      continue;
    }

    if (encoder_context == nullptr) {
      encoder_context = FetchOrCreateEncoderContext(
          /*is_lowest_quality_stream=*/stream_idx == lowest_quality_stream_idx);
    }
    if (encoder_context == nullptr) {
      Release();
      return WEBRTC_VIDEO_CODEC_MEMORY;
    }

    VideoCodec stream_codec = MakeStreamCodec(
        codec_, stream_idx, stream_start_bitrate_kbps[stream_idx],
        /*is_lowest_quality_stream=*/stream_idx == lowest_quality_stream_idx,
        /*is_highest_quality_stream=*/stream_idx == highest_quality_stream_idx);

    int ret = encoder_context->encoder().InitEncode(&stream_codec, settings);
    if (ret < 0) {
      encoder_context.reset();
      Release();
      return ret;
    }

    // Intercept frame encode complete callback only for upper streams, where
    // we need to set a correct stream index. Set |parent| to nullptr for the
    // lowest stream to bypass the callback.
    SimulcastEncoderAdapter* parent = stream_idx > 0 ? this : nullptr;

    bool is_paused = stream_start_bitrate_kbps[stream_idx] == 0;
    stream_contexts_.emplace_back(
        parent, std::move(encoder_context),
        std::make_unique<FramerateController>(stream_codec.maxFramerate),
        stream_idx, stream_codec.width, stream_codec.height, is_paused);
  }

  // To save memory, don't store encoders that we don't use.
  DestroyStoredEncoders();

  rtc::AtomicOps::ReleaseStore(&inited_, 1);
  return WEBRTC_VIDEO_CODEC_OK;
}

int SimulcastEncoderAdapter::Encode(
    const VideoFrame& input_image,
    const std::vector<VideoFrameType>* frame_types) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  if (!Initialized()) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }
  if (encoded_complete_callback_ == nullptr) {
    return WEBRTC_VIDEO_CODEC_UNINITIALIZED;
  }

  if (encoder_info_override_.requested_resolution_alignment()) {
    const int alignment =
        *encoder_info_override_.requested_resolution_alignment();
    if (input_image.width() % alignment != 0 ||
        input_image.height() % alignment != 0) {
      RTC_LOG(LS_WARNING) << "Frame " << input_image.width() << "x"
                          << input_image.height() << " not divisible by "
                          << alignment;
      return WEBRTC_VIDEO_CODEC_ERROR;
    }
    if (encoder_info_override_.apply_alignment_to_all_simulcast_layers()) {
      for (const auto& layer : stream_contexts_) {
        if (layer.width() % alignment != 0 || layer.height() % alignment != 0) {
          RTC_LOG(LS_WARNING)
              << "Codec " << layer.width() << "x" << layer.height()
              << " not divisible by " << alignment;
          return WEBRTC_VIDEO_CODEC_ERROR;
        }
      }
    }
  }

  // All active streams should generate a key frame if
  // a key frame is requested by any stream.
  bool is_keyframe_needed = false;
  if (frame_types) {
    for (const auto& frame_type : *frame_types) {
      if (frame_type == VideoFrameType::kVideoFrameKey) {
        is_keyframe_needed = true;
        break;
      }
    }
  }

  if (!is_keyframe_needed) {
    for (const auto& layer : stream_contexts_) {
      if (layer.is_keyframe_needed()) {
        is_keyframe_needed = true;
        break;
      }
    }
  }

  // Temporary thay may hold the result of texture to i420 buffer conversion.
  rtc::scoped_refptr<VideoFrameBuffer> src_buffer;
  int src_width = input_image.width();
  int src_height = input_image.height();

  for (auto& layer : stream_contexts_) {
    // Don't encode frames in resolutions that we don't intend to send.
    if (layer.is_paused()) {
      continue;
    }

    // Convert timestamp from RTP 90kHz clock.
    const Timestamp frame_timestamp =
        Timestamp::Micros((1000 * input_image.timestamp()) / 90);

    // If adapter is passed through and only one sw encoder does simulcast,
    // frame types for all streams should be passed to the encoder unchanged.
    // Otherwise a single per-encoder frame type is passed.
    std::vector<VideoFrameType> stream_frame_types(
        bypass_mode_ ? total_streams_count_ : 1);
    if (is_keyframe_needed) {
      std::fill(stream_frame_types.begin(), stream_frame_types.end(),
                VideoFrameType::kVideoFrameKey);
      layer.OnKeyframe(frame_timestamp);
    } else {
      if (layer.ShouldDropFrame(frame_timestamp)) {
        continue;
      }
      std::fill(stream_frame_types.begin(), stream_frame_types.end(),
                VideoFrameType::kVideoFrameDelta);
    }

    // If scaling isn't required, because the input resolution
    // matches the destination or the input image is empty (e.g.
    // a keyframe request for encoders with internal camera
    // sources) or the source image has a native handle, pass the image on
    // directly. Otherwise, we'll scale it to match what the encoder expects
    // (below).
    // For texture frames, the underlying encoder is expected to be able to
    // correctly sample/scale the source texture.
    // TODO(perkj): ensure that works going forward, and figure out how this
    // affects webrtc:5683.
    if ((layer.width() == src_width && layer.height() == src_height) ||
        (input_image.video_frame_buffer()->type() ==
             VideoFrameBuffer::Type::kNative &&
         layer.encoder().GetEncoderInfo().supports_native_handle)) {
      int ret = layer.encoder().Encode(input_image, &stream_frame_types);
      if (ret != WEBRTC_VIDEO_CODEC_OK) {
        return ret;
      }
    } else {
      if (src_buffer == nullptr) {
        src_buffer = input_image.video_frame_buffer();
      }
      rtc::scoped_refptr<VideoFrameBuffer> dst_buffer =
          src_buffer->Scale(layer.width(), layer.height());
      if (!dst_buffer) {
        RTC_LOG(LS_ERROR) << "Failed to scale video frame";
        return WEBRTC_VIDEO_CODEC_ENCODER_FAILURE;
      }

      // UpdateRect is not propagated to lower simulcast layers currently.
      // TODO(ilnik): Consider scaling UpdateRect together with the buffer.
      VideoFrame frame(input_image);
      frame.set_video_frame_buffer(dst_buffer);
      frame.set_rotation(webrtc::kVideoRotation_0);
      frame.set_update_rect(
          VideoFrame::UpdateRect{0, 0, frame.width(), frame.height()});
      int ret = layer.encoder().Encode(frame, &stream_frame_types);
      if (ret != WEBRTC_VIDEO_CODEC_OK) {
        return ret;
      }
    }
  }

  return WEBRTC_VIDEO_CODEC_OK;
}

int SimulcastEncoderAdapter::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);
  encoded_complete_callback_ = callback;
  if (!stream_contexts_.empty() && stream_contexts_.front().stream_idx() == 0) {
    // Bypass frame encode complete callback for the lowest layer since there is
    // no need to override frame's spatial index.
    stream_contexts_.front().encoder().RegisterEncodeCompleteCallback(callback);
  }
  return WEBRTC_VIDEO_CODEC_OK;
}

void SimulcastEncoderAdapter::SetRates(
    const RateControlParameters& parameters) {
  RTC_DCHECK_RUN_ON(&encoder_queue_);

  if (!Initialized()) {
    RTC_LOG(LS_WARNING) << "SetRates while not initialized";
    return;
  }

  if (parameters.framerate_fps < 1.0) {
    RTC_LOG(LS_WARNING) << "Invalid framerate: " << parameters.framerate_fps;
    return;
  }

  codec_.maxFramerate = static_cast<uint32_t>(parameters.framerate_fps + 0.5);

  if (bypass_mode_) {
    stream_contexts_.front().encoder().SetRates(parameters);
    return;
  }

  for (StreamContext& layer_context : stream_contexts_) {
    int stream_idx = layer_context.stream_idx();
    uint32_t stream_bitrate_kbps =
        parameters.bitrate.GetSpatialLayerSum(stream_idx) / 1000;

    // Need a key frame if we have not sent this stream before.
    if (stream_bitrate_kbps > 0 && layer_context.is_paused()) {
      layer_context.set_is_keyframe_needed();
    }
    layer_context.set_is_paused(stream_bitrate_kbps == 0);

    // Slice the temporal layers out of the full allocation and pass it on to
    // the encoder handling the current simulcast stream.
    RateControlParameters stream_parameters = parameters;
    stream_parameters.bitrate = VideoBitrateAllocation();
    for (int i = 0; i < kMaxTemporalStreams; ++i) {
      if (parameters.bitrate.HasBitrate(stream_idx, i)) {
        stream_parameters.bitrate.SetBitrate(
            0, i, parameters.bitrate.GetBitrate(stream_idx, i));
      }
    }

    // Assign link allocation proportionally to spatial layer allocation.
    if (!parameters.bandwidth_allocation.IsZero() &&
        parameters.bitrate.get_sum_bps() > 0) {
      stream_parameters.bandwidth_allocation =
          DataRate::BitsPerSec((parameters.bandwidth_allocation.bps() *
                                stream_parameters.bitrate.get_sum_bps()) /
                               parameters.bitrate.get_sum_bps());
      // Make sure we don't allocate bandwidth lower than target bitrate.
      if (stream_parameters.bandwidth_allocation.bps() <
          stream_parameters.bitrate.get_sum_bps()) {
        stream_parameters.bandwidth_allocation =
            DataRate::BitsPerSec(stream_parameters.bitrate.get_sum_bps());
      }
    }

    stream_parameters.framerate_fps = std::min<double>(
        parameters.framerate_fps,
        layer_context.target_fps().value_or(parameters.framerate_fps));

    layer_context.encoder().SetRates(stream_parameters);
  }
}

void SimulcastEncoderAdapter::OnPacketLossRateUpdate(float packet_loss_rate) {
  for (auto& c : stream_contexts_) {
    c.encoder().OnPacketLossRateUpdate(packet_loss_rate);
  }
}

void SimulcastEncoderAdapter::OnRttUpdate(int64_t rtt_ms) {
  for (auto& c : stream_contexts_) {
    c.encoder().OnRttUpdate(rtt_ms);
  }
}

void SimulcastEncoderAdapter::OnLossNotification(
    const LossNotification& loss_notification) {
  for (auto& c : stream_contexts_) {
    c.encoder().OnLossNotification(loss_notification);
  }
}

// TODO(brandtr): Add task checker to this member function, when all encoder
// callbacks are coming in on the encoder queue.
EncodedImageCallback::Result SimulcastEncoderAdapter::OnEncodedImage(
    size_t stream_idx,
    const EncodedImage& encodedImage,
    const CodecSpecificInfo* codecSpecificInfo) {
  EncodedImage stream_image(encodedImage);
  CodecSpecificInfo stream_codec_specific = *codecSpecificInfo;

  stream_image.SetSpatialIndex(stream_idx);

  return encoded_complete_callback_->OnEncodedImage(stream_image,
                                                    &stream_codec_specific);
}

void SimulcastEncoderAdapter::OnDroppedFrame(size_t stream_idx) {
  // Not yet implemented.
}

bool SimulcastEncoderAdapter::Initialized() const {
  return rtc::AtomicOps::AcquireLoad(&inited_) == 1;
}

void SimulcastEncoderAdapter::DestroyStoredEncoders() {
  while (!cached_encoder_contexts_.empty()) {
    cached_encoder_contexts_.pop_back();
  }
}

std::unique_ptr<SimulcastEncoderAdapter::EncoderContext>
SimulcastEncoderAdapter::FetchOrCreateEncoderContext(
    bool is_lowest_quality_stream) {
  bool prefer_temporal_support = fallback_encoder_factory_ != nullptr &&
                                 is_lowest_quality_stream &&
                                 prefer_temporal_support_on_base_layer_;

  // Toggling of |prefer_temporal_support| requires encoder recreation. Find
  // and reuse encoder with desired |prefer_temporal_support|. Otherwise, if
  // there is no such encoder in the cache, create a new instance.
  auto encoder_context_iter =
      std::find_if(cached_encoder_contexts_.begin(),
                   cached_encoder_contexts_.end(), [&](auto& encoder_context) {
                     return encoder_context->prefer_temporal_support() ==
                            prefer_temporal_support;
                   });

  std::unique_ptr<SimulcastEncoderAdapter::EncoderContext> encoder_context;
  if (encoder_context_iter != cached_encoder_contexts_.end()) {
    encoder_context = std::move(*encoder_context_iter);
    cached_encoder_contexts_.erase(encoder_context_iter);
  } else {
    std::unique_ptr<VideoEncoder> encoder =
        primary_encoder_factory_->CreateVideoEncoder(video_format_);
    if (fallback_encoder_factory_ != nullptr) {
      encoder = CreateVideoEncoderSoftwareFallbackWrapper(
          fallback_encoder_factory_->CreateVideoEncoder(video_format_),
          std::move(encoder), prefer_temporal_support);
    }

    encoder_context = std::make_unique<SimulcastEncoderAdapter::EncoderContext>(
        std::move(encoder), prefer_temporal_support);
  }

  encoder_context->encoder().RegisterEncodeCompleteCallback(
      encoded_complete_callback_);
  return encoder_context;
}

webrtc::VideoCodec SimulcastEncoderAdapter::MakeStreamCodec(
    const webrtc::VideoCodec& codec,
    int stream_idx,
    uint32_t start_bitrate_kbps,
    bool is_lowest_quality_stream,
    bool is_highest_quality_stream) {
  webrtc::VideoCodec codec_params = codec;
  const SpatialLayer& stream_params = codec.simulcastStream[stream_idx];

  codec_params.numberOfSimulcastStreams = 0;
  codec_params.width = stream_params.width;
  codec_params.height = stream_params.height;
  codec_params.maxBitrate = stream_params.maxBitrate;
  codec_params.minBitrate = stream_params.minBitrate;
  codec_params.maxFramerate = stream_params.maxFramerate;
  codec_params.qpMax = stream_params.qpMax;
  codec_params.active = stream_params.active;
  // Settings that are based on stream/resolution.
  if (is_lowest_quality_stream) {
    // Settings for lowest spatial resolutions.
    if (codec.mode == VideoCodecMode::kScreensharing) {
      if (experimental_boosted_screenshare_qp_) {
        codec_params.qpMax = *experimental_boosted_screenshare_qp_;
      }
    } else if (boost_base_layer_quality_) {
      codec_params.qpMax = kLowestResMaxQp;
    }
  }
  if (codec.codecType == webrtc::kVideoCodecVP8) {
    codec_params.VP8()->numberOfTemporalLayers =
        stream_params.numberOfTemporalLayers;
    if (!is_highest_quality_stream) {
      // For resolutions below CIF, set the codec |complexity| parameter to
      // kComplexityHigher, which maps to cpu_used = -4.
      int pixels_per_frame = codec_params.width * codec_params.height;
      if (pixels_per_frame < 352 * 288) {
        codec_params.VP8()->complexity =
            webrtc::VideoCodecComplexity::kComplexityHigher;
      }
      // Turn off denoising for all streams but the highest resolution.
      codec_params.VP8()->denoisingOn = false;
    }
  } else if (codec.codecType == webrtc::kVideoCodecH264) {
    codec_params.H264()->numberOfTemporalLayers =
        stream_params.numberOfTemporalLayers;
  }

  // Cap start bitrate to the min bitrate in order to avoid strange codec
  // behavior.
  codec_params.startBitrate =
      std::max(stream_params.minBitrate, start_bitrate_kbps);

  // Legacy screenshare mode is only enabled for the first simulcast layer
  codec_params.legacy_conference_mode =
      codec.legacy_conference_mode && stream_idx == 0;

  return codec_params;
}

void SimulcastEncoderAdapter::OverrideFromFieldTrial(
    VideoEncoder::EncoderInfo* info) const {
  if (encoder_info_override_.requested_resolution_alignment()) {
    info->requested_resolution_alignment =
        *encoder_info_override_.requested_resolution_alignment();
    info->apply_alignment_to_all_simulcast_layers =
        encoder_info_override_.apply_alignment_to_all_simulcast_layers();
  }
  if (!encoder_info_override_.resolution_bitrate_limits().empty()) {
    info->resolution_bitrate_limits =
        encoder_info_override_.resolution_bitrate_limits();
  }
}

VideoEncoder::EncoderInfo SimulcastEncoderAdapter::GetEncoderInfo() const {
  if (stream_contexts_.size() == 1) {
    // Not using simulcast adapting functionality, just pass through.
    VideoEncoder::EncoderInfo info =
        stream_contexts_.front().encoder().GetEncoderInfo();
    OverrideFromFieldTrial(&info);
    return info;
  }

  VideoEncoder::EncoderInfo encoder_info;
  encoder_info.implementation_name = "SimulcastEncoderAdapter";
  encoder_info.requested_resolution_alignment = 1;
  encoder_info.apply_alignment_to_all_simulcast_layers = false;
  encoder_info.supports_native_handle = true;
  encoder_info.scaling_settings.thresholds = absl::nullopt;
  if (stream_contexts_.empty()) {
    OverrideFromFieldTrial(&encoder_info);
    return encoder_info;
  }

  encoder_info.scaling_settings = VideoEncoder::ScalingSettings::kOff;

  for (size_t i = 0; i < stream_contexts_.size(); ++i) {
    VideoEncoder::EncoderInfo encoder_impl_info =
        stream_contexts_[i].encoder().GetEncoderInfo();

    if (i == 0) {
      // Encoder name indicates names of all sub-encoders.
      encoder_info.implementation_name += " (";
      encoder_info.implementation_name += encoder_impl_info.implementation_name;

      encoder_info.supports_native_handle =
          encoder_impl_info.supports_native_handle;
      encoder_info.has_trusted_rate_controller =
          encoder_impl_info.has_trusted_rate_controller;
      encoder_info.is_hardware_accelerated =
          encoder_impl_info.is_hardware_accelerated;
      encoder_info.has_internal_source = encoder_impl_info.has_internal_source;
    } else {
      encoder_info.implementation_name += ", ";
      encoder_info.implementation_name += encoder_impl_info.implementation_name;

      // Native handle supported if any encoder supports it.
      encoder_info.supports_native_handle |=
          encoder_impl_info.supports_native_handle;

      // Trusted rate controller only if all encoders have it.
      encoder_info.has_trusted_rate_controller &=
          encoder_impl_info.has_trusted_rate_controller;

      // Uses hardware support if any of the encoders uses it.
      // For example, if we are having issues with down-scaling due to
      // pipelining delay in HW encoders we need higher encoder usage
      // thresholds in CPU adaptation.
      encoder_info.is_hardware_accelerated |=
          encoder_impl_info.is_hardware_accelerated;

      // Has internal source only if all encoders have it.
      encoder_info.has_internal_source &= encoder_impl_info.has_internal_source;
    }
    encoder_info.fps_allocation[i] = encoder_impl_info.fps_allocation[0];
    encoder_info.requested_resolution_alignment = cricket::LeastCommonMultiple(
        encoder_info.requested_resolution_alignment,
        encoder_impl_info.requested_resolution_alignment);
    if (encoder_impl_info.apply_alignment_to_all_simulcast_layers) {
      encoder_info.apply_alignment_to_all_simulcast_layers = true;
    }
  }
  encoder_info.implementation_name += ")";

  OverrideFromFieldTrial(&encoder_info);

  return encoder_info;
}

}  // namespace webrtc
