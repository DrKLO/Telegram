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
#include "media/sctp/sctp_transport_factory.h"
#include "modules/rtp_rtcp/source/rtp_utility.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "platform/PlatformInterface.h"
#include "TurnCustomizerImpl.h"
#include "SctpDataChannelProviderInterfaceImpl.h"
#include "StaticThreads.h"

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

class WrappedDtlsSrtpTransport : public webrtc::DtlsSrtpTransport {
public:
    bool _voiceActivity = false;

public:
    WrappedDtlsSrtpTransport(bool rtcp_mux_enabled) :
    webrtc::DtlsSrtpTransport(rtcp_mux_enabled) {

    }

    virtual ~WrappedDtlsSrtpTransport() {
    }

    bool SendRtpPacket(rtc::CopyOnWriteBuffer *packet, const rtc::PacketOptions& options, int flags) override {
        maybeUpdateRtpVoiceActivity(packet, _voiceActivity);
        return webrtc::DtlsSrtpTransport::SendRtpPacket(packet, options, flags);
    }
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
    std::function<void(bool)> dataChannelStateUpdated,
    std::function<void(std::string const &)> dataChannelMessageReceived,
    std::function<void(uint32_t, uint8_t, bool)> audioActivityUpdated,
    std::shared_ptr<Threads> threads) :
_threads(std::move(threads)),
_stateUpdated(std::move(stateUpdated)),
_transportMessageReceived(std::move(transportMessageReceived)),
_dataChannelStateUpdated(dataChannelStateUpdated),
_dataChannelMessageReceived(dataChannelMessageReceived),
_audioActivityUpdated(audioActivityUpdated) {
    assert(_threads->getNetworkThread()->IsCurrent());

    _localIceParameters = PeerIceParameters(rtc::CreateRandomString(cricket::ICE_UFRAG_LENGTH), rtc::CreateRandomString(cricket::ICE_PWD_LENGTH));

    _localCertificate = rtc::RTCCertificateGenerator::GenerateCertificate(rtc::KeyParams(rtc::KT_ECDSA), absl::nullopt);

    _networkMonitorFactory = PlatformInterface::SharedInstance()->createNetworkMonitorFactory();

    _socketFactory.reset(new rtc::BasicPacketSocketFactory(_threads->getNetworkThread()));
    _networkManager = std::make_unique<rtc::BasicNetworkManager>(_networkMonitorFactory.get());
    _asyncResolverFactory = std::make_unique<webrtc::BasicAsyncResolverFactory>();

    _dtlsSrtpTransport = std::make_unique<WrappedDtlsSrtpTransport>(true);
    _dtlsSrtpTransport->SetDtlsTransports(nullptr, nullptr);
    _dtlsSrtpTransport->SetActiveResetSrtpParams(false);
    _dtlsSrtpTransport->SignalReadyToSend.connect(this, &GroupNetworkManager::DtlsReadyToSend);
    _dtlsSrtpTransport->SignalRtpPacketReceived.connect(this, &GroupNetworkManager::RtpPacketReceived_n);

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

    _dtlsTransport->SetDtlsRole(rtc::SSLRole::SSL_SERVER);
    _dtlsTransport->SetLocalCertificate(_localCertificate);

    _dtlsSrtpTransport->SetDtlsTransports(_dtlsTransport.get(), nullptr);
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
    bool didRead = false;
    uint32_t ssrc = 0;
    uint8_t audioLevel = 0;
    bool isSpeech = false;
    maybeReadRtpVoiceActivity(packet, didRead, ssrc, audioLevel, isSpeech);
    if (didRead && ssrc != 0) {
        if (_audioActivityUpdated) {
            _audioActivityUpdated(ssrc, audioLevel, isSpeech);
        }
    }

    if (_transportMessageReceived) {
        _transportMessageReceived(*packet, isUnresolved);
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
