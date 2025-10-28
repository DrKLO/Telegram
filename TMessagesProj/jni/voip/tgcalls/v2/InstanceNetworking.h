#ifndef TGCALLS_INSTANCE_NETWORKING_H
#define TGCALLS_INSTANCE_NETWORKING_H

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

#include "third-party/json11.hpp"

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

class InstanceNetworking {
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
        EncryptionKey encryptionKey;
        bool isOutgoing = false;
        bool enableStunMarking = false;
        bool enableTCP = false;
        bool enableP2P = false;
        std::vector<RtcServer> rtcServers;
        absl::optional<Proxy> proxy;
        std::function<void(const InstanceNetworking::State &)> stateUpdated;
        std::function<void(const cricket::Candidate &)> candidateGathered;
        std::function<void(rtc::CopyOnWriteBuffer const &, bool)> transportMessageReceived;
        std::function<void(rtc::CopyOnWriteBuffer const &, int64_t)> rtcpPacketReceived;
        std::function<void(bool)> dataChannelStateUpdated;
        std::function<void(std::string const &)> dataChannelMessageReceived;
        std::shared_ptr<Threads> threads;
        std::shared_ptr<DirectConnectionChannel> directConnectionChannel;
        std::map<std::string, json11::Json> customParameters;
    };
    
    static webrtc::CryptoOptions getDefaulCryptoOptions();
    static ConnectionDescription::CandidateDescription connectionDescriptionFromCandidate(cricket::Candidate const &candidate);

    virtual ~InstanceNetworking() = default;

    virtual void start() = 0;
    virtual void stop() = 0;

    virtual PeerIceParameters getLocalIceParameters() = 0;
    virtual std::unique_ptr<rtc::SSLFingerprint> getLocalFingerprint() = 0;
    virtual void setRemoteParams(PeerIceParameters const &remoteIceParameters, rtc::SSLFingerprint *fingerprint, std::string const &sslSetup) = 0;
    virtual void addCandidates(std::vector<cricket::Candidate> const &candidates) = 0;

    virtual void sendDataChannelMessage(std::string const &message) = 0;

    virtual webrtc::RtpTransport *getRtpTransport() = 0;
};

} // namespace tgcalls

#endif
