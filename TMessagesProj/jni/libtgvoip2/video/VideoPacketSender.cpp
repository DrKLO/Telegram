//
// Created by Grishka on 19/03/2019.
//

#include <algorithm>
#include "VideoPacketSender.h"
#include "VideoFEC.h"
#include "../logging.h"
#include "../PrivateDefines.h"

using namespace tgvoip;
using namespace tgvoip::video;

VideoPacketSender::VideoPacketSender(VoIPController* controller, VideoSource* videoSource, std::shared_ptr<VoIPController::Stream> stream) : PacketSender(controller), stm(stream){
	SetSource(videoSource);
}

VideoPacketSender::~VideoPacketSender(){
}

void VideoPacketSender::PacketAcknowledged(uint32_t seq, double sendTime, double ackTime, uint8_t type, uint32_t size){
	uint32_t bytesNewlyAcked=0;
	//if(!videoKeyframeRequested){
		// video frames are stored in sentVideoFrames in order of increasing numbers
		// so if a frame (or part of it) is acknowledged but isn't sentVideoFrames[0], we know there was a packet loss
		for(SentVideoFrame& f:sentVideoFrames){
            for(std::vector<uint32_t>::iterator s=f.unacknowledgedPackets.begin(); s!=f.unacknowledgedPackets.end();){
				//RecentOutgoingPacket* opkt=GetRecentOutgoingPacket(*s);
				if(/*opkt && opkt->ackTime!=0.0*/*s==seq){
					s=f.unacknowledgedPackets.erase(s);
					//newlyAckedVideoBytes+=opkt->size;
                    bytesNewlyAcked=size;
                    break;
				}else{
					++s;
				}
			}
		}
        for(std::vector<SentVideoFrame>::iterator f=sentVideoFrames.begin();f!=sentVideoFrames.end();){
			if(f->unacknowledgedPackets.empty() && f->fragmentsInQueue==0){
					f=sentVideoFrames.erase(f);
					continue;
			}
			++f;
		}
	//}
	if(bytesNewlyAcked){
		float _sendTime=(float)(sendTime-GetConnectionInitTime());
		float recvTime=(float)ackTime;
		float oneWayDelay=recvTime-_sendTime;
		//LOGV("one-way delay: %f", oneWayDelay);
		videoCongestionControl.ProcessAcks(oneWayDelay, bytesNewlyAcked, videoPacketLossCount, RTTHistory().Average(5));
	}
    //videoCongestionControl.GetPacingInterval();
}

void VideoPacketSender::PacketLost(uint32_t seq, uint8_t type, uint32_t size){
	if(type==PKT_STREAM_EC)
		return;
	LOGW("VideoPacketSender::PacketLost: %u (size %u)", seq, size);
	//LOGI("frame count %u", (unsigned int)sentVideoFrames.size());
	for(std::vector<SentVideoFrame>::iterator f=sentVideoFrames.begin();f!=sentVideoFrames.end();++f){
		std::vector<uint32_t>::iterator pkt=std::find(f->unacknowledgedPackets.begin(), f->unacknowledgedPackets.end(), seq);
		if(pkt!=f->unacknowledgedPackets.end()){
			LOGW("Lost packet belongs to frame %u", f->seq);
			videoPacketLossCount++;
			videoCongestionControl.ProcessPacketLost(size);
			if(!videoKeyframeRequested){
				videoKeyframeRequested=true;
				source->RequestKeyFrame();
			}
			f->unacknowledgedPackets.erase(pkt);
			if(f->unacknowledgedPackets.empty() && f->fragmentsInQueue==0){
				sentVideoFrames.erase(f);
			}
			return;
		}
	}
	//abort();
}

void VideoPacketSender::SetSource(VideoSource *source){
	if(this->source==source)
		return;

	if(this->source){
		this->source->Stop();
		this->source->SetCallback(nullptr);
		//delete this->source;
	}

	this->source=source;

	if(!source)
		return;

	sourceChangeTime=lastVideoResolutionChangeTime=VoIPController::GetCurrentTime();
	uint32_t bitrate=videoCongestionControl.GetBitrate();
	currentVideoBitrate=bitrate;
	source->SetBitrate(bitrate);
	source->Reset(stm->codec, stm->resolution=GetVideoResolutionForCurrentBitrate());
	source->Start();
    source->SetCallback(std::bind(&VideoPacketSender::SendFrame, this, std::placeholders::_1, std::placeholders::_2, std::placeholders::_3));
	source->SetStreamStateCallback([this](bool paused){
		stm->paused=paused;
		GetMessageThread().Post([this]{
			SendStreamFlags(*stm);
		});
	});
}

void VideoPacketSender::SendFrame(const Buffer &_frame, uint32_t flags, uint32_t rotation){
	std::shared_ptr<Buffer> framePtr=std::make_shared<Buffer>(Buffer::CopyOf(_frame));
	GetMessageThread().Post([this, framePtr, flags, rotation]{
		const Buffer& frame=*framePtr;

		double currentTime=VoIPController::GetCurrentTime();

		if(firstVideoFrameTime==0.0)
			firstVideoFrameTime=currentTime;

		videoCongestionControl.UpdateMediaRate(static_cast<uint32_t>(frame.Length()));
		uint32_t bitrate=videoCongestionControl.GetBitrate();
		if(bitrate!=currentVideoBitrate){
			currentVideoBitrate=bitrate;
			LOGD("Setting video bitrate to %u", bitrate);
			source->SetBitrate(bitrate);
		}
		int resolutionFromBitrate=GetVideoResolutionForCurrentBitrate();
		if(resolutionFromBitrate!=stm->resolution && currentTime-lastVideoResolutionChangeTime>3.0 && currentTime-sourceChangeTime>10.0){
			LOGI("Changing video resolution: %d -> %d", stm->resolution, resolutionFromBitrate);
			stm->resolution=resolutionFromBitrate;
			GetMessageThread().Post([this, resolutionFromBitrate]{
				source->Reset(stm->codec, resolutionFromBitrate);
				stm->csdIsValid=false;
			});
			lastVideoResolutionChangeTime=currentTime;
			return;
		}

		if(videoKeyframeRequested){
			if(flags & VIDEO_FRAME_FLAG_KEYFRAME){
				videoKeyframeRequested=false;
			}else{
				LOGV("Dropping input video frame waiting for key frame");
				//return;
			}
		}

		uint32_t pts=videoFrameCount++;
		bool csdInvalidated=!stm->csdIsValid;
		if(!stm->csdIsValid){
            std::vector<Buffer> &csd=source->GetCodecSpecificData();
			stm->codecSpecificData.clear();
			for(Buffer &b:csd){
				stm->codecSpecificData.push_back(Buffer::CopyOf(b));
			}
			stm->csdIsValid=true;
			stm->width=source->GetFrameWidth();
			stm->height=source->GetFrameHeight();
			//SendStreamCSD();
		}

		Buffer csd;
		if(flags & VIDEO_FRAME_FLAG_KEYFRAME){
			BufferOutputStream os(256);
			os.WriteInt16((int16_t) stm->width);
			os.WriteInt16((int16_t) stm->height);
			unsigned char sizeAndFlag=(unsigned char) stm->codecSpecificData.size();
			if(csdInvalidated)
				sizeAndFlag|=0x80;
			os.WriteByte(sizeAndFlag);
			for(Buffer &b:stm->codecSpecificData){
				assert(b.Length()<255);
				os.WriteByte(static_cast<unsigned char>(b.Length()));
				os.WriteBytes(b);
			}
			csd=std::move(os);
		}

		frameSeq++;
		size_t totalLength=csd.Length()+frame.Length();
		size_t segmentCount=totalLength/1024;
		if(totalLength%1024>0)
			segmentCount++;
		SentVideoFrame sentFrame;
		sentFrame.seq=frameSeq;
		sentFrame.fragmentCount=static_cast<uint32_t>(segmentCount);
		sentFrame.fragmentsInQueue=0;//static_cast<uint32_t>(segmentCount);
		size_t offset=0;
		size_t packetSize=totalLength/segmentCount;
		for(size_t seg=0; seg<segmentCount; seg++){
			BufferOutputStream pkt(1500);
			size_t len;//=std::min((size_t)1024, frame.Length()-offset);
			if(seg==segmentCount-1){
				len=frame.Length()-offset;
			}else{
				len=packetSize;
			}
			unsigned char pflags=STREAM_DATA_FLAG_LEN16;
			//pflags |= STREAM_DATA_FLAG_HAS_MORE_FLAGS;
			pkt.WriteByte((unsigned char) (stm->id | pflags)); // streamID + flags
			int16_t lengthAndFlags=static_cast<int16_t>(len & 0x7FF);
			if(segmentCount>1)
				lengthAndFlags|=STREAM_DATA_XFLAG_FRAGMENTED;
			if(flags & VIDEO_FRAME_FLAG_KEYFRAME)
				lengthAndFlags|=STREAM_DATA_XFLAG_KEYFRAME;
			pkt.WriteInt16(lengthAndFlags);
			//pkt.WriteInt32(audioTimestampOut);
			pkt.WriteInt32(pts);
			if(segmentCount>1){
				pkt.WriteByte((unsigned char) seg);
				pkt.WriteByte((unsigned char) segmentCount);
			}
			pkt.WriteByte((uint8_t) frameSeq);
			size_t dataOffset=pkt.GetLength();
			if(seg==0){
				unsigned char _rotation;
				switch(rotation){
					case 90:
						_rotation=VIDEO_ROTATION_90;
						break;
					case 180:
						_rotation=VIDEO_ROTATION_180;
						break;
					case 270:
						_rotation=VIDEO_ROTATION_270;
						break;
					case 0:
					default:
						_rotation=VIDEO_ROTATION_0;
						break;
				}
				pkt.WriteByte(_rotation);
				dataOffset++;

				if(!csd.IsEmpty()){
					pkt.WriteBytes(csd);
					len-=csd.Length();
				}
			}
			pkt.WriteBytes(frame, offset, len);
			//LOGV("Sending segment %u of %u, length %u", (unsigned int)seg, (unsigned int)segmentCount, (unsigned int)pkt.GetLength());

			Buffer fecPacketData(pkt.GetLength()-dataOffset);
			fecPacketData.CopyFrom(pkt.GetBuffer()+dataOffset, 0, pkt.GetLength()-dataOffset);
			packetsForFEC.push_back(std::move(fecPacketData));
			offset+=len;

			Buffer packetData(std::move(pkt));

			VoIPController::PendingOutgoingPacket p{
					/*.seq=*/0,
					/*.type=*/PKT_STREAM_DATA,
					/*.len=*/packetData.Length(),
					/*.data=*/std::move(packetData),
					/*.endpoint=*/0,
			};
			IncrementUnsentStreamPackets();
			uint32_t seq=SendPacket(std::move(p));
			videoCongestionControl.ProcessPacketSent(static_cast<unsigned int>(pkt.GetLength()));
			sentFrame.unacknowledgedPackets.push_back(seq);
			//packetQueue.Put(QueuedPacket{std::move(p), sentFrame.seq});
		}
		fecFrameCount++;
		if(fecFrameCount>=3){
			Buffer fecPacket=ParityFEC::Encode(packetsForFEC);

			packetsForFEC.clear();
			fecFrameCount=0;
			LOGV("FEC packet length: %u", (unsigned int) fecPacket.Length());
			BufferOutputStream out(1500);
			out.WriteByte(stm->id);
			out.WriteByte((uint8_t) frameSeq);
			out.WriteByte(FEC_SCHEME_XOR);
			out.WriteByte(3);
			out.WriteInt16((int16_t) fecPacket.Length());
			out.WriteBytes(fecPacket);

			VoIPController::PendingOutgoingPacket p{
					0,
					PKT_STREAM_EC,
					out.GetLength(),
					Buffer(std::move(out)),
					0
			};
			uint32_t seq=SendPacket(std::move(p));
		}
		sentVideoFrames.push_back(sentFrame);
	});
}

int VideoPacketSender::GetVideoResolutionForCurrentBitrate(){

	int peerMaxVideoResolution=GetProtocolInfo().maxVideoResolution;
	int resolutionFromBitrate=INIT_VIDEO_RES_1080;
	if(VoIPController::GetCurrentTime()-sourceChangeTime>10.0){
		// TODO: probably move this to server config
		if(stm->codec==CODEC_AVC || stm->codec==CODEC_VP8){
			if(currentVideoBitrate>400000){
				resolutionFromBitrate=INIT_VIDEO_RES_720;
			}else if(currentVideoBitrate>250000){
				resolutionFromBitrate=INIT_VIDEO_RES_480;
			}else{
				resolutionFromBitrate=INIT_VIDEO_RES_360;
			}
		}else if(stm->codec==CODEC_HEVC || stm->codec==CODEC_VP9){
			if(currentVideoBitrate>400000){
				resolutionFromBitrate=INIT_VIDEO_RES_1080;
			}else if(currentVideoBitrate>250000){
				resolutionFromBitrate=INIT_VIDEO_RES_720;
			}else if(currentVideoBitrate>100000){
				resolutionFromBitrate=INIT_VIDEO_RES_480;
			}else{
				resolutionFromBitrate=INIT_VIDEO_RES_360;
			}
		}
	}else{
		if(stm->codec==CODEC_AVC || stm->codec==CODEC_VP8)
			resolutionFromBitrate=INIT_VIDEO_RES_720;
		else if(stm->codec==CODEC_HEVC || stm->codec==CODEC_VP9)
			resolutionFromBitrate=INIT_VIDEO_RES_1080;
	}
	return std::min(peerMaxVideoResolution, resolutionFromBitrate);
}
