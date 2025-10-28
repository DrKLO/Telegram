#ifndef TGCALLS_VIDEO_METAL_VIEW_H
#define TGCALLS_VIDEO_METAL_VIEW_H
#ifdef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#import "api/media_stream_interface.h"

#include <memory>

@class RTCVideoFrame;

@interface VideoMetalView : UIView

+ (bool)isSupported;

@property(nonatomic) UIViewContentMode videoContentMode;
@property(nonatomic, getter=isEnabled) BOOL enabled;
@property(nonatomic, nullable) NSValue* rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;
@property (nonatomic, readwrite) CGFloat internalAspect;

- (void)setSize:(CGSize)size;
- (void)renderFrame:(nullable RTCVideoFrame *)frame;

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink;
- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int, CGFloat))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;

- (void)setClone:(VideoMetalView * _Nullable)clone;

@end

#endif //WEBRTC_IOS
#endif
