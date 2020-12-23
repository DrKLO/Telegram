/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <memory>

#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/rtp_parameters.h"
#include "api/scoped_refptr.h"
#include "call/adaptation/test/fake_resource.h"
#include "pc/test/fake_periodic_video_source.h"
#include "pc/test/fake_periodic_video_track_source.h"
#include "pc/test/peer_connection_test_wrapper.h"
#include "rtc_base/checks.h"
#include "rtc_base/gunit.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/thread.h"
#include "rtc_base/virtual_socket_server.h"
#include "test/gtest.h"

namespace webrtc {

const int64_t kDefaultTimeoutMs = 5000;

struct TrackWithPeriodicSource {
  rtc::scoped_refptr<VideoTrackInterface> track;
  rtc::scoped_refptr<FakePeriodicVideoTrackSource> periodic_track_source;
};

// Performs an O/A exchange and waits until the signaling state is stable again.
void Negotiate(rtc::scoped_refptr<PeerConnectionTestWrapper> caller,
               rtc::scoped_refptr<PeerConnectionTestWrapper> callee) {
  // Wire up callbacks and listeners such that a full O/A is performed in
  // response to CreateOffer().
  PeerConnectionTestWrapper::Connect(caller.get(), callee.get());
  caller->CreateOffer(PeerConnectionInterface::RTCOfferAnswerOptions());
  caller->WaitForNegotiation();
}

TrackWithPeriodicSource CreateTrackWithPeriodicSource(
    rtc::scoped_refptr<PeerConnectionFactoryInterface> factory) {
  FakePeriodicVideoSource::Config periodic_track_source_config;
  periodic_track_source_config.frame_interval_ms = 100;
  periodic_track_source_config.timestamp_offset_ms = rtc::TimeMillis();
  rtc::scoped_refptr<FakePeriodicVideoTrackSource> periodic_track_source =
      new rtc::RefCountedObject<FakePeriodicVideoTrackSource>(
          periodic_track_source_config, /* remote */ false);
  TrackWithPeriodicSource track_with_source;
  track_with_source.track =
      factory->CreateVideoTrack("PeriodicTrack", periodic_track_source);
  track_with_source.periodic_track_source = periodic_track_source;
  return track_with_source;
}

// Triggers overuse and obtains VideoSinkWants. Adaptation processing happens in
// parallel and this function makes no guarantee that the returnd VideoSinkWants
// have yet to reflect the overuse signal. Used together with EXPECT_TRUE_WAIT
// to "spam overuse until a change is observed".
rtc::VideoSinkWants TriggerOveruseAndGetSinkWants(
    rtc::scoped_refptr<FakeResource> fake_resource,
    const FakePeriodicVideoSource& source) {
  fake_resource->SetUsageState(ResourceUsageState::kOveruse);
  return source.wants();
}

class PeerConnectionAdaptationIntegrationTest : public ::testing::Test {
 public:
  PeerConnectionAdaptationIntegrationTest()
      : virtual_socket_server_(),
        network_thread_(new rtc::Thread(&virtual_socket_server_)),
        worker_thread_(rtc::Thread::Create()) {
    RTC_CHECK(network_thread_->Start());
    RTC_CHECK(worker_thread_->Start());
  }

  rtc::scoped_refptr<PeerConnectionTestWrapper> CreatePcWrapper(
      const char* name) {
    rtc::scoped_refptr<PeerConnectionTestWrapper> pc_wrapper =
        new rtc::RefCountedObject<PeerConnectionTestWrapper>(
            name, network_thread_.get(), worker_thread_.get());
    PeerConnectionInterface::RTCConfiguration config;
    config.sdp_semantics = SdpSemantics::kUnifiedPlan;
    EXPECT_TRUE(pc_wrapper->CreatePc(config, CreateBuiltinAudioEncoderFactory(),
                                     CreateBuiltinAudioDecoderFactory()));
    return pc_wrapper;
  }

 protected:
  rtc::VirtualSocketServer virtual_socket_server_;
  std::unique_ptr<rtc::Thread> network_thread_;
  std::unique_ptr<rtc::Thread> worker_thread_;
};

TEST_F(PeerConnectionAdaptationIntegrationTest,
       ResouceInjectedAfterNegotiationCausesReductionInResolution) {
  auto caller_wrapper = CreatePcWrapper("caller");
  auto caller = caller_wrapper->pc();
  auto callee_wrapper = CreatePcWrapper("callee");

  // Adding a track and negotiating ensures that a VideoSendStream exists.
  TrackWithPeriodicSource track_with_source =
      CreateTrackWithPeriodicSource(caller_wrapper->pc_factory());
  auto sender = caller->AddTrack(track_with_source.track, {}).value();
  Negotiate(caller_wrapper, callee_wrapper);
  // Prefer degrading resolution.
  auto parameters = sender->GetParameters();
  parameters.degradation_preference = DegradationPreference::MAINTAIN_FRAMERATE;
  sender->SetParameters(parameters);

  const auto& source =
      track_with_source.periodic_track_source->fake_periodic_source();
  int pixel_count_before_overuse = source.wants().max_pixel_count;

  // Inject a fake resource and spam kOveruse until resolution becomes limited.
  auto fake_resource = FakeResource::Create("FakeResource");
  caller->AddAdaptationResource(fake_resource);
  EXPECT_TRUE_WAIT(
      TriggerOveruseAndGetSinkWants(fake_resource, source).max_pixel_count <
          pixel_count_before_overuse,
      kDefaultTimeoutMs);
}

TEST_F(PeerConnectionAdaptationIntegrationTest,
       ResouceInjectedBeforeNegotiationCausesReductionInResolution) {
  auto caller_wrapper = CreatePcWrapper("caller");
  auto caller = caller_wrapper->pc();
  auto callee_wrapper = CreatePcWrapper("callee");

  // Inject a fake resource before adding any tracks or negotiating.
  auto fake_resource = FakeResource::Create("FakeResource");
  caller->AddAdaptationResource(fake_resource);

  // Adding a track and negotiating ensures that a VideoSendStream exists.
  TrackWithPeriodicSource track_with_source =
      CreateTrackWithPeriodicSource(caller_wrapper->pc_factory());
  auto sender = caller->AddTrack(track_with_source.track, {}).value();
  Negotiate(caller_wrapper, callee_wrapper);
  // Prefer degrading resolution.
  auto parameters = sender->GetParameters();
  parameters.degradation_preference = DegradationPreference::MAINTAIN_FRAMERATE;
  sender->SetParameters(parameters);

  const auto& source =
      track_with_source.periodic_track_source->fake_periodic_source();
  int pixel_count_before_overuse = source.wants().max_pixel_count;

  // Spam kOveruse until resolution becomes limited.
  EXPECT_TRUE_WAIT(
      TriggerOveruseAndGetSinkWants(fake_resource, source).max_pixel_count <
          pixel_count_before_overuse,
      kDefaultTimeoutMs);
}

}  // namespace webrtc
