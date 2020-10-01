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

#include "api/jsep.h"
#include "api/media_types.h"
#include "media/base/media_channel.h"
#include "pc/rtp_transport_internal.h"

namespace cricket {

class MediaContentDescription;

// ChannelInterface contains methods common to voice, video and data channels.
// As more methods are added to BaseChannel, they should be included in the
// interface as well.
class ChannelInterface {
 public:
  virtual cricket::MediaType media_type() const = 0;

  virtual MediaChannel* media_channel() const = 0;

  // TODO(deadbeef): This is redundant; remove this.
  virtual const std::string& transport_name() const = 0;

  virtual const std::string& content_name() const = 0;

  virtual bool enabled() const = 0;

  // Enables or disables this channel
  virtual bool Enable(bool enable) = 0;

  // Used for latency measurements.
  virtual sigslot::signal1<ChannelInterface*>& SignalFirstPacketReceived() = 0;

  // Channel control
  virtual bool SetLocalContent(const MediaContentDescription* content,
                               webrtc::SdpType type,
                               std::string* error_desc) = 0;
  virtual bool SetRemoteContent(const MediaContentDescription* content,
                                webrtc::SdpType type,
                                std::string* error_desc) = 0;

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

}  // namespace cricket

#endif  // PC_CHANNEL_INTERFACE_H_
