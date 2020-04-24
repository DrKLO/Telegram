//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTALSA_H
#define LIBTGVOIP_AUDIOINPUTALSA_H

#include "../../threading.h"
#include "../../audio/AudioInput.h"

#include <alsa/asoundlib.h>

namespace tgvoip
{

namespace audio
{

class AudioInputALSA : public AudioInput
{

public:
    AudioInputALSA(std::string devID);
    virtual ~AudioInputALSA();
    virtual void Start();
    virtual void Stop();
    virtual void SetCurrentDevice(std::string devID);
    static void EnumerateDevices(std::vector<AudioInputDevice>& devs);

private:
    void RunThread();

    int (*m_snd_pcm_open)(snd_pcm_t** pcm, const char* name, snd_pcm_stream_t stream, int mode);
    int (*m_snd_pcm_set_params)(snd_pcm_t* pcm, snd_pcm_format_t format, snd_pcm_access_t access,
                                unsigned int channels, unsigned int rate, int soft_resample, unsigned int latency);
    int (*m_snd_pcm_close)(snd_pcm_t* pcm);
    snd_pcm_sframes_t (*m_snd_pcm_readi)(snd_pcm_t* pcm, const void* buffer, snd_pcm_uframes_t size);
    int (*m_snd_pcm_recover)(snd_pcm_t* pcm, int err, int silent);
    const char* (*m_snd_strerror)(int errnum);
    void* m_lib;

    snd_pcm_t* m_handle;
    Thread* m_thread;
    bool m_isRecording;
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_AUDIOINPUTALSA_H
