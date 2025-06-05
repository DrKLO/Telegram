/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_encoder_wrapper.h"

#include <utility>

#include "common_video/h264/h264_common.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "modules/video_coding/svc/scalable_video_controller_no_layering.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "sdk/android/generated_video_jni/VideoEncoderWrapper_jni.h"
#include "sdk/android/generated_video_jni/VideoEncoder_jni.h"
#include "sdk/android/native_api/jni/class_loader.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/encoded_image.h"
#include "sdk/android/src/jni/video_codec_status.h"
#include "sdk/android/src/jni/video_frame.h"

namespace webrtc {
namespace jni {

VideoEncoderWrapper::VideoEncoderWrapper(JNIEnv* jni,
                                         const JavaRef<jobject>& j_encoder)
    : encoder_(jni, j_encoder), int_array_class_(GetClass(jni, "[I")) {
  initialized_ = false;
  num_resets_ = 0;

  // Fetch and update encoder info.
  UpdateEncoderInfo(jni);
}
VideoEncoderWrapper::~VideoEncoderWrapper() = default;

int VideoEncoderWrapper::InitEncode(const VideoCodec* codec_settings,
                                    const Settings& settings) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();

  codec_settings_ = *codec_settings;
  capabilities_ = settings.capabilities;
  number_of_cores_ = settings.number_of_cores;
  num_resets_ = 0;

  return InitEncodeInternal(jni);
}

int32_t VideoEncoderWrapper::InitEncodeInternal(JNIEnv* jni) {
  bool automatic_resize_on;
  switch (codec_settings_.codecType) {
    case kVideoCodecVP8:
      automatic_resize_on = codec_settings_.VP8()->automaticResizeOn;
      break;
    case kVideoCodecVP9:
      automatic_resize_on = codec_settings_.VP9()->automaticResizeOn;
      gof_.SetGofInfoVP9(TemporalStructureMode::kTemporalStructureMode1);
      gof_idx_ = 0;
      break;
    default:
      automatic_resize_on = true;
  }

  RTC_DCHECK(capabilities_);
  ScopedJavaLocalRef<jobject> capabilities =
      Java_Capabilities_Constructor(jni, capabilities_->loss_notification);

  ScopedJavaLocalRef<jobject> settings = Java_Settings_Constructor(
      jni, number_of_cores_, codec_settings_.width, codec_settings_.height,
      static_cast<int>(codec_settings_.startBitrate),
      static_cast<int>(codec_settings_.maxFramerate),
      static_cast<int>(codec_settings_.numberOfSimulcastStreams),
      automatic_resize_on, capabilities);

  ScopedJavaLocalRef<jobject> callback =
      Java_VideoEncoderWrapper_createEncoderCallback(jni,
                                                     jlongFromPointer(this));

  int32_t status = JavaToNativeVideoCodecStatus(
      jni, Java_VideoEncoder_initEncode(jni, encoder_, settings, callback));
  RTC_LOG(LS_INFO) << "initEncode: " << status;

  // Some encoder's properties depend on settings and may change after
  // initialization.
  UpdateEncoderInfo(jni);

  if (status == WEBRTC_VIDEO_CODEC_OK) {
    initialized_ = true;
  }
  return status;
}

void VideoEncoderWrapper::UpdateEncoderInfo(JNIEnv* jni) {
  encoder_info_.supports_native_handle = true;

  encoder_info_.implementation_name = JavaToStdString(
      jni, Java_VideoEncoder_getImplementationName(jni, encoder_));

  encoder_info_.is_hardware_accelerated =
      Java_VideoEncoder_isHardwareEncoder(jni, encoder_);

  encoder_info_.scaling_settings = GetScalingSettingsInternal(jni);

  encoder_info_.resolution_bitrate_limits = JavaToNativeResolutionBitrateLimits(
      jni, Java_VideoEncoder_getResolutionBitrateLimits(jni, encoder_));

  EncoderInfo info = GetEncoderInfoInternal(jni);
  encoder_info_.requested_resolution_alignment =
      info.requested_resolution_alignment;
  encoder_info_.apply_alignment_to_all_simulcast_layers =
      info.apply_alignment_to_all_simulcast_layers;
}

int32_t VideoEncoderWrapper::RegisterEncodeCompleteCallback(
    EncodedImageCallback* callback) {
  callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t VideoEncoderWrapper::Release() {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();

  int32_t status = JavaToNativeVideoCodecStatus(
      jni, Java_VideoEncoder_release(jni, encoder_));
  RTC_LOG(LS_INFO) << "release: " << status;
  {
    MutexLock lock(&frame_extra_infos_lock_);
    frame_extra_infos_.clear();
  }
  initialized_ = false;

  return status;
}

int32_t VideoEncoderWrapper::Encode(
    const VideoFrame& frame,
    const std::vector<VideoFrameType>* frame_types) {
  if (!initialized_) {
    // Most likely initializing the codec failed.
    return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
  }

  JNIEnv* jni = AttachCurrentThreadIfNeeded();

  // Construct encode info.
  ScopedJavaLocalRef<jobjectArray> j_frame_types;
  if (frame_types != nullptr) {
    j_frame_types = NativeToJavaFrameTypeArray(jni, *frame_types);
  } else {
    j_frame_types =
        NativeToJavaFrameTypeArray(jni, {VideoFrameType::kVideoFrameDelta});
  }
  ScopedJavaLocalRef<jobject> encode_info =
      Java_EncodeInfo_Constructor(jni, j_frame_types);

  FrameExtraInfo info;
  info.capture_time_ns = frame.timestamp_us() * rtc::kNumNanosecsPerMicrosec;
  info.timestamp_rtp = frame.timestamp();
  {
    MutexLock lock(&frame_extra_infos_lock_);
    frame_extra_infos_.push_back(info);
  }

  ScopedJavaLocalRef<jobject> j_frame = NativeToJavaVideoFrame(jni, frame);
  ScopedJavaLocalRef<jobject> ret =
      Java_VideoEncoder_encode(jni, encoder_, j_frame, encode_info);
  ReleaseJavaVideoFrame(jni, j_frame);
  return HandleReturnCode(jni, ret, "encode");
}

void VideoEncoderWrapper::SetRates(const RateControlParameters& rc_parameters) {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();

  ScopedJavaLocalRef<jobject> j_rc_parameters =
      ToJavaRateControlParameters(jni, rc_parameters);
  ScopedJavaLocalRef<jobject> ret =
      Java_VideoEncoder_setRates(jni, encoder_, j_rc_parameters);
  HandleReturnCode(jni, ret, "setRates");
}

VideoEncoder::EncoderInfo VideoEncoderWrapper::GetEncoderInfo() const {
  return encoder_info_;
}

VideoEncoderWrapper::ScalingSettings
VideoEncoderWrapper::GetScalingSettingsInternal(JNIEnv* jni) const {
  ScopedJavaLocalRef<jobject> j_scaling_settings =
      Java_VideoEncoder_getScalingSettings(jni, encoder_);
  bool isOn =
      Java_VideoEncoderWrapper_getScalingSettingsOn(jni, j_scaling_settings);

  if (!isOn)
    return ScalingSettings::kOff;

  absl::optional<int> low = JavaToNativeOptionalInt(
      jni,
      Java_VideoEncoderWrapper_getScalingSettingsLow(jni, j_scaling_settings));
  absl::optional<int> high = JavaToNativeOptionalInt(
      jni,
      Java_VideoEncoderWrapper_getScalingSettingsHigh(jni, j_scaling_settings));

  if (low && high)
    return ScalingSettings(*low, *high);

  switch (codec_settings_.codecType) {
    case kVideoCodecVP8: {
      // Same as in vp8_impl.cc.
      static const int kLowVp8QpThreshold = 29;
      static const int kHighVp8QpThreshold = 95;
      return ScalingSettings(low.value_or(kLowVp8QpThreshold),
                             high.value_or(kHighVp8QpThreshold));
    }
    case kVideoCodecVP9: {
      // QP is obtained from VP9-bitstream, so the QP corresponds to the
      // bitstream range of [0, 255] and not the user-level range of [0,63].
      static const int kLowVp9QpThreshold = 96;
      static const int kHighVp9QpThreshold = 185;

      return VideoEncoder::ScalingSettings(kLowVp9QpThreshold,
                                           kHighVp9QpThreshold);
    }
    case kVideoCodecH265:
    // TODO(bugs.webrtc.org/13485): Use H264 QP thresholds for now.
    case kVideoCodecH264: {
      // Same as in h264_encoder_impl.cc.
      static const int kLowH264QpThreshold = 24;
      static const int kHighH264QpThreshold = 37;
      return ScalingSettings(low.value_or(kLowH264QpThreshold),
                             high.value_or(kHighH264QpThreshold));
    }
    default:
      return ScalingSettings::kOff;
  }
}

VideoEncoder::EncoderInfo VideoEncoderWrapper::GetEncoderInfoInternal(
    JNIEnv* jni) const {
  ScopedJavaLocalRef<jobject> j_encoder_info =
      Java_VideoEncoder_getEncoderInfo(jni, encoder_);

  jint requested_resolution_alignment =
      Java_EncoderInfo_getRequestedResolutionAlignment(jni, j_encoder_info);

  jboolean apply_alignment_to_all_simulcast_layers =
      Java_EncoderInfo_getApplyAlignmentToAllSimulcastLayers(jni,
                                                             j_encoder_info);

  VideoEncoder::EncoderInfo info;
  info.requested_resolution_alignment = requested_resolution_alignment;
  info.apply_alignment_to_all_simulcast_layers =
      apply_alignment_to_all_simulcast_layers;

  return info;
}

void VideoEncoderWrapper::OnEncodedFrame(
    JNIEnv* jni,
    const JavaRef<jobject>& j_encoded_image) {
  EncodedImage frame = JavaToNativeEncodedImage(jni, j_encoded_image);
  int64_t capture_time_ns =
      GetJavaEncodedImageCaptureTimeNs(jni, j_encoded_image);

  // Encoded frames are delivered in the order received, but some of them
  // may be dropped, so remove records of frames older than the current
  // one.
  //
  // NOTE: if the current frame is associated with Encoder A, in the time
  // since this frame was received, Encoder A could have been
  // Release()'ed, Encoder B InitEncode()'ed (due to reuse of Encoder A),
  // and frames received by Encoder B. Thus there may be frame_extra_infos
  // entries that don't belong to us, and we need to be careful not to
  // remove them. Removing only those entries older than the current frame
  // provides this guarantee.
  FrameExtraInfo frame_extra_info;
  {
    MutexLock lock(&frame_extra_infos_lock_);
    while (!frame_extra_infos_.empty() &&
           frame_extra_infos_.front().capture_time_ns < capture_time_ns) {
      frame_extra_infos_.pop_front();
    }
    if (frame_extra_infos_.empty() ||
        frame_extra_infos_.front().capture_time_ns != capture_time_ns) {
      RTC_LOG(LS_WARNING)
          << "Java encoder produced an unexpected frame with timestamp: "
          << capture_time_ns;
      return;
    }
    frame_extra_info = frame_extra_infos_.front();
    frame_extra_infos_.pop_front();
  }

  // This is a bit subtle. The `frame` variable from the lambda capture is
  // const. Which implies that (i) we need to make a copy to be able to
  // write to the metadata, and (ii) we should avoid using the .data()
  // method (including implicit conversion to ArrayView) on the non-const
  // copy, since that would trigget a copy operation on the underlying
  // CopyOnWriteBuffer.
  EncodedImage frame_copy = frame;

  frame_copy.SetRtpTimestamp(frame_extra_info.timestamp_rtp);
  frame_copy.capture_time_ms_ = capture_time_ns / rtc::kNumNanosecsPerMillisec;

  if (frame_copy.qp_ < 0)
    frame_copy.qp_ = ParseQp(frame);

  CodecSpecificInfo info(ParseCodecSpecificInfo(frame));

  callback_->OnEncodedImage(frame_copy, &info);
}

int32_t VideoEncoderWrapper::HandleReturnCode(JNIEnv* jni,
                                              const JavaRef<jobject>& j_value,
                                              const char* method_name) {
  int32_t value = JavaToNativeVideoCodecStatus(jni, j_value);
  if (value >= 0) {  // OK or NO_OUTPUT
    return value;
  }

  RTC_LOG(LS_WARNING) << method_name << ": " << value;
  if (value == WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE ||
      value == WEBRTC_VIDEO_CODEC_UNINITIALIZED) {  // Critical error.
    RTC_LOG(LS_WARNING) << "Java encoder requested software fallback.";
    return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
  }

  // Try resetting the codec.
  if (Release() == WEBRTC_VIDEO_CODEC_OK &&
      InitEncodeInternal(jni) == WEBRTC_VIDEO_CODEC_OK) {
    RTC_LOG(LS_WARNING) << "Reset Java encoder.";
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  RTC_LOG(LS_WARNING) << "Unable to reset Java encoder.";
  return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
}

int VideoEncoderWrapper::ParseQp(rtc::ArrayView<const uint8_t> buffer) {
  int qp;
  bool success;
  switch (codec_settings_.codecType) {
    case kVideoCodecVP8:
      success = vp8::GetQp(buffer.data(), buffer.size(), &qp);
      break;
    case kVideoCodecVP9:
      success = vp9::GetQp(buffer.data(), buffer.size(), &qp);
      break;
    case kVideoCodecH264:
      h264_bitstream_parser_.ParseBitstream(buffer);
      qp = h264_bitstream_parser_.GetLastSliceQp().value_or(-1);
      success = (qp >= 0);
      break;
#ifdef RTC_ENABLE_H265
    case kVideoCodecH265:
      h265_bitstream_parser_.ParseBitstream(buffer);
      qp = h265_bitstream_parser_.GetLastSliceQp().value_or(-1);
      success = (qp >= 0);
      break;
#endif
    default:  // Default is to not provide QP.
      success = false;
      break;
  }
  return success ? qp : -1;  // -1 means unknown QP.
}

CodecSpecificInfo VideoEncoderWrapper::ParseCodecSpecificInfo(
    const EncodedImage& frame) {
  const bool key_frame = frame._frameType == VideoFrameType::kVideoFrameKey;

  CodecSpecificInfo info;
  // For stream with scalability, NextFrameConfig should be called before
  // encoding and used to configure encoder, then passed here e.g. via
  // FrameExtraInfo structure. But while this encoder wrapper uses only trivial
  // scalability, NextFrameConfig can be called here.
  auto layer_frames = svc_controller_.NextFrameConfig(/*reset=*/key_frame);
  RTC_DCHECK_EQ(layer_frames.size(), 1);
  info.generic_frame_info = svc_controller_.OnEncodeDone(layer_frames[0]);
  if (key_frame) {
    info.template_structure = svc_controller_.DependencyStructure();
    info.template_structure->resolutions = {
        RenderResolution(frame._encodedWidth, frame._encodedHeight)};
  }

  info.codecType = codec_settings_.codecType;

  switch (codec_settings_.codecType) {
    case kVideoCodecVP8:
      info.codecSpecific.VP8.nonReference = false;
      info.codecSpecific.VP8.temporalIdx = kNoTemporalIdx;
      info.codecSpecific.VP8.layerSync = false;
      info.codecSpecific.VP8.keyIdx = kNoKeyIdx;
      break;
    case kVideoCodecVP9:
      if (key_frame) {
        gof_idx_ = 0;
      }
      info.codecSpecific.VP9.inter_pic_predicted = key_frame ? false : true;
      info.codecSpecific.VP9.flexible_mode = false;
      info.codecSpecific.VP9.ss_data_available = key_frame ? true : false;
      info.codecSpecific.VP9.temporal_idx = kNoTemporalIdx;
      info.codecSpecific.VP9.temporal_up_switch = true;
      info.codecSpecific.VP9.inter_layer_predicted = false;
      info.codecSpecific.VP9.gof_idx =
          static_cast<uint8_t>(gof_idx_++ % gof_.num_frames_in_gof);
      info.codecSpecific.VP9.num_spatial_layers = 1;
      info.codecSpecific.VP9.first_frame_in_picture = true;
      info.codecSpecific.VP9.spatial_layer_resolution_present = false;
      if (info.codecSpecific.VP9.ss_data_available) {
        info.codecSpecific.VP9.spatial_layer_resolution_present = true;
        info.codecSpecific.VP9.width[0] = frame._encodedWidth;
        info.codecSpecific.VP9.height[0] = frame._encodedHeight;
        info.codecSpecific.VP9.gof.CopyGofInfoVP9(gof_);
      }
      break;
    default:
      break;
  }

  return info;
}

ScopedJavaLocalRef<jobject> VideoEncoderWrapper::ToJavaBitrateAllocation(
    JNIEnv* jni,
    const VideoBitrateAllocation& allocation) {
  ScopedJavaLocalRef<jobjectArray> j_allocation_array(
      jni, jni->NewObjectArray(kMaxSpatialLayers, int_array_class_.obj(),
                               nullptr /* initial */));
  for (int spatial_i = 0; spatial_i < kMaxSpatialLayers; ++spatial_i) {
    std::array<int32_t, kMaxTemporalStreams> spatial_layer;
    for (int temporal_i = 0; temporal_i < kMaxTemporalStreams; ++temporal_i) {
      spatial_layer[temporal_i] = allocation.GetBitrate(spatial_i, temporal_i);
    }

    ScopedJavaLocalRef<jintArray> j_array_spatial_layer =
        NativeToJavaIntArray(jni, spatial_layer);
    jni->SetObjectArrayElement(j_allocation_array.obj(), spatial_i,
                               j_array_spatial_layer.obj());
  }
  return Java_BitrateAllocation_Constructor(jni, j_allocation_array);
}

ScopedJavaLocalRef<jobject> VideoEncoderWrapper::ToJavaRateControlParameters(
    JNIEnv* jni,
    const VideoEncoder::RateControlParameters& rc_parameters) {
  ScopedJavaLocalRef<jobject> j_bitrate_allocation =
      ToJavaBitrateAllocation(jni, rc_parameters.bitrate);

  return Java_RateControlParameters_Constructor(jni, j_bitrate_allocation,
                                                rc_parameters.framerate_fps);
}

std::unique_ptr<VideoEncoder> JavaToNativeVideoEncoder(
    JNIEnv* jni,
    const JavaRef<jobject>& j_encoder) {
  const jlong native_encoder =
      Java_VideoEncoder_createNativeVideoEncoder(jni, j_encoder);
  VideoEncoder* encoder;
  if (native_encoder == 0) {
    encoder = new VideoEncoderWrapper(jni, j_encoder);
  } else {
    encoder = reinterpret_cast<VideoEncoder*>(native_encoder);
  }
  return std::unique_ptr<VideoEncoder>(encoder);
}

std::vector<VideoEncoder::ResolutionBitrateLimits>
JavaToNativeResolutionBitrateLimits(
    JNIEnv* jni,
    const JavaRef<jobjectArray>& j_bitrate_limits_array) {
  std::vector<VideoEncoder::ResolutionBitrateLimits> resolution_bitrate_limits;

  const jsize array_length = jni->GetArrayLength(j_bitrate_limits_array.obj());
  for (int i = 0; i < array_length; ++i) {
    ScopedJavaLocalRef<jobject> j_bitrate_limits = ScopedJavaLocalRef<jobject>(
        jni, jni->GetObjectArrayElement(j_bitrate_limits_array.obj(), i));

    jint frame_size_pixels =
        Java_ResolutionBitrateLimits_getFrameSizePixels(jni, j_bitrate_limits);
    jint min_start_bitrate_bps =
        Java_ResolutionBitrateLimits_getMinStartBitrateBps(jni,
                                                           j_bitrate_limits);
    jint min_bitrate_bps =
        Java_ResolutionBitrateLimits_getMinBitrateBps(jni, j_bitrate_limits);
    jint max_bitrate_bps =
        Java_ResolutionBitrateLimits_getMaxBitrateBps(jni, j_bitrate_limits);

    resolution_bitrate_limits.push_back(VideoEncoder::ResolutionBitrateLimits(
        frame_size_pixels, min_start_bitrate_bps, min_bitrate_bps,
        max_bitrate_bps));
  }

  return resolution_bitrate_limits;
}

}  // namespace jni
}  // namespace webrtc
