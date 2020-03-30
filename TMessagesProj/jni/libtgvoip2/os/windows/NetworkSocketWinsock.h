//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_NETWORKSOCKETWINSOCK_H
#define LIBTGVOIP_NETWORKSOCKETWINSOCK_H

#include "../../NetworkSocket.h"
#include <stdint.h>
#include <vector>

namespace tgvoip {
class Buffer;

class SocketSelectCancellerWin32 : public SocketSelectCanceller{
friend class NetworkSocketWinsock;
public:
	SocketSelectCancellerWin32();
	virtual ~SocketSelectCancellerWin32();
	virtual void CancelSelect();
private:
	bool canceled;
};

class NetworkSocketWinsock : public NetworkSocket{
public:
	NetworkSocketWinsock(NetworkProtocol protocol);
	virtual ~NetworkSocketWinsock();
	virtual void Send(NetworkPacket packet) override;
	virtual NetworkPacket Receive(size_t maxLen) override;
	virtual void Open() override;
	virtual void Close() override;
	virtual std::string GetLocalInterfaceInfo(NetworkAddress* v4addr, NetworkAddress* v6addr) override;
	virtual void OnActiveInterfaceChanged() override;
	virtual uint16_t GetLocalPort() override;
	virtual void Connect(const NetworkAddress address, uint16_t port) override;

	static std::string V4AddressToString(uint32_t address);
	static std::string V6AddressToString(const unsigned char address[16]);
	static uint32_t StringToV4Address(std::string address);
	static void StringToV6Address(std::string address, unsigned char* out);
	static NetworkAddress ResolveDomainName(std::string name);
	static bool Select(std::vector<NetworkSocket*>& readFds, std::vector<NetworkSocket*>& writeFds, std::vector<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller);
	virtual NetworkAddress GetConnectedAddress() override;
	virtual uint16_t GetConnectedPort() override;
	virtual void SetTimeouts(int sendTimeout, int recvTimeout) override;
	virtual bool OnReadyToSend() override;

protected:
	virtual void SetMaxPriority();

private:
	static int GetDescriptorFromSocket(NetworkSocket* socket);
	uintptr_t fd;
	bool needUpdateNat64Prefix;
	bool nat64Present;
	double switchToV6at;
	bool isV4Available;
	bool isAtLeastVista;
	bool closing;
	NetworkAddress tcpConnectedAddress=NetworkAddress::Empty();
	uint16_t tcpConnectedPort;
	NetworkPacket pendingOutgoingPacket=NetworkPacket::Empty();
	Buffer recvBuf=Buffer(2048);
};

}

#endif //LIBTGVOIP_NETWORKSOCKETWINSOCK_H
