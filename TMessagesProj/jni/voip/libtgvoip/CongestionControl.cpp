//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "CongestionControl.h"
#include "VoIPController.h"
#include "logging.h"
#include "VoIPServerConfig.h"
#include "PrivateDefines.h"
#include <math.h>
#include <assert.h>

using namespace tgvoip;

CongestionControl::CongestionControl(){
	memset(inflightPackets, 0, sizeof(inflightPackets));
	tmpRtt=0;
	tmpRttCount=0;
	lastSentSeq=0;
	lastActionTime=0;
	lastActionRtt=0;
	stateTransitionTime=0;
	inflightDataSize=0;
	lossCount=0;
	cwnd=(size_t) ServerConfig::GetSharedInstance()->GetInt("audio_congestion_window", 1024);
}

CongestionControl::~CongestionControl(){
}

size_t CongestionControl::GetAcknowledgedDataSize(){
	return 0;
}

double CongestionControl::GetAverageRTT(){
	return rttHistory.NonZeroAverage();
}

size_t CongestionControl::GetInflightDataSize(){
	return inflightHistory.Average();
}


size_t CongestionControl::GetCongestionWindow(){
	return cwnd;
}

double CongestionControl::GetMinimumRTT(){
	return rttHistory.Min();
}

void CongestionControl::PacketAcknowledged(uint32_t seq){
	MutexGuard sync(mutex);
	int i;
	for(i=0;i<100;i++){
		if(inflightPackets[i].seq==seq && inflightPackets[i].sendTime>0){
			tmpRtt+=(VoIPController::GetCurrentTime()-inflightPackets[i].sendTime);
			tmpRttCount++;
			inflightPackets[i].sendTime=0;
			inflightDataSize-=inflightPackets[i].size;
			break;
		}
	}
}

void CongestionControl::PacketSent(uint32_t seq, size_t size){
	if(!seqgt(seq, lastSentSeq) || seq==lastSentSeq){
		LOGW("Duplicate outgoing seq %u", seq);
		return;
	}
	lastSentSeq=seq;
	MutexGuard sync(mutex);
	double smallestSendTime=INFINITY;
	tgvoip_congestionctl_packet_t* slot=NULL;
	int i;
	for(i=0;i<100;i++){
		if(inflightPackets[i].sendTime==0){
			slot=&inflightPackets[i];
			break;
		}
		if(smallestSendTime>inflightPackets[i].sendTime){
			slot=&inflightPackets[i];
			smallestSendTime=slot->sendTime;
		}
	}
	assert(slot!=NULL);
	if(slot->sendTime>0){
		inflightDataSize-=slot->size;
		lossCount++;
		LOGD("Packet with seq %u was not acknowledged", slot->seq);
	}
	slot->seq=seq;
	slot->size=size;
	slot->sendTime=VoIPController::GetCurrentTime();
	inflightDataSize+=size;
}


void CongestionControl::Tick(){
	tickCount++;
	MutexGuard sync(mutex);
	if(tmpRttCount>0){
		rttHistory.Add(tmpRtt/tmpRttCount);
		tmpRtt=0;
		tmpRttCount=0;
	}
	int i;
	for(i=0;i<100;i++){
		if(inflightPackets[i].sendTime!=0 && VoIPController::GetCurrentTime()-inflightPackets[i].sendTime>2){
			inflightPackets[i].sendTime=0;
			inflightDataSize-=inflightPackets[i].size;
			lossCount++;
			LOGD("Packet with seq %u was not acknowledged", inflightPackets[i].seq);
		}
	}
	inflightHistory.Add(inflightDataSize);
}


int CongestionControl::GetBandwidthControlAction(){
	if(VoIPController::GetCurrentTime()-lastActionTime<1)
		return TGVOIP_CONCTL_ACT_NONE;
	size_t inflightAvg=GetInflightDataSize();
	size_t max=cwnd+cwnd/10;
	size_t min=cwnd-cwnd/10;
	if(inflightAvg<min){
		lastActionTime=VoIPController::GetCurrentTime();
		return TGVOIP_CONCTL_ACT_INCREASE;
	}
	if(inflightAvg>max){
		lastActionTime=VoIPController::GetCurrentTime();
		return TGVOIP_CONCTL_ACT_DECREASE;
	}
	return TGVOIP_CONCTL_ACT_NONE;
}


uint32_t CongestionControl::GetSendLossCount(){
	return lossCount;
}
