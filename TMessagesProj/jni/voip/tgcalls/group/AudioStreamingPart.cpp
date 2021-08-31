#include "AudioStreamingPart.h"

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"

extern "C" {
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

#include <string>
#include <bitset>
#include <set>
#include <map>

namespace tgcalls {

namespace {

uint32_t stringToUInt32(std::string const &string) {
    std::stringstream stringStream(string);
    uint32_t value = 0;
    stringStream >> value;
    return value;
}

template <typename Out>
void splitString(const std::string &s, char delim, Out result) {
    std::istringstream iss(s);
    std::string item;
    while (std::getline(iss, item, delim)) {
        *result++ = item;
    }
}

std::vector<std::string> splitString(const std::string &s, char delim) {
    std::vector<std::string> elems;
    splitString(s, delim, std::back_inserter(elems));
    return elems;
}

static absl::optional<uint32_t> readInt32(std::string const &data, int &offset) {
    if (offset + 4 > data.length()) {
        return absl::nullopt;
    }

    int32_t value = 0;
    memcpy(&value, data.data() + offset, 4);
    offset += 4;

    return value;
}

struct ChannelUpdate {
    int frameIndex = 0;
    int id = 0;
    uint32_t ssrc = 0;
};

static std::vector<ChannelUpdate> parseChannelUpdates(std::string const &data, int &offset) {
    std::vector<ChannelUpdate> result;

    auto channels = readInt32(data, offset);
    if (!channels) {
        return {};
    }

    auto count = readInt32(data, offset);
    if (!count) {
        return {};
    }

    for (int i = 0; i < count.value(); i++) {
        auto frameIndex = readInt32(data, offset);
        if (!frameIndex) {
            return {};
        }

        auto channelId = readInt32(data, offset);
        if (!channelId) {
            return {};
        }

        auto ssrc = readInt32(data, offset);
        if (!ssrc) {
            return {};
        }

        ChannelUpdate update;
        update.frameIndex = frameIndex.value();
        update.id = channelId.value();
        update.ssrc = ssrc.value();

        result.push_back(update);
    }

    return result;
}

class AVIOContextImpl {
public:
    AVIOContextImpl(std::vector<uint8_t> &&fileData) :
    _fileData(std::move(fileData)) {
        _buffer.resize(4 * 1024);
        _context = avio_alloc_context(_buffer.data(), (int)_buffer.size(), 0, this, &AVIOContextImpl::read, NULL, &AVIOContextImpl::seek);
    }

    ~AVIOContextImpl() {
        av_free(_context);
    }

    static int read(void *opaque, unsigned char *buffer, int bufferSize) {
        AVIOContextImpl *instance = static_cast<AVIOContextImpl *>(opaque);

        int bytesToRead = std::min(bufferSize, ((int)instance->_fileData.size()) - instance->_fileReadPosition);
        if (bytesToRead < 0) {
            bytesToRead = 0;
        }

        if (bytesToRead > 0) {
            memcpy(buffer, instance->_fileData.data() + instance->_fileReadPosition, bytesToRead);
            instance->_fileReadPosition += bytesToRead;

            return bytesToRead;
        } else {
            return AVERROR_EOF;
        }
    }

    static int64_t seek(void *opaque, int64_t offset, int whence) {
        AVIOContextImpl *instance = static_cast<AVIOContextImpl *>(opaque);

        if (whence == 0x10000) {
            return (int64_t)instance->_fileData.size();
        } else {
            int64_t seekOffset = std::min(offset, (int64_t)instance->_fileData.size());
            if (seekOffset < 0) {
                seekOffset = 0;
            }
            instance->_fileReadPosition = (int)seekOffset;
            return seekOffset;
        }
    }

    AVIOContext *getContext() {
        return _context;
    }

private:
    std::vector<uint8_t> _fileData;
    int _fileReadPosition = 0;

    std::vector<uint8_t> _buffer;
    AVIOContext *_context = nullptr;
};

}

struct ReadPcmResult {
    int numSamples = 0;
    int numChannels = 0;
};

class AudioStreamingPartInternal {
public:
    AudioStreamingPartInternal(std::vector<uint8_t> &&fileData) :
    _avIoContext(std::move(fileData)) {
        int ret = 0;

        _frame = av_frame_alloc();

        AVInputFormat *inputFormat = av_find_input_format("ogg");
        if (!inputFormat) {
            _didReadToEnd = true;
            return;
        }

        _inputFormatContext = avformat_alloc_context();
        if (!_inputFormatContext) {
            _didReadToEnd = true;
            return;
        }

        _inputFormatContext->pb = _avIoContext.getContext();

        if ((ret = avformat_open_input(&_inputFormatContext, "", inputFormat, nullptr)) < 0) {
            _didReadToEnd = true;
            return;
        }

        if ((ret = avformat_find_stream_info(_inputFormatContext, nullptr)) < 0) {
            _didReadToEnd = true;

            avformat_close_input(&_inputFormatContext);
            _inputFormatContext = nullptr;
            return;
        }

        AVCodecParameters *audioCodecParameters = nullptr;
        AVStream *audioStream = nullptr;
        for (int i = 0; i < _inputFormatContext->nb_streams; i++) {
            AVStream *inStream = _inputFormatContext->streams[i];

            AVCodecParameters *inCodecpar = inStream->codecpar;
            if (inCodecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
                continue;
            }
            audioCodecParameters = inCodecpar;
            audioStream = inStream;

            _durationInMilliseconds = (int)((inStream->duration + inStream->first_dts) * 1000 / 48000);

            if (inStream->metadata) {
                AVDictionaryEntry *entry = av_dict_get(inStream->metadata, "TG_META", nullptr, 0);
                if (entry && entry->value) {
                    std::string result;
                    size_t data_used = 0;
                    std::string sourceBase64 = (const char *)entry->value;
                    rtc::Base64::Decode(sourceBase64, rtc::Base64::DO_LAX, &result, &data_used);

                    if (result.size() != 0) {
                        int offset = 0;
                        _channelUpdates = parseChannelUpdates(result, offset);
                    }
                }

                uint32_t videoChannelMask = 0;
                entry = av_dict_get(inStream->metadata, "ACTIVE_MASK", nullptr, 0);
                if (entry && entry->value) {
                    std::string sourceString = (const char *)entry->value;
                    videoChannelMask = stringToUInt32(sourceString);
                }

                std::vector<std::string> endpointList;
                entry = av_dict_get(inStream->metadata, "ENDPOINTS", nullptr, 0);
                if (entry && entry->value) {
                    std::string sourceString = (const char *)entry->value;
                    endpointList = splitString(sourceString, ' ');
                }

                std::bitset<32> videoChannels(videoChannelMask);
                size_t endpointIndex = 0;
                if (videoChannels.count() == endpointList.size()) {
                    for (size_t i = 0; i < videoChannels.size(); i++) {
                        if (videoChannels[i]) {
                            _endpointMapping.insert(std::make_pair(endpointList[endpointIndex], i));
                            endpointIndex++;
                        }
                    }
                }
            }

            break;
        }

        if (audioCodecParameters && audioStream) {
            AVCodec *codec = avcodec_find_decoder(audioCodecParameters->codec_id);
            if (codec) {
                _codecContext = avcodec_alloc_context3(codec);
                ret = avcodec_parameters_to_context(_codecContext, audioCodecParameters);
                if (ret < 0) {
                    _didReadToEnd = true;

                    avcodec_free_context(&_codecContext);
                    _codecContext = nullptr;
                } else {
                    _codecContext->pkt_timebase = audioStream->time_base;

                    _channelCount = _codecContext->channels;

                    ret = avcodec_open2(_codecContext, codec, nullptr);
                    if (ret < 0) {
                        _didReadToEnd = true;

                        avcodec_free_context(&_codecContext);
                        _codecContext = nullptr;
                    }
                }
            }
        }
    }

    ~AudioStreamingPartInternal() {
        if (_frame) {
            av_frame_unref(_frame);
        }
        if (_codecContext) {
            avcodec_close(_codecContext);
            avcodec_free_context(&_codecContext);
        }
        if (_inputFormatContext) {
            avformat_close_input(&_inputFormatContext);
        }
    }

    ReadPcmResult readPcm(std::vector<int16_t> &outPcm) {
        int outPcmSampleOffset = 0;
        ReadPcmResult result;

        int readSamples = (int)outPcm.size() / _channelCount;

        result.numChannels = _channelCount;

        while (outPcmSampleOffset < readSamples) {
            if (_pcmBufferSampleOffset >= _pcmBufferSampleSize) {
                fillPcmBuffer();

                if (_pcmBufferSampleOffset >= _pcmBufferSampleSize) {
                    break;
                }
            }

            int readFromPcmBufferSamples = std::min(_pcmBufferSampleSize - _pcmBufferSampleOffset, readSamples - outPcmSampleOffset);
            if (readFromPcmBufferSamples != 0) {
                std::copy(_pcmBuffer.begin() + _pcmBufferSampleOffset * _channelCount, _pcmBuffer.begin() + _pcmBufferSampleOffset * _channelCount + readFromPcmBufferSamples * _channelCount, outPcm.begin() + outPcmSampleOffset * _channelCount);
                _pcmBufferSampleOffset += readFromPcmBufferSamples;
                outPcmSampleOffset += readFromPcmBufferSamples;
                result.numSamples += readFromPcmBufferSamples;
            }
        }

        return result;
    }

    int getDurationInMilliseconds() {
        return _durationInMilliseconds;
    }

    int getChannelCount() {
        return _channelCount;
    }

    std::vector<ChannelUpdate> const &getChannelUpdates() const {
        return _channelUpdates;
    }

    std::map<std::string, int32_t> getEndpointMapping() const {
        return _endpointMapping;
    }

private:
    static int16_t sampleFloatToInt16(float sample) {
      return av_clip_int16 (static_cast<int32_t>(lrint(sample*32767)));
    }

    void fillPcmBuffer() {
        _pcmBufferSampleSize = 0;
        _pcmBufferSampleOffset = 0;

        if (_didReadToEnd) {
            return;
        }
        if (!_inputFormatContext) {
            _didReadToEnd = true;
            return;
        }
        if (!_codecContext) {
            _didReadToEnd = true;
            return;
        }

        int ret = 0;
        do {
          ret = av_read_frame(_inputFormatContext, &_packet);
          if (ret < 0) {
            _didReadToEnd = true;
            return;
          }

          ret = avcodec_send_packet(_codecContext, &_packet);
          if (ret < 0) {
            _didReadToEnd = true;
            return;
          }

          int bytesPerSample = av_get_bytes_per_sample(_codecContext->sample_fmt);
          if (bytesPerSample != 2 && bytesPerSample != 4) {
            _didReadToEnd = true;
            return;
          }

          ret = avcodec_receive_frame(_codecContext, _frame);
        } while (ret == AVERROR(EAGAIN));

        if (ret != 0) {
            _didReadToEnd = true;
            return;
        }
        if (_frame->channels != _channelCount || _frame->channels > 8) {
            _didReadToEnd = true;
            return;
        }

        if (_pcmBuffer.size() < _frame->nb_samples * _frame->channels) {
            _pcmBuffer.resize(_frame->nb_samples * _frame->channels);
        }

        switch (_codecContext->sample_fmt) {
        case AV_SAMPLE_FMT_S16: {
            memcpy(_pcmBuffer.data(), _frame->data[0], _frame->nb_samples * 2 * _frame->channels);
        } break;

        case AV_SAMPLE_FMT_S16P: {
            int16_t *to = _pcmBuffer.data();
            for (int sample = 0; sample < _frame->nb_samples; ++sample) {
                for (int channel = 0; channel < _frame->channels; ++channel) {
                    int16_t *shortChannel = (int16_t*)_frame->data[channel];
                    *to++ = shortChannel[sample];
                }
            }
        } break;

        case AV_SAMPLE_FMT_FLT: {
			float *floatData = (float *)&_frame->data[0];
			for (int i = 0; i < _frame->nb_samples * _frame->channels; i++) {
				_pcmBuffer[i] = sampleFloatToInt16(floatData[i]);
			}
        } break;

        case AV_SAMPLE_FMT_FLTP: {
			int16_t *to = _pcmBuffer.data();
			for (int sample = 0; sample < _frame->nb_samples; ++sample) {
				for (int channel = 0; channel < _frame->channels; ++channel) {
					float *floatChannel = (float*)_frame->data[channel];
					*to++ = sampleFloatToInt16(floatChannel[sample]);
				}
			}
        } break;

        default: {
            //RTC_FATAL() << "Unexpected sample_fmt";
        } break;
        }

        _pcmBufferSampleSize = _frame->nb_samples;
        _pcmBufferSampleOffset = 0;
    }

private:
    AVIOContextImpl _avIoContext;

    AVFormatContext *_inputFormatContext = nullptr;
    AVPacket _packet;
    AVCodecContext *_codecContext = nullptr;
    AVFrame *_frame = nullptr;

    bool _didReadToEnd = false;

    int _durationInMilliseconds = 0;
    int _channelCount = 0;

    std::vector<ChannelUpdate> _channelUpdates;
    std::map<std::string, int32_t> _endpointMapping;

    std::vector<int16_t> _pcmBuffer;
    int _pcmBufferSampleOffset = 0;
    int _pcmBufferSampleSize = 0;
};

class AudioStreamingPartState {
    struct ChannelMapping {
        uint32_t ssrc = 0;
        int channelIndex = 0;

        ChannelMapping(uint32_t ssrc_, int channelIndex_) :
            ssrc(ssrc_), channelIndex(channelIndex_) {
        }
    };

public:
    AudioStreamingPartState(std::vector<uint8_t> &&data) :
    _parsedPart(std::move(data)) {
        if (_parsedPart.getChannelUpdates().size() == 0) {
            _didReadToEnd = true;
            return;
        }

        _remainingMilliseconds = _parsedPart.getDurationInMilliseconds();
        _pcm10ms.resize(480 * _parsedPart.getChannelCount());

        for (const auto &it : _parsedPart.getChannelUpdates()) {
            _allSsrcs.insert(it.ssrc);
        }
    }

    ~AudioStreamingPartState() {
    }

    std::map<std::string, int32_t> getEndpointMapping() const {
        return _parsedPart.getEndpointMapping();
    }

    int getRemainingMilliseconds() const {
        return _remainingMilliseconds;
    }

    std::vector<AudioStreamingPart::StreamingPartChannel> get10msPerChannel() {
        if (_didReadToEnd) {
            return {};
        }

        for (const auto &update : _parsedPart.getChannelUpdates()) {
            if (update.frameIndex == _frameIndex) {
                updateCurrentMapping(update.ssrc, update.id);
            }
        }

        auto readResult = _parsedPart.readPcm(_pcm10ms);
        if (readResult.numSamples <= 0) {
            _didReadToEnd = true;
            return {};
        }

        std::vector<AudioStreamingPart::StreamingPartChannel> resultChannels;
        for (const auto ssrc : _allSsrcs) {
            AudioStreamingPart::StreamingPartChannel emptyPart;
            emptyPart.ssrc = ssrc;
            resultChannels.push_back(emptyPart);
        }

        for (auto &channel : resultChannels) {
            auto mappedChannelIndex = getCurrentMappedChannelIndex(channel.ssrc);

            if (mappedChannelIndex) {
                int sourceChannelIndex = mappedChannelIndex.value();
                for (int j = 0; j < readResult.numSamples; j++) {
                    channel.pcmData.push_back(_pcm10ms[sourceChannelIndex + j * readResult.numChannels]);
                }
            } else {
                for (int j = 0; j < readResult.numSamples; j++) {
                    channel.pcmData.push_back(0);
                }
            }
        }

        _remainingMilliseconds -= 10;
        if (_remainingMilliseconds < 0) {
            _remainingMilliseconds = 0;
        }
        _frameIndex++;

        return resultChannels;
    }

private:
    absl::optional<int> getCurrentMappedChannelIndex(uint32_t ssrc) {
        for (const auto &it : _currentChannelMapping) {
            if (it.ssrc == ssrc) {
                return it.channelIndex;
            }
        }
        return absl::nullopt;
    }

    void updateCurrentMapping(uint32_t ssrc, int channelIndex) {
        for (int i = (int)_currentChannelMapping.size() - 1; i >= 0; i--) {
            const auto &entry = _currentChannelMapping[i];
            if (entry.ssrc == ssrc && entry.channelIndex == channelIndex) {
                return;
            } else if (entry.ssrc == ssrc || entry.channelIndex == channelIndex) {
                _currentChannelMapping.erase(_currentChannelMapping.begin() + i);
            }
        }
        _currentChannelMapping.emplace_back(ssrc, channelIndex);
    }

private:
    AudioStreamingPartInternal _parsedPart;
    std::set<uint32_t> _allSsrcs;

    std::vector<int16_t> _pcm10ms;
    std::vector<ChannelMapping> _currentChannelMapping;
    int _frameIndex = 0;
    int _remainingMilliseconds = 0;

    bool _didReadToEnd = false;
};

AudioStreamingPart::AudioStreamingPart(std::vector<uint8_t> &&data) {
    if (!data.empty()) {
        _state = new AudioStreamingPartState(std::move(data));
    }
}

AudioStreamingPart::~AudioStreamingPart() {
    if (_state) {
        delete _state;
    }
}

std::map<std::string, int32_t> AudioStreamingPart::getEndpointMapping() const {
    return _state ? _state->getEndpointMapping() : std::map<std::string, int32_t>();
}

int AudioStreamingPart::getRemainingMilliseconds() const {
    return _state ? _state->getRemainingMilliseconds() : 0;
}

std::vector<AudioStreamingPart::StreamingPartChannel> AudioStreamingPart::get10msPerChannel() {
    return _state
        ? _state->get10msPerChannel()
        : std::vector<AudioStreamingPart::StreamingPartChannel>();
}

}
