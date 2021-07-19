#include "GroupJoinPayloadInternal.h"

#include "third-party/json11.hpp"
#include <sstream>

namespace tgcalls {

namespace {

absl::optional<int32_t> parseInt(json11::Json::object const &object, std::string const &key) {
    const auto value = object.find(key);
    if (value == object.end() || !value->second.is_number()) {
        return absl::nullopt;
    }
    return value->second.int_value();
}

absl::optional<std::string> parseString(json11::Json::object const &object, std::string const &key) {
    const auto value = object.find(key);
    if (value == object.end() || !value->second.is_string()) {
        return absl::nullopt;
    }
    return value->second.string_value();
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

absl::optional<GroupJoinTransportDescription> parseTransportDescription(json11::Json::object const &object) {
    GroupJoinTransportDescription result;

    if (const auto pwd = parseString(object, "pwd")) {
        result.pwd = pwd.value();
    } else {
        return absl::nullopt;
    }

    if (const auto ufrag = parseString(object, "ufrag")) {
        result.ufrag = ufrag.value();
    } else {
        return absl::nullopt;
    }

    const auto fingerprints = object.find("fingerprints");
    if (fingerprints == object.end() || !fingerprints->second.is_array()) {
        return absl::nullopt;
    }
    for (const auto &fingerprint : fingerprints->second.array_items()) {
        if (!fingerprint.is_object()) {
            return absl::nullopt;
        }

        GroupJoinTransportDescription::Fingerprint parsedFingerprint;

        if (const auto hash = parseString(fingerprint.object_items(), "hash")) {
            parsedFingerprint.hash = hash.value();
        } else {
            return absl::nullopt;
        }

        if (const auto fingerprintValue = parseString(fingerprint.object_items(), "fingerprint")) {
            parsedFingerprint.fingerprint = fingerprintValue.value();
        } else {
            return absl::nullopt;
        }

        if (const auto setup = parseString(fingerprint.object_items(), "setup")) {
            parsedFingerprint.setup = setup.value();
        } else {
            return absl::nullopt;
        }

        result.fingerprints.push_back(std::move(parsedFingerprint));
    }

    const auto candidates = object.find("candidates");
    if (candidates == object.end() || !candidates->second.is_array()) {
        return absl::nullopt;
    }
    for (const auto &candidate : candidates->second.array_items()) {
        if (!candidate.is_object()) {
            return absl::nullopt;
        }

        GroupJoinTransportDescription::Candidate parsedCandidate;

        if (const auto port = parseString(candidate.object_items(), "port")) {
            parsedCandidate.port = port.value();
        } else {
            return absl::nullopt;
        }

        if (const auto protocol = parseString(candidate.object_items(), "protocol")) {
            parsedCandidate.protocol = protocol.value();
        } else {
            return absl::nullopt;
        }

        if (const auto network = parseString(candidate.object_items(), "network")) {
            parsedCandidate.network = network.value();
        } else {
            return absl::nullopt;
        }

        if (const auto generation = parseString(candidate.object_items(), "generation")) {
            parsedCandidate.generation = generation.value();
        } else {
            return absl::nullopt;
        }

        if (const auto id = parseString(candidate.object_items(), "id")) {
            parsedCandidate.id = id.value();
        } else {
            return absl::nullopt;
        }

        if (const auto component = parseString(candidate.object_items(), "component")) {
            parsedCandidate.component = component.value();
        } else {
            return absl::nullopt;
        }

        if (const auto foundation = parseString(candidate.object_items(), "foundation")) {
            parsedCandidate.foundation = foundation.value();
        } else {
            return absl::nullopt;
        }

        if (const auto priority = parseString(candidate.object_items(), "priority")) {
            parsedCandidate.priority = priority.value();
        } else {
            return absl::nullopt;
        }

        if (const auto ip = parseString(candidate.object_items(), "ip")) {
            parsedCandidate.ip = ip.value();
        } else {
            return absl::nullopt;
        }

        if (const auto type = parseString(candidate.object_items(), "type")) {
            parsedCandidate.type = type.value();
        } else {
            return absl::nullopt;
        }

        if (const auto tcpType = parseString(candidate.object_items(), "tcptype")) {
            parsedCandidate.tcpType = tcpType.value();
        }

        if (const auto relAddr = parseString(candidate.object_items(), "rel-addr")) {
            parsedCandidate.relAddr = relAddr.value();
        }

        if (const auto relPort = parseString(candidate.object_items(), "rel-port")) {
            parsedCandidate.relPort = relPort.value();
        }

        result.candidates.push_back(std::move(parsedCandidate));
    }

    return result;
}

absl::optional<GroupJoinPayloadVideoPayloadType> parsePayloadType(json11::Json::object const &object) {
    GroupJoinPayloadVideoPayloadType result;

    if (const auto id = parseInt(object, "id")) {
        result.id = (uint32_t)id.value();
    } else {
        return absl::nullopt;
    }

    if (const auto name = parseString(object, "name")) {
        result.name = name.value();
    } else {
        return absl::nullopt;
    }

    if (const auto clockrate = parseInt(object, "clockrate")) {
        result.clockrate = (uint32_t)clockrate.value();
    } else {
        result.clockrate = 0;
    }

    if (const auto channels = parseInt(object, "channels")) {
        result.channels = (uint32_t)channels.value();
    } else {
        result.channels = 1;
    }

    const auto parameters = object.find("parameters");
    if (parameters != object.end() && parameters->second.is_object()) {
        for (const auto &parameter : parameters->second.object_items()) {
            if (parameter.second.is_string()) {
                result.parameters.push_back(std::make_pair(parameter.first, parameter.second.string_value()));
            }
        }
    }

    const auto rtcpFbs = object.find("rtcp-fbs");
    if (rtcpFbs != object.end() && rtcpFbs->second.is_array()) {
        for (const auto &item : rtcpFbs->second.array_items()) {
            if (item.is_object()) {
                const auto type = item.object_items().find("type");
                if (type != item.object_items().end() && type->second.is_string()) {
                    GroupJoinPayloadVideoPayloadType::FeedbackType parsedFeedbackType;

                    const auto typeString = type->second.string_value();

                    const auto subtype = item.object_items().find("subtype");
                    if (subtype != item.object_items().end() && subtype->second.is_string()) {
                        parsedFeedbackType.type = typeString;
                        parsedFeedbackType.subtype = subtype->second.string_value();
                    } else {
                        auto components = splitString(typeString, ' ');
                        if (components.size() == 1) {
                            parsedFeedbackType.type = components[0];
                        } else if (components.size() == 2) {
                            parsedFeedbackType.type = components[0];
                            parsedFeedbackType.subtype = components[1];
                        } else {
                            continue;
                        }
                    }

                    result.feedbackTypes.push_back(std::move(parsedFeedbackType));
                }
            }
        }
    }

    return result;
}

absl::optional<GroupJoinVideoInformation> parseVideoInformation(json11::Json::object const &object) {
    GroupJoinVideoInformation result;

    const auto serverSources = object.find("server_sources");
    if (serverSources != object.end() && serverSources->second.is_array()) {
        for (const auto &item : serverSources->second.array_items()) {
            if (item.is_number()) {
                int32_t value = item.int_value();
                uint32_t unsignedValue = *(uint32_t *)&value;
                result.serverVideoBandwidthProbingSsrc = unsignedValue;
            }
        }
    }

    const auto payloadTypes = object.find("payload-types");
    if (payloadTypes != object.end() && payloadTypes->second.is_array()) {
        for (const auto &payloadType : payloadTypes->second.array_items()) {
            if (payloadType.is_object()) {
                if (const auto parsedPayloadType = parsePayloadType(payloadType.object_items())) {
                    result.payloadTypes.push_back(parsedPayloadType.value());
                }
            }
        }
    }

    const auto rtpHdrexts = object.find("rtp-hdrexts");
    if (rtpHdrexts != object.end() && rtpHdrexts->second.is_array()) {
        for (const auto &rtpHdrext : rtpHdrexts->second.array_items()) {
            if (rtpHdrext.is_object()) {
                const auto id = rtpHdrext.object_items().find("id");
                if (id == rtpHdrext.object_items().end() || !id->second.is_number()) {
                    continue;
                }

                const auto uri = rtpHdrext.object_items().find("uri");
                if (uri == rtpHdrext.object_items().end() || !uri->second.is_string()) {
                    continue;
                }

                result.extensionMap.push_back(std::make_pair(id->second.int_value(), uri->second.string_value()));
            }
        }
    }

    const auto endpointId = object.find("endpoint");
    if (endpointId != object.end() && endpointId->second.is_string()) {
        result.endpointId = endpointId->second.string_value();
    }

    return result;
}

}

std::string GroupJoinInternalPayload::serialize() {
    json11::Json::object object;

    int32_t signedSsrc = *(int32_t *)&audioSsrc;

    object.insert(std::make_pair("ssrc", json11::Json(signedSsrc)));
    object.insert(std::make_pair("ufrag", json11::Json(transport.ufrag)));
    object.insert(std::make_pair("pwd", json11::Json(transport.pwd)));

    json11::Json::array fingerprints;
    for (const auto &fingerprint : transport.fingerprints) {
        json11::Json::object fingerprintJson;

        fingerprintJson.insert(std::make_pair("hash", json11::Json(fingerprint.hash)));
        fingerprintJson.insert(std::make_pair("fingerprint", json11::Json(fingerprint.fingerprint)));
        fingerprintJson.insert(std::make_pair("setup", json11::Json(fingerprint.setup)));

        fingerprints.push_back(json11::Json(std::move(fingerprintJson)));
    }
    object.insert(std::make_pair("fingerprints", json11::Json(std::move(fingerprints))));

    if (videoInformation) {
        json11::Json::array ssrcGroups;
        for (const auto &ssrcGroup : videoInformation->ssrcGroups) {
            json11::Json::object ssrcGroupJson;

            json11::Json::array ssrcGroupSources;
            for (auto ssrc : ssrcGroup.ssrcs) {
                int32_t signedValue = *(int32_t *)&ssrc;
                ssrcGroupSources.push_back(json11::Json(signedValue));
            }

            ssrcGroupJson.insert(std::make_pair("sources", json11::Json(std::move(ssrcGroupSources))));
            ssrcGroupJson.insert(std::make_pair("semantics", json11::Json(ssrcGroup.semantics)));

            ssrcGroups.push_back(json11::Json(std::move(ssrcGroupJson)));
        }
        object.insert(std::make_pair("ssrc-groups", json11::Json(std::move(ssrcGroups))));
    }

    auto json = json11::Json(std::move(object));
    return json.dump();
}

absl::optional<GroupJoinResponsePayload> GroupJoinResponsePayload::parse(std::string const &data) {
    std::string parsingError;
    auto json = json11::Json::parse(std::string(data.begin(), data.end()), parsingError);
    if (json.type() != json11::Json::OBJECT) {
        return absl::nullopt;
    }

    tgcalls::GroupJoinResponsePayload result;

    const auto transport = json.object_items().find("transport");
    if (transport == json.object_items().end() || !transport->second.is_object()) {
        return absl::nullopt;
    }
    if (const auto parsedTransport = parseTransportDescription(transport->second.object_items())) {
        result.transport = parsedTransport.value();
    } else {
        return absl::nullopt;
    }

    const auto video = json.object_items().find("video");
    if (video != json.object_items().end() && video->second.is_object()) {
        result.videoInformation = parseVideoInformation(video->second.object_items());
    }

    return result;
}

}
