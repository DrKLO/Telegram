/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_PUBLIC_DCSCTP_OPTIONS_H_
#define NET_DCSCTP_PUBLIC_DCSCTP_OPTIONS_H_

#include <stddef.h>
#include <stdint.h>

#include "net/dcsctp/public/types.h"

namespace dcsctp {
struct DcSctpOptions {
  // The largest safe SCTP packet. Starting from the minimum guaranteed MTU
  // value of 1280 for IPv6 (which may not support fragmentation), take off 85
  // bytes for DTLS/TURN/TCP/IP and ciphertext overhead.
  //
  // Additionally, it's possible that TURN adds an additional 4 bytes of
  // overhead after a channel has been established, so an additional 4 bytes is
  // subtracted
  //
  // 1280 IPV6 MTU
  //  -40 IPV6 header
  //   -8 UDP
  //  -24 GCM Cipher
  //  -13 DTLS record header
  //   -4 TURN ChannelData
  // = 1191 bytes.
  static constexpr size_t kMaxSafeMTUSize = 1191;

  // The local port for which the socket is supposed to be bound to. Incoming
  // packets will be verified that they are sent to this port number and all
  // outgoing packets will have this port number as source port.
  int local_port = 5000;

  // The remote port to send packets to. All outgoing packets will have this
  // port number as destination port.
  int remote_port = 5000;

  // The announced maximum number of incoming streams. Note that this value is
  // constant and can't be currently increased in run-time as "Add Incoming
  // Streams Request" in RFC6525 isn't supported.
  //
  // The socket implementation doesn't have any per-stream fixed costs, which is
  // why the default value is set to be the maximum value.
  uint16_t announced_maximum_incoming_streams = 65535;

  // The announced maximum number of outgoing streams. Note that this value is
  // constant and can't be currently increased in run-time as "Add Outgoing
  // Streams Request" in RFC6525 isn't supported.
  //
  // The socket implementation doesn't have any per-stream fixed costs, which is
  // why the default value is set to be the maximum value.
  uint16_t announced_maximum_outgoing_streams = 65535;

  // Maximum SCTP packet size. The library will limit the size of generated
  // packets to be less than or equal to this number. This does not include any
  // overhead of DTLS, TURN, UDP or IP headers.
  size_t mtu = kMaxSafeMTUSize;

  // The largest allowed message payload to be sent. Messages will be rejected
  // if their payload is larger than this value. Note that this doesn't affect
  // incoming messages, which may larger than this value (but smaller than
  // `max_receiver_window_buffer_size`).
  size_t max_message_size = 256 * 1024;

  // Maximum received window buffer size. This should be a bit larger than the
  // largest sized message you want to be able to receive. This essentially
  // limits the memory usage on the receive side. Note that memory is allocated
  // dynamically, and this represents the maximum amount of buffered data. The
  // actual memory usage of the library will be smaller in normal operation, and
  // will be larger than this due to other allocations and overhead if the
  // buffer is fully utilized.
  size_t max_receiver_window_buffer_size = 5 * 1024 * 1024;

  // Maximum send buffer size. It will not be possible to queue more data than
  // this before sending it.
  size_t max_send_buffer_size = 2 * 1024 * 1024;

  // Max allowed RTT value. When the RTT is measured and it's found to be larger
  // than this value, it will be discarded and not used for e.g. any RTO
  // calculation. The default value is an extreme maximum but can be adapted
  // to better match the environment.
  DurationMs rtt_max = DurationMs(8000);

  // Initial RTO value.
  DurationMs rto_initial = DurationMs(500);

  // Maximum RTO value.
  DurationMs rto_max = DurationMs(800);

  // Minimum RTO value. This must be larger than an expected peer delayed ack
  // timeout.
  DurationMs rto_min = DurationMs(220);

  // T1-init timeout.
  DurationMs t1_init_timeout = DurationMs(1000);

  // T1-cookie timeout.
  DurationMs t1_cookie_timeout = DurationMs(1000);

  // T2-shutdown timeout.
  DurationMs t2_shutdown_timeout = DurationMs(1000);

  // Hearbeat interval (on idle connections only).
  DurationMs heartbeat_interval = DurationMs(30000);

  // The maximum time when a SACK will be sent from the arrival of an
  // unacknowledged packet. Whatever is smallest of RTO/2 and this will be used.
  DurationMs delayed_ack_max_timeout = DurationMs(200);

  // Do slow start as TCP - double cwnd instead of increasing it by MTU.
  bool slow_start_tcp_style = false;

  // The initial congestion window size, in number of MTUs.
  // See https://tools.ietf.org/html/rfc4960#section-7.2.1 which defaults at ~3
  // and https://research.google/pubs/pub36640/ which argues for at least ten
  // segments.
  size_t cwnd_mtus_initial = 10;

  // The minimum congestion window size, in number of MTUs.
  // See https://tools.ietf.org/html/rfc4960#section-7.2.3.
  size_t cwnd_mtus_min = 4;

  // Maximum Data Retransmit Attempts (per DATA chunk).
  int max_retransmissions = 10;

  // Max.Init.Retransmits (https://tools.ietf.org/html/rfc4960#section-15)
  int max_init_retransmits = 8;

  // RFC3758 Partial Reliability Extension
  bool enable_partial_reliability = true;

  // RFC8260 Stream Schedulers and User Message Interleaving
  bool enable_message_interleaving = false;

  // If RTO should be added to heartbeat_interval
  bool heartbeat_interval_include_rtt = true;

  // Disables SCTP packet crc32 verification. Useful when running with fuzzers.
  bool disable_checksum_verification = false;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_PUBLIC_DCSCTP_OPTIONS_H_
