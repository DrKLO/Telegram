//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_OSX_H
#define LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_OSX_H

#include <AudioUnit/AudioUnit.h>
#import <AudioToolbox/AudioToolbox.h>
#import <CoreAudio/CoreAudio.h>
#include "../../audio/AudioOutput.h"

namespace tgvoip{ namespace audio{
class AudioOutputAudioUnitLegacy : public AudioOutput{

public:
	AudioOutputAudioUnitLegacy(std::string deviceID);
	virtual ~AudioOutputAudioUnitLegacy();
	virtual void Start();
	virtual void Stop();
	virtual bool IsPlaying();
	void HandleBufferCallback(AudioBufferList* ioData);
	static void EnumerateDevices(std::vector<AudioOutputDevice>& devs);
	virtual void SetCurrentDevice(std::string deviceID);

private:
	static OSStatus BufferCallback(void *inRefCon, AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList *ioData);
	static OSStatus DefaultDeviceChangedCallback(AudioObjectID inObjectID, UInt32 inNumberAddresses, const AudioObjectPropertyAddress *inAddresses, void *inClientData);
	void SetPanRight(bool panRight);
	unsigned char remainingData[10240];
	size_t remainingDataSize;
	bool isPlaying;
	AudioUnit unit;
	int hardwareSampleRate;
	bool isMacBookPro;
	AudioDeviceID sysDevID;
};
}}

#endif //LIBTGVOIP_AUDIOOUTPUTAUDIOUNIT_OSX_H
