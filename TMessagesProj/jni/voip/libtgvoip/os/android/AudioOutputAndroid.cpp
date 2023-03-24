//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioOutputAndroid.h"
#include <stdio.h>
#include "../../logging.h"
#include "tgnet/FileLog.h"

extern JavaVM* sharedJVM;

using namespace tgvoip;
using namespace tgvoip::audio;

jmethodID AudioOutputAndroid::initMethod=NULL;
jmethodID AudioOutputAndroid::releaseMethod=NULL;
jmethodID AudioOutputAndroid::startMethod=NULL;
jmethodID AudioOutputAndroid::stopMethod=NULL;
jclass AudioOutputAndroid::jniClass=NULL;

AudioOutputAndroid::AudioOutputAndroid(){
	JNIEnv* env=NULL;
	bool didAttach=false;
	sharedJVM->GetEnv((void**) &env, JNI_VERSION_1_6);
	if(!env){
		sharedJVM->AttachCurrentThread(&env, NULL);
		didAttach=true;
	}

	jmethodID ctor=env->GetMethodID(jniClass, "<init>", "(J)V");
	jobject obj=env->NewObject(jniClass, ctor, (jlong)(intptr_t)this);
	DEBUG_REF("AudioOutputAndroid");
	javaObject=env->NewGlobalRef(obj);

	env->CallVoidMethod(javaObject, initMethod, 48000, 16, 1, 960*2);

	if(didAttach){
		sharedJVM->DetachCurrentThread();
	}
	running=false;
}

AudioOutputAndroid::~AudioOutputAndroid(){
	JNIEnv* env=NULL;
	bool didAttach=false;
	sharedJVM->GetEnv((void**) &env, JNI_VERSION_1_6);
	if(!env){
		sharedJVM->AttachCurrentThread(&env, NULL);
		didAttach=true;
	}

	env->CallVoidMethod(javaObject, releaseMethod);
	DEBUG_DELREF("AudioOutputAndroid");
	env->DeleteGlobalRef(javaObject);
	javaObject=NULL;

	if(didAttach){
		sharedJVM->DetachCurrentThread();
	}
}

void AudioOutputAndroid::Start(){
	JNIEnv* env=NULL;
	bool didAttach=false;
	sharedJVM->GetEnv((void**) &env, JNI_VERSION_1_6);
	if(!env){
		sharedJVM->AttachCurrentThread(&env, NULL);
		didAttach=true;
	}

	env->CallVoidMethod(javaObject, startMethod);

	if(didAttach){
		sharedJVM->DetachCurrentThread();
	}
	running=true;
}

void AudioOutputAndroid::Stop(){
	running=false;
	JNIEnv* env=NULL;
	bool didAttach=false;
	sharedJVM->GetEnv((void**) &env, JNI_VERSION_1_6);
	if(!env){
		sharedJVM->AttachCurrentThread(&env, NULL);
		didAttach=true;
	}

	env->CallVoidMethod(javaObject, stopMethod);

	if(didAttach){
		sharedJVM->DetachCurrentThread();
	}
}

void AudioOutputAndroid::HandleCallback(JNIEnv* env, jbyteArray buffer){
	if(!running)
		return;
	unsigned char* buf=(unsigned char*) env->GetByteArrayElements(buffer, NULL);
	size_t len=(size_t) env->GetArrayLength(buffer);
	InvokeCallback(buf, len);
	env->ReleaseByteArrayElements(buffer, (jbyte *) buf, 0);
}


bool AudioOutputAndroid::IsPlaying(){
	return running;
}
