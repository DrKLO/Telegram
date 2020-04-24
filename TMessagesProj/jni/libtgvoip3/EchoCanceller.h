//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_ECHOCANCELLER_H
#define LIBTGVOIP_ECHOCANCELLER_H

#include "threading.h"
#include "utils.h"
#include "BlockingQueue.h"
#include "Buffers.h"
#include "MediaStreamItf.h"

#include <cstdint>

namespace webrtc
{

class AudioProcessing;
class AudioFrame;

} // namespace webrtc

namespace tgvoip
{

class EchoCanceller
{
public:
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(EchoCanceller);
    EchoCanceller(bool m_enableAEC, bool m_enableNS, bool m_enableAGC);
    virtual ~EchoCanceller();
    virtual void Start();
    virtual void Stop();
    void SpeakerOutCallback(std::uint8_t* data, std::size_t len);
    void Enable(bool enabled);
    void ProcessInput(std::int16_t* inOut, std::size_t numSamples, bool& hasVoice);
    void SetAECStrength(int strength);
    void SetVoiceDetectionEnabled(bool enabled);

private:
#ifndef TGVOIP_NO_DSP
    BufferPool<960 * 2, 10> m_farendBufferPool;
    BlockingQueue<Buffer>* m_farendQueue;
    Thread* m_bufferFarendThread;
    webrtc::AudioProcessing* m_apm = nullptr;
    webrtc::AudioFrame* m_audioFrame = nullptr;
    bool m_didBufferFarend;
    bool m_running;

    void RunBufferFarendThread();
#endif

    bool m_enableAEC;
    bool m_enableAGC;
    bool m_enableNS;
    bool m_enableVAD = false;
    bool m_isOn;
};

namespace effects
{

class AudioEffect
{
public:
    virtual ~AudioEffect() = 0;
    virtual void Process(std::int16_t* inOut, std::size_t numSamples) const = 0;
    virtual void SetPassThrough(bool m_passThrough);

protected:
    [[nodiscard]] bool GetPassThrough() const;

private:
    bool m_passThrough = false;
};

class Volume : public AudioEffect
{
public:
    Volume();
    ~Volume() override;
    void Process(std::int16_t* inOut, std::size_t numSamples) const override;
    /**
     * Level is (0.0, 2.0]
     */
    void SetLevel(float m_level);
    [[nodiscard]] float GetLevel() const;

private:
    float m_level = 1.0f;
    float m_multiplier = 1.0f;
};

} // namespace effects

} // namespace tgvoip

#endif // LIBTGVOIP_ECHOCANCELLER_H
