//
// Created by Grishka on 19/03/2019.
//

#ifndef LIBTGVOIP_PACKETSENDER_H
#define LIBTGVOIP_PACKETSENDER_H

#include "VoIPController.h"
#include <functional>
#include <stdint.h>

namespace tgvoip{
	class PacketSender{
	public:
		PacketSender(VoIPController* controller) : controller(controller) {};
		virtual ~PacketSender(){};
		virtual void PacketAcknowledged(uint32_t seq, double sendTime, double ackTime, uint8_t type, uint32_t size)=0;
		virtual void PacketLost(uint32_t seq, uint8_t type, uint32_t size)=0;

	protected:
		void SendExtra(Buffer& data, unsigned char type){
			controller->SendExtra(data, type);
		}

		void IncrementUnsentStreamPackets(){
			controller->unsentStreamPackets++;
		}

		uint32_t SendPacket(VoIPController::PendingOutgoingPacket pkt){
			uint32_t seq=controller->GenerateOutSeq();
			pkt.seq=seq;
			controller->SendOrEnqueuePacket(std::move(pkt), true, this);
			return seq;
		}

		double GetConnectionInitTime(){
			return controller->connectionInitTime;
		}

		const HistoricBuffer<double, 32>& RTTHistory(){
			return controller->rttHistory;
		}

		MessageThread& GetMessageThread(){
			return controller->messageThread;
		}

		const VoIPController::ProtocolInfo& GetProtocolInfo(){
			return controller->protocolInfo;
		}
		
		void SendStreamFlags(VoIPController::Stream& stm){
			controller->SendStreamFlags(stm);
		}

		const VoIPController::Config& GetConfig(){
			return controller->config;
		}

        VoIPController* controller;
	};
}

#endif //LIBTGVOIP_PACKETSENDER_H
