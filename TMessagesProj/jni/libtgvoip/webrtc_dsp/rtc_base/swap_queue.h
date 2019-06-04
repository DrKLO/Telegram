/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SWAP_QUEUE_H_
#define RTC_BASE_SWAP_QUEUE_H_

#include <algorithm>
#include <utility>
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/constructormagic.h"
#include "rtc_base/criticalsection.h"
#include "rtc_base/system/unused.h"

namespace webrtc {

namespace internal {

// (Internal; please don't use outside this file.)
template <typename T>
bool NoopSwapQueueItemVerifierFunction(const T&) {
  return true;
}

}  // namespace internal

// Functor to use when supplying a verifier function for the queue.
template <typename T,
          bool (*QueueItemVerifierFunction)(const T&) =
              internal::NoopSwapQueueItemVerifierFunction>
class SwapQueueItemVerifier {
 public:
  bool operator()(const T& t) const { return QueueItemVerifierFunction(t); }
};

// This class is a fixed-size queue. A producer calls Insert() to insert
// an element of type T at the back of the queue, and a consumer calls
// Remove() to remove an element from the front of the queue. It's safe
// for the producer(s) and the consumer(s) to access the queue
// concurrently, from different threads.
//
// To avoid the construction, copying, and destruction of Ts that a naive
// queue implementation would require, for each "full" T passed from
// producer to consumer, SwapQueue<T> passes an "empty" T in the other
// direction (an "empty" T is one that contains nothing of value for the
// consumer). This bidirectional movement is implemented with swap().
//
// // Create queue:
// Bottle proto(568);  // Prepare an empty Bottle. Heap allocates space for
//                     // 568 ml.
// SwapQueue<Bottle> q(N, proto);  // Init queue with N copies of proto.
//                                 // Each copy allocates on the heap.
// // Producer pseudo-code:
// Bottle b(568); // Prepare an empty Bottle. Heap allocates space for 568 ml.
// loop {
//   b.Fill(amount);  // Where amount <= 568 ml.
//   q.Insert(&b);    // Swap our full Bottle for an empty one from q.
// }
//
// // Consumer pseudo-code:
// Bottle b(568);  // Prepare an empty Bottle. Heap allocates space for 568 ml.
// loop {
//   q.Remove(&b); // Swap our empty Bottle for the next-in-line full Bottle.
//   Drink(&b);
// }
//
// For a well-behaved Bottle class, there are no allocations in the
// producer, since it just fills an empty Bottle that's already large
// enough; no deallocations in the consumer, since it returns each empty
// Bottle to the queue after having drunk it; and no copies along the
// way, since the queue uses swap() everywhere to move full Bottles in
// one direction and empty ones in the other.
template <typename T, typename QueueItemVerifier = SwapQueueItemVerifier<T>>
class SwapQueue {
 public:
  // Creates a queue of size size and fills it with default constructed Ts.
  explicit SwapQueue(size_t size) : queue_(size) {
    RTC_DCHECK(VerifyQueueSlots());
  }

  // Same as above and accepts an item verification functor.
  SwapQueue(size_t size, const QueueItemVerifier& queue_item_verifier)
      : queue_item_verifier_(queue_item_verifier), queue_(size) {
    RTC_DCHECK(VerifyQueueSlots());
  }

  // Creates a queue of size size and fills it with copies of prototype.
  SwapQueue(size_t size, const T& prototype) : queue_(size, prototype) {
    RTC_DCHECK(VerifyQueueSlots());
  }

  // Same as above and accepts an item verification functor.
  SwapQueue(size_t size,
            const T& prototype,
            const QueueItemVerifier& queue_item_verifier)
      : queue_item_verifier_(queue_item_verifier), queue_(size, prototype) {
    RTC_DCHECK(VerifyQueueSlots());
  }

  // Resets the queue to have zero content wile maintaining the queue size.
  void Clear() {
    rtc::CritScope cs(&crit_queue_);
    next_write_index_ = 0;
    next_read_index_ = 0;
    num_elements_ = 0;
  }

  // Inserts a "full" T at the back of the queue by swapping *input with an
  // "empty" T from the queue.
  // Returns true if the item was inserted or false if not (the queue was full).
  // When specified, the T given in *input must pass the ItemVerifier() test.
  // The contents of *input after the call are then also guaranteed to pass the
  // ItemVerifier() test.
  bool Insert(T* input) RTC_WARN_UNUSED_RESULT {
    RTC_DCHECK(input);

    rtc::CritScope cs(&crit_queue_);

    RTC_DCHECK(queue_item_verifier_(*input));

    if (num_elements_ == queue_.size()) {
      return false;
    }

    using std::swap;
    swap(*input, queue_[next_write_index_]);

    ++next_write_index_;
    if (next_write_index_ == queue_.size()) {
      next_write_index_ = 0;
    }

    ++num_elements_;

    RTC_DCHECK_LT(next_write_index_, queue_.size());
    RTC_DCHECK_LE(num_elements_, queue_.size());

    return true;
  }

  // Removes the frontmost "full" T from the queue by swapping it with
  // the "empty" T in *output.
  // Returns true if an item could be removed or false if not (the queue was
  // empty). When specified, The T given in *output must pass the ItemVerifier()
  // test and the contents of *output after the call are then also guaranteed to
  // pass the ItemVerifier() test.
  bool Remove(T* output) RTC_WARN_UNUSED_RESULT {
    RTC_DCHECK(output);

    rtc::CritScope cs(&crit_queue_);

    RTC_DCHECK(queue_item_verifier_(*output));

    if (num_elements_ == 0) {
      return false;
    }

    using std::swap;
    swap(*output, queue_[next_read_index_]);

    ++next_read_index_;
    if (next_read_index_ == queue_.size()) {
      next_read_index_ = 0;
    }

    --num_elements_;

    RTC_DCHECK_LT(next_read_index_, queue_.size());
    RTC_DCHECK_LE(num_elements_, queue_.size());

    return true;
  }

 private:
  // Verify that the queue slots complies with the ItemVerifier test.
  bool VerifyQueueSlots() {
    rtc::CritScope cs(&crit_queue_);
    for (const auto& v : queue_) {
      RTC_DCHECK(queue_item_verifier_(v));
    }
    return true;
  }

  rtc::CriticalSection crit_queue_;

  // TODO(peah): Change this to use std::function() once we can use C++11 std
  // lib.
  QueueItemVerifier queue_item_verifier_ RTC_GUARDED_BY(crit_queue_);

  // (next_read_index_ + num_elements_) % queue_.size() =
  //  next_write_index_
  size_t next_write_index_ RTC_GUARDED_BY(crit_queue_) = 0;
  size_t next_read_index_ RTC_GUARDED_BY(crit_queue_) = 0;
  size_t num_elements_ RTC_GUARDED_BY(crit_queue_) = 0;

  // queue_.size() is constant.
  std::vector<T> queue_ RTC_GUARDED_BY(crit_queue_);

  RTC_DISALLOW_COPY_AND_ASSIGN(SwapQueue);
};

}  // namespace webrtc

#endif  // RTC_BASE_SWAP_QUEUE_H_
