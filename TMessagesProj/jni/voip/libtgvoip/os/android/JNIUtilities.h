//
// Created by Grishka on 15.08.2018.
//

#ifndef LIBTGVOIP_JNIUTILITIES_H
#define LIBTGVOIP_JNIUTILITIES_H

#include <functional>
#include <jni.h>
#include <stdarg.h>
#include <string>
#include "../../Buffers.h"

extern JavaVM* sharedJVM;

namespace tgvoip{
	namespace jni{

		inline JNIEnv *GetEnv() {
			JNIEnv *env = nullptr;
			sharedJVM->GetEnv((void **) &env, JNI_VERSION_1_6);
			return env;
		}

		inline void DoWithJNI(std::function<void(JNIEnv*)> f){
			JNIEnv *env=GetEnv();
			bool didAttach=false;
			if(!env){
				sharedJVM->AttachCurrentThread(&env, NULL);
				didAttach=true;
			}

			f(env);

			if(didAttach){
				sharedJVM->DetachCurrentThread();
			}
		}

		inline void AttachAndCallVoidMethod(jmethodID method, jobject obj, ...){
			if(!method || !obj)
				return;
			va_list va;
			va_start(va, obj);
			DoWithJNI([&va, method, obj](JNIEnv* env){
				env->CallVoidMethodV(obj, method, va);
			});
			va_end(va);
		}

		inline std::string JavaStringToStdString(JNIEnv* env, jstring jstr){
			if(!jstr)
				return "";
			const char* jchars=env->GetStringUTFChars(jstr, NULL);
			std::string str(jchars);
			env->ReleaseStringUTFChars(jstr, jchars);
			return str;
		}

		inline jbyteArray BufferToByteArray(JNIEnv* env, Buffer& buf){
			jbyteArray arr=env->NewByteArray((jsize)buf.Length());
			jbyte* elements=env->GetByteArrayElements(arr, NULL);
			memcpy(elements, *buf, buf.Length());
			env->ReleaseByteArrayElements(arr, elements, 0);
			return arr;
		}
	}
}

#endif //LIBTGVOIP_JNIUTILITIES_H
