/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_MOCK_SEND_QUEUE_H_
#define NET_DCSCTP_TX_MOCK_SEND_QUEUE_H_

#include <cstdint>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/tx/send_queue.h"
#include "test/gmock.h"

namespace dcsctp {

class MockSendQueue : public SendQueue {
 public:
  MockSendQueue() {
    ON_CALL(*this, Produce).WillByDefault([](TimeMs now, size_t max_size) {
      return absl::nullopt;
    });
  }

  MOCK_METHOD(absl::optional<SendQueue::DataToSend>,
              Produce,
              (TimeMs now, size_t max_size),
              (override));
  MOCK_METHOD(bool,
              Discard,
              (IsUnordered unordered, StreamID stream_id, MID message_id),
              (override));
  MOCK_METHOD(void, PrepareResetStream, (StreamID stream_id), (override));
  MOCK_METHOD(bool, HasStreamsReadyToBeReset, (), (const, override));
  MOCK_METHOD(std::vector<StreamID>, GetStreamsReadyToBeReset, (), (override));
  MOCK_METHOD(void, CommitResetStreams, (), (override));
  MOCK_METHOD(void, RollbackResetStreams, (), (override));
  MOCK_METHOD(void, Reset, (), (override));
  MOCK_METHOD(size_t, buffered_amount, (StreamID stream_id), (const, override));
  MOCK_METHOD(size_t, total_buffered_amount, (), (const, override));
  MOCK_METHOD(size_t,
              buffered_amount_low_threshold,
              (StreamID stream_id),
              (const, override));
  MOCK_METHOD(void,
              SetBufferedAmountLowThreshold,
              (StreamID stream_id, size_t bytes),
              (override));
  MOCK_METHOD(void, EnableMessageInterleaving, (bool enabled), (override));
};

}  // namespace dcsctp

#endif  // NET_DCSCTP_TX_MOCK_SEND_QUEUE_H_
