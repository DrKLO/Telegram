/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/video_decoder_wrapper.h"

#include "api/video/video_frame.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/utility/vp8_header_parser.h"
#include "modules/video_coding/utility/vp9_uncompressed_header_parser.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/time_utils.h"
#include "sdk/android/generated_video_jni/VideoDecoderWrapper_jni.h"
#include "sdk/android/generated_video_jni/VideoDecoder_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/encoded_image.h"
#include "sdk/android/src/jni/video_codec_status.h"
#include "sdk/android/src/jni/video_frame.h"

namespace webrtc {
namespace jni {

namespace {
// RTP timestamps are 90 kHz.
const int64_t kNumRtpTicksPerMillisec = 90000 / rtc::kNumMillisecsPerSec;

template <typename Dst, typename Src>
inline absl::optional<Dst> cast_optional(const absl::optional<Src>& value) {
  return value ? absl::optional<Dst>(rtc::dchecked_cast<Dst, Src>(*value))
               : absl::nullopt;
}
}  // namespace

VideoDecoderWrapper::VideoDecoderWrapper(JNIEnv* jni,
                                         const JavaRef<jobject>& decoder)
    : decoder_(jni, decoder),
      implementation_name_(JavaToStdString(
          jni,
          Java_VideoDecoder_getImplementationName(jni, decoder))),
      initialized_(false),
      qp_parsing_enabled_(true)  // QP parsing starts enabled and we disable it
                                 // if the decoder provides frames.

{
  decoder_thread_checker_.Detach();
}

VideoDecoderWrapper::~VideoDecoderWrapper() = default;

int32_t VideoDecoderWrapper::InitDecode(const VideoCodec* codec_settings,
                                        int32_t number_of_cores) {
  RTC_DCHECK_RUN_ON(&decoder_thread_checker_);
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  codec_settings_ = *codec_settings;
  number_of_cores_ = number_of_cores;
  return InitDecodeInternal(jni);
}

int32_t VideoDecoderWrapper::InitDecodeInternal(JNIEnv* jni) {
  ScopedJavaLocalRef<jobject> settings = Java_Settings_Constructor(
      jni, number_of_cores_, codec_settings_.width, codec_settings_.height);

  ScopedJavaLocalRef<jobject> callback =
      Java_VideoDecoderWrapper_createDecoderCallback(jni,
                                                     jlongFromPointer(this));

  int32_t status = JavaToNativeVideoCodecStatus(
      jni, Java_VideoDecoder_initDecode(jni, decoder_, settings, callback));
  RTC_LOG(LS_INFO) << "initDecode: " << status;
  if (status == WEBRTC_VIDEO_CODEC_OK) {
    initialized_ = true;
  }

  // The decoder was reinitialized so re-enable the QP parsing in case it stops
  // providing QP values.
  qp_parsing_enabled_ = true;

  return status;
}

int32_t VideoDecoderWrapper::Decode(
    const EncodedImage& image_param,
    bool missing_frames,
    int64_t render_time_ms) {
  RTC_DCHECK_RUN_ON(&decoder_thread_checker_);
  if (!initialized_) {
    // Most likely initializing the codec failed.
    return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
  }

  // Make a mutable copy so we can modify the timestamp.
  EncodedImage input_image(image_param);
  // We use RTP timestamp for capture time because capture_time_ms_ is always 0.
  input_image.capture_time_ms_ =
      input_image.Timestamp() / kNumRtpTicksPerMillisec;

  FrameExtraInfo frame_extra_info;
  frame_extra_info.timestamp_ns =
      input_image.capture_time_ms_ * rtc::kNumNanosecsPerMillisec;
  frame_extra_info.timestamp_rtp = input_image.Timestamp();
  frame_extra_info.timestamp_ntp = input_image.ntp_time_ms_;
  frame_extra_info.qp =
      qp_parsing_enabled_ ? ParseQP(input_image) : absl::nullopt;
  {
    MutexLock lock(&frame_extra_infos_lock_);
    frame_extra_infos_.push_back(frame_extra_info);
  }

  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> jinput_image =
      NativeToJavaEncodedImage(env, input_image);
  ScopedJavaLocalRef<jobject> decode_info;
  ScopedJavaLocalRef<jobject> ret =
      Java_VideoDecoder_decode(env, decoder_, jinput_image, decode_info);
  return HandleReturnCode(env, ret, "decode");
}

int32_t VideoDecoderWrapper::RegisterDecodeCompleteCallback(
    DecodedImageCallback* callback) {
  RTC_DCHECK_RUNS_SERIALIZED(&callback_race_checker_);
  callback_ = callback;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t VideoDecoderWrapper::Release() {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  int32_t status = JavaToNativeVideoCodecStatus(
      jni, Java_VideoDecoder_release(jni, decoder_));
  RTC_LOG(LS_INFO) << "release: " << status;
  {
    MutexLock lock(&frame_extra_infos_lock_);
    frame_extra_infos_.clear();
  }
  initialized_ = false;
  // It is allowed to reinitialize the codec on a different thread.
  decoder_thread_checker_.Detach();
  return status;
}

bool VideoDecoderWrapper::PrefersLateDecoding() const {
  JNIEnv* jni = AttachCurrentThreadIfNeeded();
  return Java_VideoDecoder_getPrefersLateDecoding(jni, decoder_);
}

const char* VideoDecoderWrapper::ImplementationName() const {
  return implementation_name_.c_str();
}

void VideoDecoderWrapper::OnDecodedFrame(
    JNIEnv* env,
    const JavaRef<jobject>& j_frame,
    const JavaRef<jobject>& j_decode_time_ms,
    const JavaRef<jobject>& j_qp) {
  RTC_DCHECK_RUNS_SERIALIZED(&callback_race_checker_);
  const int64_t timestamp_ns = GetJavaVideoFrameTimestampNs(env, j_frame);

  FrameExtraInfo frame_extra_info;
  {
    MutexLock lock(&frame_extra_infos_lock_);

    do {
      if (frame_extra_infos_.empty()) {
        RTC_LOG(LS_WARNING)
            << "Java decoder produced an unexpected frame: " << timestamp_ns;
        return;
      }

      frame_extra_info = frame_extra_infos_.front();
      frame_extra_infos_.pop_front();
      // If the decoder might drop frames so iterate through the queue until we
      // find a matching timestamp.
    } while (frame_extra_info.timestamp_ns != timestamp_ns);
  }

  VideoFrame frame =
      JavaToNativeFrame(env, j_frame, frame_extra_info.timestamp_rtp);
  frame.set_ntp_time_ms(frame_extra_info.timestamp_ntp);

  absl::optional<int32_t> decoding_time_ms =
      JavaToNativeOptionalInt(env, j_decode_time_ms);

  absl::optional<uint8_t> decoder_qp =
      cast_optional<uint8_t, int32_t>(JavaToNativeOptionalInt(env, j_qp));
  // If the decoder provides QP values itself, no need to parse the bitstream.
  // Enable QP parsing if decoder does not provide QP values itself.
  qp_parsing_enabled_ = !decoder_qp.has_value();
  callback_->Decoded(frame, decoding_time_ms,
                     decoder_qp ? decoder_qp : frame_extra_info.qp);
}

VideoDecoderWrapper::FrameExtraInfo::FrameExtraInfo() = default;
VideoDecoderWrapper::FrameExtraInfo::FrameExtraInfo(const FrameExtraInfo&) =
    default;
VideoDecoderWrapper::FrameExtraInfo::~FrameExtraInfo() = default;

int32_t VideoDecoderWrapper::HandleReturnCode(JNIEnv* jni,
                                              const JavaRef<jobject>& j_value,
                                              const char* method_name) {
  int32_t value = JavaToNativeVideoCodecStatus(jni, j_value);
  if (value >= 0) {  // OK or NO_OUTPUT
    return value;
  }

  RTC_LOG(LS_WARNING) << method_name << ": " << value;
  if (value == WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE ||
      value == WEBRTC_VIDEO_CODEC_UNINITIALIZED) {  // Critical error.
    RTC_LOG(LS_WARNING) << "Java decoder requested software fallback.";
    return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
  }

  // Try resetting the codec.
  if (Release() == WEBRTC_VIDEO_CODEC_OK &&
      InitDecodeInternal(jni) == WEBRTC_VIDEO_CODEC_OK) {
    RTC_LOG(LS_WARNING) << "Reset Java decoder.";
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  RTC_LOG(LS_WARNING) << "Unable to reset Java decoder.";
  return WEBRTC_VIDEO_CODEC_FALLBACK_SOFTWARE;
}

absl::optional<uint8_t> VideoDecoderWrapper::ParseQP(
    const EncodedImage& input_image) {
  if (input_image.qp_ != -1) {
    return input_image.qp_;
  }

  absl::optional<uint8_t> qp;
  switch (codec_settings_.codecType) {
    case kVideoCodecVP8: {
      int qp_int;
      if (vp8::GetQp(input_image.data(), input_image.size(), &qp_int)) {
        qp = qp_int;
      }
      break;
    }
    case kVideoCodecVP9: {
      int qp_int;
      if (vp9::GetQp(input_image.data(), input_image.size(), &qp_int)) {
        qp = qp_int;
      }
      break;
    }
    case kVideoCodecH264: {
      h264_bitstream_parser_.ParseBitstream(input_image.data(),
                                            input_image.size());
      int qp_int;
      if (h264_bitstream_parser_.GetLastSliceQp(&qp_int)) {
        qp = qp_int;
      }
      break;
    }
    default:
      break;  // Default is to not provide QP.
  }
  return qp;
}

std::unique_ptr<VideoDecoder> JavaToNativeVideoDecoder(
    JNIEnv* jni,
    const JavaRef<jobject>& j_decoder) {
  const jlong native_decoder =
      Java_VideoDecoder_createNativeVideoDecoder(jni, j_decoder);
  VideoDecoder* decoder;
  if (native_decoder == 0) {
    decoder = new VideoDecoderWrapper(jni, j_decoder);
  } else {
    decoder = reinterpret_cast<VideoDecoder*>(native_decoder);
  }
  return std::unique_ptr<VideoDecoder>(decoder);
}

}  // namespace jni
}  // namespace webrtc
