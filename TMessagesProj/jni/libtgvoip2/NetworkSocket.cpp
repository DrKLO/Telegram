//
// Created by Grishka on 29.03.17.
//

#include <stdexcept>
#include <algorithm>
#include <stdlib.h>
#include <string.h>
#if defined(_WIN32)
#include <winsock2.h>
#include "os/windows/NetworkSocketWinsock.h"
#else
#include "os/posix/NetworkSocketPosix.h"
#endif
#include "logging.h"
#include "VoIPServerConfig.h"
#include "VoIPController.h"
#include "Buffers.h"
#include "NetworkSocket.h"

#define MIN_UDP_PORT 16384
#define MAX_UDP_PORT 32768

using namespace tgvoip;

NetworkSocket::NetworkSocket(NetworkProtocol protocol) : protocol(protocol){
	ipv6Timeout=ServerConfig::GetSharedInstance()->GetDouble("nat64_fallback_timeout", 3);
	failed=false;
}

NetworkSocket::~NetworkSocket(){

}

std::string NetworkSocket::GetLocalInterfaceInfo(NetworkAddress *inet4addr, NetworkAddress *inet6addr){
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

NetworkAddress NetworkSocket::ResolveDomainName(std::string name){
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
	NetworkPacket pkt=Receive(len);
	if(pkt.IsEmpty())
		return 0;
	size_t actualLen=std::min(len, pkt.data.Length());
	memcpy(buffer, *pkt.data, actualLen);
	return actualLen;
}

bool NetworkAddress::operator==(const NetworkAddress &other) const{
	if(isIPv6!=other.isIPv6)
		return false;
	if(!isIPv6){
		return addr.ipv4==other.addr.ipv4;
	}
	return memcmp(addr.ipv6, other.addr.ipv6, 16)==0;
}

bool NetworkAddress::operator!=(const NetworkAddress &other) const{
	return !(*this == other);
}

std::string NetworkAddress::ToString() const{
	if(isIPv6){
#ifndef _WIN32
		return NetworkSocketPosix::V6AddressToString(addr.ipv6);
#else
		return NetworkSocketWinsock::V6AddressToString(addr.ipv6);
#endif
	}else{
#ifndef _WIN32
		return NetworkSocketPosix::V4AddressToString(addr.ipv4);
#else
		return NetworkSocketWinsock::V4AddressToString(addr.ipv4);
#endif
	}
}

bool NetworkAddress::IsEmpty() const{
	if(isIPv6){
		const uint64_t* a=reinterpret_cast<const uint64_t*>(addr.ipv6);
		return a[0]==0LL && a[1]==0LL;
	}
	return addr.ipv4==0;
}

bool NetworkAddress::PrefixMatches(const unsigned int prefix, const NetworkAddress &other) const{
	if(isIPv6!=other.isIPv6)
		return false;
	if(!isIPv6){
		uint32_t mask=0xFFFFFFFF << (32-prefix);
		return (addr.ipv4 & mask) == (other.addr.ipv4 & mask);
	}
	return false;
}

NetworkAddress NetworkAddress::Empty(){
	NetworkAddress addr;
	addr.isIPv6=false;
	addr.addr.ipv4=0;
	return addr;
}

NetworkAddress NetworkAddress::IPv4(std::string str){
	NetworkAddress addr;
	addr.isIPv6=false;
#ifndef _WIN32
	addr.addr.ipv4=NetworkSocketPosix::StringToV4Address(str);
#else
	addr.addr.ipv4=NetworkSocketWinsock::StringToV4Address(str);
#endif
	return addr;
}

NetworkAddress NetworkAddress::IPv4(uint32_t addr){
	NetworkAddress a;
	a.isIPv6=false;
	a.addr.ipv4=addr;
	return a;
}

NetworkAddress NetworkAddress::IPv6(std::string str){
	NetworkAddress addr;
	addr.isIPv6=false;
#ifndef _WIN32
	NetworkSocketPosix::StringToV6Address(str, addr.addr.ipv6);
#else
	NetworkSocketWinsock::StringToV6Address(str, addr.addr.ipv6);
#endif
	return addr;
}

NetworkAddress NetworkAddress::IPv6(const uint8_t addr[16]){
	NetworkAddress a;
	a.isIPv6=true;
	memcpy(a.addr.ipv6, addr, 16);
	return a;
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



NetworkSocketTCPObfuscated::NetworkSocketTCPObfuscated(NetworkSocket *wrapped) : NetworkSocketWrapper(NetworkProtocol::TCP){
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
	Buffer buf(64);
	GenerateTCPO2States(*buf, &recvState, &sendState);
	wrapped->Send(NetworkPacket{
		std::move(buf),
		NetworkAddress::Empty(),
		0,
		NetworkProtocol::TCP
	});
}

void NetworkSocketTCPObfuscated::Send(NetworkPacket packet){
	BufferOutputStream os(packet.data.Length()+4);
	size_t len=packet.data.Length()/4;
	if(len<0x7F){
		os.WriteByte((unsigned char)len);
	}else{
		os.WriteByte(0x7F);
		os.WriteByte((unsigned char)(len & 0xFF));
		os.WriteByte((unsigned char)((len >> 8) & 0xFF));
		os.WriteByte((unsigned char)((len >> 16) & 0xFF));
	}
	os.WriteBytes(packet.data);
	EncryptForTCPO2(os.GetBuffer(), os.GetLength(), &sendState);
	wrapped->Send(NetworkPacket{
		Buffer(std::move(os)),
		NetworkAddress::Empty(),
		0,
		NetworkProtocol::TCP
	});
	//LOGD("Sent %u bytes", os.GetLength());
}

bool NetworkSocketTCPObfuscated::OnReadyToSend(){
	LOGV("TCPO socket ready to send");
	if(!initialized){
		LOGV("Initializing TCPO2 connection");
		initialized=true;
		InitConnection();
		readyToSend=true;
		return false;
	}
	return wrapped->OnReadyToSend();
}

NetworkPacket NetworkSocketTCPObfuscated::Receive(size_t maxLen){
	unsigned char len1;
	size_t packetLen=0;
	size_t offset=0;
	size_t len;
	len=wrapped->Receive(&len1, 1);
	if(len<=0){
		return NetworkPacket::Empty();
	}
	EncryptForTCPO2(&len1, 1, &recvState);

	if(len1<0x7F){
		packetLen=(size_t)len1*4;
	}else{
		unsigned char len2[3];
		len=wrapped->Receive(len2, 3);
		if(len<=0){
			return NetworkPacket::Empty();
		}
		EncryptForTCPO2(len2, 3, &recvState);
		packetLen=((size_t)len2[0] | ((size_t)len2[1] << 8) | ((size_t)len2[2] << 16))*4;
	}

	if(packetLen>1500){
		LOGW("packet too big to fit into buffer (%u vs %u)", (unsigned int)packetLen, (unsigned int)1500);
		return NetworkPacket::Empty();
	}
	Buffer buf(packetLen);

	while(offset<packetLen){
		len=wrapped->Receive(*buf, packetLen-offset);
		if(len<=0){
			return NetworkPacket::Empty();
		}
		offset+=len;
	}
	EncryptForTCPO2(*buf, packetLen, &recvState);
	return NetworkPacket{
		std::move(buf),
		wrapped->GetConnectedAddress(),
		wrapped->GetConnectedPort(),
		NetworkProtocol::TCP
	};
}

void NetworkSocketTCPObfuscated::Open(){

}

void NetworkSocketTCPObfuscated::Close(){
	wrapped->Close();
}

void NetworkSocketTCPObfuscated::Connect(const NetworkAddress address, uint16_t port){
	wrapped->Connect(address, port);
}

bool NetworkSocketTCPObfuscated::IsFailed(){
	return wrapped->IsFailed();
}

NetworkSocketSOCKS5Proxy::NetworkSocketSOCKS5Proxy(NetworkSocket *tcp, NetworkSocket *udp, std::string username, std::string password) : NetworkSocketWrapper(udp ? NetworkProtocol::UDP : NetworkProtocol::TCP){
	this->tcp=tcp;
	this->udp=udp;
	this->username=username;
	this->password=password;
}

NetworkSocketSOCKS5Proxy::~NetworkSocketSOCKS5Proxy(){
	delete tcp;
}

void NetworkSocketSOCKS5Proxy::Send(NetworkPacket packet){
	if(protocol==NetworkProtocol::TCP){
		tcp->Send(std::move(packet));
	}else if(protocol==NetworkProtocol::UDP){
		BufferOutputStream out(1500);
		out.WriteInt16(0); // RSV
		out.WriteByte(0); // FRAG
		if(!packet.address.isIPv6){
			out.WriteByte(1); // ATYP (IPv4)
			out.WriteInt32(packet.address.addr.ipv4);
		}else{
			out.WriteByte(4); // ATYP (IPv6)
			out.WriteBytes(packet.address.addr.ipv6, 16);
		}
		out.WriteInt16(htons(packet.port));
		out.WriteBytes(packet.data);
		udp->Send(NetworkPacket{
			Buffer(std::move(out)),
			connectedAddress,
			connectedPort,
			NetworkProtocol::UDP
		});
	}
}

NetworkPacket NetworkSocketSOCKS5Proxy::Receive(size_t maxLen){
	if(protocol==NetworkProtocol::TCP){
		NetworkPacket packet=tcp->Receive();
		packet.address=connectedAddress;
		packet.port=connectedPort;
		return packet;
	}else{
		NetworkPacket p=udp->Receive();
		if(!p.IsEmpty() && p.address==connectedAddress && p.port==connectedPort){
			BufferInputStream in(p.data);
			in.ReadInt16(); // RSV
			in.ReadByte(); // FRAG
			unsigned char atyp=in.ReadByte();
			NetworkAddress address=NetworkAddress::Empty();
			if(atyp==1){ // IPv4
				address=NetworkAddress::IPv4((uint32_t) in.ReadInt32());
			}else if(atyp==4){ // IPv6
				unsigned char addr[16];
				in.ReadBytes(addr, 16);
				address=NetworkAddress::IPv6(addr);
			}
			return NetworkPacket{
				Buffer::CopyOf(p.data, in.GetOffset(), in.Remaining()),
				address,
				htons(in.ReadInt16()),
				protocol
			};
		}
	}
	return NetworkPacket::Empty();
}

void NetworkSocketSOCKS5Proxy::Open(){

}

void NetworkSocketSOCKS5Proxy::Close(){
	tcp->Close();
}

void NetworkSocketSOCKS5Proxy::Connect(const NetworkAddress address, uint16_t port){
	connectedAddress=address;
	connectedPort=port;
}

NetworkSocket *NetworkSocketSOCKS5Proxy::GetWrapped(){
	return protocol==NetworkProtocol::TCP ? tcp : udp;
}

void NetworkSocketSOCKS5Proxy::InitConnection(){
}

bool NetworkSocketSOCKS5Proxy::IsFailed(){
	return NetworkSocket::IsFailed() || tcp->IsFailed();
}

NetworkAddress NetworkSocketSOCKS5Proxy::GetConnectedAddress(){
	return connectedAddress;
}

uint16_t NetworkSocketSOCKS5Proxy::GetConnectedPort(){
	return connectedPort;
}

bool NetworkSocketSOCKS5Proxy::OnReadyToSend(){
	//LOGV("on ready to send, state=%d", state);
	if(state==ConnectionState::Initial){
		BufferOutputStream p(16);
		p.WriteByte(5); // VER
		if(!username.empty()){
			p.WriteByte(2); // NMETHODS
			p.WriteByte(0); // no auth
			p.WriteByte(2); // user/pass
		}else{
			p.WriteByte(1); // NMETHODS
			p.WriteByte(0); // no auth
		}
		tcp->Send(NetworkPacket{
				Buffer(std::move(p)),
				NetworkAddress::Empty(),
				0,
				NetworkProtocol::TCP
		});
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
        	BufferOutputStream p(512);
			p.WriteByte(1); // VER
			p.WriteByte((unsigned char)(username.length()>255 ? 255 : username.length())); // ULEN
			p.WriteBytes((unsigned char*)username.c_str(), username.length()>255 ? 255 : username.length()); // UNAME
			p.WriteByte((unsigned char)(password.length()>255 ? 255 : password.length())); // PLEN
			p.WriteBytes((unsigned char*)password.c_str(), password.length()>255 ? 255 : password.length()); // PASSWD
			tcp->Send(NetworkPacket{
				Buffer(std::move(p)),
				NetworkAddress::Empty(),
				0,
				NetworkProtocol::TCP
			});
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
		if(protocol==NetworkProtocol::TCP){
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
		}else if(protocol==NetworkProtocol::UDP){
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
					connectedAddress=NetworkAddress::IPv4(addr);
				}else if(atyp==3){
					unsigned char len=in.ReadByte();
					char domain[256];
					memset(domain, 0, sizeof(domain));
					in.ReadBytes((unsigned char*)domain, len);
					LOGD("address type is domain, address=%s", domain);
					connectedAddress=ResolveDomainName(std::string(domain));
					if(connectedAddress.IsEmpty()){
						LOGW("socks5: failed to resolve domain name '%s'", domain);
						failed=true;
						return false;
					}
				}else if(atyp==4){
					unsigned char addr[16];
					in.ReadBytes(addr, 16);
					connectedAddress=NetworkAddress::IPv6(addr);
				}else{
					LOGW("socks5: unknown address type %d", atyp);
					failed=true;
					return false;
				}
				connectedPort=(uint16_t)ntohs(in.ReadInt16());
        		state=ConnectionState::Connected;
				readyToSend=true;
				LOGV("socks5: udp associate successful, given endpoint %s:%d", connectedAddress.ToString().c_str(), connectedPort);
			}catch(std::out_of_range& x){
				LOGW("socks5: udp associate response parse failed");
				failed=true;
			}
		}
	}
	return udp ? udp->OnReadyToReceive() : tcp->OnReadyToReceive();
}

void NetworkSocketSOCKS5Proxy::SendConnectionCommand(){
	BufferOutputStream out(1024);
	if(protocol==NetworkProtocol::TCP){
		out.WriteByte(5); // VER
		out.WriteByte(1); // CMD (CONNECT)
		out.WriteByte(0); // RSV
		if(!connectedAddress.isIPv6){
			out.WriteByte(1); // ATYP (IPv4)
			out.WriteInt32(connectedAddress.addr.ipv4);
		}else{
			out.WriteByte(4); // ATYP (IPv6)
			out.WriteBytes((unsigned char*)connectedAddress.addr.ipv6, 16);
		}
		out.WriteInt16(htons(connectedPort)); // DST.PORT
	}else if(protocol==NetworkProtocol::UDP){
		LOGV("Sending udp associate");
		out.WriteByte(5); // VER
		out.WriteByte(3); // CMD (UDP ASSOCIATE)
		out.WriteByte(0); // RSV
		out.WriteByte(1); // ATYP (IPv4)
		out.WriteInt32(0); // DST.ADDR
		out.WriteInt16(0); // DST.PORT
	}
	tcp->Send(NetworkPacket{
		Buffer(std::move(out)),
		NetworkAddress::Empty(),
		0,
		NetworkProtocol::TCP
	});
	state=ConnectionState::WaitingForCommandResult;
}

bool NetworkSocketSOCKS5Proxy::NeedSelectForSending(){
	return state==ConnectionState::Initial || state==ConnectionState::Connected;
}
