//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "MediaStreamItf.h"
#include "EchoCanceller.h"
#include <stdint.h>
#include <algorithm>
#include <math.h>
#include <assert.h>

using namespace tgvoip;

void MediaStreamItf::SetCallback(size_t (*f)(unsigned char *, size_t, void*), void* param){
	callback=f;
	callbackParam=param;
}

size_t MediaStreamItf::InvokeCallback(unsigned char *data, size_t length){
	if(callback)
		return (*callback)(data, length, callbackParam);
	return 0;
}

AudioMixer::AudioMixer() : bufferPool(960*2, 16), processedQueue(16), semaphore(16, 0){
	running=false;
}

AudioMixer::~AudioMixer(){
}

void AudioMixer::SetOutput(MediaStreamItf* output){
	output->SetCallback(OutputCallback, this);
}

void AudioMixer::Start(){
	assert(!running);
	running=true;
	thread=new Thread(std::bind(&AudioMixer::RunThread, this));
	thread->SetName("AudioMixer");
	thread->Start();
}

void AudioMixer::Stop(){
	if(!running){
		LOGE("Tried to stop AudioMixer that wasn't started");
		return;
	}
	running=false;
	semaphore.Release();
	thread->Join();
	delete thread;
	thread=NULL;
}

void AudioMixer::DoCallback(unsigned char *data, size_t length){
	//memset(data, 0, 960*2);
	//LOGD("audio mixer callback, %d inputs", inputs.size());
	if(processedQueue.Size()==0)
		semaphore.Release(2);
	else
		semaphore.Release();
	unsigned char* buf=processedQueue.GetBlocking();
	memcpy(data, buf, 960*2);
	bufferPool.Reuse(buf);
}

size_t AudioMixer::OutputCallback(unsigned char *data, size_t length, void *arg){
	((AudioMixer*)arg)->DoCallback(data, length);
	return 960*2;
}

void AudioMixer::AddInput(std::shared_ptr<MediaStreamItf> input){
	MutexGuard m(inputsMutex);
	MixerInput in;
	in.multiplier=1;
	in.source=input;
	inputs.push_back(in);
}

void AudioMixer::RemoveInput(std::shared_ptr<MediaStreamItf> input){
	MutexGuard m(inputsMutex);
	for(std::vector<MixerInput>::iterator i=inputs.begin();i!=inputs.end();++i){
		if(i->source==input){
			inputs.erase(i);
			return;
		}
	}
}

void AudioMixer::SetInputVolume(std::shared_ptr<MediaStreamItf> input, float volumeDB){
	MutexGuard m(inputsMutex);
	for(std::vector<MixerInput>::iterator i=inputs.begin();i!=inputs.end();++i){
		if(i->source==input){
			if(volumeDB==-INFINITY)
				i->multiplier=0;
			else
				i->multiplier=expf(volumeDB/20.0f * logf(10.0f));
			return;
		}
	}
}

void AudioMixer::RunThread(){
	LOGV("AudioMixer thread started");
	while(running){
		semaphore.Acquire();
		if(!running)
			break;

		unsigned char* data=bufferPool.Get();
		//LOGV("Audio mixer processing a frame");
		if(!data){
			LOGE("AudioMixer: no buffers left");
			continue;
		}
		MutexGuard m(inputsMutex);
		int16_t* buf=reinterpret_cast<int16_t*>(data);
		int16_t input[960];
		float out[960];
		memset(out, 0, 960*4);
		int usedInputs=0;
		for(std::vector<MixerInput>::iterator in=inputs.begin();in!=inputs.end();++in){
			size_t res=in->source->InvokeCallback(reinterpret_cast<unsigned char*>(input), 960*2);
			if(!res || in->multiplier==0){
				//LOGV("AudioMixer: skipping silent packet");
				continue;
			}
			usedInputs++;
			float k=in->multiplier;
			if(k!=1){
				for(size_t i=0; i<960; i++){
					out[i]+=(float)input[i]*k;
				}
			}else{
				for(size_t i=0;i<960;i++){
					out[i]+=(float)input[i];
				}
			}
		}
		if(usedInputs>0){
			for(size_t i=0; i<960; i++){
				if(out[i]>32767.0f)
					buf[i]=INT16_MAX;
				else if(out[i]<-32768.0f)
					buf[i]=INT16_MIN;
				else
					buf[i]=(int16_t)out[i];
			}
		}else{
			memset(data, 0, 960*2);
		}
		if(echoCanceller)
			echoCanceller->SpeakerOutCallback(data, 960*2);
		processedQueue.Put(data);
	}
	LOGI("======== audio mixer thread exiting =========");
}

void AudioMixer::SetEchoCanceller(EchoCanceller *aec){
	echoCanceller=aec;
}

AudioLevelMeter::AudioLevelMeter(){
	absMax=0;
	count=0;
	currentLevel=0;
	currentLevelFullRange=0;
}

float AudioLevelMeter::GetLevel(){
	return currentLevel/9.0f;
}

void AudioLevelMeter::Update(int16_t *samples, size_t count){
	// Number of bars on the indicator.
	// Note that the number of elements is specified because we are indexing it
	// in the range of 0-32
	const int8_t permutation[33]={0,1,2,3,4,4,5,5,5,5,6,6,6,6,6,7,7,7,7,8,8,8,9,9,9,9,9,9,9,9,9,9,9};
	int16_t absValue=0;
	for(unsigned int k=0;k<count;k++){
		int16_t absolute=(int16_t)abs(samples[k]);
		if (absolute>absValue)
			absValue=absolute;
	}

	if(absValue>absMax)
		absMax = absValue;
	// Update level approximately 10 times per second
	if (this->count++==10){
		currentLevelFullRange=absMax;
		this->count=0;
		// Highest value for a int16_t is 0x7fff = 32767
		// Divide with 1000 to get in the range of 0-32 which is the range of
		// the permutation vector
		int32_t position=absMax/1000;
		// Make it less likely that the bar stays at position 0. I.e. only if
		// its in the range 0-250 (instead of 0-1000)
		/*if ((position==0) && (absMax>250)){
			position=1;
		}*/
		currentLevel=permutation[position];
		// Decay the absolute maximum (divide by 4)
		absMax >>= 2;
	}
}
