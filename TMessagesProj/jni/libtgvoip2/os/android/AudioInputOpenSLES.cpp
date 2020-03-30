//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <stdlib.h>
#include <stdio.h>
#include <assert.h>

#include "AudioInputOpenSLES.h"
#include "../../logging.h"
#include "OpenSLEngineWrapper.h"

#define CHECK_SL_ERROR(res, msg) if(res!=SL_RESULT_SUCCESS){ LOGE(msg); return; }
#define BUFFER_SIZE 960 // 20 ms

using namespace tgvoip;
using namespace tgvoip::audio;

unsigned int AudioInputOpenSLES::nativeBufferSize;

AudioInputOpenSLES::AudioInputOpenSLES(){
	slEngine=OpenSLEngineWrapper::CreateEngine();

	LOGI("Native buffer size is %u samples", nativeBufferSize);
	if(nativeBufferSize<BUFFER_SIZE && BUFFER_SIZE % nativeBufferSize!=0){
		LOGE("20ms is not divisible by native buffer size!!");
	}else if(nativeBufferSize>BUFFER_SIZE && nativeBufferSize%BUFFER_SIZE!=0){
		LOGE("native buffer size is not multiple of 20ms!!");
		nativeBufferSize+=nativeBufferSize%BUFFER_SIZE;
	}
	if(nativeBufferSize==BUFFER_SIZE)
		nativeBufferSize*=2;
	LOGI("Adjusted native buffer size is %u", nativeBufferSize);

	buffer=(int16_t*)calloc(BUFFER_SIZE, sizeof(int16_t));
	nativeBuffer=(int16_t*)calloc((size_t) nativeBufferSize, sizeof(int16_t));
	slRecorderObj=NULL;
}

AudioInputOpenSLES::~AudioInputOpenSLES(){
	//Stop();
	(*slBufferQueue)->Clear(slBufferQueue);
	(*slRecorderObj)->Destroy(slRecorderObj);
	slRecorderObj=NULL;
	slRecorder=NULL;
	slBufferQueue=NULL;
	slEngine=NULL;
	OpenSLEngineWrapper::DestroyEngine();
	free(buffer);
	buffer=NULL;
	free(nativeBuffer);
	nativeBuffer=NULL;
}

void AudioInputOpenSLES::BufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context){
	((AudioInputOpenSLES*)context)->HandleSLCallback();
}

void AudioInputOpenSLES::Configure(uint32_t sampleRate, uint32_t bitsPerSample, uint32_t channels){
	assert(slRecorderObj==NULL);
	SLDataLocator_IODevice loc_dev = {SL_DATALOCATOR_IODEVICE,
									  SL_IODEVICE_AUDIOINPUT,
									  SL_DEFAULTDEVICEID_AUDIOINPUT, NULL};
	SLDataSource audioSrc = {&loc_dev, NULL};
	SLDataLocator_AndroidSimpleBufferQueue loc_bq =
			{SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE, 1};
	SLDataFormat_PCM format_pcm = {SL_DATAFORMAT_PCM, channels, sampleRate*1000,
								   SL_PCMSAMPLEFORMAT_FIXED_16, SL_PCMSAMPLEFORMAT_FIXED_16,
								   channels==2 ? (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT) : SL_SPEAKER_FRONT_CENTER, SL_BYTEORDER_LITTLEENDIAN};
	SLDataSink audioSnk = {&loc_bq, &format_pcm};

	const SLInterfaceID id[2] = {SL_IID_ANDROIDSIMPLEBUFFERQUEUE, SL_IID_ANDROIDCONFIGURATION};
	const SLboolean req[2] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE};
	SLresult result = (*slEngine)->CreateAudioRecorder(slEngine, &slRecorderObj, &audioSrc, &audioSnk, 2, id, req);
	CHECK_SL_ERROR(result, "Error creating recorder");

	SLAndroidConfigurationItf recorderConfig;
	result = (*slRecorderObj)->GetInterface(slRecorderObj, SL_IID_ANDROIDCONFIGURATION, &recorderConfig);
	SLint32 streamType = SL_ANDROID_RECORDING_PRESET_VOICE_RECOGNITION;
	result = (*recorderConfig)->SetConfiguration(recorderConfig, SL_ANDROID_KEY_RECORDING_PRESET, &streamType, sizeof(SLint32));

	result=(*slRecorderObj)->Realize(slRecorderObj, SL_BOOLEAN_FALSE);
	CHECK_SL_ERROR(result, "Error realizing recorder");

	result=(*slRecorderObj)->GetInterface(slRecorderObj, SL_IID_RECORD, &slRecorder);
	CHECK_SL_ERROR(result, "Error getting recorder interface");

	result=(*slRecorderObj)->GetInterface(slRecorderObj, SL_IID_ANDROIDSIMPLEBUFFERQUEUE, &slBufferQueue);
	CHECK_SL_ERROR(result, "Error getting buffer queue");

	result=(*slBufferQueue)->RegisterCallback(slBufferQueue, AudioInputOpenSLES::BufferCallback, this);
	CHECK_SL_ERROR(result, "Error setting buffer queue callback");

	(*slBufferQueue)->Enqueue(slBufferQueue, nativeBuffer, nativeBufferSize*sizeof(int16_t));
}

void AudioInputOpenSLES::Start(){
	SLresult result=(*slRecorder)->SetRecordState(slRecorder, SL_RECORDSTATE_RECORDING);
	CHECK_SL_ERROR(result, "Error starting record");
}

void AudioInputOpenSLES::Stop(){
	SLresult result=(*slRecorder)->SetRecordState(slRecorder, SL_RECORDSTATE_STOPPED);
	CHECK_SL_ERROR(result, "Error stopping record");
}


void AudioInputOpenSLES::HandleSLCallback(){
	//SLmillisecond pMsec = 0;
	//(*slRecorder)->GetPosition(slRecorder, &pMsec);
	//LOGI("Callback! pos=%lu", pMsec);
	//InvokeCallback((unsigned char*)buffer, BUFFER_SIZE*sizeof(int16_t));
	//fwrite(nativeBuffer, 1, nativeBufferSize*2, test);

	if(nativeBufferSize==BUFFER_SIZE){
		//LOGV("nativeBufferSize==BUFFER_SIZE");
		InvokeCallback((unsigned char *) nativeBuffer, BUFFER_SIZE*sizeof(int16_t));
	}else if(nativeBufferSize<BUFFER_SIZE){
		//LOGV("nativeBufferSize<BUFFER_SIZE");
		if(positionInBuffer>=BUFFER_SIZE){
			InvokeCallback((unsigned char *) buffer, BUFFER_SIZE*sizeof(int16_t));
			positionInBuffer=0;
		}
		memcpy(((unsigned char*)buffer)+positionInBuffer*2, nativeBuffer, (size_t)nativeBufferSize*2);
		positionInBuffer+=nativeBufferSize;
	}else if(nativeBufferSize>BUFFER_SIZE){
		//LOGV("nativeBufferSize>BUFFER_SIZE");
		for(unsigned int offset=0;offset<nativeBufferSize;offset+=BUFFER_SIZE){
			InvokeCallback(((unsigned char *) nativeBuffer)+offset*2, BUFFER_SIZE*sizeof(int16_t));
		}
	}

	(*slBufferQueue)->Enqueue(slBufferQueue, nativeBuffer, nativeBufferSize*sizeof(int16_t));
}

