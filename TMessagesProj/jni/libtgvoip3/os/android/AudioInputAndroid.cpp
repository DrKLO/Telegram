//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioInputAndroid.h"
#include "../../logging.h"
#include "JNIUtilities.h"
#include <cstdio>

extern JavaVM* sharedJVM;

using namespace tgvoip;
using namespace tgvoip::audio;

jmethodID AudioInputAndroid::initMethod = nullptr;
jmethodID AudioInputAndroid::releaseMethod = nullptr;
jmethodID AudioInputAndroid::startMethod = nullptr;
jmethodID AudioInputAndroid::stopMethod = nullptr;
jmethodID AudioInputAndroid::getEnabledEffectsMaskMethod = nullptr;
jclass AudioInputAndroid::jniClass = nullptr;

AudioInputAndroid::AudioInputAndroid()
{
    jni::DoWithJNI([this](JNIEnv* env) {
        jmethodID ctor = env->GetMethodID(jniClass, "<init>", "(J)V");
        jobject obj = env->NewObject(jniClass, ctor, (jlong)(intptr_t)this);
        javaObject = env->NewGlobalRef(obj);

        env->CallVoidMethod(javaObject, initMethod, 48000, 16, 1, 960 * 2);
        enabledEffects = (unsigned int)env->CallIntMethod(javaObject, getEnabledEffectsMaskMethod);
    });
    running = false;
}

AudioInputAndroid::~AudioInputAndroid()
{
    {
        MutexGuard guard(mutex);
        jni::DoWithJNI([this](JNIEnv* env) {
            env->CallVoidMethod(javaObject, releaseMethod);
            env->DeleteGlobalRef(javaObject);
            javaObject = nullptr;
        });
    }
}

void AudioInputAndroid::Start()
{
    MutexGuard guard(mutex);
    jni::DoWithJNI([this](JNIEnv* env) {
        m_failed = !env->CallBooleanMethod(javaObject, startMethod);
    });
    running = true;
}

void AudioInputAndroid::Stop()
{
    MutexGuard guard(mutex);
    running = false;
    jni::DoWithJNI([this](JNIEnv* env) {
        env->CallVoidMethod(javaObject, stopMethod);
    });
}

void AudioInputAndroid::HandleCallback(JNIEnv* env, jobject buffer)
{
    if (!running)
        return;
    std::uint8_t* buf = reinterpret_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    std::size_t len = (std::size_t)env->GetDirectBufferCapacity(buffer);
    InvokeCallback(buf, len);
}

unsigned int AudioInputAndroid::GetEnabledEffects()
{
    return enabledEffects;
}
