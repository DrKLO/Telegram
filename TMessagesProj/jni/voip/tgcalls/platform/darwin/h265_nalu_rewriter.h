/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 *
 */

#ifndef TGCALLS_PLATFORM_DARWIN_H265_NALU_REWRITER_H_
#define TGCALLS_PLATFORM_DARWIN_H265_NALU_REWRITER_H_

#include "modules/video_coding/codecs/h264/include/h264.h"

#include <CoreMedia/CoreMedia.h>
#include <vector>

#include "common_video/h264/h264_common.h"
#include "common_video/h265/h265_common.h"
#include "rtc_base/buffer.h"

using webrtc::H264::NaluIndex;

namespace webrtc {

bool H265CMSampleBufferToAnnexBBuffer(
    CMSampleBufferRef hvcc_sample_buffer,
    bool is_keyframe,
    rtc::Buffer* annexb_buffer)
    __OSX_AVAILABLE_STARTING(__MAC_10_12, __IPHONE_11_0);

 // Converts a buffer received from RTP into a sample buffer suitable for the
// VideoToolbox decoder. The RTP buffer is in annex b format whereas the sample
// buffer is in hvcc format.
// If |is_keyframe| is true then |video_format| is ignored since the format will
// be read from the buffer. Otherwise |video_format| must be provided.
// Caller is responsible for releasing the created sample buffer.
bool H265AnnexBBufferToCMSampleBuffer(const uint8_t* annexb_buffer,
                                      size_t annexb_buffer_size,
                                      CMVideoFormatDescriptionRef video_format,
                                      CMSampleBufferRef* out_sample_buffer)
    __OSX_AVAILABLE_STARTING(__MAC_10_12, __IPHONE_11_0);

CMVideoFormatDescriptionRef CreateH265VideoFormatDescription(
    const uint8_t* annexb_buffer,
    size_t annexb_buffer_size)
    __OSX_AVAILABLE_STARTING(__MAC_10_12, __IPHONE_11_0);

}  // namespace webrtc

#endif  // TGCALLS_PLATFORM_DARWIN_H265_NALU_REWRITER_H_
