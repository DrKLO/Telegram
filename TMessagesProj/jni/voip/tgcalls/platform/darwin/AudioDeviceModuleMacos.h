
#ifndef TGCALLS_AUDIO_DEVICE_MODULE_MACOS
#define TGCALLS_AUDIO_DEVICE_MODULE_MACOS

#include "platform/PlatformInterface.h"

namespace tgcalls {

class AudioDeviceModuleMacos : public DefaultWrappedAudioDeviceModule {
public:
    AudioDeviceModuleMacos(webrtc::scoped_refptr<webrtc::AudioDeviceModule> impl) :
    DefaultWrappedAudioDeviceModule(impl) {
    }

    virtual ~AudioDeviceModuleMacos() {
    }
    virtual int32_t SetStereoPlayout(bool enable) override {
        return WrappedInstance()->SetStereoPlayout(enable);
    }
    

    virtual void Stop() override {
        WrappedInstance()->StopPlayout();
        WrappedInstance()->StopRecording();
        WrappedInstance()->Terminate();
    }
};

}

#endif
