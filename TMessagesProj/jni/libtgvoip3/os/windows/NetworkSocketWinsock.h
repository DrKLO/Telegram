//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_NETWORKSOCKETWINSOCK_H
#define LIBTGVOIP_NETWORKSOCKETWINSOCK_H

#include "../../NetworkSocket.h"
#include <cstdint>
#include <vector>

namespace tgvoip
{
class Buffer;

class SocketSelectCancellerWin32 : public SocketSelectCanceller
{
    friend class NetworkSocketWinsock;

public:
    SocketSelectCancellerWin32();
    virtual ~SocketSelectCancellerWin32();
    virtual void CancelSelect();

private:
    bool canceled;
};

class NetworkSocketWinsock : public NetworkSocket
{
public:
    NetworkSocketWinsock(NetworkProtocol m_protocol);
    ~NetworkSocketWinsock() override;
    void Send(NetworkPacket packet) override;
    NetworkPacket Receive(std::size_t maxLen) override;
    void Open() override;
    void Close() override;
    std::string GetLocalInterfaceInfo(NetworkAddress* v4addr, NetworkAddress* v6addr) override;
    void OnActiveInterfaceChanged() override;
    std::uint16_t GetLocalPort() override;
    void Connect(const NetworkAddress& address, std::uint16_t port) override;

    static std::string V4AddressToString(std::uint32_t address);
    static std::string V6AddressToString(const std::uint8_t address[16]);
    static std::uint32_t StringToV4Address(std::string address);
    static void StringToV6Address(std::string address, std::uint8_t* out);
    static NetworkAddress ResolveDomainName(std::string name);
    static bool Select(std::vector<NetworkSocket*>& readFds, std::vector<NetworkSocket*>& writeFds, std::vector<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller);
    NetworkAddress GetConnectedAddress() override;
    std::uint16_t GetConnectedPort() override;
    void SetTimeouts(int sendTimeout, int recvTimeout) override;
    bool OnReadyToSend() override;

protected:
    void SetMaxPriority() override;

private:
    static int GetDescriptorFromSocket(NetworkSocket* socket);
    uintptr_t fd;
    bool needUpdateNat64Prefix;
    bool nat64Present;
    double switchToV6at;
    bool isV4Available;
    bool isAtLeastVista;
    bool closing;
    NetworkAddress tcpConnectedAddress = NetworkAddress::Empty();
    std::uint16_t tcpConnectedPort;
    NetworkPacket pendingOutgoingPacket = NetworkPacket::Empty();
    Buffer recvBuf = Buffer(2048);
};

}

#endif //LIBTGVOIP_NETWORKSOCKETWINSOCK_H
