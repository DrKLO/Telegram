/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/generic_decoder.h"

#include <stddef.h>

#include <algorithm>
#include <cmath>
#include <iterator>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/types/optional.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/include/module_common_types_public.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/metrics.h"

namespace webrtc {

namespace {

constexpr size_t kDecoderFrameMemoryLength = 10;

}

VCMDecodedFrameCallback::VCMDecodedFrameCallback(
    VCMTiming* timing,
    Clock* clock,
    const FieldTrialsView& field_trials)
    : _clock(clock), _timing(timing) {
  ntp_offset_ =
      _clock->CurrentNtpInMilliseconds() - _clock->TimeInMilliseconds();
}

VCMDecodedFrameCallback::~VCMDecodedFrameCallback() {}

void VCMDecodedFrameCallback::SetUserReceiveCallback(
    VCMReceiveCallback* receiveCallback) {
  RTC_DCHECK(construction_thread_.IsCurrent());
  RTC_DCHECK((!_receiveCallback && receiveCallback) ||
             (_receiveCallback && !receiveCallback));
  _receiveCallback = receiveCallback;
}

VCMReceiveCallback* VCMDecodedFrameCallback::UserReceiveCallback() {
  // Called on the decode thread via VCMCodecDataBase::GetDecoder.
  // The callback must always have been set before this happens.
  RTC_DCHECK(_receiveCallback);
  return _receiveCallback;
}

int32_t VCMDecodedFrameCallback::Decoded(VideoFrame& decodedImage) {
  // This function may be called on the decode TaskQueue, but may also be called
  // on an OS provided queue such as on iOS (see e.g. b/153465112).
  return Decoded(decodedImage, -1);
}

int32_t VCMDecodedFrameCallback::Decoded(VideoFrame& decodedImage,
                                         int64_t decode_time_ms) {
  Decoded(decodedImage,
          decode_time_ms >= 0 ? absl::optional<int32_t>(decode_time_ms)
                              : absl::nullopt,
          absl::nullopt);
  return WEBRTC_VIDEO_CODEC_OK;
}

std::pair<absl::optional<FrameInfo>, size_t>
VCMDecodedFrameCallback::FindFrameInfo(uint32_t rtp_timestamp) {
  absl::optional<FrameInfo> frame_info;

  auto it = absl::c_find_if(frame_infos_, [rtp_timestamp](const auto& entry) {
    return entry.rtp_timestamp == rtp_timestamp ||
           IsNewerTimestamp(entry.rtp_timestamp, rtp_timestamp);
  });
  size_t dropped_frames = std::distance(frame_infos_.begin(), it);

  if (it != frame_infos_.end() && it->rtp_timestamp == rtp_timestamp) {
    // Frame was found and should also be removed from the queue.
    frame_info = std::move(*it);
    ++it;
  }

  frame_infos_.erase(frame_infos_.begin(), it);
  return std::make_pair(std::move(frame_info), dropped_frames);
}

void VCMDecodedFrameCallback::Decoded(VideoFrame& decodedImage,
                                      absl::optional<int32_t> decode_time_ms,
                                      absl::optional<uint8_t> qp) {
  RTC_DCHECK(_receiveCallback) << "Callback must not be null at this point";
  TRACE_EVENT_INSTANT1("webrtc", "VCMDecodedFrameCallback::Decoded",
                       "timestamp", decodedImage.timestamp());
  // TODO(holmer): We should improve this so that we can handle multiple
  // callbacks from one call to Decode().
  absl::optional<FrameInfo> frame_info;
  int timestamp_map_size = 0;
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    std::tie(frame_info, dropped_frames) =
        FindFrameInfo(decodedImage.timestamp());
    timestamp_map_size = frame_infos_.size();
  }
  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }

  if (!frame_info) {
    RTC_LOG(LS_WARNING) << "Too many frames backed up in the decoder, dropping "
                           "frame with timestamp "
                        << decodedImage.timestamp();
    return;
  }

  decodedImage.set_ntp_time_ms(frame_info->ntp_time_ms);
  decodedImage.set_packet_infos(frame_info->packet_infos);
  decodedImage.set_rotation(frame_info->rotation);
  VideoFrame::RenderParameters render_parameters = _timing->RenderParameters();
  if (render_parameters.max_composition_delay_in_frames) {
    // Subtract frames that are in flight.
    render_parameters.max_composition_delay_in_frames =
        std::max(0, *render_parameters.max_composition_delay_in_frames -
                        timestamp_map_size);
  }
  decodedImage.set_render_parameters(render_parameters);

  RTC_DCHECK(frame_info->decode_start);
  const Timestamp now = _clock->CurrentTime();
  const TimeDelta decode_time = decode_time_ms
                                    ? TimeDelta::Millis(*decode_time_ms)
                                    : now - *frame_info->decode_start;
  _timing->StopDecodeTimer(decode_time, now);
  decodedImage.set_processing_time(
      {*frame_info->decode_start, *frame_info->decode_start + decode_time});

  // Report timing information.
  TimingFrameInfo timing_frame_info;
  if (frame_info->timing.flags != VideoSendTiming::kInvalid) {
    int64_t capture_time_ms = decodedImage.ntp_time_ms() - ntp_offset_;
    // Convert remote timestamps to local time from ntp timestamps.
    frame_info->timing.encode_start_ms -= ntp_offset_;
    frame_info->timing.encode_finish_ms -= ntp_offset_;
    frame_info->timing.packetization_finish_ms -= ntp_offset_;
    frame_info->timing.pacer_exit_ms -= ntp_offset_;
    frame_info->timing.network_timestamp_ms -= ntp_offset_;
    frame_info->timing.network2_timestamp_ms -= ntp_offset_;

    int64_t sender_delta_ms = 0;
    if (decodedImage.ntp_time_ms() < 0) {
      // Sender clock is not estimated yet. Make sure that sender times are all
      // negative to indicate that. Yet they still should be relatively correct.
      sender_delta_ms =
          std::max({capture_time_ms, frame_info->timing.encode_start_ms,
                    frame_info->timing.encode_finish_ms,
                    frame_info->timing.packetization_finish_ms,
                    frame_info->timing.pacer_exit_ms,
                    frame_info->timing.network_timestamp_ms,
                    frame_info->timing.network2_timestamp_ms}) +
          1;
    }

    timing_frame_info.capture_time_ms = capture_time_ms - sender_delta_ms;
    timing_frame_info.encode_start_ms =
        frame_info->timing.encode_start_ms - sender_delta_ms;
    timing_frame_info.encode_finish_ms =
        frame_info->timing.encode_finish_ms - sender_delta_ms;
    timing_frame_info.packetization_finish_ms =
        frame_info->timing.packetization_finish_ms - sender_delta_ms;
    timing_frame_info.pacer_exit_ms =
        frame_info->timing.pacer_exit_ms - sender_delta_ms;
    timing_frame_info.network_timestamp_ms =
        frame_info->timing.network_timestamp_ms - sender_delta_ms;
    timing_frame_info.network2_timestamp_ms =
        frame_info->timing.network2_timestamp_ms - sender_delta_ms;
    RTC_HISTOGRAM_COUNTS_1000(
        "WebRTC.Video.GenericDecoder.CaptureToEncodeDelay",
        timing_frame_info.encode_start_ms - timing_frame_info.capture_time_ms);
    RTC_HISTOGRAM_COUNTS_1000(
        "WebRTC.Video.GenericDecoder.EncodeDelay",
        timing_frame_info.encode_finish_ms - timing_frame_info.encode_start_ms);
    RTC_HISTOGRAM_COUNTS_1000(
        "WebRTC.Video.GenericDecoder.PacerAndPacketizationDelay",
        timing_frame_info.pacer_exit_ms - timing_frame_info.encode_finish_ms);
  }

  timing_frame_info.flags = frame_info->timing.flags;
  timing_frame_info.decode_start_ms = frame_info->decode_start->ms();
  timing_frame_info.decode_finish_ms = now.ms();
  timing_frame_info.render_time_ms =
      frame_info->render_time ? frame_info->render_time->ms() : -1;
  timing_frame_info.rtp_timestamp = decodedImage.timestamp();
  timing_frame_info.receive_start_ms = frame_info->timing.receive_start_ms;
  timing_frame_info.receive_finish_ms = frame_info->timing.receive_finish_ms;
  RTC_HISTOGRAM_COUNTS_1000(
      "WebRTC.Video.GenericDecoder.PacketReceiveDelay",
      timing_frame_info.receive_finish_ms - timing_frame_info.receive_start_ms);
  RTC_HISTOGRAM_COUNTS_1000(
      "WebRTC.Video.GenericDecoder.JitterBufferDelay",
      timing_frame_info.decode_start_ms - timing_frame_info.receive_finish_ms);
  RTC_HISTOGRAM_COUNTS_1000(
      "WebRTC.Video.GenericDecoder.DecodeDelay",
      timing_frame_info.decode_finish_ms - timing_frame_info.decode_start_ms);
  _timing->SetTimingFrameInfo(timing_frame_info);

  decodedImage.set_timestamp_us(
      frame_info->render_time ? frame_info->render_time->us() : -1);
  _receiveCallback->FrameToRender(decodedImage, qp, decode_time,
                                  frame_info->content_type,
                                  frame_info->frame_type);
}

void VCMDecodedFrameCallback::OnDecoderInfoChanged(
    const VideoDecoder::DecoderInfo& decoder_info) {
  _receiveCallback->OnDecoderInfoChanged(decoder_info);
}

void VCMDecodedFrameCallback::Map(FrameInfo frameInfo) {
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    int initial_size = frame_infos_.size();
    if (initial_size == kDecoderFrameMemoryLength) {
      frame_infos_.pop_front();
      dropped_frames = 1;
    }
    frame_infos_.push_back(std::move(frameInfo));
    // If no frame is dropped, the new size should be `initial_size` + 1
  }
  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }
}

void VCMDecodedFrameCallback::ClearTimestampMap() {
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    dropped_frames = frame_infos_.size();
    frame_infos_.clear();
  }
  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }
}

VCMGenericDecoder::VCMGenericDecoder(VideoDecoder* decoder)
    : _callback(NULL),
      decoder_(decoder),
      _last_keyframe_content_type(VideoContentType::UNSPECIFIED) {
  RTC_DCHECK(decoder_);
}

VCMGenericDecoder::~VCMGenericDecoder() {
  decoder_->Release();
}

bool VCMGenericDecoder::Configure(const VideoDecoder::Settings& settings) {
  TRACE_EVENT0("webrtc", "VCMGenericDecoder::Configure");

  bool ok = decoder_->Configure(settings);
  decoder_info_ = decoder_->GetDecoderInfo();
  RTC_LOG(LS_INFO) << "Decoder implementation: " << decoder_info_.ToString();
  if (_callback) {
    _callback->OnDecoderInfoChanged(decoder_info_);
  }
  return ok;
}

int32_t VCMGenericDecoder::Decode(const EncodedFrame& frame, Timestamp now) {
  return Decode(frame, now, frame.RenderTimeMs());
}

int32_t VCMGenericDecoder::Decode(const VCMEncodedFrame& frame, Timestamp now) {
  return Decode(frame, now, frame.RenderTimeMs());
}

int32_t VCMGenericDecoder::Decode(const EncodedImage& frame,
                                  Timestamp now,
                                  int64_t render_time_ms) {
  TRACE_EVENT1("webrtc", "VCMGenericDecoder::Decode", "timestamp",
               frame.RtpTimestamp());
  FrameInfo frame_info;
  frame_info.rtp_timestamp = frame.RtpTimestamp();
  frame_info.decode_start = now;
  frame_info.render_time =
      render_time_ms >= 0
          ? absl::make_optional(Timestamp::Millis(render_time_ms))
          : absl::nullopt;
  frame_info.rotation = frame.rotation();
  frame_info.timing = frame.video_timing();
  frame_info.ntp_time_ms = frame.ntp_time_ms_;
  frame_info.packet_infos = frame.PacketInfos();

  // Set correctly only for key frames. Thus, use latest key frame
  // content type. If the corresponding key frame was lost, decode will fail
  // and content type will be ignored.
  if (frame.FrameType() == VideoFrameType::kVideoFrameKey) {
    frame_info.content_type = frame.contentType();
    _last_keyframe_content_type = frame.contentType();
  } else {
    frame_info.content_type = _last_keyframe_content_type;
  }
  frame_info.frame_type = frame.FrameType();
  _callback->Map(std::move(frame_info));

  int32_t ret = decoder_->Decode(frame, render_time_ms);
  VideoDecoder::DecoderInfo decoder_info = decoder_->GetDecoderInfo();
  if (decoder_info != decoder_info_) {
    RTC_LOG(LS_INFO) << "Changed decoder implementation to: "
                     << decoder_info.ToString();
    decoder_info_ = decoder_info;
    if (decoder_info.implementation_name.empty()) {
      decoder_info.implementation_name = "unknown";
    }
    _callback->OnDecoderInfoChanged(std::move(decoder_info));
  }
  if (ret < WEBRTC_VIDEO_CODEC_OK || ret == WEBRTC_VIDEO_CODEC_NO_OUTPUT) {
    _callback->ClearTimestampMap();
  }
  return ret;
}

int32_t VCMGenericDecoder::RegisterDecodeCompleteCallback(
    VCMDecodedFrameCallback* callback) {
  _callback = callback;
  int32_t ret = decoder_->RegisterDecodeCompleteCallback(callback);
  if (callback && !decoder_info_.implementation_name.empty()) {
    callback->OnDecoderInfoChanged(decoder_info_);
  }
  return ret;
}

}  // namespace webrtc
