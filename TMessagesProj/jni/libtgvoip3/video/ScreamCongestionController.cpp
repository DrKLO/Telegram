//
// Created by Grishka on 25/02/2019.
//

#include "../logging.h"
#include "../VoIPController.h"
#include "ScreamCongestionController.h"

#include <algorithm>
#include <cmath>

using namespace tgvoip;
using namespace tgvoip::video;

namespace
{

constexpr float QDELAY_TARGET_LO = 0.1f; // seconds
constexpr float QDELAY_TARGET_HI = 0.4f; // seconds
constexpr float QDELAY_WEIGHT = 0.1f;
constexpr float QDELAY_TREND_TH = 0.2f;
constexpr std::uint32_t MIN_CWND = 3000; // bytes
constexpr float MAX_BYTES_IN_FLIGHT_HEAD_ROOM = 1.1f;
constexpr float GAIN = 1.0f;
constexpr float BETA_LOSS = 0.9f;
constexpr float BETA_ECN = 0.9f;
constexpr float BETA_R = 0.9f;
constexpr std::uint32_t MSS = 1024;
constexpr float RATE_ADJUST_INTERVAL = 0.2f;
constexpr std::uint32_t TARGET_BITRATE_MIN = 50 * 1024; // bps
constexpr std::uint32_t TARGET_BITRATE_MAX = 500 * 1024; // bps
constexpr std::uint32_t RAMP_UP_SPEED = 1024 * 1024; //200000; // bps/s
constexpr float PRE_CONGESTION_GUARD = 0.1f;
constexpr float TX_QUEUE_SIZE_FACTOR = 1.0f;
constexpr float RTP_QDELAY_TH = 0.02f; // seconds
constexpr float TARGET_RATE_SCALE_RTP_QDELAY = 0.95f;
constexpr float QDELAY_TREND_LO = 0.2f;
constexpr float T_RESUME_FAST_INCREASE = 5.0f; // seconds
constexpr std::uint32_t RATE_PACE_MIN = 50000; // bps

} // namespace

ScreamCongestionController::ScreamCongestionController()
    : m_qdelayTarget(QDELAY_TARGET_LO)
    , m_cwnd(MIN_CWND)
{
}

void ScreamCongestionController::UpdateVariables(float qdelay)
{
    float qdelayFraction = qdelay / m_qdelayTarget;
    m_qdelayFractionAvg = (1.0f - QDELAY_WEIGHT) * m_qdelayFractionAvg + qdelayFraction * QDELAY_WEIGHT;
    m_qdelayFractionHist.Add(qdelayFraction);
    float avg = m_qdelayFractionHist.Average();

    float r1 = 0.0, r0 = 0.0;
    for (std::size_t i = m_qdelayFractionHist.Size(); i > 0; --i)
    {
        float v = m_qdelayFractionHist[i - 1] - avg;
        r0 += v * v;
    }
    for (std::size_t i = m_qdelayFractionHist.Size(); i > 1; --i)
    {
        float v1 = m_qdelayFractionHist[i - 1] - avg;
        float v2 = m_qdelayFractionHist[i - 2] - avg;
        r1 += v1 * v2;
    }
    float a = r1 / r0;
    m_qdelayTrend = std::min(1.0f, std::max(0.0f, a * m_qdelayFractionAvg));
    m_qdelayTrendMem = std::max(0.99f * m_qdelayTrendMem, m_qdelayTrend);

    if (m_qdelayTrend > QDELAY_TREND_LO)
    {
        m_lastTimeQDelayTrendWasGreaterThanLo = VoIPController::GetCurrentTime();
    }
}

void ScreamCongestionController::UpdateCWnd(float qdelay)
{
    if (m_inFastIncrease)
    {
        if (m_qdelayTrend >= QDELAY_TREND_TH)
        {
            m_inFastIncrease = false;
        }
        else
        {
            if (m_bytesInFlight * 1.5f + m_bytesNewlyAcked > m_cwnd)
            {
                m_cwnd += m_bytesNewlyAcked;
            }
            return;
        }
    }

    float offTarget = (m_qdelayTarget - qdelay) / m_qdelayTarget;

    float gain = GAIN;
    float cwndDelta = gain * offTarget * m_bytesNewlyAcked * MSS / m_cwnd;
    if (offTarget > 0 && m_bytesInFlight * 1.25f + m_bytesNewlyAcked <= m_cwnd)
    {
        cwndDelta = 0.0;
    }
    m_cwnd += static_cast<std::uint32_t>(cwndDelta);
    m_cwnd = std::min(m_cwnd, static_cast<std::uint32_t>(m_maxBytesInFlight * MAX_BYTES_IN_FLIGHT_HEAD_ROOM));
    m_cwnd = std::max(m_cwnd, MIN_CWND);
}

void ScreamCongestionController::AdjustQDelayTarget(float qdelay)
{
    float qdelayNorm = qdelay / QDELAY_TARGET_LO;
    m_qdelayNormHist.Add(qdelayNorm);

    float qdelayNormAvg = m_qdelayNormHist.Average();
    float qdelayNormVar = 0.0;
    for (std::uint32_t i = 0; i < m_qdelayNormHist.Size(); i++)
    {
        float tmp = m_qdelayNormHist[i] - qdelayNormAvg;
        qdelayNormVar += tmp * tmp;
    }
    qdelayNormVar /= m_qdelayNormHist.Size();

    float newTarget = qdelayNormAvg + std::sqrt(qdelayNormVar);
    newTarget *= QDELAY_TARGET_LO;

    if (m_lossEventRate > 0.002f)
    {
        m_qdelayTarget = 1.5f * newTarget;
    }
    else
    {
        if (qdelayNormVar < 0.2f)
        {
            m_qdelayTarget = newTarget;
        }
        else
        {
            if (newTarget < QDELAY_TARGET_LO)
            {
                m_qdelayTarget = std::max(m_qdelayTarget * 0.5f, newTarget);
            }
            else
            {
                m_qdelayTarget *= 0.9f;
            }
        }
    }

    m_qdelayTarget = std::min(QDELAY_TARGET_HI, m_qdelayTarget);
    m_qdelayTarget = std::max(QDELAY_TARGET_LO, m_qdelayTarget);
}

void ScreamCongestionController::AdjustBitrate()
{
    if (m_lossPending)
    {
        m_lossPending = false;
        m_targetBitrate = std::max(static_cast<std::uint32_t>(BETA_R * m_targetBitrate), TARGET_BITRATE_MIN);
        return;
    }

    float rampUpSpeed = std::min(RAMP_UP_SPEED, m_targetBitrate / 2);
    float scale = static_cast<float>(m_targetBitrate - m_targetBitrateLastMax) / m_targetBitrateLastMax;
    scale = std::max(0.2f, std::min(1.0f, (scale * 4) * (scale * 4)));
    float currentRate = std::max(m_rateTransmit, m_rateAck);

    if (m_inFastIncrease)
    {
        m_targetBitrate += static_cast<std::uint32_t>((rampUpSpeed * RATE_ADJUST_INTERVAL) * scale);
    }
    else
    {
        float deltaRate = currentRate * (1.0f - PRE_CONGESTION_GUARD * m_qdelayTrend) - TX_QUEUE_SIZE_FACTOR * m_rtpQueueSize;
        if (deltaRate > 0.0f)
        {
            deltaRate *= scale;
            deltaRate = std::min(deltaRate, rampUpSpeed * RATE_ADJUST_INTERVAL);
        }
        m_targetBitrate += static_cast<std::uint32_t>(deltaRate);
        float rtpQueueDelay = m_rtpQueueSize / currentRate;
        if (rtpQueueDelay > RTP_QDELAY_TH)
        {
            m_targetBitrate = static_cast<std::uint32_t>(m_targetBitrate * TARGET_RATE_SCALE_RTP_QDELAY);
        }
    }

    float rateMediaLimit = std::max(currentRate, std::max(m_rateMedia, m_rateMediaMedian));
    rateMediaLimit *= (2.0f - m_qdelayTrendMem);
    m_targetBitrate = std::min(m_targetBitrate, static_cast<std::uint32_t>(rateMediaLimit));
    m_targetBitrate = std::min(TARGET_BITRATE_MAX, std::max(TARGET_BITRATE_MIN, m_targetBitrate));
}

void ScreamCongestionController::CalculateSendWindow(float qdelay)
{
    if (qdelay <= m_qdelayTarget)
        m_sendWnd = m_cwnd + MSS - m_bytesInFlight;
    else
        m_sendWnd = m_cwnd - m_bytesInFlight;
}

void ScreamCongestionController::ProcessAcks(float oneWayDelay, std::uint32_t bytesNewlyAcked, std::uint32_t lossCount, double rtt)
{
    if (m_prevOneWayDelay != 0.0f)
    {
        double currentTime = VoIPController::GetCurrentTime();
        float qdelay = oneWayDelay - m_prevOneWayDelay;
        m_sRTT = static_cast<float>(rtt);
        m_bytesInFlight -= bytesNewlyAcked;
        m_rtpQueueSize -= (bytesNewlyAcked * 8);
        UpdateBytesInFlightHistory();
        m_bytesAcked += bytesNewlyAcked;
        if (currentTime - m_lastVariablesUpdateTime >= 0.050)
        {
            m_lastVariablesUpdateTime = currentTime;
            UpdateVariables(qdelay);
        }
        if (currentTime - m_lastRateAdjustmentTime >= static_cast<double>(RATE_ADJUST_INTERVAL))
        {
            m_lastRateAdjustmentTime = currentTime;
            AdjustBitrate();
        }
        if (lossCount > m_prevLossCount && currentTime > m_ignoreLossesUntil)
        {
            LOGD("Scream: loss detected");
            m_ignoreLossesUntil = currentTime + rtt;
            LOGD("ignoring losses for %f", rtt);
            m_inFastIncrease = false;
            m_cwnd = std::max(MIN_CWND, static_cast<std::uint32_t>(m_cwnd * BETA_LOSS));
            AdjustQDelayTarget(qdelay);
            CalculateSendWindow(qdelay);
            m_lossPending = true;
            m_lastTimeQDelayTrendWasGreaterThanLo = currentTime;
        }
        else
        {
            this->m_bytesNewlyAcked += bytesNewlyAcked;
            if (currentTime - m_lastCWndUpdateTime >= 0.15)
            {
                m_lastCWndUpdateTime = currentTime;
                UpdateCWnd(qdelay);
                this->m_bytesNewlyAcked = 0;
            }
            AdjustQDelayTarget(qdelay);
            CalculateSendWindow(qdelay);
            if (!m_inFastIncrease)
            {
                if (currentTime - m_lastTimeQDelayTrendWasGreaterThanLo >= static_cast<double>(T_RESUME_FAST_INCREASE))
                {
                    m_inFastIncrease = true;
                }
            }
        }
        m_prevLossCount = lossCount;
    }
    m_prevOneWayDelay = oneWayDelay;
}

void ScreamCongestionController::ProcessPacketSent(std::uint32_t size)
{
    m_bytesInFlight += size;
    m_rtpQueueSize += (size * 8);
    m_bytesSent += size;
    double currentTime = VoIPController::GetCurrentTime();
    if (currentTime - m_rateTransmitUpdateTime >= 0.2)
    {
        m_rateTransmit = static_cast<float>((m_bytesSent * 8) / (currentTime - m_rateTransmitUpdateTime));
        m_rateAck = static_cast<float>((m_bytesAcked * 8) / (currentTime - m_rateTransmitUpdateTime));
        m_rateTransmitUpdateTime = currentTime;
        m_bytesSent = 0;
        m_bytesAcked = 0;
    }
    UpdateBytesInFlightHistory();
}

void ScreamCongestionController::ProcessPacketLost(std::uint32_t size)
{
    m_bytesInFlight -= size;
    m_rtpQueueSize -= (size * 8);
    UpdateBytesInFlightHistory();
}

double ScreamCongestionController::GetPacingInterval()
{
    float paceBitrate = std::max(static_cast<float>(RATE_PACE_MIN), m_cwnd * 8.0f / m_sRTT);
    double pacingInterval = static_cast<double>(m_rtpSize * 8.0f / paceBitrate);
    return std::min(0.010, pacingInterval);
}

void ScreamCongestionController::UpdateBytesInFlightHistory()
{
    double currentTime = VoIPController::GetCurrentTime();
    ValueSample now = {m_bytesInFlight, currentTime};
    m_bytesInFlightHistory.emplace_back(now);
    std::uint32_t max = 0;
    for (auto it = m_bytesInFlightHistory.begin(); it != m_bytesInFlightHistory.end();)
    {
        if (currentTime - it->time >= 5.0)
        {
            it = m_bytesInFlightHistory.erase(it);
        }
        else
        {
            max = std::max(max, it->sample);
            ++it;
        }
    }
    m_maxBytesInFlight = max;
}

void ScreamCongestionController::UpdateMediaRate(std::uint32_t frameSize)
{
    m_bytesMedia += frameSize;
    double currentTime = VoIPController::GetCurrentTime();
    if (currentTime - m_rateMediaUpdateTime >= 0.5)
    {
        m_rateMedia = static_cast<float>((m_bytesMedia * 8) / (currentTime - m_rateMediaUpdateTime));
        m_bytesMedia = 0;
        m_rateMediaUpdateTime = currentTime;
        LOGV("rateMedia %f", static_cast<double>(m_rateMedia));
        m_rateMediaHistory.Add(m_rateMedia);
        m_rateMediaMedian = m_rateMediaHistory.NonZeroAverage();
    }
}

std::uint32_t ScreamCongestionController::GetBitrate()
{
    return m_targetBitrate;
}
