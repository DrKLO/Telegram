//
// Created by Grishka on 12.08.2018.
//

#include "VideoRendererAndroid.h"
#include "JNIUtilities.h"
#include "../../PrivateDefines.h"
#include "../../logging.h"

using namespace tgvoip;
using namespace tgvoip::video;

jmethodID VideoRendererAndroid::resetMethod=NULL;
jmethodID VideoRendererAndroid::decodeAndDisplayMethod=NULL;
jmethodID VideoRendererAndroid::setStreamEnabledMethod=NULL;
jmethodID VideoRendererAndroid::setRotationMethod=NULL;
std::vector<uint32_t> VideoRendererAndroid::availableDecoders;
int VideoRendererAndroid::maxResolution;

extern JavaVM* sharedJVM;

VideoRendererAndroid::VideoRendererAndroid(jobject jobj) : queue(50){
	this->jobj=jobj;
}

VideoRendererAndroid::~VideoRendererAndroid(){
	running=false;
	Request req{
		Buffer(0),
		Request::Type::Shutdown
	};
	queue.Put(std::move(req));
	thread->Join();
	delete thread;
	/*decoderThread.Post([this]{
		decoderEnv->DeleteGlobalRef(jobj);
	});*/
}

void VideoRendererAndroid::Reset(uint32_t codec, unsigned int width, unsigned int height, std::vector<Buffer> &_csd){
	csd.clear();
	for(Buffer& b:_csd){
		csd.push_back(Buffer::CopyOf(b));
	}
	this->codec=codec;
	this->width=width;
	this->height=height;
	Request req{
			Buffer(0),
			Request::Type::ResetDecoder
	};
	queue.Put(std::move(req));
	Request req2{
		Buffer(0),
		Request::Type::UpdateStreamState
	};
	queue.Put(std::move(req2));
	
	if(!thread){
		thread=new Thread(std::bind(&VideoRendererAndroid::RunThread, this));
		thread->Start();
	}
}

void VideoRendererAndroid::DecodeAndDisplay(Buffer frame, uint32_t pts){
	/*decoderThread.Post(std::bind([this](Buffer frame){
	}, std::move(frame)));*/
	//LOGV("2 before decode %u", (unsigned int)frame.Length());
	Request req{
		std::move(frame),
		Request::Type::DecodeFrame
	};
	queue.Put(std::move(req));
}

void VideoRendererAndroid::RunThread(){
	JNIEnv* env;
	sharedJVM->AttachCurrentThread(&env, NULL);

	constexpr size_t bufferSize=200*1024;
	unsigned char* buf=reinterpret_cast<unsigned char*>(malloc(bufferSize));
	jobject jbuf=env->NewDirectByteBuffer(buf, bufferSize);
	uint16_t lastSetRotation=0;

	while(running){
		//LOGV("before get from queue");
		Request request=std::move(queue.GetBlocking());
		//LOGV("1 before decode %u", (unsigned int)request.Length());
		if(request.type==Request::Type::Shutdown){
			LOGI("Shutting down video decoder thread");
			break;
		}else if(request.type==Request::Type::DecodeFrame){
			if(request.buffer.Length()>bufferSize){
				LOGE("Frame data is too long (%u, max %u)", (int) request.buffer.Length(), (int) bufferSize);
			}else{
				if(lastSetRotation!=rotation){
					lastSetRotation=rotation;
					env->CallVoidMethod(jobj, setRotationMethod, (jint)rotation);
				}
				memcpy(buf, *request.buffer, request.buffer.Length());
				env->CallVoidMethod(jobj, decodeAndDisplayMethod, jbuf, (jint) request.buffer.Length(), 0);
			}
		}else if(request.type==Request::Type::ResetDecoder){
			jobjectArray jcsd=NULL;
			if(!csd.empty()){
				jcsd=env->NewObjectArray((jsize)csd.size(), env->FindClass("[B"), NULL);
				jsize i=0;
				for(Buffer& b:csd){
					env->SetObjectArrayElement(jcsd, i, jni::BufferToByteArray(env, b));
					i++;
				}
			}
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
			env->CallVoidMethod(jobj, resetMethod, env->NewStringUTF(codecStr.c_str()), (jint)width, (jint)height, jcsd);
		}else if(request.type==Request::Type::UpdateStreamState){
			env->CallVoidMethod(jobj, setStreamEnabledMethod, streamEnabled);
		}
	}
	free(buf);
	sharedJVM->DetachCurrentThread();
	LOGI("==== decoder thread exiting ====");
}

void VideoRendererAndroid::SetStreamEnabled(bool enabled){
	LOGI("Video stream state: %d", enabled);
	streamEnabled=enabled;
	Request req{
			Buffer(0),
			Request::Type::UpdateStreamState
	};
	queue.Put(std::move(req));
}

void VideoRendererAndroid::SetRotation(uint16_t rotation){
	this->rotation=rotation;
}
