//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef _WIN32
#include <unistd.h>
#include <sys/time.h>
#endif
#include <errno.h>
#include <string.h>
#include <wchar.h>
#include "VoIPController.h"
#include "logging.h"
#include "threading.h"
#include "Buffers.h"
#include "OpusEncoder.h"
#include "OpusDecoder.h"
#include "VoIPServerConfig.h"
#include "PrivateDefines.h"
#include "json11.hpp"
#include "PacketSender.h"
#include "video/VideoPacketSender.h"
#include <assert.h>
#include <time.h>
#include <math.h>
#include <exception>
#include <stdexcept>
#include <algorithm>
#include <sstream>
#include <inttypes.h>
#include <float.h>
#if defined HAVE_CONFIG_H || defined TGVOIP_USE_INSTALLED_OPUS
#include <opus/opus.h>
#else
#include "opus.h"
#endif

inline int pad4(int x){
	int r=PAD4(x);
	if(r==4)
		return 0;
	return r;
}


using namespace tgvoip;
// using namespace std; // Already used in BlockingQueue.h.

#ifdef __APPLE__
#include "os/darwin/AudioUnitIO.h"
#include <mach/mach_time.h>
double VoIPController::machTimebase=0;
uint64_t VoIPController::machTimestart=0;
#endif

#ifdef _WIN32
int64_t VoIPController::win32TimeScale = 0;
bool VoIPController::didInitWin32TimeScale = false;
#endif

#ifdef __ANDROID__
#include "os/android/JNIUtilities.h"
#include "os/android/AudioInputAndroid.h"
#include "NetworkSocket.h"

extern jclass jniUtilitiesClass;
#endif

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
#include "audio/AudioIOCallback.h"
#endif

#define ENFORCE_MSG_THREAD assert(messageThread.IsCurrent())

extern FILE* tgvoipLogFile;

#pragma mark - Public API

VoIPController::VoIPController() : activeNetItfName(""),
								   currentAudioInput("default"),
								   currentAudioOutput("default"),
								   proxyAddress(""),
								   proxyUsername(""),
								   proxyPassword(""),
								   rawSendQueue(64){
	seq=1;
	lastRemoteSeq=0;
	state=STATE_WAIT_INIT;
	audioInput=NULL;
	audioOutput=NULL;
	encoder=NULL;
	audioOutStarted=false;
	audioTimestampIn=0;
	audioTimestampOut=0;
	stopping=false;
	memset(&stats, 0, sizeof(TrafficStats));
	lastRemoteAckSeq=0;
	lastSentSeq=0;
	recvLossCount=0;
	packetsReceived=0;
	waitingForAcks=false;
	networkType=NET_TYPE_UNKNOWN;
	echoCanceller=NULL;
	dontSendPackets=0;
	micMuted=false;
	waitingForRelayPeerInfo=false;
	allowP2p=true;
	dataSavingMode=false;
	publicEndpointsReqTime=0;
	connectionInitTime=0;
	lastRecvPacketTime=0;
	dataSavingRequestedByPeer=false;
	peerVersion=0;
	conctl=new CongestionControl();
	prevSendLossCount=0;
	receivedInit=false;
	receivedInitAck=false;
	statsDump=NULL;
	useTCP=false;
	useUDP=true;
	didAddTcpRelays=false;
	udpPingCount=0;
	lastUdpPingTime=0;

	proxyProtocol=PROXY_NONE;
	proxyPort=0;

	selectCanceller=SocketSelectCanceller::Create();
	udpSocket=NetworkSocket::Create(NetworkProtocol::UDP);
	realUdpSocket=udpSocket;
	udpConnectivityState=UDP_UNKNOWN;
	echoCancellationStrength=1;

	peerCapabilities=0;
	callbacks={0};
	didReceiveGroupCallKey=false;
	didReceiveGroupCallKeyAck=false;
	didSendGroupCallKey=false;
	didSendUpgradeRequest=false;
	didInvokeUpgradeCallback=false;

	connectionMaxLayer=0;
	useMTProto2=false;
	setCurrentEndpointToTCP=false;
	useIPv6=false;
	peerIPv6Available=false;
	shittyInternetMode=false;
	didAddIPv6Relays=false;
	didSendIPv6Endpoint=false;
	unsentStreamPackets.store(0);
	runReceiver=false;

	sendThread=NULL;
	recvThread=NULL;

	maxAudioBitrate=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_max_bitrate", 20000);
	maxAudioBitrateGPRS=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_max_bitrate_gprs", 8000);
	maxAudioBitrateEDGE=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_max_bitrate_edge", 16000);
	maxAudioBitrateSaving=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_max_bitrate_saving", 8000);
	initAudioBitrate=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_init_bitrate", 16000);
	initAudioBitrateGPRS=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_init_bitrate_gprs", 8000);
	initAudioBitrateEDGE=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_init_bitrate_edge", 8000);
	initAudioBitrateSaving=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_init_bitrate_saving", 8000);
	audioBitrateStepIncr=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_bitrate_step_incr", 1000);
	audioBitrateStepDecr=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_bitrate_step_decr", 1000);
	minAudioBitrate=(uint32_t) ServerConfig::GetSharedInstance()->GetInt("audio_min_bitrate", 8000);
	relaySwitchThreshold=ServerConfig::GetSharedInstance()->GetDouble("relay_switch_threshold", 0.8);
	p2pToRelaySwitchThreshold=ServerConfig::GetSharedInstance()->GetDouble("p2p_to_relay_switch_threshold", 0.6);
	relayToP2pSwitchThreshold=ServerConfig::GetSharedInstance()->GetDouble("relay_to_p2p_switch_threshold", 0.8);
	reconnectingTimeout=ServerConfig::GetSharedInstance()->GetDouble("reconnecting_state_timeout", 2.0);
	needRateFlags=static_cast<uint32_t>(ServerConfig::GetSharedInstance()->GetInt("rate_flags", 0xFFFFFFFF));
	rateMaxAcceptableRTT=ServerConfig::GetSharedInstance()->GetDouble("rate_min_rtt", 0.6);
	rateMaxAcceptableSendLoss=ServerConfig::GetSharedInstance()->GetDouble("rate_min_send_loss", 0.2);
	packetLossToEnableExtraEC=ServerConfig::GetSharedInstance()->GetDouble("packet_loss_for_extra_ec", 0.02);
	maxUnsentStreamPackets=static_cast<uint32_t>(ServerConfig::GetSharedInstance()->GetInt("max_unsent_stream_packets", 2));
	unackNopThreshold=static_cast<uint32_t>(ServerConfig::GetSharedInstance()->GetInt("unack_nop_threshold", 10));

#ifdef __APPLE__
	machTimestart=0;
#endif

	shared_ptr<Stream> stm=make_shared<Stream>();
	stm->id=1;
	stm->type=STREAM_TYPE_AUDIO;
	stm->codec=CODEC_OPUS;
	stm->enabled=1;
	stm->frameDuration=60;
	outgoingStreams.push_back(stm);

}

VoIPController::~VoIPController(){
	LOGD("Entered VoIPController::~VoIPController");
	if(!stopping){
		LOGE("!!!!!!!!!!!!!!!!!!!! CALL controller->Stop() BEFORE DELETING THE CONTROLLER OBJECT !!!!!!!!!!!!!!!!!!!!!!!1");
		abort();
	}
	LOGD("before close socket");
	if(udpSocket)
		delete udpSocket;
	if(udpSocket!=realUdpSocket)
		delete realUdpSocket;
	LOGD("before delete audioIO");
	if(audioIO){
		delete audioIO;
		audioInput=NULL;
		audioOutput=NULL;
	}
	for(vector<shared_ptr<Stream>>::iterator _stm=incomingStreams.begin();_stm!=incomingStreams.end();++_stm){
		shared_ptr<Stream> stm=*_stm;
		LOGD("before stop decoder");
		if(stm->decoder){
			stm->decoder->Stop();
		}
	}
	LOGD("before delete encoder");
	if(encoder){
		encoder->Stop();
		delete encoder;
	}
	LOGD("before delete echo canceller");
	if(echoCanceller){
		echoCanceller->Stop();
		delete echoCanceller;
	}
	delete conctl;
	if(statsDump)
		fclose(statsDump);
	delete selectCanceller;
	LOGD("Left VoIPController::~VoIPController");
	if(tgvoipLogFile){
		FILE* log=tgvoipLogFile;
		tgvoipLogFile=NULL;
		fclose(log);
	}
#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
	if (preprocDecoder) {
        opus_decoder_destroy(preprocDecoder);
        preprocDecoder=nullptr;
	}
#endif
}

void VoIPController::Stop(){
	LOGD("Entered VoIPController::Stop");
	stopping=true;
	runReceiver=false;
	LOGD("before shutdown socket");
	if(udpSocket)
		udpSocket->Close();
	if(realUdpSocket!=udpSocket)
		realUdpSocket->Close();
	selectCanceller->CancelSelect();
	//Buffer emptyBuf(0);
	//PendingOutgoingPacket emptyPacket{0, 0, 0, move(emptyBuf), 0};
	//sendQueue->Put(move(emptyPacket));
	rawSendQueue.Put(RawPendingOutgoingPacket{NetworkPacket::Empty(), nullptr});
	LOGD("before join sendThread");
	if(sendThread){
		sendThread->Join();
		delete sendThread;
	}
	LOGD("before join recvThread");
	if(recvThread){
		recvThread->Join();
		delete recvThread;
	}
	LOGD("before stop messageThread");
	messageThread.Stop();
	{
		LOGD("Before stop audio I/O");
		MutexGuard m(audioIOMutex);
		if(audioInput){
			audioInput->Stop();
			audioInput->SetCallback(NULL, NULL);
		}
		if(audioOutput){
			audioOutput->Stop();
			audioOutput->SetCallback(NULL, NULL);
		}
	}
	if(videoPacketSender){
		LOGD("before delete video packet sender");
		delete videoPacketSender;
		videoPacketSender=NULL;
	}
	LOGD("Left VoIPController::Stop [need rate = %d]", (int)needRate);
}

bool VoIPController::NeedRate(){
	return needRate && ServerConfig::GetSharedInstance()->GetBoolean("bad_call_rating", false);
}

void VoIPController::SetRemoteEndpoints(vector<Endpoint> endpoints, bool allowP2p, int32_t connectionMaxLayer){
	LOGW("Set remote endpoints, allowP2P=%d, connectionMaxLayer=%u", allowP2p ? 1 : 0, connectionMaxLayer);
	assert(!runReceiver);
	preferredRelay=0;

	this->endpoints.clear();
	didAddTcpRelays=false;
	useTCP=true;
	for(vector<Endpoint>::iterator itrtr=endpoints.begin();itrtr!=endpoints.end();++itrtr){
		if(this->endpoints.find(itrtr->id)!=this->endpoints.end())
			LOGE("Endpoint IDs are not unique!");
		this->endpoints[itrtr->id]=*itrtr;
		if(currentEndpoint==0)
			currentEndpoint=itrtr->id;
		if(itrtr->type==Endpoint::Type::TCP_RELAY)
			didAddTcpRelays=true;
		if(itrtr->type==Endpoint::Type::UDP_RELAY)
			useTCP=false;
		LOGV("Adding endpoint: %s:%d, %s", itrtr->address.ToString().c_str(), itrtr->port, itrtr->type==Endpoint::Type::UDP_RELAY ? "UDP" : "TCP");
	}
	preferredRelay=currentEndpoint;
	this->allowP2p=allowP2p;
	this->connectionMaxLayer=connectionMaxLayer;
	if(connectionMaxLayer>=74){
		useMTProto2=true;
	}
	AddIPv6Relays();
}

void VoIPController::Start(){
	LOGW("Starting voip controller");
	udpSocket->Open();
	if(udpSocket->IsFailed()){
		SetState(STATE_FAILED);
		return;
	}

	runReceiver=true;
	recvThread=new Thread(bind(&VoIPController::RunRecvThread, this));
	recvThread->SetName("VoipRecv");
	recvThread->Start();

	messageThread.Start();
}


void VoIPController::Connect(){
	assert(state!=STATE_WAIT_INIT_ACK);
	connectionInitTime=GetCurrentTime();
	if(config.initTimeout==0.0){
		LOGE("Init timeout is 0 -- did you forget to set config?");
		config.initTimeout=30.0;
	}

	//InitializeTimers();
	//SendInit();
	sendThread=new Thread(bind(&VoIPController::RunSendThread, this));
	sendThread->SetName("VoipSend");
	sendThread->Start();
}

void VoIPController::SetEncryptionKey(char *key, bool isOutgoing){
	memcpy(encryptionKey, key, 256);
	uint8_t sha1[SHA1_LENGTH];
	crypto.sha1((uint8_t*) encryptionKey, 256, sha1);
	memcpy(keyFingerprint, sha1+(SHA1_LENGTH-8), 8);
	uint8_t sha256[SHA256_LENGTH];
	crypto.sha256((uint8_t*) encryptionKey, 256, sha256);
	memcpy(callID, sha256+(SHA256_LENGTH-16), 16);
	this->isOutgoing=isOutgoing;
}

void VoIPController::SetNetworkType(int type){
	networkType=type;
	UpdateDataSavingState();
	UpdateAudioBitrateLimit();
	myIPv6=NetworkAddress::Empty();
	string itfName=udpSocket->GetLocalInterfaceInfo(NULL, &myIPv6);
	LOGI("set network type: %s, active interface %s", NetworkTypeToString(type).c_str(), itfName.c_str());
	LOGI("Local IPv6 address: %s", myIPv6.ToString().c_str());
	if(IS_MOBILE_NETWORK(networkType)){
		CellularCarrierInfo carrier=GetCarrierInfo();
		if(!carrier.name.empty()){
			LOGI("Carrier: %s [%s; mcc=%s, mnc=%s]", carrier.name.c_str(), carrier.countryCode.c_str(), carrier.mcc.c_str(), carrier.mnc.c_str());
		}
	}
	if(itfName!=activeNetItfName){
		udpSocket->OnActiveInterfaceChanged();
		LOGI("Active network interface changed: %s -> %s", activeNetItfName.c_str(), itfName.c_str());
		bool isFirstChange=activeNetItfName.length()==0 && state!=STATE_ESTABLISHED && state!=STATE_RECONNECTING;
		activeNetItfName=itfName;
		if(isFirstChange)
			return;
		messageThread.Post([this]{
			wasNetworkHandover=true;
			if(currentEndpoint){
				const Endpoint &_currentEndpoint=endpoints.at(currentEndpoint);
				const Endpoint &_preferredRelay=endpoints.at(preferredRelay);
				if(_currentEndpoint.type!=Endpoint::Type::UDP_RELAY){
					if(_preferredRelay.type==Endpoint::Type::UDP_RELAY)
						currentEndpoint=preferredRelay;
					MutexGuard m(endpointsMutex);
					constexpr int64_t lanID=(int64_t) (FOURCC('L', 'A', 'N', '4')) << 32;
					endpoints.erase(lanID);
					for(pair<const int64_t, Endpoint> &e:endpoints){
						Endpoint &endpoint=e.second;
						if(endpoint.type==Endpoint::Type::UDP_RELAY && useTCP){
							useTCP=false;
							if(_preferredRelay.type==Endpoint::Type::TCP_RELAY){
								preferredRelay=currentEndpoint=endpoint.id;
							}
						}else if(endpoint.type==Endpoint::Type::TCP_RELAY && endpoint.socket){
							endpoint.socket->Close();
							//delete endpoint.socket;
							//endpoint.socket=NULL;
						}
						//if(endpoint->type==Endpoint::Type::UDP_P2P_INET){
						endpoint.averageRTT=0;
						endpoint.rtts.Reset();
						//}
					}
				}
			}
			lastUdpPingTime=0;
			if(proxyProtocol==PROXY_SOCKS5)
				InitUDPProxy();
			if(allowP2p && currentEndpoint){
				SendPublicEndpointsRequest();
			}
			BufferOutputStream s(4);
			s.WriteInt32(dataSavingMode ? INIT_FLAG_DATA_SAVING_ENABLED : 0);
			if(peerVersion<6){
				SendPacketReliably(PKT_NETWORK_CHANGED, s.GetBuffer(), s.GetLength(), 1, 20);
			}else{
				Buffer buf(move(s));
				SendExtra(buf, EXTRA_TYPE_NETWORK_CHANGED);
			}
			needReInitUdpProxy=true;
			selectCanceller->CancelSelect();
			didSendIPv6Endpoint=false;

			AddIPv6Relays();
			ResetUdpAvailability();
			ResetEndpointPingStats();
		});

	}
}

double VoIPController::GetAverageRTT(){
	ENFORCE_MSG_THREAD;

	if(lastSentSeq>=lastRemoteAckSeq){
		uint32_t diff=lastSentSeq-lastRemoteAckSeq;
		//LOGV("rtt diff=%u", diff);
		if(diff<32){
			double res=0;
			int count=0;
			for(std::vector<RecentOutgoingPacket>::iterator itr=recentOutgoingPackets.begin();itr!=recentOutgoingPackets.end();++itr){
				if(itr->ackTime>0){
					res+=(itr->ackTime-itr->sendTime);
					count++;
				}
			}
			if(count>0)
				res/=count;
			return res;
		}
	}
	return 999;
}

void VoIPController::SetMicMute(bool mute){
	if(micMuted==mute)
		return;
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
	if(echoCanceller)
		echoCanceller->Enable(!mute);
	if(state==STATE_ESTABLISHED){
		messageThread.Post([this]{
			for(shared_ptr<Stream> &s:outgoingStreams){
				if(s->type==STREAM_TYPE_AUDIO){
					s->enabled=!micMuted;
					if(peerVersion<6){
						unsigned char buf[2];
						buf[0]=s->id;
						buf[1]=(char) (micMuted ? 0 : 1);
						SendPacketReliably(PKT_STREAM_STATE, buf, 2, .5f, 20);
					}else{
						SendStreamFlags(*s);
					}
				}
			}
		});
	}
}

string VoIPController::GetDebugString(){
	string r="Remote endpoints: \n";
	char buffer[2048];
	MutexGuard m(endpointsMutex);
	for(pair<const int64_t, Endpoint>& _e:endpoints){
		Endpoint& endpoint=_e.second;
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
		snprintf(buffer, sizeof(buffer), "%s:%u %dms %d 0x%" PRIx64 " [%s%s]\n", endpoint.address.IsEmpty() ? ("["+endpoint.v6address.ToString()+"]").c_str() : endpoint.address.ToString().c_str(), endpoint.port, (int)(endpoint.averageRTT*1000), endpoint.udpPongCount, (uint64_t)endpoint.id, type, currentEndpoint==endpoint.id ? ", IN_USE" : "");
		r+=buffer;
	}
	if(shittyInternetMode){
		snprintf(buffer, sizeof(buffer), "ShittyInternetMode: level %d\n", extraEcLevel);
		r+=buffer;
	}
	double avgLate[3];
	shared_ptr<Stream> stm=GetStreamByType(STREAM_TYPE_AUDIO, false);
	shared_ptr<JitterBuffer> jitterBuffer;
	if(stm)
		jitterBuffer=stm->jitterBuffer;
	if(jitterBuffer)
		jitterBuffer->GetAverageLateCount(avgLate);
	else
		memset(avgLate, 0, 3*sizeof(double));
	snprintf(buffer, sizeof(buffer),
			 "Jitter buffer: %d/%.2f | %.1f, %.1f, %.1f\n"
			 "RTT avg/min: %d/%d\n"
			 "Congestion window: %d/%d bytes\n"
			 "Key fingerprint: %02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%02hhX%s\n"
			 "Last sent/ack'd seq: %u/%u\n"
			 "Last recvd seq: %u\n"
			 "Send/recv losses: %u/%u (%d%%)\n"
			 "Audio bitrate: %d kbit\n"
			 "Outgoing queue: %u\n"
			 //					 "Packet grouping: %d\n"
			 "Frame size out/in: %d/%d\n"
			 "Bytes sent/recvd: %llu/%llu",
			 jitterBuffer ? jitterBuffer->GetMinPacketCount() : 0, jitterBuffer ? jitterBuffer->GetAverageDelay() : 0, avgLate[0], avgLate[1], avgLate[2],
			// (int)(GetAverageRTT()*1000), 0,
			 (int)(conctl->GetAverageRTT()*1000), (int)(conctl->GetMinimumRTT()*1000),
			 int(conctl->GetInflightDataSize()), int(conctl->GetCongestionWindow()),
			 keyFingerprint[0],keyFingerprint[1],keyFingerprint[2],keyFingerprint[3],keyFingerprint[4],keyFingerprint[5],keyFingerprint[6],keyFingerprint[7],
			 useMTProto2 ? " (MTProto2.0)" : "",
			 lastSentSeq, lastRemoteAckSeq, lastRemoteSeq,
			 sendLosses, recvLossCount, encoder ? encoder->GetPacketLoss() : 0,
			 encoder ? (encoder->GetBitrate()/1000) : 0,
			 static_cast<unsigned int>(unsentStreamPackets),
//			 audioPacketGrouping,
			 outgoingStreams[0]->frameDuration, incomingStreams.size()>0 ? incomingStreams[0]->frameDuration : 0,
			 (long long unsigned int)(stats.bytesSentMobile+stats.bytesSentWifi),
			 (long long unsigned int)(stats.bytesRecvdMobile+stats.bytesRecvdWifi));
	r+=buffer;

	if(config.enableVideoSend){
		shared_ptr<Stream> vstm=GetStreamByType(STREAM_TYPE_VIDEO, true);
		if(vstm && vstm->enabled && videoPacketSender){
			snprintf(buffer, sizeof(buffer), "\nVideo out: %ux%u '%c%c%c%c' %u kbit", vstm->width, vstm->height, PRINT_FOURCC(vstm->codec), videoPacketSender->GetBitrate());
			r+=buffer;
		}
	}
	if(!peerVideoDecoders.empty()){
		r+="\nPeer codecs: ";
		for(uint32_t codec:peerVideoDecoders){
			snprintf(buffer, sizeof(buffer), "'%c%c%c%c' ", PRINT_FOURCC(codec));
			r+=buffer;
		}
	}
	if(config.enableVideoReceive){
		shared_ptr<Stream> vstm=GetStreamByType(STREAM_TYPE_VIDEO, false);
		if(vstm && vstm->enabled){
			snprintf(buffer, sizeof(buffer), "\nVideo in: %ux%u '%c%c%c%c'", vstm->width, vstm->height, PRINT_FOURCC(vstm->codec));
			r+=buffer;
		}
	}

	return r;
}

const char* VoIPController::GetVersion(){
	return LIBTGVOIP_VERSION;
}


int64_t VoIPController::GetPreferredRelayID(){
	return preferredRelay;
}


int VoIPController::GetLastError(){
	return lastError;
}


void VoIPController::GetStats(TrafficStats *stats){
	memcpy(stats, &this->stats, sizeof(TrafficStats));
}

string VoIPController::GetDebugLog(){
	map<string, json11::Json> network{
			{"type", NetworkTypeToString(networkType)}
	};
	if(IS_MOBILE_NETWORK(networkType)){
		CellularCarrierInfo carrier=GetCarrierInfo();
		if(!carrier.name.empty()){
			network["carrier"]=carrier.name;
			network["country"]=carrier.countryCode;
			network["mcc"]=carrier.mcc;
			network["mnc"]=carrier.mnc;
		}
	}else if(networkType==NET_TYPE_WIFI){
#ifdef __ANDROID__
		jni::DoWithJNI([&](JNIEnv* env){
			jmethodID getWifiInfoMethod=env->GetStaticMethodID(jniUtilitiesClass, "getWifiInfo", "()[I");
			jintArray res=static_cast<jintArray>(env->CallStaticObjectMethod(jniUtilitiesClass, getWifiInfoMethod));
			if(res){
				jint* wifiInfo=env->GetIntArrayElements(res, NULL);
				network["rssi"]=wifiInfo[0];
				network["link_speed"]=wifiInfo[1];
				env->ReleaseIntArrayElements(res, wifiInfo, JNI_ABORT);
			}
		});
#endif
	}
	/*vector<json11::Json> lpkts;
	for(DebugLoggedPacket& lpkt:debugLoggedPackets){
		lpkts.push_back(json11::Json::array{lpkt.timestamp, lpkt.seq, lpkt.length});
	}
	return json11::Json(json11::Json::object{
			{"log_type", "out_packet_stats"},
			{"libtgvoip_version", LIBTGVOIP_VERSION},
			{"network", network},
			{"protocol_version", std::min(peerVersion, PROTOCOL_VERSION)},
			{"total_losses", json11::Json::object{
					{"s", (int32_t)conctl->GetSendLossCount()},
					{"r", (int32_t)recvLossCount}
			}},
			{"call_duration", GetCurrentTime()-connectionInitTime},
			{"out_packet_stats", lpkts}
	}).dump();*/

	vector<json11::Json> _endpoints;
	for(pair<const int64_t, Endpoint>& _e:endpoints){
		Endpoint& e=_e.second;
		string type;
		map<string, json11::Json> je{
				{"rtt", (int)(e.averageRTT*1000.0)}
		};
		int64_t id=0;
		if(e.type==Endpoint::Type::UDP_RELAY){
			je["type"]=e.IsIPv6Only() ? "udp_relay6" : "udp_relay";
			id=e.CleanID();
			if(e.totalUdpPings==0)
				je["udp_pings"]=0.0;
			else
				je["udp_pings"]=(double)e.totalUdpPingReplies/(double)e.totalUdpPings;
			je["self_rtt"]=(int)(e.selfRtts.Average()*1000.0);
		}else if(e.type==Endpoint::Type::TCP_RELAY){
			je["type"]=e.IsIPv6Only() ? "tcp_relay6" : "tcp_relay";
			id=e.CleanID();
		}else if(e.type==Endpoint::Type::UDP_P2P_INET){
			je["type"]=e.IsIPv6Only() ? "p2p_inet6" : "p2p_inet";
		}else if(e.type==Endpoint::Type::UDP_P2P_LAN){
			je["type"]="p2p_lan";
		}
		if(preferredRelay==e.id && wasEstablished)
			je["pref"]=true;
		if(id){
			ostringstream s;
			s << id;
			je["id"]=s.str();
		}
		_endpoints.push_back(je);
	}

	string p2pType="none";
	Endpoint& cur=endpoints[currentEndpoint];
	if(cur.type==Endpoint::Type::UDP_P2P_INET)
		p2pType=cur.IsIPv6Only() ? "inet6" : "inet";
	else if(cur.type==Endpoint::Type::UDP_P2P_LAN)
		p2pType="lan";

	vector<string> problems;
	if(lastError==ERROR_TIMEOUT)
		problems.push_back("timeout");
	if(wasReconnecting)
		problems.push_back("reconnecting");
	if(wasExtraEC)
		problems.push_back("extra_ec");
	if(wasEncoderLaggy)
		problems.push_back("encoder_lag");
	if(!wasEstablished)
		problems.push_back("not_inited");
	if(wasNetworkHandover)
		problems.push_back("network_handover");

	return json11::Json(json11::Json::object{
			{"log_type", "call_stats"},
			{"libtgvoip_version", LIBTGVOIP_VERSION},
			{"network", network},
			{"protocol_version", std::min(peerVersion, PROTOCOL_VERSION)},
			{"udp_avail", udpConnectivityState==UDP_AVAILABLE},
			{"tcp_used", useTCP},
			{"p2p_type", p2pType},
			{"packet_stats", json11::Json::object{
					{"out", (int)seq},
					{"in", (int)packetsReceived},
					{"lost_out", (int)conctl->GetSendLossCount()},
					{"lost_in", (int)recvLossCount}
			}},
			{"endpoints", _endpoints},
			{"problems", problems}
	}).dump();
}

vector<AudioInputDevice> VoIPController::EnumerateAudioInputs(){
	vector<AudioInputDevice> devs;
	audio::AudioInput::EnumerateDevices(devs);
	return devs;
}

vector<AudioOutputDevice> VoIPController::EnumerateAudioOutputs(){
	vector<AudioOutputDevice> devs;
	audio::AudioOutput::EnumerateDevices(devs);
	return devs;
}

void VoIPController::SetCurrentAudioInput(string id){
	currentAudioInput=id;
	if(audioInput)
		audioInput->SetCurrentDevice(id);
}

void VoIPController::SetCurrentAudioOutput(string id){
	currentAudioOutput=id;
	if(audioOutput)
		audioOutput->SetCurrentDevice(id);
}

string VoIPController::GetCurrentAudioInputID(){
	return currentAudioInput;
}

string VoIPController::GetCurrentAudioOutputID(){
	return currentAudioOutput;
}

void VoIPController::SetProxy(int protocol, string address, uint16_t port, string username, string password){
	proxyProtocol=protocol;
	proxyAddress=std::move(address);
	proxyPort=port;
	proxyUsername=std::move(username);
	proxyPassword=std::move(password);
}

int VoIPController::GetSignalBarsCount(){
	return signalBarsHistory.NonZeroAverage();
}

void VoIPController::SetCallbacks(VoIPController::Callbacks callbacks){
	this->callbacks=callbacks;
	if(callbacks.connectionStateChanged)
		callbacks.connectionStateChanged(this, state);
}

void VoIPController::SetAudioOutputGainControlEnabled(bool enabled){
	LOGD("New output AGC state: %d", enabled);
}

uint32_t VoIPController::GetPeerCapabilities(){
	return peerCapabilities;
}

void VoIPController::SendGroupCallKey(unsigned char *key){
	Buffer buf(256);
	buf.CopyFrom(key, 0, 256);
	shared_ptr<Buffer> keyPtr=make_shared<Buffer>(move(buf));
	messageThread.Post([this, keyPtr]{
		if(!(peerCapabilities & TGVOIP_PEER_CAP_GROUP_CALLS)){
			LOGE("Tried to send group call key but peer isn't capable of them");
			return;
		}
		if(didSendGroupCallKey){
			LOGE("Tried to send a group call key repeatedly");
			return;
		}
		if(!isOutgoing){
			LOGE("You aren't supposed to send group call key in an incoming call, use VoIPController::RequestCallUpgrade() instead");
			return;
		}
		didSendGroupCallKey=true;
		SendExtra(*keyPtr, EXTRA_TYPE_GROUP_CALL_KEY);
	});
}

void VoIPController::RequestCallUpgrade(){
	messageThread.Post([this]{
		if(!(peerCapabilities & TGVOIP_PEER_CAP_GROUP_CALLS)){
			LOGE("Tried to send group call key but peer isn't capable of them");
			return;
		}
		if(didSendUpgradeRequest){
			LOGE("Tried to send upgrade request repeatedly");
			return;
		}
		if(isOutgoing){
			LOGE("You aren't supposed to send an upgrade request in an outgoing call, generate an encryption key and use VoIPController::SendGroupCallKey instead");
			return;
		}
		didSendUpgradeRequest=true;
		Buffer empty(0);
		SendExtra(empty, EXTRA_TYPE_REQUEST_GROUP);
	});
}

void VoIPController::SetEchoCancellationStrength(int strength){
	echoCancellationStrength=strength;
	if(echoCanceller)
		echoCanceller->SetAECStrength(strength);
}

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
void VoIPController::SetAudioDataCallbacks(std::function<void(int16_t*, size_t)> input, std::function<void(int16_t*, size_t)> output, std::function<void(int16_t*, size_t)> preproc=nullptr){
	audioInputDataCallback=input;
	audioOutputDataCallback=output;
    audioPreprocDataCallback=preproc;
    preprocDecoder=preprocDecoder ? preprocDecoder : opus_decoder_create(48000, 1, NULL);
}
#endif

int VoIPController::GetConnectionState(){
	return state;
}

void VoIPController::SetConfig(const Config& cfg){
	config=cfg;
	if(tgvoipLogFile){
		fclose(tgvoipLogFile);
		tgvoipLogFile=NULL;
	}
	if(!config.logFilePath.empty()){
#ifndef _WIN32
		tgvoipLogFile=fopen(config.logFilePath.c_str(), "a");
#else
		if(_wfopen_s(&tgvoipLogFile, config.logFilePath.c_str(), L"a")!=0){
			tgvoipLogFile=NULL;
		}
#endif
		tgvoip_log_file_write_header(tgvoipLogFile);
	}else{
		tgvoipLogFile=NULL;
	}
	if(statsDump){
		fclose(statsDump);
		statsDump=NULL;
	}
	if(!config.statsDumpFilePath.empty()){
#ifndef _WIN32
		statsDump=fopen(config.statsDumpFilePath.c_str(), "w");
#else
		if(_wfopen_s(&statsDump, config.statsDumpFilePath.c_str(), L"w")!=0){
			statsDump=NULL;
		}
#endif
		if(statsDump)
			fprintf(statsDump, "Time\tRTT\tLRSeq\tLSSeq\tLASeq\tLostR\tLostS\tCWnd\tBitrate\tLoss%%\tJitter\tJDelay\tAJDelay\n");
		//else
		//	LOGW("Failed to open stats dump file %s for writing", config.statsDumpFilePath.c_str());
	}else{
		statsDump=NULL;
	}
	UpdateDataSavingState();
	UpdateAudioBitrateLimit();
}

void VoIPController::SetPersistentState(vector<uint8_t> state){
	using namespace json11;

	if(state.empty())
		return;
	string jsonErr;
	string json=string(state.begin(), state.end());
	Json _obj=Json::parse(json, jsonErr);
	if(!jsonErr.empty()){
		LOGE("Error parsing persistable state: %s", jsonErr.c_str());
		return;
	}
	Json::object obj=_obj.object_items();
	if(obj.find("proxy")!=obj.end()){
		Json::object proxy=obj["proxy"].object_items();
		lastTestedProxyServer=proxy["server"].string_value();
		proxySupportsUDP=proxy["udp"].bool_value();
		proxySupportsTCP=proxy["tcp"].bool_value();
	}
}

vector<uint8_t> VoIPController::GetPersistentState(){
	using namespace json11;

	Json::object obj=Json::object{
		{"ver", 1},
	};
	if(proxyProtocol==PROXY_SOCKS5){
		char pbuf[128];
		snprintf(pbuf, sizeof(pbuf), "%s:%u", proxyAddress.c_str(), proxyPort);
    	obj.insert({"proxy", Json::object{
    		{"server", string(pbuf)},
			{"udp", proxySupportsUDP},
			{"tcp", proxySupportsTCP}
    	}});
	}
	string _jstr=Json(obj).dump();
	const char* jstr=_jstr.c_str();
	return vector<uint8_t>(jstr, jstr+strlen(jstr));
}

void VoIPController::SetOutputVolume(float level){
	outputVolume.SetLevel(level);
}

void VoIPController::SetInputVolume(float level){
	inputVolume.SetLevel(level);
}

#if defined(__APPLE__) && TARGET_OS_OSX
void VoIPController::SetAudioOutputDuckingEnabled(bool enabled){
	macAudioDuckingEnabled=enabled;
	audio::AudioUnitIO* osxAudio=dynamic_cast<audio::AudioUnitIO*>(audioIO);
	if(osxAudio){
		osxAudio->SetDuckingEnabled(enabled);
	}
}
#endif

#pragma mark - Internal intialization

void VoIPController::InitializeTimers(){
	initTimeoutID=messageThread.Post([this]{
		LOGW("Init timeout, disconnecting");
		lastError=ERROR_TIMEOUT;
		SetState(STATE_FAILED);
	}, config.initTimeout);

	if(!config.statsDumpFilePath.empty()){
		messageThread.Post([this]{
			if(statsDump && incomingStreams.size()==1){
				shared_ptr<JitterBuffer>& jitterBuffer=incomingStreams[0]->jitterBuffer;
				//fprintf(statsDump, "Time\tRTT\tLISeq\tLASeq\tCWnd\tBitrate\tJitter\tJDelay\tAJDelay\n");
				fprintf(statsDump, "%.3f\t%.3f\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.3f\t%.3f\t%.3f\n",
						GetCurrentTime()-connectionInitTime,
						endpoints.at(currentEndpoint).rtts[0],
						lastRemoteSeq,
						(uint32_t)seq,
						lastRemoteAckSeq,
						recvLossCount,
						conctl ? conctl->GetSendLossCount() : 0,
						conctl ? (int)conctl->GetInflightDataSize() : 0,
						encoder ? encoder->GetBitrate() : 0,
						encoder ? encoder->GetPacketLoss() : 0,
						jitterBuffer ? jitterBuffer->GetLastMeasuredJitter() : 0,
						jitterBuffer ? jitterBuffer->GetLastMeasuredDelay()*0.06 : 0,
						jitterBuffer ? jitterBuffer->GetAverageDelay()*0.06 : 0);
			}
		}, 0.1, 0.1);
	}

	messageThread.Post(std::bind(&VoIPController::SendRelayPings, this), 0.0, 2.0);
}

void VoIPController::RunSendThread(){
	InitializeAudio();
	InitializeTimers();
	messageThread.Post(bind(&VoIPController::SendInit, this));

	while(true){
		RawPendingOutgoingPacket pkt=rawSendQueue.GetBlocking();
		if(pkt.packet.IsEmpty())
			break;

		if(IS_MOBILE_NETWORK(networkType))
			stats.bytesSentMobile+=(uint64_t)pkt.packet.data.Length();
		else
			stats.bytesSentWifi+=(uint64_t)pkt.packet.data.Length();
		if(pkt.packet.protocol==NetworkProtocol::TCP){
			if(pkt.socket && !pkt.socket->IsFailed()){
				pkt.socket->Send(std::move(pkt.packet));
			}
		}else{
			udpSocket->Send(std::move(pkt.packet));
		}
	}

	LOGI("=== send thread exiting ===");
}

#pragma mark - Miscellaneous

void VoIPController::SetState(int state){
	this->state=state;
	LOGV("Call state changed to %d", state);
	stateChangeTime=GetCurrentTime();
	messageThread.Post([this, state]{
		if(callbacks.connectionStateChanged)
			callbacks.connectionStateChanged(this, state);
	});
	if(state==STATE_ESTABLISHED){
		SetMicMute(micMuted);
		if(!wasEstablished){
			wasEstablished=true;
			messageThread.Post(std::bind(&VoIPController::UpdateRTT, this), 0.1, 0.5);
			messageThread.Post(std::bind(&VoIPController::UpdateAudioBitrate, this), 0.0, 0.3);
			messageThread.Post(std::bind(&VoIPController::UpdateCongestion, this), 0.0, 1.0);
			messageThread.Post(std::bind(&VoIPController::UpdateSignalBars, this), 1.0, 1.0);
			messageThread.Post(std::bind(&VoIPController::TickJitterBufferAndCongestionControl, this), 0.0, 0.1);
		}
	}
}

void VoIPController::SendStreamFlags(Stream& stream){
	ENFORCE_MSG_THREAD;

	BufferOutputStream s(5);
	s.WriteByte(stream.id);
	uint32_t flags=0;
	if(stream.enabled)
		flags|=STREAM_FLAG_ENABLED;
	if(stream.extraECEnabled)
		flags|=STREAM_FLAG_EXTRA_EC;
	if(stream.paused)
		flags|=STREAM_FLAG_PAUSED;
	s.WriteInt32(flags);
	LOGV("My stream state: id %u flags %u", (unsigned int)stream.id, (unsigned int)flags);
	Buffer buf(move(s));
	SendExtra(buf, EXTRA_TYPE_STREAM_FLAGS);
}

shared_ptr<VoIPController::Stream> VoIPController::GetStreamByType(int type, bool outgoing){
	shared_ptr<Stream> s;
	for(shared_ptr<Stream>& ss:(outgoing ? outgoingStreams : incomingStreams)){
		if(ss->type==type)
			return ss;
	}
	return s;
}

shared_ptr<VoIPController::Stream> VoIPController::GetStreamByID(unsigned char id, bool outgoing){
	shared_ptr<Stream> s;
	for(shared_ptr<Stream>& ss:(outgoing ? outgoingStreams : incomingStreams)){
		if(ss->id==id)
			return ss;
	}
	return s;
}

CellularCarrierInfo VoIPController::GetCarrierInfo(){
#if defined(__APPLE__) && TARGET_OS_IOS
	return DarwinSpecific::GetCarrierInfo();
#elif defined(__ANDROID__)
	CellularCarrierInfo carrier;
	jni::DoWithJNI([&carrier](JNIEnv* env){
		jmethodID getCarrierInfoMethod=env->GetStaticMethodID(jniUtilitiesClass, "getCarrierInfo", "()[Ljava/lang/String;");
		jobjectArray jinfo=(jobjectArray) env->CallStaticObjectMethod(jniUtilitiesClass, getCarrierInfoMethod);
		if(jinfo && env->GetArrayLength(jinfo)==4){
			carrier.name=jni::JavaStringToStdString(env, (jstring)env->GetObjectArrayElement(jinfo, 0));
			carrier.countryCode=jni::JavaStringToStdString(env, (jstring)env->GetObjectArrayElement(jinfo, 1));
			carrier.mcc=jni::JavaStringToStdString(env, (jstring)env->GetObjectArrayElement(jinfo, 2));
			carrier.mnc=jni::JavaStringToStdString(env, (jstring)env->GetObjectArrayElement(jinfo, 3));
		}else{
			LOGW("Failed to get carrier info");
		}
	});
	return carrier;
#else
	return CellularCarrierInfo();
#endif
}

#pragma mark - Audio I/O

void VoIPController::HandleAudioInput(unsigned char *data, size_t len, unsigned char* secondaryData, size_t secondaryLen){
	if(stopping)
		return;

	// TODO make an AudioPacketSender

	Buffer dataBuf=outgoingAudioBufferPool.Get();
	Buffer secondaryDataBuf=secondaryLen && secondaryData ? outgoingAudioBufferPool.Get() : Buffer();
	dataBuf.CopyFrom(data, 0, len);
	if(secondaryLen && secondaryData){
		secondaryDataBuf.CopyFrom(secondaryData, 0, secondaryLen);
	}
	shared_ptr<Buffer> dataBufPtr=make_shared<Buffer>(move(dataBuf));
	shared_ptr<Buffer> secondaryDataBufPtr=make_shared<Buffer>(move(secondaryDataBuf));

	messageThread.Post([this, dataBufPtr, secondaryDataBufPtr, len, secondaryLen](){
		unsentStreamPacketsHistory.Add(static_cast<unsigned int>(unsentStreamPackets));
		if(unsentStreamPacketsHistory.Average()>=maxUnsentStreamPackets && !videoPacketSender){
			LOGW("Resetting stalled send queue");
			sendQueue.clear();
			unsentStreamPacketsHistory.Reset();
			unsentStreamPackets=0;
		}
		if(waitingForAcks || dontSendPackets>0 || ((unsigned int) unsentStreamPackets>=maxUnsentStreamPackets /*&& endpoints[currentEndpoint].type==Endpoint::Type::TCP_RELAY*/)){
			LOGV("waiting for queue, dropping outgoing audio packet, %d %d %d [%d]", (unsigned int) unsentStreamPackets, waitingForAcks, dontSendPackets, maxUnsentStreamPackets);
			return;
		}
		//LOGV("Audio packet size %u", (unsigned int)len);
		if(!receivedInitAck)
			return;

		BufferOutputStream pkt(1500);

		bool hasExtraFEC=peerVersion>=7 && secondaryLen && shittyInternetMode;
		unsigned char flags=(unsigned char) (len>255 || hasExtraFEC ? STREAM_DATA_FLAG_LEN16 : 0);
		pkt.WriteByte((unsigned char) (1 | flags)); // streamID + flags
		if(len>255 || hasExtraFEC){
			int16_t lenAndFlags=static_cast<int16_t>(len);
			if(hasExtraFEC)
				lenAndFlags|=STREAM_DATA_XFLAG_EXTRA_FEC;
			pkt.WriteInt16(lenAndFlags);
		}else{
			pkt.WriteByte((unsigned char) len);
		}
		pkt.WriteInt32(audioTimestampOut);
		pkt.WriteBytes(*dataBufPtr, 0, len);

		if(hasExtraFEC){
			pkt.WriteByte((unsigned char) std::min((int) ecAudioPackets.size(), extraEcLevel));
			for(vector<Buffer>::iterator ecData=ecAudioPackets.begin()+std::max(0, (int) ecAudioPackets.size()-extraEcLevel); ecData!=ecAudioPackets.end(); ++ecData){
				pkt.WriteByte((unsigned char) ecData->Length());
				pkt.WriteBytes(*ecData);
			}
      Buffer ecBuf(secondaryLen);
      ecBuf.CopyFrom(**secondaryDataBufPtr, 0, secondaryLen);
      ecAudioPackets.push_back(move(ecBuf));
      while(ecAudioPackets.size()>4)
              ecAudioPackets.erase(ecAudioPackets.begin());
		}

		unsentStreamPackets++;
		PendingOutgoingPacket p{
				/*.seq=*/GenerateOutSeq(),
				/*.type=*/PKT_STREAM_DATA,
				/*.len=*/pkt.GetLength(),
				/*.data=*/Buffer(move(pkt)),
				/*.endpoint=*/0,
		};

		conctl->PacketSent(p.seq, p.len);

		SendOrEnqueuePacket(move(p));
		if(peerVersion<7 && secondaryLen && shittyInternetMode){
			Buffer ecBuf(secondaryLen);
			ecBuf.CopyFrom(*secondaryDataBufPtr, 0, secondaryLen);
			ecAudioPackets.push_back(move(ecBuf));
			while(ecAudioPackets.size()>4)
				ecAudioPackets.erase(ecAudioPackets.begin());
			pkt=BufferOutputStream(1500);
			pkt.WriteByte(outgoingStreams[0]->id);
			pkt.WriteInt32(audioTimestampOut);
			pkt.WriteByte((unsigned char) std::min((int) ecAudioPackets.size(), extraEcLevel));
			for(vector<Buffer>::iterator ecData=ecAudioPackets.begin()+std::max(0, (int) ecAudioPackets.size()-extraEcLevel); ecData!=ecAudioPackets.end(); ++ecData){
				pkt.WriteByte((unsigned char) ecData->Length());
				pkt.WriteBytes(*ecData);
			}

			PendingOutgoingPacket p{
					GenerateOutSeq(),
					PKT_STREAM_EC,
					pkt.GetLength(),
					Buffer(move(pkt)),
					0
			};
			SendOrEnqueuePacket(move(p));
		}

		audioTimestampOut+=outgoingStreams[0]->frameDuration;
	});

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
	if (audioPreprocDataCallback && preprocDecoder) {
        int size=opus_decode(preprocDecoder, data, len, preprocBuffer, 4096, 0);
        audioPreprocDataCallback(preprocBuffer, size);
	}
#endif
}

void VoIPController::InitializeAudio(){
	double t=GetCurrentTime();
	shared_ptr<Stream> outgoingAudioStream=GetStreamByType(STREAM_TYPE_AUDIO, true);
	LOGI("before create audio io");
	audioIO=audio::AudioIO::Create(currentAudioInput, currentAudioOutput);
	audioInput=audioIO->GetInput();
	audioOutput=audioIO->GetOutput();
#ifdef __ANDROID__
	audio::AudioInputAndroid* androidInput=dynamic_cast<audio::AudioInputAndroid*>(audioInput);
	if(androidInput){
		unsigned int effects=androidInput->GetEnabledEffects();
		if(!(effects & audio::AudioInputAndroid::EFFECT_AEC)){
			config.enableAEC=true;
			LOGI("Forcing software AEC because built-in is not good");
		}
		if(!(effects & audio::AudioInputAndroid::EFFECT_NS)){
			config.enableNS=true;
			LOGI("Forcing software NS because built-in is not good");
		}
	}
#elif defined(__APPLE__) && TARGET_OS_OSX
	SetAudioOutputDuckingEnabled(macAudioDuckingEnabled);
#endif
	LOGI("AEC: %d NS: %d AGC: %d", config.enableAEC, config.enableNS, config.enableAGC);
	echoCanceller=new EchoCanceller(config.enableAEC, config.enableNS, config.enableAGC);
	encoder=new OpusEncoder(audioInput, true);
	encoder->SetCallback(bind(&VoIPController::HandleAudioInput, this, placeholders::_1, placeholders::_2, placeholders::_3, placeholders::_4));
	encoder->SetOutputFrameDuration(outgoingAudioStream->frameDuration);
	encoder->SetEchoCanceller(echoCanceller);
	encoder->SetSecondaryEncoderEnabled(false);
	if(config.enableVolumeControl){
		encoder->AddAudioEffect(&inputVolume);
	}

#if defined(TGVOIP_USE_CALLBACK_AUDIO_IO)
	dynamic_cast<audio::AudioInputCallback*>(audioInput)->SetDataCallback(audioInputDataCallback);
	dynamic_cast<audio::AudioOutputCallback*>(audioOutput)->SetDataCallback(audioOutputDataCallback);
#endif

	if(!audioOutput->IsInitialized()){
		LOGE("Error initializing audio playback");
		lastError=ERROR_AUDIO_IO;

		SetState(STATE_FAILED);
		return;
	}
	UpdateAudioBitrateLimit();
	LOGI("Audio initialization took %f seconds", GetCurrentTime()-t);
}

void VoIPController::StartAudio(){
	OnAudioOutputReady();

	encoder->Start();
	if(!micMuted){
		audioInput->Start();
		if(!audioInput->IsInitialized()){
			LOGE("Error initializing audio capture");
			lastError=ERROR_AUDIO_IO;

			SetState(STATE_FAILED);
			return;
		}
	}
}

void VoIPController::OnAudioOutputReady(){
	LOGI("Audio I/O ready");
	shared_ptr<Stream>& stm=incomingStreams[0];
	stm->decoder=make_shared<OpusDecoder>(audioOutput, true, peerVersion>=6);
	stm->decoder->SetEchoCanceller(echoCanceller);
	if(config.enableVolumeControl){
		stm->decoder->AddAudioEffect(&outputVolume);
	}
	stm->decoder->SetJitterBuffer(stm->jitterBuffer);
	stm->decoder->SetFrameDuration(stm->frameDuration);
	stm->decoder->Start();
}

void VoIPController::UpdateAudioOutputState(){
	bool areAnyAudioStreamsEnabled=false;
	for(vector<shared_ptr<Stream>>::iterator s=incomingStreams.begin();s!=incomingStreams.end();++s){
		if((*s)->type==STREAM_TYPE_AUDIO && (*s)->enabled)
			areAnyAudioStreamsEnabled=true;
	}
	if(audioOutput){
		LOGV("New audio output state: %d", areAnyAudioStreamsEnabled);
		if(audioOutput->IsPlaying()!=areAnyAudioStreamsEnabled){
			if(areAnyAudioStreamsEnabled)
				audioOutput->Start();
			else
				audioOutput->Stop();
		}
	}
}

#pragma mark - Bandwidth management

void VoIPController::UpdateAudioBitrateLimit(){
	if(encoder){
		if(dataSavingMode || dataSavingRequestedByPeer){
			maxBitrate=maxAudioBitrateSaving;
			encoder->SetBitrate(initAudioBitrateSaving);
		}else if(networkType==NET_TYPE_GPRS){
			maxBitrate=maxAudioBitrateGPRS;
			encoder->SetBitrate(initAudioBitrateGPRS);
		}else if(networkType==NET_TYPE_EDGE){
			maxBitrate=maxAudioBitrateEDGE;
			encoder->SetBitrate(initAudioBitrateEDGE);
		}else{
			maxBitrate=maxAudioBitrate;
			encoder->SetBitrate(initAudioBitrate);
		}
		encoder->SetVadMode(dataSavingMode || dataSavingRequestedByPeer);
		if(echoCanceller)
			echoCanceller->SetVoiceDetectionEnabled(dataSavingMode || dataSavingRequestedByPeer);
	}
}

void VoIPController::UpdateDataSavingState(){
	if(config.dataSaving==DATA_SAVING_ALWAYS){
		dataSavingMode=true;
	}else if(config.dataSaving==DATA_SAVING_MOBILE){
		dataSavingMode=networkType==NET_TYPE_GPRS || networkType==NET_TYPE_EDGE ||
					   networkType==NET_TYPE_3G || networkType==NET_TYPE_HSPA || networkType==NET_TYPE_LTE || networkType==NET_TYPE_OTHER_MOBILE;
	}else{
		dataSavingMode=false;
	}
	LOGI("update data saving mode, config %d, enabled %d, reqd by peer %d", config.dataSaving, dataSavingMode, dataSavingRequestedByPeer);
}

#pragma mark - Networking & crypto

uint32_t VoIPController::GenerateOutSeq(){
	return seq++;
}

void VoIPController::WritePacketHeader(uint32_t pseq, BufferOutputStream *s, unsigned char type, uint32_t length, PacketSender* source){
	uint32_t acks=0;
	int i;
	for(i=0;i<32;i++){
		if(find(recentIncomingPackets.begin(), recentIncomingPackets.end(), lastRemoteSeq-(i+1))!=recentIncomingPackets.end()){
			acks |= (1 << (31-i));
		}
	}

	if(peerVersion>=8 || (!peerVersion && connectionMaxLayer>=92)){
		s->WriteByte(type);
		s->WriteInt32(lastRemoteSeq);
		s->WriteInt32(pseq);
		s->WriteInt32(acks);
		unsigned char flags;
		if(currentExtras.empty()){
			flags=0;
		}else{
			flags=XPFLAG_HAS_EXTRA;
		}

		shared_ptr<Stream> videoStream=GetStreamByType(STREAM_TYPE_VIDEO, false);
		if(peerVersion>=9 && videoStream && videoStream->enabled)
			flags |= XPFLAG_HAS_RECV_TS;

		s->WriteByte(flags);

		if(!currentExtras.empty()){
			s->WriteByte(static_cast<unsigned char>(currentExtras.size()));
			for(vector<UnacknowledgedExtraData>::iterator x=currentExtras.begin(); x!=currentExtras.end(); ++x){
				LOGV("Writing extra into header: type %u, length %d", x->type, int(x->data.Length()));
				assert(x->data.Length()<=254);
				s->WriteByte(static_cast<unsigned char>(x->data.Length()+1));
				s->WriteByte(x->type);
				s->WriteBytes(*x->data, x->data.Length());
				if(x->firstContainingSeq==0)
					x->firstContainingSeq=pseq;
			}
		}
		if(peerVersion>=9 && videoStream && videoStream->enabled){
			s->WriteInt32((uint32_t)((lastRecvPacketTime-connectionInitTime)*1000.0));
		}
	}else{
		if(state==STATE_WAIT_INIT || state==STATE_WAIT_INIT_ACK){
			s->WriteInt32(TLID_DECRYPTED_AUDIO_BLOCK);
			int64_t randomID;
			crypto.rand_bytes((uint8_t *) &randomID, 8);
			s->WriteInt64(randomID);
			unsigned char randBytes[7];
			crypto.rand_bytes(randBytes, 7);
			s->WriteByte(7);
			s->WriteBytes(randBytes, 7);
			uint32_t pflags=PFLAG_HAS_RECENT_RECV | PFLAG_HAS_SEQ;
			if(length>0)
				pflags|=PFLAG_HAS_DATA;
			if(state==STATE_WAIT_INIT || state==STATE_WAIT_INIT_ACK){
				pflags|=PFLAG_HAS_CALL_ID | PFLAG_HAS_PROTO;
			}
			pflags|=((uint32_t) type) << 24;
			s->WriteInt32(pflags);

			if(pflags & PFLAG_HAS_CALL_ID){
				s->WriteBytes(callID, 16);
			}
			s->WriteInt32(lastRemoteSeq);
			s->WriteInt32(pseq);
			s->WriteInt32(acks);
			if(pflags & PFLAG_HAS_PROTO){
				s->WriteInt32(PROTOCOL_NAME);
			}
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
		}else{
			s->WriteInt32(TLID_SIMPLE_AUDIO_BLOCK);
			int64_t randomID;
			crypto.rand_bytes((uint8_t *) &randomID, 8);
			s->WriteInt64(randomID);
			unsigned char randBytes[7];
			crypto.rand_bytes(randBytes, 7);
			s->WriteByte(7);
			s->WriteBytes(randBytes, 7);
			uint32_t lenWithHeader=length+13;
			if(lenWithHeader>0){
				if(lenWithHeader<=253){
					s->WriteByte((unsigned char) lenWithHeader);
				}else{
					s->WriteByte(254);
					s->WriteByte((unsigned char) (lenWithHeader & 0xFF));
					s->WriteByte((unsigned char) ((lenWithHeader >> 8) & 0xFF));
					s->WriteByte((unsigned char) ((lenWithHeader >> 16) & 0xFF));
				}
			}
			s->WriteByte(type);
			s->WriteInt32(lastRemoteSeq);
			s->WriteInt32(pseq);
			s->WriteInt32(acks);
			if(peerVersion>=6){
				if(currentExtras.empty()){
					s->WriteByte(0);
				}else{
					s->WriteByte(XPFLAG_HAS_EXTRA);
					s->WriteByte(static_cast<unsigned char>(currentExtras.size()));
					for(vector<UnacknowledgedExtraData>::iterator x=currentExtras.begin(); x!=currentExtras.end(); ++x){
						LOGV("Writing extra into header: type %u, length %d", x->type, int(x->data.Length()));
						assert(x->data.Length()<=254);
						s->WriteByte(static_cast<unsigned char>(x->data.Length()+1));
						s->WriteByte(x->type);
						s->WriteBytes(*x->data, x->data.Length());
						if(x->firstContainingSeq==0)
							x->firstContainingSeq=pseq;
					}
				}
			}
		}
	}


	unacknowledgedIncomingPacketCount=0;
	recentOutgoingPackets.push_back(RecentOutgoingPacket{
			pseq,
			0,
			GetCurrentTime(),
			0,
			type,
			length,
			source,
			false
	});
	while(recentOutgoingPackets.size()>MAX_RECENT_PACKETS){
		recentOutgoingPackets.erase(recentOutgoingPackets.begin());
	}
	lastSentSeq=pseq;
	//LOGI("packet header size %d", s->GetLength());
}



void VoIPController::SendInit(){
	ENFORCE_MSG_THREAD;

	uint32_t initSeq=GenerateOutSeq();
	for(pair<const int64_t, Endpoint>& _e:endpoints){
		Endpoint& e=_e.second;
		if(e.type==Endpoint::Type::TCP_RELAY && !useTCP)
			continue;
		BufferOutputStream out(1024);
		out.WriteInt32(PROTOCOL_VERSION);
		out.WriteInt32(MIN_PROTOCOL_VERSION);
		uint32_t flags=0;
		if(config.enableCallUpgrade)
			flags|=INIT_FLAG_GROUP_CALLS_SUPPORTED;
		if(config.enableVideoReceive)
			flags|=INIT_FLAG_VIDEO_RECV_SUPPORTED;
		if(config.enableVideoSend)
			flags|=INIT_FLAG_VIDEO_SEND_SUPPORTED;
		if(dataSavingMode)
			flags|=INIT_FLAG_DATA_SAVING_ENABLED;
		out.WriteInt32(flags);
		if(connectionMaxLayer<74){
			out.WriteByte(2); // audio codecs count
			out.WriteByte(CODEC_OPUS_OLD);
			out.WriteByte(0);
			out.WriteByte(0);
			out.WriteByte(0);
			out.WriteInt32(CODEC_OPUS);
			out.WriteByte(0); // video codecs count (decode)
			out.WriteByte(0); // video codecs count (encode)
		}else{
			out.WriteByte(1);
			out.WriteInt32(CODEC_OPUS);
			vector<uint32_t> decoders=config.enableVideoReceive ? video::VideoRenderer::GetAvailableDecoders() : vector<uint32_t>();
			vector<uint32_t> encoders=config.enableVideoSend ? video::VideoSource::GetAvailableEncoders() : vector<uint32_t>();
			out.WriteByte((unsigned char)decoders.size());
			for(uint32_t id:decoders){
				out.WriteInt32(id);
			}
			if(connectionMaxLayer>=92)
				out.WriteByte((unsigned char)video::VideoRenderer::GetMaximumResolution());
			else
				out.WriteByte(0);
		}
		SendOrEnqueuePacket(PendingOutgoingPacket{
				/*.seq=*/initSeq,
				/*.type=*/PKT_INIT,
				/*.len=*/out.GetLength(),
				/*.data=*/Buffer(move(out)),
				/*.endpoint=*/e.id
		});
	}

	if(state==STATE_WAIT_INIT)
		SetState(STATE_WAIT_INIT_ACK);
	messageThread.Post([this]{
		if(state==STATE_WAIT_INIT_ACK){
			SendInit();
		}
	}, 0.5);
}

void VoIPController::InitUDPProxy(){
	if(realUdpSocket!=udpSocket){
		udpSocket->Close();
		delete udpSocket;
		udpSocket=realUdpSocket;
	}
	char sbuf[128];
	snprintf(sbuf, sizeof(sbuf), "%s:%u", proxyAddress.c_str(), proxyPort);
	string proxyHostPort(sbuf);
	if(proxyHostPort==lastTestedProxyServer && !proxySupportsUDP){
		LOGI("Proxy does not support UDP - using UDP directly instead");
        messageThread.Post(bind(&VoIPController::ResetUdpAvailability, this));
		return;
	}
	
	NetworkSocket* tcp=NetworkSocket::Create(NetworkProtocol::TCP);
	tcp->Connect(resolvedProxyAddress, proxyPort);

	vector<NetworkSocket*> writeSockets;
	vector<NetworkSocket*> readSockets;
	vector<NetworkSocket*> errorSockets;

	while(!tcp->IsFailed() && !tcp->IsReadyToSend()){
		writeSockets.push_back(tcp);
		if(!NetworkSocket::Select(readSockets, writeSockets, errorSockets, selectCanceller)){
			LOGW("Select canceled while waiting for proxy control socket to connect");
			delete tcp;
			return;
		}
	}
	LOGV("UDP proxy control socket ready to send");
    NetworkSocketSOCKS5Proxy* udpProxy=new NetworkSocketSOCKS5Proxy(tcp, realUdpSocket, proxyUsername, proxyPassword);
	udpProxy->OnReadyToSend();
	writeSockets.clear();
	while(!udpProxy->IsFailed() && !tcp->IsFailed() && !udpProxy->IsReadyToSend()){
		readSockets.clear();
		errorSockets.clear();
		readSockets.push_back(tcp);
		errorSockets.push_back(tcp);
		if(!NetworkSocket::Select(readSockets, writeSockets, errorSockets, selectCanceller)){
			LOGW("Select canceled while waiting for UDP proxy to initialize");
			delete udpProxy;
			return;
		}
		if(!readSockets.empty())
			udpProxy->OnReadyToReceive();
	}
	LOGV("UDP proxy initialized");

	if(udpProxy->IsFailed()){
		udpProxy->Close();
		delete udpProxy;
		proxySupportsUDP=false;
	}else{
		udpSocket=udpProxy;
	}
	messageThread.Post(bind(&VoIPController::ResetUdpAvailability, this));
}

void VoIPController::RunRecvThread(){
	LOGI("Receive thread starting");
	if(proxyProtocol==PROXY_SOCKS5){
		resolvedProxyAddress=NetworkSocket::ResolveDomainName(proxyAddress);
		if(resolvedProxyAddress.IsEmpty()){
			LOGW("Error resolving proxy address %s", proxyAddress.c_str());
			SetState(STATE_FAILED);
			return;
		}
	}else{
		udpConnectivityState=UDP_PING_PENDING;
		udpPingTimeoutID=messageThread.Post(std::bind(&VoIPController::SendUdpPings, this), 0.0, 0.5);
	}
	while(runReceiver){

		if(proxyProtocol==PROXY_SOCKS5 && needReInitUdpProxy){
			InitUDPProxy();
			needReInitUdpProxy=false;
		}

		vector<NetworkSocket*> readSockets;
		vector<NetworkSocket*> errorSockets;
		vector<NetworkSocket*> writeSockets;
		readSockets.push_back(udpSocket);
		errorSockets.push_back(realUdpSocket);
		if(!realUdpSocket->IsReadyToSend())
			writeSockets.push_back(realUdpSocket);

		{
			MutexGuard m(endpointsMutex);
			for(pair<const int64_t, Endpoint>& _e:endpoints){
				const Endpoint& e=_e.second;
				if(e.type==Endpoint::Type::TCP_RELAY){
					if(e.socket){
						readSockets.push_back(&*e.socket);
						errorSockets.push_back(&*e.socket);
						if(!e.socket->IsReadyToSend()){
							NetworkSocketSOCKS5Proxy* proxy=dynamic_cast<NetworkSocketSOCKS5Proxy*>(&*e.socket);
							if(!proxy || proxy->NeedSelectForSending())
    							writeSockets.push_back(&*e.socket);
						}
					}
				}
			}
		}

		{
			bool selRes=NetworkSocket::Select(readSockets, writeSockets, errorSockets, selectCanceller);
			if(!selRes){
				LOGV("Select canceled");
				continue;
			}
		}
		if(!runReceiver)
			return;

		if(!errorSockets.empty()){
			if(find(errorSockets.begin(), errorSockets.end(), realUdpSocket)!=errorSockets.end()){
				LOGW("UDP socket failed");
				SetState(STATE_FAILED);
				return;
			}
			MutexGuard m(endpointsMutex);
			for(NetworkSocket*& socket:errorSockets){
				for(pair<const int64_t, Endpoint>& _e:endpoints){
					Endpoint& e=_e.second;
					if(e.socket && &*e.socket==socket){
						e.socket->Close();
						e.socket.reset();
						LOGI("Closing failed TCP socket for %s:%u", e.GetAddress().ToString().c_str(), e.port);
					}
				}
			}
			continue;
		}

		for(NetworkSocket*& socket:readSockets){
			//while(packet.length){
            NetworkPacket packet=socket->Receive();
			if(packet.address.IsEmpty()){
				LOGE("Packet has null address. This shouldn't happen.");
				continue;
			}
			if(packet.data.IsEmpty()){
				LOGE("Packet has zero length.");
				continue;
			}
			//LOGV("Received %d bytes from %s:%d at %.5lf", len, packet.address->ToString().c_str(), packet.port, GetCurrentTime());
			messageThread.Post(bind(&VoIPController::NetworkPacketReceived, this, make_shared<NetworkPacket>(move(packet))));
		}

		if(!writeSockets.empty()){
			messageThread.Post(bind(&VoIPController::TrySendQueuedPackets, this));
		}
	}
	LOGI("=== recv thread exiting ===");
}

void VoIPController::TrySendQueuedPackets(){
	ENFORCE_MSG_THREAD;

	for(vector<PendingOutgoingPacket>::iterator opkt=sendQueue.begin();opkt!=sendQueue.end();){
		Endpoint* endpoint=GetEndpointForPacket(*opkt);
		if(!endpoint){
			opkt=sendQueue.erase(opkt);
			LOGE("SendQueue contained packet for nonexistent endpoint");
			continue;
		}
		bool canSend;
		if(endpoint->type!=Endpoint::Type::TCP_RELAY)
			canSend=realUdpSocket->IsReadyToSend();
		else
			canSend=endpoint->socket && endpoint->socket->IsReadyToSend();
		if(canSend){
			LOGI("Sending queued packet");
			SendOrEnqueuePacket(move(*opkt), false);
			opkt=sendQueue.erase(opkt);
		}else{
			++opkt;
		}
	}
}

bool VoIPController::WasOutgoingPacketAcknowledged(uint32_t seq){
	RecentOutgoingPacket* pkt=GetRecentOutgoingPacket(seq);
	if(!pkt)
		return false;
	return pkt->ackTime!=0.0;
}

VoIPController::RecentOutgoingPacket *VoIPController::GetRecentOutgoingPacket(uint32_t seq){
	for(RecentOutgoingPacket& opkt:recentOutgoingPackets){
		if(opkt.seq==seq){
			return &opkt;
		}
	}
	return NULL;
}

void VoIPController::NetworkPacketReceived(shared_ptr<NetworkPacket> _packet){
	ENFORCE_MSG_THREAD;

	NetworkPacket& packet=*_packet;

	int64_t srcEndpointID=0;

	if(!packet.address.isIPv6){
		for(pair<const int64_t, Endpoint>& _e:endpoints){
			const Endpoint& e=_e.second;
			if(e.address==packet.address && e.port==packet.port){
				if((e.type!=Endpoint::Type::TCP_RELAY && packet.protocol==NetworkProtocol::UDP) || (e.type==Endpoint::Type::TCP_RELAY && packet.protocol==NetworkProtocol::TCP)){
					srcEndpointID=e.id;
					break;
				}
			}
		}
		if(!srcEndpointID && packet.protocol==NetworkProtocol::UDP){
			try{
				Endpoint &p2p=GetEndpointByType(Endpoint::Type::UDP_P2P_INET);
				if(p2p.rtts[0]==0.0 && p2p.address.PrefixMatches(24, packet.address)){
					LOGD("Packet source matches p2p endpoint partially: %s:%u", packet.address.ToString().c_str(), packet.port);
					srcEndpointID=p2p.id;
				}
			}catch(out_of_range& ex){}
		}
	}else{
		for(pair<const int64_t, Endpoint> &_e:endpoints){
			const Endpoint& e=_e.second;
			if(e.v6address==packet.address && e.port==packet.port && e.IsIPv6Only()){
				if((e.type!=Endpoint::Type::TCP_RELAY && packet.protocol==NetworkProtocol::UDP) || (e.type==Endpoint::Type::TCP_RELAY && packet.protocol==NetworkProtocol::TCP)){
					srcEndpointID=e.id;
					break;
				}
			}
		}
	}

	if(!srcEndpointID){
		LOGW("Received a packet from unknown source %s:%u", packet.address.ToString().c_str(), packet.port);
		return;
	}
	/*if(len<=0){
		//LOGW("error receiving: %d / %s", errno, strerror(errno));
		continue;
	}*/
	if(IS_MOBILE_NETWORK(networkType))
		stats.bytesRecvdMobile+=(uint64_t) packet.data.Length();
	else
		stats.bytesRecvdWifi+=(uint64_t) packet.data.Length();
	try{
		ProcessIncomingPacket(packet, endpoints.at(srcEndpointID));
	}catch(out_of_range& x){
		LOGW("Error parsing packet: %s", x.what());
	}
}

void VoIPController::ProcessIncomingPacket(NetworkPacket &packet, Endpoint& srcEndpoint){
	ENFORCE_MSG_THREAD;

	unsigned char *buffer=*packet.data;
	size_t len=packet.data.Length();
	BufferInputStream in(packet.data);
	bool hasPeerTag=false;
	if(peerVersion<9 || srcEndpoint.type==Endpoint::Type::UDP_RELAY || srcEndpoint.type==Endpoint::Type::TCP_RELAY){
		if(memcmp(buffer, srcEndpoint.type==Endpoint::Type::UDP_RELAY || srcEndpoint.type==Endpoint::Type::TCP_RELAY ? (void *) srcEndpoint.peerTag : (void *) callID, 16)!=0){
			LOGW("Received packet has wrong peerTag");
			return;
		}
		in.Seek(16);
		hasPeerTag=true;
	}
	if(in.Remaining()>=16 && (srcEndpoint.type==Endpoint::Type::UDP_RELAY || srcEndpoint.type==Endpoint::Type::TCP_RELAY)
	   && *reinterpret_cast<uint64_t *>(buffer+16)==0xFFFFFFFFFFFFFFFFLL && *reinterpret_cast<uint32_t *>(buffer+24)==0xFFFFFFFF){
		// relay special request response
		in.Seek(16+12);
		uint32_t tlid=(uint32_t) in.ReadInt32();

		if(tlid==TLID_UDP_REFLECTOR_SELF_INFO){
			if(srcEndpoint.type==Endpoint::Type::UDP_RELAY /*&& udpConnectivityState==UDP_PING_SENT*/ && in.Remaining()>=32){
				int32_t date=in.ReadInt32();
				int64_t queryID=in.ReadInt64();
				unsigned char myIP[16];
				in.ReadBytes(myIP, 16);
				int32_t myPort=in.ReadInt32();
				//udpConnectivityState=UDP_AVAILABLE;
				double selfRTT=0.0;
				srcEndpoint.udpPongCount++;
				srcEndpoint.totalUdpPingReplies++;
				if(srcEndpoint.udpPingTimes.find(queryID)!=srcEndpoint.udpPingTimes.end()){
					double sendTime=srcEndpoint.udpPingTimes[queryID];
					srcEndpoint.udpPingTimes.erase(queryID);
					srcEndpoint.selfRtts.Add(selfRTT=GetCurrentTime()-sendTime);
				}
				LOGV("Received UDP ping reply from %s:%d: date=%d, queryID=%ld, my IP=%s, my port=%d, selfRTT=%f", srcEndpoint.address.ToString().c_str(), srcEndpoint.port, date, (long int) queryID, NetworkAddress::IPv4(*reinterpret_cast<uint32_t *>(myIP+12)).ToString().c_str(), myPort, selfRTT);
				if(srcEndpoint.IsIPv6Only() && !didSendIPv6Endpoint){
					NetworkAddress realAddr=NetworkAddress::IPv6(myIP);
					if(realAddr==myIPv6){
						LOGI("Public IPv6 matches local address");
						useIPv6=true;
						if(allowP2p){
							didSendIPv6Endpoint=true;
							BufferOutputStream o(18);
							o.WriteBytes(myIP, 16);
							o.WriteInt16(udpSocket->GetLocalPort());
							Buffer b(move(o));
							SendExtra(b, EXTRA_TYPE_IPV6_ENDPOINT);
						}
					}
				}
			}
		}else if(tlid==TLID_UDP_REFLECTOR_PEER_INFO){
			if(in.Remaining()>=16){
				uint32_t myAddr=(uint32_t) in.ReadInt32();
				uint32_t myPort=(uint32_t) in.ReadInt32();
				uint32_t peerAddr=(uint32_t) in.ReadInt32();
				uint32_t peerPort=(uint32_t) in.ReadInt32();

				constexpr int64_t p2pID=(int64_t) (FOURCC('P', '2', 'P', '4')) << 32;
				constexpr int64_t lanID=(int64_t) (FOURCC('L', 'A', 'N', '4')) << 32;

				if(currentEndpoint==p2pID || currentEndpoint==lanID)
					currentEndpoint=preferredRelay;

				if(endpoints.find(lanID)!=endpoints.end()){
					MutexGuard m(endpointsMutex);
					endpoints.erase(lanID);
				}

				unsigned char peerTag[16];
				LOGW("Received reflector peer info, my=%s:%u, peer=%s:%u", NetworkAddress::IPv4(myAddr).ToString().c_str(), myPort, NetworkAddress::IPv4(peerAddr).ToString().c_str(), peerPort);
				if(waitingForRelayPeerInfo){
					Endpoint p2p(p2pID, (uint16_t) peerPort, NetworkAddress::IPv4(peerAddr), NetworkAddress::Empty(), Endpoint::Type::UDP_P2P_INET, peerTag);
					{
    					MutexGuard m(endpointsMutex);
						endpoints[p2pID]=p2p;
					}
					if(myAddr==peerAddr){
						LOGW("Detected LAN");
						NetworkAddress lanAddr=NetworkAddress::IPv4(0);
						udpSocket->GetLocalInterfaceInfo(&lanAddr, NULL);

						BufferOutputStream pkt(8);
						pkt.WriteInt32(lanAddr.addr.ipv4);
						pkt.WriteInt32(udpSocket->GetLocalPort());
						if(peerVersion<6){
							SendPacketReliably(PKT_LAN_ENDPOINT, pkt.GetBuffer(), pkt.GetLength(), 0.5, 10);
						}else{
							Buffer buf(move(pkt));
							SendExtra(buf, EXTRA_TYPE_LAN_ENDPOINT);
						}
					}
					waitingForRelayPeerInfo=false;
				}
			}
		}else{
			LOGV("Received relay response with unknown tl id: 0x%08X", tlid);
		}
		return;
	}
	if(in.Remaining()<40){
		LOGV("Received packet is too small");
		return;
	}

	bool retryWith2=false;
	size_t innerLen=0;
	bool shortFormat=peerVersion>=8 || (!peerVersion && connectionMaxLayer>=92);

	if(!useMTProto2){
		unsigned char fingerprint[8], msgHash[16];
		in.ReadBytes(fingerprint, 8);
		in.ReadBytes(msgHash, 16);
		unsigned char key[32], iv[32];
		KDF(msgHash, isOutgoing ? 8 : 0, key, iv);
		unsigned char aesOut[MSC_STACK_FALLBACK(in.Remaining(), 1500)];
		if(in.Remaining()>sizeof(aesOut))
			return;
		crypto.aes_ige_decrypt((unsigned char *) buffer+in.GetOffset(), aesOut, in.Remaining(), key, iv);
		BufferInputStream _in(aesOut, in.Remaining());
		unsigned char sha[SHA1_LENGTH];
		uint32_t _len=(uint32_t) _in.ReadInt32();
		if(_len>_in.Remaining())
			_len=(uint32_t) _in.Remaining();
        crypto.sha1((uint8_t *) (aesOut), static_cast<size_t>(_len)+4, sha);
		if(memcmp(msgHash, sha+(SHA1_LENGTH-16), 16)!=0){
			LOGW("Received packet has wrong hash after decryption");
			if(state==STATE_WAIT_INIT || state==STATE_WAIT_INIT_ACK)
				retryWith2=true;
			else
				return;
		}else{
			memcpy(buffer+in.GetOffset(), aesOut, in.Remaining());
			in.ReadInt32();
		}
	}

	if(useMTProto2 || retryWith2){
		if(hasPeerTag)
			in.Seek(16); // peer tag

		unsigned char fingerprint[8], msgKey[16];
		if(!shortFormat){
			in.ReadBytes(fingerprint, 8);
			if(memcmp(fingerprint, keyFingerprint, 8)!=0){
				LOGW("Received packet has wrong key fingerprint");
				return;
			}
		}
		in.ReadBytes(msgKey, 16);

		unsigned char decrypted[1500];
		unsigned char aesKey[32], aesIv[32];
		KDF2(msgKey, isOutgoing ? 8 : 0, aesKey, aesIv);
		size_t decryptedLen=in.Remaining();
		if(decryptedLen>sizeof(decrypted))
			return;
		if(decryptedLen%16!=0){
			LOGW("wrong decrypted length");
			return;
		}

		crypto.aes_ige_decrypt(*packet.data+in.GetOffset(), decrypted, decryptedLen, aesKey, aesIv);

		in=BufferInputStream(decrypted, decryptedLen);
		//LOGD("received packet length: %d", in.ReadInt32());
		size_t sizeSize=shortFormat ? 0 : 4;

		BufferOutputStream buf(decryptedLen+32);
		size_t x=isOutgoing ? 8 : 0;
		buf.WriteBytes(encryptionKey+88+x, 32);
		buf.WriteBytes(decrypted+sizeSize, decryptedLen-sizeSize);
		unsigned char msgKeyLarge[32];
		crypto.sha256(buf.GetBuffer(), buf.GetLength(), msgKeyLarge);

		if(memcmp(msgKey, msgKeyLarge+8, 16)!=0){
			LOGW("Received packet has wrong hash");
			return;
		}

		innerLen=(uint32_t) (shortFormat ? in.ReadInt16() : in.ReadInt32());
		if(innerLen>decryptedLen-sizeSize){
			LOGW("Received packet has wrong inner length (%d with total of %u)", (int) innerLen, (unsigned int) decryptedLen);
			return;
		}
		if(decryptedLen-innerLen<(shortFormat ? 16 : 12)){
			LOGW("Received packet has too little padding (%u)", (unsigned int) (decryptedLen-innerLen));
			return;
		}
		memcpy(buffer, decrypted+(shortFormat ? 2 : 4), innerLen);
		in=BufferInputStream(buffer, (size_t) innerLen);
		if(retryWith2){
			LOGD("Successfully decrypted packet in MTProto2.0 fallback, upgrading");
			useMTProto2=true;
		}
	}

	lastRecvPacketTime=GetCurrentTime();

	if(state==STATE_RECONNECTING){
		LOGI("Received a valid packet while reconnecting - setting state to established");
		SetState(STATE_ESTABLISHED);
	}

	if(srcEndpoint.type==Endpoint::Type::UDP_P2P_INET && !srcEndpoint.IsIPv6Only()){
		if(srcEndpoint.port!=packet.port || srcEndpoint.address!=packet.address){
			if(!packet.address.isIPv6){
				LOGI("Incoming packet was decrypted successfully, changing P2P endpoint to %s:%u", packet.address.ToString().c_str(), packet.port);
				srcEndpoint.address=packet.address;
				srcEndpoint.port=packet.port;
			}
		}
	}

	/*decryptedAudioBlock random_id:long random_bytes:string flags:# voice_call_id:flags.2?int128 in_seq_no:flags.4?int out_seq_no:flags.4?int
 * recent_received_mask:flags.5?int proto:flags.3?int extra:flags.1?string raw_data:flags.0?string = DecryptedAudioBlock
simpleAudioBlock random_id:long random_bytes:string raw_data:string = DecryptedAudioBlock;
*/
	uint32_t ackId, pseq, acks;
	unsigned char type, pflags;
	size_t packetInnerLen=0;
	if(shortFormat){
		type=in.ReadByte();
		ackId=(uint32_t) in.ReadInt32();
		pseq=(uint32_t) in.ReadInt32();
		acks=(uint32_t) in.ReadInt32();
		pflags=in.ReadByte();
		packetInnerLen=innerLen-14;
	}else{
		uint32_t tlid=(uint32_t) in.ReadInt32();
		if(tlid==TLID_DECRYPTED_AUDIO_BLOCK){
			in.ReadInt64(); // random id
			uint32_t randLen=(uint32_t) in.ReadTlLength();
			in.Seek(in.GetOffset()+randLen+pad4(randLen));
			uint32_t flags=(uint32_t) in.ReadInt32();
			type=(unsigned char) ((flags >> 24) & 0xFF);
			if(!(flags & PFLAG_HAS_SEQ && flags & PFLAG_HAS_RECENT_RECV)){
				LOGW("Received packet doesn't have PFLAG_HAS_SEQ, PFLAG_HAS_RECENT_RECV, or both");

				return;
			}
			if(flags & PFLAG_HAS_CALL_ID){
				unsigned char pktCallID[16];
				in.ReadBytes(pktCallID, 16);
				if(memcmp(pktCallID, callID, 16)!=0){
					LOGW("Received packet has wrong call id");

					lastError=ERROR_UNKNOWN;
					SetState(STATE_FAILED);
					return;
				}
			}
			ackId=(uint32_t) in.ReadInt32();
			pseq=(uint32_t) in.ReadInt32();
			acks=(uint32_t) in.ReadInt32();
			if(flags & PFLAG_HAS_PROTO){
				uint32_t proto=(uint32_t) in.ReadInt32();
				if(proto!=PROTOCOL_NAME){
					LOGW("Received packet uses wrong protocol");

					lastError=ERROR_INCOMPATIBLE;
					SetState(STATE_FAILED);
					return;
				}
			}
			if(flags & PFLAG_HAS_EXTRA){
				uint32_t extraLen=(uint32_t) in.ReadTlLength();
				in.Seek(in.GetOffset()+extraLen+pad4(extraLen));
			}
			if(flags & PFLAG_HAS_DATA){
				packetInnerLen=in.ReadTlLength();
			}
			pflags=0;
		}else if(tlid==TLID_SIMPLE_AUDIO_BLOCK){
			in.ReadInt64(); // random id
			uint32_t randLen=(uint32_t) in.ReadTlLength();
			in.Seek(in.GetOffset()+randLen+pad4(randLen));
			packetInnerLen=in.ReadTlLength();
			type=in.ReadByte();
			ackId=(uint32_t) in.ReadInt32();
			pseq=(uint32_t) in.ReadInt32();
			acks=(uint32_t) in.ReadInt32();
			if(peerVersion>=6)
				pflags=in.ReadByte();
			else
				pflags=0;
		}else{
			LOGW("Received a packet of unknown type %08X", tlid);

			return;
		}
	}
	packetsReceived++;

	if(seqgt(pseq, lastRemoteSeq-MAX_RECENT_PACKETS)){
		if(find(recentIncomingPackets.begin(), recentIncomingPackets.end(), pseq)!=recentIncomingPackets.end()){
			LOGW("Received duplicated packet for seq %u", pseq);
			return;
		}
		recentIncomingPackets.push_back(pseq);
		while(recentIncomingPackets.size()>MAX_RECENT_PACKETS)
			recentIncomingPackets.erase(recentIncomingPackets.begin());
		if(seqgt(pseq, lastRemoteSeq))
			lastRemoteSeq=pseq;
	}else{
		LOGW("Packet %u is out of order and too late", pseq);
		return;
	}

	if(pflags & XPFLAG_HAS_EXTRA){
		unsigned char extraCount=in.ReadByte();
		for(int i=0;i<extraCount;i++){
			size_t extraLen=in.ReadByte();
			Buffer xbuffer(extraLen);
			in.ReadBytes(*xbuffer, extraLen);
			ProcessExtraData(xbuffer);
		}
	}

	uint32_t recvTS=0;
	if(pflags & XPFLAG_HAS_RECV_TS){
		recvTS=static_cast<uint32_t>(in.ReadInt32());
	}
	if(seqgt(ackId, lastRemoteAckSeq)){

		if(waitingForAcks && lastRemoteAckSeq>=firstSentPing){
			rttHistory.Reset();
			waitingForAcks=false;
			dontSendPackets=10;
			messageThread.Post([this]{
				dontSendPackets=0;
			}, 1.0);
			LOGI("resuming sending");
		}
		vector<uint32_t> peerAcks;
		lastRemoteAckSeq=ackId;
		conctl->PacketAcknowledged(ackId);
		peerAcks.push_back(ackId);
		for(unsigned int i=0;i<32;i++){
			if((acks >> (31-i)) & 1){
				peerAcks.push_back(ackId-(i+1));
			}
		}

		for(RecentOutgoingPacket& opkt:recentOutgoingPackets){
			if(opkt.ackTime!=0.0)
				continue;
			if(find(peerAcks.begin(), peerAcks.end(), opkt.seq)!=peerAcks.end()){
				opkt.ackTime=GetCurrentTime();
				if(opkt.lost){
					LOGW("acknowledged lost packet %u", opkt.seq);
					sendLosses--;
				}
				if(opkt.sender && !opkt.lost){ // don't report lost packets as acknowledged to PacketSenders
					opkt.sender->PacketAcknowledged(opkt.seq, opkt.sendTime, recvTS/1000.0f, opkt.type, opkt.size);
				}

				// TODO move this to a PacketSender
				conctl->PacketAcknowledged(opkt.seq);
			}
		}

		if(peerVersion<6){
			for(unsigned int i=0; i<queuedPackets.size(); i++){
				QueuedPacket &qp=queuedPackets[i];
				int j;
				bool didAck=false;
				for(j=0; j<16; j++){
					LOGD("queued packet %u, seq %u=%u", i, j, qp.seqs[j]);
					if(qp.seqs[j]==0)
						break;
					int remoteAcksIndex=lastRemoteAckSeq-qp.seqs[j];
					//LOGV("remote acks index %u, value %f", remoteAcksIndex, remoteAcksIndex>=0 && remoteAcksIndex<32 ? remoteAcks[remoteAcksIndex] : -1);
					if(seqgt(lastRemoteAckSeq, qp.seqs[j]) && remoteAcksIndex>=0 && remoteAcksIndex<32){
						for(RecentOutgoingPacket &opkt:recentOutgoingPackets){
							if(opkt.seq==qp.seqs[j] && opkt.ackTime>0){
								LOGD("did ack seq %u, removing", qp.seqs[j]);
								didAck=true;
								break;
							}
						}
						if(didAck)
							break;
					}
				}
				if(didAck){
					queuedPackets.erase(queuedPackets.begin()+i);
					i--;
					continue;
				}
			}
		}else{
			for(vector<UnacknowledgedExtraData>::iterator x=currentExtras.begin(); x!=currentExtras.end();){
				if(x->firstContainingSeq!=0 && (lastRemoteAckSeq==x->firstContainingSeq || seqgt(lastRemoteAckSeq, x->firstContainingSeq))){
					LOGV("Peer acknowledged extra type %u length %u", x->type, (unsigned int)x->data.Length());
					ProcessAcknowledgedOutgoingExtra(*x);
					x=currentExtras.erase(x);
					continue;
				}
				++x;
			}
		}
	}

	Endpoint* _currentEndpoint=&endpoints.at(currentEndpoint);
	if(srcEndpoint.id!=currentEndpoint && (srcEndpoint.type==Endpoint::Type::UDP_RELAY || srcEndpoint.type==Endpoint::Type::TCP_RELAY) && ((_currentEndpoint->type!=Endpoint::Type::UDP_RELAY && _currentEndpoint->type!=Endpoint::Type::TCP_RELAY) || _currentEndpoint->averageRTT==0)){
		if(seqgt(lastSentSeq-32, lastRemoteAckSeq)){
			currentEndpoint=srcEndpoint.id;
			_currentEndpoint=&srcEndpoint;
			LOGI("Peer network address probably changed, switching to relay");
			if(allowP2p)
				SendPublicEndpointsRequest();
		}
	}


	if(config.logPacketStats){
		DebugLoggedPacket dpkt={
				static_cast<int32_t>(pseq),
				GetCurrentTime()-connectionInitTime,
				static_cast<int32_t>(packet.data.Length())
		};
		debugLoggedPackets.push_back(dpkt);
		if(debugLoggedPackets.size()>=2500){
			debugLoggedPackets.erase(debugLoggedPackets.begin(), debugLoggedPackets.begin()+500);
		}
	}

	unacknowledgedIncomingPacketCount++;
	if(unacknowledgedIncomingPacketCount>unackNopThreshold){
		//LOGV("Sending nop packet as ack");
		SendNopPacket();
	}

#ifdef LOG_PACKETS
	LOGV("Received: from=%s:%u, seq=%u, length=%u, type=%s", srcEndpoint.GetAddress().ToString().c_str(), srcEndpoint.port, pseq, (unsigned int)packet.data.Length(), GetPacketTypeString(type).c_str());
#endif

	//LOGV("acks: %u -> %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf", lastRemoteAckSeq, remoteAcks[0], remoteAcks[1], remoteAcks[2], remoteAcks[3], remoteAcks[4], remoteAcks[5], remoteAcks[6], remoteAcks[7]);
	//LOGD("recv: %u -> %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf, %.2lf", lastRemoteSeq, recvPacketTimes[0], recvPacketTimes[1], recvPacketTimes[2], recvPacketTimes[3], recvPacketTimes[4], recvPacketTimes[5], recvPacketTimes[6], recvPacketTimes[7]);
	//LOGI("RTT = %.3lf", GetAverageRTT());
	//LOGV("Packet %u type is %d", pseq, type);
	if(type==PKT_INIT){
		LOGD("Received init");
		uint32_t ver=(uint32_t)in.ReadInt32();
		if(!receivedInit)
			peerVersion=ver;
		LOGI("Peer version is %d", peerVersion);
		uint32_t minVer=(uint32_t) in.ReadInt32();
		if(minVer>PROTOCOL_VERSION || peerVersion<MIN_PROTOCOL_VERSION){
			lastError=ERROR_INCOMPATIBLE;

			SetState(STATE_FAILED);
			return;
		}
		uint32_t flags=(uint32_t) in.ReadInt32();
		if(!receivedInit){
			if(flags & INIT_FLAG_DATA_SAVING_ENABLED){
				dataSavingRequestedByPeer=true;
				UpdateDataSavingState();
				UpdateAudioBitrateLimit();
			}
			if(flags & INIT_FLAG_GROUP_CALLS_SUPPORTED){
				peerCapabilities|=TGVOIP_PEER_CAP_GROUP_CALLS;
			}
			if(flags & INIT_FLAG_VIDEO_RECV_SUPPORTED){
				peerCapabilities|=TGVOIP_PEER_CAP_VIDEO_DISPLAY;
			}
			if(flags & INIT_FLAG_VIDEO_SEND_SUPPORTED){
				peerCapabilities|=TGVOIP_PEER_CAP_VIDEO_CAPTURE;
			}
		}

		unsigned int i;
		unsigned int numSupportedAudioCodecs=in.ReadByte();
		for(i=0; i<numSupportedAudioCodecs; i++){
			if(peerVersion<5)
				in.ReadByte(); // ignore for now
			else
				in.ReadInt32();
		}
		if(!receivedInit && ((flags & INIT_FLAG_VIDEO_SEND_SUPPORTED && config.enableVideoReceive) || (flags & INIT_FLAG_VIDEO_RECV_SUPPORTED && config.enableVideoSend))){
			LOGD("Peer video decoders:");
			unsigned int numSupportedVideoDecoders=in.ReadByte();
			for(i=0; i<numSupportedVideoDecoders; i++){
				uint32_t id=static_cast<uint32_t>(in.ReadInt32());
				peerVideoDecoders.push_back(id);
				char* _id=reinterpret_cast<char*>(&id);
				LOGD("%c%c%c%c", _id[3], _id[2], _id[1], _id[0]);
			}
			protocolInfo.maxVideoResolution=in.ReadByte();

			SetupOutgoingVideoStream();
		}

		BufferOutputStream out(1024);

		out.WriteInt32(PROTOCOL_VERSION);
		out.WriteInt32(MIN_PROTOCOL_VERSION);

		out.WriteByte((unsigned char) outgoingStreams.size());
		for(vector<shared_ptr<Stream>>::iterator s=outgoingStreams.begin(); s!=outgoingStreams.end(); ++s){
			out.WriteByte((*s)->id);
			out.WriteByte((*s)->type);
			if(peerVersion<5)
				out.WriteByte((unsigned char) ((*s)->codec==CODEC_OPUS ? CODEC_OPUS_OLD : 0));
			else
				out.WriteInt32((*s)->codec);
			out.WriteInt16((*s)->frameDuration);
			out.WriteByte((unsigned char) ((*s)->enabled ? 1 : 0));
		}
		LOGI("Sending init ack");
		size_t outLength = out.GetLength();
		SendOrEnqueuePacket(PendingOutgoingPacket{
				/*.seq=*/GenerateOutSeq(),
				/*.type=*/PKT_INIT_ACK,
				/*.len=*/outLength,
				/*.data=*/Buffer(move(out)),
				/*.endpoint=*/0
		});
		if(!receivedInit){
			receivedInit=true;
			if((srcEndpoint.type==Endpoint::Type::UDP_RELAY && udpConnectivityState!=UDP_BAD && udpConnectivityState!=UDP_NOT_AVAILABLE) || srcEndpoint.type==Endpoint::Type::TCP_RELAY){
				currentEndpoint=srcEndpoint.id;
				if(srcEndpoint.type==Endpoint::Type::UDP_RELAY || (useTCP && srcEndpoint.type==Endpoint::Type::TCP_RELAY))
					preferredRelay=srcEndpoint.id;
			}
		}
		if(!audioStarted && receivedInitAck){
			StartAudio();
			audioStarted=true;
		}
	}
	if(type==PKT_INIT_ACK){
		LOGD("Received init ack");

		if(!receivedInitAck){
			receivedInitAck=true;

			messageThread.Cancel(initTimeoutID);
			initTimeoutID=MessageThread::INVALID_ID;

			if(packetInnerLen>10){
				peerVersion=in.ReadInt32();
				uint32_t minVer=(uint32_t) in.ReadInt32();
				if(minVer>PROTOCOL_VERSION || peerVersion<MIN_PROTOCOL_VERSION){
					lastError=ERROR_INCOMPATIBLE;

					SetState(STATE_FAILED);
					return;
				}
			}else{
				peerVersion=1;
			}

			LOGI("peer version from init ack %d", peerVersion);

			unsigned char streamCount=in.ReadByte();
			if(streamCount==0)
				return;

			int i;
			shared_ptr<Stream> incomingAudioStream=NULL;
			for(i=0; i<streamCount; i++){
				shared_ptr<Stream> stm=make_shared<Stream>();
				stm->id=in.ReadByte();
				stm->type=in.ReadByte();
				if(peerVersion<5){
					unsigned char codec=in.ReadByte();
					if(codec==CODEC_OPUS_OLD)
						stm->codec=CODEC_OPUS;
				}else{
					stm->codec=(uint32_t) in.ReadInt32();
				}
				in.ReadInt16();
				stm->frameDuration=60;
				stm->enabled=in.ReadByte()==1;
				if(stm->type==STREAM_TYPE_VIDEO && peerVersion<9){
					LOGV("Skipping video stream for old protocol version");
					continue;
				}
				if(stm->type==STREAM_TYPE_AUDIO){
					stm->jitterBuffer=make_shared<JitterBuffer>(nullptr, stm->frameDuration);
					if(stm->frameDuration>50)
						stm->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_60", 2));
					else if(stm->frameDuration>30)
						stm->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_40", 4));
					else
						stm->jitterBuffer->SetMinPacketCount((uint32_t) ServerConfig::GetSharedInstance()->GetInt("jitter_initial_delay_20", 6));
					stm->decoder=NULL;
				}else if(stm->type==STREAM_TYPE_VIDEO){
					if(!stm->packetReassembler){
						stm->packetReassembler=make_shared<PacketReassembler>();
						stm->packetReassembler->SetCallback(bind(&VoIPController::ProcessIncomingVideoFrame, this, placeholders::_1, placeholders::_2, placeholders::_3, placeholders::_4));
					}
				}else{
					LOGW("Unknown incoming stream type: %d", stm->type);
					continue;
				}
				incomingStreams.push_back(stm);
				if(stm->type==STREAM_TYPE_AUDIO && !incomingAudioStream)
					incomingAudioStream=stm;
			}
			if(!incomingAudioStream)
				return;

			if(peerVersion>=5 && !useMTProto2){
				useMTProto2=true;
				LOGD("MTProto2 wasn't initially enabled for whatever reason but peer supports it; upgrading");
			}

			if(!audioStarted && receivedInit){
				StartAudio();
				audioStarted=true;
			}
			messageThread.Post([this]{
				if(state==STATE_WAIT_INIT_ACK){
					SetState(STATE_ESTABLISHED);
				}
			}, ServerConfig::GetSharedInstance()->GetDouble("established_delay_if_no_stream_data", 1.5));
			if(allowP2p)
				SendPublicEndpointsRequest();
		}
	}
	if(type==PKT_STREAM_DATA || type==PKT_STREAM_DATA_X2 || type==PKT_STREAM_DATA_X3){
		if(!receivedFirstStreamPacket){
			receivedFirstStreamPacket=true;
			if(state!=STATE_ESTABLISHED && receivedInitAck){
				messageThread.Post([this](){
					SetState(STATE_ESTABLISHED);
				}, .5);
				LOGW("First audio packet - setting state to ESTABLISHED");
			}
		}
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
		if(srcEndpoint.type==Endpoint::Type::UDP_RELAY && srcEndpoint.id!=peerPreferredRelay){
			peerPreferredRelay=srcEndpoint.id;
		}
		for(i=0;i<count;i++){
			unsigned char streamID=in.ReadByte();
			unsigned char flags=(unsigned char) (streamID & 0xC0);
			streamID&=0x3F;
			uint16_t sdlen=(uint16_t) (flags & STREAM_DATA_FLAG_LEN16 ? in.ReadInt16() : in.ReadByte());
			uint32_t pts=(uint32_t) in.ReadInt32();
			unsigned char fragmentCount=1;
			unsigned char fragmentIndex=0;
			//LOGD("stream data, pts=%d, len=%d, rem=%d", pts, sdlen, in.Remaining());
			audioTimestampIn=pts;
			if(!audioOutStarted && audioOutput){
				MutexGuard m(audioIOMutex);
				audioOutput->Start();
				audioOutStarted=true;
			}
			bool fragmented=static_cast<bool>(sdlen & STREAM_DATA_XFLAG_FRAGMENTED);
			bool extraFEC=static_cast<bool>(sdlen & STREAM_DATA_XFLAG_EXTRA_FEC);
			bool keyframe=static_cast<bool>(sdlen & STREAM_DATA_XFLAG_KEYFRAME);
			if(fragmented){
				fragmentIndex=in.ReadByte();
				fragmentCount=in.ReadByte();
			}
			sdlen&=0x7FF;
			if(in.GetOffset()+sdlen>len){
				return;
			}
			shared_ptr<Stream> stm;
			for(shared_ptr<Stream>& ss:incomingStreams){
				if(ss->id==streamID){
					stm=ss;
					break;
				}
			}
			if(stm && stm->type==STREAM_TYPE_AUDIO){
				if(stm->jitterBuffer){
					stm->jitterBuffer->HandleInput((unsigned char *) (buffer+in.GetOffset()), sdlen, pts, false);
					if(extraFEC){
						in.Seek(in.GetOffset()+sdlen);
						unsigned int fecCount=in.ReadByte();
						for(unsigned int j=0;j<fecCount;j++){
							unsigned char dlen=in.ReadByte();
							unsigned char data[256];
							in.ReadBytes(data, dlen);
              stm->jitterBuffer->HandleInput(data, dlen, pts-(fecCount-j)*stm->frameDuration, true);
						}
					}
				}
			}else if(stm && stm->type==STREAM_TYPE_VIDEO){
				if(stm->packetReassembler){
					uint8_t frameSeq=in.ReadByte();
					Buffer pdata(sdlen);
					uint16_t rotation=0;
					if(fragmentIndex==0){
						unsigned char _rotation=in.ReadByte() & (unsigned char)VIDEO_ROTATION_MASK;
						switch(_rotation){
							case VIDEO_ROTATION_0:
								rotation=0;
								break;
							case VIDEO_ROTATION_90:
								rotation=90;
								break;
							case VIDEO_ROTATION_180:
								rotation=180;
								break;
							case VIDEO_ROTATION_270:
								rotation=270;
								break;
							default: // unreachable on sane CPUs
								abort();
						}
						//if(rotation!=stm->rotation){
						//	stm->rotation=rotation;
						//	LOGI("Video rotation: %u", rotation);
						//}
					}
					pdata.CopyFrom(buffer+in.GetOffset(), 0, sdlen);
					stm->packetReassembler->AddFragment(std::move(pdata), fragmentIndex, fragmentCount, pts, frameSeq, keyframe, rotation);
				}
				//LOGV("Received video fragment %u of %u", fragmentIndex, fragmentCount);
			}else{
				LOGW("received packet for unknown stream %u", (unsigned int)streamID);
			}
			if(i<count-1)
				in.Seek(in.GetOffset()+sdlen);
		}
	}
	if(type==PKT_PING){
		//LOGD("Received ping from %s:%d", srcEndpoint.address.ToString().c_str(), srcEndpoint.port);
		if(srcEndpoint.type!=Endpoint::Type::UDP_RELAY && srcEndpoint.type!=Endpoint::Type::TCP_RELAY && !allowP2p){
			LOGW("Received p2p ping but p2p is disabled by manual override");
			return;
		}
		BufferOutputStream pkt(128);
		pkt.WriteInt32(pseq);
		size_t pktLength = pkt.GetLength();
		SendOrEnqueuePacket(PendingOutgoingPacket{
				/*.seq=*/GenerateOutSeq(),
				/*.type=*/PKT_PONG,
				/*.len=*/pktLength,
				/*.data=*/Buffer(move(pkt)),
				/*.endpoint=*/srcEndpoint.id,
		});
	}
	if(type==PKT_PONG){
		if(packetInnerLen>=4){
			uint32_t pingSeq=(uint32_t) in.ReadInt32();
#ifdef LOG_PACKETS
			LOGD("Received pong for ping in seq %u", pingSeq);
#endif
			if(pingSeq==srcEndpoint.lastPingSeq){
				srcEndpoint.rtts.Add(GetCurrentTime()-srcEndpoint.lastPingTime);
				srcEndpoint.averageRTT=srcEndpoint.rtts.NonZeroAverage();
				LOGD("Current RTT via %s: %.3f, average: %.3f", packet.address.ToString().c_str(), srcEndpoint.rtts[0], srcEndpoint.averageRTT);
				if(srcEndpoint.averageRTT>rateMaxAcceptableRTT)
					needRate=true;
			}
		}
	}
	if(type==PKT_STREAM_STATE){
		unsigned char id=in.ReadByte();
		unsigned char enabled=in.ReadByte();
		LOGV("Peer stream state: id %u flags %u", (int)id, (int)enabled);
		for(vector<shared_ptr<Stream>>::iterator s=incomingStreams.begin();s!=incomingStreams.end();++s){
			if((*s)->id==id){
				(*s)->enabled=enabled==1;
				UpdateAudioOutputState();
				break;
			}
		}
	}
	if(type==PKT_LAN_ENDPOINT){
		LOGV("received lan endpoint");
		uint32_t peerAddr=(uint32_t) in.ReadInt32();
		uint16_t peerPort=(uint16_t) in.ReadInt32();
		constexpr int64_t lanID=(int64_t)(FOURCC('L','A','N','4')) << 32;
		unsigned char peerTag[16];
		Endpoint lan(lanID, peerPort, NetworkAddress::IPv4(peerAddr), NetworkAddress::Empty(), Endpoint::Type::UDP_P2P_LAN, peerTag);

		if(currentEndpoint==lanID)
			currentEndpoint=preferredRelay;

		MutexGuard m(endpointsMutex);
		endpoints[lanID]=lan;
	}
	if(type==PKT_NETWORK_CHANGED && _currentEndpoint->type!=Endpoint::Type::UDP_RELAY && _currentEndpoint->type!=Endpoint::Type::TCP_RELAY){
		currentEndpoint=preferredRelay;
		if(allowP2p)
			SendPublicEndpointsRequest();
		if(peerVersion>=2){
			uint32_t flags=(uint32_t) in.ReadInt32();
			dataSavingRequestedByPeer=(flags & INIT_FLAG_DATA_SAVING_ENABLED)==INIT_FLAG_DATA_SAVING_ENABLED;
			UpdateDataSavingState();
			UpdateAudioBitrateLimit();
			ResetEndpointPingStats();
		}
	}
	if(type==PKT_STREAM_EC){
		unsigned char streamID=in.ReadByte();
		if(peerVersion<7){
			uint32_t lastTimestamp=(uint32_t) in.ReadInt32();
			unsigned char count=in.ReadByte();
			for(shared_ptr<Stream> &stm:incomingStreams){
				if(stm->id==streamID){
					for(unsigned int i=0; i<count; i++){
						unsigned char dlen=in.ReadByte();
						unsigned char data[256];
						in.ReadBytes(data, dlen);
						if(stm->jitterBuffer){
							stm->jitterBuffer->HandleInput(data, dlen, lastTimestamp-(count-i-1)*stm->frameDuration, true);
						}
					}
					break;
				}
			}
		}else{
			shared_ptr<Stream> stm=GetStreamByID(streamID, false);
			if(!stm){
				LOGW("Received FEC packet for unknown stream %u", streamID);
				return;
			}
			if(stm->type!=STREAM_TYPE_VIDEO){
				LOGW("Received FEC packet for non-video stream %u", streamID);
				return;
			}
			if(!stm->packetReassembler)
				return;

            uint8_t fseq=in.ReadByte();
			unsigned char fecScheme=in.ReadByte();
			unsigned char prevFrameCount=in.ReadByte();
			uint16_t fecLen=(uint16_t)in.ReadInt16();
			if(fecLen>in.Remaining())
				return;

			Buffer fecData(fecLen);
			in.ReadBytes(fecData);

			stm->packetReassembler->AddFEC(std::move(fecData), fseq, prevFrameCount, fecScheme);
		}
	}
}

void VoIPController::ProcessExtraData(Buffer &data){
	BufferInputStream in(*data, data.Length());
	unsigned char type=in.ReadByte();
	unsigned char fullHash[SHA1_LENGTH];
	crypto.sha1(*data, data.Length(), fullHash);
	uint64_t hash=*reinterpret_cast<uint64_t*>(fullHash);
	if(lastReceivedExtrasByType[type]==hash){
		return;
	}
	LOGE("ProcessExtraData");
	lastReceivedExtrasByType[type]=hash;
	if(type==EXTRA_TYPE_STREAM_FLAGS){
		unsigned char id=in.ReadByte();
		uint32_t flags=static_cast<uint32_t>(in.ReadInt32());
		LOGV("Peer stream state: id %u flags %u", (unsigned int)id, (unsigned int)flags);
		for(shared_ptr<Stream>& s:incomingStreams){
			if(s->id==id){
				bool prevEnabled=s->enabled;
				bool prevPaused=s->paused;
				s->enabled=(flags & STREAM_FLAG_ENABLED)==STREAM_FLAG_ENABLED;
				s->paused=(flags & STREAM_FLAG_PAUSED)==STREAM_FLAG_PAUSED;
				if(flags & STREAM_FLAG_EXTRA_EC){
					if(!s->extraECEnabled){
						s->extraECEnabled=true;
						if(s->jitterBuffer)
							s->jitterBuffer->SetMinPacketCount(4);
					}
				}else{
					if(s->extraECEnabled){
						s->extraECEnabled=false;
						if(s->jitterBuffer)
							s->jitterBuffer->SetMinPacketCount(2);
					}
				}
				if(prevEnabled!=s->enabled && s->type==STREAM_TYPE_VIDEO && videoRenderer)
					videoRenderer->SetStreamEnabled(s->enabled);
				if(prevPaused!=s->paused && s->type==STREAM_TYPE_VIDEO && videoRenderer)
					videoRenderer->SetStreamPaused(s->paused);
				UpdateAudioOutputState();
				break;
			}
		}
	}else if(type==EXTRA_TYPE_STREAM_CSD){
		LOGI("Received codec specific data");
		/*
		os.WriteByte(stream.id);
		os.WriteByte(static_cast<unsigned char>(stream.codecSpecificData.size()));
		for(Buffer& b:stream.codecSpecificData){
			assert(b.Length()<255);
			os.WriteByte(static_cast<unsigned char>(b.Length()));
			os.WriteBytes(b);
		}
		Buffer buf(move(os));
		SendExtra(buf, EXTRA_TYPE_STREAM_CSD);
		 */
		unsigned char streamID=in.ReadByte();
		for(shared_ptr<Stream>& stm:incomingStreams){
			if(stm->id==streamID){
				stm->codecSpecificData.clear();
				stm->csdIsValid=false;
				stm->width=static_cast<unsigned int>(in.ReadInt16());
				stm->height=static_cast<unsigned int>(in.ReadInt16());
				size_t count=(size_t)in.ReadByte();
				for(size_t i=0;i<count;i++){
					size_t len=(size_t)in.ReadByte();
					Buffer csd(len);
					in.ReadBytes(*csd, len);
					stm->codecSpecificData.push_back(move(csd));
				}
				break;
			}
		}
	}else if(type==EXTRA_TYPE_LAN_ENDPOINT){
		if(!allowP2p)
			return;
		LOGV("received lan endpoint (extra)");
		uint32_t peerAddr=(uint32_t) in.ReadInt32();
		uint16_t peerPort=(uint16_t) in.ReadInt32();
		constexpr int64_t lanID=(int64_t)(FOURCC('L','A','N','4')) << 32;
		if(currentEndpoint==lanID)
			currentEndpoint=preferredRelay;

		unsigned char peerTag[16];
		Endpoint lan(lanID, peerPort, NetworkAddress::IPv4(peerAddr), NetworkAddress::Empty(), Endpoint::Type::UDP_P2P_LAN, peerTag);
		MutexGuard m(endpointsMutex);
		endpoints[lanID]=lan;
	}else if(type==EXTRA_TYPE_NETWORK_CHANGED){
		LOGI("Peer network changed");
		wasNetworkHandover=true;
		const Endpoint& _currentEndpoint=endpoints.at(currentEndpoint);
		if(_currentEndpoint.type!=Endpoint::Type::UDP_RELAY && _currentEndpoint.type!=Endpoint::Type::TCP_RELAY)
			currentEndpoint=preferredRelay;
		if(allowP2p)
			SendPublicEndpointsRequest();
		uint32_t flags=(uint32_t) in.ReadInt32();
		dataSavingRequestedByPeer=(flags & INIT_FLAG_DATA_SAVING_ENABLED)==INIT_FLAG_DATA_SAVING_ENABLED;
		UpdateDataSavingState();
		UpdateAudioBitrateLimit();
		ResetEndpointPingStats();
	}else if(type==EXTRA_TYPE_GROUP_CALL_KEY){
		if(!didReceiveGroupCallKey && !didSendGroupCallKey){
			unsigned char groupKey[256];
			in.ReadBytes(groupKey, 256);
			messageThread.Post([this, &groupKey]{
				if(callbacks.groupCallKeyReceived)
					callbacks.groupCallKeyReceived(this, groupKey);
			});
			didReceiveGroupCallKey=true;
		}
	}else if(type==EXTRA_TYPE_REQUEST_GROUP){
		if(!didInvokeUpgradeCallback){
			messageThread.Post([this]{
				if(callbacks.upgradeToGroupCallRequested)
					callbacks.upgradeToGroupCallRequested(this);
			});
			didInvokeUpgradeCallback=true;
		}
	}else if(type==EXTRA_TYPE_IPV6_ENDPOINT){
		if(!allowP2p)
			return;
		unsigned char _addr[16];
		in.ReadBytes(_addr, 16);
		NetworkAddress addr=NetworkAddress::IPv6(_addr);
		uint16_t port=static_cast<uint16_t>(in.ReadInt16());
		peerIPv6Available=true;
		LOGV("Received peer IPv6 endpoint [%s]:%u", addr.ToString().c_str(), port);

		constexpr int64_t p2pID=(int64_t)(FOURCC('P','2','P','6')) << 32;

		Endpoint ep;
		ep.type=Endpoint::Type::UDP_P2P_INET;
		ep.port=port;
		ep.v6address=addr;
		ep.id=p2pID;
		endpoints[p2pID]=ep;
		if(!myIPv6.IsEmpty())
			currentEndpoint=p2pID;
	}
}

void VoIPController::ProcessAcknowledgedOutgoingExtra(UnacknowledgedExtraData &extra){
	if(extra.type==EXTRA_TYPE_GROUP_CALL_KEY){
		if(!didReceiveGroupCallKeyAck){
			didReceiveGroupCallKeyAck=true;
			messageThread.Post([this]{
				if(callbacks.groupCallKeySent)
					callbacks.groupCallKeySent(this);
			});
		}
	}
}

Endpoint& VoIPController::GetRemoteEndpoint(){
	return endpoints.at(currentEndpoint);
}

Endpoint* VoIPController::GetEndpointForPacket(const PendingOutgoingPacket& pkt){
	Endpoint* endpoint=NULL;
	if(pkt.endpoint){
		try{
			endpoint=&endpoints.at(pkt.endpoint);
		}catch(out_of_range& x){
			LOGW("Unable to send packet via nonexistent endpoint %" PRIu64, pkt.endpoint);
			return NULL;
		}
	}
	if(!endpoint)
		endpoint=&endpoints.at(currentEndpoint);
	return endpoint;
}

bool VoIPController::SendOrEnqueuePacket(PendingOutgoingPacket pkt, bool enqueue, PacketSender* source){
	ENFORCE_MSG_THREAD;

	Endpoint* endpoint=GetEndpointForPacket(pkt);
	if(!endpoint){
		abort();
		return false;
	}

	bool canSend;
	if(endpoint->type!=Endpoint::Type::TCP_RELAY){
		canSend=realUdpSocket->IsReadyToSend();
	}else{
		if(!endpoint->socket){
			LOGV("Connecting to %s:%u", endpoint->GetAddress().ToString().c_str(), endpoint->port);
			if(proxyProtocol==PROXY_NONE){
				endpoint->socket=make_shared<NetworkSocketTCPObfuscated>(NetworkSocket::Create(NetworkProtocol::TCP));
				endpoint->socket->Connect(endpoint->GetAddress(), endpoint->port);
			}else if(proxyProtocol==PROXY_SOCKS5){
				NetworkSocket* tcp=NetworkSocket::Create(NetworkProtocol::TCP);
				tcp->Connect(resolvedProxyAddress, proxyPort);
				shared_ptr<NetworkSocketSOCKS5Proxy> proxy=make_shared<NetworkSocketSOCKS5Proxy>(tcp, nullptr, proxyUsername, proxyPassword);
				endpoint->socket=proxy;
				endpoint->socket->Connect(endpoint->GetAddress(), endpoint->port);
			}
			selectCanceller->CancelSelect();
		}
		canSend=endpoint->socket && endpoint->socket->IsReadyToSend();
	}
	if(!canSend){
		if(enqueue){
    		LOGW("Not ready to send - enqueueing");
    		sendQueue.push_back(move(pkt));
		}
		return false;
	}
	if((endpoint->type==Endpoint::Type::TCP_RELAY && useTCP) || (endpoint->type!=Endpoint::Type::TCP_RELAY && useUDP)){
		//BufferOutputStream p(buf, sizeof(buf));
		BufferOutputStream p(1500);
		WritePacketHeader(pkt.seq, &p, pkt.type, (uint32_t)pkt.len, source);
		p.WriteBytes(pkt.data);
		SendPacket(p.GetBuffer(), p.GetLength(), *endpoint, pkt);
		if(pkt.type==PKT_STREAM_DATA){
			unsentStreamPackets--;
		}
	}
	return true;
}

void VoIPController::SendPacket(unsigned char *data, size_t len, Endpoint& ep, PendingOutgoingPacket& srcPacket){
	if(stopping)
		return;
	if(ep.type==Endpoint::Type::TCP_RELAY && !useTCP)
		return;
	BufferOutputStream out(len+128);
	if(ep.type==Endpoint::Type::UDP_RELAY || ep.type==Endpoint::Type::TCP_RELAY)
		out.WriteBytes((unsigned char*)ep.peerTag, 16);
	else if(peerVersion<9)
		out.WriteBytes(callID, 16);
	if(len>0){
		if(useMTProto2){
			BufferOutputStream inner(len+128);
			size_t sizeSize;
			if(peerVersion>=8 || (!peerVersion && connectionMaxLayer>=92)){
				inner.WriteInt16((uint16_t) len);
				sizeSize=0;
			}else{
				inner.WriteInt32((uint32_t) len);
				out.WriteBytes(keyFingerprint, 8);
				sizeSize=4;
			}
			inner.WriteBytes(data, len);

			size_t padLen=16-inner.GetLength()%16;
			if(padLen<16)
				padLen+=16;
			unsigned char padding[32];
			crypto.rand_bytes((uint8_t *) padding, padLen);
			inner.WriteBytes(padding, padLen);
			assert(inner.GetLength()%16==0);

			unsigned char key[32], iv[32], msgKey[16];
			BufferOutputStream buf(len+32);
			size_t x=isOutgoing ? 0 : 8;
			buf.WriteBytes(encryptionKey+88+x, 32);
			buf.WriteBytes(inner.GetBuffer()+sizeSize, inner.GetLength()-sizeSize);
			unsigned char msgKeyLarge[32];
			crypto.sha256(buf.GetBuffer(), buf.GetLength(), msgKeyLarge);
			memcpy(msgKey, msgKeyLarge+8, 16);
			KDF2(msgKey, isOutgoing ? 0 : 8, key, iv);
			out.WriteBytes(msgKey, 16);
			//LOGV("<- MSG KEY: %08x %08x %08x %08x, hashed %u", *reinterpret_cast<int32_t*>(msgKey), *reinterpret_cast<int32_t*>(msgKey+4), *reinterpret_cast<int32_t*>(msgKey+8), *reinterpret_cast<int32_t*>(msgKey+12), inner.GetLength()-4);

			unsigned char aesOut[MSC_STACK_FALLBACK(inner.GetLength(), 1500)];
			crypto.aes_ige_encrypt(inner.GetBuffer(), aesOut, inner.GetLength(), key, iv);
			out.WriteBytes(aesOut, inner.GetLength());
		}else{
			BufferOutputStream inner(len+128);
			inner.WriteInt32((int32_t)len);
			inner.WriteBytes(data, len);
			if(inner.GetLength()%16!=0){
				size_t padLen=16-inner.GetLength()%16;
				unsigned char padding[16];
				crypto.rand_bytes((uint8_t *) padding, padLen);
				inner.WriteBytes(padding, padLen);
			}
			assert(inner.GetLength()%16==0);
			unsigned char key[32], iv[32], msgHash[SHA1_LENGTH];
			crypto.sha1((uint8_t *) inner.GetBuffer(), len+4, msgHash);
			out.WriteBytes(keyFingerprint, 8);
			out.WriteBytes((msgHash+(SHA1_LENGTH-16)), 16);
			KDF(msgHash+(SHA1_LENGTH-16), isOutgoing ? 0 : 8, key, iv);
			unsigned char aesOut[MSC_STACK_FALLBACK(inner.GetLength(), 1500)];
			crypto.aes_ige_encrypt(inner.GetBuffer(), aesOut, inner.GetLength(), key, iv);
			out.WriteBytes(aesOut, inner.GetLength());
		}
	}
	//LOGV("Sending %d bytes to %s:%d", out.GetLength(), ep.address.ToString().c_str(), ep.port);
#ifdef LOG_PACKETS
	LOGV("Sending: to=%s:%u, seq=%u, length=%u, type=%s", ep.GetAddress().ToString().c_str(), ep.port, srcPacket.seq, (unsigned int)out.GetLength(), GetPacketTypeString(srcPacket.type).c_str());
#endif


	/*ActuallySendPacket(NetworkPacket{
			Buffer(std::move(out)),
			ep.GetAddress(),
			ep.port,
			ep.type==Endpoint::Type::TCP_RELAY ? NetworkProtocol::TCP : NetworkProtocol::UDP
		}, ep);*/
	rawSendQueue.Put(RawPendingOutgoingPacket{
			NetworkPacket{
					Buffer(std::move(out)),
					ep.GetAddress(),
					ep.port,
					ep.type==Endpoint::Type::TCP_RELAY ? NetworkProtocol::TCP : NetworkProtocol::UDP
			},
			ep.type==Endpoint::Type::TCP_RELAY ? ep.socket : nullptr
	});
}

void VoIPController::ActuallySendPacket(NetworkPacket pkt, Endpoint& ep){
	//LOGI("Sending packet of %d bytes", pkt.length);
	if(IS_MOBILE_NETWORK(networkType))
		stats.bytesSentMobile+=(uint64_t)pkt.data.Length();
	else
		stats.bytesSentWifi+=(uint64_t)pkt.data.Length();
	if(ep.type==Endpoint::Type::TCP_RELAY){
		if(ep.socket && !ep.socket->IsFailed()){
			ep.socket->Send(std::move(pkt));
		}
	}else{
		udpSocket->Send(std::move(pkt));
	}
}


std::string VoIPController::NetworkTypeToString(int type){
	switch(type){
		case NET_TYPE_WIFI:
			return "wifi";
		case NET_TYPE_GPRS:
			return "gprs";
		case NET_TYPE_EDGE:
			return "edge";
		case NET_TYPE_3G:
			return "3g";
		case NET_TYPE_HSPA:
			return "hspa";
		case NET_TYPE_LTE:
			return "lte";
		case NET_TYPE_ETHERNET:
			return "ethernet";
		case NET_TYPE_OTHER_HIGH_SPEED:
			return "other_high_speed";
		case NET_TYPE_OTHER_LOW_SPEED:
			return "other_low_speed";
		case NET_TYPE_DIALUP:
			return "dialup";
		case NET_TYPE_OTHER_MOBILE:
			return "other_mobile";
		default:
			return "unknown";
	}
}

std::string VoIPController::GetPacketTypeString(unsigned char type){
	switch(type){
		case PKT_INIT:
			return "init";
		case PKT_INIT_ACK:
			return "init_ack";
		case PKT_STREAM_STATE:
			return "stream_state";
		case PKT_STREAM_DATA:
			return "stream_data";
		case PKT_PING:
			return "ping";
		case PKT_PONG:
			return "pong";
		case PKT_LAN_ENDPOINT:
			return "lan_endpoint";
		case PKT_NETWORK_CHANGED:
			return "network_changed";
		case PKT_NOP:
			return "nop";
		case PKT_STREAM_EC:
			return "stream_ec";
	}
    char buf[255];
	snprintf(buf, sizeof(buf), "unknown(%u)", type);
	return string(buf);
}

void VoIPController::AddIPv6Relays(){

	if(!myIPv6.IsEmpty() && !didAddIPv6Relays){
		unordered_map<string, vector<Endpoint>> endpointsByAddress;
		for(pair<const int64_t, Endpoint>& _e:endpoints){
			Endpoint& e=_e.second;
			if((e.type==Endpoint::Type::UDP_RELAY || e.type==Endpoint::Type::TCP_RELAY) && !e.v6address.IsEmpty() && !e.address.IsEmpty()){
				endpointsByAddress[e.v6address.ToString()].push_back(e);
			}
		}
		MutexGuard m(endpointsMutex);
		for(pair<const string, vector<Endpoint>>& addr:endpointsByAddress){
			for(Endpoint& e:addr.second){
				didAddIPv6Relays=true;
				e.address=NetworkAddress::Empty();
				e.id=e.id ^ ((int64_t)(FOURCC('I','P','v','6')) << 32);
				e.averageRTT=0;
				e.lastPingSeq=0;
				e.lastPingTime=0;
				e.rtts.Reset();
				e.udpPongCount=0;
				endpoints[e.id]=e;
				LOGD("Adding IPv6-only endpoint [%s]:%u", e.v6address.ToString().c_str(), e.port);
			}
		}
	}
}

void VoIPController::AddTCPRelays(){

	if(!didAddTcpRelays){
		bool wasSetCurrentToTCP=setCurrentEndpointToTCP;
		LOGV("Adding TCP relays");
		vector<Endpoint> relays;
		for(pair<const int64_t, Endpoint> &_e:endpoints){
			Endpoint& e=_e.second;
			if(e.type!=Endpoint::Type::UDP_RELAY)
				continue;
			if(wasSetCurrentToTCP && !useUDP){
				e.rtts.Reset();
				e.averageRTT=0;
				e.lastPingSeq=0;
			}
			Endpoint tcpRelay(e);
			tcpRelay.type=Endpoint::Type::TCP_RELAY;
			tcpRelay.averageRTT=0;
			tcpRelay.lastPingSeq=0;
			tcpRelay.lastPingTime=0;
			tcpRelay.rtts.Reset();
			tcpRelay.udpPongCount=0;
			tcpRelay.id=tcpRelay.id ^ ((int64_t) (FOURCC('T', 'C', 'P', 0)) << 32);
			if(setCurrentEndpointToTCP && endpoints.at(currentEndpoint).type!=Endpoint::Type::TCP_RELAY){
				LOGV("Setting current endpoint to TCP");
				setCurrentEndpointToTCP=false;
				currentEndpoint=tcpRelay.id;
				preferredRelay=tcpRelay.id;
			}
			relays.push_back(tcpRelay);
		}
		MutexGuard m(endpointsMutex);
		for(Endpoint& e:relays){
			endpoints[e.id]=e;
		}
		didAddTcpRelays=true;
	}
}

#if defined(__APPLE__)
static void initMachTimestart() {
	mach_timebase_info_data_t tb = { 0, 0 };
	mach_timebase_info(&tb);
	VoIPController::machTimebase = tb.numer;
	VoIPController::machTimebase /= tb.denom;
	VoIPController::machTimestart = mach_absolute_time();
}
#endif

double VoIPController::GetCurrentTime(){
#if defined(__linux__)
	struct timespec ts;
	clock_gettime(CLOCK_MONOTONIC, &ts);
	return ts.tv_sec+(double)ts.tv_nsec/1000000000.0;
#elif defined(__APPLE__)
	static pthread_once_t token = PTHREAD_ONCE_INIT;
	pthread_once(&token, &initMachTimestart);
	return (mach_absolute_time() - machTimestart) * machTimebase / 1000000000.0f;
#elif defined(_WIN32)
	if(!didInitWin32TimeScale){
		LARGE_INTEGER scale;
		QueryPerformanceFrequency(&scale);
		win32TimeScale=scale.QuadPart;
		didInitWin32TimeScale=true;
	}
	LARGE_INTEGER t;
	QueryPerformanceCounter(&t);
	return (double)t.QuadPart/(double)win32TimeScale;
#endif
}



void VoIPController::KDF(unsigned char* msgKey, size_t x, unsigned char* aesKey, unsigned char* aesIv){
	uint8_t sA[SHA1_LENGTH], sB[SHA1_LENGTH], sC[SHA1_LENGTH], sD[SHA1_LENGTH];
	BufferOutputStream buf(128);
	buf.WriteBytes(msgKey, 16);
	buf.WriteBytes(encryptionKey+x, 32);
	crypto.sha1(buf.GetBuffer(), buf.GetLength(), sA);
	buf.Reset();
	buf.WriteBytes(encryptionKey+32+x, 16);
	buf.WriteBytes(msgKey, 16);
	buf.WriteBytes(encryptionKey+48+x, 16);
	crypto.sha1(buf.GetBuffer(), buf.GetLength(), sB);
	buf.Reset();
	buf.WriteBytes(encryptionKey+64+x, 32);
	buf.WriteBytes(msgKey, 16);
	crypto.sha1(buf.GetBuffer(), buf.GetLength(), sC);
	buf.Reset();
	buf.WriteBytes(msgKey, 16);
	buf.WriteBytes(encryptionKey+96+x, 32);
	crypto.sha1(buf.GetBuffer(), buf.GetLength(), sD);
	buf.Reset();
	buf.WriteBytes(sA, 8);
	buf.WriteBytes(sB+8, 12);
	buf.WriteBytes(sC+4, 12);
	assert(buf.GetLength()==32);
	memcpy(aesKey, buf.GetBuffer(), 32);
	buf.Reset();
	buf.WriteBytes(sA+8, 12);
	buf.WriteBytes(sB, 8);
	buf.WriteBytes(sC+16, 4);
	buf.WriteBytes(sD, 8);
	assert(buf.GetLength()==32);
	memcpy(aesIv, buf.GetBuffer(), 32);
}

void VoIPController::KDF2(unsigned char* msgKey, size_t x, unsigned char *aesKey, unsigned char *aesIv){
	uint8_t sA[32], sB[32];
	BufferOutputStream buf(128);
	buf.WriteBytes(msgKey, 16);
	buf.WriteBytes(encryptionKey+x, 36);
	crypto.sha256(buf.GetBuffer(), buf.GetLength(), sA);
	buf.Reset();
	buf.WriteBytes(encryptionKey+40+x, 36);
	buf.WriteBytes(msgKey, 16);
	crypto.sha256(buf.GetBuffer(), buf.GetLength(), sB);
	buf.Reset();
	buf.WriteBytes(sA, 8);
	buf.WriteBytes(sB+8, 16);
	buf.WriteBytes(sA+24, 8);
	memcpy(aesKey, buf.GetBuffer(), 32);
	buf.Reset();
	buf.WriteBytes(sB, 8);
	buf.WriteBytes(sA+8, 16);
	buf.WriteBytes(sB+24, 8);
	memcpy(aesIv, buf.GetBuffer(), 32);
}


void VoIPController::SendPublicEndpointsRequest(const Endpoint& relay){
	if(!useUDP)
		return;
	LOGD("Sending public endpoints request to %s:%d", relay.address.ToString().c_str(), relay.port);
	publicEndpointsReqTime=GetCurrentTime();
	waitingForRelayPeerInfo=true;
	Buffer buf(32);
	memcpy(*buf, relay.peerTag, 16);
	memset(*buf+16, 0xFF, 16);
	udpSocket->Send(NetworkPacket{
		std::move(buf),
		relay.address,
		relay.port,
		NetworkProtocol::UDP
	});
}

Endpoint& VoIPController::GetEndpointByType(int type){
	if(type==Endpoint::Type::UDP_RELAY && preferredRelay)
		return endpoints.at(preferredRelay);
	for(pair<const int64_t, Endpoint>& e:endpoints){
		if(e.second.type==type)
			return e.second;
	}
	throw out_of_range("no endpoint");
}


void VoIPController::SendPacketReliably(unsigned char type, unsigned char *data, size_t len, double retryInterval, double timeout){
	ENFORCE_MSG_THREAD;

	LOGD("Send reliably, type=%u, len=%u, retry=%.3f, timeout=%.3f", type, unsigned(len), retryInterval, timeout);
	QueuedPacket pkt;
	if(data){
		Buffer b(len);
		b.CopyFrom(data, 0, len);
		pkt.data=move(b);
	}
	pkt.type=type;
	pkt.retryInterval=retryInterval;
	pkt.timeout=timeout;
	pkt.firstSentTime=0;
	pkt.lastSentTime=0;
	queuedPackets.push_back(move(pkt));
	messageThread.Post(std::bind(&VoIPController::UpdateQueuedPackets, this));
	if(timeout>0.0){
		messageThread.Post(std::bind(&VoIPController::UpdateQueuedPackets, this), timeout);
	}
}

void VoIPController::SendExtra(Buffer &data, unsigned char type){
	ENFORCE_MSG_THREAD;

	LOGV("Sending extra type %u length %u", type, (unsigned int)data.Length());
	for(vector<UnacknowledgedExtraData>::iterator x=currentExtras.begin();x!=currentExtras.end();++x){
		if(x->type==type){
			x->firstContainingSeq=0;
			x->data=move(data);
			return;
		}
	}
	UnacknowledgedExtraData xd={type, move(data), 0};
	currentExtras.push_back(move(xd));
}





void VoIPController::DebugCtl(int request, int param){

}

void VoIPController::SendUdpPing(Endpoint& endpoint){
	if(endpoint.type!=Endpoint::Type::UDP_RELAY)
		return;
	BufferOutputStream p(1024);
	p.WriteBytes(endpoint.peerTag, 16);
	p.WriteInt32(-1);
	p.WriteInt32(-1);
	p.WriteInt32(-1);
	p.WriteInt32(-2);
	int64_t id;
	crypto.rand_bytes(reinterpret_cast<uint8_t*>(&id), 8);
	p.WriteInt64(id);
	endpoint.udpPingTimes[id]=GetCurrentTime();
	udpSocket->Send(NetworkPacket{
		Buffer(std::move(p)),
		endpoint.GetAddress(),
		endpoint.port,
		NetworkProtocol::UDP
	});
	endpoint.totalUdpPings++;
	LOGV("Sending UDP ping to %s:%d, id %" PRId64, endpoint.GetAddress().ToString().c_str(), endpoint.port, id);
}


void VoIPController::ResetUdpAvailability(){
	ENFORCE_MSG_THREAD;

	LOGI("Resetting UDP availability");
	if(udpPingTimeoutID!=MessageThread::INVALID_ID){
		messageThread.Cancel(udpPingTimeoutID);
	}
	{
		for(pair<const int64_t, Endpoint>& e:endpoints){
			e.second.udpPongCount=0;
			e.second.udpPingTimes.clear();
		}
	}
	udpPingCount=0;
	udpConnectivityState=UDP_PING_PENDING;
	udpPingTimeoutID=messageThread.Post(std::bind(&VoIPController::SendUdpPings, this), 0.0, 0.5);
}

void VoIPController::ResetEndpointPingStats(){
	ENFORCE_MSG_THREAD;

	for(pair<const int64_t, Endpoint>& e:endpoints){
		e.second.averageRTT=0.0;
		e.second.rtts.Reset();
	}
}



#pragma mark - Video


void VoIPController::SetVideoSource(video::VideoSource *source){
	/*shared_ptr<Stream> stm=GetStreamByType(STREAM_TYPE_VIDEO, true);
	if(!stm){
		LOGE("Can't set video source when there is no outgoing video stream");
		return;
	}

	if(source){
		if(!stm->enabled){
			stm->enabled=true;
			messageThread.Post([this, stm]{SendStreamFlags(*stm);});
		}

		if(!videoPacketSender)
			videoPacketSender=new video::VideoPacketSender(this, source, stm);
		else
			videoPacketSender->SetSource(source);

	}else{
		if(stm->enabled){
			stm->enabled=false;
			messageThread.Post([this, stm]{SendStreamFlags(*stm);});
		}
		if(videoPacketSender){
			videoPacketSender->SetSource(NULL);
		}
	}*/
}

void VoIPController::SetVideoRenderer(video::VideoRenderer *renderer){
	videoRenderer=renderer;
}

void VoIPController::SetVideoCodecSpecificData(const std::vector<Buffer>& data){
	outgoingStreams[1]->codecSpecificData.clear();
	for(const Buffer& csd:data){
		outgoingStreams[1]->codecSpecificData.push_back(Buffer::CopyOf(csd));
	}
	LOGI("Set outgoing video stream CSD");
}

void VoIPController::SendVideoFrame(const Buffer &frame, uint32_t flags, uint32_t rotation){
	//LOGI("Send video frame %u flags %u", (unsigned int)frame.Length(), flags);
	shared_ptr<Stream> stm=GetStreamByType(STREAM_TYPE_VIDEO, true);
	if(stm){
	}
}


void VoIPController::ProcessIncomingVideoFrame(Buffer frame, uint32_t pts, bool keyframe, uint16_t rotation){
	//LOGI("Incoming video frame size %u pts %u", (unsigned int)frame.Length(), pts);
	if(frame.Length()==0){
		LOGE("EMPTY FRAME");
	}
	if(videoRenderer){
		shared_ptr<Stream> stm=GetStreamByType(STREAM_TYPE_VIDEO, false);
		size_t offset=0;
		if(keyframe){
			BufferInputStream in(frame);
			uint16_t width=(uint16_t)in.ReadInt16();
			uint16_t height=(uint16_t)in.ReadInt16();
			uint8_t sizeAndFlag=in.ReadByte();
			int size=sizeAndFlag & 0x0F;
			bool reset=(sizeAndFlag & 0x80)==0x80;
			if(reset || !stm->csdIsValid || stm->width!=width || stm->height!=height){
				stm->width=width;
				stm->height=height;
				stm->codecSpecificData.clear();
				for(int i=0; i<size; i++){
					size_t len=in.ReadByte();
					Buffer b(len);
					in.ReadBytes(b);
					stm->codecSpecificData.push_back(move(b));
				}
				stm->csdIsValid=false;
			}else{
				for(int i=0; i<size; i++){
					size_t len=in.ReadByte();
					in.Seek(in.GetOffset()+len);
				}
			}
			offset=in.GetOffset();
		}
		if(!stm->csdIsValid && stm->width && stm->height){
			videoRenderer->Reset(stm->codec, stm->width, stm->height, stm->codecSpecificData);
			stm->csdIsValid=true;
		}
		if(lastReceivedVideoFrameNumber==UINT32_MAX || lastReceivedVideoFrameNumber==pts-1 || keyframe){
			lastReceivedVideoFrameNumber=pts;
			//LOGV("3 before decode %u", (unsigned int)frame.Length());
			if(stm->rotation!=rotation){
				stm->rotation=rotation;
				videoRenderer->SetRotation(rotation);
			}
			if(offset==0){
				videoRenderer->DecodeAndDisplay(move(frame), pts);
			}else{
				videoRenderer->DecodeAndDisplay(Buffer::CopyOf(frame, offset, frame.Length()-offset), pts);
			}
		}else{
			LOGW("Skipping non-keyframe after packet loss...");
		}
	}
}

void VoIPController::SetupOutgoingVideoStream(){
	vector<uint32_t> myEncoders=video::VideoSource::GetAvailableEncoders();
	shared_ptr<Stream> vstm=make_shared<Stream>();
	vstm->id=2;
	vstm->type=STREAM_TYPE_VIDEO;

	if(find(myEncoders.begin(), myEncoders.end(), CODEC_HEVC)!=myEncoders.end() && find(peerVideoDecoders.begin(), peerVideoDecoders.end(), CODEC_HEVC)!=peerVideoDecoders.end()){
		vstm->codec=CODEC_HEVC;
	}else if(find(myEncoders.begin(), myEncoders.end(), CODEC_AVC)!=myEncoders.end() && find(peerVideoDecoders.begin(), peerVideoDecoders.end(), CODEC_AVC)!=peerVideoDecoders.end()){
		vstm->codec=CODEC_AVC;
	}else if(find(myEncoders.begin(), myEncoders.end(), CODEC_VP8)!=myEncoders.end() && find(peerVideoDecoders.begin(), peerVideoDecoders.end(), CODEC_VP8)!=peerVideoDecoders.end()){
		vstm->codec=CODEC_VP8;
	}else{
		LOGW("Can't setup outgoing video stream: no codecs in common");
		return;
	}

	vstm->enabled=false;
	outgoingStreams.push_back(vstm);
}

#pragma mark - Timer methods

void VoIPController::SendUdpPings(){
	LOGW("Send udp pings");
    ENFORCE_MSG_THREAD;

	for(pair<const int64_t, Endpoint>& e:endpoints){
		if(e.second.type==Endpoint::Type::UDP_RELAY){
			SendUdpPing(e.second);
		}
	}
	if(udpConnectivityState==UDP_UNKNOWN || udpConnectivityState==UDP_PING_PENDING)
		udpConnectivityState=UDP_PING_SENT;
	udpPingCount++;
	if(udpPingCount==4 || udpPingCount==10){
		messageThread.CancelSelf();
		udpPingTimeoutID=messageThread.Post(std::bind(&VoIPController::EvaluateUdpPingResults, this), 1.0);
	}
}

void VoIPController::EvaluateUdpPingResults(){
	double avgPongs=0;
	int count=0;
	for(pair<const int64_t, Endpoint>& _e:endpoints){
		Endpoint& e=_e.second;
		if(e.type==Endpoint::Type::UDP_RELAY){
			if(e.udpPongCount>0){
				avgPongs+=(double) e.udpPongCount;
				count++;
			}
		}
	}
	if(count>0)
		avgPongs/=(double)count;
	else
		avgPongs=0.0;
	LOGI("UDP ping reply count: %.2f", avgPongs);
	if(avgPongs==0.0 && proxyProtocol==PROXY_SOCKS5 && udpSocket!=realUdpSocket){
		LOGI("Proxy does not let UDP through, closing proxy connection and using UDP directly");
		NetworkSocket* proxySocket=udpSocket;
		proxySocket->Close();
		udpSocket=realUdpSocket;
		selectCanceller->CancelSelect();
		delete proxySocket;
		proxySupportsUDP=false;
		ResetUdpAvailability();
		return;
	}
	bool configUseTCP=ServerConfig::GetSharedInstance()->GetBoolean("use_tcp", true);
	if(configUseTCP){
		if(avgPongs==0.0 || (udpConnectivityState==UDP_BAD && avgPongs<7.0)){
			if(needRateFlags & NEED_RATE_FLAG_UDP_NA)
				needRate=true;
			udpConnectivityState=UDP_NOT_AVAILABLE;
			useTCP=true;
			useUDP=avgPongs>1.0;
			if(endpoints.at(currentEndpoint).type!=Endpoint::Type::TCP_RELAY)
				setCurrentEndpointToTCP=true;
			AddTCPRelays();
			waitingForRelayPeerInfo=false;
		}else if(avgPongs<3.0){
			if(needRateFlags & NEED_RATE_FLAG_UDP_BAD)
				needRate=true;
			udpConnectivityState=UDP_BAD;
			useTCP=true;
			setCurrentEndpointToTCP=true;
			AddTCPRelays();
			udpPingTimeoutID=messageThread.Post(std::bind(&VoIPController::SendUdpPings, this), 0.5, 0.5);
		}else{
			udpPingTimeoutID=MessageThread::INVALID_ID;
			udpConnectivityState=UDP_AVAILABLE;
		}
	}else{
		udpPingTimeoutID=MessageThread::INVALID_ID;
		udpConnectivityState=UDP_NOT_AVAILABLE;
	}
}

void VoIPController::SendRelayPings(){
    ENFORCE_MSG_THREAD;

	if((state==STATE_ESTABLISHED || state==STATE_RECONNECTING) && endpoints.size()>1){
		Endpoint* _preferredRelay=&endpoints.at(preferredRelay);
		Endpoint* _currentEndpoint=&endpoints.at(currentEndpoint);
		Endpoint* minPingRelay=_preferredRelay;
		double minPing=_preferredRelay->averageRTT*(_preferredRelay->type==Endpoint::Type::TCP_RELAY ? 2 : 1);
		if(minPing==0.0) // force the switch to an available relay, if any
			minPing=DBL_MAX;
		for(pair<const int64_t, Endpoint>& _endpoint:endpoints){
			Endpoint& endpoint=_endpoint.second;
			if(endpoint.type==Endpoint::Type::TCP_RELAY && !useTCP)
				continue;
			if(endpoint.type==Endpoint::Type::UDP_RELAY && !useUDP)
				continue;
			if(GetCurrentTime()-endpoint.lastPingTime>=10){
				LOGV("Sending ping to %s", endpoint.GetAddress().ToString().c_str());
				SendOrEnqueuePacket(PendingOutgoingPacket{
						/*.seq=*/(endpoint.lastPingSeq=GenerateOutSeq()),
						/*.type=*/PKT_PING,
						/*.len=*/0,
						/*.data=*/Buffer(),
						/*.endpoint=*/endpoint.id
				});
				endpoint.lastPingTime=GetCurrentTime();
			}
			if((useUDP && endpoint.type==Endpoint::Type::UDP_RELAY) || (useTCP && endpoint.type==Endpoint::Type::TCP_RELAY)){
				double k=endpoint.type==Endpoint::Type::UDP_RELAY ? 1 : 2;
				if(endpoint.averageRTT>0 && endpoint.averageRTT*k<minPing*relaySwitchThreshold){
					minPing=endpoint.averageRTT*k;
					minPingRelay=&endpoint;
				}
			}
		}
		if(minPingRelay->id!=preferredRelay){
			preferredRelay=minPingRelay->id;
			_preferredRelay=minPingRelay;
			LOGV("set preferred relay to %s", _preferredRelay->address.ToString().c_str());
			if(_currentEndpoint->type==Endpoint::Type::UDP_RELAY || _currentEndpoint->type==Endpoint::Type::TCP_RELAY){
				currentEndpoint=preferredRelay;
				_currentEndpoint=_preferredRelay;
			}
		}
		if(_currentEndpoint->type==Endpoint::Type::UDP_RELAY && useUDP){
			constexpr int64_t p2pID=(int64_t)(FOURCC('P','2','P','4')) << 32;
			constexpr int64_t lanID=(int64_t)(FOURCC('L','A','N','4')) << 32;

			if(endpoints.find(p2pID)!=endpoints.end()){
				Endpoint& p2p=endpoints[p2pID];
				if(endpoints.find(lanID)!=endpoints.end() && endpoints[lanID].averageRTT>0 && endpoints[lanID].averageRTT<minPing*relayToP2pSwitchThreshold){
					currentEndpoint=lanID;
					LOGI("Switching to p2p (LAN)");
				}else{
					if(p2p.averageRTT>0 && p2p.averageRTT<minPing*relayToP2pSwitchThreshold){
						currentEndpoint=p2pID;
						LOGI("Switching to p2p (Inet)");
					}
				}
			}
		}else{
			if(minPing>0 && minPing<_currentEndpoint->averageRTT*p2pToRelaySwitchThreshold){
				LOGI("Switching to relay");
				currentEndpoint=preferredRelay;
			}
		}
	}
}

void VoIPController::UpdateRTT(){
	rttHistory.Add(GetAverageRTT());
	//double v=rttHistory.Average();
	if(rttHistory[0]>10.0 && rttHistory[8]>10.0 && (networkType==NET_TYPE_EDGE || networkType==NET_TYPE_GPRS)){
		waitingForAcks=true;
	}else{
		waitingForAcks=false;
	}
	//LOGI("%.3lf/%.3lf, rtt diff %.3lf, waiting=%d, queue=%d", rttHistory[0], rttHistory[8], v, waitingForAcks, sendQueue->Size());
	for(vector<shared_ptr<Stream>>::iterator stm=incomingStreams.begin();stm!=incomingStreams.end();++stm){
		if((*stm)->jitterBuffer){
			int lostCount=(*stm)->jitterBuffer->GetAndResetLostPacketCount();
			if(lostCount>0 || (lostCount<0 && recvLossCount>((uint32_t) -lostCount)))
				recvLossCount+=lostCount;
		}
	}
}

void VoIPController::UpdateCongestion(){
	if(conctl && encoder){
		uint32_t sendLossCount=conctl->GetSendLossCount();
		sendLossCountHistory.Add(sendLossCount-prevSendLossCount);
		prevSendLossCount=sendLossCount;
		double packetsPerSec=1000/(double) outgoingStreams[0]->frameDuration;
		double avgSendLossCount=sendLossCountHistory.Average()/packetsPerSec;
		//LOGV("avg send loss: %.3f%%", avgSendLossCount*100);

		if(avgSendLossCount>packetLossToEnableExtraEC && networkType!=NET_TYPE_GPRS && networkType!=NET_TYPE_EDGE){
			if(!shittyInternetMode){
				// Shitty Internet Mode™. Redundant redundancy you can trust.
				shittyInternetMode=true;
				for(shared_ptr<Stream> &s:outgoingStreams){
					if(s->type==STREAM_TYPE_AUDIO){
						s->extraECEnabled=true;
						SendStreamFlags(*s);
						break;
					}
				}
				if(encoder)
					encoder->SetSecondaryEncoderEnabled(true);
				LOGW("Enabling extra EC");
				if(needRateFlags & NEED_RATE_FLAG_SHITTY_INTERNET_MODE)
					needRate=true;
				wasExtraEC=true;
			}
		}

		if(avgSendLossCount>0.08){
			extraEcLevel=4;
		}else if(avgSendLossCount>0.05){
			extraEcLevel=3;
		}else if(avgSendLossCount>0.02){
			extraEcLevel=2;
		}else{
			extraEcLevel=0;
		}
		encoder->SetPacketLoss((int)(avgSendLossCount*100.0));
		if(avgSendLossCount>rateMaxAcceptableSendLoss)
			needRate=true;

		if((avgSendLossCount<packetLossToEnableExtraEC || networkType==NET_TYPE_EDGE || networkType==NET_TYPE_GPRS) && shittyInternetMode){
			shittyInternetMode=false;
			for(shared_ptr<Stream> &s:outgoingStreams){
				if(s->type==STREAM_TYPE_AUDIO){
					s->extraECEnabled=false;
					SendStreamFlags(*s);
					break;
				}
			}
			if(encoder)
				encoder->SetSecondaryEncoderEnabled(false);
			LOGW("Disabling extra EC");
		}
		if(!wasEncoderLaggy && encoder->GetComplexity()<10)
			wasEncoderLaggy=true;
	}
}

void VoIPController::UpdateAudioBitrate(){
	if(encoder && conctl){
		double time=GetCurrentTime();
		if((audioInput && !audioInput->IsInitialized()) || (audioOutput && !audioOutput->IsInitialized())){
			LOGE("Audio I/O failed");
			lastError=ERROR_AUDIO_IO;
			SetState(STATE_FAILED);
		}

		int act=conctl->GetBandwidthControlAction();
		if(shittyInternetMode){
			encoder->SetBitrate(8000);
		}else if(act==TGVOIP_CONCTL_ACT_DECREASE){
			uint32_t bitrate=encoder->GetBitrate();
			if(bitrate>8000)
				encoder->SetBitrate(bitrate<(minAudioBitrate+audioBitrateStepDecr) ? minAudioBitrate : (bitrate-audioBitrateStepDecr));
		}else if(act==TGVOIP_CONCTL_ACT_INCREASE){
			uint32_t bitrate=encoder->GetBitrate();
			if(bitrate<maxBitrate)
				encoder->SetBitrate(bitrate+audioBitrateStepIncr);
		}

		if(state==STATE_ESTABLISHED && time-lastRecvPacketTime>=reconnectingTimeout){
			SetState(STATE_RECONNECTING);
			if(needRateFlags & NEED_RATE_FLAG_RECONNECTING)
				needRate=true;
			wasReconnecting=true;
			ResetUdpAvailability();
		}

		if(state==STATE_ESTABLISHED || state==STATE_RECONNECTING){
			if(time-lastRecvPacketTime>=config.recvTimeout){
				const Endpoint& _currentEndpoint=endpoints.at(currentEndpoint);
				if(_currentEndpoint.type!=Endpoint::Type::UDP_RELAY && _currentEndpoint.type!=Endpoint::Type::TCP_RELAY){
					LOGW("Packet receive timeout, switching to relay");
					currentEndpoint=preferredRelay;
					for(pair<const int64_t, Endpoint>& _e:endpoints){
						Endpoint& e=_e.second;
						if(e.type==Endpoint::Type::UDP_P2P_INET || e.type==Endpoint::Type::UDP_P2P_LAN){
							e.averageRTT=0;
							e.rtts.Reset();
						}
					}
					if(allowP2p){
						SendPublicEndpointsRequest();
					}
					UpdateDataSavingState();
					UpdateAudioBitrateLimit();
					BufferOutputStream s(4);
					s.WriteInt32(dataSavingMode ? INIT_FLAG_DATA_SAVING_ENABLED : 0);
					if(peerVersion<6){
						SendPacketReliably(PKT_NETWORK_CHANGED, s.GetBuffer(), s.GetLength(), 1, 20);
					}else{
						Buffer buf(move(s));
						SendExtra(buf, EXTRA_TYPE_NETWORK_CHANGED);
					}
					lastRecvPacketTime=time;
				}else{
					LOGW("Packet receive timeout, disconnecting");
					lastError=ERROR_TIMEOUT;
					SetState(STATE_FAILED);
				}
			}
		}
	}
}

void VoIPController::UpdateSignalBars(){
	int prevSignalBarCount=GetSignalBarsCount();
	double packetsPerSec=1000/(double) outgoingStreams[0]->frameDuration;
	double avgSendLossCount=sendLossCountHistory.Average()/packetsPerSec;

	int signalBarCount=4;
	if(state==STATE_RECONNECTING || waitingForAcks)
		signalBarCount=1;
	if(endpoints.at(currentEndpoint).type==Endpoint::Type::TCP_RELAY){
		signalBarCount=std::min(signalBarCount, 3);
	}
	if(avgSendLossCount>0.1){
		signalBarCount=1;
	}else if(avgSendLossCount>0.0625){
		signalBarCount=std::min(signalBarCount, 2);
	}else if(avgSendLossCount>0.025){
		signalBarCount=std::min(signalBarCount, 3);
	}

	for(shared_ptr<Stream>& stm:incomingStreams){
		if(stm->jitterBuffer){
			double avgLateCount[3];
			stm->jitterBuffer->GetAverageLateCount(avgLateCount);
			if(avgLateCount[2]>=0.2)
				signalBarCount=1;
			else if(avgLateCount[2]>=0.1)
				signalBarCount=std::min(signalBarCount, 2);
		}
	}

	signalBarsHistory.Add(static_cast<unsigned char>(signalBarCount));
	//LOGV("Signal bar count history %08X", *reinterpret_cast<uint32_t *>(&signalBarsHistory));
	int _signalBarCount=GetSignalBarsCount();
	if(_signalBarCount!=prevSignalBarCount){
		LOGD("SIGNAL BAR COUNT CHANGED: %d", _signalBarCount);
		if(callbacks.signalBarCountChanged)
			callbacks.signalBarCountChanged(this, _signalBarCount);
	}
}

void VoIPController::UpdateQueuedPackets(){
	vector<PendingOutgoingPacket> packetsToSend;
	for(std::vector<QueuedPacket>::iterator qp=queuedPackets.begin(); qp!=queuedPackets.end();){
		if(qp->timeout>0 && qp->firstSentTime>0 && GetCurrentTime()-qp->firstSentTime>=qp->timeout){
			LOGD("Removing queued packet because of timeout");
			qp=queuedPackets.erase(qp);
			continue;
		}
		if(GetCurrentTime()-qp->lastSentTime>=qp->retryInterval){
			messageThread.Post(std::bind(&VoIPController::UpdateQueuedPackets, this), qp->retryInterval);
			uint32_t seq=GenerateOutSeq();
			qp->seqs.Add(seq);
			qp->lastSentTime=GetCurrentTime();
			//LOGD("Sending queued packet, seq=%u, type=%u, len=%u", seq, qp.type, qp.data.Length());
			Buffer buf(qp->data.Length());
			if(qp->firstSentTime==0)
				qp->firstSentTime=qp->lastSentTime;
			if(qp->data.Length())
				buf.CopyFrom(qp->data, qp->data.Length());
			packetsToSend.push_back(PendingOutgoingPacket{
					/*.seq=*/seq,
					/*.type=*/qp->type,
					/*.len=*/qp->data.Length(),
					/*.data=*/move(buf),
					/*.endpoint=*/0
			});
		}
		++qp;
	}
	for(PendingOutgoingPacket& pkt:packetsToSend){
		SendOrEnqueuePacket(move(pkt));
	}
}

void VoIPController::SendNopPacket(){
	if(state!=STATE_ESTABLISHED)
		return;
	SendOrEnqueuePacket(PendingOutgoingPacket{
			/*.seq=*/(firstSentPing=GenerateOutSeq()),
			/*.type=*/PKT_NOP,
			/*.len=*/0,
			/*.data=*/Buffer(),
			/*.endpoint=*/0
	});
}

void VoIPController::SendPublicEndpointsRequest(){
	ENFORCE_MSG_THREAD;
	if(!allowP2p)
		return;
	LOGI("Sending public endpoints request");
	for(pair<const int64_t, Endpoint>& e:endpoints){
		if(e.second.type==Endpoint::Type::UDP_RELAY && !e.second.IsIPv6Only()){
			SendPublicEndpointsRequest(e.second);
		}
	}
	publicEndpointsReqCount++;
	if(publicEndpointsReqCount<10){
		messageThread.Post([this]{
			if(waitingForRelayPeerInfo){
				LOGW("Resending peer relay info request");
				SendPublicEndpointsRequest();
			}
		}, 5.0);
	}else{
		publicEndpointsReqCount=0;
	}
}

void VoIPController::TickJitterBufferAndCongestionControl(){
	// TODO get rid of this and update states of these things internally and retroactively
	for(shared_ptr<Stream>& stm:incomingStreams){
		if(stm->jitterBuffer){
			stm->jitterBuffer->Tick();
		}
	}
	if(conctl){
		conctl->Tick();
	}

	//MutexGuard m(queuedPacketsMutex);
	double currentTime=GetCurrentTime();
	double rtt=GetAverageRTT();
	double packetLossTimeout=std::max(rtt*2.0, 0.1);
	for(RecentOutgoingPacket& pkt:recentOutgoingPackets){
		if(pkt.ackTime!=0.0 || pkt.lost)
			continue;
		if(currentTime-pkt.sendTime>packetLossTimeout){
			pkt.lost=true;
			sendLosses++;
			LOGW("Outgoing packet lost: seq=%u, type=%s, size=%u", pkt.seq, GetPacketTypeString(pkt.type).c_str(), (unsigned int)pkt.size);
			if(pkt.sender){
				pkt.sender->PacketLost(pkt.seq, pkt.type, pkt.size);
			}else if(pkt.type==PKT_STREAM_DATA){
				conctl->PacketLost(pkt.seq);
			}
		}
	}
}

#pragma mark - Endpoint

Endpoint::Endpoint(int64_t id, uint16_t port, const IPv4Address& _address, const IPv6Address& _v6address, Type type, unsigned char peerTag[16]) : address(NetworkAddress::IPv4(_address.addr)), v6address(NetworkAddress::IPv6(_v6address.addr)){
	this->id=id;
	this->port=port;
	this->type=type;
	memcpy(this->peerTag, peerTag, 16);
	if(type==Type::UDP_RELAY && ServerConfig::GetSharedInstance()->GetBoolean("force_tcp", false))
		this->type=Type::TCP_RELAY;

	lastPingSeq=0;
	lastPingTime=0;
	averageRTT=0;
	socket=NULL;
	udpPongCount=0;
}

Endpoint::Endpoint(int64_t id, uint16_t port, const NetworkAddress _address, const NetworkAddress _v6address, Type type, unsigned char peerTag[16]) : address(_address), v6address(_v6address){
	this->id=id;
	this->port=port;
	this->type=type;
	memcpy(this->peerTag, peerTag, 16);
	if(type==Type::UDP_RELAY && ServerConfig::GetSharedInstance()->GetBoolean("force_tcp", false))
		this->type=Type::TCP_RELAY;

	lastPingSeq=0;
	lastPingTime=0;
	averageRTT=0;
	socket=NULL;
	udpPongCount=0;
}

Endpoint::Endpoint() : address(NetworkAddress::Empty()), v6address(NetworkAddress::Empty()){
	lastPingSeq=0;
	lastPingTime=0;
	averageRTT=0;
	socket=NULL;
	udpPongCount=0;
}

const NetworkAddress &Endpoint::GetAddress() const{
	return IsIPv6Only() ? (NetworkAddress&)v6address : (NetworkAddress&)address;
}

NetworkAddress &Endpoint::GetAddress(){
	return IsIPv6Only() ? (NetworkAddress&)v6address : (NetworkAddress&)address;
}

bool Endpoint::IsIPv6Only() const{
	return address.IsEmpty() && !v6address.IsEmpty();
}

int64_t Endpoint::CleanID() const{
	int64_t _id=id;
	if(type==Type::TCP_RELAY){
		_id=_id ^ ((int64_t)FOURCC('T','C','P',' ') << 32);
	}
	if(IsIPv6Only()){
		_id=_id ^ ((int64_t)FOURCC('I','P','v','6') << 32);
	}
	return _id;
}

Endpoint::~Endpoint(){
	if(socket){
		socket->Close();
	}
}

#pragma mark - AudioInputTester

AudioInputTester::AudioInputTester(std::string deviceID) : deviceID(std::move(deviceID)){
	io=audio::AudioIO::Create(deviceID, "default");
	if(io->Failed()){
		LOGE("Audio IO failed");
		return;
	}
	input=io->GetInput();
	input->SetCallback([](unsigned char* data, size_t size, void* ctx) -> size_t{
		reinterpret_cast<AudioInputTester*>(ctx)->Update(reinterpret_cast<int16_t*>(data), size/2);
		return 0;
	}, this);
	input->Start();
}

AudioInputTester::~AudioInputTester(){
	input->Stop();
	delete io;
}

void AudioInputTester::Update(int16_t *samples, size_t count){
	for(size_t i=0;i<count;i++){
		int16_t s=abs(samples[i]);
		if(s>maxSample)
			maxSample=s;
	}
}

float AudioInputTester::GetAndResetLevel(){
	float s=maxSample;
	maxSample=0;
	return s/(float)INT16_MAX;
}
