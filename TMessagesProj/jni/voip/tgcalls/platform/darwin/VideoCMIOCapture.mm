//
//  VideoCMIOCapture.m
//  TgVoipWebrtc
//
//  Created by Mikhail Filimonov on 21.06.2021.
//  Copyright © 2021 Mikhail Filimonov. All rights reserved.
//

#import "VideoCMIOCapture.h"
#import "TGCMIODevice.h"
#import "TGCMIOCapturer.h"
#import <VideoToolbox/VideoToolbox.h>
#import  "TGRTCCVPixelBuffer.h"
#include "rtc_base/logging.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"
#import "sdk/objc/native/src/objc_video_track_source.h"
#import "sdk/objc/native/src/objc_frame_buffer.h"

#import <CoreMediaIO/CMIOHardware.h>

#import "helpers/AVCaptureSession+DevicePosition.h"
#import "helpers/RTCDispatcher+Private.h"
#import "base/RTCVideoFrame.h"

#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "pc/video_track_source_proxy.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "third_party/libyuv/include/libyuv.h"
#include "DarwinVideoSource.h"

struct MTLFrameSize {
    int width = 0;
    int height = 0;
};

MTLFrameSize AspectFitted(MTLFrameSize from, MTLFrameSize to) {
    double scale = std::min(
        from.width / std::max(1., double(to.width)),
        from.height / std::max(1., double(to.height)));
    return {
        int(std::ceil(to.width * scale)),
        int(std::ceil(to.height * scale))
    };
}

static const int64_t kNanosecondsPerSecond = 1000000000;

@interface VideoCMIOCapture ()
-(void)applyPixelBuffer:(CVPixelBufferRef)pixelBuffer timeStampNs:(int64_t)timeStampNs;
@end


void decompressionSessionDecodeFrameCallback(void *decompressionOutputRefCon,
                                             void *sourceFrameRefCon,
                                             OSStatus status,
                                             VTDecodeInfoFlags infoFlags,
                                             CVImageBufferRef imageBuffer,
                                             CMTime presentationTimeStamp,
                                             CMTime presentationDuration)
{
    VideoCMIOCapture *manager = (__bridge VideoCMIOCapture *)decompressionOutputRefCon;

    if (status == noErr)
    {
        [manager applyPixelBuffer:imageBuffer timeStampNs: CMTimeGetSeconds(presentationTimeStamp) * kNanosecondsPerSecond];
    }
}


static tgcalls::DarwinVideoTrackSource *getObjCVideoSource(const rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> nativeSource) {
    webrtc::VideoTrackSourceProxy *proxy_source =
    static_cast<webrtc::VideoTrackSourceProxy *>(nativeSource.get());
    return static_cast<tgcalls::DarwinVideoTrackSource *>(proxy_source->internal());
}

@implementation VideoCMIOCapture
{
    TGCMIOCapturer *_capturer;
    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _uncroppedSink;
    std::function<void ()> _onFatalError;

    
    BOOL _hadFatalError;
    BOOL _isRunning;
    BOOL _shouldBeMirrored;
    VTDecompressionSessionRef _decompressionSession;
    
}


- (void)start  {
    
    __weak VideoCMIOCapture *weakSelf = self;
    
    [_capturer start:^(CMSampleBufferRef sampleBuffer) {
        [weakSelf apply:sampleBuffer];
    }];
}


-(void)apply:(CMSampleBufferRef)sampleBuffer {
    if (CMSampleBufferGetNumSamples(sampleBuffer) != 1 || !CMSampleBufferIsValid(sampleBuffer) ||
        !CMSampleBufferDataIsReady(sampleBuffer)) {
        return;
    }
    
    CMVideoFormatDescriptionRef formatDesc = CMSampleBufferGetFormatDescription(sampleBuffer);

    CVPixelBufferRef pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
    if (pixelBuffer == nil) {
        if (_decompressionSession == nil) {
            [self createDecompSession:formatDesc];
        }
        [self render:sampleBuffer];
        return;
    }
    
    
    int64_t timeStampNs = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) *
    kNanosecondsPerSecond;

    
    [self applyPixelBuffer:pixelBuffer timeStampNs: timeStampNs];
    
}

-(void)applyPixelBuffer:(CVPixelBufferRef)pixelBuffer timeStampNs:(int64_t)timeStampNs {
    
    int width = (int)CVPixelBufferGetWidth(pixelBuffer);
    int height = (int)CVPixelBufferGetHeight(pixelBuffer);

    MTLFrameSize fittedSize = AspectFitted({ 1280, 720 }, { width, height });
    
    fittedSize.width -= (fittedSize.width % 4);
    fittedSize.height -= (fittedSize.height % 4);

    TGRTCCVPixelBuffer *rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer adaptedWidth:fittedSize.width adaptedHeight:fittedSize.height cropWidth:width cropHeight:height cropX:0 cropY:0];
    
    rtcPixelBuffer.shouldBeMirrored = _shouldBeMirrored;
    
    RTCVideoFrame *videoFrame = [[RTCVideoFrame alloc] initWithBuffer:rtcPixelBuffer
                                                             rotation:RTCVideoRotation_0
                                                          timeStampNs:timeStampNs];
    
    if (_uncroppedSink) {
        const int64_t timestamp_us = timeStampNs / rtc::kNumNanosecsPerMicrosec;

        rtc::scoped_refptr<webrtc::VideoFrameBuffer> buffer;
        buffer = new rtc::RefCountedObject<webrtc::ObjCFrameBuffer>(videoFrame.buffer);

        webrtc::VideoRotation rotation = static_cast<webrtc::VideoRotation>(videoFrame.rotation);

        _uncroppedSink->OnFrame(webrtc::VideoFrame::Builder()
                                .set_video_frame_buffer(buffer)
                                .set_rotation(rotation)
                                .set_timestamp_us(timestamp_us)
                                .build());
    }
    
    getObjCVideoSource(_source)->OnCapturedFrame(videoFrame);
}

- (void)stop {
    [_capturer stop];
}
- (void)setIsEnabled:(bool)isEnabled {
    
}
- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink {
    self->_uncroppedSink = sink;
}
- (void)setPreferredCaptureAspectRatio:(float)aspectRatio {
    
}
- (void)setOnFatalError:(std::function<void()>)error {
    if (!self->_hadFatalError) {
      _onFatalError = std::move(error);
    } else if (error) {
      error();
    }
}
- (void)setOnPause:(std::function<void(bool)>)pause {
    
}

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source {
    self = [super init];
    if (self != nil) {
        _source = source;
    }
    return self;
}
- (void)setupCaptureWithDevice:(AVCaptureDevice *)device {
    
    _shouldBeMirrored = NO;
    _capturer = [[TGCMIOCapturer alloc] initWithDeviceId:device];
}

- (void) render:(CMSampleBufferRef)sampleBuffer
{
    VTDecodeFrameFlags flags = kVTDecodeFrame_EnableAsynchronousDecompression | kVTDecodeFrame_1xRealTimePlayback;
    VTDecodeInfoFlags flagOut;
    NSDate* currentTime = [NSDate date];
    VTDecompressionSessionDecodeFrame(_decompressionSession, sampleBuffer, flags,
                                      (void*)CFBridgingRetain(currentTime), &flagOut);
}

-(void) createDecompSession:(CMVideoFormatDescriptionRef)formatDesc
{
    if (_decompressionSession) {
        CFRelease(_decompressionSession);
    }
    _decompressionSession = NULL;
    VTDecompressionOutputCallbackRecord callBackRecord;
    callBackRecord.decompressionOutputCallback = decompressionSessionDecodeFrameCallback;

    callBackRecord.decompressionOutputRefCon = (__bridge void *)self;

    

    VTDecompressionSessionCreate(NULL, formatDesc, NULL,
                                                    NULL,
                                                    &callBackRecord, &_decompressionSession);
}

-(void)dealloc {
    if (_decompressionSession) {
        CFRelease(_decompressionSession);
    }
}


@end
