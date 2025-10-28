/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
 /* This file is borrowed from sdk/objc/components/video_codec/RTCCodecSpecificInfoH264.mm */

#import "RTCCodecSpecificInfoH265+Private.h"

// H265 specific settings.
@implementation RTCCodecSpecificInfoH265

@synthesize packetizationMode = _packetizationMode;

- (webrtc::CodecSpecificInfo)nativeCodecSpecificInfo {
  webrtc::CodecSpecificInfo codecSpecificInfo;
  codecSpecificInfo.codecType = webrtc::kVideoCodecH265;
  //codecSpecificInfo.codecSpecific.H265.packetization_mode = (webrtc::H265PacketizationMode)_packetizationMode;

  return codecSpecificInfo;
}

@end
