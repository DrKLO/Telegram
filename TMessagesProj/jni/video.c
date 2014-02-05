#include <jni.h>
#include "video.h"

JNIEXPORT void Java_org_telegram_messenger_VideoTools_initialize(JNIEnv* env, jclass class) {
    av_register_all();
}

JNIEXPORT void Java_org_telegram_messenger_VideoTools_convert(JNIEnv* env, jclass class, jstring in, jstring out, int bitr) {
    char const *in_str = (*env)->GetStringUTFChars(env, in, 0);
    char const *out_str = (*env)->GetStringUTFChars(env, out, 0);
    convertFile(in_str, out_str, bitr);
    if (in_str != 0) {
        (*env)->ReleaseStringUTFChars(env, in, in_str);
    }
    if (out_str != 0) {
        (*env)->ReleaseStringUTFChars(env, out, out_str);
    } 
}
