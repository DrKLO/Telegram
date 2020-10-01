#include <cstring>
#include <jni.h>
#include "sqlite/sqlite3.h"
#include "tgnet/NativeByteBuffer.h"
#include "tgnet/BuffersStorage.h"

void throw_sqlite3_exception(JNIEnv *env, sqlite3 *handle, int errcode) {
    const char *errmsg = sqlite3_errmsg(handle);
    jclass exClass = env->FindClass("org/telegram/SQLite/SQLiteException");
    env->ThrowNew(exClass, errmsg);
}

extern "C" {

JNIEXPORT jint Java_org_telegram_SQLite_SQLitePreparedStatement_step(JNIEnv *env, jobject object, jlong statementHandle) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_step(handle);
    if (errcode == SQLITE_ROW) {
        return 0;
    } else if (errcode == SQLITE_DONE) {
        return 1;
    } else if (errcode == SQLITE_BUSY) {
        return -1;
    }
    throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);

    return 0;
}

JNIEXPORT jlong Java_org_telegram_SQLite_SQLitePreparedStatement_prepare(JNIEnv *env, jobject object, jlong sqliteHandle, jstring sql) {
    sqlite3 *handle = (sqlite3 *) (intptr_t) sqliteHandle;

    char const *sqlStr = env->GetStringUTFChars(sql, 0);

    sqlite3_stmt *stmt_handle;

    int errcode = sqlite3_prepare_v2(handle, sqlStr, -1, &stmt_handle, 0);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, handle, errcode);
    }

    if (sqlStr != 0) {
        env->ReleaseStringUTFChars(sql, sqlStr);
    }

    return (jlong) stmt_handle;
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_reset(JNIEnv *env, jobject object, jlong statementHandle) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_reset(handle);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_finalize(JNIEnv *env, jobject object, jlong statementHandle) {
    sqlite3_finalize((sqlite3_stmt *) (intptr_t) statementHandle);
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindByteBuffer(JNIEnv *env, jobject object, jlong statementHandle, jint index, jobject value, jint length) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    void *buf = env->GetDirectBufferAddress(value);

    int errcode = sqlite3_bind_blob(handle, index, buf, length, SQLITE_STATIC);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindString(JNIEnv *env, jobject object, jlong statementHandle, jint index, jstring value) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    char const *valueStr = env->GetStringUTFChars(value, 0);

    int errcode = sqlite3_bind_text(handle, index, valueStr, -1, SQLITE_TRANSIENT);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }

    if (valueStr != 0) {
        env->ReleaseStringUTFChars(value, valueStr);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindInt(JNIEnv *env, jobject object, jlong statementHandle, jint index, jint value) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_bind_int(handle, index, value);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindLong(JNIEnv *env, jobject object, jlong statementHandle, jint index, jlong value) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_bind_int64(handle, index, value);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindDouble(JNIEnv *env, jobject object, jlong statementHandle, jint index, double value) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_bind_double(handle, index, value);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLitePreparedStatement_bindNull(JNIEnv *env, jobject object, jlong statementHandle, jint index) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;

    int errcode = sqlite3_bind_null(handle, index);
    if (SQLITE_OK != errcode) {
        throw_sqlite3_exception(env, sqlite3_db_handle(handle), errcode);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLiteDatabase_closedb(JNIEnv *env, jobject object, jlong sqliteHandle) {
    sqlite3 *handle = (sqlite3 *) (intptr_t) sqliteHandle;
    int err = sqlite3_close(handle);
    if (SQLITE_OK != err) {
        throw_sqlite3_exception(env, handle, err);
    }
}

JNIEXPORT void Java_org_telegram_SQLite_SQLiteDatabase_beginTransaction(JNIEnv *env, jobject object, jlong sqliteHandle) {
    sqlite3 *handle = (sqlite3 *) (intptr_t) sqliteHandle;
    sqlite3_exec(handle, "BEGIN", 0, 0, 0);
}

JNIEXPORT void Java_org_telegram_SQLite_SQLiteDatabase_commitTransaction(JNIEnv *env, jobject object, jlong sqliteHandle) {
    sqlite3 *handle = (sqlite3 *) (intptr_t) sqliteHandle;
    sqlite3_exec(handle, "COMMIT", 0, 0, 0);
}

JNIEXPORT jlong Java_org_telegram_SQLite_SQLiteDatabase_opendb(JNIEnv *env, jobject object, jstring fileName, jstring tempDir) {
    char const *fileNameStr = env->GetStringUTFChars(fileName, 0);
    char const *tempDirStr = env->GetStringUTFChars(tempDir, 0);

    if (sqlite3_temp_directory != 0 && strcmp(sqlite3_temp_directory, tempDirStr)) {
        sqlite3_free(sqlite3_temp_directory);
    }
    if (sqlite3_temp_directory == 0) {
        sqlite3_temp_directory = sqlite3_mprintf("%s", tempDirStr);
    }

    sqlite3 *handle = 0;
    int err = sqlite3_open(fileNameStr, &handle);
    if (SQLITE_OK != err) {
        throw_sqlite3_exception(env, handle, err);
    }
    if (fileNameStr != 0) {
        env->ReleaseStringUTFChars(fileName, fileNameStr);
    }
    if (tempDirStr != 0) {
        env->ReleaseStringUTFChars(tempDir, tempDirStr);
    }
    return (jlong) handle;
}

JNIEXPORT jint Java_org_telegram_SQLite_SQLiteCursor_columnCount(JNIEnv *env, jobject object, jlong statementHandle) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    return sqlite3_column_count(handle);
}

JNIEXPORT jint Java_org_telegram_SQLite_SQLiteCursor_columnType(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    return sqlite3_column_type(handle, columnIndex);
}

JNIEXPORT jint Java_org_telegram_SQLite_SQLiteCursor_columnIsNull(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    int valType = sqlite3_column_type(handle, columnIndex);
    return SQLITE_NULL == valType ? 1 : 0;
}

JNIEXPORT jint Java_org_telegram_SQLite_SQLiteCursor_columnIntValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    int valType = sqlite3_column_type(handle, columnIndex);
    if (SQLITE_NULL == valType) {
        return 0;
    }
    return sqlite3_column_int(handle, columnIndex);
}

JNIEXPORT jlong Java_org_telegram_SQLite_SQLiteCursor_columnLongValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    int valType = sqlite3_column_type(handle, columnIndex);
    if (SQLITE_NULL == valType) {
        return 0;
    }
    return sqlite3_column_int64(handle, columnIndex);
}

JNIEXPORT jdouble Java_org_telegram_SQLite_SQLiteCursor_columnDoubleValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    int valType = sqlite3_column_type(handle, columnIndex);
    if (SQLITE_NULL == valType) {
        return 0;
    }
    return sqlite3_column_double(handle, columnIndex);
}

JNIEXPORT jstring Java_org_telegram_SQLite_SQLiteCursor_columnStringValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    const char *str = (const char *) sqlite3_column_text(handle, columnIndex);
    if (str != 0) {
        return env->NewStringUTF(str);
    }
    return 0;
}

JNIEXPORT jbyteArray Java_org_telegram_SQLite_SQLiteCursor_columnByteArrayValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    const jbyte *buf = (const jbyte *) sqlite3_column_blob(handle, columnIndex);
    int length = sqlite3_column_bytes(handle, columnIndex);
    if (buf != nullptr && length > 0) {
        jbyteArray result = env->NewByteArray(length);
        env->SetByteArrayRegion(result, 0, length, buf);
        return result;
    }
    return nullptr;
}

JNIEXPORT jlong Java_org_telegram_SQLite_SQLiteCursor_columnByteBufferValue(JNIEnv *env, jobject object, jlong statementHandle, jint columnIndex) {
    sqlite3_stmt *handle = (sqlite3_stmt *) (intptr_t) statementHandle;
    uint32_t length = (uint32_t) sqlite3_column_bytes(handle, columnIndex);
    if (length <= 0) {
        return 0;
    }
    NativeByteBuffer *buffer = BuffersStorage::getInstance().getFreeBuffer(length);
    if (buffer == nullptr) {
        return 0;
    }
    const char *buf = (const char *) sqlite3_column_blob(handle, columnIndex);
    if (buf == nullptr) {
        return 0;
    }
    memcpy(buffer->bytes(), buf, length);
    return (jlong) buffer;
}

}
