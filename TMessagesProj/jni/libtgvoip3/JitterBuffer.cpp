//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "logging.h"
#include "JitterBuffer.h"
#include "VoIPController.h"
#include "VoIPServerConfig.h"

#include <numeric>
#include <cmath>
#include <cstring>

using namespace tgvoip;

JitterBuffer::JitterBuffer(MediaStreamItf* out, std::uint32_t step)
{
    if (out != nullptr)
        out->SetCallback(JitterBuffer::CallbackOut, this);
    m_step = step;
    if (step < 30)
    {
        m_minDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_20",  6));
        m_maxDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_20", 25));
        m_maxAllowedSlots = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_20", 50));
    }
    else if (step < 50)
    {
        m_minDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_40",  4));
        m_maxDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_40", 15));
        m_maxAllowedSlots = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_40", 30));
    }
    else
    {
        m_minDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_min_delay_60",  2));
        m_maxDelay  = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_delay_60", 10));
        m_maxAllowedSlots = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_max_slots_60", 20));
    }

    m_lossesToReset = static_cast<std::uint32_t>(ServerConfig::GetSharedInstance()->GetInt("jitter_losses_to_reset", 20));
    m_resyncThreshold = ServerConfig::GetSharedInstance()->GetDouble("jitter_resync_threshold", 1.0);
#ifdef TGVOIP_DUMP_JITTER_STATS
#ifdef TGVOIP_JITTER_DUMP_FILE
    dump = fopen(TGVOIP_JITTER_DUMP_FILE, "w");
#elif defined(__ANDROID__)
    dump = fopen("/sdcard/tgvoip_jitter_dump.txt", "w");
#else
    dump = fopen("tgvoip_jitter_dump.txt", "w");
#endif
    tgvoip_log_file_write_header(dump);
    fprintf(dump, "PTS\tRTS\tNumInBuf\tAJitter\tADelay\tTDelay\n");
#endif
    ResetNonBlocking();
}

JitterBuffer::~JitterBuffer()
{
    Reset();
}

void JitterBuffer::SetMinPacketCount(std::uint32_t count)
{
    LOGI("jitter: set min packet count %u", count);
    MutexGuard m(m_mutex);
    m_delay = count;
    m_minDelay = count;
    //Reset();
}

std::uint32_t JitterBuffer::GetMinPacketCount() const
{
    MutexGuard m(m_mutex);
    return JitterBuffer::GetMinPacketCountNonBlocking();
}

std::uint32_t JitterBuffer::GetMinPacketCountNonBlocking() const
{
    return m_delay;
}

std::size_t JitterBuffer::CallbackIn(std::uint8_t* data, std::size_t len, void* param)
{
    //((JitterBuffer*)param)->HandleInput(data, len);
    return 0;
}

std::size_t JitterBuffer::CallbackOut(std::uint8_t* data, std::size_t len, void* param)
{
    JitterBuffer* jBuffer = reinterpret_cast<JitterBuffer*>(param);
    int playbackDuration = 0;
    bool isEC = false;
    std::size_t result = jBuffer->HandleOutput(data, len, 0, true, playbackDuration, isEC);
    if (result == 0)
        result = jBuffer->HandleOutput(data, len, 0, false, playbackDuration, isEC);

    return result; //((JitterBuffer*)param)->HandleOutput(data, len, 0, nullptr);
}

void JitterBuffer::HandleInput(const std::uint8_t* data, std::size_t len, std::uint32_t timestamp, bool isEC)
{
    MutexGuard m(m_mutex);
    jitter_packet_t pkt;
    pkt.size = len;
    pkt.timestamp = timestamp;
    pkt.isEC = isEC;
    PutInternal(pkt, data, !isEC);
}

void JitterBuffer::ResetNonBlocking()
{
    m_wasReset = true;
    m_lastPutTimestamp = 0;
    m_slots.clear();
    m_delayHistory.Reset();
    m_lateHistory.Reset();
    m_lostSinceReset = 0;
    m_gotSinceReset = 0;
    m_expectNextAtTime = 0;
    m_deviationHistory.Reset();
    m_outstandingDelayChange = 0;
    m_dontChangeOutstandingDelay = 0;
}

void JitterBuffer::Reset()
{
    MutexGuard m(m_mutex);
    ResetNonBlocking();
}

std::size_t JitterBuffer::HandleOutput(std::uint8_t* data, std::size_t len, std::uint32_t offsetInSteps,
                                       bool advance, int& playbackScaledDuration, bool& isEC)
{
    jitter_packet_t pkt;
    pkt.buffer = Buffer::Wrap(
            data, len, [](void*) {}, [](void* a, std::size_t) -> void* { return a; });
    pkt.size = len;
    MutexGuard m(m_mutex);

    Status result = GetInternal(&pkt, offsetInSteps, advance);
    if (m_outstandingDelayChange != 0)
    {
        if (m_outstandingDelayChange < 0)
        {
            playbackScaledDuration = 40;
            m_outstandingDelayChange += 20;
        }
        else
        {
            playbackScaledDuration = 80;
            m_outstandingDelayChange -= 20;
        }
    }
    else if (advance && GetCurrentDelayNonBlocking() == 0)
    {
        playbackScaledDuration = 80;
    }
    else
    {
        playbackScaledDuration = 60;
    }
    switch (result)
    {
    case Status::OK:
    case Status::REPLACED:
        isEC = pkt.isEC;
        return pkt.size;
    case Status::MISSING:
        return 0;
    }
}

JitterBuffer::Status JitterBuffer::GetInternal(jitter_packet_t* pkt, std::uint32_t offset, bool advance)
{
    std::uint32_t timestampToGet = m_nextTimestamp + offset * m_step;

    auto it = m_slots.find(timestampToGet);

    if (it != m_slots.end())
    {
        const jitter_packet_t& slot = it->second;
        if (pkt != nullptr && pkt->size < slot.size)
        {
            LOGE("jitter: packet won't fit into provided buffer of %d (need %d)", int(slot.size), int(pkt->size));
        }
        else
        {
            if (pkt != nullptr)
            {
                pkt->size = slot.size;
                pkt->timestamp = slot.timestamp;
                pkt->buffer.CopyFrom(slot.buffer, slot.size);
                pkt->isEC = slot.isEC;
            }
        }
        if (advance)
        {
            Advance();
            if (offset == 0)
            {
                auto node = m_slots.extract(it);
                m_slotsHistory.insert(std::move(node));
                auto lastErased = m_slotsHistory.upper_bound(timestampToGet > m_replaceRadius * m_step ? timestampToGet - m_replaceRadius * m_step : 0);
                m_slotsHistory.erase(m_slotsHistory.begin(), lastErased);
            }
        }
        m_lostCount = 0;
        return Status::OK;
    }

    LOGV("jitter: found no packet for timestamp %lld (last put = %d, lost = %d)", static_cast<long long>(timestampToGet), m_lastPutTimestamp, m_lostCount);

    if (advance)
        Advance();

    ++m_lostCount;
    if (offset == 0)
    {
        ++m_lostPackets;
        ++m_lostSinceReset;
    }
    if (m_lostCount >= m_lossesToReset || (m_gotSinceReset > m_delay * 25 && m_lostSinceReset > m_gotSinceReset / 2))
    {
        LOGW("jitter: lost %d packets in a row, resetting", m_lostCount);
        m_dontIncDelay = 16;
        m_dontDecDelay += 128;
        m_lostCount = 0;
        ResetNonBlocking();
    }

    if (!(advance && offset == 0))
        return Status::MISSING;

    std::int64_t timestampFrom = (m_replaceRadius * m_step < timestampToGet ? timestampToGet - m_replaceRadius * m_step : 0);
    std::int64_t timestampTo = timestampToGet + m_replaceRadius * m_step;

    std::int64_t left = (timestampToGet > m_step ? timestampToGet - m_step : 0);
    std::int64_t right = timestampToGet + m_step;

    auto neighbor = m_slots.end();

    for (; left >= timestampFrom && right <= timestampTo;
         left -= static_cast<std::int64_t>(m_step), right += static_cast<std::int64_t>(m_step))
    {
        auto it = m_slots.find(static_cast<std::uint32_t>(left));
        if (it != m_slots.end() && !it->second.isEC)
        {
            neighbor = it;
            break;
        }
        it = m_slotsHistory.find(static_cast<std::uint32_t>(left));
        if (it != m_slotsHistory.end() && !it->second.isEC)
        {
            neighbor = it;
            break;
        }
        it = m_slots.find(static_cast<std::uint32_t>(right));
        if (it != m_slots.end() && !it->second.isEC)
        {
            neighbor = it;
            break;
        }
    }

    if (neighbor == m_slots.end())
    {
        return Status::MISSING;
    }

    pkt->timestamp = timestampToGet;
    pkt->isEC = neighbor->second.isEC;
    pkt->size = neighbor->second.size;
    pkt->buffer = Buffer(pkt->size);
    pkt->buffer.CopyFrom(neighbor->second.buffer, pkt->size);

    return Status::REPLACED;
}

void JitterBuffer::PutInternal(const jitter_packet_t& pkt, const std::uint8_t* data, bool overwriteExisting)
{
    if (pkt.size > JITTER_SLOT_SIZE)
    {
        LOGE("The packet is too big to fit into the jitter buffer");
        return;
    }

    if (overwriteExisting)
    {
        auto it = m_slots.find(pkt.timestamp);
        if (it != m_slots.end())
        {
            jitter_packet_t& slot = it->second;
            slot.buffer.CopyFrom(data, 0, pkt.size);
            slot.size = pkt.size;
            slot.isEC = pkt.isEC;
            return;
        }
    }

    ++m_gotSinceReset;
    if (m_wasReset)
    {
        m_wasReset = false;
        m_outstandingDelayChange = 0;
        if (m_step * m_delay < pkt.timestamp)
        {
            m_nextTimestamp = pkt.timestamp - m_step * m_delay;
        }
        else
        {
            m_nextTimestamp = 0;
            m_addToTimestamp = m_step * m_delay - pkt.timestamp;
        }
        LOGI("jitter: resyncing, next timestamp = %lld (step=%d, minDelay=%f)", static_cast<long long>(m_nextTimestamp), m_step, double(m_delay));
    }

    std::uint32_t addition = GetAdditionForTimestamp();
    auto lastErased = m_slots.lower_bound(addition < m_nextTimestamp ? m_nextTimestamp - addition : 0);
    m_slots.erase(m_slots.begin(), lastErased);

    double time = VoIPController::GetCurrentTime();
    if (m_expectNextAtTime != 0)
    {
        double dev = m_expectNextAtTime - time;
        m_deviationHistory.Add(dev);
        m_expectNextAtTime += m_step / 1000.0;
    }
    else
    {
        m_expectNextAtTime = time + m_step / 1000.0;
    }

    if (pkt.timestamp + GetAdditionForTimestamp() < m_nextTimestamp)
    {
        ++m_latePacketCount;
        return;
    }
    if (pkt.timestamp + m_addToTimestamp < m_nextTimestamp)
    {
        ++m_latePacketCount;
        --m_lostPackets;
    }

    if (pkt.timestamp > m_lastPutTimestamp)
        m_lastPutTimestamp = pkt.timestamp;

    bool emplacePacket = true;
    if (m_slots.size() >= m_maxAllowedSlots)
    {
        Advance();
        if (pkt.timestamp > m_slots.begin()->second.timestamp)
            m_slots.erase(m_slots.begin());
        else
            emplacePacket = false;
    }
    if (emplacePacket)
    {
        const auto& [it, _] = m_slots.emplace(pkt.timestamp,
                                              jitter_packet_t
                                              {
                                                  .buffer = m_bufferPool.Get(),
                                                  .recvTimeDiff = time - m_prevRecvTime,
                                                  .size = pkt.size,
                                                  .timestamp = pkt.timestamp,
                                                  .isEC = pkt.isEC
                                              });
        it->second.buffer.CopyFrom(data, 0, pkt.size);
    }
#ifdef TGVOIP_DUMP_JITTER_STATS
    fprintf(dump, "%u\t%.03f\t%d\t%.03f\t%.03f\t%.03f\n", pkt->timestamp, time, GetCurrentDelay(), lastMeasuredJitter, lastMeasuredDelay, minDelay);
#endif
    m_prevRecvTime = time;
}

void JitterBuffer::Advance()
{
    m_nextTimestamp += m_step;
    if (m_addToTimestamp > m_step)
        m_addToTimestamp -= m_step;
    else
        m_addToTimestamp = 0;
}

std::uint32_t JitterBuffer::GetAdditionForTimestamp() const
{
    return m_addToTimestamp + (m_maxDelay - m_delay) * m_step;
}

std::uint32_t JitterBuffer::GetCurrentDelay() const
{
    MutexGuard m(m_mutex);
    return GetCurrentDelayNonBlocking();
}

std::uint32_t JitterBuffer::GetCurrentDelayNonBlocking() const
{
    return static_cast<std::uint32_t>(m_slots.size());
}

void JitterBuffer::Tick()
{
    MutexGuard m(m_mutex);

    m_lateHistory.Add(m_latePacketCount);
    m_latePacketCount = 0;
    bool absolutelyNoLatePackets = m_lateHistory.Max() == 0;

    double avgLate16 = m_lateHistory.Average(16);
    if (avgLate16 >= m_resyncThreshold)
    {
        LOGV("resyncing: avgLate16=%f, resyncThreshold=%f", avgLate16, m_resyncThreshold);
        m_wasReset = true;
    }

    if (absolutelyNoLatePackets && m_dontDecDelay > 0)
        --m_dontDecDelay;

    m_delayHistory.Add(GetCurrentDelayNonBlocking());
    m_avgDelay = m_delayHistory.Average(32);

    double stddev = 0;
    double avgdev = m_deviationHistory.Average();
    for (std::size_t i = 0; i < m_deviationHistory.Size(); ++i)
    {
        double d = (m_deviationHistory[i] - avgdev);
        stddev += d * d;
    }
    stddev = std::sqrt(stddev / 64);
    std::uint32_t stddevDelay = static_cast<std::uint32_t>(std::ceil(stddev * 2 * 1000 / m_step));
    if (stddevDelay < m_minDelay)
        stddevDelay = m_minDelay;
    if (stddevDelay > m_maxDelay)
        stddevDelay = m_maxDelay;
    if (stddevDelay != m_delay)
    {
        std::int32_t diff = static_cast<std::int32_t>(stddevDelay) - static_cast<std::int32_t>(m_delay);
        if (diff > 0)
            m_dontDecDelay = 100;
        if (diff < -1)
            diff = -1;
        if (diff > 1)
            diff = 1;
        if ((diff > 0 && m_dontIncDelay == 0) || (diff < 0 && m_dontDecDelay == 0))
        {
            m_delay = static_cast<std::uint32_t>(static_cast<std::int32_t>(m_delay) + diff);
            m_outstandingDelayChange += diff * 60;
            m_dontChangeOutstandingDelay += 32;
            if (diff < 0)
                m_dontDecDelay += 25;
            if (diff > 0)
                m_dontIncDelay = 25;
        }
    }
    m_lastMeasuredJitter = stddev;
    m_lastMeasuredDelay = stddevDelay;
    if (m_dontChangeOutstandingDelay == 0)
    {
        if (m_avgDelay > m_delay + 0.5)
        {
            m_outstandingDelayChange -= m_avgDelay > m_delay + 2 ? 60 : 20;
            m_dontChangeOutstandingDelay += 10;
        }
        else if (m_avgDelay < m_delay - 0.3)
        {
            m_outstandingDelayChange += 20;
            m_dontChangeOutstandingDelay += 10;
        }
    }
    if (m_dontChangeOutstandingDelay > 0)
        --m_dontChangeOutstandingDelay;
}

void JitterBuffer::GetAverageLateCount(double* out) const
{
    double avgLate64, avgLate32, avgLate16;
    {
        MutexGuard m(m_mutex);
        avgLate64 = m_lateHistory.Average(64);
        avgLate32 = m_lateHistory.Average(32);
        avgLate16 = m_lateHistory.Average(16);
    }
    out[0] = avgLate16;
    out[1] = avgLate32;
    out[2] = avgLate64;
}

int JitterBuffer::GetAndResetLostPacketCount()
{
    MutexGuard m(m_mutex);
    int r = m_lostPackets;
    m_lostPackets = 0;
    return r;
}

double JitterBuffer::GetLastMeasuredJitter() const
{
    MutexGuard m(m_mutex);
    return m_lastMeasuredJitter;
}

double JitterBuffer::GetLastMeasuredDelay() const
{
    MutexGuard m(m_mutex);
    return m_lastMeasuredDelay;
}

double JitterBuffer::GetAverageDelay() const
{
    MutexGuard m(m_mutex);
    return m_avgDelay;
}
