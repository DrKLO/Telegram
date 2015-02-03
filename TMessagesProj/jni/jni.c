#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <stdlib.h>
#include "aes/aes.h"
#include "utils.h"
#include "sqlite.h"
#include "gif.h"
#include "image.h"

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = 0;
    srand(time(NULL));
    
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
    
    if (sqliteOnJNILoad(vm, reserved, env) == -1) {
        return -1;
    }
    
    if (imageOnJNILoad(vm, reserved, env) == -1) {
        return -1;
    }
    
    gifOnJNILoad(vm, reserved, env);
    
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {
    gifOnJNIUnload(vm, reserved);
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

uint64_t gcd(uint64_t a, uint64_t b){
    while(a != 0 && b != 0) {
        while((b & 1) == 0) b >>= 1;
        while((a & 1) == 0) a >>= 1;
        if(a > b) a -= b; else b -= a;
    }
    return b == 0 ? a : b;
}

JNIEXPORT jlong Java_org_telegram_messenger_Utilities_doPQNative(JNIEnv* env, jclass class, jlong _what) {
    uint64_t what = _what;
    int it = 0, i, j;
    uint64_t g = 0;
    for (i = 0; i < 3 || it < 1000; i++){
        int q = ((lrand48() & 15) + 17) % what;
        uint64_t x = (long long)lrand48() % (what - 1) + 1, y = x;
        int lim = 1 << (i + 18), j;
        for(j = 1; j < lim; j++){
            ++it;
            uint64_t a = x, b = x, c = q;
            while(b){
                if(b & 1){
                    c += a;
                    if(c >= what) c -= what;
                }
                a += a;
                if(a >= what) a -= what;
                b >>= 1;
            }
            x = c;
            uint64_t z = x < y ? what + x - y : x - y;
            g = gcd(z, what);
            if(g != 1) break;
            if(!(j & (j - 1))) y = x;
        }
        if(g > 1 && g < what) break;
    }
    return g;
}
