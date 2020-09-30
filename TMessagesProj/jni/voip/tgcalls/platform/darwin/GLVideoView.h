/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import <Foundation/Foundation.h>
#ifdef WEBRTC_IOS
#import <UIKit/UIKit.h>
#else
#import <AppKit/AppKit.h>
#endif

#import "RTCMacros.h"
#import "RTCVideoRenderer.h"
#import "RTCVideoViewShading.h"

#import "api/media_stream_interface.h"

#include <memory>

NS_ASSUME_NONNULL_BEGIN

@class GLVideoView;

/**
 * GLVideoView is an RTCVideoRenderer which renders video frames in its
 * bounds using OpenGLES 2.0 or OpenGLES 3.0.
 */
RTC_OBJC_EXPORT
@interface GLVideoView :
#ifdef WEBRTC_IOS
UIView
#else
NSView
#endif
<RTCVideoRenderer>

@property(nonatomic, weak) id<RTCVideoViewDelegate> delegate;

- (instancetype)initWithFrame:(CGRect)frame
                       shader:(id<RTCVideoViewShading>)shader NS_DESIGNATED_INITIALIZER;

- (instancetype)initWithCoder:(NSCoder *)aDecoder
                       shader:(id<RTCVideoViewShading>)shader NS_DESIGNATED_INITIALIZER;

/** @abstract Wrapped RTCVideoRotation, or nil.
 */
@property(nonatomic, nullable) NSValue *rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink;
- (void)setOnFirstFrameReceived:(void (^ _Nullable)())onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;

@end

NS_ASSUME_NONNULL_END
