/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_RTP_DATA_ENGINE_H_
#define MEDIA_BASE_RTP_DATA_ENGINE_H_

#include <map>
#include <memory>
#include <string>
#include <vector>

#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/media_constants.h"
#include "media/base/media_engine.h"

namespace rtc {
class DataRateLimiter;
}

namespace cricket {

class RtpDataEngine : public DataEngineInterface {
 public:
  RtpDataEngine();

  virtual DataMediaChannel* CreateChannel(const MediaConfig& config);

  virtual const std::vector<DataCodec>& data_codecs() { return data_codecs_; }

 private:
  std::vector<DataCodec> data_codecs_;
};

// Keep track of sequence number and timestamp of an RTP stream.  The
// sequence number starts with a "random" value and increments.  The
// timestamp starts with a "random" value and increases monotonically
// according to the clockrate.
class RtpClock {
 public:
  RtpClock(int clockrate, uint16_t first_seq_num, uint32_t timestamp_offset)
      : clockrate_(clockrate),
        last_seq_num_(first_seq_num),
        timestamp_offset_(timestamp_offset) {}

  // Given the current time (in number of seconds which must be
  // monotonically increasing), Return the next sequence number and
  // timestamp.
  void Tick(double now, int* seq_num, uint32_t* timestamp);

 private:
  int clockrate_;
  uint16_t last_seq_num_;
  uint32_t timestamp_offset_;
};

class RtpDataMediaChannel : public DataMediaChannel {
 public:
  explicit RtpDataMediaChannel(const MediaConfig& config);
  virtual ~RtpDataMediaChannel();

  virtual bool SetSendParameters(const DataSendParameters& params);
  virtual bool SetRecvParameters(const DataRecvParameters& params);
  virtual bool AddSendStream(const StreamParams& sp);
  virtual bool RemoveSendStream(uint32_t ssrc);
  virtual bool AddRecvStream(const StreamParams& sp);
  virtual bool RemoveRecvStream(uint32_t ssrc);
  virtual void ResetUnsignaledRecvStream();
  virtual bool SetSend(bool send) {
    sending_ = send;
    return true;
  }
  virtual bool SetReceive(bool receive) {
    receiving_ = receive;
    return true;
  }
  virtual void OnPacketReceived(rtc::CopyOnWriteBuffer packet,
                                int64_t packet_time_us);
  virtual void OnReadyToSend(bool ready) {}
  virtual bool SendData(const SendDataParams& params,
                        const rtc::CopyOnWriteBuffer& payload,
                        SendDataResult* result);

 private:
  void Construct();
  bool SetMaxSendBandwidth(int bps);
  bool SetSendCodecs(const std::vector<DataCodec>& codecs);
  bool SetRecvCodecs(const std::vector<DataCodec>& codecs);

  bool sending_;
  bool receiving_;
  std::vector<DataCodec> send_codecs_;
  std::vector<DataCodec> recv_codecs_;
  std::vector<StreamParams> send_streams_;
  std::vector<StreamParams> recv_streams_;
  std::map<uint32_t, RtpClock*> rtp_clock_by_send_ssrc_;
  std::unique_ptr<rtc::DataRateLimiter> send_limiter_;
};

}  // namespace cricket

#endif  // MEDIA_BASE_RTP_DATA_ENGINE_H_
