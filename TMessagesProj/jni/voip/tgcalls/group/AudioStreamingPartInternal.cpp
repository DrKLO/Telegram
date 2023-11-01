#include "AudioStreamingPartInternal.h"

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

int16_t sampleFloatToInt16(float sample) {
  return av_clip_int16 (static_cast<int32_t>(lrint(sample*32767)));
}

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

std::vector<AudioStreamingPartInternal::ChannelUpdate> parseChannelUpdates(std::string const &data, int &offset) {
    std::vector<AudioStreamingPartInternal::ChannelUpdate> result;

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

        AudioStreamingPartInternal::ChannelUpdate update;
        update.frameIndex = frameIndex.value();
        update.id = channelId.value();
        update.ssrc = ssrc.value();

        result.push_back(update);
    }

    return result;
}

}

AudioStreamingPartInternal::AudioStreamingPartInternal(std::vector<uint8_t> &&fileData, std::string const &container) :
_avIoContext(std::move(fileData)) {
    int ret = 0;

    _frame = av_frame_alloc();

#if LIBAVFORMAT_VERSION_MAJOR >= 59
    const
#endif
    AVInputFormat *inputFormat = av_find_input_format(container.c_str());
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

    for (int i = 0; i < _inputFormatContext->nb_streams; i++) {
        AVStream *inStream = _inputFormatContext->streams[i];

        AVCodecParameters *inCodecpar = inStream->codecpar;
        if (inCodecpar->codec_type != AVMEDIA_TYPE_AUDIO) {
            continue;
        }
        
        _audioCodecParameters = avcodec_parameters_alloc();
        avcodec_parameters_copy(_audioCodecParameters, inCodecpar);
        
        _streamId = i;

        _durationInMilliseconds = (int)(inStream->duration * av_q2d(inStream->time_base) * 1000);

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
    
    if (_streamId == -1) {
        _didReadToEnd = true;
    }
}

AudioStreamingPartInternal::~AudioStreamingPartInternal() {
    if (_frame) {
        av_frame_free(&_frame);
    }
    if (_inputFormatContext) {
        avformat_close_input(&_inputFormatContext);
    }
    if (_audioCodecParameters) {
        avcodec_parameters_free(&_audioCodecParameters);
    }
}

AudioStreamingPartInternal::ReadPcmResult AudioStreamingPartInternal::readPcm(AudioStreamingPartPersistentDecoder &persistentDecoder, std::vector<int16_t> &outPcm) {
    if (_didReadToEnd) {
        return AudioStreamingPartInternal::ReadPcmResult();
    }
    
    int outPcmSampleOffset = 0;
    ReadPcmResult result;
    
    if (_pcmBufferSampleOffset >= _pcmBufferSampleSize) {
        fillPcmBuffer(persistentDecoder);
    }

    if (outPcm.size() != 480 * _channelCount) {
        outPcm.resize(480 * _channelCount);
    }
    int readSamples = 0;
    if (_channelCount != 0) {
        readSamples = (int)outPcm.size() / _channelCount;
    }

    while (outPcmSampleOffset < readSamples) {
        if (_pcmBufferSampleOffset >= _pcmBufferSampleSize) {
            fillPcmBuffer(persistentDecoder);

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
            _readSampleCount += readFromPcmBufferSamples;
        }
    }
    
    result.numChannels = _channelCount;
    
    // Uncomment for debugging incomplete frames
    /*if (result.numSamples != 480 && result.numSamples != 0) {
        RTC_LOG(LS_INFO) << "result.numSamples = " << result.numSamples << ", _readSampleCount = " << _readSampleCount << ", duration = " << _inputFormatContext->streams[_streamId]->duration;
    }*/

    return result;
}

int AudioStreamingPartInternal::getDurationInMilliseconds() const {
    return _durationInMilliseconds;
}

std::vector<AudioStreamingPartInternal::ChannelUpdate> const &AudioStreamingPartInternal::getChannelUpdates() const {
    return _channelUpdates;
}

std::map<std::string, int32_t> AudioStreamingPartInternal::getEndpointMapping() const {
    return _endpointMapping;
}
    
void AudioStreamingPartInternal::fillPcmBuffer(AudioStreamingPartPersistentDecoder &persistentDecoder) {
    _pcmBufferSampleSize = 0;
    _pcmBufferSampleOffset = 0;

    if (_didReadToEnd) {
        return;
    }
    if (!_inputFormatContext) {
        _didReadToEnd = true;
        return;
    }

    int ret = 0;
    while (true) {
      ret = av_read_frame(_inputFormatContext, &_packet);
      if (ret < 0) {
        _didReadToEnd = true;
        return;
      }
        
      if (_packet.stream_index != _streamId) {
        av_packet_unref(&_packet);
        continue;
      }
        
      ret = persistentDecoder.decode(_audioCodecParameters, _inputFormatContext->streams[_streamId]->time_base, _packet, _frame);
      av_packet_unref(&_packet);

      if (ret == AVERROR(EAGAIN)) {
        continue;
      }
        
      break;
    }

    if (ret != 0) {
        _didReadToEnd = true;
        return;
    }
    
    if (_channelCount == 0) {
        _channelCount = _frame->channels;
    }
    
    if (_channelCount == 0) {
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

    switch (_frame->format) {
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
        RTC_FATAL() << "Unexpected sample_fmt";
    } break;
    }

    _pcmBufferSampleSize = _frame->nb_samples;
    _pcmBufferSampleOffset = 0;
}

}
