#ifndef TGCALLS_CODEC_SELECT_HELPER_H
#define TGCALLS_CODEC_SELECT_HELPER_H

#include "Message.h"
#include "media/base/codec.h"

namespace tgcalls {

class PlatformContext;

struct CommonFormats {
	std::vector<webrtc::SdpVideoFormat> list;
	int myEncoderIndex = -1;
};

struct CommonCodecs {
	std::vector<cricket::VideoCodec> list;
	int myEncoderIndex = -1;
};

VideoFormatsMessage ComposeSupportedFormats(
	std::vector<webrtc::SdpVideoFormat> encoders,
	std::vector<webrtc::SdpVideoFormat> decoders,
    const std::vector<std::string> &preferredCodecs,
	std::shared_ptr<PlatformContext> platformContext);

CommonFormats ComputeCommonFormats(
	const VideoFormatsMessage &my,
	VideoFormatsMessage theirs);

CommonCodecs AssignPayloadTypesAndDefaultCodecs(CommonFormats &&formats);

} // namespace tgcalls

#endif
