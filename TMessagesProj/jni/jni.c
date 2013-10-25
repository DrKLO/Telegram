#include <stdio.h>
#include <string.h>
#include <jni.h>
#include <sys/types.h>
#include <inttypes.h>
#include <android/log.h>
#include "aes.h"

#define LOG_TAG "tmessages_native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

JNIEXPORT jbyteArray Java_org_telegram_messenger_Utilities_aesIgeEncryption(JNIEnv *env, jclass class, jbyteArray _what, jbyteArray _key, jbyteArray _iv, jboolean encrypt, jboolean changeIv) {
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
    
    int len = (*env)->GetArrayLength(env, _what);
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

//sqlite

/*JNIEXPORT void Java_org_telegram_messenger_Utilities_beginTransaction(JNIEnv* env, jobject object, int dbHandle) {
    sqlite3 *db = (sqlite3 *)dbHandle;
    if (db == NULL) {
        return;
    }
    sqlite3_exec(db, "BEGIN", 0, 0, 0);
}

JNIEXPORT void Java_org_telegram_messenger_Utilities_commitTransaction(JNIEnv* env, jobject object, int dbHandle) {
    sqlite3 *db = (sqlite3 *)dbHandle;
    if (db == NULL) {
        return;
    }
    sqlite3_exec(db, "COMMIT", 0, 0, 0);
}

int Java_org_telegram_messenger_Utilities_step(JNIEnv* env, jobject object, int statementHandle) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	int errcode = 0 ;
    
    errcode = sqlite3_step(handle);
    if (errcode == SQLITE_ROW) {
        return 0;
    } else if(errcode == SQLITE_DONE) {
        return 1;
    } else if(errcode == SQLITE_BUSY) {
        return -1;
    }
    
	throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
}

int Java_org_telegram_messenger_Utilities_columnType(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	return sqlite3_column_type(handle, columnIndex);
}

int Java_org_telegram_messenger_Utilities_columnIsNull(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	int valType = sqlite3_column_type(handle, columnIndex);
    
	return SQLITE_NULL == valType;
}

int Java_org_telegram_messenger_Utilities_columnIntValue(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	int valType = sqlite3_column_type(handle, columnIndex);
	if (SQLITE_NULL == valType) {
		return 0;
	}
    
	return sqlite3_column_int(handle, columnIndex);
}

jdouble Java_org_telegram_messenger_Utilities_columnDoubleValue(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	int valType = sqlite3_column_type(handle, columnIndex);
	if (SQLITE_NULL == valType) {
		return 0;
	}
    
	return sqlite3_column_double(handle, columnIndex);
}

jstring Java_org_telegram_messenger_Utilities_columnStringValue(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	const char* str = sqlite3_column_text(handle, columnIndex);
	if (str != 0) {
		return (*env)->NewStringUTF(env, str);
	}
    
	return 0;
}

jbyteArray Java_org_telegram_messenger_Utilities_columnByteArrayValue(JNIEnv* env, jobject object, int statementHandle, int columnIndex) {
	sqlite3_stmt *handle = (sqlite3_stmt *)statementHandle;
    
	void *buf = sqlite3_column_blob(handle, columnIndex);
	int length = sqlite3_column_bytes(handle, columnIndex);
    
	if (buf != 0 && length > 0) {
        jbyteArray result = (*env)->NewByteArray(env, length);
        (*env)->SetByteArrayRegion(env, result, 0, length, buf);
        return result;
	}
    
	return 0;
}*/
