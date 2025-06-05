/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_MEDIA_CHANNEL_IMPL_H_
#define MEDIA_BASE_MEDIA_CHANNEL_IMPL_H_

#include <stddef.h>
#include <stdint.h>

#include <functional>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/functional/any_invocable.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/audio_options.h"
#include "api/call/audio_sink.h"
#include "api/call/transport.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/crypto/frame_encryptor_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/media_types.h"
#include "api/rtc_error.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "api/rtp_sender_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "api/transport/rtp/rtp_source.h"
#include "api/video/recordable_encoded_frame.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/stream_params.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/dscp.h"
#include "rtc_base/logging.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/network_route.h"
#include "rtc_base/socket.h"
#include "rtc_base/thread_annotations.h"
// This file contains the base classes for classes that implement
// the channel interfaces.
// These implementation classes used to be the exposed interface names,
// but this is in the process of being changed.

namespace cricket {

// The `MediaChannelUtil` class provides functionality that is used by
// multiple MediaChannel-like objects, of both sending and receiving
// types.
class MediaChannelUtil {
 public:
  MediaChannelUtil(webrtc::TaskQueueBase* network_thread,
                   bool enable_dscp = false);
  virtual ~MediaChannelUtil();
  // Returns the absolute sendtime extension id value from media channel.
  virtual int GetRtpSendTimeExtnId() const;

  webrtc::Transport* transport() { return &transport_; }

  // Base methods to send packet using MediaChannelNetworkInterface.
  // These methods are used by some tests only.
  bool SendPacket(rtc::CopyOnWriteBuffer* packet,
                  const rtc::PacketOptions& options);

  bool SendRtcp(rtc::CopyOnWriteBuffer* packet,
                const rtc::PacketOptions& options);

  int SetOption(MediaChannelNetworkInterface::SocketType type,
                rtc::Socket::Option opt,
                int option);

  // Functions that form part of one or more interface classes.
  // Not marked override, since this class does not inherit from the
  // interfaces.

  // Corresponds to the SDP attribute extmap-allow-mixed, see RFC8285.
  // Set to true if it's allowed to mix one- and two-byte RTP header extensions
  // in the same stream. The setter and getter must only be called from
  // worker_thread.
  void SetExtmapAllowMixed(bool extmap_allow_mixed);
  bool ExtmapAllowMixed() const;

  void SetInterface(MediaChannelNetworkInterface* iface);
  // Returns `true` if a non-null MediaChannelNetworkInterface pointer is held.
  // Must be called on the network thread.
  bool HasNetworkInterface() const;

 protected:
  bool DscpEnabled() const;

  void SetPreferredDscp(rtc::DiffServCodePoint new_dscp);

 private:
  // Implementation of the webrtc::Transport interface required
  // by Call().
  class TransportForMediaChannels : public webrtc::Transport {
   public:
    TransportForMediaChannels(webrtc::TaskQueueBase* network_thread,
                              bool enable_dscp);

    virtual ~TransportForMediaChannels();

    // Implementation of webrtc::Transport
    bool SendRtp(rtc::ArrayView<const uint8_t> packet,
                 const webrtc::PacketOptions& options) override;
    bool SendRtcp(rtc::ArrayView<const uint8_t> packet) override;

    // Not implementation of webrtc::Transport
    void SetInterface(MediaChannelNetworkInterface* iface);

    int SetOption(MediaChannelNetworkInterface::SocketType type,
                  rtc::Socket::Option opt,
                  int option);

    bool DoSendPacket(rtc::CopyOnWriteBuffer* packet,
                      bool rtcp,
                      const rtc::PacketOptions& options);

    bool HasNetworkInterface() const {
      RTC_DCHECK_RUN_ON(network_thread_);
      return network_interface_ != nullptr;
    }
    bool DscpEnabled() const { return enable_dscp_; }

    void SetPreferredDscp(rtc::DiffServCodePoint new_dscp);

   private:
    // This is the DSCP value used for both RTP and RTCP channels if DSCP is
    // enabled. It can be changed at any time via `SetPreferredDscp`.
    rtc::DiffServCodePoint PreferredDscp() const {
      RTC_DCHECK_RUN_ON(network_thread_);
      return preferred_dscp_;
    }

    // Apply the preferred DSCP setting to the underlying network interface RTP
    // and RTCP channels. If DSCP is disabled, then apply the default DSCP
    // value.
    void UpdateDscp() RTC_RUN_ON(network_thread_);

    int SetOptionLocked(MediaChannelNetworkInterface::SocketType type,
                        rtc::Socket::Option opt,
                        int option) RTC_RUN_ON(network_thread_);

    const rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> network_safety_
        RTC_PT_GUARDED_BY(network_thread_);
    webrtc::TaskQueueBase* const network_thread_;
    const bool enable_dscp_;
    MediaChannelNetworkInterface* network_interface_
        RTC_GUARDED_BY(network_thread_) = nullptr;
    rtc::DiffServCodePoint preferred_dscp_ RTC_GUARDED_BY(network_thread_) =
        rtc::DSCP_DEFAULT;
  };

  bool extmap_allow_mixed_ = false;
  TransportForMediaChannels transport_;
};

}  // namespace cricket

#endif  // MEDIA_BASE_MEDIA_CHANNEL_IMPL_H_
