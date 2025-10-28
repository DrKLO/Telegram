/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifndef WEBRTC_DISABLE_H265

#import "TGRTCVideoDecoderH265.h"

#import <VideoToolbox/VideoToolbox.h>

#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"
#import "helpers.h"
#import "helpers/scoped_cftyperef.h"

#if defined(WEBRTC_IOS)
#import "helpers/UIDevice+RTCDevice.h"
#endif

#include "modules/video_coding/include/video_error_codes.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/thread.h"
#include "sdk/objc/components/video_codec/nalu_rewriter.h"
#include "h265_nalu_rewriter.h"

#include "StaticThreads.h"

@interface MarkedDecodedH2651RTCCVPixelBuffer : RTCCVPixelBuffer

@end

@implementation MarkedDecodedH2651RTCCVPixelBuffer

@end

typedef void (^TGRTCVideoDecoderRequestKeyframeCallback)();

// Struct that we pass to the decoder per frame to decode. We receive it again
// in the decoder callback.
struct RTCH265FrameDecodeParams {
  RTCH265FrameDecodeParams(RTCVideoDecoderCallback cb, int64_t ts, TGRTCVideoDecoderRequestKeyframeCallback requestFrame)
      : callback(cb), timestamp(ts), requestFrame(requestFrame) {}
  RTCVideoDecoderCallback callback;
  int64_t timestamp;
  TGRTCVideoDecoderRequestKeyframeCallback requestFrame;
};

// This is the callback function that VideoToolbox calls when decode is
// complete.
static void tg_h265DecompressionOutputCallback(void* decoder,
                                     void* params,
                                     OSStatus status,
                                     VTDecodeInfoFlags infoFlags,
                                     CVImageBufferRef imageBuffer,
                                     CMTime timestamp,
                                     CMTime duration) {
  std::unique_ptr<RTCH265FrameDecodeParams> decodeParams(
      reinterpret_cast<RTCH265FrameDecodeParams*>(params));
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to decode frame. Status: " << status;
    if (status == -12909) {
      decodeParams->requestFrame();
    }
    return;
  }
  // TODO(tkchin): Handle CVO properly.
  RTCCVPixelBuffer* frameBuffer =
      [[MarkedDecodedH2651RTCCVPixelBuffer alloc] initWithPixelBuffer:imageBuffer];
  RTCVideoFrame* decodedFrame = [[RTCVideoFrame alloc]
      initWithBuffer:frameBuffer
            rotation:RTCVideoRotation_0
         timeStampNs:CMTimeGetSeconds(timestamp) * rtc::kNumNanosecsPerSec];
  decodedFrame.timeStamp = (int32_t)decodeParams->timestamp;
  decodeParams->callback(decodedFrame);
}

@interface TGRTCVideoDecoderH265RequestKeyframeHolder : NSObject

@property (nonatomic, strong) NSLock *lock;
@property (nonatomic) bool shouldRequestKeyframe;

@end

@implementation TGRTCVideoDecoderH265RequestKeyframeHolder

- (instancetype)init {
    self = [super init];
    if (self != nil) {
        _lock = [[NSLock alloc] init];
    }
    return self;
}

@end

// Decoder.
@implementation TGRTCVideoDecoderH265 {
  CMVideoFormatDescriptionRef _videoFormat;
  VTDecompressionSessionRef _decompressionSession;
  RTCVideoDecoderCallback _callback;
  TGRTCVideoDecoderH265RequestKeyframeHolder *_requestKeyframeHolder;
  TGRTCVideoDecoderRequestKeyframeCallback _requestFrame;
  OSStatus _error;
}

- (instancetype)init {
  if (self = [super init]) {
      _requestKeyframeHolder = [[TGRTCVideoDecoderH265RequestKeyframeHolder alloc] init];
      TGRTCVideoDecoderH265RequestKeyframeHolder *requestKeyframeHolder = _requestKeyframeHolder;
      _requestFrame = ^{
          [requestKeyframeHolder.lock lock];
          requestKeyframeHolder.shouldRequestKeyframe = true;
          [requestKeyframeHolder.lock unlock];
      };
      NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
#ifdef WEBRTC_IOS
      [center addObserver:self
      selector:@selector(handleApplicationDidBecomeActive:)
          name:UIApplicationWillEnterForegroundNotification
        object:[UIApplication sharedApplication]];
#endif
  }

  return self;
}

- (void)dealloc {
  [self destroyDecompressionSession];
  [self setVideoFormat:nullptr];
  [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (NSInteger)startDecodeWithNumberOfCores:(int)numberOfCores {
  return WEBRTC_VIDEO_CODEC_OK;
}

- (void)handleApplicationDidBecomeActive:(NSNotification *)notification {
    __weak TGRTCVideoDecoderH265 *weakSelf = self;
    tgcalls::StaticThreads::getMediaThread()->PostTask([weakSelf]() {
        __strong TGRTCVideoDecoderH265 *strongSelf = weakSelf;
        if (strongSelf == nil) {
            return;
        }
        strongSelf->_videoFormat = nil;
    });
}

- (NSInteger)decode:(RTCEncodedImage*)inputImage
          missingFrames:(BOOL)missingFrames
      codecSpecificInfo:(__nullable id<RTCCodecSpecificInfo>)info
           renderTimeMs:(int64_t)renderTimeMs {
  RTC_DCHECK(inputImage.buffer);

  if (_error != noErr) {
    RTC_LOG(LS_WARNING) << "Last frame decode failed.";
    _error = noErr;
    return WEBRTC_VIDEO_CODEC_ERROR;
  }

  rtc::ScopedCFTypeRef<CMVideoFormatDescriptionRef> inputFormat =
      rtc::ScopedCF(webrtc::CreateH265VideoFormatDescription(
          (uint8_t*)inputImage.buffer.bytes, inputImage.buffer.length));
  if (inputFormat) {
    CMVideoDimensions dimensions =
        CMVideoFormatDescriptionGetDimensions(inputFormat.get());
    RTC_LOG(LS_INFO) << "Resolution: " << dimensions.width << " x "
                     << dimensions.height;
    // Check if the video format has changed, and reinitialize decoder if
    // needed.
    if (!CMFormatDescriptionEqual(inputFormat.get(), _videoFormat)) {
      [self setVideoFormat:inputFormat.get()];
      int resetDecompressionSessionError = [self resetDecompressionSession];
      if (resetDecompressionSessionError != WEBRTC_VIDEO_CODEC_OK) {
        return resetDecompressionSessionError;
      }
    }
  }
  if (!_videoFormat) {
    // We received a frame but we don't have format information so we can't
    // decode it.
    // This can happen after backgrounding. We need to wait for the next
    // sps/pps before we can resume so we request a keyframe by returning an
    // error.
    RTC_LOG(LS_WARNING) << "Missing video format. Frame with sps/pps required.";
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  CMSampleBufferRef sampleBuffer = nullptr;
  if (!webrtc::H265AnnexBBufferToCMSampleBuffer(
          (uint8_t*)inputImage.buffer.bytes, inputImage.buffer.length,
          _videoFormat, &sampleBuffer)) {
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  RTC_DCHECK(sampleBuffer);
  VTDecodeFrameFlags decodeFlags =
      kVTDecodeFrame_EnableAsynchronousDecompression;
  std::unique_ptr<RTCH265FrameDecodeParams> frameDecodeParams;
  frameDecodeParams.reset(
      new RTCH265FrameDecodeParams(_callback, inputImage.timeStamp, _requestFrame));
  OSStatus status = VTDecompressionSessionDecodeFrame(
      _decompressionSession, sampleBuffer, decodeFlags,
      frameDecodeParams.release(), nullptr);
#if defined(WEBRTC_IOS)
  // Re-initialize the decoder if we have an invalid session while the app is
  // active and retry the decode request.
  if (status == kVTInvalidSessionErr &&
      [self resetDecompressionSession] == WEBRTC_VIDEO_CODEC_OK) {
    frameDecodeParams.reset(
        new RTCH265FrameDecodeParams(_callback, inputImage.timeStamp, _requestFrame));
    status = VTDecompressionSessionDecodeFrame(
        _decompressionSession, sampleBuffer, decodeFlags,
        frameDecodeParams.release(), nullptr);
  }
#endif
  CFRelease(sampleBuffer);
  if (status != noErr) {
    RTC_LOG(LS_ERROR) << "Failed to decode frame with code: " << status;
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  
  bool requestKeyframe = false;
    
  [_requestKeyframeHolder.lock lock];
  if (_requestKeyframeHolder.shouldRequestKeyframe) {
    _requestKeyframeHolder.shouldRequestKeyframe = false;
    requestKeyframe = true;
  }
  [_requestKeyframeHolder.lock unlock];
    
  if (requestKeyframe) {
    RTC_LOG(LS_ERROR) << "Decoder asynchronously asked to request keyframe";
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
    
  return WEBRTC_VIDEO_CODEC_OK;
}

- (void)setCallback:(RTCVideoDecoderCallback)callback {
  _callback = callback;
}

- (NSInteger)releaseDecoder {
  // Need to invalidate the session so that callbacks no longer occur and it
  // is safe to null out the callback.
  [self destroyDecompressionSession];
  [self setVideoFormat:nullptr];
  _callback = nullptr;
  return WEBRTC_VIDEO_CODEC_OK;
}

#pragma mark - Private

- (int)resetDecompressionSession {
  [self destroyDecompressionSession];

  // Need to wait for the first SPS to initialize decoder.
  if (!_videoFormat) {
    return WEBRTC_VIDEO_CODEC_OK;
  }

  // Set keys for OpenGL and IOSurface compatibilty, which makes the encoder
  // create pixel buffers with GPU backed memory. The intent here is to pass
  // the pixel buffers directly so we avoid a texture upload later during
  // rendering. This currently is moot because we are converting back to an
  // I420 frame after decode, but eventually we will be able to plumb
  // CVPixelBuffers directly to the renderer.
  // TODO(tkchin): Maybe only set OpenGL/IOSurface keys if we know that that
  // we can pass CVPixelBuffers as native handles in decoder output.
  static size_t const attributesSize = 3;
  CFTypeRef keys[attributesSize] = {
#if defined(WEBRTC_IOS)
    kCVPixelBufferOpenGLESCompatibilityKey,
#elif defined(WEBRTC_MAC)
    kCVPixelBufferOpenGLCompatibilityKey,
#endif
    kCVPixelBufferIOSurfacePropertiesKey,
    kCVPixelBufferPixelFormatTypeKey
  };
  CFDictionaryRef ioSurfaceValue = CreateCFTypeDictionary(nullptr, nullptr, 0);
  int64_t nv12type = kCVPixelFormatType_420YpCbCr8BiPlanarFullRange;
  CFNumberRef pixelFormat =
      CFNumberCreate(nullptr, kCFNumberLongType, &nv12type);
  CFTypeRef values[attributesSize] = {kCFBooleanTrue, ioSurfaceValue,
                                      pixelFormat};
  CFDictionaryRef attributes =
      CreateCFTypeDictionary(keys, values, attributesSize);
  if (ioSurfaceValue) {
    CFRelease(ioSurfaceValue);
    ioSurfaceValue = nullptr;
  }
  if (pixelFormat) {
    CFRelease(pixelFormat);
    pixelFormat = nullptr;
  }
  VTDecompressionOutputCallbackRecord record = {
      tg_h265DecompressionOutputCallback,
      nullptr,
  };
  OSStatus status =
      VTDecompressionSessionCreate(nullptr, _videoFormat, nullptr, attributes,
                                   &record, &_decompressionSession);
  CFRelease(attributes);
  if (status != noErr) {
    [self destroyDecompressionSession];
    return WEBRTC_VIDEO_CODEC_ERROR;
  }
  [self configureDecompressionSession];

  return WEBRTC_VIDEO_CODEC_OK;
}

- (void)configureDecompressionSession {
  RTC_DCHECK(_decompressionSession);
#if defined(WEBRTC_IOS)
  // VTSessionSetProperty(_decompressionSession,
  // kVTDecompressionPropertyKey_RealTime, kCFBooleanTrue);
#endif
}

- (void)destroyDecompressionSession {
  if (_decompressionSession) {
#if defined(WEBRTC_IOS)
    VTDecompressionSessionWaitForAsynchronousFrames(_decompressionSession);
#endif
    VTDecompressionSessionInvalidate(_decompressionSession);
    CFRelease(_decompressionSession);
    _decompressionSession = nullptr;
  }
}

- (void)setVideoFormat:(CMVideoFormatDescriptionRef)videoFormat {
  if (_videoFormat == videoFormat) {
    return;
  }
  if (_videoFormat) {
    CFRelease(_videoFormat);
  }
  _videoFormat = videoFormat;
  if (_videoFormat) {
    CFRetain(_videoFormat);
  }
}

- (NSString*)implementationName {
  return @"VideoToolbox";
}

@end

#endif
