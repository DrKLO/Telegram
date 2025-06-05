#include "v2/InstanceV2ReferenceImpl.h"

#include "LogSinkImpl.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "v2/NativeNetworkingImpl.h"
#include "v2/Signaling.h"
#include "v2/ContentNegotiation.h"

#include "CodecSelectHelper.h"

#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_decoder_multi_channel_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/audio_codecs/L16/audio_decoder_L16.h"
#include "api/audio_codecs/L16/audio_encoder_L16.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "system_wrappers/include/field_trial.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "call/call.h"
#include "api/call/audio_sink.h"
#include "modules/audio_processing/audio_buffer.h"
#include "absl/strings/match.h"
#include "audio/audio_state.h"
#include "modules/audio_coding/neteq/default_neteq_factory.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "api/candidate.h"
#include "api/jsep_ice_candidate.h"
#include "pc/used_ids.h"
#include "media/base/sdp_video_format_utils.h"
#include "pc/media_session.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "pc/peer_connection.h"
#include "pc/peer_connection_proxy.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/stats/rtc_stats_report.h"
#include "api/enable_media.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "rtc_base/network.h"
#include "p2p/base/default_ice_transport_factory.h"

#include "AudioFrame.h"
#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "CodecSelectHelper.h"
#include "AudioDeviceHelper.h"
#include "SignalingEncryption.h"
#include "ReflectorRelayPortFactory.h"
#include "v2/SignalingConnection.h"
#include "v2/ExternalSignalingConnection.h"
#include "v2/SignalingSctpConnection.h"
#include "v2/ReflectorRelayPortFactory.h"
#ifdef WEBRTC_IOS
#include "platform/darwin/iOS/tgcalls_audio_device_module_ios.h"
#endif
#include <random>
#include <sstream>
#include <map>

#include "third-party/json11.hpp"
#include "utils/gzip.h"

namespace tgcalls {
namespace {

enum class SignalingProtocolVersion {
    V1,
    V2
};

SignalingProtocolVersion signalingProtocolVersion(std::string const &version) {
    if (version == "10.0.0") {
        return SignalingProtocolVersion::V1;
    } else if (version == "11.0.0") {
        return SignalingProtocolVersion::V2;
    } else {
        RTC_LOG(LS_ERROR) << "signalingProtocolVersion: unknown version " << version;

        return SignalingProtocolVersion::V2;
    }
}

bool signalingProtocolSupportsCompression(SignalingProtocolVersion version) {
    switch (version) {
        case SignalingProtocolVersion::V1:
            return false;
        case SignalingProtocolVersion::V2:
            return true;
        default:
            RTC_DCHECK_NOTREACHED();
            break;
    }
    return false;
}

static VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

class SetSessionDescriptionObserver : public webrtc::SetLocalDescriptionObserverInterface, public webrtc::SetRemoteDescriptionObserverInterface {
public:
    SetSessionDescriptionObserver(std::function<void(webrtc::RTCError)> &&completion) :
    _completion(std::move(completion)) {
    }

    virtual void OnSetLocalDescriptionComplete(webrtc::RTCError error) override {
        OnCompelete(error);
    }

    virtual void OnSetRemoteDescriptionComplete(webrtc::RTCError error) override {
        OnCompelete(error);
    }

private:
    void OnCompelete(webrtc::RTCError error) {
        _completion(error);
    }

    std::function<void(webrtc::RTCError)> _completion;
};

class PeerConnectionDelegateAdapter: public webrtc::PeerConnectionObserver {
public:
    struct Parameters {
        std::function<void()> onRenegotiationNeeded;
        std::function<void(const webrtc::IceCandidateInterface *)> onIceCandidate;
        std::function<void(webrtc::PeerConnectionInterface::SignalingState state)> onSignalingChange;
        std::function<void(webrtc::PeerConnectionInterface::IceConnectionState state)> onConnectionChange;
        std::function<void(webrtc::scoped_refptr<webrtc::DataChannelInterface>)> onDataChannel;
        std::function<void(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>)> onTransceiverAdded;
        std::function<void(webrtc::scoped_refptr<webrtc::RtpReceiverInterface>)> onTransceiverRemoved;
        std::function<void(const cricket::CandidatePairChangeEvent &)> onCandidatePairChangeEvent;
    };

public:
    PeerConnectionDelegateAdapter(
        Parameters &&parameters
    ) : _parameters(std::move(parameters)) {
    }

    ~PeerConnectionDelegateAdapter() override {
    }

    void OnSignalingChange(webrtc::PeerConnectionInterface::SignalingState new_state) override {
        if (_parameters.onSignalingChange) {
            _parameters.onSignalingChange(new_state);
        }
    }

    void OnAddStream(webrtc::scoped_refptr<webrtc::MediaStreamInterface> stream) override {
    }

    void OnRemoveStream(webrtc::scoped_refptr<webrtc::MediaStreamInterface> stream) override {
    }

    void OnTrack(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) override {
        if (_parameters.onTransceiverAdded) {
            _parameters.onTransceiverAdded(transceiver);
        }
    }

    void OnDataChannel(webrtc::scoped_refptr<webrtc::DataChannelInterface> data_channel) override {
        if (_parameters.onDataChannel) {
            _parameters.onDataChannel(data_channel);
        }
    }

    void OnRenegotiationNeeded() override {
        if (_parameters.onRenegotiationNeeded) {
            _parameters.onRenegotiationNeeded();
        }
    }

    void OnIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) override {
        if (_parameters.onConnectionChange) {
            _parameters.onConnectionChange(new_state);
        }
    }

    void OnStandardizedIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) override {
    }

    void OnConnectionChange(webrtc::PeerConnectionInterface::PeerConnectionState new_state) override {
    }

    void OnIceGatheringChange(webrtc::PeerConnectionInterface::IceGatheringState new_state) override {
    }

    void OnIceCandidate(const webrtc::IceCandidateInterface *candidate) override {
        if (_parameters.onIceCandidate) {
            _parameters.onIceCandidate(candidate);
        }
    }

    void OnIceCandidatesRemoved(const std::vector<cricket::Candidate> &candidates) override {
    }

    void OnIceSelectedCandidatePairChanged(const cricket::CandidatePairChangeEvent &event) override {
        if (_parameters.onCandidatePairChangeEvent) {
            _parameters.onCandidatePairChangeEvent(event);
        }
    }

    void OnAddTrack(webrtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver, const std::vector<webrtc::scoped_refptr<webrtc::MediaStreamInterface>> &streams) override {

    }

    void OnRemoveTrack(webrtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) override {
        if (_parameters.onTransceiverRemoved) {
            _parameters.onTransceiverRemoved(receiver);
        }
    }

private:
    Parameters _parameters;
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

class VideoSinkImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    VideoSinkImpl() {
    }

    virtual ~VideoSinkImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        //_lastFrame = frame;
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnFrame(frame);
            }
        }
    }

    virtual void OnDiscardedFrame() override {
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnDiscardedFrame();
            }
        }
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _sinks.push_back(impl);
        if (_lastFrame) {
            auto strong = impl.lock();
            if (strong) {
                strong->OnFrame(_lastFrame.value());
            }
        }
    }

private:
    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> _sinks;
    absl::optional<webrtc::VideoFrame> _lastFrame;
};

class DataChannelObserverImpl : public webrtc::DataChannelObserver {
public:
    struct Parameters {
        std::function<void()> onStateChange;
        std::function<void(webrtc::DataBuffer const &)> onMessage;
    };

public:
    DataChannelObserverImpl(Parameters &&parameters) :
    _parameters(std::move(parameters)) {
    }

    virtual void OnStateChange() override {
        if (_parameters.onStateChange) {
            _parameters.onStateChange();
        }
    }

    virtual void OnMessage(webrtc::DataBuffer const &buffer) override {
        if (_parameters.onMessage) {
            _parameters.onMessage(buffer);
        }
    }

    virtual ~DataChannelObserverImpl() {
    }

private:
    Parameters _parameters;
};

template<typename T>
struct StateLogRecord {
    int64_t timestamp = 0;
    T record;

    explicit StateLogRecord(int32_t timestamp_, T &&record_) :
    timestamp(timestamp_),
    record(std::move(record_)) {
    }
};

struct NetworkStateLogRecord {
    bool isConnected = false;
    bool isFailed = false;
    absl::optional<InstanceNetworking::RouteDescription> route;
    absl::optional<InstanceNetworking::ConnectionDescription> connection;

    bool operator==(NetworkStateLogRecord const &rhs) const {
        if (isConnected != rhs.isConnected) {
            return false;
        }
        if (isFailed != rhs.isFailed) {
            return false;
        }
        if (route != rhs.route) {
            return false;
        }
        if (connection != rhs.connection) {
            return false;
        }

        return true;
    }
};

struct NetworkBitrateLogRecord {
    int32_t bitrate = 0;
};

} // namespace

class InstanceV2ReferenceImplInternal : public std::enable_shared_from_this<InstanceV2ReferenceImplInternal> {
public:
    InstanceV2ReferenceImplInternal(Descriptor &&descriptor, std::shared_ptr<Threads> threads) :
    _signalingProtocolVersion(signalingProtocolVersion(descriptor.version)),
    _threads(threads),
    _rtcServers(descriptor.rtcServers),
    _proxy(std::move(descriptor.proxy)),
    _enableP2P(descriptor.config.enableP2P),
    _encryptionKey(std::move(descriptor.encryptionKey)),
    _stateUpdated(descriptor.stateUpdated),
    _signalBarsUpdated(descriptor.signalBarsUpdated),
    _audioLevelsUpdated(descriptor.audioLevelsUpdated),
    _remoteBatteryLevelIsLowUpdated(descriptor.remoteBatteryLevelIsLowUpdated),
    _remoteMediaStateUpdated(descriptor.remoteMediaStateUpdated),
    _remotePrefferedAspectRatioUpdated(descriptor.remotePrefferedAspectRatioUpdated),
    _signalingDataEmitted(descriptor.signalingDataEmitted),
    _createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _createWrappedAudioDeviceModule(descriptor.createWrappedAudioDeviceModule),
    _statsLogPath(descriptor.config.statsLogPath),
    _eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
    _taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
    _videoCapture(descriptor.videoCapture),
    _platformContext(descriptor.platformContext) {
        webrtc::field_trial::InitFieldTrialsFromString(
            "WebRTC-DataChannel-Dcsctp/Enabled/"
            "WebRTC-Audio-iOS-Holding/Enabled/"
        );
    }

    ~InstanceV2ReferenceImplInternal() {
        _currentStrongSink.reset();

        _threads->getWorkerThread()->BlockingCall([&]() {
            _audioDeviceModule = nullptr;
        });

        if (_dataChannel) {
            _dataChannel->UnregisterObserver();
            _dataChannel = nullptr;
        }
        _dataChannelObserver.reset();

        _peerConnection = nullptr;
        _peerConnectionObserver.reset();
        _peerConnectionFactory = nullptr;
    }

    void start() {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        RTC_DCHECK(_threads->getMediaThread()->IsCurrent());

        if (_signalingProtocolVersion == SignalingProtocolVersion::V2) {
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
                }
            );
        }
        if (!_signalingConnection) {
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
        
        webrtc:: AudioProcessingBuilder builder;
        peerConnectionFactoryDependencies.audio_processing = builder.Create();
        
        peerConnectionFactoryDependencies.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
        peerConnectionFactoryDependencies.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();

        peerConnectionFactoryDependencies.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext, true);
        peerConnectionFactoryDependencies.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);
        
        webrtc::EnableMedia(peerConnectionFactoryDependencies);

        peerConnectionFactoryDependencies.event_log_factory = std::make_unique<webrtc::RtcEventLogFactory>(peerConnectionFactoryDependencies.task_queue_factory.get());

        _peerConnectionFactory = webrtc::CreateModularPeerConnectionFactory(std::move(peerConnectionFactoryDependencies));

        webrtc::PeerConnectionDependencies peerConnectionDependencies(nullptr);

        PeerConnectionDelegateAdapter::Parameters delegateParameters;
        delegateParameters.onRenegotiationNeeded = [weak, threads = _threads]() {
            threads->getMediaThread()->PostTask([weak]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                if (strong->_didBeginNegotiation) {
                    if (strong->_encryptionKey.isOutgoing || strong->_peerConnection->remote_description()) {
                        strong->sendLocalDescription();
                    }
                } else {
                    RTC_LOG(LS_INFO) << "onRenegotiationNeeded: not sending local description";
                }
            });
        };
        delegateParameters.onIceCandidate = [weak](const webrtc::IceCandidateInterface *iceCandidate) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->sendIceCandidate(iceCandidate);
        };
        delegateParameters.onSignalingChange = [weak](webrtc::PeerConnectionInterface::SignalingState state) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            /*switch (state) {
                case webrtc::PeerConnectionInterface::SignalingState::kStable: {
                    State mappedState = State::Established;
                    strong->_stateUpdated(mappedState);
                    break;
                }
                default: {
                    State mappedState = State::Reconnecting;
                    strong->_stateUpdated(mappedState);
                    break;
                }
            }*/
        };
        delegateParameters.onConnectionChange = [weak](webrtc::PeerConnectionInterface::IceConnectionState state) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            bool isConnected = false;
            bool isFailed = false;

            switch (state) {
                case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionConnected: {
                    isConnected = true;
                    break;
                }
                case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionCompleted: {
                    isConnected = true;
                    break;
                }
                case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionFailed: {
                    isFailed = true;
                    break;
                }
                default: {
                    break;
                }
            }

            if (strong->_isConnected != isConnected || strong->_isFailed != isFailed) {
                strong->_isConnected = isConnected;
                strong->_isFailed = isFailed;

                strong->onNetworkStateUpdated();
            }
        };
        delegateParameters.onDataChannel = [weak](webrtc::scoped_refptr<webrtc::DataChannelInterface> dataChannel) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (!strong->_dataChannel) {
                strong->attachDataChannel(dataChannel);
            } else {
                RTC_LOG(LS_WARNING) << "onDataChannel invoked, but data channel already exists";
            }
        };
        delegateParameters.onTransceiverAdded = [weak](webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (!transceiver->mid()) {
                return;
            }
            std::string mid = transceiver->mid().value();

            switch (transceiver->media_type()) {
                case cricket::MediaType::MEDIA_TYPE_VIDEO: {
                    if (strong->_incomingVideoTransceivers.find(mid) == strong->_incomingVideoTransceivers.end()) {
                        strong->_incomingVideoTransceivers.insert(std::make_pair(mid, transceiver));

                        strong->connectIncomingVideoSink(transceiver);
                    }
                    break;
                }
                default: {
                    break;
                }
            }
        };
        delegateParameters.onTransceiverRemoved = [weak](webrtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            std::string mid = receiver->track()->id();
            if (mid.empty()) {
                return;
            }

            const auto transceiver = strong->_incomingVideoTransceivers.find(mid);
            if (transceiver != strong->_incomingVideoTransceivers.end()) {
                strong->disconnectIncomingVideoSink();

                strong->_incomingVideoTransceivers.erase(transceiver);
            }
        };
        delegateParameters.onCandidatePairChangeEvent = [weak](const cricket::CandidatePairChangeEvent &event) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            InstanceNetworking::ConnectionDescription connectionDescription;

            connectionDescription.local = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.local);
            connectionDescription.remote = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.remote);

            if (!strong->_currentConnectionDescription || strong->_currentConnectionDescription.value() != connectionDescription) {
                strong->_currentConnectionDescription = std::move(connectionDescription);
                strong->onNetworkStateUpdated();
            }
        };
        _peerConnectionObserver = std::make_unique<PeerConnectionDelegateAdapter>(std::move(delegateParameters));

        peerConnectionDependencies.observer = _peerConnectionObserver.get();

        _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
        _socketFactory = std::make_unique<rtc::BasicPacketSocketFactory>(_threads->getNetworkThread()->socketserver());
        _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), _threads->getNetworkThread()->socketserver());
        _relayPortFactory = std::make_unique<ReflectorRelayPortFactory>(_rtcServers, false, 0, _threads->getNetworkThread()->socketserver());

        auto portAllocator = std::make_unique<cricket::BasicPortAllocator>(_networkManager.get(), _socketFactory.get(), nullptr, _relayPortFactory.get());
        peerConnectionDependencies.allocator = std::move(portAllocator);

        webrtc::PeerConnectionInterface::RTCConfiguration peerConnectionConfiguration;
        if (_enableP2P) {
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

        for (auto &server : _rtcServers) {
            if (server.isTcp) {
                continue;
            }

            rtc::SocketAddress address(server.host, server.port);
            if (!address.IsComplete()) {
                RTC_LOG(LS_ERROR) << "Invalid ICE server host: " << server.host;
                continue;
            }

            if (server.isTurn) {
                webrtc::PeerConnectionInterface::IceServer mappedServer;

                mappedServer.urls.push_back(
                    "turn:" + address.HostAsURIString() + ":" + std::to_string(server.port));
                mappedServer.username = server.login;
                mappedServer.password = server.password;

                peerConnectionConfiguration.servers.push_back(mappedServer);
            } else {
                webrtc::PeerConnectionInterface::IceServer mappedServer;

                mappedServer.urls.push_back(
                    "stun:" + address.HostAsURIString() + ":" + std::to_string(server.port));

                peerConnectionConfiguration.servers.push_back(mappedServer);
            }
        }

        auto peerConnectionOrError = _peerConnectionFactory->CreatePeerConnectionOrError(peerConnectionConfiguration, std::move(peerConnectionDependencies));
        if (peerConnectionOrError.ok()) {
            _peerConnection = peerConnectionOrError.value();
        }

        if (_peerConnection) {
            RTC_LOG(LS_INFO) << "Creating Data Channel";

            if (_encryptionKey.isOutgoing) {
                webrtc::DataChannelInit dataChannelInit;
                webrtc::RTCErrorOr<webrtc::scoped_refptr<webrtc::DataChannelInterface>> dataChannelOrError = _peerConnection->CreateDataChannelOrError("data", &dataChannelInit);
                if (dataChannelOrError.ok()) {
                    attachDataChannel(dataChannelOrError.value());
                }
            }

            webrtc::RtpTransceiverInit transceiverInit;
            transceiverInit.stream_ids = { "0" };

            cricket::AudioOptions audioSourceOptions;
            webrtc::scoped_refptr<webrtc::AudioSourceInterface> audioSource = _peerConnectionFactory->CreateAudioSource(audioSourceOptions);

            webrtc::scoped_refptr<webrtc::AudioTrackInterface> audioTrack = _peerConnectionFactory->CreateAudioTrack("0", audioSource.get());
            webrtc::RTCErrorOr<webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>> audioTransceiverOrError = _peerConnection->AddTransceiver(audioTrack, transceiverInit);
            if (audioTransceiverOrError.ok()) {
                _outgoingAudioTrack = audioTrack;
                _outgoingAudioTransceiver = audioTransceiverOrError.value();

                webrtc::RtpParameters parameters = _outgoingAudioTransceiver->sender()->GetParameters();
                if (parameters.encodings.empty()) {
                    parameters.encodings.push_back(webrtc::RtpEncodingParameters());
                }
                parameters.encodings[0].max_bitrate_bps = 32 * 1024;
                _outgoingAudioTransceiver->sender()->SetParameters(parameters);

                _outgoingAudioTrack->set_enabled(true);
            }
        }

        if (_videoCapture) {
            setVideoCapture(_videoCapture);
        }

        _signalingEncryptedConnection = std::make_unique<EncryptedConnection>(
            EncryptedConnection::Type::Signaling,
            _encryptionKey,
            [weak, threads = _threads](int delayMs, int cause) {
                if (delayMs == 0) {
                    threads->getMediaThread()->PostTask([weak, cause]() {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        strong->sendPendingSignalingServiceData(cause);
                    });
                } else {
                    threads->getMediaThread()->PostDelayedTask([weak, cause]() {
                        const auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        strong->sendPendingSignalingServiceData(cause);
                    }, webrtc::TimeDelta::Millis(delayMs));
                }
            }
        );

        beginSignaling();

        beginLogTimer(0);
    }

    void sendPendingSignalingServiceData(int cause) {
        commitSendSignalingMessage(_signalingEncryptedConnection->prepareForSendingService(cause));
    }

    void sendSignalingMessage(signaling::Message const &message) {
        auto data = message.serialize();
        sendRawSignalingMessage(data);
    }

    void sendRawSignalingMessage(std::vector<uint8_t> const &data) {
        RTC_LOG(LS_INFO) << "sendSignalingMessage: " << std::string(data.begin(), data.end());

        if (_signalingConnection && _signalingEncryptedConnection) {
            switch (_signalingProtocolVersion) {
                case SignalingProtocolVersion::V1: {
                    rtc::CopyOnWriteBuffer message;
                    message.AppendData(data.data(), data.size());

                    commitSendSignalingMessage(_signalingEncryptedConnection->prepareForSendingRawMessage(message, true));

                    break;
                }
                case SignalingProtocolVersion::V2: {
                    std::vector<uint8_t> packetData;
                    if (signalingProtocolSupportsCompression(_signalingProtocolVersion)) {
                        if (const auto compressedData = gzipData(data)) {
                            packetData = std::move(compressedData.value());
                        } else {
                            RTC_LOG(LS_ERROR) << "Could not gzip signaling message";
                        }
                    } else {
                        packetData = data;
                    }

                    if (const auto message = _signalingEncryptedConnection->encryptRawPacket(rtc::CopyOnWriteBuffer(packetData.data(), packetData.size()))) {
                        _signalingConnection->send(std::vector<uint8_t>(message.value().data(), message.value().data() + message.value().size()));
                    } else {
                        RTC_LOG(LS_ERROR) << "Could not encrypt signaling message";
                    }
                    break;
                }
                default: {
                    RTC_DCHECK_NOTREACHED();

                    break;
                }
            }
        } else {
            RTC_LOG(LS_ERROR) << "sendSignalingMessage encryption not available";
        }
    }

    void commitSendSignalingMessage(absl::optional<EncryptedConnection::EncryptedPacket> packet) {
        if (!packet) {
            return;
        }

        if (_signalingConnection) {
            _signalingConnection->send(packet.value().bytes);
        }
    }

    void beginLogTimer(int delayMs) {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());
        _threads->getMediaThread()->PostDelayedTask([weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->writeStateLogRecords();

            strong->beginLogTimer(1000);
        }, webrtc::TimeDelta::Millis(delayMs));
    }

    void writeStateLogRecords() {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());
        auto call = ((webrtc::PeerConnectionProxyWithInternal<webrtc::PeerConnection> *)_peerConnection.get())->internal()->call_ptr();
        if (!call) {
            return;
        }

        _threads->getWorkerThread()->PostTask([weak, call]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            auto stats = call->GetStats();
            float sendBitrateKbps = ((float)stats.send_bandwidth_bps / 1024.0f);

            strong->_threads->getMediaThread()->PostTask([weak, sendBitrateKbps]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                float bitrateNorm = 16.0f;
                if (strong->_outgoingVideoTransceiver) {
                    bitrateNorm = 600.0f;
                }

                float signalBarsNorm = 4.0f;
                float adjustedQuality = sendBitrateKbps / bitrateNorm;
                adjustedQuality = fmaxf(0.0f, adjustedQuality);
                adjustedQuality = fminf(1.0f, adjustedQuality);
                if (strong->_signalBarsUpdated) {
                    strong->_signalBarsUpdated((int)(adjustedQuality * signalBarsNorm));
                }

                NetworkBitrateLogRecord networkBitrateLogRecord;
                networkBitrateLogRecord.bitrate = (int32_t)sendBitrateKbps;

                strong->_networkBitrateLogRecords.emplace_back(rtc::TimeMillis(), std::move(networkBitrateLogRecord));
            });
        });
    }

    void sendLocalDescription() {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

        _isMakingOffer = true;

        webrtc::scoped_refptr<webrtc::SetLocalDescriptionObserverInterface> observer(new rtc::RefCountedObject<SetSessionDescriptionObserver>([threads = _threads, weak](webrtc::RTCError error) {
            threads->getMediaThread()->PostTask([weak]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                strong->doSendLocalDescription();

                strong->_isMakingOffer = false;

                strong->maybeCommitPendingIceCandidates();
            });
        }));
        RTC_LOG(LS_INFO) << "Calling SetLocalDescription";
        _peerConnection->SetLocalDescription(observer);
    }

    void sendIceCandidate(const webrtc::IceCandidateInterface *iceCandidate) {
        std::string sdp;
        iceCandidate->ToString(&sdp);

        json11::Json::object jsonCandidate;
        jsonCandidate.insert(std::make_pair("@type", json11::Json("candidate")));
        jsonCandidate.insert(std::make_pair("sdp", json11::Json(sdp)));
        jsonCandidate.insert(std::make_pair("mid", json11::Json(iceCandidate->sdp_mid())));
        jsonCandidate.insert(std::make_pair("mline", json11::Json(iceCandidate->sdp_mline_index())));

        auto jsonData = json11::Json(std::move(jsonCandidate));
        auto jsonResult = jsonData.dump();
        sendRawSignalingMessage(std::vector<uint8_t>(jsonResult.begin(), jsonResult.end()));
    }

    void doSendLocalDescription() {
        auto localDescription = _peerConnection->local_description();
        if (localDescription) {
            std::string sdp;
            localDescription->ToString(&sdp);
            std::string type = localDescription->type();

            json11::Json::object jsonDescription;
            jsonDescription.insert(std::make_pair("@type", json11::Json(type)));
            jsonDescription.insert(std::make_pair("sdp", json11::Json(sdp)));

            auto jsonData = json11::Json(std::move(jsonDescription));
            auto jsonResult = jsonData.dump();
            sendRawSignalingMessage(std::vector<uint8_t>(jsonResult.begin(), jsonResult.end()));
        }
    }

    void beginSignaling() {
        _didBeginNegotiation = true;

        if (_encryptionKey.isOutgoing) {
            sendLocalDescription();
        }
    }

    void receiveSignalingData(const std::vector<uint8_t> &data) {
        if (_signalingConnection) {
            _signalingConnection->receiveExternal(data);
        }
    }

    void onSignalingData(const std::vector<uint8_t> &data) {
        if (_signalingEncryptedConnection) {
            switch (_signalingProtocolVersion) {
                case SignalingProtocolVersion::V1: {
                    if (const auto packet = _signalingEncryptedConnection->handleIncomingRawPacket((const char *)data.data(), data.size())) {
                        processSignalingMessage(packet.value().main.message);

                        for (const auto &additional : packet.value().additional) {
                            processSignalingMessage(additional.message);
                        }
                    }

                    break;
                }
                case SignalingProtocolVersion::V2: {
                    if (const auto message = _signalingEncryptedConnection->decryptRawPacket(rtc::CopyOnWriteBuffer(data.data(), data.size()))) {
                        processSignalingMessage(message.value());
                    } else {
                        RTC_LOG(LS_ERROR) << "receiveSignalingData could not decrypt signaling data";
                    }

                    break;
                }
                default: {
                    RTC_DCHECK_NOTREACHED();

                    break;
                }
            }
        } else {
            RTC_LOG(LS_ERROR) << "receiveSignalingData encryption not available";
        }
    }

    void processSignalingMessage(rtc::CopyOnWriteBuffer const &data) {
        std::vector<uint8_t> decryptedData = std::vector<uint8_t>(data.data(), data.data() + data.size());

        if (isGzip(decryptedData)) {
            if (const auto decompressedData = gunzipData(decryptedData, 2 * 1024 * 1024)) {
                processSignalingData(decompressedData.value());
            } else {
                RTC_LOG(LS_ERROR) << "receiveSignalingData could not decompress gzipped data";
            }
        } else {
            processSignalingData(decryptedData);
        }
    }

    void processSignalingData(const std::vector<uint8_t> &data) {
        RTC_LOG(LS_INFO) << "processSignalingData: " << std::string(data.begin(), data.end());

        std::string parsingError;
        auto json = json11::Json::parse(std::string(data.begin(), data.end()), parsingError);
        if (json.type() != json11::Json::OBJECT) {
            RTC_LOG(LS_ERROR) << "Signaling: message must be an object";
            return;
        }

        const auto jsonType = json.object_items().find("@type");
        if (jsonType == json.object_items().end()) {
            RTC_LOG(LS_ERROR) << "Signaling: @type is missing";
            return;
        }
        std::string type = jsonType->second.string_value();

        if (type == "offer" || type == "answer") {
            const auto jsonSdp = json.object_items().find("sdp");
            if (jsonSdp == json.object_items().end()) {
                RTC_LOG(LS_ERROR) << "Signaling: sdp is missing";
                return;
            }
            std::string sdp = jsonSdp->second.string_value();
            handleRemoteSdp(type, sdp);
        } else if (type == "candidate") {
            auto jsonMid = json.object_items().find("mid");
            if (jsonMid == json.object_items().end()) {
                return;
            }

            auto jsonMLineIndex = json.object_items().find("mline");
            if (jsonMLineIndex == json.object_items().end()) {
                return;
            }

            auto jsonSdp = json.object_items().find("sdp");
            if (jsonSdp == json.object_items().end()) {
                return;
            }

            webrtc::SdpParseError parseError;
            webrtc::IceCandidateInterface *iceCandidate = webrtc::CreateIceCandidate(jsonMid->second.string_value(), jsonMLineIndex->second.int_value(), jsonSdp->second.string_value(), &parseError);
            if (iceCandidate) {
                std::unique_ptr<webrtc::IceCandidateInterface> candidarePtr;
                candidarePtr.reset(iceCandidate);

                _pendingIceCandidates.push_back(std::move(candidarePtr));
                maybeCommitPendingIceCandidates();
            }
        } else {
            const auto message = signaling::Message::parse(data);
            if (!message) {
                return;
            }
            const auto messageData = &message->data;

            if (const auto mediaState = absl::get_if<signaling::MediaStateMessage>(messageData)) {
                AudioState mappedAudioState;
                if (mediaState->isMuted) {
                    mappedAudioState = AudioState::Muted;
                } else {
                    mappedAudioState = AudioState::Active;
                }

                VideoState mappedVideoState;
                switch (mediaState->videoState) {
                    case signaling::MediaStateMessage::VideoState::Inactive: {
                        mappedVideoState = VideoState::Inactive;
                        break;
                    }
                    case signaling::MediaStateMessage::VideoState::Suspended: {
                        mappedVideoState = VideoState::Paused;
                        break;
                    }
                    case signaling::MediaStateMessage::VideoState::Active: {
                        mappedVideoState = VideoState::Active;
                        break;
                    }
                    default: {
                        RTC_FATAL() << "Unknown videoState";
                        break;
                    }
                }

                VideoState mappedScreencastState;
                switch (mediaState->screencastState) {
                    case signaling::MediaStateMessage::VideoState::Inactive: {
                        mappedScreencastState = VideoState::Inactive;
                        break;
                    }
                    case signaling::MediaStateMessage::VideoState::Suspended: {
                        mappedScreencastState = VideoState::Paused;
                        break;
                    }
                    case signaling::MediaStateMessage::VideoState::Active: {
                        mappedScreencastState = VideoState::Active;
                        break;
                    }
                    default: {
                        RTC_FATAL() << "Unknown videoState";
                        break;
                    }
                }

                VideoState effectiveVideoState = mappedVideoState;
                if (mappedScreencastState == VideoState::Active || mappedScreencastState == VideoState::Paused) {
                    effectiveVideoState = mappedScreencastState;
                }

                if (_remoteMediaStateUpdated) {
                    _remoteMediaStateUpdated(mappedAudioState, effectiveVideoState);
                }

                if (_remoteBatteryLevelIsLowUpdated) {
                    _remoteBatteryLevelIsLowUpdated(mediaState->isBatteryLow);
                }
            }
        }
    }

    void handleRemoteSdp(std::string const &type, std::string const &sdp) {
        webrtc::SdpParseError sdpParseError;
        std::unique_ptr<webrtc::SessionDescriptionInterface> remoteDescription(webrtc::CreateSessionDescription(type, sdp, &sdpParseError));
        if (!remoteDescription) {
            RTC_LOG(LS_ERROR) << "Failed to parse remote SDP";
            return;
        }

        bool isReadyForOffer = !_isMakingOffer && (_peerConnection->signaling_state() == webrtc::PeerConnectionInterface::SignalingState::kStable || _isSettingRemoteAnswerPending);
        bool isOfferCollision = (type == "offer") && !isReadyForOffer;
        bool ignoreOffer = !_encryptionKey.isOutgoing && isOfferCollision;
        if (ignoreOffer) {
            RTC_LOG(LS_INFO) << "Ingoring remote sdp";
            return;
        }

        _isSettingRemoteAnswerPending = type == "answer";

        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());
        webrtc::scoped_refptr<webrtc::SetRemoteDescriptionObserverInterface> observer(new rtc::RefCountedObject<SetSessionDescriptionObserver>([threads = _threads, weak, type](webrtc::RTCError error) {
            threads->getMediaThread()->PostTask([weak, type]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                strong->_isSettingRemoteAnswerPending = false;

                strong->maybeCommitPendingIceCandidates();

                if (type == "offer") {
                    strong->sendLocalDescription();
                }
            });
        }));
        RTC_LOG(LS_INFO) << "Calling SetRemoteDescription";
        _peerConnection->SetRemoteDescription(std::move(remoteDescription), observer);
    }

    void maybeCommitPendingIceCandidates() {
        if (_pendingIceCandidates.size() == 0) {
            return;
        }

        if (_peerConnection->local_description() && _peerConnection->remote_description()) {
            for (const auto &candidate : _pendingIceCandidates) {
                if (candidate) {
                    _peerConnection->AddIceCandidate(candidate.get());
                }
            }

            _pendingIceCandidates.clear();
        }
    }

    void onNetworkStateUpdated() {
        NetworkStateLogRecord record;
        record.isConnected = _isConnected;
        record.connection = _currentConnectionDescription;
        record.isFailed = _isFailed;

        if (!_currentNetworkStateLogRecord || !(_currentNetworkStateLogRecord.value() == record)) {
            _currentNetworkStateLogRecord = record;
            _networkStateLogRecords.emplace_back(rtc::TimeMillis(), std::move(record));
        }

        State mappedState;
        if (_isFailed) {
            mappedState = State::Failed;
        } else if (_isConnected) {
            mappedState = State::Established;
        } else {
            mappedState = State::Reconnecting;
        }
        _stateUpdated(mappedState);
    }

    void attachDataChannel(webrtc::scoped_refptr<webrtc::DataChannelInterface> dataChannel) {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

        DataChannelObserverImpl::Parameters dataChannelObserverParams;
        dataChannelObserverParams.onStateChange = [threads = _threads, weak]() {
            threads->getMediaThread()->PostTask([weak]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                strong->onDataChannelStateUpdated();
            });
        };
        dataChannelObserverParams.onMessage = [threads = _threads, weak](webrtc::DataBuffer const &buffer) {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }

            std::string message(buffer.data.data(), buffer.data.data() + buffer.data.size());

            if (!buffer.binary) {
                RTC_LOG(LS_INFO) << "dataChannelMessage received: " << message;
                std::vector<uint8_t> data(message.begin(), message.end());
                strong->processSignalingData(data);
            } else {
                RTC_LOG(LS_INFO) << "dataChannelMessage rejecting binary message";
            }
        };
        _dataChannelObserver = std::make_unique<DataChannelObserverImpl>(std::move(dataChannelObserverParams));
        _dataChannel = dataChannel;

        onDataChannelStateUpdated();

        _dataChannel->RegisterObserver(_dataChannelObserver.get());
    }

    void onDataChannelStateUpdated() {
        if (_dataChannel) {
            switch (_dataChannel->state()) {
                case webrtc::DataChannelInterface::DataState::kOpen: {
                    if (!_isDataChannelOpen) {
                        _isDataChannelOpen = true;
                        sendMediaState();
                    }
                    break;
                }
                default: {
                    _isDataChannelOpen = false;
                    break;
                }
            }
        }
    }

    void sendDataChannelMessage(signaling::Message const &message) {
        if (!_isDataChannelOpen) {
            RTC_LOG(LS_ERROR) << "sendDataChannelMessage called, but data channel is not open";
            return;
        }
        auto data = message.serialize();
        std::string stringData(data.begin(), data.end());
        RTC_LOG(LS_INFO) << "sendDataChannelMessage: " << stringData;
        if (_dataChannel) {
            _dataChannel->Send(webrtc::DataBuffer(stringData));
        }
    }

    void onDataChannelMessage(std::string const &message) {
        RTC_LOG(LS_INFO) << "dataChannelMessage received: " << message;
        std::vector<uint8_t> data(message.begin(), message.end());
        processSignalingData(data);
    }

    void sendMediaState() {
        if (!_isDataChannelOpen) {
            return;
        }
        signaling::Message message;
        signaling::MediaStateMessage data;
        data.isMuted = _isMicrophoneMuted;
        data.isBatteryLow = _isBatteryLow;
        if (_outgoingVideoTransceiver) {
            if (_videoCapture) {
                data.videoState = signaling::MediaStateMessage::VideoState::Active;
            } else {
                data.videoState = signaling::MediaStateMessage::VideoState::Inactive;
            }
        } else {
            data.videoState = signaling::MediaStateMessage::VideoState::Inactive;
            data.videoRotation = signaling::MediaStateMessage::VideoRotation::Rotation0;
        }
        message.data = std::move(data);
        sendDataChannelMessage(message);
    }

    void sendCandidate(const cricket::Candidate &candidate) {
        cricket::Candidate patchedCandidate = candidate;
        patchedCandidate.set_component(1);

        signaling::CandidatesMessage data;

        signaling::IceCandidate serializedCandidate;

        webrtc::JsepIceCandidate iceCandidate{ std::string(), 0 };
        iceCandidate.SetCandidate(patchedCandidate);
        std::string serialized;
        const auto success = iceCandidate.ToString(&serialized);
        assert(success);
        (void)success;

        serializedCandidate.sdpString = serialized;

        data.iceCandidates.push_back(std::move(serializedCandidate));

        signaling::Message message;
        message.data = std::move(data);
        sendSignalingMessage(message);
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
        _isPerformingConfiguration = true;

        if (_outgoingVideoTransceiver) {
            _peerConnection->RemoveTrackOrError(_outgoingVideoTransceiver->sender());
        }
        if (_outgoingVideoTrack) {
            _outgoingVideoTrack = nullptr;
        }
        _outgoingVideoTransceiver = nullptr;

        auto videoCaptureImpl = GetVideoCaptureAssumingSameThread(videoCapture.get());
        if (videoCaptureImpl) {
            if (videoCaptureImpl->isScreenCapture()) {
            } else {
                _videoCapture = videoCapture;

                auto videoTrack = _peerConnectionFactory->CreateVideoTrack(videoCaptureImpl->source(), "1");
                if (videoTrack) {
                    webrtc::RtpTransceiverInit transceiverInit;
                    transceiverInit.stream_ids = { "0" };

                    webrtc::RTCErrorOr<webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>> videoTransceiverOrError = _peerConnection->AddTransceiver(videoTrack, transceiverInit);
                    if (videoTransceiverOrError.ok()) {
                        _outgoingVideoTrack = videoTrack;
                        _outgoingVideoTransceiver = videoTransceiverOrError.value();

                        auto currentCapabilities = _peerConnectionFactory->GetRtpSenderCapabilities(cricket::MediaType::MEDIA_TYPE_VIDEO);

                        std::vector<std::string> codecPreferences = {
                            #ifndef WEBRTC_DISABLE_H265
                            cricket::kH265CodecName,
                            #endif
                            cricket::kH264CodecName
                        };

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

                        _outgoingVideoTransceiver->SetCodecPreferences(codecCapabilities);

                        webrtc::RtpParameters parameters = _outgoingVideoTransceiver->sender()->GetParameters();
                        if (parameters.encodings.empty()) {
                            parameters.encodings.push_back(webrtc::RtpEncodingParameters());
                        }
                        parameters.encodings[0].max_bitrate_bps = 1200 * 1024;
                        _outgoingVideoTransceiver->sender()->SetParameters(parameters);

                        _outgoingVideoTrack->set_enabled(true);
                    }
                }
            }
        }

        _isPerformingConfiguration = false;

        if (_didBeginNegotiation) {
            sendMediaState();
            sendLocalDescription();
        }
    }

    void setRequestedVideoAspect(float aspect) {
    }

    void setNetworkType(NetworkType networkType) {
    }

    void setMuteMicrophone(bool muteMicrophone) {
        if (_isMicrophoneMuted != muteMicrophone) {
            _isMicrophoneMuted = muteMicrophone;

            if (_outgoingAudioTrack) {
                _outgoingAudioTrack->set_enabled(!_isMicrophoneMuted);
            }

            sendMediaState();
        }
    }

    void connectIncomingVideoSink(webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
        if (_currentStrongSink) {
            webrtc::VideoTrackInterface *videoTrack = (webrtc::VideoTrackInterface *)transceiver->receiver()->track().get();
            videoTrack->AddOrUpdateSink(_currentStrongSink.get(), rtc::VideoSinkWants());
        }
    }

    void disconnectIncomingVideoSink() {
    }

    void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        _currentStrongSink = sink.lock();

        if (_currentStrongSink) {
            if (!_incomingVideoTransceivers.empty()) {
                connectIncomingVideoSink(_incomingVideoTransceivers.begin()->second);
            }
        }

        /*if (_incomingVideoChannel) {
            _incomingVideoChannel->addSink(sink);
        }
        if (_incomingScreencastChannel) {
            _incomingScreencastChannel->addSink(sink);
        }*/
    }

    void setAudioInputDevice(std::string id) {
        SetAudioInputDeviceById(_audioDeviceModule.get(), id);
    }

    void setAudioOutputDevice(std::string id) {
        SetAudioOutputDeviceById(_audioDeviceModule.get(), id);
    }

    void setIsLowBatteryLevel(bool isLowBatteryLevel) {
        if (_isBatteryLow != isLowBatteryLevel) {
            _isBatteryLow = isLowBatteryLevel;
            sendMediaState();
        }
    }

    void stop(std::function<void(FinalState)> completion) {
        _peerConnection->Close();

        FinalState finalState;

        json11::Json::object statsLog;

        statsLog.insert(std::make_pair("v", std::move(3)));

        for (int i = (int)_networkStateLogRecords.size() - 1; i >= 1; i--) {
            // coalesce events within 5ms
            if (_networkStateLogRecords[i].timestamp - _networkStateLogRecords[i - 1].timestamp < 5) {
                _networkStateLogRecords.erase(_networkStateLogRecords.begin() + i - 1);
            }
        }

        json11::Json::array jsonNetworkStateLogRecords;
        int64_t baseTimestamp = 0;
        for (const auto &record : _networkStateLogRecords) {
            json11::Json::object jsonRecord;

            if (baseTimestamp == 0) {
                baseTimestamp = record.timestamp;
            }
            jsonRecord.insert(std::make_pair("t", json11::Json(std::to_string(record.timestamp - baseTimestamp))));
            jsonRecord.insert(std::make_pair("c", json11::Json(record.record.isConnected ? 1 : 0)));
            if (record.record.route) {
                jsonRecord.insert(std::make_pair("local", json11::Json(record.record.route->localDescription)));
                jsonRecord.insert(std::make_pair("remote", json11::Json(record.record.route->remoteDescription)));
            }
            if (record.record.connection) {
                json11::Json::object jsonConnection;

                auto serializeCandidate = [](InstanceNetworking::ConnectionDescription::CandidateDescription const &candidate) -> json11::Json::object {
                    json11::Json::object jsonCandidate;

                    jsonCandidate.insert(std::make_pair("type", json11::Json(candidate.type)));
                    jsonCandidate.insert(std::make_pair("protocol", json11::Json(candidate.protocol)));
                    jsonCandidate.insert(std::make_pair("address", json11::Json(candidate.address)));

                    return jsonCandidate;
                };

                jsonConnection.insert(std::make_pair("local", serializeCandidate(record.record.connection->local)));
                jsonConnection.insert(std::make_pair("remote", serializeCandidate(record.record.connection->remote)));

                jsonRecord.insert(std::make_pair("network", std::move(jsonConnection)));
            }
            if (record.record.isFailed) {
                jsonRecord.insert(std::make_pair("failed", json11::Json(1)));
            }

            jsonNetworkStateLogRecords.push_back(std::move(jsonRecord));
        }
        statsLog.insert(std::make_pair("network", std::move(jsonNetworkStateLogRecords)));

        json11::Json::array jsonNetworkBitrateLogRecords;
        for (const auto &record : _networkBitrateLogRecords) {
            json11::Json::object jsonRecord;

            jsonRecord.insert(std::make_pair("b", json11::Json(record.record.bitrate)));

            jsonNetworkBitrateLogRecords.push_back(std::move(jsonRecord));
        }
        statsLog.insert(std::make_pair("bitrate", std::move(jsonNetworkBitrateLogRecords)));

        auto jsonStatsLog = json11::Json(std::move(statsLog));

        if (!_statsLogPath.data.empty()) {
            std::ofstream file;
            file.open(_statsLogPath.data);

            file << jsonStatsLog.dump();

            file.close();
        }

        completion(finalState);
    }

    /*void adjustBitratePreferences(bool resetStartBitrate) {
        if (_outgoingAudioChannel) {
            _outgoingAudioChannel->setMaxBitrate(32 * 1024);
        }
        if (_outgoingVideoChannel) {
            _outgoingVideoChannel->setMaxBitrate(1000 * 1024);
        }
    }*/

private:
    webrtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule() {
        const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
#ifdef WEBRTC_IOS
            return rtc::make_ref_counted<webrtc::tgcalls_ios_adm::AudioDeviceModuleIOS>(false, false, false, 1);
#else
            return webrtc::AudioDeviceModule::Create(
                layer,
                _taskQueueFactory.get());
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

private:
    SignalingProtocolVersion _signalingProtocolVersion;
    std::shared_ptr<Threads> _threads;
    std::vector<RtcServer> _rtcServers;
    std::unique_ptr<Proxy> _proxy;
    bool _enableP2P = false;
    EncryptionKey _encryptionKey;
    std::function<void(State)> _stateUpdated;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(float, float)> _audioLevelsUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
    std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<webrtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;
    std::function<webrtc::scoped_refptr<WrappedAudioDeviceModule>(webrtc::TaskQueueFactory*)> _createWrappedAudioDeviceModule;
    FilePath _statsLogPath;

    std::unique_ptr<SignalingConnection> _signalingConnection;
    std::unique_ptr<EncryptedConnection> _signalingEncryptedConnection;

    bool _isConnected = false;
    bool _isFailed = false;
    absl::optional<InstanceNetworking::ConnectionDescription> _currentConnectionDescription;

    absl::optional<NetworkStateLogRecord> _currentNetworkStateLogRecord;
    std::vector<StateLogRecord<NetworkStateLogRecord>> _networkStateLogRecords;
    std::vector<StateLogRecord<NetworkBitrateLogRecord>> _networkBitrateLogRecords;

    bool _didBeginNegotiation = false;
    bool _isMakingOffer = false;
    bool _isSettingRemoteAnswerPending = false;
    bool _isPerformingConfiguration = false;

    webrtc::scoped_refptr<webrtc::AudioTrackInterface> _outgoingAudioTrack;
    webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> _outgoingAudioTransceiver;
    bool _isMicrophoneMuted = false;

    webrtc::scoped_refptr<webrtc::VideoTrackInterface> _outgoingVideoTrack;
    webrtc::scoped_refptr<webrtc::RtpTransceiverInterface> _outgoingVideoTransceiver;

    std::map<std::string, webrtc::scoped_refptr<webrtc::RtpTransceiverInterface>> _incomingVideoTransceivers;

    std::vector<std::unique_ptr<webrtc::IceCandidateInterface>> _pendingIceCandidates;

    std::unique_ptr<DataChannelObserverImpl> _dataChannelObserver;
    webrtc::scoped_refptr<webrtc::DataChannelInterface> _dataChannel;
    bool _isDataChannelOpen = false;

    std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;

    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
    std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
    std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
    std::unique_ptr<cricket::RelayPortFactoryInterface> _relayPortFactory;

    webrtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> _peerConnectionFactory;
    std::unique_ptr<PeerConnectionDelegateAdapter> _peerConnectionObserver;
    webrtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;

    webrtc::LocalAudioSinkAdapter _audioSource;

    webrtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;

    bool _isBatteryLow = false;

    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentStrongSink;

    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    std::shared_ptr<PlatformContext> _platformContext;
};

InstanceV2ReferenceImpl::InstanceV2ReferenceImpl(Descriptor &&descriptor) {
    if (descriptor.config.logPath.data.size() != 0) {
        _logSink = std::make_unique<LogSinkImpl>(descriptor.config.logPath);
    }
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(false);
    if (_logSink) {
        rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
    }

    _threads = StaticThreads::getThreads();
    _internal.reset(new ThreadLocalObject<InstanceV2ReferenceImplInternal>(_threads->getMediaThread(), [descriptor = std::move(descriptor), threads = _threads]() mutable {
        return std::make_shared<InstanceV2ReferenceImplInternal>(std::move(descriptor), threads);
    }));
    _internal->perform([](InstanceV2ReferenceImplInternal *internal) {
        internal->start();
    });
}

InstanceV2ReferenceImpl::~InstanceV2ReferenceImpl() {
    rtc::LogMessage::RemoveLogToStream(_logSink.get());
}

void InstanceV2ReferenceImpl::receiveSignalingData(const std::vector<uint8_t> &data) {
    _internal->perform([data](InstanceV2ReferenceImplInternal *internal) {
        internal->receiveSignalingData(data);
    });
}

void InstanceV2ReferenceImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _internal->perform([videoCapture](InstanceV2ReferenceImplInternal *internal) {
        internal->setVideoCapture(videoCapture);
    });
}

void InstanceV2ReferenceImpl::setRequestedVideoAspect(float aspect) {
    _internal->perform([aspect](InstanceV2ReferenceImplInternal *internal) {
        internal->setRequestedVideoAspect(aspect);
    });
}

void InstanceV2ReferenceImpl::setNetworkType(NetworkType networkType) {
    _internal->perform([networkType](InstanceV2ReferenceImplInternal *internal) {
        internal->setNetworkType(networkType);
    });
}

void InstanceV2ReferenceImpl::setMuteMicrophone(bool muteMicrophone) {
    _internal->perform([muteMicrophone](InstanceV2ReferenceImplInternal *internal) {
        internal->setMuteMicrophone(muteMicrophone);
    });
}

void InstanceV2ReferenceImpl::setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform([sink](InstanceV2ReferenceImplInternal *internal) {
        internal->setIncomingVideoOutput(sink);
    });
}

void InstanceV2ReferenceImpl::setAudioInputDevice(std::string id) {
    _internal->perform([id](InstanceV2ReferenceImplInternal *internal) {
        internal->setAudioInputDevice(id);
    });
}

void InstanceV2ReferenceImpl::setAudioOutputDevice(std::string id) {
    _internal->perform([id](InstanceV2ReferenceImplInternal *internal) {
        internal->setAudioOutputDevice(id);
    });
}

void InstanceV2ReferenceImpl::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _internal->perform([isLowBatteryLevel](InstanceV2ReferenceImplInternal *internal) {
        internal->setIsLowBatteryLevel(isLowBatteryLevel);
    });
}

void InstanceV2ReferenceImpl::setInputVolume(float level) {
}

void InstanceV2ReferenceImpl::setOutputVolume(float level) {
}

void InstanceV2ReferenceImpl::setAudioOutputDuckingEnabled(bool enabled) {
}

void InstanceV2ReferenceImpl::setAudioOutputGainControlEnabled(bool enabled) {
}

void InstanceV2ReferenceImpl::setEchoCancellationStrength(int strength) {
}

std::vector<std::string> InstanceV2ReferenceImpl::GetVersions() {
    std::vector<std::string> result;
    result.push_back("10.0.0");
    result.push_back("11.0.0");
    return result;
}

int InstanceV2ReferenceImpl::GetConnectionMaxLayer() {
    return 92;
}

std::string InstanceV2ReferenceImpl::getLastError() {
    return "";
}

std::string InstanceV2ReferenceImpl::getDebugInfo() {
    return "";
}

int64_t InstanceV2ReferenceImpl::getPreferredRelayId() {
    return 0;
}

TrafficStats InstanceV2ReferenceImpl::getTrafficStats() {
    return {};
}

PersistentState InstanceV2ReferenceImpl::getPersistentState() {
    return {};
}

void InstanceV2ReferenceImpl::stop(std::function<void(FinalState)> completion) {
    std::string debugLog;
    if (_logSink) {
        debugLog = _logSink->result();
    }
    _internal->perform([completion, debugLog = std::move(debugLog)](InstanceV2ReferenceImplInternal *internal) mutable {
        internal->stop([completion, debugLog = std::move(debugLog)](FinalState finalState) mutable {
            finalState.debugLog = debugLog;
            completion(finalState);
        });
    });
}

template <>
bool Register<InstanceV2ReferenceImpl>() {
    return Meta::RegisterOne<InstanceV2ReferenceImpl>();
}

} // namespace tgcalls
