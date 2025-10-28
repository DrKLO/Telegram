#include "VideoStreamingPart.h"

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"
#include "api/video/i420_buffer.h"

#include "AVIOContextImpl.h"
#include "platform/PlatformInterface.h"

#include <string>
#include <set>
#include <map>

namespace tgcalls {

namespace {

class MediaDataPacket {
public:
    MediaDataPacket() : _packet(av_packet_alloc()) {
    }

    MediaDataPacket(MediaDataPacket &&other) : _packet(other._packet) {
        other._packet = nullptr;
    }

    ~MediaDataPacket() {
        if (_packet) {
            av_packet_free(&_packet);
        }
    }

    AVPacket *packet() {
        return _packet;
    }

private:
    AVPacket *_packet = nullptr;
};

class DecodableFrame {
public:
    DecodableFrame(MediaDataPacket packet, int64_t pts, int64_t dts):
    _packet(std::move(packet)),
    _pts(pts),
    _dts(dts) {
    }

    ~DecodableFrame() {
    }

    MediaDataPacket &packet() {
        return _packet;
    }

    int64_t pts() {
        return _pts;
    }

    int64_t dts() {
        return _dts;
    }

private:
    MediaDataPacket _packet;
    int64_t _pts = 0;
    int64_t _dts = 0;
};

class Frame {
public:
    Frame() {
        _frame = av_frame_alloc();
    }

    Frame(Frame &&other) {
        _frame = other._frame;
        other._frame = nullptr;
    }

    ~Frame() {
        if (_frame) {
            av_frame_free(&_frame);
        }
    }

    AVFrame *frame() {
        return _frame;
    }

    double pts(AVStream *stream, double &firstFramePts) {
        int64_t framePts = _frame->pts;
        double spf = av_q2d(stream->time_base);
        double value = ((double)framePts) * spf;
        
        if (firstFramePts < 0.0) {
            firstFramePts = value;
        }
        
        return value - firstFramePts;
    }

private:
    AVFrame *_frame = nullptr;
};

struct VideoStreamEvent {
    int32_t offset = 0;
    std::string endpointId;
    int32_t rotation = 0;
    int32_t extra = 0;
};

struct VideoStreamInfo {
    std::string container;
    int32_t activeMask = 0;
    std::vector<VideoStreamEvent> events;
};

absl::optional<int32_t> readInt32(std::vector<uint8_t> const &data, int &offset) {
    if (offset + 4 > data.size()) {
        return absl::nullopt;
    }

    int32_t value = 0;
    memcpy(&value, data.data() + offset, 4);
    offset += 4;

    return value;
}

absl::optional<uint8_t> readBytesAsInt32(std::vector<uint8_t> const &data, int &offset, int count) {
    if (offset + count > data.size()) {
        return absl::nullopt;
    }

    if (count == 0) {
        return absl::nullopt;
    }

    if (count <= 4) {
        int32_t value = 0;
        memcpy(&value, data.data() + offset, count);
        offset += count;
        return value;
    } else {
        return absl::nullopt;
    }
}

int32_t roundUp(int32_t numToRound, int32_t multiple) {
    if (multiple == 0) {
        return numToRound;
    }

    int32_t remainder = numToRound % multiple;
    if (remainder == 0) {
        return numToRound;
    }

    return numToRound + multiple - remainder;
}

absl::optional<std::string> readSerializedString(std::vector<uint8_t> const &data, int &offset) {
    if (const auto tmp = readBytesAsInt32(data, offset, 1)) {
        int paddingBytes = 0;
        int length = 0;
        if (tmp.value() == 254) {
            if (const auto len = readBytesAsInt32(data, offset, 3)) {
                length = len.value();
                paddingBytes = roundUp(length, 4) - length;
            } else {
                return absl::nullopt;
            }
        }
        else {
            length = tmp.value();
            paddingBytes = roundUp(length + 1, 4) - (length + 1);
        }

        if (offset + length > data.size()) {
            return absl::nullopt;
        }

        std::string result(data.data() + offset, data.data() + offset + length);

        offset += length;
        offset += paddingBytes;

        return result;
    } else {
        return absl::nullopt;
    }
}

absl::optional<VideoStreamEvent> readVideoStreamEvent(std::vector<uint8_t> const &data, int &offset) {
    VideoStreamEvent event;

    if (const auto offsetValue = readInt32(data, offset)) {
        event.offset = offsetValue.value();
    } else {
        return absl::nullopt;
    }

    if (const auto endpointId = readSerializedString(data, offset)) {
        event.endpointId = endpointId.value();
    } else {
        return absl::nullopt;
    }

    if (const auto rotation = readInt32(data, offset)) {
        event.rotation = rotation.value();
    } else {
        return absl::nullopt;
    }

    if (const auto extra = readInt32(data, offset)) {
        event.extra = extra.value();
    } else {
        return absl::nullopt;
    }

    return event;
}

absl::optional<VideoStreamInfo> consumeVideoStreamInfo(std::vector<uint8_t> &data) {
    int offset = 0;
    if (const auto signature = readInt32(data, offset)) {
        if (signature.value() != 0xa12e810d) {
            return absl::nullopt;
        }
    } else {
        return absl::nullopt;
    }

    VideoStreamInfo info;

    if (const auto container = readSerializedString(data, offset)) {
        info.container = container.value();
    } else {
        return absl::nullopt;
    }

    if (const auto activeMask = readInt32(data, offset)) {
        info.activeMask = activeMask.value();
    } else {
        return absl::nullopt;
    }

    if (const auto eventCount = readInt32(data, offset)) {
        if (eventCount > 0) {
            if (const auto event = readVideoStreamEvent(data, offset)) {
                info.events.push_back(event.value());
            } else {
                return absl::nullopt;
            }
        } else {
            return absl::nullopt;
        }
    } else {
        return absl::nullopt;
    }

    data.erase(data.begin(), data.begin() + offset);

    return info;
}

bool areCodecParametersEqual(AVCodecParameters const &lhs, AVCodecParameters const &rhs) {
    if (lhs.codec_id != rhs.codec_id) {
        return false;
    }
    if (lhs.extradata_size != rhs.extradata_size) {
        return false;
    }
    if (lhs.extradata_size != 0) {
        if (memcmp(lhs.extradata, rhs.extradata, lhs.extradata_size)) {
            return false;
        }
    }
    if (lhs.format != rhs.format) {
        return false;
    }
    if (lhs.profile != rhs.profile) {
        return false;
    }
    if (lhs.level != rhs.level) {
        return false;
    }
    if (lhs.width != rhs.width) {
        return false;
    }
    if (lhs.height != rhs.height) {
        return false;
    }
    if (lhs.sample_aspect_ratio.num != rhs.sample_aspect_ratio.num) {
        return false;
    }
    if (lhs.sample_aspect_ratio.den != rhs.sample_aspect_ratio.den) {
        return false;
    }
    if (lhs.field_order != rhs.field_order) {
        return false;
    }
    if (lhs.color_range != rhs.color_range) {
        return false;
    }
    if (lhs.color_primaries != rhs.color_primaries) {
        return false;
    }
    if (lhs.color_trc != rhs.color_trc) {
        return false;
    }
    if (lhs.color_space != rhs.color_space) {
        return false;
    }
    if (lhs.chroma_location != rhs.chroma_location) {
        return false;
    }
    
    return true;
}

class VideoStreamingDecoderState {
public:
    static std::unique_ptr<VideoStreamingDecoderState> create(
        AVCodecParameters const *codecParameters,
        AVRational pktTimebase
    ) {
        AVCodec const *codec = nullptr;
        if (!codec) {
            codec = avcodec_find_decoder(codecParameters->codec_id);
        }
        if (!codec) {
            return nullptr;
        }
        AVCodecContext *codecContext = avcodec_alloc_context3(codec);
        int ret = avcodec_parameters_to_context(codecContext, codecParameters);
        if (ret < 0) {
            avcodec_free_context(&codecContext);
            return nullptr;
        } else {
            codecContext->pkt_timebase = pktTimebase;
            
            PlatformInterface::SharedInstance()->setupVideoDecoding(codecContext);
            
            ret = avcodec_open2(codecContext, codec, nullptr);
            if (ret < 0) {
                avcodec_free_context(&codecContext);
                return nullptr;
            }
        }
        
        return std::make_unique<VideoStreamingDecoderState>(
            codecContext,
            codecParameters,
            pktTimebase
        );
    }
    
public:
    VideoStreamingDecoderState(
        AVCodecContext *codecContext,
        AVCodecParameters const *codecParameters,
        AVRational pktTimebase
    ) {
        _codecContext = codecContext;
        _codecParameters = avcodec_parameters_alloc();
        avcodec_parameters_copy(_codecParameters, codecParameters);
        _pktTimebase = pktTimebase;
    }
    
    ~VideoStreamingDecoderState() {
        if (_codecContext) {
            avcodec_close(_codecContext);
            avcodec_free_context(&_codecContext);
        }
        if (_codecParameters) {
            avcodec_parameters_free(&_codecParameters);
        }
    }
    
    bool supportsDecoding(
        AVCodecParameters const *codecParameters,
        AVRational pktTimebase
    ) const {
        if (!areCodecParametersEqual(*_codecParameters, *codecParameters)) {
            return false;
        }
        if (_pktTimebase.num != pktTimebase.num) {
            return false;
        }
        if (_pktTimebase.den != pktTimebase.den) {
            return false;
        }
        return true;
    }
    
    int sendFrame(std::shared_ptr<DecodableFrame> frame) {
        if (frame) {
            int status = avcodec_send_packet(_codecContext, frame->packet().packet());
            return status;
        } else {
            int status = avcodec_send_packet(_codecContext, nullptr);
            return status;
        }
    }
    
    int receiveFrame(Frame &frame) {
        int status = avcodec_receive_frame(_codecContext, frame.frame());
        return status;
    }
    
    void reset() {
        avcodec_flush_buffers(_codecContext);
    }
    
private:
    AVCodecContext *_codecContext = nullptr;
    AVCodecParameters *_codecParameters = nullptr;
    AVRational _pktTimebase;
};

}

class VideoStreamingSharedStateInternal {
public:
    VideoStreamingSharedStateInternal() {
    }
    
    ~VideoStreamingSharedStateInternal() {
    }
    
    void updateDecoderState(
        AVCodecParameters const *codecParameters,
        AVRational pktTimebase
    ) {
        if (_decoderState && _decoderState->supportsDecoding(codecParameters, pktTimebase)) {
            return;
        }
        
        _decoderState.reset();
        _decoderState = VideoStreamingDecoderState::create(codecParameters, pktTimebase);
    }
    
    int sendFrame(std::shared_ptr<DecodableFrame> frame) {
        if (!_decoderState) {
            return AVERROR(EIO);
        }
        return _decoderState->sendFrame(frame);
    }
    
    int receiveFrame(Frame &frame) {
        if (!_decoderState) {
            return AVERROR(EIO);
        }
        return _decoderState->receiveFrame(frame);
    }
    
    void reset() {
        if (!_decoderState) {
            return;
        }
        _decoderState->reset();
    }
    
private:
    std::unique_ptr<VideoStreamingDecoderState> _decoderState;
};

VideoStreamingSharedState::VideoStreamingSharedState() {
    _impl = new VideoStreamingSharedStateInternal();
}

VideoStreamingSharedState::~VideoStreamingSharedState() {
    delete _impl;
}

class VideoStreamingPartInternal {
public:
    VideoStreamingPartInternal(std::string endpointId, webrtc::VideoRotation rotation, std::vector<uint8_t> &&fileData, std::string const &container) :
    _endpointId(endpointId),
    _rotation(rotation) {
        _avIoContext = std::make_unique<AVIOContextImpl>(std::move(fileData));

        int ret = 0;

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

        _inputFormatContext->pb = _avIoContext->getContext();

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

        AVCodecParameters *videoCodecParameters = nullptr;
        AVStream *videoStream = nullptr;
        for (int i = 0; i < _inputFormatContext->nb_streams; i++) {
            AVStream *inStream = _inputFormatContext->streams[i];

            AVCodecParameters *inCodecpar = inStream->codecpar;
            if (inCodecpar->codec_type != AVMEDIA_TYPE_VIDEO) {
                continue;
            }
            videoCodecParameters = inCodecpar;
            videoStream = inStream;

            break;
        }

        if (videoCodecParameters && videoStream) {
            _videoCodecParameters = avcodec_parameters_alloc();
            avcodec_parameters_copy(_videoCodecParameters, videoCodecParameters);
            _videoStream = videoStream;
            
            /*const AVCodec *codec = avcodec_find_decoder(videoCodecParameters->codec_id);
            if (codec) {
                _codecContext = avcodec_alloc_context3(codec);
                ret = avcodec_parameters_to_context(_codecContext, videoCodecParameters);
                if (ret < 0) {
                    _didReadToEnd = true;

                    avcodec_free_context(&_codecContext);
                    _codecContext = nullptr;
                } else {
                    _codecContext->pkt_timebase = videoStream->time_base;

                    ret = avcodec_open2(_codecContext, codec, nullptr);
                    if (ret < 0) {
                        _didReadToEnd = true;

                        avcodec_free_context(&_codecContext);
                        _codecContext = nullptr;
                    } else {
                        _videoStream = videoStream;
                    }
                }
            }*/
        }
    }

    ~VideoStreamingPartInternal() {
        if (_videoCodecParameters) {
            avcodec_parameters_free(&_videoCodecParameters);
        }
        if (_inputFormatContext) {
            avformat_close_input(&_inputFormatContext);
        }
    }

    std::string endpointId() {
        return _endpointId;
    }

    absl::optional<MediaDataPacket> readPacket() {
        if (_didReadToEnd) {
            return absl::nullopt;
        }
        if (!_inputFormatContext) {
            return absl::nullopt;
        }

        MediaDataPacket packet;
        int result = av_read_frame(_inputFormatContext, packet.packet());
        if (result < 0) {
            return absl::nullopt;
        }

        return packet;
    }

    std::shared_ptr<DecodableFrame> readNextDecodableFrame() {
        while (true) {
            absl::optional<MediaDataPacket> packet = readPacket();
            if (packet) {
                if (_videoStream && packet->packet()->stream_index == _videoStream->index) {
                    return std::make_shared<DecodableFrame>(std::move(packet.value()), packet->packet()->pts, packet->packet()->dts);
                }
            } else {
                return nullptr;
            }
        }
    }

    absl::optional<VideoStreamingPartFrame> convertCurrentFrame() {
        auto platformFrameBuffer = PlatformInterface::SharedInstance()->createPlatformFrameFromData(_frame.frame());
        if (platformFrameBuffer) {
            auto videoFrame = webrtc::VideoFrame::Builder()
                .set_video_frame_buffer(platformFrameBuffer)
                .set_rotation(_rotation)
                .build();

            return VideoStreamingPartFrame(_endpointId, videoFrame, _frame.pts(_videoStream, _firstFramePts), _frameIndex);
        } else {
            webrtc::scoped_refptr<webrtc::I420Buffer> i420Buffer = webrtc::I420Buffer::Copy(
                _frame.frame()->width,
                _frame.frame()->height,
                _frame.frame()->data[0],
                _frame.frame()->linesize[0],
                _frame.frame()->data[1],
                _frame.frame()->linesize[1],
                _frame.frame()->data[2],
                _frame.frame()->linesize[2]
            );
            if (i420Buffer) {
                auto videoFrame = webrtc::VideoFrame::Builder()
                    .set_video_frame_buffer(i420Buffer)
                    .set_rotation(_rotation)
                    .build();

                return VideoStreamingPartFrame(_endpointId, videoFrame, _frame.pts(_videoStream, _firstFramePts), _frameIndex);
            } else {
                return absl::nullopt;
            }
        }
    }

    absl::optional<VideoStreamingPartFrame> getNextFrame(VideoStreamingSharedState const *sharedState) {
        if (!_videoStream) {
            return {};
        }
        if (!_videoCodecParameters) {
            return {};
        }
        
        sharedState->impl()->updateDecoderState(_videoCodecParameters, _videoStream->time_base);

        while (true) {
            if (_didReadToEnd) {
                if (!_finalFrames.empty()) {
                    auto frame = _finalFrames[0];
                    _finalFrames.erase(_finalFrames.begin());
                    return frame;
                } else {
                    break;
                }
            } else {
                const auto frame = readNextDecodableFrame();
                if (frame) {
                    int sendStatus = sharedState->impl()->sendFrame(frame);
                    if (sendStatus == 0) {
                        int receiveStatus = sharedState->impl()->receiveFrame(_frame);
                        if (receiveStatus == 0) {
                            auto convertedFrame = convertCurrentFrame();
                            if (convertedFrame) {
                                _frameIndex++;
                                return convertedFrame;
                            }
                        } else if (receiveStatus == AVERROR(EAGAIN)) {
                            // more data needed
                        } else {
                            RTC_LOG(LS_ERROR) << "avcodec_receive_frame failed with result: " << receiveStatus;
                            _didReadToEnd = true;
                            break;
                        }
                    } else {
                        RTC_LOG(LS_ERROR) << "avcodec_send_packet failed with result: " << sendStatus;
                        _didReadToEnd = true;
                        return {};
                    }
                } else {
                    _didReadToEnd = true;
                    int sendStatus = sharedState->impl()->sendFrame(nullptr);
                    if (sendStatus == 0) {
                        while (true) {
                            int receiveStatus = sharedState->impl()->receiveFrame(_frame);
                            if (receiveStatus == 0) {
                                auto convertedFrame = convertCurrentFrame();
                                if (convertedFrame) {
                                    _frameIndex++;
                                    _finalFrames.push_back(convertedFrame.value());
                                }
                            } else {
                                if (receiveStatus != AVERROR_EOF) {
                                    RTC_LOG(LS_ERROR) << "avcodec_receive_frame (drain) failed with result: " << receiveStatus;
                                }
                                break;
                            }
                        }
                    } else {
                        RTC_LOG(LS_ERROR) << "avcodec_send_packet (drain) failed with result: " << sendStatus;
                    }
                    sharedState->impl()->reset();
                }
            }
        }

        return {};
    }

private:
    std::string _endpointId;
    webrtc::VideoRotation _rotation = webrtc::VideoRotation::kVideoRotation_0;

    std::unique_ptr<AVIOContextImpl> _avIoContext;

    AVFormatContext *_inputFormatContext = nullptr;
    AVStream *_videoStream = nullptr;
    Frame _frame;
    
    AVCodecParameters *_videoCodecParameters = nullptr;

    std::vector<VideoStreamingPartFrame> _finalFrames;

    int _frameIndex = 0;
    double _firstFramePts = -1.0;
    bool _didReadToEnd = false;
};

class VideoStreamingPartState {
public:
    VideoStreamingPartState(std::vector<uint8_t> &&data, VideoStreamingPart::ContentType contentType) {
        _videoStreamInfo = consumeVideoStreamInfo(data);
        if (!_videoStreamInfo) {
            return;
        }

        for (size_t i = 0; i < _videoStreamInfo->events.size(); i++) {
            if (_videoStreamInfo->events[i].offset < 0) {
                continue;
            }
            size_t endOffset = 0;
            if (i == _videoStreamInfo->events.size() - 1) {
                endOffset = data.size();
            } else {
                endOffset = _videoStreamInfo->events[i + 1].offset;
            }
            if (endOffset <= _videoStreamInfo->events[i].offset) {
                continue;
            }
            if (endOffset > data.size()) {
                continue;
            }
            std::vector<uint8_t> dataSlice(data.begin() + _videoStreamInfo->events[i].offset, data.begin() + endOffset);
            webrtc::VideoRotation rotation = webrtc::VideoRotation::kVideoRotation_0;
            switch (_videoStreamInfo->events[i].rotation) {
                case 0: {
                    rotation = webrtc::VideoRotation::kVideoRotation_0;
                    break;
                }
                case 90: {
                    rotation = webrtc::VideoRotation::kVideoRotation_90;
                    break;
                }
                case 180: {
                    rotation = webrtc::VideoRotation::kVideoRotation_180;
                    break;
                }
                case 270: {
                    rotation = webrtc::VideoRotation::kVideoRotation_270;
                    break;
                }
                default: {
                    break;
                }
            }

            switch (contentType) {
                case VideoStreamingPart::ContentType::Audio: {
                    auto part = std::make_unique<AudioStreamingPart>(std::move(dataSlice), _videoStreamInfo->container, true);
                    _parsedAudioParts.push_back(std::move(part));

                    break;
                }
                case VideoStreamingPart::ContentType::Video: {
                    auto part = std::make_unique<VideoStreamingPartInternal>(_videoStreamInfo->events[i].endpointId, rotation, std::move(dataSlice), _videoStreamInfo->container);
                    _parsedVideoParts.push_back(std::move(part));

                    break;
                }
                default: {
                    break;
                }
            }
        }
    }

    ~VideoStreamingPartState() {
    }

    absl::optional<VideoStreamingPartFrame> getFrameAtRelativeTimestamp(VideoStreamingSharedState const *sharedState, double timestamp) {
        while (true) {
            while (_availableFrames.size() >= 2) {
                if (timestamp >= _availableFrames[1].pts) {
                    _availableFrames.erase(_availableFrames.begin());
                } else {
                    break;
                }
            }
            
            if (_availableFrames.size() < 2) {
                if (!_parsedVideoParts.empty()) {
                    auto result = _parsedVideoParts[0]->getNextFrame(sharedState);
                    if (result) {
                        _availableFrames.push_back(result.value());
                    } else {
                        _parsedVideoParts.erase(_parsedVideoParts.begin());
                    }
                    continue;
                }
            }

            if (!_availableFrames.empty()) {
                for (size_t i = 1; i < _availableFrames.size(); i++) {
                    if (timestamp < _availableFrames[i].pts) {
                        return _availableFrames[i - 1];
                    }
                }
                return _availableFrames[_availableFrames.size() - 1];
            } else {
                return absl::nullopt;
            }
        }
    }

    absl::optional<std::string> getActiveEndpointId() const {
        if (!_parsedVideoParts.empty()) {
            return _parsedVideoParts[0]->endpointId();
        } else {
            return absl::nullopt;
        }
    }
    
    bool hasRemainingFrames() const {
        return !_parsedVideoParts.empty();
    }

    int getAudioRemainingMilliseconds() {
        while (!_parsedAudioParts.empty()) {
            auto firstPartResult = _parsedAudioParts[0]->getRemainingMilliseconds();
            if (firstPartResult <= 0) {
                _parsedAudioParts.erase(_parsedAudioParts.begin());
            } else {
                return firstPartResult;
            }
        }
        return 0;
    }

    std::vector<AudioStreamingPart::StreamingPartChannel> getAudio10msPerChannel(AudioStreamingPartPersistentDecoder &persistentDecoder) {
        while (!_parsedAudioParts.empty()) {
            auto firstPartResult = _parsedAudioParts[0]->get10msPerChannel(persistentDecoder);
            if (firstPartResult.empty()) {
                _parsedAudioParts.erase(_parsedAudioParts.begin());
            } else {
                return firstPartResult;
            }
        }
        return {};
    }

private:
    absl::optional<VideoStreamInfo> _videoStreamInfo;
    std::vector<std::unique_ptr<VideoStreamingPartInternal>> _parsedVideoParts;
    std::vector<VideoStreamingPartFrame> _availableFrames;

    std::vector<std::unique_ptr<AudioStreamingPart>> _parsedAudioParts;
};

VideoStreamingPart::VideoStreamingPart(std::vector<uint8_t> &&data, VideoStreamingPart::ContentType contentType) {
    if (!data.empty()) {
        _state = new VideoStreamingPartState(std::move(data), contentType);
    }
}

VideoStreamingPart::~VideoStreamingPart() {
    if (_state) {
        delete _state;
    }
}

absl::optional<VideoStreamingPartFrame> VideoStreamingPart::getFrameAtRelativeTimestamp(VideoStreamingSharedState const *sharedState, double timestamp) {
    return _state
        ? _state->getFrameAtRelativeTimestamp(sharedState, timestamp)
        : absl::nullopt;
}

absl::optional<std::string> VideoStreamingPart::getActiveEndpointId() const {
    return _state
        ? _state->getActiveEndpointId()
        : absl::nullopt;
}

bool VideoStreamingPart::hasRemainingFrames() const {
    return _state
        ? _state->hasRemainingFrames()
        : false;
}

int VideoStreamingPart::getAudioRemainingMilliseconds() {
    return _state
        ? _state->getAudioRemainingMilliseconds()
        : 0;
}
std::vector<AudioStreamingPart::StreamingPartChannel> VideoStreamingPart::getAudio10msPerChannel(AudioStreamingPartPersistentDecoder &persistentDecoder) {
    return _state
        ? _state->getAudio10msPerChannel(persistentDecoder)
        : std::vector<AudioStreamingPart::StreamingPartChannel>();
}

}
