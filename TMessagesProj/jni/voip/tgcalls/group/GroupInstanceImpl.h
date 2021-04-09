#ifndef TGCALLS_GROUP_INSTANCE_IMPL_H
#define TGCALLS_GROUP_INSTANCE_IMPL_H

#include <functional>
#include <vector>
#include <string>
#include <memory>
#include <map>

#include "../Instance.h"

#include "../StaticThreads.h"

namespace webrtc {
class AudioDeviceModule;
class TaskQueueFactory;
}

namespace rtc {
template <class T>
class scoped_refptr;
}

namespace tgcalls {

class LogSinkImpl;
class GroupInstanceManager;
struct AudioFrame;

struct GroupConfig {
    bool need_log{true};
    FilePath logPath;
};

struct GroupLevelValue {
    float level = 0.;
    bool voice = false;
};

struct GroupLevelUpdate {
    uint32_t ssrc = 0;
    GroupLevelValue value;
};

struct GroupLevelsUpdate {
    std::vector<GroupLevelUpdate> updates;
};

class BroadcastPartTask {
public:
    virtual ~BroadcastPartTask() = default;

    virtual void cancel() = 0;
};

struct BroadcastPart {
    enum class Status {
        Success,
        NotReady,
        ResyncNeeded
    };

    int64_t timestampMilliseconds = 0;
    double responseTimestamp = 0;
    Status status = Status::NotReady;
    std::vector<uint8_t> oggData;
};

enum class GroupConnectionMode {
    GroupConnectionModeNone,
    GroupConnectionModeRtc,
    GroupConnectionModeBroadcast
};

struct GroupNetworkState {
    bool isConnected = false;
    bool isTransitioningFromBroadcastToRtc = false;
};

struct GroupInstanceDescriptor {
    std::shared_ptr<Threads> threads;
    GroupConfig config;
    std::function<void(GroupNetworkState)> networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> audioLevelsUpdated;
    std::function<void(uint32_t, const AudioFrame &)> onAudioFrame;
    std::string initialInputDeviceId;
    std::string initialOutputDeviceId;
    bool useDummyChannel{true};
    bool disableIncomingChannels{false};
    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> createAudioDeviceModule;
    std::shared_ptr<VideoCaptureInterface> videoCapture;
    std::function<void(std::vector<uint32_t> const &)> incomingVideoSourcesUpdated;
    std::function<void(std::vector<uint32_t> const &)> participantDescriptionsRequired;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> requestBroadcastPart;
    std::shared_ptr<PlatformContext> platformContext;
};

struct GroupJoinPayloadFingerprint {
    std::string hash;
    std::string setup;
    std::string fingerprint;
};

struct GroupJoinPayloadVideoSourceGroup {
    std::vector<uint32_t> ssrcs;
    std::string semantics;
};

struct GroupJoinPayloadVideoPayloadFeedbackType {
    std::string type;
    std::string subtype;
};

struct GroupJoinPayloadVideoPayloadType {
    uint32_t id = 0;
    std::string name;
    uint32_t clockrate = 0;
    uint32_t channels = 0;
    std::vector<GroupJoinPayloadVideoPayloadFeedbackType> feedbackTypes;
    std::vector<std::pair<std::string, std::string>> parameters;
};

struct GroupJoinPayload {
    std::string ufrag;
    std::string pwd;
    std::vector<GroupJoinPayloadFingerprint> fingerprints;

    std::vector<GroupJoinPayloadVideoPayloadType> videoPayloadTypes;
    std::vector<std::pair<uint32_t, std::string>> videoExtensionMap;
    uint32_t ssrc = 0;
    std::vector<GroupJoinPayloadVideoSourceGroup> videoSourceGroups;
};

struct GroupParticipantDescription {
    std::string endpointId;
    uint32_t audioSsrc = 0;
    std::vector<GroupJoinPayloadVideoPayloadType> videoPayloadTypes;
    std::vector<std::pair<uint32_t, std::string>> videoExtensionMap;
    std::vector<GroupJoinPayloadVideoSourceGroup> videoSourceGroups;
    bool isRemoved = false;
};

struct GroupJoinResponseCandidate {
    std::string port;
    std::string protocol;
    std::string network;
    std::string generation;
    std::string id;
    std::string component;
    std::string foundation;
    std::string priority;
    std::string ip;
    std::string type;

    std::string tcpType;
    std::string relAddr;
    std::string relPort;
};

struct GroupJoinResponsePayload {
    std::string ufrag;
    std::string pwd;
    std::vector<GroupJoinPayloadFingerprint> fingerprints;
    std::vector<GroupJoinResponseCandidate> candidates;
};

template <typename T>
class ThreadLocalObject;

class GroupInstanceInterface {
protected:
    GroupInstanceInterface() = default;

public:
    virtual ~GroupInstanceInterface() = default;

    virtual void stop() = 0;

    virtual void setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled) = 0;

    virtual void emitJoinPayload(std::function<void(GroupJoinPayload)> completion) = 0;
    virtual void setJoinResponsePayload(GroupJoinResponsePayload payload, std::vector<tgcalls::GroupParticipantDescription> &&participants) = 0;
    virtual void addParticipants(std::vector<GroupParticipantDescription> &&participants) = 0;
    virtual void removeSsrcs(std::vector<uint32_t> ssrcs) = 0;

    virtual void setIsMuted(bool isMuted) = 0;
    virtual void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, std::function<void(GroupJoinPayload)> completion) = 0;
    virtual void setAudioOutputDevice(std::string id) = 0;
    virtual void setAudioInputDevice(std::string id) = 0;

    virtual void addIncomingVideoOutput(uint32_t ssrc, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;

    virtual void setVolume(uint32_t ssrc, double volume) = 0;
    virtual void setFullSizeVideoSsrc(uint32_t ssrc) = 0;

    struct AudioDevice {
      enum class Type {Input, Output};
      std::string name;
      std::string guid;
    };
    static std::vector<GroupInstanceInterface::AudioDevice> getAudioDevices(AudioDevice::Type type);
};

} // namespace tgcalls

#endif
