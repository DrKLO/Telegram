/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/public/text_pcap_packet_observer.h"

#include "api/array_view.h"
#include "net/dcsctp/public/types.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"

namespace dcsctp {

void TextPcapPacketObserver::OnSentPacket(
    dcsctp::TimeMs now,
    rtc::ArrayView<const uint8_t> payload) {
  PrintPacket("O ", name_, now, payload);
}

void TextPcapPacketObserver::OnReceivedPacket(
    dcsctp::TimeMs now,
    rtc::ArrayView<const uint8_t> payload) {
  PrintPacket("I ", name_, now, payload);
}

void TextPcapPacketObserver::PrintPacket(
    absl::string_view prefix,
    absl::string_view socket_name,
    dcsctp::TimeMs now,
    rtc::ArrayView<const uint8_t> payload) {
  rtc::StringBuilder s;
  s << "\n" << prefix;
  int64_t remaining = *now % (24 * 60 * 60 * 1000);
  int hours = remaining / (60 * 60 * 1000);
  remaining = remaining % (60 * 60 * 1000);
  int minutes = remaining / (60 * 1000);
  remaining = remaining % (60 * 1000);
  int seconds = remaining / 1000;
  int ms = remaining % 1000;
  s.AppendFormat("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
  s << " 0000";
  for (uint8_t byte : payload) {
    s.AppendFormat(" %02x", byte);
  }
  s << " # SCTP_PACKET " << socket_name;
  RTC_LOG(LS_VERBOSE) << s.str();
}

}  // namespace dcsctp
