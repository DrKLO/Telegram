/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_RECEIVE_STREAM_H_
#define CALL_RECEIVE_STREAM_H_

#include <vector>

#include "api/crypto/frame_decryptor_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/media_types.h"
#include "api/scoped_refptr.h"
#include "api/transport/rtp/rtp_source.h"

namespace webrtc {

// Common base interface for MediaReceiveStream based classes and
// FlexfecReceiveStream.
class ReceiveStream {
 public:
  // Receive-stream specific RTP settings.
  struct RtpConfig {
    // Synchronization source (stream identifier) to be received.
    // This member will not change mid-stream and can be assumed to be const
    // post initialization.
    uint32_t remote_ssrc = 0;

    // Sender SSRC used for sending RTCP (such as receiver reports).
    // This value may change mid-stream and must be done on the same thread
    // that the value is read on (i.e. packet delivery).
    uint32_t local_ssrc = 0;

    // Enable feedback for send side bandwidth estimation.
    // See
    // https://tools.ietf.org/html/draft-holmer-rmcat-transport-wide-cc-extensions
    // for details.
    // This value may change mid-stream and must be done on the same thread
    // that the value is read on (i.e. packet delivery).
    bool transport_cc = false;

    // RTP header extensions used for the received stream.
    // This value may change mid-stream and must be done on the same thread
    // that the value is read on (i.e. packet delivery).
    std::vector<RtpExtension> extensions;
  };

  // Set/change the rtp header extensions. Must be called on the packet
  // delivery thread.
  virtual void SetRtpExtensions(std::vector<RtpExtension> extensions) = 0;

  // Called on the packet delivery thread since some members of the config may
  // change mid-stream (e.g. the local ssrc). All mutation must also happen on
  // the packet delivery thread. Return value can be assumed to
  // only be used in the calling context (on the stack basically).
  virtual const RtpConfig& rtp_config() const = 0;

 protected:
  virtual ~ReceiveStream() {}
};

// Either an audio or video receive stream.
class MediaReceiveStream : public ReceiveStream {
 public:
  // Starts stream activity.
  // When a stream is active, it can receive, process and deliver packets.
  virtual void Start() = 0;

  // Stops stream activity. Must be called to match with a previous call to
  // `Start()`. When a stream has been stopped, it won't receive, decode,
  // process or deliver packets to downstream objects such as callback pointers
  // set in the config struct.
  virtual void Stop() = 0;

  virtual void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface>
          frame_transformer) = 0;

  virtual void SetFrameDecryptor(
      rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor) = 0;

  virtual std::vector<RtpSource> GetSources() const = 0;
};

}  // namespace webrtc

#endif  // CALL_RECEIVE_STREAM_H_
