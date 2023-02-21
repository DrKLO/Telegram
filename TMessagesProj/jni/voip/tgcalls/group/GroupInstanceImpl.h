#ifndef TGCALLS_GROUP_INSTANCE_IMPL_H
#define TGCALLS_GROUP_INSTANCE_IMPL_H

#include <functional>
#include <vector>
#include <string>
#include <memory>
#include <map>

#include "../Instance.h"

#include "../StaticThreads.h"
#include "GroupJoinPayload.h"

namespace webrtc {
class AudioDeviceModule;
class TaskQueueFactory;
class VideoTrackSourceInterface;
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
    bool isMuted = false;
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
    struct VideoParams {
    };

    enum class Status {
        Success,
        NotReady,
        ResyncNeeded
    };

    int64_t timestampMilliseconds = 0;
    double responseTimestamp = 0;
    Status status = Status::NotReady;
    std::vector<uint8_t> data;
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

enum class VideoContentType {
    None,
    Screencast,
    Generic
};

enum class VideoCodecName {
    VP8,
    VP9,
    H264
};

class RequestMediaChannelDescriptionTask {
public:
    virtual ~RequestMediaChannelDescriptionTask() = default;

    virtual void cancel() = 0;
};

struct MediaChannelDescription {
    enum class Type {
        Audio,
        Video
    };

    Type type = Type::Audio;
    uint32_t audioSsrc = 0;
    std::string videoInformation;
};

struct MediaSsrcGroup {
    std::string semantics;
    std::vector<uint32_t> ssrcs;
};

struct VideoChannelDescription {
    enum class Quality {
        Thumbnail,
        Medium,
        Full
    };
    uint32_t audioSsrc = 0;
    std::string endpointId;
    std::vector<MediaSsrcGroup> ssrcGroups;
    Quality minQuality = Quality::Thumbnail;
    Quality maxQuality = Quality::Thumbnail;
};

struct GroupInstanceStats {
    struct IncomingVideoStats {
        int receivingQuality = 0;
        int availableQuality = 0;
    };

    std::vector<std::pair<std::string, IncomingVideoStats>> incomingVideoStats;
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
    std::shared_ptr<VideoCaptureInterface> videoCapture; // deprecated
    std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> getVideoSource;
    std::function<std::shared_ptr<BroadcastPartTask>(std::function<void(int64_t)>)> requestCurrentTime;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, std::function<void(BroadcastPart &&)>)> requestAudioBroadcastPart;
    std::function<std::shared_ptr<BroadcastPartTask>(std::shared_ptr<PlatformContext>, int64_t, int64_t, int32_t, VideoChannelDescription::Quality, std::function<void(BroadcastPart &&)>)> requestVideoBroadcastPart;
    int outgoingAudioBitrateKbit{32};
    bool disableOutgoingAudioProcessing{false};
    bool disableAudioInput{false};
    VideoContentType videoContentType{VideoContentType::None};
    bool initialEnableNoiseSuppression{false};
    std::vector<VideoCodecName> videoCodecPreferences;
    std::function<std::shared_ptr<RequestMediaChannelDescriptionTask>(std::vector<uint32_t> const &, std::function<void(std::vector<MediaChannelDescription> &&)>)> requestMediaChannelDescriptions;
    int minOutgoingVideoBitrateKbit{100};

    std::shared_ptr<PlatformContext> platformContext;
};

template <typename T>
class ThreadLocalObject;

class GroupInstanceInterface {
protected:
    GroupInstanceInterface() = default;

public:
    virtual ~GroupInstanceInterface() = default;

    virtual void stop() = 0;

    virtual void setConnectionMode(GroupConnectionMode connectionMode, bool keepBroadcastIfWasEnabled, bool isUnifiedBroadcast) = 0;

    virtual void emitJoinPayload(std::function<void(GroupJoinPayload const &)> completion) = 0;
    virtual void setJoinResponsePayload(std::string const &payload) = 0;
    virtual void removeSsrcs(std::vector<uint32_t> ssrcs) = 0;
    virtual void removeIncomingVideoSource(uint32_t ssrc) = 0;

    virtual void setIsMuted(bool isMuted) = 0;
    virtual void setIsNoiseSuppressionEnabled(bool isNoiseSuppressionEnabled) = 0;
    virtual void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture) = 0;
    virtual void setVideoSource(std::function<rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>()> getVideoSource) = 0;
    virtual void setAudioOutputDevice(std::string id) = 0;
    virtual void setAudioInputDevice(std::string id) = 0;
    virtual void addExternalAudioSamples(std::vector<uint8_t> &&samples) = 0;

    virtual void addOutgoingVideoOutput(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;
    virtual void addIncomingVideoOutput(std::string const &endpointId, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) = 0;

    virtual void setVolume(uint32_t ssrc, double volume) = 0;
    virtual void setRequestedVideoChannels(std::vector<VideoChannelDescription> &&requestedVideoChannels) = 0;

    virtual void getStats(std::function<void(GroupInstanceStats)> completion) = 0;

    struct AudioDevice {
      enum class Type {Input, Output};
      std::string name;
      std::string guid;
    };
    static std::vector<GroupInstanceInterface::AudioDevice> getAudioDevices(AudioDevice::Type type);
};

} // namespace tgcalls

#endif
