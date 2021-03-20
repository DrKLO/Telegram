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
#include "modules/audio_device/include/audio_device_factory.h"
#include "common_audio/include/audio_util.h"
#include "common_audio/vad/include/webrtc_vad.h"
#include "modules/audio_processing/agc2/vad_with_level.h"

#include "ThreadLocalObject.h"
#include "Manager.h"
#include "NetworkManager.h"
#include "VideoCaptureInterfaceImpl.h"
#include "platform/PlatformInterface.h"
#include "LogSinkImpl.h"
#include "StaticThreads.h"

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

static std::vector<uint32_t> splitSsrcList(std::string const &line) {
    std::vector<uint32_t> result;

    std::istringstream sdpStream(line);

    std::string s;
    while (std::getline(sdpStream, s, ' ')) {
        if (s.size() == 0) {
            continue;
        }

        std::istringstream iss(s);
        uint32_t ssrc = 0;
        iss >> ssrc;

        result.push_back(ssrc);
    }

    return result;
}

static std::vector<std::string> splitBundleMLines(std::string const &line) {
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

static absl::optional<GroupJoinPayloadVideoPayloadType> parsePayloadType(uint32_t id, std::string const &line) {
    std::string s;
    std::istringstream lineStream(line);
    std::string codec;
    uint32_t clockrate = 0;
    uint32_t channels = 0;
    for (int i = 0; std::getline(lineStream, s, '/'); i++) {
        if (s.size() == 0) {
            continue;
        }

        if (i == 0) {
            codec = s;
        } else if (i == 1) {
            std::istringstream iss(s);
            iss >> clockrate;
        } else if (i == 2) {
            std::istringstream iss(s);
            iss >> channels;
        }
    }
    if (codec.size() != 0) {
        GroupJoinPayloadVideoPayloadType payloadType;
        payloadType.id = id;
        payloadType.name = codec;
        payloadType.clockrate = clockrate;
        payloadType.channels = channels;
        return payloadType;
    } else {
        return absl::nullopt;
    }
}

static absl::optional<GroupJoinPayloadVideoPayloadFeedbackType> parseFeedbackType(std::string const &line) {
    std::istringstream lineStream(line);
    std::string s;

    std::string type;
    std::string subtype;
    for (int i = 0; std::getline(lineStream, s, ' '); i++) {
        if (s.size() == 0) {
            continue;
        }

        if (i == 0) {
            type = s;
        } else if (i == 1) {
            subtype = s;
        }
    }

    if (type.size() != 0) {
        GroupJoinPayloadVideoPayloadFeedbackType parsedType;
        parsedType.type = type;
        parsedType.subtype = subtype;
        return parsedType;
    } else {
        return absl::nullopt;
    }
}

static void parsePayloadParameter(std::string const &line, std::vector<std::pair<std::string, std::string>> &result) {
    std::istringstream lineStream(line);
    std::string s;

    std::string key;
    std::string value;
    for (int i = 0; std::getline(lineStream, s, '='); i++) {
        if (s.size() == 0) {
            continue;
        }

        if (i == 0) {
            key = s;
        } else if (i == 1) {
            value = s;
        }
    }
    if (key.size() != 0 && value.size() != 0) {
        result.push_back(std::make_pair(key, value));
    }
}

static std::vector<std::pair<std::string, std::string>> parsePayloadParameters(std::string const &line) {
    std::vector<std::pair<std::string, std::string>> result;

    std::istringstream lineStream(line);
    std::string s;

    while (std::getline(lineStream, s, ';')) {
        if (s.size() == 0) {
            continue;
        }

        parsePayloadParameter(s, result);
    }

    return result;
}

static absl::optional<GroupJoinPayload> parseSdpIntoJoinPayload(std::string const &sdp) {
    GroupJoinPayload result;

    auto lines = splitSdpLines(sdp);

    std::vector<std::string> audioLines;
    std::vector<std::string> videoLines;
    bool isAudioLine = false;
    bool isVideoLine = false;
    for (auto &line : lines) {
        if (line.find("m=audio") == 0) {
            isAudioLine = true;
            isVideoLine = false;
        } else if (line.find("m=video") == 0) {
            isAudioLine = false;
            isVideoLine = true;
        } else if (line.find("m=application") == 0) {
            isAudioLine = false;
            isVideoLine = true;
        }
        if (isAudioLine) {
            audioLines.push_back(line);
        } else if (isVideoLine) {
            videoLines.push_back(line);
        }
    }

    result.ssrc = 0;

    auto ufragLines = getLines(audioLines, "a=ice-ufrag:");
    if (ufragLines.size() != 1) {
        return absl::nullopt;
    }
    result.ufrag = ufragLines[0];

    auto pwdLines = getLines(audioLines, "a=ice-pwd:");
    if (pwdLines.size() != 1) {
        return absl::nullopt;
    }
    result.pwd = pwdLines[0];

    for (auto &line : getLines(audioLines, "a=fingerprint:")) {
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

    for (auto &line : getLines(videoLines, "a=rtpmap:")) {
        std::string s;
        std::istringstream lineStream(line);
        uint32_t id = 0;
        for (int i = 0; std::getline(lineStream, s, ' '); i++) {
            if (s.size() == 0) {
                continue;
            }

            if (i == 0) {
                std::istringstream iss(s);
                iss >> id;
            } else if (i == 1) {
                if (id != 0) {
                    auto payloadType = parsePayloadType(id, s);
                    if (payloadType.has_value()) {
                        std::ostringstream fbPrefixStream;
                        fbPrefixStream << "a=rtcp-fb:";
                        fbPrefixStream << id;
                        fbPrefixStream << " ";
                        for (auto &feedbackLine : getLines(videoLines, fbPrefixStream.str())) {
                            auto feedbackType = parseFeedbackType(feedbackLine);
                            if (feedbackType.has_value()) {
                                payloadType->feedbackTypes.push_back(feedbackType.value());
                            }
                        }

                        std::ostringstream parametersPrefixStream;
                        parametersPrefixStream << "a=fmtp:";
                        parametersPrefixStream << id;
                        parametersPrefixStream << " ";
                        for (auto &parametersLine : getLines(videoLines, parametersPrefixStream.str())) {
                            payloadType->parameters = parsePayloadParameters(parametersLine);
                        }

                        result.videoPayloadTypes.push_back(payloadType.value());
                    }
                }
            }
        }
    }

    for (auto &line : getLines(videoLines, "a=extmap:")) {
        std::string s;
        std::istringstream lineStream(line);
        uint32_t id = 0;
        for (int i = 0; std::getline(lineStream, s, ' '); i++) {
            if (s.size() == 0) {
                continue;
            }

            if (i == 0) {
                std::istringstream iss(s);
                iss >> id;
            } else if (i == 1) {
                if (id != 0) {
                    result.videoExtensionMap.push_back(std::make_pair(id, s));
                }
            }
        }
    }

    for (auto &line : getLines(videoLines, "a=ssrc-group:FID ")) {
        auto ssrcs = splitSsrcList(line);
        GroupJoinPayloadVideoSourceGroup group;
        group.semantics = "FID";
        group.ssrcs = ssrcs;
        result.videoSourceGroups.push_back(std::move(group));
    }
    for (auto &line : getLines(videoLines, "a=ssrc-group:SIM ")) {
        auto ssrcs = splitSsrcList(line);
        GroupJoinPayloadVideoSourceGroup group;
        group.semantics = "SIM";
        group.ssrcs = ssrcs;
        result.videoSourceGroups.push_back(std::move(group));
    }

    return result;
}

struct StreamSpec {
    bool isMain = false;
    bool isOutgoing = false;
    std::string mLine;
    uint32_t streamId = 0;
    uint32_t ssrc = 0;
    std::vector<GroupJoinPayloadVideoSourceGroup> videoSourceGroups;
    std::vector<GroupJoinPayloadVideoPayloadType> videoPayloadTypes;
    std::vector<std::pair<uint32_t, std::string>> videoExtensionMap;
    bool isRemoved = false;
    bool isData = false;
    bool isVideo = false;
};

static void appendSdp(std::vector<std::string> &lines, std::string const &line, int index = -1) {
    if (index >= 0) {
        lines.insert(lines.begin() + index, line);
    } else {
        lines.push_back(line);
    }
}

enum class SdpType {
    kSdpTypeJoinAnswer,
    kSdpTypeRemoteOffer,
    kSdpTypeLocalAnswer
};

static std::string createSdp(uint32_t sessionId, GroupJoinResponsePayload const &payload, SdpType type, std::vector<StreamSpec> const &bundleStreams) {
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
        bundleString << stream.mLine;
    }
    appendSdp(sdp, bundleString.str());

    appendSdp(sdp, "a=ice-lite");

    for (auto &stream : bundleStreams) {
        std::ostringstream streamMidString;
        streamMidString << "a=mid:" << stream.mLine;

        if (stream.isData) {
            appendSdp(sdp, "m=application 9 UDP/DTLS/SCTP webrtc-datachannel");
            appendSdp(sdp, "c=IN IP4 0.0.0.0");

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

            appendSdp(sdp, streamMidString.str());
            appendSdp(sdp, "a=sctp-port:5000");
            appendSdp(sdp, "a=max-message-size:262144");
        } else {
            std::ostringstream mLineString;
            if (stream.isVideo) {
                mLineString << "m=video ";
            } else {
                mLineString << "m=audio ";
            }
            if (stream.isMain) {
                mLineString << "1";
            } else {
                mLineString << "0";
            }
            if (stream.videoPayloadTypes.size() == 0) {
                mLineString << " RTP/AVPF 111 126";
            } else {
                mLineString << " RTP/AVPF";
                for (auto &it : stream.videoPayloadTypes) {
                    mLineString << " " << it.id;
                }
            }

            appendSdp(sdp, mLineString.str());

            if (stream.isMain) {
                appendSdp(sdp, "c=IN IP4 0.0.0.0");
            }

            appendSdp(sdp, streamMidString.str());

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

            if (stream.isMain) {
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

            if (!stream.isVideo) {
                appendSdp(sdp, "a=rtpmap:111 opus/48000/2");
                appendSdp(sdp, "a=rtpmap:126 telephone-event/8000");
                appendSdp(sdp, "a=fmtp:111 minptime=10; useinbandfec=1");
                appendSdp(sdp, "a=rtcp:1 IN IP4 0.0.0.0");
                appendSdp(sdp, "a=rtcp-mux");
                appendSdp(sdp, "a=rtcp-rsize");
                appendSdp(sdp, "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level");
                appendSdp(sdp, "a=extmap:2 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time");
                appendSdp(sdp, "a=extmap:3 http://www.ietf.org/id/draft-holmer-rmcat-transport-wide-cc-extensions-01");

                bool addSsrcs = false;
                if (stream.isRemoved) {
                    appendSdp(sdp, "a=inactive");
                } else if (type == SdpType::kSdpTypeJoinAnswer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=recvonly");
                    } else {
                        appendSdp(sdp, "a=sendonly");
                        appendSdp(sdp, "a=bundle-only");
                        addSsrcs = true;
                    }
                } else if (type == SdpType::kSdpTypeRemoteOffer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=recvonly");
                    } else {
                        appendSdp(sdp, "a=sendonly");
                        appendSdp(sdp, "a=bundle-only");
                        addSsrcs = true;
                    }
                } else if (type == SdpType::kSdpTypeLocalAnswer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=sendonly");
                        addSsrcs = true;
                    } else {
                        appendSdp(sdp, "a=recvonly");
                        appendSdp(sdp, "a=bundle-only");
                    }
                }

                if (addSsrcs) {
                    std::ostringstream cnameString;
                    cnameString << "a=ssrc:";
                    cnameString << stream.ssrc;
                    cnameString << " cname:stream";
                    cnameString << stream.streamId;
                    appendSdp(sdp, cnameString.str());

                    std::ostringstream msidString;
                    msidString << "a=ssrc:";
                    msidString << stream.ssrc;
                    msidString << " msid:stream";
                    msidString << stream.streamId;
                    msidString << " audio" << stream.streamId;
                    appendSdp(sdp, msidString.str());

                    std::ostringstream mslabelString;
                    mslabelString << "a=ssrc:";
                    mslabelString << stream.ssrc;
                    mslabelString << " mslabel:audio";
                    mslabelString << stream.streamId;
                    appendSdp(sdp, mslabelString.str());

                    std::ostringstream labelString;
                    labelString << "a=ssrc:";
                    labelString << stream.ssrc;
                    labelString << " label:audio";
                    labelString << stream.streamId;
                    appendSdp(sdp, labelString.str());
                }
            } else {
                appendSdp(sdp, "a=rtcp:1 IN IP4 0.0.0.0");
                appendSdp(sdp, "a=rtcp-mux");
                appendSdp(sdp, "a=rtcp-rsize");

                for (auto &it : stream.videoPayloadTypes) {
                    std::ostringstream rtpmapString;
                    rtpmapString << "a=rtpmap:";
                    rtpmapString << it.id;
                    rtpmapString << " ";
                    rtpmapString << it.name;
                    rtpmapString << "/";
                    rtpmapString << it.clockrate;
                    if (it.channels != 0) {
                        rtpmapString << "/";
                        rtpmapString << it.channels;
                    }
                    appendSdp(sdp, rtpmapString.str());

                    for (auto &feedbackType : it.feedbackTypes) {
                        std::ostringstream feedbackString;
                        feedbackString << "a=rtcp-fb:";
                        feedbackString << it.id;
                        feedbackString << " ";
                        feedbackString << feedbackType.type;
                        if (feedbackType.subtype.size() != 0) {
                            feedbackString << " ";
                            feedbackString << feedbackType.subtype;
                        }
                        appendSdp(sdp, feedbackString.str());
                    }

                    auto parameters = it.parameters;

                    if (it.name == "VP8") {
                        bool hasBitrate = false;
                        for (auto &param : parameters) {
                            if (param.first == "x-google-max-bitrate") {
                                hasBitrate = true;
                            }
                        }

                        if (!hasBitrate) {
                            parameters.push_back(std::make_pair("x-google-max-bitrate", "1200"));
                            //parameters.push_back(std::make_pair("x-google-start-bitrate", "300"));
                        }
                    }

                    if (parameters.size() != 0) {
                        std::ostringstream fmtpString;
                        fmtpString << "a=fmtp:";
                        fmtpString << it.id;
                        fmtpString << " ";

                        for (int i = 0; i < parameters.size(); i++) {
                            if (i != 0) {
                                fmtpString << ";";
                            }
                            fmtpString << parameters[i].first;
                            fmtpString << "=";
                            fmtpString << parameters[i].second;
                        }

                        appendSdp(sdp, fmtpString.str());
                    }
                }

                for (auto &it : stream.videoExtensionMap) {
                    std::ostringstream extString;
                    extString << "a=extmap:";
                    extString << it.first;
                    extString << " ";
                    extString << it.second;
                    appendSdp(sdp, extString.str());
                }

                bool addSsrcs = false;
                if (stream.isRemoved) {
                    appendSdp(sdp, "a=inactive");
                } else if (type == SdpType::kSdpTypeJoinAnswer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=recvonly");
                        appendSdp(sdp, "a=bundle-only");
                    } else {
                        appendSdp(sdp, "a=sendonly");
                        appendSdp(sdp, "a=bundle-only");
                        addSsrcs = true;
                    }
                } else if (type == SdpType::kSdpTypeRemoteOffer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=recvonly");
                        appendSdp(sdp, "a=bundle-only");
                    } else {
                        appendSdp(sdp, "a=sendonly");
                        appendSdp(sdp, "a=bundle-only");
                        addSsrcs = true;
                    }
                } else if (type == SdpType::kSdpTypeLocalAnswer) {
                    if (stream.isOutgoing) {
                        appendSdp(sdp, "a=sendonly");
                        appendSdp(sdp, "a=bundle-only");
                        addSsrcs = true;
                    } else {
                        appendSdp(sdp, "a=recvonly");
                        appendSdp(sdp, "a=bundle-only");
                    }
                }

                if (addSsrcs) {
                    std::vector<uint32_t> ssrcs;
                    for (auto &group : stream.videoSourceGroups) {
                        std::ostringstream groupString;
                        groupString << "a=ssrc-group:";
                        groupString << group.semantics;

                        for (auto ssrc : group.ssrcs) {
                            groupString << " " << ssrc;

                            if (std::find(ssrcs.begin(), ssrcs.end(), ssrc) == ssrcs.end()) {
                                ssrcs.push_back(ssrc);
                            }
                        }

                        appendSdp(sdp, groupString.str());
                    }

                    for (auto ssrc : ssrcs) {
                        std::ostringstream cnameString;
                        cnameString << "a=ssrc:";
                        cnameString << ssrc;
                        cnameString << " cname:stream";
                        cnameString << stream.streamId;
                        appendSdp(sdp, cnameString.str());

                        std::ostringstream msidString;
                        msidString << "a=ssrc:";
                        msidString << ssrc;
                        msidString << " msid:stream";
                        msidString << stream.streamId;
                        msidString << " video" << stream.streamId;
                        appendSdp(sdp, msidString.str());

                        std::ostringstream mslabelString;
                        mslabelString << "a=ssrc:";
                        mslabelString << ssrc;
                        mslabelString << " mslabel:video";
                        mslabelString << stream.streamId;
                        appendSdp(sdp, mslabelString.str());

                        std::ostringstream labelString;
                        labelString << "a=ssrc:";
                        labelString << ssrc;
                        labelString << " label:video";
                        labelString << stream.streamId;
                        appendSdp(sdp, labelString.str());
                    }
                }
            }
        }
    }

    std::ostringstream result;
    for (auto &line : sdp) {
        result << line << "\n";
    }

    return result.str();
}

static std::string parseJoinResponseIntoSdp(uint32_t sessionId, GroupJoinPayload const &joinPayload, GroupJoinResponsePayload const &payload, SdpType type, std::vector<GroupParticipantDescription> const &allOtherParticipants, absl::optional<std::string> localVideoMid, absl::optional<std::string> dataChannelMid, std::vector<StreamSpec> &bundleStreamsState) {

    std::vector<StreamSpec> bundleStreams;

    StreamSpec mainStream;
    mainStream.mLine = "0";
    mainStream.isMain = true;
    mainStream.isOutgoing = true;
    mainStream.streamId = 0;
    mainStream.ssrc = joinPayload.ssrc;
    mainStream.isRemoved = false;
    mainStream.isVideo = false;
    bundleStreams.push_back(mainStream);

    if (dataChannelMid.has_value() && dataChannelMid.value() == "1") {
        StreamSpec dataStream;
        dataStream.mLine = dataChannelMid.value();
        dataStream.isMain = false;
        dataStream.isOutgoing = true;
        dataStream.streamId = 0;
        dataStream.ssrc = 0;
        dataStream.isRemoved = false;
        dataStream.isData = true;
        dataStream.isVideo = false;
        bundleStreams.push_back(dataStream);
    }

    if (localVideoMid.has_value()) {
        if (joinPayload.videoSourceGroups.size() != 0) {
            StreamSpec mainVideoStream;
            mainVideoStream.mLine = localVideoMid.value();
            mainVideoStream.isMain = false;
            mainVideoStream.isOutgoing = true;
            mainVideoStream.isVideo = true;
            mainVideoStream.streamId = joinPayload.videoSourceGroups[0].ssrcs[0];
            mainVideoStream.ssrc = joinPayload.videoSourceGroups[0].ssrcs[0];
            mainVideoStream.videoSourceGroups = joinPayload.videoSourceGroups;
            mainVideoStream.videoPayloadTypes = joinPayload.videoPayloadTypes;
            mainVideoStream.videoExtensionMap = joinPayload.videoExtensionMap;

            mainVideoStream.isRemoved = joinPayload.videoSourceGroups.size() == 0;
            bundleStreams.push_back(mainVideoStream);
        }
    }

    if (dataChannelMid.has_value() && dataChannelMid.value() == "2") {
        StreamSpec dataStream;
        dataStream.mLine = dataChannelMid.value();
        dataStream.isMain = false;
        dataStream.isOutgoing = true;
        dataStream.streamId = 0;
        dataStream.ssrc = 0;
        dataStream.isRemoved = false;
        dataStream.isData = true;
        dataStream.isVideo = false;
        bundleStreams.push_back(dataStream);
    }

    for (auto &participant : allOtherParticipants) {
        StreamSpec audioStream;
        audioStream.isMain = false;

        std::ostringstream audioMLine;
        audioMLine << "audio" << participant.audioSsrc;
        audioStream.mLine = audioMLine.str();
        audioStream.ssrc = participant.audioSsrc;
        audioStream.isRemoved = participant.isRemoved;
        audioStream.streamId = participant.audioSsrc;
        bundleStreams.push_back(audioStream);

        if (participant.videoPayloadTypes.size() != 0 && participant.videoSourceGroups.size() != 0 ) {
            StreamSpec videoStream;
            videoStream.isMain = false;

            std::ostringstream videoMLine;
            videoMLine << "video" << participant.audioSsrc;
            videoStream.mLine = videoMLine.str();
            videoStream.isVideo = true;
            videoStream.ssrc = participant.videoSourceGroups[0].ssrcs[0];
            videoStream.isRemoved = participant.isRemoved;
            videoStream.streamId = participant.audioSsrc;
            videoStream.videoSourceGroups = participant.videoSourceGroups;
            videoStream.videoExtensionMap = participant.videoExtensionMap;
            videoStream.videoPayloadTypes = participant.videoPayloadTypes;

            bundleStreams.push_back(videoStream);
        }
    }

    std::vector<StreamSpec> orderedStreams;
    for (auto const &oldStream : bundleStreamsState) {
        bool found = false;
        for (int i = 0; i < (int)bundleStreams.size(); i++) {
            if (bundleStreams[i].mLine == oldStream.mLine) {
                found = true;
                orderedStreams.push_back(bundleStreams[i]);
                bundleStreams.erase(bundleStreams.begin() + i);
                break;
            }
        }
        if (!found) {
            StreamSpec copyStream = oldStream;
            copyStream.isRemoved = true;
            orderedStreams.push_back(copyStream);
        }
    }
    for (const auto &it : bundleStreams) {
        orderedStreams.push_back(it);
    }

    bundleStreamsState = orderedStreams;

    return createSdp(sessionId, payload, type, orderedStreams);
}

VideoCaptureInterfaceObject *GetVideoCaptureAssumingSameThread(VideoCaptureInterface *videoCapture) {
    return videoCapture
        ? static_cast<VideoCaptureInterfaceImpl*>(videoCapture)->object()->getSyncAssumingSameThread()
        : nullptr;
}

class ErrorParsingLogSink final : public rtc::LogSink {
public:
    ErrorParsingLogSink(std::function<void(uint32_t)> onMissingSsrc) :
    _onMissingSsrc(onMissingSsrc) {

    }

    void OnLogMessage(const std::string &msg, rtc::LoggingSeverity severity, const char *tag) override {
        handleMessage(msg);
    }

    void OnLogMessage(const std::string &message, rtc::LoggingSeverity severity) override {
        handleMessage(message);
    }

    void OnLogMessage(const std::string &message) override {
        handleMessage(message);
    }

private:
    void handleMessage(const std::string &message) {
        const std::string pattern = "Failed to demux RTP packet:";
        const std::string ssrcPattern = "SSRC=";
        auto index = message.find(pattern);
        if (index != std::string::npos) {
            index = message.find(ssrcPattern);
            if (index != std::string::npos) {
                std::string string = message;
                string.erase(0, index + ssrcPattern.size());

                std::istringstream stream(string);
                uint32_t ssrc = 0;
                stream >> ssrc;
                if (ssrc != 0) {
                    _onMissingSsrc(ssrc);
                }
            }
            return;
        }

        const std::string pattern2 = "receive_rtp_config_ lookup failed for ssrc ";
        index = message.find(pattern2);
        if (index != std::string::npos) {
            std::string string = message;
            string.erase(0, index + pattern2.size());

            std::istringstream stream(string);
            uint32_t ssrc = 0;
            stream >> ssrc;
            if (ssrc != 0) {
                _onMissingSsrc(ssrc);
            }

            return;
        }
    }

private:
    std::function<void(uint32_t)> _onMissingSsrc;

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
        _onTrackAdded(transceiver);
    }

    virtual void OnRemoveTrack(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) override {
        _onTrackRemoved(receiver);
    }

    virtual void OnInterestingUsage(int usage_pattern) override {
    }
};

class DataChannelObserverImpl : public webrtc::DataChannelObserver {
public:
    DataChannelObserverImpl(std::function<void()> stateChanged) :
    _stateChanged(stateChanged) {
    }

    virtual void OnStateChange() override {
        RTC_LOG(LS_INFO) << "DataChannel state changed";
        _stateChanged();
    }

    virtual void OnMessage(const webrtc::DataBuffer &buffer) override {
        RTC_LOG(LS_INFO) << "DataChannel message received: " << std::string((const char *)buffer.data.data(), buffer.data.size());
    }

    virtual void OnBufferedAmountChange(uint64_t sent_data_size) override {
    }

private:
    std::function<void()> _stateChanged;
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

std::string adjustLocalDescription(const std::string &sdp) {
    return sdp;
}

class CustomVideoSinkInterfaceProxyImpl : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
public:
    CustomVideoSinkInterfaceProxyImpl() {
    }

    virtual ~CustomVideoSinkInterfaceProxyImpl() {
    }

    virtual void OnFrame(const webrtc::VideoFrame& frame) override {
        //_lastFrame = frame;
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnFrame(frame);
            }
        }
    }

    virtual void OnDiscardedFrame() override {
        for (int i = (int)(_sinks.size()) - 1; i >= 0; i--) {
            auto strong = _sinks[i].lock();
            if (!strong) {
                _sinks.erase(_sinks.begin() + i);
            } else {
                strong->OnDiscardedFrame();
            }
        }
    }

    void addSink(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> impl) {
        _sinks.push_back(impl);
        if (_lastFrame) {
            auto strong = impl.lock();
            if (strong) {
                strong->OnFrame(_lastFrame.value());
            }
        }
    }

private:
    std::vector<std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>> _sinks;
    absl::optional<webrtc::VideoFrame> _lastFrame;
};

} // namespace



class GroupInstanceManager : public std::enable_shared_from_this<GroupInstanceManager> {
public:
	GroupInstanceManager(GroupInstanceDescriptor &&descriptor) :
    _networkStateUpdated(descriptor.networkStateUpdated),
    _audioLevelsUpdated(descriptor.audioLevelsUpdated),
    _incomingVideoSourcesUpdated(descriptor.incomingVideoSourcesUpdated),
    _participantDescriptionsRequired(descriptor.participantDescriptionsRequired),
    _initialInputDeviceId(descriptor.initialInputDeviceId),
    _initialOutputDeviceId(descriptor.initialOutputDeviceId),
    _createAudioDeviceModule(descriptor.createAudioDeviceModule),
    _videoCapture(descriptor.videoCapture),
    _platformContext(descriptor.platformContext) {
		auto generator = std::mt19937(std::random_device()());
		auto distribution = std::uniform_int_distribution<uint32_t>();
		do {
            _mainStreamAudioSsrc = distribution(generator);
		} while (!_mainStreamAudioSsrc);
	}

	~GroupInstanceManager() {
        assert(StaticThreads::getMediaThread()->IsCurrent());

        destroyAudioDeviceModule();
        if (_peerConnection) {
            _peerConnection->Close();
        }

        if (_errorParsingLogSink) {
            rtc::LogMessage::RemoveLogToStream(_errorParsingLogSink.get());
        }
	}

    void generateAndInsertFakeIncomingSsrc() {
        // At least on Windows recording can't be started without playout.
        // We keep a fake incoming stream, so that playout is always started.
        /*auto generator = std::mt19937(std::random_device()());
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
        _allOtherSsrcs.emplace_back(_fakeIncomingSsrc);*/
    }

    bool createAudioDeviceModule(
            const webrtc::PeerConnectionFactoryDependencies &dependencies) {
        _adm_thread = dependencies.worker_thread;
        if (!_adm_thread) {
            return false;
        }
        _adm_thread->Invoke<void>(RTC_FROM_HERE, [&] {
            const auto create = [&](webrtc::AudioDeviceModule::AudioLayer layer) {
                return webrtc::AudioDeviceModule::Create(
                    layer,
                    dependencies.task_queue_factory.get());
            };
			const auto finalize = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
				_adm_use_withAudioDeviceModule = new rtc::RefCountedObject<WrappedAudioDeviceModule>(result);
			};
            const auto check = [&](const rtc::scoped_refptr<webrtc::AudioDeviceModule> &result) {
                if (!result || result->Init() != 0) {
                    return false;
                }
                finalize(result);
                return true;
            };
            if (_createAudioDeviceModule
                && check(_createAudioDeviceModule(dependencies.task_queue_factory.get()))) {
                return;
            } else if (check(create(webrtc::AudioDeviceModule::kPlatformDefaultAudio))) {
                return;
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

        _errorParsingLogSink.reset(new ErrorParsingLogSink([weak](uint32_t ssrc) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                std::vector<uint32_t> ssrcs;
                ssrcs.push_back(ssrc);
                strong->_participantDescriptionsRequired(ssrcs);
            });
        }));
        rtc::LogMessage::AddLogToStream(_errorParsingLogSink.get(), rtc::LS_WARNING);

        webrtc::field_trial::InitFieldTrialsFromString(
            //"WebRTC-Audio-SendSideBwe/Enabled/"
            "WebRTC-Audio-Allocation/min:32kbps,max:32kbps/"
            "WebRTC-Audio-OpusMinPacketLossRate/Enabled-1/"
            //"WebRTC-FlexFEC-03/Enabled/"
            //"WebRTC-FlexFEC-03-Advertised/Enabled/"
            "WebRTC-PcFactoryDefaultBitrates/min:32kbps,start:32kbps,max:32kbps/"
            "WebRTC-Video-DiscardPacketsWithUnknownSsrc/Enabled/"
            "WebRTC-Video-BufferPacketsWithUnknownSsrc/Enabled/"
        );

        PlatformInterface::SharedInstance()->configurePlatformAudio();

        webrtc::PeerConnectionFactoryDependencies dependencies;
        dependencies.network_thread = StaticThreads::getNetworkThread();
        dependencies.worker_thread = StaticThreads::getWorkerThread();
        dependencies.signaling_thread = StaticThreads::getMediaThread();
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

            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, peak, peakCount, vadStatus](){
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

        /*mediaDeps.onUnknownAudioSsrc = [weak](uint32_t ssrc) {
            getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }
                strong->onMissingSsrc(ssrc);
            });
        };*/

        dependencies.media_engine = cricket::CreateMediaEngine(std::move(mediaDeps));
        dependencies.call_factory = webrtc::CreateCallFactory();
        dependencies.event_log_factory =
            std::make_unique<webrtc::RtcEventLogFactory>(dependencies.task_queue_factory.get());
        dependencies.network_controller_factory = nullptr;

        _nativeFactory = webrtc::CreateModularPeerConnectionFactory(std::move(dependencies));

        webrtc::PeerConnectionFactoryInterface::Options peerConnectionOptions;
        peerConnectionOptions.disable_encryption = true;
        _nativeFactory->SetOptions(peerConnectionOptions);

        webrtc::PeerConnectionInterface::RTCConfiguration config;
        config.sdp_semantics = webrtc::SdpSemantics::kUnifiedPlan;
        //config.continual_gathering_policy = webrtc::PeerConnectionInterface::ContinualGatheringPolicy::GATHER_CONTINUALLY;
        config.audio_jitter_buffer_fast_accelerate = true;
        config.prioritize_most_likely_ice_candidate_pairs = true;
        config.presume_writable_when_fully_relayed = true;
        //config.audio_jitter_buffer_enable_rtx_handling = true;
        config.enable_rtp_data_channel = true;
        config.enable_dtls_srtp = false;

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
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isConnected](){
                    auto strong = weak.lock();
                    if (strong) {
                        strong->updateIsConnected(isConnected);
                    }
                });
            },
            [weak](rtc::scoped_refptr<webrtc::RtpTransceiverInterface> transceiver) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, transceiver](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onTrackAdded(transceiver);
                });
            },
            [weak](rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, receiver](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->onTrackRemoved(receiver);
                });
            },
            [weak](uint32_t ssrc) {
                StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc](){
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
        auto addedAudioTrack = _peerConnection->AddTrack(_localAudioTrack, streamIds);

        if (addedAudioTrack.ok()) {
            _localAudioTrackSender = addedAudioTrack.value();
            for (auto &it : _peerConnection->GetTransceivers()) {
                if (it->media_type() == cricket::MediaType::MEDIA_TYPE_AUDIO) {
                    if (_localAudioTrackSender.get() == it->sender().get()) {
                        const auto error = it->SetDirectionWithError(webrtc::RtpTransceiverDirection::kRecvOnly);
                        (void)error;
                    }

                    break;
                }
            }
        }

        if (_videoCapture && false) {
            webrtc::DataChannelInit dataChannelConfig;
            _localDataChannel = _peerConnection->CreateDataChannel("1", &dataChannelConfig);

            if (_localDataChannel) {
                _localDataChannelMid = "1";

                _localDataChannelObserver.reset(new DataChannelObserverImpl([weak]() {
                    StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak](){
                        auto strong = weak.lock();
                        if (!strong) {
                            return;
                        }
                        bool isOpen = strong->_localDataChannel->state() == webrtc::DataChannelInterface::DataState::kOpen;
                        if (strong->_localDataChannelIsOpen != isOpen) {
                            RTC_LOG(LS_INFO) << "DataChannel isOpen: " << isOpen;
                            strong->_localDataChannelIsOpen = isOpen;
                            if (isOpen) {
                                strong->updateRemoteVideoConstaints();
                            }
                        }
                    });
                }));
                _localDataChannel->RegisterObserver(_localDataChannelObserver.get());
            }
        }

        updateVideoTrack(false, [](auto result) {});

        setAudioInputDevice(_initialInputDeviceId);
        setAudioOutputDevice(_initialOutputDeviceId);

        // At least on Windows recording doesn't work without started playout.
        withAudioDeviceModule([weak](webrtc::AudioDeviceModule *adm) {
#ifdef WEBRTC_WIN
            // At least on Windows starting/stopping playout while recording
            // is active leads to errors in recording and assertion violation.
			adm->EnableBuiltInAEC(false);
#endif // WEBRTC_WIN

            if (adm->InitPlayout() == 0) {
                adm->StartPlayout();
            } else {
                StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak](){
                    auto strong = weak.lock();
                    if (!strong) {
                        return;
                    }
                    strong->withAudioDeviceModule([](webrtc::AudioDeviceModule *adm) {
                        if (adm->InitPlayout() == 0) {
                            adm->StartPlayout();
                        }
                    });
                }, 2000);
            }
        });

        beginLevelsTimer(50);
        //beginTestQualityTimer(2000);
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

    void addIncomingVideoOutput(uint32_t ssrc, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
        auto current = _remoteVideoTrackSinks.find(ssrc);
        if (current != _remoteVideoTrackSinks.end()) {
            current->second->addSink(sink);
        } else {
            std::unique_ptr<CustomVideoSinkInterfaceProxyImpl> sinkProxy(new CustomVideoSinkInterfaceProxyImpl());
            sinkProxy->addSink(sink);
            _remoteVideoTrackSinks[ssrc] = std::move(sinkProxy);
        }
    }

    void setVolume(uint32_t ssrc, double volume) {
        auto current = _audioTrackVolumes.find(ssrc);
        bool updated = false;
        if (current != _audioTrackVolumes.end()) {
            if (abs(current->second - volume) > 0.001) {
                updated = true;
            }
        } else {
            if (volume < 1.0 - 0.001) {
                updated = true;
            }
        }
        if (updated) {
            _audioTrackVolumes[ssrc] = volume;
            auto track = _audioTracks.find(ssrc);
            if (track != _audioTracks.end()) {
                track->second->GetSource()->SetVolume(volume);
            }
        }
    }

    void setFullSizeVideoSsrc(uint32_t ssrc) {
        if (_currentFullSizeVideoSsrc == ssrc) {
            return;
        }
        bool update = false;
        if (_currentFullSizeVideoSsrc != 0) {
            if (setVideoConstraint(_currentFullSizeVideoSsrc, false, false)) {
                update = true;
            }
        }
        _currentFullSizeVideoSsrc = ssrc;
        if (_currentFullSizeVideoSsrc != 0) {
            if (setVideoConstraint(_currentFullSizeVideoSsrc, true, false)) {
                update = true;
            }
        }
        if (update) {
            updateRemoteVideoConstaints();
        }
    }

    void updateIsConnected(bool isConnected) {
        _isConnected = isConnected;

        auto timestamp = rtc::TimeMillis();

        _isConnectedUpdateValidTaskId++;

        if (!isConnected && _appliedOfferTimestamp > timestamp - 1000) {
            auto taskId = _isConnectedUpdateValidTaskId;
            const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
            StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak, taskId]() {
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

    std::string adjustLocalSdp(std::string const &sdp) {
        auto lines = splitSdpLines(sdp);
        std::vector<std::string> resultSdp;

        std::ostringstream generatedSsrcStringStream;
        generatedSsrcStringStream << _mainStreamAudioSsrc;
        auto generatedSsrcString = generatedSsrcStringStream.str();

        auto bundleLines = getLines(lines, "a=group:BUNDLE ");
        std::vector<std::string> bundleMLines;
        if (bundleLines.size() != 0) {
            bundleMLines = splitBundleMLines(bundleLines[0]);
        }

        bool hasVideo = false;
        std::string currentMid;
        int insertVideoLinesAtIndex = 0;
        for (auto &line : lines) {
            auto adjustedLine = line;

            if (adjustedLine.find("a=group:BUNDLE ") == 0) {
                std::ostringstream bundleString;
                bundleString << "a=group:BUNDLE";
                for (auto &mLine : bundleMLines) {
                    bundleString << " " << mLine;
                }
                adjustedLine = bundleString.str();
            }

            if (adjustedLine.find("m=") == 0) {
                currentMid = "";
            }
            if (adjustedLine.find("a=mid:") == 0) {
                currentMid = adjustedLine;
                currentMid.replace(0, std::string("a=mid:").size(), "");
            }

            if (adjustedLine.find("m=application") == 0) {
                insertVideoLinesAtIndex = (int)resultSdp.size();
            }

            if (currentMid == "0") {
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
            } else if (currentMid == "1") {
                if (adjustedLine.find("a=ssrc:") == 0 || adjustedLine.find("a=ssrc-group:") == 0) {
                    hasVideo = true;
                    adjustedLine.clear();
                }
            }

            if (adjustedLine.find("a=candidate") == 0) {
                adjustedLine.clear();
            }

            if (adjustedLine.size() != 0) {
                appendSdp(resultSdp, adjustedLine);
                if (currentMid == "1") {
                    insertVideoLinesAtIndex = (int)resultSdp.size();
                }
            }
        }

        if (hasVideo) {
            std::vector<GroupJoinPayloadVideoSourceGroup> videoSourceGroups;

            int ssrcDistance = 1;

            GroupJoinPayloadVideoSourceGroup sim;
            sim.semantics = "SIM";
            sim.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 0);
            sim.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 2);
            sim.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 4);
            videoSourceGroups.push_back(sim);

            GroupJoinPayloadVideoSourceGroup fid0;
            fid0.semantics = "FID";
            fid0.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 0);
            fid0.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 1);
            videoSourceGroups.push_back(fid0);

            GroupJoinPayloadVideoSourceGroup fid1;
            fid1.semantics = "FID";
            fid1.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 2);
            fid1.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 3);
            videoSourceGroups.push_back(fid1);

            GroupJoinPayloadVideoSourceGroup fid2;
            fid2.semantics = "FID";
            fid2.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 4);
            fid2.ssrcs.push_back(_mainStreamAudioSsrc + ssrcDistance + 5);
            videoSourceGroups.push_back(fid2);

            std::string streamId = "video0";

            std::vector<uint32_t> ssrcs;
            for (auto &group : videoSourceGroups) {
                std::ostringstream groupString;
                groupString << "a=ssrc-group:";
                groupString << group.semantics;

                for (auto ssrc : group.ssrcs) {
                    groupString << " " << ssrc;

                    if (std::find(ssrcs.begin(), ssrcs.end(), ssrc) == ssrcs.end()) {
                        ssrcs.push_back(ssrc);
                    }
                }

                appendSdp(resultSdp, groupString.str(), insertVideoLinesAtIndex);
                insertVideoLinesAtIndex++;
            }

            for (auto ssrc : ssrcs) {
                std::ostringstream cnameString;
                cnameString << "a=ssrc:";
                cnameString << ssrc;
                cnameString << " cname:stream";
                cnameString << streamId;
                appendSdp(resultSdp, cnameString.str(), insertVideoLinesAtIndex);
                insertVideoLinesAtIndex++;

                std::ostringstream msidString;
                msidString << "a=ssrc:";
                msidString << ssrc;
                msidString << " msid:stream";
                msidString << streamId;
                msidString << " video" << streamId;
                appendSdp(resultSdp, msidString.str(), insertVideoLinesAtIndex);
                insertVideoLinesAtIndex++;

                std::ostringstream mslabelString;
                mslabelString << "a=ssrc:";
                mslabelString << ssrc;
                mslabelString << " mslabel:video";
                mslabelString << streamId;
                appendSdp(resultSdp, mslabelString.str(), insertVideoLinesAtIndex);
                insertVideoLinesAtIndex++;

                std::ostringstream labelString;
                labelString << "a=ssrc:";
                labelString << ssrc;
                labelString << " label:video";
                labelString << streamId;
                appendSdp(resultSdp, labelString.str(), insertVideoLinesAtIndex);
                insertVideoLinesAtIndex++;
            }
        }

        std::ostringstream result;
        for (auto &line : resultSdp) {
            result << line << "\n";
        }

        return result.str();
    }

    void emitJoinPayload(std::function<void(GroupJoinPayload)> completion) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak, completion](std::string sdp, std::string type) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type, completion](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto adjustedSdp = strong->adjustLocalSdp(sdp);

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

                        if (strong->_localVideoTrackTransceiver) {
                            strong->_localVideoMid = strong->_localVideoTrackTransceiver->mid();
                            if (strong->_localDataChannel) {
                                if (strong->_localVideoMid && strong->_localVideoMid.value() == "1") {
                                    strong->_localDataChannelMid = "2";
                                } else {
                                    strong->_localDataChannelMid = "1";
                                }
                            }
                        } else {
                            strong->_localVideoMid.reset();
                        }

                        auto payload = parseSdpIntoJoinPayload(adjustedSdp);
                        if (payload) {
                            payload->ssrc = strong->_mainStreamAudioSsrc;
                            strong->_joinPayload = payload;
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

    void setJoinResponsePayload(GroupJoinResponsePayload payload, std::vector<tgcalls::GroupParticipantDescription> &&participants) {
        if (!_joinPayload) {
            return;
        }
        _joinResponsePayload = payload;

        auto sdp = parseJoinResponseIntoSdp(_sessionId, _joinPayload.value(), payload, SdpType::kSdpTypeJoinAnswer, _allOtherParticipants, _localVideoMid, _localDataChannelMid, _bundleStreamsState);
        setOfferSdp(sdp, true, true, false);

        addParticipantsInternal(std::move(participants), false);
    }

    void removeSsrcs(std::vector<uint32_t> ssrcs) {
        if (!_joinPayload) {
            return;
        }
        if (!_joinResponsePayload) {
            return;
        }

        bool updated = false;
        for (auto ssrc : ssrcs) {
            for (auto &participant : _allOtherParticipants) {
                if (participant.audioSsrc == ssrc) {
                    if (!participant.isRemoved) {
                        participant.isRemoved = true;
                        updated = true;
                    }
                }
            }
        }

        if (updated) {
            auto sdp = parseJoinResponseIntoSdp(_sessionId, _joinPayload.value(), _joinResponsePayload.value(), SdpType::kSdpTypeRemoteOffer, _allOtherParticipants, _localVideoMid, _localDataChannelMid, _bundleStreamsState);
            setOfferSdp(sdp, false, false, false);
        }
    }

    void addParticipants(std::vector<GroupParticipantDescription> &&participants) {
        addParticipantsInternal(std::move(participants), false);
    }

    void addParticipantsInternal(std::vector<GroupParticipantDescription> const &participants, bool completeMissingSsrcSetup) {
        if (!_joinPayload || !_joinResponsePayload) {
            if (completeMissingSsrcSetup) {
                completeProcessingMissingSsrcs();
            }
            return;
        }

        std::vector<uint32_t> addedSsrcs;

        for (auto &participant : participants) {
            bool found = false;
            for (auto &other : _allOtherParticipants) {
                if (other.audioSsrc == participant.audioSsrc) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                addedSsrcs.push_back(participant.audioSsrc);
                _allOtherParticipants.push_back(participant);
                //_activeOtherSsrcs.insert(participant.audioSsrc);
            }
        }

        auto sdp = parseJoinResponseIntoSdp(_sessionId, _joinPayload.value(), _joinResponsePayload.value(), SdpType::kSdpTypeRemoteOffer, _allOtherParticipants, _localVideoMid, _localDataChannelMid, _bundleStreamsState);
        setOfferSdp(sdp, false, false, completeMissingSsrcSetup);

        bool updated = false;
        for (auto &ssrc : addedSsrcs) {
            /*if (setVideoConstraint(ssrc, false, false)) {
                updated = true;
            }*/
        }
        if (updated) {
            updateRemoteVideoConstaints();
        }
    }

    void applyLocalSdp() {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak](std::string sdp, std::string type) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type](){
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                auto adjustedSdp = strong->adjustLocalSdp(sdp);

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
                        if (!strong->_joinResponsePayload) {
                            return;
                        }

                        if (strong->_localVideoTrackTransceiver) {
                            strong->_localVideoMid = strong->_localVideoTrackTransceiver->mid();
                            if (strong->_localDataChannel) {
                                if (strong->_localVideoMid && strong->_localVideoMid.value() == "1") {
                                    strong->_localDataChannelMid = "2";
                                } else {
                                    strong->_localDataChannelMid = "1";
                                }
                            }
                        } else {
                            strong->_localVideoMid.reset();
                        }

                        auto sdp = parseJoinResponseIntoSdp(strong->_sessionId, strong->_joinPayload.value(), strong->_joinResponsePayload.value(), SdpType::kSdpTypeJoinAnswer, strong->_allOtherParticipants, strong->_localVideoMid, strong->_localDataChannelMid, strong->_bundleStreamsState);
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
        if (!isAnswer && _appliedRemoteDescription == offerSdp) {
            if (completeMissingSsrcSetup) {
                completeProcessingMissingSsrcs();
            }
            return;
        }

        if (_appliedRemoteDescription.size() != 0) {
            _appliedOfferTimestamp = rtc::TimeMillis();
        }

        _appliedRemoteDescription = offerSdp;

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

        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        rtc::scoped_refptr<SetSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<SetSessionDescriptionObserverImpl>([weak, isInitialJoinAnswer, isAnswer, completeMissingSsrcSetup]() {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, isInitialJoinAnswer, isAnswer, completeMissingSsrcSetup](){
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
            RTC_LOG(LoggingSeverity::LS_ERROR) << "Error: " << error.message();
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, completeMissingSsrcSetup](){
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
        StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak](){
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
        StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
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

    void beginTestQualityTimer(int timeoutMs) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
        StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
            auto strong = weak.lock();
            if (!strong) {
                return;
            }

            strong->_debugQualityValue = !strong->_debugQualityValue;
            strong->updateRemoteVideoConstaints();

            strong->beginTestQualityTimer(5000);
        }, timeoutMs);
    }

    void collectStats() {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        rtc::scoped_refptr<RTCStatsCollectorCallbackImpl> observer(new rtc::RefCountedObject<RTCStatsCollectorCallbackImpl>([weak](const rtc::scoped_refptr<const webrtc::RTCStatsReport> &stats) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, stats](){
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

                rtc::scoped_refptr<webrtc::AudioTrackInterface> remoteAudioTrack(static_cast<webrtc::AudioTrackInterface *>(transceiver->receiver()->track().get()));
                if (_audioTracks.find(ssrc) == _audioTracks.end()) {
                    _audioTracks.insert(std::make_pair(ssrc, remoteAudioTrack));
                }
                auto currentVolume = _audioTrackVolumes.find(ssrc);
                if (currentVolume != _audioTrackVolumes.end()) {
                    remoteAudioTrack->GetSource()->SetVolume(currentVolume->second);
                }
                if (_audioTrackSinks.find(ssrc) == _audioTrackSinks.end()) {
                    const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
                    std::shared_ptr<AudioTrackSinkInterfaceImpl> sink(new AudioTrackSinkInterfaceImpl([weak, ssrc](float level, bool hasSpeech) {
                        StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, ssrc, level, hasSpeech]() {
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
                    //remoteAudioTrack->GetSource()->SetVolume(0.01);
                }
            }
        } else if (transceiver->direction() == webrtc::RtpTransceiverDirection::kRecvOnly && transceiver->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
            auto streamId = transceiver->mid().value();
            if (streamId.find("video") != 0) {
                return;
            }
            streamId.replace(0, 5, "");
            std::istringstream iss(streamId);
            uint32_t ssrc = 0;
            iss >> ssrc;

            auto remoteVideoTrack = static_cast<webrtc::VideoTrackInterface *>(transceiver->receiver()->track().get());
            if (_remoteVideoTracks.find(ssrc) == _remoteVideoTracks.end()) {
                _remoteVideoTracks[ssrc] = remoteVideoTrack;
                auto current = _remoteVideoTrackSinks.find(ssrc);
                if (current != _remoteVideoTrackSinks.end()) {
                    remoteVideoTrack->AddOrUpdateSink(current->second.get(), rtc::VideoSinkWants());
                } else {
                    std::unique_ptr<CustomVideoSinkInterfaceProxyImpl> sink(new CustomVideoSinkInterfaceProxyImpl());
                    remoteVideoTrack->AddOrUpdateSink(sink.get(), rtc::VideoSinkWants());
                    _remoteVideoTrackSinks[ssrc] = std::move(sink);
                }

                if (_incomingVideoSourcesUpdated) {
                    std::vector<uint32_t> allSources;
                    for (auto &it : _remoteVideoTracks) {
                        allSources.push_back(it.first);
                    }
                    _incomingVideoSourcesUpdated(allSources);
                }
            }
        }
    }

    void onTrackRemoved(rtc::scoped_refptr<webrtc::RtpReceiverInterface> receiver) {
        for (auto &transceiver : _peerConnection->GetTransceivers()) {
            if (transceiver->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
                if (receiver.get() == transceiver->receiver().get()) {
                    auto remoteVideoTrack = static_cast<webrtc::VideoTrackInterface *>(transceiver->receiver()->track().get());

                    for (auto &it : _remoteVideoTracks) {
                        if (it.second.get() == remoteVideoTrack) {
                            auto sink = _remoteVideoTrackSinks.find(it.first);
                            if (sink != _remoteVideoTrackSinks.end()) {
                                remoteVideoTrack->RemoveSink(sink->second.get());
                                _remoteVideoTrackSinks.erase(it.first);
                            }
                            _remoteVideoTracks.erase(it.first);

                            if (_incomingVideoSourcesUpdated) {
                                std::vector<uint32_t> allSources;
                                for (auto &it : _remoteVideoTracks) {
                                    allSources.push_back(it.first);
                                }
                                _incomingVideoSourcesUpdated(allSources);
                            }

                            break;
                        }
                    }

                    break;
                }
            }
        }
    }

    void onMissingSsrc(uint32_t ssrc) {
        /*if (_processedMissingSsrcs.find(ssrc) == _processedMissingSsrcs.end()) {
            _processedMissingSsrcs.insert(ssrc);

            _missingSsrcQueue.insert(ssrc);
            if (!_isProcessingMissingSsrcs) {
                beginProcessingMissingSsrcs();
            }
        }*/
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
            StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
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

        std::vector<GroupParticipantDescription> addParticipants;
        for (auto ssrc : _missingSsrcQueue) {
            GroupParticipantDescription participant;
            participant.audioSsrc = ssrc;
            addParticipants.push_back(participant);
        }
        _missingSsrcQueue.clear();

        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        addParticipantsInternal(addParticipants, true);
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
        StaticThreads::getMediaThread()->PostDelayedTask(RTC_FROM_HERE, [weak]() {
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
                    } else {
                        if (it->direction() != webrtc::RtpTransceiverDirection::kSendOnly) {
                            const auto error = it->SetDirectionWithError(webrtc::RtpTransceiverDirection::kSendOnly);
                            (void)error;

                            applyLocalSdp();
                        }
                    }
                    break;
                }
            }
        }

        _isMuted = isMuted;
        _localAudioTrack->set_enabled(!isMuted);

        RTC_LOG(LoggingSeverity::WARNING) << "setIsMuted: " << isMuted;
    }

    void setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, std::function<void(GroupJoinPayload)> completion) {
        _videoCapture = videoCapture;

        updateVideoTrack(true, completion);
    }

    void updateVideoTrack(bool applyNow, std::function<void(GroupJoinPayload)> completion) {
        if (_videoCapture) {
            VideoCaptureInterfaceObject *videoCaptureImpl = GetVideoCaptureAssumingSameThread(_videoCapture.get());

            //_videoCapture->setPreferredAspectRatio(1280.0f / 720.0f);

            _localVideoTrack = _nativeFactory->CreateVideoTrack("video0", videoCaptureImpl->source());
            _localVideoTrack->set_enabled(true);
            webrtc::RtpTransceiverInit videoInit;
            auto addedTransceiver = _peerConnection->AddTransceiver(_localVideoTrack, videoInit);
            if (addedTransceiver.ok()) {
                _localVideoTrackTransceiver = addedTransceiver.value();
                for (auto &it : _peerConnection->GetTransceivers()) {
                    if (it->media_type() == cricket::MediaType::MEDIA_TYPE_VIDEO) {
                        if (_localVideoTrackTransceiver->sender().get() == it->sender().get()) {
                            it->SetDirectionWithError(webrtc::RtpTransceiverDirection::kSendOnly);

                            auto capabilities = _nativeFactory->GetRtpSenderCapabilities(
                                cricket::MediaType::MEDIA_TYPE_VIDEO);

                            std::vector<webrtc::RtpCodecCapability> codecs;
                            bool hasVP8 = false;
                            for (auto &codec : capabilities.codecs) {
                                if (codec.name == cricket::kVp8CodecName) {
                                    if (!hasVP8) {
                                        codecs.insert(codecs.begin(), codec);
                                        hasVP8 = true;
                                    }
                                } else if (codec.name == cricket::kRtxCodecName) {
                                    codecs.push_back(codec);
                                }
                            }
                            it->SetCodecPreferences(codecs);

                            break;
                        }
                    }
                }
            }
        } else if (_localVideoTrack && _localVideoTrackTransceiver) {
            _localVideoTrack->set_enabled(false);
            _localVideoTrackTransceiver->SetDirectionWithError(webrtc::RtpTransceiverDirection::kInactive);
            for (auto &it : _peerConnection->GetTransceivers()) {
                if (it.get() == _localVideoTrackTransceiver.get()) {
                    _peerConnection->RemoveTrack(it->sender());
                    break;
                }
            }
            _localVideoTrack = nullptr;
            _localVideoTrackTransceiver = nullptr;
        }

        if (applyNow) {
            const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());
            emitJoinPayload([weak, completion](auto result) {
                auto strong = weak.lock();
                if (!strong) {
                    return;
                }

                if (!strong->_joinPayload) {
                    return;
                }
                if (!strong->_joinResponsePayload) {
                    return;
                }

                auto sdp = parseJoinResponseIntoSdp(strong->_sessionId, strong->_joinPayload.value(), strong->_joinResponsePayload.value(), SdpType::kSdpTypeJoinAnswer, strong->_allOtherParticipants, strong->_localVideoMid, strong->_localDataChannelMid, strong->_bundleStreamsState);
                strong->setOfferSdp(sdp, false, true, false);

                completion(result);
            });
        }
    }

    void emitAnswer(bool completeMissingSsrcSetup) {
        const auto weak = std::weak_ptr<GroupInstanceManager>(shared_from_this());

        webrtc::PeerConnectionInterface::RTCOfferAnswerOptions options;
        rtc::scoped_refptr<CreateSessionDescriptionObserverImpl> observer(new rtc::RefCountedObject<CreateSessionDescriptionObserverImpl>([weak, completeMissingSsrcSetup](std::string sdp, std::string type) {
            StaticThreads::getMediaThread()->PostTask(RTC_FROM_HERE, [weak, sdp, type, completeMissingSsrcSetup](){
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

    bool setVideoConstraint(uint32_t ssrc, bool highQuality, bool updateImmediately) {
        auto current = _videoConstraints.find(ssrc);
        bool updated = false;
        if (current != _videoConstraints.end()) {
            updated = current->second != highQuality;
        } else {
            updated = true;
        }

        if (updated) {
            _videoConstraints[ssrc] = highQuality;

            if (updateImmediately) {
                updateRemoteVideoConstaints();
            }
        }
        return updated;
    }

    void updateRemoteVideoConstaints() {
        if (!_localDataChannelIsOpen) {
            return;
        }

        std::vector<uint32_t> keys;
        for (auto &it : _videoConstraints) {
            keys.push_back(it.first);
        }
        std::sort(keys.begin(), keys.end());

        std::string pinnedEndpoint;

        std::ostringstream string;
        string << "{" << "\n";
        string << " \"colibriClass\": \"ReceiverVideoConstraintsChangedEvent\"," << "\n";
        string << " \"videoConstraints\": [" << "\n";
        bool isFirst = true;
        for (size_t i = 0; i < keys.size(); i++) {
            auto it = _videoConstraints.find(keys[i]);
            int idealHeight = 720;
            if (!it->second) {
                idealHeight = 180;
            }

            std::string endpointId;
            for (auto &participant : _allOtherParticipants) {
                if (participant.isRemoved) {
                    continue;
                }
                if (participant.audioSsrc == keys[i]) {
                    endpointId = participant.endpointId;
                    break;
                }
            }

            if (endpointId.size() == 0) {
                continue;
            }

            if (isFirst) {
                isFirst = false;
            } else {
                if (i != 0) {
                    string << ",";
                }
            }
            string << "    {\n";
            string << "      \"id\": \"" << endpointId << "\",\n";
            string << "      \"idealHeight\": " << idealHeight << "\n";
            string << "    }";
            string << "\n";
        }
        string << " ]" << "\n";
        string << "}";

        std::string result = string.str();
        RTC_LOG(LS_INFO) << "DataChannel send message: " << result;

        webrtc::DataBuffer buffer(result, false);
        _localDataChannel->Send(buffer);

        /*if (pinnedEndpoint.size() != 0) {
            std::ostringstream string;
            string << "{" << "\n";
            string << " \"colibriClass\": \"PinnedEndpointChangedEvent\"," << "\n";
            string << " \"pinnedEndpoint\": \"" << pinnedEndpoint << "\"" << "\n";
            string << "}";

            std::string result = string.str();

            RTC_LOG(LS_INFO) << "DataChannel send message: " << result;

            webrtc::DataBuffer buffer(result, false);
            _localDataChannel->Send(buffer);
        }*/
    }

private:
    void withAudioDeviceModule(std::function<void(webrtc::AudioDeviceModule*)> callback) {
        _adm_thread->Invoke<void>(RTC_FROM_HERE, [&] {
            callback(_adm_use_withAudioDeviceModule.get());
        });
    }

    std::function<void(bool)> _networkStateUpdated;
    std::function<void(GroupLevelsUpdate const &)> _audioLevelsUpdated;
    std::function<void(std::vector<uint32_t> const &)> _incomingVideoSourcesUpdated;
    std::function<void(std::vector<uint32_t> const &)> _participantDescriptionsRequired;

    int32_t _myAudioLevelPeakCount = 0;
    float _myAudioLevelPeak = 0;
    GroupLevelValue _myAudioLevel;

    std::string _initialInputDeviceId;
    std::string _initialOutputDeviceId;

    uint32_t _sessionId = 6543245;
    uint32_t _mainStreamAudioSsrc = 0;
    absl::optional<GroupJoinPayload> _joinPayload;
    uint32_t _fakeIncomingSsrc = 0;
    absl::optional<GroupJoinResponsePayload> _joinResponsePayload;

    int64_t _appliedOfferTimestamp = 0;
    bool _isConnected = false;
    int _isConnectedUpdateValidTaskId = 0;

    bool _isMuted = true;

    std::vector<GroupParticipantDescription> _allOtherParticipants;
    std::set<uint32_t> _processedMissingSsrcs;

    int64_t _missingSsrcsProcessedTimestamp = 0;
    bool _isProcessingMissingSsrcs = false;
    std::set<uint32_t> _missingSsrcQueue;

    std::string _appliedRemoteDescription;

    rtc::scoped_refptr<webrtc::PeerConnectionFactoryInterface> _nativeFactory;
    std::unique_ptr<PeerConnectionObserverImpl> _observer;
    rtc::scoped_refptr<webrtc::PeerConnectionInterface> _peerConnection;
    std::unique_ptr<AudioTrackSinkInterfaceImpl> _localAudioTrackSink;
    rtc::scoped_refptr<webrtc::AudioTrackInterface> _localAudioTrack;
    rtc::scoped_refptr<webrtc::RtpSenderInterface> _localAudioTrackSender;

    rtc::scoped_refptr<webrtc::VideoTrackInterface> _localVideoTrack;
    rtc::scoped_refptr<webrtc::RtpTransceiverInterface> _localVideoTrackTransceiver;

    rtc::scoped_refptr<webrtc::DataChannelInterface> _localDataChannel;
    absl::optional<std::string> _localDataChannelMid;
    std::unique_ptr<DataChannelObserverImpl> _localDataChannelObserver;
    bool _localDataChannelIsOpen = false;

    absl::optional<std::string> _localVideoMid;

    std::vector<StreamSpec> _bundleStreamsState;

    std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> _createAudioDeviceModule;
    rtc::Thread *_adm_thread = nullptr;
    rtc::scoped_refptr<webrtc::AudioDeviceModule> _adm_use_withAudioDeviceModule;

    std::map<uint32_t, rtc::scoped_refptr<webrtc::AudioTrackInterface>> _audioTracks;
    std::map<uint32_t, double> _audioTrackVolumes;
    std::map<uint32_t, std::shared_ptr<AudioTrackSinkInterfaceImpl>> _audioTrackSinks;
    std::map<uint32_t, GroupLevelValue> _audioLevels;

    std::map<uint32_t, bool> _videoConstraints;
    uint32_t _currentFullSizeVideoSsrc = 0;

    bool _debugQualityValue = false;

    std::map<uint32_t, rtc::scoped_refptr<webrtc::VideoTrackInterface>> _remoteVideoTracks;
    std::map<uint32_t, std::unique_ptr<CustomVideoSinkInterfaceProxyImpl>> _remoteVideoTrackSinks;

    std::shared_ptr<VideoCaptureInterface> _videoCapture;

    std::unique_ptr<ErrorParsingLogSink> _errorParsingLogSink;

    std::shared_ptr<PlatformContext> _platformContext;
};

GroupInstanceImpl::GroupInstanceImpl(GroupInstanceDescriptor &&descriptor)
: _logSink(std::make_unique<LogSinkImpl>(descriptor.config.logPath)) {
    rtc::LogMessage::LogToDebug(rtc::LS_INFO);
    rtc::LogMessage::SetLogToStderr(true);
    if (_logSink) {
		rtc::LogMessage::AddLogToStream(_logSink.get(), rtc::LS_INFO);
	}

	_manager.reset(new ThreadLocalObject<GroupInstanceManager>(StaticThreads::getMediaThread(), [descriptor = std::move(descriptor)]() mutable {
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
    StaticThreads::getMediaThread()->Invoke<void>(RTC_FROM_HERE, [] {});
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

void GroupInstanceImpl::setJoinResponsePayload(GroupJoinResponsePayload payload, std::vector<tgcalls::GroupParticipantDescription> &&participants) {
    _manager->perform(RTC_FROM_HERE, [payload, participants = std::move(participants)](GroupInstanceManager *manager) mutable {
        manager->setJoinResponsePayload(payload, std::move(participants));
    });
}

void GroupInstanceImpl::removeSsrcs(std::vector<uint32_t> ssrcs) {
    _manager->perform(RTC_FROM_HERE, [ssrcs](GroupInstanceManager *manager) {
        manager->removeSsrcs(ssrcs);
    });
}

void GroupInstanceImpl::addParticipants(std::vector<GroupParticipantDescription> &&participants) {
    _manager->perform(RTC_FROM_HERE, [participants = std::move(participants)](GroupInstanceManager *manager) mutable {
        manager->addParticipants(std::move(participants));
    });
}

void GroupInstanceImpl::setIsMuted(bool isMuted) {
    _manager->perform(RTC_FROM_HERE, [isMuted](GroupInstanceManager *manager) {
        manager->setIsMuted(isMuted);
    });
}

void GroupInstanceImpl::setVideoCapture(std::shared_ptr<VideoCaptureInterface> videoCapture, std::function<void(GroupJoinPayload)> completion) {
    _manager->perform(RTC_FROM_HERE, [videoCapture, completion = std::move(completion)](GroupInstanceManager *manager) mutable {
        manager->setVideoCapture(videoCapture, completion);
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

void GroupInstanceImpl::addIncomingVideoOutput(uint32_t ssrc, std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    _manager->perform(RTC_FROM_HERE, [ssrc, sink](GroupInstanceManager *manager) {
        manager->addIncomingVideoOutput(ssrc, sink);
    });
}

void GroupInstanceImpl::setVolume(uint32_t ssrc, double volume) {
    _manager->perform(RTC_FROM_HERE, [ssrc, volume](GroupInstanceManager *manager) {
        manager->setVolume(ssrc, volume);
    });
}

void GroupInstanceImpl::setFullSizeVideoSsrc(uint32_t ssrc) {
    _manager->perform(RTC_FROM_HERE, [ssrc](GroupInstanceManager *manager) {
        manager->setFullSizeVideoSsrc(ssrc);
    });
}

} // namespace tgcalls
