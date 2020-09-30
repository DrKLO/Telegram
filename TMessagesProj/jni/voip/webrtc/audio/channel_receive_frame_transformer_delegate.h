/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_CHANNEL_RECEIVE_FRAME_TRANSFORMER_DELEGATE_H_
#define AUDIO_CHANNEL_RECEIVE_FRAME_TRANSFORMER_DELEGATE_H_

#include <memory>

#include "api/frame_transformer_interface.h"
#include "rtc_base/synchronization/sequence_checker.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/thread.h"

namespace webrtc {

// Delegates calls to FrameTransformerInterface to transform frames, and to
// ChannelReceive to receive the transformed frames using the
// |receive_frame_callback_| on the |channel_receive_thread_|.
class ChannelReceiveFrameTransformerDelegate : public TransformedFrameCallback {
 public:
  using ReceiveFrameCallback =
      std::function<void(rtc::ArrayView<const uint8_t> packet,
                         const RTPHeader& header)>;
  ChannelReceiveFrameTransformerDelegate(
      ReceiveFrameCallback receive_frame_callback,
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer,
      rtc::Thread* channel_receive_thread);

  // Registers |this| as callback for |frame_transformer_|, to get the
  // transformed frames.
  void Init();

  // Unregisters and releases the |frame_transformer_| reference, and resets
  // |receive_frame_callback_| on |channel_receive_thread_|. Called from
  // ChannelReceive destructor to prevent running the callback on a dangling
  // channel.
  void Reset();

  // Delegates the call to FrameTransformerInterface::Transform, to transform
  // the frame asynchronously.
  void Transform(rtc::ArrayView<const uint8_t> packet,
                 const RTPHeader& header,
                 uint32_t ssrc);

  // Implements TransformedFrameCallback. Can be called on any thread.
  void OnTransformedFrame(
      std::unique_ptr<TransformableFrameInterface> frame) override;

  // Delegates the call to ChannelReceive::OnReceivedPayloadData on the
  // |channel_receive_thread_|, by calling |receive_frame_callback_|.
  void ReceiveFrame(std::unique_ptr<TransformableFrameInterface> frame) const;

 protected:
  ~ChannelReceiveFrameTransformerDelegate() override = default;

 private:
  SequenceChecker sequence_checker_;
  ReceiveFrameCallback receive_frame_callback_
      RTC_GUARDED_BY(sequence_checker_);
  rtc::scoped_refptr<FrameTransformerInterface> frame_transformer_
      RTC_GUARDED_BY(sequence_checker_);
  rtc::Thread* channel_receive_thread_;
};

}  // namespace webrtc
#endif  // AUDIO_CHANNEL_RECEIVE_FRAME_TRANSFORMER_DELEGATE_H_
