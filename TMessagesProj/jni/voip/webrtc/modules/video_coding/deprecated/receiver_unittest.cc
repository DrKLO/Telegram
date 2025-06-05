/*  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/receiver.h"

#include <string.h>

#include <cstdint>
#include <memory>
#include <queue>
#include <vector>

#include "modules/video_coding/deprecated/jitter_buffer_common.h"
#include "modules/video_coding/deprecated/packet.h"
#include "modules/video_coding/deprecated/stream_generator.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/timing/timing.h"
#include "rtc_base/checks.h"
#include "system_wrappers/include/clock.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

namespace webrtc {

class TestVCMReceiver : public ::testing::Test {
 protected:
  TestVCMReceiver()
      : clock_(0),
        timing_(&clock_, field_trials_),
        receiver_(&timing_, &clock_, field_trials_),
        stream_generator_(0, clock_.TimeInMilliseconds()) {}

  int32_t InsertPacket(int index) {
    VCMPacket packet;
    bool packet_available = stream_generator_.GetPacket(&packet, index);
    EXPECT_TRUE(packet_available);
    if (!packet_available)
      return kGeneralError;  // Return here to avoid crashes below.
    return receiver_.InsertPacket(packet);
  }

  int32_t InsertPacketAndPop(int index) {
    VCMPacket packet;
    bool packet_available = stream_generator_.PopPacket(&packet, index);
    EXPECT_TRUE(packet_available);
    if (!packet_available)
      return kGeneralError;  // Return here to avoid crashes below.
    return receiver_.InsertPacket(packet);
  }

  int32_t InsertFrame(VideoFrameType frame_type, bool complete) {
    int num_of_packets = complete ? 1 : 2;
    stream_generator_.GenerateFrame(
        frame_type,
        (frame_type != VideoFrameType::kEmptyFrame) ? num_of_packets : 0,
        (frame_type == VideoFrameType::kEmptyFrame) ? 1 : 0,
        clock_.TimeInMilliseconds());
    int32_t ret = InsertPacketAndPop(0);
    if (!complete) {
      // Drop the second packet.
      VCMPacket packet;
      stream_generator_.PopPacket(&packet, 0);
    }
    clock_.AdvanceTimeMilliseconds(kDefaultFramePeriodMs);
    return ret;
  }

  bool DecodeNextFrame() {
    VCMEncodedFrame* frame = receiver_.FrameForDecoding(0, false);
    if (!frame)
      return false;
    receiver_.ReleaseFrame(frame);
    return true;
  }

  test::ScopedKeyValueConfig field_trials_;
  SimulatedClock clock_;
  VCMTiming timing_;
  VCMReceiver receiver_;
  StreamGenerator stream_generator_;
};

TEST_F(TestVCMReceiver, NonDecodableDuration_Empty) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  const int kMinDelayMs = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Advance time until it's time to decode the key frame.
  clock_.AdvanceTimeMilliseconds(kMinDelayMs);
  EXPECT_TRUE(DecodeNextFrame());
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_FALSE(request_key_frame);
}

TEST_F(TestVCMReceiver, NonDecodableDuration_NoKeyFrame) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  const int kNumFrames = kDefaultFrameRate * kMaxNonDecodableDuration / 1000;
  for (int i = 0; i < kNumFrames; ++i) {
    EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, true), kNoError);
  }
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_TRUE(request_key_frame);
}

TEST_F(TestVCMReceiver, NonDecodableDuration_OneIncomplete) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  const int kMaxNonDecodableDurationFrames =
      (kDefaultFrameRate * kMaxNonDecodableDuration + 500) / 1000;
  const int kMinDelayMs = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  timing_.set_min_playout_delay(TimeDelta::Millis(kMinDelayMs));
  int64_t key_frame_inserted = clock_.TimeInMilliseconds();
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Insert an incomplete frame.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, false), kNoError);
  // Insert enough frames to have too long non-decodable sequence.
  for (int i = 0; i < kMaxNonDecodableDurationFrames; ++i) {
    EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, true), kNoError);
  }
  // Advance time until it's time to decode the key frame.
  clock_.AdvanceTimeMilliseconds(kMinDelayMs - clock_.TimeInMilliseconds() -
                                 key_frame_inserted);
  EXPECT_TRUE(DecodeNextFrame());
  // Make sure we get a key frame request.
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_TRUE(request_key_frame);
}

TEST_F(TestVCMReceiver, NonDecodableDuration_NoTrigger) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  const int kMaxNonDecodableDurationFrames =
      (kDefaultFrameRate * kMaxNonDecodableDuration + 500) / 1000;
  const int kMinDelayMs = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  timing_.set_min_playout_delay(TimeDelta::Millis(kMinDelayMs));
  int64_t key_frame_inserted = clock_.TimeInMilliseconds();
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Insert an incomplete frame.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, false), kNoError);
  // Insert all but one frame to not trigger a key frame request due to
  // too long duration of non-decodable frames.
  for (int i = 0; i < kMaxNonDecodableDurationFrames - 1; ++i) {
    EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, true), kNoError);
  }
  // Advance time until it's time to decode the key frame.
  clock_.AdvanceTimeMilliseconds(kMinDelayMs - clock_.TimeInMilliseconds() -
                                 key_frame_inserted);
  EXPECT_TRUE(DecodeNextFrame());
  // Make sure we don't get a key frame request since we haven't generated
  // enough frames.
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_FALSE(request_key_frame);
}

TEST_F(TestVCMReceiver, NonDecodableDuration_NoTrigger2) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  const int kMaxNonDecodableDurationFrames =
      (kDefaultFrameRate * kMaxNonDecodableDuration + 500) / 1000;
  const int kMinDelayMs = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  timing_.set_min_playout_delay(TimeDelta::Millis(kMinDelayMs));
  int64_t key_frame_inserted = clock_.TimeInMilliseconds();
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Insert enough frames to have too long non-decodable sequence, except that
  // we don't have any losses.
  for (int i = 0; i < kMaxNonDecodableDurationFrames; ++i) {
    EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, true), kNoError);
  }
  // Insert an incomplete frame.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, false), kNoError);
  // Advance time until it's time to decode the key frame.
  clock_.AdvanceTimeMilliseconds(kMinDelayMs - clock_.TimeInMilliseconds() -
                                 key_frame_inserted);
  EXPECT_TRUE(DecodeNextFrame());
  // Make sure we don't get a key frame request since the non-decodable duration
  // is only one frame.
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_FALSE(request_key_frame);
}

TEST_F(TestVCMReceiver, NonDecodableDuration_KeyFrameAfterIncompleteFrames) {
  const size_t kMaxNackListSize = 1000;
  const int kMaxPacketAgeToNack = 1000;
  const int kMaxNonDecodableDuration = 500;
  const int kMaxNonDecodableDurationFrames =
      (kDefaultFrameRate * kMaxNonDecodableDuration + 500) / 1000;
  const int kMinDelayMs = 500;
  receiver_.SetNackSettings(kMaxNackListSize, kMaxPacketAgeToNack,
                            kMaxNonDecodableDuration);
  timing_.set_min_playout_delay(TimeDelta::Millis(kMinDelayMs));
  int64_t key_frame_inserted = clock_.TimeInMilliseconds();
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Insert an incomplete frame.
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, false), kNoError);
  // Insert enough frames to have too long non-decodable sequence.
  for (int i = 0; i < kMaxNonDecodableDurationFrames; ++i) {
    EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameDelta, true), kNoError);
  }
  EXPECT_GE(InsertFrame(VideoFrameType::kVideoFrameKey, true), kNoError);
  // Advance time until it's time to decode the key frame.
  clock_.AdvanceTimeMilliseconds(kMinDelayMs - clock_.TimeInMilliseconds() -
                                 key_frame_inserted);
  EXPECT_TRUE(DecodeNextFrame());
  // Make sure we don't get a key frame request since we have a key frame
  // in the list.
  bool request_key_frame = false;
  std::vector<uint16_t> nack_list = receiver_.NackList(&request_key_frame);
  EXPECT_FALSE(request_key_frame);
}

// A simulated clock, when time elapses, will insert frames into the jitter
// buffer, based on initial settings.
class SimulatedClockWithFrames : public SimulatedClock {
 public:
  SimulatedClockWithFrames(StreamGenerator* stream_generator,
                           VCMReceiver* receiver)
      : SimulatedClock(0),
        stream_generator_(stream_generator),
        receiver_(receiver) {}
  virtual ~SimulatedClockWithFrames() {}

  // If `stop_on_frame` is true and next frame arrives between now and
  // now+`milliseconds`, the clock will be advanced to the arrival time of next
  // frame.
  // Otherwise, the clock will be advanced by `milliseconds`.
  //
  // For both cases, a frame will be inserted into the jitter buffer at the
  // instant when the clock time is timestamps_.front().arrive_time.
  //
  // Return true if some frame arrives between now and now+`milliseconds`.
  bool AdvanceTimeMilliseconds(int64_t milliseconds, bool stop_on_frame) {
    return AdvanceTimeMicroseconds(milliseconds * 1000, stop_on_frame);
  }

  bool AdvanceTimeMicroseconds(int64_t microseconds, bool stop_on_frame) {
    int64_t start_time = TimeInMicroseconds();
    int64_t end_time = start_time + microseconds;
    bool frame_injected = false;
    while (!timestamps_.empty() &&
           timestamps_.front().arrive_time <= end_time) {
      RTC_DCHECK_GE(timestamps_.front().arrive_time, start_time);

      SimulatedClock::AdvanceTimeMicroseconds(timestamps_.front().arrive_time -
                                              TimeInMicroseconds());
      GenerateAndInsertFrame((timestamps_.front().render_time + 500) / 1000);
      timestamps_.pop();
      frame_injected = true;

      if (stop_on_frame)
        return frame_injected;
    }

    if (TimeInMicroseconds() < end_time) {
      SimulatedClock::AdvanceTimeMicroseconds(end_time - TimeInMicroseconds());
    }
    return frame_injected;
  }

  // Input timestamps are in unit Milliseconds.
  // And `arrive_timestamps` must be positive and in increasing order.
  // `arrive_timestamps` determine when we are going to insert frames into the
  // jitter buffer.
  // `render_timestamps` are the timestamps on the frame.
  void SetFrames(const int64_t* arrive_timestamps,
                 const int64_t* render_timestamps,
                 size_t size) {
    int64_t previous_arrive_timestamp = 0;
    for (size_t i = 0; i < size; i++) {
      RTC_CHECK_GE(arrive_timestamps[i], previous_arrive_timestamp);
      timestamps_.push(TimestampPair(arrive_timestamps[i] * 1000,
                                     render_timestamps[i] * 1000));
      previous_arrive_timestamp = arrive_timestamps[i];
    }
  }

 private:
  struct TimestampPair {
    TimestampPair(int64_t arrive_timestamp, int64_t render_timestamp)
        : arrive_time(arrive_timestamp), render_time(render_timestamp) {}

    int64_t arrive_time;
    int64_t render_time;
  };

  void GenerateAndInsertFrame(int64_t render_timestamp_ms) {
    VCMPacket packet;
    stream_generator_->GenerateFrame(VideoFrameType::kVideoFrameKey,
                                     1,  // media packets
                                     0,  // empty packets
                                     render_timestamp_ms);

    bool packet_available = stream_generator_->PopPacket(&packet, 0);
    EXPECT_TRUE(packet_available);
    if (!packet_available)
      return;  // Return here to avoid crashes below.
    receiver_->InsertPacket(packet);
  }

  std::queue<TimestampPair> timestamps_;
  StreamGenerator* stream_generator_;
  VCMReceiver* receiver_;
};

// Use a SimulatedClockWithFrames
// Wait call will do either of these:
// 1. If `stop_on_frame` is true, the clock will be turned to the exact instant
// that the first frame comes and the frame will be inserted into the jitter
// buffer, or the clock will be turned to now + `max_time` if no frame comes in
// the window.
// 2. If `stop_on_frame` is false, the clock will be turn to now + `max_time`,
// and all the frames arriving between now and now + `max_time` will be
// inserted into the jitter buffer.
//
// This is used to simulate the JitterBuffer getting packets from internet as
// time elapses.

class FrameInjectEvent : public EventWrapper {
 public:
  FrameInjectEvent(SimulatedClockWithFrames* clock, bool stop_on_frame)
      : clock_(clock), stop_on_frame_(stop_on_frame) {}

  bool Set() override { return true; }

  EventTypeWrapper Wait(int max_time_ms) override {
    if (clock_->AdvanceTimeMilliseconds(max_time_ms, stop_on_frame_) &&
        stop_on_frame_) {
      return EventTypeWrapper::kEventSignaled;
    } else {
      return EventTypeWrapper::kEventTimeout;
    }
  }

 private:
  SimulatedClockWithFrames* clock_;
  bool stop_on_frame_;
};

class VCMReceiverTimingTest : public ::testing::Test {
 protected:
  VCMReceiverTimingTest()
      : clock_(&stream_generator_, &receiver_),
        stream_generator_(0, clock_.TimeInMilliseconds()),
        timing_(&clock_, field_trials_),
        receiver_(
            &timing_,
            &clock_,
            std::unique_ptr<EventWrapper>(new FrameInjectEvent(&clock_, false)),
            std::unique_ptr<EventWrapper>(new FrameInjectEvent(&clock_, true)),
            field_trials_) {}

  virtual void SetUp() {}

  test::ScopedKeyValueConfig field_trials_;
  SimulatedClockWithFrames clock_;
  StreamGenerator stream_generator_;
  VCMTiming timing_;
  VCMReceiver receiver_;
};

// Test whether VCMReceiver::FrameForDecoding handles parameter
// `max_wait_time_ms` correctly:
// 1. The function execution should never take more than `max_wait_time_ms`.
// 2. If the function exit before now + `max_wait_time_ms`, a frame must be
//    returned.
TEST_F(VCMReceiverTimingTest, FrameForDecoding) {
  const size_t kNumFrames = 100;
  const int kFramePeriod = 40;
  int64_t arrive_timestamps[kNumFrames];
  int64_t render_timestamps[kNumFrames];

  // Construct test samples.
  // render_timestamps are the timestamps stored in the Frame;
  // arrive_timestamps controls when the Frame packet got received.
  for (size_t i = 0; i < kNumFrames; i++) {
    // Preset frame rate to 25Hz.
    // But we add a reasonable deviation to arrive_timestamps to mimic Internet
    // fluctuation.
    arrive_timestamps[i] =
        (i + 1) * kFramePeriod + (i % 10) * ((i % 2) ? 1 : -1);
    render_timestamps[i] = (i + 1) * kFramePeriod;
  }

  clock_.SetFrames(arrive_timestamps, render_timestamps, kNumFrames);

  // Record how many frames we finally get out of the receiver.
  size_t num_frames_return = 0;

  const int64_t kMaxWaitTime = 30;

  // Ideally, we should get all frames that we input in InitializeFrames.
  // In the case that FrameForDecoding kills frames by error, we rely on the
  // build bot to kill the test.
  while (num_frames_return < kNumFrames) {
    int64_t start_time = clock_.TimeInMilliseconds();
    VCMEncodedFrame* frame = receiver_.FrameForDecoding(kMaxWaitTime, false);
    int64_t end_time = clock_.TimeInMilliseconds();

    // In any case the FrameForDecoding should not wait longer than
    // max_wait_time.
    // In the case that we did not get a frame, it should have been waiting for
    // exactly max_wait_time. (By the testing samples we constructed above, we
    // are sure there is no timing error, so the only case it returns with NULL
    // is that it runs out of time.)
    if (frame) {
      receiver_.ReleaseFrame(frame);
      ++num_frames_return;
      EXPECT_GE(kMaxWaitTime, end_time - start_time);
    } else {
      EXPECT_EQ(kMaxWaitTime, end_time - start_time);
    }
  }
}

// Test whether VCMReceiver::FrameForDecoding handles parameter
// `prefer_late_decoding` and `max_wait_time_ms` correctly:
// 1. The function execution should never take more than `max_wait_time_ms`.
// 2. If the function exit before now + `max_wait_time_ms`, a frame must be
//    returned and the end time must be equal to the render timestamp - delay
//    for decoding and rendering.
TEST_F(VCMReceiverTimingTest, FrameForDecodingPreferLateDecoding) {
  const size_t kNumFrames = 100;
  const int kFramePeriod = 40;

  int64_t arrive_timestamps[kNumFrames];
  int64_t render_timestamps[kNumFrames];

  auto timings = timing_.GetTimings();
  TimeDelta render_delay = timings.render_delay;
  TimeDelta max_decode = timings.estimated_max_decode_time;

  // Construct test samples.
  // render_timestamps are the timestamps stored in the Frame;
  // arrive_timestamps controls when the Frame packet got received.
  for (size_t i = 0; i < kNumFrames; i++) {
    // Preset frame rate to 25Hz.
    // But we add a reasonable deviation to arrive_timestamps to mimic Internet
    // fluctuation.
    arrive_timestamps[i] =
        (i + 1) * kFramePeriod + (i % 10) * ((i % 2) ? 1 : -1);
    render_timestamps[i] = (i + 1) * kFramePeriod;
  }

  clock_.SetFrames(arrive_timestamps, render_timestamps, kNumFrames);

  // Record how many frames we finally get out of the receiver.
  size_t num_frames_return = 0;
  const int64_t kMaxWaitTime = 30;
  bool prefer_late_decoding = true;
  while (num_frames_return < kNumFrames) {
    int64_t start_time = clock_.TimeInMilliseconds();

    VCMEncodedFrame* frame =
        receiver_.FrameForDecoding(kMaxWaitTime, prefer_late_decoding);
    int64_t end_time = clock_.TimeInMilliseconds();
    if (frame) {
      EXPECT_EQ(frame->RenderTimeMs() - max_decode.ms() - render_delay.ms(),
                end_time);
      receiver_.ReleaseFrame(frame);
      ++num_frames_return;
    } else {
      EXPECT_EQ(kMaxWaitTime, end_time - start_time);
    }
  }
}

}  // namespace webrtc
