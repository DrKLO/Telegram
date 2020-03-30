//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "VoIPController.h"
#include "logging.h"
#include "VoIPServerConfig.h"
#include "PrivateDefines.h"
#include <assert.h>
#include <math.h>
#include <time.h>

using namespace tgvoip;
using namespace std;

VoIPGroupController::VoIPGroupController(int32_t timeDifference){
	audioMixer=new AudioMixer();
	memset(&callbacks, 0, sizeof(callbacks));
	userSelfID=0;
	this->timeDifference=timeDifference;
	LOGV("Created VoIPGroupController; timeDifference=%d", timeDifference);
}

VoIPGroupController::~VoIPGroupController(){
	if(audioOutput){
		audioOutput->Stop();
	}
	LOGD("before stop audio mixer");
	audioMixer->Stop();
	delete audioMixer;

	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();p++){
		if(p->levelMeter)
			delete p->levelMeter;
	}
}

void VoIPGroupController::SetGroupCallInfo(unsigned char *encryptionKey, unsigned char *reflectorGroupTag, unsigned char *reflectorSelfTag, unsigned char *reflectorSelfSecret, unsigned char* reflectorSelfTagHash, int32_t selfUserID, NetworkAddress reflectorAddress, NetworkAddress reflectorAddressV6, uint16_t reflectorPort){
	Endpoint e;
	e.address=reflectorAddress;
	e.v6address=reflectorAddressV6;
	e.port=reflectorPort;
	memcpy(e.peerTag, reflectorGroupTag, 16);
	e.type=Endpoint::Type::UDP_RELAY;
	e.id=FOURCC('G','R','P','R');
	endpoints[e.id]=e;
	groupReflector=e;
	currentEndpoint=e.id;

	memcpy(this->encryptionKey, encryptionKey, 256);
	memcpy(this->reflectorSelfTag, reflectorSelfTag, 16);
	memcpy(this->reflectorSelfSecret, reflectorSelfSecret, 16);
	memcpy(this->reflectorSelfTagHash, reflectorSelfTagHash, 16);
	uint8_t sha256[SHA256_LENGTH];
	crypto.sha256((uint8_t*) encryptionKey, 256, sha256);
	memcpy(callID, sha256+(SHA256_LENGTH-16), 16);
	memcpy(keyFingerprint, sha256+(SHA256_LENGTH-16), 8);
	this->userSelfID=selfUserID;

	//LOGD("reflectorSelfTag = %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X", reflectorSelfTag[0], reflectorSelfTag[1], reflectorSelfTag[2], reflectorSelfTag[3], reflectorSelfTag[4], reflectorSelfTag[5], reflectorSelfTag[6], reflectorSelfTag[7], reflectorSelfTag[8], reflectorSelfTag[9], reflectorSelfTag[10], reflectorSelfTag[11], reflectorSelfTag[12], reflectorSelfTag[13], reflectorSelfTag[14], reflectorSelfTag[15]);
	//LOGD("reflectorSelfSecret = %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X", reflectorSelfSecret[0], reflectorSelfSecret[1], reflectorSelfSecret[2], reflectorSelfSecret[3], reflectorSelfSecret[4], reflectorSelfSecret[5], reflectorSelfSecret[6], reflectorSelfSecret[7], reflectorSelfSecret[8], reflectorSelfSecret[9], reflectorSelfSecret[10], reflectorSelfSecret[11], reflectorSelfSecret[12], reflectorSelfSecret[13], reflectorSelfSecret[14], reflectorSelfSecret[15]);
	//LOGD("reflectorSelfTagHash = %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X %02X", reflectorSelfTagHash[0], reflectorSelfTagHash[1], reflectorSelfTagHash[2], reflectorSelfTagHash[3], reflectorSelfTagHash[4], reflectorSelfTagHash[5], reflectorSelfTagHash[6], reflectorSelfTagHash[7], reflectorSelfTagHash[8], reflectorSelfTagHash[9], reflectorSelfTagHash[10], reflectorSelfTagHash[11], reflectorSelfTagHash[12], reflectorSelfTagHash[13], reflectorSelfTagHash[14], reflectorSelfTagHash[15]);
}

void VoIPGroupController::AddGroupCallParticipant(int32_t userID, unsigned char *memberTagHash, unsigned char* serializedStreams, size_t streamsLength){
	if(userID==userSelfID)
		return;
	if(userSelfID==0)
		return;
	//if(streamsLength==0)
	//	return;
	MutexGuard m(participantsMutex);
	LOGV("Adding group call user %d, streams length %u", userID, (unsigned int)streamsLength);

	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		if(p->userID==userID){
			LOGE("user %d already added", userID);
			abort();
			break;
		}
	}

	GroupCallParticipant p;
	p.userID=userID;
	memcpy(p.memberTagHash, memberTagHash, sizeof(p.memberTagHash));
	p.levelMeter=new AudioLevelMeter();

	BufferInputStream ss(serializedStreams, streamsLength);
	vector<shared_ptr<Stream>> streams=DeserializeStreams(ss);

	unsigned char audioStreamID=0;

	for(vector<shared_ptr<Stream>>::iterator _s=streams.begin();_s!=streams.end();++_s){
		shared_ptr<Stream>& s=*_s;
		s->userID=userID;
		if(s->type==STREAM_TYPE_AUDIO && s->codec==CODEC_OPUS && !audioStreamID){
			audioStreamID=s->id;
			s->jitterBuffer=make_shared<JitterBuffer>(nullptr, s->frameDuration);
			if(s->frameDuration>50)
				s->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_60", 2));
			else if(s->frameDuration>30)
				s->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_40", 4));
			else
				s->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_20", 6));
			s->callbackWrapper=make_shared<CallbackWrapper>();
			s->decoder=make_shared<OpusDecoder>(s->callbackWrapper, false, false);
			s->decoder->SetJitterBuffer(s->jitterBuffer);
			s->decoder->SetFrameDuration(s->frameDuration);
			s->decoder->SetDTX(true);
			s->decoder->SetLevelMeter(p.levelMeter);
			audioMixer->AddInput(s->callbackWrapper);
		}
		incomingStreams.push_back(s);
	}

	if(!audioStreamID){
		LOGW("User %d has no usable audio stream", userID);
	}

	p.streams.insert(p.streams.end(), streams.begin(), streams.end());
	participants.push_back(p);
	LOGI("Added group call participant %d", userID);
}

void VoIPGroupController::RemoveGroupCallParticipant(int32_t userID){
	MutexGuard m(participantsMutex);
	vector<shared_ptr<Stream>>::iterator stm=incomingStreams.begin();
	while(stm!=incomingStreams.end()){
		if((*stm)->userID==userID){
			LOGI("Removed stream %d belonging to user %d", (*stm)->id, userID);
			audioMixer->RemoveInput((*stm)->callbackWrapper);
			(*stm)->decoder->Stop();
			//delete (*stm)->decoder;
			//delete (*stm)->jitterBuffer;
			//delete (*stm)->callbackWrapper;
			stm=incomingStreams.erase(stm);
			continue;
		}
		++stm;
	}
	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		if(p->userID==userID){
			if(p->levelMeter)
				delete p->levelMeter;
			participants.erase(p);
			LOGI("Removed group call participant %d", userID);
			break;
		}
	}
}

vector<shared_ptr<VoIPController::Stream>> VoIPGroupController::DeserializeStreams(BufferInputStream& in){
	vector<shared_ptr<Stream>> res;
	try{
		unsigned char count=in.ReadByte();
		for(unsigned char i=0;i<count;i++){
			uint16_t len=(uint16_t) in.ReadInt16();
			BufferInputStream inner=in.GetPartBuffer(len, true);
			shared_ptr<Stream> s=make_shared<Stream>();
			s->id=inner.ReadByte();
			s->type=inner.ReadByte();
			s->codec=(uint32_t) inner.ReadInt32();
			uint32_t flags=(uint32_t) inner.ReadInt32();
			s->enabled=(flags & STREAM_FLAG_ENABLED)==STREAM_FLAG_ENABLED;
			s->frameDuration=(uint16_t) inner.ReadInt16();
			res.push_back(s);
		}
	}catch(out_of_range& x){
		LOGW("Error deserializing streams: %s", x.what());
	}
	return res;
}

void VoIPGroupController::SetParticipantStreams(int32_t userID, unsigned char *serializedStreams, size_t length){
	LOGD("Set participant streams for %d", userID);
	MutexGuard m(participantsMutex);
	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		if(p->userID==userID){
			BufferInputStream in(serializedStreams, length);
			vector<shared_ptr<Stream>> streams=DeserializeStreams(in);
			for(vector<shared_ptr<Stream>>::iterator ns=streams.begin();ns!=streams.end();++ns){
				bool found=false;
				for(vector<shared_ptr<Stream>>::iterator s=p->streams.begin();s!=p->streams.end();++s){
					if((*s)->id==(*ns)->id){
						(*s)->enabled=(*ns)->enabled;
						if(groupCallbacks.participantAudioStateChanged)
							groupCallbacks.participantAudioStateChanged(this, userID, (*s)->enabled);
						found=true;
						break;
					}
				}
				if(!found){
					LOGW("Tried to add stream %d for user %d but adding/removing streams is not supported", (*ns)->id, userID);
				}
			}
			break;
		}
	}
}

size_t VoIPGroupController::GetInitialStreams(unsigned char *buf, size_t size){
	BufferOutputStream s(buf, size);
	s.WriteByte(1); // streams count

	s.WriteInt16(12); // this object length
	s.WriteByte(1); // stream id
	s.WriteByte(STREAM_TYPE_AUDIO);
	s.WriteInt32(CODEC_OPUS);
	s.WriteInt32(STREAM_FLAG_ENABLED | STREAM_FLAG_DTX); // flags
	s.WriteInt16(60); // frame duration

	return s.GetLength();
}

void VoIPGroupController::SendInit(){
	SendRecentPacketsRequest();
}

void VoIPGroupController::ProcessIncomingPacket(NetworkPacket &packet, Endpoint& srcEndpoint){
	//LOGD("Received incoming packet from %s:%u, %u bytes", packet.address->ToString().c_str(), packet.port, packet.length);
	/*if(packet.length<17 || packet.length>2000){
		LOGW("Received packet has wrong length %d", (int)packet.length);
		return;
	}
	BufferOutputStream sigData(packet.length);
	sigData.WriteBytes(packet.data, packet.length-16);
	sigData.WriteBytes(reflectorSelfSecret, 16);
	unsigned char sig[32];
	crypto.sha256(sigData.GetBuffer(), sigData.GetLength(), sig);
	if(memcmp(sig, packet.data+(packet.length-16), 16)!=0){
		LOGW("Received packet has incorrect signature");
		return;
	}

	// reflector special response
	if(memcmp(packet.data, reflectorSelfTagHash, 16)==0 && packet.length>60){
		//LOGI("possible reflector special response");
		unsigned char firstBlock[16];
		unsigned char iv[16];
		memcpy(iv, packet.data+16, 16);
		unsigned char key[32];
		crypto.sha256(reflectorSelfSecret, 16, key);
		crypto.aes_cbc_decrypt(packet.data+32, firstBlock, 16, key, iv);
		BufferInputStream in(firstBlock, 16);
		in.Seek(8);
		size_t len=(size_t) in.ReadInt32();
		int32_t tlid=in.ReadInt32();
		//LOGD("special response: len=%d, tlid=0x%08X", len, tlid);
		if(len%4==0 && len+60<=packet.length && packet.length<=1500){
			lastRecvPacketTime=GetCurrentTime();
			memcpy(iv, packet.data+16, 16);
			unsigned char buf[1500];
			crypto.aes_cbc_decrypt(packet.data+32, buf, len+16, key, iv);
			try{
				if(tlid==TLID_UDP_REFLECTOR_LAST_PACKETS_INFO){
					MutexGuard m(sentPacketsMutex);
					//LOGV("received udpReflector.lastPacketsInfo");
					in=BufferInputStream(buf, len+16);
					in.Seek(16);
					/*int32_t date=* /in.ReadInt32();
					/*int64_t queryID=* /in.ReadInt64();
					int32_t vectorMagic=in.ReadInt32();
					if(vectorMagic!=TLID_VECTOR){
						LOGW("last packets info: expected vector, got %08X", vectorMagic);
						return;
					}
					int32_t recvCount=in.ReadInt32();
					//LOGV("%d received packets", recvCount);
					for(int i=0;i<recvCount;i++){
						uint32_t p=(uint32_t) in.ReadInt32();
						//LOGV("Relay received packet: %08X", p);
						uint16_t id=(uint16_t) (p & 0xFFFF);
						//LOGV("ack id %04X", id);
						for(vector<PacketIdMapping>::iterator pkt=recentSentPackets.begin();pkt!=recentSentPackets.end();++pkt){
							//LOGV("== sent id %04X", pkt->id);
							if(pkt->id==id){
								if(!pkt->ackTime){
									pkt->ackTime=GetCurrentTime();
									conctl->PacketAcknowledged(pkt->seq);
									//LOGV("relay acknowledged packet %u", pkt->seq);
									if(seqgt(pkt->seq, lastRemoteAckSeq))
										lastRemoteAckSeq=pkt->seq;
								}
								break;
							}
						}
					}
					vectorMagic=in.ReadInt32();
					if(vectorMagic!=TLID_VECTOR){
						LOGW("last packets info: expected vector, got %08X", vectorMagic);
						return;
					}
					int32_t sentCount=in.ReadInt32();
					//LOGV("%d sent packets", sentCount);
					for(int i=0;i<sentCount;i++){
						/*int32_t p=* /in.ReadInt32();
						//LOGV("Sent packet: %08X", p);
					}
					if(udpConnectivityState!=UDP_AVAILABLE)
						udpConnectivityState=UDP_AVAILABLE;
					if(state!=STATE_ESTABLISHED)
						SetState(STATE_ESTABLISHED);
					if(!audioInput){
						InitializeAudio();
						if(state!=STATE_FAILED){
							//	audioOutput->Start();
						}
					}
				}
			}catch(out_of_range& x){
				LOGE("Error parsing special response: %s", x.what());
			}
			return;
		}
	}

	if(packet.length<32)
		return;

	// it's a packet relayed from another participant - find the sender
	MutexGuard m(participantsMutex);
	GroupCallParticipant* sender=NULL;
	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		if(memcmp(packet.data, p->memberTagHash, 16)==0){
			//LOGV("received data packet from user %d", p->userID);
			sender=&*p;
			break;
		}
	}
	if(!sender){
		LOGV("Received data packet is from unknown user");
		return;
	}

	if(memcmp(packet.data+16, keyFingerprint, 8)!=0){
		LOGW("received packet has wrong key fingerprint");
		return;
	}

	BufferInputStream in(packet.data, packet.length-16);
	in.Seek(16+8); // peer tag + key fingerprint

	unsigned char msgKey[16];
	in.ReadBytes(msgKey, 16);

	unsigned char decrypted[1500];
	unsigned char aesKey[32], aesIv[32];
	KDF2(msgKey, 0, aesKey, aesIv);
	size_t decryptedLen=in.Remaining()-16;
	if(decryptedLen>sizeof(decrypted))
		return;
	//LOGV("-> MSG KEY: %08x %08x %08x %08x, hashed %u", *reinterpret_cast<int32_t*>(msgKey), *reinterpret_cast<int32_t*>(msgKey+4), *reinterpret_cast<int32_t*>(msgKey+8), *reinterpret_cast<int32_t*>(msgKey+12), decryptedLen-4);
	uint8_t *decryptOffset = packet.data + in.GetOffset();
	if ((((intptr_t)decryptOffset) % sizeof(long)) != 0) {
		LOGE("alignment2 packet.data+in.GetOffset()");
	}
	if (decryptedLen % sizeof(long) != 0) {
		LOGE("alignment2 decryptedLen");
	}
	crypto.aes_ige_decrypt(packet.data+in.GetOffset(), decrypted, decryptedLen, aesKey, aesIv);

	in=BufferInputStream(decrypted, decryptedLen);
	//LOGD("received packet length: %d", in.ReadInt32());

	BufferOutputStream buf(decryptedLen+32);
	size_t x=0;
	buf.WriteBytes(encryptionKey+88+x, 32);
	buf.WriteBytes(decrypted+4, decryptedLen-4);
	unsigned char msgKeyLarge[32];
	crypto.sha256(buf.GetBuffer(), buf.GetLength(), msgKeyLarge);

	if(memcmp(msgKey, msgKeyLarge+8, 16)!=0){
		LOGW("Received packet from user %d has wrong hash", sender->userID);
		return;
	}

	uint32_t innerLen=(uint32_t) in.ReadInt32();
	if(innerLen>decryptedLen-4){
		LOGW("Received packet has wrong inner length (%d with total of %u)", (int)innerLen, (unsigned int)decryptedLen);
		return;
	}
	if(decryptedLen-innerLen<12){
		LOGW("Received packet has too little padding (%u)", (unsigned int)(decryptedLen-innerLen));
		return;
	}
	in=BufferInputStream(decrypted+4, (size_t) innerLen);

	uint32_t tlid=(uint32_t) in.ReadInt32();
	if(tlid!=TLID_DECRYPTED_AUDIO_BLOCK){
		LOGW("Received packet has unknown TL ID 0x%08x", tlid);
		return;
	}
	in.Seek(in.GetOffset()+16); // random bytes
	int32_t flags=in.ReadInt32();
	if(!(flags & PFLAG_HAS_SEQ) || !(flags & PFLAG_HAS_SENDER_TAG_HASH)){
		LOGW("Received packet has wrong flags");
		return;
	}
	/*uint32_t seq=(uint32_t) * /in.ReadInt32();
	unsigned char senderTagHash[16];
	in.ReadBytes(senderTagHash, 16);
	if(memcmp(senderTagHash, sender->memberTagHash, 16)!=0){
		LOGW("Received packet has wrong inner sender tag hash");
		return;
	}

	//int32_t oneMoreInnerLengthWhyDoWeEvenNeedThis;
	if(flags & PFLAG_HAS_DATA){
		/*oneMoreInnerLengthWhyDoWeEvenNeedThis=* /in.ReadTlLength();
	}
	unsigned char type=(unsigned char) ((flags >> 24) & 0xFF);
	lastRecvPacketTime=GetCurrentTime();

	if(type==PKT_STREAM_DATA || type==PKT_STREAM_DATA_X2 || type==PKT_STREAM_DATA_X3){
		if(state!=STATE_ESTABLISHED && receivedInitAck)
			SetState(STATE_ESTABLISHED);
		int count;
		switch(type){
			case PKT_STREAM_DATA_X2:
				count=2;
				break;
			case PKT_STREAM_DATA_X3:
				count=3;
				break;
			case PKT_STREAM_DATA:
			default:
				count=1;
				break;
		}
		int i;
		//if(srcEndpoint->type==Endpoint::Type::UDP_RELAY && srcEndpoint!=peerPreferredRelay){
		//	peerPreferredRelay=srcEndpoint;
		//}
		for(i=0;i<count;i++){
			unsigned char streamID=in.ReadByte();
			unsigned char sflags=(unsigned char) (streamID & 0xC0);
			uint16_t sdlen=(uint16_t) (sflags & STREAM_DATA_FLAG_LEN16 ? in.ReadInt16() : in.ReadByte());
			uint32_t pts=(uint32_t) in.ReadInt32();
			//LOGD("stream data, pts=%d, len=%d, rem=%d", pts, sdlen, in.Remaining());
			audioTimestampIn=pts;
			/*if(!audioOutStarted && audioOutput){
				audioOutput->Start();
				audioOutStarted=true;
			}* /
			if(in.GetOffset()+sdlen>in.GetLength()){
				return;
			}
			for(vector<shared_ptr<Stream>>::iterator stm=sender->streams.begin();stm!=sender->streams.end();++stm){
				if((*stm)->id==streamID){
					if((*stm)->jitterBuffer){
						(*stm)->jitterBuffer->HandleInput(decrypted+4+in.GetOffset(), sdlen, pts, false);
					}
					break;
				}
			}
			if(i<count-1)
				in.Seek(in.GetOffset()+sdlen);
		}
	}*/
}

void VoIPGroupController::SendUdpPing(Endpoint& endpoint){

}

void VoIPGroupController::SetNetworkType(int type){
	networkType=type;
	UpdateDataSavingState();
	UpdateAudioBitrateLimit();
	string itfName=udpSocket->GetLocalInterfaceInfo(NULL, NULL);
	if(itfName!=activeNetItfName){
		udpSocket->OnActiveInterfaceChanged();
		LOGI("Active network interface changed: %s -> %s", activeNetItfName.c_str(), itfName.c_str());
		bool isFirstChange=activeNetItfName.length()==0;
		activeNetItfName=itfName;
		if(isFirstChange)
			return;
		udpConnectivityState=UDP_UNKNOWN;
		udpPingCount=0;
		lastUdpPingTime=0;
		if(proxyProtocol==PROXY_SOCKS5)
			InitUDPProxy();
		selectCanceller->CancelSelect();
	}
}

void VoIPGroupController::SendRecentPacketsRequest(){
	BufferOutputStream out(1024);
	out.WriteInt32(TLID_UDP_REFLECTOR_REQUEST_PACKETS_INFO); // TL function
	out.WriteInt32(GetCurrentUnixtime()); // date:int
	out.WriteInt64(0); // query_id:long
	out.WriteInt32(64); // recv_num:int
	out.WriteInt32(0); // sent_num:int
	SendSpecialReflectorRequest(out.GetBuffer(), out.GetLength());
}

void VoIPGroupController::SendSpecialReflectorRequest(unsigned char *data, size_t len){
	/*BufferOutputStream out(1024);
	unsigned char buf[1500];
	crypto.rand_bytes(buf, 8);
	out.WriteBytes(buf, 8);
	out.WriteInt32((int32_t)len);
	out.WriteBytes(data, len);
	if(out.GetLength()%16!=0){
		size_t paddingLen=16-(out.GetLength()%16);
		crypto.rand_bytes(buf, paddingLen);
		out.WriteBytes(buf, paddingLen);
	}
	unsigned char iv[16];
	crypto.rand_bytes(iv, 16);
	unsigned char key[32];
	crypto.sha256(reflectorSelfSecret, 16, key);
	unsigned char _iv[16];
	memcpy(_iv, iv, 16);
	size_t encryptedLen=out.GetLength();
	crypto.aes_cbc_encrypt(out.GetBuffer(), buf, encryptedLen, key, _iv);
	out.Reset();
	out.WriteBytes(reflectorSelfTag, 16);
	out.WriteBytes(iv, 16);
	out.WriteBytes(buf, encryptedLen);
	out.WriteBytes(reflectorSelfSecret, 16);
	crypto.sha256(out.GetBuffer(), out.GetLength(), buf);
	out.Rewind(16);
	out.WriteBytes(buf, 16);

	NetworkPacket pkt={0};
	pkt.address=&groupReflector.address;
	pkt.port=groupReflector.port;
	pkt.protocol=PROTO_UDP;
	pkt.data=out.GetBuffer();
	pkt.length=out.GetLength();
	ActuallySendPacket(pkt, groupReflector);*/
}

void VoIPGroupController::SendRelayPings(){
	//LOGV("Send relay pings 2");
	double currentTime=GetCurrentTime();
	if(currentTime-groupReflector.lastPingTime>=0.25){
		SendRecentPacketsRequest();
		groupReflector.lastPingTime=currentTime;
	}
}

void VoIPGroupController::OnAudioOutputReady(){
	encoder->SetDTX(true);
	audioMixer->SetOutput(audioOutput);
	audioMixer->SetEchoCanceller(echoCanceller);
	audioMixer->Start();
	audioOutput->Start();
	audioOutStarted=true;
	encoder->SetLevelMeter(&selfLevelMeter);
}

void VoIPGroupController::WritePacketHeader(uint32_t seq, BufferOutputStream *s, unsigned char type, uint32_t length, PacketSender* source){
	s->WriteInt32(TLID_DECRYPTED_AUDIO_BLOCK);
	int64_t randomID;
	crypto.rand_bytes((uint8_t *) &randomID, 8);
	s->WriteInt64(randomID);
	unsigned char randBytes[7];
	crypto.rand_bytes(randBytes, 7);
	s->WriteByte(7);
	s->WriteBytes(randBytes, 7);
	uint32_t pflags=PFLAG_HAS_SEQ | PFLAG_HAS_SENDER_TAG_HASH;
	if(length>0)
		pflags|=PFLAG_HAS_DATA;
	pflags|=((uint32_t) type) << 24;
	s->WriteInt32(pflags);

	if(type==PKT_STREAM_DATA || type==PKT_STREAM_DATA_X2 || type==PKT_STREAM_DATA_X3){
		conctl->PacketSent(seq, length);
	}

	/*if(pflags & PFLAG_HAS_CALL_ID){
		s->WriteBytes(callID, 16);
	}*/
	//s->WriteInt32(lastRemoteSeq);
	s->WriteInt32(seq);
	s->WriteBytes(reflectorSelfTagHash, 16);
	if(length>0){
		if(length<=253){
			s->WriteByte((unsigned char) length);
		}else{
			s->WriteByte(254);
			s->WriteByte((unsigned char) (length & 0xFF));
			s->WriteByte((unsigned char) ((length >> 8) & 0xFF));
			s->WriteByte((unsigned char) ((length >> 16) & 0xFF));
		}
	}
}

void VoIPGroupController::SendPacket(unsigned char *data, size_t len, Endpoint& ep, PendingOutgoingPacket& srcPacket){
	if(stopping)
		return;
	if(ep.type==Endpoint::Type::TCP_RELAY && !useTCP)
		return;
	BufferOutputStream out(len+128);
	//LOGV("send group packet %u", len);

	out.WriteBytes(reflectorSelfTag, 16);

	if(len>0){
		BufferOutputStream inner(len+128);
		inner.WriteInt32((uint32_t)len);
		inner.WriteBytes(data, len);
		size_t padLen=16-inner.GetLength()%16;
		if(padLen<12)
			padLen+=16;
		unsigned char padding[28];
		crypto.rand_bytes((uint8_t *) padding, padLen);
		inner.WriteBytes(padding, padLen);
		assert(inner.GetLength()%16==0);

		unsigned char key[32], iv[32], msgKey[16];
		out.WriteBytes(keyFingerprint, 8);
		BufferOutputStream buf(len+32);
		size_t x=0;
		buf.WriteBytes(encryptionKey+88+x, 32);
		buf.WriteBytes(inner.GetBuffer()+4, inner.GetLength()-4);
		unsigned char msgKeyLarge[32];
		crypto.sha256(buf.GetBuffer(), buf.GetLength(), msgKeyLarge);
		memcpy(msgKey, msgKeyLarge+8, 16);
		KDF2(msgKey, 0, key, iv);
		out.WriteBytes(msgKey, 16);
		//LOGV("<- MSG KEY: %08x %08x %08x %08x, hashed %u", *reinterpret_cast<int32_t*>(msgKey), *reinterpret_cast<int32_t*>(msgKey+4), *reinterpret_cast<int32_t*>(msgKey+8), *reinterpret_cast<int32_t*>(msgKey+12), inner.GetLength()-4);

		unsigned char aesOut[MSC_STACK_FALLBACK(inner.GetLength(), 1500)];
		crypto.aes_ige_encrypt(inner.GetBuffer(), aesOut, inner.GetLength(), key, iv);
		out.WriteBytes(aesOut, inner.GetLength());
	}

	// relay signature
	out.WriteBytes(reflectorSelfSecret, 16);
	unsigned char sig[32];
	crypto.sha256(out.GetBuffer(), out.GetLength(), sig);
	out.Rewind(16);
	out.WriteBytes(sig, 16);

	if(srcPacket.type==PKT_STREAM_DATA || srcPacket.type==PKT_STREAM_DATA_X2 || srcPacket.type==PKT_STREAM_DATA_X3){
		PacketIdMapping mapping={srcPacket.seq, *reinterpret_cast<uint16_t*>(sig+14), 0};
		MutexGuard m(sentPacketsMutex);
		recentSentPackets.push_back(mapping);
		//LOGD("sent packet with id: %04X", mapping.id);
		while(recentSentPackets.size()>64)
			recentSentPackets.erase(recentSentPackets.begin());
	}
	lastSentSeq=srcPacket.seq;

	if(IS_MOBILE_NETWORK(networkType))
		stats.bytesSentMobile+=(uint64_t)out.GetLength();
	else
		stats.bytesSentWifi+=(uint64_t)out.GetLength();

	/*NetworkPacket pkt={0};
	pkt.address=(NetworkAddress*)&ep.address;
	pkt.port=ep.port;
	pkt.length=out.GetLength();
	pkt.data=out.GetBuffer();
	pkt.protocol=ep.type==Endpoint::Type::TCP_RELAY ? PROTO_TCP : PROTO_UDP;
	ActuallySendPacket(pkt, ep);*/
}

void VoIPGroupController::SetCallbacks(VoIPGroupController::Callbacks callbacks){
	VoIPController::SetCallbacks(callbacks);
	this->groupCallbacks=callbacks;
}

int32_t VoIPGroupController::GetCurrentUnixtime(){
	return time(NULL)+timeDifference;
}

float VoIPGroupController::GetParticipantAudioLevel(int32_t userID){
	if(userID==userSelfID)
		return selfLevelMeter.GetLevel();
	MutexGuard m(participantsMutex);
	for(vector<GroupCallParticipant>::iterator p=participants.begin(); p!=participants.end(); ++p){
		if(p->userID==userID){
			return p->levelMeter->GetLevel();
		}
	}
	return 0;
}

void VoIPGroupController::SetMicMute(bool mute){
	micMuted=mute;
	if(audioInput){
		if(mute)
			audioInput->Stop();
		else
			audioInput->Start();
		if(!audioInput->IsInitialized()){
			lastError=ERROR_AUDIO_IO;
			SetState(STATE_FAILED);
			return;
		}
	}
	outgoingStreams[0]->enabled=!mute;
	SerializeAndUpdateOutgoingStreams();
}

void VoIPGroupController::SetParticipantVolume(int32_t userID, float volume){
	MutexGuard m(participantsMutex);
	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		if(p->userID==userID){
			for(vector<shared_ptr<Stream>>::iterator s=p->streams.begin();s!=p->streams.end();++s){
				if((*s)->type==STREAM_TYPE_AUDIO){
					if((*s)->decoder){
						float db;
						if(volume==0.0f)
							db=-INFINITY;
						else if(volume<1.0f)
							db=-50.0f*(1.0f-volume);
						else if(volume>1.0f && volume<=2.0f)
							db=10.0f*(volume-1.0f);
						else
							db=0.0f;
						//LOGV("Setting user %u audio volume to %.2f dB", userID, db);
						audioMixer->SetInputVolume((*s)->callbackWrapper, db);
					}
					break;
				}
			}
			break;
		}
	}
}

void VoIPGroupController::SerializeAndUpdateOutgoingStreams(){
	BufferOutputStream out(1024);
	out.WriteByte((unsigned char) outgoingStreams.size());

	for(vector<shared_ptr<Stream>>::iterator s=outgoingStreams.begin(); s!=outgoingStreams.end(); ++s){
		BufferOutputStream o(128);
		o.WriteByte((*s)->id);
		o.WriteByte((*s)->type);
		o.WriteInt32((*s)->codec);
		o.WriteInt32((unsigned char) (((*s)->enabled ? STREAM_FLAG_ENABLED : 0) | STREAM_FLAG_DTX));
		o.WriteInt16((*s)->frameDuration);
		out.WriteInt16((int16_t) o.GetLength());
		out.WriteBytes(o.GetBuffer(), o.GetLength());
	}
	if(groupCallbacks.updateStreams)
		groupCallbacks.updateStreams(this, out.GetBuffer(), out.GetLength());
}

std::string VoIPGroupController::GetDebugString(){
	std::string r="Remote endpoints: \n";
	char buffer[2048];
	for(pair<const int64_t, Endpoint>& _endpoint:endpoints){
		Endpoint& endpoint=_endpoint.second;
		const char* type;
		switch(endpoint.type){
			case Endpoint::Type::UDP_P2P_INET:
				type="UDP_P2P_INET";
				break;
			case Endpoint::Type::UDP_P2P_LAN:
				type="UDP_P2P_LAN";
				break;
			case Endpoint::Type::UDP_RELAY:
				type="UDP_RELAY";
				break;
			case Endpoint::Type::TCP_RELAY:
				type="TCP_RELAY";
				break;
			default:
				type="UNKNOWN";
				break;
		}
		snprintf(buffer, sizeof(buffer), "%s:%u %dms [%s%s]\n", endpoint.address.ToString().c_str(), endpoint.port, (int)(endpoint.averageRTT*1000), type, currentEndpoint==endpoint.id ? ", IN_USE" : "");
		r+=buffer;
	}
	double avgLate[3];
	shared_ptr<JitterBuffer> jitterBuffer=incomingStreams.size()==1 ? incomingStreams[0]->jitterBuffer : NULL;
	if(jitterBuffer)
		jitterBuffer->GetAverageLateCount(avgLate);
	else
		memset(avgLate, 0, 3*sizeof(double));
	snprintf(buffer, sizeof(buffer),
					 "RTT avg/min: %d/%d\n"
					 "Congestion window: %d/%d bytes\n"
					 "Key fingerprint: %02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX\n"
					 "Last sent/ack'd seq: %u/%u\n"
					 "Send/recv losses: %u/%u (%d%%)\n"
					 "Audio bitrate: %d kbit\n"
					 "Bytes sent/recvd: %llu/%llu\n\n",
			 (int)(conctl->GetAverageRTT()*1000), (int)(conctl->GetMinimumRTT()*1000),
			 int(conctl->GetInflightDataSize()), int(conctl->GetCongestionWindow()),
			 keyFingerprint[0],keyFingerprint[1],keyFingerprint[2],keyFingerprint[3],keyFingerprint[4],keyFingerprint[5],keyFingerprint[6],keyFingerprint[7],
			 lastSentSeq, lastRemoteAckSeq,
			 conctl->GetSendLossCount(), recvLossCount, encoder ? encoder->GetPacketLoss() : 0,
			 encoder ? (encoder->GetBitrate()/1000) : 0,
			 (long long unsigned int)(stats.bytesSentMobile+stats.bytesSentWifi),
			 (long long unsigned int)(stats.bytesRecvdMobile+stats.bytesRecvdWifi));

	MutexGuard m(participantsMutex);
	for(vector<GroupCallParticipant>::iterator p=participants.begin();p!=participants.end();++p){
		snprintf(buffer, sizeof(buffer), "Participant id: %d\n", p->userID);
		r+=buffer;
		for(vector<shared_ptr<Stream>>::iterator stm=p->streams.begin();stm!=p->streams.end();++stm){
			char* codec=reinterpret_cast<char*>(&(*stm)->codec);
			snprintf(buffer, sizeof(buffer), "Stream %d (type %d, codec '%c%c%c%c', %sabled)\n",
					 (*stm)->id, (*stm)->type, codec[3], codec[2], codec[1], codec[0], (*stm)->enabled ? "en" : "dis");
			r+=buffer;
			if((*stm)->enabled){
				if((*stm)->jitterBuffer){
					snprintf(buffer, sizeof(buffer), "Jitter buffer: %d/%.2f\n",
							 (*stm)->jitterBuffer->GetMinPacketCount(), (*stm)->jitterBuffer->GetAverageDelay());
					r+=buffer;
				}
			}
		}
		r+="\n";
	}
	return r;
}
