/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/pseudo_tcp.h"

#include <errno.h>
#include <stdio.h>
#include <string.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <set>

#include "rtc_base/byte_buffer.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_minmax.h"
#include "rtc_base/socket.h"
#include "rtc_base/time_utils.h"

// The following logging is for detailed (packet-level) analysis only.
#define _DBG_NONE 0
#define _DBG_NORMAL 1
#define _DBG_VERBOSE 2
#define _DEBUGMSG _DBG_NONE

namespace cricket {

//////////////////////////////////////////////////////////////////////
// Network Constants
//////////////////////////////////////////////////////////////////////

// Standard MTUs
const uint16_t PACKET_MAXIMUMS[] = {
    65535,  // Theoretical maximum, Hyperchannel
    32000,  // Nothing
    17914,  // 16Mb IBM Token Ring
    8166,   // IEEE 802.4
    // 4464,   // IEEE 802.5 (4Mb max)
    4352,  // FDDI
    // 2048,   // Wideband Network
    2002,  // IEEE 802.5 (4Mb recommended)
    // 1536,   // Expermental Ethernet Networks
    // 1500,   // Ethernet, Point-to-Point (default)
    1492,  // IEEE 802.3
    1006,  // SLIP, ARPANET
    // 576,    // X.25 Networks
    // 544,    // DEC IP Portal
    // 512,    // NETBIOS
    508,  // IEEE 802/Source-Rt Bridge, ARCNET
    296,  // Point-to-Point (low delay)
    // 68,     // Official minimum
    0,  // End of list marker
};

const uint32_t MAX_PACKET = 65535;
// Note: we removed lowest level because packet overhead was larger!
const uint32_t MIN_PACKET = 296;

const uint32_t IP_HEADER_SIZE = 20;  // (+ up to 40 bytes of options?)
const uint32_t UDP_HEADER_SIZE = 8;
// TODO(?): Make JINGLE_HEADER_SIZE transparent to this code?
const uint32_t JINGLE_HEADER_SIZE = 64;  // when relay framing is in use

// Default size for receive and send buffer.
const uint32_t DEFAULT_RCV_BUF_SIZE = 60 * 1024;
const uint32_t DEFAULT_SND_BUF_SIZE = 90 * 1024;

//////////////////////////////////////////////////////////////////////
// Global Constants and Functions
//////////////////////////////////////////////////////////////////////
//
//    0                   1                   2                   3
//    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  0 |                      Conversation Number                      |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  4 |                        Sequence Number                        |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//  8 |                     Acknowledgment Number                     |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//    |               |   |U|A|P|R|S|F|                               |
// 12 |    Control    |   |R|C|S|S|Y|I|            Window             |
//    |               |   |G|K|H|T|N|N|                               |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 16 |                       Timestamp sending                       |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 20 |                      Timestamp receiving                      |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// 24 |                             data                              |
//    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
//////////////////////////////////////////////////////////////////////

#define PSEUDO_KEEPALIVE 0

const uint32_t HEADER_SIZE = 24;
const uint32_t PACKET_OVERHEAD =
    HEADER_SIZE + UDP_HEADER_SIZE + IP_HEADER_SIZE + JINGLE_HEADER_SIZE;

const uint32_t MIN_RTO =
    250;  // 250 ms (RFC1122, Sec 4.2.3.1 "fractions of a second")
const uint32_t DEF_RTO = 3000;       // 3 seconds (RFC1122, Sec 4.2.3.1)
const uint32_t MAX_RTO = 60000;      // 60 seconds
const uint32_t DEF_ACK_DELAY = 100;  // 100 milliseconds

const uint8_t FLAG_CTL = 0x02;
const uint8_t FLAG_RST = 0x04;

const uint8_t CTL_CONNECT = 0;

// TCP options.
const uint8_t TCP_OPT_EOL = 0;        // End of list.
const uint8_t TCP_OPT_NOOP = 1;       // No-op.
const uint8_t TCP_OPT_MSS = 2;        // Maximum segment size.
const uint8_t TCP_OPT_WND_SCALE = 3;  // Window scale factor.

const long DEFAULT_TIMEOUT =
    4000;  // If there are no pending clocks, wake up every 4 seconds
const long CLOSED_TIMEOUT =
    60 * 1000;  // If the connection is closed, once per minute

#if PSEUDO_KEEPALIVE
// !?! Rethink these times
const uint32_t IDLE_PING =
    20 *
    1000;  // 20 seconds (note: WinXP SP2 firewall udp timeout is 90 seconds)
const uint32_t IDLE_TIMEOUT = 90 * 1000;  // 90 seconds;
#endif                                    // PSEUDO_KEEPALIVE

//////////////////////////////////////////////////////////////////////
// Helper Functions
//////////////////////////////////////////////////////////////////////

inline void long_to_bytes(uint32_t val, void* buf) {
  *static_cast<uint32_t*>(buf) = rtc::HostToNetwork32(val);
}

inline void short_to_bytes(uint16_t val, void* buf) {
  *static_cast<uint16_t*>(buf) = rtc::HostToNetwork16(val);
}

inline uint32_t bytes_to_long(const void* buf) {
  return rtc::NetworkToHost32(*static_cast<const uint32_t*>(buf));
}

inline uint16_t bytes_to_short(const void* buf) {
  return rtc::NetworkToHost16(*static_cast<const uint16_t*>(buf));
}

//////////////////////////////////////////////////////////////////////
// Debugging Statistics
//////////////////////////////////////////////////////////////////////

#if 0  // Not used yet

enum Stat {
  S_SENT_PACKET,    // All packet sends
  S_RESENT_PACKET,  // All packet sends that are retransmits
  S_RECV_PACKET,    // All packet receives
  S_RECV_NEW,       // All packet receives that are too new
  S_RECV_OLD,       // All packet receives that are too old
  S_NUM_STATS
};

const char* const STAT_NAMES[S_NUM_STATS] = {
  "snt",
  "snt-r",
  "rcv"
  "rcv-n",
  "rcv-o"
};

int g_stats[S_NUM_STATS];
inline void Incr(Stat s) { ++g_stats[s]; }
void ReportStats() {
  char buffer[256];
  size_t len = 0;
  for (int i = 0; i < S_NUM_STATS; ++i) {
    len += snprintf(buffer, arraysize(buffer), "%s%s:%d",
                          (i == 0) ? "" : ",", STAT_NAMES[i], g_stats[i]);
    g_stats[i] = 0;
  }
  RTC_LOG(LS_INFO) << "Stats[" << buffer << "]";
}

#endif

//////////////////////////////////////////////////////////////////////
// PseudoTcp
//////////////////////////////////////////////////////////////////////

uint32_t PseudoTcp::Now() {
#if 0  // Use this to synchronize timers with logging timestamps (easier debug)
  return static_cast<uint32_t>(rtc::TimeSince(StartTime()));
#else
  return rtc::Time32();
#endif
}

PseudoTcp::PseudoTcp(IPseudoTcpNotify* notify, uint32_t conv)
    : m_notify(notify),
      m_shutdown(SD_NONE),
      m_error(0),
      m_rbuf_len(DEFAULT_RCV_BUF_SIZE),
      m_rbuf(m_rbuf_len),
      m_sbuf_len(DEFAULT_SND_BUF_SIZE),
      m_sbuf(m_sbuf_len) {
  // Sanity check on buffer sizes (needed for OnTcpWriteable notification logic)
  RTC_DCHECK(m_rbuf_len + MIN_PACKET < m_sbuf_len);

  uint32_t now = Now();

  m_state = TCP_LISTEN;
  m_conv = conv;
  m_rcv_wnd = m_rbuf_len;
  m_rwnd_scale = m_swnd_scale = 0;
  m_snd_nxt = 0;
  m_snd_wnd = 1;
  m_snd_una = m_rcv_nxt = 0;
  m_bReadEnable = true;
  m_bWriteEnable = false;
  m_t_ack = 0;

  m_msslevel = 0;
  m_largest = 0;
  RTC_DCHECK(MIN_PACKET > PACKET_OVERHEAD);
  m_mss = MIN_PACKET - PACKET_OVERHEAD;
  m_mtu_advise = MAX_PACKET;

  m_rto_base = 0;

  m_cwnd = 2 * m_mss;
  m_ssthresh = m_rbuf_len;
  m_lastrecv = m_lastsend = m_lasttraffic = now;
  m_bOutgoing = false;

  m_dup_acks = 0;
  m_recover = 0;

  m_ts_recent = m_ts_lastack = 0;

  m_rx_rto = DEF_RTO;
  m_rx_srtt = m_rx_rttvar = 0;

  m_use_nagling = true;
  m_ack_delay = DEF_ACK_DELAY;
  m_support_wnd_scale = true;
}

PseudoTcp::~PseudoTcp() {}

int PseudoTcp::Connect() {
  if (m_state != TCP_LISTEN) {
    m_error = EINVAL;
    return -1;
  }

  m_state = TCP_SYN_SENT;
  RTC_LOG(LS_INFO) << "State: TCP_SYN_SENT";

  queueConnectMessage();
  attemptSend();

  return 0;
}

void PseudoTcp::NotifyMTU(uint16_t mtu) {
  m_mtu_advise = mtu;
  if (m_state == TCP_ESTABLISHED) {
    adjustMTU();
  }
}

void PseudoTcp::NotifyClock(uint32_t now) {
  if (m_state == TCP_CLOSED)
    return;

  // Check if it's time to retransmit a segment
  if (m_rto_base && (rtc::TimeDiff32(m_rto_base + m_rx_rto, now) <= 0)) {
    if (m_slist.empty()) {
      RTC_DCHECK_NOTREACHED();
    } else {
// Note: (m_slist.front().xmit == 0)) {
// retransmit segments
#if _DEBUGMSG >= _DBG_NORMAL
      RTC_LOG(LS_INFO) << "timeout retransmit (rto: " << m_rx_rto
                       << ") (rto_base: " << m_rto_base << ") (now: " << now
                       << ") (dup_acks: " << static_cast<unsigned>(m_dup_acks)
                       << ")";
#endif  // _DEBUGMSG
      if (!transmit(m_slist.begin(), now)) {
        closedown(ECONNABORTED);
        return;
      }

      uint32_t nInFlight = m_snd_nxt - m_snd_una;
      m_ssthresh = std::max(nInFlight / 2, 2 * m_mss);
      // RTC_LOG(LS_INFO) << "m_ssthresh: " << m_ssthresh << "  nInFlight: " <<
      // nInFlight << "  m_mss: " << m_mss;
      m_cwnd = m_mss;

      // Back off retransmit timer.  Note: the limit is lower when connecting.
      uint32_t rto_limit = (m_state < TCP_ESTABLISHED) ? DEF_RTO : MAX_RTO;
      m_rx_rto = std::min(rto_limit, m_rx_rto * 2);
      m_rto_base = now;
    }
  }

  // Check if it's time to probe closed windows
  if ((m_snd_wnd == 0) && (rtc::TimeDiff32(m_lastsend + m_rx_rto, now) <= 0)) {
    if (rtc::TimeDiff32(now, m_lastrecv) >= 15000) {
      closedown(ECONNABORTED);
      return;
    }

    // probe the window
    packet(m_snd_nxt - 1, 0, 0, 0);
    m_lastsend = now;

    // back off retransmit timer
    m_rx_rto = std::min(MAX_RTO, m_rx_rto * 2);
  }

  // Check if it's time to send delayed acks
  if (m_t_ack && (rtc::TimeDiff32(m_t_ack + m_ack_delay, now) <= 0)) {
    packet(m_snd_nxt, 0, 0, 0);
  }

#if PSEUDO_KEEPALIVE
  // Check for idle timeout
  if ((m_state == TCP_ESTABLISHED) &&
      (TimeDiff32(m_lastrecv + IDLE_TIMEOUT, now) <= 0)) {
    closedown(ECONNABORTED);
    return;
  }

  // Check for ping timeout (to keep udp mapping open)
  if ((m_state == TCP_ESTABLISHED) &&
      (TimeDiff32(m_lasttraffic + (m_bOutgoing ? IDLE_PING * 3 / 2 : IDLE_PING),
                  now) <= 0)) {
    packet(m_snd_nxt, 0, 0, 0);
  }
#endif  // PSEUDO_KEEPALIVE
}

bool PseudoTcp::NotifyPacket(const char* buffer, size_t len) {
  if (len > MAX_PACKET) {
    RTC_LOG_F(LS_WARNING) << "packet too large";
    return false;
  }
  return parse(reinterpret_cast<const uint8_t*>(buffer), uint32_t(len));
}

bool PseudoTcp::GetNextClock(uint32_t now, long& timeout) {
  return clock_check(now, timeout);
}

void PseudoTcp::GetOption(Option opt, int* value) {
  if (opt == OPT_NODELAY) {
    *value = m_use_nagling ? 0 : 1;
  } else if (opt == OPT_ACKDELAY) {
    *value = m_ack_delay;
  } else if (opt == OPT_SNDBUF) {
    *value = m_sbuf_len;
  } else if (opt == OPT_RCVBUF) {
    *value = m_rbuf_len;
  } else {
    RTC_DCHECK_NOTREACHED();
  }
}
void PseudoTcp::SetOption(Option opt, int value) {
  if (opt == OPT_NODELAY) {
    m_use_nagling = value == 0;
  } else if (opt == OPT_ACKDELAY) {
    m_ack_delay = value;
  } else if (opt == OPT_SNDBUF) {
    RTC_DCHECK(m_state == TCP_LISTEN);
    resizeSendBuffer(value);
  } else if (opt == OPT_RCVBUF) {
    RTC_DCHECK(m_state == TCP_LISTEN);
    resizeReceiveBuffer(value);
  } else {
    RTC_DCHECK_NOTREACHED();
  }
}

uint32_t PseudoTcp::GetCongestionWindow() const {
  return m_cwnd;
}

uint32_t PseudoTcp::GetBytesInFlight() const {
  return m_snd_nxt - m_snd_una;
}

uint32_t PseudoTcp::GetBytesBufferedNotSent() const {
  return static_cast<uint32_t>(m_snd_una + m_sbuf.GetBuffered() - m_snd_nxt);
}

uint32_t PseudoTcp::GetRoundTripTimeEstimateMs() const {
  return m_rx_srtt;
}

//
// IPStream Implementation
//

int PseudoTcp::Recv(char* buffer, size_t len) {
  if (m_state != TCP_ESTABLISHED) {
    m_error = ENOTCONN;
    return SOCKET_ERROR;
  }

  size_t read = 0;
  if (!m_rbuf.Read(buffer, len, &read)) {
    m_bReadEnable = true;
    m_error = EWOULDBLOCK;
    return SOCKET_ERROR;
  }

  size_t available_space = 0;
  m_rbuf.GetWriteRemaining(&available_space);

  if (uint32_t(available_space) - m_rcv_wnd >=
      std::min<uint32_t>(m_rbuf_len / 2, m_mss)) {
    // TODO(jbeda): !?! Not sure about this was closed business
    bool bWasClosed = (m_rcv_wnd == 0);
    m_rcv_wnd = static_cast<uint32_t>(available_space);

    if (bWasClosed) {
      attemptSend(sfImmediateAck);
    }
  }

  return static_cast<int>(read);
}

int PseudoTcp::Send(const char* buffer, size_t len) {
  if (m_state != TCP_ESTABLISHED) {
    m_error = ENOTCONN;
    return SOCKET_ERROR;
  }

  size_t available_space = 0;
  m_sbuf.GetWriteRemaining(&available_space);

  if (!available_space) {
    m_bWriteEnable = true;
    m_error = EWOULDBLOCK;
    return SOCKET_ERROR;
  }

  int written = queue(buffer, uint32_t(len), false);
  attemptSend();
  return written;
}

void PseudoTcp::Close(bool force) {
  RTC_LOG_F(LS_VERBOSE) << "(" << (force ? "true" : "false") << ")";
  m_shutdown = force ? SD_FORCEFUL : SD_GRACEFUL;
}

int PseudoTcp::GetError() {
  return m_error;
}

//
// Internal Implementation
//

uint32_t PseudoTcp::queue(const char* data, uint32_t len, bool bCtrl) {
  size_t available_space = 0;
  m_sbuf.GetWriteRemaining(&available_space);

  if (len > static_cast<uint32_t>(available_space)) {
    RTC_DCHECK(!bCtrl);
    len = static_cast<uint32_t>(available_space);
  }

  // We can concatenate data if the last segment is the same type
  // (control v. regular data), and has not been transmitted yet
  if (!m_slist.empty() && (m_slist.back().bCtrl == bCtrl) &&
      (m_slist.back().xmit == 0)) {
    m_slist.back().len += len;
  } else {
    SSegment sseg(static_cast<uint32_t>(m_snd_una + m_sbuf.GetBuffered()), len,
                  bCtrl);
    m_slist.push_back(sseg);
  }

  size_t written = 0;
  m_sbuf.Write(data, len, &written);
  return static_cast<uint32_t>(written);
}

IPseudoTcpNotify::WriteResult PseudoTcp::packet(uint32_t seq,
                                                uint8_t flags,
                                                uint32_t offset,
                                                uint32_t len) {
  RTC_DCHECK(HEADER_SIZE + len <= MAX_PACKET);

  uint32_t now = Now();

  std::unique_ptr<uint8_t[]> buffer(new uint8_t[MAX_PACKET]);
  long_to_bytes(m_conv, buffer.get());
  long_to_bytes(seq, buffer.get() + 4);
  long_to_bytes(m_rcv_nxt, buffer.get() + 8);
  buffer[12] = 0;
  buffer[13] = flags;
  short_to_bytes(static_cast<uint16_t>(m_rcv_wnd >> m_rwnd_scale),
                 buffer.get() + 14);

  // Timestamp computations
  long_to_bytes(now, buffer.get() + 16);
  long_to_bytes(m_ts_recent, buffer.get() + 20);
  m_ts_lastack = m_rcv_nxt;

  if (len) {
    size_t bytes_read = 0;
    bool result =
        m_sbuf.ReadOffset(buffer.get() + HEADER_SIZE, len, offset, &bytes_read);
    RTC_DCHECK(result);
    RTC_DCHECK(static_cast<uint32_t>(bytes_read) == len);
  }

#if _DEBUGMSG >= _DBG_VERBOSE
  RTC_LOG(LS_INFO) << "<-- <CONV=" << m_conv
                   << "><FLG=" << static_cast<unsigned>(flags)
                   << "><SEQ=" << seq << ":" << seq + len
                   << "><ACK=" << m_rcv_nxt << "><WND=" << m_rcv_wnd
                   << "><TS=" << (now % 10000)
                   << "><TSR=" << (m_ts_recent % 10000) << "><LEN=" << len
                   << ">";
#endif  // _DEBUGMSG

  IPseudoTcpNotify::WriteResult wres = m_notify->TcpWritePacket(
      this, reinterpret_cast<char*>(buffer.get()), len + HEADER_SIZE);
  // Note: When len is 0, this is an ACK packet.  We don't read the return value
  // for those, and thus we won't retry.  So go ahead and treat the packet as a
  // success (basically simulate as if it were dropped), which will prevent our
  // timers from being messed up.
  if ((wres != IPseudoTcpNotify::WR_SUCCESS) && (0 != len))
    return wres;

  m_t_ack = 0;
  if (len > 0) {
    m_lastsend = now;
  }
  m_lasttraffic = now;
  m_bOutgoing = true;

  return IPseudoTcpNotify::WR_SUCCESS;
}

bool PseudoTcp::parse(const uint8_t* buffer, uint32_t size) {
  if (size < HEADER_SIZE)
    return false;

  Segment seg;
  seg.conv = bytes_to_long(buffer);
  seg.seq = bytes_to_long(buffer + 4);
  seg.ack = bytes_to_long(buffer + 8);
  seg.flags = buffer[13];
  seg.wnd = bytes_to_short(buffer + 14);

  seg.tsval = bytes_to_long(buffer + 16);
  seg.tsecr = bytes_to_long(buffer + 20);

  seg.data = reinterpret_cast<const char*>(buffer) + HEADER_SIZE;
  seg.len = size - HEADER_SIZE;

#if _DEBUGMSG >= _DBG_VERBOSE
  RTC_LOG(LS_INFO) << "--> <CONV=" << seg.conv
                   << "><FLG=" << static_cast<unsigned>(seg.flags)
                   << "><SEQ=" << seg.seq << ":" << seg.seq + seg.len
                   << "><ACK=" << seg.ack << "><WND=" << seg.wnd
                   << "><TS=" << (seg.tsval % 10000)
                   << "><TSR=" << (seg.tsecr % 10000) << "><LEN=" << seg.len
                   << ">";
#endif  // _DEBUGMSG

  return process(seg);
}

bool PseudoTcp::clock_check(uint32_t now, long& nTimeout) {
  if (m_shutdown == SD_FORCEFUL)
    return false;

  if ((m_shutdown == SD_GRACEFUL) &&
      ((m_state != TCP_ESTABLISHED) ||
       ((m_sbuf.GetBuffered() == 0) && (m_t_ack == 0)))) {
    return false;
  }

  if (m_state == TCP_CLOSED) {
    nTimeout = CLOSED_TIMEOUT;
    return true;
  }

  nTimeout = DEFAULT_TIMEOUT;

  if (m_t_ack) {
    nTimeout = std::min<int32_t>(nTimeout,
                                 rtc::TimeDiff32(m_t_ack + m_ack_delay, now));
  }
  if (m_rto_base) {
    nTimeout = std::min<int32_t>(nTimeout,
                                 rtc::TimeDiff32(m_rto_base + m_rx_rto, now));
  }
  if (m_snd_wnd == 0) {
    nTimeout = std::min<int32_t>(nTimeout,
                                 rtc::TimeDiff32(m_lastsend + m_rx_rto, now));
  }
#if PSEUDO_KEEPALIVE
  if (m_state == TCP_ESTABLISHED) {
    nTimeout = std::min<int32_t>(
        nTimeout,
        rtc::TimeDiff32(
            m_lasttraffic + (m_bOutgoing ? IDLE_PING * 3 / 2 : IDLE_PING),
            now));
  }
#endif  // PSEUDO_KEEPALIVE
  return true;
}

bool PseudoTcp::process(Segment& seg) {
  // If this is the wrong conversation, send a reset!?! (with the correct
  // conversation?)
  if (seg.conv != m_conv) {
    // if ((seg.flags & FLAG_RST) == 0) {
    //  packet(tcb, seg.ack, 0, FLAG_RST, 0, 0);
    //}
    RTC_LOG_F(LS_ERROR) << "wrong conversation";
    return false;
  }

  uint32_t now = Now();
  m_lasttraffic = m_lastrecv = now;
  m_bOutgoing = false;

  if (m_state == TCP_CLOSED) {
    // !?! send reset?
    RTC_LOG_F(LS_ERROR) << "closed";
    return false;
  }

  // Check if this is a reset segment
  if (seg.flags & FLAG_RST) {
    closedown(ECONNRESET);
    return false;
  }

  // Check for control data
  bool bConnect = false;
  if (seg.flags & FLAG_CTL) {
    if (seg.len == 0) {
      RTC_LOG_F(LS_ERROR) << "Missing control code";
      return false;
    } else if (seg.data[0] == CTL_CONNECT) {
      bConnect = true;

      // TCP options are in the remainder of the payload after CTL_CONNECT.
      parseOptions(&seg.data[1], seg.len - 1);

      if (m_state == TCP_LISTEN) {
        m_state = TCP_SYN_RECEIVED;
        RTC_LOG(LS_INFO) << "State: TCP_SYN_RECEIVED";
        // m_notify->associate(addr);
        queueConnectMessage();
      } else if (m_state == TCP_SYN_SENT) {
        m_state = TCP_ESTABLISHED;
        RTC_LOG(LS_INFO) << "State: TCP_ESTABLISHED";
        adjustMTU();
        if (m_notify) {
          m_notify->OnTcpOpen(this);
        }
        // notify(evOpen);
      }
    } else {
      RTC_LOG_F(LS_WARNING) << "Unknown control code: " << seg.data[0];
      return false;
    }
  }

  // Update timestamp
  if ((seg.seq <= m_ts_lastack) && (m_ts_lastack < seg.seq + seg.len)) {
    m_ts_recent = seg.tsval;
  }

  // Check if this is a valuable ack
  if ((seg.ack > m_snd_una) && (seg.ack <= m_snd_nxt)) {
    // Calculate round-trip time
    if (seg.tsecr) {
      int32_t rtt = rtc::TimeDiff32(now, seg.tsecr);
      if (rtt >= 0) {
        if (m_rx_srtt == 0) {
          m_rx_srtt = rtt;
          m_rx_rttvar = rtt / 2;
        } else {
          uint32_t unsigned_rtt = static_cast<uint32_t>(rtt);
          uint32_t abs_err = unsigned_rtt > m_rx_srtt
                                 ? unsigned_rtt - m_rx_srtt
                                 : m_rx_srtt - unsigned_rtt;
          m_rx_rttvar = (3 * m_rx_rttvar + abs_err) / 4;
          m_rx_srtt = (7 * m_rx_srtt + rtt) / 8;
        }
        m_rx_rto = rtc::SafeClamp(m_rx_srtt + rtc::SafeMax(1, 4 * m_rx_rttvar),
                                  MIN_RTO, MAX_RTO);
#if _DEBUGMSG >= _DBG_VERBOSE
        RTC_LOG(LS_INFO) << "rtt: " << rtt << "  srtt: " << m_rx_srtt
                         << "  rto: " << m_rx_rto;
#endif  // _DEBUGMSG
      } else {
        RTC_LOG(LS_WARNING) << "rtt < 0";
      }
    }

    m_snd_wnd = static_cast<uint32_t>(seg.wnd) << m_swnd_scale;

    uint32_t nAcked = seg.ack - m_snd_una;
    m_snd_una = seg.ack;

    m_rto_base = (m_snd_una == m_snd_nxt) ? 0 : now;

    m_sbuf.ConsumeReadData(nAcked);

    for (uint32_t nFree = nAcked; nFree > 0;) {
      RTC_DCHECK(!m_slist.empty());
      if (nFree < m_slist.front().len) {
        m_slist.front().len -= nFree;
        nFree = 0;
      } else {
        if (m_slist.front().len > m_largest) {
          m_largest = m_slist.front().len;
        }
        nFree -= m_slist.front().len;
        m_slist.pop_front();
      }
    }

    if (m_dup_acks >= 3) {
      if (m_snd_una >= m_recover) {  // NewReno
        uint32_t nInFlight = m_snd_nxt - m_snd_una;
        m_cwnd = std::min(m_ssthresh, nInFlight + m_mss);  // (Fast Retransmit)
#if _DEBUGMSG >= _DBG_NORMAL
        RTC_LOG(LS_INFO) << "exit recovery";
#endif  // _DEBUGMSG
        m_dup_acks = 0;
      } else {
#if _DEBUGMSG >= _DBG_NORMAL
        RTC_LOG(LS_INFO) << "recovery retransmit";
#endif  // _DEBUGMSG
        if (!transmit(m_slist.begin(), now)) {
          closedown(ECONNABORTED);
          return false;
        }
        m_cwnd += m_mss - std::min(nAcked, m_cwnd);
      }
    } else {
      m_dup_acks = 0;
      // Slow start, congestion avoidance
      if (m_cwnd < m_ssthresh) {
        m_cwnd += m_mss;
      } else {
        m_cwnd += std::max<uint32_t>(1, m_mss * m_mss / m_cwnd);
      }
    }
  } else if (seg.ack == m_snd_una) {
    // !?! Note, tcp says don't do this... but otherwise how does a closed
    // window become open?
    m_snd_wnd = static_cast<uint32_t>(seg.wnd) << m_swnd_scale;

    // Check duplicate acks
    if (seg.len > 0) {
      // it's a dup ack, but with a data payload, so don't modify m_dup_acks
    } else if (m_snd_una != m_snd_nxt) {
      m_dup_acks += 1;
      if (m_dup_acks == 3) {  // (Fast Retransmit)
#if _DEBUGMSG >= _DBG_NORMAL
        RTC_LOG(LS_INFO) << "enter recovery";
        RTC_LOG(LS_INFO) << "recovery retransmit";
#endif  // _DEBUGMSG
        if (!transmit(m_slist.begin(), now)) {
          closedown(ECONNABORTED);
          return false;
        }
        m_recover = m_snd_nxt;
        uint32_t nInFlight = m_snd_nxt - m_snd_una;
        m_ssthresh = std::max(nInFlight / 2, 2 * m_mss);
        // RTC_LOG(LS_INFO) << "m_ssthresh: " << m_ssthresh << "  nInFlight: "
        // << nInFlight << "  m_mss: " << m_mss;
        m_cwnd = m_ssthresh + 3 * m_mss;
      } else if (m_dup_acks > 3) {
        m_cwnd += m_mss;
      }
    } else {
      m_dup_acks = 0;
    }
  }

  // !?! A bit hacky
  if ((m_state == TCP_SYN_RECEIVED) && !bConnect) {
    m_state = TCP_ESTABLISHED;
    RTC_LOG(LS_INFO) << "State: TCP_ESTABLISHED";
    adjustMTU();
    if (m_notify) {
      m_notify->OnTcpOpen(this);
    }
    // notify(evOpen);
  }

  // If we make room in the send queue, notify the user
  // The goal it to make sure we always have at least enough data to fill the
  // window.  We'd like to notify the app when we are halfway to that point.
  const uint32_t kIdealRefillSize = (m_sbuf_len + m_rbuf_len) / 2;
  if (m_bWriteEnable &&
      static_cast<uint32_t>(m_sbuf.GetBuffered()) < kIdealRefillSize) {
    m_bWriteEnable = false;
    if (m_notify) {
      m_notify->OnTcpWriteable(this);
    }
    // notify(evWrite);
  }

  // Conditions were acks must be sent:
  // 1) Segment is too old (they missed an ACK) (immediately)
  // 2) Segment is too new (we missed a segment) (immediately)
  // 3) Segment has data (so we need to ACK!) (delayed)
  // ... so the only time we don't need to ACK, is an empty segment that points
  // to rcv_nxt!

  SendFlags sflags = sfNone;
  if (seg.seq != m_rcv_nxt) {
    sflags = sfImmediateAck;  // (Fast Recovery)
  } else if (seg.len != 0) {
    if (m_ack_delay == 0) {
      sflags = sfImmediateAck;
    } else {
      sflags = sfDelayedAck;
    }
  }
#if _DEBUGMSG >= _DBG_NORMAL
  if (sflags == sfImmediateAck) {
    if (seg.seq > m_rcv_nxt) {
      RTC_LOG_F(LS_INFO) << "too new";
    } else if (seg.seq + seg.len <= m_rcv_nxt) {
      RTC_LOG_F(LS_INFO) << "too old";
    }
  }
#endif  // _DEBUGMSG

  // Adjust the incoming segment to fit our receive buffer
  if (seg.seq < m_rcv_nxt) {
    uint32_t nAdjust = m_rcv_nxt - seg.seq;
    if (nAdjust < seg.len) {
      seg.seq += nAdjust;
      seg.data += nAdjust;
      seg.len -= nAdjust;
    } else {
      seg.len = 0;
    }
  }

  size_t available_space = 0;
  m_rbuf.GetWriteRemaining(&available_space);

  if ((seg.seq + seg.len - m_rcv_nxt) >
      static_cast<uint32_t>(available_space)) {
    uint32_t nAdjust =
        seg.seq + seg.len - m_rcv_nxt - static_cast<uint32_t>(available_space);
    if (nAdjust < seg.len) {
      seg.len -= nAdjust;
    } else {
      seg.len = 0;
    }
  }

  bool bIgnoreData = (seg.flags & FLAG_CTL) || (m_shutdown != SD_NONE);
  bool bNewData = false;

  if (seg.len > 0) {
    bool bRecover = false;
    if (bIgnoreData) {
      if (seg.seq == m_rcv_nxt) {
        m_rcv_nxt += seg.len;
        // If we received a data segment out of order relative to a control
        // segment, then we wrote it into the receive buffer at an offset (see
        // "WriteOffset") below. So we need to advance the position in the
        // buffer to avoid corrupting data. See bugs.webrtc.org/9208
        //
        // We advance the position in the buffer by N bytes by acting like we
        // wrote N bytes and then immediately read them. We can only do this if
        // there's not already data ready to read, but this should always be
        // true in the problematic scenario, since control frames are always
        // sent first in the stream.
        if (m_rbuf.GetBuffered() == 0) {
          m_rbuf.ConsumeWriteBuffer(seg.len);
          m_rbuf.ConsumeReadData(seg.len);
          // After shifting the position in the buffer, we may have
          // out-of-order packets ready to be recovered.
          bRecover = true;
        }
      }
    } else {
      uint32_t nOffset = seg.seq - m_rcv_nxt;

      if (!m_rbuf.WriteOffset(seg.data, seg.len, nOffset, NULL)) {
        // Ignore incoming packets outside of the receive window.
        return false;
      }

      if (seg.seq == m_rcv_nxt) {
        m_rbuf.ConsumeWriteBuffer(seg.len);
        m_rcv_nxt += seg.len;
        m_rcv_wnd -= seg.len;
        bNewData = true;
        // May be able to recover packets previously received out-of-order
        // now.
        bRecover = true;
      } else {
#if _DEBUGMSG >= _DBG_NORMAL
        RTC_LOG(LS_INFO) << "Saving " << seg.len << " bytes (" << seg.seq
                         << " -> " << seg.seq + seg.len << ")";
#endif  // _DEBUGMSG
        RSegment rseg;
        rseg.seq = seg.seq;
        rseg.len = seg.len;
        RList::iterator it = m_rlist.begin();
        while ((it != m_rlist.end()) && (it->seq < rseg.seq)) {
          ++it;
        }
        m_rlist.insert(it, rseg);
      }
    }
    if (bRecover) {
      RList::iterator it = m_rlist.begin();
      while ((it != m_rlist.end()) && (it->seq <= m_rcv_nxt)) {
        if (it->seq + it->len > m_rcv_nxt) {
          sflags = sfImmediateAck;  // (Fast Recovery)
          uint32_t nAdjust = (it->seq + it->len) - m_rcv_nxt;
#if _DEBUGMSG >= _DBG_NORMAL
          RTC_LOG(LS_INFO) << "Recovered " << nAdjust << " bytes (" << m_rcv_nxt
                           << " -> " << m_rcv_nxt + nAdjust << ")";
#endif  // _DEBUGMSG
          m_rbuf.ConsumeWriteBuffer(nAdjust);
          m_rcv_nxt += nAdjust;
          m_rcv_wnd -= nAdjust;
          bNewData = true;
        }
        it = m_rlist.erase(it);
      }
    }
  }

  attemptSend(sflags);

  // If we have new data, notify the user
  if (bNewData && m_bReadEnable) {
    m_bReadEnable = false;
    if (m_notify) {
      m_notify->OnTcpReadable(this);
    }
    // notify(evRead);
  }

  return true;
}

bool PseudoTcp::transmit(const SList::iterator& seg, uint32_t now) {
  if (seg->xmit >= ((m_state == TCP_ESTABLISHED) ? 15 : 30)) {
    RTC_LOG_F(LS_VERBOSE) << "too many retransmits";
    return false;
  }

  uint32_t nTransmit = std::min(seg->len, m_mss);

  while (true) {
    uint32_t seq = seg->seq;
    uint8_t flags = (seg->bCtrl ? FLAG_CTL : 0);
    IPseudoTcpNotify::WriteResult wres =
        packet(seq, flags, seg->seq - m_snd_una, nTransmit);

    if (wres == IPseudoTcpNotify::WR_SUCCESS)
      break;

    if (wres == IPseudoTcpNotify::WR_FAIL) {
      RTC_LOG_F(LS_VERBOSE) << "packet failed";
      return false;
    }

    RTC_DCHECK(wres == IPseudoTcpNotify::WR_TOO_LARGE);

    while (true) {
      if (PACKET_MAXIMUMS[m_msslevel + 1] == 0) {
        RTC_LOG_F(LS_VERBOSE) << "MTU too small";
        return false;
      }
      // !?! We need to break up all outstanding and pending packets and then
      // retransmit!?!

      m_mss = PACKET_MAXIMUMS[++m_msslevel] - PACKET_OVERHEAD;
      m_cwnd = 2 * m_mss;  // I added this... haven't researched actual formula
      if (m_mss < nTransmit) {
        nTransmit = m_mss;
        break;
      }
    }
#if _DEBUGMSG >= _DBG_NORMAL
    RTC_LOG(LS_INFO) << "Adjusting mss to " << m_mss << " bytes";
#endif  // _DEBUGMSG
  }

  if (nTransmit < seg->len) {
    RTC_LOG_F(LS_VERBOSE) << "mss reduced to " << m_mss;

    SSegment subseg(seg->seq + nTransmit, seg->len - nTransmit, seg->bCtrl);
    // subseg.tstamp = seg->tstamp;
    subseg.xmit = seg->xmit;
    seg->len = nTransmit;

    SList::iterator next = seg;
    m_slist.insert(++next, subseg);
  }

  if (seg->xmit == 0) {
    m_snd_nxt += seg->len;
  }
  seg->xmit += 1;
  // seg->tstamp = now;
  if (m_rto_base == 0) {
    m_rto_base = now;
  }

  return true;
}

void PseudoTcp::attemptSend(SendFlags sflags) {
  uint32_t now = Now();

  if (rtc::TimeDiff32(now, m_lastsend) > static_cast<long>(m_rx_rto)) {
    m_cwnd = m_mss;
  }

#if _DEBUGMSG
  bool bFirst = true;
#endif  // _DEBUGMSG

  while (true) {
    uint32_t cwnd = m_cwnd;
    if ((m_dup_acks == 1) || (m_dup_acks == 2)) {  // Limited Transmit
      cwnd += m_dup_acks * m_mss;
    }
    uint32_t nWindow = std::min(m_snd_wnd, cwnd);
    uint32_t nInFlight = m_snd_nxt - m_snd_una;
    uint32_t nUseable = (nInFlight < nWindow) ? (nWindow - nInFlight) : 0;

    size_t snd_buffered = m_sbuf.GetBuffered();
    uint32_t nAvailable =
        std::min(static_cast<uint32_t>(snd_buffered) - nInFlight, m_mss);

    if (nAvailable > nUseable) {
      if (nUseable * 4 < nWindow) {
        // RFC 813 - avoid SWS
        nAvailable = 0;
      } else {
        nAvailable = nUseable;
      }
    }

#if _DEBUGMSG >= _DBG_VERBOSE
    if (bFirst) {
      size_t available_space = 0;
      m_sbuf.GetWriteRemaining(&available_space);

      bFirst = false;
      RTC_LOG(LS_INFO) << "[cwnd: " << m_cwnd << "  nWindow: " << nWindow
                       << "  nInFlight: " << nInFlight
                       << "  nAvailable: " << nAvailable
                       << "  nQueued: " << snd_buffered
                       << "  nEmpty: " << available_space
                       << "  ssthresh: " << m_ssthresh << "]";
    }
#endif  // _DEBUGMSG

    if (nAvailable == 0) {
      if (sflags == sfNone)
        return;

      // If this is an immediate ack, or the second delayed ack
      if ((sflags == sfImmediateAck) || m_t_ack) {
        packet(m_snd_nxt, 0, 0, 0);
      } else {
        m_t_ack = Now();
      }
      return;
    }

    // Nagle's algorithm.
    // If there is data already in-flight, and we haven't a full segment of
    // data ready to send then hold off until we get more to send, or the
    // in-flight data is acknowledged.
    if (m_use_nagling && (m_snd_nxt > m_snd_una) && (nAvailable < m_mss)) {
      return;
    }

    // Find the next segment to transmit
    SList::iterator it = m_slist.begin();
    while (it->xmit > 0) {
      ++it;
      RTC_DCHECK(it != m_slist.end());
    }
    SList::iterator seg = it;

    // If the segment is too large, break it into two
    if (seg->len > nAvailable) {
      SSegment subseg(seg->seq + nAvailable, seg->len - nAvailable, seg->bCtrl);
      seg->len = nAvailable;
      m_slist.insert(++it, subseg);
    }

    if (!transmit(seg, now)) {
      RTC_LOG_F(LS_VERBOSE) << "transmit failed";
      // TODO(?): consider closing socket
      return;
    }

    sflags = sfNone;
  }
}

void PseudoTcp::closedown(uint32_t err) {
  RTC_LOG(LS_INFO) << "State: TCP_CLOSED";
  m_state = TCP_CLOSED;
  if (m_notify) {
    m_notify->OnTcpClosed(this, err);
  }
  // notify(evClose, err);
}

void PseudoTcp::adjustMTU() {
  // Determine our current mss level, so that we can adjust appropriately later
  for (m_msslevel = 0; PACKET_MAXIMUMS[m_msslevel + 1] > 0; ++m_msslevel) {
    if (static_cast<uint16_t>(PACKET_MAXIMUMS[m_msslevel]) <= m_mtu_advise) {
      break;
    }
  }
  m_mss = m_mtu_advise - PACKET_OVERHEAD;
// !?! Should we reset m_largest here?
#if _DEBUGMSG >= _DBG_NORMAL
  RTC_LOG(LS_INFO) << "Adjusting mss to " << m_mss << " bytes";
#endif  // _DEBUGMSG
  // Enforce minimums on ssthresh and cwnd
  m_ssthresh = std::max(m_ssthresh, 2 * m_mss);
  m_cwnd = std::max(m_cwnd, m_mss);
}

bool PseudoTcp::isReceiveBufferFull() const {
  size_t available_space = 0;
  m_rbuf.GetWriteRemaining(&available_space);
  return !available_space;
}

void PseudoTcp::disableWindowScale() {
  m_support_wnd_scale = false;
}

void PseudoTcp::queueConnectMessage() {
  rtc::ByteBufferWriter buf;

  buf.WriteUInt8(CTL_CONNECT);
  if (m_support_wnd_scale) {
    buf.WriteUInt8(TCP_OPT_WND_SCALE);
    buf.WriteUInt8(1);
    buf.WriteUInt8(m_rwnd_scale);
  }
  m_snd_wnd = static_cast<uint32_t>(buf.Length());
  queue(buf.Data(), static_cast<uint32_t>(buf.Length()), true);
}

void PseudoTcp::parseOptions(const char* data, uint32_t len) {
  std::set<uint8_t> options_specified;

  // See http://www.freesoft.org/CIE/Course/Section4/8.htm for
  // parsing the options list.
  rtc::ByteBufferReader buf(data, len);
  while (buf.Length()) {
    uint8_t kind = TCP_OPT_EOL;
    buf.ReadUInt8(&kind);

    if (kind == TCP_OPT_EOL) {
      // End of option list.
      break;
    } else if (kind == TCP_OPT_NOOP) {
      // No op.
      continue;
    }

    // Length of this option.
    RTC_DCHECK(len != 0);
    uint8_t opt_len = 0;
    buf.ReadUInt8(&opt_len);

    // Content of this option.
    if (opt_len <= buf.Length()) {
      applyOption(kind, buf.Data(), opt_len);
      buf.Consume(opt_len);
    } else {
      RTC_LOG(LS_ERROR) << "Invalid option length received.";
      return;
    }
    options_specified.insert(kind);
  }

  if (options_specified.find(TCP_OPT_WND_SCALE) == options_specified.end()) {
    RTC_LOG(LS_WARNING) << "Peer doesn't support window scaling";

    if (m_rwnd_scale > 0) {
      // Peer doesn't support TCP options and window scaling.
      // Revert receive buffer size to default value.
      resizeReceiveBuffer(DEFAULT_RCV_BUF_SIZE);
      m_swnd_scale = 0;
    }
  }
}

void PseudoTcp::applyOption(char kind, const char* data, uint32_t len) {
  if (kind == TCP_OPT_MSS) {
    RTC_LOG(LS_WARNING) << "Peer specified MSS option which is not supported.";
    // TODO(?): Implement.
  } else if (kind == TCP_OPT_WND_SCALE) {
    // Window scale factor.
    // http://www.ietf.org/rfc/rfc1323.txt
    if (len != 1) {
      RTC_LOG_F(LS_WARNING) << "Invalid window scale option received.";
      return;
    }
    applyWindowScaleOption(data[0]);
  }
}

void PseudoTcp::applyWindowScaleOption(uint8_t scale_factor) {
  m_swnd_scale = scale_factor;
}

void PseudoTcp::resizeSendBuffer(uint32_t new_size) {
  m_sbuf_len = new_size;
  m_sbuf.SetCapacity(new_size);
}

void PseudoTcp::resizeReceiveBuffer(uint32_t new_size) {
  uint8_t scale_factor = 0;

  // Determine the scale factor such that the scaled window size can fit
  // in a 16-bit unsigned integer.
  while (new_size > 0xFFFF) {
    ++scale_factor;
    new_size >>= 1;
  }

  // Determine the proper size of the buffer.
  new_size <<= scale_factor;
  bool result = m_rbuf.SetCapacity(new_size);

  // Make sure the new buffer is large enough to contain data in the old
  // buffer. This should always be true because this method is called either
  // before connection is established or when peers are exchanging connect
  // messages.
  RTC_DCHECK(result);
  m_rbuf_len = new_size;
  m_rwnd_scale = scale_factor;
  m_ssthresh = new_size;

  size_t available_space = 0;
  m_rbuf.GetWriteRemaining(&available_space);
  m_rcv_wnd = static_cast<uint32_t>(available_space);
}

PseudoTcp::LockedFifoBuffer::LockedFifoBuffer(size_t size)
    : buffer_(new char[size]),
      buffer_length_(size),
      data_length_(0),
      read_position_(0) {}

PseudoTcp::LockedFifoBuffer::~LockedFifoBuffer() {}

size_t PseudoTcp::LockedFifoBuffer::GetBuffered() const {
  webrtc::MutexLock lock(&mutex_);
  return data_length_;
}

bool PseudoTcp::LockedFifoBuffer::SetCapacity(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  if (data_length_ > size)
    return false;

  if (size != buffer_length_) {
    char* buffer = new char[size];
    const size_t copy = data_length_;
    const size_t tail_copy = std::min(copy, buffer_length_ - read_position_);
    memcpy(buffer, &buffer_[read_position_], tail_copy);
    memcpy(buffer + tail_copy, &buffer_[0], copy - tail_copy);
    buffer_.reset(buffer);
    read_position_ = 0;
    buffer_length_ = size;
  }

  return true;
}

bool PseudoTcp::LockedFifoBuffer::ReadOffset(void* buffer,
                                             size_t bytes,
                                             size_t offset,
                                             size_t* bytes_read) {
  webrtc::MutexLock lock(&mutex_);
  return ReadOffsetLocked(buffer, bytes, offset, bytes_read);
}

bool PseudoTcp::LockedFifoBuffer::WriteOffset(const void* buffer,
                                              size_t bytes,
                                              size_t offset,
                                              size_t* bytes_written) {
  webrtc::MutexLock lock(&mutex_);
  return WriteOffsetLocked(buffer, bytes, offset, bytes_written);
}

bool PseudoTcp::LockedFifoBuffer::Read(void* buffer,
                                       size_t bytes,
                                       size_t* bytes_read) {
  webrtc::MutexLock lock(&mutex_);
  size_t copy = 0;
  if (!ReadOffsetLocked(buffer, bytes, 0, &copy))
    return false;

  // If read was successful then adjust the read position and number of
  // bytes buffered.
  read_position_ = (read_position_ + copy) % buffer_length_;
  data_length_ -= copy;
  if (bytes_read)
    *bytes_read = copy;

  return true;
}

bool PseudoTcp::LockedFifoBuffer::Write(const void* buffer,
                                        size_t bytes,
                                        size_t* bytes_written) {
  webrtc::MutexLock lock(&mutex_);
  size_t copy = 0;
  if (!WriteOffsetLocked(buffer, bytes, 0, &copy))
    return false;

  // If write was successful then adjust the number of readable bytes.
  data_length_ += copy;
  if (bytes_written) {
    *bytes_written = copy;
  }

  return true;
}

void PseudoTcp::LockedFifoBuffer::ConsumeReadData(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  RTC_DCHECK(size <= data_length_);
  read_position_ = (read_position_ + size) % buffer_length_;
  data_length_ -= size;
}

void PseudoTcp::LockedFifoBuffer::ConsumeWriteBuffer(size_t size) {
  webrtc::MutexLock lock(&mutex_);
  RTC_DCHECK(size <= buffer_length_ - data_length_);
  data_length_ += size;
}

bool PseudoTcp::LockedFifoBuffer::GetWriteRemaining(size_t* size) const {
  webrtc::MutexLock lock(&mutex_);
  *size = buffer_length_ - data_length_;
  return true;
}

bool PseudoTcp::LockedFifoBuffer::ReadOffsetLocked(void* buffer,
                                                   size_t bytes,
                                                   size_t offset,
                                                   size_t* bytes_read) {
  if (offset >= data_length_)
    return false;

  const size_t available = data_length_ - offset;
  const size_t read_position = (read_position_ + offset) % buffer_length_;
  const size_t copy = std::min(bytes, available);
  const size_t tail_copy = std::min(copy, buffer_length_ - read_position);
  char* const p = static_cast<char*>(buffer);
  memcpy(p, &buffer_[read_position], tail_copy);
  memcpy(p + tail_copy, &buffer_[0], copy - tail_copy);

  if (bytes_read)
    *bytes_read = copy;

  return true;
}

bool PseudoTcp::LockedFifoBuffer::WriteOffsetLocked(const void* buffer,
                                                    size_t bytes,
                                                    size_t offset,
                                                    size_t* bytes_written) {
  if (data_length_ + offset >= buffer_length_)
    return false;

  const size_t available = buffer_length_ - data_length_ - offset;
  const size_t write_position =
      (read_position_ + data_length_ + offset) % buffer_length_;
  const size_t copy = std::min(bytes, available);
  const size_t tail_copy = std::min(copy, buffer_length_ - write_position);
  const char* const p = static_cast<const char*>(buffer);
  memcpy(&buffer_[write_position], p, tail_copy);
  memcpy(&buffer_[0], p + tail_copy, copy - tail_copy);

  if (bytes_written)
    *bytes_written = copy;

  return true;
}

}  // namespace cricket
