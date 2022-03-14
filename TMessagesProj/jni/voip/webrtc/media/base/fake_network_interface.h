/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_FAKE_NETWORK_INTERFACE_H_
#define MEDIA_BASE_FAKE_NETWORK_INTERFACE_H_

#include <map>
#include <set>
#include <vector>

#include "media/base/media_channel.h"
#include "media/base/rtp_utils.h"
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/dscp.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread.h"

namespace cricket {

// Fake NetworkInterface that sends/receives RTP/RTCP packets.
class FakeNetworkInterface : public MediaChannel::NetworkInterface,
                             public rtc::MessageHandlerAutoCleanup {
 public:
  FakeNetworkInterface()
      : thread_(rtc::Thread::Current()),
        dest_(NULL),
        conf_(false),
        sendbuf_size_(-1),
        recvbuf_size_(-1),
        dscp_(rtc::DSCP_NO_CHANGE) {}

  void SetDestination(MediaChannel* dest) { dest_ = dest; }

  // Conference mode is a mode where instead of simply forwarding the packets,
  // the transport will send multiple copies of the packet with the specified
  // SSRCs. This allows us to simulate receiving media from multiple sources.
  void SetConferenceMode(bool conf, const std::vector<uint32_t>& ssrcs)
      RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    conf_ = conf;
    conf_sent_ssrcs_ = ssrcs;
  }

  int NumRtpBytes() RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    int bytes = 0;
    for (size_t i = 0; i < rtp_packets_.size(); ++i) {
      bytes += static_cast<int>(rtp_packets_[i].size());
    }
    return bytes;
  }

  int NumRtpBytes(uint32_t ssrc) RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    int bytes = 0;
    GetNumRtpBytesAndPackets(ssrc, &bytes, NULL);
    return bytes;
  }

  int NumRtpPackets() RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    return static_cast<int>(rtp_packets_.size());
  }

  int NumRtpPackets(uint32_t ssrc) RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    int packets = 0;
    GetNumRtpBytesAndPackets(ssrc, NULL, &packets);
    return packets;
  }

  int NumSentSsrcs() RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    return static_cast<int>(sent_ssrcs_.size());
  }

  rtc::CopyOnWriteBuffer GetRtpPacket(int index) RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    if (index >= static_cast<int>(rtp_packets_.size())) {
      return {};
    }
    return rtp_packets_[index];
  }

  int NumRtcpPackets() RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    return static_cast<int>(rtcp_packets_.size());
  }

  // Note: callers are responsible for deleting the returned buffer.
  const rtc::CopyOnWriteBuffer* GetRtcpPacket(int index)
      RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    if (index >= static_cast<int>(rtcp_packets_.size())) {
      return NULL;
    }
    return new rtc::CopyOnWriteBuffer(rtcp_packets_[index]);
  }

  int sendbuf_size() const { return sendbuf_size_; }
  int recvbuf_size() const { return recvbuf_size_; }
  rtc::DiffServCodePoint dscp() const { return dscp_; }
  rtc::PacketOptions options() const { return options_; }

 protected:
  virtual bool SendPacket(rtc::CopyOnWriteBuffer* packet,
                          const rtc::PacketOptions& options)
      RTC_LOCKS_EXCLUDED(mutex_) {
    if (!webrtc::IsRtpPacket(*packet)) {
      return false;
    }

    webrtc::MutexLock lock(&mutex_);
    sent_ssrcs_[webrtc::ParseRtpSsrc(*packet)]++;
    options_ = options;

    rtp_packets_.push_back(*packet);
    if (conf_) {
      for (size_t i = 0; i < conf_sent_ssrcs_.size(); ++i) {
        SetRtpSsrc(conf_sent_ssrcs_[i], *packet);
        PostMessage(ST_RTP, *packet);
      }
    } else {
      PostMessage(ST_RTP, *packet);
    }
    return true;
  }

  virtual bool SendRtcp(rtc::CopyOnWriteBuffer* packet,
                        const rtc::PacketOptions& options)
      RTC_LOCKS_EXCLUDED(mutex_) {
    webrtc::MutexLock lock(&mutex_);
    rtcp_packets_.push_back(*packet);
    options_ = options;
    if (!conf_) {
      // don't worry about RTCP in conf mode for now
      PostMessage(ST_RTCP, *packet);
    }
    return true;
  }

  virtual int SetOption(SocketType type, rtc::Socket::Option opt, int option) {
    if (opt == rtc::Socket::OPT_SNDBUF) {
      sendbuf_size_ = option;
    } else if (opt == rtc::Socket::OPT_RCVBUF) {
      recvbuf_size_ = option;
    } else if (opt == rtc::Socket::OPT_DSCP) {
      dscp_ = static_cast<rtc::DiffServCodePoint>(option);
    }
    return 0;
  }

  void PostMessage(int id, const rtc::CopyOnWriteBuffer& packet) {
    thread_->Post(RTC_FROM_HERE, this, id, rtc::WrapMessageData(packet));
  }

  virtual void OnMessage(rtc::Message* msg) {
    rtc::TypedMessageData<rtc::CopyOnWriteBuffer>* msg_data =
        static_cast<rtc::TypedMessageData<rtc::CopyOnWriteBuffer>*>(msg->pdata);
    if (dest_) {
      if (msg->message_id == ST_RTP) {
        dest_->OnPacketReceived(msg_data->data(), rtc::TimeMicros());
      } else {
        RTC_LOG(LS_VERBOSE) << "Dropping RTCP packet, they not handled by "
                               "MediaChannel anymore.";
      }
    }
    delete msg_data;
  }

 private:
  void SetRtpSsrc(uint32_t ssrc, rtc::CopyOnWriteBuffer& buffer) {
    RTC_CHECK_GE(buffer.size(), 12);
    rtc::SetBE32(buffer.MutableData() + 8, ssrc);
  }

  void GetNumRtpBytesAndPackets(uint32_t ssrc, int* bytes, int* packets) {
    if (bytes) {
      *bytes = 0;
    }
    if (packets) {
      *packets = 0;
    }
    for (size_t i = 0; i < rtp_packets_.size(); ++i) {
      if (ssrc == webrtc::ParseRtpSsrc(rtp_packets_[i])) {
        if (bytes) {
          *bytes += static_cast<int>(rtp_packets_[i].size());
        }
        if (packets) {
          ++(*packets);
        }
      }
    }
  }

  rtc::Thread* thread_;
  MediaChannel* dest_;
  bool conf_;
  // The ssrcs used in sending out packets in conference mode.
  std::vector<uint32_t> conf_sent_ssrcs_;
  // Map to track counts of packets that have been sent per ssrc.
  // This includes packets that are dropped.
  std::map<uint32_t, uint32_t> sent_ssrcs_;
  // Map to track packet-number that needs to be dropped per ssrc.
  std::map<uint32_t, std::set<uint32_t> > drop_map_;
  webrtc::Mutex mutex_;
  std::vector<rtc::CopyOnWriteBuffer> rtp_packets_;
  std::vector<rtc::CopyOnWriteBuffer> rtcp_packets_;
  int sendbuf_size_;
  int recvbuf_size_;
  rtc::DiffServCodePoint dscp_;
  // Options of the most recently sent packet.
  rtc::PacketOptions options_;
};

}  // namespace cricket

#endif  // MEDIA_BASE_FAKE_NETWORK_INTERFACE_H_
