//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_CONGESTIONCONTROL_H
#define LIBTGVOIP_CONGESTIONCONTROL_H

#include <stdlib.h>
#include <stdint.h>
#include "threading.h"
#include "Buffers.h"

#define TGVOIP_CONCTL_ACT_INCREASE 1
#define TGVOIP_CONCTL_ACT_DECREASE 2
#define TGVOIP_CONCTL_ACT_NONE 0

namespace tgvoip{

struct tgvoip_congestionctl_packet_t{
	uint32_t seq;
	double sendTime;
	size_t size;
};
typedef struct tgvoip_congestionctl_packet_t tgvoip_congestionctl_packet_t;

class CongestionControl{
public:
	CongestionControl();
	~CongestionControl();

	void PacketSent(uint32_t seq, size_t size);
	void PacketLost(uint32_t seq);
	void PacketAcknowledged(uint32_t seq);

	double GetAverageRTT();
	double GetMinimumRTT();
	size_t GetInflightDataSize();
	size_t GetCongestionWindow();
	size_t GetAcknowledgedDataSize();
	void Tick();
	int GetBandwidthControlAction();
	uint32_t GetSendLossCount();

private:
	HistoricBuffer<double, 100> rttHistory;
	HistoricBuffer<size_t, 30> inflightHistory;
	tgvoip_congestionctl_packet_t inflightPackets[100];
	uint32_t lossCount;
	double tmpRtt;
	double lastActionTime;
	double lastActionRtt;
	double stateTransitionTime;
	int tmpRttCount;
	uint32_t lastSentSeq;
	uint32_t tickCount;
	size_t inflightDataSize;
	size_t cwnd;
};
}

#endif //LIBTGVOIP_CONGESTIONCONTROL_H
