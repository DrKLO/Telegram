/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/frame_cadence_adapter.h"

#include <atomic>
#include <deque>
#include <memory>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/base/attributes.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/video_frame.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/race_checker.h"
#include "rtc_base/rate_statistics.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/metrics.h"
#include "system_wrappers/include/ntp_time.h"

namespace webrtc {
namespace {

// Abstracts concrete modes of the cadence adapter.
class AdapterMode {
 public:
  virtual ~AdapterMode() = default;

  // Called on the worker thread for every frame that enters.
  virtual void OnFrame(Timestamp post_time,
                       int frames_scheduled_for_processing,
                       const VideoFrame& frame) = 0;

  // Returns the currently estimated input framerate.
  virtual absl::optional<uint32_t> GetInputFrameRateFps() = 0;

  // Updates the frame rate.
  virtual void UpdateFrameRate() = 0;
};

// Implements a pass-through adapter. Single-threaded.
class PassthroughAdapterMode : public AdapterMode {
 public:
  PassthroughAdapterMode(Clock* clock,
                         FrameCadenceAdapterInterface::Callback* callback)
      : clock_(clock), callback_(callback) {
    sequence_checker_.Detach();
  }

  // Adapter overrides.
  void OnFrame(Timestamp post_time,
               int frames_scheduled_for_processing,
               const VideoFrame& frame) override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    callback_->OnFrame(post_time, frames_scheduled_for_processing, frame);
  }

  absl::optional<uint32_t> GetInputFrameRateFps() override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    return input_framerate_.Rate(clock_->TimeInMilliseconds());
  }

  void UpdateFrameRate() override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    input_framerate_.Update(1, clock_->TimeInMilliseconds());
  }

 private:
  Clock* const clock_;
  FrameCadenceAdapterInterface::Callback* const callback_;
  RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
  // Input frame rate statistics for use when not in zero-hertz mode.
  RateStatistics input_framerate_ RTC_GUARDED_BY(sequence_checker_){
      FrameCadenceAdapterInterface::kFrameRateAveragingWindowSizeMs, 1000};
};

// Implements a frame cadence adapter supporting zero-hertz input.
class ZeroHertzAdapterMode : public AdapterMode {
 public:
  ZeroHertzAdapterMode(TaskQueueBase* queue,
                       Clock* clock,
                       FrameCadenceAdapterInterface::Callback* callback,
                       double max_fps);

  // Reconfigures according to parameters.
  // All spatial layer trackers are initialized as unconverged by this method.
  void ReconfigureParameters(
      const FrameCadenceAdapterInterface::ZeroHertzModeParams& params);

  // Updates spatial layer quality convergence status.
  void UpdateLayerQualityConvergence(size_t spatial_index,
                                     bool quality_converged);

  // Updates spatial layer enabled status.
  void UpdateLayerStatus(size_t spatial_index, bool enabled);

  // Adapter overrides.
  void OnFrame(Timestamp post_time,
               int frames_scheduled_for_processing,
               const VideoFrame& frame) override;
  absl::optional<uint32_t> GetInputFrameRateFps() override;
  void UpdateFrameRate() override {}

  // Notified on dropped frames.
  void OnDiscardedFrame();

  // Conditionally requests a refresh frame via
  // Callback::RequestRefreshFrame.
  void ProcessKeyFrameRequest();

 private:
  // The tracking state of each spatial layer. Used for determining when to
  // stop repeating frames.
  struct SpatialLayerTracker {
    // If unset, the layer is disabled. Otherwise carries the quality
    // convergence status of the layer.
    absl::optional<bool> quality_converged;
  };
  // The state of a scheduled repeat.
  struct ScheduledRepeat {
    ScheduledRepeat(Timestamp origin,
                    int64_t origin_timestamp_us,
                    int64_t origin_ntp_time_ms)
        : scheduled(origin),
          idle(false),
          origin(origin),
          origin_timestamp_us(origin_timestamp_us),
          origin_ntp_time_ms(origin_ntp_time_ms) {}
    // The instant when the repeat was scheduled.
    Timestamp scheduled;
    // True if the repeat was scheduled as an idle repeat (long), false
    // otherwise.
    bool idle;
    // The moment we decided to start repeating.
    Timestamp origin;
    // The timestamp_us of the frame when we started repeating.
    int64_t origin_timestamp_us;
    // The ntp_times_ms of the frame when we started repeating.
    int64_t origin_ntp_time_ms;
  };

  // Returns true if all spatial layers can be considered to be converged in
  // terms of quality.
  // Convergence means QP has dropped to a low-enough level to warrant ceasing
  // to send identical frames at high frequency.
  bool HasQualityConverged() const RTC_RUN_ON(sequence_checker_);
  // Resets quality convergence information. HasQualityConverged() returns false
  // after this call.
  void ResetQualityConvergenceInfo() RTC_RUN_ON(sequence_checker_);
  // Processes incoming frames on a delayed cadence.
  void ProcessOnDelayedCadence() RTC_RUN_ON(sequence_checker_);
  // Schedules a later repeat with delay depending on state of layer trackers.
  // If true is passed in `idle_repeat`, the repeat is going to be
  // kZeroHertzIdleRepeatRatePeriod. Otherwise it'll be the value of
  // `frame_delay`.
  void ScheduleRepeat(int frame_id, bool idle_repeat)
      RTC_RUN_ON(sequence_checker_);
  // Repeats a frame in the abscence of incoming frames. Slows down when quality
  // convergence is attained, and stops the cadence terminally when new frames
  // have arrived.
  void ProcessRepeatedFrameOnDelayedCadence(int frame_id)
      RTC_RUN_ON(sequence_checker_);
  // Sends a frame, updating the timestamp to the current time.
  void SendFrameNow(const VideoFrame& frame) const
      RTC_RUN_ON(sequence_checker_);
  // Returns the repeat duration depending on if it's an idle repeat or not.
  TimeDelta RepeatDuration(bool idle_repeat) const
      RTC_RUN_ON(sequence_checker_);
  // Unless timer already running, starts repeatedly requesting refresh frames
  // after a grace_period. If a frame appears before the grace_period has
  // passed, the request is cancelled.
  void MaybeStartRefreshFrameRequester() RTC_RUN_ON(sequence_checker_);

  TaskQueueBase* const queue_;
  Clock* const clock_;
  FrameCadenceAdapterInterface::Callback* const callback_;

  // The configured max_fps.
  // TODO(crbug.com/1255737): support max_fps updates.
  const double max_fps_;
  // How much the incoming frame sequence is delayed by.
  const TimeDelta frame_delay_ = TimeDelta::Seconds(1) / max_fps_;

  RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
  // A queue of incoming frames and repeated frames.
  std::deque<VideoFrame> queued_frames_ RTC_GUARDED_BY(sequence_checker_);
  // The current frame ID to use when starting to repeat frames. This is used
  // for cancelling deferred repeated frame processing happening.
  int current_frame_id_ RTC_GUARDED_BY(sequence_checker_) = 0;
  // Has content when we are repeating frames.
  absl::optional<ScheduledRepeat> scheduled_repeat_
      RTC_GUARDED_BY(sequence_checker_);
  // Convergent state of each of the configured simulcast layers.
  std::vector<SpatialLayerTracker> layer_trackers_
      RTC_GUARDED_BY(sequence_checker_);
  // Repeating task handle used for requesting refresh frames until arrival, as
  // they can be dropped in various places in the capture pipeline.
  RepeatingTaskHandle refresh_frame_requester_
      RTC_GUARDED_BY(sequence_checker_);

  ScopedTaskSafety safety_;
};

class FrameCadenceAdapterImpl : public FrameCadenceAdapterInterface {
 public:
  FrameCadenceAdapterImpl(Clock* clock,
                          TaskQueueBase* queue,
                          const FieldTrialsView& field_trials);
  ~FrameCadenceAdapterImpl();

  // FrameCadenceAdapterInterface overrides.
  void Initialize(Callback* callback) override;
  void SetZeroHertzModeEnabled(
      absl::optional<ZeroHertzModeParams> params) override;
  absl::optional<uint32_t> GetInputFrameRateFps() override;
  void UpdateFrameRate() override;
  void UpdateLayerQualityConvergence(size_t spatial_index,
                                     bool quality_converged) override;
  void UpdateLayerStatus(size_t spatial_index, bool enabled) override;
  void ProcessKeyFrameRequest() override;

  // VideoFrameSink overrides.
  void OnFrame(const VideoFrame& frame) override;
  void OnDiscardedFrame() override;
  void OnConstraintsChanged(
      const VideoTrackSourceConstraints& constraints) override;

 private:
  // Called from OnFrame in zero-hertz mode.
  void OnFrameOnMainQueue(Timestamp post_time,
                          int frames_scheduled_for_processing,
                          const VideoFrame& frame) RTC_RUN_ON(queue_);

  // Returns true under all of the following conditions:
  // - constraints min fps set to 0
  // - constraints max fps set and greater than 0,
  // - field trial enabled
  // - zero-hertz mode enabled
  bool IsZeroHertzScreenshareEnabled() const RTC_RUN_ON(queue_);

  // Handles adapter creation on configuration changes.
  void MaybeReconfigureAdapters(bool was_zero_hertz_enabled) RTC_RUN_ON(queue_);

  // Called to report on constraint UMAs.
  void MaybeReportFrameRateConstraintUmas() RTC_RUN_ON(queue_);

  Clock* const clock_;
  TaskQueueBase* const queue_;

  // True if we support frame entry for screenshare with a minimum frequency of
  // 0 Hz.
  const bool zero_hertz_screenshare_enabled_;

  // The two possible modes we're under.
  absl::optional<PassthroughAdapterMode> passthrough_adapter_;
  absl::optional<ZeroHertzAdapterMode> zero_hertz_adapter_;
  // If set, zero-hertz mode has been enabled.
  absl::optional<ZeroHertzModeParams> zero_hertz_params_;
  // Cache for the current adapter mode.
  AdapterMode* current_adapter_mode_ = nullptr;

  // Timestamp for statistics reporting.
  absl::optional<Timestamp> zero_hertz_adapter_created_timestamp_
      RTC_GUARDED_BY(queue_);

  // Set up during Initialize.
  Callback* callback_ = nullptr;

  // The source's constraints.
  absl::optional<VideoTrackSourceConstraints> source_constraints_
      RTC_GUARDED_BY(queue_);

  // Race checker for incoming frames. This is the network thread in chromium,
  // but may vary from test contexts.
  rtc::RaceChecker incoming_frame_race_checker_;
  bool has_reported_screenshare_frame_rate_umas_ RTC_GUARDED_BY(queue_) = false;

  // Number of frames that are currently scheduled for processing on the
  // `queue_`.
  std::atomic<int> frames_scheduled_for_processing_{0};

  ScopedTaskSafetyDetached safety_;
};

ZeroHertzAdapterMode::ZeroHertzAdapterMode(
    TaskQueueBase* queue,
    Clock* clock,
    FrameCadenceAdapterInterface::Callback* callback,
    double max_fps)
    : queue_(queue), clock_(clock), callback_(callback), max_fps_(max_fps) {
  sequence_checker_.Detach();
  MaybeStartRefreshFrameRequester();
}

void ZeroHertzAdapterMode::ReconfigureParameters(
    const FrameCadenceAdapterInterface::ZeroHertzModeParams& params) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_INFO) << __func__ << " this " << this << " num_simulcast_layers "
                    << params.num_simulcast_layers;

  // Start as unconverged.
  layer_trackers_.clear();
  layer_trackers_.resize(params.num_simulcast_layers,
                         SpatialLayerTracker{false});
}

void ZeroHertzAdapterMode::UpdateLayerQualityConvergence(
    size_t spatial_index,
    bool quality_converged) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_LOG(LS_INFO) << __func__ << " this " << this << " layer " << spatial_index
                   << " quality has converged: " << quality_converged;
  if (spatial_index >= layer_trackers_.size())
    return;
  if (layer_trackers_[spatial_index].quality_converged.has_value())
    layer_trackers_[spatial_index].quality_converged = quality_converged;
}

void ZeroHertzAdapterMode::UpdateLayerStatus(size_t spatial_index,
                                             bool enabled) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (spatial_index >= layer_trackers_.size())
    return;
  if (enabled) {
    if (!layer_trackers_[spatial_index].quality_converged.has_value()) {
      // Assume quality has not converged until hearing otherwise.
      layer_trackers_[spatial_index].quality_converged = false;
    }
  } else {
    layer_trackers_[spatial_index].quality_converged = absl::nullopt;
  }
  RTC_LOG(LS_INFO)
      << __func__ << " this " << this << " layer " << spatial_index
      << (enabled
              ? (layer_trackers_[spatial_index].quality_converged.has_value()
                     ? " enabled."
                     : " enabled and it's assumed quality has not converged.")
              : " disabled.");
}

void ZeroHertzAdapterMode::OnFrame(Timestamp post_time,
                                   int frames_scheduled_for_processing,
                                   const VideoFrame& frame) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << "ZeroHertzAdapterMode::" << __func__ << " this "
                       << this;
  refresh_frame_requester_.Stop();

  // Assume all enabled layers are unconverged after frame entry.
  ResetQualityConvergenceInfo();

  // Remove stored repeating frame if needed.
  if (scheduled_repeat_.has_value()) {
    RTC_DCHECK(queued_frames_.size() == 1);
    RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this
                         << " cancel repeat and restart with original";
    queued_frames_.pop_front();
  }

  // Store the frame in the queue and schedule deferred processing.
  queued_frames_.push_back(frame);
  current_frame_id_++;
  scheduled_repeat_ = absl::nullopt;
  queue_->PostDelayedHighPrecisionTask(
      SafeTask(safety_.flag(),
               [this] {
                 RTC_DCHECK_RUN_ON(&sequence_checker_);
                 ProcessOnDelayedCadence();
               }),
      frame_delay_);
}

void ZeroHertzAdapterMode::OnDiscardedFrame() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << "ZeroHertzAdapterMode::" << __func__;

  // Under zero hertz source delivery, a discarded frame ending a sequence of
  // frames which happened to contain important information can be seen as a
  // capture freeze. Avoid this by starting requesting refresh frames after a
  // grace period.
  MaybeStartRefreshFrameRequester();
}

absl::optional<uint32_t> ZeroHertzAdapterMode::GetInputFrameRateFps() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return max_fps_;
}

void ZeroHertzAdapterMode::ProcessKeyFrameRequest() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // If we're new and don't have a frame, there's no need to request refresh
  // frames as this was being triggered for us when zero-hz mode was set up.
  //
  // The next frame encoded will be a key frame. Reset quality convergence so we
  // don't get idle repeats shortly after, because key frames need a lot of
  // refinement frames.
  ResetQualityConvergenceInfo();

  // If we're not repeating, or we're repeating with short duration, we will
  // very soon send out a frame and don't need a refresh frame.
  if (!scheduled_repeat_.has_value() || !scheduled_repeat_->idle) {
    RTC_LOG(LS_INFO) << __func__ << " this " << this
                     << " not requesting refresh frame because of recently "
                        "incoming frame or short repeating.";
    return;
  }

  // If the repeat is scheduled within a short (i.e. frame_delay_) interval, we
  // will very soon send out a frame and don't need a refresh frame.
  Timestamp now = clock_->CurrentTime();
  if (scheduled_repeat_->scheduled + RepeatDuration(/*idle_repeat=*/true) -
          now <=
      frame_delay_) {
    RTC_LOG(LS_INFO) << __func__ << " this " << this
                     << " not requesting refresh frame because of soon "
                        "happening idle repeat";
    return;
  }

  // Cancel the current repeat and reschedule a short repeat now. No need for a
  // new refresh frame.
  RTC_LOG(LS_INFO) << __func__ << " this " << this
                   << " not requesting refresh frame and scheduling a short "
                      "repeat due to key frame request";
  ScheduleRepeat(++current_frame_id_, /*idle_repeat=*/false);
  return;
}

bool ZeroHertzAdapterMode::HasQualityConverged() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  // 1. Define ourselves as unconverged with no spatial layers configured. This
  // is to keep short repeating until the layer configuration comes.
  // 2. Unset layers implicitly imply that they're converged to support
  // disabling layers when they're not needed.
  const bool quality_converged =
      !layer_trackers_.empty() &&
      absl::c_all_of(layer_trackers_, [](const SpatialLayerTracker& tracker) {
        return tracker.quality_converged.value_or(true);
      });
  return quality_converged;
}

void ZeroHertzAdapterMode::ResetQualityConvergenceInfo() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_INFO) << __func__ << " this " << this;
  for (auto& layer_tracker : layer_trackers_) {
    if (layer_tracker.quality_converged.has_value())
      layer_tracker.quality_converged = false;
  }
}

void ZeroHertzAdapterMode::ProcessOnDelayedCadence() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!queued_frames_.empty());
  RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this;

  SendFrameNow(queued_frames_.front());

  // If there were two or more frames stored, we do not have to schedule repeats
  // of the front frame.
  if (queued_frames_.size() > 1) {
    queued_frames_.pop_front();
    return;
  }

  // There's only one frame to send. Schedule a repeat sequence, which is
  // cancelled by `current_frame_id_` getting incremented should new frames
  // arrive.
  ScheduleRepeat(current_frame_id_, HasQualityConverged());
}

void ZeroHertzAdapterMode::ScheduleRepeat(int frame_id, bool idle_repeat) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this << " frame_id "
                       << frame_id;
  Timestamp now = clock_->CurrentTime();
  if (!scheduled_repeat_.has_value()) {
    scheduled_repeat_.emplace(now, queued_frames_.front().timestamp_us(),
                              queued_frames_.front().ntp_time_ms());
  }
  scheduled_repeat_->scheduled = now;
  scheduled_repeat_->idle = idle_repeat;

  TimeDelta repeat_delay = RepeatDuration(idle_repeat);
  queue_->PostDelayedHighPrecisionTask(
      SafeTask(safety_.flag(),
               [this, frame_id] {
                 RTC_DCHECK_RUN_ON(&sequence_checker_);
                 ProcessRepeatedFrameOnDelayedCadence(frame_id);
               }),
      repeat_delay);
}

void ZeroHertzAdapterMode::ProcessRepeatedFrameOnDelayedCadence(int frame_id) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this << " frame_id "
                       << frame_id;
  RTC_DCHECK(!queued_frames_.empty());

  // Cancel this invocation if new frames turned up.
  if (frame_id != current_frame_id_)
    return;
  RTC_DCHECK(scheduled_repeat_.has_value());

  VideoFrame& frame = queued_frames_.front();

  // Since this is a repeated frame, nothing changed compared to before.
  VideoFrame::UpdateRect empty_update_rect;
  empty_update_rect.MakeEmptyUpdate();
  frame.set_update_rect(empty_update_rect);

  // Adjust timestamps of the frame of the repeat, accounting for the actual
  // delay since we started repeating.
  //
  // NOTE: No need to update the RTP timestamp as the VideoStreamEncoder
  // overwrites it based on its chosen NTP timestamp source.
  TimeDelta total_delay = clock_->CurrentTime() - scheduled_repeat_->origin;
  if (frame.timestamp_us() > 0) {
    frame.set_timestamp_us(scheduled_repeat_->origin_timestamp_us +
                           total_delay.us());
  }
  if (frame.ntp_time_ms()) {
    frame.set_ntp_time_ms(scheduled_repeat_->origin_ntp_time_ms +
                          total_delay.ms());
  }
  SendFrameNow(frame);

  // Schedule another repeat.
  ScheduleRepeat(frame_id, HasQualityConverged());
}

void ZeroHertzAdapterMode::SendFrameNow(const VideoFrame& frame) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this << " timestamp "
                       << frame.timestamp() << " timestamp_us "
                       << frame.timestamp_us() << " ntp_time_ms "
                       << frame.ntp_time_ms();
  // TODO(crbug.com/1255737): figure out if frames_scheduled_for_processing
  // makes sense to compute in this implementation.
  callback_->OnFrame(/*post_time=*/clock_->CurrentTime(),
                     /*frames_scheduled_for_processing=*/1, frame);
}

TimeDelta ZeroHertzAdapterMode::RepeatDuration(bool idle_repeat) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return idle_repeat
             ? FrameCadenceAdapterInterface::kZeroHertzIdleRepeatRatePeriod
             : frame_delay_;
}

void ZeroHertzAdapterMode::MaybeStartRefreshFrameRequester() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DLOG(LS_VERBOSE) << __func__;
  if (!refresh_frame_requester_.Running()) {
    refresh_frame_requester_ = RepeatingTaskHandle::DelayedStart(
        queue_,
        FrameCadenceAdapterInterface::kOnDiscardedFrameRefreshFramePeriod *
            frame_delay_,
        [this] {
          RTC_DLOG(LS_VERBOSE) << __func__ << " RequestRefreshFrame";
          if (callback_)
            callback_->RequestRefreshFrame();
          return frame_delay_;
        });
  }
}

FrameCadenceAdapterImpl::FrameCadenceAdapterImpl(
    Clock* clock,
    TaskQueueBase* queue,
    const FieldTrialsView& field_trials)
    : clock_(clock),
      queue_(queue),
      zero_hertz_screenshare_enabled_(
          !field_trials.IsDisabled("WebRTC-ZeroHertzScreenshare")) {}

FrameCadenceAdapterImpl::~FrameCadenceAdapterImpl() {
  RTC_DLOG(LS_VERBOSE) << __func__ << " this " << this;
}

void FrameCadenceAdapterImpl::Initialize(Callback* callback) {
  callback_ = callback;
  passthrough_adapter_.emplace(clock_, callback);
  current_adapter_mode_ = &passthrough_adapter_.value();
}

void FrameCadenceAdapterImpl::SetZeroHertzModeEnabled(
    absl::optional<ZeroHertzModeParams> params) {
  RTC_DCHECK_RUN_ON(queue_);
  bool was_zero_hertz_enabled = zero_hertz_params_.has_value();
  if (params.has_value() && !was_zero_hertz_enabled)
    has_reported_screenshare_frame_rate_umas_ = false;
  zero_hertz_params_ = params;
  MaybeReconfigureAdapters(was_zero_hertz_enabled);
}

absl::optional<uint32_t> FrameCadenceAdapterImpl::GetInputFrameRateFps() {
  RTC_DCHECK_RUN_ON(queue_);
  return current_adapter_mode_->GetInputFrameRateFps();
}

void FrameCadenceAdapterImpl::UpdateFrameRate() {
  RTC_DCHECK_RUN_ON(queue_);
  // The frame rate need not be updated for the zero-hertz adapter. The
  // passthrough adapter however uses it. Always pass frames into the
  // passthrough to keep the estimation alive should there be an adapter switch.
  passthrough_adapter_->UpdateFrameRate();
}

void FrameCadenceAdapterImpl::UpdateLayerQualityConvergence(
    size_t spatial_index,
    bool quality_converged) {
  if (zero_hertz_adapter_.has_value())
    zero_hertz_adapter_->UpdateLayerQualityConvergence(spatial_index,
                                                       quality_converged);
}

void FrameCadenceAdapterImpl::UpdateLayerStatus(size_t spatial_index,
                                                bool enabled) {
  if (zero_hertz_adapter_.has_value())
    zero_hertz_adapter_->UpdateLayerStatus(spatial_index, enabled);
}

void FrameCadenceAdapterImpl::ProcessKeyFrameRequest() {
  RTC_DCHECK_RUN_ON(queue_);
  if (zero_hertz_adapter_)
    zero_hertz_adapter_->ProcessKeyFrameRequest();
}

void FrameCadenceAdapterImpl::OnFrame(const VideoFrame& frame) {
  // This method is called on the network thread under Chromium, or other
  // various contexts in test.
  RTC_DCHECK_RUNS_SERIALIZED(&incoming_frame_race_checker_);
  RTC_DLOG(LS_VERBOSE) << "FrameCadenceAdapterImpl::" << __func__ << " this "
                       << this;

  // Local time in webrtc time base.
  Timestamp post_time = clock_->CurrentTime();
  frames_scheduled_for_processing_.fetch_add(1, std::memory_order_relaxed);
  queue_->PostTask(SafeTask(safety_.flag(), [this, post_time, frame] {
    RTC_DCHECK_RUN_ON(queue_);
    if (zero_hertz_adapter_created_timestamp_.has_value()) {
      TimeDelta time_until_first_frame =
          clock_->CurrentTime() - *zero_hertz_adapter_created_timestamp_;
      zero_hertz_adapter_created_timestamp_ = absl::nullopt;
      RTC_HISTOGRAM_COUNTS_10000(
          "WebRTC.Screenshare.ZeroHz.TimeUntilFirstFrameMs",
          time_until_first_frame.ms());
    }

    const int frames_scheduled_for_processing =
        frames_scheduled_for_processing_.fetch_sub(1,
                                                   std::memory_order_relaxed);
    OnFrameOnMainQueue(post_time, frames_scheduled_for_processing,
                       std::move(frame));
    MaybeReportFrameRateConstraintUmas();
  }));
}

void FrameCadenceAdapterImpl::OnDiscardedFrame() {
  callback_->OnDiscardedFrame();
  queue_->PostTask(SafeTask(safety_.flag(), [this] {
    RTC_DCHECK_RUN_ON(queue_);
    if (zero_hertz_adapter_) {
      zero_hertz_adapter_->OnDiscardedFrame();
    }
  }));
}

void FrameCadenceAdapterImpl::OnConstraintsChanged(
    const VideoTrackSourceConstraints& constraints) {
  RTC_LOG(LS_INFO) << __func__ << " this " << this << " min_fps "
                   << constraints.min_fps.value_or(-1) << " max_fps "
                   << constraints.max_fps.value_or(-1);
  queue_->PostTask(SafeTask(safety_.flag(), [this, constraints] {
    RTC_DCHECK_RUN_ON(queue_);
    bool was_zero_hertz_enabled = IsZeroHertzScreenshareEnabled();
    source_constraints_ = constraints;
    MaybeReconfigureAdapters(was_zero_hertz_enabled);
  }));
}

void FrameCadenceAdapterImpl::OnFrameOnMainQueue(
    Timestamp post_time,
    int frames_scheduled_for_processing,
    const VideoFrame& frame) {
  RTC_DCHECK_RUN_ON(queue_);
  current_adapter_mode_->OnFrame(post_time, frames_scheduled_for_processing,
                                 frame);
}

bool FrameCadenceAdapterImpl::IsZeroHertzScreenshareEnabled() const {
  RTC_DCHECK_RUN_ON(queue_);
  return zero_hertz_screenshare_enabled_ && source_constraints_.has_value() &&
         source_constraints_->max_fps.value_or(-1) > 0 &&
         source_constraints_->min_fps.value_or(-1) == 0 &&
         zero_hertz_params_.has_value();
}

void FrameCadenceAdapterImpl::MaybeReconfigureAdapters(
    bool was_zero_hertz_enabled) {
  RTC_DCHECK_RUN_ON(queue_);
  bool is_zero_hertz_enabled = IsZeroHertzScreenshareEnabled();
  if (is_zero_hertz_enabled) {
    if (!was_zero_hertz_enabled) {
      zero_hertz_adapter_.emplace(queue_, clock_, callback_,
                                  source_constraints_->max_fps.value());
      RTC_LOG(LS_INFO) << "Zero hertz mode activated.";
      zero_hertz_adapter_created_timestamp_ = clock_->CurrentTime();
    }
    zero_hertz_adapter_->ReconfigureParameters(zero_hertz_params_.value());
    current_adapter_mode_ = &zero_hertz_adapter_.value();
  } else {
    if (was_zero_hertz_enabled)
      zero_hertz_adapter_ = absl::nullopt;
    current_adapter_mode_ = &passthrough_adapter_.value();
  }
}

void FrameCadenceAdapterImpl::MaybeReportFrameRateConstraintUmas() {
  RTC_DCHECK_RUN_ON(queue_);
  if (has_reported_screenshare_frame_rate_umas_)
    return;
  has_reported_screenshare_frame_rate_umas_ = true;
  if (!zero_hertz_params_.has_value())
    return;
  RTC_HISTOGRAM_BOOLEAN("WebRTC.Screenshare.FrameRateConstraints.Exists",
                        source_constraints_.has_value());
  if (!source_constraints_.has_value())
    return;
  RTC_HISTOGRAM_BOOLEAN("WebRTC.Screenshare.FrameRateConstraints.Min.Exists",
                        source_constraints_->min_fps.has_value());
  if (source_constraints_->min_fps.has_value()) {
    RTC_HISTOGRAM_COUNTS_100(
        "WebRTC.Screenshare.FrameRateConstraints.Min.Value",
        source_constraints_->min_fps.value());
  }
  RTC_HISTOGRAM_BOOLEAN("WebRTC.Screenshare.FrameRateConstraints.Max.Exists",
                        source_constraints_->max_fps.has_value());
  if (source_constraints_->max_fps.has_value()) {
    RTC_HISTOGRAM_COUNTS_100(
        "WebRTC.Screenshare.FrameRateConstraints.Max.Value",
        source_constraints_->max_fps.value());
  }
  if (!source_constraints_->min_fps.has_value()) {
    if (source_constraints_->max_fps.has_value()) {
      RTC_HISTOGRAM_COUNTS_100(
          "WebRTC.Screenshare.FrameRateConstraints.MinUnset.Max",
          source_constraints_->max_fps.value());
    }
  } else if (source_constraints_->max_fps.has_value()) {
    if (source_constraints_->min_fps.value() <
        source_constraints_->max_fps.value()) {
      RTC_HISTOGRAM_COUNTS_100(
          "WebRTC.Screenshare.FrameRateConstraints.MinLessThanMax.Min",
          source_constraints_->min_fps.value());
      RTC_HISTOGRAM_COUNTS_100(
          "WebRTC.Screenshare.FrameRateConstraints.MinLessThanMax.Max",
          source_constraints_->max_fps.value());
    }
    // Multi-dimensional histogram for min and max FPS making it possible to
    // uncover min and max combinations. See
    // https://chromium.googlesource.com/chromium/src.git/+/HEAD/tools/metrics/histograms/README.md#multidimensional-histograms
    constexpr int kMaxBucketCount =
        60 * /*max min_fps=*/60 + /*max max_fps=*/60 - 1;
    RTC_HISTOGRAM_ENUMERATION_SPARSE(
        "WebRTC.Screenshare.FrameRateConstraints.60MinPlusMaxMinusOne",
        source_constraints_->min_fps.value() * 60 +
            source_constraints_->max_fps.value() - 1,
        /*boundary=*/kMaxBucketCount);
  }
}

}  // namespace

std::unique_ptr<FrameCadenceAdapterInterface>
FrameCadenceAdapterInterface::Create(Clock* clock,
                                     TaskQueueBase* queue,
                                     const FieldTrialsView& field_trials) {
  return std::make_unique<FrameCadenceAdapterImpl>(clock, queue, field_trials);
}

}  // namespace webrtc
