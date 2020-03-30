//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "OpusEncoder.h"
#include <assert.h>
#include <algorithm>
#include "logging.h"
#include "VoIPServerConfig.h"
#if defined HAVE_CONFIG_H || defined TGVOIP_USE_INSTALLED_OPUS
#include <opus/opus.h>
#else
#include "opus.h"
#endif

namespace{
	int serverConfigValueToBandwidth(int config){
		switch(config){
			case 0:
				return OPUS_BANDWIDTH_NARROWBAND;
			case 1:
				return OPUS_BANDWIDTH_MEDIUMBAND;
			case 2:
				return OPUS_BANDWIDTH_WIDEBAND;
			case 3:
				return OPUS_BANDWIDTH_SUPERWIDEBAND;
			case 4:
			default:
				return OPUS_BANDWIDTH_FULLBAND;
		}
	}
}

tgvoip::OpusEncoder::OpusEncoder(MediaStreamItf *source, bool needSecondary):queue(10){
	this->source=source;
	source->SetCallback(tgvoip::OpusEncoder::Callback, this);
	enc=opus_encoder_create(48000, 1, OPUS_APPLICATION_VOIP, NULL);
	opus_encoder_ctl(enc, OPUS_SET_COMPLEXITY(10));
	opus_encoder_ctl(enc, OPUS_SET_PACKET_LOSS_PERC(1));
	opus_encoder_ctl(enc, OPUS_SET_INBAND_FEC(1));
	opus_encoder_ctl(enc, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
	opus_encoder_ctl(enc, OPUS_SET_BANDWIDTH(OPUS_AUTO));
	requestedBitrate=20000;
	currentBitrate=0;
	running=false;
	echoCanceller=NULL;
	complexity=10;
	frameDuration=20;
	levelMeter=NULL;
	vadNoVoiceBitrate=static_cast<uint32_t>(ServerConfig::GetSharedInstance()->GetInt("audio_vad_no_voice_bitrate", 6000));
	vadModeVoiceBandwidth=serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_vad_bandwidth", 3));
	vadModeNoVoiceBandwidth=serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_vad_no_voice_bandwidth", 0));
	secondaryEnabledBandwidth=serverConfigValueToBandwidth(ServerConfig::GetSharedInstance()->GetInt("audio_extra_ec_bandwidth", 2));
	secondaryEncoderEnabled=false;

	if(needSecondary){
		secondaryEncoder=opus_encoder_create(48000, 1, OPUS_APPLICATION_VOIP, NULL);
		opus_encoder_ctl(secondaryEncoder, OPUS_SET_COMPLEXITY(10));
		opus_encoder_ctl(secondaryEncoder, OPUS_SET_SIGNAL(OPUS_SIGNAL_VOICE));
		opus_encoder_ctl(secondaryEncoder, OPUS_SET_BITRATE(8000));
	}else{
		secondaryEncoder=NULL;
	}
}

tgvoip::OpusEncoder::~OpusEncoder(){
	opus_encoder_destroy(enc);
	if(secondaryEncoder)
		opus_encoder_destroy(secondaryEncoder);
}

void tgvoip::OpusEncoder::Start(){
	if(running)
		return;
	running=true;
	thread=new Thread(std::bind(&tgvoip::OpusEncoder::RunThread, this));
	thread->SetName("OpusEncoder");
	thread->SetMaxPriority();
	thread->Start();
}

void tgvoip::OpusEncoder::Stop(){
	if(!running)
		return;
	running=false;
	queue.Put(Buffer());
	thread->Join();
	delete thread;
}


void tgvoip::OpusEncoder::SetBitrate(uint32_t bitrate){
	requestedBitrate=bitrate;
}

void tgvoip::OpusEncoder::Encode(int16_t* data, size_t len){
	if(requestedBitrate!=currentBitrate){
		opus_encoder_ctl(enc, OPUS_SET_BITRATE(requestedBitrate));
		currentBitrate=requestedBitrate;
		LOGV("opus_encoder: setting bitrate to %u", currentBitrate);
	}
	if(levelMeter)
		levelMeter->Update(data, len);
	if(secondaryEncoderEnabled!=wasSecondaryEncoderEnabled){
		wasSecondaryEncoderEnabled=secondaryEncoderEnabled;
	}
	int32_t r=opus_encode(enc, data, static_cast<int>(len), buffer, 4096);
//	int bw;
//	opus_encoder_ctl(enc, OPUS_GET_BANDWIDTH(&bw));
//	LOGV("Opus bandwidth: %d", bw);
	if(r<=0){
		LOGE("Error encoding: %d", r);
	}else if(r==1){
		LOGW("DTX");
	}else if(running){
		//LOGV("Packet size = %d", r);
		int32_t secondaryLen=0;
		unsigned char secondaryBuffer[128];
		if(secondaryEncoderEnabled && secondaryEncoder){
			secondaryLen=opus_encode(secondaryEncoder, data, static_cast<int>(len), secondaryBuffer, sizeof(secondaryBuffer));
			//LOGV("secondaryLen %d", secondaryLen);
		}
		InvokeCallback(buffer, (size_t)r, secondaryBuffer, (size_t)secondaryLen);
	}
}

size_t tgvoip::OpusEncoder::Callback(unsigned char *data, size_t len, void* param){
	assert(len==960*2);
	OpusEncoder* e=(OpusEncoder*)param;
	try{
		Buffer buf=e->bufferPool.Get();
		buf.CopyFrom(data, 0, 960*2);
		e->queue.Put(std::move(buf));
	}catch(std::bad_alloc& x){
		LOGW("opus_encoder: no buffer slots left");
		if(e->complexity>1){
			e->complexity--;
			opus_encoder_ctl(e->enc, OPUS_SET_COMPLEXITY(e->complexity));
		}
	}
	return 0;
}


uint32_t tgvoip::OpusEncoder::GetBitrate(){
	return requestedBitrate;
}

void tgvoip::OpusEncoder::SetEchoCanceller(EchoCanceller* aec){
	echoCanceller=aec;
}

void tgvoip::OpusEncoder::RunThread(){
	uint32_t bufferedCount=0;
	uint32_t packetsPerFrame=frameDuration/20;
	LOGV("starting encoder, packets per frame=%d", packetsPerFrame);
	int16_t* frame;
	if(packetsPerFrame>1)
		frame=(int16_t*) malloc(960*2*packetsPerFrame);
	else
		frame=NULL;
	bool frameHasVoice=false;
	bool wasVadMode=false;
	while(running){
		Buffer _packet=queue.GetBlocking();
		if(!_packet.IsEmpty()){
			int16_t* packet=(int16_t*)*_packet;
			bool hasVoice=true;
			if(echoCanceller)
				echoCanceller->ProcessInput(packet, 960, hasVoice);
			if(!postProcEffects.empty()){
				for(effects::AudioEffect* effect:postProcEffects){
					effect->Process(packet, 960);
				}
			}
			if(packetsPerFrame==1){
				Encode(packet, 960);
			}else{
				memcpy(frame+(960*bufferedCount), packet, 960*2);
    			frameHasVoice=frameHasVoice || hasVoice;
				bufferedCount++;
				if(bufferedCount==packetsPerFrame){
					if(vadMode){
						if(frameHasVoice){
							opus_encoder_ctl(enc, OPUS_SET_BITRATE(currentBitrate));
							if(secondaryEncoder){
								opus_encoder_ctl(secondaryEncoder, OPUS_SET_BITRATE(currentBitrate));
							}
						}else{
							opus_encoder_ctl(enc, OPUS_SET_BITRATE(vadNoVoiceBitrate));
							if(secondaryEncoder){
								opus_encoder_ctl(secondaryEncoder, OPUS_SET_BITRATE(vadNoVoiceBitrate));
							}
						}
						wasVadMode=true;
					}else if(wasVadMode){
						wasVadMode=false;
						opus_encoder_ctl(enc, OPUS_SET_BITRATE(currentBitrate));
						if(secondaryEncoder){
							opus_encoder_ctl(secondaryEncoder, OPUS_SET_BITRATE(currentBitrate));
						}
					}
					Encode(frame, 960*packetsPerFrame);
					bufferedCount=0;
					frameHasVoice=false;
				}
			}
		}else{
			break;
		}
	}
	if(frame)
		free(frame);
}


void tgvoip::OpusEncoder::SetOutputFrameDuration(uint32_t duration){
	frameDuration=duration;
}


void tgvoip::OpusEncoder::SetPacketLoss(int percent){
	packetLossPercent=std::min(20, percent);
	opus_encoder_ctl(enc, OPUS_SET_PACKET_LOSS_PERC(packetLossPercent));
	opus_encoder_ctl(enc, OPUS_SET_INBAND_FEC(percent>0 && !secondaryEncoderEnabled ? 1 : 0));
}

int tgvoip::OpusEncoder::GetPacketLoss(){
	return packetLossPercent;
}

void tgvoip::OpusEncoder::SetDTX(bool enable){
	opus_encoder_ctl(enc, OPUS_SET_DTX(enable ? 1 : 0));
}

void tgvoip::OpusEncoder::SetLevelMeter(tgvoip::AudioLevelMeter *levelMeter){
	this->levelMeter=levelMeter;
}

void tgvoip::OpusEncoder::SetCallback(std::function <void(unsigned char*, size_t, unsigned char*, size_t)> f){
	callback=f;
}

void tgvoip::OpusEncoder::InvokeCallback(unsigned char *data, size_t length, unsigned char *secondaryData, size_t secondaryLength){
	callback(data, length, secondaryData, secondaryLength);
}

void tgvoip::OpusEncoder::SetSecondaryEncoderEnabled(bool enabled){
	secondaryEncoderEnabled=enabled;
}

void tgvoip::OpusEncoder::SetVadMode(bool vad){
	vadMode=vad;
}
void tgvoip::OpusEncoder::AddAudioEffect(effects::AudioEffect *effect){
	postProcEffects.push_back(effect);
}

void tgvoip::OpusEncoder::RemoveAudioEffect(effects::AudioEffect *effect){
	std::vector<effects::AudioEffect*>::iterator i=std::find(postProcEffects.begin(), postProcEffects.end(), effect);
	if(i!=postProcEffects.end())
		postProcEffects.erase(i);
}
