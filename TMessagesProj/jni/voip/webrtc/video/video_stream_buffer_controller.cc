/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/video_stream_buffer_controller.h"

#include <algorithm>
#include <memory>
#include <utility>

#include "absl/base/attributes.h"
#include "absl/functional/bind_front.h"
#include "absl/types/optional.h"
#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/data_size.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/encoded_frame.h"
#include "api/video/frame_buffer.h"
#include "api/video/video_content_type.h"
#include "modules/video_coding/frame_helpers.h"
#include "modules/video_coding/timing/inter_frame_delay_variation_calculator.h"
#include "modules/video_coding/timing/jitter_estimator.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/thread_annotations.h"
#include "video/frame_decode_scheduler.h"
#include "video/frame_decode_timing.h"
#include "video/task_queue_frame_decode_scheduler.h"
#include "video/video_receive_stream_timeout_tracker.h"

namespace webrtc {

namespace {

// Max number of frames the buffer will hold.
static constexpr size_t kMaxFramesBuffered = 800;
// Max number of decoded frame info that will be saved.
static constexpr int kMaxFramesHistory = 1 << 13;

// Default value for the maximum decode queue size that is used when the
// low-latency renderer is used.
static constexpr size_t kZeroPlayoutDelayDefaultMaxDecodeQueueSize = 8;

struct FrameMetadata {
  explicit FrameMetadata(const EncodedFrame& frame)
      : is_last_spatial_layer(frame.is_last_spatial_layer),
        is_keyframe(frame.is_keyframe()),
        size(frame.size()),
        contentType(frame.contentType()),
        delayed_by_retransmission(frame.delayed_by_retransmission()),
        rtp_timestamp(frame.RtpTimestamp()),
        receive_time(frame.ReceivedTimestamp()) {}

  const bool is_last_spatial_layer;
  const bool is_keyframe;
  const size_t size;
  const VideoContentType contentType;
  const bool delayed_by_retransmission;
  const uint32_t rtp_timestamp;
  const absl::optional<Timestamp> receive_time;
};

Timestamp MinReceiveTime(const EncodedFrame& frame) {
  Timestamp first_recv_time = Timestamp::PlusInfinity();
  for (const auto& packet_info : frame.PacketInfos()) {
    if (packet_info.receive_time().IsFinite()) {
      first_recv_time = std::min(first_recv_time, packet_info.receive_time());
    }
  }
  return first_recv_time;
}

Timestamp ReceiveTime(const EncodedFrame& frame) {
  absl::optional<Timestamp> ts = frame.ReceivedTimestamp();
  RTC_DCHECK(ts.has_value()) << "Received frame must have a timestamp set!";
  return *ts;
}

}  // namespace

VideoStreamBufferController::VideoStreamBufferController(
    Clock* clock,
    TaskQueueBase* worker_queue,
    VCMTiming* timing,
    VideoStreamBufferControllerStatsObserver* stats_proxy,
    FrameSchedulingReceiver* receiver,
    TimeDelta max_wait_for_keyframe,
    TimeDelta max_wait_for_frame,
    std::unique_ptr<FrameDecodeScheduler> frame_decode_scheduler,
    const FieldTrialsView& field_trials)
    : field_trials_(field_trials),
      clock_(clock),
      stats_proxy_(stats_proxy),
      receiver_(receiver),
      timing_(timing),
      frame_decode_scheduler_(std::move(frame_decode_scheduler)),
      jitter_estimator_(clock_, field_trials),
      buffer_(std::make_unique<FrameBuffer>(kMaxFramesBuffered,
                                            kMaxFramesHistory,
                                            field_trials)),
      decode_timing_(clock_, timing_),
      timeout_tracker_(
          clock_,
          worker_queue,
          VideoReceiveStreamTimeoutTracker::Timeouts{
              .max_wait_for_keyframe = max_wait_for_keyframe,
              .max_wait_for_frame = max_wait_for_frame},
          absl::bind_front(&VideoStreamBufferController::OnTimeout, this)),
      zero_playout_delay_max_decode_queue_size_(
          "max_decode_queue_size",
          kZeroPlayoutDelayDefaultMaxDecodeQueueSize) {
  RTC_DCHECK(stats_proxy_);
  RTC_DCHECK(receiver_);
  RTC_DCHECK(timing_);
  RTC_DCHECK(clock_);
  RTC_DCHECK(frame_decode_scheduler_);

  ParseFieldTrial({&zero_playout_delay_max_decode_queue_size_},
                  field_trials.Lookup("WebRTC-ZeroPlayoutDelay"));
}

void VideoStreamBufferController::Stop() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  frame_decode_scheduler_->Stop();
  timeout_tracker_.Stop();
  decoder_ready_for_new_frame_ = false;
}

void VideoStreamBufferController::SetProtectionMode(
    VCMVideoProtection protection_mode) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  protection_mode_ = protection_mode;
}

void VideoStreamBufferController::Clear() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  stats_proxy_->OnDroppedFrames(buffer_->CurrentSize());
  buffer_ = std::make_unique<FrameBuffer>(kMaxFramesBuffered, kMaxFramesHistory,
                                          field_trials_);
  frame_decode_scheduler_->CancelOutstanding();
}

absl::optional<int64_t> VideoStreamBufferController::InsertFrame(
    std::unique_ptr<EncodedFrame> frame) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  FrameMetadata metadata(*frame);
  int complete_units = buffer_->GetTotalNumberOfContinuousTemporalUnits();
  if (buffer_->InsertFrame(std::move(frame))) {
    RTC_DCHECK(metadata.receive_time) << "Frame receive time must be set!";
    if (!metadata.delayed_by_retransmission && metadata.receive_time &&
        (field_trials_.IsDisabled("WebRTC-IncomingTimestampOnMarkerBitOnly") ||
         metadata.is_last_spatial_layer)) {
      timing_->IncomingTimestamp(metadata.rtp_timestamp,
                                 *metadata.receive_time);
    }
    if (complete_units < buffer_->GetTotalNumberOfContinuousTemporalUnits()) {
      stats_proxy_->OnCompleteFrame(metadata.is_keyframe, metadata.size,
                                    metadata.contentType);
      MaybeScheduleFrameForRelease();
    }
  }

  return buffer_->LastContinuousFrameId();
}

void VideoStreamBufferController::UpdateRtt(int64_t max_rtt_ms) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  jitter_estimator_.UpdateRtt(TimeDelta::Millis(max_rtt_ms));
}

void VideoStreamBufferController::SetMaxWaits(TimeDelta max_wait_for_keyframe,
                                              TimeDelta max_wait_for_frame) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  timeout_tracker_.SetTimeouts({.max_wait_for_keyframe = max_wait_for_keyframe,
                                .max_wait_for_frame = max_wait_for_frame});
}

void VideoStreamBufferController::StartNextDecode(bool keyframe_required) {
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

int VideoStreamBufferController::Size() {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  return buffer_->CurrentSize();
}

void VideoStreamBufferController::OnFrameReady(
    absl::InlinedVector<std::unique_ptr<EncodedFrame>, 4> frames,
    Timestamp render_time) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  RTC_CHECK(!frames.empty())
      << "Callers must ensure there is at least one frame to decode.";

  timeout_tracker_.OnEncodedFrameReleased();

  Timestamp now = clock_->CurrentTime();
  bool superframe_delayed_by_retransmission = false;
  DataSize superframe_size = DataSize::Zero();
  const EncodedFrame& first_frame = *frames.front();
  Timestamp min_receive_time = MinReceiveTime(first_frame);
  Timestamp max_receive_time = ReceiveTime(first_frame);

  if (first_frame.is_keyframe())
    keyframe_required_ = false;

  // Gracefully handle bad RTP timestamps and render time issues.
  if (FrameHasBadRenderTiming(render_time, now) ||
      TargetVideoDelayIsTooLarge(timing_->TargetVideoDelay())) {
    RTC_LOG(LS_WARNING) << "Resetting jitter estimator and timing module due "
                           "to bad render timing for rtp_timestamp="
                        << first_frame.RtpTimestamp();
    jitter_estimator_.Reset();
    timing_->Reset();
    render_time = timing_->RenderTime(first_frame.RtpTimestamp(), now);
  }

  for (std::unique_ptr<EncodedFrame>& frame : frames) {
    frame->SetRenderTime(render_time.ms());

    superframe_delayed_by_retransmission |= frame->delayed_by_retransmission();
    min_receive_time = std::min(min_receive_time, MinReceiveTime(*frame));
    max_receive_time = std::max(max_receive_time, ReceiveTime(*frame));
    superframe_size += DataSize::Bytes(frame->size());
  }

  if (!superframe_delayed_by_retransmission) {
    absl::optional<TimeDelta> inter_frame_delay_variation =
        ifdv_calculator_.Calculate(first_frame.RtpTimestamp(),
                                   max_receive_time);
    if (inter_frame_delay_variation) {
      jitter_estimator_.UpdateEstimate(*inter_frame_delay_variation,
                                       superframe_size);
    }

    float rtt_mult = protection_mode_ == kProtectionNackFEC ? 0.0 : 1.0;
    absl::optional<TimeDelta> rtt_mult_add_cap_ms = absl::nullopt;
    if (rtt_mult_settings_.has_value()) {
      rtt_mult = rtt_mult_settings_->rtt_mult_setting;
      rtt_mult_add_cap_ms =
          TimeDelta::Millis(rtt_mult_settings_->rtt_mult_add_cap_ms);
    }
    timing_->SetJitterDelay(
        jitter_estimator_.GetJitterEstimate(rtt_mult, rtt_mult_add_cap_ms));
    timing_->UpdateCurrentDelay(render_time, now);
  } else if (RttMultExperiment::RttMultEnabled()) {
    jitter_estimator_.FrameNacked();
  }

  // Update stats.
  UpdateDroppedFrames();
  UpdateFrameBufferTimings(min_receive_time, now);
  UpdateTimingFrameInfo();

  std::unique_ptr<EncodedFrame> frame =
      CombineAndDeleteFrames(std::move(frames));

  timing_->SetLastDecodeScheduledTimestamp(now);

  decoder_ready_for_new_frame_ = false;
  receiver_->OnEncodedFrame(std::move(frame));
}

void VideoStreamBufferController::OnTimeout(TimeDelta delay) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);

  // Stop sending timeouts until receiver starts waiting for a new frame.
  timeout_tracker_.Stop();

  // If the stream is paused then ignore the timeout.
  if (!decoder_ready_for_new_frame_) {
    return;
  }
  decoder_ready_for_new_frame_ = false;
  receiver_->OnDecodableFrameTimeout(delay);
}

void VideoStreamBufferController::FrameReadyForDecode(uint32_t rtp_timestamp,
                                                      Timestamp render_time) {
  RTC_DCHECK_RUN_ON(&worker_sequence_checker_);
  // Check that the frame to decode is still valid before passing the frame for
  // decoding.
  auto decodable_tu_info = buffer_->DecodableTemporalUnitsInfo();
  if (!decodable_tu_info) {
    RTC_LOG(LS_ERROR)
        << "The frame buffer became undecodable during the wait "
           "to decode frame with rtp-timestamp "
        << rtp_timestamp
        << ". Cancelling the decode of this frame, decoding "
           "will resume when the frame buffers become decodable again.";
    return;
  }
  RTC_DCHECK_EQ(rtp_timestamp, decodable_tu_info->next_rtp_timestamp)
      << "Frame buffer's next decodable frame was not the one sent for "
         "extraction.";
  auto frames = buffer_->ExtractNextDecodableTemporalUnit();
  if (frames.empty()) {
    RTC_LOG(LS_ERROR)
        << "The frame buffer should never return an empty temporal until list "
           "when there is a decodable temporal unit.";
    RTC_DCHECK_NOTREACHED();
    return;
  }
  OnFrameReady(std::move(frames), render_time);
}

void VideoStreamBufferController::UpdateDroppedFrames()
    RTC_RUN_ON(&worker_sequence_checker_) {
  const int dropped_frames = buffer_->GetTotalNumberOfDroppedFrames() -
                             frames_dropped_before_last_new_frame_;
  if (dropped_frames > 0)
    stats_proxy_->OnDroppedFrames(dropped_frames);
  frames_dropped_before_last_new_frame_ =
      buffer_->GetTotalNumberOfDroppedFrames();
}

void VideoStreamBufferController::UpdateFrameBufferTimings(
    Timestamp min_receive_time,
    Timestamp now) {
  // Update instantaneous delays.
  auto timings = timing_->GetTimings();
  if (timings.num_decoded_frames) {
    stats_proxy_->OnFrameBufferTimingsUpdated(
        timings.estimated_max_decode_time.ms(), timings.current_delay.ms(),
        timings.target_delay.ms(), timings.minimum_delay.ms(),
        timings.min_playout_delay.ms(), timings.render_delay.ms());
  }

  // The spec mandates that `jitterBufferDelay` is the "time the first
  // packet is received by the jitter buffer (ingest timestamp) to the time it
  // exits the jitter buffer (emit timestamp)". Since the "jitter buffer"
  // is not a monolith in the webrtc.org implementation, we take the freedom to
  // define "ingest timestamp" as "first packet received by
  // RtpVideoStreamReceiver2" and "emit timestamp" as "decodable frame released
  // by VideoStreamBufferController".
  //
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-jitterbufferdelay
  TimeDelta jitter_buffer_delay =
      std::max(TimeDelta::Zero(), now - min_receive_time);
  stats_proxy_->OnDecodableFrame(jitter_buffer_delay, timings.target_delay,
                                 timings.minimum_delay);
}

void VideoStreamBufferController::UpdateTimingFrameInfo() {
  absl::optional<TimingFrameInfo> info = timing_->GetTimingFrameInfo();
  if (info)
    stats_proxy_->OnTimingFrameInfoUpdated(*info);
}

bool VideoStreamBufferController::IsTooManyFramesQueued() const
    RTC_RUN_ON(&worker_sequence_checker_) {
  return buffer_->CurrentSize() > zero_playout_delay_max_decode_queue_size_;
}

void VideoStreamBufferController::ForceKeyFrameReleaseImmediately()
    RTC_RUN_ON(&worker_sequence_checker_) {
  RTC_DCHECK(keyframe_required_);
  // Iterate through the frame buffer until there is a complete keyframe and
  // release this right away.
  while (buffer_->DecodableTemporalUnitsInfo()) {
    auto next_frame = buffer_->ExtractNextDecodableTemporalUnit();
    if (next_frame.empty()) {
      RTC_DCHECK_NOTREACHED()
          << "Frame buffer should always return at least 1 frame.";
      continue;
    }
    // Found keyframe - decode right away.
    if (next_frame.front()->is_keyframe()) {
      auto render_time = timing_->RenderTime(next_frame.front()->RtpTimestamp(),
                                             clock_->CurrentTime());
      OnFrameReady(std::move(next_frame), render_time);
      return;
    }
  }
}

void VideoStreamBufferController::MaybeScheduleFrameForRelease()
    RTC_RUN_ON(&worker_sequence_checker_) {
  auto decodable_tu_info = buffer_->DecodableTemporalUnitsInfo();
  if (!decoder_ready_for_new_frame_ || !decodable_tu_info) {
    return;
  }

  if (keyframe_required_) {
    return ForceKeyFrameReleaseImmediately();
  }

  // If already scheduled then abort.
  if (frame_decode_scheduler_->ScheduledRtpTimestamp() ==
      decodable_tu_info->next_rtp_timestamp) {
    return;
  }

  TimeDelta max_wait = timeout_tracker_.TimeUntilTimeout();
  // Ensures the frame is scheduled for decode before the stream times out.
  // This is otherwise a race condition.
  max_wait = std::max(max_wait - TimeDelta::Millis(1), TimeDelta::Zero());
  absl::optional<FrameDecodeTiming::FrameSchedule> schedule;
  while (decodable_tu_info) {
    schedule = decode_timing_.OnFrameBufferUpdated(
        decodable_tu_info->next_rtp_timestamp,
        decodable_tu_info->last_rtp_timestamp, max_wait,
        IsTooManyFramesQueued());
    if (schedule) {
      // Don't schedule if already waiting for the same frame.
      if (frame_decode_scheduler_->ScheduledRtpTimestamp() !=
          decodable_tu_info->next_rtp_timestamp) {
        frame_decode_scheduler_->CancelOutstanding();
        frame_decode_scheduler_->ScheduleFrame(
            decodable_tu_info->next_rtp_timestamp, *schedule,
            absl::bind_front(&VideoStreamBufferController::FrameReadyForDecode,
                             this));
      }
      return;
    }
    // If no schedule for current rtp, drop and try again.
    buffer_->DropNextDecodableTemporalUnit();
    decodable_tu_info = buffer_->DecodableTemporalUnitsInfo();
  }
}

}  // namespace webrtc
