//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTAUDIOUNIT_OSX_H
#define LIBTGVOIP_AUDIOINPUTAUDIOUNIT_OSX_H

#include <AudioUnit/AudioUnit.h>
#import <AudioToolbox/AudioToolbox.h>
#import <CoreAudio/CoreAudio.h>
#include "../../audio/AudioInput.h"

namespace tgvoip{ namespace audio{
class AudioInputAudioUnitLegacy : public AudioInput{

public:
	AudioInputAudioUnitLegacy(std::string deviceID);
	virtual ~AudioInputAudioUnitLegacy();
	virtual void Start();
	virtual void Stop();
	void HandleBufferCallback(AudioBufferList* ioData);
	static void EnumerateDevices(std::vector<AudioInputDevice>& devs);
	virtual void SetCurrentDevice(std::string deviceID);

private:
	static OSStatus BufferCallback(void *inRefCon, AudioUnitRenderActionFlags *ioActionFlags, const AudioTimeStamp *inTimeStamp, UInt32 inBusNumber, UInt32 inNumberFrames, AudioBufferList *ioData);
	static OSStatus DefaultDeviceChangedCallback(AudioObjectID inObjectID, UInt32 inNumberAddresses, const AudioObjectPropertyAddress *inAddresses, void *inClientData);
	unsigned char remainingData[10240];
	size_t remainingDataSize;
	bool isRecording;
	AudioUnit unit;
	AudioBufferList inBufferList;
	int hardwareSampleRate;
};
}}

#endif //LIBTGVOIP_AUDIOINPUTAUDIOUNIT_OSX_H
