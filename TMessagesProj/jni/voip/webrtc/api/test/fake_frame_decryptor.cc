/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/fake_frame_decryptor.h"

#include <vector>

#include "rtc_base/checks.h"

namespace webrtc {

FakeFrameDecryptor::FakeFrameDecryptor(uint8_t fake_key,
                                       uint8_t expected_postfix_byte)
    : fake_key_(fake_key), expected_postfix_byte_(expected_postfix_byte) {}

FakeFrameDecryptor::Result FakeFrameDecryptor::Decrypt(
    cricket::MediaType media_type,
    const std::vector<uint32_t>& csrcs,
    rtc::ArrayView<const uint8_t> additional_data,
    rtc::ArrayView<const uint8_t> encrypted_frame,
    rtc::ArrayView<uint8_t> frame) {
  if (fail_decryption_) {
    return Result(Status::kFailedToDecrypt, 0);
  }

  RTC_CHECK_EQ(frame.size() + 1, encrypted_frame.size());
  for (size_t i = 0; i < frame.size(); i++) {
    frame[i] = encrypted_frame[i] ^ fake_key_;
  }

  if (encrypted_frame[frame.size()] != expected_postfix_byte_) {
    return Result(Status::kFailedToDecrypt, 0);
  }

  return Result(Status::kOk, frame.size());
}

size_t FakeFrameDecryptor::GetMaxPlaintextByteSize(
    cricket::MediaType media_type,
    size_t encrypted_frame_size) {
  return encrypted_frame_size - 1;
}

void FakeFrameDecryptor::SetFakeKey(uint8_t fake_key) {
  fake_key_ = fake_key;
}

uint8_t FakeFrameDecryptor::GetFakeKey() const {
  return fake_key_;
}

void FakeFrameDecryptor::SetExpectedPostfixByte(uint8_t expected_postfix_byte) {
  expected_postfix_byte_ = expected_postfix_byte;
}

uint8_t FakeFrameDecryptor::GetExpectedPostfixByte() const {
  return expected_postfix_byte_;
}

void FakeFrameDecryptor::SetFailDecryption(bool fail_decryption) {
  fail_decryption_ = fail_decryption;
}

}  // namespace webrtc
