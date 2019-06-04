//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//
#include <stdio.h>
#include "AudioUnitIO.h"
#include "AudioInputAudioUnit.h"
#include "AudioOutputAudioUnit.h"
#include "../../logging.h"
#include "../../VoIPController.h"
#include "../../VoIPServerConfig.h"

#define CHECK_AU_ERROR(res, msg) if(res!=noErr){ LOGE(msg": OSStatus=%d", (int)res); failed=true; return; }
#define BUFFER_SIZE 960 // 20 ms

#if TARGET_OS_OSX
#define INPUT_BUFFER_SIZE 20480
#else
#define INPUT_BUFFER_SIZE 10240
#endif

#define kOutputBus 0
#define kInputBus 1

#if TARGET_OS_OSX && !defined(TGVOIP_NO_OSX_PRIVATE_API)
extern "C" {
OSStatus AudioDeviceDuck(AudioDeviceID inDevice,
                         Float32 inDuckedLevel,
                         const AudioTimeStamp* __nullable inStartTime,
                         Float32 inRampDuration) __attribute__((weak_import));
}
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

AudioUnitIO::AudioUnitIO(std::string inputDeviceID, std::string outputDeviceID){
	input=NULL;
	output=NULL;
	inputEnabled=false;
	outputEnabled=false;
	failed=false;
	started=false;
	inBufferList.mBuffers[0].mData=malloc(INPUT_BUFFER_SIZE);
	inBufferList.mBuffers[0].mDataByteSize=INPUT_BUFFER_SIZE;
	inBufferList.mNumberBuffers=1;
	
#if TARGET_OS_IPHONE
	DarwinSpecific::ConfigureAudioSession();
#endif
	
	OSStatus status;
	AudioComponentDescription desc;
	AudioComponent inputComponent;
	desc.componentType = kAudioUnitType_Output;
	desc.componentSubType = kAudioUnitSubType_VoiceProcessingIO;
	desc.componentFlags = 0;
	desc.componentFlagsMask = 0;
	desc.componentManufacturer = kAudioUnitManufacturer_Apple;
	inputComponent = AudioComponentFindNext(NULL, &desc);
	status = AudioComponentInstanceNew(inputComponent, &unit);
	
	UInt32 flag=1;
#if TARGET_OS_IPHONE
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Output, kOutputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit output");
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_EnableIO, kAudioUnitScope_Input, kInputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error enabling AudioUnit input");
#endif
	
#if TARGET_OS_IPHONE
	flag=ServerConfig::GetSharedInstance()->GetBoolean("use_ios_vpio_agc", true) ? 1 : 0;
#else
	flag=ServerConfig::GetSharedInstance()->GetBoolean("use_osx_vpio_agc", true) ? 1 : 0;
#endif
	status=AudioUnitSetProperty(unit, kAUVoiceIOProperty_VoiceProcessingEnableAGC, kAudioUnitScope_Global, kInputBus, &flag, sizeof(flag));
	CHECK_AU_ERROR(status, "Error disabling AGC");
	
	AudioStreamBasicDescription audioFormat;
	audioFormat.mSampleRate			= 48000;
	audioFormat.mFormatID			= kAudioFormatLinearPCM;
#if TARGET_OS_IPHONE
	audioFormat.mFormatFlags		= kAudioFormatFlagIsSignedInteger | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian;
	audioFormat.mBitsPerChannel		= 16;
	audioFormat.mBytesPerPacket		= 2;
	audioFormat.mBytesPerFrame		= 2;
#else // OS X
	audioFormat.mFormatFlags		= kAudioFormatFlagIsFloat | kAudioFormatFlagIsPacked | kAudioFormatFlagsNativeEndian;
	audioFormat.mBitsPerChannel		= 32;
	audioFormat.mBytesPerPacket		= 4;
	audioFormat.mBytesPerFrame		= 4;
#endif
	audioFormat.mFramesPerPacket	= 1;
	audioFormat.mChannelsPerFrame	= 1;
	
	status = AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, kOutputBus, &audioFormat, sizeof(audioFormat));
	CHECK_AU_ERROR(status, "Error setting output format");
	status = AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Output, kInputBus, &audioFormat, sizeof(audioFormat));
	CHECK_AU_ERROR(status, "Error setting input format");
	
	AURenderCallbackStruct callbackStruct;
	
	callbackStruct.inputProc = AudioUnitIO::BufferCallback;
	callbackStruct.inputProcRefCon = this;
	status = AudioUnitSetProperty(unit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Global, kOutputBus, &callbackStruct, sizeof(callbackStruct));
	CHECK_AU_ERROR(status, "Error setting output buffer callback");
	status = AudioUnitSetProperty(unit, kAudioOutputUnitProperty_SetInputCallback, kAudioUnitScope_Global, kInputBus, &callbackStruct, sizeof(callbackStruct));
	CHECK_AU_ERROR(status, "Error setting input buffer callback");
	
#if TARGET_OS_OSX
	CFRunLoopRef theRunLoop = NULL;
	AudioObjectPropertyAddress propertyAddress = { kAudioHardwarePropertyRunLoop,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster };
	status = AudioObjectSetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, sizeof(CFRunLoopRef), &theRunLoop);
	
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectAddPropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioUnitIO::DefaultDeviceChangedCallback, this);
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultInputDevice;
	AudioObjectAddPropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioUnitIO::DefaultDeviceChangedCallback, this);
	
	
#endif
	
	
	input=new AudioInputAudioUnit(inputDeviceID, this);
	output=new AudioOutputAudioUnit(outputDeviceID, this);
}

AudioUnitIO::~AudioUnitIO(){
#if TARGET_OS_OSX
	AudioObjectPropertyAddress propertyAddress;
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultOutputDevice;
	propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
	propertyAddress.mElement = kAudioObjectPropertyElementMaster;
	AudioObjectRemovePropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioUnitIO::DefaultDeviceChangedCallback, this);
	propertyAddress.mSelector = kAudioHardwarePropertyDefaultInputDevice;
	AudioObjectRemovePropertyListener(kAudioObjectSystemObject, &propertyAddress, AudioUnitIO::DefaultDeviceChangedCallback, this);
#endif
	AudioOutputUnitStop(unit);
	AudioUnitUninitialize(unit);
	AudioComponentInstanceDispose(unit);
	free(inBufferList.mBuffers[0].mData);
	delete input;
	delete output;
}

OSStatus AudioUnitIO::BufferCallback(void *inRefCon, AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList *ioData){
	((AudioUnitIO*)inRefCon)->BufferCallback(ioActionFlags, inTimeStamp, inBusNumber, inNumberFrames, ioData);
	return noErr;
}

void AudioUnitIO::BufferCallback(AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 bus, UInt32 numFrames, AudioBufferList *ioData){
	if(bus==kOutputBus){
		if(output && outputEnabled){
			output->HandleBufferCallback(ioData);
		}else{
			memset(ioData->mBuffers[0].mData, 0, ioData->mBuffers[0].mDataByteSize);
		}
	}else if(bus==kInputBus){
		inBufferList.mBuffers[0].mDataByteSize=INPUT_BUFFER_SIZE;
		AudioUnitRender(unit, ioActionFlags, inTimeStamp, bus, numFrames, &inBufferList);
		if(input && inputEnabled){
			input->HandleBufferCallback(&inBufferList);
		}
	}
}

void AudioUnitIO::EnableInput(bool enabled){
	inputEnabled=enabled;
	StartIfNeeded();
}

void AudioUnitIO::EnableOutput(bool enabled){
	outputEnabled=enabled;
	StartIfNeeded();
#if TARGET_OS_OSX && !defined(TGVOIP_NO_OSX_PRIVATE_API)
	if(actualDuckingEnabled!=duckingEnabled){
		actualDuckingEnabled=duckingEnabled;
    	AudioDeviceDuck(currentOutputDeviceID, duckingEnabled ? 0.177828f : 1.0f, NULL, 0.1f);
	}
#endif
}

void AudioUnitIO::StartIfNeeded(){
	if(started)
		return;
	started=true;
	OSStatus status = AudioUnitInitialize(unit);
	CHECK_AU_ERROR(status, "Error initializing AudioUnit");
	status=AudioOutputUnitStart(unit);
	CHECK_AU_ERROR(status, "Error starting AudioUnit");
}

AudioInput* AudioUnitIO::GetInput(){
	return input;
}

AudioOutput* AudioUnitIO::GetOutput(){
	return output;
}

#if TARGET_OS_OSX
OSStatus AudioUnitIO::DefaultDeviceChangedCallback(AudioObjectID inObjectID, UInt32 inNumberAddresses, const AudioObjectPropertyAddress *inAddresses, void *inClientData){
	AudioUnitIO* self=(AudioUnitIO*)inClientData;
	if(inAddresses[0].mSelector==kAudioHardwarePropertyDefaultOutputDevice){
		LOGV("System default output device changed");
		if(self->currentOutputDevice=="default"){
			self->SetCurrentDevice(false, self->currentOutputDevice);
		}
	}else if(inAddresses[0].mSelector==kAudioHardwarePropertyDefaultInputDevice){
		LOGV("System default input device changed");
		if(self->currentInputDevice=="default"){
			self->SetCurrentDevice(true, self->currentInputDevice);
		}
	}
	return noErr;
}

void AudioUnitIO::SetCurrentDevice(bool input, std::string deviceID){
	LOGV("Setting current %sput device: %s", input ? "in" : "out", deviceID.c_str());
	if(started){
		AudioOutputUnitStop(unit);
		AudioUnitUninitialize(unit);
	}
	UInt32 size=sizeof(AudioDeviceID);
	AudioDeviceID device=0;
	OSStatus status;
	
	if(deviceID=="default"){
		AudioObjectPropertyAddress propertyAddress;
		propertyAddress.mSelector = input ? kAudioHardwarePropertyDefaultInputDevice : kAudioHardwarePropertyDefaultOutputDevice;
		propertyAddress.mScope = kAudioObjectPropertyScopeGlobal;
		propertyAddress.mElement = kAudioObjectPropertyElementMaster;
		UInt32 propsize = sizeof(AudioDeviceID);
		status = AudioObjectGetPropertyData(kAudioObjectSystemObject, &propertyAddress, 0, NULL, &propsize, &device);
		CHECK_AU_ERROR(status, "Error getting default device");
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
				device=audioDevices[i];
				break;
			}
		}
		if(!device){
			LOGW("Requested device not found, using default");
			SetCurrentDevice(input, "default");
			return;
		}
	}
 
	status=AudioUnitSetProperty(unit,
							  kAudioOutputUnitProperty_CurrentDevice,
							  kAudioUnitScope_Global,
								input ? kInputBus : kOutputBus,
							  &device,
							  size);
	CHECK_AU_ERROR(status, "Error setting input device");
	
	if(input)
		currentInputDevice=deviceID;
	else
		currentOutputDevice=deviceID;
	
	/*AudioObjectPropertyAddress propertyAddress = {
		kAudioDevicePropertyBufferFrameSize,
		kAudioObjectPropertyScopeGlobal,
		kAudioObjectPropertyElementMaster
	};
	size=4;
	UInt32 bufferFrameSize;
	status=AudioObjectGetPropertyData(device, &propertyAddress, 0, NULL, &size, &bufferFrameSize);
	if(status==noErr){
		estimatedDelay=bufferFrameSize/48;
		LOGD("CoreAudio buffer size for device is %u frames (%u ms)", bufferFrameSize, estimatedDelay);
	}*/
	if(started){
		started=false;
		StartIfNeeded();
	}
	if(!input){
		currentOutputDeviceID=device;
	}
	LOGV("Set current %sput device done", input ? "in" : "out");
}

void AudioUnitIO::SetDuckingEnabled(bool enabled){
	duckingEnabled=enabled;
#ifndef TGVOIP_NO_OSX_PRIVATE_API
	if(outputEnabled && duckingEnabled!=actualDuckingEnabled){
		actualDuckingEnabled=enabled;
    	AudioDeviceDuck(currentOutputDeviceID, enabled ? 0.177828f : 1.0f, NULL, 0.1f);
	}
#endif
}

#endif
