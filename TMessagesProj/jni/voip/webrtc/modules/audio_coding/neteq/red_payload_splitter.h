/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_RED_PAYLOAD_SPLITTER_H_
#define MODULES_AUDIO_CODING_NETEQ_RED_PAYLOAD_SPLITTER_H_

#include "modules/audio_coding/neteq/packet.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

class DecoderDatabase;

// This class handles splitting of RED payloads into smaller parts.
// Codec-specific packet splitting can be performed by
// AudioDecoder::ParsePayload.
class RedPayloadSplitter {
 public:
  RedPayloadSplitter() {}

  virtual ~RedPayloadSplitter() {}

  // Splits each packet in |packet_list| into its separate RED payloads. Each
  // RED payload is packetized into a Packet. The original elements in
  // |packet_list| are properly deleted, and replaced by the new packets.
  // Note that all packets in |packet_list| must be RED payloads, i.e., have
  // RED headers according to RFC 2198 at the very beginning of the payload.
  // Returns kOK or an error.
  virtual bool SplitRed(PacketList* packet_list);

  // Checks all packets in |packet_list|. Packets that are DTMF events or
  // comfort noise payloads are kept. Except that, only one single payload type
  // is accepted. Any packet with another payload type is discarded.
  virtual void CheckRedPayloads(PacketList* packet_list,
                                const DecoderDatabase& decoder_database);

 private:
  RTC_DISALLOW_COPY_AND_ASSIGN(RedPayloadSplitter);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_RED_PAYLOAD_SPLITTER_H_
