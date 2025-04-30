/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/codecs/test/videoprocessor.h"

#include <string.h>

#include <algorithm>
#include <cstddef>
#include <limits>
#include <memory>
#include <utility>

#include "api/scoped_refptr.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "api/video/i420_buffer.h"
#include "api/video/video_bitrate_allocator_factory.h"
#include "api/video/video_frame_buffer.h"
#include "api/video/video_rotation.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_encoder.h"
#include "common_video/h264/h264_common.h"
#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/video_coding/codecs/interface/common_constants.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/checks.h"
#include "rtc_base/time_utils.h"
#include "test/gtest.h"
#include "third_party/libyuv/include/libyuv/compare.h"
#include "third_party/libyuv/include/libyuv/scale.h"

namespace webrtc {
namespace test {

namespace {
const int kMsToRtpTimestamp = kVideoPayloadTypeFrequency / 1000;
const int kMaxBufferedInputFrames = 20;

const VideoEncoder::Capabilities kCapabilities(false);

size_t GetMaxNaluSizeBytes(const EncodedImage& encoded_frame,
                           const VideoCodecTestFixture::Config& config) {
  if (config.codec_settings.codecType != kVideoCodecH264)
    return 0;

  std::vector<webrtc::H264::NaluIndex> nalu_indices =
      webrtc::H264::FindNaluIndices(encoded_frame.data(), encoded_frame.size());

  RTC_CHECK(!nalu_indices.empty());

  size_t max_size = 0;
  for (const webrtc::H264::NaluIndex& index : nalu_indices)
    max_size = std::max(max_size, index.payload_size);

  return max_size;
}

size_t GetTemporalLayerIndex(const CodecSpecificInfo& codec_specific) {
  size_t temporal_idx = 0;
  if (codec_specific.codecType == kVideoCodecVP8) {
    temporal_idx = codec_specific.codecSpecific.VP8.temporalIdx;
  } else if (codec_specific.codecType == kVideoCodecVP9) {
    temporal_idx = codec_specific.codecSpecific.VP9.temporal_idx;
  }
  if (temporal_idx == kNoTemporalIdx) {
    temporal_idx = 0;
  }
  return temporal_idx;
}

int GetElapsedTimeMicroseconds(int64_t start_ns, int64_t stop_ns) {
  int64_t diff_us = (stop_ns - start_ns) / rtc::kNumNanosecsPerMicrosec;
  RTC_DCHECK_GE(diff_us, std::numeric_limits<int>::min());
  RTC_DCHECK_LE(diff_us, std::numeric_limits<int>::max());
  return static_cast<int>(diff_us);
}

void CalculateFrameQuality(const I420BufferInterface& ref_buffer,
                           const I420BufferInterface& dec_buffer,
                           VideoCodecTestStats::FrameStatistics* frame_stat,
                           bool calc_ssim) {
  if (ref_buffer.width() != dec_buffer.width() ||
      ref_buffer.height() != dec_buffer.height()) {
    RTC_CHECK_GE(ref_buffer.width(), dec_buffer.width());
    RTC_CHECK_GE(ref_buffer.height(), dec_buffer.height());
    // Downscale reference frame.
    rtc::scoped_refptr<I420Buffer> scaled_buffer =
        I420Buffer::Create(dec_buffer.width(), dec_buffer.height());
    I420Scale(ref_buffer.DataY(), ref_buffer.StrideY(), ref_buffer.DataU(),
              ref_buffer.StrideU(), ref_buffer.DataV(), ref_buffer.StrideV(),
              ref_buffer.width(), ref_buffer.height(),
              scaled_buffer->MutableDataY(), scaled_buffer->StrideY(),
              scaled_buffer->MutableDataU(), scaled_buffer->StrideU(),
              scaled_buffer->MutableDataV(), scaled_buffer->StrideV(),
              scaled_buffer->width(), scaled_buffer->height(),
              libyuv::kFilterBox);

    CalculateFrameQuality(*scaled_buffer, dec_buffer, frame_stat, calc_ssim);
  } else {
    const uint64_t sse_y = libyuv::ComputeSumSquareErrorPlane(
        dec_buffer.DataY(), dec_buffer.StrideY(), ref_buffer.DataY(),
        ref_buffer.StrideY(), dec_buffer.width(), dec_buffer.height());

    const uint64_t sse_u = libyuv::ComputeSumSquareErrorPlane(
        dec_buffer.DataU(), dec_buffer.StrideU(), ref_buffer.DataU(),
        ref_buffer.StrideU(), dec_buffer.width() / 2, dec_buffer.height() / 2);

    const uint64_t sse_v = libyuv::ComputeSumSquareErrorPlane(
        dec_buffer.DataV(), dec_buffer.StrideV(), ref_buffer.DataV(),
        ref_buffer.StrideV(), dec_buffer.width() / 2, dec_buffer.height() / 2);

    const size_t num_y_samples = dec_buffer.width() * dec_buffer.height();
    const size_t num_u_samples =
        dec_buffer.width() / 2 * dec_buffer.height() / 2;

    frame_stat->psnr_y = libyuv::SumSquareErrorToPsnr(sse_y, num_y_samples);
    frame_stat->psnr_u = libyuv::SumSquareErrorToPsnr(sse_u, num_u_samples);
    frame_stat->psnr_v = libyuv::SumSquareErrorToPsnr(sse_v, num_u_samples);
    frame_stat->psnr = libyuv::SumSquareErrorToPsnr(
        sse_y + sse_u + sse_v, num_y_samples + 2 * num_u_samples);

    if (calc_ssim) {
      frame_stat->ssim = I420SSIM(ref_buffer, dec_buffer);
    }
  }
}

}  // namespace

VideoProcessor::VideoProcessor(webrtc::VideoEncoder* encoder,
                               VideoDecoderList* decoders,
                               FrameReader* input_frame_reader,
                               const VideoCodecTestFixture::Config& config,
                               VideoCodecTestStatsImpl* stats,
                               IvfFileWriterMap* encoded_frame_writers,
                               FrameWriterList* decoded_frame_writers)
    : config_(config),
      num_simulcast_or_spatial_layers_(
          std::max(config_.NumberOfSimulcastStreams(),
                   config_.NumberOfSpatialLayers())),
      analyze_frame_quality_(!config_.measure_cpu),
      stats_(stats),
      encoder_(encoder),
      decoders_(decoders),
      bitrate_allocator_(
          CreateBuiltinVideoBitrateAllocatorFactory()
              ->CreateVideoBitrateAllocator(config_.codec_settings)),
      encode_callback_(this),
      input_frame_reader_(input_frame_reader),
      merged_encoded_frames_(num_simulcast_or_spatial_layers_),
      encoded_frame_writers_(encoded_frame_writers),
      decoded_frame_writers_(decoded_frame_writers),
      last_inputed_frame_num_(0),
      last_inputed_timestamp_(0),
      first_encoded_frame_(num_simulcast_or_spatial_layers_, true),
      last_encoded_frame_num_(num_simulcast_or_spatial_layers_),
      first_decoded_frame_(num_simulcast_or_spatial_layers_, true),
      last_decoded_frame_num_(num_simulcast_or_spatial_layers_),
      last_decoded_frame_buffer_(num_simulcast_or_spatial_layers_),
      post_encode_time_ns_(0),
      is_finalized_(false) {
  // Sanity checks.
  RTC_CHECK(TaskQueueBase::Current())
      << "VideoProcessor must be run on a task queue.";
  RTC_CHECK(stats_);
  RTC_CHECK(encoder_);
  RTC_CHECK(decoders_);
  RTC_CHECK_EQ(decoders_->size(), num_simulcast_or_spatial_layers_);
  RTC_CHECK(input_frame_reader_);
  RTC_CHECK(encoded_frame_writers_);
  RTC_CHECK(!decoded_frame_writers ||
            decoded_frame_writers->size() == num_simulcast_or_spatial_layers_);

  // Setup required callbacks for the encoder and decoder and initialize them.
  RTC_CHECK_EQ(encoder_->RegisterEncodeCompleteCallback(&encode_callback_),
               WEBRTC_VIDEO_CODEC_OK);

  // Initialize codecs so that they are ready to receive frames.
  RTC_CHECK_EQ(encoder_->InitEncode(
                   &config_.codec_settings,
                   VideoEncoder::Settings(
                       kCapabilities, static_cast<int>(config_.NumberOfCores()),
                       config_.max_payload_size_bytes)),
               WEBRTC_VIDEO_CODEC_OK);

  for (size_t i = 0; i < num_simulcast_or_spatial_layers_; ++i) {
    decode_callback_.push_back(
        std::make_unique<VideoProcessorDecodeCompleteCallback>(this, i));
    VideoDecoder::Settings decoder_settings;
    decoder_settings.set_max_render_resolution(
        {config_.codec_settings.width, config_.codec_settings.height});
    decoder_settings.set_codec_type(config_.codec_settings.codecType);
    decoder_settings.set_number_of_cores(config_.NumberOfCores());
    RTC_CHECK(decoders_->at(i)->Configure(decoder_settings));
    RTC_CHECK_EQ(decoders_->at(i)->RegisterDecodeCompleteCallback(
                     decode_callback_.at(i).get()),
                 WEBRTC_VIDEO_CODEC_OK);
  }
}

VideoProcessor::~VideoProcessor() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  if (!is_finalized_) {
    Finalize();
  }

  // Explicitly reset codecs, in case they don't do that themselves when they
  // go out of scope.
  RTC_CHECK_EQ(encoder_->Release(), WEBRTC_VIDEO_CODEC_OK);
  encoder_->RegisterEncodeCompleteCallback(nullptr);
  for (auto& decoder : *decoders_) {
    RTC_CHECK_EQ(decoder->Release(), WEBRTC_VIDEO_CODEC_OK);
    decoder->RegisterDecodeCompleteCallback(nullptr);
  }

  // Sanity check.
  RTC_CHECK_LE(input_frames_.size(), kMaxBufferedInputFrames);
}

void VideoProcessor::ProcessFrame() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!is_finalized_);

  RTC_DCHECK_GT(target_rates_.size(), 0u);
  RTC_DCHECK_EQ(target_rates_.begin()->first, 0u);
  RateProfile target_rate =
      std::prev(target_rates_.upper_bound(last_inputed_frame_num_))->second;

  const size_t frame_number = last_inputed_frame_num_++;

  // Get input frame and store for future quality calculation.
  Resolution resolution = Resolution({.width = config_.codec_settings.width,
                                      .height = config_.codec_settings.height});
  FrameReader::Ratio framerate_scale = FrameReader::Ratio(
      {.num = config_.clip_fps.value_or(config_.codec_settings.maxFramerate),
       .den = static_cast<int>(config_.codec_settings.maxFramerate)});
  rtc::scoped_refptr<I420BufferInterface> buffer =
      input_frame_reader_->PullFrame(
          /*frame_num*/ nullptr, resolution, framerate_scale);

  RTC_CHECK(buffer) << "Tried to read too many frames from the file.";
  const size_t timestamp =
      last_inputed_timestamp_ +
      static_cast<size_t>(kVideoPayloadTypeFrequency / target_rate.input_fps);
  VideoFrame input_frame =
      VideoFrame::Builder()
          .set_video_frame_buffer(buffer)
          .set_timestamp_rtp(static_cast<uint32_t>(timestamp))
          .set_timestamp_ms(static_cast<int64_t>(timestamp / kMsToRtpTimestamp))
          .set_rotation(webrtc::kVideoRotation_0)
          .build();
  // Store input frame as a reference for quality calculations.
  if (config_.decode && !config_.measure_cpu) {
    if (input_frames_.size() == kMaxBufferedInputFrames) {
      input_frames_.erase(input_frames_.begin());
    }

    if (config_.reference_width != -1 && config_.reference_height != -1 &&
        (input_frame.width() != config_.reference_width ||
         input_frame.height() != config_.reference_height)) {
      rtc::scoped_refptr<I420Buffer> scaled_buffer = I420Buffer::Create(
          config_.codec_settings.width, config_.codec_settings.height);
      scaled_buffer->ScaleFrom(*input_frame.video_frame_buffer()->ToI420());

      VideoFrame scaled_reference_frame = input_frame;
      scaled_reference_frame.set_video_frame_buffer(scaled_buffer);
      input_frames_.emplace(frame_number, scaled_reference_frame);

      if (config_.reference_width == config_.codec_settings.width &&
          config_.reference_height == config_.codec_settings.height) {
        // Both encoding and comparison uses the same down-scale factor, reuse
        // it for encoder below.
        input_frame = scaled_reference_frame;
      }
    } else {
      input_frames_.emplace(frame_number, input_frame);
    }
  }
  last_inputed_timestamp_ = timestamp;

  post_encode_time_ns_ = 0;

  // Create frame statistics object for all simulcast/spatial layers.
  for (size_t i = 0; i < num_simulcast_or_spatial_layers_; ++i) {
    FrameStatistics frame_stat(frame_number, timestamp, i);
    stats_->AddFrame(frame_stat);
  }

  // For the highest measurement accuracy of the encode time, the start/stop
  // time recordings should wrap the Encode call as tightly as possible.
  const int64_t encode_start_ns = rtc::TimeNanos();
  for (size_t i = 0; i < num_simulcast_or_spatial_layers_; ++i) {
    FrameStatistics* frame_stat = stats_->GetFrame(frame_number, i);
    frame_stat->encode_start_ns = encode_start_ns;
  }

  if (input_frame.width() != config_.codec_settings.width ||
      input_frame.height() != config_.codec_settings.height) {
    rtc::scoped_refptr<I420Buffer> scaled_buffer = I420Buffer::Create(
        config_.codec_settings.width, config_.codec_settings.height);
    scaled_buffer->ScaleFrom(*input_frame.video_frame_buffer()->ToI420());
    input_frame.set_video_frame_buffer(scaled_buffer);
  }

  // Encode.
  const std::vector<VideoFrameType> frame_types =
      (frame_number == 0)
          ? std::vector<VideoFrameType>(num_simulcast_or_spatial_layers_,
                                        VideoFrameType::kVideoFrameKey)
          : std::vector<VideoFrameType>(num_simulcast_or_spatial_layers_,
                                        VideoFrameType::kVideoFrameDelta);
  const int encode_return_code = encoder_->Encode(input_frame, &frame_types);
  for (size_t i = 0; i < num_simulcast_or_spatial_layers_; ++i) {
    FrameStatistics* frame_stat = stats_->GetFrame(frame_number, i);
    frame_stat->encode_return_code = encode_return_code;
  }
}

void VideoProcessor::SetRates(size_t bitrate_kbps, double framerate_fps) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!is_finalized_);

  target_rates_[last_inputed_frame_num_] =
      RateProfile({.target_kbps = bitrate_kbps, .input_fps = framerate_fps});

  auto bitrate_allocation =
      bitrate_allocator_->Allocate(VideoBitrateAllocationParameters(
          static_cast<uint32_t>(bitrate_kbps * 1000), framerate_fps));
  encoder_->SetRates(
      VideoEncoder::RateControlParameters(bitrate_allocation, framerate_fps));
}

int32_t VideoProcessor::VideoProcessorDecodeCompleteCallback::Decoded(
    VideoFrame& image) {
  // Post the callback to the right task queue, if needed.
  if (!task_queue_->IsCurrent()) {
    // There might be a limited amount of output buffers, make a copy to make
    // sure we don't block the decoder.
    VideoFrame copy = VideoFrame::Builder()
                          .set_video_frame_buffer(I420Buffer::Copy(
                              *image.video_frame_buffer()->ToI420()))
                          .set_rotation(image.rotation())
                          .set_timestamp_us(image.timestamp_us())
                          .set_id(image.id())
                          .build();
    copy.set_timestamp(image.timestamp());

    task_queue_->PostTask([this, copy]() {
      video_processor_->FrameDecoded(copy, simulcast_svc_idx_);
    });
    return 0;
  }
  video_processor_->FrameDecoded(image, simulcast_svc_idx_);
  return 0;
}

void VideoProcessor::FrameEncoded(
    const webrtc::EncodedImage& encoded_image,
    const webrtc::CodecSpecificInfo& codec_specific) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // For the highest measurement accuracy of the encode time, the start/stop
  // time recordings should wrap the Encode call as tightly as possible.
  const int64_t encode_stop_ns = rtc::TimeNanos();

  const VideoCodecType codec_type = codec_specific.codecType;
  if (config_.encoded_frame_checker) {
    config_.encoded_frame_checker->CheckEncodedFrame(codec_type, encoded_image);
  }

  // Layer metadata.
  // We could either have simulcast layers or spatial layers.
  // TODO(https://crbug.com/webrtc/14891): If we want to support a mix of
  // simulcast and SVC we'll also need to consider the case where we have both
  // simulcast and spatial indices.
  size_t stream_idx = encoded_image.SpatialIndex().value_or(
      encoded_image.SimulcastIndex().value_or(0));
  size_t temporal_idx = GetTemporalLayerIndex(codec_specific);

  FrameStatistics* frame_stat =
      stats_->GetFrameWithTimestamp(encoded_image.RtpTimestamp(), stream_idx);
  const size_t frame_number = frame_stat->frame_number;

  // Ensure that the encode order is monotonically increasing, within this
  // simulcast/spatial layer.
  RTC_CHECK(first_encoded_frame_[stream_idx] ||
            last_encoded_frame_num_[stream_idx] < frame_number);

  // Ensure SVC spatial layers are delivered in ascending order.
  const size_t num_spatial_layers = config_.NumberOfSpatialLayers();
  if (!first_encoded_frame_[stream_idx] && num_spatial_layers > 1) {
    for (size_t i = 0; i < stream_idx; ++i) {
      RTC_CHECK_LE(last_encoded_frame_num_[i], frame_number);
    }
    for (size_t i = stream_idx + 1; i < num_simulcast_or_spatial_layers_; ++i) {
      RTC_CHECK_GT(frame_number, last_encoded_frame_num_[i]);
    }
  }
  first_encoded_frame_[stream_idx] = false;
  last_encoded_frame_num_[stream_idx] = frame_number;

  RateProfile target_rate =
      std::prev(target_rates_.upper_bound(frame_number))->second;
  auto bitrate_allocation =
      bitrate_allocator_->Allocate(VideoBitrateAllocationParameters(
          static_cast<uint32_t>(target_rate.target_kbps * 1000),
          target_rate.input_fps));

  // Update frame statistics.
  frame_stat->encoding_successful = true;
  frame_stat->encode_time_us = GetElapsedTimeMicroseconds(
      frame_stat->encode_start_ns, encode_stop_ns - post_encode_time_ns_);
  frame_stat->target_bitrate_kbps =
      bitrate_allocation.GetTemporalLayerSum(stream_idx, temporal_idx) / 1000;
  frame_stat->target_framerate_fps = target_rate.input_fps;
  frame_stat->length_bytes = encoded_image.size();
  frame_stat->frame_type = encoded_image._frameType;
  frame_stat->temporal_idx = temporal_idx;
  frame_stat->max_nalu_size_bytes = GetMaxNaluSizeBytes(encoded_image, config_);
  frame_stat->qp = encoded_image.qp_;

  if (codec_type == kVideoCodecVP9) {
    const CodecSpecificInfoVP9& vp9_info = codec_specific.codecSpecific.VP9;
    frame_stat->inter_layer_predicted = vp9_info.inter_layer_predicted;
    frame_stat->non_ref_for_inter_layer_pred =
        vp9_info.non_ref_for_inter_layer_pred;
  } else {
    frame_stat->inter_layer_predicted = false;
    frame_stat->non_ref_for_inter_layer_pred = true;
  }

  const webrtc::EncodedImage* encoded_image_for_decode = &encoded_image;
  if (config_.decode || !encoded_frame_writers_->empty()) {
    if (num_spatial_layers > 1) {
      encoded_image_for_decode = BuildAndStoreSuperframe(
          encoded_image, codec_type, frame_number, stream_idx,
          frame_stat->inter_layer_predicted);
    }
  }

  if (config_.decode) {
    DecodeFrame(*encoded_image_for_decode, stream_idx);

    if (codec_specific.end_of_picture && num_spatial_layers > 1) {
      // If inter-layer prediction is enabled and upper layer was dropped then
      // base layer should be passed to upper layer decoder. Otherwise decoder
      // won't be able to decode next superframe.
      const EncodedImage* base_image = nullptr;
      const FrameStatistics* base_stat = nullptr;
      for (size_t i = 0; i < num_spatial_layers; ++i) {
        const bool layer_dropped = (first_decoded_frame_[i] ||
                                    last_decoded_frame_num_[i] < frame_number);

        // Ensure current layer was decoded.
        RTC_CHECK(layer_dropped == false || i != stream_idx);

        if (!layer_dropped) {
          base_image = &merged_encoded_frames_[i];
          base_stat =
              stats_->GetFrameWithTimestamp(encoded_image.RtpTimestamp(), i);
        } else if (base_image && !base_stat->non_ref_for_inter_layer_pred) {
          DecodeFrame(*base_image, i);
        }
      }
    }
  } else {
    frame_stat->decode_return_code = WEBRTC_VIDEO_CODEC_NO_OUTPUT;
  }

  // Since frames in higher TLs typically depend on frames in lower TLs,
  // write out frames in lower TLs to bitstream dumps of higher TLs.
  for (size_t write_temporal_idx = temporal_idx;
       write_temporal_idx < config_.NumberOfTemporalLayers();
       ++write_temporal_idx) {
    const VideoProcessor::LayerKey layer_key(stream_idx, write_temporal_idx);
    auto it = encoded_frame_writers_->find(layer_key);
    if (it != encoded_frame_writers_->cend()) {
      RTC_CHECK(it->second->WriteFrame(*encoded_image_for_decode,
                                       config_.codec_settings.codecType));
    }
  }

  if (!config_.encode_in_real_time) {
    // To get pure encode time for next layers, measure time spent in encode
    // callback and subtract it from encode time of next layers.
    post_encode_time_ns_ += rtc::TimeNanos() - encode_stop_ns;
  }
}

void VideoProcessor::CalcFrameQuality(const I420BufferInterface& decoded_frame,
                                      FrameStatistics* frame_stat) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  const auto reference_frame = input_frames_.find(frame_stat->frame_number);
  RTC_CHECK(reference_frame != input_frames_.cend())
      << "The codecs are either buffering too much, dropping too much, or "
         "being too slow relative to the input frame rate.";

  // SSIM calculation is not optimized. Skip it in real-time mode.
  const bool calc_ssim = !config_.encode_in_real_time;
  CalculateFrameQuality(*reference_frame->second.video_frame_buffer()->ToI420(),
                        decoded_frame, frame_stat, calc_ssim);

  frame_stat->quality_analysis_successful = true;
}

void VideoProcessor::WriteDecodedFrame(const I420BufferInterface& decoded_frame,
                                       FrameWriter& frame_writer) {
  int input_video_width = config_.codec_settings.width;
  int input_video_height = config_.codec_settings.height;

  rtc::scoped_refptr<I420Buffer> scaled_buffer;
  const I420BufferInterface* scaled_frame;

  if (decoded_frame.width() == input_video_width &&
      decoded_frame.height() == input_video_height) {
    scaled_frame = &decoded_frame;
  } else {
    EXPECT_DOUBLE_EQ(
        static_cast<double>(input_video_width) / input_video_height,
        static_cast<double>(decoded_frame.width()) / decoded_frame.height());

    scaled_buffer = I420Buffer::Create(input_video_width, input_video_height);
    scaled_buffer->ScaleFrom(decoded_frame);

    scaled_frame = scaled_buffer.get();
  }

  // Ensure there is no padding.
  RTC_CHECK_EQ(scaled_frame->StrideY(), input_video_width);
  RTC_CHECK_EQ(scaled_frame->StrideU(), input_video_width / 2);
  RTC_CHECK_EQ(scaled_frame->StrideV(), input_video_width / 2);

  RTC_CHECK_EQ(3 * input_video_width * input_video_height / 2,
               frame_writer.FrameLength());

  RTC_CHECK(frame_writer.WriteFrame(scaled_frame->DataY()));
}

void VideoProcessor::FrameDecoded(const VideoFrame& decoded_frame,
                                  size_t spatial_idx) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // For the highest measurement accuracy of the decode time, the start/stop
  // time recordings should wrap the Decode call as tightly as possible.
  const int64_t decode_stop_ns = rtc::TimeNanos();

  FrameStatistics* frame_stat =
      stats_->GetFrameWithTimestamp(decoded_frame.timestamp(), spatial_idx);
  const size_t frame_number = frame_stat->frame_number;

  if (!first_decoded_frame_[spatial_idx]) {
    for (size_t dropped_frame_number = last_decoded_frame_num_[spatial_idx] + 1;
         dropped_frame_number < frame_number; ++dropped_frame_number) {
      FrameStatistics* dropped_frame_stat =
          stats_->GetFrame(dropped_frame_number, spatial_idx);

      if (analyze_frame_quality_ && config_.analyze_quality_of_dropped_frames) {
        // Calculate frame quality comparing input frame with last decoded one.
        CalcFrameQuality(*last_decoded_frame_buffer_[spatial_idx],
                         dropped_frame_stat);
      }

      if (decoded_frame_writers_ != nullptr) {
        // Fill drops with last decoded frame to make them look like freeze at
        // playback and to keep decoded layers in sync.
        WriteDecodedFrame(*last_decoded_frame_buffer_[spatial_idx],
                          *decoded_frame_writers_->at(spatial_idx));
      }
    }
  }

  // Ensure that the decode order is monotonically increasing, within this
  // simulcast/spatial layer.
  RTC_CHECK(first_decoded_frame_[spatial_idx] ||
            last_decoded_frame_num_[spatial_idx] < frame_number);
  first_decoded_frame_[spatial_idx] = false;
  last_decoded_frame_num_[spatial_idx] = frame_number;

  // Update frame statistics.
  frame_stat->decoding_successful = true;
  frame_stat->decode_time_us =
      GetElapsedTimeMicroseconds(frame_stat->decode_start_ns, decode_stop_ns);
  frame_stat->decoded_width = decoded_frame.width();
  frame_stat->decoded_height = decoded_frame.height();

  // Skip quality metrics calculation to not affect CPU usage.
  if (analyze_frame_quality_ || decoded_frame_writers_) {
    // Save last decoded frame to handle possible future drops.
    rtc::scoped_refptr<I420BufferInterface> i420buffer =
        decoded_frame.video_frame_buffer()->ToI420();

    // Copy decoded frame to a buffer without padding/stride such that we can
    // dump Y, U and V planes into a file in one shot.
    last_decoded_frame_buffer_[spatial_idx] = I420Buffer::Copy(
        i420buffer->width(), i420buffer->height(), i420buffer->DataY(),
        i420buffer->StrideY(), i420buffer->DataU(), i420buffer->StrideU(),
        i420buffer->DataV(), i420buffer->StrideV());
  }

  if (analyze_frame_quality_) {
    CalcFrameQuality(*decoded_frame.video_frame_buffer()->ToI420(), frame_stat);
  }

  if (decoded_frame_writers_ != nullptr) {
    WriteDecodedFrame(*last_decoded_frame_buffer_[spatial_idx],
                      *decoded_frame_writers_->at(spatial_idx));
  }

  // Erase all buffered input frames that we have moved past for all
  // simulcast/spatial layers. Never buffer more than
  // `kMaxBufferedInputFrames` frames, to protect against long runs of
  // consecutive frame drops for a particular layer.
  const auto min_last_decoded_frame_num = std::min_element(
      last_decoded_frame_num_.cbegin(), last_decoded_frame_num_.cend());
  const size_t min_buffered_frame_num =
      std::max(0, static_cast<int>(frame_number) - kMaxBufferedInputFrames + 1);
  RTC_CHECK(min_last_decoded_frame_num != last_decoded_frame_num_.cend());
  const auto input_frames_erase_before = input_frames_.lower_bound(
      std::max(*min_last_decoded_frame_num, min_buffered_frame_num));
  input_frames_.erase(input_frames_.cbegin(), input_frames_erase_before);
}

void VideoProcessor::DecodeFrame(const EncodedImage& encoded_image,
                                 size_t spatial_idx) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  FrameStatistics* frame_stat =
      stats_->GetFrameWithTimestamp(encoded_image.RtpTimestamp(), spatial_idx);

  frame_stat->decode_start_ns = rtc::TimeNanos();
  frame_stat->decode_return_code =
      decoders_->at(spatial_idx)->Decode(encoded_image, 0);
}

const webrtc::EncodedImage* VideoProcessor::BuildAndStoreSuperframe(
    const EncodedImage& encoded_image,
    const VideoCodecType codec,
    size_t frame_number,
    size_t spatial_idx,
    bool inter_layer_predicted) {
  // Should only be called for SVC.
  RTC_CHECK_GT(config_.NumberOfSpatialLayers(), 1);

  EncodedImage base_image;
  RTC_CHECK_EQ(base_image.size(), 0);

  // Each SVC layer is decoded with dedicated decoder. Find the nearest
  // non-dropped base frame and merge it and current frame into superframe.
  if (inter_layer_predicted) {
    for (int base_idx = static_cast<int>(spatial_idx) - 1; base_idx >= 0;
         --base_idx) {
      EncodedImage lower_layer = merged_encoded_frames_.at(base_idx);
      if (lower_layer.RtpTimestamp() == encoded_image.RtpTimestamp()) {
        base_image = lower_layer;
        break;
      }
    }
  }
  const size_t payload_size_bytes = base_image.size() + encoded_image.size();

  auto buffer = EncodedImageBuffer::Create(payload_size_bytes);
  if (base_image.size()) {
    RTC_CHECK(base_image.data());
    memcpy(buffer->data(), base_image.data(), base_image.size());
  }
  memcpy(buffer->data() + base_image.size(), encoded_image.data(),
         encoded_image.size());

  EncodedImage copied_image = encoded_image;
  copied_image.SetEncodedData(buffer);
  if (base_image.size())
    copied_image._frameType = base_image._frameType;

  // Replace previous EncodedImage for this spatial layer.
  merged_encoded_frames_.at(spatial_idx) = std::move(copied_image);

  return &merged_encoded_frames_.at(spatial_idx);
}

void VideoProcessor::Finalize() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!is_finalized_);
  is_finalized_ = true;

  if (!(analyze_frame_quality_ && config_.analyze_quality_of_dropped_frames) &&
      decoded_frame_writers_ == nullptr) {
    return;
  }

  for (size_t spatial_idx = 0; spatial_idx < num_simulcast_or_spatial_layers_;
       ++spatial_idx) {
    if (first_decoded_frame_[spatial_idx]) {
      continue;  // No decoded frames on this spatial layer.
    }

    for (size_t dropped_frame_number = last_decoded_frame_num_[spatial_idx] + 1;
         dropped_frame_number < last_inputed_frame_num_;
         ++dropped_frame_number) {
      FrameStatistics* frame_stat =
          stats_->GetFrame(dropped_frame_number, spatial_idx);

      RTC_DCHECK(!frame_stat->decoding_successful);

      if (analyze_frame_quality_ && config_.analyze_quality_of_dropped_frames) {
        CalcFrameQuality(*last_decoded_frame_buffer_[spatial_idx], frame_stat);
      }

      if (decoded_frame_writers_ != nullptr) {
        WriteDecodedFrame(*last_decoded_frame_buffer_[spatial_idx],
                          *decoded_frame_writers_->at(spatial_idx));
      }
    }
  }
}

}  // namespace test
}  // namespace webrtc
