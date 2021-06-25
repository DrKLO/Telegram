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

#include "api/video/video_timing.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

VCMDecodedFrameCallback::VCMDecodedFrameCallback(VCMTiming* timing,
                                                 Clock* clock)
    : _clock(clock),
      _timing(timing),
      _timestampMap(kDecoderFrameMemoryLength),
      _extra_decode_time("t", absl::nullopt),
      low_latency_renderer_enabled_("enabled", true),
      low_latency_renderer_include_predecode_buffer_("include_predecode_buffer",
                                                     true) {
  ntp_offset_ =
      _clock->CurrentNtpInMilliseconds() - _clock->TimeInMilliseconds();

  ParseFieldTrial({&_extra_decode_time},
                  field_trial::FindFullName("WebRTC-SlowDownDecoder"));
  ParseFieldTrial({&low_latency_renderer_enabled_,
                   &low_latency_renderer_include_predecode_buffer_},
                  field_trial::FindFullName("WebRTC-LowLatencyRenderer"));
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

void VCMDecodedFrameCallback::Decoded(VideoFrame& decodedImage,
                                      absl::optional<int32_t> decode_time_ms,
                                      absl::optional<uint8_t> qp) {
  // Wait some extra time to simulate a slow decoder.
  if (_extra_decode_time) {
    rtc::Thread::SleepMs(_extra_decode_time->ms());
  }

  RTC_DCHECK(_receiveCallback) << "Callback must not be null at this point";
  TRACE_EVENT_INSTANT1("webrtc", "VCMDecodedFrameCallback::Decoded",
                       "timestamp", decodedImage.timestamp());
  // TODO(holmer): We should improve this so that we can handle multiple
  // callbacks from one call to Decode().
  absl::optional<VCMFrameInformation> frameInfo;
  int timestamp_map_size = 0;
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    int initial_timestamp_map_size = _timestampMap.Size();
    frameInfo = _timestampMap.Pop(decodedImage.timestamp());
    timestamp_map_size = _timestampMap.Size();
    // _timestampMap.Pop() erases all frame upto the specified timestamp and
    // return the frame info for this timestamp if it exists. Thus, the
    // difference in the _timestampMap size before and after Pop() will show
    // internally dropped frames.
    dropped_frames =
        initial_timestamp_map_size - timestamp_map_size - (frameInfo ? 1 : 0);
  }

  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }

  if (!frameInfo) {
    RTC_LOG(LS_WARNING) << "Too many frames backed up in the decoder, dropping "
                           "this one.";
    return;
  }

  decodedImage.set_ntp_time_ms(frameInfo->ntp_time_ms);
  decodedImage.set_packet_infos(frameInfo->packet_infos);
  decodedImage.set_rotation(frameInfo->rotation);

  if (low_latency_renderer_enabled_) {
    absl::optional<int> max_composition_delay_in_frames =
        _timing->MaxCompositionDelayInFrames();
    if (max_composition_delay_in_frames) {
      // Subtract frames that are in flight.
      if (low_latency_renderer_include_predecode_buffer_) {
        *max_composition_delay_in_frames -= timestamp_map_size;
        *max_composition_delay_in_frames =
            std::max(0, *max_composition_delay_in_frames);
      }
      decodedImage.set_max_composition_delay_in_frames(
          max_composition_delay_in_frames);
    }
  }

  RTC_DCHECK(frameInfo->decodeStart);
  const Timestamp now = _clock->CurrentTime();
  const TimeDelta decode_time = decode_time_ms
                                    ? TimeDelta::Millis(*decode_time_ms)
                                    : now - *frameInfo->decodeStart;
  _timing->StopDecodeTimer(decode_time.ms(), now.ms());
  decodedImage.set_processing_time(
      {*frameInfo->decodeStart, *frameInfo->decodeStart + decode_time});

  // Report timing information.
  TimingFrameInfo timing_frame_info;
  if (frameInfo->timing.flags != VideoSendTiming::kInvalid) {
    int64_t capture_time_ms = decodedImage.ntp_time_ms() - ntp_offset_;
    // Convert remote timestamps to local time from ntp timestamps.
    frameInfo->timing.encode_start_ms -= ntp_offset_;
    frameInfo->timing.encode_finish_ms -= ntp_offset_;
    frameInfo->timing.packetization_finish_ms -= ntp_offset_;
    frameInfo->timing.pacer_exit_ms -= ntp_offset_;
    frameInfo->timing.network_timestamp_ms -= ntp_offset_;
    frameInfo->timing.network2_timestamp_ms -= ntp_offset_;

    int64_t sender_delta_ms = 0;
    if (decodedImage.ntp_time_ms() < 0) {
      // Sender clock is not estimated yet. Make sure that sender times are all
      // negative to indicate that. Yet they still should be relatively correct.
      sender_delta_ms =
          std::max({capture_time_ms, frameInfo->timing.encode_start_ms,
                    frameInfo->timing.encode_finish_ms,
                    frameInfo->timing.packetization_finish_ms,
                    frameInfo->timing.pacer_exit_ms,
                    frameInfo->timing.network_timestamp_ms,
                    frameInfo->timing.network2_timestamp_ms}) +
          1;
    }

    timing_frame_info.capture_time_ms = capture_time_ms - sender_delta_ms;
    timing_frame_info.encode_start_ms =
        frameInfo->timing.encode_start_ms - sender_delta_ms;
    timing_frame_info.encode_finish_ms =
        frameInfo->timing.encode_finish_ms - sender_delta_ms;
    timing_frame_info.packetization_finish_ms =
        frameInfo->timing.packetization_finish_ms - sender_delta_ms;
    timing_frame_info.pacer_exit_ms =
        frameInfo->timing.pacer_exit_ms - sender_delta_ms;
    timing_frame_info.network_timestamp_ms =
        frameInfo->timing.network_timestamp_ms - sender_delta_ms;
    timing_frame_info.network2_timestamp_ms =
        frameInfo->timing.network2_timestamp_ms - sender_delta_ms;
  }

  timing_frame_info.flags = frameInfo->timing.flags;
  timing_frame_info.decode_start_ms = frameInfo->decodeStart->ms();
  timing_frame_info.decode_finish_ms = now.ms();
  timing_frame_info.render_time_ms = frameInfo->renderTimeMs;
  timing_frame_info.rtp_timestamp = decodedImage.timestamp();
  timing_frame_info.receive_start_ms = frameInfo->timing.receive_start_ms;
  timing_frame_info.receive_finish_ms = frameInfo->timing.receive_finish_ms;
  _timing->SetTimingFrameInfo(timing_frame_info);

  decodedImage.set_timestamp_us(frameInfo->renderTimeMs *
                                rtc::kNumMicrosecsPerMillisec);
  _receiveCallback->FrameToRender(decodedImage, qp, decode_time.ms(),
                                  frameInfo->content_type);
}

void VCMDecodedFrameCallback::OnDecoderImplementationName(
    const char* implementation_name) {
  _receiveCallback->OnDecoderImplementationName(implementation_name);
}

void VCMDecodedFrameCallback::Map(uint32_t timestamp,
                                  const VCMFrameInformation& frameInfo) {
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    int initial_size = _timestampMap.Size();
    _timestampMap.Add(timestamp, frameInfo);
    // If no frame is dropped, the new size should be |initial_size| + 1
    dropped_frames = (initial_size + 1) - _timestampMap.Size();
  }
  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }
}

void VCMDecodedFrameCallback::ClearTimestampMap() {
  int dropped_frames = 0;
  {
    MutexLock lock(&lock_);
    dropped_frames = _timestampMap.Size();
    _timestampMap.Clear();
  }
  if (dropped_frames > 0) {
    _receiveCallback->OnDroppedFrames(dropped_frames);
  }
}

VCMGenericDecoder::VCMGenericDecoder(std::unique_ptr<VideoDecoder> decoder)
    : VCMGenericDecoder(decoder.release(), false /* isExternal */) {}

VCMGenericDecoder::VCMGenericDecoder(VideoDecoder* decoder, bool isExternal)
    : _callback(NULL),
      decoder_(decoder),
      _codecType(kVideoCodecGeneric),
      _isExternal(isExternal),
      _last_keyframe_content_type(VideoContentType::UNSPECIFIED) {
  RTC_DCHECK(decoder_);
}

VCMGenericDecoder::~VCMGenericDecoder() {
  decoder_->Release();
  if (_isExternal)
    decoder_.release();
  RTC_DCHECK(_isExternal || decoder_);
}

int32_t VCMGenericDecoder::InitDecode(const VideoCodec* settings,
                                      int32_t numberOfCores) {
  TRACE_EVENT0("webrtc", "VCMGenericDecoder::InitDecode");
  _codecType = settings->codecType;

  int err = decoder_->InitDecode(settings, numberOfCores);
  decoder_info_ = decoder_->GetDecoderInfo();
  RTC_LOG(LS_INFO) << "Decoder implementation: " << decoder_info_.ToString();
  if (_callback) {
    _callback->OnDecoderImplementationName(
        decoder_info_.implementation_name.c_str());
  }
  return err;
}

int32_t VCMGenericDecoder::Decode(const VCMEncodedFrame& frame, Timestamp now) {
  TRACE_EVENT1("webrtc", "VCMGenericDecoder::Decode", "timestamp",
               frame.Timestamp());
  VCMFrameInformation frame_info;
  frame_info.decodeStart = now;
  frame_info.renderTimeMs = frame.RenderTimeMs();
  frame_info.rotation = frame.rotation();
  frame_info.timing = frame.video_timing();
  frame_info.ntp_time_ms = frame.EncodedImage().ntp_time_ms_;
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
  _callback->Map(frame.Timestamp(), frame_info);

  int32_t ret = decoder_->Decode(frame.EncodedImage(), frame.MissingFrame(),
                                 frame.RenderTimeMs());
  VideoDecoder::DecoderInfo decoder_info = decoder_->GetDecoderInfo();
  if (decoder_info != decoder_info_) {
    RTC_LOG(LS_INFO) << "Changed decoder implementation to: "
                     << decoder_info.ToString();
    decoder_info_ = decoder_info;
    _callback->OnDecoderImplementationName(
        decoder_info.implementation_name.empty()
            ? "unknown"
            : decoder_info.implementation_name.c_str());
  }
  if (ret < WEBRTC_VIDEO_CODEC_OK) {
    RTC_LOG(LS_WARNING) << "Failed to decode frame with timestamp "
                        << frame.Timestamp() << ", error code: " << ret;
    _callback->ClearTimestampMap();
  } else if (ret == WEBRTC_VIDEO_CODEC_NO_OUTPUT) {
    // No output.
    _callback->ClearTimestampMap();
  }
  return ret;
}

int32_t VCMGenericDecoder::RegisterDecodeCompleteCallback(
    VCMDecodedFrameCallback* callback) {
  _callback = callback;
  int32_t ret = decoder_->RegisterDecodeCompleteCallback(callback);
  if (callback && !decoder_info_.implementation_name.empty()) {
    callback->OnDecoderImplementationName(
        decoder_info_.implementation_name.c_str());
  }
  return ret;
}

}  // namespace webrtc
