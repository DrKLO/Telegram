/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#import "RTCH265ProfileLevelId.h"

#include "media/base/media_constants.h"

NSString *const kRTCVideoCodecH265Name = @(cricket::kH265CodecName);
// TODO(jianjunz): This is value is not correct.
NSString *const kRTCLevel31Main = @"4d001f";
