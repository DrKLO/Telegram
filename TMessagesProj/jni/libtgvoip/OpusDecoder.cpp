//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "OpusDecoder.h"
#include "audio/Resampler.h"
#include "logging.h"
#include <assert.h>
#include <math.h>
#include <algorithm>
#ifdef HAVE_CONFIG_H
#include <opus/opus.h>
#else
#include "opus.h"
#endif

#include "VoIPController.h"

#define PACKET_SIZE (960*2)

using namespace tgvoip;

tgvoip::OpusDecoder::OpusDecoder(const std::shared_ptr<MediaStreamItf>& dst, bool isAsync, bool needEC){
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

tgvoip::OpusDecoder::OpusDecoder(const std::unique_ptr<MediaStreamItf>& dst, bool isAsync, bool needEC){
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

tgvoip::OpusDecoder::OpusDecoder(MediaStreamItf* dst, bool isAsync, bool needEC){
	dst->SetCallback(OpusDecoder::Callback, this);
	Initialize(isAsync, needEC);
}

void tgvoip::OpusDecoder::Initialize(bool isAsync, bool needEC){
	async=isAsync;
	if(async){
		decodedQueue=new BlockingQueue<unsigned char*>(33);
		bufferPool=new BufferPool(PACKET_SIZE, 32);
		semaphore=new Semaphore(32, 0);
	}else{
		decodedQueue=NULL;
		bufferPool=NULL;
		semaphore=NULL;
	}
	dec=opus_decoder_create(48000, 1, NULL);
	if(needEC)
		ecDec=opus_decoder_create(48000, 1, NULL);
	else
		ecDec=NULL;
	buffer=(unsigned char *) malloc(8192);
	lastDecoded=NULL;
	outputBufferSize=0;
	echoCanceller=NULL;
	frameDuration=20;
	consecutiveLostPackets=0;
	enableDTX=false;
	silentPacketCount=0;
	levelMeter=NULL;
	nextLen=0;
	running=false;
	remainingDataLen=0;
	processedBuffer=NULL;
	prevWasEC=false;
	prevLastSample=0;
}

tgvoip::OpusDecoder::~OpusDecoder(){
	opus_decoder_destroy(dec);
	if(ecDec)
		opus_decoder_destroy(ecDec);
	free(buffer);
	if(bufferPool)
		delete bufferPool;
	if(decodedQueue)
		delete decodedQueue;
	if(semaphore)
		delete semaphore;
}


void tgvoip::OpusDecoder::SetEchoCanceller(EchoCanceller* canceller){
	echoCanceller=canceller;
}

size_t tgvoip::OpusDecoder::Callback(unsigned char *data, size_t len, void *param){
	return ((OpusDecoder*)param)->HandleCallback(data, len);
}

size_t tgvoip::OpusDecoder::HandleCallback(unsigned char *data, size_t len){
	if(async){
		if(!running){
			memset(data, 0, len);
			return 0;
		}
		if(outputBufferSize==0){
			outputBufferSize=len;
			int packetsNeeded;
			if(len>PACKET_SIZE)
				packetsNeeded=len/PACKET_SIZE;
			else
				packetsNeeded=1;
			packetsNeeded*=2;
			semaphore->Release(packetsNeeded);
		}
		assert(outputBufferSize==len && "output buffer size is supposed to be the same throughout callbacks");
		if(len==PACKET_SIZE){
			lastDecoded=(unsigned char *) decodedQueue->GetBlocking();
			if(!lastDecoded)
				return 0;
			memcpy(data, lastDecoded, PACKET_SIZE);
			bufferPool->Reuse(lastDecoded);
			semaphore->Release();
			if(silentPacketCount>0){
				silentPacketCount--;
				if(levelMeter)
					levelMeter->Update(reinterpret_cast<int16_t *>(data), 0);
				return 0;
			}
			if(echoCanceller){
				echoCanceller->SpeakerOutCallback(data, PACKET_SIZE);
			}
		}else{
			LOGE("Opus decoder buffer length != 960 samples");
			abort();
		}
	}else{
		if(remainingDataLen==0 && silentPacketCount==0){
			int duration=DecodeNextFrame();
			remainingDataLen=(size_t) (duration/20*960*2);
		}
		if(silentPacketCount>0 || remainingDataLen==0 || !processedBuffer){
			if(silentPacketCount>0)
				silentPacketCount--;
			memset(data, 0, 960*2);
			if(levelMeter)
				levelMeter->Update(reinterpret_cast<int16_t *>(data), 0);
			return 0;
		}
		memcpy(data, processedBuffer, 960*2);
		remainingDataLen-=960*2;
		if(remainingDataLen>0){
			memmove(processedBuffer, processedBuffer+960*2, remainingDataLen);
		}
	}
	if(levelMeter)
		levelMeter->Update(reinterpret_cast<int16_t *>(data), len/2);
	return len;
}


void tgvoip::OpusDecoder::Start(){
	if(!async)
		return;
	running=true;
	thread=new Thread(std::bind(&tgvoip::OpusDecoder::RunThread, this));
	thread->SetName("opus_decoder");
	thread->SetMaxPriority();
	thread->Start();
}

void tgvoip::OpusDecoder::Stop(){
	if(!running || !async)
		return;
	running=false;
	semaphore->Release();
	thread->Join();
	delete thread;
}

void tgvoip::OpusDecoder::RunThread(){
	int i;
	LOGI("decoder: packets per frame %d", packetsPerFrame);
	while(running){
		int playbackDuration=DecodeNextFrame();
		for(i=0;i<playbackDuration/20;i++){
			semaphore->Acquire();
			if(!running){
				LOGI("==== decoder exiting ====");
				return;
			}
			unsigned char *buf=bufferPool->Get();
			if(buf){
				if(remainingDataLen>0){
					for(effects::AudioEffect*& effect:postProcEffects){
						effect->Process(reinterpret_cast<int16_t*>(processedBuffer+(PACKET_SIZE*i)), 960);
					}
					memcpy(buf, processedBuffer+(PACKET_SIZE*i), PACKET_SIZE);
				}else{
					//LOGE("Error decoding, result=%d", size);
					memset(buf, 0, PACKET_SIZE);
				}
				decodedQueue->Put(buf);
			}else{
				LOGW("decoder: no buffers left!");
			}
		}
	}
}

int tgvoip::OpusDecoder::DecodeNextFrame(){
	int playbackDuration=0;
	bool isEC=false;
	size_t len=jitterBuffer->HandleOutput(buffer, 8192, 0, true, playbackDuration, isEC);
	bool fec=false;
	if(!len){
		fec=true;
		len=jitterBuffer->HandleOutput(buffer, 8192, 0, false, playbackDuration, isEC);
		//if(len)
		//	LOGV("Trying FEC...");
	}
	int size;
	if(len){
		size=opus_decode(isEC ? ecDec : dec, buffer, len, (opus_int16 *) decodeBuffer, packetsPerFrame*960, fec ? 1 : 0);
		consecutiveLostPackets=0;
		if(prevWasEC!=isEC && size){
			// It turns out the waveforms generated by the PLC feature are also great to help smooth out the
			// otherwise audible transition between the frames from different decoders. Those are basically an extrapolation
			// of the previous successfully decoded data -- which is exactly what we need here.
			size=opus_decode(prevWasEC ? ecDec : dec, NULL, 0, (opus_int16*)nextBuffer, packetsPerFrame*960, 0);
			if(size){
				int16_t* plcSamples=reinterpret_cast<int16_t*>(nextBuffer);
				int16_t* samples=reinterpret_cast<int16_t*>(decodeBuffer);
				constexpr float coeffs[]={0.999802, 0.995062, 0.984031, 0.966778, 0.943413, 0.914084, 0.878975, 0.838309, 0.792344,
										  0.741368, 0.685706, 0.625708, 0.561754, 0.494249, 0.423619, 0.350311, 0.274788, 0.197527, 0.119018, 0.039757};
				for(int i=0;i<20;i++){
					samples[i]=(int16_t)round((plcSamples[i]*coeffs[i]+(float)samples[i]*(1.0-coeffs[i])));
				}
			}
		}
		prevWasEC=isEC;
		prevLastSample=decodeBuffer[size-1];
	}else{ // do packet loss concealment
		consecutiveLostPackets++;
		if(consecutiveLostPackets>2 && enableDTX){
			silentPacketCount+=packetsPerFrame;
			size=packetsPerFrame*960;
		}else{
			size=opus_decode(prevWasEC ? ecDec : dec, NULL, 0, (opus_int16 *) decodeBuffer, packetsPerFrame*960, 0);
			//LOGV("PLC");
		}
	}
	if(size<0)
		LOGW("decoder: opus_decode error %d", size);
	remainingDataLen=size;
	if(playbackDuration==80){
		processedBuffer=buffer;
		audio::Resampler::Rescale60To80((int16_t*) decodeBuffer, (int16_t*) processedBuffer);
	}else if(playbackDuration==40){
		processedBuffer=buffer;
		audio::Resampler::Rescale60To40((int16_t*) decodeBuffer, (int16_t*) processedBuffer);
	}else{
		processedBuffer=decodeBuffer;
	}
	return playbackDuration;
}


void tgvoip::OpusDecoder::SetFrameDuration(uint32_t duration){
	frameDuration=duration;
	packetsPerFrame=frameDuration/20;
}


void tgvoip::OpusDecoder::SetJitterBuffer(std::shared_ptr<JitterBuffer> jitterBuffer){
	this->jitterBuffer=jitterBuffer;
}

void tgvoip::OpusDecoder::SetDTX(bool enable){
	enableDTX=enable;
}

void tgvoip::OpusDecoder::SetLevelMeter(AudioLevelMeter *levelMeter){
	this->levelMeter=levelMeter;
}

void tgvoip::OpusDecoder::AddAudioEffect(effects::AudioEffect *effect){
	postProcEffects.push_back(effect);
}

void tgvoip::OpusDecoder::RemoveAudioEffect(effects::AudioEffect *effect){
	std::vector<effects::AudioEffect*>::iterator i=std::find(postProcEffects.begin(), postProcEffects.end(), effect);
	if(i!=postProcEffects.end())
		postProcEffects.erase(i);
}
