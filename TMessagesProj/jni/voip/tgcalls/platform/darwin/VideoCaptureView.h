#ifndef TGCALLS_VIDEO_CAPTURE_VIEW_H
#define TGCALLS_VIDEO_CAPTURE_VIEW_H

#ifdef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

#include <memory>

@class AVCaptureVideoPreviewLayer;

@interface VideoCaptureView : UIView

@property(nonatomic) UIViewContentMode videoContentMode;
@property(nonatomic, getter=isEnabled) BOOL enabled;
@property(nonatomic, nullable) NSValue* rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;
@property (nonatomic, readwrite) CGFloat internalAspect;

- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int, CGFloat))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;

- (void)onFrameGenerated:(CGSize)size isMirrored:(bool)isMirrored rotation:(int)rotation;

- (AVCaptureVideoPreviewLayer * _Nonnull)previewLayer;

@end

#endif //WEBRTC_IOS

#endif
