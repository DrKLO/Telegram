#ifndef TGCALLS_AUDIO_STREAMING_PART_INTERNAL_H
#define TGCALLS_AUDIO_STREAMING_PART_INTERNAL_H

#include "absl/types/optional.h"
#include <vector>
#include <string>
#include <map>
#include <stdint.h>

#include "AVIOContextImpl.h"
#include "AudioStreamingPartPersistentDecoder.h"

namespace tgcalls {

class AudioStreamingPartInternal {
public:
    struct ReadPcmResult {
        int numSamples = 0;
        int numChannels = 0;
    };

    struct ChannelUpdate {
        int frameIndex = 0;
        int id = 0;
        uint32_t ssrc = 0;
    };

public:
    AudioStreamingPartInternal(std::vector<uint8_t> &&fileData, std::string const &container);
    ~AudioStreamingPartInternal();

    ReadPcmResult readPcm(AudioStreamingPartPersistentDecoder &persistentDecoder, std::vector<int16_t> &outPcm);
    int getDurationInMilliseconds() const;
    std::vector<ChannelUpdate> const &getChannelUpdates() const;
    std::map<std::string, int32_t> getEndpointMapping() const;

private:
    void fillPcmBuffer(AudioStreamingPartPersistentDecoder &persistentDecoder);

private:
    AVIOContextImpl _avIoContext;

    AVFormatContext *_inputFormatContext = nullptr;
    AVPacket _packet;
    AVFrame *_frame = nullptr;
    AVCodecParameters *_audioCodecParameters = nullptr;

    bool _didReadToEnd = false;

    int _durationInMilliseconds = 0;
    int _streamId = -1;
    int _channelCount = 0;

    std::vector<ChannelUpdate> _channelUpdates;
    std::map<std::string, int32_t> _endpointMapping;

    std::vector<int16_t> _pcmBuffer;
    int _pcmBufferSampleOffset = 0;
    int _pcmBufferSampleSize = 0;
    int _readSampleCount = 0;
};

}

#endif
