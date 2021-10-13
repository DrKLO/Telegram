#include "VideoStreamingPart.h"

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"
#include "api/video/i420_buffer.h"

extern "C" {
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

#include <string>
#include <set>
#include <map>

namespace tgcalls {

namespace {

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
            av_frame_unref(_frame);
        }
    }

    AVFrame *frame() {
        return _frame;
    }

    double pts(AVStream *stream) {
        int64_t framePts = _frame->pts;
        double spf = av_q2d(stream->time_base);
        return ((double)framePts) * spf;
    }

    double duration(AVStream *stream) {
        int64_t frameDuration = _frame->pkt_duration;
        double spf = av_q2d(stream->time_base);
        if (frameDuration != 0) {
            return ((double)frameDuration) * spf;
        } else {
            return spf;
        }
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
        if (const auto event = readVideoStreamEvent(data, offset)) {
            info.events.push_back(event.value());
        } else {
            return absl::nullopt;
        }
    } else {
        return absl::nullopt;
    }

    data.erase(data.begin(), data.begin() + offset);

    return info;
}

}

class VideoStreamingPartInternal {
public:
    VideoStreamingPartInternal(std::string endpointId, webrtc::VideoRotation rotation, std::vector<uint8_t> &&fileData, std::string const &container) :
    _endpointId(endpointId),
    _rotation(rotation) {
        _avIoContext = std::make_unique<AVIOContextImpl>(std::move(fileData));

        int ret = 0;

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
            AVCodec *codec = avcodec_find_decoder(videoCodecParameters->codec_id);
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
            }
        }
    }

    ~VideoStreamingPartInternal() {
        if (_codecContext) {
            avcodec_close(_codecContext);
            avcodec_free_context(&_codecContext);
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
        rtc::scoped_refptr<webrtc::I420Buffer> i420Buffer = webrtc::I420Buffer::Copy(
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

            return VideoStreamingPartFrame(_endpointId, videoFrame, _frame.pts(_videoStream), _frame.duration(_videoStream), _frameIndex);
        } else {
            return absl::nullopt;
        }
    }

    absl::optional<VideoStreamingPartFrame> getNextFrame() {
        if (!_codecContext) {
            return {};
        }

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
                    auto status = avcodec_send_packet(_codecContext, frame->packet().packet());
                    if (status == 0) {
                        auto status = avcodec_receive_frame(_codecContext, _frame.frame());
                        if (status == 0) {
                            auto convertedFrame = convertCurrentFrame();
                            if (convertedFrame) {
                                _frameIndex++;
                                return convertedFrame;
                            }
                        } else if (status == -35) {
                            // more data needed
                        } else {
                            _didReadToEnd = true;
                            break;
                        }
                    } else {
                        _didReadToEnd = true;
                        return {};
                    }
                } else {
                    _didReadToEnd = true;
                    int status = avcodec_send_packet(_codecContext, nullptr);
                    if (status == 0) {
                        while (true) {
                            auto status = avcodec_receive_frame(_codecContext, _frame.frame());
                            if (status == 0) {
                                auto convertedFrame = convertCurrentFrame();
                                if (convertedFrame) {
                                    _frameIndex++;
                                    _finalFrames.push_back(convertedFrame.value());
                                }
                            } else {
                                break;
                            }
                        }
                    }
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
    AVCodecContext *_codecContext = nullptr;
    AVStream *_videoStream = nullptr;
    Frame _frame;

    std::vector<VideoStreamingPartFrame> _finalFrames;

    int _frameIndex = 0;
    bool _didReadToEnd = false;
};

class VideoStreamingPartState {
public:
    VideoStreamingPartState(std::vector<uint8_t> &&data) {
        _videoStreamInfo = consumeVideoStreamInfo(data);
        if (!_videoStreamInfo) {
            return;
        }

        for (size_t i = 0; i < _videoStreamInfo->events.size(); i++) {
            std::vector<uint8_t> dataSlice(data.begin() + _videoStreamInfo->events[i].offset, i == (_videoStreamInfo->events.size() - 1) ? data.end() : (data.begin() + _videoStreamInfo->events[i + 1].offset));
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
            auto part = std::make_unique<VideoStreamingPartInternal>(_videoStreamInfo->events[i].endpointId, rotation, std::move(dataSlice), _videoStreamInfo->container);
            _parsedParts.push_back(std::move(part));
        }
    }

    ~VideoStreamingPartState() {
    }

    absl::optional<VideoStreamingPartFrame> getFrameAtRelativeTimestamp(double timestamp) {
        while (true) {
            if (!_currentFrame) {
                if (!_parsedParts.empty()) {
                    auto result = _parsedParts[0]->getNextFrame();
                    if (result) {
                        _currentFrame = result;
                        _relativeTimestamp += result->duration;
                    } else {
                        _parsedParts.erase(_parsedParts.begin());
                        continue;
                    }
                }
            }

            if (_currentFrame) {
                if (timestamp <= _relativeTimestamp) {
                    return _currentFrame;
                } else {
                    _currentFrame = absl::nullopt;
                }
            } else {
                return absl::nullopt;
            }
        }
    }

    absl::optional<std::string> getActiveEndpointId() const {
        if (!_parsedParts.empty()) {
            return _parsedParts[0]->endpointId();
        } else {
            return absl::nullopt;
        }
    }

private:
    absl::optional<VideoStreamInfo> _videoStreamInfo;
    std::vector<std::unique_ptr<VideoStreamingPartInternal>> _parsedParts;
    absl::optional<VideoStreamingPartFrame> _currentFrame;
    double _relativeTimestamp = 0.0;
};

VideoStreamingPart::VideoStreamingPart(std::vector<uint8_t> &&data) {
    if (!data.empty()) {
        _state = new VideoStreamingPartState(std::move(data));
    }
}

VideoStreamingPart::~VideoStreamingPart() {
    if (_state) {
        delete _state;
    }
}

absl::optional<VideoStreamingPartFrame> VideoStreamingPart::getFrameAtRelativeTimestamp(double timestamp) {
    return _state
        ? _state->getFrameAtRelativeTimestamp(timestamp)
        : absl::nullopt;
}

absl::optional<std::string> VideoStreamingPart::getActiveEndpointId() const {
    return _state
        ? _state->getActiveEndpointId()
        : absl::nullopt;
}

}
