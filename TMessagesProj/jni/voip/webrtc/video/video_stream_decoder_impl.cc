/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_stream_decoder_impl.h"

#include <memory>

#include "rtc_base/logging.h"
#include "rtc_base/numerics/mod_ops.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

VideoStreamDecoderImpl::VideoStreamDecoderImpl(
    VideoStreamDecoderInterface::Callbacks* callbacks,
    VideoDecoderFactory* decoder_factory,
    TaskQueueFactory* task_queue_factory,
    std::map<int, std::pair<SdpVideoFormat, int>> decoder_settings,
    const FieldTrialsView* field_trials)
    : field_trials_(field_trials),
      timing_(Clock::GetRealTimeClock(), *field_trials_),
      decode_callbacks_(this),
      next_frame_info_index_(0),
      callbacks_(callbacks),
      keyframe_required_(true),
      decoder_factory_(decoder_factory),
      decoder_settings_(std::move(decoder_settings)),
      shut_down_(false),
      frame_buffer_(Clock::GetRealTimeClock(), &timing_, *field_trials_),
      bookkeeping_queue_(task_queue_factory->CreateTaskQueue(
          "video_stream_decoder_bookkeeping_queue",
          TaskQueueFactory::Priority::NORMAL)),
      decode_queue_(task_queue_factory->CreateTaskQueue(
          "video_stream_decoder_decode_queue",
          TaskQueueFactory::Priority::NORMAL)) {
  bookkeeping_queue_.PostTask([this]() {
    RTC_DCHECK_RUN_ON(&bookkeeping_queue_);
    StartNextDecode();
  });
}

VideoStreamDecoderImpl::~VideoStreamDecoderImpl() {
  MutexLock lock(&shut_down_mutex_);
  shut_down_ = true;
}

void VideoStreamDecoderImpl::OnFrame(std::unique_ptr<EncodedFrame> frame) {
  if (!bookkeeping_queue_.IsCurrent()) {
    bookkeeping_queue_.PostTask([this, frame = std::move(frame)]() mutable {
      OnFrame(std::move(frame));
      return true;
    });

    return;
  }

  RTC_DCHECK_RUN_ON(&bookkeeping_queue_);

  int64_t continuous_frame_id = frame_buffer_.InsertFrame(std::move(frame));
  if (last_continuous_frame_id_ < continuous_frame_id) {
    last_continuous_frame_id_ = continuous_frame_id;
    callbacks_->OnContinuousUntil(last_continuous_frame_id_);
  }
}

void VideoStreamDecoderImpl::SetMinPlayoutDelay(TimeDelta min_delay) {
  timing_.set_min_playout_delay(min_delay);
}

void VideoStreamDecoderImpl::SetMaxPlayoutDelay(TimeDelta max_delay) {
  timing_.set_max_playout_delay(max_delay);
}

VideoDecoder* VideoStreamDecoderImpl::GetDecoder(int payload_type) {
  if (current_payload_type_ == payload_type) {
    RTC_DCHECK(decoder_);
    return decoder_.get();
  }

  current_payload_type_.reset();
  decoder_.reset();

  auto decoder_settings_it = decoder_settings_.find(payload_type);
  if (decoder_settings_it == decoder_settings_.end()) {
    RTC_LOG(LS_WARNING) << "Payload type " << payload_type
                        << " not registered.";
    return nullptr;
  }

  const SdpVideoFormat& video_format = decoder_settings_it->second.first;
  std::unique_ptr<VideoDecoder> decoder =
      decoder_factory_->CreateVideoDecoder(video_format);
  if (!decoder) {
    RTC_LOG(LS_WARNING) << "Failed to create decoder for payload type "
                        << payload_type << ".";
    return nullptr;
  }

  VideoDecoder::Settings settings;
  settings.set_number_of_cores(decoder_settings_it->second.second);
  if (!decoder->Configure(settings)) {
    RTC_LOG(LS_WARNING) << "Failed to initialize decoder for payload type "
                        << payload_type << ".";
    return nullptr;
  }

  int32_t register_result =
      decoder->RegisterDecodeCompleteCallback(&decode_callbacks_);
  if (register_result != WEBRTC_VIDEO_CODEC_OK) {
    RTC_LOG(LS_WARNING) << "Failed to register decode callback.";
    return nullptr;
  }

  current_payload_type_.emplace(payload_type);
  decoder_ = std::move(decoder);
  return decoder_.get();
}

void VideoStreamDecoderImpl::SaveFrameInfo(const EncodedFrame& frame) {
  FrameInfo* frame_info = &frame_info_[next_frame_info_index_];
  frame_info->timestamp = frame.Timestamp();
  frame_info->decode_start_time_ms = rtc::TimeMillis();
  frame_info->render_time_us = frame.RenderTimeMs() * 1000;
  frame_info->content_type = frame.EncodedImage().content_type_;

  next_frame_info_index_ = Add<kFrameInfoMemory>(next_frame_info_index_, 1);
}

void VideoStreamDecoderImpl::StartNextDecode() {
  int64_t max_wait_time = keyframe_required_ ? 200 : 3000;

  frame_buffer_.NextFrame(max_wait_time, keyframe_required_,
                          bookkeeping_queue_.Get(),
                          [this](std::unique_ptr<EncodedFrame> frame) {
                            RTC_DCHECK_RUN_ON(&bookkeeping_queue_);
                            OnNextFrameCallback(std::move(frame));
                          });
}

void VideoStreamDecoderImpl::OnNextFrameCallback(
    std::unique_ptr<EncodedFrame> frame) {
  if (frame) {
    RTC_DCHECK(frame);
    SaveFrameInfo(*frame);

    MutexLock lock(&shut_down_mutex_);
    if (shut_down_) {
      return;
    }

    decode_queue_.PostTask([this, frame = std::move(frame)]() mutable {
      RTC_DCHECK_RUN_ON(&decode_queue_);
      DecodeResult decode_result = DecodeFrame(std::move(frame));
      bookkeeping_queue_.PostTask([this, decode_result]() {
        RTC_DCHECK_RUN_ON(&bookkeeping_queue_);
        switch (decode_result) {
          case kOk: {
            keyframe_required_ = false;
            break;
          }
          case kOkRequestKeyframe: {
            callbacks_->OnNonDecodableState();
            keyframe_required_ = false;
            break;
          }
          case kDecodeFailure: {
            callbacks_->OnNonDecodableState();
            keyframe_required_ = true;
            break;
          }
        }
        StartNextDecode();
      });
    });
  } else {
    callbacks_->OnNonDecodableState();
    // The `frame_buffer_` requires the frame callback function to complete
    // before NextFrame is called again. For this reason we call
    // StartNextDecode in a later task to allow this task to complete first.
    bookkeeping_queue_.PostTask([this]() {
      RTC_DCHECK_RUN_ON(&bookkeeping_queue_);
      StartNextDecode();
    });
  }
}

VideoStreamDecoderImpl::DecodeResult VideoStreamDecoderImpl::DecodeFrame(
    std::unique_ptr<EncodedFrame> frame) {
  RTC_DCHECK(frame);

  VideoDecoder* decoder = GetDecoder(frame->PayloadType());
  if (!decoder) {
    return kDecodeFailure;
  }

  int32_t decode_result = decoder->Decode(frame->EncodedImage(),     //
                                          /*missing_frames=*/false,  //
                                          frame->RenderTimeMs());
  switch (decode_result) {
    case WEBRTC_VIDEO_CODEC_OK: {
      return kOk;
    }
    case WEBRTC_VIDEO_CODEC_OK_REQUEST_KEYFRAME: {
      return kOkRequestKeyframe;
    }
    default:
      return kDecodeFailure;
  }
}

VideoStreamDecoderImpl::FrameInfo* VideoStreamDecoderImpl::GetFrameInfo(
    int64_t timestamp) {
  int start_time_index = next_frame_info_index_;
  for (int i = 0; i < kFrameInfoMemory; ++i) {
    start_time_index = Subtract<kFrameInfoMemory>(start_time_index, 1);

    if (frame_info_[start_time_index].timestamp == timestamp)
      return &frame_info_[start_time_index];
  }

  return nullptr;
}

void VideoStreamDecoderImpl::OnDecodedFrameCallback(
    VideoFrame& decoded_image,
    absl::optional<int32_t> decode_time_ms,
    absl::optional<uint8_t> qp) {
  int64_t decode_stop_time_ms = rtc::TimeMillis();

  bookkeeping_queue_.PostTask([this, decode_stop_time_ms, decoded_image,
                               decode_time_ms, qp]() mutable {
    RTC_DCHECK_RUN_ON(&bookkeeping_queue_);

    FrameInfo* frame_info = GetFrameInfo(decoded_image.timestamp());
    if (!frame_info) {
      RTC_LOG(LS_ERROR) << "No frame information found for frame with timestamp"
                        << decoded_image.timestamp();
      return;
    }

    Callbacks::FrameInfo callback_info;
    callback_info.content_type = frame_info->content_type;

    if (qp)
      callback_info.qp.emplace(*qp);

    if (!decode_time_ms) {
      decode_time_ms = decode_stop_time_ms - frame_info->decode_start_time_ms;
    }
    decoded_image.set_processing_time(
        {Timestamp::Millis(frame_info->decode_start_time_ms),
         Timestamp::Millis(frame_info->decode_start_time_ms +
                           *decode_time_ms)});
    decoded_image.set_timestamp_us(frame_info->render_time_us);
    timing_.StopDecodeTimer(TimeDelta::Millis(*decode_time_ms),
                            Timestamp::Millis(decode_stop_time_ms));

    callbacks_->OnDecodedFrame(decoded_image, callback_info);
  });
}

VideoStreamDecoderImpl::DecodeCallbacks::DecodeCallbacks(
    VideoStreamDecoderImpl* video_stream_decoder_impl)
    : video_stream_decoder_impl_(video_stream_decoder_impl) {}

int32_t VideoStreamDecoderImpl::DecodeCallbacks::Decoded(
    VideoFrame& decoded_image) {
  Decoded(decoded_image, absl::nullopt, absl::nullopt);
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t VideoStreamDecoderImpl::DecodeCallbacks::Decoded(
    VideoFrame& decoded_image,
    int64_t decode_time_ms) {
  Decoded(decoded_image, decode_time_ms, absl::nullopt);
  return WEBRTC_VIDEO_CODEC_OK;
}

void VideoStreamDecoderImpl::DecodeCallbacks::Decoded(
    VideoFrame& decoded_image,
    absl::optional<int32_t> decode_time_ms,
    absl::optional<uint8_t> qp) {
  video_stream_decoder_impl_->OnDecodedFrameCallback(decoded_image,
                                                     decode_time_ms, qp);
}
}  // namespace webrtc
