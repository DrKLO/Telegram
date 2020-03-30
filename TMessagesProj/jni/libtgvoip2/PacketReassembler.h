//
// Created by Grishka on 19.03.2018.
//

#ifndef TGVOIP_PACKETREASSEMBLER_H
#define TGVOIP_PACKETREASSEMBLER_H

#include <vector>
#include <functional>
#include <unordered_map>
#include <memory>

#include "Buffers.h"
#include "logging.h"

namespace tgvoip {
	class PacketReassembler{
	public:
		PacketReassembler();
		virtual ~PacketReassembler();

		void Reset();
		void AddFragment(Buffer pkt, unsigned int fragmentIndex, unsigned int fragmentCount, uint32_t pts, uint8_t fseq, bool keyframe, uint16_t rotation);
		void AddFEC(Buffer data, uint8_t fseq, unsigned int frameCount, unsigned int fecScheme);
		void SetCallback(std::function<void(Buffer packet, uint32_t pts, bool keyframe, uint16_t rotation)> callback);

	private:
		struct Packet{
			uint32_t seq;
			uint32_t timestamp;
			uint32_t partCount;
			uint32_t receivedPartCount;
			bool isKeyframe;
			uint16_t rotation;
			std::vector<Buffer> parts;
			
			Packet(uint32_t seq, uint32_t timestamp, uint32_t partCount, uint32_t receivedPartCount, bool keyframe, uint16_t rotation)
    			:seq(seq), timestamp(timestamp), partCount(partCount), receivedPartCount(receivedPartCount), isKeyframe(keyframe), rotation(rotation){
			
			}

			void AddFragment(Buffer pkt, uint32_t fragmentIndex);
			Buffer Reassemble();
		};
		struct FecPacket{
			uint32_t seq;
			uint32_t prevFrameCount;
			uint32_t fecScheme;
			Buffer data;
		};

		bool TryDecodeFEC(FecPacket& fec);

		std::function<void(Buffer, uint32_t, bool, uint16_t)> callback;
		std::vector<std::unique_ptr<Packet>> packets;
		std::vector<std::unique_ptr<Packet>> oldPackets; // for FEC
		std::vector<FecPacket> fecPackets;
		uint32_t maxTimestamp=0;
		uint32_t lastFrameSeq=0;
		bool waitingForFEC=false;
	};
}

#endif //TGVOIP_PACKETREASSEMBLER_H
