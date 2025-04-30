/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TEST_PCLF_MEDIA_QUALITY_TEST_PARAMS_H_
#define API_TEST_PCLF_MEDIA_QUALITY_TEST_PARAMS_H_

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

#include "api/async_dns_resolver.h"
#include "api/audio/audio_mixer.h"
#include "api/fec_controller.h"
#include "api/field_trials_view.h"
#include "api/rtc_event_log/rtc_event_log_factory_interface.h"
#include "api/test/pclf/media_configuration.h"
#include "api/transport/network_control.h"
#include "api/video_codecs/video_decoder_factory.h"
#include "api/video_codecs/video_encoder_factory.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "p2p/base/port_allocator.h"
#include "rtc_base/network.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/ssl_certificate.h"
#include "rtc_base/thread.h"

namespace webrtc {
namespace webrtc_pc_e2e {

// Contains most part from PeerConnectionFactoryDependencies. Also all fields
// are optional and defaults will be provided by fixture implementation if
// any will be omitted.
//
// Separate class was introduced to clarify which components can be
// overridden. For example worker and signaling threads will be provided by
// fixture implementation. The same is applicable to the media engine. So user
// can override only some parts of media engine like video encoder/decoder
// factories.
struct PeerConnectionFactoryComponents {
  std::unique_ptr<RtcEventLogFactoryInterface> event_log_factory;
  std::unique_ptr<FecControllerFactoryInterface> fec_controller_factory;
  std::unique_ptr<NetworkControllerFactoryInterface> network_controller_factory;
  std::unique_ptr<NetEqFactory> neteq_factory;

  std::unique_ptr<VideoEncoderFactory> video_encoder_factory;
  std::unique_ptr<VideoDecoderFactory> video_decoder_factory;
  rtc::scoped_refptr<webrtc::AudioEncoderFactory> audio_encoder_factory;
  rtc::scoped_refptr<webrtc::AudioDecoderFactory> audio_decoder_factory;

  std::unique_ptr<FieldTrialsView> trials;

  rtc::scoped_refptr<webrtc::AudioProcessing> audio_processing;
  rtc::scoped_refptr<webrtc::AudioMixer> audio_mixer;
};

// Contains most parts from PeerConnectionDependencies. Also all fields are
// optional and defaults will be provided by fixture implementation if any
// will be omitted.
//
// Separate class was introduced to clarify which components can be
// overridden. For example observer, which is required to
// PeerConnectionDependencies, will be provided by fixture implementation,
// so client can't inject its own. Also only network manager can be overridden
// inside port allocator.
struct PeerConnectionComponents {
  PeerConnectionComponents(rtc::NetworkManager* network_manager,
                           rtc::PacketSocketFactory* packet_socket_factory)
      : network_manager(network_manager),
        packet_socket_factory(packet_socket_factory) {
    RTC_CHECK(network_manager);
  }

  rtc::NetworkManager* const network_manager;
  rtc::PacketSocketFactory* const packet_socket_factory;
  std::unique_ptr<webrtc::AsyncDnsResolverFactoryInterface>
      async_dns_resolver_factory;
  std::unique_ptr<rtc::RTCCertificateGeneratorInterface> cert_generator;
  std::unique_ptr<rtc::SSLCertificateVerifier> tls_cert_verifier;
  std::unique_ptr<IceTransportFactory> ice_transport_factory;
};

// Contains all components, that can be overridden in peer connection. Also
// has a network thread, that will be used to communicate with another peers.
struct InjectableComponents {
  InjectableComponents(rtc::Thread* network_thread,
                       rtc::NetworkManager* network_manager,
                       rtc::PacketSocketFactory* packet_socket_factory)
      : network_thread(network_thread),
        worker_thread(nullptr),
        pcf_dependencies(std::make_unique<PeerConnectionFactoryComponents>()),
        pc_dependencies(
            std::make_unique<PeerConnectionComponents>(network_manager,
                                                       packet_socket_factory)) {
    RTC_CHECK(network_thread);
  }

  rtc::Thread* const network_thread;
  rtc::Thread* worker_thread;

  std::unique_ptr<PeerConnectionFactoryComponents> pcf_dependencies;
  std::unique_ptr<PeerConnectionComponents> pc_dependencies;
};

// Contains information about call media streams (up to 1 audio stream and
// unlimited amount of video streams) and rtc configuration, that will be used
// to set up peer connection.
struct Params {
  // Peer name. If empty - default one will be set by the fixture.
  absl::optional<std::string> name;
  // If `audio_config` is set audio stream will be configured
  absl::optional<AudioConfig> audio_config;
  // Flags to set on `cricket::PortAllocator`. These flags will be added
  // to the default ones that are presented on the port allocator.
  uint32_t port_allocator_extra_flags = cricket::kDefaultPortAllocatorFlags;
  // If `rtc_event_log_path` is set, an RTCEventLog will be saved in that
  // location and it will be available for further analysis.
  absl::optional<std::string> rtc_event_log_path;
  // If `aec_dump_path` is set, an AEC dump will be saved in that location and
  // it will be available for further analysis.
  absl::optional<std::string> aec_dump_path;

  bool use_ulp_fec = false;
  bool use_flex_fec = false;
  // Specifies how much video encoder target bitrate should be different than
  // target bitrate, provided by WebRTC stack. Must be greater then 0. Can be
  // used to emulate overshooting of video encoders. This multiplier will
  // be applied for all video encoder on both sides for all layers. Bitrate
  // estimated by WebRTC stack will be multiplied by this multiplier and then
  // provided into VideoEncoder::SetRates(...).
  double video_encoder_bitrate_multiplier = 1.0;

  PeerConnectionFactoryInterface::Options peer_connection_factory_options;
  PeerConnectionInterface::RTCConfiguration rtc_configuration;
  PeerConnectionInterface::RTCOfferAnswerOptions rtc_offer_answer_options;
  BitrateSettings bitrate_settings;
  std::vector<VideoCodecConfig> video_codecs;

  // A list of RTP header extensions which will be enforced on all video streams
  // added to this peer.
  std::vector<std::string> extra_video_rtp_header_extensions;
  // A list of RTP header extensions which will be enforced on all audio streams
  // added to this peer.
  std::vector<std::string> extra_audio_rtp_header_extensions;
};

// Contains parameters that maybe changed by test writer during the test call.
struct ConfigurableParams {
  // If `video_configs` is empty - no video should be added to the test call.
  std::vector<VideoConfig> video_configs;

  VideoSubscription video_subscription =
      VideoSubscription().SubscribeToAllPeers();
};

// Contains parameters, that describe how long framework should run quality
// test.
struct RunParams {
  explicit RunParams(TimeDelta run_duration) : run_duration(run_duration) {}

  // Specifies how long the test should be run. This time shows how long
  // the media should flow after connection was established and before
  // it will be shut downed.
  TimeDelta run_duration;

  // If set to true peers will be able to use Flex FEC, otherwise they won't
  // be able to negotiate it even if it's enabled on per peer level.
  bool enable_flex_fec_support = false;
  // If true will set conference mode in SDP media section for all video
  // tracks for all peers.
  bool use_conference_mode = false;
  // If specified echo emulation will be done, by mixing the render audio into
  // the capture signal. In such case input signal will be reduced by half to
  // avoid saturation or compression in the echo path simulation.
  absl::optional<EchoEmulationConfig> echo_emulation_config;
};

}  // namespace webrtc_pc_e2e
}  // namespace webrtc

#endif  // API_TEST_PCLF_MEDIA_QUALITY_TEST_PARAMS_H_
