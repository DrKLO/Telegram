//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <jni.h>
#include <string.h>
#include <map>
#include <string>
#include <vector>
#include "../../VoIPServerConfig.h"
#include "../../VoIPController.h"
#include "../../os/android/AudioOutputOpenSLES.h"
#include "../../os/android/AudioInputOpenSLES.h"
#include "../../os/android/AudioInputAndroid.h"
#include "../../os/android/AudioOutputAndroid.h"
#include "../../os/android/VideoSourceAndroid.h"
#include "../../os/android/VideoRendererAndroid.h"
#include "../../audio/Resampler.h"
#include "../../os/android/JNIUtilities.h"
#include "../../PrivateDefines.h"
#include "../../logging.h"

#ifdef TGVOIP_HAS_CONFIG
#include <tgvoip_config.h>
#endif

JavaVM* sharedJVM;
jfieldID audioRecordInstanceFld=NULL;
jfieldID audioTrackInstanceFld=NULL;
jmethodID setStateMethod=NULL;
jmethodID setSignalBarsMethod=NULL;
jmethodID setSelfStreamsMethod=NULL;
jmethodID setParticipantAudioEnabledMethod=NULL;
jmethodID groupCallKeyReceivedMethod=NULL;
jmethodID groupCallKeySentMethod=NULL;
jmethodID callUpgradeRequestReceivedMethod=NULL;
jclass jniUtilitiesClass=NULL;

struct ImplDataAndroid{
	jobject javaObject;
	std::string persistentStateFile="";
};

#ifndef TGVOIP_PACKAGE_PATH
#define TGVOIP_PACKAGE_PATH "org/telegram/messenger/voip"
#endif

#ifndef TGVOIP_PEER_TAG_VARIABLE_NAME
#define TGVOIP_PEER_TAG_VARIABLE_NAME "peer_tag"
#endif

#ifndef TGVOIP_ENDPOINT_CLASS
#define TGVOIP_ENDPOINT_CLASS "org/telegram/tgnet/TLRPC$TL_phoneConnection"
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

namespace tgvoip {
#pragma mark - Callbacks

	void updateConnectionState(VoIPController *cntrlr, int state){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(setStateMethod, impl->javaObject, state);
	}

	void updateSignalBarCount(VoIPController *cntrlr, int count){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(setSignalBarsMethod, impl->javaObject, count);
	}

	void updateGroupCallStreams(VoIPGroupController *cntrlr, unsigned char *streams, size_t len){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		if(!impl->javaObject)
			return;
		jni::DoWithJNI([streams, len, &impl](JNIEnv* env){
			if(setSelfStreamsMethod){
				jbyteArray jstreams=env->NewByteArray(static_cast<jsize>(len));
				jbyte *el=env->GetByteArrayElements(jstreams, NULL);
				memcpy(el, streams, len);
				env->ReleaseByteArrayElements(jstreams, el, 0);
				env->CallVoidMethod(impl->javaObject, setSelfStreamsMethod, jstreams);
			}
		});
	}

	void groupCallKeyReceived(VoIPController *cntrlr, const unsigned char *key){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		if(!impl->javaObject)
			return;
		jni::DoWithJNI([key, &impl](JNIEnv* env){
			if(groupCallKeyReceivedMethod){
				jbyteArray jkey=env->NewByteArray(256);
				jbyte *el=env->GetByteArrayElements(jkey, NULL);
				memcpy(el, key, 256);
				env->ReleaseByteArrayElements(jkey, el, 0);
				env->CallVoidMethod(impl->javaObject, groupCallKeyReceivedMethod, jkey);
			}
		});
	}

	void groupCallKeySent(VoIPController *cntrlr){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(groupCallKeySentMethod, impl->javaObject);
	}

	void callUpgradeRequestReceived(VoIPController* cntrlr){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(callUpgradeRequestReceivedMethod, impl->javaObject);
	}

	void updateParticipantAudioState(VoIPGroupController *cntrlr, int32_t userID, bool enabled){
		ImplDataAndroid *impl=(ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(setParticipantAudioEnabledMethod, impl->javaObject, userID, enabled);
	}

#pragma mark - VoIPController

	uint32_t AndroidCodecToFOURCC(std::string mime){
		if(mime=="video/avc")
			return CODEC_AVC;
		else if(mime=="video/hevc")
			return CODEC_HEVC;
		else if(mime=="video/x-vnd.on2.vp8")
			return CODEC_VP8;
		else if(mime=="video/x-vnd.on2.vp9")
			return CODEC_VP9;
		return 0;
	}

	jlong VoIPController_nativeInit(JNIEnv* env, jobject thiz, jstring persistentStateFile) {
		ImplDataAndroid* impl=new ImplDataAndroid();
		impl->javaObject=env->NewGlobalRef(thiz);
		if(persistentStateFile){
			impl->persistentStateFile=jni::JavaStringToStdString(env, persistentStateFile);
		}
		VoIPController* cntrlr=new VoIPController();
		cntrlr->implData=impl;
		VoIPController::Callbacks callbacks;
		callbacks.connectionStateChanged=updateConnectionState;
		callbacks.signalBarCountChanged=updateSignalBarCount;
		callbacks.groupCallKeyReceived=groupCallKeyReceived;
		callbacks.groupCallKeySent=groupCallKeySent;
		callbacks.upgradeToGroupCallRequested=callUpgradeRequestReceived;
		cntrlr->SetCallbacks(callbacks);
		if(!impl->persistentStateFile.empty()){
			FILE* f=fopen(impl->persistentStateFile.c_str(), "r");
			if(f){
				fseek(f, 0, SEEK_END);
				size_t len=static_cast<size_t>(ftell(f));
				fseek(f, 0, SEEK_SET);
				if(len<1024*512 && len>0){
					char *fbuf=static_cast<char *>(malloc(len));
					fread(fbuf, 1, len, f);
					std::vector<uint8_t> state(fbuf, fbuf+len);
					free(fbuf);
					cntrlr->SetPersistentState(state);
				}
				fclose(f);
			}
		}
		/*if(video::VideoRendererAndroid::availableDecoders.empty() || video::VideoSourceAndroid::availableEncoders.empty()){
			video::VideoRendererAndroid::availableDecoders.clear();
			video::VideoSourceAndroid::availableEncoders.clear();
			jmethodID getCodecsMethod=env->GetStaticMethodID(jniUtilitiesClass, "getSupportedVideoCodecs", "()[[Ljava/lang/String;");
			jobjectArray codecs=static_cast<jobjectArray>(env->CallStaticObjectMethod(jniUtilitiesClass, getCodecsMethod));
			jobjectArray encoders=static_cast<jobjectArray>(env->GetObjectArrayElement(codecs, 0));
			jobjectArray decoders=static_cast<jobjectArray>(env->GetObjectArrayElement(codecs, 1));
			for(jsize i=0;i<env->GetArrayLength(encoders);i++){
				std::string codec=jni::JavaStringToStdString(env, static_cast<jstring>(env->GetObjectArrayElement(encoders, i)));
				uint32_t id=AndroidCodecToFOURCC(codec);
				if(id)
					video::VideoSourceAndroid::availableEncoders.push_back(id);
			}
			for(jsize i=0;i<env->GetArrayLength(decoders);i++){
				std::string codec=jni::JavaStringToStdString(env, static_cast<jstring>(env->GetObjectArrayElement(decoders, i)));
				uint32_t id=AndroidCodecToFOURCC(codec);
				if(id)
					video::VideoRendererAndroid::availableDecoders.push_back(id);
			}
			jmethodID getMaxResolutionMethod=env->GetStaticMethodID(jniUtilitiesClass, "getMaxVideoResolution", "()I");
			video::VideoRendererAndroid::maxResolution=env->CallStaticIntMethod(jniUtilitiesClass, getMaxResolutionMethod);
		}*/
		return (jlong)(intptr_t)cntrlr;
	}

	void VoIPController_nativeStart(JNIEnv* env, jobject thiz, jlong inst){
		((VoIPController*)(intptr_t)inst)->Start();
	}

	void VoIPController_nativeConnect(JNIEnv* env, jobject thiz, jlong inst){
		((VoIPController*)(intptr_t)inst)->Connect();
	}

	void VoIPController_nativeSetProxy(JNIEnv* env, jobject thiz, jlong inst, jstring _address, jint port, jstring _username, jstring _password){
		((VoIPController*)(intptr_t)inst)->SetProxy(PROXY_SOCKS5, jni::JavaStringToStdString(env, _address), (uint16_t)port, jni::JavaStringToStdString(env, _username), jni::JavaStringToStdString(env, _password));
	}

	void VoIPController_nativeSetEncryptionKey(JNIEnv* env, jobject thiz, jlong inst, jbyteArray key, jboolean isOutgoing){
		jbyte* akey=env->GetByteArrayElements(key, NULL);
		((VoIPController*)(intptr_t)inst)->SetEncryptionKey((char *) akey, isOutgoing);
		env->ReleaseByteArrayElements(key, akey, JNI_ABORT);
	}

	void VoIPController_nativeSetRemoteEndpoints(JNIEnv* env, jobject thiz, jlong inst, jobjectArray endpoints, jboolean allowP2p, jboolean tcp, jint connectionMaxLayer){
		size_t len=(size_t) env->GetArrayLength(endpoints);
		std::vector<Endpoint> eps;
		/*public String ip;
			public String ipv6;
			public int port;
			public byte[] peer_tag;*/
		jclass epClass=env->GetObjectClass(env->GetObjectArrayElement(endpoints, 0));
		jfieldID ipFld=env->GetFieldID(epClass, "ip", "Ljava/lang/String;");
		jfieldID ipv6Fld=env->GetFieldID(epClass, "ipv6", "Ljava/lang/String;");
		jfieldID portFld=env->GetFieldID(epClass, "port", "I");
		jfieldID peerTagFld=env->GetFieldID(epClass, TGVOIP_PEER_TAG_VARIABLE_NAME, "[B");
		jfieldID idFld=env->GetFieldID(epClass, "id", "J");
		int i;
		for(i=0;i<len;i++){
			jobject endpoint=env->GetObjectArrayElement(endpoints, i);
			jstring ip=(jstring) env->GetObjectField(endpoint, ipFld);
			jstring ipv6=(jstring) env->GetObjectField(endpoint, ipv6Fld);
			jint port=env->GetIntField(endpoint, portFld);
			jlong id=env->GetLongField(endpoint, idFld);
			jbyteArray peerTag=(jbyteArray) env->GetObjectField(endpoint, peerTagFld);
			IPv4Address v4addr(jni::JavaStringToStdString(env, ip));
			IPv6Address v6addr("::0");
			if(ipv6 && env->GetStringLength(ipv6)){
				v6addr=IPv6Address(jni::JavaStringToStdString(env, ipv6));
			}
			unsigned char pTag[16];
			if(peerTag && env->GetArrayLength(peerTag)){
				jbyte* peerTagBytes=env->GetByteArrayElements(peerTag, NULL);
				memcpy(pTag, peerTagBytes, 16);
				env->ReleaseByteArrayElements(peerTag, peerTagBytes, JNI_ABORT);
			}
			eps.push_back(Endpoint((int64_t)id, (uint16_t)port, v4addr, v6addr, tcp ? Endpoint::Type::TCP_RELAY : Endpoint::Type::UDP_RELAY, pTag));
		}
		((VoIPController*)(intptr_t)inst)->SetRemoteEndpoints(eps, allowP2p, connectionMaxLayer);
	}

	void VoIPController_nativeSetNativeBufferSize(JNIEnv* env, jclass thiz, jint size){
		AudioOutputOpenSLES::nativeBufferSize=(unsigned int) size;
		AudioInputOpenSLES::nativeBufferSize=(unsigned int) size;
	}

	void VoIPController_nativeRelease(JNIEnv* env, jobject thiz, jlong inst){
		//env->DeleteGlobalRef(AudioInputAndroid::jniClass);

		VoIPController* ctlr=((VoIPController*)(intptr_t)inst);
		ImplDataAndroid* impl=(ImplDataAndroid*)ctlr->implData;
		ctlr->Stop();
		std::vector<uint8_t> state=ctlr->GetPersistentState();
		delete ctlr;
		env->DeleteGlobalRef(impl->javaObject);
		if(!impl->persistentStateFile.empty()){
			FILE* f=fopen(impl->persistentStateFile.c_str(), "w");
			if(f){
				fwrite(state.data(), 1, state.size(), f);
				fclose(f);
			}
		}
		delete impl;
	}

	jstring VoIPController_nativeGetDebugString(JNIEnv* env, jobject thiz, jlong inst){
		std::string str=((VoIPController*)(intptr_t)inst)->GetDebugString();
		return env->NewStringUTF(str.c_str());
	}

	void VoIPController_nativeSetNetworkType(JNIEnv* env, jobject thiz, jlong inst, jint type){
		((VoIPController*)(intptr_t)inst)->SetNetworkType(type);
	}

	void VoIPController_nativeSetMicMute(JNIEnv* env, jobject thiz, jlong inst, jboolean mute){
		((VoIPController*)(intptr_t)inst)->SetMicMute(mute);
	}

	void VoIPController_nativeSetConfig(JNIEnv* env, jobject thiz, jlong inst, jdouble recvTimeout, jdouble initTimeout, jint dataSavingMode, jboolean enableAEC, jboolean enableNS, jboolean enableAGC, jstring logFilePath, jstring statsDumpPath, jboolean logPacketStats){
		VoIPController::Config cfg;
		cfg.initTimeout=initTimeout;
		cfg.recvTimeout=recvTimeout;
		cfg.dataSaving=dataSavingMode;
		cfg.enableAEC=enableAEC;
		cfg.enableNS=enableNS;
		cfg.enableAGC=enableAGC;
		cfg.enableCallUpgrade=false;
		cfg.logPacketStats=logPacketStats;
		if(logFilePath){
			cfg.logFilePath=jni::JavaStringToStdString(env, logFilePath);
		}
		if(statsDumpPath){
			cfg.statsDumpFilePath=jni::JavaStringToStdString(env, statsDumpPath);
		}

		((VoIPController*)(intptr_t)inst)->SetConfig(cfg);
	}

	void VoIPController_nativeDebugCtl(JNIEnv* env, jobject thiz, jlong inst, jint request, jint param){
		((VoIPController*)(intptr_t)inst)->DebugCtl(request, param);
	}

	jstring VoIPController_nativeGetVersion(JNIEnv* env, jclass clasz){
		return env->NewStringUTF(VoIPController::GetVersion());
	}

	jlong VoIPController_nativeGetPreferredRelayID(JNIEnv* env, jclass clasz, jlong inst){
		return ((VoIPController*)(intptr_t)inst)->GetPreferredRelayID();
	}

	jint VoIPController_nativeGetLastError(JNIEnv* env, jclass clasz, jlong inst){
		return ((VoIPController*)(intptr_t)inst)->GetLastError();
	}

	void VoIPController_nativeGetStats(JNIEnv* env, jclass clasz, jlong inst, jobject stats){
		VoIPController::TrafficStats _stats;
		((VoIPController*)(intptr_t)inst)->GetStats(&_stats);
		jclass cls=env->GetObjectClass(stats);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesSentWifi", "J"), _stats.bytesSentWifi);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesSentMobile", "J"), _stats.bytesSentMobile);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesRecvdWifi", "J"), _stats.bytesRecvdWifi);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesRecvdMobile", "J"), _stats.bytesRecvdMobile);
	}

	jstring VoIPController_nativeGetDebugLog(JNIEnv* env, jobject thiz, jlong inst){
		VoIPController* ctlr=((VoIPController*)(intptr_t)inst);
		std::string log=ctlr->GetDebugLog();
		return env->NewStringUTF(log.c_str());
	}

	void VoIPController_nativeSetAudioOutputGainControlEnabled(JNIEnv* env, jclass clasz, jlong inst, jboolean enabled){
		((VoIPController*)(intptr_t)inst)->SetAudioOutputGainControlEnabled(enabled);
	}

	void VoIPController_nativeSetEchoCancellationStrength(JNIEnv* env, jclass cls, jlong inst, jint strength){
		((VoIPController*)(intptr_t)inst)->SetEchoCancellationStrength(strength);
	}

	jint VoIPController_nativeGetPeerCapabilities(JNIEnv* env, jclass cls, jlong inst){
		return ((VoIPController*)(intptr_t)inst)->GetPeerCapabilities();
	}

	void VoIPController_nativeSendGroupCallKey(JNIEnv* env, jclass cls, jlong inst, jbyteArray _key){
		jbyte* key=env->GetByteArrayElements(_key, NULL);
		((VoIPController*)(intptr_t)inst)->SendGroupCallKey((unsigned char *) key);
		env->ReleaseByteArrayElements(_key, key, JNI_ABORT);
	}

	void VoIPController_nativeRequestCallUpgrade(JNIEnv* env, jclass cls, jlong inst){
		((VoIPController*)(intptr_t)inst)->RequestCallUpgrade();
	}

	void VoIPController_nativeSetVideoSource(JNIEnv* env, jobject thiz, jlong inst, jlong source){
		((VoIPController*)(intptr_t)inst)->SetVideoSource((video::VideoSource*)(intptr_t)source);
	}

	void VoIPController_nativeSetVideoRenderer(JNIEnv* env, jobject thiz, jlong inst, jlong renderer){
		((VoIPController*)(intptr_t)inst)->SetVideoRenderer((video::VideoRenderer*)(intptr_t)renderer);
	}

	jboolean VoIPController_nativeNeedRate(JNIEnv* env, jclass cls, jlong inst){
		return static_cast<jboolean>(((VoIPController*)(intptr_t)inst)->NeedRate());
	}

	jint VoIPController_getConnectionMaxLayer(JNIEnv* env, jclass cls){
		return VoIPController::GetConnectionMaxLayer();
	}

#pragma mark - AudioRecordJNI

	void AudioRecordJNI_nativeCallback(JNIEnv* env, jobject thiz, jobject buffer){
		jlong inst=env->GetLongField(thiz, audioRecordInstanceFld);
		AudioInputAndroid* in=(AudioInputAndroid*)(intptr_t)inst;
		in->HandleCallback(env, buffer);
	}

#pragma mark - AudioTrackJNI

	void AudioTrackJNI_nativeCallback(JNIEnv* env, jobject thiz, jbyteArray buffer){
		jlong inst=env->GetLongField(thiz, audioTrackInstanceFld);
		AudioOutputAndroid* in=(AudioOutputAndroid*)(intptr_t)inst;
		in->HandleCallback(env, buffer);
	}

#pragma mark - VoIPServerConfig

	void VoIPServerConfig_nativeSetConfig(JNIEnv* env, jclass clasz, jstring jsonString){
		ServerConfig::GetSharedInstance()->Update(jni::JavaStringToStdString(env, jsonString));
	}

#pragma mark - Resampler

	jint Resampler_convert44to48(JNIEnv* env, jclass cls, jobject from, jobject to){
		return (jint)tgvoip::audio::Resampler::Convert44To48((int16_t *) env->GetDirectBufferAddress(from), (int16_t *) env->GetDirectBufferAddress(to), (size_t) (env->GetDirectBufferCapacity(from)/2), (size_t) (env->GetDirectBufferCapacity(to)/2));
	}

	jint Resampler_convert48to44(JNIEnv* env, jclass cls, jobject from, jobject to){
		return (jint)tgvoip::audio::Resampler::Convert48To44((int16_t *) env->GetDirectBufferAddress(from), (int16_t *) env->GetDirectBufferAddress(to), (size_t) (env->GetDirectBufferCapacity(from)/2), (size_t) (env->GetDirectBufferCapacity(to)/2));
	}

#pragma mark - VoIPGroupController

#ifndef TGVOIP_NO_GROUP_CALLS
	jlong VoIPGroupController_nativeInit(JNIEnv* env, jobject thiz, jint timeDifference){
		ImplDataAndroid* impl=(ImplDataAndroid*) malloc(sizeof(ImplDataAndroid));
		impl->javaObject=env->NewGlobalRef(thiz);
		VoIPGroupController* cntrlr=new VoIPGroupController(timeDifference);
		cntrlr->implData=impl;

		VoIPGroupController::Callbacks callbacks;
		callbacks.connectionStateChanged=updateConnectionState;
		callbacks.updateStreams=updateGroupCallStreams;
		callbacks.participantAudioStateChanged=updateParticipantAudioState;
		callbacks.signalBarCountChanged=NULL;
		cntrlr->SetCallbacks(callbacks);

		return (jlong)(intptr_t)cntrlr;
	}

	void VoIPGroupController_nativeSetGroupCallInfo(JNIEnv* env, jclass cls, jlong inst, jbyteArray _encryptionKey, jbyteArray _reflectorGroupTag, jbyteArray _reflectorSelfTag, jbyteArray _reflectorSelfSecret, jbyteArray _reflectorSelfTagHash, jint selfUserID, jstring reflectorAddress, jstring reflectorAddressV6, jint reflectorPort){
		VoIPGroupController* ctlr=((VoIPGroupController*)(intptr_t)inst);
		jbyte* encryptionKey=env->GetByteArrayElements(_encryptionKey, NULL);
		jbyte* reflectorGroupTag=env->GetByteArrayElements(_reflectorGroupTag, NULL);
		jbyte* reflectorSelfTag=env->GetByteArrayElements(_reflectorSelfTag, NULL);
		jbyte* reflectorSelfSecret=env->GetByteArrayElements(_reflectorSelfSecret, NULL);
		jbyte* reflectorSelfTagHash=env->GetByteArrayElements(_reflectorSelfTagHash, NULL);


		const char* ipChars=env->GetStringUTFChars(reflectorAddress, NULL);
		std::string ipLiteral(ipChars);
		IPv4Address v4addr(ipLiteral);
		IPv6Address v6addr("::0");
		env->ReleaseStringUTFChars(reflectorAddress, ipChars);
		if(reflectorAddressV6 && env->GetStringLength(reflectorAddressV6)){
			const char* ipv6Chars=env->GetStringUTFChars(reflectorAddressV6, NULL);
			v6addr=IPv6Address(ipv6Chars);
			env->ReleaseStringUTFChars(reflectorAddressV6, ipv6Chars);
		}
		ctlr->SetGroupCallInfo((unsigned char *) encryptionKey, (unsigned char *) reflectorGroupTag, (unsigned char *) reflectorSelfTag, (unsigned char *) reflectorSelfSecret, (unsigned char*) reflectorSelfTagHash, selfUserID, v4addr, v6addr, (uint16_t)reflectorPort);

		env->ReleaseByteArrayElements(_encryptionKey, encryptionKey, JNI_ABORT);
		env->ReleaseByteArrayElements(_reflectorGroupTag, reflectorGroupTag, JNI_ABORT);
		env->ReleaseByteArrayElements(_reflectorSelfTag, reflectorSelfTag, JNI_ABORT);
		env->ReleaseByteArrayElements(_reflectorSelfSecret, reflectorSelfSecret, JNI_ABORT);
		env->ReleaseByteArrayElements(_reflectorSelfTagHash, reflectorSelfTagHash, JNI_ABORT);
	}

	void VoIPGroupController_nativeAddGroupCallParticipant(JNIEnv* env, jclass cls, jlong inst, jint userID, jbyteArray _memberTagHash, jbyteArray _streams){
		VoIPGroupController* ctlr=((VoIPGroupController*)(intptr_t)inst);
		jbyte* memberTagHash=env->GetByteArrayElements(_memberTagHash, NULL);
		jbyte* streams=_streams ? env->GetByteArrayElements(_streams, NULL) : NULL;

		ctlr->AddGroupCallParticipant(userID, (unsigned char *) memberTagHash, (unsigned char *) streams, (size_t) env->GetArrayLength(_streams));

		env->ReleaseByteArrayElements(_memberTagHash, memberTagHash, JNI_ABORT);
		if(_streams)
			env->ReleaseByteArrayElements(_streams, streams, JNI_ABORT);

	}

	void VoIPGroupController_nativeRemoveGroupCallParticipant(JNIEnv* env, jclass cls, jlong inst, jint userID){
		VoIPGroupController* ctlr=((VoIPGroupController*)(intptr_t)inst);
		ctlr->RemoveGroupCallParticipant(userID);
	}

	jfloat VoIPGroupController_nativeGetParticipantAudioLevel(JNIEnv* env, jclass cls, jlong inst, jint userID){
		return ((VoIPGroupController*)(intptr_t)inst)->GetParticipantAudioLevel(userID);
	}

	void VoIPGroupController_nativeSetParticipantVolume(JNIEnv* env, jclass cls, jlong inst, jint userID, jfloat volume){
		((VoIPGroupController*)(intptr_t)inst)->SetParticipantVolume(userID, volume);
	}

	jbyteArray VoIPGroupController_getInitialStreams(JNIEnv* env, jclass cls){
		unsigned char buf[1024];
		size_t len=VoIPGroupController::GetInitialStreams(buf, sizeof(buf));
		jbyteArray arr=env->NewByteArray(len);
		jbyte* arrElems=env->GetByteArrayElements(arr, NULL);
		memcpy(arrElems, buf, len);
		env->ReleaseByteArrayElements(arr, arrElems, 0);
		return arr;
	}

	void VoIPGroupController_nativeSetParticipantStreams(JNIEnv* env, jclass cls, jlong inst, jint userID, jbyteArray _streams){
		jbyte* streams=env->GetByteArrayElements(_streams, NULL);

		((VoIPGroupController*)(intptr_t)inst)->SetParticipantStreams(userID, (unsigned char *) streams, (size_t) env->GetArrayLength(_streams));

		env->ReleaseByteArrayElements(_streams, streams, JNI_ABORT);
	}
#endif

#pragma mark - VideoSource

	jlong VideoSource_nativeInit(JNIEnv* env, jobject thiz){
		return (jlong)(intptr_t)new video::VideoSourceAndroid(env->NewGlobalRef(thiz));
	}

	void VideoSource_nativeRelease(JNIEnv* env, jobject thiz, jlong inst){
		delete (video::VideoSource*)(intptr_t)inst;
	}

	void VideoSource_nativeSetVideoStreamParameters(JNIEnv* env, jobject thiz, jlong inst, jobjectArray _csd, jint width, jint height){
		std::vector<Buffer> csd;
		if(_csd){
			for(int i=0; i<env->GetArrayLength(_csd); i++){
				jobject _buf=env->GetObjectArrayElement(_csd, i);
				size_t len=static_cast<size_t>(env->GetDirectBufferCapacity(_buf));
				Buffer buf(len);
				buf.CopyFrom(env->GetDirectBufferAddress(_buf), 0, len);
				csd.push_back(std::move(buf));
			}
		}
		((video::VideoSourceAndroid*)(intptr_t)inst)->SetStreamParameters(std::move(csd), width, height);
	}

	void VideoSource_nativeSendFrame(JNIEnv* env, jobject thiz, jlong inst, jobject buffer, jint offset, jint length, jint flags){
		size_t bufsize=(size_t)env->GetDirectBufferCapacity(buffer);
		Buffer buf(static_cast<size_t>(length));
		buf.CopyFrom(((char*)env->GetDirectBufferAddress(buffer))+offset, 0, static_cast<size_t>(length));
		((video::VideoSourceAndroid*)(intptr_t)inst)->SendFrame(std::move(buf), static_cast<uint32_t>(flags));
	}

	void VideoSource_nativeSetRotation(JNIEnv* env, jobject thiz, jlong inst, jint rotation){
		((video::VideoSourceAndroid*)(intptr_t)inst)->SetRotation((unsigned int)rotation);
	}

#pragma mark - VideoRenderer

	jlong VideoRenderer_nativeInit(JNIEnv* env, jobject thiz){
		return (jlong)(intptr_t)new video::VideoRendererAndroid(env->NewGlobalRef(thiz));
	}

#pragma mark - VLog

	template<int level> void VLog_log(JNIEnv* env, jclass cls, jstring jmsg){
		const char* format="[java] %s";
		std::string msg=jni::JavaStringToStdString(env, jmsg);
		switch(level){
			case 0:
				LOGV(format, msg.c_str());
				break;
			case 1:
				LOGD(format, msg.c_str());
				break;
			case 2:
				LOGI(format, msg.c_str());
				break;
			case 3:
				LOGW(format, msg.c_str());
				break;
			case 4:
				LOGE(format, msg.c_str());
				break;
			default:
				break;
		}
	}
}

extern "C" void tgvoipRegisterNatives(JNIEnv* env){
	jclass controller=env->FindClass(TGVOIP_PACKAGE_PATH "/VoIPController");
	jclass groupController=env->FindClass(TGVOIP_PACKAGE_PATH "/VoIPGroupController");
	if(env->ExceptionCheck()){
		env->ExceptionClear(); // is returning NULL from FindClass not enough?
	}
	jclass audioRecordJNI=env->FindClass(TGVOIP_PACKAGE_PATH "/AudioRecordJNI");
	jclass audioTrackJNI=env->FindClass(TGVOIP_PACKAGE_PATH "/AudioTrackJNI");
	jclass serverConfig=env->FindClass(TGVOIP_PACKAGE_PATH "/VoIPServerConfig");
	jclass resampler=env->FindClass(TGVOIP_PACKAGE_PATH "/Resampler");
	jclass videoSource=env->FindClass(TGVOIP_PACKAGE_PATH "/VideoSource");
	if(env->ExceptionCheck()){
		env->ExceptionClear(); // is returning NULL from FindClass not enough?
	}
	jclass videoRenderer=env->FindClass(TGVOIP_PACKAGE_PATH "/VideoRenderer");
	if(env->ExceptionCheck()){
		env->ExceptionClear(); // is returning NULL from FindClass not enough?
	}
	jclass vlog=env->FindClass(TGVOIP_PACKAGE_PATH "/VLog");
	if(env->ExceptionCheck()){
		env->ExceptionClear();
	}
	assert(controller && audioRecordJNI && audioTrackJNI && serverConfig && resampler);

	audioRecordInstanceFld=env->GetFieldID(audioRecordJNI, "nativeInst", "J");
	audioTrackInstanceFld=env->GetFieldID(audioTrackJNI, "nativeInst", "J");

	env->GetJavaVM(&sharedJVM);
	if(!AudioInputAndroid::jniClass){
		jclass cls=env->FindClass(TGVOIP_PACKAGE_PATH "/AudioRecordJNI");
		AudioInputAndroid::jniClass=(jclass) env->NewGlobalRef(cls);
		AudioInputAndroid::initMethod=env->GetMethodID(cls, "init", "(IIII)V");
		AudioInputAndroid::releaseMethod=env->GetMethodID(cls, "release", "()V");
		AudioInputAndroid::startMethod=env->GetMethodID(cls, "start", "()Z");
		AudioInputAndroid::stopMethod=env->GetMethodID(cls, "stop", "()V");
		AudioInputAndroid::getEnabledEffectsMaskMethod=env->GetMethodID(cls, "getEnabledEffectsMask", "()I");

		cls=env->FindClass(TGVOIP_PACKAGE_PATH "/AudioTrackJNI");
		AudioOutputAndroid::jniClass=(jclass) env->NewGlobalRef(cls);
		AudioOutputAndroid::initMethod=env->GetMethodID(cls, "init", "(IIII)V");
		AudioOutputAndroid::releaseMethod=env->GetMethodID(cls, "release", "()V");
		AudioOutputAndroid::startMethod=env->GetMethodID(cls, "start", "()V");
		AudioOutputAndroid::stopMethod=env->GetMethodID(cls, "stop", "()V");

		if(videoRenderer){
			video::VideoRendererAndroid::decodeAndDisplayMethod=env->GetMethodID(videoRenderer, "decodeAndDisplay", "(Ljava/nio/ByteBuffer;IJ)V");
			video::VideoRendererAndroid::resetMethod=env->GetMethodID(videoRenderer, "reset", "(Ljava/lang/String;II[[B)V");
			video::VideoRendererAndroid::setStreamEnabledMethod=env->GetMethodID(videoRenderer, "setStreamEnabled", "(Z)V");
			video::VideoRendererAndroid::setRotationMethod=env->GetMethodID(videoRenderer, "setRotation", "(I)V");
		}
	}

	setStateMethod=env->GetMethodID(controller, "handleStateChange", "(I)V");
	setSignalBarsMethod=env->GetMethodID(controller, "handleSignalBarsChange", "(I)V");
	groupCallKeyReceivedMethod=env->GetMethodID(controller, "groupCallKeyReceived", "([B)V");
	groupCallKeySentMethod=env->GetMethodID(controller, "groupCallKeySent", "()V");
	callUpgradeRequestReceivedMethod=env->GetMethodID(controller, "callUpgradeRequestReceived", "()V");

	if(!jniUtilitiesClass)
		jniUtilitiesClass=(jclass) env->NewGlobalRef(env->FindClass(TGVOIP_PACKAGE_PATH "/JNIUtilities"));

	// VoIPController
	JNINativeMethod controllerMethods[]={
			{"nativeInit", "(Ljava/lang/String;)J", (void*)&tgvoip::VoIPController_nativeInit},
			{"nativeStart", "(J)V", (void*)&tgvoip::VoIPController_nativeStart},
			{"nativeConnect", "(J)V", (void*)&tgvoip::VoIPController_nativeConnect},
			{"nativeSetProxy", "(JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", (void*)&tgvoip::VoIPController_nativeSetProxy},
			{"nativeSetEncryptionKey", "(J[BZ)V", (void*)&tgvoip::VoIPController_nativeSetEncryptionKey},
			{"nativeSetRemoteEndpoints", "(J[L" TGVOIP_ENDPOINT_CLASS ";ZZI)V", (void*)&tgvoip::VoIPController_nativeSetRemoteEndpoints},
			{"nativeSetNativeBufferSize", "(I)V", (void*)&tgvoip::VoIPController_nativeSetNativeBufferSize},
			{"nativeRelease", "(J)V", (void*)&tgvoip::VoIPController_nativeRelease},
			{"nativeGetDebugString", "(J)Ljava/lang/String;", (void*)&tgvoip::VoIPController_nativeGetDebugString},
			{"nativeSetNetworkType", "(JI)V", (void*)&tgvoip::VoIPController_nativeSetNetworkType},
			{"nativeSetMicMute", "(JZ)V", (void*)&tgvoip::VoIPController_nativeSetMicMute},
			{"nativeSetConfig", "(JDDIZZZLjava/lang/String;Ljava/lang/String;Z)V", (void*)&tgvoip::VoIPController_nativeSetConfig},
			{"nativeDebugCtl", "(JII)V", (void*)&tgvoip::VoIPController_nativeDebugCtl},
			{"nativeGetVersion", "()Ljava/lang/String;", (void*)&tgvoip::VoIPController_nativeGetVersion},
			{"nativeGetPreferredRelayID", "(J)J", (void*)&tgvoip::VoIPController_nativeGetPreferredRelayID},
			{"nativeGetLastError", "(J)I", (void*)&tgvoip::VoIPController_nativeGetLastError},
			{"nativeGetStats", "(JL" TGVOIP_PACKAGE_PATH "/VoIPController$Stats;)V", (void*)&tgvoip::VoIPController_nativeGetStats},
			{"nativeGetDebugLog", "(J)Ljava/lang/String;", (void*)&tgvoip::VoIPController_nativeGetDebugLog},
			{"nativeSetAudioOutputGainControlEnabled", "(JZ)V", (void*)&tgvoip::VoIPController_nativeSetAudioOutputGainControlEnabled},
			{"nativeSetEchoCancellationStrength", "(JI)V", (void*)&tgvoip::VoIPController_nativeSetEchoCancellationStrength},
			{"nativeGetPeerCapabilities", "(J)I", (void*)&tgvoip::VoIPController_nativeGetPeerCapabilities},
			{"nativeSendGroupCallKey", "(J[B)V", (void*)&tgvoip::VoIPController_nativeSendGroupCallKey},
			{"nativeRequestCallUpgrade", "(J)V", (void*)&tgvoip::VoIPController_nativeRequestCallUpgrade},
			{"nativeNeedRate", "(J)Z", (void*)&tgvoip::VoIPController_nativeNeedRate},
			{"getConnectionMaxLayer", "()I", (void*)&tgvoip::VoIPController_getConnectionMaxLayer},
			{"nativeSetVideoSource", "(JJ)V", (void*)&tgvoip::VoIPController_nativeSetVideoSource},
			{"nativeSetVideoRenderer", "(JJ)V", (void*)&tgvoip::VoIPController_nativeSetVideoRenderer}
	};
	env->RegisterNatives(controller, controllerMethods, sizeof(controllerMethods)/sizeof(JNINativeMethod));

	// VoIPGroupController
#ifndef TGVOIP_NO_GROUP_CALLS
	if(groupController){
		setStateMethod=env->GetMethodID(groupController, "handleStateChange", "(I)V");
		setParticipantAudioEnabledMethod=env->GetMethodID(groupController, "setParticipantAudioEnabled", "(IZ)V");
		setSelfStreamsMethod=env->GetMethodID(groupController, "setSelfStreams", "([B)V");

		JNINativeMethod groupControllerMethods[]={
				{"nativeInit", "(I)J", (void*)&tgvoip::VoIPGroupController_nativeInit},
				{"nativeSetGroupCallInfo", "(J[B[B[B[B[BILjava/lang/String;Ljava/lang/String;I)V", (void*)&tgvoip::VoIPGroupController_nativeSetGroupCallInfo},
				{"nativeAddGroupCallParticipant", "(JI[B[B)V", (void*)&tgvoip::VoIPGroupController_nativeAddGroupCallParticipant},
				{"nativeRemoveGroupCallParticipant", "(JI)V", (void*)&tgvoip::VoIPGroupController_nativeRemoveGroupCallParticipant},
				{"nativeGetParticipantAudioLevel", "(JI)F", (void*)&tgvoip::VoIPGroupController_nativeGetParticipantAudioLevel},
				{"nativeSetParticipantVolume", "(JIF)V", (void*)&tgvoip::VoIPGroupController_nativeSetParticipantVolume},
				{"getInitialStreams", "()[B", (void*)&tgvoip::VoIPGroupController_getInitialStreams},
				{"nativeSetParticipantStreams", "(JI[B)V", (void*)&tgvoip::VoIPGroupController_nativeSetParticipantStreams}
		};
		env->RegisterNatives(groupController, groupControllerMethods, sizeof(groupControllerMethods)/sizeof(JNINativeMethod));
	}
#endif

	// AudioRecordJNI
	JNINativeMethod audioRecordMethods[]={
			{"nativeCallback", "(Ljava/nio/ByteBuffer;)V", (void*)&tgvoip::AudioRecordJNI_nativeCallback}
	};
	env->RegisterNatives(audioRecordJNI, audioRecordMethods, sizeof(audioRecordMethods)/sizeof(JNINativeMethod));

	// AudioTrackJNI
	JNINativeMethod audioTrackMethods[]={
			{"nativeCallback", "([B)V", (void*)&tgvoip::AudioTrackJNI_nativeCallback}
	};
	env->RegisterNatives(audioTrackJNI, audioTrackMethods, sizeof(audioTrackMethods)/sizeof(JNINativeMethod));

	// VoIPServerConfig
	JNINativeMethod serverConfigMethods[]={
			{"nativeSetConfig", "(Ljava/lang/String;)V", (void*)&tgvoip::VoIPServerConfig_nativeSetConfig}
	};
	env->RegisterNatives(serverConfig, serverConfigMethods, sizeof(serverConfigMethods)/sizeof(JNINativeMethod));

	// Resampler
	JNINativeMethod resamplerMethods[]={
			{"convert44to48", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I", (void*)&tgvoip::Resampler_convert44to48},
			{"convert48to44", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I", (void*)&tgvoip::Resampler_convert48to44}
	};
	env->RegisterNatives(resampler, resamplerMethods, sizeof(resamplerMethods)/sizeof(JNINativeMethod));

	if(videoSource){
		// VideoSource
		JNINativeMethod videoSourceMethods[]={
				{"nativeInit", "()J", (void *) &tgvoip::VideoSource_nativeInit},
				{"nativeRelease", "(J)V", (void *) &tgvoip::VideoSource_nativeRelease},
				{"nativeSetVideoStreamParameters", "(J[Ljava/nio/ByteBuffer;II)V", (void *) &tgvoip::VideoSource_nativeSetVideoStreamParameters},
				{"nativeSendFrame", "(JLjava/nio/ByteBuffer;III)V", (void *) &tgvoip::VideoSource_nativeSendFrame},
				{"nativeSetRotation", "(JI)V", (void*)&tgvoip::VideoSource_nativeSetRotation}
		};
		env->RegisterNatives(videoSource, videoSourceMethods, sizeof(videoSourceMethods)/sizeof(JNINativeMethod));
	}

	if(videoRenderer){
		// VideoRenderer
		JNINativeMethod videoRendererMethods[]={
				{"nativeInit", "()J", (void *) &tgvoip::VideoRenderer_nativeInit}
		};
		env->RegisterNatives(videoRenderer, videoRendererMethods, sizeof(videoRendererMethods)/sizeof(JNINativeMethod));
	}

	if(vlog){
		// VLog
		JNINativeMethod vlogMethods[]={
				{"v", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<0>},
				{"d", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<1>},
				{"i", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<2>},
				{"w", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<3>},
				{"e", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<4>}
		};
		env->RegisterNatives(vlog, vlogMethods, sizeof(vlogMethods)/sizeof(JNINativeMethod));
	}
}
