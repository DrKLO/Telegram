/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "GLVideoView.h"

#import <GLKit/GLKit.h>

#import "RTCDefaultShader.h"
#import "RTCDisplayLinkTimer.h"
#import "RTCI420TextureCache.h"
#import "RTCNV12TextureCache.h"
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
                                   rotation:RTCVideoRotation_90
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
        RTCVideoRotation rotation = RTCVideoRotation_90;
        RTCVideoFrame* videoFrame = customToObjCVideoFrame(nativeVideoFrame, rotation);
        
        CGSize currentSize = CGSizeMake(videoFrame.height, videoFrame.width);
        
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

// GLVideoView wraps a GLKView which is setup with
// enableSetNeedsDisplay = NO for the purpose of gaining control of
// exactly when to call -[GLKView display]. This need for extra
// control is required to avoid triggering method calls on GLKView
// that results in attempting to bind the underlying render buffer
// when the drawable size would be empty which would result in the
// error GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT. -[GLKView display] is
// the method that will trigger the binding of the render
// buffer. Because the standard behaviour of -[UIView setNeedsDisplay]
// is disabled for the reasons above, the GLVideoView maintains
// its own |isDirty| flag.

@interface GLVideoView () <GLKViewDelegate>
// |videoFrame| is set when we receive a frame from a worker thread and is read
// from the display link callback so atomicity is required.
@property(atomic, strong) RTCVideoFrame *videoFrame;
@property(nonatomic, readonly) GLKView *glkView;
@end

@implementation GLVideoView {
    RTCDisplayLinkTimer *_timer;
    EAGLContext *_glContext;
    // This flag should only be set and read on the main thread (e.g. by
    // setNeedsDisplay)
    BOOL _isDirty;
    id<RTCVideoViewShading> _shader;
    RTCNV12TextureCache *_nv12TextureCache;
    RTCI420TextureCache *_i420TextureCache;
    // As timestamps should be unique between frames, will store last
    // drawn frame timestamp instead of the whole frame to reduce memory usage.
    int64_t _lastDrawnFrameTimeStampNs;
    
    CGSize _currentSize;
    
    void (^_onFirstFrameReceived)();
    bool _firstFrameReceivedReported;
    
    void (^_onOrientationUpdated)(int);
    
    std::shared_ptr<VideoRendererAdapterImpl> _sink;
}

@synthesize delegate = _delegate;
@synthesize videoFrame = _videoFrame;
@synthesize glkView = _glkView;
@synthesize rotationOverride = _rotationOverride;

- (instancetype)initWithFrame:(CGRect)frame {
    return [self initWithFrame:frame shader:[[RTCDefaultShader alloc] init]];
}

- (instancetype)initWithCoder:(NSCoder *)aDecoder {
    return [self initWithCoder:aDecoder shader:[[RTCDefaultShader alloc] init]];
}

- (instancetype)initWithFrame:(CGRect)frame shader:(id<RTCVideoViewShading>)shader {
    if (self = [super initWithFrame:frame]) {
        _shader = shader;
        if (![self configure]) {
            return nil;
        }
    }
    return self;
}

- (instancetype)initWithCoder:(NSCoder *)aDecoder shader:(id<RTCVideoViewShading>)shader {
    if (self = [super initWithCoder:aDecoder]) {
        _shader = shader;
        if (![self configure]) {
            return nil;
        }
    }
    return self;
}

- (BOOL)configure {
    EAGLContext *glContext =
    [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES3];
    if (!glContext) {
        glContext = [[EAGLContext alloc] initWithAPI:kEAGLRenderingAPIOpenGLES2];
    }
    if (!glContext) {
        RTCLogError(@"Failed to create EAGLContext");
        return NO;
    }
    _glContext = glContext;
    
    // GLKView manages a framebuffer for us.
    _glkView = [[GLKView alloc] initWithFrame:CGRectZero
                                      context:_glContext];
    _glkView.drawableColorFormat = GLKViewDrawableColorFormatRGBA8888;
    _glkView.drawableDepthFormat = GLKViewDrawableDepthFormatNone;
    _glkView.drawableStencilFormat = GLKViewDrawableStencilFormatNone;
    _glkView.drawableMultisample = GLKViewDrawableMultisampleNone;
    _glkView.delegate = self;
    _glkView.layer.masksToBounds = YES;
    _glkView.enableSetNeedsDisplay = NO;
    [self addSubview:_glkView];
    
    // Listen to application state in order to clean up OpenGL before app goes
    // away.
    NSNotificationCenter *notificationCenter =
    [NSNotificationCenter defaultCenter];
    [notificationCenter addObserver:self
                           selector:@selector(willResignActive)
                               name:UIApplicationWillResignActiveNotification
                             object:nil];
    [notificationCenter addObserver:self
                           selector:@selector(didBecomeActive)
                               name:UIApplicationDidBecomeActiveNotification
                             object:nil];
    
    // Frames are received on a separate thread, so we poll for current frame
    // using a refresh rate proportional to screen refresh frequency. This
    // occurs on the main thread.
    __weak GLVideoView *weakSelf = self;
    _timer = [[RTCDisplayLinkTimer alloc] initWithTimerHandler:^{
        GLVideoView *strongSelf = weakSelf;
        [strongSelf displayLinkTimerDidFire];
    }];
    if ([[UIApplication sharedApplication] applicationState] == UIApplicationStateActive) {
        [self setupGL];
    }
    
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
    
    return YES;
}

- (void)setMultipleTouchEnabled:(BOOL)multipleTouchEnabled {
    [super setMultipleTouchEnabled:multipleTouchEnabled];
    _glkView.multipleTouchEnabled = multipleTouchEnabled;
}

- (void)dealloc {
    [[NSNotificationCenter defaultCenter] removeObserver:self];
    UIApplicationState appState =
    [UIApplication sharedApplication].applicationState;
    if (appState == UIApplicationStateActive) {
        [self teardownGL];
    }
    [_timer invalidate];
    [self ensureGLContext];
    _shader = nil;
    if (_glContext && [EAGLContext currentContext] == _glContext) {
        [EAGLContext setCurrentContext:nil];
    }
}

#pragma mark - UIView

- (void)setNeedsDisplay {
    [super setNeedsDisplay];
    _isDirty = YES;
}

- (void)setNeedsDisplayInRect:(CGRect)rect {
    [super setNeedsDisplayInRect:rect];
    _isDirty = YES;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    if (self.bounds.size.width > 0.0f) {
        CGSize contentSize = scaleToFillSize(_currentSize, self.bounds.size);
        _glkView.frame = CGRectMake(floor((self.bounds.size.width - contentSize.width) / 2.0), floor((self.bounds.size.height - contentSize.height) / 2.0), contentSize.width, contentSize.height);
    }
}

#pragma mark - GLKViewDelegate

// This method is called when the GLKView's content is dirty and needs to be
// redrawn. This occurs on main thread.
- (void)glkView:(GLKView *)view drawInRect:(CGRect)rect {
    // The renderer will draw the frame to the framebuffer corresponding to the
    // one used by |view|.
    RTCVideoFrame *frame = self.videoFrame;
    if (!frame || frame.timeStampNs == _lastDrawnFrameTimeStampNs) {
        return;
    }
    RTCVideoRotation rotation = frame.rotation;
    if(_rotationOverride != nil) {
        [_rotationOverride getValue: &rotation];
    }
    [self ensureGLContext];
    glClear(GL_COLOR_BUFFER_BIT);
    if ([frame.buffer isKindOfClass:[RTCCVPixelBuffer class]]) {
        if (!_nv12TextureCache) {
            _nv12TextureCache = [[RTCNV12TextureCache alloc] initWithContext:_glContext];
        }
        if (_nv12TextureCache) {
            [_nv12TextureCache uploadFrameToTextures:frame];
            [_shader applyShadingForFrameWithWidth:frame.width
                                            height:frame.height
                                          rotation:rotation
                                            yPlane:_nv12TextureCache.yTexture
                                           uvPlane:_nv12TextureCache.uvTexture];
            [_nv12TextureCache releaseTextures];
            
            _lastDrawnFrameTimeStampNs = self.videoFrame.timeStampNs;
        }
    } else {
        if (!_i420TextureCache) {
            _i420TextureCache = [[RTCI420TextureCache alloc] initWithContext:_glContext];
        }
        [_i420TextureCache uploadFrameToTextures:frame];
        [_shader applyShadingForFrameWithWidth:frame.width
                                        height:frame.height
                                      rotation:rotation
                                        yPlane:_i420TextureCache.yTexture
                                        uPlane:_i420TextureCache.uTexture
                                        vPlane:_i420TextureCache.vTexture];
        
        _lastDrawnFrameTimeStampNs = self.videoFrame.timeStampNs;
    }
}

#pragma mark - RTCVideoRenderer

- (void)setSize:(CGSize)size {
    [self.delegate videoView:self didChangeVideoSize:size];
    
    if (self.bounds.size.width > 0.0f) {
        CGSize contentSize = scaleToFillSize(size, self.bounds.size);
        _glkView.frame = CGRectMake(floor((self.bounds.size.width - contentSize.width) / 2.0), floor((self.bounds.size.height - contentSize.height) / 2.0), contentSize.width, contentSize.height);
    }
}

- (void)renderFrame:(RTCVideoFrame *)frame {
    self.videoFrame = frame;
}

#pragma mark - Private

- (void)displayLinkTimerDidFire {
    // Don't render unless video frame have changed or the view content
    // has explicitly been marked dirty.
    if (!_isDirty && _lastDrawnFrameTimeStampNs == self.videoFrame.timeStampNs) {
        return;
    }
    
    // Always reset isDirty at this point, even if -[GLKView display]
    // won't be called in the case the drawable size is empty.
    _isDirty = NO;
    
    // Only call -[GLKView display] if the drawable size is
    // non-empty. Calling display will make the GLKView setup its
    // render buffer if necessary, but that will fail with error
    // GL_FRAMEBUFFER_INCOMPLETE_ATTACHMENT if size is empty.
    if (self.bounds.size.width > 0 && self.bounds.size.height > 0) {
        [_glkView display];
    }
}

- (void)setupGL {
    [self ensureGLContext];
    glDisable(GL_DITHER);
    _timer.isPaused = NO;
}

- (void)teardownGL {
    self.videoFrame = nil;
    _timer.isPaused = YES;
    [_glkView deleteDrawable];
    [self ensureGLContext];
    _nv12TextureCache = nil;
    _i420TextureCache = nil;
}

- (void)didBecomeActive {
    [self setupGL];
}

- (void)willResignActive {
    [self teardownGL];
}

- (void)ensureGLContext {
    NSAssert(_glContext, @"context shouldn't be nil");
    if ([EAGLContext currentContext] != _glContext) {
        [EAGLContext setCurrentContext:_glContext];
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

@end
