//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>
#include "AudioInputWave.h"
#include "../../logging.h"
#include "../../VoIPController.h"

using namespace tgvoip::audio;

#define BUFFER_SIZE 960
#define CHECK_ERROR(res, msg) if(res!=MMSYSERR_NOERROR){wchar_t _buf[1024]; waveInGetErrorTextW(res, _buf, 1024); LOGE(msg ": %ws (MMRESULT=0x%08X)", _buf, res); failed=true;}

AudioInputWave::AudioInputWave(std::string deviceID){
	isRecording=false;

	for(int i=0;i<4;i++){
		ZeroMemory(&buffers[i], sizeof(WAVEHDR));
		buffers[i].dwBufferLength=960*2;
		buffers[i].lpData=(char*)malloc(960*2);
	}

	hWaveIn=NULL;

	SetCurrentDevice(deviceID);
}

AudioInputWave::~AudioInputWave(){
	for(int i=0;i<4;i++){
		free(buffers[i].lpData);
	}
	waveInClose(hWaveIn);
}

void AudioInputWave::Start(){
	if(!isRecording){
		isRecording=true;

		MMRESULT res;
		for(int i=0;i<4;i++){
			res=waveInPrepareHeader(hWaveIn, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveInPrepareHeader failed");
			res=waveInAddBuffer(hWaveIn, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveInAddBuffer failed");
		}
		res=waveInStart(hWaveIn);
		CHECK_ERROR(res, "waveInStart failed");
	}
}

void AudioInputWave::Stop(){
	if(isRecording){
		isRecording=false;

		MMRESULT res=waveInStop(hWaveIn);
		CHECK_ERROR(res, "waveInStop failed");
		res=waveInReset(hWaveIn);
		CHECK_ERROR(res, "waveInReset failed");
		for(int i=0;i<4;i++){
			res=waveInUnprepareHeader(hWaveIn, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveInUnprepareHeader failed");
		}
	}
}

void CALLBACK AudioInputWave::WaveInProc(HWAVEIN hwi, UINT uMsg, DWORD_PTR dwInstance, DWORD_PTR dwParam1, DWORD_PTR dwParam2){
	if(uMsg==WIM_DATA){
		((AudioInputWave*)dwInstance)->OnData((WAVEHDR*)dwParam1);
	}
}

void AudioInputWave::OnData(WAVEHDR* hdr){
	if(!isRecording)
		return;

	InvokeCallback((unsigned char*)hdr->lpData, hdr->dwBufferLength);
	hdr->dwFlags&= ~WHDR_DONE;
	MMRESULT res=waveInAddBuffer(hWaveIn, hdr, sizeof(WAVEHDR));
	CHECK_ERROR(res, "waveInAddBuffer failed");
}

void AudioInputWave::EnumerateDevices(std::vector<tgvoip::AudioInputDevice>& devs){
	UINT num=waveInGetNumDevs();
	WAVEINCAPSW caps;
	char nameBuf[512];
	for(UINT i=0;i<num;i++){
		waveInGetDevCapsW(i, &caps, sizeof(caps));
		AudioInputDevice dev;
		WideCharToMultiByte(CP_UTF8, 0, caps.szPname, -1, nameBuf, sizeof(nameBuf), NULL, NULL);
		dev.displayName=std::string(nameBuf);
		dev.id=std::string(nameBuf);
		devs.push_back(dev);
	}
}

void AudioInputWave::SetCurrentDevice(std::string deviceID){
	currentDevice=deviceID;

	bool wasRecording=isRecording;
	isRecording=false;
	if(hWaveIn){
		MMRESULT res;
		if(isRecording){
			res=waveInStop(hWaveIn);
			CHECK_ERROR(res, "waveInStop failed");
			res=waveInReset(hWaveIn);
			CHECK_ERROR(res, "waveInReset failed");
			for(int i=0;i<4;i++){
				res=waveInUnprepareHeader(hWaveIn, &buffers[i], sizeof(WAVEHDR));
				CHECK_ERROR(res, "waveInUnprepareHeader failed");
			}
		}
		res=waveInClose(hWaveIn);
		CHECK_ERROR(res, "waveInClose failed");
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
		MMRESULT res=waveInOpen(&hWaveIn, WAVE_MAPPER, &format, (DWORD_PTR)AudioInputWave::WaveInProc, (DWORD_PTR)this, CALLBACK_FUNCTION);
		CHECK_ERROR(res, "waveInOpen failed");
	}else{
		UINT num=waveInGetNumDevs();
		WAVEINCAPSW caps;
		char nameBuf[512];
		hWaveIn=NULL;
		for(UINT i=0;i<num;i++){
			waveInGetDevCapsW(i, &caps, sizeof(caps));
			WideCharToMultiByte(CP_UTF8, 0, caps.szPname, -1, nameBuf, sizeof(nameBuf), NULL, NULL);
			std::string name=std::string(nameBuf);
			if(name==deviceID){
				MMRESULT res=waveInOpen(&hWaveIn, i, &format, (DWORD_PTR)AudioInputWave::WaveInProc, (DWORD_PTR)this, CALLBACK_FUNCTION | WAVE_MAPPED);
				CHECK_ERROR(res, "waveInOpen failed");
				LOGD("Opened device %s", nameBuf);
				break;
			}
		}
		if(!hWaveIn){
			SetCurrentDevice("default");
			return;
		}
	}

	isRecording=wasRecording;

	if(isRecording){
		MMRESULT res;
		for(int i=0;i<4;i++){
			res=waveInPrepareHeader(hWaveIn, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveInPrepareHeader failed");
			res=waveInAddBuffer(hWaveIn, &buffers[i], sizeof(WAVEHDR));
			CHECK_ERROR(res, "waveInAddBuffer failed");
		}
		res=waveInStart(hWaveIn);
		CHECK_ERROR(res, "waveInStart failed");
	}
}