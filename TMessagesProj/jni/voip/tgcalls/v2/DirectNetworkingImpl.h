#ifndef TGCALLS_DIRECT_NETWORKING_IMPL_H
#define TGCALLS_DIRECT_NETWORKING_IMPL_H

#ifdef WEBRTC_WIN
// Compiler errors in conflicting Windows headers if not included here.
#include <winsock2.h>
#endif // WEBRTC_WIN

#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "api/candidate.h"
#include "media/base/media_channel.h"
#include "rtc_base/ssl_fingerprint.h"
#include "pc/sctp_data_channel.h"
#include "p2p/base/port.h"
#include "api/transport/field_trial_based_config.h"

#include <functional>
#include <memory>

#include "InstanceNetworking.h"
#include "Message.h"
#include "ThreadLocalObject.h"
#include "Instance.h"
#include "EncryptedConnection.h"

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
class RelayPortFactoryInterface;
} // namespace cricket

namespace webrtc {
class TurnCustomizer;
class DtlsSrtpTransport;
class RtpTransport;
class AsyncDnsResolverFactoryInterface;
} // namespace webrtc

namespace tgcalls {

struct Message;
class SctpDataChannelProviderInterfaceImpl;
class Threads;
class DirectPacketTransport;
class DirectRtpTransport;

class DirectNetworkingImpl : public InstanceNetworking, public sigslot::has_slots<>, public std::enable_shared_from_this<DirectNetworkingImpl> {
public:
    static webrtc::CryptoOptions getDefaulCryptoOptions();

    DirectNetworkingImpl(Configuration &&configuration);
    ~DirectNetworkingImpl();

    void start();
    void stop();

    PeerIceParameters getLocalIceParameters();
    std::unique_ptr<rtc::SSLFingerprint> getLocalFingerprint();
    void setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup);
    void addCandidates(std::vector<cricket::Candidate> const &candidates);

    void sendDataChannelMessage(std::string const &message);

    webrtc::RtpTransport *getRtpTransport();

private:
    void checkConnectionTimeout();
    void notifyStateUpdated();
    
    void resetDtlsSrtpTransport();
    
    void DtlsReadyToSend(bool DtlsReadyToSend);
    void UpdateAggregateStates_n();
    void OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us);
    void OnTransportWritableState_n(rtc::PacketTransportInternal *transport);
    void OnTransportReceivingState_n(rtc::PacketTransportInternal *transport);
    
private:
    std::shared_ptr<Threads> _threads;
    bool _isOutgoing = false;
    
    rtc::scoped_refptr<rtc::RTCCertificate> _localCertificate;
    std::vector<RtcServer> _rtcServers;
    PeerIceParameters _localIceParameters;
    
    std::unique_ptr<DirectConnectionChannel> _channel;
    
    std::shared_ptr<DirectConnectionChannel> _directConnectionChannel;
    std::shared_ptr<DirectPacketTransport> _packetTransport;
    std::unique_ptr<DirectRtpTransport> _rtpTransport;
    std::unique_ptr<SctpDataChannelProviderInterfaceImpl> _dataChannelInterface;

    std::function<void(const DirectNetworkingImpl::State &)> _stateUpdated;
    std::function<void(rtc::CopyOnWriteBuffer const &, bool)> _transportMessageReceived;
    std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> _rtcpPacketReceived;
    std::function<void(bool)> _dataChannelStateUpdated;
    std::function<void(std::string const &)> _dataChannelMessageReceived;

    bool _transportIsConnected = false;
    bool _isConnected = false;
    bool _isFailed = false;
    int64_t _lastDisconnectedTimestamp = 0;
    absl::optional<RouteDescription> _currentRouteDescription;
    absl::optional<ConnectionDescription> _currentConnectionDescription;
};

} // namespace tgcalls

#endif
