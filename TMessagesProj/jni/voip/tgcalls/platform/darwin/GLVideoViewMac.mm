/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "GLVideoViewMac.h"

#import "TGRTCCVPixelBuffer.h"

#import <GLKit/GLKit.h>

#import "RTCDefaultShader.h"
#import "RTCDisplayLinkTimer.h"
#import "RTCI420TextureCache.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrame.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"
#include "sdk/objc/native/api/video_frame.h"
#import "rtc_base/time_utils.h"
#include "sdk/objc/native/src/objc_frame_buffer.h"

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



static CGSize scaleToFillSize(CGSize size, CGSize maxSize) {
    if (size.width < 1.0f) {
        size.width = 1.0f;
    }
    if (size.height < 1.0f) {
        size.height = 1.0f;
    }
    if (size.width < maxSize.width) {
        size.height = floor(maxSize.width * size.height / MAX(1.0f, size.width));
        size.width = maxSize.width;
    }
    if (size.height < maxSize.height) {
        size.width = floor(maxSize.height * size.width / MAX(1.0f, size.height));
        size.height = maxSize.height;
    }
    return size;
}

static CGSize aspectFilled(CGSize from, CGSize to) {
    CGFloat scale = MAX(from.width / MAX(1.0, to.width), from.height / MAX(1.0, to.height));
    return NSMakeSize(ceil(to.width * scale), ceil(to.height * scale));
}
static CGSize aspectFitted(CGSize from, CGSize to) {
    CGFloat scale = MAX(from.width / MAX(1.0, to.width), from.height / MAX(1.0, to.height));
    return NSMakeSize(ceil(to.width * scale), ceil(to.height * scale));
}

/*
 
 func aspectFilled(_ size: CGSize) -> CGSize {
 let scale = max(size.width / max(1.0, self.width), size.height / max(1.0, self.height))
 return CGSize(width: ceil(self.width * scale), height: ceil(self.height * scale))
 }
 func fittedToWidthOrSmaller(_ width: CGFloat) -> CGSize {
 let scale = min(1.0, width / max(1.0, self.width))
 return CGSize(width: floor(self.width * scale), height: floor(self.height * scale))
 }
 
 func aspectFitted(_ size: CGSize) -> CGSize {
 let scale = min(size.width / max(1.0, self.width), size.height / max(1.0, self.height))
 return CGSize(width: ceil(self.width * scale), height: ceil(self.height * scale))
 }
 */


#if !TARGET_OS_IPHONE

@interface OpenGLVideoView : NSOpenGLView
@property(atomic, strong) RTCVideoFrame *videoFrame;
@property(atomic, strong) RTCI420TextureCache *i420TextureCache;

- (void)drawFrame;
- (instancetype)initWithFrame:(NSRect)frame
                  pixelFormat:(NSOpenGLPixelFormat *)format
                       shader:(id<RTCVideoViewShading>)shader;
@end

static CVReturn OnDisplayLinkFired(CVDisplayLinkRef displayLink,
                                   const CVTimeStamp *now,
                                   const CVTimeStamp *outputTime,
                                   CVOptionFlags flagsIn,
                                   CVOptionFlags *flagsOut,
                                   void *displayLinkContext) {
    OpenGLVideoView *view =
    (__bridge OpenGLVideoView *)displayLinkContext;
    [view drawFrame];
    return kCVReturnSuccess;
}


@implementation OpenGLVideoView {
    CVDisplayLinkRef _displayLink;
    RTCVideoFrame * _lastDrawnFrame;
    id<RTCVideoViewShading> _shader;
    
    int64_t _lastDrawnFrameTimeStampNs;
    void (^_onFirstFrameReceived)(float);
    bool _firstFrameReceivedReported;
}

@synthesize videoFrame = _videoFrame;
@synthesize i420TextureCache = _i420TextureCache;

- (instancetype)initWithFrame:(NSRect)frame
                  pixelFormat:(NSOpenGLPixelFormat *)format
                       shader:(id<RTCVideoViewShading>)shader {
    if (self = [super initWithFrame:frame pixelFormat:format]) {
        self->_shader = shader;
    }
    return self;
}

- (void)reshape {
    [super reshape];
    NSRect frame = [self frame];
    [self ensureGLContext];
    CGLLockContext([[self openGLContext] CGLContextObj]);
    glViewport(0, 0, frame.size.width, frame.size.height);
    CGLUnlockContext([[self openGLContext] CGLContextObj]);
}

- (void)lockFocus {
    NSOpenGLContext *context = [self openGLContext];
    [super lockFocus];
    if ([context view] != self) {
        [context setView:self];
    }
    [context makeCurrentContext];
}

- (void)prepareOpenGL {
    [super prepareOpenGL];
    [self ensureGLContext];
    glDisable(GL_DITHER);
    [self setupDisplayLink];
}

- (void)clearGLContext {
    [self ensureGLContext];
    self.i420TextureCache = nil;
    [super clearGLContext];
}

- (void)drawRect:(NSRect)rect {
    [self drawFrame];
}

- (void)drawFrame {
    RTCVideoFrame *frame = self.videoFrame;
    if (!frame || frame == _lastDrawnFrame) {
        return;
    }
    // This method may be called from CVDisplayLink callback which isn't on the
    // main thread so we have to lock the GL context before drawing.
    NSOpenGLContext *context = [self openGLContext];
    CGLLockContext([context CGLContextObj]);
    
    [self ensureGLContext];
    glClear(GL_COLOR_BUFFER_BIT);
    
    
    // Rendering native CVPixelBuffer is not supported on OS X.
    // TODO(magjed): Add support for NV12 texture cache on OS X.
    frame = [frame newI420VideoFrame];
    if (!self.i420TextureCache) {
        self.i420TextureCache = [[RTCI420TextureCache alloc] initWithContext:context];
    }
    RTCVideoRotation rotation = frame.rotation;
    
    RTCI420TextureCache *i420TextureCache = self.i420TextureCache;
    if (i420TextureCache) {
        [i420TextureCache uploadFrameToTextures:frame];
        [_shader applyShadingForFrameWithWidth:frame.width
                                        height:frame.height
                                      rotation:rotation
                                        yPlane:i420TextureCache.yTexture
                                        uPlane:i420TextureCache.uTexture
                                        vPlane:i420TextureCache.vTexture];
        [context flushBuffer];
        _lastDrawnFrame = frame;
    }
    CGLUnlockContext([context CGLContextObj]);
    
    if (!_firstFrameReceivedReported && _onFirstFrameReceived) {
        _firstFrameReceivedReported = true;
        float aspectRatio = (float)frame.width / (float)frame.height;
        dispatch_async(dispatch_get_main_queue(), ^{
            self->_onFirstFrameReceived(aspectRatio);
        });
    }
    
}


- (void)setupDisplayLink {
    if (_displayLink) {
        return;
    }
    // Synchronize buffer swaps with vertical refresh rate.
    GLint swapInt = 1;
    [[self openGLContext] setValues:&swapInt forParameter:NSOpenGLCPSwapInterval];
    
    // Create display link.
    CVDisplayLinkCreateWithActiveCGDisplays(&_displayLink);
    CVDisplayLinkSetOutputCallback(_displayLink,
                                   &OnDisplayLinkFired,
                                   (__bridge void *)self);
    // Set the display link for the current renderer.
    CGLContextObj cglContext = [[self openGLContext] CGLContextObj];
    CGLPixelFormatObj cglPixelFormat = [[self pixelFormat] CGLPixelFormatObj];
    CVDisplayLinkSetCurrentCGDisplayFromOpenGLContext(
                                                      _displayLink, cglContext, cglPixelFormat);
    CVDisplayLinkStart(_displayLink);
}

-(void)setFrameOrigin:(NSPoint)newOrigin {
    [super setFrameOrigin:newOrigin];
}

- (void)teardownDisplayLink {
    if (!_displayLink) {
        return;
    }
    CVDisplayLinkRelease(_displayLink);
    _displayLink = NULL;
}

- (void)ensureGLContext {
    NSOpenGLContext* context = [self openGLContext];
    NSAssert(context, @"context shouldn't be nil");
    if ([NSOpenGLContext currentContext] != context) {
        [context makeCurrentContext];
    }
}

- (void)dealloc {
    [self teardownDisplayLink];
}

- (void)setOnFirstFrameReceived:(void (^ _Nullable)(float))onFirstFrameReceived {
    _onFirstFrameReceived = [onFirstFrameReceived copy];
    _firstFrameReceivedReported = false;
}


@end




@interface GLVideoView ()
@property(nonatomic, strong) OpenGLVideoView *glView;
@end

@implementation GLVideoView {
    
    CGSize _currentSize;
    
    std::shared_ptr<VideoRendererAdapterImpl> _sink;
    
    void (^_onOrientationUpdated)(int);
    void (^_onIsMirroredUpdated)(bool);
    
    bool _didSetShouldBeMirrored;
    bool _shouldBeMirrored;
    bool _forceMirrored;

}

@synthesize delegate = _delegate;

-(instancetype)initWithFrame:(NSRect)frameRect {
    NSOpenGLPixelFormatAttribute attributes[] = {
        NSOpenGLPFADoubleBuffer,
        NSOpenGLPFADepthSize, 24,
        NSOpenGLPFAOpenGLProfile,
        NSOpenGLProfileVersion3_2Core,
        0
    };
    NSOpenGLPixelFormat* pixelFormat =
    [[NSOpenGLPixelFormat alloc] initWithAttributes:attributes];
    return [self initWithFrame:frameRect pixelFormat: pixelFormat];
}

- (instancetype)initWithFrame:(NSRect)frame pixelFormat:(NSOpenGLPixelFormat *)format {
    return [self initWithFrame:frame pixelFormat:format shader:[[RTCDefaultShader alloc] init]];
}

- (instancetype)initWithFrame:(NSRect)frame
                  pixelFormat:(NSOpenGLPixelFormat *)format
                       shader:(id<RTCVideoViewShading>)shader {
    if (self = [super initWithFrame:frame]) {
        
        _glView = [[OpenGLVideoView alloc] initWithFrame:frame pixelFormat:format shader:shader];
        _glView.wantsLayer = YES;
        self.layerContentsRedrawPolicy = NSViewLayerContentsRedrawDuringViewResize;
        _glView.layerContentsRedrawPolicy = NSViewLayerContentsRedrawDuringViewResize;

        [self addSubview:_glView];
        
        __weak GLVideoView *weakSelf = self;
        
        self.wantsLayer = YES;
        
        _sink.reset(new VideoRendererAdapterImpl(^(CGSize size, RTCVideoFrame *videoFrame, RTCVideoRotation rotation) {
            dispatch_async(dispatch_get_main_queue(), ^{
                __strong GLVideoView *strongSelf = weakSelf;
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



- (CALayerContentsGravity)videoContentMode {
    return self.glView.layer.contentsGravity;
}

- (void)setVideoContentMode:(CALayerContentsGravity)mode {
    self.glView.layer.contentsGravity = mode;
    [self setNeedsLayout:YES];
}

-(void)layout {
    [super layout];
    
    if (self.bounds.size.width > 0.0f && _currentSize.width > 0) {
        
        NSSize size = _currentSize;
        NSSize frameSize = self.frame.size;
        if ( self.glView.layer.contentsGravity == kCAGravityResizeAspectFill) {
            size = aspectFitted(frameSize, _currentSize);
        } else {
            size = aspectFilled(frameSize, _currentSize);
        }
        _glView.frame = CGRectMake(floor((self.bounds.size.width - size.width) / 2.0), floor((self.bounds.size.height - size.height) / 2.0), size.width, size.height);
    }
    
    if (_shouldBeMirrored || _forceMirrored) {
        self.glView.layer.anchorPoint = NSMakePoint(1, 0);
        self.glView.layer.affineTransform = CGAffineTransformMakeScale(-1, 1);
    } else {
        self.glView.layer.anchorPoint = NSMakePoint(0, 0);
        self.glView.layer.affineTransform = CGAffineTransformIdentity;
    }
}

- (void)setSize:(CGSize)size {
    [self.delegate videoView:self didChangeVideoSize:size];
    [self setNeedsLayout:YES];
}

- (void)renderFrame:(RTCVideoFrame *)videoFrame {
    self.glView.videoFrame = videoFrame;
    
    if ([videoFrame.buffer isKindOfClass:[RTCCVPixelBuffer class]]) {
        RTCCVPixelBuffer *buffer = (RTCCVPixelBuffer*)videoFrame.buffer;
        if ([buffer isKindOfClass:[TGRTCCVPixelBuffer class]]) {
            bool shouldBeMirrored = ((TGRTCCVPixelBuffer *)buffer).shouldBeMirrored;
            if (shouldBeMirrored != _shouldBeMirrored) {
                _shouldBeMirrored = shouldBeMirrored;
                if (shouldBeMirrored || _forceMirrored) {
                    self.glView.layer.anchorPoint = NSMakePoint(1, 0);
                    self.glView.layer.affineTransform = CGAffineTransformMakeScale(-1, 1);
                } else {
                    self.glView.layer.anchorPoint = NSMakePoint(0, 0);
                    self.glView.layer.affineTransform = CGAffineTransformIdentity;
                }
            }
            
            if (shouldBeMirrored != _shouldBeMirrored) {
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
}

#pragma mark - Private



- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink {
    assert([NSThread isMainThread]);
    
    return _sink;
}

- (void)setOnFirstFrameReceived:(void (^ _Nullable)(float))onFirstFrameReceived {
    [self.glView setOnFirstFrameReceived:onFirstFrameReceived];
}

- (void)setInternalOrientation:(int)internalOrientation {
    _internalOrientation = internalOrientation;
    if (_onOrientationUpdated) {
        _onOrientationUpdated(internalOrientation);
    }
}

- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int))onOrientationUpdated {
    _onOrientationUpdated = [onOrientationUpdated copy];
}

- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated {
}

- (void)setIsForceMirrored:(BOOL)forceMirrored {
    _forceMirrored = forceMirrored;
    [self setNeedsLayout:YES];
}

@end

#endif  // !TARGET_OS_IPHONE
