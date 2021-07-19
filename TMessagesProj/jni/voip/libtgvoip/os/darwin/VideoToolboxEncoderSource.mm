//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#import <Foundation/Foundation.h>
#include "VideoToolboxEncoderSource.h"
#include "../../PrivateDefines.h"
#include "../../logging.h"

using namespace tgvoip;
using namespace tgvoip::video;

#define CHECK_ERR(err, msg) if(err!=noErr){LOGE("VideoToolboxEncoder: " msg " failed: %d", err); return;}

VideoToolboxEncoderSource::VideoToolboxEncoderSource(){

}

VideoToolboxEncoderSource::~VideoToolboxEncoderSource(){
	if(session){
		CFRelease(session);
		session=NULL;
	}
}

void VideoToolboxEncoderSource::Start(){

}

void VideoToolboxEncoderSource::Stop(){

}

void VideoToolboxEncoderSource::Reset(uint32_t codec, int maxResolution){
	if(session){
		LOGV("Releasing old compression session");
		//VTCompressionSessionCompleteFrames(session, kCMTimeInvalid);
		VTCompressionSessionInvalidate(session);
		CFRelease(session);
		session=NULL;
		LOGV("Released compression session");
	}
	CMVideoCodecType codecType;
	switch(codec){
	case CODEC_AVC:
		codecType=kCMVideoCodecType_H264;
		break;
	case CODEC_HEVC:
		codecType=kCMVideoCodecType_HEVC;
		break;
	default:
		LOGE("VideoToolboxEncoder: Unsupported codec");
		return;
	}
	needUpdateStreamParams=true;
	this->codec=codec;
	// typedef void (*VTCompressionOutputCallback)(void *outputCallbackRefCon, void *sourceFrameRefCon, OSStatus status, VTEncodeInfoFlags infoFlags, CMSampleBufferRef sampleBuffer);
	uint32_t width, height;
	switch(maxResolution){
		case INIT_VIDEO_RES_1080:
			width=1920;
			height=1080;
			break;
		case INIT_VIDEO_RES_720:
			width=1280;
			height=720;
			break;
		case INIT_VIDEO_RES_480:
			width=854;
			height=480;
			break;
		case INIT_VIDEO_RES_360:
		default:
			width=640;
			height=360;
			break;
	}
	OSStatus status=VTCompressionSessionCreate(NULL, width, height, codecType, NULL, NULL, NULL, [](void *outputCallbackRefCon, void *sourceFrameRefCon, OSStatus status, VTEncodeInfoFlags infoFlags, CMSampleBufferRef sampleBuffer){
		reinterpret_cast<VideoToolboxEncoderSource*>(outputCallbackRefCon)->EncoderCallback(status, sampleBuffer, infoFlags);
	}, this, &session);
	if(status!=noErr){
		LOGE("VTCompressionSessionCreate failed: %d", status);
		return;
	}
	LOGD("Created VTCompressionSession");
	status=VTSessionSetProperty(session, kVTCompressionPropertyKey_AllowFrameReordering, kCFBooleanFalse);
	CHECK_ERR(status, "VTSessionSetProperty(AllowFrameReordering)");
	int64_t interval=15;
	status=VTSessionSetProperty(session, kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration, (__bridge CFTypeRef)@(interval));
	CHECK_ERR(status, "VTSessionSetProperty(MaxKeyFrameIntervalDuration)");
	SetEncoderBitrateAndLimit(lastBitrate);
	status=VTSessionSetProperty(session, kVTCompressionPropertyKey_RealTime, kCFBooleanTrue);
	CHECK_ERR(status, "VTSessionSetProperty(RealTime)");
	LOGD("VTCompressionSession initialized");
	
	// TODO change camera frame rate dynamically based on resolution + codec
}

void VideoToolboxEncoderSource::RequestKeyFrame(){
	keyframeRequested=true;
}

void VideoToolboxEncoderSource::EncodeFrame(CMSampleBufferRef frame){
	if(!session)
		return;
	CMFormatDescriptionRef format=CMSampleBufferGetFormatDescription(frame);
	CMMediaType type=CMFormatDescriptionGetMediaType(format);
	if(type!=kCMMediaType_Video){
		//LOGW("Received non-video CMSampleBuffer");
		return;
	}
	if(bitrateChangeRequested){
		LOGI("VideoToolboxEocnder: setting bitrate to %u", bitrateChangeRequested);
		SetEncoderBitrateAndLimit(bitrateChangeRequested);
    	lastBitrate=bitrateChangeRequested;
    	bitrateChangeRequested=0;
	}
	CFDictionaryRef frameProps=NULL;
	if(keyframeRequested){
		LOGI("VideoToolboxEncoder: requesting keyframe");
		const void* keys[]={kVTEncodeFrameOptionKey_ForceKeyFrame};
		const void* values[]={kCFBooleanTrue};
		frameProps=CFDictionaryCreate(NULL, keys, values, 1, NULL, NULL);
		keyframeRequested=false;
	}
	
	//CMVideoDimensions size=CMVideoFormatDescriptionGetDimensions(format);
	//LOGD("EncodeFrame %d x %d", size.width, size.height);
	CVImageBufferRef imgBuffer=CMSampleBufferGetImageBuffer(frame);
	//OSType pixFmt=CVPixelBufferGetPixelFormatType(imgBuffer);
	//LOGV("pixel format: %c%c%c%c", PRINT_FOURCC(pixFmt));
	OSStatus status=VTCompressionSessionEncodeFrame(session, imgBuffer, CMSampleBufferGetPresentationTimeStamp(frame), CMSampleBufferGetDuration(frame), frameProps, NULL, NULL);
	CHECK_ERR(status, "VTCompressionSessionEncodeFrame");
	if(frameProps)
		CFRelease(frameProps);
}

void VideoToolboxEncoderSource::SetBitrate(uint32_t bitrate){
	bitrateChangeRequested=bitrate;
}

void VideoToolboxEncoderSource::EncoderCallback(OSStatus status, CMSampleBufferRef buffer, VTEncodeInfoFlags flags){
	if(status!=noErr){
		LOGE("EncoderCallback error: %d", status);
		return;
	}
	if(flags & kVTEncodeInfo_FrameDropped){
		LOGW("VideoToolboxEncoder: Frame dropped");
	}
	if(!CMSampleBufferGetNumSamples(buffer)){
		LOGW("Empty CMSampleBuffer");
		return;
	}
	const uint8_t startCode[]={0, 0, 0, 1};
	if(needUpdateStreamParams){
		LOGI("VideoToolboxEncoder: Updating stream params");
		CMFormatDescriptionRef format=CMSampleBufferGetFormatDescription(buffer);
    	CMVideoDimensions size=CMVideoFormatDescriptionGetDimensions(format);
    	width=size.width;
    	height=size.height;
    	csd.clear();
    	if(codec==CODEC_AVC){
			for(size_t i=0;i<2;i++){
				const uint8_t* ps=NULL;
				size_t pl=0;
    			status=CMVideoFormatDescriptionGetH264ParameterSetAtIndex(format, i, &ps, &pl, NULL, NULL);
    			CHECK_ERR(status, "CMVideoFormatDescriptionGetH264ParameterSetAtIndex");
				Buffer b(pl+4);
				b.CopyFrom(ps, 4, pl);
				b.CopyFrom(startCode, 0, 4);
				csd.push_back(std::move(b));
			}
		}else if(codec==CODEC_HEVC){
			LOGD("here1");
			BufferOutputStream csdBuf(1024);
			for(size_t i=0;i<3;i++){
				const uint8_t* ps=NULL;
				size_t pl=0;
				status=CMVideoFormatDescriptionGetHEVCParameterSetAtIndex(format, i, &ps, &pl, NULL, NULL);
				CHECK_ERR(status, "CMVideoFormatDescriptionGetHEVCParameterSetAtIndex");
				csdBuf.WriteBytes(startCode, 4);
				csdBuf.WriteBytes(ps, pl);
			}
			csd.push_back(std::move(csdBuf));
		}
		needUpdateStreamParams=false;
	}
	CMBlockBufferRef blockBuffer=CMSampleBufferGetDataBuffer(buffer);
	size_t len=CMBlockBufferGetDataLength(blockBuffer);

	int frameFlags=0;
	CFArrayRef attachmentsArray=CMSampleBufferGetSampleAttachmentsArray(buffer, 0);
	if(attachmentsArray && CFArrayGetCount(attachmentsArray)){
		CFBooleanRef notSync;
        CFDictionaryRef dict=(CFDictionaryRef)CFArrayGetValueAtIndex(attachmentsArray, 0);
        BOOL keyExists=CFDictionaryGetValueIfPresent(dict, kCMSampleAttachmentKey_NotSync, (const void **)&notSync);
        if(!keyExists || !CFBooleanGetValue(notSync)){
			frameFlags |= VIDEO_FRAME_FLAG_KEYFRAME;
		}
	}else{
		frameFlags |= VIDEO_FRAME_FLAG_KEYFRAME;
	}
	
	Buffer frame(len);
	CMBlockBufferCopyDataBytes(blockBuffer, 0, len, *frame);
	uint32_t offset=0;
	while(offset<len){
		uint32_t nalLen=CFSwapInt32BigToHost(*reinterpret_cast<uint32_t*>(*frame+offset));
		//LOGV("NAL length %u", nalLen);
		frame.CopyFrom(startCode, offset, 4);
		offset+=nalLen+4;
	}
	callback(std::move(frame), frameFlags);
	
	//LOGV("EncoderCallback: %u bytes total", (unsigned int)len);
}

void VideoToolboxEncoderSource::SetEncoderBitrateAndLimit(uint32_t bitrate){
	OSStatus status=VTSessionSetProperty(session, kVTCompressionPropertyKey_AverageBitRate, (__bridge CFTypeRef)@(bitrate));
	CHECK_ERR(status, "VTSessionSetProperty(AverageBitRate)");
	
	int64_t dataLimitValue=(int64_t)(bitrate/8);
	CFNumberRef bytesPerSecond=CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt64Type, &dataLimitValue);
	int64_t oneValue=1;
	CFNumberRef one=CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt64Type, &oneValue);
	const void* numbers[]={bytesPerSecond, one};
	CFArrayRef limits=CFArrayCreate(NULL, numbers, 2, &kCFTypeArrayCallBacks);
	status=VTSessionSetProperty(session, kVTCompressionPropertyKey_DataRateLimits, limits);
	CFRelease(bytesPerSecond);
	CFRelease(one);
	CFRelease(limits);
	CHECK_ERR(status, "VTSessionSetProperty(DataRateLimits");
}

std::vector<uint32_t> VideoToolboxEncoderSource::GetAvailableEncoders(){
	std::vector<uint32_t> res;
	res.push_back(CODEC_AVC);
	CFArrayRef encoders;
	OSStatus status=VTCopyVideoEncoderList(NULL, &encoders);
	for(CFIndex i=0;i<CFArrayGetCount(encoders);i++){
		CFDictionaryRef v=(CFDictionaryRef)CFArrayGetValueAtIndex(encoders, i);
		NSDictionary* encoder=(__bridge NSDictionary*)v;
		//NSString* name=(NSString*)CFDictionaryGetValue(v, kVTVideoEncoderList_EncoderName);
		uint32_t codecType=[(NSNumber*)encoder[(NSString*)kVTVideoEncoderList_CodecType] intValue];
		//LOGV("Encoders[%u]: %s, %c%c%c%c", i, [(NSString*)encoder[(NSString*)kVTVideoEncoderList_EncoderName] cStringUsingEncoding:NSUTF8StringEncoding], PRINT_FOURCC(codecType));
		if(codecType==kCMVideoCodecType_HEVC){
			res.push_back(CODEC_HEVC);
			break;
		}
	}
	CFRelease(encoders);
	return res;
}
