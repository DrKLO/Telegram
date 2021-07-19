/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_FRAME_ENCRYPTOR_H_
#define API_TEST_MOCK_FRAME_ENCRYPTOR_H_

#include "api/crypto/frame_encryptor_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockFrameEncryptor : public FrameEncryptorInterface {
 public:
  MOCK_METHOD(int,
              Encrypt,
              (cricket::MediaType,
               uint32_t,
               rtc::ArrayView<const uint8_t>,
               rtc::ArrayView<const uint8_t>,
               rtc::ArrayView<uint8_t>,
               size_t*),
              (override));

  MOCK_METHOD(size_t,
              GetMaxCiphertextByteSize,
              (cricket::MediaType media_type, size_t frame_size),
              (override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_FRAME_ENCRYPTOR_H_
