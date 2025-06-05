/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/sync_buffer.h"

#include "rtc_base/numerics/safe_conversions.h"
#include "test/gtest.h"

namespace webrtc {

TEST(SyncBuffer, CreateAndDestroy) {
  // Create a SyncBuffer with two channels and 10 samples each.
  static const size_t kLen = 10;
  static const size_t kChannels = 2;
  SyncBuffer sync_buffer(kChannels, kLen);
  EXPECT_EQ(kChannels, sync_buffer.Channels());
  EXPECT_EQ(kLen, sync_buffer.Size());
  // When the buffer is empty, the next index to play out is at the end.
  EXPECT_EQ(kLen, sync_buffer.next_index());
  // Verify that all elements are zero.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kLen; ++i) {
      EXPECT_EQ(0, sync_buffer[channel][i]);
    }
  }
}

TEST(SyncBuffer, SetNextIndex) {
  // Create a SyncBuffer with two channels and 100 samples each.
  static const size_t kLen = 100;
  static const size_t kChannels = 2;
  SyncBuffer sync_buffer(kChannels, kLen);
  sync_buffer.set_next_index(0);
  EXPECT_EQ(0u, sync_buffer.next_index());
  sync_buffer.set_next_index(kLen / 2);
  EXPECT_EQ(kLen / 2, sync_buffer.next_index());
  sync_buffer.set_next_index(kLen);
  EXPECT_EQ(kLen, sync_buffer.next_index());
  // Try to set larger than the buffer size; should cap at buffer size.
  sync_buffer.set_next_index(kLen + 1);
  EXPECT_EQ(kLen, sync_buffer.next_index());
}

TEST(SyncBuffer, PushBackAndFlush) {
  // Create a SyncBuffer with two channels and 100 samples each.
  static const size_t kLen = 100;
  static const size_t kChannels = 2;
  SyncBuffer sync_buffer(kChannels, kLen);
  static const size_t kNewLen = 10;
  AudioMultiVector new_data(kChannels, kNewLen);
  // Populate `new_data`.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kNewLen; ++i) {
      new_data[channel][i] = rtc::checked_cast<int16_t>(i);
    }
  }
  // Push back `new_data` into `sync_buffer`. This operation should pop out
  // data from the front of `sync_buffer`, so that the size of the buffer
  // remains the same. The `next_index_` should also move with the same length.
  sync_buffer.PushBack(new_data);
  ASSERT_EQ(kLen, sync_buffer.Size());
  // Verify that `next_index_` moved accordingly.
  EXPECT_EQ(kLen - kNewLen, sync_buffer.next_index());
  // Verify the new contents.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kNewLen; ++i) {
      EXPECT_EQ(new_data[channel][i],
                sync_buffer[channel][sync_buffer.next_index() + i]);
    }
  }

  // Now flush the buffer, and verify that it is all zeros, and that next_index
  // points to the end.
  sync_buffer.Flush();
  ASSERT_EQ(kLen, sync_buffer.Size());
  EXPECT_EQ(kLen, sync_buffer.next_index());
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kLen; ++i) {
      EXPECT_EQ(0, sync_buffer[channel][i]);
    }
  }
}

TEST(SyncBuffer, PushFrontZeros) {
  // Create a SyncBuffer with two channels and 100 samples each.
  static const size_t kLen = 100;
  static const size_t kChannels = 2;
  SyncBuffer sync_buffer(kChannels, kLen);
  static const size_t kNewLen = 10;
  AudioMultiVector new_data(kChannels, kNewLen);
  // Populate `new_data`.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kNewLen; ++i) {
      new_data[channel][i] = rtc::checked_cast<int16_t>(1000 + i);
    }
  }
  sync_buffer.PushBack(new_data);
  EXPECT_EQ(kLen, sync_buffer.Size());

  // Push `kNewLen` - 1 zeros into each channel in the front of the SyncBuffer.
  sync_buffer.PushFrontZeros(kNewLen - 1);
  EXPECT_EQ(kLen, sync_buffer.Size());  // Size should remain the same.
  // Verify that `next_index_` moved accordingly. Should be at the end - 1.
  EXPECT_EQ(kLen - 1, sync_buffer.next_index());
  // Verify the zeros.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kNewLen - 1; ++i) {
      EXPECT_EQ(0, sync_buffer[channel][i]);
    }
  }
  // Verify that the correct data is at the end of the SyncBuffer.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    EXPECT_EQ(1000, sync_buffer[channel][sync_buffer.next_index()]);
  }
}

TEST(SyncBuffer, GetNextAudioInterleaved) {
  // Create a SyncBuffer with two channels and 100 samples each.
  static const size_t kLen = 100;
  static const size_t kChannels = 2;
  SyncBuffer sync_buffer(kChannels, kLen);
  static const size_t kNewLen = 10;
  AudioMultiVector new_data(kChannels, kNewLen);
  // Populate `new_data`.
  for (size_t channel = 0; channel < kChannels; ++channel) {
    for (size_t i = 0; i < kNewLen; ++i) {
      new_data[channel][i] = rtc::checked_cast<int16_t>(i);
    }
  }
  // Push back `new_data` into `sync_buffer`. This operation should pop out
  // data from the front of `sync_buffer`, so that the size of the buffer
  // remains the same. The `next_index_` should also move with the same length.
  sync_buffer.PushBack(new_data);

  // Read to interleaved output. Read in two batches, where each read operation
  // should automatically update the `net_index_` in the SyncBuffer.
  // Note that `samples_read` is the number of samples read from each channel.
  // That is, the number of samples written to `output` is
  // `samples_read` * `kChannels`.
  AudioFrame output1;
  sync_buffer.GetNextAudioInterleaved(kNewLen / 2, &output1);
  EXPECT_EQ(kChannels, output1.num_channels_);
  EXPECT_EQ(kNewLen / 2, output1.samples_per_channel_);

  AudioFrame output2;
  sync_buffer.GetNextAudioInterleaved(kNewLen / 2, &output2);
  EXPECT_EQ(kChannels, output2.num_channels_);
  EXPECT_EQ(kNewLen / 2, output2.samples_per_channel_);

  // Verify the data.
  const int16_t* output_ptr = output1.data();
  for (size_t i = 0; i < kNewLen / 2; ++i) {
    for (size_t channel = 0; channel < kChannels; ++channel) {
      EXPECT_EQ(new_data[channel][i], *output_ptr);
      ++output_ptr;
    }
  }
  output_ptr = output2.data();
  for (size_t i = kNewLen / 2; i < kNewLen; ++i) {
    for (size_t channel = 0; channel < kChannels; ++channel) {
      EXPECT_EQ(new_data[channel][i], *output_ptr);
      ++output_ptr;
    }
  }
}

}  // namespace webrtc
