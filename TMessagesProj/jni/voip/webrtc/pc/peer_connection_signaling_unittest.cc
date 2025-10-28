/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains tests that check the PeerConnection's signaling state
// machine, as well as tests that check basic, media-agnostic aspects of SDP.

#include <algorithm>
#include <cstdint>
#include <functional>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <tuple>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/create_peerconnection_factory.h"
#include "api/dtls_transport_interface.h"
#include "api/jsep.h"
#include "api/media_types.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/rtp_transceiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/set_local_description_observer_interface.h"
#include "api/set_remote_description_observer_interface.h"
#include "api/video_codecs/video_decoder_factory_template.h"
#include "api/video_codecs/video_decoder_factory_template_dav1d_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_decoder_factory_template_open_h264_adapter.h"
#include "api/video_codecs/video_encoder_factory_template.h"
#include "api/video_codecs/video_encoder_factory_template_libaom_av1_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp8_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_libvpx_vp9_adapter.h"
#include "api/video_codecs/video_encoder_factory_template_open_h264_adapter.h"
#include "media/base/codec.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/port_allocator.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_proxy.h"
#include "pc/peer_connection_wrapper.h"
#include "pc/sdp_utils.h"
#include "pc/session_description.h"
#include "pc/test/mock_peer_connection_observers.h"
#include "rtc_base/checks.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/thread.h"
#include "test/gmock.h"
#include "test/gtest.h"
#ifdef WEBRTC_ANDROID
#include "pc/test/android_test_initializer.h"
#endif
#include "pc/test/fake_audio_capture_module.h"
#include "pc/test/fake_rtc_certificate_generator.h"
#include "rtc_base/gunit.h"
#include "rtc_base/virtual_socket_server.h"

namespace webrtc {

using SignalingState = PeerConnectionInterface::SignalingState;
using RTCConfiguration = PeerConnectionInterface::RTCConfiguration;
using RTCOfferAnswerOptions = PeerConnectionInterface::RTCOfferAnswerOptions;
using ::testing::Bool;
using ::testing::Combine;
using ::testing::StartsWith;
using ::testing::Values;

namespace {
const int64_t kWaitTimeout = 10000;
}  // namespace

class PeerConnectionWrapperForSignalingTest : public PeerConnectionWrapper {
 public:
  using PeerConnectionWrapper::PeerConnectionWrapper;

  bool initial_offerer() {
    return GetInternalPeerConnection()->initial_offerer();
  }

  PeerConnection* GetInternalPeerConnection() {
    auto* pci =
        static_cast<PeerConnectionProxyWithInternal<PeerConnectionInterface>*>(
            pc());
    return static_cast<PeerConnection*>(pci->internal());
  }
};

class ExecuteFunctionOnCreateSessionDescriptionObserver
    : public CreateSessionDescriptionObserver {
 public:
  ExecuteFunctionOnCreateSessionDescriptionObserver(
      std::function<void(SessionDescriptionInterface*)> function)
      : function_(std::move(function)) {}
  ~ExecuteFunctionOnCreateSessionDescriptionObserver() override {
    RTC_DCHECK(was_called_);
  }

  bool was_called() const { return was_called_; }

  void OnSuccess(SessionDescriptionInterface* desc) override {
    RTC_DCHECK(!was_called_);
    was_called_ = true;
    function_(desc);
  }

  void OnFailure(RTCError error) override { RTC_DCHECK_NOTREACHED(); }

 private:
  bool was_called_ = false;
  std::function<void(SessionDescriptionInterface*)> function_;
};

class PeerConnectionSignalingBaseTest : public ::testing::Test {
 protected:
  typedef std::unique_ptr<PeerConnectionWrapperForSignalingTest> WrapperPtr;

  explicit PeerConnectionSignalingBaseTest(SdpSemantics sdp_semantics)
      : vss_(new rtc::VirtualSocketServer()),
        main_(vss_.get()),
        sdp_semantics_(sdp_semantics) {
#ifdef WEBRTC_ANDROID
    InitializeAndroidObjects();
#endif
    pc_factory_ = CreatePeerConnectionFactory(
        rtc::Thread::Current(), rtc::Thread::Current(), rtc::Thread::Current(),
        rtc::scoped_refptr<AudioDeviceModule>(FakeAudioCaptureModule::Create()),
        CreateBuiltinAudioEncoderFactory(), CreateBuiltinAudioDecoderFactory(),
        std::make_unique<VideoEncoderFactoryTemplate<
            LibvpxVp8EncoderTemplateAdapter, LibvpxVp9EncoderTemplateAdapter,
            OpenH264EncoderTemplateAdapter, LibaomAv1EncoderTemplateAdapter>>(),
        std::make_unique<VideoDecoderFactoryTemplate<
            LibvpxVp8DecoderTemplateAdapter, LibvpxVp9DecoderTemplateAdapter,
            OpenH264DecoderTemplateAdapter, Dav1dDecoderTemplateAdapter>>(),
        nullptr /* audio_mixer */, nullptr /* audio_processing */);
  }

  WrapperPtr CreatePeerConnection() {
    return CreatePeerConnection(RTCConfiguration());
  }

  WrapperPtr CreatePeerConnection(const RTCConfiguration& config) {
    auto observer = std::make_unique<MockPeerConnectionObserver>();
    RTCConfiguration modified_config = config;
    modified_config.sdp_semantics = sdp_semantics_;
    auto result = pc_factory_->CreatePeerConnectionOrError(
        modified_config, PeerConnectionDependencies(observer.get()));
    if (!result.ok()) {
      return nullptr;
    }

    observer->SetPeerConnectionInterface(result.value().get());
    return std::make_unique<PeerConnectionWrapperForSignalingTest>(
        pc_factory_, result.MoveValue(), std::move(observer));
  }

  // Accepts the same arguments as CreatePeerConnection and adds default audio
  // and video tracks.
  template <typename... Args>
  WrapperPtr CreatePeerConnectionWithAudioVideo(Args&&... args) {
    auto wrapper = CreatePeerConnection(std::forward<Args>(args)...);
    if (!wrapper) {
      return nullptr;
    }
    wrapper->AddAudioTrack("a");
    wrapper->AddVideoTrack("v");
    return wrapper;
  }

  int NumberOfDtlsTransports(const WrapperPtr& pc_wrapper) {
    std::set<DtlsTransportInterface*> transports;
    auto transceivers = pc_wrapper->pc()->GetTransceivers();

    for (auto& transceiver : transceivers) {
      if (transceiver->sender()->dtls_transport()) {
        EXPECT_TRUE(transceiver->receiver()->dtls_transport());
        EXPECT_EQ(transceiver->sender()->dtls_transport().get(),
                  transceiver->receiver()->dtls_transport().get());
        transports.insert(transceiver->sender()->dtls_transport().get());
      } else {
        // If one transceiver is missing, they all should be.
        EXPECT_EQ(0UL, transports.size());
      }
    }
    return transports.size();
  }

  bool HasDtlsTransport(const WrapperPtr& pc_wrapper) {
    return NumberOfDtlsTransports(pc_wrapper) > 0;
  }

  std::unique_ptr<rtc::VirtualSocketServer> vss_;
  rtc::AutoSocketServerThread main_;
  rtc::scoped_refptr<PeerConnectionFactoryInterface> pc_factory_;
  const SdpSemantics sdp_semantics_;
};

class PeerConnectionSignalingTest
    : public PeerConnectionSignalingBaseTest,
      public ::testing::WithParamInterface<SdpSemantics> {
 protected:
  PeerConnectionSignalingTest() : PeerConnectionSignalingBaseTest(GetParam()) {}
};

TEST_P(PeerConnectionSignalingTest, SetLocalOfferTwiceWorks) {
  auto caller = CreatePeerConnection();

  EXPECT_TRUE(caller->SetLocalDescription(caller->CreateOffer()));
  EXPECT_TRUE(caller->SetLocalDescription(caller->CreateOffer()));
}

TEST_P(PeerConnectionSignalingTest, SetRemoteOfferTwiceWorks) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();

  EXPECT_TRUE(callee->SetRemoteDescription(caller->CreateOffer()));
  EXPECT_TRUE(callee->SetRemoteDescription(caller->CreateOffer()));
}

TEST_P(PeerConnectionSignalingTest, FailToSetNullLocalDescription) {
  auto caller = CreatePeerConnection();
  std::string error;
  ASSERT_FALSE(caller->SetLocalDescription(nullptr, &error));
  EXPECT_EQ("SessionDescription is NULL.", error);
}

TEST_P(PeerConnectionSignalingTest, FailToSetNullRemoteDescription) {
  auto caller = CreatePeerConnection();
  std::string error;
  ASSERT_FALSE(caller->SetRemoteDescription(nullptr, &error));
  EXPECT_EQ("SessionDescription is NULL.", error);
}

// The following parameterized test verifies that calls to various signaling
// methods on PeerConnection will succeed/fail depending on what is the
// PeerConnection's signaling state. Note that the test tries many different
// forms of SignalingState::kClosed by arriving at a valid state then calling
// `Close()`. This is intended to catch cases where the PeerConnection signaling
// method ignores the closed flag but may work/not work because of the single
// state the PeerConnection was created in before it was closed.

class PeerConnectionSignalingStateTest
    : public PeerConnectionSignalingBaseTest,
      public ::testing::WithParamInterface<
          std::tuple<SdpSemantics, SignalingState, bool>> {
 protected:
  PeerConnectionSignalingStateTest()
      : PeerConnectionSignalingBaseTest(std::get<0>(GetParam())),
        state_under_test_(std::make_tuple(std::get<1>(GetParam()),
                                          std::get<2>(GetParam()))) {}

  RTCConfiguration GetConfig() {
    RTCConfiguration config;
    config.certificates.push_back(
        FakeRTCCertificateGenerator::GenerateCertificate());
    return config;
  }

  WrapperPtr CreatePeerConnectionUnderTest() {
    return CreatePeerConnectionInState(state_under_test_);
  }

  WrapperPtr CreatePeerConnectionInState(SignalingState state) {
    return CreatePeerConnectionInState(std::make_tuple(state, false));
  }

  WrapperPtr CreatePeerConnectionInState(
      std::tuple<SignalingState, bool> state_tuple) {
    SignalingState state = std::get<0>(state_tuple);
    bool closed = std::get<1>(state_tuple);

    auto wrapper = CreatePeerConnectionWithAudioVideo(GetConfig());
    switch (state) {
      case SignalingState::kStable: {
        break;
      }
      case SignalingState::kHaveLocalOffer: {
        wrapper->SetLocalDescription(wrapper->CreateOffer());
        break;
      }
      case SignalingState::kHaveLocalPrAnswer: {
        auto caller = CreatePeerConnectionWithAudioVideo(GetConfig());
        wrapper->SetRemoteDescription(caller->CreateOffer());
        auto answer = wrapper->CreateAnswer();
        wrapper->SetLocalDescription(
            CloneSessionDescriptionAsType(answer.get(), SdpType::kPrAnswer));
        break;
      }
      case SignalingState::kHaveRemoteOffer: {
        auto caller = CreatePeerConnectionWithAudioVideo(GetConfig());
        wrapper->SetRemoteDescription(caller->CreateOffer());
        break;
      }
      case SignalingState::kHaveRemotePrAnswer: {
        auto callee = CreatePeerConnectionWithAudioVideo(GetConfig());
        callee->SetRemoteDescription(wrapper->CreateOfferAndSetAsLocal());
        auto answer = callee->CreateAnswer();
        wrapper->SetRemoteDescription(
            CloneSessionDescriptionAsType(answer.get(), SdpType::kPrAnswer));
        break;
      }
      case SignalingState::kClosed: {
        RTC_DCHECK_NOTREACHED()
            << "Set the second member of the tuple to true to "
               "achieve a closed state from an existing, valid "
               "state.";
      }
    }

    RTC_DCHECK_EQ(state, wrapper->pc()->signaling_state());

    if (closed) {
      wrapper->pc()->Close();
      RTC_DCHECK_EQ(SignalingState::kClosed, wrapper->signaling_state());
    }

    return wrapper;
  }

  std::tuple<SignalingState, bool> state_under_test_;
};

TEST_P(PeerConnectionSignalingStateTest, CreateOffer) {
  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() != SignalingState::kClosed) {
    EXPECT_TRUE(wrapper->CreateOffer());
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->CreateOffer(RTCOfferAnswerOptions(), &error));
    EXPECT_EQ(error, "CreateOffer called when PeerConnection is closed.");
  }
}

TEST_P(PeerConnectionSignalingStateTest, CreateAnswer) {
  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kHaveLocalPrAnswer ||
      wrapper->signaling_state() == SignalingState::kHaveRemoteOffer) {
    EXPECT_TRUE(wrapper->CreateAnswer());
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->CreateAnswer(RTCOfferAnswerOptions(), &error));
    EXPECT_EQ(error,
              "PeerConnection cannot create an answer in a state other than "
              "have-remote-offer or have-local-pranswer.");
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetLocalOffer) {
  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kStable ||
      wrapper->signaling_state() == SignalingState::kHaveLocalOffer) {
    // Need to call CreateOffer on the PeerConnection under test, otherwise when
    // setting the local offer it will want to verify the DTLS fingerprint
    // against the locally generated certificate, but without a call to
    // CreateOffer the certificate will never be generated.
    EXPECT_TRUE(wrapper->SetLocalDescription(wrapper->CreateOffer()));
  } else {
    auto wrapper_for_offer =
        CreatePeerConnectionInState(SignalingState::kHaveLocalOffer);
    auto offer =
        CloneSessionDescription(wrapper_for_offer->pc()->local_description());

    std::string error;
    ASSERT_FALSE(wrapper->SetLocalDescription(std::move(offer), &error));
    EXPECT_THAT(
        error,
        StartsWith("Failed to set local offer sdp: Called in wrong state:"));
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetLocalPrAnswer) {
  auto wrapper_for_pranswer =
      CreatePeerConnectionInState(SignalingState::kHaveLocalPrAnswer);
  auto pranswer =
      CloneSessionDescription(wrapper_for_pranswer->pc()->local_description());

  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kHaveLocalPrAnswer ||
      wrapper->signaling_state() == SignalingState::kHaveRemoteOffer) {
    EXPECT_TRUE(wrapper->SetLocalDescription(std::move(pranswer)));
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->SetLocalDescription(std::move(pranswer), &error));
    EXPECT_THAT(
        error,
        StartsWith("Failed to set local pranswer sdp: Called in wrong state:"));
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetLocalAnswer) {
  auto wrapper_for_answer =
      CreatePeerConnectionInState(SignalingState::kHaveRemoteOffer);
  auto answer = wrapper_for_answer->CreateAnswer();

  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kHaveLocalPrAnswer ||
      wrapper->signaling_state() == SignalingState::kHaveRemoteOffer) {
    EXPECT_TRUE(wrapper->SetLocalDescription(std::move(answer)));
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->SetLocalDescription(std::move(answer), &error));
    EXPECT_THAT(
        error,
        StartsWith("Failed to set local answer sdp: Called in wrong state:"));
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetRemoteOffer) {
  auto wrapper_for_offer =
      CreatePeerConnectionInState(SignalingState::kHaveRemoteOffer);
  auto offer =
      CloneSessionDescription(wrapper_for_offer->pc()->remote_description());

  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kStable ||
      wrapper->signaling_state() == SignalingState::kHaveRemoteOffer) {
    EXPECT_TRUE(wrapper->SetRemoteDescription(std::move(offer)));
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->SetRemoteDescription(std::move(offer), &error));
    EXPECT_THAT(
        error,
        StartsWith("Failed to set remote offer sdp: Called in wrong state:"));
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetRemotePrAnswer) {
  auto wrapper_for_pranswer =
      CreatePeerConnectionInState(SignalingState::kHaveRemotePrAnswer);
  auto pranswer =
      CloneSessionDescription(wrapper_for_pranswer->pc()->remote_description());

  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kHaveLocalOffer ||
      wrapper->signaling_state() == SignalingState::kHaveRemotePrAnswer) {
    EXPECT_TRUE(wrapper->SetRemoteDescription(std::move(pranswer)));
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->SetRemoteDescription(std::move(pranswer), &error));
    EXPECT_THAT(
        error,
        StartsWith(
            "Failed to set remote pranswer sdp: Called in wrong state:"));
  }
}

TEST_P(PeerConnectionSignalingStateTest, SetRemoteAnswer) {
  auto wrapper_for_answer =
      CreatePeerConnectionInState(SignalingState::kHaveRemoteOffer);
  auto answer = wrapper_for_answer->CreateAnswer();

  auto wrapper = CreatePeerConnectionUnderTest();
  if (wrapper->signaling_state() == SignalingState::kHaveLocalOffer ||
      wrapper->signaling_state() == SignalingState::kHaveRemotePrAnswer) {
    EXPECT_TRUE(wrapper->SetRemoteDescription(std::move(answer)));
  } else {
    std::string error;
    ASSERT_FALSE(wrapper->SetRemoteDescription(std::move(answer), &error));
    EXPECT_THAT(
        error,
        StartsWith("Failed to set remote answer sdp: Called in wrong state:"));
  }
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionSignalingTest,
                         PeerConnectionSignalingStateTest,
                         Combine(Values(SdpSemantics::kPlanB_DEPRECATED,
                                        SdpSemantics::kUnifiedPlan),
                                 Values(SignalingState::kStable,
                                        SignalingState::kHaveLocalOffer,
                                        SignalingState::kHaveLocalPrAnswer,
                                        SignalingState::kHaveRemoteOffer,
                                        SignalingState::kHaveRemotePrAnswer),
                                 Bool()));

// Test that CreateAnswer fails if a round of offer/answer has been done and
// the PeerConnection is in the stable state.
TEST_P(PeerConnectionSignalingTest, CreateAnswerFailsIfStable) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();

  ASSERT_TRUE(caller->ExchangeOfferAnswerWith(callee.get()));

  ASSERT_EQ(SignalingState::kStable, caller->signaling_state());
  EXPECT_FALSE(caller->CreateAnswer());

  ASSERT_EQ(SignalingState::kStable, callee->signaling_state());
  EXPECT_FALSE(callee->CreateAnswer());
}

// According to https://tools.ietf.org/html/rfc3264#section-8, the session id
// stays the same but the version must be incremented if a later, different
// session description is generated. These two tests verify that is the case for
// both offers and answers.
TEST_P(PeerConnectionSignalingTest,
       SessionVersionIncrementedInSubsequentDifferentOffer) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();

  auto original_offer = caller->CreateOfferAndSetAsLocal();
  const std::string original_id = original_offer->session_id();
  const std::string original_version = original_offer->session_version();

  ASSERT_TRUE(callee->SetRemoteDescription(std::move(original_offer)));
  ASSERT_TRUE(caller->SetRemoteDescription(callee->CreateAnswer()));

  // Add track to get a different offer.
  caller->AddAudioTrack("a");

  auto later_offer = caller->CreateOffer();

  EXPECT_EQ(original_id, later_offer->session_id());
  EXPECT_LT(rtc::FromString<uint64_t>(original_version),
            rtc::FromString<uint64_t>(later_offer->session_version()));
}
TEST_P(PeerConnectionSignalingTest,
       SessionVersionIncrementedInSubsequentDifferentAnswer) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  auto original_answer = callee->CreateAnswer();
  const std::string original_id = original_answer->session_id();
  const std::string original_version = original_answer->session_version();

  // Add track to get a different answer.
  callee->AddAudioTrack("a");

  auto later_answer = callee->CreateAnswer();

  EXPECT_EQ(original_id, later_answer->session_id());
  EXPECT_LT(rtc::FromString<uint64_t>(original_version),
            rtc::FromString<uint64_t>(later_answer->session_version()));
}

TEST_P(PeerConnectionSignalingTest, InitiatorFlagSetOnCallerAndNotOnCallee) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  EXPECT_FALSE(caller->initial_offerer());
  EXPECT_FALSE(callee->initial_offerer());

  ASSERT_TRUE(callee->SetRemoteDescription(caller->CreateOfferAndSetAsLocal()));

  EXPECT_TRUE(caller->initial_offerer());
  EXPECT_FALSE(callee->initial_offerer());

  ASSERT_TRUE(
      caller->SetRemoteDescription(callee->CreateAnswerAndSetAsLocal()));

  EXPECT_TRUE(caller->initial_offerer());
  EXPECT_FALSE(callee->initial_offerer());
}

// Test creating a PeerConnection, request multiple offers, destroy the
// PeerConnection and make sure we get success/failure callbacks for all of the
// requests.
// Background: crbug.com/507307
TEST_P(PeerConnectionSignalingTest, CreateOffersAndShutdown) {
  auto caller = CreatePeerConnection();

  RTCOfferAnswerOptions options;
  options.offer_to_receive_audio =
      RTCOfferAnswerOptions::kOfferToReceiveMediaTrue;

  rtc::scoped_refptr<MockCreateSessionDescriptionObserver> observers[100];
  for (auto& observer : observers) {
    observer = rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
    caller->pc()->CreateOffer(observer.get(), options);
  }

  // Destroy the PeerConnection.
  caller.reset(nullptr);

  for (auto& observer : observers) {
    // We expect to have received a notification now even if the PeerConnection
    // was terminated. The offer creation may or may not have succeeded, but we
    // must have received a notification.
    EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  }
}

// Similar to the above test, but by closing the PC first the CreateOffer() will
// fail "early", which triggers a codepath where the PeerConnection is
// reponsible for invoking the observer, instead of the normal codepath where
// the WebRtcSessionDescriptionFactory is responsible for it.
TEST_P(PeerConnectionSignalingTest, CloseCreateOfferAndShutdown) {
  auto caller = CreatePeerConnection();
  auto observer = rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
  caller->pc()->Close();
  caller->pc()->CreateOffer(observer.get(), RTCOfferAnswerOptions());
  caller.reset(nullptr);
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
}

TEST_P(PeerConnectionSignalingTest,
       ImplicitCreateOfferAndShutdownWithOldObserver) {
  auto caller = CreatePeerConnection();
  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(observer.get());
  caller.reset(nullptr);
  // The old observer does not get invoked because posted messages are lost.
  EXPECT_FALSE(observer->called());
}

TEST_P(PeerConnectionSignalingTest, ImplicitCreateOfferAndShutdown) {
  auto caller = CreatePeerConnection();
  auto observer = rtc::make_ref_counted<FakeSetLocalDescriptionObserver>();
  caller->pc()->SetLocalDescription(observer);
  caller.reset(nullptr);
  // The new observer gets invoked because it is called immediately.
  EXPECT_TRUE(observer->called());
  EXPECT_FALSE(observer->error().ok());
}

TEST_P(PeerConnectionSignalingTest,
       CloseBeforeImplicitCreateOfferAndShutdownWithOldObserver) {
  auto caller = CreatePeerConnection();
  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->Close();
  caller->pc()->SetLocalDescription(observer.get());
  caller.reset(nullptr);
  // The old observer does not get invoked because posted messages are lost.
  EXPECT_FALSE(observer->called());
}

TEST_P(PeerConnectionSignalingTest, CloseBeforeImplicitCreateOfferAndShutdown) {
  auto caller = CreatePeerConnection();
  auto observer = rtc::make_ref_counted<FakeSetLocalDescriptionObserver>();
  caller->pc()->Close();
  caller->pc()->SetLocalDescription(observer);
  caller.reset(nullptr);
  // The new observer gets invoked because it is called immediately.
  EXPECT_TRUE(observer->called());
  EXPECT_FALSE(observer->error().ok());
}

TEST_P(PeerConnectionSignalingTest,
       CloseAfterImplicitCreateOfferAndShutdownWithOldObserver) {
  auto caller = CreatePeerConnection();
  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(observer.get());
  caller->pc()->Close();
  caller.reset(nullptr);
  // The old observer does not get invoked because posted messages are lost.
  EXPECT_FALSE(observer->called());
}

TEST_P(PeerConnectionSignalingTest, CloseAfterImplicitCreateOfferAndShutdown) {
  auto caller = CreatePeerConnection();
  auto observer = rtc::make_ref_counted<FakeSetLocalDescriptionObserver>();
  caller->pc()->SetLocalDescription(observer);
  caller->pc()->Close();
  caller.reset(nullptr);
  // The new observer gets invoked because it is called immediately.
  EXPECT_TRUE(observer->called());
  EXPECT_FALSE(observer->error().ok());
}

TEST_P(PeerConnectionSignalingTest,
       SetLocalDescriptionNewObserverIsInvokedImmediately) {
  auto caller = CreatePeerConnection();
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  auto observer = rtc::make_ref_counted<FakeSetLocalDescriptionObserver>();
  caller->pc()->SetLocalDescription(std::move(offer), observer);
  // The new observer is invoked immediately.
  EXPECT_TRUE(observer->called());
  EXPECT_TRUE(observer->error().ok());
}

TEST_P(PeerConnectionSignalingTest,
       SetLocalDescriptionOldObserverIsInvokedInAPostedMessage) {
  auto caller = CreatePeerConnection();
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(observer.get(), offer.release());
  // The old observer is not invoked immediately.
  EXPECT_FALSE(observer->called());
  // Process all currently pending messages by waiting for a posted task to run.
  bool checkpoint_reached = false;
  rtc::Thread::Current()->PostTask(
      [&checkpoint_reached] { checkpoint_reached = true; });
  EXPECT_TRUE_WAIT(checkpoint_reached, kWaitTimeout);
  // If resolving the observer was pending, it must now have been called.
  EXPECT_TRUE(observer->called());
}

TEST_P(PeerConnectionSignalingTest, SetRemoteDescriptionExecutesImmediately) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  // This offer will cause receivers to be created.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  // By not waiting for the observer's callback we can verify that the operation
  // executed immediately.
  callee->pc()->SetRemoteDescription(
      std::move(offer),
      rtc::make_ref_counted<FakeSetRemoteDescriptionObserver>());
  EXPECT_EQ(2u, callee->pc()->GetReceivers().size());
}

TEST_P(PeerConnectionSignalingTest, CreateOfferBlocksSetRemoteDescription) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  // This offer will cause receivers to be created.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  EXPECT_EQ(0u, callee->pc()->GetReceivers().size());
  auto offer_observer =
      rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
  // Synchronously invoke CreateOffer() and SetRemoteDescription(). The
  // SetRemoteDescription() operation should be chained to be executed
  // asynchronously, when CreateOffer() completes.
  callee->pc()->CreateOffer(offer_observer.get(), RTCOfferAnswerOptions());
  callee->pc()->SetRemoteDescription(
      std::move(offer),
      rtc::make_ref_counted<FakeSetRemoteDescriptionObserver>());
  // CreateOffer() is asynchronous; without message processing this operation
  // should not have completed.
  EXPECT_FALSE(offer_observer->called());
  // Due to chaining, the receivers should not have been created by the offer
  // yet.
  EXPECT_EQ(0u, callee->pc()->GetReceivers().size());
  // EXPECT_TRUE_WAIT causes messages to be processed...
  EXPECT_TRUE_WAIT(offer_observer->called(), kWaitTimeout);
  // Now that the offer has been completed, SetRemoteDescription() will have
  // been executed next in the chain.
  EXPECT_EQ(2u, callee->pc()->GetReceivers().size());
}

TEST_P(PeerConnectionSignalingTest,
       ParameterlessSetLocalDescriptionCreatesOffer) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(observer.get());

  // The offer is created asynchronously; message processing is needed for it to
  // complete.
  EXPECT_FALSE(observer->called());
  EXPECT_FALSE(caller->pc()->pending_local_description());
  EXPECT_EQ(PeerConnection::kStable, caller->signaling_state());

  // Wait for messages to be processed.
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  EXPECT_TRUE(observer->result());
  EXPECT_TRUE(caller->pc()->pending_local_description());
  EXPECT_EQ(SdpType::kOffer,
            caller->pc()->pending_local_description()->GetType());
  EXPECT_EQ(PeerConnection::kHaveLocalOffer, caller->signaling_state());
}

TEST_P(PeerConnectionSignalingTest,
       ParameterlessSetLocalDescriptionCreatesAnswer) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  callee->SetRemoteDescription(caller->CreateOffer());
  EXPECT_EQ(PeerConnection::kHaveRemoteOffer, callee->signaling_state());

  auto observer = MockSetSessionDescriptionObserver::Create();
  callee->pc()->SetLocalDescription(observer.get());

  // The answer is created asynchronously; message processing is needed for it
  // to complete.
  EXPECT_FALSE(observer->called());
  EXPECT_FALSE(callee->pc()->current_local_description());

  // Wait for messages to be processed.
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  EXPECT_TRUE(observer->result());
  EXPECT_TRUE(callee->pc()->current_local_description());
  EXPECT_EQ(SdpType::kAnswer,
            callee->pc()->current_local_description()->GetType());
  EXPECT_EQ(PeerConnection::kStable, callee->signaling_state());
}

TEST_P(PeerConnectionSignalingTest,
       ParameterlessSetLocalDescriptionFullExchange) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnectionWithAudioVideo();

  // SetLocalDescription(), implicitly creating an offer.
  auto caller_set_local_description_observer =
      MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(
      caller_set_local_description_observer.get());
  EXPECT_TRUE_WAIT(caller_set_local_description_observer->called(),
                   kWaitTimeout);
  ASSERT_TRUE(caller->pc()->pending_local_description());

  // SetRemoteDescription(offer)
  auto callee_set_remote_description_observer =
      MockSetSessionDescriptionObserver::Create();
  callee->pc()->SetRemoteDescription(
      callee_set_remote_description_observer.get(),
      CloneSessionDescription(caller->pc()->pending_local_description())
          .release());

  // SetLocalDescription(), implicitly creating an answer.
  auto callee_set_local_description_observer =
      MockSetSessionDescriptionObserver::Create();
  callee->pc()->SetLocalDescription(
      callee_set_local_description_observer.get());
  EXPECT_TRUE_WAIT(callee_set_local_description_observer->called(),
                   kWaitTimeout);
  // Chaining guarantees SetRemoteDescription() happened before
  // SetLocalDescription().
  EXPECT_TRUE(callee_set_remote_description_observer->called());
  EXPECT_TRUE(callee->pc()->current_local_description());

  // SetRemoteDescription(answer)
  auto caller_set_remote_description_observer =
      MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetRemoteDescription(
      caller_set_remote_description_observer.get(),
      CloneSessionDescription(callee->pc()->current_local_description())
          .release());
  EXPECT_TRUE_WAIT(caller_set_remote_description_observer->called(),
                   kWaitTimeout);

  EXPECT_EQ(PeerConnection::kStable, caller->signaling_state());
  EXPECT_EQ(PeerConnection::kStable, callee->signaling_state());
}

TEST_P(PeerConnectionSignalingTest,
       ParameterlessSetLocalDescriptionCloseBeforeCreatingOffer) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->Close();
  caller->pc()->SetLocalDescription(observer.get());

  // The operation should fail asynchronously.
  EXPECT_FALSE(observer->called());
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  EXPECT_FALSE(observer->result());
  // This did not affect the signaling state.
  EXPECT_EQ(PeerConnection::kClosed, caller->pc()->signaling_state());
  EXPECT_EQ(
      "SetLocalDescription failed to create session description - "
      "SetLocalDescription called when PeerConnection is closed.",
      observer->error());
}

TEST_P(PeerConnectionSignalingTest,
       ParameterlessSetLocalDescriptionCloseWhileCreatingOffer) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  auto observer = MockSetSessionDescriptionObserver::Create();
  caller->pc()->SetLocalDescription(observer.get());
  caller->pc()->Close();

  // The operation should fail asynchronously.
  EXPECT_FALSE(observer->called());
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  EXPECT_FALSE(observer->result());
  // This did not affect the signaling state.
  EXPECT_EQ(PeerConnection::kClosed, caller->pc()->signaling_state());
  EXPECT_EQ(
      "SetLocalDescription failed to create session description - "
      "CreateOffer failed because the session was shut down",
      observer->error());
}

TEST_P(PeerConnectionSignalingTest, UnsupportedContentType) {
  auto caller = CreatePeerConnection();

  // Call setRemoteDescription with a m= line we don't understand.
  std::string sdp =
      "v=0\r\n"
      "o=- 18446744069414584320 18446462598732840960 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "m=bogus 9 FOO 0 8\r\n"
      "c=IN IP4 0.0.0.0\r\n"
      "a=mid:bogusmid\r\n";
  std::unique_ptr<SessionDescriptionInterface> remote_description =
      CreateSessionDescription(SdpType::kOffer, sdp, nullptr);

  EXPECT_TRUE(caller->SetRemoteDescription(std::move(remote_description)));

  // Assert we respond back with something meaningful.
  auto answer = caller->CreateAnswer();
  ASSERT_EQ(answer->description()->contents().size(), 1u);
  EXPECT_NE(answer->description()
                ->contents()[0]
                .media_description()
                ->as_unsupported(),
            nullptr);
  EXPECT_EQ(answer->description()
                ->contents()[0]
                .media_description()
                ->as_unsupported()
                ->media_type(),
            "bogus");
  EXPECT_TRUE(answer->description()->contents()[0].rejected);
  EXPECT_EQ(answer->description()->contents()[0].mid(), "bogusmid");
  EXPECT_EQ(
      answer->description()->contents()[0].media_description()->protocol(),
      "FOO");
  EXPECT_FALSE(
      answer->description()->contents()[0].media_description()->has_codecs());

  EXPECT_TRUE(caller->SetLocalDescription(std::move(answer)));

  // Assert we keep this in susequent offers.
  auto offer = caller->CreateOffer();
  EXPECT_EQ(offer->description()
                ->contents()[0]
                .media_description()
                ->as_unsupported()
                ->media_type(),
            "bogus");
  EXPECT_TRUE(offer->description()->contents()[0].rejected);
  EXPECT_EQ(offer->description()->contents()[0].media_description()->protocol(),
            "FOO");
  EXPECT_EQ(offer->description()->contents()[0].mid(), "bogusmid");
  EXPECT_FALSE(
      offer->description()->contents()[0].media_description()->has_codecs());
  EXPECT_TRUE(caller->SetLocalDescription(std::move(offer)));
}

TEST_P(PeerConnectionSignalingTest, ReceiveFlexFec) {
  auto caller = CreatePeerConnection();

  std::string sdp =
      "v=0\r\n"
      "o=- 8403615332048243445 2 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "a=group:BUNDLE 0\r\n"
      "m=video 9 UDP/TLS/RTP/SAVPF 102 122\r\n"
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp:9 IN IP4 0.0.0.0\r\n"
      "a=ice-ufrag:IZeV\r\n"
      "a=ice-pwd:uaZhQD4rYM/Tta2qWBT1Bbt4\r\n"
      "a=ice-options:trickle\r\n"
      "a=fingerprint:sha-256 "
      "D8:6C:3D:FA:23:E2:2C:63:11:2D:D0:86:BE:C4:D0:65:F9:42:F7:1C:06:04:27:E6:"
      "1C:2C:74:01:8D:50:67:23\r\n"
      "a=setup:actpass\r\n"
      "a=mid:0\r\n"
      "a=sendrecv\r\n"
      "a=msid:stream track\r\n"
      "a=rtcp-mux\r\n"
      "a=rtcp-rsize\r\n"
      "a=rtpmap:102 VP8/90000\r\n"
      "a=rtcp-fb:102 goog-remb\r\n"
      "a=rtcp-fb:102 transport-cc\r\n"
      "a=rtcp-fb:102 ccm fir\r\n"
      "a=rtcp-fb:102 nack\r\n"
      "a=rtcp-fb:102 nack pli\r\n"
      "a=rtpmap:122 flexfec-03/90000\r\n"
      "a=fmtp:122 repair-window=10000000\r\n"
      "a=ssrc-group:FEC-FR 1224551896 1953032773\r\n"
      "a=ssrc:1224551896 cname:/exJcmhSLpyu9FgV\r\n"
      "a=ssrc:1953032773 cname:/exJcmhSLpyu9FgV\r\n";
  std::unique_ptr<SessionDescriptionInterface> remote_description =
      CreateSessionDescription(SdpType::kOffer, sdp, nullptr);

  EXPECT_TRUE(caller->SetRemoteDescription(std::move(remote_description)));

  auto answer = caller->CreateAnswer();
  ASSERT_EQ(answer->description()->contents().size(), 1u);
  ASSERT_NE(answer->description()->contents()[0].media_description(), nullptr);
  auto codecs = answer->description()
                    ->contents()[0]
                    .media_description()
                    ->codecs();
  ASSERT_EQ(codecs.size(), 2u);
  EXPECT_EQ(codecs[1].name, "flexfec-03");

  EXPECT_TRUE(caller->SetLocalDescription(std::move(answer)));
}

TEST_P(PeerConnectionSignalingTest, ReceiveFlexFecReoffer) {
  auto caller = CreatePeerConnection();

  std::string sdp =
      "v=0\r\n"
      "o=- 8403615332048243445 2 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "a=group:BUNDLE 0\r\n"
      "m=video 9 UDP/TLS/RTP/SAVPF 102 35\r\n"
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp:9 IN IP4 0.0.0.0\r\n"
      "a=ice-ufrag:IZeV\r\n"
      "a=ice-pwd:uaZhQD4rYM/Tta2qWBT1Bbt4\r\n"
      "a=ice-options:trickle\r\n"
      "a=fingerprint:sha-256 "
      "D8:6C:3D:FA:23:E2:2C:63:11:2D:D0:86:BE:C4:D0:65:F9:42:F7:1C:06:04:27:E6:"
      "1C:2C:74:01:8D:50:67:23\r\n"
      "a=setup:actpass\r\n"
      "a=mid:0\r\n"
      "a=sendrecv\r\n"
      "a=msid:stream track\r\n"
      "a=rtcp-mux\r\n"
      "a=rtcp-rsize\r\n"
      "a=rtpmap:102 VP8/90000\r\n"
      "a=rtcp-fb:102 goog-remb\r\n"
      "a=rtcp-fb:102 transport-cc\r\n"
      "a=rtcp-fb:102 ccm fir\r\n"
      "a=rtcp-fb:102 nack\r\n"
      "a=rtcp-fb:102 nack pli\r\n"
      "a=rtpmap:35 flexfec-03/90000\r\n"
      "a=fmtp:35 repair-window=10000000\r\n"
      "a=ssrc-group:FEC-FR 1224551896 1953032773\r\n"
      "a=ssrc:1224551896 cname:/exJcmhSLpyu9FgV\r\n"
      "a=ssrc:1953032773 cname:/exJcmhSLpyu9FgV\r\n";
  std::unique_ptr<SessionDescriptionInterface> remote_description =
      CreateSessionDescription(SdpType::kOffer, sdp, nullptr);

  EXPECT_TRUE(caller->SetRemoteDescription(std::move(remote_description)));

  auto answer = caller->CreateAnswer();
  ASSERT_EQ(answer->description()->contents().size(), 1u);
  ASSERT_NE(answer->description()->contents()[0].media_description(), nullptr);
  auto codecs = answer->description()
                    ->contents()[0]
                    .media_description()
                    ->codecs();
  ASSERT_EQ(codecs.size(), 2u);
  EXPECT_EQ(codecs[1].name, "flexfec-03");
  EXPECT_EQ(codecs[1].id, 35);

  EXPECT_TRUE(caller->SetLocalDescription(std::move(answer)));

  // This generates a collision for AV1 which needs to be remapped.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());
  auto offer_codecs = offer->description()
                          ->contents()[0]
                          .media_description()
                          ->codecs();
  auto flexfec_it = std::find_if(
      offer_codecs.begin(), offer_codecs.end(),
      [](const cricket::Codec& codec) { return codec.name == "flexfec-03"; });
  ASSERT_EQ(flexfec_it->id, 35);
  auto av1_it = std::find_if(
      offer_codecs.begin(), offer_codecs.end(),
      [](const cricket::Codec& codec) { return codec.name == "AV1"; });
  if (av1_it != offer_codecs.end()) {
    ASSERT_NE(av1_it->id, 35);
  }
}

TEST_P(PeerConnectionSignalingTest, MidAttributeMaxLength) {
  auto caller = CreatePeerConnection();

  std::string sdp =
      "v=0\r\n"
      "o=- 8403615332048243445 2 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "m=video 9 UDP/TLS/RTP/SAVPF 102\r\n"
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp:9 IN IP4 0.0.0.0\r\n"
      "a=ice-ufrag:IZeV\r\n"
      "a=ice-pwd:uaZhQD4rYM/Tta2qWBT1Bbt4\r\n"
      "a=ice-options:trickle\r\n"
      "a=fingerprint:sha-256 "
      "D8:6C:3D:FA:23:E2:2C:63:11:2D:D0:86:BE:C4:D0:65:F9:42:F7:1C:06:04:27:E6:"
      "1C:2C:74:01:8D:50:67:23\r\n"
      "a=setup:actpass\r\n"
      // Too long mid attribute.
      "a=mid:0123456789012345678901234567890123\r\n"
      "a=sendrecv\r\n"
      "a=msid:stream track\r\n"
      "a=rtcp-mux\r\n"
      "a=rtcp-rsize\r\n"
      "a=rtpmap:102 VP8/90000\r\n"
      "a=rtcp-fb:102 goog-remb\r\n"
      "a=rtcp-fb:102 transport-cc\r\n"
      "a=rtcp-fb:102 ccm fir\r\n"
      "a=rtcp-fb:102 nack\r\n"
      "a=rtcp-fb:102 nack pli\r\n"
      "a=ssrc:1224551896 cname:/exJcmhSLpyu9FgV\r\n";
  std::unique_ptr<SessionDescriptionInterface> remote_description =
      CreateSessionDescription(SdpType::kOffer, sdp, nullptr);

  EXPECT_FALSE(caller->SetRemoteDescription(std::move(remote_description)));
}

INSTANTIATE_TEST_SUITE_P(PeerConnectionSignalingTest,
                         PeerConnectionSignalingTest,
                         Values(SdpSemantics::kPlanB_DEPRECATED,
                                SdpSemantics::kUnifiedPlan));

class PeerConnectionSignalingUnifiedPlanTest
    : public PeerConnectionSignalingBaseTest {
 protected:
  PeerConnectionSignalingUnifiedPlanTest()
      : PeerConnectionSignalingBaseTest(SdpSemantics::kUnifiedPlan) {}
};

// We verify that SetLocalDescription() executed immediately by verifying that
// the transceiver mid values got assigned. SLD executing immeditately is not
// unique to Unified Plan, but the transceivers used to verify this are only
// available in Unified Plan.
TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       SetLocalDescriptionExecutesImmediatelyUsingOldObserver) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  // This offer will cause transceiver mids to get assigned.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  // By not waiting for the observer's callback we can verify that the operation
  // executed immediately. The old observer is invoked in a posted message, so
  // waiting for it would not ensure synchronicity.
  RTC_DCHECK(!caller->pc()->GetTransceivers()[0]->mid().has_value());
  caller->pc()->SetLocalDescription(
      rtc::make_ref_counted<MockSetSessionDescriptionObserver>().get(),
      offer.release());
  EXPECT_TRUE(caller->pc()->GetTransceivers()[0]->mid().has_value());
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       SetLocalDescriptionExecutesImmediatelyUsingNewObserver) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  // This offer will cause transceiver mids to get assigned.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  // Verify that mids were assigned without waiting for the observer. (However,
  // the new observer should also be invoked synchronously - as is ensured by
  // other tests.)
  RTC_DCHECK(!caller->pc()->GetTransceivers()[0]->mid().has_value());
  caller->pc()->SetLocalDescription(
      std::move(offer),
      rtc::make_ref_counted<FakeSetLocalDescriptionObserver>());
  EXPECT_TRUE(caller->pc()->GetTransceivers()[0]->mid().has_value());
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       SetLocalDescriptionExecutesImmediatelyInsideCreateOfferCallback) {
  auto caller = CreatePeerConnectionWithAudioVideo();

  // This offer will cause transceiver mids to get assigned.
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  auto offer_observer =
      rtc::make_ref_counted<ExecuteFunctionOnCreateSessionDescriptionObserver>(
          [pc = caller->pc()](SessionDescriptionInterface* desc) {
            // By not waiting for the observer's callback we can verify that the
            // operation executed immediately.
            RTC_DCHECK(!pc->GetTransceivers()[0]->mid().has_value());
            pc->SetLocalDescription(
                rtc::make_ref_counted<MockSetSessionDescriptionObserver>()
                    .get(),
                desc);
            EXPECT_TRUE(pc->GetTransceivers()[0]->mid().has_value());
          });
  caller->pc()->CreateOffer(offer_observer.get(), RTCOfferAnswerOptions());
  EXPECT_TRUE_WAIT(offer_observer->was_called(), kWaitTimeout);
}

// Test that transports are shown in the sender/receiver API after offer/answer.
// This only works in Unified Plan.
TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       DtlsTransportsInstantiateInOfferAnswer) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  EXPECT_FALSE(HasDtlsTransport(caller));
  EXPECT_FALSE(HasDtlsTransport(callee));
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());
  caller->SetLocalDescription(CloneSessionDescription(offer.get()));
  EXPECT_TRUE(HasDtlsTransport(caller));
  callee->SetRemoteDescription(std::move(offer));
  EXPECT_FALSE(HasDtlsTransport(callee));
  auto answer = callee->CreateAnswer(RTCOfferAnswerOptions());
  callee->SetLocalDescription(CloneSessionDescription(answer.get()));
  EXPECT_TRUE(HasDtlsTransport(callee));
  caller->SetRemoteDescription(std::move(answer));
  EXPECT_TRUE(HasDtlsTransport(caller));

  ASSERT_EQ(SignalingState::kStable, caller->signaling_state());
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest, DtlsTransportsMergeWhenBundled) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  EXPECT_FALSE(HasDtlsTransport(caller));
  EXPECT_FALSE(HasDtlsTransport(callee));
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());
  caller->SetLocalDescription(CloneSessionDescription(offer.get()));
  EXPECT_EQ(2, NumberOfDtlsTransports(caller));
  callee->SetRemoteDescription(std::move(offer));
  auto answer = callee->CreateAnswer(RTCOfferAnswerOptions());
  callee->SetLocalDescription(CloneSessionDescription(answer.get()));
  caller->SetRemoteDescription(std::move(answer));
  EXPECT_EQ(1, NumberOfDtlsTransports(caller));

  ASSERT_EQ(SignalingState::kStable, caller->signaling_state());
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       DtlsTransportsAreSeparateeWhenUnbundled) {
  auto caller = CreatePeerConnectionWithAudioVideo();
  auto callee = CreatePeerConnection();

  EXPECT_FALSE(HasDtlsTransport(caller));
  EXPECT_FALSE(HasDtlsTransport(callee));
  RTCOfferAnswerOptions unbundle_options;
  unbundle_options.use_rtp_mux = false;
  auto offer = caller->CreateOffer(unbundle_options);
  caller->SetLocalDescription(CloneSessionDescription(offer.get()));
  EXPECT_EQ(2, NumberOfDtlsTransports(caller));
  callee->SetRemoteDescription(std::move(offer));
  auto answer = callee->CreateAnswer(RTCOfferAnswerOptions());
  callee->SetLocalDescription(CloneSessionDescription(answer.get()));
  EXPECT_EQ(2, NumberOfDtlsTransports(callee));
  caller->SetRemoteDescription(std::move(answer));
  EXPECT_EQ(2, NumberOfDtlsTransports(caller));

  ASSERT_EQ(SignalingState::kStable, caller->signaling_state());
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       ShouldFireNegotiationNeededWhenNoChangesArePending) {
  auto caller = CreatePeerConnection();
  EXPECT_FALSE(caller->observer()->has_negotiation_needed_event());
  auto transceiver =
      caller->AddTransceiver(cricket::MEDIA_TYPE_AUDIO, RtpTransceiverInit());
  EXPECT_TRUE(caller->observer()->has_negotiation_needed_event());
  EXPECT_TRUE(caller->pc()->ShouldFireNegotiationNeededEvent(
      caller->observer()->latest_negotiation_needed_event()));
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       SuppressNegotiationNeededWhenOperationChainIsNotEmpty) {
  auto caller = CreatePeerConnection();
  EXPECT_FALSE(caller->observer()->has_negotiation_needed_event());
  auto transceiver =
      caller->AddTransceiver(cricket::MEDIA_TYPE_AUDIO, RtpTransceiverInit());
  EXPECT_TRUE(caller->observer()->has_negotiation_needed_event());

  auto observer = rtc::make_ref_counted<MockCreateSessionDescriptionObserver>();
  caller->pc()->CreateOffer(observer.get(), RTCOfferAnswerOptions());
  // For this test to work, the operation has to be pending, i.e. the observer
  // has not yet been invoked.
  EXPECT_FALSE(observer->called());
  // Because the Operations Chain is not empty, the event is now suppressed.
  EXPECT_FALSE(caller->pc()->ShouldFireNegotiationNeededEvent(
      caller->observer()->latest_negotiation_needed_event()));
  caller->observer()->clear_latest_negotiation_needed_event();

  // When the Operations Chain becomes empty again, a new negotiation needed
  // event will be generated that is not suppressed.
  EXPECT_TRUE_WAIT(observer->called(), kWaitTimeout);
  EXPECT_TRUE(caller->observer()->has_negotiation_needed_event());
  EXPECT_TRUE(caller->pc()->ShouldFireNegotiationNeededEvent(
      caller->observer()->latest_negotiation_needed_event()));
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest,
       SuppressNegotiationNeededWhenSignalingStateIsNotStable) {
  auto caller = CreatePeerConnection();
  auto callee = CreatePeerConnection();
  auto offer = caller->CreateOffer(RTCOfferAnswerOptions());

  EXPECT_FALSE(caller->observer()->has_negotiation_needed_event());
  auto transceiver =
      callee->AddTransceiver(cricket::MEDIA_TYPE_AUDIO, RtpTransceiverInit());
  EXPECT_TRUE(callee->observer()->has_negotiation_needed_event());

  // Change signaling state (to "have-remote-offer") by setting a remote offer.
  callee->SetRemoteDescription(std::move(offer));
  // Because the signaling state is not "stable", the event is now suppressed.
  EXPECT_FALSE(callee->pc()->ShouldFireNegotiationNeededEvent(
      callee->observer()->latest_negotiation_needed_event()));
  callee->observer()->clear_latest_negotiation_needed_event();

  // Upon rolling back to "stable", a new negotiation needed event will be
  // generated that is not suppressed.
  callee->SetLocalDescription(CreateSessionDescription(SdpType::kRollback, ""));
  EXPECT_TRUE(callee->observer()->has_negotiation_needed_event());
  EXPECT_TRUE(callee->pc()->ShouldFireNegotiationNeededEvent(
      callee->observer()->latest_negotiation_needed_event()));
}

TEST_F(PeerConnectionSignalingUnifiedPlanTest, RtxReofferApt) {
  auto callee = CreatePeerConnection();

  std::string sdp =
      "v=0\r\n"
      "o=- 8403615332048243445 2 IN IP4 127.0.0.1\r\n"
      "s=-\r\n"
      "t=0 0\r\n"
      "m=video 9 UDP/TLS/RTP/SAVPF 102\r\n"
      "c=IN IP4 0.0.0.0\r\n"
      "a=rtcp:9 IN IP4 0.0.0.0\r\n"
      "a=ice-ufrag:IZeV\r\n"
      "a=ice-pwd:uaZhQD4rYM/Tta2qWBT1Bbt4\r\n"
      "a=ice-options:trickle\r\n"
      "a=fingerprint:sha-256 "
      "D8:6C:3D:FA:23:E2:2C:63:11:2D:D0:86:BE:C4:D0:65:F9:42:F7:1C:06:04:27:E6:"
      "1C:2C:74:01:8D:50:67:23\r\n"
      "a=setup:actpass\r\n"
      "a=mid:0\r\n"
      "a=sendrecv\r\n"
      "a=msid:stream track\r\n"
      "a=rtcp-mux\r\n"
      "a=rtcp-rsize\r\n"
      "a=rtpmap:102 VP8/90000\r\n"
      "a=rtcp-fb:102 goog-remb\r\n"
      "a=rtcp-fb:102 transport-cc\r\n"
      "a=rtcp-fb:102 ccm fir\r\n"
      "a=rtcp-fb:102 nack\r\n"
      "a=rtcp-fb:102 nack pli\r\n"
      "a=ssrc:1224551896 cname:/exJcmhSLpyu9FgV\r\n";
  std::unique_ptr<SessionDescriptionInterface> remote_description =
      CreateSessionDescription(SdpType::kOffer, sdp, nullptr);

  EXPECT_TRUE(callee->SetRemoteDescription(std::move(remote_description)));

  auto answer = callee->CreateAnswer(RTCOfferAnswerOptions());
  EXPECT_TRUE(
      callee->SetLocalDescription(CloneSessionDescription(answer.get())));

  callee->pc()->GetTransceivers()[0]->StopStandard();
  auto reoffer = callee->CreateOffer(RTCOfferAnswerOptions());
  auto codecs = reoffer->description()
                    ->contents()[0]
                    .media_description()
                    ->codecs();
  ASSERT_GT(codecs.size(), 2u);
  EXPECT_EQ(codecs[0].name, "VP8");
  EXPECT_EQ(codecs[1].name, "rtx");
  auto apt_it = codecs[1].params.find("apt");
  ASSERT_NE(apt_it, codecs[1].params.end());
  // The apt should match the id from the remote offer.
  EXPECT_EQ(apt_it->second, rtc::ToString(codecs[0].id));
  EXPECT_EQ(apt_it->second, "102");
}

}  // namespace webrtc
