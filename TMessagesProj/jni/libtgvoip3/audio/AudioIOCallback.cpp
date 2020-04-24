//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "../logging.h"
#include "../VoIPController.h"
#include "AudioIOCallback.h"

#include <cstring>

using namespace tgvoip;
using namespace tgvoip::audio;

#pragma mark - IO

AudioIOCallback::AudioIOCallback()
{
    m_input = new AudioInputCallback();
    m_output = new AudioOutputCallback();
}

AudioIOCallback::~AudioIOCallback()
{
    delete m_input;
    delete m_output;
}

AudioInput* AudioIOCallback::GetInput()
{
    return m_input;
}

AudioOutput* AudioIOCallback::GetOutput()
{
    return m_output;
}

#pragma mark - Input

AudioInputCallback::AudioInputCallback()
{
    m_thread = new Thread(std::bind(&AudioInputCallback::RunThread, this));
    m_thread->SetName("AudioInputCallback");
}

AudioInputCallback::~AudioInputCallback()
{
    m_running = false;
    m_thread->Join();
    delete m_thread;
}

void AudioInputCallback::Start()
{
    if (!m_running)
    {
        m_running = true;
        m_thread->Start();
    }
    m_recording = true;
}

void AudioInputCallback::Stop()
{
    m_recording = false;
}

void AudioInputCallback::SetDataCallback(std::function<void(std::int16_t*, std::size_t)> dataCallback)
{
    m_dataCallback = std::move(dataCallback);
}

void AudioInputCallback::RunThread()
{
    std::int16_t buf[960];
    while (m_running)
    {
        double t = VoIPController::GetCurrentTime();
        std::memset(buf, 0, sizeof(buf));
        m_dataCallback(buf, 960);
        InvokeCallback(reinterpret_cast<std::uint8_t*>(buf), 960 * 2);
        double sl = 0.02 - (VoIPController::GetCurrentTime() - t);
        if (sl > 0)
            Thread::Sleep(sl);
    }
}

#pragma mark - Output

AudioOutputCallback::AudioOutputCallback()
{
    m_thread = new Thread(std::bind(&AudioOutputCallback::RunThread, this));
    m_thread->SetName("AudioOutputCallback");
}

AudioOutputCallback::~AudioOutputCallback()
{
    m_running = false;
    m_thread->Join();
    delete m_thread;
}

void AudioOutputCallback::Start()
{
    if (!m_running)
    {
        m_running = true;
        m_thread->Start();
    }
    m_playing = true;
}

void AudioOutputCallback::Stop()
{
    m_playing = false;
}

bool AudioOutputCallback::IsPlaying()
{
    return m_playing;
}

void AudioOutputCallback::SetDataCallback(std::function<void(std::int16_t*, std::size_t)> c)
{
    m_dataCallback = c;
}

void AudioOutputCallback::RunThread()
{
    std::int16_t buf[960];
    while (m_running)
    {
        double t = VoIPController::GetCurrentTime();
        InvokeCallback(reinterpret_cast<std::uint8_t*>(buf), 960 * 2);
        m_dataCallback(buf, 960);
        double sl = 0.02 - (VoIPController::GetCurrentTime() - t);
        if (sl > 0)
            Thread::Sleep(sl);
    }
}
