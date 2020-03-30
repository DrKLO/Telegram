//
// Created by Grishka on 19/03/2019.
//

#ifndef LIBTGVOIP_VIDEOPACKETSENDER_H
#define LIBTGVOIP_VIDEOPACKETSENDER_H

#include "../PacketSender.h"
#include "../Buffers.h"
#include "../threading.h"
#include <memory>
#include <stdint.h>
#include <vector>

namespace tgvoip{
	namespace video{
		class VideoSource;

		class VideoPacketSender : public PacketSender{
		public:
			VideoPacketSender(VoIPController* controller, VideoSource* videoSource, std::shared_ptr<VoIPController::Stream> stream);
			virtual ~VideoPacketSender();
			virtual void PacketAcknowledged(uint32_t seq, double sendTime, double ackTime, uint8_t type, uint32_t size) override;
			virtual void PacketLost(uint32_t seq, uint8_t type, uint32_t size) override;
			void SetSource(VideoSource* source);

			uint32_t GetBitrate(){
				return currentVideoBitrate;
			}

		private:
			struct SentVideoFrame{
				uint32_t seq;
				uint32_t fragmentCount;
				std::vector<uint32_t> unacknowledgedPackets;
				uint32_t fragmentsInQueue;
			};
			struct QueuedPacket{
				VoIPController::PendingOutgoingPacket packet;
                uint32_t seq;
			};

			void SendFrame(const Buffer& frame, uint32_t flags, uint32_t rotation);
			int GetVideoResolutionForCurrentBitrate();

			VideoSource* source=NULL;
			std::shared_ptr<VoIPController::Stream> stm;
			video::ScreamCongestionController videoCongestionControl;
			double firstVideoFrameTime=0.0;
			uint32_t videoFrameCount=0;
			std::vector<SentVideoFrame> sentVideoFrames;
			bool videoKeyframeRequested=false;
			uint32_t sendVideoPacketID=MessageThread::INVALID_ID;
			uint32_t videoPacketLossCount=0;
			uint32_t currentVideoBitrate=0;
			double lastVideoResolutionChangeTime=0.0;
			double sourceChangeTime=0.0;

			std::vector<Buffer> packetsForFEC;
			size_t fecFrameCount=0;
			uint32_t frameSeq=0;
		};
	}
}

#endif //LIBTGVOIP_VIDEOPACKETSENDER_H
