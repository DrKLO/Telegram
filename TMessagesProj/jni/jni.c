#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <stdlib.h>
#include <openssl/aes.h>
#include <unistd.h>
#include "utils.h"
#include "image.h"

int registerNativeTgNetFunctions(JavaVM *vm, JNIEnv *env);
int gifvideoOnJNILoad(JavaVM *vm, JNIEnv *env);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = 0;
    srand(time(NULL));
    
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
    
    if (imageOnJNILoad(vm, reserved, env) == -1) {
        return -1;
    }
    
    if (gifvideoOnJNILoad(vm, env) == -1) {
        return -1;
    }

    if (registerNativeTgNetFunctions(vm, env) != JNI_TRUE) {
        return -1;
    }
    
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesIgeEncryption(JNIEnv *env, jclass class, jobject buffer, jbyteArray key, jbyteArray iv, jboolean encrypt, int offset, int length) {
    jbyte *what = (*env)->GetDirectBufferAddress(env, buffer) + offset;
    unsigned char *keyBuff = (unsigned char *)(*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *)(*env)->GetByteArrayElements(env, iv, NULL);
    
    AES_KEY akey;
    if (!encrypt) {
        AES_set_decrypt_key(keyBuff, 32 * 8, &akey);
        AES_ige_encrypt(what, what, length, &akey, ivBuff, AES_DECRYPT);
    } else {
        AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
        AES_ige_encrypt(what, what, length, &akey, ivBuff, AES_ENCRYPT);
    }
    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, 0);
}

JNIEXPORT jstring Java_org_telegram_messenger_Utilities_readlink(JNIEnv *env, jclass class, jstring path) {
    static char buf[1000];
    char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    int result = readlink(fileName, buf, 999);
    jstring value = 0;
    if (result != -1) {
        buf[result] = '\0';
        value = (*env)->NewStringUTF(env, buf);
    }
    (*env)->ReleaseStringUTFChars(env, path, fileName);
    return value;
}
