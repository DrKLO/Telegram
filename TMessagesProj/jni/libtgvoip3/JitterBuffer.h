//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_JITTERBUFFER_H
#define LIBTGVOIP_JITTERBUFFER_H

#include "threading.h"
#include "BlockingQueue.h"
#include "Buffers.h"
#include "MediaStreamItf.h"

#include <cstdint>
#include <map>

#define JITTER_SLOT_COUNT 64
#define JITTER_SLOT_SIZE 1024
#define HISTORY_SIZE 64

namespace tgvoip
{

class JitterBuffer
{
public:
    JitterBuffer(MediaStreamItf* out, std::uint32_t m_step);
    ~JitterBuffer();
    void SetMinPacketCount(std::uint32_t count);
    std::uint32_t GetMinPacketCount() const;
    std::uint32_t GetCurrentDelay() const;
    double GetAverageDelay() const;
    void Reset();
    void HandleInput(const std::uint8_t* data, std::size_t len, std::uint32_t timestamp, bool isEC);
    std::size_t HandleOutput(std::uint8_t* data, std::size_t len, std::uint32_t offsetInSteps,
                             bool advance, int& playbackScaledDuration, bool& isEC);
    void Tick();
    void GetAverageLateCount(double* out) const;
    int GetAndResetLostPacketCount();
    double GetLastMeasuredJitter() const;
    double GetLastMeasuredDelay() const;

private:
    struct jitter_packet_t
    {
        Buffer buffer = Buffer();
        double recvTimeDiff;
        std::size_t size;
        std::uint32_t timestamp;
        bool isEC;
    };

    enum class Status
    {
        OK = 1,
        MISSING,
        REPLACED,
    };

    static std::size_t CallbackIn(std::uint8_t* data, std::size_t len, void* param);
    static std::size_t CallbackOut(std::uint8_t* data, std::size_t len, void* param);
    void PutInternal(const jitter_packet_t& pkt, const std::uint8_t* data, bool overwriteExisting);
    Status GetInternal(jitter_packet_t* pkt, std::uint32_t offset, bool advance);
    void Advance();
    std::uint32_t GetAdditionForTimestamp() const;
    std::uint32_t GetMinPacketCountNonBlocking() const;
    std::uint32_t GetCurrentDelayNonBlocking() const;
    void ResetNonBlocking();

#ifdef TGVOIP_DUMP_JITTER_STATS
    FILE* dump;
#endif

    BufferPool<JITTER_SLOT_SIZE, JITTER_SLOT_COUNT> m_bufferPool;
    mutable Mutex m_mutex;

    double m_resyncThreshold;
    double m_prevRecvTime = 0;
    double m_expectNextAtTime = 0;
    double m_lastMeasuredJitter = 0;
    double m_lastMeasuredDelay = 0;
    double m_avgDelay = 0;

    std::map<std::uint32_t, jitter_packet_t> m_slots;

    // if there is no slot with requested timestamp in m_slots,
    // attempt to find previous a slot with close previous timestamp
    std::map<std::uint32_t, jitter_packet_t> m_slotsHistory;

    HistoricBuffer<unsigned int, HISTORY_SIZE, double> m_delayHistory;
    HistoricBuffer<unsigned int, HISTORY_SIZE, double> m_lateHistory;
    HistoricBuffer<double, HISTORY_SIZE> m_deviationHistory;

    std::uint32_t m_nextTimestamp = 0;
    // if m_nextTimestamp is too little, we need to use this because of
    // restrictions of unsigned arithmetics
    std::uint32_t m_addToTimestamp = 0;
    std::uint32_t m_step;
    std::uint32_t m_delay = 6;
    std::uint32_t m_minDelay;
    std::uint32_t m_maxDelay;
    std::uint32_t m_maxAllowedSlots;
    std::uint32_t m_lastPutTimestamp;
    std::uint32_t m_lossesToReset;

    std::uint32_t m_replaceRadius = 1;

    unsigned int m_lostCount = 0;
    unsigned int m_lostSinceReset = 0;
    unsigned int m_gotSinceReset = 0;
    unsigned int m_latePacketCount = 0;
    unsigned int m_dontIncDelay = 0;
    unsigned int m_dontDecDelay = 0;
    unsigned int m_dontChangeOutstandingDelay = 0;
    int m_lostPackets = 0;
    int m_outstandingDelayChange = 0;

    bool m_wasReset = true;
};

} // namespace tgvoip

#endif // LIBTGVOIP_JITTERBUFFER_H
