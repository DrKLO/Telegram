//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTPULSE_H
#define LIBTGVOIP_AUDIOINPUTPULSE_H

#include "../../threading.h"
#include "../../audio/AudioInput.h"

#include <pulse/pulseaudio.h>

#define DECLARE_DL_FUNCTION(name) typeof(name)* _import_##name

namespace tgvoip
{

namespace audio
{

class AudioInputPulse : public AudioInput
{
public:
    AudioInputPulse(pa_context* context, pa_threaded_mainloop* mainloop, std::string devID);
    virtual ~AudioInputPulse();
    virtual void Start();
    virtual void Stop();
    virtual bool IsRecording();
    virtual void SetCurrentDevice(std::string devID);
    static bool EnumerateDevices(std::vector<AudioInputDevice>& devs);

private:
    pa_threaded_mainloop* m_mainloop;
    pa_context* m_context;
    pa_stream* m_stream = nullptr;

    std::size_t m_remainingDataSize = 0;
    std::uint8_t m_remainingData[960 * 8 * 2];

    bool m_isRecording = false;
    bool m_isConnected = false;
    bool m_didStart = false;
    bool m_isLocked = false;

    static void StreamStateCallback(pa_stream* s, void* arg);
    static void StreamReadCallback(pa_stream* stream, std::size_t requestedBytes, void* userdata);
    void StreamReadCallback(pa_stream* stream, std::size_t requestedBytes);
    pa_stream* CreateAndInitStream();
};

} // namespace audio

} // namespace tgvoip

#undef DECLARE_DL_FUNCTION

#endif // LIBTGVOIP_AUDIOINPUTPULSE_H
