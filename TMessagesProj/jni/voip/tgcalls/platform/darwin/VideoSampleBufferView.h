#ifndef TGCALLS_VIDEO_SAMPLE_BUFFER_VIEW_H
#define TGCALLS_VIDEO_SAMPLE_BUFFER_VIEW_H

#ifdef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#import "api/media_stream_interface.h"

#include <memory>

@interface VideoSampleBufferView : UIView

@property(nonatomic) UIViewContentMode videoContentMode;
@property(nonatomic, getter=isEnabled) BOOL enabled;
@property(nonatomic, nullable) NSValue* rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;
@property (nonatomic, readwrite) CGFloat internalAspect;

- (void)setSize:(CGSize)size;

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink;
- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int, CGFloat))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;
- (void)addFrame:(const webrtc::VideoFrame&)frame;

- (void)setCloneTarget:(VideoSampleBufferView * _Nullable)cloneTarget;

@end

#endif //WEBRTC_IOS

#endif
