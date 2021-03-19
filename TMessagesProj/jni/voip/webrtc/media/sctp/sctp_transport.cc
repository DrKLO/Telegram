/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <errno.h>
namespace {
// Some ERRNO values get re-#defined to WSA* equivalents in some talk/
// headers. We save the original ones in an enum.
enum PreservedErrno {
  SCTP_EINPROGRESS = EINPROGRESS,
  SCTP_EWOULDBLOCK = EWOULDBLOCK
};

// Successful return value from usrsctp callbacks. Is not actually used by
// usrsctp, but all example programs for usrsctp use 1 as their return value.
constexpr int kSctpSuccessReturn = 1;

}  // namespace

#include <stdarg.h>
#include <stdio.h>
#include <usrsctp.h>

#include <memory>
#include <unordered_map>

#include "absl/algorithm/container.h"
#include "absl/base/attributes.h"
#include "absl/types/optional.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/media_constants.h"
#include "media/base/stream_params.h"
#include "media/sctp/sctp_transport.h"
#include "p2p/base/dtls_transport_internal.h"  // For PF_NORMAL
#include "rtc_base/arraysize.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_checker.h"
#include "rtc_base/trace_event.h"

namespace {

// The biggest SCTP packet. Starting from a 'safe' wire MTU value of 1280,
// take off 80 bytes for DTLS/TURN/TCP/IP overhead.
static constexpr size_t kSctpMtu = 1200;

// Set the initial value of the static SCTP Data Engines reference count.
ABSL_CONST_INIT int g_usrsctp_usage_count = 0;
ABSL_CONST_INIT bool g_usrsctp_initialized_ = false;
ABSL_CONST_INIT webrtc::GlobalMutex g_usrsctp_lock_(absl::kConstInit);

// DataMessageType is used for the SCTP "Payload Protocol Identifier", as
// defined in http://tools.ietf.org/html/rfc4960#section-14.4
//
// For the list of IANA approved values see:
// http://www.iana.org/assignments/sctp-parameters/sctp-parameters.xml
// The value is not used by SCTP itself. It indicates the protocol running
// on top of SCTP.
enum {
  PPID_NONE = 0,  // No protocol is specified.
  // Matches the PPIDs in mozilla source and
  // https://datatracker.ietf.org/doc/draft-ietf-rtcweb-data-protocol Sec. 9
  // They're not yet assigned by IANA.
  PPID_CONTROL = 50,
  PPID_BINARY_PARTIAL = 52,
  PPID_BINARY_LAST = 53,
  PPID_TEXT_PARTIAL = 54,
  PPID_TEXT_LAST = 51
};

// Maps SCTP transport ID to SctpTransport object, necessary in send threshold
// callback and outgoing packet callback.
// TODO(crbug.com/1076703): Remove once the underlying problem is fixed or
// workaround is provided in usrsctp.
class SctpTransportMap {
 public:
  SctpTransportMap() = default;

  // Assigns a new unused ID to the following transport.
  uintptr_t Register(cricket::SctpTransport* transport) {
    webrtc::MutexLock lock(&lock_);
    // usrsctp_connect fails with a value of 0...
    if (next_id_ == 0) {
      ++next_id_;
    }
    // In case we've wrapped around and need to find an empty spot from a
    // removed transport. Assumes we'll never be full.
    while (map_.find(next_id_) != map_.end()) {
      ++next_id_;
      if (next_id_ == 0) {
        ++next_id_;
      }
    };
    map_[next_id_] = transport;
    return next_id_++;
  }

  // Returns true if found.
  bool Deregister(uintptr_t id) {
    webrtc::MutexLock lock(&lock_);
    return map_.erase(id) > 0;
  }

  cricket::SctpTransport* Retrieve(uintptr_t id) const {
    webrtc::MutexLock lock(&lock_);
    auto it = map_.find(id);
    if (it == map_.end()) {
      return nullptr;
    }
    return it->second;
  }

 private:
  mutable webrtc::Mutex lock_;

  uintptr_t next_id_ RTC_GUARDED_BY(lock_) = 0;
  std::unordered_map<uintptr_t, cricket::SctpTransport*> map_
      RTC_GUARDED_BY(lock_);
};

// Should only be modified by UsrSctpWrapper.
ABSL_CONST_INIT SctpTransportMap* g_transport_map_ = nullptr;

// Helper for logging SCTP messages.
#if defined(__GNUC__)
__attribute__((__format__(__printf__, 1, 2)))
#endif
void DebugSctpPrintf(const char* format, ...) {
#if RTC_DCHECK_IS_ON
  char s[255];
  va_list ap;
  va_start(ap, format);
  vsnprintf(s, sizeof(s), format, ap);
  RTC_LOG(LS_INFO) << "SCTP: " << s;
  va_end(ap);
#endif
}

// Get the PPID to use for the terminating fragment of this type.
uint32_t GetPpid(cricket::DataMessageType type) {
  switch (type) {
    default:
    case cricket::DMT_NONE:
      return PPID_NONE;
    case cricket::DMT_CONTROL:
      return PPID_CONTROL;
    case cricket::DMT_BINARY:
      return PPID_BINARY_LAST;
    case cricket::DMT_TEXT:
      return PPID_TEXT_LAST;
  }
}

bool GetDataMediaType(uint32_t ppid, cricket::DataMessageType* dest) {
  RTC_DCHECK(dest != NULL);
  switch (ppid) {
    case PPID_BINARY_PARTIAL:
    case PPID_BINARY_LAST:
      *dest = cricket::DMT_BINARY;
      return true;

    case PPID_TEXT_PARTIAL:
    case PPID_TEXT_LAST:
      *dest = cricket::DMT_TEXT;
      return true;

    case PPID_CONTROL:
      *dest = cricket::DMT_CONTROL;
      return true;

    case PPID_NONE:
      *dest = cricket::DMT_NONE;
      return true;

    default:
      return false;
  }
}

// Log the packet in text2pcap format, if log level is at LS_VERBOSE.
//
// In order to turn these logs into a pcap file you can use, first filter the
// "SCTP_PACKET" log lines:
//
//   cat chrome_debug.log | grep SCTP_PACKET > filtered.log
//
// Then run through text2pcap:
//
//   text2pcap -n -l 248 -D -t '%H:%M:%S.' filtered.log filtered.pcapng
//
// Command flag information:
// -n: Outputs to a pcapng file, can specify inbound/outbound packets.
// -l: Specifies the link layer header type. 248 means SCTP. See:
//     http://www.tcpdump.org/linktypes.html
// -D: Text before packet specifies if it is inbound or outbound.
// -t: Time format.
//
// Why do all this? Because SCTP goes over DTLS, which is encrypted. So just
// getting a normal packet capture won't help you, unless you have the DTLS
// keying material.
void VerboseLogPacket(const void* data, size_t length, int direction) {
  if (RTC_LOG_CHECK_LEVEL(LS_VERBOSE) && length > 0) {
    char* dump_buf;
    // Some downstream project uses an older version of usrsctp that expects
    // a non-const "void*" as first parameter when dumping the packet, so we
    // need to cast the const away here to avoid a compiler error.
    if ((dump_buf = usrsctp_dumppacket(const_cast<void*>(data), length,
                                       direction)) != NULL) {
      RTC_LOG(LS_VERBOSE) << dump_buf;
      usrsctp_freedumpbuffer(dump_buf);
    }
  }
}

// Creates the sctp_sendv_spa struct used for setting flags in the
// sctp_sendv() call.
sctp_sendv_spa CreateSctpSendParams(const cricket::SendDataParams& params) {
  struct sctp_sendv_spa spa = {0};
  spa.sendv_flags |= SCTP_SEND_SNDINFO_VALID;
  spa.sendv_sndinfo.snd_sid = params.sid;
  spa.sendv_sndinfo.snd_ppid = rtc::HostToNetwork32(GetPpid(params.type));
  // Explicitly marking the EOR flag turns the usrsctp_sendv call below into a
  // non atomic operation. This means that the sctp lib might only accept the
  // message partially. This is done in order to improve throughput, so that we
  // don't have to wait for an empty buffer to send the max message length, for
  // example.
  spa.sendv_sndinfo.snd_flags |= SCTP_EOR;

  // Ordered implies reliable.
  if (!params.ordered) {
    spa.sendv_sndinfo.snd_flags |= SCTP_UNORDERED;
    if (params.max_rtx_count >= 0 || params.max_rtx_ms == 0) {
      spa.sendv_flags |= SCTP_SEND_PRINFO_VALID;
      spa.sendv_prinfo.pr_policy = SCTP_PR_SCTP_RTX;
      spa.sendv_prinfo.pr_value = params.max_rtx_count;
    } else {
      spa.sendv_flags |= SCTP_SEND_PRINFO_VALID;
      spa.sendv_prinfo.pr_policy = SCTP_PR_SCTP_TTL;
      spa.sendv_prinfo.pr_value = params.max_rtx_ms;
    }
  }
  return spa;
}
}  // namespace

namespace cricket {

// Handles global init/deinit, and mapping from usrsctp callbacks to
// SctpTransport calls.
class SctpTransport::UsrSctpWrapper {
 public:
  static void InitializeUsrSctp() {
    RTC_LOG(LS_INFO) << __FUNCTION__;
    // UninitializeUsrSctp tries to call usrsctp_finish in a loop for three
    // seconds; if that failed and we were left in a still-initialized state, we
    // don't want to call usrsctp_init again as that will result in undefined
    // behavior.
    if (g_usrsctp_initialized_) {
      RTC_LOG(LS_WARNING) << "Not reinitializing usrsctp since last attempt at "
                             "usrsctp_finish failed.";
    } else {
      // First argument is udp_encapsulation_port, which is not releveant for
      // our AF_CONN use of sctp.
      usrsctp_init(0, &UsrSctpWrapper::OnSctpOutboundPacket, &DebugSctpPrintf);
      g_usrsctp_initialized_ = true;
    }

    // To turn on/off detailed SCTP debugging. You will also need to have the
    // SCTP_DEBUG cpp defines flag, which can be turned on in media/BUILD.gn.
    // usrsctp_sysctl_set_sctp_debug_on(SCTP_DEBUG_ALL);

    // TODO(ldixon): Consider turning this on/off.
    usrsctp_sysctl_set_sctp_ecn_enable(0);

    // WebRTC doesn't use these features, so disable them to reduce the
    // potential attack surface.
    usrsctp_sysctl_set_sctp_asconf_enable(0);
    usrsctp_sysctl_set_sctp_auth_enable(0);

    // This is harmless, but we should find out when the library default
    // changes.
    int send_size = usrsctp_sysctl_get_sctp_sendspace();
    if (send_size != kSctpSendBufferSize) {
      RTC_LOG(LS_ERROR) << "Got different send size than expected: "
                        << send_size;
    }

    // TODO(ldixon): Consider turning this on/off.
    // This is not needed right now (we don't do dynamic address changes):
    // If SCTP Auto-ASCONF is enabled, the peer is informed automatically
    // when a new address is added or removed. This feature is enabled by
    // default.
    // usrsctp_sysctl_set_sctp_auto_asconf(0);

    // TODO(ldixon): Consider turning this on/off.
    // Add a blackhole sysctl. Setting it to 1 results in no ABORTs
    // being sent in response to INITs, setting it to 2 results
    // in no ABORTs being sent for received OOTB packets.
    // This is similar to the TCP sysctl.
    //
    // See: http://lakerest.net/pipermail/sctp-coders/2012-January/009438.html
    // See: http://svnweb.freebsd.org/base?view=revision&revision=229805
    // usrsctp_sysctl_set_sctp_blackhole(2);

    // Set the number of default outgoing streams. This is the number we'll
    // send in the SCTP INIT message.
    usrsctp_sysctl_set_sctp_nr_outgoing_streams_default(kMaxSctpStreams);

    g_transport_map_ = new SctpTransportMap();
  }

  static void UninitializeUsrSctp() {
    RTC_LOG(LS_INFO) << __FUNCTION__;
    // usrsctp_finish() may fail if it's called too soon after the transports
    // are
    // closed. Wait and try again until it succeeds for up to 3 seconds.
    for (size_t i = 0; i < 300; ++i) {
      if (usrsctp_finish() == 0) {
        g_usrsctp_initialized_ = false;
        delete g_transport_map_;
        g_transport_map_ = nullptr;
        return;
      }

      rtc::Thread::SleepMs(10);
    }
    delete g_transport_map_;
    g_transport_map_ = nullptr;
    RTC_LOG(LS_ERROR) << "Failed to shutdown usrsctp.";
  }

  static void IncrementUsrSctpUsageCount() {
    webrtc::GlobalMutexLock lock(&g_usrsctp_lock_);
    if (!g_usrsctp_usage_count) {
      InitializeUsrSctp();
    }
    ++g_usrsctp_usage_count;
  }

  static void DecrementUsrSctpUsageCount() {
    webrtc::GlobalMutexLock lock(&g_usrsctp_lock_);
    --g_usrsctp_usage_count;
    if (!g_usrsctp_usage_count) {
      UninitializeUsrSctp();
    }
  }

  // This is the callback usrsctp uses when there's data to send on the network
  // that has been wrapped appropriatly for the SCTP protocol.
  static int OnSctpOutboundPacket(void* addr,
                                  void* data,
                                  size_t length,
                                  uint8_t tos,
                                  uint8_t set_df) {
    if (!g_transport_map_) {
      RTC_LOG(LS_ERROR)
          << "OnSctpOutboundPacket called after usrsctp uninitialized?";
      return EINVAL;
    }
    SctpTransport* transport =
        g_transport_map_->Retrieve(reinterpret_cast<uintptr_t>(addr));
    if (!transport) {
      RTC_LOG(LS_ERROR)
          << "OnSctpOutboundPacket: Failed to get transport for socket ID "
          << addr;
      return EINVAL;
    }
    RTC_LOG(LS_VERBOSE) << "global OnSctpOutboundPacket():"
                           "addr: "
                        << addr << "; length: " << length
                        << "; tos: " << rtc::ToHex(tos)
                        << "; set_df: " << rtc::ToHex(set_df);

    VerboseLogPacket(data, length, SCTP_DUMP_OUTBOUND);
    // Note: We have to copy the data; the caller will delete it.
    rtc::CopyOnWriteBuffer buf(reinterpret_cast<uint8_t*>(data), length);
    // TODO(deadbeef): Why do we need an AsyncInvoke here? We're already on the
    // right thread and don't need to unwind the stack.
    transport->invoker_.AsyncInvoke<void>(
        RTC_FROM_HERE, transport->network_thread_,
        rtc::Bind(&SctpTransport::OnPacketFromSctpToNetwork, transport, buf));
    return 0;
  }

  // This is the callback called from usrsctp when data has been received, after
  // a packet has been interpreted and parsed by usrsctp and found to contain
  // payload data. It is called by a usrsctp thread. It is assumed this function
  // will free the memory used by 'data'.
  static int OnSctpInboundPacket(struct socket* sock,
                                 union sctp_sockstore addr,
                                 void* data,
                                 size_t length,
                                 struct sctp_rcvinfo rcv,
                                 int flags,
                                 void* ulp_info) {
    SctpTransport* transport = GetTransportFromSocket(sock);
    if (!transport) {
      RTC_LOG(LS_ERROR)
          << "OnSctpInboundPacket: Failed to get transport for socket " << sock
          << "; possibly was already destroyed.";
      free(data);
      return 0;
    }
    // Sanity check that both methods of getting the SctpTransport pointer
    // yield the same result.
    RTC_CHECK_EQ(transport, static_cast<SctpTransport*>(ulp_info));
    int result =
        transport->OnDataOrNotificationFromSctp(data, length, rcv, flags);
    free(data);
    return result;
  }

  static SctpTransport* GetTransportFromSocket(struct socket* sock) {
    struct sockaddr* addrs = nullptr;
    int naddrs = usrsctp_getladdrs(sock, 0, &addrs);
    if (naddrs <= 0 || addrs[0].sa_family != AF_CONN) {
      return nullptr;
    }
    // usrsctp_getladdrs() returns the addresses bound to this socket, which
    // contains the SctpTransport id as sconn_addr.  Read the id,
    // then free the list of addresses once we have the pointer.  We only open
    // AF_CONN sockets, and they should all have the sconn_addr set to the
    // id of the transport that created them, so [0] is as good as any other.
    struct sockaddr_conn* sconn =
        reinterpret_cast<struct sockaddr_conn*>(&addrs[0]);
    if (!g_transport_map_) {
      RTC_LOG(LS_ERROR)
          << "GetTransportFromSocket called after usrsctp uninitialized?";
      usrsctp_freeladdrs(addrs);
      return nullptr;
    }
    SctpTransport* transport = g_transport_map_->Retrieve(
        reinterpret_cast<uintptr_t>(sconn->sconn_addr));
    usrsctp_freeladdrs(addrs);

    return transport;
  }

  // TODO(crbug.com/webrtc/11899): This is a legacy callback signature, remove
  // when usrsctp is updated.
  static int SendThresholdCallback(struct socket* sock, uint32_t sb_free) {
    // Fired on our I/O thread. SctpTransport::OnPacketReceived() gets
    // a packet containing acknowledgments, which goes into usrsctp_conninput,
    // and then back here.
    SctpTransport* transport = GetTransportFromSocket(sock);
    if (!transport) {
      RTC_LOG(LS_ERROR)
          << "SendThresholdCallback: Failed to get transport for socket "
          << sock << "; possibly was already destroyed.";
      return 0;
    }
    transport->OnSendThresholdCallback();
    return 0;
  }

  static int SendThresholdCallback(struct socket* sock,
                                   uint32_t sb_free,
                                   void* ulp_info) {
    // Fired on our I/O thread. SctpTransport::OnPacketReceived() gets
    // a packet containing acknowledgments, which goes into usrsctp_conninput,
    // and then back here.
    SctpTransport* transport = GetTransportFromSocket(sock);
    if (!transport) {
      RTC_LOG(LS_ERROR)
          << "SendThresholdCallback: Failed to get transport for socket "
          << sock << "; possibly was already destroyed.";
      return 0;
    }
    // Sanity check that both methods of getting the SctpTransport pointer
    // yield the same result.
    RTC_CHECK_EQ(transport, static_cast<SctpTransport*>(ulp_info));
    transport->OnSendThresholdCallback();
    return 0;
  }
};

SctpTransport::SctpTransport(rtc::Thread* network_thread,
                             rtc::PacketTransportInternal* transport)
    : network_thread_(network_thread),
      transport_(transport),
      was_ever_writable_(transport ? transport->writable() : false) {
  RTC_DCHECK(network_thread_);
  RTC_DCHECK_RUN_ON(network_thread_);
  ConnectTransportSignals();
}

SctpTransport::~SctpTransport() {
  // Close abruptly; no reset procedure.
  CloseSctpSocket();
  // It's not strictly necessary to reset these fields to nullptr,
  // but having these fields set to nullptr is a clear indication that
  // object was destructed. There was a bug in usrsctp when it
  // invoked OnSctpOutboundPacket callback for destructed SctpTransport,
  // which caused obscure SIGSEGV on access to these fields,
  // having this fields set to nullptr will make it easier to understand
  // that SctpTransport was destructed and "use-after-free" bug happen.
  // SIGSEGV error triggered on dereference these pointers will also
  // be easier to understand due to 0x0 address. All of this assumes
  // that ASAN is not enabled to detect "use-after-free", which is
  // currently default configuration.
  network_thread_ = nullptr;
  transport_ = nullptr;
}

void SctpTransport::SetDtlsTransport(rtc::PacketTransportInternal* transport) {
  RTC_DCHECK_RUN_ON(network_thread_);
  DisconnectTransportSignals();
  transport_ = transport;
  ConnectTransportSignals();
  if (!was_ever_writable_ && transport && transport->writable()) {
    was_ever_writable_ = true;
    // New transport is writable, now we can start the SCTP connection if Start
    // was called already.
    if (started_) {
      RTC_DCHECK(!sock_);
      Connect();
    }
  }
}

bool SctpTransport::Start(int local_sctp_port,
                          int remote_sctp_port,
                          int max_message_size) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (local_sctp_port == -1) {
    local_sctp_port = kSctpDefaultPort;
  }
  if (remote_sctp_port == -1) {
    remote_sctp_port = kSctpDefaultPort;
  }
  if (max_message_size > kSctpSendBufferSize) {
    RTC_LOG(LS_ERROR) << "Max message size of " << max_message_size
                      << " is larger than send bufffer size "
                      << kSctpSendBufferSize;
    return false;
  }
  if (max_message_size < 1) {
    RTC_LOG(LS_ERROR) << "Max message size of " << max_message_size
                      << " is too small";
    return false;
  }
  // We allow changing max_message_size with a second Start() call,
  // but not changing the port numbers.
  max_message_size_ = max_message_size;
  if (started_) {
    if (local_sctp_port != local_port_ || remote_sctp_port != remote_port_) {
      RTC_LOG(LS_ERROR)
          << "Can't change SCTP port after SCTP association formed.";
      return false;
    }
    return true;
  }
  local_port_ = local_sctp_port;
  remote_port_ = remote_sctp_port;
  started_ = true;
  RTC_DCHECK(!sock_);
  // Only try to connect if the DTLS transport has been writable before
  // (indicating that the DTLS handshake is complete).
  if (was_ever_writable_) {
    return Connect();
  }
  return true;
}

bool SctpTransport::OpenStream(int sid) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (sid > kMaxSctpSid) {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->OpenStream(...): "
                           "Not adding data stream "
                           "with sid="
                        << sid << " because sid is too high.";
    return false;
  }
  auto it = stream_status_by_sid_.find(sid);
  if (it == stream_status_by_sid_.end()) {
    stream_status_by_sid_[sid] = StreamStatus();
    return true;
  }
  if (it->second.is_open()) {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->OpenStream(...): "
                           "Not adding data stream "
                           "with sid="
                        << sid << " because stream is already open.";
    return false;
  } else {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->OpenStream(...): "
                           "Not adding data stream "
                           " with sid="
                        << sid << " because stream is still closing.";
    return false;
  }
}

bool SctpTransport::ResetStream(int sid) {
  RTC_DCHECK_RUN_ON(network_thread_);

  auto it = stream_status_by_sid_.find(sid);
  if (it == stream_status_by_sid_.end() || !it->second.is_open()) {
    RTC_LOG(LS_WARNING) << debug_name_ << "->ResetStream(" << sid
                        << "): stream not open.";
    return false;
  }

  RTC_LOG(LS_VERBOSE) << debug_name_ << "->ResetStream(" << sid
                      << "): "
                         "Queuing RE-CONFIG chunk.";
  it->second.closure_initiated = true;

  // Signal our stream-reset logic that it should try to send now, if it can.
  SendQueuedStreamResets();

  // The stream will actually get removed when we get the acknowledgment.
  return true;
}

bool SctpTransport::SendData(const SendDataParams& params,
                             const rtc::CopyOnWriteBuffer& payload,
                             SendDataResult* result) {
  RTC_DCHECK_RUN_ON(network_thread_);

  if (partial_outgoing_message_.has_value()) {
    if (result) {
      *result = SDR_BLOCK;
    }
    // Ready to send should get set only when SendData() call gets blocked.
    ready_to_send_data_ = false;
    return false;
  }
  size_t payload_size = payload.size();
  OutgoingMessage message(payload, params);
  SendDataResult send_message_result = SendMessageInternal(&message);
  if (result) {
    *result = send_message_result;
  }
  if (payload_size == message.size()) {
    // Nothing was sent.
    return false;
  }
  // If any data is sent, we accept the message. In the case that data was
  // partially accepted by the sctp library, the remaining is buffered. This
  // ensures the client does not resend the message.
  RTC_DCHECK_LT(message.size(), payload_size);
  if (message.size() > 0) {
    RTC_DCHECK(!partial_outgoing_message_.has_value());
    RTC_DLOG(LS_VERBOSE) << "Partially sent message. Buffering the remaining"
                         << message.size() << "/" << payload_size << " bytes.";

    partial_outgoing_message_.emplace(message);
  }
  return true;
}

SendDataResult SctpTransport::SendMessageInternal(OutgoingMessage* message) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!sock_) {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->SendMessageInternal(...): "
                           "Not sending packet with sid="
                        << message->send_params().sid
                        << " len=" << message->size() << " before Start().";
    return SDR_ERROR;
  }
  if (message->send_params().type != DMT_CONTROL) {
    auto it = stream_status_by_sid_.find(message->send_params().sid);
    if (it == stream_status_by_sid_.end() || !it->second.is_open()) {
      RTC_LOG(LS_WARNING)
          << debug_name_
          << "->SendMessageInternal(...): "
             "Not sending data because sid is unknown or closing: "
          << message->send_params().sid;
      return SDR_ERROR;
    }
  }
  if (message->size() > static_cast<size_t>(max_message_size_)) {
    RTC_LOG(LS_ERROR) << "Attempting to send message of size "
                      << message->size() << " which is larger than limit "
                      << max_message_size_;
    return SDR_ERROR;
  }

  // Send data using SCTP.
  sctp_sendv_spa spa = CreateSctpSendParams(message->send_params());
  // Note: this send call is not atomic because the EOR bit is set. This means
  // that usrsctp can partially accept this message and it is our duty to buffer
  // the rest.
  ssize_t send_res = usrsctp_sendv(
      sock_, message->data(), message->size(), NULL, 0, &spa,
      rtc::checked_cast<socklen_t>(sizeof(spa)), SCTP_SENDV_SPA, 0);
  if (send_res < 0) {
    if (errno == SCTP_EWOULDBLOCK) {
      ready_to_send_data_ = false;
      RTC_LOG(LS_INFO) << debug_name_
                       << "->SendMessageInternal(...): EWOULDBLOCK returned";
      return SDR_BLOCK;
    }

    RTC_LOG_ERRNO(LS_ERROR) << "ERROR:" << debug_name_
                            << "->SendMessageInternal(...): "
                               " usrsctp_sendv: ";
    return SDR_ERROR;
  }

  size_t amount_sent = static_cast<size_t>(send_res);
  RTC_DCHECK_LE(amount_sent, message->size());
  message->Advance(amount_sent);
  // Only way out now is success.
  return SDR_SUCCESS;
}

bool SctpTransport::ReadyToSendData() {
  RTC_DCHECK_RUN_ON(network_thread_);
  return ready_to_send_data_;
}

void SctpTransport::ConnectTransportSignals() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!transport_) {
    return;
  }
  transport_->SignalWritableState.connect(this,
                                          &SctpTransport::OnWritableState);
  transport_->SignalReadPacket.connect(this, &SctpTransport::OnPacketRead);
  transport_->SignalClosed.connect(this, &SctpTransport::OnClosed);
}

void SctpTransport::DisconnectTransportSignals() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!transport_) {
    return;
  }
  transport_->SignalWritableState.disconnect(this);
  transport_->SignalReadPacket.disconnect(this);
  transport_->SignalClosed.disconnect(this);
}

bool SctpTransport::Connect() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_LOG(LS_VERBOSE) << debug_name_ << "->Connect().";

  // If we already have a socket connection (which shouldn't ever happen), just
  // return.
  RTC_DCHECK(!sock_);
  if (sock_) {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->Connect(): Ignored as socket "
                         "is already established.";
    return true;
  }

  // If no socket (it was closed) try to start it again. This can happen when
  // the socket we are connecting to closes, does an sctp shutdown handshake,
  // or behaves unexpectedly causing us to perform a CloseSctpSocket.
  if (!OpenSctpSocket()) {
    return false;
  }

  // Note: conversion from int to uint16_t happens on assignment.
  sockaddr_conn local_sconn = GetSctpSockAddr(local_port_);
  if (usrsctp_bind(sock_, reinterpret_cast<sockaddr*>(&local_sconn),
                   sizeof(local_sconn)) < 0) {
    RTC_LOG_ERRNO(LS_ERROR)
        << debug_name_ << "->Connect(): " << ("Failed usrsctp_bind");
    CloseSctpSocket();
    return false;
  }

  // Note: conversion from int to uint16_t happens on assignment.
  sockaddr_conn remote_sconn = GetSctpSockAddr(remote_port_);
  int connect_result = usrsctp_connect(
      sock_, reinterpret_cast<sockaddr*>(&remote_sconn), sizeof(remote_sconn));
  if (connect_result < 0 && errno != SCTP_EINPROGRESS) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->Connect(): "
                               "Failed usrsctp_connect. got errno="
                            << errno << ", but wanted " << SCTP_EINPROGRESS;
    CloseSctpSocket();
    return false;
  }
  // Set the MTU and disable MTU discovery.
  // We can only do this after usrsctp_connect or it has no effect.
  sctp_paddrparams params = {};
  memcpy(&params.spp_address, &remote_sconn, sizeof(remote_sconn));
  params.spp_flags = SPP_PMTUD_DISABLE;
  // The MTU value provided specifies the space available for chunks in the
  // packet, so we subtract the SCTP header size.
  params.spp_pathmtu = kSctpMtu - sizeof(struct sctp_common_header);
  if (usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_PEER_ADDR_PARAMS, &params,
                         sizeof(params))) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->Connect(): "
                               "Failed to set SCTP_PEER_ADDR_PARAMS.";
  }
  // Since this is a fresh SCTP association, we'll always start out with empty
  // queues, so "ReadyToSendData" should be true.
  SetReadyToSendData();
  return true;
}

bool SctpTransport::OpenSctpSocket() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (sock_) {
    RTC_LOG(LS_WARNING) << debug_name_
                        << "->OpenSctpSocket(): "
                           "Ignoring attempt to re-create existing socket.";
    return false;
  }

  UsrSctpWrapper::IncrementUsrSctpUsageCount();

  // If kSctpSendBufferSize isn't reflective of reality, we log an error, but we
  // still have to do something reasonable here.  Look up what the buffer's real
  // size is and set our threshold to something reasonable.
  // TODO(bugs.webrtc.org/11824): That was previously set to 50%, not 25%, but
  // it was reduced to a recent usrsctp regression. Can return to 50% when the
  // root cause is fixed.
  static const int kSendThreshold = usrsctp_sysctl_get_sctp_sendspace() / 4;

  sock_ = usrsctp_socket(
      AF_CONN, SOCK_STREAM, IPPROTO_SCTP, &UsrSctpWrapper::OnSctpInboundPacket,
      &UsrSctpWrapper::SendThresholdCallback, kSendThreshold, this);
  if (!sock_) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->OpenSctpSocket(): "
                               "Failed to create SCTP socket.";
    UsrSctpWrapper::DecrementUsrSctpUsageCount();
    return false;
  }

  if (!ConfigureSctpSocket()) {
    usrsctp_close(sock_);
    sock_ = nullptr;
    UsrSctpWrapper::DecrementUsrSctpUsageCount();
    return false;
  }
  id_ = g_transport_map_->Register(this);
  // Register our id as an address for usrsctp. This is used by SCTP to
  // direct the packets received (by the created socket) to this class.
  usrsctp_register_address(reinterpret_cast<void*>(id_));
  return true;
}

bool SctpTransport::ConfigureSctpSocket() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(sock_);
  // Make the socket non-blocking. Connect, close, shutdown etc will not block
  // the thread waiting for the socket operation to complete.
  if (usrsctp_set_non_blocking(sock_, 1) < 0) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->ConfigureSctpSocket(): "
                               "Failed to set SCTP to non blocking.";
    return false;
  }

  // This ensures that the usrsctp close call deletes the association. This
  // prevents usrsctp from calling OnSctpOutboundPacket with references to
  // this class as the address.
  linger linger_opt;
  linger_opt.l_onoff = 1;
  linger_opt.l_linger = 0;
  if (usrsctp_setsockopt(sock_, SOL_SOCKET, SO_LINGER, &linger_opt,
                         sizeof(linger_opt))) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->ConfigureSctpSocket(): "
                               "Failed to set SO_LINGER.";
    return false;
  }

  // Enable stream ID resets.
  struct sctp_assoc_value stream_rst;
  stream_rst.assoc_id = SCTP_ALL_ASSOC;
  stream_rst.assoc_value = 1;
  if (usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_ENABLE_STREAM_RESET,
                         &stream_rst, sizeof(stream_rst))) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->ConfigureSctpSocket(): "
                               "Failed to set SCTP_ENABLE_STREAM_RESET.";
    return false;
  }

  // Nagle.
  uint32_t nodelay = 1;
  if (usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_NODELAY, &nodelay,
                         sizeof(nodelay))) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->ConfigureSctpSocket(): "
                               "Failed to set SCTP_NODELAY.";
    return false;
  }

  // Explicit EOR.
  uint32_t eor = 1;
  if (usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_EXPLICIT_EOR, &eor,
                         sizeof(eor))) {
    RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                            << "->ConfigureSctpSocket(): "
                               "Failed to set SCTP_EXPLICIT_EOR.";
    return false;
  }

  // Subscribe to SCTP event notifications.
  // TODO(crbug.com/1137936): Subscribe to SCTP_SEND_FAILED_EVENT once deadlock
  // is fixed upstream, or we switch to the upcall API:
  // https://github.com/sctplab/usrsctp/issues/537
  int event_types[] = {SCTP_ASSOC_CHANGE, SCTP_PEER_ADDR_CHANGE,
                       SCTP_SENDER_DRY_EVENT, SCTP_STREAM_RESET_EVENT};
  struct sctp_event event = {0};
  event.se_assoc_id = SCTP_ALL_ASSOC;
  event.se_on = 1;
  for (size_t i = 0; i < arraysize(event_types); i++) {
    event.se_type = event_types[i];
    if (usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_EVENT, &event,
                           sizeof(event)) < 0) {
      RTC_LOG_ERRNO(LS_ERROR) << debug_name_
                              << "->ConfigureSctpSocket(): "
                                 "Failed to set SCTP_EVENT type: "
                              << event.se_type;
      return false;
    }
  }
  return true;
}

void SctpTransport::CloseSctpSocket() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (sock_) {
    // We assume that SO_LINGER option is set to close the association when
    // close is called. This means that any pending packets in usrsctp will be
    // discarded instead of being sent.
    usrsctp_close(sock_);
    sock_ = nullptr;
    usrsctp_deregister_address(reinterpret_cast<void*>(id_));
    RTC_CHECK(g_transport_map_->Deregister(id_));
    UsrSctpWrapper::DecrementUsrSctpUsageCount();
    ready_to_send_data_ = false;
  }
}

bool SctpTransport::SendQueuedStreamResets() {
  RTC_DCHECK_RUN_ON(network_thread_);

  // Figure out how many streams need to be reset. We need to do this so we can
  // allocate the right amount of memory for the sctp_reset_streams structure.
  size_t num_streams = absl::c_count_if(
      stream_status_by_sid_,
      [](const std::map<uint32_t, StreamStatus>::value_type& stream) {
        return stream.second.need_outgoing_reset();
      });
  if (num_streams == 0) {
    // Nothing to reset.
    return true;
  }

  RTC_LOG(LS_VERBOSE) << "SendQueuedStreamResets[" << debug_name_
                      << "]: Resetting " << num_streams << " outgoing streams.";

  const size_t num_bytes =
      sizeof(struct sctp_reset_streams) + (num_streams * sizeof(uint16_t));
  std::vector<uint8_t> reset_stream_buf(num_bytes, 0);
  struct sctp_reset_streams* resetp =
      reinterpret_cast<sctp_reset_streams*>(&reset_stream_buf[0]);
  resetp->srs_assoc_id = SCTP_ALL_ASSOC;
  resetp->srs_flags = SCTP_STREAM_RESET_OUTGOING;
  resetp->srs_number_streams = rtc::checked_cast<uint16_t>(num_streams);
  int result_idx = 0;

  for (const std::map<uint32_t, StreamStatus>::value_type& stream :
       stream_status_by_sid_) {
    if (!stream.second.need_outgoing_reset()) {
      continue;
    }
    resetp->srs_stream_list[result_idx++] = stream.first;
  }

  int ret =
      usrsctp_setsockopt(sock_, IPPROTO_SCTP, SCTP_RESET_STREAMS, resetp,
                         rtc::checked_cast<socklen_t>(reset_stream_buf.size()));
  if (ret < 0) {
    // Note that usrsctp only lets us have one reset in progress at a time
    // (even though multiple streams can be reset at once). If this happens,
    // SendQueuedStreamResets will end up called after the current in-progress
    // reset finishes, in OnStreamResetEvent.
    RTC_LOG_ERRNO(LS_WARNING) << debug_name_
                              << "->SendQueuedStreamResets(): "
                                 "Failed to send a stream reset for "
                              << num_streams << " streams";
    return false;
  }

  // Since the usrsctp call completed successfully, update our stream status
  // map to note that we started the outgoing reset.
  for (auto it = stream_status_by_sid_.begin();
       it != stream_status_by_sid_.end(); ++it) {
    if (it->second.need_outgoing_reset()) {
      it->second.outgoing_reset_initiated = true;
    }
  }
  return true;
}

void SctpTransport::SetReadyToSendData() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!ready_to_send_data_) {
    ready_to_send_data_ = true;
    SignalReadyToSendData();
  }
}

bool SctpTransport::SendBufferedMessage() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(partial_outgoing_message_.has_value());
  RTC_DLOG(LS_VERBOSE) << "Sending partially buffered message of size "
                       << partial_outgoing_message_->size() << ".";

  SendMessageInternal(&partial_outgoing_message_.value());
  if (partial_outgoing_message_->size() > 0) {
    // Still need to finish sending the message.
    return false;
  }
  RTC_DCHECK_EQ(0u, partial_outgoing_message_->size());
  partial_outgoing_message_.reset();
  return true;
}

void SctpTransport::OnWritableState(rtc::PacketTransportInternal* transport) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK_EQ(transport_, transport);
  if (!was_ever_writable_ && transport->writable()) {
    was_ever_writable_ = true;
    if (started_) {
      Connect();
    }
  }
}

// Called by network interface when a packet has been received.
void SctpTransport::OnPacketRead(rtc::PacketTransportInternal* transport,
                                 const char* data,
                                 size_t len,
                                 const int64_t& /* packet_time_us */,
                                 int flags) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK_EQ(transport_, transport);
  TRACE_EVENT0("webrtc", "SctpTransport::OnPacketRead");

  if (flags & PF_SRTP_BYPASS) {
    // We are only interested in SCTP packets.
    return;
  }

  RTC_LOG(LS_VERBOSE) << debug_name_
                      << "->OnPacketRead(...): "
                         " length="
                      << len << ", started: " << started_;
  // Only give receiving packets to usrsctp after if connected. This enables two
  // peers to each make a connect call, but for them not to receive an INIT
  // packet before they have called connect; least the last receiver of the INIT
  // packet will have called connect, and a connection will be established.
  if (sock_) {
    // Pass received packet to SCTP stack. Once processed by usrsctp, the data
    // will be will be given to the global OnSctpInboundData, and then,
    // marshalled by the AsyncInvoker.
    VerboseLogPacket(data, len, SCTP_DUMP_INBOUND);
    usrsctp_conninput(reinterpret_cast<void*>(id_), data, len, 0);
  } else {
    // TODO(ldixon): Consider caching the packet for very slightly better
    // reliability.
  }
}

void SctpTransport::OnClosed(rtc::PacketTransportInternal* transport) {
  SignalClosedAbruptly();
}

void SctpTransport::OnSendThresholdCallback() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (partial_outgoing_message_.has_value()) {
    if (!SendBufferedMessage()) {
      // Did not finish sending the buffered message.
      return;
    }
  }
  SetReadyToSendData();
}

sockaddr_conn SctpTransport::GetSctpSockAddr(int port) {
  sockaddr_conn sconn = {0};
  sconn.sconn_family = AF_CONN;
#ifdef HAVE_SCONN_LEN
  sconn.sconn_len = sizeof(sockaddr_conn);
#endif
  // Note: conversion from int to uint16_t happens here.
  sconn.sconn_port = rtc::HostToNetwork16(port);
  sconn.sconn_addr = reinterpret_cast<void*>(id_);
  return sconn;
}

void SctpTransport::OnPacketFromSctpToNetwork(
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (buffer.size() > (kSctpMtu)) {
    RTC_LOG(LS_ERROR) << debug_name_
                      << "->OnPacketFromSctpToNetwork(...): "
                         "SCTP seems to have made a packet that is bigger "
                         "than its official MTU: "
                      << buffer.size() << " vs max of " << kSctpMtu;
  }
  TRACE_EVENT0("webrtc", "SctpTransport::OnPacketFromSctpToNetwork");

  // Don't create noise by trying to send a packet when the DTLS transport isn't
  // even writable.
  if (!transport_ || !transport_->writable()) {
    return;
  }

  // Bon voyage.
  transport_->SendPacket(buffer.data<char>(), buffer.size(),
                         rtc::PacketOptions(), PF_NORMAL);
}

int SctpTransport::InjectDataOrNotificationFromSctpForTesting(
    void* data,
    size_t length,
    struct sctp_rcvinfo rcv,
    int flags) {
  return OnDataOrNotificationFromSctp(data, length, rcv, flags);
}

int SctpTransport::OnDataOrNotificationFromSctp(void* data,
                                                size_t length,
                                                struct sctp_rcvinfo rcv,
                                                int flags) {
  // If data is NULL, the SCTP association has been closed.
  if (!data) {
    RTC_LOG(LS_INFO) << debug_name_
                     << "->OnDataOrNotificationFromSctp(...): "
                        "No data; association closed.";
    return kSctpSuccessReturn;
  }

  // Handle notifications early.
  // Note: Notifications are never split into chunks, so they can and should
  //       be handled early and entirely separate from the reassembly
  //       process.
  if (flags & MSG_NOTIFICATION) {
    RTC_LOG(LS_VERBOSE)
        << debug_name_
        << "->OnDataOrNotificationFromSctp(...): SCTP notification"
        << " length=" << length;

    // Copy and dispatch asynchronously
    rtc::CopyOnWriteBuffer notification(reinterpret_cast<uint8_t*>(data),
                                        length);
    invoker_.AsyncInvoke<void>(
        RTC_FROM_HERE, network_thread_,
        rtc::Bind(&SctpTransport::OnNotificationFromSctp, this, notification));
    return kSctpSuccessReturn;
  }

  // Log data chunk
  const uint32_t ppid = rtc::NetworkToHost32(rcv.rcv_ppid);
  RTC_LOG(LS_VERBOSE) << debug_name_
                      << "->OnDataOrNotificationFromSctp(...): SCTP data chunk"
                      << " length=" << length << ", sid=" << rcv.rcv_sid
                      << ", ppid=" << ppid << ", ssn=" << rcv.rcv_ssn
                      << ", cum-tsn=" << rcv.rcv_cumtsn
                      << ", eor=" << ((flags & MSG_EOR) ? "y" : "n");

  // Validate payload protocol identifier
  DataMessageType type = DMT_NONE;
  if (!GetDataMediaType(ppid, &type)) {
    // Unexpected PPID, dropping
    RTC_LOG(LS_ERROR) << "Received an unknown PPID " << ppid
                      << " on an SCTP packet.  Dropping.";
    return kSctpSuccessReturn;
  }

  // Expect only continuation messages belonging to the same SID. The SCTP
  // stack is expected to ensure this as long as the User Message
  // Interleaving extension (RFC 8260) is not explicitly enabled, so this
  // merely acts as a safeguard.
  if ((partial_incoming_message_.size() != 0) &&
      (rcv.rcv_sid != partial_params_.sid)) {
    RTC_LOG(LS_ERROR) << "Received a new SID without EOR in the previous"
                      << " SCTP packet. Discarding the previous packet.";
    partial_incoming_message_.Clear();
  }

  // Copy metadata of interest
  ReceiveDataParams params;
  params.type = type;
  params.sid = rcv.rcv_sid;
  // Note that the SSN is identical for each chunk of the same message.
  // Furthermore, it is increased per stream and not on the whole
  // association.
  params.seq_num = rcv.rcv_ssn;
  // There is no timestamp field in the SCTP API
  params.timestamp = 0;

  // Append the chunk's data to the message buffer
  partial_incoming_message_.AppendData(reinterpret_cast<uint8_t*>(data),
                                       length);
  partial_params_ = params;
  partial_flags_ = flags;

  // If the message is not yet complete...
  if (!(flags & MSG_EOR)) {
    if (partial_incoming_message_.size() < kSctpSendBufferSize) {
      // We still have space in the buffer. Continue buffering chunks until
      // the message is complete before handing it out.
      return kSctpSuccessReturn;
    } else {
      // The sender is exceeding the maximum message size that we announced.
      // Spit out a warning but still hand out the partial message. Note that
      // this behaviour is undesirable, see the discussion in issue 7774.
      //
      // TODO(lgrahl): Once sufficient time has passed and all supported
      // browser versions obey the announced maximum message size, we should
      // abort the SCTP association instead to prevent message integrity
      // violation.
      RTC_LOG(LS_ERROR) << "Handing out partial SCTP message.";
    }
  }

  // Dispatch the complete message.
  // The ownership of the packet transfers to |invoker_|. Using
  // CopyOnWriteBuffer is the most convenient way to do this.
  invoker_.AsyncInvoke<void>(
      RTC_FROM_HERE, network_thread_,
      rtc::Bind(&SctpTransport::OnDataFromSctpToTransport, this, params,
                partial_incoming_message_));

  // Reset the message buffer
  partial_incoming_message_.Clear();
  return kSctpSuccessReturn;
}

void SctpTransport::OnDataFromSctpToTransport(
    const ReceiveDataParams& params,
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_LOG(LS_VERBOSE) << debug_name_
                      << "->OnDataFromSctpToTransport(...): "
                         "Posting with length: "
                      << buffer.size() << " on stream " << params.sid;
  // Reports all received messages to upper layers, no matter whether the sid
  // is known.
  SignalDataReceived(params, buffer);
}

void SctpTransport::OnNotificationFromSctp(
    const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (buffer.size() < sizeof(sctp_notification::sn_header)) {
    RTC_LOG(LS_ERROR) << "SCTP notification is shorter than header size: "
                      << buffer.size();
    return;
  }

  const sctp_notification& notification =
      reinterpret_cast<const sctp_notification&>(*buffer.data());
  if (buffer.size() != notification.sn_header.sn_length) {
    RTC_LOG(LS_ERROR) << "SCTP notification length (" << buffer.size()
                      << ") does not match sn_length field ("
                      << notification.sn_header.sn_length << ").";
    return;
  }

  // TODO(ldixon): handle notifications appropriately.
  switch (notification.sn_header.sn_type) {
    case SCTP_ASSOC_CHANGE:
      RTC_LOG(LS_VERBOSE) << "SCTP_ASSOC_CHANGE";
      if (buffer.size() < sizeof(notification.sn_assoc_change)) {
        RTC_LOG(LS_ERROR)
            << "SCTP_ASSOC_CHANGE notification has less than required length: "
            << buffer.size();
        return;
      }
      OnNotificationAssocChange(notification.sn_assoc_change);
      break;
    case SCTP_REMOTE_ERROR:
      RTC_LOG(LS_INFO) << "SCTP_REMOTE_ERROR";
      break;
    case SCTP_SHUTDOWN_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_SHUTDOWN_EVENT";
      break;
    case SCTP_ADAPTATION_INDICATION:
      RTC_LOG(LS_INFO) << "SCTP_ADAPTATION_INDICATION";
      break;
    case SCTP_PARTIAL_DELIVERY_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_PARTIAL_DELIVERY_EVENT";
      break;
    case SCTP_AUTHENTICATION_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_AUTHENTICATION_EVENT";
      break;
    case SCTP_SENDER_DRY_EVENT:
      RTC_LOG(LS_VERBOSE) << "SCTP_SENDER_DRY_EVENT";
      SetReadyToSendData();
      break;
    // TODO(ldixon): Unblock after congestion.
    case SCTP_NOTIFICATIONS_STOPPED_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_NOTIFICATIONS_STOPPED_EVENT";
      break;
    case SCTP_SEND_FAILED_EVENT: {
      if (buffer.size() < sizeof(notification.sn_send_failed_event)) {
        RTC_LOG(LS_ERROR) << "SCTP_SEND_FAILED_EVENT notification has less "
                             "than required length: "
                          << buffer.size();
        return;
      }
      const struct sctp_send_failed_event& ssfe =
          notification.sn_send_failed_event;
      RTC_LOG(LS_WARNING) << "SCTP_SEND_FAILED_EVENT: message with"
                             " PPID = "
                          << rtc::NetworkToHost32(ssfe.ssfe_info.snd_ppid)
                          << " SID = " << ssfe.ssfe_info.snd_sid
                          << " flags = " << rtc::ToHex(ssfe.ssfe_info.snd_flags)
                          << " failed to sent due to error = "
                          << rtc::ToHex(ssfe.ssfe_error);
      break;
    }
    case SCTP_STREAM_RESET_EVENT:
      if (buffer.size() < sizeof(notification.sn_strreset_event)) {
        RTC_LOG(LS_ERROR) << "SCTP_STREAM_RESET_EVENT notification has less "
                             "than required length: "
                          << buffer.size();
        return;
      }
      OnStreamResetEvent(&notification.sn_strreset_event);
      break;
    case SCTP_ASSOC_RESET_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_ASSOC_RESET_EVENT";
      break;
    case SCTP_STREAM_CHANGE_EVENT:
      RTC_LOG(LS_INFO) << "SCTP_STREAM_CHANGE_EVENT";
      // An acknowledgment we get after our stream resets have gone through,
      // if they've failed.  We log the message, but don't react -- we don't
      // keep around the last-transmitted set of SSIDs we wanted to close for
      // error recovery.  It doesn't seem likely to occur, and if so, likely
      // harmless within the lifetime of a single SCTP association.
      break;
    case SCTP_PEER_ADDR_CHANGE:
      RTC_LOG(LS_INFO) << "SCTP_PEER_ADDR_CHANGE";
      break;
    default:
      RTC_LOG(LS_WARNING) << "Unknown SCTP event: "
                          << notification.sn_header.sn_type;
      break;
  }
}

void SctpTransport::OnNotificationAssocChange(const sctp_assoc_change& change) {
  RTC_DCHECK_RUN_ON(network_thread_);
  switch (change.sac_state) {
    case SCTP_COMM_UP:
      RTC_LOG(LS_VERBOSE) << "Association change SCTP_COMM_UP, stream # is "
                          << change.sac_outbound_streams << " outbound, "
                          << change.sac_inbound_streams << " inbound.";
      max_outbound_streams_ = change.sac_outbound_streams;
      max_inbound_streams_ = change.sac_inbound_streams;
      SignalAssociationChangeCommunicationUp();
      // In case someone tried to close a stream before communication
      // came up, send any queued resets.
      SendQueuedStreamResets();
      break;
    case SCTP_COMM_LOST:
      RTC_LOG(LS_INFO) << "Association change SCTP_COMM_LOST";
      break;
    case SCTP_RESTART:
      RTC_LOG(LS_INFO) << "Association change SCTP_RESTART";
      break;
    case SCTP_SHUTDOWN_COMP:
      RTC_LOG(LS_INFO) << "Association change SCTP_SHUTDOWN_COMP";
      break;
    case SCTP_CANT_STR_ASSOC:
      RTC_LOG(LS_INFO) << "Association change SCTP_CANT_STR_ASSOC";
      break;
    default:
      RTC_LOG(LS_INFO) << "Association change UNKNOWN";
      break;
  }
}

void SctpTransport::OnStreamResetEvent(
    const struct sctp_stream_reset_event* evt) {
  RTC_DCHECK_RUN_ON(network_thread_);

  // This callback indicates that a reset is complete for incoming and/or
  // outgoing streams. The reset may have been initiated by us or the remote
  // side.
  const int num_sids = (evt->strreset_length - sizeof(*evt)) /
                       sizeof(evt->strreset_stream_list[0]);

  if (evt->strreset_flags & SCTP_STREAM_RESET_FAILED) {
    // OK, just try sending any previously sent stream resets again. The stream
    // IDs sent over when the RESET_FIALED flag is set seem to be garbage
    // values. Ignore them.
    for (std::map<uint32_t, StreamStatus>::value_type& stream :
         stream_status_by_sid_) {
      stream.second.outgoing_reset_initiated = false;
    }
    SendQueuedStreamResets();
    // TODO(deadbeef): If this happens, the entire SCTP association is in quite
    // crippled state. The SCTP session should be dismantled, and the WebRTC
    // connectivity errored because is clear that the distant party is not
    // playing ball: malforms the transported data.
    return;
  }

  // Loop over the received events and properly update the StreamStatus map.
  for (int i = 0; i < num_sids; i++) {
    const uint32_t sid = evt->strreset_stream_list[i];
    auto it = stream_status_by_sid_.find(sid);
    if (it == stream_status_by_sid_.end()) {
      // This stream is unknown. Sometimes this can be from a
      // RESET_FAILED-related retransmit.
      RTC_LOG(LS_VERBOSE) << "SCTP_STREAM_RESET_EVENT(" << debug_name_
                          << "): Unknown sid " << sid;
      continue;
    }
    StreamStatus& status = it->second;

    if (evt->strreset_flags & SCTP_STREAM_RESET_INCOMING_SSN) {
      RTC_LOG(LS_VERBOSE) << "SCTP_STREAM_RESET_INCOMING_SSN(" << debug_name_
                          << "): sid " << sid;
      status.incoming_reset_complete = true;
      // If we receive an incoming stream reset and we haven't started the
      // closing procedure ourselves, this means the remote side started the
      // closing procedure; fire a signal so that the relevant data channel
      // can change to "closing" (we still need to reset the outgoing stream
      // before it changes to "closed").
      if (!status.closure_initiated) {
        SignalClosingProcedureStartedRemotely(sid);
      }
    }
    if (evt->strreset_flags & SCTP_STREAM_RESET_OUTGOING_SSN) {
      RTC_LOG(LS_VERBOSE) << "SCTP_STREAM_RESET_OUTGOING_SSN(" << debug_name_
                          << "): sid " << sid;
      status.outgoing_reset_complete = true;
    }

    // If this reset completes the closing procedure, remove the stream from
    // our map so we can consider it closed, and fire a signal such that the
    // relevant DataChannel will change its state to "closed" and its ID can be
    // re-used.
    if (status.reset_complete()) {
      stream_status_by_sid_.erase(it);
      SignalClosingProcedureComplete(sid);
    }
  }

  // Always try to send any queued resets because this call indicates that the
  // last outgoing or incoming reset has made some progress.
  SendQueuedStreamResets();
}

}  // namespace cricket
