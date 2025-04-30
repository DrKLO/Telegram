#include "v2/DirectNetworkingImpl.h"

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

namespace tgcalls {

namespace {

}

class DirectPacketTransport : public rtc::PacketTransportInternal, public std::enable_shared_from_this<DirectPacketTransport> {
public:
    DirectPacketTransport(
        rtc::Thread *thread,
        EncryptionKey const &encryptionKey,
        std::shared_ptr<DirectConnectionChannel> channel, std::function<void(bool)> &&isConnectedUpdated
    ) :
    _isConnectedUpdated(std::move(isConnectedUpdated)),
    _thread(thread),
    _encryption(
        EncryptedConnection::Type::Transport,
        encryptionKey,
        [=](int delayMs, int cause) {
            assert(false);
        }),
    _channel(channel) {
        assert(_thread->IsCurrent());
    }
    
    virtual ~DirectPacketTransport() {
    }
    
    void start() {
        auto weakSelf = std::weak_ptr<DirectPacketTransport>(shared_from_this());
        _onIncomingPacketToken = _channel->addOnIncomingPacket([weakSelf, thread = _thread](std::shared_ptr<std::vector<uint8_t>> packet) {
            thread->PostTask([weakSelf, packet] {
                auto strongSelf = weakSelf.lock();
                if (!strongSelf) {
                    return;
                }
                strongSelf->processIncomingPacket(packet);
            });
        });
        
        updateState();
        runStateTimer();
    }
    
    void stop() {
        if (!_onIncomingPacketToken.empty()) {
            _channel->removeOnIncomingPacket(_onIncomingPacketToken);
            _onIncomingPacketToken.resize(0);
        }
    }
    
public:
    virtual const std::string &transport_name() const override {
        return name;
    }
    
    // The transport has been established.
    virtual bool writable() const override {
        return _isConnected;
    }
    
    // The transport has received a packet in the last X milliseconds, here X is
    // configured by each implementation.
    virtual bool receiving() const override {
        return _isConnected;
    }
    
    // Attempts to send the given packet.
    // The return value is < 0 on failure. The return value in failure case is not
    // descriptive. Depending on failure cause and implementation details
    // GetError() returns an descriptive errno.h error value.
    // This mimics posix socket send() or sendto() behavior.
    // TODO(johan): Reliable, meaningful, consistent error codes for all
    // implementations would be nice.
    // TODO(johan): Remove the default argument once channel code is updated.
    virtual int SendPacket(const char *data,
                           size_t len,
                           const rtc::PacketOptions& options,
                           int flags = 0) override {
        if (!_isConnected) {
            _lastError = ENOTCONN;
            return -1;
        }
        
        rtc::CopyOnWriteBuffer buffer;
        buffer.AppendData(data, len);
        
        Message message = flags == 0 ? Message { AudioDataMessage { buffer } } : Message { VideoDataMessage { buffer } };
        
        if (const auto prepared = _encryption.prepareForSending(message)) {
            rtc::PacketOptions packetOptions;
            
            rtc::ByteBufferWriter bufferWriter;
            bufferWriter.WriteUInt32((uint32_t)prepared->bytes.size());
            bufferWriter.WriteBytes(reinterpret_cast<const uint8_t *>(prepared->bytes.data()), prepared->bytes.size());
            while (bufferWriter.Length() % 4 != 0) {
                bufferWriter.WriteUInt8(0);
            }
            
            auto packet = std::make_unique<std::vector<uint8_t>>(bufferWriter.Data(), bufferWriter.Data() + bufferWriter.Length());
            _channel->sendPacket(std::move(packet));
        }
        
        rtc::SentPacket sentPacket;
        sentPacket.packet_id = options.packet_id;
        sentPacket.send_time_ms = rtc::TimeMillis();
        SignalSentPacket(this, sentPacket);
        
        return 0;
    }
    
    // Sets a socket option. Note that not all options are
    // supported by all transport types.
    virtual int SetOption(rtc::Socket::Option opt, int value) override {
        return 0;
    }
    
    // TODO(pthatcher): Once Chrome's MockPacketTransportInterface implements
    // this, remove the default implementation.
    virtual bool GetOption(rtc::Socket::Option opt, int *value) override {
        return false;
    }
    
    // Returns the most recent error that occurred on this channel.
    virtual int GetError() override {
        return _lastError;
    }
    
    // Returns the current network route with transport overhead.
    // TODO(zhihuang): Make it pure virtual once the Chrome/remoting is updated.
    virtual absl::optional<rtc::NetworkRoute> network_route() const override {
        return absl::nullopt;
    }
    
    /*sigslot::signal1<PacketTransportInternal*> SignalWritableState;

    //  Emitted when the PacketTransportInternal is ready to send packets. "Ready
    //  to send" is more sensitive than the writable state; a transport may be
    //  writable, but temporarily not able to send packets. For example, the
    //  underlying transport's socket buffer may be full, as indicated by
    //  SendPacket's return code and/or GetError.
    sigslot::signal1<PacketTransportInternal*> SignalReadyToSend;

    // Emitted when receiving state changes to true.
    sigslot::signal1<PacketTransportInternal*> SignalReceivingState;

    // Signalled each time a packet is received on this channel.
    sigslot::signal5<PacketTransportInternal*,
                     const char*,
                     size_t,
                     // TODO(bugs.webrtc.org/9584): Change to passing the int64_t
                     // timestamp by value.
                     const int64_t&,
                     int>
        SignalReadPacket;

    // Signalled each time a packet is sent on this channel.
    sigslot::signal2<PacketTransportInternal*, const rtc::SentPacket&>
        SignalSentPacket;

    // Signalled when the current network route has changed.
    sigslot::signal1<absl::optional<rtc::NetworkRoute>> SignalNetworkRouteChanged;

    // Signalled when the transport is closed.
    sigslot::signal1<PacketTransportInternal*> SignalClosed;*/
    
private:
    void runStateTimer() {
        const auto weakSelf = std::weak_ptr<DirectPacketTransport>(shared_from_this());
        _thread->PostDelayedTask([weakSelf]() {
            auto strongSelf = weakSelf.lock();
            if (!strongSelf) {
                return;
            }

            strongSelf->updateState();
            strongSelf->runStateTimer();
        }, webrtc::TimeDelta::Millis(100));
    }
    
    std::unique_ptr<std::vector<uint8_t>> makeServerHelloPacket() {
        rtc::ByteBufferWriter bufferWriter;
        for (int i = 0; i < 12; i++) {
            bufferWriter.WriteUInt8(0xffu);
        }
        bufferWriter.WriteUInt8(0xfeu);
        for (int i = 0; i < 3; i++) {
            bufferWriter.WriteUInt8(0xffu);
        }
        bufferWriter.WriteUInt64(123);
        
        while (bufferWriter.Length() % 4 != 0) {
            bufferWriter.WriteUInt8(0);
        }
        
        return std::make_unique<std::vector<uint8_t>>(bufferWriter.Data(), bufferWriter.Data() + bufferWriter.Length());
    }
    
    std::unique_ptr<std::vector<uint8_t>> makePingPacket() {
        rtc::ByteBufferWriter bufferWriter;
        bufferWriter.WriteUInt32(_pingMarker);
        
        return std::make_unique<std::vector<uint8_t>>(bufferWriter.Data(), bufferWriter.Data() + bufferWriter.Length());
    }
    
    void updateState() {
        auto timestamp = rtc::TimeMillis();
        
        if (_isConnected && _lastDataReceivedTimestamp < timestamp - _keepalivePingInterval * 2) {
            _isConnected = false;
            SignalWritableState(this);
            
            if (_isConnectedUpdated) {
                _isConnectedUpdated(_isConnected);
            }
        }
        
        if (_isConnected) {
            if (_lastPingSentTimestamp < timestamp - _initialPingInterval) {
                _lastPingSentTimestamp = timestamp;
                
                auto packet = makePingPacket();
                _channel->sendPacket(std::move(packet));
            }
        } else {
            if (_lastPingSentTimestamp < timestamp - _keepalivePingInterval) {
                _lastPingSentTimestamp = timestamp;
                
                if (_hasCompletedHello) {
                    auto packet = makePingPacket();
                    _channel->sendPacket(std::move(packet));
                } else {
                    auto packet = makeServerHelloPacket();
                    _channel->sendPacket(std::move(packet));
                }
            }
        }
    }
    
    void processIncomingPacket(std::shared_ptr<std::vector<uint8_t>> const &packet) {
        rtc::ByteBufferReader reader(rtc::ArrayView<const uint8_t>(reinterpret_cast<const uint8_t *>(packet->data()), packet->size()));
        
        uint32_t header = 0;
        if (!reader.ReadUInt32(&header)) {
            return;
        }
        
        _lastDataReceivedTimestamp = rtc::TimeMillis();
        if (!_isConnected) {
            _isConnected = true;
            SignalWritableState(this);
            SignalReadyToSend(this);
            SignalReceivingState(this);
            
            if (_isConnectedUpdated) {
                _isConnectedUpdated(_isConnected);
            }
        }
        
        if (header == _pingMarker) {
        } else {
            bool isSpecialPacket = false;
            if (packet->size() >= 12) {
                uint8_t specialTag[12];
                memcpy(specialTag, packet->data(), 12);
                
                uint8_t expectedSpecialTag[12];
                memset(expectedSpecialTag, 0xff, 12);
                
                if (memcmp(specialTag, expectedSpecialTag, 12) == 0) {
                    isSpecialPacket = true;
                }
            }
            
            if (!isSpecialPacket) {
                rtc::ByteBufferReader dataPacketReader(rtc::ArrayView<const uint8_t>(reinterpret_cast<const uint8_t *>(packet->data()), packet->size()));
                uint32_t dataSize = 0;
                if (!dataPacketReader.ReadUInt32(&dataSize)) {
                    return;
                }
                if (dataSize > packet->size() - 4) {
                    return;
                }
                
                if (auto decrypted = _encryption.handleIncomingPacket(reinterpret_cast<const char *>(packet->data()) + 4, dataSize)) {
                    handleIncomingMessage(decrypted->main);
                    for (auto &message : decrypted->additional) {
                        handleIncomingMessage(message);
                    }
                    
                    /*if (_transportMessageReceived) {
                        _transportMessageReceived(std::move(decrypted->main));
                        for (auto &message : decrypted->additional) {
                            _transportMessageReceived(std::move(message));
                        }
                    }*/
                } else {
                    RTC_LOG(LS_ERROR) << "DirectPacketTransport: could not decrypt incoming packet";
                }
                
                /*uint32_t dataSize = 0;
                memcpy(&dataSize, packet->data(), 4);
                dataSize = be32toh(dataSize);
                if (dataSize > packet->size() - 4) {
                    RTC_LOG(LS_WARNING) << "DirectPacketTransport: Received data packet with invalid size tag";
                } else {
                    SignalReadPacket(this, reinterpret_cast<const char *>(packet->data() + 4), dataSize, rtc::TimeMicros(), 0);
                }*/
            }
        }
    }
    
    void handleIncomingMessage(DecryptedMessage const &message) {
        const auto data = &message.message.data;
        if (const auto dataMessage = absl::get_if<AudioDataMessage>(data)) {
            SignalReadPacket(this, reinterpret_cast<const char *>(dataMessage->data.data()), dataMessage->data.size(), rtc::TimeMicros(), 0);
        } else if (const auto dataMessage = absl::get_if<VideoDataMessage>(data)) {
            SignalReadPacket(this, reinterpret_cast<const char *>(dataMessage->data.data()), dataMessage->data.size(), rtc::TimeMicros(), 1);
        } else {
            RTC_LOG(LS_INFO) << "DirectPacketTransport: unknown incoming message";
        }
    }
    
private:
    std::string name = "DirectPacketTransport";
    
    std::function<void(bool)> _isConnectedUpdated;
    
    rtc::Thread *_thread = nullptr;
    EncryptedConnection _encryption;
    std::shared_ptr<DirectConnectionChannel> _channel;
    
    std::vector<uint8_t> _onIncomingPacketToken;
    
    int _lastError = 0;
    
    int64_t _lastPingSentTimestamp = 0;
    int64_t _lastDataReceivedTimestamp = 0;
    bool _isConnected = false;
    
    bool _hasCompletedHello = false;
    
    uint32_t _pingMarker = 0xabcd0102;
    int64_t _initialPingInterval = 100;
    int64_t _keepalivePingInterval = 1000;
};

class DirectRtpTransport : public webrtc::RtpTransport {
public:
    explicit DirectRtpTransport() :
    webrtc::RtpTransport(true) {
    }
    
    virtual bool IsSrtpActive() const override {
        return true;
    }
};

DirectNetworkingImpl::DirectNetworkingImpl(Configuration &&configuration) :
_threads(std::move(configuration.threads)),
_isOutgoing(configuration.isOutgoing),
_rtcServers(configuration.rtcServers),
_stateUpdated(std::move(configuration.stateUpdated)),
_transportMessageReceived(std::move(configuration.transportMessageReceived)),
_rtcpPacketReceived(std::move(configuration.rtcpPacketReceived)),
_dataChannelStateUpdated(configuration.dataChannelStateUpdated),
_dataChannelMessageReceived(configuration.dataChannelMessageReceived) {
    assert(_threads->getNetworkThread()->IsCurrent());
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
    
    _rtpTransport = std::make_unique<DirectRtpTransport>();
    
    _rtpTransport->SubscribeReadyToSend(this, [this](bool value) {
        this->DtlsReadyToSend(value);
    });
    _rtpTransport->SubscribeRtcpPacketReceived(this, [this](rtc::CopyOnWriteBuffer *packet, int64_t timestamp) {
        this->OnRtcpPacketReceived_n(packet, timestamp);
    });
    
    _directConnectionChannel = configuration.directConnectionChannel;
    _packetTransport = std::make_shared<DirectPacketTransport>(
        _threads->getNetworkThread(),
        configuration.encryptionKey,
        _directConnectionChannel,
        [this](bool isConnected) {
            this->_transportIsConnected = isConnected;
            this->UpdateAggregateStates_n();
        }
    );
    
    resetDtlsSrtpTransport();
}

DirectNetworkingImpl::~DirectNetworkingImpl() {
    assert(_threads->getNetworkThread()->IsCurrent());

    RTC_LOG(LS_INFO) << "DirectNetworkingImpl::~DirectNetworkingImpl()";

    _rtpTransport.reset();
    _dataChannelInterface.reset();
    _packetTransport.reset();
}

void DirectNetworkingImpl::resetDtlsSrtpTransport() {
    _rtpTransport->SetRtpPacketTransport(_packetTransport.get());
}

void DirectNetworkingImpl::start() {
    const auto weak = std::weak_ptr<DirectNetworkingImpl>(shared_from_this());
    _dataChannelInterface.reset(new SctpDataChannelProviderInterfaceImpl(
        _packetTransport.get(),
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
    
    _packetTransport->start();
}

void DirectNetworkingImpl::stop() {
    _rtpTransport->UnsubscribeReadyToSend(this);
    
    _dataChannelInterface.reset();
    _rtpTransport.reset();
    
    _packetTransport->stop();
    
    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), true);
    
    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);
}

PeerIceParameters DirectNetworkingImpl::getLocalIceParameters() {
    return _localIceParameters;
}

std::unique_ptr<rtc::SSLFingerprint> DirectNetworkingImpl::getLocalFingerprint() {
    auto certificate = _localCertificate;
    if (!certificate) {
        return nullptr;
    }
    return rtc::SSLFingerprint::CreateFromCertificate(*certificate);
}

void DirectNetworkingImpl::setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup) {
}

void DirectNetworkingImpl::addCandidates(std::vector<cricket::Candidate> const &candidates) {
}

void DirectNetworkingImpl::sendDataChannelMessage(std::string const &message) {
    if (_dataChannelInterface) {
        _dataChannelInterface->sendDataChannelMessage(message);
    }
}

webrtc::RtpTransport *DirectNetworkingImpl::getRtpTransport() {
    return _rtpTransport.get();
}

void DirectNetworkingImpl::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<DirectNetworkingImpl>(shared_from_this());
    _threads->getNetworkThread()->PostDelayedTask([weak]() {
        auto strong = weak.lock();
        if (!strong) {
            return;
        }

        int64_t currentTimestamp = rtc::TimeMillis();
        const int64_t maxTimeout = 20000;

        if (!strong->_isConnected && strong->_lastDisconnectedTimestamp + maxTimeout < currentTimestamp) {
            RTC_LOG(LS_INFO) << "DirectNetworkingImpl timeout " << (currentTimestamp - strong->_lastDisconnectedTimestamp) << " ms";
            
            strong->_isFailed = true;
            strong->notifyStateUpdated();
        }

        strong->checkConnectionTimeout();
    }, webrtc::TimeDelta::Millis(1000));
}

void DirectNetworkingImpl::OnTransportWritableState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}
void DirectNetworkingImpl::OnTransportReceivingState_n(rtc::PacketTransportInternal *transport) {
    assert(_threads->getNetworkThread()->IsCurrent());

    UpdateAggregateStates_n();
}

void DirectNetworkingImpl::DtlsReadyToSend(bool isReadyToSend) {
    UpdateAggregateStates_n();

    if (isReadyToSend) {
        const auto weak = std::weak_ptr<DirectNetworkingImpl>(shared_from_this());
        _threads->getNetworkThread()->PostTask([weak]() {
            const auto strong = weak.lock();
            if (!strong) {
                return;
            }
            strong->UpdateAggregateStates_n();
        });
    }
}

void DirectNetworkingImpl::OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us) {
    if (_rtcpPacketReceived) {
        _rtcpPacketReceived(*packet, packet_time_us);
    }
}

void DirectNetworkingImpl::UpdateAggregateStates_n() {
    assert(_threads->getNetworkThread()->IsCurrent());

    bool isConnected = _transportIsConnected;

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

void DirectNetworkingImpl::notifyStateUpdated() {
    DirectNetworkingImpl::State emitState;
    emitState.isReadyToSendData = _isConnected;
    emitState.route = _currentRouteDescription;
    emitState.connection = _currentConnectionDescription;
    emitState.isFailed = _isFailed;
    _stateUpdated(emitState);
}

} // namespace tgcalls
