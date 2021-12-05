//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUTOPENSLES_H
#define LIBTGVOIP_AUDIOOUTPUTOPENSLES_H

#include <SLES/OpenSLES.h>
#include <SLES/OpenSLES_Android.h>

#include "../../audio/AudioOutput.h"

namespace tgvoip{ namespace audio{
class AudioOutputOpenSLES : public AudioOutput{
public:
	AudioOutputOpenSLES();
	virtual ~AudioOutputOpenSLES();
	virtual void Configure(uint32_t sampleRate, uint32_t bitsPerSample, uint32_t channels);
	virtual bool IsPhone();
	virtual void EnableLoudspeaker(bool enabled);
	virtual void Start();
	virtual void Stop();
	virtual bool IsPlaying();
	virtual float GetLevel();

	static void SetNativeBufferSize(unsigned int size);
	static unsigned int nativeBufferSize;

private:
	static void BufferCallback(SLAndroidSimpleBufferQueueItf bq, void *context);
	void HandleSLCallback();
	SLEngineItf slEngine;
	SLObjectItf slPlayerObj;
	SLObjectItf slOutputMixObj;
	SLPlayItf slPlayer;
	SLAndroidSimpleBufferQueueItf slBufferQueue;
	int16_t* buffer;
	int16_t* nativeBuffer;
	bool stopped;
	unsigned char remainingData[10240];
	size_t remainingDataSize;
};
}}

#endif //LIBTGVOIP_AUDIOOUTPUTANDROID_H
