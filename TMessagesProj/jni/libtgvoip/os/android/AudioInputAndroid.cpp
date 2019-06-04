//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioInputAndroid.h"
#include <stdio.h>
#include "../../logging.h"
#include "JNIUtilities.h"

extern JavaVM* sharedJVM;

using namespace tgvoip;
using namespace tgvoip::audio;

jmethodID AudioInputAndroid::initMethod=NULL;
jmethodID AudioInputAndroid::releaseMethod=NULL;
jmethodID AudioInputAndroid::startMethod=NULL;
jmethodID AudioInputAndroid::stopMethod=NULL;
jmethodID AudioInputAndroid::getEnabledEffectsMaskMethod=NULL;
jclass AudioInputAndroid::jniClass=NULL;

AudioInputAndroid::AudioInputAndroid(){
	jni::DoWithJNI([this](JNIEnv* env){
		jmethodID ctor=env->GetMethodID(jniClass, "<init>", "(J)V");
		jobject obj=env->NewObject(jniClass, ctor, (jlong)(intptr_t)this);
		javaObject=env->NewGlobalRef(obj);

		env->CallVoidMethod(javaObject, initMethod, 48000, 16, 1, 960*2);
		enabledEffects=(unsigned int)env->CallIntMethod(javaObject, getEnabledEffectsMaskMethod);
	});
	running=false;
}

AudioInputAndroid::~AudioInputAndroid(){
	{
		MutexGuard guard(mutex);
		jni::DoWithJNI([this](JNIEnv* env){
			env->CallVoidMethod(javaObject, releaseMethod);
			env->DeleteGlobalRef(javaObject);
			javaObject=NULL;
		});
	}
}

void AudioInputAndroid::Start(){
	MutexGuard guard(mutex);
	jni::DoWithJNI([this](JNIEnv* env){
		failed=!env->CallBooleanMethod(javaObject, startMethod);
	});
	running=true;
}

void AudioInputAndroid::Stop(){
	MutexGuard guard(mutex);
	running=false;
	jni::DoWithJNI([this](JNIEnv* env){
		env->CallVoidMethod(javaObject, stopMethod);
	});
}

void AudioInputAndroid::HandleCallback(JNIEnv* env, jobject buffer){
	if(!running)
		return;
	unsigned char* buf=(unsigned char*) env->GetDirectBufferAddress(buffer);
	size_t len=(size_t) env->GetDirectBufferCapacity(buffer);
	InvokeCallback(buf, len);
}

unsigned int AudioInputAndroid::GetEnabledEffects(){
	return enabledEffects;
}
