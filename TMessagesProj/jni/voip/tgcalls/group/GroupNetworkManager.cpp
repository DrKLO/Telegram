#include "group/GroupNetworkManager.h"

#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "api/packet_socket_factory.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "api/jsep_ice_candidate.h"
#include "p2p/base/dtls_transport.h"
#include "p2p/base/dtls_transport_factory.h"
#include "pc/dtls_srtp_transport.h"
#include "pc/dtls_transport.h"

#include "StaticThreads.h"

namespace tgcalls {

class TurnCustomizerImpl : public webrtc::TurnCustomizer {
public:
    TurnCustomizerImpl() {
    }

    virtual ~TurnCustomizerImpl() {
    }

    void MaybeModifyOutgoingStunMessage(cricket::PortInterface* port,
                                        cricket::StunMessage* message) override {
        message->AddAttribute(std::make_unique<cricket::StunByteStringAttribute>(cricket::STUN_ATTR_SOFTWARE, "Telegram "));
    }

    bool AllowChannelData(cricket::PortInterface* port, const void *data, size_t size, bool payload) override {
        return true;
    }
};

class SctpDataChannelProviderInterfaceImpl : public sigslot::has_slots<>, public webrtc::SctpDataChannelProviderInterface, public webrtc::DataChannelObserver {
public:
    SctpDataChannelProviderInterfaceImpl(
        cricket::DtlsTransport *transportChannel,
        std::function<void(bool)> onStateChanged,
        std::function<void(std::string const &)> onMessageReceived,
        std::shared_ptr<Threads> threads
    ) :
    _threads(std::move(threads)),
    _onStateChanged(onStateChanged),
    _onMessageReceived(onMessageReceived) {
        assert(_threads->getNetworkThread()->IsCurrent());

        _sctpTransportFactory.reset(new cricket::SctpTransportFactory(_threads->getNetworkThread()));

        _sctpTransport = _sctpTransportFactory->CreateSctpTransport(transportChannel);
        _sctpTransport->SignalReadyToSendData.connect(this, &SctpDataChannelProviderInterfaceImpl::sctpReadyToSendData);
        _sctpTransport->SignalDataReceived.connect(this, &SctpDataChannelProviderInterfaceImpl::sctpDataReceived);

        webrtc::InternalDataChannelInit dataChannelInit;
        dataChannelInit.id = 0;
        _dataChannel = webrtc::SctpDataChannel::Create(
            this,
            "data",
            dataChannelInit,
            _threads->getNetworkThread(),
            _threads->getNetworkThread()
        );

        _dataChannel->RegisterObserver(this);
    }

    virtual ~SctpDataChannelProviderInterfaceImpl() {
        assert(_threads->getNetworkThread()->IsCurrent());

        _dataChannel->UnregisterObserver();
        _dataChannel->Close();
        _dataChannel = nullptr;

        _sctpTransport = nullptr;
        _sctpTransportFactory.reset();
    }

    void sendDataChannelMessage(std::string const &message) {
        assert(_threads->getNetworkThread()->IsCurrent());

        if (_isDataChannelOpen) {
            RTC_LOG(LS_INFO) << "Outgoing DataChannel message: " << message;

            webrtc::DataBuffer buffer(message);
            _dataChannel->Send(buffer);
        } else {
            RTC_LOG(LS_INFO) << "Could not send an outgoing DataChannel message: the channel is not open";
        }
    }

    virtual void OnStateChange() override {
        assert(_threads->getNetworkThread()->IsCurrent());

        auto state = _dataChannel->state();
        bool isDataChannelOpen = state == webrtc::DataChannelInterface::DataState::kOpen;
        if (_isDataChannelOpen != isDataChannelOpen) {
            _isDataChannelOpen = isDataChannelOpen;
            _onStateChanged(_isDataChannelOpen);
        }
    }

    virtual void OnMessage(const webrtc::DataBuffer& buffer) override {
        assert(_threads->getNetworkThread()->IsCurrent());

        if (!buffer.binary) {
            std::string messageText(buffer.data.data(), buffer.data.data() + buffer.data.size());
            RTC_LOG(LS_INFO) << "Incoming DataChannel message: " << messageText;

            _onMessageReceived(messageText);
        }
    }

    void updateIsConnected(bool isConnected) {
        assert(_threads->getNetworkThread()->IsCurrent());

        if (isConnected) {
            if (!_isSctpTransportStarted) {
                _isSctpTransportStarted = true;
                _sctpTransport->Start(5000, 5000, 262144);
            }
        }
    }

    void sctpReadyToSendData() {
        assert(_threads->getNetworkThread()->IsCurrent());

        _dataChannel->OnTransportReady(true);
    }

    void sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer) {
        assert(_threads->getNetworkThread()->IsCurrent());

        _dataChannel->OnDataReceived(params, buffer);
    }

    virtual bool SendData(const cricket::SendDataParams& params, const rtc::CopyOnWriteBuffer& payload, cricket::SendDataResult* result) override {
        assert(_threads->getNetworkThread()->IsCurrent());

        return _sctpTransport->SendData(params, payload);
    }

    virtual bool ConnectDataChannel(webrtc::SctpDataChannel *data_channel) override {
        assert(_threads->getNetworkThread()->IsCurrent());

        return true;
    }

    virtual void DisconnectDataChannel(webrtc::SctpDataChannel* data_channel) override {
        assert(_threads->getNetworkThread()->IsCurrent());

        return;
    }

    virtual void AddSctpDataStream(int sid) override {
      assert(_threads->getNetworkThread()->IsCurrent());

        _sctpTransport->OpenStream(sid);
    }

    virtual void RemoveSctpDataStream(int sid) override {
        assert(_threads->getNetworkThread()->IsCurrent());

        _threads->getNetworkThread()->Invoke<void>(RTC_FROM_HERE, [this, sid]() {
            _sctpTransport->ResetStream(sid);
        });
    }

    virtual bool ReadyToSendData() const override {
        assert(_threads->getNetworkThread()->IsCurrent());

        return _sctpTransport->ReadyToSendData();
    }

private:
    std::shared_ptr<Threads> _threads;
    std::function<void(bool)> _onStateChanged;
    std::function<void(std::string const &)> _onMessageReceived;

    std::unique_ptr<cricket::SctpTransportFactory> _sctpTransportFactory;
    std::unique_ptr<cricket::SctpTransportInternal> _sctpTransport;
    rtc::scoped_refptr<webrtc::SctpDataChannel> _dataChannel;

    bool _isSctpTransportStarted = false;
    bool _isDataChannelOpen = false;

};

webrtc::CryptoOptions GroupNetworkManager::getDefaulCryptoOptions() {
    auto options = webrtc::CryptoOptions();
    options.srtp.enable_aes128_sha1_80_crypto_cipher = false;
    options.srtp.enable_gcm_crypto_suites = true;
    return options;
}

GroupNetworkManager::GroupNetworkManager(
    std::function<void(const State &)> stateUpdated,
    std::function<void(rtc::CopyOnWriteBuffer const &, bool)> transportMessageReceived,
    std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> rtcpPacketReceived,
    std::function<void(bool)> dataChannelStateUpdated,
    std::function<void(std::string const &)> dataChannelMessageReceived,
    std::shared_ptr<Threads> threads) :
_threads(std::move(threads)),
_stateUpdated(std::move(stateUpdated)),
_transportMessageReceived(std::move(transportMessageReceived)),
_rtcpPacketReceived(std::move(rtcpPacketReceived)),
_dataChannelStateUpdated(dataChannelStateUpdated),
_dataChannelMessageReceived(dataChannelMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH));
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_threads->getNetworkThread()));
    _networkManager = std::make_unique<rtc::BasicNetworkManager>();
    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncResolverFactory>();
    
    _dtlsSrtpTransport = std::make_unique<webrtc::DtlsSrtpTransport>(true);
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    _dtlsSrtpTransport->SetActiveResetSrtpParams(false);
    _dtlsSrtpTransport->SignalDtlsStateChange.connect(this, &GroupNetworkManager::DtlsStateChanged);
    _dtlsSrtpTransport->SignalReadyToSend.connect(this, &GroupNetworkManager::DtlsReadyToSend);
    _dtlsSrtpTransport->SignalRtpPacketReceived.connect(this, &GroupNetworkManager::RtpPacketReceived_n);
    _dtlsSrtpTransport->SignalRtcpPacketReceived.connect(this, &GroupNetworkManager::OnRtcpPacketReceived_n);
    
    resetDtlsSrtpTransport();
}

GroupNetworkManager::~GroupNetworkManager() {
    assert(_threads->getNetworkThread()->IsCurrent());

    RTC_LOG(LS_INFO) << "GroupNetworkManager::~GroupNetworkManager()";

    _dtlsSrtpTransport.reset();
    _dtlsTransport.reset();
    _dataChannelInterface.reset();
    _transportChannel.reset();
    _asyncResolverFactory.reset();
    _portAllocator.reset();
    _networkManager.reset();
    _socketFactory.reset();
}

void GroupNetworkManager::resetDtlsSrtpTransport() {
    _portAllocator.reset(new cricket::BasicPortAllocator(_networkManager.get(), _socketFactory.get(), _turnCustomizer.get(), nullptr));
    _portAllocator->set_flags(_portAllocator->flags());
    _portAllocator->Initialize();

    _portAllocator->SetConfiguration({}, {}, 2, webrtc::NO_PRUNE, _turnCustomizer.get());

    _transportChannel.reset(new cricket::P2PTransportChannel("transport", 0, _portAllocator.get(), _asyncResolverFactory.get(), nullptr));

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_ONCE;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = 8000;
    _transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        false
    );

    _transportChannel->SetIceParameters(localIceParameters);
    const bool isOutgoing = false;
    _transportChannel->SetIceRole(isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);
    _transportChannel->SetRemoteIceMode(cricket::ICEMODE_LITE);

    _transportChannel->SignalIceTransportStateChanged.connect(this, &GroupNetworkManager::transportStateChanged);
    _transportChannel->SignalReadPacket.connect(this, &GroupNetworkManager::transportPacketReceived);

    webrtc::CryptoOptions cryptoOptions = GroupNetworkManager::getDefaulCryptoOptions();
    _dtlsTransport.reset(new cricket::DtlsTransport(_transportChannel.get(), cryptoOptions, nullptr));

    _dtlsTransport->SignalWritableState.connect(
        this, &GroupNetworkManager::OnTransportWritableState_n);
    _dtlsTransport->SignalReceivingState.connect(
        this, &GroupNetworkManager::OnTransportReceivingState_n);
    _dtlsTransport->SignalDtlsHandshakeError.connect(
        this, &GroupNetworkManager::OnDtlsHandshakeError);

    _dtlsTransport->SetDtlsRole(rtc::SSLRole::SSL_SERVER);
    _dtlsTransport->SetLocalCertificate(_localCertificate);
    
    _dtlsSrtpTransport->SetDtlsTransports(_dtlsTransport.get(), nullptr);
}

void GroupNetworkManager::start() {
    _transportChannel->MaybeStartGathering();

    /*const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
    _dataChannelInterface.reset(new SctpDataChannelProviderInterfaceImpl(_dtlsTransport.get(), [weak, threads = _threads](bool state) {
        assert(threads->getNetworkThread()->IsCurrent());
        const auto strong = weak.lock();
        if (!strong) {
            return;
        }
        strong->_dataChannelStateUpdated(state);
    }, [weak, threads = _threads[](std::string const &message) {
        assert(threads->getNetworkThread()->IsCurrent());
        const auto strong = weak.lock();
        if (!strong) {
            return;
        }
        strong->_dataChannelMessageReceived(message);
    }));*/
}

void GroupNetworkManager::stop() {
    _transportChannel->SignalIceTransportStateChanged.disconnect(this);
    _transportChannel->SignalReadPacket.disconnect(this);
    
    _dtlsTransport->SignalWritableState.disconnect(this);
    _dtlsTransport->SignalReceivingState.disconnect(this);
    _dtlsTransport->SignalDtlsHandshakeError.disconnect(this);
    
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    
    _dataChannelInterface.reset();
    _dtlsTransport.reset();
    _transportChannel.reset();
    _portAllocator.reset();
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH));
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    resetDtlsSrtpTransport();
}

PeerIceParameters GroupNetworkManager::getLocalIceParameters() {
    return _localIceParameters;
}

std::unique_ptr<rtc::SSLFingerprint> GroupNetworkManager::getLocalFingerprint() {
    auto certificate = _localCertificate;
    if (!certificate) {
        return nullptr;
    }
    return rtc::SSLFingerprint::CreateFromCertificate(*certificate);
}

void GroupNetworkManager::setRemoteParams(PeerIceParameters const &remoteIceParameters, std::vector<cricket::Candidate> const &iceCandidates, rtc::SSLFingerprint *fingerprint) {
    _remoteIceParameters = remoteIceParameters;

    cricket::IceParameters parameters(
        remoteIceParameters.ufrag,
        remoteIceParameters.pwd,
        false
    );

    _transportChannel->SetRemoteIceParameters(parameters);

    for (const auto &candidate : iceCandidates) {
        _transportChannel->AddRemoteCandidate(candidate);
    }

    if (fingerprint) {
        _dtlsTransport->SetRemoteFingerprint(fingerprint->algorithm, fingerprint->digest.data(), fingerprint->digest.size());
    }
}

void GroupNetworkManager::sendDataChannelMessage(std::string const &message) {
    if (_dataChannelInterface) {
        _dataChannelInterface->sendDataChannelMessage(message);
    }
}

webrtc::RtpTransport *GroupNetworkManager::getRtpTransport() {
    return _dtlsSrtpTransport.get();
}

void GroupNetworkManager::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
    _threads->getNetworkThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }

        int64_t currentTimestamp = rtc::TimeMillis();
        const int64_t maxTimeout = 20000;

        if (strong->_lastNetworkActivityMs + maxTimeout < currentTimestamp) {
            GroupNetworkManager::State emitState;
            emitState.isReadyToSendData = false;
            emitState.isFailed = true;
            strong->_stateUpdated(emitState);
        }

        strong->checkConnectionTimeout();
    }, 1000);
}

void GroupNetworkManager::candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void GroupNetworkManager::candidateGatheringState(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void GroupNetworkManager::OnTransportWritableState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}
void GroupNetworkManager::OnTransportReceivingState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}

void GroupNetworkManager::OnDtlsHandshakeError(rtc::SSLHandshakeError error) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void GroupNetworkManager::DtlsStateChanged() {
    UpdateAggregateStates_n();

    if (_dtlsTransport->IsDtlsActive()) {
        const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
        _threads->getNetworkThread()->PostTask(RTC_FROM_HERE, [weak]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->UpdateAggregateStates_n();
        });
    }
}

void GroupNetworkManager::DtlsReadyToSend(bool isReadyToSend) {
    UpdateAggregateStates_n();

    if (isReadyToSend) {
        const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
        _threads->getNetworkThread()->PostTask(RTC_FROM_HERE, [weak]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->UpdateAggregateStates_n();
        });
    }
}

void GroupNetworkManager::transportStateChanged(cricket::IceTransportInternal *transport) {
    UpdateAggregateStates_n();
}

void GroupNetworkManager::transportReadyToSend(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void GroupNetworkManager::transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _lastNetworkActivityMs = rtc::TimeMillis();
}

void GroupNetworkManager::RtpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us, bool isUnresolved) {
    if (_transportMessageReceived) {
        _transportMessageReceived(*packet, isUnresolved);
    }
}

void GroupNetworkManager::OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us) {
    if (_rtcpPacketReceived) {
        _rtcpPacketReceived(*packet, packet_time_us);
    }
}

void GroupNetworkManager::UpdateAggregateStates_n() {
    assert(_threads->getNetworkThread()->IsCurrent());

    auto state = _transportChannel->GetIceTransportState();
    bool isConnected = false;
    switch (state) {
        case webrtc::IceTransportState::kConnected:
        case webrtc::IceTransportState::kCompleted:
            isConnected = true;
            break;
        default:
            break;
    }

    if (!_dtlsSrtpTransport->IsWritable(false)) {
        isConnected = false;
    }

    if (_isConnected != isConnected) {
        _isConnected = isConnected;

        GroupNetworkManager::State emitState;
        emitState.isReadyToSendData = isConnected;
        _stateUpdated(emitState);

        if (_dataChannelInterface) {
            _dataChannelInterface->updateIsConnected(isConnected);
        }
    }
}

void GroupNetworkManager::sctpReadyToSendData() {
}

void GroupNetworkManager::sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer) {
}

} // namespace tgcalls
