//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIO_IO_CALLBACK
#define LIBTGVOIP_AUDIO_IO_CALLBACK

#include "../threading.h"
#include "AudioIO.h"

#include <atomic>
#include <functional>

namespace tgvoip
{

namespace audio
{

class AudioInputCallback : public AudioInput
{
public:
    AudioInputCallback();
    ~AudioInputCallback() override;
    void Start() override;
    void Stop() override;
    void SetDataCallback(std::function<void(std::int16_t*, std::size_t)> dataCallback);

private:
    void RunThread();
    std::atomic<bool> m_running{false};
    bool m_recording = false;
    Thread* m_thread;
    std::function<void(std::int16_t*, std::size_t)> m_dataCallback;
};

class AudioOutputCallback : public AudioOutput
{
public:
    AudioOutputCallback();
    ~AudioOutputCallback() override;
    void Start() override;
    void Stop() override;
    bool IsPlaying() override;
    void SetDataCallback(std::function<void(std::int16_t*, std::size_t)> c);

private:
    void RunThread();
    std::atomic<bool> m_running{false};
    bool m_playing = false;
    Thread* m_thread;
    std::function<void(std::int16_t*, std::size_t)> m_dataCallback;
};

class AudioIOCallback : public AudioIO
{
public:
    AudioIOCallback();
    ~AudioIOCallback() override;
    AudioInput* GetInput() override;
    AudioOutput* GetOutput() override;

private:
    AudioInputCallback* m_input;
    AudioOutputCallback* m_output;
};

} // namespace audio

} // namespace tgvoip

#endif // LIBTGVOIP_AUDIO_IO_CALLBACK
