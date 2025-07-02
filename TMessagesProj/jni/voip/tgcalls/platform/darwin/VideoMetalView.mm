#import "VideoMetalView.h"

#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>

#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "TGRTCCVPixelBuffer.h"
#include "sdk/objc/native/api/video_frame.h"
#include "sdk/objc/native/src/objc_frame_buffer.h"

#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"
#import "rtc_base/time_utils.h"

#import "RTCMTLI420Renderer.h"
#import "RTCMTLNV12Renderer.h"
#import "RTCMTLRGBRenderer.h"

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

@interface VideoMetalView () <MTKViewDelegate> {
    RTCMTLI420Renderer *_rendererI420;
    RTCMTLNV12Renderer *_rendererNV12;
    RTCMTLRGBRenderer *_rendererRGB;
    MTKView *_metalView;
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
    bool _shouldBeMirroredVertically;

    __weak VideoMetalView *_cloneView;
}

@end

@implementation VideoMetalView

+ (bool)isSupported {
    static bool value;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        value = device != nil;
    });
    return value;
}

- (instancetype)initWithFrame:(CGRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self) {
        [self configure];
        
        _currentSize = CGSizeZero;
        _rotationOverride = @(RTCVideoRotation_0);
        
        __weak VideoMetalView *weakSelf = self;
        _sink.reset(new VideoRendererAdapterImpl(^(CGSize size, RTCVideoFrame *videoFrame, RTCVideoRotation rotation) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong VideoMetalView *strongSelf = weakSelf;
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

                VideoMetalView *cloneView = strongSelf->_cloneView;
                if (cloneView) {
                    if (!CGSizeEqualToSize(size, cloneView->_currentSize)) {
                        cloneView->_currentSize = size;
                        [cloneView setSize:size];
                    }

                    [cloneView setInternalOrientationAndSize:mappedValue size:size];
                    [cloneView renderFrame:videoFrame];
                }
            });
        }));
    }
    return self;
}

- (void)dealloc {
    _sink.reset();
}

- (BOOL)isEnabled {
    return !_metalView.paused;
}

- (void)setEnabled:(BOOL)enabled {
    _metalView.paused = !enabled;
}

- (UIViewContentMode)videoContentMode {
    return _metalView.contentMode;
}

- (void)setVideoContentMode:(UIViewContentMode)mode {
    _metalView.contentMode = mode;
}

#pragma mark - Private

+ (BOOL)isMetalAvailable {
    return MTLCreateSystemDefaultDevice() != nil;
}

+ (MTKView *)createMetalView:(CGRect)frame {
    return [[MTKViewClass alloc] initWithFrame:frame];
}

+ (RTCMTLNV12Renderer *)createNV12Renderer {
    return [[RTCMTLNV12RendererClass alloc] init];
}

+ (RTCMTLI420Renderer *)createI420Renderer {
    return [[RTCMTLI420RendererClass alloc] init];
}

+ (RTCMTLRGBRenderer *)createRGBRenderer {
    return [[RTCMTLRGBRenderer alloc] init];
}

- (void)configure {
    NSAssert([VideoMetalView isMetalAvailable], @"Metal not availiable on this device");
    
    _metalView = [VideoMetalView createMetalView:self.bounds];
    _metalView.delegate = self;
    _metalView.contentMode = UIViewContentModeScaleToFill;
    _metalView.preferredFramesPerSecond = 30;
    [self addSubview:_metalView];
    _videoFrameSize = CGSizeZero;
}

- (void)setMultipleTouchEnabled:(BOOL)multipleTouchEnabled {
    [super setMultipleTouchEnabled:multipleTouchEnabled];
    _metalView.multipleTouchEnabled = multipleTouchEnabled;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    CGRect bounds = self.bounds;
    _metalView.frame = bounds;
    if (!CGSizeEqualToSize(_videoFrameSize, CGSizeZero)) {
        _metalView.drawableSize = [self drawableSize];
    } else {
        _metalView.drawableSize = bounds.size;
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

#pragma mark - MTKViewDelegate methods

- (void)drawInMTKView:(nonnull MTKView *)view {
    NSAssert(view == _metalView, @"Receiving draw callbacks from foreign instance.");
    RTCVideoFrame *videoFrame = _videoFrame;
    // Skip rendering if we've already rendered this frame.
    if (!videoFrame || videoFrame.timeStampNs == _lastFrameTimeNs) {
        return;
    }
    
    if (CGRectIsEmpty(view.bounds)) {
        return;
    }
    
    RTCMTLRenderer *renderer;
    if ([videoFrame.buffer isKindOfClass:[RTCCVPixelBuffer class]]) {
        RTCCVPixelBuffer *buffer = (RTCCVPixelBuffer*)videoFrame.buffer;
        
        if ([buffer isKindOfClass:[TGRTCCVPixelBuffer class]]) {
            bool shouldBeMirrored = ((TGRTCCVPixelBuffer *)buffer).shouldBeMirrored;
            bool shouldBeMirroredVertically = _internalOrientation == 1 || _internalOrientation == 3;
            if (shouldBeMirrored != _shouldBeMirrored || shouldBeMirroredVertically != _shouldBeMirroredVertically) {
                _shouldBeMirrored = shouldBeMirrored;
                _shouldBeMirroredVertically = shouldBeMirroredVertically;
                if (_shouldBeMirrored) {
                    if (_shouldBeMirroredVertically) {
                        _metalView.transform = CGAffineTransformMakeScale(1.0f, -1.0f);
                    } else {
                        _metalView.transform = CGAffineTransformMakeScale(-1.0f, 1.0f);
                    }
                } else {
                    _metalView.transform = CGAffineTransformIdentity;
                }
                
                if (_didSetShouldBeMirrored) {
                    if (_onIsMirroredUpdated) {
                        _onIsMirroredUpdated(_shouldBeMirrored);
                    }
                } else {
                    _didSetShouldBeMirrored = true;
                }
            }
        }
        
        const OSType pixelFormat = CVPixelBufferGetPixelFormatType(buffer.pixelBuffer);
        if (pixelFormat == kCVPixelFormatType_32BGRA || pixelFormat == kCVPixelFormatType_32ARGB) {
            if (!_rendererRGB) {
                _rendererRGB = [VideoMetalView createRGBRenderer];
                if (![_rendererRGB addRenderingDestination:_metalView]) {
                    _rendererRGB = nil;
                    RTCLogError(@"Failed to create RGB renderer");
                    return;
                }
            }
            renderer = _rendererRGB;
        } else {
            if (!_rendererNV12) {
                _rendererNV12 = [VideoMetalView createNV12Renderer];
                if (![_rendererNV12 addRenderingDestination:_metalView]) {
                    _rendererNV12 = nil;
                    RTCLogError(@"Failed to create NV12 renderer");
                    return;
                }
            }
            renderer = _rendererNV12;
        }
    } else {
        if (!_rendererI420) {
            _rendererI420 = [VideoMetalView createI420Renderer];
            if (![_rendererI420 addRenderingDestination:_metalView]) {
                _rendererI420 = nil;
                RTCLogError(@"Failed to create I420 renderer");
                return;
            }
        }
        renderer = _rendererI420;
    }
    
    renderer.rotationOverride = _rotationOverride;
    
    [renderer drawFrame:videoFrame];
    _lastFrameTimeNs = videoFrame.timeStampNs;
}

- (void)mtkView:(MTKView *)view drawableSizeWillChange:(CGSize)size {
}

#pragma mark -

- (void)setRotationOverride:(NSValue *)rotationOverride {
    _rotationOverride = rotationOverride;
    
    _metalView.drawableSize = [self drawableSize];
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
    
    return _videoFrame.rotation;
}

- (CGSize)drawableSize {
    // Flip width/height if the rotations are not the same.
    CGSize videoFrameSize = _videoFrameSize;
    return videoFrameSize;
    
    /*RTCVideoRotation frameRotation = [self frameRotation];
    
    BOOL useLandscape =
    (frameRotation == RTCVideoRotation_0) || (frameRotation == RTCVideoRotation_180);
    BOOL sizeIsLandscape = (_videoFrame.rotation == RTCVideoRotation_0) ||
    (_videoFrame.rotation == RTCVideoRotation_180);
    
    if (useLandscape == sizeIsLandscape) {
        return videoFrameSize;
    } else {
        return CGSizeMake(videoFrameSize.height, videoFrameSize.width);
    }*/
}

#pragma mark - RTCVideoRenderer

- (void)setSize:(CGSize)size {
    assert([NSThread isMainThread]);
           
    _videoFrameSize = size;
    _metalView.drawableSize = [self drawableSize];
    
   //_metalView.drawableSize = drawableSize;
   //[self setNeedsLayout];
   //[strongSelf.delegate videoView:self didChangeVideoSize:size];
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
        RTCLogInfo(@"VideoMetalView@%lx orientation: %d, aspect: %f", (intptr_t)self, internalOrientation, (float)aspect);
        
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

- (void)setClone:(VideoMetalView * _Nullable)clone {
    _cloneView = clone;
}

@end
