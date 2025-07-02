/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import <Foundation/Foundation.h>
#import <AppKit/AppKit.h>
#import <MetalKit/MetalKit.h>

#import "base/RTCVideoFrame.h"

bool initMetal();

struct MTLFrameSize {
    float width = 0;
    float height = 0;
};
MTLFrameSize MTLAspectFilled(MTLFrameSize from, MTLFrameSize to);
MTLFrameSize MTLAspectFitted(MTLFrameSize from, MTLFrameSize to);

NS_ASSUME_NONNULL_BEGIN

typedef enum {
    TGRTCMTLRenderModeSinge,
    TGRTCMTLRenderModeDouble
} TGRTCMTLRenderMode;

@protocol TGRTCMTLRenderer <NSObject>

- (void)drawFrame:(RTC_OBJC_TYPE(RTCVideoFrame) *)frame;
- (BOOL)setSingleRendering:(__kindof CAMetalLayer *)view;
- (BOOL)setDoubleRendering:(__kindof CAMetalLayer *)view foreground: (__kindof CAMetalLayer *)foreground;

@end


NS_AVAILABLE(10_11, 9_0)
@interface TGRTCMTLRenderer : NSObject <TGRTCMTLRenderer>

@property(atomic, nullable) NSValue *rotationOverride;

@end

NS_ASSUME_NONNULL_END
