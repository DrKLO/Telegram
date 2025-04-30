/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "TGRTCDefaultVideoDecoderFactory.h"

#import "RTCH264ProfileLevelId.h"
#import "TGRTCVideoDecoderH264.h"
#import "api/video_codec/RTCVideoCodecConstants.h"
#import "api/video_codec/RTCVideoDecoderVP8.h"
#import "base/RTCVideoCodecInfo.h"
#if defined(RTC_ENABLE_VP9)
#import "api/video_codec/RTCVideoDecoderVP9.h"
#endif
#if !defined(WEBRTC_DISABLE_H265)
#import "RTCH265ProfileLevelId.h"
#import "TGRTCVideoDecoderH265.h"
#endif

#import "sdk/objc/api/video_codec/RTCWrappedNativeVideoDecoder.h"
#include "modules/video_coding/codecs/h264/include/h264.h"

@implementation TGRTCDefaultVideoDecoderFactory

- (NSArray<RTCVideoCodecInfo *> *)supportedCodecs {
    NSDictionary<NSString *, NSString *> *constrainedHighParams = @{
        @"profile-level-id" : kRTCMaxSupportedH264ProfileLevelConstrainedHigh,
        @"level-asymmetry-allowed" : @"1",
        @"packetization-mode" : @"1",
    };
    RTCVideoCodecInfo *constrainedHighInfo =
    [[RTCVideoCodecInfo alloc] initWithName:kRTCVideoCodecH264Name
                                 parameters:constrainedHighParams];
    
    NSDictionary<NSString *, NSString *> *constrainedBaselineParams = @{
        @"profile-level-id" : kRTCMaxSupportedH264ProfileLevelConstrainedBaseline,
        @"level-asymmetry-allowed" : @"1",
        @"packetization-mode" : @"1",
    };
    RTCVideoCodecInfo *constrainedBaselineInfo =
    [[RTCVideoCodecInfo alloc] initWithName:kRTCVideoCodecH264Name
                                 parameters:constrainedBaselineParams];
    
    RTCVideoCodecInfo *vp8Info = [[RTCVideoCodecInfo alloc] initWithName:kRTCVideoCodecVp8Name];
    
#if defined(RTC_ENABLE_VP9)
    RTCVideoCodecInfo *vp9Info = [[RTCVideoCodecInfo alloc] initWithName:kRTCVideoCodecVp9Name];
#endif
    
#if !defined(WEBRTC_DISABLE_H265)
    RTCVideoCodecInfo *h265Info = [[RTCVideoCodecInfo alloc] initWithName:kRTCVideoCodecH265Name];
#endif
    
    NSMutableArray<RTCVideoCodecInfo *> *result = [[NSMutableArray alloc] initWithArray:@[
        constrainedHighInfo,
        constrainedBaselineInfo,
        vp8Info,
#if defined(RTC_ENABLE_VP9)
        vp9Info,
#endif
    ]];
    
#if !defined(WEBRTC_DISABLE_H265)
#ifdef WEBRTC_IOS
    if (@available(iOS 11.0, *)) {
        [result addObject:h265Info];
    }
#else // WEBRTC_IOS
    if (@available(macOS 10.13, *)) {
        [result addObject:h265Info];
    }
#endif // WEBRTC_IOS
#endif
    
    return result;
}

- (id<RTCVideoDecoder>)createDecoder:(RTCVideoCodecInfo *)info {
  if ([info.name isEqualToString:kRTCVideoCodecH264Name]) {
    return [[TGRTCVideoDecoderH264 alloc] init];
  } else if ([info.name isEqualToString:kRTCVideoCodecVp8Name]) {
    return [RTCVideoDecoderVP8 vp8Decoder];
  }

#if defined(RTC_ENABLE_VP9)
  if ([info.name isEqualToString:kRTCVideoCodecVp9Name]) {
    return [RTCVideoDecoderVP9 vp9Decoder];
  }
#endif

#if !defined(WEBRTC_DISABLE_H265)
#ifdef WEBRTC_IOS
  if (@available(iOS 11.0, *)) {
    if ([info.name isEqualToString:kRTCVideoCodecH265Name]) {
      return [[TGRTCVideoDecoderH265 alloc] init];
    }
  }
#else // WEBRTC_IOS
  if (@available(macOS 10.13, *)) {
    if ([info.name isEqualToString:kRTCVideoCodecH265Name]) {
      return [[TGRTCVideoDecoderH265 alloc] init];
    }
  }
#endif // WEBRTC_IOS
#endif // !DISABLE_H265

  return nil;
}

@end
