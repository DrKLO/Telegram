/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_PROCESSING_UTIL_SKIN_DETECTION_H_
#define MODULES_VIDEO_PROCESSING_UTIL_SKIN_DETECTION_H_

namespace webrtc {

#define MODEL_MODE 0

typedef unsigned char uint8_t;
bool MbHasSkinColor(const uint8_t* y_src,
                    const uint8_t* u_src,
                    const uint8_t* v_src,
                    int stride_y,
                    int stride_u,
                    int stride_v,
                    int mb_row,
                    int mb_col);

}  // namespace webrtc

#endif  // MODULES_VIDEO_PROCESSING_UTIL_SKIN_DETECTION_H_
