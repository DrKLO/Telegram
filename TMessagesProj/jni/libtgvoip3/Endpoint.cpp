#include "Endpoint.h"
#include "PrivateDefines.h"
#include "VoIPServerConfig.h"

#include <cstring>

using namespace tgvoip;

IPv4Address::IPv4Address(std::string addr)
    : addr(std::move(addr))
{
}

IPv6Address::IPv6Address(std::string addr)
    : addr(std::move(addr))
{
}

Endpoint::Endpoint(std::int64_t id, std::uint16_t port, const IPv4Address& address,
                   const IPv6Address& v6address, Type type, const std::uint8_t peerTag[16])
    : id(id)
    , address(NetworkAddress::IPv4(address.addr))
    , v6address(NetworkAddress::IPv6(v6address.addr))
    , type(type)
    , port(port)
{
    std::memcpy(this->peerTag, peerTag, 16);
    if (type == Type::UDP_RELAY && ServerConfig::GetSharedInstance()->GetBoolean("force_tcp", false))
        this->type = Type::TCP_RELAY;
}

Endpoint::Endpoint(std::int64_t id, std::uint16_t port, const NetworkAddress& address,
                   const NetworkAddress& v6address, Type type, const std::uint8_t peerTag[16])
    : id(id)
    , address(address)
    , v6address(v6address)
    , type(type)
    , port(port)
{
    std::memcpy(this->peerTag, peerTag, 16);
    if (type == Type::UDP_RELAY && ServerConfig::GetSharedInstance()->GetBoolean("force_tcp", false))
        this->type = Type::TCP_RELAY;
}

Endpoint::Endpoint()
    : address(NetworkAddress::Empty())
    , v6address(NetworkAddress::Empty())
{
}

const NetworkAddress& Endpoint::GetAddress() const
{
    return IsIPv6Only() ? v6address : address;
}

NetworkAddress& Endpoint::GetAddress()
{
    return IsIPv6Only() ? v6address : address;
}

bool Endpoint::IsIPv6Only() const
{
    return address.IsEmpty() && !v6address.IsEmpty();
}

std::int64_t Endpoint::CleanID() const
{
    std::int64_t _id = id;
    if (type == Type::TCP_RELAY)
    {
        _id = _id ^ (static_cast<std::int64_t>(FOURCC('T', 'C', 'P', ' ')) << 32);
    }
    if (IsIPv6Only())
    {
        _id = _id ^ (static_cast<std::int64_t>(FOURCC('I', 'P', 'v', '6')) << 32);
    }
    return _id;
}

Endpoint::~Endpoint()
{
    if (m_socket)
    {
        m_socket->Close();
    }
}
