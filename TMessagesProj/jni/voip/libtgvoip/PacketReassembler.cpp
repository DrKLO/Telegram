//
// Created by Grishka on 19.03.2018.
//

#include "PacketReassembler.h"
#include "logging.h"

#include <assert.h>

using namespace tgvoip;

PacketReassembler::PacketReassembler(){

}

PacketReassembler::~PacketReassembler(){

}

void PacketReassembler::Reset(){

}

void PacketReassembler::AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount, uint32_t pts, bool keyframe, uint16_t rotation){
	for(Packet& packet:packets){
		if(packet.timestamp==pts){
			if(fragmentCount!=packet.partCount){
				LOGE("Received fragment total count %u inconsistent with previous %u", fragmentCount, packet.partCount);
				return;
			}
			packet.AddFragment(std::move(pkt), fragmentIndex);
			return;
		}
	}

	if(pts<maxTimestamp){
		LOGW("Received fragment doesn't belong here (ts=%u < maxTs=%u)", pts, maxTimestamp);
		return;
	}
	if(fragmentIndex>=fragmentCount){
		LOGE("Received fragment index %u is out of bounds %u", fragmentIndex, fragmentCount);
		return;
	}
	if(fragmentCount>255){
		LOGE("Received fragment total count too big %u", fragmentCount);
		return;
	}

	maxTimestamp=std::max(maxTimestamp, pts);

	Packet packet(fragmentCount);
	packet.timestamp=pts;
	packet.isKeyframe=keyframe;
	packet.receivedPartCount=0;
	packet.rotation=rotation;
	packet.AddFragment(std::move(pkt), fragmentIndex);

	packets.push_back(std::move(packet));
	while(packets.size()>3){
		Packet&& old=std::move(packets[0]);
		packets.erase(packets.begin());
		if(old.receivedPartCount==old.partCount){
			Buffer buffer=old.Reassemble();
			callback(std::move(buffer), old.timestamp, old.isKeyframe, old.rotation);
			//LOGV("Packet %u reassembled", old.timestamp);
		}else{
			LOGW("Packet %u not reassembled (%u of %u)", old.timestamp, old.receivedPartCount, old.partCount);
		}
	}
}

void PacketReassembler::SetCallback(std::function<void(Buffer packet, uint32_t pts, bool keyframe, uint16_t rotation)> callback){
	this->callback=callback;
}

void PacketReassembler::Packet::AddFragment(Buffer pkt, uint32_t fragmentIndex){
	//LOGV("Add fragment %u/%u to packet %u", fragmentIndex, partCount, timestamp);
	parts[fragmentIndex]=std::move(pkt);
	receivedPartCount++;
}

Buffer PacketReassembler::Packet::Reassemble(){
	if(partCount==1){
		return std::move(parts[0]);
	}
	BufferOutputStream out(10240);
	for(unsigned int i=0;i<partCount;i++){
		out.WriteBytes(parts[i]);
		parts[i]=Buffer();
	}
	return Buffer(std::move(out));
}
