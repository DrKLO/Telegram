//
// Created by Grishka on 29.03.17.
//

#ifndef LIBTGVOIP_NETWORKSOCKET_H
#define LIBTGVOIP_NETWORKSOCKET_H

#include <stdint.h>
#include <string>
#include <vector>
#include "utils.h"

namespace tgvoip {

	enum NetworkProtocol{
		PROTO_UDP=0,
		PROTO_TCP
	};

	struct TCPO2State{
		unsigned char key[32];
		unsigned char iv[16];
		unsigned char ecount[16];
		uint32_t num;
	};

	class NetworkAddress{
	public:
		virtual std::string ToString() const =0;
		bool operator==(const NetworkAddress& other) const;
		bool operator!=(const NetworkAddress& other) const;
		virtual ~NetworkAddress()=default;
		virtual bool IsEmpty() const =0;
		virtual bool PrefixMatches(const unsigned int prefix, const NetworkAddress& other) const =0;
	};

	class IPv4Address : public NetworkAddress{
	public:
		IPv4Address(std::string addr);
		IPv4Address(uint32_t addr);
		IPv4Address();
		virtual std::string ToString() const override;
		uint32_t GetAddress() const;
		virtual bool IsEmpty() const override;
		virtual bool PrefixMatches(const unsigned int prefix, const NetworkAddress& other) const override;

		static const IPv4Address Broadcast(){
			return IPv4Address(0xFFFFFFFF);
		}
	private:
		uint32_t address;
	};

	class IPv6Address : public NetworkAddress{
	public:
		IPv6Address(std::string addr);
		IPv6Address(const uint8_t* addr);
		IPv6Address();
		virtual std::string ToString() const override;
		const uint8_t* GetAddress() const;
		virtual bool IsEmpty() const override;
		virtual bool PrefixMatches(const unsigned int prefix, const NetworkAddress& other) const override;
	private:
		uint8_t address[16];
	};

	struct NetworkPacket{
		unsigned char* data;
		size_t length;
		NetworkAddress* address;
		uint16_t port;
		NetworkProtocol protocol;
	};
	typedef struct NetworkPacket NetworkPacket;

	class SocketSelectCanceller{
	public:
		virtual ~SocketSelectCanceller();
		virtual void CancelSelect()=0;
		static SocketSelectCanceller* Create();
	};

	class NetworkSocket{
	public:
		friend class NetworkSocketPosix;
		friend class NetworkSocketWinsock;

		TGVOIP_DISALLOW_COPY_AND_ASSIGN(NetworkSocket);
		NetworkSocket(NetworkProtocol protocol);
		virtual ~NetworkSocket();
		virtual void Send(NetworkPacket* packet)=0;
		virtual void Receive(NetworkPacket* packet)=0;
		size_t Receive(unsigned char* buffer, size_t len);
		size_t Send(unsigned char* buffer, size_t len);
		virtual void Open()=0;
		virtual void Close()=0;
		virtual uint16_t GetLocalPort(){ return 0; };
		virtual void Connect(const NetworkAddress* address, uint16_t port)=0;
		virtual std::string GetLocalInterfaceInfo(IPv4Address* inet4addr, IPv6Address* inet6addr);
		virtual void OnActiveInterfaceChanged(){};
		virtual NetworkAddress* GetConnectedAddress(){ return NULL; };
		virtual uint16_t GetConnectedPort(){ return 0; };
		virtual void SetTimeouts(int sendTimeout, int recvTimeout){};

		virtual bool IsFailed();
		virtual bool IsReadyToSend(){
			return readyToSend;
		}
		virtual bool OnReadyToSend(){ readyToSend=true; return true; };
		virtual bool OnReadyToReceive(){ return true; };
		void SetTimeout(double timeout){
			this->timeout=timeout;
		};

		static NetworkSocket* Create(NetworkProtocol protocol);
		static IPv4Address* ResolveDomainName(std::string name);
		static bool Select(std::vector<NetworkSocket*>& readFds, std::vector<NetworkSocket*>& writeFds, std::vector<NetworkSocket*>& errorFds, SocketSelectCanceller* canceller);

	protected:
		virtual uint16_t GenerateLocalPort();
		virtual void SetMaxPriority();

		static void GenerateTCPO2States(unsigned char* buffer, TCPO2State* recvState, TCPO2State* sendState);
		static void EncryptForTCPO2(unsigned char* buffer, size_t len, TCPO2State* state);
		double ipv6Timeout;
		unsigned char nat64Prefix[12];
		bool failed;
		bool readyToSend=false;
		double lastSuccessfulOperationTime=0.0;
		double timeout=0.0;
		NetworkProtocol protocol;
	};

	class NetworkSocketWrapper : public NetworkSocket{
	public:
		NetworkSocketWrapper(NetworkProtocol protocol) : NetworkSocket(protocol){};
		virtual ~NetworkSocketWrapper(){};
		virtual NetworkSocket* GetWrapped()=0;
		virtual void InitConnection()=0;
		virtual void SetNonBlocking(bool){};
	};

	class NetworkSocketTCPObfuscated : public NetworkSocketWrapper{
	public:
		NetworkSocketTCPObfuscated(NetworkSocket* wrapped);
		virtual ~NetworkSocketTCPObfuscated();
		virtual NetworkSocket* GetWrapped();
		virtual void InitConnection();
		virtual void Send(NetworkPacket *packet);
		virtual void Receive(NetworkPacket *packet);
		virtual void Open();
		virtual void Close();
		virtual void Connect(const NetworkAddress *address, uint16_t port);
		virtual bool OnReadyToSend();

		virtual bool IsFailed();
		virtual bool IsReadyToSend(){
			return readyToSend && wrapped->IsReadyToSend();
		};

	private:
		NetworkSocket* wrapped;
		TCPO2State recvState;
		TCPO2State sendState;
		bool initialized=false;
	};

	class NetworkSocketSOCKS5Proxy : public NetworkSocketWrapper{
	public:
		NetworkSocketSOCKS5Proxy(NetworkSocket* tcp, NetworkSocket* udp, std::string username, std::string password);
		virtual ~NetworkSocketSOCKS5Proxy();
		virtual void Send(NetworkPacket *packet);
		virtual void Receive(NetworkPacket *packet);
		virtual void Open();
		virtual void Close();
		virtual void Connect(const NetworkAddress *address, uint16_t port);
		virtual NetworkSocket *GetWrapped();
		virtual void InitConnection();
		virtual bool IsFailed();
		virtual NetworkAddress *GetConnectedAddress();
		virtual uint16_t GetConnectedPort();
		virtual bool OnReadyToSend();
		virtual bool OnReadyToReceive();
		
		bool NeedSelectForSending();

	private:
		void SendConnectionCommand();
		enum ConnectionState{
			Initial,
			WaitingForAuthMethod,
			WaitingForAuthResult,
			WaitingForCommandResult,
			Connected
		};
		NetworkSocket* tcp;
		NetworkSocket* udp;
		std::string username;
		std::string password;
		NetworkAddress* connectedAddress;
		uint16_t connectedPort;
		ConnectionState state=ConnectionState::Initial;

		IPv4Address lastRecvdV4;
		IPv6Address lastRecvdV6;
	};

}

#endif //LIBTGVOIP_NETWORKSOCKET_H
