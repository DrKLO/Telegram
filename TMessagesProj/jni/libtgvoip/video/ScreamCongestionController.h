//
// Created by Grishka on 25/02/2019.
//

#ifndef LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H
#define LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H

#include "../Buffers.h"
#include <vector>
#include <stdint.h>

namespace tgvoip{
	namespace video{
		class ScreamCongestionController{
		public:
			ScreamCongestionController();
			void AdjustBitrate();
			void ProcessAcks(float oneWayDelay, uint32_t bytesNewlyAcked, uint32_t lossCount, double rtt);
			void ProcessPacketSent(uint32_t size);
			void ProcessPacketLost(uint32_t size);
			double GetPacingInterval();
			void UpdateMediaRate(uint32_t frameSize);
			uint32_t GetBitrate();

		private:
			void UpdateVariables(float qdelay);
			void UpdateCWnd(float qdelay);
			void AdjustQDelayTarget(float qdelay);
			void CalculateSendWindow(float qdelay);

			void UpdateBytesInFlightHistory();

			struct ValueSample{
				uint32_t sample;
				double time;
			};

			float qdelayTarget;
			float qdelayFractionAvg=0.0f;
			HistoricBuffer<float, 20> qdelayFractionHist;
			float qdelayTrend=0.0f;
			float qdelayTrendMem=0.0f;
			HistoricBuffer<float, 100> qdelayNormHist;
			bool inFastIncrease=true;
			uint32_t cwnd;
			uint32_t bytesNewlyAcked=0;
			uint32_t maxBytesInFlight=0;
			uint32_t sendWnd=0;
			uint32_t targetBitrate=0;
			uint32_t targetBitrateLastMax=1;
			float rateTransmit=0.0f;
			float rateAck=0.0f;
			float rateMedia=0.0f;
			float rateMediaMedian=0.0f;
			float sRTT=0.0f;
			uint32_t rtpQueueSize=0;
			uint32_t rtpSize=1024; //0;
			float lossEventRate=0.0f;

			bool lossPending=false;
			float prevOneWayDelay=0.0f;
			double ignoreLossesUntil=0.0;
			uint32_t prevLossCount=0;
			double lastTimeQDelayTrendWasGreaterThanLo=0.0;
			double lastVariablesUpdateTime=0.0;
			double lastRateAdjustmentTime=0.0;
			double lastCWndUpdateTime=0.0;
			uint32_t bytesInFlight=0;
			std::vector<ValueSample> bytesInFlightHistory;
			uint32_t bytesSent=0;
			uint32_t bytesAcked=0;
			uint32_t bytesMedia=0;
			double rateTransmitUpdateTime=0.0;
			double rateMediaUpdateTime=0.0;
			HistoricBuffer<float, 25> rateMediaHistory;
		};
	}
}

#endif //LIBTGVOIP_SCREAMCONGESTIONCONTROLLER_H
