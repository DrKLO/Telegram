#import "VideoSampleBufferView.h"

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

class VideoRendererAdapterImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoRendererAdapterImpl(void (^frameReceived)(std::shared_ptr<webrtc::VideoFrame>)) {
        _frameReceived = [frameReceived copy];
    }
    
    void OnFrame(const webrtc::VideoFrame& nativeVideoFrame) override {
        @autoreleasepool {
            if (_frameReceived) {
                std::shared_ptr<webrtc::VideoFrame> videoFrame = std::make_shared<webrtc::VideoFrame>(nativeVideoFrame);
                _frameReceived(videoFrame);
            }
        }
    }
    
private:
    void (^_frameReceived)(std::shared_ptr<webrtc::VideoFrame>);
};

}

@interface VideoSampleBufferContentView : UIView

@property (nonatomic) bool isPaused;

@end

@implementation VideoSampleBufferContentView

+ (Class)layerClass {
    return [AVSampleBufferDisplayLayer class];
}

- (AVSampleBufferDisplayLayer * _Nonnull)videoLayer {
    return (AVSampleBufferDisplayLayer *)self.layer;
}

@end

@interface VideoSampleBufferViewRenderingContext : NSObject {
    __weak VideoSampleBufferContentView *_sampleBufferView;
    __weak VideoSampleBufferContentView *_cloneTarget;

    CVPixelBufferPoolRef _pixelBufferPool;
    int _pixelBufferPoolWidth;
    int _pixelBufferPoolHeight;

    bool _isBusy;
}

@end

@implementation VideoSampleBufferViewRenderingContext

+ (dispatch_queue_t)sharedQueue {
    static dispatch_queue_t queue;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        queue = dispatch_queue_create("VideoSampleBufferViewRenderingContext", 0);
    });
    return queue;
}

- (instancetype)initWithView:(VideoSampleBufferContentView *)view {
    self = [super init];
    if (self != nil) {
        _sampleBufferView = view;
    }
    return self;
}

- (void)dealloc {
    _isBusy = true;
    if (_pixelBufferPool) {
        CFRelease(_pixelBufferPool);
    }

    void *opaqueReference = (__bridge_retained void *)_sampleBufferView;
    dispatch_async(dispatch_get_main_queue(), ^{
        __strong VideoSampleBufferContentView *object = (__bridge_transfer VideoSampleBufferContentView *)opaqueReference;
        [object description];
    });
}

static bool CopyVideoFrameToNV12PixelBuffer(const webrtc::I420BufferInterface *frameBuffer, CVPixelBufferRef pixelBuffer) {
    if (!frameBuffer) {
        return false;
    }
    RTC_DCHECK(pixelBuffer);
    RTC_DCHECK_EQ(CVPixelBufferGetPixelFormatType(pixelBuffer), kCVPixelFormatType_420YpCbCr8BiPlanarFullRange);
    RTC_DCHECK_EQ(CVPixelBufferGetHeightOfPlane(pixelBuffer, 0), frameBuffer->height());
    RTC_DCHECK_EQ(CVPixelBufferGetWidthOfPlane(pixelBuffer, 0), frameBuffer->width());

    CVReturn cvRet = CVPixelBufferLockBaseAddress(pixelBuffer, 0);
    if (cvRet != kCVReturnSuccess) {
        return false;
    }
    uint8_t *dstY = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0));
    int dstStrideY = (int)CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0);
    uint8_t *dstUV = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1));
    int dstStrideUV = (int)CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 1);
    // Convert I420 to NV12.
    int ret = libyuv::I420ToNV12(frameBuffer->DataY(),
                                 frameBuffer->StrideY(),
                                 frameBuffer->DataU(),
                                 frameBuffer->StrideU(),
                                 frameBuffer->DataV(),
                                 frameBuffer->StrideV(),
                                 dstY,
                                 dstStrideY,
                                 dstUV,
                                 dstStrideUV,
                                 frameBuffer->width(),
                                 frameBuffer->height());
    CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
    if (ret) {
        return false;
    }
    return true;
}

static bool CopyNV12VideoFrameToNV12PixelBuffer(const webrtc::NV12BufferInterface *frameBuffer, CVPixelBufferRef pixelBuffer) {
    if (!frameBuffer) {
        return false;
    }
    RTC_DCHECK(pixelBuffer);
    RTC_DCHECK_EQ(CVPixelBufferGetPixelFormatType(pixelBuffer), kCVPixelFormatType_420YpCbCr8BiPlanarFullRange);
    RTC_DCHECK_EQ(CVPixelBufferGetHeightOfPlane(pixelBuffer, 0), frameBuffer->height());
    RTC_DCHECK_EQ(CVPixelBufferGetWidthOfPlane(pixelBuffer, 0), frameBuffer->width());

    CVReturn cvRet = CVPixelBufferLockBaseAddress(pixelBuffer, 0);
    if (cvRet != kCVReturnSuccess) {
        return false;
    }
    uint8_t *dstY = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 0));
    int dstStrideY = (int)CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 0);
    uint8_t *dstUV = reinterpret_cast<uint8_t *>(CVPixelBufferGetBaseAddressOfPlane(pixelBuffer, 1));
    int dstStrideUV = (int)CVPixelBufferGetBytesPerRowOfPlane(pixelBuffer, 1);
    // Convert I420 to NV12.
    int ret = libyuv::NV12Copy(frameBuffer->DataY(), frameBuffer->StrideY(), frameBuffer->DataUV(), frameBuffer->StrideUV(), dstY, dstStrideY, dstUV, dstStrideUV, frameBuffer->width(), frameBuffer->height());
    CVPixelBufferUnlockBaseAddress(pixelBuffer, 0);
    if (ret) {
        return false;
    }
    return true;
}

- (CVPixelBufferPoolRef)createPixelBufferPoolWithWidth:(int32_t)width height:(int32_t)height pixelFormat:(FourCharCode)pixelFormat maxBufferCount:(int32_t) maxBufferCount {
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

- (CMSampleBufferRef)createSampleBufferFromI420Buffer:(const webrtc::I420BufferInterface *)buffer {
    if (!buffer) {
        return nil;
    }

    NSMutableDictionary *ioSurfaceProperties = [[NSMutableDictionary alloc] init];
    //ioSurfaceProperties[@"IOSurfaceIsGlobal"] = @(true);

    NSMutableDictionary *options = [[NSMutableDictionary alloc] init];
    //options[(__bridge NSString *)kCVPixelBufferBytesPerRowAlignmentKey] = @(buffer.strideY);
    options[(__bridge NSString *)kCVPixelBufferIOSurfacePropertiesKey] = ioSurfaceProperties;

    CVPixelBufferRef pixelBufferRef = nil;

    if (!(_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer->width() && _pixelBufferPoolHeight == buffer->height())) {
        if (_pixelBufferPool) {
            CFRelease(_pixelBufferPool);
            _pixelBufferPool = nil;
        }
        _pixelBufferPool = [self createPixelBufferPoolWithWidth:buffer->width() height:buffer->height() pixelFormat:kCVPixelFormatType_420YpCbCr8BiPlanarFullRange maxBufferCount:10];
        _pixelBufferPoolWidth = buffer->width();
        _pixelBufferPoolHeight = buffer->height();
    }

    if (_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer->width() && _pixelBufferPoolHeight == buffer->height()) {
        CVPixelBufferPoolCreatePixelBufferWithAuxAttributes(kCFAllocatorDefault, _pixelBufferPool, nil, &pixelBufferRef);
    } else {
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            buffer->width(),
            buffer->height(),
            kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            (__bridge CFDictionaryRef)options,
            &pixelBufferRef
        );
    }

    if (pixelBufferRef == nil) {
        return nil;
    }

    CopyVideoFrameToNV12PixelBuffer(buffer, pixelBufferRef);

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

    CFRelease(formatRef);
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

- (CMSampleBufferRef)createSampleBufferFromNV12Buffer:(const webrtc::NV12BufferInterface *)buffer {
    if (!buffer) {
        return nil;
    }

    NSMutableDictionary *ioSurfaceProperties = [[NSMutableDictionary alloc] init];
    //ioSurfaceProperties[@"IOSurfaceIsGlobal"] = @(true);

    NSMutableDictionary *options = [[NSMutableDictionary alloc] init];
    //options[(__bridge NSString *)kCVPixelBufferBytesPerRowAlignmentKey] = @(buffer.strideY);
    options[(__bridge NSString *)kCVPixelBufferIOSurfacePropertiesKey] = ioSurfaceProperties;

    CVPixelBufferRef pixelBufferRef = nil;

    if (!(_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer->width() && _pixelBufferPoolHeight == buffer->height())) {
        if (_pixelBufferPool) {
            CFRelease(_pixelBufferPool);
            _pixelBufferPool = nil;
        }
        _pixelBufferPool = [self createPixelBufferPoolWithWidth:buffer->width() height:buffer->height() pixelFormat:kCVPixelFormatType_420YpCbCr8BiPlanarFullRange maxBufferCount:10];
        _pixelBufferPoolWidth = buffer->width();
        _pixelBufferPoolHeight = buffer->height();
    }

    if (_pixelBufferPool != nil && _pixelBufferPoolWidth == buffer->width() && _pixelBufferPoolHeight == buffer->height()) {
        CVPixelBufferPoolCreatePixelBufferWithAuxAttributes(kCFAllocatorDefault, _pixelBufferPool, nil, &pixelBufferRef);
    } else {
        CVPixelBufferCreate(
            kCFAllocatorDefault,
            buffer->width(),
            buffer->height(),
            kCVPixelFormatType_420YpCbCr8BiPlanarFullRange,
            (__bridge CFDictionaryRef)options,
            &pixelBufferRef
        );
    }

    if (pixelBufferRef == nil) {
        return nil;
    }

    CopyNV12VideoFrameToNV12PixelBuffer(buffer, pixelBufferRef);

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

    CFRelease(formatRef);
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

- (CMSampleBufferRef)createSampleBufferFromPixelBuffer:(CVPixelBufferRef)pixelBufferRef {
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

    CFRelease(formatRef);

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

- (void)renderFrameIfReady:(std::shared_ptr<webrtc::VideoFrame>)frame {
    if (_isBusy) {
        return;
    }
    VideoSampleBufferContentView *sampleBufferView = _sampleBufferView;
    if (!sampleBufferView) {
        return;
    }

    AVSampleBufferDisplayLayer *layer = [sampleBufferView videoLayer];

    VideoSampleBufferContentView *cloneTarget = _cloneTarget;
    __weak AVSampleBufferDisplayLayer *cloneLayer = nil;
    if (cloneTarget) {
        cloneLayer = [cloneTarget videoLayer];
    }

    _isBusy = true;
    dispatch_async([VideoSampleBufferViewRenderingContext sharedQueue], ^{
        __strong AVSampleBufferDisplayLayer *strongCloneLayer = cloneLayer;

        switch (frame->video_frame_buffer()->type()) {
            case webrtc::VideoFrameBuffer::Type::kI420:
            case webrtc::VideoFrameBuffer::Type::kI420A: {
                CMSampleBufferRef sampleBuffer = [self createSampleBufferFromI420Buffer:frame->video_frame_buffer()->GetI420()];
                if (sampleBuffer) {
                    [layer enqueueSampleBuffer:sampleBuffer];
                    [cloneLayer enqueueSampleBuffer:sampleBuffer];

                    if ([layer status] == AVQueuedSampleBufferRenderingStatusFailed) {
                        [layer flush];
                    }
                    if ([cloneLayer status] == AVQueuedSampleBufferRenderingStatusFailed) {
                        [cloneLayer flush];
                    }

                    CFRelease(sampleBuffer);
                }

                break;
            }
            case webrtc::VideoFrameBuffer::Type::kNV12: {
                CMSampleBufferRef sampleBuffer = [self createSampleBufferFromNV12Buffer:(webrtc::NV12BufferInterface *)frame->video_frame_buffer().get()];
                if (sampleBuffer) {
                    [layer enqueueSampleBuffer:sampleBuffer];
                    [cloneLayer enqueueSampleBuffer:sampleBuffer];

                    CFRelease(sampleBuffer);
                }
                break;
            }
            case webrtc::VideoFrameBuffer::Type::kNative: {
                id<RTC_OBJC_TYPE(RTCVideoFrameBuffer)> nativeBuffer = static_cast<webrtc::ObjCFrameBuffer *>(frame->video_frame_buffer().get())->wrapped_frame_buffer();
                if ([nativeBuffer isKindOfClass:[RTC_OBJC_TYPE(RTCCVPixelBuffer) class]]) {
                    RTCCVPixelBuffer *pixelBuffer = (RTCCVPixelBuffer *)nativeBuffer;
                    CMSampleBufferRef sampleBuffer = [self createSampleBufferFromPixelBuffer:pixelBuffer.pixelBuffer];
                    if (sampleBuffer) {
                        [layer enqueueSampleBuffer:sampleBuffer];
                        [cloneLayer enqueueSampleBuffer:sampleBuffer];

                        CFRelease(sampleBuffer);
                    }
                }
                break;
            }
            default: {
                break;
            }
        }

        _isBusy = false;

        void *opaqueReference = (__bridge_retained void *)layer;
        void *cloneLayerReference = (__bridge_retained void *)strongCloneLayer;
        strongCloneLayer = nil;

        dispatch_async(dispatch_get_main_queue(), ^{
            __strong AVSampleBufferDisplayLayer *object = (__bridge_transfer AVSampleBufferDisplayLayer *)opaqueReference;
            object = nil;

            __strong AVSampleBufferDisplayLayer *cloneObject = (__bridge_transfer AVSampleBufferDisplayLayer *)cloneLayerReference;
            cloneObject = nil;
        });
    });
}

- (void)setCloneTarget:(VideoSampleBufferContentView * _Nullable)cloneTarget {
    _cloneTarget = cloneTarget;
}

@end

@protocol ClonePortalView

- (void)setSourceView:(UIView * _Nullable)sourceView;
- (void)setHidesSourceView:(bool)arg1;
- (void)setMatchesAlpha:(bool)arg1;
- (void)setMatchesPosition:(bool)arg1;
- (void)setMatchesTransform:(bool)arg1;

@end

@interface VideoSampleBufferView () {
    VideoSampleBufferContentView *_sampleBufferView;
    VideoSampleBufferViewRenderingContext *_renderingContext;

    std::shared_ptr<webrtc::VideoFrame> _videoFrame;
    std::shared_ptr<webrtc::VideoFrame> _stashedVideoFrame;

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

    __weak VideoSampleBufferView *_cloneTarget;
    UIView<ClonePortalView> *_portalView;
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
        _sink.reset(new VideoRendererAdapterImpl(^(std::shared_ptr<webrtc::VideoFrame> videoFrame) {
            if (!videoFrame) {
                return;
            }

            dispatch_async(dispatch_get_main_queue(), ^{
                __strong VideoSampleBufferView *strongSelf = weakSelf;
                if (strongSelf == nil) {
                    return;
                }

                [strongSelf renderFrame:videoFrame];
            });
        }));
    }
    return self;
}

- (void)dealloc {
}

- (void)setEnabled:(BOOL)enabled {
    if (_enabled != enabled) {
        _enabled = enabled;
        _sampleBufferView.isPaused = !enabled;
    }
}

- (void)setVideoContentMode:(UIViewContentMode)mode {
    _videoContentMode = mode;
}

#pragma mark - Private

- (void)configure {
    _sampleBufferView = [[VideoSampleBufferContentView alloc] init];
    [self addSubview:_sampleBufferView];

    _renderingContext = [[VideoSampleBufferViewRenderingContext alloc] initWithView:_sampleBufferView];

    _videoFrameSize = CGSizeZero;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    CGRect bounds = self.bounds;

    if (!CGRectEqualToRect(_sampleBufferView.frame, bounds)) {
        _sampleBufferView.frame = bounds;
        _portalView.frame = bounds;
    }
    
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

    [self setNeedsLayout];
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

    if (_videoFrame) {
        switch (_videoFrame->rotation()) {
            case webrtc::kVideoRotation_0:
                return RTCVideoRotation_0;
            case webrtc::kVideoRotation_90:
                return RTCVideoRotation_90;
            case webrtc::kVideoRotation_180:
                return RTCVideoRotation_180;
            case webrtc::kVideoRotation_270:
                return RTCVideoRotation_270;
            default:
                return RTCVideoRotation_0;
        }
    } else {
        return RTCVideoRotation_0;
    }
}

- (void)setSize:(CGSize)size {
    assert([NSThread isMainThread]);
}

- (void)renderFrame:(std::shared_ptr<webrtc::VideoFrame>)frame {
    [self renderFrameInternal:frame skipRendering:false];
    VideoSampleBufferView *cloneTarget = _cloneTarget;
    if (cloneTarget) {
        [cloneTarget renderFrameInternal:frame skipRendering:true];
    }
}

- (void)renderFrameInternal:(std::shared_ptr<webrtc::VideoFrame>)frame skipRendering:(bool)skipRendering {
    assert([NSThread isMainThread]);

    CGSize size = CGSizeMake(frame->width(), frame->height());
    if (!CGSizeEqualToSize(size, _currentSize)) {
        _currentSize = size;
        [self setSize:size];
    }

    int mappedValue = 0;
    switch (RTCVideoRotation(frame->rotation())) {
        case RTCVideoRotation_90: {
            mappedValue = 1;
            break;
        }
        case RTCVideoRotation_180: {
            mappedValue = 2;
            break;
        }
        case RTCVideoRotation_270: {
            mappedValue = 3;
            break;
        }
        default: {
            mappedValue = 0;
            break;
        }
    }
    [self setInternalOrientationAndSize:mappedValue size:size];
    
    if (!_firstFrameReceivedReported && _onFirstFrameReceived) {
        _firstFrameReceivedReported = true;
        _onFirstFrameReceived();
    }
               
    if (!self.isEnabled) {
        return;
    }

    if (!frame) {
        return;
    }

    if (_isWaitingForLayoutFrameCount > 0) {
        _stashedVideoFrame = frame;
        _isWaitingForLayoutFrameCount--;
        return;
    }
    if (!_didStartWaitingForLayout) {
        if (_videoFrame && _videoFrame->width() > 0 && _videoFrame->height() > 0 && frame->width() > 0 && frame->height() > 0) {
            float previousAspect = ((float)_videoFrame->width()) / ((float)_videoFrame->height());
            float updatedAspect = ((float)frame->width()) / ((float)frame->height());
            if ((previousAspect < 1.0f) != (updatedAspect < 1.0f)) {
                _stashedVideoFrame = frame;
                _didStartWaitingForLayout = true;
                _isWaitingForLayoutFrameCount = 5;
                return;
            }
        }
    }

    _videoFrame = frame;

    if (!skipRendering) {
        [_renderingContext renderFrameIfReady:frame];
    }
}

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink {
    assert([NSThread isMainThread]);
    
    return _sink;
}

- (void)addFrame:(const webrtc::VideoFrame&)frame {
    std::shared_ptr<webrtc::VideoFrame> videoFrame = std::make_shared<webrtc::VideoFrame>(frame);
    [self renderFrame:videoFrame];
}

static NSString * _Nonnull shiftString(NSString *string, int key) {
    NSMutableString *result = [[NSMutableString alloc] init];

    for (int i = 0; i < (int)[string length]; i++) {
        unichar c = [string characterAtIndex:i];
        c += key;
        [result appendString:[NSString stringWithCharacters:&c length:1]];
    }

    return result;
}

/*- (void)addAsCloneTarget:(VideoSampleBufferView *)sourceView {
    if (_portalView) {
        [_portalView setSourceView:nil];
        [_portalView removeFromSuperview];
    }

    if (!sourceView) {
        return;
    }
    static Class portalViewClass = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        portalViewClass = NSClassFromString(@"_UIPortalView");
    });
    if (portalViewClass) {
        _portalView = (UIView<ClonePortalView> *)[[portalViewClass alloc] init];
        _portalView.frame = sourceView->_sampleBufferView.frame;
        _portalView.backgroundColor = [UIColor redColor];
        [_portalView setSourceView:sourceView->_sampleBufferView];
        [_portalView setHidesSourceView:true];
        [_portalView setMatchesAlpha:false];
        [_portalView setMatchesPosition:false];
        [_portalView setMatchesTransform:false];

        [self addSubview:_portalView];
    }
}*/

- (void)setCloneTarget:(VideoSampleBufferView * _Nullable)cloneTarget {
    _cloneTarget = cloneTarget;
    if (cloneTarget) {
        [_renderingContext setCloneTarget:cloneTarget->_sampleBufferView];
        //[cloneTarget addAsCloneTarget:self];
    }
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

@end
