/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/media_channel_impl.h"

#include <map>
#include <string>
#include <type_traits>
#include <utility>

#include "absl/functional/any_invocable.h"
#include "api/audio_options.h"
#include "api/media_stream_interface.h"
#include "api/rtc_error.h"
#include "api/rtp_sender_interface.h"
#include "api/units/time_delta.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/scalability_mode.h"
#include "common_video/include/quality_limitation_reason.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/rtp_utils.h"
#include "media/base/stream_params.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "rtc_base/checks.h"

namespace webrtc {

webrtc::RTCError InvokeSetParametersCallback(SetParametersCallback& callback,
                                             RTCError error) {
  if (callback) {
    std::move(callback)(error);
    callback = nullptr;
  }
  return error;
}

}  // namespace webrtc

namespace cricket {
using webrtc::FrameDecryptorInterface;
using webrtc::FrameEncryptorInterface;
using webrtc::FrameTransformerInterface;
using webrtc::PendingTaskSafetyFlag;
using webrtc::SafeTask;
using webrtc::TaskQueueBase;
using webrtc::VideoTrackInterface;

VideoOptions::VideoOptions()
    : content_hint(VideoTrackInterface::ContentHint::kNone) {}
VideoOptions::~VideoOptions() = default;

MediaChannelUtil::MediaChannelUtil(TaskQueueBase* network_thread,
                                   bool enable_dscp)
    : transport_(network_thread, enable_dscp) {}

MediaChannelUtil::~MediaChannelUtil() {}

void MediaChannelUtil::SetInterface(MediaChannelNetworkInterface* iface) {
  transport_.SetInterface(iface);
}

int MediaChannelUtil::GetRtpSendTimeExtnId() const {
  return -1;
}

bool MediaChannelUtil::SendPacket(rtc::CopyOnWriteBuffer* packet,
                                  const rtc::PacketOptions& options) {
  return transport_.DoSendPacket(packet, false, options);
}

bool MediaChannelUtil::SendRtcp(rtc::CopyOnWriteBuffer* packet,
                                const rtc::PacketOptions& options) {
  return transport_.DoSendPacket(packet, true, options);
}

int MediaChannelUtil::SetOption(MediaChannelNetworkInterface::SocketType type,
                                rtc::Socket::Option opt,
                                int option) {
  return transport_.SetOption(type, opt, option);
}

// Corresponds to the SDP attribute extmap-allow-mixed, see RFC8285.
// Set to true if it's allowed to mix one- and two-byte RTP header extensions
// in the same stream. The setter and getter must only be called from
// worker_thread.
void MediaChannelUtil::SetExtmapAllowMixed(bool extmap_allow_mixed) {
  extmap_allow_mixed_ = extmap_allow_mixed;
}

bool MediaChannelUtil::ExtmapAllowMixed() const {
  return extmap_allow_mixed_;
}

bool MediaChannelUtil::HasNetworkInterface() const {
  return transport_.HasNetworkInterface();
}

bool MediaChannelUtil::DscpEnabled() const {
  return transport_.DscpEnabled();
}

void MediaChannelUtil::SetPreferredDscp(rtc::DiffServCodePoint new_dscp) {
  transport_.SetPreferredDscp(new_dscp);
}

MediaSenderInfo::MediaSenderInfo() = default;
MediaSenderInfo::~MediaSenderInfo() = default;

MediaReceiverInfo::MediaReceiverInfo() = default;
MediaReceiverInfo::~MediaReceiverInfo() = default;

VoiceSenderInfo::VoiceSenderInfo() = default;
VoiceSenderInfo::~VoiceSenderInfo() = default;

VoiceReceiverInfo::VoiceReceiverInfo() = default;
VoiceReceiverInfo::~VoiceReceiverInfo() = default;

VideoSenderInfo::VideoSenderInfo() = default;
VideoSenderInfo::~VideoSenderInfo() = default;

VideoReceiverInfo::VideoReceiverInfo() = default;
VideoReceiverInfo::~VideoReceiverInfo() = default;

VoiceMediaInfo::VoiceMediaInfo() = default;
VoiceMediaInfo::~VoiceMediaInfo() = default;

VideoMediaInfo::VideoMediaInfo() = default;
VideoMediaInfo::~VideoMediaInfo() = default;

VideoMediaSendInfo::VideoMediaSendInfo() = default;
VideoMediaSendInfo::~VideoMediaSendInfo() = default;

VoiceMediaSendInfo::VoiceMediaSendInfo() = default;
VoiceMediaSendInfo::~VoiceMediaSendInfo() = default;

VideoMediaReceiveInfo::VideoMediaReceiveInfo() = default;
VideoMediaReceiveInfo::~VideoMediaReceiveInfo() = default;

VoiceMediaReceiveInfo::VoiceMediaReceiveInfo() = default;
VoiceMediaReceiveInfo::~VoiceMediaReceiveInfo() = default;

AudioSenderParameter::AudioSenderParameter() = default;
AudioSenderParameter::~AudioSenderParameter() = default;

std::map<std::string, std::string> AudioSenderParameter::ToStringMap() const {
  auto params = SenderParameters::ToStringMap();
  params["options"] = options.ToString();
  return params;
}

VideoSenderParameters::VideoSenderParameters() = default;
VideoSenderParameters::~VideoSenderParameters() = default;

std::map<std::string, std::string> VideoSenderParameters::ToStringMap() const {
  auto params = SenderParameters::ToStringMap();
  params["conference_mode"] = (conference_mode ? "yes" : "no");
  return params;
}

// --------------------- MediaChannelUtil::TransportForMediaChannels -----

MediaChannelUtil::TransportForMediaChannels::TransportForMediaChannels(
    webrtc::TaskQueueBase* network_thread,
    bool enable_dscp)
    : network_safety_(webrtc::PendingTaskSafetyFlag::CreateDetachedInactive()),
      network_thread_(network_thread),

      enable_dscp_(enable_dscp) {}

MediaChannelUtil::TransportForMediaChannels::~TransportForMediaChannels() {
  RTC_DCHECK(!network_interface_);
}

bool MediaChannelUtil::TransportForMediaChannels::SendRtcp(
    rtc::ArrayView<const uint8_t> packet) {
  auto send = [this, packet = rtc::CopyOnWriteBuffer(
                         packet, kMaxRtpPacketLen)]() mutable {
    rtc::PacketOptions rtc_options;
    if (DscpEnabled()) {
      rtc_options.dscp = PreferredDscp();
    }
    DoSendPacket(&packet, true, rtc_options);
  };

  if (network_thread_->IsCurrent()) {
    send();
  } else {
    network_thread_->PostTask(SafeTask(network_safety_, std::move(send)));
  }
  return true;
}

bool MediaChannelUtil::TransportForMediaChannels::SendRtp(
    rtc::ArrayView<const uint8_t> packet,
    const webrtc::PacketOptions& options) {
  auto send =
      [this, packet_id = options.packet_id,
       included_in_feedback = options.included_in_feedback,
       included_in_allocation = options.included_in_allocation,
       batchable = options.batchable,
       last_packet_in_batch = options.last_packet_in_batch,
       packet = rtc::CopyOnWriteBuffer(packet, kMaxRtpPacketLen)]() mutable {
        rtc::PacketOptions rtc_options;
        rtc_options.packet_id = packet_id;
        if (DscpEnabled()) {
          rtc_options.dscp = PreferredDscp();
        }
        rtc_options.info_signaled_after_sent.included_in_feedback =
            included_in_feedback;
        rtc_options.info_signaled_after_sent.included_in_allocation =
            included_in_allocation;
        rtc_options.batchable = batchable;
        rtc_options.last_packet_in_batch = last_packet_in_batch;
        DoSendPacket(&packet, false, rtc_options);
      };

  // TODO(bugs.webrtc.org/11993): ModuleRtpRtcpImpl2 and related classes (e.g.
  // RTCPSender) aren't aware of the network thread and may trigger calls to
  // this function from different threads. Update those classes to keep
  // network traffic on the network thread.
  if (network_thread_->IsCurrent()) {
    send();
  } else {
    network_thread_->PostTask(SafeTask(network_safety_, std::move(send)));
  }
  return true;
}

void MediaChannelUtil::TransportForMediaChannels::SetInterface(
    MediaChannelNetworkInterface* iface) {
  RTC_DCHECK_RUN_ON(network_thread_);
  iface ? network_safety_->SetAlive() : network_safety_->SetNotAlive();
  network_interface_ = iface;
  UpdateDscp();
}

void MediaChannelUtil::TransportForMediaChannels::UpdateDscp() {
  rtc::DiffServCodePoint value =
      enable_dscp_ ? preferred_dscp_ : rtc::DSCP_DEFAULT;
  int ret = SetOptionLocked(MediaChannelNetworkInterface::ST_RTP,
                            rtc::Socket::OPT_DSCP, value);
  if (ret == 0)
    SetOptionLocked(MediaChannelNetworkInterface::ST_RTCP,
                    rtc::Socket::OPT_DSCP, value);
}

bool MediaChannelUtil::TransportForMediaChannels::DoSendPacket(
    rtc::CopyOnWriteBuffer* packet,
    bool rtcp,
    const rtc::PacketOptions& options) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!network_interface_)
    return false;

  return (!rtcp) ? network_interface_->SendPacket(packet, options)
                 : network_interface_->SendRtcp(packet, options);
}

int MediaChannelUtil::TransportForMediaChannels::SetOption(
    MediaChannelNetworkInterface::SocketType type,
    rtc::Socket::Option opt,
    int option) {
  RTC_DCHECK_RUN_ON(network_thread_);
  return SetOptionLocked(type, opt, option);
}

int MediaChannelUtil::TransportForMediaChannels::SetOptionLocked(
    MediaChannelNetworkInterface::SocketType type,
    rtc::Socket::Option opt,
    int option) {
  if (!network_interface_)
    return -1;
  return network_interface_->SetOption(type, opt, option);
}

void MediaChannelUtil::TransportForMediaChannels::SetPreferredDscp(
    rtc::DiffServCodePoint new_dscp) {
  if (!network_thread_->IsCurrent()) {
    // This is currently the common path as the derived channel classes
    // get called on the worker thread. There are still some tests though
    // that call directly on the network thread.
    network_thread_->PostTask(SafeTask(
        network_safety_, [this, new_dscp]() { SetPreferredDscp(new_dscp); }));
    return;
  }

  RTC_DCHECK_RUN_ON(network_thread_);
  if (new_dscp == preferred_dscp_)
    return;

  preferred_dscp_ = new_dscp;
  UpdateDscp();
}

}  // namespace cricket
