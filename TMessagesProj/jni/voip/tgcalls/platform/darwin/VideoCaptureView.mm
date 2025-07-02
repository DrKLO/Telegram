#import "VideoCaptureView.h"

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

@interface VideoCaptureContentView : UIView

@end

@implementation VideoCaptureContentView

+ (Class)layerClass {
    return [AVCaptureVideoPreviewLayer class];
}

- (AVCaptureVideoPreviewLayer * _Nonnull)videoLayer {
    return (AVCaptureVideoPreviewLayer *)self.layer;
}

@end

@interface VideoCaptureFrame : NSObject

@property (nonatomic, readonly) CGSize size;
@property (nonatomic, readonly) bool isMirrored;
@property (nonatomic, readonly) int rotation;

@end

@implementation VideoCaptureFrame

- (instancetype)initWithSize:(CGSize)size isMirrored:(bool)isMirrored rotation:(int)rotation {
    self = [super init];
    if (self != nil) {
        _size = size;
        _isMirrored = isMirrored;
        _rotation = rotation;
    }
    return self;
}

@end

@interface VideoCaptureView () {
    VideoCaptureContentView *_captureView;

    VideoCaptureFrame *_videoFrame;
    VideoCaptureFrame *_stashedVideoFrame;

    int _isWaitingForLayoutFrameCount;
    bool _didStartWaitingForLayout;
    CGSize _videoFrameSize;
    int64_t _lastFrameTimeNs;
    
    CGSize _currentSize;
    
    void (^_onFirstFrameReceived)();
    bool _firstFrameReceivedReported;
    
    void (^_onOrientationUpdated)(int, CGFloat);
    
    void (^_onIsMirroredUpdated)(bool);
    
    bool _didSetShouldBeMirrored;
    bool _shouldBeMirrored;
}

@end

@implementation VideoCaptureView

- (instancetype)initWithFrame:(CGRect)frameRect {
    self = [super initWithFrame:frameRect];
    if (self) {
        [self configure];

        _enabled = true;
        
        _currentSize = CGSizeZero;
        _rotationOverride = @(RTCVideoRotation_0);
    }
    return self;
}

- (void)dealloc {
}

- (AVCaptureVideoPreviewLayer * _Nonnull)previewLayer {
    return _captureView.videoLayer;
}

- (void)setEnabled:(BOOL)enabled {
    if (_enabled != enabled) {
        _enabled = enabled;
    }
}

- (void)setVideoContentMode:(UIViewContentMode)mode {
    _videoContentMode = mode;
}

- (void)configure {
    _captureView = [[VideoCaptureContentView alloc] init];
    [_captureView videoLayer].videoGravity = AVLayerVideoGravityResizeAspectFill;
    [self addSubview:_captureView];

    _videoFrameSize = CGSizeZero;
}

- (void)layoutSubviews {
    [super layoutSubviews];
    
    CGRect bounds = self.bounds;

    _captureView.frame = bounds;
    
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
        switch (_videoFrame.rotation) {
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

- (void)onFrameGenerated:(CGSize)size isMirrored:(bool)isMirrored rotation:(int)rotation {
    assert([NSThread isMainThread]);

    if (!CGSizeEqualToSize(size, _currentSize)) {
        _currentSize = size;
    }

    int mappedValue = 0;
    switch (RTCVideoRotation(rotation)) {
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

    VideoCaptureFrame *frame = [[VideoCaptureFrame alloc] initWithSize:size isMirrored:isMirrored rotation:rotation];

    if (_isWaitingForLayoutFrameCount > 0) {
        _stashedVideoFrame = frame;
        _isWaitingForLayoutFrameCount--;
        return;
    }
    if (!_didStartWaitingForLayout) {
        if (_videoFrame && _videoFrame.size.width > 0 && _videoFrame.size.height > 0 && frame.size.width > 0 && frame.size.height > 0) {
            float previousAspect = ((float)_videoFrame.size.width) / ((float)_videoFrame.size.height);
            float updatedAspect = ((float)frame.size.width) / ((float)frame.size.height);
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
        RTCLogInfo(@"VideoCaptureView@%lx orientation: %d, aspect: %f", (intptr_t)self, internalOrientation, (float)aspect);
        
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
