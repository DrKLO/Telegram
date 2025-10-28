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

#import "RTCMacros.h"
#import "RTCVideoCodecInfo.h"
#import "RTCVideoEncoder.h"

RTC_OBJC_EXPORT
@interface TGRTCVideoEncoderH264 : NSObject <RTCVideoEncoder>

- (instancetype)initWithCodecInfo:(RTCVideoCodecInfo *)codecInfo;

@end
