//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUTPULSE_H
#define LIBTGVOIP_AUDIOOUTPUTPULSE_H

#include "../../audio/AudioOutput.h"
#include "../../threading.h"
#include <pulse/pulseaudio.h>

namespace tgvoip
{

namespace audio
{

class AudioOutputPulse : public AudioOutput
{
public:
    AudioOutputPulse(pa_context* context, pa_threaded_mainloop* mainloop, std::string devID);
    virtual ~AudioOutputPulse();
    virtual void Start();
    virtual void Stop();
    virtual bool IsPlaying();
    virtual void SetCurrentDevice(std::string devID);
    static bool EnumerateDevices(std::vector<AudioOutputDevice>& devs);

private:
    pa_threaded_mainloop* m_mainloop;
    pa_context* m_context;
    pa_stream* m_stream = nullptr;

    std::size_t m_remainingDataSize = 0;
    std::uint8_t m_remainingData[960 * 8 * 2];

    bool m_isPlaying = false;
    bool m_isConnected = false;
    bool m_didStart = false;
    bool m_isLocked = false;

    static void StreamStateCallback(pa_stream* s, void* arg);
    static void StreamWriteCallback(pa_stream* stream, std::size_t requestedBytes, void* userdata);
    void StreamWriteCallback(pa_stream* stream, std::size_t requestedBytes);
    pa_stream* CreateAndInitStream();
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_AUDIOOUTPUTPULSE_H
