#ifndef TGCALLS_VIDEO_SAMPLE_BUFFER_VIEW_H
#define TGCALLS_VIDEO_SAMPLE_BUFFER_VIEW_H

#ifdef WEBRTC_MAC
#import <Foundation/Foundation.h>
#import <Cocoa/Cocoa.h>

#import "api/media_stream_interface.h"

#include <memory>

@interface VideoSampleBufferView : NSView



@property(nonatomic) CALayerContentsGravity _Nullable videoContentMode;
@property(nonatomic, getter=isEnabled) BOOL enabled;
@property(nonatomic, nullable) NSValue* rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;
@property (nonatomic, readwrite) CGFloat internalAspect;

- (void)setSize:(CGSize)size;

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink;
- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int, CGFloat))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;

+(BOOL)isAvailable;

@end

#endif //WEBRTC_MAC

#endif
