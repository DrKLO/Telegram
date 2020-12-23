#ifndef TGCALLS_GROUP_INSTANCE_IMPL_H
#define TGCALLS_GROUP_INSTANCE_IMPL_H

#include <functional>
#include <vector>
#include <string>
#include <memory>
#include <map>

#include "../Instance.h"

namespace webrtc {
class AudioDeviceModule;
class TaskQueueFactory;
}

namespace tgcalls {

class LogSinkImpl;
class GroupInstanceManager;

struct GroupConfig {
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

struct GroupInstanceDescriptor {
    GroupConfig config;
    std::function<void(bool)> networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> audioLevelsUpdated;
    std::string initialInputDeviceId;
    std::string initialOutputDeviceId;
    bool debugIgnoreMissingSsrcs = false;
    std::shared_ptr<PlatformContext> platformContext;
};

struct GroupJoinPayloadFingerprint {
    std::string hash;
    std::string setup;
    std::string fingerprint;
};

struct GroupJoinPayload {
    std::string ufrag;
    std::string pwd;
    std::vector<GroupJoinPayloadFingerprint> fingerprints;

    uint32_t ssrc = 0;
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

class GroupInstanceImpl final {
public:
	explicit GroupInstanceImpl(GroupInstanceDescriptor &&descriptor);
	~GroupInstanceImpl();

    void stop();

    void emitJoinPayload(std::function<void(GroupJoinPayload)> completion);
    void setJoinResponsePayload(GroupJoinResponsePayload payload);
    void removeSsrcs(std::vector<uint32_t> ssrcs);

    void setIsMuted(bool isMuted);
    void setAudioOutputDevice(std::string id);
    void setAudioInputDevice(std::string id);

    struct AudioDevice {
      enum class Type {Input, Output};
      std::string name;
      std::string guid;
    };
    static std::vector<AudioDevice> getAudioDevices(AudioDevice::Type type);
private:
	std::unique_ptr<ThreadLocalObject<GroupInstanceManager>> _manager;
	std::unique_ptr<LogSinkImpl> _logSink;

};

} // namespace tgcalls

#endif
