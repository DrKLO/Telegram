#ifndef TGCALLS_NATIVE_NETWORKING_IMPL_H
#define TGCALLS_NATIVE_NETWORKING_IMPL_H

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

#include "Message.h"
#include "ThreadLocalObject.h"
#include "Instance.h"

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

class NativeNetworkingImpl : public sigslot::has_slots<>, public std::enable_shared_from_this<NativeNetworkingImpl> {
public:
    struct RouteDescription {
        explicit RouteDescription(std::string const &localDescription_, std::string const &remoteDescription_) :
        localDescription(localDescription_),
        remoteDescription(remoteDescription_) {
        }
        
        std::string localDescription;
        std::string remoteDescription;
        
        bool operator==(RouteDescription const &rhs) const {
            if (localDescription != rhs.localDescription) {
                return false;
            }
            if (remoteDescription != rhs.remoteDescription) {
                return false;
            }
            
            return true;
        }
        
        bool operator!=(const RouteDescription& rhs) const {
            return !(*this == rhs);
        }
    };
    
    struct ConnectionDescription {
        struct CandidateDescription {
            std::string protocol;
            std::string type;
            std::string address;
            
            bool operator==(CandidateDescription const &rhs) const {
                if (protocol != rhs.protocol) {
                    return false;
                }
                if (type != rhs.type) {
                    return false;
                }
                if (address != rhs.address) {
                    return false;
                }
                
                return true;
            }
            
            bool operator!=(const CandidateDescription& rhs) const {
                return !(*this == rhs);
            }
        };
        
        CandidateDescription local;
        CandidateDescription remote;
        
        bool operator==(ConnectionDescription const &rhs) const {
            if (local != rhs.local) {
                return false;
            }
            if (remote != rhs.remote) {
                return false;
            }
            
            return true;
        }
        
        bool operator!=(const ConnectionDescription& rhs) const {
            return !(*this == rhs);
        }
    };
    
    struct State {
        bool isReadyToSendData = false;
        bool isFailed = false;
        absl::optional<RouteDescription> route;
        absl::optional<ConnectionDescription> connection;
    };
    
    struct Configuration {
        bool isOutgoing = false;
        bool enableStunMarking = false;
        bool enableTCP = false;
        bool enableP2P = false;
        std::vector<RtcServer> rtcServers;
        absl::optional<Proxy> proxy;
        std::function<void(const NativeNetworkingImpl::State &)> stateUpdated;
        std::function<void(const cricket::Candidate &)> candidateGathered;
        std::function<void(rtc::CopyOnWriteBuffer const &, bool)> transportMessageReceived;
        std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> rtcpPacketReceived;
        std::function<void(bool)> dataChannelStateUpdated;
        std::function<void(std::string const &)> dataChannelMessageReceived;
        std::shared_ptr<Threads> threads;
    };
    
    static webrtc::CryptoOptions getDefaulCryptoOptions();
    static ConnectionDescription::CandidateDescription connectionDescriptionFromCandidate(cricket::Candidate const &candidate);

    NativeNetworkingImpl(Configuration &&configuration);
    ~NativeNetworkingImpl();

    void start();
    void stop();

    PeerIceParameters getLocalIceParameters();
    std::unique_ptr<rtc::SSLFingerprint> getLocalFingerprint();
    void setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup);
    void addCandidates(std::vector<cricket::Candidate> const &candidates);

    void sendDataChannelMessage(std::string const &message);

    webrtc::RtpTransport *getRtpTransport();

private:
    void resetDtlsSrtpTransport();
    void checkConnectionTimeout();
    void candidateGathered(cricket::IceTransportInternal *transport, const cricket::Candidate &candidate);
    void candidateGatheringState(cricket::IceTransportInternal *transport);
    void OnTransportWritableState_n(rtc::PacketTransportInternal *transport);
    void OnTransportReceivingState_n(rtc::PacketTransportInternal *transport);
    void transportStateChanged(cricket::IceTransportInternal *transport);
    void transportReadyToSend(cricket::IceTransportInternal *transport);
    void transportRouteChanged(absl::optional<rtc::NetworkRoute> route);
    void candidatePairChanged(cricket::CandidatePairChangeEvent const &event);
    void DtlsReadyToSend(bool DtlsReadyToSend);
    void UpdateAggregateStates_n();
    void RtpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us, bool isUnresolved);
    void OnRtcpPacketReceived_n(rtc::CopyOnWriteBuffer *packet, int64_t packet_time_us);

    void sctpReadyToSendData();
    void sctpDataReceived(const cricket::ReceiveDataParams& params, const rtc::CopyOnWriteBuffer& buffer);
    
    void notifyStateUpdated();

    std::shared_ptr<Threads> _threads;
    bool _isOutgoing = false;
    bool _enableStunMarking = false;
    bool _enableTCP = false;
    bool _enableP2P = false;
    std::vector<RtcServer> _rtcServers;
    absl::optional<Proxy> _proxy;

    std::function<void(const NativeNetworkingImpl::State &)> _stateUpdated;
    std::function<void(const cricket::Candidate &)> _candidateGathered;
    std::function<void(rtc::CopyOnWriteBuffer const &, bool)> _transportMessageReceived;
    std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> _rtcpPacketReceived;
    std::function<void(bool)> _dataChannelStateUpdated;
    std::function<void(std::string const &)> _dataChannelMessageReceived;

    std::unique_ptr<rtc::NetworkMonitorFactory> _networkMonitorFactory;
    std::unique_ptr<rtc::BasicPacketSocketFactory> _socketFactory;
    std::unique_ptr<rtc::BasicNetworkManager> _networkManager;
    std::unique_ptr<webrtc::TurnCustomizer> _turnCustomizer;
    std::unique_ptr<cricket::RelayPortFactoryInterface> _relayPortFactory;
    std::unique_ptr<cricket::BasicPortAllocator> _portAllocator;
    std::unique_ptr<webrtc::AsyncDnsResolverFactoryInterface> _asyncResolverFactory;
    std::unique_ptr<cricket::P2PTransportChannel> _transportChannel;
    std::unique_ptr<cricket::DtlsTransport> _dtlsTransport;
    std::unique_ptr<webrtc::DtlsSrtpTransport> _dtlsSrtpTransport;

    std::unique_ptr<SctpDataChannelProviderInterfaceImpl> _dataChannelInterface;

    rtc::scoped_refptr<rtc::RTCCertificate> _localCertificate;
    PeerIceParameters _localIceParameters;
    absl::optional<PeerIceParameters> _remoteIceParameters;

    bool _isConnected = false;
    bool _isFailed = false;
    int64_t _lastDisconnectedTimestamp = 0;
    absl::optional<RouteDescription> _currentRouteDescription;
    absl::optional<ConnectionDescription> _currentConnectionDescription;
};

} // namespace tgcalls

#endif
