#include "v2/InstanceV2ReferenceImpl.h"

#include "LogSinkImpl.h"
#include "VideoCaptureInterfaceImpl.h"
#include "VideoCapturerInterface.h"
#include "v2/NativeNetworkingImpl.h"
#include "v2/Signaling.h"
#include "v2/ContentNegotiation.h"

#include "CodecSelectHelper.h"
#include "platform/PlatformInterface.h"

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
#include "pc/channel_manager.h"
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
#ifdef WEBRTC_IOS
#include "platform/darwin/iOS/tgcalls_audio_device_module_ios.h"
#endif
#include <random>
#include <sstream>

namespace tgcalls {
namespace {

static VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

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

} // namespace

class InstanceV2ReferenceImplInternal : public std::enable_shared_from_this<InstanceV2ReferenceImplInternal> {
public:
    InstanceV2ReferenceImplInternal(Descriptor &&descriptor, std::shared_ptr<Threads> threads) :
    _threads(threads),
    _rtcServers(descriptor.rtcServers),
    _proxy(std::move(descriptor.proxy)),
    _enableP2P(descriptor.config.enableP2P),
    _encryptionKey(std::move(descriptor.encryptionKey)),
    _stateUpdated(descriptor.stateUpdated),
    _signalBarsUpdated(descriptor.signalBarsUpdated),
    _audioLevelUpdated(descriptor.audioLevelUpdated),
    _remoteBatteryLevelIsLowUpdated(descriptor.remoteBatteryLevelIsLowUpdated),
    _remoteMediaStateUpdated(descriptor.remoteMediaStateUpdated),
    _remotePrefferedAspectRatioUpdated(descriptor.remotePrefferedAspectRatioUpdated),
    _signalingDataEmitted(descriptor.signalingDataEmitted),
    _createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _eventLog(std::make_unique<webrtc::RtcEventLogNull>()),
    _taskQueueFactory(webrtc::CreateDefaultTaskQueueFactory()),
    _videoCapture(descriptor.videoCapture),
    _platformContext(descriptor.platformContext) {
    }

    ~InstanceV2ReferenceImplInternal() {
        
        _currentSink.reset();
    }

    void start() {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());
        
        PlatformInterface::SharedInstance()->configurePlatformAudio();
        
        RTC_DCHECK(_threads->getMediaThread()->IsCurrent());
        
        //_threads->getWorkerThread()->Invoke<void>(RTC_FROM_HERE, [&]() {
            cricket::MediaEngineDependencies mediaDeps;
            mediaDeps.task_queue_factory = _taskQueueFactory.get();
            mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
            mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();

            mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext, true);
            mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);

            _audioDeviceModule = createAudioDeviceModule();

            mediaDeps.adm = _audioDeviceModule;

            std::unique_ptr<cricket::MediaEngineInterface> mediaEngine = cricket::CreateMediaEngine(std::move(mediaDeps));
        //});
        
        webrtc::PeerConnectionFactoryDependencies peerConnectionFactoryDependencies;
        peerConnectionFactoryDependencies.signaling_thread = _threads->getMediaThread();
        peerConnectionFactoryDependencies.worker_thread = _threads->getWorkerThread();
        peerConnectionFactoryDependencies.task_queue_factory = std::move(_taskQueueFactory);
        peerConnectionFactoryDependencies.media_engine = std::move(mediaEngine);
        
        auto peerConnectionFactory = webrtc::PeerConnectionFactory::Create(std::move(peerConnectionFactoryDependencies));
        
        webrtc::PeerConnectionDependencies peerConnectionDependencies(nullptr);
        
        webrtc::PeerConnectionInterface::RTCConfiguration peerConnectionConfiguration;
        
        auto peerConnectionOrError = peerConnectionFactory->CreatePeerConnectionOrError(peerConnectionConfiguration, std::move(peerConnectionDependencies));
        if (peerConnectionOrError.ok()) {
            _peerConnection = peerConnectionOrError.value();
        }

        if (_videoCapture) {
            setVideoCapture(_videoCapture);
        }

        beginSignaling();
    }

    void sendSignalingMessage(signaling::Message const &message) {
        auto data = message.serialize();

        RTC_LOG(LS_INFO) << "sendSignalingMessage: " << std::string(data.begin(), data.end());

        if (_signalingEncryption) {
            if (const auto encryptedData = _signalingEncryption->encryptOutgoing(data)) {
                _signalingDataEmitted(std::vector<uint8_t>(encryptedData->data(), encryptedData->data() + encryptedData->size()));
            } else {
                RTC_LOG(LS_ERROR) << "sendSignalingMessage: failed to encrypt payload";
            }
        } else {
            _signalingDataEmitted(data);
        }
    }

    void beginSignaling() {
        _signalingEncryption.reset(new SignalingEncryption(_encryptionKey));

        if (_encryptionKey.isOutgoing) {
            sendInitialSetup();
        }
    }

    void createNegotiatedChannels() {
        /*const auto coordinatedState = _contentNegotiationContext->coordinatedState();
        if (!coordinatedState) {
            return;
        }
        
        if (_outgoingAudioChannelId) {
            const auto audioSsrc = _contentNegotiationContext->outgoingChannelSsrc(_outgoingAudioChannelId.value());
            if (audioSsrc) {
                if (_outgoingAudioChannel && _outgoingAudioChannel->ssrc() != audioSsrc.value()) {
                    _outgoingAudioChannel.reset();
                }
                
                absl::optional<signaling::MediaContent> outgoingAudioContent;
                for (const auto &content : coordinatedState->outgoingContents) {
                    if (content.type == signaling::MediaContent::Type::Audio && content.ssrc == audioSsrc.value()) {
                        outgoingAudioContent = content;
                        break;
                    }
                }
                
                if (outgoingAudioContent) {
                    if (!_outgoingAudioChannel) {
                        _outgoingAudioChannel.reset(new OutgoingAudioChannel(
                            _call.get(),
                            _channelManager.get(),
                            _uniqueRandomIdGenerator.get(),
                            &_audioSource,
                            _rtpTransport,
                            outgoingAudioContent.value(),
                            _threads
                        ));
                    }
                }
            }
        }
        
        if (_outgoingVideoChannelId) {
            const auto videoSsrc = _contentNegotiationContext->outgoingChannelSsrc(_outgoingVideoChannelId.value());
            if (videoSsrc) {
                if (_outgoingVideoChannel && _outgoingVideoChannel->ssrc() != videoSsrc.value()) {
                    _outgoingVideoChannel.reset();
                }
                
                absl::optional<signaling::MediaContent> outgoingVideoContent;
                for (const auto &content : coordinatedState->outgoingContents) {
                    if (content.type == signaling::MediaContent::Type::Video && content.ssrc == videoSsrc.value()) {
                        outgoingVideoContent = content;
                        break;
                    }
                }
                
                if (outgoingVideoContent) {
                    if (!_outgoingVideoChannel) {
                        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

                        _outgoingVideoChannel.reset(new OutgoingVideoChannel(
                            _threads,
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            _videoBitrateAllocatorFactory.get(),
                            [threads = _threads, weak]() {
                                threads->getMediaThread()->PostTask(RTC_FROM_HERE, [=] {
                                    const auto strong = weak.lock();
                                    if (!strong) {
                                        return;
                                    }
                                    strong->sendMediaState();
                                });
                            },
                            outgoingVideoContent.value(),
                            false
                        ));

                        if (_videoCapture) {
                            _outgoingVideoChannel->setVideoCapture(_videoCapture);
                        }
                    }
                }
            }
        }
        
        if (_outgoingScreencastChannelId) {
            const auto screencastSsrc = _contentNegotiationContext->outgoingChannelSsrc(_outgoingScreencastChannelId.value());
            if (screencastSsrc) {
                if (_outgoingScreencastChannel && _outgoingScreencastChannel->ssrc() != screencastSsrc.value()) {
                    _outgoingScreencastChannel.reset();
                }
                
                absl::optional<signaling::MediaContent> outgoingScreencastContent;
                for (const auto &content : coordinatedState->outgoingContents) {
                    if (content.type == signaling::MediaContent::Type::Video && content.ssrc == screencastSsrc.value()) {
                        outgoingScreencastContent = content;
                        break;
                    }
                }
                
                if (outgoingScreencastContent) {
                    if (!_outgoingScreencastChannel) {
                        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

                        _outgoingScreencastChannel.reset(new OutgoingVideoChannel(
                            _threads,
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            _videoBitrateAllocatorFactory.get(),
                            [threads = _threads, weak]() {
                                threads->getMediaThread()->PostTask(RTC_FROM_HERE, [=] {
                                    const auto strong = weak.lock();
                                    if (!strong) {
                                        return;
                                    }
                                    strong->sendMediaState();
                                });
                            },
                            outgoingScreencastContent.value(),
                            true
                        ));

                        if (_screencastCapture) {
                            _outgoingScreencastChannel->setVideoCapture(_screencastCapture);
                        }
                    }
                }
            }
        }
        
        for (const auto &content : coordinatedState->incomingContents) {
            switch (content.type) {
                case signaling::MediaContent::Type::Audio: {
                    if (_incomingAudioChannel && _incomingAudioChannel->ssrc() != content.ssrc) {
                        _incomingAudioChannel.reset();
                    }
                    
                    if (!_incomingAudioChannel) {
                        _incomingAudioChannel.reset(new IncomingV2AudioChannel(
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            content,
                            _threads
                        ));
                    }
                    
                    break;
                }
                case signaling::MediaContent::Type::Video: {
                    if (_incomingVideoChannel && _incomingVideoChannel->ssrc() != content.ssrc) {
                        _incomingVideoChannel.reset();
                    }
                    
                    if (!_incomingVideoChannel) {
                        _incomingVideoChannel.reset(new IncomingV2VideoChannel(
                            _channelManager.get(),
                            _call.get(),
                            _rtpTransport,
                            _uniqueRandomIdGenerator.get(),
                            content,
                            _threads
                        ));
                        _incomingVideoChannel->addSink(_currentSink);
                    }
                    
                    break;
                }
                default: {
                    RTC_FATAL() << "Unknown media type";
                    break;
                }
            }
        }

        adjustBitratePreferences(true);
        sendMediaState();*/
    }

    void sendInitialSetup() {
        const auto weak = std::weak_ptr<InstanceV2ReferenceImplInternal>(shared_from_this());

        /*_networking->perform(RTC_FROM_HERE, [weak, threads = _threads, isOutgoing = _encryptionKey.isOutgoing](NativeNetworkingImpl *networking) {
            auto localFingerprint = networking->getLocalFingerprint();
            std::string hash = localFingerprint->algorithm;
            std::string fingerprint = localFingerprint->GetRfc4572Fingerprint();
            std::string setup;
            if (isOutgoing) {
                setup = "actpass";
            } else {
                setup = "passive";
            }

            auto localIceParams = networking->getLocalIceParameters();
            std::string ufrag = localIceParams.ufrag;
            std::string pwd = localIceParams.pwd;

            threads->getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ufrag, pwd, hash, fingerprint, setup, localIceParams]() {
                const auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                signaling::InitialSetupMessage data;

                data.ufrag = ufrag;
                data.pwd = pwd;

                signaling::DtlsFingerprint dtlsFingerprint;
                dtlsFingerprint.hash = hash;
                dtlsFingerprint.fingerprint = fingerprint;
                dtlsFingerprint.setup = setup;
                data.fingerprints.push_back(std::move(dtlsFingerprint));

                signaling::Message message;
                message.data = std::move(data);
                strong->sendSignalingMessage(message);
            });
        });*/
    }
    
    void sendOfferIfNeeded() {
        /*if (const auto offer = _contentNegotiationContext->getPendingOffer()) {
            signaling::NegotiateChannelsMessage data;

            data.exchangeId = offer->exchangeId;
            
            data.contents = offer->contents;

            signaling::Message message;
            message.data = std::move(data);
            sendSignalingMessage(message);
        }*/
    }

    void receiveSignalingData(const std::vector<uint8_t> &data) {
        std::vector<uint8_t> decryptedData;

        if (_signalingEncryption) {
            const auto rawDecryptedData = _signalingEncryption->decryptIncoming(data);
            if (!rawDecryptedData) {
                RTC_LOG(LS_ERROR) << "receiveSignalingData: could not decrypt payload";

                return;
            }

            decryptedData = std::vector<uint8_t>(rawDecryptedData->data(), rawDecryptedData->data() + rawDecryptedData->size());
        } else {
            decryptedData = data;
        }

        processSignalingData(decryptedData);
    }

    void processSignalingData(const std::vector<uint8_t> &data) {
        RTC_LOG(LS_INFO) << "processSignalingData: " << std::string(data.begin(), data.end());

        /*const auto message = signaling::Message::parse(data);
        if (!message) {
            return;
        }
        const auto messageData = &message->data;
        if (const auto initialSetup = absl::get_if<signaling::InitialSetupMessage>(messageData)) {
            PeerIceParameters remoteIceParameters;
            remoteIceParameters.ufrag = initialSetup->ufrag;
            remoteIceParameters.pwd = initialSetup->pwd;

            std::unique_ptr<rtc::SSLFingerprint> fingerprint;
            std::string sslSetup;
            if (initialSetup->fingerprints.size() != 0) {
                fingerprint = rtc::SSLFingerprint::CreateUniqueFromRfc4572(initialSetup->fingerprints[0].hash, initialSetup->fingerprints[0].fingerprint);
                sslSetup = initialSetup->fingerprints[0].setup;
            }

            _networking->perform(RTC_FROM_HERE, [threads = _threads, remoteIceParameters = std::move(remoteIceParameters), fingerprint = std::move(fingerprint), sslSetup = std::move(sslSetup)](NativeNetworkingImpl *networking) {
                networking->setRemoteParams(remoteIceParameters, fingerprint.get(), sslSetup);
            });

            if (_encryptionKey.isOutgoing) {
                sendOfferIfNeeded();
            } else {
                sendInitialSetup();
            }

            _handshakeCompleted = true;
            commitPendingIceCandidates();
        } else if (const auto offerAnwer = absl::get_if<signaling::NegotiateChannelsMessage>(messageData)) {
            auto negotiationContents = std::make_unique<ContentNegotiationContext::NegotiationContents>();
            negotiationContents->exchangeId = offerAnwer->exchangeId;
            negotiationContents->contents = offerAnwer->contents;
            
            if (const auto response = _contentNegotiationContext->setRemoteNegotiationContent(std::move(negotiationContents))) {
                signaling::NegotiateChannelsMessage data;

                data.exchangeId = response->exchangeId;
                data.contents = response->contents;

                signaling::Message message;
                message.data = std::move(data);
                sendSignalingMessage(message);
            }
            
            sendOfferIfNeeded();
            
            createNegotiatedChannels();
        } else if (const auto candidatesList = absl::get_if<signaling::CandidatesMessage>(messageData)) {
            for (const auto &candidate : candidatesList->iceCandidates) {
                webrtc::JsepIceCandidate parseCandidate{ std::string(), 0 };
                if (!parseCandidate.Initialize(candidate.sdpString, nullptr)) {
                    RTC_LOG(LS_ERROR) << "Could not parse candidate: " << candidate.sdpString;
                    continue;
                }
                _pendingIceCandidates.push_back(parseCandidate.candidate());
            }

            if (_handshakeCompleted) {
                commitPendingIceCandidates();
            }
        } else if (const auto mediaState = absl::get_if<signaling::MediaStateMessage>(messageData)) {
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
        }*/
    }

    void commitPendingIceCandidates() {
        if (_pendingIceCandidates.size() == 0) {
            return;
        }
        /*_networking->perform(RTC_FROM_HERE, [threads = _threads, parsedCandidates = _pendingIceCandidates](NativeNetworkingImpl *networking) {
            networking->addCandidates(parsedCandidates);
        });
        _pendingIceCandidates.clear();*/
    }

    /*void onNetworkStateUpdated(NativeNetworkingImpl::State const &state) {
        State mappedState;
        if (state.isReadyToSendData) {
            mappedState = State::Established;
        } else {
            mappedState = State::Reconnecting;
        }
        _stateUpdated(mappedState);
    }*/

    /*void onDataChannelStateUpdated(bool isDataChannelOpen) {
        if (_isDataChannelOpen != isDataChannelOpen) {
            _isDataChannelOpen = isDataChannelOpen;

            if (_isDataChannelOpen) {
                sendMediaState();
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
        _networking->perform(RTC_FROM_HERE, [stringData = std::move(stringData)](NativeNetworkingImpl *networking) {
            networking->sendDataChannelMessage(stringData);
        });
    }

    void onDataChannelMessage(std::string const &message) {
        RTC_LOG(LS_INFO) << "dataChannelMessage received: " << message;
        std::vector<uint8_t> data(message.begin(), message.end());
        processSignalingData(data);
    }*/

    void sendMediaState() {
        /*if (!_isDataChannelOpen) {
            return;
        }
        signaling::Message message;
        signaling::MediaStateMessage data;
        data.isMuted = _isMicrophoneMuted;
        data.isBatteryLow = _isBatteryLow;
        if (_outgoingVideoChannel) {
            if (_outgoingVideoChannel->videoCapture()) {
                data.videoState = signaling::MediaStateMessage::VideoState::Active;
            } else{
                data.videoState = signaling::MediaStateMessage::VideoState::Inactive;
            }
            data.videoRotation = _outgoingVideoChannel->getRotation();
        } else {
            data.videoState = signaling::MediaStateMessage::VideoState::Inactive;
            data.videoRotation = signaling::MediaStateMessage::VideoRotation::Rotation0;
        }
        if (_outgoingScreencastChannel) {
            if (_outgoingScreencastChannel->videoCapture()) {
                data.screencastState = signaling::MediaStateMessage::VideoState::Active;
            } else{
                data.screencastState = signaling::MediaStateMessage::VideoState::Inactive;
            }
        } else {
            data.screencastState = signaling::MediaStateMessage::VideoState::Inactive;
        }
        message.data = std::move(data);
        sendDataChannelMessage(message);*/
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
        /*auto videoCaptureImpl = GetVideoCaptureAssumingSameThread(videoCapture.get());
        if (videoCaptureImpl) {
            if (videoCaptureImpl->isScreenCapture()) {
                _videoCapture = nullptr;
                _screencastCapture = videoCapture;

                if (_outgoingVideoChannel) {
                    _outgoingVideoChannel->setVideoCapture(nullptr);
                }
                if (_outgoingVideoChannelId) {
                    _contentNegotiationContext->removeOutgoingChannel(_outgoingVideoChannelId.value());
                    _outgoingVideoChannelId.reset();
                }

                if (_outgoingScreencastChannel) {
                    _outgoingScreencastChannel->setVideoCapture(videoCapture);
                }
                if (!_outgoingScreencastChannelId) {
                    _outgoingScreencastChannelId = _contentNegotiationContext->addOutgoingChannel(signaling::MediaContent::Type::Video);
                }
            } else {
                _videoCapture = videoCapture;
                _screencastCapture = nullptr;

                if (_outgoingVideoChannel) {
                    _outgoingVideoChannel->setVideoCapture(videoCapture);
                }
                if (!_outgoingVideoChannelId) {
                    _outgoingVideoChannelId = _contentNegotiationContext->addOutgoingChannel(signaling::MediaContent::Type::Video);
                }

                if (_outgoingScreencastChannel) {
                    _outgoingScreencastChannel->setVideoCapture(nullptr);
                }
                if (_outgoingScreencastChannelId) {
                    _contentNegotiationContext->removeOutgoingChannel(_outgoingScreencastChannelId.value());
                    _outgoingScreencastChannelId.reset();
                }
            }
        } else {
            _videoCapture = nullptr;
            _screencastCapture = nullptr;

            if (_outgoingVideoChannel) {
                _outgoingVideoChannel->setVideoCapture(nullptr);
            }

            if (_outgoingScreencastChannel) {
                _outgoingScreencastChannel->setVideoCapture(nullptr);
            }
            
            if (_outgoingVideoChannelId) {
                _contentNegotiationContext->removeOutgoingChannel(_outgoingVideoChannelId.value());
                _outgoingVideoChannelId.reset();
            }
            
            if (_outgoingScreencastChannelId) {
                _contentNegotiationContext->removeOutgoingChannel(_outgoingScreencastChannelId.value());
                _outgoingScreencastChannelId.reset();
            }
        }

        sendOfferIfNeeded();
        sendMediaState();
        adjustBitratePreferences(true);
        createNegotiatedChannels();*/
    }

    void setRequestedVideoAspect(float aspect) {
    }

    void setNetworkType(NetworkType networkType) {
    }

    void setMuteMicrophone(bool muteMicrophone) {
        /*if (_isMicrophoneMuted != muteMicrophone) {
            _isMicrophoneMuted = muteMicrophone;

            if (_outgoingAudioChannel) {
                _outgoingAudioChannel->setIsMuted(muteMicrophone);
            }

            sendMediaState();
        }*/
    }

    void setIncomingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        /*_currentSink = sink;
        if (_incomingVideoChannel) {
            _incomingVideoChannel->addSink(sink);
        }
        if (_incomingScreencastChannel) {
            _incomingScreencastChannel->addSink(sink);
        }*/
    }

    void setAudioInputDevice(std::string id) {
    }

    void setAudioOutputDevice(std::string id) {
    }

    void setIsLowBatteryLevel(bool isLowBatteryLevel) {
        if (_isBatteryLow != isLowBatteryLevel) {
            _isBatteryLow = isLowBatteryLevel;
            sendMediaState();
        }
    }

    void stop(std::function<void(FinalState)> completion) {
        completion({});
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
    rtc::scoped_refptr<webrtc::AudioDeviceModule> createAudioDeviceModule() {
        const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
#ifdef WEBRTC_IOS
            return rtc::make_ref_counted<webrtc::tgcalls_ios_adm::AudioDeviceModuleIOS>(false, false, 1);
#else
            return webrtc::AudioDeviceModule::Create(
                layer,
                _taskQueueFactory.get());
#endif
        };
        const auto check = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
            return (result && result->Init() == 0) ? result : nullptr;
        };
        if (_createAudioDeviceModule) {
            if (const auto result = check(_createAudioDeviceModule(_taskQueueFactory.get()))) {
                return result;
            }
        }
        return check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio));
    }

private:
    std::shared_ptr<Threads> _threads;
    std::vector<RtcServer> _rtcServers;
    std::unique_ptr<Proxy> _proxy;
    bool _enableP2P = false;
    EncryptionKey _encryptionKey;
    std::function<void(State)> _stateUpdated;
    std::function<void(int)> _signalBarsUpdated;
    std::function<void(float)> _audioLevelUpdated;
    std::function<void(bool)> _remoteBatteryLevelIsLowUpdated;
    std::function<void(AudioState, VideoState)> _remoteMediaStateUpdated;
    std::function<void(float)> _remotePrefferedAspectRatioUpdated;
    std::function<void(const std::vector<uint8_t> &)> _signalingDataEmitted;
    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;

    std::unique_ptr<SignalingEncryption> _signalingEncryption;

    bool _handshakeCompleted = false;
    std::vector<cricket::Candidate> _pendingIceCandidates;
    bool _isDataChannelOpen = false;

    std::unique_ptr<webrtc::RtcEventLogNull> _eventLog;
    std::unique_ptr<webrtc::TaskQueueFactory> _taskQueueFactory;
    rtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;
    webrtc::FieldTrialBasedConfig _fieldTrials;
    
    webrtc::LocalAudioSinkAdapter _audioSource;
    
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _audioDeviceModule;

    bool _isBatteryLow = false;

    std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _currentSink;

    std::shared_ptr<VideoCaptureInterface> _videoCapture;
    std::shared_ptr<VideoCaptureInterface> _screencastCapture;
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
        return new InstanceV2ReferenceImplInternal(std::move(descriptor), threads);
    }));
    _internal->perform(RTC_FROM_HERE, [](InstanceV2ReferenceImplInternal *internal) {
        internal->start();
    });
}

InstanceV2ReferenceImpl::~InstanceV2ReferenceImpl() {
    rtc::LogMessage::RemoveLogToStream(_logSink.get());
}

void InstanceV2ReferenceImpl::receiveSignalingData(const std::vector<uint8_t> &data) {
    _internal->perform(RTC_FROM_HERE, [data](InstanceV2ReferenceImplInternal *internal) {
        internal->receiveSignalingData(data);
    });
}

void InstanceV2ReferenceImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) {
    _internal->perform(RTC_FROM_HERE, [videoCapture](InstanceV2ReferenceImplInternal *internal) {
        internal->setVideoCapture(videoCapture);
    });
}

void InstanceV2ReferenceImpl::setRequestedVideoAspect(float aspect) {
    _internal->perform(RTC_FROM_HERE, [aspect](InstanceV2ReferenceImplInternal *internal) {
        internal->setRequestedVideoAspect(aspect);
    });
}

void InstanceV2ReferenceImpl::setNetworkType(NetworkType networkType) {
    _internal->perform(RTC_FROM_HERE, [networkType](InstanceV2ReferenceImplInternal *internal) {
        internal->setNetworkType(networkType);
    });
}

void InstanceV2ReferenceImpl::setMuteMicrophone(bool muteMicrophone) {
    _internal->perform(RTC_FROM_HERE, [muteMicrophone](InstanceV2ReferenceImplInternal *internal) {
        internal->setMuteMicrophone(muteMicrophone);
    });
}

void InstanceV2ReferenceImpl::setIncomingVideoOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _internal->perform(RTC_FROM_HERE, [sink](InstanceV2ReferenceImplInternal *internal) {
        internal->setIncomingVideoOutput(sink);
    });
}

void InstanceV2ReferenceImpl::setAudioInputDevice(std::string id) {
    _internal->perform(RTC_FROM_HERE, [id](InstanceV2ReferenceImplInternal *internal) {
        internal->setAudioInputDevice(id);
    });
}

void InstanceV2ReferenceImpl::setAudioOutputDevice(std::string id) {
    _internal->perform(RTC_FROM_HERE, [id](InstanceV2ReferenceImplInternal *internal) {
        internal->setAudioOutputDevice(id);
    });
}

void InstanceV2ReferenceImpl::setIsLowBatteryLevel(bool isLowBatteryLevel) {
    _internal->perform(RTC_FROM_HERE, [isLowBatteryLevel](InstanceV2ReferenceImplInternal *internal) {
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
    result.push_back("4.0.2");
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
    _internal->perform(RTC_FROM_HERE, [completion, debugLog = std::move(debugLog)](InstanceV2ReferenceImplInternal *internal) mutable {
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
