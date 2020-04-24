//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_OPUSDECODER_H
#define LIBTGVOIP_OPUSDECODER_H

#include "threading.h"
#include "utils.h"
#include "BlockingQueue.h"
#include "Buffers.h"
#include "EchoCanceller.h"
#include "JitterBuffer.h"
#include "MediaStreamItf.h"

#include <atomic>
#include <cstdio>
#include <memory>
#include <vector>

struct OpusDecoder;

namespace tgvoip
{

class OpusDecoder
{
public:
    TGVOIP_DISALLOW_COPY_AND_ASSIGN(OpusDecoder);
    virtual void Start();
    virtual void Stop();

    OpusDecoder(MediaStreamItf* dst, bool isAsync, bool needEC);
    virtual ~OpusDecoder();

    std::size_t HandleCallback(std::uint8_t* data, std::size_t len);
    void SetEchoCanceller(EchoCanceller* canceller);
    void SetFrameDuration(std::uint32_t duration);
    void SetJitterBuffer(std::shared_ptr<JitterBuffer> m_jitterBuffer);
    void SetDTX(bool enable);
    void SetLevelMeter(AudioLevelMeter* m_levelMeter);
    void AddAudioEffect(effects::AudioEffect* effect);
    void RemoveAudioEffect(effects::AudioEffect* effect);

private:
    BlockingQueue<Buffer>* m_decodedQueue;
    BufferPool<960 * 2, 32> m_bufferPool;
    std::vector<effects::AudioEffect*> m_postProcEffects;
    std::uint8_t* m_buffer;
    std::uint8_t* m_processedBuffer;
    std::uint8_t* m_lastDecoded;

    ::OpusDecoder* m_dec;
    ::OpusDecoder* m_ecDec;
    EchoCanceller* m_echoCanceller;
    AudioLevelMeter* m_levelMeter;
    std::shared_ptr<JitterBuffer> m_jitterBuffer;

    Thread* m_thread;
    Semaphore* m_semaphore;

    std::size_t m_outputBufferSize;
    std::size_t m_silentPacketCount;
    std::size_t m_nextLen;
    std::ptrdiff_t m_remainingDataLen;

    std::uint32_t m_frameDuration;
    unsigned int m_packetsPerFrame;
    int m_consecutiveLostPackets;

    std::int16_t m_prevLastSample;
    alignas(2) std::uint8_t m_nextBuffer[8192];
    alignas(2) std::uint8_t m_decodeBuffer[8192];

    std::atomic<bool> m_running;
    std::atomic<bool> m_async;
    bool m_enableDTX;
    bool m_prevWasEC;


    void Initialize(bool isAsync, bool needEC);
    void RunThread();
    int DecodeNextFrame();
    static std::size_t Callback(std::uint8_t* data, std::size_t len, void* param);
};

} // namespace tgvoip

#endif // LIBTGVOIP_OPUSDECODER_H
