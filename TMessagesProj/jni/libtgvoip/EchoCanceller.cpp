//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef TGVOIP_NO_DSP
#include "webrtc_dsp/modules/audio_processing/include/audio_processing.h"
#include "webrtc_dsp/api/audio/audio_frame.h"
#endif

#include "EchoCanceller.h"
#include "audio/AudioOutput.h"
#include "audio/AudioInput.h"
#include "logging.h"
#include "VoIPServerConfig.h"
#include <string.h>
#include <stdio.h>
#include <math.h>

using namespace tgvoip;

EchoCanceller::EchoCanceller(bool enableAEC, bool enableNS, bool enableAGC){
#ifndef TGVOIP_NO_DSP
	this->enableAEC=enableAEC;
	this->enableAGC=enableAGC;
	this->enableNS=enableNS;
	isOn=true;

	webrtc::Config extraConfig;
#ifdef TGVOIP_USE_DESKTOP_DSP
	extraConfig.Set(new webrtc::DelayAgnostic(true));
#endif

	apm=webrtc::AudioProcessingBuilder().Create(extraConfig);

	webrtc::AudioProcessing::Config config;
	config.echo_canceller.enabled = enableAEC;
#ifndef TGVOIP_USE_DESKTOP_DSP
	config.echo_canceller.mobile_mode = true;
#else
	config.echo_canceller.mobile_mode = false;
#endif
	config.high_pass_filter.enabled = enableAEC;
	config.gain_controller2.enabled = enableAGC;
	apm->ApplyConfig(config);
	
	webrtc::NoiseSuppression::Level nsLevel;
#ifdef __APPLE__
	switch(ServerConfig::GetSharedInstance()->GetInt("webrtc_ns_level_vpio", 0)){
#else
	switch(ServerConfig::GetSharedInstance()->GetInt("webrtc_ns_level", 2)){
#endif
		case 0:
			nsLevel=webrtc::NoiseSuppression::Level::kLow;
			break;
		case 1:
			nsLevel=webrtc::NoiseSuppression::Level::kModerate;
			break;
		case 3:
			nsLevel=webrtc::NoiseSuppression::Level::kVeryHigh;
			break;
		case 2:
		default:
			nsLevel=webrtc::NoiseSuppression::Level::kHigh;
			break;
	}
	apm->noise_suppression()->set_level(nsLevel);
	apm->noise_suppression()->Enable(enableNS);
	if(enableAGC){
		apm->gain_control()->set_mode(webrtc::GainControl::Mode::kAdaptiveDigital);
		apm->gain_control()->set_target_level_dbfs(ServerConfig::GetSharedInstance()->GetInt("webrtc_agc_target_level", 9));
		apm->gain_control()->enable_limiter(ServerConfig::GetSharedInstance()->GetBoolean("webrtc_agc_enable_limiter", true));
		apm->gain_control()->set_compression_gain_db(ServerConfig::GetSharedInstance()->GetInt("webrtc_agc_compression_gain", 20));
	}
	apm->voice_detection()->set_likelihood(webrtc::VoiceDetection::Likelihood::kVeryLowLikelihood);

	audioFrame=new webrtc::AudioFrame();
	audioFrame->samples_per_channel_=480;
	audioFrame->sample_rate_hz_=48000;
	audioFrame->num_channels_=1;

	farendQueue=new BlockingQueue<int16_t*>(11);
	farendBufferPool=new BufferPool(960*2, 10);
	running=true;
	bufferFarendThread=new Thread(std::bind(&EchoCanceller::RunBufferFarendThread, this));
	bufferFarendThread->Start();

#else
	this->enableAEC=this->enableAGC=enableAGC=this->enableNS=enableNS=false;
	isOn=true;
#endif
}

EchoCanceller::~EchoCanceller(){
#ifndef TGVOIP_NO_DSP
	delete apm;
	delete audioFrame;
#endif
}

void EchoCanceller::Start(){

}

void EchoCanceller::Stop(){

}


void EchoCanceller::SpeakerOutCallback(unsigned char* data, size_t len){
    if(len!=960*2 || !enableAEC || !isOn)
		return;
#ifndef TGVOIP_NO_DSP
	int16_t* buf=(int16_t*)farendBufferPool->Get();
	if(buf){
		memcpy(buf, data, 960*2);
		farendQueue->Put(buf);
	}
#endif
}

#ifndef TGVOIP_NO_DSP
void EchoCanceller::RunBufferFarendThread(){
	webrtc::AudioFrame frame;
	frame.num_channels_=1;
	frame.sample_rate_hz_=48000;
	frame.samples_per_channel_=480;
	while(running){
		int16_t* samplesIn=farendQueue->GetBlocking();
		if(samplesIn){
			memcpy(frame.mutable_data(), samplesIn, 480*2);
			apm->ProcessReverseStream(&frame);
			memcpy(frame.mutable_data(), samplesIn+480, 480*2);
			apm->ProcessReverseStream(&frame);
			didBufferFarend=true;
			farendBufferPool->Reuse(reinterpret_cast<unsigned char*>(samplesIn));
		}
	}
}
#endif

void EchoCanceller::Enable(bool enabled){
	isOn=enabled;
}

void EchoCanceller::ProcessInput(int16_t* inOut, size_t numSamples, bool& hasVoice){
#ifndef TGVOIP_NO_DSP
	if(!isOn || (!enableAEC && !enableAGC && !enableNS)){
		return;
	}
	int delay=audio::AudioInput::GetEstimatedDelay()+audio::AudioOutput::GetEstimatedDelay();
	assert(numSamples==960);

	memcpy(audioFrame->mutable_data(), inOut, 480*2);
	if(enableAEC)
    	apm->set_stream_delay_ms(delay);
	apm->ProcessStream(audioFrame);
	if(enableVAD)
    	hasVoice=apm->voice_detection()->stream_has_voice();
	memcpy(inOut, audioFrame->data(), 480*2);
	memcpy(audioFrame->mutable_data(), inOut+480, 480*2);
	if(enableAEC)
    	apm->set_stream_delay_ms(delay);
	apm->ProcessStream(audioFrame);
	if(enableVAD){
    	hasVoice=hasVoice || apm->voice_detection()->stream_has_voice();
	}
	memcpy(inOut+480, audioFrame->data(), 480*2);
#endif
}

void EchoCanceller::SetAECStrength(int strength){
#ifndef TGVOIP_NO_DSP
	/*if(aec){
#ifndef TGVOIP_USE_DESKTOP_DSP
		AecmConfig cfg;
		cfg.cngMode=AecmFalse;
		cfg.echoMode=(int16_t) strength;
		WebRtcAecm_set_config(aec, cfg);
#endif
	}*/
#endif
}

void EchoCanceller::SetVoiceDetectionEnabled(bool enabled){
	enableVAD=enabled;
#ifndef TGVOIP_NO_DSP
	apm->voice_detection()->Enable(enabled);
#endif
}

using namespace tgvoip::effects;

AudioEffect::~AudioEffect(){

}

void AudioEffect::SetPassThrough(bool passThrough){
	this->passThrough=passThrough;
}

Volume::Volume(){

}

Volume::~Volume(){

}

void Volume::Process(int16_t* inOut, size_t numSamples){
	if(level==1.0f || passThrough){
		return;
	}
	for(size_t i=0;i<numSamples;i++){
		float sample=(float)inOut[i]*multiplier;
		if(sample>32767.0f)
			inOut[i]=INT16_MAX;
		else if(sample<-32768.0f)
			inOut[i]=INT16_MIN;
		else
			inOut[i]=(int16_t)sample;
	}
}

void Volume::SetLevel(float level){
	this->level=level;
	float db;
	if(level<1.0f)
		db=-50.0f*(1.0f-level);
	else if(level>1.0f && level<=2.0f)
		db=10.0f*(level-1.0f);
	else
		db=0.0f;
	multiplier=expf(db/20.0f * logf(10.0f));
}

float Volume::GetLevel(){
	return level;
}
