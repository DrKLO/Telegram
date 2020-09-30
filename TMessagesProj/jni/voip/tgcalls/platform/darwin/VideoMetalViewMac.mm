#import "VideoMetalViewMac.h"
#import <Metal/Metal.h>
#import <MetalKit/MetalKit.h>
#import "TGRTCCVPixelBuffer.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"
#include "sdk/objc/native/api/video_frame.h"
#include "sdk/objc/native/src/objc_frame_buffer.h"

#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"
#import "rtc_base/time_utils.h"



#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"

#import "RTCMTLI420Renderer.h"

#define MTKViewClass NSClassFromString(@"MTKView")
#define RTCMTLI420RendererClass NSClassFromString(@"RTCMTLI420Renderer")

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
        RTCVideoRotation rotation = RTCVideoRotation_0;
        RTCVideoFrame* videoFrame = customToObjCVideoFrame(nativeVideoFrame, rotation);
        
        CGSize currentSize = (videoFrame.rotation % 180 == 0) ? CGSizeMake(videoFrame.width, videoFrame.height) : CGSizeMake(videoFrame.height, videoFrame.width);

        if (_frameReceived) {
            _frameReceived(currentSize, videoFrame, rotation);
        }
    }
    
private:
    void (^_frameReceived)(CGSize, RTCVideoFrame *, RTCVideoRotation);
};

}



@interface VideoMetalView () <MTKViewDelegate> {
    RTCMTLI420Renderer *_rendererI420;

    MTKView *_metalView;
    RTCVideoFrame *_videoFrame;
    CGSize _videoFrameSize;
    int64_t _lastFrameTimeNs;
    
    CGSize _currentSize;
    std::shared_ptr<VideoRendererAdapterImpl> _sink;
    
    void (^_onFirstFrameReceived)(float);
    bool _firstFrameReceivedReported;
    void (^_onOrientationUpdated)(int);
    void (^_onIsMirroredUpdated)(bool);
    
    bool _didSetShouldBeMirrored;
    bool _shouldBeMirrored;
    bool _forceMirrored;
}

@end

@implementation VideoMetalView

+ (bool)isSupported {
    return [VideoMetalView isMetalAvailable];
}

- (instancetype)initWithFrame:(CGRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self) {
        [self configure];
        
        _currentSize = CGSizeZero;
        
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
                        mappedValue = 0;
                        break;
                    case RTCVideoRotation_180:
                        mappedValue = 1;
                        break;
                    case RTCVideoRotation_270:
                        mappedValue = 2;
                        break;
                    default:
                        mappedValue = 0;
                        break;
                }
                [strongSelf setInternalOrientation:mappedValue];
                
                [strongSelf renderFrame:videoFrame];
            });
        }));

    }
    return self;
}

- (BOOL)isEnabled {
    return !_metalView.paused;
}

- (void)setEnabled:(BOOL)enabled {
    _metalView.paused = !enabled;
}

- (CALayerContentsGravity)videoContentMode {
    return _metalView.layer.contentsGravity;
}

- (void)setVideoContentMode:(CALayerContentsGravity)mode {
    _metalView.layer.contentsGravity = mode;
}

#pragma mark - Private

+ (BOOL)isMetalAvailable {
    return MTLCreateSystemDefaultDevice() != nil;
}

+ (MTKView *)createMetalView:(CGRect)frame {
    return [[MTKViewClass alloc] initWithFrame:frame];
}

+ (RTCMTLI420Renderer *)createI420Renderer {
    return [[RTCMTLI420RendererClass alloc] init];
}


- (void)configure {
    NSAssert([VideoMetalView isMetalAvailable], @"Metal not availiable on this device");
    self.wantsLayer = YES;
    self.layerContentsRedrawPolicy = NSViewLayerContentsRedrawDuringViewResize;
    _metalView = [VideoMetalView createMetalView:self.bounds];
    _metalView.delegate = self;
    _metalView.layer.cornerRadius = 4;
    _metalView.layer.backgroundColor = [NSColor clearColor].CGColor;
    _metalView.layer.contentsGravity = kCAGravityResizeAspectFill;//UIViewContentModeScaleAspectFill;
    [self addSubview:_metalView];
    _videoFrameSize = CGSizeZero;
//    _metalView.layer.affineTransform = CGAffineTransformMakeScale(-1.0, -1.0);
}



- (void)layout {
    [super layout];
    
    if (_shouldBeMirrored || _forceMirrored) {
        _metalView.layer.anchorPoint = NSMakePoint(1, 0);
        _metalView.layer.affineTransform = CGAffineTransformMakeScale(-1, 1);
        //  _metalView.layer.transform = CATransform3DMakeScale(-1, 1, 1);
    } else {
        _metalView.layer.anchorPoint = NSMakePoint(0, 0);
        _metalView.layer.affineTransform = CGAffineTransformIdentity;
        //_metalView.layer.transform = CATransform3DIdentity;
    }
    
    CGRect bounds = self.bounds;
    _metalView.frame = bounds;
    if (!CGSizeEqualToSize(_videoFrameSize, CGSizeZero)) {
        _metalView.drawableSize = [self drawableSize];
    } else {
        _metalView.drawableSize = bounds.size;
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
            if (shouldBeMirrored != _shouldBeMirrored) {
                _shouldBeMirrored = shouldBeMirrored;
                bool shouldBeMirrored = ((TGRTCCVPixelBuffer *)buffer).shouldBeMirrored;
                
                if (shouldBeMirrored || _forceMirrored) {
                    _metalView.layer.anchorPoint = NSMakePoint(1, 0);
                    _metalView.layer.affineTransform = CGAffineTransformMakeScale(-1, 1);
                    //  _metalView.layer.transform = CATransform3DMakeScale(-1, 1, 1);
                } else {
                    _metalView.layer.anchorPoint = NSMakePoint(0, 0);
                    _metalView.layer.affineTransform = CGAffineTransformIdentity;
                    //_metalView.layer.transform = CATransform3DIdentity;
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
    }
    if (!_rendererI420) {
        _rendererI420 = [VideoMetalView createI420Renderer];
        if (![_rendererI420 addRenderingDestination:_metalView]) {
            _rendererI420 = nil;
            RTCLogError(@"Failed to create I420 renderer");
            return;
        }
    }
    renderer = _rendererI420;
    
    renderer.rotationOverride = _rotationOverride;
    [renderer drawFrame:videoFrame];
    _lastFrameTimeNs = videoFrame.timeStampNs;
    
    if (!_firstFrameReceivedReported && _onFirstFrameReceived) {
        _firstFrameReceivedReported = true;
        _onFirstFrameReceived((float)videoFrame.width / (float)videoFrame.height);
    }
}

- (void)mtkView:(MTKView *)view drawableSizeWillChange:(CGSize)size {
}

#pragma mark -

- (void)setRotationOverride:(NSValue *)rotationOverride {
    _rotationOverride = rotationOverride;
    
    _metalView.drawableSize = [self drawableSize];
    [self setNeedsLayout:YES];
}

- (RTCVideoRotation)rtcFrameRotation {
    if (_rotationOverride) {
        RTCVideoRotation rotation;
        if (@available(macOS 10.13, *)) {
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
    RTCVideoRotation frameRotation = [self rtcFrameRotation];
    
    BOOL useLandscape =
    (frameRotation == RTCVideoRotation_0) || (frameRotation == RTCVideoRotation_180);
    BOOL sizeIsLandscape = (_videoFrame.rotation == RTCVideoRotation_0) ||
    (_videoFrame.rotation == RTCVideoRotation_180);
    
    if (useLandscape == sizeIsLandscape) {
        return videoFrameSize;
    } else {
        return CGSizeMake(videoFrameSize.height, videoFrameSize.width);
    }
}

#pragma mark - RTCVideoRenderer

- (void)setSize:(CGSize)size {
    assert([NSThread isMainThread]);
           
   _videoFrameSize = size;
   CGSize drawableSize = [self drawableSize];
   
   _metalView.drawableSize = drawableSize;
   [self setNeedsLayout:YES];
   //[strongSelf.delegate videoView:self didChangeVideoSize:size];
}

- (void)renderFrame:(nullable RTCVideoFrame *)frame {
    assert([NSThread isMainThread]);
    
    if (!self.isEnabled) {
        return;
    }
    
    
    
    if (frame == nil) {
        RTCLogInfo(@"Incoming frame is nil. Exiting render callback.");
        return;
    }
    _videoFrame = frame;
    

}

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink {
    assert([NSThread isMainThread]);
    
    return _sink;
}

- (void)setOnFirstFrameReceived:(void (^ _Nullable)(float))onFirstFrameReceived {
    _onFirstFrameReceived = [onFirstFrameReceived copy];
    _firstFrameReceivedReported = false;
}

- (void)setInternalOrientation:(int)internalOrientation {
    if (_internalOrientation != internalOrientation) {
        _internalOrientation = internalOrientation;
        if (_onOrientationUpdated) {
            _onOrientationUpdated(internalOrientation);
        }
    }
}
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int))onOrientationUpdated {
    _onOrientationUpdated = [onOrientationUpdated copy];
}
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated {
    _onIsMirroredUpdated = [onIsMirroredUpdated copy];
}

- (void)setIsForceMirrored:(BOOL)forceMirrored {
    _forceMirrored = forceMirrored;
    [self setNeedsLayout:YES];
}


@end
