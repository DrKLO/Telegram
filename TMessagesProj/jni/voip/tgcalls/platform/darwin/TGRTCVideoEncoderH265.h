/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef WEBRTC_DISABLE_H265

#import <Foundation/Foundation.h>

#import "RTCMacros.h"
#import "RTCVideoCodecInfo.h"
#import "RTCVideoEncoder.h"

RTC_OBJC_EXPORT
API_AVAILABLE(ios(11.0))
@interface RTCVideoEncoderH265 : NSObject <RTCVideoEncoder>

- (instancetype _Nonnull)initWithCodecInfo:(RTCVideoCodecInfo * _Nonnull)codecInfo;

- (nullable RTC_OBJC_TYPE(RTCVideoEncoderQpThresholds) *)scalingSettings;

/** Resolutions should be aligned to this value. */
@property(nonatomic, readonly) NSInteger resolutionAlignment;

/** If enabled, resolution alignment is applied to all simulcast layers simultaneously so that when
    scaled, all resolutions comply with 'resolutionAlignment'. */
@property(nonatomic, readonly) BOOL applyAlignmentToAllSimulcastLayers;

/** If YES, the reciever is expected to resample/scale the source texture to the expected output
    size. */
@property(nonatomic, readonly) BOOL supportsNativeHandle;

@end

#endif
