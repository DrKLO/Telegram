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
	virtual void Send(NetworkPacket* packet);
	virtual void Receive(NetworkPacket* packet);
	virtual void Open();
	virtual void Close();
	virtual std::string GetLocalInterfaceInfo(IPv4Address* v4addr, IPv6Address* v6addr);
	virtual void OnActiveInterfaceChanged();
	virtual uint16_t GetLocalPort();
	virtual void Connect(const NetworkAddress* address, uint16_t port);

	static std::string V4AddressToString(uint32_t address);
	static std::string V6AddressToString(const unsigned char address[16]);
	static uint32_t StringToV4Address(std::string address);
	static void StringToV6Address(std::string address, unsigned char* out);
	static IPv4Address* ResolveDomainName(std::string name);
	static bool Select(std::vector<NetworkSocket*>& readFds, std::vector<NetworkSocket*>& writeFds, std::vector<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller);
	virtual NetworkAddress *GetConnectedAddress();
	virtual uint16_t GetConnectedPort();
	virtual void SetTimeouts(int sendTimeout, int recvTimeout);
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
	IPv4Address lastRecvdV4;
	IPv6Address lastRecvdV6;
	bool isAtLeastVista;
	bool closing;
	NetworkAddress* tcpConnectedAddress;
	uint16_t tcpConnectedPort;
	Buffer* pendingOutgoingPacket=NULL;
};

}

#endif //LIBTGVOIP_NETWORKSOCKETWINSOCK_H
