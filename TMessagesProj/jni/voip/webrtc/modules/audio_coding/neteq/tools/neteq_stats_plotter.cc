/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/neteq_stats_plotter.h"

#include <inttypes.h>
#include <stdio.h>

#include <utility>

#include "absl/strings/string_view.h"

namespace webrtc {
namespace test {

NetEqStatsPlotter::NetEqStatsPlotter(bool make_matlab_plot,
                                     bool make_python_plot,
                                     bool show_concealment_events,
                                     absl::string_view base_file_name)
    : make_matlab_plot_(make_matlab_plot),
      make_python_plot_(make_python_plot),
      show_concealment_events_(show_concealment_events),
      base_file_name_(base_file_name) {
  std::unique_ptr<NetEqDelayAnalyzer> delay_analyzer;
  if (make_matlab_plot || make_python_plot) {
    delay_analyzer.reset(new NetEqDelayAnalyzer);
  }
  stats_getter_.reset(new NetEqStatsGetter(std::move(delay_analyzer)));
}

void NetEqStatsPlotter::SimulationEnded(int64_t simulation_time_ms) {
  if (make_matlab_plot_) {
    auto matlab_script_name = base_file_name_;
    std::replace(matlab_script_name.begin(), matlab_script_name.end(), '.',
                 '_');
    printf("Creating Matlab plot script %s.m\n", matlab_script_name.c_str());
    stats_getter_->delay_analyzer()->CreateMatlabScript(matlab_script_name +
                                                        ".m");
  }
  if (make_python_plot_) {
    auto python_script_name = base_file_name_;
    std::replace(python_script_name.begin(), python_script_name.end(), '.',
                 '_');
    printf("Creating Python plot script %s.py\n", python_script_name.c_str());
    stats_getter_->delay_analyzer()->CreatePythonScript(python_script_name +
                                                        ".py");
  }

  printf("Simulation statistics:\n");
  printf("  output duration: %" PRId64 " ms\n", simulation_time_ms);
  auto stats = stats_getter_->AverageStats();
  printf("  packet_loss_rate: %f %%\n", 100.0 * stats.packet_loss_rate);
  printf("  expand_rate: %f %%\n", 100.0 * stats.expand_rate);
  printf("  speech_expand_rate: %f %%\n", 100.0 * stats.speech_expand_rate);
  printf("  preemptive_rate: %f %%\n", 100.0 * stats.preemptive_rate);
  printf("  accelerate_rate: %f %%\n", 100.0 * stats.accelerate_rate);
  printf("  secondary_decoded_rate: %f %%\n",
         100.0 * stats.secondary_decoded_rate);
  printf("  secondary_discarded_rate: %f %%\n",
         100.0 * stats.secondary_discarded_rate);
  printf("  clockdrift_ppm: %f ppm\n", stats.clockdrift_ppm);
  printf("  mean_waiting_time_ms: %f ms\n", stats.mean_waiting_time_ms);
  printf("  median_waiting_time_ms: %f ms\n", stats.median_waiting_time_ms);
  printf("  min_waiting_time_ms: %f ms\n", stats.min_waiting_time_ms);
  printf("  max_waiting_time_ms: %f ms\n", stats.max_waiting_time_ms);
  printf("  current_buffer_size_ms: %f ms\n", stats.current_buffer_size_ms);
  printf("  preferred_buffer_size_ms: %f ms\n", stats.preferred_buffer_size_ms);
  if (show_concealment_events_) {
    printf(" concealment_events_ms:\n");
    for (auto concealment_event : stats_getter_->concealment_events())
      printf("%s\n", concealment_event.ToString().c_str());
    printf(" end of concealment_events_ms\n");
  }

  const auto lifetime_stats_vector = stats_getter_->lifetime_stats();
  if (!lifetime_stats_vector->empty()) {
    auto lifetime_stats = lifetime_stats_vector->back().second;
    printf("  total_samples_received: %" PRIu64 "\n",
           lifetime_stats.total_samples_received);
    printf("  concealed_samples: %" PRIu64 "\n",
           lifetime_stats.concealed_samples);
    printf("  concealment_events: %" PRIu64 "\n",
           lifetime_stats.concealment_events);
    printf("  delayed_packet_outage_samples: %" PRIu64 "\n",
           lifetime_stats.delayed_packet_outage_samples);
    printf("  delayed_packet_outage_events: %" PRIu64 "\n",
           lifetime_stats.delayed_packet_outage_events);
    printf("  num_interruptions: %d\n", lifetime_stats.interruption_count);
    printf("  sum_interruption_length_ms: %d ms\n",
           lifetime_stats.total_interruption_duration_ms);
    printf("  interruption_ratio: %f\n",
           static_cast<double>(lifetime_stats.total_interruption_duration_ms) /
               simulation_time_ms);
    printf("  removed_samples_for_acceleration: %" PRIu64 "\n",
           lifetime_stats.removed_samples_for_acceleration);
    printf("  inserted_samples_for_deceleration: %" PRIu64 "\n",
           lifetime_stats.inserted_samples_for_deceleration);
    printf("  generated_noise_samples: %" PRIu64 "\n",
           lifetime_stats.generated_noise_samples);
    printf("  packets_discarded: %" PRIu64 "\n",
           lifetime_stats.packets_discarded);
  }
}

}  // namespace test
}  // namespace webrtc
