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

#import "SQueueLocalObject.h"


#import "api/video/video_sink_interface.h"
#import "api/media_stream_interface.h"

#import "tgcalls/platform/darwin/macOS/TGRTCMTLI420Renderer.h"

#define MTKViewClass NSClassFromString(@"MTKView")
#define TGRTCMTLI420RendererClass NSClassFromString(@"TGRTCMTLI420Renderer")

SQueue *renderQueue = [[SQueue alloc] init];

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

@interface TGCAMetalLayer : CAMetalLayer

@end

@implementation TGCAMetalLayer


-(void)dealloc {
}
@end

@interface VideoMetalView () {
    SQueueLocalObject *_rendererI420;

    CAMetalLayer *_metalView;
    NSView *_foregroundView;

    CGSize _videoFrameSize;
    RTCVideoRotation _rotation;
    int64_t _lastFrameTimeNs;
    
    CGSize _currentSize;
    std::shared_ptr<VideoRendererAdapterImpl> _sink;
    
    void (^_onFirstFrameReceived)(float);
    bool _firstFrameReceivedReported;
    void (^_onOrientationUpdated)(int, CGFloat);
    void (^_onIsMirroredUpdated)(bool);
    
    bool _didSetShouldBeMirrored;
    bool _shouldBeMirrored;
    bool _forceMirrored;
    
    bool _isPaused;
    
    NSMutableArray<RTCVideoFrame *> *_frames;
    RTCVideoFrame *_videoFrame;
    BOOL _drawing;
    
    BOOL _deleteForegroundOnNextDrawing;
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
        _lastFrameTimeNs = INT32_MAX;
        _currentSize = CGSizeZero;
        _frames = [[NSMutableArray alloc] init];
        _drawing = false;
        _isPaused = false;
        _deleteForegroundOnNextDrawing = false;
        __weak VideoMetalView *weakSelf = self;
        _sink.reset(new VideoRendererAdapterImpl(^(CGSize size, RTCVideoFrame *videoFrame, RTCVideoRotation rotation) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong VideoMetalView *strongSelf = weakSelf;
                if (strongSelf == nil) {
                    return;
                }
                strongSelf->_rotation = videoFrame.rotation;
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
                
                if ([videoFrame.buffer isKindOfClass:[RTCCVPixelBuffer class]]) {
                    RTCCVPixelBuffer *buffer = (RTCCVPixelBuffer*)videoFrame.buffer;
                    
                    if ([buffer isKindOfClass:[TGRTCCVPixelBuffer class]]) {
                        bool shouldBeMirrored = ((TGRTCCVPixelBuffer *)buffer).shouldBeMirrored;
                        if (shouldBeMirrored != strongSelf->_shouldBeMirrored) {
                            strongSelf->_shouldBeMirrored = shouldBeMirrored;
                            if (strongSelf->_onIsMirroredUpdated) {
                                strongSelf->_onIsMirroredUpdated(strongSelf->_shouldBeMirrored);
                            }
                        }
                    }
                }
                
                if (!strongSelf->_firstFrameReceivedReported && strongSelf->_onFirstFrameReceived) {
                    strongSelf->_firstFrameReceivedReported = true;
                    strongSelf->_onFirstFrameReceived((float)videoFrame.width / (float)videoFrame.height);
                }
            });
        }));

    }
    return self;
}

- (BOOL)isEnabled {
    return !_isPaused;
}

- (void)setEnabled:(BOOL)enabled {
    _isPaused = enabled;
}
-(void)setIsPaused:(bool)paused {
    _isPaused = paused;
    [self updateDrawingSize:self.frame.size];
}
-(void)renderToSize:(NSSize)size animated: (bool)animated {
    [CATransaction begin];
    [CATransaction setDisableActions:YES];
    if (animated) {
        if (!_foregroundView) {
            _foregroundView = [[NSView alloc] initWithFrame:self.bounds];
            _foregroundView.wantsLayer = YES;
            _foregroundView.autoresizingMask = 0;
            _foregroundView.layer = [VideoMetalView createMetalView:self.bounds];
            _foregroundView.layer.contentsGravity = kCAGravityResizeAspect;
            [self addSubview:_foregroundView];
        }
        
        CAMetalLayer *layer = _metalView;
        CAMetalLayer *foreground = (CAMetalLayer *)_foregroundView.layer;

        [_rendererI420 with:^(TGRTCMTLI420Renderer * renderer) {
            [renderer setDoubleRendering:layer foreground:foreground];
        }];
        _deleteForegroundOnNextDrawing = false;
    } else {
        _deleteForegroundOnNextDrawing = true;
        CAMetalLayer *layer = _metalView;
        
        [_rendererI420 with:^(TGRTCMTLI420Renderer * renderer) {
            [renderer setSingleRendering:layer];
        }];
    }
    [self updateDrawingSize:size];
    [CATransaction commit];
}

- (CALayerContentsGravity)videoContentMode {
    return _metalView.contentsGravity;
}

- (void)setVideoContentMode:(CALayerContentsGravity)mode {
   // _metalView.contentsGravity = mode;
}

#pragma mark - Private

+ (BOOL)isMetalAvailable {
    return initMetal();
}

+ (CAMetalLayer *)createMetalView:(CGRect)frame {
    CAMetalLayer *layer = [[TGCAMetalLayer alloc] init];
    [CATransaction begin];
    [CATransaction setDisableActions:true];
    layer.framebufferOnly = true;
    layer.opaque = false;
//    layer.cornerRadius = 4;
    if (@available(macOS 10.13, *)) {
        layer.displaySyncEnabled = NO;
    }
//    layer.presentsWithTransaction = YES;
    layer.backgroundColor = [NSColor clearColor].CGColor;
    layer.contentsGravity = kCAGravityResizeAspectFill;
    layer.frame = frame;

    [CATransaction commit];
    return layer;
}

+ (TGRTCMTLI420Renderer *)createI420Renderer {
    return [[TGRTCMTLI420RendererClass alloc] init];
}


- (void)configure {
    NSAssert([VideoMetalView isMetalAvailable], @"Metal not availiable on this device");
    self.wantsLayer = YES;
    self.layerContentsRedrawPolicy = NSViewLayerContentsRedrawOnSetNeedsDisplay;
    _metalView = [VideoMetalView createMetalView:self.bounds];

    
    self.layer = _metalView;
    _videoFrameSize = CGSizeZero;
        
    CAMetalLayer *layer = _metalView;

    _rendererI420 = [[SQueueLocalObject alloc] initWithQueue:renderQueue generate: ^{
        TGRTCMTLI420Renderer *renderer = [VideoMetalView createI420Renderer];
        [renderer setSingleRendering:layer];
        return renderer;
    }];
}


-(void)setFrameSize:(NSSize)newSize {
    [super setFrameSize:newSize];
    
    [self updateDrawingSize: newSize];
    
}
- (void)layout {
    [super layout];
}

-(void)updateDrawingSize:(NSSize)size {
    if (_isPaused) {
        return;
    }
    _metalView.frame = CGRectMake(0, 0, size.width, size.height);
    _foregroundView.frame = self.bounds;
    if (!CGSizeEqualToSize(_videoFrameSize, CGSizeZero)) {
        _metalView.drawableSize = [self drawableSize:size];
        ((CAMetalLayer *)_foregroundView.layer).drawableSize = _videoFrameSize;
    } else {
        _metalView.drawableSize = size;
        ((CAMetalLayer *)_foregroundView.layer).drawableSize = size;
    }
    
    if(!_isPaused) {
        RTCVideoFrame *frame = [_frames lastObject];
        if (frame == nil) {
            frame = _videoFrame;
        }
        if (frame) {
            [self renderFrame:frame];
        }
    }
}


-(void)dealloc {
    int bp = 0;
    bp += 1;
}

#pragma mark -

- (void)setRotationOverride:(NSValue *)rotationOverride {
    _rotationOverride = rotationOverride;
    
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
    
    return _rotation;
}

- (CGSize)drawableSize:(NSSize)forSize {
    
    MTLFrameSize from;
    MTLFrameSize to;
        
    from.width = _videoFrameSize.width;
    from.height = _videoFrameSize.height;
    
    if (CGSizeEqualToSize(CGSizeZero, forSize)) {
        to.width = _videoFrameSize.width;
        to.height = _videoFrameSize.height;
    } else {
        to.width = forSize.width;
        to.height = forSize.height;
    }
    

    MTLFrameSize size = MTLAspectFilled(to, from);
    
    return CGSizeMake(size.width, size.height);
}

#pragma mark - RTCVideoRenderer

- (void)setSize:(CGSize)size {
    assert([NSThread isMainThread]);
           
   _videoFrameSize = size;
   [self updateDrawingSize:self.frame.size];
    
    _internalAspect = _videoFrameSize.width / _videoFrameSize.height;
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
    
    RTCVideoFrame *videoFrame = frame;
    // Skip rendering if we've already rendered this frame.
    if (!videoFrame) {
        return;
    }
        
    if (CGRectIsEmpty(self.bounds)) {
        return;
    }
    if (CGRectIsEmpty(self.visibleRect)) {
        return;
    }
    if (self.window == nil || self.superview == nil) {
        return;
    }
    if ((self.window.occlusionState & NSWindowOcclusionStateVisible) == 0) {
        return;
    }
   
    
    if (_frames.count >= 5) {
        [_frames removeAllObjects];
        [_frames addObject:videoFrame];
        [self enqueue];
        return;
    }
    
    [_frames addObject:videoFrame];
        
    [self enqueue];

}

-(void)enqueue {
    if(_frames.count > 0 && !_drawing) {
        RTCVideoFrame *videoFrame = [_frames firstObject];
        
        NSValue * rotationOverride = _rotationOverride;
        
        
        int64_t timeStampNs = videoFrame.timeStampNs;
        
        __weak VideoMetalView *weakSelf = self;
        dispatch_block_t completion = ^{
            __strong VideoMetalView *strongSelf = weakSelf;
            if (strongSelf && strongSelf->_frames.count > 0) {
                [strongSelf->_frames removeObjectAtIndex:0];
                strongSelf->_drawing = false;
                strongSelf->_lastFrameTimeNs = timeStampNs;
                if (strongSelf->_deleteForegroundOnNextDrawing) {
                    [strongSelf->_foregroundView removeFromSuperview];
                    strongSelf->_foregroundView = nil;
                    strongSelf->_deleteForegroundOnNextDrawing = false;
                }
                [strongSelf enqueue];
            }
        };
        
        _videoFrame = videoFrame;
        
        self->_drawing = true;        
        
        [_rendererI420 with:^(TGRTCMTLI420Renderer * object) {
            object.rotationOverride = rotationOverride;
            [object drawFrame:videoFrame];
            dispatch_async(dispatch_get_main_queue(), completion);
        }];
        
    }
}

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink {
    assert([NSThread isMainThread]);
    
    return _sink;
}

- (void)setOnFirstFrameReceived:(void (^ _Nullable)(float))onFirstFrameReceived {
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

- (void)setForceMirrored:(BOOL)forceMirrored {
    _forceMirrored = forceMirrored;
    [self setNeedsLayout:YES];
}


@end
