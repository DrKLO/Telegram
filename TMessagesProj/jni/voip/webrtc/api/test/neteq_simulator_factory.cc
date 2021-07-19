/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/neteq_simulator_factory.h"

#include <memory>
#include <string>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "modules/audio_coding/neteq/tools/neteq_test_factory.h"
#include "rtc_base/checks.h"

namespace webrtc {
namespace test {
namespace {
NetEqTestFactory::Config convertConfig(
    const NetEqSimulatorFactory::Config& simulation_config,
    absl::string_view replacement_audio_filename) {
  NetEqTestFactory::Config config;
  config.replacement_audio_file = std::string(replacement_audio_filename);
  config.max_nr_packets_in_buffer = simulation_config.max_nr_packets_in_buffer;
  config.initial_dummy_packets = simulation_config.initial_dummy_packets;
  config.skip_get_audio_events = simulation_config.skip_get_audio_events;
  config.field_trial_string = simulation_config.field_trial_string;
  config.output_audio_filename = simulation_config.output_audio_filename;
  config.pythonplot = simulation_config.python_plot_filename.has_value();
  config.plot_scripts_basename = simulation_config.python_plot_filename;
  config.textlog = simulation_config.text_log_filename.has_value();
  config.textlog_filename = simulation_config.text_log_filename;
  return config;
}
}  // namespace

NetEqSimulatorFactory::NetEqSimulatorFactory()
    : factory_(std::make_unique<NetEqTestFactory>()) {}

NetEqSimulatorFactory::~NetEqSimulatorFactory() = default;

std::unique_ptr<NetEqSimulator> NetEqSimulatorFactory::CreateSimulatorFromFile(
    absl::string_view event_log_filename,
    absl::string_view replacement_audio_filename,
    Config simulation_config) {
  NetEqTestFactory::Config config =
      convertConfig(simulation_config, replacement_audio_filename);
  return factory_->InitializeTestFromFile(
      std::string(event_log_filename), simulation_config.neteq_factory, config);
}

std::unique_ptr<NetEqSimulator>
NetEqSimulatorFactory::CreateSimulatorFromString(
    absl::string_view event_log_file_contents,
    absl::string_view replacement_audio_filename,
    Config simulation_config) {
  NetEqTestFactory::Config config =
      convertConfig(simulation_config, replacement_audio_filename);
  return factory_->InitializeTestFromString(
      std::string(event_log_file_contents), simulation_config.neteq_factory,
      config);
}

}  // namespace test
}  // namespace webrtc
