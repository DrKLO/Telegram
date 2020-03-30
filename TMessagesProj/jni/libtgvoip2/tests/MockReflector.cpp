//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "MockReflector.h"
#include <arpa/inet.h>
#include <assert.h>
#include <stdio.h>

using namespace tgvoip;
using namespace tgvoip::test;

struct UdpReflectorSelfInfo{
	uint8_t peerTag[16];
	uint64_t _id1=0xFFFFFFFFFFFFFFFFLL;
	uint32_t _id2=0xFFFFFFFF;
	uint32_t magic=0xc01572c7;
	int32_t date;
	uint64_t query_id;
	uint64_t my_ip_padding1;
	uint32_t my_ip_padding2;
	uint32_t my_ip;
	uint32_t my_port;
} __attribute__((packed));

MockReflector::MockReflector(std::string bindAddress, uint16_t bindPort){
	sfd=socket(PF_INET, SOCK_DGRAM, IPPROTO_UDP);
	assert(sfd!=-1);
	sockaddr_in bindAddr={0};
	bindAddr.sin_family=AF_INET;
	bindAddr.sin_port=htons(bindPort);
	inet_aton(bindAddress.c_str(), &bindAddr.sin_addr);
	int res=bind(sfd, (struct sockaddr*)&bindAddr, sizeof(bindAddr));
	assert(res==0);
}

MockReflector::~MockReflector(){

}

std::array<std::array<uint8_t, 16>, 2> MockReflector::GeneratePeerTags(){
	std::array<uint8_t, 16> tag1;
	for(int i=0;i<16;i++){
		tag1[i]=(uint8_t)rand();
	}
	tag1[15] &= 0xFE;
	std::array<std::array<uint8_t, 16>, 2> res;
	res[0]=tag1;
	std::copy(tag1.begin(), tag1.end(), res[1].begin());
	res[1][15] |= 1;
	return res;
}

void MockReflector::Start(){
	if(running)
		return;
	running=true;
	pthread_create(&thread, NULL, [](void* arg) -> void* {
		reinterpret_cast<MockReflector*>(arg)->RunThread();
		return NULL;
	}, this);
}

void MockReflector::Stop(){
	running=false;
	shutdown(sfd, SHUT_RDWR);
	close(sfd);
	pthread_join(thread, NULL);
}

void MockReflector::SetDropAllPackets(bool drop){
	dropAllPackets=drop;
}

void MockReflector::RunThread(){
	while(running){
		std::array<uint8_t, 1500> buf;
		sockaddr_in addr;
		socklen_t addrlen=sizeof(addr);
		ssize_t len=recvfrom(sfd, buf.data(), sizeof(buf), 0, (struct sockaddr*)&addr, &addrlen);
		if(len<=0)
			return;
		if(len>=32){
			std::array<uint8_t, 16> peerTag;
			int32_t specialID[4];
			std::copy(buf.begin(), buf.begin()+16, peerTag.begin());
			memcpy(specialID, buf.data()+16, 16);
			uint64_t tagID=*reinterpret_cast<uint64_t*>(peerTag.data());
			ClientPair c=clients[tagID];
			sockaddr_in* dest;
			if(peerTag[15] & 1){
				c.addr1=addr;
				dest=&c.addr0;
			}else{
				c.addr0=addr;
				dest=&c.addr1;
			}
			clients[tagID]=c;

			if(specialID[0]==-1 && specialID[1]==-1 && specialID[2]==-1){
				if(specialID[3]==-1){
					continue;
				}else if(specialID[3]==-2){
					UdpReflectorSelfInfo response;
					memcpy(response.peerTag, peerTag.data(), 16);
					response.date=(int32_t)time(NULL);
					response.query_id=*reinterpret_cast<uint64_t*>(buf.data()+32);
					response.my_ip_padding1=0;
					response.my_ip_padding2=0xFFFF0000;
					response.my_ip=(uint32_t)addr.sin_addr.s_addr;
					response.my_port=ntohs(addr.sin_port);
					sendto(sfd, &response, sizeof(response), 0, (struct sockaddr*)&addr, sizeof(addr));
					continue;
				}
			}

			if(dest->sin_family==AF_INET && !dropAllPackets){
				if(peerTag[15] & 1)
					buf[15] &= 0xFE;
				else
					buf[15] |= 1;

				sendto(sfd, buf.data(), len, 0, (struct sockaddr*)dest, sizeof(sockaddr_in));
			}
		}
	}
}
