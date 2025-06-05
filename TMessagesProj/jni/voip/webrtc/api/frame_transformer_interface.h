/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_FRAME_TRANSFORMER_INTERFACE_H_
#define API_FRAME_TRANSFORMER_INTERFACE_H_

#include <memory>
#include <string>

#include "api/ref_count.h"
#include "api/scoped_refptr.h"
#include "api/video/encoded_frame.h"
#include "api/video/video_frame_metadata.h"

namespace webrtc {

// Owns the frame payload data.
class TransformableFrameInterface {
 public:
  virtual ~TransformableFrameInterface() = default;

  // Returns the frame payload data. The data is valid until the next non-const
  // method call.
  virtual rtc::ArrayView<const uint8_t> GetData() const = 0;

  // Copies `data` into the owned frame payload data.
  virtual void SetData(rtc::ArrayView<const uint8_t> data) = 0;

  virtual uint8_t GetPayloadType() const = 0;
  virtual uint32_t GetSsrc() const = 0;
  virtual uint32_t GetTimestamp() const = 0;
  virtual void SetRTPTimestamp(uint32_t timestamp) = 0;

  // TODO(https://bugs.webrtc.org/14878): Change this to pure virtual after it
  // is implemented everywhere.
  virtual absl::optional<Timestamp> GetCaptureTimeIdentifier() const {
    return absl::nullopt;
  }

  enum class Direction {
    kUnknown,
    kReceiver,
    kSender,
  };
  // TODO(crbug.com/1250638): Remove this distinction between receiver and
  // sender frames to allow received frames to be directly re-transmitted on
  // other PeerConnectionss.
  virtual Direction GetDirection() const { return Direction::kUnknown; }
  virtual std::string GetMimeType() const = 0;
};

class TransformableVideoFrameInterface : public TransformableFrameInterface {
 public:
  virtual ~TransformableVideoFrameInterface() = default;
  virtual bool IsKeyFrame() const = 0;

  virtual VideoFrameMetadata Metadata() const = 0;

  virtual void SetMetadata(const VideoFrameMetadata&) = 0;
};

// Extends the TransformableFrameInterface to expose audio-specific information.
class TransformableAudioFrameInterface : public TransformableFrameInterface {
 public:
  virtual ~TransformableAudioFrameInterface() = default;

  virtual rtc::ArrayView<const uint32_t> GetContributingSources() const = 0;

  virtual const absl::optional<uint16_t> SequenceNumber() const = 0;

  virtual absl::optional<uint64_t> AbsoluteCaptureTimestamp() const = 0;

  enum class FrameType { kEmptyFrame, kAudioFrameSpeech, kAudioFrameCN };

  // TODO(crbug.com/1456628): Change this to pure virtual after it
  // is implemented everywhere.
  virtual FrameType Type() const { return FrameType::kEmptyFrame; }
};

// Objects implement this interface to be notified with the transformed frame.
class TransformedFrameCallback : public rtc::RefCountInterface {
 public:
  virtual void OnTransformedFrame(
      std::unique_ptr<TransformableFrameInterface> frame) = 0;

  // Request to no longer be called on each frame, instead having frames be
  // sent directly to OnTransformedFrame without additional work.
  // TODO(crbug.com/1502781): Make pure virtual once all mocks have
  // implementations.
  virtual void StartShortCircuiting() {}

 protected:
  ~TransformedFrameCallback() override = default;
};

// Transforms encoded frames. The transformed frame is sent in a callback using
// the TransformedFrameCallback interface (see above).
class FrameTransformerInterface : public rtc::RefCountInterface {
 public:
  // Transforms `frame` using the implementing class' processing logic.
  virtual void Transform(
      std::unique_ptr<TransformableFrameInterface> transformable_frame) = 0;

  virtual void RegisterTransformedFrameCallback(
      rtc::scoped_refptr<TransformedFrameCallback>) {}
  virtual void RegisterTransformedFrameSinkCallback(
      rtc::scoped_refptr<TransformedFrameCallback>,
      uint32_t ssrc) {}
  virtual void UnregisterTransformedFrameCallback() {}
  virtual void UnregisterTransformedFrameSinkCallback(uint32_t ssrc) {}

 protected:
  ~FrameTransformerInterface() override = default;
};

}  // namespace webrtc

#endif  // API_FRAME_TRANSFORMER_INTERFACE_H_
