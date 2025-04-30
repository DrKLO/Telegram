#ifndef TGCALLS_AUDIO_DEVICE_MODULE_IOS
#define TGCALLS_AUDIO_DEVICE_MODULE_IOS

#include "platform/PlatformInterface.h"

namespace tgcalls {

class AudioDeviceModuleIOS : public DefaultWrappedAudioDeviceModule {
public:
    AudioDeviceModuleIOS(webrtc::scoped_refptr<webrtc::AudioDeviceModule> impl) :
    DefaultWrappedAudioDeviceModule(impl) {
    }

    virtual ~AudioDeviceModuleIOS() {
    }

    virtual int32_t StopPlayout() override {
        return 0;
    }

    virtual int32_t StopRecording() override {
        return 0;
    }

    virtual int32_t Terminate() override {
        return 0;
    }

    virtual void Stop() override {
        WrappedInstance()->StopPlayout();
        WrappedInstance()->StopRecording();
        WrappedInstance()->Terminate();
    }
};

}

#endif

