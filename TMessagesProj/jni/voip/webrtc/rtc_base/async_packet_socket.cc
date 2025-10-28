/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_packet_socket.h"

#include "rtc_base/checks.h"

namespace rtc {

PacketTimeUpdateParams::PacketTimeUpdateParams() = default;

PacketTimeUpdateParams::PacketTimeUpdateParams(
    const PacketTimeUpdateParams& other) = default;

PacketTimeUpdateParams::~PacketTimeUpdateParams() = default;

PacketOptions::PacketOptions() = default;
PacketOptions::PacketOptions(DiffServCodePoint dscp) : dscp(dscp) {}
PacketOptions::PacketOptions(const PacketOptions& other) = default;
PacketOptions::~PacketOptions() = default;

AsyncPacketSocket::~AsyncPacketSocket() = default;

void AsyncPacketSocket::SubscribeCloseEvent(
    const void* removal_tag,
    std::function<void(AsyncPacketSocket*, int)> callback) {
  RTC_DCHECK_RUN_ON(&network_checker_);
  on_close_.AddReceiver(removal_tag, std::move(callback));
}

void AsyncPacketSocket::UnsubscribeCloseEvent(const void* removal_tag) {
  RTC_DCHECK_RUN_ON(&network_checker_);
  on_close_.RemoveReceivers(removal_tag);
}

void AsyncPacketSocket::RegisterReceivedPacketCallback(
    absl::AnyInvocable<void(AsyncPacketSocket*, const rtc::ReceivedPacket&)>
        received_packet_callback) {
  RTC_DCHECK_RUN_ON(&network_checker_);
  RTC_CHECK(!received_packet_callback_);
  received_packet_callback_ = std::move(received_packet_callback);
}

void AsyncPacketSocket::DeregisterReceivedPacketCallback() {
  RTC_DCHECK_RUN_ON(&network_checker_);
  received_packet_callback_ = nullptr;
}

void AsyncPacketSocket::NotifyPacketReceived(
    const rtc::ReceivedPacket& packet) {
  RTC_DCHECK_RUN_ON(&network_checker_);
  if (received_packet_callback_) {
    received_packet_callback_(this, packet);
    return;
  }
}

void CopySocketInformationToPacketInfo(size_t packet_size_bytes,
                                       const AsyncPacketSocket& socket_from,
                                       bool is_connectionless,
                                       rtc::PacketInfo* info) {
  info->packet_size_bytes = packet_size_bytes;
  info->ip_overhead_bytes = socket_from.GetLocalAddress().ipaddr().overhead();
}

}  // namespace rtc
