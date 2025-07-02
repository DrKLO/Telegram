/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
/* This file is borrowed from sdk/objc/components/video_codec/RTCCodecSpecificInfoH264+Private.h */

#import "RTCCodecSpecificInfoH265.h"

#include "modules/video_coding/include/video_codec_interface.h"

NS_ASSUME_NONNULL_BEGIN

/* Interfaces for converting to/from internal C++ formats. */
@interface RTCCodecSpecificInfoH265 ()

- (webrtc::CodecSpecificInfo)nativeCodecSpecificInfo;

@end

NS_ASSUME_NONNULL_END
