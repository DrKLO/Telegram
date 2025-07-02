#ifndef TGCALLS_GROUP_NETWORK_MANAGER_H
#define TGCALLS_GROUP_NETWORK_MANAGER_H

#ifdef WEBRTC_WIN
// Compiler errors in conflicting Windows headers if not included here.
#include <winsock2.h>
#endif // WEBRTC_WIN

#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "api/candidate.h"
#include "media/base/media_channel.h"
#include "pc/sctp_transport.h"
#include "pc/sctp_data_channel.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"

#include <functional>
#include <memory>

#include "Message.h"
#include "ThreadLocalObject.h"

namespace rtc {
class BasicPacketSocketFactory;
class BasicNetworkManager;
class PacketTransportInternal;
struct NetworkRoute;
} // namespace rtc

namespace cricket {
class BasicPortAllocator;
class P2PTransportChannel;
class IceTransportInternal;
class DtlsTransport;
} // namespace cricket

namespace webrtc {
class BasicAsyncResolverFactory;
class TurnCustomizer;
class DtlsSrtpTransport;
class RtpTransport;
} // namespace webrtc

namespace tgcalls {

struct Message;
class SctpDataChannelProviderInterfaceImpl;
class Threads;

class GroupNetworkManager : public sigslot::has_slots<>, public std::enable_shared_from_this<GroupNetworkManager> {
public:
    struct State {
        bool isReadyToSendData = false;
        bool isFailed = false;
    };
    
    static webrtc::CryptoOptions getDefaulCryptoOptions();

    GroupNetworkManager(
        const webrtc::FieldTrialsView& fieldTrials,
        std::function<void(const State &)> stateUpdated,
        std::function<void(uint32_t, int)> unknownSsrcPacketReceived,
        std::function<void(bool)> dataChannelStateUpdated,
        std::function<void(std::string const &)> dataChannelMessageReceived,
        std::function<void(uint32_t, uint8_t, bool)> audioActivityUpdated,
        bool zeroAudioLevel,
        std::function<void(uint32_t)> anyActivityUpdated,
        std::shared_ptr<Threads> threads);
    ~GroupNetworkManager();

    void start();
    void stop();

    PeerIceParameters getLocalIceParameters();
    std::unique_ptr<rtc::SSLFingerprint> getLocalFingerprint();
    void setRemoteParams(PeerIceParameters const &remoteIceParameters, std::vector<cricket::Candidate> const &iceCandidates, rtc::SSLFingerprint *fingerprint);

    void sendDataChannelMessage(std::string const &message);

    void setOutgoingVoiceActivity(bool isSpeech);

    webrtc::RtpTransport *getRtpTransport();

private:
    void resetDtlsSrtpTransport();
    void restartDataChannel();
    void checkConnectionTimeout();
    void candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
    void candidateGatheringState(cricket::IceTransportInternal *transport);
    void OnTransportWritableState_n(rtc::PacketTransportInternal *transport);
    void OnTransportReceivingState_n(rtc::PacketTransportInternal *transport);
    void transportStateChanged(cricket::IceTransportInternal *transport);
    void transportReadyToSend(cricket::IceTransportInternal *transport);
    void transportPacketReceived(rtc::PacketTransportInternal *transport, const char *bytes, size_t size, const int64_t &timestamp, int unused);
    void DtlsReadyToSend(bool DtlsReadyToSend);
    void UpdateAggregateStates_n();
    void RtpPacketReceived_n(webrtc::RtpPacketReceived const &packet, bool isUnresolved);
    void OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us);

    void sctpReadyToSendData();

    std::shared_ptr<Threads> _threads;
    std::function<void(const GroupNetworkManager::State &)> _stateUpdated;
    std::function<void(uint32_t, int)> _unknownSsrcPacketReceived;
    std::function<void(bool)> _dataChannelStateUpdated;
    std::function<void(std::string const &)> _dataChannelMessageReceived;
    std::function<void(uint32_t, uint8_t, bool)> _audioActivityUpdated;
    bool _zeroAudioLevel = false;
    std::function<void(uint32_t)> _anyActivityUpdated;

    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
    std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
    std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
    std::unique_ptr<webrtc::TurnCustomizer> _turnCustomizer;
    std::unique_ptr<cricket::BasicPortAllocator> _portAllocator;
    std::unique_ptr<webrtc::AsyncDnsResolverFactoryInterface> _asyncResolverFactory;
    std::unique_ptr<cricket::P2PTransportChannel> _transportChannel;
    std::unique_ptr<cricket::DtlsTransport> _dtlsTransport;
    std::unique_ptr<webrtc::DtlsSrtpTransport> _dtlsSrtpTransport;

    std::unique_ptr<SctpDataChannelProviderInterfaceImpl> _dataChannelInterface;

    webrtc::scoped_refptr<rtc::RTCCertificate> _localCertificate;
    PeerIceParameters _localIceParameters;
    absl::optional<PeerIceParameters> _remoteIceParameters;

    bool _isConnected = false;
    int64_t _lastNetworkActivityMs = 0;
};

} // namespace tgcalls

#endif
