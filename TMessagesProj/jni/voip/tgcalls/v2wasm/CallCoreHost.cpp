#include "v2wasm/CallCoreHost.h"

#include <fstream>
#include <sstream>

#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "api/enable_media.h"
#include "api/jsep_ice_candidate.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "p2p/client/basic_port_allocator.h"
#include "rtc_base/network.h"
#include "rtc_base/time_utils.h"
#include "system_wrappers/include/field_trial.h"

#include "AudioDeviceHelper.h"
#include "EncryptedConnection.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "v2/InstanceNetworking.h"
#include "v2/MtProtoIceTransport.h"
#include "v2/ReflectorRelayPortFactory.h"
#include "v2/SignalingConnection.h"
#include "v2/ExternalSignalingConnection.h"
#include "v2/SignalingSctpConnection.h"
#include "v2/CustomParameters.h"
#include "v2wasm/CoreBase64.h"
#include "v2wasm/EmbeddedCoreModule.h"
#include "v2wasm/NativeCoreBackend.h"
#include "v2wasm/WamrCoreBackend.h"

#ifdef WEBRTC_IOS
#include "platform/darwin/iOS/tgcalls_audio_device_module_ios.h"
#endif

namespace tgcalls {

namespace v2wasm_detail {

class SetSessionDescriptionObserver : public webrtc::SetLocalDescriptionObserverInterface, public webrtc::SetRemoteDescriptionObserverInterface {
public:
    SetSessionDescriptionObserver(std::function<void(webrtc::RTCError)> &&completion) :
    _completion(std::move(completion)) {
    }

    void OnSetLocalDescriptionComplete(webrtc::RTCError error) override {
        _completion(error);
    }

    void OnSetRemoteDescriptionComplete(webrtc::RTCError error) override {
        _completion(error);
    }

private:
    std::function<void(webrtc::RTCError)> _completion;
};

class CreateSessionDescriptionObserverAdapter : public webrtc::CreateSessionDescriptionObserver {
public:
    // completion(type, sdp, error): success -> error empty; failure -> type/sdp empty.
    CreateSessionDescriptionObserverAdapter(std::function<void(std::string, std::string, std::string)> &&completion) :
    _completion(std::move(completion)) {
    }

    void OnSuccess(webrtc::SessionDescriptionInterface *desc) override {
        std::unique_ptr<webrtc::SessionDescriptionInterface> description(desc); // ownership transferred
        std::string sdp;
        description->ToString(&sdp);
        _completion(description->type(), sdp, std::string());
    }

    void OnFailure(webrtc::RTCError error) override {
        _completion(std::string(), std::string(), error.message());
    }

private:
    std::function<void(std::string, std::string, std::string)> _completion;
};

class StatsCollectorCallbackAdapter : public webrtc::RTCStatsCollectorCallback {
public:
    StatsCollectorCallbackAdapter(std::function<void(const webrtc::scoped_refptr<const webrtc::RTCStatsReport> &)> &&completion_) :
    completion(std::move(completion_)) {
    }

    void OnStatsDelivered(const webrtc::scoped_refptr<const webrtc::RTCStatsReport> &report) override {
        completion(report);
    }

private:
    std::function<void(const webrtc::scoped_refptr<const webrtc::RTCStatsReport> &)> completion;
};

class DataChannelObserverImpl : public webrtc::DataChannelObserver {
public:
    struct Parameters {
        std::function<void()> onStateChange;
        std::function<void(webrtc::DataBuffer const &)> onMessage;
        std::function<void(uint64_t)> onBufferedAmountChange;
    };

    DataChannelObserverImpl(Parameters &&parameters) :
    _parameters(std::move(parameters)) {
    }

    void OnStateChange() override {
        if (_parameters.onStateChange) {
            _parameters.onStateChange();
        }
    }

    void OnMessage(webrtc::DataBuffer const &buffer) override {
        if (_parameters.onMessage) {
            _parameters.onMessage(buffer);
        }
    }

    void OnBufferedAmountChange(uint64_t sentDataSize) override {
        if (_parameters.onBufferedAmountChange) {
            _parameters.onBufferedAmountChange(sentDataSize);
        }
    }

private:
    Parameters _parameters;
};

class PeerConnectionDelegateAdapter : public webrtc::PeerConnectionObserver {
public:
    PeerConnectionDelegateAdapter(std::weak_ptr<CallCoreHost> host, std::shared_ptr<Threads> threads) :
    _host(host), _threads(threads) {
    }

    void OnSignalingChange(webrtc::PeerConnectionInterface::SignalingState newState) override {
        std::string state;
        switch (newState) {
            case webrtc::PeerConnectionInterface::SignalingState::kStable: state = "stable"; break;
            case webrtc::PeerConnectionInterface::SignalingState::kHaveLocalOffer: state = "have-local-offer"; break;
            case webrtc::PeerConnectionInterface::SignalingState::kHaveLocalPrAnswer: state = "have-local-pranswer"; break;
            case webrtc::PeerConnectionInterface::SignalingState::kHaveRemoteOffer: state = "have-remote-offer"; break;
            case webrtc::PeerConnectionInterface::SignalingState::kHaveRemotePrAnswer: state = "have-remote-pranswer"; break;
            case webrtc::PeerConnectionInterface::SignalingState::kClosed: state = "closed"; break;
            default: state = "stable"; break;
        }
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({ {"@type", "pc_signaling_state"}, {"state", state} });
        }
    }

    // Stock wraps this body in PostTask (InstanceV2ReferenceImpl.cpp:483).
    // Here deliverEvent's deferral guard provides the equivalent protection:
    // any synchronous fire from inside command execution is deferred.
    void OnRenegotiationNeeded() override {
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({ {"@type", "pc_renegotiation_needed"} });
        }
    }

    void OnIceCandidate(const webrtc::IceCandidateInterface *candidate) override {
        std::string sdp;
        candidate->ToString(&sdp);
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({
                {"@type", "pc_ice_candidate"},
                {"mid", candidate->sdp_mid()},
                {"mline", candidate->sdp_mline_index()},
                {"sdp", sdp},
            });
        }
    }

    void OnIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState newState) override {
        std::string state;
        switch (newState) {
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionNew: state = "new"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionChecking: state = "checking"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionConnected: state = "connected"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionCompleted: state = "completed"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionFailed: state = "failed"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionDisconnected: state = "disconnected"; break;
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionClosed: state = "closed"; break;
            default: state = "new"; break;
        }
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({ {"@type", "pc_ice_state"}, {"state", state} });
        }
    }

    void OnIceSelectedCandidatePairChanged(const cricket::CandidatePairChangeEvent &event) override {
        const auto local = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.local);
        const auto remote = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.remote);
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({
                {"@type", "pc_candidate_pair_changed"},
                {"local", json11::Json::object{ {"type", local.type}, {"protocol", local.protocol}, {"address", local.address} }},
                {"remote", json11::Json::object{ {"type", remote.type}, {"protocol", remote.protocol}, {"address", remote.address} }},
            });
        }
    }

    void OnDataChannel(webrtc::scoped_refptr<webrtc::DataChannelInterface> dataChannel) override {
        if (const auto strong = _host.lock()) {
            const std::string label = dataChannel->label();
            if (strong->_dataChannels.count(label)) {
                strong->emitErrorEvent("duplicate remote data channel label", "dc_channel");
                return;
            }
            RTC_LOG(LS_INFO) << "CallCoreHost: dc_channel [" << label << "] id=" << dataChannel->id();
            strong->deliverEvent({ {"@type", "dc_channel"}, {"label", label}, {"id", dataChannel->id()} });
            strong->attachDataChannel(label, dataChannel);
        }
    }

    void OnTrack(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) override {
        const auto strong = _host.lock();
        if (!strong) {
            return;
        }
        if (!transceiver->mid()) {
            return;
        }
        std::string mid = transceiver->mid().value();
        std::string kind = "audio";
        if (transceiver->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
            kind = "video";
            if (strong->_incomingVideoTransceivers.find(mid) == strong->_incomingVideoTransceivers.end()) {
                strong->_incomingVideoTransceivers.insert(std::make_pair(mid, transceiver));
                if (strong->_requestedSinkMids.find(mid) != strong->_requestedSinkMids.end()) {
                    strong->connectIncomingVideoSink(transceiver);
                }
            }
        }
        strong->deliverEvent({ {"@type", "pc_track"}, {"mid", mid}, {"kind", kind} });
    }

    void OnRemoveTrack(webrtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) override {
        const auto strong = _host.lock();
        if (!strong) {
            return;
        }
        // _incomingVideoTransceivers is keyed by mid, but RtpReceiverInterface
        // exposes no mid() - receiver->track()->id() is the TRACK id, so looking
        // it up by that never matched and entries were never erased. Find the
        // entry by its receiver instead (mirrors InstanceV2ReferenceImpl).
        for (auto it = strong->_incomingVideoTransceivers.begin(); it != strong->_incomingVideoTransceivers.end(); it++) {
            if (it->second->receiver() != receiver) {
                continue;
            }
            strong->disconnectIncomingVideoSink(it->second);
            strong->_incomingVideoTransceivers.erase(it);
            break;
        }
    }

    void OnAddStream(webrtc::scoped_refptr<webrtc::MediaStreamInterface>) override {}
    void OnRemoveStream(webrtc::scoped_refptr<webrtc::MediaStreamInterface>) override {}

    void OnIceGatheringChange(webrtc::PeerConnectionInterface::IceGatheringState newState) override {
        std::string state;
        switch (newState) {
            case webrtc::PeerConnectionInterface::kIceGatheringNew: state = "new"; break;
            case webrtc::PeerConnectionInterface::kIceGatheringGathering: state = "gathering"; break;
            case webrtc::PeerConnectionInterface::kIceGatheringComplete: state = "complete"; break;
            default: state = "new"; break;
        }
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({ {"@type", "pc_gathering_state"}, {"state", state} });
        }
    }

    void OnIceCandidatesRemoved(const std::vector<cricket::Candidate> &) override {}
    void OnStandardizedIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState) override {}

    void OnConnectionChange(webrtc::PeerConnectionInterface::PeerConnectionState newState) override {
        std::string state;
        switch (newState) {
            case webrtc::PeerConnectionInterface::PeerConnectionState::kNew: state = "new"; break;
            case webrtc::PeerConnectionInterface::PeerConnectionState::kConnecting: state = "connecting"; break;
            case webrtc::PeerConnectionInterface::PeerConnectionState::kConnected: state = "connected"; break;
            case webrtc::PeerConnectionInterface::PeerConnectionState::kDisconnected: state = "disconnected"; break;
            case webrtc::PeerConnectionInterface::PeerConnectionState::kFailed: state = "failed"; break;
            case webrtc::PeerConnectionInterface::PeerConnectionState::kClosed: state = "closed"; break;
            default: state = "new"; break;
        }
        if (const auto strong = _host.lock()) {
            strong->deliverEvent({ {"@type", "pc_connection_state"}, {"state", state} });
        }
    }

    void OnAddTrack(webrtc::scoped_refptr<webrtc::RtpReceiverInterface>, const std::vector<webrtc::scoped_refptr<webrtc::MediaStreamInterface>> &) override {}

private:
    std::weak_ptr<CallCoreHost> _host;
    std::shared_ptr<Threads> _threads;
};

} // namespace v2wasm_detail

namespace {

constexpr size_t kMaxDcMessageBytes = 256 * 1024;

VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl *>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

// Substrate is intrinsic to the negotiated version string, never taken from
// configuration: 18.0.0 runs the core natively, 19.0.0 runs the same source
// as the embedded wasm module. An unrecognized version falls back to native,
// mirroring stock's unknown-version -> V2 defaulting
// (v2/InstanceV2ReferenceImpl.cpp:79).
bool versionUsesWasmCore(std::string const &version) {
    return version == "19.0.0";
}

std::string coreStringField(json11::Json const &object, std::string const &key) {
    const auto &value = object[key];
    return value.is_string() ? value.string_value() : std::string();
}

// Plain-value snapshot of the RTCStatsReport, filled on the stats callback
// thread and shipped to the media thread where JSON building + delta
// computation happen. Sentinels: -1.0 = absent for doubles, empty = absent
// for strings, present=false = no stream of that kind.
struct ReducedStats {
    double availableOutgoingBitrateBps = 0.0; // max over pairs (stock parity)
    double availableIncomingBitrateBps = 0.0;
    bool hasAvailableOutgoingBitrate = false;
    bool hasAvailableIncomingBitrate = false;
    double rttMs = -1.0;
    std::string localCandidateType;
    std::string remoteCandidateType;
    uint64_t transportBytesSent = 0;
    uint64_t transportBytesReceived = 0;
    struct Send {
        bool present = false;
        uint64_t bytesSent = 0;
        uint64_t packetsSent = 0;
        double remoteLossFraction = -1.0;
        double remoteRttMs = -1.0;
        double remoteJitterMs = -1.0;
        double frameRate = -1.0;
        int frameWidth = 0;
        int frameHeight = 0;
        std::string qualityLimitationReason;
    };
    struct Recv {
        bool present = false;
        uint64_t bytesReceived = 0;
        uint64_t packetsReceived = 0;
        int packetsLost = 0;
        double jitterMs = -1.0;
        double audioLevel = -1.0;
        uint64_t framesDecoded = 0;
        double frameRate = -1.0;
        int frameWidth = 0;
        int frameHeight = 0;
    };
    Send audioSend;
    Send videoSend;
    Recv audioRecv;
    Recv videoRecv;
};

ReducedStats reduceStatsReport(const webrtc::scoped_refptr<const webrtc::RTCStatsReport> &report) {
    ReducedStats reduced;
    if (!report) {
        return reduced;
    }

    std::string selectedPairId;
    for (const auto *transportStats : report->GetStatsOfType<webrtc::RTCTransportStats>()) {
        if (transportStats->selected_candidate_pair_id.has_value()) {
            selectedPairId = *transportStats->selected_candidate_pair_id;
        }
        reduced.transportBytesSent += transportStats->bytes_sent.value_or(0);
        reduced.transportBytesReceived += transportStats->bytes_received.value_or(0);
    }

    const auto candidateTypeById = [&](const std::string &candidateId) -> std::string {
        for (const auto *candidate : report->GetStatsOfType<webrtc::RTCLocalIceCandidateStats>()) {
            if (candidate->id() == candidateId && candidate->candidate_type.has_value()) {
                return *candidate->candidate_type;
            }
        }
        for (const auto *candidate : report->GetStatsOfType<webrtc::RTCRemoteIceCandidateStats>()) {
            if (candidate->id() == candidateId && candidate->candidate_type.has_value()) {
                return *candidate->candidate_type;
            }
        }
        return std::string();
    };

    for (const auto *pairStats : report->GetStatsOfType<webrtc::RTCIceCandidatePairStats>()) {
        if (pairStats->available_outgoing_bitrate.has_value()) {
            reduced.availableOutgoingBitrateBps = std::max(reduced.availableOutgoingBitrateBps, *pairStats->available_outgoing_bitrate);
            reduced.hasAvailableOutgoingBitrate = true;
        }
        if (pairStats->available_incoming_bitrate.has_value()) {
            reduced.availableIncomingBitrateBps = std::max(reduced.availableIncomingBitrateBps, *pairStats->available_incoming_bitrate);
            reduced.hasAvailableIncomingBitrate = true;
        }
        const bool isSelected = (!selectedPairId.empty() && pairStats->id() == selectedPairId)
            || (selectedPairId.empty() && pairStats->nominated.value_or(false));
        if (isSelected) {
            if (pairStats->current_round_trip_time.has_value()) {
                reduced.rttMs = *pairStats->current_round_trip_time * 1000.0;
            }
            if (pairStats->local_candidate_id.has_value()) {
                reduced.localCandidateType = candidateTypeById(*pairStats->local_candidate_id);
            }
            if (pairStats->remote_candidate_id.has_value()) {
                reduced.remoteCandidateType = candidateTypeById(*pairStats->remote_candidate_id);
            }
        }
    }

    for (const auto *outbound : report->GetStatsOfType<webrtc::RTCOutboundRtpStreamStats>()) {
        const bool isVideo = outbound->kind.value_or("") == "video";
        auto &send = isVideo ? reduced.videoSend : reduced.audioSend;
        send.present = true;
        send.bytesSent += outbound->bytes_sent.value_or(0);
        send.packetsSent += outbound->packets_sent.value_or(0);
        if (isVideo) {
            if (outbound->frames_per_second.has_value()) {
                send.frameRate = *outbound->frames_per_second;
            }
            send.frameWidth = (int)outbound->frame_width.value_or(0);
            send.frameHeight = (int)outbound->frame_height.value_or(0);
            if (outbound->quality_limitation_reason.has_value()) {
                send.qualityLimitationReason = *outbound->quality_limitation_reason;
            }
        }
    }

    for (const auto *remoteInbound : report->GetStatsOfType<webrtc::RTCRemoteInboundRtpStreamStats>()) {
        const bool isVideo = remoteInbound->kind.value_or("") == "video";
        auto &send = isVideo ? reduced.videoSend : reduced.audioSend;
        if (remoteInbound->fraction_lost.has_value()) {
            send.remoteLossFraction = *remoteInbound->fraction_lost;
        }
        if (remoteInbound->round_trip_time.has_value()) {
            send.remoteRttMs = *remoteInbound->round_trip_time * 1000.0;
        }
        if (remoteInbound->jitter.has_value()) {
            send.remoteJitterMs = *remoteInbound->jitter * 1000.0;
        }
    }

    for (const auto *inbound : report->GetStatsOfType<webrtc::RTCInboundRtpStreamStats>()) {
        const bool isVideo = inbound->kind.value_or("") == "video";
        auto &recv = isVideo ? reduced.videoRecv : reduced.audioRecv;
        recv.present = true;
        recv.bytesReceived += inbound->bytes_received.value_or(0);
        recv.packetsReceived += inbound->packets_received.value_or(0);
        recv.packetsLost += inbound->packets_lost.value_or(0);
        if (inbound->jitter.has_value()) {
            recv.jitterMs = *inbound->jitter * 1000.0;
        }
        if (isVideo) {
            recv.framesDecoded += inbound->frames_decoded.value_or(0);
            if (inbound->frames_per_second.has_value()) {
                recv.frameRate = *inbound->frames_per_second;
            }
            recv.frameWidth = (int)inbound->frame_width.value_or(0);
            recv.frameHeight = (int)inbound->frame_height.value_or(0);
        } else if (inbound->audio_level.has_value()) {
            recv.audioLevel = *inbound->audio_level;
        }
    }

    return reduced;
}

} // namespace

CallCoreHost::CallCoreHost(Descriptor &&descriptor, std::shared_ptr<Threads> threads) :
_threads(threads),
_version(descriptor.version),
_rtcServers(descriptor.rtcServers),
_enableP2P(descriptor.config.enableP2P),
_encryptionKey(std::move(descriptor.encryptionKey)),
_customParameters(descriptor.config.customParameters),
_stateUpdated(descriptor.stateUpdated),
_signalBarsUpdated(descriptor.signalBarsUpdated),
_remoteBatteryLevelIsLowUpdated(descriptor.remoteBatteryLevelIsLowUpdated),
_remoteMediaStateUpdated(descriptor.remoteMediaStateUpdated),
_signalingDataEmitted(descriptor.signalingDataEmitted),
_createAudioDeviceModule(descriptor.createAudioDeviceModule),
_createWrappedAudioDeviceModule(descriptor.createWrappedAudioDeviceModule),
_statsLogPath(descriptor.config.statsLogPath),
_videoCapture(descriptor.videoCapture) {
    if (!_customParameters.empty()) {
        std::string parsingError;
        auto customParametersJson = json11::Json::parse(_customParameters, parsingError);
        if (customParametersJson.is_object()) {
            _parsedCustomParameters = customParametersJson.object_items();
        }
    }

    // Both shipped versions are wire 11.0.0, whose signaling runs over SCTP.
    _useSctpSignalingTransport = true;
    webrtc::field_trial::InitFieldTrialsFromString(
        "WebRTC-DataChannel-Dcsctp/Enabled/"
        "WebRTC-Audio-iOS-Holding/Enabled/"
    );
}

CallCoreHost::~CallCoreHost() {
    disconnectAllIncomingVideoSinks();
    _currentStrongSink.reset();
    _threads->getWorkerThread()->BlockingCall([&]() {
        _audioDeviceModule = nullptr;
    });
    for (auto &it : _dataChannels) {
        if (it.second.channel) {
            it.second.channel->UnregisterObserver();
            it.second.channel = nullptr;
        }
        it.second.observer.reset();
    }
    _dataChannels.clear();
    _coreTracks.clear();
    _coreTransceivers.clear();
    _peerConnection = nullptr;
    _peerConnectionObserver.reset();
    _peerConnectionFactory = nullptr;
    _core.reset();
}

void CallCoreHost::start() {
    RTC_DCHECK(_threads->getMediaThread()->IsCurrent());
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());

    PlatformInterface::SharedInstance()->configurePlatformAudio();

    if (_useSctpSignalingTransport) {
        _signalingConnection = std::make_unique<SignalingSctpConnection>(
            _threads,
            [threads = _threads, weak](const std::vector<uint8_t> &data) {
                threads->getMediaThread()->PostTask([weak, data] {
                    const auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onSignalingData(data);
                });
            },
            [signalingDataEmitted = _signalingDataEmitted](const std::vector<uint8_t> &data) {
                signalingDataEmitted(data);
            },
            _encryptionKey.isOutgoing
        );
    } else {
        _signalingConnection = std::make_unique<ExternalSignalingConnection>(
            [threads = _threads, weak](const std::vector<uint8_t> &data) {
                threads->getMediaThread()->PostTask([weak, data] {
                    const auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onSignalingData(data);
                });
            },
            [signalingDataEmitted = _signalingDataEmitted](const std::vector<uint8_t> &data) {
                signalingDataEmitted(data);
            }
        );
    }
    _signalingConnection->start();

    _taskQueueFactory = webrtc::CreateDefaultTaskQueueFactory();
    _threads->getWorkerThread()->BlockingCall([&]() {
        _audioDeviceModule = createAudioDeviceModule();
    });

    webrtc::PeerConnectionFactoryDependencies peerConnectionFactoryDependencies;
    peerConnectionFactoryDependencies.network_thread = _threads->getNetworkThread();
    peerConnectionFactoryDependencies.signaling_thread = _threads->getMediaThread();
    peerConnectionFactoryDependencies.worker_thread = _threads->getWorkerThread();
    peerConnectionFactoryDependencies.task_queue_factory = webrtc::CreateDefaultTaskQueueFactory();
    peerConnectionFactoryDependencies.network_monitor_factory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
    peerConnectionFactoryDependencies.adm = _audioDeviceModule;

    webrtc::AudioProcessingBuilder builder;
    _audioProcessing = builder.Create();
    peerConnectionFactoryDependencies.audio_processing = _audioProcessing;
    peerConnectionFactoryDependencies.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
    peerConnectionFactoryDependencies.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();
    peerConnectionFactoryDependencies.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(true);
    peerConnectionFactoryDependencies.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory();
    webrtc::EnableMedia(peerConnectionFactoryDependencies);
    peerConnectionFactoryDependencies.event_log_factory = std::make_unique<webrtc::RtcEventLogFactory>(peerConnectionFactoryDependencies.task_queue_factory.get());

    _peerConnectionFactory = webrtc::CreateModularPeerConnectionFactory(std::move(peerConnectionFactoryDependencies));

    if (getCustomParameterBool(_parsedCustomParameters, "network_use_mtproto")) {
        // As in InstanceV2ReferenceImpl: selects a plain RtpTransport, matching
        // 13.0.0. SrtpTransport hard-fails when SRTP is inactive, so this is not
        // optional. Must precede CreatePeerConnectionOrError.
        webrtc::PeerConnectionFactoryInterface::Options factoryOptions;
        factoryOptions.disable_encryption = true;
        _peerConnectionFactory->SetOptions(factoryOptions);
    }

    _signalingEncryptedConnection = std::make_unique<EncryptedConnection>(
        EncryptedConnection::Type::Signaling,
        _encryptionKey,
        [](int, int) {
            // Service sends are core policy since Phase 2.6; the raw seal/open
            // methods used by the pump never invoke this callback.
        }
    );

    // Build the core config and create the core. The core emits its initial
    // command burst synchronously; drain it after create returns.
    json11::Json::array rtcServers;
    for (const auto &server : _rtcServers) {
        rtcServers.push_back(json11::Json::object{
            {"host", server.host},
            {"port", (int)server.port},
            {"login", server.login},
            {"password", server.password},
            {"isTurn", server.isTurn},
            {"isTcp", server.isTcp},
        });
    }
    const std::string configJson = json11::Json(json11::Json::object{
        {"abiVersion", 1},
        {"isOutgoing", _encryptionKey.isOutgoing},
        {"enableP2P", _enableP2P},
        {"customParameters", _customParameters},
        {"rtcServers", std::move(rtcServers)},
    }).dump();

    std::unique_ptr<CallCoreBackend> core;
#if TGCALLS_ALLOW_EXTERNAL_WASM_CORE
    // CLI/dev only. This key arrives from the server, so shipping it would be
    // a remote-code-execution surface: WAMR bounds the module's memory, but
    // the module drives negotiation and signaling through its host imports.
    // The app target never defines this macro, so neither this block nor the
    // filesystem loader exists in the shipped binary.
    {
        std::string parsingError;
        const auto custom = json11::Json::parse(_customParameters, parsingError);
        if (custom.is_object() && custom["wasm_core_path"].is_string()) {
            const auto path = custom["wasm_core_path"].string_value();
            if (!path.empty()) {
                RTC_LOG(LS_INFO) << "CallCoreHost: WAMR core backend (external): " << path;
                core = std::make_unique<WamrCoreBackend>(path);
            }
        }
    }
#endif
    if (!core) {
        if (versionUsesWasmCore(_version)) {
            RTC_LOG(LS_INFO) << "CallCoreHost: WAMR core backend (embedded)";
            core = std::make_unique<WamrCoreBackend>(v2wasm::kReferenceCoreWasm, v2wasm::kReferenceCoreWasmSize);
        } else {
            RTC_LOG(LS_INFO) << "CallCoreHost: native core backend";
            core = std::make_unique<NativeCoreBackend>();
        }
    }
    _core = std::move(core);

    const auto emitToQueue = [this](const uint8_t *data, size_t len) {
        std::string parsingError;
        auto command = json11::Json::parse(std::string((const char *)data, len), parsingError);
        if (!command.is_object()) {
            RTC_LOG(LS_ERROR) << "CallCoreHost: core emitted non-object command";
            return;
        }
        _pendingCommands.push_back(std::move(command));
    };
    if (!_core->create(configJson, emitToQueue)) {
        disableCoreWithFailure("core backend create failed");
        return;
    }
    processPendingCommands();

    if (_videoCapture) {
        // Stock start() re-applies descriptor.videoCapture (InstanceV2ReferenceImpl.cpp:726-728).
        // The core has already set _didBeginNegotiation, so this also triggers the
        // MediaState + renegotiation it would have folded into initial negotiation.
        setVideoCapture(_videoCapture);
    }
}

void CallCoreHost::deliverEvent(json11::Json::object &&event) {
    // Ordering invariant: an event deferred here (raised mid-drain) may be
    // overtaken by a directly-delivered event from an already-queued media
    // task. Today every consumer is gated by the core's negotiation flags
    // (_isMakingOffer/_isSettingRemoteAnswerPending), which absorb the
    // reorder; revisit if a new event type carries ordering-sensitive state.
    if (_isDeliveringEvent || _isProcessingCommands) {
        // Never re-enter the core: defer to a fresh media-thread task.
        const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
        _threads->getMediaThread()->PostTask([weak, event = std::move(event)]() mutable {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->deliverEventNow(std::move(event));
        });
        return;
    }
    deliverEventNow(std::move(event));
}

void CallCoreHost::deliverEventNow(json11::Json::object &&event) {
    if (!_core || _isStopped.load() || _isCoreDisabled) {
        return;
    }
    event.insert(std::make_pair("nowMs", json11::Json((double)rtc::TimeMillis())));
    const std::string serialized = json11::Json(std::move(event)).dump();

    _isDeliveringEvent = true;
    const bool ok = _core->onEvent((const uint8_t *)serialized.data(), serialized.size());
    _isDeliveringEvent = false;
    if (!ok) {
        disableCoreWithFailure("core backend event dispatch failed");
        return;
    }

    processPendingCommands();
}

void CallCoreHost::processPendingCommands() {
    if (_isProcessingCommands) {
        return;
    }
    _isProcessingCommands = true;
    while (!_pendingCommands.empty() && !_isCoreDisabled) {
        const auto command = std::move(_pendingCommands.front());
        _pendingCommands.pop_front();
        executeCommand(command);
    }
    _isProcessingCommands = false;
}

void CallCoreHost::executeCommand(json11::Json const &command) {
    const auto type = coreStringField(command, "@type");

    if (type == "core_ready") {
        if ((int)command["abiVersion"].number_value() != 1) {
            disableCoreWithFailure("core ABI version mismatch");
        }
    } else if (type == "pc_create") {
        executePcCreate(command);
    } else if (type == "pc_create_offer") {
        executeCreateDescription(true);
    } else if (type == "pc_create_answer") {
        executeCreateDescription(false);
    } else if (type == "pc_set_local_description") {
        executeSetLocalDescription(command);
    } else if (type == "pc_set_remote_description") {
        executeSetRemoteDescription(command);
    } else if (type == "pc_add_ice_candidate") {
        executeAddIceCandidate(command);
    } else if (type == "pc_restart_ice") {
        if (_peerConnection) {
            RTC_LOG(LS_INFO) << "CallCoreHost: RestartIce";
            _peerConnection->RestartIce();
        } else {
            emitErrorEvent("no peer connection", "pc_restart_ice");
        }
    } else if (type == "pc_add_transceiver") {
        executeAddTransceiver(command);
    } else if (type == "pc_set_parameters") {
        executeSetParameters(command);
    } else if (type == "pc_set_track_enabled") {
        executeSetTrackEnabled(command);
    } else if (type == "pc_remove_track") {
        executeRemoveTrack(command);
    } else if (type == "pc_set_incoming_sink") {
        executeSetIncomingSink(command);
    } else if (type == "pc_create_data_channel") {
        executeCreateDataChannel(command);
    } else if (type == "dc_send") {
        executeDcSend(command);
    } else if (type == "set_audio_processing") {
        applyAudioProcessingConfig(command, "set_audio_processing");
    } else if (type == "pc_set_configuration") {
        executeSetConfiguration(command);
    } else if (type == "signaling_send_packet") {
        executeSignalingSendPacket(command);
    } else if (type == "set_timer") {
        const int token = (int)command["token"].number_value();
        // Echoed back on the timer event so a core can discard a stale timer:
        // set_timer does NOT cancel a prior timer of the same token. Absent in a
        // command, number_value() yields 0, which the stats timer ignores.
        const int generation = (int)command["generation"].number_value();
        const int delayMs = (int)command["delayMs"].number_value();
        const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak, token, generation]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->deliverEvent({ {"@type", "timer"}, {"token", token}, {"generation", generation} });
        }, webrtc::TimeDelta::Millis(delayMs));
    } else if (type == "pc_get_stats") {
        executeGetStats();
    } else if (type == "emit_state") {
        const auto state = coreStringField(command, "state");
        State mappedState = State::Reconnecting;
        if (state == "established") {
            mappedState = State::Established;
        } else if (state == "failed") {
            mappedState = State::Failed;
        }
        if (_stateUpdated) {
            _stateUpdated(mappedState);
        }
    } else if (type == "emit_signal_bars") {
        if (_signalBarsUpdated) {
            _signalBarsUpdated((int)command["bars"].number_value());
        }
    } else if (type == "emit_remote_media_state") {
        if (_remoteMediaStateUpdated) {
            const auto audio = coreStringField(command, "audio") == "muted" ? AudioState::Muted : AudioState::Active;
            const auto videoValue = coreStringField(command, "video");
            VideoState video = VideoState::Inactive;
            if (videoValue == "paused") {
                video = VideoState::Paused;
            } else if (videoValue == "active") {
                video = VideoState::Active;
            }
            _remoteMediaStateUpdated(audio, video);
        }
    } else if (type == "emit_remote_battery_low") {
        if (_remoteBatteryLevelIsLowUpdated) {
            _remoteBatteryLevelIsLowUpdated(command["low"].bool_value());
        }
    } else if (type == "log") {
        RTC_LOG(LS_INFO) << "[core] " << coreStringField(command, "message");
    } else if (type == "stats_log") {
        _pendingStatsLogJson = coreStringField(command, "json");
    } else if (type == "close") {
        executeClose();
    } else {
        emitErrorEvent("unknown command", type);
    }
}

void CallCoreHost::emitErrorEvent(std::string const &message, std::string const &commandType) {
    RTC_LOG(LS_ERROR) << "CallCoreHost error: " << message << " (command: " << commandType << ")";
    deliverEvent({ {"@type", "error"}, {"message", message}, {"command", commandType} });
}

void CallCoreHost::disableCoreWithFailure(std::string const &reason) {
    RTC_LOG(LS_ERROR) << "CallCoreHost: disabling core: " << reason;
    _isCoreDisabled = true;
    _pendingCommands.clear();
    if (_stopCompletion) {
        // The queued "close" (if any) was just discarded; complete the pending
        // stop directly so the caller is never left hanging.
        if (_peerConnection) {
            _peerConnection->Close();
        }
        auto completion = std::move(_stopCompletion);
        _stopCompletion = nullptr;
        completion(FinalState());
    }
    if (_stateUpdated) {
        _stateUpdated(State::Failed);
    }
}

void CallCoreHost::executePcCreate(json11::Json const &command) {
    if (_peerConnection) {
        emitErrorEvent("peer connection already exists", "pc_create");
        return;
    }

    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
    _socketFactory = std::make_unique<rtc::BasicPacketSocketFactory>(_threads->getNetworkThread()->socketserver());
    _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), _threads->getNetworkThread()->socketserver());
    _relayPortFactory = std::make_unique<ReflectorRelayPortFactory>(_rtcServers, false, 0, _threads->getNetworkThread()->socketserver(), getCustomParameterBool(_parsedCustomParameters, "network_reflector_resolve_remote_candidate_ip"));

    webrtc::PeerConnectionDependencies peerConnectionDependencies(nullptr);
    _peerConnectionObserver = std::make_unique<v2wasm_detail::PeerConnectionDelegateAdapter>(std::weak_ptr<CallCoreHost>(shared_from_this()), _threads);
    peerConnectionDependencies.observer = _peerConnectionObserver.get();

    auto portAllocator = std::make_unique<cricket::BasicPortAllocator>(_networkManager.get(), _socketFactory.get(), nullptr, _relayPortFactory.get());

    if (getCustomParameterBool(_parsedCustomParameters, "network_disable_stun_when_unconfigured")) {
        bool hasStunServer = false;
        for (const auto &server : _rtcServers) {
            // Unlike the actual ICE-server construction (ReferenceCallCore.cpp's
            // rawHost.empty() check, standing in for stock's address.IsComplete()),
            // this does not validate the host, so a malformed STUN entry still
            // counts as "has STUN" here. That only suppresses
            // PORTALLOCATOR_DISABLE_STUN, i.e. it errs on the safe side.
            if (!server.isTurn && !server.isTcp) {
                hasStunServer = true;
                break;
            }
        }
        if (!hasStunServer) {
            // PeerConnection forces PORTALLOCATOR_ENABLE_SHARED_SOCKET on every
            // allocator, which auto-promotes each UDP relay into the STUN server
            // set and sends it real Binding Requests. A reflector cannot parse
            // those - it expects a 16-byte peer tag first.
            //
            // The WebRTC-UseTurnServerAsStunServer field trial does NOT help
            // here: BasicPortAllocator bypasses it when the STUN set is empty,
            // which is exactly the reflector case.
            //
            // InitializePortAllocator_n ORs onto the existing flags, so setting
            // this before the allocator is moved survives.
            portAllocator->set_flags(portAllocator->flags() | cricket::PORTALLOCATOR_DISABLE_STUN);
        }
    }

    peerConnectionDependencies.allocator = std::move(portAllocator);

    webrtc::PeerConnectionInterface::RTCConfiguration peerConnectionConfiguration;
    if (coreStringField(command, "iceTransportsType") == "all") {
        peerConnectionConfiguration.type = webrtc::PeerConnectionInterface::IceTransportsType::kAll;
    } else {
        peerConnectionConfiguration.type = webrtc::PeerConnectionInterface::IceTransportsType::kRelay;
    }
    peerConnectionConfiguration.tcp_candidate_policy = webrtc::PeerConnectionInterface::TcpCandidatePolicy::kTcpCandidatePolicyDisabled;
    peerConnectionConfiguration.enable_ice_renomination = true;
    peerConnectionConfiguration.sdp_semantics = webrtc::SdpSemantics::kUnifiedPlan;
    peerConnectionConfiguration.bundle_policy = webrtc::PeerConnectionInterface::kBundlePolicyMaxBundle;
    peerConnectionConfiguration.rtcp_mux_policy = webrtc::PeerConnectionInterface::RtcpMuxPolicy::kRtcpMuxPolicyRequire;
    peerConnectionConfiguration.enable_implicit_rollback = true;
    peerConnectionConfiguration.continual_gathering_policy = webrtc::PeerConnectionInterface::ContinualGatheringPolicy::GATHER_CONTINUALLY;
    peerConnectionConfiguration.audio_jitter_buffer_fast_accelerate = true;
    peerConnectionConfiguration.prioritize_most_likely_ice_candidate_pairs = true;

    for (const auto &server : command["iceServers"].array_items()) {
        webrtc::PeerConnectionInterface::IceServer mappedServer;
        for (const auto &url : server["urls"].array_items()) {
            mappedServer.urls.push_back(url.string_value());
        }
        mappedServer.username = coreStringField(server, "username");
        mappedServer.password = coreStringField(server, "password");
        peerConnectionConfiguration.servers.push_back(mappedServer);
    }

    if (getCustomParameterBool(_parsedCustomParameters, "network_use_mtproto")) {
        // Host-side by necessity, not preference: EncryptionKey is a secret and
        // must not cross into the wasm module, so the core cannot own this.
        peerConnectionDependencies.ice_transport_factory = std::make_unique<MtProtoIceTransportFactory>(_encryptionKey);
    }

    auto peerConnectionOrError = _peerConnectionFactory->CreatePeerConnectionOrError(peerConnectionConfiguration, std::move(peerConnectionDependencies));
    if (!peerConnectionOrError.ok()) {
        // Mirror InstanceV2ReferenceImpl::start(), which logs the underlying
        // message and returns immediately. The core turns a "pc_create" error
        // into updateNetworkState(false, true), so the call fails fast rather
        // than waiting out the 20s watchdog - but only the FIRST such event
        // carries useful diagnostics, so do not fall through to the audio
        // processing config: with no _audioProcessing it would emit a second,
        // redundant "pc_create" error, and with one it would configure audio
        // for a peer connection that does not exist.
        emitErrorEvent(std::string("CreatePeerConnectionOrError failed: ") + peerConnectionOrError.error().message(), "pc_create");
        return;
    }
    _peerConnection = peerConnectionOrError.value();

    if (command["audioProcessing"].is_object()) {
        applyAudioProcessingConfig(command["audioProcessing"], "pc_create");
    }
}

void CallCoreHost::applyAudioProcessingConfig(json11::Json const &config, std::string const &commandName) {
    if (!_audioProcessing) {
        emitErrorEvent("no audio processing", commandName);
        return;
    }
    auto apmConfig = _audioProcessing->GetConfig();
    if (config["echoCancellation"].is_bool()) {
        apmConfig.echo_canceller.enabled = config["echoCancellation"].bool_value();
    }
    if (config["noiseSuppression"].is_bool()) {
        apmConfig.noise_suppression.enabled = config["noiseSuppression"].bool_value();
    }
    if (config["autoGainControl"].is_bool()) {
        // Maps to the classic AGC only (gain_controller1); AGC2 is untouched.
        apmConfig.gain_controller1.enabled = config["autoGainControl"].bool_value();
    }
    if (config["highPassFilter"].is_bool()) {
        apmConfig.high_pass_filter.enabled = config["highPassFilter"].bool_value();
    }
    RTC_LOG(LS_INFO) << "CallCoreHost: ApplyConfig audio processing (" << commandName << ")";
    _audioProcessing->ApplyConfig(apmConfig);
}

void CallCoreHost::executeSetConfiguration(json11::Json const &command) {
    if (!_peerConnection) {
        emitErrorEvent("no peer connection", "pc_set_configuration");
        return;
    }
    // Merge ONLY the exposed keys into the live configuration so fields the
    // host chose at pc_create (sdp semantics, bundle policy, ...) survive.
    auto configuration = _peerConnection->GetConfiguration();
    if (command["iceTransportsType"].is_string()) {
        configuration.type = (coreStringField(command, "iceTransportsType") == "all")
            ? webrtc::PeerConnectionInterface::IceTransportsType::kAll
            : webrtc::PeerConnectionInterface::IceTransportsType::kRelay;
    }
    if (command["candidatePoolSize"].is_number()) {
        configuration.ice_candidate_pool_size = (int)command["candidatePoolSize"].number_value();
    }
    if (command["iceServers"].is_array()) {
        configuration.servers.clear();
        for (const auto &server : command["iceServers"].array_items()) {
            webrtc::PeerConnectionInterface::IceServer mappedServer;
            for (const auto &url : server["urls"].array_items()) {
                mappedServer.urls.push_back(url.string_value());
            }
            mappedServer.username = coreStringField(server, "username");
            mappedServer.password = coreStringField(server, "password");
            configuration.servers.push_back(mappedServer);
        }
    }
    const auto result = _peerConnection->SetConfiguration(configuration);
    if (!result.ok()) {
        emitErrorEvent(std::string("SetConfiguration failed: ") + result.message(), "pc_set_configuration");
    }
}

void CallCoreHost::executeCreateDescription(bool isOffer) {
    const std::string commandName = isOffer ? "pc_create_offer" : "pc_create_answer";
    const std::string descType = isOffer ? "offer" : "answer";
    if (!_peerConnection) {
        deliverEvent({ {"@type", "pc_description_created"}, {"ok", false}, {"type", descType}, {"sdp", ""}, {"error", "no peer connection"} });
        emitErrorEvent("no peer connection", commandName);
        return;
    }
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
    webrtc::scoped_refptr<v2wasm_detail::CreateSessionDescriptionObserverAdapter> observer(new rtc::RefCountedObject<v2wasm_detail::CreateSessionDescriptionObserverAdapter>([weak, threads = _threads, descType](std::string type, std::string sdp, std::string error) {
        threads->getMediaThread()->PostTask([weak, descType, type, sdp, error]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->deliverEvent({
                {"@type", "pc_description_created"},
                {"ok", error.empty() && !sdp.empty()},
                {"type", type.empty() ? descType : type},
                {"sdp", sdp},
                {"error", error},
            });
        });
    }));
    RTC_LOG(LS_INFO) << "CallCoreHost: " << (isOffer ? "CreateOffer" : "CreateAnswer");
    webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
    if (isOffer) {
        _peerConnection->CreateOffer(observer.get(), options);
    } else {
        _peerConnection->CreateAnswer(observer.get(), options);
    }
}

void CallCoreHost::executeSetLocalDescription(json11::Json const &command) {
    const auto type = coreStringField(command, "type");
    if (!_peerConnection) {
        deliverEvent({ {"@type", "pc_set_local_done"}, {"ok", false}, {"type", type}, {"sdp", ""} });
        emitErrorEvent("no peer connection", "pc_set_local_description");
        return;
    }
    webrtc::SdpParseError sdpParseError;
    std::unique_ptr<webrtc::SessionDescriptionInterface> localDescription(webrtc::CreateSessionDescription(type, coreStringField(command, "sdp"), &sdpParseError));
    if (!localDescription) {
        deliverEvent({ {"@type", "pc_set_local_done"}, {"ok", false}, {"type", type}, {"sdp", ""} });
        emitErrorEvent("failed to parse local SDP", "pc_set_local_description");
        return;
    }
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
    webrtc::scoped_refptr<webrtc::SetLocalDescriptionObserverInterface> observer(new rtc::RefCountedObject<v2wasm_detail::SetSessionDescriptionObserver>([weak, threads = _threads](webrtc::RTCError error) {
        threads->getMediaThread()->PostTask([weak, ok = error.ok()]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            // Read back the applied description (parity with the Phase-1 host:
            // the core sends what was actually applied, post-munge).
            std::string type;
            std::string sdp;
            if (ok && strong->_peerConnection && strong->_peerConnection->local_description()) {
                strong->_peerConnection->local_description()->ToString(&sdp);
                type = strong->_peerConnection->local_description()->type();
            }
            strong->deliverEvent({ {"@type", "pc_set_local_done"}, {"ok", ok && !sdp.empty()}, {"type", type}, {"sdp", sdp} });
        });
    }));
    RTC_LOG(LS_INFO) << "CallCoreHost: SetLocalDescription";
    _peerConnection->SetLocalDescription(std::move(localDescription), observer);
}

void CallCoreHost::executeSetRemoteDescription(json11::Json const &command) {
    if (!_peerConnection) {
        deliverEvent({ {"@type", "pc_set_remote_done"}, {"ok", false}, {"sdpType", coreStringField(command, "sdpType")} });
        emitErrorEvent("no peer connection", "pc_set_remote_description");
        return;
    }
    const auto sdpType = coreStringField(command, "sdpType");
    webrtc::SdpParseError sdpParseError;
    std::unique_ptr<webrtc::SessionDescriptionInterface> remoteDescription(webrtc::CreateSessionDescription(sdpType, coreStringField(command, "sdp"), &sdpParseError));
    if (!remoteDescription) {
        deliverEvent({ {"@type", "pc_set_remote_done"}, {"ok", false}, {"sdpType", sdpType} });
        emitErrorEvent("failed to parse remote SDP", "pc_set_remote_description");
        return;
    }
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
    webrtc::scoped_refptr<webrtc::SetRemoteDescriptionObserverInterface> observer(new rtc::RefCountedObject<v2wasm_detail::SetSessionDescriptionObserver>([weak, threads = _threads, sdpType](webrtc::RTCError error) {
        threads->getMediaThread()->PostTask([weak, ok = error.ok(), sdpType]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->deliverEvent({ {"@type", "pc_set_remote_done"}, {"ok", ok}, {"sdpType", sdpType} });
        });
    }));
    RTC_LOG(LS_INFO) << "CallCoreHost: SetRemoteDescription";
    _peerConnection->SetRemoteDescription(std::move(remoteDescription), observer);
}

void CallCoreHost::executeAddIceCandidate(json11::Json const &command) {
    if (!_peerConnection) {
        emitErrorEvent("no peer connection", "pc_add_ice_candidate");
        return;
    }
    webrtc::SdpParseError parseError;
    webrtc::IceCandidateInterface *iceCandidate = webrtc::CreateIceCandidate(coreStringField(command, "mid"), (int)command["mline"].number_value(), coreStringField(command, "sdp"), &parseError);
    if (iceCandidate) {
        std::unique_ptr<webrtc::IceCandidateInterface> candidatePtr;
        candidatePtr.reset(iceCandidate);
        _peerConnection->AddIceCandidate(candidatePtr.get());
    } else {
        emitErrorEvent("failed to parse ICE candidate", "pc_add_ice_candidate");
    }
}

void CallCoreHost::executeAddTransceiver(json11::Json const &command) {
    if (!_peerConnection) {
        emitErrorEvent("no peer connection", "pc_add_transceiver");
        return;
    }
    const auto id = coreStringField(command, "id");
    if (id.empty() || _coreTransceivers.find(id) != _coreTransceivers.end()) {
        emitErrorEvent("missing or duplicate transceiver id", "pc_add_transceiver");
        return;
    }
    const bool isVideo = (coreStringField(command, "kind") == "video");

    webrtc::RtpTransceiverInit transceiverInit;
    transceiverInit.stream_ids = { "0" };
    const auto direction = coreStringField(command, "direction");
    if (direction == "sendonly") {
        transceiverInit.direction = webrtc::RtpTransceiverDirection::kSendOnly;
    } else if (direction == "recvonly") {
        transceiverInit.direction = webrtc::RtpTransceiverDirection::kRecvOnly;
    } else if (direction == "inactive") {
        transceiverInit.direction = webrtc::RtpTransceiverDirection::kInactive;
    } else {
        transceiverInit.direction = webrtc::RtpTransceiverDirection::kSendRecv;
    }
    for (const auto &encoding : command["sendEncodings"].array_items()) {
        webrtc::RtpEncodingParameters encodingParameters;
        if (encoding["active"].is_bool()) {
            encodingParameters.active = encoding["active"].bool_value();
        }
        if (encoding["maxBitrateBps"].is_number()) {
            encodingParameters.max_bitrate_bps = (int)encoding["maxBitrateBps"].number_value();
        }
        if (encoding["minBitrateBps"].is_number()) {
            encodingParameters.min_bitrate_bps = (int)encoding["minBitrateBps"].number_value();
        }
        if (encoding["scaleResolutionDownBy"].is_number()) {
            encodingParameters.scale_resolution_down_by = encoding["scaleResolutionDownBy"].number_value();
        }
        if (encoding["rid"].is_string()) {
            encodingParameters.rid = encoding["rid"].string_value();
        }
        transceiverInit.send_encodings.push_back(encodingParameters);
    }

    // trackSource binds host-owned devices; track ids "0"/"1" are stock's and
    // appear in a=msid on the wire — do not change them.
    webrtc::scoped_refptr<webrtc::MediaStreamTrackInterface> track;
    const auto trackSource = coreStringField(command, "trackSource");
    if (trackSource == "microphone") {
        cricket::AudioOptions audioSourceOptions;
        webrtc::scoped_refptr<webrtc::AudioSourceInterface> audioSource = _peerConnectionFactory->CreateAudioSource(audioSourceOptions);
        track = _peerConnectionFactory->CreateAudioTrack("0", audioSource.get());
    } else if (trackSource == "camera") {
        auto videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());
        if (!videoCaptureImpl || videoCaptureImpl->isScreenCapture()) {
            emitErrorEvent("no usable video capture", "pc_add_transceiver");
            return;
        }
        track = _peerConnectionFactory->CreateVideoTrack(videoCaptureImpl->source(), "1");
        if (!track) {
            emitErrorEvent("CreateVideoTrack failed", "pc_add_transceiver");
            return;
        }
    }

    auto transceiverOrError = track
        ? _peerConnection->AddTransceiver(track, transceiverInit)
        : _peerConnection->AddTransceiver(isVideo ? cricket::MediaType::MEDIA_TYPE_VIDEO : cricket::MediaType::MEDIA_TYPE_AUDIO, transceiverInit);
    if (!transceiverOrError.ok()) {
        emitErrorEvent("AddTransceiver failed", "pc_add_transceiver");
        return;
    }
    const auto transceiver = transceiverOrError.value();
    _coreTransceivers.insert(std::make_pair(id, transceiver));
    if (track) {
        _coreTracks.insert(std::make_pair(id, track));
        track->set_enabled(true);
    }

    if (command["codecPreferences"].is_array()) {
        // Stock preference merge: requested names first, remaining
        // capabilities after, mapped back to capability entries.
        auto currentCapabilities = _peerConnectionFactory->GetRtpSenderCapabilities(isVideo ? cricket::MediaType::MEDIA_TYPE_VIDEO : cricket::MediaType::MEDIA_TYPE_AUDIO);
        std::vector<std::string> codecPreferences;
        for (const auto &name : command["codecPreferences"].array_items()) {
            codecPreferences.push_back(name.string_value());
        }
        for (const auto &codecCapability : currentCapabilities.codecs) {
            if (std::find_if(codecPreferences.begin(), codecPreferences.end(), [&](std::string const &value) {
                return value == codecCapability.name;
            }) != codecPreferences.end()) {
                continue;
            }
            codecPreferences.push_back(codecCapability.name);
        }
        std::vector<webrtc::RtpCodecCapability> codecCapabilities;
        for (const auto &name : codecPreferences) {
            for (const auto &codecCapability : currentCapabilities.codecs) {
                if (codecCapability.name == name) {
                    codecCapabilities.push_back(codecCapability);
                    break;
                }
            }
        }
        transceiver->SetCodecPreferences(codecCapabilities);
    }
}

void CallCoreHost::executeSetParameters(json11::Json const &command) {
    const auto it = _coreTransceivers.find(coreStringField(command, "id"));
    if (it == _coreTransceivers.end()) {
        emitErrorEvent("unknown transceiver id", "pc_set_parameters");
        return;
    }
    webrtc::RtpParameters parameters = it->second->sender()->GetParameters();
    const auto degradation = coreStringField(command, "degradationPreference");
    if (degradation == "disabled") {
        parameters.degradation_preference = webrtc::DegradationPreference::DISABLED;
    } else if (degradation == "maintain-framerate") {
        parameters.degradation_preference = webrtc::DegradationPreference::MAINTAIN_FRAMERATE;
    } else if (degradation == "maintain-resolution") {
        parameters.degradation_preference = webrtc::DegradationPreference::MAINTAIN_RESOLUTION;
    } else if (degradation == "balanced") {
        parameters.degradation_preference = webrtc::DegradationPreference::BALANCED;
    }
    const auto &encodings = command["encodings"].array_items();
    for (size_t i = 0; i < encodings.size() && i < parameters.encodings.size(); i++) {
        const auto &encoding = encodings[i];
        if (encoding["active"].is_bool()) {
            parameters.encodings[i].active = encoding["active"].bool_value();
        }
        if (encoding["maxBitrateBps"].is_number()) {
            parameters.encodings[i].max_bitrate_bps = (int)encoding["maxBitrateBps"].number_value();
        }
        if (encoding["minBitrateBps"].is_number()) {
            parameters.encodings[i].min_bitrate_bps = (int)encoding["minBitrateBps"].number_value();
        }
        if (encoding["scaleResolutionDownBy"].is_number()) {
            parameters.encodings[i].scale_resolution_down_by = encoding["scaleResolutionDownBy"].number_value();
        }
    }
    const auto result = it->second->sender()->SetParameters(parameters);
    if (!result.ok()) {
        emitErrorEvent("SetParameters failed", "pc_set_parameters");
    }
}

void CallCoreHost::executeSetTrackEnabled(json11::Json const &command) {
    const auto it = _coreTracks.find(coreStringField(command, "id"));
    if (it == _coreTracks.end()) {
        emitErrorEvent("unknown track id", "pc_set_track_enabled");
        return;
    }
    it->second->set_enabled(command["enabled"].bool_value());
}

void CallCoreHost::executeRemoveTrack(json11::Json const &command) {
    const auto id = coreStringField(command, "id");
    const auto it = _coreTransceivers.find(id);
    if (it == _coreTransceivers.end()) {
        emitErrorEvent("unknown transceiver id", "pc_remove_track");
        return;
    }
    if (_peerConnection) {
        _peerConnection->RemoveTrackOrError(it->second->sender());
    }
    _coreTransceivers.erase(it);
    _coreTracks.erase(id);
}

void CallCoreHost::executeSetIncomingSink(json11::Json const &command) {
    const auto mid = coreStringField(command, "mid");
    if (mid.empty()) {
        emitErrorEvent("missing mid", "pc_set_incoming_sink");
        return;
    }
    _requestedSinkMids.insert(mid);
    const auto it = _incomingVideoTransceivers.find(mid);
    if (it != _incomingVideoTransceivers.end()) {
        connectIncomingVideoSink(it->second);
    }
}

void CallCoreHost::executeCreateDataChannel(json11::Json const &command) {
    if (!_peerConnection) {
        emitErrorEvent("no peer connection", "pc_create_data_channel");
        return;
    }
    webrtc::DataChannelInit dataChannelInit;
    if (command["ordered"].is_bool()) {
        dataChannelInit.ordered = command["ordered"].bool_value();
    }
    if (command["negotiated"].is_bool()) {
        dataChannelInit.negotiated = command["negotiated"].bool_value();
    }
    if (command["id"].is_number()) {
        dataChannelInit.id = (int)command["id"].number_value();
    }
    std::string label = coreStringField(command, "label");
    if (label.empty()) {
        label = "data";
    }
    if (_dataChannels.count(label)) {
        emitErrorEvent("duplicate data channel label", "pc_create_data_channel");
        return;
    }
    auto dataChannelOrError = _peerConnection->CreateDataChannelOrError(label, &dataChannelInit);
    if (dataChannelOrError.ok()) {
        attachDataChannel(label, dataChannelOrError.value());
    } else {
        emitErrorEvent("CreateDataChannelOrError failed", "pc_create_data_channel");
    }
}

void CallCoreHost::executeDcSend(json11::Json const &command) {
    std::string label = coreStringField(command, "label");
    if (label.empty()) {
        label = "data";
    }
    const auto it = _dataChannels.find(label);
    if (it == _dataChannels.end()) {
        emitErrorEvent("unknown data channel label", "dc_send");
        return;
    }
    if (!it->second.isOpen) {
        emitErrorEvent("data channel not open", "dc_send");
        return;
    }
    if (command["dataB64"].is_string()) {
        const auto bytes = v2wasm::base64Decode(command["dataB64"].string_value());
        if (!bytes || bytes->size() > kMaxDcMessageBytes) {
            emitErrorEvent("bad dc payload", "dc_send");
            return;
        }
        RTC_LOG(LS_INFO) << "CallCoreHost dc_send: [" << label << "] " << bytes->size() << " binary bytes";
        it->second.channel->Send(webrtc::DataBuffer(rtc::CopyOnWriteBuffer(bytes->data(), bytes->size()), true));
    } else {
        const auto data = coreStringField(command, "data");
        if (data.size() > kMaxDcMessageBytes) {
            emitErrorEvent("bad dc payload", "dc_send");
            return;
        }
        RTC_LOG(LS_INFO) << "CallCoreHost dc_send: [" << label << "] " << data;
        it->second.channel->Send(webrtc::DataBuffer(data));
    }
    it->second.lastBufferedAmount = it->second.channel->buffered_amount();
}

void CallCoreHost::attachDataChannel(std::string const &label, webrtc::scoped_refptr<webrtc::DataChannelInterface> dataChannel) {
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());

    v2wasm_detail::DataChannelObserverImpl::Parameters dataChannelObserverParams;
    dataChannelObserverParams.onStateChange = [threads = _threads, weak, label]() {
        threads->getMediaThread()->PostTask([weak, label]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->onDataChannelStateUpdated(label);
        });
    };
    dataChannelObserverParams.onMessage = [threads = _threads, weak, label](webrtc::DataBuffer const &buffer) {
        const auto strong = weak.lock();
        if (!strong) {
            return;
        }
        if (!buffer.binary) {
            std::string message(buffer.data.data(), buffer.data.data() + buffer.data.size());
            strong->deliverEvent({ {"@type", "dc_message"}, {"label", label}, {"data", message} });
        } else {
            strong->deliverEvent({
                {"@type", "dc_message"},
                {"label", label},
                {"dataB64", v2wasm::base64Encode((const uint8_t *)buffer.data.data(), buffer.data.size())},
            });
        }
    };
    dataChannelObserverParams.onBufferedAmountChange = [threads = _threads, weak, label](uint64_t) {
        threads->getMediaThread()->PostTask([weak, label]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->onDataChannelBufferedAmountChanged(label);
        });
    };

    auto &entry = _dataChannels[label];
    entry.observer = std::make_unique<v2wasm_detail::DataChannelObserverImpl>(std::move(dataChannelObserverParams));
    entry.channel = dataChannel;
    onDataChannelStateUpdated(label);
    entry.channel->RegisterObserver(entry.observer.get());
}

void CallCoreHost::onDataChannelStateUpdated(std::string const &label) {
    const auto it = _dataChannels.find(label);
    if (it == _dataChannels.end() || !it->second.channel) {
        return;
    }
    const bool open = (it->second.channel->state() == webrtc::DataChannelInterface::DataState::kOpen);
    if (open != it->second.isOpen) {
        it->second.isOpen = open;
        deliverEvent({ {"@type", "dc_state"}, {"label", label}, {"open", open} });
    }
}

void CallCoreHost::onDataChannelBufferedAmountChanged(std::string const &label) {
    const auto it = _dataChannels.find(label);
    if (it == _dataChannels.end() || !it->second.channel) {
        return;
    }
    const uint64_t amount = it->second.channel->buffered_amount();
    if (amount == 0 && it->second.lastBufferedAmount != 0) {
        deliverEvent({ {"@type", "dc_buffered"}, {"label", label}, {"bufferedAmount", 0} });
    }
    it->second.lastBufferedAmount = amount;
}

void CallCoreHost::executeSignalingSendPacket(json11::Json const &command) {
    const auto packet = v2wasm::base64Decode(coreStringField(command, "packetB64"));
    if (!packet || packet->size() < 5) {
        emitErrorEvent("bad packetB64", "signaling_send_packet");
        return;
    }
    if (!_signalingConnection || !_signalingEncryptedConnection) {
        emitErrorEvent("signaling not available", "signaling_send_packet");
        return;
    }
    // seq = first 4 bytes (network order); low 30 bits are the AEAD counter
    // (top two bits are the core's framing flags — see CallCoreABI.h).
    const uint32_t seq = (uint32_t((*packet)[0]) << 24) | (uint32_t((*packet)[1]) << 16)
        | (uint32_t((*packet)[2]) << 8) | uint32_t((*packet)[3]);
    const uint32_t counter = seq & 0x3FFFFFFFu;
    if (counter <= _lastSentSignalingCounter) {
        emitErrorEvent("non-monotonic signaling counter", "signaling_send_packet");
        return;
    }
    const auto sealed = _signalingEncryptedConnection->encryptFullPlaintextPacket(
        rtc::CopyOnWriteBuffer(packet->data(), packet->size()));
    if (!sealed) {
        emitErrorEvent("could not encrypt signaling packet", "signaling_send_packet");
        return;
    }
    _lastSentSignalingCounter = counter;
    RTC_LOG(LS_INFO) << "CallCoreHost signaling_send_packet: counter " << counter << ", " << packet->size() << " plaintext bytes";
    _signalingConnection->send(std::vector<uint8_t>(sealed->data(), sealed->data() + sealed->size()));
}

void CallCoreHost::receiveSignalingData(const std::vector<uint8_t> &data) {
    if (_signalingConnection) {
        _signalingConnection->receiveExternal(data);
    }
}

void CallCoreHost::onSignalingData(const std::vector<uint8_t> &data) {
    if (!_signalingEncryptedConnection) {
        RTC_LOG(LS_ERROR) << "CallCoreHost: receiveSignalingData encryption not available";
        return;
    }
    const auto plaintext = _signalingEncryptedConnection->decryptFullPlaintextPacket(
        rtc::CopyOnWriteBuffer(data.data(), data.size()));
    if (!plaintext) {
        RTC_LOG(LS_ERROR) << "CallCoreHost: could not decrypt signaling packet";
        return;
    }
    deliverEvent({
        {"@type", "signaling_packet"},
        {"packetB64", v2wasm::base64Encode(plaintext->data(), plaintext->size())},
    });
}

void CallCoreHost::executeGetStats() {
    if (!_peerConnection) {
        return;
    }
    const auto weak = std::weak_ptr<CallCoreHost>(shared_from_this());
    webrtc::scoped_refptr<v2wasm_detail::StatsCollectorCallbackAdapter> callback(new rtc::RefCountedObject<v2wasm_detail::StatsCollectorCallbackAdapter>([weak, threads = _threads](const webrtc::scoped_refptr<const webrtc::RTCStatsReport> &report) {
        ReducedStats reduced = reduceStatsReport(report);
        threads->getMediaThread()->PostTask([weak, reduced]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            const int64_t nowMs = rtc::TimeMillis();
            const double elapsedSec = strong->_lastStatsTimestampMs > 0
                ? (double)(nowMs - strong->_lastStatsTimestampMs) / 1000.0
                : 0.0;
            strong->_lastStatsTimestampMs = nowMs;
            const auto deltaKbps = [elapsedSec](uint64_t current, uint64_t &last) {
                double kbps = 0.0;
                if (elapsedSec > 0.0 && current >= last) {
                    kbps = (double)(current - last) * 8.0 / 1024.0 / elapsedSec;
                }
                last = current;
                return kbps;
            };

            json11::Json::object transport;
            if (reduced.rttMs >= 0.0) {
                transport["rttMs"] = reduced.rttMs;
            }
            if (reduced.hasAvailableOutgoingBitrate) {
                transport["availableOutgoingKbps"] = reduced.availableOutgoingBitrateBps / 1024.0;
            }
            if (reduced.hasAvailableIncomingBitrate) {
                transport["availableIncomingKbps"] = reduced.availableIncomingBitrateBps / 1024.0;
            }
            transport["bytesSent"] = (double)reduced.transportBytesSent;
            transport["bytesReceived"] = (double)reduced.transportBytesReceived;
            if (!reduced.localCandidateType.empty()) {
                transport["localCandidateType"] = reduced.localCandidateType;
            }
            if (!reduced.remoteCandidateType.empty()) {
                transport["remoteCandidateType"] = reduced.remoteCandidateType;
            }

            const auto sendObject = [&deltaKbps](ReducedStats::Send const &send, uint64_t &lastBytes, bool isVideo) {
                json11::Json::object object;
                object["bitrateKbps"] = deltaKbps(send.bytesSent, lastBytes);
                object["packetsSent"] = (double)send.packetsSent;
                if (send.remoteLossFraction >= 0.0) {
                    object["remoteLossFraction"] = send.remoteLossFraction;
                }
                if (send.remoteRttMs >= 0.0) {
                    object["remoteRttMs"] = send.remoteRttMs;
                }
                if (send.remoteJitterMs >= 0.0) {
                    object["remoteJitterMs"] = send.remoteJitterMs;
                }
                if (isVideo) {
                    if (send.frameRate >= 0.0) {
                        object["frameRate"] = send.frameRate;
                    }
                    if (send.frameWidth > 0) {
                        object["frameWidth"] = send.frameWidth;
                        object["frameHeight"] = send.frameHeight;
                    }
                    if (!send.qualityLimitationReason.empty()) {
                        object["qualityLimitationReason"] = send.qualityLimitationReason;
                    }
                }
                return object;
            };
            const auto recvObject = [&deltaKbps](ReducedStats::Recv const &recv, uint64_t &lastBytes, bool isVideo) {
                json11::Json::object object;
                object["bitrateKbps"] = deltaKbps(recv.bytesReceived, lastBytes);
                object["packetsReceived"] = (double)recv.packetsReceived;
                object["packetsLost"] = recv.packetsLost;
                if (isVideo) {
                    object["framesDecoded"] = (double)recv.framesDecoded;
                    if (recv.frameRate >= 0.0) {
                        object["frameRate"] = recv.frameRate;
                    }
                    if (recv.frameWidth > 0) {
                        object["frameWidth"] = recv.frameWidth;
                        object["frameHeight"] = recv.frameHeight;
                    }
                } else {
                    if (recv.jitterMs >= 0.0) {
                        object["jitterMs"] = recv.jitterMs;
                    }
                    if (recv.audioLevel >= 0.0) {
                        object["audioLevel"] = recv.audioLevel;
                    }
                }
                return object;
            };

            json11::Json::object audio;
            if (reduced.audioSend.present) {
                audio["send"] = sendObject(reduced.audioSend, strong->_lastAudioBytesSent, false);
            }
            if (reduced.audioRecv.present) {
                audio["recv"] = recvObject(reduced.audioRecv, strong->_lastAudioBytesReceived, false);
            }
            json11::Json::object video;
            if (reduced.videoSend.present) {
                video["send"] = sendObject(reduced.videoSend, strong->_lastVideoBytesSent, true);
            }
            if (reduced.videoRecv.present) {
                video["recv"] = recvObject(reduced.videoRecv, strong->_lastVideoBytesReceived, true);
            }

            strong->deliverEvent({
                {"@type", "stats"},
                // Stock parity: identical computation to the Phase-1 event.
                {"sendBitrateKbps", reduced.availableOutgoingBitrateBps / 1024.0},
                {"transport", std::move(transport)},
                {"audio", std::move(audio)},
                {"video", std::move(video)},
            });
        });
    }));
    _peerConnection->GetStats(callback.get());
}

void CallCoreHost::executeClose() {
    if (_peerConnection) {
        _peerConnection->Close();
    }
    if (!_pendingStatsLogJson.empty() && !_statsLogPath.data.empty()) {
        std::ofstream file;
        file.open(_statsLogPath.data);
        file << _pendingStatsLogJson;
        file.close();
    }
    if (_stopCompletion) {
        FinalState finalState;
        auto completion = std::move(_stopCompletion);
        _stopCompletion = nullptr;
        completion(finalState);
    }
}

void CallCoreHost::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _videoCapture = videoCapture;
    bool screencast = false;
    if (const auto captureImpl = GetVideoCaptureAssumingSameThread(videoCapture.get())) {
        screencast = captureImpl->isScreenCapture();
    }
    deliverEvent({ {"@type", "video_capture"}, {"active", videoCapture != nullptr}, {"screencast", screencast} });
}

void CallCoreHost::setMuteMicrophone(bool mute) {
    deliverEvent({ {"@type", "mute"}, {"muted", mute} });
}

void CallCoreHost::setIsLowBatteryLevel(bool low) {
    deliverEvent({ {"@type", "battery_low"}, {"low", low} });
}

void CallCoreHost::setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    disconnectAllIncomingVideoSinks();
    _currentStrongSink = sink.lock();
    if (!_currentStrongSink) {
        return;
    }
    for (const auto &mid : _requestedSinkMids) {
        const auto it = _incomingVideoTransceivers.find(mid);
        if (it != _incomingVideoTransceivers.end()) {
            connectIncomingVideoSink(it->second);
        }
    }
}

void CallCoreHost::connectIncomingVideoSink(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
    if (!_currentStrongSink) {
        return;
    }
    auto track = transceiver->receiver()->track();
    if (!track) {
        return;
    }
    webrtc::VideoTrackInterface *videoTrack = (webrtc::VideoTrackInterface *)track.get();
    videoTrack->AddOrUpdateSink(_currentStrongSink.get(), rtc::VideoSinkWants());
    _attachedSinkTracks.insert(videoTrack);
}

void CallCoreHost::disconnectIncomingVideoSink(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
    if (!_currentStrongSink) {
        return;
    }
    auto track = transceiver->receiver()->track();
    if (!track) {
        return;
    }
    webrtc::VideoTrackInterface *videoTrack = (webrtc::VideoTrackInterface *)track.get();
    if (_attachedSinkTracks.erase(videoTrack) == 0) {
        // Never attached to this track - RemoveSink would trip
        // RTC_DCHECK(FindSinkPair(sink)) in a debug build.
        return;
    }
    videoTrack->RemoveSink(_currentStrongSink.get());
}

void CallCoreHost::disconnectAllIncomingVideoSinks() {
    if (!_currentStrongSink) {
        return;
    }
    for (const auto &it : _incomingVideoTransceivers) {
        disconnectIncomingVideoSink(it.second);
    }
    _attachedSinkTracks.clear();
}

void CallCoreHost::setAudioInputDevice(std::string id) {
    SetAudioInputDeviceById(_audioDeviceModule.get(), id);
}

void CallCoreHost::setAudioOutputDevice(std::string id) {
    SetAudioOutputDeviceById(_audioDeviceModule.get(), id);
}

void CallCoreHost::stop(std::function<void(FinalState)> completion) {
    // stop() must arrive as its own media-thread task, never from inside the
    // pump (deliverEventNow below bypasses deliverEvent's deferral guard).
    RTC_DCHECK(!_isDeliveringEvent && !_isProcessingCommands);
    if (_isStopped.load()) {
        // Double stop: the first call owns the core shutdown; complete
        // immediately instead of silently dropping the completion.
        if (completion) {
            completion(FinalState());
        }
        return;
    }
    if (_isCoreDisabled) {
        // A disabled core can never emit "close"; finish the shutdown directly
        // (mirrors executeClose minus the core-produced stats log).
        if (_peerConnection) {
            _peerConnection->Close();
        }
        _isStopped = true;
        if (completion) {
            completion(FinalState());
        }
        return;
    }
    _stopCompletion = std::move(completion);
    deliverEventNow({ {"@type", "stop"} });
    _isStopped = true;
}

webrtc::scoped_refptr<webrtc::AudioDeviceModule> CallCoreHost::createAudioDeviceModule() {
    const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
#ifdef WEBRTC_IOS
        return rtc::make_ref_counted<webrtc::tgcalls_ios_adm::AudioDeviceModuleIOS>(false, false, false, 1);
#else
        return webrtc::AudioDeviceModule::Create(layer, _taskQueueFactory.get());
#endif
    };
    const auto check = [&](const webrtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
        return (result && result->Init() == 0) ? result : nullptr;
    };
    if (_createWrappedAudioDeviceModule) {
        auto result = _createWrappedAudioDeviceModule(_taskQueueFactory.get());
        if (result) {
            return result;
        }
    }
    if (_createAudioDeviceModule) {
        if (const auto result = check(_createAudioDeviceModule(_taskQueueFactory.get()))) {
            return result;
        }
    }
    return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
}

} // namespace tgcalls
