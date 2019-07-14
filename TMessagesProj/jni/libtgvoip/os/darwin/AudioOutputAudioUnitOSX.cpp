//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <stdlib.h>
#include <stdio.h>
#include <sys/sysctl.h>
#include "AudioOutputAudioUnitOSX.h"
#include "../../logging.h"
#include "../../VoIPController.h"

#define BUFFER_SIZE 960
#define CHECK_AU_ERROR(res, msg) if(res!=noErr){ LOGE("output: " msg": OSStatus=%d", (int)res); return; }

#define kOutputBus 0
#define kInputBus 1

using namespace tgvoip;
using namespace tgvoip::audio;

AudioOutputAudioUnitLegacy::AudioOutputAudioUnitLegacy(std::string deviceID){
	remainingDataSize=0;
	isPlaying=false;
	sysDevID=0;
	
	OSStatus status;
	AudioComponentDescription inputDesc={
		.componentType = kAudioUnitType_Output, .componentSubType = kAudioUnitSubType_HALOutput, .componentFlags = 0, .componentFlagsMask = 0,
		.componentManufacturer = kAudioUnitManufacturer_Apple
	};
	AudioComponent component=AudioComponentFindNext(NULL, &inputDesc);
	status=AudioComponentInstanceNew(component, &unit);
	CHECK_AU_ERROR(status, "Error creating AudioUnit");
	
	UInt32 flag=1;
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, kOutputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit output");
	flag=0;
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit input");
	
	char model[128];
	memset(model, 0, sizeof(model));
	size_t msize=sizeof(model);
	int mres=sysctlbyname("hw.model", model, &msize, NULL, 0);
	if(mres==0){
		LOGV("Mac model: %s", model);
		isMacBookPro=(strncmp("MacBookPro", model, 10)==0);
	}
	
	SetCurrentDevice(deviceID);
	
	CFRunLoopRef theRunLoop = NULL;
	AudioObjectPropertyAddress propertyAddress = { kAudioHardwarePropertyRunLoop,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster };
	status = AudioObjectSetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, sizeof(CFRunLoopRef), &theRunLoop);
	
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectAddPropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback, this);
	
	AudioStreamBasicDescription desiredFormat={
		.mSampleRate=/*hardwareFormat.mSampleRate*/48000, .mFormatID=kAudioFormatLinearPCM, .mFormatFlags=kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian,
		.mFramesPerPacket=1, .mChannelsPerFrame=1, .mBitsPerChannel=16, .mBytesPerPacket=2, .mBytesPerFrame=2
	};
	
	status=AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kOutputBus, &desiredFormat, sizeof(desiredFormat));
	CHECK_AU_ERROR(status, "Error setting format");
	
	AURenderCallbackStruct callbackStruct;
	callbackStruct.inputProc = AudioOutputAudioUnitLegacy::BufferCallback;
	callbackStruct.inputProcRefCon=this;
	status = AudioUnitSetProperty(unit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Global, kOutputBus, &callbackStruct, sizeof(callbackStruct));
	CHECK_AU_ERROR(status, "Error setting input buffer callback");
	status=AudioUnitInitialize(unit);
	CHECK_AU_ERROR(status, "Error initializing unit");
}

AudioOutputAudioUnitLegacy::~AudioOutputAudioUnitLegacy(){
	AudioObjectPropertyAddress propertyAddress;
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectRemovePropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback, this);
	
	AudioObjectPropertyAddress dataSourceProp={
		kAudioDevicePropertyDataSource,
		kAudioDevicePropertyScopeOutput,
		kAudioObjectPropertyElementMaster
	};
	if(isMacBookPro && sysDevID && AudioObjectHasProperty(sysDevID, &dataSourceProp)){
		AudioObjectRemovePropertyListener(sysDevID, &dataSourceProp, AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback, this);
	}
	
	AudioUnitUninitialize(unit);
	AudioComponentInstanceDispose(unit);
}

void AudioOutputAudioUnitLegacy::Start(){
	isPlaying=true;
	OSStatus status=AudioOutputUnitStart(unit);
	CHECK_AU_ERROR(status, "Error starting AudioUnit");
}

void AudioOutputAudioUnitLegacy::Stop(){
	isPlaying=false;
	OSStatus status=AudioOutputUnitStart(unit);
	CHECK_AU_ERROR(status, "Error stopping AudioUnit");
}

OSStatus AudioOutputAudioUnitLegacy::BufferCallback(void *inRefCon, AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList *ioData){
	AudioOutputAudioUnitLegacy* input=(AudioOutputAudioUnitLegacy*) inRefCon;
	input->HandleBufferCallback(ioData);
	return noErr;
}

bool AudioOutputAudioUnitLegacy::IsPlaying(){
	return isPlaying;
}

void AudioOutputAudioUnitLegacy::HandleBufferCallback(AudioBufferList *ioData){
	int i;
	for(i=0;i<ioData->mNumberBuffers;i++){
		AudioBuffer buf=ioData->mBuffers[i];
		if(!isPlaying){
			memset(buf.mData, 0, buf.mDataByteSize);
			return;
		}
		while(remainingDataSize<buf.mDataByteSize){
			assert(remainingDataSize+BUFFER_SIZE*2<10240);
			InvokeCallback(remainingData+remainingDataSize, BUFFER_SIZE*2);
			remainingDataSize+=BUFFER_SIZE*2;
		}
		memcpy(buf.mData, remainingData, buf.mDataByteSize);
		remainingDataSize-=buf.mDataByteSize;
		memmove(remainingData, remainingData+buf.mDataByteSize, remainingDataSize);
	}
}


void AudioOutputAudioUnitLegacy::EnumerateDevices(std::vector<AudioOutputDevice>& devs){
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
	propertyAddress.mScope = kAudioDevicePropertyScopeOutput;
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
		
		AudioOutputDevice dev;
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

void AudioOutputAudioUnitLegacy::SetCurrentDevice(std::string deviceID){
	UInt32 size=sizeof(AudioDeviceID);
	AudioDeviceID outputDevice=0;
	OSStatus status;
	AudioObjectPropertyAddress dataSourceProp={
		kAudioDevicePropertyDataSource,
		kAudioDevicePropertyScopeOutput,
		kAudioObjectPropertyElementMaster
	};
	
	if(isMacBookPro && sysDevID && AudioObjectHasProperty(sysDevID, &dataSourceProp)){
		AudioObjectRemovePropertyListener(sysDevID, &dataSourceProp, AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback, this);
	}
	
	if(deviceID=="default"){
		AudioObjectPropertyAddress propertyAddress;
		propertyAddress.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
		propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
		propertyAddress.mElement = kAudioObjectPropertyElementMaster;
		UInt32 propsize = sizeof(AudioDeviceID);
		status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &propsize, &outputDevice);
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
				outputDevice=audioDevices[i];
				break;
			}
		}
		if(!outputDevice){
			LOGW("Requested device not found, using default");
			SetCurrentDevice("default");
			return;
		}
	}
 
	status =AudioUnitSetProperty(unit,
							  kAudioOutputUnitProperty_CurrentDevice,
							  kAudioUnitScope_Global,
							  kOutputBus,
							  &outputDevice,
							  size);
	CHECK_AU_ERROR(status, "Error setting output device");
	
	AudioStreamBasicDescription hardwareFormat;
	size=sizeof(hardwareFormat);
	status=AudioUnitGetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kOutputBus, &hardwareFormat, &size);
	CHECK_AU_ERROR(status, "Error getting hardware format");
	hardwareSampleRate=hardwareFormat.mSampleRate;
	
	AudioStreamBasicDescription desiredFormat={
		.mSampleRate=48000, .mFormatID=kAudioFormatLinearPCM, .mFormatFlags=kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian,
		.mFramesPerPacket=1, .mChannelsPerFrame=1, .mBitsPerChannel=16, .mBytesPerPacket=2, .mBytesPerFrame=2
	};
	
	status=AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kOutputBus, &desiredFormat, sizeof(desiredFormat));
	CHECK_AU_ERROR(status, "Error setting format");
	
	LOGD("Switched playback device, new sample rate %d", hardwareSampleRate);
	
	this->currentDevice=deviceID;
	sysDevID=outputDevice;
	
	AudioObjectPropertyAddress propertyAddress = {
		kAudioDevicePropertyBufferFrameSize,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster
	};
	size=4;
	UInt32 bufferFrameSize;
	status=AudioObjectGetPropertyData(outputDevice, &propertyAddress, 0, NULL, &size, &bufferFrameSize);
	if(status==noErr){
		estimatedDelay=bufferFrameSize/48;
		LOGD("CoreAudio buffer size for output device is %u frames (%u ms)", bufferFrameSize, estimatedDelay);
	}
	
	if(isMacBookPro){
		if(AudioObjectHasProperty(outputDevice, &dataSourceProp)){
			UInt32 dataSource;
			size=4;
			AudioObjectGetPropertyData(outputDevice, &dataSourceProp, 0, NULL, &size, &dataSource);
			SetPanRight(dataSource=='ispk');
			AudioObjectAddPropertyListener(outputDevice, &dataSourceProp, AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback, this);
		}else{
			SetPanRight(false);
		}
	}
}

void AudioOutputAudioUnitLegacy::SetPanRight(bool panRight){
	LOGI("%sabling pan right on macbook pro", panRight ? "En" : "Dis");
	int32_t channelMap[]={panRight ? -1 : 0, 0};
	OSStatus status=AudioUnitSetProperty(unit, kAudioOutputUnitProperty_ChannelMap, kAudioUnitScope_Global, kOutputBus, channelMap, sizeof(channelMap));
	CHECK_AU_ERROR(status, "Error setting channel map");
}

OSStatus AudioOutputAudioUnitLegacy::DefaultDeviceChangedCallback(AudioObjectID inObjectID, UInt32 inNumberAddresses, const AudioObjectPropertyAddress *inAddresses, void *inClientData){
	AudioOutputAudioUnitLegacy* self=(AudioOutputAudioUnitLegacy*)inClientData;
	if(inAddresses[0].mSelector==kAudioHardwarePropertyDefaultOutputDevice){
		LOGV("System default input device changed");
		if(self->currentDevice=="default"){
			self->SetCurrentDevice(self->currentDevice);
		}
	}else if(inAddresses[0].mSelector==kAudioDevicePropertyDataSource){
		UInt32 dataSource;
		UInt32 size=4;
		AudioObjectGetPropertyData(inObjectID, inAddresses, 0, NULL, &size, &dataSource);
		self->SetPanRight(dataSource=='ispk');
	}
	return noErr;
}
