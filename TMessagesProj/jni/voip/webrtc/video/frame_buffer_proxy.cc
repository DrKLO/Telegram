/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/frame_buffer_proxy.h"

#include <algorithm>
#include <memory>
#include <utility>

#include "absl/base/attributes.h"
#include "absl/functional/bind_front.h"
#include "api/sequence_checker.h"
#include "modules/video_coding/frame_buffer2.h"
#include "modules/video_coding/frame_buffer3.h"
#include "modules/video_coding/frame_helpers.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/field_trial.h"
#include "video/frame_decode_timing.h"
#include "video/task_queue_frame_decode_scheduler.h"
#include "video/video_receive_stream_timeout_tracker.h"

namespace webrtc {

namespace {

class FrameBuffer2Proxy : public FrameBufferProxy {
 public:
  FrameBuffer2Proxy(Clock* clock,
                    VCMTiming* timing,
                    VCMReceiveStatisticsCallback* stats_proxy,
                    rtc::TaskQueue* decode_queue,
                    FrameSchedulingReceiver* receiver,
                    TimeDelta max_wait_for_keyframe,
                    TimeDelta max_wait_for_frame)
      : max_wait_for_keyframe_(max_wait_for_keyframe),
        max_wait_for_frame_(max_wait_for_frame),
        frame_buffer_(clock, timing, stats_proxy),
        decode_queue_(decode_queue),
        stats_proxy_(stats_proxy),
        receiver_(receiver) {
    RTC_DCHECK(decode_queue_);
    RTC_DCHECK(stats_proxy_);
    RTC_DCHECK(receiver_);
  }

  void StopOnWorker() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    decode_queue_->PostTask([this] {
      frame_buffer_.Stop();
      decode_safety_->SetNotAlive();
    });
  }

  void SetProtectionMode(VCMVideoProtection protection_mode) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    frame_buffer_.SetProtectionMode(kProtectionNackFEC);
  }

  void Clear() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    frame_buffer_.Clear();
  }

  absl::optional<int64_t> InsertFrame(
      std::unique_ptr<EncodedFrame> frame) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    int64_t last_continuous_pid = frame_buffer_.InsertFrame(std::move(frame));
    if (last_continuous_pid != -1)
      return last_continuous_pid;
    return absl::nullopt;
  }

  void UpdateRtt(int64_t max_rtt_ms) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    frame_buffer_.UpdateRtt(max_rtt_ms);
  }

  void StartNextDecode(bool keyframe_required) override {
    if (!decode_queue_->IsCurrent()) {
      decode_queue_->PostTask(ToQueuedTask(
          decode_safety_,
          [this, keyframe_required] { StartNextDecode(keyframe_required); }));
      return;
    }
    RTC_DCHECK_RUN_ON(decode_queue_);

    frame_buffer_.NextFrame(
        MaxWait(keyframe_required).ms(), keyframe_required, decode_queue_,
        /* encoded frame handler */
        [this, keyframe_required](std::unique_ptr<EncodedFrame> frame) {
          RTC_DCHECK_RUN_ON(decode_queue_);
          if (!decode_safety_->alive())
            return;
          if (frame) {
            receiver_->OnEncodedFrame(std::move(frame));
          } else {
            receiver_->OnDecodableFrameTimeout(MaxWait(keyframe_required));
          }
        });
  }

  int Size() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    return frame_buffer_.Size();
  }

 private:
  TimeDelta MaxWait(bool keyframe_required) const {
    return keyframe_required ? max_wait_for_keyframe_ : max_wait_for_frame_;
  }

  RTC_NO_UNIQUE_ADDRESS SequenceChecker worker_sequence_checker_;
  const TimeDelta max_wait_for_keyframe_;
  const TimeDelta max_wait_for_frame_;
  video_coding::FrameBuffer frame_buffer_;
  rtc::TaskQueue* const decode_queue_;
  VCMReceiveStatisticsCallback* const stats_proxy_;
  FrameSchedulingReceiver* const receiver_;
  rtc::scoped_refptr<PendingTaskSafetyFlag> decode_safety_ =
      PendingTaskSafetyFlag::CreateDetached();
};

// Max number of frames the buffer will hold.
static constexpr size_t kMaxFramesBuffered = 800;
// Max number of decoded frame info that will be saved.
static constexpr int kMaxFramesHistory = 1 << 13;

// Default value for the maximum decode queue size that is used when the
// low-latency renderer is used.
static constexpr size_t kZeroPlayoutDelayDefaultMaxDecodeQueueSize = 8;

// Encapsulates use of the new frame buffer for use in VideoReceiveStream. This
// behaves the same as the FrameBuffer2Proxy but uses frame_buffer3 instead.
// Responsibilities from frame_buffer2, like stats, jitter and frame timing
// accounting are moved into this pro
class FrameBuffer3Proxy : public FrameBufferProxy {
 public:
  FrameBuffer3Proxy(
      Clock* clock,
      TaskQueueBase* worker_queue,
      VCMTiming* timing,
      VCMReceiveStatisticsCallback* stats_proxy,
      rtc::TaskQueue* decode_queue,
      FrameSchedulingReceiver* receiver,
      TimeDelta max_wait_for_keyframe,
      TimeDelta max_wait_for_frame,
      std::unique_ptr<FrameDecodeScheduler> frame_decode_scheduler)
      : max_wait_for_keyframe_(max_wait_for_keyframe),
        max_wait_for_frame_(max_wait_for_frame),
        clock_(clock),
        worker_queue_(worker_queue),
        decode_queue_(decode_queue),
        stats_proxy_(stats_proxy),
        receiver_(receiver),
        timing_(timing),
        frame_decode_scheduler_(std::move(frame_decode_scheduler)),
        jitter_estimator_(clock_),
        inter_frame_delay_(clock_->TimeInMilliseconds()),
        buffer_(std::make_unique<FrameBuffer>(kMaxFramesBuffered,
                                              kMaxFramesHistory)),
        decode_timing_(clock_, timing_),
        timeout_tracker_(clock_,
                         worker_queue_,
                         VideoReceiveStreamTimeoutTracker::Timeouts{
                             .max_wait_for_keyframe = max_wait_for_keyframe,
                             .max_wait_for_frame = max_wait_for_frame},
                         absl::bind_front(&FrameBuffer3Proxy::OnTimeout, this)),
        zero_playout_delay_max_decode_queue_size_(
            "max_decode_queue_size",
            kZeroPlayoutDelayDefaultMaxDecodeQueueSize) {
    RTC_DCHECK(decode_queue_);
    RTC_DCHECK(stats_proxy_);
    RTC_DCHECK(receiver_);
    RTC_DCHECK(timing_);
    RTC_DCHECK(worker_queue_);
    RTC_DCHECK(clock_);
    RTC_DCHECK(frame_decode_scheduler_);
    RTC_LOG(LS_WARNING) << "Using FrameBuffer3";

    ParseFieldTrial({&zero_playout_delay_max_decode_queue_size_},
                    field_trial::FindFullName("WebRTC-ZeroPlayoutDelay"));
  }

  // FrameBufferProxy implementation.
  void StopOnWorker() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    frame_decode_scheduler_->Stop();
    timeout_tracker_.Stop();
    decoder_ready_for_new_frame_ = false;
    decode_queue_->PostTask([this] {
      RTC_DCHECK_RUN_ON(decode_queue_);
      decode_safety_->SetNotAlive();
    });
  }

  void SetProtectionMode(VCMVideoProtection protection_mode) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    protection_mode_ = kProtectionNackFEC;
  }

  void Clear() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    stats_proxy_->OnDroppedFrames(buffer_->CurrentSize());
    buffer_ =
        std::make_unique<FrameBuffer>(kMaxFramesBuffered, kMaxFramesHistory);
    frame_decode_scheduler_->CancelOutstanding();
  }

  absl::optional<int64_t> InsertFrame(
      std::unique_ptr<EncodedFrame> frame) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    if (frame->is_last_spatial_layer)
      stats_proxy_->OnCompleteFrame(frame->is_keyframe(), frame->size(),
                                    frame->contentType());
    if (!frame->delayed_by_retransmission())
      timing_->IncomingTimestamp(frame->Timestamp(), frame->ReceivedTime());

    buffer_->InsertFrame(std::move(frame));
    MaybeScheduleFrameForRelease();

    return buffer_->LastContinuousFrameId();
  }

  void UpdateRtt(int64_t max_rtt_ms) override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    jitter_estimator_.UpdateRtt(max_rtt_ms);
  }

  void StartNextDecode(bool keyframe_required) override {
    if (!worker_queue_->IsCurrent()) {
      worker_queue_->PostTask(ToQueuedTask(
          worker_safety_,
          [this, keyframe_required] { StartNextDecode(keyframe_required); }));
      return;
    }

    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    if (!timeout_tracker_.Running())
      timeout_tracker_.Start(keyframe_required);
    keyframe_required_ = keyframe_required;
    if (keyframe_required_) {
      timeout_tracker_.SetWaitingForKeyframe();
    }
    decoder_ready_for_new_frame_ = true;
    MaybeScheduleFrameForRelease();
  }

  int Size() override {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    return buffer_->CurrentSize();
  }

  void OnFrameReady(
      absl::InlinedVector<std::unique_ptr<EncodedFrame>, 4> frames,
      Timestamp render_time) {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    RTC_DCHECK(!frames.empty());

    timeout_tracker_.OnEncodedFrameReleased();

    int64_t now_ms = clock_->TimeInMilliseconds();
    bool superframe_delayed_by_retransmission = false;
    size_t superframe_size = 0;
    const EncodedFrame& first_frame = *frames.front();
    int64_t receive_time_ms = first_frame.ReceivedTime();

    if (first_frame.is_keyframe())
      keyframe_required_ = false;

    // Gracefully handle bad RTP timestamps and render time issues.
    if (FrameHasBadRenderTiming(render_time.ms(), now_ms,
                                timing_->TargetVideoDelay())) {
      jitter_estimator_.Reset();
      timing_->Reset();
      render_time = Timestamp::Millis(
          timing_->RenderTimeMs(first_frame.Timestamp(), now_ms));
    }

    for (std::unique_ptr<EncodedFrame>& frame : frames) {
      frame->SetRenderTime(render_time.ms());

      superframe_delayed_by_retransmission |=
          frame->delayed_by_retransmission();
      receive_time_ms = std::max(receive_time_ms, frame->ReceivedTime());
      superframe_size += frame->size();
    }

    if (!superframe_delayed_by_retransmission) {
      int64_t frame_delay;

      if (inter_frame_delay_.CalculateDelay(first_frame.Timestamp(),
                                            &frame_delay, receive_time_ms)) {
        jitter_estimator_.UpdateEstimate(frame_delay, superframe_size);
      }

      float rtt_mult = protection_mode_ == kProtectionNackFEC ? 0.0 : 1.0;
      absl::optional<float> rtt_mult_add_cap_ms = absl::nullopt;
      if (rtt_mult_settings_.has_value()) {
        rtt_mult = rtt_mult_settings_->rtt_mult_setting;
        rtt_mult_add_cap_ms = rtt_mult_settings_->rtt_mult_add_cap_ms;
      }
      timing_->SetJitterDelay(
          jitter_estimator_.GetJitterEstimate(rtt_mult, rtt_mult_add_cap_ms));
      timing_->UpdateCurrentDelay(render_time.ms(), now_ms);
    } else if (RttMultExperiment::RttMultEnabled()) {
      jitter_estimator_.FrameNacked();
    }

    // Update stats.
    UpdateDroppedFrames();
    UpdateJitterDelay();
    UpdateTimingFrameInfo();

    std::unique_ptr<EncodedFrame> frame =
        CombineAndDeleteFrames(std::move(frames));

    timing_->SetLastDecodeScheduledTimestamp(now_ms);

    decoder_ready_for_new_frame_ = false;
    // VideoReceiveStream2 wants frames on the decoder thread.
    decode_queue_->PostTask(ToQueuedTask(
        decode_safety_, [this, frame = std::move(frame)]() mutable {
          receiver_->OnEncodedFrame(std::move(frame));
        }));
  }

  void OnTimeout() {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    // If the stream is paused then ignore the timeout.
    if (!decoder_ready_for_new_frame_) {
      timeout_tracker_.Stop();
      return;
    }
    receiver_->OnDecodableFrameTimeout(MaxWait());
    // Stop sending timeouts until receive starts waiting for a new frame.
    timeout_tracker_.Stop();
    decoder_ready_for_new_frame_ = false;
  }

 private:
  void FrameReadyForDecode(uint32_t rtp_timestamp, Timestamp render_time) {
    RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
    RTC_DCHECK(buffer_->NextDecodableTemporalUnitRtpTimestamp() ==
               rtp_timestamp)
        << "Frame buffer's next decodable frame was not the one sent for "
           "extraction rtp="
        << rtp_timestamp << " next="
        << buffer_->NextDecodableTemporalUnitRtpTimestamp().value_or(-1);
    auto frames = buffer_->ExtractNextDecodableTemporalUnit();
    OnFrameReady(std::move(frames), render_time);
  }

  TimeDelta MaxWait() const RTC_RUN_ON(&worker_sequence_checker_) {
    return keyframe_required_ ? max_wait_for_keyframe_ : max_wait_for_frame_;
  }

  void UpdateDroppedFrames() RTC_RUN_ON(&worker_sequence_checker_) {
    const int dropped_frames = buffer_->GetTotalNumberOfDroppedFrames() -
                               frames_dropped_before_last_new_frame_;
    if (dropped_frames > 0)
      stats_proxy_->OnDroppedFrames(dropped_frames);
    frames_dropped_before_last_new_frame_ =
        buffer_->GetTotalNumberOfDroppedFrames();
  }

  void UpdateJitterDelay() {
    int max_decode_ms;
    int current_delay_ms;
    int target_delay_ms;
    int jitter_buffer_ms;
    int min_playout_delay_ms;
    int render_delay_ms;
    if (timing_->GetTimings(&max_decode_ms, &current_delay_ms, &target_delay_ms,
                            &jitter_buffer_ms, &min_playout_delay_ms,
                            &render_delay_ms)) {
      stats_proxy_->OnFrameBufferTimingsUpdated(
          max_decode_ms, current_delay_ms, target_delay_ms, jitter_buffer_ms,
          min_playout_delay_ms, render_delay_ms);
    }
  }

  void UpdateTimingFrameInfo() {
    absl::optional<TimingFrameInfo> info = timing_->GetTimingFrameInfo();
    if (info)
      stats_proxy_->OnTimingFrameInfoUpdated(*info);
  }

  bool IsTooManyFramesQueued() const RTC_RUN_ON(&worker_sequence_checker_) {
    return buffer_->CurrentSize() > zero_playout_delay_max_decode_queue_size_;
  }

  void ForceKeyFrameReleaseImmediately() RTC_RUN_ON(&worker_sequence_checker_) {
    RTC_DCHECK(keyframe_required_);
    // Iterate through the frame buffer until there is a complete keyframe and
    // release this right away.
    while (buffer_->NextDecodableTemporalUnitRtpTimestamp()) {
      auto next_frame = buffer_->ExtractNextDecodableTemporalUnit();
      if (next_frame.empty()) {
        RTC_DCHECK_NOTREACHED()
            << "Frame buffer should always return at least 1 frame.";
        continue;
      }
      // Found keyframe - decode right away.
      if (next_frame.front()->is_keyframe()) {
        auto render_time = Timestamp::Millis(timing_->RenderTimeMs(
            next_frame.front()->Timestamp(), clock_->TimeInMilliseconds()));
        OnFrameReady(std::move(next_frame), render_time);
        return;
      }
    }
  }

  void MaybeScheduleFrameForRelease() RTC_RUN_ON(&worker_sequence_checker_) {
    if (!decoder_ready_for_new_frame_ ||
        !buffer_->NextDecodableTemporalUnitRtpTimestamp())
      return;

    if (keyframe_required_) {
      return ForceKeyFrameReleaseImmediately();
    }

    // TODO(https://bugs.webrtc.org/13343): Make [next,last] decodable returned
    // as an optional pair and remove this check.
    RTC_CHECK(buffer_->LastDecodableTemporalUnitRtpTimestamp());
    auto last_rtp = *buffer_->LastDecodableTemporalUnitRtpTimestamp();

    // If already scheduled then abort.
    if (frame_decode_scheduler_->ScheduledRtpTimestamp() ==
        buffer_->NextDecodableTemporalUnitRtpTimestamp())
      return;

    absl::optional<FrameDecodeTiming::FrameSchedule> schedule;
    while (buffer_->NextDecodableTemporalUnitRtpTimestamp()) {
      auto next_rtp = *buffer_->NextDecodableTemporalUnitRtpTimestamp();
      schedule = decode_timing_.OnFrameBufferUpdated(next_rtp, last_rtp,
                                                     IsTooManyFramesQueued());
      if (schedule) {
        // Don't schedule if already waiting for the same frame.
        if (frame_decode_scheduler_->ScheduledRtpTimestamp() != next_rtp) {
          frame_decode_scheduler_->CancelOutstanding();
          frame_decode_scheduler_->ScheduleFrame(
              next_rtp, *schedule,
              absl::bind_front(&FrameBuffer3Proxy::FrameReadyForDecode, this));
        }
        return;
      }
      // If no schedule for current rtp, drop and try again.
      buffer_->DropNextDecodableTemporalUnit();
    }
  }

  RTC_NO_UNIQUE_ADDRESS SequenceChecker worker_sequence_checker_;
  const TimeDelta max_wait_for_keyframe_;
  const TimeDelta max_wait_for_frame_;
  const absl::optional<RttMultExperiment::Settings> rtt_mult_settings_ =
      RttMultExperiment::GetRttMultValue();
  Clock* const clock_;
  TaskQueueBase* const worker_queue_;
  rtc::TaskQueue* const decode_queue_;
  VCMReceiveStatisticsCallback* const stats_proxy_;
  FrameSchedulingReceiver* const receiver_;
  VCMTiming* const timing_;
  const std::unique_ptr<FrameDecodeScheduler> frame_decode_scheduler_
      RTC_GUARDED_BY(&worker_sequence_checker_);

  VCMJitterEstimator jitter_estimator_
      RTC_GUARDED_BY(&worker_sequence_checker_);
  VCMInterFrameDelay inter_frame_delay_
      RTC_GUARDED_BY(&worker_sequence_checker_);
  bool keyframe_required_ RTC_GUARDED_BY(&worker_sequence_checker_) = false;
  std::unique_ptr<FrameBuffer> buffer_
      RTC_GUARDED_BY(&worker_sequence_checker_);
  FrameDecodeTiming decode_timing_ RTC_GUARDED_BY(&worker_sequence_checker_);
  VideoReceiveStreamTimeoutTracker timeout_tracker_
      RTC_GUARDED_BY(&worker_sequence_checker_);
  int frames_dropped_before_last_new_frame_
      RTC_GUARDED_BY(&worker_sequence_checker_) = 0;
  VCMVideoProtection protection_mode_
      RTC_GUARDED_BY(&worker_sequence_checker_) = kProtectionNack;

  // This flag guards frames from queuing in front of the decoder. Without this
  // guard, encoded frames will not wait for the decoder to finish decoding a
  // frame and just queue up, meaning frames will not be dropped or
  // fast-forwarded when the decoder is slow or hangs.
  bool decoder_ready_for_new_frame_ RTC_GUARDED_BY(&worker_sequence_checker_) =
      false;

  // Maximum number of frames in the decode queue to allow pacing. If the
  // queue grows beyond the max limit, pacing will be disabled and frames will
  // be pushed to the decoder as soon as possible. This only has an effect
  // when the low-latency rendering path is active, which is indicated by
  // the frame's render time == 0.
  FieldTrialParameter<unsigned> zero_playout_delay_max_decode_queue_size_;

  rtc::scoped_refptr<PendingTaskSafetyFlag> decode_safety_ =
      PendingTaskSafetyFlag::CreateDetached();
  ScopedTaskSafety worker_safety_;
};

enum class FrameBufferArm {
  kFrameBuffer2,
  kFrameBuffer3,
  kSyncDecode,
};

constexpr const char* kFrameBufferFieldTrial = "WebRTC-FrameBuffer3";

FrameBufferArm ParseFrameBufferFieldTrial() {
  webrtc::FieldTrialEnum<FrameBufferArm> arm(
      "arm", FrameBufferArm::kFrameBuffer2,
      {
          {"FrameBuffer2", FrameBufferArm::kFrameBuffer2},
          {"FrameBuffer3", FrameBufferArm::kFrameBuffer3},
          {"SyncDecoding", FrameBufferArm::kSyncDecode},
      });
  ParseFieldTrial({&arm}, field_trial::FindFullName(kFrameBufferFieldTrial));
  return arm.Get();
}

}  // namespace

std::unique_ptr<FrameBufferProxy> FrameBufferProxy::CreateFromFieldTrial(
    Clock* clock,
    TaskQueueBase* worker_queue,
    VCMTiming* timing,
    VCMReceiveStatisticsCallback* stats_proxy,
    rtc::TaskQueue* decode_queue,
    FrameSchedulingReceiver* receiver,
    TimeDelta max_wait_for_keyframe,
    TimeDelta max_wait_for_frame,
    DecodeSynchronizer* decode_sync) {
  switch (ParseFrameBufferFieldTrial()) {
    case FrameBufferArm::kFrameBuffer3: {
      auto scheduler =
          std::make_unique<TaskQueueFrameDecodeScheduler>(clock, worker_queue);
      return std::make_unique<FrameBuffer3Proxy>(
          clock, worker_queue, timing, stats_proxy, decode_queue, receiver,
          max_wait_for_keyframe, max_wait_for_frame, std::move(scheduler));
    }
    case FrameBufferArm::kSyncDecode: {
      std::unique_ptr<FrameDecodeScheduler> scheduler;
      if (decode_sync) {
        scheduler = decode_sync->CreateSynchronizedFrameScheduler();
      } else {
        RTC_LOG(LS_ERROR) << "In FrameBuffer with sync decode trial, but "
                             "no DecodeSynchronizer was present!";
        // Crash in debug, but in production use the task queue scheduler.
        RTC_DCHECK_NOTREACHED();
        scheduler = std::make_unique<TaskQueueFrameDecodeScheduler>(
            clock, worker_queue);
      }
      return std::make_unique<FrameBuffer3Proxy>(
          clock, worker_queue, timing, stats_proxy, decode_queue, receiver,
          max_wait_for_keyframe, max_wait_for_frame, std::move(scheduler));
    }
    case FrameBufferArm::kFrameBuffer2:
      ABSL_FALLTHROUGH_INTENDED;
    default:
      return std::make_unique<FrameBuffer2Proxy>(
          clock, timing, stats_proxy, decode_queue, receiver,
          max_wait_for_keyframe, max_wait_for_frame);
  }
}

}  // namespace webrtc
