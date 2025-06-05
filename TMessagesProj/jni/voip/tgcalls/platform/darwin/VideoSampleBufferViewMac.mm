#import "VideoSampleBufferViewMac.h"

#import <CoreVideo/CoreVideo.h>

#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "TGRTCCVPixelBuffer.h"
#include "sdk/objc/native/api/video_frame.h"
#include "sdk/objc/native/src/objc_frame_buffer.h"
#include "sdk/objc/base/RTCI420Buffer.h"

#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"
#import "rtc_base/time_utils.h"

#import "RTCMTLI420Renderer.h"
#import "RTCMTLNV12Renderer.h"
#import "RTCMTLRGBRenderer.h"

#include "libyuv.h"

#define MTKViewClass NSClassFromString(@"MTKView")
#define RTCMTLNV12RendererClass NSClassFromString(@"RTCMTLNV12Renderer")
#define RTCMTLI420RendererClass NSClassFromString(@"RTCMTLI420Renderer")
#define RTCMTLRGBRendererClass NSClassFromString(@"RTCMTLRGBRenderer")

namespace {

static RTCVideoFrame *customToObjCVideoFrame(const webrtc::VideoFrame &frame, RTCVideoRotation &rotation) {
    rotation = RTCVideoRotation(frame.rotation());
    RTCVideoFrame *videoFrame =
    [[RTCVideoFrame alloc] initWithBuffer:webrtc::ToObjCVideoFrameBuffer(frame.video_frame_buffer())
                                 rotation:rotation
                              timeStampNs:frame.timestamp_us() * rtc::kNumNanosecsPerMicrosec];
    videoFrame.timeStamp = frame.timestamp();

    return videoFrame;
}

class VideoRendererAdapterImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoRendererAdapterImpl(void (^frameReceived)(CGSize, RTCVideoFrame *, RTCVideoRotation)) {
        _frameReceived = [frameReceived copy];
    }
    
    void OnFrame(const webrtc::VideoFrame& nativeVideoFrame) override {
        @autoreleasepool {
            RTCVideoRotation rotation = RTCVideoRotation_90;
            RTCVideoFrame* videoFrame = customToObjCVideoFrame(nativeVideoFrame, rotation);
            
            //CGSize currentSize = (videoFrame.rotation % 180 == 0) ? CGSizeMake(videoFrame.width, videoFrame.height) : CGSizeMake(videoFrame.height, videoFrame.width);
            CGSize currentSize = CGSizeMake(videoFrame.width, videoFrame.height);
            
            if (_frameReceived) {
                _frameReceived(currentSize, videoFrame, rotation);
            }
        }
    }
    
private:
    void (^_frameReceived)(CGSize, RTCVideoFrame *, RTCVideoRotation);
};

}

@interface VideoSampleBufferView () {
    AVSampleBufferDisplayLayer *_sampleBufferLayer;

    RTCVideoFrame *_videoFrame;
    RTCVideoFrame *_stashedVideoFrame;

    int _isWaitingForLayoutFrameCount;
    bool _didStartWaitingForLayout;
    CGSize _videoFrameSize;
    int64_t _lastFrameTimeNs;
    
    CGSize _currentSize;
    std::shared_ptr<VideoRendererAdapterImpl> _sink;
    
    void (^_onFirstFrameReceived)();
    bool _firstFrameReceivedReported;
    
    void (^_onOrientationUpdated)(int, CGFloat);
    
    void (^_onIsMirroredUpdated)(bool);
    
    bool _didSetShouldBeMirrored;
    bool _shouldBeMirrored;

    CVPixelBufferPoolRef _pixelBufferPool;
    int _pixelBufferPoolWidth;
    int _pixelBufferPoolHeight;
}

@end

@implementation VideoSampleBufferView

- (instancetype)initWithFrame:(CGRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self) {
        [self configure];

        _enabled = true;
        _currentSize = CGSizeZero;
        _rotationOverride = @(RTCVideoRotation_0);
        
        __weak VideoSampleBufferView *weakSelf = self;
        _sink.reset(new VideoRendererAdapterImpl(^(CGSize size, RTCVideoFrame *videoFrame, RTCVideoRotation rotation) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong VideoSampleBufferView *strongSelf = weakSelf;
                if (strongSelf == nil) {
                    return;
                }
                if (!CGSizeEqualToSize(size, strongSelf->_currentSize)) {
                    strongSelf->_currentSize = size;
                    [strongSelf setSize:size];
                }
                
                int mappedValue = 0;
                switch (rotation) {
                    case RTCVideoRotation_90:
                        mappedValue = 1;
                        break;
                    case RTCVideoRotation_180:
                        mappedValue = 2;
                        break;
                    case RTCVideoRotation_270:
                        mappedValue = 3;
                        break;
                    default:
                        mappedValue = 0;
                        break;
                }
                [strongSelf setInternalOrientationAndSize:mappedValue size:size];
                
                [strongSelf renderFrame:videoFrame];
            });
        }));
    }
    return self;
}

- (void)dealloc {
    if (_pixelBufferPool) {
        CFRelease(_pixelBufferPool);
    }
}

- (void)setEnabled:(BOOL)enabled {
    _enabled = enabled;
}

- (void)setVideoContentMode:(CALayerContentsGravity)mode {
    _videoContentMode = mode;
}

#pragma mark - Private

- (void)configure {
    self.wantsLayer = YES;
    _sampleBufferLayer = [[AVSampleBufferDisplayLayer alloc] init];
    self.layer = _sampleBufferLayer;
//    [self.layer addSublayer:_sampleBufferLayer];

    
    _videoFrameSize = CGSizeZero;
}

- (void)layout {
    [super layout];
    
    CGRect bounds = self.bounds;
    [CATransaction begin];
    [CATransaction setAnimationDuration:0];
    [CATransaction setDisableActions:true];
    _sampleBufferLayer.frame = bounds;
    [CATransaction commit];
    
    if (_didStartWaitingForLayout) {
        _didStartWaitingForLayout = false;
        _isWaitingForLayoutFrameCount = 0;
        if (_stashedVideoFrame != nil) {
            _videoFrame = _stashedVideoFrame;
            _stashedVideoFrame = nil;
        }
    }
}


#pragma mark -

- (void)setRotationOverride:(NSValue *)rotationOverride {
    _rotationOverride = rotationOverride;

    [self setNeedsLayout:YES];
}

- (RTCVideoRotation)frameRotation {
    if (_rotationOverride) {
        RTCVideoRotation rotation;
        if (@available(iOS 11, *)) {
            [_rotationOverride getValue:&rotation size:sizeof(rotation)];
        } else {
            [_rotationOverride getValue:&rotation];
        }
        return rotation;
    }
    
    return _videoFrame.rotation;
}

#pragma mark - RTCVideoRenderer

bool CopyVideoFrameToNV12PixelBuffer(id<RTC_OBJC_TYPE(RTCI420Buffer)> frameBuffer,
                                     CVPixelBufferRef pixelBuffer) {
    RTC_DCHECK(pixelBuffer);
    RTC_DCHECK_EQ(CVPixelBufferGetPixelFormatType(pixelBuffer), kCVPixelFormatType_420YpCbCr8BiPlanarFullRange);
    RTC_DCHECK_EQ(CVPixelBufferGetHeightOfPlane(pixelBuffer, 0), frameBuffer.height);
    RTC_DCHECK_EQ(CVPixelBufferGetWidthOfPlane(pixelBuffer, 0), frameBuffer.width);

    CVReturn cvRet = CVPixelBufferLockBaseAddress(pixelBuffer, 0);
    if (cvRet != kCVReturnSuccess) {
        return false;
    }
    uint8_t *dstY = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0));
    size_t dstStrideY = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0);
    uint8_t *dstUV = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1));
    size_t dstStrideUV = CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 1);

    int ret = libyuv::I420ToNV12(frameBuffer.dataY,
                                 frameBuffer.strideY,
                                 frameBuffer.dataU,
                                 frameBuffer.strideU,
                                 frameBuffer.dataV,
                                 frameBuffer.strideV,
                                 dstY,
                                 (int)dstStrideY,
                                 dstUV,
                                 (int)dstStrideUV,
                                 frameBuffer.width,
                                 frameBuffer.height);
    CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
    if (ret) {
        return false;
    }
    return true;
}

+ (CVPixelBufferPoolRef)createPixelBufferPoolWithWidth:(int32_t)width height:(int32_t)height pixelFormat:(FourCharCode)pixelFormat maxBufferCount:(int32_t) maxBufferCount {
    CVPixelBufferPoolRef outputPool = NULL;

    NSDictionary *sourcePixelBufferOptions = @{
        (id)kCVPixelBufferPixelFormatTypeKey : @(pixelFormat),
        (id)kCVPixelBufferWidthKey : @(width),
        (id)kCVPixelBufferHeightKey : @(height),
        (id)kCVPixelBufferIOSurfacePropertiesKey : @{}
    };

    NSDictionary *pixelBufferPoolOptions = @{ (id)kCVPixelBufferPoolMinimumBufferCountKey : @(maxBufferCount) };
    CVPixelBufferPoolCreate(kCFAllocatorDefault, (__bridge CFDictionaryRef)pixelBufferPoolOptions, (__bridge CFDictionaryRef)sourcePixelBufferOptions, &outputPool);

    return outputPool;
}

- (CMSampleBufferRef)createSampleBufferFromBuffer:(id<RTC_OBJC_TYPE(RTCI420Buffer)>)buffer {
    NSMutableDictionary *ioSurfaceProperties = [[NSMutableDictionary alloc] init];
    //ioSurfaceProperties[@"IOSurfaceIsGlobal"] = @(true);

    NSMutableDictionary *options = [[NSMutableDictionary alloc] init];
    //options[(__bridge NSString *)kCVPixelBufferBytesPerRowAlignmentKey] = @(buffer.strideY);
    options[(__bridge NSString *)kCVPixelBufferIOSurfacePropertiesKey] = ioSurfaceProperties;

    CVPixelBufferRef pixelBufferRef = nil;

    if (!(_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer.width && _pixelBufferPoolHeight == buffer.height)) {
        if (_pixelBufferPool) {
            CFRelease(_pixelBufferPool);
            _pixelBufferPool = nil;
        }
        _pixelBufferPool = [VideoSampleBufferView createPixelBufferPoolWithWidth:buffer.width height:buffer.height pixelFormat:kCVPixelFormatType_420YpCbCr8BiPlanarFullRange maxBufferCount:10];
        _pixelBufferPoolWidth = buffer.width;
        _pixelBufferPoolHeight = buffer.height;
    }

    if (_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer.width && _pixelBufferPoolHeight == buffer.height) {
        CVPixelBufferPoolCreatePixelBufferWithAuxAttributes(kCFAllocatorDefault, _pixelBufferPool, nil, &pixelBufferRef);
    } else {
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            buffer.width,
            buffer.height,
            kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            (__bridge CFDictionaryRef)options,
            &pixelBufferRef
        );
    }

    if (pixelBufferRef == nil) {
        return nil;
    }

    CopyVideoFrameToNV12PixelBuffer([buffer toI420], pixelBufferRef);

    CMVideoFormatDescriptionRef formatRef = nil;
    OSStatus status = CMVideoFormatDescriptionCreateForImageBuffer(kCFAllocatorDefault, pixelBufferRef, &formatRef);
    if (status != 0) {
        return nil;
    }
    if (formatRef == nil) {
        return nil;
    }

    CMSampleTimingInfo timingInfo;
    timingInfo.duration = CMTimeMake(1, 30);
    timingInfo.presentationTimeStamp = CMTimeMake(0, 30);
    timingInfo.decodeTimeStamp = CMTimeMake(0, 30);

    CMSampleBufferRef sampleBuffer = nil;
    OSStatus bufferStatus = CMSampleBufferCreateReadyWithImageBuffer(kCFAllocatorDefault, pixelBufferRef, formatRef, &timingInfo, &sampleBuffer);

    CFRelease(pixelBufferRef);

    if (bufferStatus != noErr) {
        return nil;
    }
    if (sampleBuffer == nil) {
        return nil;
    }

    NSArray *attachments = (__bridge NSArray *)CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, true);
    NSMutableDictionary *dict = (NSMutableDictionary *)attachments[0];

    dict[(__bridge NSString *)kCMSampleAttachmentKey_DisplayImmediately] = @(true);

    return sampleBuffer;
}

- (void)setSize:(CGSize)size {
    assert([NSThread isMainThread]);
}

- (void)renderFrame:(nullable RTCVideoFrame *)frame {
    assert([NSThread isMainThread]);
    
    if (!_firstFrameReceivedReported && _onFirstFrameReceived) {
        _firstFrameReceivedReported = true;
        _onFirstFrameReceived();
    }
               
    if (!self.isEnabled) {
        return;
    }
    
    if (frame == nil) {
        RTCLogInfo(@"Incoming frame is nil. Exiting render callback.");
        return;
    }
    if (_isWaitingForLayoutFrameCount > 0) {
        _stashedVideoFrame = frame;
        _isWaitingForLayoutFrameCount--;
        return;
    }
    if (!_didStartWaitingForLayout) {
        if (_videoFrame != nil && _videoFrame.width > 0 && _videoFrame.height > 0 && frame.width > 0 && frame.height > 0) {
            float previousAspect = ((float)_videoFrame.width) / ((float)_videoFrame.height);
            float updatedAspect = ((float)frame.width) / ((float)frame.height);
            if ((previousAspect < 1.0f) != (updatedAspect < 1.0f)) {
                _stashedVideoFrame = frame;
                _didStartWaitingForLayout = true;
                _isWaitingForLayoutFrameCount = 5;
                return;
            }
        }
    }
    _videoFrame = frame;

    id<RTC_OBJC_TYPE(RTCI420Buffer)> buffer = [frame.buffer toI420];
    CMSampleBufferRef sampleBuffer = [self createSampleBufferFromBuffer:buffer];
    if (sampleBuffer) {
        [_sampleBufferLayer enqueueSampleBuffer:sampleBuffer];

        CFRelease(sampleBuffer);
    }
}

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink {
    assert([NSThread isMainThread]);
    
    return _sink;
}

- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived {
    _onFirstFrameReceived = [onFirstFrameReceived copy];
    _firstFrameReceivedReported = false;
}

- (void)setInternalOrientationAndSize:(int)internalOrientation size:(CGSize)size {
    CGFloat aspect = 1.0f;
    if (size.width > 1.0f && size.height > 1.0f) {
        aspect = size.width / size.height;
    }
    if (_internalOrientation != internalOrientation || ABS(_internalAspect - aspect) > 0.001) {
        RTCLogInfo(@"VideoSampleBufferView@%lx orientation: %d, aspect: %f", (intptr_t)self, internalOrientation, (float)aspect);
        
        _internalOrientation = internalOrientation;
        _internalAspect = aspect;
        if (_onOrientationUpdated) {
            _onOrientationUpdated(internalOrientation, aspect);
        }
    }
}

- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int, CGFloat))onOrientationUpdated {
    _onOrientationUpdated = [onOrientationUpdated copy];
}

- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated {
    _onIsMirroredUpdated = [onIsMirroredUpdated copy];
}

+(BOOL)isAvailable {
    return YES;
}

@end
