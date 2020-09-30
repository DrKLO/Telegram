/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_FRAME_DECRYPTOR_H_
#define API_TEST_MOCK_FRAME_DECRYPTOR_H_

#include <vector>

#include "api/crypto/frame_decryptor_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockFrameDecryptor : public FrameDecryptorInterface {
 public:
  MOCK_METHOD(Result,
              Decrypt,
              (cricket::MediaType,
               const std::vector<uint32_t>&,
               rtc::ArrayView<const uint8_t>,
               rtc::ArrayView<const uint8_t>,
               rtc::ArrayView<uint8_t>),
              (override));

  MOCK_METHOD(size_t,
              GetMaxPlaintextByteSize,
              (cricket::MediaType, size_t encrypted_frame_size),
              (override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_FRAME_DECRYPTOR_H_
