//
// Created by Grishka on 12.08.2018.
//

#include "VideoSourceAndroid.h"
#include "JNIUtilities.h"
#include "../../logging.h"
#include "../../PrivateDefines.h"

using namespace tgvoip;
using namespace tgvoip::video;

extern JavaVM* sharedJVM;

std::vector<uint32_t> VideoSourceAndroid::availableEncoders;

VideoSourceAndroid::VideoSourceAndroid(jobject jobj) : javaObject(jobj){
	jni::DoWithJNI([&](JNIEnv* env){
		jclass cls=env->GetObjectClass(javaObject);
		startMethod=env->GetMethodID(cls, "start", "()V");
		stopMethod=env->GetMethodID(cls, "stop", "()V");
		prepareEncoderMethod=env->GetMethodID(cls, "prepareEncoder", "(Ljava/lang/String;I)V");
		requestKeyFrameMethod=env->GetMethodID(cls, "requestKeyFrame", "()V");
		setBitrateMethod=env->GetMethodID(cls, "setBitrate", "(I)V");
	});
}

VideoSourceAndroid::~VideoSourceAndroid(){
	jni::DoWithJNI([this](JNIEnv* env){
		env->DeleteGlobalRef(javaObject);
	});
}

void VideoSourceAndroid::Start(){
	jni::DoWithJNI([this](JNIEnv* env){
		env->CallVoidMethod(javaObject, startMethod);
	});
}

void VideoSourceAndroid::Stop(){
	jni::DoWithJNI([this](JNIEnv* env){
		env->CallVoidMethod(javaObject, stopMethod);
	});
}

void VideoSourceAndroid::SendFrame(Buffer frame, uint32_t flags){
	callback(frame, flags, rotation);
}

void VideoSourceAndroid::SetStreamParameters(std::vector<Buffer> csd, unsigned int width, unsigned int height){
	LOGD("Video stream parameters: %d x %d", width, height);
	this->width=width;
	this->height=height;
	this->csd=std::move(csd);
}

void VideoSourceAndroid::Reset(uint32_t codec, int maxResolution){
	jni::DoWithJNI([&](JNIEnv* env){
		std::string codecStr="";
		switch(codec){
			case CODEC_AVC:
				codecStr="video/avc";
				break;
			case CODEC_HEVC:
				codecStr="video/hevc";
				break;
			case CODEC_VP8:
				codecStr="video/x-vnd.on2.vp8";
				break;
			case CODEC_VP9:
				codecStr="video/x-vnd.on2.vp9";
				break;
		}
		env->CallVoidMethod(javaObject, prepareEncoderMethod, env->NewStringUTF(codecStr.c_str()), maxResolution);
	});
}

void VideoSourceAndroid::RequestKeyFrame(){
	jni::DoWithJNI([this](JNIEnv* env){
		env->CallVoidMethod(javaObject, requestKeyFrameMethod);
	});
}

void VideoSourceAndroid::SetBitrate(uint32_t bitrate){
	jni::DoWithJNI([&](JNIEnv* env){
		env->CallVoidMethod(javaObject, setBitrateMethod, (jint)bitrate);
	});
}
