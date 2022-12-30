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

int registerNativeTgNetFunctions(JavaVM *vm, JNIEnv *env);
int videoOnJNILoad(JavaVM *vm, JNIEnv *env);
int imageOnJNILoad(JavaVM *vm, JNIEnv *env);
int tgvoipOnJNILoad(JavaVM *vm, JNIEnv *env);

jint JNI_OnLoad(JavaVM *vm, void *reserved) {
	JNIEnv *env = 0;
    srand(time(NULL));
    
	if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
		return -1;
	}

    if (imageOnJNILoad(vm, env) != JNI_TRUE) {
        return -1;
    }

    if (videoOnJNILoad(vm, env) != JNI_TRUE) {
        return -1;
    }

    if (registerNativeTgNetFunctions(vm, env) != JNI_TRUE) {
        return -1;
    }

    tgvoipOnJNILoad(vm, env);

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

JNIEXPORT void Java_org_telegram_messenger_Utilities_aesIgeEncryptionByteArray(JNIEnv *env, jclass class, jbyteArray buffer, jbyteArray key, jbyteArray iv, jboolean encrypt, jint offset, jint length) {
    unsigned char *bufferBuff = (unsigned char *) (*env)->GetByteArrayElements(env, buffer, NULL);
    unsigned char *keyBuff = (unsigned char *) (*env)->GetByteArrayElements(env, key, NULL);
    unsigned char *ivBuff = (unsigned char *) (*env)->GetByteArrayElements(env, iv, NULL);

    AES_KEY akey;
    if (!encrypt) {
        AES_set_decrypt_key(keyBuff, 32 * 8, &akey);
        AES_ige_encrypt(bufferBuff, bufferBuff, length, &akey, ivBuff, AES_DECRYPT);
    } else {
        AES_set_encrypt_key(keyBuff, 32 * 8, &akey);
        AES_ige_encrypt(bufferBuff, bufferBuff, length, &akey, ivBuff, AES_ENCRYPT);
    }
    (*env)->ReleaseByteArrayElements(env, key, keyBuff, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, iv, ivBuff, 0);
    (*env)->ReleaseByteArrayElements(env, buffer, bufferBuff, 0);
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

#define LISTDIR_DOCTYPE_ALL 0
#define LISTDIR_DOCTYPE_OTHER_THAN_MUSIC 1
#define LISTDIR_DOCTYPE_MUSIC 2

#define LISTDIR_DOCTYPE2_EMOJI 3
#define LISTDIR_DOCTYPE2_TEMP 4
#define LISTDIR_DOCTYPE2_OTHER 5

int64_t listdir(const char *fileName, int32_t mode, int32_t docType, int64_t time, uint8_t subdirs) {
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
            if (docType > 0 && len > 4) {
                int isMusic = (
                    name[len - 4] == '.' && (
                        ((name[len - 3] == 'm' || name[len - 3] == 'M') && (name[len - 2] == 'p' || name[len - 2] == 'P') && (name[len - 1] == '3')) || // mp3
                        ((name[len - 3] == 'm' || name[len - 3] == 'M') && (name[len - 2] == '4') && (name[len - 1] == 'a' || name[len - 1] == 'A'))    // m4a
                ));
                int isEmoji = (
                    name[len - 4] == '.' && (name[len - 3] == 't' || name[len - 3] == 'T') && (name[len - 2] == 'g' || name[len - 2] == 'G') && (name[len - 1] == 's' || name[len - 1] == 'S') || // tgs
                    len > 5 && name[len - 5] == '.' && (name[len - 4] == 'w' || name[len - 4] == 'W') && (name[len - 3] == 'e' || name[len - 3] == 'E') && (name[len - 2] == 'b' || name[len - 2] == 'B') && (name[len - 1] == 'm' || name[len - 1] == 'M') // webm
                );
                int isTemp = (
                    name[len - 4] == '.' && (name[len - 3] == 't' || name[len - 3] == 'T') && (name[len - 2] == 'm' || name[len - 2] == 'M') && (name[len - 1] == 'p' || name[len - 1] == 'P') || // tmp
                    len > 5 && name[len - 5] == '.' && (name[len - 4] == 't' || name[len - 4] == 'T') && (name[len - 3] == 'e' || name[len - 3] == 'E') && (name[len - 2] == 'm' || name[len - 2] == 'M') && (name[len - 1] == 'p' || name[len - 1] == 'P') || // temp
                    len > 8 && name[len - 8] == '.' && (name[len - 7] == 'p' || name[len - 7] == 'P') && (name[len - 6] == 'r' || name[len - 6] == 'R') && (name[len - 5] == 'e' || name[len - 5] == 'E') && (name[len - 4] == 'l' || name[len - 4] == 'L') && (name[len - 3] == 'o' || name[len - 3] == 'O') && (name[len - 2] == 'a' || name[len - 2] == 'A') && (name[len - 1] == 'd' || name[len - 1] == 'D') // preload
                );
                if (
                    isMusic && docType == LISTDIR_DOCTYPE_OTHER_THAN_MUSIC ||
                    !isMusic && docType == LISTDIR_DOCTYPE_MUSIC ||
                    isEmoji && docType == LISTDIR_DOCTYPE2_OTHER ||
                    !isEmoji && docType == LISTDIR_DOCTYPE2_EMOJI ||
                    isTemp && docType == LISTDIR_DOCTYPE2_OTHER ||
                    !isTemp && docType == LISTDIR_DOCTYPE2_TEMP
                ) {
                    continue;
                }
            }
            strncpy(buff, fileName, 4095);
            strncat(buff, "/", 4095);
            strncat(buff, entry->d_name, 4095);
            if (entry->d_type == DT_DIR) {
                if (subdirs) {
                    value += listdir(buff, mode, docType, time, subdirs);
                }
            } else {
                int rc = stat(buff, &attrib);
                if (mode == 0) {
                    value += rc == 0 ? attrib.st_size : 0;
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

JNIEXPORT jlong Java_org_telegram_messenger_Utilities_getDirSize(JNIEnv *env, jclass class, jstring path, jint docType, jboolean subdirs) {
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    jlong value = listdir(fileName, 0, docType, 0, subdirs);
    (*env)->ReleaseStringUTFChars(env, path, fileName);
    return value;
}

JNIEXPORT jlong Java_org_telegram_messenger_Utilities_getLastUsageFileTime(JNIEnv *env, jclass class, jstring path) {
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    struct stat attrib;
    stat(fileName, &attrib);
    jlong value;
    if (attrib.st_atim.tv_sec != 0) {
        value = attrib.st_atim.tv_sec;
    } else {
        value = attrib.st_mtim.tv_sec;
    }
    (*env)->ReleaseStringUTFChars(env, path, fileName);
    return value;
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_clearDir(JNIEnv *env, jclass class, jstring path, jint docType, jlong time, jboolean subdirs) {
    const char *fileName = (*env)->GetStringUTFChars(env, path, NULL);
    listdir(fileName, 1, docType, time, subdirs);
    (*env)->ReleaseStringUTFChars(env, path, fileName);
}
