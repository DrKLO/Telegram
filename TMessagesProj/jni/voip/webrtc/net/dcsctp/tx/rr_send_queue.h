/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_RR_SEND_QUEUE_H_
#define NET_DCSCTP_TX_RR_SEND_QUEUE_H_

#include <cstdint>
#include <deque>
#include <map>
#include <string>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/public/dcsctp_message.h"
#include "net/dcsctp/public/dcsctp_socket.h"
#include "net/dcsctp/public/types.h"
#include "net/dcsctp/tx/send_queue.h"

namespace dcsctp {

// The Round Robin SendQueue holds all messages that the client wants to send,
// but that haven't yet been split into chunks and fully sent on the wire.
//
// As defined in https://datatracker.ietf.org/doc/html/rfc8260#section-3.2,
// it will cycle to send messages from different streams. It will send all
// fragments from one message before continuing with a different message on
// possibly a different stream, until support for message interleaving has been
// implemented.
//
// As messages can be (requested to be) sent before the connection is properly
// established, this send queue is always present - even for closed connections.
class RRSendQueue : public SendQueue {
 public:
  // How small a data chunk's payload may be, if having to fragment a message.
  static constexpr size_t kMinimumFragmentedPayload = 10;

  RRSendQueue(absl::string_view log_prefix,
              size_t buffer_size,
              std::function<void(StreamID)> on_buffered_amount_low,
              size_t total_buffered_amount_low_threshold,
              std::function<void()> on_total_buffered_amount_low,
              const DcSctpSocketHandoverState* handover_state = nullptr);

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
  bool Discard(IsUnordered unordered,
               StreamID stream_id,
               MID message_id) override;
  void PrepareResetStreams(rtc::ArrayView<const StreamID> streams) override;
  bool CanResetStreams() const override;
  void CommitResetStreams() override;
  void RollbackResetStreams() override;
  void Reset() override;
  size_t buffered_amount(StreamID stream_id) const override;
  size_t total_buffered_amount() const override {
    return total_buffered_amount_.value();
  }
  size_t buffered_amount_low_threshold(StreamID stream_id) const override;
  void SetBufferedAmountLowThreshold(StreamID stream_id, size_t bytes) override;

  HandoverReadinessStatus GetHandoverReadiness() const;
  void AddHandoverState(DcSctpSocketHandoverState& state);
  void RestoreFromState(const DcSctpSocketHandoverState& state);

 private:
  // Represents a value and a "low threshold" that when the value reaches or
  // goes under the "low threshold", will trigger `on_threshold_reached`
  // callback.
  class ThresholdWatcher {
   public:
    explicit ThresholdWatcher(std::function<void()> on_threshold_reached)
        : on_threshold_reached_(std::move(on_threshold_reached)) {}
    // Increases the value.
    void Increase(size_t bytes) { value_ += bytes; }
    // Decreases the value and triggers `on_threshold_reached` if it's at or
    // below `low_threshold()`.
    void Decrease(size_t bytes);

    size_t value() const { return value_; }
    size_t low_threshold() const { return low_threshold_; }
    void SetLowThreshold(size_t low_threshold);

   private:
    const std::function<void()> on_threshold_reached_;
    size_t value_ = 0;
    size_t low_threshold_ = 0;
  };

  // Per-stream information.
  class OutgoingStream {
   public:
    explicit OutgoingStream(
        std::function<void()> on_buffered_amount_low,
        ThresholdWatcher& total_buffered_amount,
        const DcSctpSocketHandoverState::OutgoingStream* state = nullptr)
        : next_unordered_mid_(MID(state ? state->next_unordered_mid : 0)),
          next_ordered_mid_(MID(state ? state->next_ordered_mid : 0)),
          next_ssn_(SSN(state ? state->next_ssn : 0)),
          buffered_amount_(std::move(on_buffered_amount_low)),
          total_buffered_amount_(total_buffered_amount) {}

    // Enqueues a message to this stream.
    void Add(DcSctpMessage message,
             TimeMs expires_at,
             const SendOptions& send_options);

    // Possibly produces a data chunk to send.
    absl::optional<DataToSend> Produce(TimeMs now, size_t max_size);

    const ThresholdWatcher& buffered_amount() const { return buffered_amount_; }
    ThresholdWatcher& buffered_amount() { return buffered_amount_; }

    // Discards a partially sent message, see `SendQueue::Discard`.
    bool Discard(IsUnordered unordered, MID message_id);

    // Pauses this stream, which is used before resetting it.
    void Pause();

    // Resumes a paused stream.
    void Resume() { is_paused_ = false; }

    bool is_paused() const { return is_paused_; }

    // Resets this stream, meaning MIDs and SSNs are set to zero.
    void Reset();

    // Indicates if this stream has a partially sent message in it.
    bool has_partially_sent_message() const;

    // Indicates if the stream has data to send. It will also try to remove any
    // expired non-partially sent message.
    bool HasDataToSend(TimeMs now);

    void AddHandoverState(
        DcSctpSocketHandoverState::OutgoingStream& state) const;

   private:
    // An enqueued message and metadata.
    struct Item {
      explicit Item(DcSctpMessage msg,
                    TimeMs expires_at,
                    const SendOptions& send_options)
          : message(std::move(msg)),
            expires_at(expires_at),
            send_options(send_options),
            remaining_offset(0),
            remaining_size(message.payload().size()) {}
      DcSctpMessage message;
      TimeMs expires_at;
      SendOptions send_options;
      // The remaining payload (offset and size) to be sent, when it has been
      // fragmented.
      size_t remaining_offset;
      size_t remaining_size;
      // If set, an allocated Message ID and SSN. Will be allocated when the
      // first fragment is sent.
      absl::optional<MID> message_id = absl::nullopt;
      absl::optional<SSN> ssn = absl::nullopt;
      // The current Fragment Sequence Number, incremented for each fragment.
      FSN current_fsn = FSN(0);
    };

    bool IsConsistent() const;

    // Streams are pause when they are about to be reset.
    bool is_paused_ = false;
    // MIDs are different for unordered and ordered messages sent on a stream.
    MID next_unordered_mid_;
    MID next_ordered_mid_;

    SSN next_ssn_;
    // Enqueued messages, and metadata.
    std::deque<Item> items_;

    // The current amount of buffered data.
    ThresholdWatcher buffered_amount_;

    // Reference to the total buffered amount, which is updated directly by each
    // stream.
    ThresholdWatcher& total_buffered_amount_;
  };

  bool IsConsistent() const;
  OutgoingStream& GetOrCreateStreamInfo(StreamID stream_id);
  absl::optional<DataToSend> Produce(
      std::map<StreamID, OutgoingStream>::iterator it,
      TimeMs now,
      size_t max_size);

  // Return the next stream, in round-robin fashion.
  std::map<StreamID, OutgoingStream>::iterator GetNextStream(TimeMs now);

  const std::string log_prefix_;
  const size_t buffer_size_;

  // Called when the buffered amount is below what has been set using
  // `SetBufferedAmountLowThreshold`.
  const std::function<void(StreamID)> on_buffered_amount_low_;

  // Called when the total buffered amount is below what has been set using
  // `SetTotalBufferedAmountLowThreshold`.
  const std::function<void()> on_total_buffered_amount_low_;

  // The total amount of buffer data, for all streams.
  ThresholdWatcher total_buffered_amount_;

  // Indicates if the previous fragment sent was the end of a message. For
  // non-interleaved sending, this means that the next message may come from a
  // different stream. If not true, the next fragment must be produced from the
  // same stream as last time.
  bool previous_message_has_ended_ = true;

  // The current stream to send chunks from. Modified by `GetNextStream`.
  StreamID current_stream_id_ = StreamID(0);

  // All streams, and messages added to those.
  std::map<StreamID, OutgoingStream> streams_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_TX_RR_SEND_QUEUE_H_
