/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_
#define MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_

#include "api/neteq/neteq.h"
#include "api/neteq/neteq_controller.h"
#include "api/neteq/tick_timer.h"
#include "modules/audio_coding/neteq/buffer_level_filter.h"
#include "modules/audio_coding/neteq/delay_manager.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/experiments/field_trial_parser.h"

namespace webrtc {

// This is the class for the decision tree implementation.
class DecisionLogic : public NetEqController {
 public:
  static const int kReinitAfterExpands = 100;
  static const int kMaxWaitForPacket = 10;

  // Constructor.
  DecisionLogic(NetEqController::Config config);

  ~DecisionLogic() override;

  // Resets object to a clean state.
  void Reset() override;

  // Resets parts of the state. Typically done when switching codecs.
  void SoftReset() override;

  // Sets the sample rate and the output block size.
  void SetSampleRate(int fs_hz, size_t output_size_samples) override;

  // Given info about the latest received packet, and current jitter buffer
  // status, returns the operation. |target_timestamp| and |expand_mutefactor|
  // are provided for reference. |last_packet_samples| is the number of samples
  // obtained from the last decoded frame. If there is a packet available, it
  // should be supplied in |packet|; otherwise it should be NULL. The mode
  // resulting from the last call to NetEqImpl::GetAudio is supplied in
  // |last_mode|. If there is a DTMF event to play, |play_dtmf| should be set to
  // true. The output variable |reset_decoder| will be set to true if a reset is
  // required; otherwise it is left unchanged (i.e., it can remain true if it
  // was true before the call).
  NetEq::Operation GetDecision(const NetEqController::NetEqStatus& status,
                               bool* reset_decoder) override;

  // These methods test the |cng_state_| for different conditions.
  bool CngRfc3389On() const override { return cng_state_ == kCngRfc3389On; }
  bool CngOff() const override { return cng_state_ == kCngOff; }

  // Resets the |cng_state_| to kCngOff.
  void SetCngOff() override { cng_state_ = kCngOff; }

  // Reports back to DecisionLogic whether the decision to do expand remains or
  // not. Note that this is necessary, since an expand decision can be changed
  // to kNormal in NetEqImpl::GetDecision if there is still enough data in the
  // sync buffer.
  void ExpandDecision(NetEq::Operation operation) override;

  // Adds |value| to |sample_memory_|.
  void AddSampleMemory(int32_t value) override { sample_memory_ += value; }

  int TargetLevelMs() override {
    return ((delay_manager_->TargetLevel() * packet_length_samples_) >> 8) /
           rtc::CheckedDivExact(sample_rate_, 1000);
  }

  absl::optional<int> PacketArrived(bool last_cng_or_dtmf,
                                    size_t packet_length_samples,
                                    bool should_update_stats,
                                    uint16_t main_sequence_number,
                                    uint32_t main_timestamp,
                                    int fs_hz) override;

  void RegisterEmptyPacket() override { delay_manager_->RegisterEmptyPacket(); }

  bool SetMaximumDelay(int delay_ms) override {
    return delay_manager_->SetMaximumDelay(delay_ms);
  }
  bool SetMinimumDelay(int delay_ms) override {
    return delay_manager_->SetMinimumDelay(delay_ms);
  }
  bool SetBaseMinimumDelay(int delay_ms) override {
    return delay_manager_->SetBaseMinimumDelay(delay_ms);
  }
  int GetBaseMinimumDelay() const override {
    return delay_manager_->GetBaseMinimumDelay();
  }
  bool PeakFound() const override { return false; }

  int GetFilteredBufferLevel() const override {
    return buffer_level_filter_.filtered_current_level();
  }

  // Accessors and mutators.
  void set_sample_memory(int32_t value) override { sample_memory_ = value; }
  size_t noise_fast_forward() const override { return noise_fast_forward_; }
  size_t packet_length_samples() const override {
    return packet_length_samples_;
  }
  void set_packet_length_samples(size_t value) override {
    packet_length_samples_ = value;
  }
  void set_prev_time_scale(bool value) override { prev_time_scale_ = value; }

 private:
  // The value 5 sets maximum time-stretch rate to about 100 ms/s.
  static const int kMinTimescaleInterval = 5;

  enum CngState { kCngOff, kCngRfc3389On, kCngInternalOn };

  // Updates the |buffer_level_filter_| with the current buffer level
  // |buffer_size_packets|.
  void FilterBufferLevel(size_t buffer_size_packets);

  // Returns the operation given that the next available packet is a comfort
  // noise payload (RFC 3389 only, not codec-internal).
  virtual NetEq::Operation CngOperation(NetEq::Mode prev_mode,
                                        uint32_t target_timestamp,
                                        uint32_t available_timestamp,
                                        size_t generated_noise_samples);

  // Returns the operation given that no packets are available (except maybe
  // a DTMF event, flagged by setting |play_dtmf| true).
  virtual NetEq::Operation NoPacket(bool play_dtmf);

  // Returns the operation to do given that the expected packet is available.
  virtual NetEq::Operation ExpectedPacketAvailable(NetEq::Mode prev_mode,
                                                   bool play_dtmf);

  // Returns the operation to do given that the expected packet is not
  // available, but a packet further into the future is at hand.
  virtual NetEq::Operation FuturePacketAvailable(
      size_t decoder_frame_length,
      NetEq::Mode prev_mode,
      uint32_t target_timestamp,
      uint32_t available_timestamp,
      bool play_dtmf,
      size_t generated_noise_samples,
      size_t span_samples_in_packet_buffer,
      size_t num_packets_in_packet_buffer);

  // Checks if enough time has elapsed since the last successful timescale
  // operation was done (i.e., accelerate or preemptive expand).
  bool TimescaleAllowed() const {
    return !timescale_countdown_ || timescale_countdown_->Finished();
  }

  // Checks if the current (filtered) buffer level is under the target level.
  bool UnderTargetLevel() const;

  // Checks if |timestamp_leap| is so long into the future that a reset due
  // to exceeding kReinitAfterExpands will be done.
  bool ReinitAfterExpands(uint32_t timestamp_leap) const;

  // Checks if we still have not done enough expands to cover the distance from
  // the last decoded packet to the next available packet, the distance beeing
  // conveyed in |timestamp_leap|.
  bool PacketTooEarly(uint32_t timestamp_leap) const;

  // Checks if num_consecutive_expands_ >= kMaxWaitForPacket.
  bool MaxWaitForPacket() const;

  std::unique_ptr<DelayManager> delay_manager_;
  BufferLevelFilter buffer_level_filter_;
  const TickTimer* tick_timer_;
  int sample_rate_;
  size_t output_size_samples_;
  CngState cng_state_ = kCngOff;  // Remember if comfort noise is interrupted by
                                  // other event (e.g., DTMF).
  size_t noise_fast_forward_ = 0;
  size_t packet_length_samples_ = 0;
  int sample_memory_ = 0;
  bool prev_time_scale_ = false;
  bool disallow_time_stretching_;
  std::unique_ptr<TickTimer::Countdown> timescale_countdown_;
  int num_consecutive_expands_ = 0;
  int time_stretched_cn_samples_ = 0;
  FieldTrialParameter<bool> estimate_dtx_delay_;
  FieldTrialParameter<bool> time_stretch_cn_;
  FieldTrialConstrained<int> target_level_window_ms_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DecisionLogic);
};

}  // namespace webrtc
#endif  // MODULES_AUDIO_CODING_NETEQ_DECISION_LOGIC_H_
