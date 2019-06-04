//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "NetworkSocketPosix.h"
#include <sys/socket.h>
#include <errno.h>
#include <assert.h>
#include <netdb.h>
#include <net/if.h>
#include <sys/ioctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <netinet/tcp.h>
#include "../../logging.h"
#include "../../VoIPController.h"
#include "../../Buffers.h"

#ifdef __ANDROID__
#include <jni.h>
#include <sys/system_properties.h>
extern JavaVM* sharedJVM;
extern jclass jniUtilitiesClass;
#else
#include <ifaddrs.h>
#endif

using namespace tgvoip;


NetworkSocketPosix::NetworkSocketPosix(NetworkProtocol protocol) : NetworkSocket(protocol), lastRecvdV4(0), lastRecvdV6("::0"){
	needUpdateNat64Prefix=true;
	nat64Present=false;
	switchToV6at=0;
	isV4Available=false;
    fd=-1;
	useTCP=false;
	closing=false;

	tcpConnectedAddress=NULL;
	tcpConnectedPort=0;

	if(protocol==PROTO_TCP)
		timeout=10.0;
	lastSuccessfulOperationTime=VoIPController::GetCurrentTime();
}

NetworkSocketPosix::~NetworkSocketPosix(){
	if(fd>=0){
		Close();
	}
	if(tcpConnectedAddress)
		delete tcpConnectedAddress;
	if(pendingOutgoingPacket)
		delete pendingOutgoingPacket;
}

void NetworkSocketPosix::SetMaxPriority(){
#ifdef __APPLE__
	int prio=NET_SERVICE_TYPE_VO;
	int res=setsockopt(fd, SOL_SOCKET, SO_NET_SERVICE_TYPE, &prio, sizeof(prio));
	if(res<0){
		LOGE("error setting darwin-specific net priority: %d / %s", errno, strerror(errno));
	}
#elif defined(__linux__)
	int prio=6;
	int res=setsockopt(fd, SOL_SOCKET, SO_PRIORITY, &prio, sizeof(prio));
	if(res<0){
		LOGE("error setting priority: %d / %s", errno, strerror(errno));
	}
	prio=46 << 2;
	res=setsockopt(fd, SOL_IP, IP_TOS, &prio, sizeof(prio));
	if(res<0){
		LOGE("error setting ip tos: %d / %s", errno, strerror(errno));
	}
#else
	LOGI("cannot set socket priority");
#endif
}

void NetworkSocketPosix::Send(NetworkPacket *packet){
	if(!packet || (protocol==PROTO_UDP && !packet->address)){
		LOGW("tried to send null packet");
		return;
	}
	int res;
	if(protocol==PROTO_UDP){
		sockaddr_in6 addr;
		IPv4Address *v4addr=dynamic_cast<IPv4Address *>(packet->address);
		if(v4addr){
			if(needUpdateNat64Prefix && !isV4Available && VoIPController::GetCurrentTime()>switchToV6at && switchToV6at!=0){
				LOGV("Updating NAT64 prefix");
				nat64Present=false;
				addrinfo *addr0;
				int res=getaddrinfo("ipv4only.arpa", NULL, NULL, &addr0);
				if(res!=0){
					LOGW("Error updating NAT64 prefix: %d / %s", res, gai_strerror(res));
				}else{
					addrinfo *addrPtr;
					unsigned char *addr170=NULL;
					unsigned char *addr171=NULL;
					for(addrPtr=addr0; addrPtr; addrPtr=addrPtr->ai_next){
						if(addrPtr->ai_family==AF_INET6){
							sockaddr_in6 *translatedAddr=(sockaddr_in6 *) addrPtr->ai_addr;
							uint32_t v4part=*((uint32_t *) &translatedAddr->sin6_addr.s6_addr[12]);
							if(v4part==0xAA0000C0 && !addr170){
								addr170=translatedAddr->sin6_addr.s6_addr;
							}
							if(v4part==0xAB0000C0 && !addr171){
								addr171=translatedAddr->sin6_addr.s6_addr;
							}
							char buf[INET6_ADDRSTRLEN];
							LOGV("Got translated address: %s", inet_ntop(AF_INET6, &translatedAddr->sin6_addr, buf, sizeof(buf)));
						}
					}
					if(addr170 && addr171 && memcmp(addr170, addr171, 12)==0){
						nat64Present=true;
						memcpy(nat64Prefix, addr170, 12);
						char buf[INET6_ADDRSTRLEN];
						LOGV("Found nat64 prefix from %s", inet_ntop(AF_INET6, addr170, buf, sizeof(buf)));
					}else{
						LOGV("Didn't find nat64");
					}
					freeaddrinfo(addr0);
				}
				needUpdateNat64Prefix=false;
			}
			memset(&addr, 0, sizeof(sockaddr_in6));
			addr.sin6_family=AF_INET6;
			*((uint32_t *) &addr.sin6_addr.s6_addr[12])=v4addr->GetAddress();
			if(nat64Present)
				memcpy(addr.sin6_addr.s6_addr, nat64Prefix, 12);
			else
				addr.sin6_addr.s6_addr[11]=addr.sin6_addr.s6_addr[10]=0xFF;

		}else{
			IPv6Address *v6addr=dynamic_cast<IPv6Address *>(packet->address);
			assert(v6addr!=NULL);
			memcpy(addr.sin6_addr.s6_addr, v6addr->GetAddress(), 16);
			addr.sin6_family=AF_INET6;
		}
		addr.sin6_port=htons(packet->port);
		res=(int)sendto(fd, packet->data, packet->length, 0, (const sockaddr *) &addr, sizeof(addr));
	}else{
		res=(int)send(fd, packet->data, packet->length, 0);
	}
	if(res<=0){
		if(errno==EAGAIN || errno==EWOULDBLOCK){
			if(pendingOutgoingPacket){
				LOGE("Got EAGAIN but there's already a pending packet");
				failed=true;
			}else{
				LOGV("Socket %d not ready to send", fd);
				pendingOutgoingPacket=new Buffer(packet->length);
				pendingOutgoingPacket->CopyFrom(packet->data, 0, packet->length);
				readyToSend=false;
			}
		}else{
    		LOGE("error sending: %d / %s", errno, strerror(errno));
    		if(errno==ENETUNREACH && !isV4Available && VoIPController::GetCurrentTime()<switchToV6at){
    			switchToV6at=VoIPController::GetCurrentTime();
    			LOGI("Network unreachable, trying NAT64");
    		}
		}
	}else if((size_t)res!=packet->length && packet->protocol==PROTO_TCP){
		if(pendingOutgoingPacket){
			LOGE("send returned less than packet length but there's already a pending packet");
			failed=true;
		}else{
			LOGV("Socket %d not ready to send", fd);
			pendingOutgoingPacket=new Buffer(packet->length-res);
			pendingOutgoingPacket->CopyFrom(packet->data+res, 0, packet->length-res);
			readyToSend=false;
		}
	}
}

bool NetworkSocketPosix::OnReadyToSend(){
	if(pendingOutgoingPacket){
		NetworkPacket pkt={0};
		pkt.data=**pendingOutgoingPacket;
		pkt.length=pendingOutgoingPacket->Length();
		Send(&pkt);
		delete pendingOutgoingPacket;
		pendingOutgoingPacket=NULL;
		return false;
	}
	readyToSend=true;
	return true;
}

void NetworkSocketPosix::Receive(NetworkPacket *packet){
	if(failed){
		packet->length=0;
		return;
	}
	if(protocol==PROTO_UDP){
		int addrLen=sizeof(sockaddr_in6);
		sockaddr_in6 srcAddr;
		ssize_t len=recvfrom(fd, packet->data, packet->length, 0, (sockaddr *) &srcAddr, (socklen_t *) &addrLen);
		if(len>0)
			packet->length=(size_t) len;
		else{
			LOGE("error receiving %d / %s", errno, strerror(errno));
			packet->length=0;
			return;
		}
		//LOGV("Received %d bytes from %s:%d at %.5lf", len, inet_ntoa(srcAddr.sin_addr), ntohs(srcAddr.sin_port), GetCurrentTime());
		if(!isV4Available && IN6_IS_ADDR_V4MAPPED(&srcAddr.sin6_addr)){
			isV4Available=true;
			LOGI("Detected IPv4 connectivity, will not try IPv6");
		}
		if(IN6_IS_ADDR_V4MAPPED(&srcAddr.sin6_addr) || (nat64Present && memcmp(nat64Prefix, srcAddr.sin6_addr.s6_addr, 12)==0)){
			in_addr v4addr=*((in_addr *) &srcAddr.sin6_addr.s6_addr[12]);
			lastRecvdV4=IPv4Address(v4addr.s_addr);
			packet->address=&lastRecvdV4;
		}else{
			lastRecvdV6=IPv6Address(srcAddr.sin6_addr.s6_addr);
			packet->address=&lastRecvdV6;
		}
		packet->protocol=PROTO_UDP;
		packet->port=ntohs(srcAddr.sin6_port);
	}else if(protocol==PROTO_TCP){
		int res=(int)recv(fd, packet->data, packet->length, 0);
		if(res<=0){
			LOGE("Error receiving from TCP socket: %d / %s", errno, strerror(errno));
			failed=true;
			packet->length=0;
		}else{
			packet->length=(size_t)res;
			packet->address=tcpConnectedAddress;
			packet->port=tcpConnectedPort;
			packet->protocol=PROTO_TCP;
		}
	}
}

void NetworkSocketPosix::Open(){
	if(protocol!=PROTO_UDP)
		return;
	fd=socket(PF_INET6, SOCK_DGRAM, IPPROTO_UDP);
	if(fd<0){
		LOGE("error creating socket: %d / %s", errno, strerror(errno));
		failed=true;
		return;
	}
	int flag=0;
	int res=setsockopt(fd, IPPROTO_IPV6, IPV6_V6ONLY, &flag, sizeof(flag));
	if(res<0){
		LOGE("error enabling dual stack socket: %d / %s", errno, strerror(errno));
		failed=true;
		return;
	}

	SetMaxPriority();
	fcntl(fd, F_SETFL, O_NONBLOCK);

	int tries=0;
	sockaddr_in6 addr;
	//addr.sin6_addr.s_addr=0;
	memset(&addr, 0, sizeof(sockaddr_in6));
	//addr.sin6_len=sizeof(sa_family_t);
	addr.sin6_family=AF_INET6;
	for(tries=0;tries<10;tries++){
		addr.sin6_port=htons(GenerateLocalPort());
		res=::bind(fd, (sockaddr *) &addr, sizeof(sockaddr_in6));
		LOGV("trying bind to port %u", ntohs(addr.sin6_port));
		if(res<0){
			LOGE("error binding to port %u: %d / %s", ntohs(addr.sin6_port), errno, strerror(errno));
		}else{
			break;
		}
	}
	if(tries==10){
		addr.sin6_port=0;
		res=::bind(fd, (sockaddr *) &addr, sizeof(sockaddr_in6));
		if(res<0){
			LOGE("error binding to port %u: %d / %s", ntohs(addr.sin6_port), errno, strerror(errno));
			//SetState(STATE_FAILED);
			failed=true;
			return;
		}
	}
	size_t addrLen=sizeof(sockaddr_in6);
	getsockname(fd, (sockaddr*)&addr, (socklen_t*) &addrLen);
	LOGD("Bound to local UDP port %u", ntohs(addr.sin6_port));

	needUpdateNat64Prefix=true;
	isV4Available=false;
	switchToV6at=VoIPController::GetCurrentTime()+ipv6Timeout;
}

void NetworkSocketPosix::Close(){
	closing=true;
	failed=true;
	
    if (fd>=0) {
        shutdown(fd, SHUT_RDWR);
        close(fd);
		fd=-1;
    }
}

void NetworkSocketPosix::Connect(const NetworkAddress *address, uint16_t port){
	const IPv4Address* v4addr=dynamic_cast<const IPv4Address*>(address);
	const IPv6Address* v6addr=dynamic_cast<const IPv6Address*>(address);
	struct sockaddr_in v4={0};
	struct sockaddr_in6 v6={0};
	struct sockaddr* addr=NULL;
	size_t addrLen=0;
	if(v4addr){
		v4.sin_family=AF_INET;
		v4.sin_addr.s_addr=v4addr->GetAddress();
		v4.sin_port=htons(port);
		addr=reinterpret_cast<sockaddr*>(&v4);
		addrLen=sizeof(v4);
	}else if(v6addr){
		v6.sin6_family=AF_INET6;
		memcpy(v6.sin6_addr.s6_addr, v6addr->GetAddress(), 16);
		v6.sin6_flowinfo=0;
		v6.sin6_scope_id=0;
		v6.sin6_port=htons(port);
		addr=reinterpret_cast<sockaddr*>(&v6);
		addrLen=sizeof(v6);
	}else{
		LOGE("Unknown address type in TCP connect");
		failed=true;
		return;
	}
	fd=socket(addr->sa_family, SOCK_STREAM, IPPROTO_TCP);
	if(fd<0){
		LOGE("Error creating TCP socket: %d / %s", errno, strerror(errno));
		failed=true;
		return;
	}
	int opt=1;
	setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
	timeval timeout;
	timeout.tv_sec=5;
	timeout.tv_usec=0;
	setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
	timeout.tv_sec=60;
	setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
	fcntl(fd, F_SETFL, O_NONBLOCK);
	int res=(int)connect(fd, (const sockaddr*) addr, (socklen_t)addrLen);
	if(res!=0 && errno!=EINVAL && errno!=EINPROGRESS){
		LOGW("error connecting TCP socket to %s:%u: %d / %s; %d / %s", address->ToString().c_str(), port, res, strerror(res), errno, strerror(errno));
		close(fd);
		failed=true;
		return;
	}
	tcpConnectedAddress=v4addr ? (NetworkAddress*)new IPv4Address(*v4addr) : (NetworkAddress*)new IPv6Address(*v6addr);
	tcpConnectedPort=port;
	LOGI("successfully connected to %s:%d", tcpConnectedAddress->ToString().c_str(), tcpConnectedPort);
}

void NetworkSocketPosix::OnActiveInterfaceChanged(){
	needUpdateNat64Prefix=true;
	isV4Available=false;
	switchToV6at=VoIPController::GetCurrentTime()+ipv6Timeout;
}

std::string NetworkSocketPosix::GetLocalInterfaceInfo(IPv4Address *v4addr, IPv6Address *v6addr){
	std::string name="";
	// Android doesn't support ifaddrs
#ifdef __ANDROID__
	JNIEnv *env=NULL;
	bool didAttach=false;
	sharedJVM->GetEnv((void **) &env, JNI_VERSION_1_6);
	if(!env){
		sharedJVM->AttachCurrentThread(&env, NULL);
		didAttach=true;
	}

	jmethodID getLocalNetworkAddressesAndInterfaceNameMethod=env->GetStaticMethodID(jniUtilitiesClass, "getLocalNetworkAddressesAndInterfaceName", "()[Ljava/lang/String;");
	jobjectArray jinfo=(jobjectArray) env->CallStaticObjectMethod(jniUtilitiesClass, getLocalNetworkAddressesAndInterfaceNameMethod);
	if(jinfo){
		jstring jitfName=static_cast<jstring>(env->GetObjectArrayElement(jinfo, 0));
		jstring jipv4=static_cast<jstring>(env->GetObjectArrayElement(jinfo, 1));
		jstring jipv6=static_cast<jstring>(env->GetObjectArrayElement(jinfo, 2));
		if(jitfName){
			const char *itfchars=env->GetStringUTFChars(jitfName, NULL);
			name=std::string(itfchars);
			env->ReleaseStringUTFChars(jitfName, itfchars);
		}

		if(v4addr && jipv4){
			const char* ipchars=env->GetStringUTFChars(jipv4, NULL);
			*v4addr=IPv4Address(ipchars);
			env->ReleaseStringUTFChars(jipv4, ipchars);
		}
		if(v6addr && jipv6){
			const char* ipchars=env->GetStringUTFChars(jipv6, NULL);
			*v6addr=IPv6Address(ipchars);
			env->ReleaseStringUTFChars(jipv6, ipchars);
		}
	}else{
		LOGW("Failed to get android network interface info");
	}

	if(didAttach){
		sharedJVM->DetachCurrentThread();
	}
#else
	struct ifaddrs* interfaces;
	if(!getifaddrs(&interfaces)){
		struct ifaddrs* interface;
		for(interface=interfaces;interface;interface=interface->ifa_next){
			if(!(interface->ifa_flags & IFF_UP) || !(interface->ifa_flags & IFF_RUNNING) || (interface->ifa_flags & IFF_LOOPBACK))
				continue;
			const struct sockaddr_in* addr=(const struct sockaddr_in*)interface->ifa_addr;
			if(addr){
				if(addr->sin_family==AF_INET){
					if((ntohl(addr->sin_addr.s_addr) & 0xFFFF0000)==0xA9FE0000)
						continue;
					if(v4addr)
						*v4addr=IPv4Address(addr->sin_addr.s_addr);
					name=interface->ifa_name;
				}else if(addr->sin_family==AF_INET6){
					const struct sockaddr_in6* addr6=(const struct sockaddr_in6*)addr;
					if((addr6->sin6_addr.s6_addr[0] & 0xF0)==0xF0)
						continue;
					if(v6addr)
						*v6addr=IPv6Address(addr6->sin6_addr.s6_addr);
					name=interface->ifa_name;
				}
			}
		}
		freeifaddrs(interfaces);
	}
#endif
	return name;
}

uint16_t NetworkSocketPosix::GetLocalPort(){
	sockaddr_in6 addr;
	size_t addrLen=sizeof(sockaddr_in6);
	getsockname(fd, (sockaddr*)&addr, (socklen_t*) &addrLen);
	return ntohs(addr.sin6_port);
}

std::string NetworkSocketPosix::V4AddressToString(uint32_t address){
	char buf[INET_ADDRSTRLEN];
	in_addr addr;
	addr.s_addr=address;
	inet_ntop(AF_INET, &addr, buf, sizeof(buf));
	return std::string(buf);
}

std::string NetworkSocketPosix::V6AddressToString(const unsigned char *address){
	char buf[INET6_ADDRSTRLEN];
	in6_addr addr;
	memcpy(addr.s6_addr, address, 16);
	inet_ntop(AF_INET6, &addr, buf, sizeof(buf));
	return std::string(buf);
}

uint32_t NetworkSocketPosix::StringToV4Address(std::string address){
	in_addr addr;
	inet_pton(AF_INET, address.c_str(), &addr);
	return addr.s_addr;
}

void NetworkSocketPosix::StringToV6Address(std::string address, unsigned char *out){
	in6_addr addr;
	inet_pton(AF_INET6, address.c_str(), &addr);
	memcpy(out, addr.s6_addr, 16);
}

IPv4Address *NetworkSocketPosix::ResolveDomainName(std::string name){
	addrinfo* addr0;
	IPv4Address* ret=NULL;
	int res=getaddrinfo(name.c_str(), NULL, NULL, &addr0);
	if(res!=0){
		LOGW("Error updating NAT64 prefix: %d / %s", res, gai_strerror(res));
	}else{
		addrinfo* addrPtr;
		for(addrPtr=addr0;addrPtr;addrPtr=addrPtr->ai_next){
			if(addrPtr->ai_family==AF_INET){
				sockaddr_in* addr=(sockaddr_in*)addrPtr->ai_addr;
				ret=new IPv4Address(addr->sin_addr.s_addr);
				break;
			}
		}
		freeaddrinfo(addr0);
	}
	return ret;
}

NetworkAddress *NetworkSocketPosix::GetConnectedAddress(){
	return tcpConnectedAddress;
}

uint16_t NetworkSocketPosix::GetConnectedPort(){
	return tcpConnectedPort;
}

void NetworkSocketPosix::SetTimeouts(int sendTimeout, int recvTimeout){
	timeval timeout;
	timeout.tv_sec=sendTimeout;
	timeout.tv_usec=0;
	setsockopt(fd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
	timeout.tv_sec=recvTimeout;
	setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
}

bool NetworkSocketPosix::Select(std::vector<NetworkSocket *> &readFds, std::vector<NetworkSocket*>& writeFds, std::vector<NetworkSocket *> &errorFds, SocketSelectCanceller* _canceller){
	fd_set readSet;
	fd_set writeSet;
	fd_set errorSet;
	FD_ZERO(&readSet);
	FD_ZERO(&writeSet);
	FD_ZERO(&errorSet);
	SocketSelectCancellerPosix* canceller=dynamic_cast<SocketSelectCancellerPosix*>(_canceller);
	if(canceller)
		FD_SET(canceller->pipeRead, &readSet);

	int maxfd=canceller ? canceller->pipeRead : 0;

	for(NetworkSocket*& s:readFds){
		int sfd=GetDescriptorFromSocket(s);
		if(sfd==0){
			LOGW("can't select on one of sockets because it's not a NetworkSocketPosix instance");
			continue;
		}
		FD_SET(sfd, &readSet);
		if(maxfd<sfd)
			maxfd=sfd;
	}

	for(NetworkSocket*& s:writeFds){
		int sfd=GetDescriptorFromSocket(s);
		if(sfd==0){
			LOGW("can't select on one of sockets because it's not a NetworkSocketPosix instance");
			continue;
		}
		FD_SET(sfd, &writeSet);
		if(maxfd<sfd)
			maxfd=sfd;
	}

	bool anyFailed=false;

	for(NetworkSocket*& s:errorFds){
		int sfd=GetDescriptorFromSocket(s);
		if(sfd==0){
			LOGW("can't select on one of sockets because it's not a NetworkSocketPosix instance");
			continue;
		}
		if(s->timeout>0 && VoIPController::GetCurrentTime()-s->lastSuccessfulOperationTime>s->timeout){
			LOGW("Socket %d timed out", sfd);
			s->failed=true;
		}
		anyFailed |= s->IsFailed();
		FD_SET(sfd, &errorSet);
		if(maxfd<sfd)
			maxfd=sfd;
	}

	select(maxfd+1, &readSet, &writeSet, &errorSet, NULL);

	if(canceller && FD_ISSET(canceller->pipeRead, &readSet) && !anyFailed){
		char c;
		(void) read(canceller->pipeRead, &c, 1);
		return false;
	}else if(anyFailed){
		FD_ZERO(&readSet);
		FD_ZERO(&writeSet);
	}

	std::vector<NetworkSocket*>::iterator itr=readFds.begin();
	while(itr!=readFds.end()){
		int sfd=GetDescriptorFromSocket(*itr);
		if(FD_ISSET(sfd, &readSet))
			(*itr)->lastSuccessfulOperationTime=VoIPController::GetCurrentTime();
		if(sfd==0 || !FD_ISSET(sfd, &readSet) || !(*itr)->OnReadyToReceive()){
			itr=readFds.erase(itr);
		}else{
			++itr;
		}
	}

	itr=writeFds.begin();
	while(itr!=writeFds.end()){
		int sfd=GetDescriptorFromSocket(*itr);
		if(sfd==0 || !FD_ISSET(sfd, &writeSet)){
			itr=writeFds.erase(itr);
		}else{
			LOGV("Socket %d is ready to send", sfd);
			(*itr)->lastSuccessfulOperationTime=VoIPController::GetCurrentTime();
			if((*itr)->OnReadyToSend())
				++itr;
			else
				itr=writeFds.erase(itr);
		}
	}

	itr=errorFds.begin();
	while(itr!=errorFds.end()){
		int sfd=GetDescriptorFromSocket(*itr);
		if((sfd==0 || !FD_ISSET(sfd, &errorSet)) && !(*itr)->IsFailed()){
			itr=errorFds.erase(itr);
		}else{
			++itr;
		}
	}
	//LOGV("select fds left: read=%d, write=%d, error=%d", (int)readFds.size(), (int)writeFds.size(), (int)errorFds.size());

	return readFds.size()>0 || errorFds.size()>0 || writeFds.size()>0;
}

SocketSelectCancellerPosix::SocketSelectCancellerPosix(){
	int p[2];
	int pipeRes=pipe(p);
	if(pipeRes!=0){
		LOGE("pipe() failed");
		abort();
	}
	pipeRead=p[0];
	pipeWrite=p[1];
}

SocketSelectCancellerPosix::~SocketSelectCancellerPosix(){
	close(pipeRead);
	close(pipeWrite);
}

void SocketSelectCancellerPosix::CancelSelect(){
	char c=1;
	(void) write(pipeWrite, &c, 1);
}

int NetworkSocketPosix::GetDescriptorFromSocket(NetworkSocket *socket){
	NetworkSocketPosix* sp=dynamic_cast<NetworkSocketPosix*>(socket);
	if(sp)
		return sp->fd;
	NetworkSocketWrapper* sw=dynamic_cast<NetworkSocketWrapper*>(socket);
	if(sw)
		return GetDescriptorFromSocket(sw->GetWrapped());
	return 0;
}
