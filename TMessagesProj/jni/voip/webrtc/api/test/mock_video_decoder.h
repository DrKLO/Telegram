/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_VIDEO_DECODER_H_
#define API_TEST_MOCK_VIDEO_DECODER_H_

#include "api/video_codecs/video_decoder.h"
#include "test/gmock.h"

namespace webrtc {

class MockDecodedImageCallback : public DecodedImageCallback {
 public:
  MOCK_METHOD(int32_t,
              Decoded,
              (VideoFrame & decoded_image),  // NOLINT
              (override));
  MOCK_METHOD(int32_t,
              Decoded,
              (VideoFrame & decoded_image,  // NOLINT
               int64_t decode_time_ms),
              (override));
  MOCK_METHOD(void,
              Decoded,
              (VideoFrame & decoded_image,  // NOLINT
               absl::optional<int32_t> decode_time_ms,
               absl::optional<uint8_t> qp),
              (override));
};

class MockVideoDecoder : public VideoDecoder {
 public:
  MOCK_METHOD(int32_t,
              InitDecode,
              (const VideoCodec* codec_settings, int32_t number_of_cores),
              (override));
  MOCK_METHOD(int32_t,
              Decode,
              (const EncodedImage& input_image,
               bool missing_frames,
               int64_t render_time_ms),
              (override));
  MOCK_METHOD(int32_t,
              RegisterDecodeCompleteCallback,
              (DecodedImageCallback * callback),
              (override));
  MOCK_METHOD(int32_t, Release, (), (override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_VIDEO_DECODER_H_
