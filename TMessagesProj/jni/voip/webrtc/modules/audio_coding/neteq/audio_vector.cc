/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/audio_vector.h"

#include <algorithm>
#include <memory>

#include "rtc_base/checks.h"

namespace webrtc {

AudioVector::AudioVector() : AudioVector(kDefaultInitialSize) {
  Clear();
}

AudioVector::AudioVector(size_t initial_size)
    : array_(new int16_t[initial_size + 1]),
      capacity_(initial_size + 1),
      begin_index_(0),
      end_index_(capacity_ - 1) {
  memset(array_.get(), 0, capacity_ * sizeof(int16_t));
}

AudioVector::~AudioVector() = default;

void AudioVector::Clear() {
  end_index_ = begin_index_ = 0;
}

void AudioVector::CopyTo(AudioVector* copy_to) const {
  RTC_DCHECK(copy_to);
  copy_to->Reserve(Size());
  CopyTo(Size(), 0, copy_to->array_.get());
  copy_to->begin_index_ = 0;
  copy_to->end_index_ = Size();
}

void AudioVector::CopyTo(size_t length,
                         size_t position,
                         int16_t* copy_to) const {
  if (length == 0)
    return;
  length = std::min(length, Size() - position);
  const size_t copy_index = (begin_index_ + position) % capacity_;
  const size_t first_chunk_length = std::min(length, capacity_ - copy_index);
  memcpy(copy_to, &array_[copy_index], first_chunk_length * sizeof(int16_t));
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0) {
    memcpy(&copy_to[first_chunk_length], array_.get(),
           remaining_length * sizeof(int16_t));
  }
}

void AudioVector::PushFront(const AudioVector& prepend_this) {
  const size_t length = prepend_this.Size();
  if (length == 0)
    return;

  // Although the subsequent calling to PushFront does Reserve in it, it is
  // always more efficient to do a big Reserve first.
  Reserve(Size() + length);

  const size_t first_chunk_length =
      std::min(length, prepend_this.capacity_ - prepend_this.begin_index_);
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0)
    PushFront(prepend_this.array_.get(), remaining_length);
  PushFront(&prepend_this.array_[prepend_this.begin_index_],
            first_chunk_length);
}

void AudioVector::PushFront(const int16_t* prepend_this, size_t length) {
  if (length == 0)
    return;
  Reserve(Size() + length);
  const size_t first_chunk_length = std::min(length, begin_index_);
  memcpy(&array_[begin_index_ - first_chunk_length],
         &prepend_this[length - first_chunk_length],
         first_chunk_length * sizeof(int16_t));
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0) {
    memcpy(&array_[capacity_ - remaining_length], prepend_this,
           remaining_length * sizeof(int16_t));
  }
  begin_index_ = (begin_index_ + capacity_ - length) % capacity_;
}

void AudioVector::PushBack(const AudioVector& append_this) {
  PushBack(append_this, append_this.Size(), 0);
}

void AudioVector::PushBack(const AudioVector& append_this,
                           size_t length,
                           size_t position) {
  RTC_DCHECK_LE(position, append_this.Size());
  RTC_DCHECK_LE(length, append_this.Size() - position);

  if (length == 0)
    return;

  // Although the subsequent calling to PushBack does Reserve in it, it is
  // always more efficient to do a big Reserve first.
  Reserve(Size() + length);

  const size_t start_index =
      (append_this.begin_index_ + position) % append_this.capacity_;
  const size_t first_chunk_length =
      std::min(length, append_this.capacity_ - start_index);
  PushBack(&append_this.array_[start_index], first_chunk_length);

  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0)
    PushBack(append_this.array_.get(), remaining_length);
}

void AudioVector::PushBack(const int16_t* append_this, size_t length) {
  if (length == 0)
    return;
  Reserve(Size() + length);
  const size_t first_chunk_length = std::min(length, capacity_ - end_index_);
  memcpy(&array_[end_index_], append_this,
         first_chunk_length * sizeof(int16_t));
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0) {
    memcpy(array_.get(), &append_this[first_chunk_length],
           remaining_length * sizeof(int16_t));
  }
  end_index_ = (end_index_ + length) % capacity_;
}

void AudioVector::PopFront(size_t length) {
  if (length == 0)
    return;
  length = std::min(length, Size());
  begin_index_ = (begin_index_ + length) % capacity_;
}

void AudioVector::PopBack(size_t length) {
  if (length == 0)
    return;
  // Never remove more than what is in the array.
  length = std::min(length, Size());
  end_index_ = (end_index_ + capacity_ - length) % capacity_;
}

void AudioVector::Extend(size_t extra_length) {
  if (extra_length == 0)
    return;
  InsertZerosByPushBack(extra_length, Size());
}

void AudioVector::InsertAt(const int16_t* insert_this,
                           size_t length,
                           size_t position) {
  if (length == 0)
    return;
  // Cap the insert position at the current array length.
  position = std::min(Size(), position);

  // When inserting to a position closer to the beginning, it is more efficient
  // to insert by pushing front than to insert by pushing back, since less data
  // will be moved, vice versa.
  if (position <= Size() - position) {
    InsertByPushFront(insert_this, length, position);
  } else {
    InsertByPushBack(insert_this, length, position);
  }
}

void AudioVector::InsertZerosAt(size_t length, size_t position) {
  if (length == 0)
    return;
  // Cap the insert position at the current array length.
  position = std::min(Size(), position);

  // When inserting to a position closer to the beginning, it is more efficient
  // to insert by pushing front than to insert by pushing back, since less data
  // will be moved, vice versa.
  if (position <= Size() - position) {
    InsertZerosByPushFront(length, position);
  } else {
    InsertZerosByPushBack(length, position);
  }
}

void AudioVector::OverwriteAt(const AudioVector& insert_this,
                              size_t length,
                              size_t position) {
  RTC_DCHECK_LE(length, insert_this.Size());
  if (length == 0)
    return;

  // Cap the insert position at the current array length.
  position = std::min(Size(), position);

  // Although the subsequent calling to OverwriteAt does Reserve in it, it is
  // always more efficient to do a big Reserve first.
  size_t new_size = std::max(Size(), position + length);
  Reserve(new_size);

  const size_t first_chunk_length =
      std::min(length, insert_this.capacity_ - insert_this.begin_index_);
  OverwriteAt(&insert_this.array_[insert_this.begin_index_], first_chunk_length,
              position);
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0) {
    OverwriteAt(insert_this.array_.get(), remaining_length,
                position + first_chunk_length);
  }
}

void AudioVector::OverwriteAt(const int16_t* insert_this,
                              size_t length,
                              size_t position) {
  if (length == 0)
    return;
  // Cap the insert position at the current array length.
  position = std::min(Size(), position);

  size_t new_size = std::max(Size(), position + length);
  Reserve(new_size);

  const size_t overwrite_index = (begin_index_ + position) % capacity_;
  const size_t first_chunk_length =
      std::min(length, capacity_ - overwrite_index);
  memcpy(&array_[overwrite_index], insert_this,
         first_chunk_length * sizeof(int16_t));
  const size_t remaining_length = length - first_chunk_length;
  if (remaining_length > 0) {
    memcpy(array_.get(), &insert_this[first_chunk_length],
           remaining_length * sizeof(int16_t));
  }

  end_index_ = (begin_index_ + new_size) % capacity_;
}

void AudioVector::CrossFade(const AudioVector& append_this,
                            size_t fade_length) {
  // Fade length cannot be longer than the current vector or `append_this`.
  RTC_DCHECK_LE(fade_length, Size());
  RTC_DCHECK_LE(fade_length, append_this.Size());
  fade_length = std::min(fade_length, Size());
  fade_length = std::min(fade_length, append_this.Size());
  size_t position = Size() - fade_length + begin_index_;
  // Cross fade the overlapping regions.
  // `alpha` is the mixing factor in Q14.
  // TODO(hlundin): Consider skipping +1 in the denominator to produce a
  // smoother cross-fade, in particular at the end of the fade.
  int alpha_step = 16384 / (static_cast<int>(fade_length) + 1);
  int alpha = 16384;
  for (size_t i = 0; i < fade_length; ++i) {
    alpha -= alpha_step;
    array_[(position + i) % capacity_] =
        (alpha * array_[(position + i) % capacity_] +
         (16384 - alpha) * append_this[i] + 8192) >>
        14;
  }
  RTC_DCHECK_GE(alpha, 0);  // Verify that the slope was correct.
  // Append what is left of `append_this`.
  size_t samples_to_push_back = append_this.Size() - fade_length;
  if (samples_to_push_back > 0)
    PushBack(append_this, samples_to_push_back, fade_length);
}

// Returns the number of elements in this AudioVector.
size_t AudioVector::Size() const {
  return (end_index_ + capacity_ - begin_index_) % capacity_;
}

// Returns true if this AudioVector is empty.
bool AudioVector::Empty() const {
  return begin_index_ == end_index_;
}

void AudioVector::Reserve(size_t n) {
  if (capacity_ > n)
    return;
  const size_t length = Size();
  // Reserve one more sample to remove the ambiguity between empty vector and
  // full vector. Therefore `begin_index_` == `end_index_` indicates empty
  // vector, and `begin_index_` == (`end_index_` + 1) % capacity indicates
  // full vector.
  std::unique_ptr<int16_t[]> temp_array(new int16_t[n + 1]);
  CopyTo(length, 0, temp_array.get());
  array_.swap(temp_array);
  begin_index_ = 0;
  end_index_ = length;
  capacity_ = n + 1;
}

void AudioVector::InsertByPushBack(const int16_t* insert_this,
                                   size_t length,
                                   size_t position) {
  const size_t move_chunk_length = Size() - position;
  std::unique_ptr<int16_t[]> temp_array(nullptr);
  if (move_chunk_length > 0) {
    // TODO(minyue): see if it is possible to avoid copying to a buffer.
    temp_array.reset(new int16_t[move_chunk_length]);
    CopyTo(move_chunk_length, position, temp_array.get());
    PopBack(move_chunk_length);
  }

  Reserve(Size() + length + move_chunk_length);
  PushBack(insert_this, length);
  if (move_chunk_length > 0)
    PushBack(temp_array.get(), move_chunk_length);
}

void AudioVector::InsertByPushFront(const int16_t* insert_this,
                                    size_t length,
                                    size_t position) {
  std::unique_ptr<int16_t[]> temp_array(nullptr);
  if (position > 0) {
    // TODO(minyue): see if it is possible to avoid copying to a buffer.
    temp_array.reset(new int16_t[position]);
    CopyTo(position, 0, temp_array.get());
    PopFront(position);
  }

  Reserve(Size() + length + position);
  PushFront(insert_this, length);
  if (position > 0)
    PushFront(temp_array.get(), position);
}

void AudioVector::InsertZerosByPushBack(size_t length, size_t position) {
  const size_t move_chunk_length = Size() - position;
  std::unique_ptr<int16_t[]> temp_array(nullptr);
  if (move_chunk_length > 0) {
    temp_array.reset(new int16_t[move_chunk_length]);
    CopyTo(move_chunk_length, position, temp_array.get());
    PopBack(move_chunk_length);
  }

  Reserve(Size() + length + move_chunk_length);

  const size_t first_zero_chunk_length =
      std::min(length, capacity_ - end_index_);
  memset(&array_[end_index_], 0, first_zero_chunk_length * sizeof(int16_t));
  const size_t remaining_zero_length = length - first_zero_chunk_length;
  if (remaining_zero_length > 0)
    memset(array_.get(), 0, remaining_zero_length * sizeof(int16_t));
  end_index_ = (end_index_ + length) % capacity_;

  if (move_chunk_length > 0)
    PushBack(temp_array.get(), move_chunk_length);
}

void AudioVector::InsertZerosByPushFront(size_t length, size_t position) {
  std::unique_ptr<int16_t[]> temp_array(nullptr);
  if (position > 0) {
    temp_array.reset(new int16_t[position]);
    CopyTo(position, 0, temp_array.get());
    PopFront(position);
  }

  Reserve(Size() + length + position);

  const size_t first_zero_chunk_length = std::min(length, begin_index_);
  memset(&array_[begin_index_ - first_zero_chunk_length], 0,
         first_zero_chunk_length * sizeof(int16_t));
  const size_t remaining_zero_length = length - first_zero_chunk_length;
  if (remaining_zero_length > 0)
    memset(&array_[capacity_ - remaining_zero_length], 0,
           remaining_zero_length * sizeof(int16_t));
  begin_index_ = (begin_index_ + capacity_ - length) % capacity_;

  if (position > 0)
    PushFront(temp_array.get(), position);
}

}  // namespace webrtc
