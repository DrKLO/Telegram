/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_NACK_TRACKER_H_
#define MODULES_AUDIO_CODING_NETEQ_NACK_TRACKER_H_

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <vector>

#include "modules/include/module_common_types_public.h"
#include "rtc_base/gtest_prod_util.h"

//
// The NackTracker class keeps track of the lost packets, an estimate of
// time-to-play for each packet is also given.
//
// Every time a packet is pushed into NetEq, LastReceivedPacket() has to be
// called to update the NACK list.
//
// Every time 10ms audio is pulled from NetEq LastDecodedPacket() should be
// called, and time-to-play is updated at that moment.
//
// If packet N is received, any packet prior to |N - NackThreshold| which is not
// arrived is considered lost, and should be labeled as "missing" (the size of
// the list might be limited and older packet eliminated from the list). Packets
// |N - NackThreshold|, |N - NackThreshold + 1|, ..., |N - 1| are considered
// "late." A "late" packet with sequence number K is changed to "missing" any
// time a packet with sequence number newer than |K + NackList| is arrived.
//
// The NackTracker class has to know about the sample rate of the packets to
// compute time-to-play. So sample rate should be set as soon as the first
// packet is received. If there is a change in the receive codec (sender changes
// codec) then NackTracker should be reset. This is because NetEQ would flush
// its buffer and re-transmission is meaning less for old packet. Therefore, in
// that case, after reset the sampling rate has to be updated.
//
// Thread Safety
// =============
// Please note that this class in not thread safe. The class must be protected
// if different APIs are called from different threads.
//
namespace webrtc {

class NackTracker {
 public:
  // A limit for the size of the NACK list.
  static const size_t kNackListSizeLimit = 500;  // 10 seconds for 20 ms frame
                                                 // packets.
  // Factory method.
  static NackTracker* Create(int nack_threshold_packets);

  ~NackTracker();

  // Set a maximum for the size of the NACK list. If the last received packet
  // has sequence number of N, then NACK list will not contain any element
  // with sequence number earlier than N - |max_nack_list_size|.
  //
  // The largest maximum size is defined by |kNackListSizeLimit|
  void SetMaxNackListSize(size_t max_nack_list_size);

  // Set the sampling rate.
  //
  // If associated sampling rate of the received packets is changed, call this
  // function to update sampling rate. Note that if there is any change in
  // received codec then NetEq will flush its buffer and NACK has to be reset.
  // After Reset() is called sampling rate has to be set.
  void UpdateSampleRate(int sample_rate_hz);

  // Update the sequence number and the timestamp of the last decoded RTP. This
  // API should be called every time 10 ms audio is pulled from NetEq.
  void UpdateLastDecodedPacket(uint16_t sequence_number, uint32_t timestamp);

  // Update the sequence number and the timestamp of the last received RTP. This
  // API should be called every time a packet pushed into ACM.
  void UpdateLastReceivedPacket(uint16_t sequence_number, uint32_t timestamp);

  // Get a list of "missing" packets which have expected time-to-play larger
  // than the given round-trip-time (in milliseconds).
  // Note: Late packets are not included.
  std::vector<uint16_t> GetNackList(int64_t round_trip_time_ms) const;

  // Reset to default values. The NACK list is cleared.
  // |nack_threshold_packets_| & |max_nack_list_size_| preserve their values.
  void Reset();

 private:
  // This test need to access the private method GetNackList().
  FRIEND_TEST_ALL_PREFIXES(NackTrackerTest, EstimateTimestampAndTimeToPlay);

  struct NackElement {
    NackElement(int64_t initial_time_to_play_ms,
                uint32_t initial_timestamp,
                bool missing)
        : time_to_play_ms(initial_time_to_play_ms),
          estimated_timestamp(initial_timestamp),
          is_missing(missing) {}

    // Estimated time (ms) left for this packet to be decoded. This estimate is
    // updated every time jitter buffer decodes a packet.
    int64_t time_to_play_ms;

    // A guess about the timestamp of the missing packet, it is used for
    // estimation of |time_to_play_ms|. The estimate might be slightly wrong if
    // there has been frame-size change since the last received packet and the
    // missing packet. However, the risk of this is low, and in case of such
    // errors, there will be a minor misestimation in time-to-play of missing
    // packets. This will have a very minor effect on NACK performance.
    uint32_t estimated_timestamp;

    // True if the packet is considered missing. Otherwise indicates packet is
    // late.
    bool is_missing;
  };

  class NackListCompare {
   public:
    bool operator()(uint16_t sequence_number_old,
                    uint16_t sequence_number_new) const {
      return IsNewerSequenceNumber(sequence_number_new, sequence_number_old);
    }
  };

  typedef std::map<uint16_t, NackElement, NackListCompare> NackList;

  // Constructor.
  explicit NackTracker(int nack_threshold_packets);

  // This API is used only for testing to assess whether time-to-play is
  // computed correctly.
  NackList GetNackList() const;

  // Given the |sequence_number_current_received_rtp| of currently received RTP,
  // recognize packets which are not arrive and add to the list.
  void AddToList(uint16_t sequence_number_current_received_rtp);

  // This function subtracts 10 ms of time-to-play for all packets in NACK list.
  // This is called when 10 ms elapsed with no new RTP packet decoded.
  void UpdateEstimatedPlayoutTimeBy10ms();

  // Given the |sequence_number_current_received_rtp| and
  // |timestamp_current_received_rtp| of currently received RTP update number
  // of samples per packet.
  void UpdateSamplesPerPacket(uint16_t sequence_number_current_received_rtp,
                              uint32_t timestamp_current_received_rtp);

  // Given the |sequence_number_current_received_rtp| of currently received RTP
  // update the list. That is; some packets will change from late to missing,
  // some packets are inserted as missing and some inserted as late.
  void UpdateList(uint16_t sequence_number_current_received_rtp);

  // Packets which are considered late for too long (according to
  // |nack_threshold_packets_|) are flagged as missing.
  void ChangeFromLateToMissing(uint16_t sequence_number_current_received_rtp);

  // Packets which have sequence number older that
  // |sequence_num_last_received_rtp_| - |max_nack_list_size_| are removed
  // from the NACK list.
  void LimitNackListSize();

  // Estimate timestamp of a missing packet given its sequence number.
  uint32_t EstimateTimestamp(uint16_t sequence_number);

  // Compute time-to-play given a timestamp.
  int64_t TimeToPlay(uint32_t timestamp) const;

  // If packet N is arrived, any packet prior to N - |nack_threshold_packets_|
  // which is not arrived is considered missing, and should be in NACK list.
  // Also any packet in the range of N-1 and N - |nack_threshold_packets_|,
  // exclusive, which is not arrived is considered late, and should should be
  // in the list of late packets.
  const int nack_threshold_packets_;

  // Valid if a packet is received.
  uint16_t sequence_num_last_received_rtp_;
  uint32_t timestamp_last_received_rtp_;
  bool any_rtp_received_;  // If any packet received.

  // Valid if a packet is decoded.
  uint16_t sequence_num_last_decoded_rtp_;
  uint32_t timestamp_last_decoded_rtp_;
  bool any_rtp_decoded_;  // If any packet decoded.

  int sample_rate_khz_;  // Sample rate in kHz.

  // Number of samples per packet. We update this every time we receive a
  // packet, not only for consecutive packets.
  int samples_per_packet_;

  // A list of missing packets to be retransmitted. Components of the list
  // contain the sequence number of missing packets and the estimated time that
  // each pack is going to be played out.
  NackList nack_list_;

  // NACK list will not keep track of missing packets prior to
  // |sequence_num_last_received_rtp_| - |max_nack_list_size_|.
  size_t max_nack_list_size_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_NETEQ_NACK_TRACKER_H_
