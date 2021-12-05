//
// Created by Grishka on 19.03.2018.
//

#ifndef TGVOIP_PACKETREASSEMBLER_H
#define TGVOIP_PACKETREASSEMBLER_H

#include <vector>
#include <functional>
#include <unordered_map>

#include "Buffers.h"

namespace tgvoip {
	class PacketReassembler{
	public:
		PacketReassembler();
		virtual ~PacketReassembler();

		void Reset();
		void AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount, uint32_t pts, bool keyframe, uint16_t rotation);
		void SetCallback(std::function<void(Buffer packet, uint32_t pts, bool keyframe, uint16_t rotation)> callback);

	private:
		struct Packet{
			uint32_t timestamp;
			uint32_t partCount;
			uint32_t receivedPartCount;
			bool isKeyframe;
			uint16_t rotation;
			Buffer* parts;

			TGVOIP_DISALLOW_COPY_AND_ASSIGN(Packet);

			Packet(Packet&& other) : timestamp(other.timestamp), partCount(other.partCount), receivedPartCount(other.receivedPartCount), isKeyframe(other.isKeyframe), rotation(other.rotation){
				parts=other.parts;
				other.parts=NULL;
			}
			Packet& operator=(Packet&& other){
				if(&other!=this){
					if(parts)
						delete[] parts;
					parts=other.parts;
					other.parts=NULL;
					timestamp=other.timestamp;
					partCount=other.partCount;
					receivedPartCount=other.receivedPartCount;
					isKeyframe=other.isKeyframe;
					rotation=other.rotation;
				}
				return *this;
			}

			Packet(uint32_t partCount) : partCount(partCount){
				parts=new Buffer[partCount];
			}
			~Packet(){
				if(parts)
					delete[] parts;
			}


			void AddFragment(Buffer pkt, uint32_t fragmentIndex);
			Buffer Reassemble();
		};
		std::function<void(Buffer, uint32_t, bool, uint16_t)> callback;
		std::vector<Packet> packets;
		uint32_t maxTimestamp=0;
	};
}

#endif //TGVOIP_PACKETREASSEMBLER_H
