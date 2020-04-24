//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_MEDIASTREAMINPUT_H
#define LIBTGVOIP_MEDIASTREAMINPUT_H

#include "threading.h"
#include "BlockingQueue.h"
#include "Buffers.h"

#include <cstdint>
#include <functional>
#include <memory>
#include <mutex>
#include <unordered_map>

namespace tgvoip
{

class EchoCanceller;

class MediaStreamItf
{
public:
    using CallbackType = std::function<std::size_t(std::uint8_t*, std::size_t, void*)>;

    virtual void Start() = 0;
    virtual void Stop() = 0;
    void SetCallback(CallbackType callback, void* param);

    std::size_t InvokeCallback(std::uint8_t* data, std::size_t length) const;

    virtual ~MediaStreamItf() = default;

private:
    mutable std::mutex m_mutexCallback;
    CallbackType m_callback = nullptr;
    void* m_callbackParam = nullptr;
};

class AudioMixer : public MediaStreamItf
{
public:
    using MediaStreamItfPtr = std::shared_ptr<MediaStreamItf>;

    AudioMixer();
    ~AudioMixer() override;
    void SetOutput(MediaStreamItf* output);
    void Start() override;
    void Stop() override;
    void AddInput(MediaStreamItfPtr input);
    void RemoveInput(const MediaStreamItfPtr& input);
    void SetInputVolume(const MediaStreamItfPtr& input, float volumeDB);
    void SetEchoCanceller(EchoCanceller* aec);

private:
    BufferPool<960 * 2, 16> m_bufferPool;
    BlockingQueue<Buffer> m_processedQueue;
    std::unordered_map<MediaStreamItfPtr, float> m_inputs;

    mutable Mutex m_inputsMutex;
    Semaphore m_semaphore;

    Thread* m_thread = nullptr;
    EchoCanceller* m_echoCanceller = nullptr;

    bool m_running = false;

    void RunThread();
    void DoCallback(std::uint8_t* data, std::size_t length);
    static std::size_t OutputCallback(std::uint8_t* data, std::size_t length, void* arg);
};

class CallbackWrapper : public MediaStreamItf
{
public:
    CallbackWrapper();
    ~CallbackWrapper() override;
    void Start() override;
    void Stop() override;
};

class AudioLevelMeter
{
public:
    AudioLevelMeter();
    float GetLevel();
    void Update(std::int16_t* samples, std::size_t m_count);

private:
    std::int16_t m_absMax;
    std::int16_t m_count;
    std::int16_t m_currentLevelFullRange;
    std::int8_t m_currentLevel;
};

} // namespace tgvoip

#endif // LIBTGVOIP_MEDIASTREAMINPUT_H
