/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_TX_SEND_QUEUE_H_
#define NET_DCSCTP_TX_SEND_QUEUE_H_

#include <cstdint>
#include <limits>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/internal_types.h"
#include "net/dcsctp/packet/data.h"
#include "net/dcsctp/public/types.h"

namespace dcsctp {

class SendQueue {
 public:
  // Container for a data chunk that is produced by the SendQueue
  struct DataToSend {
    explicit DataToSend(Data data) : data(std::move(data)) {}
    // The data to send, including all parameters.
    Data data;

    // Partial reliability - RFC3758
    absl::optional<int> max_retransmissions;
    absl::optional<TimeMs> expires_at;
  };

  virtual ~SendQueue() = default;

  // TODO(boivie): This interface is obviously missing an "Add" function, but
  // that is postponed a bit until the story around how to model message
  // prioritization, which is important for any advanced stream scheduler, is
  // further clarified.

  // Produce a chunk to be sent.
  //
  // `max_size` refers to how many payload bytes that may be produced, not
  // including any headers.
  virtual absl::optional<DataToSend> Produce(TimeMs now, size_t max_size) = 0;

  // Discards a partially sent message identified by the parameters `unordered`,
  // `stream_id` and `message_id`. The `message_id` comes from the returned
  // information when having called `Produce`. A partially sent message means
  // that it has had at least one fragment of it returned when `Produce` was
  // called prior to calling this method).
  //
  // This is used when a message has been found to be expired (by the partial
  // reliability extension), and the retransmission queue will signal the
  // receiver that any partially received message fragments should be skipped.
  // This means that any remaining fragments in the Send Queue must be removed
  // as well so that they are not sent.
  virtual void Discard(IsUnordered unordered,
                       StreamID stream_id,
                       MID message_id) = 0;

  // Prepares the streams to be reset. This is used to close a WebRTC data
  // channel and will be signaled to the other side.
  //
  // Concretely, it discards all whole (not partly sent) messages in the given
  // streams and pauses those streams so that future added messages aren't
  // produced until `ResumeStreams` is called.
  //
  // TODO(boivie): Investigate if it really should discard any message at all.
  // RFC8831 only mentions that "[RFC6525] also guarantees that all the messages
  // are delivered (or abandoned) before the stream is reset."
  //
  // This method can be called multiple times to add more streams to be
  // reset, and paused while they are resetting. This is the first part of the
  // two-phase commit protocol to reset streams, where the caller completes the
  // procedure by either calling `CommitResetStreams` or `RollbackResetStreams`.
  virtual void PrepareResetStreams(rtc::ArrayView<const StreamID> streams) = 0;

  // Returns true if all non-discarded messages during `PrepareResetStreams`
  // (which are those that was partially sent before that method was called)
  // have been sent.
  virtual bool CanResetStreams() const = 0;

  // Called to commit to reset the streams provided to `PrepareResetStreams`.
  // It will reset the stream sequence numbers (SSNs) and message identifiers
  // (MIDs) and resume the paused streams.
  virtual void CommitResetStreams() = 0;

  // Called to abort the resetting of streams provided to `PrepareResetStreams`.
  // Will resume the paused streams without resetting the stream sequence
  // numbers (SSNs) or message identifiers (MIDs). Note that the non-partial
  // messages that were discarded when calling `PrepareResetStreams` will not be
  // recovered, to better match the intention from the sender to "close the
  // channel".
  virtual void RollbackResetStreams() = 0;

  // Resets all message identifier counters (MID, SSN) and makes all partially
  // messages be ready to be re-sent in full. This is used when the peer has
  // been detected to have restarted and is used to try to minimize the amount
  // of data loss. However, data loss cannot be completely guaranteed when a
  // peer restarts.
  virtual void Reset() = 0;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_TX_SEND_QUEUE_H_
