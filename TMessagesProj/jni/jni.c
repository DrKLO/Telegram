#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <stdlib.h>
#include <openssl/aes.h>
#include <openssl/evp.h>
#include <unistd.h>
#include <dirent.h>
#include <sys/stat.h>
#include "image.h"

int registerNativeTgNetFunctions(JavaVM *vm, JNIEnv *env);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = 0;
    srand(time(NULL));
    
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}
    
    if (imageOnJNILoad(vm, reserved, env) == -1) {
        return -1;
    }

    if (registerNativeTgNetFunctions(vm, env) != JNI_TRUE) {
        return -1;
    }
    
	return JNI_VERSION_1_6;
}

void JNI_OnUnload(JavaVM *vm, void *reserved) {

}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesIgeEncryption(JNIEnv *env, jclass class, jobject buffer, jbyteArray key, jbyteArray iv, jboolean encrypt, jint offset, jint length) {
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

JNIEXPORT jint Java_org_telegram_messenger_Utilities_pbkdf2(JNIEnv *env, jclass class, jbyteArray password, jbyteArray salt, jbyteArray dst, jint iterations) {
    jbyte *passwordBuff = (*env)->GetByteArrayElements(env, password, NULL);
    size_t passwordLength = (size_t) (*env)->GetArrayLength(env, password);
    jbyte *saltBuff = (*env)->GetByteArrayElements(env, salt, NULL);
    size_t saltLength = (size_t) (*env)->GetArrayLength(env, salt);
    jbyte *dstBuff = (*env)->GetByteArrayElements(env, dst, NULL);
    size_t dstLength = (size_t) (*env)->GetArrayLength(env, dst);

    int result = PKCS5_PBKDF2_HMAC((char *) passwordBuff, passwordLength, (uint8_t *) saltBuff, saltLength, (unsigned int) iterations, EVP_sha512(), dstLength, (uint8_t *) dstBuff);

    (*env)->ReleaseByteArrayElements(env, password, passwordBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, salt, saltBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, dst, dstBuff, 0);

    return result;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesCtrDecryption(JNIEnv *env, jclass class, jobject buffer, jbyteArray key, jbyteArray iv, jint offset, jint length) {
    jbyte *what = (*env)->GetDirectBufferAddress(env, buffer) + offset;
    unsigned char *keyBuff = (unsigned char *)(*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *)(*env)->GetByteArrayElements(env, iv, NULL);

    AES_KEY akey;
    unsigned int num = 0;
    uint8_t count[16];
    memset(count, 0, 16);
    AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
    AES_ctr128_encrypt(what, what, length, &akey, ivBuff, count, &num);
    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, JNI_ABORT);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesCtrDecryptionByteArray(JNIEnv *env, jclass class, jbyteArray buffer, jbyteArray key, jbyteArray iv, jint offset, jint length, jint fileOffset) {
    unsigned char *bufferBuff = (unsigned char *) (*env)->GetByteArrayElements(env, buffer, NULL);
    unsigned char *keyBuff = (unsigned char *) (*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *) (*env)->GetByteArrayElements(env, iv, NULL);

    AES_KEY akey;
    uint8_t count[16];
    AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
    unsigned int num = (unsigned int) (fileOffset % 16);

    int o = fileOffset / 16;
    ivBuff[15] = (uint8_t) (o & 0xff);
    ivBuff[14] = (uint8_t) ((o >> 8) & 0xff);
    ivBuff[13] = (uint8_t) ((o >> 16) & 0xff);
    ivBuff[12] = (uint8_t) ((o >> 24) & 0xff);
    AES_encrypt(ivBuff, count, &akey);

    o = (fileOffset + 15) / 16;
    ivBuff[15] = (uint8_t) (o & 0xff);
    ivBuff[14] = (uint8_t) ((o >> 8) & 0xff);
    ivBuff[13] = (uint8_t) ((o >> 16) & 0xff);
    ivBuff[12] = (uint8_t) ((o >> 24) & 0xff);

    AES_ctr128_encrypt(bufferBuff + offset, bufferBuff + offset, length, &akey, ivBuff, count, &num);

    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, buffer, bufferBuff, 0);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesCbcEncryptionByteArray(JNIEnv *env, jclass class, jbyteArray buffer, jbyteArray key, jbyteArray iv, jint offset, jint length, jint fileOffset, jint encrypt) {
    unsigned char *bufferBuff = (unsigned char *) (*env)->GetByteArrayElements(env, buffer, NULL);
    unsigned char *keyBuff = (unsigned char *) (*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *) (*env)->GetByteArrayElements(env, iv, NULL);

    AES_KEY akey;
    if (encrypt) {
        AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
    } else {
        AES_set_decrypt_key(keyBuff, 32 * 8, &akey);

        if (fileOffset != 0) {
            int o = (fileOffset + 15) / 16;
            ivBuff[15] = (uint8_t) (o & 0xff);
            ivBuff[14] = (uint8_t) ((o >> 8) & 0xff);
            ivBuff[13] = (uint8_t) ((o >> 16) & 0xff);
            ivBuff[12] = (uint8_t) ((o >> 24) & 0xff);
        }
    }

    AES_cbc_encrypt(bufferBuff, bufferBuff, length, &akey, ivBuff, encrypt);

    (*env)->ReleaseByteArrayElements(env, buffer, bufferBuff, 0);
    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, JNI_ABORT);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesCbcEncryption(JNIEnv *env, jclass class, jobject buffer, jbyteArray key, jbyteArray iv, jint offset, jint length, jint encrypt) {
    unsigned char *bufferBuff = (*env)->GetDirectBufferAddress(env, buffer) + offset;
    unsigned char *keyBuff = (unsigned char *) (*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *) (*env)->GetByteArrayElements(env, iv, NULL);

    AES_KEY akey;
    if (encrypt) {
        AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
    } else {
        AES_set_decrypt_key(keyBuff, 32 * 8, &akey);
    }

    AES_cbc_encrypt(bufferBuff + offset, bufferBuff + offset, length, &akey, ivBuff, encrypt);

    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, JNI_ABORT);
}

JNIEXPORT jstring Java_org_telegram_messenger_Utilities_readlink(JNIEnv *env, jclass class, jstring path) {
    static char buf[1000];
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    ssize_t result = readlink(fileName, buf, 999);
    jstring value = 0;
    if (result != -1) {
        buf[result] = '\0';
        value = (*env)->NewStringUTF(env, buf);
    }
    (*env)->ReleaseStringUTFChars(env, path, fileName);
    return value;
}

int64_t listdir(const char *fileName, int32_t mode, int32_t docType, int64_t time) {
    int64_t value = 0;
    DIR *dir;
    struct stat attrib;
    if ((dir = opendir(fileName)) != NULL) {
        char buff[4096];
        struct dirent *entry;
        while ((entry = readdir(dir)) != NULL) {
            char *name = entry->d_name;
            size_t len = strlen(name);
            if (name[0] == '.') {
                continue;
            }
            if ((docType == 1 || docType == 2) && len > 4) {
                if (name[len - 4] == '.' && (
                        ((name[len - 3] == 'm' || name[len - 3] == 'M') && (name[len - 2] == 'p' || name[len - 2] == 'P') && (name[len - 1] == '3')) ||
                        ((name[len - 3] == 'm' || name[len - 3] == 'M') && (name[len - 2] == '4') && (name[len - 1] == 'a' || name[len - 1] == 'A'))
                )) {
                    if (docType == 1) {
                        continue;
                    }
                } else if (docType == 2) {
                    continue;
                }
            }
            strncpy(buff, fileName, 4095);
            strncat(buff, "/", 4095);
            strncat(buff, entry->d_name, 4095);
            if (entry->d_type == DT_DIR) {
                value += listdir(buff, mode, docType, time);
            } else {
                stat(buff, &attrib);
                if (mode == 0) {
                    value += attrib.st_size;
                } else if (mode == 1) {
                    if (attrib.st_atim.tv_sec != 0) {
                        if (attrib.st_atim.tv_sec < time) {
                            remove(buff);
                        }
                    } else {
                        if (attrib.st_mtim.tv_sec < time) {
                            remove(buff);
                        }
                    }
                }
            }
        }
        closedir(dir);
    }
    return value;
}

JNIEXPORT jlong Java_org_telegram_messenger_Utilities_getDirSize(JNIEnv *env, jclass class, jstring path, jint docType) {
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    jlong value = listdir(fileName, 0, docType, 0);
    (*env)->ReleaseStringUTFChars(env, path, fileName);
    return value;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_clearDir(JNIEnv *env, jclass class, jstring path, jint docType, jlong time) {
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    listdir(fileName, 1, docType, time);
    (*env)->ReleaseStringUTFChars(env, path, fileName);
}
