/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_FCFS_SEND_QUEUE_H_
#define NET_DCSCTP_TX_FCFS_SEND_QUEUE_H_

#include <cstdint>
#include <deque>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/pair_hash.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/tx/send_queue.h"

namespace dcsctp {

// The FCFSSendQueue (First-Come, First-Served Send Queue) holds all messages
// that the client wants to send, but that haven't yet been split into chunks
// and sent on the wire.
//
// First-Come, First Served means that it passes the data in the exact same
// order as they were delivered by the calling application, and is defined in
// https://tools.ietf.org/html/rfc8260#section-3.1. It's a FIFO queue, but that
// term isn't used in this RFC.
//
// As messages can be (requested to be) sent before
// the connection is properly established, this send queue is always present -
// even for closed connections.
class FCFSSendQueue : public SendQueue {
 public:
  // How small a data chunk's payload may be, if having to fragment a message.
  static constexpr size_t kMinimumFragmentedPayload = 10;

  FCFSSendQueue(absl::string_view log_prefix, size_t buffer_size)
      : log_prefix_(std::string(log_prefix) + "fcfs: "),
        buffer_size_(buffer_size) {}

  // Indicates if the buffer is full. Note that it's up to the caller to ensure
  // that the buffer is not full prior to adding new items to it.
  bool IsFull() const;
  // Indicates if the buffer is empty.
  bool IsEmpty() const;

  // Adds the message to be sent using the `send_options` provided. The current
  // time should be in `now`. Note that it's the responsibility of the caller to
  // ensure that the buffer is not full (by calling `IsFull`) before adding
  // messages to it.
  void Add(TimeMs now,
           DcSctpMessage message,
           const SendOptions& send_options = {});

  // Implementation of `SendQueue`.
  absl::optional<DataToSend> Produce(TimeMs now, size_t max_size) override;
  void Discard(IsUnordered unordered,
               StreamID stream_id,
               MID message_id) override;
  void PrepareResetStreams(rtc::ArrayView<const StreamID> streams) override;
  bool CanResetStreams() const override;
  void CommitResetStreams() override;
  void RollbackResetStreams() override;
  void Reset() override;

  // The size of the buffer, in "payload bytes".
  size_t total_bytes() const;

 private:
  // An enqueued message and metadata.
  struct Item {
    explicit Item(DcSctpMessage msg,
                  absl::optional<TimeMs> expires_at,
                  const SendOptions& send_options)
        : message(std::move(msg)),
          expires_at(expires_at),
          send_options(send_options),
          remaining_offset(0),
          remaining_size(message.payload().size()) {}
    DcSctpMessage message;
    absl::optional<TimeMs> expires_at;
    SendOptions send_options;
    // The remaining payload (offset and size) to be sent, when it has been
    // fragmented.
    size_t remaining_offset;
    size_t remaining_size;
    // If set, an allocated Message ID and SSN. Will be allocated when the first
    // fragment is sent.
    absl::optional<MID> message_id = absl::nullopt;
    absl::optional<SSN> ssn = absl::nullopt;
    // The current Fragment Sequence Number, incremented for each fragment.
    FSN current_fsn = FSN(0);
  };

  Item* GetFirstNonExpiredMessage(TimeMs now);
  bool IsPaused(StreamID stream_id) const;

  const std::string log_prefix_;
  const size_t buffer_size_;
  std::deque<Item> items_;

  std::unordered_set<StreamID, StreamID::Hasher> paused_streams_;
  std::deque<Item> paused_items_;

  std::unordered_map<std::pair<IsUnordered, StreamID>, MID, UnorderedStreamHash>
      mid_by_stream_id_;
  std::unordered_map<StreamID, SSN, StreamID::Hasher> ssn_by_stream_id_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_TX_FCFS_SEND_QUEUE_H_
