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
#include "libtgvoip/VoIPServerConfig.h"
#include "libtgvoip/VoIPController.h"
#include "libtgvoip/os/android/AudioOutputOpenSLES.h"
#include "libtgvoip/os/android/AudioInputOpenSLES.h"
#include "libtgvoip/os/android/AudioInputAndroid.h"
#include "libtgvoip/os/android/AudioOutputAndroid.h"
#include "libtgvoip/audio/Resampler.h"
#include "libtgvoip/os/android/JNIUtilities.h"
#include "libtgvoip/PrivateDefines.h"
#include "libtgvoip/logging.h"
#include "../c_utils.h"

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
jclass jniUtilitiesClass=NULL;

struct ImplDataAndroid{
	jobject javaObject;
	std::string persistentStateFile="";
};

#ifndef TGVOIP_PACKAGE_PATH
#define TGVOIP_PACKAGE_PATH "org/telegram/messenger/voip"
#endif

using namespace tgvoip;
using namespace tgvoip::audio;

namespace tgvoip {
#pragma mark - Callbacks

	void updateConnectionState(VoIPController *cntrlr, int state) {
		ImplDataAndroid *impl = (ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(setStateMethod, impl->javaObject, state);
	}

	void updateSignalBarCount(VoIPController *cntrlr, int count) {
		ImplDataAndroid *impl = (ImplDataAndroid *) cntrlr->implData;
		jni::AttachAndCallVoidMethod(setSignalBarsMethod, impl->javaObject, count);
	}

#pragma mark - VoIPController

	jlong VoIPController_nativeInit(JNIEnv *env, jobject thiz, jstring persistentStateFile) {
		ImplDataAndroid *impl = new ImplDataAndroid();
		impl->javaObject = env->NewGlobalRef(thiz);
		if (persistentStateFile) {
			impl->persistentStateFile = jni::JavaStringToStdString(env, persistentStateFile);
		}
		VoIPController *cntrlr = new VoIPController();
		cntrlr->implData = impl;
		VoIPController::Callbacks callbacks;
		callbacks.connectionStateChanged = updateConnectionState;
		callbacks.signalBarCountChanged = updateSignalBarCount;
		cntrlr->SetCallbacks(callbacks);
		if (!impl->persistentStateFile.empty()) {
			FILE *f = fopen(impl->persistentStateFile.c_str(), "r");
			if (f) {
				fseek(f, 0, SEEK_END);
				size_t len = static_cast<size_t>(ftell(f));
				fseek(f, 0, SEEK_SET);
				if (len < 1024 * 512 && len > 0) {
					char *fbuf = static_cast<char *>(malloc(len));
					fread(fbuf, 1, len, f);
					std::vector<uint8_t> state(fbuf, fbuf + len);
					free(fbuf);
					cntrlr->SetPersistentState(state);
				}
				fclose(f);
			}
		}
		return (jlong) (intptr_t) cntrlr;
	}

	void VoIPController_nativeStart(JNIEnv *env, jobject thiz, jlong inst) {
		((VoIPController *) (intptr_t) inst)->Start();
	}

	void VoIPController_nativeConnect(JNIEnv *env, jobject thiz, jlong inst) {
		((VoIPController *) (intptr_t) inst)->Connect();
	}

	void VoIPController_nativeSetProxy(JNIEnv *env, jobject thiz, jlong inst, jstring _address, jint port, jstring _username, jstring _password) {
		((VoIPController *) (intptr_t) inst)->SetProxy(PROXY_SOCKS5, jni::JavaStringToStdString(env, _address), (uint16_t) port, jni::JavaStringToStdString(env, _username), jni::JavaStringToStdString(env, _password));
	}

	void VoIPController_nativeSetEncryptionKey(JNIEnv *env, jobject thiz, jlong inst, jbyteArray key, jboolean isOutgoing) {
		jbyte *akey = env->GetByteArrayElements(key, NULL);
		((VoIPController *) (intptr_t) inst)->SetEncryptionKey((char *) akey, isOutgoing);
		env->ReleaseByteArrayElements(key, akey, JNI_ABORT);
	}

	void VoIPController_nativeSetNativeBufferSize(JNIEnv *env, jclass thiz, jint size) {
		AudioOutputOpenSLES::nativeBufferSize = (unsigned int) size;
		AudioInputOpenSLES::nativeBufferSize = (unsigned int) size;
	}

	void VoIPController_nativeRelease(JNIEnv *env, jobject thiz, jlong inst) {
		//env->DeleteGlobalRef(AudioInputAndroid::jniClass);

		VoIPController *ctlr = ((VoIPController *) (intptr_t) inst);
		ImplDataAndroid *impl = (ImplDataAndroid *) ctlr->implData;
		ctlr->Stop();
		std::vector<uint8_t> state = ctlr->GetPersistentState();
		delete ctlr;
		env->DeleteGlobalRef(impl->javaObject);
		if (!impl->persistentStateFile.empty()) {
			FILE *f = fopen(impl->persistentStateFile.c_str(), "w");
			if (f) {
				fwrite(state.data(), 1, state.size(), f);
				fclose(f);
			}
		}
		delete impl;
	}

	jstring VoIPController_nativeGetDebugString(JNIEnv *env, jobject thiz, jlong inst) {
		std::string str = ((VoIPController *) (intptr_t) inst)->GetDebugString();
		return env->NewStringUTF(str.c_str());
	}

	void VoIPController_nativeSetNetworkType(JNIEnv *env, jobject thiz, jlong inst, jint type) {
		((VoIPController *) (intptr_t) inst)->SetNetworkType(type);
	}

	void VoIPController_nativeSetMicMute(JNIEnv *env, jobject thiz, jlong inst, jboolean mute) {
		((VoIPController *) (intptr_t) inst)->SetMicMute(mute);
	}

	void VoIPController_nativeSetConfig(JNIEnv *env, jobject thiz, jlong inst, jdouble recvTimeout, jdouble initTimeout, jint dataSavingMode, jboolean enableAEC, jboolean enableNS, jboolean enableAGC, jstring logFilePath, jstring statsDumpPath, jboolean logPacketStats) {
		VoIPController::Config cfg;
		cfg.initTimeout = initTimeout;
		cfg.recvTimeout = recvTimeout;
		cfg.dataSaving = dataSavingMode;
		cfg.enableAEC = enableAEC;
		cfg.enableNS = enableNS;
		cfg.enableAGC = enableAGC;
		cfg.enableCallUpgrade = false;
		cfg.logPacketStats = logPacketStats;
		if (logFilePath) {
			cfg.logFilePath = jni::JavaStringToStdString(env, logFilePath);
		}
		if (statsDumpPath) {
			cfg.statsDumpFilePath = jni::JavaStringToStdString(env, statsDumpPath);
		}

		((VoIPController *) (intptr_t) inst)->SetConfig(cfg);
	}

	void VoIPController_nativeDebugCtl(JNIEnv *env, jobject thiz, jlong inst, jint request, jint param) {
		((VoIPController *) (intptr_t) inst)->DebugCtl(request, param);
	}

	jstring VoIPController_nativeGetVersion(JNIEnv *env, jclass clasz) {
		return env->NewStringUTF(VoIPController::GetVersion());
	}

	jlong VoIPController_nativeGetPreferredRelayID(JNIEnv *env, jclass clasz, jlong inst) {
		return ((VoIPController *) (intptr_t) inst)->GetPreferredRelayID();
	}

	jint VoIPController_nativeGetLastError(JNIEnv *env, jclass clasz, jlong inst) {
		return ((VoIPController *) (intptr_t) inst)->GetLastError();
	}

	void VoIPController_nativeGetStats(JNIEnv *env, jclass clasz, jlong inst, jobject stats) {
		VoIPController::TrafficStats _stats;
		((VoIPController *) (intptr_t) inst)->GetStats(&_stats);
		jclass cls = env->GetObjectClass(stats);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesSentWifi", "J"), _stats.bytesSentWifi);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesSentMobile", "J"), _stats.bytesSentMobile);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesRecvdWifi", "J"), _stats.bytesRecvdWifi);
		env->SetLongField(stats, env->GetFieldID(cls, "bytesRecvdMobile", "J"), _stats.bytesRecvdMobile);
	}

	jstring VoIPController_nativeGetDebugLog(JNIEnv *env, jobject thiz, jlong inst) {
		VoIPController *ctlr = ((VoIPController *) (intptr_t) inst);
		std::string log = ctlr->GetDebugLog();
		return env->NewStringUTF(log.c_str());
	}

	void VoIPController_nativeSetAudioOutputGainControlEnabled(JNIEnv *env, jclass clasz, jlong inst, jboolean enabled) {
		((VoIPController *) (intptr_t) inst)->SetAudioOutputGainControlEnabled(enabled);
	}

	void VoIPController_nativeSetEchoCancellationStrength(JNIEnv *env, jclass cls, jlong inst, jint strength) {
		((VoIPController *) (intptr_t) inst)->SetEchoCancellationStrength(strength);
	}

	jint VoIPController_nativeGetPeerCapabilities(JNIEnv *env, jclass cls, jlong inst) {
		return ((VoIPController *) (intptr_t) inst)->GetPeerCapabilities();
	}

	jboolean VoIPController_nativeNeedRate(JNIEnv *env, jclass cls, jlong inst) {
		return static_cast<jboolean>(((VoIPController *) (intptr_t) inst)->NeedRate());
	}

	jint VoIPController_getConnectionMaxLayer(JNIEnv *env, jclass cls) {
		return VoIPController::GetConnectionMaxLayer();
	}

	void AudioRecordJNI_nativeCallback(JNIEnv *env, jobject thiz, jobject buffer) {
		jlong inst = env->GetLongField(thiz, audioRecordInstanceFld);
		AudioInputAndroid *in = (AudioInputAndroid *) (intptr_t) inst;
		in->HandleCallback(env, buffer);
	}

	void AudioTrackJNI_nativeCallback(JNIEnv *env, jobject thiz, jbyteArray buffer) {
		jlong inst = env->GetLongField(thiz, audioTrackInstanceFld);
		AudioOutputAndroid *in = (AudioOutputAndroid *) (intptr_t) inst;
		in->HandleCallback(env, buffer);
	}

	void VoIPServerConfig_nativeSetConfig(JNIEnv *env, jclass clasz, jstring jsonString) {
		ServerConfig::GetSharedInstance()->Update(jni::JavaStringToStdString(env, jsonString));
	}

	jint Resampler_convert44to48(JNIEnv *env, jclass cls, jobject from, jobject to) {
		return (jint) tgvoip::audio::Resampler::Convert44To48((int16_t *) env->GetDirectBufferAddress(from), (int16_t *) env->GetDirectBufferAddress(to), (size_t) (env->GetDirectBufferCapacity(from) / 2), (size_t) (env->GetDirectBufferCapacity(to) / 2));
	}

	jint Resampler_convert48to44(JNIEnv *env, jclass cls, jobject from, jobject to) {
		return (jint) tgvoip::audio::Resampler::Convert48To44((int16_t *) env->GetDirectBufferAddress(from), (int16_t *) env->GetDirectBufferAddress(to), (size_t) (env->GetDirectBufferCapacity(from) / 2), (size_t) (env->GetDirectBufferCapacity(to) / 2));
	}

	template<int level>
	void VLog_log(JNIEnv *env, jclass cls, jstring jmsg) {
		const char *format = "[java] %s";
		std::string msg = jni::JavaStringToStdString(env, jmsg);
		switch (level) {
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

extern "C" {
int tgvoipOnJNILoad(JavaVM *vm, JNIEnv *env) {
    jclass controller = env->FindClass(TGVOIP_PACKAGE_PATH "/VoIPController");
    if (env->ExceptionCheck()) {
        env->ExceptionClear(); // is returning NULL from FindClass not enough?
    }
    jclass audioRecordJNI = env->FindClass(TGVOIP_PACKAGE_PATH "/AudioRecordJNI");
    jclass audioTrackJNI = env->FindClass(TGVOIP_PACKAGE_PATH "/AudioTrackJNI");
    jclass serverConfig = env->FindClass(TGVOIP_PACKAGE_PATH "/VoIPServerConfig");
    jclass resampler = env->FindClass(TGVOIP_PACKAGE_PATH "/Resampler");
    if (env->ExceptionCheck()) {
        env->ExceptionClear(); // is returning NULL from FindClass not enough?
    }
    jclass vlog = env->FindClass(TGVOIP_PACKAGE_PATH "/VLog");
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    assert(controller && audioRecordJNI && audioTrackJNI && serverConfig && resampler);

    audioRecordInstanceFld = env->GetFieldID(audioRecordJNI, "nativeInst", "J");
    audioTrackInstanceFld = env->GetFieldID(audioTrackJNI, "nativeInst", "J");

    env->GetJavaVM(&sharedJVM);
    if (!AudioInputAndroid::jniClass) {
        jclass cls = env->FindClass(TGVOIP_PACKAGE_PATH "/AudioRecordJNI");
        AudioInputAndroid::jniClass = (jclass) env->NewGlobalRef(cls);
        AudioInputAndroid::initMethod = env->GetMethodID(cls, "init", "(IIII)V");
        AudioInputAndroid::releaseMethod = env->GetMethodID(cls, "release", "()V");
        AudioInputAndroid::startMethod = env->GetMethodID(cls, "start", "()Z");
        AudioInputAndroid::stopMethod = env->GetMethodID(cls, "stop", "()V");
        AudioInputAndroid::getEnabledEffectsMaskMethod = env->GetMethodID(cls, "getEnabledEffectsMask", "()I");

        cls = env->FindClass(TGVOIP_PACKAGE_PATH "/AudioTrackJNI");
        AudioOutputAndroid::jniClass = (jclass) env->NewGlobalRef(cls);
        AudioOutputAndroid::initMethod = env->GetMethodID(cls, "init", "(IIII)V");
        AudioOutputAndroid::releaseMethod = env->GetMethodID(cls, "release", "()V");
        AudioOutputAndroid::startMethod = env->GetMethodID(cls, "start", "()V");
        AudioOutputAndroid::stopMethod = env->GetMethodID(cls, "stop", "()V");
    }

    setStateMethod = env->GetMethodID(controller, "handleStateChange", "(I)V");
    setSignalBarsMethod = env->GetMethodID(controller, "handleSignalBarsChange", "(I)V");

    if (!jniUtilitiesClass) {
        jniUtilitiesClass = (jclass) env->NewGlobalRef(env->FindClass(TGVOIP_PACKAGE_PATH "/JNIUtilities"));
    }

    // VoIPController
    JNINativeMethod controllerMethods[] = {
            {"nativeInit",                             "(Ljava/lang/String;)J",                                       (void *) &tgvoip::VoIPController_nativeInit},
            {"nativeStart",                            "(J)V",                                                        (void *) &tgvoip::VoIPController_nativeStart},
            {"nativeConnect",                          "(J)V",                                                        (void *) &tgvoip::VoIPController_nativeConnect},
            {"nativeSetProxy",                         "(JLjava/lang/String;ILjava/lang/String;Ljava/lang/String;)V", (void *) &tgvoip::VoIPController_nativeSetProxy},
            {"nativeSetEncryptionKey",                 "(J[BZ)V",                                                     (void *) &tgvoip::VoIPController_nativeSetEncryptionKey},
            {"nativeSetNativeBufferSize",              "(I)V",                                                        (void *) &tgvoip::VoIPController_nativeSetNativeBufferSize},
            {"nativeRelease",                          "(J)V",                                                        (void *) &tgvoip::VoIPController_nativeRelease},
            {"nativeGetDebugString",                   "(J)Ljava/lang/String;",                                       (void *) &tgvoip::VoIPController_nativeGetDebugString},
            {"nativeSetNetworkType",                   "(JI)V",                                                       (void *) &tgvoip::VoIPController_nativeSetNetworkType},
            {"nativeSetMicMute",                       "(JZ)V",                                                       (void *) &tgvoip::VoIPController_nativeSetMicMute},
            {"nativeSetConfig",                        "(JDDIZZZLjava/lang/String;Ljava/lang/String;Z)V",             (void *) &tgvoip::VoIPController_nativeSetConfig},
            {"nativeDebugCtl",                         "(JII)V",                                                      (void *) &tgvoip::VoIPController_nativeDebugCtl},
            {"nativeGetVersion",                       "()Ljava/lang/String;",                                        (void *) &tgvoip::VoIPController_nativeGetVersion},
            {"nativeGetPreferredRelayID",              "(J)J",                                                        (void *) &tgvoip::VoIPController_nativeGetPreferredRelayID},
            {"nativeGetLastError",                     "(J)I",                                                        (void *) &tgvoip::VoIPController_nativeGetLastError},
            {"nativeGetStats",                         "(JL" TGVOIP_PACKAGE_PATH "/VoIPController$Stats;)V",          (void *) &tgvoip::VoIPController_nativeGetStats},
            {"nativeGetDebugLog",                      "(J)Ljava/lang/String;",                                       (void *) &tgvoip::VoIPController_nativeGetDebugLog},
            {"nativeSetAudioOutputGainControlEnabled", "(JZ)V",                                                       (void *) &tgvoip::VoIPController_nativeSetAudioOutputGainControlEnabled},
            {"nativeSetEchoCancellationStrength",      "(JI)V",                                                       (void *) &tgvoip::VoIPController_nativeSetEchoCancellationStrength},
            {"nativeGetPeerCapabilities",              "(J)I",                                                        (void *) &tgvoip::VoIPController_nativeGetPeerCapabilities},
            {"nativeNeedRate",                         "(J)Z",                                                        (void *) &tgvoip::VoIPController_nativeNeedRate},
            {"getConnectionMaxLayer",                  "()I",                                                         (void *) &tgvoip::VoIPController_getConnectionMaxLayer}
    };
    env->RegisterNatives(controller, controllerMethods, sizeof(controllerMethods) / sizeof(JNINativeMethod));

    // AudioRecordJNI
    JNINativeMethod audioRecordMethods[] = {
            {"nativeCallback", "(Ljava/nio/ByteBuffer;)V", (void *) &tgvoip::AudioRecordJNI_nativeCallback}
    };
    env->RegisterNatives(audioRecordJNI, audioRecordMethods, sizeof(audioRecordMethods) / sizeof(JNINativeMethod));

    // AudioTrackJNI
    JNINativeMethod audioTrackMethods[] = {
            {"nativeCallback", "([B)V", (void *) &tgvoip::AudioTrackJNI_nativeCallback}
    };
    env->RegisterNatives(audioTrackJNI, audioTrackMethods, sizeof(audioTrackMethods) / sizeof(JNINativeMethod));

    // VoIPServerConfig
    JNINativeMethod serverConfigMethods[] = {
            {"nativeSetConfig", "(Ljava/lang/String;)V", (void *) &tgvoip::VoIPServerConfig_nativeSetConfig}
    };
    env->RegisterNatives(serverConfig, serverConfigMethods, sizeof(serverConfigMethods) / sizeof(JNINativeMethod));

    // Resampler
    JNINativeMethod resamplerMethods[] = {
            {"convert44to48", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I", (void *) &tgvoip::Resampler_convert44to48},
            {"convert48to44", "(Ljava/nio/ByteBuffer;Ljava/nio/ByteBuffer;)I", (void *) &tgvoip::Resampler_convert48to44}
    };
    env->RegisterNatives(resampler, resamplerMethods, sizeof(resamplerMethods) / sizeof(JNINativeMethod));

    if (vlog) {
        // VLog
        JNINativeMethod vlogMethods[] = {
                {"v", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<0>},
                {"d", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<1>},
                {"i", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<2>},
                {"w", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<3>},
                {"e", "(Ljava/lang/String;)V", (void *) &tgvoip::VLog_log<4>}
        };
        env->RegisterNatives(vlog, vlogMethods, sizeof(vlogMethods) / sizeof(JNINativeMethod));
    }

	return JNI_TRUE;
}
}