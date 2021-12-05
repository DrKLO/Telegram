//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOINPUTANDROID_H
#define LIBTGVOIP_AUDIOINPUTANDROID_H

#include <jni.h>
#include "../../audio/AudioInput.h"
#include "../../threading.h"

namespace tgvoip{ namespace audio{
class AudioInputAndroid : public AudioInput{

public:
	AudioInputAndroid();
	virtual ~AudioInputAndroid();
	virtual void Start();
	virtual void Stop();
	void HandleCallback(JNIEnv* env, jobject buffer);
	unsigned int GetEnabledEffects();
	static jmethodID initMethod;
	static jmethodID releaseMethod;
	static jmethodID startMethod;
	static jmethodID stopMethod;
	static jmethodID getEnabledEffectsMaskMethod;
	static jclass jniClass;

	static constexpr unsigned int EFFECT_AEC=1;
	static constexpr unsigned int EFFECT_NS=2;

private:
	jobject javaObject;
	bool running;
	Mutex mutex;
	unsigned int enabledEffects=0;

};
}}

#endif //LIBTGVOIP_AUDIOINPUTANDROID_H
