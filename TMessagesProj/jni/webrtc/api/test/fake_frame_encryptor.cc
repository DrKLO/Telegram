/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/fake_frame_encryptor.h"

#include "rtc_base/checks.h"

namespace webrtc {
FakeFrameEncryptor::FakeFrameEncryptor(uint8_t fake_key, uint8_t postfix_byte)
    : fake_key_(fake_key), postfix_byte_(postfix_byte) {}

// FrameEncryptorInterface implementation
int FakeFrameEncryptor::Encrypt(cricket::MediaType media_type,
                                uint32_t ssrc,
                                rtc::ArrayView<const uint8_t> additional_data,
                                rtc::ArrayView<const uint8_t> frame,
                                rtc::ArrayView<uint8_t> encrypted_frame,
                                size_t* bytes_written) {
  if (fail_encryption_) {
    return static_cast<int>(FakeEncryptionStatus::FORCED_FAILURE);
  }

  RTC_CHECK_EQ(frame.size() + 1, encrypted_frame.size());
  for (size_t i = 0; i < frame.size(); i++) {
    encrypted_frame[i] = frame[i] ^ fake_key_;
  }

  encrypted_frame[frame.size()] = postfix_byte_;
  *bytes_written = encrypted_frame.size();
  return static_cast<int>(FakeEncryptionStatus::OK);
}

size_t FakeFrameEncryptor::GetMaxCiphertextByteSize(
    cricket::MediaType media_type,
    size_t frame_size) {
  return frame_size + 1;
}

void FakeFrameEncryptor::SetFakeKey(uint8_t fake_key) {
  fake_key_ = fake_key;
}

uint8_t FakeFrameEncryptor::GetFakeKey() const {
  return fake_key_;
}

void FakeFrameEncryptor::SetPostfixByte(uint8_t postfix_byte) {
  postfix_byte_ = postfix_byte;
}

uint8_t FakeFrameEncryptor::GetPostfixByte() const {
  return postfix_byte_;
}

void FakeFrameEncryptor::SetFailEncryption(bool fail_encryption) {
  fail_encryption_ = fail_encryption;
}

}  // namespace webrtc
