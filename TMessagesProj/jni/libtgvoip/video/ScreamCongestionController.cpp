//
// Created by Grishka on 25/02/2019.
//

#include <algorithm>
#include <math.h>
#include "ScreamCongestionController.h"
#include "../logging.h"
#include "../VoIPController.h"

using namespace tgvoip;
using namespace tgvoip::video;

namespace{
	/*static*/ constexpr float QDELAY_TARGET_LO=0.1f; // seconds
	/*static*/ constexpr float QDELAY_TARGET_HI=0.4f; // seconds
	/*static*/ constexpr float QDELAY_WEIGHT=0.1f;
	/*static*/ constexpr float QDELAY_TREND_TH=0.2f;
	/*static*/ constexpr uint32_t MIN_CWND=3000; // bytes
	/*static*/ constexpr float MAX_BYTES_IN_FLIGHT_HEAD_ROOM=1.1f;
	/*static*/ constexpr float GAIN=1.0f;
	/*static*/ constexpr float BETA_LOSS=0.8f;
	/*static*/ constexpr float BETA_ECN=0.9f;
	/*static*/ constexpr float BETA_R=0.9f;
	/*static*/ constexpr uint32_t MSS=1024;
	/*static*/ constexpr float RATE_ADJUST_INTERVAL=0.2f;
	/*static*/ constexpr uint32_t TARGET_BITRATE_MIN=50*1024; // bps
	/*static*/ constexpr uint32_t TARGET_BITRATE_MAX=500*1024; // bps
	/*static*/ constexpr uint32_t RAMP_UP_SPEED=200000; // bps/s
	/*static*/ constexpr float PRE_CONGESTION_GUARD=0.1f;
	/*static*/ constexpr float TX_QUEUE_SIZE_FACTOR=1.0f;
	/*static*/ constexpr float RTP_QDELAY_TH=0.02f; // seconds
	/*static*/ constexpr float TARGET_RATE_SCALE_RTP_QDELAY=0.95f;
	/*static*/ constexpr float QDELAY_TREND_LO=0.2f;
	/*static*/ constexpr float T_RESUME_FAST_INCREASE=5.0f; // seconds
	/*static*/ constexpr uint32_t RATE_PACE_MIN=50000; // bps
}

ScreamCongestionController::ScreamCongestionController() : qdelayTarget(QDELAY_TARGET_LO), cwnd(MIN_CWND) {
	
}

void ScreamCongestionController::UpdateVariables(float qdelay){
	float qdelayFraction=qdelay/qdelayTarget;
	qdelayFractionAvg=(1.0f-QDELAY_WEIGHT)*qdelayFractionAvg+qdelayFraction*QDELAY_WEIGHT;
	qdelayFractionHist.Add(qdelayFraction);
	float avg=qdelayFractionHist.Average();

	float r1=0.0, r0=0.0;
	for(int i=(int)qdelayFractionHist.Size()-1;i>=0;i--){
		float v=qdelayFractionHist[i]-avg;
		r0+=v*v;
	}
	for(int i=(int)qdelayFractionHist.Size()-1;i>=1;i--){
		float v1=qdelayFractionHist[i]-avg;
		float v2=qdelayFractionHist[i-1]-avg;
		r1+=v1*v2;
	}
	float a=r1/r0;
	qdelayTrend=std::min(1.0f, std::max(0.0f, a*qdelayFractionAvg));
	qdelayTrendMem=std::max(0.99f*qdelayTrendMem, qdelayTrend);

	if(qdelayTrend>QDELAY_TREND_LO){
		lastTimeQDelayTrendWasGreaterThanLo=VoIPController::GetCurrentTime();
	}
}

void ScreamCongestionController::UpdateCWnd(float qdelay){
	if(inFastIncrease){
		if(qdelayTrend>=QDELAY_TREND_TH){
			inFastIncrease=false;
		}else{
			if((float)bytesInFlight*1.5f+bytesNewlyAcked>cwnd){
				LOGD("HERE");
				cwnd+=bytesNewlyAcked;
			}
			return;
		}
	}

	float offTarget=(qdelayTarget-qdelay)/qdelayTarget;

	float gain=GAIN;
	float cwndDelta=gain*offTarget*bytesNewlyAcked*MSS/(float)cwnd;
	if(offTarget>0 && (float)bytesInFlight*1.25f+bytesNewlyAcked<=cwnd){
		cwndDelta=0.0;
	}
	cwnd+=cwndDelta;
	cwnd=std::min(cwnd, (uint32_t)(maxBytesInFlight*MAX_BYTES_IN_FLIGHT_HEAD_ROOM));
	cwnd=std::max(cwnd, MIN_CWND);
}

void ScreamCongestionController::AdjustQDelayTarget(float qdelay){
	float qdelayNorm=qdelay/QDELAY_TARGET_LO;
	qdelayNormHist.Add(qdelayNorm);

	float qdelayNormAvg=qdelayNormHist.Average();
	float qdelayNormVar=0.0;
	for(uint32_t i=0;i<qdelayNormHist.Size();i++){
		float tmp=qdelayNormHist[i]-qdelayNormAvg;
		qdelayNormVar+=tmp*tmp;
	}
	qdelayNormVar/=qdelayNormHist.Size();

	float newTarget=qdelayNormAvg+sqrt(qdelayNormVar);
	newTarget*=QDELAY_TARGET_LO;

	if(lossEventRate>0.002f){
		qdelayTarget=1.5f*newTarget;
	}else{
		if(qdelayNormVar<0.2f){
			qdelayTarget=newTarget;
		}else{
			if(newTarget<QDELAY_TARGET_LO){
				qdelayTarget=std::max(qdelayTarget*0.5f, newTarget);
			}else{
				qdelayTarget*=0.9;
			}
		}
	}

	qdelayTarget=std::min(QDELAY_TARGET_HI, qdelayTarget);
	qdelayTarget=std::max(QDELAY_TARGET_LO, qdelayTarget);
}

void ScreamCongestionController::AdjustBitrate(){
	if(lossPending){
		lossPending=false;
		targetBitrate=std::max((uint32_t)(BETA_R*(float)targetBitrate), TARGET_BITRATE_MIN);
		return;
	}

	float rampUpSpeed=std::min(RAMP_UP_SPEED, targetBitrate/2);
	float scale=(float)(targetBitrate-targetBitrateLastMax)/(float)targetBitrateLastMax;
	scale=std::max(0.2f, std::min(1.0f, (scale*4)*(scale*4)));
	float currentRate=std::max(rateTransmit, rateAck);

	if(inFastIncrease){
		float increment=(rampUpSpeed*RATE_ADJUST_INTERVAL)*scale;
		targetBitrate+=increment;
	}else{
		float deltaRate=currentRate*(1.0f-PRE_CONGESTION_GUARD*qdelayTrend)-TX_QUEUE_SIZE_FACTOR*(float)rtpQueueSize;
		if(deltaRate>0.0f){
			deltaRate*=scale;
			deltaRate=std::min(deltaRate, rampUpSpeed*RATE_ADJUST_INTERVAL);
		}
		targetBitrate+=deltaRate;
		float rtpQueueDelay=(float)rtpQueueSize/currentRate;
		if(rtpQueueDelay>RTP_QDELAY_TH){
			targetBitrate=(uint32_t)((float)targetBitrate*TARGET_RATE_SCALE_RTP_QDELAY);
		}
	}

	float rateMediaLimit=std::max(currentRate, std::max(rateMedia, rateMediaMedian));
	rateMediaLimit*=(2.0-qdelayTrendMem);
	targetBitrate=std::min(targetBitrate, (uint32_t)rateMediaLimit);
	targetBitrate=std::min(TARGET_BITRATE_MAX, std::max(TARGET_BITRATE_MIN, targetBitrate));
}

void ScreamCongestionController::CalculateSendWindow(float qdelay){
	if(qdelay<=qdelayTarget)
		sendWnd=cwnd+MSS-bytesInFlight;
	else
		sendWnd=cwnd-bytesInFlight;
}

void ScreamCongestionController::ProcessAcks(float oneWayDelay, uint32_t bytesNewlyAcked, uint32_t lossCount, double rtt){
    if(prevOneWayDelay!=0.0f){
    	double currentTime=VoIPController::GetCurrentTime();
    	float qdelay=oneWayDelay-prevOneWayDelay;
    	sRTT=(float)rtt;
		bytesInFlight-=bytesNewlyAcked;
		rtpQueueSize-=(bytesNewlyAcked*8);
		UpdateBytesInFlightHistory();
		bytesAcked+=bytesNewlyAcked;
    	//LOGV("Scream: qdelay = %f, newly acked = %u, in flight = %u, losses = %u", qdelay, bytesNewlyAcked, bytesInFlight, lossCount);
		if(currentTime-lastVariablesUpdateTime>=0.050){
			lastVariablesUpdateTime=currentTime;
			UpdateVariables(qdelay);
		}
		if(currentTime-lastRateAdjustmentTime>=RATE_ADJUST_INTERVAL){
			lastRateAdjustmentTime=currentTime;
			AdjustBitrate();
			//LOGV("Scream: target bitrate = %u", targetBitrate);
		}
    	if(lossCount>prevLossCount && currentTime>ignoreLossesUntil){
    		LOGD("Scream: loss detected");
    		ignoreLossesUntil=currentTime+rtt;
    		inFastIncrease=false;
    		cwnd=std::max(MIN_CWND, (uint32_t)(cwnd*BETA_LOSS));
    		AdjustQDelayTarget(qdelay);
			CalculateSendWindow(qdelay);
			lossPending=true;
			prevLossCount=lossCount;
			lastTimeQDelayTrendWasGreaterThanLo=currentTime;
    	}else{
    		this->bytesNewlyAcked+=bytesNewlyAcked;
    		if(currentTime-lastCWndUpdateTime>=0.15){
    			lastCWndUpdateTime=currentTime;
				UpdateCWnd(qdelay);
				//LOGI("Scream: cwnd = %u", cwnd);
				this->bytesNewlyAcked=0;
			}
    		AdjustQDelayTarget(qdelay);
    		CalculateSendWindow(qdelay);
    		if(!inFastIncrease){
    			if(currentTime-lastTimeQDelayTrendWasGreaterThanLo>=T_RESUME_FAST_INCREASE){
    				inFastIncrease=true;
    			}
    		}
    	}
    }
    prevOneWayDelay=oneWayDelay;
}

void ScreamCongestionController::ProcessPacketSent(uint32_t size){
	bytesInFlight+=size;
	rtpQueueSize+=(size*8);
	bytesSent+=size;
	double currentTime=VoIPController::GetCurrentTime();
	if(currentTime-rateTransmitUpdateTime>=0.2){
		rateTransmit=(float)(bytesSent*8)/(float)(currentTime-rateTransmitUpdateTime);
		rateAck=(float)(bytesAcked*8)/(float)(currentTime-rateTransmitUpdateTime);
		rateTransmitUpdateTime=currentTime;
		bytesSent=0;
		bytesAcked=0;
		//LOGV("rateTransmit %f, rateAck %f", rateTransmit, rateAck);
	}
	UpdateBytesInFlightHistory();
}

void ScreamCongestionController::ProcessPacketLost(uint32_t size){
	bytesInFlight-=size;
	rtpQueueSize-=(size*8);
	UpdateBytesInFlightHistory();
}

double ScreamCongestionController::GetPacingInterval(){
	float paceBitrate=std::max((float)RATE_PACE_MIN, cwnd*8.0f/sRTT);
	//LOGV("RTT=%f cwnd=%u paceBitrate=%f fastIncrease=%d", sRTT, cwnd, paceBitrate, inFastIncrease);
	double pacingInterval=rtpSize*8.0f/paceBitrate;
	return std::min(0.010, pacingInterval);
}

void ScreamCongestionController::UpdateBytesInFlightHistory(){
	double currentTime=VoIPController::GetCurrentTime();
	ValueSample now={bytesInFlight, currentTime};
	bytesInFlightHistory.push_back(now);
	uint32_t max=0;
	for(std::vector<ValueSample>::iterator i=bytesInFlightHistory.begin();i!=bytesInFlightHistory.end();){
		if(currentTime-i->time>=5.0){
			i=bytesInFlightHistory.erase(i);
		}else{
			max=std::max(max, i->sample);
			++i;
		}
	}
	maxBytesInFlight=max;
}

void ScreamCongestionController::UpdateMediaRate(uint32_t frameSize){
	bytesMedia+=frameSize;
	double currentTime=VoIPController::GetCurrentTime();
	if(currentTime-rateMediaUpdateTime>=0.5){
		rateMedia=(float)(bytesMedia*8)/(float)(currentTime-rateMediaUpdateTime);
		bytesMedia=0;
		rateMediaUpdateTime=currentTime;
		LOGV("rateMedia %f", rateMedia);
		rateMediaHistory.Add(rateMedia);
		rateMediaMedian=rateMediaHistory.NonZeroAverage();
	}
}

uint32_t ScreamCongestionController::GetBitrate(){
	return targetBitrate;
}
