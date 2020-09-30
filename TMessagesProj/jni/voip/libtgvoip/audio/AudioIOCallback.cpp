//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioIOCallback.h"
#include "../VoIPController.h"
#include "../logging.h"

using namespace tgvoip;
using namespace tgvoip::audio;

#pragma mark - IO

AudioIOCallback::AudioIOCallback(){
	input=new AudioInputCallback();
	output=new AudioOutputCallback();
}

AudioIOCallback::~AudioIOCallback(){
	delete input;
	delete output;
}

AudioInput* AudioIOCallback::GetInput(){
	return input;
}

AudioOutput* AudioIOCallback::GetOutput(){
	return output;
}

#pragma mark - Input

AudioInputCallback::AudioInputCallback(){
	thread=new Thread(std::bind(&AudioInputCallback::RunThread, this));
	thread->SetName("AudioInputCallback");
}

AudioInputCallback::~AudioInputCallback(){
	running=false;
	thread->Join();
	delete thread;
}

void AudioInputCallback::Start(){
	if(!running){
		running=true;
		thread->Start();
	}
	recording=true;
}

void AudioInputCallback::Stop(){
	recording=false;
}

void AudioInputCallback::SetDataCallback(std::function<void(int16_t*, size_t)> c){
	dataCallback=c;
}

void AudioInputCallback::RunThread(){
	int16_t buf[960];
	while(running){
		double t=VoIPController::GetCurrentTime();
		memset(buf, 0, sizeof(buf));
		dataCallback(buf, 960);
		InvokeCallback(reinterpret_cast<unsigned char*>(buf), 960*2);
		double sl=0.02-(VoIPController::GetCurrentTime()-t);
		if(sl>0)
			Thread::Sleep(sl);
	}
}

#pragma mark - Output

AudioOutputCallback::AudioOutputCallback(){
	thread=new Thread(std::bind(&AudioOutputCallback::RunThread, this));
	thread->SetName("AudioOutputCallback");
}

AudioOutputCallback::~AudioOutputCallback(){
	running=false;
	thread->Join();
	delete thread;
}

void AudioOutputCallback::Start(){
	if(!running){
		running=true;
		thread->Start();
	}
	playing=true;
}

void AudioOutputCallback::Stop(){
	playing=false;
}

bool AudioOutputCallback::IsPlaying(){
	return playing;
}

void AudioOutputCallback::SetDataCallback(std::function<void(int16_t*, size_t)> c){
	dataCallback=c;
}

void AudioOutputCallback::RunThread(){
	int16_t buf[960];
	while(running){
		double t=VoIPController::GetCurrentTime();
		InvokeCallback(reinterpret_cast<unsigned char*>(buf), 960*2);
		dataCallback(buf, 960);
		double sl=0.02-(VoIPController::GetCurrentTime()-t);
		if(sl>0)
			Thread::Sleep(sl);
	}
}


