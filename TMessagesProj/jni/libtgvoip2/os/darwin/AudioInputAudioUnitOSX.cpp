//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <stdlib.h>
#include <stdio.h>
#include "AudioInputAudioUnitOSX.h"
#include "../../logging.h"
#include "../../audio/Resampler.h"
#include "../../VoIPController.h"

#define BUFFER_SIZE 960
#define CHECK_AU_ERROR(res, msg) if(res!=noErr){ LOGE("input: " msg": OSStatus=%d", (int)res); failed=true; return; }

#define kOutputBus 0
#define kInputBus 1

using namespace tgvoip;
using namespace tgvoip::audio;

AudioInputAudioUnitLegacy::AudioInputAudioUnitLegacy(std::string deviceID) : AudioInput(deviceID){
	remainingDataSize=0;
	isRecording=false;

	inBufferList.mBuffers[0].mData=malloc(10240);
	inBufferList.mBuffers[0].mDataByteSize=10240;
	inBufferList.mNumberBuffers=1;

	OSStatus status;
	AudioComponentDescription inputDesc={
		.componentType = kAudioUnitType_Output, .componentSubType = kAudioUnitSubType_HALOutput, .componentFlags = 0, .componentFlagsMask = 0,
		.componentManufacturer = kAudioUnitManufacturer_Apple
	};
	AudioComponent component=AudioComponentFindNext(NULL, &inputDesc);
	status=AudioComponentInstanceNew(component, &unit);
	CHECK_AU_ERROR(status, "Error creating AudioUnit");

	UInt32 flag=0;
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, kOutputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit output");
	flag=1;
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit input");

	SetCurrentDevice(deviceID);

	CFRunLoopRef theRunLoop = NULL;
	AudioObjectPropertyAddress propertyAddress = { kAudioHardwarePropertyRunLoop,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster };
	status = AudioObjectSetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, sizeof(CFRunLoopRef), &theRunLoop);

	propertyAddress.mSelector = kAudioHardwarePropertyDefaultInputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectAddPropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioInputAudioUnitLegacy::DefaultDeviceChangedCallback, this);

	AURenderCallbackStruct callbackStruct;
	callbackStruct.inputProc = AudioInputAudioUnitLegacy::BufferCallback;
	callbackStruct.inputProcRefCon=this;
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_SetInputCallback, kAudioUnitScope_Global, kInputBus, &callbackStruct, sizeof(callbackStruct));
	CHECK_AU_ERROR(status, "Error setting input buffer callback");
	status=AudioUnitInitialize(unit);
	CHECK_AU_ERROR(status, "Error initializing unit");
}

AudioInputAudioUnitLegacy::~AudioInputAudioUnitLegacy(){
	AudioObjectPropertyAddress propertyAddress;
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultInputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectRemovePropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioInputAudioUnitLegacy::DefaultDeviceChangedCallback, this);

	AudioUnitUninitialize(unit);
	AudioComponentInstanceDispose(unit);
	free(inBufferList.mBuffers[0].mData);
}

void AudioInputAudioUnitLegacy::Start(){
	isRecording=true;
	OSStatus status=AudioOutputUnitStart(unit);
	CHECK_AU_ERROR(status, "Error starting AudioUnit");
}

void AudioInputAudioUnitLegacy::Stop(){
	isRecording=false;
	OSStatus status=AudioOutputUnitStart(unit);
	CHECK_AU_ERROR(status, "Error stopping AudioUnit");
}

OSStatus AudioInputAudioUnitLegacy::BufferCallback(void *inRefCon, AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList *ioData){
	AudioInputAudioUnitLegacy* input=(AudioInputAudioUnitLegacy*) inRefCon;
	input->inBufferList.mBuffers[0].mDataByteSize=10240;
	AudioUnitRender(input->unit, ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, &input->inBufferList);
	input->HandleBufferCallback(&input->inBufferList);
	return noErr;
}

void AudioInputAudioUnitLegacy::HandleBufferCallback(AudioBufferList *ioData){
	int i;
	for(i=0;i<ioData->mNumberBuffers;i++){
		AudioBuffer buf=ioData->mBuffers[i];
		size_t len=buf.mDataByteSize;
		if(hardwareSampleRate!=48000){
			len=tgvoip::audio::Resampler::Convert((int16_t*)buf.mData, (int16_t*)(remainingData+remainingDataSize), buf.mDataByteSize/2, (10240-(buf.mDataByteSize+remainingDataSize))/2, 48000, hardwareSampleRate)*2;
		}else{
			assert(remainingDataSize+buf.mDataByteSize<10240);
			memcpy(remainingData+remainingDataSize, buf.mData, buf.mDataByteSize);
		}
		remainingDataSize+=len;
		while(remainingDataSize>=BUFFER_SIZE*2){
			InvokeCallback((unsigned char*)remainingData, BUFFER_SIZE*2);
			remainingDataSize-=BUFFER_SIZE*2;
			if(remainingDataSize>0){
				memmove(remainingData, remainingData+(BUFFER_SIZE*2), remainingDataSize);
			}
		}
	}
}


void AudioInputAudioUnitLegacy::EnumerateDevices(std::vector<AudioInputDevice>& devs){
	AudioObjectPropertyAddress propertyAddress = {
		kAudioHardwarePropertyDevices,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster
	};

	UInt32 dataSize = 0;
	OSStatus status = AudioObjectGetPropertyDataSize(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &dataSize);
	if(kAudioHardwareNoError != status) {
		LOGE("AudioObjectGetPropertyDataSize (kAudioHardwarePropertyDevices) failed: %i", status);
		return;
	}

	UInt32 deviceCount = (UInt32)(dataSize / sizeof(AudioDeviceID));


	AudioDeviceID *audioDevices = (AudioDeviceID*)(malloc(dataSize));

	status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &dataSize, audioDevices);
	if(kAudioHardwareNoError != status) {
		LOGE("AudioObjectGetPropertyData (kAudioHardwarePropertyDevices) failed: %i", status);
		free(audioDevices);
		audioDevices = NULL;
		return;
	}


	// Iterate through all the devices and determine which are input-capable
	propertyAddress.mScope = kAudioDevicePropertyScopeInput;
	for(UInt32 i = 0; i < deviceCount; ++i) {
		// Query device UID
		CFStringRef deviceUID = NULL;
		dataSize = sizeof(deviceUID);
		propertyAddress.mSelector = kAudioDevicePropertyDeviceUID;
		status = AudioObjectGetPropertyData(audioDevices[i], &propertyAddress, 0, NULL, &dataSize, &deviceUID);
		if(kAudioHardwareNoError != status) {
			LOGE("AudioObjectGetPropertyData (kAudioDevicePropertyDeviceUID) failed: %i", status);
			continue;
		}

		// Query device name
		CFStringRef deviceName = NULL;
		dataSize = sizeof(deviceName);
		propertyAddress.mSelector = kAudioDevicePropertyDeviceNameCFString;
		status = AudioObjectGetPropertyData(audioDevices[i], &propertyAddress, 0, NULL, &dataSize, &deviceName);
		if(kAudioHardwareNoError != status) {
			LOGE("AudioObjectGetPropertyData (kAudioDevicePropertyDeviceNameCFString) failed: %i", status);
			continue;
		}

		// Determine if the device is an input device (it is an input device if it has input channels)
		dataSize = 0;
		propertyAddress.mSelector = kAudioDevicePropertyStreamConfiguration;
		status = AudioObjectGetPropertyDataSize(audioDevices[i], &propertyAddress, 0, NULL, &dataSize);
		if(kAudioHardwareNoError != status) {
			LOGE("AudioObjectGetPropertyDataSize (kAudioDevicePropertyStreamConfiguration) failed: %i", status);
			continue;
		}

		AudioBufferList *bufferList = (AudioBufferList*)(malloc(dataSize));

		status = AudioObjectGetPropertyData(audioDevices[i], &propertyAddress, 0, NULL, &dataSize, bufferList);
		if(kAudioHardwareNoError != status || 0 == bufferList->mNumberBuffers) {
			if(kAudioHardwareNoError != status)
				LOGE("AudioObjectGetPropertyData (kAudioDevicePropertyStreamConfiguration) failed: %i", status);
			free(bufferList);
			bufferList = NULL;
			continue;
		}

		free(bufferList);
		bufferList = NULL;

		AudioInputDevice dev;
		char buf[1024];
		CFStringGetCString(deviceName, buf, 1024, kCFStringEncodingUTF8);
		dev.displayName=std::string(buf);
		CFStringGetCString(deviceUID, buf, 1024, kCFStringEncodingUTF8);
		dev.id=std::string(buf);
		if(dev.id.rfind("VPAUAggregateAudioDevice-0x")==0)
			continue;
		devs.push_back(dev);
	}

	free(audioDevices);
	audioDevices = NULL;
}

void AudioInputAudioUnitLegacy::SetCurrentDevice(std::string deviceID){
	UInt32 size=sizeof(AudioDeviceID);
	AudioDeviceID inputDevice=0;
	OSStatus status;

	if(deviceID=="default"){
		AudioObjectPropertyAddress propertyAddress;
		propertyAddress.mSelector = kAudioHardwarePropertyDefaultInputDevice;
		propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
		propertyAddress.mElement = kAudioObjectPropertyElementMaster;
		UInt32 propsize = sizeof(AudioDeviceID);
		status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &propsize, &inputDevice);
		CHECK_AU_ERROR(status, "Error getting default input device");
	}else{
		AudioObjectPropertyAddress propertyAddress = {
			kAudioHardwarePropertyDevices,
			kAudioObjectPropertyScopeGlobal,
			kAudioObjectPropertyElementMaster
		};
		UInt32 dataSize = 0;
		status = AudioObjectGetPropertyDataSize(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &dataSize);
		CHECK_AU_ERROR(status, "Error getting devices size");
		UInt32 deviceCount = (UInt32)(dataSize / sizeof(AudioDeviceID));
		AudioDeviceID audioDevices[deviceCount];
		status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &dataSize, audioDevices);
		CHECK_AU_ERROR(status, "Error getting device list");
		for(UInt32 i = 0; i < deviceCount; ++i) {
			// Query device UID
			CFStringRef deviceUID = NULL;
			dataSize = sizeof(deviceUID);
			propertyAddress.mSelector = kAudioDevicePropertyDeviceUID;
			status = AudioObjectGetPropertyData(audioDevices[i], &propertyAddress, 0, NULL, &dataSize, &deviceUID);
			CHECK_AU_ERROR(status, "Error getting device uid");
			char buf[1024];
			CFStringGetCString(deviceUID, buf, 1024, kCFStringEncodingUTF8);
			if(deviceID==buf){
				LOGV("Found device for id %s", buf);
				inputDevice=audioDevices[i];
				break;
			}
		}
		if(!inputDevice){
			LOGW("Requested device not found, using default");
			SetCurrentDevice("default");
			return;
		}
	}

	status =AudioUnitSetProperty(unit,
							  kAudioOutputUnitProperty_CurrentDevice,
							  kAudioUnitScope_Global,
							  kInputBus,
							  &inputDevice,
							  size);
	CHECK_AU_ERROR(status, "Error setting input device");

	AudioStreamBasicDescription hardwareFormat;
	size=sizeof(hardwareFormat);
	status=AudioUnitGetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kInputBus, &hardwareFormat, &size);
	CHECK_AU_ERROR(status, "Error getting hardware format");
	hardwareSampleRate=hardwareFormat.mSampleRate;

	AudioStreamBasicDescription desiredFormat={
		.mSampleRate=hardwareFormat.mSampleRate, .mFormatID=kAudioFormatLinearPCM, .mFormatFlags=kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian,
		.mFramesPerPacket=1, .mChannelsPerFrame=1, .mBitsPerChannel=16, .mBytesPerPacket=2, .mBytesPerFrame=2
	};

	status=AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &desiredFormat, sizeof(desiredFormat));
	CHECK_AU_ERROR(status, "Error setting format");

	LOGD("Switched capture device, new sample rate %d", hardwareSampleRate);

	this->currentDevice=deviceID;

	AudioObjectPropertyAddress propertyAddress = {
		kAudioDevicePropertyBufferFrameSize,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster
	};
	size=4;
	UInt32 bufferFrameSize;
	status=AudioObjectGetPropertyData(inputDevice, &propertyAddress, 0, NULL, &size, &bufferFrameSize);
	if(status==noErr){
		estimatedDelay=bufferFrameSize/48;
		LOGD("CoreAudio buffer size for output device is %u frames (%u ms)", bufferFrameSize, estimatedDelay);
	}
}

OSStatus AudioInputAudioUnitLegacy::DefaultDeviceChangedCallback(AudioObjectID inObjectID, UInt32 inNumberAddresses, const AudioObjectPropertyAddress *inAddresses, void *inClientData){
	LOGV("System default input device changed");
	AudioInputAudioUnitLegacy* self=(AudioInputAudioUnitLegacy*)inClientData;
	if(self->currentDevice=="default"){
		self->SetCurrentDevice(self->currentDevice);
	}
	return noErr;
}
