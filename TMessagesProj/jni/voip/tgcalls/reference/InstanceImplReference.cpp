#include "InstanceImplReference.h"

#include <memory>
#include "api/scoped_refptr.h"
#include "rtc_base/thread.h"
#include "rtc_base/logging.h"
#include "api/peer_connection_interface.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "sdk/media_constraints.h"
#include "api/peer_connection_interface.h"
#include "api/video_track_source_proxy.h"
#include "system_wrappers/include/field_trial.h"
#include "api/stats/rtcstats_objects.h"

#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "StaticThreads.h"

namespace tgcalls {
namespace {

VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

class PeerConnectionObserverImpl : public webrtc::PeerConnectionObserver {
private:
    std::function<void(std::string, int, std::string)> _discoveredIceCandidate;
    std::function<void(bool)> _connectionStateChanged;
    std::function<void(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver)> _onTrack;

public:
    PeerConnectionObserverImpl(
        std::function<void(std::string, int, std::string)> discoveredIceCandidate,
        std::function<void(bool)> connectionStateChanged,
        std::function<void(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver)> onTrack
    ) :
    _discoveredIceCandidate(discoveredIceCandidate),
    _connectionStateChanged(connectionStateChanged),
    _onTrack(onTrack) {
    }

    virtual void OnSignalingChange(webrtc::PeerConnectionInterface::SignalingState new_state) {
        bool isConnected = false;
        if (new_state == webrtc::PeerConnectionInterface::SignalingState::kStable) {
            isConnected = true;
        }
        _connectionStateChanged(isConnected);
    }

    virtual void OnAddStream(rtc::scoped_refptr<webrtc::MediaStreamInterface> stream) {
    }

    virtual void OnRemoveStream(rtc::scoped_refptr<webrtc::MediaStreamInterface> stream) {
    }

    virtual void OnDataChannel(rtc::scoped_refptr<webrtc::DataChannelInterface> data_channel) {
    }

    virtual void OnRenegotiationNeeded() {
    }

    virtual void OnIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) {
    }

    virtual void OnStandardizedIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) {
    }

    virtual void OnConnectionChange(webrtc::PeerConnectionInterface::PeerConnectionState new_state) {
    }

    virtual void OnIceGatheringChange(webrtc::PeerConnectionInterface::IceGatheringState new_state) {
    }

    virtual void OnIceCandidate(const webrtc::IceCandidateInterface* candidate) {
        std::string sdp;
        candidate->ToString(&sdp);
        _discoveredIceCandidate(sdp, candidate->sdp_mline_index(), candidate->sdp_mid());
    }

    virtual void OnIceCandidateError(const std::string& host_candidate, const std::string& url, int error_code, const std::string& error_text) {
    }

    virtual void OnIceCandidateError(const std::string& address,
                                     int port,
                                     const std::string& url,
                                     int error_code,
                                     const std::string& error_text) {
    }

    virtual void OnIceCandidatesRemoved(const std::vector<cricket::Candidate>& candidates) {
    }

    virtual void OnIceConnectionReceivingChange(bool receiving) {
    }

    virtual void OnIceSelectedCandidatePairChanged(const cricket::CandidatePairChangeEvent& event) {
    }

    virtual void OnAddTrack(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver, const std::vector<rtc::scoped_refptr<webrtc::MediaStreamInterface>>& streams) {
    }

    virtual void OnTrack(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
        _onTrack(transceiver);
    }

    virtual void OnRemoveTrack(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
    }

    virtual void OnInterestingUsage(int usage_pattern) {
    }
};

class RTCStatsCollectorCallbackImpl : public webrtc::RTCStatsCollectorCallback {
public:
    RTCStatsCollectorCallbackImpl(std::function<void(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &)> completion) :
    _completion(completion) {
    }

    virtual void OnStatsDelivered(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &report) override {
        _completion(report);
    }

private:
    std::function<void(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &)> _completion;
};

class CreateSessionDescriptionObserverImpl : public webrtc::CreateSessionDescriptionObserver {
private:
    std::function<void(std::string, std::string)> _completion;

public:
    CreateSessionDescriptionObserverImpl(std::function<void(std::string, std::string)> completion) :
    _completion(completion) {
    }

    virtual void OnSuccess(webrtc::SessionDescriptionInterface* desc) override {
        if (desc) {
            std::string sdp;
            desc->ToString(&sdp);

            _completion(sdp, desc->type());
        }
    }

    virtual void OnFailure(webrtc::RTCError error) override {
    }
};

class SetSessionDescriptionObserverImpl : public webrtc::SetSessionDescriptionObserver {
private:
    std::function<void()> _completion;

public:
    SetSessionDescriptionObserverImpl(std::function<void()> completion) :
    _completion(completion) {
    }

    virtual void OnSuccess() override {
        _completion();
    }

    virtual void OnFailure(webrtc::RTCError error) override {
    }
};

struct StatsData {
    int32_t packetsReceived = 0;
    int32_t packetsLost = 0;
};

struct IceCandidateData {
    std::string sdpMid;
    int mid;
    std::string sdp;

    IceCandidateData(std::string _sdpMid, int _mid, std::string _sdp) :
    sdpMid(_sdpMid),
    mid(_mid),
    sdp(_sdp) {
    }
};

} //namespace

class InstanceImplReferenceInternal final : public std::enable_shared_from_this<InstanceImplReferenceInternal> {
public:
    InstanceImplReferenceInternal(
        const Descriptor &descriptor
    ) :
    _encryptionKey(descriptor.encryptionKey),
    _rtcServers(descriptor.rtcServers),
    _enableP2P(descriptor.config.enableP2P),
    _stateUpdated(descriptor.stateUpdated),
    _signalBarsUpdated(descriptor.signalBarsUpdated),
    _signalingDataEmitted(descriptor.signalingDataEmitted),
    _remoteMediaStateUpdated(descriptor.remoteMediaStateUpdated),
    _remoteBatteryLevelIsLowUpdated(descriptor.remoteBatteryLevelIsLowUpdated),
    _remotePrefferedAspectRatioUpdated(descriptor.remotePrefferedAspectRatioUpdated),
	_videoCapture(descriptor.videoCapture),
	_state(State::Reconnecting),
	_videoState(_videoCapture ? VideoState::Active : VideoState::Inactive),
    _platformContext(descriptor.platformContext) {
        assert(StaticThreads::getMediaThread()->IsCurrent());

        rtc::LogMessage::LogToDebug(rtc::LS_INFO);
        rtc::LogMessage::SetLogToStderr(false);

        /*webrtc::field_trial::InitFieldTrialsFromString(
            "WebRTC-Audio-SendSideBwe/Enabled/"
            "WebRTC-Audio-Allocation/min:6kbps,max:32kbps/"
            "WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
            "WebRTC-FlexFEC-03/Enabled/"
            "WebRTC-FlexFEC-03-Advertised/Enabled/"
            "WebRTC-Audio-BitrateAdaptation/Enabled/WebRTC-Audio-FecAdaptation/Enabled/"
        );*/

        _streamIds.push_back("stream");
    }

    ~InstanceImplReferenceInternal() {
        assert(StaticThreads::getMediaThread()->IsCurrent());

        _peerConnection->Close();
    }

    void start() {
        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        _signalingConnection.reset(new EncryptedConnection(
            EncryptedConnection::Type::Signaling,
            _encryptionKey,
            [weak](int delayMs, int cause) {
                if (delayMs == 0) {
                    StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, cause](){
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->sendPendingServiceMessages(cause);
                    });
                } else {
                    StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak, cause]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->sendPendingServiceMessages(cause);
                    }, delayMs);
                }
            }
        ));

        webrtc::PeerConnectionFactoryDependencies dependencies;
        dependencies.network_thread = StaticThreads::getNetworkThread();
        dependencies.worker_thread = StaticThreads::getWorkerThread();
        dependencies.signaling_thread = StaticThreads::getMediaThread();
        dependencies.task_queue_factory = webrtc::CreateDefaultTaskQueueFactory();

        cricket::MediaEngineDependencies mediaDeps;
        mediaDeps.task_queue_factory = dependencies.task_queue_factory.get();
        mediaDeps.audio_encoder_factory = webrtc::CreateBuiltinAudioEncoderFactory();
        mediaDeps.audio_decoder_factory = webrtc::CreateBuiltinAudioDecoderFactory();
        mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext);
        mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

        webrtc::AudioProcessing *apm = webrtc::AudioProcessingBuilder().Create();
        webrtc::AudioProcessing::Config audioConfig;
        webrtc::AudioProcessing::Config::NoiseSuppression noiseSuppression;
        noiseSuppression.enabled = true;
        noiseSuppression.level = webrtc::AudioProcessing::Config::NoiseSuppression::kHigh;
        audioConfig.noise_suppression = noiseSuppression;

        audioConfig.high_pass_filter.enabled = true;

        apm->ApplyConfig(audioConfig);

        mediaDeps.audio_processing = apm;

        dependencies.media_engine = cricket::CreateMediaEngine(std::move(mediaDeps));
        dependencies.call_factory = webrtc::CreateCallFactory();
        dependencies.event_log_factory =
            std::make_unique<webrtc::RtcEventLogFactory>(dependencies.task_queue_factory.get());
        dependencies.network_controller_factory = nullptr;
        //dependencies.media_transport_factory = nullptr;

        _nativeFactory = webrtc::CreateModularPeerConnectionFactory(std::move(dependencies));

        webrtc::PeerConnectionInterface::RTCConfiguration config;
        config.sdp_semantics = webrtc::SdpSemantics::kUnifiedPlan;
        //config.continual_gathering_policy = webrtc::PeerConnectionInterface::ContinualGatheringPolicy::GATHER_CONTINUALLY;
        /*config.audio_jitter_buffer_fast_accelerate = true;
        config.prioritize_most_likely_ice_candidate_pairs = true;
        config.presume_writable_when_fully_relayed = true;
        config.audio_jitter_buffer_enable_rtx_handling = true;*/

        for (auto &server : _rtcServers) {
            if (server.isTurn) {
                webrtc::PeerConnectionInterface::IceServer iceServer;
                std::ostringstream uri;
                uri << "turn:";
                uri << server.host;
                uri << ":";
                uri << server.port;
                iceServer.uri = uri.str();
                iceServer.username = server.login;
                iceServer.password = server.password;
                config.servers.push_back(iceServer);
            } else {
                webrtc::PeerConnectionInterface::IceServer iceServer;
                std::ostringstream uri;
                uri << "stun:";
                uri << server.host;
                uri << ":";
                uri << server.port;
                iceServer.uri = uri.str();
                config.servers.push_back(iceServer);
            }
        }

        if (true || !_enableP2P) {
            config.type = webrtc::PeerConnectionInterface::kRelay;
        }

        _observer.reset(new PeerConnectionObserverImpl(
            [weak](std::string sdp, int mid, std::string sdpMid) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, mid, sdpMid](){
                    auto strong = weak.lock();
                    if (strong) {
                        strong->emitIceCandidate(sdp, mid, sdpMid);
                    }
                });
            },
            [weak](bool isConnected) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isConnected](){
                    auto strong = weak.lock();
                    if (strong) {
                        strong->updateIsConnected(isConnected);
                    }
                });
            },
            [weak](rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, transceiver](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onTrack(transceiver);
                });
            }
        ));
        _peerConnection = _nativeFactory->CreatePeerConnection(config, nullptr, nullptr, _observer.get());
        assert(_peerConnection != nullptr);

        cricket::AudioOptions options;
        rtc::scoped_refptr<webrtc::AudioSourceInterface> audioSource = _nativeFactory->CreateAudioSource(options);
        _localAudioTrack = _nativeFactory->CreateAudioTrack("audio0", audioSource);
        _peerConnection->AddTrack(_localAudioTrack, _streamIds);

        if (_videoCapture) {
            beginSendingVideo();
        }

        if (_encryptionKey.isOutgoing) {
            emitOffer();
        }

        beginStatsTimer(1000);
    }

    void setMuteMicrophone(bool muteMicrophone) {
        _localAudioTrack->set_enabled(!muteMicrophone);
        changeAudioState(muteMicrophone ? AudioState::Muted : AudioState::Active);
    }

    void setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        if (!sink) {
            return;
        }
        _currentSink = sink;
        if (_remoteVideoTrack) {
            _remoteVideoTrack->AddOrUpdateSink(_currentSink.get(), rtc::VideoSinkWants());
        }
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
        assert(videoCapture != nullptr);

        _videoCapture = videoCapture;

        if (_preferredAspectRatio > 0.01f) {
            VideoCaptureInterfaceObject *videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());
            videoCaptureImpl->setPreferredAspectRatio(_preferredAspectRatio);
        }
		beginSendingVideo();
    }

    void sendVideoDeviceUpdated() {
    }

    void setRequestedVideoAspect(float aspect) {
    }

    void receiveSignalingData(const std::vector<uint8_t> &data) {
        if (true) {
            rtc::CopyOnWriteBuffer packet;
            packet.SetData(data.data(), data.size());
            processSignalingData(packet);
            return;
        }

        if (const auto packet = _signalingConnection->handleIncomingPacket((const char *)data.data(), data.size())) {
            const auto mainMessage = &packet->main.message.data;
            if (const auto signalingData = absl::get_if<UnstructuredDataMessage>(mainMessage)) {
                processSignalingData(signalingData->data);
            }
            for (auto &it : packet->additional) {
                const auto additionalMessage = &it.message.data;
                if (const auto signalingData = absl::get_if<UnstructuredDataMessage>(additionalMessage)) {
                    processSignalingData(signalingData->data);
                }
            }
        }
    }

    void processSignalingData(const rtc::CopyOnWriteBuffer &decryptedPacket) {
        rtc::ByteBufferReader reader((const char *)decryptedPacket.data(), decryptedPacket.size());
        uint8_t command = 0;
        if (!reader.ReadUInt8(&command)) {
            return;
        }
        if (command == 1) {
            uint32_t sdpLength = 0;
            if (!reader.ReadUInt32(&sdpLength)) {
                return;
            }
            std::string sdp;
            if (!reader.ReadString(&sdp, sdpLength)) {
                return;
            }
            uint32_t mid = 0;
            if (!reader.ReadUInt32(&mid)) {
                return;
            }
            uint32_t sdpMidLength = 0;
            if (!reader.ReadUInt32(&sdpMidLength)) {
                return;
            }
            std::string sdpMid;
            if (!reader.ReadString(&sdpMid, sdpMidLength)) {
                return;
            }
            _pendingRemoteIceCandidates.push_back(std::make_shared<IceCandidateData>(sdpMid, mid, sdp));
            processRemoteIceCandidatesIfReady();
        } else if (command == 2) {
            uint32_t sdpLength = 0;
            if (!reader.ReadUInt32(&sdpLength)) {
                return;
            }
            std::string sdp;
            if (!reader.ReadString(&sdp, sdpLength)) {
                return;
            }
            uint32_t typeLength = 0;
            if (!reader.ReadUInt32(&typeLength)) {
                return;
            }
            std::string type;
            if (!reader.ReadString(&type, typeLength)) {
                return;
            }
            webrtc::SdpParseError error;
            webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, sdp, &error);
            if (sessionDescription != nullptr) {
                const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());
                rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak]() {
                    StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak](){
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->emitAnswer();
                    });
                }));
                _peerConnection->SetRemoteDescription(observer, sessionDescription);
                _didSetRemoteDescription = true;
                processRemoteIceCandidatesIfReady();
            }
        } else if (command == 3) {
            uint32_t sdpLength = 0;
            if (!reader.ReadUInt32(&sdpLength)) {
                return;
            }
            std::string sdp;
            if (!reader.ReadString(&sdp, sdpLength)) {
                return;
            }
            uint32_t typeLength = 0;
            if (!reader.ReadUInt32(&typeLength)) {
                return;
            }
            std::string type;
            if (!reader.ReadString(&type, typeLength)) {
                return;
            }
            webrtc::SdpParseError error;
            webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, sdp, &error);
            if (sessionDescription != nullptr) {
                rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([]() {
                }));
                _peerConnection->SetRemoteDescription(observer, sessionDescription);
                _didSetRemoteDescription = true;
                processRemoteIceCandidatesIfReady();
            }
        } else if (command == 4) {
            uint8_t value = 0;
            if (!reader.ReadUInt8(&value)) {
                return;
            }
            const auto audio = AudioState(value & 0x01);
            const auto video = VideoState((value >> 1) & 0x03);
            if (video == VideoState(0x03)) {
                return;
            }
            _remoteMediaStateUpdated(audio, video);
        } else if (command == 6) {
            uint32_t value = 0;
            if (!reader.ReadUInt32(&value)) {
                return;
            }
            _preferredAspectRatio = ((float)value) / 1000.0f;
            if (_videoCapture) {
                VideoCaptureInterfaceObject *videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());
                videoCaptureImpl->setPreferredAspectRatio(_preferredAspectRatio);
            }
            _remotePrefferedAspectRatioUpdated(_preferredAspectRatio);
        }
    }

private:
    void beginStatsTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());
        StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->collectStats();
            });
        }, timeoutMs);
    }

    void collectStats() {
        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());

        rtc::scoped_refptr<RTCStatsCollectorCallbackImpl> observer(new rtc::RefCountedObject<RTCStatsCollectorCallbackImpl>([weak](const rtc::scoped_refptr<const webrtc::RTCStatsReport> &stats) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, stats](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->reportStats(stats);
                strong->beginStatsTimer(5000);
            });
        }));
        _peerConnection->GetStats(observer);
    }

    void reportStats(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &stats) {
        int32_t inboundPacketsReceived = 0;
        int32_t inboundPacketsLost = 0;

        for (auto it = stats->begin(); it != stats->end(); it++) {
            if (it->type() == std::string("inbound-rtp")) {
                for (auto &member : it->Members()) {
                    if (member->name() == std::string("packetsLost")) {
                        inboundPacketsLost = *(member->cast_to<webrtc::RTCStatsMember<int>>());
                    } else if (member->name() == std::string("packetsReceived")) {
                        inboundPacketsReceived = *(member->cast_to<webrtc::RTCStatsMember<unsigned int>>());
                    }
                }
            }
        }

        int32_t deltaPacketsReceived = inboundPacketsReceived - _statsData.packetsReceived;
        int32_t deltaPacketsLost = inboundPacketsLost - _statsData.packetsLost;

        _statsData.packetsReceived = inboundPacketsReceived;
        _statsData.packetsLost = inboundPacketsLost;

        float signalBarsNorm = 5.0f;

        if (deltaPacketsReceived > 0) {
            float lossRate = ((float)deltaPacketsLost) / ((float)deltaPacketsReceived);
            float adjustedLossRate = lossRate / 0.1f;
            adjustedLossRate = fmaxf(0.0f, adjustedLossRate);
            adjustedLossRate = fminf(1.0f, adjustedLossRate);
            float adjustedQuality = 1.0f - adjustedLossRate;
            _signalBarsUpdated((int)(adjustedQuality * signalBarsNorm));
        } else {
            _signalBarsUpdated((int)(1.0f * signalBarsNorm));
        }
    }

    void sendPendingServiceMessages(int cause) {
        if (const auto prepared = _signalingConnection->prepareForSendingService(cause)) {
            _signalingDataEmitted(prepared->bytes);
        }
    }

    void emitSignaling(const rtc::ByteBufferWriter &buffer) {
        rtc::CopyOnWriteBuffer packet;
        packet.SetData(buffer.Data(), buffer.Length());

        if (true) {
            std::vector<uint8_t> result;
            result.resize(buffer.Length());
            memcpy(result.data(), buffer.Data(), buffer.Length());
            _signalingDataEmitted(result);
            return;
        }

        if (const auto prepared = _signalingConnection->prepareForSending(Message{ UnstructuredDataMessage{ packet } })) {
            _signalingDataEmitted(prepared->bytes);
        }
    }

    void emitIceCandidate(std::string sdp, int mid, std::string sdpMid) {
        RTC_LOG(LS_INFO) << "emitIceCandidate " << sdp << ", " << mid << ", " << sdpMid;

        rtc::ByteBufferWriter writer;
        writer.WriteUInt8(1);
        writer.WriteUInt32((uint32_t)sdp.size());
        writer.WriteString(sdp);
        writer.WriteUInt32((uint32_t)mid);
        writer.WriteUInt32((uint32_t)sdpMid.size());
        writer.WriteString(sdpMid);

        emitSignaling(writer);
    }

    void emitOffer() {
        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());

        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        options.offer_to_receive_audio = 1;
        if (_videoCapture) {
            options.offer_to_receive_video = 1;
        }

        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak](std::string sdp, std::string type) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                webrtc::SdpParseError error;
                webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, sdp, &error);
                if (sessionDescription != nullptr) {
                    rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, sdp, type]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->emitOfferData(sdp, type);
                    }));
                    strong->_peerConnection->SetLocalDescription(observer, sessionDescription);
                }
            });
        }));
        _peerConnection->CreateOffer(observer, options);
    }

    void emitOfferData(std::string sdp, std::string type) {
        rtc::ByteBufferWriter writer;
        writer.WriteUInt8(2);
        writer.WriteUInt32((uint32_t)sdp.size());
        writer.WriteString(sdp);
        writer.WriteUInt32((uint32_t)type.size());
        writer.WriteString(type);

        emitSignaling(writer);
    }

    void emitAnswerData(std::string sdp, std::string type) {
        rtc::ByteBufferWriter writer;
        writer.WriteUInt8(3);
        writer.WriteUInt32((uint32_t)sdp.size());
        writer.WriteString(sdp);
        writer.WriteUInt32((uint32_t)type.size());
        writer.WriteString(type);

        emitSignaling(writer);
    }

    void emitAnswer() {
        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());

        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        options.offer_to_receive_audio = 1;
        if (_videoCapture) {
            options.offer_to_receive_video = 1;
        }

        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak](std::string sdp, std::string type) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                webrtc::SdpParseError error;
                webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, sdp, &error);
                if (sessionDescription != nullptr) {
                    rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, sdp, type]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        strong->emitAnswerData(sdp, type);
                    }));
                    strong->_peerConnection->SetLocalDescription(observer, sessionDescription);
                }
            });
        }));
        _peerConnection->CreateAnswer(observer, options);

    }

    void changeVideoState(VideoState state) {
        if (_videoState != state) {
            _videoState = state;
            emitMediaState();
        }
    }

    void changeAudioState(AudioState state) {
        if (_audioState != state) {
            _audioState = state;
            emitMediaState();
        }
    }

    void emitMediaState() {
        rtc::ByteBufferWriter writer;
        writer.WriteUInt8(4);
        writer.WriteUInt8((uint8_t(_videoState) << 1) | uint8_t(_audioState));

        emitSignaling(writer);
    }

    void emitRequestVideo() {
        rtc::ByteBufferWriter writer;
        writer.WriteUInt8(5);

        emitSignaling(writer);
    }

    void emitVideoParameters() {
        if (_localPreferredVideoAspectRatio > 0.01f) {
            rtc::ByteBufferWriter writer;
            writer.WriteUInt8(6);
            writer.WriteUInt32((uint32_t)(_localPreferredVideoAspectRatio * 1000.0f));

            emitSignaling(writer);
        }
    }

    void processRemoteIceCandidatesIfReady() {
        if (_pendingRemoteIceCandidates.size() == 0 || !_didSetRemoteDescription) {
            return;
        }

        for (auto &it : _pendingRemoteIceCandidates) {
            webrtc::SdpParseError error;
            webrtc::IceCandidateInterface *iceCandidate = webrtc::CreateIceCandidate(it->sdpMid, it->mid, it->sdp, &error);
            if (iceCandidate != nullptr) {
                std::unique_ptr<webrtc::IceCandidateInterface> nativeCandidate = std::unique_ptr<webrtc::IceCandidateInterface>(iceCandidate);
                _peerConnection->AddIceCandidate(std::move(nativeCandidate), [](auto error) {
                });
            }
        }
        _pendingRemoteIceCandidates.clear();
    }

    void updateIsConnected(bool isConnected) {
        if (isConnected) {
            _state = State::Established;
            if (!_didConnectOnce) {
                _didConnectOnce = true;
            }
        } else {
            _state = State::Reconnecting;
        }
        _stateUpdated(_state);
    }

    void onTrack(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
        if (!_remoteVideoTrack) {
            if (transceiver->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
                _remoteVideoTrack = static_cast<webrtc::VideoTrackInterface *>(transceiver->receiver()->track().get());
            }
            if (_remoteVideoTrack && _currentSink) {
                _remoteVideoTrack->AddOrUpdateSink(_currentSink.get(), rtc::VideoSinkWants());
            }
        }
    }

    void beginSendingVideo() {
        if (!_videoCapture) {
            return;
        }

        VideoCaptureInterfaceObject *videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());

        const auto weak = std::weak_ptr<InstanceImplReferenceInternal>(shared_from_this());

        videoCaptureImpl->setStateUpdated([weak](VideoState state) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, state](){
                auto strong = weak.lock();
                if (strong) {
                    strong->changeVideoState(state);
                }
            });
        });

        _localVideoTrack = _nativeFactory->CreateVideoTrack("video0", videoCaptureImpl->source());
        _peerConnection->AddTrack(_localVideoTrack, _streamIds);
        for (auto &it : _peerConnection->GetTransceivers()) {
            if (it->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
                auto capabilities = _nativeFactory->GetRtpSenderCapabilities(
                    cricket::MediaType::MEDIA_TYPE_VIDEO);

                std::vector<webrtc::RtpCodecCapability> codecs;
                for (auto &codec : capabilities.codecs) {
#ifndef WEBRTC_DISABLE_H265
                    if (codec.name == cricket::kH265CodecName) {
                        codecs.insert(codecs.begin(), codec);
                    } else {
                        codecs.push_back(codec);
                    }
#else
                    codecs.push_back(codec);
#endif
                }
                it->SetCodecPreferences(codecs);

                break;
            }
        }

        if (_didConnectOnce && _encryptionKey.isOutgoing) {
            emitOffer();
        }

        emitVideoParameters();
    }

private:
    EncryptionKey _encryptionKey;
    std::vector<RtcServer> _rtcServers;
    bool _enableP2P;
    std::function<void(State)> _stateUpdated;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    std::unique_ptr<EncryptedConnection> _signalingConnection;
    float _localPreferredVideoAspectRatio = 0.0f;
    float _preferredAspectRatio = 0.0f;

    State _state = State::WaitInit;
    AudioState _audioState = AudioState::Active;
    VideoState _videoState = VideoState::Inactive;
    bool _didConnectOnce = false;

    std::vector<std::string> _streamIds;

    StatsData _statsData;

    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> _nativeFactory;
    std::unique_ptr<PeerConnectionObserverImpl> _observer;
    rtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;
    std::unique_ptr<webrtc::MediaConstraints> _nativeConstraints;
    rtc::scoped_refptr<webrtc::AudioTrackInterface> _localAudioTrack;
    rtc::scoped_refptr<webrtc::VideoTrackInterface> _localVideoTrack;
    rtc::scoped_refptr<webrtc::VideoTrackInterface> _remoteVideoTrack;

    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentSink;

    bool _didSetRemoteDescription = false;
    std::vector<std::shared_ptr<IceCandidateData>> _pendingRemoteIceCandidates;

    std::shared_ptr<PlatformContext> _platformContext;
};

InstanceImplReference::InstanceImplReference(Descriptor &&descriptor) :
    logSink_(std::make_unique<LogSinkImpl>(descriptor.config.logPath)) {
    rtc::LogMessage::AddLogToStream(logSink_.get(), rtc::LS_INFO);

	internal_.reset(new ThreadLocalObject<InstanceImplReferenceInternal>(StaticThreads::getMediaThread(), [descriptor = std::move(descriptor)]() {
        return new InstanceImplReferenceInternal(
            descriptor
        );
    }));
    internal_->perform(RTC_FROM_HERE, [](InstanceImplReferenceInternal *internal){
        internal->start();
    });
}

InstanceImplReference::~InstanceImplReference() {
	rtc::LogMessage::RemoveLogToStream(logSink_.get());
}

void InstanceImplReference::setNetworkType(NetworkType networkType) {
}

void InstanceImplReference::setMuteMicrophone(bool muteMicrophone) {
    internal_->perform(RTC_FROM_HERE, [muteMicrophone = muteMicrophone](InstanceImplReferenceInternal *internal) {
        internal->setMuteMicrophone(muteMicrophone);
    });
}

void InstanceImplReference::receiveSignalingData(const std::vector<uint8_t> &data) {
    internal_->perform(RTC_FROM_HERE, [data](InstanceImplReferenceInternal *internal) {
        internal->receiveSignalingData(data);
    });
}

void InstanceImplReference::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    internal_->perform(RTC_FROM_HERE, [videoCapture](InstanceImplReferenceInternal *internal) {
        internal->setVideoCapture(videoCapture);
    });
}

void InstanceImplReference::setRequestedVideoAspect(float aspect) {
    internal_->perform(RTC_FROM_HERE, [aspect](InstanceImplReferenceInternal *internal) {
        internal->setRequestedVideoAspect(aspect);
    });
}

void InstanceImplReference::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    internal_->perform(RTC_FROM_HERE, [sink](InstanceImplReferenceInternal *internal) {
        internal->setIncomingVideoOutput(sink);
    });
}

void InstanceImplReference::setAudioOutputGainControlEnabled(bool enabled) {
}

void InstanceImplReference::setEchoCancellationStrength(int strength) {
}

void InstanceImplReference::setAudioInputDevice(std::string id) {
}

void InstanceImplReference::setAudioOutputDevice(std::string id) {
}

void InstanceImplReference::setInputVolume(float level) {
}

void InstanceImplReference::setOutputVolume(float level) {
}

void InstanceImplReference::setAudioOutputDuckingEnabled(bool enabled) {
}

void InstanceImplReference::setIsLowBatteryLevel(bool isLowBatteryLevel) {
}

int InstanceImplReference::GetConnectionMaxLayer() {
    return 92;
}

std::vector<std::string> InstanceImplReference::GetVersions() {
    std::vector<std::string> result;
    result.push_back("2.8.8");
    return result;
}

std::string InstanceImplReference::getLastError() {
	return "ERROR_UNKNOWN";
}

std::string InstanceImplReference::getDebugInfo() {
	return "";
}

int64_t InstanceImplReference::getPreferredRelayId() {
    return 0;
}

TrafficStats InstanceImplReference::getTrafficStats() {
	auto result = TrafficStats();
	return result;
}

PersistentState InstanceImplReference::getPersistentState() {
	return PersistentState();
}

void InstanceImplReference::stop(std::function<void(FinalState)> completion) {
    auto result = FinalState();

    result.persistentState = getPersistentState();
    result.debugLog = logSink_->result();
    result.trafficStats = getTrafficStats();
    result.isRatingSuggested = false;

    completion(result);
}

template <>
bool Register<InstanceImplReference>() {
	return Meta::RegisterOne<InstanceImplReference>();
}

} // namespace tgcalls
