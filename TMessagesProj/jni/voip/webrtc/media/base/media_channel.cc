/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "media/base/media_channel.h"

#include "media/base/rtp_utils.h"

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

MediaChannel::MediaChannel(TaskQueueBase* network_thread, bool enable_dscp)
    : enable_dscp_(enable_dscp),
      network_safety_(PendingTaskSafetyFlag::CreateDetachedInactive()),
      network_thread_(network_thread) {}

MediaChannel::~MediaChannel() {
  RTC_DCHECK(!network_interface_);
}

void MediaChannel::SetInterface(NetworkInterface* iface) {
  RTC_DCHECK_RUN_ON(network_thread_);
  iface ? network_safety_->SetAlive() : network_safety_->SetNotAlive();
  network_interface_ = iface;
  UpdateDscp();
}

int MediaChannel::GetRtpSendTimeExtnId() const {
  return -1;
}

void MediaChannel::SetFrameEncryptor(
    uint32_t ssrc,
    rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor) {
  // Placeholder should be pure virtual once internal supports it.
}

void MediaChannel::SetFrameDecryptor(
    uint32_t ssrc,
    rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) {
  // Placeholder should be pure virtual once internal supports it.
}

void MediaChannel::SetVideoCodecSwitchingEnabled(bool enabled) {}

bool MediaChannel::SendPacket(rtc::CopyOnWriteBuffer* packet,
                              const rtc::PacketOptions& options) {
  return DoSendPacket(packet, false, options);
}

bool MediaChannel::SendRtcp(rtc::CopyOnWriteBuffer* packet,
                            const rtc::PacketOptions& options) {
  return DoSendPacket(packet, true, options);
}

int MediaChannel::SetOption(NetworkInterface::SocketType type,
                            rtc::Socket::Option opt,
                            int option) {
  RTC_DCHECK_RUN_ON(network_thread_);
  return SetOptionLocked(type, opt, option);
}

// Corresponds to the SDP attribute extmap-allow-mixed, see RFC8285.
// Set to true if it's allowed to mix one- and two-byte RTP header extensions
// in the same stream. The setter and getter must only be called from
// worker_thread.
void MediaChannel::SetExtmapAllowMixed(bool extmap_allow_mixed) {
  extmap_allow_mixed_ = extmap_allow_mixed;
}

bool MediaChannel::ExtmapAllowMixed() const {
  return extmap_allow_mixed_;
}

bool MediaChannel::HasNetworkInterface() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return network_interface_ != nullptr;
}

void MediaChannel::SetEncoderToPacketizerFrameTransformer(
    uint32_t ssrc,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {}

void MediaChannel::SetDepacketizerToDecoderFrameTransformer(
    uint32_t ssrc,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) {}

int MediaChannel::SetOptionLocked(NetworkInterface::SocketType type,
                                  rtc::Socket::Option opt,
                                  int option) {
  if (!network_interface_)
    return -1;
  return network_interface_->SetOption(type, opt, option);
}

bool MediaChannel::DscpEnabled() const {
  return enable_dscp_;
}

// This is the DSCP value used for both RTP and RTCP channels if DSCP is
// enabled. It can be changed at any time via `SetPreferredDscp`.
rtc::DiffServCodePoint MediaChannel::PreferredDscp() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return preferred_dscp_;
}

void MediaChannel::SetPreferredDscp(rtc::DiffServCodePoint new_dscp) {
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

rtc::scoped_refptr<PendingTaskSafetyFlag> MediaChannel::network_safety() {
  return network_safety_;
}

void MediaChannel::UpdateDscp() {
  rtc::DiffServCodePoint value =
      enable_dscp_ ? preferred_dscp_ : rtc::DSCP_DEFAULT;
  int ret =
      SetOptionLocked(NetworkInterface::ST_RTP, rtc::Socket::OPT_DSCP, value);
  if (ret == 0)
    SetOptionLocked(NetworkInterface::ST_RTCP, rtc::Socket::OPT_DSCP, value);
}

bool MediaChannel::DoSendPacket(rtc::CopyOnWriteBuffer* packet,
                                bool rtcp,
                                const rtc::PacketOptions& options) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!network_interface_)
    return false;

  return (!rtcp) ? network_interface_->SendPacket(packet, options)
                 : network_interface_->SendRtcp(packet, options);
}

void MediaChannel::SendRtp(const uint8_t* data,
                           size_t len,
                           const webrtc::PacketOptions& options) {
  auto send =
      [this, packet_id = options.packet_id,
       included_in_feedback = options.included_in_feedback,
       included_in_allocation = options.included_in_allocation,
       packet = rtc::CopyOnWriteBuffer(data, len, kMaxRtpPacketLen)]() mutable {
        rtc::PacketOptions rtc_options;
        rtc_options.packet_id = packet_id;
        if (DscpEnabled()) {
          rtc_options.dscp = PreferredDscp();
        }
        rtc_options.info_signaled_after_sent.included_in_feedback =
            included_in_feedback;
        rtc_options.info_signaled_after_sent.included_in_allocation =
            included_in_allocation;
        SendPacket(&packet, rtc_options);
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
}

void MediaChannel::SendRtcp(const uint8_t* data, size_t len) {
  auto send = [this, packet = rtc::CopyOnWriteBuffer(
                         data, len, kMaxRtpPacketLen)]() mutable {
    rtc::PacketOptions rtc_options;
    if (DscpEnabled()) {
      rtc_options.dscp = PreferredDscp();
    }
    SendRtcp(&packet, rtc_options);
  };

  if (network_thread_->IsCurrent()) {
    send();
  } else {
    network_thread_->PostTask(SafeTask(network_safety_, std::move(send)));
  }
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

AudioSendParameters::AudioSendParameters() = default;
AudioSendParameters::~AudioSendParameters() = default;

std::map<std::string, std::string> AudioSendParameters::ToStringMap() const {
  auto params = RtpSendParameters<AudioCodec>::ToStringMap();
  params["options"] = options.ToString();
  return params;
}

cricket::MediaType VoiceMediaChannel::media_type() const {
  return cricket::MediaType::MEDIA_TYPE_AUDIO;
}

VideoSendParameters::VideoSendParameters() = default;
VideoSendParameters::~VideoSendParameters() = default;

std::map<std::string, std::string> VideoSendParameters::ToStringMap() const {
  auto params = RtpSendParameters<VideoCodec>::ToStringMap();
  params["conference_mode"] = (conference_mode ? "yes" : "no");
  return params;
}

cricket::MediaType VideoMediaChannel::media_type() const {
  return cricket::MediaType::MEDIA_TYPE_VIDEO;
}

}  // namespace cricket
