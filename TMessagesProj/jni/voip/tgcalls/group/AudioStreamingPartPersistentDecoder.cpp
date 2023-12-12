#include "AudioStreamingPartPersistentDecoder.h"

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"

namespace tgcalls {

WrappedCodecParameters::WrappedCodecParameters(AVCodecParameters const *codecParameters) {
    _value = avcodec_parameters_alloc();
    avcodec_parameters_copy(_value, codecParameters);
}
    
WrappedCodecParameters::~WrappedCodecParameters() {
    avcodec_parameters_free(&_value);
}

bool WrappedCodecParameters::isEqual(AVCodecParameters const *other) {
    if (_value->codec_id != other->codec_id) {
        return false;
    }
    if (_value->format != other->format) {
        return false;
    }
    if (_value->channels != other->channels) {
        return false;
    }
    return true;
}

class AudioStreamingPartPersistentDecoderState {
public:
    AudioStreamingPartPersistentDecoderState(AVCodecParameters const *codecParameters, AVRational timeBase) :
    _codecParameters(codecParameters),
    _timeBase(timeBase) {
        const AVCodec *codec = avcodec_find_decoder(codecParameters->codec_id);
        if (codec) {
            _codecContext = avcodec_alloc_context3(codec);
            int ret = avcodec_parameters_to_context(_codecContext, codecParameters);
            if (ret < 0) {
                avcodec_free_context(&_codecContext);
                _codecContext = nullptr;
            } else {
                _codecContext->pkt_timebase = timeBase;

                _channelCount = _codecContext->channels;

                ret = avcodec_open2(_codecContext, codec, nullptr);
                if (ret < 0) {
                    avcodec_free_context(&_codecContext);
                    _codecContext = nullptr;
                }
            }
        }
    }
    
    ~AudioStreamingPartPersistentDecoderState() {
        if (_codecContext) {
            avcodec_close(_codecContext);
            avcodec_free_context(&_codecContext);
        }
    }
    
    int decode(AVPacket &packet, AVFrame *frame) {
        int ret = avcodec_send_packet(_codecContext, &packet);
        if (ret < 0) {
            return ret;
        }

        int bytesPerSample = av_get_bytes_per_sample(_codecContext->sample_fmt);
        if (bytesPerSample != 2 && bytesPerSample != 4) {
            return -1;
        }

        ret = avcodec_receive_frame(_codecContext, frame);
        return ret;
    }

public:
    WrappedCodecParameters _codecParameters;
    AVRational _timeBase;
    AVCodecContext *_codecContext = nullptr;
    int _channelCount = 0;
};

AudioStreamingPartPersistentDecoder::AudioStreamingPartPersistentDecoder() {
}

AudioStreamingPartPersistentDecoder::~AudioStreamingPartPersistentDecoder() {
    if (_state) {
        delete _state;
        _state = nullptr;
    }
}

void AudioStreamingPartPersistentDecoder::maybeReset(AVCodecParameters const *codecParameters, AVRational timeBase) {
    if (_state) {
        bool isUpdated = false;
        if (!_state->_codecParameters.isEqual(codecParameters)) {
            isUpdated = true;
        }
        if (_state->_timeBase.num != timeBase.num || _state->_timeBase.den != timeBase.den) {
            isUpdated = true;
        }
        if (!isUpdated) {
            return;
        }
    }
    
    if (_state) {
        delete _state;
        _state = nullptr;
    }
    
    _state = new AudioStreamingPartPersistentDecoderState(codecParameters, timeBase);
}

int AudioStreamingPartPersistentDecoder::decode(AVCodecParameters const *codecParameters, AVRational timeBase, AVPacket &packet, AVFrame *frame) {
    maybeReset(codecParameters, timeBase);
    
    if (!_state) {
        return -1;
    }
    
    return _state->decode(packet, frame);
}

}
