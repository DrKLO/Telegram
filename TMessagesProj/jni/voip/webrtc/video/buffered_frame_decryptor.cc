/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/buffered_frame_decryptor.h"

#include <utility>
#include <vector>

#include "modules/rtp_rtcp/source/rtp_descriptor_authentication.h"
#include "modules/video_coding/frame_object.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

BufferedFrameDecryptor::BufferedFrameDecryptor(
    OnDecryptedFrameCallback* decrypted_frame_callback,
    OnDecryptionStatusChangeCallback* decryption_status_change_callback)
    : generic_descriptor_auth_experiment_(
          !field_trial::IsDisabled("WebRTC-GenericDescriptorAuth")),
      decrypted_frame_callback_(decrypted_frame_callback),
      decryption_status_change_callback_(decryption_status_change_callback) {}

BufferedFrameDecryptor::~BufferedFrameDecryptor() {}

void BufferedFrameDecryptor::SetFrameDecryptor(
    rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) {
  frame_decryptor_ = std::move(frame_decryptor);
}

void BufferedFrameDecryptor::ManageEncryptedFrame(
    std::unique_ptr<video_coding::RtpFrameObject> encrypted_frame) {
  switch (DecryptFrame(encrypted_frame.get())) {
    case FrameDecision::kStash:
      if (stashed_frames_.size() >= kMaxStashedFrames) {
        RTC_LOG(LS_WARNING) << "Encrypted frame stash full poping oldest item.";
        stashed_frames_.pop_front();
      }
      stashed_frames_.push_back(std::move(encrypted_frame));
      break;
    case FrameDecision::kDecrypted:
      RetryStashedFrames();
      decrypted_frame_callback_->OnDecryptedFrame(std::move(encrypted_frame));
      break;
    case FrameDecision::kDrop:
      break;
  }
}

BufferedFrameDecryptor::FrameDecision BufferedFrameDecryptor::DecryptFrame(
    video_coding::RtpFrameObject* frame) {
  // Optionally attempt to decrypt the raw video frame if it was provided.
  if (frame_decryptor_ == nullptr) {
    RTC_LOG(LS_INFO) << "Frame decryption required but not attached to this "
                        "stream. Stashing frame.";
    return FrameDecision::kStash;
  }
  // When using encryption we expect the frame to have the generic descriptor.
  if (frame->GetRtpVideoHeader().generic == absl::nullopt) {
    RTC_LOG(LS_ERROR) << "No generic frame descriptor found dropping frame.";
    return FrameDecision::kDrop;
  }
  // Retrieve the maximum possible size of the decrypted payload.
  const size_t max_plaintext_byte_size =
      frame_decryptor_->GetMaxPlaintextByteSize(cricket::MEDIA_TYPE_VIDEO,
                                                frame->size());
  RTC_CHECK_LE(max_plaintext_byte_size, frame->size());
  // Place the decrypted frame inline into the existing frame.
  rtc::ArrayView<uint8_t> inline_decrypted_bitstream(frame->mutable_data(),
                                                     max_plaintext_byte_size);

  // Enable authenticating the header if the field trial isn't disabled.
  std::vector<uint8_t> additional_data;
  if (generic_descriptor_auth_experiment_) {
    additional_data = RtpDescriptorAuthentication(frame->GetRtpVideoHeader());
  }

  // Attempt to decrypt the video frame.
  const FrameDecryptorInterface::Result decrypt_result =
      frame_decryptor_->Decrypt(cricket::MEDIA_TYPE_VIDEO, /*csrcs=*/{},
                                additional_data, *frame,
                                inline_decrypted_bitstream);
  // Optionally call the callback if there was a change in status
  if (decrypt_result.status != last_status_) {
    last_status_ = decrypt_result.status;
    decryption_status_change_callback_->OnDecryptionStatusChange(
        decrypt_result.status);
  }

  if (!decrypt_result.IsOk()) {
    // Only stash frames if we have never decrypted a frame before.
    return first_frame_decrypted_ ? FrameDecision::kDrop
                                  : FrameDecision::kStash;
  }
  RTC_CHECK_LE(decrypt_result.bytes_written, max_plaintext_byte_size);
  // Update the frame to contain just the written bytes.
  frame->set_size(decrypt_result.bytes_written);

  // Indicate that all future fail to decrypt frames should be dropped.
  if (!first_frame_decrypted_) {
    first_frame_decrypted_ = true;
  }

  return FrameDecision::kDecrypted;
}

void BufferedFrameDecryptor::RetryStashedFrames() {
  if (!stashed_frames_.empty()) {
    RTC_LOG(LS_INFO) << "Retrying stashed encrypted frames. Count: "
                     << stashed_frames_.size();
  }
  for (auto& frame : stashed_frames_) {
    if (DecryptFrame(frame.get()) == FrameDecision::kDecrypted) {
      decrypted_frame_callback_->OnDecryptedFrame(std::move(frame));
    }
  }
  stashed_frames_.clear();
}

}  // namespace webrtc
