//
// Created by Grishka on 19.03.2018.
//

#include "PacketReassembler.h"
#include "logging.h"
#include "PrivateDefines.h"
#include "video/VideoFEC.h"

#include <assert.h>
#include <sstream>

#define NUM_OLD_PACKETS 3
#define NUM_FEC_PACKETS 10

using namespace tgvoip;
using namespace tgvoip::video;

PacketReassembler::PacketReassembler(){
}

PacketReassembler::~PacketReassembler(){
}

void PacketReassembler::Reset(){

}

void PacketReassembler::AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount, uint32_t pts, uint8_t _fseq, bool keyframe, uint16_t rotation){
	for(std::unique_ptr<Packet>& packet:packets){
		if(packet->timestamp==pts){
			if(fragmentCount!=packet->partCount){
				LOGE("Received fragment total count %u inconsistent with previous %u", fragmentCount, packet->partCount);
				return;
			}
			if(fragmentIndex>=packet->partCount){
				LOGE("Received fragment index %u is greater than total %u", fragmentIndex, fragmentCount);
				return;
			}
			packet->AddFragment(std::move(pkt), fragmentIndex);
			return;
		}
	}
	uint32_t fseq=(lastFrameSeq & 0xFFFFFF00) | (uint32_t)_fseq;
	if((uint8_t)lastFrameSeq>_fseq)
		fseq+=256;
	//LOGV("fseq: %u", (unsigned int)fseq);

	/*if(pts<maxTimestamp){
		LOGW("Received fragment doesn't belong here (ts=%u < maxTs=%u)", pts, maxTimestamp);
		return;
	}*/
	if(lastFrameSeq>3 && fseq<lastFrameSeq-3){
		LOGW("Packet too late (fseq=%u, lastFseq=%u)", fseq, lastFrameSeq);
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

	packets.push_back(std::unique_ptr<Packet>(new Packet(fseq, pts, fragmentCount, 0, keyframe, rotation)));
	packets[packets.size()-1]->AddFragment(std::move(pkt), fragmentIndex);
	while(packets.size()>3){
		std::unique_ptr<Packet>& _old=packets[0];
		if(_old->receivedPartCount==_old->partCount){
			std::unique_ptr<Packet> old=std::move(packets[0]);
			packets.erase(packets.begin());

			Buffer buffer=old->Reassemble();
			callback(std::move(buffer), old->seq, old->isKeyframe, old->rotation);
			oldPackets.push_back(std::move(old));
			while(oldPackets.size()>NUM_OLD_PACKETS)
				oldPackets.erase(oldPackets.begin());
		}else{
			LOGW("Packet %u not reassembled (%u of %u)", packets[0]->seq, packets[0]->receivedPartCount, packets[0]->partCount);
			if(packets[0]->partCount-packets[0]->receivedPartCount==1 && !waitingForFEC){
				bool found=false;
				for(FecPacket& fec:fecPackets){
					if(packets[0]->seq<=fec.seq && packets[0]->seq>fec.seq-fec.prevFrameCount){
						LOGI("Found FEC packet: %u %u", fec.seq, fec.prevFrameCount);
						found=true;
						TryDecodeFEC(fec);
						packets.erase(packets.begin());
						break;
					}
				}
				if(!found){
					waitingForFEC=true;
					break;
				}
			}else{
				waitingForFEC=false;
				LOGE("unrecoverable packet loss");
				std::unique_ptr<Packet> old=std::move(packets[0]);
				packets.erase(packets.begin());
				oldPackets.push_back(std::move(old));
				while(oldPackets.size()>NUM_OLD_PACKETS)
					oldPackets.erase(oldPackets.begin());
			}
		}
	}

	lastFrameSeq=fseq;
}

void PacketReassembler::AddFEC(Buffer data, uint8_t _fseq, unsigned int frameCount, unsigned int fecScheme){
	uint32_t fseq=(lastFrameSeq & 0xFFFFFF00) | (uint32_t)_fseq;
	std::ostringstream _s;
	for(unsigned int i=0;i<frameCount;i++){
		_s << (fseq-i);
		_s << " ";
	}
	//LOGV("Received FEC packet: len %u, scheme %u, frames %s", (unsigned int)data.Length(), fecScheme, _s.str().c_str());
	FecPacket fec{
		fseq,
		frameCount,
		fecScheme,
		std::move(data)
	};

	if(waitingForFEC){
		if(packets[0]->seq<=fec.seq && packets[0]->seq>fec.seq-fec.prevFrameCount){
			LOGI("Found FEC packet: %u %u", fec.seq, fec.prevFrameCount);
			TryDecodeFEC(fec);
			packets.erase(packets.begin());
			waitingForFEC=false;
		}
	}
	fecPackets.push_back(std::move(fec));
	while(fecPackets.size()>NUM_FEC_PACKETS)
		fecPackets.erase(fecPackets.begin());
}

void PacketReassembler::SetCallback(std::function<void(Buffer packet, uint32_t pts, bool keyframe, uint16_t rotation)> callback){
	this->callback=callback;
}

bool PacketReassembler::TryDecodeFEC(PacketReassembler::FecPacket &fec){
	/*LOGI("Decoding FEC");

	std::vector<Buffer> packetsForRecovery;
	for(std::unique_ptr<Packet>& p:oldPackets){
		if(p->seq<=fec.seq && p->seq>fec.seq-fec.prevFrameCount){
			LOGD("Adding frame %u from old", p->seq);
			for(uint32_t i=0;i<p->partCount;i++){
				packetsForRecovery.push_back(i<p->parts.size() ? Buffer::CopyOf(p->parts[i]) : Buffer());
			}
		}
	}

	for(std::unique_ptr<Packet>& p:packets){
		if(p->seq<=fec.seq && p->seq>fec.seq-fec.prevFrameCount){
			LOGD("Adding frame %u from pending", p->seq);
			for(uint32_t i=0;i<p->partCount;i++){
				//LOGV("[%u] size %u", i, p.parts[i].Length());
				packetsForRecovery.push_back(i<p->parts.size() ? Buffer::CopyOf(p->parts[i]) : Buffer());
			}
		}
	}

	if(fec.fecScheme==FEC_SCHEME_XOR){
		Buffer recovered=ParityFEC::Decode(packetsForRecovery, fec.data);
		LOGI("Recovered packet size %u", (unsigned int)recovered.Length());
		if(!recovered.IsEmpty()){
			std::unique_ptr<Packet>& pkt=packets[0];
			if(pkt->parts.size()<pkt->partCount){
				pkt->parts.push_back(std::move(recovered));
			}else{
				for(Buffer &b:pkt->parts){
					if(b.IsEmpty()){
						b=std::move(recovered);
						break;
					}
				}
			}
			pkt->receivedPartCount++;
			callback(pkt->Reassemble(), pkt->seq, pkt->isKeyframe, pkt->rotation);
		}
	}*/

	return false;
}

#pragma mark - Packet

void PacketReassembler::Packet::AddFragment(Buffer pkt, uint32_t fragmentIndex){
	//LOGV("Add fragment %u/%u to packet %u", fragmentIndex, partCount, timestamp);
	if(parts.size()==fragmentIndex){
		parts.push_back(std::move(pkt));
		//LOGV("add1");
	}else if(parts.size()>fragmentIndex){
		assert(parts[fragmentIndex].IsEmpty());
		parts[fragmentIndex]=std::move(pkt);
		//LOGV("add2");
	}else{
		while(parts.size()<fragmentIndex)
			parts.push_back(Buffer());
		parts.push_back(std::move(pkt));
		//LOGV("add3");
	}
	receivedPartCount++;
	//assert(parts.size()>=receivedPartCount);
	if(parts.size()<receivedPartCount)
		LOGW("Received %u parts but parts.size is %u", (unsigned int)receivedPartCount, (unsigned int)parts.size());
}

Buffer PacketReassembler::Packet::Reassemble(){
	assert(partCount==receivedPartCount);
	assert(parts.size()==partCount);
	if(partCount==1){
		return Buffer::CopyOf(parts[0]);
	}
	BufferOutputStream out(10240);
	for(unsigned int i=0;i<partCount;i++){
		out.WriteBytes(parts[i]);
		//parts[i]=Buffer();
	}
	return Buffer(std::move(out));
}
