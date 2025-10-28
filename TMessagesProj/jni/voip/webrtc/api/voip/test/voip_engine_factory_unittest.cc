/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/voip/voip_engine_factory.h"

#include <utility>

#include "api/task_queue/default_task_queue_factory.h"
#include "modules/audio_device/include/mock_audio_device.h"
#include "modules/audio_processing/include/mock_audio_processing.h"
#include "test/gtest.h"
#include "test/mock_audio_decoder_factory.h"
#include "test/mock_audio_encoder_factory.h"

namespace webrtc {
namespace {

// Create voip engine with mock modules as normal use case.
TEST(VoipEngineFactoryTest, CreateEngineWithMockModules) {
  VoipEngineConfig config;
  config.encoder_factory = rtc::make_ref_counted<MockAudioEncoderFactory>();
  config.decoder_factory = rtc::make_ref_counted<MockAudioDecoderFactory>();
  config.task_queue_factory = CreateDefaultTaskQueueFactory();
  config.audio_processing =
      rtc::make_ref_counted<testing::NiceMock<test::MockAudioProcessing>>();
  config.audio_device_module = test::MockAudioDeviceModule::CreateNice();

  auto voip_engine = CreateVoipEngine(std::move(config));
  EXPECT_NE(voip_engine, nullptr);
}

// Create voip engine without setting audio processing as optional component.
TEST(VoipEngineFactoryTest, UseNoAudioProcessing) {
  VoipEngineConfig config;
  config.encoder_factory = rtc::make_ref_counted<MockAudioEncoderFactory>();
  config.decoder_factory = rtc::make_ref_counted<MockAudioDecoderFactory>();
  config.task_queue_factory = CreateDefaultTaskQueueFactory();
  config.audio_device_module = test::MockAudioDeviceModule::CreateNice();

  auto voip_engine = CreateVoipEngine(std::move(config));
  EXPECT_NE(voip_engine, nullptr);
}

}  // namespace
}  // namespace webrtc
