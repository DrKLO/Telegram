//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <sys/time.h>
#include <unistd.h>
#include <assert.h>
#include "AudioOutputAudioUnit.h"
#include "../../logging.h"
#include "AudioUnitIO.h"

#define BUFFER_SIZE 960

using namespace tgvoip;
using namespace tgvoip::audio;

AudioOutputAudioUnit::AudioOutputAudioUnit(std::string deviceID, AudioUnitIO* io){
	isPlaying=false;
	remainingDataSize=0;
	this->io=io;
#if TARGET_OS_OSX
	io->SetCurrentDevice(false, deviceID);
#endif
}

AudioOutputAudioUnit::~AudioOutputAudioUnit(){
}

void AudioOutputAudioUnit::Start(){
	isPlaying=true;
	io->EnableOutput(true);
}

void AudioOutputAudioUnit::Stop(){
	isPlaying=false;
	io->EnableOutput(false);
}

bool AudioOutputAudioUnit::IsPlaying(){
	return isPlaying;
}

void AudioOutputAudioUnit::HandleBufferCallback(AudioBufferList *ioData){
	int i;
	for(i=0;i<ioData->mNumberBuffers;i++){
		AudioBuffer buf=ioData->mBuffers[i];
		if(!isPlaying){
			memset(buf.mData, 0, buf.mDataByteSize);
			return;
		}
#if TARGET_OS_OSX
        unsigned int k;
		while(remainingDataSize<buf.mDataByteSize/2){
			assert(remainingDataSize+BUFFER_SIZE*2<sizeof(remainingData));
			InvokeCallback(remainingData+remainingDataSize, BUFFER_SIZE*2);
			remainingDataSize+=BUFFER_SIZE*2;
		}
		float* dst=reinterpret_cast<float*>(buf.mData);
		int16_t* src=reinterpret_cast<int16_t*>(remainingData);
		for(k=0;k<buf.mDataByteSize/4;k++){
			dst[k]=src[k]/(float)INT16_MAX;
		}
		remainingDataSize-=buf.mDataByteSize/2;
		memmove(remainingData, remainingData+buf.mDataByteSize/2, remainingDataSize);
#else
		while(remainingDataSize<buf.mDataByteSize){
			assert(remainingDataSize+BUFFER_SIZE*2<sizeof(remainingData));
			InvokeCallback(remainingData+remainingDataSize, BUFFER_SIZE*2);
			remainingDataSize+=BUFFER_SIZE*2;
		}
		memcpy(buf.mData, remainingData, buf.mDataByteSize);
		remainingDataSize-=buf.mDataByteSize;
		memmove(remainingData, remainingData+buf.mDataByteSize, remainingDataSize);
#endif
	}
}

#if TARGET_OS_OSX
void AudioOutputAudioUnit::SetCurrentDevice(std::string deviceID){
	io->SetCurrentDevice(false, deviceID);
}
#endif
