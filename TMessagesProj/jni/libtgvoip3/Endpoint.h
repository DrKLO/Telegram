#ifndef ENDPOINT_H
#define ENDPOINT_H

#include "Buffers.h"
#include "NetworkSocket.h"

#include <cstdint>
#include <memory>
#include <map>

namespace tgvoip
{

// API compatibility
struct IPv4Address
{
    IPv4Address(std::string addr);
    std::string addr;
};
struct IPv6Address
{
    IPv6Address(std::string addr);
    std::string addr;
};

class Endpoint
{
    friend class VoIPController;
    friend class VoIPGroupController;

public:
    enum class Type
    {
        UDP_P2P_INET = 1,
        UDP_P2P_LAN,
        UDP_RELAY,
        TCP_RELAY,
    };

    Endpoint(std::int64_t id, std::uint16_t port, const IPv4Address& address,
             const IPv6Address& v6address, Type type, const std::uint8_t peerTag[16]);
    Endpoint(std::int64_t id, std::uint16_t port, const NetworkAddress& address,
             const NetworkAddress& v6address, Type type, const std::uint8_t peerTag[16]);
    Endpoint();
    ~Endpoint();
    [[nodiscard]] const NetworkAddress& GetAddress() const;
    [[nodiscard]] NetworkAddress& GetAddress();
    [[nodiscard]] bool IsIPv6Only() const;
    [[nodiscard]] std::int64_t CleanID() const;

    std::int64_t id;
    NetworkAddress address;
    NetworkAddress v6address;
    Type type;
    std::uint16_t port;
    std::uint8_t peerTag[16];

private:
    HistoricBuffer<double, 6> m_rtts;
    HistoricBuffer<double, 4> m_selfRtts;
    std::map<std::int64_t, double> m_udpPingTimes;
    std::shared_ptr<NetworkSocket> m_socket = nullptr;

    double m_averageRTT = 0;
    double m_lastPingTime = 0;

    std::uint32_t m_lastPingSeq = 0;
    int m_udpPongCount = 0;
    int m_totalUdpPings = 0;
    int m_totalUdpPingReplies = 0;
};

} // namespace tgvoip

#endif // ENDPOINT_H
