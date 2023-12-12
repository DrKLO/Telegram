/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/engine/fake_webrtc_video_engine.h"

#include <algorithm>
#include <memory>

#include "absl/strings/match.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/engine/simulcast_encoder_adapter.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/time_utils.h"

namespace cricket {

namespace {

static constexpr webrtc::TimeDelta kEventTimeout =
    webrtc::TimeDelta::Seconds(10);

}  // namespace

// Decoder.
FakeWebRtcVideoDecoder::FakeWebRtcVideoDecoder(
    FakeWebRtcVideoDecoderFactory* factory)
    : num_frames_received_(0), factory_(factory) {}

FakeWebRtcVideoDecoder::~FakeWebRtcVideoDecoder() {
  if (factory_) {
    factory_->DecoderDestroyed(this);
  }
}

bool FakeWebRtcVideoDecoder::Configure(const Settings& settings) {
  return true;
}

int32_t FakeWebRtcVideoDecoder::Decode(const webrtc::EncodedImage&,
                                       bool,
                                       int64_t) {
  num_frames_received_++;
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t FakeWebRtcVideoDecoder::RegisterDecodeCompleteCallback(
    webrtc::DecodedImageCallback*) {
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t FakeWebRtcVideoDecoder::Release() {
  return WEBRTC_VIDEO_CODEC_OK;
}

int FakeWebRtcVideoDecoder::GetNumFramesReceived() const {
  return num_frames_received_;
}

// Decoder factory.
FakeWebRtcVideoDecoderFactory::FakeWebRtcVideoDecoderFactory()
    : num_created_decoders_(0) {}

std::vector<webrtc::SdpVideoFormat>
FakeWebRtcVideoDecoderFactory::GetSupportedFormats() const {
  std::vector<webrtc::SdpVideoFormat> formats;

  for (const webrtc::SdpVideoFormat& format : supported_codec_formats_) {
    // Don't add same codec twice.
    if (!format.IsCodecInList(formats))
      formats.push_back(format);
  }

  return formats;
}

std::unique_ptr<webrtc::VideoDecoder>
FakeWebRtcVideoDecoderFactory::CreateVideoDecoder(
    const webrtc::SdpVideoFormat& format) {
  if (format.IsCodecInList(supported_codec_formats_)) {
    num_created_decoders_++;
    std::unique_ptr<FakeWebRtcVideoDecoder> decoder =
        std::make_unique<FakeWebRtcVideoDecoder>(this);
    decoders_.push_back(decoder.get());
    return decoder;
  }

  return nullptr;
}

void FakeWebRtcVideoDecoderFactory::DecoderDestroyed(
    FakeWebRtcVideoDecoder* decoder) {
  decoders_.erase(std::remove(decoders_.begin(), decoders_.end(), decoder),
                  decoders_.end());
}

void FakeWebRtcVideoDecoderFactory::AddSupportedVideoCodecType(
    const std::string& name) {
  // This is to match the default H264 params of cricket::VideoCodec.
  cricket::VideoCodec video_codec(name);
  supported_codec_formats_.push_back(
      webrtc::SdpVideoFormat(video_codec.name, video_codec.params));
}

int FakeWebRtcVideoDecoderFactory::GetNumCreatedDecoders() {
  return num_created_decoders_;
}

const std::vector<FakeWebRtcVideoDecoder*>&
FakeWebRtcVideoDecoderFactory::decoders() {
  return decoders_;
}

// Encoder.
FakeWebRtcVideoEncoder::FakeWebRtcVideoEncoder(
    FakeWebRtcVideoEncoderFactory* factory)
    : num_frames_encoded_(0), factory_(factory) {}

FakeWebRtcVideoEncoder::~FakeWebRtcVideoEncoder() {
  if (factory_) {
    factory_->EncoderDestroyed(this);
  }
}

void FakeWebRtcVideoEncoder::SetFecControllerOverride(
    webrtc::FecControllerOverride* fec_controller_override) {
  // Ignored.
}

int32_t FakeWebRtcVideoEncoder::InitEncode(
    const webrtc::VideoCodec* codecSettings,
    const VideoEncoder::Settings& settings) {
  webrtc::MutexLock lock(&mutex_);
  codec_settings_ = *codecSettings;
  init_encode_event_.Set();
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t FakeWebRtcVideoEncoder::Encode(
    const webrtc::VideoFrame& inputImage,
    const std::vector<webrtc::VideoFrameType>* frame_types) {
  webrtc::MutexLock lock(&mutex_);
  ++num_frames_encoded_;
  init_encode_event_.Set();
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t FakeWebRtcVideoEncoder::RegisterEncodeCompleteCallback(
    webrtc::EncodedImageCallback* callback) {
  return WEBRTC_VIDEO_CODEC_OK;
}

int32_t FakeWebRtcVideoEncoder::Release() {
  return WEBRTC_VIDEO_CODEC_OK;
}

void FakeWebRtcVideoEncoder::SetRates(const RateControlParameters& parameters) {
}

webrtc::VideoEncoder::EncoderInfo FakeWebRtcVideoEncoder::GetEncoderInfo()
    const {
  EncoderInfo info;
  info.is_hardware_accelerated = true;
  return info;
}

bool FakeWebRtcVideoEncoder::WaitForInitEncode() {
  return init_encode_event_.Wait(kEventTimeout);
}

webrtc::VideoCodec FakeWebRtcVideoEncoder::GetCodecSettings() {
  webrtc::MutexLock lock(&mutex_);
  return codec_settings_;
}

int FakeWebRtcVideoEncoder::GetNumEncodedFrames() {
  webrtc::MutexLock lock(&mutex_);
  return num_frames_encoded_;
}

// Video encoder factory.
FakeWebRtcVideoEncoderFactory::FakeWebRtcVideoEncoderFactory()
    : num_created_encoders_(0),
      vp8_factory_mode_(false) {}

std::vector<webrtc::SdpVideoFormat>
FakeWebRtcVideoEncoderFactory::GetSupportedFormats() const {
  std::vector<webrtc::SdpVideoFormat> formats;

  for (const webrtc::SdpVideoFormat& format : formats_) {
    // Don't add same codec twice.
    if (!format.IsCodecInList(formats))
      formats.push_back(format);
  }

  return formats;
}

std::unique_ptr<webrtc::VideoEncoder>
FakeWebRtcVideoEncoderFactory::CreateVideoEncoder(
    const webrtc::SdpVideoFormat& format) {
  webrtc::MutexLock lock(&mutex_);
  std::unique_ptr<webrtc::VideoEncoder> encoder;
  if (format.IsCodecInList(formats_)) {
    if (absl::EqualsIgnoreCase(format.name, kVp8CodecName) &&
        !vp8_factory_mode_) {
      // The simulcast adapter will ask this factory for multiple VP8
      // encoders. Enter vp8_factory_mode so that we now create these encoders
      // instead of more adapters.
      vp8_factory_mode_ = true;
      encoder = std::make_unique<webrtc::SimulcastEncoderAdapter>(this, format);
    } else {
      num_created_encoders_++;
      created_video_encoder_event_.Set();
      encoder = std::make_unique<FakeWebRtcVideoEncoder>(this);
      encoders_.push_back(static_cast<FakeWebRtcVideoEncoder*>(encoder.get()));
    }
  }
  return encoder;
}

bool FakeWebRtcVideoEncoderFactory::WaitForCreatedVideoEncoders(
    int num_encoders) {
  int64_t start_offset_ms = rtc::TimeMillis();
  int64_t wait_time = kEventTimeout.ms();
  do {
    if (GetNumCreatedEncoders() >= num_encoders)
      return true;
    wait_time = kEventTimeout.ms() - (rtc::TimeMillis() - start_offset_ms);
  } while (wait_time > 0 && created_video_encoder_event_.Wait(
                                webrtc::TimeDelta::Millis(wait_time)));
  return false;
}

void FakeWebRtcVideoEncoderFactory::EncoderDestroyed(
    FakeWebRtcVideoEncoder* encoder) {
  webrtc::MutexLock lock(&mutex_);
  encoders_.erase(std::remove(encoders_.begin(), encoders_.end(), encoder),
                  encoders_.end());
}

void FakeWebRtcVideoEncoderFactory::AddSupportedVideoCodec(
    const webrtc::SdpVideoFormat& format) {
  formats_.push_back(format);
}

void FakeWebRtcVideoEncoderFactory::AddSupportedVideoCodecType(
    const std::string& name) {
  // This is to match the default H264 params of cricket::VideoCodec.
  cricket::VideoCodec video_codec(name);
  formats_.push_back(
      webrtc::SdpVideoFormat(video_codec.name, video_codec.params));
}

int FakeWebRtcVideoEncoderFactory::GetNumCreatedEncoders() {
  webrtc::MutexLock lock(&mutex_);
  return num_created_encoders_;
}

const std::vector<FakeWebRtcVideoEncoder*>
FakeWebRtcVideoEncoderFactory::encoders() {
  webrtc::MutexLock lock(&mutex_);
  return encoders_;
}

}  // namespace cricket
