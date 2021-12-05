//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//


#include <assert.h>
#include "AudioOutputWave.h"
#include "../../logging.h"
#include "../../VoIPController.h"

#define BUFFER_SIZE 960
#define CHECK_ERROR(res, msg) if(res!=MMSYSERR_NOERROR){wchar_t _buf[1024]; waveOutGetErrorTextW(res, _buf, 1024); LOGE(msg ": %ws (MMRESULT=0x%08X)", _buf, res); failed=true;}

using namespace tgvoip::audio;

AudioOutputWave::AudioOutputWave(std::string deviceID){
	isPlaying=false;
	hWaveOut=NULL;

	for(int i=0;i<4;i++){
		ZeroMemory(&buffers[i], sizeof(WAVEHDR));
		buffers[i].dwBufferLength=960*2;
		buffers[i].lpData=(char*)malloc(960*2);
	}

	SetCurrentDevice(deviceID);
}

AudioOutputWave::~AudioOutputWave(){
	for(int i=0;i<4;i++){
		free(buffers[i].lpData);
	}
	waveOutClose(hWaveOut);
}

void AudioOutputWave::Start(){
	if(!isPlaying){
		isPlaying=true;
		
		for(int i=0;i<4;i++){
			MMRESULT res=waveOutPrepareHeader(hWaveOut, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveOutPrepareHeader failed");
			//InvokeCallback((unsigned char*)buffers[i].lpData, buffers[i].dwBufferLength);
			ZeroMemory(buffers[i].lpData, buffers[i].dwBufferLength);
			res=waveOutWrite(hWaveOut, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveOutWrite failed");
		}
	}
}

void AudioOutputWave::Stop(){
	if(isPlaying){
		isPlaying=false;

		MMRESULT res=waveOutReset(hWaveOut);
		CHECK_ERROR(res, "waveOutReset failed");
		for(int i=0;i<4;i++){
			res=waveOutUnprepareHeader(hWaveOut, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveOutUnprepareHeader failed");
		}
	}
}

bool AudioOutputWave::IsPlaying(){
	return isPlaying;
}

void AudioOutputWave::WaveOutProc(HWAVEOUT hwo, UINT uMsg, DWORD_PTR dwInstance, DWORD_PTR dwParam1, DWORD_PTR dwParam2) {
	if(uMsg==WOM_DONE){
		((AudioOutputWave*)dwInstance)->OnBufferDone((WAVEHDR*)dwParam1);
	}
}

void AudioOutputWave::OnBufferDone(WAVEHDR* hdr){
	if(!isPlaying)
		return;

	InvokeCallback((unsigned char*)hdr->lpData, hdr->dwBufferLength);
	hdr->dwFlags&= ~WHDR_DONE;
	MMRESULT res=waveOutWrite(hWaveOut, hdr, sizeof(WAVEHDR));
}

void AudioOutputWave::EnumerateDevices(std::vector<tgvoip::AudioOutputDevice>& devs){
	UINT num=waveOutGetNumDevs();
	WAVEOUTCAPSW caps;
	char nameBuf[512];
	for(UINT i=0;i<num;i++){
		waveOutGetDevCapsW(i, &caps, sizeof(caps));
		AudioOutputDevice dev;
		WideCharToMultiByte(CP_UTF8, 0, caps.szPname, -1, nameBuf, sizeof(nameBuf), NULL, NULL);
		dev.displayName=std::string(nameBuf);
		dev.id=std::string(nameBuf);
		devs.push_back(dev);
	}
}

void AudioOutputWave::SetCurrentDevice(std::string deviceID){
	currentDevice=deviceID;

	bool wasPlaying=isPlaying;
	isPlaying=false;
	LOGV("closing, hWaveOut=%d", (int)hWaveOut);
	if(hWaveOut){
		MMRESULT res;
		if(isPlaying){
			res=waveOutReset(hWaveOut);
			CHECK_ERROR(res, "waveOutReset failed");
			for(int i=0;i<4;i++){
				res=waveOutUnprepareHeader(hWaveOut, &buffers[i], sizeof(WAVEHDR));
				CHECK_ERROR(res, "waveOutUnprepareHeader failed");
			}
		}
		res=waveOutClose(hWaveOut);
		CHECK_ERROR(res, "waveOutClose failed");
	}

	ZeroMemory(&format, sizeof(format));
	format.cbSize=0;
	format.wFormatTag=WAVE_FORMAT_PCM;
	format.nSamplesPerSec=48000;
	format.wBitsPerSample=16;
	format.nChannels=1;
	format.nBlockAlign=2;

	LOGV("before open device %s", deviceID.c_str());

	if(deviceID=="default"){
		MMRESULT res=waveOutOpen(&hWaveOut, WAVE_MAPPER, &format, (DWORD_PTR)AudioOutputWave::WaveOutProc, (DWORD_PTR)this, CALLBACK_FUNCTION);
		CHECK_ERROR(res, "waveOutOpen failed");
	}else{
		UINT num=waveOutGetNumDevs();
		WAVEOUTCAPSW caps;
		char nameBuf[512];
		hWaveOut=NULL;
		for(UINT i=0;i<num;i++){
			waveOutGetDevCapsW(i, &caps, sizeof(caps));
			WideCharToMultiByte(CP_UTF8, 0, caps.szPname, -1, nameBuf, sizeof(nameBuf), NULL, NULL);
			std::string name=std::string(nameBuf);
			if(name==deviceID){
				MMRESULT res=waveOutOpen(&hWaveOut, i, &format, (DWORD_PTR)AudioOutputWave::WaveOutProc, (DWORD_PTR)this, CALLBACK_FUNCTION | WAVE_MAPPED);
				CHECK_ERROR(res, "waveOutOpen failed");
				LOGD("Opened device %s", nameBuf);
				break;
			}
		}
		if(!hWaveOut){
			SetCurrentDevice("default");
			return;
		}
	}

	isPlaying=wasPlaying;

	if(isPlaying){
		MMRESULT res;
		for(int i=0;i<4;i++){
			res=waveOutPrepareHeader(hWaveOut, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveOutPrepareHeader failed");
			res=waveOutWrite(hWaveOut, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveOutWrite failed");
		}
	}
}
