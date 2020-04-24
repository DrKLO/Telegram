//
// Created by Grishka on 29.03.17.
//

#include "logging.h"
#include "PrivateDefines.h"
#include "Buffers.h"
#include "NetworkSocket.h"
#include "VoIPController.h"
#include "VoIPServerConfig.h"

#if defined(_WIN32)
#include "os/windows/NetworkSocketWinsock.h"
#include <winsock2.h>
#else
#include "os/posix/NetworkSocketPosix.h"
#endif

#include <algorithm>
#include <cstdlib>
#include <cstring>
#include <stdexcept>

#define MIN_UDP_PORT 16384
#define MAX_UDP_PORT 32768

using namespace tgvoip;

NetworkAddress::NetworkAddress() = default;

NetworkPacket::NetworkPacket(Buffer data, const NetworkAddress& address, std::uint16_t port, NetworkProtocol protocol)
    : data(std::move(data))
    , address(address)
    , protocol(protocol)
    , port(port)
{
}

NetworkPacket NetworkPacket::Empty()
{
    return NetworkPacket{ Buffer(), NetworkAddress::Empty(), 0, NetworkProtocol::UDP };
}

bool NetworkPacket::IsEmpty() const
{
    return data.IsEmpty() || (protocol == NetworkProtocol::UDP && (port == 0 || address.IsEmpty()));
}

NetworkSocket::NetworkSocket(NetworkProtocol protocol)
    : m_ipv6Timeout(ServerConfig::GetSharedInstance()->GetDouble("nat64_fallback_timeout", 3))
    , m_protocol(protocol)
    , m_failed(false)
{
}

NetworkSocket::~NetworkSocket() = default;

std::string NetworkSocket::GetLocalInterfaceInfo(NetworkAddress* inet4addr, NetworkAddress* inet6addr)
{
    return "not implemented";
}

std::uint16_t NetworkSocket::GenerateLocalPort()
{
    std::uint16_t rnd;
    VoIPController::crypto.rand_bytes(reinterpret_cast<std::uint8_t*>(&rnd), 2);
    return static_cast<std::uint16_t>((rnd % (MAX_UDP_PORT - MIN_UDP_PORT)) + MIN_UDP_PORT);
}

void NetworkSocket::SetMaxPriority()
{
}

std::uint16_t NetworkSocket::GetLocalPort()
{
    return 0;
}

void NetworkSocket::OnActiveInterfaceChanged()
{
}

NetworkAddress NetworkSocket::GetConnectedAddress()
{
    return NetworkAddress::Empty();
}

std::uint16_t NetworkSocket::GetConnectedPort()
{
    return 0;
}

void NetworkSocket::SetTimeouts(int sendTimeout, int recvTimeout)
{
}

bool NetworkSocket::IsFailed() const
{
    return m_failed;
}

bool NetworkSocket::IsReadyToSend() const
{
    return m_readyToSend;
}

bool NetworkSocket::OnReadyToSend()
{
    m_readyToSend = true;
    return true;
}

bool NetworkSocket::OnReadyToReceive()
{
    return true;
}

void NetworkSocket::SetTimeout(double timeout)
{
    m_timeout = timeout;
}

NetworkSocket* NetworkSocket::Create(NetworkProtocol protocol)
{
#ifndef _WIN32
    return new NetworkSocketPosix(protocol);
#else
    return new NetworkSocketWinsock(protocol);
#endif
}

NetworkAddress NetworkSocket::ResolveDomainName(const std::string& name)
{
#ifndef _WIN32
    return NetworkSocketPosix::ResolveDomainName(name);
#else
    return NetworkSocketWinsock::ResolveDomainName(name);
#endif
}

void NetworkSocket::GenerateTCPO2States(std::uint8_t* buffer, TCPO2State* recvState, TCPO2State* sendState)
{
    std::memset(recvState, 0, sizeof(TCPO2State));
    std::memset(sendState, 0, sizeof(TCPO2State));

    std::array<std::uint8_t, 64> nonce;

    std::uint32_t *first = reinterpret_cast<std::uint32_t*>(nonce.data()), *second = first + 1;
    std::uint32_t first1 = 0x44414548u, first2 = 0x54534F50u, first3 = 0x20544547u, first4 = 0x20544547u, first5 = 0xEEEEEEEEu;
    std::uint32_t second1 = 0;
    do
    {
        VoIPController::crypto.rand_bytes(nonce.data(), STD_ARRAY_SIZEOF(nonce));
    } while (*first == first1 || *first == first2 || *first == first3 || *first == first4 ||
             *first == first5 || *second == second1 || nonce.front() == 0xEF);

    // prepare encryption key/iv
    std::memcpy(sendState->key, nonce.data() + 8, 32);
    std::memcpy(sendState->iv, nonce.data() + 8 + 32, 16);

    // prepare decryption key/iv
    std::array<char, 48> reversed;

    std::memcpy(reversed.data(), nonce.data() + 8, STD_ARRAY_SIZEOF(reversed));
    std::reverse(reversed.begin(), reversed.end());
    std::memcpy(recvState->key, reversed.data(), 32);
    std::memcpy(recvState->iv, reversed.data() + 32, 16);

    // write protocol identifier
    *reinterpret_cast<std::uint32_t*>(nonce.data() + 56) = 0xEFEFEFEFu;
    std::memcpy(buffer, nonce.data(), 56);
    EncryptForTCPO2(nonce.data(), STD_ARRAY_SIZEOF(nonce), sendState);
    std::memcpy(buffer + 56, nonce.data() + 56, 8);
}

void NetworkSocket::EncryptForTCPO2(std::uint8_t* buffer, std::size_t len, TCPO2State* state)
{
    VoIPController::crypto.aes_ctr_encrypt(buffer, len, state->key, state->iv, state->ecount, &state->num);
}

std::size_t NetworkSocket::Receive(std::uint8_t* buffer, std::size_t len)
{
    NetworkPacket pkt = Receive(len);
    if (pkt.IsEmpty())
        return 0;
    std::size_t actualLen = std::min(len, pkt.data.Length());
    std::memcpy(buffer, *pkt.data, actualLen);
    return actualLen;
}

NetworkSocketWrapper::NetworkSocketWrapper(NetworkProtocol protocol)
    : NetworkSocket(protocol)
{
}

NetworkSocketWrapper::~NetworkSocketWrapper() = default;

void NetworkSocketWrapper::SetNonBlocking(bool)
{
}

bool NetworkAddress::operator==(const NetworkAddress& other) const
{
    if (isIPv6 != other.isIPv6)
        return false;
    if (!isIPv6)
        return addr.ipv4 == other.addr.ipv4;

    return std::memcmp(addr.ipv6, other.addr.ipv6, 16) == 0;
}

bool NetworkAddress::operator!=(const NetworkAddress& other) const
{
    return !(*this == other);
}

std::string NetworkAddress::ToString() const
{
    if (isIPv6)
    {
#ifndef _WIN32
        return NetworkSocketPosix::V6AddressToString(addr.ipv6);
#else
        return NetworkSocketWinsock::V6AddressToString(addr.ipv6);
#endif
    }
#ifndef _WIN32
    return NetworkSocketPosix::V4AddressToString(addr.ipv4);
#else
    return NetworkSocketWinsock::V4AddressToString(addr.ipv4);
#endif
}

bool NetworkAddress::IsEmpty() const
{
    if (isIPv6)
    {
        const std::uint64_t* a = reinterpret_cast<const std::uint64_t*>(addr.ipv6);
        return a[0] == 0LL && a[1] == 0LL;
    }
    return addr.ipv4 == 0;
}

bool NetworkAddress::PrefixMatches(const unsigned int prefix, const NetworkAddress& other) const
{
    if (isIPv6 != other.isIPv6)
        return false;
    if (!isIPv6)
    {
        std::uint32_t mask = 0xFFFFFFFF << (32 - prefix);
        return (addr.ipv4 & mask) == (other.addr.ipv4 & mask);
    }
    return false;
}

NetworkAddress NetworkAddress::Empty()
{
    NetworkAddress addr;
    addr.isIPv6 = false;
    addr.addr.ipv4 = 0;
    return addr;
}

NetworkAddress NetworkAddress::IPv4(const std::string& str)
{
    NetworkAddress addr;
    addr.isIPv6 = false;
#ifndef _WIN32
    addr.addr.ipv4 = NetworkSocketPosix::StringToV4Address(str);
#else
    addr.addr.ipv4 = NetworkSocketWinsock::StringToV4Address(str);
#endif
    return addr;
}

NetworkAddress NetworkAddress::IPv4(std::uint32_t addr)
{
    NetworkAddress a;
    a.isIPv6 = false;
    a.addr.ipv4 = addr;
    return a;
}

NetworkAddress NetworkAddress::IPv6(const std::string& str)
{
    NetworkAddress addr;
    addr.isIPv6 = false;
#ifndef _WIN32
    NetworkSocketPosix::StringToV6Address(str, addr.addr.ipv6);
#else
    NetworkSocketWinsock::StringToV6Address(str, addr.addr.ipv6);
#endif
    return addr;
}

NetworkAddress NetworkAddress::IPv6(const std::uint8_t addr[16])
{
    NetworkAddress a;
    a.isIPv6 = true;
    std::memcpy(a.addr.ipv6, addr, 16);
    return a;
}

bool NetworkSocket::Select(std::list<NetworkSocket*>& readFds, std::list<NetworkSocket*>& writeFds, std::list<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller)
{
#ifndef _WIN32
    return NetworkSocketPosix::Select(readFds, writeFds, errorFds, canceller);
#else
    return NetworkSocketWinsock::Select(readFds, writeFds, errorFds, canceller);
#endif
}

SocketSelectCanceller::~SocketSelectCanceller() = default;

SocketSelectCanceller* SocketSelectCanceller::Create()
{
#ifndef _WIN32
    return new SocketSelectCancellerPosix();
#else
    return new SocketSelectCancellerWin32();
#endif
}

NetworkSocketTCPObfuscated::NetworkSocketTCPObfuscated(NetworkSocket* wrapped)
    : NetworkSocketWrapper(NetworkProtocol::TCP)
    , m_wrapped(wrapped)
{
}

NetworkSocketTCPObfuscated::~NetworkSocketTCPObfuscated()
{
    delete m_wrapped;
}

NetworkSocket* NetworkSocketTCPObfuscated::GetWrapped()
{
    return m_wrapped;
}

void NetworkSocketTCPObfuscated::InitConnection()
{
    Buffer buf(64);
    GenerateTCPO2States(*buf, &m_recvState, &m_sendState);
    m_wrapped->Send(NetworkPacket
    {
        std::move(buf),
        NetworkAddress::Empty(),
        0,
        NetworkProtocol::TCP
    });
}

void NetworkSocketTCPObfuscated::Send(NetworkPacket packet)
{
    BufferOutputStream os(packet.data.Length() + 4);
    std::size_t len = packet.data.Length() / 4;
    if (len < 0x7F)
    {
        os.WriteUInt8(static_cast<std::uint8_t>(len));
    }
    else
    {
        os.WriteUInt8(std::uint8_t{0x7F});
        os.WriteUInt8(static_cast<std::uint8_t>((len >>  0) & 0xFF));
        os.WriteUInt8(static_cast<std::uint8_t>((len >>  8) & 0xFF));
        os.WriteUInt8(static_cast<std::uint8_t>((len >> 16) & 0xFF));
    }
    os.WriteBytes(packet.data);
    EncryptForTCPO2(os.GetBuffer(), os.GetLength(), &m_sendState);
    m_wrapped->Send(NetworkPacket
    {
        Buffer(std::move(os)),
        NetworkAddress::Empty(),
        0,
        NetworkProtocol::TCP
    });
}

bool NetworkSocketTCPObfuscated::OnReadyToSend()
{
    LOGV("TCPO socket ready to send");
    if (!m_initialized)
    {
        LOGV("Initializing TCPO2 connection");
        m_initialized = true;
        InitConnection();
        m_readyToSend = true;
        return false;
    }
    return m_wrapped->OnReadyToSend();
}

NetworkPacket NetworkSocketTCPObfuscated::Receive(std::size_t maxLen)
{
    std::uint8_t len1;
    std::size_t packetLen = 0;
    std::size_t offset = 0;
    std::size_t len;
    len = m_wrapped->Receive(&len1, 1);
    if (len <= 0)
    {
        return NetworkPacket::Empty();
    }
    EncryptForTCPO2(&len1, 1, &m_recvState);

    if (len1 < 0x7F)
    {
        packetLen = static_cast<std::size_t>(len1) * 4;
    }
    else
    {
        std::uint8_t len2[3];
        len = m_wrapped->Receive(len2, 3);
        if (len <= 0)
        {
            return NetworkPacket::Empty();
        }
        EncryptForTCPO2(len2, 3, &m_recvState);
        packetLen = ((static_cast<std::size_t>(len2[0]) <<  0) |
                     (static_cast<std::size_t>(len2[1]) <<  8) |
                     (static_cast<std::size_t>(len2[2]) << 16)) * 4;
    }

    if (packetLen > 1500)
    {
        LOGW("packet too big to fit into buffer (%u vs %u)", static_cast<unsigned>(packetLen), 1500u);
        return NetworkPacket::Empty();
    }
    Buffer buf(packetLen);

    while (offset < packetLen)
    {
        len = m_wrapped->Receive(*buf, packetLen - offset);
        if (len <= 0)
        {
            return NetworkPacket::Empty();
        }
        offset += len;
    }
    EncryptForTCPO2(*buf, packetLen, &m_recvState);
    return NetworkPacket
    {
        std::move(buf),
        m_wrapped->GetConnectedAddress(),
        m_wrapped->GetConnectedPort(),
        NetworkProtocol::TCP
    };
}

void NetworkSocketTCPObfuscated::Open()
{
}

void NetworkSocketTCPObfuscated::Close()
{
    m_wrapped->Close();
}

void NetworkSocketTCPObfuscated::Connect(const NetworkAddress& address, std::uint16_t port)
{
    m_wrapped->Connect(address, port);
}

bool NetworkSocketTCPObfuscated::IsFailed() const
{
    return m_wrapped->IsFailed();
}

bool NetworkSocketTCPObfuscated::IsReadyToSend() const
{
    return m_readyToSend && m_wrapped->IsReadyToSend();
}

NetworkSocketSOCKS5Proxy::NetworkSocketSOCKS5Proxy(NetworkSocket* tcp, NetworkSocket* udp, std::string username, std::string password)
    : NetworkSocketWrapper(udp ? NetworkProtocol::UDP : NetworkProtocol::TCP)
    , m_tcp(tcp)
    , m_udp(udp)
    , m_username(std::move(username))
    , m_password(std::move(password))
{
}

NetworkSocketSOCKS5Proxy::~NetworkSocketSOCKS5Proxy()
{
    delete m_tcp;
}

void NetworkSocketSOCKS5Proxy::Send(NetworkPacket packet)
{
    if (m_protocol == NetworkProtocol::TCP)
    {
        m_tcp->Send(std::move(packet));
    }
    else if (m_protocol == NetworkProtocol::UDP)
    {
        BufferOutputStream out(1500);
        out.WriteInt16(0); // RSV
        out.WriteUInt8(0); // FRAG
        if (!packet.address.isIPv6)
        {
            out.WriteUInt8(1); // ATYP (IPv4)
            out.WriteUInt32(packet.address.addr.ipv4);
        }
        else
        {
            out.WriteUInt8(4); // ATYP (IPv6)
            out.WriteBytes(packet.address.addr.ipv6, 16);
        }
        out.WriteUInt16(htons(packet.port));
        out.WriteBytes(packet.data);
        m_udp->Send(NetworkPacket
        {
            Buffer(std::move(out)),
            m_connectedAddress,
            m_connectedPort,
            NetworkProtocol::UDP
        });
    }
}

NetworkPacket NetworkSocketSOCKS5Proxy::Receive(std::size_t maxLen)
{
    if (m_protocol == NetworkProtocol::TCP)
    {
        NetworkPacket packet = m_tcp->Receive(0);
        packet.address = m_connectedAddress;
        packet.port = m_connectedPort;
        return packet;
    }
    NetworkPacket p = m_udp->Receive(0);
    if (!p.IsEmpty() && p.address == m_connectedAddress && p.port == m_connectedPort)
    {
        BufferInputStream in(p.data);
        in.ReadInt16(); // RSV
        in.ReadUInt8(); // FRAG
        std::uint8_t atyp = in.ReadUInt8();
        NetworkAddress address = NetworkAddress::Empty();
        if (atyp == 1)
        { // IPv4
            address = NetworkAddress::IPv4(in.ReadUInt32());
        }
        else if (atyp == 4)
        { // IPv6
            std::uint8_t addr[16];
            in.ReadBytes(addr, 16);
            address = NetworkAddress::IPv6(addr);
        }
        return NetworkPacket
        {
            Buffer::CopyOf(p.data, in.GetOffset(), in.Remaining()),
            address,
            htons(in.ReadUInt16()),
            m_protocol
        };
    }
    return NetworkPacket::Empty();
}

void NetworkSocketSOCKS5Proxy::Open()
{
}

void NetworkSocketSOCKS5Proxy::Close()
{
    m_tcp->Close();
}

void NetworkSocketSOCKS5Proxy::Connect(const NetworkAddress& address, std::uint16_t port)
{
    m_connectedAddress = address;
    m_connectedPort = port;
}

NetworkSocket* NetworkSocketSOCKS5Proxy::GetWrapped()
{
    return m_protocol == NetworkProtocol::TCP ? m_tcp : m_udp;
}

void NetworkSocketSOCKS5Proxy::InitConnection()
{
}

bool NetworkSocketSOCKS5Proxy::IsFailed() const
{
    return NetworkSocket::IsFailed() || m_tcp->IsFailed();
}

NetworkAddress NetworkSocketSOCKS5Proxy::GetConnectedAddress()
{
    return m_connectedAddress;
}

std::uint16_t NetworkSocketSOCKS5Proxy::GetConnectedPort()
{
    return m_connectedPort;
}

bool NetworkSocketSOCKS5Proxy::OnReadyToSend()
{
    if (m_state == ConnectionState::INITIAL)
    {
        BufferOutputStream p(16);
        p.WriteUInt8(std::uint8_t{5}); // VER
        if (!m_username.empty())
        {
            p.WriteUInt8(std::uint8_t{2}); // NMETHODS
            p.WriteUInt8(std::uint8_t{0}); // no auth
            p.WriteUInt8(std::uint8_t{2}); // user/pass
        }
        else
        {
            p.WriteUInt8(std::uint8_t{1}); // NMETHODS
            p.WriteUInt8(std::uint8_t{0}); // no auth
        }
        m_tcp->Send(NetworkPacket {
            Buffer(std::move(p)),
            NetworkAddress::Empty(),
            0,
            NetworkProtocol::TCP});
        m_state = ConnectionState::WAITING_FOR_AUTH_METHOD;
        return false;
    }
    return m_udp ? m_udp->OnReadyToSend() : m_tcp->OnReadyToSend();
}

bool NetworkSocketSOCKS5Proxy::OnReadyToReceive()
{
    std::uint8_t buf[1024];
    switch (m_state)
    {
    case ConnectionState::INITIAL:
        LOGE("NetworkSocketSOCKS5Proxy: connection state Initial");
        break;
    case ConnectionState::CONNECTED:
        LOGE("NetworkSocketSOCKS5Proxy: connection state Connected");
        break;
    case ConnectionState::WAITING_FOR_AUTH_METHOD:
    {
        std::size_t l = m_tcp->Receive(buf, sizeof(buf));
        if (l < 2 || m_tcp->IsFailed())
        {
            m_failed = true;
            return false;
        }
        BufferInputStream in(buf, l);
        std::uint8_t ver = in.ReadUInt8();
        std::uint8_t chosenMethod = in.ReadUInt8();
        LOGV("socks5: VER=%02X, METHOD=%02X", ver, chosenMethod);
        if (ver != 5)
        {
            LOGW("socks5: incorrect VER in response");
            m_failed = true;
            return false;
        }
        if (chosenMethod == 0)
        {
            // connected, no further auth needed
            SendConnectionCommand();
        }
        else if (chosenMethod == 2 && !m_username.empty())
        {
            BufferOutputStream p(512);
            p.WriteUInt8(std::uint8_t{1}); // VER
            p.WriteUInt8(static_cast<std::uint8_t>(m_username.length() > 255 ? 255 : m_username.length())); // ULEN
            p.WriteBytes(reinterpret_cast<const std::uint8_t*>(m_username.c_str()), m_username.length() > 255 ? 255 : m_username.length()); // UNAME
            p.WriteUInt8(static_cast<std::uint8_t>(m_password.length() > 255 ? 255 : m_password.length())); // PLEN
            p.WriteBytes(reinterpret_cast<const std::uint8_t*>(m_password.c_str()), m_password.length() > 255 ? 255 : m_password.length()); // PASSWD
            m_tcp->Send(NetworkPacket
            {
                Buffer(std::move(p)),
                NetworkAddress::Empty(),
                0,
                NetworkProtocol::TCP
            });
            m_state = ConnectionState::WAITING_FOR_AUTH_RESULT;
        }
        else
        {
            LOGW("socks5: unsupported auth method");
            m_failed = true;
            return false;
        }
        return false;
    }
    case ConnectionState::WAITING_FOR_AUTH_RESULT:
    {
        std::size_t l = m_tcp->Receive(buf, sizeof(buf));
        if (l < 2 || m_tcp->IsFailed())
        {
            m_failed = true;
            return false;
        }
        BufferInputStream in(buf, l);
        std::uint8_t ver = in.ReadUInt8();
        std::uint8_t status = in.ReadUInt8();
        LOGV("socks5: auth response VER=%02X, STATUS=%02X", ver, status);
        if (ver != 1)
        {
            LOGW("socks5: auth response VER is incorrect");
            m_failed = true;
            return false;
        }
        if (status != 0)
        {
            LOGW("socks5: username/password auth failed");
            m_failed = true;
            return false;
        }
        LOGV("socks5: authentication succeeded");
        SendConnectionCommand();
        return false;
    }
    case ConnectionState::WAITING_FOR_COMMAND_RESULT:
    {
        std::size_t l = m_tcp->Receive(buf, sizeof(buf));
        switch (m_protocol)
        {
        case NetworkProtocol::TCP:
        {
            if (l < 2 || m_tcp->IsFailed())
            {
                LOGW("socks5: connect failed")
                m_failed = true;
                return false;
            }
            BufferInputStream in(buf, l);
            std::uint8_t ver = in.ReadUInt8();
            if (ver != 5)
            {
                LOGW("socks5: connect: wrong ver in response");
                m_failed = true;
                return false;
            }
            std::uint8_t rep = in.ReadUInt8();
            if (rep != 0)
            {
                LOGW("socks5: connect: failed with error %02X", rep);
                m_failed = true;
                return false;
            }
            LOGV("socks5: connect succeeded");
            m_state = ConnectionState::CONNECTED;
            m_tcp = new NetworkSocketTCPObfuscated(m_tcp);
            m_readyToSend = true;
            return m_tcp->OnReadyToSend();
        }
        case NetworkProtocol::UDP:
        {
            if (l < 2 || m_tcp->IsFailed())
            {
                LOGW("socks5: udp associate failed");
                m_failed = true;
                return false;
            }
            try
            {
                BufferInputStream in(buf, l);
                std::uint8_t ver = in.ReadUInt8();
                std::uint8_t rep = in.ReadUInt8();
                if (ver != 5)
                {
                    LOGW("socks5: udp associate: wrong ver in response");
                    m_failed = true;
                    return false;
                }
                if (rep != 0)
                {
                    LOGW("socks5: udp associate failed with error %02X", rep);
                    m_failed = true;
                    return false;
                }
                in.ReadUInt8(); // RSV
                std::uint8_t atyp = in.ReadUInt8();
                if (atyp == 1)
                {
                    std::uint32_t addr = in.ReadUInt32();
                    m_connectedAddress = NetworkAddress::IPv4(addr);
                }
                else if (atyp == 3)
                {
                    std::uint8_t len = in.ReadUInt8();
                    char domain[256];
                    std::memset(domain, 0, sizeof(domain));
                    in.ReadBytes(reinterpret_cast<std::uint8_t*>(domain), len);
                    LOGD("address type is domain, address=%s", domain);
                    m_connectedAddress = ResolveDomainName(std::string(domain));
                    if (m_connectedAddress.IsEmpty())
                    {
                        LOGW("socks5: failed to resolve domain name '%s'", domain);
                        m_failed = true;
                        return false;
                    }
                }
                else if (atyp == 4)
                {
                    std::uint8_t addr[16];
                    in.ReadBytes(addr, 16);
                    m_connectedAddress = NetworkAddress::IPv6(addr);
                }
                else
                {
                    LOGW("socks5: unknown address type %d", atyp);
                    m_failed = true;
                    return false;
                }
                m_connectedPort = ntohs(in.ReadUInt16());
                m_state = ConnectionState::CONNECTED;
                m_readyToSend = true;
                LOGV("socks5: udp associate successful, given endpoint %s:%d", m_connectedAddress.ToString().c_str(), m_connectedPort);
            }
            catch (const std::out_of_range& exception)
            {
                LOGW("socks5: udp associate response parse failed.\nwhat():\n%s", exception.what());
                m_failed = true;
            }
            break;
        }
        }
    }
    }
    return m_udp ? m_udp->OnReadyToReceive() : m_tcp->OnReadyToReceive();
}

void NetworkSocketSOCKS5Proxy::SendConnectionCommand()
{
    BufferOutputStream out(1024);
    switch (m_protocol)
    {
    case NetworkProtocol::TCP:
        out.WriteUInt8(5); // VER
        out.WriteUInt8(1); // CMD (CONNECT)
        out.WriteUInt8(0); // RSV
        if (!m_connectedAddress.isIPv6)
        {
            out.WriteUInt8(1); // ATYP (IPv4)
            out.WriteInt32(static_cast<std::int32_t>(m_connectedAddress.addr.ipv4));
        }
        else
        {
            out.WriteUInt8(4); // ATYP (IPv6)
            out.WriteBytes(reinterpret_cast<std::uint8_t*>(m_connectedAddress.addr.ipv6), 16);
        }
        out.WriteUInt16(htons(m_connectedPort)); // DST.PORT
        break;
    case NetworkProtocol::UDP:
        LOGV("Sending udp associate");
        out.WriteUInt8(5); // VER
        out.WriteUInt8(3); // CMD (UDP ASSOCIATE)
        out.WriteUInt8(0); // RSV
        out.WriteUInt8(1); // ATYP (IPv4)
        out.WriteInt32(0); // DST.ADDR
        out.WriteInt16(0); // DST.PORT
        break;
    }
    m_tcp->Send(NetworkPacket
    {
        Buffer(std::move(out)),
        NetworkAddress::Empty(),
        0,
        NetworkProtocol::TCP
    });
    m_state = ConnectionState::WAITING_FOR_COMMAND_RESULT;
}

bool NetworkSocketSOCKS5Proxy::NeedSelectForSending() const
{
    return m_state == ConnectionState::INITIAL || m_state == ConnectionState::CONNECTED;
}
