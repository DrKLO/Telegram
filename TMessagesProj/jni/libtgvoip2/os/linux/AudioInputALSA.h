//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTALSA_H
#define LIBTGVOIP_AUDIOINPUTALSA_H

#include "../../audio/AudioInput.h"
#include "../../threading.h"
#include <alsa/asoundlib.h>

namespace tgvoip{
namespace audio{

class AudioInputALSA : public AudioInput{

public:
	AudioInputALSA(std::string devID);
	virtual ~AudioInputALSA();
	virtual void Start();
	virtual void Stop();
	virtual void SetCurrentDevice(std::string devID);
	static void EnumerateDevices(std::vector<AudioInputDevice>& devs);

private:
	void RunThread();

	int (*_snd_pcm_open)(snd_pcm_t** pcm, const char* name, snd_pcm_stream_t stream, int mode);
	int (*_snd_pcm_set_params)(snd_pcm_t* pcm, snd_pcm_format_t format, snd_pcm_access_t access, unsigned int channels, unsigned int rate, int soft_resample, unsigned int latency);
	int (*_snd_pcm_close)(snd_pcm_t* pcm);
	snd_pcm_sframes_t (*_snd_pcm_readi)(snd_pcm_t *pcm, const void *buffer, snd_pcm_uframes_t size);
	int (*_snd_pcm_recover)(snd_pcm_t* pcm, int err, int silent);
	const char* (*_snd_strerror)(int errnum);
	void* lib;

	snd_pcm_t* handle;
	Thread* thread;
	bool isRecording;
};

}
}

#endif //LIBTGVOIP_AUDIOINPUTALSA_H
