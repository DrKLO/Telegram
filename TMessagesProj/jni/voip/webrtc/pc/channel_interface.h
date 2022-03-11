/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_CHANNEL_INTERFACE_H_
#define PC_CHANNEL_INTERFACE_H_

#include <string>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/jsep.h"
#include "api/media_types.h"
#include "media/base/media_channel.h"
#include "pc/rtp_transport_internal.h"

namespace webrtc {
class Call;
class VideoBitrateAllocatorFactory;
}  // namespace webrtc

namespace cricket {

class MediaContentDescription;
class VideoChannel;
class VoiceChannel;
struct MediaConfig;

// A Channel is a construct that groups media streams of the same type
// (audio or video), both outgoing and incoming.
// When the PeerConnection API is used, a Channel corresponds one to one
// to an RtpTransceiver.
// When Unified Plan is used, there can only be at most one outgoing and
// one incoming stream. With Plan B, there can be more than one.

// ChannelInterface contains methods common to voice and video channels.
// As more methods are added to BaseChannel, they should be included in the
// interface as well.
class ChannelInterface {
 public:
  virtual cricket::MediaType media_type() const = 0;

  virtual MediaChannel* media_channel() const = 0;

  // Returns a string view for the transport name. Fetching the transport name
  // must be done on the network thread only and note that the lifetime of
  // the returned object should be assumed to only be the calling scope.
  // TODO(deadbeef): This is redundant; remove this.
  virtual absl::string_view transport_name() const = 0;

  // TODO(tommi): Change return type to string_view.
  virtual const std::string& mid() const = 0;

  // Enables or disables this channel
  virtual void Enable(bool enable) = 0;

  // Used for latency measurements.
  virtual void SetFirstPacketReceivedCallback(
      std::function<void()> callback) = 0;

  // Channel control
  virtual bool SetLocalContent(const MediaContentDescription* content,
                               webrtc::SdpType type,
                               std::string& error_desc) = 0;
  virtual bool SetRemoteContent(const MediaContentDescription* content,
                                webrtc::SdpType type,
                                std::string& error_desc) = 0;
  virtual bool SetPayloadTypeDemuxingEnabled(bool enabled) = 0;

  // Access to the local and remote streams that were set on the channel.
  virtual const std::vector<StreamParams>& local_streams() const = 0;
  virtual const std::vector<StreamParams>& remote_streams() const = 0;

  // Set an RTP level transport.
  // Some examples:
  //   * An RtpTransport without encryption.
  //   * An SrtpTransport for SDES.
  //   * A DtlsSrtpTransport for DTLS-SRTP.
  virtual bool SetRtpTransport(webrtc::RtpTransportInternal* rtp_transport) = 0;

 protected:
  virtual ~ChannelInterface() = default;
};

class ChannelFactoryInterface {
 public:
  virtual VideoChannel* CreateVideoChannel(
      webrtc::Call* call,
      const MediaConfig& media_config,
      const std::string& mid,
      bool srtp_required,
      const webrtc::CryptoOptions& crypto_options,
      const VideoOptions& options,
      webrtc::VideoBitrateAllocatorFactory*
          video_bitrate_allocator_factory) = 0;

  virtual VoiceChannel* CreateVoiceChannel(
      webrtc::Call* call,
      const MediaConfig& media_config,
      const std::string& mid,
      bool srtp_required,
      const webrtc::CryptoOptions& crypto_options,
      const AudioOptions& options) = 0;

  virtual void DestroyChannel(ChannelInterface* channel) = 0;

 protected:
  virtual ~ChannelFactoryInterface() = default;
};

}  // namespace cricket

#endif  // PC_CHANNEL_INTERFACE_H_
