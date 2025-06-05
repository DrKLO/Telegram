#include "CustomExternalCapturer.h"

#import <AVFoundation/AVFoundation.h>

#include "rtc_base/logging.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrameBuffer.h"
#import "TGRTCCVPixelBuffer.h"
#import "sdk/objc/native/src/objc_video_track_source.h"
#import "sdk/objc/native/src/objc_frame_buffer.h"
//#import "api/video_track_source_proxy.h"

#import "helpers/UIDevice+RTCDevice.h"

#import "helpers/AVCaptureSession+DevicePosition.h"
#import "helpers/RTCDispatcher+Private.h"
#import "base/RTCVideoFrame.h"

#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "third_party/libyuv/include/libyuv.h"
#include "pc/video_track_source_proxy.h"

#include "DarwinVideoSource.h"

static const int64_t kNanosecondsPerSecond = 1000000000;

static tgcalls::DarwinVideoTrackSource *getObjCVideoSource(const webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> nativeSource) {
    webrtc::VideoTrackSourceProxy *proxy_source =
    static_cast<webrtc::VideoTrackSourceProxy *>(nativeSource.get());
    return static_cast<tgcalls::DarwinVideoTrackSource *>(proxy_source->internal());
}

@interface CustomExternalCapturer () {
    webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
}

@end

@implementation CustomExternalCapturer

- (instancetype)initWithSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source {
    self = [super init];
    if (self != nil) {
        _source = source;
    }
    return self;
}

- (void)dealloc {
}

+ (void)passPixelBuffer:(CVPixelBufferRef)pixelBuffer sampleBufferReference:(CMSampleBufferRef)sampleBufferReference rotation:(RTCVideoRotation)rotation toSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source croppingBuffer:(std::vector<uint8_t> &)croppingBuffer {
    TGRTCCVPixelBuffer *rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer];
    if (sampleBufferReference) {
        [rtcPixelBuffer storeSampleBufferReference:sampleBufferReference];
    }
    rtcPixelBuffer.deviceRelativeVideoRotation = -1;

    int width = rtcPixelBuffer.width;
    int height = rtcPixelBuffer.height;

    width -= width % 4;
    height -= height % 4;

    if (width != rtcPixelBuffer.width || height != rtcPixelBuffer.height) {
        CVPixelBufferRef outputPixelBufferRef = NULL;
        OSType pixelFormat = CVPixelBufferGetPixelFormatType(rtcPixelBuffer.pixelBuffer);
        CVPixelBufferCreate(NULL, width, height, pixelFormat, NULL, &outputPixelBufferRef);
        if (outputPixelBufferRef) {
            int bufferSize = [rtcPixelBuffer bufferSizeForCroppingAndScalingToWidth:width height:height];
            if (croppingBuffer.size() < bufferSize) {
                croppingBuffer.resize(bufferSize);
            }
            if ([rtcPixelBuffer cropAndScaleTo:outputPixelBufferRef withTempBuffer:croppingBuffer.data()]) {
                rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:outputPixelBufferRef];
                rtcPixelBuffer.deviceRelativeVideoRotation = -1;
            }
            CVPixelBufferRelease(outputPixelBufferRef);
        }
    }

    int64_t timeStampNs = CACurrentMediaTime() * kNanosecondsPerSecond;
    RTCVideoFrame *videoFrame = [[RTCVideoFrame alloc] initWithBuffer:(id<RTCVideoFrameBuffer>)[rtcPixelBuffer toI420] rotation:rotation timeStampNs:timeStampNs];

    getObjCVideoSource(source)->OnCapturedFrame(videoFrame);
}

@end
