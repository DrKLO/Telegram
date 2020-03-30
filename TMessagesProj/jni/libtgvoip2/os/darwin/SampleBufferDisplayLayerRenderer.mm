//
// libtgvoip is free and unencumbered public domain software.
// For more information, see http://unlicense.org or the UNLICENSE file
// you should have received with this source code distribution.
//

#include <TargetConditionals.h>
#if TARGET_OS_IPHONE
#include <UIKit/UIKit.h>
#endif
#include "SampleBufferDisplayLayerRenderer.h"
#include "../../PrivateDefines.h"
#include "../../logging.h"
#include "TGVVideoRenderer.h"

using namespace tgvoip;
using namespace tgvoip::video;

SampleBufferDisplayLayerRenderer::SampleBufferDisplayLayerRenderer(TGVVideoRenderer* renderer) : renderer(renderer){

}

SampleBufferDisplayLayerRenderer::~SampleBufferDisplayLayerRenderer(){

}

void SampleBufferDisplayLayerRenderer::Reset(uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer>& csd){
	LOGI("video renderer reset: %d x %d", width, height);
	if(formatDesc){
		CFRelease(formatDesc);
	}
	if(codec==CODEC_AVC){
		if(csd.size()!=2){
			LOGE("H264 requires exactly 2 CSD buffers");
			return;
		}
		const uint8_t* params[]={*csd[0]+4, *csd[1]+4};
		size_t paramSizes[]={csd[0].Length()-4, csd[1].Length()-4};
		OSStatus status=CMVideoFormatDescriptionCreateFromH264ParameterSets(NULL, 2, params, paramSizes, 4, &formatDesc);
		if(status!=noErr){
			LOGE("CMVideoFormatDescriptionCreateFromH264ParameterSets failed: %d", status);
			return;
		}
		CGRect rect=CMVideoFormatDescriptionGetCleanAperture(formatDesc, true);
		LOGI("size from formatDesc: %f x %f", rect.size.width, rect.size.height);
	}else if(codec==CODEC_HEVC){
		if(@available(iOS 11.0, *)){
    		if(csd.size()!=1){
    			LOGE("HEVC requires exactly 1 CSD buffer");
    			return;
    		}
    		int offsets[]={0, 0, 0};
    		Buffer& buf=csd[0];
    		int currentNAL=0;
    		for(int i=0;i<buf.Length()-4;i++){
    			if(buf[i]==0 && buf[i+1]==0 && buf[i+2]==0 && buf[i+3]==1){
    				offsets[currentNAL]=i+4;
    				currentNAL++;
    			}
    		}
    		LOGV("CSD NAL offsets: %d %d %d", offsets[0], offsets[1], offsets[2]);
    		if(offsets[0]==0 || offsets[1]==0 || offsets[2]==0){
    			LOGE("Error splitting CSD buffer");
    			return;
    		}
    		const uint8_t* params[]={*buf+offsets[0], *buf+offsets[1], *buf+offsets[2]};
    		size_t paramSizes[]={(size_t)((offsets[1]-4)-offsets[0]), (size_t)((offsets[2]-4)-offsets[1]), (size_t)(buf.Length()-offsets[2])};
    		OSStatus status=CMVideoFormatDescriptionCreateFromHEVCParameterSets(NULL, 3, params, paramSizes, 4, NULL, &formatDesc);
    		if(status!=noErr){
				LOGE("CMVideoFormatDescriptionCreateFromHEVCParameterSets failed: %d", status);
				return;
			}
    		CGRect rect=CMVideoFormatDescriptionGetCleanAperture(formatDesc, true);
    		LOGI("size from formatDesc: %f x %f", rect.size.width, rect.size.height);
			
		}else{
			LOGE("HEVC not available on this OS");
		}
	}
	[renderer _setSizeWidth:(uint16_t)width height:(uint16_t)height];
	needReset=true;
}

void SampleBufferDisplayLayerRenderer::DecodeAndDisplay(Buffer frame, uint32_t pts){

	std::vector<uint32_t> nalStartOffsets;
	uint8_t* _data=*frame;
	for(uint32_t offset=0;offset<frame.Length()-4;offset++){
		if(_data[offset]==0 && _data[offset+1]==0 && ((_data[offset+2]==0 && _data[offset+3]==1) || _data[offset+2]==1)){
			nalStartOffsets.push_back(offset+(_data[offset+2]==1 ? 3 : 4));
			offset+=4;
		}
	}
	BufferOutputStream out(frame.Length());
	for(uint32_t i=0;i<nalStartOffsets.size();i++){
		uint32_t length=(i==nalStartOffsets.size()-1 ? (uint32_t)frame.Length() : nalStartOffsets[i+1])-nalStartOffsets[i];
    	uint8_t lenBytes[]={(uint8_t)(length >> 24), (uint8_t)(length >> 16), (uint8_t)(length >> 8), (uint8_t)length};
    	out.WriteBytes(lenBytes, 4);
    	out.WriteBytes(frame, nalStartOffsets[i], length);
	}

	CMBlockBufferRef blockBuffer;

	OSStatus status=CMBlockBufferCreateWithMemoryBlock(kCFAllocatorDefault, out.GetBuffer(), out.GetLength(), kCFAllocatorNull, NULL, 0, out.GetLength(), 0, &blockBuffer);
	if(status!=noErr){
		LOGE("CMBlockBufferCreateWithMemoryBlock failed: %d", status);
		return;
	}
	CMSampleBufferRef sampleBuffer;
	status=CMSampleBufferCreate(kCFAllocatorDefault, blockBuffer, true, NULL, NULL, formatDesc, 1, 0, NULL, 0, NULL, &sampleBuffer);
	if(status!=noErr){
		LOGE("CMSampleBufferCreate failed: %d", status);
		return;
	}

	CFRelease(blockBuffer);
	CFArrayRef attachments=CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, true);
	CFMutableDictionaryRef dict=(CFMutableDictionaryRef)CFArrayGetValueAtIndex(attachments, 0);
	CFDictionarySetValue(dict, kCMSampleAttachmentKey_DisplayImmediately, kCFBooleanTrue);

	[renderer _enqueueBuffer:sampleBuffer reset:needReset];
	needReset=false;
	CFRelease(sampleBuffer);
}

void SampleBufferDisplayLayerRenderer::SetStreamEnabled(bool enabled){
	if(enabled!=streamEnabled){
		streamEnabled=enabled;
		if(enabled){
			[renderer _setResumed];
		}else{
			[renderer _setStopped];
		}
	}
}

int SampleBufferDisplayLayerRenderer::GetMaximumResolution(){
#if TARGET_OS_IPHONE
	CGRect screenSize=[UIScreen mainScreen].nativeBounds;
	CGFloat minSize=std::min(screenSize.size.width, screenSize.size.height);
	if(minSize>720.f){
		return INIT_VIDEO_RES_1080;
	}else if(minSize>480.f){
		return INIT_VIDEO_RES_720;
	}else{
		return INIT_VIDEO_RES_480;
	}
#else // OS X
	// TODO support OS X
#endif
	return INIT_VIDEO_RES_1080;
}

void SampleBufferDisplayLayerRenderer::SetRotation(uint16_t rotation){
	[renderer _setRotation:rotation];
}

void SampleBufferDisplayLayerRenderer::SetStreamPaused(bool paused){
	if(paused){
		[renderer _setPaused];
	}else if(streamEnabled){
		[renderer _setResumed];
	}
}

std::vector<uint32_t> SampleBufferDisplayLayerRenderer::GetAvailableDecoders(){
	std::vector<uint32_t> res;
	res.push_back(CODEC_AVC);
	if(@available(iOS 11.0, *)){
		if(VTIsHardwareDecodeSupported(kCMVideoCodecType_HEVC)){
			res.push_back(CODEC_HEVC);
		}
	}
	return res;
}
