/*
 *  Copyright 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/peer_connection_factory.h"

#include <type_traits>
#include <utility>

#include "absl/strings/match.h"
#include "api/async_resolver_factory.h"
#include "api/call/call_factory_interface.h"
#include "api/fec_controller.h"
#include "api/ice_transport_interface.h"
#include "api/network_state_predictor.h"
#include "api/packet_socket_factory.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/sequence_checker.h"
#include "api/transport/bitrate_settings.h"
#include "api/units/data_rate.h"
#include "call/audio_state.h"
#include "call/rtp_transport_controller_send_factory.h"
#include "media/base/media_engine.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/base/default_ice_transport_factory.h"
#include "p2p/base/port_allocator.h"
#include "p2p/client/basic_port_allocator.h"
#include "pc/audio_track.h"
#include "pc/local_audio_source.h"
#include "pc/media_stream.h"
#include "pc/media_stream_proxy.h"
#include "pc/media_stream_track_proxy.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_factory_proxy.h"
#include "pc/peer_connection_proxy.h"
#include "pc/rtp_parameters_conversion.h"
#include "pc/session_description.h"
#include "pc/video_track.h"
#include "rtc_base/checks.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/experiments/field_trial_units.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/system/file_wrapper.h"

namespace webrtc {

rtc::scoped_refptr<PeerConnectionFactoryInterface>
CreateModularPeerConnectionFactory(
    PeerConnectionFactoryDependencies dependencies) {
  // The PeerConnectionFactory must be created on the signaling thread.
  if (dependencies.signaling_thread &&
      !dependencies.signaling_thread->IsCurrent()) {
    return dependencies.signaling_thread->BlockingCall([&dependencies] {
      return CreateModularPeerConnectionFactory(std::move(dependencies));
    });
  }

  auto pc_factory = PeerConnectionFactory::Create(std::move(dependencies));
  if (!pc_factory) {
    return nullptr;
  }
  // Verify that the invocation and the initialization ended up agreeing on the
  // thread.
  RTC_DCHECK_RUN_ON(pc_factory->signaling_thread());
  return PeerConnectionFactoryProxy::Create(
      pc_factory->signaling_thread(), pc_factory->worker_thread(), pc_factory);
}

// Static
rtc::scoped_refptr<PeerConnectionFactory> PeerConnectionFactory::Create(
    PeerConnectionFactoryDependencies dependencies) {
  auto context = ConnectionContext::Create(&dependencies);
  if (!context) {
    return nullptr;
  }
  return rtc::make_ref_counted<PeerConnectionFactory>(context, &dependencies);
}

PeerConnectionFactory::PeerConnectionFactory(
    rtc::scoped_refptr<ConnectionContext> context,
    PeerConnectionFactoryDependencies* dependencies)
    : context_(context),
      task_queue_factory_(std::move(dependencies->task_queue_factory)),
      event_log_factory_(std::move(dependencies->event_log_factory)),
      fec_controller_factory_(std::move(dependencies->fec_controller_factory)),
      network_state_predictor_factory_(
          std::move(dependencies->network_state_predictor_factory)),
      injected_network_controller_factory_(
          std::move(dependencies->network_controller_factory)),
      neteq_factory_(std::move(dependencies->neteq_factory)),
      transport_controller_send_factory_(
          (dependencies->transport_controller_send_factory)
              ? std::move(dependencies->transport_controller_send_factory)
              : std::make_unique<RtpTransportControllerSendFactory>()),
      metronome_(std::move(dependencies->metronome)) {}

PeerConnectionFactory::PeerConnectionFactory(
    PeerConnectionFactoryDependencies dependencies)
    : PeerConnectionFactory(ConnectionContext::Create(&dependencies),
                            &dependencies) {}

PeerConnectionFactory::~PeerConnectionFactory() {
  RTC_DCHECK_RUN_ON(signaling_thread());
}

void PeerConnectionFactory::SetOptions(const Options& options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  options_ = options;
}

RtpCapabilities PeerConnectionFactory::GetRtpSenderCapabilities(
    cricket::MediaType kind) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  switch (kind) {
    case cricket::MEDIA_TYPE_AUDIO: {
      cricket::AudioCodecs cricket_codecs;
      cricket_codecs = media_engine()->voice().send_codecs();
      auto extensions =
          GetDefaultEnabledRtpHeaderExtensions(media_engine()->voice());
      return ToRtpCapabilities(cricket_codecs, extensions);
    }
    case cricket::MEDIA_TYPE_VIDEO: {
      cricket::VideoCodecs cricket_codecs;
      cricket_codecs = media_engine()->video().send_codecs();
      auto extensions =
          GetDefaultEnabledRtpHeaderExtensions(media_engine()->video());
      return ToRtpCapabilities(cricket_codecs, extensions);
    }
    case cricket::MEDIA_TYPE_DATA:
      return RtpCapabilities();
    case cricket::MEDIA_TYPE_UNSUPPORTED:
      return RtpCapabilities();
  }
  RTC_DLOG(LS_ERROR) << "Got unexpected MediaType " << kind;
  RTC_CHECK_NOTREACHED();
}

RtpCapabilities PeerConnectionFactory::GetRtpReceiverCapabilities(
    cricket::MediaType kind) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  switch (kind) {
    case cricket::MEDIA_TYPE_AUDIO: {
      cricket::AudioCodecs cricket_codecs;
      cricket_codecs = media_engine()->voice().recv_codecs();
      auto extensions =
          GetDefaultEnabledRtpHeaderExtensions(media_engine()->voice());
      return ToRtpCapabilities(cricket_codecs, extensions);
    }
    case cricket::MEDIA_TYPE_VIDEO: {
      cricket::VideoCodecs cricket_codecs =
          media_engine()->video().recv_codecs(context_->use_rtx());
      auto extensions =
          GetDefaultEnabledRtpHeaderExtensions(media_engine()->video());
      return ToRtpCapabilities(cricket_codecs, extensions);
    }
    case cricket::MEDIA_TYPE_DATA:
      return RtpCapabilities();
    case cricket::MEDIA_TYPE_UNSUPPORTED:
      return RtpCapabilities();
  }
  RTC_DLOG(LS_ERROR) << "Got unexpected MediaType " << kind;
  RTC_CHECK_NOTREACHED();
}

rtc::scoped_refptr<AudioSourceInterface>
PeerConnectionFactory::CreateAudioSource(const cricket::AudioOptions& options) {
  RTC_DCHECK(signaling_thread()->IsCurrent());
  rtc::scoped_refptr<LocalAudioSource> source(
      LocalAudioSource::Create(&options));
  return source;
}

bool PeerConnectionFactory::StartAecDump(FILE* file, int64_t max_size_bytes) {
  RTC_DCHECK_RUN_ON(worker_thread());
  return media_engine()->voice().StartAecDump(FileWrapper(file),
                                              max_size_bytes);
}

void PeerConnectionFactory::StopAecDump() {
  RTC_DCHECK_RUN_ON(worker_thread());
  media_engine()->voice().StopAecDump();
}

cricket::MediaEngineInterface* PeerConnectionFactory::media_engine() const {
  RTC_DCHECK(context_);
  return context_->media_engine();
}

RTCErrorOr<rtc::scoped_refptr<PeerConnectionInterface>>
PeerConnectionFactory::CreatePeerConnectionOrError(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies dependencies) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  // Set internal defaults if optional dependencies are not set.
  if (!dependencies.cert_generator) {
    dependencies.cert_generator =
        std::make_unique<rtc::RTCCertificateGenerator>(signaling_thread(),
                                                       network_thread());
  }
  if (!dependencies.allocator) {
    const FieldTrialsView* trials =
        dependencies.trials ? dependencies.trials.get() : &field_trials();
    dependencies.allocator = std::make_unique<cricket::BasicPortAllocator>(
        context_->default_network_manager(), context_->default_socket_factory(),
        configuration.turn_customizer, /*relay_port_factory=*/nullptr, trials);
    dependencies.allocator->SetPortRange(
        configuration.port_allocator_config.min_port,
        configuration.port_allocator_config.max_port);
    dependencies.allocator->set_flags(
        configuration.port_allocator_config.flags);
  }

  if (!dependencies.async_resolver_factory) {
    dependencies.async_resolver_factory =
        std::make_unique<webrtc::BasicAsyncResolverFactory>();
  }

  if (!dependencies.ice_transport_factory) {
    dependencies.ice_transport_factory =
        std::make_unique<DefaultIceTransportFactory>();
  }

  dependencies.allocator->SetNetworkIgnoreMask(options().network_ignore_mask);
  dependencies.allocator->SetVpnList(configuration.vpn_list);

  std::unique_ptr<RtcEventLog> event_log =
      worker_thread()->BlockingCall([this] { return CreateRtcEventLog_w(); });

  const FieldTrialsView* trials =
      dependencies.trials ? dependencies.trials.get() : &field_trials();
  std::unique_ptr<Call> call =
      worker_thread()->BlockingCall([this, &event_log, trials] {
        return CreateCall_w(event_log.get(), *trials);
      });

  auto result = PeerConnection::Create(context_, options_, std::move(event_log),
                                       std::move(call), configuration,
                                       std::move(dependencies));
  if (!result.ok()) {
    return result.MoveError();
  }
  // We configure the proxy with a pointer to the network thread for methods
  // that need to be invoked there rather than on the signaling thread.
  // Internally, the proxy object has a member variable named `worker_thread_`
  // which will point to the network thread (and not the factory's
  // worker_thread()).  All such methods have thread checks though, so the code
  // should still be clear (outside of macro expansion).
  rtc::scoped_refptr<PeerConnectionInterface> result_proxy =
      PeerConnectionProxy::Create(signaling_thread(), network_thread(),
                                  result.MoveValue());
  return result_proxy;
}

rtc::scoped_refptr<MediaStreamInterface>
PeerConnectionFactory::CreateLocalMediaStream(const std::string& stream_id) {
  RTC_DCHECK(signaling_thread()->IsCurrent());
  return MediaStreamProxy::Create(signaling_thread(),
                                  MediaStream::Create(stream_id));
}

rtc::scoped_refptr<VideoTrackInterface> PeerConnectionFactory::CreateVideoTrack(
    const std::string& id,
    VideoTrackSourceInterface* source) {
  RTC_DCHECK(signaling_thread()->IsCurrent());
  rtc::scoped_refptr<VideoTrackInterface> track = VideoTrack::Create(
      id, rtc::scoped_refptr<VideoTrackSourceInterface>(source),
      worker_thread());
  return VideoTrackProxy::Create(signaling_thread(), worker_thread(), track);
}

rtc::scoped_refptr<AudioTrackInterface> PeerConnectionFactory::CreateAudioTrack(
    const std::string& id,
    AudioSourceInterface* source) {
  RTC_DCHECK(signaling_thread()->IsCurrent());
  rtc::scoped_refptr<AudioTrackInterface> track =
      AudioTrack::Create(id, rtc::scoped_refptr<AudioSourceInterface>(source));
  return AudioTrackProxy::Create(signaling_thread(), track);
}

std::unique_ptr<RtcEventLog> PeerConnectionFactory::CreateRtcEventLog_w() {
  RTC_DCHECK_RUN_ON(worker_thread());

  auto encoding_type = RtcEventLog::EncodingType::Legacy;
  if (IsTrialEnabled("WebRTC-RtcEventLogNewFormat"))
    encoding_type = RtcEventLog::EncodingType::NewFormat;
  return event_log_factory_ ? event_log_factory_->Create(encoding_type)
                            : std::make_unique<RtcEventLogNull>();
}

std::unique_ptr<Call> PeerConnectionFactory::CreateCall_w(
    RtcEventLog* event_log,
    const FieldTrialsView& field_trials) {
  RTC_DCHECK_RUN_ON(worker_thread());

  webrtc::Call::Config call_config(event_log, network_thread());
  if (!media_engine() || !context_->call_factory()) {
    return nullptr;
  }
  call_config.audio_state = media_engine()->voice().GetAudioState();

  FieldTrialParameter<DataRate> min_bandwidth("min",
                                              DataRate::KilobitsPerSec(30));
  FieldTrialParameter<DataRate> start_bandwidth("start",
                                                DataRate::KilobitsPerSec(300));
  FieldTrialParameter<DataRate> max_bandwidth("max",
                                              DataRate::KilobitsPerSec(2000));
  ParseFieldTrial({&min_bandwidth, &start_bandwidth, &max_bandwidth},
                  field_trials.Lookup("WebRTC-PcFactoryDefaultBitrates"));

  call_config.bitrate_config.min_bitrate_bps =
      rtc::saturated_cast<int>(min_bandwidth->bps());
  call_config.bitrate_config.start_bitrate_bps =
      rtc::saturated_cast<int>(start_bandwidth->bps());
  call_config.bitrate_config.max_bitrate_bps =
      rtc::saturated_cast<int>(max_bandwidth->bps());

  call_config.fec_controller_factory = fec_controller_factory_.get();
  call_config.task_queue_factory = task_queue_factory_.get();
  call_config.network_state_predictor_factory =
      network_state_predictor_factory_.get();
  call_config.neteq_factory = neteq_factory_.get();

  if (IsTrialEnabled("WebRTC-Bwe-InjectedCongestionController")) {
    RTC_LOG(LS_INFO) << "Using injected network controller factory";
    call_config.network_controller_factory =
        injected_network_controller_factory_.get();
  } else {
    RTC_LOG(LS_INFO) << "Using default network controller factory";
  }

  call_config.trials = &field_trials;
  call_config.rtp_transport_controller_send_factory =
      transport_controller_send_factory_.get();
  call_config.metronome = metronome_.get();
  return std::unique_ptr<Call>(
      context_->call_factory()->CreateCall(call_config));
}

bool PeerConnectionFactory::IsTrialEnabled(absl::string_view key) const {
  return absl::StartsWith(field_trials().Lookup(key), "Enabled");
}

}  // namespace webrtc
