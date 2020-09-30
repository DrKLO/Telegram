/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_VOIP_VOIP_BASE_H_
#define API_VOIP_VOIP_BASE_H_

#include "absl/types/optional.h"

namespace webrtc {

class Transport;

// VoipBase interface
//
// VoipBase provides a management interface on a media session using a
// concept called 'channel'.  A channel represents an interface handle
// for application to request various media session operations.  This
// notion of channel is used throughout other interfaces as well.
//
// Underneath the interface, a channel id is mapped into an audio session
// object that is capable of sending and receiving a single RTP stream with
// another media endpoint.  It's possible to create and use multiple active
// channels simultaneously which would mean that particular application
// session has RTP streams with multiple remote endpoints.
//
// A typical example for the usage context is outlined in VoipEngine
// header file.

enum class ChannelId : int {};

class VoipBase {
 public:
  // Creates a channel.
  // Each channel handle maps into one audio media session where each has
  // its own separate module for send/receive rtp packet with one peer.
  // Caller must set |transport|, webrtc::Transport callback pointer to
  // receive rtp/rtcp packets from corresponding media session in VoIP engine.
  // VoipEngine framework expects applications to handle network I/O directly
  // and injection for incoming RTP from remote endpoint is handled via
  // VoipNetwork interface. |local_ssrc| is optional and when local_ssrc is not
  // set, some random value will be used by voip engine.
  // Returns value is optional as to indicate the failure to create channel.
  virtual absl::optional<ChannelId> CreateChannel(
      Transport* transport,
      absl::optional<uint32_t> local_ssrc) = 0;

  // Releases |channel_id| that no longer has any use.
  virtual void ReleaseChannel(ChannelId channel_id) = 0;

  // Starts sending on |channel_id|. This will start microphone if not started
  // yet. Returns false if initialization has failed on selected microphone
  // device. API is subject to expand to reflect error condition to application
  // later.
  virtual bool StartSend(ChannelId channel_id) = 0;

  // Stops sending on |channel_id|. If this is the last active channel, it will
  // stop microphone input from underlying audio platform layer.
  // Returns false if termination logic has failed on selected microphone
  // device. API is subject to expand to reflect error condition to application
  // later.
  virtual bool StopSend(ChannelId channel_id) = 0;

  // Starts playing on speaker device for |channel_id|.
  // This will start underlying platform speaker device if not started.
  // Returns false if initialization has failed
  // on selected speaker device. API is subject to expand to reflect error
  // condition to application later.
  virtual bool StartPlayout(ChannelId channel_id) = 0;

  // Stops playing on speaker device for |channel_id|.
  // If this is the last active channel playing, then it will stop speaker
  // from the platform layer.
  // Returns false if termination logic has failed on selected speaker device.
  // API is subject to expand to reflect error condition to application later.
  virtual bool StopPlayout(ChannelId channel_id) = 0;

 protected:
  virtual ~VoipBase() = default;
};

}  // namespace webrtc

#endif  // API_VOIP_VOIP_BASE_H_
