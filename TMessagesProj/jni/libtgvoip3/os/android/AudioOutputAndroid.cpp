//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include "AudioOutputAndroid.h"
#include "../../logging.h"
#include <cstdio>

extern JavaVM* sharedJVM;

using namespace tgvoip;
using namespace tgvoip::audio;

jmethodID AudioOutputAndroid::initMethod = nullptr;
jmethodID AudioOutputAndroid::releaseMethod = nullptr;
jmethodID AudioOutputAndroid::startMethod = nullptr;
jmethodID AudioOutputAndroid::stopMethod = nullptr;
jclass AudioOutputAndroid::jniClass = nullptr;

AudioOutputAndroid::AudioOutputAndroid()
{
    JNIEnv* env = nullptr;
    bool didAttach = false;
    sharedJVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (!env)
    {
        sharedJVM->AttachCurrentThread(&env, nullptr);
        didAttach = true;
    }

    jmethodID ctor = env->GetMethodID(jniClass, "<init>", "(J)V");
    jobject obj = env->NewObject(jniClass, ctor, (jlong)(intptr_t)this);
    javaObject = env->NewGlobalRef(obj);

    env->CallVoidMethod(javaObject, initMethod, 48000, 16, 1, 960 * 2);

    if (didAttach)
    {
        sharedJVM->DetachCurrentThread();
    }
    running = false;
}

AudioOutputAndroid::~AudioOutputAndroid()
{
    JNIEnv* env = nullptr;
    bool didAttach = false;
    sharedJVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (!env)
    {
        sharedJVM->AttachCurrentThread(&env, nullptr);
        didAttach = true;
    }

    env->CallVoidMethod(javaObject, releaseMethod);
    env->DeleteGlobalRef(javaObject);
    javaObject = nullptr;

    if (didAttach)
    {
        sharedJVM->DetachCurrentThread();
    }
}

void AudioOutputAndroid::Start()
{
    JNIEnv* env = nullptr;
    bool didAttach = false;
    sharedJVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (!env)
    {
        sharedJVM->AttachCurrentThread(&env, nullptr);
        didAttach = true;
    }

    env->CallVoidMethod(javaObject, startMethod);

    if (didAttach)
    {
        sharedJVM->DetachCurrentThread();
    }
    running = true;
}

void AudioOutputAndroid::Stop()
{
    running = false;
    JNIEnv* env = nullptr;
    bool didAttach = false;
    sharedJVM->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (!env)
    {
        sharedJVM->AttachCurrentThread(&env, nullptr);
        didAttach = true;
    }

    env->CallVoidMethod(javaObject, stopMethod);

    if (didAttach)
    {
        sharedJVM->DetachCurrentThread();
    }
}

void AudioOutputAndroid::HandleCallback(JNIEnv* env, jbyteArray buffer)
{
    if (!running)
        return;
    std::uint8_t* buf = reinterpret_cast<std::uint8_t*>(env->GetByteArrayElements(buffer, nullptr));
    std::size_t len = (std::size_t)env->GetArrayLength(buffer);
    InvokeCallback(buf, len);
    env->ReleaseByteArrayElements(buffer, (jbyte*)buf, 0);
}

bool AudioOutputAndroid::IsPlaying()
{
    return running;
}
