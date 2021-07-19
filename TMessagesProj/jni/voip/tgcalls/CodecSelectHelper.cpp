#include "CodecSelectHelper.h"

#include "platform/PlatformInterface.h"

#include "media/base/media_constants.h"
#include "media/base/codec.h"
#include "absl/strings/match.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/field_trial.h"

namespace tgcalls {
namespace {

using VideoFormat = webrtc::SdpVideoFormat;

bool CompareFormats(const VideoFormat &a, const VideoFormat &b) {
	if (a.name < b.name) {
		return true;
	} else if (b.name < a.name) {
		return false;
	} else {
		return a.parameters < b.parameters;
	}
}

int FormatPriority(const VideoFormat &format, const std::vector<std::string> &preferredCodecs, std::shared_ptr<PlatformContext> platformContext) {
	static const auto kCodecs = {
		std::string(cricket::kAv1CodecName),
        std::string(cricket::kVp9CodecName),
#ifndef WEBRTC_DISABLE_H265
		std::string(cricket::kH265CodecName),
#endif
		std::string(cricket::kH264CodecName),
		std::string(cricket::kVp8CodecName),
	};
	static const auto kSupported = [platformContext] {
		const auto platform = PlatformInterface::SharedInstance();

		auto result = std::vector<std::string>();
		result.reserve(kCodecs.size());
		for (const auto &codec : kCodecs) {
			if (platform->supportsEncoding(codec, platformContext)) {
				result.push_back(codec);
			}
		}
		return result;
	}();

    for (int i = 0; i < preferredCodecs.size(); i++) {
        for (const auto &name : kSupported) {
            if (absl::EqualsIgnoreCase(format.name, preferredCodecs[i]) && absl::EqualsIgnoreCase(format.name, name)) {
                return i;
            }
        }
    }

    auto result = (int)preferredCodecs.size();
	for (const auto &name : kSupported) {
		if (absl::EqualsIgnoreCase(format.name, name)) {
			return result;
		}
		++result;
	}
	return -1;
}

bool ComparePriorities(const VideoFormat &a, const VideoFormat &b, const std::vector<std::string> &preferredCodecs, std::shared_ptr<PlatformContext> platformContext) {
	return FormatPriority(a, preferredCodecs, platformContext) < FormatPriority(b, preferredCodecs, platformContext);
}

std::vector<VideoFormat> FilterAndSortEncoders(std::vector<VideoFormat> list, const std::vector<std::string> &preferredCodecs, std::shared_ptr<PlatformContext> platformContext) {
	const auto listBegin = begin(list);
	const auto listEnd = end(list);
    std::sort(listBegin, listEnd, [&preferredCodecs, platformContext](const VideoFormat &lhs, const VideoFormat &rhs) {
        return ComparePriorities(lhs, rhs, preferredCodecs, platformContext);
    });
	auto eraseFrom = listBegin;
	auto eraseTill = eraseFrom;
	while (eraseTill != listEnd && FormatPriority(*eraseTill, preferredCodecs, platformContext) == -1) {
		++eraseTill;
	}
	if (eraseTill != eraseFrom) {
		list.erase(eraseFrom, eraseTill);
	}
	return list;
}

std::vector<VideoFormat> AppendUnique(
		std::vector<VideoFormat> list,
		std::vector<VideoFormat> other) {
	if (list.empty()) {
		return other;
	}
	list.reserve(list.size() + other.size());
	const auto oldBegin = &list[0];
	const auto oldEnd = oldBegin + list.size();
	for (auto &format : other) {
		if (std::find(oldBegin, oldEnd, format) == oldEnd) {
			list.push_back(std::move(format));
		}
	}
	return list;
}

std::vector<VideoFormat>::const_iterator FindEqualFormat(
		const std::vector<VideoFormat> &list,
		const VideoFormat &format) {
	return std::find_if(list.begin(), list.end(), [&](const VideoFormat &other) {
		return cricket::IsSameCodec(
			format.name,
			format.parameters,
			other.name,
			other.parameters);
	});
}

void AddDefaultFeedbackParams(cricket::VideoCodec *codec) {
	// Don't add any feedback params for RED and ULPFEC.
	if (codec->name == cricket::kRedCodecName || codec->name == cricket::kUlpfecCodecName)
		return;
	codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamRemb, cricket::kParamValueEmpty));
	codec->AddFeedbackParam(
		cricket::FeedbackParam(cricket::kRtcpFbParamTransportCc, cricket::kParamValueEmpty));
	// Don't add any more feedback params for FLEXFEC.
	if (codec->name == cricket::kFlexfecCodecName)
		return;
	codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamCcm, cricket::kRtcpFbCcmParamFir));
	codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kParamValueEmpty));
	codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamNack, cricket::kRtcpFbNackParamPli));
	if (codec->name == cricket::kVp8CodecName &&
		webrtc::field_trial::IsEnabled("WebRTC-RtcpLossNotification")) {
		codec->AddFeedbackParam(cricket::FeedbackParam(cricket::kRtcpFbParamLntf, cricket::kParamValueEmpty));
	}
}

} // namespace

VideoFormatsMessage ComposeSupportedFormats(
		std::vector<VideoFormat> encoders,
		std::vector<VideoFormat> decoders,
        const std::vector<std::string> &preferredCodecs,
		std::shared_ptr<PlatformContext> platformContext) {
	encoders = FilterAndSortEncoders(std::move(encoders), preferredCodecs, platformContext);

	auto result = VideoFormatsMessage();
	result.encodersCount = (int)encoders.size();
	result.formats = AppendUnique(std::move(encoders), std::move(decoders));
	for (const auto &format : result.formats) {
		RTC_LOG(LS_INFO) << "Format: " << format.ToString();
	}
	RTC_LOG(LS_INFO) << "First " << result.encodersCount << " formats are supported encoders.";
	return result;
}

CommonFormats ComputeCommonFormats(
		const VideoFormatsMessage &my,
		VideoFormatsMessage their) {
	assert(my.encodersCount <= my.formats.size());
	assert(their.encodersCount <= their.formats.size());

	for (const auto &format : their.formats) {
		RTC_LOG(LS_INFO) << "Their format: " << format.ToString();
	}
	RTC_LOG(LS_INFO) << "Their first " << their.encodersCount << " formats are supported encoders.";

	const auto myEncodersBegin = begin(my.formats);
	const auto myEncodersEnd = myEncodersBegin + my.encodersCount;
	const auto theirEncodersBegin = begin(their.formats);
	const auto theirEncodersEnd = theirEncodersBegin + their.encodersCount;

	auto result = CommonFormats();
	const auto addUnique = [&](const VideoFormat &format) {
		const auto already = std::find(
			result.list.begin(),
			result.list.end(),
			format);
		if (already == result.list.end()) {
			result.list.push_back(format);
		}
	};
	const auto addCommonAndFindFirst = [&](
			std::vector<VideoFormat>::const_iterator begin,
			std::vector<VideoFormat>::const_iterator end,
			const std::vector<VideoFormat> &decoders) {
		auto first = VideoFormat(std::string());
		for (auto i = begin; i != end; ++i) {
			const auto &format = *i;
			const auto j = FindEqualFormat(decoders, format);
			if (j != decoders.end()) {
				if (first.name.empty()) {
					first = format;
				}
				addUnique(format);
				addUnique(*j);
			};
		}
		return first;
	};

	result.list.reserve(my.formats.size() + their.formats.size());
	auto myEncoderFormat = addCommonAndFindFirst(
		myEncodersBegin,
		myEncodersEnd,
		their.formats);
	auto theirEncoderFormat = addCommonAndFindFirst(
		theirEncodersBegin,
		theirEncodersEnd,
		my.formats);
	std::sort(begin(result.list), end(result.list), CompareFormats);
	if (!myEncoderFormat.name.empty()) {
		const auto i = std::find(begin(result.list), end(result.list), myEncoderFormat);
		assert(i != end(result.list));
		result.myEncoderIndex = (i - begin(result.list));
	}

	for (const auto &format : result.list) {
		RTC_LOG(LS_INFO) << "Common format: " << format.ToString();
	}
	RTC_LOG(LS_INFO) << "My encoder: " << (result.myEncoderIndex >= 0 ? result.list[result.myEncoderIndex].ToString() : "(null)");
	RTC_LOG(LS_INFO) << "Their encoder: " << (!theirEncoderFormat.name.empty() ? theirEncoderFormat.ToString() : "(null)");

	return result;
}

CommonCodecs AssignPayloadTypesAndDefaultCodecs(CommonFormats &&formats) {
	if (formats.list.empty()) {
		return CommonCodecs();
	}

	constexpr int kFirstDynamicPayloadType = 96;
	constexpr int kLastDynamicPayloadType = 127;

	int payload_type = kFirstDynamicPayloadType;

	formats.list.push_back(webrtc::SdpVideoFormat(cricket::kRedCodecName));
	formats.list.push_back(webrtc::SdpVideoFormat(cricket::kUlpfecCodecName));

	if (true) {
		webrtc::SdpVideoFormat flexfec_format(cricket::kFlexfecCodecName);
		// This value is currently arbitrarily set to 10 seconds. (The unit
		// is microseconds.) This parameter MUST be present in the SDP, but
		// we never use the actual value anywhere in our code however.
		// TODO(brandtr): Consider honouring this value in the sender and receiver.
		flexfec_format.parameters = { {cricket::kFlexfecFmtpRepairWindow, "10000000"} };
		formats.list.push_back(flexfec_format);
	}

	auto inputIndex = 0;
	auto result = CommonCodecs();
	result.list.reserve(2 * formats.list.size() - 2);
	for (const auto &format : formats.list) {
		cricket::VideoCodec codec(format);
		codec.id = payload_type;
		AddDefaultFeedbackParams(&codec);

		if (inputIndex++ == formats.myEncoderIndex) {
			result.myEncoderIndex = result.list.size();
		}
		result.list.push_back(codec);

		// Increment payload type.
		++payload_type;
		if (payload_type > kLastDynamicPayloadType) {
			RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
			break;
		}

		// Add associated RTX codec for non-FEC codecs.
		if (!absl::EqualsIgnoreCase(codec.name, cricket::kUlpfecCodecName) &&
			!absl::EqualsIgnoreCase(codec.name, cricket::kFlexfecCodecName)) {
			result.list.push_back(cricket::VideoCodec::CreateRtxCodec(payload_type, codec.id));

			// Increment payload type.
			++payload_type;
			if (payload_type > kLastDynamicPayloadType) {
				RTC_LOG(LS_ERROR) << "Out of dynamic payload types, skipping the rest.";
				break;
			}
		}
	}
	return result;
}

} // namespace tgcalls
