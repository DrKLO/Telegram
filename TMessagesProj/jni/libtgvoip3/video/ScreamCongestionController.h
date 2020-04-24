//
// Created by Grishka on 25/02/2019.
//

#ifndef LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H
#define LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H

#include "../Buffers.h"

#include <cstdint>
#include <vector>

namespace tgvoip
{

namespace video
{

class ScreamCongestionController
{
public:
    ScreamCongestionController();
    void AdjustBitrate();
    void ProcessAcks(float oneWayDelay, std::uint32_t m_bytesNewlyAcked, std::uint32_t lossCount, double rtt);
    void ProcessPacketSent(std::uint32_t size);
    void ProcessPacketLost(std::uint32_t size);
    double GetPacingInterval();
    void UpdateMediaRate(std::uint32_t frameSize);
    std::uint32_t GetBitrate();

private:
    struct ValueSample
    {
        std::uint32_t sample;
        double time;
    };

    std::vector<ValueSample> m_bytesInFlightHistory;
    HistoricBuffer<float, 20> m_qdelayFractionHist;
    HistoricBuffer<float, 100> m_qdelayNormHist;
    HistoricBuffer<float, 25> m_rateMediaHistory;

    double m_ignoreLossesUntil = 0.0;
    double m_lastTimeQDelayTrendWasGreaterThanLo = 0.0;
    double m_lastVariablesUpdateTime = 0.0;
    double m_lastRateAdjustmentTime = 0.0;
    double m_lastCWndUpdateTime = 0.0;
    double m_rateTransmitUpdateTime = 0.0;
    double m_rateMediaUpdateTime = 0.0;

    float m_qdelayTarget;
    float m_qdelayFractionAvg = 0.0f;
    float m_qdelayTrend = 0.0f;
    float m_qdelayTrendMem = 0.0f;
    float m_rateTransmit = 0.0f;
    float m_rateAck = 0.0f;
    float m_rateMedia = 0.0f;
    float m_rateMediaMedian = 0.0f;
    float m_sRTT = 0.0f;
    float m_lossEventRate = 0.0f;
    float m_prevOneWayDelay = 0.0f;

    std::uint32_t m_cwnd;
    std::uint32_t m_bytesNewlyAcked = 0;
    std::uint32_t m_maxBytesInFlight = 0;
    std::uint32_t m_sendWnd = 0;
    std::uint32_t m_targetBitrate = 0;
    std::uint32_t m_targetBitrateLastMax = 1;

    std::uint32_t m_rtpQueueSize = 0;
    std::uint32_t m_rtpSize = 1024; //0;
    std::uint32_t m_prevLossCount = 0;
    std::uint32_t m_bytesInFlight = 0;
    std::uint32_t m_bytesSent = 0;
    std::uint32_t m_bytesAcked = 0;
    std::uint32_t m_bytesMedia = 0;

    bool m_lossPending = false;
    bool m_inFastIncrease = true;

    void UpdateVariables(float qdelay);
    void UpdateCWnd(float qdelay);
    void AdjustQDelayTarget(float qdelay);
    void CalculateSendWindow(float qdelay);
    void UpdateBytesInFlightHistory();
};

} // namespace video

} // namespace tgvoip

#endif // LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H
