//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#ifndef LIBTGVOIP_AUDIOOUTPUTANDROID_H
#define LIBTGVOIP_AUDIOOUTPUTANDROID_H

#include <jni.h>
#include "../../audio/AudioOutput.h"

namespace tgvoip{ namespace audio{
class AudioOutputAndroid : public AudioOutput{

public:

	AudioOutputAndroid();
	virtual ~AudioOutputAndroid();
	virtual void Start();
	virtual void Stop();
	virtual bool IsPlaying() override;
	void HandleCallback(JNIEnv* env, jbyteArray buffer);
	static jmethodID initMethod;
	static jmethodID releaseMethod;
	static jmethodID startMethod;
	static jmethodID stopMethod;
	static jclass jniClass;

private:
	jobject javaObject;
	bool running;

};
}}

#endif //LIBTGVOIP_AUDIOOUTPUTANDROID_H
