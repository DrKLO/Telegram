/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "objc_video_encoder_factory.h"

#include <string>

#import "base/RTCMacros.h"
#import "base/RTCVideoEncoder.h"
#import "base/RTCVideoEncoderFactory.h"
#import "components/video_codec/RTCCodecSpecificInfoH264+Private.h"
#ifndef WEBRTC_DISABLE_H265
#import "RTCCodecSpecificInfoH265+Private.h"
#endif
#import "sdk/objc/api/peerconnection/RTCEncodedImage+Private.h"
#import "sdk/objc/api/peerconnection/RTCVideoCodecInfo+Private.h"
#import "sdk/objc/api/peerconnection/RTCVideoEncoderSettings+Private.h"
#import "sdk/objc/api/video_codec/RTCVideoCodecConstants.h"
#import "sdk/objc/api/video_codec/RTCWrappedNativeVideoEncoder.h"
#import "sdk/objc/helpers/NSString+StdString.h"

#include "api/video/video_frame.h"
#include "api/video_codecs/sdp_video_format.h"
#include "api/video_codecs/video_encoder.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/logging.h"
#include "sdk/objc/native/src/objc_video_frame.h"
#include "api/transport/field_trial_based_config.h"

#include "CustomSimulcastEncoderAdapter.h"

namespace webrtc {

namespace {

class ObjCVideoEncoder : public VideoEncoder {
 public:
  ObjCVideoEncoder(id<RTC_OBJC_TYPE(RTCVideoEncoder)> encoder)
      : encoder_(encoder), implementation_name_([encoder implementationName].stdString) {}

  int32_t InitEncode(const VideoCodec *codec_settings, const Settings &encoder_settings) override {
    RTC_OBJC_TYPE(RTCVideoEncoderSettings) *settings =
        [[RTC_OBJC_TYPE(RTCVideoEncoderSettings) alloc] initWithNativeVideoCodec:codec_settings];
    return [encoder_ startEncodeWithSettings:settings
                               numberOfCores:encoder_settings.number_of_cores];
  }

  int32_t RegisterEncodeCompleteCallback(EncodedImageCallback *callback) override {
    [encoder_ setCallback:^BOOL(RTC_OBJC_TYPE(RTCEncodedImage) * _Nonnull frame,
                                id<RTC_OBJC_TYPE(RTCCodecSpecificInfo)> _Nonnull info) {
      EncodedImage encodedImage = [frame nativeEncodedImage];

      // Handle types that can be converted into one of CodecSpecificInfo's hard coded cases.
      CodecSpecificInfo codecSpecificInfo;
      // Because of symbol conflict, isKindOfClass doesn't work as expected.
      // See https://bugs.webkit.org/show_bug.cgi?id=198782.
      if ([NSStringFromClass([info class]) isEqual:@"RTCCodecSpecificInfoH264"]) {
        // if ([info isKindOfClass:[RTCCodecSpecificInfoH264 class]]) {
        codecSpecificInfo = [(RTCCodecSpecificInfoH264 *)info nativeCodecSpecificInfo];
#ifndef WEBRTC_DISABLE_H265
      } else if ([NSStringFromClass([info class]) isEqual:@"RTCCodecSpecificInfoH265"]) {
        // if ([info isKindOfClass:[RTCCodecSpecificInfoH265 class]]) {
        codecSpecificInfo = [(RTCCodecSpecificInfoH265 *)info nativeCodecSpecificInfo];
#endif
      }

      EncodedImageCallback::Result res = callback->OnEncodedImage(encodedImage, &codecSpecificInfo);
      return res.error == EncodedImageCallback::Result::OK;
    }];

    return WEBRTC_VIDEO_CODEC_OK;
  }

  int32_t Release() override { return [encoder_ releaseEncoder]; }

  int32_t Encode(const VideoFrame &frame,
                 const std::vector<VideoFrameType> *frame_types) override {
    int32_t result = 0;
    @autoreleasepool {
    NSMutableArray<NSNumber *> *rtcFrameTypes = [NSMutableArray array];
    for (size_t i = 0; i < frame_types->size(); ++i) {
      [rtcFrameTypes addObject:@(RTCFrameType(frame_types->at(i)))];
    }

    result = [encoder_ encode:ToObjCVideoFrame(frame)
          codecSpecificInfo:nil
                 frameTypes:rtcFrameTypes];
    }
    return result;
  }

  void SetRates(const RateControlParameters &parameters) override {
    const uint32_t bitrate = parameters.bitrate.get_sum_kbps();
    const uint32_t framerate = static_cast<uint32_t>(parameters.framerate_fps + 0.5);
    [encoder_ setBitrate:bitrate framerate:framerate];
  }

  VideoEncoder::EncoderInfo GetEncoderInfo() const override {
    EncoderInfo info;
    info.supports_native_handle = true;
    info.implementation_name = implementation_name_;

    RTC_OBJC_TYPE(RTCVideoEncoderQpThresholds) *qp_thresholds = [encoder_ scalingSettings];
    info.scaling_settings = qp_thresholds ? ScalingSettings(qp_thresholds.low, qp_thresholds.high) :
                                            ScalingSettings::kOff;

    info.is_hardware_accelerated = true;
    return info;
  }

 private:
  id<RTC_OBJC_TYPE(RTCVideoEncoder)> encoder_;
  const std::string implementation_name_;
};

class ObjcVideoEncoderSelector : public VideoEncoderFactory::EncoderSelectorInterface {
 public:
  ObjcVideoEncoderSelector(id<RTC_OBJC_TYPE(RTCVideoEncoderSelector)> selector) {
    selector_ = selector;
  }
  void OnCurrentEncoder(const SdpVideoFormat &format) override {
    RTC_OBJC_TYPE(RTCVideoCodecInfo) *info =
        [[RTC_OBJC_TYPE(RTCVideoCodecInfo) alloc] initWithNativeSdpVideoFormat:format];
    [selector_ registerCurrentEncoderInfo:info];
  }
  absl::optional<SdpVideoFormat> OnEncoderBroken() override {
    RTC_OBJC_TYPE(RTCVideoCodecInfo) *info = [selector_ encoderForBrokenEncoder];
    if (info) {
      return [info nativeSdpVideoFormat];
    }
    return absl::nullopt;
  }
  absl::optional<SdpVideoFormat> OnAvailableBitrate(const DataRate &rate) override {
    RTC_OBJC_TYPE(RTCVideoCodecInfo) *info = [selector_ encoderForBitrate:rate.kbps<NSInteger>()];
    if (info) {
      return [info nativeSdpVideoFormat];
    }
    return absl::nullopt;
  }

 private:
  id<RTC_OBJC_TYPE(RTCVideoEncoderSelector)> selector_;
};

}  // namespace

CustomObjCVideoEncoderFactory::CustomObjCVideoEncoderFactory(
    id<RTC_OBJC_TYPE(RTCVideoEncoderFactory)> encoder_factory)
    : encoder_factory_(encoder_factory) {}

CustomObjCVideoEncoderFactory::~CustomObjCVideoEncoderFactory() {}

id<RTC_OBJC_TYPE(RTCVideoEncoderFactory)> CustomObjCVideoEncoderFactory::wrapped_encoder_factory() const {
  return encoder_factory_;
}

std::vector<SdpVideoFormat> CustomObjCVideoEncoderFactory::GetSupportedFormats() const {
  std::vector<SdpVideoFormat> supported_formats;
  for (RTC_OBJC_TYPE(RTCVideoCodecInfo) * supportedCodec in [encoder_factory_ supportedCodecs]) {
    SdpVideoFormat format = [supportedCodec nativeSdpVideoFormat];
    supported_formats.push_back(format);
  }

  return supported_formats;
}

std::vector<SdpVideoFormat> CustomObjCVideoEncoderFactory::GetImplementations() const {
  if ([encoder_factory_ respondsToSelector:@selector(implementations)]) {
    std::vector<SdpVideoFormat> supported_formats;
    for (RTC_OBJC_TYPE(RTCVideoCodecInfo) * supportedCodec in [encoder_factory_ implementations]) {
      SdpVideoFormat format = [supportedCodec nativeSdpVideoFormat];
      supported_formats.push_back(format);
    }
    return supported_formats;
  }
  return GetSupportedFormats();
}

std::unique_ptr<VideoEncoder> CustomObjCVideoEncoderFactory::CreateVideoEncoder(
    const SdpVideoFormat &format) {
  RTCVideoCodecInfo *info = [[RTCVideoCodecInfo alloc] initWithNativeSdpVideoFormat:format];
  id<RTCVideoEncoder> encoder = [encoder_factory_ createEncoder:info];
  if ([encoder isKindOfClass:[RTCWrappedNativeVideoEncoder class]]) {
    return [(RTCWrappedNativeVideoEncoder *)encoder releaseWrappedEncoder];
  } else {
    return std::unique_ptr<ObjCVideoEncoder>(new ObjCVideoEncoder(encoder));
  }
}

std::unique_ptr<VideoEncoderFactory::EncoderSelectorInterface>
    CustomObjCVideoEncoderFactory::GetEncoderSelector() const {
  if ([encoder_factory_ respondsToSelector:@selector(encoderSelector)]) {
    id<RTC_OBJC_TYPE(RTCVideoEncoderSelector)> selector = [encoder_factory_ encoderSelector];
    if (selector) {
      return absl::make_unique<ObjcVideoEncoderSelector>(selector);
    }
  }
  return nullptr;
}

SimulcastVideoEncoderFactory::SimulcastVideoEncoderFactory(std::unique_ptr<CustomObjCVideoEncoderFactory> softwareFactory, std::unique_ptr<CustomObjCVideoEncoderFactory> hardwareFactory) :
    _softwareFactory(std::move(softwareFactory)),
    _hardwareFactory(std::move(hardwareFactory)) {
}
SimulcastVideoEncoderFactory::~SimulcastVideoEncoderFactory() {
}
    
std::vector<SdpVideoFormat> SimulcastVideoEncoderFactory::GetSupportedFormats() const {
    return _hardwareFactory->GetSupportedFormats();
}

std::vector<SdpVideoFormat> SimulcastVideoEncoderFactory::GetImplementations() const {
    return _hardwareFactory->GetImplementations();
}

std::unique_ptr<VideoEncoder> SimulcastVideoEncoderFactory::CreateVideoEncoder(const SdpVideoFormat& format) {
#ifndef __aarch64__
#ifdef WEBRTC_MAC
    return std::make_unique<webrtc::CustomSimulcastEncoderAdapter>(_softwareFactory.get(), _softwareFactory.get(), format, webrtc::FieldTrialBasedConfig());
#else
    return std::make_unique<webrtc::CustomSimulcastEncoderAdapter>(_hardwareFactory.get(), _hardwareFactory.get(), format, webrtc::FieldTrialBasedConfig());
#endif //WEBRTC_MAC
#else
    return std::make_unique<webrtc::CustomSimulcastEncoderAdapter>(_hardwareFactory.get(), _hardwareFactory.get(), format, webrtc::FieldTrialBasedConfig());
#endif //__aarch64__
}

std::unique_ptr<VideoEncoderFactory::EncoderSelectorInterface> SimulcastVideoEncoderFactory::GetEncoderSelector() const {
    return _hardwareFactory->GetEncoderSelector();
}

}  // namespace webrtc
