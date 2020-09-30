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

#if !TARGET_OS_IPHONE

#import <AppKit/NSOpenGLView.h>
#import "api/media_stream_interface.h"
#import "RTCVideoRenderer.h"
#import "RTCVideoViewShading.h"

NS_ASSUME_NONNULL_BEGIN

@class GLVideoView;

@protocol GLVideoViewDelegate<RTCVideoViewDelegate> @end

@interface GLVideoView : NSView <RTCVideoRenderer>

@property(nonatomic, weak) id<GLVideoViewDelegate> delegate;

- (instancetype)initWithFrame:(NSRect)frameRect
pixelFormat:(NSOpenGLPixelFormat *)format
shader:(id<RTCVideoViewShading>)shader
NS_DESIGNATED_INITIALIZER;


@property(nonatomic, nullable) NSValue *rotationOverride;

@property (nonatomic, readwrite) int internalOrientation;

- (std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)getSink;
- (void)setOnFirstFrameReceived:(void (^ _Nullable)(float))onFirstFrameReceived;
- (void)internalSetOnOrientationUpdated:(void (^ _Nullable)(int))onOrientationUpdated;
- (void)internalSetOnIsMirroredUpdated:(void (^ _Nullable)(bool))onIsMirroredUpdated;
- (void)setVideoContentMode:(CALayerContentsGravity)mode;
- (void)setIsForceMirrored:(BOOL)forceMirrored;
@end

NS_ASSUME_NONNULL_END

#endif
