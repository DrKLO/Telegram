#include "group/GroupNetworkManager.h"

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
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "media/sctp/sctp_transport_factory.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "platform/PlatformInterface.h"
#include "TurnCustomizerImpl.h"
#include "SctpDataChannelProviderInterfaceImpl.h"
#include "StaticThreads.h"
#include "call/rtp_packet_sink_interface.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"

namespace tgcalls {

enum {
    kRtcpExpectedVersion = 2,
    kRtcpMinHeaderLength = 4,
    kRtcpMinParseLength = 8,

    kRtpExpectedVersion = 2,
    kRtpMinParseLength = 12
};

static void updateHeaderWithVoiceActivity(rtc::CopyOnWriteBuffer *packet, const uint8_t* ptrRTPDataExtensionEnd, const uint8_t* ptr, bool voiceActivity) {
    while (ptrRTPDataExtensionEnd - ptr > 0) {
        //  0
        //  0 1 2 3 4 5 6 7
        // +-+-+-+-+-+-+-+-+
        // |  ID   |  len  |
        // +-+-+-+-+-+-+-+-+

        // Note that 'len' is the header extension element length, which is the
        // number of bytes - 1.
        const int id = (*ptr & 0xf0) >> 4;
        const int len = (*ptr & 0x0f);
        ptr++;

        if (id == 0) {
            // Padding byte, skip ignoring len.
            continue;
        }

        if (id == 15) {
            RTC_LOG(LS_VERBOSE)
            << "RTP extension header 15 encountered. Terminate parsing.";
            return;
        }

        if (ptrRTPDataExtensionEnd - ptr < (len + 1)) {
            RTC_LOG(LS_WARNING) << "Incorrect one-byte extension len: " << (len + 1)
            << ", bytes left in buffer: "
            << (ptrRTPDataExtensionEnd - ptr);
            return;
        }

        if (id == 1) { // kAudioLevelUri
            uint8_t audioLevel = ptr[0] & 0x7f;
            bool parsedVoiceActivity = (ptr[0] & 0x80) != 0;

            if (parsedVoiceActivity != voiceActivity) {
                ptrdiff_t byteOffset = ptr - packet->data();
                uint8_t *mutableBytes = packet->MutableData();
                uint8_t audioActivityBit = voiceActivity ? 0x80 : 0;
                mutableBytes[byteOffset] = audioLevel | audioActivityBit;
            }
            return;
        }

        ptr += (len + 1);
    }
}

#if 0 // Currently unused.
static void readHeaderVoiceActivity(const uint8_t* ptrRTPDataExtensionEnd, const uint8_t* ptr, bool &didRead, uint8_t &audioLevel, bool &voiceActivity) {
    while (ptrRTPDataExtensionEnd - ptr > 0) {
        //  0
        //  0 1 2 3 4 5 6 7
        // +-+-+-+-+-+-+-+-+
        // |  ID   |  len  |
        // +-+-+-+-+-+-+-+-+

        // Note that 'len' is the header extension element length, which is the
        // number of bytes - 1.
        const int id = (*ptr & 0xf0) >> 4;
        const int len = (*ptr & 0x0f);
        ptr++;

        if (id == 0) {
            // Padding byte, skip ignoring len.
            continue;
        }

        if (id == 15) {
            RTC_LOG(LS_VERBOSE)
            << "RTP extension header 15 encountered. Terminate parsing.";
            return;
        }

        if (ptrRTPDataExtensionEnd - ptr < (len + 1)) {
            RTC_LOG(LS_WARNING) << "Incorrect one-byte extension len: " << (len + 1)
            << ", bytes left in buffer: "
            << (ptrRTPDataExtensionEnd - ptr);
            return;
        }

        if (id == 1) { // kAudioLevelUri
            didRead = true;
            audioLevel = ptr[0] & 0x7f;
            voiceActivity = (ptr[0] & 0x80) != 0;

            return;
        }

        ptr += (len + 1);
    }
}
#endif


static void maybeUpdateRtpVoiceActivity(rtc::CopyOnWriteBuffer *packet, bool voiceActivity) {
    const uint8_t *_ptrRTPDataBegin = packet->data();
    const uint8_t *_ptrRTPDataEnd = packet->data() + packet->size();

    const ptrdiff_t length = _ptrRTPDataEnd - _ptrRTPDataBegin;
    if (length < kRtpMinParseLength) {
        return;
    }

    // Version
    const uint8_t V = _ptrRTPDataBegin[0] >> 6;
    // eXtension
    const bool X = ((_ptrRTPDataBegin[0] & 0x10) == 0) ? false : true;
    const uint8_t CC = _ptrRTPDataBegin[0] & 0x0f;

    const uint8_t PT = _ptrRTPDataBegin[1] & 0x7f;

    const uint8_t* ptr = &_ptrRTPDataBegin[4];

    ptr += 4;

    ptr += 4;

    if (V != kRtpExpectedVersion) {
        return;
    }

    const size_t CSRCocts = CC * 4;

    if ((ptr + CSRCocts) > _ptrRTPDataEnd) {
        return;
    }

    if (PT != 111) {
        return;
    }

    for (uint8_t i = 0; i < CC; ++i) {
        ptr += 4;
    }

    if (X) {
      /* RTP header extension, RFC 3550.
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |      defined by profile       |           length              |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        header extension                       |
      |                             ....                              |
      */
      const ptrdiff_t remain = _ptrRTPDataEnd - ptr;
      if (remain < 4) {
          return;
      }

      uint16_t definedByProfile = webrtc::ByteReader<uint16_t>::ReadBigEndian(ptr);
      ptr += 2;

      // in 32 bit words
      size_t XLen = webrtc::ByteReader<uint16_t>::ReadBigEndian(ptr);
      ptr += 2;
      XLen *= 4;  // in bytes

      if (static_cast<size_t>(remain) < (4 + XLen)) {
          return;
      }
      static constexpr uint16_t kRtpOneByteHeaderExtensionId = 0xBEDE;
      if (definedByProfile == kRtpOneByteHeaderExtensionId) {
          const uint8_t* ptrRTPDataExtensionEnd = ptr + XLen;
          updateHeaderWithVoiceActivity(packet, ptrRTPDataExtensionEnd, ptr, voiceActivity);
      }
    }
}

#if 0 // Currently unused.
static void maybeReadRtpVoiceActivity(rtc::CopyOnWriteBuffer *packet, bool &didRead, uint32_t &ssrc, uint8_t &audioLevel, bool &voiceActivity) {
    const uint8_t *_ptrRTPDataBegin = packet->data();
    const uint8_t *_ptrRTPDataEnd = packet->data() + packet->size();

    const ptrdiff_t length = _ptrRTPDataEnd - _ptrRTPDataBegin;
    if (length < kRtpMinParseLength) {
        return;
    }

    // Version
    const uint8_t V = _ptrRTPDataBegin[0] >> 6;
    // eXtension
    const bool X = ((_ptrRTPDataBegin[0] & 0x10) == 0) ? false : true;
    const uint8_t CC = _ptrRTPDataBegin[0] & 0x0f;

    const uint8_t PT = _ptrRTPDataBegin[1] & 0x7f;

    const uint8_t* ptr = &_ptrRTPDataBegin[4];

    ptr += 4;

    ssrc = webrtc::ByteReader<uint32_t>::ReadBigEndian(ptr);
    ptr += 4;

    if (V != kRtpExpectedVersion) {
        return;
    }

    const size_t CSRCocts = CC * 4;

    if ((ptr + CSRCocts) > _ptrRTPDataEnd) {
        return;
    }

    if (PT != 111) {
        return;
    }

    for (uint8_t i = 0; i < CC; ++i) {
        ptr += 4;
    }

    if (X) {
      /* RTP header extension, RFC 3550.
       0                   1                   2                   3
       0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |      defined by profile       |           length              |
      +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
      |                        header extension                       |
      |                             ....                              |
      */
      const ptrdiff_t remain = _ptrRTPDataEnd - ptr;
      if (remain < 4) {
          return;
      }

      uint16_t definedByProfile = webrtc::ByteReader<uint16_t>::ReadBigEndian(ptr);
      ptr += 2;

      // in 32 bit words
      size_t XLen = webrtc::ByteReader<uint16_t>::ReadBigEndian(ptr);
      ptr += 2;
      XLen *= 4;  // in bytes

      if (static_cast<size_t>(remain) < (4 + XLen)) {
          return;
      }
      static constexpr uint16_t kRtpOneByteHeaderExtensionId = 0xBEDE;
      if (definedByProfile == kRtpOneByteHeaderExtensionId) {
          const uint8_t* ptrRTPDataExtensionEnd = ptr + XLen;
          readHeaderVoiceActivity(ptrRTPDataExtensionEnd, ptr, didRead, audioLevel, voiceActivity);
      }
    }
}
#endif

class WrappedDtlsSrtpTransport : public webrtc::DtlsSrtpTransport {
public:
    bool _voiceActivity = false;

public:
    WrappedDtlsSrtpTransport(bool rtcp_mux_enabled, const webrtc::WebRtcKeyValueConfig& fieldTrials, std::function<void(webrtc::RtpPacketReceived const &, bool)> &&processRtpPacket) :
    webrtc::DtlsSrtpTransport(rtcp_mux_enabled, fieldTrials),
    _processRtpPacket(std::move(processRtpPacket)) {
    }

    virtual ~WrappedDtlsSrtpTransport() {
    }

    bool SendRtpPacket(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options, int flags) override {
        maybeUpdateRtpVoiceActivity(packet, _voiceActivity);
        return webrtc::DtlsSrtpTransport::SendRtpPacket(packet, options, flags);
    }

    void ProcessRtpPacket(webrtc::RtpPacketReceived const &packet, bool isUnresolved) override {
        _processRtpPacket(packet, isUnresolved);
    }

private:
    std::function<void(webrtc::RtpPacketReceived const &, bool)> _processRtpPacket;
};

webrtc::CryptoOptions GroupNetworkManager::getDefaulCryptoOptions() {
    auto options = webrtc::CryptoOptions();
    options.srtp.enable_aes128_sha1_80_crypto_cipher = false;
    options.srtp.enable_gcm_crypto_suites = true;
    return options;
}

GroupNetworkManager::GroupNetworkManager(
    const webrtc::WebRtcKeyValueConfig& fieldTrials,
    std::function<void(const State &)> stateUpdated,
    std::function<void(uint32_t, int)> unknownSsrcPacketReceived,
    std::function<void(bool)> dataChannelStateUpdated,
    std::function<void(std::string const &)> dataChannelMessageReceived,
    std::function<void(uint32_t, uint8_t, bool)> audioActivityUpdated,
    std::shared_ptr<Threads> threads) :
_threads(std::move(threads)),
_stateUpdated(std::move(stateUpdated)),
_unknownSsrcPacketReceived(std::move(unknownSsrcPacketReceived)),
_dataChannelStateUpdated(dataChannelStateUpdated),
_dataChannelMessageReceived(dataChannelMessageReceived),
_audioActivityUpdated(audioActivityUpdated) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), false);

    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);

    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();

    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_threads->getNetworkThread()->socketserver()));
    _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get(), _threads->getNetworkThread()->socketserver());
    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncResolverFactory>();

    _dtlsSrtpTransport = std::make_unique<WrappedDtlsSrtpTransport>(true, fieldTrials, [this](webrtc::RtpPacketReceived const &packet, bool isUnresolved) {
        this->RtpPacketReceived_n(packet, isUnresolved);
    });
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    _dtlsSrtpTransport->SetActiveResetSrtpParams(false);
    _dtlsSrtpTransport->SignalReadyToSend.connect(this, &GroupNetworkManager::DtlsReadyToSend);
    //_dtlsSrtpTransport->SignalRtpPacketReceived.connect(this, &GroupNetworkManager::RtpPacketReceived_n);

    resetDtlsSrtpTransport();
}

GroupNetworkManager::~GroupNetworkManager() {
    assert(_threads->getNetworkThread()->IsCurrent());

    RTC_LOG(LS_INFO) << "GroupNetworkManager::~GroupNetworkManager()";

    _dataChannelInterface.reset();
    _dtlsSrtpTransport.reset();
    _dtlsTransport.reset();
    _transportChannel.reset();
    _asyncResolverFactory.reset();
    _portAllocator.reset();
    _networkManager.reset();
    _socketFactory.reset();
}

void GroupNetworkManager::resetDtlsSrtpTransport() {
    std::unique_ptr<cricket::BasicPortAllocator> portAllocator = std::make_unique<cricket::BasicPortAllocator>(_networkManager.get(), _socketFactory.get(), _turnCustomizer.get(), nullptr);
    portAllocator->set_flags(portAllocator->flags());
    portAllocator->Initialize();

    portAllocator->SetConfiguration({}, {}, 2, webrtc::NO_PRUNE, _turnCustomizer.get());

    webrtc::IceTransportInit iceTransportInit;
    iceTransportInit.set_port_allocator(portAllocator.get());
    iceTransportInit.set_async_resolver_factory(_asyncResolverFactory.get());

    auto transportChannel = cricket::P2PTransportChannel::Create("transport", 0, std::move(iceTransportInit));

    cricket::IceConfig iceConfig;
    iceConfig.continual_gathering_policy = cricket::GATHER_CONTINUALLY;
    iceConfig.prioritize_most_likely_candidate_pairs = true;
    iceConfig.regather_on_failed_networks_interval = 2000;
    transportChannel->SetIceConfig(iceConfig);

    cricket::IceParameters localIceParameters(
        _localIceParameters.ufrag,
        _localIceParameters.pwd,
        false
    );

    transportChannel->SetIceParameters(localIceParameters);
    const bool isOutgoing = false;
    transportChannel->SetIceRole(isOutgoing ? cricket::ICEROLE_CONTROLLING : cricket::ICEROLE_CONTROLLED);
    transportChannel->SetRemoteIceMode(cricket::ICEMODE_LITE);

    transportChannel->SignalIceTransportStateChanged.connect(this, &GroupNetworkManager::transportStateChanged);
    transportChannel->SignalReadPacket.connect(this, &GroupNetworkManager::transportPacketReceived);

    webrtc::CryptoOptions cryptoOptions = GroupNetworkManager::getDefaulCryptoOptions();

    auto dtlsTransport = std::make_unique<cricket::DtlsTransport>(transportChannel.get(), cryptoOptions, nullptr);

    dtlsTransport->SignalWritableState.connect(
        this, &GroupNetworkManager::OnTransportWritableState_n);
    dtlsTransport->SignalReceivingState.connect(
        this, &GroupNetworkManager::OnTransportReceivingState_n);

    dtlsTransport->SetDtlsRole(rtc::SSLRole::SSL_SERVER);
    dtlsTransport->SetLocalCertificate(_localCertificate);

    _dtlsSrtpTransport->SetDtlsTransports(dtlsTransport.get(), nullptr);

    _dtlsTransport = std::move(dtlsTransport);
    _transportChannel = std::move(transportChannel);
    _portAllocator = std::move(portAllocator);
}

void GroupNetworkManager::start() {
    _transportChannel->MaybeStartGathering();

    restartDataChannel();
}

void GroupNetworkManager::restartDataChannel() {
    _dataChannelStateUpdated(false);

    const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
    _dataChannelInterface.reset(new SctpDataChannelProviderInterfaceImpl(
        _dtlsTransport.get(),
        true,
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
            strong->restartDataChannel();
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

    _dataChannelInterface->updateIsConnected(_isConnected);
}

void GroupNetworkManager::stop() {
    _transportChannel->SignalIceTransportStateChanged.disconnect(this);
    _transportChannel->SignalReadPacket.disconnect(this);

    _dtlsTransport->SignalWritableState.disconnect(this);
    _dtlsTransport->SignalReceivingState.disconnect(this);

    _dataChannelInterface.reset();

    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH), false);

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

void GroupNetworkManager::setOutgoingVoiceActivity(bool isSpeech) {
    if (_dtlsSrtpTransport) {
        ((WrappedDtlsSrtpTransport *)_dtlsSrtpTransport.get())->_voiceActivity = isSpeech;
    }
}

webrtc::RtpTransport *GroupNetworkManager::getRtpTransport() {
    return _dtlsSrtpTransport.get();
}

void GroupNetworkManager::checkConnectionTimeout() {
    const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
    _threads->getNetworkThread()->PostDelayedTask([weak]() {
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
    }, webrtc::TimeDelta::Millis(1000));
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

void GroupNetworkManager::DtlsReadyToSend(bool isReadyToSend) {
    UpdateAggregateStates_n();

    if (isReadyToSend) {
        const auto weak = std::weak_ptr<GroupNetworkManager>(shared_from_this());
        _threads->getNetworkThread()->PostTask([weak]() {
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

void GroupNetworkManager::RtpPacketReceived_n(webrtc::RtpPacketReceived const &packet, bool isUnresolved) {
    if (packet.HasExtension(webrtc::kRtpExtensionAudioLevel)) {
        uint8_t audioLevel = 0;
        bool isSpeech = false;

        if (packet.GetExtension<webrtc::AudioLevel>(&isSpeech, &audioLevel)) {
            if (_audioActivityUpdated) {
                _audioActivityUpdated(packet.Ssrc(), audioLevel, isSpeech);
            }
        }
    }

    if (isUnresolved && _unknownSsrcPacketReceived) {
        uint32_t ssrc = packet.Ssrc();
        int payloadType = packet.PayloadType();

        _unknownSsrcPacketReceived(ssrc, payloadType);
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
