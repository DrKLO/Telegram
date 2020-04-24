//
// Created by Grishka on 29.03.17.
//

#ifndef LIBTGVOIP_NETWORKSOCKET_H
#define LIBTGVOIP_NETWORKSOCKET_H

#include "Buffers.h"
#include "utils.h"
#include <atomic>
#include <memory>
#include <cstdint>
#include <string>
#include <vector>
#include <list>

namespace tgvoip
{

enum class NetworkProtocol
{
    UDP = 0,
    TCP
};

struct TCPO2State
{
    std::uint8_t key[32];
    std::uint8_t iv[16];
    std::uint8_t ecount[16];
    std::uint32_t num;
};

class NetworkAddress
{
public:
    virtual ~NetworkAddress() = default;

    [[nodiscard]] virtual std::string ToString() const;
    [[nodiscard]] virtual bool IsEmpty() const;
    [[nodiscard]] virtual bool PrefixMatches(const unsigned int prefix, const NetworkAddress& other) const;

    bool operator==(const NetworkAddress& other) const;
    bool operator!=(const NetworkAddress& other) const;

    static NetworkAddress Empty();
    static NetworkAddress IPv4(const std::string& str);
    static NetworkAddress IPv4(std::uint32_t addr);
    static NetworkAddress IPv6(const std::string& str);
    static NetworkAddress IPv6(const std::uint8_t addr[16]);

    union
    {
        std::uint32_t ipv4;
        std::uint8_t ipv6[16];
    } addr;
    bool isIPv6 = false;

private:
    NetworkAddress();
};

struct NetworkPacket
{
    NetworkPacket(Buffer data, const NetworkAddress& address, std::uint16_t port, NetworkProtocol protocol);
    TGVOIP_MOVE_ONLY(NetworkPacket);

    Buffer data;
    NetworkAddress address;
    NetworkProtocol protocol;
    std::uint16_t port;

    static NetworkPacket Empty();
    [[nodiscard]] bool IsEmpty() const;
};

class SocketSelectCanceller
{
public:
    virtual ~SocketSelectCanceller();
    virtual void CancelSelect() = 0;
    static SocketSelectCanceller* Create();
};

class NetworkSocket
{
public:
    friend class NetworkSocketPosix;
    friend class NetworkSocketWinsock;

    TGVOIP_DISALLOW_COPY_AND_ASSIGN(NetworkSocket);
    NetworkSocket(NetworkProtocol m_protocol);
    virtual ~NetworkSocket();
    virtual void Send(NetworkPacket packet) = 0;
    virtual NetworkPacket Receive(std::size_t maxLen) = 0;
    std::size_t Receive(std::uint8_t* buffer, std::size_t len);
    virtual void Open() = 0;
    virtual void Close() = 0;
    virtual std::uint16_t GetLocalPort();
    virtual void Connect(const NetworkAddress& address, std::uint16_t port) = 0;
    virtual std::string GetLocalInterfaceInfo(NetworkAddress* inet4addr, NetworkAddress* inet6addr);
    virtual void OnActiveInterfaceChanged();
    virtual NetworkAddress GetConnectedAddress();
    virtual std::uint16_t GetConnectedPort();
    virtual void SetTimeouts(int sendTimeout, int recvTimeout);

    [[nodiscard]] virtual bool IsFailed() const;
    [[nodiscard]] virtual bool IsReadyToSend() const;
    virtual bool OnReadyToSend();
    virtual bool OnReadyToReceive();
    void SetTimeout(double timeout);

    static NetworkSocket* Create(NetworkProtocol m_protocol);
    static NetworkAddress ResolveDomainName(const std::string& name);
    static bool Select(std::list<NetworkSocket*>& readFds, std::list<NetworkSocket*>& writeFds,
                       std::list<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller);

protected:
    virtual std::uint16_t GenerateLocalPort();
    virtual void SetMaxPriority();

    static void GenerateTCPO2States(std::uint8_t* buffer, TCPO2State* recvState, TCPO2State* sendState);
    static void EncryptForTCPO2(std::uint8_t* buffer, std::size_t len, TCPO2State* state);

    double m_ipv6Timeout;
    double m_lastSuccessfulOperationTime = 0.0;
    double m_timeout = 0.0;
    NetworkProtocol m_protocol;
    std::uint8_t m_nat64Prefix[12];
    std::atomic<bool> m_failed;
    bool m_readyToSend = false;
};

class NetworkSocketWrapper : public NetworkSocket
{
public:
    NetworkSocketWrapper(NetworkProtocol protocol);
    ~NetworkSocketWrapper() override;
    virtual NetworkSocket* GetWrapped() = 0;
    virtual void InitConnection() = 0;
    virtual void SetNonBlocking(bool);
};

class NetworkSocketTCPObfuscated : public NetworkSocketWrapper
{
public:
    NetworkSocketTCPObfuscated(NetworkSocket* m_wrapped);
    ~NetworkSocketTCPObfuscated() override;
    NetworkSocket* GetWrapped() override;
    void InitConnection() override;
    void Send(NetworkPacket packet) override;
    NetworkPacket Receive(std::size_t maxLen) override;
    void Open() override;
    void Close() override;
    void Connect(const NetworkAddress& address, std::uint16_t port) override;
    bool OnReadyToSend() override;

    [[nodiscard]] bool IsFailed() const override;
    [[nodiscard]] bool IsReadyToSend() const override;

private:
    NetworkSocket* m_wrapped;
    TCPO2State m_recvState;
    TCPO2State m_sendState;
    bool m_initialized = false;
};

class NetworkSocketSOCKS5Proxy : public NetworkSocketWrapper
{
public:
    NetworkSocketSOCKS5Proxy(NetworkSocket* m_tcp, NetworkSocket* m_udp, std::string m_username, std::string m_password);
    ~NetworkSocketSOCKS5Proxy() override;
    void Send(NetworkPacket packet) override;
    NetworkPacket Receive(std::size_t maxLen) override;
    void Open() override;
    void Close() override;
    void Connect(const NetworkAddress& address, std::uint16_t port) override;
    NetworkSocket* GetWrapped() override;
    void InitConnection() override;
    NetworkAddress GetConnectedAddress() override;
    std::uint16_t GetConnectedPort() override;
    bool OnReadyToSend() override;
    bool OnReadyToReceive() override;

    [[nodiscard]] bool IsFailed() const override;
    [[nodiscard]] bool NeedSelectForSending() const;

private:
    enum class ConnectionState
    {
        INITIAL,
        WAITING_FOR_AUTH_METHOD,
        WAITING_FOR_AUTH_RESULT,
        WAITING_FOR_COMMAND_RESULT,
        CONNECTED,
    };

    NetworkSocket* m_tcp;
    NetworkSocket* m_udp;
    std::string m_username;
    std::string m_password;
    NetworkAddress m_connectedAddress = NetworkAddress::Empty();
    ConnectionState m_state = ConnectionState::INITIAL;
    std::uint16_t m_connectedPort = 0;

    void SendConnectionCommand();
};

} // namespace tgvoip

#endif // LIBTGVOIP_NETWORKSOCKET_H
