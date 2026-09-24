#ifndef TGCALLS_GROUP_INSTANCE_REFERENCE_IMPL_H
#define TGCALLS_GROUP_INSTANCE_REFERENCE_IMPL_H

#include <cstdint>
#include <functional>
#include <vector>
#include <string>
#include <memory>

#include "../Instance.h"
#include "GroupInstanceImpl.h"

namespace tgcalls {

class LogSinkImpl;
class GroupInstanceReferenceInternal;
class Threads;

class GroupInstanceReferenceImpl final : public GroupInstanceInterface {
public:
    explicit GroupInstanceReferenceImpl(GroupInstanceDescriptor &&descriptor);
    ~GroupInstanceReferenceImpl();

    void stop(std::function<void()> completion) override;

    void setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled, bool isUnifiedBroadcast) override;

    void emitJoinPayload(std::function<void(GroupJoinPayload const &)> completion) override;
    void setJoinResponsePayload(std::string const &payload) override;
    void removeSsrcs(std::vector<uint32_t> ssrcs) override;
    void removeIncomingVideoSource(uint32_t ssrc) override;

    void setIsMuted(bool isMuted) override;
    void setIsNoiseSuppressionEnabled(bool isNoiseSuppressionEnabled) override;
    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) override;
    void setVideoSource(std::function<webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> getVideoSource) override;
    void setAudioOutputDevice(std::string id) override;
    void setAudioInputDevice(std::string id) override;
    void addExternalAudioSamples(std::vector<uint8_t> &&samples) override;

    void addOutgoingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;
    void addIncomingVideoOutput(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) override;

    void setVolume(uint32_t ssrc, double volume) override;
    void setRequestedVideoChannels(std::vector<VideoChannelDescription> &&requestedVideoChannels) override;

    void getStats(std::function<void(GroupInstanceStats)> completion) override;
    void internal_addCustomNetworkEvent(bool isRemoteConnected) override;

private:
    std::shared_ptr<Threads> _threads;
    std::unique_ptr<ThreadLocalObject<GroupInstanceReferenceInternal>> _internal;
    std::unique_ptr<LogSinkImpl> _logSink;
};

} // namespace tgcalls

#endif
