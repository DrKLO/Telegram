//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "VoIPController.h"
#include "JitterBuffer.h"
#include "logging.h"
#include "VoIPServerConfig.h"
#include <math.h>

using namespace tgvoip;

JitterBuffer::JitterBuffer(MediaStreamItf *out, uint32_t step):bufferPool(JITTER_SLOT_SIZE, JITTER_SLOT_COUNT){
	if(out)
		out->SetCallback(JitterBuffer::CallbackOut, this);
	this->step=step;
	memset(slots, 0, sizeof(jitter_packet_t)*JITTER_SLOT_COUNT);
	if(step<30){
		minMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_20", 6);
		maxMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_20", 25);
		maxUsedSlots=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_20", 50);
	}else if(step<50){
		minMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_40", 4);
		maxMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_40", 15);
		maxUsedSlots=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_40", 30);
	}else{
		minMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_60", 2);
		maxMinDelay=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_60", 10);
		maxUsedSlots=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_60", 20);
	}
	lossesToReset=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_losses_to_reset", 20);
	resyncThreshold=ServerConfig::GetSharedInstance()->GetDouble("jitter_resync_threshold", 1.0);
#ifdef TGVOIP_DUMP_JITTER_STATS
#ifdef TGVOIP_JITTER_DUMP_FILE
	dump=fopen(TGVOIP_JITTER_DUMP_FILE, "w");
#elif defined(__ANDROID__)
	dump=fopen("/sdcard/tgvoip_jitter_dump.txt", "w");
#else
	dump=fopen("tgvoip_jitter_dump.txt", "w");
#endif
	tgvoip_log_file_write_header(dump);
	fprintf(dump, "PTS\tRTS\tNumInBuf\tAJitter\tADelay\tTDelay\n");
#endif
	Reset();
}

JitterBuffer::~JitterBuffer(){
	Reset();
}

void JitterBuffer::SetMinPacketCount(uint32_t count){
	LOGI("jitter: set min packet count %u", count);
	minDelay=count;
	minMinDelay=count;
	//Reset();
}

int JitterBuffer::GetMinPacketCount(){
	return (int)minDelay;
}

size_t JitterBuffer::CallbackIn(unsigned char *data, size_t len, void *param){
	//((JitterBuffer*)param)->HandleInput(data, len);
	return 0;
}

size_t JitterBuffer::CallbackOut(unsigned char *data, size_t len, void *param){
	return 0; //((JitterBuffer*)param)->HandleOutput(data, len, 0, NULL);
}

void JitterBuffer::HandleInput(unsigned char *data, size_t len, uint32_t timestamp, bool isEC){
	MutexGuard m(mutex);
	jitter_packet_t pkt;
	pkt.size=len;
	pkt.buffer=data;
	pkt.timestamp=timestamp;
	pkt.isEC=isEC;
	PutInternal(&pkt, !isEC);
	//LOGV("in, ts=%d, ec=%d", timestamp, isEC);

}

void JitterBuffer::Reset(){
	wasReset=true;
	needBuffering=true;
	lastPutTimestamp=0;
	int i;
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer){
			bufferPool.Reuse(slots[i].buffer);
			slots[i].buffer=NULL;
		}
	}
	delayHistory.Reset();
	lateHistory.Reset();
	adjustingDelay=false;
	lostSinceReset=0;
	gotSinceReset=0;
	expectNextAtTime=0;
	deviationHistory.Reset();
	outstandingDelayChange=0;
	dontChangeDelay=0;
}


size_t JitterBuffer::HandleOutput(unsigned char *buffer, size_t len, int offsetInSteps, bool advance, int& playbackScaledDuration, bool& isEC){
	jitter_packet_t pkt;
	pkt.buffer=buffer;
	pkt.size=len;
	MutexGuard m(mutex);
	if(first){
		first=false;
		unsigned int delay=GetCurrentDelay();
		if(GetCurrentDelay()>5){
			LOGW("jitter: delay too big upon start (%u), dropping packets", delay);
			while(delay>GetMinPacketCount()){
				for(int i=0;i<JITTER_SLOT_COUNT;i++){
					if(slots[i].timestamp==nextTimestamp){
						if(slots[i].buffer){
    						bufferPool.Reuse(slots[i].buffer);
    						slots[i].buffer=NULL;
						}
						break;
					}
				}
				Advance();
				delay--;
			}
		}
	}
	int result=GetInternal(&pkt, offsetInSteps, advance);
	if(outstandingDelayChange!=0){
		if(outstandingDelayChange<0){
			playbackScaledDuration=40;
			outstandingDelayChange+=20;
		}else{
			playbackScaledDuration=80;
			outstandingDelayChange-=20;
		}
		//LOGV("outstanding delay change: %d", outstandingDelayChange);
	}else if(advance && GetCurrentDelay()==0){
		//LOGV("stretching packet because the next one is late");
		playbackScaledDuration=80;
	}else{
		playbackScaledDuration=60;
	}
	if(result==JR_OK){
		isEC=pkt.isEC;
		return pkt.size;
	}else{
		return 0;
	}
}


int JitterBuffer::GetInternal(jitter_packet_t* pkt, int offset, bool advance){
	/*if(needBuffering && lastPutTimestamp<nextTimestamp){
		LOGV("jitter: don't have timestamp %lld, buffering", (long long int)nextTimestamp);
		Advance();
		return JR_BUFFERING;
	}*/

	//needBuffering=false;

	int64_t timestampToGet=nextTimestamp+offset*(int32_t)step;

	int i;
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer!=NULL && slots[i].timestamp==timestampToGet){
			break;
		}
	}

	if(i<JITTER_SLOT_COUNT){
		if(pkt && pkt->size<slots[i].size){
			LOGE("jitter: packet won't fit into provided buffer of %d (need %d)", int(slots[i].size), int(pkt->size));
		}else{
			if(pkt) {
				pkt->size = slots[i].size;
				pkt->timestamp = slots[i].timestamp;
				memcpy(pkt->buffer, slots[i].buffer, slots[i].size);
				pkt->isEC=slots[i].isEC;
			}
		}
		bufferPool.Reuse(slots[i].buffer);
		slots[i].buffer=NULL;
		if(offset==0)
			Advance();
		lostCount=0;
		needBuffering=false;
		return JR_OK;
	}

	LOGV("jitter: found no packet for timestamp %lld (last put = %d, lost = %d)", (long long int)timestampToGet, lastPutTimestamp, lostCount);

	if(advance)
		Advance();

	if(!needBuffering){
		lostCount++;
		if(offset==0){
			lostPackets++;
			lostSinceReset++;
		}
		if(lostCount>=lossesToReset || (gotSinceReset>minDelay*25 && lostSinceReset>gotSinceReset/2)){
			LOGW("jitter: lost %d packets in a row, resetting", lostCount);
			//minDelay++;
			dontIncMinDelay=16;
			dontDecMinDelay+=128;
			if(GetCurrentDelay()<minDelay)
				nextTimestamp-=(int64_t)(minDelay-GetCurrentDelay());
			lostCount=0;
			Reset();
		}

		return JR_MISSING;
	}
	return JR_BUFFERING;
}

void JitterBuffer::PutInternal(jitter_packet_t* pkt, bool overwriteExisting){
	if(pkt->size>JITTER_SLOT_SIZE){
		LOGE("The packet is too big to fit into the jitter buffer");
		return;
	}

	int i;
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer && slots[i].timestamp==pkt->timestamp){
			//LOGV("Found existing packet for timestamp %u, overwrite %d", pkt->timestamp, overwriteExisting);
			if(overwriteExisting){
				memcpy(slots[i].buffer, pkt->buffer, pkt->size);
				slots[i].size=pkt->size;
				slots[i].isEC=pkt->isEC;
			}
			return;
		}
	}
	gotSinceReset++;
	if(wasReset){
		wasReset=false;
		outstandingDelayChange=0;
		nextTimestamp=(int64_t)(((int64_t)pkt->timestamp)-step*minDelay);
		first=true;
		LOGI("jitter: resyncing, next timestamp = %lld (step=%d, minDelay=%f)", (long long int)nextTimestamp, step, minDelay);
	}
	
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer!=NULL){
			if(slots[i].timestamp<nextTimestamp-1){
				bufferPool.Reuse(slots[i].buffer);
				slots[i].buffer=NULL;
			}
		}
	}

	/*double prevTime=0;
	uint32_t closestTime=0;
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer!=NULL && pkt->timestamp-slots[i].timestamp<pkt->timestamp-closestTime){
			closestTime=slots[i].timestamp;
			prevTime=slots[i].recvTime;
		}
	}*/
	double time=VoIPController::GetCurrentTime();
	if(expectNextAtTime!=0){
		double dev=expectNextAtTime-time;
		//LOGV("packet dev %f", dev);
		deviationHistory.Add(dev);
		expectNextAtTime+=step/1000.0;
	}else{
		expectNextAtTime=time+step/1000.0;
	}

	if(pkt->timestamp<nextTimestamp){
		//LOGW("jitter: would drop packet with timestamp %d because it is late but not hopelessly", pkt->timestamp);
		latePacketCount++;
		lostPackets--;
	}else if(pkt->timestamp<nextTimestamp-1){
		//LOGW("jitter: dropping packet with timestamp %d because it is too late", pkt->timestamp);
		latePacketCount++;
		return;
	}

	if(pkt->timestamp>lastPutTimestamp)
		lastPutTimestamp=pkt->timestamp;

	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer==NULL)
			break;
	}
	if(i==JITTER_SLOT_COUNT || GetCurrentDelay()>=maxUsedSlots){
		int toRemove=JITTER_SLOT_COUNT;
		uint32_t bestTimestamp=0xFFFFFFFF;
		for(i=0;i<JITTER_SLOT_COUNT;i++){
			if(slots[i].buffer!=NULL && slots[i].timestamp<bestTimestamp){
				toRemove=i;
				bestTimestamp=slots[i].timestamp;
			}
		}
		Advance();
		bufferPool.Reuse(slots[toRemove].buffer);
		slots[toRemove].buffer=NULL;
		i=toRemove;
	}
	slots[i].timestamp=pkt->timestamp;
	slots[i].size=pkt->size;
	slots[i].buffer=bufferPool.Get();
	slots[i].recvTimeDiff=time-prevRecvTime;
	slots[i].isEC=pkt->isEC;
	if(slots[i].buffer)
		memcpy(slots[i].buffer, pkt->buffer, pkt->size);
	else
		LOGE("WTF!!");
#ifdef TGVOIP_DUMP_JITTER_STATS
	fprintf(dump, "%u\t%.03f\t%d\t%.03f\t%.03f\t%.03f\n", pkt->timestamp, time, GetCurrentDelay(), lastMeasuredJitter, lastMeasuredDelay, minDelay);
#endif
	prevRecvTime=time;
}


void JitterBuffer::Advance(){
	nextTimestamp+=step;
}


unsigned int JitterBuffer::GetCurrentDelay(){
	unsigned int delay=0;
	int i;
	for(i=0;i<JITTER_SLOT_COUNT;i++){
		if(slots[i].buffer!=NULL)
			delay++;
	}
	return delay;
}

void JitterBuffer::Tick(){
	MutexGuard m(mutex);
	int i;

	lateHistory.Add(latePacketCount);
	latePacketCount=0;
	bool absolutelyNoLatePackets=lateHistory.Max()==0;

	double avgLate16=lateHistory.Average(16);
	//LOGV("jitter: avg late=%.1f, %.1f, %.1f", avgLate16, avgLate32, avgLate64);
	if(avgLate16>=resyncThreshold){
		LOGV("resyncing: avgLate16=%f, resyncThreshold=%f", avgLate16, resyncThreshold);
		wasReset=true;
	}

	if(absolutelyNoLatePackets){
		if(dontDecMinDelay>0)
			dontDecMinDelay--;
	}

	delayHistory.Add(GetCurrentDelay());
	avgDelay=delayHistory.Average(32);

	double stddev=0;
	double avgdev=deviationHistory.Average();
	for(i=0;i<64;i++){
		double d=(deviationHistory[i]-avgdev);
		stddev+=(d*d);
	}
	stddev=sqrt(stddev/64);
	uint32_t stddevDelay=(uint32_t)ceil(stddev*2*1000/step);
	if(stddevDelay<minMinDelay)
		stddevDelay=minMinDelay;
	if(stddevDelay>maxMinDelay)
		stddevDelay=maxMinDelay;
	if(stddevDelay!=minDelay){
		int32_t diff=(int32_t)(stddevDelay-minDelay);
		if(diff>0){
			dontDecMinDelay=100;
		}
		if(diff<-1)
			diff=-1;
		if(diff>1)
			diff=1;
		if((diff>0 && dontIncMinDelay==0) || (diff<0 && dontDecMinDelay==0)){
			//nextTimestamp+=diff*(int32_t)step;
			minDelay+=diff;
			outstandingDelayChange+=diff*60;
			dontChangeDelay+=32;
			//LOGD("new delay from stddev %f", minDelay);
			if(diff<0){
				dontDecMinDelay+=25;
			}
			if(diff>0){
				dontIncMinDelay=25;
			}
		}
	}
	lastMeasuredJitter=stddev;
	lastMeasuredDelay=stddevDelay;
	//LOGV("stddev=%.3f, avg=%.3f, ndelay=%d, dontDec=%u", stddev, avgdev, stddevDelay, dontDecMinDelay);
	if(dontChangeDelay==0){
		if(avgDelay>minDelay+0.5){
			outstandingDelayChange-=avgDelay>minDelay+2 ? 60 : 20;
			dontChangeDelay+=10;
		}else if(avgDelay<minDelay-0.3){
			outstandingDelayChange+=20;
			dontChangeDelay+=10;
		}
	}
	if(dontChangeDelay>0)
		dontChangeDelay--;

	//LOGV("jitter: avg delay=%d, delay=%d, late16=%.1f, dontDecMinDelay=%d", avgDelay, delayHistory[0], avgLate16, dontDecMinDelay);
	/*if(!adjustingDelay) {
		if (((minDelay==1 ? (avgDelay>=3) : (avgDelay>=minDelay/2)) && delayHistory[0]>minDelay && avgLate16<=0.1 && absolutelyNoLatePackets && dontDecMinDelay<32 && min>minDelay)) {
			LOGI("jitter: need adjust");
			adjustingDelay=true;
		}
	}else{
		if(!absolutelyNoLatePackets){
			LOGI("jitter: done adjusting because we're losing packets");
			adjustingDelay=false;
		}else if(tickCount%5==0){
			LOGD("jitter: removing a packet to reduce delay");
			GetInternal(NULL, 0);
			expectNextAtTime=0;
			if(GetCurrentDelay()<=minDelay || min<=minDelay){
				adjustingDelay = false;
				LOGI("jitter: done adjusting");
			}
		}
	}*/

	tickCount++;

}


void JitterBuffer::GetAverageLateCount(double *out){
	double avgLate64=lateHistory.Average(), avgLate32=lateHistory.Average(32), avgLate16=lateHistory.Average(16);
	out[0]=avgLate16;
	out[1]=avgLate32;
	out[2]=avgLate64;
}


int JitterBuffer::GetAndResetLostPacketCount(){
	MutexGuard m(mutex);
	int r=lostPackets;
	lostPackets=0;
	return r;
}

double JitterBuffer::GetLastMeasuredJitter(){
	return lastMeasuredJitter;
}

double JitterBuffer::GetLastMeasuredDelay(){
	return lastMeasuredDelay;
}

double JitterBuffer::GetAverageDelay(){
	return avgDelay;
}
