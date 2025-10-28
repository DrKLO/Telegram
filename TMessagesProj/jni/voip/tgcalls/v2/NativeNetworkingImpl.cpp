#include "v2/NativeNetworkingImpl.h"

#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/client/basic_port_allocator.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "api/packet_socket_factory.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "p2p/base/ice_credentials_iterator.h"
#include "api/jsep_ice_candidate.h"
#include "p2p/base/dtls_transport.h"
#include "p2p/base/dtls_transport_factory.h"
#include "pc/dtls_srtp_transport.h"
#include "pc/dtls_transport.h"
#include "pc/jsep_transport_controller.h"
#include "api/async_dns_resolver.h"

#include "TurnCustomizerImpl.h"
#include "ReflectorRelayPortFactory.h"
#include "SctpDataChannelProviderInterfaceImpl.h"
#include "StaticThreads.h"
#include "platform/PlatformInterface.h"
#include "p2p/base/turn_port.h"

#include "ReflectorPort.h"
#include "FieldTrialsConfig.h"
#include "EncryptedConnection.h"

namespace tgcalls {

namespace {

bool getCustomParameterBool(std::map<std::string, json11::Json> const &parameters, std::string const &name) {
    const auto value = parameters.find(name);
    if (value != parameters.end() && value->second.is_bool() && value->second.bool_value()) {
        return true;
    } else {
        return false;
    }
}

class CryptStringImpl : public rtc::CryptStringImpl {
public:
    CryptStringImpl(std::string const &value) :
    _value(value) {
    }
    
    virtual ~CryptStringImpl() override {
    }
    
    virtual size_t GetLength() const override {
        return _value.size();
    }
    
    virtual void CopyTo(char* dest, bool nullterminate) const override {
        memcpy(dest, _value.data(), _value.size());
        if (nullterminate) {
            dest[_value.size()] = 0;
        }
    }
    virtual std::string UrlEncode() const override {
        return _value;
    }
    virtual CryptStringImpl* Copy() const override {
        return new CryptStringImpl(_value);
    }
    
    virtual void CopyRawTo(std::vector<unsigned char>* dest) const override {
        dest->resize(_value.size());
        memcpy(dest->data(), _value.data(), _value.size());
    }
    
private:
    std::string _value;
};

class WrappedAsyncPacketSocket : public rtc::AsyncPacketSocket {
public:
    WrappedAsyncPacketSocket(std::unique_ptr<rtc::AsyncPacketSocket> &&wrappedSocket) :
    _wrappedSocket(std::move(wrappedSocket)) {
        _wrappedSocket->RegisterReceivedPacketCallback([this](AsyncPacketSocket *socket, rtc::ReceivedPacket const &packet) {
            this->onReadPacket(packet);
        });
        _wrappedSocket->SignalSentPacket.connect(this, &WrappedAsyncPacketSocket::onSentPacket);
        _wrappedSocket->SignalReadyToSend.connect(this, &WrappedAsyncPacketSocket::onReadyToSend);
        _wrappedSocket->SignalAddressReady.connect(this, &WrappedAsyncPacketSocket::onAddressReady);
        _wrappedSocket->SignalConnect.connect(this, &WrappedAsyncPacketSocket::onConnect);
        _wrappedSocket->SubscribeCloseEvent(this, [this](AsyncPacketSocket* socket, int error) { onClose(socket, error); });
    }
    
    virtual ~WrappedAsyncPacketSocket() override {
        _wrappedSocket->DeregisterReceivedPacketCallback();
        _wrappedSocket->SignalSentPacket.disconnect(this);
        _wrappedSocket->SignalReadyToSend.disconnect(this);
        _wrappedSocket->SignalAddressReady.disconnect(this);
        _wrappedSocket->SignalConnect.disconnect(this);
        _wrappedSocket->UnsubscribeCloseEvent(this);
        
        _wrappedSocket.reset();
    }

    virtual rtc::SocketAddress GetLocalAddress() const override {
        return _wrappedSocket->GetLocalAddress();
    }

    virtual rtc::SocketAddress GetRemoteAddress() const override {
        return _wrappedSocket->GetRemoteAddress();
    }

    virtual int Send(const void* pv, size_t cb, const rtc::PacketOptions& options) override {
        return _wrappedSocket->Send(pv, cb, options);
    }
    
    virtual int SendTo(const void* pv,
                       size_t cb,
                       const rtc::SocketAddress& addr,
                       const rtc::PacketOptions& options) override {
        return _wrappedSocket->SendTo(pv, cb, addr, options);
    }

    virtual int Close() override {
        return _wrappedSocket->Close();
    }

    virtual State GetState() const override {
        return _wrappedSocket->GetState();
    }

    virtual int GetOption(rtc::Socket::Option opt, int* value) override {
        return _wrappedSocket->GetOption(opt, value);
    }
    
    virtual int SetOption(rtc::Socket::Option opt, int value) override {
        return _wrappedSocket->SetOption(opt, value);
    }

    virtual int GetError() const override {
        return _wrappedSocket->GetError();
    }
    
    virtual void SetError(int error) override {
        _wrappedSocket->SetError(error);
    }
    
private:
    void onReadPacket(rtc::ReceivedPacket const &packet) {
        NotifyPacketReceived(packet);
    }
    
    void onSentPacket(AsyncPacketSocket *socket, const rtc::SentPacket &packet) {
        SignalSentPacket.emit(this, packet);
    }

    void onReadyToSend(AsyncPacketSocket *socket) {
        SignalReadyToSend.emit(this);
    }
    
    void onAddressReady(AsyncPacketSocket *socket, const rtc::SocketAddress &address) {
        SignalAddressReady.emit(this, address);
    }

    void onConnect(AsyncPacketSocket *socket) {
        SignalConnect.emit(this);
    }
    
    void onClose(AsyncPacketSocket *socket, int value) {
        SignalClose(this, value);
    }
    
private:
    std::unique_ptr<rtc::AsyncPacketSocket> _wrappedSocket;
};

class WrappedBasicPacketSocketFactory : public rtc::PacketSocketFactory {
public:
    WrappedBasicPacketSocketFactory(std::unique_ptr<rtc::BasicPacketSocketFactory> &&impl, bool standaloneReflectorMode) :
    _impl(std::move(impl)),
    _standaloneReflectorMode(standaloneReflectorMode) {
    }

    virtual ~WrappedBasicPacketSocketFactory() {
    }

    virtual rtc::AsyncPacketSocket *CreateUdpSocket(const rtc::SocketAddress& address, uint16_t min_port, uint16_t max_port) override {
        in_addr v4addr;
        inet_pton(AF_INET, "0.1.2.3", &v4addr);
        rtc::IPAddress ipAddress(v4addr);
        if (_standaloneReflectorMode && address.ipaddr() == ipAddress && address.port() != 12345) {
            return nullptr;
        } else {
            rtc::SocketAddress updatedAddress = address;
            if (updatedAddress.port() == 12345) {
                updatedAddress.SetPort(0);
            }
            return _impl->CreateUdpSocket(updatedAddress, min_port, max_port);
        }
    }
    
    virtual rtc::AsyncListenSocket *CreateServerTcpSocket(const rtc::SocketAddress &local_address, uint16_t min_port, uint16_t max_port, int opts) override {
        in_addr v4addr;
        inet_pton(AF_INET, "0.1.2.3", &v4addr);
        rtc::IPAddress ipAddress(v4addr);
        if (_standaloneReflectorMode && local_address.ipaddr() == ipAddress) {
            return nullptr;
        } else {
            return _impl->CreateServerTcpSocket(local_address, min_port, max_port, opts);
        }
    }

    virtual rtc::AsyncPacketSocket *CreateClientTcpSocket(const rtc::SocketAddress &local_address, const rtc::SocketAddress& remote_address, const rtc::ProxyInfo& proxy_info, const std::string &user_agent, const rtc::PacketSocketTcpOptions& tcp_options) override {
        in_addr v4addr;
        inet_pton(AF_INET, "0.1.2.3", &v4addr);
        rtc::IPAddress ipAddress(v4addr);
        if (_standaloneReflectorMode && local_address.ipaddr() == ipAddress) {
            return nullptr;
        } else {
            return _impl->CreateClientTcpSocket(local_address, remote_address, proxy_info, user_agent, tcp_options);
        }
    }

    virtual std::unique_ptr<webrtc::AsyncDnsResolverInterface> CreateAsyncDnsResolver() override {
        return _impl->CreateAsyncDnsResolver();
    }
private:
    std::unique_ptr<rtc::BasicPacketSocketFactory> _impl;
    bool _standaloneReflectorMode = false;
};

class WrappedNetworkManager: public rtc::NetworkManager, public sigslot::has_slots<> {
public:
    WrappedNetworkManager(rtc::NetworkMonitorFactory *networkMonitorFactory, rtc::SocketFactory *socketFactory) {
        in_addr v4addr;
        inet_pton(AF_INET, "0.1.2.3", &v4addr);
        rtc::IPAddress ipAddress(v4addr);
        _sharedReflectorNetwork = std::make_unique<rtc::Network>(
            "shared-reflector-network",
            "shared-reflector-network",
            ipAddress,
            0,
            rtc::AdapterType::ADAPTER_TYPE_UNKNOWN
        );
        _sharedReflectorNetwork->AddIP(ipAddress);
        
        _impl = std::make_unique<rtc::BasicNetworkManager>(networkMonitorFactory, socketFactory);
        
        _impl->SignalNetworksChanged.connect(this, &WrappedNetworkManager::PassthroughSignalNetworksChanged);
        _impl->SignalError.connect(this, &WrappedNetworkManager::PassthroughSignalError);
    }
    
public:
    void PassthroughSignalNetworksChanged() {
        SignalNetworksChanged();
    }
    
    void PassthroughSignalError() {
        SignalError();
    }

    virtual void Initialize() override {
        _impl->Initialize();
    }

    virtual void StartUpdating() override {
        _impl->StartUpdating();
    }
    
    virtual void StopUpdating() override {
        _impl->StopUpdating();
    }

    virtual std::vector<const rtc::Network *> GetNetworks() const override {
        //return _impl->GetNetworks();
        
        std::vector<const rtc::Network *> result;
        result.push_back(_sharedReflectorNetwork.get());
        return result;
    }

    virtual EnumerationPermission enumeration_permission() const override {
        return _impl->enumeration_permission();
    }

    virtual std::vector<const rtc::Network *> GetAnyAddressNetworks() override {
        return _impl->GetAnyAddressNetworks();
    }
    
    virtual void DumpNetworks() override {
        _impl->DumpNetworks();
    }
    
    bool GetDefaultLocalAddress(int family, rtc::IPAddress *ipaddr) const override {
        return _impl->GetDefaultLocalAddress(family, ipaddr);
    }

    webrtc::MdnsResponderInterface* GetMdnsResponder() const override {
        return _impl->GetMdnsResponder();
    }

    virtual void set_vpn_list(const std::vector<rtc::NetworkMask> &vpn) override {
        _impl->set_vpn_list(vpn);
    }
    
private:
    std::unique_ptr<rtc::BasicNetworkManager> _impl;
    std::unique_ptr<rtc::Network> _sharedReflectorNetwork;
};

class MtProtoPacketTransport : public rtc::PacketTransportInternal {
public:
    MtProtoPacketTransport(
        rtc::PacketTransportInternal *rawTransport,
        EncryptionKey encryptionKey
    ) :
    _rawTransport(rawTransport) {
        _rawTransport->SignalWritableState.connect(this, &MtProtoPacketTransport::InternalOnWritableState);
        _rawTransport->SignalReadyToSend.connect(this, &MtProtoPacketTransport::InternalOnReadyToSend);
        _rawTransport->SignalReceivingState.connect(this, &MtProtoPacketTransport::InternalOnReceivingState);
        _rawTransport->SignalReadPacket.connect(this, &MtProtoPacketTransport::InternalOnReadPacket);
        _rawTransport->SignalSentPacket.connect(this, &MtProtoPacketTransport::InternalOnSentPacket);
        _rawTransport->SignalNetworkRouteChanged.connect(this, &MtProtoPacketTransport::InternalOnNetworkRouteChanged);
        _rawTransport->SignalClosed.connect(this, &MtProtoPacketTransport::InternalOnClosed);
        
        _transportEncryption = std::make_unique<EncryptedConnection>(
            EncryptedConnection::Type::Transport,
            encryptionKey,
            [=](int delayMs, int cause) {
            }
        );
    }
    
    virtual ~MtProtoPacketTransport() {
        _rawTransport->SignalWritableState.disconnect(this);
        _rawTransport->SignalReadyToSend.disconnect(this);
        _rawTransport->SignalReceivingState.disconnect(this);
        _rawTransport->SignalReadPacket.disconnect(this);
        _rawTransport->SignalSentPacket.disconnect(this);
        _rawTransport->SignalNetworkRouteChanged.disconnect(this);
        _rawTransport->SignalClosed.disconnect(this);
    }
    
    virtual const std::string& transport_name() const override {
        return _rawTransport->transport_name();
    }
    
    virtual bool writable() const override {
        return _rawTransport->writable();
    }
    
    virtual bool receiving() const override {
        return _rawTransport->receiving();
    }
    
    virtual int SendPacket(
        const char *data,
        size_t len,
        const rtc::PacketOptions &options,
        int flags
    ) override {
        if (flags != 0) {
            rtc::CopyOnWriteBuffer buffer;
            buffer.AppendData((const unsigned char *)data, len);
            SendPacketInternal(buffer, options);
            return 0;
        } else {
            rtc::CopyOnWriteBuffer buffer;
            uint32_t magic = 0xdcdcdcdc; // SCTP
            buffer.AppendData((const unsigned char *)&magic, 4);
            buffer.AppendData((const unsigned char *)data, len);
            SendPacketInternal(buffer, options);
            return 0;
        }
    }
    
    virtual int SetOption(rtc::Socket::Option opt, int value) override {
        return _rawTransport->SetOption(opt, value);
    }
    
    virtual bool GetOption(rtc::Socket::Option opt, int* value) override {
        return _rawTransport->GetOption(opt, value);
    }
    
    virtual int GetError() override {
        return _rawTransport->GetError();
    }
    
    virtual absl::optional<rtc::NetworkRoute> network_route() const override {
        return _rawTransport->network_route();
    }
    
private:
    void InternalOnWritableState(PacketTransportInternal *transport) {
        SignalWritableState(this);
    }
    
    void InternalOnReadyToSend(PacketTransportInternal *transport) {
        SignalReadyToSend(this);
    }
    
    void InternalOnReceivingState(PacketTransportInternal *transport) {
        SignalReceivingState(this);
    }
    
    void InternalOnReadPacket(PacketTransportInternal *transport, const char *data, size_t size, const int64_t &timestamp, int flags) {
        if (const auto packet = _transportEncryption->handleIncomingRawPacket(data, size)) {
            ProcessReadPacketInternal(packet.value().main.message, timestamp);

            for (const auto &additional : packet.value().additional) {
                ProcessReadPacketInternal(additional.message, timestamp);
            }
        }
    }
    
    void InternalOnSentPacket(PacketTransportInternal *transport, const rtc::SentPacket &packet) {
        SignalSentPacket(this, packet);
    }
    
    void InternalOnNetworkRouteChanged(absl::optional<rtc::NetworkRoute> route) {
        SignalNetworkRouteChanged(route);
    }
    
    void InternalOnClosed(PacketTransportInternal *transport) {
        SignalClosed(this);
    }
    
private:
    void SendPacketInternal(rtc::CopyOnWriteBuffer &packet, const rtc::PacketOptions &options) {
        if (const auto encryptedPacket = _transportEncryption->prepareForSendingRawMessage(packet, false)) {
            _rawTransport->SendPacket((const char *)encryptedPacket->bytes.data(), encryptedPacket->bytes.size(), options);
        }
    }
    
    void ProcessReadPacketInternal(rtc::CopyOnWriteBuffer const &data, int64_t timestamp) {
        if (data.size() >= 4) {
            uint32_t header = 0;
            memcpy(&header, data.data(), 4);
            uint32_t magic = 0xdcdcdcdc; // SCTP
            if (header == magic) {
                SignalReadPacket(this, (const char *)(data.data() + 4), data.size() - 4, timestamp, 0);
            } else {
                SignalReadPacket(this, (const char *)data.data(), data.size(), timestamp, 1);
            }
        } else {
            SignalReadPacket(this, (const char *)data.data(), data.size(), timestamp, 1);
        }
    }
    
private:
    rtc::PacketTransportInternal *_rawTransport = nullptr;
    std::unique_ptr<EncryptedConnection> _transportEncryption;
};

class MtProtoRtpTransport : public webrtc::RtpTransport {
public:
    explicit MtProtoRtpTransport(cricket::IceTransportInternal *iceTransport, EncryptionKey encryptionKey) :
    webrtc::RtpTransport(true) {
        _packetTransport = std::make_unique<MtProtoPacketTransport>(iceTransport, encryptionKey);
        SetRtpPacketTransport(_packetTransport.get());
    }
    
    virtual bool IsSrtpActive() const override {
        return true;
    }
    
    virtual void OnWritableState(rtc::PacketTransportInternal *packet_transport) override {
        webrtc::RtpTransport::OnWritableState(packet_transport);
        
        SignalWritableState(packet_transport);
    }
    
public:
    sigslot::signal1<rtc::PacketTransportInternal *> SignalWritableState;
    sigslot::signal1<rtc::PacketTransportInternal *> SignalReceivingState;
    
private:
    std::unique_ptr<MtProtoPacketTransport> _packetTransport;
};

}

InstanceNetworking::ConnectionDescription::CandidateDescription InstanceNetworking::connectionDescriptionFromCandidate(cricket::Candidate const &candidate) {
    InstanceNetworking::ConnectionDescription::CandidateDescription result;
    
    result.type = candidate.type();
    result.protocol = candidate.protocol();
    result.address = candidate.address().ToString();
    
    return result;
}

webrtc::CryptoOptions NativeNetworkingImpl::getDefaulCryptoOptions() {
    auto options = webrtc::CryptoOptions();
    options.srtp.enable_aes128_sha1_80_crypto_cipher = true;
    options.srtp.enable_gcm_crypto_suites = true;
    return options;
}

NativeNetworkingImpl::NativeNetworkingImpl(Configuration &&configuration) :
_threads(std::move(configuration.threads)),
_isOutgoing(configuration.isOutgoing),
_encryptionKey(configuration.encryptionKey),
_enableStunMarking(configuration.enableStunMarking),
_enableTCP(configuration.enableTCP),
_enableP2P(configuration.enableP2P),
_rtcServers(configuration.rtcServers),
_proxy(configuration.proxy),
_customParameters(configuration.customParameters),
_stateUpdated(std::move(configuration.stateUpdated)),
_candidateGathered(std::move(configuration.candidateGathered)),
_transportMessageReceived(std::move(configuration.transportMessageReceived)),
_rtcpPacketReceived(std::move(configuration.rtcpPacketReceived)),
_dataChannelStateUpdated(configuration.dataChannelStateUpdated),
_dataChannelMessageReceived(configuration.dataChannelMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    _underlyingSocketFactory = _threads->getNetworkThread()->socketserver();
    
    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();
    if (getCustomParameterBool(_customParameters, "network_standalone_reflectors")) {
        _socketFactory = std::make_unique<WrappedBasicPacketSocketFactory>(std::make_unique<rtc::BasicPacketSocketFactory>(_threads->getNetworkThread()->socketserver()), true);
        _networkManager = std::make_unique<WrappedNetworkManager>(_networkMonitorFactory.get(), _threads->getNetworkThread()->socketserver());
    } else {
        _socketFactory = std::make_unique<rtc::BasicPacketSocketFactory>(_threads->getNetworkThread()->socketserver());
        _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), _threads->getNetworkThread()->socketserver());
    }
    
    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncDnsResolverFactory>();
    
    if (getCustomParameterBool(_customParameters, "network_use_mtproto")) {
        
    } else {
        _dtlsSrtpTransport = std::make_unique<webrtc::DtlsSrtpTransport>(true, fieldTrialsBasedConfig);
        _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
        _dtlsSrtpTransport->SetActiveResetSrtpParams(false);
        _dtlsSrtpTransport->SubscribeReadyToSend(this, [this](bool value) {
            this->DtlsReadyToSend(value);
        });
        _dtlsSrtpTransport->SubscribeRtcpPacketReceived(this, [this](rtc::CopyOnWriteBuffer *packet, int64_t timestamp) {
            this->OnRtcpPacketReceived_n(packet, timestamp);
        });
    }
    resetDtlsSrtpTransport();
}

NativeNetworkingImpl::~NativeNetworkingImpl() {
    assert(_threads->getNetworkThread()->IsCurrent());

    RTC_LOG(LS_INFO) << "NativeNetworkingImpl::~NativeNetworkingImpl()";

    _mtProtoRtpTransport.reset();
    _dtlsSrtpTransport.reset();
    _dtlsTransport.reset();
    _dataChannelInterface.reset();
    _transportChannel.reset();
    _asyncResolverFactory.reset();
    _portAllocator.reset();
    _networkManager.reset();
    _underlyingSocketFactory = nullptr;
    _socketFactory.reset();
    _networkMonitorFactory.reset();
}

void NativeNetworkingImpl::resetDtlsSrtpTransport() {
    if (_enableStunMarking) {
        _turnCustomizer.reset(new TurnCustomizerImpl());
    }
    
    bool standaloneReflectorMode = getCustomParameterBool(_customParameters, "network_standalone_reflectors");
    
    uint32_t standaloneReflectorRoleId = 0;
    if (standaloneReflectorMode) {
        if (_isOutgoing) {
            standaloneReflectorRoleId = 1;
        } else {
            standaloneReflectorRoleId = 2;
        }
    }
    
    _relayPortFactory.reset(new ReflectorRelayPortFactory(_rtcServers, standaloneReflectorMode, standaloneReflectorRoleId, _underlyingSocketFactory));

    _portAllocator.reset(new cricket::BasicPortAllocator(_networkManager.get(), _socketFactory.get(), _turnCustomizer.get(), _relayPortFactory.get()));

    uint32_t flags = _portAllocator->flags();
    
    if (getCustomParameterBool(_customParameters, "network_use_default_route")) {
        flags |= cricket::PORTALLOCATOR_DISABLE_ADAPTER_ENUMERATION;
    }
    
    if (getCustomParameterBool(_customParameters, "network_enable_shared_socket")) {
        flags |= cricket::PORTALLOCATOR_ENABLE_SHARED_SOCKET;
    }
    
    flags |=
        cricket::PORTALLOCATOR_ENABLE_IPV6 |
        cricket::PORTALLOCATOR_ENABLE_IPV6_ON_WIFI;

    if (!_enableTCP) {
        flags |= cricket::PORTALLOCATOR_DISABLE_TCP;
    }
    
    if (_proxy || !_enableP2P) {
        flags |= cricket::PORTALLOCATOR_DISABLE_UDP;
        flags |= cricket::PORTALLOCATOR_DISABLE_STUN;
        uint32_t candidateFilter = _portAllocator->candidate_filter();
        candidateFilter &= ~(cricket::CF_REFLEXIVE);
        _portAllocator->SetCandidateFilter(candidateFilter);
    }
    
    _portAllocator->set_step_delay(cricket::kMinimumStepDelay);

    _portAllocator->set_flags(flags);
    _portAllocator->Initialize();

    cricket::ServerAddresses stunServers;
    std::vector<cricket::RelayServerConfig> turnServers;

    for (auto &server : _rtcServers) {
        if (server.isTurn) {
            turnServers.push_back(cricket::RelayServerConfig(
                rtc::SocketAddress(server.host, server.port),
                server.login,
                server.password,
                server.isTcp ? cricket::PROTO_TCP : cricket::PROTO_UDP
            ));
        } else {
            rtc::SocketAddress stunAddress = rtc::SocketAddress(server.host, server.port);
            stunServers.insert(stunAddress);
        }
    }

    _portAllocator->SetConfiguration(stunServers, turnServers, 0, webrtc::NO_PRUNE, _turnCustomizer.get());

    webrtc::IceTransportInit iceTransportInit;
    iceTransportInit.set_port_allocator(_portAllocator.get());
    iceTransportInit.set_async_dns_resolver_factory(_asyncResolverFactory.get());
    
    _transportChannel = cricket::P2PTransportChannel::Create("transport", 0, std::move(iceTransportInit));

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_CONTINUALLY;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = cricket::REGATHER_ON_FAILED_NETWORKS_INTERVAL;
    
    if (getCustomParameterBool(_customParameters, "network_skip_initial_ping")) {
        iceConfig.presume_writable_when_fully_relayed = true;
    }
    _transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        _localIceParameters.supportsRenomination
    );

    _transportChannel->SetIceParameters(localIceParameters);
    _transportChannel->SetIceRole(_isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);
    _transportChannel->SetRemoteIceMode(cricket::ICEMODE_FULL);

    _transportChannel->SignalCandidateGathered.connect(this, &NativeNetworkingImpl::candidateGathered);
    _transportChannel->SignalIceTransportStateChanged.connect(this, &NativeNetworkingImpl::transportStateChanged);
    _transportChannel->SetCandidatePairChangeCallback([this](cricket::CandidatePairChangeEvent const &event) {
        this->candidatePairChanged(event);
    });
    _transportChannel->SignalNetworkRouteChanged.connect(this, &NativeNetworkingImpl::transportRouteChanged);

    if (getCustomParameterBool(_customParameters, "network_use_mtproto")) {
        _mtProtoRtpTransport = std::make_unique<MtProtoRtpTransport>(_transportChannel.get(), _encryptionKey);
        
        ((MtProtoRtpTransport *)_mtProtoRtpTransport.get())->SignalWritableState.connect(this, &NativeNetworkingImpl::OnTransportWritableState_n);
        ((MtProtoRtpTransport *)_mtProtoRtpTransport.get())->SignalReceivingState.connect(this, &NativeNetworkingImpl::OnTransportReceivingState_n);
        
        _mtProtoRtpTransport->SubscribeReadyToSend(this, [this](bool value) {
            this->DtlsReadyToSend(value);
        });
        _mtProtoRtpTransport->SubscribeRtcpPacketReceived(this, [this](rtc::CopyOnWriteBuffer *packet, int64_t timestamp) {
            this->OnRtcpPacketReceived_n(packet, timestamp);
        });
    } else {
        webrtc::CryptoOptions cryptoOptions = NativeNetworkingImpl::getDefaulCryptoOptions();
        _dtlsTransport.reset(new cricket::DtlsTransport(_transportChannel.get(), cryptoOptions, nullptr));
        
        _dtlsTransport->SignalWritableState.connect(this, &NativeNetworkingImpl::OnTransportWritableState_n);
        _dtlsTransport->SignalReceivingState.connect(this, &NativeNetworkingImpl::OnTransportReceivingState_n);
        
        _dtlsTransport->SetLocalCertificate(_localCertificate);
        
        _dtlsSrtpTransport->SetDtlsTransports(_dtlsTransport.get(), nullptr);
    }
}

void NativeNetworkingImpl::start() {
    _transportChannel->MaybeStartGathering();

    rtc::PacketTransportInternal *sctpPacketTransport = nullptr;
    if (_mtProtoRtpTransport) {
        sctpPacketTransport = _mtProtoRtpTransport->rtp_packet_transport();
    } else {
        sctpPacketTransport = _dtlsTransport.get();
    }
    
    const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
    _dataChannelInterface.reset(new SctpDataChannelProviderInterfaceImpl(
        sctpPacketTransport,
        _isOutgoing,
        [weak, threads = _threads](bool state) {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->_dataChannelStateUpdated(state);
        },
        [weak, threads = _threads]() {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            //strong->restartDataChannel();
        },
        [weak, threads = _threads](std::string const &message) {
            assert(threads->getNetworkThread()->IsCurrent());
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->_dataChannelMessageReceived(message);
        },
        _threads
    ));
    
    _lastDisconnectedTimestamp = rtc::TimeMillis();
    checkConnectionTimeout();
}

void NativeNetworkingImpl::stop() {
    _transportChannel->SignalCandidateGathered.disconnect(this);
    _transportChannel->SignalIceTransportStateChanged.disconnect(this);
    _transportChannel->SignalReadPacket.disconnect(this);
    _transportChannel->SignalNetworkRouteChanged.disconnect(this);
    
    _dataChannelInterface.reset();
    
    if (_dtlsTransport) {
        _dtlsTransport->SignalWritableState.disconnect(this);
        _dtlsTransport->SignalReceivingState.disconnect(this);
    }
    if (_dtlsSrtpTransport) {
        _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    }
    
    if (_mtProtoRtpTransport) {
        ((MtProtoRtpTransport *)_mtProtoRtpTransport.get())->SignalWritableState.disconnect(this);
        ((MtProtoRtpTransport *)_mtProtoRtpTransport.get())->SignalReceivingState.disconnect(this);
        _mtProtoRtpTransport.reset();
    }
    
    _dtlsTransport.reset();
    _transportChannel.reset();
    _portAllocator.reset();
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
}

PeerIceParameters NativeNetworkingImpl::getLocalIceParameters() {
    return _localIceParameters;
}

std::unique_ptr<rtc::SSLFingerprint> NativeNetworkingImpl::getLocalFingerprint() {
    auto certificate = _localCertificate;
    if (!certificate) {
        return nullptr;
    }
    return rtc::SSLFingerprint::CreateFromCertificate(*certificate);
}

void NativeNetworkingImpl::setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup) {
    _remoteIceParameters = remoteIceParameters;

    cricket::IceParameters parameters(
        remoteIceParameters.ufrag,
        remoteIceParameters.pwd,
        remoteIceParameters.supportsRenomination
    );

    _transportChannel->SetRemoteIceParameters(parameters);

    rtc::SSLRole sslRole;
    if (sslSetup == "active") {
        sslRole = rtc::SSLRole::SSL_SERVER;
    } else if (sslSetup == "passive") {
        sslRole = rtc::SSLRole::SSL_CLIENT;
    } else {
        sslRole = _isOutgoing ? rtc::SSLRole::SSL_CLIENT : rtc::SSLRole::SSL_SERVER;
    }

    if (fingerprint) {
        if (_dtlsTransport) {
            _dtlsTransport->SetRemoteParameters(fingerprint->algorithm, fingerprint->digest.data(), fingerprint->digest.size(), sslRole);
        }
    }
    
    processPendingLocalStandaloneReflectorCandidates();
}

void NativeNetworkingImpl::addCandidates(std::vector<cricket::Candidate> const &candidates) {
    bool standaloneReflectorMode = getCustomParameterBool(_customParameters, "network_standalone_reflectors");
    
    for (const auto &candidate : candidates) {
        if (standaloneReflectorMode) {
            if (absl::EndsWith(candidate.address().hostname(), ".reflector")) {
                continue;
            }
        }
        
        _transportChannel->AddRemoteCandidate(candidate);
    }
}

void NativeNetworkingImpl::sendDataChannelMessage(std::string const &message) {
    if (_dataChannelInterface) {
        _dataChannelInterface->sendDataChannelMessage(message);
    }
}

webrtc::RtpTransport *NativeNetworkingImpl::getRtpTransport() {
    if (_mtProtoRtpTransport) {
        return _mtProtoRtpTransport.get();
    } else {
        return _dtlsSrtpTransport.get();
    }
}

void NativeNetworkingImpl::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
    _threads->getNetworkThread()->PostDelayedTask([weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }

        int64_t currentTimestamp = rtc::TimeMillis();
        const int64_t maxTimeout = 20000;

        if (!strong->_isConnected && strong->_lastDisconnectedTimestamp + maxTimeout < currentTimestamp) {
            RTC_LOG(LS_INFO) << "NativeNetworkingImpl timeout " << (currentTimestamp - strong->_lastDisconnectedTimestamp) << " ms";
            
            strong->_isFailed = true;
            strong->notifyStateUpdated();
        }

        strong->checkConnectionTimeout();
    }, webrtc::TimeDelta::Millis(1000));
}

void NativeNetworkingImpl::candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate) {
    assert(_threads->getNetworkThread()->IsCurrent());

    bool standaloneReflectorMode = getCustomParameterBool(_customParameters, "network_standalone_reflectors");
    if (standaloneReflectorMode && absl::EndsWith(candidate.address().hostname(), ".reflector")) {
        _pendingLocalStandaloneReflectorCandidates.push_back(candidate);
        
        if (_remoteIceParameters) {
            processPendingLocalStandaloneReflectorCandidates();
        }
    } else {
        _candidateGathered(candidate);
    }
}

void NativeNetworkingImpl::processPendingLocalStandaloneReflectorCandidates() {
    if (!_remoteIceParameters) {
        return;
    }
    
    auto candidates = _pendingLocalStandaloneReflectorCandidates;
    _pendingLocalStandaloneReflectorCandidates.clear();
    
    for (const auto &candidate : candidates) {
        auto remoteHostname = candidate.address().hostname();
        if (!remoteHostname.empty()) {
            uint32_t standaloneReflectorRoleId = 0;
            if (_isOutgoing) {
                standaloneReflectorRoleId = 1;
            } else {
                standaloneReflectorRoleId = 2;
            }
            
            std::string prefixFormat = "reflector-";
            std::string suffixFormat = "-" + std::to_string(standaloneReflectorRoleId) + ".reflector";
            if (!absl::StartsWith(remoteHostname, prefixFormat) || !absl::EndsWith(remoteHostname, suffixFormat)) {
                return;
            }
            
            auto startPosition = prefixFormat.size();
            auto tagString = remoteHostname.substr(startPosition, remoteHostname.size() - suffixFormat.size() - startPosition);
            
            std::stringstream tagStringStream(tagString);
            
            uint32_t resolvedServerId = 0;
            tagStringStream >> resolvedServerId;
            
            uint32_t remoteReflectorRoleId = 0;
            if (!_isOutgoing) {
                remoteReflectorRoleId = 1;
            } else {
                remoteReflectorRoleId = 2;
            }
            
            if (resolvedServerId != 0) {
                cricket::Candidate remoteCandidate = candidate;
                rtc::SocketAddress address = remoteCandidate.address();
                const auto remoteHost = "reflector-" + std::to_string(resolvedServerId) + "-" + std::to_string(remoteReflectorRoleId) + ".reflector";
                address.SetIP(remoteHost);
                address.SetResolvedIP(remoteCandidate.address().ipaddr());
                remoteCandidate.set_address(address);
                remoteCandidate.set_username(_remoteIceParameters->ufrag);
                remoteCandidate.set_password(_remoteIceParameters->pwd);
                _transportChannel->AddRemoteCandidate(remoteCandidate);
            }
        }
    }
}

void NativeNetworkingImpl::candidateGatheringState(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void NativeNetworkingImpl::OnTransportWritableState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}
void NativeNetworkingImpl::OnTransportReceivingState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}

void NativeNetworkingImpl::DtlsReadyToSend(bool isReadyToSend) {
    UpdateAggregateStates_n();

    if (isReadyToSend) {
        const auto weak = std::weak_ptr<NativeNetworkingImpl>(shared_from_this());
        _threads->getNetworkThread()->PostTask([weak]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->UpdateAggregateStates_n();
        });
    }
}

void NativeNetworkingImpl::transportStateChanged(cricket::IceTransportInternal *transport) {
    UpdateAggregateStates_n();
}

void NativeNetworkingImpl::transportReadyToSend(cricket::IceTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());
}

void NativeNetworkingImpl::transportRouteChanged(absl::optional<rtc::NetworkRoute> route) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    if (route.has_value()) {
        /*cricket::IceTransportStats iceTransportStats;
        if (_transportChannel->GetStats(&iceTransportStats)) {
        }*/
        
        RTC_LOG(LS_INFO) << "NativeNetworkingImpl route changed: " << route->DebugString();
        
        bool localIsWifi = route->local.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        bool remoteIsWifi = route->remote.adapter_type() == rtc::AdapterType::ADAPTER_TYPE_WIFI;
        
        RTC_LOG(LS_INFO) << "NativeNetworkingImpl is wifi: local=" << localIsWifi << ", remote=" << remoteIsWifi;
        
        std::string localDescription = route->local.uses_turn() ? "turn" : "p2p";
        std::string remoteDescription = route->remote.uses_turn() ? "turn" : "p2p";
        
        RouteDescription routeDescription(localDescription, remoteDescription);
        
        if (!_currentRouteDescription || routeDescription != _currentRouteDescription.value()) {
            _currentRouteDescription = std::move(routeDescription);
            notifyStateUpdated();
        }
    }
}

void NativeNetworkingImpl::candidatePairChanged(cricket::CandidatePairChangeEvent const &event) {
    ConnectionDescription connectionDescription;
    
    connectionDescription.local = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.local);
    connectionDescription.remote = InstanceNetworking::connectionDescriptionFromCandidate(event.selected_candidate_pair.remote);
    
    if (!_currentConnectionDescription || _currentConnectionDescription.value() != connectionDescription) {
        _currentConnectionDescription = std::move(connectionDescription);
        notifyStateUpdated();
    }
}

void NativeNetworkingImpl::RtpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us, bool isUnresolved) {
    if (_transportMessageReceived) {
        _transportMessageReceived(*packet, isUnresolved);
    }
}

void NativeNetworkingImpl::OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us) {
    if (_rtcpPacketReceived) {
        _rtcpPacketReceived(*packet, packet_time_us);
    }
}

void NativeNetworkingImpl::UpdateAggregateStates_n() {
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

    if (_mtProtoRtpTransport) {
        if (!_mtProtoRtpTransport->IsWritable(false)) {
            isConnected = false;
        }
    } else {
        if (!_dtlsSrtpTransport->IsWritable(false)) {
            isConnected = false;
        }
    }

    if (_isConnected != isConnected) {
        _isConnected = isConnected;
        
        if (!isConnected) {
            _lastDisconnectedTimestamp = rtc::TimeMillis();
        }

        notifyStateUpdated();

        if (_dataChannelInterface) {
            _dataChannelInterface->updateIsConnected(isConnected);
        }
    }
}

void NativeNetworkingImpl::notifyStateUpdated() {
    NativeNetworkingImpl::State emitState;
    emitState.isReadyToSendData = _isConnected;
    emitState.route = _currentRouteDescription;
    emitState.connection = _currentConnectionDescription;
    emitState.isFailed = _isFailed;
    _stateUpdated(emitState);
}

void NativeNetworkingImpl::sctpReadyToSendData() {
}

} // namespace tgcalls
