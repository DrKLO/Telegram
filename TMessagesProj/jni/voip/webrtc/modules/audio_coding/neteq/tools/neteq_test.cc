/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_test.h"

#include <iomanip>
#include <iostream>

#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace test {
namespace {

absl::optional<NetEq::Operation> ActionToOperations(
    absl::optional<NetEqSimulator::Action> a) {
  if (!a) {
    return absl::nullopt;
  }
  switch (*a) {
    case NetEqSimulator::Action::kAccelerate:
      return absl::make_optional(NetEq::Operation::kAccelerate);
    case NetEqSimulator::Action::kExpand:
      return absl::make_optional(NetEq::Operation::kExpand);
    case NetEqSimulator::Action::kNormal:
      return absl::make_optional(NetEq::Operation::kNormal);
    case NetEqSimulator::Action::kPreemptiveExpand:
      return absl::make_optional(NetEq::Operation::kPreemptiveExpand);
  }
}

std::unique_ptr<NetEq> CreateNetEq(
    const NetEq::Config& config,
    Clock* clock,
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory) {
  return DefaultNetEqFactory().CreateNetEq(config, decoder_factory, clock);
}

}  // namespace

void DefaultNetEqTestErrorCallback::OnInsertPacketError(
    const NetEqInput::PacketData& packet) {
  std::cerr << "InsertPacket returned an error." << std::endl;
  std::cerr << "Packet data: " << packet.ToString() << std::endl;
  RTC_FATAL();
}

void DefaultNetEqTestErrorCallback::OnGetAudioError() {
  std::cerr << "GetAudio returned an error." << std::endl;
  RTC_FATAL();
}

NetEqTest::NetEqTest(const NetEq::Config& config,
                     rtc::scoped_refptr<AudioDecoderFactory> decoder_factory,
                     const DecoderMap& codecs,
                     std::unique_ptr<std::ofstream> text_log,
                     NetEqFactory* neteq_factory,
                     std::unique_ptr<NetEqInput> input,
                     std::unique_ptr<AudioSink> output,
                     Callbacks callbacks)
    : input_(std::move(input)),
      clock_(Timestamp::Millis(input_->NextEventTime().value_or(0))),
      neteq_(neteq_factory
                 ? neteq_factory->CreateNetEq(config, decoder_factory, &clock_)
                 : CreateNetEq(config, &clock_, decoder_factory)),
      output_(std::move(output)),
      callbacks_(callbacks),
      sample_rate_hz_(config.sample_rate_hz),
      text_log_(std::move(text_log)) {
  RTC_CHECK(!config.enable_muted_state)
      << "The code does not handle enable_muted_state";
  RegisterDecoders(codecs);
}

NetEqTest::~NetEqTest() = default;

int64_t NetEqTest::Run() {
  int64_t simulation_time = 0;
  SimulationStepResult step_result;
  do {
    step_result = RunToNextGetAudio();
    simulation_time += step_result.simulation_step_ms;
  } while (!step_result.is_simulation_finished);
  if (callbacks_.simulation_ended_callback) {
    callbacks_.simulation_ended_callback->SimulationEnded(simulation_time);
  }
  return simulation_time;
}

NetEqTest::SimulationStepResult NetEqTest::RunToNextGetAudio() {
  SimulationStepResult result;
  const int64_t start_time_ms = *input_->NextEventTime();
  int64_t time_now_ms = clock_.CurrentTime().ms();
  current_state_.packet_iat_ms.clear();

  while (!input_->ended()) {
    // Advance time to next event.
    RTC_DCHECK(input_->NextEventTime());
    clock_.AdvanceTimeMilliseconds(*input_->NextEventTime() - time_now_ms);
    time_now_ms = *input_->NextEventTime();
    // Check if it is time to insert packet.
    if (input_->NextPacketTime() && time_now_ms >= *input_->NextPacketTime()) {
      std::unique_ptr<NetEqInput::PacketData> packet_data = input_->PopPacket();
      RTC_CHECK(packet_data);
      const size_t payload_data_length =
          packet_data->payload.size() - packet_data->header.paddingLength;
      if (payload_data_length != 0) {
        int error = neteq_->InsertPacket(
            packet_data->header,
            rtc::ArrayView<const uint8_t>(packet_data->payload));
        if (error != NetEq::kOK && callbacks_.error_callback) {
          callbacks_.error_callback->OnInsertPacketError(*packet_data);
        }
        if (callbacks_.post_insert_packet) {
          callbacks_.post_insert_packet->AfterInsertPacket(*packet_data,
                                                           neteq_.get());
        }
      } else {
        neteq_->InsertEmptyPacket(packet_data->header);
      }
      if (last_packet_time_ms_) {
        current_state_.packet_iat_ms.push_back(time_now_ms -
                                               *last_packet_time_ms_);
      }
      if (text_log_) {
        const auto ops_state = neteq_->GetOperationsAndState();
        const auto delta_wallclock =
            last_packet_time_ms_ ? (time_now_ms - *last_packet_time_ms_) : -1;
        const auto delta_timestamp =
            last_packet_timestamp_
                ? (static_cast<int64_t>(packet_data->header.timestamp) -
                   *last_packet_timestamp_) *
                      1000 / sample_rate_hz_
                : -1;
        const auto packet_size_bytes =
            packet_data->payload.size() == 12
                ? ByteReader<uint32_t>::ReadLittleEndian(
                      &packet_data->payload[8])
                : -1;
        *text_log_ << "Packet   - wallclock: " << std::setw(5) << time_now_ms
                   << ", delta wc: " << std::setw(4) << delta_wallclock
                   << ", seq_no: " << packet_data->header.sequenceNumber
                   << ", timestamp: " << std::setw(10)
                   << packet_data->header.timestamp
                   << ", delta ts: " << std::setw(4) << delta_timestamp
                   << ", size: " << std::setw(5) << packet_size_bytes
                   << ", frame size: " << std::setw(3)
                   << ops_state.current_frame_size_ms
                   << ", buffer size: " << std::setw(4)
                   << ops_state.current_buffer_size_ms << std::endl;
      }
      last_packet_time_ms_ = absl::make_optional<int>(time_now_ms);
      last_packet_timestamp_ =
          absl::make_optional<uint32_t>(packet_data->header.timestamp);
    }

    if (input_->NextSetMinimumDelayInfo().has_value() &&
        time_now_ms >= input_->NextSetMinimumDelayInfo().value().timestamp_ms) {
      neteq_->SetBaseMinimumDelayMs(
          input_->NextSetMinimumDelayInfo().value().delay_ms);
      input_->AdvanceSetMinimumDelay();
    }

    // Check if it is time to get output audio.
    if (input_->NextOutputEventTime() &&
        time_now_ms >= *input_->NextOutputEventTime()) {
      if (callbacks_.get_audio_callback) {
        callbacks_.get_audio_callback->BeforeGetAudio(neteq_.get());
      }
      AudioFrame out_frame;
      bool muted;
      int error = neteq_->GetAudio(&out_frame, &muted, nullptr,
                                   ActionToOperations(next_action_));
      next_action_ = absl::nullopt;
      RTC_CHECK(!muted) << "The code does not handle enable_muted_state";
      if (error != NetEq::kOK) {
        if (callbacks_.error_callback) {
          callbacks_.error_callback->OnGetAudioError();
        }
      } else {
        sample_rate_hz_ = out_frame.sample_rate_hz_;
      }
      if (callbacks_.get_audio_callback) {
        callbacks_.get_audio_callback->AfterGetAudio(time_now_ms, out_frame,
                                                     muted, neteq_.get());
      }

      if (output_) {
        RTC_CHECK(output_->WriteArray(
            out_frame.data(),
            out_frame.samples_per_channel_ * out_frame.num_channels_));
      }

      input_->AdvanceOutputEvent();
      result.simulation_step_ms =
          input_->NextEventTime().value_or(time_now_ms) - start_time_ms;
      const auto operations_state = neteq_->GetOperationsAndState();
      current_state_.current_delay_ms = operations_state.current_buffer_size_ms;
      current_state_.packet_size_ms = operations_state.current_frame_size_ms;
      current_state_.next_packet_available =
          operations_state.next_packet_available;
      current_state_.packet_buffer_flushed =
          operations_state.packet_buffer_flushes >
          prev_ops_state_.packet_buffer_flushes;
      // TODO(ivoc): Add more accurate reporting by tracking the origin of
      // samples in the sync buffer.
      result.action_times_ms[Action::kExpand] = 0;
      result.action_times_ms[Action::kAccelerate] = 0;
      result.action_times_ms[Action::kPreemptiveExpand] = 0;
      result.action_times_ms[Action::kNormal] = 0;

      if (out_frame.speech_type_ == AudioFrame::SpeechType::kPLC ||
          out_frame.speech_type_ == AudioFrame::SpeechType::kPLCCNG) {
        // Consider the whole frame to be the result of expansion.
        result.action_times_ms[Action::kExpand] = 10;
      } else if (operations_state.accelerate_samples -
                     prev_ops_state_.accelerate_samples >
                 0) {
        // Consider the whole frame to be the result of acceleration.
        result.action_times_ms[Action::kAccelerate] = 10;
      } else if (operations_state.preemptive_samples -
                     prev_ops_state_.preemptive_samples >
                 0) {
        // Consider the whole frame to be the result of preemptive expansion.
        result.action_times_ms[Action::kPreemptiveExpand] = 10;
      } else {
        // Consider the whole frame to be the result of normal playout.
        result.action_times_ms[Action::kNormal] = 10;
      }
      auto lifetime_stats = LifetimeStats();
      if (text_log_) {
        const bool plc =
            (out_frame.speech_type_ == AudioFrame::SpeechType::kPLC) ||
            (out_frame.speech_type_ == AudioFrame::SpeechType::kPLCCNG);
        const bool cng = out_frame.speech_type_ == AudioFrame::SpeechType::kCNG;
        const bool voice_concealed =
            (lifetime_stats.concealed_samples -
             lifetime_stats.silent_concealed_samples) >
            (prev_lifetime_stats_.concealed_samples -
             prev_lifetime_stats_.silent_concealed_samples);
        *text_log_ << "GetAudio - wallclock: " << std::setw(5) << time_now_ms
                   << ", delta wc: " << std::setw(4)
                   << (input_->NextEventTime().value_or(time_now_ms) -
                       start_time_ms)
                   << ", CNG: " << cng << ", PLC: " << plc
                   << ", voice concealed: " << voice_concealed
                   << ", buffer size: " << std::setw(4)
                   << current_state_.current_delay_ms << std::endl;
        if (lifetime_stats.packets_discarded >
            prev_lifetime_stats_.packets_discarded) {
          *text_log_ << "Discarded "
                     << (lifetime_stats.packets_discarded -
                         prev_lifetime_stats_.packets_discarded)
                     << " primary packets." << std::endl;
        }
        if (operations_state.packet_buffer_flushes >
            prev_ops_state_.packet_buffer_flushes) {
          *text_log_ << "Flushed packet buffer "
                     << (operations_state.packet_buffer_flushes -
                         prev_ops_state_.packet_buffer_flushes)
                     << " times." << std::endl;
        }
      }
      prev_lifetime_stats_ = lifetime_stats;
      const bool no_more_packets_to_decode =
          !input_->NextPacketTime() && !operations_state.next_packet_available;
      result.is_simulation_finished =
          no_more_packets_to_decode || input_->ended();
      prev_ops_state_ = operations_state;
      return result;
    }
  }
  result.simulation_step_ms =
      input_->NextEventTime().value_or(time_now_ms) - start_time_ms;
  result.is_simulation_finished = true;
  return result;
}

void NetEqTest::SetNextAction(NetEqTest::Action next_operation) {
  next_action_ = absl::optional<Action>(next_operation);
}

NetEqTest::NetEqState NetEqTest::GetNetEqState() {
  return current_state_;
}

NetEqNetworkStatistics NetEqTest::SimulationStats() {
  NetEqNetworkStatistics stats;
  RTC_CHECK_EQ(neteq_->NetworkStatistics(&stats), 0);
  return stats;
}

NetEqLifetimeStatistics NetEqTest::LifetimeStats() const {
  return neteq_->GetLifetimeStatistics();
}

NetEqTest::DecoderMap NetEqTest::StandardDecoderMap() {
  DecoderMap codecs = {{0, SdpAudioFormat("pcmu", 8000, 1)},
                       {8, SdpAudioFormat("pcma", 8000, 1)},
#ifdef WEBRTC_CODEC_ILBC
                       {102, SdpAudioFormat("ilbc", 8000, 1)},
#endif
#ifdef WEBRTC_CODEC_OPUS
                       {111, SdpAudioFormat("opus", 48000, 2)},
#endif
                       {93, SdpAudioFormat("l16", 8000, 1)},
                       {94, SdpAudioFormat("l16", 16000, 1)},
                       {95, SdpAudioFormat("l16", 32000, 1)},
                       {96, SdpAudioFormat("l16", 48000, 1)},
                       {9, SdpAudioFormat("g722", 8000, 1)},
                       {106, SdpAudioFormat("telephone-event", 8000, 1)},
                       {114, SdpAudioFormat("telephone-event", 16000, 1)},
                       {115, SdpAudioFormat("telephone-event", 32000, 1)},
                       {116, SdpAudioFormat("telephone-event", 48000, 1)},
                       {117, SdpAudioFormat("red", 8000, 1)},
                       {13, SdpAudioFormat("cn", 8000, 1)},
                       {98, SdpAudioFormat("cn", 16000, 1)},
                       {99, SdpAudioFormat("cn", 32000, 1)},
                       {100, SdpAudioFormat("cn", 48000, 1)}};
  return codecs;
}

void NetEqTest::RegisterDecoders(const DecoderMap& codecs) {
  for (const auto& c : codecs) {
    RTC_CHECK(neteq_->RegisterPayloadType(c.first, c.second))
        << "Cannot register " << c.second.name << " to payload type "
        << c.first;
  }
}

}  // namespace test
}  // namespace webrtc
