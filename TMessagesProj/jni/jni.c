#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <android/log.h>
#include "aes.h"
#include "log.h"

JNIEXPORT jbyteArray Java_org_telegram_messenger_Utilities_aesIgeEncryption(JNIEnv *env, jclass class, jbyteArray _what, jbyteArray _key, jbyteArray _iv, jboolean encrypt, jboolean changeIv, jint l) {
    unsigned char *what = (unsigned char *)(*env)->GetByteArrayElements(env, _what, NULL);
    unsigned char *key = (unsigned char *)(*env)->GetByteArrayElements(env, _key, NULL);
    unsigned char *__iv = (unsigned char *)(*env)->GetByteArrayElements(env, _iv, NULL);
    unsigned char *iv = 0;
    
    if (!changeIv) {
        iv = (unsigned char *)malloc((*env)->GetArrayLength(env, _iv));
        memcpy(iv, __iv, (*env)->GetArrayLength(env, _iv));
    } else {
        iv = __iv;
    }
    
    int len = l == 0 ? (*env)->GetArrayLength(env, _what) : l;
    AES_KEY akey;
    if (!encrypt) {
        AES_set_decrypt_key(key, (*env)->GetArrayLength(env, _key) * 8, &akey);
        AES_ige_encrypt(what, what, len, &akey, iv, AES_DECRYPT);
    } else {
        AES_set_encrypt_key(key, (*env)->GetArrayLength(env, _key) * 8, &akey);
        AES_ige_encrypt(what, what, len, &akey, iv, AES_ENCRYPT);
    }
    (*env)->ReleaseByteArrayElements(env, _what, what, 0);
    (*env)->ReleaseByteArrayElements(env, _key, key, JNI_ABORT);
    if (!changeIv) {
        (*env)->ReleaseByteArrayElements(env, _iv, __iv, JNI_ABORT);
        free(iv);
    } else {
        (*env)->ReleaseByteArrayElements(env, _iv, __iv, 0);
    }
    return _what;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesIgeEncryption2(JNIEnv *env, jclass class, jobject _what, jbyteArray _key, jbyteArray _iv, jboolean encrypt, jboolean changeIv, jint l) {
    jbyte *what = (*env)->GetDirectBufferAddress(env, _what);
    unsigned char *key = (unsigned char *)(*env)->GetByteArrayElements(env, _key, NULL);
    unsigned char *__iv = (unsigned char *)(*env)->GetByteArrayElements(env, _iv, NULL);
    unsigned char *iv = 0;
    
    if (!changeIv) {
        iv = (unsigned char *)malloc((*env)->GetArrayLength(env, _iv));
        memcpy(iv, __iv, (*env)->GetArrayLength(env, _iv));
    } else {
        iv = __iv;
    }
    
    AES_KEY akey;
    if (!encrypt) {
        AES_set_decrypt_key(key, (*env)->GetArrayLength(env, _key) * 8, &akey);
        AES_ige_encrypt(what, what, l, &akey, iv, AES_DECRYPT);
    } else {
        AES_set_encrypt_key(key, (*env)->GetArrayLength(env, _key) * 8, &akey);
        AES_ige_encrypt(what, what, l, &akey, iv, AES_ENCRYPT);
    }
    (*env)->ReleaseByteArrayElements(env, _key, key, JNI_ABORT);
    if (!changeIv) {
        (*env)->ReleaseByteArrayElements(env, _iv, __iv, JNI_ABORT);
        free(iv);
    } else {
        (*env)->ReleaseByteArrayElements(env, _iv, __iv, 0);
    }
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
