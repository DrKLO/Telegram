#include "GroupInstanceImpl.h"

#include <memory>
#include "api/scoped_refptr.h"
#include "rtc_base/thread.h"
#include "rtc_base/logging.h"
#include "api/peer_connection_interface.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/engine/webrtc_media_engine.h"
#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/rtc_event_log/rtc_event_log_factory.h"
#include "api/peer_connection_interface.h"
#include "api/video_track_source_proxy.h"
#include "system_wrappers/include/field_trial.h"
#include "api/stats/rtcstats_objects.h"
#include "modules/audio_processing/audio_buffer.h"
#include "common_audio/include/audio_util.h"
#include "common_audio/vad/include/webrtc_vad.h"
#include "modules/audio_processing/agc2/vad_with_level.h"

#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"

#include <random>
#include <sstream>
#include <iostream>

namespace tgcalls {

namespace {

static std::vector<std::string> splitSdpLines(std::string const &sdp) {
    std::vector<std::string> result;

    std::istringstream sdpStream(sdp);

    std::string s;
    while (std::getline(sdpStream, s, '\n')) {
        if (s.size() == 0) {
            continue;
        }
        if (s[s.size() - 1] == '\r') {
            s.resize(s.size() - 1);
        }
        result.push_back(s);
    }

    return result;
}

static std::vector<std::string> splitFingerprintLines(std::string const &line) {
    std::vector<std::string> result;

    std::istringstream sdpStream(line);

    std::string s;
    while (std::getline(sdpStream, s, ' ')) {
        if (s.size() == 0) {
            continue;
        }
        result.push_back(s);
    }

    return result;
}

static std::vector<std::string> getLines(std::vector<std::string> const &lines, std::string prefix) {
    std::vector<std::string> result;

    for (auto &line : lines) {
        if (line.find(prefix) == 0) {
            auto cleanLine = line;
            cleanLine.replace(0, prefix.size(), "");
            result.push_back(cleanLine);
        }
    }

    return result;
}

static absl::optional<GroupJoinPayload> parseSdpIntoJoinPayload(std::string const &sdp) {
    GroupJoinPayload result;

    auto lines = splitSdpLines(sdp);

    std::vector<std::string> audioLines;
    bool isAudioLine = false;
    for (auto &line : lines) {
        if (line.find("m=audio") == 0) {
            isAudioLine = true;
        }
        if (isAudioLine) {
            audioLines.push_back(line);
        }
    }

    /*std::vector<uint32_t> audioSources;
    for (auto &line : getLines(audioLines, "a=ssrc:")) {
        std::istringstream iss(line);
        uint32_t value = 0;
        iss >> value;
        if (std::find(audioSources.begin(), audioSources.end(), value) == audioSources.end()) {
            audioSources.push_back(value);
        }
    }

    if (audioSources.size() != 1) {
        return absl::nullopt;
    }
    result.ssrc = audioSources[0];*/
    result.ssrc = 0;

    auto ufragLines = getLines(lines, "a=ice-ufrag:");
    if (ufragLines.size() != 1) {
        return absl::nullopt;
    }
    result.ufrag = ufragLines[0];

    auto pwdLines = getLines(lines, "a=ice-pwd:");
    if (pwdLines.size() != 1) {
        return absl::nullopt;
    }
    result.pwd = pwdLines[0];

    for (auto &line : getLines(lines, "a=fingerprint:")) {
        auto fingerprintComponents = splitFingerprintLines(line);
        if (fingerprintComponents.size() != 2) {
            continue;
        }

        GroupJoinPayloadFingerprint fingerprint;
        fingerprint.hash = fingerprintComponents[0];
        fingerprint.fingerprint = fingerprintComponents[1];
        fingerprint.setup = "active";
        result.fingerprints.push_back(fingerprint);
    }

    return result;
}

struct StreamSpec {
    bool isMain = false;
    uint32_t streamId = 0;
    uint32_t audioSsrcOrZero = 0;
    bool isRemoved = false;
};

static void appendSdp(std::vector<std::string> &lines, std::string const &line) {
    lines.push_back(line);
}

static std::string createSdp(uint32_t sessionId, GroupJoinResponsePayload const &payload, bool isAnswer, std::vector<StreamSpec> const &bundleStreams) {
    std::vector<std::string> sdp;

    appendSdp(sdp, "v=0");

    std::ostringstream sessionIdString;
    sessionIdString << "o=- ";
    sessionIdString << sessionId;
    sessionIdString << " 2 IN IP4 0.0.0.0";
    appendSdp(sdp, sessionIdString.str());

    appendSdp(sdp, "s=-");
    appendSdp(sdp, "t=0 0");

    std::ostringstream bundleString;
    bundleString << "a=group:BUNDLE";
    for (auto &stream : bundleStreams) {
        bundleString << " ";
        if (stream.isMain) {
            bundleString << "0";
        } else {
            bundleString << "audio";
            bundleString << stream.streamId;
        }
    }
    appendSdp(sdp, bundleString.str());

    appendSdp(sdp, "a=ice-lite");

    for (auto &stream : bundleStreams) {
        std::ostringstream audioMidString;
        if (stream.isMain) {
            audioMidString << "0";
        } else {
            audioMidString << "audio";
            audioMidString << stream.streamId;
        }

        std::ostringstream mLineString;
        mLineString << "m=audio ";
        if (stream.isMain) {
            mLineString << "1";
        } else {
            mLineString << "0";
        }
        mLineString << " RTP/SAVPF 111 126";

        appendSdp(sdp, mLineString.str());

        if (stream.isMain) {
            appendSdp(sdp, "c=IN IP4 0.0.0.0");
        }

        std::ostringstream mLineMidString;
        mLineMidString << "a=mid:";
        mLineMidString << audioMidString.str();
        appendSdp(sdp, mLineMidString.str());

        if (stream.isMain) {
            std::ostringstream ufragString;
            ufragString << "a=ice-ufrag:";
            ufragString << payload.ufrag;
            appendSdp(sdp, ufragString.str());

            std::ostringstream pwdString;
            pwdString << "a=ice-pwd:";
            pwdString << payload.pwd;
            appendSdp(sdp, pwdString.str());

            for (auto &fingerprint : payload.fingerprints) {
                std::ostringstream fingerprintString;
                fingerprintString << "a=fingerprint:";
                fingerprintString << fingerprint.hash;
                fingerprintString << " ";
                fingerprintString << fingerprint.fingerprint;
                appendSdp(sdp, fingerprintString.str());
                appendSdp(sdp, "a=setup:passive");
            }

            for (auto &candidate : payload.candidates) {
                std::ostringstream candidateString;
                candidateString << "a=candidate:";
                candidateString << candidate.foundation;
                candidateString << " ";
                candidateString << candidate.component;
                candidateString << " ";
                candidateString << candidate.protocol;
                candidateString << " ";
                candidateString << candidate.priority;
                candidateString << " ";
                candidateString << candidate.ip;
                candidateString << " ";
                candidateString << candidate.port;
                candidateString << " ";
                candidateString << "typ ";
                candidateString << candidate.type;
                candidateString << " ";

                if (candidate.type == "srflx" || candidate.type == "prflx" || candidate.type == "relay") {
                    if (candidate.relAddr.size() != 0 && candidate.relPort.size() != 0) {
                        candidateString << "raddr ";
                        candidateString << candidate.relAddr;
                        candidateString << " ";
                        candidateString << "rport ";
                        candidateString << candidate.relPort;
                        candidateString << " ";
                    }
                }

                if (candidate.protocol == "tcp") {
                    if (candidate.tcpType.size() != 0) {
                        candidateString << "tcptype ";
                        candidateString << candidate.tcpType;
                        candidateString << " ";
                    }
                }

                candidateString << "generation ";
                candidateString << candidate.generation;

                appendSdp(sdp, candidateString.str());
            }
        }

        appendSdp(sdp, "a=rtpmap:111 opus/48000/2");
        appendSdp(sdp, "a=rtpmap:126 telephone-event/8000");
        appendSdp(sdp, "a=fmtp:111 minptime=10; useinbandfec=1");
        appendSdp(sdp, "a=rtcp:1 IN IP4 0.0.0.0");
        appendSdp(sdp, "a=rtcp-mux");
        appendSdp(sdp, "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level");
        appendSdp(sdp, "a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time");
        appendSdp(sdp, "a=extmap:5 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01");
        appendSdp(sdp, "a=rtcp-fb:111 transport-cc");

        if (isAnswer && stream.isMain) {
            appendSdp(sdp, "a=recvonly");
        } else {
            if (stream.isMain) {
                appendSdp(sdp, "a=sendrecv");
            } else {
                appendSdp(sdp, "a=sendonly");
                appendSdp(sdp, "a=bundle-only");
            }

            /*std::ostringstream ssrcGroupString;
            ssrcGroupString << "a=ssrc-group:FID ";
            ssrcGroupString << stream.audioSsrc;
            appendSdp(sdp, ssrcGroupString.str());*/

            if (stream.isRemoved) {
                appendSdp(sdp, "a=inactive");
            } else {
                std::ostringstream cnameString;
                cnameString << "a=ssrc:";
                cnameString << stream.audioSsrcOrZero;
                cnameString << " cname:stream";
                cnameString << stream.streamId;
                appendSdp(sdp, cnameString.str());

                std::ostringstream msidString;
                msidString << "a=ssrc:";
                msidString << stream.audioSsrcOrZero;
                msidString << " msid:stream";
                msidString << stream.streamId;
                msidString << " audio" << stream.streamId;
                appendSdp(sdp, msidString.str());

                std::ostringstream mslabelString;
                mslabelString << "a=ssrc:";
                mslabelString << stream.audioSsrcOrZero;
                mslabelString << " mslabel:audio";
                mslabelString << stream.streamId;
                appendSdp(sdp, mslabelString.str());

                std::ostringstream labelString;
                labelString << "a=ssrc:";
                labelString << stream.audioSsrcOrZero;
                labelString << " label:audio";
                labelString << stream.streamId;
                appendSdp(sdp, labelString.str());
            }
        }
    }

    std::ostringstream result;
    for (auto &line : sdp) {
        result << line << "\n";
    }

    return result.str();
}

static std::string parseJoinResponseIntoSdp(uint32_t sessionId, uint32_t mainStreamAudioSsrc, GroupJoinResponsePayload const &payload, bool isAnswer, std::vector<uint32_t> const &allOtherSsrcs, std::set<uint32_t> const &activeOtherSsrcs) {

    std::vector<StreamSpec> bundleStreams;

    StreamSpec mainStream;
    mainStream.isMain = true;
    mainStream.streamId = 0;
    mainStream.audioSsrcOrZero = mainStreamAudioSsrc;
    mainStream.isRemoved = false;
    bundleStreams.push_back(mainStream);

    uint32_t numStreamsToAllocate = (uint32_t)allOtherSsrcs.size();
    /*if (numStreamsToAllocate < 10) {
        numStreamsToAllocate = 10;
    }*/

    for (uint32_t i = 0; i < numStreamsToAllocate; i++) {
        StreamSpec stream;
        stream.isMain = false;
        if (i < allOtherSsrcs.size()) {
            uint32_t ssrc = allOtherSsrcs[i];
            stream.audioSsrcOrZero = ssrc;
            stream.isRemoved = activeOtherSsrcs.find(ssrc) == activeOtherSsrcs.end();
            stream.streamId = ssrc;
        } else {
            stream.audioSsrcOrZero = 0;
            stream.isRemoved = true;
            stream.streamId = 1 + (uint32_t)i;
        }
        bundleStreams.push_back(stream);
    }

    return createSdp(sessionId, payload, isAnswer, bundleStreams);
}

rtc::Thread *makeNetworkThread() {
    static std::unique_ptr<rtc::Thread> value = rtc::Thread::CreateWithSocketServer();
    value->SetName("WebRTC-Group-Network", nullptr);
    value->Start();
    return value.get();
}

rtc::Thread *getNetworkThread() {
    static rtc::Thread *value = makeNetworkThread();
    return value;
}

rtc::Thread *makeWorkerThread() {
    static std::unique_ptr<rtc::Thread> value = rtc::Thread::Create();
    value->SetName("WebRTC-Group-Worker", nullptr);
    value->Start();
    return value.get();
}

rtc::Thread *getWorkerThread() {
    static rtc::Thread *value = makeWorkerThread();
    return value;
}

rtc::Thread *getSignalingThread() {
    return Manager::getMediaThread();
}

rtc::Thread *getMediaThread() {
    return Manager::getMediaThread();
}

class FrameEncryptorImpl : public webrtc::FrameEncryptorInterface {
public:
    FrameEncryptorImpl() {
    }

    virtual int Encrypt(cricket::MediaType media_type,
                        uint32_t ssrc,
                        rtc::ArrayView<const uint8_t> additional_data,
                        rtc::ArrayView<const uint8_t> frame,
                        rtc::ArrayView<uint8_t> encrypted_frame,
                        size_t* bytes_written) override {
        memcpy(encrypted_frame.data(), frame.data(), frame.size());
        for (auto it = encrypted_frame.begin(); it != encrypted_frame.end(); it++) {
            *it ^= 123;
        }
        *bytes_written = frame.size();
        return 0;
    }

    virtual size_t GetMaxCiphertextByteSize(cricket::MediaType media_type,
                                            size_t frame_size) override {
        return frame_size;
    }
};

class FrameDecryptorImpl : public webrtc::FrameDecryptorInterface {
public:
    FrameDecryptorImpl() {
    }

    virtual webrtc::FrameDecryptorInterface::Result Decrypt(cricket::MediaType media_type,
                           const std::vector<uint32_t>& csrcs,
                           rtc::ArrayView<const uint8_t> additional_data,
                           rtc::ArrayView<const uint8_t> encrypted_frame,
                           rtc::ArrayView<uint8_t> frame) override {
        memcpy(frame.data(), encrypted_frame.data(), encrypted_frame.size());
        for (auto it = frame.begin(); it != frame.end(); it++) {
            *it ^= 123;
        }
        return webrtc::FrameDecryptorInterface::Result(webrtc::FrameDecryptorInterface::Status::kOk, encrypted_frame.size());
    }

    virtual size_t GetMaxPlaintextByteSize(cricket::MediaType media_type,
                                           size_t encrypted_frame_size) override {
        return encrypted_frame_size;
    }
};

class PeerConnectionObserverImpl : public webrtc::PeerConnectionObserver {
private:
    std::function<void(std::string, int, std::string)> _discoveredIceCandidate;
    std::function<void(bool)> _connectionStateChanged;
    std::function<void(rtc::scoped_refptr<webrtc::RtpTransceiverInterface>)> _onTrackAdded;
    std::function<void(rtc::scoped_refptr<webrtc::RtpReceiverInterface>)> _onTrackRemoved;
    std::function<void(uint32_t)> _onMissingSsrc;

public:
    PeerConnectionObserverImpl(
        std::function<void(std::string, int, std::string)> discoveredIceCandidate,
        std::function<void(bool)> connectionStateChanged,
        std::function<void(rtc::scoped_refptr<webrtc::RtpTransceiverInterface>)> onTrackAdded,
        std::function<void(rtc::scoped_refptr<webrtc::RtpReceiverInterface>)> onTrackRemoved,
        std::function<void(uint32_t)> onMissingSsrc
    ) :
    _discoveredIceCandidate(discoveredIceCandidate),
    _connectionStateChanged(connectionStateChanged),
    _onTrackAdded(onTrackAdded),
    _onTrackRemoved(onTrackRemoved),
    _onMissingSsrc(onMissingSsrc) {
    }

    virtual void OnSignalingChange(webrtc::PeerConnectionInterface::SignalingState new_state) override {
    }

    virtual void OnAddStream(rtc::scoped_refptr<webrtc::MediaStreamInterface> stream) override {
    }

    virtual void OnRemoveStream(rtc::scoped_refptr<webrtc::MediaStreamInterface> stream) override {
    }

    virtual void OnDataChannel(rtc::scoped_refptr<webrtc::DataChannelInterface> data_channel) override {
    }

    virtual void OnRenegotiationNeeded() override {
    }

    virtual void OnIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) override {
        bool isConnected = false;
        switch (new_state) {
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionConnected:
            case webrtc::PeerConnectionInterface::IceConnectionState::kIceConnectionCompleted:
                isConnected = true;
                break;
            default:
                break;
        }
        _connectionStateChanged(isConnected);
    }

    virtual void OnStandardizedIceConnectionChange(webrtc::PeerConnectionInterface::IceConnectionState new_state) override {
    }

    virtual void OnConnectionChange(webrtc::PeerConnectionInterface::PeerConnectionState new_state) override {
    }

    virtual void OnIceGatheringChange(webrtc::PeerConnectionInterface::IceGatheringState new_state) override {
    }

    virtual void OnIceCandidate(const webrtc::IceCandidateInterface* candidate) override {
        std::string sdp;
        candidate->ToString(&sdp);
        _discoveredIceCandidate(sdp, candidate->sdp_mline_index(), candidate->sdp_mid());
    }

    virtual void OnIceCandidateError(const std::string& host_candidate, const std::string& url, int error_code, const std::string& error_text) override {
    }

    virtual void OnIceCandidateError(const std::string& address,
                                     int port,
                                     const std::string& url,
                                     int error_code,
                                     const std::string& error_text) override {
    }

    virtual void OnIceCandidatesRemoved(const std::vector<cricket::Candidate>& candidates) override {
    }

    virtual void OnIceConnectionReceivingChange(bool receiving) override {
    }

    virtual void OnIceSelectedCandidatePairChanged(const cricket::CandidatePairChangeEvent& event) override {
    }

    virtual void OnAddTrack(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver, const std::vector<rtc::scoped_refptr<webrtc::MediaStreamInterface>>& streams) override {
    }

    virtual void OnTrack(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) override {
        /*if (transceiver->receiver()) {
            rtc::scoped_refptr<FrameDecryptorImpl> decryptor(new rtc::RefCountedObject<FrameDecryptorImpl>());
            transceiver->receiver()->SetFrameDecryptor(decryptor);
        }*/

        _onTrackAdded(transceiver);
    }

    virtual void OnRemoveTrack(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) override {
        _onTrackRemoved(receiver);
    }

    virtual void OnInterestingUsage(int usage_pattern) override {
    }

    virtual void OnErrorDemuxingPacket(uint32_t ssrc) override {
        _onMissingSsrc(ssrc);
    }
};

class RTCStatsCollectorCallbackImpl : public webrtc::RTCStatsCollectorCallback {
public:
    RTCStatsCollectorCallbackImpl(std::function<void(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &)> completion) :
    _completion(completion) {
    }

    virtual void OnStatsDelivered(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &report) override {
        _completion(report);
    }

private:
    std::function<void(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &)> _completion;
};

static const int kVadResultHistoryLength = 8;

class CombinedVad {
private:
    webrtc::VadLevelAnalyzer _vadWithLevel;
    float _vadResultHistory[kVadResultHistoryLength];

public:
    CombinedVad() {
        for (int i = 0; i < kVadResultHistoryLength; i++) {
            _vadResultHistory[i] = 0.0f;
        }
    }

    ~CombinedVad() {
    }

    bool update(webrtc::AudioBuffer *buffer) {
        webrtc::AudioFrameView<float> frameView(buffer->channels(), buffer->num_channels(), buffer->num_frames());
        auto result = _vadWithLevel.AnalyzeFrame(frameView);
        for (int i = 1; i < kVadResultHistoryLength; i++) {
            _vadResultHistory[i - 1] = _vadResultHistory[i];
        }
        _vadResultHistory[kVadResultHistoryLength - 1] = result.speech_probability;

        float movingAverage = 0.0f;
        for (int i = 0; i < kVadResultHistoryLength; i++) {
            movingAverage += _vadResultHistory[i];
        }
        movingAverage /= (float)kVadResultHistoryLength;

        bool vadResult = false;
        if (movingAverage > 0.8f) {
            vadResult = true;
        }

        return vadResult;
    }
};

class AudioTrackSinkInterfaceImpl: public webrtc::AudioTrackSinkInterface {
private:
    std::function<void(float, bool)> _update;

    int _peakCount = 0;
    uint16_t _peak = 0;

    CombinedVad _vad;

public:
    AudioTrackSinkInterfaceImpl(std::function<void(float, bool)> update) :
    _update(update) {
    }

    virtual ~AudioTrackSinkInterfaceImpl() {
    }

    virtual void OnData(const void *audio_data, int bits_per_sample, int sample_rate, size_t number_of_channels, size_t number_of_frames) override {
        if (bits_per_sample == 16 && number_of_channels == 1) {
            int16_t *samples = (int16_t *)audio_data;
            int numberOfSamplesInFrame = (int)number_of_frames;

            webrtc::AudioBuffer buffer(sample_rate, 1, 48000, 1, 48000, 1);
            webrtc::StreamConfig config(sample_rate, 1);
            buffer.CopyFrom(samples, config);

            bool vadResult = _vad.update(&buffer);

            for (int i = 0; i < numberOfSamplesInFrame; i++) {
                int16_t sample = samples[i];
                if (sample < 0) {
                    sample = -sample;
                }
                if (_peak < sample) {
                    _peak = sample;
                }
                _peakCount += 1;
            }

            if (_peakCount >= 1200) {
                float level = ((float)(_peak)) / 4000.0f;
                _peak = 0;
                _peakCount = 0;
                _update(level, vadResult);
            }
        }
    }
};

class CreateSessionDescriptionObserverImpl : public webrtc::CreateSessionDescriptionObserver {
private:
    std::function<void(std::string, std::string)> _completion;

public:
    CreateSessionDescriptionObserverImpl(std::function<void(std::string, std::string)> completion) :
    _completion(completion) {
    }

    virtual void OnSuccess(webrtc::SessionDescriptionInterface* desc) override {
        if (desc) {
            std::string sdp;
            desc->ToString(&sdp);

            _completion(sdp, desc->type());
        }
    }

    virtual void OnFailure(webrtc::RTCError error) override {
    }
};

class SetSessionDescriptionObserverImpl : public webrtc::SetSessionDescriptionObserver {
private:
    std::function<void()> _completion;
    std::function<void(webrtc::RTCError)> _error;

public:
    SetSessionDescriptionObserverImpl(std::function<void()> completion, std::function<void(webrtc::RTCError)> error) :
    _completion(completion), _error(error) {
    }

    virtual void OnSuccess() override {
        _completion();
    }

    virtual void OnFailure(webrtc::RTCError error) override {
        _error(error);
    }
};

class AudioCaptureAnalyzer : public webrtc::CustomAudioAnalyzer {
private:
    void Initialize(int sample_rate_hz, int num_channels) override {

    }
    // Analyzes the given capture or render signal.
    void Analyze(const webrtc::AudioBuffer* audio) override {
        _analyze(audio);
    }
    // Returns a string representation of the module state.
    std::string ToString() const override {
        return "analyzing";
    }

    std::function<void(const webrtc::AudioBuffer*)> _analyze;

public:
    AudioCaptureAnalyzer(std::function<void(const webrtc::AudioBuffer*)> analyze) :
    _analyze(analyze) {
    }

    virtual ~AudioCaptureAnalyzer() = default;
};

class WrappedAudioDeviceModule : public webrtc::AudioDeviceModule {
private:
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _impl;

public:
    WrappedAudioDeviceModule(rtc::scoped_refptr<webrtc::AudioDeviceModule> impl) :
    _impl(impl) {
    }

    virtual ~WrappedAudioDeviceModule() {
    }

    virtual int32_t ActiveAudioLayer(AudioLayer *audioLayer) const override {
        return _impl->ActiveAudioLayer(audioLayer);
    }

    virtual int32_t RegisterAudioCallback(webrtc::AudioTransport *audioCallback) override {
        return _impl->RegisterAudioCallback(audioCallback);
    }

    virtual int32_t Init() override {
        return _impl->Init();
    }

    virtual int32_t Terminate() override {
        return _impl->Terminate();
    }

    virtual bool Initialized() const override {
        return _impl->Initialized();
    }

    virtual int16_t PlayoutDevices() override {
        return _impl->PlayoutDevices();
    }

    virtual int16_t RecordingDevices() override {
        return _impl->RecordingDevices();
    }

    virtual int32_t PlayoutDeviceName(uint16_t index, char name[webrtc::kAdmMaxDeviceNameSize], char guid[webrtc::kAdmMaxGuidSize]) override {
        return _impl->PlayoutDeviceName(index, name, guid);
    }

    virtual int32_t RecordingDeviceName(uint16_t index, char name[webrtc::kAdmMaxDeviceNameSize], char guid[webrtc::kAdmMaxGuidSize]) override {
        return _impl->RecordingDeviceName(index, name, guid);
    }

    virtual int32_t SetPlayoutDevice(uint16_t index) override {
        return _impl->SetPlayoutDevice(index);
    }

    virtual int32_t SetPlayoutDevice(WindowsDeviceType device) override {
        return _impl->SetPlayoutDevice(device);
    }

    virtual int32_t SetRecordingDevice(uint16_t index) override {
        return _impl->SetRecordingDevice(index);
    }

    virtual int32_t SetRecordingDevice(WindowsDeviceType device) override {
        return _impl->SetRecordingDevice(device);
    }

    virtual int32_t PlayoutIsAvailable(bool *available) override {
        return _impl->PlayoutIsAvailable(available);
    }

    virtual int32_t InitPlayout() override {
        return _impl->InitPlayout();
    }

    virtual bool PlayoutIsInitialized() const override {
        return _impl->PlayoutIsInitialized();
    }

    virtual int32_t RecordingIsAvailable(bool *available) override {
        return _impl->RecordingIsAvailable(available);
    }

    virtual int32_t InitRecording() override {
        return _impl->InitRecording();
    }

    virtual bool RecordingIsInitialized() const override {
        return _impl->RecordingIsInitialized();
    }

    virtual int32_t StartPlayout() override {
        return _impl->StartPlayout();
    }

    virtual int32_t StopPlayout() override {
        return _impl->StopPlayout();
    }

    virtual bool Playing() const override {
        return _impl->Playing();
    }

    virtual int32_t StartRecording() override {
        return _impl->StartRecording();
    }

    virtual int32_t StopRecording() override {
        return _impl->StopRecording();
    }

    virtual bool Recording() const override {
        return _impl->Recording();
    }

    virtual int32_t InitSpeaker() override {
        return _impl->InitSpeaker();
    }

    virtual bool SpeakerIsInitialized() const override {
        return _impl->SpeakerIsInitialized();
    }

    virtual int32_t InitMicrophone() override {
        return _impl->InitMicrophone();
    }

    virtual bool MicrophoneIsInitialized() const override {
        return _impl->MicrophoneIsInitialized();
    }

    virtual int32_t SpeakerVolumeIsAvailable(bool *available) override {
        return _impl->SpeakerVolumeIsAvailable(available);
    }

    virtual int32_t SetSpeakerVolume(uint32_t volume) override {
        return _impl->SetSpeakerVolume(volume);
    }

    virtual int32_t SpeakerVolume(uint32_t* volume) const override {
        return _impl->SpeakerVolume(volume);
    }

    virtual int32_t MaxSpeakerVolume(uint32_t *maxVolume) const override {
        return _impl->MaxSpeakerVolume(maxVolume);
    }

    virtual int32_t MinSpeakerVolume(uint32_t *minVolume) const override {
        return _impl->MinSpeakerVolume(minVolume);
    }

    virtual int32_t MicrophoneVolumeIsAvailable(bool *available) override {
        return _impl->MicrophoneVolumeIsAvailable(available);
    }

    virtual int32_t SetMicrophoneVolume(uint32_t volume) override {
        return _impl->SetMicrophoneVolume(volume);
    }

    virtual int32_t MicrophoneVolume(uint32_t *volume) const override {
        return _impl->MicrophoneVolume(volume);
    }

    virtual int32_t MaxMicrophoneVolume(uint32_t *maxVolume) const override {
        return _impl->MaxMicrophoneVolume(maxVolume);
    }

    virtual int32_t MinMicrophoneVolume(uint32_t *minVolume) const override {
        return _impl->MinMicrophoneVolume(minVolume);
    }

    virtual int32_t SpeakerMuteIsAvailable(bool *available) override {
        return _impl->SpeakerMuteIsAvailable(available);
    }

    virtual int32_t SetSpeakerMute(bool enable) override {
        return _impl->SetSpeakerMute(enable);
    }

    virtual int32_t SpeakerMute(bool *enabled) const override {
        return _impl->SpeakerMute(enabled);
    }

    virtual int32_t MicrophoneMuteIsAvailable(bool *available) override {
        return _impl->MicrophoneMuteIsAvailable(available);
    }

    virtual int32_t SetMicrophoneMute(bool enable) override {
        return _impl->SetMicrophoneMute(enable);
    }

    virtual int32_t MicrophoneMute(bool *enabled) const override {
        return _impl->MicrophoneMute(enabled);
    }

    virtual int32_t StereoPlayoutIsAvailable(bool *available) const override {
        return _impl->StereoPlayoutIsAvailable(available);
    }

    virtual int32_t SetStereoPlayout(bool enable) override {
        return _impl->SetStereoPlayout(enable);
    }

    virtual int32_t StereoPlayout(bool *enabled) const override {
        return _impl->StereoPlayout(enabled);
    }

    virtual int32_t StereoRecordingIsAvailable(bool *available) const override {
        return _impl->StereoRecordingIsAvailable(available);
    }

    virtual int32_t SetStereoRecording(bool enable) override {
        return _impl->SetStereoRecording(enable);
    }

    virtual int32_t StereoRecording(bool *enabled) const override {
        return _impl->StereoRecording(enabled);
    }

    virtual int32_t PlayoutDelay(uint16_t* delayMS) const override {
        return _impl->PlayoutDelay(delayMS);
    }

    virtual bool BuiltInAECIsAvailable() const override {
        return _impl->BuiltInAECIsAvailable();
    }

    virtual bool BuiltInAGCIsAvailable() const override {
        return _impl->BuiltInAGCIsAvailable();
    }

    virtual bool BuiltInNSIsAvailable() const override {
        return _impl->BuiltInNSIsAvailable();
    }

    virtual int32_t EnableBuiltInAEC(bool enable) override {
        return _impl->EnableBuiltInAEC(enable);
    }

    virtual int32_t EnableBuiltInAGC(bool enable) override {
        return _impl->EnableBuiltInAGC(enable);
    }

    virtual int32_t EnableBuiltInNS(bool enable) override {
        return _impl->EnableBuiltInNS(enable);
    }

    virtual int32_t GetPlayoutUnderrunCount() const override {
        return _impl->GetPlayoutUnderrunCount();
    }

#if defined(WEBRTC_IOS)
    virtual int GetPlayoutAudioParameters(webrtc::AudioParameters *params) const override {
        return _impl->GetPlayoutAudioParameters(params);
    }
    virtual int GetRecordAudioParameters(webrtc::AudioParameters *params) const override {
        return _impl->GetRecordAudioParameters(params);
    }
#endif  // WEBRTC_IOS
};

template <typename Out>
void split(const std::string &s, char delim, Out result) {
    std::istringstream iss(s);
    std::string item;
    while (std::getline(iss, item, delim)) {
        *result++ = item;
    }
}

std::vector<std::string> split(const std::string &s, char delim) {
    std::vector<std::string> elems;
    split(s, delim, std::back_inserter(elems));
    return elems;
}

std::string adjustLocalDescription(const std::string &sdp) {
    std::vector<std::string> lines = split(sdp, '\n');

    std::string pattern = "c=IN ";

    bool foundAudio = false;
    std::stringstream result;
    for (const auto &it : lines) {
        result << it << "\n";
        if (!foundAudio && it.compare(0, pattern.size(), pattern) == 0) {
            foundAudio = true;
            result << "b=AS:" << 32 << "\n";
        }
    }

    return result.str();
}

} // namespace



class GroupInstanceManager : public std::enable_shared_from_this<GroupInstanceManager> {
public:
	GroupInstanceManager(GroupInstanceDescriptor &&descriptor) :
    _networkStateUpdated(descriptor.networkStateUpdated),
    _audioLevelsUpdated(descriptor.audioLevelsUpdated),
    _initialInputDeviceId(descriptor.initialInputDeviceId),
    _initialOutputDeviceId(descriptor.initialOutputDeviceId),
    _platformContext(descriptor.platformContext) {
		auto generator = std::mt19937(std::random_device()());
		auto distribution = std::uniform_int_distribution<uint32_t>();
		do {
            _mainStreamAudioSsrc = distribution(generator);
		} while (!_mainStreamAudioSsrc);
	}

	~GroupInstanceManager() {
        assert(getMediaThread()->IsCurrent());

        destroyAudioDeviceModule();
        if (_peerConnection) {
            _peerConnection->Close();
        }
	}

    void generateAndInsertFakeIncomingSsrc() {
        // At least on Windows recording can't be started without playout.
        // We keep a fake incoming stream, so that playout is always started.
        auto generator = std::mt19937(std::random_device()());
        auto distribution = std::uniform_int_distribution<uint32_t>();
        while (true) {
            _fakeIncomingSsrc = distribution(generator);
            if (_fakeIncomingSsrc != 0
                && _fakeIncomingSsrc != _mainStreamAudioSsrc
                && std::find(_allOtherSsrcs.begin(), _allOtherSsrcs.end(), _fakeIncomingSsrc) == _allOtherSsrcs.end()) {
                break;
            }
        }
        _activeOtherSsrcs.emplace(_fakeIncomingSsrc);
        _allOtherSsrcs.emplace_back(_fakeIncomingSsrc);
    }

    bool createAudioDeviceModule(
            const webrtc::PeerConnectionFactoryDependencies &dependencies) {
        _adm_thread = dependencies.worker_thread;
        if (!_adm_thread) {
            return false;
        }
        _adm_thread->Invoke<void>(RTC_FROM_HERE, [&] {
            const auto check = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
                auto result = webrtc::AudioDeviceModule::Create(
                    layer,
                    dependencies.task_queue_factory.get());
                return (result && (result->Init() == 0)) ? result : nullptr;
            };
            if (auto result = check(webrtc::AudioDeviceModule::kPlatformDefaultAudio)) {
                _adm_use_withAudioDeviceModule = new rtc::RefCountedObject<WrappedAudioDeviceModule>(result);
#ifdef WEBRTC_LINUX
            } else if (auto result = check(webrtc::AudioDeviceModule::kLinuxAlsaAudio)) {
                _adm_use_withAudioDeviceModule = new rtc::RefCountedObject<WrappedAudioDeviceModule>(result);
#endif // WEBRTC_LINUX
            }
        });
        return (_adm_use_withAudioDeviceModule != nullptr);
    }
    void destroyAudioDeviceModule() {
		if (!_adm_thread) {
			return;
		}
        _adm_thread->Invoke<void>(RTC_FROM_HERE, [&] {
            _adm_use_withAudioDeviceModule = nullptr;
        });
    }

	void start() {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        webrtc::field_trial::InitFieldTrialsFromString(
            //"WebRTC-Audio-SendSideBwe/Enabled/"
            "WebRTC-Audio-Allocation/min:6kbps,max:32kbps/"
            "WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
            //"WebRTC-FlexFEC-03/Enabled/"
            //"WebRTC-FlexFEC-03-Advertised/Enabled/"
            "WebRTC-PcFactoryDefaultBitrates/min:6kbps,start:32kbps,max:32kbps/"
        );

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        webrtc::PeerConnectionFactoryDependencies dependencies;
        dependencies.network_thread = getNetworkThread();
        dependencies.worker_thread = getWorkerThread();
        dependencies.signaling_thread = getSignalingThread();
        dependencies.task_queue_factory = webrtc::CreateDefaultTaskQueueFactory();

        if (!createAudioDeviceModule(dependencies)) {
            return;
        }

        cricket::MediaEngineDependencies mediaDeps;
        mediaDeps.task_queue_factory = dependencies.task_queue_factory.get();
        mediaDeps.audio_encoder_factory = webrtc::CreateAudioEncoderFactory<webrtc::AudioEncoderOpus>();
        mediaDeps.audio_decoder_factory = webrtc::CreateAudioDecoderFactory<webrtc::AudioDecoderOpus>();
        mediaDeps.video_encoder_factory = PlatformInterface::SharedInstance()->makeVideoEncoderFactory(_platformContext);
        mediaDeps.video_decoder_factory = PlatformInterface::SharedInstance()->makeVideoDecoderFactory(_platformContext);
        mediaDeps.adm = _adm_use_withAudioDeviceModule;

        std::shared_ptr<CombinedVad> myVad(new CombinedVad());

        auto analyzer = new AudioCaptureAnalyzer([&, weak, myVad](const webrtc::AudioBuffer* buffer) {
            if (!buffer) {
                return;
            }
            if (buffer->num_channels() != 1) {
                return;
            }

            float peak = 0;
            int peakCount = 0;
            const float *samples = buffer->channels_const()[0];
            for (int i = 0; i < buffer->num_frames(); i++) {
                float sample = samples[i];
                if (sample < 0) {
                    sample = -sample;
                }
                if (peak < sample) {
                    peak = sample;
                }
                peakCount += 1;
            }

            bool vadStatus = myVad->update((webrtc::AudioBuffer *)buffer);

            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, peak, peakCount, vadStatus](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                strong->_myAudioLevelPeakCount += peakCount;
                if (strong->_myAudioLevelPeak < peak) {
                    strong->_myAudioLevelPeak = peak;
                }
                if (strong->_myAudioLevelPeakCount >= 1200) {
                    float level = strong->_myAudioLevelPeak / 4000.0f;
                    if (strong->_isMuted) {
                        level = 0.0f;
                    }
                    strong->_myAudioLevelPeak = 0;
                    strong->_myAudioLevelPeakCount = 0;
                    strong->_myAudioLevel = GroupLevelValue{
                        level,
                        vadStatus,
                    };
                }
            });
        });

        webrtc::AudioProcessingBuilder builder;
        builder.SetCaptureAnalyzer(std::unique_ptr<AudioCaptureAnalyzer>(analyzer));
        webrtc::AudioProcessing *apm = builder.Create();

        webrtc::AudioProcessing::Config audioConfig;
        webrtc::AudioProcessing::Config::NoiseSuppression noiseSuppression;
        noiseSuppression.enabled = true;
        noiseSuppression.level = webrtc::AudioProcessing::Config::NoiseSuppression::kHigh;
        audioConfig.noise_suppression = noiseSuppression;

        audioConfig.high_pass_filter.enabled = true;

        audioConfig.voice_detection.enabled = true;

        apm->ApplyConfig(audioConfig);

        mediaDeps.audio_processing = apm;

        mediaDeps.onUnknownAudioSsrc = [weak](uint32_t ssrc) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->onMissingSsrc(ssrc);
            });
        };

        dependencies.media_engine = cricket::CreateMediaEngine(std::move(mediaDeps));
        dependencies.call_factory = webrtc::CreateCallFactory();
        dependencies.event_log_factory =
            std::make_unique<webrtc::RtcEventLogFactory>(dependencies.task_queue_factory.get());
        dependencies.network_controller_factory = nullptr;

        _nativeFactory = webrtc::CreateModularPeerConnectionFactory(std::move(dependencies));

        webrtc::PeerConnectionInterface::RTCConfiguration config;
        config.sdp_semantics = webrtc::SdpSemantics::kUnifiedPlan;
        //config.continual_gathering_policy = webrtc::PeerConnectionInterface::ContinualGatheringPolicy::GATHER_CONTINUALLY;
        config.audio_jitter_buffer_fast_accelerate = true;
        config.prioritize_most_likely_ice_candidate_pairs = true;
        config.presume_writable_when_fully_relayed = true;
        //config.audio_jitter_buffer_enable_rtx_handling = true;

        /*webrtc::CryptoOptions cryptoOptions;
        webrtc::CryptoOptions::SFrame sframe;
        sframe.require_frame_encryption = true;
        cryptoOptions.sframe = sframe;
        config.crypto_options = cryptoOptions;*/

        _observer.reset(new PeerConnectionObserverImpl(
            [weak](std::string sdp, int mid, std::string sdpMid) {
                /*getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, mid, sdpMid](){
                    auto strong = weak.lock();
                    if (strong) {
                        //strong->emitIceCandidate(sdp, mid, sdpMid);
                    }
                });*/
            },
            [weak](bool isConnected) {
                getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isConnected](){
                    auto strong = weak.lock();
                    if (strong) {
                        strong->updateIsConnected(isConnected);
                    }
                });
            },
            [weak](rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
                getMediaThread()->PostTask(RTC_FROM_HERE, [weak, transceiver](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onTrackAdded(transceiver);
                });
            },
            [weak](rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
                getMediaThread()->PostTask(RTC_FROM_HERE, [weak, receiver](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onTrackRemoved(receiver);
                });
            },
            [weak](uint32_t ssrc) {
                getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onMissingSsrc(ssrc);
                });
            }
        ));
        _peerConnection = _nativeFactory->CreatePeerConnection(config, nullptr, nullptr, _observer.get());
        assert(_peerConnection != nullptr);

        cricket::AudioOptions options;
        rtc::scoped_refptr<webrtc::AudioSourceInterface> audioSource = _nativeFactory->CreateAudioSource(options);
        std::stringstream name;
        name << "audio";
        name << 0;
        std::vector<std::string> streamIds;
        streamIds.push_back(name.str());
        _localAudioTrack = _nativeFactory->CreateAudioTrack(name.str(), audioSource);
        _localAudioTrack->set_enabled(false);
        auto addedTrack = _peerConnection->AddTrack(_localAudioTrack, streamIds);

        if (addedTrack.ok()) {
            _localAudioTrackSender = addedTrack.value();
            for (auto &it : _peerConnection->GetTransceivers()) {
                if (it->media_type() == cricket::MediaType::MEDIA_TYPE_AUDIO) {
                    if (_localAudioTrackSender.get() == it->sender().get()) {
                        it->SetDirection(webrtc::RtpTransceiverDirection::kRecvOnly);
                    }

                    break;
                }
            }
        }

        setAudioInputDevice(_initialInputDeviceId);
        setAudioOutputDevice(_initialOutputDeviceId);

        // At least on Windows recording doesn't work without started playout.
        withAudioDeviceModule([weak](webrtc::AudioDeviceModule *adm) {
#ifdef WEBRTC_WIN
            // At least on Windows starting/stopping playout while recording
            // is active leads to errors in recording and assertion violation.
			adm->EnableBuiltInAEC(false);
#endif // WEBRTC_WIN

            if (adm->InitPlayout()) {
                adm->StartPlayout();
            } else {
                getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->withAudioDeviceModule([](webrtc::AudioDeviceModule *adm) {
                        if (adm->InitPlayout()) {
                            adm->StartPlayout();
                        }
                    });
                }, 2000);
            }
        });

        //beginStatsTimer(100);
        beginLevelsTimer(50);
	}


    void setAudioInputDevice(std::string id) {
#if !defined(WEBRTC_IOS) && !defined(WEBRTC_ANDROID)
        withAudioDeviceModule([&](webrtc::AudioDeviceModule *adm) {
            const auto recording = adm->Recording();
            if (recording) {
                adm->StopRecording();
            }
            const auto finish = [&] {
                if (recording) {
                    adm->InitRecording();
                    adm->StartRecording();
                }
            };
            if (id == "default" || id.empty()) {
                if (const auto result = adm->SetRecordingDevice(webrtc::AudioDeviceModule::kDefaultCommunicationDevice)) {
                    RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): SetRecordingDevice(kDefaultCommunicationDevice) failed: " << result << ".";
                } else {
                    RTC_LOG(LS_INFO) << "setAudioInputDevice(" << id << "): SetRecordingDevice(kDefaultCommunicationDevice) success.";
                }
                return finish();
            }
            const auto count = adm
                ? adm->RecordingDevices()
                : int16_t(-666);
            if (count <= 0) {
                RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): Could not get recording devices count: " << count << ".";
                return finish();
            }
            for (auto i = 0; i != count; ++i) {
                char name[webrtc::kAdmMaxDeviceNameSize + 1] = { 0 };
                char guid[webrtc::kAdmMaxGuidSize + 1] = { 0 };
                adm->RecordingDeviceName(i, name, guid);
                if (id == guid) {
                    const auto result = adm->SetRecordingDevice(i);
                    if (result != 0) {
                        RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << ") name '" << std::string(name) << "' failed: " << result << ".";
                    } else {
                        RTC_LOG(LS_INFO) << "setAudioInputDevice(" << id << ") name '" << std::string(name) << "' success.";
                    }
                    return finish();
                }
            }
            RTC_LOG(LS_ERROR) << "setAudioInputDevice(" << id << "): Could not find recording device.";
            return finish();
        });
#endif
    }

    void setAudioOutputDevice(std::string id) {
#if !defined(WEBRTC_IOS) && !defined(WEBRTC_ANDROID)
        withAudioDeviceModule([&](webrtc::AudioDeviceModule *adm) {
            const auto playing = adm->Playing();
            if (playing) {
                adm->StopPlayout();
            }
            const auto finish = [&] {
                if (playing) {
                    adm->InitPlayout();
                    adm->StartPlayout();
                }
            };
            if (id == "default" || id.empty()) {
                if (const auto result = adm->SetPlayoutDevice(webrtc::AudioDeviceModule::kDefaultCommunicationDevice)) {
                    RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): SetPlayoutDevice(kDefaultCommunicationDevice) failed: " << result << ".";
                } else {
                    RTC_LOG(LS_INFO) << "setAudioOutputDevice(" << id << "): SetPlayoutDevice(kDefaultCommunicationDevice) success.";
                }
                return finish();
            }
            const auto count = adm
                ? adm->PlayoutDevices()
                : int16_t(-666);
            if (count <= 0) {
                RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): Could not get playout devices count: " << count << ".";
                return finish();
            }
            for (auto i = 0; i != count; ++i) {
                char name[webrtc::kAdmMaxDeviceNameSize + 1] = { 0 };
                char guid[webrtc::kAdmMaxGuidSize + 1] = { 0 };
                adm->PlayoutDeviceName(i, name, guid);
                if (id == guid) {
                    const auto result = adm->SetPlayoutDevice(i);
                    if (result != 0) {
                        RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << ") name '" << std::string(name) << "' failed: " << result << ".";
                    } else {
                        RTC_LOG(LS_INFO) << "setAudioOutputDevice(" << id << ") name '" << std::string(name) << "' success.";
                    }
                    return finish();
                }
            }
            RTC_LOG(LS_ERROR) << "setAudioOutputDevice(" << id << "): Could not find playout device.";
            return finish();
        });
#endif
    }

    void updateIsConnected(bool isConnected) {
        _isConnected = isConnected;

        auto timestamp = rtc::TimeMillis();

        _isConnectedUpdateValidTaskId++;

        if (!isConnected && _appliedOfferTimestamp > timestamp - 1000) {
            auto taskId = _isConnectedUpdateValidTaskId;
            const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
            getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak, taskId]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                if (strong->_isConnectedUpdateValidTaskId == taskId) {
                    strong->_networkStateUpdated(strong->_isConnected);
                }
            }, 1000);
        } else {
            _networkStateUpdated(_isConnected);
        }
    }

    void stop() {
        _peerConnection->Close();
    }

    void emitJoinPayload(std::function<void(GroupJoinPayload)> completion) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak, completion](std::string sdp, std::string type) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type, completion](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto lines = splitSdpLines(sdp);
                std::vector<std::string> resultSdp;

                std::ostringstream generatedSsrcStringStream;
                generatedSsrcStringStream << strong->_mainStreamAudioSsrc;
                auto generatedSsrcString = generatedSsrcStringStream.str();

                for (auto &line : lines) {
                    auto adjustedLine = line;
                    if (adjustedLine.find("a=ssrc:") == 0) {
                        int startIndex = 7;
                        int i = startIndex;
                        while (i < adjustedLine.size()) {
                            if (!isdigit(adjustedLine[i])) {
                                break;
                            }
                            i++;
                        }
                        if (i >= startIndex) {
                            adjustedLine.replace(startIndex, i - startIndex, generatedSsrcString);
                        }
                    }
                    appendSdp(resultSdp, adjustedLine);
                }

                std::ostringstream result;
                for (auto &line : resultSdp) {
                    result << line << "\n";
                }

                auto adjustedSdp = result.str();

                RTC_LOG(LoggingSeverity::WARNING) << "----- setLocalDescription join -----";
                RTC_LOG(LoggingSeverity::WARNING) << adjustedSdp;
                RTC_LOG(LoggingSeverity::WARNING) << "-----";

                webrtc::SdpParseError error;
                webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, adjustLocalDescription(adjustedSdp), &error);
                if (sessionDescription != nullptr) {
                    rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, adjustedSdp, completion]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        auto payload = parseSdpIntoJoinPayload(adjustedSdp);
                        if (payload) {
                            payload->ssrc = strong->_mainStreamAudioSsrc;
                            completion(payload.value());
                        }
                    }, [](webrtc::RTCError error) {
                    }));
                    strong->_peerConnection->SetLocalDescription(observer, sessionDescription);
                } else {
                    return;
                }
            });
        }));
        _peerConnection->CreateOffer(observer, options);
    }

    void setJoinResponsePayload(GroupJoinResponsePayload payload) {
        _joinPayload = payload;
        auto sdp = parseJoinResponseIntoSdp(_sessionId, _mainStreamAudioSsrc, payload, true, _allOtherSsrcs, _activeOtherSsrcs);
        setOfferSdp(sdp, true, true, false);
    }

    void removeSsrcs(std::vector<uint32_t> ssrcs) {
        if (!_joinPayload) {
            return;
        }

        bool updated = false;
        for (auto ssrc : ssrcs) {
            if (std::find(_allOtherSsrcs.begin(), _allOtherSsrcs.end(), ssrc) != _allOtherSsrcs.end() && std::find(_activeOtherSsrcs.begin(), _activeOtherSsrcs.end(), ssrc) != _activeOtherSsrcs.end()) {
                if (!_fakeIncomingSsrc || ssrc == _fakeIncomingSsrc) {
                    generateAndInsertFakeIncomingSsrc();
                }
                _activeOtherSsrcs.erase(ssrc);
                updated = true;
            }
        }

        if (updated) {
            auto sdp = parseJoinResponseIntoSdp(_sessionId, _mainStreamAudioSsrc, _joinPayload.value(), false, _allOtherSsrcs, _activeOtherSsrcs);
            setOfferSdp(sdp, false, false, false);
        }
    }

    void addSsrcsInternal(std::vector<uint32_t> const &ssrcs, bool completeMissingSsrcSetup) {
        if (!_joinPayload) {
            if (completeMissingSsrcSetup) {
                completeProcessingMissingSsrcs();
            }
            return;
        }

        for (auto ssrc : ssrcs) {
            if (std::find(_allOtherSsrcs.begin(), _allOtherSsrcs.end(), ssrc) == _allOtherSsrcs.end()) {
                _allOtherSsrcs.push_back(ssrc);
                _activeOtherSsrcs.insert(ssrc);
            }
        }

        auto sdp = parseJoinResponseIntoSdp(_sessionId, _mainStreamAudioSsrc, _joinPayload.value(), false, _allOtherSsrcs, _activeOtherSsrcs);
        setOfferSdp(sdp, false, false, completeMissingSsrcSetup);
    }

    void applyLocalSdp() {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak](std::string sdp, std::string type) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto lines = splitSdpLines(sdp);
                std::vector<std::string> resultSdp;

                std::ostringstream generatedSsrcStringStream;
                generatedSsrcStringStream << strong->_mainStreamAudioSsrc;
                auto generatedSsrcString = generatedSsrcStringStream.str();

                for (auto &line : lines) {
                    auto adjustedLine = line;
                    if (adjustedLine.find("a=ssrc:") == 0) {
                        int startIndex = 7;
                        int i = startIndex;
                        while (i < adjustedLine.size()) {
                            if (!isdigit(adjustedLine[i])) {
                                break;
                            }
                            i++;
                        }
                        if (i >= startIndex) {
                            adjustedLine.replace(startIndex, i - startIndex, generatedSsrcString);
                        }
                    }
                    appendSdp(resultSdp, adjustedLine);
                }

                std::ostringstream result;
                for (auto &line : resultSdp) {
                    result << line << "\n";
                }

                auto adjustedSdp = result.str();

                RTC_LOG(LoggingSeverity::WARNING) << "----- setLocalDescription applyLocalSdp -----";
                RTC_LOG(LoggingSeverity::WARNING) << adjustedSdp;
                RTC_LOG(LoggingSeverity::WARNING) << "-----";

                webrtc::SdpParseError error;
                webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, adjustLocalDescription(adjustedSdp), &error);
                if (sessionDescription != nullptr) {
                    rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, adjustedSdp]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        if (!strong->_joinPayload) {
                            return;
                        }

                        auto sdp = parseJoinResponseIntoSdp(strong->_sessionId, strong->_mainStreamAudioSsrc, strong->_joinPayload.value(), true, strong->_allOtherSsrcs, strong->_activeOtherSsrcs);
                        strong->setOfferSdp(sdp, false, true, false);
                    }, [](webrtc::RTCError error) {
                    }));
                    strong->_peerConnection->SetLocalDescription(observer, sessionDescription);
                } else {
                    return;
                }
            });
        }));
        _peerConnection->CreateOffer(observer, options);
    }

    void setOfferSdp(std::string const &offerSdp, bool isInitialJoinAnswer, bool isAnswer, bool completeMissingSsrcSetup) {
        if (!isAnswer && _appliedRemoteRescription == offerSdp) {
            if (completeMissingSsrcSetup) {
                completeProcessingMissingSsrcs();
            }
            return;
        }
        _appliedRemoteRescription = offerSdp;

        RTC_LOG(LoggingSeverity::WARNING) << "----- setOfferSdp " << (isAnswer ? "answer" : "offer") << " -----";
        RTC_LOG(LoggingSeverity::WARNING) << offerSdp;
        RTC_LOG(LoggingSeverity::WARNING) << "-----";

        webrtc::SdpParseError error;
        webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(isAnswer ? "answer" : "offer", adjustLocalDescription(offerSdp), &error);
        if (!sessionDescription) {
            if (completeMissingSsrcSetup) {
                completeProcessingMissingSsrcs();
            }
            return;
        }

        if (!isAnswer) {
            _appliedOfferTimestamp = rtc::TimeMillis();
        }

        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, isInitialJoinAnswer, isAnswer, completeMissingSsrcSetup]() {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isInitialJoinAnswer, isAnswer, completeMissingSsrcSetup](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                if (!isAnswer) {
                    strong->emitAnswer(completeMissingSsrcSetup);
                } else {
                    if (isInitialJoinAnswer) {
                        strong->completedInitialSetup();
                    }

                    if (completeMissingSsrcSetup) {
                        strong->completeProcessingMissingSsrcs();
                    }
                }
            });
        }, [weak, completeMissingSsrcSetup](webrtc::RTCError error) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, completeMissingSsrcSetup](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                if (completeMissingSsrcSetup) {
                    strong->completeProcessingMissingSsrcs();
                }
            });
        }));

        _peerConnection->SetRemoteDescription(observer, sessionDescription);
    }

    void beginStatsTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->collectStats();
            });
        }, timeoutMs);
    }

    void beginLevelsTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            GroupLevelsUpdate levelsUpdate;
            levelsUpdate.updates.reserve(strong->_audioLevels.size() + 1);
            for (auto &it : strong->_audioLevels) {
                if (it.second.level > 0.001f) {
                    levelsUpdate.updates.push_back(GroupLevelUpdate{
                        it.first,
                        it.second,
                        });
                }
            }
            levelsUpdate.updates.push_back(GroupLevelUpdate{ 0, strong->_myAudioLevel });

            strong->_audioLevels.clear();
            strong->_audioLevelsUpdated(levelsUpdate);

            strong->beginLevelsTimer(50);
        }, timeoutMs);
    }

    void collectStats() {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        rtc::scoped_refptr<RTCStatsCollectorCallbackImpl> observer(new rtc::RefCountedObject<RTCStatsCollectorCallbackImpl>([weak](const rtc::scoped_refptr<const webrtc::RTCStatsReport> &stats) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, stats](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->reportStats(stats);
                strong->beginStatsTimer(100);
            });
        }));
        _peerConnection->GetStats(observer);
    }

    void reportStats(const rtc::scoped_refptr<const webrtc::RTCStatsReport> &stats) {
    }

    void onTrackAdded(rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
        if (transceiver->direction() == webrtc::RtpTransceiverDirection::kRecvOnly && transceiver->media_type() == cricket::MediaType::MEDIA_TYPE_AUDIO) {
            if (transceiver->mid()) {
                auto streamId = transceiver->mid().value();
                if (streamId.find("audio") != 0) {
                    return;
                }
                streamId.replace(0, 5, "");
                std::istringstream iss(streamId);
                uint32_t ssrc = 0;
                iss >> ssrc;

                auto remoteAudioTrack = static_cast<webrtc::AudioTrackInterface *>(transceiver->receiver()->track().get());
                if (_audioTrackSinks.find(ssrc) == _audioTrackSinks.end()) {
                    const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
                    std::shared_ptr<AudioTrackSinkInterfaceImpl> sink(new AudioTrackSinkInterfaceImpl([weak, ssrc](float level, bool hasSpeech) {
                        getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc, level, hasSpeech]() {
                            auto strong = weak.lock();
                            if (!strong) {
                                return;
                            }
                            auto current = strong->_audioLevels.find(ssrc);
                            if (current != strong->_audioLevels.end()) {
                                if (current->second.level < level) {
                                    strong->_audioLevels[ssrc] = GroupLevelValue{
                                        level,
                                        hasSpeech,
                                    };
                                }
                            } else {
                                strong->_audioLevels.emplace(
                                    ssrc,
                                    GroupLevelValue{
                                        level,
                                        hasSpeech,
                                    });
                            }
                        });
                    }));
                    _audioTrackSinks[ssrc] = sink;
                    remoteAudioTrack->AddSink(sink.get());
                }
            }
        }
    }

    void onTrackRemoved(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
    }

    void onMissingSsrc(uint32_t ssrc) {
        if (_processedMissingSsrcs.find(ssrc) == _processedMissingSsrcs.end()) {
            _processedMissingSsrcs.insert(ssrc);

            _missingSsrcQueue.insert(ssrc);
            if (!_isProcessingMissingSsrcs) {
                beginProcessingMissingSsrcs();
            }
        }
    }

    void beginProcessingMissingSsrcs() {
        if (_isProcessingMissingSsrcs) {
            return;
        }
        _isProcessingMissingSsrcs = true;
        auto timestamp = rtc::TimeMillis();
        if (timestamp > _missingSsrcsProcessedTimestamp + 200) {
            applyMissingSsrcs();
        } else {
            const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
            getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->applyMissingSsrcs();
            }, 200);
        }
    }

    void applyMissingSsrcs() {
        assert(_isProcessingMissingSsrcs);
        if (_missingSsrcQueue.size() == 0) {
            completeProcessingMissingSsrcs();
            return;
        }

        std::vector<uint32_t> addSsrcs;
        for (auto ssrc : _missingSsrcQueue) {
            addSsrcs.push_back(ssrc);
        }
        _missingSsrcQueue.clear();

        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        addSsrcsInternal(addSsrcs, true);
    }

    void completeProcessingMissingSsrcs() {
        assert(_isProcessingMissingSsrcs);
        _isProcessingMissingSsrcs = false;
        _missingSsrcsProcessedTimestamp = rtc::TimeMillis();

        if (_missingSsrcQueue.size() != 0) {
            beginProcessingMissingSsrcs();
        }
    }

    void completedInitialSetup() {
        //beginDebugSsrcTimer(1000);
    }

    uint32_t _nextTestSsrc = 100;

    void beginDebugSsrcTimer(int timeout) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            if (strong->_nextTestSsrc >= 100 + 50) {
                return;
            }

            strong->_nextTestSsrc++;
            strong->onMissingSsrc(strong->_nextTestSsrc);

            strong->beginDebugSsrcTimer(20);
        }, timeout);
    }

    void setIsMuted(bool isMuted) {
        if (!_localAudioTrackSender) {
            return;
        }
        if (_isMuted == isMuted) {
            return;
        }

        for (auto &it : _peerConnection->GetTransceivers()) {
            if (it->media_type() == cricket::MediaType::MEDIA_TYPE_AUDIO) {
                if (_localAudioTrackSender.get() == it->sender().get()) {
                    if (isMuted) {
                        /*if (it->direction() == webrtc::RtpTransceiverDirection::kSendRecv) {
                            it->SetDirection(webrtc::RtpTransceiverDirection::kRecvOnly);

                            applyLocalSdp();

                            break;
                        }*/
                    } else {
                        if (it->direction() == webrtc::RtpTransceiverDirection::kRecvOnly) {
                            it->SetDirection(webrtc::RtpTransceiverDirection::kSendRecv);

                            applyLocalSdp();

                            break;
                        }
                    }
                }

                break;
            }
        }

        _isMuted = isMuted;
        _localAudioTrack->set_enabled(!isMuted);

        RTC_LOG(LoggingSeverity::WARNING) << "setIsMuted: " << isMuted;
    }

    void emitAnswer(bool completeMissingSsrcSetup) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak, completeMissingSsrcSetup](std::string sdp, std::string type) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type, completeMissingSsrcSetup](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                RTC_LOG(LoggingSeverity::WARNING) << "----- setLocalDescription answer -----";
                RTC_LOG(LoggingSeverity::WARNING) << sdp;
                RTC_LOG(LoggingSeverity::WARNING) << "-----";

                webrtc::SdpParseError error;
                webrtc::SessionDescriptionInterface *sessionDescription = webrtc::CreateSessionDescription(type, adjustLocalDescription(sdp), &error);
                if (sessionDescription != nullptr) {
                    rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, sdp, completeMissingSsrcSetup]() {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        if (completeMissingSsrcSetup) {
                            strong->completeProcessingMissingSsrcs();
                        }
                    }, [weak, completeMissingSsrcSetup](webrtc::RTCError error) {
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }

                        if (completeMissingSsrcSetup) {
                            strong->completeProcessingMissingSsrcs();
                        }
                    }));
                    strong->_peerConnection->SetLocalDescription(observer, sessionDescription);
                } else {
                    if (completeMissingSsrcSetup) {
                        strong->completeProcessingMissingSsrcs();
                    }
                }
            });
        }));
        _peerConnection->CreateAnswer(observer, options);
    }

private:
    void withAudioDeviceModule(std::function<void(webrtc::AudioDeviceModule*)> callback) {
        _adm_thread->Invoke<void>(RTC_FROM_HERE, [&] {
            callback(_adm_use_withAudioDeviceModule.get());
        });
    }

    std::function<void(bool)> _networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> _audioLevelsUpdated;

    int32_t _myAudioLevelPeakCount = 0;
    float _myAudioLevelPeak = 0;
    GroupLevelValue _myAudioLevel;

    std::string _initialInputDeviceId;
    std::string _initialOutputDeviceId;

    uint32_t _sessionId = 6543245;
    uint32_t _mainStreamAudioSsrc = 0;
    uint32_t _fakeIncomingSsrc = 0;
    absl::optional<GroupJoinResponsePayload> _joinPayload;

    int64_t _appliedOfferTimestamp = 0;
    bool _isConnected = false;
    int _isConnectedUpdateValidTaskId = 0;

    bool _isMuted = true;

    std::vector<uint32_t> _allOtherSsrcs;
    std::set<uint32_t> _activeOtherSsrcs;
    std::set<uint32_t> _processedMissingSsrcs;

    int64_t _missingSsrcsProcessedTimestamp = 0;
    bool _isProcessingMissingSsrcs = false;
    std::set<uint32_t> _missingSsrcQueue;

    std::string _appliedRemoteRescription;

    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> _nativeFactory;
    std::unique_ptr<PeerConnectionObserverImpl> _observer;
    rtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;
    std::unique_ptr<AudioTrackSinkInterfaceImpl> _localAudioTrackSink;
    rtc::scoped_refptr<webrtc::AudioTrackInterface> _localAudioTrack;
    rtc::scoped_refptr<webrtc::RtpSenderInterface> _localAudioTrackSender;

    rtc::Thread *_adm_thread = nullptr;
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _adm_use_withAudioDeviceModule;

    std::map<uint32_t, std::shared_ptr<AudioTrackSinkInterfaceImpl>> _audioTrackSinks;
    std::map<uint32_t, GroupLevelValue> _audioLevels;
    
    std::shared_ptr<PlatformContext> _platformContext;
};

GroupInstanceImpl::GroupInstanceImpl(GroupInstanceDescriptor &&descriptor)
: _logSink(std::make_unique<LogSinkImpl>(descriptor.config.logPath)) {
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(true);
    if (_logSink) {
		rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
	}

	_manager.reset(new ThreadLocalObject<GroupInstanceManager>(getMediaThread(), [descriptor = std::move(descriptor)]() mutable {
		return new GroupInstanceManager(std::move(descriptor));
	}));
	_manager->perform(RTC_FROM_HERE, [](GroupInstanceManager *manager) {
		manager->start();
	});
}

GroupInstanceImpl::~GroupInstanceImpl() {
	if (_logSink) {
		rtc::LogMessage::RemoveLogToStream(_logSink.get());
	}
    _manager = nullptr;

    // Wait until _manager is destroyed, otherwise there is a race condition
    // in destruction of PeerConnection on media thread and network thread.
    getMediaThread()->Invoke<void>(RTC_FROM_HERE, [] {});
}

void GroupInstanceImpl::stop() {
    _manager->perform(RTC_FROM_HERE, [](GroupInstanceManager *manager) {
        manager->stop();
    });
}

void GroupInstanceImpl::emitJoinPayload(std::function<void(GroupJoinPayload)> completion) {
    _manager->perform(RTC_FROM_HERE, [completion](GroupInstanceManager *manager) {
        manager->emitJoinPayload(completion);
    });
}

void GroupInstanceImpl::setJoinResponsePayload(GroupJoinResponsePayload payload) {
    _manager->perform(RTC_FROM_HERE, [payload](GroupInstanceManager *manager) {
        manager->setJoinResponsePayload(payload);
    });
}

void GroupInstanceImpl::removeSsrcs(std::vector<uint32_t> ssrcs) {
    _manager->perform(RTC_FROM_HERE, [ssrcs](GroupInstanceManager *manager) {
        manager->removeSsrcs(ssrcs);
    });
}

void GroupInstanceImpl::setIsMuted(bool isMuted) {
    _manager->perform(RTC_FROM_HERE, [isMuted](GroupInstanceManager *manager) {
        manager->setIsMuted(isMuted);
    });
}

void GroupInstanceImpl::setAudioInputDevice(std::string id) {
    _manager->perform(RTC_FROM_HERE, [id](GroupInstanceManager *manager) {
        manager->setAudioInputDevice(id);
    });
}
void GroupInstanceImpl::setAudioOutputDevice(std::string id) {
    _manager->perform(RTC_FROM_HERE, [id](GroupInstanceManager *manager) {
        manager->setAudioOutputDevice(id);
    });
}

} // namespace tgcalls
