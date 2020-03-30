//
// Created by Grishka on 29.03.17.
//

#include "NetworkSocket.h"
#include <stdexcept>
#include <algorithm>
#include <stdlib.h>
#include <string.h>
#if defined(_WIN32)
#include "os/windows/NetworkSocketWinsock.h"
#include <winsock2.h>
#else
#include "os/posix/NetworkSocketPosix.h"
#endif
#include "logging.h"
#include "VoIPServerConfig.h"
#include "VoIPController.h"
#include "Buffers.h"

#define MIN_UDP_PORT 16384
#define MAX_UDP_PORT 32768

using namespace tgvoip;

NetworkSocket::NetworkSocket(NetworkProtocol protocol) : protocol(protocol){
	ipv6Timeout=ServerConfig::GetSharedInstance()->GetDouble("nat64_fallback_timeout", 3);
	failed=false;
}

NetworkSocket::~NetworkSocket(){

}

std::string NetworkSocket::GetLocalInterfaceInfo(IPv4Address *inet4addr, IPv6Address *inet6addr){
	std::string r="not implemented";
	return r;
}

uint16_t NetworkSocket::GenerateLocalPort(){
	uint16_t rnd;
	VoIPController::crypto.rand_bytes(reinterpret_cast<uint8_t*>(&rnd), 2);
	return (uint16_t) ((rnd%(MAX_UDP_PORT-MIN_UDP_PORT))+MIN_UDP_PORT);
}

void NetworkSocket::SetMaxPriority(){
}

bool NetworkSocket::IsFailed(){
	return failed;
}

NetworkSocket *NetworkSocket::Create(NetworkProtocol protocol){
#ifndef _WIN32
	return new NetworkSocketPosix(protocol);
#else
	return new NetworkSocketWinsock(protocol);
#endif
}

IPv4Address *NetworkSocket::ResolveDomainName(std::string name){
#ifndef _WIN32
	return NetworkSocketPosix::ResolveDomainName(name);
#else
	return NetworkSocketWinsock::ResolveDomainName(name);
#endif
}

void NetworkSocket::GenerateTCPO2States(unsigned char* buffer, TCPO2State* recvState, TCPO2State* sendState){
	memset(recvState, 0, sizeof(TCPO2State));
	memset(sendState, 0, sizeof(TCPO2State));
	unsigned char nonce[64];
	uint32_t *first = reinterpret_cast<uint32_t*>(nonce), *second = first + 1;
	uint32_t first1 = 0x44414548U, first2 = 0x54534f50U, first3 = 0x20544547U, first4 = 0x20544547U, first5 = 0xeeeeeeeeU;
	uint32_t second1 = 0;
	do {
		VoIPController::crypto.rand_bytes(nonce, sizeof(nonce));
	} while (*first == first1 || *first == first2 || *first == first3 || *first == first4 || *first == first5 || *second == second1 || *reinterpret_cast<unsigned char*>(nonce) == 0xef);

	// prepare encryption key/iv
	memcpy(sendState->key, nonce + 8, 32);
	memcpy(sendState->iv, nonce + 8 + 32, 16);

	// prepare decryption key/iv
	char reversed[48];
	memcpy(reversed, nonce + 8, sizeof(reversed));
	std::reverse(reversed, reversed + sizeof(reversed));
	memcpy(recvState->key, reversed, 32);
	memcpy(recvState->iv, reversed + 32, 16);

	// write protocol identifier
	*reinterpret_cast<uint32_t*>(nonce + 56) = 0xefefefefU;
	memcpy(buffer, nonce, 56);
	EncryptForTCPO2(nonce, sizeof(nonce), sendState);
	memcpy(buffer+56, nonce+56, 8);
}

void NetworkSocket::EncryptForTCPO2(unsigned char *buffer, size_t len, TCPO2State *state){
	VoIPController::crypto.aes_ctr_encrypt(buffer, len, state->key, state->iv, state->ecount, &state->num);
}

size_t NetworkSocket::Receive(unsigned char *buffer, size_t len){
	NetworkPacket pkt={0};
	pkt.data=buffer;
	pkt.length=len;
	Receive(&pkt);
	return pkt.length;
}

size_t NetworkSocket::Send(unsigned char *buffer, size_t len){
	NetworkPacket pkt={0};
	pkt.data=buffer;
	pkt.length=len;
	Send(&pkt);
	return pkt.length;
}

bool NetworkAddress::operator==(const NetworkAddress &other) const{
	const IPv4Address* self4=dynamic_cast<const IPv4Address*>(this);
	const IPv4Address* other4=dynamic_cast<const IPv4Address*>((NetworkAddress*)&other);
	if(self4 && other4){
		return self4->GetAddress()==other4->GetAddress();
	}
	const IPv6Address* self6=dynamic_cast<const IPv6Address*>(this);
	const IPv6Address* other6=dynamic_cast<const IPv6Address*>((NetworkAddress*)&other);
	if(self6 && other6){
		return memcmp(self6->GetAddress(), other6->GetAddress(), 16)==0;
	}
	return false;
}

bool NetworkAddress::operator!=(const NetworkAddress &other) const{
	return !(*this == other);
}

IPv4Address::IPv4Address(std::string addr){
#ifndef _WIN32
	this->address=NetworkSocketPosix::StringToV4Address(addr);
#else
	this->address=NetworkSocketWinsock::StringToV4Address(addr);
#endif
}

IPv4Address::IPv4Address(uint32_t addr){
	this->address=addr;
}

IPv4Address::IPv4Address(){
	this->address=0;
}


std::string IPv4Address::ToString() const{
#ifndef _WIN32
	return NetworkSocketPosix::V4AddressToString(address);
#else
	return NetworkSocketWinsock::V4AddressToString(address);
#endif
}

bool IPv4Address::PrefixMatches(const unsigned int prefix, const NetworkAddress &other) const{
	const IPv4Address* v4=dynamic_cast<const IPv4Address*>(&other);
	if(v4){
		uint32_t mask=0xFFFFFFFF << (32-prefix);
		return (address & mask) == (v4->address & mask);
	}
	return false;
}

/*sockaddr &IPv4Address::ToSockAddr(uint16_t port){
	sockaddr_in sa;
	sa.sin_family=AF_INET;
	sa.sin_addr=addr;
	sa.sin_port=port;
	return *((sockaddr *) &sa);
}*/

uint32_t IPv4Address::GetAddress() const{
	return address;
}

bool IPv4Address::IsEmpty() const{
	return address==0;
}

IPv6Address::IPv6Address(std::string addr){
#ifndef _WIN32
	NetworkSocketPosix::StringToV6Address(addr, this->address);
#else
	NetworkSocketWinsock::StringToV6Address(addr, this->address);
#endif
}

IPv6Address::IPv6Address(const uint8_t* addr){
	memcpy(address, addr, 16);
}

IPv6Address::IPv6Address(){
	memset(address, 0, 16);
}

std::string IPv6Address::ToString() const{
#ifndef _WIN32
	return NetworkSocketPosix::V6AddressToString(address);
#else
	return NetworkSocketWinsock::V6AddressToString(address);
#endif
}

bool IPv6Address::PrefixMatches(const unsigned int prefix, const NetworkAddress &other) const{
	return false;
}

bool IPv6Address::IsEmpty() const{
	const uint64_t* a=reinterpret_cast<const uint64_t*>(address);
	return a[0]==0LL && a[1]==0LL;
}

/*sockaddr &IPv6Address::ToSockAddr(uint16_t port){
	sockaddr_in6 sa;
	sa.sin6_family=AF_INET6;
	sa.sin6_addr=addr;
	sa.sin6_port=port;
	return *((sockaddr *) &sa);
}*/

const uint8_t *IPv6Address::GetAddress() const{
	return address;
}

bool NetworkSocket::Select(std::vector<NetworkSocket *> &readFds, std::vector<NetworkSocket*> &writeFds, std::vector<NetworkSocket *> &errorFds, SocketSelectCanceller *canceller){
#ifndef _WIN32
	return NetworkSocketPosix::Select(readFds, writeFds, errorFds, canceller);
#else
	return NetworkSocketWinsock::Select(readFds, writeFds, errorFds, canceller);
#endif
}

SocketSelectCanceller::~SocketSelectCanceller(){

}

SocketSelectCanceller *SocketSelectCanceller::Create(){
#ifndef _WIN32
	return new SocketSelectCancellerPosix();
#else
	return new SocketSelectCancellerWin32();
#endif
}



NetworkSocketTCPObfuscated::NetworkSocketTCPObfuscated(NetworkSocket *wrapped) : NetworkSocketWrapper(PROTO_TCP){
	this->wrapped=wrapped;
}

NetworkSocketTCPObfuscated::~NetworkSocketTCPObfuscated(){
	if(wrapped)
		delete wrapped;
}

NetworkSocket *NetworkSocketTCPObfuscated::GetWrapped(){
	return wrapped;
}

void NetworkSocketTCPObfuscated::InitConnection(){
	unsigned char buf[64];
	GenerateTCPO2States(buf, &recvState, &sendState);
	wrapped->Send(buf, 64);
}

void NetworkSocketTCPObfuscated::Send(NetworkPacket *packet){
	BufferOutputStream os(packet->length+4);
	size_t len=packet->length/4;
	if(len<0x7F){
		os.WriteByte((unsigned char)len);
	}else{
		os.WriteByte(0x7F);
		os.WriteByte((unsigned char)(len & 0xFF));
		os.WriteByte((unsigned char)((len >> 8) & 0xFF));
		os.WriteByte((unsigned char)((len >> 16) & 0xFF));
	}
	os.WriteBytes(packet->data, packet->length);
	EncryptForTCPO2(os.GetBuffer(), os.GetLength(), &sendState);
	wrapped->Send(os.GetBuffer(), os.GetLength());
	//LOGD("Sent %u bytes", os.GetLength());
}

bool NetworkSocketTCPObfuscated::OnReadyToSend(){
	if(!initialized){
		//LOGV("Initializing TCPO2 connection");
		initialized=true;
		InitConnection();
		readyToSend=true;
		return false;
	}
	return wrapped->OnReadyToSend();
}

void NetworkSocketTCPObfuscated::Receive(NetworkPacket *packet){
	unsigned char len1;
	size_t packetLen=0;
	size_t offset=0;
	size_t len;
	len=wrapped->Receive(&len1, 1);
	if(len<=0){
		packet->length=0;
		return;
	}
	EncryptForTCPO2(&len1, 1, &recvState);

	if(len1<0x7F){
		packetLen=(size_t)len1*4;
	}else{
		unsigned char len2[3];
		len=wrapped->Receive(len2, 3);
		if(len<=0){
			packet->length=0;
			return;
		}
		EncryptForTCPO2(len2, 3, &recvState);
		packetLen=((size_t)len2[0] | ((size_t)len2[1] << 8) | ((size_t)len2[2] << 16))*4;
	}

	if(packetLen>packet->length){
		LOGW("packet too big to fit into buffer (%u vs %u)", (unsigned int)packetLen, (unsigned int)packet->length);
		packet->length=0;
		return;
	}

	while(offset<packetLen){
		len=wrapped->Receive(packet->data+offset, packetLen-offset);
		if(len<=0){
			packet->length=0;
			return;
		}
		offset+=len;
	}
	EncryptForTCPO2(packet->data, packetLen, &recvState);
	//packet->address=&itr->address;
	packet->length=packetLen;
	//packet->port=itr->port;
	packet->protocol=PROTO_TCP;
	packet->address=wrapped->GetConnectedAddress();
	packet->port=wrapped->GetConnectedPort();
}

void NetworkSocketTCPObfuscated::Open(){

}

void NetworkSocketTCPObfuscated::Close(){
	wrapped->Close();
}

void NetworkSocketTCPObfuscated::Connect(const NetworkAddress *address, uint16_t port){
	wrapped->Connect(address, port);
}

bool NetworkSocketTCPObfuscated::IsFailed(){
	return wrapped->IsFailed();
}

NetworkSocketSOCKS5Proxy::NetworkSocketSOCKS5Proxy(NetworkSocket *tcp, NetworkSocket *udp, std::string username, std::string password) : NetworkSocketWrapper(udp ? PROTO_UDP : PROTO_TCP){
	this->tcp=tcp;
	this->udp=udp;
	this->username=std::move(username);
	this->password=std::move(password);
	connectedAddress=NULL;
}

NetworkSocketSOCKS5Proxy::~NetworkSocketSOCKS5Proxy(){
	delete tcp;
	if(connectedAddress)
		delete connectedAddress;
}

void NetworkSocketSOCKS5Proxy::Send(NetworkPacket *packet){
	if(protocol==PROTO_TCP){
		tcp->Send(packet);
	}else if(protocol==PROTO_UDP){
		unsigned char buf[1500];
		BufferOutputStream out(buf, sizeof(buf));
		out.WriteInt16(0); // RSV
		out.WriteByte(0); // FRAG
		const IPv4Address* v4=dynamic_cast<IPv4Address*>(packet->address);
		const IPv6Address* v6=dynamic_cast<IPv6Address*>(packet->address);
		if(v4){
			out.WriteByte(1); // ATYP (IPv4)
			out.WriteInt32(v4->GetAddress());
		}else{
			out.WriteByte(4); // ATYP (IPv6)
			out.WriteBytes((unsigned char *) v6->GetAddress(), 16);
		}
		out.WriteInt16(htons(packet->port));
		out.WriteBytes(packet->data, packet->length);
		NetworkPacket p={0};
		p.data=buf;
		p.length=out.GetLength();
		p.address=connectedAddress;
		p.port=connectedPort;
		p.protocol=PROTO_UDP;
		udp->Send(&p);
	}
}

void NetworkSocketSOCKS5Proxy::Receive(NetworkPacket *packet){
	if(protocol==PROTO_TCP){
		tcp->Receive(packet);
		packet->address=connectedAddress;
		packet->port=connectedPort;
	}else if(protocol==PROTO_UDP){
		unsigned char buf[1500];
		NetworkPacket p={0};
		p.data=buf;
		p.length=sizeof(buf);
		udp->Receive(&p);
		if(p.length && p.address && *p.address==*connectedAddress && p.port==connectedPort){
			BufferInputStream in(buf, p.length);
			in.ReadInt16(); // RSV
			in.ReadByte(); // FRAG
			unsigned char atyp=in.ReadByte();
			if(atyp==1){ // IPv4
				lastRecvdV4=IPv4Address((uint32_t) in.ReadInt32());
				packet->address=&lastRecvdV4;
			}else if(atyp==4){ // IPv6
				unsigned char addr[16];
				in.ReadBytes(addr, 16);
				lastRecvdV6=IPv6Address(addr);
				packet->address=&lastRecvdV6;
			}
			packet->port=ntohs(in.ReadInt16());
			if(packet->length>=in.Remaining()){
				packet->length=in.Remaining();
				in.ReadBytes(packet->data, in.Remaining());
			}else{
				packet->length=0;
				LOGW("socks5: received packet too big");
			}
		}
	}
}

void NetworkSocketSOCKS5Proxy::Open(){

}

void NetworkSocketSOCKS5Proxy::Close(){
	tcp->Close();
}

void NetworkSocketSOCKS5Proxy::Connect(const NetworkAddress *address, uint16_t port){
	const IPv4Address* v4=dynamic_cast<const IPv4Address*>(address);
	const IPv6Address* v6=dynamic_cast<const IPv6Address*>(address);
	connectedAddress=v4 ? (NetworkAddress*)new IPv4Address(*v4) : (NetworkAddress*)new IPv6Address(*v6);
	connectedPort=port;
}

NetworkSocket *NetworkSocketSOCKS5Proxy::GetWrapped(){
	return protocol==PROTO_TCP ? tcp : udp;
}

void NetworkSocketSOCKS5Proxy::InitConnection(){
}

bool NetworkSocketSOCKS5Proxy::IsFailed(){
	return NetworkSocket::IsFailed() || tcp->IsFailed();
}

NetworkAddress *NetworkSocketSOCKS5Proxy::GetConnectedAddress(){
	return connectedAddress;
}

uint16_t NetworkSocketSOCKS5Proxy::GetConnectedPort(){
	return connectedPort;
}

bool NetworkSocketSOCKS5Proxy::OnReadyToSend(){
	//LOGV("on ready to send, state=%d", state);
	unsigned char buf[1024];
	if(state==ConnectionState::Initial){
		BufferOutputStream p(buf, sizeof(buf));
		p.WriteByte(5); // VER
		if(!username.empty()){
			p.WriteByte(2); // NMETHODS
			p.WriteByte(0); // no auth
			p.WriteByte(2); // user/pass
		}else{
			p.WriteByte(1); // NMETHODS
			p.WriteByte(0); // no auth
		}
		tcp->Send(buf, p.GetLength());
		state=ConnectionState::WaitingForAuthMethod;
		return false;
	}
	return udp ? udp->OnReadyToSend() : tcp->OnReadyToSend();
}

bool NetworkSocketSOCKS5Proxy::OnReadyToReceive(){
	//LOGV("on ready to receive state=%d", state);
	unsigned char buf[1024];
	if(state==ConnectionState::WaitingForAuthMethod){
		size_t l=tcp->Receive(buf, sizeof(buf));
		if(l<2 || tcp->IsFailed()){
			failed=true;
			return false;
		}
		BufferInputStream in(buf, l);
		unsigned char ver=in.ReadByte();
		unsigned char chosenMethod=in.ReadByte();
		LOGV("socks5: VER=%02X, METHOD=%02X", ver, chosenMethod);
		if(ver!=5){
			LOGW("socks5: incorrect VER in response");
			failed=true;
			return false;
		}
		if(chosenMethod==0){
			// connected, no further auth needed
			SendConnectionCommand();
		}else if(chosenMethod==2 && !username.empty()){
        	BufferOutputStream p(buf, sizeof(buf));
			p.WriteByte(1); // VER
			p.WriteByte((unsigned char)(username.length()>255 ? 255 : username.length())); // ULEN
			p.WriteBytes((unsigned char*)username.c_str(), username.length()>255 ? 255 : username.length()); // UNAME
			p.WriteByte((unsigned char)(password.length()>255 ? 255 : password.length())); // PLEN
			p.WriteBytes((unsigned char*)password.c_str(), password.length()>255 ? 255 : password.length()); // PASSWD
			tcp->Send(buf, p.GetLength());
			state=ConnectionState::WaitingForAuthResult;
		}else{
			LOGW("socks5: unsupported auth method");
			failed=true;
			return false;
		}
		return false;
	}else if(state==ConnectionState::WaitingForAuthResult){
		size_t l=tcp->Receive(buf, sizeof(buf));
		if(l<2 || tcp->IsFailed()){
			failed=true;
			return false;
		}
		BufferInputStream in(buf, l);
		uint8_t ver=in.ReadByte();
		unsigned char status=in.ReadByte();
		LOGV("socks5: auth response VER=%02X, STATUS=%02X", ver, status);
		if(ver!=1){
			LOGW("socks5: auth response VER is incorrect");
			failed=true;
			return false;
		}
		if(status!=0){
			LOGW("socks5: username/password auth failed");
			failed=true;
			return false;
		}
		LOGV("socks5: authentication succeeded");
		SendConnectionCommand();
		return false;
	}else if(state==ConnectionState::WaitingForCommandResult){
		size_t l=tcp->Receive(buf, sizeof(buf));
		if(protocol==PROTO_TCP){
    		if(l<2 || tcp->IsFailed()){
    			LOGW("socks5: connect failed")
    			failed=true;
    			return false;
    		}
    		BufferInputStream in(buf, l);
    		unsigned char ver=in.ReadByte();
    		if(ver!=5){
    			LOGW("socks5: connect: wrong ver in response");
    			failed=true;
    			return false;
    		}
    		unsigned char rep=in.ReadByte();
    		if(rep!=0){
    			LOGW("socks5: connect: failed with error %02X", rep);
    			failed=true;
    			return false;
    		}
    		LOGV("socks5: connect succeeded");
    		state=ConnectionState::Connected;
    		tcp=new NetworkSocketTCPObfuscated(tcp);
    		readyToSend=true;
    		return tcp->OnReadyToSend();
		}else if(protocol==PROTO_UDP){
			if(l<2 || tcp->IsFailed()){
				LOGW("socks5: udp associate failed");
				failed=true;
				return false;
			}
			try{
				BufferInputStream in(buf, l);
				unsigned char ver=in.ReadByte();
				unsigned char rep=in.ReadByte();
				if(ver!=5){
					LOGW("socks5: udp associate: wrong ver in response");
					failed=true;
					return false;
				}
				if(rep!=0){
					LOGW("socks5: udp associate failed with error %02X", rep);
					failed=true;
					return false;
				}
				in.ReadByte(); // RSV
				unsigned char atyp=in.ReadByte();
				if(atyp==1){
					uint32_t addr=(uint32_t) in.ReadInt32();
					connectedAddress=new IPv4Address(addr);
				}else if(atyp==3){
					unsigned char len=in.ReadByte();
					char domain[256];
					memset(domain, 0, sizeof(domain));
					in.ReadBytes((unsigned char*)domain, len);
					LOGD("address type is domain, address=%s", domain);
					connectedAddress=ResolveDomainName(std::string(domain));
					if(!connectedAddress){
						LOGW("socks5: failed to resolve domain name '%s'", domain);
						failed=true;
						return false;
					}
				}else if(atyp==4){
					unsigned char addr[16];
					in.ReadBytes(addr, 16);
					connectedAddress=new IPv6Address(addr);
				}else{
					LOGW("socks5: unknown address type %d", atyp);
					failed=true;
					return false;
				}
				connectedPort=(uint16_t)ntohs(in.ReadInt16());
        		state=ConnectionState::Connected;
				readyToSend=true;
				LOGV("socks5: udp associate successful, given endpoint %s:%d", connectedAddress->ToString().c_str(), connectedPort);
			}catch(std::out_of_range& x){
				LOGW("socks5: udp associate response parse failed");
				failed=true;
			}
		}
	}
	return udp ? udp->OnReadyToReceive() : tcp->OnReadyToReceive();
}

void NetworkSocketSOCKS5Proxy::SendConnectionCommand(){
	unsigned char buf[1024];
	if(protocol==PROTO_TCP){
		BufferOutputStream out(buf, sizeof(buf));
		out.WriteByte(5); // VER
		out.WriteByte(1); // CMD (CONNECT)
		out.WriteByte(0); // RSV
		const IPv4Address* v4=dynamic_cast<const IPv4Address*>(connectedAddress);
		const IPv6Address* v6=dynamic_cast<const IPv6Address*>(connectedAddress);
		if(v4){
			out.WriteByte(1); // ATYP (IPv4)
			out.WriteInt32(v4->GetAddress());
		}else if(v6){
			out.WriteByte(4); // ATYP (IPv6)
			out.WriteBytes((unsigned char*)v6->GetAddress(), 16);
		}else{
			LOGW("socks5: unknown address type");
			failed=true;
			return;
		}
		out.WriteInt16(htons(connectedPort)); // DST.PORT
		tcp->Send(buf, out.GetLength());
		state=ConnectionState::WaitingForCommandResult;
	}else if(protocol==PROTO_UDP){
		LOGV("Sending udp associate");
		BufferOutputStream out(buf, sizeof(buf));
		out.WriteByte(5); // VER
		out.WriteByte(3); // CMD (UDP ASSOCIATE)
		out.WriteByte(0); // RSV
		out.WriteByte(1); // ATYP (IPv4)
		out.WriteInt32(0); // DST.ADDR
		out.WriteInt16(0); // DST.PORT
		tcp->Send(buf, out.GetLength());
		state=ConnectionState::WaitingForCommandResult;
	}
}

bool NetworkSocketSOCKS5Proxy::NeedSelectForSending(){
	return state==ConnectionState::Initial || state==ConnectionState::Connected;
}
