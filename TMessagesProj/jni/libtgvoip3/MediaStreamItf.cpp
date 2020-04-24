//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "PrivateDefines.h"
#include "MediaStreamItf.h"
#include "EchoCanceller.h"

#include <cassert>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <utility>

using namespace tgvoip;

void MediaStreamItf::SetCallback(std::function<std::size_t(std::uint8_t*, std::size_t, void*)> callback, void* param)
{
    std::lock_guard<std::mutex> lock(m_mutexCallback);
    m_callback = std::move(callback);
    m_callbackParam = param;
}

std::size_t MediaStreamItf::InvokeCallback(std::uint8_t* data, std::size_t length) const
{
    CallbackType callback;
    void* callbackParam;
    {
        std::lock_guard<std::mutex> lock(m_mutexCallback);
        callback = m_callback;
        callbackParam = m_callbackParam;
    }
    if (callback != nullptr)
        return callback(data, length, callbackParam);
    return 0;
}

AudioMixer::AudioMixer()
    : m_processedQueue(16)
    , m_semaphore(16, 0)
{
}

AudioMixer::~AudioMixer() = default;

void AudioMixer::SetOutput(MediaStreamItf* output)
{
    output->SetCallback(OutputCallback, this);
}

void AudioMixer::Start()
{
    assert(!m_running);
    m_running = true;
    m_thread = new Thread(std::bind(&AudioMixer::RunThread, this));
    m_thread->SetName("AudioMixer");
    m_thread->Start();
}

void AudioMixer::Stop()
{
    if (!m_running)
    {
        LOGE("Tried to stop AudioMixer that wasn't started");
        return;
    }
    m_running = false;
    m_semaphore.Release();
    m_thread->Join();
    delete m_thread;
    m_thread = nullptr;
}

void AudioMixer::DoCallback(std::uint8_t* data, std::size_t length)
{
    if (m_processedQueue.Size() == 0)
        m_semaphore.Release(2);
    else
        m_semaphore.Release();
    Buffer buf = m_processedQueue.GetBlocking();
    std::memcpy(data, *buf, 960 * 2);
}

std::size_t AudioMixer::OutputCallback(std::uint8_t* data, std::size_t length, void* arg)
{
    reinterpret_cast<AudioMixer*>(arg)->DoCallback(data, length);
    return 960 * 2;
}

void AudioMixer::AddInput(MediaStreamItfPtr input)
{
    MutexGuard m(m_inputsMutex);
    m_inputs.emplace(std::move(input), 1);
}

void AudioMixer::RemoveInput(const MediaStreamItfPtr& input)
{
    MutexGuard m(m_inputsMutex);
    auto it = m_inputs.find(input);
    if (it != m_inputs.end())
        m_inputs.erase(it);
}

void AudioMixer::SetInputVolume(const MediaStreamItfPtr& input, float volumeDB)
{
    MutexGuard m(m_inputsMutex);
    auto it = m_inputs.find(input);
    if (it != m_inputs.end())
    {
        if (volumeDB == -std::numeric_limits<float>::infinity())
            it->second = 0;
        else
            it->second = std::exp(volumeDB / 20.0f * std::log(10.0f));
    }
}

void AudioMixer::RunThread()
{
    LOGV("AudioMixer thread started");
    while (m_running)
    {
        m_semaphore.Acquire();
        if (!m_running)
            break;

        try
        {
            Buffer data = m_bufferPool.Get();
            MutexGuard m(m_inputsMutex);
            std::int16_t* buf = reinterpret_cast<std::int16_t*>(*data);

            constexpr std::size_t SIZE = 960;
            constexpr std::size_t INT16_SIZE = sizeof(std::int16_t);

            std::array<std::int16_t, SIZE> input;
            std::array<float, SIZE> out;
            out.fill(0);
            int usedInputs = 0;
            for (auto& [source, multiplier] : m_inputs)
            {
                std::size_t res = source->InvokeCallback(reinterpret_cast<std::uint8_t*>(input.data()), STD_ARRAY_SIZEOF(input));
                if (res == 0 || multiplier == 0)
                {
                    continue;
                }
                ++usedInputs;
                for (std::size_t i = 0; i < SIZE; ++i)
                {
                    out[i] += input[i] * multiplier;
                }
            }
            if (usedInputs > 0)
            {
                for (std::size_t i = 0; i < SIZE; ++i)
                {
                    if (out[i] > 32767.0f)
                        buf[i] = std::numeric_limits<std::int16_t>::max();
                    else if (out[i] < -32768.0f)
                        buf[i] = std::numeric_limits<std::int16_t>::min();
                    else
                        buf[i] = static_cast<std::int16_t>(out[i]);
                }
            }
            else
            {
                std::memset(buf, 0, SIZE * INT16_SIZE);
            }
            if (m_echoCanceller != nullptr)
                m_echoCanceller->SpeakerOutCallback(reinterpret_cast<std::uint8_t*>(buf), SIZE * INT16_SIZE);
            m_processedQueue.Put(std::move(data));
        }
        catch (const std::bad_alloc& exception)
        {
            LOGE("AudioMixer: no buffers left.\nwhat():\n%s", exception.what());
            continue;
        }
    }
    LOGI("======== audio mixer thread exiting =========");
}

void AudioMixer::SetEchoCanceller(EchoCanceller* aec)
{
    m_echoCanceller = aec;
}

CallbackWrapper::CallbackWrapper() = default;

CallbackWrapper::~CallbackWrapper() = default;

void CallbackWrapper::Start()
{
}

void CallbackWrapper::Stop()
{
}

AudioLevelMeter::AudioLevelMeter()
{
    m_absMax = 0;
    m_count = 0;
    m_currentLevel = 0;
    m_currentLevelFullRange = 0;
}

float AudioLevelMeter::GetLevel()
{
    return m_currentLevel / 9.0f;
}

void AudioLevelMeter::Update(std::int16_t* samples, std::size_t count)
{
    // Number of bars on the indicator.
    // Note that the number of elements is specified because we are indexing it
    // in the range of 0-32
    const std::array<std::int8_t, 33> permutation =
    {
        0, 1, 2, 3, 4, 4, 5, 5, 5, 5, 6,
        6, 6, 6, 6, 7, 7, 7, 7, 8, 8, 8,
        9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9
    };
    std::int16_t absValue = 0;
    for (std::size_t k = 0; k < count; ++k)
    {
        std::int16_t absolute = static_cast<std::int16_t>(std::abs(samples[k]));
        if (absolute > absValue)
            absValue = absolute;
    }

    if (absValue > m_absMax)
        m_absMax = absValue;
    // Update level approximately 10 times per second
    if (this->m_count++ == 10)
    {
        m_currentLevelFullRange = m_absMax;
        this->m_count = 0;
        // Highest value for a std::int16_t is 0x7fff = 32767
        // Divide with 1000 to get in the range of 0-32 which is the range of
        // the permutation vector
        std::size_t position = static_cast<std::size_t>(m_absMax) / 1000;
        // Make it less likely that the bar stays at position 0. I.e. only if
        // its in the range 0-250 (instead of 0-1000)
        m_currentLevel = permutation[position];
        // Decay the absolute maximum (divide by 4)
        m_absMax >>= 2;
    }
}
